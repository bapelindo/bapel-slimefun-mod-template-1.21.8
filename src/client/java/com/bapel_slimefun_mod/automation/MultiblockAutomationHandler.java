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
 * ✅ COMPLETE FIX: Clear dispenser when changing recipes + validate recipe before filling
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
    
    // ✅ NEW: Track current machine ID to detect machine changes
    private static String currentMachineId = null;
    
    public static void init(ModConfig cfg) {
        config = cfg;
        BapelSlimefunMod.LOGGER.info("[MultiblockAuto] Initialized with config");
    }
    
    /**
     * ✅ CRITICAL FIX: Validate + don't clear if opening SAME machine with SAME recipe
     */
    public static void setSelectedRecipe(String recipeId) {
        // Get current machine from UnifiedAutomationManager
        SlimefunMachineData currentMachine = UnifiedAutomationManager.getCurrentMachine();
        
        if (recipeId != null && currentMachine != null) {
            // Validate recipe belongs to this machine
            RecipeData recipe = RecipeDatabase.getRecipe(recipeId);
            if (recipe != null) {
                String recipeMachineId = recipe.getMachineId();
                String machineId = currentMachine.getId();
                
                if (!recipeMachineId.equals(machineId)) {
                    BapelSlimefunMod.LOGGER.error(
                        "[MultiblockAuto] ❌ BLOCKED: Recipe {} belongs to {}, not {}",
                        recipeId, recipeMachineId, machineId
                    );
                    
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.displayClientMessage(
                            Component.literal("§c✗ Recipe does not belong to this machine!"),
                            true
                        );
                    }
                    return; // Don't set invalid recipe
                }
            }
        }
        
        // ✅ FIX: Detect if this is the SAME recipe being re-selected
        boolean isSameRecipe = recipeId != null && 
                               selectedRecipeId != null && 
                               recipeId.equals(selectedRecipeId);
        
        // ✅ FIX: Only clear dispenser if CHANGING to DIFFERENT recipe
        boolean isChangingRecipe = recipeId != null && 
                                    selectedRecipeId != null && 
                                    !recipeId.equals(selectedRecipeId);
        
        if (isChangingRecipe) {
            clearDispenserForNewRecipe();
            resetAutomationState(); // Reset only when changing
        }
        
        // ✅ Update current machine ID
        if (currentMachine != null) {
            currentMachineId = currentMachine.getId();
        }
        
        selectedRecipeId = recipeId;
        
        // ✅ CRITICAL: Don't reset if re-selecting same recipe (allows auto-fill to continue)
        if (recipeId == null) {
            resetAutomationState(); // Only reset when clearing
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
            
            int clearedCount = 0;
            
            // Clear all 9 dispenser slots
            for (int i = 0; i < 9; i++) {
                ItemStack stack = menu.getSlot(i).getItem();
                
                if (!stack.isEmpty()) {
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
     * ✅ OPTIMIZED: Fast validation without logging spam
     */
    public static void tick(SlimefunMachineData machine) {
        if (machine == null || !machine.isMultiblock() || selectedRecipeId == null) {
            return;
        }
        
        // ✅ FAST CHECK: Only compare machine IDs
        if (currentMachineId != null && !currentMachineId.equals(machine.getId())) {
            selectedRecipeId = null;
            currentMachineId = null;
            resetAutomationState();
            return;
        }
        
        RecipeData recipe = RecipeDatabase.getRecipe(selectedRecipeId);
        if (recipe == null) {
            selectedRecipeId = null;
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
        int delayMs = 25;
        
        if (timeSinceLastProcess < delayMs) {
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
        int maxActionsPerTick = 5;
        
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
                    continue;
                }
                continue;
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
                    continue;
                }
                
                // B. Find item in player inventory
                int sourceSlot = findItemInPlayerInventory(menu, player, target.getItemId());
                
                if (sourceSlot != -1) {
                    // Place 1 item at a time
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, sourceSlot, 0, 
                                                         ClickType.PICKUP, player);
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, slotIndex, 1, 
                                                         ClickType.PICKUP, player);
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, sourceSlot, 0, 
                                                         ClickType.PICKUP, player);
                    
                    actionsThisTick++;
                    continue;
                }
                
                continue;
            }
        }
        
        currentSlotIndex = (currentSlotIndex + actionsThisTick) % 9;
        
        return actionsThisTick > 0;
    }
    
    /**
     * ✅ Calculate click count
     */
    private static int calculateClickCount(AbstractContainerMenu menu, 
                                           List<RecipeHandler.RecipeIngredient> paddedInputs) {
        int minClicks = Integer.MAX_VALUE;
        boolean hasValidItems = false;
        
        for (int i = 0; i < 9; i++) {
            RecipeHandler.RecipeIngredient target = paddedInputs.get(i);
            
            if (target.getItemId().equals("AIR") || target.getAmount() == 0) {
                continue;
            }
            
            ItemStack currentStack = menu.getSlot(i).getItem();
            
            if (currentStack.isEmpty()) {
                return 0;
            }
            
            int currentCount = currentStack.getCount();
            int requiredPerClick = target.getAmount();
            
            if (requiredPerClick <= 0) {
                continue;
            }
            
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
            return new ArrayList<>(padded.subList(0, 9));
        }
        
        while (padded.size() < 9) {
            padded.add(new RecipeHandler.RecipeIngredient("AIR", 0));
        }
        
        return padded;
    }
    
    private static boolean needsWorkOnSlot(ItemStack currentStack, RecipeHandler.RecipeIngredient target) {
        String currentId = AutomationUtils.getItemId(currentStack);
        
        if ((target.getItemId().equals("AIR") || target.getAmount() == 0) && !currentStack.isEmpty()) {
            return true;
        }
        
        if (!target.getItemId().equals("AIR") && target.getAmount() > 0) {
            if (currentStack.isEmpty()) {
                return true;
            }
            
            if (!currentId.equals(target.getItemId())) {
                return true;
            }
            
            if (currentStack.getCount() < currentStack.getMaxStackSize()) {
                return true;
            }
        }
        
        return false;
    }
    
    private static int findItemInPlayerInventory(AbstractContainerMenu menu, LocalPlayer player, 
                                                 String targetItemId) {
        int startSlot = 9;
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
    
    public static void reset() {
        selectedRecipeId = null;
        currentMachineId = null;
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
            
            List<ItemStack> inventory = new ArrayList<>();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                inventory.add(player.getInventory().getItem(i));
            }
            
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