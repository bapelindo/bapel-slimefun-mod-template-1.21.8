package com.bapel_slimefun_mod.client;

// import com.bapel_slimefun_mod.automation.AutomationManager;
import com.bapel_slimefun_mod.automation.MachineAutomationHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Handles keybind registration and input
 * SIMPLIFIED: Hanya 3 keybind utama - K (toggle), J (debug), R (recipe overlay)
 */
public class ModKeybinds {
    
    // K = Toggle automation on/off
    private static KeyMapping toggleAutomationKey;
    
    // J = Debug info
    private static KeyMapping debugInfoKey;
    
    // R = Toggle recipe overlay (handled in mixin)
    private static KeyMapping recipeOverlayKey;
    
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
        
        // Register tick event to check for key presses
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // K = Toggle automation
            while (toggleAutomationKey.consumeClick()) {
                AutomationManager.toggleAutomation();
            }
            
            // J = Debug info
            while (debugInfoKey.consumeClick()) {
                if (MachineAutomationHandler.getConfig() != null && 
                    MachineAutomationHandler.getConfig().isDebugMode()) {
                    
                    MachineAutomationHandler.runFullDiagnostic();
                    
                    if (client.player != null) {
                        client.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(
                                "§6[Slimefun] §fDebug info printed to log"
                            ),
                            true
                        );
                    }
                } else {
                    if (client.player != null) {
                        client.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(
                                "§6[Slimefun] §cDebug mode is disabled"
                            ),
                            true
                        );
                    }
                }
            }
            
            // R = Recipe overlay (handled in ContainerScreenMixin)
            // No need to handle here, mixin will catch it
        });
    }
    
    /**
     * Get the toggle automation keybind (K)
     */
    public static KeyMapping getToggleAutomationKey() {
        return toggleAutomationKey;
    }
    
    /**
     * Get the debug info keybind (J)
     */
    public static KeyMapping getDebugInfoKey() {
        return debugInfoKey;
    }
    
    /**
     * Get the recipe overlay keybind (R)
     */
    public static KeyMapping getRecipeOverlayKey() {
        return recipeOverlayKey;
    }
}