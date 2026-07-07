package com.bapel_slimefun_mod.automation.state;

import com.bapel_slimefun_mod.automation.*;
import com.bapel_slimefun_mod.automation.event.*;
import com.bapel_slimefun_mod.automation.infra.*;

public class VerifyingStateHandler implements StateHandler {

    private final InventoryChecker inventoryChecker;
    private final GuiInteractor    guiInteractor;

    // Snapshot inventory sebelum crafting
    private int inventoryBefore = 0;

    public VerifyingStateHandler(InventoryChecker inventoryChecker, GuiInteractor guiInteractor) {
        this.inventoryChecker = inventoryChecker;
        this.guiInteractor    = guiInteractor;
    }

    @Override
    public void onEnter(AutomationContext ctx) {
        ctx.markStateEntry();
        // Ambil snapshot SEBELUM crafting
        ctx.peekJob().ifPresent(job ->  
            inventoryBefore = inventoryChecker.countInInventory(job.getTargetItem())
        );
    }

    @Override
    public AutomationResult onTick(AutomationContext ctx) {
        if (ctx.isTimedOut(AutomationContext.TIMEOUT_CRAFTING)) {
            AutomationEventBus.get().publish(AutomationEvent.CRAFTING_TIMEOUT);
            return AutomationResult.fail("Output verification timeout");
        }

        CraftingJob job = ctx.peekJob().orElse(null);
        if (job == null) return AutomationResult.fail("No active job during verify");

        int currentCount = inventoryChecker.countInInventory(job.getTargetItem());

        // Output belum masuk inventory
        if (currentCount <= inventoryBefore) {
            return AutomationResult.stay();
        }

        // Output berhasil dikonfirmasi ✅
        AutomationEventBus.get().publish(AutomationEvent.CRAFTING_VERIFIED, job);
        guiInteractor.closeCurrentGui();
        return AutomationResult.goTo(AutomationState.DONE);
    }
}
