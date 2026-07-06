package com.bapel_slimefun_mod.automation.fastmachine;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Intercepts the client-only pseudo-command {@code /fmtarget <amount>} — typed by
 * the player via the {@code Alt+J} keybind prompt — and
 * routes it to {@link FastMachineAutomationHandler#setTargetCount(int)},
 * <b>without ever transmitting it to the server</b>.
 */
public final class FastMachineChatCommandInterceptor {

    private FastMachineChatCommandInterceptor() {}

    /** Command name without the leading slash — matches how ALLOW_COMMAND delivers it. */
    private static final String COMMAND_NAME = "fmtarget";

    /**
     * Registers the interceptor against Fabric's client command-send event.
     */
    public static void register() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(FastMachineChatCommandInterceptor::onCommand);
    }

    private static boolean onCommand(String command) {
        if (command == null) return true;
        String trimmed = command.trim();
        if (!trimmed.equalsIgnoreCase(COMMAND_NAME) && !trimmed.toLowerCase().startsWith(COMMAND_NAME + " ")) {
            return true; // not our command — let vanilla/other mods handle it
        }

        Minecraft mc = Minecraft.getInstance();

        if (!FastMachineAutomationHandler.isActive()) {
            notify(mc, "§c✗ No FastMachine GUI is currently open.");
            return false;
        }

        String arg = trimmed.length() > COMMAND_NAME.length()
                ? trimmed.substring(COMMAND_NAME.length()).trim()
                : "";

        if (arg.isEmpty()) {
            notify(mc, "§e⚠ Usage: §f/fmtarget <amount> §7or §f/fmtarget clear");
            return false;
        }

        if (arg.equalsIgnoreCase("clear")) {
            FastMachineAutomationHandler.clearTargetCount();
            return false;
        }

        try {
            int amount = Integer.parseInt(arg.replaceAll("[,._]", ""));
            FastMachineAutomationHandler.setTargetCount(amount);
        } catch (NumberFormatException e) {
            notify(mc, "§c✗ Invalid number: §f" + arg);
        }

        return false; // always cancel — this is a client-only pseudo-command
    }

    private static void notify(Minecraft mc, String msg) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(msg));
        }
    }
}
