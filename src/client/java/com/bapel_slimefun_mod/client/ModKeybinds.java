package com.bapel_slimefun_mod.client;

import com.bapel_slimefun_mod.automation.MachineAutomationHandler;
import com.bapel_slimefun_mod.client.gui.AutomationModeScreen;
import com.bapel_slimefun_mod.config.ModConfig;
import com.bapel_slimefun_mod.debug.PerformanceMonitor;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Handles keybind registration and input
 * K = Toggle automation, R = Recipe overlay, M = Mode settings, F3 = Performance Monitor
 */
public class ModKeybinds {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("bapel-slimefun-mod", "automation")
    );

    // K = Toggle automation on/off
    private static KeyMapping toggleAutomationKey;
    
    // R = Toggle recipe overlay (handled in mixin)
    private static KeyMapping recipeOverlayKey;
    
    // M = Open mode settings
    private static KeyMapping modeSettingsKey;
    
    // F3 = Toggle performance monitor
    private static KeyMapping performanceMonitorKey;
    
    /**
     * Register all keybinds
     */
    public static void register() {
        // K = Toggle automation on/off
        toggleAutomationKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bapel-slimefun-mod.toggle_automation",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY
        ));

        // R = Toggle recipe overlay
        recipeOverlayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bapel-slimefun-mod.recipe_overlay",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY
        ));

        // M = Open mode settings
        modeSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bapel-slimefun-mod.mode_settings",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY
        ));

        // F8 = Toggle performance monitor
        performanceMonitorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bapel-slimefun-mod.performance_monitor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            CATEGORY
        ));
        
        // Register tick event to handle key presses
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleKeyPresses(client);
        });
    }
    
    /**
     * Handle all key presses
     */
    private static void handleKeyPresses(Minecraft mc) {
        // K = Toggle automation
        while (toggleAutomationKey.consumeClick()) {
            handleToggleAutomation();
        }
        
        // M = Mode settings
        while (modeSettingsKey.consumeClick()) {
            handleModeSettings(mc);
        }
        
        // F3 = Performance monitor
        while (performanceMonitorKey.consumeClick()) {
            handlePerformanceMonitor();
        }
        
        // R is handled in RecipeOverlayInputHandler mixin
    }
    
    /**
     * Handle K = Toggle automation
     */
    private static void handleToggleAutomation() {
        if (!MachineAutomationHandler.isActive()) {
            return;
        }
        
        boolean currentState = MachineAutomationHandler.isAutomationEnabled();
        MachineAutomationHandler.setAutomationEnabled(!currentState);
        
        String status = !currentState ? "§aENABLED" : "§cDISABLED";
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(
                net.minecraft.network.chat.Component.literal("§eAutomation: " + status));
        }
    }
    
    /**
     * Handle M = Mode settings
     */
    private static void handleModeSettings(Minecraft mc) {
        if (mc.player != null) {
            ModConfig config = ModConfig.load();
            mc.setScreen(new AutomationModeScreen(mc.screen, config));
        }
    }
    
    /**
     * Handle F3 = Performance monitor toggle
     */
    private static void handlePerformanceMonitor() {
        PerformanceMonitor.toggle();
        
        String status = PerformanceMonitor.isVisible() ? "§aON" : "§cOFF";
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(
                net.minecraft.network.chat.Component.literal("§ePerformance Monitor: " + status));
        }
    }
    
    // ========================================
    // GETTER METHODS (for Mixin access)
    // ========================================
    
    /**
     * Get the toggle automation keybind (for mixin access)
     */
    public static KeyMapping getToggleAutomationKey() {
        return toggleAutomationKey;
    }
    
    /**
     * Get the recipe overlay keybind (for mixin access)
     */
    public static KeyMapping getRecipeOverlayKey() {
        return recipeOverlayKey;
    }
    
    /**
     * Get the mode settings keybind (for mixin access)
     */
    public static KeyMapping getModeSettingsKey() {
        return modeSettingsKey;
    }
    
    /**
     * Get the performance monitor keybind (for mixin access)
     */
    public static KeyMapping getPerformanceMonitorKey() {
        return performanceMonitorKey;
    }
}