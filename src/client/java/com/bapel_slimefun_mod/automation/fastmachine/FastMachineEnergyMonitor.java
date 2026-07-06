// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║  FILE 4 — FastMachineEnergyMonitor.java  (BARU)                            ║
// ║  src/client/java/com/bapel_slimefun_mod/automation/                        ║
// ╚══════════════════════════════════════════════════════════════════════════════╝
package com.bapel_slimefun_mod.automation.fastmachine;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the FastMachine's energy level from {@link FastMachineGuiLayout#ENERGY_SLOT}'s
 * lore/name text (commonly formatted like {@code "⚡ 12,450 / 50,000 J"}).
 *
 * @author bapelindo
 */
public final class FastMachineEnergyMonitor {

    private FastMachineEnergyMonitor() {}

    private static final Pattern ENERGY_PATTERN = Pattern.compile("([\\d.,]+)\\s*/\\s*([\\d.,]+)");

    public static final double CRITICAL_THRESHOLD    = 0.05;
    public static final double SAFE_RESUME_THRESHOLD = 0.15;

    private static volatile long   lastCurrent  = -1;
    private static volatile long   lastMax      = -1;
    private static volatile double lastFraction = 1.0;

    public static Reading read(AbstractContainerMenu menu) {
        try {
            if (menu == null || FastMachineGuiLayout.ENERGY_SLOT >= menu.slots.size()) {
                return Reading.UNKNOWN;
            }
            ItemStack stack = menu.slots.get(FastMachineGuiLayout.ENERGY_SLOT).getItem();
            if (stack == null || stack.isEmpty()) return Reading.UNKNOWN;

            for (String line : extractLoreLines(stack)) {
                Reading r = tryParse(FastMachineDetector.stripColorCodes(line));
                if (r.valid()) return r;
            }
            Reading r = tryParse(FastMachineDetector.stripColorCodes(stack.getHoverName().getString()));
            if (r.valid()) return r;
        } catch (Exception ignored) {}
        return Reading.UNKNOWN;
    }

    public static double getLastFraction()     { return (lastMax > 0) ? lastFraction : 1.0; }
    public static long   getLastCurrentEnergy() { return lastCurrent; }
    public static long   getLastMaxEnergy()     { return lastMax; }

    private static Reading tryParse(String clean) {
        Matcher matcher = ENERGY_PATTERN.matcher(clean);
        if (matcher.find()) {
            long current = parseNumber(matcher.group(1));
            long max     = parseNumber(matcher.group(2));
            if (max > 0) {
                double fraction = Math.min(1.0, Math.max(0.0, (double) current / max));
                lastCurrent  = current;
                lastMax      = max;
                lastFraction = fraction;
                return new Reading(current, max, fraction, true);
            }
        }
        return Reading.UNKNOWN;
    }

    private static List<String> extractLoreLines(ItemStack stack) {
        try {
            var lore = stack.get(DataComponents.LORE);
            if (lore == null) return List.of();
            return lore.lines().stream().map(c -> c.getString()).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static long parseNumber(String raw) {
        try {
            return Long.parseLong(raw.replaceAll("[.,]", ""));
        } catch (Exception e) {
            return 0L;
        }
    }

    public record Reading(long current, long max, double fraction, boolean valid) {
        public static final Reading UNKNOWN = new Reading(-1, -1, 1.0, false);
        public boolean isCritical()  { return valid && fraction <= CRITICAL_THRESHOLD; }
        public boolean isSafeToRun() { return !valid || fraction >= SAFE_RESUME_THRESHOLD; }
    }
}