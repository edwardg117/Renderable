package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Hook;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The HUD bucket (spec addendum §P5.A1): every layer, vanilla and modded, and the HUD's own events.
 * {@code GameRenderer.render} calls {@code Gui.render} once a frame; screens are drawn separately.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @WrapMethod(method = "render")
    private void renderable$timeHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(guiGraphics, deltaTracker);
            return;
        }
        Probe.beginBucket(Bucket.HUD, Hook.HUD);
        try {
            original.call(guiGraphics, deltaTracker);
        } finally {
            Probe.endBucket();
        }
    }
}
