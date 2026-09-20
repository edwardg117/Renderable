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

    @Test
    void vertexCalibrationIsANonNegativeNumberOfNanoseconds() {
        long[] clock = {0};
        // A clock that advances 3 ns per read: each vertex() call reads it zero times, so the loop's
        // elapsed time is the two reads around it, which divided by the sample count floors to zero.
        assertEquals(0, Calibration.measureVertexNanos(() -> clock[0] += 3),
                "no clock reads inside the loop, so the two reads around it floor to zero");
        long[] backwards = {0};
        assertEquals(0, Calibration.measureVertexNanos(() -> backwards[0] -= 1_000_000),
                "a clock that goes backwards is clamped, not reported as negative");

        long real = Calibration.measureVertexNanos(System::nanoTime);
        assertTrue(real >= 0 && real < 10_000, "a vertex count is a field increment, not a microsecond: " + real);
    }
}
