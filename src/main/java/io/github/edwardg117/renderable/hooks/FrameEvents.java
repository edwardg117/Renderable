package io.github.edwardg117.renderable.hooks;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Ends a running session when its world goes away. Registered on the NeoForge game bus. The frame and tick
 * boundaries that were listeners here moved to {@link FrameBoundaries} (spec addendum §P5.A2).
 */
public final class FrameEvents {

    private FrameEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        discard("logged out");
        worldGone();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        discard("level unloaded");
        worldGone();
    }

    private static void discard(String reason) {
        if (Probe.session() == null) return;
        Probe.Listener l = Probe.listener();
        if (l != null) {
            l.onDiscardRequested(reason);
        } else {
            Probe.detach();
        }
    }

    private static void worldGone() {
        Probe.Listener l = Probe.listener();
        if (l != null) l.onWorldGone();
    }
}
