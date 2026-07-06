// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║  FILE 2 — FastMachineDetector.java  (BARU)                                 ║
// ║  src/client/java/com/bapel_slimefun_mod/automation/FastMachineDetector.java ║
// ╚══════════════════════════════════════════════════════════════════════════════╝
package com.bapel_slimefun_mod.automation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Detects whether a GUI window title belongs to a GuizhanCraft/FastMachines
 * machine and maps it to a canonical machine ID.
 *
 * <p>This is checked <b>before</b> {@link SlimefunDataLoader#getMachineByTitle}
 * inside {@link UnifiedAutomationManager#onMachineOpen(String)}, since "Fast ..."
 * titles are not part of the regular Slimefun machine dataset and would
 * otherwise trigger a false "Different machine - recipe cleared" message.
 *
 * @author bapelindo
 */
public final class FastMachineDetector {

    private FastMachineDetector() {}

    private static final Map<String, String> TITLE_TO_ID;

    static {
        Map<String, String> m = new HashMap<>(32);
        m.put("Fast Crafting Table",          "FAST_CRAFTING_TABLE");
        m.put("Fast Furnace",                 "FAST_FURNACE");
        m.put("Fast Enhanced Crafting Table", "FAST_ENHANCED_CRAFTING_TABLE");
        m.put("Fast Magic Workbench",         "FAST_MAGIC_WORKBENCH");
        m.put("Fast Armor Forge",             "FAST_ARMOR_FORGE");
        m.put("Fast Ore Crusher",             "FAST_ORE_CRUSHER");
        m.put("Fast Grind Stone",             "FAST_GRIND_STONE");
        m.put("Fast Compressor",              "FAST_COMPRESSOR");
        m.put("Fast Pressure Chamber",        "FAST_PRESSURE_CHAMBER");
        m.put("Fast Ore Washer",              "FAST_ORE_WASHER");
        m.put("Fast Panning Machine",         "FAST_PANNING_MACHINE");
        m.put("Fast Table Saw",               "FAST_TABLE_SAW");
        m.put("Fast Composter",               "FAST_COMPOSTER");
        m.put("Fast Juicer",                  "FAST_JUICER");
        m.put("Fast Smeltery",                "FAST_SMELTERY");
        m.put("Fast Ancient Altar",           "FAST_ANCIENT_ALTAR");
        m.put("Fast Infinity Workbench",      "FAST_INFINITY_WORKBENCH");
        m.put("Fast Mob Data Infuser",        "FAST_MOB_DATA_INFUSER");
        m.put("Fast SlimeFrame Foundry",      "FAST_SLIMEFRAME_FOUNDRY");
        TITLE_TO_ID = Collections.unmodifiableMap(m);
    }

    public static boolean isFastMachine(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) return false;
        String clean = stripColorCodes(rawTitle).trim();
        if (TITLE_TO_ID.containsKey(clean)) return true;
        return clean.toLowerCase().startsWith("fast ");
    }

    public static String getMachineId(String rawTitle) {
        if (rawTitle == null) return "FAST_UNKNOWN";
        String clean = stripColorCodes(rawTitle).trim();
        String known = TITLE_TO_ID.get(clean);
        if (known != null) return known;
        return clean.toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    public static String stripColorCodes(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fklmnorA-FKLMNOR]", "");
    }
}