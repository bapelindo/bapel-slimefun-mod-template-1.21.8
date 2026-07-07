package com.bapel_slimefun_mod.automation;

import net.minecraft.world.item.ItemStack;

public final class CraftingJob {

    private final String    itemKey;       // "slimefun:COPPER_DUST" + NBT hash
    private final ItemStack targetItem;
    private final int       amountNeeded;
    private final int       depth;

    // Per-job Grid flag (BUKAN global!)
    private boolean triedGrid = false;

    public CraftingJob(String itemKey, ItemStack targetItem, int amountNeeded, int depth) {
        this.itemKey      = itemKey;
        this.targetItem   = targetItem;
        this.amountNeeded = amountNeeded;
        this.depth        = depth;
    }

    public String    getItemKey()      { return itemKey; }
    public ItemStack getTargetItem()   { return targetItem; }
    public int       getAmountNeeded() { return amountNeeded; }
    public int       getDepth()        { return depth; }

    public boolean   isTriedGrid()     { return triedGrid; }
    public void      markGridTried()   { triedGrid = true; }

    @Override
    public String toString() {
        return String.format("CraftingJob{item=%s, amount=%d, depth=%d, triedGrid=%b}",
            itemKey, amountNeeded, depth, triedGrid);
    }
}
