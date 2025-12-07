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
import com.bapel_slimefun_mod.debug.PerformanceMonitor;

/**
 * âœ… FIXED: Keep AIR items in recipe parsing for multiblock automation
 */
public class RecipeDatabase {
    private static final Gson GSON = new Gson();
    
    private static final Map<String, List<RecipeData>> RECIPES_BY_MACHINE = new ConcurrentHashMap<>();
    private static final Map<String, RecipeData> RECIPES_BY_ID = new ConcurrentHashMap<>();
    
    private static final Map<String, Set<String>> RECIPES_BY_OUTPUT = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> RECIPES_BY_INPUT = new ConcurrentHashMap<>();
    
    private static final Map<String, List<RecipeData>> CRAFTABLE_CACHE = new ConcurrentHashMap<>();
    private static long lastCacheClear = 0;
    private static final long CACHE_CLEAR_INTERVAL = 5000;
    
    private static boolean initialized = false;
    
    public static void initialize() {
        if (initialized) return;
        
        try {
            long startTime = System.currentTimeMillis();
            
            int externalCount = loadExternalRecipes();
            int processingCount = loadProcessingRecipes();
            
            buildIndexes();
            
            long duration = System.currentTimeMillis() - startTime;
            initialized = true;
            
            BapelSlimefunMod.LOGGER.info("[RecipeDB] Loaded {} external + {} processing recipes in {}ms", 
                externalCount, processingCount, duration);
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to initialize Recipe Database", e);
        }
    }
    
    private static void buildIndexes() {
        RECIPES_BY_OUTPUT.clear();
        RECIPES_BY_INPUT.clear();
        
        for (RecipeData recipe : RECIPES_BY_ID.values()) {
            for (RecipeData.RecipeOutput output : recipe.getOutputs()) {
                RECIPES_BY_OUTPUT
                    .computeIfAbsent(output.getItemId().toUpperCase(), k -> new HashSet<>())
                    .add(recipe.getRecipeId());
            }
            
            for (RecipeHandler.RecipeIngredient input : recipe.getInputs()) {
                RECIPES_BY_INPUT
                    .computeIfAbsent(input.getItemId().toUpperCase(), k -> new HashSet<>())
                    .add(recipe.getRecipeId());
            }
        }
    }
    
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
                    // Skip invalid recipe
                }
            }
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to load external recipes", e);
        }
        
        return loaded;
    }
    
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
                        // Skip invalid recipe
                    }
                }
            }
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to load processing recipes", e);
        }
        
        return loaded;
    }
    
    /**
     * âœ… FIXED: Keep ALL inputs including AIR for multiblock recipes
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
                
                // âœ… CRITICAL FIX: Always add ingredient (including AIR)
                // This preserves the 3x3 grid structure for multiblock automation
                inputs.add(ingredient);
            }
        }
        
        // âœ… REMOVED: Don't check if inputs are empty
        // Even if all AIR, we need the structure
        
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
     * âœ… FIXED: Keep ALL inputs including AIR for multiblock recipes
     */
    private static RecipeData parseProcessingRecipe(String machineId, JsonObject json) {
        String recipeId = machineId + "_recipe_" + Math.abs(json.hashCode());
        
        List<RecipeHandler.RecipeIngredient> inputs = new ArrayList<>();
        if (json.has("inputs")) {
            JsonArray inputsArray = json.getAsJsonArray("inputs");
            for (JsonElement inputElement : inputsArray) {
                RecipeHandler.RecipeIngredient ingredient = 
                    RecipeHandler.RecipeIngredient.parse(inputElement.getAsString());
                
                // âœ… CRITICAL FIX: Always add ingredient (including AIR)
                inputs.add(ingredient);
            }
        }
        
        // âœ… REMOVED: Don't check if inputs are empty
        
        // Parse outputs
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
    
    public static void registerRecipe(RecipeData recipe) {
        RECIPES_BY_ID.put(recipe.getRecipeId(), recipe);
        
        String machineId = recipe.getMachineId();
        RECIPES_BY_MACHINE.computeIfAbsent(machineId, k -> new ArrayList<>()).add(recipe);
    }
    
    public static List<RecipeData> getRecipesForMachine(String machineId) {
        List<RecipeData> recipes = RECIPES_BY_MACHINE.get(machineId);
        return recipes != null ? Collections.unmodifiableList(recipes) : Collections.emptyList();
    }
    
    public static RecipeData getRecipe(String recipeId) {
        return RECIPES_BY_ID.get(recipeId);
    }
    
    public static boolean hasMachineRecipes(String machineId) {
        List<RecipeData> recipes = RECIPES_BY_MACHINE.get(machineId);
        return recipes != null && !recipes.isEmpty();
    }
    
    public static int getTotalRecipes() {
        return RECIPES_BY_ID.size();
    }
    
    public static int getTotalMachines() {
        return RECIPES_BY_MACHINE.size();
    }
    
    public static Set<String> getAllMachineIds() {
        return Collections.unmodifiableSet(RECIPES_BY_MACHINE.keySet());
    }
    
    public static List<RecipeData> getCraftableRecipes(String machineId, 
                                                       List<net.minecraft.world.item.ItemStack> inventory) {
        clearCacheIfNeeded();
        
        String cacheKey = machineId + "_" + getInventoryHash(inventory);
        
        List<RecipeData> cached = CRAFTABLE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
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
        
        CRAFTABLE_CACHE.put(cacheKey, craftable);
        
        return craftable;
    }
    
    private static int getInventoryHash(List<net.minecraft.world.item.ItemStack> inventory) {
        int hash = 0;
        for (net.minecraft.world.item.ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                hash += AutomationUtils.getItemId(stack).hashCode() + stack.getCount();
            }
        }
        return hash;
    }
    
    private static void clearCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCacheClear > CACHE_CLEAR_INTERVAL) {
            CRAFTABLE_CACHE.clear();
            lastCacheClear = now;
        }
    }
    
    public static List<RecipeData> getRecipesSortedByCompletion(String machineId,
                                                                List<net.minecraft.world.item.ItemStack> inventory) {
        List<RecipeData> recipes = getRecipesForMachine(machineId);
        
        List<RecipeWithCompletion> recipesWithCompletion = new ArrayList<>(recipes.size());
        for (RecipeData recipe : recipes) {
            RecipeHandler.RecipeSummary summary = new RecipeHandler.RecipeSummary(
                inventory, recipe.getInputs()
            );
            recipesWithCompletion.add(new RecipeWithCompletion(recipe, summary.getCompletionPercentage()));
        }
        
        recipesWithCompletion.sort((a, b) -> Float.compare(b.completion, a.completion));
        
        List<RecipeData> sorted = new ArrayList<>(recipesWithCompletion.size());
        for (RecipeWithCompletion rwc : recipesWithCompletion) {
            sorted.add(rwc.recipe);
        }
        
        return sorted;
    }
    
    private static class RecipeWithCompletion {
        final RecipeData recipe;
        final float completion;
        
        RecipeWithCompletion(RecipeData recipe, float completion) {
            this.recipe = recipe;
            this.completion = completion;
        }
    }
    
    public static List<RecipeData> searchRecipesByOutput(String searchTerm) {
        List<RecipeData> results = new ArrayList<>();
        String searchUpper = searchTerm.toUpperCase();
        
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
    
    public static void clear() {
        RECIPES_BY_ID.clear();
        RECIPES_BY_MACHINE.clear();
        RECIPES_BY_OUTPUT.clear();
        RECIPES_BY_INPUT.clear();
        CRAFTABLE_CACHE.clear();
        initialized = false;
    }
    
    public static void reload() {
        clear();
        initialize();
    }
    
    public static boolean isInitialized() {
        return initialized;
    }
}