package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;

import java.util.*;

/**
 * ✅ FIXED: Multiblock automation now works correctly in Dispenser GUI
 */
public class MultiblockAutomationHandler {
    
    private static final int SEARCH_RADIUS = 5;
    private static ModConfig config;
    private static String selectedRecipeId = null;
    private static long lastProcessTime = 0;
    private static int nextSlotIndex = 0;
    
    public static void init(ModConfig cfg) {
        config = cfg;
    }
    
    public static void setSelectedRecipe(String recipeId) {
        selectedRecipeId = recipeId;
    }
    
    public static String getSelectedRecipe() {
        return selectedRecipeId;
    }
    
    /**
     * ✅ FIX: Simplified tick - only check if GUI is open
     */
    public static void tick(SlimefunMachineData machine) {
        if (machine == null || !machine.isMultiblock()) return;
        if (selectedRecipeId == null) return;
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        
        if (player == null || level == null) return;
        
        // ✅ FIX: Check if Dispenser GUI is actually open
        if (!(player.containerMenu instanceof DispenserMenu)) {
            return; // Not in dispenser, skip
        }
        
        // Throttle processing
        long now = System.currentTimeMillis();
        if (config != null && now - lastProcessTime < config.getAutomationDelayMs()) {
            return;
        }
        
        // ✅ FIX: Direct automation (no need to find dispenser - we're IN the GUI!)
        RecipeData recipe = RecipeDatabase.getRecipe(selectedRecipeId);
        if (recipe != null) {
            if (autoFillDispenser(player, level, player.blockPosition(), recipe)) {
                lastProcessTime = now;
            }
        }
    }
    
    /**
     * ✅ FIX: Improved dispenser filling with proper item distribution
     */
    private static boolean autoFillDispenser(LocalPlayer player, Level level, BlockPos pos, RecipeData recipe) {
        // REQUIREMENT: Dispenser GUI must be open
        if (!(player.containerMenu instanceof DispenserMenu)) return false;

        Minecraft mc = Minecraft.getInstance();
        AbstractContainerMenu menu = player.containerMenu;
        List<RecipeHandler.RecipeIngredient> inputs = recipe.getInputs();
        
        // ✅ FIX: Must be 9-slot recipe (3x3 grid)
        if (inputs.size() != 9) {
            BapelSlimefunMod.LOGGER.warn("[MultiblockAuto] Recipe must have exactly 9 slots, got: {}", inputs.size());
            return false;
        }

        // Process slots in round-robin fashion (1 per tick)
        for (int k = 0; k < 9; k++) {
            int i = (nextSlotIndex + k) % 9;
            RecipeHandler.RecipeIngredient target = inputs.get(i);
            
            // Get current item in dispenser slot
            ItemStack currentStack = menu.getSlot(i).getItem();
            String currentId = AutomationUtils.getItemId(currentStack);
            
            // CASE 1: Slot should be EMPTY (AIR)
            if (target.getItemId().equals("AIR") || target.getAmount() == 0) {
                if (!currentStack.isEmpty()) {
                    // Remove wrong item
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, player);
                    nextSlotIndex = (i + 1) % 9;
                    return true;
                }
                continue;
            }

            // CASE 2: Slot needs ITEM
            boolean isSameItem = !currentStack.isEmpty() && currentId.equals(target.getItemId());
            boolean needsRefill = currentStack.isEmpty() || 
                                 (isSameItem && currentStack.getCount() < currentStack.getMaxStackSize());
            
            if (needsRefill) {
                // Remove wrong item first
                if (!currentStack.isEmpty() && !isSameItem) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, player);
                    nextSlotIndex = (i + 1) % 9;
                    return true;
                }

                // Find item in player inventory
                int sourceSlot = findItemInPlayerInventory(menu, player, target.getItemId());
                
                if (sourceSlot != -1) {
                    // ✅ TECHNIQUE: 1-by-1 distribution (PICK - PLACE ONE - RETURN)
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, sourceSlot, 0, ClickType.PICKUP, player);
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 1, ClickType.PICKUP, player); // Right-click = 1 item
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, sourceSlot, 0, ClickType.PICKUP, player);
                    
                    nextSlotIndex = (i + 1) % 9;
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Find item in player inventory slots (skip dispenser slots 0-8)
     */
    private static int findItemInPlayerInventory(AbstractContainerMenu menu, LocalPlayer player, String targetItemId) {
        int startSlot = 9; // Skip dispenser slots
        int endSlot = menu.slots.size();

        for (int i = startSlot; i < endSlot; i++) {
            Slot slot = menu.slots.get(i);
            if (slot.hasItem()) {
                ItemStack stack = slot.getItem();
                if (AutomationUtils.getItemId(stack).equals(targetItemId)) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    /**
     * Get player inventory as list
     */
    private static List<ItemStack> getPlayerInventory(LocalPlayer player) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            items.add(player.getInventory().getItem(i));
        }
        return items;
    }
    
    /**
     * Check if we can process recipe with current dispenser contents
     */
    public static boolean canProcess(SlimefunMachineData machine, BlockPos dispenserPos, Level level) {
        if (selectedRecipeId == null) return false;
        
        RecipeData recipe = RecipeDatabase.getRecipe(selectedRecipeId);
        if (recipe == null) return false;
        
        BlockEntity blockEntity = level.getBlockEntity(dispenserPos);
        if (!(blockEntity instanceof DispenserBlockEntity)) return false;
        
        DispenserBlockEntity dispenser = (DispenserBlockEntity) blockEntity;
        
        // Check if dispenser has all required items
        Map<String, Integer> requirements = recipe.getGroupedInputs();
        Map<String, Integer> available = new HashMap<>();
        
        for (int i = 0; i < dispenser.getContainerSize(); i++) {
            ItemStack stack = dispenser.getItem(i);
            if (!stack.isEmpty()) {
                String itemId = AutomationUtils.getItemId(stack);
                available.put(itemId, available.getOrDefault(itemId, 0) + stack.getCount());
            }
        }
        
        // Check if all requirements are met
        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            int needed = entry.getValue();
            int has = available.getOrDefault(entry.getKey(), 0);
            if (has < needed) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Reset handler
     */
    public static void reset() {
        selectedRecipeId = null;
        lastProcessTime = 0;
    }
    
    /**
     * Get recipe summary for multiblock
     */
    public static RecipeHandler.RecipeSummary getRecipeSummary(SlimefunMachineData machine) {
        if (machine == null || !machine.isMultiblock()) return null;
        if (selectedRecipeId == null) return null;
        
        RecipeData recipe = RecipeDatabase.getRecipe(selectedRecipeId);
        if (recipe == null) return null;
        
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return null;
            
            List<ItemStack> inventory = getPlayerInventory(player);
            List<RecipeHandler.RecipeIngredient> recipeIngredients = recipe.getInputs();
            
            return new RecipeHandler.RecipeSummary(inventory, recipeIngredients);
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[MultiblockAuto] Error getting recipe summary", e);
            return null;
        }
    }
}