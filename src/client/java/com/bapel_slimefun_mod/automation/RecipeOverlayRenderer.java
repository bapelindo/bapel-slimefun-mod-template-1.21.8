package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Renders an in-game overlay for recipe selection without opening a screen
 * FIXED VERSION - Better toggle handling and user feedback
 */
public class RecipeOverlayRenderer {
    private static final Gson GSON = new Gson();
    private static JsonObject config;
    private static boolean overlayVisible = false;
    private static List<RecipeData> availableRecipes = new ArrayList<>();
    private static int selectedIndex = 0;
    private static int scrollOffset = 0;
    private static long fadeStartTime = 0;
    private static boolean fadingIn = false;
    private static SlimefunMachineData currentMachine = null;
    
    // Config cache
    private static int posX, posY;
    private static int width, maxHeight, entryHeight, padding, spacing;
    private static int maxVisible;
    private static boolean showIndex, showInputCount, showOutput, showCompletion, showKeybinds;
    
    /**
     * Initialize the overlay system
     */
    public static void initialize() {
        loadConfig();
        BapelSlimefunMod.LOGGER.info("Recipe Overlay Renderer initialized");
    }
    
    /**
     * Load configuration from JSON
     */
    private static void loadConfig() {
        try {
            InputStream stream = RecipeOverlayRenderer.class
                .getResourceAsStream("/assets/bapel-slimefun-mod/recipe_overlay_config.json");
            
            if (stream == null) {
                BapelSlimefunMod.LOGGER.warn("Could not find recipe_overlay_config.json, using defaults");
                setDefaultConfig();
                return;
            }
            
            config = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                JsonObject.class
            );
            
            cacheConfigValues();
            BapelSlimefunMod.LOGGER.info("Recipe overlay config loaded successfully");
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to load recipe overlay config", e);
            setDefaultConfig();
        }
    }
    
    /**
     * Set default configuration
     */
    private static void setDefaultConfig() {
        posX = 10;
        posY = 60;
        width = 250;
        maxHeight = 400;
        entryHeight = 40;
        padding = 8;
        spacing = 4;
        maxVisible = 8;
        showIndex = true;
        showInputCount = true;
        showOutput = true;
        showCompletion = true;
        showKeybinds = true;
    }
    
    /**
     * Cache frequently accessed config values
     */
    private static void cacheConfigValues() {
        if (config == null) {
            setDefaultConfig();
            return;
        }
        
        JsonObject overlay = config.getAsJsonObject("overlay");
        
        // Position
        JsonObject position = overlay.getAsJsonObject("position");
        posX = position.get("x").getAsInt();
        posY = position.get("y").getAsInt();
        
        // Dimensions
        JsonObject dimensions = overlay.getAsJsonObject("dimensions");
        width = dimensions.get("width").getAsInt();
        maxHeight = dimensions.get("maxHeight").getAsInt();
        entryHeight = dimensions.get("recipeEntryHeight").getAsInt();
        padding = dimensions.get("padding").getAsInt();
        spacing = dimensions.get("spacing").getAsInt();
        
        // Display options
        JsonObject display = overlay.getAsJsonObject("display");
        showIndex = display.get("showRecipeIndex").getAsBoolean();
        showInputCount = display.get("showInputCount").getAsBoolean();
        showOutput = display.get("showOutputInfo").getAsBoolean();
        showCompletion = display.get("showCompletionPercentage").getAsBoolean();
        showKeybinds = display.get("showKeybindHints").getAsBoolean();
        maxVisible = display.get("maxRecipesVisible").getAsInt();
    }
    
    /**
     * Show the overlay for a specific machine
     */
    public static void show(SlimefunMachineData machine) {
        if (machine == null) {
            BapelSlimefunMod.LOGGER.warn("Cannot show overlay - machine is null");
            sendPlayerMessage("§c[Slimefun] No machine detected");
            return;
        }
        
        currentMachine = machine;
        loadRecipesForMachine(machine);
        
        if (availableRecipes.isEmpty()) {
            BapelSlimefunMod.LOGGER.warn("No recipes available for machine: {}", machine.getName());
            sendPlayerMessage("§c[Slimefun] No recipes for: " + machine.getName());
            return;
        }
        
        overlayVisible = true;
        selectedIndex = 0;
        scrollOffset = 0;
        fadeStartTime = System.currentTimeMillis();
        fadingIn = true;
        
        BapelSlimefunMod.LOGGER.info("Showing recipe overlay for: {} ({} recipes)", 
            machine.getName(), availableRecipes.size());
        sendPlayerMessage("§a[Slimefun] Recipe Overlay: §f" + machine.getName());
    }
    
    /**
     * Hide the overlay
     */
/**
 * Hide the overlay
 */
public static void hide() {
    if (!overlayVisible) {
        return; // Already hidden
    }
    
    overlayVisible = false;
    currentMachine = null;
    availableRecipes = new ArrayList<>(); // ✅ Create new list instead of clear()
    
    BapelSlimefunMod.LOGGER.info("Recipe overlay hidden");
}
    
    /**
     * Toggle overlay visibility
     * FIXED: Better handling and user feedback
     */
    public static void toggle() {
        if (overlayVisible) {
            hide();
        } else {
            // Check if player is in a machine GUI
            SlimefunMachineData machine = MachineAutomationHandler.getCurrentMachine();
            if (machine != null) {
                show(machine);
            } else {
                BapelSlimefunMod.LOGGER.info("No machine detected, cannot show recipe overlay");
                sendPlayerMessage("§e[Slimefun] Open a Slimefun machine first!");
            }
        }
    }
    
    /**
     * Send a message to the player (action bar)
     */
    private static void sendPlayerMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), true);
        }
    }
    
    /**
     * Load all recipes for the given machine
     */
private static void loadRecipesForMachine(SlimefunMachineData machine) {
    availableRecipes = new ArrayList<>(); // ✅ Create new list instead of clear()
    
    // Try to load from database first
    if (RecipeDatabase.isInitialized() && RecipeDatabase.hasMachineRecipes(machine.getId())) {
        availableRecipes = new ArrayList<>(RecipeDatabase.getRecipesForMachine(machine.getId())); // ✅ Copy into new list
        BapelSlimefunMod.LOGGER.info("Loaded {} recipes from database for {}", 
            availableRecipes.size(), machine.getName());
        } else {
            // Fallback: create recipe from machine data
            List<String> recipeStrings = machine.getRecipe();
            
            if (recipeStrings != null && !recipeStrings.isEmpty()) {
                List<RecipeHandler.RecipeIngredient> inputs = RecipeHandler.parseRecipe(recipeStrings);
                
                // Create a default output
                List<RecipeData.RecipeOutput> outputs = new ArrayList<>();
                outputs.add(new RecipeData.RecipeOutput("OUTPUT_ITEM", "Crafted Item", 1));
                
                RecipeData recipe = new RecipeData(
                    machine.getId() + "_recipe_1",
                    machine.getId(),
                    inputs,
                    outputs
                );
                
                availableRecipes.add(recipe);
                BapelSlimefunMod.LOGGER.info("Created fallback recipe for {}", machine.getName());
            } else {
                BapelSlimefunMod.LOGGER.warn("No recipes found for machine: {}", machine.getName());
            }
        }
    }
    
    /**
     * Main render method - called every frame
     */
    public static void render(GuiGraphics graphics, float partialTicks) {
        if (!overlayVisible || currentMachine == null || availableRecipes.isEmpty()) {
            return;
        }
        
        int alpha = calculateAlpha();
        if (alpha <= 0) return;
        
        int yPos = posY;
        
        // Render background
        renderBackground(graphics, yPos, alpha);
        
        // Render title
        yPos += padding;
        yPos = renderTitle(graphics, yPos, alpha);
        
        // Render recipes
        yPos += spacing;
        yPos = renderRecipeList(graphics, yPos, alpha);
        
        // Render keybind hints
        if (showKeybinds) {
            yPos += spacing;
            renderKeybindHints(graphics, yPos, alpha);
        }
    }
    
    /**
     * Calculate fade alpha value
     */
    private static int calculateAlpha() {
        if (config == null) return 255;
        
        try {
            JsonObject animation = config.getAsJsonObject("overlay").getAsJsonObject("animation");
            if (!animation.get("enabled").getAsBoolean()) {
                return 255;
            }
            
            long elapsed = System.currentTimeMillis() - fadeStartTime;
            int duration = fadingIn ? 
                animation.get("fadeInDuration").getAsInt() : 
                animation.get("fadeOutDuration").getAsInt();
            
            if (elapsed >= duration) {
                return fadingIn ? 255 : 0;
            }
            
            float progress = (float) elapsed / duration;
            return (int) (fadingIn ? progress * 255 : (1 - progress) * 255);
            
        } catch (Exception e) {
            return 255;
        }
    }
    
    /**
     * Render background box
     */
    private static void renderBackground(GuiGraphics graphics, int yPos, int alpha) {
        int totalHeight = calculateTotalHeight();
        int bgColor = getColor("background", alpha);
        int borderColor = getColor("border", alpha);
        
        // Main background
        graphics.fill(posX, yPos, posX + width, yPos + totalHeight, bgColor);
        
        // Border
        graphics.fill(posX, yPos, posX + width, yPos + 1, borderColor); // Top
        graphics.fill(posX, yPos + totalHeight - 1, posX + width, yPos + totalHeight, borderColor); // Bottom
        graphics.fill(posX, yPos, posX + 1, yPos + totalHeight, borderColor); // Left
        graphics.fill(posX + width - 1, yPos, posX + width, yPos + totalHeight, borderColor); // Right
    }
    
    /**
     * Render title
     */
    private static int renderTitle(GuiGraphics graphics, int yPos, int alpha) {
        Minecraft mc = Minecraft.getInstance();
        int textColor = getColor("titleText", alpha);
        
        String title = currentMachine != null ? 
            currentMachine.getName() + " - Recipes" : 
            "Recipe Overlay";
        
        int centerX = posX + width / 2;
        
        graphics.drawCenteredString(mc.font, title, centerX, yPos, textColor);
        
        return yPos + mc.font.lineHeight + spacing;
    }
    
    /**
     * Render the list of recipes
     */
    private static int renderRecipeList(GuiGraphics graphics, int startY, int alpha) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return startY;
        
        List<ItemStack> inventory = getPlayerInventory(player);
        int yPos = startY;
        
        int visibleStart = scrollOffset;
        int visibleEnd = Math.min(scrollOffset + maxVisible, availableRecipes.size());
        
        for (int i = visibleStart; i < visibleEnd; i++) {
            RecipeData recipe = availableRecipes.get(i);
            boolean isSelected = (i == selectedIndex);
            
            yPos = renderRecipeEntry(graphics, recipe, i, yPos, alpha, isSelected, inventory);
            yPos += spacing;
        }
        
        return yPos;
    }
    
    /**
     * Render a single recipe entry
     */
    private static int renderRecipeEntry(GuiGraphics graphics, RecipeData recipe, int index,
                                        int yPos, int alpha, boolean isSelected, 
                                        List<ItemStack> inventory) {
        Minecraft mc = Minecraft.getInstance();
        
        // Draw selection highlight
        if (isSelected) {
            int highlightColor = getColor("selectedBackground", alpha);
            graphics.fill(posX + 2, yPos, posX + width - 2, yPos + entryHeight, highlightColor);
        }
        
        int textX = posX + padding;
        int textY = yPos + 4;
        int textColor = getColor("normalText", alpha);
        
        // Recipe index and name
        StringBuilder line1 = new StringBuilder();
        if (showIndex) {
            line1.append(index + 1).append(". ");
        }
        
        RecipeData.RecipeOutput primaryOutput = recipe.getPrimaryOutput();
        if (primaryOutput != null && showOutput) {
            line1.append(primaryOutput.getDisplayName());
            if (primaryOutput.getAmount() > 1) {
                line1.append(" x").append(primaryOutput.getAmount());
            }
        }
        
        graphics.drawString(mc.font, line1.toString(), textX, textY, textColor);
        textY += mc.font.lineHeight + 2;
        
        // Inputs info
        if (showInputCount) {
            String inputInfo = "Inputs: " + recipe.getGroupedInputs().size() + " types";
            graphics.drawString(mc.font, inputInfo, textX + 10, textY, 
                getColor("normalText", alpha / 2));
            textY += mc.font.lineHeight;
        }
        
        // Completion percentage
        if (showCompletion) {
            RecipeHandler.RecipeSummary summary = new RecipeHandler.RecipeSummary(
                inventory, recipe.getInputs()
            );
            
            float completion = summary.getCompletionPercentage();
            int completionColor = completion >= 1.0f ? 
                getColor("availableText", alpha) : 
                getColor("missingText", alpha);
            
            String completionText = String.format("%.0f%%", completion * 100);
            graphics.drawString(mc.font, completionText, textX + 10, textY, completionColor);
        }
        
        return yPos + entryHeight;
    }
    
    /**
     * Render keybind hints
     */
    private static void renderKeybindHints(GuiGraphics graphics, int yPos, int alpha) {
        Minecraft mc = Minecraft.getInstance();
        int textColor = getColor("normalText", alpha / 2);
        
        String hints = "[↑↓] Navigate  [Enter] Select  [R/Esc] Close";
        graphics.drawCenteredString(mc.font, hints, posX + width / 2, yPos, textColor);
    }
    
    /**
     * Calculate total height needed for overlay
     */
    private static int calculateTotalHeight() {
        Minecraft mc = Minecraft.getInstance();
        
        int height = padding * 2; // Top and bottom padding
        height += mc.font.lineHeight + spacing; // Title
        height += spacing; // After title
        
        int visibleRecipes = Math.min(maxVisible, availableRecipes.size());
        height += visibleRecipes * (entryHeight + spacing);
        
        if (showKeybinds) {
            height += mc.font.lineHeight + spacing; // Keybind hints
        }
        
        return Math.min(height, maxHeight);
    }
    
    /**
     * Get color from config with alpha
     */
    private static int getColor(String colorName, int alpha) {
        if (config == null) {
            return (alpha << 24) | 0xFFFFFF; // White with alpha
        }
        
        try {
            JsonObject colors = config.getAsJsonObject("overlay").getAsJsonObject("colors");
            JsonObject color = colors.getAsJsonObject(colorName);
            
            int r = color.get("r").getAsInt();
            int g = color.get("g").getAsInt();
            int b = color.get("b").getAsInt();
            int a = Math.min(alpha, color.get("a").getAsInt());
            
            return (a << 24) | (r << 16) | (g << 8) | b;
            
        } catch (Exception e) {
            return (alpha << 24) | 0xFFFFFF;
        }
    }
    
    /**
     * Get player inventory items
     */
    private static List<ItemStack> getPlayerInventory(LocalPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        
        return stacks;
    }
    
    // ===== Navigation Methods =====
    
    /**
     * Move selection up
     */
    public static void moveUp() {
        if (availableRecipes.isEmpty()) return;
        
        selectedIndex--;
        if (selectedIndex < 0) {
            selectedIndex = availableRecipes.size() - 1;
        }
        
        updateScrollOffset();
    }
    
    /**
     * Move selection down
     */
    public static void moveDown() {
        if (availableRecipes.isEmpty()) return;
        
        selectedIndex++;
        if (selectedIndex >= availableRecipes.size()) {
            selectedIndex = 0;
        }
        
        updateScrollOffset();
    }
    
    /**
     * Update scroll offset to keep selected item visible
     */
    private static void updateScrollOffset() {
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + maxVisible) {
            scrollOffset = selectedIndex - maxVisible + 1;
        }
    }
    
    /**
     * Select current recipe
     */
    public static void selectCurrent() {
        if (availableRecipes.isEmpty() || selectedIndex < 0 || 
            selectedIndex >= availableRecipes.size()) {
            return;
        }
        
        RecipeData selected = availableRecipes.get(selectedIndex);
        MachineAutomationHandler.setSelectedRecipe(selected.getRecipeId());
        
        BapelSlimefunMod.LOGGER.info("Selected recipe: {}", selected.getDisplayString());
        sendPlayerMessage("§a[Slimefun] Selected: §f" + selected.getDisplayString());
        hide();
    }
    
    /**
     * Check if overlay is visible
     */
    public static boolean isVisible() {
        return overlayVisible;
    }
    
    /**
     * Get selected index
     */
    public static int getSelectedIndex() {
        return selectedIndex;
    }
    
    /**
     * Get available recipes
     */
    public static List<RecipeData> getAvailableRecipes() {
        return new ArrayList<>(availableRecipes);
    }
    
    /**
     * Get current machine
     */
    public static SlimefunMachineData getCurrentMachine() {
        return currentMachine;
    }
}