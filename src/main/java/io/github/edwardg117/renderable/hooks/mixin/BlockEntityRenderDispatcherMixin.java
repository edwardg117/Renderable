package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Times every block entity render call (spec §4.1). {@code render} is the only method of that name. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {

    @WrapMethod(method = "render")
    private void renderable$timeRender(BlockEntity blockEntity, float partialTick, PoseStack poseStack,
                                       MultiBufferSource bufferSource, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(blockEntity, partialTick, poseStack, bufferSource);
            return;
        }
        Probe.beginBlockEntity(blockEntity);
        try {
            original.call(blockEntity, partialTick, poseStack, bufferSource);
        } finally {
            Probe.end();
        }
    }
}
