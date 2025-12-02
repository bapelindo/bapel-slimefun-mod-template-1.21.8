package com.bapel_slimefun_mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration class for mod settings
 * COMPLETE VERSION - Replace your entire ModConfig.java with this file
 */
public class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("bapel-slimefun-mod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "bapel-slimefun-mod.json";
    
    // Automation settings
    private boolean automationEnabled = true;
    private int automationDelayMs = 200;
    private boolean debugMode = false;
    
    // Overlay settings (NEW)
    private boolean autoShowOverlay = false;
    private boolean enableOverlayAnimations = true;
    private int overlayPositionX = 10;
    private int overlayPositionY = 60;
    
    /**
     * Private constructor for singleton-like usage
     */
    private ModConfig() {}
    
    /**
     * Load configuration from file
     */
    public static ModConfig load() {
        Path configPath = getConfigPath();
        
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                ModConfig config = GSON.fromJson(reader, ModConfig.class);
                LOGGER.info("Configuration loaded from {}", configPath);
                return config;
            } catch (Exception e) {
                LOGGER.error("Failed to load configuration, using defaults", e);
            }
        }
        
        // Create default config
        ModConfig config = new ModConfig();
        config.save(); // Save default config
        return config;
    }
    
    /**
     * Save configuration to file
     */
    public void save() {
        Path configPath = getConfigPath();
        
        try {
            // Create parent directories if they don't exist
            Files.createDirectories(configPath.getParent());
            
            // Write config to file
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(this, writer);
                LOGGER.info("Configuration saved to {}", configPath);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save configuration", e);
        }
    }
    
    /**
     * Get config file path
     */
    private static Path getConfigPath() {
        return Paths.get("config", CONFIG_FILE);
    }
    
    // ========================================
    // AUTOMATION SETTINGS - Getters/Setters
    // ========================================
    
    /**
     * Check if automation is enabled
     */
    // 1. Tambahkan variable ini di bagian atas (bersama variable automationEnabled dll)
    private boolean rememberLastRecipe = true; 

    // 2. Tambahkan Getter dan Setter ini di bagian bawah
    public boolean isRememberLastRecipe() {
        return rememberLastRecipe;
    }

    public void setRememberLastRecipe(boolean rememberLastRecipe) {
        this.rememberLastRecipe = rememberLastRecipe;
        save();
    }
    public boolean isAutomationEnabled() {
        return automationEnabled;
    }
    
    /**
     * Set automation enabled state
     */
    public void setAutomationEnabled(boolean automationEnabled) {
        this.automationEnabled = automationEnabled;
        save();
    }
    
    /**
     * Get automation delay in milliseconds
     */
    public int getAutomationDelayMs() {
        return automationDelayMs;
    }
    
    /**
     * Set automation delay in milliseconds
     */
    public void setAutomationDelayMs(int automationDelayMs) {
        this.automationDelayMs = Math.max(50, Math.min(1000, automationDelayMs));
        save();
    }
    
    /**
     * Check if debug mode is enabled
     */
    public boolean isDebugMode() {
        return debugMode;
    }
    
    /**
     * Set debug mode enabled state
     */
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        save();
    }
    
    // ========================================
    // OVERLAY SETTINGS - Getters/Setters (NEW)
    // ========================================
    
    /**
     * Check if overlay should auto-show when opening machine
     */
    public boolean isAutoShowOverlay() {
        return autoShowOverlay;
    }
    
    /**
     * Set auto-show overlay setting
     */
    public void setAutoShowOverlay(boolean autoShowOverlay) {
        this.autoShowOverlay = autoShowOverlay;
        save();
    }
    
    /**
     * Check if overlay animations are enabled
     */
    public boolean isEnableOverlayAnimations() {
        return enableOverlayAnimations;
    }
    
    /**
     * Set overlay animations enabled
     */
    public void setEnableOverlayAnimations(boolean enableOverlayAnimations) {
        this.enableOverlayAnimations = enableOverlayAnimations;
        save();
    }
    
    /**
     * Get overlay X position
     */
    public int getOverlayPositionX() {
        return overlayPositionX;
    }
    
    /**
     * Set overlay X position
     */
    public void setOverlayPositionX(int overlayPositionX) {
        this.overlayPositionX = Math.max(0, overlayPositionX);
        save();
    }
    
    /**
     * Get overlay Y position
     */
    public int getOverlayPositionY() {
        return overlayPositionY;
    }
    
    /**
     * Set overlay Y position
     */
    public void setOverlayPositionY(int overlayPositionY) {
        this.overlayPositionY = Math.max(0, overlayPositionY);
        save();
    }
    
    
    /**
     * String representation of config
     */
    @Override
    public String toString() {
        return "ModConfig{" +
                "automationEnabled=" + automationEnabled +
                ", automationDelayMs=" + automationDelayMs +
                ", debugMode=" + debugMode +
                ", autoShowOverlay=" + autoShowOverlay +
                ", enableOverlayAnimations=" + enableOverlayAnimations +
                ", overlayPositionX=" + overlayPositionX +
                ", overlayPositionY=" + overlayPositionY +
                '}';
    }
}
