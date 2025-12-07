package com.bapel_slimefun_mod.automation;

import net.minecraft.world.item.ItemStack;
import java.util.*;
import com.bapel_slimefun_mod.debug.PerformanceMonitor;

/**
 * Handles complex recipe matching including multi-item recipes
 * FIXED: Preserves AIR items to maintain correct 3x3 grid pattern for automation
 */
public class RecipeHandler {
    
    /**
     * Represents a recipe ingredient with quantity
     */
    public static class RecipeIngredient {
        private final String itemId;
        private final int amount;
        
        public RecipeIngredient(String itemId, int amount) {
            this.itemId = itemId;
            this.amount = amount;
        }
        
        public String getItemId() { return itemId; }
        public int getAmount() { return amount; }
        
        /**
         * Parse from format "ITEM_ID:AMOUNT"
         */
        public static RecipeIngredient parse(String recipeString) {
            if (recipeString == null || recipeString.isEmpty()) {
                return new RecipeIngredient("AIR", 0);
            }
            
            String[] parts = recipeString.split(":");
            if (parts.length < 2) {
                return new RecipeIngredient(parts[0], 1);
            }
            
            try {
                int amount = Integer.parseInt(parts[1]);
                return new RecipeIngredient(parts[0], amount);
            } catch (NumberFormatException e) {
                return new RecipeIngredient(parts[0], 1);
            }
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RecipeIngredient)) return false;
            RecipeIngredient that = (RecipeIngredient) o;
            return amount == that.amount && itemId.equals(that.itemId);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(itemId, amount);
        }
    }
    
    /**
     * Parse recipe strings into structured ingredients
     * FIX: Now KEEPS "AIR" items to ensure list size is always 9 for 3x3 recipes
     */
    public static List<RecipeIngredient> parseRecipe(List<String> recipeStrings) {
        List<RecipeIngredient> ingredients = new ArrayList<>();
        
        for (String recipeString : recipeStrings) {
            RecipeIngredient ingredient = RecipeIngredient.parse(recipeString);
            // PERUBAHAN PENTING: Selalu tambahkan ingredient, termasuk AIR
            ingredients.add(ingredient);
        }
        
        return ingredients;
    }
    
    /**
     * Group recipe ingredients by item ID and sum their amounts
     * FIX: Filters out "AIR" here so it doesn't count as a required item to fetch
     */
public static Map<String, Integer> groupRecipeIngredients(List<RecipeIngredient> ingredients) {
        Map<String, Integer> grouped = new HashMap<>();
        
        for (RecipeIngredient ingredient : ingredients) {
            String itemId = ingredient.getItemId().toUpperCase();
            
            // PERUBAHAN: Abaikan AIR atau item dengan jumlah 0
            if (itemId.equals("AIR") || ingredient.getAmount() <= 0) continue;
            
            grouped.merge(itemId, ingredient.getAmount(), Integer::sum);
        }
        
        return grouped;
    }
    
    /**
     * Check if an ItemStack matches a recipe ingredient
     */
    public static boolean matchesIngredient(ItemStack stack, String itemId) {
        if (stack.isEmpty()) return false;
        if (itemId.equalsIgnoreCase("AIR")) return false;
        
        String stackItemId = AutomationUtils.getItemId(stack);
        return stackItemId.equalsIgnoreCase(itemId);
    }
    
    /**
     * Count how many of a specific item exist in inventory
     */
    public static int countItemInInventory(List<ItemStack> inventory, String itemId) {
        int total = 0;
        
        for (ItemStack stack : inventory) {
            if (matchesIngredient(stack, itemId)) {
                total += stack.getCount();
            }
        }
        
        return total;
    }
    
    /**
     * Check if inventory has enough items for recipe
     */
    public static boolean hasEnoughIngredients(List<ItemStack> inventory, Map<String, Integer> requiredItems) {
        for (Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
            String itemId = entry.getKey();
            int required = entry.getValue();
            int available = countItemInInventory(inventory, itemId);
            
            if (available < required) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Find which items from recipe are available in inventory
     */
    public static Map<String, Integer> getAvailableIngredients(List<ItemStack> inventory, Map<String, Integer> requiredItems) {
        Map<String, Integer> available = new HashMap<>();
        
        for (String itemId : requiredItems.keySet()) {
            int count = countItemInInventory(inventory, itemId);
            if (count > 0) {
                available.put(itemId, count);
            }
        }
        
        return available;
    }
    
    /**
     * Get missing ingredients (required but not available)
     */
    public static Map<String, Integer> getMissingIngredients(List<ItemStack> inventory, Map<String, Integer> requiredItems) {
        Map<String, Integer> missing = new HashMap<>();
        
        for (Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
            String itemId = entry.getKey();
            int required = entry.getValue();
            int available = countItemInInventory(inventory, itemId);
            
            if (available < required) {
                missing.put(itemId, required - available);
            }
        }
        
        return missing;
    }
    
    /**
     * Calculate how many times a recipe can be crafted with available items
     */
    public static int calculateMaxCrafts(List<ItemStack> inventory, Map<String, Integer> requiredItems) {
        if (requiredItems.isEmpty()) return 0;
        
        int maxCrafts = Integer.MAX_VALUE;
        
        for (Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
            String itemId = entry.getKey();
            int required = entry.getValue();
            int available = countItemInInventory(inventory, itemId);
            
            int possibleCrafts = available / required;
            maxCrafts = Math.min(maxCrafts, possibleCrafts);
        }
        
        return maxCrafts == Integer.MAX_VALUE ? 0 : maxCrafts;
    }
    
    /**
     * Get recipe completion percentage
     */
    public static float getRecipeCompletionPercentage(List<ItemStack> inventory, Map<String, Integer> requiredItems) {
        if (requiredItems.isEmpty()) return 0f;
        
        int totalRequired = 0;
        int totalAvailable = 0;
        
        for (Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
            String itemId = entry.getKey();
            int required = entry.getValue();
            int available = Math.min(countItemInInventory(inventory, itemId), required);
            
            totalRequired += required;
            totalAvailable += available;
        }
        
        return totalRequired > 0 ? (float) totalAvailable / totalRequired : 0f;
    }
    
    /**
     * Format ingredient for display
     */
    public static String formatIngredient(String itemId, int amount) {
        return String.format("%s x%d", itemId, amount);
    }
    
    /**
     * Format recipe requirements for display
     */
    public static List<String> formatRecipeRequirements(Map<String, Integer> requiredItems) {
        List<String> formatted = new ArrayList<>();
        
        for (Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
            formatted.add(formatIngredient(entry.getKey(), entry.getValue()));
        }
        
        return formatted;
    }
    
    /**
     * Check if recipe is a multi-item recipe (has duplicate items)
     */
    public static boolean isMultiItemRecipe(List<RecipeIngredient> ingredients) {
        Set<String> uniqueItems = new HashSet<>();
        
        for (RecipeIngredient ingredient : ingredients) {
            if (!uniqueItems.add(ingredient.getItemId().toUpperCase())) {
                return true; // Found duplicate
            }
        }
        
        return false;
    }
    
    /**
     * Get unique item types in recipe
     */
    public static Set<String> getUniqueItems(List<RecipeIngredient> ingredients) {
        Set<String> unique = new HashSet<>();
        
        for (RecipeIngredient ingredient : ingredients) {
            if (!ingredient.getItemId().equalsIgnoreCase("AIR")) {
                unique.add(ingredient.getItemId().toUpperCase());
            }
        }
        
        return unique;
    }
    
    /**
     * Validate recipe format
     */
    public static boolean isValidRecipe(List<String> recipeStrings) {
        if (recipeStrings == null || recipeStrings.isEmpty()) {
            return false;
        }
        
        // Check if at least one non-AIR ingredient
        for (String recipe : recipeStrings) {
            RecipeIngredient ingredient = RecipeIngredient.parse(recipe);
            if (!ingredient.getItemId().equalsIgnoreCase("AIR") && ingredient.getAmount() > 0) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Create a summary of recipe requirements
     */
    public static class RecipeSummary {
        private final Map<String, Integer> requiredItems;
        private final Map<String, Integer> availableItems;
        private final Map<String, Integer> missingItems;
        private final boolean canCraft;
        private final int maxCrafts;
        private final float completionPercentage;
        
        public RecipeSummary(List<ItemStack> inventory, List<RecipeIngredient> recipe) {
            this.requiredItems = groupRecipeIngredients(recipe);
            this.availableItems = getAvailableIngredients(inventory, requiredItems);
            this.missingItems = getMissingIngredients(inventory, requiredItems);
            this.canCraft = missingItems.isEmpty();
            this.maxCrafts = calculateMaxCrafts(inventory, requiredItems);
            this.completionPercentage = getRecipeCompletionPercentage(inventory, requiredItems);
        }
        
        public Map<String, Integer> getRequiredItems() { return requiredItems; }
        public Map<String, Integer> getAvailableItems() { return availableItems; }
        public Map<String, Integer> getMissingItems() { return missingItems; }
        public boolean canCraft() { return canCraft; }
        public int getMaxCrafts() { return maxCrafts; }
        public float getCompletionPercentage() { return completionPercentage; }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Recipe Summary:\n");
            sb.append(String.format("  Can Craft: %s\n", canCraft ? "YES" : "NO"));
            sb.append(String.format("  Max Crafts: %d\n", maxCrafts));
            sb.append(String.format("  Completion: %.1f%%\n", completionPercentage * 100));
            
            if (!missingItems.isEmpty()) {
                sb.append("  Missing:\n");
                for (Map.Entry<String, Integer> entry : missingItems.entrySet()) {
                    sb.append(String.format("    - %s x%d\n", entry.getKey(), entry.getValue()));
                }
            }
            
            return sb.toString();
        }
    }
}