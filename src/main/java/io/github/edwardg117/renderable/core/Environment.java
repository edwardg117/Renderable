package io.github.edwardg117.renderable.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain environment description for reports (spec §6.1 item 2). Built by the Minecraft-aware collector in
 * {@code report}; the core only carries it. Entries keep insertion order.
 */
public record Environment(Map<String, String> entries, List<String> hooksApplied) {

    /**
     * The value written for a mod or feature that is not installed. Its informal partner, written directly as
     * the literal {@code "unknown"} by the collectors and compat classes that need it, means the opposite
     * kind of gap: the mod or feature is present but a lookup into it failed, so which value belongs there
     * could not be determined. {@code ABSENT} answers "is it there"; {@code "unknown"} answers "we tried to
     * find out and could not".
     */
    public static final String ABSENT = "absent";

    /** The key saying whether event handlers were timed (spec addendum §P5.A3): "on", or {@link #UNAVAILABLE} and why. */
    public static final String EVENT_TIMING = "eventTiming";

    /** Starts the {@link #EVENT_TIMING} value when the event bus could not be reached; the reason follows it. */
    public static final String UNAVAILABLE = "unavailable: ";

    /**
     * Copies both collections so the record is really immutable, however it is built. The entries copy is a
     * {@link LinkedHashMap}, not {@code Map.copyOf}, because {@code Map.copyOf} does not keep order.
     */
    public Environment {
        entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        hooksApplied = List.copyOf(hooksApplied);
    }

    public static Environment of(Map<String, String> entries, List<String> hooksApplied) {
        return new Environment(entries, hooksApplied);
    }

    public static Environment empty() {
        return new Environment(Map.of(), List.of());
    }

    /** True when {@code key} was collected and is not {@link #ABSENT}. */
    public boolean has(String key) {
        String v = entries.get(key);
        return v != null && !ABSENT.equals(v);
    }

    /** The collected value for {@code key}, or {@link #ABSENT} when it was not collected. */
    public String value(String key) {
        return entries.getOrDefault(key, ABSENT);
    }
}
