package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Times each vanilla packet's main-thread handling (spec addendum §P5.A5). A handler that is not on the main
 * thread queues this lambda there through {@code ensureRunningOnSameThread}, and the lambda runs it inside
 * {@code Minecraft.runAllTasks}.
 */
@Mixin(PacketUtils.class)
public abstract class PacketUtilsMixin {

    @WrapOperation(method = "lambda$ensureRunningOnSameThread$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
    private static void renderable$timePacket(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(packet, listener);
            return;
        }
        boolean timed = Probe.beginPacket(packet);
        try {
            original.call(packet, listener);
        } finally {
            if (timed) Probe.end();
        }
    }
}
