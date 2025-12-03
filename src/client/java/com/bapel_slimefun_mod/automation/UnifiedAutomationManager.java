package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * ✅ COMPLETE FIXED VERSION: Clean, efficient multi-machine cache system
 * 
 * FIXES APPLIED:
 * 1. ✅ Removed ALL excessive debug logging
 * 2. ✅ Fixed encoding issues in messages
 * 3. ✅ Improved error handling with try-catch
 * 4. ✅ Cleaned up redundant code
 * 5. ✅ Better null checks throughout
 * 6. ✅ Simplified logic flow
 * 7. ✅ Removed unnecessary comments
 * 8. ✅ Optimized method calls
 */
public class UnifiedAutomationManager {
    
    private static ModConfig config;
    private static SlimefunMachineData currentMachine = null;
    private static boolean automationEnabled = false;
    private static long lastTickTime = 0;
    private static MultiblockCacheManager.CachedMultiblock currentCachedMachine = null;
    
    /**
     * Initialize automation manager
     */
    public static void init(ModConfig cfg) {
        config = cfg;
        MachineAutomationHandler.init(cfg);
        MultiblockAutomationHandler.init(cfg);
        MultiblockCacheManager.load();
    }
    
    /**
     * Called when user constructs a multiblock
     */
    public static void onMultiblockConstructed(SlimefunMachineData machine) {
        if (machine == null) return;
        
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return;
            
            BlockPos playerPos = player.blockPosition();
            
            // Cache machine at position
            MultiblockCacheManager.addMachine(machine, playerPos);
            currentMachine = machine;
            currentCachedMachine = MultiblockCacheManager.getMachineAt(playerPos);
            
            // Show confirmation
            player.displayClientMessage(
                Component.literal("§a✓ " + machine.getName() + " cached! Press R for recipes."),
                false
            );
            
            // Auto-load remembered recipe if exists
            if (currentCachedMachine != null && currentCachedMachine.getLastSelectedRecipe() != null) {
                String rememberedRecipe = currentCachedMachine.getLastSelectedRecipe();
                
                try {
                    RecipeOverlayRenderer.show(machine);
                    player.displayClientMessage(
                        Component.literal("§e⚡ Last recipe: " + getRecipeDisplayName(rememberedRecipe)),
                        true
                    );
                } catch (Exception e) {
                    BapelSlimefunMod.LOGGER.error("Failed to show overlay", e);
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error in onMultiblockConstructed", e);
        }
    }
    
    /**
     * Called when opening a container
     */
    public static void onMachineOpen(String title) {
        if (title == null) return;
        
        try {
            currentMachine = SlimefunDataLoader.getMachineByTitle(title);
            
            // Handle Dispenser (potential multiblock)
            if ("Dispenser".equalsIgnoreCase(title) || title.contains("Dispenser")) {
                handleDispenserOpen();
                return;
            }
            
            // Handle normal machines
            if (currentMachine != null) {
                if (currentMachine.isElectric()) {
                    MachineAutomationHandler.onContainerOpen(title);
                } else if (currentMachine.isMultiblock() && config != null && config.isAutoShowOverlay()) {
                    RecipeOverlayRenderer.show(currentMachine);
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error in onMachineOpen", e);
        }
    }
    
    /**
     * Handle dispenser opening (might be multiblock)
     */
    private static void handleDispenserOpen() {
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return;
            
            BlockPos playerPos = player.blockPosition();
            currentCachedMachine = MultiblockCacheManager.findNearestMachine(playerPos);
            
            if (currentCachedMachine == null) return;
            
            String machineId = currentCachedMachine.getMachineId();
            currentMachine = SlimefunDataLoader.getMultiblockById(machineId);
            
            if (currentMachine == null) return;
            
            // Auto-load last recipe if exists
            String lastRecipe = currentCachedMachine.getLastSelectedRecipe();
            if (lastRecipe != null && config != null && config.isRememberLastRecipe()) {
                MultiblockAutomationHandler.setSelectedRecipe(lastRecipe);
                automationEnabled = true;
                config.setAutomationEnabled(true);
                
                player.displayClientMessage(
                    Component.literal("§a✓ Auto-loaded: " + getRecipeDisplayName(lastRecipe)),
                    false
                );
                player.displayClientMessage(
                    Component.literal("§a▶ Automation STARTED!"),
                    true
                );
                return;
            }
            
            // Show overlay if no auto-load
            if (config != null && config.isAutoShowOverlay()) {
                RecipeOverlayRenderer.show(currentMachine);
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error handling dispenser", e);
        }
    }
    
    /**
     * Called when machine GUI is closed
     */
    public static void onMachineClose() {
        try {
            if (currentMachine != null && currentMachine.isElectric()) {
                MachineAutomationHandler.onContainerClose();
            }
            currentMachine = null;
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error in onMachineClose", e);
        }
    }
    
    /**
     * Main tick handler
     */
    public static void tick() {
        if (!automationEnabled) return;
        
        try {
            // Throttle ticks
            long now = System.currentTimeMillis();
            if (config != null && now - lastTickTime < 50) return;
            lastTickTime = now;
            
            SlimefunMachineData machine = getCurrentMachine();
            
            if (machine != null) {
                if (machine.isElectric()) {
                    MachineAutomationHandler.tick();
                } else if (machine.isMultiblock()) {
                    MultiblockAutomationHandler.tick(machine);
                }
            } else if (currentCachedMachine != null) {
                // Multiblock automation without GUI
                SlimefunMachineData cachedMachine = SlimefunDataLoader.getMultiblockById(
                    currentCachedMachine.getMachineId()
                );
                if (cachedMachine != null && cachedMachine.isMultiblock()) {
                    MultiblockAutomationHandler.tick(cachedMachine);
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error in automation tick", e);
        }
    }
    
    /**
     * Set selected recipe + save to cache + auto-start
     */
    public static void setSelectedRecipe(String recipeId) {
        if (recipeId == null) {
            BapelSlimefunMod.LOGGER.warn("Cannot set null recipe");
            return;
        }
        
        try {
            SlimefunMachineData machine = getCurrentMachine();
            if (machine == null) {
                BapelSlimefunMod.LOGGER.warn("Cannot set recipe - no machine active");
                return;
            }
            
            if (machine.isElectric()) {
                MachineAutomationHandler.setSelectedRecipe(recipeId);
            } else if (machine.isMultiblock()) {
                MultiblockAutomationHandler.setSelectedRecipe(recipeId);
                
                // Save to cache
                if (currentCachedMachine != null && config != null && config.isRememberLastRecipe()) {
                    MultiblockCacheManager.updateRecipe(
                        currentCachedMachine.getPosition(), 
                        recipeId
                    );
                }
            }
            
            // Auto-start automation
            automationEnabled = true;
            if (config != null) {
                config.setAutomationEnabled(true);
            }
            
            // Show confirmation
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String displayName = getRecipeDisplayName(recipeId);
                mc.player.displayClientMessage(
                    Component.literal("§a✓ Recipe: " + displayName), 
                    false
                );
                mc.player.displayClientMessage(
                    Component.literal("§a▶ Automation STARTED!"), 
                    true
                );
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error setting recipe", e);
        }
    }
    
    /**
     * Get selected recipe
     */
    public static String getSelectedRecipe() {
        try {
            SlimefunMachineData machine = getCurrentMachine();
            if (machine == null) return null;
            
            if (machine.isElectric()) {
                return MachineAutomationHandler.getSelectedRecipe();
            } else if (machine.isMultiblock()) {
                return MultiblockAutomationHandler.getSelectedRecipe();
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error getting recipe", e);
        }
        return null;
    }
    
    /**
     * Get current machine
     */
    public static SlimefunMachineData getCurrentMachine() {
        return currentMachine;
    }
    
    /**
     * Toggle automation on/off
     */
    public static void toggleAutomation() {
        try {
            automationEnabled = !automationEnabled;
            
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            
            if (player != null) {
                if (automationEnabled) {
                    player.displayClientMessage(
                        Component.literal("§a[Slimefun] Automation STARTED ▶"), 
                        false
                    );
                } else {
                    player.displayClientMessage(
                        Component.literal("§c[Slimefun] Automation STOPPED ■"), 
                        false
                    );
                }
            }
            
            if (config != null) {
                config.setAutomationEnabled(automationEnabled);
            }
            MachineAutomationHandler.setAutomationEnabled(automationEnabled);
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error toggling automation", e);
        }
    }
    
    /**
     * Check if automation is enabled
     */
    public static boolean isAutomationEnabled() {
        return automationEnabled;
    }
    
    /**
     * Check if any machine is active
     */
    public static boolean isActive() {
        return getCurrentMachine() != null || currentCachedMachine != null;
    }
    
    /**
     * Get recipe summary
     */
    public static RecipeHandler.RecipeSummary getRecipeSummary() {
        try {
            SlimefunMachineData machine = getCurrentMachine();
            if (machine == null) return null;
            
            if (machine.isElectric()) {
                return MachineAutomationHandler.getRecipeSummary();
            } else if (machine.isMultiblock()) {
                return MultiblockAutomationHandler.getRecipeSummary(machine);
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error getting recipe summary", e);
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
            // Fallback to formatted ID
        }
        
        // Format ID to display name
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
}