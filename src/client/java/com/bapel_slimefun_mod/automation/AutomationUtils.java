package com.bapel_slimefun_mod.automation;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for automation
 */
public class AutomationUtils {
    
    /**
     * Get all non-empty slots in a container
     */
    public static List<Slot> getNonEmptySlots(AbstractContainerMenu menu) {
        List<Slot> result = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (!slot.getItem().isEmpty()) {
                result.add(slot);
            }
        }
        return result;
    }
    
    /**
     * Get all empty slots in a container
     */
    public static List<Slot> getEmptySlots(AbstractContainerMenu menu) {
        List<Slot> result = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.getItem().isEmpty()) {
                result.add(slot);
            }
        }
        return result;
    }
    
    /**
     * Check if item matches a recipe component
     */
    public static boolean matchesRecipe(ItemStack stack, String recipeItem) {
        if (stack.isEmpty()) return false;
        if (recipeItem.equals("AIR:0")) return false;
        
        String[] parts = recipeItem.split(":");
        if (parts.length < 1) return false;
        
        String itemId = getItemId(stack);
        return parts[0].equalsIgnoreCase(itemId);
    }
    
    /**
     * Extract item ID from ItemStack
     */
    public static String getItemId(ItemStack stack) {
        String fullId = stack.getItem().toString();
        
        // Try to extract registry name
        if (fullId.contains("'")) {
            int start = fullId.indexOf("'") + 1;
            int end = fullId.lastIndexOf("'");
            if (start > 0 && end > start) {
                String registryName = fullId.substring(start, end);
                if (registryName.contains(":")) {
                    return registryName.split(":")[1].toUpperCase();
                }
                return registryName.toUpperCase();
            }
        }
        
        // Fallback: use class name
        return stack.getItem().getClass().getSimpleName().toUpperCase();
    }
    
    /**
     * Get slot by index safely
     */
    public static Slot getSlot(AbstractContainerMenu menu, int index) {
        if (index < 0 || index >= menu.slots.size()) {
            return null;
        }
        return menu.slots.get(index);
    }
    
    /**
     * Check if slot is in player inventory
     */
    public static boolean isPlayerSlot(Slot slot, AbstractContainerMenu menu) {
        return slot.container == menu.slots.get(0).container;
    }
    
    /**
     * Format slot info for debugging
     */
    public static String formatSlotInfo(Slot slot, int index) {
        if (slot == null) return "Slot " + index + ": null";
        
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) {
            return "Slot " + index + ": empty";
        }
        
        return String.format("Slot %d: %s x%d", 
            index, 
            getItemId(stack), 
            stack.getCount()
        );
    }
    
    /**
     * Check if two items can stack together
     */
    public static boolean canStack(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (!ItemStack.isSameItem(a, b)) return false;
        if (!ItemStack.isSameItemSameComponents(a, b)) return false;
        return a.getCount() + b.getCount() <= a.getMaxStackSize();
    }
    
    /**
     * Remove color codes from string
     */
    public static String stripColorCodes(String text) {
        return text.replaceAll("§[0-9a-fk-or]", "");
    }
}
