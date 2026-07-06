package com.bapel_slimefun_mod.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@code Screen#addRenderableWidget} is {@code protected}, so it can only be called
 * from inside a {@code Screen} subclass (or same package). {@code FastMachineGuiButtons}
 * lives in a completely different package and is not a Screen subclass, so it needs
 * this Mixin Invoker to call the method from the outside.
 *
 * <p>Don't forget to register this in your {@code *.mixins.json} (client mixins list),
 * e.g. {@code "mixin.ScreenWidgetInvoker"}.
 *
 * @author bapelindo
 */
@Mixin(Screen.class)
public interface ScreenWidgetInvoker {

    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T bapel$addRenderableWidget(T widget);

    @Invoker("removeWidget")
    void bapel$removeWidget(GuiEventListener widget);
}