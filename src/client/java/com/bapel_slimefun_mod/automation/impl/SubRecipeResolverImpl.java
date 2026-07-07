package com.bapel_slimefun_mod.automation.impl;

import com.bapel_slimefun_mod.automation.CraftingJob;
import com.bapel_slimefun_mod.automation.infra.SubRecipeResolver;
import com.bapel_slimefun_mod.automation.infra.InventoryChecker;
import com.bapel_slimefun_mod.automation.recipe.*;
import com.bapel_slimefun_mod.automation.util.ItemKeyUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SubRecipeResolverImpl implements SubRecipeResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger("SubRecipeResolver");
    private static final int    MAX_DEPTH = 8;

    private final InventoryChecker inventoryChecker;
    private final SlimefunRecipeRegistry registry;

    // Track "reserved" bahan oleh sub-job yang pending
    // itemKey → total reserved amount
    private final Map<String, Integer> reservedMaterials = new ConcurrentHashMap<>();

    public SubRecipeResolverImpl(InventoryChecker inventoryChecker) {
        this.inventoryChecker = inventoryChecker;
        this.registry         = SlimefunRecipeRegistry.get();
    }

    // ─────────────────────────────────────────────────────────────

    @Override
    public int getMissingAmount(CraftingJob job) {
        String itemKey  = job.getItemKey();
        int    needed   = job.getAmountNeeded();
        int    inInv    = inventoryChecker.countInInventory(job.getTargetItem());
        int    reserved = getReservedAmount(itemKey);

        int available = Math.max(0, inInv - reserved);
        int missing   = Math.max(0, needed - available);

        LOGGER.debug("Missing check: {} | needed={} inInv={} reserved={} missing={}",
            itemKey, needed, inInv, reserved, missing);

        return missing;
    }

    @Override
    public Optional<CraftingJob> resolveSubJob(CraftingJob parentJob, int currentDepth) {
        if (currentDepth >= MAX_DEPTH) {
            LOGGER.warn("Max depth {} reached, cannot resolve sub-job for: {}",
                MAX_DEPTH, parentJob.getItemKey());
            return Optional.empty();
        }

        Optional<RecipeEntry> parentRecipe = registry.getBestRecipe(parentJob.getItemKey());
        if (parentRecipe.isEmpty()) return Optional.empty();

        for (RecipeIngredient ingredient : parentRecipe.get().ingredients()) {
            int missing = calculateIngredientMissing(ingredient, parentJob);
            if (missing <= 0) continue;

            Optional<RecipeEntry> subRecipe = registry.getBestRecipe(ingredient.itemKey());
            if (subRecipe.isEmpty()) continue;

            RecipeEntry recipe    = subRecipe.get();
            int craftCount        = recipe.craftCountNeeded(missing);
            int totalOutput       = craftCount * recipe.outputAmount();

            reserveMaterial(ingredient.itemKey(), totalOutput);

            CraftingJob subJob = new CraftingJob(
                ingredient.itemKey(),
                recipe.outputItem(),
                totalOutput,
                currentDepth + 1
            );

            LOGGER.info("Resolved sub-job: {} x{} (depth {})",
                ingredient.itemKey(), totalOutput, currentDepth + 1);

            return Optional.of(subJob);
        }

        return Optional.empty();
    }

    @Override
    public int getReservedAmount(String itemKey) {
        return reservedMaterials.getOrDefault(itemKey, 0);
    }

    // ─────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────

    private int calculateIngredientMissing(RecipeIngredient ingredient, CraftingJob parentJob) {
        Optional<RecipeEntry> parentRecipe = registry.getBestRecipe(parentJob.getItemKey());
        if (parentRecipe.isEmpty()) return 0;

        int craftCount = parentRecipe.get().craftCountNeeded(parentJob.getAmountNeeded());
        int totalNeeded = ingredient.amount() * craftCount;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return totalNeeded;

        int inInv = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && ItemKeyUtil.getKey(stack).equals(ingredient.itemKey())) {
                inInv += stack.getCount();
            }
        }

        int reserved = getReservedAmount(ingredient.itemKey());
        int available = Math.max(0, inInv - reserved);

        return Math.max(0, totalNeeded - available);
    }

    private void reserveMaterial(String itemKey, int amount) {
        reservedMaterials.merge(itemKey, amount, Integer::sum);
    }

    /**  
     * Dipanggil saat job selesai untuk release reserved materials.  
     */  
    public void releaseReservation(String itemKey, int amount) {
        reservedMaterials.compute(itemKey, (k, current) -> {
            if (current == null) return null;
            int newVal = current - amount;
            return newVal <= 0 ? null : newVal;
        });
    }

    public void clearAllReservations() {
        reservedMaterials.clear();
    }
}
