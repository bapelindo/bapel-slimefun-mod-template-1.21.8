// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║  FILE 1 — FastMachineGuiLayout.java  (BARU)                                ║
// ║  src/client/java/com/bapel_slimefun_mod/automation/FastMachineGuiLayout.java║
// ╚══════════════════════════════════════════════════════════════════════════════╝
package com.bapel_slimefun_mod.automation;

/**
 * Slot-index constants for the FastMachines (GuizhanCraft) 54-slot chest GUI.
 *
 * <pre>
 * ┌────────────────── FastMachines GUI (54 slots) ──────────────────┐
 * │  [ 0][ 1][ 2][ 3][ 4][ 5][ 6][ 7][ 8]   ROW 0 — INPUT            │
 * │  [ 9][10][11][12][13][14][15][16][17]   ROW 1 — INPUT            │
 * │  [18][19][20][21][22][23][24][25][26]   ROW 2 — INPUT            │
 * │  [27][28][29][30][31][32][33][34][35]   ROW 3 — INPUT            │
 * │  [36][37][38][39][40][41][↑ ][ℹ ][⚡]   ROW 4 — PREVIEW + ctrl    │
 * │  [45][46][47][48][49][50][↓ ][✔ ][🔨]   ROW 5 — PREVIEW + ctrl    │
 * └───────────────────────────────────────────────────────────────────┘
 * </pre>
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
    public static final int ENERGY_SLOT      = 44;
    public static final int SCROLL_DOWN_SLOT = 51;
    public static final int CHOICE_SLOT      = 52;
    public static final int CRAFT_SLOT       = 53;

    public static final int GUI_SIZE               = 54;
    public static final int REFILL_FULL_THRESHOLD  = 30;
    public static final int MAX_CLICKS_PER_TICK    = 3;
}