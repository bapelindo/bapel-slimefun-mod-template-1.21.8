package com.bapel_slimefun_mod.automation.state;

import com.bapel_slimefun_mod.automation.AutomationContext;
import com.bapel_slimefun_mod.automation.AutomationResult;

/**
 * Setiap state punya handler-nya sendiri.
 * onEnter  → dipanggil sekali saat state pertama kali dimasuki
 * onTick   → dipanggil setiap tick selama state aktif
 * onExit   → dipanggil sekali saat state akan meninggalkan
 */
public interface StateHandler {
    default void onEnter(AutomationContext ctx) {}
    AutomationResult onTick(AutomationContext ctx);
    default void onExit(AutomationContext ctx) {}
}
