package com.bapel_slimefun_mod.automation.impl;

import com.bapel_slimefun_mod.automation.CraftingJob;
import com.bapel_slimefun_mod.automation.infra.GridScanner;
import com.bapel_slimefun_mod.automation.infra.GuiInteractor;
import com.bapel_slimefun_mod.automation.recipe.*;
import com.bapel_slimefun_mod.automation.util.ItemKeyUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class GridScannerImpl implements GridScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger("GridScanner");

    private static final int GRID_CONTENT_START = 0;
    private static final int GRID_CONTENT_END   = 44;
    private static final int NEXT_PAGE_SLOT     = 45;
    private static final int ITEMS_PER_PAGE     = 45;

    private final GuiInteractor guiInteractor;
    private final SlimefunRecipeRegistry registry;

    private final Set<Integer> clickedSlots = new HashSet<>();

    public GridScannerImpl(GuiInteractor guiInteractor) {
        this.guiInteractor = guiInteractor;
        this.registry      = SlimefunRecipeRegistry.get();
    }

    // ─────────────────────────────────────────────────────────────

    @Override
    public int getMissingAmount(CraftingJob job) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return job.getAmountNeeded();

        int inInv = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && ItemKeyUtil.getKey(stack).equals(job.getItemKey())) {
                inInv += stack.getCount();
            }
        }

        return Math.max(0, job.getAmountNeeded() - inInv);
    }

    @Override
    public boolean shiftClickMaterials(CraftingJob job, int page) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) return false;

        AbstractContainerMenu handler = mc.player.containerMenu;
        if (handler == null) return false;

        boolean found = false;

        Set<String> neededKeys = getNeededIngredientKeys(job);
        if (neededKeys.isEmpty()) return false;

        for (int i = GRID_CONTENT_START; i <= GRID_CONTENT_END; i++) {
            if (i >= handler.slots.size()) break;

            Slot      slot      = handler.slots.get(i);
            ItemStack slotStack = slot.getItem();
            if (slotStack.isEmpty()) continue;

            String slotKey = ItemKeyUtil.getKey(slotStack);

            if (!neededKeys.contains(slotKey)) continue;

            int stillNeed = calculateStillNeeded(job, slotKey, mc);
            if (stillNeed <= 0) continue;

            int absoluteSlot = page * ITEMS_PER_PAGE + i;
            if (clickedSlots.contains(absoluteSlot)) continue;

            mc.gameMode.handleContainerInput(
                handler.containerId,
                i,
                0,
                ContainerInput.QUICK_MOVE,
                mc.player
            );

            clickedSlots.add(absoluteSlot);
            found = true;

            LOGGER.debug("Shift-clicked slot {} for item: {} (need {})",
                i, slotKey, stillNeed);
        }

        return found;
    }

    @Override
    public void navigateToPage(int page) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) return;

        AbstractContainerMenu handler = mc.player.containerMenu;
        if (handler == null || NEXT_PAGE_SLOT >= handler.slots.size()) return;

        mc.gameMode.handleContainerInput(
            handler.containerId,
            NEXT_PAGE_SLOT,
            0,
            ContainerInput.PICKUP,
            mc.player
        );

        LOGGER.debug("Navigated to page {}", page);
    }

    @Override
    public boolean isLastPage(int page) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) return true;

        AbstractContainerMenu handler = mc.player.containerMenu;
        if (handler == null || NEXT_PAGE_SLOT >= handler.slots.size()) return true;

        Slot nextPageSlot = handler.slots.get(NEXT_PAGE_SLOT);
        return nextPageSlot.getItem().isEmpty();
    }

    public void resetClickedSlots() {
        clickedSlots.clear();
    }

    // ─────────────────────────────────────────────────────────────
    // Internal Helpers
    // ─────────────────────────────────────────────────────────────

    private Set<String> getNeededIngredientKeys(CraftingJob job) {
        Optional<RecipeEntry> recipe = registry.getBestRecipe(job.getItemKey());
        if (recipe.isEmpty()) return Set.of(job.getItemKey());

        Set<String> keys = new HashSet<>();
        for (RecipeIngredient ingredient : recipe.get().ingredients()) {
            keys.add(ingredient.itemKey());
        }
        return keys;
    }

    private int calculateStillNeeded(CraftingJob job, String itemKey, Minecraft mc) {
        Optional<RecipeEntry> recipe = registry.getBestRecipe(job.getItemKey());
        if (recipe.isEmpty()) return 0;

        RecipeEntry entry      = recipe.get();
        int craftCount         = entry.craftCountNeeded(job.getAmountNeeded());
        int totalIngredients   = entry.getTotalIngredientAmount(itemKey, craftCount);

        int inInv = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && ItemKeyUtil.getKey(stack).equals(itemKey)) {
                inInv += stack.getCount();
            }
        }

        return Math.max(0, totalIngredients - inInv);
    }
}
