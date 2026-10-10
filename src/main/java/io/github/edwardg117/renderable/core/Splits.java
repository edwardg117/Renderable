package io.github.edwardg117.renderable.core;

/**
 * One-line descriptions of how a frame, or the average frame, was spent. Tick buckets are children of
 * {@code tick} and are printed in brackets after it, so the parts outside the brackets sum to the frame.
 * {@code mods x} in brackets after tick, other render or tasks is the part of it the Mods tab names (spec
 * addendum §P5.A6). The tick's brackets end with {@code unnamed x}, the rest of the tick, so they partition it
 * (spec addendum §P6.6). Tasks and the frame cap are printed when they have time, as render buckets are.
 */
public final class Splits {

    private Splits() {
    }

    /** Average per-frame split, two decimals. */
    public static String average(FrameStats st) {
        StringBuilder sb = new StringBuilder();
        sb.append("tick ").append(Fmt.ms2(st.avgTickNanos()));
        appendTickChildren(sb, st.avgBucketNanos(), st.avgNamed(Window.TICK), st.avgUnnamedTickNanos(), true);
        for (Bucket b : Bucket.VALUES) {
            long v = st.avgBucket(b);
            if (b.isRender() && v > 0) sb.append(" | ").append(b.label()).append(' ').append(Fmt.ms2(v));
        }
        sb.append(" | other render ").append(Fmt.ms2(st.avgOtherRenderNanos()));
        appendNamed(sb, st.avgNamed(Window.RENDER), true);
        if (st.avgTasksNanos() > 0) {
            sb.append(" | tasks ").append(Fmt.ms2(st.avgTasksNanos()));
            appendNamed(sb, st.avgNamed(Window.TASKS), true);
        }
        if (st.avgCapNanos() > 0) sb.append(" | frame cap ").append(Fmt.ms2(st.avgCapNanos()));
        sb.append(" | swap/wait ").append(Fmt.ms2(st.avgSwapNanos()));
        return sb.toString();
    }

    /** One frame's split, one decimal. */
    public static String frame(FrameRecord f) {
        StringBuilder sb = new StringBuilder();
        sb.append("tick ").append(Fmt.ms(f.tickNanos()));
        appendTickChildren(sb, f.buckets(), f.named(Window.TICK), f.unnamedTickNanos(), false);
        for (Bucket b : Bucket.VALUES) {
            long v = f.bucket(b);
            if (b.isRender() && v > 0) sb.append(" | ").append(b.label()).append(' ').append(Fmt.ms(v));
        }
        sb.append(" | other render ").append(Fmt.ms(f.otherRenderNanos()));
        appendNamed(sb, f.named(Window.RENDER), false);
        if (f.tasksNanos() > 0) {
            sb.append(" | tasks ").append(Fmt.ms(f.tasksNanos()));
            appendNamed(sb, f.named(Window.TASKS), false);
        }
        if (f.capNanos() > 0) sb.append(" | frame cap ").append(Fmt.ms(f.capNanos()));
        sb.append(" | swap ").append(Fmt.ms(f.swapNanos()));
        return sb.toString();
    }

    /** The tick's brackets: its buckets in enum order, then {@code mods}, then {@code unnamed}, each when above zero. */
    private static void appendTickChildren(StringBuilder sb, long[] buckets, long named, long unnamed, boolean twoDecimals) {
        boolean any = false;
        for (Bucket b : Bucket.VALUES) {
            long v = buckets[b.ordinal()];
            if (b.isRender() || v <= 0) continue;
            sb.append(any ? ", " : " [").append(b.label()).append(' ').append(twoDecimals ? Fmt.ms2(v) : Fmt.ms(v));
            any = true;
        }
        if (named > 0) {
            sb.append(any ? ", " : " [").append("mods ").append(twoDecimals ? Fmt.ms2(named) : Fmt.ms(named));
            any = true;
        }
        if (unnamed > 0) {
            sb.append(any ? ", " : " [").append("unnamed ").append(twoDecimals ? Fmt.ms2(unnamed) : Fmt.ms(unnamed));
            any = true;
        }
        if (any) sb.append(']');
    }

    private static void appendNamed(StringBuilder sb, long named, boolean twoDecimals) {
        if (named > 0) sb.append(" [mods ").append(twoDecimals ? Fmt.ms2(named) : Fmt.ms(named)).append(']');
    }
}
