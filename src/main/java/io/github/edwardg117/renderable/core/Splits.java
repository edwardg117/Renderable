package io.github.edwardg117.renderable.core;

/**
 * One-line descriptions of how a frame, or the average frame, was spent. Tick buckets are children of
 * {@code tick} and are printed in brackets after it, so the parts outside the brackets sum to the frame.
 */
public final class Splits {

    private Splits() {
    }

    /** Average per-frame split, two decimals: tick with its children, each non-zero render bucket, other render, swap. */
    public static String average(FrameStats st) {
        StringBuilder sb = new StringBuilder();
        sb.append("tick ").append(Fmt.ms2(st.avgTickNanos()));
        appendTickChildren(sb, st.avgBucketNanos(), true);
        for (Bucket b : Bucket.VALUES) {
            long v = st.avgBucket(b);
            if (b.isRender() && v > 0) sb.append(" | ").append(b.label()).append(' ').append(Fmt.ms2(v));
        }
        sb.append(" | other render ").append(Fmt.ms2(st.avgOtherRenderNanos()));
        sb.append(" | swap/wait ").append(Fmt.ms2(st.avgSwapNanos()));
        return sb.toString();
    }

    /** One frame's split, one decimal: tick with its children, each non-zero render bucket, other render, swap. */
    public static String frame(FrameRecord f) {
        StringBuilder sb = new StringBuilder();
        sb.append("tick ").append(Fmt.ms(f.tickNanos()));
        appendTickChildren(sb, f.buckets(), false);
        for (Bucket b : Bucket.VALUES) {
            long v = f.bucket(b);
            if (b.isRender() && v > 0) sb.append(" | ").append(b.label()).append(' ').append(Fmt.ms(v));
        }
        sb.append(" | other render ").append(Fmt.ms(f.otherRenderNanos()));
        sb.append(" | swap ").append(Fmt.ms(f.swapNanos()));
        return sb.toString();
    }

    private static void appendTickChildren(StringBuilder sb, long[] buckets, boolean twoDecimals) {
        boolean any = false;
        for (Bucket b : Bucket.VALUES) {
            long v = buckets[b.ordinal()];
            if (b.isRender() || v <= 0) continue;
            sb.append(any ? ", " : " [").append(b.label()).append(' ').append(twoDecimals ? Fmt.ms2(v) : Fmt.ms(v));
            any = true;
        }
        if (any) sb.append(']');
    }
}
