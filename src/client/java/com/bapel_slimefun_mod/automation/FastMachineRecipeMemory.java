// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║  FILE 3 — FastMachineRecipeMemory.java  (BARU)                             ║
// ║  Gaya persist mengikuti ModConfig.java: Gson sederhana + Paths.get("config")║
// ║  src/client/java/com/bapel_slimefun_mod/automation/                        ║
// ╚══════════════════════════════════════════════════════════════════════════════╝
package com.bapel_slimefun_mod.automation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Persists per-machine-type FastMachine automation preferences.
 *
 * <p>Storage location mirrors {@link com.bapel_slimefun_mod.config.ModConfig}'s
 * convention: a JSON file directly under the working directory's {@code config/}
 * folder (no Fabric-specific config-dir API is used, to stay consistent with the
 * rest of this codebase).
 *
 * @author bapelindo
 */
public final class FastMachineRecipeMemory {

    private FastMachineRecipeMemory() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("bapel-slimefun-mod");
    private static final Gson GSON      = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE    = "bapel-slimefun-mod-fastmachine.json";
    private static final Type  MAP_TYPE = new TypeToken<Map<String, RecipeEntry>>(){}.getType();

    private static Map<String, RecipeEntry> store = new HashMap<>();

    /** Automation preferences for a single FastMachine type. */
    public static final class RecipeEntry {
        public String  lockedRecipeDisplayName = null;
        public CraftMode craftMode             = CraftMode.BULK_64;
        public boolean autoRefill              = false;
        public int     targetCount             = 0;
        public int     craftedSinceTarget      = 0;
        public RecipeEntry() {}
    }

    /** Click combination sent to CRAFT_SLOT. */
    public enum CraftMode {
        SINGLE("×1"), BULK_16("×16"), BULK_64("×64"), MAX("×MAX");

        public final String label;
        CraftMode(String label) { this.label = label; }

        public CraftMode next() {
            CraftMode[] v = values();
            return v[(ordinal() + 1) % v.length];
        }

        public int approxYieldMultiplier() {
            return switch (this) {
                case SINGLE  -> 1;
                case BULK_16 -> 16;
                case BULK_64 -> 64;
                case MAX     -> 128;
            };
        }
    }

    public static void load() {
        Path path = getPath();
        if (!Files.exists(path)) { store = new HashMap<>(); return; }
        try (Reader r = Files.newBufferedReader(path)) {
            Map<String, RecipeEntry> loaded = GSON.fromJson(r, MAP_TYPE);
            store = (loaded != null) ? loaded : new HashMap<>();
            LOGGER.info("[FastMachineMemory] Loaded {} preference(s).", store.size());
        } catch (Exception e) {
            LOGGER.error("[FastMachineMemory] Load failed", e);
            store = new HashMap<>();
        }
    }

    public static void save() {
        Path path = getPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(store, w);
            }
        } catch (Exception e) {
            LOGGER.error("[FastMachineMemory] Save failed", e);
        }
    }

    private static Path getPath() {
        return Paths.get("config", FILE);
    }

    public static RecipeEntry get(String machineId) {
        return (machineId != null) ? store.get(machineId) : null;
    }

    public static RecipeEntry getOrCreate(String machineId) {
        return store.computeIfAbsent(machineId, k -> new RecipeEntry());
    }

    public static void put(String machineId, RecipeEntry entry) {
        if (machineId == null || entry == null) return;
        store.put(machineId, entry);
        save();
    }

    public static void lockRecipe(String machineId, String displayName) {
        RecipeEntry e = getOrCreate(machineId);
        e.lockedRecipeDisplayName = displayName;
        put(machineId, e);
    }

    public static void unlockRecipe(String machineId) {
        RecipeEntry e = getOrCreate(machineId);
        e.lockedRecipeDisplayName = null;
        put(machineId, e);
    }

    public static void setCraftMode(String machineId, CraftMode mode) {
        RecipeEntry e = getOrCreate(machineId);
        e.craftMode = mode;
        put(machineId, e);
    }

    public static void setAutoRefill(String machineId, boolean value) {
        RecipeEntry e = getOrCreate(machineId);
        e.autoRefill = value;
        put(machineId, e);
    }

    public static void setTargetCount(String machineId, int target) {
        RecipeEntry e = getOrCreate(machineId);
        e.targetCount        = Math.max(0, target);
        e.craftedSinceTarget = 0;
        put(machineId, e);
    }

    public static void addCraftedProgress(String machineId, int amount) {
        RecipeEntry e = getOrCreate(machineId);
        e.craftedSinceTarget += amount;
        put(machineId, e);
    }

    public static void clearTarget(String machineId) {
        setTargetCount(machineId, 0);
    }
}