package io.github.edwardg117.renderable.core;

import java.util.List;

/**
 * One sampled frame (spec §3.2).
 *
 * @param index       0-based frame number within the session
 * @param startNanos  offset of this frame's start from the session start
 * @param totalNanos  start of this frame to start of the next
 * @param tickNanos   client tick time that ran inside this frame
 * @param renderNanos RenderFrameEvent.Pre to Post
 * @param buckets     nanoseconds per {@link Bucket}, indexed by ordinal
 * @param gcCount     garbage collections observed during this frame
 * @param gcMillis    GC pause milliseconds observed during this frame
 * @param top         top contributors, most expensive first
 */
public record FrameRecord(int index,
                          long startNanos,
                          long totalNanos,
                          long tickNanos,
                          long renderNanos,
                          long[] buckets,
                          int gcCount,
                          long gcMillis,
                          List<TopEntry> top) {

    public long bucket(Bucket b) {
        return buckets[b.ordinal()];
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

    /** Everything outside tick and render: window swap, vsync wait, input polling. Clamped at zero. */
    public long swapNanos() {
        return Math.max(0, totalNanos - tickNanos - renderNanos);
    }
}
