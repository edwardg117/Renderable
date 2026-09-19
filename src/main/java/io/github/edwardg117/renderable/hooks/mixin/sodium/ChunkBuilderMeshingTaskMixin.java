package io.github.edwardg117.renderable.hooks.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.edwardg117.renderable.hooks.Probe;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Spec §4.3: times one section rebuild and every block and fluid rendered into it. Runs on Sodium's worker
 * threads, and on the render thread when {@code RenderSectionManager.updateChunks} steals a pending job (that
 * time then also sits inside the CHUNK_UPLOAD bucket); only the thread-safe mesh entry points of Probe are
 * used. A cancelled rebuild returns null and is not counted. Applied only when sodium is loaded.
 *
 * <p>The {@code render} field lives on the generic superclass {@code ChunkBuilderTask}, not on this mixin's
 * target: a {@code @Shadow} field here is rejected at runtime ({@code InvalidMixinException}: "@Shadow field
 * render was not located in the target class"), so it is read through {@link ChunkBuilderTaskAccessor}
 * instead, which mixes into {@code ChunkBuilderTask} directly.
 */
@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
public abstract class ChunkBuilderMeshingTaskMixin {

    private RenderSection renderable$section() {
        return ((ChunkBuilderTaskAccessor) (Object) this).renderable$render();
    }

    @WrapMethod(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            remap = false, require = 0)
    private ChunkBuildOutput renderable$timeSection(ChunkBuildContext context, CancellationToken token,
                                                    Operation<ChunkBuildOutput> original) {
        if (!Probe.ACTIVE) {
            return original.call(context, token);
        }
        long start = System.nanoTime();
        ChunkBuildOutput out = null;
        try {
            out = original.call(context, token);
            return out;
        } finally {
            if (out != null) {
                RenderSection render = renderable$section();
                Probe.meshSection(render.getChunkX(), render.getChunkY(), render.getChunkZ(), System.nanoTime() - start);
            }
        }
    }

    @WrapOperation(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;renderModel(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)V"),
            remap = false, require = 0)
    private void renderable$timeBlock(BlockRenderer renderer, BakedModel model, BlockState state, BlockPos pos, BlockPos origin,
                                      Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(renderer, model, state, pos, origin);
            return;
        }
        long start = System.nanoTime();
        try {
            original.call(renderer, model, state, pos, origin);
        } finally {
            Probe.meshBlock(state, System.nanoTime() - start);
        }
    }

    @WrapOperation(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/FluidRenderer;render(Lnet/caffeinemc/mods/sodium/client/world/LevelSlice;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/TranslucentGeometryCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildBuffers;)V"),
            remap = false, require = 0)
    private void renderable$timeFluid(FluidRenderer renderer, LevelSlice slice, BlockState state, FluidState fluid, BlockPos pos,
                                      BlockPos origin, TranslucentGeometryCollector collector, ChunkBuildBuffers buffers,
                                      Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(renderer, slice, state, fluid, pos, origin, collector, buffers);
            return;
        }
        long start = System.nanoTime();
        try {
            original.call(renderer, slice, state, fluid, pos, origin, collector, buffers);
        } finally {
            Probe.meshFluid(fluid, System.nanoTime() - start);
        }
    }
}
