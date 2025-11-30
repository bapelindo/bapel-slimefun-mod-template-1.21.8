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
 * Loads and manages Slimefun machine data from JSON
 */
public class SlimefunDataLoader {
    private static final Gson GSON = new Gson();
    private static final Map<String, SlimefunMachineData> MACHINES = new HashMap<>();
    private static boolean loaded = false;
    
    /**
     * Load machine data from slimefun_full_export.json
     */
    public static void loadData() {
        if (loaded) return;
        
        try {
            InputStream stream = SlimefunDataLoader.class
                .getResourceAsStream("/assets/bapel-slimefun-mod/slimefun_full_export.json");
            
            if (stream == null) {
                BapelSlimefunMod.LOGGER.error("Could not find slimefun_full_export.json!");
                return;
            }
            
            JsonArray items = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                JsonArray.class
            );
            
            int count = 0;
            for (JsonElement element : items) {
                JsonObject obj = element.getAsJsonObject();
                
                // Only process items with machineData
                if (!obj.has("machineData")) continue;
                
                JsonObject machineData = obj.getAsJsonObject("machineData");
                
                String id = obj.get("id").getAsString();
                String name = obj.has("name") ? obj.get("name").getAsString() : id;
                String inventoryTitle = machineData.get("inventoryTitle").getAsString();
                
                // Parse input slots
                int[] inputSlots = parseSlotArray(machineData.get("inputSlots"));
                
                // Parse output slots
                int[] outputSlots = parseSlotArray(machineData.get("outputSlots"));
                
                // Parse recipe
                List<String> recipe = new ArrayList<>();
                if (obj.has("recipe")) {
                    JsonArray recipeArray = obj.getAsJsonArray("recipe");
                    for (JsonElement recipeElement : recipeArray) {
                        recipe.add(recipeElement.getAsString());
                    }
                }
                
                // Parse energy data
                int energyCapacity = machineData.has("energyCapacity") 
                    ? machineData.get("energyCapacity").getAsInt() : 0;
                int energyConsumption = machineData.has("energyConsumption") 
                    ? machineData.get("energyConsumption").getAsInt() : 0;
                
                SlimefunMachineData data = new SlimefunMachineData(
                    id, name, inventoryTitle, inputSlots, outputSlots, 
                    recipe, energyCapacity, energyConsumption
                );
                
                // Store by title for quick lookup
                MACHINES.put(cleanTitle(inventoryTitle), data);
                count++;
            }
            
            loaded = true;
            BapelSlimefunMod.LOGGER.info("Loaded {} Slimefun machines", count);
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to load Slimefun data", e);
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
}