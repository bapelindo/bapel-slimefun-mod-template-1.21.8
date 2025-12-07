package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.client.AutomationManager;
import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;
import com.bapel_slimefun_mod.debug.PerformanceMonitor;

/**
 * ✅ ULTRA OPTIMIZED VERSION
 * 
 * KEY OPTIMIZATIONS:
 * 1. Smart tick throttling (dynamic delays)
 * 2. Cached inventory with 200ms duration
 * 3. Batch slot processing (multiple per tick)
 * 4. Early exit patterns
 * 5. Reduced HashMap operations
 * 6. Pre-validated slot indices
 */
public class MachineAutomationHandler {
    private static SlimefunMachineData currentMachine = null;
    private static long lastAutoTick = 0;
    private static ModConfig config;
    private static Map<String, Integer> cachedRecipeRequirements = new HashMap<>();
    private static String selectedRecipeId = null;

    private static boolean automationEnabled = false;
    private static int successfulInputs = 0;
    private static int successfulOutputs = 0;
    
    // ✅ OPTIMIZATION: Longer cache duration (200ms instead of 50ms)
    private static List<ItemStack> cachedPlayerInventory = new ArrayList<>();
    private static long lastInventoryCacheTime = 0;
    private static final long INVENTORY_CACHE_DURATION = 200;
    
    // ✅ OPTIMIZATION: Smarter empty slot tracking
    private static Set<Integer> knownEmptyInputSlots = new HashSet<>();
    private static long lastEmptySlotCheck = 0;
    private static final long EMPTY_SLOT_CHECK_INTERVAL = 200; // Increased from 100ms
    
    // ✅ OPTIMIZATION: Pre-validated slot indices
    private static int[] validInputSlots = new int[0];
    private static int[] validOutputSlots = new int[0];
    
    // Machine change tracking
    private static String lastMachineId = null;
    
    public static void init(ModConfig cfg) {
        config = cfg;
        RecipeMemoryManager.load();
    }
    
    public static void setSelectedRecipe(String recipeId) {
        setSelectedRecipe(recipeId, true);
    }
    
public static void setSelectedRecipe(String recipeId, boolean rememberRecipe) {
    selectedRecipeId = recipeId;
    
    if (rememberRecipe && config != null && config.isRememberLastRecipe() && 
        currentMachine != null && recipeId != null) {
        
        RecipeMemoryManager.rememberRecipe(currentMachine.getId(), recipeId);
    }
    
    if (RecipeDatabase.isInitialized() && recipeId != null) {
        RecipeData recipe = RecipeDatabase.getRecipe(recipeId);
        if (recipe != null) {
            cachedRecipeRequirements = recipe.getGroupedInputs();
            
            // ✅ CRITICAL FIX: Auto-enable automation when recipe is selected
            automationEnabled = true;
        }
    }
}
    
    public static String getSelectedRecipe() {
        return selectedRecipeId;
    }
    
    /**
     * ✅ OPTIMIZED: Clear recipe on machine change
     */
    public static void onContainerOpen(String title) {
        currentMachine = SlimefunDataLoader.getMachineByTitle(title);
        
        if (currentMachine != null) {
            
            // Check if different machine
            boolean isDifferentMachine = lastMachineId != null && 
                                        !lastMachineId.equals(currentMachine.getId());
            
            if (isDifferentMachine) {
                selectedRecipeId = null;
                cachedRecipeRequirements.clear();
                
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                        Component.literal("§e⚠ Different machine - recipe cleared"), 
                        true
                    );
                }
            }
            
            lastMachineId = currentMachine.getId();
            
            // ✅ OPTIMIZATION: Pre-validate slot indices
            validateSlotIndices();
            
            resetCaches();
            
            // AUTO MODE
            if (config != null && config.isRememberLastRecipe()) {
                String rememberedRecipe = RecipeMemoryManager.getRememberedRecipe(currentMachine.getId());
                
                if (rememberedRecipe != null) {
                    setSelectedRecipe(rememberedRecipe, false);
                    
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        String displayName = getRecipeDisplayName(rememberedRecipe);
                        mc.player.displayClientMessage(
                            Component.literal("§a✓ Auto Mode: Loaded recipe '" + displayName + "'"), 
                            true
                        );
                    }
                    
                    return;
                }
            }
            
            // MANUAL MODE
            if (config != null && config.isAutoShowOverlay()) {
                try {
                    RecipeOverlayRenderer.show(currentMachine);
                } catch (Exception e) {
                    BapelSlimefunMod.LOGGER.error("[Automation] Failed to auto-show overlay", e);
                }
            }
            
        } else {
            lastMachineId = null;
        }
    }
    
    /**
     * ✅ NEW: Pre-validate slot indices to avoid bounds checking
     */
    private static void validateSlotIndices() {
        if (currentMachine == null) {
            validInputSlots = new int[0];
            validOutputSlots = new int[0];
            return;
        }
        
        // Validate input slots
        int[] rawInput = currentMachine.getInputSlots();
        List<Integer> validInput = new ArrayList<>();
        for (int slot : rawInput) {
            if (slot >= 0) { // We'll check upper bound at runtime
                validInput.add(slot);
            }
        }
        validInputSlots = validInput.stream().mapToInt(i -> i).toArray();
        
        // Validate output slots
        int[] rawOutput = currentMachine.getOutputSlots();
        List<Integer> validOutput = new ArrayList<>();
        for (int slot : rawOutput) {
            if (slot >= 0) {
                validOutput.add(slot);
            }
        }
        validOutputSlots = validOutput.stream().mapToInt(i -> i).toArray();
    }
    
    public static void onContainerClose() {
        if (config != null && !config.isRememberLastRecipe()) {
            selectedRecipeId = null;
        }
        
        currentMachine = null;
        cachedRecipeRequirements.clear();
        resetCaches();
        
        successfulInputs = 0;
        successfulOutputs = 0;
    }
    
    private static void resetCaches() {
        cachedPlayerInventory.clear();
        knownEmptyInputSlots.clear();
        lastInventoryCacheTime = 0;
        lastEmptySlotCheck = 0;
    }
    
    private static String getRecipeDisplayName(String recipeId) {
        if (recipeId == null) return "Unknown";
        
        try {
            RecipeData recipe = RecipeDatabase.getRecipe(recipeId);
            if (recipe != null && recipe.getPrimaryOutput() != null) {
                return recipe.getPrimaryOutput().getDisplayName();
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to get recipe display name", e);
        }
        
        return recipeId;
    }
    
    /**
     * ✅ ULTRA OPTIMIZED: Smart throttling + batch processing
     */
    public static void tick() {
        PerformanceMonitor.start("MachineAuto.tick");
        try {
            // ✅ FAST PATH: Early exits
            if (config == null || !automationEnabled || currentMachine == null) {
                return;
            }
            
            if (selectedRecipeId == null || cachedRecipeRequirements.isEmpty()) {
                return;
            }
            
            if (!isRecipeValidForCurrentMachine()) {
                selectedRecipeId = null;
                cachedRecipeRequirements.clear();
                return;
            }
            
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return;
            
            AbstractContainerMenu menu = player.containerMenu;
            if (menu == null) return;
            
            long now = System.currentTimeMillis();
            
            // ✅ OPTIMIZATION: Dynamic delay based on success rate
            long delay = config.getAutomationDelayMs();
            if (successfulInputs == 0 && successfulOutputs == 0) {
                delay = Math.min(delay * 2, 500); // Slow down if nothing happening
            }
            
            if (now - lastAutoTick < delay) return;
            lastAutoTick = now;
            
            try {
                // ✅ Process output first (higher priority)
                autoOutput(menu, mc);
                
                // ✅ Then process input
                autoInput(menu, player, mc);
            } catch (Exception e) {
                BapelSlimefunMod.LOGGER.error("[Automation] Error in automation tick", e);
            }
        } finally {
            PerformanceMonitor.end("MachineAuto.tick");
        }
    }
    
    private static boolean isRecipeValidForCurrentMachine() {
        if (selectedRecipeId == null || currentMachine == null) {
            return false;
        }
        
        try {
            RecipeData recipe = RecipeDatabase.getRecipe(selectedRecipeId);
            if (recipe == null) {
                return false;
            }
            
            String recipeMachineId = recipe.getMachineId();
            String currentMachineId = currentMachine.getId();
            
            return recipeMachineId.equals(currentMachineId);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * ✅ OPTIMIZED: Batch output processing
     */
    private static void autoOutput(AbstractContainerMenu menu, Minecraft mc) {
        if (validOutputSlots.length == 0) return;
        
        try {
            int menuSize = menu.slots.size();
            
            // ✅ Process multiple output slots per tick
            for (int slotIndex : validOutputSlots) {
                if (slotIndex >= menuSize) continue;
                
                Slot slot = menu.slots.get(slotIndex);
                if (slot == null || slot.getItem().isEmpty()) continue;
                
                mc.gameMode.handleInventoryMouseClick(
                    menu.containerId, slotIndex, 0, ClickType.QUICK_MOVE, mc.player
                );
                
                successfulOutputs++;
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error in auto-output", e);
        }
    }
    
    /**
     * ✅ OPTIMIZED: Cached inventory + batch input
     */
    private static void autoInput(AbstractContainerMenu menu, LocalPlayer player, Minecraft mc) {
        if (validInputSlots.length == 0 || cachedRecipeRequirements.isEmpty()) return;
        
        try {
            long now = System.currentTimeMillis();
            
            // ✅ Update empty slots less frequently
            if (now - lastEmptySlotCheck > EMPTY_SLOT_CHECK_INTERVAL) {
                updateEmptyInputSlots(menu);
                lastEmptySlotCheck = now;
            }
            
            if (knownEmptyInputSlots.isEmpty()) {
                return;
            }
            
            // ✅ Use cached inventory
            List<ItemStack> playerInventory = getCachedPlayerInventory(player);
            
            // ✅ Process multiple items per tick
            int processed = 0;
            for (Map.Entry<String, Integer> required : cachedRecipeRequirements.entrySet()) {
                if (processed >= 3) break; // Limit to 3 items per tick
                
                String itemId = required.getKey();
                if (moveItemToInput(menu, player, mc, itemId, playerInventory)) {
                    processed++;
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error in auto-input", e);
        }
    }
    
    private static void updateEmptyInputSlots(AbstractContainerMenu menu) {
        knownEmptyInputSlots.clear();
        
        try {
            int menuSize = menu.slots.size();
            
            for (int slotIndex : validInputSlots) {
                if (slotIndex >= menuSize) continue;
                
                Slot slot = menu.slots.get(slotIndex);
                if (slot != null && slot.getItem().isEmpty()) {
                    knownEmptyInputSlots.add(slotIndex);
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error updating empty slots", e);
        }
    }
    
    /**
     * ✅ OPTIMIZED: Longer cache duration (200ms)
     */
    private static List<ItemStack> getCachedPlayerInventory(LocalPlayer player) {
        long now = System.currentTimeMillis();
        
        if (now - lastInventoryCacheTime < INVENTORY_CACHE_DURATION && !cachedPlayerInventory.isEmpty()) {
            return cachedPlayerInventory;
        }
        
        cachedPlayerInventory.clear();
        try {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    cachedPlayerInventory.add(stack);
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error caching player inventory", e);
        }
        
        lastInventoryCacheTime = now;
        return cachedPlayerInventory;
    }
    
    private static boolean moveItemToInput(AbstractContainerMenu menu, LocalPlayer player, 
                                          Minecraft mc, String itemId, List<ItemStack> inventory) {
        try {
            int playerSlotIndex = findItemInPlayerInventoryOptimized(menu, player, itemId);
            if (playerSlotIndex == -1) return false;
            
            Integer emptySlot = knownEmptyInputSlots.stream().findFirst().orElse(-1);
            if (emptySlot == -1) return false;
            
            mc.gameMode.handleInventoryMouseClick(
                menu.containerId, playerSlotIndex, 0, ClickType.QUICK_MOVE, player
            );
            
            knownEmptyInputSlots.remove(emptySlot);
            successfulInputs++;
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * ✅ OPTIMIZED: Early exit on first match
     */
    private static int findItemInPlayerInventoryOptimized(AbstractContainerMenu menu, 
                                                          LocalPlayer player, String itemId) {
        try {
            int menuSize = menu.slots.size();
            
            for (int i = 0; i < menuSize; i++) {
                Slot slot = menu.slots.get(i);
                if (slot == null || slot.container != player.getInventory()) continue;
                
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) continue;
                
                String stackItemId = AutomationUtils.getItemId(stack);
                if (stackItemId.equalsIgnoreCase(itemId)) {
                    return i; // ✅ Early exit
                }
            }
        } catch (Exception e) {
            // Silent fail
        }
        
        return -1;
    }

    private static void sendPlayerMessage(String message) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(message), true);
            }
        } catch (Exception ignored) {}
    }

    public static void toggle() {
        if (config != null) {
            automationEnabled = !automationEnabled;
            config.setAutomationEnabled(automationEnabled);
            
            if (!automationEnabled) {
                resetCaches();
                sendPlayerMessage("§c[Slimefun] Automation STOPPED ■");
            } else {
                sendPlayerMessage("§a[Slimefun] Automation STARTED ▶");
            }
        }
    }

    public static void toggleAutomation() {
        toggle();
    }
    
    public static boolean isActive() {
        return currentMachine != null;
    }
    
    public static SlimefunMachineData getCurrentMachine() {
        return currentMachine;
    }
    
    public static Map<String, Integer> getCachedRecipeRequirements() {
        return new HashMap<>(cachedRecipeRequirements);
    }
    
    public static RecipeHandler.RecipeSummary getRecipeSummary() {
        if (currentMachine == null) return null;
        
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return null;
            
            List<ItemStack> inventory = getCachedPlayerInventory(player);
            List<RecipeHandler.RecipeIngredient> recipe = 
                RecipeHandler.parseRecipe(currentMachine.getRecipe());
            
            return new RecipeHandler.RecipeSummary(inventory, recipe);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isAutomationEnabled() {
        return automationEnabled;
    }
    
    public static void setAutomationEnabled(boolean enabled) {
        automationEnabled = enabled;
    }
}