package com.bapel_slimefun_mod.automation;

import net.minecraft.core.BlockPos;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Shared context yang dibawa sepanjang lifecycle satu chain.
 * Semua state handler membaca dan menulis lewat sini.
 */
public final class AutomationContext {

    // ── Chain Stack ───────────────────────────────────────────────
    private final Deque<CraftingJob> jobStack = new ConcurrentLinkedDeque<>();

    // ── Grid ──────────────────────────────────────────────────────
    private volatile BlockPos gridPos       = null;
    private volatile int      gridPage      = 0;
    private static final int  MAX_GRID_PAGES = 20;

    // ── Machine ───────────────────────────────────────────────────
    private volatile BlockPos machinePos    = null;

    // ── Timing ────────────────────────────────────────────────────
    private volatile long stateEntryTime    = 0L;

    // ── Timeouts (ms) ─────────────────────────────────────────────
    public static final long TIMEOUT_GUI_OPEN      = 3_000L;
    public static final long TIMEOUT_CRAFTING      = 10_000L;
    public static final long TIMEOUT_SCAN_PAGE     = 2_000L;

    // ── Circular detection ────────────────────────────────────────
    private final Set<String> visitedItemIds = new HashSet<>();

    // ── Config ────────────────────────────────────────────────────
    private static final int MAX_DEPTH       = 8;
    private static final int GRID_RADIUS     = 6;
    private static final int MACHINE_RADIUS  = 6;

    // ─────────────────────────────────────────────────────────────
    // Job Stack Operations
    // ─────────────────────────────────────────────────────────────

    public boolean pushJob(CraftingJob job) {
        if (jobStack.size() >= MAX_DEPTH) return false;
        if (visitedItemIds.contains(job.getItemKey())) return false; // circular guard
        visitedItemIds.add(job.getItemKey());
        jobStack.push(job);
        return true;
    }

    public Optional<CraftingJob> peekJob() {
        return Optional.ofNullable(jobStack.peek());
    }

    public Optional<CraftingJob> popJob() {
        CraftingJob job = jobStack.poll();
        if (job != null) visitedItemIds.remove(job.getItemKey());
        return Optional.ofNullable(job);
    }

    public boolean hasJobs()        { return !jobStack.isEmpty(); }
    public int     getDepth()       { return jobStack.size(); }
    public boolean isAtMaxDepth()   { return jobStack.size() >= MAX_DEPTH; }

    // ─────────────────────────────────────────────────────────────
    // Grid
    // ─────────────────────────────────────────────────────────────

    public Optional<BlockPos> getGridPos()            { return Optional.ofNullable(gridPos); }
    public void               setGridPos(BlockPos p)  { this.gridPos = p; }
    public void               clearGridPos()          { this.gridPos = null; }

    public int  getGridPage()                          { return gridPage; }
    public void nextGridPage()                         { gridPage++; }
    public void resetGridPage()                        { gridPage = 0; }
    public boolean isGridPageExhausted()               { return gridPage >= MAX_GRID_PAGES; }

    // ─────────────────────────────────────────────────────────────
    // Machine
    // ─────────────────────────────────────────────────────────────

    public Optional<BlockPos> getMachinePos()            { return Optional.ofNullable(machinePos); }
    public void               setMachinePos(BlockPos p)  { this.machinePos = p; }
    public void               clearMachinePos()          { this.machinePos = null; }

    // ─────────────────────────────────────────────────────────────
    // Timing
    // ─────────────────────────────────────────────────────────────

    public void  markStateEntry()                        { this.stateEntryTime = System.currentTimeMillis(); }
    public long  elapsedSinceEntry()                     { return System.currentTimeMillis() - stateEntryTime; }
    public boolean isTimedOut(long timeoutMs)            { return elapsedSinceEntry() > timeoutMs; }

    // ─────────────────────────────────────────────────────────────
    // Misc
    // ─────────────────────────────────────────────────────────────

    public int getGridRadius()    { return GRID_RADIUS; }
    public int getMachineRadius() { return MACHINE_RADIUS; }

    public void reset() {
        jobStack.clear();
        visitedItemIds.clear();
        gridPos = null;
        machinePos = null;
        gridPage = 0;
        stateEntryTime = 0L;
    }
}
