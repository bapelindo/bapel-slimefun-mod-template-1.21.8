package com.bapel_slimefun_mod.automation.recipe;

import com.bapel_slimefun_mod.automation.util.ItemKeyUtil;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**  
 * Client-side cache untuk semua resep Slimefun yang diketahui.  
 */  
public final class SlimefunRecipeRegistry {  

    private static final Logger LOGGER = LoggerFactory.getLogger("RecipeRegistry");  

    private static final SlimefunRecipeRegistry INSTANCE = new SlimefunRecipeRegistry();  
    public static SlimefunRecipeRegistry get() { return INSTANCE; }  

    private final Map<String, List<RecipeEntry>> recipesByOutput = new ConcurrentHashMap<>();  

    private final Map<String, Set<String>> recipesByMachine = new ConcurrentHashMap<>();  

    private SlimefunRecipeRegistry() {}  

    // ─────────────────────────────────────────────────────────────  
    // Registration  
    // ─────────────────────────────────────────────────────────────  

    public void addRecipe(RecipeEntry entry) {  
        recipesByOutput  
            .computeIfAbsent(entry.outputKey(), k -> new ArrayList<>())  
            .add(entry);  

        recipesByMachine  
            .computeIfAbsent(entry.requiredMachine(), k -> new HashSet<>())  
            .add(entry.outputKey());  

        LOGGER.debug("Registered recipe: {} via {}", entry.outputKey(), entry.requiredMachine());  
    }  

    public void clear() {  
        recipesByOutput.clear();  
        recipesByMachine.clear();  
        LOGGER.info("Recipe registry cleared");  
    }  

    // ─────────────────────────────────────────────────────────────  
    // Query  
    // ─────────────────────────────────────────────────────────────  

    public List<RecipeEntry> getRecipesFor(ItemStack output) {  
        String key = ItemKeyUtil.getKey(output);  
        return recipesByOutput.getOrDefault(key, Collections.emptyList());  
    }  

    public List<RecipeEntry> getRecipesFor(String outputKey) {  
        return recipesByOutput.getOrDefault(outputKey, Collections.emptyList());  
    }  

    public boolean hasRecipe(ItemStack output) {  
        return !getRecipesFor(output).isEmpty();  
    }  

    public boolean hasRecipe(String outputKey) {  
        return !getRecipesFor(outputKey).isEmpty();  
    }  

    public Optional<RecipeEntry> getRecipeForMachine(String outputKey, String machineId) {  
        return getRecipesFor(outputKey).stream()  
            .filter(r -> r.requiredMachine().equals(machineId))  
            .findFirst();  
    }  

    public Optional<RecipeEntry> getBestRecipe(String outputKey) {  
        List<RecipeEntry> recipes = getRecipesFor(outputKey);  
        if (recipes.isEmpty()) return Optional.empty();  
        return Optional.of(recipes.get(0));  
    }  

    public Set<String> getKnownMachines() {  
        return Collections.unmodifiableSet(recipesByMachine.keySet());  
    }  

    public int totalRecipes() {  
        return recipesByOutput.values().stream().mapToInt(List::size).sum();  
    }  

    public Set<String> getKnownOutputs() {
        return Collections.unmodifiableSet(recipesByOutput.keySet());
    }

    public void removeRecipe(String outputKey) {  
        List<RecipeEntry> removed = recipesByOutput.remove(outputKey);  
        if (removed != null) {  
            recipesByMachine.values().forEach(set -> set.remove(outputKey));  
            LOGGER.debug("Removed recipe: {}", outputKey);  
        }  
    }  
}
