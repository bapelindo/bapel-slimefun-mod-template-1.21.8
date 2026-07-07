package com.bapel_slimefun_mod.automation.state;

import com.bapel_slimefun_mod.automation.*;
import com.bapel_slimefun_mod.automation.infra.*;

public class GridOpenStateHandler implements StateHandler {

    private final CameraController camera;
    private final GuiInteractor    guiInteractor;

    public GridOpenStateHandler(CameraController camera, GuiInteractor guiInteractor) {
        this.camera        = camera;
        this.guiInteractor = guiInteractor;
    }

    @Override
    public void onEnter(AutomationContext ctx) {
        ctx.markStateEntry();
        // Mulai putar kamera ke arah Grid
        ctx.getGridPos().ifPresent(pos -> camera.lookAt(pos));
    }

    @Override
    public AutomationResult onTick(AutomationContext ctx) {
        // Timeout guard
        if (ctx.isTimedOut(AutomationContext.TIMEOUT_GUI_OPEN)) {
            ctx.peekJob().ifPresent(CraftingJob::markGridTried);
            return AutomationResult.goTo(AutomationState.FINDING_MACHINE);
        }

        // Tunggu kamera selesai rotate
        if (!camera.isLookingAt(ctx.getGridPos().orElseThrow())) {
            return AutomationResult.stay(); // masih rotating
        }

        // Klik kanan untuk buka GUI
        if (!guiInteractor.isGridGuiOpen()) {
            guiInteractor.rightClickBlock(ctx.getGridPos().orElseThrow());
            return AutomationResult.stay();
        }

        return AutomationResult.goTo(AutomationState.SCANNING);
    }

    @Override
    public void onExit(AutomationContext ctx) {
        // Pastikan kamera kembali ke posisi bebas setelah selesai
    }
}
