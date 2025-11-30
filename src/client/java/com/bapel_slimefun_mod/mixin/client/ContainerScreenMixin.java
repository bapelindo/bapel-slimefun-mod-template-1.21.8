package com.bapel_slimefun_mod.mixin.client;

import com.bapel_slimefun_mod.automation.MachineAutomationHandler;
import com.bapel_slimefun_mod.automation.SlimefunDataLoader;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to detect when container screens are opened
 */
@Mixin(AbstractContainerScreen.class)
public class ContainerScreenMixin {
    
    @Inject(method = "init", at = @At("TAIL"))
    private void onScreenInit(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        Component title = screen.getTitle();
        String titleText = title.getString();
        
        // Check if this is a Slimefun machine
        if (SlimefunDataLoader.isMachine(titleText)) {
            MachineAutomationHandler.onContainerOpen(titleText);
        }
    }
    
    @Inject(method = "removed", at = @At("HEAD"))
    private void onScreenRemoved(CallbackInfo ci) {
        MachineAutomationHandler.onContainerClose();
    }
}