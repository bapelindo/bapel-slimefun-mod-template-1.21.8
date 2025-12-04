package com.bapel_slimefun_mod.mixin.client;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ✅ SIMPLIFIED: No longer needed for multiblock detection
 * Detection now happens in UnifiedAutomationManager.handleDispenserOpen()
 */
@Mixin(ClientPacketListener.class)
public class ChatListenerMixin {
    
    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        // Kosong - deteksi sekarang di handleDispenserOpen()
    }
}