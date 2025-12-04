package com.bapel_slimefun_mod.client.gui;

import com.bapel_slimefun_mod.automation.MultiblockDetector;
import com.bapel_slimefun_mod.automation.MultiblockCacheManager;
import com.bapel_slimefun_mod.automation.SlimefunMachineData;
import com.bapel_slimefun_mod.automation.SlimefunDataLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * ✅ FIXED: Machine Detector Screen
 * 
 * Accessible via M key in overlay (similar to Cache Multiblock)
 * Features:
 * - Verify Multiblock: Detects multiblock structure at dispenser
 * - Clear Cache: Removes cached multiblock at current position
 */
public class MachineDetectorScreen extends Screen {
    private final Screen parent;
    private final BlockPos dispenserPos;
    
    private Button verifyButton;
    private Button clearCacheButton;
    private Button backButton;
    
    private String detectionResult = null;
    private boolean isDetecting = false;
    
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 25;
    
    public MachineDetectorScreen(Screen parent, BlockPos dispenserPos) {
        super(Component.literal("Machine Detector"));
        this.parent = parent;
        this.dispenserPos = dispenserPos;
    }
    
    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 40;
        
        // Verify Multiblock Button
        this.verifyButton = Button.builder(
            Component.literal("§a✓ Verify Multiblock"),
            button -> verifyMultiblock()
        )
        .bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
        
        // Clear Cache Button
        MultiblockCacheManager.CachedMultiblock cached = 
            MultiblockCacheManager.getMachineAt(dispenserPos);
        
        String clearLabel = cached != null ? 
            "§c✖ Clear Cache (" + cached.getMachineName() + ")" :
            "§7✖ Clear Cache (No cache)";
        
        this.clearCacheButton = Button.builder(
            Component.literal(clearLabel),
            button -> clearCache()
        )
        .bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
        
        // Disable clear button if no cache
        if (cached == null) {
            this.clearCacheButton.active = false;
        }
        
        // Back Button
        this.backButton = Button.builder(
            Component.literal("Back"),
            button -> this.minecraft.setScreen(parent)
        )
        .bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 3, BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
        
        this.addRenderableWidget(verifyButton);
        this.addRenderableWidget(clearCacheButton);
        this.addRenderableWidget(backButton);
    }
    
    private void verifyMultiblock() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }
        
        isDetecting = true;
        detectionResult = "§eDetecting...";
        
        // Disable verify button during detection
        verifyButton.active = false;
        
        // Run detection
        Level level = minecraft.level;
        
        // Check if position is actually a dispenser
        if (level.getBlockState(dispenserPos).getBlock() != Blocks.DISPENSER) {
            detectionResult = "§c✖ Not a dispenser!";
            verifyButton.active = true;
            isDetecting = false;
            return;
        }
        
        // Perform detection
        MultiblockDetector.DetectionResult result = MultiblockDetector.detect(level, dispenserPos);
        
        if (result != null) {
            // Success - get machine data using getMultiblockById() instead of getMachine()
            SlimefunMachineData machine = SlimefunDataLoader.getMultiblockById(result.getMachineId());
            
            if (machine != null) {
                // Cache the detected machine
                MultiblockCacheManager.addMachine(machine, dispenserPos);
                
                // Update detection result
                detectionResult = String.format(
                    "§a✓ Detected: §f%s\n§7Confidence: §b%.0f%%\n§aAdded to cache!",
                    machine.getName(),
                    result.getConfidence() * 100
                );
                
                // Send player message
                minecraft.player.displayClientMessage(
                    Component.literal(String.format(
                        "§a[Detector] ✓ Detected: §f%s §7(%.0f%% confidence)",
                        machine.getName(),
                        result.getConfidence() * 100
                    )),
                    false
                );
                
                // Update clear cache button
                updateClearCacheButton();
            } else {
                detectionResult = "§c✖ Machine data not found";
            }
        } else {
            detectionResult = "§c✖ No multiblock detected\n§7Make sure structure is complete";
            
            minecraft.player.displayClientMessage(
                Component.literal("§c[Detector] ✖ No multiblock detected"),
                false
            );
        }
        
        verifyButton.active = true;
        isDetecting = false;
    }
    
    private void clearCache() {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        
        MultiblockCacheManager.CachedMultiblock cached = 
            MultiblockCacheManager.getMachineAt(dispenserPos);
        
        if (cached != null) {
            String machineName = cached.getMachineName();
            MultiblockCacheManager.removeMachine(dispenserPos);
            
            detectionResult = "§e✓ Cache cleared: §f" + machineName;
            
            minecraft.player.displayClientMessage(
                Component.literal("§e[Detector] ✓ Cache cleared: §f" + machineName),
                true
            );
            
            // Update clear cache button
            updateClearCacheButton();
        }
    }
    
    private void updateClearCacheButton() {
        MultiblockCacheManager.CachedMultiblock cached = 
            MultiblockCacheManager.getMachineAt(dispenserPos);
        
        if (cached != null) {
            clearCacheButton.setMessage(
                Component.literal("§c✖ Clear Cache (" + cached.getMachineName() + ")")
            );
            clearCacheButton.active = true;
        } else {
            clearCacheButton.setMessage(
                Component.literal("§7✖ Clear Cache (No cache)")
            );
            clearCacheButton.active = false;
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
        
        // Dispenser position info
        int infoY = this.height / 2 - 60;
        graphics.drawCenteredString(
            this.font,
            Component.literal("§7Dispenser at: §f[" + 
                dispenserPos.getX() + ", " + 
                dispenserPos.getY() + ", " + 
                dispenserPos.getZ() + "]"),
            this.width / 2,
            infoY,
            0xAAAAAA
        );
        
        // Current cache status
        MultiblockCacheManager.CachedMultiblock cached = 
            MultiblockCacheManager.getMachineAt(dispenserPos);
        
        if (cached != null) {
            graphics.drawCenteredString(
                this.font,
                Component.literal("§7Currently cached: §a" + cached.getMachineName()),
                this.width / 2,
                infoY + 12,
                0xAAAAAA
            );
        } else {
            graphics.drawCenteredString(
                this.font,
                Component.literal("§7No machine cached at this position"),
                this.width / 2,
                infoY + 12,
                0x888888
            );
        }
        
        // Detection result
        if (detectionResult != null) {
            int resultY = this.height / 2 + 60;
            String[] lines = detectionResult.split("\n");
            
            for (int i = 0; i < lines.length; i++) {
                graphics.drawCenteredString(
                    this.font,
                    Component.literal(lines[i]),
                    this.width / 2,
                    resultY + (i * 12),
                    0xFFFFFF
                );
            }
        }
        
        // Instructions
        int instructY = this.height - 40;
        graphics.drawCenteredString(
            this.font,
            Component.literal("§7Click 'Verify Multiblock' to detect structure"),
            this.width / 2,
            instructY,
            0x666666
        );
        
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