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

/**
 * Database for managing multiple recipes per machine
 * UPDATED: Now supports both slimefun_recipes.json and slimefun_machines.json
 * Loads recipes automatically from Slimefun official JSON files
 */
public class RecipeDatabase {
    private static final Gson GSON = new Gson();
    private static final Map<String, List<RecipeData>> RECIPES_BY_MACHINE = new HashMap<>();
    private static final Map<String, RecipeData> RECIPES_BY_ID = new HashMap<>();
    private static boolean initialized = false;
    
    /**
     * Initialize the recipe database
     * Loads from both slimefun_recipes.json and slimefun_machines.json
     */
    public static void initialize() {
        if (initialized) return;
        
        try {
            long startTime = System.currentTimeMillis();
            BapelSlimefunMod.LOGGER.info("Initializing Recipe Database...");
            
            // Load external recipes from slimefun_recipes.json
            int externalCount = loadExternalRecipes();
            
            // Load processing recipes from slimefun_machines.json
            int processingCount = loadProcessingRecipes();
            
            long duration = System.currentTimeMillis() - startTime;
            initialized = true;
            
            BapelSlimefunMod.LOGGER.info("Recipe Database initialized with {} recipes for {} machines in {}ms", 
                RECIPES_BY_ID.size(), RECIPES_BY_MACHINE.size(), duration);
            BapelSlimefunMod.LOGGER.info("  - External recipes: {}", externalCount);
            BapelSlimefunMod.LOGGER.info("  - Processing recipes: {}", processingCount);
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to initialize Recipe Database", e);
        }
    }
    
    /**
     * Load external recipes from slimefun_recipes.json
     * These are recipes like Enhanced Crafting Table, Magic Workbench, etc.
     */
    private static int loadExternalRecipes() {
        int loaded = 0;
        
        try {
            InputStream stream = RecipeDatabase.class
                .getResourceAsStream("/assets/bapel-slimefun-mod/slimefun_recipes.json");
            
            if (stream == null) {
                BapelSlimefunMod.LOGGER.warn("Could not find slimefun_recipes.json - skipping external recipes");
                return 0;
            }
            
            JsonArray recipesArray = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                JsonArray.class
            );
            
            for (JsonElement element : recipesArray) {
                JsonObject recipeObj = element.getAsJsonObject();
                
                try {
                    RecipeData recipe = parseExternalRecipe(recipeObj);
                    if (recipe != null) {
                        registerRecipe(recipe);
                        loaded++;
                    }
                } catch (Exception e) {
                    BapelSlimefunMod.LOGGER.debug("Failed to parse external recipe: {}", recipeObj, e);
                }
            }
            
            BapelSlimefunMod.LOGGER.info("Loaded {} external recipes from slimefun_recipes.json", loaded);
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to load external recipes", e);
        }
        
        return loaded;
    }
    
    /**
     * Load processing recipes from slimefun_machines.json
     * These are recipes for electric machines like ELECTRIC_PRESS, CARBON_PRESS, etc.
     */
    private static int loadProcessingRecipes() {
        int loaded = 0;
        
        try {
            InputStream stream = RecipeDatabase.class
                .getResourceAsStream("/assets/bapel-slimefun-mod/slimefun_machines.json");
            
            if (stream == null) {
                BapelSlimefunMod.LOGGER.warn("Could not find slimefun_machines.json - skipping processing recipes");
                return 0;
            }
            
            JsonArray machinesArray = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                JsonArray.class
            );
            
            int machineCount = 0;
            
            for (JsonElement element : machinesArray) {
                JsonObject machineObj = element.getAsJsonObject();
                
                if (!machineObj.has("id") || !machineObj.has("processingRecipes")) {
                    continue;
                }
                
                String machineId = machineObj.get("id").getAsString();
                JsonArray recipes = machineObj.getAsJsonArray("processingRecipes");
                
                if (recipes.size() == 0) {
                    continue;
                }
                
                machineCount++;
                
                for (JsonElement recipeElement : recipes) {
                    JsonObject recipeObj = recipeElement.getAsJsonObject();
                    
                    try {
                        RecipeData recipe = parseProcessingRecipe(machineId, recipeObj);
                        if (recipe != null) {
                            registerRecipe(recipe);
                            loaded++;
                        }
                    } catch (Exception e) {
                        BapelSlimefunMod.LOGGER.debug("Failed to parse processing recipe for {}: {}", 
                            machineId, recipeObj, e);
                    }
                }
            }
            
            BapelSlimefunMod.LOGGER.info("Loaded {} processing recipes from {} machines in slimefun_machines.json", 
                loaded, machineCount);
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to load processing recipes", e);
        }
        
        return loaded;
    }
    
    /**
     * Parse external recipe from slimefun_recipes.json format
     * Format: { "itemId": "...", "recipeType": "...", "inputs": [...], "outputAmount": 1 }
     */
    private static RecipeData parseExternalRecipe(JsonObject json) {
        String itemId = json.get("itemId").getAsString();
        String machineType = "UNKNOWN";

        // Handle 'recipeType' and normalize ID (remove 'slimefun:' and uppercase)
        if (json.has("recipeType")) {
            String rawType = json.get("recipeType").getAsString();
            machineType = rawType.replace("slimefun:", "").toUpperCase();
        } else if (json.has("machineType")) {
            machineType = json.get("machineType").getAsString();
        }
        
        // Parse inputs
        List<RecipeHandler.RecipeIngredient> inputs = new ArrayList<>();
        if (json.has("inputs")) {
            JsonArray inputsArray = json.getAsJsonArray("inputs");
            for (JsonElement inputElement : inputsArray) {
                String inputString = inputElement.getAsString();
                RecipeHandler.RecipeIngredient ingredient = 
                    RecipeHandler.RecipeIngredient.parse(inputString);
                
                if (ingredient.getAmount() > 0 && 
                    !ingredient.getItemId().equalsIgnoreCase("AIR")) {
                    inputs.add(ingredient);
                }
            }
        }
        
        // Parse outputs
        List<RecipeData.RecipeOutput> outputs = new ArrayList<>();
        
        if (json.has("outputs")) {
            JsonArray outputsArray = json.getAsJsonArray("outputs");
            for (JsonElement outputElement : outputsArray) {
                String outputString = outputElement.getAsString();
                RecipeData.RecipeOutput output = 
                    RecipeData.RecipeOutput.parse(outputString);
                outputs.add(output);
            }
        }
        // Fallback: single output field
        else if (json.has("output")) {
            String outputString = json.get("output").getAsString();
            RecipeData.RecipeOutput output = 
                RecipeData.RecipeOutput.parse(outputString);
            outputs.add(output);
        }
        // If no output field, use itemId as output (standard Slimefun format)
        else {
            int amount = json.has("outputAmount") ? json.get("outputAmount").getAsInt() : 1;
            outputs.add(new RecipeData.RecipeOutput(itemId, itemId, amount));
        }
        
        // Validate recipe has inputs
        if (inputs.isEmpty()) {
            return null;
        }
        
        return new RecipeData(itemId, machineType, inputs, outputs);
    }
    
    /**
     * Parse processing recipe from slimefun_machines.json format
     * Format: { "inputs": [...], "outputs": [...], "ticks": 8, "seconds": 0.4 }
     */
    private static RecipeData parseProcessingRecipe(String machineId, JsonObject json) {
        // Create unique recipe ID from machine and hash
        String recipeId = machineId + "_recipe_" + Math.abs(json.hashCode());
        
        // Parse inputs
        List<RecipeHandler.RecipeIngredient> inputs = new ArrayList<>();
        if (json.has("inputs")) {
            JsonArray inputsArray = json.getAsJsonArray("inputs");
            for (JsonElement inputElement : inputsArray) {
                String inputString = inputElement.getAsString();
                RecipeHandler.RecipeIngredient ingredient = 
                    RecipeHandler.RecipeIngredient.parse(inputString);
                
                if (ingredient.getAmount() > 0 && 
                    !ingredient.getItemId().equalsIgnoreCase("AIR")) {
                    inputs.add(ingredient);
                }
            }
        }
        
        // Parse outputs
        List<RecipeData.RecipeOutput> outputs = new ArrayList<>();
        if (json.has("outputs")) {
            JsonArray outputsArray = json.getAsJsonArray("outputs");
            for (JsonElement outputElement : outputsArray) {
                String outputString = outputElement.getAsString();
                RecipeData.RecipeOutput output = 
                    RecipeData.RecipeOutput.parse(outputString);
                outputs.add(output);
            }
        }
        
        // Validate recipe has inputs and outputs
        if (inputs.isEmpty() || outputs.isEmpty()) {
            return null;
        }
        
        return new RecipeData(recipeId, machineId, inputs, outputs);
    }
    
    /**
     * Register a recipe in the database
     */
    public static void registerRecipe(RecipeData recipe) {
        // Store by recipe ID
        RECIPES_BY_ID.put(recipe.getRecipeId(), recipe);
        
        // Store by machine ID
        String machineId = recipe.getMachineId();
        RECIPES_BY_MACHINE.computeIfAbsent(machineId, k -> new ArrayList<>()).add(recipe);
    }
    
    /**
     * Get all recipes for a specific machine
     */
    public static List<RecipeData> getRecipesForMachine(String machineId) {
        return new ArrayList<>(RECIPES_BY_MACHINE.getOrDefault(machineId, new ArrayList<>()));
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
        return RECIPES_BY_MACHINE.containsKey(machineId) && 
               !RECIPES_BY_MACHINE.get(machineId).isEmpty();
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
        return new HashSet<>(RECIPES_BY_MACHINE.keySet());
    }
    
    /**
     * Get recipes that player can craft with current inventory
     */
    public static List<RecipeData> getCraftableRecipes(String machineId, 
                                                       List<net.minecraft.world.item.ItemStack> inventory) {
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
        
        return craftable;
    }
    
    /**
     * Get recipes sorted by completion percentage
     */
    public static List<RecipeData> getRecipesSortedByCompletion(String machineId,
                                                                List<net.minecraft.world.item.ItemStack> inventory) {
        List<RecipeData> recipes = getRecipesForMachine(machineId);
        
        // Create list with completion data
        List<RecipeWithCompletion> recipesWithCompletion = new ArrayList<>();
        for (RecipeData recipe : recipes) {
            RecipeHandler.RecipeSummary summary = new RecipeHandler.RecipeSummary(
                inventory, recipe.getInputs()
            );
            recipesWithCompletion.add(new RecipeWithCompletion(recipe, summary.getCompletionPercentage()));
        }
        
        // Sort by completion (highest first)
        recipesWithCompletion.sort((a, b) -> Float.compare(b.completion, a.completion));
        
        // Extract recipes
        List<RecipeData> sorted = new ArrayList<>();
        for (RecipeWithCompletion rwc : recipesWithCompletion) {
            sorted.add(rwc.recipe);
        }
        
        return sorted;
    }
    
    /**
     * Helper class for sorting recipes by completion
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
     * Search recipes by output name
     */
    public static List<RecipeData> searchRecipesByOutput(String searchTerm) {
        List<RecipeData> results = new ArrayList<>();
        String searchLower = searchTerm.toLowerCase();
        
        for (RecipeData recipe : RECIPES_BY_ID.values()) {
            RecipeData.RecipeOutput output = recipe.getPrimaryOutput();
            if (output != null) {
                String displayName = output.getDisplayName().toLowerCase();
                String itemId = output.getItemId().toLowerCase();
                
                if (displayName.contains(searchLower) || itemId.contains(searchLower)) {
                    results.add(recipe);
                }
            }
        }
        
        return results;
    }
    
    /**
     * Get recipes that use a specific ingredient
     */
    public static List<RecipeData> getRecipesUsingIngredient(String itemId) {
        List<RecipeData> results = new ArrayList<>();
        String itemIdUpper = itemId.toUpperCase();
        
        for (RecipeData recipe : RECIPES_BY_ID.values()) {
            for (RecipeHandler.RecipeIngredient ingredient : recipe.getInputs()) {
                if (ingredient.getItemId().equalsIgnoreCase(itemIdUpper)) {
                    results.add(recipe);
                    break;
                }
            }
        }
        
        return results;
    }
    
    /**
     * Get recipes that produce a specific output
     */
    public static List<RecipeData> getRecipesProducing(String itemId) {
        List<RecipeData> results = new ArrayList<>();
        String itemIdUpper = itemId.toUpperCase();
        
        for (RecipeData recipe : RECIPES_BY_ID.values()) {
            for (RecipeData.RecipeOutput output : recipe.getOutputs()) {
                if (output.getItemId().equalsIgnoreCase(itemIdUpper)) {
                    results.add(recipe);
                    break;
                }
            }
        }
        
        return results;
    }
    
    /**
     * Clear all recipes (for reloading)
     */
    public static void clear() {
        RECIPES_BY_ID.clear();
        RECIPES_BY_MACHINE.clear();
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
        
        // Show machines with most recipes
        List<Map.Entry<String, List<RecipeData>>> entries = 
            new ArrayList<>(RECIPES_BY_MACHINE.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));
        
        BapelSlimefunMod.LOGGER.info("Top Machines by Recipe Count:");
        for (int i = 0; i < Math.min(10, entries.size()); i++) {
            Map.Entry<String, List<RecipeData>> entry = entries.get(i);
            BapelSlimefunMod.LOGGER.info("  {}: {} recipes", 
                entry.getKey(), entry.getValue().size());
        }
        
        BapelSlimefunMod.LOGGER.info("================================");
    }
    
    /**
     * Check if database is initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
}