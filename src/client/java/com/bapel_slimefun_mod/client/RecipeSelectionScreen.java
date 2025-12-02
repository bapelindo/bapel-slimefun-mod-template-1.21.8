package com.bapel_slimefun_mod.client;

import com.bapel_slimefun_mod.automation.MachineAutomationHandler;
import com.bapel_slimefun_mod.automation.SlimefunDataLoader;
import com.bapel_slimefun_mod.automation.SlimefunMachineData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI untuk memilih recipe/item yang akan dibuat
 * UPDATED: Sekarang langsung menjalankan automation setelah memilih resep
 */
public class RecipeSelectionScreen extends Screen {
    
    private static final int SLOT_SIZE = 18;
    private static final int SLOTS_PER_ROW = 9;
    private static final int PADDING = 8;
    
    private final List<RecipeEntry> recipes = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int selectedIndex = -1;
    private boolean autoStartAutomation = true; // Opsi untuk auto-start automation
    
    public RecipeSelectionScreen() {
        super(Component.literal("§6§lSelect Recipe"));
        loadRecipes();
    }
    
    /**
     * Constructor dengan opsi auto-start automation
     */
    public RecipeSelectionScreen(boolean autoStart) {
        super(Component.literal("§6§lSelect Recipe"));
        this.autoStartAutomation = autoStart;
        loadRecipes();
    }
    
    private void loadRecipes() {
        Map<String, SlimefunMachineData> machines = SlimefunDataLoader.getAllMachines();
        
        for (Map.Entry<String, SlimefunMachineData> entry : machines.entrySet()) {
            SlimefunMachineData machine = entry.getValue();
            
            // Hanya tampilkan machine yang punya recipe
            if (machine.getRecipe() != null && !machine.getRecipe().isEmpty()) {
                recipes.add(new RecipeEntry(
                    machine.getId(),
                    machine.getName(),
                    machine.getRecipe()
                ));
            }
        }
        
        // Hitung max scroll
        int rows = (recipes.size() + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW;
        int visibleRows = getVisibleRows();
        maxScroll = Math.max(0, rows - visibleRows);
    }
    
    @Override
    protected void init() {
        super.init();
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Background
        renderBackground(graphics, mouseX, mouseY, delta);
        
        int startX = (width - (SLOTS_PER_ROW * SLOT_SIZE)) / 2;
        int startY = 40;
        
        // Title
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        
        // Info text
        String info = autoStartAutomation ? 
            "§7Click to select and §aAUTO-START§7 automation" :
            "§7Click to select a recipe to craft";
        graphics.drawCenteredString(font, Component.literal(info), width / 2, 32, 0xFFFFFF);
        
        // Render recipe slots
        int visibleRows = getVisibleRows();
        int startIndex = scrollOffset * SLOTS_PER_ROW;
        int endIndex = Math.min(recipes.size(), startIndex + (visibleRows * SLOTS_PER_ROW));
        
        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int row = relativeIndex / SLOTS_PER_ROW;
            int col = relativeIndex % SLOTS_PER_ROW;
            
            int x = startX + (col * SLOT_SIZE);
            int y = startY + (row * SLOT_SIZE);
            
            // Slot background
            int bgColor = 0x80000000;
            if (i == selectedIndex) {
                bgColor = 0x8000FF00; // Green if selected
            } else if (isMouseOver(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
                bgColor = 0x80FFFFFF; // White if hovered
            }
            
            graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, bgColor);
            
            // Border
            graphics.fill(x, y, x + SLOT_SIZE, y + 1, 0xFF8B8B8B);
            graphics.fill(x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF8B8B8B);
            graphics.fill(x, y, x + 1, y + SLOT_SIZE, 0xFF8B8B8B);
            graphics.fill(x + SLOT_SIZE - 1, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF8B8B8B);
            
            // Item icon (simplified - just show a placeholder)
            RecipeEntry recipe = recipes.get(i);
            String displayName = recipe.name.length() > 20 ? 
                recipe.name.substring(0, 17) + "..." : recipe.name;
            
            // Draw item representation
            graphics.drawString(font, "§e■", x + 3, y + 5, 0xFFFFFF);
        }
        
        // Scroll indicator
        if (maxScroll > 0) {
            String scrollText = String.format("§7Scroll: %d/%d", scrollOffset + 1, maxScroll + 1);
            graphics.drawCenteredString(font, Component.literal(scrollText), 
                width / 2, startY + (visibleRows * SLOT_SIZE) + 5, 0xFFFFFF);
        }
        
        // Selected recipe info
        if (selectedIndex >= 0 && selectedIndex < recipes.size()) {
            RecipeEntry selected = recipes.get(selectedIndex);
            int infoY = startY + (visibleRows * SLOT_SIZE) + 20;
            
            graphics.drawCenteredString(font, 
                Component.literal("§6Selected: §f" + selected.name), 
                width / 2, infoY, 0xFFFFFF);
            
            if (autoStartAutomation) {
                graphics.drawCenteredString(font, 
                    Component.literal("§7Click again to confirm and §aSTART AUTOMATION"), 
                    width / 2, infoY + 12, 0xFFFFFF);
            } else {
                graphics.drawCenteredString(font, 
                    Component.literal("§7Press ENTER to confirm or ESC to cancel"), 
                    width / 2, infoY + 12, 0xFFFFFF);
            }
        }
        
        super.render(graphics, mouseX, mouseY, delta);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            int startX = (width - (SLOTS_PER_ROW * SLOT_SIZE)) / 2;
            int startY = 40;
            int visibleRows = getVisibleRows();
            int startIndex = scrollOffset * SLOTS_PER_ROW;
            int endIndex = Math.min(recipes.size(), startIndex + (visibleRows * SLOTS_PER_ROW));
            
            for (int i = startIndex; i < endIndex; i++) {
                int relativeIndex = i - startIndex;
                int row = relativeIndex / SLOTS_PER_ROW;
                int col = relativeIndex % SLOTS_PER_ROW;
                
                int x = startX + (col * SLOT_SIZE);
                int y = startY + (row * SLOT_SIZE);
                
                if (isMouseOver((int)mouseX, (int)mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
                    if (selectedIndex == i) {
                        // Double click atau click lagi pada item yang sama - konfirmasi pilihan
                        confirmSelection();
                    } else {
                        // First click - select item
                        selectedIndex = i;
                    }
                    return true;
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0) {
            if (verticalAmount > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
            } else if (verticalAmount < 0) {
                scrollOffset = Math.min(maxScroll, scrollOffset + 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter key - confirm selection
        if (keyCode == 257 && selectedIndex >= 0) { // GLFW.GLFW_KEY_ENTER
            confirmSelection();
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    /**
     * Konfirmasi pilihan resep dan mulai automation
     */
    private void confirmSelection() {
        if (selectedIndex < 0 || selectedIndex >= recipes.size()) return;
        
        RecipeEntry selected = recipes.get(selectedIndex);
        
        // Set selected recipe di automation handler
        MachineAutomationHandler.setSelectedRecipe(selected.id);
        
        if (minecraft != null && minecraft.player != null) {
            if (autoStartAutomation) {
                // Aktifkan automation jika belum aktif
                if (!MachineAutomationHandler.isAutomationEnabled()) {
                    MachineAutomationHandler.setAutomationEnabled(true);
                }
                
                minecraft.player.displayClientMessage(
                    Component.literal("§a✓ Recipe selected: " + selected.name + " - §6AUTOMATION STARTED!"), 
                    false
                );
                
                minecraft.player.displayClientMessage(
                    Component.literal("§7Automation akan menaruh item ke input slot dan mengambil dari output slot"), 
                    false
                );
            } else {
                minecraft.player.displayClientMessage(
                    Component.literal("§a✓ Selected: " + selected.name), 
                    true
                );
            }
        }
        
        onClose();
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    private int getVisibleRows() {
        return Math.min(6, (height - 100) / SLOT_SIZE);
    }
    
    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
    
    /**
     * Recipe entry data class
     */
    private static class RecipeEntry {
        final String id;
        final String name;
        final List<String> recipe;
        
        RecipeEntry(String id, String name, List<String> recipe) {
            this.id = id;
            this.name = name.replaceAll("§[0-9a-fk-or]", ""); // Strip color codes
            this.recipe = recipe;
        }
    }
}