package io.github.edwardg117.renderable.hooks.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.hooks.Hook;
import io.github.edwardg117.renderable.hooks.Probe;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Spec §4.3: chunk upload and section bookkeeping on the render thread, as the CHUNK_UPLOAD bucket. updateChunks
 * can run pending meshing jobs on the render thread, so mesh time and this bucket are not disjoint.
 */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class RenderSectionManagerMixin {

    @WrapMethod(method = "uploadChunks", remap = false, require = 0)
    private void renderable$timeUpload(Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call();
            return;
        }
        Probe.beginBucket(Bucket.CHUNK_UPLOAD, Hook.SODIUM_UPLOAD);
        try {
            original.call();
        } finally {
            Probe.endBucket();
        }
    }

    @WrapMethod(method = "updateChunks", remap = false, require = 0)
    private void renderable$timeUpdate(boolean updateImmediately, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(updateImmediately);
            return;
        }
        Probe.beginBucket(Bucket.CHUNK_UPLOAD, Hook.SODIUM_UPLOAD);
        try {
            original.call(updateImmediately);
        } finally {
            Probe.endBucket();
        }
    }
}
