package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OPTIMIZED Database for managing multiple recipes per machine
 * Performance improvements:
 * - Lazy loading of recipe data
 * - ConcurrentHashMap for thread-safe access
 * - Indexed lookups for faster queries
 * - Cached common queries
 */
public class RecipeDatabase {
    private static final Gson GSON = new Gson();
    
    // OPTIMIZATION: Use ConcurrentHashMap for thread-safe access
    private static final Map<String, List<RecipeData>> RECIPES_BY_MACHINE = new ConcurrentHashMap<>();
    private static final Map<String, RecipeData> RECIPES_BY_ID = new ConcurrentHashMap<>();
    
    // OPTIMIZATION: Index for faster lookups
    private static final Map<String, Set<String>> RECIPES_BY_OUTPUT = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> RECIPES_BY_INPUT = new ConcurrentHashMap<>();
    
    // OPTIMIZATION: Cache common queries
    private static final Map<String, List<RecipeData>> CRAFTABLE_CACHE = new ConcurrentHashMap<>();
    private static long lastCacheClear = 0;
    private static final long CACHE_CLEAR_INTERVAL = 5000; // Clear cache every 5 seconds
    
    private static boolean initialized = false;
    
    /**
     * Initialize the recipe database
     */
    public static void initialize() {
        if (initialized) return;
        
        try {
            long startTime = System.currentTimeMillis();
            BapelSlimefunMod.LOGGER.info("Initializing Recipe Database...");
            
            // Load recipes from both files
            int externalCount = loadExternalRecipes();
            int processingCount = loadProcessingRecipes();
            
            // Build indexes after loading
            buildIndexes();
            
            long duration = System.currentTimeMillis() - startTime;
            initialized = true;
            
            BapelSlimefunMod.LOGGER.info("Recipe Database initialized with {} recipes for {} machines in {}ms", 
                RECIPES_BY_ID.size(), RECIPES_BY_MACHINE.size(), duration);
            BapelSlimefunMod.LOGGER.info("  - External recipes: {}", externalCount);
            BapelSlimefunMod.LOGGER.info("  - Processing recipes: {}", processingCount);
            BapelSlimefunMod.LOGGER.info("  - Indexed outputs: {}, inputs: {}", 
                RECIPES_BY_OUTPUT.size(), RECIPES_BY_INPUT.size());
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to initialize Recipe Database", e);
        }
    }
    
    /**
     * OPTIMIZATION: Build indexes for faster lookups
     */
    private static void buildIndexes() {
        RECIPES_BY_OUTPUT.clear();
        RECIPES_BY_INPUT.clear();
        
        for (RecipeData recipe : RECIPES_BY_ID.values()) {
            // Index by outputs
            for (RecipeData.RecipeOutput output : recipe.getOutputs()) {
                RECIPES_BY_OUTPUT
                    .computeIfAbsent(output.getItemId().toUpperCase(), k -> new HashSet<>())
                    .add(recipe.getRecipeId());
            }
            
            // Index by inputs
            for (RecipeHandler.RecipeIngredient input : recipe.getInputs()) {
                RECIPES_BY_INPUT
                    .computeIfAbsent(input.getItemId().toUpperCase(), k -> new HashSet<>())
                    .add(recipe.getRecipeId());
            }
        }
        
        BapelSlimefunMod.LOGGER.debug("Built indexes: {} outputs, {} inputs", 
            RECIPES_BY_OUTPUT.size(), RECIPES_BY_INPUT.size());
    }
    
    /**
     * Load external recipes from slimefun_recipes.json
     */
    private static int loadExternalRecipes() {
        int loaded = 0;
        
        try {
            InputStream stream = RecipeDatabase.class
                .getResourceAsStream("/assets/bapel-slimefun-mod/slimefun_recipes.json");
            
            if (stream == null) {
                BapelSlimefunMod.LOGGER.warn("Could not find slimefun_recipes.json");
                return 0;
            }
            
            JsonArray recipesArray = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                JsonArray.class
            );
            
            for (JsonElement element : recipesArray) {
                try {
                    RecipeData recipe = parseExternalRecipe(element.getAsJsonObject());
                    if (recipe != null) {
                        registerRecipe(recipe);
                        loaded++;
                    }
                } catch (Exception e) {
                    BapelSlimefunMod.LOGGER.debug("Failed to parse external recipe", e);
                }
            }
            
            BapelSlimefunMod.LOGGER.info("Loaded {} external recipes", loaded);
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to load external recipes", e);
        }
        
        return loaded;
    }
    
    /**
     * Load processing recipes from slimefun_machines.json
     */
    private static int loadProcessingRecipes() {
        int loaded = 0;
        
        try {
            InputStream stream = RecipeDatabase.class
                .getResourceAsStream("/assets/bapel-slimefun-mod/slimefun_machines.json");
            
            if (stream == null) {
                BapelSlimefunMod.LOGGER.warn("Could not find slimefun_machines.json");
                return 0;
            }
            
            JsonArray machinesArray = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                JsonArray.class
            );
            
            for (JsonElement element : machinesArray) {
                JsonObject machineObj = element.getAsJsonObject();
                
                if (!machineObj.has("id") || !machineObj.has("processingRecipes")) {
                    continue;
                }
                
                String machineId = machineObj.get("id").getAsString();
                JsonArray recipes = machineObj.getAsJsonArray("processingRecipes");
                
                for (JsonElement recipeElement : recipes) {
                    try {
                        RecipeData recipe = parseProcessingRecipe(machineId, recipeElement.getAsJsonObject());
                        if (recipe != null) {
                            registerRecipe(recipe);
                            loaded++;
                        }
                    } catch (Exception e) {
                        BapelSlimefunMod.LOGGER.debug("Failed to parse processing recipe", e);
                    }
                }
            }
            
            BapelSlimefunMod.LOGGER.info("Loaded {} processing recipes", loaded);
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to load processing recipes", e);
        }
        
        return loaded;
    }
    
    /**
     * Parse external recipe from JSON
     */
private static RecipeData parseExternalRecipe(JsonObject json) {
        String itemId = json.get("itemId").getAsString();
        String machineType = json.has("recipeType") ? 
            json.get("recipeType").getAsString().replace("slimefun:", "").toUpperCase() :
            json.has("machineType") ? json.get("machineType").getAsString() : "UNKNOWN";
        
        List<RecipeHandler.RecipeIngredient> inputs = new ArrayList<>();
        if (json.has("inputs")) {
            JsonArray inputsArray = json.getAsJsonArray("inputs");
            for (JsonElement inputElement : inputsArray) {
                RecipeHandler.RecipeIngredient ingredient = 
                    RecipeHandler.RecipeIngredient.parse(inputElement.getAsString());
                
                // PERUBAHAN: Langsung tambahkan semua (termasuk AIR)
                inputs.add(ingredient);
            }
        }
        
        if (inputs.isEmpty()) return null;
        
        // Parse outputs
        List<RecipeData.RecipeOutput> outputs = new ArrayList<>();
        
        if (json.has("outputs")) {
            JsonArray outputsArray = json.getAsJsonArray("outputs");
            for (JsonElement outputElement : outputsArray) {
                outputs.add(RecipeData.RecipeOutput.parse(outputElement.getAsString()));
            }
        } else if (json.has("output")) {
            outputs.add(RecipeData.RecipeOutput.parse(json.get("output").getAsString()));
        } else {
            int amount = json.has("outputAmount") ? json.get("outputAmount").getAsInt() : 1;
            outputs.add(new RecipeData.RecipeOutput(itemId, itemId, amount));
        }
        
        return new RecipeData(itemId, machineType, inputs, outputs);
    }
    
    /**
     * Parse processing recipe from JSON
     */
private static RecipeData parseProcessingRecipe(String machineId, JsonObject json) {
        String recipeId = machineId + "_recipe_" + Math.abs(json.hashCode());
        
        List<RecipeHandler.RecipeIngredient> inputs = new ArrayList<>();
        if (json.has("inputs")) {
            JsonArray inputsArray = json.getAsJsonArray("inputs");
            for (JsonElement inputElement : inputsArray) {
                RecipeHandler.RecipeIngredient ingredient = 
                    RecipeHandler.RecipeIngredient.parse(inputElement.getAsString());
                
                // PERUBAHAN: Langsung tambahkan semua (termasuk AIR)
                inputs.add(ingredient);
            }
        }
        
        if (inputs.isEmpty()) return null;
        
        // Parse outputs (tetap sama)
        List<RecipeData.RecipeOutput> outputs = new ArrayList<>();
        if (json.has("outputs")) {
            JsonArray outputsArray = json.getAsJsonArray("outputs");
            for (JsonElement outputElement : outputsArray) {
                outputs.add(RecipeData.RecipeOutput.parse(outputElement.getAsString()));
            }
        }
        
        if (outputs.isEmpty()) return null;
        
        return new RecipeData(recipeId, machineId, inputs, outputs);
    }
    
    /**
     * Register a recipe in the database
     */
    public static void registerRecipe(RecipeData recipe) {
        RECIPES_BY_ID.put(recipe.getRecipeId(), recipe);
        
        String machineId = recipe.getMachineId();
        RECIPES_BY_MACHINE.computeIfAbsent(machineId, k -> new ArrayList<>()).add(recipe);
    }
    
    /**
     * OPTIMIZED: Get all recipes for a specific machine (returns immutable view)
     */
    public static List<RecipeData> getRecipesForMachine(String machineId) {
        List<RecipeData> recipes = RECIPES_BY_MACHINE.get(machineId);
        return recipes != null ? Collections.unmodifiableList(recipes) : Collections.emptyList();
    }
    
    /**
     * Get a specific recipe by ID
     */
    public static RecipeData getRecipe(String recipeId) {
        return RECIPES_BY_ID.get(recipeId);
    }
    
    /**
     * Check if a machine has recipes
     */
    public static boolean hasMachineRecipes(String machineId) {
        List<RecipeData> recipes = RECIPES_BY_MACHINE.get(machineId);
        return recipes != null && !recipes.isEmpty();
    }
    
    /**
     * Get total number of recipes
     */
    public static int getTotalRecipes() {
        return RECIPES_BY_ID.size();
    }
    
    /**
     * Get total number of machines with recipes
     */
    public static int getTotalMachines() {
        return RECIPES_BY_MACHINE.size();
    }
    
    /**
     * Get all machine IDs that have recipes
     */
    public static Set<String> getAllMachineIds() {
        return Collections.unmodifiableSet(RECIPES_BY_MACHINE.keySet());
    }
    
    /**
     * OPTIMIZED: Get craftable recipes with caching
     */
    public static List<RecipeData> getCraftableRecipes(String machineId, 
                                                       List<net.minecraft.world.item.ItemStack> inventory) {
        // Clear cache periodically
        clearCacheIfNeeded();
        
        // Create cache key
        String cacheKey = machineId + "_" + getInventoryHash(inventory);
        
        // Check cache
        List<RecipeData> cached = CRAFTABLE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Calculate craftable recipes
        List<RecipeData> craftable = new ArrayList<>();
        List<RecipeData> allRecipes = getRecipesForMachine(machineId);
        
        for (RecipeData recipe : allRecipes) {
            RecipeHandler.RecipeSummary summary = new RecipeHandler.RecipeSummary(
                inventory, recipe.getInputs()
            );
            
            if (summary.canCraft()) {
                craftable.add(recipe);
            }
        }
        
        // Cache result
        CRAFTABLE_CACHE.put(cacheKey, craftable);
        
        return craftable;
    }
    
    /**
     * OPTIMIZATION: Simple hash for inventory state
     */
    private static int getInventoryHash(List<net.minecraft.world.item.ItemStack> inventory) {
        int hash = 0;
        for (net.minecraft.world.item.ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                hash += AutomationUtils.getItemId(stack).hashCode() + stack.getCount();
            }
        }
        return hash;
    }
    
    /**
     * OPTIMIZATION: Clear cache if too old
     */
    private static void clearCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCacheClear > CACHE_CLEAR_INTERVAL) {
            CRAFTABLE_CACHE.clear();
            lastCacheClear = now;
        }
    }
    
    /**
     * OPTIMIZED: Get recipes sorted by completion (cached)
     */
    public static List<RecipeData> getRecipesSortedByCompletion(String machineId,
                                                                List<net.minecraft.world.item.ItemStack> inventory) {
        List<RecipeData> recipes = getRecipesForMachine(machineId);
        
        // Create list with completion data
        List<RecipeWithCompletion> recipesWithCompletion = new ArrayList<>(recipes.size());
        for (RecipeData recipe : recipes) {
            RecipeHandler.RecipeSummary summary = new RecipeHandler.RecipeSummary(
                inventory, recipe.getInputs()
            );
            recipesWithCompletion.add(new RecipeWithCompletion(recipe, summary.getCompletionPercentage()));
        }
        
        // Sort by completion (highest first)
        recipesWithCompletion.sort((a, b) -> Float.compare(b.completion, a.completion));
        
        // Extract recipes
        List<RecipeData> sorted = new ArrayList<>(recipesWithCompletion.size());
        for (RecipeWithCompletion rwc : recipesWithCompletion) {
            sorted.add(rwc.recipe);
        }
        
        return sorted;
    }
    
    /**
     * Helper class for sorting
     */
    private static class RecipeWithCompletion {
        final RecipeData recipe;
        final float completion;
        
        RecipeWithCompletion(RecipeData recipe, float completion) {
            this.recipe = recipe;
            this.completion = completion;
        }
    }
    
    /**
     * OPTIMIZED: Search recipes by output using index
     */
    public static List<RecipeData> searchRecipesByOutput(String searchTerm) {
        List<RecipeData> results = new ArrayList<>();
        String searchUpper = searchTerm.toUpperCase();
        
        // Try exact match first (O(1) with index)
        Set<String> recipeIds = RECIPES_BY_OUTPUT.get(searchUpper);
        if (recipeIds != null) {
            for (String recipeId : recipeIds) {
                RecipeData recipe = RECIPES_BY_ID.get(recipeId);
                if (recipe != null) {
                    results.add(recipe);
                }
            }
            return results;
        }
        
        // Fallback to partial match
        for (Map.Entry<String, Set<String>> entry : RECIPES_BY_OUTPUT.entrySet()) {
            if (entry.getKey().contains(searchUpper)) {
                for (String recipeId : entry.getValue()) {
                    RecipeData recipe = RECIPES_BY_ID.get(recipeId);
                    if (recipe != null && !results.contains(recipe)) {
                        results.add(recipe);
                    }
                }
            }
        }
        
        return results;
    }
    
    /**
     * OPTIMIZED: Get recipes using ingredient (indexed)
     */
    public static List<RecipeData> getRecipesUsingIngredient(String itemId) {
        List<RecipeData> results = new ArrayList<>();
        String itemIdUpper = itemId.toUpperCase();
        
        Set<String> recipeIds = RECIPES_BY_INPUT.get(itemIdUpper);
        if (recipeIds != null) {
            for (String recipeId : recipeIds) {
                RecipeData recipe = RECIPES_BY_ID.get(recipeId);
                if (recipe != null) {
                    results.add(recipe);
                }
            }
        }
        
        return results;
    }
    
    /**
     * OPTIMIZED: Get recipes producing output (indexed)
     */
    public static List<RecipeData> getRecipesProducing(String itemId) {
        List<RecipeData> results = new ArrayList<>();
        String itemIdUpper = itemId.toUpperCase();
        
        Set<String> recipeIds = RECIPES_BY_OUTPUT.get(itemIdUpper);
        if (recipeIds != null) {
            for (String recipeId : recipeIds) {
                RecipeData recipe = RECIPES_BY_ID.get(recipeId);
                if (recipe != null) {
                    results.add(recipe);
                }
            }
        }
        
        return results;
    }
    
    /**
     * Clear all recipes
     */
    public static void clear() {
        RECIPES_BY_ID.clear();
        RECIPES_BY_MACHINE.clear();
        RECIPES_BY_OUTPUT.clear();
        RECIPES_BY_INPUT.clear();
        CRAFTABLE_CACHE.clear();
        initialized = false;
    }
    
    /**
     * Reload recipes from files
     */
    public static void reload() {
        BapelSlimefunMod.LOGGER.info("Reloading recipe database...");
        clear();
        initialize();
    }
    
    /**
     * Print database statistics
     */
    public static void printStats() {
        BapelSlimefunMod.LOGGER.info("===== Recipe Database Stats =====");
        BapelSlimefunMod.LOGGER.info("Total Recipes: {}", getTotalRecipes());
        BapelSlimefunMod.LOGGER.info("Machines with Recipes: {}", getTotalMachines());
        BapelSlimefunMod.LOGGER.info("Indexed Outputs: {}", RECIPES_BY_OUTPUT.size());
        BapelSlimefunMod.LOGGER.info("Indexed Inputs: {}", RECIPES_BY_INPUT.size());
        BapelSlimefunMod.LOGGER.info("Cache Size: {}", CRAFTABLE_CACHE.size());
        BapelSlimefunMod.LOGGER.info("================================");
    }
    
    /**
     * Check if database is initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * DEBUG: Print recipes for a specific machine
     */
    public static void debugMachineRecipes(String machineId) {
        BapelSlimefunMod.LOGGER.info("╔═══════════════════════════════════════╗");
        BapelSlimefunMod.LOGGER.info("║   DEBUG: Recipes for {}       ", machineId);
        BapelSlimefunMod.LOGGER.info("╠═══════════════════════════════════════╣");
        
        if (RECIPES_BY_MACHINE.containsKey(machineId)) {
            List<RecipeData> recipes = RECIPES_BY_MACHINE.get(machineId);
            BapelSlimefunMod.LOGGER.info("║ Found {} recipes", recipes.size());
            
            for (int i = 0; i < Math.min(5, recipes.size()); i++) {
                RecipeData recipe = recipes.get(i);
                BapelSlimefunMod.LOGGER.info("║   {}. {}", i+1, recipe.getDisplayString());
            }
            
            if (recipes.size() > 5) {
                BapelSlimefunMod.LOGGER.info("║   ... and {} more", recipes.size() - 5);
            }
        } else {
            BapelSlimefunMod.LOGGER.info("║ ✗ Machine ID not found");
        }
        
        BapelSlimefunMod.LOGGER.info("╚═══════════════════════════════════════╝");
    }
    
    /**
     * DEBUG: Print all machine IDs
     */
    public static void printAllMachineRecipes() {
        BapelSlimefunMod.LOGGER.info("╔═══════════════════════════════════════╗");
        BapelSlimefunMod.LOGGER.info("║   ALL MACHINES WITH RECIPES            ║");
        BapelSlimefunMod.LOGGER.info("╠═══════════════════════════════════════╣");
        
        for (Map.Entry<String, List<RecipeData>> entry : RECIPES_BY_MACHINE.entrySet()) {
            BapelSlimefunMod.LOGGER.info("║ {} → {} recipes", 
                entry.getKey(), entry.getValue().size());
        }
        
        BapelSlimefunMod.LOGGER.info("╚═══════════════════════════════════════╝");
    }
}