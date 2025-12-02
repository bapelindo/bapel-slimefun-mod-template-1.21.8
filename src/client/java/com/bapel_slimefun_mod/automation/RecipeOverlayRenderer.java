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
    
    private static long lastToggleTime = 0;
    private static final long TOGGLE_COOLDOWN = 250;
    
    private static int posX, posY;
    private static int width, maxHeight, entryHeight, padding, spacing;
    private static int maxVisible;
    private static boolean showIndex, showInputCount, showOutput, showCompletion, showKeybinds;
    
    public static void initialize() {
        loadConfig();
        BapelSlimefunMod.LOGGER.info("Recipe Overlay Renderer initialized");
    }
    
    private static void loadConfig() {
        try {
            InputStream stream = RecipeOverlayRenderer.class
                .getResourceAsStream("/assets/bapel-slimefun-mod/recipe_overlay_config.json");
            if (stream == null) { setDefaultConfig(); return; }
            config = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
            cacheConfigValues();
        } catch (Exception e) { setDefaultConfig(); }
    }
    
    private static void setDefaultConfig() {
        posX = 10; posY = 60; width = 250; maxHeight = 400; entryHeight = 40;
        padding = 8; spacing = 4; maxVisible = 8;
        showIndex = true; showInputCount = true; showOutput = true; showCompletion = true; showKeybinds = true;
    }
    
    private static void cacheConfigValues() {
        if (config == null) { setDefaultConfig(); return; }
        try {
            JsonObject overlay = config.getAsJsonObject("overlay");
            JsonObject position = overlay.getAsJsonObject("position");
            posX = position.get("x").getAsInt(); posY = position.get("y").getAsInt();
            JsonObject dimensions = overlay.getAsJsonObject("dimensions");
            width = dimensions.get("width").getAsInt(); maxHeight = dimensions.get("maxHeight").getAsInt();
            entryHeight = dimensions.get("recipeEntryHeight").getAsInt(); padding = dimensions.get("padding").getAsInt();
            spacing = dimensions.get("spacing").getAsInt();
            JsonObject display = overlay.getAsJsonObject("display");
            showIndex = display.get("showRecipeIndex").getAsBoolean(); showInputCount = display.get("showInputCount").getAsBoolean();
            showOutput = display.get("showOutputInfo").getAsBoolean(); showCompletion = display.get("showCompletionPercentage").getAsBoolean();
            showKeybinds = display.get("showKeybindHints").getAsBoolean(); maxVisible = display.get("maxRecipesVisible").getAsInt();
        } catch (Exception e) { setDefaultConfig(); }
    }
    
    public static void show(SlimefunMachineData machine) {
        if (machine == null) return;
        currentMachine = machine;
        loadRecipesForMachine(machine);
        if (availableRecipes.isEmpty()) {
            sendPlayerMessage("§c[Slimefun] No recipes for: " + machine.getName());
            currentMachine = null;
            return;
        }
        overlayVisible = true;
        selectedIndex = 0;
        scrollOffset = 0;
        fadeStartTime = System.currentTimeMillis();
        fadingIn = true;
    }
    
    public static void hide() {
        if (!overlayVisible) return;
        overlayVisible = false;
        currentMachine = null;
        availableRecipes = new ArrayList<>();
    }
    
    public static void toggle() {
        long now = System.currentTimeMillis();
        if (now - lastToggleTime < TOGGLE_COOLDOWN) return;
        lastToggleTime = now;
        if (overlayVisible) hide();
        else {
            SlimefunMachineData machine = MachineAutomationHandler.getCurrentMachine();
            if (machine != null) show(machine);
            else sendPlayerMessage("§e[Slimefun] Open a Slimefun machine first!");
        }
    }
    
    private static void sendPlayerMessage(String message) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.displayClientMessage(Component.literal(message), true);
        } catch (Exception e) {}
    }
    
    private static void loadRecipesForMachine(SlimefunMachineData machine) {
        List<RecipeData> newRecipes = new ArrayList<>();
        try {
            if (RecipeDatabase.isInitialized() && RecipeDatabase.hasMachineRecipes(machine.getId())) {
                newRecipes.addAll(RecipeDatabase.getRecipesForMachine(machine.getId()));
            } else {
                List<String> recipeStrings = machine.getRecipe();
                if (recipeStrings != null && !recipeStrings.isEmpty()) {
                    List<RecipeHandler.RecipeIngredient> inputs = RecipeHandler.parseRecipe(recipeStrings);
                    List<RecipeData.RecipeOutput> outputs = new ArrayList<>();
                    outputs.add(new RecipeData.RecipeOutput("OUTPUT_ITEM", "Crafted Item", 1));
                    newRecipes.add(new RecipeData(machine.getId() + "_recipe_1", machine.getId(), inputs, outputs));
                }
            }
        } catch (Exception e) {}
        availableRecipes = newRecipes;
    }
    
    public static void render(GuiGraphics graphics, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            if (overlayVisible) hide();
            return;
        }
        
        if (!overlayVisible || currentMachine == null || availableRecipes.isEmpty()) return;
        
        try {
            int alpha = calculateAlpha();
            if (alpha <= 0) return;
            
            int yPos = posY;
            renderBackground(graphics, yPos, alpha);
            yPos += padding;
            yPos = renderTitle(graphics, yPos, alpha);
            yPos += spacing;
            yPos = renderRecipeList(graphics, yPos, alpha);
            
            if (showKeybinds) {
                yPos += spacing;
                renderKeybindHints(graphics, yPos, alpha);
            }
        } catch (Exception e) { hide(); }
    }
    
    private static int calculateAlpha() {
        if (config == null) return 255;
        try {
            JsonObject animation = config.getAsJsonObject("overlay").getAsJsonObject("animation");
            if (!animation.get("enabled").getAsBoolean()) return 255;
            long elapsed = System.currentTimeMillis() - fadeStartTime;
            int duration = fadingIn ? animation.get("fadeInDuration").getAsInt() : animation.get("fadeOutDuration").getAsInt();
            if (elapsed >= duration) return fadingIn ? 255 : 0;
            float progress = (float) elapsed / duration;
            return (int) (fadingIn ? progress * 255 : (1 - progress) * 255);
        } catch (Exception e) { return 255; }
    }
    
    private static void renderBackground(GuiGraphics graphics, int yPos, int alpha) {
        try {
            int totalHeight = calculateTotalHeight();
            int bgColor = getColor("background", alpha);
            int borderColor = getColor("border", alpha);
            graphics.fill(posX, yPos, posX + width, yPos + totalHeight, bgColor);
            graphics.fill(posX, yPos, posX + width, yPos + 1, borderColor);
            graphics.fill(posX, yPos + totalHeight - 1, posX + width, yPos + totalHeight, borderColor);
            graphics.fill(posX, yPos, posX + 1, yPos + totalHeight, borderColor);
            graphics.fill(posX + width - 1, yPos, posX + width, yPos + totalHeight, borderColor);
        } catch (Exception e) {}
    }
    
    private static int renderTitle(GuiGraphics graphics, int yPos, int alpha) {
        try {
            Minecraft mc = Minecraft.getInstance();
            int textColor = getColor("titleText", alpha);
            String title = currentMachine != null ? currentMachine.getName() : "Recipes";
            graphics.drawCenteredString(mc.font, title, posX + width / 2, yPos, textColor);
            return yPos + mc.font.lineHeight + spacing;
        } catch (Exception e) { return yPos; }
    }
    
    private static int renderRecipeList(GuiGraphics graphics, int startY, int alpha) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return startY;
            List<ItemStack> inventory = getPlayerInventory(mc.player);
            int yPos = startY;
            int recipeCount = availableRecipes.size();
            if (recipeCount == 0) return yPos;
            int visibleStart = Math.max(0, Math.min(scrollOffset, recipeCount - 1));
            int visibleEnd = Math.min(visibleStart + maxVisible, recipeCount);
            List<RecipeData> recipesToRender = new ArrayList<>(availableRecipes.subList(visibleStart, visibleEnd));
            for (int i = 0; i < recipesToRender.size(); i++) {
                RecipeData recipe = recipesToRender.get(i);
                boolean isSelected = ((visibleStart + i) == selectedIndex);
                yPos = renderRecipeEntry(graphics, recipe, visibleStart + i, yPos, alpha, isSelected, inventory);
                yPos += spacing;
            }
            return yPos;
        } catch (Exception e) { return startY; }
    }
    
    private static String formatItemName(String itemId) {
        if (itemId == null || itemId.isEmpty()) return "Unknown";
        String[] words = itemId.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() > 0) sb.append(" ");
            if (!word.isEmpty()) { sb.append(Character.toUpperCase(word.charAt(0))); sb.append(word.substring(1)); }
        }
        return sb.toString();
    }
    
    private static int renderRecipeEntry(GuiGraphics graphics, RecipeData recipe, int index, int yPos, int alpha, boolean isSelected, List<ItemStack> inventory) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (isSelected) {
                int highlightColor = getColor("selectedBackground", alpha);
                graphics.fill(posX + 2, yPos, posX + width - 2, yPos + entryHeight, highlightColor);
            }
            int textX = posX + padding; int textY = yPos + 4; int textColor = getColor("normalText", alpha);
            StringBuilder line1 = new StringBuilder();
            if (showIndex) line1.append(index + 1).append(". ");
            RecipeData.RecipeOutput primaryOutput = recipe.getPrimaryOutput();
            if (primaryOutput != null && showOutput) {
                line1.append(primaryOutput.getDisplayName());
                if (primaryOutput.getAmount() > 1) line1.append(" x").append(primaryOutput.getAmount());
            } else line1.append("Unknown Output");
            graphics.drawString(mc.font, line1.toString(), textX, textY, textColor);
            textY += mc.font.lineHeight + 2;
            
            if (showInputCount) {
                Map<String, Integer> inputs = recipe.getGroupedInputs();
                StringBuilder inputStr = new StringBuilder();
                int i = 0;
                for (Map.Entry<String, Integer> entry : inputs.entrySet()) {
                    if (i > 0) inputStr.append(" + ");
                    inputStr.append(formatItemName(entry.getKey()));
                    if (entry.getValue() > 1) inputStr.append(" x").append(entry.getValue());
                    i++;
                    if (i >= 3 && inputs.size() > 3) { inputStr.append("..."); break; }
                }
                graphics.drawString(mc.font, inputStr.toString(), textX + 10, textY, getColor("normalText", alpha / 2));
                textY += mc.font.lineHeight;
            }
            
            if (showCompletion) {
                RecipeHandler.RecipeSummary summary = new RecipeHandler.RecipeSummary(inventory, recipe.getInputs());
                float completion = summary.getCompletionPercentage();
                int completionColor = completion >= 1.0f ? getColor("availableText", alpha) : getColor("missingText", alpha);
                String completionText = String.format("%.0f%%", completion * 100);
                graphics.drawString(mc.font, completionText, textX + 10, textY, completionColor);
            }
            return yPos + entryHeight;
        } catch (Exception e) { return yPos + entryHeight; }
    }
    
    private static void renderKeybindHints(GuiGraphics graphics, int yPos, int alpha) {
        try {
            Minecraft mc = Minecraft.getInstance();
            int textColor = getColor("normalText", alpha / 2);
            String hints = "[↑↓] Navigate  [Enter] Select  [R/Esc] Close";
            graphics.drawCenteredString(mc.font, hints, posX + width / 2, yPos, textColor);
        } catch (Exception e) {}
    }
    
    private static int calculateTotalHeight() {
        try {
            Minecraft mc = Minecraft.getInstance();
            int height = padding * 2;
            height += mc.font.lineHeight + spacing;
            height += spacing;
            int visibleRecipes = Math.min(maxVisible, availableRecipes.size());
            height += visibleRecipes * (entryHeight + spacing);
            if (showKeybinds) height += mc.font.lineHeight + spacing;
            return Math.min(height, maxHeight);
        } catch (Exception e) { return maxHeight; }
    }
    
    private static int getColor(String colorName, int alpha) {
        if (config == null) return (alpha << 24) | 0xFFFFFF;
        try {
            JsonObject colors = config.getAsJsonObject("overlay").getAsJsonObject("colors");
            JsonObject color = colors.getAsJsonObject(colorName);
            int r = color.get("r").getAsInt(); int g = color.get("g").getAsInt(); int b = color.get("b").getAsInt();
            int a = Math.min(alpha, color.get("a").getAsInt());
            return (a << 24) | (r << 16) | (g << 8) | b;
        } catch (Exception e) { return (alpha << 24) | 0xFFFFFF; }
    }
    
    private static List<ItemStack> getPlayerInventory(LocalPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        try {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) stacks.add(stack);
            }
        } catch (Exception e) {}
        return stacks;
    }
    
    public static void moveUp() {
        if (availableRecipes == null || availableRecipes.isEmpty()) return;
        selectedIndex--;
        if (selectedIndex < 0) selectedIndex = availableRecipes.size() - 1;
        updateScrollOffset();
    }
    
    public static void moveDown() {
        if (availableRecipes == null || availableRecipes.isEmpty()) return;
        selectedIndex++;
        if (selectedIndex >= availableRecipes.size()) selectedIndex = 0;
        updateScrollOffset();
    }
    
    private static void updateScrollOffset() {
        if (availableRecipes == null || availableRecipes.isEmpty()) { scrollOffset = 0; return; }
        int recipeCount = availableRecipes.size();
        selectedIndex = Math.max(0, Math.min(selectedIndex, recipeCount - 1));
        if (selectedIndex < scrollOffset) scrollOffset = selectedIndex;
        else if (selectedIndex >= scrollOffset + maxVisible) scrollOffset = selectedIndex - maxVisible + 1;
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, recipeCount - maxVisible)));
    }
    
    public static void selectCurrent() {
        if (availableRecipes == null || availableRecipes.isEmpty()) return;
        if (selectedIndex < 0 || selectedIndex >= availableRecipes.size()) { selectedIndex = 0; return; }
        RecipeData selected = availableRecipes.get(selectedIndex);
        MachineAutomationHandler.setSelectedRecipe(selected.getRecipeId());
        
        try {
            Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[Slimefun] Selected: §f" + selected.getDisplayString()), true
            );
        } catch(Exception e) {}
        
        hide();
    }
    
    public static boolean isVisible() { return overlayVisible; }
    public static int getSelectedIndex() { return selectedIndex; }
    public static List<RecipeData> getAvailableRecipes() { return availableRecipes == null ? new ArrayList<>() : new ArrayList<>(availableRecipes); }
    public static SlimefunMachineData getCurrentMachine() { return currentMachine; }
    public static int getScrollOffset() { return scrollOffset; }
    public static int getPosX() { return posX; }
    public static int getPosY() { return posY; }
    public static int getEntryHeight() { return entryHeight; }
}