package io.github.edwardg117.renderable.hooks.mixin.distanthorizons;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Hook;
import io.github.edwardg117.renderable.hooks.Probe;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The {@code lod} bucket (spec addendum §P6.3). Distant Horizons draws its LODs from a head injection in
 * {@code LevelRenderer.renderSectionLayer}, which calls one of these four methods by the layer it is given: the main
 * pass, the deferred pass for shaders and the two fades. No DH class is needed to compile: the target is named, and
 * every method wrapped takes nothing and returns nothing. Applied only when the distanthorizons mod is loaded (package
 * gate in RenderableMixinPlugin). Each injector is require = 0 and its selector carries the descriptor, so a renamed or
 * re-signed method leaves the hook under "did not apply" instead of failing the game.
 */
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ClientApi", remap = false)
public abstract class ClientApiMixin {

    @WrapMethod(method = "renderLods()V", remap = false, require = 0)
    private void renderable$timeRenderLods(Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call();
            return;
        }
        Probe.beginBucket(Bucket.LOD, Hook.LOD);
        try {
            original.call();
        } finally {
            Probe.endBucket();
        }
    }

    @WrapMethod(method = "renderDeferredLodsForShaders()V", remap = false, require = 0)
    private void renderable$timeDeferredLods(Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call();
            return;
        }
        Probe.beginBucket(Bucket.LOD, Hook.LOD);
        try {
            original.call();
        } finally {
            Probe.endBucket();
        }
    }

    @WrapMethod(method = "renderFadeOpaque()V", remap = false, require = 0)
    private void renderable$timeFadeOpaque(Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call();
            return;
        }
        Probe.beginBucket(Bucket.LOD, Hook.LOD);
        try {
            original.call();
        } finally {
            Probe.endBucket();
        }
    }

    @WrapMethod(method = "renderFadeTransparent()V", remap = false, require = 0)
    private void renderable$timeFadeTransparent(Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call();
            return;
        }
        Probe.beginBucket(Bucket.LOD, Hook.LOD);
        try {
            original.call();
        } finally {
            Probe.endBucket();
        }
    }
}
