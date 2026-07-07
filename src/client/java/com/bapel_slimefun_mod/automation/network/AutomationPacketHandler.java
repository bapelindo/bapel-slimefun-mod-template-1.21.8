package com.bapel_slimefun_mod.automation.network;

import com.bapel_slimefun_mod.automation.recipe.SlimefunRecipeRegistry;
import com.bapel_slimefun_mod.automation.util.ChatFeedback;
import com.bapel_slimefun_mod.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**  
 * Mendaftarkan semua custom packet handler di sisi client.  
 */  
public final class AutomationPacketHandler {  

    private static final Logger LOGGER = LoggerFactory.getLogger("AutomationPackets");  

    private AutomationPacketHandler() {}  

    // ─────────────────────────────────────────────────────────────  

    public static void register() {  
        registerRecipeSyncFull();  
        registerRecipeSyncDelta();  
        registerCraftConfirm();  
        registerMachineStatus();  

        LOGGER.info("[BapelMod] Custom packet handlers registered.");  
    }  

    // ─────────────────────────────────────────────────────────────  
    // Handlers  
    // ─────────────────────────────────────────────────────────────  

    private static void registerRecipeSyncFull() {  
        ClientPlayNetworking.registerGlobalReceiver(  
            RecipeSyncFullPacket.TYPE,  
            (payload, context) -> {  
                context.client().execute(() -> {  
                    SlimefunRecipeRegistry registry = SlimefunRecipeRegistry.get();  
                    registry.clear();  

                    for (RecipeSyncPacket pkt : payload.recipes()) {  
                        registry.addRecipe(pkt.toRecipeEntry());  
                    }  

                    LOGGER.info("[BapelMod] Recipe sync complete: {} recipes loaded.",  
                        registry.totalRecipes());  
                    ChatFeedback.success("Recipe registry synced: "  
                        + registry.totalRecipes() + " recipes.");  
                });  
            }  
        );  
    }  

    private static void registerRecipeSyncDelta() {  
        ClientPlayNetworking.registerGlobalReceiver(  
            RecipeSyncDeltaPacket.TYPE,  
            (payload, context) -> {  
                context.client().execute(() -> {  
                    SlimefunRecipeRegistry reg = SlimefunRecipeRegistry.get();  

                    if (payload.action() == 0 && payload.packet() != null) {  
                        reg.addRecipe(payload.packet().toRecipeEntry());  
                        LOGGER.debug("Delta ADD: {}", payload.packet().outputKey());  
                    } else if (payload.action() == 1 && payload.outputKey() != null) {  
                        reg.removeRecipe(payload.outputKey());  
                        LOGGER.debug("Delta REMOVE: {}", payload.outputKey());  
                    }  
                });  
            }  
        );  
    }  

    private static void registerCraftConfirm() {  
        ClientPlayNetworking.registerGlobalReceiver(  
            CraftConfirmPacket.TYPE,  
            (payload, context) -> {  
                context.client().execute(() -> {  
                    LOGGER.info("[BapelMod] Server confirmed craft: {} x{}",  
                        payload.itemKey(), payload.amount());  
                    com.bapel_slimefun_mod.automation.event.AutomationEventBus.get()  
                        .publish(  
                            com.bapel_slimefun_mod.automation.event.AutomationEvent.CRAFTING_VERIFIED,  
                            payload.itemKey()  
                        );  
                });  
            }  
        );  
    }  

    private static void registerMachineStatus() {  
        ClientPlayNetworking.registerGlobalReceiver(  
            MachineStatusPacket.TYPE,  
            (payload, context) -> {  
                BlockPos pos = new BlockPos(payload.x(), payload.y(), payload.z());  
                context.client().execute(() -> {  
                    MachineStatusCache.get().update(pos, MachineStatus.fromByte(payload.status()));  
                    LOGGER.debug("Machine status update: {} → {}", pos, payload.status());  
                });  
            }  
        );  
    }  
}
