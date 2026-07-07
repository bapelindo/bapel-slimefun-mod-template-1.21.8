package com.bapel_slimefun_mod.automation.fastmachine;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.automation.RecipeData;
import com.bapel_slimefun_mod.automation.RecipeDatabase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Explicit state-machine orchestrator for the FastMachine auto-craft chain.
 *
 * <p>The old inline chain code had five fatal flaws that prevented it from working:
 * <ol>
 *   <li>UAM cancelled the chain on every container-close (including normal navigation).</li>
 *   <li>{@code tickNetworkGrid()} was never called during the 2-tick open delay.</li>
 *   <li>{@code rightClickBlock()} failed silently when the player held an item.</li>
 *   <li>Race condition between {@code advanceChain()} and {@code onContainerOpen()}.</li>
 *   <li>Item matching used Slimefun item-IDs; Slimefun items must be matched by display name.</li>
 * </ol>
 *
 * <h2>State machine</h2>
 * <pre>
 * IDLE → start() → RESOLVING
 *   RESOLVING:
 *     ingredients present?  → OPENING_MACHINE
 *     grid not tried?       → OPENING_GRID
 *     sub-recipe available? → push sub-job → RESOLVING (loop)
 *     else                  → abort
 *   OPENING_GRID  → onNetworkGridOpened() → EXTRACTING_GRID
 *   EXTRACTING_GRID → items done → CLOSING_GRID
 *   CLOSING_GRID  → cooldown → RESOLVING
 *   OPENING_MACHINE → onMachineOpened() → CRAFTING
 *   CRAFTING → onSubJobCompleted() → RESOLVING (or IDLE if stack empty)
 * </pre>
 */
public final class ChainOrchestrator {

    // Singleton
    private static final ChainOrchestrator INSTANCE = new ChainOrchestrator();
    public static ChainOrchestrator get() { return INSTANCE; }
    private ChainOrchestrator() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Phase enum
    // ─────────────────────────────────────────────────────────────────────────
    public enum Phase { IDLE, RESOLVING, OPENING_GRID, EXTRACTING_GRID, CLOSING_GRID, OPENING_MACHINE, CRAFTING }

    // ─────────────────────────────────────────────────────────────────────────
    // CraftJob
    // ─────────────────────────────────────────────────────────────────────────
    public static final class CraftJob {
        public final String recipeName;
        public final String sfMachineId;
        public final int    qty;
        public boolean triedGrid = false;
        public CraftJob(String recipeName, String sfMachineId, int qty) {
            this.recipeName = recipeName; this.sfMachineId = sfMachineId; this.qty = qty;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State fields
    // ─────────────────────────────────────────────────────────────────────────
    private Phase phase = Phase.IDLE;
    private final Deque<CraftJob> stack = new ArrayDeque<>();
    private static final int MAX_DEPTH = 8;

    /** Display-name → still-needed count of items to pull from the Network Grid. */
    private final Map<String, Integer> pendingIngredients = new LinkedHashMap<>();

    private boolean loggedGridContents = false;
    private int  closingGridCooldown   = 0;
    private static final int GRID_CLOSE_TICKS = 3;

    private BlockPos pendingOpenPos       = null;
    private int      rightClickDelayTicks = 0;

    private long openRequestedAtMs = 0L;
    private static final long OPEN_TIMEOUT_MS = 6_000L;

    private long nextGridActionMs = 0L;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /** Entry point called from RecipeOverlayRenderer on Shift+Enter. */
    public void start(String recipeName, String sfMachineId, int qty) {
        reset();
        stack.push(new CraftJob(recipeName, sfMachineId, qty));
        phase = Phase.RESOLVING;
        showBar("§e⛓ Chain dimulai: §f" + recipeName + " ×" + qty);
        BapelSlimefunMod.LOGGER.info("[Chain] start recipeName={} machine={} qty={}", recipeName, sfMachineId, qty);
    }

    public Phase getPhase()   { return phase; }
    public boolean isActive() { return phase != Phase.IDLE; }

    /** Called every client tick by UnifiedAutomationManager — before FastMachineAutomationHandler.tick(). */
    public void tick(Minecraft mc) {
        if (phase == Phase.IDLE) return;
        LocalPlayer player = mc.player;
        if (player == null) return;

        // Delayed right-click: hold all state transitions until click fires
        if (pendingOpenPos != null) {
            rightClickDelayTicks--;
            if (rightClickDelayTicks <= 0) {
                executeRightClick(mc, pendingOpenPos);
                pendingOpenPos = null;
            }
            return;
        }

        // Open-timeout guard
        if ((phase == Phase.OPENING_GRID || phase == Phase.OPENING_MACHINE)
                && openRequestedAtMs > 0
                && System.currentTimeMillis() - openRequestedAtMs > OPEN_TIMEOUT_MS) {
            abort("§c✗ Timeout: GUI tidak terbuka setelah " + (OPEN_TIMEOUT_MS / 1000) + "s — chain dibatalkan.");
            return;
        }

        switch (phase) {
            case RESOLVING       -> tickResolving(mc, player);
            case EXTRACTING_GRID -> tickExtractingGrid(mc, player);
            case CLOSING_GRID    -> tickClosingGrid();
            default              -> {} // OPENING_GRID / OPENING_MACHINE / CRAFTING: wait for callbacks
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Callbacks
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called by UnifiedAutomationManager.onMachineOpen() when a FastMachine opens.
     * @return true if the orchestrator consumed the event (i.e. chain is active).
     */
    public boolean onMachineOpened(String detectedFastMachineId) {
        if (phase != Phase.OPENING_MACHINE) return false;
        if (stack.isEmpty()) { abort(null); return false; }

        CraftJob job = stack.peek();
        String expectedId = FastMachineAutomationHandler.getFastMachineIdFromSlimefun(job.sfMachineId);
        if (expectedId == null || !expectedId.equalsIgnoreCase(detectedFastMachineId)) {
            abort("§c✗ Mesin salah terbuka (expected=" + expectedId + ", got=" + detectedFastMachineId + ").");
            return true;
        }

        openRequestedAtMs = 0L;
        phase = Phase.CRAFTING;
        FastMachineAutomationHandler.lockRecipeByNameDirect(job.recipeName, job.qty);
        showBar("§a🎯 Crafting: §e" + job.recipeName + " §7(×" + job.qty + ")");
        BapelSlimefunMod.LOGGER.info("[Chain] Machine OK — crafting {}", job.recipeName);
        return true;
    }

    /**
     * Called by UnifiedAutomationManager.onMachineOpen() when the Network Grid opens.
     * @return true if the orchestrator consumed the event.
     */
    public boolean onNetworkGridOpened(AbstractContainerMenu menu) {
        if (phase != Phase.OPENING_GRID) return false;
        openRequestedAtMs = 0L;
        loggedGridContents = false;
        nextGridActionMs = System.currentTimeMillis() + 500L;
        phase = Phase.EXTRACTING_GRID;
        showBar("§e📦 Grid terbuka — scanning bahan...");
        BapelSlimefunMod.LOGGER.info("[Chain] Network Grid opened pending={}", pendingIngredients.keySet());
        return true;
    }

    /**
     * Called by FastMachineAutomationHandler when the craft target is reached.
     * This is the primary completion signal for the CRAFTING phase.
     */
    public void onSubJobCompleted() {
        if (phase != Phase.CRAFTING) return;
        if (stack.isEmpty()) { abort(null); return; }
        CraftJob done = stack.pop();
        BapelSlimefunMod.LOGGER.info("[Chain] Sub-job done: {}", done.recipeName);
        if (stack.isEmpty()) {
            phase = Phase.IDLE;
            showBar("§a🎉 Chain selesai! " + done.recipeName + " berhasil dibuat.");
            BapelSlimefunMod.LOGGER.info("[Chain] Chain completed successfully.");
        } else {
            showBar("§a✔ §f" + done.recipeName + " §aselesai — lanjut ke berikutnya...");
            phase = Phase.RESOLVING;
        }
    }

    /**
     * Called by UnifiedAutomationManager.onContainerClose().
     * The chain must NOT be aborted just because a GUI closes — closing is normal.
     */
    public void onGuiClosed() {
        if (phase == Phase.EXTRACTING_GRID) {
            // Grid was closed (manually or by us)
            phase = Phase.CLOSING_GRID;
            closingGridCooldown = GRID_CLOSE_TICKS;
        }
        // OPENING_GRID / OPENING_MACHINE: we closed the previous GUI deliberately — ignore
        // CRAFTING: FastMachineAutomationHandler.onContainerClose() handles this
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tick helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void tickResolving(Minecraft mc, LocalPlayer player) {
        if (stack.isEmpty()) { phase = Phase.IDLE; return; }
        if (stack.size() > MAX_DEPTH) {
            abort("§c✗ Rantai terlalu dalam (>" + MAX_DEPTH + ") — kemungkinan resep saling melingkar."); return;
        }

        CraftJob job = stack.peek();
        RecipeData recipe = findRecipeByDisplayName(job.recipeName);
        if (recipe == null) { abort("§c✗ Resep \"" + job.recipeName + "\" tidak ditemukan!"); return; }

        String fmId = FastMachineAutomationHandler.getFastMachineIdFromSlimefun(job.sfMachineId);
        if (fmId == null) { abort("§c✗ Mesin \"" + job.sfMachineId + "\" bukan FastMachine yang didukung."); return; }

        Map<String, Integer> missing = computeMissingByDisplayName(recipe, job.qty, player);

        // Case A: all ingredients in inventory
        if (missing.isEmpty()) { openMachineForJob(mc, player, job, fmId); return; }

        // Case B: try Network Grid once per job
        if (!job.triedGrid) {
            job.triedGrid = true;
            BlockPos gridPos = FastMachineRecipeMemory.getClosestPosition("NETWORK_GRID", player.position(), mc.level);
            if (gridPos != null && gridPos.distToCenterSqr(player.position()) <= 36.0) {
                pendingIngredients.clear();
                pendingIngredients.putAll(missing);
                showBar("§e📦 Mengambil bahan dari Network Grid untuk §f" + job.recipeName + "...");
                openGrid(mc, gridPos);
                return;
            }
        }

        // Case C: sub-recipe on another machine
        for (Map.Entry<String, Integer> e : missing.entrySet()) {
            String missingName = e.getKey();
            int    missingQty  = e.getValue();
            RecipeData sub = findProducibleRecipeForDisplayName(missingName, player, mc);
            if (sub == null) continue;
            RecipeData.RecipeOutput subOut = findOutputByDisplayName(sub, missingName);
            if (subOut == null) continue;

            // Anti-circular
            boolean circular = false;
            for (CraftJob j : stack) { if (j.recipeName.equalsIgnoreCase(subOut.getDisplayName())) { circular = true; break; } }
            if (circular) { abort("§c✗ Resep melingkar: §f" + missingName); return; }

            int perCraft = Math.max(1, subOut.getAmount());
            int craftsNeeded = (int) Math.ceil(missingQty / (double) perCraft);
            showBar("§d⛓ Perlu §f" + missingName + " §d— craft §e" + subOut.getDisplayName() + " §ddulu.");
            stack.push(new CraftJob(subOut.getDisplayName(), sub.getMachineId(), craftsNeeded));
            return; // re-enter tickResolving next tick with new top-of-stack
        }

        abort("§c✗ Tidak bisa membuat §f" + job.recipeName + "§c — bahan " + missing.keySet() + " tidak tersedia.");
    }

    private void tickExtractingGrid(Minecraft mc, LocalPlayer player) {
        if (pendingIngredients.isEmpty()) {
            showBar("§a✔ Semua bahan berhasil diambil dari Grid!");
            closeCurrentScreen(mc, player);
            phase = Phase.CLOSING_GRID;
            closingGridCooldown = GRID_CLOSE_TICKS;
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextGridActionMs) return;

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;

        int guiSlots     = Math.max(0, menu.slots.size() - 36);
        int contentSlots = Math.min(guiSlots, 45);
        if (guiSlots == 0) return;

        if (!loggedGridContents) {
            loggedGridContents = true;
            player.sendSystemMessage(Component.literal("§d[Chain] Grid: " + contentSlots + " slots | seeking: " + pendingIngredients.keySet()));
            for (int i = 0; i < contentSlots && i < menu.slots.size(); i++) {
                ItemStack s = menu.slots.get(i).getItem();
                if (s.isEmpty()) continue;
                String name = FastMachineDetector.stripColorCodes(s.getHoverName().getString()).trim();
                player.sendSystemMessage(Component.literal("§7  [" + i + "] " + name + " x" + s.getCount()));
            }
        }

        // Phase 1: shift-click matching items
        for (int i = 0; i < contentSlots && i < menu.slots.size(); i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (s.isEmpty()) continue;
            String itemName = FastMachineDetector.stripColorCodes(s.getHoverName().getString()).trim();
            String key = findMatchingPendingKey(itemName);
            if (key == null) continue;

            if (!hasRoomFor(player, s)) {
                abort("§c✗ Inventory penuh! Tidak bisa mengambil " + itemName);
                closeCurrentScreen(mc, player);
                return;
            }

            int stackSize  = s.getCount();
            int cur        = pendingIngredients.getOrDefault(key, 0);
            int newMissing = Math.max(0, cur - stackSize);
            clickSlot(mc, menu, i, 0, ContainerInput.QUICK_MOVE);
            if (newMissing <= 0) pendingIngredients.remove(key);
            else pendingIngredients.put(key, newMissing);

            showBar("§e📦 Extracted §f" + itemName + " §7(×" + stackSize + ") | Remaining: " + pendingIngredients);
            nextGridActionMs = now + 200L;
            return;
        }

        // Phase 2: next-page button
        int nextPageSlot = -1;
        for (int i = contentSlots; i < guiSlots && i < menu.slots.size(); i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (s.isEmpty()) continue;
            String name = s.getHoverName().getString().toLowerCase();
            if (name.contains("next") || name.contains(">") || name.contains("selanjutnya") || name.contains("forward")) {
                nextPageSlot = i; break;
            }
        }
        if (nextPageSlot != -1) {
            clickSlot(mc, menu, nextPageSlot, 0, ContainerInput.PICKUP);
            nextGridActionMs = now + 700L;
            loggedGridContents = false;
            showBar("§e📦 Berpindah halaman Grid...");
        } else {
            abort("§c✗ Bahan tidak ditemukan di Grid: " + pendingIngredients.keySet());
            closeCurrentScreen(mc, player);
        }
    }

    private void tickClosingGrid() {
        if (--closingGridCooldown <= 0) {
            closingGridCooldown = 0;
            phase = Phase.RESOLVING;
            BapelSlimefunMod.LOGGER.info("[Chain] Grid closed, back to RESOLVING");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void openMachineForJob(Minecraft mc, LocalPlayer player, CraftJob job, String fmId) {
        if (FastMachineAutomationHandler.isActive()
                && fmId.equalsIgnoreCase(FastMachineAutomationHandler.getCurrentMachineId())) {
            phase = Phase.CRAFTING;
            FastMachineAutomationHandler.lockRecipeByNameDirect(job.recipeName, job.qty);
            showBar("§a🎯 Crafting (same machine): §e" + job.recipeName);
            return;
        }
        BlockPos pos = FastMachineRecipeMemory.getClosestPosition(fmId, player.position(), mc.level);
        if (pos == null) { abort("§c✗ Koordinat §f" + fmId + "§c belum di-cache! Buka manual sekali dulu."); return; }
        if (pos.distToCenterSqr(player.position()) > 36.0) { abort("§c✗ §f" + fmId + "§c terlalu jauh (maks 6 blok)."); return; }
        closeCurrentScreen(mc, player);
        phase = Phase.OPENING_MACHINE;
        openRequestedAtMs = System.currentTimeMillis();
        scheduleRightClick(mc, pos);
        showBar("§e🔁 Membuka §f" + fmId + "§e...");
        BapelSlimefunMod.LOGGER.info("[Chain] Navigate to machine {} at {}", fmId, pos);
    }

    private void openGrid(Minecraft mc, BlockPos gridPos) {
        closeCurrentScreen(mc, mc.player);
        phase = Phase.OPENING_GRID;
        openRequestedAtMs = System.currentTimeMillis();
        scheduleRightClick(mc, gridPos);
        BapelSlimefunMod.LOGGER.info("[Chain] Navigate to Grid at {}", gridPos);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // rightClickBlock with 2-tick delay + empty-hand swap (FATAL 3 fix)
    // ─────────────────────────────────────────────────────────────────────────

    private void scheduleRightClick(Minecraft mc, BlockPos pos) {
        aimAt(mc, pos);
        pendingOpenPos = pos;
        rightClickDelayTicks = 2;
    }

    private void executeRightClick(Minecraft mc, BlockPos pos) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.level == null) {
            abort("§c✗ Player/level null saat membuka blok."); return;
        }

        // Cache position reliably (before server responds)
        if (phase == Phase.OPENING_GRID) {
            FastMachineRecipeMemory.cachePosition("NETWORK_GRID", pos);
        } else if (phase == Phase.OPENING_MACHINE && !stack.isEmpty()) {
            String fmId = FastMachineAutomationHandler.getFastMachineIdFromSlimefun(stack.peek().sfMachineId);
            if (fmId != null) FastMachineRecipeMemory.cachePosition(fmId, pos);
        }

        // Swap to empty hand if needed (FATAL 3 fix)
        int savedSlot  = getSelectedSlot(player.getInventory());
        int emptySlot  = findEmptyHotbarSlot(player);
        boolean swapped = false;
        if (emptySlot != -1 && !player.getInventory().getItem(savedSlot).isEmpty()) {
            setSelectedSlot(player.getInventory(), emptySlot);
            swapped = true;
        }

        aimAt(mc, pos);

        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
            new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
            net.minecraft.core.Direction.UP, pos, false);
        mc.gameMode.useItemOn(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

        if (swapped) setSelectedSlot(player.getInventory(), savedSlot);
        BapelSlimefunMod.LOGGER.info("[Chain] useItemOn({}) phase={}", pos, phase);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Recipe / item helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static RecipeData findRecipeByDisplayName(String name) {
        if (!RecipeDatabase.isInitialized()) RecipeDatabase.initialize();
        List<RecipeData> c = RecipeDatabase.searchRecipesByOutput(name);
        if (c == null || c.isEmpty()) return null;
        for (RecipeData r : c) {
            if (r.getPrimaryOutput() != null && r.getPrimaryOutput().getDisplayName().equalsIgnoreCase(name)) return r;
        }
        return c.get(0);
    }

    private static RecipeData findProducibleRecipeForDisplayName(String displayName, LocalPlayer player, Minecraft mc) {
        if (!RecipeDatabase.isInitialized()) RecipeDatabase.initialize();
        List<RecipeData> c = RecipeDatabase.searchRecipesByOutput(displayName);
        if (c == null) return null;
        RecipeData fallback = null;
        for (RecipeData r : c) {
            String fmId = FastMachineAutomationHandler.getFastMachineIdFromSlimefun(r.getMachineId());
            if (fmId == null) continue;
            if (findOutputByDisplayName(r, displayName) == null) continue;
            if (mc.level != null && FastMachineRecipeMemory.getClosestPosition(fmId, player.position(), mc.level) != null) return r;
            if (fallback == null) fallback = r;
        }
        return fallback;
    }

    private static RecipeData.RecipeOutput findOutputByDisplayName(RecipeData r, String name) {
        for (RecipeData.RecipeOutput out : r.getOutputs()) {
            if (out.getDisplayName().equalsIgnoreCase(name)) return out;
        }
        return null;
    }

    /**
     * Computes missing ingredients using display names as keys.
     * Slimefun items must be matched by hover name, not vanilla item ID.
     */
    private static Map<String, Integer> computeMissingByDisplayName(RecipeData recipe, int qty, LocalPlayer player) {
        Map<String, Integer> missing = new LinkedHashMap<>();
        if (recipe == null) return missing;

        // Build inventory: display-name → total count
        Map<String, Integer> inv = new HashMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            String name = FastMachineDetector.stripColorCodes(s.getHoverName().getString()).trim();
            inv.merge(name, s.getCount(), Integer::sum);
        }

        for (com.bapel_slimefun_mod.automation.RecipeHandler.RecipeIngredient ing : recipe.getInputs()) {
            if (ing == null) continue;
            String ingId = ing.getItemId();
            if (ingId == null || ingId.equalsIgnoreCase("AIR") || ingId.isEmpty()) continue;

            String dispName = resolveDisplayName(ingId);
            int needed = qty; // 1 slot = 1 ingredient per craft in Slimefun

            int have = inv.getOrDefault(dispName, 0);
            if (have == 0) have = inv.getOrDefault(ingId, 0); // fallback for vanilla items

            if (have < needed) missing.merge(dispName, needed - have, Integer::sum);
        }
        return missing;
    }

    private static String resolveDisplayName(String itemId) {
        if (!RecipeDatabase.isInitialized()) RecipeDatabase.initialize();
        List<RecipeData> c = RecipeDatabase.searchRecipesByOutput(itemId);
        if (c != null) {
            for (RecipeData r : c) {
                for (RecipeData.RecipeOutput out : r.getOutputs()) {
                    if (out.getItemId().equalsIgnoreCase(itemId)) return out.getDisplayName();
                }
            }
        }
        // Fallback: title-case the ID
        String[] words = itemId.toLowerCase().split("[_\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private String findMatchingPendingKey(String itemName) {
        for (String key : pendingIngredients.keySet()) {
            if (key.equalsIgnoreCase(itemName)) return key;
            if (itemName.toLowerCase().contains(key.toLowerCase())
                    || key.toLowerCase().contains(itemName.toLowerCase())) return key;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    private static void aimAt(Minecraft mc, BlockPos pos) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        double dx = (pos.getX() + 0.5) - p.getX();
        double dy = (pos.getY() + 0.5) - (p.getY() + p.getEyeHeight());
        double dz = (pos.getZ() + 0.5) - p.getZ();
        p.setYRot((float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        p.setXRot((float)-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx*dx + dz*dz))));
    }

    private static void clickSlot(Minecraft mc, AbstractContainerMenu menu, int slot, int btn, ContainerInput type) {
        try { mc.gameMode.handleContainerInput(menu.containerId, slot, btn, type, mc.player); }
        catch (Exception e) { BapelSlimefunMod.LOGGER.error("[Chain] Click error slot={}", slot, e); }
    }

    private static boolean hasRoomFor(LocalPlayer player, ItemStack stack) {
        if (player.getInventory().getFreeSlot() != -1) return true;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack inv = player.getInventory().getItem(i);
            if (!inv.isEmpty() && ItemStack.isSameItemSameComponents(inv, stack) && inv.getCount() < inv.getMaxStackSize()) return true;
        }
        return false;
    }

    private static int findEmptyHotbarSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) if (player.getInventory().getItem(i).isEmpty()) return i;
        return -1;
    }

    private static void showBar(String msg) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) p.sendOverlayMessage(Component.literal(msg));
    }

    public void abort(String message) {
        if (message != null) { showBar(message); BapelSlimefunMod.LOGGER.warn("[Chain] ABORT: {}", message); }
        reset();
    }

    private void reset() {
        phase = Phase.IDLE; stack.clear(); pendingIngredients.clear();
        pendingOpenPos = null; rightClickDelayTicks = 0; openRequestedAtMs = 0L;
        nextGridActionMs = 0L; loggedGridContents = false; closingGridCooldown = 0;
    }

    private static int getSelectedSlot(net.minecraft.world.entity.player.Inventory inv) {
        try {
            java.lang.reflect.Field f = net.minecraft.world.entity.player.Inventory.class.getDeclaredField("selected");
            f.setAccessible(true);
            return f.getInt(inv);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field f = net.minecraft.world.entity.player.Inventory.class.getDeclaredField("selectedSlot");
                f.setAccessible(true);
                return f.getInt(inv);
            } catch (Exception e2) {
                return 0;
            }
        }
    }

    private static void setSelectedSlot(net.minecraft.world.entity.player.Inventory inv, int slot) {
        try {
            java.lang.reflect.Field f = net.minecraft.world.entity.player.Inventory.class.getDeclaredField("selected");
            f.setAccessible(true);
            f.setInt(inv, slot);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field f = net.minecraft.world.entity.player.Inventory.class.getDeclaredField("selectedSlot");
                f.setAccessible(true);
                f.setInt(inv, slot);
            } catch (Exception e2) {
                // fall through
            }
        }
    }

    private static void closeCurrentScreen(Minecraft mc, LocalPlayer player) {
        if (player != null) {
            player.closeContainer();
        }
        if (mc.screen != null) {
            mc.setScreen(null);
        }
    }
}
