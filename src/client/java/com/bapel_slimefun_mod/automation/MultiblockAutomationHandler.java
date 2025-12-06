package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;

import java.util.*;

/**
 * ✅ FIXED: Multiblock automation with proper click calculation
 * 
 * Bug Fixes:
 * 1. Auto-click works with partial fills (not just full stacks)
 * 2. Click calculation happens every tick (not just when full)
 * 3. User can close dispenser anytime and auto-click will start
 */
public class MultiblockAutomationHandler {
    
    private static final int SEARCH_RADIUS = 5;
    private static ModConfig config;
    private static String selectedRecipeId = null;
    private static long lastProcessTime = 0;
    
    private static int currentSlotIndex = 0;
    private static int emptySlotCount = 0;
    private static boolean allSlotsFilled = false;
    
    // ✅ KALKULASI CLICK COUNT untuk auto-stopper
    private static int calculatedClickCount = 0;
    
    public static void init(ModConfig cfg) {
        config = cfg;
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Initialized with config");
    }
    
    /**
     * EVENT: Recipe selected - aktifkan auto-fill
     */
    public static void setSelectedRecipe(String recipeId) {
        selectedRecipeId = recipeId;
        resetAutomationState();
        
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
     * ✅ GET CALCULATED CLICK COUNT untuk auto-clicker
     */
    public static int getCalculatedClickCount() {
        return calculatedClickCount;
    }
    
    /**
     * Main tick - auto-fill dengan round-robin
     */
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
            BapelSlimefunMod.LOGGER.warn("[MultiblockAuto] ✗ Recipe not found in database: {}", selectedRecipeId);
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
     * ✅ AUTO-FILL dengan ROUND-ROBIN + KALKULASI CLICK COUNT
     * 🆕 FIXED: Calculate click count every tick (not just when full)
     */
    private static boolean autoFillDispenserRoundRobin(LocalPlayer player, Level level, 
                                                       BlockPos pos, RecipeData recipe) {
        if (!(player.containerMenu instanceof DispenserMenu)) {
            return false;
        }
        
        Minecraft mc = Minecraft.getInstance();
        AbstractContainerMenu menu = player.containerMenu;
        List<RecipeHandler.RecipeIngredient> inputs = recipe.getInputs();
        
        // Pad inputs to 9
        List<RecipeHandler.RecipeIngredient> paddedInputs = padInputsTo9(inputs);
        
        if (paddedInputs.size() != 9) {
            BapelSlimefunMod.LOGGER.error("[MultiblockAuto] ERROR: After padding got {} inputs", 
                paddedInputs.size());
            return false;
        }
        
        // 🆕 ALWAYS calculate click count (even if not full)
        calculatedClickCount = calculateClickCount(menu, paddedInputs);
        
        // Count how many slots still need work
        emptySlotCount = 0;
        allSlotsFilled = true;
        
        for (int i = 0; i < 9; i++) {
            RecipeHandler.RecipeIngredient target = paddedInputs.get(i);
            ItemStack currentStack = menu.getSlot(i).getItem();
            
            boolean needsWork = needsWorkOnSlot(currentStack, target);
            
            if (needsWork) {
                emptySlotCount++;
                allSlotsFilled = false;
            }
        }
        
        // ✅ Show message when dispenser is ready (has at least 1 click)
        if (allSlotsFilled && calculatedClickCount > 0) {
            player.displayClientMessage(
                Component.literal(String.format(
                    "§a✓ Dispenser ready! Can process §b%d times",
                    calculatedClickCount
                )),
                true
            );
            
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ✓ Calculated {} clicks needed", 
                calculatedClickCount);
            
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
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, slotIndex, 0, 
                                                         ClickType.QUICK_MOVE, player);
                    
                    currentSlotIndex = (slotIndex + 1) % 9;
                    return true;
                }
                continue; // Already correct (empty)
            }
            
            // CASE 2: Slot needs a specific ITEM
            boolean isSameItem = !currentStack.isEmpty() && currentId.equals(target.getItemId());
            boolean needsRefill = currentStack.isEmpty() || 
                                 (isSameItem && currentStack.getCount() < currentStack.getMaxStackSize());
            
            if (needsRefill) {
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
     * ✅ KALKULASI: Berapa kali bisa click berdasarkan item di dispenser
     * 🆕 FIXED: Work with partial stacks (not just full 64)
     * 
     * Contoh:
     * - Recipe: 3 copper, 3 copper, 4 copper (total 10 per proses)
     * - Dispenser: slot 0 = 30, slot 1 = 30, slot 2 = 40
     * - Hasil: min(30/3, 30/3, 40/4) = min(10, 10, 10) = 10 clicks
     * 
     * 🆕 PARTIAL EXAMPLE:
     * - Recipe: 3 copper, 3 copper, 4 copper
     * - Dispenser: slot 0 = 15, slot 1 = 15, slot 2 = 20
     * - Hasil: min(15/3, 15/3, 20/4) = min(5, 5, 5) = 5 clicks
     */
    private static int calculateClickCount(AbstractContainerMenu menu, 
                                           List<RecipeHandler.RecipeIngredient> paddedInputs) {
        int minClicks = Integer.MAX_VALUE;
        boolean hasValidItems = false;
        
        for (int i = 0; i < 9; i++) {
            RecipeHandler.RecipeIngredient target = paddedInputs.get(i);
            
            // Skip AIR slots
            if (target.getItemId().equals("AIR") || target.getAmount() == 0) {
                continue;
            }
            
            ItemStack currentStack = menu.getSlot(i).getItem();
            
            if (currentStack.isEmpty()) {
                // Slot kosong tapi butuh item = 0 clicks
                return 0;
            }
            
            int currentCount = currentStack.getCount();
            int requiredPerClick = target.getAmount();
            
            if (requiredPerClick <= 0) {
                continue;
            }
            
            // Berapa kali bisa proses dengan jumlah item ini?
            int possibleClicks = currentCount / requiredPerClick;
            
            minClicks = Math.min(minClicks, possibleClicks);
            hasValidItems = true;
            
            BapelSlimefunMod.LOGGER.debug("[ClickCalc] Slot {}: {} items / {} per click = {} clicks", 
                i, currentCount, requiredPerClick, possibleClicks);
        }
        
        if (!hasValidItems) {
            return 0;
        }
        
        int finalClicks = minClicks == Integer.MAX_VALUE ? 0 : minClicks;
        BapelSlimefunMod.LOGGER.info("[ClickCalc] ✓ Final calculation: {} clicks possible", finalClicks);
        return finalClicks;
    }
    
    /**
     * Pad inputs to exactly 9 slots
     */
    private static List<RecipeHandler.RecipeIngredient> padInputsTo9(List<RecipeHandler.RecipeIngredient> inputs) {
        List<RecipeHandler.RecipeIngredient> padded = new ArrayList<>(inputs);
        
        if (padded.size() == 9) {
            return padded;
        }
        
        if (padded.size() > 9) {
            BapelSlimefunMod.LOGGER.warn("[MultiblockAuto] Recipe has {} inputs, trimming to 9", 
                padded.size());
            return new ArrayList<>(padded.subList(0, 9));
        }
        
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
     * Find item in player inventory slots (9-44 for Dispenser GUI)
     */
    private static int findItemInPlayerInventory(AbstractContainerMenu menu, LocalPlayer player, 
                                                 String targetItemId) {
        int startSlot = 9; // Skip dispenser slots (0-8)
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
    
    /**
     * Reset automation state when recipe changes
     */
    private static void resetAutomationState() {
        currentSlotIndex = 0;
        emptySlotCount = 0;
        allSlotsFilled = false;
        calculatedClickCount = 0;
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
        return String.format("Slot: %d/9 | Empty: %d | Filled: %s | Recipe: %s | Clicks: %d", 
                           currentSlotIndex, emptySlotCount, allSlotsFilled, 
                           selectedRecipeId != null ? selectedRecipeId : "NONE",
                           calculatedClickCount);
    }
}