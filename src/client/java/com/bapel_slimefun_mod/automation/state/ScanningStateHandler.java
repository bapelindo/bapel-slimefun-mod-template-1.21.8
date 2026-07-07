package com.bapel_slimefun_mod.automation.state;

import com.bapel_slimefun_mod.automation.*;
import com.bapel_slimefun_mod.automation.event.*;
import com.bapel_slimefun_mod.automation.infra.*;

public class ScanningStateHandler implements StateHandler {

    private final GridScanner gridScanner;
    private final GuiInteractor guiInteractor;

    public ScanningStateHandler(GridScanner gridScanner, GuiInteractor guiInteractor) {
        this.gridScanner   = gridScanner;
        this.guiInteractor = guiInteractor;
    }

    @Override
    public void onEnter(AutomationContext ctx) {
        ctx.markStateEntry();
    }

    @Override
    public AutomationResult onTick(AutomationContext ctx) {
        // Timeout per halaman
        if (ctx.isTimedOut(AutomationContext.TIMEOUT_SCAN_PAGE)) {
            closeAndProceed(ctx);
            return AutomationResult.goTo(AutomationState.FINDING_MACHINE);
        }

        // Batas halaman
        if (ctx.isGridPageExhausted()) {
            closeAndProceed(ctx);
            return AutomationResult.goTo(AutomationState.FINDING_MACHINE);
        }

        CraftingJob job = ctx.peekJob().orElse(null);
        if (job == null) return AutomationResult.fail("No active job during scan");

        // Cek kebutuhan bahan
        int missing = gridScanner.getMissingAmount(job);
        if (missing <= 0) {
            // Semua bahan tersedia!
            closeAndProceed(ctx);
            AutomationEventBus.get().publish(AutomationEvent.GRID_SCAN_COMPLETE);
            return AutomationResult.goTo(AutomationState.FINDING_MACHINE);
        }

        // Shift-click tarik bahan dari halaman ini
        boolean pageHasItem = gridScanner.shiftClickMaterials(job, ctx.getGridPage());
        if (!pageHasItem || gridScanner.isLastPage(ctx.getGridPage())) {
            closeAndProceed(ctx);
            return AutomationResult.goTo(AutomationState.FINDING_MACHINE);
        }

        // Lanjut halaman berikutnya
        ctx.nextGridPage();
        ctx.markStateEntry(); // reset timer per halaman
        return AutomationResult.stay();
    }

    private void closeAndProceed(AutomationContext ctx) {
        guiInteractor.closeCurrentGui();
        ctx.peekJob().ifPresent(CraftingJob::markGridTried);
    }
}
