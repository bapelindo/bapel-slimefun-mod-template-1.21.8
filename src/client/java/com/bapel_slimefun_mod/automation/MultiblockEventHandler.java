package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EVENT-DRIVEN Multiblock Automation Handler
 * 
 * Menggunakan event-driven approach untuk:
 * 1. Deteksi perubahan inventory (item habis/penuh)
 * 2. Auto-stop auto clicker saat kondisi tercapai
 * 3. Event-based filling daripada tick-based
 */
public class MultiblockEventHandler {
    
    // Event listeners
    private static Map<Integer, ItemStack> previousDispenserState = new HashMap<>();
    private static Map<Integer, ItemStack> previousInventoryState = new HashMap<>();
    
    // Status flags
    private static boolean dispenserFull = false;
    private static boolean inventoryEmpty = false;
    private static long lastCheckTime = 0;
    private static final long CHECK_INTERVAL = 500; // Check setiap 0.5 detik
    
    /**
     * EVENT: Cek perubahan inventory - dipanggil setiap tick
     */
    public static void checkInventoryChanges() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < CHECK_INTERVAL) {
            return;
        }
        lastCheckTime = now;
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        if (player == null) return;
        
        AbstractContainerMenu menu = player.containerMenu;
        
        // Hanya cek jika di GUI dispenser
        if (!(menu instanceof DispenserMenu)) {
            return;
        }
        
        // EVENT 1: Cek apakah dispenser penuh
        boolean nowDispenserFull = checkDispenserFull(menu);
        if (nowDispenserFull != dispenserFull) {
            dispenserFull = nowDispenserFull;
            onDispenserFullChanged(nowDispenserFull);
        }
        
        // EVENT 2: Cek apakah inventory kosong (untuk item yang dibutuhkan)
        boolean nowInventoryEmpty = checkInventoryEmpty(menu, player);
        if (nowInventoryEmpty != inventoryEmpty) {
            inventoryEmpty = nowInventoryEmpty;
            onInventoryEmptyChanged(nowInventoryEmpty);
        }
    }
    
    /**
     * EVENT HANDLER: Saat dispenser menjadi penuh
     */
    private static void onDispenserFullChanged(boolean isFull) {
        BapelSlimefunMod.LOGGER.info("[Event] Dispenser full status changed: {}", isFull);
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && isFull) {
            mc.player.displayClientMessage(
                Component.literal("§a✓ Dispenser Full - Ready to process!"),
                true
            );
        }
    }
    
    /**
     * EVENT HANDLER: Saat inventory kosong (tidak ada item yang dibutuhkan)
     */
    private static void onInventoryEmptyChanged(boolean isEmpty) {
        BapelSlimefunMod.LOGGER.info("[Event] Inventory empty status changed: {}", isEmpty);
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && isEmpty) {
            mc.player.displayClientMessage(
                Component.literal("§e⚠ No more items in inventory!"),
                true
            );
        }
    }
    
    /**
     * Cek apakah dispenser sudah penuh sesuai resep
     */
    private static boolean checkDispenserFull(AbstractContainerMenu menu) {
        String selectedRecipe = MultiblockAutomationHandler.getSelectedRecipe();
        if (selectedRecipe == null) return false;
        
        RecipeData recipe = RecipeDatabase.getRecipe(selectedRecipe);
        if (recipe == null) return false;
        
        List<RecipeHandler.RecipeIngredient> inputs = padInputsTo9(recipe.getInputs());
        
        // Cek setiap slot (0-8)
        for (int i = 0; i < 9; i++) {
            Slot slot = menu.getSlot(i);
            ItemStack currentStack = slot.getItem();
            RecipeHandler.RecipeIngredient target = inputs.get(i);
            
            String currentId = AutomationUtils.getItemId(currentStack);
            
            // Slot harus kosong
            if (target.getItemId().equals("AIR") || target.getAmount() == 0) {
                if (!currentStack.isEmpty()) {
                    return false; // Ada item di slot yang seharusnya kosong
                }
            } 
            // Slot harus ada item
            else {
                if (currentStack.isEmpty()) {
                    return false; // Slot kosong padahal harus ada item
                }
                
                if (!currentId.equals(target.getItemId())) {
                    return false; // Item salah
                }
                
                // Cek apakah sudah penuh (max stack size)
                if (currentStack.getCount() < currentStack.getMaxStackSize()) {
                    return false; // Belum penuh
                }
            }
        }
        
        return true; // Semua slot sudah benar dan penuh
    }
    
    /**
     * Cek apakah inventory player kosong (tidak ada item yang dibutuhkan resep)
     */
    private static boolean checkInventoryEmpty(AbstractContainerMenu menu, LocalPlayer player) {
        String selectedRecipe = MultiblockAutomationHandler.getSelectedRecipe();
        if (selectedRecipe == null) return false;
        
        RecipeData recipe = RecipeDatabase.getRecipe(selectedRecipe);
        if (recipe == null) return false;
        
        List<RecipeHandler.RecipeIngredient> inputs = recipe.getInputs();
        
        // Cek apakah ada salah satu item resep di inventory
        for (RecipeHandler.RecipeIngredient ingredient : inputs) {
            if (ingredient.getItemId().equals("AIR") || ingredient.getAmount() == 0) {
                continue;
            }
            
            // Cari item ini di player inventory (slot 9+)
            for (int i = 9; i < menu.slots.size(); i++) {
                Slot slot = menu.slots.get(i);
                if (slot.hasItem()) {
                    ItemStack stack = slot.getItem();
                    String itemId = AutomationUtils.getItemId(stack);
                    
                    if (itemId.equals(ingredient.getItemId())) {
                        return false; // Masih ada item
                    }
                }
            }
        }
        
        return true; // Tidak ada item yang dibutuhkan di inventory
    }
    
    /**
     * Pad inputs to 9 slots (sama seperti di MultiblockAutomationHandler)
     */
    private static List<RecipeHandler.RecipeIngredient> padInputsTo9(List<RecipeHandler.RecipeIngredient> inputs) {
        java.util.List<RecipeHandler.RecipeIngredient> padded = new java.util.ArrayList<>(inputs);
        
        if (padded.size() == 9) {
            return padded;
        }
        
        if (padded.size() > 9) {
            return new java.util.ArrayList<>(padded.subList(0, 9));
        }
        
        while (padded.size() < 9) {
            padded.add(new RecipeHandler.RecipeIngredient("AIR", 0));
        }
        
        return padded;
    }
    
    /**
     * EVENT: Saat slot dispenser berubah
     * Dipanggil dari mixin atau event listener
     */
    public static void onDispenserSlotChanged(int slotIndex, ItemStack oldStack, ItemStack newStack) {
        BapelSlimefunMod.LOGGER.debug("[Event] Dispenser slot {} changed: {} -> {}", 
            slotIndex, 
            AutomationUtils.getItemId(oldStack), 
            AutomationUtils.getItemId(newStack));
        
        // Update state
        previousDispenserState.put(slotIndex, newStack.copy());
        
        // Trigger check
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof DispenserMenu) {
            checkInventoryChanges();
        }
    }
    
    /**
     * EVENT: Saat slot inventory player berubah
     */
    public static void onInventorySlotChanged(int slotIndex, ItemStack oldStack, ItemStack newStack) {
        BapelSlimefunMod.LOGGER.debug("[Event] Inventory slot {} changed: {} -> {}", 
            slotIndex, 
            AutomationUtils.getItemId(oldStack), 
            AutomationUtils.getItemId(newStack));
        
        // Update state
        previousInventoryState.put(slotIndex, newStack.copy());
    }
    
    /**
     * Check jika auto clicker harus berhenti
     */
    public static boolean shouldStopAutoClicker() {
        // Stop jika inventory kosong DAN dispenser tidak penuh
        // (artinya tidak bisa melanjutkan proses)
        if (inventoryEmpty && !dispenserFull) {
            BapelSlimefunMod.LOGGER.info("[Event] Auto-clicker should stop: inventory empty");
            return true;
        }
        
        return false;
    }
    
    /**
     * Check jika dispenser sudah siap untuk di-click
     */
    public static boolean isReadyToClick() {
        return dispenserFull;
    }
    
    /**
     * Reset state
     */
    public static void reset() {
        previousDispenserState.clear();
        previousInventoryState.clear();
        dispenserFull = false;
        inventoryEmpty = false;
        lastCheckTime = 0;
    }
    
    /**
     * Get status untuk debugging
     */
    public static String getStatus() {
        return String.format("Dispenser Full: %s | Inventory Empty: %s", 
            dispenserFull, inventoryEmpty);
    }
}