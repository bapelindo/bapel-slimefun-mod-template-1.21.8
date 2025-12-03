package com.bapel_slimefun_mod.client;

import com.bapel_slimefun_mod.automation.MachineAutomationHandler;
import com.bapel_slimefun_mod.client.gui.AutomationModeScreen;
import com.bapel_slimefun_mod.config.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Handles keybind registration and input
 * K = Toggle automation, J = Debug info, R = Recipe overlay, M = Mode settings
 */
public class ModKeybinds {
    
    // K = Toggle automation on/off
    private static KeyMapping toggleAutomationKey;
    
    // J = Debug info
    private static KeyMapping debugInfoKey;
    
    // R = Toggle recipe overlay (handled in mixin)
    private static KeyMapping recipeOverlayKey;
    
    // M = Open mode settings
    private static KeyMapping modeSettingsKey;
    
    /**
     * Register all keybinds
     */
    public static void register() {
        // K = Toggle automation on/off
        toggleAutomationKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.bapel-slimefun-mod.toggle_automation",
            GLFW.GLFW_KEY_K,
            "category.bapel-slimefun-mod.automation"
        ));
        
        // J = Debug info
        debugInfoKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.bapel-slimefun-mod.debug_info",
            GLFW.GLFW_KEY_J,
            "category.bapel-slimefun-mod.automation"
        ));
        
        // R = Toggle recipe overlay
        recipeOverlayKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.bapel-slimefun-mod.recipe_overlay",
            GLFW.GLFW_KEY_R,
            "category.bapel-slimefun-mod.automation"
        ));
        
        // M = Open mode settings
        modeSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.bapel-slimefun-mod.mode_settings",
            GLFW.GLFW_KEY_M,
            "category.bapel-slimefun-mod.automation"
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
        
        // J = Debug info
        while (debugInfoKey.consumeClick()) {
            handleDebugInfo();
        }
        
        // M = Mode settings
        while (modeSettingsKey.consumeClick()) {
            handleModeSettings(mc);
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
            mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§eAutomation: " + status), 
                true
            );
        }
    }
    
    /**
     * Handle J = Debug info
     */
    private static void handleDebugInfo() {
        AutomationManager.showDetailedStatus();
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
     * Get the debug info keybind (for mixin access)
     */
    public static KeyMapping getDebugInfoKey() {
        return debugInfoKey;
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
}