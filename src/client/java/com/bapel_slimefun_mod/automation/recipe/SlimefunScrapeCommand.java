package com.bapel_slimefun_mod.automation.recipe;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Pseudo-command /sfscrape yang dijalankan di sisi client untuk memulai
 * proses scanning resep guide Slimefun secara otomatis.
 */
public final class SlimefunScrapeCommand {

    private SlimefunScrapeCommand() {}

    public static void register() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (command == null) return true;
            String trimmed = command.trim();
            if (trimmed.equalsIgnoreCase("sfscrape")) {
                Minecraft.getInstance().execute(SlimefunGuideScraper::startScraping);
                return false; // batalkan pengiriman ke server
            }
            return true;
        });
    }
}
