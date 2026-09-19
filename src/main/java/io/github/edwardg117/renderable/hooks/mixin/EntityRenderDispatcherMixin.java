package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** Times every entity render call (spec §4.1). {@code render} is the only method of that name. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @WrapMethod(method = "render")
    private void renderable$timeRender(Entity entity, double x, double y, double z, float rotationYaw,
                                       float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                                       int packedLight, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(entity, x, y, z, rotationYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }
        Probe.beginEntity(entity);
        try {
            original.call(entity, x, y, z, rotationYaw, partialTick, poseStack, bufferSource, packedLight);
        } finally {
            Probe.end();
        }
    }
}
