package io.github.edwardg117.renderable.hooks.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The {@code render} field {@link ChunkBuilderMeshingTaskMixin} needs is declared on the generic superclass
 * {@code ChunkBuilderTask<OUTPUT>}, not on {@code ChunkBuilderMeshingTask} itself: a {@code @Shadow} on the
 * subclass mixin fails at runtime ({@code InvalidMixinException}: "@Shadow field render was not located in
 * the target class"), so this accessor targets the declaring class instead. {@code ChunkBuilderMeshingTask}
 * extends {@code ChunkBuilderTask}, so its instances implement this interface too.
 */
@Mixin(value = ChunkBuilderTask.class, remap = false)
public interface ChunkBuilderTaskAccessor {

    @Accessor("render")
    RenderSection renderable$render();
}
