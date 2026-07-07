package com.bapel_slimefun_mod.automation.util;

import java.util.*;

/**  
 * Lightweight tick-delay scheduler untuk client thread.  
 */  
public final class TickScheduler {  

    private static final TickScheduler INSTANCE = new TickScheduler();  
    public static TickScheduler get() { return INSTANCE; }  

    private record DelayedTask(int ticksLeft, Runnable action) {}  

    private final List<DelayedTask> tasks = new ArrayList<>();  

    private TickScheduler() {}  

    /**  
     * Jalankan action setelah N tick.  
     */  
    public void runAfter(int ticks, Runnable action) {  
        tasks.add(new DelayedTask(ticks, action));  
    }  

    /**  
     * Dipanggil setiap client tick.  
     */  
    public void tick() {  
        if (tasks.isEmpty()) return;  

        List<DelayedTask> remaining = new ArrayList<>();  
        for (DelayedTask task : tasks) {  
            if (task.ticksLeft() <= 0) {  
                task.action().run();  
            } else {  
                remaining.add(new DelayedTask(task.ticksLeft() - 1, task.action()));  
            }  
        }  

        tasks.clear();  
        tasks.addAll(remaining);  
    }  

    public void cancelAll() {  
        tasks.clear();  
    }  
}
