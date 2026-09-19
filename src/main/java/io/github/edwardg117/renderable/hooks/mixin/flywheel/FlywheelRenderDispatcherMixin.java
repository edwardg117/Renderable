package io.github.edwardg117.renderable.hooks.mixin.flywheel;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.engine_room.flywheel.api.backend.RenderContext;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.hooks.Hook;
import io.github.edwardg117.renderable.hooks.Probe;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.BlockDestructionProgress;
import org.spongepowered.asm.mixin.Mixin;

import java.util.SortedSet;

/**
 * Spec §4.2 and §4.4: Flywheel's per-level render dispatcher is the only render-thread entry into its
 * instancing work, so its three calls per frame are summed into the FLYWHEEL bucket. Applied only when the
 * flywheel mod is loaded (package gate in RenderableMixinPlugin); require = 0 so a renamed method degrades to a
 * missing bucket instead of a crash.
 */
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl$RenderDispatcherImpl", remap = false)
public abstract class FlywheelRenderDispatcherMixin {

    @WrapMethod(method = "onStartLevelRender", remap = false, require = 0)
    private void renderable$timeStartLevelRender(RenderContext context, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(context);
            return;
        }
        Probe.beginBucket(Bucket.FLYWHEEL, Hook.FLYWHEEL);
        try {
            original.call(context);
        } finally {
            Probe.endBucket();
        }
    }

    @WrapMethod(method = "afterEntities", remap = false, require = 0)
    private void renderable$timeAfterEntities(RenderContext context, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(context);
            return;
        }
        Probe.beginBucket(Bucket.FLYWHEEL, Hook.FLYWHEEL);
        try {
            original.call(context);
        } finally {
            Probe.endBucket();
        }
    }

    @WrapMethod(method = "beforeCrumbling", remap = false, require = 0)
    private void renderable$timeCrumbling(RenderContext context, Long2ObjectMap<SortedSet<BlockDestructionProgress>> progress,
                                          Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(context, progress);
            return;
        }
        Probe.beginBucket(Bucket.FLYWHEEL, Hook.FLYWHEEL);
        try {
            original.call(context, progress);
        } finally {
            Probe.endBucket();
        }
    }
}
