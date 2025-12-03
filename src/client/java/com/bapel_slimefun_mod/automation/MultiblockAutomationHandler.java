package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;

import java.util.*;

/**
 * Handles automation specifically for multiblock machines
 * 
 * Multiblock machines work differently:
 * - No GUI slots like electric machines
 * - Items are placed in dispenser/hopper blocks
 * - Right-click on dispenser triggers recipe processing
 * - Output appears in dispenser/player inventory
 */
public class MultiblockAutomationHandler {
    
    private static final int SEARCH_RADIUS = 5; // Blocks to search around player
    private static ModConfig config;
    private static String selectedRecipeId = null;
    private static long lastProcessTime = 0;
    
    public static void init(ModConfig cfg) {
        config = cfg;
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Handler initialized");
    }
    
    /**
     * Set selected recipe for multiblock machine
     */
    public static void setSelectedRecipe(String recipeId) {
        selectedRecipeId = recipeId;
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Selected recipe: {}", recipeId);
    }
    
    public static String getSelectedRecipe() {
        return selectedRecipeId;
    }
    
    /**
     * Main tick handler for multiblock automation
     * Called every game tick when automation is enabled
     */
    public static void tick(SlimefunMachineData machine) {
        if (machine == null || !machine.isMultiblock()) return;
        if (selectedRecipeId == null) return;
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        
        if (player == null || level == null) return;
        
        // Throttle processing
        long now = System.currentTimeMillis();
        if (config != null && now - lastProcessTime < config.getAutomationDelayMs()) {
            return;
        }
        lastProcessTime = now;
        
        // Get recipe requirements
        RecipeData recipe = RecipeDatabase.getRecipe(selectedRecipeId);
        if (recipe == null) {
            BapelSlimefunMod.LOGGER.warn("[MultiblockAuto] Recipe not found: {}", selectedRecipeId);
            return;
        }
        
        // Find nearby dispenser/hopper
        BlockPos dispenserPos = findNearbyDispenser(player, level);
        if (dispenserPos == null) {
            // No dispenser found - might not be near the multiblock
            return;
        }
        
        // Try to auto-fill dispenser with recipe ingredients
        boolean inserted = autoFillDispenser(player, level, dispenserPos, recipe);
        
        if (inserted) {
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Auto-filled dispenser with recipe items");
        }
    }
    
    /**
     * Find dispenser/hopper near player
     */
    private static BlockPos findNearbyDispenser(LocalPlayer player, Level level) {
        BlockPos playerPos = player.blockPosition();
        
        // Search in radius around player
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -SEARCH_RADIUS; y <= SEARCH_RADIUS; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    
                    if (blockEntity instanceof DispenserBlockEntity) {
                        return pos;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Auto-fill dispenser with recipe ingredients from player inventory
     */
    private static boolean autoFillDispenser(LocalPlayer player, Level level, 
                                            BlockPos pos, RecipeData recipe) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof DispenserBlockEntity)) {
            return false;
        }
        
        DispenserBlockEntity dispenser = (DispenserBlockEntity) blockEntity;
        
        // Get recipe requirements
        Map<String, Integer> requirements = recipe.getGroupedInputs();
        
        // Check if dispenser already has items
        boolean hasItems = false;
        for (int i = 0; i < dispenser.getContainerSize(); i++) {
            if (!dispenser.getItem(i).isEmpty()) {
                hasItems = true;
                break;
            }
        }
        
        // If dispenser already has items, don't interfere
        if (hasItems) {
            return false;
        }
        
        // Try to insert items from player inventory
        Map<String, Integer> toInsert = new HashMap<>(requirements);
        List<ItemStack> playerInv = getPlayerInventory(player);
        
        boolean inserted = false;
        int dispenserSlot = 0;
        
        for (ItemStack stack : playerInv) {
            if (stack.isEmpty() || dispenserSlot >= dispenser.getContainerSize()) continue;
            
            String itemId = getItemId(stack);
            Integer needed = toInsert.get(itemId);
            
            if (needed != null && needed > 0) {
                int amount = Math.min(needed, stack.getCount());
                
                // Create stack to insert
                ItemStack insertStack = stack.copy();
                insertStack.setCount(amount);
                
                // Insert into dispenser
                dispenser.setItem(dispenserSlot++, insertStack);
                
                // Update tracking
                toInsert.put(itemId, needed - amount);
                inserted = true;
                
                BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Inserted {} x{} into dispenser", 
                    itemId, amount);
            }
        }
        
        return inserted;
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
     * Get item ID from ItemStack
     * FIXED: Compatible with Minecraft 1.21.8 API
     */
    private static String getItemId(ItemStack stack) {
        if (stack.isEmpty()) return "AIR";
        
        try {
            // Try to get custom name from hover text
            String hoverName = stack.getHoverName().getString();
            if (hoverName != null && !hoverName.isEmpty()) {
                // Clean color codes and format as ID
                String cleaned = hoverName.replaceAll("§[0-9a-fk-or]", "");
                if (!cleaned.isEmpty() && !cleaned.equals(stack.getItem().toString())) {
                    return cleaned.trim().toUpperCase().replace(" ", "_");
                }
            }
        } catch (Exception e) {
            // Ignore errors and fallback
        }
        
        // Fallback to registry name
        String registryName = stack.getItem().toString();
        return registryName.toUpperCase();
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
                String itemId = getItemId(stack);
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
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Reset");
    }
    
    /**
     * Get recipe summary for multiblock
     * FIXED: Use correct return type
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
            // FIXED: recipe.getInputs() already returns List<RecipeIngredient>
            List<RecipeHandler.RecipeIngredient> recipeIngredients = recipe.getInputs();
            
            return new RecipeHandler.RecipeSummary(inventory, recipeIngredients);
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[MultiblockAuto] Error getting recipe summary", e);
            return null;
        }
    }
}
