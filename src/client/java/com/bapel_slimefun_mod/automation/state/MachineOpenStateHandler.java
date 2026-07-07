package com.bapel_slimefun_mod.automation.state;

import com.bapel_slimefun_mod.automation.*;
import com.bapel_slimefun_mod.automation.infra.*;

public class MachineOpenStateHandler implements StateHandler {

    private final CameraController camera;
    private final GuiInteractor    guiInteractor;

    public MachineOpenStateHandler(CameraController camera, GuiInteractor guiInteractor) {
        this.camera        = camera;
        this.guiInteractor = guiInteractor;
    }

    @Override
    public void onEnter(AutomationContext ctx) {
        ctx.markStateEntry();
        ctx.getMachinePos().ifPresent(pos -> camera.lookAt(pos));
    }

    @Override
    public AutomationResult onTick(AutomationContext ctx) {
        if (ctx.isTimedOut(AutomationContext.TIMEOUT_GUI_OPEN)) {
            return AutomationResult.fail("Machine GUI open timeout");
        }

        if (!camera.isLookingAt(ctx.getMachinePos().orElseThrow())) {
            return AutomationResult.stay();
        }

        if (!guiInteractor.isMachineGuiOpen()) {
            guiInteractor.rightClickBlock(ctx.getMachinePos().orElseThrow());
            return AutomationResult.stay();
        }

        return AutomationResult.goTo(AutomationState.CRAFTING);
    }
}
