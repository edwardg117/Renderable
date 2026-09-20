package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.MeshAccumulator;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.hooks.compat.IrisCompat;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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
    /** Mesh accumulators by Block or Fluid instance; written from Sodium's worker threads. */
    private static final ConcurrentHashMap<Object, MeshAccumulator> MESH_BY_OBJECT = new ConcurrentHashMap<>(512);
    /** Dimension of the level the session was started in, for section keys built on worker threads. */
    private static volatile String dimension = "unknown";
    /** Set from worker threads; SEEN is a plain array only the render thread writes, so this is mirrored into it. */
    private static volatile boolean meshSeen;
    private static boolean seenVertices;
    private static boolean seenAcceleratedVertices;

    private Probe() {
    }

    public static void setListener(Listener l) {
        listener = l;
    }

    static Listener listener() {
        return listener;
    }

    /**
     * Attaches a session. Must be called on the render thread: bucket-only hooks and vertex counting compare
     * against it. {@code levelDimension} names the dimension for section keys.
     *
     * The write order is load-bearing: renderThread and session are plain fields, and the volatile write to
     * ACTIVE publishes them. Every hook reads ACTIVE first, so a thread that sees ACTIVE also sees the rest.
     */
    public static void attach(Session s, String levelDimension) {
        BY_INSTANCE.clear();
        BY_CLASS.clear();
        MESH_BY_OBJECT.clear();
        seenVertices = false;
        seenAcceleratedVertices = false;
        dimension = levelDimension;
        renderThread = Thread.currentThread();
        session = s;
        ACTIVE = true;
    }

    /**
     * The write order matters: ACTIVE goes false first so no new hook call starts, then the session is
     * nulled (a call already past its ACTIVE check sees null and returns), then the caches are cleared;
     * a worker still holding a mesh accumulator may put it back, which the owner check in
     * meshBlock/meshFluid tolerates.
     */
    public static void detach() {
        ACTIVE = false;
        session = null;
        BY_INSTANCE.clear();
        BY_CLASS.clear();
        MESH_BY_OBJECT.clear();
    }

    /** The running session, or null. */
    public static Session session() {
        return session;
    }

    /** Names of hooks that have fired at least once, in enum order. */
    public static List<String> hooksApplied() {
        if (meshSeen) SEEN[Hook.SODIUM_MESH.ordinal()] = true;
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

    /**
     * One vertex on the innermost keyed call (spec §4.3). Runs for every vertex the game emits while sampling,
     * so it is one null check, one thread check and one increment. Vanilla mesh workers also emit vertices;
     * those are ignored because they have no keyed call.
     */
    public static void vertex() {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return;
        if (!seenVertices) {
            seenVertices = true;
            SEEN[Hook.VERTICES.ordinal()] = true;
        }
        s.vertex();
    }

    /** {@code n} vertices on the innermost keyed call at once (Sodium's batched push). Render thread only. */
    public static void vertices(int n) {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return;
        if (!seenVertices) {
            seenVertices = true;
            SEEN[Hook.VERTICES.ordinal()] = true;
        }
        s.vertices(n);
    }

    /**
     * One vertex written through Accelerated Rendering's own buffer builder (spec addendum §P4.A3). That
     * builder replaces vanilla's for the renderers the mod takes over, so those vertices never reach
     * {@code BufferBuilder.beginVertex} and there is no double counting. Render thread only.
     */
    public static void acceleratedVertex() {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return;
        if (!seenAcceleratedVertices) {
            seenAcceleratedVertices = true;
            SEEN[Hook.ACCELERATED_VERTICES.ordinal()] = true;
        }
        s.vertex();
    }

    /** {@code n} vertices from one Accelerated Rendering mesh upload. Render thread only. */
    public static void acceleratedVertices(int n) {
        Session s = session;
        if (s == null || n <= 0 || Thread.currentThread() != renderThread) return;
        if (!seenAcceleratedVertices) {
            seenAcceleratedVertices = true;
            SEEN[Hook.ACCELERATED_VERTICES.ordinal()] = true;
        }
        s.vertices(n);
    }

    /**
     * Sodium mesh build, any thread: one block model rendered into a section mesh. A cached accumulator from
     * an earlier session is replaced rather than reused.
     */
    public static void meshBlock(BlockState state, long elapsedNanos) {
        Session s = session;
        if (s == null) return;
        if (!meshSeen) meshSeen = true;
        Block block = state.getBlock();
        MeshAccumulator a = MESH_BY_OBJECT.get(block);
        if (a == null || a.owner != s.mesh()) {
            a = s.mesh().get(KeyFactory.block(block));
            MESH_BY_OBJECT.put(block, a);
        }
        a.add(elapsedNanos);
    }

    /**
     * Sodium mesh build, any thread: one fluid rendered into a section mesh, keyed by its block. A cached
     * accumulator from an earlier session is replaced rather than reused.
     */
    public static void meshFluid(FluidState fluidState, long elapsedNanos) {
        Session s = session;
        if (s == null) return;
        if (!meshSeen) meshSeen = true;
        Fluid fluid = fluidState.getType();
        MeshAccumulator a = MESH_BY_OBJECT.get(fluid);
        if (a == null || a.owner != s.mesh()) {
            a = s.mesh().get(KeyFactory.fluidBlock(fluid));
            MESH_BY_OBJECT.put(fluid, a);
        }
        a.add(elapsedNanos);
    }

    /** Sodium mesh build, any thread: one section rebuilt, in section coordinates. */
    public static void meshSection(int sectionX, int sectionY, int sectionZ, long elapsedNanos) {
        Session s = session;
        if (s == null) return;
        if (!meshSeen) meshSeen = true;
        s.mesh().get(KeyFactory.section(dimension, sectionX, sectionY, sectionZ)).add(elapsedNanos);
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
