package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.handling.MainThreadPayloadHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Times each mod payload handled on the main thread (spec addendum §P5.A5). NeoForge wraps a main-thread
 * handler, the default, in {@code MainThreadPayloadHandler}, which queues this lambda to run the mod's handler
 * inside {@code Minecraft.runAllTasks}.
 */
@Mixin(MainThreadPayloadHandler.class)
public abstract class MainThreadPayloadHandlerMixin {

    @WrapOperation(method = "lambda$handle$0", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/network/handling/IPayloadHandler;handle(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V"))
    private void renderable$timePayload(IPayloadHandler<?> handler, CustomPacketPayload payload, IPayloadContext context,
                                        Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(handler, payload, context);
            return;
        }
        boolean timed = Probe.beginPayload(payload);
        try {
            original.call(handler, payload, context);
        } finally {
            if (timed) Probe.end();
        }
    }
}
