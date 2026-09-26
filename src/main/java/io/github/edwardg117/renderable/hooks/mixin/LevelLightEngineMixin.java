package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Hook;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Times the client's light updates (spec §4.2). The same base method runs on the server's light thread in
 * singleplayer; {@code Probe.beginBucket} ignores calls off the render thread.
 */
@Mixin(LevelLightEngine.class)
public abstract class LevelLightEngineMixin {

    @WrapMethod(method = "runLightUpdates")
    private int renderable$timeLightUpdates(Operation<Integer> original) {
        if (!Probe.ACTIVE) {
            return original.call();
        }
        Probe.beginBucket(Bucket.LIGHT, Hook.LIGHT);
        try {
            return original.call();
        } finally {
            Probe.endBucket();
        }
    }
}
