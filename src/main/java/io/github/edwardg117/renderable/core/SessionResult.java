package io.github.edwardg117.renderable.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything a finished session knows. Immutable; produced by {@link Session#finish()}. */
public record SessionResult(SessionConfig config,
                            long wallStartMillis,
                            List<FrameRecord> frames,
                            Map<ProfileKey, Accumulator> accumulators,
                            FrameStats stats,
                            long hitchThresholdNanos,
                            List<FrameRecord> hitches,
                            long timedCalls,
                            double overheadEstimateMs) {

    public int frameCount() {
        return frames.size();
    }

    public long hitchTotalNanos() {
        long sum = 0;
        for (FrameRecord h : hitches) sum += h.totalNanos();
        return sum;
    }

    public double hitchSharePercent() {
        return stats.totalNanos() == 0 ? 0 : 100.0 * hitchTotalNanos() / stats.totalNanos();
    }

    /** Keys that appeared in any hitch's top contributors, in hitch order. */
    public Set<ProfileKey> hitchFlaggedKeys() {
        LinkedHashSet<ProfileKey> out = new LinkedHashSet<>();
        for (FrameRecord h : hitches) {
            for (TopEntry t : h.top()) out.add(t.key());
        }
        return out;
    }
}
