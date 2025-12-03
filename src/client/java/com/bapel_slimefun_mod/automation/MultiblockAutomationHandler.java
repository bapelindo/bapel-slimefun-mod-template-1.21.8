package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;

import java.util.*;

/**
 * Handles automation specifically for multiblock machines
 * 
 * Multiblock machines work differently:
 * - No GUI slots like electric machines
 * - Items are placed in dispenser/hopper blocks
 * - Right-click on dispenser triggers recipe processing
 * - Output appears in dispenser/player inventory
 */
public class MultiblockAutomationHandler {
    
    private static final int SEARCH_RADIUS = 5; // Blocks to search around player
    private static ModConfig config;
    private static String selectedRecipeId = null;
    private static long lastProcessTime = 0;
    private static final Map<String, Integer> recipeRequirements = new HashMap<>();
    private static long lastActionTime = 0;
    private static int nextSlotIndex = 0;
    public static void init(ModConfig cfg) {
        config = cfg;
    }
    
    /**
     * Set selected recipe for multiblock machine
     */
    public static void setSelectedRecipe(String recipeId) {
        selectedRecipeId = recipeId;
    }
    
    public static String getSelectedRecipe() {
        return selectedRecipeId;
    }
    
    /**
     * Main tick handler for multiblock automation
     * Called every game tick when automation is enabled
     */
    public static void tick(SlimefunMachineData machine) {
        if (machine == null || !machine.isMultiblock()) return;
        if (selectedRecipeId == null) return;
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        
        if (player == null || level == null) return;
        
        // Throttle processing
        long now = System.currentTimeMillis();
        if (config != null && now - lastProcessTime < config.getAutomationDelayMs()) {
            return;
        }
if (player.containerMenu instanceof DispenserMenu) {
            RecipeData recipe = RecipeDatabase.getRecipe(selectedRecipeId);
            if (recipe != null) {
                // Cari posisi dispenser (untuk memenuhi parameter, meski logic utama pakai GUI)
                BlockPos dispenserPos = findNearbyDispenser(player, level);
                if (dispenserPos == null) dispenserPos = player.blockPosition(); // Fallback

                if (autoFillDispenser(player, level, dispenserPos, recipe)) {
                    lastProcessTime = now;
                }
            }
        }
        lastProcessTime = now;
        
        // Get recipe requirements
        RecipeData recipe = RecipeDatabase.getRecipe(selectedRecipeId);
        if (recipe == null) {
            BapelSlimefunMod.LOGGER.warn("[MultiblockAuto] Recipe not found: {}", selectedRecipeId);
            return;
        }
        
        // Find nearby dispenser/hopper
        BlockPos dispenserPos = findNearbyDispenser(player, level);
        if (dispenserPos == null) {
            // No dispenser found - might not be near the multiblock
            return;
        }
        // Try to auto-fill dispenser with recipe ingredients
        boolean inserted = autoFillDispenser(player, level, dispenserPos, recipe);
        
        if (inserted) {
        }
    }
    
    /**
     * Find dispenser/hopper near player
     */
private static BlockPos findNearbyDispenser(LocalPlayer player, Level level) {
        BlockPos playerPos = player.blockPosition();
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -SEARCH_RADIUS; y <= SEARCH_RADIUS; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (level.getBlockEntity(pos) instanceof DispenserBlockEntity) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
    
/**
     * Mengisi dispenser sesuai resep menggunakan Shift-Click (Server Sync)
     */
private static boolean autoFillDispenser(LocalPlayer player, Level level, BlockPos pos, RecipeData recipe) {
        // SYARAT: GUI Dispenser harus terbuka
        if (!(player.containerMenu instanceof DispenserMenu)) return false;

        Minecraft mc = Minecraft.getInstance();
        AbstractContainerMenu menu = player.containerMenu;
        List<RecipeHandler.RecipeIngredient> inputs = recipe.getInputs();
        
        if (inputs.size() != 9) return false;

        // Loop 9 kali dimulai dari nextSlotIndex (agar bergiliran)
        for (int k = 0; k < 9; k++) {
            // Hitung index slot saat ini (0-8) secara melingkar
            int i = (nextSlotIndex + k) % 9; 
            
            RecipeHandler.RecipeIngredient target = inputs.get(i);
            ItemStack currentStack = menu.getSlot(i).getItem();
            String currentId = AutomationUtils.getItemId(currentStack);
            
            // 1. Handle Slot yang harusnya KOSONG (AIR)
            if (target.getItemId().equals("AIR") || target.getAmount() == 0) {
                if (!currentStack.isEmpty()) {
                    // Buang item sampah ke inventory
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, player);
                    return true; 
                }
                continue; 
            }

            // 2. Handle Slot yang butuh ITEM
            // Cek: Kosong ATAU (Item Sama DAN Belum Full Stack)
            boolean isSameItem = !currentStack.isEmpty() && currentId.equals(target.getItemId());
            boolean needsRefill = currentStack.isEmpty() || (isSameItem && currentStack.getCount() < currentStack.getMaxStackSize());
            
            if (needsRefill) {
                // A. Jika item salah, bersihkan dulu
                if (!currentStack.isEmpty() && !isSameItem) {
                     mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, player);
                     return true;
                }

                // B. Cari item di inventory player
                int sourceSlot = findItemInPlayerInventory(menu, player, target.getItemId());
                
                if (sourceSlot != -1) {
                    // TEKNIK DISTRIBUSI 1-BY-1 (PICK - PLACE ONE - RETURN)
                    
                    // 1. Ambil Stack dari Inventory (Klik Kiri)
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, sourceSlot, 0, ClickType.PICKUP, player);
                    
                    // 2. Taruh 1 Item di Dispenser (Klik Kanan = Button 1)
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 1, ClickType.PICKUP, player);
                    
                    // 3. Kembalikan Sisa ke Inventory (Klik Kiri)
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, sourceSlot, 0, ClickType.PICKUP, player);
                    
                    // Geser giliran ke slot berikutnya untuk tick depan
                    nextSlotIndex = (i + 1) % 9;
                    
                    return true; // Selesai satu aksi per tick
                }
            }
        }
        
        return false;
    }
    // --- Helper Methods ---

    private static int countItemInContainer(AbstractContainerMenu menu, int startSlot, int endSlot, String targetItemId) {
        int count = 0;
        for (int i = startSlot; i < endSlot; i++) {
            if (i >= menu.slots.size()) break;
            ItemStack stack = menu.slots.get(i).getItem();
            if (!stack.isEmpty() && AutomationUtils.getItemId(stack).equals(targetItemId)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int findItemInPlayerInventory(AbstractContainerMenu menu, LocalPlayer player, String targetItemId) {
        int startSlot = 9; // Mulai dari inventory player (lewati slot dispenser 0-8)
        int endSlot = menu.slots.size();

        for (int i = startSlot; i < endSlot; i++) {
            Slot slot = menu.slots.get(i);
            if (slot.hasItem()) {
                ItemStack stack = slot.getItem();
                if (AutomationUtils.getItemId(stack).equals(targetItemId)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Get player inventory as list
     */
    private static List<ItemStack> getPlayerInventory(LocalPlayer player) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            items.add(player.getInventory().getItem(i));
        }
        return items;
    }
    
    /**
     * Get item ID from ItemStack
     * FIXED: Compatible with Minecraft 1.21.8 API
     */
    private static String getItemId(ItemStack stack) {
        if (stack.isEmpty()) return "AIR";
        
        try {
            // Try to get custom name from hover text
            String hoverName = stack.getHoverName().getString();
            if (hoverName != null && !hoverName.isEmpty()) {
                // Clean color codes and format as ID
                String cleaned = hoverName.replaceAll("§[0-9a-fk-or]", "");
                if (!cleaned.isEmpty() && !cleaned.equals(stack.getItem().toString())) {
                    return cleaned.trim().toUpperCase().replace(" ", "_");
                }
            }
        } catch (Exception e) {
            // Ignore errors and fallback
        }
        
        // Fallback to registry name
        String registryName = stack.getItem().toString();
        return registryName.toUpperCase();
    }
    
    /**
     * Check if we can process recipe with current dispenser contents
     */
    public static boolean canProcess(SlimefunMachineData machine, BlockPos dispenserPos, Level level) {
        if (selectedRecipeId == null) return false;
        
        RecipeData recipe = RecipeDatabase.getRecipe(selectedRecipeId);
        if (recipe == null) return false;
        
        BlockEntity blockEntity = level.getBlockEntity(dispenserPos);
        if (!(blockEntity instanceof DispenserBlockEntity)) return false;
        
        DispenserBlockEntity dispenser = (DispenserBlockEntity) blockEntity;
        
        // Check if dispenser has all required items
        Map<String, Integer> requirements = recipe.getGroupedInputs();
        Map<String, Integer> available = new HashMap<>();
        
        for (int i = 0; i < dispenser.getContainerSize(); i++) {
            ItemStack stack = dispenser.getItem(i);
            if (!stack.isEmpty()) {
                String itemId = getItemId(stack);
                available.put(itemId, available.getOrDefault(itemId, 0) + stack.getCount());
            }
        }
        
        // Check if all requirements are met
        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            int needed = entry.getValue();
            int has = available.getOrDefault(entry.getKey(), 0);
            if (has < needed) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Reset handler
     */
    public static void reset() {
        selectedRecipeId = null;
        lastProcessTime = 0;
    }
    
    /**
     * Get recipe summary for multiblock
     * FIXED: Use correct return type
     */
    public static RecipeHandler.RecipeSummary getRecipeSummary(SlimefunMachineData machine) {
        if (machine == null || !machine.isMultiblock()) return null;
        if (selectedRecipeId == null) return null;
        
        RecipeData recipe = RecipeDatabase.getRecipe(selectedRecipeId);
        if (recipe == null) return null;
        
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return null;
            
            List<ItemStack> inventory = getPlayerInventory(player);
            // FIXED: recipe.getInputs() already returns List<RecipeIngredient>
            List<RecipeHandler.RecipeIngredient> recipeIngredients = recipe.getInputs();
            
            return new RecipeHandler.RecipeSummary(inventory, recipeIngredients);
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[MultiblockAuto] Error getting recipe summary", e);
            return null;
        }
    }
}