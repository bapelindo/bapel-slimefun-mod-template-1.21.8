package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * ✅ FIXED VERSION: Multi-machine cache system with proximity detection
 * 
 * BUGS FIXED:
 * 1. Removed excessive debug logging
 * 2. Fixed encoding issues in log messages
 * 3. Improved error handling
 * 4. Cleaned up redundant code
 * 5. Better null checks
 */
public class UnifiedAutomationManager {
    
    private static ModConfig config;
    private static SlimefunMachineData currentMachine = null;
    private static boolean automationEnabled = false;
    private static long lastTickTime = 0;
    
    // Track current cached machine reference
    private static MultiblockCacheManager.CachedMultiblock currentCachedMachine = null;
    
    public static void init(ModConfig cfg) {
        config = cfg;
        MachineAutomationHandler.init(cfg);
        MultiblockAutomationHandler.init(cfg);
        
        // Load multiblock cache
        MultiblockCacheManager.load();
        
        BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Initialized with {} cached multiblocks", 
            MultiblockCacheManager.getAllMachines().size());
    }
    
    /**
     * Called when user constructs a multiblock ("successfully constructed")
     * This caches the machine at the player's position
     */
    public static void onMultiblockConstructed(SlimefunMachineData machine) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        if (player == null || machine == null) return;
        
        BlockPos playerPos = player.blockPosition();
        
        // Cache this machine at player position
        MultiblockCacheManager.addMachine(machine, playerPos);
        
        // Set as current machine
        currentMachine = machine;
        currentCachedMachine = MultiblockCacheManager.getMachineAt(playerPos);
        
        BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Multiblock constructed and cached: {} at {}", 
            machine.getName(), playerPos);
        
        // Show confirmation
        if (mc.player != null) {
            mc.player.displayClientMessage(
                Component.literal("§a✓ " + machine.getName() + " cached! Press R for recipes."),
                false
            );
        }
        
        // AUTO-LOAD: Check if this machine has a remembered recipe
        if (currentCachedMachine != null && currentCachedMachine.getLastSelectedRecipe() != null) {
            String rememberedRecipe = currentCachedMachine.getLastSelectedRecipe();
            
            BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Found remembered recipe: {}", rememberedRecipe);
            
            // Show recipe overlay with this recipe pre-selected
            try {
                RecipeOverlayRenderer.show(machine);
                
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                        Component.literal("§e⚡ Last recipe: " + getRecipeDisplayName(rememberedRecipe)),
                        true
                    );
                }
            } catch (Exception e) {
                BapelSlimefunMod.LOGGER.error("[UnifiedAuto] Failed to show overlay", e);
            }
        }
    }
    
    /**
     * Called when opening a container (might be dispenser near multiblock)
     */
    public static void onMachineOpen(String title) {
        if (title == null) return;
        
        currentMachine = SlimefunDataLoader.getMachineByTitle(title);
        
        // Special handling for Dispenser - might be a multiblock
        if ("Dispenser".equalsIgnoreCase(title) || title.contains("Dispenser")) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            
            if (player != null) {
                BlockPos playerPos = player.blockPosition();
                
                // Find nearest cached multiblock
                currentCachedMachine = MultiblockCacheManager.findNearestMachine(playerPos);
                
                if (currentCachedMachine != null) {
                    // Load the cached machine
                    String machineId = currentCachedMachine.getMachineId();
                    currentMachine = SlimefunDataLoader.getMultiblockById(machineId);
                    
                    if (currentMachine != null) {
                        BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Detected nearby multiblock: {} at distance {}", 
                            currentMachine.getName(),
                            Math.sqrt(playerPos.distSqr(currentCachedMachine.getPosition())));
                        
                        // AUTO-LOAD: Load last recipe if exists
                        String lastRecipe = currentCachedMachine.getLastSelectedRecipe();
                        if (lastRecipe != null && config != null && config.isRememberLastRecipe()) {
                            BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Auto-loading recipe: {}", lastRecipe);
                            
                            // Set recipe and auto-start
                            MultiblockAutomationHandler.setSelectedRecipe(lastRecipe);
                            
                            // Auto-start automation
                            automationEnabled = true;
                            config.setAutomationEnabled(true);
                            
                            if (mc.player != null) {
                                mc.player.displayClientMessage(
                                    Component.literal("§a✓ Auto-loaded recipe: " + getRecipeDisplayName(lastRecipe)),
                                    false
                                );
                                mc.player.displayClientMessage(
                                    Component.literal("§a▶ Automation STARTED!"),
                                    true
                                );
                            }
                            
                            return; // Don't show overlay in auto mode
                        }
                        
                        // Show overlay if no auto-load
                        if (config != null && config.isAutoShowOverlay()) {
                            try {
                                RecipeOverlayRenderer.show(currentMachine);
                            } catch (Exception e) {
                                BapelSlimefunMod.LOGGER.error("[UnifiedAuto] Failed to show overlay", e);
                            }
                        }
                        
                        return;
                    }
                }
            }
        }
        
        // Normal electric machine handling
        if (currentMachine != null) {
            BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Detected {} machine: {}", 
                currentMachine.isMultiblock() ? "MULTIBLOCK" : "ELECTRIC",
                currentMachine.getId());
            
            if (currentMachine.isElectric()) {
                MachineAutomationHandler.onContainerOpen(title);
            } else if (currentMachine.isMultiblock()) {
                if (config != null && config.isAutoShowOverlay()) {
                    try {
                        RecipeOverlayRenderer.show(currentMachine);
                    } catch (Exception e) {
                        BapelSlimefunMod.LOGGER.error("[UnifiedAuto] Failed to show overlay", e);
                    }
                }
            }
        }
    }
    
    /**
     * Called when machine GUI is closed
     */
    public static void onMachineClose() {
        if (currentMachine != null) {
            BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Closing machine: {}", currentMachine.getId());
            
            if (currentMachine.isElectric()) {
                MachineAutomationHandler.onContainerClose();
            }
        }
        
        // Keep currentCachedMachine for proximity detection
        // Only clear currentMachine (GUI reference)
        currentMachine = null;
    }
    
    /**
     * Main tick handler
     */
    public static void tick() {
        if (!automationEnabled) return;
        
        // For multiblock, we don't need GUI open - use cached machine
        SlimefunMachineData machine = getCurrentMachine();
        
        // Throttle ticks
        long now = System.currentTimeMillis();
        if (config != null && now - lastTickTime < 50) {
            return;
        }
        lastTickTime = now;
        
        if (machine != null) {
            // Normal case: GUI is open
            if (machine.isElectric()) {
                MachineAutomationHandler.tick();
            } else if (machine.isMultiblock()) {
                MultiblockAutomationHandler.tick(machine);
            }
        } else if (currentCachedMachine != null) {
            // For multiblock automation without GUI
            // Reconstruct SlimefunMachineData from cached info
            SlimefunMachineData cachedMachine = SlimefunDataLoader.getMultiblockById(
                currentCachedMachine.getMachineId()
            );
            if (cachedMachine != null && cachedMachine.isMultiblock()) {
                MultiblockAutomationHandler.tick(cachedMachine);
            }
        }
    }
    
    /**
     * Set selected recipe + save to cache + AUTO-START AUTOMATION
     */
    public static void setSelectedRecipe(String recipeId) {
        if (recipeId == null) {
            BapelSlimefunMod.LOGGER.warn("[UnifiedAuto] Cannot set null recipe!");
            return;
        }
        
        SlimefunMachineData machine = getCurrentMachine();
        if (machine == null) {
            BapelSlimefunMod.LOGGER.warn("[UnifiedAuto] Cannot set recipe - no machine active!");
            return;
        }
        
        BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Setting recipe: {} for {}", 
            recipeId, machine.getId());
        
        if (machine.isElectric()) {
            MachineAutomationHandler.setSelectedRecipe(recipeId);
        } else if (machine.isMultiblock()) {
            MultiblockAutomationHandler.setSelectedRecipe(recipeId);
            
            // Save recipe to cache for this machine
            if (currentCachedMachine != null && config != null && config.isRememberLastRecipe()) {
                MultiblockCacheManager.updateRecipe(
                    currentCachedMachine.getPosition(), 
                    recipeId
                );
                BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Recipe saved to cache");
            }
        }
        
        // AUTO-START AUTOMATION
        automationEnabled = true;
        if (config != null) {
            config.setAutomationEnabled(true);
        }
        
        BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Automation auto-started after recipe selection");
        
        // Show confirmation
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String displayName = getRecipeDisplayName(recipeId);
            mc.player.displayClientMessage(
                Component.literal("§a✓ Recipe selected: " + displayName), 
                false
            );
            mc.player.displayClientMessage(
                Component.literal("§a▶ Automation STARTED!"), 
                true
            );
        }
    }
    
    /**
     * Get selected recipe
     */
    public static String getSelectedRecipe() {
        SlimefunMachineData machine = getCurrentMachine();
        if (machine == null) return null;
        
        if (machine.isElectric()) {
            return MachineAutomationHandler.getSelectedRecipe();
        } else if (machine.isMultiblock()) {
            return MultiblockAutomationHandler.getSelectedRecipe();
        }
        
        return null;
    }
    
    /**
     * Get current machine (includes cached multiblock)
     */
    public static SlimefunMachineData getCurrentMachine() {
        return currentMachine;
    }
    
    /**
     * Toggle automation on/off
     */
    public static void toggleAutomation() {
        automationEnabled = !automationEnabled;
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        if (player != null) {
            if (automationEnabled) {
                player.displayClientMessage(
                    Component.literal("§a[Slimefun] Automation STARTED ▶"), 
                    false
                );
                BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Automation ENABLED");
            } else {
                player.displayClientMessage(
                    Component.literal("§c[Slimefun] Automation STOPPED ■"), 
                    false
                );
                BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Automation DISABLED");
            }
        }
        
        if (config != null) {
            config.setAutomationEnabled(automationEnabled);
        }
        MachineAutomationHandler.setAutomationEnabled(automationEnabled);
    }
    
    public static boolean isAutomationEnabled() {
        return automationEnabled;
    }
    
    public static boolean isActive() {
        return getCurrentMachine() != null || currentCachedMachine != null;
    }
    
    public static RecipeHandler.RecipeSummary getRecipeSummary() {
        SlimefunMachineData machine = getCurrentMachine();
        if (machine == null) return null;
        
        if (machine.isElectric()) {
            return MachineAutomationHandler.getRecipeSummary();
        } else if (machine.isMultiblock()) {
            return MultiblockAutomationHandler.getRecipeSummary(machine);
        }
        
        return null;
    }
    
    /**
     * Get display name for recipe
     */
    private static String getRecipeDisplayName(String recipeId) {
        if (recipeId == null) return "Unknown";
        
        try {
            RecipeData recipe = RecipeDatabase.getRecipe(recipeId);
            if (recipe != null && recipe.getPrimaryOutput() != null) {
                return recipe.getPrimaryOutput().getDisplayName();
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[UnifiedAuto] Failed to get recipe display name", e);
        }
        
        // Fallback: format the ID
        String[] words = recipeId.toLowerCase().split("_");
        StringBuilder displayName = new StringBuilder();
        
        for (String word : words) {
            if (displayName.length() > 0) {
                displayName.append(" ");
            }
            if (word.length() > 0) {
                displayName.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    displayName.append(word.substring(1));
                }
            }
        }
        
        return displayName.toString();
    }
    
    /**
     * Debug info
     */
    public static void printDebugInfo() {
        BapelSlimefunMod.LOGGER.info("========================================");
        BapelSlimefunMod.LOGGER.info("  UNIFIED AUTOMATION STATUS");
        BapelSlimefunMod.LOGGER.info("========================================");
        BapelSlimefunMod.LOGGER.info("  Enabled: {}", automationEnabled);
        BapelSlimefunMod.LOGGER.info("  Active: {}", isActive());
        
        SlimefunMachineData machine = getCurrentMachine();
        if (machine != null) {
            BapelSlimefunMod.LOGGER.info("  Machine: {} ({})", 
                machine.getId(),
                machine.isMultiblock() ? "MULTIBLOCK" : "ELECTRIC");
            BapelSlimefunMod.LOGGER.info("  Selected Recipe: {}", getSelectedRecipe());
            
            if (currentCachedMachine != null) {
                BapelSlimefunMod.LOGGER.info("  Cached: {} at {}", 
                    currentCachedMachine.getMachineName(),
                    currentCachedMachine.getPosition());
            }
        } else {
            BapelSlimefunMod.LOGGER.info("  Machine: none");
        }
        
        BapelSlimefunMod.LOGGER.info("");
        BapelSlimefunMod.LOGGER.info("  Total Cached: {} machines", 
            MultiblockCacheManager.getAllMachines().size());
        BapelSlimefunMod.LOGGER.info("========================================");
        
        // Print cache contents
        MultiblockCacheManager.printCache();
    }
}