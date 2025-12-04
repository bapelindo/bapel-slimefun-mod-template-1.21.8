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
 * ✅ Auto-Clicker for Multiblock Machines
 * 
 * After user exits dispenser GUI with recipe selected,
 * this will automatically right-click the signature block
 * to process the recipe.
 * 
 * Signature blocks by machine:
 * - ARMOR_FORGE: Anvil
 * - GRIND_STONE: Fence
 * - MAGIC_WORKBENCH: Bookshelf
 * - ENHANCED_CRAFTING_TABLE: Crafting Table
 * - ORE_CRUSHER: Iron Bars
 * - COMPRESSOR: Piston
 * - SMELTERY: Nether Brick Fence
 * - PRESSURE_CHAMBER: Smooth Stone Slab
 * - ORE_WASHER: Cauldron
 * - JUICER: Glass
 * - etc.
 */
public class MultiblockAutoClicker {
    
    private static boolean autoClickEnabled = false;
    private static BlockPos dispenserPos = null;
    private static String machineId = null;
    private static long lastClickTime = 0;
    private static final long CLICK_INTERVAL = 1000; // Click every 1 second
    private static int clickCount = 0;
    
    /**
     * Enable auto-click for a multiblock machine
     * Called when user exits dispenser GUI with recipe selected
     */
    public static void enable(BlockPos pos, String machine) {
        dispenserPos = pos;
        machineId = machine;
        autoClickEnabled = true;
        clickCount = 0;
        
        BapelSlimefunMod.LOGGER.info("[AutoClick] Enabled for {} at {}", machine, pos);
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                Component.literal("§a▶ Auto-Click STARTED - " + getMachineName(machine)),
                false
            );
            mc.player.displayClientMessage(
                Component.literal("§7Will auto-click " + getSignatureBlockName(machine)),
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
            mc.player.displayClientMessage(
                Component.literal("§c■ Auto-Click STOPPED - Total clicks: " + clickCount),
                false
            );
        }
        
        BapelSlimefunMod.LOGGER.info("[AutoClick] Disabled - Total clicks: {}", clickCount);
        
        dispenserPos = null;
        machineId = null;
        clickCount = 0;
    }
    
    /**
     * Toggle auto-click on/off
     */
    public static void toggle() {
        if (autoClickEnabled) {
            disable();
        } else {
            // Can't enable without position/machine - show message
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    Component.literal("§e[AutoClick] Exit dispenser with recipe selected first!"),
                    true
                );
            }
        }
    }
    
    /**
     * Main tick - called every game tick
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
            disable();
            return;
        }
        
        // Perform right-click on signature block
        boolean success = clickBlock(mc, player, level, targetPos);
        
        if (success) {
            lastClickTime = now;
            clickCount++;
            
            BapelSlimefunMod.LOGGER.debug("[AutoClick] Clicked {} at {} (count: {})", 
                signatureBlock, targetPos, clickCount);
        }
    }
    
    /**
     * Find signature block in 3x3x3 area around dispenser
     */
    private static BlockPos findSignatureBlock(Level level, BlockPos center, Block targetBlock) {
        // Search in 3x3x3 cube
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
     * Check if blocks match (handles variants like fence types)
     */
    private static boolean isMatchingBlock(Block actual, Block target) {
        // Exact match
        if (actual == target) {
            return true;
        }
        
        // Fence variants (all wood fences match)
        if (target == Blocks.OAK_FENCE) {
            String blockName = actual.toString().toLowerCase();
            return blockName.contains("fence") && !blockName.contains("nether");
        }
        
        // Nether Brick Fence variants
        if (target == Blocks.NETHER_BRICK_FENCE) {
            String blockName = actual.toString().toLowerCase();
            return blockName.contains("nether") && blockName.contains("fence");
        }
        
        // Anvil variants (normal, chipped, damaged)
        if (target == Blocks.ANVIL) {
            return actual == Blocks.ANVIL || 
                   actual == Blocks.CHIPPED_ANVIL || 
                   actual == Blocks.DAMAGED_ANVIL;
        }
        
        // Piston variants (normal, sticky)
        if (target == Blocks.PISTON) {
            return actual == Blocks.PISTON || actual == Blocks.STICKY_PISTON;
        }
        
        // Cauldron variants (empty, water, lava)
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
            // Create hit result (simulate player looking at block)
            Vec3 hitVec = Vec3.atCenterOf(pos);
            Direction direction = Direction.UP; // Default to top face
            
            BlockHitResult hitResult = new BlockHitResult(
                hitVec,
                direction,
                pos,
                false
            );
            
            // Perform right-click interaction
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
                return Blocks.OAK_FENCE; // Any fence
            
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
     * Get signature block name for display
     */
    private static String getSignatureBlockName(String machineId) {
        Block block = getSignatureBlock(machineId);
        if (block == null) return "Unknown";
        
        // Format block name
        String blockName = block.toString()
            .replace("Block{minecraft:", "")
            .replace("}", "")
            .replace("_", " ");
        
        // Capitalize first letter of each word
        String[] words = blockName.split(" ");
        StringBuilder formatted = new StringBuilder();
        
        for (String word : words) {
            if (formatted.length() > 0) formatted.append(" ");
            if (!word.isEmpty()) {
                formatted.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    formatted.append(word.substring(1).toLowerCase());
                }
            }
        }
        
        return formatted.toString();
    }
    
    /**
     * Check if auto-click is enabled
     */
    public static boolean isEnabled() {
        return autoClickEnabled;
    }
    
    /**
     * Get current click count
     */
    public static int getClickCount() {
        return clickCount;
    }
    
    /**
     * Get status string for debugging
     */
    public static String getStatus() {
        if (!autoClickEnabled) {
            return "§7Disabled";
        }
        
        return String.format("§aEnabled §7| Machine: §f%s §7| Clicks: §b%d", 
            getMachineName(machineId), clickCount);
    }
}