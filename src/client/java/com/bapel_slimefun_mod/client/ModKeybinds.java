package com.bapel_slimefun_mod.client;

import com.bapel_slimefun_mod.automation.MachineAutomationHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Handles keybind registration and input
 */
public class ModKeybinds {
    
    // Keybind for toggling automation
    private static KeyMapping toggleAutomationKey;
    
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
        
        // Register tick event to check for key presses
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
        });
    }
    
    /**
     * Get the toggle automation keybind
     */
    public static KeyMapping getToggleAutomationKey() {
        return toggleAutomationKey;
    }
}