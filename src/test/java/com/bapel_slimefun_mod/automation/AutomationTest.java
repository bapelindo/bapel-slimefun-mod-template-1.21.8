package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.automation.fastmachine.FastMachineDetector;
import com.bapel_slimefun_mod.automation.fastmachine.FastMachineAutomationHandler;
import com.bapel_slimefun_mod.automation.fastmachine.FastMachineRecipeMemory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class AutomationTest {

    @BeforeAll
    public static void setup() {
        if (!RecipeDatabase.isInitialized()) {
            RecipeDatabase.initialize();
        }
    }

    @Test
    public void testTitleMatching() {
        assertTrue(FastMachineDetector.isFastMachine("[Server] §eFast Enhanced Crafting Table §7(Lv 2)"));
        assertEquals("FAST_ENHANCED_CRAFTING_TABLE", FastMachineDetector.getMachineId("[Server] §eFast Enhanced Crafting Table §7(Lv 2)"));

        assertTrue(FastMachineDetector.isFastMachine("Fast Compressor - LV 3"));
        assertEquals("FAST_COMPRESSOR", FastMachineDetector.getMachineId("Fast Compressor - LV 3"));

        assertTrue(FastMachineDetector.isFastMachine("§6Fast Smeltery §7(Active)"));
        assertEquals("FAST_SMELTERY", FastMachineDetector.getMachineId("§6Fast Smeltery §7(Active)"));

        assertFalse(FastMachineDetector.isFastMachine("Regular Chest"));
    }

    @Test
    public void testRecipeLookup() {
        List<RecipeData> recipes1 = RecipeDatabase.searchRecipesByOutput("Small Capacitor");
        assertNotNull(recipes1);
        assertFalse(recipes1.isEmpty(), "Recipes for Small Capacitor should not be empty");

        List<RecipeData> recipes2 = RecipeDatabase.searchRecipesByOutput("Portable Dustbin");
        assertNotNull(recipes2);
        assertFalse(recipes2.isEmpty(), "Recipes for Portable Dustbin should not be empty");

        List<RecipeData> recipes3 = RecipeDatabase.searchRecipesByOutput("Gold Dust");
        assertNotNull(recipes3);
        assertFalse(recipes3.isEmpty(), "Recipes for Gold Dust should not be empty");
    }

    @Test
    public void testPrefixBypass() {
        String m1 = FastMachineAutomationHandler.getFastMachineIdFromSlimefun("ENHANCED_CRAFTING_TABLE");
        assertEquals("FAST_ENHANCED_CRAFTING_TABLE", m1);

        String m2 = FastMachineAutomationHandler.getFastMachineIdFromSlimefun("FAST_ENHANCED_CRAFTING_TABLE");
        assertEquals("FAST_ENHANCED_CRAFTING_TABLE", m2);
    }

    @Test
    public void testColorStripping() {
        assertEquals("Fast Machine", AutomationUtils.stripColorCodes("§e§lFast Machine"));
        assertEquals("Fast Machine", AutomationUtils.stripColorCodes("§E§LFast Machine"));
    }

    @Test
    public void testCircularStack() {
        java.util.Deque<com.bapel_slimefun_mod.automation.CraftingJob> chain = new java.util.ArrayDeque<>();
        chain.push(new com.bapel_slimefun_mod.automation.CraftingJob("slimefun:PORTABLE_DUSTBIN", net.minecraft.world.item.ItemStack.EMPTY, 1, 0));
        chain.push(new com.bapel_slimefun_mod.automation.CraftingJob("slimefun:COPPER_WIRE", net.minecraft.world.item.ItemStack.EMPTY, 1, 1));

        boolean isCircularNormal = false;
        for (com.bapel_slimefun_mod.automation.CraftingJob existing : chain) {
            if (existing.getItemKey().equalsIgnoreCase("slimefun:SILICON")) {
                isCircularNormal = true;
                break;
            }
        }
        assertFalse(isCircularNormal);

        boolean isCircularFake = false;
        for (com.bapel_slimefun_mod.automation.CraftingJob existing : chain) {
            if (existing.getItemKey().equalsIgnoreCase("slimefun:PORTABLE_DUSTBIN")) {
                isCircularFake = true;
                break;
            }
        }
        assertTrue(isCircularFake);
    }

    @Test
    public void testPlayerReachRange() {
        net.minecraft.core.BlockPos machinePos = new net.minecraft.core.BlockPos(100, 64, 100);
        
        // Within 6 blocks reach (squared distance <= 36.0)
        net.minecraft.world.phys.Vec3 playerWithin = new net.minecraft.world.phys.Vec3(103.0, 64.0, 100.0);
        double distWithin = machinePos.distToCenterSqr(playerWithin);
        assertTrue(distWithin <= 36.0, "Player should be within interaction reach (dist = " + distWithin + ")");

        // Beyond 6 blocks reach (squared distance > 36.0)
        net.minecraft.world.phys.Vec3 playerBeyond = new net.minecraft.world.phys.Vec3(107.0, 64.0, 100.0);
        double distBeyond = machinePos.distToCenterSqr(playerBeyond);
        assertTrue(distBeyond > 36.0, "Player should be out of interaction reach (dist = " + distBeyond + ")");
    }

    @Test
    public void testRecipeDatabaseSpacingTolerance() {
        // Player types "Gold Dust" with spaces
        List<RecipeData> r1 = RecipeDatabase.searchRecipesByOutput("Gold Dust");
        assertFalse(r1.isEmpty());

        // Player types "gold_dust" with underscores
        List<RecipeData> r2 = RecipeDatabase.searchRecipesByOutput("gold_dust");
        assertFalse(r2.isEmpty());

        // Player types "GOLD   DUST" with extra spaces
        List<RecipeData> r3 = RecipeDatabase.searchRecipesByOutput("GOLD   DUST");
        assertFalse(r3.isEmpty());
    }

    @Test
    public void testCachedMachinePrioritization() {
        // Cache coordinates for FAST_ENHANCED_CRAFTING_TABLE
        net.minecraft.core.BlockPos tablePos = new net.minecraft.core.BlockPos(10, 64, 10);
        FastMachineRecipeMemory.cachePosition("FAST_ENHANCED_CRAFTING_TABLE", tablePos);

        // Do not cache coordinates for FAST_MAGIC_WORKBENCH
        // Verify getClosestPosition behaves correctly
        net.minecraft.world.phys.Vec3 playerPos = new net.minecraft.world.phys.Vec3(11.0, 64.0, 10.0);
        net.minecraft.core.BlockPos closestTable = FastMachineRecipeMemory.getClosestPosition("FAST_ENHANCED_CRAFTING_TABLE", playerPos, null);
        assertNotNull(closestTable);
        assertEquals(tablePos, closestTable);

        net.minecraft.core.BlockPos closestWorkbench = FastMachineRecipeMemory.getClosestPosition("FAST_MAGIC_WORKBENCH", playerPos, null);
        assertNull(closestWorkbench);
    }
}
