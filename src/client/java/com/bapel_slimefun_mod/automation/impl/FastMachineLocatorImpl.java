package com.bapel_slimefun_mod.automation.impl;

import com.bapel_slimefun_mod.automation.infra.FastMachineLocator;
import com.bapel_slimefun_mod.automation.fastmachine.FastMachineRecipeMemory;
import com.bapel_slimefun_mod.automation.fastmachine.FastMachineAutomationHandler;
import com.bapel_slimefun_mod.automation.fastmachine.FastMachineDetector;
import com.bapel_slimefun_mod.automation.RecipeData;
import com.bapel_slimefun_mod.automation.RecipeDatabase;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

public class FastMachineLocatorImpl implements FastMachineLocator {

    @Override
    public Optional<BlockPos> findNearest(BlockPos origin, ItemStack targetItem, int radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return Optional.empty();
        
        String targetName = FastMachineDetector.stripColorCodes(targetItem.getHoverName().getString()).trim();
        RecipeData recipe = findRecipeByDisplayName(targetName);
        if (recipe == null) return Optional.empty();
        
        String fmId = FastMachineAutomationHandler.getFastMachineIdFromSlimefun(recipe.getMachineId());
        if (fmId == null) return Optional.empty();
        
        BlockPos pos = FastMachineRecipeMemory.getClosestPosition(fmId, mc.player.position(), mc.level);
        if (pos != null && pos.distToCenterSqr(mc.player.position()) <= radius * radius) {
            return Optional.of(pos);
        }
        return Optional.empty();
    }

    @Override
    public boolean isMachineAvailable(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        String blockId = mc.level.getBlockState(pos).getBlock().getDescriptionId();
        return FastMachineDetector.isFastMachine(blockId);
    }

    @Override
    public boolean supportsRecipe(BlockPos pos, ItemStack targetItem) {
        return isMachineAvailable(pos);
    }
    
    private RecipeData findRecipeByDisplayName(String name) {
        if (!RecipeDatabase.isInitialized()) RecipeDatabase.initialize();
        java.util.List<RecipeData> c = RecipeDatabase.searchRecipesByOutput(name);
        if (c == null || c.isEmpty()) return null;
        for (RecipeData r : c) {
            if (r.getPrimaryOutput() != null && r.getPrimaryOutput().getDisplayName().equalsIgnoreCase(name)) return r;
        }
        return c.get(0);
    }
}
