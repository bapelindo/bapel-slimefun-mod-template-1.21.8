package com.bapel_slimefun_mod.automation.hud;

import com.bapel_slimefun_mod.automation.AutomationContext;
import com.bapel_slimefun_mod.automation.AutomationStateMachine;
import com.bapel_slimefun_mod.automation.CraftingJob;
import com.bapel_slimefun_mod.automation.event.AutomationEvent;
import com.bapel_slimefun_mod.automation.event.AutomationEventBus;
import com.bapel_slimefun_mod.automation.state.AutomationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**  
 * Renders automation status overlay di pojok kiri atas layar.  
 */  
public class AutomationHudRenderer {  

    private static final int COLOR_BG          = 0xAA000000;  
    private static final int COLOR_TITLE        = 0xFFFFD700; // gold  
    private static final int COLOR_STATE_ACTIVE = 0xFF55FF55; // green  
    private static final int COLOR_STATE_WAIT   = 0xFFFFAA00; // orange  
    private static final int COLOR_STATE_ERROR  = 0xFFFF5555; // red  
    private static final int COLOR_JOB          = 0xFFAAAAAA; // gray  
    private static final int COLOR_DEPTH        = 0xFF5555FF; // blue  

    private static final int PADDING  = 4;  
    private static final int LINE_H   = 10;  
    private static final int X_OFFSET = 4;  
    private static final int Y_OFFSET = 120; // Lower than fastmachine HUD to avoid overlaying

    private final AutomationStateMachine stateMachine;  
    private final AutomationContext      ctx;  

    private volatile String lastError  = null;  
    private volatile long   errorTimer = 0L;  

    public AutomationHudRenderer(AutomationStateMachine stateMachine, AutomationContext ctx) {  
        this.stateMachine = stateMachine;  
        this.ctx          = ctx;  

        AutomationEventBus.get().subscribe(AutomationEvent.CHAIN_FAILED, reason -> {  
            lastError  = (String) reason;  
            errorTimer = System.currentTimeMillis();  
        });  
    }  

    public void render(GuiGraphicsExtractor graphics) {  
        if (!com.bapel_slimefun_mod.automation.config.AutomationConfig.get().showHudOverlay) return;

        Minecraft mc = Minecraft.getInstance();  

        if (mc.screen != null) return;  
        if (!stateMachine.isRunning() && lastError == null) return;  

        Font tr = mc.font;  
        AutomationState state = stateMachine.getCurrentState();  

        List<HudLine> lines = new ArrayList<>();  

        lines.add(new HudLine("⚙ AutoCraft", COLOR_TITLE));  

        lines.add(new HudLine(  
            "▶ " + formatState(state),  
            stateColor(state)  
        ));  

        ctx.peekJob().ifPresent(job -> {  
            lines.add(new HudLine(  
                "  Item : " + shortKey(job.getItemKey()),  
                COLOR_JOB  
            ));  
            lines.add(new HudLine(  
                "  Amt  : " + job.getAmountNeeded(),  
                COLOR_JOB  
            ));  
        });  

        if (ctx.getDepth() > 0) {  
            lines.add(new HudLine(  
                "  Depth: " + ctx.getDepth() + " / 8",  
                COLOR_DEPTH  
            ));  
        }  

        if (lastError != null) {  
            if (System.currentTimeMillis() - errorTimer < 5000) {  
                lines.add(new HudLine("✗ " + lastError, COLOR_STATE_ERROR));  
            } else {  
                lastError = null;  
            }  
        }  

        int maxWidth = 0;
        for (HudLine l : lines) {
            maxWidth = Math.max(maxWidth, tr.width(l.text()));
        }

        int bgW = maxWidth  + PADDING * 2;  
        int bgH = lines.size() * LINE_H + PADDING * 2;  

        graphics.fill(  
            X_OFFSET,  
            Y_OFFSET,  
            X_OFFSET + bgW,  
            Y_OFFSET + bgH,  
            COLOR_BG  
        );  

        for (int i = 0; i < lines.size(); i++) {  
            HudLine line = lines.get(i);  
            graphics.text(  
                tr,  
                line.text(),  
                X_OFFSET + PADDING,  
                Y_OFFSET + PADDING + i * LINE_H,  
                line.color(),  
                false  
            );  
        }  
    }  

    private String formatState(AutomationState state) {  
        return switch (state) {  
            case IDLE           -> "Idle";  
            case FINDING_GRID   -> "Mencari Grid...";  
            case GRID_OPEN      -> "Membuka Grid...";  
            case SCANNING       -> "Scanning Grid (hal. " + ctx.getGridPage() + ")";  
            case FINDING_MACHINE-> "Mencari Mesin...";  
            case MACHINE_OPEN   -> "Membuka Mesin...";  
            case CRAFTING       -> "Crafting...";  
            case VERIFYING      -> "Verifikasi Output...";  
            case DONE           -> "Selesai ✓";  
            case ERROR          -> "ERROR";  
        };  
    }  

    private int stateColor(AutomationState state) {  
        return switch (state) {  
            case ERROR          -> COLOR_STATE_ERROR;  
            case DONE, IDLE     -> COLOR_STATE_ACTIVE;  
            default             -> COLOR_STATE_WAIT;  
        };  
    }  

    private String shortKey(String itemKey) {  
        int colon = itemKey.lastIndexOf(':');  
        return colon >= 0 ? itemKey.substring(colon + 1) : itemKey;  
    }  

    private record HudLine(String text, int color) {}  
}
