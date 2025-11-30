package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Handles automation logic for Slimefun machines
 */
public class MachineAutomationHandler {
    private static SlimefunMachineData currentMachine = null;
    private static long lastAutoTick = 0;
    private static final long AUTO_DELAY_MS = 500; // 0.5 second delay between operations
    
    /**
     * Called when player opens a container
     */
    public static void onContainerOpen(String title) {
        currentMachine = SlimefunDataLoader.getMachineByTitle(title);
        if (currentMachine != null) {
            BapelSlimefunMod.LOGGER.info("Detected machine: {}", currentMachine.getName());
        }
    }
    
    /**
     * Called when player closes a container
     */
    public static void onContainerClose() {
        currentMachine = null;
    }
    
    /**
     * Main automation tick - called every client tick
     */
    public static void tick() {
        if (currentMachine == null) return;
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;
        
        // Rate limiting
        long now = System.currentTimeMillis();
        if (now - lastAutoTick < AUTO_DELAY_MS) return;
        lastAutoTick = now;
        
        // 1. Auto-output: Move items from output slots to player inventory
        autoOutput(menu);
        
        // 2. Auto-input: Move matching items from player to input slots
        autoInput(menu, player);
    }
    
    /**
     * Automatically move items from output slots to player inventory
     */
    private static void autoOutput(AbstractContainerMenu menu) {
        if (!currentMachine.hasOutputSlots()) return;
        
        for (int slotIndex : currentMachine.getOutputSlots()) {
            if (slotIndex >= menu.slots.size()) continue;
            
            Slot slot = menu.slots.get(slotIndex);
            ItemStack stack = slot.getItem();
            
            if (!stack.isEmpty()) {
                // Simulate shift-click to move to player inventory
                menu.quickMoveStack(Minecraft.getInstance().player, slotIndex);
                BapelSlimefunMod.LOGGER.debug("Auto-output: Moved {} from slot {}", 
                    stack.getItem().toString(), slotIndex);
                break; // Only move one stack per tick to avoid lag
            }
        }
    }
    
    /**
     * Automatically move matching items from player to input slots
     */
    private static void autoInput(AbstractContainerMenu menu, LocalPlayer player) {
        if (!currentMachine.hasInputSlots()) return;
        if (currentMachine.getRecipe().isEmpty()) return;
        
        // Check if input slots are empty (don't overfill)
        boolean hasEmptySlot = false;
        for (int slotIndex : currentMachine.getInputSlots()) {
            if (slotIndex < menu.slots.size()) {
                Slot slot = menu.slots.get(slotIndex);
                if (slot.getItem().isEmpty()) {
                    hasEmptySlot = true;
                    break;
                }
            }
        }
        
        if (!hasEmptySlot) return;
        
        // Find matching items in player inventory
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            
            String itemId = getItemId(stack);
            
            // Check if this item is in the recipe
            if (isInRecipe(itemId)) {
                // Find empty input slot
                for (int slotIndex : currentMachine.getInputSlots()) {
                    if (slotIndex >= menu.slots.size()) continue;
                    
                    Slot slot = menu.slots.get(slotIndex);
                    if (slot.getItem().isEmpty()) {
                        // Move item to input slot
                        int playerSlotIndex = getPlayerSlotIndex(menu, i);
                        if (playerSlotIndex != -1) {
                            menu.quickMoveStack(player, playerSlotIndex);
                            BapelSlimefunMod.LOGGER.debug("Auto-input: Moved {} to slot {}", 
                                itemId, slotIndex);
                            return; // Only move one item per tick
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Get item ID from ItemStack
     */
    private static String getItemId(ItemStack stack) {
        String fullId = stack.getItem().toString();
        // Extract just the item name (e.g., "minecraft:iron_ingot" from full string)
        if (fullId.contains(":")) {
            return fullId.substring(fullId.lastIndexOf(":") + 1).toUpperCase();
        }
        return fullId.toUpperCase();
    }
    
    /**
     * Check if item ID is in the machine's recipe
     */
    private static boolean isInRecipe(String itemId) {
        for (String recipeItem : currentMachine.getRecipe()) {
            if (recipeItem.contains(":")) {
                String[] parts = recipeItem.split(":");
                if (parts[0].equalsIgnoreCase(itemId)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Get player slot index in container menu
     */
    private static int getPlayerSlotIndex(AbstractContainerMenu menu, int inventoryIndex) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container == Minecraft.getInstance().player.getInventory() 
                && slot.getContainerSlot() == inventoryIndex) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Check if automation is active
     */
    public static boolean isActive() {
        return currentMachine != null;
    }
    
    /**
     * Get current machine (for debugging/UI)
     */
    public static SlimefunMachineData getCurrentMachine() {
        return currentMachine;
    }
}