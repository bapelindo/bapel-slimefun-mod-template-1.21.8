package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.bapel_slimefun_mod.debug.PerformanceMonitor;

/**
 * OPTIMIZED VERSION - Reduced CPU overhead
 * 
 * Performance improvements:
 * 1. Cached signature block position - find once, reuse
 * 2. Smart block validation - cache block type
 * 3. Reduced Level queries - batch checks
 * 4. Early exit optimization
 */
public class MultiblockAutoClicker {
    
    private static boolean autoClickEnabled = false;
    private static BlockPos dispenserPos = null;
    private static String machineId = null;
    private static long lastClickTime = 0;
    private static final long CLICK_INTERVAL = 1000; // 1 second
    
    private static int targetClickCount = 0;
    private static int currentClickCount = 0;
    
    // OPTIMIZATION: Cache signature block position
    private static BlockPos cachedSignaturePos = null;
    private static Block cachedSignatureBlock = null;
    
    public static void enable(BlockPos pos, String machine, int targetClicks) {
        dispenserPos = pos;
        machineId = machine;
        targetClickCount = targetClicks;
        currentClickCount = 0;
        autoClickEnabled = true;
        
        // OPTIMIZATION: Pre-find signature block position
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level != null) {
            Block signatureBlock = getSignatureBlock(machine);
            if (signatureBlock != null) {
                cachedSignaturePos = findSignatureBlock(level, pos, signatureBlock, machine);
                cachedSignatureBlock = signatureBlock;
            }
        }
        
        BapelSlimefunMod.LOGGER.info("[AutoClick] ✅ ENABLED - Target: {} clicks for {} at {}", 
            targetClicks, machine, pos);
        
        if (mc.player != null) {
            mc.player.displayClientMessage(
                Component.literal(String.format(
                    "§a▶ Auto-Click STARTED - Will click §b%d times",
                    targetClicks
                )),
                false
            );
            mc.player.displayClientMessage(
                Component.literal("§7" + getMachineName(machine)),
                true
            );
        }
    }
    
    public static void disable() {
        if (!autoClickEnabled) return;
        
        autoClickEnabled = false;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String reason = currentClickCount >= targetClickCount ? "Target reached" : "Stopped";
            
            mc.player.displayClientMessage(
                Component.literal(String.format(
                    "§c■ Auto-Click STOPPED - %s (%d/%d clicks)",
                    reason, currentClickCount, targetClickCount
                )),
                false
            );
        }
        
        BapelSlimefunMod.LOGGER.info("[AutoClick] Disabled - Completed {}/{} clicks", 
            currentClickCount, targetClickCount);
        
        // Clear cache
        dispenserPos = null;
        machineId = null;
        targetClickCount = 0;
        currentClickCount = 0;
        cachedSignaturePos = null;
        cachedSignatureBlock = null;
    }
    
    /**
     * OPTIMIZED: Main tick with cached signature position
     */
    public static void tick() {
        PerformanceMonitor.start("AutoClicker.tick");
        try {
            // OPTIMIZATION: Fast-path early exit
            if (!autoClickEnabled || dispenserPos == null || machineId == null) {
                return;
            }
            
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            Level level = mc.level;
            
            if (player == null || level == null) {
                return;
            }
            
            // Check if target reached
            if (currentClickCount >= targetClickCount) {
                BapelSlimefunMod.LOGGER.info("[AutoClick] ✓ Target reached! ({}/{})", 
                    currentClickCount, targetClickCount);
                disable();
                return;
            }
            
            // Check automation status
            if (!UnifiedAutomationManager.isAutomationEnabled()) {
                BapelSlimefunMod.LOGGER.info("[AutoClick] Stopped: automation disabled");
                disable();
                return;
            }
            
            // Check recipe selection
            String selectedRecipe = MultiblockAutomationHandler.getSelectedRecipe();
            if (selectedRecipe == null) {
                BapelSlimefunMod.LOGGER.info("[AutoClick] Stopped: no recipe selected");
                disable();
                return;
            }
            
            // Throttle clicking
            long now = System.currentTimeMillis();
            if (now - lastClickTime < CLICK_INTERVAL) {
                return;
            }
            
            // OPTIMIZATION: Validate cached position before searching
            BlockPos targetPos = cachedSignaturePos;
            
            if (targetPos != null) {
                // Verify cached position is still valid
                Block currentBlock = level.getBlockState(targetPos).getBlock();
                if (!isMatchingBlock(currentBlock, cachedSignatureBlock)) {
                    // Cache invalidated - search again
                    BapelSlimefunMod.LOGGER.warn("[AutoClick] Cached position invalid, searching...");
                    cachedSignaturePos = findSignatureBlock(level, dispenserPos, cachedSignatureBlock, machineId);
                    targetPos = cachedSignaturePos;
                }
            } else {
                // No cache - find signature block
                Block signatureBlock = getSignatureBlock(machineId);
                if (signatureBlock == null) {
                    BapelSlimefunMod.LOGGER.warn("[AutoClick] Unknown signature block for: {}", machineId);
                    disable();
                    return;
                }
                
                cachedSignatureBlock = signatureBlock;
                cachedSignaturePos = findSignatureBlock(level, dispenserPos, signatureBlock, machineId);
                targetPos = cachedSignaturePos;
            }
            
            if (targetPos == null) {
                BapelSlimefunMod.LOGGER.warn("[AutoClick] Signature block not found near {}", dispenserPos);
                return; // Don't disable - might be temporary
            }
            
            // Perform right-click
            boolean success = clickBlock(mc, player, level, targetPos);
            
            if (success) {
                lastClickTime = now;
                currentClickCount++;
                
                BapelSlimefunMod.LOGGER.info("[AutoClick] ✓ Click {}/{} on {} at {}", 
                    currentClickCount, targetClickCount, cachedSignatureBlock, targetPos);
                
                player.displayClientMessage(
                    Component.literal(String.format(
                        "§a✓ Auto-Click: %d/%d",
                        currentClickCount, targetClickCount
                    )),
                    true
                );
                
                if (currentClickCount >= targetClickCount) {
                    BapelSlimefunMod.LOGGER.info("[AutoClick] ✅ All clicks completed!");
                    disable();
                }
            }
        
        } finally {
            PerformanceMonitor.end("AutoClicker.tick");
        }
    }
    
    /**
     * ✅ FIXED: Enhanced search with machine-specific logic
     */
    private static BlockPos findSignatureBlock(Level level, BlockPos center, Block targetBlock, String machineId) {
        // ✅ SPECIAL CASE: PRESSURE_CHAMBER - cauldron is 2 blocks below dispenser
        if ("PRESSURE_CHAMBER".equals(machineId)) {
            BlockPos cauldronPos = center.below(2); // Y-2
            Block block = level.getBlockState(cauldronPos).getBlock();
            if (isMatchingBlock(block, targetBlock)) {
                return cauldronPos;
            }
        }
        
        // OPTIMIZATION: Check center first (most common case)
        Block centerBlock = level.getBlockState(center).getBlock();
        if (isMatchingBlock(centerBlock, targetBlock)) {
            return center;
        }
        
        // OPTIMIZATION: Search in order of most likely positions
        // Check adjacent blocks first (6 directions)
        for (Direction dir : Direction.values()) {
            BlockPos pos = center.relative(dir);
            Block block = level.getBlockState(pos).getBlock();
            if (isMatchingBlock(block, targetBlock)) {
                return pos;
            }
        }
        
        // Then check diagonal and corners (within 3x3x3)
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue; // Skip center (already checked)
                    
                    // Skip direct adjacent (already checked)
                    int absSum = Math.abs(x) + Math.abs(y) + Math.abs(z);
                    if (absSum == 1) continue;
                    
                    BlockPos pos = center.offset(x, y, z);
                    Block block = level.getBlockState(pos).getBlock();
                    
                    if (isMatchingBlock(block, targetBlock)) {
                        return pos;
                    }
                }
            }
        }
        
        // ✅ EXTENDED SEARCH: For machines with signature blocks further away
        // Check Y-2 and Y+2 explicitly (for vertical multiblocks)
        BlockPos down2 = center.below(2);
        if (isMatchingBlock(level.getBlockState(down2).getBlock(), targetBlock)) {
            return down2;
        }
        
        BlockPos up2 = center.above(2);
        if (isMatchingBlock(level.getBlockState(up2).getBlock(), targetBlock)) {
            return up2;
        }
        
        return null;
    }
    
    /**
     * OPTIMIZATION: Cached block type matching
     */
    private static boolean isMatchingBlock(Block actual, Block target) {
        if (actual == target) {
            return true;
        }
        
        // OPTIMIZATION: Use switch for faster lookup
        String targetName = target.toString().toLowerCase();
        String actualName = actual.toString().toLowerCase();
        
        if (targetName.contains("fence")) {
            if (targetName.contains("nether")) {
                return actualName.contains("nether") && actualName.contains("fence");
            } else {
                return actualName.contains("fence") && !actualName.contains("nether");
            }
        }
        
        if (target == Blocks.ANVIL) {
            return actual == Blocks.ANVIL || 
                   actual == Blocks.CHIPPED_ANVIL || 
                   actual == Blocks.DAMAGED_ANVIL;
        }
        
        if (target == Blocks.PISTON) {
            return actual == Blocks.PISTON || actual == Blocks.STICKY_PISTON;
        }
        
        if (targetName.contains("cauldron")) {
            return actualName.contains("cauldron");
        }
        
        return false;
    }
    
    private static boolean clickBlock(Minecraft mc, LocalPlayer player, Level level, BlockPos pos) {
        try {
            Vec3 hitVec = Vec3.atCenterOf(pos);
            Direction direction = Direction.UP;
            
            BlockHitResult hitResult = new BlockHitResult(
                hitVec,
                direction,
                pos,
                false
            );
            
            mc.gameMode.useItemOn(
                player,
                InteractionHand.MAIN_HAND,
                hitResult
            );
            
            return true;
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[AutoClick] Error clicking block", e);
            return false;
        }
    }
    
    private static Block getSignatureBlock(String machineId) {
        switch (machineId.toUpperCase()) {
            case "ARMOR_FORGE":
                return Blocks.ANVIL;
            case "GRIND_STONE":
                return Blocks.OAK_FENCE;
            case "MAGIC_WORKBENCH":
                return Blocks.CRAFTING_TABLE;
            case "ENHANCED_CRAFTING_TABLE":
                return Blocks.CRAFTING_TABLE;
            case "ORE_CRUSHER":
                return Blocks.NETHER_BRICK_FENCE;
            case "COMPRESSOR":
                return Blocks.NETHER_BRICK_FENCE;
            case "SMELTERY":
                return Blocks.NETHER_BRICK_FENCE;
            case "MAKESHIFT_SMELTERY":
                return Blocks.OAK_FENCE;
            case "PRESSURE_CHAMBER":
                return Blocks.CAULDRON; // ✅ This is correct
            case "ORE_WASHER":
                return Blocks.OAK_FENCE;
            case "AUTOMATED_PANNING_MACHINE":
                return Blocks.CAULDRON;
            case "JUICER":
                return Blocks.GLASS;
            case "ANCIENT_ALTAR":
                return Blocks.ENCHANTING_TABLE;
            case "TABLE_SAW":
                return Blocks.SMOOTH_STONE_SLAB;
            default:
                BapelSlimefunMod.LOGGER.warn("[AutoClick] Unknown machine type: {}", machineId);
                return null;
        }
    }
    
    private static String getMachineName(String machineId) {
        if (machineId == null) return "Unknown";
        
        String[] words = machineId.toLowerCase().split("_");
        StringBuilder name = new StringBuilder();
        
        for (String word : words) {
            if (name.length() > 0) name.append(" ");
            if (!word.isEmpty()) {
                name.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    name.append(word.substring(1));
                }
            }
        }
        
        return name.toString();
    }
    
    public static boolean isEnabled() {
        return autoClickEnabled;
    }
    
    public static String getStatus() {
        if (!autoClickEnabled) {
            return "§7Disabled";
        }
        
        return String.format("§aEnabled §7| Machine: §f%s §7| Progress: §b%d§7/§b%d", 
            getMachineName(machineId), currentClickCount, targetClickCount);
    }
    
    public static void forceStop() {
        if (autoClickEnabled) {
            BapelSlimefunMod.LOGGER.info("[AutoClick] Force stopped by user");
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    Component.literal("§e⚠ Auto-Click manually stopped"),
                    false
                );
            }
            
            disable();
        }
    }
}