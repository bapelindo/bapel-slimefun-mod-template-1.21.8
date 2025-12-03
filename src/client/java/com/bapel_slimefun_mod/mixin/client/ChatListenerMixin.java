package com.bapel_slimefun_mod.mixin.client;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.automation.SlimefunDataLoader;
import com.bapel_slimefun_mod.automation.SlimefunMachineData;
import com.bapel_slimefun_mod.automation.UnifiedAutomationManager;
import com.bapel_slimefun_mod.automation.RecipeOverlayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ChatListenerMixin {
    
    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        try {
            Component message = packet.content();
            String text = message.getString();
            
            // Detect Slimefun multiblock construction message
            // Format: "Slimefun 4> You have successfully constructed this Multiblock..."
            if (text.contains("Slimefun") && text.contains("successfully constructed this Multiblock")) {
                BapelSlimefunMod.LOGGER.info("[ChatDetector] Multiblock construction detected!");
                
                // Delayed detection - wait for dispenser to open
                Minecraft mc = Minecraft.getInstance();
                new Thread(() -> {
                    try {
                        Thread.sleep(100); // Small delay
                        
                        mc.execute(() -> {
                            // Try to detect which multiblock was built
                            detectMultiblockType();
                        });
                    } catch (Exception e) {
                        BapelSlimefunMod.LOGGER.error("Error in delayed multiblock detection", e);
                    }
                }).start();
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error in chat listener", e);
        }
    }
    
    private void detectMultiblockType() {
        // For now, show a generic multiblock selection overlay
        // In the future, we can detect based on blocks around the player
        
        BapelSlimefunMod.LOGGER.info("[ChatDetector] Attempting to detect multiblock type...");
        
        // Get all multiblock machines
        var allMachines = SlimefunDataLoader.getAllMachines();
        
        for (var entry : allMachines.entrySet()) {
            SlimefunMachineData machine = entry.getValue();
            
            // For Enhanced Crafting Table specifically
            if (machine.getId().equals("ENHANCED_CRAFTING_TABLE")) {
                BapelSlimefunMod.LOGGER.info("[ChatDetector] Detected ENHANCED_CRAFTING_TABLE");
                
                // Set as current machine
                UnifiedAutomationManager.onMachineOpen("ENHANCED_CRAFTING_TABLE");
                
                // Show recipe overlay
                try {
                    RecipeOverlayRenderer.show(machine);
                    
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.displayClientMessage(
                            Component.literal("§a[Slimefun] Enhanced Crafting Table detected! Press R for recipes."),
                            true
                        );
                    }
                } catch (Exception e) {
                    BapelSlimefunMod.LOGGER.error("Failed to show overlay", e);
                }
                
                break;
            }
        }
    }
}