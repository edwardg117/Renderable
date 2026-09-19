package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Times every particle render and tick by particle class (spec §4.2). The render call lives in NeoForge's
 * five-argument {@code render} overload, which the three-argument vanilla signature delegates to.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @WrapOperation(
            method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"))
    private void renderable$timeParticleRender(Particle particle, VertexConsumer consumer, Camera camera, float partialTick,
                                               Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(particle, consumer, camera, partialTick);
            return;
        }
        Probe.beginParticleRender(particle);
        try {
            original.call(particle, consumer, camera, partialTick);
        } finally {
            Probe.end();
        }
    }

    @WrapOperation(
            method = "tickParticle",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;tick()V"))
    private void renderable$timeParticleTick(Particle particle, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(particle);
            return;
        }
        Probe.beginParticleTick(particle);
        try {
            original.call(particle);
        } finally {
            Probe.end();
        }
    }
}
