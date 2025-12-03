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
    private static boolean debugMode = false;
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
        BapelSlimefunMod.LOGGER.info("[Automation] Handler initialized");
    }
    
    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
        BapelSlimefunMod.LOGGER.info("[Automation] Debug mode: {}", enabled ? "ENABLED" : "DISABLED");
    }
    
    // ============================================
    // âœ… FIX #1: OVERLOADED setSelectedRecipe()
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
        BapelSlimefunMod.LOGGER.info("[Automation] Selected recipe: {}", recipeId);
        
        // AUTO MODE: Remember this recipe for the current machine
        // ONLY if rememberRecipe=true (manual selection)
        if (rememberRecipe && config != null && config.isRememberLastRecipe() && 
            currentMachine != null && recipeId != null) {
            
            RecipeMemoryManager.rememberRecipe(currentMachine.getId(), recipeId);
            BapelSlimefunMod.LOGGER.info("[Automation] Auto Mode: Remembered recipe '{}' for machine '{}'", 
                recipeId, currentMachine.getId());
        }
        
        // UPDATE REQUIREMENTS from RecipeDatabase
        if (RecipeDatabase.isInitialized() && recipeId != null) {
            RecipeData recipe = RecipeDatabase.getRecipe(recipeId);
            if (recipe != null) {
                cachedRecipeRequirements = recipe.getGroupedInputs();
                BapelSlimefunMod.LOGGER.info("[Automation] Updated recipe requirements: {}", 
                    cachedRecipeRequirements);
            }
        }
    }
    
    public static String getSelectedRecipe() {
        return selectedRecipeId;
    }
    
    // ============================================
    // âœ… FIX #2: UPDATED onContainerOpen()
    // ============================================
    
    public static void onContainerOpen(String title) {
        currentMachine = SlimefunDataLoader.getMachineByTitle(title);
        autoInsertTriggered = false;
        
        if (currentMachine != null) {
            BapelSlimefunMod.LOGGER.info("[Automation] Detected machine: {} (ID: {})", 
                currentMachine.getName(), currentMachine.getId());
            
            // Reset caches for new machine
            resetCaches();
            // âŒ REMOVED: cacheRecipeRequirements(); - This was causing auto-select bug!
            
            // AUTO MODE: Check if we have a remembered recipe for this machine
            if (config != null && config.isRememberLastRecipe()) {
                String rememberedRecipe = RecipeMemoryManager.getRememberedRecipe(currentMachine.getId());
                
                if (rememberedRecipe != null) {
                    BapelSlimefunMod.LOGGER.info("[Automation] Auto Mode: Found remembered recipe '{}' for machine '{}'", 
                        rememberedRecipe, currentMachine.getId());
                    
                    // âœ… FIX: Use overload with rememberRecipe=false to prevent double-save
                    setSelectedRecipe(rememberedRecipe, false);
                    
                    // Show message to player
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        String displayName = getRecipeDisplayName(rememberedRecipe);
                        mc.player.displayClientMessage(
                            Component.literal("Â§aâœ“ Auto Mode: Loaded recipe '" + displayName + "'"), 
                            true
                        );
                    }
                    
                    // âŒ REMOVED: Auto-start automation - user must press K
                    // User has full control!
                    
                    autoInsertTriggered = true;
                    return; // Don't show overlay in auto mode
                }
            }
            
            // MANUAL MODE or no remembered recipe: Show overlay if configured
            if (config != null && config.isAutoShowOverlay()) {
                try {
                    BapelSlimefunMod.LOGGER.info("[Automation] Auto-showing recipe overlay");
                    RecipeOverlayRenderer.show(currentMachine);
                } catch (Exception e) {
                    BapelSlimefunMod.LOGGER.error("[Automation] Failed to auto-show overlay", e);
                }
            }
            
            if (debugMode) {
                logRecipeInfo();
            }
        }
    }
    
    public static void onContainerClose() {
        if (currentMachine != null && debugMode) {
            BapelSlimefunMod.LOGGER.info("[Automation] Machine closed: {} - Stats: Inputs={}, Outputs={}, Ticks={}", 
                currentMachine.getName(), successfulInputs, successfulOutputs, automationTickCount);
        }
        
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
    // âŒ DELETED: cacheRecipeRequirements()
    // This method was causing auto-select bug!
    // ============================================
    
    private static void logRecipeInfo() {
        if (currentMachine == null) return;
        
        List<String> recipe = currentMachine.getRecipe();
        if (recipe.isEmpty()) return;
        
        List<RecipeHandler.RecipeIngredient> ingredients = RecipeHandler.parseRecipe(recipe);
        Set<String> uniqueItems = RecipeHandler.getUniqueItems(ingredients);
        
        BapelSlimefunMod.LOGGER.debug("[Automation] Recipe: {} ingredients, {} unique items", 
            ingredients.size(), uniqueItems.size());
    }
    
    // ============================================
    // âœ… FIX #3: NEW HELPER METHOD
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
        
        if (debugMode && automationTickCount == 1) {
            BapelSlimefunMod.LOGGER.info("â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—");
            BapelSlimefunMod.LOGGER.info("â•‘  AUTOMATION STARTED                    â•‘");
            BapelSlimefunMod.LOGGER.info("â•‘  Machine: {}", currentMachine.getName());
            BapelSlimefunMod.LOGGER.info("â•‘  Delay: {}ms", config.getAutomationDelayMs());
            BapelSlimefunMod.LOGGER.info("â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
        }
        
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
                
                ItemStack stack = slot.getItem();
                String itemId = AutomationUtils.getItemId(stack);
                int count = stack.getCount();
                
                // Quick move to player inventory
                mc.gameMode.handleInventoryMouseClick(
                    menu.containerId, slotIndex, 0, ClickType.QUICK_MOVE, mc.player
                );
                
                successfulOutputs++;
                
                if (debugMode) {
                    BapelSlimefunMod.LOGGER.info("[Automation] Auto-output: {} x{}", itemId, count);
                }
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
                if (debugMode && automationTickCount % 100 == 1) {
                    BapelSlimefunMod.LOGGER.debug("[Automation] No empty input slots");
                }
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
            ItemStack stack = playerSlot.getItem();
            int count = stack.getCount();
            
            // Quick move to input
            mc.gameMode.handleInventoryMouseClick(
                menu.containerId, playerSlotIndex, 0, ClickType.QUICK_MOVE, player
            );
            
            // Remove from empty slots cache (now occupied)
            knownEmptyInputSlots.remove(emptySlot);
            
            successfulInputs++;
            
            if (debugMode) {
                BapelSlimefunMod.LOGGER.info("[Automation] Auto-input: {} x{} â†’ slot {}", 
                    itemId, count, emptySlot);
            }
            
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
                sendPlayerMessage("Â§c[Slimefun] Automation STOPPED â¹");
                BapelSlimefunMod.LOGGER.info("[Automation] Stopped manually");
            } else {
                sendPlayerMessage("Â§a[Slimefun] Automation STARTED â–¶");
                BapelSlimefunMod.LOGGER.info("[Automation] Started manually");
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
    // DEBUG DIAGNOSTIC METHODS
    // ========================================
    
    public static void runFullDiagnostic() {
        BapelSlimefunMod.LOGGER.info("â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—");
        BapelSlimefunMod.LOGGER.info("â•‘                   FULL DIAGNOSTIC                       â•‘");
        BapelSlimefunMod.LOGGER.info("â• â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•£");
        
        // Config
        BapelSlimefunMod.LOGGER.info("â•‘ CONFIG:");
        if (config != null) {
            BapelSlimefunMod.LOGGER.info("â•‘   Automation: {}", config.isAutomationEnabled());
            BapelSlimefunMod.LOGGER.info("â•‘   Delay: {}ms", config.getAutomationDelayMs());
            BapelSlimefunMod.LOGGER.info("â•‘   Debug: {}", config.isDebugMode());
        } else {
            BapelSlimefunMod.LOGGER.info("â•‘   NULL");
        }
        
        // Machine
        BapelSlimefunMod.LOGGER.info("â•‘");
        BapelSlimefunMod.LOGGER.info("â•‘ MACHINE:");
        if (currentMachine != null) {
            BapelSlimefunMod.LOGGER.info("â•‘   Name: {}", currentMachine.getName());
            BapelSlimefunMod.LOGGER.info("â•‘   ID: {}", currentMachine.getId());
            BapelSlimefunMod.LOGGER.info("â•‘   Input Slots: {}", currentMachine.getInputSlots().length);
            BapelSlimefunMod.LOGGER.info("â•‘   Output Slots: {}", currentMachine.getOutputSlots().length);
        } else {
            BapelSlimefunMod.LOGGER.info("â•‘   NULL");
        }
        
        // Automation status
        BapelSlimefunMod.LOGGER.info("â•‘");
        BapelSlimefunMod.LOGGER.info("â•‘ AUTOMATION:");
        BapelSlimefunMod.LOGGER.info("â•‘   Active: {}", isActive());
        BapelSlimefunMod.LOGGER.info("â•‘   Enabled: {}", isAutomationEnabled());
        BapelSlimefunMod.LOGGER.info("â•‘   Selected Recipe: {}", 
            selectedRecipeId != null ? selectedRecipeId : "none");
        
        // Performance metrics
        BapelSlimefunMod.LOGGER.info("â•‘");
        BapelSlimefunMod.LOGGER.info("â•‘ PERFORMANCE:");
        BapelSlimefunMod.LOGGER.info("â•‘   Cached Inventory Items: {}", cachedPlayerInventory.size());
        BapelSlimefunMod.LOGGER.info("â•‘   Known Empty Slots: {}", knownEmptyInputSlots.size());
        BapelSlimefunMod.LOGGER.info("â•‘   Tick Count: {}", automationTickCount);
        BapelSlimefunMod.LOGGER.info("â•‘   Success Rate: {}/{}", successfulInputs, successfulOutputs);
        
        // Overlay
        BapelSlimefunMod.LOGGER.info("â•‘");
        BapelSlimefunMod.LOGGER.info("â•‘ OVERLAY:");
        BapelSlimefunMod.LOGGER.info("â•‘   Visible: {}", RecipeOverlayRenderer.isVisible());
        BapelSlimefunMod.LOGGER.info("â•‘   Available Recipes: {}", 
            RecipeOverlayRenderer.getAvailableRecipes().size());
        
        // Database
        BapelSlimefunMod.LOGGER.info("â•‘");
        BapelSlimefunMod.LOGGER.info("â•‘ DATABASE:");
        BapelSlimefunMod.LOGGER.info("â•‘   Initialized: {}", RecipeDatabase.isInitialized());
        BapelSlimefunMod.LOGGER.info("â•‘   Total Recipes: {}", RecipeDatabase.getTotalRecipes());
        
        // Client
        BapelSlimefunMod.LOGGER.info("â•‘");
        BapelSlimefunMod.LOGGER.info("â•‘ CLIENT:");
        Minecraft mc = Minecraft.getInstance();
        BapelSlimefunMod.LOGGER.info("â•‘   Player: {}", mc.player != null ? "present" : "null");
        BapelSlimefunMod.LOGGER.info("â•‘   Screen: {}", 
            mc.screen != null ? mc.screen.getClass().getSimpleName() : "null");
        
        BapelSlimefunMod.LOGGER.info("â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
    }
    
    public static void logKeyPress(String key) {
        BapelSlimefunMod.LOGGER.info("â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—");
        BapelSlimefunMod.LOGGER.info("â•‘  KEY PRESS: {}", key);
        BapelSlimefunMod.LOGGER.info("â•‘  Overlay Visible: {}", RecipeOverlayRenderer.isVisible());
        BapelSlimefunMod.LOGGER.info("â•‘  In Container: {}", 
            Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen);
        BapelSlimefunMod.LOGGER.info("â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
    }
    
    public static void printDebugStatus() {
        BapelSlimefunMod.LOGGER.info("â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—");
        BapelSlimefunMod.LOGGER.info("â•‘  AUTOMATION STATUS                     â•‘");
        BapelSlimefunMod.LOGGER.info("â• â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•£");
        BapelSlimefunMod.LOGGER.info("â•‘  Enabled: {}", isAutomationEnabled());
        BapelSlimefunMod.LOGGER.info("â•‘  Active: {}", isActive());
        BapelSlimefunMod.LOGGER.info("â•‘  Machine: {}", currentMachine != null ? currentMachine.getName() : "none");
        BapelSlimefunMod.LOGGER.info("â•‘  Tick Count: {}", automationTickCount);
        BapelSlimefunMod.LOGGER.info("â•‘  Inputs: {} | Outputs: {}", successfulInputs, successfulOutputs);
        BapelSlimefunMod.LOGGER.info("â•‘  Cache Size: {} items, {} slots", 
            cachedPlayerInventory.size(), knownEmptyInputSlots.size());
        BapelSlimefunMod.LOGGER.info("â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
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