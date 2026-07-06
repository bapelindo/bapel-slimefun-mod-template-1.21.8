// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║  FILE 7 — AbstractContainerScreenAccessor.java  (BARU — Mixin Accessor)    ║
// ║  src/client/java/com/bapel_slimefun_mod/mixin/                             ║
// ╚══════════════════════════════════════════════════════════════════════════════╝
package com.bapel_slimefun_mod.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the protected layout fields of {@link AbstractContainerScreen} so that
 * {@link com.bapel_slimefun_mod.automation.FastMachineGuiButtons} can correctly
 * position custom widgets relative to the vanilla container panel.
 *
 * <p>Since Minecraft 26.1 ships unobfuscated jars, these field names
 * ({@code leftPos}, {@code topPos}, {@code imageWidth}, {@code imageHeight}) are
 * the actual Mojang source names present directly in the game jar — no mapping
 * layer or remap step is involved.
 *
 * @author bapelindo
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int bapel$getLeftPos();

    @Accessor("topPos")
    int bapel$getTopPos();

    @Accessor("imageWidth")
    int bapel$getImageWidth();

    @Accessor("imageHeight")
    int bapel$getImageHeight();
}