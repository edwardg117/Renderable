package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Hook;
import io.github.edwardg117.renderable.hooks.FrameBoundaries;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The frame loop's boundaries and two of its components (spec addendum §P5.A1 and §P5.A2). A frame opens just
 * before NeoForge posts RenderFrameEvent.Pre and its render window closes just after RenderFrameEvent.Post; a
 * tick opens just before ClientTickEvent.Pre and closes just after ClientTickEvent.Post. So every listener of
 * those events, at any priority, falls inside the window it belongs to. Inside the tick it times two buckets, the
 * sound engine's tick and the random block display effects (spec addendum §P6.3).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;fireRenderFramePre(Lnet/minecraft/client/DeltaTracker;)V"))
    private void renderable$frameStart(boolean renderLevel, CallbackInfo ci) {
        if (!Probe.ACTIVE) return;
        FrameBoundaries.renderPre();
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;fireRenderFramePost(Lnet/minecraft/client/DeltaTracker;)V",
            shift = At.Shift.AFTER))
    private void renderable$renderEnd(boolean renderLevel, CallbackInfo ci) {
        if (!Probe.ACTIVE) return;
        FrameBoundaries.renderPost();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;fireClientTickPre()V"))
    private void renderable$tickStart(CallbackInfo ci) {
        if (!Probe.ACTIVE) return;
        FrameBoundaries.tickPre();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;fireClientTickPost()V", shift = At.Shift.AFTER))
    private void renderable$tickEnd(CallbackInfo ci) {
        if (!Probe.ACTIVE) return;
        FrameBoundaries.tickPost();
    }

    @WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V"))
    private void renderable$timeTasks(Minecraft instance, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(instance);
            return;
        }
        Probe.beginTasks();
        try {
            original.call(instance);
        } finally {
            Probe.endTasks();
        }
    }

    @WrapOperation(method = "runTick", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"))
    private void renderable$timeFrameCap(int fps, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(fps);
            return;
        }
        Probe.beginFrameCap();
        try {
            original.call(fps);
        } finally {
            Probe.endFrameCap();
        }
    }

    /**
     * The {@code sound} bucket (spec addendum §P6.3): updating the playing sounds and playing the ones
     * {@code playDelayed} queued. Called every tick, paused or not.
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;tick(Z)V"))
    private void renderable$timeSound(SoundManager soundManager, boolean paused, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(soundManager, paused);
            return;
        }
        Probe.beginBucket(Bucket.SOUND, Hook.SOUND);
        try {
            original.call(soundManager, paused);
        } finally {
            Probe.endBucket();
        }
    }

    /** The {@code animate tick} bucket: drips, smoke and sparkles around the player, and the particles they spawn. */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;animateTick(III)V"))
    private void renderable$timeAnimateTick(ClientLevel level, int x, int y, int z, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(level, x, y, z);
            return;
        }
        Probe.beginBucket(Bucket.ANIMATE_TICK, Hook.ANIMATE_TICK);
        try {
            original.call(level, x, y, z);
        } finally {
            Probe.endBucket();
        }
    }
}
