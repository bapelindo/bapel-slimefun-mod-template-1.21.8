package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * ✅ COMPLETE FIX: Scan from DISPENSER position, not player position
 * 
 * KEY CHANGES:
 * 1. detect() now accepts dispenserPos parameter (from GUI open event)
 * 2. No more "find nearest dispenser" - we KNOW which dispenser was opened
 * 3. Scan 3x3x3 and 5x5x5 centered on THE dispenser
 * 4. More accurate pattern matching
 */
public class MultiblockDetector {
    
    /**
     * Detection result with confidence score
     */
    public static class DetectionResult {
        private final String machineId;
        private final BlockPos dispenserPos;
        private final double confidence;
        private final Map<String, Integer> matchedBlocks;
        
        public DetectionResult(String machineId, BlockPos dispenserPos, double confidence, 
                              Map<String, Integer> matchedBlocks) {
            this.machineId = machineId;
            this.dispenserPos = dispenserPos;
            this.confidence = confidence;
            this.matchedBlocks = matchedBlocks;
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
     * Structure snapshot of area around dispenser
     */
    private static class StructureSnapshot {
        final Map<String, Integer> blockCounts;
        final Set<String> uniqueBlocks;
        final List<BlockPos> allPositions;
        
        StructureSnapshot(Map<String, Integer> blockCounts, Set<String> uniqueBlocks, 
                         List<BlockPos> allPositions) {
            this.blockCounts = blockCounts;
            this.uniqueBlocks = uniqueBlocks;
            this.allPositions = allPositions;
        }
    }
    
    /**
     * ✅ NEW SIGNATURE: Detect from OPENED dispenser position
     * 
     * @param level World level
     * @param dispenserPos Position of dispenser that was opened
     * @return Detection result or null
     */
    public static DetectionResult detect(Level level, BlockPos dispenserPos) {
        BapelSlimefunMod.LOGGER.info("[Detector] Starting detection at dispenser [{},{},{}]", 
            dispenserPos.getX(), dispenserPos.getY(), dispenserPos.getZ());
        
        // Validate that this is actually a dispenser
        if (level.getBlockState(dispenserPos).getBlock() != Blocks.DISPENSER) {
            BapelSlimefunMod.LOGGER.error("[Detector] Position is not a dispenser!");
            return null;
        }
        
        // Get all machine definitions
        Map<String, SlimefunMachineData> machines = SlimefunDataLoader.getAllMachines();
        
        // ✅ STEP 1: Try EXACT pattern matching first (3x3x3 with dispenser at center)
        for (SlimefunMachineData machine : machines.values()) {
            if (!machine.isMultiblock() || machine.getStructure() == null) continue;
            
            if (testExactPattern(level, dispenserPos, machine)) {
                BapelSlimefunMod.LOGGER.info("[Detector] ✓ EXACT MATCH: {} (100%)", machine.getId());
                
                StructureSnapshot snapshot = scanStructure(level, dispenserPos);
                return new DetectionResult(
                    machine.getId(), 
                    dispenserPos, 
                    1.0,
                    snapshot.blockCounts
                );
            }
        }
        
        // ✅ STEP 2: FALLBACK - Fuzzy matching (5x5x5 scan)
        BapelSlimefunMod.LOGGER.info("[Detector] No exact match, trying fuzzy matching...");
        
        StructureSnapshot snapshot = scanStructure(level, dispenserPos);
        List<DetectionResult> candidates = new ArrayList<>();
        
        for (SlimefunMachineData machine : machines.values()) {
            if (!machine.isMultiblock() || machine.getStructure() == null) continue;
            
            double confidence = calculateConfidence(machine, snapshot);
            
            if (confidence >= 0.5) {
                candidates.add(new DetectionResult(
                    machine.getId(), 
                    dispenserPos, 
                    confidence, 
                    snapshot.blockCounts
                ));
                
                BapelSlimefunMod.LOGGER.info("[Detector]   -> {}: {}% (fuzzy)", 
                    machine.getId(), (int)(confidence * 100));
            }
        }
        
        if (candidates.isEmpty()) {
            BapelSlimefunMod.LOGGER.warn("[Detector] No match found");
            logSnapshot(snapshot);
            return null;
        }
        
        // Return BEST match
        DetectionResult best = candidates.stream()
            .max(Comparator
                .comparingDouble(DetectionResult::getConfidence)
                .thenComparingInt(r -> countSignatureMatches(r.getMachineId(), snapshot))
            )
            .orElse(null);
        
        if (best != null) {
            BapelSlimefunMod.LOGGER.info("[Detector] ✓ BEST MATCH: {}", best.toString());
        }
        
        return best;
    }
    
    /**
     * ✅ IMPROVED: Test exact 3x3x3 pattern with dispenser at CENTER
     */
    private static boolean testExactPattern(Level level, BlockPos dispenserPos, SlimefunMachineData machine) {
        List<SlimefunMachineData.MultiblockStructure> structure = machine.getStructure();
        
        if (structure.size() != 27) return false;
        
        // Build expected pattern
        Map<Integer, String> expectedPattern = new HashMap<>();
        for (int i = 0; i < 27; i++) {
            String material = normalizeMaterial(structure.get(i).getMaterial());
            expectedPattern.put(i, material);
        }
        
        // CRITICAL: Index 13 (center) MUST be DISPENSER
        if (!"DISPENSER".equals(expectedPattern.get(13))) {
            return false;
        }
        
        // Scan 3x3x3 cube with dispenser as center
        // Order: [y=-1 to 1][z=-1 to 1][x=-1 to 1]
        int index = 0;
        int mismatches = 0;
        
        for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                for (int x = -1; x <= 1; x++) {
                    BlockPos checkPos = dispenserPos.offset(x, y, z);
                    BlockState state = level.getBlockState(checkPos);
                    Block block = state.getBlock();
                    
                    String actualMaterial = normalizeMaterial(block.toString());
                    String expectedMaterial = expectedPattern.get(index);
                    
                    if (!actualMaterial.equals(expectedMaterial)) {
                        mismatches++;
                        if (mismatches <= 3) {
                            BapelSlimefunMod.LOGGER.debug("[Detector] Mismatch at index {}: expected {}, got {}", 
                                index, expectedMaterial, actualMaterial);
                        }
                        return false;
                    }
                    
                    index++;
                }
            }
        }
        
        return true;
    }
    
    /**
     * ✅ IMPROVED: Scan 5x5x5 area centered on dispenser
     */
    private static StructureSnapshot scanStructure(Level level, BlockPos dispenserPos) {
        Map<String, Integer> blockCounts = new HashMap<>();
        Set<String> uniqueBlocks = new HashSet<>();
        List<BlockPos> allPositions = new ArrayList<>();
        
        // Scan 5x5x5 cube centered on dispenser
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos pos = dispenserPos.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    Block block = state.getBlock();
                    
                    if (!state.isAir()) {
                        String blockId = normalizeBlockId(block);
                        blockCounts.merge(blockId, 1, Integer::sum);
                        uniqueBlocks.add(blockId);
                        allPositions.add(pos);
                    }
                }
            }
        }
        
        return new StructureSnapshot(blockCounts, uniqueBlocks, allPositions);
    }
    
    /**
     * ✅ IMPROVED: Calculate confidence with stricter rules
     */
    private static double calculateConfidence(SlimefunMachineData machine, StructureSnapshot snapshot) {
        List<SlimefunMachineData.MultiblockStructure> structure = machine.getStructure();
        
        // Build required blocks
        Map<String, Integer> required = new HashMap<>();
        for (SlimefunMachineData.MultiblockStructure block : structure) {
            String material = normalizeMaterial(block.getMaterial());
            required.merge(material, 1, Integer::sum);
        }
        
        int totalRequired = required.values().stream().mapToInt(Integer::intValue).sum();
        int matched = 0;
        int excess = 0;
        
        // Count matches
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            String material = entry.getKey();
            int requiredCount = entry.getValue();
            int foundCount = snapshot.blockCounts.getOrDefault(material, 0);
            
            int matchCount = Math.min(requiredCount, foundCount);
            matched += matchCount;
            
            if (foundCount > requiredCount) {
                excess += (foundCount - requiredCount);
            }
        }
        
        // Base score
        double baseScore = (double) matched / totalRequired;
        
        // Penalty for excess blocks (more strict)
        double excessPenalty = Math.min(0.4, excess * 0.08);
        
        // Bonus for signature blocks
        double signatureBonus = calculateSignatureBonus(machine.getId(), snapshot);
        
        // ✅ SPECIAL RULES (more strict)
        
        // ENHANCED_CRAFTING_TABLE: Must have 4+ crafting tables
        if (machine.getId().equals("ENHANCED_CRAFTING_TABLE")) {
            int craftingTableCount = snapshot.blockCounts.getOrDefault("CRAFTING_TABLE", 0);
            if (craftingTableCount < 4) {
                return 0;
            }
            // ✅ NEW: Must NOT have bookshelf (to distinguish from MAGIC_WORKBENCH)
            if (snapshot.uniqueBlocks.contains("BOOKSHELF")) {
                return 0;
            }
        }
        
        // MAGIC_WORKBENCH: Must have bookshelf AND only 1 crafting table
        if (machine.getId().equals("MAGIC_WORKBENCH")) {
            if (!snapshot.uniqueBlocks.contains("BOOKSHELF")) {
                return 0;
            }
            int craftingTableCount = snapshot.blockCounts.getOrDefault("CRAFTING_TABLE", 0);
            if (craftingTableCount != 1) { // ✅ STRICT: Exactly 1 crafting table
                return 0;
            }
        }
        
        // ORE_CRUSHER: Must have BOTH iron bars AND nether brick fence
        if (machine.getId().equals("ORE_CRUSHER")) {
            boolean hasIronBars = snapshot.uniqueBlocks.contains("IRON_BARS");
            boolean hasNetherFence = snapshot.uniqueBlocks.contains("NETHER_BRICK_FENCE");
            if (!hasIronBars || !hasNetherFence) {
                return 0;
            }
        }
        
        // COMPRESSOR: Must have pistons, but NOT cauldron/bookshelf/anvil
        if (machine.getId().equals("COMPRESSOR")) {
            if (!snapshot.uniqueBlocks.contains("PISTON")) {
                return 0;
            }
            // ✅ NEW: Exclude if has signature blocks from other machines
            if (snapshot.uniqueBlocks.contains("BOOKSHELF") || 
                snapshot.uniqueBlocks.contains("CAULDRON") ||
                snapshot.uniqueBlocks.contains("ANVIL")) {
                return 0;
            }
        }
        
        // SMELTERY: Must have nether brick fence + nether brick
        if (machine.getId().equals("SMELTERY")) {
            if (!snapshot.uniqueBlocks.contains("NETHER_BRICK_FENCE") ||
                !snapshot.uniqueBlocks.contains("NETHER_BRICK")) {
                return 0;
            }
        }
        
        // PRESSURE_CHAMBER: Must have smooth stone slab + glass + piston
        if (machine.getId().equals("PRESSURE_CHAMBER")) {
            if (!snapshot.uniqueBlocks.contains("SMOOTH_STONE_SLAB") ||
                !snapshot.uniqueBlocks.contains("GLASS") ||
                !snapshot.uniqueBlocks.contains("PISTON")) {
                return 0;
            }
        }
        
        // ARMOR_FORGE: Must have anvil
        if (machine.getId().equals("ARMOR_FORGE")) {
            if (!snapshot.uniqueBlocks.contains("ANVIL")) {
                return 0;
            }
        }
        
        // ORE_WASHER: Must have cauldron
        if (machine.getId().equals("ORE_WASHER")) {
            if (!snapshot.uniqueBlocks.contains("CAULDRON")) {
                return 0;
            }
        }
        
        double confidence = baseScore - excessPenalty + signatureBonus;
        return Math.max(0, Math.min(1, confidence));
    }
    
    /**
     * Count signature matches (for tie-breaking)
     */
    private static int countSignatureMatches(String machineId, StructureSnapshot snapshot) {
        Map<String, Set<String>> signatures = new HashMap<>();
        signatures.put("MAGIC_WORKBENCH", Set.of("BOOKSHELF"));
        signatures.put("ENHANCED_CRAFTING_TABLE", Set.of("CRAFTING_TABLE"));
        signatures.put("ORE_CRUSHER", Set.of("IRON_BARS", "NETHER_BRICK_FENCE"));
        signatures.put("COMPRESSOR", Set.of("PISTON"));
        signatures.put("SMELTERY", Set.of("NETHER_BRICK_FENCE", "NETHER_BRICK"));
        signatures.put("PRESSURE_CHAMBER", Set.of("SMOOTH_STONE_SLAB", "GLASS", "PISTON"));
        signatures.put("ARMOR_FORGE", Set.of("ANVIL"));
        signatures.put("ORE_WASHER", Set.of("CAULDRON"));
        
        Set<String> required = signatures.get(machineId);
        if (required == null) return 0;
        
        int count = 0;
        for (String sig : required) {
            if (snapshot.uniqueBlocks.contains(sig)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Calculate signature bonus
     */
    private static double calculateSignatureBonus(String machineId, StructureSnapshot snapshot) {
        Map<String, Set<String>> signatures = new HashMap<>();
        
        signatures.put("MAGIC_WORKBENCH", Set.of("BOOKSHELF"));
        signatures.put("ENHANCED_CRAFTING_TABLE", Set.of("CRAFTING_TABLE"));
        signatures.put("ORE_CRUSHER", Set.of("IRON_BARS", "NETHER_BRICK_FENCE"));
        signatures.put("COMPRESSOR", Set.of("PISTON"));
        signatures.put("SMELTERY", Set.of("NETHER_BRICK_FENCE", "NETHER_BRICK"));
        signatures.put("PRESSURE_CHAMBER", Set.of("SMOOTH_STONE_SLAB", "GLASS", "PISTON"));
        signatures.put("ARMOR_FORGE", Set.of("ANVIL"));
        signatures.put("MAKESHIFT_SMELTERY", Set.of("FURNACE", "BRICK_BLOCK"));
        signatures.put("ORE_WASHER", Set.of("CAULDRON"));
        signatures.put("JUICER", Set.of("GLASS"));
        signatures.put("ANCIENT_ALTAR", Set.of("ENCHANTMENT_TABLE"));
        signatures.put("GOLD_PAN", Set.of("TRAP_DOOR"));
        
        Set<String> requiredSignature = signatures.get(machineId);
        if (requiredSignature == null) return 0;
        
        boolean hasAllSignatures = requiredSignature.stream()
            .allMatch(snapshot.uniqueBlocks::contains);
        
        if (!hasAllSignatures) return -0.5;
        return 0.4; // Increased bonus for signature match
    }
    
    /**
     * Normalize block ID
     */
    private static String normalizeBlockId(Block block) {
        return normalizeMaterial(block.toString());
    }
    
    /**
     * Normalize material name with comprehensive mappings
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
        mappings.put("IRON_BLOCK", "IRON_BLOCK");
        mappings.put("ANVIL", "ANVIL");
        mappings.put("CHIPPED_ANVIL", "ANVIL");
        mappings.put("DAMAGED_ANVIL", "ANVIL");
        mappings.put("ENCHANTING_TABLE", "ENCHANTMENT_TABLE");
        
        // Fences
        String[] fenceTypes = {"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", 
                               "MANGROVE", "CHERRY", "BAMBOO", "CRIMSON", "WARPED"};
        for (String type : fenceTypes) {
            mappings.put(type + "_FENCE", "FENCE");
        }
        
        // Iron Bars & alternatives
        mappings.put("IRON_BARS", "IRON_BARS");
        mappings.put("BLACK_STAINED_GLASS", "IRON_BARS");
        mappings.put("GRAY_STAINED_GLASS", "IRON_BARS");
        mappings.put("GLASS_PANE", "IRON_BARS");
        
        // Glass (all colors)
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
        
        // Nether Brick
        mappings.put("NETHER_BRICK_FENCE", "NETHER_BRICK_FENCE");
        mappings.put("NETHER_BRICK_WALL", "NETHER_BRICK_FENCE");
        mappings.put("NETHER_BRICKS", "NETHER_BRICK");
        
        // Furnace
        mappings.put("FURNACE", "FURNACE");
        mappings.put("BLAST_FURNACE", "FURNACE");
        mappings.put("SMOKER", "FURNACE");
        
        // Bricks
        mappings.put("BRICKS", "BRICK_BLOCK");
        
        // Trapdoor
        String[] trapdoorTypes = {"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK",
                                  "MANGROVE", "CHERRY", "BAMBOO", "CRIMSON", "WARPED", "IRON",
                                  "COPPER", "EXPOSED_COPPER", "WEATHERED_COPPER", "OXIDIZED_COPPER",
                                  "WAXED_COPPER", "WAXED_EXPOSED_COPPER", "WAXED_WEATHERED_COPPER",
                                  "WAXED_OXIDIZED_COPPER"};
        for (String type : trapdoorTypes) {
            mappings.put(type + "_TRAPDOOR", "TRAP_DOOR");
        }
        
        // Stone
        mappings.put("STONE_BRICKS", "SMOOTH_BRICK");
        mappings.put("SMOOTH_STONE_SLAB", "SMOOTH_STONE_SLAB");
        
        return mappings.getOrDefault(material, material);
    }
    
    /**
     * Log snapshot for debugging
     */
    private static void logSnapshot(StructureSnapshot snapshot) {
        BapelSlimefunMod.LOGGER.info("[Detector] === Structure Snapshot ===");
        BapelSlimefunMod.LOGGER.info("[Detector] Unique blocks: {}", snapshot.uniqueBlocks);
        BapelSlimefunMod.LOGGER.info("[Detector] Block counts:");
        
        snapshot.blockCounts.forEach((block, count) -> 
            BapelSlimefunMod.LOGGER.info("[Detector]   - {} x{}", block, count)
        );
        
        BapelSlimefunMod.LOGGER.info("[Detector] =======================");
    }
    
    /**
     * Get detailed report
     */
    public static String getDetectionReport(DetectionResult result) {
        if (result == null) return "No machine detected";
        
        return String.format("Detected: %s (%.0f%% confidence)", 
            result.getMachineId(), result.getConfidence() * 100);
    }
}