package io.github.edwardg117.renderable.core;

import java.util.function.LongSupplier;

/** Spec §3.5: measures what one begin/end pair costs so the report can state its own overhead. */
public final class Calibration {
    private static final int WARMUP = 2_000;
    private static final int SAMPLES = 10_000;

    private Calibration() {
    }

    public static long measureCallNanos(LongSupplier clock) {
        Session s = new Session(new SessionConfig(Long.MAX_VALUE, 2.0, 0, 5, 0, 0), clock, 0);
        Accumulator a = s.accumulator(new EntityKey("renderable:calibration", 0, ""));
        for (int i = 0; i < WARMUP; i++) {
            s.begin(a, Bucket.ENTITY);
            s.end();
        }
        long start = clock.getAsLong();
        for (int i = 0; i < SAMPLES; i++) {
            s.begin(a, Bucket.ENTITY);
            s.end();
        }
        long elapsed = clock.getAsLong() - start;
        return Math.max(0, elapsed / SAMPLES);
    }
}
