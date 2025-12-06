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

/**
 * ✅ AUTO-CLICKER dengan AUTO-STOPPER
 * 
 * Features:
 * 1. Otomatis aktif saat user keluar dispenser
 * 2. Click sejumlah calculated click count
 * 3. Auto-stop setelah mencapai target click
 */
public class MultiblockAutoClicker {
    
    private static boolean autoClickEnabled = false;
    private static BlockPos dispenserPos = null;
    private static String machineId = null;
    private static long lastClickTime = 0;
    private static final long CLICK_INTERVAL = 1000; // Click setiap 1 detik
    
    // ✅ AUTO-STOPPER: Target dan current click count
    private static int targetClickCount = 0;
    private static int currentClickCount = 0;
    
    /**
     * EVENT TRIGGER: Enable auto-click dengan target click count
     */
    public static void enable(BlockPos pos, String machine, int targetClicks) {
        dispenserPos = pos;
        machineId = machine;
        targetClickCount = targetClicks;
        currentClickCount = 0;
        autoClickEnabled = true;
        
        BapelSlimefunMod.LOGGER.info("[AutoClick] ✅ ENABLED - Target: {} clicks for {} at {}", 
            targetClicks, machine, pos);
        
        Minecraft mc = Minecraft.getInstance();
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
    
    /**
     * Disable auto-click
     */
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
        
        dispenserPos = null;
        machineId = null;
        targetClickCount = 0;
        currentClickCount = 0;
    }
    
    /**
     * Main tick dengan AUTO-STOPPER
     */
    public static void tick() {
        if (!autoClickEnabled || dispenserPos == null || machineId == null) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        
        if (player == null || level == null) {
            return;
        }
        
        // ✅ AUTO-STOPPER: Cek apakah sudah mencapai target
        if (currentClickCount >= targetClickCount) {
            BapelSlimefunMod.LOGGER.info("[AutoClick] ✓ Target reached! ({}/{})", 
                currentClickCount, targetClickCount);
            disable();
            return;
        }
        
        // Cek apakah automation masih enabled
        if (!UnifiedAutomationManager.isAutomationEnabled()) {
            BapelSlimefunMod.LOGGER.info("[AutoClick] Stopped: automation disabled");
            disable();
            return;
        }
        
        // Cek apakah resep masih dipilih
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
        
        // Find signature block
        Block signatureBlock = getSignatureBlock(machineId);
        if (signatureBlock == null) {
            BapelSlimefunMod.LOGGER.warn("[AutoClick] Unknown signature block for: {}", machineId);
            disable();
            return;
        }
        
        // Search for signature block in 3x3x3 area around dispenser
        BlockPos targetPos = findSignatureBlock(level, dispenserPos, signatureBlock);
        
        if (targetPos == null) {
            BapelSlimefunMod.LOGGER.warn("[AutoClick] Signature block not found near {}", dispenserPos);
            // Jangan langsung disable, mungkin block sementara tidak terdeteksi
            return;
        }
        
        // Perform right-click on signature block
        boolean success = clickBlock(mc, player, level, targetPos);
        
        if (success) {
            lastClickTime = now;
            currentClickCount++;
            
            BapelSlimefunMod.LOGGER.info("[AutoClick] ✓ Click {}/{} on {} at {}", 
                currentClickCount, targetClickCount, signatureBlock, targetPos);
            
            // Show progress
            player.displayClientMessage(
                Component.literal(String.format(
                    "§a✓ Auto-Click: %d/%d",
                    currentClickCount, targetClickCount
                )),
                true
            );
            
            // Check jika sudah selesai
            if (currentClickCount >= targetClickCount) {
                BapelSlimefunMod.LOGGER.info("[AutoClick] ✅ All clicks completed!");
                disable();
            }
        }
    }
    
    /**
     * Find signature block in 3x3x3 area around dispenser
     */
    private static BlockPos findSignatureBlock(Level level, BlockPos center, Block targetBlock) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    Block block = level.getBlockState(pos).getBlock();
                    
                    if (isMatchingBlock(block, targetBlock)) {
                        return pos;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Check if blocks match (handles variants)
     */
    private static boolean isMatchingBlock(Block actual, Block target) {
        if (actual == target) {
            return true;
        }
        
        if (target == Blocks.OAK_FENCE) {
            String blockName = actual.toString().toLowerCase();
            return blockName.contains("fence") && !blockName.contains("nether");
        }
        
        if (target == Blocks.NETHER_BRICK_FENCE) {
            String blockName = actual.toString().toLowerCase();
            return blockName.contains("nether") && blockName.contains("fence");
        }
        
        if (target == Blocks.ANVIL) {
            return actual == Blocks.ANVIL || 
                   actual == Blocks.CHIPPED_ANVIL || 
                   actual == Blocks.DAMAGED_ANVIL;
        }
        
        if (target == Blocks.PISTON) {
            return actual == Blocks.PISTON || actual == Blocks.STICKY_PISTON;
        }
        
        if (target == Blocks.CAULDRON) {
            String blockName = actual.toString().toLowerCase();
            return blockName.contains("cauldron");
        }
        
        return false;
    }
    
    /**
     * Perform right-click on block
     */
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
    
    /**
     * Get signature block for machine type
     */
    private static Block getSignatureBlock(String machineId) {
        switch (machineId.toUpperCase()) {
            case "ARMOR_FORGE":
                return Blocks.ANVIL;
            case "GRIND_STONE":
                return Blocks.OAK_FENCE;
            case "MAGIC_WORKBENCH":
                return Blocks.BOOKSHELF;
            case "ENHANCED_CRAFTING_TABLE":
                return Blocks.CRAFTING_TABLE;
            case "ORE_CRUSHER":
                return Blocks.IRON_BARS;
            case "COMPRESSOR":
                return Blocks.PISTON;
            case "SMELTERY":
                return Blocks.NETHER_BRICK_FENCE;
            case "MAKESHIFT_SMELTERY":
                return Blocks.FURNACE;
            case "PRESSURE_CHAMBER":
                return Blocks.SMOOTH_STONE_SLAB;
            case "ORE_WASHER":
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
    
    /**
     * Get human-readable machine name
     */
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
    
    /**
     * Check if auto-click is enabled
     */
    public static boolean isEnabled() {
        return autoClickEnabled;
    }
    
    /**
     * Get status string
     */
    public static String getStatus() {
        if (!autoClickEnabled) {
            return "§7Disabled";
        }
        
        return String.format("§aEnabled §7| Machine: §f%s §7| Progress: §b%d§7/§b%d", 
            getMachineName(machineId), currentClickCount, targetClickCount);
    }
    
    /**
     * Force stop
     */
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