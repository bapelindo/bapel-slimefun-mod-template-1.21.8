package com.bapel_slimefun_mod.automation.state;

import java.util.Set;

public enum AutomationState {

    IDLE("idle", false),
    FINDING_GRID("finding_grid", true),
    GRID_OPEN("grid_open", true),
    SCANNING("scanning", true),
    FINDING_MACHINE("finding_machine", true),
    MACHINE_OPEN("machine_open", true),
    CRAFTING("crafting", true),
    VERIFYING("verifying", true),
    DONE("done", false),
    ERROR("error", false);

    private final String id;
    private final boolean hasTimeout;
    private Set<AutomationState> allowedPreviousStates;

    AutomationState(String id, boolean hasTimeout) {
        this.id = id;
        this.hasTimeout = hasTimeout;
    }

    public String getId() { return id; }
    public boolean hasTimeout() { return hasTimeout; }

    static {
        IDLE.allowedPreviousStates = Set.of();
        FINDING_GRID.allowedPreviousStates = Set.of(IDLE, SCANNING, FINDING_MACHINE);
        GRID_OPEN.allowedPreviousStates = Set.of(FINDING_GRID);
        SCANNING.allowedPreviousStates = Set.of(GRID_OPEN);
        FINDING_MACHINE.allowedPreviousStates = Set.of(IDLE, SCANNING, FINDING_GRID, DONE);
        MACHINE_OPEN.allowedPreviousStates = Set.of(FINDING_MACHINE);
        CRAFTING.allowedPreviousStates = Set.of(MACHINE_OPEN);
        VERIFYING.allowedPreviousStates = Set.of(CRAFTING);
        DONE.allowedPreviousStates = Set.of(VERIFYING);
        ERROR.allowedPreviousStates = Set.of();
    }

    /**
     * Validasi transisi legal atau tidak.
     * ERROR selalu boleh dicapai dari state manapun.
     */
    public boolean canTransitionFrom(AutomationState previous) {
        if (this == ERROR) return true;
        if (this == IDLE) return true;
        return allowedPreviousStates.contains(previous);
    }
}
