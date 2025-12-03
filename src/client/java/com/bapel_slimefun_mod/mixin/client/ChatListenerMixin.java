package com.bapel_slimefun_mod.mixin.client;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.automation.MultiblockDetector;
import com.bapel_slimefun_mod.automation.SlimefunDataLoader;
import com.bapel_slimefun_mod.automation.SlimefunMachineData;
import com.bapel_slimefun_mod.automation.UnifiedAutomationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ✅ BEST: Ultra-precise multiblock detection using MultiblockDetector
 * 
 * FEATURES:
 * 1. Confidence-based matching (60%+ threshold)
 * 2. Signature block recognition
 * 3. Disambiguates similar structures
 * 4. Handles all edge cases
 */
@Mixin(ClientPacketListener.class)
public class ChatListenerMixin {
    
    private static long lastDetectionTime = 0;
    private static final long DETECTION_COOLDOWN = 500;
  
    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        try {
            Component message = packet.content();
            String text = message.getString();
            
            if (text.contains("Slimefun") && text.contains("successfully constructed this Multiblock")) {
                long now = System.currentTimeMillis();
                if (now - lastDetectionTime < DETECTION_COOLDOWN) {
                    return;
                }
                lastDetectionTime = now;
                
                Minecraft mc = Minecraft.getInstance();
                new Thread(() -> {
                    try {
                        Thread.sleep(250);
                        mc.execute(() -> detectAndCacheMachine());
                    } catch (Exception e) {
                        BapelSlimefunMod.LOGGER.error("Error in delayed detection", e);
                    }
                }).start();
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error in chat listener", e);
        }
    }
    
    /**
     * ✅ ADVANCED: Use MultiblockDetector for accurate detection
     */
    private void detectAndCacheMachine() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        
        BlockPos playerPos = mc.player.blockPosition();
        Level level = mc.level;
        
        // Use advanced detector
        MultiblockDetector.DetectionResult result = MultiblockDetector.detect(level, playerPos);
        
        if (result != null) {
            SlimefunMachineData machine = SlimefunDataLoader.getMultiblockById(result.getMachineId());
            
            if (machine != null) {
                UnifiedAutomationManager.onMultiblockConstructed(machine);
                BapelSlimefunMod.LOGGER.info("Detected: {}", result.toString());
            }
        } else {
            BapelSlimefunMod.LOGGER.warn("No matching multiblock found");
        }
    }
}