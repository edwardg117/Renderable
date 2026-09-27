package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimpleNamesTest {

    @Test
    void namesAreSimpleUnlessTwoCollideInTheTable() {
        List<String> classes = List.of(
                "net.minecraft.client.particle.FlameParticle",
                "com.example.fx.SparkParticle",
                "com.other.fx.SparkParticle",
                "com.example.dust.DustParticle",
                "com.other.smoke.DustParticle",
                "NoPackage");
        Map<String, String> names = SimpleNames.of(classes);
        assertEquals(classes, List.copyOf(names.keySet()), "in the order given");
        assertEquals("FlameParticle", names.get("net.minecraft.client.particle.FlameParticle"));
        assertEquals("com.example.fx.SparkParticle", names.get("com.example.fx.SparkParticle"),
                "the same last segment, so only the full name tells them apart");
        assertEquals("com.other.fx.SparkParticle", names.get("com.other.fx.SparkParticle"));
        assertEquals("dust.DustParticle", names.get("com.example.dust.DustParticle"));
        assertEquals("smoke.DustParticle", names.get("com.other.smoke.DustParticle"));
        assertEquals("NoPackage", names.get("NoPackage"));
    }
}
