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
 * ✅ DISABLED: Detection now happens in ContainerScreenMixin (GUI open event)
 * 
 * This mixin is kept for future features but multiblock detection is disabled.
 * Detection from chat message is unreliable because:
 * 1. Message arrives AFTER dispenser state changes
 * 2. Raycast may miss the dispenser
 * 3. Player might have moved away
 * 
 * Better approach: Detect when Dispenser GUI opens (ContainerScreenMixin)
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
            
            // ❌ REMOVED: Multiblock detection (now handled in ContainerScreenMixin)
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error in chat listener", e);
        }
    }
}