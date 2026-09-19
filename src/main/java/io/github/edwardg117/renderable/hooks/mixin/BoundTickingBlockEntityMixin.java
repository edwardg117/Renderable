package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Times every client-side block entity tick (spec §4.2). Server ticks in singleplayer pass straight through. */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BoundTickingBlockEntityMixin {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"))
    private void renderable$timeTick(BlockEntityTicker ticker, Level level, BlockPos pos, BlockState state, BlockEntity blockEntity,
                                     Operation<Void> original) {
        if (!Probe.ACTIVE || !level.isClientSide) {
            original.call(ticker, level, pos, state, blockEntity);
            return;
        }
        Probe.beginBlockEntityTick(blockEntity);
        try {
            original.call(ticker, level, pos, state, blockEntity);
        } finally {
            Probe.end();
        }
    }
}
