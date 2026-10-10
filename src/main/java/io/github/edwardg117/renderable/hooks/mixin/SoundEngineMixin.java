package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Times each sound play (spec addendum §P6.5): the whole of {@code SoundEngine.play}, which posts NeoForge's
 * PlaySoundEvent, checks the volume and waits for the sound thread to hand over a channel. A play that returns early
 * costs almost nothing and still counts. Attribution-only: the bucket the play runs in keeps its time, and the Mods
 * tab names it {@code sound <event> (<category>)}.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    @WrapMethod(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V")
    private void renderable$timeSoundPlay(SoundInstance sound, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(sound);
            return;
        }
        boolean timed = Probe.beginSound(sound);
        try {
            original.call(sound);
        } finally {
            if (timed) Probe.end();
        }
    }
}
