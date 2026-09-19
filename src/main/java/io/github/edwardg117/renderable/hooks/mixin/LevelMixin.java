package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Consumer;

/** Times every client-side entity tick (spec §4.2). {@code guardEntityTick} is the one funnel vanilla uses. */
@Mixin(Level.class)
public abstract class LevelMixin {

    @Shadow
    @Final
    public boolean isClientSide;

    @WrapMethod(method = "guardEntityTick")
    private void renderable$timeEntityTick(Consumer<?> consumer, Entity entity, Operation<Void> original) {
        if (!Probe.ACTIVE || !isClientSide) {
            original.call(consumer, entity);
            return;
        }
        Probe.beginEntityTick(entity);
        try {
            original.call(consumer, entity);
        } finally {
            Probe.end();
        }
    }
}
