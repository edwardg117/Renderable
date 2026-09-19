package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CalibrationTest {

    @Test
    void fakeClockGivesExactPerPairCost() {
        long[] t = {0};
        // Each clock read advances 7 ns. begin + end = 2 reads = 14 ns per pair.
        long perPair = Calibration.measureCallNanos(() -> t[0] += 7);
        assertEquals(14, perPair);
    }

    @Test
    void realClockGivesSaneNumber() {
        long perPair = Calibration.measureCallNanos(System::nanoTime);
        assertTrue(perPair >= 0 && perPair < 10_000, "per-pair cost was " + perPair + " ns");
    }
}
