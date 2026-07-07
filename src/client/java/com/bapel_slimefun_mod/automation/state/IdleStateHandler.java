package com.bapel_slimefun_mod.automation.state;

import com.bapel_slimefun_mod.automation.*;

public class IdleStateHandler implements StateHandler {

    @Override
    public void onEnter(AutomationContext ctx) {
        ctx.reset();
    }

    @Override
    public AutomationResult onTick(AutomationContext ctx) {
        return AutomationResult.stay();
    }
}
