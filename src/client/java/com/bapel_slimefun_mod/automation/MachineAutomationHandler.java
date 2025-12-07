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
 * ✅ FIXED: Clear recipe when changing machines
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
    
    private static List<ItemStack> cachedPlayerInventory = new ArrayList<>();
    private static long lastInventoryCacheTime = 0;
    private static final long INVENTORY_CACHE_DURATION = 50;
    
    private static Set<Integer> knownEmptyInputSlots = new HashSet<>();
    private static long lastEmptySlotCheck = 0;
    private static final long EMPTY_SLOT_CHECK_INTERVAL = 100;
    
    // ✅ Track current machine ID to detect machine changes
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
            }
        }
    }
    
    public static String getSelectedRecipe() {
        return selectedRecipeId;
    }
    
    /**
     * ✅ FIXED: Clear recipe when opening DIFFERENT machine
     */
    public static void onContainerOpen(String title) {
        currentMachine = SlimefunDataLoader.getMachineByTitle(title);
        autoInsertTriggered = false;
        
        if (currentMachine != null) {
            
            // ✅ Check if this is a DIFFERENT machine
            boolean isDifferentMachine = lastMachineId != null && 
                                        !lastMachineId.equals(currentMachine.getId());
            
            if (isDifferentMachine) {
                BapelSlimefunMod.LOGGER.info("[MachineAuto] ⚠ Machine changed: {} → {}", 
                    lastMachineId, currentMachine.getId());
                
                // ✅ CLEAR OLD RECIPE
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
            
            // ✅ Update last machine ID
            lastMachineId = currentMachine.getId();
            
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
                            Component.literal("§a Auto Mode: Loaded recipe '" + displayName + "'"), 
                            true
                        );
                    }
                    
                    autoInsertTriggered = true;
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
    
    public static void onContainerClose() {
        if (config != null && !config.isRememberLastRecipe()) {
            selectedRecipeId = null;
        }
        
        currentMachine = null;
        cachedRecipeRequirements.clear();
        resetCaches();
        autoInsertTriggered = false;
        
        automationTickCount = 0;
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
    
    public static void tick() {
        PerformanceMonitor.start("MachineAuto.tick");
        try {
        if (config == null || !config.isAutomationEnabled() || currentMachine == null) {
            return;
        }
        
        if (selectedRecipeId == null || cachedRecipeRequirements.isEmpty()) {
            return;
        }
        
        if (!isRecipeValidForCurrentMachine()) {
            BapelSlimefunMod.LOGGER.warn("[MachineAuto] ⚠ Recipe mismatch detected - clearing");
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
        if (now - lastAutoTick < config.getAutomationDelayMs()) return;
        lastAutoTick = now;
        
        automationTickCount++;
        
        try {
            autoOutput(menu, mc);
            autoInput(menu, player, mc);
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error in automation tick", e);
        }
    
        } finally {
            PerformanceMonitor.end("MachineAuto.tick");
        }}
    
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
            
            if (!recipeMachineId.equals(currentMachineId)) {
                BapelSlimefunMod.LOGGER.warn("[MachineAuto] Recipe machine mismatch: recipe={}, current={}", 
                    recipeMachineId, currentMachineId);
                return false;
            }
            
            return true;
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[MachineAuto] Error validating recipe", e);
            return false;
        }
    }
    
    private static void autoOutput(AbstractContainerMenu menu, Minecraft mc) {
        if (!currentMachine.hasOutputSlots()) return;
        
        try {
            int[] outputSlots = currentMachine.getOutputSlots();
            
            for (int slotIndex : outputSlots) {
                if (slotIndex < 0 || slotIndex >= menu.slots.size()) continue;
                
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
    
    private static void autoInput(AbstractContainerMenu menu, LocalPlayer player, Minecraft mc) {
        if (!currentMachine.hasInputSlots() || cachedRecipeRequirements.isEmpty()) return;
        
        try {
            long now = System.currentTimeMillis();
            if (now - lastEmptySlotCheck > EMPTY_SLOT_CHECK_INTERVAL) {
                updateEmptyInputSlots(menu);
                lastEmptySlotCheck = now;
            }
            
            if (knownEmptyInputSlots.isEmpty()) {
                return;
            }
            
            List<ItemStack> playerInventory = getCachedPlayerInventory(player);
            
            for (Map.Entry<String, Integer> required : cachedRecipeRequirements.entrySet()) {
                String itemId = required.getKey();
                moveItemToInput(menu, player, mc, itemId, playerInventory);
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error in auto-input", e);
        }
    }
    
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
            
            Slot playerSlot = menu.slots.get(playerSlotIndex);
            
            mc.gameMode.handleInventoryMouseClick(
                menu.containerId, playerSlotIndex, 0, ClickType.QUICK_MOVE, player
            );
            
            knownEmptyInputSlots.remove(emptySlot);
            successfulInputs++;
            
            return true;
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[Automation] Error moving item to input", e);
            return false;
        }
    }
    
    private static int findItemInPlayerInventoryOptimized(AbstractContainerMenu menu, 
                                                          LocalPlayer player, String itemId) {
        try {
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

    private static void sendPlayerMessage(String message) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(message), true);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

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
            BapelSlimefunMod.LOGGER.error("[Automation] Error getting recipe summary", e);
            return null;
        }
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