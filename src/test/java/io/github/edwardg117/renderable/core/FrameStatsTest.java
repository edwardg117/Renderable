package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FrameStatsTest {
    static final long MS = 1_000_000L;

    static FrameRecord frame(int i, long totalMs, long tickMs, long renderMs, long blockEntityMs, int gc, long gcMs) {
        long[] b = new long[Bucket.COUNT];
        b[Bucket.BLOCK_ENTITY.ordinal()] = blockEntityMs * MS;
        return new FrameRecord(i, i * totalMs * MS, totalMs * MS, tickMs * MS, renderMs * MS, b, gc, gcMs, List.of());
    }

    @Test
    void percentilesUseNearestRank() {
        List<FrameRecord> frames = new ArrayList<>();
        for (int i = 1; i <= 10; i++) frames.add(frame(i - 1, 10L * i, 1, 5, 2, 0, 0));
        FrameStats st = FrameStats.of(frames);
        assertEquals(10, st.frames());
        assertEquals(550 * MS, st.totalNanos());
        assertEquals(55 * MS, st.avgNanos());
        assertEquals(50 * MS, st.p50Nanos());
        assertEquals(100 * MS, st.p95Nanos());
        assertEquals(100 * MS, st.p99Nanos());
        assertEquals(100 * MS, st.maxNanos());
        assertEquals(1e9 / (55 * MS), st.avgFps(), 1e-6);
        assertEquals(0.55, st.durationSeconds(), 1e-9);
    }

    @Test
    void averagesCoverTickRenderSwapBucketsAndGc() {
        List<FrameRecord> frames = List.of(
                frame(0, 20, 2, 10, 4, 0, 0),
                frame(1, 40, 4, 20, 8, 1, 6));
        FrameStats st = FrameStats.of(frames);
        assertEquals(3 * MS, st.avgTickNanos());
        assertEquals(15 * MS, st.avgRenderNanos());
        assertEquals(12 * MS, st.avgSwapNanos());
        assertEquals(6 * MS, st.avgBucket(Bucket.BLOCK_ENTITY));
        assertEquals(9 * MS, st.avgOtherRenderNanos());
        assertEquals(3 * MS, st.avgOtherTickNanos());
        assertEquals(1, st.gcCount());
        assertEquals(6, st.gcMillis());
    }

    @Test
    void emptyIsAllZero() {
        FrameStats st = FrameStats.of(List.of());
        assertEquals(0, st.frames());
        assertEquals(0, st.p99Nanos());
        assertEquals(0.0, st.avgFps());
        assertEquals(Bucket.COUNT, st.avgBucketNanos().length);
    }
}
