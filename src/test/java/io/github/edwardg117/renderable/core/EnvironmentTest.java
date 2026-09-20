package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentTest {

    @Test
    void entriesAreCopiedInOrderAndCannotBeMutatedThroughTheCaller() {
        // Deliberately six keys, not three: Map.copyOf randomises its iteration order per JVM run (it salts
        // with ImmutableCollections.SALT32L), so a Map.copyOf regression here would not flake red - it would
        // silently pass whenever that run's random order happened to match insertion order. A three-key
        // fixture lands on insertion order about 1 run in 6; six keys is about 1 in 720, which is far enough
        // into the noise to trust a green run. Do not trim this back down for tidiness.
        // The first six keys are also the first six EnvironmentCollector.collect actually emits, so this
        // fixture doubles as a sample of the real report header.
        LinkedHashMap<String, String> source = new LinkedHashMap<>();
        source.put("minecraft", "1.21.1");
        source.put("neoforge", "21.1.220");
        source.put("java", "21.0.3");
        source.put("maxHeapMiB", "4096");
        source.put("mods", "87");
        source.put("gpu", "NVIDIA GeForce RTX 3080/PCIe/SSE2");
        Environment env = Environment.of(source, List.of("BLOCK_ENTITY_RENDER"));

        source.put("glVersion", "added after the fact");
        assertEquals(6, env.entries().size(), "the record kept its own copy");
        assertEquals(List.of("minecraft", "neoforge", "java", "maxHeapMiB", "mods", "gpu"),
                List.copyOf(env.entries().keySet()),
                "insertion order is what the text report prints, so the copy must keep it");
        assertThrows(UnsupportedOperationException.class, () -> env.entries().put("x", "y"));
        assertThrows(UnsupportedOperationException.class, () -> env.hooksApplied().add("x"));
    }

    @Test
    void absentIsNamedAndTestable() {
        assertEquals("absent", Environment.ABSENT);
        Environment env = Environment.of(new LinkedHashMap<>(Map.of(
                "sodium", "0.6.13", "iris", Environment.ABSENT)), List.of());
        assertTrue(env.has("sodium"));
        assertFalse(env.has("iris"), "absent means the mod is not installed");
        assertFalse(env.has("flywheel"), "a key that was never collected is not present either");
        assertEquals("0.6.13", env.value("sodium"));
        assertEquals(Environment.ABSENT, env.value("flywheel"));
    }
}
