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
<<<<<<< HEAD
 * âœ… CLEANED: No more command handling
 * 
 * All machine detection is now handled via GUI (M key â†’ Machine Detector)
 * This mixin only logs Slimefun messages for debugging
=======
 * ✅ DISABLED: Detection now happens in ContainerScreenMixin (GUI open event)
 * 
 * This mixin is kept for future features but multiblock detection is disabled.
 * Detection from chat message is unreliable because:
 * 1. Message arrives AFTER dispenser state changes
 * 2. Raycast may miss the dispenser
 * 3. Player might have moved away
 * 
 * Better approach: Detect when Dispenser GUI opens (ContainerScreenMixin)
>>>>>>> main
 */
@Mixin(ClientPacketListener.class)
public class ChatListenerMixin {
    
    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        try {
            Component message = packet.content();
            String text = message.getString();
            
<<<<<<< HEAD
            // âœ… OPTIONAL: Log Slimefun messages for debugging
=======
            // ✅ OPTIONAL: Log Slimefun messages for debugging
>>>>>>> main
            if (text.contains("Slimefun")) {
                BapelSlimefunMod.LOGGER.debug("[Chat] Slimefun message: {}", text);
            }
            
<<<<<<< HEAD
            // NOTE: All commands removed - use GUI instead (M key â†’ Machine Detector)
            // - /verify â†’ Now: M key â†’ Machine Detector â†’ Verify Multiblock
            // - /clear â†’ Now: M key â†’ Multiblock Cache â†’ Clear Cache
            // - /list â†’ Now: M key â†’ Multiblock Cache â†’ View Cache
            // - /clearall â†’ Now: M key â†’ Multiblock Cache â†’ Clear All
=======
            // ❌ REMOVED: Multiblock detection (now handled in ContainerScreenMixin)
>>>>>>> main
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error in chat listener", e);
        }
    }
}