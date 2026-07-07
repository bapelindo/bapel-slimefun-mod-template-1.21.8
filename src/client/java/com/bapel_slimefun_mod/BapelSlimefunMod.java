package com.bapel_slimefun_mod;

import com.bapel_slimefun_mod.automation.*;
import com.bapel_slimefun_mod.client.ModKeybinds;
import com.bapel_slimefun_mod.config.ModConfig;
import com.bapel_slimefun_mod.debug.PerformanceMonitor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BapelSlimefunMod implements ClientModInitializer {
    public static final String MOD_ID = "bapel-slimefun-mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static ModConfig config;
    private static net.minecraft.client.KeyMapping triggerKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Bapel Slimefun Mod Initializing");

        // Register custom network payloads
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(com.bapel_slimefun_mod.network.RecipeSyncFullPacket.TYPE, com.bapel_slimefun_mod.network.RecipeSyncFullPacket.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(com.bapel_slimefun_mod.network.RecipeSyncDeltaPacket.TYPE, com.bapel_slimefun_mod.network.RecipeSyncDeltaPacket.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(com.bapel_slimefun_mod.network.CraftConfirmPacket.TYPE, com.bapel_slimefun_mod.network.CraftConfirmPacket.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(com.bapel_slimefun_mod.network.MachineStatusPacket.TYPE, com.bapel_slimefun_mod.network.MachineStatusPacket.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(com.bapel_slimefun_mod.network.RequestRecipeSyncPacket.TYPE, com.bapel_slimefun_mod.network.RequestRecipeSyncPacket.CODEC);
        
        config = ModConfig.load();
        
        triggerKey = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(
            new net.minecraft.client.KeyMapping(
                "key.bapelmod.trigger",
                com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,
                com.bapel_slimefun_mod.client.ModKeybinds.CATEGORY
            )
        );
        
        initializeSystems();
        com.bapel_slimefun_mod.automation.network.AutomationPacketHandler.register();
        ModKeybinds.register();
        com.bapel_slimefun_mod.automation.fastmachine.FastMachineChatCommandInterceptor.register();
        com.bapel_slimefun_mod.automation.fastmachine.FastMachineTestRunner.register();
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
            
            // Load automation engine configuration
            com.bapel_slimefun_mod.automation.config.AutomationConfigLoader.load();
            // Load local learned recipes cache
            com.bapel_slimefun_mod.automation.recipe.RecipeCacheManager.load();
            // Seed development/testing recipes
            seedRecipes();
            
            LOGGER.info("All systems initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Error during system initialization", e);
        }
    }

    private void seedRecipes() {
        com.bapel_slimefun_mod.automation.recipe.SlimefunRecipeRegistry reg = com.bapel_slimefun_mod.automation.recipe.SlimefunRecipeRegistry.get();
        reg.addRecipe(new com.bapel_slimefun_mod.automation.recipe.RecipeEntry(
            "slimefun:COPPER_INGOT",
            net.minecraft.world.item.ItemStack.EMPTY,
            1,
            java.util.List.of(
                com.bapel_slimefun_mod.automation.recipe.RecipeIngredient.of("slimefun:COPPER_DUST", 4)
            ),
            "slimefun:electric_smeltery"
        ));
        reg.addRecipe(new com.bapel_slimefun_mod.automation.recipe.RecipeEntry(
            "slimefun:COPPER_DUST",
            net.minecraft.world.item.ItemStack.EMPTY,
            2,
            java.util.List.of(
                com.bapel_slimefun_mod.automation.recipe.RecipeIngredient.of("minecraft:raw_copper", 1)
            ),
            "slimefun:electric_ore_grinder"
        ));
        reg.addRecipe(new com.bapel_slimefun_mod.automation.recipe.RecipeEntry(
            "slimefun:PORTABLE_DUSTBIN",
            net.minecraft.world.item.ItemStack.EMPTY,
            1,
            java.util.List.of(
                com.bapel_slimefun_mod.automation.recipe.RecipeIngredient.of("minecraft:chest", 1),
                com.bapel_slimefun_mod.automation.recipe.RecipeIngredient.of("minecraft:iron_ingot", 1)
            ),
            "slimefun:enhanced_crafting_table"
        ));
        LOGGER.info("Dev recipes seeded: {} total", reg.totalRecipes());
    }
    
    private void registerEventHandlers() {
        try {
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                try {
                    PerformanceMonitor.trackFrame();
                    com.bapel_slimefun_mod.automation.util.TickScheduler.get().tick();
                    UnifiedAutomationManager.tick();
                    com.bapel_slimefun_mod.automation.recipe.SlimefunGuideScraper.tick(client);

                    if (triggerKey.consumeClick()
                        && client.options.keyShift.isDown()
                        && client.screen == null
                        && client.player != null
                    ) {
                        net.minecraft.world.item.ItemStack target = client.player.getMainHandItem();
                        if (target.isEmpty()) {
                            com.bapel_slimefun_mod.automation.util.ChatFeedback.warn("Hold target item di tangan utama!");
                        } else if (!com.bapel_slimefun_mod.automation.recipe.SlimefunRecipeRegistry.get().hasRecipe(target)) {
                            com.bapel_slimefun_mod.automation.util.ChatFeedback.error("Tidak ada resep untuk: " + com.bapel_slimefun_mod.automation.util.ItemKeyUtil.getKey(target));
                        } else {
                            String itemKey = com.bapel_slimefun_mod.automation.util.ItemKeyUtil.getKey(target);
                            com.bapel_slimefun_mod.automation.AutomationStateMachine sm = com.bapel_slimefun_mod.automation.AutomationStateMachine.getGlobalInstance();
                            if (sm != null) {
                                com.bapel_slimefun_mod.automation.util.ChatFeedback.info("Starting chain: " + itemKey);
                                sm.start(new com.bapel_slimefun_mod.automation.CraftingJob(itemKey, target, 1, 0));
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error in client tick handler", e);
                }
            });
            
            // Register FastMachine GUI Hook Buttons right at system events
            com.bapel_slimefun_mod.automation.fastmachine.FastMachineGuiButtons.register();
            
            HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "performance_monitor"), (graphics, tickDelta) -> {
                try {
                    PerformanceMonitor.render(graphics);
                } catch (Exception e) {
                    LOGGER.error("ERROR IN PERFORMANCE MONITOR RENDER!", e);
                }
            });

            // FastMachine HUD Engine Registry integration
            HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "fastmachine_hud"), (graphics, tickDelta) -> {
                try {
                    com.bapel_slimefun_mod.automation.fastmachine.FastMachineHudOverlay.render(graphics);
                } catch (Exception e) {
                    LOGGER.error("ERROR IN FASTMACHINE HUD RENDER!", e);
                }
            });

            // AutoCraft HUD Overlay integration
            com.bapel_slimefun_mod.automation.AutomationStateMachine autocraftSM = com.bapel_slimefun_mod.automation.AutomationStateMachine.getGlobalInstance();
            if (autocraftSM != null) {
                com.bapel_slimefun_mod.automation.hud.AutomationHudRenderer autocraftHud =
                    new com.bapel_slimefun_mod.automation.hud.AutomationHudRenderer(autocraftSM, autocraftSM.getContext());
                HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "autocraft_hud"), (graphics, tickDelta) -> {
                    try {
                        autocraftHud.render(graphics);
                    } catch (Exception e) {
                        LOGGER.error("ERROR IN AUTOCRAFT HUD RENDER!", e);
                    }
                });
            }
            
            // State Machine Event Subscriptions
            com.bapel_slimefun_mod.automation.event.AutomationEventBus.get().subscribe(
                com.bapel_slimefun_mod.automation.event.AutomationEvent.STATE_CHANGED,
                payload -> com.bapel_slimefun_mod.automation.RecipeOverlayRenderer.updateState((java.util.Map<?, ?>) payload)
            );

            com.bapel_slimefun_mod.automation.event.AutomationEventBus.get().subscribe(
                com.bapel_slimefun_mod.automation.event.AutomationEvent.CHAIN_FAILED,
                reason -> {
                    net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                    if (client.player != null) {
                        client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[AutoCraft] Failed: " + reason));
                    }
                }
            );

            LOGGER.info("Event handlers registered successfully");
            LOGGER.info("Performance Monitor installed! Press F3 to toggle.");
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