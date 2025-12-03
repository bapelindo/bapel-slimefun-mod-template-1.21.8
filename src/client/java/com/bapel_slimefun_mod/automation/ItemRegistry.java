package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry untuk item-item Slimefun
 * Memuat data dari slimefun_items.json dan menyediakan lookup berdasarkan ID
 */
public class ItemRegistry {
    private static final Gson GSON = new Gson();
    private static final Map<String, SlimefunItemData> ITEMS_BY_ID = new HashMap<>();
    private static boolean initialized = false;
    
    /**
     * Data class untuk item Slimefun
     */
    public static class SlimefunItemData {
        private final String id;
        private final String name;
        private final String category;
        
        public SlimefunItemData(String id, String name, String category) {
            this.id = id;
            this.name = name;
            this.category = category;
        }
        
        public String getId() { 
            return id; 
        }
        
        public String getName() { 
            return name; 
        }
        
        public String getCategory() { 
            return category; 
        }
        
        @Override
        public String toString() {
            return String.format("SlimefunItem[%s: %s (%s)]", id, name, category);
        }
    }
    
    /**
     * Initialize the item registry
     */
    public static void initialize() {
        if (initialized) {
            BapelSlimefunMod.LOGGER.warn("ItemRegistry already initialized");
            return;
        }
        
        try {
            loadItems();
            initialized = true;
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to initialize Item Registry", e);
        }
    }
    
    /**
     * Load items from JSON file
     */
    private static void loadItems() {
        try {
            InputStream stream = ItemRegistry.class
                .getResourceAsStream("/assets/bapel-slimefun-mod/slimefun_items.json");
            
            if (stream == null) {
                BapelSlimefunMod.LOGGER.warn("Could not find slimefun_items.json");
                return;
            }
            
            JsonArray itemsArray = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                JsonArray.class
            );
            
            int loaded = 0;
            for (JsonElement element : itemsArray) {
                JsonObject itemObj = element.getAsJsonObject();
                
                try {
                    String id = itemObj.get("id").getAsString();
                    String name = itemObj.get("name").getAsString();
                    String category = itemObj.has("category") ? 
                        itemObj.get("category").getAsString() : "unknown";
                    
                    SlimefunItemData item = new SlimefunItemData(id, name, category);
                    ITEMS_BY_ID.put(id, item);
                    loaded++;
                    
                } catch (Exception e) {
                    BapelSlimefunMod.LOGGER.error("Failed to parse item: {}", itemObj, e);
                }
            }
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to load items", e);
        }
    }
    
    /**
     * Get item data by ID
     */
    public static SlimefunItemData getItem(String itemId) {
        return ITEMS_BY_ID.get(itemId.toUpperCase());
    }
    
    /**
     * Check if item exists
     */
    public static boolean hasItem(String itemId) {
        return ITEMS_BY_ID.containsKey(itemId.toUpperCase());
    }
    
    /**
     * Get all registered items
     */
    public static Map<String, SlimefunItemData> getAllItems() {
        return new HashMap<>(ITEMS_BY_ID);
    }
    
    /**
     * Get display name for item ID
     */
    public static String getDisplayName(String itemId) {
        SlimefunItemData item = getItem(itemId);
        if (item != null) {
            return item.getName();
        }
        
        // Fallback: format ID ke display name
        return formatItemName(itemId);
    }
    
    /**
     * Format item ID menjadi display name
     * Contoh: "GOLD_DUST" -> "Gold Dust"
     */
    private static String formatItemName(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return "Unknown Item";
        }
        
        String[] words = itemId.toLowerCase().split("_");
        StringBuilder displayName = new StringBuilder();
        
        for (String word : words) {
            if (displayName.length() > 0) {
                displayName.append(" ");
            }
            if (word.length() > 0) {
                displayName.append(Character.toUpperCase(word.charAt(0)))
                          .append(word.substring(1));
            }
        }
        
        return displayName.toString();
    }
    
    /**
     * Get items by category
     */
    public static Map<String, SlimefunItemData> getItemsByCategory(String category) {
        Map<String, SlimefunItemData> result = new HashMap<>();
        
        for (Map.Entry<String, SlimefunItemData> entry : ITEMS_BY_ID.entrySet()) {
            if (entry.getValue().getCategory().equalsIgnoreCase(category)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        
        return result;
    }
    
    /**
     * Clear registry (for reloading)
     */
    public static void clear() {
        ITEMS_BY_ID.clear();
        initialized = false;
    }
    
    /**
     * Reload items from file
     */
    public static void reload() {
        clear();
        initialize();
    }
    
    /**
     * Check if registry is initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Get registry size
     */
    public static int size() {
        return ITEMS_BY_ID.size();
    }
}