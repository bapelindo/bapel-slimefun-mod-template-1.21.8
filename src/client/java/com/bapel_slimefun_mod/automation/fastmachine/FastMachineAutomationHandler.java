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
 * Client-side "Best of the Best" automation engine for GuizhanCraft/FastMachines.
 *
 * <h2>Automation Phases (per tick, independently throttled)</h2>
 * <ul>
 *   <li><b>A — Energy Guard</b> (200 ms): Reads energy level; pauses crafting when
 *       critical, resumes when safe. Prevents wasted clicks and confusing failures.</li>
 *   <li><b>B — Re-select</b>  (400 ms): Restores the locked recipe if the server
 *       cleared the selection (e.g. after page scroll or craft completion).</li>
 *   <li><b>C — Craft</b>      (configurable): Clicks CRAFT_SLOT with the stored mode.
 *       Skips entirely while paused (energy-critical or out-of-stock) or when the
 *       target batch has been reached.</li>
 *   <li><b>D — Refill</b>     (700 ms): Shift-clicks ingredients from the player's
 *       inventory into free INPUT_SLOTS.</li>
 *   <li><b>E — Stock Watchdog</b> (per craft attempt): Detects prolonged
 *       "recipe selected but INPUT_SLOTS never shrink" as an out-of-stock signal
 *       and pauses automatically with a clear player-facing message.</li>
 * </ul>
 *
 * @author bapelindo
 */
public final class FastMachineAutomationHandler {

    private FastMachineAutomationHandler() {}

    // ── Dependencies ───────────────────────────────────────────────────────────
    private static ModConfig config;

    // ── Session state ──────────────────────────────────────────────────────────
    private static boolean active              = false;
    private static String  currentMachineId    = null;
    private static String  currentMachineTitle = null;
    private static String  lockedRecipeName    = null;

    // ── Guard state ─────────────────────────────────────────────────────────────
    private static boolean paused        = false;
    private static String  pauseReason   = "";

    /** Consecutive craft attempts where INPUT_SLOTS item counts didn't decrease. */
    private static int     stagnantCraftStreak = 0;
    private static int     lastKnownInputCount  = -1;
    private static final int STAGNANT_STREAK_LIMIT = 6; // ~ 2s at default craft interval

    // ── Tick throttles ──────────────────────────────────────────────────────────
    private static long lastEnergyCheckMs = 0L;
    private static long lastReselectMs    = 0L;
    private static long lastCraftMs       = 0L;
    private static long lastRefillMs      = 0L;

    private static final long ENERGY_CHECK_INTERVAL_MS = 200L;
    private static final long RESELECT_INTERVAL_MS      = 400L;
    private static final long REFILL_INTERVAL_MS         = 700L;

    // ── Statistics ────────────────────────────────────────────────────────────
    private static int  statCrafts  = 0;
    private static int  statRefills = 0;
    private static long sessionStartMs = 0L;

    // ── Lifecycle ───────────────────────────────────────────────────────────────

    public static void init(ModConfig cfg) {
        config = cfg;
        FastMachineRecipeMemory.init();
        BapelSlimefunMod.LOGGER.info("[FastMachineAuto] Initialized (Best-of-Best edition).");
    }

    public static void onContainerOpen(String rawTitle) {
        if (rawTitle == null) return;

        String cleanTitle = FastMachineDetector.stripColorCodes(rawTitle).trim();
        String newId      = FastMachineDetector.getMachineId(cleanTitle);

        boolean machineChanged = !newId.equals(currentMachineId);
        currentMachineId    = newId;
        currentMachineTitle = cleanTitle;
        active               = true;
        sessionStartMs       = System.currentTimeMillis();

        statCrafts  = 0;
        statRefills = 0;
        paused      = false;
        pauseReason = "";
        stagnantCraftStreak = 0;
        lastKnownInputCount = -1;
        resetTickTimers();

        FastMachineRecipeMemory.RecipeEntry saved = FastMachineRecipeMemory.get(newId);
        if (saved != null && saved.lockedRecipeDisplayName != null) {
            lockedRecipeName = saved.lockedRecipeDisplayName;
            String targetInfo = (saved.targetCount > 0)
                ? " | " + saved.craftedSinceTarget + "/" + saved.targetCount
                : "";
            showActionBar("§a⚡ Auto | §fLocked: §e" + lockedRecipeName
                + " | §f" + saved.craftMode.label
                + (saved.autoRefill ? " | §bRefill:ON" : "") + targetInfo);
        } else {
            lockedRecipeName = null;
            showActionBar("§e⚡ FastMachine: §f" + cleanTitle
                + " — §7Click recipe → Press §eJ §7to Lock");
        }

        if (machineChanged) {
            BapelSlimefunMod.LOGGER.info("[FastMachineAuto] Opened: {} (id={})", cleanTitle, newId);
        }
    }

    public static void onContainerClose() {
        if (active) {
            long elapsedSec = Math.max(1, (System.currentTimeMillis() - sessionStartMs) / 1000);
            BapelSlimefunMod.LOGGER.info(
                "[FastMachineAuto] Closed '{}'. Session: crafts={}, refills={}, duration={}s",
                currentMachineTitle, statCrafts, statRefills, elapsedSec);
        }
        active = false;
        paused = false;
        resetTickTimers();
    }

    // ── Main tick ───────────────────────────────────────────────────────────────

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

            // ── Phase A: Energy guard ─────────────────────────────────────────
            if (now - lastEnergyCheckMs >= ENERGY_CHECK_INTERVAL_MS) {
                lastEnergyCheckMs = now;
                tickEnergyGuard(menu);
            }

            // ── Phase B: Re-select locked recipe ──────────────────────────────
            if (lockedRecipeName != null && now - lastReselectMs >= RESELECT_INTERVAL_MS) {
                lastReselectMs = now;
                tickReselect(menu, mc);
            }

            // ── Phase C: Auto-craft (skips if paused or target reached) ──────
            long craftInterval = (config != null) ? config.getAutomationDelayMs() : 300;
            if (!paused && now - lastCraftMs >= craftInterval) {
                lastCraftMs = now;
                tickCraft(menu, mc);
            }

            // ── Phase D: Auto-refill ───────────────────────────────────────────
            FastMachineRecipeMemory.RecipeEntry entry = FastMachineRecipeMemory.get(currentMachineId);
            boolean refillEnabled = (entry != null) && entry.autoRefill;
            if (refillEnabled && now - lastRefillMs >= REFILL_INTERVAL_MS) {
                lastRefillMs = now;
                tickRefill(menu, mc);
            }

        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[FastMachineAuto] Unexpected error in tick", e);
        } finally {
            PerformanceMonitor.end("FastMachineAuto.tick");
        }
    }

    // ── Phase implementations ───────────────────────────────────────────────────

    private static void tickEnergyGuard(AbstractContainerMenu menu) {
        FastMachineEnergyMonitor.Reading reading = FastMachineEnergyMonitor.read(menu);
        if (!reading.valid()) return;

        if (reading.isCritical() && !isPausedFor("ENERGY")) {
            paused      = true;
            pauseReason = "ENERGY";
            showActionBar("§c⚠ Energy critical (" + (int) (reading.fraction() * 100)
                + "%) — automation paused, waiting to recharge...");
        } else if (isPausedFor("ENERGY") && reading.isSafeToRun()) {
            paused      = false;
            pauseReason = "";
            showActionBar("§a✓ Energy recovered — automation resumed");
        }
    }

    private static void tickReselect(AbstractContainerMenu menu, Minecraft mc) {
        ItemStack choice = getSlotItem(menu, FastMachineGuiLayout.CHOICE_SLOT);
        if (choice != null && !choice.isEmpty() && choice.getItem() != Items.BARRIER) {
            return;
        }

        for (int slot : FastMachineGuiLayout.PREVIEW_SLOTS) {
            ItemStack preview = getSlotItem(menu, slot);
            if (preview == null || preview.isEmpty()) continue;

            String name = getDisplayName(preview);
            if (lockedRecipeName.equalsIgnoreCase(name)) {
                click(mc, menu, slot, 0, ContainerInput.PICKUP);
                if (isPausedFor("STOCK")) {
                    paused = false; pauseReason = "";
                    showActionBar("§a✓ Ingredients replenished — automation resumed");
                }
                return;
            }
        }
        click(mc, menu, FastMachineGuiLayout.SCROLL_DOWN_SLOT, 0, ContainerInput.PICKUP);
    }

    private static void tickCraft(AbstractContainerMenu menu, Minecraft mc) {
        ItemStack choice = getSlotItem(menu, FastMachineGuiLayout.CHOICE_SLOT);
        if (choice == null || choice.isEmpty() || choice.getItem() == Items.BARRIER) {
            return;
        }

        FastMachineRecipeMemory.RecipeEntry entry = FastMachineRecipeMemory.getOrCreate(currentMachineId);

        if (entry.targetCount > 0 && entry.craftedSinceTarget >= entry.targetCount) {
            FastMachineRecipeMemory.clearTarget(currentMachineId);
            showActionBar("§d🎯 Target reached! §f" + entry.targetCount + " items crafted — batch complete");
            return;
        }

        int inputCountBefore = countOccupiedInputs(menu);

        FastMachineRecipeMemory.CraftMode mode = entry.craftMode;
        int      button;
        ContainerInput clickType;
        switch (mode) {
            case SINGLE:  button = 0; clickType = ContainerInput.PICKUP;     break;
            case BULK_16: button = 1; clickType = ContainerInput.PICKUP;     break;
            case MAX:     button = 1; clickType = ContainerInput.QUICK_MOVE; break;
            case BULK_64:
            default:      button = 0; clickType = ContainerInput.QUICK_MOVE; break;
        }

        click(mc, menu, FastMachineGuiLayout.CRAFT_SLOT, button, clickType);
        statCrafts++;

        if (entry.targetCount > 0) {
            FastMachineRecipeMemory.addCraftedProgress(currentMachineId, mode.approxYieldMultiplier());
        }

        int inputCountAfter = countOccupiedInputs(menu);
        if (lastKnownInputCount >= 0 && inputCountAfter >= lastKnownInputCount && inputCountAfter == inputCountBefore) {
            stagnantCraftStreak++;
        } else {
            stagnantCraftStreak = 0;
        }
        lastKnownInputCount = inputCountAfter;

        if (stagnantCraftStreak >= STAGNANT_STREAK_LIMIT && !isPausedFor("STOCK")) {
            paused      = true;
            pauseReason = "STOCK";
            stagnantCraftStreak = 0;
            showActionBar("§c⚠ Ingredients likely depleted — automation paused. Refill to resume.");
        }
    }

    private static void tickRefill(AbstractContainerMenu menu, Minecraft mc) {
        int occupied = countOccupiedInputs(menu);
        if (occupied >= FastMachineGuiLayout.REFILL_FULL_THRESHOLD) return;

        int clicked  = 0;
        int menuSize = menu.slots.size();

        for (int i = FastMachineGuiLayout.GUI_SIZE; i < menuSize && clicked < FastMachineGuiLayout.MAX_CLICKS_PER_TICK; i++) {
            Slot slot = getSlot(menu, i);
            if (slot == null) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;

            click(mc, menu, i, 0, ContainerInput.QUICK_MOVE);
            clicked++;
            statRefills++;
        }

        if (clicked > 0) {
            stagnantCraftStreak = 0;
            if (isPausedFor("STOCK")) {
                paused      = false;
                pauseReason = "";
                showActionBar("§a✓ Auto-Refill topped up ingredients — automation resumed");
            }
        }
    }

    // ── Public player-control API ──────────────────────────────────────────────

    public static void lockCurrentRecipe() {
        if (!active || currentMachineId == null) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.slots.size() < FastMachineGuiLayout.GUI_SIZE) return;

        ItemStack choice = getSlotItem(menu, FastMachineGuiLayout.CHOICE_SLOT);
        if (choice == null || choice.isEmpty() || choice.getItem() == Items.BARRIER) {
            showActionBar("§c✗ No recipe selected — click a recipe preview first, then press §eJ");
            return;
        }

        String name = getDisplayName(choice);

        if (name.equalsIgnoreCase(lockedRecipeName)) {
            unlockRecipe();
            return;
        }

        lockedRecipeName = name;
        FastMachineRecipeMemory.lockRecipe(currentMachineId, name);
        stagnantCraftStreak = 0;
        lastKnownInputCount = -1;
        if (isPausedFor("STOCK")) { paused = false; pauseReason = ""; }

        showActionBar("§a🔒 Locked: §f" + name + " §8(§7" + currentMachineTitle + "§8)");
        BapelSlimefunMod.LOGGER.info("[FastMachineAuto] Locked '{}' for {}", name, currentMachineId);
    }

    public static void unlockRecipe() {
        if (currentMachineId == null) return;
        lockedRecipeName = null;
        FastMachineRecipeMemory.unlockRecipe(currentMachineId);
        if (isPausedFor("STOCK")) { paused = false; pauseReason = ""; }
        showActionBar("§e🔓 Recipe unlocked — §7manual mode for §f" + currentMachineTitle);
    }

    public static void cycleCraftMode() {
        if (currentMachineId == null) return;
        FastMachineRecipeMemory.RecipeEntry entry = FastMachineRecipeMemory.getOrCreate(currentMachineId);
        FastMachineRecipeMemory.CraftMode next = entry.craftMode.next();
        FastMachineRecipeMemory.setCraftMode(currentMachineId, next);
        showActionBar("§b⚙ Craft mode: §f" + next.label + " §8(" + next.hint + "§8)");
    }

    public static void toggleAutoRefill() {
        if (currentMachineId == null) return;
        FastMachineRecipeMemory.RecipeEntry entry = FastMachineRecipeMemory.getOrCreate(currentMachineId);
        boolean next = !entry.autoRefill;
        FastMachineRecipeMemory.setAutoRefill(currentMachineId, next);
        showActionBar(next
            ? "§a✓ Auto-Refill: §fON §8— §7Inventory → Machine"
            : "§c✗ Auto-Refill: §fOFF");
    }

    public static void setTargetCount(int amount) {
        if (currentMachineId == null) return;
        if (amount <= 0) {
            FastMachineRecipeMemory.clearTarget(currentMachineId);
            showActionBar("§e🎯 Target cleared — crafting unlimited");
            return;
        }
        FastMachineRecipeMemory.setTargetCount(currentMachineId, amount);
        showActionBar("§d🎯 Target set: §f" + amount + " items");
    }

    public static void clearTargetCount() {
        setTargetCount(0);
    }

    // ── State queries ──────────────────────────────────────────────────────────

    public static boolean isActive()               { return active; }
    public static boolean isPaused()               { return paused; }
    public static String  getCurrentMachineId()    { return currentMachineId; }
    public static String  getCurrentMachineTitle() { return currentMachineTitle; }
    public static String  getLockedRecipeName()    { return lockedRecipeName; }

    public static String  getPauseReason() {
        return switch (pauseReason) {
            case "ENERGY" -> "Low Energy";
            case "STOCK"  -> "Out of Stock";
            default       -> "";
        };
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private static boolean isPausedFor(String reason) {
        return paused && reason.equals(pauseReason);
    }

    private static int countOccupiedInputs(AbstractContainerMenu menu) {
        int count = 0;
        for (int s : FastMachineGuiLayout.INPUT_SLOTS) {
            ItemStack item = getSlotItem(menu, s);
            if (item != null && !item.isEmpty()) count++;
        }
        return count;
    }

    private static void click(Minecraft mc, AbstractContainerMenu menu,
                              int slot, int button, ContainerInput type) {
        try {
            mc.gameMode.handleContainerInput(menu.containerId, slot, button, type, mc.player);
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[FastMachineAuto] Click error (slot={}): {}", slot, e.getMessage());
        }
    }

    private static ItemStack getSlotItem(AbstractContainerMenu menu, int index) {
        try {
            if (index < 0 || index >= menu.slots.size()) return null;
            return menu.slots.get(index).getItem();
        } catch (Exception e) { return null; }
    }

    private static Slot getSlot(AbstractContainerMenu menu, int index) {
        try {
            if (index < 0 || index >= menu.slots.size()) return null;
            return menu.slots.get(index);
        } catch (Exception e) { return null; }
    }

    private static String getDisplayName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        try {
            return FastMachineDetector.stripColorCodes(stack.getHoverName().getString()).trim();
        } catch (Exception e) {
            return stack.getItem().toString();
        }
    }

    private static void showActionBar(String msg) {
        try {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) p.sendOverlayMessage(Component.literal(msg));
        } catch (Exception ignored) {}
    }

    private static void resetTickTimers() {
        lastEnergyCheckMs = 0L;
        lastReselectMs    = 0L;
        lastCraftMs       = 0L;
        lastRefillMs      = 0L;
    }
}
