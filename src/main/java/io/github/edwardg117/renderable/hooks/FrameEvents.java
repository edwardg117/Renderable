package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Session;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Feeds frame and tick boundaries into the running session. Registered on the NeoForge game bus. */
public final class FrameEvents {
    private static final GcSampler GC = new GcSampler();

    private FrameEvents() {
    }

    @SubscribeEvent
    public static void onRenderPre(RenderFrameEvent.Pre event) {
        Session s = Probe.session();
        if (s == null) return;
        GC.sample();
        boolean done = s.onRenderPre(GC.count(), GC.millis());
        Probe.Listener l = Probe.listener();
        if (l == null) return;
        if (done) {
            l.onFinishRequested();
        } else {
            l.onFrameStarted(s);
        }
    }

    @SubscribeEvent
    public static void onRenderPost(RenderFrameEvent.Post event) {
        Session s = Probe.session();
        if (s != null) s.onRenderPost();
    }

    @SubscribeEvent
    public static void onTickPre(ClientTickEvent.Pre event) {
        Session s = Probe.session();
        if (s != null) s.onTickPre();
    }

    @SubscribeEvent
    public static void onTickPost(ClientTickEvent.Post event) {
        Session s = Probe.session();
        if (s != null) s.onTickPost();
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
