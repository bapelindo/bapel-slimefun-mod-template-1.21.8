package com.bapel_slimefun_mod.automation.guard;

import com.bapel_slimefun_mod.automation.state.AutomationState;

public interface TransitionGuard {
    boolean isValid(AutomationState from, AutomationState to);
}
