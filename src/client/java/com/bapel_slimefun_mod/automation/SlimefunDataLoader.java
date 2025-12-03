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
 * Loads machine data from slimefun_machines.json
 * Supports both electric machines and multiblock structures
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
            BapelSlimefunMod.LOGGER.info("[DataLoader] Successfully loaded {} Slimefun machines", 
                MACHINES.size());
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[DataLoader] Failed to load Slimefun data", e);
            loaded = true; // Prevent infinite retry
        }
    }
    
    /**
     * Load machines from slimefun_machines.json
     * Supports both electric and multiblock machines
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
            
            int loadedCount = 0;
            int multiblockCount = 0;
            
            for (JsonElement element : machines) {
                JsonObject obj = element.getAsJsonObject();
                
                if (!obj.has("id")) {
                    BapelSlimefunMod.LOGGER.warn("[DataLoader] Machine object missing 'id' field");
                    continue;
                }
                
                String id = obj.get("id").getAsString();
                String type = obj.has("type") ? obj.get("type").getAsString() : "ELECTRIC";
                
                SlimefunMachineData data;
                
                if ("MULTIBLOCK".equals(type)) {
                    // Load multiblock machine
                    data = loadMultiblockMachine(obj, id);
                    if (data != null) {
                        multiblockCount++;
                    }
                } else {
                    // Load electric machine
                    data = loadElectricMachine(obj, id);
                }
                
                if (data != null) {
                    // Store by ID and cleaned title
                    String inventoryTitle = data.getInventoryTitle();
                    String cleanedTitle = cleanTitle(inventoryTitle);
                    
                    MACHINES.put(cleanedTitle, data);
                    MACHINES.put(id, data); // Also store by ID for direct lookup
                    loadedCount++;
                    
                    if (loadedCount <= 10) {
                        BapelSlimefunMod.LOGGER.info("[DataLoader]   Loaded {}: '{}' ({})", 
                            type, id, cleanedTitle);
                    }
                }
            }
            
            BapelSlimefunMod.LOGGER.info("[DataLoader] Loaded {} machines ({} multiblock, {} electric)", 
                loadedCount, multiblockCount, loadedCount - multiblockCount);
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[DataLoader] Failed to load machines data", e);
            e.printStackTrace();
        }
    }
    
    /**
     * Load electric machine from JSON
     */
    private static SlimefunMachineData loadElectricMachine(JsonObject obj, String id) {
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
                // Take first recipe's inputs as default
                JsonObject firstRecipe = recipes.get(0).getAsJsonObject();
                if (firstRecipe.has("inputs")) {
                    JsonArray inputs = firstRecipe.getAsJsonArray("inputs");
                    for (JsonElement input : inputs) {
                        recipe.add(input.getAsString());
                    }
                }
            }
        }
        
        return new SlimefunMachineData(
            id, id, inventoryTitle, inputSlots, outputSlots, 
            recipe, energyCapacity, energyConsumption
        );
    }
    
    /**
     * Load multiblock machine from JSON
     */
    private static SlimefunMachineData loadMultiblockMachine(JsonObject obj, String id) {
        String inventoryTitle = id; // Multiblocks don't have inventory titles
        
        // Parse structure
        List<SlimefunMachineData.MultiblockStructure> structure = new ArrayList<>();
        if (obj.has("structure")) {
            JsonArray structureArray = obj.getAsJsonArray("structure");
            for (JsonElement elem : structureArray) {
                JsonObject block = elem.getAsJsonObject();
                String material = block.get("material").getAsString();
                String name = block.get("name").getAsString();
                structure.add(new SlimefunMachineData.MultiblockStructure(material, name));
            }
        }
        
        // Parse processing recipes
        List<String> recipe = new ArrayList<>();
        if (obj.has("processingRecipes")) {
            JsonArray recipes = obj.getAsJsonArray("processingRecipes");
            if (recipes.size() > 0) {
                // Take first recipe's inputs as default
                JsonObject firstRecipe = recipes.get(0).getAsJsonObject();
                if (firstRecipe.has("inputs")) {
                    JsonArray inputs = firstRecipe.getAsJsonArray("inputs");
                    for (JsonElement input : inputs) {
                        recipe.add(input.getAsString());
                    }
                }
            }
        }
        
        return new SlimefunMachineData(id, id, inventoryTitle, structure, recipe);
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
     */
    private static String cleanTitle(String title) {
        if (title == null || title.isEmpty()) {
            return "";
        }
        
        // Remove all Minecraft color codes (§ followed by any character)
        String cleaned = title.replaceAll("§.", "");
        
        // Remove alternative encoding (common in broken UTF-8)
        cleaned = cleaned.replaceAll("Â§.", "");
        cleaned = cleaned.replaceAll("Â", "");
        
        // Remove & color codes
        cleaned = cleaned.replaceAll("&[0-9a-fk-or]", "");
        
        // Normalize whitespace
        cleaned = cleaned.replaceAll("\\s+", " ");
        
        // Trim whitespace
        cleaned = cleaned.trim();
        
        return cleaned;
    }
    
    /**
     * Get machine by GUI title or ID
     */
    public static SlimefunMachineData getMachineByTitle(String title) {
        String cleanedTitle = cleanTitle(title);
        
        BapelSlimefunMod.LOGGER.info("[DataLoader] Looking for machine: '{}'", title);
        BapelSlimefunMod.LOGGER.info("[DataLoader] Cleaned: '{}'", cleanedTitle);
        
        // Try exact match by title
        SlimefunMachineData machine = MACHINES.get(cleanedTitle);
        
        if (machine != null) {
            BapelSlimefunMod.LOGGER.info("[DataLoader] ✓ Found exact match: {} ({})", 
                machine.getId(), machine.isMultiblock() ? "MULTIBLOCK" : "ELECTRIC");
            return machine;
        }
        
        // Try by ID
        machine = MACHINES.get(title);
        if (machine != null) {
            BapelSlimefunMod.LOGGER.info("[DataLoader] ✓ Found by ID: {} ({})", 
                machine.getId(), machine.isMultiblock() ? "MULTIBLOCK" : "ELECTRIC");
            return machine;
        }
        
        // Try fuzzy matching
        String fuzzyTitle = cleanedTitle.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        
        BapelSlimefunMod.LOGGER.warn("[DataLoader] ✗ No exact match, trying fuzzy: '{}'", fuzzyTitle);
        
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
        
        BapelSlimefunMod.LOGGER.warn("[DataLoader] ✗ No match found for '{}'", title);
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
}