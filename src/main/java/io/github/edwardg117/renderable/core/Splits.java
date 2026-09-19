package io.github.edwardg117.renderable.core;

/** One-line descriptions of how a frame, or the average frame, was spent. */
public final class Splits {

    private Splits() {
    }

    /** Average per-frame split: tick, every bucket, other render, other tick, swap. Two decimals. */
    public static String average(FrameStats st) {
        StringBuilder sb = new StringBuilder();
        sb.append("tick ").append(Fmt.ms2(st.avgTickNanos()));
        for (Bucket b : Bucket.VALUES) {
            sb.append(" | ").append(b.label()).append(' ').append(Fmt.ms2(st.avgBucket(b)));
        }
        sb.append(" | other render ").append(Fmt.ms2(st.avgOtherRenderNanos()));
        sb.append(" | other tick ").append(Fmt.ms2(st.avgOtherTickNanos()));
        sb.append(" | swap/wait ").append(Fmt.ms2(st.avgSwapNanos()));
        return sb.toString();
    }

    /** One frame's split, skipping empty buckets. One decimal. */
    public static String frame(FrameRecord f) {
        StringBuilder sb = new StringBuilder();
        sb.append("tick ").append(Fmt.ms(f.tickNanos()));
        for (Bucket b : Bucket.VALUES) {
            long v = f.bucket(b);
            if (v > 0) sb.append(" | ").append(b.label()).append(' ').append(Fmt.ms(v));
        }
        sb.append(" | other render ").append(Fmt.ms(f.otherRenderNanos()));
        sb.append(" | swap ").append(Fmt.ms(f.swapNanos()));
        return sb.toString();
    }
}
