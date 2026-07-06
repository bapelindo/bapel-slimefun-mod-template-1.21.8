package com.bapel_slimefun_mod.automation.fastmachine;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Persists per-machine-type automation preferences across sessions.
 *
 * <h2>Storage</h2>
 * <pre>.minecraft/config/bapel-slimefun-mod/fastmachine_prefs.json</pre>
 *
 * <h2>Schema (v2 — adds target-count batch goals)</h2>
 * <pre>
 * {
 *   "FAST_ENHANCED_CRAFTING_TABLE": {
 *     "lockedRecipeDisplayName": "Blistering Ingot",
 *     "craftMode": "BULK_64",
 *     "autoRefill": true,
 *     "targetCount": 640,
 *     "craftedSinceTarget": 128
 *   }
 * }
 * </pre>
 *
 * @author bapelindo
 */
public final class FastMachineRecipeMemory {

    private FastMachineRecipeMemory() {}

    private static final Gson GSON      = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE    = "fastmachine_prefs.json";
    private static final Type  MAP_TYPE = new TypeToken<Map<String, RecipeEntry>>(){}.getType();

    private static Path savePath;
    private static volatile Map<String, RecipeEntry> store = new HashMap<>();

    /**
     * Automation preferences for a single FastMachine type.
     */
    public static final class RecipeEntry {

        /** Display name of the locked recipe, or {@code null} for manual mode. */
        public String lockedRecipeDisplayName = null;

        /** Click combination used for the CRAFT button. */
        public CraftMode craftMode = CraftMode.BULK_64;

        /** Whether inventory items auto-refill machine inputs. */
        public boolean autoRefill = false;

        /**
         * Target batch size (number of crafted items to produce before auto-stopping).
         * {@code 0} means "unlimited / no target" (craft forever).
         */
        public int targetCount = 0;

        /**
         * Number of items crafted toward {@link #targetCount} in the current batch.
         * Reset to 0 whenever a new target is set or the target is reached.
         */
        public int craftedSinceTarget = 0;

        public RecipeEntry() {}
    }

    /** Click combination sent to CRAFT_SLOT. */
    public enum CraftMode {
        SINGLE("×1",   "§7Left-click"),
        BULK_16("×16", "§7Right-click"),
        BULK_64("×64", "§7Shift+Left"),
        MAX("×MAX",    "§7Shift+Right");

        public final String label;
        public final String hint;

        CraftMode(String label, String hint) { this.label = label; this.hint = hint; }

        public CraftMode next() {
            CraftMode[] v = values();
            return v[(ordinal() + 1) % v.length];
        }

        /** Approximate items produced per successful craft click (for ETA estimates). */
        public int approxYieldMultiplier() {
            return switch (this) {
                case SINGLE  -> 1;
                case BULK_16 -> 16;
                case BULK_64 -> 64;
                case MAX     -> 128; // conservative estimate; actual depends on stack caps
            };
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────────

    public static void init() {
        savePath = FabricLoader.getInstance()
                               .getConfigDir()
                               .resolve("bapel-slimefun-mod")
                               .resolve(FILE);
        load();
    }

    public static void load() {
        if (savePath == null) return;
        if (!Files.exists(savePath)) { store = new HashMap<>(); return; }
        try (Reader r = new InputStreamReader(Files.newInputStream(savePath), StandardCharsets.UTF_8)) {
            Map<String, RecipeEntry> loaded = GSON.fromJson(r, MAP_TYPE);
            store = (loaded != null) ? loaded : new HashMap<>();
            BapelSlimefunMod.LOGGER.info("[FastMachineMemory] Loaded {} preference(s).", store.size());
        } catch (IOException e) {
            BapelSlimefunMod.LOGGER.error("[FastMachineMemory] Load failed: {}", e.getMessage());
            store = new HashMap<>();
        }
    }

    public static void save() {
        if (savePath == null) return;
        try {
            Files.createDirectories(savePath.getParent());
            try (Writer w = new OutputStreamWriter(Files.newOutputStream(savePath), StandardCharsets.UTF_8)) {
                GSON.toJson(store, w);
            }
        } catch (IOException e) {
            BapelSlimefunMod.LOGGER.error("[FastMachineMemory] Save failed: {}", e.getMessage());
        }
    }

    // ── API ─────────────────────────────────────────────────────────────────────

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

    /** Sets a new target batch size and resets progress to 0. */
    public static void setTargetCount(String machineId, int target) {
        RecipeEntry e = getOrCreate(machineId);
        e.targetCount        = Math.max(0, target);
        e.craftedSinceTarget = 0;
        put(machineId, e);
    }

    /** Adds {@code amount} to the crafted-since-target counter and saves. */
    public static void addCraftedProgress(String machineId, int amount) {
        RecipeEntry e = getOrCreate(machineId);
        e.craftedSinceTarget += amount;
        put(machineId, e);
    }

    /** Clears the target (sets to 0) and resets progress. */
    public static void clearTarget(String machineId) {
        setTargetCount(machineId, 0);
    }
}
