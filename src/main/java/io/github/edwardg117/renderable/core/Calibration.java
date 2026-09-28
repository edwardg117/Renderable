package io.github.edwardg117.renderable.core;

import java.util.function.LongSupplier;

/**
 * Spec §3.5 and spec addendum §P5.C2: the timing loop behind the report's overhead estimate. The calls it times
 * come from {@code hooks.CallCalibration}, which drives the same static {@code Probe} methods the mixins call,
 * guards included; this class has no Minecraft imports, so the loop is tested here with a fake clock.
 */
public final class Calibration {
    private static final int WARMUP = 2_000;
    private static final int SAMPLES = 10_000;

    private Calibration() {
    }

    /**
     * Runs {@code call} 2,000 times to warm it up, then times 10,000 runs, and returns the nanoseconds one run
     * took, rounded down and never negative. The figure includes the loop's own call through {@code Runnable}, a
     * nanosecond or two. The loop keeps everything the call touches in cache, so the same call in a real frame can
     * cost more.
     */
    public static long nanosPerCall(LongSupplier clock, Runnable call) {
        for (int i = 0; i < WARMUP; i++) call.run();
        long start = clock.getAsLong();
        for (int i = 0; i < SAMPLES; i++) call.run();
        long elapsed = clock.getAsLong() - start;
        return Math.max(0, elapsed / SAMPLES);
    }
}
