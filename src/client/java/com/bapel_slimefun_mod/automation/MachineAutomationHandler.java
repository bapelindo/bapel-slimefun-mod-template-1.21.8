package com.bapel_slimefun_mod.automation;

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
 * OPTIMIZED Main handler for machine automation
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
        BapelSlimefunMod.LOGGER.info("[Automation] Handler initialized");
    }
    
    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
        BapelSlimefunMod.LOGGER.info("[Automation] Debug mode: {}", enabled ? "ENABLED" : "DISABLED");
    }
    
    public static void setSelectedRecipe(String recipeId) {
        selectedRecipeId = recipeId;
        BapelSlimefunMod.LOGGER.info("[Automation] Selected recipe: {}", recipeId);
        
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
    
    public static void onContainerOpen(String title) {
        currentMachine = SlimefunDataLoader.getMachineByTitle(title);
        if (currentMachine != null) {
            BapelSlimefunMod.LOGGER.info("[Automation] Detected machine: {} (ID: {})", 
                currentMachine.getName(), currentMachine.getId());
            
            // Reset caches for new machine
            resetCaches();
            cacheRecipeRequirements();
            
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
        
        // Clear all state
        currentMachine = null;
        cachedRecipeRequirements.clear();
        selectedRecipeId = null;
        resetCaches();
        
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
    
    private static void cacheRecipeRequirements() {
        if (currentMachine == null || currentMachine.getRecipe().isEmpty()) {
            cachedRecipeRequirements.clear();
            return;
        }
        
        List<RecipeHandler.RecipeIngredient> ingredients = 
            RecipeHandler.parseRecipe(currentMachine.getRecipe());
        
        cachedRecipeRequirements = RecipeHandler.groupRecipeIngredients(ingredients);
        
        if (debugMode) {
            BapelSlimefunMod.LOGGER.info("[Automation] Cached {} recipe requirements", 
                cachedRecipeRequirements.size());
        }
    }
    
    private static void logRecipeInfo() {
        if (currentMachine == null) return;
        
        List<String> recipe = currentMachine.getRecipe();
        if (recipe.isEmpty()) return;
        
        List<RecipeHandler.RecipeIngredient> ingredients = RecipeHandler.parseRecipe(recipe);
        Set<String> uniqueItems = RecipeHandler.getUniqueItems(ingredients);
        
        BapelSlimefunMod.LOGGER.debug("[Automation] Recipe: {} ingredients, {} unique items", 
            ingredients.size(), uniqueItems.size());
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
            BapelSlimefunMod.LOGGER.info("╔═══════════════════════════════════════╗");
            BapelSlimefunMod.LOGGER.info("║  AUTOMATION STARTED                    ║");
            BapelSlimefunMod.LOGGER.info("║  Machine: {}", currentMachine.getName());
            BapelSlimefunMod.LOGGER.info("║  Delay: {}ms", config.getAutomationDelayMs());
            BapelSlimefunMod.LOGGER.info("╚═══════════════════════════════════════╝");
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
                
                // PERBAIKAN DI SINI: "break" dihapus
                // Sekarang loop akan mencoba memindahkan SATU dari SETIAP jenis item per tick.
                // Contoh: 1 Carbon, lalu 1 Iron Ingot, lalu 1 Iron Dust dalam satu siklus tick.
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
     * OPTIMIZED: Move item to input with cached inventory lookup
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
                BapelSlimefunMod.LOGGER.info("[Automation] Auto-input: {} x{} → slot {}", 
                    itemId, count, emptySlot);
            }
            
            return true;
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error moving item to input", e);
            return false;
        }
    }
    
    /**
     * OPTIMIZED: Find item in player inventory (reduced iterations)
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

    // Helper method untuk mengirim pesan ke player (sebelumnya hilang)
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

    // FIX: Method toggle untuk Keybind (dengan akses config yang benar)
    public static void toggle() {
        if (config != null) {
            boolean newState = !config.isAutomationEnabled();
            config.setAutomationEnabled(newState);
            
            if (!newState) {
                resetCaches();
                sendPlayerMessage("§c[Slimefun] Automation STOPPED ⏹");
                BapelSlimefunMod.LOGGER.info("[Automation] Stopped manually");
            } else {
                sendPlayerMessage("§a[Slimefun] Automation STARTED ▶");
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
    
    public static void setAutomationEnabled(boolean enabled) {
        if (config != null) {
            config.setAutomationEnabled(enabled);
            
            if (!enabled) {
                resetCaches();
            }
            
            BapelSlimefunMod.LOGGER.info("[Automation] Set to: {}", enabled ? "ENABLED" : "DISABLED");
        }
    }
    
    public static boolean isAutomationEnabled() {
        return config != null && config.isAutomationEnabled();
    }
    
    public static ModConfig getConfig() {
        return config;
    }
    
    // ========================================
    // DEBUG DIAGNOSTIC METHODS
    // ========================================
    
    public static void runFullDiagnostic() {
        BapelSlimefunMod.LOGGER.info("╔════════════════════════════════════════════════════════╗");
        BapelSlimefunMod.LOGGER.info("║                   FULL DIAGNOSTIC                       ║");
        BapelSlimefunMod.LOGGER.info("╠════════════════════════════════════════════════════════╣");
        
        // Config
        BapelSlimefunMod.LOGGER.info("║ CONFIG:");
        if (config != null) {
            BapelSlimefunMod.LOGGER.info("║   Automation: {}", config.isAutomationEnabled());
            BapelSlimefunMod.LOGGER.info("║   Delay: {}ms", config.getAutomationDelayMs());
            BapelSlimefunMod.LOGGER.info("║   Debug: {}", config.isDebugMode());
        } else {
            BapelSlimefunMod.LOGGER.info("║   NULL");
        }
        
        // Machine
        BapelSlimefunMod.LOGGER.info("║");
        BapelSlimefunMod.LOGGER.info("║ MACHINE:");
        if (currentMachine != null) {
            BapelSlimefunMod.LOGGER.info("║   Name: {}", currentMachine.getName());
            BapelSlimefunMod.LOGGER.info("║   ID: {}", currentMachine.getId());
            BapelSlimefunMod.LOGGER.info("║   Input Slots: {}", currentMachine.getInputSlots().length);
            BapelSlimefunMod.LOGGER.info("║   Output Slots: {}", currentMachine.getOutputSlots().length);
        } else {
            BapelSlimefunMod.LOGGER.info("║   NULL");
        }
        
        // Automation status
        BapelSlimefunMod.LOGGER.info("║");
        BapelSlimefunMod.LOGGER.info("║ AUTOMATION:");
        BapelSlimefunMod.LOGGER.info("║   Active: {}", isActive());
        BapelSlimefunMod.LOGGER.info("║   Enabled: {}", isAutomationEnabled());
        BapelSlimefunMod.LOGGER.info("║   Selected Recipe: {}", 
            selectedRecipeId != null ? selectedRecipeId : "none");
        
        // Performance metrics
        BapelSlimefunMod.LOGGER.info("║");
        BapelSlimefunMod.LOGGER.info("║ PERFORMANCE:");
        BapelSlimefunMod.LOGGER.info("║   Cached Inventory Items: {}", cachedPlayerInventory.size());
        BapelSlimefunMod.LOGGER.info("║   Known Empty Slots: {}", knownEmptyInputSlots.size());
        BapelSlimefunMod.LOGGER.info("║   Tick Count: {}", automationTickCount);
        BapelSlimefunMod.LOGGER.info("║   Success Rate: {}/{}", successfulInputs, successfulOutputs);
        
        // Overlay
        BapelSlimefunMod.LOGGER.info("║");
        BapelSlimefunMod.LOGGER.info("║ OVERLAY:");
        BapelSlimefunMod.LOGGER.info("║   Visible: {}", RecipeOverlayRenderer.isVisible());
        BapelSlimefunMod.LOGGER.info("║   Available Recipes: {}", 
            RecipeOverlayRenderer.getAvailableRecipes().size());
        
        // Database
        BapelSlimefunMod.LOGGER.info("║");
        BapelSlimefunMod.LOGGER.info("║ DATABASE:");
        BapelSlimefunMod.LOGGER.info("║   Initialized: {}", RecipeDatabase.isInitialized());
        BapelSlimefunMod.LOGGER.info("║   Total Recipes: {}", RecipeDatabase.getTotalRecipes());
        
        // Client
        BapelSlimefunMod.LOGGER.info("║");
        BapelSlimefunMod.LOGGER.info("║ CLIENT:");
        Minecraft mc = Minecraft.getInstance();
        BapelSlimefunMod.LOGGER.info("║   Player: {}", mc.player != null ? "present" : "null");
        BapelSlimefunMod.LOGGER.info("║   Screen: {}", 
            mc.screen != null ? mc.screen.getClass().getSimpleName() : "null");
        
        BapelSlimefunMod.LOGGER.info("╚════════════════════════════════════════════════════════╝");
    }
    
    public static void logKeyPress(String key) {
        BapelSlimefunMod.LOGGER.info("╔════════════════════════════════════════════════════════╗");
        BapelSlimefunMod.LOGGER.info("║  KEY PRESS: {}", key);
        BapelSlimefunMod.LOGGER.info("║  Overlay Visible: {}", RecipeOverlayRenderer.isVisible());
        BapelSlimefunMod.LOGGER.info("║  In Container: {}", 
            Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen);
        BapelSlimefunMod.LOGGER.info("╚════════════════════════════════════════════════════════╝");
    }
    
    public static void printDebugStatus() {
        BapelSlimefunMod.LOGGER.info("╔═══════════════════════════════════════╗");
        BapelSlimefunMod.LOGGER.info("║  AUTOMATION STATUS                     ║");
        BapelSlimefunMod.LOGGER.info("╠═══════════════════════════════════════╣");
        BapelSlimefunMod.LOGGER.info("║  Enabled: {}", isAutomationEnabled());
        BapelSlimefunMod.LOGGER.info("║  Active: {}", isActive());
        BapelSlimefunMod.LOGGER.info("║  Machine: {}", currentMachine != null ? currentMachine.getName() : "none");
        BapelSlimefunMod.LOGGER.info("║  Tick Count: {}", automationTickCount);
        BapelSlimefunMod.LOGGER.info("║  Inputs: {} | Outputs: {}", successfulInputs, successfulOutputs);
        BapelSlimefunMod.LOGGER.info("║  Cache Size: {} items, {} slots", 
            cachedPlayerInventory.size(), knownEmptyInputSlots.size());
        BapelSlimefunMod.LOGGER.info("╚═══════════════════════════════════════╝");
    }
}