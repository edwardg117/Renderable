package io.github.edwardg117.renderable.hooks.mixin;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.hooks.AccumulatorHolder;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * One reference field so {@link io.github.edwardg117.renderable.hooks.Probe} can cache an entity's
 * accumulator on the entity (spec addendum §P4.B2). No injector, no behaviour change.
 *
 * <p>{@code Probe} casts every rendered entity to {@link AccumulatorHolder} with no {@code instanceof}
 * check, so this mixin must stay in the required config: moving it to {@code require = 0} turns a silent
 * non-application into a {@code ClassCastException} on the render thread instead of a load-time error.
 */
@Mixin(Entity.class)
public abstract class EntityAccumulatorMixin implements AccumulatorHolder {

    @Unique
    private Accumulator renderable$accumulator;

    @Override
    public Accumulator renderable$accumulator() {
        return renderable$accumulator;
    }

    @Override
    public void renderable$accumulator(Accumulator accumulator) {
        this.renderable$accumulator = accumulator;
    }
}
