package com.bapel_slimefun_mod.mixin.client;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.automation.MachineAutomationHandler;
import com.bapel_slimefun_mod.automation.RecipeOverlayInputHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept container screen events
 * DENGAN DEBUG LOGGING
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    
    /**
     * Called when container screen is opened
     */
    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        try {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
            Component title = screen.getTitle();
            
            if (title != null) {
                String titleString = title.getString();
                
                // DEBUG LOG
                BapelSlimefunMod.LOGGER.info("╔════════════════════════════════════════╗");
                BapelSlimefunMod.LOGGER.info("║  CONTAINER OPENED                      ║");
                BapelSlimefunMod.LOGGER.info("╠════════════════════════════════════════╣");
                BapelSlimefunMod.LOGGER.info("║  Title: {}", titleString);
                BapelSlimefunMod.LOGGER.info("║  Class: {}", screen.getClass().getSimpleName());
                BapelSlimefunMod.LOGGER.info("╚════════════════════════════════════════╝");
                
                // Notify automation handler
                MachineAutomationHandler.onContainerOpen(titleString);
                
                // Check if machine was detected
                if (MachineAutomationHandler.getCurrentMachine() != null) {
                    BapelSlimefunMod.LOGGER.info("✓ Machine detected: {}", 
                        MachineAutomationHandler.getCurrentMachine().getName());
                } else {
                    BapelSlimefunMod.LOGGER.info("✗ Not a Slimefun machine");
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("ERROR in onInit mixin", e);
            e.printStackTrace();
        }
    }
    
    /**
     * Called when container screen is closed
     */
    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        try {
            BapelSlimefunMod.LOGGER.info("╔════════════════════════════════════════╗");
            BapelSlimefunMod.LOGGER.info("║  CONTAINER CLOSED                      ║");
            BapelSlimefunMod.LOGGER.info("╚════════════════════════════════════════╝");
            
            MachineAutomationHandler.onContainerClose();
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("ERROR in onRemoved mixin", e);
            e.printStackTrace();
        }
    }
    
    /**
     * Intercept key presses in container screens
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, 
                             CallbackInfoReturnable<Boolean> cir) {
        try {
            // Let the overlay input handler process the key
            boolean handled = RecipeOverlayInputHandler.handleKeyPress(
                keyCode, scanCode, 1, modifiers
            );
            
            if (handled) {
                cir.setReturnValue(true);
                cir.cancel();
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("ERROR in onKeyPressed mixin", e);
        }
    }
    
    /**
     * Intercept mouse scroll in container screens
     */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount,
                                CallbackInfoReturnable<Boolean> cir) {
        try {
            boolean handled = RecipeOverlayInputHandler.handleMouseScroll(verticalAmount);
            
            if (handled) {
                cir.setReturnValue(true);
                cir.cancel();
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("ERROR in onMouseScrolled mixin", e);
        }
    }
    
    /**
     * Intercept mouse clicks in container screens
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button,
                               CallbackInfoReturnable<Boolean> cir) {
        try {
            boolean handled = RecipeOverlayInputHandler.handleMouseClick(
                mouseX, mouseY, button
            );
            
            if (handled) {
                cir.setReturnValue(true);
                cir.cancel();
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("ERROR in onMouseClicked mixin", e);
        }
    }
}