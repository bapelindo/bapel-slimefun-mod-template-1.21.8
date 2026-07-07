package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.automation.event.*;
import com.bapel_slimefun_mod.automation.state.*;
import com.bapel_slimefun_mod.automation.infra.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core state machine yang dijalankan setiap client tick.
 */
public final class AutomationStateMachine {

    private static final Logger LOGGER = LoggerFactory.getLogger("AutomationSM");

    private AutomationState                           currentState;
    private final AutomationContext                   ctx;
    private final Map<AutomationState, StateHandler>  handlers;
    private StateHandler                              activeHandler;

    // Singleton / Global reference
    private static AutomationStateMachine instance = null;

    public static synchronized AutomationStateMachine getGlobalInstance() {
        return instance;
    }

    public static synchronized void setGlobalInstance(AutomationStateMachine sm) {
        instance = sm;
    }

    // ─────────────────────────────────────────────────────────────

    private AutomationStateMachine(
        AutomationContext ctx,
        Map<AutomationState, StateHandler> handlers
    ) {
        this.ctx          = ctx;
        this.handlers     = Collections.unmodifiableMap(handlers);
        this.currentState = AutomationState.IDLE;
        this.activeHandler = handlers.get(AutomationState.IDLE);
    }

    public AutomationContext getContext() {
        return ctx;
    }

    /**
     * Trigger chain baru. Aman dipanggil saat chain sedang berjalan
     * (akan cancel chain lama otomatis).
     */
    public void start(CraftingJob initialJob) {
        if (isRunning()) {
            LOGGER.warn("Cancelling existing chain before starting new one");
            cancelChain();
        }

        boolean pushed = ctx.pushJob(initialJob);
        if (!pushed) {
            LOGGER.warn("Failed to push initial job: {}", initialJob);
            return;
        }

        AutomationEventBus.get().publish(AutomationEvent.CHAIN_STARTED, initialJob);
        transitionTo(AutomationState.FINDING_GRID);
    }

    public void cancelChain() {
        ctx.reset();
        transitionTo(AutomationState.IDLE);
    }

    public boolean isRunning() {
        return currentState != AutomationState.IDLE
            && currentState != AutomationState.ERROR;
    }

    public AutomationState getCurrentState() { return currentState; }

    // ─────────────────────────────────────────────────────────────
    // Tick (dipanggil setiap client tick)
    // ─────────────────────────────────────────────────────────────

    public void onTick() {
        if (currentState == AutomationState.IDLE) return;

        AutomationResult result = activeHandler.onTick(ctx);

        switch (result) {
            case AutomationResult.Stay ignored -> { /* tetap di state ini */ }

            case AutomationResult.Transition t -> transitionTo(t.nextState());

            case AutomationResult.Fail f -> {
                LOGGER.error("[AutomationSM] FAILED at {}: {}", currentState, f.reason());
                AutomationEventBus.get().publish(AutomationEvent.CHAIN_FAILED, f.reason());
                ctx.reset();
                transitionTo(AutomationState.ERROR);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Transition
    // ─────────────────────────────────────────────────────────────

    private void transitionTo(AutomationState nextState) {
        if (!nextState.canTransitionFrom(currentState)) {
            LOGGER.error("ILLEGAL transition: {} → {}", currentState, nextState);
            return;
        }

        LOGGER.debug("State: {} → {}", currentState, nextState);

        if (activeHandler != null) {
            activeHandler.onExit(ctx);
        }

        AutomationState prev = currentState;
        currentState  = nextState;
        activeHandler = handlers.get(nextState);

        if (activeHandler != null) {
            activeHandler.onEnter(ctx);
        }

        AutomationEventBus.get().publish(AutomationEvent.STATE_CHANGED,
            Map.of("from", prev, "to", nextState)
        );
    }

    // ─────────────────────────────────────────────────────────────
    // Builder / Factory
    // ─────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private NetworkGridLocator  gridLocator;
        private FastMachineLocator  machineLocator;
        private SubRecipeResolver   subRecipeResolver;
        private RecipeLockExecutor  recipeLockExecutor;
        private InventoryChecker    inventoryChecker;
        private CameraController    cameraController;
        private GuiInteractor       guiInteractor;
        private GridScanner         gridScanner;

        public Builder gridLocator(NetworkGridLocator v)      { gridLocator = v;       return this; }
        public Builder machineLocator(FastMachineLocator v)   { machineLocator = v;    return this; }
        public Builder subRecipeResolver(SubRecipeResolver v) { subRecipeResolver = v; return this; }
        public Builder recipeLockExecutor(RecipeLockExecutor v){ recipeLockExecutor = v; return this; }
        public Builder inventoryChecker(InventoryChecker v)   { inventoryChecker = v;  return this; }
        public Builder cameraController(CameraController v)   { cameraController = v;  return this; }
        public Builder guiInteractor(GuiInteractor v)         { guiInteractor = v;     return this; }
        public Builder gridScanner(GridScanner v)             { gridScanner = v;       return this; }

        public AutomationStateMachine build() {
            AutomationContext ctx = new AutomationContext();

            Map<AutomationState, StateHandler> handlers = new EnumMap<>(AutomationState.class);
            handlers.put(AutomationState.IDLE,            new IdleStateHandler());
            handlers.put(AutomationState.FINDING_GRID,    new FindingGridStateHandler(gridLocator));
            handlers.put(AutomationState.GRID_OPEN,       new GridOpenStateHandler(cameraController, guiInteractor));
            handlers.put(AutomationState.SCANNING,        new ScanningStateHandler(gridScanner, guiInteractor));
            handlers.put(AutomationState.FINDING_MACHINE, new FindingMachineStateHandler(machineLocator, subRecipeResolver));
            handlers.put(AutomationState.MACHINE_OPEN,    new MachineOpenStateHandler(cameraController, guiInteractor));
            handlers.put(AutomationState.CRAFTING,        new CraftingStateHandler(recipeLockExecutor));
            handlers.put(AutomationState.VERIFYING,       new VerifyingStateHandler(inventoryChecker, guiInteractor));
            handlers.put(AutomationState.DONE,            new DoneStateHandler());

            return new AutomationStateMachine(ctx, handlers);
        }
    }
}
