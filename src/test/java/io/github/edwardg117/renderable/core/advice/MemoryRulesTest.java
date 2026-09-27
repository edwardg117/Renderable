package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Environment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryRulesTest {

    static List<Recommendation> run(Facts f, java.util.function.BiConsumer<Facts, List<Recommendation>> rule) {
        List<Recommendation> out = new ArrayList<>();
        rule.accept(f, out);
        return out;
    }

    static FactsBuilder machine(String physicalMib, String heapMib) {
        return new FactsBuilder().env(Environment.PHYSICAL_MEMORY_MIB, physicalMib).env("maxHeapMiB", heapMib);
    }

    static FactsBuilder machine(String physicalMib, String heapMib, String liveMib) {
        return machine(physicalMib, heapMib).env(Environment.LIVE_HEAP_AFTER_GC_MIB, liveMib);
    }

    @Test
    void heapVsRamAdvisesTheLargestSafeAllocation() {
        Recommendation a = run(machine("16384", "14336").build(), MemoryRules::heapVsRam).get(0);
        assertEquals(Recommendation.Kind.NOTE, a.kind());
        assertEquals("14 GiB is allocated on a machine with 16 GiB, which leaves 2 GiB for Windows, drivers and the"
                + " game's memory outside the heap.", a.finding());
        assertEquals("Allocate at most 12 GiB, this machine's memory less 4 GiB.", a.action());

        Recommendation stalled = run(machine("16384", "14336").gc(3, 900, 600).build(), MemoryRules::heapVsRam).get(0);
        assertEquals(Recommendation.Kind.STUTTER, stalled.kind(), "a pause of half a second or more is the symptom");
        assertEquals(600, stalled.worstMs(), 1e-9);
        assertEquals(0.9, stalled.msPerFrame(), 1e-9);

        assertTrue(run(machine("32768", "8192").build(), MemoryRules::heapVsRam).isEmpty(), "24 GiB to spare");

        assertEquals("This machine is short of memory for this pack: close other programs while playing.",
                run(machine("4096", "3072").build(), MemoryRules::heapVsRam).get(0).action(), "not a whole GiB to spare");
    }

    @Test
    void heapVsRamSaysTheMachineIsShortWhenTheLiveDataNeedsMore() {
        Recommendation a = run(machine("16384", "14336").env(Environment.LIVE_HEAP_AFTER_GC_MIB, "10240").build(),
                MemoryRules::heapVsRam).get(0);
        assertEquals("The live data alone is 10 GiB, so this machine is short of memory for this pack: close other"
                + " programs while playing.", a.action());
    }

    @Test
    void heapVsRamCountsPhysicalMemoryInWholeGiB() {
        // What machines report: the installed memory less what the firmware and an integrated GPU reserve.
        // {reported MiB, the whole GiB it counts as}
        long[][] machines = {
                {16384, 16}, {16173, 16}, {16300, 16}, {16383, 16},   // 16 GB PCs, and an exact 16 GiB
                {32768, 32}, {32600, 32},                              // 32 GB
                {8192, 8}, {8034, 8},                                  // 8 GB
                {12288, 12}, {12200, 12}, {6144, 6}, {6050, 6},
                {63100, 62},                                           // the dev machine: 64 GB less 2.4 GiB reserved
                {14234, 14},                                           // 16 GB with 2 GB for an integrated GPU
                {15871, 15}, {15872, 16}};                             // either side of half a GiB
        for (long[] m : machines) {
            String physical = Long.toString(m[0]);
            long whole = m[1];
            assertTrue(run(machine(physical, Long.toString((whole - 4) * 1024)).build(), MemoryRules::heapVsRam).isEmpty(),
                    physical + ": 4 GiB left is enough");
            Recommendation a = run(machine(physical, Long.toString((whole - 3) * 1024)).build(), MemoryRules::heapVsRam).get(0);
            assertEquals((whole - 3) + " GiB is allocated on a machine with " + whole + " GiB, which leaves 3 GiB for"
                    + " Windows, drivers and the game's memory outside the heap.", a.finding(), physical);
            assertEquals("Allocate at most " + (whole - 4) + " GiB, this machine's memory less 4 GiB.", a.action(), physical);
        }
    }

    @Test
    void heapVsRamComparesTheLiveDataWithTheHeapItAdvises() {
        assertEquals("Allocate at most 12 GiB, this machine's memory less 4 GiB.",
                run(machine("16384", "14336", "9830").build(), MemoryRules::heapVsRam).get(0).action(),
                "9,830 MiB is under 80% of the 12 GiB cap: heap-small would be quiet there");
        assertEquals("The live data alone is 9.6 GiB, so this machine is short of memory for this pack: close other"
                + " programs while playing.", run(machine("16384", "14336", "9831").build(), MemoryRules::heapVsRam).get(0).action());
        assertEquals("Allocate at most 12 GiB, this machine's memory less 4 GiB.",
                run(machine("16173", "14336", "8192").build(), MemoryRules::heapVsRam).get(0).action(),
                "a real 16 GB PC: 8 GiB of live data is 67% of the 12 GiB it can spare");
    }

    @Test
    void heapVsRamAndHeapSmallAgreeOnWhatTheMachineCanHold() {
        // A 16 GB PC can spare 12 GiB, and 8.5 GiB of live data is 71% of it, whatever is allocated now.
        assertEquals("Allocate at most 12 GiB, this machine's memory less 4 GiB.",
                run(machine("16173", "16384", "8704").build(), MemoryRules::heapVsRam).get(0).action(), "16 GiB allocated");
        assertEquals("Allocate 12 GiB, the most this machine can spare (1.6 x the live data would be 14 GiB).",
                run(machine("16173", "10240", "8704").build(), MemoryRules::heapSmall).get(0).action(), "10 GiB allocated");
    }

    @Test
    void heapVsRamEdgesAndMissingData() {
        assertTrue(run(machine("16384", "12288").build(), MemoryRules::heapVsRam).isEmpty(), "exactly 4 GiB left");
        assertEquals(1, run(machine("16384", "12289").build(), MemoryRules::heapVsRam).size(), "a MiB under 4 GiB");

        Recommendation note = run(machine("16384", "14336").gc(3, 900, 499).build(), MemoryRules::heapVsRam).get(0);
        assertEquals(Recommendation.Kind.NOTE, note.kind(), "under half a second");
        assertEquals(0, note.msPerFrame(), 1e-9, "a note carries no figures");
        assertEquals(0, note.worstMs(), 1e-9);
        assertEquals(Recommendation.Kind.STUTTER,
                run(machine("16384", "14336").gc(3, 900, 500).build(), MemoryRules::heapVsRam).get(0).kind(), "half a second");

        assertEquals("20 GiB is allocated on a machine with 16 GiB, which leaves 0 GiB for Windows, drivers and the"
                + " game's memory outside the heap.", run(machine("16384", "20480").build(), MemoryRules::heapVsRam).get(0).finding());
        assertEquals("Allocate at most 1 GiB, this machine's memory less 4 GiB.",
                run(machine("5120", "3072").build(), MemoryRules::heapVsRam).get(0).action(), "one whole GiB to spare");

        assertTrue(run(new FactsBuilder().env("maxHeapMiB", "14336").build(), MemoryRules::heapVsRam).isEmpty(), "no physical");
        assertTrue(run(machine("unknown", "14336").build(), MemoryRules::heapVsRam).isEmpty(), "physical unknown");
        assertTrue(run(new FactsBuilder().env(Environment.PHYSICAL_MEMORY_MIB, "16384").build(), MemoryRules::heapVsRam).isEmpty(),
                "no heap");
    }

    @Test
    void heapSmallSuggestsOnePointSixTimesTheLiveData() {
        Recommendation a = run(machine("32768", "8192").env(Environment.LIVE_HEAP_AFTER_GC_MIB, "7066")
                .gc(10, 183, 30).build(), MemoryRules::heapSmall).get(0);
        assertEquals(Recommendation.Kind.FRAME_RATE, a.kind());
        assertEquals("After every collection at least 6.9 of 8 GiB was still in use, so the collector runs often.", a.finding());
        assertEquals("Allocate 12 GiB (1.6 x the live data, rounded up to a whole GiB).", a.action());
        assertEquals(0.183, a.msPerFrame(), 1e-9, "the session's GC time per frame");

        Recommendation cannot = run(machine("12288", "8192").env(Environment.LIVE_HEAP_AFTER_GC_MIB, "7066").build(),
                MemoryRules::heapSmall).get(0);
        assertEquals("This machine cannot spare 12 GiB (1.6 x the live data): close other programs, or lower render"
                + " distance so the game needs less.", cannot.action());

        assertTrue(run(machine("32768", "8192").env(Environment.LIVE_HEAP_AFTER_GC_MIB, "4096").build(),
                MemoryRules::heapSmall).isEmpty(), "half full");
    }

    @Test
    void heapSmallSuggestsTheMostTheMachineCanSpareWhenThatIsEnough() {
        assertEquals("Allocate 12 GiB, the most this machine can spare (1.6 x the live data would be 13 GiB).",
                run(machine("16384", "10240", "8192").build(), MemoryRules::heapSmall).get(0).action(),
                "8 GiB of live data is 67% of 12 GiB, under the 80% trigger");
        assertEquals("Allocate 10 GiB, the most this machine can spare (1.6 x the live data would be 13 GiB).",
                run(machine("14336", "9216", "8191").build(), MemoryRules::heapSmall).get(0).action(), "just under 80% of 10 GiB");
        assertEquals("This machine cannot spare 13 GiB (1.6 x the live data): close other programs, or lower render"
                + " distance so the game needs less.", run(machine("14336", "9216", "8192").build(), MemoryRules::heapSmall)
                .get(0).action(), "80% of 10 GiB: the most it can spare would still be nearly full");
    }

    @Test
    void heapSmallEdgesAndMissingData() {
        assertEquals("Allocate 12 GiB (1.6 x the live data, rounded up to a whole GiB).",
                run(machine("16384", "8192", "7066").build(), MemoryRules::heapSmall).get(0).action(), "12 GiB is what 16 GiB can spare");
        assertEquals("Allocate 12 GiB (1.6 x the live data, rounded up to a whole GiB).",
                run(machine("16173", "8192", "7066").build(), MemoryRules::heapSmall).get(0).action(), "a real 16 GB PC");
        assertEquals("Allocate 12 GiB (1.6 x the live data, rounded up to a whole GiB).",
                run(new FactsBuilder().env("maxHeapMiB", "8192").env(Environment.LIVE_HEAP_AFTER_GC_MIB, "7066").build(),
                        MemoryRules::heapSmall).get(0).action(), "physical memory unknown: nothing caps it");

        assertEquals(1, run(machine("32768", "10240", "8192").build(), MemoryRules::heapSmall).size(), "exactly 80%");
        assertTrue(run(machine("32768", "10240", "8191").build(), MemoryRules::heapSmall).isEmpty(), "under 80%");
        assertTrue(run(machine("32768", "0", "100").build(), MemoryRules::heapSmall).isEmpty(), "no heap to be full");
        assertTrue(run(machine("32768", "8192").build(), MemoryRules::heapSmall).isEmpty(), "no collection ran");
    }

    @Test
    void gcPausesRecommendZgcWithTheFlagsTheJavaVersionNeeds() {
        FactsBuilder g1 = new FactsBuilder().env(Environment.GC, "G1").env("java", "21.0.3 (Eclipse Adoptium)").gc(14, 610, 120);
        Recommendation a = run(g1.build(), MemoryRules::gcPauses).get(0);
        assertEquals(Recommendation.Kind.STUTTER, a.kind());
        assertEquals("Garbage collection paused the game for up to 120 ms (14 collections, 610 ms in total).", a.finding());
        assertEquals("On Java 21, add -XX:+UseZGC -XX:+ZGenerational to the JVM arguments (leave out -XX:+ZGenerational"
                + " on Java 23 or later: from Java 26 the game will not start with it). ZGC pauses for well under a"
                + " millisecond but uses more CPU, and more memory for the same data, so keep a quarter of the heap free.",
                a.action());
        assertEquals(120, a.worstMs(), 1e-9);

        Recommendation newer = run(new FactsBuilder().env(Environment.GC, "G1").env("java", "23.0.1").gc(14, 610, 120).build(),
                MemoryRules::gcPauses).get(0);
        assertTrue(newer.action().startsWith("On Java 23, add -XX:+UseZGC to"), newer.action());

        assertTrue(run(new FactsBuilder().env(Environment.GC, "ZGC generational").env("java", "21").gc(14, 610, 120).build(),
                MemoryRules::gcPauses).isEmpty(), "already on ZGC");
        assertTrue(run(new FactsBuilder().env(Environment.GC, "G1").env("java", "21").gc(14, 400, 40).build(),
                MemoryRules::gcPauses).isEmpty(), "under 50 ms");
        assertTrue(run(new FactsBuilder().env("java", "21").gc(14, 610, 120).build(), MemoryRules::gcPauses).isEmpty(),
                "never fires on missing data: the collector is not known");
    }

    @Test
    void gcPausesSwapsOutACollectorTheArgumentsChoose() {
        // The Minecraft launcher's default arguments choose G1, and the JVM will not start with two collectors chosen.
        FactsBuilder launcher = new FactsBuilder().env(Environment.GC, "G1").env("java", "21.0.3 (Eclipse Adoptium)")
                .env(Environment.JVM_FLAGS, "-Xmx8G -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1NewSizePercent=20"
                        + " -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M")
                .gc(14, 610, 120);
        assertEquals("On Java 21, replace -XX:+UseG1GC with -XX:+UseZGC -XX:+ZGenerational in the JVM arguments (leave"
                + " out -XX:+ZGenerational on Java 23 or later: from Java 26 the game will not start with it). ZGC pauses"
                + " for well under a millisecond but uses more CPU, and more memory for the same data, so keep a quarter"
                + " of the heap free.", run(launcher.build(), MemoryRules::gcPauses).get(0).action());
        assertTrue(run(new FactsBuilder().env(Environment.GC, "Parallel").env("java", "25").env(Environment.JVM_FLAGS,
                        "-Xmx8G -XX:+UseParallelGC").gc(14, 610, 120).build(), MemoryRules::gcPauses).get(0).action()
                .startsWith("On Java 25, replace -XX:+UseParallelGC with -XX:+UseZGC in the JVM arguments. ZGC"));
        assertTrue(run(new FactsBuilder().env(Environment.GC, "G1").env("java", "21").env(Environment.JVM_FLAGS, "-Xmx8G")
                        .gc(14, 610, 120).build(), MemoryRules::gcPauses).get(0).action()
                .startsWith("On Java 21, add -XX:+UseZGC -XX:+ZGenerational to the JVM arguments (leave out"), "G1 by default");
    }

    @Test
    void gcPausesEdges() {
        for (String gc : new String[] {"G1", "Parallel", "Serial"}) {
            assertEquals(1, run(new FactsBuilder().env(Environment.GC, gc).env("java", "21").gc(14, 610, 120).build(),
                    MemoryRules::gcPauses).size(), gc);
        }
        for (String gc : new String[] {"ZGC", "Shenandoah", "unknown", "PS Scavenge, Foo"}) {
            assertTrue(run(new FactsBuilder().env(Environment.GC, gc).env("java", "21").gc(14, 610, 120).build(),
                    MemoryRules::gcPauses).isEmpty(), gc);
        }
        Recommendation fifty = run(new FactsBuilder().env(Environment.GC, "G1").env("java", "21").gc(1, 50, 50).build(),
                MemoryRules::gcPauses).get(0);
        assertEquals("Garbage collection paused the game for up to 50 ms (1 collection, 50 ms in total).", fifty.finding());
        assertEquals(0.05, fifty.msPerFrame(), 1e-9, "the session's GC time per frame");
        assertTrue(run(new FactsBuilder().env(Environment.GC, "G1").env("java", "21").gc(1, 49, 49).build(),
                MemoryRules::gcPauses).isEmpty(), "49 ms");

        assertTrue(run(new FactsBuilder().env(Environment.GC, "G1").env("java", "22.0.2").gc(14, 610, 120).build(),
                MemoryRules::gcPauses).get(0).action().startsWith("On Java 22, add -XX:+UseZGC -XX:+ZGenerational to"));
        assertFalse(run(new FactsBuilder().env(Environment.GC, "G1").env("java", "25").gc(14, 610, 120).build(),
                MemoryRules::gcPauses).get(0).action().contains("ZGenerational"), "generational by default from Java 23");
        assertTrue(run(new FactsBuilder().env(Environment.GC, "G1").gc(14, 610, 120).build(), MemoryRules::gcPauses).isEmpty(),
                "Java version unknown");
    }
}
