package com.bapel_slimefun_mod.automation;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.bapel_slimefun_mod.BapelSlimefunMod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FIXED VERSION - Loads machine data correctly from slimefun_machines.json
 */
public class SlimefunDataLoader {
    private static final Gson GSON = new Gson();
    private static final Map<String, SlimefunMachineData> MACHINES = new HashMap<>();
    private static boolean loaded = false;
    
    /**
     * Load all Slimefun data
     */
    public static void loadData() {
        if (loaded) return;
        
        try {
            BapelSlimefunMod.LOGGER.info("[DataLoader] Starting to load Slimefun data...");
            
            // Load machines from slimefun_machines.json
            loadMachinesData();
            
            loaded = true;
            BapelSlimefunMod.LOGGER.info("[DataLoader] Successfully loaded {} Slimefun machines with recipes", 
                MACHINES.size());
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[DataLoader] Failed to load Slimefun data", e);
            loaded = true; // Prevent infinite retry
        }
    }
    
    /**
     * Load machines from slimefun_machines.json
     * FIX: Now correctly reads "id" field from machines JSON
     */
    private static void loadMachinesData() {
        try {
            InputStream stream = SlimefunDataLoader.class
                .getResourceAsStream("/assets/bapel-slimefun-mod/slimefun_machines.json");
            
            if (stream == null) {
                BapelSlimefunMod.LOGGER.error("[DataLoader] Failed to load machines data");
                return;
            }
            
            JsonArray machines = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                JsonArray.class
            );
            
            int loaded = 0;
            for (JsonElement element : machines) {
                JsonObject obj = element.getAsJsonObject();
                
                // FIX: Check for "id" field existence
                if (!obj.has("id")) {
                    BapelSlimefunMod.LOGGER.warn("[DataLoader] Machine object missing 'id' field");
                    continue;
                }
                
                String id = obj.get("id").getAsString();
                String inventoryTitle = obj.has("inventoryTitle") ? 
                    obj.get("inventoryTitle").getAsString() : id;
                
                // Parse slots
                int[] inputSlots = parseSlotArray(obj.get("inputSlots"));
                int[] outputSlots = parseSlotArray(obj.get("outputSlots"));
                
                // Parse energy
                int energyCapacity = obj.has("energyCapacity") ? 
                    obj.get("energyCapacity").getAsInt() : 0;
                int energyConsumption = obj.has("energyConsumption") ? 
                    obj.get("energyConsumption").getAsInt() : 0;
                
                // Parse processing recipes
                List<String> recipe = new ArrayList<>();
                if (obj.has("processingRecipes")) {
                    JsonArray recipes = obj.getAsJsonArray("processingRecipes");
                    if (recipes.size() > 0) {
                        // For now, just take first recipe's inputs
                        JsonObject firstRecipe = recipes.get(0).getAsJsonObject();
                        if (firstRecipe.has("inputs")) {
                            JsonArray inputs = firstRecipe.getAsJsonArray("inputs");
                            for (JsonElement input : inputs) {
                                recipe.add(input.getAsString());
                            }
                        }
                    }
                }
                
                SlimefunMachineData data = new SlimefunMachineData(
                    id, id, inventoryTitle, inputSlots, outputSlots, 
                    recipe, energyCapacity, energyConsumption
                );
                
                // Store by CLEANED title for matching
                String cleanedTitle = cleanTitle(inventoryTitle);
                MACHINES.put(cleanedTitle, data);
                loaded++;
                
                if (loaded <= 5) {
                    BapelSlimefunMod.LOGGER.info("[DataLoader]   Loaded: '{}' -> '{}'", 
                        inventoryTitle, cleanedTitle);
                }
            }
            
            BapelSlimefunMod.LOGGER.info("[DataLoader] Loaded {} machines from slimefun_machines.json", loaded);
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[DataLoader] Failed to load machines data", e);
            e.printStackTrace();
        }
    }
    
    /**
     * Parse slot array from JSON
     */
    private static int[] parseSlotArray(JsonElement element) {
        if (element == null || !element.isJsonArray()) return new int[0];
        
        JsonArray array = element.getAsJsonArray();
        int[] result = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            result[i] = array.get(i).getAsInt();
        }
        return result;
    }
    
    /**
     * Clean title by removing color codes
     * FIX: Better regex pattern
     */
private static String cleanTitle(String title) {
    if (title == null || title.isEmpty()) {
        return "";
    }
    
    // Remove all Minecraft color codes (§ followed by any character)
    String cleaned = title.replaceAll("§.", "");
    
    // Remove alternative encoding (common in broken UTF-8)
    cleaned = cleaned.replaceAll("Â§.", "");
    cleaned = cleaned.replaceAll("Â", ""); // ✅ Remove standalone  characters
    
    // Remove & color codes
    cleaned = cleaned.replaceAll("&[0-9a-fk-or]", "");
    
    // Normalize whitespace (replace multiple spaces with single space)
    cleaned = cleaned.replaceAll("\\s+", " ");
    
    // Trim whitespace
    cleaned = cleaned.trim();
    
    return cleaned;
}
    
    /**
     * Get machine by GUI title
     * FIX: Enhanced logging and fallback matching
     */
/**
 * Get machine by GUI title
 * ENHANCED: Better fuzzy matching
 */
public static SlimefunMachineData getMachineByTitle(String title) {
    String cleanedTitle = cleanTitle(title);
    
    BapelSlimefunMod.LOGGER.info("[DataLoader] Looking for machine with title: '{}'", title);
    BapelSlimefunMod.LOGGER.info("[DataLoader] Cleaned title: '{}'", cleanedTitle);
    
    // Try exact match first
    SlimefunMachineData machine = MACHINES.get(cleanedTitle);
    
    if (machine != null) {
        BapelSlimefunMod.LOGGER.info("[DataLoader] ✓ Found exact match: {}", machine.getId());
        return machine;
    }
    
    // ✅ ENHANCED: Try fuzzy matching by removing ALL non-alphanumeric characters
    String fuzzyTitle = cleanedTitle.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    
    BapelSlimefunMod.LOGGER.warn("[DataLoader] ✗ No exact match found");
    BapelSlimefunMod.LOGGER.warn("[DataLoader] Trying fuzzy match with: '{}'", fuzzyTitle);
    
    for (Map.Entry<String, SlimefunMachineData> entry : MACHINES.entrySet()) {
        String key = entry.getKey();
        String fuzzyKey = key.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        
        if (fuzzyTitle.equals(fuzzyKey)) {
            BapelSlimefunMod.LOGGER.info("[DataLoader] ✓ Found fuzzy match: '{}' for '{}'", 
                key, cleanedTitle);
            return entry.getValue();
        }
    }
    
    // Final fallback: contains match
    for (Map.Entry<String, SlimefunMachineData> entry : MACHINES.entrySet()) {
        String key = entry.getKey();
        
        if (cleanedTitle.contains(key) || key.contains(cleanedTitle)) {
            BapelSlimefunMod.LOGGER.info("[DataLoader] ✓ Found contains match: '{}' for '{}'", 
                key, cleanedTitle);
            return entry.getValue();
        }
    }
    
    // Debug: Show available titles
    BapelSlimefunMod.LOGGER.warn("[DataLoader] Available machine titles:");
    int count = 0;
    for (String key : MACHINES.keySet()) {
        if (count++ < 10) {
            BapelSlimefunMod.LOGGER.warn("[DataLoader]   - '{}'", key);
        }
    }
    if (MACHINES.size() > 10) {
        BapelSlimefunMod.LOGGER.warn("[DataLoader]   ... and {} more", MACHINES.size() - 10);
    }
    
    return null;
}
    
    /**
     * Check if title is a machine
     */
    public static boolean isMachine(String title) {
        return getMachineByTitle(title) != null;
    }
    
    /**
     * Get all machines
     */
    public static Map<String, SlimefunMachineData> getAllMachines() {
        return new HashMap<>(MACHINES);
    }
    
    /**
     * Check if loaded
     */
    public static boolean isLoaded() {
        return loaded;
    }
    
    /**
     * Reload data
     */
    public static void reload() {
        BapelSlimefunMod.LOGGER.info("[DataLoader] Reloading...");
        MACHINES.clear();
        loaded = false;
        loadData();
    }
    
    /**
     * Print all loaded machines for debugging
     */
    public static void printLoadedMachines() {
        BapelSlimefunMod.LOGGER.info("╔════════════════════════════════════════╗");
        BapelSlimefunMod.LOGGER.info("║   Loaded Machines ({} total)", MACHINES.size());
        BapelSlimefunMod.LOGGER.info("╠════════════════════════════════════════╣");
        
        int count = 0;
        for (Map.Entry<String, SlimefunMachineData> entry : MACHINES.entrySet()) {
            if (count++ < 15) {
                String title = entry.getKey();
                SlimefunMachineData machine = entry.getValue();
                BapelSlimefunMod.LOGGER.info("║ '{}' -> {}", title, machine.getId());
            }
        }
        
        if (MACHINES.size() > 15) {
            BapelSlimefunMod.LOGGER.info("║ ... and {} more machines", MACHINES.size() - 15);
        }
        
        BapelSlimefunMod.LOGGER.info("╚════════════════════════════════════════╝");
    }
}