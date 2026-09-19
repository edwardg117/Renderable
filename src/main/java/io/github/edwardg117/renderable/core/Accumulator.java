package io.github.edwardg117.renderable.core;

/**
 * Counters for one key (spec §3.4). Fields are public and mutable on purpose: they are written on the
 * render thread's hot path and read only after the session is finished.
 */
public final class Accumulator {
    public final ProfileKey key;

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
    /** Largest render-plus-tick nanoseconds seen in a single frame. */
    public long maxFrameNanos;
    /** Vertices emitted while this key was innermost (phase 3 fills it). */
    public long vertices;

    /** Render plus tick nanoseconds in the current frame. Reset by Session at each frame boundary. */
    long frameNanos;
    /** True once this accumulator has been added to the session's touched list for the current frame. */
    boolean inFrame;

    public Accumulator(ProfileKey key) {
        this.key = key;
    }

    public long totalNanos() {
        return renderNanos + tickNanos;
    }

    public long frameNanos() {
        return frameNanos;
    }
}
