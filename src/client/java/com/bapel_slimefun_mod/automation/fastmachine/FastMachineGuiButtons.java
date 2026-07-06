// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║  FILE 8 — FastMachineGuiButtons.java  (BARU)                               ║
// ║  src/client/java/com/bapel_slimefun_mod/automation/                        ║
// ╚══════════════════════════════════════════════════════════════════════════════╝
package com.bapel_slimefun_mod.automation.fastmachine;
import com.bapel_slimefun_mod.mixin.AbstractContainerScreenAccessor;
import com.bapel_slimefun_mod.mixin.ScreenWidgetInvoker;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

/**
 * Injects a compact panel of native widgets onto the vanilla FastMachines chest
 * GUI, positioned in the empty space to the right of the container panel.
 *
 * <p>All low-frequency "configure once per session" actions (lock recipe, craft
 * mode, auto-refill, target count) live here as clickable widgets rather than
 * keybinds, for discoverability. The only remaining keybind is the emergency
 * manual pause/resume ({@code J}, see {@link com.bapel_slimefun_mod.client.ModKeybinds}).
 *
 * @author bapelindo
 */
    public final class FastMachineGuiButtons {

        private FastMachineGuiButtons() {}

        private static final int PANEL_MARGIN_X = 8;
        private static final int BUTTON_WIDTH   = 130;
        private static final int BUTTON_HEIGHT  = 20;
        private static final int ROW_SPACING    = 24;
        private static final int SMALL_BUTTON_W = 62;
        private static String lastValidTargetInput = "";

        private static void attachDigitFilter(net.minecraft.client.gui.components.EditBox targetBox) {
        targetBox.setResponder(newValue -> {
            if (newValue.isEmpty() || newValue.matches("\\d+")) {
                lastValidTargetInput = newValue;
            } else {
                // Jika input mengandung huruf, paksa revert ke teks angka terakhir yang valid
                targetBox.setValue(lastValidTargetInput);
            }
        });
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register(FastMachineGuiButtons::onScreenInit);
    }

    /**
     * Wraps {@code Screen#addRenderableWidget}, yang protected, lewat
     * {@link ScreenWidgetInvoker} karena class ini bukan subclass {@link Screen}.
     */
    private static <T extends net.minecraft.client.gui.components.events.GuiEventListener
            & net.minecraft.client.gui.components.Renderable
            & net.minecraft.client.gui.narration.NarratableEntry> T addWidget(Screen screen, T widget) {
        return ((ScreenWidgetInvoker) screen).bapel$addRenderableWidget(widget);
    }

    private static void onScreenInit(net.minecraft.client.Minecraft mc, Screen screen, int width, int height) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;

        String title = FastMachineDetector.stripColorCodes(screen.getTitle().getString());
        if (!FastMachineDetector.isFastMachine(title)) return;

        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) containerScreen;
        int panelX = accessor.bapel$getLeftPos() + accessor.bapel$getImageWidth() + PANEL_MARGIN_X;
        int panelY = accessor.bapel$getTopPos();

        buildPanel(screen, panelX, panelY);
    }

    private static void buildPanel(Screen screen, int x, int y) {
        String machineId = FastMachineAutomationHandler.getCurrentMachineId();
        FastMachineRecipeMemory.RecipeEntry entry = (machineId != null)
                ? FastMachineRecipeMemory.getOrCreate(machineId)
                : new FastMachineRecipeMemory.RecipeEntry();

        int row = 0;

        String lockLabel = (FastMachineAutomationHandler.getLockedRecipeName() != null)
                ? "§a🔒 " + truncate(FastMachineAutomationHandler.getLockedRecipeName(), 14)
                : "§7🔓 Lock Recipe";
        addWidget(screen, Button.builder(Component.literal(lockLabel), btn -> {
                    FastMachineAutomationHandler.lockCurrentRecipe();
                    refreshPanel(screen, x, y);
                })
                .bounds(x, y + row * ROW_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("Click a recipe preview first, then click here to lock it.")))
                .build());
        row++;

        addWidget(screen, Button.builder(Component.literal("§b⚙ Mode: §f" + entry.craftMode.label), btn -> {
                    FastMachineAutomationHandler.cycleCraftMode();
                    refreshPanel(screen, x, y);
                })
                .bounds(x, y + row * ROW_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("Cycles: ×1 → ×16 → ×64 → ×MAX per craft click.")))
                .build());
        row++;

        String refillLabel = entry.autoRefill ? "§a♻ Refill: ON" : "§7♻ Refill: OFF";
        addWidget(screen, Button.builder(Component.literal(refillLabel), btn -> {
                    FastMachineAutomationHandler.toggleAutoRefill();
                    refreshPanel(screen, x, y);
                })
                .bounds(x, y + row * ROW_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("Auto-move ingredients from inventory into the machine.")))
                .build());
        row++;

        EditBox targetBox = new EditBox(
                net.minecraft.client.Minecraft.getInstance().font,
                x, y + row * ROW_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal("Target"));
        targetBox.setMaxLength(9);
        attachDigitFilter(targetBox);
        targetBox.setValue(entry.targetCount > 0 ? String.valueOf(entry.targetCount) : "");
        targetBox.setHint(Component.literal("§7Target qty..."));
        addWidget(screen, targetBox);
        row++;

        addWidget(screen, Button.builder(Component.literal("§a✔ Set"), btn -> {
                    String raw = targetBox.getValue().trim();
                    if (!raw.isEmpty()) {
                        try { FastMachineAutomationHandler.setTargetCount(Integer.parseInt(raw)); }
                        catch (NumberFormatException ignored) {}
                    }
                    refreshPanel(screen, x, y);
                })
                .bounds(x, y + row * ROW_SPACING, SMALL_BUTTON_W, BUTTON_HEIGHT)
                .build());

        addWidget(screen, Button.builder(Component.literal("§c✗ Clear"), btn -> {
                    FastMachineAutomationHandler.clearTargetCount();
                    refreshPanel(screen, x, y);
                })
                .bounds(x + SMALL_BUTTON_W + 6, y + row * ROW_SPACING, SMALL_BUTTON_W, BUTTON_HEIGHT)
                .build());
        row++;

        if (entry.targetCount > 0) {
            Button progress = Button.builder(
                    Component.literal("§d📦 " + entry.craftedSinceTarget + " / " + entry.targetCount),
                    btn -> {})
                .bounds(x, y + row * ROW_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
            progress.active = false;
            addWidget(screen, progress);
        }
    }

    /**
     * Rebuilds the panel by removing previously-added widgets in our panel's
     * horizontal region (identified by X coordinate ≥ panel anchor) and
     * re-adding them with fresh state — necessary since {@link Button} labels
     * are immutable {@link Component}s set at construction time.
     */
    private static void refreshPanel(Screen screen, int x, int y) {
        screen.children().removeIf(child ->
            child instanceof AbstractWidget widget && widget.getX() >= x);
        buildPanel(screen, x, y);
    }

    private static String truncate(String s, int maxLen) {
        return (s.length() <= maxLen) ? s : s.substring(0, maxLen - 1) + "…";
    }
}