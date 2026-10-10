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
        assertTrue(Bucket.HUD.isRender(), "the HUD is drawn inside the render window");
        assertEquals("hud", Bucket.HUD.label());
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

    @Test
    void entityIdentityIgnoresTheNameSoARenameDoesNotForkTheKey() {
        EntityKey unnamed = new EntityKey("minecraft:cow", 7, "");
        EntityKey named = new EntityKey("minecraft:cow", 7, "Bessie");
        EntityKey other = new EntityKey("minecraft:cow", 8, "");
        EntityKey otherType = new EntityKey("minecraft:pig", 7, "");

        assertEquals(unnamed, named, "the same entity, renamed part way through a session, is one key");
        assertEquals(unnamed.hashCode(), named.hashCode());
        assertNotEquals(unnamed, other);
        assertNotEquals(unnamed, otherType);
        assertNotEquals(unnamed, new Object());

        // The name is still carried, for the label and the report.
        assertEquals("minecraft:cow #7", unnamed.label());
        assertEquals("minecraft:cow #7 (Bessie)", named.label());
        assertEquals(named.serialize(), unnamed.serialize(), "identity and serialisation agree");

        java.util.Map<ProfileKey, String> map = new java.util.HashMap<>();
        map.put(unnamed, "first");
        map.put(named, "second");
        assertEquals(1, map.size(), "one accumulator, not two");
        assertEquals("second", map.get(unnamed));
    }

    @Test
    void blockEntityLabelsOmitTheDimensionOnPurpose() {
        BlockEntityKey overworld = new BlockEntityKey("minecraft:chest", "minecraft:overworld", 1, 2, 3);
        BlockEntityKey nether = new BlockEntityKey("minecraft:chest", "minecraft:the_nether", 1, 2, 3);

        assertEquals(overworld.label(), nether.label(),
                "a session cannot span two dimensions, so the label does not need one");
        assertNotEquals(overworld, nether, "the dimension is still part of identity");
        assertNotEquals(overworld.serialize(), nether.serialize(), "and of the serialised form");
        assertEquals("minecraft:chest@minecraft:the_nether:1,2,3", nether.serialize());
    }

    @Test
    void modKeysFormatAndRollUpByMod() {
        ModKey event = ModKey.event("create", "net.neoforged.neoforge.client.event.ClientTickEvent$Post");
        assertEquals(ProfileKey.Category.MOD, event.category());
        assertEquals("create", event.typeId());
        assertEquals("event ClientTickEvent.Post (create)", event.label(), "standalone, an event row names its mod");
        assertEquals("event ClientTickEvent.Post", event.memberLabel(), "under its mod's row it need not");
        assertEquals("event@create:net.neoforged.neoforge.client.event.ClientTickEvent$Post", event.serialize());

        ModKey layer = ModKey.layer("xaerominimap", "xaerominimap:minimap");
        assertEquals("xaerominimap", layer.typeId());
        assertEquals("layer xaerominimap:minimap", layer.label());
        assertEquals(layer.label(), layer.memberLabel(), "a layer id already names its mod");
        assertEquals("layer@xaerominimap:xaerominimap:minimap", layer.serialize());

        ModKey packet = ModKey.packet("minecraft", "minecraft:level_chunk_with_light");
        assertEquals("packet minecraft:level_chunk_with_light", packet.label());
        assertEquals("packet@minecraft:minecraft:level_chunk_with_light", packet.serialize());

        assertEquals(ModKey.event("a", "x.Y"), ModKey.event("a", "x.Y"));
        assertNotEquals(ModKey.event("a", "x.Y"), ModKey.event("b", "x.Y"), "one event handled by two mods is two keys");
        assertNotEquals(ModKey.layer("a", "a:y"), ModKey.packet("a", "a:y"), "the kind is part of identity");
        assertEquals("Event", ModKey.simpleEventName("Event"), "a class in the default package keeps its name");
    }

    @Test
    void blockEntityAndSectionKeysKnowTheirDimensionAndOthersDoNot() {
        assertEquals("minecraft:the_nether",
                new BlockEntityKey("create:mechanical_press", "minecraft:the_nether", 1, 2, 3).dimension());
        assertEquals("minecraft:the_nether", new SectionKey("minecraft:the_nether", 1, 2, 3).dimension());
        assertNull(new EntityKey("minecraft:cow", 7, "").dimension(), "an entity's is its session's");
        assertNull(new ParticleKey("a.B").dimension());
        assertNull(new BlockKey("minecraft:stone").dimension());
        assertNull(ModKey.event("a", "b.C").dimension());
    }

    @Test
    void aPlayersKeyCarriesNoNameExceptTheLocalPlayersYou() {
        assertEquals("minecraft:player #1234", EntityKey.player("minecraft:player", 1234, false).label());
        assertEquals("minecraft:player #118098 (you)", EntityKey.player("minecraft:player", 118098, true).label());
        assertEquals("minecraft:player#118098", EntityKey.player("minecraft:player", 118098, true).serialize(),
                "the JSON key has no name either way");
    }

    @Test
    void terrainAndLodLeadTheRenderSideAndSoundAndAnimateTickEndTheTick() {
        assertEquals(List.of(Bucket.TERRAIN, Bucket.LOD, Bucket.BLOCK_ENTITY, Bucket.ENTITY, Bucket.PARTICLE_RENDER,
                Bucket.FLYWHEEL, Bucket.CHUNK_UPLOAD, Bucket.LIGHT, Bucket.HUD, Bucket.PARTICLE_TICK, Bucket.BE_TICK,
                Bucket.ENTITY_TICK, Bucket.SOUND, Bucket.ANIMATE_TICK), List.of(Bucket.VALUES),
                "terrain and its LODs lead the render side; the split prints in this order");
        assertTrue(Bucket.TERRAIN.isRender());
        assertTrue(Bucket.LOD.isRender());
        assertFalse(Bucket.SOUND.isRender(), "the sound engine ticks inside the client tick");
        assertFalse(Bucket.ANIMATE_TICK.isRender());
        assertEquals("terrain", Bucket.TERRAIN.label());
        assertEquals("lod", Bucket.LOD.label());
        assertEquals("sound", Bucket.SOUND.label());
        assertEquals("animate tick", Bucket.ANIMATE_TICK.label());
    }
}
