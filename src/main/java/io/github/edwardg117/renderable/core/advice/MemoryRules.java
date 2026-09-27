package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.advice.Recommendation.Audience;
import io.github.edwardg117.renderable.core.advice.Recommendation.Kind;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Memory allocation and launch arguments (spec addendum §P5.B2): one method per rule, in the table's order. */
final class MemoryRules {
    private static final double MIB_PER_GIB = 1024;
    /** Generational ZGC arrived in Java 21, turned on with {@code -XX:+ZGenerational}. */
    private static final int GENERATIONAL_ZGC_JAVA = 21;
    /** From Java 23 ZGC is generational by default, and {@code -XX:+ZGenerational} is deprecated. */
    private static final int GENERATIONAL_BY_DEFAULT_JAVA = 23;
    /** From Java 26 the JVM no longer knows {@code -XX:+ZGenerational}, and will not start with it. */
    private static final int ZGENERATIONAL_REFUSED_JAVA = 26;

    private MemoryRules() {
    }

    static void all(Facts f, List<Recommendation> out) {
        heapVsRam(f, out);
        heapSmall(f, out);
        gcPauses(f, out);
    }

    /** The swap-stall problem: a heap so large that the system and the game's off-heap memory are squeezed. */
    static void heapVsRam(Facts f, List<Recommendation> out) {
        double physical = physicalMib(f);
        double heap = f.number("maxHeapMiB");
        if (Double.isNaN(physical) || Double.isNaN(heap) || physical - heap >= Thresholds.RAM_RESERVE_MIB) return;
        long cap = largestHeapGib(physical);
        double live = f.number(Environment.LIVE_HEAP_AFTER_GC_MIB);
        String action;
        if (!Double.isNaN(live) && !roomy(live, cap)) {
            action = "The live data alone is " + Words.gib(live) + " GiB, so this machine is short of memory for this pack:"
                    + " close other programs while playing.";
        } else if (cap < 1) {
            action = "This machine is short of memory for this pack: close other programs while playing.";
        } else {
            action = String.format(Locale.ROOT, "Allocate at most %d GiB, this machine's memory less %s GiB.",
                    cap, Words.gib(Thresholds.RAM_RESERVE_MIB));
        }
        boolean stalled = f.maxFrameGcMs() >= Thresholds.RAM_STALL_GC_MS;
        out.add(new Recommendation("heap-vs-ram", stalled ? Kind.STUTTER : Kind.NOTE, Audience.PLAYER,
                "Allocated memory leaves too little for the system",
                String.format(Locale.ROOT, "%s GiB is allocated on a machine with %s GiB, which leaves %s GiB for Windows,"
                                + " drivers and the game's memory outside the heap.",
                        Words.gib(heap), Words.gib(physical), Words.gib(Math.max(0, physical - heap))),
                action, stalled ? gcPerFrame(f) : 0, stalled ? f.maxFrameGcMs() : 0));
    }

    /** A heap nearly full of live data even after collecting, so the collector runs often. */
    static void heapSmall(Facts f, List<Recommendation> out) {
        double live = f.number(Environment.LIVE_HEAP_AFTER_GC_MIB);
        double heap = f.number("maxHeapMiB");
        if (Double.isNaN(live) || Double.isNaN(heap) || heap <= 0 || live < Thresholds.LIVE_HEAP_SHARE * heap) return;
        long suggest = (long) Math.ceil(live * Thresholds.HEAP_GROWTH / MIB_PER_GIB);
        double physical = physicalMib(f);
        // With physical memory unknown, nothing caps the suggestion.
        long cap = Double.isNaN(physical) ? Long.MAX_VALUE : largestHeapGib(physical);
        String action;
        if (suggest <= cap) {
            action = String.format(Locale.ROOT, "Allocate %d GiB (%s x the live data, rounded up to a whole GiB).",
                    suggest, Thresholds.HEAP_GROWTH);
        } else if (roomy(live, cap)) {
            action = String.format(Locale.ROOT, "Allocate %d GiB, the most this machine can spare (%s x the live data"
                    + " would be %d GiB).", cap, Thresholds.HEAP_GROWTH, suggest);
        } else {
            action = String.format(Locale.ROOT, "This machine cannot spare %d GiB (%s x the live data): close other"
                    + " programs, or lower render distance so the game needs less.", suggest, Thresholds.HEAP_GROWTH);
        }
        out.add(new Recommendation("heap-small", Kind.FRAME_RATE, Audience.PLAYER, "The heap is nearly full",
                String.format(Locale.ROOT, "After every collection at least %s of %s GiB was still in use, so the collector runs often.",
                        Words.gib(live), Words.gib(heap)),
                action, gcPerFrame(f), 0));
    }

    /**
     * Long stop-the-world pauses under a collector known to have them; ZGC's are well under a millisecond. An
     * unknown collector, or none recorded, is missing data, so the rule stays quiet.
     */
    static void gcPauses(Facts f, List<Recommendation> out) {
        String gc = f.text(Environment.GC);
        if (!"G1".equals(gc) && !"Parallel".equals(gc) && !"Serial".equals(gc)) return;
        int java = Words.javaMajor(f.text("java"));
        if (f.maxFrameGcMs() < Thresholds.GC_PAUSE_MS || java < GENERATIONAL_ZGC_JAVA) return;
        String zgc = java < GENERATIONAL_BY_DEFAULT_JAVA ? "-XX:+UseZGC -XX:+ZGenerational" : "-XX:+UseZGC";
        // The JVM will not start with two collectors chosen, and launchers often choose G1 by default.
        String chosen = "-XX:+Use" + gc + "GC";
        String launch = f.text(Environment.JVM_FLAGS);
        String change = launch != null && Arrays.asList(launch.split(" ")).contains(chosen)
                ? "replace " + chosen + " with " + zgc + " in the JVM arguments"
                : "add " + zgc + " to the JVM arguments";
        String later = java < GENERATIONAL_BY_DEFAULT_JAVA
                ? String.format(Locale.ROOT, " (leave out -XX:+ZGenerational on Java %d or later: from Java %d the game"
                        + " will not start with it)", GENERATIONAL_BY_DEFAULT_JAVA, ZGENERATIONAL_REFUSED_JAVA)
                : "";
        out.add(new Recommendation("gc-pauses", Kind.STUTTER, Audience.PLAYER, "Long garbage-collection pauses",
                String.format(Locale.ROOT, "Garbage collection paused the game for up to %d ms (%s, %d ms in total).",
                        f.maxFrameGcMs(), Words.count(f.gcCount(), "collection", "collections"), f.gcTotalMs()),
                String.format(Locale.ROOT, "On Java %d, %s%s. ZGC pauses for well under a millisecond but uses more CPU,"
                        + " and more memory for the same data, so keep a quarter of the heap free.", java, change, later),
                gcPerFrame(f), f.maxFrameGcMs()));
    }

    /**
     * Physical memory to the nearest whole GiB, or NaN when it is unknown. The JVM reports the memory the system
     * can use: what is installed, less what the firmware and an integrated GPU reserve, so a 16 GB PC reads 15.8
     * to 15.9 GiB. Rounding counts that PC as the 16 GiB the player knows, so 12 GiB on it is fine, while a
     * reservation of half a GiB or more, which the game cannot use either, still counts against it.
     */
    private static double physicalMib(Facts f) {
        double mib = f.number(Environment.PHYSICAL_MEMORY_MIB);
        return Double.isNaN(mib) ? Double.NaN : Math.round(mib / MIB_PER_GIB) * MIB_PER_GIB;
    }

    /**
     * True when a heap of {@code gib} would hold {@code live} MiB under heap-small's trigger. Both rules ask it of
     * the largest heap heap-vs-ram accepts, so they agree on whether the machine can hold this pack.
     */
    private static boolean roomy(double live, long gib) {
        return live < Thresholds.LIVE_HEAP_SHARE * gib * MIB_PER_GIB;
    }

    /** The largest heap, in whole GiB, that heap-vs-ram accepts on {@code physical} MiB; under 1 when there is none. */
    private static long largestHeapGib(double physical) {
        return (long) Math.floor((physical - Thresholds.RAM_RESERVE_MIB) / MIB_PER_GIB);
    }

    private static double gcPerFrame(Facts f) {
        return f.frames() == 0 ? 0 : (double) f.gcTotalMs() / f.frames();
    }
}
