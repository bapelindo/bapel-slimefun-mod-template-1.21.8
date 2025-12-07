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
import com.bapel_slimefun_mod.debug.PerformanceMonitor;

/**
 * ✅ CRITICAL FIX: Clear recipe when switching between machines
 * 
 * BUGS FIXED:
 * 1. Recipe persists when switching from Enhanced Crafting Table → Armor Forge
 * 2. Items from previous recipe auto-insert into wrong machine
 * 3. selectedRecipeId not cleared on machine change
 * 
 * ROOT CAUSE:
 * - UnifiedAutomationManager doesn't track machine changes
 * - setSelectedRecipe() called without validation
 * - No machine ID comparison on container open
 */
public class UnifiedAutomationManager {
    
    private static ModConfig config;
    private static SlimefunMachineData currentMachine = null;
    private static boolean automationEnabled = false;
    private static long lastTickTime = 0;
    private static MultiblockCacheManager.CachedMultiblock currentCachedMachine = null;
    private static BlockPos currentDispenserPos = null;
    
    private static final long MIN_TICK_INTERVAL = 50;
    private static boolean needsTick = false;
    
    // ✅ CRITICAL: Track last machine to detect changes
    private static String lastMachineId = null;
    private static String lastMachineTitle = null;
    
    public static void init(ModConfig cfg) {
        config = cfg;
        MachineAutomationHandler.init(cfg);
        MultiblockAutomationHandler.init(cfg);
        MultiblockCacheManager.load();
        
        BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Initialized");
    }
    
    public static void onMultiblockConstructed(SlimefunMachineData machine) {
        if (machine == null) return;
        
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return;
            
            BlockPos playerPos = player.blockPosition();
            
            MultiblockCacheManager.addMachine(machine, playerPos);
            currentMachine = machine;
            currentCachedMachine = MultiblockCacheManager.getMachineAt(playerPos);
            currentDispenserPos = playerPos;
            
            // ✅ Update tracking
            lastMachineId = machine.getId();
            lastMachineTitle = machine.getName();
            
            player.displayClientMessage(
                Component.literal("§a✓ " + machine.getName() + " cached! Press R for recipes."),
                false
            );
            
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
     * ✅ CRITICAL FIX: Detect and handle machine changes
     */
    public static void onMachineOpen(String title) {
        if (title == null) return;
        
        try {
            // ✅ CRITICAL: Always detect machine change, even during auto-clicker
            SlimefunMachineData newMachine = SlimefunDataLoader.getMachineByTitle(title);
            
            // Check if this is a DIFFERENT machine
            boolean isDifferentMachine = false;
            if (newMachine != null) {
                if (lastMachineId != null && !lastMachineId.equals(newMachine.getId())) {
                    isDifferentMachine = true;
                    BapelSlimefunMod.LOGGER.info("[UnifiedAuto] ⚠ MACHINE CHANGE DETECTED: {} → {}", 
                        lastMachineId, newMachine.getId());
                }
                else if (lastMachineTitle != null && !lastMachineTitle.equals(title)) {
                    isDifferentMachine = true;
                    BapelSlimefunMod.LOGGER.info("[UnifiedAuto] ⚠ MACHINE CHANGE (title): {} → {}", 
                        lastMachineTitle, title);
                }
            }
            
            // ✅ CRITICAL: Stop auto-clicker if machine changed
            if (isDifferentMachine && MultiblockAutoClicker.isEnabled()) {
                BapelSlimefunMod.LOGGER.info("[UnifiedAuto] 🔴 Stopping auto-clicker due to machine change");
                MultiblockAutoClicker.forceStop();
            }
            
            // ✅ Update current machine FIRST (before clearing recipe)
            currentMachine = newMachine;
            if (newMachine != null) {
                lastMachineId = newMachine.getId();
                lastMachineTitle = title;
            }
            
            // ✅ CRITICAL: Clear recipe if machine changed (after updating currentMachine)
            if (isDifferentMachine) {
                BapelSlimefunMod.LOGGER.info("[UnifiedAuto] 🔴 Clearing old recipe from previous machine");
                
                // Clear in BOTH handlers
                try {
                    MachineAutomationHandler.setSelectedRecipe(null);
                } catch (Exception e) {
                    BapelSlimefunMod.LOGGER.error("Error clearing MachineAutomationHandler recipe", e);
                }
                
                try {
                    MultiblockAutomationHandler.setSelectedRecipe(null);
                } catch (Exception e) {
                    BapelSlimefunMod.LOGGER.error("Error clearing MultiblockAutomationHandler recipe", e);
                }
                
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                        Component.literal("§e⚠ Different machine - recipe cleared"),
                        true
                    );
                }
            }
            
            // ✅ Now check auto-clicker for SAME machine
            if (MultiblockAutoClicker.isEnabled() && !isDifferentMachine) {
                BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Auto-clicker running, keeping it active");
                
                if ("Dispenser".equalsIgnoreCase(title) || title.contains("Dispenser")) {
                    BlockPos dispenserPos = getDispenserPosition(Minecraft.getInstance(), Minecraft.getInstance().level);
                    
                    if (dispenserPos != null && dispenserPos.equals(currentDispenserPos)) {
                        BapelSlimefunMod.LOGGER.info("[UnifiedAuto] User re-opened same dispenser");
                        return;
                    }
                }
            }

            
            // ✅ Update current machine and tracking
            currentMachine = newMachine;
            if (newMachine != null) {
                lastMachineId = newMachine.getId();
                lastMachineTitle = title;
            }
            
            // Handle Dispenser (potential multiblock)
            if ("Dispenser".equalsIgnoreCase(title) || title.contains("Dispenser")) {
                handleDispenserOpenWithAutoDetect();
                needsTick = true;
                return;
            }
            
            // Handle normal machines
            if (currentMachine != null) {
                if (currentMachine.isElectric()) {
                    MachineAutomationHandler.onContainerOpen(title);
                    needsTick = true;
                } else if (currentMachine.isMultiblock() && config != null && config.isAutoShowOverlay()) {
                    RecipeOverlayRenderer.show(currentMachine);
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error in onMachineOpen", e);
        }
    }
    
    private static void handleDispenserOpenWithAutoDetect() {
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            Level level = mc.level;
            
            if (player == null || level == null) return;
            
            BlockPos dispenserPos = getDispenserPosition(mc, level);
            
            if (dispenserPos == null) {
                BapelSlimefunMod.LOGGER.warn("[AutoDetect] Could not determine dispenser position");
                return;
            }
            
            currentDispenserPos = dispenserPos;
            
            // Check if already cached
            currentCachedMachine = MultiblockCacheManager.getMachineAt(dispenserPos);
            
            if (currentCachedMachine != null) {
                loadCachedMultiblock(player);
                return;
            }
            
            // Run detection
            BapelSlimefunMod.LOGGER.info("[AutoDetect] Running multiblock detection at {}", dispenserPos);
            
            MultiblockDetector.DetectionResult result = MultiblockDetector.detect(level, dispenserPos);
            
            if (result != null) {
                String machineId = result.getMachineId();
                SlimefunMachineData machine = SlimefunDataLoader.getMultiblockById(machineId);
                
                if (machine != null) {
                    MultiblockCacheManager.addMachine(machine, dispenserPos);
                    currentCachedMachine = MultiblockCacheManager.getMachineAt(dispenserPos);
                    currentMachine = machine;
                    
                    // ✅ Update tracking
                    lastMachineId = machine.getId();
                    lastMachineTitle = machine.getName();
                    
                    player.displayClientMessage(
                        Component.literal(String.format(
                            "§a✓ Detected & Cached: §f%s §7(%.0f%% match)",
                            machine.getName(),
                            result.getConfidence() * 100
                        )),
                        false
                    );
                    
                    player.displayClientMessage(
                        Component.literal("§7Press R to view recipes"),
                        true
                    );
                    
                    if (config != null && config.isAutoShowOverlay()) {
                        RecipeOverlayRenderer.show(machine);
                    }
                    
                    BapelSlimefunMod.LOGGER.info("[AutoDetect] ✓ Successfully detected and cached: {}", machineId);
                } else {
                    BapelSlimefunMod.LOGGER.error("[AutoDetect] Machine data not found for: {}", machineId);
                }
            } else {
                BapelSlimefunMod.LOGGER.warn("[AutoDetect] No multiblock detected");
                player.displayClientMessage(
                    Component.literal("§7No multiblock detected. Use /mdetect to identify."),
                    true
                );
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[AutoDetect] Error in auto-detect", e);
        }
    }
    
    private static void loadCachedMultiblock(LocalPlayer player) {
        if (currentCachedMachine == null) return;
        
        try {
            String machineId = currentCachedMachine.getMachineId();
            currentMachine = SlimefunDataLoader.getMultiblockById(machineId);
            
            if (currentMachine == null) {
                BapelSlimefunMod.LOGGER.error("[AutoDetect] Machine data not found for cached ID: {}", machineId);
                return;
            }
            
            // ✅ Update tracking
            lastMachineId = currentMachine.getId();
            lastMachineTitle = currentMachine.getName();
            
            player.displayClientMessage(
                Component.literal(String.format(
                    "§a✓ Loaded: §f%s §7(cached)",
                    currentMachine.getName()
                )),
                false
            );
            
            String lastRecipe = currentCachedMachine.getLastSelectedRecipe();
            if (lastRecipe != null) {
                player.displayClientMessage(
                    Component.literal("§7Last recipe: " + getRecipeDisplayName(lastRecipe)),
                    true
                );
            } else {
                player.displayClientMessage(
                    Component.literal("§7Press R to view recipes"),
                    true
                );
            }
            
            if (config != null && config.isAutoShowOverlay()) {
                RecipeOverlayRenderer.show(currentMachine);
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[AutoDetect] Error loading cached multiblock", e);
        }
    }
    
    private static BlockPos getDispenserPosition(Minecraft mc, Level level) {
        try {
            if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
                return null;
            }
            
            BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
            BlockPos pos = blockHit.getBlockPos();
            
            if (level.getBlockState(pos).getBlock() == Blocks.DISPENSER) {
                return pos;
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("[AutoDetect] Error getting dispenser position", e);
        }
        
        return null;
    }
    
    /**
     * ✅ FIXED: Only start auto-click if recipe matches current machine
     */
    public static void onContainerClose() {
        needsTick = false;
        
        // ✅ Start auto-click if dispenser is ready
        if (currentMachine != null && currentMachine.isMultiblock()) {
            String selectedRecipe = MultiblockAutomationHandler.getSelectedRecipe();
            
            if (selectedRecipe != null && automationEnabled) {
                // ✅ CRITICAL: Validate recipe belongs to THIS machine
                RecipeData recipe = RecipeDatabase.getRecipe(selectedRecipe);
                if (recipe != null) {
                    String recipeMachineId = recipe.getMachineId();
                    String currentMachineId = currentMachine.getId();
                    
                    if (!recipeMachineId.equals(currentMachineId)) {
                        BapelSlimefunMod.LOGGER.error(
                            "[UnifiedAuto] 🔴 CRITICAL: Recipe mismatch on close! recipe={}, machine={}", 
                            recipeMachineId, currentMachineId
                        );
                        
                        // Clear invalid recipe
                        MultiblockAutomationHandler.setSelectedRecipe(null);
                        
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.player.displayClientMessage(
                                Component.literal("§c✗ Recipe mismatch - cleared"),
                                true
                            );
                        }
                        
                        currentDispenserPos = null;
                        currentMachine = null;
                        return;
                    }
                }
                
                int clickCount = MultiblockAutomationHandler.getCalculatedClickCount();
                
                BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Dispenser closed - checking if ready for auto-click");
                BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Calculated clicks: {}", clickCount);
                
                if (clickCount > 0 && currentDispenserPos != null) {
                    MultiblockAutoClicker.enable(currentDispenserPos, currentMachine.getId(), clickCount);
                    BapelSlimefunMod.LOGGER.info("[UnifiedAuto] ✓ Auto-click started with {} clicks", clickCount);
                } else {
                    BapelSlimefunMod.LOGGER.warn("[UnifiedAuto] Cannot start auto-click: clicks={}, pos={}", 
                        clickCount, currentDispenserPos);
                }
            }
        }
        
        // ✅ DON'T clear lastMachineId here - we need it to detect changes
        currentDispenserPos = null;
        
        if (currentMachine != null && currentMachine.isElectric()) {
            MachineAutomationHandler.onContainerClose();
        }
        
        // ✅ Only clear currentMachine, keep lastMachineId for comparison
        currentMachine = null;
    }
    
    public static void onMachineClose() {
        onContainerClose();
    }
    
    public static void tick() {
        PerformanceMonitor.start("UnifiedAuto.tick");
        try {
        if (!automationEnabled && !MultiblockAutoClicker.isEnabled()) {
            return;
        }
        
        if (!needsTick && !MultiblockAutoClicker.isEnabled()) {
            return;
        }
        
        try {
            long now = System.currentTimeMillis();
            
            if (now - lastTickTime < MIN_TICK_INTERVAL) {
                return;
            }
            
            lastTickTime = now;
            
            // Auto-clicker has highest priority
            MultiblockAutoClicker.tick();
            
            if (currentMachine == null) {
                return;
            }
            
            if (!automationEnabled) {
                return;
            }
            
            if (currentMachine.isElectric()) {
                MachineAutomationHandler.tick();
            } else if (currentMachine.isMultiblock()) {
                MultiblockAutomationHandler.tick(currentMachine);
            }
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error in tick", e);
        }
    
        } finally {
            PerformanceMonitor.end("UnifiedAuto.tick");
        }}
    
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
                    needsTick = true;
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
            
            if (!automationEnabled) {
                MultiblockAutoClicker.disable();
            }
            
            BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Automation toggled: {}", automationEnabled);
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error toggling automation", e);
        }
    }
    
/**
 * ✅ FIXED: Don't reset automation state when re-selecting same recipe
 */
public static void setSelectedRecipe(String recipeId) {
    try {
        SlimefunMachineData machine = getCurrentMachine();
        if (machine == null) {
            BapelSlimefunMod.LOGGER.warn("[UnifiedAuto] Cannot set recipe - no machine active");
            return;
        }
        
        // ✅ Validate recipe belongs to this machine
        if (recipeId != null) {
            RecipeData recipe = RecipeDatabase.getRecipe(recipeId);
            if (recipe != null) {
                String recipeMachineId = recipe.getMachineId();
                String currentMachineId = machine.getId();
                
                if (!recipeMachineId.equals(currentMachineId)) {
                    BapelSlimefunMod.LOGGER.error(
                        "[UnifiedAuto] 🔴 RECIPE MISMATCH: recipe={}, machine={}", 
                        recipeMachineId, currentMachineId
                    );
                    
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.displayClientMessage(
                            Component.literal("§c✗ Recipe does not belong to this machine!"),
                            true
                        );
                    }
                    return;
                }
            }
        }
        
        // ✅ Delegate to appropriate handler WITHOUT forcing reset
        if (machine.isElectric()) {
            MachineAutomationHandler.setSelectedRecipe(recipeId);
        } else if (machine.isMultiblock()) {
            MultiblockAutomationHandler.setSelectedRecipe(recipeId);
            
            if (recipeId != null) {
                automationEnabled = true;
                needsTick = true;
                if (config != null) {
                    config.setAutomationEnabled(true);
                }
            }
            
            if (currentCachedMachine != null) {
                currentCachedMachine.setLastSelectedRecipe(recipeId);
                MultiblockCacheManager.save();
            }
        }
        
        // ✅ Show message ONLY if recipe was actually set (not null)
        if (recipeId != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    Component.literal("§a✓ Recipe selected: §f" + getRecipeDisplayName(recipeId)), 
                    true
                );
                
                if (machine.isMultiblock()) {
                    mc.player.displayClientMessage(
                        Component.literal("§a▶ Automation STARTED - Items will auto-fill!"), 
                        false
                    );
                }
            }
        }
    } catch (Exception e) {
        BapelSlimefunMod.LOGGER.error("Error setting recipe", e);
    }
}
    
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
    
    public static SlimefunMachineData getCurrentMachine() {
        return currentMachine;
    }
    
    public static BlockPos getCurrentDispenserPos() {
        return currentDispenserPos;
    }

    public static boolean isAutomationEnabled() {
        return automationEnabled;
    }
    
    public static boolean isActive() {
        return getCurrentMachine() != null || currentCachedMachine != null;
    }
    
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
            // Fallback
        }
        
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