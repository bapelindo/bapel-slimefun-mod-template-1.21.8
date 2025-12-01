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
 * Loads recipes from JSON and provides query methods
 */
public class RecipeDatabase {
    private static final Gson GSON = new Gson();
    private static final Map<String, List<RecipeData>> RECIPES_BY_MACHINE = new HashMap<>();
    private static final Map<String, RecipeData> RECIPES_BY_ID = new HashMap<>();
    private static boolean initialized = false;
    
    /**
     * Initialize the recipe database
     */
    public static void initialize() {
        if (initialized) return;
        
        try {
            loadRecipes();
            initialized = true;
            BapelSlimefunMod.LOGGER.info("Recipe Database initialized with {} recipes for {} machines", 
                RECIPES_BY_ID.size(), RECIPES_BY_MACHINE.size());
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to initialize Recipe Database", e);
        }
    }
    
    /**
     * Load recipes from slimefun_recipes.json
     */
    private static void loadRecipes() {
        try {
            InputStream stream = RecipeDatabase.class
                .getResourceAsStream("/assets/bapel-slimefun-mod/slimefun_recipes.json");
            
            if (stream == null) {
                BapelSlimefunMod.LOGGER.warn("Could not find slimefun_recipes.json");
                return;
            }
            
            JsonArray recipesArray = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                JsonArray.class
            );
            
            int loaded = 0;
            for (JsonElement element : recipesArray) {
                JsonObject recipeObj = element.getAsJsonObject();
                
                try {
                    RecipeData recipe = parseRecipeFromJson(recipeObj);
                    if (recipe != null) {
                        registerRecipe(recipe);
                        loaded++;
                    }
                } catch (Exception e) {
                    BapelSlimefunMod.LOGGER.error("Failed to parse recipe: {}", recipeObj, e);
                }
            }
            
            BapelSlimefunMod.LOGGER.info("Loaded {} recipes from JSON", loaded);
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to load recipes", e);
        }
    }
    
    /**
     * Parse a recipe from JSON object
     */
    private static RecipeData parseRecipeFromJson(JsonObject json) {
        String id = json.get("id").getAsString();
        String machineType = json.has("machineType") ? 
            json.get("machineType").getAsString() : "UNKNOWN";
        
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
        
        // Validate recipe has both inputs and outputs
        if (inputs.isEmpty() || outputs.isEmpty()) {
            BapelSlimefunMod.LOGGER.warn("Recipe {} has no inputs or outputs, skipping", id);
            return null;
        }
        
        return new RecipeData(id, machineType, inputs, outputs);
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
        for (int i = 0; i < Math.min(5, entries.size()); i++) {
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
