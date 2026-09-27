package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the JVM's garbage-collector bean names (spec addendum §P5.B4). ZGC and Shenandoah publish separate
 * beans for their concurrent cycles and for their pauses, and a cycle bean's time is concurrent work, not a
 * pause. So when a collector has pause beans, only those count. G1's three beans are all pauses: its
 * concurrent bean counts the Remark and Cleanup pauses.
 */
public final class GcNames {
    private static final String PAUSES = " Pauses";

    private GcNames() {
    }

    /** The beans whose time and count are pauses: those ending in " Pauses" if any do, otherwise all of them. */
    public static List<String> countedBeans(List<String> names) {
        List<String> pauses = new ArrayList<>();
        for (String n : names) {
            if (n.endsWith(PAUSES)) pauses.add(n);
        }
        return pauses.isEmpty() ? List.copyOf(names) : List.copyOf(pauses);
    }

    /**
     * The beans whose collection notifications report the heap in use afterwards: every bean but the pause beans.
     * A ZGC or Shenandoah pause notification reports 0 used in every pool (measured on JDK 21.0.11).
     */
    public static List<String> liveHeapBeans(List<String> names) {
        List<String> out = new ArrayList<>();
        for (String n : names) {
            if (!n.endsWith(PAUSES)) out.add(n);
        }
        return List.copyOf(out);
    }

    /** The collector in use, for the report: G1, ZGC, ZGC generational, Shenandoah, Parallel, Serial, or the names. */
    public static String collector(List<String> names) {
        if (names.stream().anyMatch(n -> n.startsWith("G1 "))) return "G1";
        if (names.stream().anyMatch(n -> n.startsWith("ZGC Minor") || n.startsWith("ZGC Major"))) return "ZGC generational";
        if (names.stream().anyMatch(n -> n.startsWith("ZGC"))) return "ZGC";
        if (names.stream().anyMatch(n -> n.startsWith("Shenandoah"))) return "Shenandoah";
        if (names.contains("PS Scavenge") || names.contains("PS MarkSweep")) return "Parallel";
        if (names.contains("Copy") || names.contains("MarkSweepCompact")) return "Serial";
        return String.join(", ", names);
    }
}
