package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

class CalibrationTest {

    @Test
    void aFakeClockGivesTheExactCostOfEachCall() {
        long[] t = {0};
        LongSupplier clock = () -> t[0] += 7;
        // Each call reads the clock twice, 14 ns. The read that ends the loop adds 7 ns over 10,000 calls,
        // which floors away.
        assertEquals(14, Calibration.nanosPerCall(clock, () -> {
            clock.getAsLong();
            clock.getAsLong();
        }));
    }

    @Test
    void aClockThatGoesBackwardsIsClampedAndACallThatReadsNothingIsFree() {
        long[] backwards = {0};
        assertEquals(0, Calibration.nanosPerCall(() -> backwards[0] -= 1_000_000, () -> {
        }), "a clock that goes backwards is clamped, not reported as negative");
        long[] forwards = {0};
        assertEquals(0, Calibration.nanosPerCall(() -> forwards[0] += 3, () -> {
        }), "no reads inside the loop, so the two around it floor to zero");
    }

    @Test
    void theRealClockGivesASaneNumberForARealSessionPair() {
        Session s = new Session(new SessionConfig(Long.MAX_VALUE, 2.0, 0, 5, 0, 0), System::nanoTime, 0);
        Accumulator a = s.accumulator(new EntityKey("renderable:calibration", 0, ""));
        s.onRenderPre(0, 0);
        long perPair = Calibration.nanosPerCall(System::nanoTime, () -> {
            s.begin(a, Bucket.ENTITY);
            s.end();
        });
        assertTrue(perPair >= 0 && perPair < 10_000, "per-pair cost was " + perPair + " ns");
    }
}
