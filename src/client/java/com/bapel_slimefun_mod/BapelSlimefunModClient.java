package com.bapel_slimefun_mod;

import com.bapel_slimefun_mod.automation.MachineAutomationHandler;
import com.bapel_slimefun_mod.automation.SlimefunDataLoader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

/**
 * Client-side initialization and event handling
 */
public class BapelSlimefunModClient implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        BapelSlimefunMod.LOGGER.info("Initializing Slimefun Automation Client");
        
        // Load machine data from JSON
        SlimefunDataLoader.loadData();
        
        // Register tick event for automation
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MachineAutomationHandler.tick();
        });
        
        // Register screen events to detect GUI opening/closing
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
                Component title = containerScreen.getTitle();
                String titleText = title.getString();
                
                // Check if this is a Slimefun machine
                if (SlimefunDataLoader.isMachine(titleText)) {
                    MachineAutomationHandler.onContainerOpen(titleText);
                }
                
                // Register screen close handler
                ScreenEvents.remove(screen).register(removedScreen -> {
                    MachineAutomationHandler.onContainerClose();
                });
            }
        });
        
        BapelSlimefunMod.LOGGER.info("Slimefun Automation initialized successfully");
    }
}