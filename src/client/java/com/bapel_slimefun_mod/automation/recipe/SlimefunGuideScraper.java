package com.bapel_slimefun_mod.automation.recipe;

import com.bapel_slimefun_mod.automation.util.ItemKeyUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * World-class Slimefun Guide Auto-Scraper.
 *
 * <h2>Architecture</h2>
 * <p>Uses a robust FSM (Finite State Machine) with:
 * <ul>
 *   <li><b>GUI Fingerprinting</b>: Detects actual GUI changes using slot content hashes,
 *       preventing false-positive transitions before the server responds.</li>
 *   <li><b>Watchdog Timer</b>: Aborts a stuck state after {@link #WATCHDOG_TICKS} ticks.</li>
 *   <li><b>Pagination</b>: Discovers and follows "Next Page" buttons in both main and
 *       category views so every item on every page is captured.</li>
 *   <li><b>Utility-Item Blacklist</b>: Both item-type and keyword heuristics guard against
 *       accidentally clicking Search, Settings, or navigation buttons.</li>
 *   <li><b>Safe Clicking</b>: A configurable inter-action delay prevents server-side
 *       rate-limit kicks.</li>
 * </ul>
 *
 * <h2>State Transitions</h2>
 * <pre>
 * INACTIVE
 *   ──(startScraping)──► WAIT_FOR_MAIN
 *                          │
 *                    (fingerprint stable)
 *                          │
 *                          ▼
 *                    SCAN_MAIN_PAGE ◄──────────────────────────────────────────┐
 *                     │         │                                               │
 *              (click cat)   (next page btn found)                             │
 *                     │         │                                               │
 *                     ▼         ▼                                               │
 *              WAIT_CATEGORY  WAIT_MAIN_NEXT_PAGE                              │
 *                     │                                                         │
 *             (fingerprint changed)                                             │
 *                     │                                                         │
 *                     ▼                                                         │
 *              SCAN_CATEGORY ◄────────────────────────────┐                    │
 *               │         │                                │                    │
 *         (click item) (next page btn / category done)     │                    │
 *               │         │                                │                    │
 *               ▼         └──► WAIT_CATEGORY ──────────────┘                   │
 *          WAIT_RECIPE                                                           │
 *               │                                                               │
 *       (fingerprint changed)                                                   │
 *               │                                                               │
 *               ▼                                                               │
 *          PARSE_RECIPE ──(goBack)──► WAIT_CATEGORY ──(all items done)──► WAIT_MAIN
 *                                                                               │
 *                                                         (all cats done)───────┘
 *                                                                               │
 *                                                                          FINISHED
 * </pre>
 */
public final class SlimefunGuideScraper {

    // ─── Timing constants ─────────────────────────────────────────────────────

    /** Ticks to wait after a click before re-evaluating state (20 ticks = 1 second). */
    private static final int ACTION_DELAY_TICKS      = 1;
    /** Ticks after which we declare a state "stuck" and recover (150 ticks = 7.5 seconds). */
    private static final int WATCHDOG_TICKS          = 50;
    /** How many retry attempts before permanently skipping a stuck slot (5 retries * 6 ticks = 1.5 seconds). */
    private static final int MAX_RETRIES_PER_SLOT    = 5;

    // ─── Utility-item blacklists ───────────────────────────────────────────────

    /** Item types that are Slimefun Guide UI chrome — never real categories or items. */
    private static final Set<net.minecraft.world.item.Item> CHROME_ITEMS = new HashSet<>(Arrays.asList(
            Items.BLACK_STAINED_GLASS_PANE,
            Items.WHITE_STAINED_GLASS_PANE,
            Items.GRAY_STAINED_GLASS_PANE,
            Items.LIGHT_GRAY_STAINED_GLASS_PANE,
            Items.BARRIER,
            Items.COMPASS,
            Items.BOOK,
            Items.WRITTEN_BOOK,
            Items.KNOWLEDGE_BOOK,
            Items.PLAYER_HEAD
    ));

    /**
     * Keywords that, when found in a slot's cleaned display name, identify it as a
     * UI navigation/utility button rather than a real Slimefun item or category.
     */
    private static final List<String> NAV_KEYWORDS = Arrays.asList(
            "search", "cari", "find",
            "setting", "pengaturan", "option", "opsi", "config",
            "info", "tutorial", "about", "tentang",
            "back", "kembali", "tutup",
            "close", "exit", "keluar",
            "next page", "previous page", "halaman",
            "lanjut", "sebelum"
    );

    // ─── FSM states ───────────────────────────────────────────────────────────

    public enum ScrapeState {
        INACTIVE,
        WAIT_FOR_MAIN,
        SCAN_MAIN_PAGE,
        WAIT_CATEGORY,
        SCAN_CATEGORY,
        WAIT_RECIPE,
        PARSE_AND_BACK,
        WAIT_BACK
    }

    // ─── Runtime state ────────────────────────────────────────────────────────

    private static ScrapeState  state               = ScrapeState.INACTIVE;
    private static int          tickDelay           = 0;
    private static int          watchdogTicks       = 0;

    /** Snapshot of all category slots on the main page (only built once per main page). */
    private static final List<Integer> categorySlots = new ArrayList<>();
    /** Index into {@link #categorySlots} pointing to the current category. */
    private static int          categoryIndex       = 0;

    /** Snapshot of item slots visible on the current category page. */
    private static final List<Integer> itemSlots    = new ArrayList<>();
    /** Index into {@link #itemSlots} pointing to the current item. */
    private static int          itemIndex           = 0;

    /** Fingerprint of the menu at the last transition point. */
    private static long         lastFingerprint     = -1;
    /** containerId of the last seen container (to detect GUI close). */
    private static int          lastContainerId     = -1;

    /** Retry count for the current slot (to avoid infinite loops on broken items). */
    private static int          retryCount          = 0;

    /** Running total of newly learned recipes this session. */
    private static int          totalRecipesLearned = 0;

    /** lastMenuId used by the passive scraper to avoid re-parsing the same screen. */
    private static int          passiveLastMenuId   = -1;

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Triggered by the "Auto Scrape" button injected into the Slimefun Guide screen.
     * Resets all state and starts the FSM.
     */
    public static void startScraping() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!(mc.screen instanceof AbstractContainerScreen<?>)) {
            sendMsg(mc, "§c[AutoScrape] Buka '/sf guide' dulu sebelum menekan tombol Auto Scrape!");
            return;
        }

        resetState();
        state     = ScrapeState.WAIT_FOR_MAIN;
        tickDelay = ACTION_DELAY_TICKS;
        sendMsg(mc, "§a[AutoScrape] Mulai menyusuri Slimefun Guide... jangan tutup GUI!");
    }

    /**
     * Called every client tick from {@link com.bapel_slimefun_mod.BapelSlimefunMod}'s
     * ClientTickEvent handler.
     */
    public static void tick(Minecraft mc) {
        if (mc.player == null) {
            if (state != ScrapeState.INACTIVE) forcedStop(mc, "Player hilang");
            return;
        }

        // ── Passive scraper (runs when auto-scrape is inactive) ──────────────
        if (state == ScrapeState.INACTIVE) {
            tickPassive(mc);
            return;
        }

        // ── GUI closed mid-scrape ────────────────────────────────────────────
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) {
            forcedStop(mc, "GUI ditutup");
            return;
        }

        // ── Tick-delay gate ──────────────────────────────────────────────────
        if (tickDelay > 0) {
            tickDelay--;
            return;
        }

        // ── Watchdog ─────────────────────────────────────────────────────────
        watchdogTicks++;
        if (watchdogTicks > WATCHDOG_TICKS) {
            sendMsg(mc, "§e[AutoScrape] Watchdog: state '" + state.name() + "' terlalu lama, recovery...");
            handleWatchdog(mc);
            return;
        }

        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null || menu.slots.size() < 54) return;
        lastContainerId = menu.containerId;

        // ── Dispatch ─────────────────────────────────────────────────────────
        switch (state) {
            case WAIT_FOR_MAIN  -> onWaitForMain(mc, menu);
            case SCAN_MAIN_PAGE -> onScanMainPage(mc, menu);
            case WAIT_CATEGORY  -> onWaitCategory(mc, menu);
            case SCAN_CATEGORY  -> onScanCategory(mc, menu);
            case WAIT_RECIPE    -> onWaitRecipe(mc, menu);
            case PARSE_AND_BACK -> onParseAndBack(mc, menu);
            case WAIT_BACK      -> onWaitBack(mc, menu);
            default             -> {}
        }
    }

    // ─── FSM handlers ─────────────────────────────────────────────────────────

    /** Wait until the main guide page is stable (fingerprint not changing). */
    private static void onWaitForMain(Minecraft mc, AbstractContainerMenu menu) {
        long fp = fingerprint(menu);
        if (fp == lastFingerprint) {
            // Stable — start scanning
            lastFingerprint = fp;
            resetWatchdog();
            state = ScrapeState.SCAN_MAIN_PAGE;
        } else {
            lastFingerprint = fp;
            tickDelay       = ACTION_DELAY_TICKS;
        }
    }

    /** Read all category slots on the current main page; click the next one. */
    private static void onScanMainPage(Minecraft mc, AbstractContainerMenu menu) {
        // Build category list only once per main-page load
        if (categorySlots.isEmpty()) {
            for (int i = 0; i < 45; i++) {
                if (isCategory(menu.slots.get(i).getItem())) {
                    categorySlots.add(i);
                }
            }
            if (categorySlots.isEmpty()) {
                forcedStop(mc, "Tidak ada kategori ditemukan di halaman ini");
                return;
            }
        }

        if (categoryIndex >= categorySlots.size()) {
            // All categories on this page done — check for a "Next Page" button
            int nextBtn = findNextPageButton(menu);
            if (nextBtn >= 0) {
                sendMsg(mc, "§7[AutoScrape] Lanjut ke halaman berikutnya...");
                categorySlots.clear();
                categoryIndex = 0;
                clickSlot(mc, menu, nextBtn);
                lastFingerprint = fingerprint(menu);
                state     = ScrapeState.WAIT_FOR_MAIN;
                tickDelay = ACTION_DELAY_TICKS;
            } else {
                // Done entirely
                state = ScrapeState.INACTIVE;
                sendMsg(mc, "§a[AutoScrape] ✔ Selesai! Total " + totalRecipesLearned + " resep baru berhasil direkam.");
            }
            return;
        }

        int slot = categorySlots.get(categoryIndex);
        String catName = strip(menu.slots.get(slot).getItem().getHoverName().getString());
        sendMsg(mc, "§b[AutoScrape] Kategori §e" + catName + " §b(" + (categoryIndex + 1) + "/" + categorySlots.size() + ")");

        itemSlots.clear();
        itemIndex = 0;

        lastFingerprint = fingerprint(menu);
        clickSlot(mc, menu, slot);
        state     = ScrapeState.WAIT_CATEGORY;
        tickDelay = ACTION_DELAY_TICKS;
        resetWatchdog();
    }

    /**
     * Wait until the category screen is stable (fingerprint changed from main).
     * This confirms the server sent us the category content.
     */
    private static void onWaitCategory(Minecraft mc, AbstractContainerMenu menu) {
        long fp = fingerprint(menu);
        if (fp != lastFingerprint) {
            lastFingerprint = fp;
            resetWatchdog();
            state = ScrapeState.SCAN_CATEGORY;
        } else {
            // Still waiting — bump delay
            tickDelay = ACTION_DELAY_TICKS / 2;
        }
    }

    /** Read all item slots on the current category page; click the next one. */
    private static void onScanCategory(Minecraft mc, AbstractContainerMenu menu) {
        // Build item list once per category-page load
        if (itemSlots.isEmpty()) {
            for (int i = 0; i < 45; i++) {
                if (isScrapableItem(menu.slots.get(i).getItem())) {
                    itemSlots.add(i);
                }
            }
        }

        if (itemIndex >= itemSlots.size()) {
            // Items on this page done — check for "Next Page" in category
            int nextBtn = findNextPageButton(menu);
            if (nextBtn >= 0) {
                itemSlots.clear();
                itemIndex = 0;
                clickSlot(mc, menu, nextBtn);
                lastFingerprint = fingerprint(menu);
                state     = ScrapeState.WAIT_CATEGORY;
                tickDelay = ACTION_DELAY_TICKS;
                resetWatchdog();
            } else {
                // Category fully scraped — go back to main
                goBack(mc, menu);
                categoryIndex++;
                state     = ScrapeState.WAIT_FOR_MAIN;
                tickDelay = ACTION_DELAY_TICKS;
                lastFingerprint = -1;   // force re-detect on main
                resetWatchdog();
            }
            return;
        }

        int slot = itemSlots.get(itemIndex);
        ItemStack target = menu.slots.get(slot).getItem();
        if (target.isEmpty()) {
            // Slot became empty (layout changed) — skip
            itemIndex++;
            return;
        }

        lastFingerprint = fingerprint(menu);
        clickSlot(mc, menu, slot);
        state     = ScrapeState.WAIT_RECIPE;
        tickDelay = ACTION_DELAY_TICKS;
        resetWatchdog();
    }

    /**
     * Wait until the recipe screen is stable (fingerprint changed from category).
     * This confirms the recipe data has been sent by the server.
     */
    private static void onWaitRecipe(Minecraft mc, AbstractContainerMenu menu) {
        long fp = fingerprint(menu);
        if (fp != lastFingerprint) {
            lastFingerprint = fp;
            retryCount      = 0;
            resetWatchdog();
            state = ScrapeState.PARSE_AND_BACK;
        } else {
            retryCount++;
            if (retryCount >= MAX_RETRIES_PER_SLOT) {
                // Item did not open a recipe screen — skip it
                retryCount = 0;
                itemIndex++;
                goBack(mc, menu);
                state     = ScrapeState.WAIT_CATEGORY;
                tickDelay = ACTION_DELAY_TICKS;
                lastFingerprint = -1;
            } else {
                tickDelay = ACTION_DELAY_TICKS;
            }
        }
    }

    /** Parse the current recipe screen and immediately click "Back". */
    private static void onParseAndBack(Minecraft mc, AbstractContainerMenu menu) {
        tryParseRecipe(menu, mc);
        itemIndex++;
        lastFingerprint = fingerprint(menu);
        goBack(mc, menu);
        state     = ScrapeState.WAIT_BACK;
        tickDelay = ACTION_DELAY_TICKS;
        resetWatchdog();
    }

    /**
     * Wait until we are back in the category view (fingerprint changed from recipe).
     * Then continue scanning items.
     */
    private static void onWaitBack(Minecraft mc, AbstractContainerMenu menu) {
        long fp = fingerprint(menu);
        if (fp != lastFingerprint) {
            lastFingerprint = fp;
            resetWatchdog();
            state = ScrapeState.SCAN_CATEGORY;
        } else {
            tickDelay = ACTION_DELAY_TICKS / 2;
        }
    }

    // ─── Watchdog recovery ────────────────────────────────────────────────────

    /**
     * When stuck, try pressing ESC → Back and return to main-page scanning.
     */
    private static void handleWatchdog(Minecraft mc) {
        AbstractContainerMenu menu = mc.player != null ? mc.player.containerMenu : null;
        resetWatchdog();

        if (menu != null && menu.slots.size() >= 54) {
            // Attempt "Back" click — worst case it does nothing
            goBack(mc, menu);
        }

        // Reset to a safe known state
        categorySlots.clear();
        itemSlots.clear();
        state           = ScrapeState.WAIT_FOR_MAIN;
        tickDelay       = ACTION_DELAY_TICKS * 2;
        lastFingerprint = -1;
    }

    // ─── Passive scraper ──────────────────────────────────────────────────────

    /**
     * Passive mode: whenever the player manually navigates to any recipe screen,
     * parse and store whatever recipe is visible — no clicking required.
     */
    private static void tickPassive(Minecraft mc) {
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) return;
        if (mc.player == null) return;

        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null || menu.slots.size() < 54) return;

        int menuId = menu.containerId;
        if (menuId == passiveLastMenuId) return;

        if (tryParseRecipe(menu, mc)) {
            passiveLastMenuId = menuId;
        }
    }

    // ─── Recipe parsing ───────────────────────────────────────────────────────

    /**
     * Attempts to read and store the Slimefun recipe shown on the current screen.
     *
     * <p>Layout (double-chest, 54 slots):
     * <pre>
     *  slot 10,11,12   ← ingredient row 1    slot 16 ← machine display
     *  slot 19,20,21   ← ingredient row 2    slot 25 ← output (alt)
     *  slot 28,29,30   ← ingredient row 3    slot 43 ← output (primary)
     * </pre>
     *
     * @return {@code true} if a valid Slimefun recipe was detected and stored.
     */
    private static boolean tryParseRecipe(AbstractContainerMenu menu, Minecraft mc) {
        try {
            // ── Resolve output ──────────────────────────────────────────────
            ItemStack output = menu.slots.get(43).getItem();
            if (output.isEmpty() || output.getItem() == Items.BARRIER) {
                output = menu.slots.get(25).getItem();
            }
            if (output.isEmpty() || output.getItem() == Items.BARRIER) return false;

            String outputKey = ItemKeyUtil.getKey(output);
            if (!outputKey.contains(":")) return false;

            // ── Resolve machine ─────────────────────────────────────────────
            String machineKey = "slimefun:enhanced_crafting_table";
            ItemStack machine = menu.slots.get(16).getItem();
            if (!machine.isEmpty() && machine.getItem() != Items.BARRIER) {
                String mk = ItemKeyUtil.getKey(machine);
                if (mk.contains(":")) machineKey = mk;
            }

            // ── Resolve ingredients ─────────────────────────────────────────
            final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
            Map<String, Integer> ingMap = new LinkedHashMap<>();
            for (int s : GRID) {
                if (s >= menu.slots.size()) continue;
                ItemStack ing = menu.slots.get(s).getItem();
                if (ing.isEmpty() || ing.getItem() == Items.BARRIER) continue;
                String key = ItemKeyUtil.getKey(ing);
                if (!key.isBlank()) {
                    ingMap.merge(key, ing.getCount() > 0 ? ing.getCount() : 1, Integer::sum);
                }
            }
            if (ingMap.isEmpty()) return false;

            List<RecipeIngredient> ingredients = new ArrayList<>();
            ingMap.forEach((k, v) -> ingredients.add(RecipeIngredient.of(k, v)));

            int amount = Math.max(1, output.getCount());

            // ── Dedup check ─────────────────────────────────────────────────
            for (RecipeEntry existing : SlimefunRecipeRegistry.get().getRecipesFor(outputKey)) {
                if (isDuplicate(existing, machineKey, ingredients)) return false;
            }

            // ── Store ───────────────────────────────────────────────────────
            RecipeEntry entry = new RecipeEntry(outputKey, ItemStack.EMPTY, amount, ingredients, machineKey);
            SlimefunRecipeRegistry.get().addRecipe(entry);
            RecipeCacheManager.save();
            totalRecipesLearned++;

            if (mc.player != null) {
                String name    = outputKey.substring(outputKey.indexOf(':') + 1).replace("_", " ");
                String machine2 = machineKey.substring(machineKey.indexOf(':') + 1).replace("_", " ");
                sendMsg(mc, "§b[AutoScrape] §aResep baru: §e" + name + " §7@§6" + machine2);
            }
            return true;

        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isDuplicate(RecipeEntry existing, String machineKey, List<RecipeIngredient> ingredients) {
        if (!existing.requiredMachine().equals(machineKey)) return false;
        if (existing.ingredients().size() != ingredients.size()) return false;
        for (RecipeIngredient ing : ingredients) {
            boolean found = existing.ingredients().stream()
                    .anyMatch(x -> x.itemKey().equals(ing.itemKey()) && x.amount() == ing.amount());
            if (!found) return false;
        }
        return true;
    }

    // ─── Slot classification ──────────────────────────────────────────────────

    /**
     * A slot is a <em>category</em> if it is not a known chrome item and its
     * display name does not match any navigation keyword.
     */
    private static boolean isCategory(ItemStack stack) {
        if (stack.isEmpty() || CHROME_ITEMS.contains(stack.getItem())) return false;
        String name = strip(stack.getHoverName().getString()).toLowerCase();
        return NAV_KEYWORDS.stream().noneMatch(name::contains);
    }

    /**
     * A slot is a <em>scrapable item</em> if it passes the chrome filter, the nav
     * keyword filter, and its display name matches the Slimefun item naming pattern
     * (generally all-caps underscored or plain words — not navigation labels).
     */
    private static boolean isScrapableItem(ItemStack stack) {
        if (stack.isEmpty() || CHROME_ITEMS.contains(stack.getItem())) return false;
        String name = strip(stack.getHoverName().getString()).toLowerCase();
        return NAV_KEYWORDS.stream().noneMatch(name::contains);
    }

    // ─── Navigation helpers ────────────────────────────────────────────────────

    /**
     * Locates a "Next Page" button in the bottom row (slots 45–53).
     *
     * @return slot index, or -1 if not found.
     */
    private static int findNextPageButton(AbstractContainerMenu menu) {
        for (int i = 45; i < Math.min(54, menu.slots.size()); i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (s.isEmpty()) continue;
            String name = strip(s.getHoverName().getString()).toLowerCase();
            if (name.contains("next") || name.contains("lanjut") || name.contains("»") || name.contains("forward")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Clicks the "Back" / "Kembali" button (bottom row), falling back to slot 45.
     */
    private static void goBack(Minecraft mc, AbstractContainerMenu menu) {
        int slot = 45;  // safe default
        for (int i = 45; i < Math.min(54, menu.slots.size()); i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (s.isEmpty()) continue;
            String name = strip(s.getHoverName().getString()).toLowerCase();
            if (name.contains("back") || name.contains("kembali") || name.contains("◀") || name.contains("←")) {
                slot = i;
                break;
            }
        }
        clickSlot(mc, menu, slot);
    }

    // ─── GUI fingerprinting ────────────────────────────────────────────────────

    /**
     * Produces a deterministic hash of the first 45 slots' item IDs and display names.
     * Used to detect when the server has sent a new GUI page.
     */
    private static long fingerprint(AbstractContainerMenu menu) {
        long hash = 1L;
        int limit = Math.min(45, menu.slots.size());
        for (int i = 0; i < limit; i++) {
            ItemStack s = menu.slots.get(i).getItem();
            String id   = s.isEmpty() ? "EMPTY" : net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(s.getItem()).toString();
            // mix id and display name
            hash = hash * 31 + id.hashCode();
            hash = hash * 31 + (s.isEmpty() ? 0 : s.getHoverName().getString().hashCode());
        }
        return hash;
    }

    // ─── Utility ─────────────────────────────────────────────────────────────

    private static void clickSlot(Minecraft mc, AbstractContainerMenu menu, int slot) {
        if (mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
        }
    }

    private static void resetState() {
        state               = ScrapeState.INACTIVE;
        tickDelay           = 0;
        watchdogTicks       = 0;
        categorySlots.clear();
        itemSlots.clear();
        categoryIndex       = 0;
        itemIndex           = 0;
        lastFingerprint     = -1;
        lastContainerId     = -1;
        retryCount          = 0;
        totalRecipesLearned = 0;
    }

    private static void resetWatchdog() {
        watchdogTicks = 0;
    }

    private static void forcedStop(Minecraft mc, String reason) {
        resetState();
        sendMsg(mc, "§c[AutoScrape] Dihentikan: " + reason);
    }

    /** Strips Minecraft color/format codes from a string. */
    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("§[0-9a-fk-orxz]", "");
    }

    private static void sendMsg(Minecraft mc, String msg) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(msg));
        }
    }
}
