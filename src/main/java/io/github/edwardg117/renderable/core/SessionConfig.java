package io.github.edwardg117.renderable.core;

/**
 * Immutable settings for one session. Durations are nanoseconds so the core never converts units.
 *
 * @param durationNanos              how long to sample
 * @param hitchMedianMultiplier      spec §3.3, default 2.0
 * @param hitchMinExcessNanos        spec §3.3, default 16 ms
 * @param topContributorsPerFrame    keys kept per frame, default 5
 * @param calibratedCallNanos        measured cost of one timed begin/end pair through {@code Probe}, guard included
 * @param calibratedVertexNanos      measured cost of one vertex-counting call through {@code Probe} (spec addendum §P4.A5)
 * @param calibratedAttributionNanos measured cost of one attribution-only pair: an event handler's timing proxy,
 *                                   which also stands for a HUD layer's and a packet's (spec addendum §P5.C2)
 */
public record SessionConfig(long durationNanos,
                            double hitchMedianMultiplier,
                            long hitchMinExcessNanos,
                            int topContributorsPerFrame,
                            long calibratedCallNanos,
                            long calibratedVertexNanos,
                            long calibratedAttributionNanos) {

    public SessionConfig {
        if (durationNanos <= 0) {
            throw new IllegalArgumentException("durationNanos must be positive");
        }
        if (topContributorsPerFrame < 0) {
            throw new IllegalArgumentException("topContributorsPerFrame must be >= 0");
        }
    }

    /**
     * Settings with no figure of their own for attribution-only pairs, which are then charged what a timed pair
     * costs. For tests and fixtures that build a config by hand.
     */
    public SessionConfig(long durationNanos, double hitchMedianMultiplier, long hitchMinExcessNanos,
                         int topContributorsPerFrame, long calibratedCallNanos, long calibratedVertexNanos) {
        this(durationNanos, hitchMedianMultiplier, hitchMinExcessNanos, topContributorsPerFrame, calibratedCallNanos,
                calibratedVertexNanos, calibratedCallNanos);
    }

    public static SessionConfig ofSeconds(int seconds, double hitchMedianMultiplier, int hitchMinExcessMs,
                                          int topContributorsPerFrame, long calibratedCallNanos,
                                          long calibratedVertexNanos, long calibratedAttributionNanos) {
        return new SessionConfig(seconds * 1_000_000_000L, hitchMedianMultiplier, hitchMinExcessMs * 1_000_000L,
                topContributorsPerFrame, calibratedCallNanos, calibratedVertexNanos, calibratedAttributionNanos);
    }

    public double durationSeconds() {
        return durationNanos / 1e9;
    }
}
