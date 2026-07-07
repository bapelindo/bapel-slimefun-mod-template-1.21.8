package com.bapel_slimefun_mod.automation.impl;

import com.bapel_slimefun_mod.automation.infra.NetworkGridLocator;
import com.bapel_slimefun_mod.automation.fastmachine.FastMachineRecipeMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Optional;

public class NetworkGridLocatorImpl implements NetworkGridLocator {

    @Override
    public Optional<BlockPos> findNearest(BlockPos origin, int radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return Optional.empty();
        
        // optimize lookup by checking closest cached grid position
        BlockPos pos = FastMachineRecipeMemory.getClosestPosition("NETWORK_GRID", mc.player.position(), mc.level);
        if (pos != null && pos.distToCenterSqr(mc.player.position()) <= radius * radius) {
            return Optional.of(pos);
        }
        return Optional.empty();
    }

    @Override
    public boolean isGridActive(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        
        String blockId = mc.level.getBlockState(pos).getBlock().getDescriptionId().toLowerCase();
        if (!blockId.contains("network_grid") && !blockId.contains("grid") && !blockId.contains("terminal")) return false;
        
        BlockEntity be = mc.level.getBlockEntity(pos);
        return be != null;
    }
}
