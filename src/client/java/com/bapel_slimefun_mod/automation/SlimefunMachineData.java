package com.bapel_slimefun_mod.automation;

import java.util.List;

/**
 * Data class representing a Slimefun machine configuration
 */
public class SlimefunMachineData {
    private final String id;
    private final String name;
    private final String inventoryTitle;
    private final int[] inputSlots;
    private final int[] outputSlots;
    private final List<String> recipe;
    private final int energyCapacity;
    private final int energyConsumption;
    
    public SlimefunMachineData(String id, String name, String inventoryTitle, 
                               int[] inputSlots, int[] outputSlots, 
                               List<String> recipe, int energyCapacity, 
                               int energyConsumption) {
        this.id = id;
        this.name = name;
        this.inventoryTitle = inventoryTitle;
        this.inputSlots = inputSlots;
        this.outputSlots = outputSlots;
        this.recipe = recipe;
        this.energyCapacity = energyCapacity;
        this.energyConsumption = energyConsumption;
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public String getInventoryTitle() { return inventoryTitle; }
    public int[] getInputSlots() { return inputSlots; }
    public int[] getOutputSlots() { return outputSlots; }
    public List<String> getRecipe() { return recipe; }
    public int getEnergyCapacity() { return energyCapacity; }
    public int getEnergyConsumption() { return energyConsumption; }
    
    public boolean hasInputSlots() {
        return inputSlots != null && inputSlots.length > 0;
    }
    
    public boolean hasOutputSlots() {
        return outputSlots != null && outputSlots.length > 0;
    }
}