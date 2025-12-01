package com.bapel_slimefun_mod;

import com.bapel_slimefun_mod.automation.*;
import com.bapel_slimefun_mod.config.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main mod class for Bapel Slimefun Mod
 * COMPLETE VERSION - Replace your entire BapelSlimefunMod.java with this file
 */
public class BapelSlimefunMod implements ClientModInitializer {
    public static final String MOD_ID = "bapel-slimefun-mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    // Config
    private static ModConfig config;
    
    // Keybindings
    private static KeyMapping toggleAutomationKey;
    private static KeyMapping debugInfoKey;
    private static KeyMapping toggleRecipeOverlayKey; // NEW
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Bapel Slimefun Mod");
        
        // Load configuration
        config = ModConfig.load();
        LOGGER.info("Configuration loaded: {}", config);
        
        // Initialize systems
        initializeSystems();
        
        // Register keybindings
        registerKeybindings();
        
        // Register event handlers
        registerEventHandlers();
        
        LOGGER.info("Bapel Slimefun Mod initialized successfully");
    }
    
    /**
     * Initialize all mod systems
     */
    private void initializeSystems() {
        try {
            // Initialize item registry
            //ItemRegistry.initialize();
            LOGGER.info("✓ Item Registry initialized");
            
            // Initialize Slimefun data loader
            SlimefunDataLoader.loadData();
            LOGGER.info("✓ Slimefun data loaded");
            
            // Initialize recipe database (NEW)
            RecipeDatabase.initialize();
            LOGGER.info("✓ Recipe Database initialized");
            
            // Initialize recipe overlay renderer (NEW)
            RecipeOverlayRenderer.initialize();
            LOGGER.info("✓ Recipe Overlay Renderer initialized");
            
            // Initialize machine automation handler
            MachineAutomationHandler.init(config);
            LOGGER.info("✓ Machine Automation Handler initialized");
            
            LOGGER.info("All systems initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Error during system initialization", e);
        }
    }
    
    /**
     * Register keybindings
     */
    private void registerKeybindings() {
        try {
            // Existing keybinding: Toggle automation
            toggleAutomationKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.bapel-slimefun-mod.toggle_automation",
                GLFW.GLFW_KEY_G,
                "category.bapel-slimefun-mod.automation"
            ));
            
            // Existing keybinding: Debug info
            debugInfoKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.bapel-slimefun-mod.debug_info",
                GLFW.GLFW_KEY_F3,
                "category.bapel-slimefun-mod.automation"
            ));
            
            // NEW: Recipe overlay keybinding
            toggleRecipeOverlayKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.bapel-slimefun-mod.toggle_recipe_overlay",
                GLFW.GLFW_KEY_R,
                "category.bapel-slimefun-mod.automation"
            ));
            
            LOGGER.info("✓ Keybindings registered");
        } catch (Exception e) {
            LOGGER.error("Error registering keybindings", e);
        }
    }
    
    /**
     * Register event handlers
     */
    private void registerEventHandlers() {
        try {
            // NEW: Register HUD render callback for overlay
            HudRenderCallback.EVENT.register((context, tickDelta) -> {
                try {
                    RecipeOverlayRenderer.render(context, tickDelta.getGameTimeDeltaPartialTick(true));
                } catch (Exception e) {
                    LOGGER.error("Error rendering recipe overlay", e);
                }
            });
            
            // Register client tick callback for keybind handling
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                try {
                    // Handle toggle automation
                    while (toggleAutomationKey.consumeClick()) {
                        MachineAutomationHandler.toggleAutomation();
                    }
                    
                    // Handle debug info
                    while (debugInfoKey.consumeClick()) {
                        if (config.isDebugMode()) {
                            DebugHelper.runFullDiagnostic();
                        } else {
                            LOGGER.info("Debug mode is disabled. Enable in config to use.");
                        }
                    }
                    
                    // NEW: Handle recipe overlay toggle
                    while (toggleRecipeOverlayKey.consumeClick()) {
                        RecipeOverlayRenderer.toggle();
                    }
                    
                    // Automation tick
                    MachineAutomationHandler.tick();
                    
                } catch (Exception e) {
                    LOGGER.error("Error in client tick handler", e);
                }
            });
            
            LOGGER.info("✓ Event handlers registered");
        } catch (Exception e) {
            LOGGER.error("Error registering event handlers", e);
        }
    }
    
    /**
     * Get mod configuration
     */
    public static ModConfig getConfig() {
        return config;
    }
    
    /**
     * Save configuration to file
     */
    public static void saveConfig() {
        if (config != null) {
            config.save();
        }
    }
}
