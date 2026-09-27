package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.neoforged.neoforge.client.gui.GuiLayerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Times each HUD layer (spec addendum §P5.A4). {@code renderInner} loops over its named layers; the loop's
 * {@code NamedLayer} local, the only one of that type, gives the layer's id.
 */
@Mixin(GuiLayerManager.class)
public abstract class GuiLayerManagerMixin {

    @WrapOperation(method = "renderInner", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/LayeredDraw$Layer;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
    private void renderable$timeLayer(LayeredDraw.Layer layer, GuiGraphics guiGraphics, DeltaTracker deltaTracker,
                                      Operation<Void> original, @Local GuiLayerManager.NamedLayer named) {
        if (!Probe.ACTIVE) {
            original.call(layer, guiGraphics, deltaTracker);
            return;
        }
        boolean timed = Probe.beginLayer(named.name());
        try {
            original.call(layer, guiGraphics, deltaTracker);
        } finally {
            if (timed) Probe.end();
        }
    }
}
