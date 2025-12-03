package com.bapel_slimefun_mod.automation;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.core.component.DataComponents; // Penting untuk cek nama di 1.21

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
        
        String targetId = parts[0];
        String itemId = getItemId(stack);
        
        // System.out.println("Comparing " + itemId + " with target " + targetId);
        
        return targetId.equalsIgnoreCase(itemId);
    }
    
    /**
     * Extract item ID from ItemStack
     * FIXED: Now detects Slimefun items based on Display Name
     */
    public static String getItemId(ItemStack stack) {
        // 1. Cek apakah item punya nama custom (ciri khas item Slimefun)
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            // Ambil nama, bersihkan kode warna
            String displayName = stack.getHoverName().getString();
            String cleanName = stripColorCodes(displayName);
            
            // Ubah jadi format ID: "Gold Dust" -> "GOLD_DUST"
            return cleanName.toUpperCase().replace(" ", "_");
        }
        
        // 2. Fallback: Gunakan ID vanilla jika tidak ada nama khusus
        String fullId = stack.getItem().toString(); // Output contoh: "gold_nugget"
        
        // Di 1.21 toString() biasanya sudah bersih, tapi kita pastikan
        if (fullId.contains(":")) {
            fullId = fullId.split(":")[1];
        }
        
        return fullId.toUpperCase();
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
        // Slot inventory player biasanya ada di indeks container terakhir atau memiliki inventory instance yang sama
        return slot.container == menu.slots.get(menu.slots.size() - 1).container;
    }
    
    public static String formatSlotInfo(Slot slot, int index) {
        if (slot == null) return "Slot " + index + ": null";
        
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) {
            return "Slot " + index + ": empty";
        }
        
        return String.format("Slot %d: %s x%d", 
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