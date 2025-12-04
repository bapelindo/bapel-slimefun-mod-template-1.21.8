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
 * ✅ COMPLETELY FIXED: Multiblock automation with proper round-robin auto-fill
 * 
 * KEY FIXES:
 * 1. Added extensive debug logging to diagnose issues
 * 2. Round-robin works continuously until items run out
 * 3. Auto-fills ALL slots that need items
 * 4. Better slot state tracking (empty, wrong item, correct item)
 * 5. Proper item distribution (one-by-one placement)
 * 6. Works when GUI is open with proper checks
 */
public class MultiblockAutomationHandler {
    
    private static final int SEARCH_RADIUS = 5;
    private static ModConfig config;
    private static String selectedRecipeId = null;
    private static long lastProcessTime = 0;
    
    // ✅ Better state tracking
    private static int currentSlotIndex = 0; // Which slot we're currently trying to fill
    private static int emptySlotCount = 0;   // How many slots still need items
    private static boolean allSlotsFilled = false; // Stop when all slots filled
    
    public static void init(ModConfig cfg) {
        config = cfg;
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Initialized with config");
    }
    
    public static void setSelectedRecipe(String recipeId) {
        selectedRecipeId = recipeId;
        resetAutomationState();
        
        // Log when recipe is selected
        if (recipeId != null) {
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ✓ Recipe selected: {}", recipeId);
        } else {
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Recipe deselected (null)");
        }
    }
    
    public static String getSelectedRecipe() {
        return selectedRecipeId;
    }
    
    /**
     * ✅ FIXED: Main tick handler - continuously fills until complete
     * 
     * CRITICAL: Multiblock automation REQUIRES Dispenser GUI to be open!
     * We need the GUI menu to perform click actions (PICKUP, QUICK_MOVE)
     */
    public static void tick(SlimefunMachineData machine) {
        // ✅ DEBUG: Check #1 - Machine validity
        if (machine == null) {
            BapelSlimefunMod.LOGGER.debug("[MultiblockAuto] ❌ Machine is NULL");
            return;
        }
        
if (!machine.isMultiblock()) {
            // Change .getMachineId() to .getId()
            BapelSlimefunMod.LOGGER.debug("[MultiblockAuto] ❌ Machine is not multiblock: {}", machine.getId());
            return;
        }
        
        // ✅ DEBUG: Check #2 - Recipe selection
        if (selectedRecipeId == null) {
            BapelSlimefunMod.LOGGER.debug("[MultiblockAuto] ❌ No recipe selected");
            return;
        }
        
        // ✅ DEBUG: Check #3 - Minecraft instance
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        
        if (player == null) {
            BapelSlimefunMod.LOGGER.debug("[MultiblockAuto] ❌ Player is NULL");
            return;
        }
        if (level == null) {
            BapelSlimefunMod.LOGGER.debug("[MultiblockAuto] ❌ Level is NULL");
            return;
        }
        
        // ✅ DEBUG: Check #4 - GUI must be open
        AbstractContainerMenu container = player.containerMenu;
        boolean isDispenserGUI = container instanceof DispenserMenu;
        
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Container type: {} | IsDispenserGUI: {}", 
                                    container.getClass().getSimpleName(), isDispenserGUI);
        
        if (!isDispenserGUI) {
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ❌ GUI NOT OPEN! Need to open Dispenser GUI first!");
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Current container: {}", container.getClass().getName());
            return;
        }
        
        // ✅ Log that we passed all checks!
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ✓✓✓ PASSED ALL CHECKS! Starting automation...");
        
        // ✅ DEBUG: Check #5 - Throttling
        long now = System.currentTimeMillis();
        long timeSinceLastProcess = now - lastProcessTime;
        int delayMs = (config != null) ? config.getAutomationDelayMs() : 100;
        
        if (timeSinceLastProcess < delayMs) {
            BapelSlimefunMod.LOGGER.debug("[MultiblockAuto] ⏸ Throttled: {}ms < {}ms", timeSinceLastProcess, delayMs);
            return;
        }
        
        // ✅ DEBUG: Check #6 - Recipe data
        RecipeData recipe = RecipeDatabase.getRecipe(selectedRecipeId);
        if (recipe == null) {
            BapelSlimefunMod.LOGGER.warn("[MultiblockAuto] ❌ Recipe not found in database: {}", selectedRecipeId);
            return;
        }
        
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ✓ Recipe found: {}", recipe.getRecipeId());
        
        // ✅ DEBUG: Check #7 - Dispenser position
        BlockPos dispenserPos = findNearbyDispenser(player, level);
        if (dispenserPos == null) {
            BapelSlimefunMod.LOGGER.warn("[MultiblockAuto] ⚠ No dispenser found nearby, using player position");
            dispenserPos = player.blockPosition();
        } else {
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ✓ Dispenser found at: {}", dispenserPos);
        }
        
        // ✅ KEY FIX: Try to fill slots continuously
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ▶▶▶ Calling autoFillDispenserRoundRobin...");
        boolean actionTaken = autoFillDispenserRoundRobin(player, level, dispenserPos, recipe);
        
        if (actionTaken) {
            lastProcessTime = now;
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ✅✅✅ ACTION TAKEN! Item moved successfully!");
        } else {
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ⭕ No action taken (maybe all filled or no items?)");
        }
    }
    
    /**
     * Find dispenser near player
     */
    private static BlockPos findNearbyDispenser(LocalPlayer player, Level level) {
        BlockPos playerPos = player.blockPosition();
        BapelSlimefunMod.LOGGER.debug("[MultiblockAuto] Searching for dispenser around {}", playerPos);
        
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -SEARCH_RADIUS; y <= SEARCH_RADIUS; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (level.getBlockEntity(pos) instanceof DispenserBlockEntity) {
                        BapelSlimefunMod.LOGGER.debug("[MultiblockAuto] Found dispenser at offset ({},{},{})", x, y, z);
                        return pos;
                    }
                }
            }
        }
        BapelSlimefunMod.LOGGER.debug("[MultiblockAuto] No dispenser found in radius {}", SEARCH_RADIUS);
        return null;
    }
    
    /**
     * ✅ COMPLETELY REWRITTEN: Round-robin auto-fill with continuous operation
     * 
     * Algorithm:
     * 1. Check all 9 slots in round-robin order
     * 2. For each slot:
     *    - If empty and needs item → place 1 item
     *    - If has wrong item → remove it
     *    - If has correct item but not full → add 1 more
     * 3. Continue until all slots are correctly filled or player runs out of items
     */
    private static boolean autoFillDispenserRoundRobin(LocalPlayer player, Level level, 
                                                       BlockPos pos, RecipeData recipe) {
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] === START autoFillDispenserRoundRobin ===");
        
        // Must have Dispenser GUI open
        if (!(player.containerMenu instanceof DispenserMenu)) {
            BapelSlimefunMod.LOGGER.error("[MultiblockAuto] ERROR: Not DispenserMenu!");
            return false;
        }
        
        Minecraft mc = Minecraft.getInstance();
        AbstractContainerMenu menu = player.containerMenu;
        List<RecipeHandler.RecipeIngredient> inputs = recipe.getInputs();
        
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Recipe has {} inputs", inputs.size());
        
        if (inputs.size() != 9) {
            BapelSlimefunMod.LOGGER.error("[MultiblockAuto] ERROR: Recipe must have 9 inputs, got {}", inputs.size());
            return false;
        }
        
        // ✅ STEP 1: Count how many slots still need work
        emptySlotCount = 0;
        allSlotsFilled = true;
        
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] === Checking all 9 slots ===");
        for (int i = 0; i < 9; i++) {
            RecipeHandler.RecipeIngredient target = inputs.get(i);
            ItemStack currentStack = menu.getSlot(i).getItem();
            String currentId = AutomationUtils.getItemId(currentStack);
            
            boolean needsWork = needsWorkOnSlot(currentStack, target);
            
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Slot {}: Current='{}' Target='{}' NeedsWork={}", 
                                        i, currentId, target.getItemId(), needsWork);
            
            if (needsWork) {
                emptySlotCount++;
                allSlotsFilled = false;
            }
        }
        
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Summary: {} slots need work | AllFilled: {}", 
                                    emptySlotCount, allSlotsFilled);
        
        // If all slots are filled correctly, stop automation
        if (allSlotsFilled) {
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ✓✓✓ All slots filled correctly!");
            return false;
        }
        
        // ✅ STEP 2: Try to work on current slot (round-robin)
        // We'll try up to 9 slots (full cycle) to find one that needs work
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] === Starting round-robin from slot {} ===", currentSlotIndex);
        
        for (int attempt = 0; attempt < 9; attempt++) {
            int slotIndex = (currentSlotIndex + attempt) % 9;
            RecipeHandler.RecipeIngredient target = inputs.get(slotIndex);
            ItemStack currentStack = menu.getSlot(slotIndex).getItem();
            String currentId = AutomationUtils.getItemId(currentStack);
            
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Attempt {}: Checking slot {}", attempt, slotIndex);
            
            // ✅ CASE 1: Slot should be EMPTY (AIR)
            if (target.getItemId().equals("AIR") || target.getAmount() == 0) {
                if (!currentStack.isEmpty()) {
                    // Remove wrong item
                    BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ➜ Removing wrong item from slot {}", slotIndex);
                    
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, slotIndex, 0, 
                                                         ClickType.QUICK_MOVE, player);
                    
                    // Move to next slot for next tick
                    currentSlotIndex = (slotIndex + 1) % 9;
                    BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ✓ Removed! Next slot will be: {}", currentSlotIndex);
                    return true;
                }
                BapelSlimefunMod.LOGGER.debug("[MultiblockAuto] Slot {} already empty (correct)", slotIndex);
                continue; // Already correct (empty)
            }
            
            // ✅ CASE 2: Slot needs a specific ITEM
            boolean isSameItem = !currentStack.isEmpty() && currentId.equals(target.getItemId());
            boolean needsRefill = currentStack.isEmpty() || 
                                 (isSameItem && currentStack.getCount() < currentStack.getMaxStackSize());
            
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Slot {}: SameItem={} NeedsRefill={}", 
                                        slotIndex, isSameItem, needsRefill);
            
            if (needsRefill) {
                // A. If wrong item, remove it first
                if (!currentStack.isEmpty() && !isSameItem) {
                    BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ➜ Removing wrong item '{}' (need '{}')", 
                                                currentId, target.getItemId());
                    
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, slotIndex, 0, 
                                                         ClickType.QUICK_MOVE, player);
                    currentSlotIndex = (slotIndex + 1) % 9;
                    BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ✓ Removed! Next slot: {}", currentSlotIndex);
                    return true;
                }
                
                // B. Find item in player inventory
                int sourceSlot = findItemInPlayerInventory(menu, player, target.getItemId());
                
                BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Looking for '{}' in inventory: Found at slot {}", 
                                            target.getItemId(), sourceSlot);
                
                if (sourceSlot != -1) {
                    // ✅ TECHNIQUE: Place 1 item at a time (PICK - PLACE ONE - RETURN)
                    
                    BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ➜➜➜ Placing 1x {} from slot {} to slot {}", 
                                                target.getItemId(), sourceSlot, slotIndex);
                    
                    // 1. Pick up stack from inventory (Left Click)
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, sourceSlot, 0, 
                                                         ClickType.PICKUP, player);
                    BapelSlimefunMod.LOGGER.debug("[MultiblockAuto]   Step 1: Picked up from inventory");
                    
                    // 2. Place 1 item in dispenser (Right Click = Button 1)
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, slotIndex, 1, 
                                                         ClickType.PICKUP, player);
                    BapelSlimefunMod.LOGGER.debug("[MultiblockAuto]   Step 2: Placed 1 item in dispenser");
                    
                    // 3. Return remaining items to inventory (Left Click)
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, sourceSlot, 0, 
                                                         ClickType.PICKUP, player);
                    BapelSlimefunMod.LOGGER.debug("[MultiblockAuto]   Step 3: Returned rest to inventory");
                    
                    BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ✅✅✅ PLACED 1x {} in slot {}!", 
                                                target.getItemId(), slotIndex);
                    
                    // Move to next slot for next tick
                    currentSlotIndex = (slotIndex + 1) % 9;
                    BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Next slot will be: {}", currentSlotIndex);
                    return true;
                }
                
                // No items available in inventory - try next slot
                BapelSlimefunMod.LOGGER.warn("[MultiblockAuto] ❌ No '{}' available in inventory!", target.getItemId());
                continue;
            } else {
                BapelSlimefunMod.LOGGER.debug("[MultiblockAuto] Slot {} doesn't need refill", slotIndex);
            }
        }
        
        // ✅ Completed full cycle - move to next slot anyway
        currentSlotIndex = (currentSlotIndex + 1) % 9;
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Completed full cycle. Next slot: {}", currentSlotIndex);
        return false;
    }
    
    /**
     * ✅ NEW: Check if a slot needs work
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
     * Find item in player inventory slots (9-44 for Dispenser GUI)
     */
    private static int findItemInPlayerInventory(AbstractContainerMenu menu, LocalPlayer player, 
                                                 String targetItemId) {
        int startSlot = 9; // Skip dispenser slots (0-8)
        int endSlot = menu.slots.size();
        
        BapelSlimefunMod.LOGGER.debug("[MultiblockAuto] Searching inventory slots {}-{} for '{}'", 
                                     startSlot, endSlot-1, targetItemId);
        
        for (int i = startSlot; i < endSlot; i++) {
            Slot slot = menu.slots.get(i);
            if (slot.hasItem()) {
                ItemStack stack = slot.getItem();
                String itemId = AutomationUtils.getItemId(stack);
                
                if (itemId.equals(targetItemId)) {
                    BapelSlimefunMod.LOGGER.debug("[MultiblockAuto] Found '{}' at slot {} (count: {})", 
                                                 targetItemId, i, stack.getCount());
                    return i;
                }
            }
        }
        
        BapelSlimefunMod.LOGGER.debug("[MultiblockAuto] Item '{}' not found in inventory", targetItemId);
        return -1;
    }
    
    /**
     * ✅ NEW: Reset automation state when recipe changes
     */
    private static void resetAutomationState() {
        currentSlotIndex = 0;
        emptySlotCount = 0;
        allSlotsFilled = false;
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Automation state reset");
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
     * Reset handler
     */
    public static void reset() {
        selectedRecipeId = null;
        lastProcessTime = 0;
        resetAutomationState();
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Handler reset");
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
    
    /**
     * ✅ NEW: Get automation status for debugging
     */
    public static String getAutomationStatus() {
        return String.format("Slot: %d/9 | Empty: %d | Filled: %s | Recipe: %s", 
                           currentSlotIndex, emptySlotCount, allSlotsFilled, 
                           selectedRecipeId != null ? selectedRecipeId : "NONE");
    }
}