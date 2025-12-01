package com.bapel_slimefun_mod.automation;

import java.util.*;

/**
 * Data class representing a craftable recipe with inputs and outputs
 */
public class RecipeData {
    private final String recipeId;
    private final String machineId;
    private final List<RecipeHandler.RecipeIngredient> inputs;
    private final List<RecipeOutput> outputs;
    private final Map<String, Integer> groupedInputs;
    
    /**
     * Represents a recipe output item
     */
    public static class RecipeOutput {
        private final String itemId;
        private final String displayName;
        private final int amount;
        
        public RecipeOutput(String itemId, String displayName, int amount) {
            this.itemId = itemId;
            this.displayName = displayName;
            this.amount = amount;
        }
        
        public String getItemId() { return itemId; }
        public String getDisplayName() { return displayName; }
        public int getAmount() { return amount; }
        
        public static RecipeOutput parse(String outputString) {
            if (outputString == null || outputString.isEmpty()) {
                return new RecipeOutput("UNKNOWN", "Unknown Item", 1);
            }
            
            String[] parts = outputString.split(":");
            String itemId = parts[0];
            int amount = parts.length > 1 ? parseAmount(parts[1]) : 1;
            String displayName = formatDisplayName(itemId);
            
            return new RecipeOutput(itemId, displayName, amount);
        }
        
        private static int parseAmount(String amountStr) {
            try {
                return Integer.parseInt(amountStr);
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        
        private static String formatDisplayName(String itemId) {
            // Convert ITEM_ID to Item Id format
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
    }
    
    public RecipeData(String recipeId, String machineId, 
                     List<RecipeHandler.RecipeIngredient> inputs,
                     List<RecipeOutput> outputs) {
        this.recipeId = recipeId;
        this.machineId = machineId;
        this.inputs = inputs;
        this.outputs = outputs;
        this.groupedInputs = RecipeHandler.groupRecipeIngredients(inputs);
    }
    
    public String getRecipeId() { return recipeId; }
    public String getMachineId() { return machineId; }
    public List<RecipeHandler.RecipeIngredient> getInputs() { return inputs; }
    public List<RecipeOutput> getOutputs() { return outputs; }
    public Map<String, Integer> getGroupedInputs() { return groupedInputs; }
    
    /**
     * Get primary output (first output)
     */
    public RecipeOutput getPrimaryOutput() {
        return outputs.isEmpty() ? null : outputs.get(0);
    }
    
    /**
     * Check if recipe has multiple outputs
     */
    public boolean hasMultipleOutputs() {
        return outputs.size() > 1;
    }
    
    /**
     * Get total number of input items required
     */
    public int getTotalInputCount() {
        int total = 0;
        for (RecipeHandler.RecipeIngredient input : inputs) {
            total += input.getAmount();
        }
        return total;
    }
    
    /**
     * Get total number of output items produced
     */
    public int getTotalOutputCount() {
        int total = 0;
        for (RecipeOutput output : outputs) {
            total += output.getAmount();
        }
        return total;
    }
    
    /**
     * Get display string for the recipe
     */
    public String getDisplayString() {
        RecipeOutput primary = getPrimaryOutput();
        if (primary == null) {
            return "Unknown Recipe";
        }
        
        StringBuilder display = new StringBuilder();
        display.append(primary.getDisplayName());
        
        if (primary.getAmount() > 1) {
            display.append(" x").append(primary.getAmount());
        }
        
        if (hasMultipleOutputs()) {
            display.append(" + ").append(outputs.size() - 1).append(" more");
        }
        
        return display.toString();
    }
    
    /**
     * Get formatted inputs string
     */
    public String getInputsString() {
        StringBuilder sb = new StringBuilder();
        
        for (Map.Entry<String, Integer> entry : groupedInputs.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append(" x").append(entry.getValue());
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return String.format("Recipe[%s -> %s]", getInputsString(), getDisplayString());
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecipeData)) return false;
        RecipeData that = (RecipeData) o;
        return recipeId.equals(that.recipeId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(recipeId);
    }
}