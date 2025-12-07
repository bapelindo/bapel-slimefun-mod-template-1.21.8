
package com.bapel_slimefun_mod.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PerformanceMonitor {
    
    private static boolean visible = false;
    private static final Map<String, Long> startTimes = new ConcurrentHashMap<>();
    private static final Map<String, Stats> stats = new ConcurrentHashMap<>();
    private static final List<Long> frameTimes = Collections.synchronizedList(new ArrayList<>());
    private static long lastFrame = System.nanoTime();
    private static long startTime = System.currentTimeMillis();
    
    public static void start(String name) {
        startTimes.put(name, System.nanoTime());
    }
    
    public static void end(String name) {
        Long start = startTimes.remove(name);
        if (start != null) {
            stats.computeIfAbsent(name, k -> new Stats()).add(System.nanoTime() - start);
        }
    }
    
    public static void toggle() {
        visible = !visible;
    }
    
    public static boolean isVisible() {
        return visible;
    }
    
    public static void trackFrame() {
        long now = System.nanoTime();
        frameTimes.add(now - lastFrame);
        lastFrame = now;
        if (frameTimes.size() > 100) frameTimes.remove(0);
    }
    
public static void render(GuiGraphics g) {
        if (!visible) return;
        
        try {
            int x = 5, y = 5;
            int width = 450;
            int height = 300;
            
            // Dark background (Warna background sudah benar menggunakan ARGB)
            g.fill(x, y, x + width, y + height, 0xE0000000);
            
            // White border (Warna border sudah benar menggunakan ARGB)
            g.fill(x, y, x + width, y + 1, 0xFFFFFFFF);
            g.fill(x, y + height - 1, x + width, y + height, 0xFFFFFFFF);
            g.fill(x, y, x + 1, y + height, 0xFFFFFFFF);
            g.fill(x + width - 1, y, x + width, y + height, 0xFFFFFFFF);
            
            Minecraft mc = Minecraft.getInstance();
            int yy = y + 6;
            int leftX = x + 6;
            int rightX = x + 230;
            
            // PERBAIKAN DI SINI:
            // Ganti semua 0xFFFFFF menjadi 0xFFFFFFFF agar teks tidak transparan
            
            // Header
            g.drawString(mc.font, "=== PERFORMANCE MONITOR ===", leftX, yy, 0xFFFFFFFF, true); // Saya ubah shadow jadi true agar lebih jelas
            yy += 12;
            
            // FPS and Frame Time
            int fps = getFPS();
            double avgFrameMs = getAvgFrameTime() / 1000000.0;
            double minFrameMs = getMinFrameTime() / 1000000.0;
            double maxFrameMs = getMaxFrameTime() / 1000000.0;
            
            g.drawString(mc.font, String.format("FPS: %d (%.2fms avg)", fps, avgFrameMs), leftX, yy, 0xFFFFFFFF, false);
            yy += 10;
            g.drawString(mc.font, String.format("Frame: %.2f / %.2f / %.2f ms", minFrameMs, avgFrameMs, maxFrameMs), leftX, yy, 0xFFFFFFFF, false);
            yy += 10;
            
            // Uptime
            long uptime = (System.currentTimeMillis() - startTime) / 1000;
            g.drawString(mc.font, String.format("Uptime: %d:%02d:%02d", uptime / 3600, (uptime % 3600) / 60, uptime % 60), leftX, yy, 0xFFFFFFFF, false);
            yy += 10;
            
            // Memory usage
            Runtime runtime = Runtime.getRuntime();
            long usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1048576;
            long maxMem = runtime.maxMemory() / 1048576;
            g.drawString(mc.font, String.format("Memory: %dMB / %dMB", usedMem, maxMem), leftX, yy, 0xFFFFFFFF, false);
            yy += 12;
            
            // Separator
            g.drawString(mc.font, "----------------------------------------", leftX, yy, 0xFFFFFFFF, false);
            yy += 10;
            
            // Method stats header
            g.drawString(mc.font, "Method Name", leftX, yy, 0xFFFFFFFF, false);
            g.drawString(mc.font, "Avg", rightX, yy, 0xFFFFFFFF, false);
            g.drawString(mc.font, "Min", rightX + 50, yy, 0xFFFFFFFF, false);
            g.drawString(mc.font, "Max", rightX + 100, yy, 0xFFFFFFFF, false);
            g.drawString(mc.font, "Calls", rightX + 150, yy, 0xFFFFFFFF, false);
            yy += 10;
            
            g.drawString(mc.font, "----------------------------------------", leftX, yy, 0xFFFFFFFF, false);
            yy += 10;
            
            // Sort methods by average time
            List<Map.Entry<String, Stats>> list = new ArrayList<>(stats.entrySet());
            list.sort((a, b) -> Double.compare(b.getValue().avg(), a.getValue().avg()));
            
            // Display methods
            int count = 0;
            long totalTime = 0;
            int totalCalls = 0;
            
            for (Map.Entry<String, Stats> e : list) {
                if (count++ > 14) break;
                
                Stats s = e.getValue();
                double avgMs = s.avg() / 1000000.0;
                double minMs = s.min / 1000000.0;
                double maxMs = s.max / 1000000.0;
                
                totalTime += s.total;
                totalCalls += s.count;
                
                String name = e.getKey();
                if (name.length() > 26) name = name.substring(0, 24) + "..";
                
                g.drawString(mc.font, name, leftX, yy, 0xFFFFFFFF, false);
                g.drawString(mc.font, String.format("%.2f", avgMs), rightX, yy, 0xFFFFFFFF, false);
                g.drawString(mc.font, String.format("%.2f", minMs), rightX + 50, yy, 0xFFFFFFFF, false);
                g.drawString(mc.font, String.format("%.2f", maxMs), rightX + 100, yy, 0xFFFFFFFF, false);
                g.drawString(mc.font, String.format("%d", s.count), rightX + 150, yy, 0xFFFFFFFF, false);
                yy += 10;
            }
            
            if (list.isEmpty()) {
                g.drawString(mc.font, "No data yet - use mod features to collect stats", leftX, yy, 0xFFFFFFFF, false);
                yy += 10;
            } else {
                yy += 2;
                g.drawString(mc.font, "----------------------------------------", leftX, yy, 0xFFFFFFFF, false);
                yy += 10;
                
                // Total stats
                double totalMs = totalTime / 1000000.0;
                g.drawString(mc.font, String.format("Total: %.2fms across %d calls", totalMs, totalCalls), leftX, yy, 0xFFFFFFFF, false);
                yy += 10;
                
                // Methods tracked
                g.drawString(mc.font, String.format("Tracking: %d methods", stats.size()), leftX, yy, 0xFFFFFFFF, false);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static int getFPS() {
        if (frameTimes.isEmpty()) return 0;
        long total = 0;
        for (Long t : frameTimes) total += t;
        long avg = total / frameTimes.size();
        return avg == 0 ? 0 : (int)(1000000000L / avg);
    }
    
    private static long getAvgFrameTime() {
        if (frameTimes.isEmpty()) return 0;
        long total = 0;
        for (Long t : frameTimes) total += t;
        return total / frameTimes.size();
    }
    
    private static long getMinFrameTime() {
        if (frameTimes.isEmpty()) return 0;
        long min = Long.MAX_VALUE;
        for (Long t : frameTimes) min = Math.min(min, t);
        return min;
    }
    
    private static long getMaxFrameTime() {
        if (frameTimes.isEmpty()) return 0;
        long max = 0;
        for (Long t : frameTimes) max = Math.max(max, t);
        return max;
    }
    
    public static void reset() {
        stats.clear();
        frameTimes.clear();
        startTime = System.currentTimeMillis();
    }
    
    private static class Stats {
        long total = 0;
        long min = Long.MAX_VALUE;
        long max = 0;
        int count = 0;
        
        synchronized void add(long time) {
            total += time;
            min = Math.min(min, time);
            max = Math.max(max, time);
            count++;
        }
        
        double avg() {
            return count == 0 ? 0 : (double)total / count;
        }
    }
}