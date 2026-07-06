package com.bapel_slimefun_mod.automation.fastmachine;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import com.bapel_slimefun_mod.client.TextDrawing;

/**
 * Renders a compact real-time status panel in the top-left corner of the screen
 * while a FastMachine GUI is open and automation is active.
 *
 * <h2>Displayed information</h2>
 * <pre>
 * ⚡ Fast Enhanced Crafting Table
 * 🔒 Blistering Ingot  [×64]
 * 🔋 Energy: 84% ▓▓▓▓▓▓▓▓░░
 * 📦 Target: 128 / 640  (ETA ~3m 42s)
 * ♻ Refill: ON
 * </pre>
 *
 * <p>Registered via Fabric's {@code HudRenderCallback} in the mod's client
 * initializer:
 * <pre>{@code
 * HudRenderCallback.EVENT.register((graphics, tickDelta) ->
 *     FastMachineHudOverlay.render(graphics));
 * }</pre>
 *
 * @author bapelindo
 */
public final class FastMachineHudOverlay {

    private FastMachineHudOverlay() {}

    private static final int PANEL_X       = 6;
    private static final int PANEL_Y       = 6;
    private static final int LINE_HEIGHT   = 11;
    private static final int PANEL_PADDING = 4;
    private static final int BG_COLOR      = 0x88000000; // semi-transparent black
    private static final int TEXT_COLOR    = 0xFFFFFF;

    /**
     * Draws the overlay if {@link FastMachineAutomationHandler#isActive()}.
     * No-op otherwise (near-zero overhead when no FastMachine is open).
     *
     * @param graphics the current frame's {@link GuiGraphicsExtractor}
     */
    public static void render(GuiGraphicsExtractor graphics) {
        if (!FastMachineAutomationHandler.isActive()) return;

        String machineTitle = FastMachineAutomationHandler.getCurrentMachineTitle();
        String machineId     = FastMachineAutomationHandler.getCurrentMachineId();
        String lockedRecipe  = FastMachineAutomationHandler.getLockedRecipeName();
        if (machineTitle == null || machineId == null) return;

        FastMachineRecipeMemory.RecipeEntry entry = FastMachineRecipeMemory.get(machineId);

        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("§b⚡ §f" + machineTitle);

        if (lockedRecipe != null) {
            String modeLabel = (entry != null) ? entry.craftMode.label : "×64";
            lines.add("§a🔒 §f" + lockedRecipe + " §7[" + modeLabel + "]");
        } else {
            lines.add("§7🔓 No recipe locked — press §eJ");
        }

        // Energy bar
        double fraction = FastMachineEnergyMonitor.getLastFraction();
        long   cur       = FastMachineEnergyMonitor.getLastCurrentEnergy();
        long   max       = FastMachineEnergyMonitor.getLastMaxEnergy();
        if (max > 0) {
            String energyColor = fraction <= FastMachineEnergyMonitor.CRITICAL_THRESHOLD ? "§c"
                                 : fraction <= FastMachineEnergyMonitor.SAFE_RESUME_THRESHOLD ? "§e"
                                 : "§a";
            lines.add(energyColor + "🔋 " + formatPercent(fraction) + "% " + buildBar(fraction, 10));
        }

        // Target progress
        if (entry != null && entry.targetCount > 0) {
            int pct = Math.min(100, (int) ((entry.craftedSinceTarget * 100.0) / entry.targetCount));
            lines.add("§d📦 Target: §f" + entry.craftedSinceTarget + " / " + entry.targetCount
                + " §7(" + pct + "%)");
        }

        // Refill state
        if (entry != null && entry.autoRefill) {
            lines.add("§b♻ Auto-Refill: §aON");
        }

        // Paused state
        if (FastMachineAutomationHandler.isPaused()) {
            lines.add("§c⏸ PAUSED — " + FastMachineAutomationHandler.getPauseReason());
        }

        drawPanel(graphics, lines);
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, java.util.List<String> lines) {
        Font font = net.minecraft.client.Minecraft.getInstance().font;

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, font.width(line));
        }

        int panelWidth  = maxWidth + PANEL_PADDING * 2;
        int panelHeight = lines.size() * LINE_HEIGHT + PANEL_PADDING * 2;

        graphics.fill(PANEL_X, PANEL_Y, PANEL_X + panelWidth, PANEL_Y + panelHeight, BG_COLOR);

        int textY = PANEL_Y + PANEL_PADDING;
        for (String line : lines) {
            TextDrawing.drawString(graphics, font, line, PANEL_X + PANEL_PADDING, textY, TEXT_COLOR);
            textY += LINE_HEIGHT;
        }
    }

    private static String formatPercent(double fraction) {
        return String.valueOf((int) Math.round(fraction * 100));
    }

    private static String buildBar(double fraction, int segments) {
        int filled = (int) Math.round(fraction * segments);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments; i++) {
            sb.append(i < filled ? "▓" : "░");
        }
        return sb.toString();
    }
}
