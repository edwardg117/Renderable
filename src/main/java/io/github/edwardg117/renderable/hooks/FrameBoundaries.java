package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Session;

/**
 * Frame and tick boundaries (spec §3.2, spec addendum §P5.A2), called by {@code MinecraftMixin} just before
 * NeoForge posts RenderFrameEvent.Pre and ClientTickEvent.Pre, and just after it posts the matching Post.
 * They were listeners in {@link FrameEvents}, but listeners at the default priority split other mods'
 * listeners of the same events across the window boundary, which matters once those listeners are timed.
 * Render thread only.
 */
public final class FrameBoundaries {
    private static final GcSampler GC = new GcSampler();

    private FrameBoundaries() {
    }

    /** Closes the previous frame and opens the next; ends the session when its duration has passed. */
    public static void renderPre() {
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

    public static void renderPost() {
        Session s = Probe.session();
        if (s != null) s.onRenderPost();
    }

    public static void tickPre() {
        Session s = Probe.session();
        if (s != null) s.onTickPre();
    }

    public static void tickPost() {
        Session s = Probe.session();
        if (s != null) s.onTickPost();
    }
}
