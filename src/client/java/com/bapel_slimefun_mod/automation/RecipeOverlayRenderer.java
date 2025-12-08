package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import com.bapel_slimefun_mod.debug.PerformanceMonitor;
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
import java.util.stream.Collectors;

/**
 * ✅ ENHANCED: Recipe Overlay with Search/Filter and Compact Mode
 * 
 * NEW FEATURES:
 * 1. Search/Filter: Type to filter recipes by name
 * 2. Compact Mode: Toggle between normal and compact view
 * 3. Sort Options: Sort by name, completion, craftable
 */
public class RecipeOverlayRenderer {
    private static final Gson GSON = new Gson();
    
    // State
    private static boolean overlayVisible = false;
    private static List<RecipeData> availableRecipes = new ArrayList<>();
    private static List<RecipeData> filteredRecipes = new ArrayList<>(); // ✅ NEW: Filtered list
    private static int selectedIndex = 0;
    private static int scrollOffset = 0;
    private static SlimefunMachineData currentMachine = null;
    
    // ✅ NEW: Search state
    private static String searchQuery = "";
    private static boolean searchMode = false;
    private static long lastSearchInput = 0;
    
    // ✅ NEW: Compact mode
    private static boolean compactMode = false;
    
    // ✅ NEW: Sort mode
    private static SortMode sortMode = SortMode.DEFAULT;
    
    public enum SortMode {
        DEFAULT("Default"),
        NAME("Name A-Z"),
        COMPLETION("Completion %"),
        CRAFTABLE("Craftable First");
        
        private final String displayName;
        SortMode(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }
    
    // Fade animation
    private static long fadeStartTime = 0;
    private static boolean fadingIn = false;
    
    // Toggle cooldown
    private static long lastToggleTime = 0;
    private static final long TOGGLE_COOLDOWN = 250;
    
    // Cached config
    private static boolean configLoaded = false;
    private static int posX, posY;
    private static int width, maxHeight, entryHeight, padding, spacing;
    private static int maxVisible;
    private static boolean showIndex, showInputCount, showOutput, showCompletion, showKeybinds;
    
    // Alpha cache (60 FPS)
    private static int cachedAlpha = 255;
    private static long lastAlphaCalc = 0;
    private static final long ALPHA_CALC_INTERVAL = 16;
    
    // Inventory cache (100ms)
    private static List<ItemStack> cachedInventory = null;
    private static long lastInventoryCache = 0;
    private static final long INVENTORY_CACHE_DURATION = 100;
    
    // Color cache
    private static int bgColor, borderColor, selectedBgColor, searchBgColor;
    private static boolean colorsLoaded = false;
    
    public static void initialize() {
        if (!configLoaded) {
            loadConfig();
        }
    }
    
    private static void loadConfig() {
        if (configLoaded) return;
        
        try {
            InputStream stream = RecipeOverlayRenderer.class
                .getResourceAsStream("/assets/bapel-slimefun-mod/recipe_overlay_config.json");
            
            if (stream == null) { 
                setDefaultConfig(); 
                configLoaded = true;
                return; 
            }
            
            JsonObject config = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8), 
                JsonObject.class
            );
            
            cacheConfigValues(config);
            cacheColors(config);
            configLoaded = true;
            
        } catch (Exception e) { 
            setDefaultConfig();
            configLoaded = true;
        }
    }
    
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
        
        bgColor = 0xE0000000;
        borderColor = 0xFF000000;
        selectedBgColor = 0xC8329632;
        searchBgColor = 0xE0001540; // ✅ NEW: Dark blue for search
        colorsLoaded = true;
    }
    
    private static void cacheConfigValues(JsonObject config) {
        try {
            JsonObject overlay = config.getAsJsonObject("overlay");
            JsonObject position = overlay.getAsJsonObject("position");
            posX = position.get("x").getAsInt(); 
            posY = position.get("y").getAsInt();
            
            JsonObject dimensions = overlay.getAsJsonObject("dimensions");
            width = dimensions.get("width").getAsInt(); 
            maxHeight = dimensions.get("maxHeight").getAsInt();
            entryHeight = dimensions.get("recipeEntryHeight").getAsInt(); 
            padding = dimensions.get("padding").getAsInt();
            spacing = dimensions.get("spacing").getAsInt();
            
            JsonObject display = overlay.getAsJsonObject("display");
            showIndex = display.get("showRecipeIndex").getAsBoolean(); 
            showInputCount = display.get("showInputCount").getAsBoolean();
            showOutput = display.get("showOutputInfo").getAsBoolean(); 
            showCompletion = display.get("showCompletionPercentage").getAsBoolean();
            showKeybinds = display.get("showKeybindHints").getAsBoolean(); 
            maxVisible = display.get("maxRecipesVisible").getAsInt();
        } catch (Exception e) { 
            setDefaultConfig(); 
        }
    }
    
    private static void cacheColors(JsonObject config) {
        if (colorsLoaded) return;
        
        try {
            JsonObject colors = config.getAsJsonObject("overlay").getAsJsonObject("colors");
            
            JsonObject bg = colors.getAsJsonObject("background");
            int bgR = bg.get("r").getAsInt();
            int bgG = bg.get("g").getAsInt();
            int bgB = bg.get("b").getAsInt();
            int bgA = bg.get("a").getAsInt();
            bgColor = (bgA << 24) | (bgR << 16) | (bgG << 8) | bgB;
            
            JsonObject border = colors.getAsJsonObject("border");
            int borR = border.get("r").getAsInt();
            int borG = border.get("g").getAsInt();
            int borB = border.get("b").getAsInt();
            int borA = border.get("a").getAsInt();
            borderColor = (borA << 24) | (borR << 16) | (borG << 8) | borB;
            
            JsonObject selBg = colors.getAsJsonObject("selectedBackground");
            int selR = selBg.get("r").getAsInt();
            int selG = selBg.get("g").getAsInt();
            int selB = selBg.get("b").getAsInt();
            int selA = selBg.get("a").getAsInt();
            selectedBgColor = (selA << 24) | (selR << 16) | (selG << 8) | selB;
            
            // ✅ NEW: Search background color
            searchBgColor = 0xE0001540;
            
            colorsLoaded = true;
        } catch (Exception e) {
            setDefaultConfig();
        }
    }
    
    public static void show(SlimefunMachineData machine) {
        PerformanceMonitor.start("RecipeOverlay.show");
        try {
            if (machine == null) return;
            
            currentMachine = machine;
            loadRecipesForMachine(machine);
            
            if (availableRecipes.isEmpty()) {
                sendPlayerMessage("§c[Slimefun] No recipes for: " + machine.getName());
                currentMachine = null;
                return;
            }
            
            // ✅ Apply initial filter and sort
            applyFilterAndSort();
            
            overlayVisible = true;
            selectedIndex = 0;
            scrollOffset = 0;
            fadeStartTime = System.currentTimeMillis();
            fadingIn = true;
            
            // ✅ Reset search
            searchQuery = "";
            searchMode = false;
            
            cachedInventory = null;
            cachedAlpha = 0;
            lastAlphaCalc = 0;
        } finally {
            PerformanceMonitor.end("RecipeOverlay.show");
        }
    }
    
    public static void hide() {
        if (!overlayVisible) return;
        
        overlayVisible = false;
        currentMachine = null;
        availableRecipes = new ArrayList<>();
        filteredRecipes = new ArrayList<>();
        
        // ✅ Reset search
        searchQuery = "";
        searchMode = false;
        
        cachedInventory = null;
        cachedAlpha = 0;
    }
    
    public static void toggle() {
        long now = System.currentTimeMillis();
        if (now - lastToggleTime < TOGGLE_COOLDOWN) return;
        lastToggleTime = now;
        
        if (overlayVisible) {
            hide();
        } else {
            SlimefunMachineData machine = UnifiedAutomationManager.getCurrentMachine();
            if (machine != null) {
                show(machine);
            } else {
                sendPlayerMessage("§e[Slimefun] Open a Slimefun machine first!");
            }
        }
    }
    
    // ✅ NEW: Toggle search mode
    public static void toggleSearchMode() {
        if (!overlayVisible) return;
        
        searchMode = !searchMode;
        if (searchMode) {
            searchQuery = "";
            sendPlayerMessage("§e[Search] Type to filter recipes...");
        } else {
            searchQuery = "";
            applyFilterAndSort();
            sendPlayerMessage("§7[Search] Search mode disabled");
        }
    }
    
    // ✅ NEW: Toggle compact mode
    public static void toggleCompactMode() {
        if (!overlayVisible) return;
        
        compactMode = !compactMode;
        
        if (compactMode) {
            entryHeight = 20; // Compact: 20px
            maxVisible = 15; // Show more items
            sendPlayerMessage("§a[View] Compact mode enabled");
        } else {
            entryHeight = 40; // Normal: 40px
            maxVisible = 8;
            sendPlayerMessage("§7[View] Normal mode enabled");
        }
        
        updateScrollOffset();
    }
    
    // ✅ NEW: Cycle sort mode
    public static void cycleSortMode() {
        if (!overlayVisible) return;
        
        SortMode[] modes = SortMode.values();
        int nextIndex = (sortMode.ordinal() + 1) % modes.length;
        sortMode = modes[nextIndex];
        
        applyFilterAndSort();
        
        sendPlayerMessage("§b[Sort] " + sortMode.getDisplayName());
    }
    
    // ✅ NEW: Handle text input for search
    public static boolean handleCharTyped(char chr, int modifiers) {
        if (!overlayVisible || !searchMode) return false;
        
        // Valid characters for search
        if (Character.isLetterOrDigit(chr) || chr == ' ' || chr == '_' || chr == '-') {
            searchQuery += chr;
            applyFilterAndSort();
            lastSearchInput = System.currentTimeMillis();
            return true;
        }
        
        return false;
    }
    
    // ✅ NEW: Handle backspace in search
    public static boolean handleBackspace() {
        if (!overlayVisible || !searchMode) return false;
        
        if (!searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            applyFilterAndSort();
            lastSearchInput = System.currentTimeMillis();
            return true;
        }
        
        return false;
    }
    
    // ✅ NEW: Apply filter and sort
    private static void applyFilterAndSort() {
        // Start with all recipes
        filteredRecipes = new ArrayList<>(availableRecipes);
        
        // Apply search filter
        if (!searchQuery.isEmpty()) {
            String query = searchQuery.toLowerCase();
            filteredRecipes = filteredRecipes.stream()
                .filter(recipe -> {
                    String displayName = recipe.getDisplayString().toLowerCase();
                    return displayName.contains(query);
                })
                .collect(Collectors.toList());
        }
        
        // Apply sort
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        List<ItemStack> inventory = (player != null) ? getCachedPlayerInventory(player) : new ArrayList<>();
        
        switch (sortMode) {
            case NAME:
                filteredRecipes.sort(Comparator.comparing(r -> r.getDisplayString()));
                break;
                
            case COMPLETION:
                filteredRecipes.sort((a, b) -> {
                    RecipeHandler.RecipeSummary summaryA = new RecipeHandler.RecipeSummary(inventory, a.getInputs());
                    RecipeHandler.RecipeSummary summaryB = new RecipeHandler.RecipeSummary(inventory, b.getInputs());
                    return Float.compare(summaryB.getCompletionPercentage(), summaryA.getCompletionPercentage());
                });
                break;
                
            case CRAFTABLE:
                filteredRecipes.sort((a, b) -> {
                    RecipeHandler.RecipeSummary summaryA = new RecipeHandler.RecipeSummary(inventory, a.getInputs());
                    RecipeHandler.RecipeSummary summaryB = new RecipeHandler.RecipeSummary(inventory, b.getInputs());
                    
                    boolean canCraftA = summaryA.canCraft();
                    boolean canCraftB = summaryB.canCraft();
                    
                    if (canCraftA && !canCraftB) return -1;
                    if (!canCraftA && canCraftB) return 1;
                    
                    return Float.compare(summaryB.getCompletionPercentage(), summaryA.getCompletionPercentage());
                });
                break;
                
            case DEFAULT:
            default:
                // Keep original order
                break;
        }
        
        // Reset selection if out of bounds
        if (selectedIndex >= filteredRecipes.size()) {
            selectedIndex = Math.max(0, filteredRecipes.size() - 1);
        }
        
        updateScrollOffset();
    }
    
    private static void sendPlayerMessage(String message) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(message), true);
            }
        } catch (Exception ignored) {}
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
                    newRecipes.add(new RecipeData(
                        machine.getId() + "_recipe_1", 
                        machine.getId(), 
                        inputs, 
                        outputs
                    ));
                }
            }
        } catch (Exception ignored) {}
        
        availableRecipes = newRecipes;
    }
    
    public static void render(GuiGraphics graphics, float partialTicks) {
        PerformanceMonitor.start("RecipeOverlay.render");
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                if (overlayVisible) hide();
                return;
            }
            
            if (!overlayVisible || currentMachine == null || filteredRecipes.isEmpty()) {
                return;
            }
            
            int alpha = getCachedAlpha();
            if (alpha <= 0) return;
            
            try {
                int yPos = posY;
                
                renderBackground(graphics, yPos, alpha);
                yPos += padding;
                yPos = renderTitle(graphics, yPos, alpha);
                yPos += spacing;
                
                // ✅ ALWAYS render search bar (click to activate)
                yPos = renderSearchBar(graphics, yPos, alpha);
                yPos += spacing;
                
                yPos = renderRecipeList(graphics, yPos, alpha);
                
                // ✅ Render clickable status bar
                renderStatusBar(graphics, yPos, alpha);
                
                if (showKeybinds) {
                    renderKeybindHints(graphics, yPos + 12, alpha);
                }
            } catch (Exception ignored) {}
        } finally {
            PerformanceMonitor.end("RecipeOverlay.render");
        }
    }
    
    // ✅ NEW: Render search bar (always visible, click to activate)
    private static int renderSearchBar(GuiGraphics graphics, int yPos, int alpha) {
        try {
            Minecraft mc = Minecraft.getInstance();
            
            // Background (darker if active, lighter if inactive)
            int searchAlpha = Math.max(200, alpha);
            int bgColor = searchMode ? searchBgColor : 0xE0002050; // Blue if active, purple if inactive
            int finalSearchBg = applyAlpha(bgColor, searchAlpha);
            graphics.fill(posX + 4, yPos, posX + width - 4, yPos + 20, finalSearchBg);
            
            // Search icon
            String searchIcon = "🔍";
            graphics.drawString(mc.font, searchIcon, posX + 8, yPos + 6, 0xFFFFFFFF);
            
            // Search text or hint
            String displayText;
            int textColor;
            
            if (searchMode) {
                // Active: show current query or placeholder
                displayText = searchQuery.isEmpty() ? "Type to search..." : searchQuery;
                textColor = searchQuery.isEmpty() ? 0xFF888888 : 0xFFFFFFFF;
                
                // Cursor blink
                boolean showCursor = (System.currentTimeMillis() % 1000) < 500;
                if (showCursor && !searchQuery.isEmpty()) {
                    displayText += "_";
                }
            } else {
                // Inactive: show click hint
                displayText = "Click to search...";
                textColor = 0xFF888888;
            }
            
            graphics.drawString(mc.font, displayText, posX + 24, yPos + 6, textColor);
            
            return yPos + 20;
        } catch (Exception e) {
            return yPos + 20;
        }
    }
    
    // ✅ NEW: Render clickable status bar
    private static void renderStatusBar(GuiGraphics graphics, int yPos, int alpha) {
        try {
            Minecraft mc = Minecraft.getInstance();
            
            int textX = posX + 8;
            
            // ✅ Compact Mode Button (clickable)
            String modeText = compactMode ? "§a[Compact]" : "§7[Normal]";
            graphics.drawString(mc.font, modeText, textX, yPos, compactMode ? 0xFF00FF00 : 0xFF888888);
            int modeWidth = mc.font.width(compactMode ? "[Compact]" : "[Normal]");
            
            // Separator
            textX += modeWidth + 8;
            graphics.drawString(mc.font, "§7|", textX, yPos, 0xFF888888);
            textX += 8;
            
            // ✅ Sort Button (clickable)
            String sortText = "§b[Sort: " + sortMode.getDisplayName() + "]";
            graphics.drawString(mc.font, sortText, textX, yPos, 0xFF5599FF);
            int sortWidth = mc.font.width("[Sort: " + sortMode.getDisplayName() + "]");
            
            textX += sortWidth + 8;
            
            // ✅ Recipe Count (not clickable)
            String countText = "§7(" + filteredRecipes.size() + "/" + availableRecipes.size() + ")";
            graphics.drawString(mc.font, countText, textX, yPos, 0xFF888888);
            
        } catch (Exception ignored) {}
    }
    
    private static int getCachedAlpha() {
        long now = System.currentTimeMillis();
        
        if (now - lastAlphaCalc < ALPHA_CALC_INTERVAL) {
            return cachedAlpha;
        }
        
        if (!fadingIn) {
            cachedAlpha = 255;
        } else {
            long elapsed = now - fadeStartTime;
            if (elapsed >= 200) {
                fadingIn = false;
                cachedAlpha = 255;
            } else {
                float progress = elapsed / 200.0f;
                cachedAlpha = (int)(progress * 255);
            }
        }
        
        lastAlphaCalc = now;
        return cachedAlpha;
    }
    
    private static void renderBackground(GuiGraphics graphics, int yPos, int alpha) {
        try {
            int totalHeight = calculateTotalHeight();
            int bgAlpha = Math.max(220, alpha);
            
            int finalBgColor = applyAlpha(bgColor, bgAlpha);
            int finalBorderColor = applyAlpha(borderColor, alpha);
            
            graphics.fill(posX, yPos, posX + width, yPos + totalHeight, finalBgColor);
            
            graphics.fill(posX, yPos, posX + width, yPos + 2, finalBorderColor);
            graphics.fill(posX, yPos + totalHeight - 2, posX + width, yPos + totalHeight, finalBorderColor);
            graphics.fill(posX, yPos, posX + 2, yPos + totalHeight, finalBorderColor);
            graphics.fill(posX + width - 2, yPos, posX + width, yPos + totalHeight, finalBorderColor);
        } catch (Exception ignored) {}
    }
    
    private static int applyAlpha(int color, int alpha) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }
    
    private static int renderTitle(GuiGraphics graphics, int yPos, int alpha) {
        try {
            Minecraft mc = Minecraft.getInstance();
            String title = currentMachine.getName() + " Recipes";
            int titleColor = 0xFFFFFF00;
            
            graphics.drawCenteredString(mc.font, title, posX + width / 2 + 1, yPos + 1, 0xFF000000);
            graphics.drawCenteredString(mc.font, title, posX + width / 2, yPos, titleColor);
            
            return yPos + mc.font.lineHeight + spacing;
        } catch (Exception e) { 
            return yPos + 10; 
        }
    }
    
    private static int renderRecipeList(GuiGraphics graphics, int yPos, int alpha) {
        if (filteredRecipes == null || filteredRecipes.isEmpty()) {
            // Show "No results" message
            Minecraft mc = Minecraft.getInstance();
            String noResults = searchQuery.isEmpty() ? "No recipes available" : "No recipes match: " + searchQuery;
            graphics.drawCenteredString(mc.font, noResults, posX + width / 2, yPos + 20, 0xFFFF5555);
            return yPos + 40;
        }
        
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            List<ItemStack> inventory = (player != null) ? getCachedPlayerInventory(player) : new ArrayList<>();
            
            int visibleCount = Math.min(maxVisible, filteredRecipes.size() - scrollOffset);
            
            for (int i = 0; i < visibleCount; i++) {
                int recipeIndex = scrollOffset + i;
                if (recipeIndex >= filteredRecipes.size()) break;
                
                RecipeData recipe = filteredRecipes.get(recipeIndex);
                boolean isSelected = (recipeIndex == selectedIndex);
                
                yPos = renderRecipeEntry(graphics, yPos, recipe, isSelected, alpha, inventory);
                yPos += spacing;
            }
            
            return yPos;
        } catch (Exception e) { 
            return yPos; 
        }
    }
    
    private static List<ItemStack> getCachedPlayerInventory(LocalPlayer player) {
        long now = System.currentTimeMillis();
        
        if (cachedInventory != null && now - lastInventoryCache < INVENTORY_CACHE_DURATION) {
            return cachedInventory;
        }
        
        cachedInventory = new ArrayList<>();
        try {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    cachedInventory.add(stack);
                }
            }
        } catch (Exception e) {
            cachedInventory = new ArrayList<>();
        }
        
        lastInventoryCache = now;
        return cachedInventory;
    }
    
    private static int renderRecipeEntry(GuiGraphics graphics, int yPos, RecipeData recipe, 
                                        boolean isSelected, int alpha, List<ItemStack> inventory) {
        try {
            Minecraft mc = Minecraft.getInstance();
            
            int entryBg = isSelected ? applyAlpha(selectedBgColor, alpha) : applyAlpha(bgColor, alpha);
            graphics.fill(posX + 4, yPos, posX + width - 4, yPos + entryHeight, entryBg);
            
            int textX = posX + 8;
            int textY = yPos + 4;
            
            String displayName = recipe.getDisplayString();
            
            // ✅ Highlight search matches
            if (!searchQuery.isEmpty() && displayName.toLowerCase().contains(searchQuery.toLowerCase())) {
                displayName = highlightMatch(displayName, searchQuery);
            }
            
            int nameColor = isSelected ? 0xFFFFFF00 : 0xFFFFFFFF;
            graphics.drawString(mc.font, displayName, textX, textY, nameColor);
            textY += mc.font.lineHeight;
            
            // ✅ Compact mode: Skip input details
            if (!compactMode && showInputCount) {
                Map<String, Integer> inputs = recipe.getGroupedInputs();
                StringBuilder inputStr = new StringBuilder();
                int count = 0;
                
                for (Map.Entry<String, Integer> entry : inputs.entrySet()) {
                    if (count > 0) inputStr.append(" + ");
                    inputStr.append(formatItemName(entry.getKey()));
                    if (entry.getValue() > 1) {
                        inputStr.append(" x").append(entry.getValue());
                    }
                    count++;
                    if (count >= 3 && inputs.size() > 3) { 
                        inputStr.append("..."); 
                        break; 
                    }
                }
                
                int grayColor = isSelected ? 0xFFAAAAAA : 0xFF888888;
                graphics.drawString(mc.font, inputStr.toString(), textX + 10, textY, grayColor);
                textY += mc.font.lineHeight;
            }
            
            if (showCompletion) {
                RecipeHandler.RecipeSummary summary = new RecipeHandler.RecipeSummary(inventory, recipe.getInputs());
                float completion = summary.getCompletionPercentage();
                
                int completionColor = completion >= 1.0f ? 0xFF00FF00 : 0xFFFF5555;
                String completionText = String.format("%.0f%%", completion * 100);
                
                // ✅ Compact mode: Show on same line as name
                if (compactMode) {
                    int textWidth = mc.font.width(displayName);
                    graphics.drawString(mc.font, completionText, textX + textWidth + 8, yPos + 4, completionColor);
                } else {
                    graphics.drawString(mc.font, completionText, textX + 10, textY, completionColor);
                }
            }
            
            return yPos + entryHeight;
        } catch (Exception e) { 
            return yPos + entryHeight; 
        }
    }
    
    // ✅ NEW: Highlight matching text
    private static String highlightMatch(String text, String query) {
        // Simple highlight - wrap match with color codes
        int index = text.toLowerCase().indexOf(query.toLowerCase());
        if (index >= 0) {
            String before = text.substring(0, index);
            String match = text.substring(index, index + query.length());
            String after = text.substring(index + query.length());
            return before + "§e" + match + "§r" + after;
        }
        return text;
    }
    
    private static void renderKeybindHints(GuiGraphics graphics, int yPos, int alpha) {
        try {
            Minecraft mc = Minecraft.getInstance();
            int textColor = 0xFFFFFFFF;
            
            // ✅ NEW: Simplified hints (click on UI elements)
            String hints = searchMode ? 
                "[ESC] Exit Search  [Backspace] Clear" :
                "[↑↓] Navigate  [Enter] Select  [C] Compact  [S] Sort";
            
            graphics.drawCenteredString(mc.font, hints, posX + width / 2 + 1, yPos + 1, 0xFF000000);
            graphics.drawCenteredString(mc.font, hints, posX + width / 2, yPos, textColor);
        } catch (Exception ignored) {}
    }
    
    private static int calculateTotalHeight() {
        try {
            Minecraft mc = Minecraft.getInstance();
            int height = padding * 2;
            height += mc.font.lineHeight + spacing;
            
            // ✅ Add status bar + keybinds
            height += 12; // Status bar
            
            if (showKeybinds) {
                height += mc.font.lineHeight + spacing;
            }
            
            return Math.min(height, maxHeight);
        } catch (Exception e) { 
            return maxHeight; 
        }
    }
    
    private static final Map<String, String> formattedNames = new HashMap<>();
    
    private static String formatItemName(String itemId) {
        if (itemId == null) return "Unknown";
        
        String cached = formattedNames.get(itemId);
        if (cached != null) return cached;
        
        String formatted = itemId.replace("minecraft:", "")
                                 .replace("_", " ")
                                 .toLowerCase();
        
        String[] words = formatted.split(" ");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (result.length() > 0) result.append(" ");
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
            }
        }
        
        String finalName = result.toString();
        
        if (formattedNames.size() < 1000) {
            formattedNames.put(itemId, finalName);
        }
        
        return finalName;
    }
    
    public static void moveUp() {
        if (filteredRecipes == null || filteredRecipes.isEmpty()) return;
        selectedIndex--;
        if (selectedIndex < 0) {
            selectedIndex = filteredRecipes.size() - 1;
        }
        updateScrollOffset();
    }
    
    public static void moveDown() {
        if (filteredRecipes == null || filteredRecipes.isEmpty()) return;
        selectedIndex++;
        if (selectedIndex >= filteredRecipes.size()) {
            selectedIndex = 0;
        }
        updateScrollOffset();
    }
    
    private static void updateScrollOffset() {
        if (filteredRecipes == null || filteredRecipes.isEmpty()) { 
            scrollOffset = 0; 
            return; 
        }
        
        int recipeCount = filteredRecipes.size();
        selectedIndex = Math.max(0, Math.min(selectedIndex, recipeCount - 1));
        
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + maxVisible) {
            scrollOffset = selectedIndex - maxVisible + 1;
        }
        
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, recipeCount - maxVisible)));
    }
    
    public static void selectCurrent() {
        if (filteredRecipes == null || filteredRecipes.isEmpty()) return;
        
        if (selectedIndex < 0 || selectedIndex >= filteredRecipes.size()) { 
            selectedIndex = 0; 
            return; 
        }
        
        RecipeData selected = filteredRecipes.get(selectedIndex);
        UnifiedAutomationManager.setSelectedRecipe(selected.getRecipeId());
        
        try {
            Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[Slimefun] Selected: §f" + selected.getDisplayString()), 
                true
            );
        } catch(Exception ignored) {}
        
        hide();
    }
    
    // Getters
    public static boolean isVisible() { return overlayVisible; }
    public static int getSelectedIndex() { return selectedIndex; }
    public static List<RecipeData> getAvailableRecipes() { 
        return availableRecipes == null ? new ArrayList<>() : new ArrayList<>(availableRecipes); 
    }
    public static SlimefunMachineData getCurrentMachine() { return currentMachine; }
    public static int getScrollOffset() { return scrollOffset; }
    public static int getPosX() { return posX; }
    public static int getPosY() { return posY; }
    public static int getEntryHeight() { return entryHeight; }
    
    // ✅ NEW: Getters for new features
    public static boolean isSearchMode() { return searchMode; }
    public static boolean isCompactMode() { return compactMode; }
    public static String getSearchQuery() { return searchQuery; }
    public static SortMode getSortMode() { return sortMode; }
    
    // ✅ NEW: Click detection methods for UI buttons
    public static boolean isClickInSearchBar(double mouseX, double mouseY) {
        if (!overlayVisible) return false;
        
        // Search bar area (below title, before recipe list)
        int searchY = posY + padding + 9 + spacing + spacing;
        int searchHeight = 20;
        
        return mouseX >= posX + 4 && mouseX <= posX + width - 4 &&
               mouseY >= searchY && mouseY <= searchY + searchHeight;
    }
    
    public static boolean isClickInCompactButton(double mouseX, double mouseY) {
        if (!overlayVisible) return false;
        
        // Compact button (bottom left of status bar)
        int statusY = calculateStatusBarY();
        
        Minecraft mc = Minecraft.getInstance();
        String compactText = compactMode ? "Compact" : "Normal";
        int textWidth = mc.font.width(compactText);
        
        return mouseX >= posX + 8 && mouseX <= posX + 8 + textWidth &&
               mouseY >= statusY && mouseY <= statusY + 9;
    }
    
    public static boolean isClickInSortButton(double mouseX, double mouseY) {
        if (!overlayVisible) return false;
        
        // Sort button (middle of status bar)
        int statusY = calculateStatusBarY();
        
        Minecraft mc = Minecraft.getInstance();
        String sortText = "Sort: " + sortMode.getDisplayName();
        int compactWidth = mc.font.width(compactMode ? "Compact" : "Normal");
        int sortX = posX + 8 + compactWidth + 20; // After compact text
        int sortWidth = mc.font.width(sortText);
        
        return mouseX >= sortX && mouseX <= sortX + sortWidth &&
               mouseY >= statusY && mouseY <= statusY + 9;
    }
    
    private static int calculateStatusBarY() {
        try {
            Minecraft mc = Minecraft.getInstance();
            int yPos = posY + padding;
            yPos += mc.font.lineHeight + spacing;
            
            if (searchMode) {
                yPos += 20 + spacing;
            }
            
            yPos += spacing;
            
            int visibleRecipes = Math.min(maxVisible, filteredRecipes.size());
            yPos += visibleRecipes * (entryHeight + spacing);
            
            return yPos;
        } catch (Exception e) {
            return posY + 200;
        }
    }
}