package com.bapel_slimefun_mod.automation.impl;

import com.bapel_slimefun_mod.automation.infra.RecipeLockExecutor;
import com.bapel_slimefun_mod.automation.util.ItemKeyUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecipeLockExecutorImpl implements RecipeLockExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger("RecipeLockExecutor");

    private boolean recipeLocked       = false;
    private boolean craftingTriggered  = false;
    private int     confirmTicksWaited = 0;

    private static final int RECIPE_SLOT_START  = 0;
    private static final int RECIPE_SLOT_END    = 8;
    private static final int CONFIRM_BUTTON_SLOT = 9;
    private static final int CRAFT_WAIT_TICKS    = 3;

    @Override
    public boolean lockAndExecute(ItemStack targetItem) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) {
            LOGGER.warn("lockAndExecute called but no AbstractContainerScreen is open");
            return false;
        }

        AbstractContainerMenu handler = mc.player.containerMenu;
        if (handler == null) return false;

        if (!recipeLocked) {
            return tryLockRecipe(mc, handler, targetItem);
        }

        if (!craftingTriggered) {
            return tryTriggerCraft(mc, handler);
        }

        confirmTicksWaited++;
        return confirmTicksWaited >= CRAFT_WAIT_TICKS;
    }

    @Override
    public boolean isCraftingInProgress() {
        return craftingTriggered && confirmTicksWaited < CRAFT_WAIT_TICKS;
    }

    private boolean tryLockRecipe(
        Minecraft mc,
        AbstractContainerMenu handler,
        ItemStack targetItem
    ) {
        String targetKey = ItemKeyUtil.getKey(targetItem);

        for (int i = RECIPE_SLOT_START; i <= RECIPE_SLOT_END; i++) {
            if (i >= handler.slots.size()) break;

            Slot slot = handler.slots.get(i);
            if (slot.getItem().isEmpty()) continue;

            String slotKey = ItemKeyUtil.getKey(slot.getItem());
            if (!slotKey.equals(targetKey)) continue;

            mc.gameMode.handleContainerInput(
                handler.containerId,
                i,
                0,
                ContainerInput.PICKUP,
                mc.player
            );

            recipeLocked = true;
            LOGGER.info("Recipe locked for: {} at slot {}", targetKey, i);
            return false;
        }

        LOGGER.warn("No recipe slot found for: {}", targetKey);
        reset();
        return false;
    }

    private boolean tryTriggerCraft(Minecraft mc, AbstractContainerMenu handler) {
        if (CONFIRM_BUTTON_SLOT >= handler.slots.size()) {
            LOGGER.warn("Confirm button slot {} out of bounds (total: {})",
                CONFIRM_BUTTON_SLOT, handler.slots.size());
            reset();
            return false;
        }

        mc.gameMode.handleContainerInput(
            handler.containerId,
            CONFIRM_BUTTON_SLOT,
            0,
            ContainerInput.PICKUP,
            mc.player
        );

        craftingTriggered  = true;
        confirmTicksWaited = 0;
        LOGGER.info("Craft triggered, waiting {} ticks for output...", CRAFT_WAIT_TICKS);
        return false;
    }

    public void reset() {
        recipeLocked       = false;
        craftingTriggered  = false;
        confirmTicksWaited = 0;
    }
}
