package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * âœ… FIXED: Auto-enable automation when recipe is selected for multiblock
 */
public class UnifiedAutomationManager {
    
    private static ModConfig config;
    private static SlimefunMachineData currentMachine = null;
    private static boolean automationEnabled = false;
    private static long lastTickTime = 0;
    private static MultiblockCacheManager.CachedMultiblock currentCachedMachine = null;
    private static BlockPos currentDispenserPos = null;
    
    /**
     * Initialize automation manager
     */
    public static void init(ModConfig cfg) {
        config = cfg;
        MachineAutomationHandler.init(cfg);
        MultiblockAutomationHandler.init(cfg);
        MultiblockCacheManager.load();
        
        BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Initialized");
    }
    
    /**
     * Called when user constructs a multiblock (legacy method)
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
            currentDispenserPos = playerPos;
            
            // Show confirmation
            player.displayClientMessage(
                Component.literal("Â§aâœ“ " + machine.getName() + " cached! Press R for recipes."),
                false
            );
            
            // Auto-load remembered recipe if exists
            if (currentCachedMachine != null && currentCachedMachine.getLastSelectedRecipe() != null) {
                String rememberedRecipe = currentCachedMachine.getLastSelectedRecipe();
                
                try {
                    RecipeOverlayRenderer.show(machine);
                    player.displayClientMessage(
                        Component.literal("Â§eâš¡ Last recipe: " + getRecipeDisplayName(rememberedRecipe)),
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
            
            // Handle Dispenser (potential multiblock) - AUTO DETECT & CACHE
            if ("Dispenser".equalsIgnoreCase(title) || title.contains("Dispenser")) {
                handleDispenserOpenWithAutoDetect();
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
     * ðŸ†• AUTO-DETECT & CACHE: Handle dispenser opening with automatic detection
     */
    private static void handleDispenserOpenWithAutoDetect() {
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            Level level = mc.level;
            
            if (player == null || level == null) return;
            
            // ðŸ” GET DISPENSER POSITION using HitResult (what player is looking at)
            BlockPos dispenserPos = getDispenserPosition(mc, level);
            
            if (dispenserPos == null) {
                BapelSlimefunMod.LOGGER.warn("[AutoDetect] Could not determine dispenser position");
                return;
            }
            
            // Save position for M key access
            currentDispenserPos = dispenserPos;
            
            // ðŸ“¦ CHECK IF ALREADY CACHED
            currentCachedMachine = MultiblockCacheManager.getMachineAt(dispenserPos);
            
            if (currentCachedMachine != null) {
                // Already cached - load it
                loadCachedMultiblock(player);
                return;
            }
            
            // ðŸ”Ž NOT CACHED - AUTO DETECT MULTIBLOCK
            BapelSlimefunMod.LOGGER.info("[AutoDetect] Running multiblock detection at {}", dispenserPos);
            
            MultiblockDetector.DetectionResult result = MultiblockDetector.detect(level, dispenserPos);
            
            if (result != null) {
                // âœ… MULTIBLOCK DETECTED - AUTO CACHE IT
                String machineId = result.getMachineId();
                SlimefunMachineData machine = SlimefunDataLoader.getMultiblockById(machineId);
                
                if (machine != null) {
                    // Cache the detected multiblock
                    MultiblockCacheManager.addMachine(machine, dispenserPos);
                    currentCachedMachine = MultiblockCacheManager.getMachineAt(dispenserPos);
                    currentMachine = machine;
                    
                    // Notify user
                    player.displayClientMessage(
                        Component.literal(String.format(
                            "Â§aâœ“ Detected & Cached: Â§f%s Â§7(%.0f%% match)",
                            machine.getName(),
                            result.getConfidence() * 100
                        )),
                        false
                    );
                    
                    player.displayClientMessage(
                        Component.literal("Â§7Press R to view recipes"),
                        true
                    );
                    
                    // Auto-show overlay if configured
                    if (config != null && config.isAutoShowOverlay()) {
                        RecipeOverlayRenderer.show(machine);
                    }
                    
                    BapelSlimefunMod.LOGGER.info("[AutoDetect] âœ“ Successfully detected and cached: {}", machineId);
                } else {
                    BapelSlimefunMod.LOGGER.error("[AutoDetect] Machine data not found for: {}", machineId);
                }
            } else {
                // No multiblock detected
                BapelSlimefunMod.LOGGER.debug("[AutoDetect] No multiblock structure detected at {}", dispenserPos);
                currentCachedMachine = null;
            }
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error in handleDispenserOpenWithAutoDetect", e);
        }
    }
    
    /**
     * ðŸ†• GET DISPENSER POSITION from player's crosshair or nearby search
     */
    private static BlockPos getDispenserPosition(Minecraft mc, Level level) {
        // Method 1: Try to get from player's crosshair (most accurate)
        HitResult hitResult = mc.hitResult;
        
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            BlockPos pos = blockHit.getBlockPos();
            
            // Verify it's actually a dispenser
            if (level.getBlockState(pos).getBlock() == Blocks.DISPENSER) {
                BapelSlimefunMod.LOGGER.info("[AutoDetect] Found dispenser via hitResult: {}", pos);
                return pos;
            }
        }
        
        // Method 2: Fallback - search nearby (within 5 blocks)
        LocalPlayer player = mc.player;
        if (player != null) {
            BlockPos playerPos = player.blockPosition();
            
            for (int radius = 1; radius <= 5; radius++) {
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            BlockPos checkPos = playerPos.offset(x, y, z);
                            
                            if (level.getBlockState(checkPos).getBlock() == Blocks.DISPENSER) {
                                BapelSlimefunMod.LOGGER.info("[AutoDetect] Found dispenser via search: {}", checkPos);
                                return checkPos;
                            }
                        }
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Load cached multiblock
     */
    private static void loadCachedMultiblock(LocalPlayer player) {
        if (currentCachedMachine == null) {
            currentDispenserPos = null;
            return;
        }
        
        currentDispenserPos = currentCachedMachine.getPosition();
        
        String machineId = currentCachedMachine.getMachineId();
        currentMachine = SlimefunDataLoader.getMultiblockById(machineId);
        
        if (currentMachine == null) {
            BapelSlimefunMod.LOGGER.error("[UnifiedAuto] Failed to load machine: {}", machineId);
            return;
        }
        
        BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Loaded cached multiblock: {}", currentMachine.getId());
        
        // Auto-load last recipe if exists
        String lastRecipe = currentCachedMachine.getLastSelectedRecipe();
        if (lastRecipe != null && config != null && config.isRememberLastRecipe()) {
            MultiblockAutomationHandler.setSelectedRecipe(lastRecipe);
            
            // Event-driven: JANGAN auto-enable automation
            // Biarkan user pilih recipe manual untuk trigger
            
            BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Auto-enabled automation for: {}", lastRecipe);
            BapelSlimefunMod.LOGGER.info("[UnifiedAuto] currentMachine = {}", currentMachine.getId());
            return;
        }
        
        // Show overlay if no auto-load
        if (config != null && config.isAutoShowOverlay()) {
            RecipeOverlayRenderer.show(currentMachine);
        }
    }
    
    /**
     * Called when machine GUI is closed
     */
/**
     * Called when machine GUI is closed
     */
    public static void onMachineClose() {
        try {
            // 1. Handle Electric Machines
            if (currentMachine != null && currentMachine.isElectric()) {
                MachineAutomationHandler.onContainerClose();
            }
            
            // 2. ðŸ†• CHAIN TO AUTO-CLICKER (Mata Rantai Otomatisasi)
            // Jika kita punya data mesin multiblock, posisi dispenser, dan resep yang dipilih...
            if (currentCachedMachine != null && currentDispenserPos != null) {
                String selectedRecipe = MultiblockAutomationHandler.getSelectedRecipe();
                
                // Hanya jalankan auto-click jika automation dinyalakan secara global
                if (selectedRecipe != null && automationEnabled) {
                    BapelSlimefunMod.LOGGER.info("[UnifiedAuto] GUI Closed -> Starting Auto-Clicker chain");
                    int calculatedClicks = MultiblockAutomationHandler.getCalculatedClickCount();
                    if (calculatedClicks > 0) {
                        MultiblockAutoClicker.enable(currentDispenserPos, currentCachedMachine.getMachineId(), calculatedClicks);
                    }
                }
            }
            
            // 3. Clear currentMachine (Karena GUI sudah tertutup)
            currentMachine = null;
            
            BapelSlimefunMod.LOGGER.debug("[UnifiedAuto] Machine GUI closed");
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error in onMachineClose", e);
        }
    }
    
    /**
     * Main tick handler
     */
    public static void tick() {
        // 1. ðŸ†• ALWAYS TICK AUTO-CLICKER
        // Auto-clicker berjalan saat GUI tertutup, jadi harus dipanggil di luar logic GUI
        try {
            MultiblockAutoClicker.tick();
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error ticking AutoClicker", e);
        }

        // 2. Cek Global Automation
        if (!automationEnabled) {
            return;
        }
        
        try {
            // 3. GUI Automation (Hanya berjalan saat membuka Container/Dispenser)
            SlimefunMachineData machine = getCurrentMachine();
            
            if (machine == null) {
                // GUI tertutup -> logic pengisian item tidak jalan, tapi auto-clicker di atas tetap jalan
                return;
            }
            
            if (machine.isElectric()) {
                MachineAutomationHandler.tick();
            } else if (machine.isMultiblock()) {
                MultiblockAutomationHandler.tick(machine);
            }
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error in tick", e);
        }
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
                        Component.literal("Â§a[Slimefun] Automation STARTED â–¶"), 
                        false
                    );
                } else {
                    player.displayClientMessage(
                        Component.literal("Â§c[Slimefun] Automation STOPPED â– "), 
                        false
                    );
                }
            }
            
            if (config != null) {
                config.setAutomationEnabled(automationEnabled);
            }
            MachineAutomationHandler.setAutomationEnabled(automationEnabled);
            
            // ðŸ†• MATIKAN AUTO-CLICKER JUGA SAAT TOGGLE OFF
            if (!automationEnabled) {
                MultiblockAutoClicker.disable();
            }
            
            BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Automation toggled: {}", automationEnabled);
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error toggling automation", e);
        }
    }
    
    /**
     * Set selected recipe
     */
    public static void setSelectedRecipe(String recipeId) {
        try {
            SlimefunMachineData machine = getCurrentMachine();
            if (machine == null) return;
            
            if (machine.isElectric()) {
                MachineAutomationHandler.setSelectedRecipe(recipeId);
            } else if (machine.isMultiblock()) {
                MultiblockAutomationHandler.setSelectedRecipe(recipeId);
                
                // âœ… KEY FIX: AUTO-ENABLE AUTOMATION FOR MULTIBLOCK!
                if (recipeId != null) {
                    automationEnabled = true;
                    if (config != null) {
                        config.setAutomationEnabled(true);
                    }
                    BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Auto-enabled automation for multiblock recipe: {}", 
                                                 recipeId);
                }
                
                // Save to cache
                if (currentCachedMachine != null) {
                    currentCachedMachine.setLastSelectedRecipe(recipeId);
                    MultiblockCacheManager.save();
                }
            }
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    Component.literal("Â§aâœ“ Recipe selected: Â§f" + getRecipeDisplayName(recipeId)), 
                    true
                );
                
                // âœ… NEW: Show automation status
                if (machine.isMultiblock()) {
                    mc.player.displayClientMessage(
                        Component.literal("Â§aâ–¶ Automation STARTED - Items will auto-fill!"), 
                        false
                    );
                }
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
     * Get current dispenser position (for Machine Detector)
     */
    public static BlockPos getCurrentDispenserPos() {
        return currentDispenserPos;
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
     * Get recipe display name
     */
    private static String getRecipeDisplayName(String recipeId) {
        if (recipeId == null) return "Unknown";
        
        try {
            RecipeData recipe = RecipeDatabase.getRecipe(recipeId);
            if (recipe != null) {
                RecipeData.RecipeOutput primaryOutput = recipe.getPrimaryOutput();
                if (primaryOutput != null) {
                    return primaryOutput.getDisplayName();
                }
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