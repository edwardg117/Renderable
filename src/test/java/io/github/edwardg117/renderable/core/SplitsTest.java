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
        Accumulator a = new Accumulator(new ParticleKey("x"));
        a.calls = 60;
        a.tickCalls = 40;
        RankedEntry e = RankedEntry.of(a);
        assertEquals(3.0, e.callsPerFrame(20), 1e-9);
        assertEquals(0.0, e.callsPerFrame(0), 1e-9);
    }

    @Test
    void newComponentsAppearWhenTheyHaveTime() {
        long[] b = new long[Bucket.COUNT];
        b[Bucket.HUD.ordinal()] = 2 * MS;
        b[Bucket.ENTITY_TICK.ordinal()] = 1 * MS;
        long[] named = new long[Window.COUNT];
        named[Window.RENDER.ordinal()] = 1 * MS;
        named[Window.TICK.ordinal()] = 400_000;
        named[Window.TASKS.ordinal()] = 600_000;
        FrameRecord f = new FrameRecord(0, 0, 20 * MS, 2 * MS, 5 * MS, 2 * MS, 3 * MS, b, named, 0, 0, List.of());

        assertEquals("tick 2.0 [entity tick 1.0, mods 0.4] | hud 2.0 | other render 3.0 [mods 1.0]"
                + " | tasks 2.0 [mods 0.6] | frame cap 3.0 | swap 8.0", Splits.frame(f));
        assertEquals("tick 2.00 [entity tick 1.00, mods 0.40] | hud 2.00 | other render 3.00 [mods 1.00]"
                + " | tasks 2.00 [mods 0.60] | frame cap 3.00 | swap/wait 8.00", Splits.average(FrameStats.of(List.of(f))));
    }

    @Test
    void tasksAndTheFrameCapAppearIndependently() {
        FrameRecord tasksOnly = new FrameRecord(0, 0, 20 * MS, 2 * MS, 5 * MS, 2 * MS, 0, new long[Bucket.COUNT],
                new long[Window.COUNT], 0, 0, List.of());
        FrameRecord capOnly = new FrameRecord(0, 0, 20 * MS, 2 * MS, 5 * MS, 0, 3 * MS, new long[Bucket.COUNT],
                new long[Window.COUNT], 0, 0, List.of());

        assertEquals("tick 2.0 | other render 5.0 | tasks 2.0 | swap 11.0", Splits.frame(tasksOnly));
        assertEquals("tick 2.0 | other render 5.0 | frame cap 3.0 | swap 10.0", Splits.frame(capOnly));
        assertEquals("tick 2.00 | other render 5.00 | tasks 2.00 | swap/wait 11.00",
                Splits.average(FrameStats.of(List.of(tasksOnly))));
        assertEquals("tick 2.00 | other render 5.00 | frame cap 3.00 | swap/wait 10.00",
                Splits.average(FrameStats.of(List.of(capOnly))));
    }

    @Test
    void aTickBracketCanHoldOnlyNamedTime() {
        long[] named = new long[Window.COUNT];
        named[Window.TICK.ordinal()] = 400_000;
        FrameRecord f = new FrameRecord(0, 0, 20 * MS, 2 * MS, 5 * MS, 0, 0, new long[Bucket.COUNT], named, 0, 0,
                List.of());

        assertEquals("tick 2.0 [mods 0.4] | other render 5.0 | swap 13.0", Splits.frame(f));
        assertEquals("tick 2.00 [mods 0.40] | other render 5.00 | swap/wait 13.00",
                Splits.average(FrameStats.of(List.of(f))));
    }
}
