package io.github.edwardg117.renderable.core;

import java.util.List;

/**
 * One sampled frame (spec §3.2, amended by spec addendum §P5.A1).
 *
 * @param index       0-based frame number within the session
 * @param startNanos  offset of this frame's start from the session start
 * @param totalNanos  start of this frame to start of the next
 * @param tickNanos   client tick time that ran inside this frame
 * @param renderNanos frame start, just before RenderFrameEvent.Pre is posted, to just after RenderFrameEvent.Post
 * @param tasksNanos  time in {@code Minecraft.runAllTasks}: packets and other queued main-thread work
 * @param capNanos    time in {@code RenderSystem.limitDisplayFPS}: the wait for the frame-rate limit
 * @param buckets     nanoseconds per {@link Bucket}, indexed by ordinal
 * @param namedNanos  nanoseconds per {@link Window} that the Mods tab names (spec addendum §P5.A6)
 * @param gcCount     garbage collections observed during this frame
 * @param gcMillis    GC pause milliseconds observed during this frame
 * @param top         top contributors, most expensive first
 */
public record FrameRecord(int index,
                          long startNanos,
                          long totalNanos,
                          long tickNanos,
                          long renderNanos,
                          long tasksNanos,
                          long capNanos,
                          long[] buckets,
                          long[] namedNanos,
                          int gcCount,
                          long gcMillis,
                          List<TopEntry> top) {

    /** A frame with no tasks, no frame-cap wait and nothing named: the phase 1 to 4 shape that tests build. */
    public FrameRecord(int index, long startNanos, long totalNanos, long tickNanos, long renderNanos,
                       long[] buckets, int gcCount, long gcMillis, List<TopEntry> top) {
        this(index, startNanos, totalNanos, tickNanos, renderNanos, 0, 0, buckets, new long[Window.COUNT],
                gcCount, gcMillis, top);
    }

    public long bucket(Bucket b) {
        return buckets[b.ordinal()];
    }

    /** The part of window {@code w} that an attribution-only call accounts for and no bucket does. */
    public long named(Window w) {
        return namedNanos[w.ordinal()];
    }

    public long renderBucketsSum() {
        long sum = 0;
        for (Bucket b : Bucket.VALUES) {
            if (b.isRender()) sum += buckets[b.ordinal()];
        }
        return sum;
    }

    public long tickBucketsSum() {
        long sum = 0;
        for (Bucket b : Bucket.VALUES) {
            if (!b.isRender()) sum += buckets[b.ordinal()];
        }
        return sum;
    }

    /** Render time not explained by any render bucket. Clamped at zero. */
    public long otherRenderNanos() {
        return Math.max(0, renderNanos - renderBucketsSum());
    }

    /** Tick time not explained by any tick bucket. Clamped at zero. */
    public long otherTickNanos() {
        return Math.max(0, tickNanos - tickBucketsSum());
    }

    /**
     * Everything outside tick, render, tasks and the frame cap: the buffer swap and any vsync wait, the blit,
     * the sound update, mouse turning and the yield. Clamped at zero.
     */
    public long swapNanos() {
        return Math.max(0, totalNanos - tickNanos - renderNanos - tasksNanos - capNanos);
    }
}
