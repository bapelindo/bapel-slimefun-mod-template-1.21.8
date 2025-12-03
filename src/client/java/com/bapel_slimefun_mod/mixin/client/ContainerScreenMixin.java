package com.bapel_slimefun_mod.mixin.client;

import com.bapel_slimefun_mod.client.ModKeybinds;
import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.automation.MachineAutomationHandler;
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
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
            Component title = screen.getTitle();
            
            if (title != null) {
                String titleString = title.getString();
                
                BapelSlimefunMod.LOGGER.info("╔═══════════════════════════════════════╗");
                BapelSlimefunMod.LOGGER.info("║  CONTAINER OPENED                      ║");
                BapelSlimefunMod.LOGGER.info("╠═══════════════════════════════════════╣");
                BapelSlimefunMod.LOGGER.info("║  Title: {}", titleString);
                BapelSlimefunMod.LOGGER.info("║  Class: {}", screen.getClass().getSimpleName());
                BapelSlimefunMod.LOGGER.info("╚═══════════════════════════════════════╝");
                
                MachineAutomationHandler.onContainerOpen(titleString);
                
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
    
    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        try {
            BapelSlimefunMod.LOGGER.info("╔═══════════════════════════════════════╗");
            BapelSlimefunMod.LOGGER.info("║  CONTAINER CLOSED                      ║");
            BapelSlimefunMod.LOGGER.info("╚═══════════════════════════════════════╝");
            
            // PERBAIKAN UTAMA: Panggil hide() secara langsung di sini!
            RecipeOverlayRenderer.hide();
            
            MachineAutomationHandler.onContainerClose();
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("ERROR in onRemoved mixin", e);
            e.printStackTrace();
        }
    }
    
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        try {
            // ✅ CRITICAL FIX: Render overlay at TAIL (after everything else)
            // This ensures it appears on top of all GUI elements including tooltips
            RecipeOverlayRenderer.render(graphics, partialTick);
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("ERROR rendering recipe overlay", e);
        }
    }
    
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, 
                             CallbackInfoReturnable<Boolean> cir) {
        try {
            // Log R key specifically
            if (keyCode == GLFW.GLFW_KEY_R) {
                BapelSlimefunMod.LOGGER.info("║ R key pressed in container!");
            }
            
            // --- FIX: Gunakan ModKeybinds.getToggleAutomationKey() ---
            if (ModKeybinds.getToggleAutomationKey().matches(keyCode, scanCode)) {
                BapelSlimefunMod.LOGGER.info("║ Automation key pressed in container!");
                MachineAutomationHandler.toggle(); 
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