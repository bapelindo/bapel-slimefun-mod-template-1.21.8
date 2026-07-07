package com.bapel_slimefun_mod.automation.state;

import com.bapel_slimefun_mod.automation.*;
import com.bapel_slimefun_mod.automation.event.*;
import com.bapel_slimefun_mod.automation.infra.*;

public class CraftingStateHandler implements StateHandler {

    private final RecipeLockExecutor recipeLockExecutor;

    public CraftingStateHandler(RecipeLockExecutor recipeLockExecutor) {
        this.recipeLockExecutor = recipeLockExecutor;
    }

    @Override
    public void onEnter(AutomationContext ctx) {
        ctx.markStateEntry();
    }

    @Override
    public AutomationResult onTick(AutomationContext ctx) {
        if (ctx.isTimedOut(AutomationContext.TIMEOUT_CRAFTING)) {
            return AutomationResult.fail("Crafting timeout");
        }

        CraftingJob job = ctx.peekJob().orElse(null);
        if (job == null) return AutomationResult.fail("No active job");

        // Kunci resep + eksekusi
        boolean started = recipeLockExecutor.lockAndExecute(job.getTargetItem());
        if (!started) {
            return AutomationResult.stay(); // GUI belum siap
        }

        AutomationEventBus.get().publish(AutomationEvent.CRAFTING_STARTED, job);
        return AutomationResult.goTo(AutomationState.VERIFYING);
    }
}
