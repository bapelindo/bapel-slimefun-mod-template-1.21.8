package com.bapel_slimefun_mod.automation.network;

import net.minecraft.resources.Identifier;

/**  
 * Semua custom packet ID yang digunakan mod ini.  
 */  
public final class PacketIds {  

    private PacketIds() {}  

    // ── Server → Client ───────────────────────────────────────────  

    public static final Identifier RECIPE_SYNC_FULL =  
        Identifier.fromNamespaceAndPath("bapelmod", "recipe_sync_full");  

    public static final Identifier RECIPE_SYNC_DELTA =  
        Identifier.fromNamespaceAndPath("bapelmod", "recipe_sync_delta");  

    public static final Identifier CRAFT_CONFIRM =  
        Identifier.fromNamespaceAndPath("bapelmod", "craft_confirm");  

    public static final Identifier MACHINE_STATUS =  
        Identifier.fromNamespaceAndPath("bapelmod", "machine_status");  

    // ── Client → Server ───────────────────────────────────────────  

    public static final Identifier REQUEST_RECIPE_SYNC =  
        Identifier.fromNamespaceAndPath("bapelmod", "request_recipe_sync");  
}
