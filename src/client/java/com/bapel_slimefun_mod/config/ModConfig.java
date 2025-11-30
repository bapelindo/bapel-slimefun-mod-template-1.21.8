package com.bapel_slimefun_mod.config;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Configuration manager for mod settings
 */
public class ModConfig {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/bapel-slimefun-mod.json");
    
    // Config values with defaults
    private boolean automationEnabled = true;
    private boolean showOverlay = true;
    private int automationDelayMs = 500;
    private boolean debugMode = false;
    private boolean soundEnabled = true;
    
    /**
     * Load config from file
     */
    public static ModConfig load() {
        // Create config directory if it doesn't exist
        CONFIG_FILE.getParentFile().mkdirs();
        
        // Load existing config or create default
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ModConfig config = GSON.fromJson(reader, ModConfig.class);
                BapelSlimefunMod.LOGGER.info("Loaded config from file");
                return config;
            } catch (IOException e) {
                BapelSlimefunMod.LOGGER.error("Failed to load config, using defaults", e);
            }
        }
        
        // Return default config
        ModConfig config = new ModConfig();
        config.save(); // Save default config
        return config;
    }
    
    /**
     * Save config to file
     */
    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(this, writer);
            BapelSlimefunMod.LOGGER.info("Saved config to file");
        } catch (IOException e) {
            BapelSlimefunMod.LOGGER.error("Failed to save config", e);
        }
    }
    
    // Getters and setters
    
    public boolean isAutomationEnabled() {
        return automationEnabled;
    }
    
    public void setAutomationEnabled(boolean enabled) {
        this.automationEnabled = enabled;
        save();
    }
    
    public boolean isShowOverlay() {
        return showOverlay;
    }
    
    public void setShowOverlay(boolean show) {
        this.showOverlay = show;
        save();
    }
    
    public int getAutomationDelayMs() {
        return automationDelayMs;
    }
    
    public void setAutomationDelayMs(int delay) {
        this.automationDelayMs = Math.max(100, Math.min(2000, delay)); // Clamp 100-2000ms
        save();
    }
    
    public boolean isDebugMode() {
        return debugMode;
    }
    
    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
        save();
    }
    
    public boolean isSoundEnabled() {
        return soundEnabled;
    }
    
    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
        save();
    }
}