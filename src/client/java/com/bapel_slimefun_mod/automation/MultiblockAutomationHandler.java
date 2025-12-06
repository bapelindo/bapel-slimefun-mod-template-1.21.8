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
 * ✅ COMPLETE FIX: Auto-fill now correctly calculates clicks and stops when ready
 */
public class MultiblockAutomationHandler {
    
    private static final int SEARCH_RADIUS = 5;
    private static ModConfig config;
    private static String selectedRecipeId = null;
    private static long lastProcessTime = 0;
    
    private static int currentSlotIndex = 0;
    private static int emptySlotCount = 0;
    private static boolean allSlotsFilled = false;
    
    private static int calculatedClickCount = 0;
    private static boolean hasShownReadyMessage = false;
    
    public static void init(ModConfig cfg) {
        config = cfg;
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Initialized with config");
    }
    
    /**
     * ✅ FIXED: Clear dispenser only when changing to different recipe (not when clearing)
     */
    public static void setSelectedRecipe(String recipeId) {
        // ✅ Clear dispenser only if changing from one recipe to another (not to null)
        if (recipeId != null && selectedRecipeId != null && !recipeId.equals(selectedRecipeId)) {
            clearDispenserForNewRecipe();
        }
        
        selectedRecipeId = recipeId;
        resetAutomationState();
        
        if (recipeId != null) {
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ✓ Recipe selected: {}", recipeId);
        } else {
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Recipe cleared");
        }
    }
    
    /**
     * ✅ NEW: Clear all items from dispenser when changing recipe
     */
    private static void clearDispenserForNewRecipe() {
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            
            if (player == null) return;
            
            AbstractContainerMenu menu = player.containerMenu;
            
            if (!(menu instanceof DispenserMenu)) {
                return;
            }
            
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Clearing dispenser for new recipe...");
            
            int clearedCount = 0;
            
            // Clear all 9 dispenser slots
            for (int i = 0; i < 9; i++) {
                ItemStack stack = menu.getSlot(i).getItem();
                
                if (!stack.isEmpty()) {
                    // Quick move to player inventory
                    mc.gameMode.handleInventoryMouseClick(
                        menu.containerId, i, 0, ClickType.QUICK_MOVE, player
                    );
                    clearedCount++;
                }
            }
            
            if (clearedCount > 0) {
                player.displayClientMessage(
                    Component.literal(String.format("§e⚠ Cleared %d items from dispenser", clearedCount)),
                    true
                );
                
                BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ✓ Cleared {} items", clearedCount);
            }
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[MultiblockAuto] Error clearing dispenser", e);
        }
    }
    
    public static String getSelectedRecipe() {
        return selectedRecipeId;
    }
    
    public static int getCalculatedClickCount() {
        return calculatedClickCount;
    }
    
    /**
     * ✅ FIXED: Main tick with proper completion detection
     */
    public static void tick(SlimefunMachineData machine) {
        if (machine == null || !machine.isMultiblock() || selectedRecipeId == null) {
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
        // ✅ FASTEST: 25ms delay for rapid 1-item placement
        int delayMs = 25;
        
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
     * ✅ FIXED: Auto-fill with FASTER round-robin (multiple slots per tick)
     */
    private static boolean autoFillDispenserRoundRobin(LocalPlayer player, Level level, 
                                                       BlockPos pos, RecipeData recipe) {
        if (!(player.containerMenu instanceof DispenserMenu)) {
            return false;
        }
        
        Minecraft mc = Minecraft.getInstance();
        AbstractContainerMenu menu = player.containerMenu;
        List<RecipeHandler.RecipeIngredient> inputs = recipe.getInputs();
        
        List<RecipeHandler.RecipeIngredient> paddedInputs = padInputsTo9(inputs);
        
        if (paddedInputs.size() != 9) {
            BapelSlimefunMod.LOGGER.error("[MultiblockAuto] ERROR: After padding got {} inputs", 
                paddedInputs.size());
            return false;
        }
        
        // ✅ ALWAYS calculate click count
        int previousClickCount = calculatedClickCount;
        calculatedClickCount = calculateClickCount(menu, paddedInputs);
        
        // ✅ Check if dispenser is ready (all slots filled correctly)
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
        
        // ✅ Show ready message only ONCE when dispenser becomes ready
        if (allSlotsFilled && calculatedClickCount > 0 && !hasShownReadyMessage) {
            player.displayClientMessage(
                Component.literal(String.format(
                    "§a✓ Dispenser ready! Can process §b%d §atimes",
                    calculatedClickCount
                )),
                false
            );
            
            player.displayClientMessage(
                Component.literal("§7Close dispenser to start auto-clicking"),
                true
            );
            
            BapelSlimefunMod.LOGGER.info("[MultiblockAuto] ✓ Dispenser ready! Calculated {} clicks", 
                calculatedClickCount);
            
            hasShownReadyMessage = true;
            return false; // Stop filling
        }
        
        // ✅ Reset message flag if dispenser becomes not ready
        if (!allSlotsFilled || calculatedClickCount == 0) {
            hasShownReadyMessage = false;
        }
        
        // ✅ FIXED ROUND-ROBIN: Process multiple slots per tick, evenly distributed
        int actionsThisTick = 0;
        int maxActionsPerTick = 5; // Process up to 5 different slots per tick
        
        // ✅ KEY FIX: Start from currentSlotIndex and advance it after EACH action
        // This ensures items are distributed evenly across all slots
        for (int attempt = 0; attempt < 9 && actionsThisTick < maxActionsPerTick; attempt++) {
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
                    
                    actionsThisTick++;
                    // ✅ Don't update currentSlotIndex here - let it update at the end
                    continue;
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
                    actionsThisTick++;
                    // ✅ Don't update currentSlotIndex here
                    continue;
                }
                
                // B. Find item in player inventory
                int sourceSlot = findItemInPlayerInventory(menu, player, target.getItemId());
                
                if (sourceSlot != -1) {
                    // ✅ Place 1 item at a time (no shift-click)
                    
                    // 1. Pick up stack from inventory
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, sourceSlot, 0, 
                                                         ClickType.PICKUP, player);
                    
                    // 2. Place 1 item in dispenser (Right Click = Button 1)
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, slotIndex, 1, 
                                                         ClickType.PICKUP, player);
                    
                    // 3. Return remaining items to inventory
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, sourceSlot, 0, 
                                                         ClickType.PICKUP, player);
                    
                    actionsThisTick++;
                    // ✅ Don't update currentSlotIndex here
                    continue;
                }
                
                // No items available - try next slot
                continue;
            }
        }
        
        // ✅ KEY FIX: Update currentSlotIndex by the number of actions taken
        // This ensures we continue from where we left off, creating true round-robin
        currentSlotIndex = (currentSlotIndex + actionsThisTick) % 9;
        
        return actionsThisTick > 0;
    }
    
    /**
     * ✅ FIXED: Calculate click count - works with ANY stack size
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
                // Empty slot but needs item = 0 clicks
                return 0;
            }
            
            int currentCount = currentStack.getCount();
            int requiredPerClick = target.getAmount();
            
            if (requiredPerClick <= 0) {
                continue;
            }
            
            // How many times can we process with this item count?
            int possibleClicks = currentCount / requiredPerClick;
            
            minClicks = Math.min(minClicks, possibleClicks);
            hasValidItems = true;
        }
        
        if (!hasValidItems) {
            return 0;
        }
        
        int finalClicks = minClicks == Integer.MAX_VALUE ? 0 : minClicks;
        return finalClicks;
    }
    
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
    
    private static void resetAutomationState() {
        currentSlotIndex = 0;
        emptySlotCount = 0;
        allSlotsFilled = false;
        calculatedClickCount = 0;
        hasShownReadyMessage = false;
    }
    
    private static List<ItemStack> getPlayerInventory(LocalPlayer player) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            items.add(player.getInventory().getItem(i));
        }
        return items;
    }
    
    public static void reset() {
        selectedRecipeId = null;
        lastProcessTime = 0;
        resetAutomationState();
    }
    
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
    
    public static String getAutomationStatus() {
        return String.format("Slot: %d/9 | Empty: %d | Filled: %s | Recipe: %s | Clicks: %d", 
                           currentSlotIndex, emptySlotCount, allSlotsFilled, 
                           selectedRecipeId != null ? selectedRecipeId : "NONE",
                           calculatedClickCount);
    }
}