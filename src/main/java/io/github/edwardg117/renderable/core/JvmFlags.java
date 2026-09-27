package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The JVM arguments a report may carry (spec addendum §P5.B3): heap sizes and collector choices, which the
 * memory recommendations read. A whitelist, not a blacklist, because the argument list also carries paths
 * with the player's user name in them. Everything not listed here is dropped: {@code -D} properties,
 * {@code -javaagent}, class paths, {@code --add-opens}, and any value that is not a number with an optional
 * size suffix or a bare word.
 */
public final class JvmFlags {
    private static final Pattern SIZE = Pattern.compile("-X(?:mx|ms|mn|ss)\\d+[kKmMgGtT]?");
    private static final Pattern SWITCH = Pattern.compile("-XX:[+-](\\w+)");
    private static final Pattern VALUE = Pattern.compile("-XX:(\\w+)=(\\d+(?:\\.\\d+)?[kKmMgGtT]?|[A-Za-z_]\\w*)");
    private static final Set<String> SWITCHES = Set.of("UseG1GC", "UseZGC", "ZGenerational", "UseShenandoahGC",
            "UseParallelGC", "UseSerialGC", "AlwaysPreTouch", "UseLargePages", "UseTransparentHugePages",
            "UseStringDeduplication", "DisableExplicitGC", "UnlockExperimentalVMOptions");
    private static final Set<String> VALUES = Set.of("MaxGCPauseMillis", "ParallelGCThreads", "ConcGCThreads",
            "SoftMaxHeapSize", "MaxRAMPercentage", "InitialRAMPercentage");

    private JvmFlags() {
    }

    /** The whitelisted arguments of {@code args}, in their order. */
    public static List<String> kept(List<String> args) {
        List<String> out = new ArrayList<>();
        for (String a : args) {
            if (SIZE.matcher(a).matches()) {
                out.add(a);
                continue;
            }
            Matcher s = SWITCH.matcher(a);
            if (s.matches() && (SWITCHES.contains(s.group(1)) || s.group(1).startsWith("G1"))) {
                out.add(a);
                continue;
            }
            Matcher v = VALUE.matcher(a);
            if (v.matches() && (VALUES.contains(v.group(1)) || v.group(1).startsWith("G1"))) out.add(a);
        }
        return out;
    }
}
