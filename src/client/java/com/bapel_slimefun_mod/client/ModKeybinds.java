package com.bapel_slimefun_mod.client;

import com.bapel_slimefun_mod.automation.UnifiedAutomationManager;
import com.bapel_slimefun_mod.automation.fastmachine.FastMachineAutomationHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Registers and dispatches all FastMachine automation keybinds.
 */
public final class ModKeybinds {

    private ModKeybinds() {}

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("bapel-slimefun-mod", "automation")
    );

    /** The single registered keybind — physically bound to {@code J} by default. */
    public static KeyMapping FASTMACHINE_KEY;

    /** The registered keybind for pausing/resuming FastMachine automation — physically bound to {@code O} by default. */
    public static KeyMapping FASTMACHINE_PAUSE_KEY;

    /** Tracks whether the physical J key was down last tick (edge-trigger guard). */
    private static boolean wasDownLastTick = false;

    // Keep original keybind mappings for compatibility with Mixins
    private static KeyMapping toggleAutomationKey;
    private static KeyMapping recipeOverlayKey;
    private static KeyMapping modeSettingsKey;
    private static KeyMapping performanceMonitorKey;
    private static KeyMapping lockRecipeKey;
    private static KeyMapping cycleCraftModeKey;
    private static KeyMapping toggleAutoRefillKey;

    /**
     * Call once during client mod initialization (e.g. in the mod's
     * {@code ClientModInitializer.onInitializeClient()}).
     */
    public static void register() {
        FASTMACHINE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bapel-slimefun-mod.fastmachine_action",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY
        ));

        FASTMACHINE_PAUSE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bapel-slimefun-mod.fastmachine_pause",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY
        ));

        // Register legacy keys for registry compatibility if other parts of code still access them
        toggleAutomationKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bapel-slimefun-mod.toggle_automation",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY
        ));
        recipeOverlayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bapel-slimefun-mod.recipe_overlay",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY
        ));
        modeSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bapel-slimefun-mod.mode_settings",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY
        ));
        performanceMonitorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bapel-slimefun-mod.performance_monitor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            CATEGORY
        ));
        lockRecipeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bapel-slimefun-mod.lock_recipe",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            CATEGORY
        ));
        cycleCraftModeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bapel-slimefun-mod.cycle_craft_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
        ));
        toggleAutoRefillKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bapel-slimefun-mod.toggle_auto_refill",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(ModKeybinds::onClientTick);
    }

    /**
     * Polls the {@code J} key each tick. On a fresh press, inspects currently-held
     * modifier keys via raw GLFW state and dispatches the corresponding actions.
     */
    private static void onClientTick(Minecraft mc) {
        if (FASTMACHINE_KEY == null || mc.player == null) return;

        // Poll the Pause/Resume key
        if (FASTMACHINE_PAUSE_KEY != null && FASTMACHINE_PAUSE_KEY.consumeClick()) {
            if (FastMachineAutomationHandler.isActive()) {
                FastMachineAutomationHandler.toggleManualPause();
            }
        }

        boolean isDown = FASTMACHINE_KEY.isDown();

        if (isDown && !wasDownLastTick) {
            long window = mc.getWindow().handle();
            boolean shift = isPhysicallyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT);
            boolean ctrl  = isPhysicallyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL);
            boolean alt   = isPhysicallyDown(window, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT);

            dispatch(shift, ctrl, alt);
        }
        wasDownLastTick = isDown;
    }

    private static boolean isPhysicallyDown(long window, int keyA, int keyB) {
        return GLFW.glfwGetKey(window, keyA) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(window, keyB) == GLFW.GLFW_PRESS;
    }

    private static void dispatch(boolean shift, boolean ctrl, boolean alt) {
        if (alt) {
            promptTargetCount();
        } else if (ctrl) {
            UnifiedAutomationManager.onFastMachineKeybind(
                UnifiedAutomationManager.FastMachineKeybindAction.TOGGLE_REFILL);
        } else if (shift) {
            UnifiedAutomationManager.onFastMachineKeybind(
                UnifiedAutomationManager.FastMachineKeybindAction.CYCLE_CRAFT);
        } else {
            UnifiedAutomationManager.onFastMachineKeybind(
                UnifiedAutomationManager.FastMachineKeybindAction.LOCK_RECIPE);
        }
    }

    private static void promptTargetCount() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new ChatScreen("/fmtarget ", true));
    }

    // Legacy getters for compatibility
    public static KeyMapping getToggleAutomationKey() { return toggleAutomationKey; }
    public static KeyMapping getRecipeOverlayKey() { return recipeOverlayKey; }
    public static KeyMapping getModeSettingsKey() { return modeSettingsKey; }
    public static KeyMapping getPerformanceMonitorKey() { return performanceMonitorKey; }
    public static KeyMapping getLockRecipeKey() { return lockRecipeKey; }
    public static KeyMapping getCycleCraftModeKey() { return cycleCraftModeKey; }
    public static KeyMapping getToggleAutoRefillKey() { return toggleAutoRefillKey; }
}