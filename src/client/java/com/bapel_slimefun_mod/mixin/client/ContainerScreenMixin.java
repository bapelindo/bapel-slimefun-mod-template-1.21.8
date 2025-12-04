package com.bapel_slimefun_mod.mixin.client;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.automation.*;
import com.bapel_slimefun_mod.client.ModKeybinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ✅ COMPLETE FIX: Detect multiblock when Dispenser GUI opens
 * 
 * STRATEGY:
 * 1. When player opens Dispenser GUI
 * 2. Find BlockEntity position from menu
 * 3. Pass EXACT position to MultiblockDetector
 * 4. Cache result if multiblock found
 */
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
                
                // ✅ SPECIAL HANDLING: Detect multiblock from Dispenser GUI
                if ("Dispenser".equalsIgnoreCase(titleString) && 
                    screen.getMenu() instanceof DispenserMenu) {
                    
                    BapelSlimefunMod.LOGGER.info("[Dispenser] Opened - starting detection...");
                    detectMultiblockFromDispenser((DispenserMenu) screen.getMenu());
                } else {
                    // Normal machine handling
                    UnifiedAutomationManager.onMachineOpen(titleString);
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("ERROR in onInit mixin", e);
        }
    }
    
    /**
     * ✅ NEW: Detect multiblock from opened Dispenser GUI
     */
    private void detectMultiblockFromDispenser(DispenserMenu menu) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Level level = mc.level;
            
            if (level == null) {
                BapelSlimefunMod.LOGGER.warn("[Dispenser] World is null");
                return;
            }
            
            // ✅ FIX: Get EXACT dispenser position from BlockEntity
            BlockPos dispenserPos = getDispenserPosition(menu, level);
            
            if (dispenserPos == null) {
                BapelSlimefunMod.LOGGER.warn("[Dispenser] Could not find dispenser position");
                return;
            }
            
            BapelSlimefunMod.LOGGER.info("[Detector] Starting detection at dispenser [{},{},{}]", 
                dispenserPos.getX(), dispenserPos.getY(), dispenserPos.getZ());
            
            // ✅ Validate it's actually a dispenser block
            if (level.getBlockState(dispenserPos).getBlock() != Blocks.DISPENSER) {
                BapelSlimefunMod.LOGGER.error("[Detector] Position is not a dispenser!");
                return;
            }
            
            // ✅ Run detection from EXACT dispenser position
            MultiblockDetector.DetectionResult result = MultiblockDetector.detect(level, dispenserPos);
            
            if (result != null) {
                // ✅ Get machine data
                SlimefunMachineData machine = SlimefunDataLoader.getMultiblockById(result.getMachineId());
                
                if (machine != null) {
                    BapelSlimefunMod.LOGGER.info("[Dispenser] ✓ Detected: {}", result.toString());
                    
                    // ✅ Cache machine
                    UnifiedAutomationManager.onMultiblockConstructed(machine);
                    
                    // ✅ Show success message
                    if (mc.player != null) {
                        mc.player.displayClientMessage(
                            Component.literal("§a✓ " + machine.getName() + " detected! Press R for recipes."),
                            false
                        );
                    }
                } else {
                    BapelSlimefunMod.LOGGER.warn("[Dispenser] Machine data not found for: {}", result.getMachineId());
                }
            } else {
                BapelSlimefunMod.LOGGER.info("[Dispenser] No multiblock pattern found - normal dispenser");
            }
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Dispenser] Error detecting multiblock", e);
        }
    }
    
    /**
     * ✅ NEW: Get exact dispenser position from BlockEntity
     */
    private BlockPos getDispenserPosition(DispenserMenu menu, Level level) {
        try {
            // Strategy 1: Search nearby for DispenserBlockEntity
            // (Container menus don't directly expose BlockEntity position in 1.21.8)
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return null;
            
            BlockPos playerPos = mc.player.blockPosition();
            
            // Search in 10 block radius for dispenser with matching inventory
            for (int x = -10; x <= 10; x++) {
                for (int y = -10; y <= 10; y++) {
                    for (int z = -10; z <= 10; z++) {
                        BlockPos pos = playerPos.offset(x, y, z);
                        
                        if (level.getBlockState(pos).getBlock() == Blocks.DISPENSER) {
                            BlockEntity be = level.getBlockEntity(pos);
                            
                            if (be instanceof DispenserBlockEntity) {
                                DispenserBlockEntity dispenser = (DispenserBlockEntity) be;
                                
                                // ✅ Verify this is the correct dispenser
                                // (Check if inventory size matches - DispenserMenu has 9 slots)
if (dispenser.getContainerSize() == menu.getSlot(0).container.getContainerSize()) {
                                    return pos;
                                }
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Dispenser] Error finding position", e);
        }
        
        return null;
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