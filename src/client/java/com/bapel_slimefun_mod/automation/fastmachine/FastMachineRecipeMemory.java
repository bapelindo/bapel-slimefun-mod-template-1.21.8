// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║  FILE 3 — FastMachineRecipeMemory.java  (BARU)                             ║
// ║  Gaya persist mengikuti ModConfig.java: Gson sederhana + Paths.get("config")║
// ║  src/client/java/com/bapel_slimefun_mod/automation/                        ║
// ╚══════════════════════════════════════════════════════════════════════════════╝
package com.bapel_slimefun_mod.automation.fastmachine;
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
import java.util.List;
import net.minecraft.core.BlockPos;

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

    private static Map<String, RecipeEntry> store = new java.util.concurrent.ConcurrentHashMap<>();

    public static final class QueueEntry {
        public String recipeName;
        public int targetCount;
        public int craftedCount;

        public QueueEntry() {}
        public QueueEntry(String recipeName, int targetCount) {
            this.recipeName = recipeName;
            this.targetCount = targetCount;
            this.craftedCount = 0;
        }
    }

    /** Automation preferences for a single FastMachine type. */
    public static final class RecipeEntry {
        public String  lockedRecipeDisplayName = null;
        public CraftMode craftMode             = CraftMode.BULK_64;
        public boolean autoRefill              = false;
        public int     targetCount             = 0;
        public int     craftedSinceTarget      = 0;

        // Multi-Recipe Queue
        public java.util.List<QueueEntry> recipeQueue = new java.util.ArrayList<>();
        public int currentQueueIndex = 0;

        public boolean autoMatch = false;
        public boolean manuallyPaused = true;

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
        loadPositions();
        Path path = getPath();
        if (!Files.exists(path)) { store = new java.util.concurrent.ConcurrentHashMap<>(); return; }
        try (Reader r = Files.newBufferedReader(path)) {
            Map<String, RecipeEntry> loaded = GSON.fromJson(r, MAP_TYPE);
            store = (loaded != null) ? new java.util.concurrent.ConcurrentHashMap<>(loaded) : new java.util.concurrent.ConcurrentHashMap<>();
            LOGGER.info("[FastMachineMemory] Loaded {} preference(s).", store.size());
        } catch (Exception e) {
            LOGGER.error("[FastMachineMemory] Load failed", e);
            store = new java.util.concurrent.ConcurrentHashMap<>();
        }
    }

    public static void save() {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            Path path = getPath();
            try {
                Files.createDirectories(path.getParent());
                Map<String, RecipeEntry> snapshot = new HashMap<>(store);
                try (Writer w = Files.newBufferedWriter(path)) {
                    GSON.toJson(snapshot, w);
                }
            } catch (Exception e) {
                LOGGER.error("[FastMachineMemory] Save failed", e);
            }
        });
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
    }

    public static void clearTarget(String machineId) {
        setTargetCount(machineId, 0);
    }

    public static void addToQueue(String machineId, String recipeName, int targetCount) {
        RecipeEntry e = getOrCreate(machineId);
        if (e.recipeQueue == null) {
            e.recipeQueue = new java.util.ArrayList<>();
        }
        e.recipeQueue.add(new QueueEntry(recipeName, targetCount));
        put(machineId, e);
    }

    public static void clearQueue(String machineId) {
        RecipeEntry e = getOrCreate(machineId);
        if (e.recipeQueue != null) {
            e.recipeQueue.clear();
        }
        e.currentQueueIndex = 0;
        put(machineId, e);
    }

    public static void advanceQueue(String machineId) {
        RecipeEntry e = getOrCreate(machineId);
        e.currentQueueIndex++;
        put(machineId, e);
    }

    public static void addQueueCraftedProgress(String machineId, int amount) {
        RecipeEntry e = getOrCreate(machineId);
        if (e.recipeQueue != null && e.currentQueueIndex >= 0 && e.currentQueueIndex < e.recipeQueue.size()) {
            e.recipeQueue.get(e.currentQueueIndex).craftedCount += amount;
        }
    }

    public static void removeQueueEntry(String machineId, int index) {
        RecipeEntry e = getOrCreate(machineId);
        if (e.recipeQueue != null && index >= 0 && index < e.recipeQueue.size()) {
            e.recipeQueue.remove(index);
            if (e.currentQueueIndex >= e.recipeQueue.size()) {
                e.currentQueueIndex = Math.max(0, e.recipeQueue.size() - 1);
            }
            if (e.recipeQueue.isEmpty()) {
                e.currentQueueIndex = 0;
                FastMachineAutomationHandler.unlockRecipe();
            } else {
                FastMachineAutomationHandler.setLockedRecipeNameDirect(e.recipeQueue.get(e.currentQueueIndex).recipeName);
            }
            put(machineId, e);
        }
    }

    public static void setAutoMatch(String machineId, boolean value) {
        RecipeEntry e = getOrCreate(machineId);
        e.autoMatch = value;
        put(machineId, e);
    }

    public static void setManuallyPaused(String machineId, boolean value) {
        RecipeEntry e = getOrCreate(machineId);
        e.manuallyPaused = value;
        put(machineId, e);
    }

    // ── Coordinate Cache for Hands-free Hopping ──────────────────────────────
    private static Map<String, List<BlockPos>> machinePositions = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String POS_FILE = "bapel-slimefun-mod-fastmachine-positions.json";
    private static final Type POS_MAP_TYPE = new TypeToken<Map<String, List<BlockPos>>>(){}.getType();

    public static void loadPositions() {
        Path path = Paths.get("config", POS_FILE);
        if (!Files.exists(path)) {
            machinePositions = new java.util.concurrent.ConcurrentHashMap<>();
            return;
        }
        try (Reader r = Files.newBufferedReader(path)) {
            Map<String, List<BlockPos>> loaded = GSON.fromJson(r, POS_MAP_TYPE);
            machinePositions = (loaded != null) 
                ? new java.util.concurrent.ConcurrentHashMap<>(loaded) 
                : new java.util.concurrent.ConcurrentHashMap<>();
        } catch (Exception e) {
            LOGGER.error("[FastMachineMemory] Failed to load positions", e);
            machinePositions = new java.util.concurrent.ConcurrentHashMap<>();
        }
    }

    public static void savePositions() {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            Path path = Paths.get("config", POS_FILE);
            try {
                Files.createDirectories(path.getParent());
                Map<String, List<BlockPos>> snapshot = new HashMap<>(machinePositions);
                try (Writer w = Files.newBufferedWriter(path)) {
                    GSON.toJson(snapshot, w);
                }
            } catch (Exception e) {
                LOGGER.error("[FastMachineMemory] Failed to save positions", e);
            }
        });
    }

    public static void cachePosition(String machineId, BlockPos pos) {
        if (machineId == null || pos == null) return;
        List<BlockPos> list = machinePositions.computeIfAbsent(machineId, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        if (!list.contains(pos)) {
            list.add(pos);
            savePositions();
        }
    }

    public static BlockPos getClosestPosition(String machineId, net.minecraft.world.phys.Vec3 playerPos, net.minecraft.world.level.Level level) {
        List<BlockPos> list = machinePositions.get(machineId);
        if (list == null || list.isEmpty()) return null;

        // Collect stale positions FIRST, then bulk-remove after iteration.
        // Modifying a CopyOnWriteArrayList mid-iteration is safe from CME but
        // yields undefined iteration order once the internal snapshot diverges.
        List<BlockPos> stale = new java.util.ArrayList<>();
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        for (BlockPos pos : list) {
            if (level != null && level.hasChunkAt(pos) && level.getBlockState(pos).isAir()) {
                stale.add(pos);
                continue;  // skip – this position is now air (machine removed)
            }
            double dist = pos.distToCenterSqr(playerPos);
            if (dist < closestDist) {
                closestDist = dist;
                closest = pos;
            }
        }

        if (!stale.isEmpty()) {
            list.removeAll(stale);
            savePositions();
        }
        return closest;
    }
}