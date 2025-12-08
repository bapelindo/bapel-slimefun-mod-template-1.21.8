package com.bapel_slimefun_mod.mixin.client;

import com.bapel_slimefun_mod.client.ModKeybinds;
import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.automation.UnifiedAutomationManager;
import com.bapel_slimefun_mod.automation.RecipeOverlayInputHandler;
import com.bapel_slimefun_mod.automation.RecipeOverlayRenderer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    
    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        try {
            RecipeOverlayRenderer.hide();
            
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
            Component title = screen.getTitle();
            
            if (title != null) {
                String titleString = title.getString();
                UnifiedAutomationManager.onMachineOpen(titleString);
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("ERROR in onInit mixin", e);
        }
    }
    
    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        try {
            RecipeOverlayRenderer.hide();
            UnifiedAutomationManager.onMachineClose();
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("ERROR in onRemoved mixin", e);
        }
    }
    
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        try {
            RecipeOverlayRenderer.render(graphics, partialTick);
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("ERROR rendering recipe overlay", e);
        }
    }
    
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, 
                             CallbackInfoReturnable<Boolean> cir) {
        try {
            if (ModKeybinds.getToggleAutomationKey().matches(keyCode, scanCode)) {
                UnifiedAutomationManager.toggleAutomation();
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }
            
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
    
    // ✅ NEW: Handle character input for search
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void onCharTyped(char chr, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        try {
            boolean handled = RecipeOverlayInputHandler.handleCharTyped(chr, modifiers);
            
            if (handled) {
                cir.setReturnValue(true);
                cir.cancel();
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("ERROR in onCharTyped mixin", e);
        }
    }
    
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