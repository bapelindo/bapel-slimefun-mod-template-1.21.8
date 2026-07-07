package com.bapel_slimefun_mod.mixin.client;

import com.bapel_slimefun_mod.automation.network.MachineStatusCache;
import com.bapel_slimefun_mod.automation.recipe.SlimefunRecipeRegistry;
import com.bapel_slimefun_mod.network.RequestRecipeSyncPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("BapelMixin");

    @Inject(
        method = "handleLogin",
        at = @At("TAIL")
    )
    private void onGameJoin(ClientboundLoginPacket packet, CallbackInfo ci) {
        LOGGER.info("[BapelMod] Player joined, requesting recipe sync...");

        SlimefunRecipeRegistry.get().clear();
        MachineStatusCache.get().clear();

        if (ClientPlayNetworking.canSend(RequestRecipeSyncPacket.TYPE)) {
            ClientPlayNetworking.send(new RequestRecipeSyncPacket());
            LOGGER.info("[BapelMod] Recipe sync request sent.");
        } else {
            LOGGER.warn("[BapelMod] Server tidak support packet sync. Menggunakan dev recipes.");
        }
    }
}
