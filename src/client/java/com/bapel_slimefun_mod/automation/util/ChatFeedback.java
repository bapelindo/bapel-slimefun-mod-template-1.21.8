package com.bapel_slimefun_mod.automation.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**  
 * Utility untuk mengirim feedback ke pemain via actionbar atau chat.  
 */  
public final class ChatFeedback {  

    private ChatFeedback() {}  

    /** Tampilkan di action bar (bawah layar, tidak permanent) */  
    public static void actionBar(String message) {  
        Minecraft mc = Minecraft.getInstance();  
        if (mc.player == null) return;  
        mc.player.sendOverlayMessage(Component.literal(message));  
    }  

    /** Tampilkan di chat (permanent) */  
    public static void chat(String message) {  
        Minecraft mc = Minecraft.getInstance();  
        if (mc.player == null) return;  
        mc.player.sendSystemMessage(Component.literal(message));  
    }  

    // ── Preset ────────────────────────────────────────────────────  

    public static void info(String msg)    { actionBar("§e[BapelMod] " + msg); }  
    public static void success(String msg) { actionBar("§a[BapelMod] ✓ " + msg); }  
    public static void warn(String msg)    { actionBar("§6[BapelMod] ⚠ " + msg); }  
    public static void error(String msg)   { actionBar("§c[BapelMod] ✗ " + msg); }  
}
