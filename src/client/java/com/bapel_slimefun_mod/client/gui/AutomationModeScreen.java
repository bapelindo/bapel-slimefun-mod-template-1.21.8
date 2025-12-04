package com.bapel_slimefun_mod.client.gui;

import com.bapel_slimefun_mod.automation.MultiblockCacheManager;
import com.bapel_slimefun_mod.automation.RecipeMemoryManager;
import com.bapel_slimefun_mod.automation.UnifiedAutomationManager;
import com.bapel_slimefun_mod.automation.SlimefunMachineData;
import com.bapel_slimefun_mod.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * ✅ FIXED: GUI Screen with Multiblock Automation Management + Machine Detector
 * 
 * Features:
 * - Auto/Manual Mode toggle
 * - Multiblock cache management
 * - Recipe memory management
 * - Machine Detector (Always accessible - checks position when clicked)
 */
public class AutomationModeScreen extends Screen {
    private final Screen parent;
    private final ModConfig config;
    
    private Button autoModeButton;
    private Button manualModeButton;
    private Button multiblockCacheButton;
    private Button machineDetectorButton;
    private Button clearMemoryButton;
    private Button doneButton;
    
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 25;
    
    public AutomationModeScreen(Screen parent, ModConfig config) {
        super(Component.literal("Automation Mode Settings"));
        this.parent = parent;
        this.config = config;
    }
    
    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 90;
        
        // Auto Mode Button
        this.autoModeButton = Button.builder(
            Component.literal(config.isRememberLastRecipe() ? "§a✓ Auto Mode" : "Auto Mode"),
            button -> setAutoMode(true)
        )
        .bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
        
        // Manual Mode Button
        this.manualModeButton = Button.builder(
            Component.literal(!config.isRememberLastRecipe() ? "§a✓ Manual Mode" : "Manual Mode"),
            button -> setAutoMode(false)
        )
        .bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
        
        // Multiblock Cache Button
        int multiblockCount = MultiblockCacheManager.size();
        this.multiblockCacheButton = Button.builder(
            Component.literal("§b⚙ Multiblock Cache (" + multiblockCount + " machines)"),
            button -> openMultiblockManager()
        )
        .bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 3, BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
        
        // Machine Detector Button (FIXED: Always enabled)
        this.machineDetectorButton = Button.builder(
            Component.literal("§e🔍 Machine Detector"),
            button -> openMachineDetector()
        )
        .bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 4, BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
        
        // Clear Memory Button
        int memoryCount = RecipeMemoryManager.getMemoryCount();
        this.clearMemoryButton = Button.builder(
            Component.literal("§c✖ Clear Memory (" + memoryCount + " recipes)"),
            button -> clearMemory()
        )
        .bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 5, BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
        
        // Done Button
        this.doneButton = Button.builder(
            Component.literal("Done"),
            button -> this.minecraft.setScreen(parent)
        )
        .bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 7, BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
        
        // Add buttons
        this.addRenderableWidget(autoModeButton);
        this.addRenderableWidget(manualModeButton);
        this.addRenderableWidget(multiblockCacheButton);
        this.addRenderableWidget(machineDetectorButton);
        this.addRenderableWidget(clearMemoryButton);
        this.addRenderableWidget(doneButton);
        
        updateButtonStates();
    }
    
    private void setAutoMode(boolean auto) {
        config.setRememberLastRecipe(auto);
        updateButtonStates();
        
        // Show confirmation
        if (minecraft != null && minecraft.player != null) {
            String message = auto ? 
                "§a✓ Auto Mode Enabled - Recipes will be remembered" :
                "§e✓ Manual Mode Enabled - Select recipes manually";
            minecraft.player.displayClientMessage(Component.literal(message), true);
        }
    }
    
    /**
     * Open multiblock cache manager
     */
    private void openMultiblockManager() {
        if (minecraft != null) {
            minecraft.setScreen(new MultiblockCacheScreen(this, config));
        }
    }
    
    /**
     * FIXED: Open machine detector - check position when clicked
     */
    private void openMachineDetector() {
        if (minecraft == null || minecraft.player == null) return;
        
        // Get saved dispenser position
        BlockPos dispenserPos = UnifiedAutomationManager.getCurrentDispenserPos();
        
        if (dispenserPos != null) {
            // Have position - open detector screen
            minecraft.setScreen(new MachineDetectorScreen(this, dispenserPos));
        } else {
            // No position - show helpful message
            minecraft.player.displayClientMessage(
                Component.literal("§e[Detector] Please open a multiblock dispenser first!"),
                true
            );
            minecraft.player.displayClientMessage(
                Component.literal("§7Tip: Right-click a multiblock dispenser, then press M"),
                false
            );
        }
    }
    
    private void clearMemory() {
        int count = RecipeMemoryManager.getMemoryCount();
        RecipeMemoryManager.clearAll();
        
        // Update button
        this.clearMemoryButton.setMessage(
            Component.literal("§c✖ Clear Memory (0 recipes)")
        );
        
        // Show confirmation
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(
                Component.literal("§e✓ Cleared " + count + " recipe memories"), 
                true
            );
        }
    }
    
    private void updateButtonStates() {
        boolean isAuto = config.isRememberLastRecipe();
        
        this.autoModeButton.setMessage(
            Component.literal(isAuto ? "§a✓ Auto Mode" : "Auto Mode")
        );
        
        this.manualModeButton.setMessage(
            Component.literal(!isAuto ? "§a✓ Manual Mode" : "Manual Mode")
        );
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw background
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
        
        // Title
        graphics.drawCenteredString(
            this.font, 
            this.title, 
            this.width / 2, 
            20, 
            0xFFFFFF
        );
        
        // Current mode
        int descY = this.height / 2 - 105;
        String currentMode = config.isRememberLastRecipe() ? "§aAuto Mode" : "§eManual Mode";
        graphics.drawCenteredString(
            this.font,
            Component.literal("Current: " + currentMode),
            this.width / 2,
            descY,
            0xFFFFFF
        );
        
        // Explanation
        int explainY = this.height / 2 - 20;
        
        if (config.isRememberLastRecipe()) {
            graphics.drawCenteredString(
                this.font,
                Component.literal("§7Auto Mode: Recipes remembered per machine"),
                this.width / 2,
                explainY,
                0xAAAAAA
            );
            graphics.drawCenteredString(
                this.font,
                Component.literal("§7Auto-insert items when returning to machine"),
                this.width / 2,
                explainY + 12,
                0xAAAAAA
            );
        } else {
            graphics.drawCenteredString(
                this.font,
                Component.literal("§7Manual Mode: Select recipe every time"),
                this.width / 2,
                explainY,
                0xAAAAAA
            );
            graphics.drawCenteredString(
                this.font,
                Component.literal("§7Overlay shows when opening machine"),
                this.width / 2,
                explainY + 12,
                0xAAAAAA
            );
        }
        
        // Multiblock info
        int multiblockY = this.height / 2 + 40;
        int cacheCount = MultiblockCacheManager.size();
        graphics.drawCenteredString(
            this.font,
            Component.literal("§7Cached multiblocks: §b" + cacheCount + " §7machines"),
            this.width / 2,
            multiblockY,
            0xAAAAAA
        );
        
        // Machine Detector status (UPDATED: Show helpful info)
        BlockPos dispenserPos = UnifiedAutomationManager.getCurrentDispenserPos();
        if (dispenserPos != null) {
            graphics.drawCenteredString(
                this.font,
                Component.literal("§7Last dispenser: §e[" + 
                    dispenserPos.getX() + ", " + 
                    dispenserPos.getY() + ", " + 
                    dispenserPos.getZ() + "]"),
                this.width / 2,
                multiblockY + 12,
                0xAAAAAA
            );
        } else {
            graphics.drawCenteredString(
                this.font,
                Component.literal("§7Tip: Open a dispenser first to use detector"),
                this.width / 2,
                multiblockY + 12,
                0x888888
            );
        }
        
        // Render buttons
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}

/**
 * ✅ Multiblock Cache Management Screen
 */
class MultiblockCacheScreen extends Screen {
    private final Screen parent;
    private final ModConfig config;
    
    private Button viewCacheButton;
    private Button clearCacheButton;
    private Button backButton;
    
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 25;
    
    public MultiblockCacheScreen(Screen parent, ModConfig config) {
        super(Component.literal("Multiblock Cache Manager"));
        this.parent = parent;
        this.config = config;
    }
    
    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 40;
        
        // View Cache Button
        this.viewCacheButton = Button.builder(
            Component.literal("§b📋 View Cached Machines"),
            button -> viewCache()
        )
        .bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
        
        // Clear Cache Button
        int cacheCount = MultiblockCacheManager.size();
        this.clearCacheButton = Button.builder(
            Component.literal("§c✖ Clear Cache (" + cacheCount + " machines)"),
            button -> clearCache()
        )
        .bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
        
        // Back Button
        this.backButton = Button.builder(
            Component.literal("Back"),
            button -> this.minecraft.setScreen(parent)
        )
        .bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 3, BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
        
        this.addRenderableWidget(viewCacheButton);
        this.addRenderableWidget(clearCacheButton);
        this.addRenderableWidget(backButton);
    }
    
    private void viewCache() {
        if (minecraft != null && minecraft.player != null) {
            var machines = MultiblockCacheManager.getAllMachines();
            
            if (machines.isEmpty()) {
                minecraft.player.displayClientMessage(
                    Component.literal("§eNo multiblocks cached yet"), 
                    false
                );
                return;
            }
            
            minecraft.player.displayClientMessage(
                Component.literal("§6§l=== Cached Multiblocks ==="), 
                false
            );
            
            int index = 1;
            for (var machine : machines) {
                String lastRecipe = machine.getLastSelectedRecipe() != null ? 
                    "§a✓" : "§7✗";
                
                minecraft.player.displayClientMessage(
                    Component.literal(String.format(
                        "§7%d. §f%s %s §7at [%d, %d, %d]",
                        index++,
                        machine.getMachineName(),
                        lastRecipe,
                        machine.getPosition().getX(),
                        machine.getPosition().getY(),
                        machine.getPosition().getZ()
                    )), 
                    false
                );
            }
            
            minecraft.player.displayClientMessage(
                Component.literal("§6§l======================"), 
                false
            );
        }
    }
    
    private void clearCache() {
        int count = MultiblockCacheManager.size();
        MultiblockCacheManager.clearAll();
        
        // Update button
        this.clearCacheButton.setMessage(
            Component.literal("§c✖ Clear Cache (0 machines)")
        );
        
        // Show confirmation
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(
                Component.literal("§e✓ Cleared " + count + " cached multiblocks"), 
                true
            );
        }
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw background
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
        
        // Title
        graphics.drawCenteredString(
            this.font, 
            this.title, 
            this.width / 2, 
            20, 
            0xFFFFFF
        );
        
        // Info
        int infoY = this.height / 2 - 60;
        int cacheCount = MultiblockCacheManager.size();
        
        graphics.drawCenteredString(
            this.font,
            Component.literal("§7Total Cached: §b" + cacheCount + " §7multiblocks"),
            this.width / 2,
            infoY,
            0xAAAAAA
        );
        
        graphics.drawCenteredString(
            this.font,
            Component.literal("§7Cached machines remember their last recipe"),
            this.width / 2,
            infoY + 12,
            0x888888
        );
        
        graphics.drawCenteredString(
            this.font,
            Component.literal("§7and auto-load when you return to them"),
            this.width / 2,
            infoY + 24,
            0x888888
        );
        
        // Cache statistics
        int statsY = this.height / 2 + 60;
        var stats = MultiblockCacheManager.getStatistics();
        
        if (!stats.isEmpty()) {
            graphics.drawCenteredString(
                this.font,
                Component.literal("§7Most cached: §f" + getMostCachedType(stats)),
                this.width / 2,
                statsY,
                0xAAAAAA
            );
        }
        
        // Render buttons
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    
    private String getMostCachedType(java.util.Map<String, Integer> stats) {
        return stats.entrySet().stream()
            .max(java.util.Map.Entry.comparingByValue())
            .map(e -> e.getKey() + " (" + e.getValue() + ")")
            .orElse("None");
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}