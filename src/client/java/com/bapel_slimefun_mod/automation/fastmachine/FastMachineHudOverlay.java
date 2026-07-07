// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║  FILE 6 — FastMachineHudOverlay.java  (BARU)                               ║
// ║  src/client/java/com/bapel_slimefun_mod/automation/                        ║
// ╚══════════════════════════════════════════════════════════════════════════════╝
package com.bapel_slimefun_mod.automation.fastmachine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
// GuiGraphics di-rename jadi GuiGraphicsExtractor di Minecraft 26.1.
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact status panel drawn top-left while a FastMachine automation session
 * is active. Registered via {@code HudRenderCallback.EVENT} in
 * {@link com.bapel_slimefun_mod.BapelSlimefunMod#registerEventHandlers()}.
 *
 * @author bapelindo
 */
public final class FastMachineHudOverlay {

    private FastMachineHudOverlay() {}

    private static final int PANEL_X = 6, PANEL_Y = 6, LINE_HEIGHT = 11, PADDING = 4;
    // Sejak 1.21.6+, warna teks harus ARGB — RGB polos (tanpa alpha) akan render transparan/tidak kelihatan.
    private static final int BG_COLOR = 0x88000000, TEXT_COLOR = 0xFFFFFFFF;

    public static void render(GuiGraphicsExtractor graphics) {
        com.bapel_slimefun_mod.automation.AutomationStateMachine sm = com.bapel_slimefun_mod.automation.AutomationStateMachine.getGlobalInstance();
        boolean smRunning = (sm != null && sm.isRunning());
        if (!FastMachineAutomationHandler.isActive() && !smRunning) return;

        String title  = FastMachineAutomationHandler.getCurrentMachineTitle();
        String id     = FastMachineAutomationHandler.getCurrentMachineId();
        String locked = FastMachineAutomationHandler.getLockedRecipeName();

        List<String> lines = new ArrayList<>();
        if (smRunning) {
            lines.add("§b🤖 Auto State: §e" + sm.getCurrentState().name());
        }
        if (title != null) {
            lines.add("§b⚡ §f" + title);
        }

                FastMachineRecipeMemory.RecipeEntry entry = (id != null) ? FastMachineRecipeMemory.get(id) : null;

        if (locked != null) {
            String modeLabel = (entry != null) ? entry.craftMode.label : "×64";
            lines.add("§a🔒 §f" + locked + " §7[" + modeLabel + "]");
        } else {
            lines.add("§7🔓 No recipe locked — use panel");
        }

        double fraction = FastMachineEnergyMonitor.getLastFraction();
        long   max      = FastMachineEnergyMonitor.getLastMaxEnergy();
        if (max > 0) {
            String color = fraction <= FastMachineEnergyMonitor.CRITICAL_THRESHOLD ? "§c"
                         : fraction <= FastMachineEnergyMonitor.SAFE_RESUME_THRESHOLD ? "§e" : "§a";
            lines.add(color + "🔋 " + Math.round(fraction * 100) + "% " + buildBar(fraction, 10));
        }

        if (entry != null && entry.recipeQueue != null && !entry.recipeQueue.isEmpty() && entry.currentQueueIndex < entry.recipeQueue.size()) {
            FastMachineRecipeMemory.QueueEntry currentQueue = entry.recipeQueue.get(entry.currentQueueIndex);
            int pct = Math.min(100, (int) ((currentQueue.craftedCount * 100.0) / currentQueue.targetCount));
            lines.add("§b📋 Queue: §f" + (entry.currentQueueIndex + 1) + "/" + entry.recipeQueue.size());
            lines.add("§d📦 Progress: §f" + currentQueue.craftedCount + " / " + currentQueue.targetCount + " §7(" + pct + "%)");
        } else if (entry != null && entry.targetCount > 0) {
            int pct = Math.min(100, (int) ((entry.craftedSinceTarget * 100.0) / entry.targetCount));
            lines.add("§d📦 Target: §f" + entry.craftedSinceTarget + " / " + entry.targetCount + " §7(" + pct + "%)");
        }

        if (entry != null && entry.autoRefill) {
            lines.add("§b♻ Auto-Refill: §aON");
        }

        if (entry != null && entry.autoMatch) {
            lines.add("§d⚙ Auto-Match: §aON");
        }

        if (FastMachineAutomationHandler.isPaused()) {
            lines.add("§c⏸ PAUSED — " + FastMachineAutomationHandler.getPauseReason());
        }

        drawPanel(graphics, lines);
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, List<String> lines) {
        Font font = Minecraft.getInstance().font;
        int maxWidth = 0;
        for (String line : lines) maxWidth = Math.max(maxWidth, font.width(line));

        int panelWidth  = maxWidth + PADDING * 2;
        int panelHeight = lines.size() * LINE_HEIGHT + PADDING * 2;

        graphics.fill(PANEL_X, PANEL_Y, PANEL_X + panelWidth, PANEL_Y + panelHeight, BG_COLOR);

        int y = PANEL_Y + PADDING;
        for (String line : lines) {
            // drawString(...) di-rename jadi text(...) di GuiGraphicsExtractor.
            graphics.text(font, line, PANEL_X + PADDING, y, TEXT_COLOR, false);
            y += LINE_HEIGHT;
        }
    }

    private static String buildBar(double fraction, int segments) {
        int filled = (int) Math.round(fraction * segments);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments; i++) sb.append(i < filled ? "▓" : "░");
        return sb.toString();
    }
}