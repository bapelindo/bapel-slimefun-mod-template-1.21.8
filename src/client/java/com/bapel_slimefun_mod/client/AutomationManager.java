package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Manager untuk mengelola automation dengan mudah dari client side
 * Menyediakan method untuk start, stop, dan configure automation
 */
public class AutomationManager {
    
    /**
     * Mulai automation dengan recipe yang dipilih
     * @param recipeId ID dari recipe yang akan dijalankan
     * @param showMessage Apakah menampilkan pesan ke player
     * @return true jika berhasil dimulai, false jika gagal
     */
    public static boolean startAutomation(String recipeId, boolean showMessage) {
        try {
            // Validasi recipe ID
            if (recipeId == null || recipeId.isEmpty()) {
                BapelSlimefunMod.LOGGER.warn("[AutomationManager] Cannot start automation: recipe ID is null or empty");
                return false;
            }
            
            // Validasi machine terbuka
            if (!MachineAutomationHandler.isActive()) {
                BapelSlimefunMod.LOGGER.warn("[AutomationManager] Cannot start automation: no machine is active");
                if (showMessage) {
                    showPlayerMessage("§cCannot start automation: No machine detected!", false);
                }
                return false;
            }
            
            // Set recipe
            MachineAutomationHandler.setSelectedRecipe(recipeId);
            
            // Aktifkan automation
            MachineAutomationHandler.setAutomationEnabled(true);
            
            BapelSlimefunMod.LOGGER.info("[AutomationManager] Automation started for recipe: {}", recipeId);
            
            if (showMessage) {
                SlimefunMachineData machine = MachineAutomationHandler.getCurrentMachine();
                String machineName = machine != null ? machine.getName() : "Unknown";
                
                showPlayerMessage("§a✓ Automation Started!", false);
                showPlayerMessage("§7Machine: §f" + machineName, false);
                showPlayerMessage("§7Recipe: §f" + recipeId, false);
                showPlayerMessage("§7System akan otomatis menaruh input dan mengambil output", false);
            }
            
            return true;
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[AutomationManager] Failed to start automation", e);
            if (showMessage) {
                showPlayerMessage("§cFailed to start automation: " + e.getMessage(), false);
            }
            return false;
        }
    }
    
    /**
     * Mulai automation dengan recipe yang dipilih (dengan pesan default)
     */
    public static boolean startAutomation(String recipeId) {
        return startAutomation(recipeId, true);
    }
    
    /**
     * Stop automation
     */
    public static void stopAutomation(boolean showMessage) {
        MachineAutomationHandler.setAutomationEnabled(false);
        
        BapelSlimefunMod.LOGGER.info("[AutomationManager] Automation stopped");
        
        if (showMessage) {
            showPlayerMessage("§e⏸ Automation Stopped", true);
        }
    }
    
    /**
     * Stop automation (dengan pesan default)
     */
    public static void stopAutomation() {
        stopAutomation(true);
    }
    
    /**
     * Toggle automation on/off
     */
    public static void toggleAutomation() {
        if (MachineAutomationHandler.isAutomationEnabled()) {
            stopAutomation(true);
        } else {
            // Jika ada recipe yang dipilih, start automation
            String selectedRecipe = MachineAutomationHandler.getSelectedRecipe();
            if (selectedRecipe != null) {
                startAutomation(selectedRecipe, true);
            } else {
                // Jika belum ada recipe, aktifkan automation saja
                MachineAutomationHandler.setAutomationEnabled(true);
                showPlayerMessage("§aAutomation Enabled", true);
            }
        }
    }
    
    /**
     * Check apakah automation sedang berjalan
     */
    public static boolean isRunning() {
        return MachineAutomationHandler.isAutomationEnabled() && 
               MachineAutomationHandler.isActive();
    }
    
    /**
     * Get status automation sebagai string
     */
    public static String getStatusString() {
        if (!MachineAutomationHandler.isActive()) {
            return "§7No Machine Detected";
        }
        
        boolean enabled = MachineAutomationHandler.isAutomationEnabled();
        String selectedRecipe = MachineAutomationHandler.getSelectedRecipe();
        SlimefunMachineData machine = MachineAutomationHandler.getCurrentMachine();
        
        StringBuilder status = new StringBuilder();
        status.append(enabled ? "§a●" : "§c●");
        status.append(" Automation: ");
        status.append(enabled ? "§aENABLED" : "§cDISABLED");
        
        if (machine != null) {
            status.append(" §7| Machine: §f").append(machine.getName());
        }
        
        if (selectedRecipe != null) {
            status.append(" §7| Recipe: §f").append(selectedRecipe);
        }
        
        return status.toString();
    }
    
    /**
     * Show info lengkap tentang automation status
     */
    public static void showDetailedStatus() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        mc.player.displayClientMessage(Component.literal("§6§l=== Automation Status ==="), false);
        
        if (!MachineAutomationHandler.isActive()) {
            mc.player.displayClientMessage(Component.literal("§cNo machine detected"), false);
            return;
        }
        
        boolean enabled = MachineAutomationHandler.isAutomationEnabled();
        SlimefunMachineData machine = MachineAutomationHandler.getCurrentMachine();
        String selectedRecipe = MachineAutomationHandler.getSelectedRecipe();
        
        mc.player.displayClientMessage(
            Component.literal("§7Status: " + (enabled ? "§aENABLED" : "§cDISABLED")), 
            false
        );
        
        if (machine != null) {
            mc.player.displayClientMessage(
                Component.literal("§7Machine: §f" + machine.getName()), 
                false
            );
        }
        
        if (selectedRecipe != null) {
            mc.player.displayClientMessage(
                Component.literal("§7Recipe: §f" + selectedRecipe), 
                false
            );
        } else {
            mc.player.displayClientMessage(
                Component.literal("§7Recipe: §eNo recipe selected"), 
                false
            );
        }
        
        mc.player.displayClientMessage(Component.literal("§6§l===================="), false);
    }
    
    /**
     * Helper method untuk menampilkan pesan ke player
     */
    private static void showPlayerMessage(String message, boolean actionBar) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(message), actionBar);
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[AutomationManager] Failed to show player message", e);
        }
    }
    
    /**
     * Clear selected recipe
     */
    public static void clearSelectedRecipe() {
        MachineAutomationHandler.setSelectedRecipe(null);
        BapelSlimefunMod.LOGGER.info("[AutomationManager] Recipe cleared");
    }
    
    /**
     * Quick start automation - untuk digunakan dari keybind atau command
     * Jika sudah ada recipe yang dipilih, start automation
     * Jika belum, buka recipe selection screen
     */
    public static void quickStart() {
        String selectedRecipe = MachineAutomationHandler.getSelectedRecipe();
        
        if (selectedRecipe != null) {
            // Ada recipe yang dipilih, langsung start
            startAutomation(selectedRecipe, true);
        } else {
            // Belum ada recipe, buka selection screen
            if (!MachineAutomationHandler.isActive()) {
                showPlayerMessage("§cBuka machine terlebih dahulu!", true);
                return;
            }
            
            showPlayerMessage("§eSelect a recipe first...", true);
            // TODO: Buka RecipeSelectionScreen di sini
        }
    }
}