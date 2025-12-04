package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * ✅ FLEXIBLE: Detects multiblock by scanning ALL possible structure variations
 * 
 * STRATEGY:
 * 1. Scan 5x5x5 area around dispenser
 * 2. Build "block fingerprint" (what blocks exist and where)
 * 3. Match against known patterns with fuzzy tolerance
 * 4. Score based on signature blocks + structure completeness
 */
public class MultiblockDetector {
    
    private static final int SCAN_RADIUS = 4;
    
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
     * Main detection method - Tests ALL nearby dispensers
     */
    public static DetectionResult detect(Level level, BlockPos searchCenter) {
        BapelSlimefunMod.LOGGER.info("[Detector] Starting detection at [{},{},{}]", 
            searchCenter.getX(), searchCenter.getY(), searchCenter.getZ());
        
        // Find ALL dispensers nearby
        List<BlockPos> dispensers = findAllDispensers(level, searchCenter);
        
        if (dispensers.isEmpty()) {
            BapelSlimefunMod.LOGGER.warn("[Detector] No dispensers found nearby");
            return null;
        }
        
        BapelSlimefunMod.LOGGER.info("[Detector] Found {} dispenser(s) to check", dispensers.size());
        
        List<DetectionResult> allCandidates = new ArrayList<>();
        
        // Test EACH dispenser
        for (BlockPos dispenserPos : dispensers) {
            BapelSlimefunMod.LOGGER.info("[Detector] Testing dispenser at [{},{},{}]", 
                dispenserPos.getX(), dispenserPos.getY(), dispenserPos.getZ());
            
            // Scan structure around this dispenser
            StructureSnapshot snapshot = scanStructure(level, dispenserPos);
            
            // Get all machine definitions
            Map<String, SlimefunMachineData> machines = SlimefunDataLoader.getAllMachines();
            
            // Test each multiblock against this dispenser
            for (SlimefunMachineData machine : machines.values()) {
                if (!machine.isMultiblock() || machine.getStructure() == null) continue;
                
                double confidence = calculateConfidence(machine, snapshot);
                
                if (confidence >= 0.5) { // 50% threshold
                    allCandidates.add(new DetectionResult(
                        machine.getId(), 
                        dispenserPos, 
                        confidence, 
                        snapshot.blockCounts
                    ));
                    
                    BapelSlimefunMod.LOGGER.info("[Detector]   -> {} at dispenser [{},{},{}]: {}%", 
                        machine.getId(), dispenserPos.getX(), dispenserPos.getY(), dispenserPos.getZ(),
                        (int)(confidence * 100));
                }
            }
        }
        
        if (allCandidates.isEmpty()) {
            BapelSlimefunMod.LOGGER.warn("[Detector] No candidates found across {} dispensers!", dispensers.size());
            
            // Log first dispenser's snapshot for debugging
            if (!dispensers.isEmpty()) {
                StructureSnapshot snapshot = scanStructure(level, dispensers.get(0));
                logSnapshot(snapshot);
            }
            
            return null;
        }
        
        // Return BEST match across ALL dispensers
        // Tie-breaker: If confidence is equal, prefer the one with MORE unique signature blocks
        DetectionResult best = allCandidates.stream()
            .max(Comparator
                .comparingDouble(DetectionResult::getConfidence)
                .thenComparingInt(r -> countSignatureMatches(r.getMachineId(), 
                    scanStructure(level, r.getDispenserPos())))
            )
            .orElse(null);
        
        if (best != null) {
            BapelSlimefunMod.LOGGER.info("[Detector] ✓ BEST MATCH: {}", best.toString());
        }
        
        return best;
    }
    
    /**
     * Find ALL dispensers within scan radius (not just nearest)
     */
    private static List<BlockPos> findAllDispensers(Level level, BlockPos center) {
        List<BlockPos> dispensers = new ArrayList<>();
        
        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int y = -SCAN_RADIUS; y <= SCAN_RADIUS; y++) {
                for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (level.getBlockState(pos).getBlock() == Blocks.DISPENSER) {
                        dispensers.add(pos);
                    }
                }
            }
        }
        
        // Sort by distance to player (closest first for better UX)
        dispensers.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        
        return dispensers;
    }
    
    /**
     * Scan structure in 5x5x5 area around dispenser
     */
    private static StructureSnapshot scanStructure(Level level, BlockPos dispenserPos) {
        Map<String, Integer> blockCounts = new HashMap<>();
        Set<String> uniqueBlocks = new HashSet<>();
        List<BlockPos> allPositions = new ArrayList<>();
        
        // Scan 5x5x5 cube
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
     * Calculate confidence score for machine match
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
        
        // Penalty for excess blocks
        double excessPenalty = Math.min(0.3, excess * 0.05);
        
        // Bonus for signature blocks
        double signatureBonus = calculateSignatureBonus(machine.getId(), snapshot);
        
        // SPECIAL RULE: Enhanced Crafting Table needs MULTIPLE crafting tables (4+ based on structure)
        if (machine.getId().equals("ENHANCED_CRAFTING_TABLE")) {
            int craftingTableCount = snapshot.blockCounts.getOrDefault("CRAFTING_TABLE", 0);
            if (craftingTableCount < 4) {
                return 0; // Not Enhanced Crafting Table if less than 4 crafting tables
            }
        }
        
        // SPECIAL RULE: Magic Workbench MUST have bookshelf AND only 1-2 crafting tables
        if (machine.getId().equals("MAGIC_WORKBENCH")) {
            if (!snapshot.uniqueBlocks.contains("BOOKSHELF")) {
                return 0; // Not Magic Workbench without bookshelf
            }
            
            int craftingTableCount = snapshot.blockCounts.getOrDefault("CRAFTING_TABLE", 0);
            if (craftingTableCount >= 4) {
                return 0; // Too many crafting tables for Magic Workbench
            }
        }
        
        // SPECIAL RULE: Ore Crusher MUST have iron bars AND nether brick fence
        if (machine.getId().equals("ORE_CRUSHER")) {
            boolean hasIronBars = snapshot.uniqueBlocks.contains("IRON_BARS");
            boolean hasNetherFence = snapshot.uniqueBlocks.contains("NETHER_BRICK_FENCE");
            
            if (!hasIronBars || !hasNetherFence) {
                return 0; // Not Ore Crusher without both signature blocks
            }
        }
        
        double confidence = baseScore - excessPenalty + signatureBonus;
        return Math.max(0, Math.min(1, confidence));
    }
    
    /**
     * Count how many signature blocks match (for tie-breaking)
     */
    private static int countSignatureMatches(String machineId, StructureSnapshot snapshot) {
        Map<String, Set<String>> signatures = new HashMap<>();
        signatures.put("MAGIC_WORKBENCH", Set.of("BOOKSHELF"));
        signatures.put("ENHANCED_CRAFTING_TABLE", Set.of("CRAFTING_TABLE"));
        signatures.put("ORE_CRUSHER", Set.of("IRON_BARS", "NETHER_BRICK_FENCE"));
        signatures.put("COMPRESSOR", Set.of("PISTON"));
        signatures.put("SMELTERY", Set.of("NETHER_BRICK_FENCE", "NETHER_BRICK"));
        
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
     * Calculate bonus for unique signature blocks
     */
    private static double calculateSignatureBonus(String machineId, StructureSnapshot snapshot) {
        Map<String, Set<String>> signatures = new HashMap<>();
        
        // Define UNIQUE signatures (blocks that ONLY this machine has)
        signatures.put("MAGIC_WORKBENCH", Set.of("BOOKSHELF")); // Only Magic Workbench has bookshelf
        signatures.put("ENHANCED_CRAFTING_TABLE", Set.of("CRAFTING_TABLE")); // Multiple crafting tables
        signatures.put("ORE_CRUSHER", Set.of("IRON_BARS", "NETHER_BRICK_FENCE")); // Iron bars + nether brick fence combo
        signatures.put("COMPRESSOR", Set.of("PISTON")); // Multiple pistons
        signatures.put("SMELTERY", Set.of("NETHER_BRICK_FENCE", "NETHER_BRICK")); // Full nether brick structure
        signatures.put("PRESSURE_CHAMBER", Set.of("SMOOTH_STONE_SLAB", "GLASS", "PISTON"));
        signatures.put("ARMOR_FORGE", Set.of("ANVIL")); // Anvil signature
        signatures.put("MAKESHIFT_SMELTERY", Set.of("FURNACE", "BRICK_BLOCK"));
        signatures.put("ORE_WASHER", Set.of("CAULDRON")); // Cauldron signature
        signatures.put("JUICER", Set.of("GLASS")); // Glass + fence combo
        signatures.put("ANCIENT_ALTAR", Set.of("ENCHANTMENT_TABLE")); // Only Ancient Altar has enchanting table
        signatures.put("GOLD_PAN", Set.of("TRAP_DOOR"));
        
        Set<String> requiredSignature = signatures.get(machineId);
        if (requiredSignature == null) return 0;
        
        // ALL signature blocks must be present
        boolean hasAllSignatures = requiredSignature.stream()
            .allMatch(snapshot.uniqueBlocks::contains);
        
        if (!hasAllSignatures) return -0.5; // PENALTY if missing signature
        
        // Bonus if has signature
        return 0.3;
    }
    
    /**
     * Normalize block ID
     */
    private static String normalizeBlockId(Block block) {
        return normalizeMaterial(block.toString());
    }
    
    /**
     * Normalize material name with COMPREHENSIVE mappings and aliases
     */
    private static String normalizeMaterial(String material) {
        material = material.replace("Block{minecraft:", "")
                         .replace("}", "")
                         .toUpperCase();
        
        Map<String, String> mappings = new HashMap<>();
        
        // Standard mappings
        mappings.put("CRAFTING_TABLE", "CRAFTING_TABLE");
        mappings.put("BOOKSHELF", "BOOKSHELF");
        mappings.put("DISPENSER", "DISPENSER");
        mappings.put("OAK_FENCE", "FENCE");
        mappings.put("SPRUCE_FENCE", "FENCE");
        mappings.put("BIRCH_FENCE", "FENCE");
        mappings.put("JUNGLE_FENCE", "FENCE");
        mappings.put("ACACIA_FENCE", "FENCE");
        mappings.put("DARK_OAK_FENCE", "FENCE");
        
        // Iron Bars alternatives (Slimefun might use glass or other blocks)
        mappings.put("IRON_BARS", "IRON_BARS");
        mappings.put("BLACK_STAINED_GLASS", "IRON_BARS"); // ⚠️ Slimefun uses glass as iron bars!
        mappings.put("GRAY_STAINED_GLASS", "IRON_BARS");
        mappings.put("GLASS_PANE", "IRON_BARS");
        
        mappings.put("PISTON", "PISTON");
        mappings.put("STICKY_PISTON", "PISTON");
        
        mappings.put("CAULDRON", "CAULDRON");
        mappings.put("WATER_CAULDRON", "CAULDRON");
        mappings.put("LAVA_CAULDRON", "CAULDRON");
        
        mappings.put("NETHER_BRICK_FENCE", "NETHER_BRICK_FENCE");
        mappings.put("NETHER_BRICK_WALL", "NETHER_BRICK_FENCE"); // ⚠️ Wall = Fence in Slimefun
        mappings.put("NETHER_BRICKS", "NETHER_BRICK");
        
        mappings.put("GLASS", "GLASS");
        mappings.put("WHITE_STAINED_GLASS", "GLASS");
        mappings.put("ORANGE_STAINED_GLASS", "GLASS");
        mappings.put("MAGENTA_STAINED_GLASS", "GLASS");
        mappings.put("LIGHT_BLUE_STAINED_GLASS", "GLASS");
        mappings.put("YELLOW_STAINED_GLASS", "GLASS");
        mappings.put("LIME_STAINED_GLASS", "GLASS");
        mappings.put("PINK_STAINED_GLASS", "GLASS");
        mappings.put("CYAN_STAINED_GLASS", "GLASS");
        mappings.put("PURPLE_STAINED_GLASS", "GLASS");
        mappings.put("BLUE_STAINED_GLASS", "GLASS");
        mappings.put("BROWN_STAINED_GLASS", "GLASS");
        mappings.put("GREEN_STAINED_GLASS", "GLASS");
        mappings.put("RED_STAINED_GLASS", "GLASS");
        
        mappings.put("ANVIL", "ANVIL");
        mappings.put("CHIPPED_ANVIL", "ANVIL");
        mappings.put("DAMAGED_ANVIL", "ANVIL");
        
        mappings.put("IRON_BLOCK", "IRON_BLOCK");
        
        mappings.put("FURNACE", "FURNACE");
        mappings.put("BLAST_FURNACE", "FURNACE");
        mappings.put("SMOKER", "FURNACE");
        
        mappings.put("BRICKS", "BRICK_BLOCK");
        
        mappings.put("OAK_TRAPDOOR", "TRAP_DOOR");
        mappings.put("SPRUCE_TRAPDOOR", "TRAP_DOOR");
        mappings.put("BIRCH_TRAPDOOR", "TRAP_DOOR");
        mappings.put("JUNGLE_TRAPDOOR", "TRAP_DOOR");
        mappings.put("ACACIA_TRAPDOOR", "TRAP_DOOR");
        mappings.put("DARK_OAK_TRAPDOOR", "TRAP_DOOR");
        
        mappings.put("ENCHANTING_TABLE", "ENCHANTMENT_TABLE");
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