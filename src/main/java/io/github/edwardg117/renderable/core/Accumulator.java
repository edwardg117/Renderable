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

    /** A standalone accumulator with a type aggregation of its own. Sessions use the two-argument form. */
    Accumulator(ProfileKey key) {
        this(key, new TypeStats(key.typeId(), key.category()));
    }

    Accumulator(ProfileKey key, TypeStats type) {
        this.key = key;
        this.type = type;
    }

    /** The aggregation this key's type shares. Never null. */
    public TypeStats type() {
        return type;
    }

    public long totalNanos() {
        return renderNanos + tickNanos;
    }

    public long frameNanos() {
        return frameNanos;
    }
}
