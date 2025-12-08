package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import com.bapel_slimefun_mod.debug.PerformanceMonitor;

public class RecipeOverlayInputHandler {
    
    private static long lastInputTime = 0;
    private static final long INPUT_COOLDOWN = 150;
    private static long lastToggleTime = 0;
    private static final long TOGGLE_COOLDOWN = 200;
    
    public static boolean handleKeyPress(int key, int scancode, int action, int modifiers) {
        PerformanceMonitor.start("InputHandler.handleKeyPress");
        try {
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) {
            return false;
        }
        
        if (key == GLFW.GLFW_KEY_R) {
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
            case GLFW.GLFW_KEY_BACKSPACE:
                if (RecipeOverlayRenderer.isSearchMode()) {
                    RecipeOverlayRenderer.handleBackspace();
                    handled = true;
                }
                break;
        }
        
        if (handled) {
            lastInputTime = now;
        }
        
        return handled;
    
        } finally {
            PerformanceMonitor.end("InputHandler.handleKeyPress");
        }}
    
    private static boolean handleToggleKey() {
        long now = System.currentTimeMillis();
        
        if (now - lastToggleTime < TOGGLE_COOLDOWN) {
            return true;
        }
        
        lastToggleTime = now;
        RecipeOverlayRenderer.toggle();
        
        return true;
    }
    
    public static boolean isToggleOnCooldown() {
        return System.currentTimeMillis() - lastToggleTime < TOGGLE_COOLDOWN;
    }
    
    public static boolean handleMouseScroll(double scrollDelta) {
        PerformanceMonitor.start("InputHandler.handleMouseScroll");
        try {
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
    
        } finally {
            PerformanceMonitor.end("InputHandler.handleMouseScroll");
        }}
    
    public static boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (!RecipeOverlayRenderer.isVisible()) {
            return false;
        }
        
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (isClickInOverlay(mouseX, mouseY)) {
                int clickedIndex = calculateClickedRecipeIndex(mouseX, mouseY);
                
                if (clickedIndex >= 0 && clickedIndex < RecipeOverlayRenderer.getAvailableRecipes().size()) {
                    // Logic navigasi: hitung selisih antara item yang diklik dengan item yang sedang dipilih
                    int currentIndex = RecipeOverlayRenderer.getSelectedIndex();
                    int diff = clickedIndex - currentIndex;
                    
                    if (diff > 0) {
                        for (int i = 0; i < diff; i++) RecipeOverlayRenderer.moveDown();
                    } else if (diff < 0) {
                        for (int i = 0; i < -diff; i++) RecipeOverlayRenderer.moveUp();
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
        // Gunakan nilai dinamis dari renderer agar hitbox akurat
        int overlayX = RecipeOverlayRenderer.getPosX();
        int overlayY = RecipeOverlayRenderer.getPosY();
        int overlayWidth = 250; // Lebar standar
        int overlayHeight = 400; // Tinggi max
        
        return mouseX >= overlayX && mouseX <= overlayX + overlayWidth &&
               mouseY >= overlayY && mouseY <= overlayY + overlayHeight;
    }
    
    private static int calculateClickedRecipeIndex(double mouseX, double mouseY) {
        int entryHeight = RecipeOverlayRenderer.getEntryHeight();
        int spacing = 4;
        
        // Kalkulasi Y awal list resep agar persis sama dengan render logic:
        // posY + padding(8) + titleHeight(9) + spacing(4) + spacing(4)
        int listStartY = RecipeOverlayRenderer.getPosY() + 8 + 9 + 4 + 4;
        
        int relativeY = (int) (mouseY - listStartY);
        
        if (relativeY < 0) {
            return -1;
        }
        
        // 1. Hitung baris keberapa yang diklik (Visual Index)
        int visualIndex = relativeY / (entryHeight + spacing);
        
        // 2. PERBAIKAN UTAMA: Tambahkan Scroll Offset!
        // Tanpa ini, klik baris ke-1 akan selalu memilih item index 0, walaupun sudah di-scroll.
        int absoluteIndex = visualIndex + RecipeOverlayRenderer.getScrollOffset();
        
        return absoluteIndex;
    }
    
    public static void reset() {
        lastInputTime = 0;
        lastToggleTime = 0;
    }
    public static boolean handleCharTyped(char chr, int modifiers) {
        // Meneruskan input ke Renderer
        return RecipeOverlayRenderer.handleCharTyped(chr, modifiers);
    }
}