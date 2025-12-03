package com.bapel_slimefun_mod.mixin.client;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.automation.SlimefunDataLoader;
import com.bapel_slimefun_mod.automation.SlimefunMachineData;
import com.bapel_slimefun_mod.automation.UnifiedAutomationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 *  COMPLETE REWRITE: Multi-machine cache system
 * 
 * Detects "successfully constructed" message and caches the machine
 * Supports unlimited number of multiblocks
 */
@Mixin(ClientPacketListener.class)
public class ChatListenerMixin {
    
    private static long lastDetectionTime = 0;
    private static final long DETECTION_COOLDOWN = 500; // 500ms
  
    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        try {
            Component message = packet.content();
            String text = message.getString();
            
            // Detect Slimefun multiblock construction message
if (text.contains("Slimefun") && text.contains("successfully constructed this Multiblock")) {
                
    long now = System.currentTimeMillis();
    if (now - lastDetectionTime < DETECTION_COOLDOWN) {
        return; // Skip duplicate
    }
    lastDetectionTime = now;
                
                
                // Small delay to detect machine type
                Minecraft mc = Minecraft.getInstance();
                new Thread(() -> {
                    try {
                        Thread.sleep(150);
                        
                        mc.execute(() -> {
                            detectAndCacheMachine();
                        });
                    } catch (Exception e) {
                        BapelSlimefunMod.LOGGER.error("[ChatDetector] Error in delayed detection", e);
                    }
                }).start();
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[ChatDetector] Error in chat listener", e);
        }
    }
    
    
    /**
     *  NEW: Detect and cache the multiblock machine
     * This adds the machine to the multi-machine cache system
     */
    private void detectAndCacheMachine() {
        
        // Get all machines
        var allMachines = SlimefunDataLoader.getAllMachines();
        
        // Priority list of common multiblocks
        String[] priorityMultiblocks = {
            "ENHANCED_CRAFTING_TABLE",
            "ORE_CRUSHER",
            "COMPRESSOR",
            "SMELTERY",
            "PRESSURE_CHAMBER",
            "MAGIC_WORKBENCH",
            "ARMOR_FORGE",
            "MAKESHIFT_SMELTERY",
            "ORE_WASHER",
            "AUTOMATED_PANNING_MACHINE",
            "GOLD_PAN",
            "ANCIENT_ALTAR",
            "JUICER"
        };
        
        SlimefunMachineData detectedMachine = null;
        
        // Try priority list first
        for (String multiblockId : priorityMultiblocks) {
            SlimefunMachineData machine = allMachines.get(multiblockId);
            if (machine != null && machine.isMultiblock()) {
                detectedMachine = machine;
                break;
            }
        }
        
        // If not found, try any multiblock
        if (detectedMachine == null) {
            for (var entry : allMachines.entrySet()) {
                SlimefunMachineData machine = entry.getValue();
                if (machine.isMultiblock()) {
                    detectedMachine = machine;
                    break;
                }
            }
        }
        
        if (detectedMachine != null) {
            //  CORE: Cache this machine at player position!
            UnifiedAutomationManager.onMultiblockConstructed(detectedMachine);
        } else {
            BapelSlimefunMod.LOGGER.warn("[ChatDetector]  Could not detect multiblock type!");
        }
    }
}