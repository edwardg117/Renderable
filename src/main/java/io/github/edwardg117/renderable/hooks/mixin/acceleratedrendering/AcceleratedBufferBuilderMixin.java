package io.github.edwardg117.renderable.hooks.mixin.acceleratedrendering;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.edwardg117.renderable.hooks.Probe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts the vertices Accelerated Rendering writes, so the renderers it takes over stop reading zero
 * (spec addendum §P4.A3). The target is named by string because the mod is an alpha that is on no Maven,
 * so there is no compile-time dependency; every injector is {@code require = 0}, and a rename that only an
 * injector's target moves degrades this column to the zero it already showed. {@code require} governs
 * injectors only, though: the shadowed {@link #getTotalVertexCount()} has no such guard, and a rename of it
 * is an apply-time {@code InvalidMixinException} that takes out all five hooks, not just the mesh pair. What
 * keeps that from being fatal is {@code "required": false} on {@code renderable.thirdparty.mixins.json},
 * which makes Mixin disable the mixin and log instead of crashing — this class must never move into the
 * required vanilla config, where that same rename would become a crash instead.
 *
 * <p>The two {@code addVertex} overloads each add one vertex, {@code addClientMesh}'s second parameter is
 * its vertex count, and {@code addServerMesh} adds {@code mesh.size()} to one of two counters depending on
 * whether the mesh is uploaded immediately, which the delta of {@code getTotalVertexCount()} covers either
 * way. {@code addVertex(PoseStack$Pose, float, float, float)} writes no counter and delegates, so hooking
 * it as well would double count.
 *
 * <p>The two {@code addVertex} hooks avoid {@code @Inject} because its callback object would land once per
 * vertex; {@code addServerMesh}'s pair can afford one because it runs once per mesh instead.
 *
 * <p>The mesh pair holds one baseline in a field, so it assumes {@code addServerMesh} does not re-enter
 * itself. It does not: the method writes buffers and returns, and calls nothing that renders. Were that ever
 * to change, an inner call would overwrite the baseline and the outer {@code RETURN} would count the inner
 * mesh's vertices a second time — an over-count, which {@link Probe#acceleratedVertices(int)}'s
 * {@code n <= 0} guard does **not** catch, since the delta stays positive. That guard is there for a
 * different case: a delta of zero when Accelerated Rendering defers the upload. If this mod ever gains a
 * nested mesh path, this pair needs a depth counter, not a bigger comment.
 *
 * <p>The other way a delta could inflate — the baseline going stale because {@link Probe#ACTIVE} flips true
 * between {@code HEAD} and {@code RETURN}, so a whole cumulative count is reported as one mesh — is closed
 * outright by the {@code renderable$meshBefore < 0} sentinel below: {@code RETURN} can only fire against a
 * baseline its own {@code HEAD} established.
 */
@Mixin(targets = "com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.AcceleratedBufferBuilder",
        remap = false)
public abstract class AcceleratedBufferBuilderMixin {

    @Shadow
    public abstract int getTotalVertexCount();

    /** Vertex total before the current addServerMesh call, or -1 when sampling was not active at HEAD. */
    @Unique
    private int renderable$meshBefore;

    @ModifyReturnValue(method = "addVertex(FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At("RETURN"), require = 0)
    private VertexConsumer renderable$countSlowVertex(VertexConsumer original) {
        if (Probe.ACTIVE) Probe.acceleratedVertex();
        return original;
    }

    @ModifyVariable(method = "addVertex(FFFIFFIIFFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float renderable$countFastVertex(float x) {
        if (Probe.ACTIVE) Probe.acceleratedVertex();
        return x;
    }

    @ModifyVariable(method = "addClientMesh", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int renderable$countClientMesh(int vertexCount) {
        if (Probe.ACTIVE) Probe.acceleratedVertices(vertexCount);
        return vertexCount;
    }

    @Inject(method = "addServerMesh", at = @At("HEAD"), require = 0)
    private void renderable$serverMeshBefore(CallbackInfo ci) {
        renderable$meshBefore = Probe.ACTIVE ? getTotalVertexCount() : -1;
    }

    @Inject(method = "addServerMesh", at = @At("RETURN"), require = 0)
    private void renderable$serverMeshAfter(CallbackInfo ci) {
        if (renderable$meshBefore >= 0) Probe.acceleratedVertices(getTotalVertexCount() - renderable$meshBefore);
    }
}
