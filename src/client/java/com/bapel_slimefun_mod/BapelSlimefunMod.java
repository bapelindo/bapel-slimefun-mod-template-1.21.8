package com.bapel_slimefun_mod;

import com.bapel_slimefun_mod.automation.*;
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
        LOGGER.info("Bapel Slimefun Mod Initializing");
        
        config = ModConfig.load();
        
        initializeSystems();
        ModKeybinds.register();
        registerEventHandlers();
        
        LOGGER.info("Bapel Slimefun Mod Initialized Successfully");
    }
    
    private void initializeSystems() {
        try {
            ItemRegistry.initialize();
            SlimefunDataLoader.loadData();
            RecipeDatabase.initialize();
            RecipeOverlayRenderer.initialize();
            MultiblockCacheManager.load();
            UnifiedAutomationManager.init(config);
            
            LOGGER.info("All systems initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Error during system initialization", e);
        }
    }
    
    private void registerEventHandlers() {
        try {
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                try {
                    UnifiedAutomationManager.tick();
                } catch (Exception e) {
                    LOGGER.error("Error in client tick handler", e);
                }
            });
            
            LOGGER.info("Event handlers registered successfully");
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
