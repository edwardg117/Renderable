package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GcNamesTest {
    static final List<String> G1 = List.of("G1 Young Generation", "G1 Concurrent GC", "G1 Old Generation");
    static final List<String> ZGC = List.of("ZGC Cycles", "ZGC Pauses");
    static final List<String> GENERATIONAL_ZGC =
            List.of("ZGC Minor Cycles", "ZGC Minor Pauses", "ZGC Major Cycles", "ZGC Major Pauses");
    static final List<String> SHENANDOAH = List.of("Shenandoah Pauses", "Shenandoah Cycles");

    @Test
    void pauseBeansAreCountedAndTheOthersReportTheHeap() {
        assertEquals(G1, GcNames.countedBeans(G1), "G1's concurrent bean counts the Remark and Cleanup pauses");
        assertEquals(List.of("ZGC Pauses"), GcNames.countedBeans(ZGC));
        assertEquals(List.of("ZGC Minor Pauses", "ZGC Major Pauses"), GcNames.countedBeans(GENERATIONAL_ZGC));
        assertEquals(List.of("Shenandoah Pauses"), GcNames.countedBeans(SHENANDOAH));
        assertEquals(List.of("PS Scavenge", "PS MarkSweep"), GcNames.countedBeans(List.of("PS Scavenge", "PS MarkSweep")));

        // A pause bean's notification reports no heap in use, so the live-heap figure listens to the others.
        assertEquals(G1, GcNames.liveHeapBeans(G1));
        assertEquals(List.of("ZGC Cycles"), GcNames.liveHeapBeans(ZGC));
        assertEquals(List.of("ZGC Minor Cycles", "ZGC Major Cycles"), GcNames.liveHeapBeans(GENERATIONAL_ZGC));
        assertEquals(List.of("Shenandoah Cycles"), GcNames.liveHeapBeans(SHENANDOAH));

        // Once any bean is a pause bean, only pause beans count, whatever the others are called.
        List<String> mixed = List.of("X Cycles", "X Pauses", "X Old Generation");
        assertEquals(List.of("X Pauses"), GcNames.countedBeans(mixed));
        assertEquals(List.of("X Cycles", "X Old Generation"), GcNames.liveHeapBeans(mixed));
    }

    @Test
    void theCollectorIsNamedFromItsBeans() {
        assertEquals("G1", GcNames.collector(G1));
        assertEquals("ZGC", GcNames.collector(ZGC));
        assertEquals("ZGC generational", GcNames.collector(GENERATIONAL_ZGC));
        assertEquals("Shenandoah", GcNames.collector(SHENANDOAH));
        assertEquals("Parallel", GcNames.collector(List.of("PS Scavenge", "PS MarkSweep")));
        assertEquals("Serial", GcNames.collector(List.of("Copy", "MarkSweepCompact")));
        assertEquals("Epsilon, Other", GcNames.collector(List.of("Epsilon", "Other")), "anything else is listed as it is");
    }
}
