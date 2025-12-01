package com.bapel_slimefun_mod.mixin.client;

import com.bapel_slimefun_mod.automation.MachineAutomationHandler;
import com.bapel_slimefun_mod.automation.SlimefunDataLoader;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixed mixin to detect container screens without duplicates
 */
@Mixin(AbstractContainerScreen.class)
public class ContainerScreenMixin {
    
    @Unique
    private boolean bapelSlimefunMod$detected = false;
    
    @Inject(method = "init", at = @At("TAIL"))
    private void onScreenInit(CallbackInfo ci) {
        // Prevent duplicate detection
        if (bapelSlimefunMod$detected) {
            return;
        }
        
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        Component title = screen.getTitle();
        String titleText = title.getString();
        
        // Check if this is a Slimefun machine
        if (SlimefunDataLoader.isMachine(titleText)) {
            MachineAutomationHandler.onContainerOpen(titleText);
            bapelSlimefunMod$detected = true;
        }
    }
    
    @Inject(method = "removed", at = @At("HEAD"))
    private void onScreenRemoved(CallbackInfo ci) {
        // Only call close if we detected this screen
        if (bapelSlimefunMod$detected) {
            MachineAutomationHandler.onContainerClose();
            bapelSlimefunMod$detected = false;
        }
    }
}