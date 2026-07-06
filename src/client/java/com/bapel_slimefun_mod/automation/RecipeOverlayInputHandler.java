package com.bapel_slimefun_mod.automation;

import org.lwjgl.glfw.GLFW;
import com.bapel_slimefun_mod.debug.PerformanceMonitor;

public class RecipeOverlayInputHandler {
    
    private static long lastInputTime = 0;
    private static final long INPUT_COOLDOWN = 50;
    private static long lastToggleTime = 0;
    private static final long TOGGLE_COOLDOWN = 200;
    
    public static boolean handleKeyPress(int key, int scancode, int action, int modifiers) {
        PerformanceMonitor.start("InputHandler.handleKeyPress");
        try {
            if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) {
                return false;
            }
            
            // 1. Jika overlay tidak terlihat, hanya tombol R yang bisa membukanya
            if (!RecipeOverlayRenderer.isVisible()) {
                if (key == GLFW.GLFW_KEY_R) {
                    return handleToggleKey();
                }
                return false;
            }
            
            // 2. Tombol ESCAPE selalu prioritas untuk menutup/keluar
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (RecipeOverlayRenderer.isSearchMode()) {
                    RecipeOverlayRenderer.toggleSearchMode();
                } else {
                    RecipeOverlayRenderer.hide();
                }
                return true;
            }
            
            // 3. PRIORITAS UTAMA: Handle Input Search
            // Wajib ditaruh SEBELUM cek tombol R atau Navigasi (W, S, dll)
            // agar huruf R, S, C, dll masuk sebagai teks search.
            if (RecipeOverlayRenderer.isSearchMode()) {
                // Handle Backspace
                if (key == GLFW.GLFW_KEY_BACKSPACE) {
                    RecipeOverlayRenderer.handleBackspace();
                    return true;
                }
                
                // Handle huruf A-Z
                if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
                    boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
                    char chr = (char) (key - GLFW.GLFW_KEY_A + (shift ? 'A' : 'a'));
                    RecipeOverlayRenderer.handleCharTyped(chr, modifiers);
                    return true;
                // Handle angka 0-9
                } else if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
                    char chr = (char) (key - GLFW.GLFW_KEY_0 + '0');
                    RecipeOverlayRenderer.handleCharTyped(chr, modifiers);
                    return true;
                // Handle spasi dan minus
                } else if (key == GLFW.GLFW_KEY_SPACE) {
                    RecipeOverlayRenderer.handleCharTyped(' ', modifiers);
                    return true;
                } else if (key == GLFW.GLFW_KEY_MINUS) {
                    RecipeOverlayRenderer.handleCharTyped('-', modifiers);
                    return true;
                }
                
                // Konsumsi semua tombol lain saat mode search agar tidak memicu navigasi
                return true;
            }
            
            // 4. Handle Toggle (R) - Hanya dieksekusi jika TIDAK sedang search
            if (key == GLFW.GLFW_KEY_R) {
                return handleToggleKey();
            }
            
            // 5. Navigasi (Scroll/Select) - Hanya jika TIDAK sedang search
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
                    
                case GLFW.GLFW_KEY_PAGE_UP: {
                    // moveUp()/moveDown() wrap around at the list boundaries by
                    // design (single-step navigation), so calling them in a fixed
                    // loop here would let Page Up teleport to the bottom of the
                    // list when starting near the top. Clamp the step count so
                    // paging never crosses the boundary.
                    int steps = Math.min(5, RecipeOverlayRenderer.getSelectedIndex());
                    for (int i = 0; i < steps; i++) {
                        RecipeOverlayRenderer.moveUp();
                    }
                    handled = true;
                    break;
                }
                    
                case GLFW.GLFW_KEY_PAGE_DOWN: {
                    int lastIndex = RecipeOverlayRenderer.getFilteredRecipes().size() - 1;
                    int steps = Math.min(5, Math.max(0, lastIndex - RecipeOverlayRenderer.getSelectedIndex()));
                    for (int i = 0; i < steps; i++) {
                        RecipeOverlayRenderer.moveDown();
                    }
                    handled = true;
                    break;
                }
                    
                case GLFW.GLFW_KEY_HOME:
                    int currentIndex = RecipeOverlayRenderer.getSelectedIndex();
                    for (int i = 0; i < currentIndex; i++) {
                        RecipeOverlayRenderer.moveUp();
                    }
                    handled = true;
                    break;
                    
                case GLFW.GLFW_KEY_END:
                    int recipesCount = RecipeOverlayRenderer.getFilteredRecipes().size();
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
        } finally {
            PerformanceMonitor.end("InputHandler.handleKeyPress");
        }
    }
    
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
        }
    }
    
    public static boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (!RecipeOverlayRenderer.isVisible()) {
            return false;
        }
        
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (RecipeOverlayRenderer.isClickInSearchButton(mouseX, mouseY)) {
                RecipeOverlayRenderer.toggleSearchMode();
                return true;
            }
            
            if (RecipeOverlayRenderer.isClickInCompactButton(mouseX, mouseY)) {
                RecipeOverlayRenderer.toggleCompactMode();
                return true;
            }
            
            if (RecipeOverlayRenderer.isClickInSortButton(mouseX, mouseY)) {
                RecipeOverlayRenderer.cycleSortMode();
                return true;
            }
            
            if (isClickInRecipeList(mouseX, mouseY)) {
                int clickedIndex = calculateClickedRecipeIndex(mouseX, mouseY);
                
                if (clickedIndex >= 0 && clickedIndex < RecipeOverlayRenderer.getFilteredRecipes().size()) {
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
            if (RecipeOverlayRenderer.isSearchMode()) {
                RecipeOverlayRenderer.toggleSearchMode();
            } else {
                RecipeOverlayRenderer.hide();
            }
            return true;
        }
        
        return false;
    }
    
    private static boolean isClickInRecipeList(double mouseX, double mouseY) {
        int overlayX = RecipeOverlayRenderer.getPosX();
        int overlayWidth = 250;
        
        // Read the actual on-screen bounds computed by the renderer this frame,
        // rather than re-deriving them here. The two used to be calculated
        // independently and would silently drift apart whenever the layout
        // above the list (title/buttons) changed size.
        int listStartY = RecipeOverlayRenderer.getListStartY();
        int listEndY = RecipeOverlayRenderer.getListEndY();
        
        return mouseX >= overlayX + 4 && mouseX <= overlayX + overlayWidth - 4 &&
               mouseY >= listStartY && mouseY <= listEndY;
    }
    
    private static int calculateClickedRecipeIndex(double mouseX, double mouseY) {
        int entryHeight = RecipeOverlayRenderer.getEntryHeight();
        int spacing = 4;
        
        int listStartY = RecipeOverlayRenderer.getListStartY();
        
        int relativeY = (int) (mouseY - listStartY);
        
        if (relativeY < 0) {
            return -1;
        }
        
        int visualIndex = relativeY / (entryHeight + spacing);
        int absoluteIndex = visualIndex + RecipeOverlayRenderer.getScrollOffset();
        
        return absoluteIndex;
    }
    
    public static void reset() {
        lastInputTime = 0;
        lastToggleTime = 0;
    }
}