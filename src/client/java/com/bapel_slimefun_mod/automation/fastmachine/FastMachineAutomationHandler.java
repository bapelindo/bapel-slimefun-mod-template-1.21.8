// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║  FILE 5 — FastMachineAutomationHandler.java  (BARU)                        ║
// ║  src/client/java/com/bapel_slimefun_mod/automation/                        ║
// ╚══════════════════════════════════════════════════════════════════════════════╝
package com.bapel_slimefun_mod.automation.fastmachine;
import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.config.ModConfig;
import com.bapel_slimefun_mod.debug.PerformanceMonitor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Client-side automation engine for GuizhanCraft/FastMachines.
 *
 * <h2>Automation Phases (per tick, independently throttled)</h2>
 * <ul>
 *   <li><b>A — Energy Guard</b>: pauses crafting when energy is critically low,
 *       auto-resumes once recharged.</li>
 *   <li><b>B — Re-select</b>: restores the locked recipe if the server cleared it.</li>
 *   <li><b>C — Craft</b> + Stock Watchdog: clicks CRAFT_SLOT; detects and pauses
 *       on ingredient depletion.</li>
 *   <li><b>D — Refill</b>: shift-clicks ingredients from inventory into inputs.</li>
 * </ul>
 *
 * <h2>Controls</h2>
 * <ul>
 *   <li>Lock/Mode/Refill/Target → native GUI widgets ({@link FastMachineGuiButtons})</li>
 *   <li>{@code J} → manual emergency pause/resume ({@link com.bapel_slimefun_mod.client.ModKeybinds})</li>
 * </ul>
 *
 * @author bapelindo
 */
public final class FastMachineAutomationHandler {

    private FastMachineAutomationHandler() {}

    private static ModConfig config;

    private static boolean active              = false;
    private static String  currentMachineId    = null;
    private static String  currentMachineTitle = null;
    private static String  lockedRecipeName    = null;

    private static boolean paused         = false;
    private static boolean manuallyPaused = false;
    private static String  pauseReason    = "";

    private static int stagnantCraftStreak  = 0;
    private static int lastKnownInputCount  = -1;
    private static final int STAGNANT_STREAK_LIMIT = 6;

    private static long lastEnergyCheckMs = 0L;
    private static long lastReselectMs    = 0L;
    private static long lastCraftMs       = 0L;
    private static long lastRefillMs      = 0L;

    private static final long ENERGY_CHECK_INTERVAL_MS = 200L;
    private static final long RESELECT_INTERVAL_MS     = 400L;
    private static final long REFILL_INTERVAL_MS        = 700L;

    private static int  statCrafts  = 0;
    private static int  statRefills = 0;

    // ── Lifecycle ───────────────────────────────────────────────────────────────

    public static void init(ModConfig cfg) {
        config = cfg;
        FastMachineRecipeMemory.load();
        BapelSlimefunMod.LOGGER.info("[FastMachineAuto] Initialized.");
    }

    /** Called from {@link UnifiedAutomationManager#onMachineOpen(String)} early-return branch. */
    public static void onContainerOpen(String rawTitle) {
        if (rawTitle == null) return;

        String cleanTitle = FastMachineDetector.stripColorCodes(rawTitle).trim();
        currentMachineId    = FastMachineDetector.getMachineId(cleanTitle);
        currentMachineTitle = cleanTitle;
        active               = true;

        statCrafts = 0; statRefills = 0;
        paused = false; manuallyPaused = false; pauseReason = "";
        stagnantCraftStreak = 0; lastKnownInputCount = -1;
        resetTickTimers();

        FastMachineRecipeMemory.RecipeEntry saved = FastMachineRecipeMemory.get(currentMachineId);
        if (saved != null && saved.lockedRecipeDisplayName != null) {
            lockedRecipeName = saved.lockedRecipeDisplayName;
            showActionBar("§a⚡ Auto §8| §fLocked: §e" + lockedRecipeName
                + " §8| §f" + saved.craftMode.label
                + (saved.autoRefill ? " §8| §bRefill:ON" : ""));
        } else {
            lockedRecipeName = null;
            showActionBar("§e⚡ FastMachine: §f" + cleanTitle + " §8— §7Use the panel to lock a recipe");
        }

        BapelSlimefunMod.LOGGER.info("[FastMachineAuto] Opened: {} (id={})", cleanTitle, currentMachineId);
    }

    /** Called from {@link UnifiedAutomationManager#onContainerClose()} early-return branch. */
    public static void onContainerClose() {
        if (active) {
            BapelSlimefunMod.LOGGER.info("[FastMachineAuto] Closed '{}'. crafts={}, refills={}",
                currentMachineTitle, statCrafts, statRefills);
        }
        active = false;
        paused = false;
        resetTickTimers();
    }

    // ── Main tick ───────────────────────────────────────────────────────────────

    /** Called from {@link UnifiedAutomationManager#tick()} early-return branch. */
    public static void tick() {
        if (!active || currentMachineId == null) return;

        PerformanceMonitor.start("FastMachineAuto.tick");
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || mc.gameMode == null) return;

            AbstractContainerMenu menu = player.containerMenu;
            if (menu == null || menu.slots.size() < FastMachineGuiLayout.GUI_SIZE) return;

            long now = System.currentTimeMillis();

            if (now - lastEnergyCheckMs >= ENERGY_CHECK_INTERVAL_MS) {
                lastEnergyCheckMs = now;
                tickEnergyGuard(menu);
            }

            if (lockedRecipeName != null && now - lastReselectMs >= RESELECT_INTERVAL_MS) {
                lastReselectMs = now;
                tickReselect(menu, mc);
            }

            long craftInterval = (config != null) ? config.getAutomationDelayMs() : 300;
            if (!paused && now - lastCraftMs >= craftInterval) {
                lastCraftMs = now;
                tickCraft(menu, mc);
            }

            FastMachineRecipeMemory.RecipeEntry entry = FastMachineRecipeMemory.get(currentMachineId);
            if (entry != null && entry.autoRefill && now - lastRefillMs >= REFILL_INTERVAL_MS) {
                lastRefillMs = now;
                tickRefill(menu, mc);
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[FastMachineAuto] Error in tick", e);
        } finally {
            PerformanceMonitor.end("FastMachineAuto.tick");
        }
    }

    // ── Phases ──────────────────────────────────────────────────────────────────

    private static void tickEnergyGuard(AbstractContainerMenu menu) {
        FastMachineEnergyMonitor.Reading reading = FastMachineEnergyMonitor.read(menu);
        if (!reading.valid()) return;

        if (reading.isCritical() && !isPausedFor("ENERGY") && !manuallyPaused) {
            paused = true; pauseReason = "ENERGY";
            showActionBar("§c⚠ Energy critical (" + (int) (reading.fraction() * 100) + "%) — paused");
        } else if (isPausedFor("ENERGY") && reading.isSafeToRun()) {
            paused = false; pauseReason = "";
            showActionBar("§a✓ Energy recovered — resumed");
        }
    }

    private static void tickReselect(AbstractContainerMenu menu, Minecraft mc) {
        ItemStack choice = getSlotItem(menu, FastMachineGuiLayout.CHOICE_SLOT);
        if (choice != null && !choice.isEmpty() && choice.getItem() != Items.BARRIER) return;

        for (int slot : FastMachineGuiLayout.PREVIEW_SLOTS) {
            ItemStack preview = getSlotItem(menu, slot);
            if (preview == null || preview.isEmpty()) continue;
            if (lockedRecipeName.equalsIgnoreCase(getDisplayName(preview))) {
                click(mc, menu, slot, 0, ContainerInput.PICKUP);
                if (isPausedFor("STOCK") && !manuallyPaused) {
                    paused = false; pauseReason = "";
                    showActionBar("§a✓ Ingredients replenished — resumed");
                }
                return;
            }
        }
        click(mc, menu, FastMachineGuiLayout.SCROLL_DOWN_SLOT, 0, ContainerInput.PICKUP);
    }

    private static void tickCraft(AbstractContainerMenu menu, Minecraft mc) {
        ItemStack choice = getSlotItem(menu, FastMachineGuiLayout.CHOICE_SLOT);
        if (choice == null || choice.isEmpty() || choice.getItem() == Items.BARRIER) return;

        FastMachineRecipeMemory.RecipeEntry entry = FastMachineRecipeMemory.getOrCreate(currentMachineId);

        if (entry.targetCount > 0 && entry.craftedSinceTarget >= entry.targetCount) {
            FastMachineRecipeMemory.clearTarget(currentMachineId);
            showActionBar("§d🎯 Target reached! §f" + entry.targetCount + " §7items crafted");
            return;
        }

        int before = countOccupiedInputs(menu);

        int button; ContainerInput inputType;
        switch (entry.craftMode) {
            case SINGLE  -> { button = 0; inputType = ContainerInput.PICKUP; }
            case BULK_16 -> { button = 1; inputType = ContainerInput.PICKUP; }
            case MAX     -> { button = 1; inputType = ContainerInput.QUICK_MOVE; }
            default      -> { button = 0; inputType = ContainerInput.QUICK_MOVE; }
        }
        click(mc, menu, FastMachineGuiLayout.CRAFT_SLOT, button, inputType);
        statCrafts++;

        if (entry.targetCount > 0) {
            FastMachineRecipeMemory.addCraftedProgress(currentMachineId, entry.craftMode.approxYieldMultiplier());
        }

        int after = countOccupiedInputs(menu);
        if (lastKnownInputCount >= 0 && after >= lastKnownInputCount && after == before) {
            stagnantCraftStreak++;
        } else {
            stagnantCraftStreak = 0;
        }
        lastKnownInputCount = after;

        if (stagnantCraftStreak >= STAGNANT_STREAK_LIMIT && !isPausedFor("STOCK") && !manuallyPaused) {
            paused = true; pauseReason = "STOCK"; stagnantCraftStreak = 0;
            showActionBar("§c⚠ Ingredients likely depleted — paused. Refill to resume.");
        }
    }

    private static void tickRefill(AbstractContainerMenu menu, Minecraft mc) {
        if (countOccupiedInputs(menu) >= FastMachineGuiLayout.REFILL_FULL_THRESHOLD) return;

        int clicked = 0;
        int size = menu.slots.size();
        for (int i = FastMachineGuiLayout.GUI_SIZE; i < size && clicked < FastMachineGuiLayout.MAX_CLICKS_PER_TICK; i++) {
            Slot slot = getSlot(menu, i);
            if (slot == null || slot.getItem().isEmpty()) continue;
            click(mc, menu, i, 0, ContainerInput.QUICK_MOVE);
            clicked++; statRefills++;
        }

        if (clicked > 0) {
            stagnantCraftStreak = 0;
            if (isPausedFor("STOCK") && !manuallyPaused) {
                paused = false; pauseReason = "";
                showActionBar("§a✓ Auto-Refill topped up ingredients — resumed");
            }
        }
    }

    // ── Public API used by FastMachineGuiButtons / ModKeybinds ─────────────────

    public static void lockCurrentRecipe() {
        if (!active || currentMachineId == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.slots.size() < FastMachineGuiLayout.GUI_SIZE) return;

        ItemStack choice = getSlotItem(menu, FastMachineGuiLayout.CHOICE_SLOT);
        if (choice == null || choice.isEmpty() || choice.getItem() == Items.BARRIER) {
            showActionBar("§c✗ No recipe selected — click a recipe preview first");
            return;
        }

        String name = getDisplayName(choice);
        if (name.equalsIgnoreCase(lockedRecipeName)) { unlockRecipe(); return; }

        lockedRecipeName = name;
        FastMachineRecipeMemory.lockRecipe(currentMachineId, name);
        stagnantCraftStreak = 0; lastKnownInputCount = -1;
        if (isPausedFor("STOCK")) { paused = false; pauseReason = ""; }

        showActionBar("§a🔒 Locked: §f" + name);
    }

    public static void unlockRecipe() {
        if (currentMachineId == null) return;
        lockedRecipeName = null;
        FastMachineRecipeMemory.unlockRecipe(currentMachineId);
        if (isPausedFor("STOCK")) { paused = false; pauseReason = ""; }
        showActionBar("§e🔓 Recipe unlocked");
    }

    public static void cycleCraftMode() {
        if (currentMachineId == null) return;
        FastMachineRecipeMemory.RecipeEntry entry = FastMachineRecipeMemory.getOrCreate(currentMachineId);
        FastMachineRecipeMemory.CraftMode next = entry.craftMode.next();
        FastMachineRecipeMemory.setCraftMode(currentMachineId, next);
        showActionBar("§b⚙ Craft mode: §f" + next.label);
    }

    public static void toggleAutoRefill() {
        if (currentMachineId == null) return;
        FastMachineRecipeMemory.RecipeEntry entry = FastMachineRecipeMemory.getOrCreate(currentMachineId);
        boolean next = !entry.autoRefill;
        FastMachineRecipeMemory.setAutoRefill(currentMachineId, next);
        showActionBar(next ? "§a✓ Auto-Refill: ON" : "§c✗ Auto-Refill: OFF");
    }

    public static void setTargetCount(int amount) {
        if (currentMachineId == null) return;
        if (amount <= 0) { FastMachineRecipeMemory.clearTarget(currentMachineId); showActionBar("§e🎯 Target cleared"); return; }
        FastMachineRecipeMemory.setTargetCount(currentMachineId, amount);
        showActionBar("§d🎯 Target set: §f" + amount);
    }

    public static void clearTargetCount() { setTargetCount(0); }

    /** Bound to the {@code J} key — the sole emergency panic control (see ModKeybinds). */
    public static void toggleManualPause() {
        if (!active) return;
        manuallyPaused = !manuallyPaused;
        if (manuallyPaused) {
            paused = true; pauseReason = "MANUAL";
            showActionBar("§c⏸ Automation manually PAUSED §7(press §eJ§7 to resume)");
        } else {
            paused = false; pauseReason = "";
            showActionBar("§a▶ Automation RESUMED");
        }
    }

    // ── State queries ──────────────────────────────────────────────────────────

    public static boolean isActive()               { return active; }
    public static String  getCurrentMachineId()     { return currentMachineId; }
    public static String  getCurrentMachineTitle()  { return currentMachineTitle; }
    public static String  getLockedRecipeName()     { return lockedRecipeName; }
    public static boolean isPaused()                { return paused; }

    public static String getPauseReason() {
        return switch (pauseReason) {
            case "ENERGY" -> "Low Energy";
            case "STOCK"  -> "Out of Stock";
            case "MANUAL" -> "Manual Pause (J)";
            default       -> "";
        };
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static boolean isPausedFor(String reason) { return paused && reason.equals(pauseReason); }

    private static int countOccupiedInputs(AbstractContainerMenu menu) {
        int count = 0;
        for (int s : FastMachineGuiLayout.INPUT_SLOTS) {
            ItemStack item = getSlotItem(menu, s);
            if (item != null && !item.isEmpty()) count++;
        }
        return count;
    }

    private static void click(Minecraft mc, AbstractContainerMenu menu, int slot, int button, ContainerInput type) {
        try {
            // Yarn lama: handleInventoryMouseClick(...). Nama official Mojang di 26.1.2: handleContainerInput(...).
            mc.gameMode.handleContainerInput(menu.containerId, slot, button, type, mc.player);
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[FastMachineAuto] Click error (slot={})", slot, e);
        }
    }

    private static ItemStack getSlotItem(AbstractContainerMenu menu, int index) {
        try { return (index >= 0 && index < menu.slots.size()) ? menu.slots.get(index).getItem() : null; }
        catch (Exception e) { return null; }
    }

    private static Slot getSlot(AbstractContainerMenu menu, int index) {
        try { return (index >= 0 && index < menu.slots.size()) ? menu.slots.get(index) : null; }
        catch (Exception e) { return null; }
    }

    private static String getDisplayName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        try { return FastMachineDetector.stripColorCodes(stack.getHoverName().getString()).trim(); }
        catch (Exception e) { return stack.getItem().toString(); }
    }

    private static void showActionBar(String msg) {
        LocalPlayer p = Minecraft.getInstance().player;
        // sendSystemMessage di 26.1.2 cuma overload 1-argumen (Component) — tidak ada lagi
        // parameter boolean untuk action-bar seperti displayClientMessage yang lama.
        if (p != null) p.sendSystemMessage(Component.literal(msg));
    }

    private static void resetTickTimers() {
        lastEnergyCheckMs = 0L; lastReselectMs = 0L; lastCraftMs = 0L; lastRefillMs = 0L;
    }
}