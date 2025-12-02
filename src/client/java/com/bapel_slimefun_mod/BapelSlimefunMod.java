package com.bapel_slimefun_mod;

import com.bapel_slimefun_mod.automation.*;
import com.bapel_slimefun_mod.client.DebugOverlay;
import com.bapel_slimefun_mod.config.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BapelSlimefunMod implements ClientModInitializer {
    public static final String MOD_ID = "bapel-slimefun-mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static ModConfig config;
    private static KeyMapping toggleAutomationKey;
    private static KeyMapping debugInfoKey;
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Bapel Slimefun Mod");
        
        config = ModConfig.load();
        LOGGER.info("Configuration loaded: {}", config);
        
        initializeSystems();
        registerKeybindings();
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
            
            RecipeOverlayRenderer.initialize();
            LOGGER.info("✓ Recipe Overlay Renderer initialized");
            
            DebugOverlay.register();
            LOGGER.info("✓ Debug Overlay registered");
            
            MachineAutomationHandler.init(config);
            LOGGER.info("✓ Machine Automation Handler initialized");
            
            LOGGER.info("All systems initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Error during system initialization", e);
        }
    }
    
    private void registerKeybindings() {
        try {
            toggleAutomationKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.bapel-slimefun-mod.toggle_automation",
                GLFW.GLFW_KEY_G,
                "category.bapel-slimefun-mod.automation"
            ));
            
            debugInfoKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.bapel-slimefun-mod.debug_info",
                GLFW.GLFW_KEY_F3,
                "category.bapel-slimefun-mod.automation"
            ));
            
            LOGGER.info("✓ Keybindings registered (R key handled by mixin)");
        } catch (Exception e) {
            LOGGER.error("Error registering keybindings", e);
        }
    }
    
    private void registerEventHandlers() {
        try {
            HudRenderCallback.EVENT.register((context, tickDelta) -> {
                try {
                    RecipeOverlayRenderer.render(context, tickDelta.getGameTimeDeltaPartialTick(true));
                } catch (Exception e) {
                    LOGGER.error("Error rendering recipe overlay", e);
                }
            });
            
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                try {
                    while (toggleAutomationKey.consumeClick()) {
                        MachineAutomationHandler.toggleAutomation();
                    }
                    
                    while (debugInfoKey.consumeClick()) {
                        if (config.isDebugMode()) {
                            DebugOverlay.toggle();
                            DebugHelper.runFullDiagnostic();
                            
                            if (client.player != null) {
                                String status = DebugOverlay.isVisible() ? "§aSHOWN" : "§cHIDDEN";
                                client.player.displayClientMessage(
                                    net.minecraft.network.chat.Component.literal(
                                        "§6[Slimefun] §fDebug Overlay: " + status
                                    ), 
                                    true
                                );
                            }
                        } else {
                            if (client.player != null) {
                                client.player.displayClientMessage(
                                    net.minecraft.network.chat.Component.literal(
                                        "§cDebug mode is disabled in config"
                                    ), 
                                    true
                                );
                            }
                        }
                    }
                    
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
    
    public static ModConfig getConfig() {
        return config;
    }
    
    public static void saveConfig() {
        if (config != null) {
            config.save();
        }
    }
}
