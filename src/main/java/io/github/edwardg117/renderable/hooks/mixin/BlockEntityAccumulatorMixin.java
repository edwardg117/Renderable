package io.github.edwardg117.renderable.hooks.mixin;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.hooks.AccumulatorHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * One reference field so {@link io.github.edwardg117.renderable.hooks.Probe} can cache a block entity's
 * accumulator on the block entity (spec addendum §P4.B2). No injector, no behaviour change: the field is
 * null for every block entity until one is rendered or ticked while a session is sampling.
 *
 * <p>{@code Probe} casts every rendered block entity to {@link AccumulatorHolder} with no
 * {@code instanceof} check, so this mixin must stay in the required config: moving it to {@code require = 0}
 * turns a silent non-application into a {@code ClassCastException} on the render thread instead of a
 * load-time error.
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityAccumulatorMixin implements AccumulatorHolder {

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
