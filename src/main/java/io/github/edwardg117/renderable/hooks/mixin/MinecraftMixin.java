package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.edwardg117.renderable.hooks.FrameBoundaries;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The frame loop's boundaries and two of its components (spec addendum §P5.A1 and §P5.A2). A frame opens just
 * before NeoForge posts RenderFrameEvent.Pre and its render window closes just after RenderFrameEvent.Post; a
 * tick opens just before ClientTickEvent.Pre and closes just after ClientTickEvent.Post. So every listener of
 * those events, at any priority, falls inside the window it belongs to.
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
}
