package com.bapel_slimefun_mod.automation.fastmachine;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and tracks the FastMachine's energy level from {@link FastMachineGuiLayout#ENERGY_SLOT}.
 *
 * <h2>Why this matters</h2>
 * FastMachines <b>consume energy per craft</b>. If energy runs out mid-automation, the
 * craft button silently fails to produce output while the automation keeps clicking —
 * wasting ingredients that get "consumed" attempts and confusing the player. This monitor
 * reads the lore of the energy display item (typically formatted like
 * {@code "⚡ 12,450 / 50,000 J"}) and exposes a normalized 0.0–1.0 charge level so the
 * automation handler can pause gracefully and resume automatically.
 *
 * <h2>Parsing strategy</h2>
 * FastMachines/Slimefun energy lore commonly follows one of these patterns:
 * <pre>
 *   "⚡ 12,450 / 50,000 J"
 *   "Energy: 12450 / 50000"
 *   "§b⚡ §f12,450 §7/ §f50,000 J"
 * </pre>
 * We strip color codes, then apply a regex that extracts two numeric groups
 * separated by "/". This is resilient to most FastMachines locale variants,
 * including localized numbers with "," or "." thousand separators.
 *
 * @author bapelindo
 */
public final class FastMachineEnergyMonitor {

    private FastMachineEnergyMonitor() {}

    /** Matches "<number> / <number>" allowing thousands separators (, or .) */
    private static final Pattern ENERGY_PATTERN =
        Pattern.compile("([\\d.,]+)\\s*/\\s*([\\d.,]+)");

    /** Below this fraction (of max energy) the machine is considered "critical". */
    public static final double CRITICAL_THRESHOLD = 0.05; // 5%

    /** Above this fraction the machine is considered "safe" to resume automation. */
    public static final double SAFE_RESUME_THRESHOLD = 0.15; // 15%

    // ── Cached last reading (for HUD + rate display) ────────────────────────────
    private static volatile long   lastCurrentEnergy = -1;
    private static volatile long   lastMaxEnergy      = -1;
    private static volatile double lastFraction       = 1.0;

    /**
     * Reads and parses the energy display item from the given container menu.
     *
     * @param menu the open FastMachine container
     * @return a {@link Reading} describing current/max energy and fraction,
     *         or {@link Reading#UNKNOWN} if parsing failed (assumed safe to continue)
     */
    public static Reading read(AbstractContainerMenu menu) {
        try {
            if (menu == null || FastMachineGuiLayout.ENERGY_SLOT >= menu.slots.size()) {
                return Reading.UNKNOWN;
            }
            ItemStack stack = menu.slots.get(FastMachineGuiLayout.ENERGY_SLOT).getItem();
            if (stack == null || stack.isEmpty()) return Reading.UNKNOWN;

            List<String> lines = extractLoreLines(stack);
            for (String line : lines) {
                String clean = FastMachineDetector.stripColorCodes(line);
                Matcher matcher = ENERGY_PATTERN.matcher(clean);
                if (matcher.find()) {
                    long current = parseNumber(matcher.group(1));
                    long max     = parseNumber(matcher.group(2));
                    if (max > 0) {
                        double fraction = Math.min(1.0, Math.max(0.0, (double) current / max));
                        lastCurrentEnergy = current;
                        lastMaxEnergy     = max;
                        lastFraction      = fraction;
                        return new Reading(current, max, fraction, true);
                    }
                }
            }

            // Also check the item's own display name (some addons put energy in name, not lore)
            String name = FastMachineDetector.stripColorCodes(stack.getHoverName().getString());
            Matcher matcher = ENERGY_PATTERN.matcher(name);
            if (matcher.find()) {
                long current = parseNumber(matcher.group(1));
                long max     = parseNumber(matcher.group(2));
                if (max > 0) {
                    double fraction = Math.min(1.0, Math.max(0.0, (double) current / max));
                    lastCurrentEnergy = current;
                    lastMaxEnergy     = max;
                    lastFraction      = fraction;
                    return new Reading(current, max, fraction, true);
                }
            }
        } catch (Exception ignored) {
            // Parsing is best-effort; fall through to UNKNOWN
        }
        return Reading.UNKNOWN;
    }

    /** Returns the most recently successfully-parsed energy fraction (0.0–1.0), or 1.0 if never read. */
    public static double getLastFraction() {
        return (lastMaxEnergy > 0) ? lastFraction : 1.0;
    }

    /** Returns the most recently parsed current energy value, or -1 if never read. */
    public static long getLastCurrentEnergy() { return lastCurrentEnergy; }

    /** Returns the most recently parsed max energy value, or -1 if never read. */
    public static long getLastMaxEnergy() { return lastMaxEnergy; }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private static List<String> extractLoreLines(ItemStack stack) {
        try {
            var loreComponent = stack.get(net.minecraft.core.component.DataComponents.LORE);
            if (loreComponent == null) return List.of();
            return loreComponent.lines().stream()
                    .map(c -> c.getString())
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static long parseNumber(String raw) {
        try {
            String digitsOnly = raw.replaceAll("[.,]", "");
            return Long.parseLong(digitsOnly);
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * Immutable snapshot of a parsed energy reading.
     *
     * @param current  parsed current energy (J or arbitrary unit)
     * @param max      parsed max energy capacity
     * @param fraction current/max clamped to [0.0, 1.0]
     * @param valid    {@code true} if parsing succeeded
     */
    public record Reading(long current, long max, double fraction, boolean valid) {
        public static final Reading UNKNOWN = new Reading(-1, -1, 1.0, false);

        public boolean isCritical()   { return valid && fraction <= CRITICAL_THRESHOLD; }
        public boolean isSafeToRun()  { return !valid || fraction >= SAFE_RESUME_THRESHOLD; }
    }
}
