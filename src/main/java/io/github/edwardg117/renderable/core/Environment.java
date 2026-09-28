package io.github.edwardg117.renderable.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain environment description for reports (spec §6.1 item 3). Built by the Minecraft-aware collector in
 * {@code report}; the core only carries it. Entries keep insertion order.
 *
 * <p>Three hook lists (spec addendum §P5.D1): {@code hooksFired}, the hooks that have run while a session was
 * sampling, in any session since the game started; {@code hooksInstalled}, those every injector of which found its
 * target when its class loaded; and
 * {@code hooksFailed}, those with an injector that did not. A hook whose classes never loaded is in neither of the
 * last two. {@link #state} reads them together.
 */
public record Environment(Map<String, String> entries, List<String> hooksFired, List<String> hooksInstalled,
                          List<String> hooksFailed) {

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

    /** Physical memory in MiB (spec addendum §P5.B3). */
    public static final String PHYSICAL_MEMORY_MIB = "physicalMemoryMiB";
    /** The garbage collector in use, as {@code GcNames.collector} names it. */
    public static final String GC = "gc";
    /** The initial heap in MiB. */
    public static final String INITIAL_HEAP_MIB = "initialHeapMiB";
    /** The smallest heap in use after any collection during the session, in MiB; absent when none ran. */
    public static final String LIVE_HEAP_AFTER_GC_MIB = "liveHeapAfterGcMiB";
    /** The whitelisted JVM arguments, space-separated (see {@code JvmFlags}). */
    public static final String JVM_FLAGS = "jvmFlags";

    /**
     * Copies every collection so the record is really immutable, however it is built. The entries copy is a
     * {@link LinkedHashMap}, not {@code Map.copyOf}, because {@code Map.copyOf} does not keep order.
     */
    public Environment {
        entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        hooksFired = List.copyOf(hooksFired);
        hooksInstalled = List.copyOf(hooksInstalled);
        hooksFailed = List.copyOf(hooksFailed);
    }

    /** An environment with fired hooks only, for tests that need no more. */
    public static Environment of(Map<String, String> entries, List<String> hooksFired) {
        return new Environment(entries, hooksFired, List.of(), List.of());
    }

    public static Environment of(Map<String, String> entries, List<String> hooksFired, List<String> hooksInstalled,
                                 List<String> hooksFailed) {
        return new Environment(entries, hooksFired, hooksInstalled, hooksFailed);
    }

    public static Environment empty() {
        return new Environment(Map.of(), List.of(), List.of(), List.of());
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

    /** What the three hook lists say about one hook (spec addendum §P5.D1). */
    public enum HookState {
        /** An injector of it did not find its target: the hook did not apply, wholly or in part. */
        FAILED,
        /** Every checked injector of it found its target, or it fired. */
        INSTALLED,
        /**
         * No injector of it was checked and it never fired: the classes it hooks did not load in this client run, or
         * Mixin rejected its mixin as a whole, or the check threw. The game log records the last two.
         */
        NOT_LOADED
    }

    /** The state of {@code hook}. A failed injector outweighs a firing: part of the hook is missing. */
    public HookState state(Hook hook) {
        String name = hook.name();
        if (hooksFailed.contains(name)) return HookState.FAILED;
        if (hooksInstalled.contains(name) || hooksFired.contains(name)) return HookState.INSTALLED;
        return HookState.NOT_LOADED;
    }
}
