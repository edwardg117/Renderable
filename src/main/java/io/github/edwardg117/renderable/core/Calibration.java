package io.github.edwardg117.renderable.core;

import java.util.function.LongSupplier;

/** Spec §3.5: measures what one begin/end pair costs so the report can state its own overhead. */
public final class Calibration {
    private static final int WARMUP = 2_000;
    private static final int SAMPLES = 10_000;

    private Calibration() {
    }

    /**
     * What one {@link Session#begin} / {@link Session#end} pair costs.
     *
     * <p>This measures only the {@code Session} side. {@code Calibration} lives in {@code core} and cannot
     * import {@code Probe}, so the guard a timed hook such as {@code Probe.beginBlockEntity} pays first —
     * its {@code SEEN[]} write and {@code markShadow} call — is not measured (spec §P4.3, a phase 5 item).
     * The loop always runs the most expensive branch, so a call that returns at its own guard is
     * over-charged by this estimate, never under-charged: the error is one-sided in the safe direction.
     */
    public static long measureCallNanos(LongSupplier clock) {
        Session s = new Session(new SessionConfig(Long.MAX_VALUE, 2.0, 0, 5, 0, 0), clock, 0);
        Accumulator a = s.accumulator(new EntityKey("renderable:calibration", 0, ""));
        // Load-bearing: opens a frame so end()'s in-frame block (the touched-list add and the bucket add)
        // runs for every warmup and sampled call, matching what a real begin/end pair pays. That block
        // performs no clock reads, so no fake-clock test can pin this regression — this comment is the
        // only thing protecting it.
        s.onRenderPre(0, 0);
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

    /**
     * Spec addendum §P4.A5: what one {@link Session#vertex()} costs. Measured inside one open call, because
     * that is the shape the hook has in a real frame: the loop body is the branch and the increment.
     *
     * <p>This measures only the {@code Session} side. {@code Calibration} lives in {@code core} and cannot
     * import {@code Probe}, so {@code Probe.vertex()}'s own guard — its static {@code ACTIVE} read, its
     * thread comparison and its {@code seenVertices} check — is not measured (spec §P4.3, a phase 5 item).
     * The loop always runs the most expensive branch, so a call that returns at its own guard is
     * over-charged by this estimate, never under-charged: the error is one-sided in the safe direction.
     */
    public static long measureVertexNanos(LongSupplier clock) {
        Session s = new Session(new SessionConfig(Long.MAX_VALUE, 2.0, 0, 5, 0, 0), clock, 0);
        Accumulator a = s.accumulator(new EntityKey("renderable:calibration", 0, ""));
        // Not load-bearing here — vertex() never reads frameStartNanos — but keeps the two calibrations
        // one shape, so a reader comparing this line to measureCallNanos's does not have to wonder.
        s.onRenderPre(0, 0);
        s.begin(a, Bucket.ENTITY);
        for (int i = 0; i < WARMUP; i++) s.vertex();
        long start = clock.getAsLong();
        for (int i = 0; i < SAMPLES; i++) s.vertex();
        long elapsed = clock.getAsLong() - start;
        s.end();
        return Math.max(0, elapsed / SAMPLES);
    }
}
