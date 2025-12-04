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
<<<<<<< HEAD
 * âœ… FIXED: Auto-pad recipes with AIR to reach 9 inputs for multiblock automation
=======
 * ✅ FIXED: Multiblock automation now works correctly in Dispenser GUI
>>>>>>> main
 */
public class MultiblockAutomationHandler {
    
    private static final int SEARCH_RADIUS = 5;
    private static ModConfig config;
    private static String selectedRecipeId = null;
    private static long lastProcessTime = 0;
<<<<<<< HEAD
    
    private static int currentSlotIndex = 0;
    private static int emptySlotCount = 0;
    private static boolean allSlotsFilled = false;
=======
    private static int nextSlotIndex = 0;
>>>>>>> main
    
    public static void init(ModConfig cfg) {
        config = cfg;
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Initialized with config");
    }
    
    public static void setSelectedRecipe(String recipeId) {
        selectedRecipeId = recipeId;
        resetAutomationState();
        
        if (recipeId != null) {
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] âœ“ Recipe selected: {}", recipeId);
        } else {
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Recipe deselected (null)");
        }
    }
    
    public static String getSelectedRecipe() {
        return selectedRecipeId;
    }
    
<<<<<<< HEAD
=======
    /**
     * ✅ FIX: Simplified tick - only check if GUI is open
     */
>>>>>>> main
    public static void tick(SlimefunMachineData machine) {
        if (machine == null) {
            return;
        }
        
        if (!machine.isMultiblock()) {
            return;
        }
        
        if (selectedRecipeId == null) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        
<<<<<<< HEAD
        if (player == null || level == null) {
            return;
        }
        
        AbstractContainerMenu container = player.containerMenu;
        boolean isDispenserGUI = container instanceof DispenserMenu;
        
        if (!isDispenserGUI) {
            return;
        }
        
        long now = System.currentTimeMillis();
        long timeSinceLastProcess = now - lastProcessTime;
        int delayMs = (config != null) ? config.getAutomationDelayMs() : 100;
        
        if (timeSinceLastProcess < delayMs) {
            return;
        }
        
        RecipeData recipe = RecipeDatabase.getRecipe(selectedRecipeId);
        if (recipe == null) {
            BapelSlimefunMod.LOGGER.warn("[MultiblockAuto] âœ– Recipe not found in database: {}", selectedRecipeId);
            return;
        }
        
        BlockPos dispenserPos = findNearbyDispenser(player, level);
        if (dispenserPos == null) {
            dispenserPos = player.blockPosition();
        }
        
        boolean actionTaken = autoFillDispenserRoundRobin(player, level, dispenserPos, recipe);
        
        if (actionTaken) {
            lastProcessTime = now;
        }
    }
    
    private static BlockPos findNearbyDispenser(LocalPlayer player, Level level) {
        BlockPos playerPos = player.blockPosition();
        
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -SEARCH_RADIUS; y <= SEARCH_RADIUS; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (level.getBlockEntity(pos) instanceof DispenserBlockEntity) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * âœ… FIXED: Auto-pad recipe inputs to 9 if needed
     */
    private static boolean autoFillDispenserRoundRobin(LocalPlayer player, Level level, 
                                                       BlockPos pos, RecipeData recipe) {
        if (!(player.containerMenu instanceof DispenserMenu)) {
            return false;
        }
        
=======
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

>>>>>>> main
        Minecraft mc = Minecraft.getInstance();
        AbstractContainerMenu menu = player.containerMenu;
        List<RecipeHandler.RecipeIngredient> inputs = recipe.getInputs();
        
<<<<<<< HEAD
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Recipe '{}' has {} raw inputs", 
            recipe.getRecipeId(), inputs.size());
        
        // âœ… CRITICAL FIX: Auto-pad inputs to 9 if needed
        List<RecipeHandler.RecipeIngredient> paddedInputs = padInputsTo9(inputs);
        
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] After padding: {} inputs", paddedInputs.size());
        
        if (paddedInputs.size() != 9) {
            BapelSlimefunMod.LOGGER.error("[MultiblockAuto] ERROR: After padding still got {} inputs", 
                paddedInputs.size());
            return false;
        }
        
        // Count how many slots still need work
        emptySlotCount = 0;
        allSlotsFilled = true;
        
        for (int i = 0; i < 9; i++) {
            RecipeHandler.RecipeIngredient target = paddedInputs.get(i);
=======
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
>>>>>>> main
            ItemStack currentStack = menu.getSlot(i).getItem();
            
            boolean needsWork = needsWorkOnSlot(currentStack, target);
            
            if (needsWork) {
                emptySlotCount++;
                allSlotsFilled = false;
            }
        }
        
        if (allSlotsFilled) {
            return false;
        }
        
        // Try to work on current slot (round-robin)
        for (int attempt = 0; attempt < 9; attempt++) {
            int slotIndex = (currentSlotIndex + attempt) % 9;
            RecipeHandler.RecipeIngredient target = paddedInputs.get(slotIndex);
            ItemStack currentStack = menu.getSlot(slotIndex).getItem();
            String currentId = AutomationUtils.getItemId(currentStack);
            
            // CASE 1: Slot should be EMPTY (AIR)
            if (target.getItemId().equals("AIR") || target.getAmount() == 0) {
                if (!currentStack.isEmpty()) {
                    // Remove wrong item
<<<<<<< HEAD
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, slotIndex, 0, 
                                                         ClickType.QUICK_MOVE, player);
                    
                    currentSlotIndex = (slotIndex + 1) % 9;
                    return true;
                }
                continue; // Already correct (empty)
            }
            
            // CASE 2: Slot needs a specific ITEM
=======
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, player);
                    nextSlotIndex = (i + 1) % 9;
                    return true;
                }
                continue;
            }

            // CASE 2: Slot needs ITEM
>>>>>>> main
            boolean isSameItem = !currentStack.isEmpty() && currentId.equals(target.getItemId());
            boolean needsRefill = currentStack.isEmpty() || 
                                 (isSameItem && currentStack.getCount() < currentStack.getMaxStackSize());
            
            if (needsRefill) {
<<<<<<< HEAD
                // A. If wrong item, remove it first
                if (!currentStack.isEmpty() && !isSameItem) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, slotIndex, 0, 
                                                         ClickType.QUICK_MOVE, player);
                    currentSlotIndex = (slotIndex + 1) % 9;
                    return true;
                }
                
                // B. Find item in player inventory
                int sourceSlot = findItemInPlayerInventory(menu, player, target.getItemId());
                
                if (sourceSlot != -1) {
                    // Place 1 item at a time
                    
                    // 1. Pick up stack from inventory
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, sourceSlot, 0, 
                                                         ClickType.PICKUP, player);
                    
                    // 2. Place 1 item in dispenser (Right Click = Button 1)
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, slotIndex, 1, 
                                                         ClickType.PICKUP, player);
                    
                    // 3. Return remaining items to inventory
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, sourceSlot, 0, 
                                                         ClickType.PICKUP, player);
                    
                    currentSlotIndex = (slotIndex + 1) % 9;
=======
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
>>>>>>> main
                    return true;
                }
                
                // No items available - try next slot
                continue;
            }
        }
        
        // Completed full cycle - move to next slot anyway
        currentSlotIndex = (currentSlotIndex + 1) % 9;
        return false;
    }
    
    /**
     * âœ… NEW: Pad recipe inputs to exactly 9 entries with AIR
     */
    private static List<RecipeHandler.RecipeIngredient> padInputsTo9(List<RecipeHandler.RecipeIngredient> inputs) {
        List<RecipeHandler.RecipeIngredient> padded = new ArrayList<>(inputs);
        
        // If already 9, return as-is
        if (padded.size() == 9) {
            return padded;
        }
        
        // If more than 9, trim to 9 (shouldn't happen)
        if (padded.size() > 9) {
            BapelSlimefunMod.LOGGER.warn("[MultiblockAuto] Recipe has {} inputs, trimming to 9", 
                padded.size());
            return new ArrayList<>(padded.subList(0, 9));
        }
        
        // Pad with AIR to reach 9
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Padding recipe from {} to 9 inputs with AIR", 
            padded.size());
        
        while (padded.size() < 9) {
            padded.add(new RecipeHandler.RecipeIngredient("AIR", 0));
        }
        
        return padded;
    }
    
    /**
     * Check if a slot needs work
     */
    private static boolean needsWorkOnSlot(ItemStack currentStack, RecipeHandler.RecipeIngredient target) {
        String currentId = AutomationUtils.getItemId(currentStack);
        
        // Should be empty but isn't
        if ((target.getItemId().equals("AIR") || target.getAmount() == 0) && !currentStack.isEmpty()) {
            return true;
        }
        
        // Should have item but doesn't
        if (!target.getItemId().equals("AIR") && target.getAmount() > 0) {
            // Empty slot that needs item
            if (currentStack.isEmpty()) {
                return true;
            }
            
            // Wrong item
            if (!currentId.equals(target.getItemId())) {
                return true;
            }
            
            // Right item but not full stack yet
            if (currentStack.getCount() < currentStack.getMaxStackSize()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
<<<<<<< HEAD
     * Find item in player inventory slots (9-44 for Dispenser GUI)
     */
    private static int findItemInPlayerInventory(AbstractContainerMenu menu, LocalPlayer player, 
                                                 String targetItemId) {
        int startSlot = 9; // Skip dispenser slots (0-8)
=======
     * Find item in player inventory slots (skip dispenser slots 0-8)
     */
    private static int findItemInPlayerInventory(AbstractContainerMenu menu, LocalPlayer player, String targetItemId) {
        int startSlot = 9; // Skip dispenser slots
>>>>>>> main
        int endSlot = menu.slots.size();
        
        for (int i = startSlot; i < endSlot; i++) {
            Slot slot = menu.slots.get(i);
            if (slot.hasItem()) {
                ItemStack stack = slot.getItem();
                String itemId = AutomationUtils.getItemId(stack);
                
                if (itemId.equals(targetItemId)) {
                    return i;
                }
            }
        }
        
        return -1;
    }
    
<<<<<<< HEAD
    /**
     * Reset automation state when recipe changes
     */
    private static void resetAutomationState() {
        currentSlotIndex = 0;
        emptySlotCount = 0;
        allSlotsFilled = false;
    }
    
=======
>>>>>>> main
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
<<<<<<< HEAD
=======
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
>>>>>>> main
     * Reset handler
     */
    public static void reset() {
        selectedRecipeId = null;
        lastProcessTime = 0;
        resetAutomationState();
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
<<<<<<< HEAD
=======
            List<RecipeHandler.RecipeIngredient> recipeIngredients = recipe.getInputs();
>>>>>>> main
            
            // âœ… Use padded inputs
            List<RecipeHandler.RecipeIngredient> paddedInputs = padInputsTo9(recipe.getInputs());
            
            return new RecipeHandler.RecipeSummary(inventory, paddedInputs);
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[MultiblockAuto] Error getting recipe summary", e);
            return null;
        }
    }
    
    /**
     * Get automation status for debugging
     */
    public static String getAutomationStatus() {
        return String.format("Slot: %d/9 | Empty: %d | Filled: %s | Recipe: %s", 
                           currentSlotIndex, emptySlotCount, allSlotsFilled, 
                           selectedRecipeId != null ? selectedRecipeId : "NONE");
    }
}