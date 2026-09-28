package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Calibration;
import io.github.edwardg117.renderable.core.EntityKey;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.core.SessionConfig;
import io.github.edwardg117.renderable.hooks.events.ProxyCalibration;
import net.minecraft.world.entity.Entity;

/**
 * Measures what Renderable's own calls cost, for the report's overhead estimate (spec §3.5, spec addendum §P5.C2).
 * Phase 4 timed {@code Session} alone. This drives whole calls through the same static {@link Probe} methods the
 * mixins call, and through an event-timing proxy, so each figure includes what a hook pays besides
 * {@code Session}'s own work: its volatile guard, {@code Probe}'s checks or the proxy's, and for the timed pair the
 * lookup of the entity's accumulator. It runs once, before the first real session, against a scratch session
 * attached for the duration. Anything other threads record into it is thrown away with it, and the fired-hook flags
 * are put back, so the calls it drives mark no hook as fired. A Sodium mesh build running meanwhile can still mark
 * its hook: worker threads set that flag, and it is not put back. Render thread only.
 */
public final class CallCalibration {
    /**
     * Rounds of each measurement. The first rounds time the JIT, not the calls, so each kind keeps its fastest.
     * Load-bearing: the kinds take turns within a round. Five rounds of one kind before the next left the first
     * kinds cold in a replica on JDK 21.0.11, a vertex at 6 to 7 ns rather than 2.
     */
    private static final int ROUNDS = 5;

    /**
     * Nanoseconds per call: a timed begin/end pair, a vertex and an attribution-only pair, which the report uses;
     * and {@code Session} alone for a pair and a vertex, phase 4's way, which the log shows beside them so the
     * guard's cost can be read off (plan 5c, deviation 1).
     */
    public record Costs(long timedPair, long vertex, long attributionPair, long sessionPair, long sessionVertex) {
    }

    private CallCalibration() {
    }

    /** {@code sample} is the entity whose render call is timed, the local player; {@code dimension} names its level. */
    public static Costs measure(Entity sample, String dimension) {
        Probe.Fired fired = Probe.fired();
        Session scratch = new Session(new SessionConfig(Long.MAX_VALUE, 2.0, 0, 5, 0, 0), System::nanoTime, 0);
        Probe.attach(scratch, dimension);
        try {
            // Load-bearing: an open frame makes end() do its in-frame work, as it does in a real frame.
            scratch.onRenderPre(0, 0);
            Runnable pairCall = () -> {
                if (Probe.ACTIVE) {
                    Probe.beginEntity(sample);
                    Probe.end();
                }
            };
            Runnable vertexCall = () -> {
                if (Probe.ACTIVE) Probe.vertex();
            };
            Runnable proxyCall = ProxyCalibration.invocation(scratch);
            Accumulator a = scratch.accumulator(new EntityKey("renderable:calibration", 0, ""));
            Runnable sessionPairCall = () -> {
                scratch.begin(a, Bucket.ENTITY);
                scratch.end();
            };
            Runnable sessionVertexCall = scratch::vertex;

            long pair = Long.MAX_VALUE;
            long vertex = Long.MAX_VALUE;
            long attribution = Long.MAX_VALUE;
            long sessionPair = Long.MAX_VALUE;
            long sessionVertex = Long.MAX_VALUE;
            for (int round = 0; round < ROUNDS; round++) {
                pair = Math.min(pair, Calibration.nanosPerCall(System::nanoTime, pairCall));
                Probe.beginEntity(sample);   // a vertex lands on an open keyed call, as in a real frame
                try {
                    vertex = Math.min(vertex, Calibration.nanosPerCall(System::nanoTime, vertexCall));
                } finally {
                    Probe.end();
                }
                attribution = Math.min(attribution, Calibration.nanosPerCall(System::nanoTime, proxyCall));
                sessionPair = Math.min(sessionPair, Calibration.nanosPerCall(System::nanoTime, sessionPairCall));
                scratch.begin(a, Bucket.ENTITY);
                try {
                    sessionVertex = Math.min(sessionVertex, Calibration.nanosPerCall(System::nanoTime, sessionVertexCall));
                } finally {
                    scratch.end();
                }
            }
            return new Costs(pair, vertex, attribution, sessionPair, sessionVertex);
        } finally {
            Probe.detach();
            Probe.restore(fired);
        }
    }
}
