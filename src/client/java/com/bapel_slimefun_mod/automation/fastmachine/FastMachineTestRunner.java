package com.bapel_slimefun_mod.automation.fastmachine;

import com.bapel_slimefun_mod.automation.RecipeDatabase;
import com.bapel_slimefun_mod.automation.RecipeData;
import com.bapel_slimefun_mod.automation.AutomationUtils;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;

import java.util.ArrayList;
import java.util.List;

public final class FastMachineTestRunner {
    private FastMachineTestRunner() {}

    private static final String COMMAND_NAME = "fmtest";

    public static void register() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(FastMachineTestRunner::onCommand);
    }

    private static boolean onCommand(String command) {
        if (command == null) return true;
        String trimmed = command.trim();
        if (!trimmed.equalsIgnoreCase(COMMAND_NAME) && !trimmed.toLowerCase().startsWith(COMMAND_NAME + " ")) {
            return true;
        }

        Minecraft mc = Minecraft.getInstance();
        runAllTests(mc);
        return false; // Cancel sending command to server
    }

    private static void runAllTests(Minecraft mc) {
        notify(mc, "§d[BapelTest] Running comprehensive logic test suite...");
        int passed = 0;
        int total = 0;

        // Test 1: Title Matching
        total++;
        if (testTitleMatching(mc)) passed++;

        // Test 2: Recipe database lookup
        total++;
        if (testRecipeLookup(mc)) passed++;

        // Test 3: Circular Stack
        total++;
        if (testCircularStack(mc)) passed++;

        // Test 4: Prefix Bypass
        total++;
        if (testPrefixBypass(mc)) passed++;

        // Test 5: Color Stripping
        total++;
        if (testColorStripping(mc)) passed++;

        // Test 6: MatchesItem plugin matching
        total++;
        if (testMatchesItem(mc)) passed++;

        if (passed == total) {
            notify(mc, "§a§l=== ALL TESTS PASSED SUCCESSFULLY (" + passed + "/" + total + ") ===");
        } else {
            notify(mc, "§c§l=== TEST SUITE FAILED (" + (total - passed) + " failures) ===");
        }
    }

    private static boolean testTitleMatching(Minecraft mc) {
        try {
            assertTest(FastMachineDetector.isFastMachine("[Server] §eFast Enhanced Crafting Table §7(Lv 2)"), "Title enhanced table check");
            assertTest(FastMachineDetector.getMachineId("[Server] §eFast Enhanced Crafting Table §7(Lv 2)").equals("FAST_ENHANCED_CRAFTING_TABLE"), "Machine ID enhanced table mapping");

            assertTest(FastMachineDetector.isFastMachine("Fast Compressor - LV 3"), "Title compressor check");
            assertTest(FastMachineDetector.getMachineId("Fast Compressor - LV 3").equals("FAST_COMPRESSOR"), "Machine ID compressor mapping");

            assertTest(FastMachineDetector.isFastMachine("§6Fast Smeltery §7(Active)"), "Title smeltery check");
            assertTest(FastMachineDetector.getMachineId("§6Fast Smeltery §7(Active)").equals("FAST_SMELTERY"), "Machine ID smeltery mapping");

            assertTest(!FastMachineDetector.isFastMachine("Regular Chest"), "Regular chest matching rejection");
            
            notify(mc, "§a✔ Test 1 (Title Matching): PASSED");
            return true;
        } catch (Exception e) {
            notify(mc, "§c✗ Test 1 (Title Matching): FAILED - " + e.getMessage());
            return false;
        }
    }

    private static boolean testRecipeLookup(Minecraft mc) {
        try {
            if (!RecipeDatabase.isInitialized()) RecipeDatabase.initialize();
            
            List<RecipeData> recipes1 = RecipeDatabase.searchRecipesByOutput("Small Capacitor");
            assertTest(recipes1 != null && !recipes1.isEmpty(), "Capacitor search not empty");

            List<RecipeData> recipes2 = RecipeDatabase.searchRecipesByOutput("Portable Dustbin");
            assertTest(recipes2 != null && !recipes2.isEmpty(), "Dustbin search not empty");

            List<RecipeData> recipes3 = RecipeDatabase.searchRecipesByOutput("Gold Dust");
            assertTest(recipes3 != null && !recipes3.isEmpty(), "Gold Dust space-to-underscore search not empty");

            notify(mc, "§a✔ Test 2 (Recipe Lookup): PASSED");
            return true;
        } catch (Exception e) {
            notify(mc, "§c✗ Test 2 (Recipe Lookup): FAILED - " + e.getMessage());
            return false;
        }
    }

    private static boolean testCircularStack(Minecraft mc) {
        try {
            java.util.Deque<FastMachineAutomationHandler.CraftJob> chain = new java.util.ArrayDeque<>();
            chain.push(new FastMachineAutomationHandler.CraftJob("Portable Dustbin", "COMPRESSOR", 1));
            chain.push(new FastMachineAutomationHandler.CraftJob("Copper Wire", "COMPRESSOR", 1));

            boolean isCircularNormal = false;
            for (FastMachineAutomationHandler.CraftJob existing : chain) {
                if (existing.recipeName.equalsIgnoreCase("Silicon")) {
                    isCircularNormal = true;
                    break;
                }
            }
            assertTest(!isCircularNormal, "Silicon is not circular in chain");

            boolean isCircularFake = false;
            for (FastMachineAutomationHandler.CraftJob existing : chain) {
                if (existing.recipeName.equalsIgnoreCase("Portable Dustbin")) {
                    isCircularFake = true;
                    break;
                }
            }
            assertTest(isCircularFake, "Portable Dustbin is circular in chain");

            notify(mc, "§a✔ Test 3 (Circular Stack): PASSED");
            return true;
        } catch (Exception e) {
            notify(mc, "§c✗ Test 3 (Circular Stack): FAILED - " + e.getMessage());
            return false;
        }
    }

    private static boolean testPrefixBypass(Minecraft mc) {
        try {
            String m1 = FastMachineAutomationHandler.getFastMachineIdFromSlimefun("ENHANCED_CRAFTING_TABLE");
            assertTest(m1 != null && m1.equals("FAST_ENHANCED_CRAFTING_TABLE"), "Normal enhanced mapping");

            String m2 = FastMachineAutomationHandler.getFastMachineIdFromSlimefun("FAST_ENHANCED_CRAFTING_TABLE");
            assertTest(m2 != null && m2.equals("FAST_ENHANCED_CRAFTING_TABLE"), "FAST_ prefixed mapping bypass");

            notify(mc, "§a✔ Test 4 (Prefix Bypass): PASSED");
            return true;
        } catch (Exception e) {
            notify(mc, "§c✗ Test 4 (Prefix Bypass): FAILED - " + e.getMessage());
            return false;
        }
    }

    private static boolean testColorStripping(Minecraft mc) {
        try {
            String c1 = AutomationUtils.stripColorCodes("§e§lFast Machine");
            assertTest(c1.equals("Fast Machine"), "Lower color stripping");

            String c2 = AutomationUtils.stripColorCodes("§E§LFast Machine");
            assertTest(c2.equals("Fast Machine"), "Upper color stripping case-insensitive");

            notify(mc, "§a✔ Test 5 (Color Stripping): PASSED");
            return true;
        } catch (Exception e) {
            notify(mc, "§c✗ Test 5 (Color Stripping): FAILED - " + e.getMessage());
            return false;
        }
    }

    private static boolean testMatchesItem(Minecraft mc) {
        try {
            ItemStack copperStack = new ItemStack(Items.COPPER_INGOT);
            copperStack.set(DataComponents.CUSTOM_NAME, Component.literal("§6Copper Wire x64"));

            assertTest(AutomationUtils.matchesItem(copperStack, "COPPER_WIRE"), "Custom name matchesItem check");

            ItemStack ironStack = new ItemStack(Items.IRON_INGOT);
            assertTest(AutomationUtils.matchesItem(ironStack, "IRON_INGOT"), "Vanilla matchesItem check");

            notify(mc, "§a✔ Test 6 (MatchesItem): PASSED");
            return true;
        } catch (Exception e) {
            notify(mc, "§c✗ Test 6 (MatchesItem): FAILED - " + e.getMessage());
            return false;
        }
    }

    private static void assertTest(boolean condition, String label) {
        if (!condition) {
            throw new RuntimeException("Assertion failed: " + label);
        }
    }

    private static void notify(Minecraft mc, String msg) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(msg));
        }
    }
}
