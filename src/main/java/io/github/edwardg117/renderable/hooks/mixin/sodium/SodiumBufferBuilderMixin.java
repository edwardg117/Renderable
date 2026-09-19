package io.github.edwardg117.renderable.hooks.mixin.sodium;

import com.mojang.blaze3d.vertex.BufferBuilder;
import io.github.edwardg117.renderable.hooks.Probe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Spec §4.3 under Sodium: Sodium's BufferBuilderMixin adds {@code push(MemoryStack, long, int, VertexFormat)}
 * and routes entity, block entity, particle, item and font vertices through it without touching
 * {@code beginVertex()}. Priority 1100 so this applies after Sodium's mixin has merged the method. Modifying
 * the {@code int count} argument passes a primitive through with no callback object, so the idle path is one
 * volatile read.
 */
@Mixin(value = BufferBuilder.class, priority = 1100)
public abstract class SodiumBufferBuilderMixin {

    @ModifyVariable(
            method = "push(Lorg/lwjgl/system/MemoryStack;JILcom/mojang/blaze3d/vertex/VertexFormat;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false, require = 0)
    private int renderable$countPushedVertices(int count) {
        if (Probe.ACTIVE) Probe.vertices(count);
        return count;
    }
}
