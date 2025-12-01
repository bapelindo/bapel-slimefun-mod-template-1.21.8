package com.bapel_slimefun_mod;

import com.bapel_slimefun_mod.automation.MachineAutomationHandler;
import com.bapel_slimefun_mod.automation.SlimefunDataLoader;
import com.bapel_slimefun_mod.client.AutomationOverlay;
import com.bapel_slimefun_mod.client.ModKeybinds;
import com.bapel_slimefun_mod.config.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Client-side initialization and event handling (FIXED)
 */
public class BapelSlimefunModClient implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        BapelSlimefunMod.LOGGER.info("Initializing Slimefun Automation Client");
        
        // Load configuration
        ModConfig config = ModConfig.load();
        
        // Initialize automation handler with config
        MachineAutomationHandler.init(config);
        
        // Load machine data from JSON
        SlimefunDataLoader.loadData();
        
        // Register keybinds
        ModKeybinds.register();
        
        // Register HUD overlay
        AutomationOverlay.register();
        
        // Register tick event for automation
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MachineAutomationHandler.tick();
        });
        
        // Note: Screen detection is now handled by ContainerScreenMixin
        // This avoids duplicate registration and timing issues
        
        BapelSlimefunMod.LOGGER.info("Slimefun Automation initialized successfully");
        BapelSlimefunMod.LOGGER.info("Press 'K' to toggle automation (Current: {})", 
            config.isAutomationEnabled() ? "ON" : "OFF");
    }
}