package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Keeps NeoForge's stage event out of the {@code terrain} bucket (plan 6a, deviation 4). Vanilla's
 * {@code renderSectionLayer} and Sodium's overwrite of it both end by posting the layer's RenderLevelStageEvent
 * through this method, inside {@code LevelRendererMixin}'s terrain call. While it runs, the terrain call is closed,
 * so the event's handlers stay named time and whatever they draw keeps its own bucket; then it is opened again. The
 * selector carries the descriptor because the method has an overload that takes a stage.
 */
@Mixin(ClientHooks.class)
public abstract class ClientHooksMixin {

    @WrapMethod(method = "dispatchRenderStage(Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/LevelRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V")
    private static void renderable$leaveTerrain(RenderType renderType, LevelRenderer levelRenderer,
                                                Matrix4f modelViewMatrix, Matrix4f projectionMatrix, int renderTick,
                                                Camera camera, Frustum frustum, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(renderType, levelRenderer, modelViewMatrix, projectionMatrix, renderTick, camera, frustum);
            return;
        }
        boolean paused = Probe.pauseBucket(Bucket.TERRAIN);
        try {
            original.call(renderType, levelRenderer, modelViewMatrix, projectionMatrix, renderTick, camera, frustum);
        } finally {
            if (paused) Probe.resumeBucket(Bucket.TERRAIN);
        }
    }
}
