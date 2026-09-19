package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.hooks.compat.IrisCompat;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * The single static entry point every hook calls (spec §4). Mixins check {@link #ACTIVE} first, so the idle
 * cost is one volatile read. Accumulators are cached per Minecraft object (block entities, entities) or per
 * class (particles) so the hot path allocates nothing after a key has been seen once. Render and tick hooks
 * for the same object share one accumulator.
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

    /** True while a session is sampling. Volatile so worker-thread hooks see it. */
    public static volatile boolean ACTIVE;

    private static Session session;
    private static Listener listener;
    private static Thread renderThread;
    private static final IdentityHashMap<Object, Accumulator> BY_INSTANCE = new IdentityHashMap<>(4096);
    private static final HashMap<Class<?>, Accumulator> BY_CLASS = new HashMap<>(256);
    private static final boolean[] SEEN = new boolean[Hook.values().length];

    private Probe() {
    }

    public static void setListener(Listener l) {
        listener = l;
    }

    static Listener listener() {
        return listener;
    }

    /** Attaches a session. Must be called on the render thread: bucket-only hooks compare against it. */
    public static void attach(Session s) {
        BY_INSTANCE.clear();
        BY_CLASS.clear();
        renderThread = Thread.currentThread();
        session = s;
        ACTIVE = true;
    }

    public static void detach() {
        ACTIVE = false;
        session = null;
        BY_INSTANCE.clear();
        BY_CLASS.clear();
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
        markShadow(s);
        s.begin(instanceAccumulator(s, be), Bucket.BLOCK_ENTITY);
    }

    public static void beginEntity(Entity e) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.ENTITY_RENDER.ordinal()] = true;
        markShadow(s);
        s.begin(instanceAccumulator(s, e), Bucket.ENTITY);
    }

    public static void beginParticleRender(Particle p) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.PARTICLE_RENDER.ordinal()] = true;
        markShadow(s);
        s.begin(classAccumulator(s, p), Bucket.PARTICLE_RENDER);
    }

    public static void beginBlockEntityTick(BlockEntity be) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.BLOCK_ENTITY_TICK.ordinal()] = true;
        s.begin(instanceAccumulator(s, be), Bucket.BE_TICK);
    }

    public static void beginEntityTick(Entity e) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.ENTITY_TICK.ordinal()] = true;
        s.begin(instanceAccumulator(s, e), Bucket.ENTITY_TICK);
    }

    public static void beginParticleTick(Particle p) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.PARTICLE_TICK.ordinal()] = true;
        s.begin(classAccumulator(s, p), Bucket.PARTICLE_TICK);
    }

    /**
     * Times work that has no key: light updates, Flywheel, chunk upload. Ignored off the render thread,
     * because the light engine's base method also runs on the server's light thread in singleplayer.
     * Pair with {@link #endBucket()}, never with {@link #end()}.
     */
    public static void beginBucket(Bucket bucket, Hook hook) {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return;
        SEEN[hook.ordinal()] = true;
        s.begin(null, bucket);
    }

    /** Ends a bucket-only timing. Makes the same thread decision as {@link #beginBucket} so the stack stays balanced. */
    public static void endBucket() {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return;
        s.end();
    }

    /** Ends the innermost keyed call. Safe to call when no session is running. */
    public static void end() {
        Session s = session;
        if (s != null) s.end();
    }

    private static Accumulator instanceAccumulator(Session s, BlockEntity be) {
        Accumulator a = BY_INSTANCE.get(be);
        if (a == null) {
            a = s.accumulator(KeyFactory.blockEntity(be));
            BY_INSTANCE.put(be, a);
        }
        return a;
    }

    private static Accumulator instanceAccumulator(Session s, Entity e) {
        Accumulator a = BY_INSTANCE.get(e);
        if (a == null) {
            a = s.accumulator(KeyFactory.entity(e));
            BY_INSTANCE.put(e, a);
        }
        return a;
    }

    private static Accumulator classAccumulator(Session s, Particle p) {
        Class<?> c = p.getClass();
        Accumulator a = BY_CLASS.get(c);
        if (a == null) {
            a = s.accumulator(KeyFactory.particle(c));
            BY_CLASS.put(c, a);
        }
        return a;
    }

    /** Iris draws block entities, entities and particles again in its shadow pass; tag those calls. */
    private static void markShadow(Session s) {
        if (IrisCompat.PRESENT) {
            boolean shadow = IrisCompat.shadowActive();
            s.shadowPass = shadow;
            if (shadow) SEEN[Hook.IRIS_SHADOW.ordinal()] = true;
        }
    }
}
