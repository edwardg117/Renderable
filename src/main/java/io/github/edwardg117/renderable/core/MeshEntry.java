package io.github.edwardg117.renderable.core;

/** One row of the Blocks or Sections table. Rates are derived against the session's sampled seconds. */
public record MeshEntry(ProfileKey key, long calls, long nanos) {

    /** Average worker-thread microseconds per call (per rebuild for a section). */
    public double usPerCall() {
        return calls == 0 ? 0 : nanos / 1000.0 / calls;
    }

    /**
     * Worker-thread milliseconds spent on this key per second of session. Mesh work runs on several Sodium
     * worker threads in parallel, so this is a parallel-work rate that can exceed 1000; it is not a share of
     * frame time (spec §4.3).
     */
    public double msPerSecond(double seconds) {
        return seconds <= 0 ? 0 : nanos / 1e6 / seconds;
    }

    /** Calls (rebuilds for a section) per second of session. */
    public double callsPerSecond(double seconds) {
        return seconds <= 0 ? 0 : calls / seconds;
    }
}
