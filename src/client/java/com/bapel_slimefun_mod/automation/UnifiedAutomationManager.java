package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Unified automation manager that handles both electric and multiblock machines
 */
public class UnifiedAutomationManager {
    
    private static ModConfig config;
    private static SlimefunMachineData currentMachine = null;
    private static boolean automationEnabled = false;
    private static long lastTickTime = 0;
    
    public static void init(ModConfig cfg) {
        config = cfg;
        MachineAutomationHandler.init(cfg);
        MultiblockAutomationHandler.init(cfg);
        BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Initialized");
    }
    
    /**
     * Called when a machine GUI is opened
     */
    public static void onMachineOpen(String title) {
        currentMachine = SlimefunDataLoader.getMachineByTitle(title);
        
        if (currentMachine != null) {
            BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Detected {} machine: {}", 
                currentMachine.isMultiblock() ? "MULTIBLOCK" : "ELECTRIC",
                currentMachine.getId());
            
            if (currentMachine.isElectric()) {
                // Delegate to electric handler
                MachineAutomationHandler.onContainerOpen(title);
            } else if (currentMachine.isMultiblock()) {
                // Multiblock machines don't have GUIs in the same way
                // Show recipe overlay
                if (config != null && config.isAutoShowOverlay()) {
                    try {
                        RecipeOverlayRenderer.show(currentMachine);
                    } catch (Exception e) {
                        BapelSlimefunMod.LOGGER.error("[UnifiedAuto] Failed to show overlay", e);
                    }
                }
            }
        }
    }
    
    /**
     * Called when machine GUI is closed
     */
    public static void onMachineClose() {
        if (currentMachine != null) {
            BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Closing machine: {}", currentMachine.getId());
            
            if (currentMachine.isElectric()) {
                MachineAutomationHandler.onContainerClose();
            } else if (currentMachine.isMultiblock()) {
                MultiblockAutomationHandler.reset();
            }
        }
        
        currentMachine = null;
    }
    
    /**
     * Main tick handler - routes to appropriate handler
     */
    public static void tick() {
        if (!automationEnabled || currentMachine == null) return;
        
        // Throttle ticks
        long now = System.currentTimeMillis();
        if (config != null && now - lastTickTime < 50) { // 50ms = 1 tick
            return;
        }
        lastTickTime = now;
        
        if (currentMachine.isElectric()) {
            // Electric machines handled by MachineAutomationHandler
            MachineAutomationHandler.tick();
        } else if (currentMachine.isMultiblock()) {
            // Multiblock machines
            MultiblockAutomationHandler.tick(currentMachine);
        }
    }
    
    /**
     * Set selected recipe (works for both types)
     */
    public static void setSelectedRecipe(String recipeId) {
        if (currentMachine == null) return;
        
        BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Setting recipe: {} for {}", 
            recipeId, currentMachine.getId());
        
        if (currentMachine.isElectric()) {
            MachineAutomationHandler.setSelectedRecipe(recipeId);
        } else if (currentMachine.isMultiblock()) {
            MultiblockAutomationHandler.setSelectedRecipe(recipeId);
        }
        
        // Show confirmation
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String displayName = getRecipeDisplayName(recipeId);
            mc.player.displayClientMessage(
                Component.literal("§aRecipe selected: " + displayName), 
                true
            );
        }
    }
    
    /**
     * Get selected recipe
     */
    public static String getSelectedRecipe() {
        if (currentMachine == null) return null;
        
        if (currentMachine.isElectric()) {
            return MachineAutomationHandler.getSelectedRecipe();
        } else if (currentMachine.isMultiblock()) {
            return MultiblockAutomationHandler.getSelectedRecipe();
        }
        
        return null;
    }
    
    /**
     * Toggle automation on/off
     */
    public static void toggleAutomation() {
        automationEnabled = !automationEnabled;
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        if (player != null) {
            if (automationEnabled) {
                player.displayClientMessage(
                    Component.literal("§a[Slimefun] Automation STARTED ▶"), 
                    false
                );
                BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Automation ENABLED");
            } else {
                player.displayClientMessage(
                    Component.literal("§c[Slimefun] Automation STOPPED ■"), 
                    false
                );
                BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Automation DISABLED");
            }
        }
        
        // Also update electric handler
        if (config != null) {
            config.setAutomationEnabled(automationEnabled);
        }
        MachineAutomationHandler.setAutomationEnabled(automationEnabled);
    }
    
    /**
     * Check if automation is enabled
     */
    public static boolean isAutomationEnabled() {
        return automationEnabled;
    }
    
    /**
     * Get current machine
     */
    public static SlimefunMachineData getCurrentMachine() {
        return currentMachine;
    }
    
    /**
     * Check if currently in a machine
     */
    public static boolean isActive() {
        return currentMachine != null;
    }
    
    /**
     * Get recipe summary for current machine
     */
    public static RecipeHandler.RecipeSummary getRecipeSummary() {
        if (currentMachine == null) return null;
        
        if (currentMachine.isElectric()) {
            return MachineAutomationHandler.getRecipeSummary();
        } else if (currentMachine.isMultiblock()) {
            return MultiblockAutomationHandler.getRecipeSummary(currentMachine);
        }
        
        return null;
    }
    
    /**
     * Get display name for recipe
     * FIXED: Use proper string manipulation without lambda
     */
    private static String getRecipeDisplayName(String recipeId) {
        RecipeData recipe = RecipeDatabase.getRecipe(recipeId);
        if (recipe == null) return recipeId;
        
        // FIXED: Get outputs properly
        List<RecipeData.RecipeOutput> outputs = recipe.getOutputs();
        if (outputs.isEmpty()) return recipeId;
        
        RecipeData.RecipeOutput output = outputs.get(0);
        String itemId = output.getItemId();
        
        // Convert ITEM_ID to "Item Id" format
        String[] words = itemId.toLowerCase().split("_");
        StringBuilder displayName = new StringBuilder();
        
        for (String word : words) {
            if (displayName.length() > 0) {
                displayName.append(" ");
            }
            if (word.length() > 0) {
                // Capitalize first letter manually
                displayName.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    displayName.append(word.substring(1));
                }
            }
        }
        
        return displayName.toString();
    }
    
    /**
     * Debug info
     */
    public static void printDebugInfo() {
        BapelSlimefunMod.LOGGER.info("╔═══════════════════════════════════════╗");
        BapelSlimefunMod.LOGGER.info("║  UNIFIED AUTOMATION STATUS            ║");
        BapelSlimefunMod.LOGGER.info("╠═══════════════════════════════════════╣");
        BapelSlimefunMod.LOGGER.info("║  Enabled: {}", automationEnabled);
        BapelSlimefunMod.LOGGER.info("║  Active: {}", isActive());
        
        if (currentMachine != null) {
            BapelSlimefunMod.LOGGER.info("║  Machine: {} ({})", 
                currentMachine.getId(),
                currentMachine.isMultiblock() ? "MULTIBLOCK" : "ELECTRIC");
            BapelSlimefunMod.LOGGER.info("║  Selected Recipe: {}", getSelectedRecipe());
        } else {
            BapelSlimefunMod.LOGGER.info("║  Machine: none");
        }
        
        BapelSlimefunMod.LOGGER.info("╚═══════════════════════════════════════╝");
    }
}
