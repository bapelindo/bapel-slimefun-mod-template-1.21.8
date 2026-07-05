package com.bapel_slimefun_mod.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Minecraft 26.1 replaced {@code GuiGraphics#drawString}/{@code drawCenteredString} with a
 * single {@code GuiGraphicsExtractor#text(...)} method and dropped the centered variant entirely.
 * This keeps the old call shape available in one place instead of hand-centering at every call site.
 */
public final class TextDrawing {
    private TextDrawing() {
    }

    public static void drawString(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        graphics.text(font, text, x, y, color, true);
    }

    public static void drawString(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
        graphics.text(font, text, x, y, color, true);
    }

    public static void drawCenteredString(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        graphics.text(font, text, x - font.width(text) / 2, y, color, true);
    }

    public static void drawCenteredString(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
        graphics.text(font, text, x - font.width(text) / 2, y, color, true);
    }
}
