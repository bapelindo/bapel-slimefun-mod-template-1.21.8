package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Manager untuk menyimpan dan mengingat recipe yang terakhir dipilih per machine
 * Digunakan untuk Auto Mode - menyimpan recipe history per machine
 */
public class RecipeMemoryManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String MEMORY_FILE = "recipe_memory.json";
    
    // Map: machineId -> recipeId
    private static Map<String, String> recipeMemory = new HashMap<>();
    private static boolean isLoaded = false;
    
    /**
     * Load recipe memory from file
     */
    public static void load() {
        Path memoryPath = getMemoryPath();
        
        if (Files.exists(memoryPath)) {
            try (Reader reader = Files.newBufferedReader(memoryPath)) {
                recipeMemory = GSON.fromJson(reader, 
                    new TypeToken<Map<String, String>>(){}.getType());
                
                if (recipeMemory == null) {
                    recipeMemory = new HashMap<>();
                }
                
                BapelSlimefunMod.LOGGER.info("[RecipeMemory] Loaded {} machine recipes from memory", 
                    recipeMemory.size());
                isLoaded = true;
                return;
            } catch (Exception e) {
                BapelSlimefunMod.LOGGER.error("[RecipeMemory] Failed to load memory", e);
            }
        }
        
        // Create empty memory
        recipeMemory = new HashMap<>();
        isLoaded = true;
        BapelSlimefunMod.LOGGER.info("[RecipeMemory] Created new recipe memory");
    }
    
    /**
     * Save recipe memory to file
     */
    public static void save() {
        if (!isLoaded) {
            BapelSlimefunMod.LOGGER.warn("[RecipeMemory] Cannot save - memory not loaded");
            return;
        }
        
        Path memoryPath = getMemoryPath();
        
        try {
            // Create parent directories if they don't exist
            Files.createDirectories(memoryPath.getParent());
            
            // Write memory to file
            try (Writer writer = Files.newBufferedWriter(memoryPath)) {
                GSON.toJson(recipeMemory, writer);
                BapelSlimefunMod.LOGGER.info("[RecipeMemory] Saved {} machine recipes", 
                    recipeMemory.size());
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[RecipeMemory] Failed to save memory", e);
        }
    }
    
    /**
     * Remember recipe untuk machine tertentu
     * @param machineId ID dari machine
     * @param recipeId ID dari recipe yang dipilih
     */
    public static void rememberRecipe(String machineId, String recipeId) {
        if (!isLoaded) {
            load();
        }
        
        if (machineId == null || recipeId == null) {
            BapelSlimefunMod.LOGGER.warn("[RecipeMemory] Cannot remember null values");
            return;
        }
        
        recipeMemory.put(machineId, recipeId);
        save();
        
        BapelSlimefunMod.LOGGER.info("[RecipeMemory] Remembered recipe '{}' for machine '{}'", 
            recipeId, machineId);
    }
    
    /**
     * Get recipe yang terakhir diingat untuk machine tertentu
     * @param machineId ID dari machine
     * @return Recipe ID yang terakhir digunakan, atau null jika tidak ada
     */
    public static String getRememberedRecipe(String machineId) {
        if (!isLoaded) {
            load();
        }
        
        if (machineId == null) {
            return null;
        }
        
        String recipeId = recipeMemory.get(machineId);
        
        if (recipeId != null) {
            BapelSlimefunMod.LOGGER.debug("[RecipeMemory] Retrieved recipe '{}' for machine '{}'", 
                recipeId, machineId);
        }
        
        return recipeId;
    }
    
    /**
     * Forget recipe untuk machine tertentu
     * @param machineId ID dari machine
     */
    public static void forgetRecipe(String machineId) {
        if (!isLoaded) {
            load();
        }
        
        if (machineId == null) {
            return;
        }
        
        recipeMemory.remove(machineId);
        save();
        
        BapelSlimefunMod.LOGGER.info("[RecipeMemory] Forgot recipe for machine '{}'", machineId);
    }
    
    /**
     * Clear all recipe memory
     */
    public static void clearAll() {
        if (!isLoaded) {
            load();
        }
        
        int count = recipeMemory.size();
        recipeMemory.clear();
        save();
        
        BapelSlimefunMod.LOGGER.info("[RecipeMemory] Cleared all {} recipes from memory", count);
    }
    
    /**
     * Check apakah machine memiliki recipe yang diingat
     * @param machineId ID dari machine
     * @return true jika ada recipe yang diingat
     */
    public static boolean hasRememberedRecipe(String machineId) {
        if (!isLoaded) {
            load();
        }
        
        return machineId != null && recipeMemory.containsKey(machineId);
    }
    
    /**
     * Get total jumlah machine yang memiliki recipe tersimpan
     * @return Jumlah machine dengan recipe tersimpan
     */
    public static int getMemoryCount() {
        if (!isLoaded) {
            load();
        }
        
        return recipeMemory.size();
    }
    
    /**
     * Get path untuk memory file
     */
    private static Path getMemoryPath() {
        return Paths.get("config", MEMORY_FILE);
    }
    
    /**
     * Get snapshot dari seluruh recipe memory (untuk debugging)
     */
    public static Map<String, String> getMemorySnapshot() {
        if (!isLoaded) {
            load();
        }
        
        return new HashMap<>(recipeMemory);
    }
}