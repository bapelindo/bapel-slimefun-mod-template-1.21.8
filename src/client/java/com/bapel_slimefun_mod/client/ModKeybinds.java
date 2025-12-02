package com.bapel_slimefun_mod.client;

import com.bapel_slimefun_mod.automation.AutomationManager;
import com.bapel_slimefun_mod.automation.DebugHelper;
import com.bapel_slimefun_mod.automation.MachineAutomationHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Handles keybind registration and input
 * UPDATED: Menambahkan keybind untuk quick start dan recipe selection
 */
public class ModKeybinds {
    
    // Keybind for toggling automation
    private static KeyMapping toggleAutomationKey;
    
    // Keybind for quick start automation
    private static KeyMapping quickStartKey;
    
    // Keybind for opening recipe selection
    private static KeyMapping recipeSelectionKey;
    
    // Keybind for stopping automation
    private static KeyMapping stopAutomationKey;
    
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
        
        // Register quick start key (default: J)
        quickStartKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.bapel-slimefun-mod.quick_start",
            GLFW.GLFW_KEY_J,
            "category.bapel-slimefun-mod.automation"
        ));
        
        // Register recipe selection key (default: U)
        recipeSelectionKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.bapel-slimefun-mod.recipe_selection",
            GLFW.GLFW_KEY_U,
            "category.bapel-slimefun-mod.automation"
        ));
        
        // Register stop automation key (default: H)
        stopAutomationKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.bapel-slimefun-mod.stop_automation",
            GLFW.GLFW_KEY_H,
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
                AutomationManager.toggleAutomation();
            }
            
            // Quick start automation
            while (quickStartKey.consumeClick()) {
                handleQuickStart(client);
            }
            
            // Open recipe selection
            while (recipeSelectionKey.consumeClick()) {
                handleRecipeSelection(client);
            }
            
            // Stop automation
            while (stopAutomationKey.consumeClick()) {
                AutomationManager.stopAutomation(true);
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
     * Handle quick start automation
     */
    private static void handleQuickStart(Minecraft client) {
        if (client.player == null) return;
        
        // Cek apakah ada machine yang terbuka
        if (!MachineAutomationHandler.isActive()) {
            client.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                    "§c[Slimefun] Buka machine terlebih dahulu!"
                ),
                true
            );
            return;
        }
        
        // Cek apakah sudah ada recipe yang dipilih
        String selectedRecipe = MachineAutomationHandler.getSelectedRecipe();
        if (selectedRecipe != null) {
            // Ada recipe, langsung start automation
            AutomationManager.startAutomation(selectedRecipe, true);
        } else {
            // Belum ada recipe, buka selection screen
            client.setScreen(new RecipeSelectionScreen(true)); // true = auto-start setelah pilih
        }
    }
    
    /**
     * Handle recipe selection screen
     */
    private static void handleRecipeSelection(Minecraft client) {
        if (client.player == null) return;
        
        // Cek apakah ada machine yang terbuka
        if (!MachineAutomationHandler.isActive()) {
            client.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                    "§c[Slimefun] Buka machine terlebih dahulu!"
                ),
                true
            );
            return;
        }
        
        // Buka recipe selection screen
        client.setScreen(new RecipeSelectionScreen(true)); // true = auto-start setelah pilih
    }
    
    /**
     * Get the toggle automation keybind
     */
    public static KeyMapping getToggleAutomationKey() {
        return toggleAutomationKey;
    }
    
    /**
     * Get the quick start keybind
     */
    public static KeyMapping getQuickStartKey() {
        return quickStartKey;
    }
    
    /**
     * Get the recipe selection keybind
     */
    public static KeyMapping getRecipeSelectionKey() {
        return recipeSelectionKey;
    }
    
    /**
     * Get the stop automation keybind
     */
    public static KeyMapping getStopAutomationKey() {
        return stopAutomationKey;
    }
    
    /**
     * Get the debug info keybind
     */
    public static KeyMapping getDebugInfoKey() {
        return debugInfoKey;
    }
}