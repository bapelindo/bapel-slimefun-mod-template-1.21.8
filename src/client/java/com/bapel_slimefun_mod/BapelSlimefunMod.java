package com.bapel_slimefun_mod;

import com.bapel_slimefun_mod.automation.*;
import com.bapel_slimefun_mod.client.DebugOverlay;
import com.bapel_slimefun_mod.client.ModKeybinds;
import com.bapel_slimefun_mod.config.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BapelSlimefunMod implements ClientModInitializer {
    public static final String MOD_ID = "bapel-slimefun-mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static ModConfig config;
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Bapel Slimefun Mod");
        
        config = ModConfig.load();
        LOGGER.info("Configuration loaded: {}", config);
        
        initializeSystems();
        ModKeybinds.register();
        registerEventHandlers();
        
        LOGGER.info("Bapel Slimefun Mod initialized successfully");
    }
    
    private void initializeSystems() {
        try {
            ItemRegistry.initialize();
            LOGGER.info("✓ Item Registry initialized");
            
            SlimefunDataLoader.loadData();
            LOGGER.info("✓ Slimefun data loaded");
            
            RecipeDatabase.initialize();
            LOGGER.info("✓ Recipe Database initialized");

            RecipeDatabase.printAllMachineRecipes();
            RecipeDatabase.debugMachineRecipes("ELECTRIC_INGOT_FACTORY_3");
            
            RecipeOverlayRenderer.initialize();
            LOGGER.info("✓ Recipe Overlay Renderer initialized");
            
            DebugOverlay.register();
            LOGGER.info("✓ Debug Overlay registered");
            
            // UPDATED: Use UnifiedAutomationManager instead of MachineAutomationHandler
            UnifiedAutomationManager.init(config);
            LOGGER.info("✓ Unified Automation Manager initialized");
            
            LOGGER.info("All systems initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Error during system initialization", e);
        }
    }
    
    private void registerEventHandlers() {
        try {
            // ✅ Overlay rendering is now handled in ContainerScreenMixin
            // No need for HUD callback - rendering happens after container renders
            
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                try {
                    // UPDATED: Use UnifiedAutomationManager instead of MachineAutomationHandler
                    UnifiedAutomationManager.tick();
                } catch (Exception e) {
                    LOGGER.error("Error in client tick handler", e);
                }
            });
            
            LOGGER.info("✓ Event handlers registered");
        } catch (Exception e) {
            LOGGER.error("Error registering event handlers", e);
        }
    }
    
    public static ModConfig getConfig() {
        return config;
    }
    
    public static void saveConfig() {
        if (config != null) {
            config.save();
        }
    }
}