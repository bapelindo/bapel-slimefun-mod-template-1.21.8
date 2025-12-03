package com.bapel_slimefun_mod.automation;

import java.util.List;

/**
 * Data class representing a Slimefun machine configuration
 * Supports both electric machines and multiblock structures
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
    private final MachineType type;
    private final List<MultiblockStructure> structure;
    
    public enum MachineType {
        ELECTRIC,    // Normal machine with GUI slots
        MULTIBLOCK   // Multiblock structure (Enhanced Crafting Table, Ore Crusher, etc)
    }
    
    /**
     * Constructor for electric machines
     */
    public SlimefunMachineData(String id, String name, String inventoryTitle, 
                               int[] inputSlots, int[] outputSlots, 
                               List<String> recipe, int energyCapacity, 
                               int energyConsumption) {
        this(id, name, inventoryTitle, inputSlots, outputSlots, recipe, 
             energyCapacity, energyConsumption, MachineType.ELECTRIC, null);
    }
    
    /**
     * Constructor for multiblock machines
     */
    public SlimefunMachineData(String id, String name, String inventoryTitle,
                               List<MultiblockStructure> structure,
                               List<String> recipe) {
        this(id, name, inventoryTitle, new int[0], new int[0], recipe,
             0, 0, MachineType.MULTIBLOCK, structure);
    }
    
    /**
     * Full constructor
     */
    private SlimefunMachineData(String id, String name, String inventoryTitle, 
                                int[] inputSlots, int[] outputSlots, 
                                List<String> recipe, int energyCapacity, 
                                int energyConsumption, MachineType type,
                                List<MultiblockStructure> structure) {
        this.id = id;
        this.name = name;
        this.inventoryTitle = inventoryTitle;
        this.inputSlots = inputSlots;
        this.outputSlots = outputSlots;
        this.recipe = recipe;
        this.energyCapacity = energyCapacity;
        this.energyConsumption = energyConsumption;
        this.type = type;
        this.structure = structure;
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public String getInventoryTitle() { return inventoryTitle; }
    public int[] getInputSlots() { return inputSlots; }
    public int[] getOutputSlots() { return outputSlots; }
    public List<String> getRecipe() { return recipe; }
    public int getEnergyCapacity() { return energyCapacity; }
    public int getEnergyConsumption() { return energyConsumption; }
    public MachineType getType() { return type; }
    public List<MultiblockStructure> getStructure() { return structure; }
    
    public boolean hasInputSlots() {
        return inputSlots != null && inputSlots.length > 0;
    }
    
    public boolean hasOutputSlots() {
        return outputSlots != null && outputSlots.length > 0;
    }
    
    public boolean isMultiblock() {
        return type == MachineType.MULTIBLOCK;
    }
    
    public boolean isElectric() {
        return type == MachineType.ELECTRIC;
    }
    
    /**
     * Data class for multiblock structure components
     */
    public static class MultiblockStructure {
        private final String material;
        private final String name;
        
        public MultiblockStructure(String material, String name) {
            this.material = material;
            this.name = name;
        }
        
        public String getMaterial() { return material; }
        public String getName() { return name; }
    }
}