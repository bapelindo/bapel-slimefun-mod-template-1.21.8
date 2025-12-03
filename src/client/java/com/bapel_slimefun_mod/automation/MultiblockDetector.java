package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ✅ ADVANCED: Intelligent multiblock structure detector
 * 
 * FEATURES:
 * 1. Multi-layer 3D structure scanning
 * 2. Pattern recognition with fuzzy matching
 * 3. Disambiguation for similar structures
 * 4. Confidence scoring system
 */
public class MultiblockDetector {
    
    private static final int SCAN_RADIUS = 4;
    private static final double MIN_CONFIDENCE = 0.6;
    
    /**
     * Detection result with confidence score
     */
    public static class DetectionResult {
        private final String machineId;
        private final double confidence;
        private final Map<String, Integer> matchedBlocks;
        
        public DetectionResult(String machineId, double confidence, Map<String, Integer> matchedBlocks) {
            this.machineId = machineId;
            this.confidence = confidence;
            this.matchedBlocks = matchedBlocks;
        }
        
        public String getMachineId() { return machineId; }
        public double getConfidence() { return confidence; }
        public Map<String, Integer> getMatchedBlocks() { return matchedBlocks; }
        
        @Override
        public String toString() {
            return String.format("%s (%.1f%% confidence)", machineId, confidence * 100);
        }
    }
    
    /**
     * Main detection method
     */
    public static DetectionResult detect(Level level, BlockPos playerPos) {
        // Scan surrounding blocks
        StructureSnapshot snapshot = scanStructure(level, playerPos);
        
        // Get all multiblock machines
        Map<String, SlimefunMachineData> machines = SlimefunDataLoader.getAllMachines();
        List<DetectionResult> candidates = new ArrayList<>();
        
        // Test each multiblock
        for (SlimefunMachineData machine : machines.values()) {
            if (!machine.isMultiblock() || machine.getStructure() == null) continue;
            
            DetectionResult result = matchStructure(machine, snapshot);
            if (result != null && result.getConfidence() >= MIN_CONFIDENCE) {
                candidates.add(result);
            }
        }
        
        // Return best match
        return candidates.stream()
            .max(Comparator.comparingDouble(DetectionResult::getConfidence))
            .orElse(null);
    }
    
    /**
     * Scan structure around position
     */
    private static StructureSnapshot scanStructure(Level level, BlockPos center) {
        Map<String, Integer> blockCounts = new HashMap<>();
        List<BlockPos> dispenserPositions = new ArrayList<>();
        Set<String> uniqueBlocks = new HashSet<>();
        
        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int y = -SCAN_RADIUS; y <= SCAN_RADIUS; y++) {
                for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    Block block = state.getBlock();
                    
                    if (!state.isAir()) {
                        String blockId = normalizeBlockId(block);
                        blockCounts.merge(blockId, 1, Integer::sum);
                        uniqueBlocks.add(blockId);
                        
                        if (block == Blocks.DISPENSER) {
                            dispenserPositions.add(pos);
                        }
                    }
                }
            }
        }
        
        return new StructureSnapshot(blockCounts, dispenserPositions, uniqueBlocks);
    }
    
    /**
     * Match structure against machine definition
     */
    private static DetectionResult matchStructure(SlimefunMachineData machine, StructureSnapshot snapshot) {
        List<SlimefunMachineData.MultiblockStructure> structure = machine.getStructure();
        
        // Build required blocks map
        Map<String, Integer> required = new HashMap<>();
        for (SlimefunMachineData.MultiblockStructure block : structure) {
            String material = normalizeMaterial(block.getMaterial());
            required.merge(material, 1, Integer::sum);
        }
        
        // Calculate match score
        int totalRequired = required.values().stream().mapToInt(Integer::intValue).sum();
        int matched = 0;
        int excess = 0;
        int missing = 0;
        
        Map<String, Integer> matchedBlocks = new HashMap<>();
        
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            String material = entry.getKey();
            int requiredCount = entry.getValue();
            int foundCount = snapshot.blockCounts.getOrDefault(material, 0);
            
            int matchCount = Math.min(requiredCount, foundCount);
            matched += matchCount;
            
            if (matchCount > 0) {
                matchedBlocks.put(material, matchCount);
            }
            
            if (foundCount > requiredCount) {
                excess += (foundCount - requiredCount);
            } else if (foundCount < requiredCount) {
                missing += (requiredCount - foundCount);
            }
        }
        
        // Calculate confidence
        double baseScore = (double) matched / totalRequired;
        double excessPenalty = Math.min(0.3, excess * 0.05);
        double missingPenalty = Math.min(0.5, missing * 0.1);
        
        double confidence = baseScore - excessPenalty - missingPenalty;
        
        // Bonus for exact match
        if (matched == totalRequired && excess == 0) {
            confidence += 0.2;
        }
        
        // Bonus for unique signatures
        confidence += calculateSignatureBonus(machine.getId(), snapshot);
        
        confidence = Math.max(0, Math.min(1, confidence));
        
        return new DetectionResult(machine.getId(), confidence, matchedBlocks);
    }
    
    /**
     * Calculate bonus based on unique block signatures
     */
    private static double calculateSignatureBonus(String machineId, StructureSnapshot snapshot) {
        Map<String, Set<String>> signatures = new HashMap<>();
        
        // Define unique signatures for each machine
        signatures.put("MAGIC_WORKBENCH", Set.of("BOOKSHELF"));
        signatures.put("ENHANCED_CRAFTING_TABLE", Set.of("CRAFTING_TABLE"));
        signatures.put("ORE_CRUSHER", Set.of("IRON_BARS"));
        signatures.put("COMPRESSOR", Set.of("PISTON"));
        signatures.put("SMELTERY", Set.of("NETHER_BRICK_FENCE", "NETHER_BRICK"));
        signatures.put("PRESSURE_CHAMBER", Set.of("GLASS", "PISTON"));
        signatures.put("ARMOR_FORGE", Set.of("ANVIL", "IRON_BLOCK"));
        signatures.put("MAKESHIFT_SMELTERY", Set.of("FURNACE", "BRICK_BLOCK"));
        signatures.put("ORE_WASHER", Set.of("CAULDRON", "FENCE"));
        signatures.put("AUTOMATED_PANNING_MACHINE", Set.of("CAULDRON"));
        signatures.put("GOLD_PAN", Set.of("TRAP_DOOR"));
        signatures.put("JUICER", Set.of("GLASS", "FENCE"));
        signatures.put("ANCIENT_ALTAR", Set.of("ENCHANTMENT_TABLE", "SMOOTH_BRICK"));
        
        Set<String> requiredSignature = signatures.get(machineId);
        if (requiredSignature == null) return 0;
        
        // Check if all signature blocks are present
        boolean hasSignature = requiredSignature.stream()
            .allMatch(snapshot.uniqueBlocks::contains);
        
        return hasSignature ? 0.1 : 0;
    }
    
    /**
     * Normalize Minecraft block ID to Slimefun material name
     */
    private static String normalizeBlockId(Block block) {
        return normalizeMaterial(block.toString());
    }
    
    /**
     * Normalize material name
     */
    private static String normalizeMaterial(String material) {
        // Remove minecraft namespace
        material = material.replace("Block{minecraft:", "")
                         .replace("}", "")
                         .toUpperCase();
        
        // Material name mappings
        Map<String, String> mappings = new HashMap<>();
        mappings.put("CRAFTING_TABLE", "CRAFTING_TABLE");
        mappings.put("BOOKSHELF", "BOOKSHELF");
        mappings.put("DISPENSER", "DISPENSER");
        mappings.put("OAK_FENCE", "FENCE");
        mappings.put("IRON_BARS", "IRON_BARS");
        mappings.put("PISTON", "PISTON");
        mappings.put("CAULDRON", "CAULDRON");
        mappings.put("NETHER_BRICK_FENCE", "NETHER_BRICK_FENCE");
        mappings.put("NETHER_BRICKS", "NETHER_BRICK");
        mappings.put("GLASS", "GLASS");
        mappings.put("ANVIL", "ANVIL");
        mappings.put("CHIPPED_ANVIL", "ANVIL");
        mappings.put("DAMAGED_ANVIL", "ANVIL");
        mappings.put("IRON_BLOCK", "IRON_BLOCK");
        mappings.put("FURNACE", "FURNACE");
        mappings.put("BRICKS", "BRICK_BLOCK");
        mappings.put("OAK_TRAPDOOR", "TRAP_DOOR");
        mappings.put("ENCHANTING_TABLE", "ENCHANTMENT_TABLE");
        mappings.put("STONE_BRICKS", "SMOOTH_BRICK");
        
        return mappings.getOrDefault(material, material);
    }
    
    /**
     * Structure snapshot data
     */
    private static class StructureSnapshot {
        final Map<String, Integer> blockCounts;
        final List<BlockPos> dispenserPositions;
        final Set<String> uniqueBlocks;
        
        StructureSnapshot(Map<String, Integer> blockCounts, 
                         List<BlockPos> dispenserPositions,
                         Set<String> uniqueBlocks) {
            this.blockCounts = blockCounts;
            this.dispenserPositions = dispenserPositions;
            this.uniqueBlocks = uniqueBlocks;
        }
    }
    
    /**
     * Get detailed detection report (for debugging)
     */
    public static String getDetectionReport(DetectionResult result) {
        if (result == null) return "No machine detected";
        
        StringBuilder report = new StringBuilder();
        report.append(String.format("Detected: %s\n", result.getMachineId()));
        report.append(String.format("Confidence: %.1f%%\n", result.getConfidence() * 100));
        report.append("Matched blocks:\n");
        
        result.getMatchedBlocks().forEach((block, count) -> 
            report.append(String.format("  - %s: %d\n", block, count))
        );
        
        return report.toString();
    }
}