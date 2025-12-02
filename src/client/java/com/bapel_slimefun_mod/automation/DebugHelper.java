package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.config.ModConfig;
import net.minecraft.client.Minecraft;

public class DebugHelper {
    
    public static void runFullDiagnostic() {
        BapelSlimefunMod.LOGGER.info("╔════════════════════════════════════════════════════════╗");
        BapelSlimefunMod.LOGGER.info("║                   FULL DIAGNOSTIC                       ║");
        BapelSlimefunMod.LOGGER.info("╠════════════════════════════════════════════════════════╣");
        
        // 1. Config status
        ModConfig config = BapelSlimefunMod.getConfig();
        BapelSlimefunMod.LOGGER.info("║ CONFIG:");
        if (config != null) {
            BapelSlimefunMod.LOGGER.info("║   Automation: {}", config.isAutomationEnabled());
            BapelSlimefunMod.LOGGER.info("║   Delay: {}ms", config.getAutomationDelayMs());
            BapelSlimefunMod.LOGGER.info("║   Debug: {}", config.isDebugMode());
            BapelSlimefunMod.LOGGER.info("║   Auto Show Overlay: {}", config.isAutoShowOverlay());
        } else {
            BapelSlimefunMod.LOGGER.info("║   NULL - CONFIG NOT LOADED!");
        }
        
        // 2. Machine status
        BapelSlimefunMod.LOGGER.info("║");
        BapelSlimefunMod.LOGGER.info("║ MACHINE:");
        SlimefunMachineData machine = MachineAutomationHandler.getCurrentMachine();
        if (machine != null) {
            BapelSlimefunMod.LOGGER.info("║   Name: {}", machine.getName());
            BapelSlimefunMod.LOGGER.info("║   ID: {}", machine.getId());
            BapelSlimefunMod.LOGGER.info("║   Input Slots: {}", machine.getInputSlots().length);
            BapelSlimefunMod.LOGGER.info("║   Output Slots: {}", machine.getOutputSlots().length);
            BapelSlimefunMod.LOGGER.info("║   Has Recipe: {}", !machine.getRecipe().isEmpty());
        } else {
            BapelSlimefunMod.LOGGER.info("║   NULL - No machine detected");
        }
        
        // 3. Automation status
        BapelSlimefunMod.LOGGER.info("║");
        BapelSlimefunMod.LOGGER.info("║ AUTOMATION:");
        BapelSlimefunMod.LOGGER.info("║   Active: {}", MachineAutomationHandler.isActive());
        BapelSlimefunMod.LOGGER.info("║   Enabled: {}", MachineAutomationHandler.isAutomationEnabled());
        BapelSlimefunMod.LOGGER.info("║   Selected Recipe: {}", 
            MachineAutomationHandler.getSelectedRecipe() != null ? 
            MachineAutomationHandler.getSelectedRecipe() : "none");
        BapelSlimefunMod.LOGGER.info("║   Cached Requirements: {}", 
            MachineAutomationHandler.getCachedRecipeRequirements().size());
        
        // 4. Overlay status
        BapelSlimefunMod.LOGGER.info("║");
        BapelSlimefunMod.LOGGER.info("║ OVERLAY:");
        BapelSlimefunMod.LOGGER.info("║   Visible: {}", RecipeOverlayRenderer.isVisible());
        BapelSlimefunMod.LOGGER.info("║   Available Recipes: {}", 
            RecipeOverlayRenderer.getAvailableRecipes().size());
        BapelSlimefunMod.LOGGER.info("║   Selected Index: {}", RecipeOverlayRenderer.getSelectedIndex());
        
        // 5. Database status
        BapelSlimefunMod.LOGGER.info("║");
        BapelSlimefunMod.LOGGER.info("║ DATABASE:");
        BapelSlimefunMod.LOGGER.info("║   Initialized: {}", RecipeDatabase.isInitialized());
        BapelSlimefunMod.LOGGER.info("║   Total Recipes: {}", RecipeDatabase.getTotalRecipes());        
        // 6. Player/Client status
        BapelSlimefunMod.LOGGER.info("║");
        BapelSlimefunMod.LOGGER.info("║ CLIENT:");
        Minecraft mc = Minecraft.getInstance();
        BapelSlimefunMod.LOGGER.info("║   Player: {}", mc.player != null ? "present" : "null");
        BapelSlimefunMod.LOGGER.info("║   Screen: {}", mc.screen != null ? mc.screen.getClass().getSimpleName() : "null");
        BapelSlimefunMod.LOGGER.info("║   Container Menu: {}", 
            mc.player != null && mc.player.containerMenu != null ? "present" : "null");
        
        BapelSlimefunMod.LOGGER.info("╚════════════════════════════════════════════════════════╝");
    }
    
    public static void logKeyPress(String key) {
        BapelSlimefunMod.LOGGER.info("╔════════════════════════════════════════════════════════╗");
        BapelSlimefunMod.LOGGER.info("║  KEY PRESS: {}", key);
        BapelSlimefunMod.LOGGER.info("║  Overlay Visible: {}", RecipeOverlayRenderer.isVisible());
        BapelSlimefunMod.LOGGER.info("║  In Container: {}", 
            Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen);
        BapelSlimefunMod.LOGGER.info("╚════════════════════════════════════════════════════════╝");
    }
}
