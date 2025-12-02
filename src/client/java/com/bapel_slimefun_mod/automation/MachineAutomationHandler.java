package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.*;

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
        } else {
            if (debugMode) {
                BapelSlimefunMod.LOGGER.warn("[Automation] No machine detected for title: {}", title);
            }
        }
    }
    
    public static void onContainerClose() {
        if (currentMachine != null) {
            BapelSlimefunMod.LOGGER.info("[Automation] Machine closed: {}", currentMachine.getName());
            
            if (debugMode) {
                BapelSlimefunMod.LOGGER.info("[Automation] Session stats - Inputs: {}, Outputs: {}, Ticks: {}", 
                    successfulInputs, successfulOutputs, automationTickCount);
            }
        }
        
        currentMachine = null;
        cachedRecipeRequirements.clear();
        selectedRecipeId = null;
        
        automationTickCount = 0;
        successfulInputs = 0;
        successfulOutputs = 0;
    }
    
    private static void cacheRecipeRequirements() {
        if (currentMachine == null || currentMachine.getRecipe().isEmpty()) {
            cachedRecipeRequirements.clear();
            BapelSlimefunMod.LOGGER.warn("[Automation] No recipe data to cache");
            return;
        }
        
        List<RecipeHandler.RecipeIngredient> ingredients = 
            RecipeHandler.parseRecipe(currentMachine.getRecipe());
        
        cachedRecipeRequirements = RecipeHandler.groupRecipeIngredients(ingredients);
        
        BapelSlimefunMod.LOGGER.info("[Automation] Cached {} recipe requirements", 
            cachedRecipeRequirements.size());
        
        if (debugMode) {
            BapelSlimefunMod.LOGGER.info("[Automation] Recipe requirements:");
            for (Map.Entry<String, Integer> entry : cachedRecipeRequirements.entrySet()) {
                BapelSlimefunMod.LOGGER.info("[Automation]   - {} x{}", 
                    entry.getKey(), entry.getValue());
            }
        }
    }
    
    private static void logRecipeInfo() {
        if (currentMachine == null) return;
        
        List<String> recipe = currentMachine.getRecipe();
        if (recipe.isEmpty()) {
            BapelSlimefunMod.LOGGER.debug("[Automation] Machine has no recipe");
            return;
        }
        
        List<RecipeHandler.RecipeIngredient> ingredients = RecipeHandler.parseRecipe(recipe);
        boolean isMultiItem = RecipeHandler.isMultiItemRecipe(ingredients);
        Set<String> uniqueItems = RecipeHandler.getUniqueItems(ingredients);
        
        BapelSlimefunMod.LOGGER.debug("[Automation] Recipe Info:");
        BapelSlimefunMod.LOGGER.debug("[Automation]   Total ingredients: {}", ingredients.size());
        BapelSlimefunMod.LOGGER.debug("[Automation]   Unique items: {}", uniqueItems.size());
        BapelSlimefunMod.LOGGER.debug("[Automation]   Multi-item recipe: {}", isMultiItem);
        BapelSlimefunMod.LOGGER.debug("[Automation]   Input slots: {}", Arrays.toString(currentMachine.getInputSlots()));
        BapelSlimefunMod.LOGGER.debug("[Automation]   Output slots: {}", Arrays.toString(currentMachine.getOutputSlots()));
    }
    
    public static void tick() {
        if (config == null) {
            if (debugMode && automationTickCount == 0) {
                BapelSlimefunMod.LOGGER.warn("[Automation] Config is null!");
            }
            return;
        }
        
        if (!config.isAutomationEnabled()) {
            if (debugMode && automationTickCount == 0) {
                BapelSlimefunMod.LOGGER.info("[Automation] Automation disabled in config");
            }
            return;
        }
        
        if (currentMachine == null) return;
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;
        
        long now = System.currentTimeMillis();
        if (now - lastAutoTick < config.getAutomationDelayMs()) return;
        lastAutoTick = now;
        
        automationTickCount++;
        
        if (debugMode && automationTickCount == 1) {
            BapelSlimefunMod.LOGGER.info("╔═══════════════════════════════════════╗");
            BapelSlimefunMod.LOGGER.info("║  AUTOMATION STARTED                    ║");
            BapelSlimefunMod.LOGGER.info("╠═══════════════════════════════════════╣");
            BapelSlimefunMod.LOGGER.info("║  Machine: {}", currentMachine.getName());
            BapelSlimefunMod.LOGGER.info("║  Delay: {}ms", config.getAutomationDelayMs());
            BapelSlimefunMod.LOGGER.info("║  Recipe Requirements: {}", cachedRecipeRequirements.size());
            BapelSlimefunMod.LOGGER.info("╚═══════════════════════════════════════╝");
        }
        
        try {
            autoOutput(menu, mc);
            autoInput(menu, player, mc);
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error in automation tick", e);
        }
    }
    
    private static void autoOutput(AbstractContainerMenu menu, Minecraft mc) {
        if (!currentMachine.hasOutputSlots()) {
            if (debugMode && automationTickCount == 1) {
                BapelSlimefunMod.LOGGER.warn("[Automation] Machine has no output slots");
            }
            return;
        }
        
        try {
            for (int slotIndex : currentMachine.getOutputSlots()) {
                if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
                    if (debugMode) {
                        BapelSlimefunMod.LOGGER.warn("[Automation] Invalid output slot index: {}", slotIndex);
                    }
                    continue;
                }
                
                Slot slot = menu.slots.get(slotIndex);
                if (slot == null) continue;
                
                ItemStack stack = slot.getItem();
                
                if (!stack.isEmpty()) {
                    String itemId = AutomationUtils.getItemId(stack);
                    int count = stack.getCount();
                    
                    mc.gameMode.handleInventoryMouseClick(
                        menu.containerId, slotIndex, 0, ClickType.QUICK_MOVE, mc.player
                    );
                    
                    successfulOutputs++;
                    
                    BapelSlimefunMod.LOGGER.info("[Automation] Auto-output: Moved {} x{}", itemId, count);
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error in auto-output", e);
        }
    }
    
    private static void autoInput(AbstractContainerMenu menu, LocalPlayer player, Minecraft mc) {
        if (!currentMachine.hasInputSlots()) {
            if (debugMode && automationTickCount == 1) {
                BapelSlimefunMod.LOGGER.warn("[Automation] Machine has no input slots");
            }
            return;
        }
        
        if (cachedRecipeRequirements.isEmpty()) {
            if (debugMode && automationTickCount % 20 == 1) {
                BapelSlimefunMod.LOGGER.warn("[Automation] No cached recipe requirements - cannot automate");
            }
            return;
        }
        
        try {
            if (!hasEmptyInputSlot(menu)) {
                if (debugMode && automationTickCount % 100 == 1) {
                    BapelSlimefunMod.LOGGER.debug("[Automation] No empty input slots");
                }
                return;
            }
            
            List<ItemStack> playerInventory = getPlayerInventoryStacks(player);
            
            RecipeHandler.RecipeSummary summary = new RecipeHandler.RecipeSummary(
                playerInventory, 
                RecipeHandler.parseRecipe(currentMachine.getRecipe())
            );
            
            if (debugMode && automationTickCount % 100 == 1) {
                BapelSlimefunMod.LOGGER.debug("[Automation] Recipe completion: {:.1f}%", 
                    summary.getCompletionPercentage() * 100);
            }
            
            for (Map.Entry<String, Integer> required : cachedRecipeRequirements.entrySet()) {
                String itemId = required.getKey();
                
                if (moveItemToInput(menu, player, mc, itemId)) {
                    break;
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error in auto-input", e);
        }
    }
    
    private static boolean hasEmptyInputSlot(AbstractContainerMenu menu) {
        try {
            for (int slotIndex : currentMachine.getInputSlots()) {
                if (slotIndex >= 0 && slotIndex < menu.slots.size()) {
                    Slot slot = menu.slots.get(slotIndex);
                    if (slot != null && slot.getItem().isEmpty()) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error checking empty input slots", e);
        }
        return false;
    }
    
    private static List<ItemStack> getPlayerInventoryStacks(LocalPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        
        try {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error getting player inventory", e);
        }
        
        return stacks;
    }
    
    private static boolean moveItemToInput(AbstractContainerMenu menu, LocalPlayer player, 
                                          Minecraft mc, String itemId) {
        try {
            int playerSlotIndex = findItemInPlayerInventory(menu, player, itemId);
            if (playerSlotIndex == -1) {
                if (debugMode && automationTickCount % 100 == 1) {
                    BapelSlimefunMod.LOGGER.debug("[Automation] Item not found in player inventory: {}", itemId);
                }
                return false;
            }
            
            int emptyInputSlot = findEmptyInputSlot(menu);
            if (emptyInputSlot == -1) {
                if (debugMode) {
                    BapelSlimefunMod.LOGGER.warn("[Automation] No empty input slot found");
                }
                return false;
            }
            
            Slot playerSlot = menu.slots.get(playerSlotIndex);
            ItemStack stack = playerSlot.getItem();
            int count = stack.getCount();
            
            mc.gameMode.handleInventoryMouseClick(
                menu.containerId, playerSlotIndex, 0, ClickType.QUICK_MOVE, player
            );
            
            successfulInputs++;
            
            BapelSlimefunMod.LOGGER.info("[Automation] Auto-input: Moved {} x{} to slot {}", 
                itemId, count, emptyInputSlot);
            
            return true;
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error moving item to input", e);
            return false;
        }
    }
    
    private static int findItemInPlayerInventory(AbstractContainerMenu menu, 
                                                 LocalPlayer player, String itemId) {
        try {
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot slot = menu.slots.get(i);
                if (slot == null) continue;
                
                if (slot.container != player.getInventory()) continue;
                
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) continue;
                
                String stackItemId = AutomationUtils.getItemId(stack);
                if (stackItemId.equalsIgnoreCase(itemId)) {
                    return i;
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error finding item in player inventory", e);
        }
        
        return -1;
    }
    
    private static int findEmptyInputSlot(AbstractContainerMenu menu) {
        try {
            for (int slotIndex : currentMachine.getInputSlots()) {
                if (slotIndex >= 0 && slotIndex < menu.slots.size()) {
                    Slot slot = menu.slots.get(slotIndex);
                    if (slot != null && slot.getItem().isEmpty()) {
                        return slotIndex;
                    }
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error finding empty input slot", e);
        }
        
        return -1;
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
            
            List<ItemStack> inventory = getPlayerInventoryStacks(player);
            List<RecipeHandler.RecipeIngredient> recipe = 
                RecipeHandler.parseRecipe(currentMachine.getRecipe());
            
            return new RecipeHandler.RecipeSummary(inventory, recipe);
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error getting recipe summary", e);
            return null;
        }
    }
    
    public static void toggleAutomation() {
        if (config != null) {
            boolean newState = !config.isAutomationEnabled();
            config.setAutomationEnabled(newState);
            BapelSlimefunMod.LOGGER.info("[Automation] Toggled: {}", newState ? "ENABLED" : "DISABLED");
        }
    }
    
    public static void setAutomationEnabled(boolean enabled) {
        if (config != null) {
            config.setAutomationEnabled(enabled);
            BapelSlimefunMod.LOGGER.info("[Automation] Set to: {}", enabled ? "ENABLED" : "DISABLED");
        }
    }
    
    public static boolean isAutomationEnabled() {
        return config != null && config.isAutomationEnabled();
    }
    
    public static ModConfig getConfig() {
        return config;
    }
    
    public static void printDebugStatus() {
        BapelSlimefunMod.LOGGER.info("╔═══════════════════════════════════════╗");
        BapelSlimefunMod.LOGGER.info("║  AUTOMATION STATUS                     ║");
        BapelSlimefunMod.LOGGER.info("╠═══════════════════════════════════════╣");
        BapelSlimefunMod.LOGGER.info("║  Enabled: {}", isAutomationEnabled());
        BapelSlimefunMod.LOGGER.info("║  Active: {}", isActive());
        BapelSlimefunMod.LOGGER.info("║  Machine: {}", currentMachine != null ? currentMachine.getName() : "none");
        BapelSlimefunMod.LOGGER.info("║  Selected Recipe: {}", selectedRecipeId != null ? selectedRecipeId : "none");
        BapelSlimefunMod.LOGGER.info("║  Cached Requirements: {}", cachedRecipeRequirements.size());
        BapelSlimefunMod.LOGGER.info("║  Tick Count: {}", automationTickCount);
        BapelSlimefunMod.LOGGER.info("║  Successful Inputs: {}", successfulInputs);
        BapelSlimefunMod.LOGGER.info("║  Successful Outputs: {}", successfulOutputs);
        BapelSlimefunMod.LOGGER.info("║  Debug Mode: {}", debugMode);
        BapelSlimefunMod.LOGGER.info("╚═══════════════════════════════════════╝");
    }
}
