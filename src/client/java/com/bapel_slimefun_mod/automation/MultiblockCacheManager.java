package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-machine cache with position tracking
 * 
 * Stores multiple multiblock machines with their positions
 * Automatically detects which machine user is near based on position
 * Persists across game sessions
 */
public class MultiblockCacheManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CACHE_FILE = "multiblock_cache.json";
    private static final int DETECTION_RADIUS = 10; // Blocks
    
    // Map: Position Key -> Cached Machine Data
    private static final Map<String, CachedMultiblock> machineCache = new ConcurrentHashMap<>();
    private static boolean isLoaded = false;
    
    /**
     * Data class for cached multiblock
     */
    public static class CachedMultiblock {
        private final String machineId;
        private final String machineName;
        private final BlockPos position;
        private final long constructedTime;
        private String lastSelectedRecipe;
        
        public CachedMultiblock(String machineId, String machineName, BlockPos position) {
            this.machineId = machineId;
            this.machineName = machineName;
            this.position = position;
            this.constructedTime = System.currentTimeMillis();
            this.lastSelectedRecipe = null;
        }
        
        public String getMachineId() { 
            return machineId; 
        }
        
        public String getMachineName() { 
            return machineName; 
        }
        
        public BlockPos getPosition() { 
            return position; 
        }
        
        public long getConstructedTime() { 
            return constructedTime; 
        }
        
        public String getLastSelectedRecipe() { 
            return lastSelectedRecipe; 
        }
        
        public void setLastSelectedRecipe(String recipeId) {
            this.lastSelectedRecipe = recipeId;
        }
        
        @Override
        public String toString() {
            return String.format("%s at [%d, %d, %d]", 
                machineName, position.getX(), position.getY(), position.getZ());
        }
    }
    
    /**
     * Load cache from file
     */
    public static void load() {
        Path cachePath = getCachePath();
        
        if (Files.exists(cachePath)) {
            try (Reader reader = Files.newBufferedReader(cachePath)) {
                Map<String, CachedMultiblock> loaded = GSON.fromJson(reader, 
                    new TypeToken<Map<String, CachedMultiblock>>(){}.getType());
                
                if (loaded != null) {
                    machineCache.putAll(loaded);
                }
                
                isLoaded = true;
                return;
            } catch (Exception e) {
                BapelSlimefunMod.LOGGER.error("[MultiblockCache] Failed to load cache", e);
            }
        }
        
        isLoaded = true;
    }
    
    /**
     * Save cache to file
     */
    public static void save() {
        if (!isLoaded) return;
        
        Path cachePath = getCachePath();
        
        try {
            Files.createDirectories(cachePath.getParent());
            
            try (Writer writer = Files.newBufferedWriter(cachePath)) {
                GSON.toJson(machineCache, writer);
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[MultiblockCache] Failed to save cache", e);
        }
    }
    
    /**
     * Add machine to cache (called when "successfully constructed" appears)
     */
    public static void addMachine(SlimefunMachineData machine, BlockPos position) {
        if (!isLoaded) load();
        
        String posKey = getPositionKey(position);
        CachedMultiblock cached = new CachedMultiblock(
            machine.getId(), 
            machine.getName(), 
            position
        );
        
        machineCache.put(posKey, cached);
        save();
    }
    
    /**
     * Find nearest machine to player position
     */
    public static CachedMultiblock findNearestMachine(BlockPos playerPos) {
        if (!isLoaded) load();
        
        CachedMultiblock nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (CachedMultiblock machine : machineCache.values()) {
            double distance = playerPos.distSqr(machine.getPosition());
            
            if (distance < nearestDistance && distance <= (DETECTION_RADIUS * DETECTION_RADIUS)) {
                nearest = machine;
                nearestDistance = distance;
            }
        }
        
        return nearest;
    }
    
    /**
     * Get machine at specific position (exact match)
     */
    public static CachedMultiblock getMachineAt(BlockPos position) {
        if (!isLoaded) load();
        
        String posKey = getPositionKey(position);
        return machineCache.get(posKey);
    }
    
    /**
     * Update last selected recipe for a machine
     */
    public static void updateRecipe(BlockPos position, String recipeId) {
        if (!isLoaded) load();
        
        String posKey = getPositionKey(position);
        CachedMultiblock machine = machineCache.get(posKey);
        
        if (machine != null) {
            machine.setLastSelectedRecipe(recipeId);
            save();
        }
    }
    
    /**
     * Remove machine from cache
     */
    public static void removeMachine(BlockPos position) {
        if (!isLoaded) load();
        
        String posKey = getPositionKey(position);
        CachedMultiblock removed = machineCache.remove(posKey);
        
        if (removed != null) {
            save();
        }
    }
    
    /**
     * Clear all cached machines
     */
    public static void clearAll() {
        if (!isLoaded) load();
        
        machineCache.clear();
        save();
    }
    
    /**
     * Get all cached machines
     */
    public static Collection<CachedMultiblock> getAllMachines() {
        if (!isLoaded) load();
        return new ArrayList<>(machineCache.values());
    }
    
    /**
     * Get cache statistics
     */
    public static Map<String, Integer> getStatistics() {
        if (!isLoaded) load();
        
        Map<String, Integer> stats = new HashMap<>();
        
        for (CachedMultiblock machine : machineCache.values()) {
            stats.merge(machine.getMachineId(), 1, Integer::sum);
        }
        
        return stats;
    }
    
    /**
     * Get cache size
     */
    public static int size() {
        if (!isLoaded) load();
        return machineCache.size();
    }
    
    /**
     * Convert BlockPos to unique string key
     */
    private static String getPositionKey(BlockPos pos) {
        return String.format("%d,%d,%d", pos.getX(), pos.getY(), pos.getZ());
    }
    
    /**
     * Get cache file path
     */
    private static Path getCachePath() {
        return Paths.get("config", CACHE_FILE);
    }
}