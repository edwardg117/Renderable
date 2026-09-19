package io.github.edwardg117.renderable.core;

/**
 * Immutable settings for one session. Durations are nanoseconds so the core never converts units.
 *
 * @param durationNanos           how long to sample
 * @param hitchMedianMultiplier   spec §3.3, default 2.0
 * @param hitchMinExcessNanos     spec §3.3, default 16 ms
 * @param topContributorsPerFrame keys kept per frame, default 5
 * @param calibratedCallNanos     measured cost of one begin/end pair, for the overhead estimate
 */
public record SessionConfig(long durationNanos,
                            double hitchMedianMultiplier,
                            long hitchMinExcessNanos,
                            int topContributorsPerFrame,
                            long calibratedCallNanos) {

    public SessionConfig {
        if (durationNanos <= 0) {
            throw new IllegalArgumentException("durationNanos must be positive");
        }
        if (topContributorsPerFrame < 0) {
            throw new IllegalArgumentException("topContributorsPerFrame must be >= 0");
        }
    }

    public static SessionConfig ofSeconds(int seconds, double hitchMedianMultiplier, int hitchMinExcessMs,
                                          int topContributorsPerFrame, long calibratedCallNanos) {
        return new SessionConfig(seconds * 1_000_000_000L, hitchMedianMultiplier, hitchMinExcessMs * 1_000_000L,
                topContributorsPerFrame, calibratedCallNanos);
    }

    public double durationSeconds() {
        return durationNanos / 1e9;
    }
}
