package com.bapel_slimefun_mod.mixin.client;

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
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    
    /**
     * Called when container screen is opened
     */
    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        Component title = screen.getTitle();
        
        if (title != null) {
            String titleString = title.getString();
            MachineAutomationHandler.onContainerOpen(titleString);
        }
    }
    
    /**
     * Called when container screen is closed
     */
    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        MachineAutomationHandler.onContainerClose();
    }
    
    /**
     * Intercept key presses in container screens (NEW)
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, 
                             CallbackInfoReturnable<Boolean> cir) {
        // Let the overlay input handler process the key
        boolean handled = RecipeOverlayInputHandler.handleKeyPress(
            keyCode, scanCode, 1, modifiers  // action = 1 for PRESS
        );
        
        if (handled) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
    
    /**
     * Intercept mouse scroll in container screens (NEW)
     */
@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount,
                                CallbackInfoReturnable<Boolean> cir) {
        // Pass the vertical scroll amount as the "delta"
        boolean handled = RecipeOverlayInputHandler.handleMouseScroll(verticalAmount);
        
        if (handled) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
    
    /**
     * Intercept mouse clicks in container screens (NEW)
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button,
                               CallbackInfoReturnable<Boolean> cir) {
        boolean handled = RecipeOverlayInputHandler.handleMouseClick(
            mouseX, mouseY, button
        );
        
        if (handled) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
