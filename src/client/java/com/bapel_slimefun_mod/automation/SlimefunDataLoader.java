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
 * Loads and manages Slimefun machine data from JSON files
 * FIXED VERSION - Enhanced title matching and debug logging
 * FIX: Changed "id" to "itemId" to match JSON format
 */
public class SlimefunDataLoader {
    private static final Gson GSON = new Gson();
    private static final Map<String, SlimefunMachineData> MACHINES = new HashMap<>();
    private static final Map<String, List<String>> RECIPES = new HashMap<>();
    private static boolean loaded = false;
    private static boolean debugMode = false;
    
    /**
     * Enable/disable debug mode
     */
    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
        BapelSlimefunMod.LOGGER.info("[DataLoader] Debug mode: {}", enabled ? "ENABLED" : "DISABLED");
    }
    
    /**
     * Load all Slimefun data from multiple JSON files
     */
    public static void loadData() {
        if (loaded) return;
        
        try {
            BapelSlimefunMod.LOGGER.info("[DataLoader] Starting to load Slimefun data...");
            
            // Load items data (contains basic item info)
            loadItemsData();
            
            // Load recipes data (contains crafting recipes) - this also loads machines
            loadRecipesData();
            
            // Merge recipes into machine data
            mergeRecipesIntoMachines();
            
            loaded = true;
            BapelSlimefunMod.LOGGER.info("[DataLoader] Successfully loaded {} Slimefun machines with recipes", MACHINES.size());
            
            if (debugMode) {
                printLoadedMachines();
            }
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[DataLoader] Failed to load Slimefun data", e);
            // Set loaded to true anyway to prevent infinite retry
            loaded = true;
        }
    }
    
    /**
     * Load items data from slimefun_items.json
     */
    private static void loadItemsData() {
        try {
            InputStream stream = SlimefunDataLoader.class
                .getResourceAsStream("/assets/bapel-slimefun-mod/slimefun_items.json");
            
            if (stream == null) {
                BapelSlimefunMod.LOGGER.warn("[DataLoader] Could not find slimefun_items.json - skipping items data");
                return;
            }
            
            JsonArray items = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                JsonArray.class
            );
            
            BapelSlimefunMod.LOGGER.info("[DataLoader] Loaded {} items from slimefun_items.json", items.size());
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[DataLoader] Failed to load items data", e);
        }
    }
    
    /**
     * Load machine configurations from slimefun_machines.json
     */
    private static void loadMachinesData() {
        try {
            InputStream stream = SlimefunDataLoader.class
                .getResourceAsStream("/assets/bapel-slimefun-mod/slimefun_machines.json");
            
            if (stream == null) {
                BapelSlimefunMod.LOGGER.error("[DataLoader] Could not find slimefun_machines.json!");
                return;
            }
            
            JsonArray machines = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                JsonArray.class
            );
            
            int count = 0;
            for (JsonElement element : machines) {
                JsonObject obj = element.getAsJsonObject();
                
                String id = obj.get("id").getAsString();
                String inventoryTitle = obj.get("inventoryTitle").getAsString();
                
                // Parse input slots
                int[] inputSlots = parseSlotArray(obj.get("inputSlots"));
                
                // Parse output slots
                int[] outputSlots = parseSlotArray(obj.get("outputSlots"));
                
                // Parse energy data
                int energyCapacity = obj.has("energyCapacity") 
                    ? obj.get("energyCapacity").getAsInt() : 0;
                int energyConsumption = obj.has("energyConsumption") 
                    ? obj.get("energyConsumption").getAsInt() : 0;
                
                // Get recipe from RECIPES map (will be merged later)
                List<String> recipe = RECIPES.getOrDefault(id, new ArrayList<>());
                
                SlimefunMachineData data = new SlimefunMachineData(
                    id, id, inventoryTitle, inputSlots, outputSlots, 
                    recipe, energyCapacity, energyConsumption
                );
                
                // Store by CLEANED title for matching
                String cleanedTitle = cleanTitle(inventoryTitle);
                MACHINES.put(cleanedTitle, data);
                count++;
                
                if (debugMode && count <= 5) {
                    BapelSlimefunMod.LOGGER.info("[DataLoader]   Loaded: {} -> {}", inventoryTitle, cleanedTitle);
                }
            }
            
            BapelSlimefunMod.LOGGER.info("[DataLoader] Loaded {} machines from slimefun_machines.json", count);
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[DataLoader] Failed to load machines data", e);
        }
    }
    
    /**
     * Load recipes from slimefun_recipes.json
     * FIXED: Changed "id" to "itemId" to match JSON format
     */
    private static void loadRecipesData() {
        try {
            InputStream stream = SlimefunDataLoader.class
                .getResourceAsStream("/assets/bapel-slimefun-mod/slimefun_recipes.json");
            
            if (stream == null) {
                BapelSlimefunMod.LOGGER.warn("[DataLoader] Could not find slimefun_recipes.json - machines will have no recipes");
                loadMachinesData(); // Still load machines without recipes
                return;
            }
            
            JsonArray recipes = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                JsonArray.class
            );
            
            int count = 0;
            int skipped = 0;
            for (JsonElement element : recipes) {
                JsonObject obj = element.getAsJsonObject();
                
                // FIXED: Use "itemId" instead of "id" to match JSON format
                if (!obj.has("itemId")) {
                    if (debugMode) {
                        BapelSlimefunMod.LOGGER.warn("[DataLoader] Recipe object missing 'itemId' field, skipping");
                    }
                    skipped++;
                    continue;
                }
                
                String id = obj.get("itemId").getAsString();
                
                // Parse recipe inputs
                List<String> recipe = new ArrayList<>();
                if (obj.has("inputs")) {
                    JsonArray inputs = obj.getAsJsonArray("inputs");
                    for (JsonElement input : inputs) {
                        recipe.add(input.getAsString());
                    }
                }
                
                RECIPES.put(id, recipe);
                count++;
            }
            
            BapelSlimefunMod.LOGGER.info("[DataLoader] Loaded {} recipes from slimefun_recipes.json", count);
            if (skipped > 0) {
                BapelSlimefunMod.LOGGER.warn("[DataLoader] Skipped {} recipes without itemId", skipped);
            }
            
            // Now load machines after recipes are loaded
            loadMachinesData();
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[DataLoader] Failed to load recipes data", e);
            e.printStackTrace();
        }
    }
    
    /**
     * Merge loaded recipes into machine data
     */
    private static void mergeRecipesIntoMachines() {
        int mergedCount = 0;
        
        for (Map.Entry<String, SlimefunMachineData> entry : MACHINES.entrySet()) {
            SlimefunMachineData machine = entry.getValue();
            String machineId = machine.getId();
            
            // Get recipe from RECIPES map
            List<String> recipe = RECIPES.get(machineId);
            
            if (recipe != null && !recipe.isEmpty()) {
                // Create new machine data with recipe
                SlimefunMachineData updatedMachine = new SlimefunMachineData(
                    machine.getId(),
                    machine.getName(),
                    machine.getInventoryTitle(),
                    machine.getInputSlots(),
                    machine.getOutputSlots(),
                    recipe,
                    machine.getEnergyCapacity(),
                    machine.getEnergyConsumption()
                );
                
                // Update the machine in the map
                entry.setValue(updatedMachine);
                mergedCount++;
            }
        }
        
        BapelSlimefunMod.LOGGER.info("[DataLoader] Merged recipes into {} machines", mergedCount);
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
     * Clean title by removing Minecraft color codes and normalizing
     * FIXED: Better handling of color codes
     */
    private static String cleanTitle(String title) {
        if (title == null || title.isEmpty()) {
            return "";
        }
        
        // Remove Minecraft color codes (§ followed by any character)
        String cleaned = title.replaceAll("§.", "");
        
        // Also remove the alternative encoding (Â§ followed by character)
        cleaned = cleaned.replaceAll("Â§.", "");
        
        // Remove other common formatting
        cleaned = cleaned.replaceAll("&[0-9a-fk-or]", "");
        
        // Trim whitespace
        cleaned = cleaned.trim();
        
        return cleaned;
    }
    
    /**
     * Get machine data by GUI title
     * ENHANCED: Better logging for debugging
     */
    public static SlimefunMachineData getMachineByTitle(String title) {
        String cleanedTitle = cleanTitle(title);
        SlimefunMachineData machine = MACHINES.get(cleanedTitle);
        
        if (debugMode) {
            if (machine != null) {
                BapelSlimefunMod.LOGGER.info("[DataLoader] Found machine: {} -> {}", title, machine.getId());
            } else {
                BapelSlimefunMod.LOGGER.warn("[DataLoader] Machine not found for title: '{}' (cleaned: '{}')", title, cleanedTitle);
                BapelSlimefunMod.LOGGER.warn("[DataLoader] Available titles:");
                int count = 0;
                for (String key : MACHINES.keySet()) {
                    if (count++ < 5) {
                        BapelSlimefunMod.LOGGER.warn("[DataLoader]   - '{}'", key);
                    }
                }
                if (MACHINES.size() > 5) {
                    BapelSlimefunMod.LOGGER.warn("[DataLoader]   ... and {} more", MACHINES.size() - 5);
                }
            }
        }
        
        return machine;
    }
    
    /**
     * Check if a title matches a known machine
     */
    public static boolean isMachine(String title) {
        return MACHINES.containsKey(cleanTitle(title));
    }
    
    /**
     * Get all loaded machines
     */
    public static Map<String, SlimefunMachineData> getAllMachines() {
        return new HashMap<>(MACHINES);
    }
    
    /**
     * Get recipe for a specific item ID
     */
    public static List<String> getRecipe(String itemId) {
        return RECIPES.getOrDefault(itemId, new ArrayList<>());
    }
    
    /**
     * Print all loaded machines (for debugging)
     */
    public static void printLoadedMachines() {
        BapelSlimefunMod.LOGGER.info("╔════════════════════════════════════════╗");
        BapelSlimefunMod.LOGGER.info("║   Loaded Machines ({} total)              ║", MACHINES.size());
        BapelSlimefunMod.LOGGER.info("╠════════════════════════════════════════╣");
        
        int count = 0;
        for (Map.Entry<String, SlimefunMachineData> entry : MACHINES.entrySet()) {
            if (count++ < 10) {
                String title = entry.getKey();
                SlimefunMachineData machine = entry.getValue();
                BapelSlimefunMod.LOGGER.info("║ '{}' -> {}", title, machine.getId());
            }
        }
        
        if (MACHINES.size() > 10) {
            BapelSlimefunMod.LOGGER.info("║ ... and {} more machines", MACHINES.size() - 10);
        }
        
        BapelSlimefunMod.LOGGER.info("╚════════════════════════════════════════╝");
    }
    
    /**
     * Test title matching
     */
    public static void testTitleMatching(String title) {
        BapelSlimefunMod.LOGGER.info("╔════════════════════════════════════════╗");
        BapelSlimefunMod.LOGGER.info("║   Title Matching Test                     ║");
        BapelSlimefunMod.LOGGER.info("╠════════════════════════════════════════╣");
        BapelSlimefunMod.LOGGER.info("║ Input: '{}'", title);
        
        String cleaned = cleanTitle(title);
        BapelSlimefunMod.LOGGER.info("║ Cleaned: '{}'", cleaned);
        
        SlimefunMachineData machine = MACHINES.get(cleaned);
        if (machine != null) {
            BapelSlimefunMod.LOGGER.info("║ ✓ Match found!");
            BapelSlimefunMod.LOGGER.info("║   ID: {}", machine.getId());
            BapelSlimefunMod.LOGGER.info("║   Name: {}", machine.getName());
            BapelSlimefunMod.LOGGER.info("║   Input slots: {}", machine.getInputSlots().length);
            BapelSlimefunMod.LOGGER.info("║   Output slots: {}", machine.getOutputSlots().length);
        } else {
            BapelSlimefunMod.LOGGER.info("║ ✗ No match found");
            BapelSlimefunMod.LOGGER.info("║ Similar titles:");
            
            for (String key : MACHINES.keySet()) {
                if (key.toLowerCase().contains(cleaned.toLowerCase()) || 
                    cleaned.toLowerCase().contains(key.toLowerCase())) {
                    BapelSlimefunMod.LOGGER.info("║   - '{}'", key);
                }
            }
        }
        
        BapelSlimefunMod.LOGGER.info("╚════════════════════════════════════════╝");
    }
    
    /**
     * Check if data is loaded
     */
    public static boolean isLoaded() {
        return loaded;
    }
    
    /**
     * Reload all data
     */
    public static void reload() {
        BapelSlimefunMod.LOGGER.info("[DataLoader] Reloading all data...");
        MACHINES.clear();
        RECIPES.clear();
        loaded = false;
        loadData();
    }
}