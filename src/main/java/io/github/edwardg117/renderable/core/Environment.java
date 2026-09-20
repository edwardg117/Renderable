package io.github.edwardg117.renderable.core;

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

    public static Environment of(Map<String, String> entries, List<String> hooksApplied) {
        return new Environment(entries, List.copyOf(hooksApplied));
    }

    public static Environment empty() {
        return new Environment(Map.of(), List.of());
    }
}
