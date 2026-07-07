package com.bapel_slimefun_mod.automation.state;

import com.bapel_slimefun_mod.automation.*;
import com.bapel_slimefun_mod.automation.event.*;

public class DoneStateHandler implements StateHandler {

    @Override
    public void onEnter(AutomationContext ctx) {}

    @Override
    public AutomationResult onTick(AutomationContext ctx) {
        // Pop job yang sudah selesai
        ctx.popJob().ifPresent(completedJob ->  
            AutomationEventBus.get().publish(AutomationEvent.JOB_COMPLETED, completedJob)
        );  

        if (ctx.hasJobs()) {
            // Masih ada sub-job → lanjut ke FINDING_MACHINE
            return AutomationResult.goTo(AutomationState.FINDING_MACHINE);
        }

        // Semua selesai!
        AutomationEventBus.get().publish(AutomationEvent.CHAIN_COMPLETED);
        return AutomationResult.goTo(AutomationState.IDLE);
    }
}
