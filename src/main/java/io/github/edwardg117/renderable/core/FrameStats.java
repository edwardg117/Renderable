package io.github.edwardg117.renderable.core;

import java.util.Arrays;
import java.util.List;

/** Whole-session frame statistics. Percentiles use nearest-rank on the sorted frame totals. */
public record FrameStats(int frames,
                         long totalNanos,
                         long avgNanos,
                         long p50Nanos,
                         long p95Nanos,
                         long p99Nanos,
                         long maxNanos,
                         long avgTickNanos,
                         long avgRenderNanos,
                         long avgSwapNanos,
                         long[] avgBucketNanos,
                         long avgOtherRenderNanos,
                         long avgOtherTickNanos,
                         int gcCount,
                         long gcMillis) {

    public static FrameStats of(List<FrameRecord> frames) {
        int n = frames.size();
        long[] avgBuckets = new long[Bucket.COUNT];
        if (n == 0) {
            return new FrameStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, avgBuckets, 0, 0, 0, 0);
        }

        long[] sorted = new long[n];
        long total = 0, tick = 0, render = 0, swap = 0, otherRender = 0, otherTick = 0, gcMillis = 0;
        int gcCount = 0;
        long[] bucketSums = new long[Bucket.COUNT];

        for (int i = 0; i < n; i++) {
            FrameRecord f = frames.get(i);
            sorted[i] = f.totalNanos();
            total += f.totalNanos();
            tick += f.tickNanos();
            render += f.renderNanos();
            swap += f.swapNanos();
            otherRender += f.otherRenderNanos();
            otherTick += f.otherTickNanos();
            gcCount += f.gcCount();
            gcMillis += f.gcMillis();
            long[] b = f.buckets();
            for (int k = 0; k < Bucket.COUNT; k++) bucketSums[k] += b[k];
        }
        Arrays.sort(sorted);
        for (int k = 0; k < Bucket.COUNT; k++) avgBuckets[k] = bucketSums[k] / n;

        return new FrameStats(n, total, total / n,
                percentile(sorted, 50), percentile(sorted, 95), percentile(sorted, 99), sorted[n - 1],
                tick / n, render / n, swap / n, avgBuckets, otherRender / n, otherTick / n, gcCount, gcMillis);
    }

    /** Nearest-rank percentile of an ascending array. */
    static long percentile(long[] sorted, double p) {
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    public long avgBucket(Bucket b) {
        return avgBucketNanos[b.ordinal()];
    }

    public double avgFps() {
        return avgNanos == 0 ? 0 : 1e9 / avgNanos;
    }

    public double durationSeconds() {
        return totalNanos / 1e9;
    }
}
