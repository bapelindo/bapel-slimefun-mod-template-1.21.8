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
 */
public class SlimefunDataLoader {
    private static final Gson GSON = new Gson();
    private static final Map<String, SlimefunMachineData> MACHINES = new HashMap<>();
    private static final Map<String, List<String>> RECIPES = new HashMap<>();
    private static boolean loaded = false;
    
    /**
     * Load all Slimefun data from multiple JSON files
     */
    public static void loadData() {
        if (loaded) return;
        
        try {
            BapelSlimefunMod.LOGGER.info("Starting to load Slimefun data...");
            
            // Load items data (contains basic item info)
            loadItemsData();
            
            // Load recipes data (contains crafting recipes) - this also loads machines
            loadRecipesData();
            
            // Merge recipes into machine data
            mergeRecipesIntoMachines();
            
            loaded = true;
            BapelSlimefunMod.LOGGER.info("Successfully loaded {} Slimefun machines with recipes", MACHINES.size());
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to load Slimefun data", e);
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
                BapelSlimefunMod.LOGGER.warn("Could not find slimefun_items.json - skipping items data");
                return;
            }
            
            JsonArray items = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                JsonArray.class
            );
            
            BapelSlimefunMod.LOGGER.info("Loaded {} items from slimefun_items.json", items.size());
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to load items data", e);
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
                BapelSlimefunMod.LOGGER.error("Could not find slimefun_machines.json!");
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
                
                // Store by title for quick lookup
                MACHINES.put(cleanTitle(inventoryTitle), data);
                count++;
            }
            
            BapelSlimefunMod.LOGGER.info("Loaded {} machines from slimefun_machines.json", count);
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to load machines data", e);
        }
    }
    
    /**
     * Load recipes from slimefun_recipes.json
     */
    private static void loadRecipesData() {
        try {
            InputStream stream = SlimefunDataLoader.class
                .getResourceAsStream("/assets/bapel-slimefun-mod/slimefun_recipes.json");
            
            if (stream == null) {
                BapelSlimefunMod.LOGGER.warn("Could not find slimefun_recipes.json - machines will have no recipes");
                loadMachinesData(); // Still load machines without recipes
                return;
            }
            
            JsonArray recipes = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                JsonArray.class
            );
            
            int count = 0;
            for (JsonElement element : recipes) {
                JsonObject obj = element.getAsJsonObject();
                
                String id = obj.get("id").getAsString();
                
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
            
            BapelSlimefunMod.LOGGER.info("Loaded {} recipes from slimefun_recipes.json", count);
            
            // Now load machines after recipes are loaded
            loadMachinesData();
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to load recipes data", e);
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
        
        BapelSlimefunMod.LOGGER.info("Merged recipes into {} machines", mergedCount);
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
     * Clean title by removing Minecraft color codes
     */
    private static String cleanTitle(String title) {
        return title.replaceAll("§[0-9a-fk-or]", "");
    }
    
    /**
     * Get machine data by GUI title
     */
    public static SlimefunMachineData getMachineByTitle(String title) {
        return MACHINES.get(cleanTitle(title));
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
}