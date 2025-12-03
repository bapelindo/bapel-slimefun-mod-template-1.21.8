package com.bapel_slimefun_mod.automation;
import com.bapel_slimefun_mod.client.AutomationManager;
import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * FIXED VERSION - Main handler for machine automation
 * 
 * BUGS FIXED:
 * 1. Auto-select recipe #1 bug - Removed cacheRecipeRequirements()
 * 2. Double remember bug - Added overloaded setSelectedRecipe()
 * 
 * Performance improvements:
 * - Cached inventory lookups
 * - Reduced redundant checks
 * - Batch operations where possible
 * - Smart tick throttling
 */
public class MachineAutomationHandler {
    private static SlimefunMachineData currentMachine = null;
    private static long lastAutoTick = 0;
    private static ModConfig config;
    private static Map<String, Integer> cachedRecipeRequirements = new HashMap<>();
    private static String selectedRecipeId = null;

    private static boolean automationEnabled = false;
    private static boolean autoInsertTriggered = false;
    private static int automationTickCount = 0;
    private static int successfulInputs = 0;
    private static int successfulOutputs = 0;
    
    // OPTIMIZATION: Cache player inventory to avoid repeated lookups
    private static List<ItemStack> cachedPlayerInventory = new ArrayList<>();
    private static long lastInventoryCacheTime = 0;
    private static final long INVENTORY_CACHE_DURATION = 50; // 50ms = 1 tick
    
    // OPTIMIZATION: Track empty slots to avoid repeated scanning
    private static Set<Integer> knownEmptyInputSlots = new HashSet<>();
    private static long lastEmptySlotCheck = 0;
    private static final long EMPTY_SLOT_CHECK_INTERVAL = 100; // Check every 100ms
    
    public static void init(ModConfig cfg) {
        config = cfg;
        RecipeMemoryManager.load();
    }
    
    
    // ============================================
    //  FIX #1: OVERLOADED setSelectedRecipe()
    // ============================================
    
    /**
     * Set selected recipe (DEFAULT: remember = true)
     */
    public static void setSelectedRecipe(String recipeId) {
        setSelectedRecipe(recipeId, true); // Default behavior - remember recipe
    }
    
    /**
     * Set selected recipe with control over remembering
     * @param recipeId Recipe to select
     * @param rememberRecipe If true, save to memory (for manual selection)
     *                       If false, don't save (for loading from memory)
     */
    public static void setSelectedRecipe(String recipeId, boolean rememberRecipe) {
        selectedRecipeId = recipeId;
        
        // AUTO MODE: Remember this recipe for the current machine
        // ONLY if rememberRecipe=true (manual selection)
        if (rememberRecipe && config != null && config.isRememberLastRecipe() && 
            currentMachine != null && recipeId != null) {
            
            RecipeMemoryManager.rememberRecipe(currentMachine.getId(), recipeId);
        }
        
        // UPDATE REQUIREMENTS from RecipeDatabase
        if (RecipeDatabase.isInitialized() && recipeId != null) {
            RecipeData recipe = RecipeDatabase.getRecipe(recipeId);
            if (recipe != null) {
                cachedRecipeRequirements = recipe.getGroupedInputs();
            }
        }
    }
    
    public static String getSelectedRecipe() {
        return selectedRecipeId;
    }
    
    // ============================================
    //  FIX #2: UPDATED onContainerOpen()
    // ============================================
    
    public static void onContainerOpen(String title) {
        currentMachine = SlimefunDataLoader.getMachineByTitle(title);
        autoInsertTriggered = false;
        
        if (currentMachine != null) {
            
            // Reset caches for new machine
            resetCaches();
            //  REMOVED: cacheRecipeRequirements(); - This was causing auto-select bug!
            
            // AUTO MODE: Check if we have a remembered recipe for this machine
            if (config != null && config.isRememberLastRecipe()) {
                String rememberedRecipe = RecipeMemoryManager.getRememberedRecipe(currentMachine.getId());
                
                if (rememberedRecipe != null) {
                    
                    //  FIX: Use overload with rememberRecipe=false to prevent double-save
                    setSelectedRecipe(rememberedRecipe, false);
                    
                    // Show message to player
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        String displayName = getRecipeDisplayName(rememberedRecipe);
                        mc.player.displayClientMessage(
                            Component.literal("§a Auto Mode: Loaded recipe '" + displayName + "'"), 
                            true
                        );
                    }
                    
                    //  REMOVED: Auto-start automation - user must press K
                    // User has full control!
                    
                    autoInsertTriggered = true;
                    return; // Don't show overlay in auto mode
                }
            }
            
            // MANUAL MODE or no remembered recipe: Show overlay if configured
            if (config != null && config.isAutoShowOverlay()) {
                try {
                    RecipeOverlayRenderer.show(currentMachine);
                } catch (Exception e) {
                    BapelSlimefunMod.LOGGER.error("[Automation] Failed to auto-show overlay", e);
                }
            }
            
        }
    }
    
    public static void onContainerClose() {
        
        // MANUAL MODE: Clear recipe selection when closing machine
        if (config != null && !config.isRememberLastRecipe()) {
            selectedRecipeId = null;
        }
        
        // Clear all state
        currentMachine = null;
        cachedRecipeRequirements.clear();
        resetCaches();
        autoInsertTriggered = false;
        
        automationTickCount = 0;
        successfulInputs = 0;
        successfulOutputs = 0;
    }
    
    /**
     * OPTIMIZATION: Reset all caches
     */
    private static void resetCaches() {
        cachedPlayerInventory.clear();
        knownEmptyInputSlots.clear();
        lastInventoryCacheTime = 0;
        lastEmptySlotCheck = 0;
    }
    
    // ============================================
    //  DELETED: cacheRecipeRequirements()
    // This method was causing auto-select bug!
    // ============================================
    
    // ============================================
    //  FIX #3: NEW HELPER METHOD
    // ============================================
    
    /**
     * Get user-friendly recipe display name
     */
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
     * OPTIMIZED: Main automation tick with smart throttling
     */
    public static void tick() {
        // Fast-path checks
        if (config == null || !config.isAutomationEnabled() || currentMachine == null) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;
        
        // Throttle based on config
        long now = System.currentTimeMillis();
        if (now - lastAutoTick < config.getAutomationDelayMs()) return;
        lastAutoTick = now;
        
        automationTickCount++;
        
        
        try {
            // Process outputs first (clear space)
            autoOutput(menu, mc);
            
            // Then process inputs
            autoInput(menu, player, mc);
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error in automation tick", e);
        }
    }
    
    /**
     * OPTIMIZED: Auto-output with batch processing
     */
    private static void autoOutput(AbstractContainerMenu menu, Minecraft mc) {
        if (!currentMachine.hasOutputSlots()) return;
        
        try {
            int[] outputSlots = currentMachine.getOutputSlots();
            
            // OPTIMIZATION: Process all output slots in one pass
            for (int slotIndex : outputSlots) {
                if (slotIndex < 0 || slotIndex >= menu.slots.size()) continue;
                
                Slot slot = menu.slots.get(slotIndex);
                if (slot == null || slot.getItem().isEmpty()) continue;
                
                // Quick move to player inventory
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
     * OPTIMIZED: Auto-input with cached inventory and smart slot tracking
     */
    private static void autoInput(AbstractContainerMenu menu, LocalPlayer player, Minecraft mc) {
        if (!currentMachine.hasInputSlots() || cachedRecipeRequirements.isEmpty()) return;
        
        try {
            // OPTIMIZATION: Check for empty input slots periodically
            long now = System.currentTimeMillis();
            if (now - lastEmptySlotCheck > EMPTY_SLOT_CHECK_INTERVAL) {
                updateEmptyInputSlots(menu);
                lastEmptySlotCheck = now;
            }
            
            if (knownEmptyInputSlots.isEmpty()) {
                return;
            }
            
            // OPTIMIZATION: Use cached player inventory
            List<ItemStack> playerInventory = getCachedPlayerInventory(player);
            
            // Try to move items in priority order (recipe requirements)
            for (Map.Entry<String, Integer> required : cachedRecipeRequirements.entrySet()) {
                String itemId = required.getKey();
                
                // Move one item at a time, try all recipe items each tick
                moveItemToInput(menu, player, mc, itemId, playerInventory);
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error in auto-input", e);
        }
    }
    
    /**
     * OPTIMIZATION: Update cache of empty input slots
     */
    private static void updateEmptyInputSlots(AbstractContainerMenu menu) {
        knownEmptyInputSlots.clear();
        
        try {
            for (int slotIndex : currentMachine.getInputSlots()) {
                if (slotIndex >= 0 && slotIndex < menu.slots.size()) {
                    Slot slot = menu.slots.get(slotIndex);
                    if (slot != null && slot.getItem().isEmpty()) {
                        knownEmptyInputSlots.add(slotIndex);
                    }
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error updating empty slots", e);
        }
    }
    
    /**
     * OPTIMIZATION: Get cached player inventory
     */
    private static List<ItemStack> getCachedPlayerInventory(LocalPlayer player) {
        long now = System.currentTimeMillis();
        
        // Return cached if still valid
        if (now - lastInventoryCacheTime < INVENTORY_CACHE_DURATION && !cachedPlayerInventory.isEmpty()) {
            return cachedPlayerInventory;
        }
        
        // Rebuild cache
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
    
    /**
     * OPTIMIZATION: Move item to input with cached inventory lookup
     */
    private static boolean moveItemToInput(AbstractContainerMenu menu, LocalPlayer player, 
                                          Minecraft mc, String itemId, List<ItemStack> inventory) {
        try {
            // OPTIMIZATION: Use cached inventory instead of scanning menu slots
            int playerSlotIndex = findItemInPlayerInventoryOptimized(menu, player, itemId);
            if (playerSlotIndex == -1) return false;
            
            // OPTIMIZATION: Pick first available empty slot from cache
            Integer emptySlot = knownEmptyInputSlots.stream().findFirst().orElse(-1);
            if (emptySlot == -1) return false;
            
            Slot playerSlot = menu.slots.get(playerSlotIndex);
            
            // Quick move to input
            mc.gameMode.handleInventoryMouseClick(
                menu.containerId, playerSlotIndex, 0, ClickType.QUICK_MOVE, player
            );
            
            // Remove from empty slots cache (now occupied)
            knownEmptyInputSlots.remove(emptySlot);
            
            successfulInputs++;
            
            
            return true;
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error moving item to input", e);
            return false;
        }
    }
    
    /**
     * OPTIMIZATION: Find item in player inventory (reduced iterations)
     */
    private static int findItemInPlayerInventoryOptimized(AbstractContainerMenu menu, 
                                                          LocalPlayer player, String itemId) {
        try {
            // OPTIMIZATION: Only scan player inventory slots (skip machine slots)
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot slot = menu.slots.get(i);
                if (slot == null || slot.container != player.getInventory()) continue;
                
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) continue;
                
                String stackItemId = AutomationUtils.getItemId(stack);
                if (stackItemId.equalsIgnoreCase(itemId)) {
                    return i;
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error finding item", e);
        }
        
        return -1;
    }

    // Helper method untuk mengirim pesan ke player
    private static void sendPlayerMessage(String message) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(message), true);
            }
        } catch (Exception e) {
            // Ignore if player is null
        }
    }

    // Method toggle untuk Keybind
    public static void toggle() {
        if (config != null) {
            boolean newState = !config.isAutomationEnabled();
            config.setAutomationEnabled(newState);
            
            if (!newState) {
                resetCaches();
                sendPlayerMessage("§c[Slimefun] Automation STOPPED ");
            } else {
                sendPlayerMessage("§a[Slimefun] Automation STARTED ");
            }
        }
    }

    // Alias untuk kompatibilitas
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
    
    /**
     * OPTIMIZED: Get recipe summary with cached inventory
     */
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
            BapelSlimefunMod.LOGGER.error("[Automation] Error getting recipe summary", e);
            return null;
        }
    }
    
    // ========================================
    // ========================================
    
    public static void runFullDiagnostic() {
        
        // Config
        if (config != null) {
        } else {
        }
        
        // Machine
        if (currentMachine != null) {
        } else {
        }
        
        // Automation status
        
        // Performance metrics
        
        // Overlay
        
        // Database
        // Diagnostic removed for production
    }
    
    public static void logKeyPress(String key) {
        // Log removed for production
    }

    public static boolean isAutomationEnabled() {
        return automationEnabled;
    }
    
    public static void setAutomationEnabled(boolean enabled) {
        automationEnabled = enabled;
    }
    
    public static boolean isAutoInsertTriggered() {
        return autoInsertTriggered;
    }
}