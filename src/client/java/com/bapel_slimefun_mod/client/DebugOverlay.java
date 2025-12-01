package com.bapel_slimefun_mod.client;

import com.bapel_slimefun_mod.automation.AutomationUtils;
import com.bapel_slimefun_mod.automation.MachineAutomationHandler;
import com.bapel_slimefun_mod.automation.SlimefunMachineData;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * Overlay Debug untuk menampilkan data internal mesin dan slot secara realtime
 */
public class DebugOverlay {

    private static boolean visible = false;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_HEADER = 0xFFFFFF55; // Yellow
    private static final int COLOR_VALUE = 0xFFAAAAAA;  // Gray
    private static final int COLOR_ACTIVE = 0xFF55FF55; // Green
    private static final int COLOR_INACTIVE = 0xFFFF5555; // Red

    /**
     * Toggle visibility overlay
     */
    public static void toggle() {
        visible = !visible;
    }

    public static boolean isVisible() {
        return visible;
    }

    /**
     * Register HUD render callback
     */
    public static void register() {
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            if (visible) {
                render(graphics);
            }
        });
    }

    private static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int x = 10;
        int y = 10;
        int lineHeight = 10;

        // --- Header ---
        graphics.drawString(mc.font, "§6[Bapel Slimefun Debug]", x, y, COLOR_WHITE);
        y += lineHeight * 2;

        // --- Automation Status ---
        boolean enabled = MachineAutomationHandler.isAutomationEnabled();
        boolean active = MachineAutomationHandler.isActive();
        
        graphics.drawString(mc.font, "Automation: " + (enabled ? "§aENABLED" : "§cDISABLED"), x, y, COLOR_WHITE);
        y += lineHeight;
        graphics.drawString(mc.font, "Active State: " + (active ? "§aACTIVE" : "§cIDLE"), x, y, COLOR_WHITE);
        y += lineHeight * 2;

        // --- Machine Info ---
        SlimefunMachineData machine = MachineAutomationHandler.getCurrentMachine();
        if (machine != null) {
            graphics.drawString(mc.font, "§e--- Current Machine ---", x, y, COLOR_HEADER);
            y += lineHeight;
            
            drawKeyValue(graphics, mc, "Name", machine.getName(), x, y); y += lineHeight;
            drawKeyValue(graphics, mc, "ID", machine.getId(), x, y); y += lineHeight;
            drawKeyValue(graphics, mc, "Title", machine.getInventoryTitle(), x, y); y += lineHeight;
            
            String inSlots = Arrays.toString(machine.getInputSlots());
            String outSlots = Arrays.toString(machine.getOutputSlots());
            
            drawKeyValue(graphics, mc, "Input Slots", inSlots, x, y); y += lineHeight;
            drawKeyValue(graphics, mc, "Output Slots", outSlots, x, y); y += lineHeight;
            
            // --- Live Slot Data ---
            y += lineHeight;
            graphics.drawString(mc.font, "§e--- Live Slot Data ---", x, y, COLOR_HEADER);
            y += lineHeight;
            
            AbstractContainerMenu menu = mc.player.containerMenu;
            if (menu != null) {
                // Render Input Slots Content
                graphics.drawString(mc.font, "§7Input Items:", x, y, COLOR_WHITE);
                y += lineHeight;
                y = renderSlotsContent(graphics, mc, menu, machine.getInputSlots(), x + 5, y);
                
                // Render Output Slots Content
                graphics.drawString(mc.font, "§7Output Items:", x, y, COLOR_WHITE);
                y += lineHeight;
                y = renderSlotsContent(graphics, mc, menu, machine.getOutputSlots(), x + 5, y);
            }
            
        } else {
            graphics.drawString(mc.font, "§7No Machine Detected", x, y, COLOR_VALUE);
        }
    }

    private static void drawKeyValue(GuiGraphics graphics, Minecraft mc, String key, String value, int x, int y) {
        graphics.drawString(mc.font, "§7" + key + ": §f" + value, x, y, COLOR_WHITE);
    }

    private static int renderSlotsContent(GuiGraphics graphics, Minecraft mc, AbstractContainerMenu menu, int[] slots, int x, int y) {
        if (slots == null) return y;
        
        boolean hasItems = false;
        for (int slotIndex : slots) {
            if (slotIndex < menu.slots.size()) {
                Slot slot = menu.slots.get(slotIndex);
                ItemStack stack = slot.getItem();
                
                if (!stack.isEmpty()) {
                    hasItems = true;
                    String itemId = AutomationUtils.getItemId(stack);
                    String text = String.format("Slot %d: %dx %s", slotIndex, stack.getCount(), itemId);
                    graphics.drawString(mc.font, text, x, y, COLOR_ACTIVE);
                    y += 10;
                }
            }
        }
        
        if (!hasItems) {
            graphics.drawString(mc.font, "§8(Empty)", x, y, COLOR_VALUE);
            y += 10;
        }
        
        return y + 5; // Add padding
    }
}