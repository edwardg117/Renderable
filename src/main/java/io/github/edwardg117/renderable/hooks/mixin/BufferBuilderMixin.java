package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.BufferBuilder;
import io.github.edwardg117.renderable.hooks.Probe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Counts vertices for the innermost timed key (spec §4.3). Every {@code addVertex} overload calls the private
 * {@code beginVertex()} exactly once per vertex, and a return-value modifier on a primitive allocates nothing.
 */
@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin {

    @ModifyReturnValue(method = "beginVertex", at = @At("RETURN"))
    private long renderable$countVertex(long original) {
        if (Probe.ACTIVE) Probe.vertex();
        return original;
    }
}
