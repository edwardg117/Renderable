package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Spec §3.3. */
public final class HitchDetector {

    private HitchDetector() {
    }

    public static long threshold(long medianNanos, double multiplier, long minExcessNanos) {
        return Math.max((long) (medianNanos * multiplier), medianNanos + minExcessNanos);
    }

    /** Frames strictly over the threshold, longest first. */
    public static List<FrameRecord> detect(List<FrameRecord> frames, long thresholdNanos) {
        ArrayList<FrameRecord> out = new ArrayList<>();
        for (FrameRecord f : frames) {
            if (f.totalNanos() > thresholdNanos) out.add(f);
        }
        out.sort(Comparator.comparingLong(FrameRecord::totalNanos).reversed());
        return List.copyOf(out);
    }

    /**
     * Live count for the HUD: threshold from the median of the last {@code window} frames, applied to all frames so far.
     */
    public static int countRolling(List<FrameRecord> frames, int window, double multiplier, long minExcessNanos) {
        int n = frames.size();
        if (n == 0) return 0;
        int from = Math.max(0, n - window);
        long[] recent = new long[n - from];
        for (int i = from; i < n; i++) recent[i - from] = frames.get(i).totalNanos();
        Arrays.sort(recent);
        long threshold = threshold(FrameStats.percentile(recent, 50), multiplier, minExcessNanos);
        int count = 0;
        for (FrameRecord f : frames) {
            if (f.totalNanos() > threshold) count++;
        }
        return count;
    }
}
