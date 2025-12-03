package com.bapel_slimefun_mod.client.gui;

import com.bapel_slimefun_mod.automation.RecipeMemoryManager;
import com.bapel_slimefun_mod.config.ModConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * GUI Screen untuk mengatur Auto/Manual Mode
 * - Auto Mode: Otomatis mengingat dan memasukkan recipe ketika kembali ke machine
 * - Manual Mode: Harus memilih recipe setiap kali membuka machine
 * 
 * FIXED: Changed renderBackground to renderDirtBackground to avoid blur crash in 1.21.8
 */
public class AutomationModeScreen extends Screen {
    private final Screen parent;
    private final ModConfig config;
    
    private Button autoModeButton;
    private Button manualModeButton;
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
        int startY = this.height / 2 - 60;
        
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
        
        // Clear Memory Button
        int memoryCount = RecipeMemoryManager.getMemoryCount();
        this.clearMemoryButton = Button.builder(
            Component.literal("§c Clear Memory (" + memoryCount + " recipes)"),
            button -> clearMemory()
        )
        .bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 3, BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
        
        // Done Button
        this.doneButton = Button.builder(
            Component.literal("Done"),
            button -> this.minecraft.setScreen(parent)
        )
        .bounds(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 5, BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
        
        // Add buttons
        this.addRenderableWidget(autoModeButton);
        this.addRenderableWidget(manualModeButton);
        this.addRenderableWidget(clearMemoryButton);
        this.addRenderableWidget(doneButton);
        
        updateButtonStates();
    }
    
    private void setAutoMode(boolean auto) {
        config.setRememberLastRecipe(auto);
        updateButtonStates();
        
        // Show confirmation message
        if (minecraft != null && minecraft.player != null) {
            String message = auto ? 
                "§a✓ Auto Mode Enabled - Recipes will be remembered" :
                "§e✓ Manual Mode Enabled - You must select recipes manually";
            minecraft.player.displayClientMessage(Component.literal(message), true);
        }
    }
    
    private void clearMemory() {
        int count = RecipeMemoryManager.getMemoryCount();
        RecipeMemoryManager.clearAll();
        
        // Update button text
        this.clearMemoryButton.setMessage(Component.literal("§c Clear Memory (0 recipes)"));
        
        // Show confirmation message
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
        // FIXED: Use manual background rendering to avoid blur crash
        // In Minecraft 1.21.8, renderBackground can cause "Can only blur once per frame" error
        // Draw a simple semi-transparent dark background instead
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
        
        // Render title
        graphics.drawCenteredString(
            this.font, 
            this.title, 
            this.width / 2, 
            20, 
            0xFFFFFF
        );
        
        // Render mode description
        int descY = this.height / 2 - 85;
        String currentMode = config.isRememberLastRecipe() ? "§aAuto Mode" : "§eManual Mode";
        graphics.drawCenteredString(
            this.font,
            Component.literal("Current: " + currentMode),
            this.width / 2,
            descY,
            0xFFFFFF
        );
        
        // Render explanation
        int explainY = this.height / 2 + 15;
        
        if (config.isRememberLastRecipe()) {
            // Auto mode explanation
            graphics.drawCenteredString(
                this.font,
                Component.literal("§7Auto Mode: Recipes are remembered per machine"),
                this.width / 2,
                explainY,
                0xAAAAAA
            );
            graphics.drawCenteredString(
                this.font,
                Component.literal("§7When you return to a machine, it will auto-insert items"),
                this.width / 2,
                explainY + 12,
                0xAAAAAA
            );
        } else {
            // Manual mode explanation
            graphics.drawCenteredString(
                this.font,
                Component.literal("§7Manual Mode: You must select recipe every time"),
                this.width / 2,
                explainY,
                0xAAAAAA
            );
            graphics.drawCenteredString(
                this.font,
                Component.literal("§7The overlay will always show when opening a machine"),
                this.width / 2,
                explainY + 12,
                0xAAAAAA
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