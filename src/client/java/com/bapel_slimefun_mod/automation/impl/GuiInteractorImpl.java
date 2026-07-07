package com.bapel_slimefun_mod.automation.impl;

import com.bapel_slimefun_mod.automation.infra.GuiInteractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class GuiInteractorImpl implements GuiInteractor {

    private long lastRightClickTime = 0L;
    private BlockPos lastClickedPos = null;

    @Override
    public void rightClickBlock(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;

        long now = System.currentTimeMillis();
        if (pos.equals(lastClickedPos) && (now - lastRightClickTime < 1500)) {
            return;
        }

        lastClickedPos = pos;
        lastRightClickTime = now;

        boolean swapped = false;
        int savedSlot = getSelectedSlot(mc.player.getInventory());
        if (!mc.player.getMainHandItem().isEmpty()) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getItem(i).isEmpty()) {
                    setSelectedSlot(mc.player.getInventory(), i);
                    swapped = true;
                    break;
                }
            }
        }

        BlockHitResult hit = new BlockHitResult(
            new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
            Direction.UP, pos, false
        );
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);

        if (swapped) {
            setSelectedSlot(mc.player.getInventory(), savedSlot);
        }
    }

    @Override
    public void closeCurrentGui() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.closeContainer();
        }
    }

    @Override
    public boolean isGridGuiOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            String title = screen.getTitle().getString().toLowerCase();
            return title.contains("cargo") || title.contains("network") || title.contains("grid") || title.contains("terminal");
        }
        return false;
    }

    @Override
    public boolean isMachineGuiOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            String title = screen.getTitle().getString().toLowerCase();
            return title.contains("fast") || title.contains("machine") || title.contains("auto");
        }
        return false;
    }

    @Override
    public void shiftClickSlot(int slotIndex) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu == null) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        mc.gameMode.handleContainerInput(menu.containerId, slotIndex, 0, ContainerInput.QUICK_MOVE, mc.player);
    }

    @Override
    public int getActiveGuiSlotCount() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu != null) {
            return mc.player.containerMenu.slots.size();
        }
        return 0;
    }

    private int getSelectedSlot(net.minecraft.world.entity.player.Inventory inv) {
        try {
            java.lang.reflect.Field f = net.minecraft.world.entity.player.Inventory.class.getDeclaredField("selected");
            f.setAccessible(true);
            return f.getInt(inv);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field f = net.minecraft.world.entity.player.Inventory.class.getDeclaredField("selectedSlot");
                f.setAccessible(true);
                return f.getInt(inv);
            } catch (Exception e2) {
                return 0;
            }
        }
    }

    private void setSelectedSlot(net.minecraft.world.entity.player.Inventory inv, int slot) {
        try {
            java.lang.reflect.Field f = net.minecraft.world.entity.player.Inventory.class.getDeclaredField("selected");
            f.setAccessible(true);
            f.setInt(inv, slot);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field f = net.minecraft.world.entity.player.Inventory.class.getDeclaredField("selectedSlot");
                f.setAccessible(true);
                f.setInt(inv, slot);
            } catch (Exception e2) {}
        }
    }
}
