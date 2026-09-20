package io.github.edwardg117.renderable.core;

/**
 * Counters for one key (spec §3.4). Fields are public and mutable on purpose: they are written on the
 * render thread's hot path and read only after the session is finished.
 *
 * <p>An accumulator must not outlive the {@link Session} that created it: {@link #type} is shared,
 * session-scoped state, and a stale accumulator would keep writing into a dead session's {@link TypeStats},
 * corrupting a whole type row rather than just one key.
 */
public final class Accumulator {
    public final ProfileKey key;
    /** The aggregation shared by every accumulator of this key's type (spec addendum §P4.A2). */
    private final TypeStats type;
    /**
     * The id of the session that created this accumulator, or 0 for a standalone one (spec addendum
     * §P4.B2). An id rather than a {@link Session} reference, so caching this accumulator on a Minecraft
     * object never keeps a finished session's frame history reachable.
     */
    private final long sessionId;

    /** Number of timed render calls. */
    public long calls;
    /** Number of timed tick calls. */
    public long tickCalls;
    /** Nanoseconds spent in render-side buckets. */
    public long renderNanos;
    /** Nanoseconds spent in tick-side buckets. */
    public long tickNanos;
    /** Subset of renderNanos spent while the Iris shadow pass was active (phase 2 sets it). */
    public long shadowNanos;
    /** Largest clean render-plus-tick nanoseconds seen in a single frame (spec addendum §P4.A0). */
    public long maxFrameNanos;
    /** Frames in which this key's total was GC-suspect, and so did not update {@link #maxFrameNanos}. */
    public long gcFrames;
    /** Vertices emitted while this key was innermost (phase 3 fills it). */
    public long vertices;

    /** Render plus tick nanoseconds in the current frame. Reset by Session at each frame boundary. */
    long frameNanos;
    /** True once this accumulator has been added to the session's touched list for the current frame. */
    boolean inFrame;

    /** A standalone accumulator with a type aggregation of its own and no session. */
    Accumulator(ProfileKey key) {
        this(key, new TypeStats(key.typeId(), key.category()), 0);
    }

    Accumulator(ProfileKey key, TypeStats type, long sessionId) {
        this.key = key;
        this.type = type;
        this.sessionId = sessionId;
    }

    /** The aggregation this key's type shares. Never null. */
    public TypeStats type() {
        return type;
    }

    /**
     * The id of the session that created this accumulator, or 0 for a standalone one. A hook that caches an
     * accumulator on a Minecraft object compares this with the running session's {@link Session#id()}, so an
     * object that survives into the next session is re-keyed rather than credited to the wrong one.
     */
    public long sessionId() {
        return sessionId;
    }

    public long totalNanos() {
        return renderNanos + tickNanos;
    }

    public long frameNanos() {
        return frameNanos;
    }
}
