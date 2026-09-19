package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HitchDetectorTest {
    static final long MS = 1_000_000L;

    static FrameRecord frame(int i, double totalMs) {
        return new FrameRecord(i, 0, (long) (totalMs * MS), 0, 0, new long[Bucket.COUNT], 0, 0, List.of());
    }

    @Test
    void thresholdIsTheLargerOfMultiplierAndMinimumExcess() {
        assertEquals((long) (33.2 * MS), HitchDetector.threshold((long) (16.6 * MS), 2.0, 16 * MS));
        assertEquals(23 * MS, HitchDetector.threshold(7 * MS, 2.0, 16 * MS));
    }

    @Test
    void detectReturnsFramesOverThresholdWorstFirst() {
        List<FrameRecord> frames = List.of(frame(0, 16), frame(1, 40), frame(2, 16), frame(3, 90), frame(4, 32));
        List<FrameRecord> hitches = HitchDetector.detect(frames, 32 * MS);
        assertEquals(2, hitches.size());
        assertEquals(3, hitches.get(0).index());
        assertEquals(1, hitches.get(1).index());
    }

    @Test
    void rollingCountUsesMedianOfRecentWindow() {
        List<FrameRecord> frames = List.of(frame(0, 16), frame(1, 16), frame(2, 16), frame(3, 50), frame(4, 16));
        assertEquals(1, HitchDetector.countRolling(frames, 120, 2.0, 16 * MS));
        assertEquals(0, HitchDetector.countRolling(List.of(), 120, 2.0, 16 * MS));
    }
}
