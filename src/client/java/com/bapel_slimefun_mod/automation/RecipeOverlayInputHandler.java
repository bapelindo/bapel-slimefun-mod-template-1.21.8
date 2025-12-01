package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Handles keyboard input for the recipe overlay
 * FIXED: Consistent toggle behavior and proper event consumption
 */
public class RecipeOverlayInputHandler {
    
    private static long lastInputTime = 0;
    private static final long INPUT_COOLDOWN = 150; // milliseconds
    private static long lastToggleTime = 0;
    private static final long TOGGLE_COOLDOWN = 200; // milliseconds - prevent double toggle
    
    /**
     * Handle key press event
     * Returns true if the event was handled and should be consumed
     */
    public static boolean handleKeyPress(int key, int scancode, int action, int modifiers) {
        // Only handle key press, not release
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) {
            return false;
        }
        
        // Handle R key for toggle - ALWAYS handle this in container screens
        if (key == GLFW.GLFW_KEY_R) {
            return handleToggleKey();
        }
        
        // For other keys, only handle if overlay is visible
        if (!RecipeOverlayRenderer.isVisible()) {
            return false;
        }
        
        // Apply input cooldown to prevent rapid firing
        long now = System.currentTimeMillis();
        if (now - lastInputTime < INPUT_COOLDOWN) {
            return true; // Consume event but don't process
        }
        
        boolean handled = false;
        
        switch (key) {
            case GLFW.GLFW_KEY_UP:
            case GLFW.GLFW_KEY_W:
                RecipeOverlayRenderer.moveUp();
                handled = true;
                break;
                
            case GLFW.GLFW_KEY_DOWN:
            case GLFW.GLFW_KEY_S:
                RecipeOverlayRenderer.moveDown();
                handled = true;
                break;
                
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
            case GLFW.GLFW_KEY_SPACE:
                RecipeOverlayRenderer.selectCurrent();
                handled = true;
                break;
                
            case GLFW.GLFW_KEY_ESCAPE:
                RecipeOverlayRenderer.hide();
                handled = true;
                break;
                
            case GLFW.GLFW_KEY_PAGE_UP:
                // Move up by multiple entries
                for (int i = 0; i < 5; i++) {
                    RecipeOverlayRenderer.moveUp();
                }
                handled = true;
                break;
                
            case GLFW.GLFW_KEY_PAGE_DOWN:
                // Move down by multiple entries
                for (int i = 0; i < 5; i++) {
                    RecipeOverlayRenderer.moveDown();
                }
                handled = true;
                break;
                
            case GLFW.GLFW_KEY_HOME:
                // Jump to first recipe
                int currentIndex = RecipeOverlayRenderer.getSelectedIndex();
                for (int i = 0; i < currentIndex; i++) {
                    RecipeOverlayRenderer.moveUp();
                }
                handled = true;
                break;
                
            case GLFW.GLFW_KEY_END:
                // Jump to last recipe
                int recipesCount = RecipeOverlayRenderer.getAvailableRecipes().size();
                currentIndex = RecipeOverlayRenderer.getSelectedIndex();
                for (int i = currentIndex; i < recipesCount - 1; i++) {
                    RecipeOverlayRenderer.moveDown();
                }
                handled = true;
                break;
        }
        
        if (handled) {
            lastInputTime = now;
        }
        
        return handled;
    }
    
    /**
     * Handle the R key toggle with debounce
     */
    private static boolean handleToggleKey() {
        long now = System.currentTimeMillis();
        
        // Prevent double toggle from multiple handlers
        if (now - lastToggleTime < TOGGLE_COOLDOWN) {
            BapelSlimefunMod.LOGGER.debug("Toggle cooldown active, ignoring R press");
            return true; // Consume but don't process
        }
        
        lastToggleTime = now;
        
        // Use toggle for consistent behavior
        RecipeOverlayRenderer.toggle();
        
        BapelSlimefunMod.LOGGER.debug("Recipe overlay toggled via R key, visible: {}", 
            RecipeOverlayRenderer.isVisible());
        
        return true; // Always consume R key in container screens
    }
    
    /**
     * Check if toggle is on cooldown (for external handlers to check)
     */
    public static boolean isToggleOnCooldown() {
        return System.currentTimeMillis() - lastToggleTime < TOGGLE_COOLDOWN;
    }
    
    /**
     * Handle mouse scroll for recipe navigation
     * Returns true if the event was handled
     */
    public static boolean handleMouseScroll(double scrollDelta) {
        if (!RecipeOverlayRenderer.isVisible()) {
            return false;
        }
        
        // Scroll up
        if (scrollDelta > 0) {
            RecipeOverlayRenderer.moveUp();
            return true;
        }
        // Scroll down
        else if (scrollDelta < 0) {
            RecipeOverlayRenderer.moveDown();
            return true;
        }
        
        return false;
    }
    
    /**
     * Handle mouse click for recipe selection
     * Returns true if the event was handled
     */
    public static boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (!RecipeOverlayRenderer.isVisible()) {
            return false;
        }
        
        // Left click to select
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // Check if click is within overlay bounds
            if (isClickInOverlay(mouseX, mouseY)) {
                int clickedIndex = calculateClickedRecipeIndex(mouseX, mouseY);
                
                if (clickedIndex >= 0 && clickedIndex < RecipeOverlayRenderer.getAvailableRecipes().size()) {
                    // Move selection to clicked recipe
                    int currentIndex = RecipeOverlayRenderer.getSelectedIndex();
                    int diff = clickedIndex - currentIndex;
                    
                    if (diff > 0) {
                        for (int i = 0; i < diff; i++) {
                            RecipeOverlayRenderer.moveDown();
                        }
                    } else if (diff < 0) {
                        for (int i = 0; i < -diff; i++) {
                            RecipeOverlayRenderer.moveUp();
                        }
                    }
                    
                    // Select the recipe
                    RecipeOverlayRenderer.selectCurrent();
                    return true;
                }
            }
        }
        // Right click to close
        else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            RecipeOverlayRenderer.hide();
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if mouse click is within overlay bounds
     */
    private static boolean isClickInOverlay(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        // These values should match the overlay position/size from config
        // For simplicity, using hardcoded values here
        int overlayX = 10;
        int overlayY = 60;
        int overlayWidth = 250;
        int overlayHeight = 400;
        
        return mouseX >= overlayX && mouseX <= overlayX + overlayWidth &&
               mouseY >= overlayY && mouseY <= overlayY + overlayHeight;
    }
    
    /**
     * Calculate which recipe was clicked based on mouse position
     */
    private static int calculateClickedRecipeIndex(double mouseX, double mouseY) {
        // These should match overlay rendering calculations
        int entryHeight = 40;
        int spacing = 4;
        int titleHeight = 20;
        int overlayY = 60;
        
        // Calculate relative Y position
        int relativeY = (int) (mouseY - overlayY - titleHeight - spacing);
        
        if (relativeY < 0) {
            return -1; // Clicked on title
        }
        
        // Calculate which entry was clicked
        int entryIndex = relativeY / (entryHeight + spacing);
        
        return entryIndex;
    }
    
    /**
     * Reset input handler state
     */
    public static void reset() {
        lastInputTime = 0;
        lastToggleTime = 0;
    }
}