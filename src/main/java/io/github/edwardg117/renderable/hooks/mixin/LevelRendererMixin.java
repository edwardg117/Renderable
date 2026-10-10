package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Hook;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The {@code terrain} bucket (spec addendum §P6.3): the seven chunk-layer draws {@code renderLevel} makes, solid
 * through tripwire, one injector for all of them. Whatever supplies the body is inside it: Sodium overwrites
 * {@code renderSectionLayer}, and Distant Horizons' head injection draws its LODs there, which {@code lod} carves out
 * (spec addendum §P6.4). Iris's {@code @WrapWithCondition} on the same calls chains with this wrapper; its shadow pass
 * calls the method through an accessor, so shadow terrain stays outside. The stage event NeoForge posts at the end of
 * each layer is not terrain: {@code ClientHooksMixin} steps out of this call while it runs.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void renderable$timeTerrain(LevelRenderer renderer, RenderType renderType, double camX, double camY,
                                        double camZ, Matrix4f frustumMatrix, Matrix4f projectionMatrix,
                                        Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(renderer, renderType, camX, camY, camZ, frustumMatrix, projectionMatrix);
            return;
        }
        Probe.beginBucket(Bucket.TERRAIN, Hook.TERRAIN);
        try {
            original.call(renderer, renderType, camX, camY, camZ, frustumMatrix, projectionMatrix);
        } finally {
            Probe.endBucket();
        }
    }
}
