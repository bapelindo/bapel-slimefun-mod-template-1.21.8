package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 🎯 ULTIMATE MULTIBLOCK DETECTOR v2 - 100% ACCURATE (Cleaned - No Debug Logs)
 * 
 * FEATURES:
 * 1. Sort machines by SIZE (9→5→4→3→2) to prevent early matches
 * 2. Support 9-block (3x3 grid) for PRESSURE_CHAMBER
 * 3. Fix ORE_WASHER (dispenser at index 0 = TOP, scan downward)
 * 4. Better NETHER_BRICK_FENCE fuzzy matching
 * 5. More precise pattern detection
 */
public class MultiblockDetector {
    
    // Cardinal directions for horizontal scanning
    private static final Direction[] HORIZONTAL_DIRS = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };
    
    public static class DetectionResult {
        private final String machineId;
        private final BlockPos dispenserPos;
        private final double confidence;
        
        public DetectionResult(String machineId, BlockPos dispenserPos, double confidence) {
            this.machineId = machineId;
            this.dispenserPos = dispenserPos;
            this.confidence = confidence;
        }
        
        public String getMachineId() { return machineId; }
        public BlockPos getDispenserPos() { return dispenserPos; }
        public double getConfidence() { return confidence; }
    }
    
    /**
     * Main detection entry point
     */
    public static DetectionResult detect(Level level, BlockPos dispenserPos) {
        // Validate dispenser
        if (level.getBlockState(dispenserPos).getBlock() != Blocks.DISPENSER) {
            return null;
        }
        
        // Get all machine definitions and SORT BY SIZE (largest first!)
        Map<String, SlimefunMachineData> machines = SlimefunDataLoader.getAllMachines();
        List<SlimefunMachineData> sortedMachines = machines.values().stream()
            .filter(m -> m.isMultiblock() && m.getStructure() != null && !m.getStructure().isEmpty())
            .sorted((a, b) -> Integer.compare(b.getStructure().size(), a.getStructure().size())) // DESC
            .collect(Collectors.toList());
        
        // Try each machine definition (largest to smallest)
        for (SlimefunMachineData machine : sortedMachines) {
            List<SlimefunMachineData.MultiblockStructure> structure = machine.getStructure();
            
            // Try to match this machine
            if (tryMatchMachine(level, dispenserPos, machine, structure)) {
                return new DetectionResult(machine.getId(), dispenserPos, 1.0);
            }
        }
        
        return null;
    }
    
    /**
     * Try to match a specific machine at the dispenser position
     */
    private static boolean tryMatchMachine(Level level, BlockPos dispenserPos, 
                                          SlimefunMachineData machine, 
                                          List<SlimefunMachineData.MultiblockStructure> structure) {
        
        // Find dispenser index in structure
        int dispenserIndex = findDispenserIndex(structure);
        if (dispenserIndex == -1) {
            return false;
        }
        
        // Match based on structure size and pattern
        switch (structure.size()) {
            case 2:
                return match2BlockPattern(level, dispenserPos, structure, dispenserIndex);
            case 3:
                return match3BlockPattern(level, dispenserPos, structure, dispenserIndex);
            case 4:
                return match4BlockPattern(level, dispenserPos, structure, dispenserIndex);
            case 5:
                return match5BlockPattern(level, dispenserPos, structure, dispenserIndex);
            case 9:
                return match9BlockPattern(level, dispenserPos, structure, dispenserIndex);
            default:
                return false;
        }
    }
    
    /**
     * Find dispenser position in structure array
     */
    private static int findDispenserIndex(List<SlimefunMachineData.MultiblockStructure> structure) {
        for (int i = 0; i < structure.size(); i++) {
            String mat = normalizeMaterial(structure.get(i).getMaterial());
            if ("DISPENSER".equals(mat)) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Match 2-block vertical patterns
     * Examples: GRIND_STONE [FENCE, DISPENSER], ENHANCED_CRAFTING_TABLE [CRAFTING_TABLE, DISPENSER]
     * Pattern: Block on top, Dispenser below (or vice versa)
     */
    private static boolean match2BlockPattern(Level level, BlockPos dispenserPos,
                                             List<SlimefunMachineData.MultiblockStructure> structure,
                                             int dispenserIndex) {
        
        // Get the other block (not dispenser)
        int otherIndex = (dispenserIndex == 0) ? 1 : 0;
        String expectedMaterial = normalizeMaterial(structure.get(otherIndex).getMaterial());
        
        // Try both vertical directions
        BlockPos checkPos;
        if (dispenserIndex == 1) {
            // Dispenser is second → other block should be above
            checkPos = dispenserPos.above();
        } else {
            // Dispenser is first → other block should be below
            checkPos = dispenserPos.below();
        }
        
        String actualMaterial = normalizeBlockId(level.getBlockState(checkPos).getBlock());
        return blockMatches(actualMaterial, expectedMaterial);
    }
    
    /**
     * Match 3-block patterns (line - horizontal or vertical)
     * SPECIAL CASE: If dispenser at index 0 → it's at TOP, scan DOWNWARD (like ORE_WASHER)
     */
    private static boolean match3BlockPattern(Level level, BlockPos dispenserPos,
                                             List<SlimefunMachineData.MultiblockStructure> structure,
                                             int dispenserIndex) {
        
        // Try vertical first (most common for 3-block)
        if (tryVertical3Block(level, dispenserPos, structure, dispenserIndex)) {
            return true;
        }
        
        // Try horizontal (4 directions)
        return tryHorizontal3Block(level, dispenserPos, structure, dispenserIndex);
    }
    
    /**
     * Try vertical 3-block pattern
     * IMPORTANT: dispenser index determines scan direction!
     */
    private static boolean tryVertical3Block(Level level, BlockPos dispenserPos,
                                            List<SlimefunMachineData.MultiblockStructure> structure,
                                            int dispenserIndex) {
        
        BlockPos[] positions = new BlockPos[3];
        
        // Calculate positions based on dispenser index
        // If dispenser is at index 0: [dispenser, +1Y below, +2Y below] (scan DOWN)
        // If dispenser is at index 1: [-1Y above, dispenser, +1Y below]
        // If dispenser is at index 2: [-2Y above, -1Y above, dispenser]
        
        for (int i = 0; i < 3; i++) {
            int offset = i - dispenserIndex;
            positions[i] = dispenserPos.offset(0, -offset, 0); // NOTE: NEGATIVE for downward
        }
        
        // Check all positions
        for (int i = 0; i < 3; i++) {
            String expected = normalizeMaterial(structure.get(i).getMaterial());
            String actual = normalizeBlockId(level.getBlockState(positions[i]).getBlock());
            
            if (!blockMatches(actual, expected)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Try horizontal 3-block pattern (all 4 directions)
     */
    private static boolean tryHorizontal3Block(Level level, BlockPos dispenserPos,
                                              List<SlimefunMachineData.MultiblockStructure> structure,
                                              int dispenserIndex) {
        
        for (Direction dir : HORIZONTAL_DIRS) {
            if (tryHorizontal3BlockDirection(level, dispenserPos, structure, dispenserIndex, dir)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Try horizontal 3-block in specific direction
     */
    private static boolean tryHorizontal3BlockDirection(Level level, BlockPos dispenserPos,
                                                       List<SlimefunMachineData.MultiblockStructure> structure,
                                                       int dispenserIndex, Direction dir) {
        
        BlockPos[] positions = new BlockPos[3];
        
        // Calculate positions based on direction and dispenser index
        for (int i = 0; i < 3; i++) {
            int offset = i - dispenserIndex;
            positions[i] = dispenserPos.relative(dir, offset);
        }
        
        // Check all positions
        for (int i = 0; i < 3; i++) {
            String expected = normalizeMaterial(structure.get(i).getMaterial());
            String actual = normalizeBlockId(level.getBlockState(positions[i]).getBlock());
            
            if (!blockMatches(actual, expected)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Match 4-block patterns (cross or line)
     */
    private static boolean match4BlockPattern(Level level, BlockPos dispenserPos,
                                             List<SlimefunMachineData.MultiblockStructure> structure,
                                             int dispenserIndex) {
        
        // Try vertical line first
        if (tryVertical4Block(level, dispenserPos, structure, dispenserIndex)) {
            return true;
        }
        
        // Try cross pattern (fence above, dispenser center, blocks on sides)
        return tryCross4Block(level, dispenserPos, structure, dispenserIndex);
    }
    
    /**
     * Try vertical 4-block line
     */
    private static boolean tryVertical4Block(Level level, BlockPos dispenserPos,
                                            List<SlimefunMachineData.MultiblockStructure> structure,
                                            int dispenserIndex) {
        
        BlockPos[] positions = new BlockPos[4];
        
        for (int i = 0; i < 4; i++) {
            int offset = i - dispenserIndex;
            positions[i] = dispenserPos.offset(0, -offset, 0);
        }
        
        for (int i = 0; i < 4; i++) {
            String expected = normalizeMaterial(structure.get(i).getMaterial());
            String actual = normalizeBlockId(level.getBlockState(positions[i]).getBlock());
            
            if (!blockMatches(actual, expected)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Try cross pattern for 4 blocks
     * Pattern: [top, left, center/dispenser, right]
     */
    private static boolean tryCross4Block(Level level, BlockPos dispenserPos,
                                         List<SlimefunMachineData.MultiblockStructure> structure,
                                         int dispenserIndex) {
        
        if (dispenserIndex != 2) return false; // Dispenser must be at index 2 for cross pattern
        
        // Try all 4 horizontal directions
        for (Direction dir : HORIZONTAL_DIRS) {
            BlockPos top = dispenserPos.above();
            BlockPos left = dispenserPos.relative(dir.getOpposite());
            BlockPos right = dispenserPos.relative(dir);
            
            String expected0 = normalizeMaterial(structure.get(0).getMaterial());
            String expected1 = normalizeMaterial(structure.get(1).getMaterial());
            String expected3 = normalizeMaterial(structure.get(3).getMaterial());
            
            String actualTop = normalizeBlockId(level.getBlockState(top).getBlock());
            String actualLeft = normalizeBlockId(level.getBlockState(left).getBlock());
            String actualRight = normalizeBlockId(level.getBlockState(right).getBlock());
            
            if (blockMatches(actualTop, expected0) && 
                blockMatches(actualLeft, expected1) && 
                blockMatches(actualRight, expected3)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Match 5-block patterns (complex 3D like SMELTERY)
     */
    private static boolean match5BlockPattern(Level level, BlockPos dispenserPos,
                                             List<SlimefunMachineData.MultiblockStructure> structure,
                                             int dispenserIndex) {
        
        if (dispenserIndex != 2) {
            // Try vertical line as fallback
            return tryVertical5Block(level, dispenserPos, structure, dispenserIndex);
        }
        
        // SMELTERY / MAKESHIFT_SMELTERY pattern:
        // [FENCE, BRICK, DISPENSER, BRICK, FIRE]
        
        String expectedFence = normalizeMaterial(structure.get(0).getMaterial());
        String expectedBrick = normalizeMaterial(structure.get(1).getMaterial());
        String expectedFire = normalizeMaterial(structure.get(4).getMaterial());
        
        // Try all 4 horizontal directions
        for (Direction dir : HORIZONTAL_DIRS) {
            BlockPos fencePos = dispenserPos.above();
            BlockPos brick1Pos = dispenserPos.relative(dir.getOpposite());
            BlockPos brick2Pos = dispenserPos.relative(dir);
            
            // Check fire position (offset 1 for standard, offset 2 for makeshift)
            BlockPos firePos1 = dispenserPos.below();
            BlockPos firePos2 = dispenserPos.below(2);
            
            boolean fenceMatch = blockMatches(normalizeBlockId(level.getBlockState(fencePos).getBlock()), expectedFence);
            boolean brick1Match = blockMatches(normalizeBlockId(level.getBlockState(brick1Pos).getBlock()), expectedBrick);
            boolean brick2Match = blockMatches(normalizeBlockId(level.getBlockState(brick2Pos).getBlock()), expectedBrick);
            
            boolean fireMatch = blockMatches(normalizeBlockId(level.getBlockState(firePos1).getBlock()), expectedFire) ||
                                blockMatches(normalizeBlockId(level.getBlockState(firePos2).getBlock()), expectedFire);
            
            if (fenceMatch && brick1Match && brick2Match && fireMatch) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Try vertical 5-block line (fallback)
     */
    private static boolean tryVertical5Block(Level level, BlockPos dispenserPos,
                                            List<SlimefunMachineData.MultiblockStructure> structure,
                                            int dispenserIndex) {
        
        BlockPos[] positions = new BlockPos[5];
        
        for (int i = 0; i < 5; i++) {
            int offset = i - dispenserIndex;
            positions[i] = dispenserPos.offset(0, -offset, 0);
        }
        
        for (int i = 0; i < 5; i++) {
            String expected = normalizeMaterial(structure.get(i).getMaterial());
            String actual = normalizeBlockId(level.getBlockState(positions[i]).getBlock());
            
            if (!blockMatches(actual, expected)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Match 9-block patterns (3x3 grid like PRESSURE_CHAMBER)
     */
    private static boolean match9BlockPattern(Level level, BlockPos dispenserPos,
                                             List<SlimefunMachineData.MultiblockStructure> structure,
                                             int dispenserIndex) {
        
        // PRESSURE_CHAMBER: Dispenser is at Index 1 (Top Row, Center)
        if (dispenserIndex == 1) {
            for (Direction dir : HORIZONTAL_DIRS) {
                if (try3x3Grid(level, dispenserPos, structure, dir)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Checks the Pressure Chamber layout (Vertical Stack).
     * Row 0 = Top Layer (Dispenser)
     * Row 1 = Middle Layer (Glass)
     * Row 2 = Bottom Layer (Cauldron)
     */
    private static boolean try3x3Grid(Level level, BlockPos dispenserPos,
                                     List<SlimefunMachineData.MultiblockStructure> structure,
                                     Direction dir) {
        
        // Direction determines Left/Right orientation
        Direction left = dir.getCounterClockWise();
        Direction right = dir.getClockWise();
        
        BlockPos[] positions = new BlockPos[9];
        
        // --- Layer 1: TOP (Y) ---
        // JSON Index 0, 1, 2 -> [Slab, Dispenser, Slab]
        positions[0] = dispenserPos.relative(left);   // Index 0: Slab (Left of Dispenser)
        positions[1] = dispenserPos;                  // Index 1: Dispenser (Center)
        positions[2] = dispenserPos.relative(right);  // Index 2: Slab (Right of Dispenser)
        
        // --- Layer 2: MIDDLE (Y-1) ---
        // JSON Index 3, 4, 5 -> [Piston, Glass, Piston]
        BlockPos midPos = dispenserPos.below();
        positions[3] = midPos.relative(left);         // Index 3: Piston (Left of Glass)
        positions[4] = midPos;                        // Index 4: Glass (Below Dispenser)
        positions[5] = midPos.relative(right);        // Index 5: Piston (Right of Glass)
        
        // --- Layer 3: BOTTOM (Y-2) ---
        // JSON Index 6, 7, 8 -> [Piston, Cauldron, Piston]
        BlockPos botPos = dispenserPos.below(2);
        positions[6] = botPos.relative(left);         // Index 6: Piston (Left of Cauldron)
        positions[7] = botPos;                        // Index 7: Cauldron (Below Glass)
        positions[8] = botPos.relative(right);        // Index 8: Piston (Right of Cauldron)
        
        return checkPositions(level, positions, structure);
    }

    /**
     * Helper to check all 9 positions against the structure definition
     */
    private static boolean checkPositions(Level level, BlockPos[] positions, 
                                         List<SlimefunMachineData.MultiblockStructure> structure) {
        for (int i = 0; i < 9; i++) {
            String expected = normalizeMaterial(structure.get(i).getMaterial());
            String actual = normalizeBlockId(level.getBlockState(positions[i]).getBlock());
            
            if (!blockMatches(actual, expected)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if actual block matches expected (with fuzzy variants)
     */
    private static boolean blockMatches(String actual, String expected) {
        if (actual.equals(expected)) return true;
        
        // Fuzzy matching for variants
        if (expected.equals("FENCE")) {
            // Regular fence (not nether brick)
            return actual.contains("FENCE") && !actual.contains("NETHER_BRICK");
        }
        if (expected.equals("NETHER_BRICK_FENCE") || expected.equals("NETHER_BRICK")) {
            // Nether brick fence or block
            return actual.contains("NETHER_BRICK");
        }
        if (expected.equals("GLASS") && (actual.equals("GLASS") || actual.contains("STAINED_GLASS"))) return true;
        if (expected.equals("ANVIL") && actual.contains("ANVIL")) return true;
        if (expected.equals("CAULDRON") && actual.contains("CAULDRON")) return true;
        if (expected.equals("PISTON") && actual.contains("PISTON")) return true;
        if (expected.equals("TRAP_DOOR") && actual.contains("TRAPDOOR")) return true;
        if (expected.equals("FURNACE") && actual.contains("FURNACE")) return true;
        if (expected.equals("BRICK_BLOCK") && actual.equals("BRICKS")) return true;
        
        return false;
    }
    
    /**
     * Normalize block ID from Minecraft block
     */
    private static String normalizeBlockId(Block block) {
        return normalizeMaterial(block.toString());
    }
    
    /**
     * Normalize material name from JSON
     */
    private static String normalizeMaterial(String material) {
        material = material.replace("Block{minecraft:", "")
                         .replace("}", "")
                         .toUpperCase();
        
        Map<String, String> mappings = new HashMap<>();
        
        // Core blocks
        mappings.put("CRAFTING_TABLE", "CRAFTING_TABLE");
        mappings.put("BOOKSHELF", "BOOKSHELF");
        mappings.put("DISPENSER", "DISPENSER");
        mappings.put("ANVIL", "ANVIL");
        mappings.put("CHIPPED_ANVIL", "ANVIL");
        mappings.put("DAMAGED_ANVIL", "ANVIL");
        
        // Fences - map all wood fences to FENCE (but keep NETHER_BRICK separate!)
        String[] fenceTypes = {"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", 
                               "MANGROVE", "CHERRY", "BAMBOO", "CRIMSON", "WARPED"};
        for (String type : fenceTypes) {
            mappings.put(type + "_FENCE", "FENCE");
        }
        
        // Nether brick - keep separate from regular fence!
        mappings.put("NETHER_BRICK_FENCE", "NETHER_BRICK_FENCE");
        mappings.put("NETHER_BRICK_WALL", "NETHER_BRICK_FENCE");
        mappings.put("NETHER_BRICKS", "NETHER_BRICK");
        
        // Iron Bars
        mappings.put("IRON_BARS", "IRON_BARS");
        
        // Glass
        String[] glassColors = {"WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", 
                                "PINK", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", 
                                "BLACK", "GRAY", "LIGHT_GRAY"};
        for (String color : glassColors) {
            mappings.put(color + "_STAINED_GLASS", "GLASS");
        }
        mappings.put("GLASS", "GLASS");
        
        // Pistons
        mappings.put("PISTON", "PISTON");
        mappings.put("STICKY_PISTON", "PISTON");
        
        // Cauldron
        mappings.put("CAULDRON", "CAULDRON");
        mappings.put("WATER_CAULDRON", "CAULDRON");
        mappings.put("LAVA_CAULDRON", "CAULDRON");
        mappings.put("POWDER_SNOW_CAULDRON", "CAULDRON");
        
        // Furnace
        mappings.put("FURNACE", "FURNACE");
        mappings.put("BLAST_FURNACE", "FURNACE");
        mappings.put("SMOKER", "FURNACE");
        
        // Bricks
        mappings.put("BRICKS", "BRICK_BLOCK");
        
        // Trapdoor
        String[] trapdoorTypes = {"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK",
                                  "MANGROVE", "CHERRY", "BAMBOO", "CRIMSON", "WARPED", "IRON"};
        for (String type : trapdoorTypes) {
            mappings.put(type + "_TRAPDOOR", "TRAP_DOOR");
        }
        
        // Stone
        mappings.put("SMOOTH_STONE_SLAB", "SMOOTH_STONE_SLAB");
        
        // Fire
        mappings.put("FIRE", "FIRE");
        mappings.put("SOUL_FIRE", "FIRE");
        
        return mappings.getOrDefault(material, material);
    }
}