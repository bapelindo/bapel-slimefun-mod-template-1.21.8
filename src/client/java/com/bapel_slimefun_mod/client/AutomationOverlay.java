package com.bapel_slimefun_mod.client;

import com.bapel_slimefun_mod.automation.MachineAutomationHandler;
import com.bapel_slimefun_mod.automation.SlimefunMachineData;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Renders automation status overlay on screen
 */
public class AutomationOverlay {
    
    private static final int PADDING = 5;
    private static final int LINE_HEIGHT = 10;
    
    /**
     * Register the HUD overlay
     */
    public static void register() {
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            
            // Only show when in a container screen
            if (!(mc.screen instanceof AbstractContainerScreen)) {
                return;
            }
            
            // Only show if automation is active on a machine
            if (!MachineAutomationHandler.isActive()) {
                return;
            }
            
            renderOverlay(guiGraphics, mc);
        });
    }
    
    /**
     * Render the overlay
     */
    private static void renderOverlay(GuiGraphics graphics, Minecraft mc) {
        // Check if overlay should be shown
        if (MachineAutomationHandler.getConfig() != null && 
            !MachineAutomationHandler.getConfig().isShowOverlay()) {
            return;
        }
        
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        // Position: top-right corner
        int x = screenWidth - 150;
        int y = PADDING;
        
        SlimefunMachineData machine = MachineAutomationHandler.getCurrentMachine();
        boolean enabled = MachineAutomationHandler.isAutomationEnabled();
        
        // Background
        int bgColor = enabled ? 0x80000000 : 0x80400000; // Semi-transparent black or dark red
        graphics.fill(x - PADDING, y - PADDING, x + 140, y + 40, bgColor);
        
        // Border
        int borderColor = enabled ? 0xFF00FF00 : 0xFFFF0000; // Green or red
        graphics.fill(x - PADDING, y - PADDING, x + 140, y - PADDING + 1, borderColor); // Top
        graphics.fill(x - PADDING, y + 40 - 1, x + 140, y + 40, borderColor); // Bottom
        graphics.fill(x - PADDING, y - PADDING, x - PADDING + 1, y + 40, borderColor); // Left
        graphics.fill(x + 140 - 1, y - PADDING, x + 140, y + 40, borderColor); // Right
        
        // Title
        String title = "§6§lSlimefun Automation";
        graphics.drawString(mc.font, title, x, y, 0xFFFFFF);
        y += LINE_HEIGHT;
        
        // Status
        String status = enabled ? "§a§lENABLED" : "§c§lDISABLED";
        graphics.drawString(mc.font, "Status: " + status, x, y, 0xFFFFFF);
        y += LINE_HEIGHT;
        
        // Machine name
        if (machine != null) {
            String machineName = machine.getName();
            // Remove color codes for display
            machineName = machineName.replaceAll("§[0-9a-fk-or]", "");
            if (machineName.length() > 20) {
                machineName = machineName.substring(0, 17) + "...";
            }
            graphics.drawString(mc.font, "§7Machine: §f" + machineName, x, y, 0xFFFFFF);
            y += LINE_HEIGHT;
        }
        
        // Toggle hint
        graphics.drawString(mc.font, "§8[K] to toggle", x, y, 0xFFFFFF);
    }
}