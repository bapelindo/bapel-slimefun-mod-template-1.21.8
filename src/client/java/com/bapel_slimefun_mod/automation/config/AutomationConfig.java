package com.bapel_slimefun_mod.automation.config;

/**  
 * Semua nilai konfigurasi automation engine.  
 */  
public final class AutomationConfig {  

    // ── Radius ────────────────────────────────────────────────────  
    public int gridSearchRadius    = 6;  
    public int machineSearchRadius = 6;  

    // ── Timeouts (ms) ─────────────────────────────────────────────  
    public long guiOpenTimeoutMs    = 3_000L;  
    public long craftingTimeoutMs   = 10_000L;  
    public long scanPageTimeoutMs   = 2_000L;  

    // ── Limits ────────────────────────────────────────────────────  
    public int  maxChainDepth       = 8;  
    public int  maxGridPages        = 20;  

    // ── Camera ────────────────────────────────────────────────────  
    public float cameraLookSpeed    = 5.0f;   // derajat per tick  
    public float cameraLookTolerance= 2.0f;   // derajat toleransi  

    // ── UI ────────────────────────────────────────────────────────  
    public boolean showHudOverlay   = true;  
    public int     hudX             = 4;  
    public int     hudY             = 4;  

    // ── Debug ─────────────────────────────────────────────────────  
    public boolean debugLogging     = false;  

    // Singleton instance  
    private static AutomationConfig instance;  
    public static AutomationConfig get() {  
        if (instance == null) instance = new AutomationConfig();  
        return instance;  
    }  
    static void setInstance(AutomationConfig cfg) { instance = cfg; }  

    private AutomationConfig() {}  
}
