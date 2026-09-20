package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class KeysTest {

    @Test
    void blockEntityKeyFormats() {
        BlockEntityKey k = new BlockEntityKey("create:mechanical_press", "minecraft:overworld", 10, 64, -5);
        assertEquals(ProfileKey.Category.BLOCK_ENTITY, k.category());
        assertEquals("create:mechanical_press", k.typeId());
        assertEquals("create:mechanical_press @ 10 64 -5", k.label());
        assertEquals("create:mechanical_press@minecraft:overworld:10,64,-5", k.serialize());
    }

    @Test
    void entityKeyFormatsWithAndWithoutName() {
        EntityKey plain = new EntityKey("minecraft:cow", 42, "");
        assertEquals(ProfileKey.Category.ENTITY, plain.category());
        assertEquals("minecraft:cow #42", plain.label());
        assertEquals("minecraft:cow#42", plain.serialize());

        EntityKey named = new EntityKey("minecraft:cow", 42, "Bessie");
        assertEquals("minecraft:cow #42 (Bessie)", named.label());
    }

    @Test
    void keysAreValueEqual() {
        assertEquals(new BlockEntityKey("a:b", "d", 1, 2, 3), new BlockEntityKey("a:b", "d", 1, 2, 3));
        assertEquals(new EntityKey("a:b", 1, "x"), new EntityKey("a:b", 1, "x"));
    }

    @Test
    void renderBucketsAreDistinguishedFromTickBuckets() {
        assertTrue(Bucket.BLOCK_ENTITY.isRender());
        assertTrue(Bucket.ENTITY.isRender());
        assertFalse(Bucket.BE_TICK.isRender());
        assertFalse(Bucket.PARTICLE_TICK.isRender());
        assertEquals("block entity", Bucket.BLOCK_ENTITY.label());
        assertEquals(Bucket.values().length, Bucket.COUNT);
    }

    @Test
    void accumulatorStartsEmpty() {
        Accumulator a = new Accumulator(new EntityKey("a:b", 1, ""));
        assertEquals(0, a.calls);
        assertEquals(0, a.totalNanos());
        assertEquals(0, a.frameNanos());
    }

    @Test
    void particleKeyFormats() {
        ParticleKey vanilla = new ParticleKey("net.minecraft.client.particle.FlameParticle");
        assertEquals(ProfileKey.Category.PARTICLE, vanilla.category());
        assertEquals("net.minecraft.client.particle.FlameParticle", vanilla.typeId());
        assertEquals("FlameParticle", vanilla.label());
        assertEquals("particle:net.minecraft.client.particle.FlameParticle", vanilla.serialize());

        ParticleKey modded = new ParticleKey("com.simibubi.create.content.fluids.particle.FluidParticle");
        assertEquals("com.simibubi.create.content.fluids.particle.FluidParticle", modded.label());
        assertEquals(new ParticleKey("a.B"), new ParticleKey("a.B"));
    }

    @Test
    void blockAndSectionKeysFormat() {
        BlockKey stone = new BlockKey("minecraft:stone");
        assertEquals(ProfileKey.Category.BLOCK, stone.category());
        assertEquals("minecraft:stone", stone.typeId());
        assertEquals("minecraft:stone", stone.label());
        assertEquals("block:minecraft:stone", stone.serialize());

        SectionKey s = new SectionKey("minecraft:overworld", 12, 4, -3);
        assertEquals(ProfileKey.Category.SECTION, s.category());
        assertEquals("section", s.typeId());
        assertEquals("section 12 4 -3", s.label());
        assertEquals("section@minecraft:overworld:12,4,-3", s.serialize());
        assertEquals(200, s.centerX());
        assertEquals(72, s.centerY());
        assertEquals(-40, s.centerZ());
        assertEquals(new SectionKey("d", 1, 2, 3), new SectionKey("d", 1, 2, 3));
    }

    /** Guards the architecture rule: core has no Minecraft, NeoForge, Mojang or LWJGL imports. */
    @Test
    void coreHasNoMinecraftImports() throws IOException {
        Path core = Path.of("src/main/java/io/github/edwardg117/renderable/core");
        List<String> forbidden = List.of("import net.minecraft", "import net.neoforged", "import com.mojang", "import org.lwjgl");
        try (Stream<Path> files = Files.walk(core)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p);
                for (String f : forbidden) {
                    assertFalse(src.contains(f), p + " contains " + f);
                }
            }
        }
    }

    @Test
    void reportWritersHaveNoMinecraftImports() throws IOException {
        // Spec §8: EnvironmentCollector is the only Minecraft-aware class in report.
        Path report = Path.of("src/main/java/io/github/edwardg117/renderable/report");
        List<String> forbidden = List.of("import net.minecraft", "import net.neoforged", "import com.mojang", "import org.lwjgl");
        try (Stream<Path> files = Files.walk(report)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (p.getFileName().toString().equals("EnvironmentCollector.java")) continue;
                String src = Files.readString(p);
                for (String f : forbidden) {
                    assertFalse(src.contains(f), p + " contains " + f);
                }
            }
        }
    }
}
