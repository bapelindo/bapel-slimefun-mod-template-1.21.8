package com.bapel_slimefun_mod.client;

import com.bapel_slimefun_mod.automation.MachineAutomationHandler;
import com.bapel_slimefun_mod.automation.RecipeHandler;
import com.bapel_slimefun_mod.automation.SlimefunMachineData;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Enhanced overlay showing automation status and recipe progress
 */
public class AutomationOverlay {
    
    private static final int PADDING = 5;
    private static final int LINE_HEIGHT = 10;
    private static final int PROGRESS_BAR_WIDTH = 100;
    private static final int PROGRESS_BAR_HEIGHT = 4;
    
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
        int x = screenWidth - 200;
        int y = PADDING;
        
        SlimefunMachineData machine = MachineAutomationHandler.getCurrentMachine();
        boolean enabled = MachineAutomationHandler.isAutomationEnabled();
        
        // Get recipe summary
        RecipeHandler.RecipeSummary summary = MachineAutomationHandler.getRecipeSummary();
        
        // Calculate overlay height
        int overlayHeight = calculateOverlayHeight(machine, summary);
        
        // Background
        int bgColor = enabled ? 0x80000000 : 0x80400000;
        graphics.fill(x - PADDING, y - PADDING, x + 190, y + overlayHeight, bgColor);
        
        // Border
        int borderColor = enabled ? 0xFF00FF00 : 0xFFFF0000;
        drawBorder(graphics, x - PADDING, y - PADDING, 195, overlayHeight + PADDING, borderColor);
        
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
            String machineName = cleanColorCodes(machine.getName());
            if (machineName.length() > 25) {
                machineName = machineName.substring(0, 22) + "...";
            }
            graphics.drawString(mc.font, "§7Machine: §f" + machineName, x, y, 0xFFFFFF);
            y += LINE_HEIGHT;
        }
        
        // Recipe progress
        if (summary != null && !summary.getRequiredItems().isEmpty()) {
            y += 3; // Extra spacing
            
            // Progress bar
            float completion = summary.getCompletionPercentage();
            drawProgressBar(graphics, x, y, PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT, completion);
            y += PROGRESS_BAR_HEIGHT + 5;
            
            // Progress text
            String progressText = String.format("§7Recipe: §f%.0f%%", completion * 100);
            graphics.drawString(mc.font, progressText, x, y, 0xFFFFFF);
            y += LINE_HEIGHT;
            
            // Can craft indicator
            if (summary.canCraft()) {
                String craftText = String.format("§a✓ Can craft x%d", summary.getMaxCrafts());
                graphics.drawString(mc.font, craftText, x, y, 0xFFFFFF);
                y += LINE_HEIGHT;
            } else {
                graphics.drawString(mc.font, "§c✗ Missing items", x, y, 0xFFFFFF);
                y += LINE_HEIGHT;
                
                // Show missing items
                Map<String, Integer> missing = summary.getMissingItems();
                if (!missing.isEmpty() && MachineAutomationHandler.getConfig().isDebugMode()) {
                    y += 2;
                    graphics.drawString(mc.font, "§7Missing:", x, y, 0xFFFFFF);
                    y += LINE_HEIGHT;
                    
                    int count = 0;
                    for (Map.Entry<String, Integer> entry : missing.entrySet()) {
                        if (count >= 3) {
                            graphics.drawString(mc.font, "  §7...", x, y, 0xFFFFFF);
                            break;
                        }
                        
                        String itemName = formatItemName(entry.getKey());
                        String line = String.format("  §c%s §7x%d", itemName, entry.getValue());
                        graphics.drawString(mc.font, line, x, y, 0xFFFFFF);
                        y += LINE_HEIGHT;
                        count++;
                    }
                }
            }
        }
        
        // Toggle hint
        y += 3;
        graphics.drawString(mc.font, "§8[K] to toggle", x, y, 0xFFFFFF);
    }
    
    /**
     * Calculate overlay height based on content
     */
    private static int calculateOverlayHeight(SlimefunMachineData machine, RecipeHandler.RecipeSummary summary) {
        int height = LINE_HEIGHT * 3; // Title + Status + Machine
        
        if (summary != null && !summary.getRequiredItems().isEmpty()) {
            height += 3 + PROGRESS_BAR_HEIGHT + 5; // Spacing + bar + spacing
            height += LINE_HEIGHT; // Progress text
            height += LINE_HEIGHT; // Can craft / Missing
            
            if (!summary.canCraft() && MachineAutomationHandler.getConfig().isDebugMode()) {
                Map<String, Integer> missing = summary.getMissingItems();
                height += 2 + LINE_HEIGHT; // "Missing:" header
                height += LINE_HEIGHT * Math.min(3, missing.size()); // Up to 3 items
            }
        }
        
        height += 3 + LINE_HEIGHT; // Toggle hint
        
        return height;
    }
    
    /**
     * Draw a border around an area
     */
    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color); // Top
        graphics.fill(x, y + height - 1, x + width, y + height, color); // Bottom
        graphics.fill(x, y, x + 1, y + height, color); // Left
        graphics.fill(x + width - 1, y, x + width, y + height, color); // Right
    }
    
    /**
     * Draw a progress bar
     */
    private static void drawProgressBar(GuiGraphics graphics, int x, int y, int width, int height, float progress) {
        // Background (empty)
        graphics.fill(x, y, x + width, y + height, 0xFF333333);
        
        // Foreground (filled)
        int filledWidth = (int) (width * Math.min(1.0f, progress));
        
        // Color based on progress
        int fillColor;
        if (progress >= 1.0f) {
            fillColor = 0xFF00FF00; // Green
        } else if (progress >= 0.5f) {
            fillColor = 0xFFFFAA00; // Orange
        } else {
            fillColor = 0xFFFF0000; // Red
        }
        
        graphics.fill(x, y, x + filledWidth, y + height, fillColor);
        
        // Border
        graphics.fill(x, y, x + width, y + 1, 0xFF888888);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF888888);
        graphics.fill(x, y, x + 1, y + height, 0xFF888888);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF888888);
    }
    
    /**
     * Format item name for display (shorten if needed)
     */
    private static String formatItemName(String itemId) {
        // Remove underscores and capitalize
        String formatted = itemId.replace("_", " ");
        
        // Shorten if too long
        if (formatted.length() > 15) {
            formatted = formatted.substring(0, 12) + "...";
        }
        
        return formatted;
    }
    
    /**
     * Remove color codes from string
     */
    private static String cleanColorCodes(String text) {
        return text.replaceAll("§[0-9a-fk-or]", "");
    }
}