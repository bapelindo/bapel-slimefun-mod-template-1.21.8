package com.bapel_slimefun_mod.automation.util;

import com.bapel_slimefun_mod.automation.AutomationUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ItemKeyUtil {

    private static final String SF_PREFIX = "slimefun:";

    private ItemKeyUtil() {}

    public static String getKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "air";

        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            return SF_PREFIX + AutomationUtils.getItemId(stack);
        }

        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static boolean isSameItem(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        return getKey(a).equals(getKey(b));
    }

    public static boolean isSlimefunItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
    }
}
