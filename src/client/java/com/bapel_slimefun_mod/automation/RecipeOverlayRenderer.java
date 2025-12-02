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
 * FIXED VERSION - Semua bug sudah diperbaiki
 * Bug fixes:
 * 1. ✅ NullPointerException saat hide() - menggunakan new ArrayList() bukan clear()
 * 2. ✅ ArrayIndexOutOfBoundsException - validasi index sebelum akses
 * 3. ✅ ConcurrentModificationException - menggunakan copy list saat iterate
 * 4. ✅ Memory leak - proper cleanup di hide()
 * 5. ✅ Toggle tidak responsive - debounce mechanism
 * 6. ✅ Scroll offset tidak sync - fixed updateScrollOffset()
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
    
    // Debounce untuk toggle
    private static long lastToggleTime = 0;
    private static final long TOGGLE_COOLDOWN = 250; // 250ms cooldown
    
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
        
        try {
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
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error parsing config, using defaults", e);
            setDefaultConfig();
        }
    }
    
    /**
     * Show the overlay for a specific machine
     * FIX: Validasi yang lebih ketat
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
            currentMachine = null; // Reset jika tidak ada recipes
            return;
        }
        
        // Reset state dengan aman
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
     * Menyiapkan data resep tanpa menampilkan overlay (untuk Auto-Load)
     */
    public static void prepareData(SlimefunMachineData machine) {
        if (machine == null) return;
        
        currentMachine = machine;
        loadRecipesForMachine(machine);
        
        // Kita TIDAK mengubah overlayVisible menjadi true di sini
        // agar overlay tetap tersembunyi saat auto-load
    }
    
    /**
     * Hide the overlay
     * FIX: Proper cleanup tanpa NullPointerException
     */
    public static void hide() {
        if (!overlayVisible) {
            return; // Already hidden
        }
        
        // Set flag dulu
        overlayVisible = false;
        
        // Clear references dengan aman
        currentMachine = null;
        selectedIndex = 0;
        scrollOffset = 0;
        
        // FIX: Buat list baru, jangan clear yang existing
        availableRecipes = new ArrayList<>();
        
        BapelSlimefunMod.LOGGER.info("Recipe overlay hidden");
    }
    
    /**
     * Toggle overlay visibility
     * FIX: Debounce mechanism untuk mencegah toggle spam
     */
    public static void toggle() {
        long now = System.currentTimeMillis();
        
        // Debounce check
        if (now - lastToggleTime < TOGGLE_COOLDOWN) {
            BapelSlimefunMod.LOGGER.debug("Toggle cooldown active, ignoring");
            return;
        }
        lastToggleTime = now;
        
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
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(message), true);
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Failed to send player message", e);
        }
    }
    
    /**
     * Load all recipes for the given machine
     * FIX: Buat list baru, avoid concurrent modification
     */
    private static void loadRecipesForMachine(SlimefunMachineData machine) {
        // FIX: Buat list baru dari awal
        List<RecipeData> newRecipes = new ArrayList<>();
        
        try {
            // Try to load from database first
            if (RecipeDatabase.isInitialized() && RecipeDatabase.hasMachineRecipes(machine.getId())) {
                // FIX: Copy ke list baru untuk avoid concurrent modification
                List<RecipeData> dbRecipes = RecipeDatabase.getRecipesForMachine(machine.getId());
                newRecipes.addAll(dbRecipes);
                
                BapelSlimefunMod.LOGGER.info("Loaded {} recipes from database for {}", 
                    newRecipes.size(), machine.getName());
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
                    
                    newRecipes.add(recipe);
                    BapelSlimefunMod.LOGGER.info("Created fallback recipe for {}", machine.getName());
                } else {
                    BapelSlimefunMod.LOGGER.warn("No recipes found for machine: {}", machine.getName());
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error loading recipes for machine", e);
        }
        
        // FIX: Replace dengan list baru yang sudah di-populate
        availableRecipes = newRecipes;
    }
    
    /**
     * Main render method - called every frame
     * FIX: Validasi state sebelum render
     */
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
            
            // --- GANTI renderTitle JADI renderHeader DI SINI ---
            yPos = renderHeader(graphics, yPos, alpha);
            // --------------------------------------------------
            
            yPos += spacing;
            yPos = renderRecipeList(graphics, yPos, alpha);

            yPos += spacing;
            renderAutomationStatus(graphics, yPos, alpha);
            
            if (showKeybinds) {
                yPos += spacing;
                renderKeybindHints(graphics, yPos, alpha);
            }
        } catch (Exception e) {
            hide();
        }
    }
    

    private static void renderAutomationStatus(GuiGraphics graphics, int yPos, int alpha) {
        Minecraft mc = Minecraft.getInstance();
        int labelColor = getColor("normalText", alpha);
        int valueColor = getColor("availableText", alpha); // Hijau
        int warningColor = 0xFFFF5555; // Merah
        
        int textX = posX + padding;
        
        // Baris 1: Status Automation
        String statusText = MachineAutomationHandler.isAutomationEnabled() ? "ENABLED" : "DISABLED";
        int statusColor = MachineAutomationHandler.isAutomationEnabled() ? valueColor : warningColor;
        
        graphics.drawString(mc.font, "Auto: ", textX, yPos, labelColor);
        graphics.drawString(mc.font, statusText, textX + 30, yPos, statusColor);
        
        // Baris 2: Mode (Farming / Crafting Massal)
        yPos += mc.font.lineHeight + 2;
        String modeText = MachineAutomationHandler.getCurrentMode().getDisplayName();
        
        graphics.drawString(mc.font, "Mode: ", textX, yPos, labelColor);
        graphics.drawString(mc.font, modeText, textX + 30, yPos, 0xFF55FFFF); // Cyan color for mode
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
        try {
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
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error rendering background", e);
        }
    }
    
    /**
     * Render title
     */
// Method baru untuk menggambar Judul + Tombol Mode
    private static int renderHeader(GuiGraphics graphics, int yPos, int alpha) {
        try {
            Minecraft mc = Minecraft.getInstance();
            int textColor = getColor("titleText", alpha);
            
            // 1. Gambar Judul Mesin (Kiri)
            String title = currentMachine != null ? currentMachine.getName() : "Recipes";
            graphics.drawString(mc.font, title, posX + padding, yPos, textColor);
            
            // 2. Gambar Tombol Mode (Kanan)
            boolean isMemory = MachineAutomationHandler.getConfig().isRememberLastRecipe();
            String modeText = "Mode: " + (isMemory ? "§aAuto" : "§cManual");
            
            int modeWidth = mc.font.width(modeText);
            int modeX = posX + width - padding - modeWidth;
            
            // Background tombol (abu-abu gelap)
            int btnColor = (alpha << 24) | 0x444444; 
            graphics.fill(modeX - 2, yPos - 2, modeX + modeWidth + 2, yPos + mc.font.lineHeight + 2, btnColor);
            
            graphics.drawString(mc.font, modeText, modeX, yPos, 0xFFFFFF);
            
            return yPos + mc.font.lineHeight + spacing;
        } catch (Exception e) { return yPos; }
    }
 
    
    // --- HELPER BARU: FORMAT NAMA ITEM ---
    private static String formatItemName(String itemId) {
        if (itemId == null || itemId.isEmpty()) return "Unknown";
        String[] words = itemId.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() > 0) sb.append(" ");
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }
    /**
     * Render the list of recipes
     * FIX: Validasi bounds untuk mencegah ArrayIndexOutOfBoundsException
     */
    private static int renderRecipeList(GuiGraphics graphics, int startY, int alpha) {
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return startY;
            
            List<ItemStack> inventory = getPlayerInventory(player);
            int yPos = startY;
            
            // FIX: Validasi bounds dengan ketat
            int recipeCount = availableRecipes.size();
            if (recipeCount == 0) return yPos;
            
            int visibleStart = Math.max(0, Math.min(scrollOffset, recipeCount - 1));
            int visibleEnd = Math.min(visibleStart + maxVisible, recipeCount);
            
            // FIX: Iterate dengan copy untuk avoid concurrent modification
            List<RecipeData> recipesToRender = new ArrayList<>(availableRecipes.subList(visibleStart, visibleEnd));
            
            for (int i = 0; i < recipesToRender.size(); i++) {
                int actualIndex = visibleStart + i;
                RecipeData recipe = recipesToRender.get(i);
                boolean isSelected = (actualIndex == selectedIndex);
                
                yPos = renderRecipeEntry(graphics, recipe, actualIndex, yPos, alpha, isSelected, inventory);
                yPos += spacing;
            }
            
            return yPos;
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error rendering recipe list", e);
            return startY;
        }
    }
    
    /**
     * Render a single recipe entry
     */
private static int renderRecipeEntry(GuiGraphics graphics, RecipeData recipe, int index,
                                        int yPos, int alpha, boolean isSelected, 
                                        List<ItemStack> inventory) {
        try {
            Minecraft mc = Minecraft.getInstance();
            
            // Highlight baris yang dipilih
            if (isSelected) {
                int highlightColor = getColor("selectedBackground", alpha);
                graphics.fill(posX + 2, yPos, posX + width - 2, yPos + entryHeight, highlightColor);
            }
            
            int textX = posX + padding;
            int textY = yPos + 4;
            int textColor = getColor("normalText", alpha);
            
            // --- BARIS 1: NAMA OUTPUT (HASIL) ---
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
            } else {
                line1.append("Unknown Output");
            }
            
            graphics.drawString(mc.font, line1.toString(), textX, textY, textColor);
            textY += mc.font.lineHeight + 2;
            
            // --- BARIS 2: NAMA INPUT (BAHAN) ---
            // LOGIKA BARU: Menampilkan nama item input
            if (showInputCount) {
                Map<String, Integer> inputs = recipe.getGroupedInputs();
                StringBuilder inputStr = new StringBuilder();
                int i = 0;
                
                // Loop semua input dan format namanya
                for (Map.Entry<String, Integer> entry : inputs.entrySet()) {
                    if (i > 0) inputStr.append(" + ");
                    
                    inputStr.append(formatItemName(entry.getKey())); // Panggil helper baru
                    if (entry.getValue() > 1) {
                        inputStr.append(" x").append(entry.getValue());
                    }
                    
                    i++;
                    // Batasi tampilan max 3 bahan agar tidak kepanjangan
                    if (i >= 3 && inputs.size() > 3) {
                        inputStr.append("...");
                        break;
                    }
                }

                // Render teks input dengan warna lebih redup (alpha/2)
                graphics.drawString(mc.font, inputStr.toString(), textX + 10, textY, 
                    getColor("normalText", alpha / 2));
                textY += mc.font.lineHeight;
            }
            
            // --- INFO LAINNYA (Persentase) ---
            if (showCompletion) {
                RecipeHandler.RecipeSummary summary = new RecipeHandler.RecipeSummary(inventory, recipe.getInputs());
                float completion = summary.getCompletionPercentage();
                int completionColor = completion >= 1.0f ? getColor("availableText", alpha) : getColor("missingText", alpha);
                String completionText = String.format("%.0f%%", completion * 100);
                graphics.drawString(mc.font, completionText, textX + 10, textY, completionColor);
            }
            
            return yPos + entryHeight;
        } catch (Exception e) {
            return yPos + entryHeight;
        }
    }
    
    /**
     * Render keybind hints
     */
    private static void renderKeybindHints(GuiGraphics graphics, int yPos, int alpha) {
        try {
            Minecraft mc = Minecraft.getInstance();
            int textColor = getColor("normalText", alpha / 2);
            
            String hints = "[↑↓] Navigate  [Enter] Select  [R/Esc] Close";
            graphics.drawCenteredString(mc.font, hints, posX + width / 2, yPos, textColor);
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error rendering keybind hints", e);
        }
    }
    
    /**
     * Calculate total height needed for overlay
     */
    private static int calculateTotalHeight() {
        try {
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
        } catch (Exception e) {
            return maxHeight;
        }
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
        
        try {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            }
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error getting player inventory", e);
        }
        
        return stacks;
    }
    
    // ===== Navigation Methods =====
    
    /**
     * Move selection up
     * FIX: Validasi bounds dengan ketat
     */
    public static void moveUp() {
        if (availableRecipes == null || availableRecipes.isEmpty()) return;
        
        try {
            selectedIndex--;
            if (selectedIndex < 0) {
                selectedIndex = availableRecipes.size() - 1;
            }
            
            updateScrollOffset();
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error in moveUp", e);
            selectedIndex = 0;
        }
    }
    
    /**
     * Move selection down
     * FIX: Validasi bounds dengan ketat
     */
    public static void moveDown() {
        if (availableRecipes == null || availableRecipes.isEmpty()) return;
        
        try {
            selectedIndex++;
            if (selectedIndex >= availableRecipes.size()) {
                selectedIndex = 0;
            }
            
            updateScrollOffset();
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error in moveDown", e);
            selectedIndex = 0;
        }
    }
    
    /**
     * Update scroll offset to keep selected item visible
     * FIX: Proper bounds checking
     */
    private static void updateScrollOffset() {
        try {
            if (availableRecipes == null || availableRecipes.isEmpty()) {
                scrollOffset = 0;
                return;
            }
            
            int recipeCount = availableRecipes.size();
            
            // Clamp selectedIndex
            selectedIndex = Math.max(0, Math.min(selectedIndex, recipeCount - 1));
            
            // Update scroll offset
            if (selectedIndex < scrollOffset) {
                scrollOffset = selectedIndex;
            } else if (selectedIndex >= scrollOffset + maxVisible) {
                scrollOffset = selectedIndex - maxVisible + 1;
            }
            
            // Clamp scrollOffset
            scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, recipeCount - maxVisible)));
            
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error updating scroll offset", e);
            scrollOffset = 0;
        }
    }
    
    /**
     * Select current recipe
     * FIX: Validasi bounds sebelum select
     */
    public static void selectCurrent() {
        if (availableRecipes == null || availableRecipes.isEmpty()) {
            BapelSlimefunMod.LOGGER.warn("No recipes to select");
            return;
        }
        
        try {
            // FIX: Validasi index
            if (selectedIndex < 0 || selectedIndex >= availableRecipes.size()) {
                BapelSlimefunMod.LOGGER.warn("Invalid selected index: {}", selectedIndex);
                selectedIndex = 0;
                return;
            }
            
            RecipeData selected = availableRecipes.get(selectedIndex);
            MachineAutomationHandler.setSelectedRecipe(selected.getRecipeId());
            
            BapelSlimefunMod.LOGGER.info("Selected recipe: {}", selected.getDisplayString());
            sendPlayerMessage("§a[Slimefun] Selected: §f" + selected.getDisplayString());
            hide();
        } catch (Exception e) {
            BapelSlimefunMod.LOGGER.error("Error selecting recipe", e);
        }
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
     * FIX: Return defensive copy
     */
    public static List<RecipeData> getAvailableRecipes() {
        if (availableRecipes == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(availableRecipes);
    }
    
    /**
     * Get current machine
     */
    public static SlimefunMachineData getCurrentMachine() {
        return currentMachine;
    }

    public static int getScrollOffset() { return scrollOffset; }
    public static int getPosX() { return posX; }
    public static int getPosY() { return posY; }
    public static int getEntryHeight() { return entryHeight; }
}