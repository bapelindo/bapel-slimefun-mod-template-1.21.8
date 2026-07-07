package com.bapel_slimefun_mod.automation.config;

import com.google.gson.*;  
import net.fabricmc.loader.api.FabricLoader;  
import org.slf4j.Logger;  
import org.slf4j.LoggerFactory;  

import java.io.*;  
import java.nio.file.*;  

/**  
 * GSON-based config loader/saver.  
 */  
public final class AutomationConfigLoader {  

    private static final Logger LOGGER   = LoggerFactory.getLogger("AutomationConfig");  
    private static final String FILENAME = "bapelmod-automation.json";  
    private static final Gson   GSON     = new GsonBuilder()  
        .setPrettyPrinting()  
        .serializeNulls()  
        .create();  

    private AutomationConfigLoader() {}  

    // ─────────────────────────────────────────────────────────────  

    public static void load() {  
        Path configPath = getConfigPath();  

        if (!Files.exists(configPath)) {  
            LOGGER.info("Config not found, creating default: {}", configPath);  
            save(); // buat file default  
            return;  
        }  

        try (Reader reader = Files.newBufferedReader(configPath)) {  
            AutomationConfig loaded = GSON.fromJson(reader, AutomationConfig.class);  
            if (loaded != null) {  
                AutomationConfig.setInstance(loaded);  
                LOGGER.info("Config loaded from: {}", configPath);  
            }  
        } catch (IOException | JsonParseException e) {  
            LOGGER.error("Failed to load config, using defaults: {}", e.getMessage());  
        }  
    }  

    public static void save() {  
        Path configPath = getConfigPath();  

        try {  
            Files.createDirectories(configPath.getParent());  
            try (Writer writer = Files.newBufferedWriter(configPath)) {  
                GSON.toJson(AutomationConfig.get(), writer);  
                LOGGER.info("Config saved to: {}", configPath);  
            }  
        } catch (IOException e) {  
            LOGGER.error("Failed to save config: {}", e.getMessage());  
        }  
    }  

    private static Path getConfigPath() {  
        return FabricLoader.getInstance()  
            .getConfigDir()  
            .resolve(FILENAME);  
    }  
}
