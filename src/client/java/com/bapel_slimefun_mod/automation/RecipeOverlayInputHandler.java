package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class RecipeOverlayInputHandler {
    
    private static long lastInputTime = 0;
    private static final long INPUT_COOLDOWN = 150;
    private static long lastToggleTime = 0;
    private static final long TOGGLE_COOLDOWN = 200;
    
    public static boolean handleKeyPress(int key, int scancode, int action, int modifiers) {
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) {
            return false;
        }
        
        if (key == GLFW.GLFW_KEY_R) {
            BapelSlimefunMod.LOGGER.info("║ handleKeyPress: R key detected");
            return handleToggleKey();
        }
        
        if (!RecipeOverlayRenderer.isVisible()) {
            return false;
        }
        
        long now = System.currentTimeMillis();
        if (now - lastInputTime < INPUT_COOLDOWN) {
            return true;
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
                for (int i = 0; i < 5; i++) {
                    RecipeOverlayRenderer.moveUp();
                }
                handled = true;
                break;
                
            case GLFW.GLFW_KEY_PAGE_DOWN:
                for (int i = 0; i < 5; i++) {
                    RecipeOverlayRenderer.moveDown();
                }
                handled = true;
                break;
                
            case GLFW.GLFW_KEY_HOME:
                int currentIndex = RecipeOverlayRenderer.getSelectedIndex();
                for (int i = 0; i < currentIndex; i++) {
                    RecipeOverlayRenderer.moveUp();
                }
                handled = true;
                break;
                
            case GLFW.GLFW_KEY_END:
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
    
    private static boolean handleToggleKey() {
        long now = System.currentTimeMillis();
        
        if (now - lastToggleTime < TOGGLE_COOLDOWN) {
            BapelSlimefunMod.LOGGER.debug("║ Toggle cooldown active, ignoring R press");
            return true;
        }
        
        lastToggleTime = now;
        
        BapelSlimefunMod.LOGGER.info("║ Toggling recipe overlay...");
        RecipeOverlayRenderer.toggle();
        
        BapelSlimefunMod.LOGGER.info("║ Recipe overlay visible: {}", 
            RecipeOverlayRenderer.isVisible());
        
        return true;
    }
    
    public static boolean isToggleOnCooldown() {
        return System.currentTimeMillis() - lastToggleTime < TOGGLE_COOLDOWN;
    }
    
    public static boolean handleMouseScroll(double scrollDelta) {
        if (!RecipeOverlayRenderer.isVisible()) {
            return false;
        }
        
        if (scrollDelta > 0) {
            RecipeOverlayRenderer.moveUp();
            return true;
        } else if (scrollDelta < 0) {
            RecipeOverlayRenderer.moveDown();
            return true;
        }
        
        return false;
    }
    
    public static boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (!RecipeOverlayRenderer.isVisible()) {
            return false;
        }
        
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (isClickInOverlay(mouseX, mouseY)) {
                int clickedIndex = calculateClickedRecipeIndex(mouseX, mouseY);
                
                if (clickedIndex >= 0 && clickedIndex < RecipeOverlayRenderer.getAvailableRecipes().size()) {
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
                    
                    RecipeOverlayRenderer.selectCurrent();
                    return true;
                }
            }
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            RecipeOverlayRenderer.hide();
            return true;
        }
        
        return false;
    }
    
    private static boolean isClickInOverlay(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        int overlayX = 10;
        int overlayY = 60;
        int overlayWidth = 250;
        int overlayHeight = 400;
        
        return mouseX >= overlayX && mouseX <= overlayX + overlayWidth &&
               mouseY >= overlayY && mouseY <= overlayY + overlayHeight;
    }
    
    private static int calculateClickedRecipeIndex(double mouseX, double mouseY) {
        int entryHeight = 40;
        int spacing = 4;
        int titleHeight = 20;
        int overlayY = 60;
        
        int relativeY = (int) (mouseY - overlayY - titleHeight - spacing);
        
        if (relativeY < 0) {
            return -1;
        }
        
        int entryIndex = relativeY / (entryHeight + spacing);
        
        return entryIndex;
    }
    
    public static void reset() {
        lastInputTime = 0;
        lastToggleTime = 0;
    }
}
