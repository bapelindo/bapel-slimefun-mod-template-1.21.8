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
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import com.bapel_slimefun_mod.automation.RecipeDatabase;
import com.bapel_slimefun_mod.automation.RecipeData;
import com.bapel_slimefun_mod.automation.RecipeHandler;
import com.bapel_slimefun_mod.automation.SlimefunDataLoader;
import com.bapel_slimefun_mod.automation.SlimefunMachineData;

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

    private static boolean scrollDownDirection = true;
    private static java.util.List<String> lastPreviewNames = new java.util.ArrayList<>();
    private static int scrollFlipCount = 0;

    private static boolean autoMatch = false;
    private static long lastMatchCheckMs = 0L;
    private static final long MATCH_CHECK_INTERVAL_MS = 1000L;

    // request state for hands-free hopping
    private static String requestRecipeName = null;
    private static int requestTargetQty = 0;
    private static java.util.Map<String, Integer> pendingIngredients = new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean isExtractingFromNetwork = false;
    private static long nextNetworkActionTime = 0L;

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

        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            net.minecraft.core.BlockPos pos = blockHit.getBlockPos();
            FastMachineRecipeMemory.cachePosition(currentMachineId, pos);
        }

        FastMachineRecipeMemory.RecipeEntry saved = FastMachineRecipeMemory.get(currentMachineId);
        autoMatch = (saved != null) && saved.autoMatch;
        manuallyPaused = (saved != null) ? saved.manuallyPaused : true;
        paused = manuallyPaused;
        pauseReason = manuallyPaused ? "MANUAL" : "";

        if (requestRecipeName != null) {
            String reqName = requestRecipeName;
            int reqQty = requestTargetQty;
            requestRecipeName = null;
            requestTargetQty = 0;
            lockRecipeByNameDirect(reqName, reqQty);
        } else if (saved != null && saved.recipeQueue != null && !saved.recipeQueue.isEmpty() && saved.currentQueueIndex < saved.recipeQueue.size()) {
            FastMachineRecipeMemory.QueueEntry currentEntry = saved.recipeQueue.get(saved.currentQueueIndex);
            lockedRecipeName = currentEntry.recipeName;
            showActionBar("§a⚡ Auto Queue §8| §f" + (saved.currentQueueIndex + 1) + "/" + saved.recipeQueue.size()
                + " §e" + lockedRecipeName + " §7(x" + currentEntry.targetCount + ")");
        } else if (saved != null && saved.lockedRecipeDisplayName != null) {
            lockedRecipeName = saved.lockedRecipeDisplayName;
            showActionBar("§a🔒 Locked: §e" + lockedRecipeName
                + " §8| §f" + saved.craftMode.label
                + (saved.autoRefill ? " §8| §bRefill:ON" : ""));
        } else {
            lockedRecipeName = null;
            showActionBar("§e⚡ FastMachine: §f" + cleanTitle + " §8— §7Use the panel to lock a recipe");
        }

        if (lockedRecipeName != null) {
            String sfId = getSlimefunMachineId(currentMachineId);
            if (sfId != null) {
                java.util.List<RecipeData> recipes = RecipeDatabase.getRecipesForMachine(sfId);
                boolean found = false;
                for (RecipeData r : recipes) {
                    if (r.getPrimaryOutput() != null && r.getPrimaryOutput().getDisplayName().equalsIgnoreCase(lockedRecipeName)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    lockedRecipeName = null;
                    FastMachineRecipeMemory.unlockRecipe(currentMachineId);
                    showActionBar("§c✗ Recipe mismatch - unlocked");
                }
            }
        }
        scrollFlipCount = 0;

        BapelSlimefunMod.LOGGER.info("[FastMachineAuto] Opened: {} (id={})", cleanTitle, currentMachineId);
    }

    /** Called from {@link UnifiedAutomationManager#onContainerClose()} early-return branch. */
    public static void onContainerClose() {
        if (active) {
            BapelSlimefunMod.LOGGER.info("[FastMachineAuto] Closed '{}'. crafts={}, refills={}",
                currentMachineTitle, statCrafts, statRefills);
            FastMachineRecipeMemory.save();
        }
        active = false;
        paused = false;
        autoMatch = false;
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

            if (autoMatch && now - lastMatchCheckMs >= MATCH_CHECK_INTERVAL_MS) {
                lastMatchCheckMs = now;
                String detected = detectRecipeFromInputs(menu, currentMachineId);
                if (detected != null && !detected.equalsIgnoreCase(lockedRecipeName)) {
                    lockedRecipeName = detected;
                    stagnantCraftStreak = 0;
                    lastKnownInputCount = -1;
                    showActionBar("§d⚙ Auto-Match detected: §e" + lockedRecipeName);
                }
            }

            if (lockedRecipeName != null && !manuallyPaused && !isPausedFor("ENERGY") && now - lastReselectMs >= RESELECT_INTERVAL_MS) {
                lastReselectMs = now;
                tickReselect(menu, mc);
            }

            long craftInterval = (config != null) ? config.getAutomationDelayMs() : 300;
            // Adaptive Latency Scaling (Lag Backoff)
            long adaptiveDelay = craftInterval + (stagnantCraftStreak * 100L);
            if (!paused && now - lastCraftMs >= adaptiveDelay) {
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
        boolean choiceMatches = (choice != null && !choice.isEmpty() && choice.getItem() != Items.BARRIER && lockedRecipeName.equalsIgnoreCase(getDisplayName(choice)));
        if (choiceMatches) {
            scrollDownDirection = true;
            lastPreviewNames.clear();
            scrollFlipCount = 0;
            return;
        }

        java.util.List<String> currentPreviews = new java.util.ArrayList<>();
        for (int slot : FastMachineGuiLayout.PREVIEW_SLOTS) {
            ItemStack preview = getSlotItem(menu, slot);
            String name = (preview != null && !preview.isEmpty()) ? getDisplayName(preview) : "";
            currentPreviews.add(name);
        }

        for (int i = 0; i < FastMachineGuiLayout.PREVIEW_SLOTS.length; i++) {
            String name = currentPreviews.get(i);
            if (!name.isEmpty() && lockedRecipeName.equalsIgnoreCase(name)) {
                click(mc, menu, FastMachineGuiLayout.PREVIEW_SLOTS[i], 0, ContainerInput.PICKUP);
                if (isPausedFor("STOCK") && !manuallyPaused) {
                    paused = false; pauseReason = "";
                    showActionBar("§a✓ Ingredients replenished — resumed");
                }
                scrollDownDirection = true;
                lastPreviewNames.clear();
                scrollFlipCount = 0;
                return;
            }
        }

        if (!lastPreviewNames.isEmpty() && lastPreviewNames.equals(currentPreviews)) {
            scrollFlipCount++;
            if (scrollFlipCount >= 3) {
                unlockRecipe();
                showActionBar("§c✗ Recipe not found in machine's scroll list!");
                scrollFlipCount = 0;
                return;
            }
            scrollDownDirection = !scrollDownDirection;
        }

        lastPreviewNames = currentPreviews;

        int scrollSlot = scrollDownDirection ? FastMachineGuiLayout.SCROLL_DOWN_SLOT : FastMachineGuiLayout.SCROLL_UP_SLOT;
        click(mc, menu, scrollSlot, 0, ContainerInput.PICKUP);
    }

    private static void tickCraft(AbstractContainerMenu menu, Minecraft mc) {
        ItemStack choice = getSlotItem(menu, FastMachineGuiLayout.CHOICE_SLOT);
        if (choice == null || choice.isEmpty() || choice.getItem() == Items.BARRIER) return;
        if (lockedRecipeName != null && !lockedRecipeName.equalsIgnoreCase(getDisplayName(choice))) return;

        FastMachineRecipeMemory.RecipeEntry entry = FastMachineRecipeMemory.getOrCreate(currentMachineId);

        boolean isQueueActive = (entry.recipeQueue != null && !entry.recipeQueue.isEmpty() && entry.currentQueueIndex < entry.recipeQueue.size());

        if (isQueueActive) {
            FastMachineRecipeMemory.QueueEntry currentQueue = entry.recipeQueue.get(entry.currentQueueIndex);
            if (currentQueue.craftedCount >= currentQueue.targetCount) {
                entry.currentQueueIndex++;
                if (entry.currentQueueIndex >= entry.recipeQueue.size()) {
                    FastMachineRecipeMemory.clearQueue(currentMachineId);
                    lockedRecipeName = null;
                    paused = true;
                    manuallyPaused = true;
                    pauseReason = "MANUAL";
                    showActionBar("§d🎯 All queue targets reached! Automation paused.");
                    FastMachineRecipeMemory.save();
                    return;
                } else {
                    FastMachineRecipeMemory.advanceQueue(currentMachineId);
                    FastMachineRecipeMemory.QueueEntry nextQueue = entry.recipeQueue.get(entry.currentQueueIndex);
                    lockedRecipeName = nextQueue.recipeName;
                    showActionBar("§b🎯 Next queue target: §e" + lockedRecipeName + " §7(x" + nextQueue.targetCount + ")");
                    return;
                }
            }
        } else {
            if (entry.targetCount > 0 && entry.craftedSinceTarget >= entry.targetCount) {
                int crafted = entry.targetCount;
                FastMachineRecipeMemory.clearTarget(currentMachineId);
                paused = true;
                manuallyPaused = true;
                pauseReason = "MANUAL";
                showActionBar("§d🎯 Target reached! §f" + crafted + " §7items crafted — automation paused");
                return;
            }
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

        if (stagnantCraftStreak == 0) {
            if (isQueueActive) {
                FastMachineRecipeMemory.addQueueCraftedProgress(currentMachineId, entry.craftMode.approxYieldMultiplier());
            } else if (entry.targetCount > 0) {
                FastMachineRecipeMemory.addCraftedProgress(currentMachineId, entry.craftMode.approxYieldMultiplier());
            }
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
        if (lockedRecipeName == null) return;
        if (countOccupiedInputs(menu) >= FastMachineGuiLayout.REFILL_FULL_THRESHOLD) return;

        if (!RecipeDatabase.isInitialized()) {
            RecipeDatabase.initialize();
        }

        RecipeData recipe = null;
        java.util.List<RecipeData> candidates = RecipeDatabase.searchRecipesByOutput(lockedRecipeName);
        if (candidates != null && !candidates.isEmpty()) {
            for (RecipeData r : candidates) {
                if (r.getPrimaryOutput() != null && r.getPrimaryOutput().getDisplayName().equalsIgnoreCase(lockedRecipeName)) {
                    recipe = r;
                    break;
                }
            }
            if (recipe == null) {
                recipe = candidates.get(0);
            }
        }

        if (recipe == null) return;
        java.util.Map<String, Integer> requiredInputs = recipe.getGroupedInputs();
        if (requiredInputs.isEmpty()) return;

        int clicked = 0;
        int size = menu.slots.size();
        for (int i = FastMachineGuiLayout.GUI_SIZE; i < size && clicked < FastMachineGuiLayout.MAX_CLICKS_PER_TICK; i++) {
            Slot slot = getSlot(menu, i);
            if (slot == null || slot.getItem().isEmpty()) continue;

            ItemStack stack = slot.getItem();
            boolean isIngredient = false;
            for (String neededId : requiredInputs.keySet()) {
                if (com.bapel_slimefun_mod.automation.AutomationUtils.matchesItem(stack, neededId)) {
                    isIngredient = true;
                    break;
                }
            }

            if (!isIngredient) continue;

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

    public static void lockQueueFirstRecipe(String machineId) {
        FastMachineRecipeMemory.RecipeEntry entry = FastMachineRecipeMemory.get(machineId);
        if (entry != null && entry.recipeQueue != null && !entry.recipeQueue.isEmpty()) {
            entry.currentQueueIndex = 0;
            lockedRecipeName = entry.recipeQueue.get(0).recipeName;
            FastMachineRecipeMemory.put(machineId, entry);
            showActionBar("§a🎯 Queue started: §e" + lockedRecipeName);
        }
    }

    public static void setLockedRecipeNameDirect(String name) {
        lockedRecipeName = name;
        stagnantCraftStreak = 0;
        lastKnownInputCount = -1;
    }

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
        manuallyPaused = false;
        paused = false;
        FastMachineRecipeMemory.setManuallyPaused(currentMachineId, false);
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
        if (!active || currentMachineId == null) return;
        manuallyPaused = !manuallyPaused;
        FastMachineRecipeMemory.setManuallyPaused(currentMachineId, manuallyPaused);
        if (manuallyPaused) {
            paused = true; pauseReason = "MANUAL";
            String keyName = "O";
            if (com.bapel_slimefun_mod.client.ModKeybinds.FASTMACHINE_PAUSE_KEY != null) {
                keyName = com.bapel_slimefun_mod.client.ModKeybinds.FASTMACHINE_PAUSE_KEY.getTranslatedKeyMessage().getString();
            }
            showActionBar("§c⏸ Automation manually PAUSED §7(press §e" + keyName + "§7 to resume)");
        } else {
            paused = false; pauseReason = "";
            showActionBar("§a▶ Automation RESUMED");
        }
        FastMachineRecipeMemory.save();
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
        if (p != null) {
            p.sendOverlayMessage(Component.literal(msg));
        }
    }

    private static void resetTickTimers() {
        lastEnergyCheckMs = 0L; lastReselectMs = 0L; lastCraftMs = 0L; lastRefillMs = 0L;
        scrollDownDirection = true;
        if (lastPreviewNames != null) {
            lastPreviewNames.clear();
        }
    }

    public static boolean isAutoMatch() {
        return autoMatch;
    }

    public static void toggleAutoMatch() {
        if (currentMachineId == null) return;
        autoMatch = !autoMatch;
        FastMachineRecipeMemory.setAutoMatch(currentMachineId, autoMatch);
        showActionBar(autoMatch ? "§d⚙ Auto-Match: §aON" : "§d⚙ Auto-Match: §7OFF");
    }

    public static String getFastMachineIdFromSlimefun(String sfMachineId) {
        if (sfMachineId == null) return null;
        switch (sfMachineId.toUpperCase()) {
            case "ENHANCED_CRAFTING_TABLE": return "FAST_ENHANCED_CRAFTING_TABLE";
            case "MAGIC_WORKBENCH": return "FAST_MAGIC_WORKBENCH";
            case "ARMOR_FORGE": return "FAST_ARMOR_FORGE";
            case "ORE_CRUSHER": return "FAST_ORE_CRUSHER";
            case "GRIND_STONE": return "FAST_GRIND_STONE";
            case "GRINDSTONE": return "FAST_GRIND_STONE";
            case "COMPRESSOR": return "FAST_COMPRESSOR";
            case "PRESSURE_CHAMBER": return "FAST_PRESSURE_CHAMBER";
            case "ORE_WASHER": return "FAST_ORE_WASHER";
            case "PANNING_MACHINE": return "FAST_PANNING_MACHINE";
            case "TABLE_SAW": return "FAST_TABLE_SAW";
            case "SMELTERY": return "FAST_SMELTERY";
            case "ANCIENT_ALTAR": return "FAST_ANCIENT_ALTAR";
            default: return null;
        }
    }

    public static String getSlimefunMachineId(String fastMachineId) {
        if (fastMachineId == null) return null;
        switch (fastMachineId) {
            case "FAST_CRAFTING_TABLE": return "ENHANCED_CRAFTING_TABLE";
            case "FAST_ENHANCED_CRAFTING_TABLE": return "ENHANCED_CRAFTING_TABLE";
            case "FAST_MAGIC_WORKBENCH": return "MAGIC_WORKBENCH";
            case "FAST_ARMOR_FORGE": return "ARMOR_FORGE";
            case "FAST_ORE_CRUSHER": return "ORE_CRUSHER";
            case "FAST_GRIND_STONE": return "GRIND_STONE";
            case "FAST_COMPRESSOR": return "COMPRESSOR";
            case "FAST_PRESSURE_CHAMBER": return "PRESSURE_CHAMBER";
            case "FAST_ORE_WASHER": return "ORE_WASHER";
            case "FAST_PANNING_MACHINE": return "PANNING_MACHINE";
            case "FAST_TABLE_SAW": return "TABLE_SAW";
            case "FAST_SMELTERY": return "SMELTERY";
            case "FAST_ANCIENT_ALTAR": return "ANCIENT_ALTAR";
            default: return null;
        }
    }

    public static void requestAutoCraft(String recipeName, String sfMachineId, int qty) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        String fastMachineId = getFastMachineIdFromSlimefun(sfMachineId);
        if (fastMachineId == null) {
            showActionBar("§c✗ Machine " + sfMachineId + " is not a supported FastMachine");
            return;
        }

        if (active && currentMachineId != null && currentMachineId.equalsIgnoreCase(fastMachineId)) {
            lockRecipeByNameDirect(recipeName, qty);
            return;
        }

        // Check for missing ingredients
        if (!RecipeDatabase.isInitialized()) {
            RecipeDatabase.initialize();
        }
        RecipeData recipe = null;
        java.util.List<RecipeData> candidates = RecipeDatabase.searchRecipesByOutput(recipeName);
        if (candidates != null) {
            for (RecipeData r : candidates) {
                if (r.getPrimaryOutput() != null && r.getPrimaryOutput().getDisplayName().equalsIgnoreCase(recipeName)) {
                    recipe = r;
                    break;
                }
            }
            if (recipe == null && !candidates.isEmpty()) {
                recipe = candidates.get(0);
            }
        }

        java.util.Map<String, Integer> missing = new java.util.HashMap<>();
        if (recipe != null) {
            java.util.List<ItemStack> invItems = new java.util.ArrayList<>();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack != null && !stack.isEmpty()) {
                    invItems.add(stack);
                }
            }
            java.util.Map<String, Integer> groupedInputs = recipe.getGroupedInputs();
            for (java.util.Map.Entry<String, java.lang.Integer> entry : groupedInputs.entrySet()) {
                String itemId = entry.getKey();
                int needed = entry.getValue() * qty;
                int current = 0;
                for (ItemStack invStack : invItems) {
                    String invId = com.bapel_slimefun_mod.automation.AutomationUtils.getItemId(invStack);
                    if (invId != null && invId.equalsIgnoreCase(itemId)) {
                        current += invStack.getCount();
                    }
                }
                if (current < needed) {
                    missing.put(itemId, needed - current);
                }
            }
        }

        // If ingredients are missing, open the NETWORK_GRID first!
        if (!missing.isEmpty()) {
            BlockPos gridPos = FastMachineRecipeMemory.getClosestPosition("NETWORK_GRID", player.position(), mc.level);
            if (gridPos == null) {
                showActionBar("§c✗ Missing items! Open Network Grid once to cache its coordinates.");
                return;
            }
            if (gridPos.distToCenterSqr(player.position()) > 25.0) {
                showActionBar("§c✗ Network Grid is too far away! (Max 5 blocks)");
                return;
            }

            requestRecipeName = recipeName;
            requestTargetQty = qty;
            pendingIngredients = new java.util.concurrent.ConcurrentHashMap<>(missing);
            isExtractingFromNetwork = true;
            nextNetworkActionTime = 0L;

            if (mc.screen != null) {
                mc.screen.onClose();
            }

            showActionBar("§e📦 Opening Network Grid to extract missing items...");
            rightClickBlock(mc, gridPos);
            return;
        }

        BlockPos pos = FastMachineRecipeMemory.getClosestPosition(fastMachineId, player.position(), mc.level);
        if (pos == null) {
            showActionBar("§c✗ No cached coordinates for " + fastMachineId + "! Open it manually once to cache.");
            return;
        }

        if (pos.distToCenterSqr(player.position()) > 25.0) {
            showActionBar("§c✗ " + fastMachineId + " is too far away! (Max 5 blocks)");
            return;
        }

        requestRecipeName = recipeName;
        requestTargetQty = qty;

        if (mc.screen != null) {
            mc.screen.onClose();
        }

        rightClickBlock(mc, pos);
    }

    public static void lockRecipeByNameDirect(String name, int qty) {
        if (!active || currentMachineId == null) return;
        lockedRecipeName = name;
        FastMachineRecipeMemory.lockRecipe(currentMachineId, name);
        FastMachineRecipeMemory.setTargetCount(currentMachineId, qty);
        paused = false;
        manuallyPaused = false;
        FastMachineRecipeMemory.setManuallyPaused(currentMachineId, false);
        pauseReason = "";
        stagnantCraftStreak = 0;
        lastKnownInputCount = -1;
        showActionBar("§a🎯 Auto-Craft: §e" + name + " §7(x" + qty + ")");
    }

    public static String detectRecipeFromInputs(AbstractContainerMenu menu, String fastMachineId) {
        String sfMachineId = getSlimefunMachineId(fastMachineId);
        if (sfMachineId == null) return null;

        if (!RecipeDatabase.isInitialized()) {
            RecipeDatabase.initialize();
        }

        java.util.List<ItemStack> inputs = new java.util.ArrayList<>();
        for (int slot : FastMachineGuiLayout.INPUT_SLOTS) {
            ItemStack stack = getSlotItem(menu, slot);
            if (stack != null && !stack.isEmpty()) {
                inputs.add(stack);
            }
        }
        if (inputs.isEmpty()) return null;

        java.util.List<RecipeData> recipes = RecipeDatabase.getRecipesForMachine(sfMachineId);
        if (recipes == null || recipes.isEmpty()) return null;

        RecipeData bestMatch = null;
        int bestMatchIngredientCount = 0;

        for (RecipeData recipe : recipes) {
            java.util.Map<String, Integer> required = recipe.getGroupedInputs();
            if (required.isEmpty()) continue;

            if (RecipeHandler.hasEnoughIngredients(inputs, required)) {
                int uniqueIngredients = required.size();
                if (uniqueIngredients > bestMatchIngredientCount) {
                    bestMatch = recipe;
                    bestMatchIngredientCount = uniqueIngredients;
                }
            }
        }

        if (bestMatch != null) {
            return bestMatch.getPrimaryOutput().getDisplayName();
        }

        return null;
    }

    private static void rightClickBlock(Minecraft mc, BlockPos pos) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.level == null) return;

        net.minecraft.world.phys.BlockHitResult hitResult = new net.minecraft.world.phys.BlockHitResult(
            new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
            net.minecraft.core.Direction.UP,
            pos,
            false
        );

        mc.gameMode.useItemOn(player, net.minecraft.world.InteractionHand.MAIN_HAND, hitResult);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    // ── Network Grid Penarikan Otomatis ──────────────────────────────────────

    public static boolean isExtractingFromNetwork() {
        return isExtractingFromNetwork;
    }

    public static void cancelNetworkExtraction() {
        isExtractingFromNetwork = false;
        pendingIngredients.clear();
        showActionBar("§c✗ Network extraction cancelled.");
    }

    public static String getSlimefunMachineIdFromRecipe(String name) {
        if (!RecipeDatabase.isInitialized()) {
            RecipeDatabase.initialize();
        }
        java.util.List<RecipeData> candidates = RecipeDatabase.searchRecipesByOutput(name);
        if (candidates != null) {
            for (RecipeData r : candidates) {
                if (r.getPrimaryOutput() != null && r.getPrimaryOutput().getDisplayName().equalsIgnoreCase(name)) {
                    return r.getMachineId();
                }
            }
            if (!candidates.isEmpty()) {
                return candidates.get(0).getMachineId();
            }
        }
        return null;
    }

    public static void onNetworkGridOpen(String title) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            net.minecraft.core.BlockPos pos = blockHit.getBlockPos();
            FastMachineRecipeMemory.cachePosition("NETWORK_GRID", pos);
        }
        if (isExtractingFromNetwork) {
            nextNetworkActionTime = System.currentTimeMillis() + 500L;
            showActionBar("§e📦 Scanning Network Grid...");
        }
    }

    public static void tickNetworkGrid(AbstractContainerMenu menu) {
        if (!isExtractingFromNetwork || pendingIngredients.isEmpty()) {
            if (isExtractingFromNetwork) {
                isExtractingFromNetwork = false;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    showActionBar("§a✔ Ingredients extracted! Switching to FastMachine...");
                    requestAutoCraft(requestRecipeName, getSlimefunMachineIdFromRecipe(requestRecipeName), requestTargetQty);
                }
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextNetworkActionTime) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;

        boolean foundAny = false;
        for (int i = 0; i < 45; i++) {
            if (i >= menu.slots.size()) break;
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack == null || stack.isEmpty()) continue;

            String matchedKey = null;
            for (String pendingId : pendingIngredients.keySet()) {
                if (com.bapel_slimefun_mod.automation.AutomationUtils.matchesItem(stack, pendingId)) {
                    matchedKey = pendingId;
                    break;
                }
            }

            if (matchedKey != null) {
                int missingAmount = pendingIngredients.get(matchedKey);
                click(mc, menu, i, 0, ContainerInput.QUICK_MOVE);
                
                int stackSize = stack.getCount();
                int newMissing = Math.max(0, missingAmount - stackSize);
                if (newMissing <= 0) {
                    pendingIngredients.remove(matchedKey);
                } else {
                    pendingIngredients.put(matchedKey, newMissing);
                }
                
                showActionBar("§e📦 Extracted: §f" + stack.getHoverName().getString() + " §7(" + stackSize + " pcs)");
                nextNetworkActionTime = now + 250L;
                foundAny = true;
                break;
            }
        }

        if (foundAny) return;

        int nextPageSlot = -1;
        for (int i = 45; i < Math.min(54, menu.slots.size()); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack == null || stack.isEmpty()) continue;

            String name = stack.getHoverName().getString().toLowerCase();
            if (name.contains("next") || name.contains("arrow") || name.contains("selanjutnya") || name.contains(">") || name.contains("forward")) {
                nextPageSlot = i;
                break;
            }
        }

        if (nextPageSlot != -1) {
            click(mc, menu, nextPageSlot, 0, ContainerInput.PICKUP);
            nextNetworkActionTime = now + 800L;
            showActionBar("§e📦 Flipping to next page...");
        } else {
            showActionBar("§c✗ Not all items found in network! Missing: " + pendingIngredients.keySet());
            isExtractingFromNetwork = false;
            pendingIngredients.clear();
            if (mc.screen != null) {
                mc.screen.onClose();
            }
        }
    }
}