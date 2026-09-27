package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Times a passenger's own tick (spec addendum §P5.A8). The client ticks a rider from its vehicle through
 * {@code tickPassenger}, never through {@code Level.guardEntityTick}, so {@code LevelMixin} never sees it.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @WrapOperation(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
    private void renderable$timeRiderTick(Entity rider, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(rider);
            return;
        }
        Probe.beginRiderTick(rider);
        try {
            original.call(rider);
        } finally {
            Probe.end();
        }
    }
}
