package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.config.ModConfig;
import com.bapel_slimefun_mod.debug.PerformanceMonitor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

// Pastikan import pendukung FastMachine ini terpasang dengan benar
import com.bapel_slimefun_mod.automation.fastmachine.FastMachineAutomationHandler;
import com.bapel_slimefun_mod.automation.fastmachine.FastMachineDetector;
/**
 * ✅ PERFORMANCE OPTIMIZED VERSION
 * 
 * KEY OPTIMIZATIONS:
 * 1. Smart tick throttling - different intervals for different operations
 * 2. Cached dispenser position - avoid repeated raycasts
 * 3. Early exit patterns - skip unnecessary work
 * 4. Lazy detection - only detect when needed
 * 5. Reduced logging - only log important events
 * 6. State tracking - avoid redundant operations
 */
public class UnifiedAutomationManager {
    
    private static ModConfig config;
    private static SlimefunMachineData currentMachine = null;
    private static boolean automationEnabled = false;
    
    // ✅ OPTIMIZATION: Separate tick intervals for different operations
    private static long lastTickTime = 0;
    private static long lastAutoClickerCheck = 0;
    private static long lastMachineCheck = 0;
    private static final long MIN_TICK_INTERVAL = 50;           // 50ms = 20 TPS
    private static final long AUTO_CLICKER_CHECK_INTERVAL = 100; // 100ms = 10 TPS
    private static final long MACHINE_CHECK_INTERVAL = 100;      // 200ms = 5 TPS
    
    // Cache
    private static MultiblockCacheManager.CachedMultiblock currentCachedMachine = null;
    private static BlockPos currentDispenserPos = null;
    private static BlockPos cachedDispenserPos = null;
    private static long lastDispenserPosCache = 0;
    private static final long DISPENSER_CACHE_DURATION = 1000; // 1 second
    
    // State tracking
    private static boolean needsTick = false;
    private static String lastMachineId = null;
    private static String lastMachineTitle = null;
    private static boolean isProcessingMachineOpen = false;
    
    public static void init(ModConfig cfg) {
        config = cfg;
        MachineAutomationHandler.init(cfg);
        MultiblockAutomationHandler.init(cfg);
        MultiblockCacheManager.load();
        
        // ── FastMachine integration ────────────────────────────────────────────
        FastMachineAutomationHandler.init(cfg);
        
        BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Initialized with optimizations (FastMachine support enabled).");
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
            
            lastMachineId = machine.getId();
            lastMachineTitle = machine.getName();
            
            player.sendSystemMessage(
                Component.literal("§a✓ " + machine.getName() + " cached! Press R for recipes."));
            
            if (currentCachedMachine != null && currentCachedMachine.getLastSelectedRecipe() != null) {
                String rememberedRecipe = currentCachedMachine.getLastSelectedRecipe();
                
                try {
                    RecipeOverlayRenderer.show(machine);
                    player.sendOverlayMessage(
                        Component.literal("§e⚡ Last recipe: " + getRecipeDisplayName(rememberedRecipe)));
                } catch (Exception e) {
                    BapelSlimefunMod.LOGGER.error("Failed to show overlay", e);
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error in onMultiblockConstructed", e);
        }
    }
    
    /**
     * ✅ OPTIMIZED: Reduced redundant checks and logging
     */
    public static void onMachineOpen(String title) {
        PerformanceMonitor.start("UnifiedAuto.onMachineOpen");
        try {
            if (title == null) return;
            
            String lowerTitle = title.toLowerCase();
            if (lowerTitle.contains("network grid") || 
                lowerTitle.contains("network terminal") || 
                lowerTitle.contains("jaringan") || 
                lowerTitle.contains("grid") || 
                lowerTitle.contains("terminal")) {
                // Let ChainOrchestrator handle if it's waiting for a Grid open.
                // Also cache position for manual (non-chain) opens via hitResult.
                boolean chainConsumed = false;
                Minecraft clientInst = Minecraft.getInstance();
                if (clientInst.player != null && clientInst.player.containerMenu != null) {
                    chainConsumed = com.bapel_slimefun_mod.automation.fastmachine.ChainOrchestrator.get()
                        .onNetworkGridOpened(clientInst.player.containerMenu);
                }
                if (!chainConsumed) {
                    // Manual open — cache via hitResult fallback
                    com.bapel_slimefun_mod.automation.fastmachine.FastMachineAutomationHandler.onNetworkGridOpenManual();
                }
                needsTick = true;
                return;
            }

            // ══════════════════════════════════════════════════════════════════
            // ★ FAST PATH: GuizhanCraft/FastMachines detection
            //   Must be checked BEFORE SlimefunDataLoader.
            // ══════════════════════════════════════════════════════════════════
            if (FastMachineDetector.isFastMachine(title)) {
                FastMachineAutomationHandler.onContainerOpen(title);
                needsTick = true;
                return;
            }
            
            // ✅ Set flag to prevent redundant processing
            isProcessingMachineOpen = true;
            
            try {
                // ✅ CRITICAL: Detect machine change
                SlimefunMachineData newMachine = SlimefunDataLoader.getMachineByTitle(title);
                
                boolean isDifferentMachine = false;
                if (newMachine != null) {
                    if (lastMachineId != null && !lastMachineId.equals(newMachine.getId())) {
                        isDifferentMachine = true;
                    }
                    else if (lastMachineTitle != null && !lastMachineTitle.equals(title)) {
                        isDifferentMachine = true;
                    }
                }
                
                // ✅ OPTIMIZATION: Only log important changes
                if (isDifferentMachine) {
                    BapelSlimefunMod.LOGGER.info("[UnifiedAuto] Machine change: {} → {}", 
                        lastMachineId, newMachine != null ? newMachine.getId() : "null");
                }
                
                // ✅ Stop auto-clicker if machine changed
                if (isDifferentMachine && MultiblockAutoClicker.isEnabled()) {
                    MultiblockAutoClicker.forceStop();
                }
                
                // ✅ Update current machine FIRST
                currentMachine = newMachine;
                if (newMachine != null) {
                    lastMachineId = newMachine.getId();
                    lastMachineTitle = title;
                }
                
                // ✅ Clear recipe if machine changed
                if (isDifferentMachine) {
                    clearRecipesQuietly();
                    
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.sendOverlayMessage(
                            Component.literal("§e⚠ Different machine - recipe cleared"));
                    }
                }
                
                // ✅ Handle auto-clicker for SAME machine
                if (MultiblockAutoClicker.isEnabled() && !isDifferentMachine) {
                    if ("Dispenser".equalsIgnoreCase(title) || title.contains("Dispenser")) {
                        BlockPos dispenserPos = getCachedDispenserPosition();
                        
                        if (dispenserPos != null && dispenserPos.equals(currentDispenserPos)) {
                            // Same dispenser, keep auto-clicker running
                            return;
                        }
                    }
                }
                
                // ✅ Handle Dispenser (potential multiblock)
                if ("Dispenser".equalsIgnoreCase(title) || title.contains("Dispenser")) {
                    handleDispenserOpenWithAutoDetect();
                    needsTick = true;
                    return;
                }
// ✅ Handle normal machines
if (currentMachine != null) {
    if (currentMachine.isElectric()) {
        MachineAutomationHandler.onContainerOpen(title);
        needsTick = true; // ← Tambahkan ini
    } else if (currentMachine.isMultiblock() && config != null && config.isAutoShowOverlay()) {
        RecipeOverlayRenderer.show(currentMachine);
    }
}
            } finally {
                isProcessingMachineOpen = false;
            }
        } finally {
            PerformanceMonitor.end("UnifiedAuto.onMachineOpen");
        }
    }
    
    /**
     * ✅ NEW: Clear recipes without excessive logging
     */
    private static void clearRecipesQuietly() {
        try {
            MachineAutomationHandler.setSelectedRecipe(null);
        } catch (Exception ignored) {}
        
        try {
            MultiblockAutomationHandler.setSelectedRecipe(null);
        } catch (Exception ignored) {}
    }
    
    /**
     * ✅ OPTIMIZED: Lazy detection with caching
     */
    private static void handleDispenserOpenWithAutoDetect() {
        PerformanceMonitor.start("AutoDetect.handle");
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            Level level = mc.level;
            
            if (player == null || level == null) return;
            
            // ✅ Use cached position if available
            BlockPos dispenserPos = getCachedDispenserPosition();
            
            if (dispenserPos == null) {
                return;
            }
            
            currentDispenserPos = dispenserPos;
            
            // ✅ Check cache first (avoid detection)
            currentCachedMachine = MultiblockCacheManager.getMachineAt(dispenserPos);
            
            if (currentCachedMachine != null) {
                loadCachedMultiblock(player);
                return;
            }
            
            // ✅ Run detection only if not cached
            MultiblockDetector.DetectionResult result = MultiblockDetector.detect(level, dispenserPos);
            
            if (result != null) {
                String machineId = result.getMachineId();
                SlimefunMachineData machine = SlimefunDataLoader.getMultiblockById(machineId);
                
                if (machine != null) {
                    MultiblockCacheManager.addMachine(machine, dispenserPos);
                    currentCachedMachine = MultiblockCacheManager.getMachineAt(dispenserPos);
                    currentMachine = machine;
                    
                    lastMachineId = machine.getId();
                    lastMachineTitle = machine.getName();
                    
                    player.sendSystemMessage(
                        Component.literal(String.format(
                            "§a✓ Detected & Cached: §f%s §7(%.0f%% match)",
                            machine.getName(),
                            result.getConfidence() * 100
                        )));
                    
                    player.sendOverlayMessage(
                        Component.literal("§7Press R to view recipes"));
                    
                    if (config != null && config.isAutoShowOverlay()) {
                        RecipeOverlayRenderer.show(machine);
                    }
                }
            } else {
                player.sendOverlayMessage(
                    Component.literal("§7No multiblock detected. Use /mdetect to identify."));
            }
        } finally {
            PerformanceMonitor.end("AutoDetect.handle");
        }
    }
    
    private static void loadCachedMultiblock(LocalPlayer player) {
        if (currentCachedMachine == null) return;
        
        try {
            String machineId = currentCachedMachine.getMachineId();
            currentMachine = SlimefunDataLoader.getMultiblockById(machineId);
            
            if (currentMachine == null) {
                return;
            }
            
            lastMachineId = currentMachine.getId();
            lastMachineTitle = currentMachine.getName();
            
            player.sendSystemMessage(
                Component.literal(String.format(
                    "§a✓ Loaded: §f%s §7(cached)",
                    currentMachine.getName()
                )));
            
            String lastRecipe = currentCachedMachine.getLastSelectedRecipe();
            if (lastRecipe != null) {
                player.sendOverlayMessage(
                    Component.literal("§7Last recipe: " + getRecipeDisplayName(lastRecipe)));
            } else {
                player.sendOverlayMessage(
                    Component.literal("§7Press R to view recipes"));
            }
            
            if (config != null && config.isAutoShowOverlay()) {
                RecipeOverlayRenderer.show(currentMachine);
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error loading cached multiblock", e);
        }
    }
    
    /**
     * ✅ NEW: Cached dispenser position lookup
     */
    private static BlockPos getCachedDispenserPosition() {
        long now = System.currentTimeMillis();
        
        // Return cached if still valid
        if (cachedDispenserPos != null && now - lastDispenserPosCache < DISPENSER_CACHE_DURATION) {
            return cachedDispenserPos;
        }
        
        // Refresh cache
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return null;
        
        cachedDispenserPos = getDispenserPosition(mc, level);
        lastDispenserPosCache = now;
        
        return cachedDispenserPos;
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
            // Silent fail
        }
        
        return null;
    }
    
    /**
     * ✅ OPTIMIZED: Start auto-click with validation
     */
    public static void onContainerClose() {
        PerformanceMonitor.start("UnifiedAuto.onContainerClose");
        try {
            needsTick = false;
            
            // ✅ Clear dispenser cache
            cachedDispenserPos = null;
            lastDispenserPosCache = 0;
            
            if (FastMachineAutomationHandler.isActive()) {
                FastMachineAutomationHandler.onContainerClose();
            }

            // FATAL 1 FIX: do NOT abort chain on GUI close.
            // Closing a GUI is NORMAL during chain navigation (Grid → Machine etc.).
            // Notify the orchestrator so it can handle EXTRACTING_GRID → CLOSING_GRID transition.
            com.bapel_slimefun_mod.automation.fastmachine.ChainOrchestrator.get().onGuiClosed();
            
            // ✅ Start auto-click if dispenser is ready
            if (currentMachine != null && currentMachine.isMultiblock()) {
                String selectedRecipe = MultiblockAutomationHandler.getSelectedRecipe();
                
                if (selectedRecipe != null && automationEnabled) {
                    // ✅ Validate recipe belongs to THIS machine
                    RecipeData recipe = RecipeDatabase.getRecipe(selectedRecipe);
                    if (recipe != null) {
                        String recipeMachineId = recipe.getMachineId();
                        String currentMachineId = currentMachine.getId();
                        
                        if (!recipeMachineId.equals(currentMachineId)) {
                            MultiblockAutomationHandler.setSelectedRecipe(null);
                            
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null) {
                                mc.player.sendOverlayMessage(
                                    Component.literal("§c✗ Recipe mismatch - cleared"));
                            }
                            
                            currentDispenserPos = null;
                            currentMachine = null;
                            return;
                        }
                    }
                    
                    int clickCount = MultiblockAutomationHandler.getCalculatedClickCount();
                    
                    if (clickCount > 0 && currentDispenserPos != null) {
                        MultiblockAutoClicker.enable(currentDispenserPos, currentMachine.getId(), clickCount);
                    }
                }
            }
            
            currentDispenserPos = null;
            
            if (currentMachine != null && currentMachine.isElectric()) {
                MachineAutomationHandler.onContainerClose();
            }
            
            currentMachine = null;
        } finally {
            PerformanceMonitor.end("UnifiedAuto.onContainerClose");
        }
    }
    
    public static void onMachineClose() {
        onContainerClose();
    }
    
    /**
     * ✅ FULLY OPTIMIZED: Smart throttling with early exits
     */
    public static void tick() {
        PerformanceMonitor.start("UnifiedAuto.tick");
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                com.bapel_slimefun_mod.automation.fastmachine.ChainOrchestrator.get().tick(mc);
            }

            // FastMachine GUI tick
            if (FastMachineAutomationHandler.isActive()) {
                FastMachineAutomationHandler.tick();
            }

            // Network Grid extraction tick — driven by ChainOrchestrator (FATAL 2 FIX).
            // The old code only ran tickNetworkGrid when mc.screen title matched,
            // but during the 2-tick rightClick delay mc.screen is null.
            // ChainOrchestrator.tick() now handles EXTRACTING_GRID phase directly.
            
            // ✅ FAST PATH: Skip if nothing to do
            if (!automationEnabled && !MultiblockAutoClicker.isEnabled()) {
                return;
            }
            
            long now = System.currentTimeMillis();
            
            // ✅ FAST PATH: Throttle main tick
            if (now - lastTickTime < MIN_TICK_INTERVAL) {
                return;
            }
            
            lastTickTime = now;
            
            // ✅ PRIORITY 1: Auto-clicker (separate interval)
            if (MultiblockAutoClicker.isEnabled() && now - lastAutoClickerCheck >= AUTO_CLICKER_CHECK_INTERVAL) {
                lastAutoClickerCheck = now;
                MultiblockAutoClicker.tick();
            }
            
            // ✅ FAST PATH: Skip machine automation if not needed
            if (!needsTick || currentMachine == null || !automationEnabled) {
                return;
            }
            
            // ✅ PRIORITY 2: Machine automation (separate interval)
            if (now - lastMachineCheck < MACHINE_CHECK_INTERVAL) {
                return;
            }
            
            lastMachineCheck = now;
            
            // ✅ Handle machine automation
            try {
                if (currentMachine.isElectric()) {
                    MachineAutomationHandler.tick();
                } else if (currentMachine.isMultiblock()) {
                    MultiblockAutomationHandler.tick(currentMachine);
                }
            } catch (Exception e) {
                BapelSlimefunMod.LOGGER.error("Error in automation tick", e);
            }
        } finally {
            PerformanceMonitor.end("UnifiedAuto.tick");
        }
    }
    
    public static void toggleAutomation() {
        try {
            automationEnabled = !automationEnabled;
            
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            
            if (player != null) {
                if (automationEnabled) {
                    player.sendSystemMessage(
                        Component.literal("§a[Slimefun] Automation STARTED ▶"));
                    needsTick = true;
                } else {
                    player.sendSystemMessage(
                        Component.literal("§c[Slimefun] Automation STOPPED ■"));
                }
            }
            
            if (config != null) {
                config.setAutomationEnabled(automationEnabled);
            }
            MachineAutomationHandler.setAutomationEnabled(automationEnabled);
            
            if (!automationEnabled) {
                MultiblockAutoClicker.disable();
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error toggling automation", e);
        }
    }
    
/**
 * ✅ OPTIMIZED: Set recipe with validation
 */
public static void setSelectedRecipe(String recipeId) {
    PerformanceMonitor.start("UnifiedAuto.setSelectedRecipe");
    try {
        SlimefunMachineData machine = getCurrentMachine();
        if (machine == null) {
            return;
        }
        
        // ✅ Validate recipe belongs to this machine
        if (recipeId != null) {
            RecipeData recipe = RecipeDatabase.getRecipe(recipeId);
            if (recipe != null) {
                String recipeMachineId = recipe.getMachineId();
                String currentMachineId = machine.getId();
                
                if (!recipeMachineId.equals(currentMachineId)) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.sendOverlayMessage(
                            Component.literal("§c✗ Recipe does not belong to this machine!"));
                    }
                    return;
                }
            }
        }
        
        // ✅ Delegate to appropriate handler
        if (machine.isElectric()) {
            MachineAutomationHandler.setSelectedRecipe(recipeId);
            
            // ✅ CRITICAL FIX: Enable automation and set needsTick for electric machines
            if (recipeId != null) {
                automationEnabled = true;
                needsTick = true; // ← INI YANG PENTING!
                if (config != null) {
                    config.setAutomationEnabled(true);
                }
                MachineAutomationHandler.setAutomationEnabled(true);
            }
            
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
        
        // ✅ Show message only if recipe was set
        if (recipeId != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendOverlayMessage(
                    Component.literal("§a✓ Recipe selected: §f" + getRecipeDisplayName(recipeId)));
                
                // ✅ Different message for electric vs multiblock
                if (machine.isElectric()) {
                    mc.player.sendSystemMessage(
                        Component.literal("§a▶ Automation STARTED - Items will auto-fill!"));
                } else if (machine.isMultiblock()) {
                    mc.player.sendSystemMessage(
                        Component.literal("§a▶ Automation STARTED - Fill dispenser!"));
                }
            }
        }
    } finally {
        PerformanceMonitor.end("UnifiedAuto.setSelectedRecipe");
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
            // Silent fail
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
        return getCurrentMachine() != null || currentCachedMachine != null
            || FastMachineAutomationHandler.isActive();
    }

    public static void onFastMachineKeybind(FastMachineKeybindAction action) {
        if (!FastMachineAutomationHandler.isActive()) return;
        switch (action) {
            case LOCK_RECIPE   -> FastMachineAutomationHandler.lockCurrentRecipe();
            case CYCLE_CRAFT   -> FastMachineAutomationHandler.cycleCraftMode();
            case TOGGLE_REFILL -> FastMachineAutomationHandler.toggleAutoRefill();
        }
    }

    public enum FastMachineKeybindAction {
        LOCK_RECIPE,
        CYCLE_CRAFT,
        TOGGLE_REFILL
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
            // Silent fail
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