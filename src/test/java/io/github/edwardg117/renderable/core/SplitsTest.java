package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SplitsTest {
    static final long MS = 1_000_000L;

    static FrameRecord frame(long totalMs10, long tickMs10, long renderMs10, long... bucketMs10) {
        long[] b = new long[Bucket.COUNT];
        for (int i = 0; i + 1 < bucketMs10.length; i += 2) b[(int) bucketMs10[i]] = bucketMs10[i + 1] * MS / 10;
        return new FrameRecord(0, 0, totalMs10 * MS / 10, tickMs10 * MS / 10, renderMs10 * MS / 10, b, 0, 0, List.of());
    }

    @Test
    void frameSplitNestsTickBucketsUnderTick() {
        FrameRecord f = frame(200, 20, 150,
                Bucket.BLOCK_ENTITY.ordinal(), 50, Bucket.LIGHT.ordinal(), 5,
                Bucket.BE_TICK.ordinal(), 8, Bucket.ENTITY_TICK.ordinal(), 4);
        assertEquals("tick 2.0 [be tick 0.8, entity tick 0.4] | block entity 5.0 | light 0.5 | other render 9.5 | swap 3.0",
                Splits.frame(f));
    }

    @Test
    void frameSplitOmitsBracketsWhenNoTickBucketHasData() {
        FrameRecord f = frame(160, 10, 100, Bucket.ENTITY.ordinal(), 20);
        assertEquals("tick 1.0 | entity 2.0 | other render 8.0 | swap 5.0", Splits.frame(f));
    }

    @Test
    void averageSplitUsesTwoDecimalsAndSkipsEmptyRenderBuckets() {
        FrameStats st = FrameStats.of(List.of(
                frame(200, 20, 150, Bucket.BLOCK_ENTITY.ordinal(), 50, Bucket.PARTICLE_TICK.ordinal(), 10),
                frame(200, 20, 150, Bucket.BLOCK_ENTITY.ordinal(), 30, Bucket.PARTICLE_TICK.ordinal(), 10)));
        assertEquals("tick 2.00 [particle tick 1.00] | block entity 4.00 | other render 11.00 | swap/wait 3.00",
                Splits.average(st));
    }

    @Test
    void callsPerFrameIsTheAverageLiveCount() {
        RankedEntry e = new RankedEntry("x", null, "x", ProfileKey.Category.PARTICLE, 1, 60, 40, 0, 0, 0, 0, 0);
        assertEquals(3.0, e.callsPerFrame(20), 1e-9);
        assertEquals(0.0, e.callsPerFrame(0), 1e-9);
    }
}
