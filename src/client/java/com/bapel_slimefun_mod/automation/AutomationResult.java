package com.bapel_slimefun_mod.automation;

/**
 * Result dari setiap tick handler.
 * Membawa sinyal: tetap di state ini, pindah ke state lain, atau error.
 */
public sealed interface AutomationResult
    permits AutomationResult.Stay,
            AutomationResult.Transition,
            AutomationResult.Fail {

    /** Tetap di state saat ini, tunggu tick berikutnya */
    record Stay() implements AutomationResult {}

    /** Pindah ke state target */
    record Transition(com.bapel_slimefun_mod.automation.state.AutomationState nextState)
        implements AutomationResult {}

    /** Gagal dengan pesan error */
    record Fail(String reason) implements AutomationResult {}

    // ── Factory helpers ───────────────────────────────────────────
    static AutomationResult stay()                    { return new Stay(); }
    static AutomationResult goTo(com.bapel_slimefun_mod.automation.state.AutomationState s) { return new Transition(s); }
    static AutomationResult fail(String reason)       { return new Fail(reason); }
}
