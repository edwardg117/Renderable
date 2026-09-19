package io.github.edwardg117.renderable.core;

import java.util.List;
import java.util.Map;

/**
 * Plain environment description for reports (spec §6.1 item 2). Built by the Minecraft-aware collector in
 * {@code report}; the core only carries it. Entries keep insertion order.
 */
public record Environment(Map<String, String> entries, List<String> hooksApplied) {

    public static Environment of(Map<String, String> entries, List<String> hooksApplied) {
        return new Environment(entries, List.copyOf(hooksApplied));
    }

    public static Environment empty() {
        return new Environment(Map.of(), List.of());
    }
}
