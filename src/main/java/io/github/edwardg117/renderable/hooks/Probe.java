package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Session;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * The single static entry point every hook calls (spec §4). Mixins check {@link #ACTIVE} first, so the idle
 * cost is one volatile read. Accumulators are cached per Minecraft object in an identity map so the hot path
 * allocates nothing after a key has been seen once.
 */
public final class Probe {

    /** Implemented by the controller. All calls happen on the render thread. */
    public interface Listener {
        /** A new frame has just been opened in the session. */
        void onFrameStarted(Session session);

        /** The session's duration has elapsed; the listener should finish it. */
        void onFinishRequested();

        /** The level went away; the listener should discard the running session. */
        void onDiscardRequested(String reason);
    }

    /** True while a session is sampling. Volatile so worker-thread hooks in later phases see it. */
    public static volatile boolean ACTIVE;

    private static Session session;
    private static Listener listener;
    private static final IdentityHashMap<Object, Accumulator> BY_INSTANCE = new IdentityHashMap<>(4096);
    private static final boolean[] SEEN = new boolean[Hook.values().length];

    private Probe() {
    }

    public static void setListener(Listener l) {
        listener = l;
    }

    static Listener listener() {
        return listener;
    }

    public static void attach(Session s) {
        BY_INSTANCE.clear();
        session = s;
        ACTIVE = true;
    }

    public static void detach() {
        ACTIVE = false;
        session = null;
        BY_INSTANCE.clear();
    }

    /** The running session, or null. */
    public static Session session() {
        return session;
    }

    /** Names of hooks that have fired at least once, in enum order. */
    public static List<String> hooksApplied() {
        List<String> out = new ArrayList<>();
        for (Hook h : Hook.values()) {
            if (SEEN[h.ordinal()]) out.add(h.name());
        }
        return out;
    }

    public static void beginBlockEntity(BlockEntity be) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.BLOCK_ENTITY_RENDER.ordinal()] = true;
        Accumulator a = BY_INSTANCE.get(be);
        if (a == null) {
            a = s.accumulator(KeyFactory.blockEntity(be));
            BY_INSTANCE.put(be, a);
        }
        s.begin(a, Bucket.BLOCK_ENTITY);
    }

    public static void beginEntity(Entity e) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.ENTITY_RENDER.ordinal()] = true;
        Accumulator a = BY_INSTANCE.get(e);
        if (a == null) {
            a = s.accumulator(KeyFactory.entity(e));
            BY_INSTANCE.put(e, a);
        }
        s.begin(a, Bucket.ENTITY);
    }

    /** Ends the innermost timed call. Safe to call when no session is running. */
    public static void end() {
        Session s = session;
        if (s != null) s.end();
    }
}
