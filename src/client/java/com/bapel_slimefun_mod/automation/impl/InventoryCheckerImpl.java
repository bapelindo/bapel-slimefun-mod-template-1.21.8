package com.bapel_slimefun_mod.automation.impl;

import com.bapel_slimefun_mod.automation.infra.InventoryChecker;
import com.bapel_slimefun_mod.automation.AutomationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public class InventoryCheckerImpl implements InventoryChecker {

    @Override
    public int countInInventory(ItemStack target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        String targetKey = getSlimefunKey(target);
        int total = 0;

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            if (targetKey != null) {
                if (targetKey.equals(getSlimefunKey(stack))) {
                    total += stack.getCount();
                }
            } else {
                if (ItemStack.isSameItem(stack, target)) {
                    total += stack.getCount();
                }
            }
        }

        return total;
    }

    @Override
    public boolean hasInventorySpace() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private String getSlimefunKey(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return "slimefun:" + AutomationUtils.getItemId(stack);
    }
}
