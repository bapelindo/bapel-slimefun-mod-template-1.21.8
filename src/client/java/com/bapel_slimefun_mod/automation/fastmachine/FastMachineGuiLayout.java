package com.bapel_slimefun_mod.automation.fastmachine;

/**
 * Slot-index constants for the FastMachines 54-slot chest GUI.
 * See previous revision for full layout diagram.
 *
 * @author bapelindo
 */
public final class FastMachineGuiLayout {

    private FastMachineGuiLayout() {}

    public static final int[] INPUT_SLOTS = {
         0,  1,  2,  3,  4,  5,  6,  7,  8,
         9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35
    };

    public static final int[] PREVIEW_SLOTS = {
        36, 37, 38, 39, 40, 41,
        45, 46, 47, 48, 49, 50
    };

    public static final int SCROLL_UP_SLOT   = 42;
    public static final int INFO_SLOT        = 43;

    /** Energy display slot — its lore contains the current/max energy value. */
    public static final int ENERGY_SLOT      = 44;

    public static final int SCROLL_DOWN_SLOT = 51;
    public static final int CHOICE_SLOT      = 52;
    public static final int CRAFT_SLOT       = 53;

    public static final int PREVIEWS_PER_PAGE     = PREVIEW_SLOTS.length;
    public static final int GUI_SIZE               = 54;
    public static final int REFILL_FULL_THRESHOLD  = 30;
    public static final int MAX_CLICKS_PER_TICK     = 3;
}
