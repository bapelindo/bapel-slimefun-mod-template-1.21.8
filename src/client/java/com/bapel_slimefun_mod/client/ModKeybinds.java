package com.bapel_slimefun_mod.client;

import com.bapel_slimefun_mod.automation.DebugHelper;
import com.bapel_slimefun_mod.automation.MachineAutomationHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Handles keybind registration and input (with debug key)
 */
public class ModKeybinds {
    
    // Keybind for toggling automation
    private static KeyMapping toggleAutomationKey;
    
    // Keybind for debug info (Ctrl+D)
    private static KeyMapping debugInfoKey;
    
    /**
     * Register all keybinds
     */
    public static void register() {
        // Register toggle automation key (default: K)
        toggleAutomationKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.bapel-slimefun-mod.toggle_automation",
            GLFW.GLFW_KEY_K,
            "category.bapel-slimefun-mod.automation"
        ));
        
        // Register debug info key (default: L)
        debugInfoKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.bapel-slimefun-mod.debug_info",
            GLFW.GLFW_KEY_L,
            "category.bapel-slimefun-mod.automation"
        ));
        
        // Register tick event to check for key presses
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Toggle automation
            while (toggleAutomationKey.consumeClick()) {
                MachineAutomationHandler.toggleAutomation();
                
                // Send feedback to player
                if (client.player != null) {
                    String status = MachineAutomationHandler.isAutomationEnabled() 
                        ? "§aEnabled" : "§cDisabled";
                    client.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                            "§6[Slimefun] §fAutomation: " + status
                        ),
                        true // Show as action bar (above hotbar)
                    );
                }
            }
            
            // Debug info
            while (debugInfoKey.consumeClick()) {
                if (MachineAutomationHandler.getConfig() != null && 
                    MachineAutomationHandler.getConfig().isDebugMode()) {
                    
                    // Run full diagnostic
                    DebugHelper.runFullDiagnostic();
                    
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
        });
    }
    
    /**
     * Get the toggle automation keybind
     */
    public static KeyMapping getToggleAutomationKey() {
        return toggleAutomationKey;
    }
    
    /**
     * Get the debug info keybind
     */
    public static KeyMapping getDebugInfoKey() {
        return debugInfoKey;
    }
}