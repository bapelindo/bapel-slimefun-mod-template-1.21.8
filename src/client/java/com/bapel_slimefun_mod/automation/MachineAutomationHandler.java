package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Enhanced automation handler with multi-item recipe support
 */
public class MachineAutomationHandler {
    private static SlimefunMachineData currentMachine = null;
    private static long lastAutoTick = 0;
    private static ModConfig config;
    private static Map<String, Integer> cachedRecipeRequirements = new HashMap<>();
    
    /**
     * Initialize with config
     */
    public static void init(ModConfig cfg) {
        config = cfg;
    }
    
    /**
     * Called when player opens a container
     */
    public static void onContainerOpen(String title) {
        currentMachine = SlimefunDataLoader.getMachineByTitle(title);
        if (currentMachine != null) {
            BapelSlimefunMod.LOGGER.info("Detected machine: {}", currentMachine.getName());
            
            // Cache recipe requirements for performance
            cacheRecipeRequirements();
            
            // Log recipe info
            if (config.isDebugMode()) {
                logRecipeInfo();
            }
        }
    }
    
    /**
     * Called when player closes a container
     */
    public static void onContainerClose() {
        currentMachine = null;
        cachedRecipeRequirements.clear();
    }
    
    /**
     * Cache recipe requirements for quick lookup
     */
    private static void cacheRecipeRequirements() {
        if (currentMachine == null || currentMachine.getRecipe().isEmpty()) {
            cachedRecipeRequirements.clear();
            return;
        }
        
        List<RecipeHandler.RecipeIngredient> ingredients = 
            RecipeHandler.parseRecipe(currentMachine.getRecipe());
        
        cachedRecipeRequirements = RecipeHandler.groupRecipeIngredients(ingredients);
        
        if (config.isDebugMode()) {
            BapelSlimefunMod.LOGGER.info("Recipe requirements: {}", cachedRecipeRequirements);
        }
    }
    
    /**
     * Log detailed recipe information
     */
    private static void logRecipeInfo() {
        if (currentMachine == null) return;
        
        List<String> recipe = currentMachine.getRecipe();
        if (recipe.isEmpty()) {
            BapelSlimefunMod.LOGGER.debug("Machine has no recipe");
            return;
        }
        
        List<RecipeHandler.RecipeIngredient> ingredients = RecipeHandler.parseRecipe(recipe);
        boolean isMultiItem = RecipeHandler.isMultiItemRecipe(ingredients);
        Set<String> uniqueItems = RecipeHandler.getUniqueItems(ingredients);
        
        BapelSlimefunMod.LOGGER.debug("Recipe Info:");
        BapelSlimefunMod.LOGGER.debug("  Total ingredients: {}", ingredients.size());
        BapelSlimefunMod.LOGGER.debug("  Unique items: {}", uniqueItems.size());
        BapelSlimefunMod.LOGGER.debug("  Multi-item recipe: {}", isMultiItem);
        BapelSlimefunMod.LOGGER.debug("  Requirements: {}", cachedRecipeRequirements);
    }
    
    /**
     * Main automation tick - called every client tick
     */
    public static void tick() {
        if (config == null || !config.isAutomationEnabled()) return;
        if (currentMachine == null) return;
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;
        
        // Rate limiting with configurable delay
        long now = System.currentTimeMillis();
        if (now - lastAutoTick < config.getAutomationDelayMs()) return;
        lastAutoTick = now;
        
        // 1. Auto-output: Move items from output slots to player inventory
        autoOutput(menu, mc);
        
        // 2. Auto-input: Move matching items from player to input slots
        autoInput(menu, player, mc);
    }
    
    /**
     * Automatically move items from output slots to player inventory
     */
    private static void autoOutput(AbstractContainerMenu menu, Minecraft mc) {
        if (!currentMachine.hasOutputSlots()) return;
        
        for (int slotIndex : currentMachine.getOutputSlots()) {
            if (slotIndex >= menu.slots.size()) continue;
            
            Slot slot = menu.slots.get(slotIndex);
            ItemStack stack = slot.getItem();
            
            if (!stack.isEmpty()) {
                mc.gameMode.handleInventoryMouseClick(
                    menu.containerId, slotIndex, 0, ClickType.QUICK_MOVE, mc.player
                );
                
                if (config.isDebugMode()) {
                    BapelSlimefunMod.LOGGER.debug("Auto-output: Moved {} x{} from slot {}", 
                        AutomationUtils.getItemId(stack), stack.getCount(), slotIndex);
                }
                break; // Only move one stack per tick
            }
        }
    }
    
    /**
     * Automatically move matching items from player to input slots (Enhanced)
     */
    private static void autoInput(AbstractContainerMenu menu, LocalPlayer player, Minecraft mc) {
        if (!currentMachine.hasInputSlots()) return;
        if (cachedRecipeRequirements.isEmpty()) return;
        
        // Check if any input slot is empty
        if (!hasEmptyInputSlot(menu)) return;
        
        // Get player inventory items
        List<ItemStack> playerInventory = getPlayerInventoryStacks(player);
        
        // Calculate what we need
        RecipeHandler.RecipeSummary summary = new RecipeHandler.RecipeSummary(
            playerInventory, 
            RecipeHandler.parseRecipe(currentMachine.getRecipe())
        );
        
        if (config.isDebugMode() && summary.getCompletionPercentage() > 0) {
            BapelSlimefunMod.LOGGER.debug("Recipe completion: {:.1f}%", 
                summary.getCompletionPercentage() * 100);
        }
        
        // Find and move one item that's needed
        for (Map.Entry<String, Integer> required : cachedRecipeRequirements.entrySet()) {
            String itemId = required.getKey();
            
            // Try to move this item to input
            if (moveItemToInput(menu, player, mc, itemId)) {
                break; // Only move one item per tick
            }
        }
    }
    
    /**
     * Check if there's at least one empty input slot
     */
    private static boolean hasEmptyInputSlot(AbstractContainerMenu menu) {
        for (int slotIndex : currentMachine.getInputSlots()) {
            if (slotIndex < menu.slots.size()) {
                Slot slot = menu.slots.get(slotIndex);
                if (slot.getItem().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Get all items from player inventory
     */
    private static List<ItemStack> getPlayerInventoryStacks(LocalPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        
        return stacks;
    }
    
    /**
     * Move a specific item from player inventory to input slot
     */
    private static boolean moveItemToInput(AbstractContainerMenu menu, LocalPlayer player, 
                                          Minecraft mc, String itemId) {
        // Find item in player inventory
        int playerSlotIndex = findItemInPlayerInventory(menu, player, itemId);
        if (playerSlotIndex == -1) return false;
        
        // Find empty input slot
        int emptyInputSlot = findEmptyInputSlot(menu);
        if (emptyInputSlot == -1) return false;
        
        // Move item
        mc.gameMode.handleInventoryMouseClick(
            menu.containerId, playerSlotIndex, 0, ClickType.QUICK_MOVE, player
        );
        
        if (config.isDebugMode()) {
            BapelSlimefunMod.LOGGER.debug("Auto-input: Moved {} to slot {}", 
                itemId, emptyInputSlot);
        }
        
        return true;
    }
    
    /**
     * Find item in player inventory section of container
     */
    private static int findItemInPlayerInventory(AbstractContainerMenu menu, 
                                                 LocalPlayer player, String itemId) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            
            // Check if this is a player inventory slot
            if (slot.container != player.getInventory()) continue;
            
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            
            String stackItemId = AutomationUtils.getItemId(stack);
            if (stackItemId.equalsIgnoreCase(itemId)) {
                return i;
            }
        }
        
        return -1;
    }
    
    /**
     * Find first empty input slot
     */
    private static int findEmptyInputSlot(AbstractContainerMenu menu) {
        for (int slotIndex : currentMachine.getInputSlots()) {
            if (slotIndex >= menu.slots.size()) continue;
            
            Slot slot = menu.slots.get(slotIndex);
            if (slot.getItem().isEmpty()) {
                return slotIndex;
            }
        }
        
        return -1;
    }
    
    /**
     * Count specific item in input slots
     */
    private static int countItemInInputSlots(AbstractContainerMenu menu, String itemId) {
        int total = 0;
        
        for (int slotIndex : currentMachine.getInputSlots()) {
            if (slotIndex >= menu.slots.size()) continue;
            
            Slot slot = menu.slots.get(slotIndex);
            ItemStack stack = slot.getItem();
            
            if (!stack.isEmpty()) {
                String stackItemId = AutomationUtils.getItemId(stack);
                if (stackItemId.equalsIgnoreCase(itemId)) {
                    total += stack.getCount();
                }
            }
        }
        
        return total;
    }
    
    /**
     * Check if automation is active
     */
    public static boolean isActive() {
        return currentMachine != null;
    }
    
    /**
     * Get current machine (for debugging/UI)
     */
    public static SlimefunMachineData getCurrentMachine() {
        return currentMachine;
    }
    
    /**
     * Get cached recipe requirements
     */
    public static Map<String, Integer> getCachedRecipeRequirements() {
        return new HashMap<>(cachedRecipeRequirements);
    }
    
    /**
     * Get recipe summary for current machine
     */
    public static RecipeHandler.RecipeSummary getRecipeSummary() {
        if (currentMachine == null) return null;
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return null;
        
        List<ItemStack> inventory = getPlayerInventoryStacks(player);
        List<RecipeHandler.RecipeIngredient> recipe = 
            RecipeHandler.parseRecipe(currentMachine.getRecipe());
        
        return new RecipeHandler.RecipeSummary(inventory, recipe);
    }
    
    /**
     * Toggle automation on/off
     */
    public static void toggleAutomation() {
        if (config != null) {
            config.setAutomationEnabled(!config.isAutomationEnabled());
            BapelSlimefunMod.LOGGER.info("Slimefun Automation: {}", 
                config.isAutomationEnabled() ? "ENABLED" : "DISABLED");
        }
    }
    
    /**
     * Set automation state
     */
    public static void setAutomationEnabled(boolean enabled) {
        if (config != null) {
            config.setAutomationEnabled(enabled);
            BapelSlimefunMod.LOGGER.info("Slimefun Automation: {}", 
                enabled ? "ENABLED" : "DISABLED");
        }
    }
    
    /**
     * Check if automation is enabled
     */
    public static boolean isAutomationEnabled() {
        return config != null && config.isAutomationEnabled();
    }
    
    /**
     * Get config instance
     */
    public static ModConfig getConfig() {
        return config;
    }
}