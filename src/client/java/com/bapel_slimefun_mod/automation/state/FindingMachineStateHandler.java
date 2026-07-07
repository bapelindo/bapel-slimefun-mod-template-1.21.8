package com.bapel_slimefun_mod.automation.state;

import com.bapel_slimefun_mod.automation.*;
import com.bapel_slimefun_mod.automation.event.*;
import com.bapel_slimefun_mod.automation.infra.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import java.util.Optional;

public class FindingMachineStateHandler implements StateHandler {

    private final FastMachineLocator machineLocator;
    private final SubRecipeResolver  subRecipeResolver;

    public FindingMachineStateHandler(
        FastMachineLocator machineLocator,
        SubRecipeResolver  subRecipeResolver
    ) {
        this.machineLocator    = machineLocator;
        this.subRecipeResolver = subRecipeResolver;
    }

    @Override
    public void onEnter(AutomationContext ctx) {
        ctx.markStateEntry();
        ctx.clearMachinePos();
    }

    @Override
    public AutomationResult onTick(AutomationContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return AutomationResult.fail("Player is null");

        CraftingJob job = ctx.peekJob().orElse(null);
        if (job == null) return AutomationResult.fail("No active job");

        // Cek apakah bahan masih kurang
        int missing = subRecipeResolver.getMissingAmount(job);
        if (missing > 0) {
            // Bahan kurang → coba sub-recipe dulu
            Optional<CraftingJob> subJob = subRecipeResolver.resolveSubJob(
                job, ctx.getDepth()
            );

            if (subJob.isPresent()) {
                boolean pushed = ctx.pushJob(subJob.get());
                if (!pushed) {
                    return AutomationResult.fail(
                        "Max depth or circular recipe detected for: " + subJob.get().getItemKey()
                    );
                }
                AutomationEventBus.get().publish(AutomationEvent.JOB_PUSHED, subJob.get());
                // Restart loop dari awal dengan sub-job baru
                return AutomationResult.goTo(AutomationState.FINDING_GRID);
            }

            // Tidak bisa dibuat di mesin manapun
            return AutomationResult.fail("Cannot craft missing material: " + job.getItemKey());
        }

        // Bahan cukup! Cari mesin
        Optional<BlockPos> machinePos = machineLocator.findNearest(
            mc.player.blockPosition(),
            job.getTargetItem(),
            ctx.getMachineRadius()
        );

        if (machinePos.isEmpty()) {
            AutomationEventBus.get().publish(AutomationEvent.MACHINE_NOT_FOUND);
            return AutomationResult.fail("No FastMachine found for: " + job.getItemKey());
        }

        ctx.setMachinePos(machinePos.get());
        AutomationEventBus.get().publish(AutomationEvent.MACHINE_FOUND, machinePos.get());
        return AutomationResult.goTo(AutomationState.MACHINE_OPEN);
    }
}
