package com.bapel_slimefun_mod.automation.state;

import com.bapel_slimefun_mod.automation.*;
import com.bapel_slimefun_mod.automation.event.*;
import com.bapel_slimefun_mod.automation.infra.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import java.util.Optional;

public class FindingGridStateHandler implements StateHandler {

    private final NetworkGridLocator gridLocator;

    public FindingGridStateHandler(NetworkGridLocator gridLocator) {
        this.gridLocator = gridLocator;
    }

    @Override
    public void onEnter(AutomationContext ctx) {
        ctx.markStateEntry();
        ctx.clearGridPos();
        ctx.resetGridPage();
    }

    @Override
    public AutomationResult onTick(AutomationContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return AutomationResult.fail("Player is null");

        // Cek apakah job saat ini sudah mencoba grid
        CraftingJob job = ctx.peekJob().orElse(null);
        if (job == null) return AutomationResult.fail("No active job");
        if (job.isTriedGrid()) {
            // Sudah pernah coba, langsung ke FINDING_MACHINE
            return AutomationResult.goTo(AutomationState.FINDING_MACHINE);
        }

        // Cari Grid terdekat
        Optional<BlockPos> gridPos = gridLocator.findNearest(
            mc.player.blockPosition(),
            ctx.getGridRadius()
        );

        if (gridPos.isEmpty()) {
            job.markGridTried();
            AutomationEventBus.get().publish(AutomationEvent.GRID_NOT_FOUND);
            return AutomationResult.goTo(AutomationState.FINDING_MACHINE);
        }

        ctx.setGridPos(gridPos.get());
        AutomationEventBus.get().publish(AutomationEvent.GRID_FOUND, gridPos.get());
        return AutomationResult.goTo(AutomationState.GRID_OPEN);
    }
}
