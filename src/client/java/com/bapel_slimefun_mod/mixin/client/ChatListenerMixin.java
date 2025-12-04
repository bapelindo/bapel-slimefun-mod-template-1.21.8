package com.bapel_slimefun_mod.mixin.client;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ✅ CLEANED: No more command handling
 * 
 * All machine detection is now handled via GUI (M key → Machine Detector)
 * This mixin only logs Slimefun messages for debugging
 */
@Mixin(ClientPacketListener.class)
public class ChatListenerMixin {
    
    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        try {
            Component message = packet.content();
            String text = message.getString();
            
            // ✅ OPTIONAL: Log Slimefun messages for debugging
            if (text.contains("Slimefun")) {
                BapelSlimefunMod.LOGGER.debug("[Chat] Slimefun message: {}", text);
            }
            
            // NOTE: All commands removed - use GUI instead (M key → Machine Detector)
            // - /verify → Now: M key → Machine Detector → Verify Multiblock
            // - /clear → Now: M key → Multiblock Cache → Clear Cache
            // - /list → Now: M key → Multiblock Cache → View Cache
            // - /clearall → Now: M key → Multiblock Cache → Clear All
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error in chat listener", e);
        }
    }
}