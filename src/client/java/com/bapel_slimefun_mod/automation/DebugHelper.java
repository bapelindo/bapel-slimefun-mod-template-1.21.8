package com.bapel_slimefun_mod.automation;

import com.bapel_slimefun_mod.BapelSlimefunMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Debug helper for testing and troubleshooting
 */
public class DebugHelper {
    
    /**
     * Print current machine info
     */
    public static void printCurrentMachineInfo() {
        SlimefunMachineData machine = MachineAutomationHandler.getCurrentMachine();
        
        if (machine == null) {
            BapelSlimefunMod.LOGGER.info("=== No Machine Active ===");
            return;
        }
        
        BapelSlimefunMod.LOGGER.info("=== Machine Info ===");
        BapelSlimefunMod.LOGGER.info("ID: {}", machine.getId());
        BapelSlimefunMod.LOGGER.info("Name: {}", machine.getName());
        BapelSlimefunMod.LOGGER.info("Title: {}", machine.getInventoryTitle());
        BapelSlimefunMod.LOGGER.info("Energy: {}/{}", machine.getEnergyConsumption(), machine.getEnergyCapacity());
        BapelSlimefunMod.LOGGER.info("Input Slots: {}", java.util.Arrays.toString(machine.getInputSlots()));
        BapelSlimefunMod.LOGGER.info("Output Slots: {}", java.util.Arrays.toString(machine.getOutputSlots()));
        BapelSlimefunMod.LOGGER.info("Recipe: {}", machine.getRecipe());
        BapelSlimefunMod.LOGGER.info("===================");
    }
    
    /**
     * Print container slot info
     */
    public static void printContainerSlots() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;
        
        BapelSlimefunMod.LOGGER.info("=== Container Slots ({} total) ===", menu.slots.size());
        
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            
            if (!stack.isEmpty()) {
                String itemId = AutomationUtils.getItemId(stack);
                boolean isPlayerSlot = slot.container == player.getInventory();
                
                BapelSlimefunMod.LOGGER.info("Slot {}: {} x{} [{}]", 
                    i, itemId, stack.getCount(), isPlayerSlot ? "PLAYER" : "MACHINE");
            }
        }
        
        BapelSlimefunMod.LOGGER.info("========================");
    }
    
    /**
     * Print recipe requirements vs available items
     */
    public static void printRecipeStatus() {
        Map<String, Integer> requirements = MachineAutomationHandler.getCachedRecipeRequirements();
        
        if (requirements.isEmpty()) {
            BapelSlimefunMod.LOGGER.info("=== No Recipe ===");
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        
        List<ItemStack> inventory = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                inventory.add(stack);
            }
        }
        
        BapelSlimefunMod.LOGGER.info("=== Recipe Status ===");
        
        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            String itemId = entry.getKey();
            int required = entry.getValue();
            int available = RecipeHandler.countItemInInventory(inventory, itemId);
            
            String status = available >= required ? "✓" : "✗";
            BapelSlimefunMod.LOGGER.info("{} {}: {}/{}", status, itemId, available, required);
        }
        
        RecipeHandler.RecipeSummary summary = MachineAutomationHandler.getRecipeSummary();
        if (summary != null) {
            BapelSlimefunMod.LOGGER.info("Completion: {:.1f}%", summary.getCompletionPercentage() * 100);
            BapelSlimefunMod.LOGGER.info("Can Craft: {} (max: {})", 
                summary.canCraft() ? "YES" : "NO", summary.getMaxCrafts());
        }
        
        BapelSlimefunMod.LOGGER.info("=====================");
    }
    
    /**
     * Print automation status
     */
    public static void printAutomationStatus() {
        BapelSlimefunMod.LOGGER.info("=== Automation Status ===");
        BapelSlimefunMod.LOGGER.info("Enabled: {}", MachineAutomationHandler.isAutomationEnabled());
        BapelSlimefunMod.LOGGER.info("Active: {}", MachineAutomationHandler.isActive());
        BapelSlimefunMod.LOGGER.info("Current Machine: {}", 
            MachineAutomationHandler.getCurrentMachine() != null ? 
            MachineAutomationHandler.getCurrentMachine().getName() : "None");
        BapelSlimefunMod.LOGGER.info("========================");
    }
    
    /**
     * Print all loaded machines
     */
    public static void printLoadedMachines() {
        Map<String, SlimefunMachineData> machines = SlimefunDataLoader.getAllMachines();
        
        BapelSlimefunMod.LOGGER.info("=== Loaded Machines ({}) ===", machines.size());
        
        int count = 0;
        for (Map.Entry<String, SlimefunMachineData> entry : machines.entrySet()) {
            SlimefunMachineData machine = entry.getValue();
            BapelSlimefunMod.LOGGER.info("{}. {} ({})", 
                ++count, 
                machine.getName(), 
                machine.getId());
            
            if (count >= 10) {
                BapelSlimefunMod.LOGGER.info("... and {} more", machines.size() - 10);
                break;
            }
        }
        
        BapelSlimefunMod.LOGGER.info("=========================");
    }
    
    /**
     * Print input slot contents
     */
    public static void printInputSlots() {
        SlimefunMachineData machine = MachineAutomationHandler.getCurrentMachine();
        if (machine == null || !machine.hasInputSlots()) {
            BapelSlimefunMod.LOGGER.info("=== No Input Slots ===");
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;
        
        BapelSlimefunMod.LOGGER.info("=== Input Slots ===");
        
        for (int slotIndex : machine.getInputSlots()) {
            if (slotIndex >= menu.slots.size()) continue;
            
            Slot slot = menu.slots.get(slotIndex);
            ItemStack stack = slot.getItem();
            
            if (stack.isEmpty()) {
                BapelSlimefunMod.LOGGER.info("Slot {}: EMPTY", slotIndex);
            } else {
                String itemId = AutomationUtils.getItemId(stack);
                BapelSlimefunMod.LOGGER.info("Slot {}: {} x{}", slotIndex, itemId, stack.getCount());
            }
        }
        
        BapelSlimefunMod.LOGGER.info("===================");
    }
    
    /**
     * Run full diagnostic
     */
    public static void runFullDiagnostic() {
        BapelSlimefunMod.LOGGER.info("");
        BapelSlimefunMod.LOGGER.info("╔════════════════════════════════════╗");
        BapelSlimefunMod.LOGGER.info("║  SLIMEFUN AUTOMATION DIAGNOSTIC    ║");
        BapelSlimefunMod.LOGGER.info("╚════════════════════════════════════╝");
        BapelSlimefunMod.LOGGER.info("");
        
        printAutomationStatus();
        BapelSlimefunMod.LOGGER.info("");
        
        printCurrentMachineInfo();
        BapelSlimefunMod.LOGGER.info("");
        
        printRecipeStatus();
        BapelSlimefunMod.LOGGER.info("");
        
        printInputSlots();
        BapelSlimefunMod.LOGGER.info("");
        
        printContainerSlots();
        BapelSlimefunMod.LOGGER.info("");
        
        BapelSlimefunMod.LOGGER.info("╔════════════════════════════════════╗");
        BapelSlimefunMod.LOGGER.info("║     DIAGNOSTIC COMPLETE            ║");
        BapelSlimefunMod.LOGGER.info("╚════════════════════════════════════╝");
        BapelSlimefunMod.LOGGER.info("");
    }
}