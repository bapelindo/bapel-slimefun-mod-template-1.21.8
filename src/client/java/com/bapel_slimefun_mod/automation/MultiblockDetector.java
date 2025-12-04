package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/**
 * ✅ COMPLETE MULTIBLOCK DETECTOR - ALL ROTATIONS SUPPORTED
 * 
 * Checks patterns in ALL 4 CARDINAL DIRECTIONS (N, E, S, W)
 */
public class MultiblockDetector {
    
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
        
        @Override
        public String toString() {
            return String.format("%s at [%d,%d,%d] (%.0f%% confidence)", 
                machineId, dispenserPos.getX(), dispenserPos.getY(), dispenserPos.getZ(), 
                confidence * 100);
        }
    }
    
    /**
     * Detect multiblock from dispenser position
     */
    public static DetectionResult detect(Level level, BlockPos dispenserPos) {
        BapelSlimefunMod.LOGGER.info("[Detector] Starting detection at dispenser [{},{},{}]", 
            dispenserPos.getX(), dispenserPos.getY(), dispenserPos.getZ());
        
        // Validate dispenser
        if (level.getBlockState(dispenserPos).getBlock() != Blocks.DISPENSER) {
            BapelSlimefunMod.LOGGER.error("[Detector] Position is not a dispenser!");
            return null;
        }
        
        DetectionResult result;
        
        // 1. PRESSURE CHAMBER (3x3 grid)
        result = checkPressureChamber(level, dispenserPos);
        if (result != null) return result;
        
        // 2. SMELTERY variants (3D cross)
        result = checkSmeltery(level, dispenserPos);
        if (result != null) return result;
        
        result = checkMakeshiftSmeltery(level, dispenserPos);
        if (result != null) return result;
        
        // 3. 2x2 grids
        result = checkOreCrusher(level, dispenserPos);
        if (result != null) return result;
        
        result = checkCompressor(level, dispenserPos);
        if (result != null) return result;
        
        // 4. 3-block vertical
        result = checkMagicWorkbench(level, dispenserPos);
        if (result != null) return result;
        
        result = checkJuicer(level, dispenserPos);
        if (result != null) return result;
        
        result = checkOreWasher(level, dispenserPos);
        if (result != null) return result;
        
        // 5. 2-block vertical
        result = checkArmorForge(level, dispenserPos);
        if (result != null) return result;
        
        result = checkGrindStone(level, dispenserPos);
        if (result != null) return result;
        
        result = checkEnhancedCraftingTable(level, dispenserPos);
        if (result != null) return result;
        
        BapelSlimefunMod.LOGGER.warn("[Detector] No multiblock pattern matched");
        return null;
    }
    
    // ========================================================================
    // HELPER: Check pattern in ALL 4 directions
    // ========================================================================
    
    private static boolean matchesBlock(Block actual, Block expected) {
        return actual == expected;
    }
    
    private static boolean isCauldron(Block block) {
        return block == Blocks.CAULDRON || block == Blocks.WATER_CAULDRON;
    }
    
    private static boolean isFire(Block block) {
        return block == Blocks.FIRE || block == Blocks.SOUL_FIRE;
    }
    
    private static boolean isAnvil(Block block) {
        return block == Blocks.ANVIL || 
               block == Blocks.CHIPPED_ANVIL || 
               block == Blocks.DAMAGED_ANVIL;
    }
    
    // ========================================================================
    // PRESSURE CHAMBER - 3x3 Grid (ALL 4 ROTATIONS)
    // ========================================================================
    
    private static DetectionResult checkPressureChamber(Level level, BlockPos dispenserPos) {
        // Pattern: Dispenser at center-top of 3x3
        //   [SLAB] [DISPENSER] [SLAB]
        //   [PISTON] [GLASS] [PISTON]
        //   [PISTON] [CAULDRON] [PISTON]
        
        // Try all 4 directions
        BlockPos[][] directions = {
            { // NORTH (Z-)
                dispenserPos.west(), dispenserPos, dispenserPos.east(),
                dispenserPos.west().south(), dispenserPos.south(), dispenserPos.east().south(),
                dispenserPos.west().south(2), dispenserPos.south(2), dispenserPos.east().south(2)
            },
            { // EAST (X+)
                dispenserPos.north(), dispenserPos, dispenserPos.south(),
                dispenserPos.north().east(), dispenserPos.east(), dispenserPos.south().east(),
                dispenserPos.north().east(2), dispenserPos.east(2), dispenserPos.south().east(2)
            },
            { // SOUTH (Z+)
                dispenserPos.west(), dispenserPos, dispenserPos.east(),
                dispenserPos.west().north(), dispenserPos.north(), dispenserPos.east().north(),
                dispenserPos.west().north(2), dispenserPos.north(2), dispenserPos.east().north(2)
            },
            { // WEST (X-)
                dispenserPos.north(), dispenserPos, dispenserPos.south(),
                dispenserPos.north().west(), dispenserPos.west(), dispenserPos.south().west(),
                dispenserPos.north().west(2), dispenserPos.west(2), dispenserPos.south().west(2)
            }
        };
        
        for (BlockPos[] pattern : directions) {
            Block[] blocks = new Block[9];
            for (int i = 0; i < 9; i++) {
                blocks[i] = level.getBlockState(pattern[i]).getBlock();
            }
            
            // Check pattern
            if (blocks[0] == Blocks.SMOOTH_STONE_SLAB &&
                blocks[1] == Blocks.DISPENSER &&
                blocks[2] == Blocks.SMOOTH_STONE_SLAB &&
                blocks[3] == Blocks.PISTON &&
                blocks[4] == Blocks.GLASS &&
                blocks[5] == Blocks.PISTON &&
                blocks[6] == Blocks.PISTON &&
                isCauldron(blocks[7]) &&
                blocks[8] == Blocks.PISTON) {
                
                BapelSlimefunMod.LOGGER.info("[Detector] ✓ PRESSURE_CHAMBER detected");
                return new DetectionResult("PRESSURE_CHAMBER", dispenserPos, 1.0);
            }
        }
        
        return null;
    }
    
    // ========================================================================
    // SMELTERY - 3D Cross (ALL 4 ROTATIONS)
    // ========================================================================
    
    private static DetectionResult checkSmeltery(Level level, BlockPos dispenserPos) {
        // Pattern: NETHER_BRICK_FENCE - DISPENSER - NETHER_BRICKS
        //          + NETHER_BRICKS above + FIRE above that
        
        BlockPos[][] directions = {
            { dispenserPos.north(), dispenserPos.south(), dispenserPos.above(), dispenserPos.above(2) },
            { dispenserPos.east(), dispenserPos.west(), dispenserPos.above(), dispenserPos.above(2) },
            { dispenserPos.south(), dispenserPos.north(), dispenserPos.above(), dispenserPos.above(2) },
            { dispenserPos.west(), dispenserPos.east(), dispenserPos.above(), dispenserPos.above(2) }
        };
        
        for (BlockPos[] pattern : directions) {
            Block north = level.getBlockState(pattern[0]).getBlock();
            Block south = level.getBlockState(pattern[1]).getBlock();
            Block up1 = level.getBlockState(pattern[2]).getBlock();
            Block up2 = level.getBlockState(pattern[3]).getBlock();
            
            if (north == Blocks.NETHER_BRICK_FENCE &&
                south == Blocks.NETHER_BRICKS &&
                up1 == Blocks.NETHER_BRICKS &&
                isFire(up2)) {
                
                BapelSlimefunMod.LOGGER.info("[Detector] ✓ SMELTERY detected");
                return new DetectionResult("SMELTERY", dispenserPos, 1.0);
            }
        }
        
        return null;
    }
    
    private static DetectionResult checkMakeshiftSmeltery(Level level, BlockPos dispenserPos) {
        BlockPos[][] directions = {
            { dispenserPos.north(), dispenserPos.south(), dispenserPos.above(), dispenserPos.above(2) },
            { dispenserPos.east(), dispenserPos.west(), dispenserPos.above(), dispenserPos.above(2) },
            { dispenserPos.south(), dispenserPos.north(), dispenserPos.above(), dispenserPos.above(2) },
            { dispenserPos.west(), dispenserPos.east(), dispenserPos.above(), dispenserPos.above(2) }
        };
        
        for (BlockPos[] pattern : directions) {
            Block north = level.getBlockState(pattern[0]).getBlock();
            Block south = level.getBlockState(pattern[1]).getBlock();
            Block up1 = level.getBlockState(pattern[2]).getBlock();
            Block up2 = level.getBlockState(pattern[3]).getBlock();
            
            if (north == Blocks.OAK_FENCE &&
                south == Blocks.BRICKS &&
                up1 == Blocks.BRICKS &&
                isFire(up2)) {
                
                BapelSlimefunMod.LOGGER.info("[Detector] ✓ MAKESHIFT_SMELTERY detected");
                return new DetectionResult("MAKESHIFT_SMELTERY", dispenserPos, 1.0);
            }
        }
        
        return null;
    }
    
    // ========================================================================
    // ORE CRUSHER & COMPRESSOR - 2x2 Grid (ALL 4 ROTATIONS)
    // ========================================================================
    
    private static DetectionResult checkOreCrusher(Level level, BlockPos dispenserPos) {
        // Pattern: NETHER_BRICK_FENCE - IRON_BARS
        //          DISPENSER - IRON_BARS
        
        BlockPos[][] directions = {
            { dispenserPos.north(), dispenserPos.north().east(), dispenserPos.east() },
            { dispenserPos.east(), dispenserPos.east().south(), dispenserPos.south() },
            { dispenserPos.south(), dispenserPos.south().west(), dispenserPos.west() },
            { dispenserPos.west(), dispenserPos.west().north(), dispenserPos.north() }
        };
        
        for (BlockPos[] pattern : directions) {
            Block north = level.getBlockState(pattern[0]).getBlock();
            Block ne = level.getBlockState(pattern[1]).getBlock();
            Block east = level.getBlockState(pattern[2]).getBlock();
            
            if (north == Blocks.NETHER_BRICK_FENCE &&
                ne == Blocks.IRON_BARS &&
                east == Blocks.IRON_BARS) {
                
                BapelSlimefunMod.LOGGER.info("[Detector] ✓ ORE_CRUSHER detected");
                return new DetectionResult("ORE_CRUSHER", dispenserPos, 1.0);
            }
        }
        
        return null;
    }
    
    private static DetectionResult checkCompressor(Level level, BlockPos dispenserPos) {
        BlockPos[][] directions = {
            { dispenserPos.north(), dispenserPos.north().east(), dispenserPos.east() },
            { dispenserPos.east(), dispenserPos.east().south(), dispenserPos.south() },
            { dispenserPos.south(), dispenserPos.south().west(), dispenserPos.west() },
            { dispenserPos.west(), dispenserPos.west().north(), dispenserPos.north() }
        };
        
        for (BlockPos[] pattern : directions) {
            Block north = level.getBlockState(pattern[0]).getBlock();
            Block ne = level.getBlockState(pattern[1]).getBlock();
            Block east = level.getBlockState(pattern[2]).getBlock();
            
            if (north == Blocks.NETHER_BRICK_FENCE &&
                ne == Blocks.PISTON &&
                east == Blocks.PISTON) {
                
                BapelSlimefunMod.LOGGER.info("[Detector] ✓ COMPRESSOR detected");
                return new DetectionResult("COMPRESSOR", dispenserPos, 1.0);
            }
        }
        
        return null;
    }
    
    // ========================================================================
    // VERTICAL STRUCTURES (3 blocks)
    // ========================================================================
    
    private static DetectionResult checkMagicWorkbench(Level level, BlockPos dispenserPos) {
        Block above1 = level.getBlockState(dispenserPos.above()).getBlock();
        Block above2 = level.getBlockState(dispenserPos.above(2)).getBlock();
        
        if (above1 == Blocks.CRAFTING_TABLE && above2 == Blocks.BOOKSHELF) {
            BapelSlimefunMod.LOGGER.info("[Detector] ✓ MAGIC_WORKBENCH detected");
            return new DetectionResult("MAGIC_WORKBENCH", dispenserPos, 1.0);
        }
        return null;
    }
    
    private static DetectionResult checkJuicer(Level level, BlockPos dispenserPos) {
        Block above1 = level.getBlockState(dispenserPos.above()).getBlock();
        Block above2 = level.getBlockState(dispenserPos.above(2)).getBlock();
        
        if (above1 == Blocks.NETHER_BRICK_FENCE && above2 == Blocks.GLASS) {
            BapelSlimefunMod.LOGGER.info("[Detector] ✓ JUICER detected");
            return new DetectionResult("JUICER", dispenserPos, 1.0);
        }
        return null;
    }
    
    private static DetectionResult checkOreWasher(Level level, BlockPos dispenserPos) {
        Block above1 = level.getBlockState(dispenserPos.above()).getBlock();
        Block above2 = level.getBlockState(dispenserPos.above(2)).getBlock();
        
        if (above1 == Blocks.OAK_FENCE && isCauldron(above2)) {
            BapelSlimefunMod.LOGGER.info("[Detector] ✓ ORE_WASHER detected");
            return new DetectionResult("ORE_WASHER", dispenserPos, 1.0);
        }
        return null;
    }
    
    // ========================================================================
    // VERTICAL STRUCTURES (2 blocks)
    // ========================================================================
    
    private static DetectionResult checkArmorForge(Level level, BlockPos dispenserPos) {
        Block above = level.getBlockState(dispenserPos.above()).getBlock();
        
        if (isAnvil(above)) {
            BapelSlimefunMod.LOGGER.info("[Detector] ✓ ARMOR_FORGE detected");
            return new DetectionResult("ARMOR_FORGE", dispenserPos, 1.0);
        }
        return null;
    }
    
    private static DetectionResult checkGrindStone(Level level, BlockPos dispenserPos) {
        Block above = level.getBlockState(dispenserPos.above()).getBlock();
        
        if (above == Blocks.OAK_FENCE) {
            BapelSlimefunMod.LOGGER.info("[Detector] ✓ GRIND_STONE detected");
            return new DetectionResult("GRIND_STONE", dispenserPos, 1.0);
        }
        return null;
    }
    
    private static DetectionResult checkEnhancedCraftingTable(Level level, BlockPos dispenserPos) {
        Block above1 = level.getBlockState(dispenserPos.above()).getBlock();
        Block above2 = level.getBlockState(dispenserPos.above(2)).getBlock();
        
        if (above1 == Blocks.CRAFTING_TABLE && above2 != Blocks.BOOKSHELF) {
            BapelSlimefunMod.LOGGER.info("[Detector] ✓ ENHANCED_CRAFTING_TABLE detected");
            return new DetectionResult("ENHANCED_CRAFTING_TABLE", dispenserPos, 1.0);
        }
        return null;
    }
    
    // ========================================================================
    // DEBUG HELPER
    // ========================================================================
    
    public static void printDebugLayout(Level level, BlockPos dispenserPos) {
        BapelSlimefunMod.LOGGER.info("=== MULTIBLOCK DEBUG ===");
        BapelSlimefunMod.LOGGER.info("Dispenser at: [{},{},{}]", 
            dispenserPos.getX(), dispenserPos.getY(), dispenserPos.getZ());
        
        for (int y = 2; y >= -1; y--) {
            BapelSlimefunMod.LOGGER.info("--- Y={} ---", dispenserPos.getY() + y);
            for (int z = -1; z <= 1; z++) {
                StringBuilder row = new StringBuilder();
                for (int x = -1; x <= 1; x++) {
                    BlockPos pos = dispenserPos.offset(x, y, z);
                    Block block = level.getBlockState(pos).getBlock();
                    
                    String blockName = block.toString();
                    if (blockName.contains("Block{")) {
                        blockName = blockName.substring(blockName.indexOf("Block{") + 6, blockName.length() - 1);
                    }
                    if (blockName.contains(":")) {
                        blockName = blockName.split(":")[1];
                    }
                    
                    row.append(String.format("[%10s] ", blockName));
                }
                BapelSlimefunMod.LOGGER.info("  {}", row);
            }
        }
        BapelSlimefunMod.LOGGER.info("=====================");
    }
}