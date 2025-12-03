package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * âœ… COMPLETE REWRITE: Multi-machine cache system with proximity detection
 * 
 * NEW FEATURES:
 * 1. Unlimited multiblock caching (not just one!)
 * 2. Proximity-based detection (which machine am I near?)
 * 3. Auto-load last recipe per machine
 * 4. Persistent across sessions
 */
public class UnifiedAutomationManager {
    
    private static ModConfig config;
    private static SlimefunMachineData currentMachine = null;
    private static boolean automationEnabled = false;
    private static long lastTickTime = 0;
    
    // âœ… NEW: Track current cached machine reference
    private static MultiblockCacheManager.CachedMultiblock currentCachedMachine = null;
    
    public static void init(ModConfig cfg) {
        config = cfg;
        MachineAutomationHandler.init(cfg);
        MultiblockAutomationHandler.init(cfg);
        
        // âœ… NEW: Load multiblock cache
        MultiblockCacheManager.load();
        
        BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Initialized with {} cached multiblocks", 
            MultiblockCacheManager.getAllMachines().size());
    }
    
    /**
     * âœ… NEW: Called when user constructs a multiblock ("successfully constructed")
     * This caches the machine at the player's position
     */
    public static void onMultiblockConstructed(SlimefunMachineData machine) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        if (player == null) return;
        
        BlockPos playerPos = player.blockPosition();
        
        // Cache this machine at player position
        MultiblockCacheManager.addMachine(machine, playerPos);
        
        // Set as current machine
        currentMachine = machine;
        currentCachedMachine = MultiblockCacheManager.getMachineAt(playerPos);
        
        BapelSlimefunMod.LOGGER.info("[UnifiedAuto] âœ“ Multiblock constructed and cached: {} at {}", 
            machine.getName(), playerPos);
        
        // Show confirmation
        if (mc.player != null) {
            mc.player.displayClientMessage(
                Component.literal("Â§aâœ“ " + machine.getName() + " cached! Press R for recipes."),
                false
            );
        }
        
        // âœ… AUTO-LOAD: Check if this machine has a remembered recipe
        if (currentCachedMachine != null && currentCachedMachine.getLastSelectedRecipe() != null) {
            String rememberedRecipe = currentCachedMachine.getLastSelectedRecipe();
            
            BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Found remembered recipe: {}", rememberedRecipe);
            
            // Show recipe overlay with this recipe pre-selected
            // (User can still change it)
            try {
                RecipeOverlayRenderer.show(machine);
                
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                        Component.literal("Â§eâ†’ Last recipe: " + getRecipeDisplayName(rememberedRecipe)),
                        true
                    );
                }
            } catch (Exception e) {
                BapelSlimefunMod.LOGGER.error("Failed to show overlay", e);
            }
        }
    }
    
    /**
     * âœ… UPDATED: Called when opening a container (might be dispenser near multiblock)
     */
    public static void onMachineOpen(String title) {
        currentMachine = SlimefunDataLoader.getMachineByTitle(title);
        
        // âœ… NEW: If title is "Dispenser", try to detect nearby cached multiblock
        if (title.equalsIgnoreCase("Dispenser") || title.contains("Dispenser")) {
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
                        BapelSlimefunMod.LOGGER.info("[UnifiedAuto] âœ“ Detected nearby multiblock: {} at distance {}", 
                            currentMachine.getName(),
                            playerPos.distSqr(currentCachedMachine.getPosition()));
                        
                        // âœ… AUTO-LOAD: Load last recipe if exists
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
                                    Component.literal("Â§aâœ“ Auto-loaded recipe: " + getRecipeDisplayName(lastRecipe)),
                                    false
                                );
                                mc.player.displayClientMessage(
                                    Component.literal("Â§aâ–¶ Automation STARTED!"),
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
                                BapelSlimefunMod.LOGGER.error("Failed to show overlay", e);
                            }
                        }
                        
                        return;
                    }
                }
                
                BapelSlimefunMod.LOGGER.info("[UnifiedAuto] No cached multiblock found near player");
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
                        BapelSlimefunMod.LOGGER.error("Failed to show overlay", e);
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
        
        // âœ… CHANGED: Keep currentCachedMachine for proximity detection
        // Only clear currentMachine (GUI reference)
        currentMachine = null;
    }
    
    /**
     * Main tick handler
     */
    public static void tick() {
        if (!automationEnabled) return;
        
        SlimefunMachineData machine = getCurrentMachine();
        if (machine == null) return;
        
        // Throttle ticks
        long now = System.currentTimeMillis();
        if (config != null && now - lastTickTime < 50) {
            return;
        }
        lastTickTime = now;
        
        if (machine.isElectric()) {
            MachineAutomationHandler.tick();
        } else if (machine.isMultiblock()) {
            MultiblockAutomationHandler.tick(machine);
        }
    }
    
    /**
     * âœ… UPDATED: Set selected recipe + save to cache
     */
    public static void setSelectedRecipe(String recipeId) {
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
            
            // âœ… NEW: Save recipe to cache for this machine
            if (currentCachedMachine != null && config != null && config.isRememberLastRecipe()) {
                MultiblockCacheManager.updateRecipe(
                    currentCachedMachine.getPosition(), 
                    recipeId
                );
                BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Recipe saved to cache");
            }
        }
        
        // Auto-start automation
        automationEnabled = true;
        if (config != null) {
            config.setAutomationEnabled(true);
        }
        MachineAutomationHandler.setAutomationEnabled(true);
        
        // Show confirmation
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String displayName = getRecipeDisplayName(recipeId);
            mc.player.displayClientMessage(
                Component.literal("Â§aâœ“ Recipe selected: " + displayName), 
                false
            );
            mc.player.displayClientMessage(
                Component.literal("Â§aâ–¶ Automation AUTO STARTED!"), 
                true
            );
        }
        
        BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Automation AUTO STARTED");
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
     * âœ… UPDATED: Get current machine (includes cached multiblock)
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
                    Component.literal("Â§a[Slimefun] Automation STARTED â–¶"), 
                    false
                );
                BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Automation ENABLED");
            } else {
                player.displayClientMessage(
                    Component.literal("Â§c[Slimefun] Automation STOPPED â– "), 
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
        return getCurrentMachine() != null;
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
        RecipeData recipe = RecipeDatabase.getRecipe(recipeId);
        if (recipe == null) return recipeId;
        
        List<RecipeData.RecipeOutput> outputs = recipe.getOutputs();
        if (outputs.isEmpty()) return recipeId;
        
        RecipeData.RecipeOutput output = outputs.get(0);
        String itemId = output.getItemId();
        
        String[] words = itemId.toLowerCase().split("_");
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
        BapelSlimefunMod.LOGGER.info("â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—");
        BapelSlimefunMod.LOGGER.info("â•‘  UNIFIED AUTOMATION STATUS            â•‘");
        BapelSlimefunMod.LOGGER.info("â• â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•£");
        BapelSlimefunMod.LOGGER.info("â•‘  Enabled: {}", automationEnabled);
        BapelSlimefunMod.LOGGER.info("â•‘  Active: {}", isActive());
        
        SlimefunMachineData machine = getCurrentMachine();
        if (machine != null) {
            BapelSlimefunMod.LOGGER.info("â•‘  Machine: {} ({})", 
                machine.getId(),
                machine.isMultiblock() ? "MULTIBLOCK" : "ELECTRIC");
            BapelSlimefunMod.LOGGER.info("â•‘  Selected Recipe: {}", getSelectedRecipe());
            
            if (currentCachedMachine != null) {
                BapelSlimefunMod.LOGGER.info("â•‘  Cached: {} at {}", 
                    currentCachedMachine.getMachineName(),
                    currentCachedMachine.getPosition());
            }
        } else {
            BapelSlimefunMod.LOGGER.info("â•‘  Machine: none");
        }
        
        BapelSlimefunMod.LOGGER.info("â•‘");
        BapelSlimefunMod.LOGGER.info("â•‘  Total Cached: {} machines", 
            MultiblockCacheManager.getAllMachines().size());
        BapelSlimefunMod.LOGGER.info("â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
        
        // Print cache contents
        MultiblockCacheManager.printCache();
    }
}