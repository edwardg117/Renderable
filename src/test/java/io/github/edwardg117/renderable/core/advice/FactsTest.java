package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.core.SessionConfig;
import io.github.edwardg117.renderable.core.TestSessions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.edwardg117.renderable.core.TestSessions.MS;
import static org.junit.jupiter.api.Assertions.*;

class FactsTest {

    @Test
    void theFrameComponentsCostsAndModsOfThePhase5Fixture() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>(Map.of("particles", "ALL", "shaderPack", "none"));
        Facts f = Facts.of(TestSessions.phase5(), Environment.of(m, List.of()));

        assertEquals(2, f.frames());
        assertEquals(20.0, f.frameMs(), 1e-9);
        assertEquals(2.0, f.bucket(Bucket.HUD), 1e-9);
        assertEquals(2.0, f.tasksMs(), 1e-9);
        assertEquals(3.0, f.capMs(), 1e-9);
        assertEquals(8.0, f.swapMs(), 1e-9);
        assertEquals("ALL", f.text("particles"));
        assertFalse(f.shadersOn(), "\"none\" is not a pack");
        assertTrue(Double.isNaN(f.number("renderDistance")), "not collected");

        Facts.Cost press = f.blockEntityTypes().get(0);
        assertEquals("create:mechanical_press", press.label());
        assertEquals(1.0, press.msPerFrame(), 1e-9);
        assertEquals("100 64 -20", press.where());
        assertEquals(List.of("minecraft:boat", "minecraft:cow", "minecraft:player"),
                f.entityTypes().stream().map(Facts.Cost::label).toList());

        assertEquals(List.of("create", "minecraft", "xaerominimap", "somemod"),
                f.mods().stream().map(Facts.ModCost::modId).toList());
        Facts.ModCost create = f.mods().get(0);
        assertEquals(2.0, create.handlersMs(), 1e-9);
        assertEquals("RenderLevelStageEvent", create.topEvent());
        assertEquals(1.0, f.mods().get(1).packetsMs(), 1e-9);
        assertEquals(1.0, f.mods().get(2).layersMs(), 1e-9);
        Facts.ModCost somemod = f.mods().get(3);
        assertEquals(0.6, somemod.handlersMs(), 1e-9);
        assertEquals("ClientTickEvent.Post", somemod.topEvent(), "the tick handler costs more than the living one");
        assertTrue(f.hitches().isEmpty());
    }

    @Test
    void aHitchCarriesItsUnnamedTimeAndTopContributors() {
        Facts f = Facts.of(TestSessions.sample(), Environment.empty());
        assertEquals(1, f.hitches().size());
        Facts.Hitch h = f.hitches().get(0);
        assertEquals(1, h.number());
        assertEquals(80.0, h.totalMs(), 1e-9);
        assertEquals(0, h.gcMs(), 1e-9, "the hitch frame had no pause");
        assertFalse(h.chunkUploadLargest());
        assertEquals(2.1, h.unnamedRenderMs(), 1e-9, "other render, none of it named");
        assertEquals(0.8, h.unnamedTickMs(), 1e-9);
        assertEquals(0, h.untimedTasksMs(), 1e-9);
        assertEquals(TestSessions.PRESS, h.top().get(0).key());
        assertEquals(60.0, h.top().get(0).ms(), 1e-9);
        assertFalse(h.top().get(0).gc());
        assertEquals(1, f.gcCount());
        assertEquals(5, f.maxFrameGcMs());
    }

    @Test
    void typeCostsCountTheirMembersAndNameTheCostliestPosition() {
        Facts f = Facts.of(TestSessions.phase2(), Environment.empty());
        Facts.Cost press = f.blockEntityTypes().get(0);
        assertEquals(6.8, press.msPerFrame(), 1e-9, "render plus tick: 98 + 8 + 30 ms over 20 frames");
        assertEquals(2, press.members());
        assertEquals("100 64 -20", press.where(), "the press at 100 costs more than the one at 103");
        Facts.Cost cow = f.entityTypes().get(0);
        assertEquals(0.7, cow.msPerFrame(), 1e-9, "10 ms render and 4 ms tick over 20 frames");
        assertEquals("", cow.where());
        Facts.Cost flame = f.particleClasses().get(0);
        assertEquals("FlameParticle", flame.label(), "the key's label, which drops vanilla's package");
        assertEquals(0.4, flame.msPerFrame(), 1e-9, "6 ms render and 2 ms tick over 20 frames");
        assertEquals(3.0, flame.livePerFrame(), 1e-9);
        assertEquals(1, flame.members());
    }

    @Test
    void shadowTimeIsTheShadowPassPartOfBlockEntityAndEntityRender() {
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> now[0], 0);
        Accumulator press = s.accumulator(TestSessions.PRESS);
        Accumulator cow = s.accumulator(TestSessions.COW);
        s.onRenderPre(0, 0);
        s.shadowPass = true;
        s.begin(press, Bucket.BLOCK_ENTITY); now[0] = 2 * MS; s.end();
        s.begin(cow, Bucket.ENTITY);         now[0] = 3 * MS; s.end();
        s.shadowPass = false;
        s.begin(press, Bucket.BLOCK_ENTITY); now[0] = 4 * MS; s.end();
        s.onRenderPost();
        s.onTickPre();
        s.begin(cow, Bucket.ENTITY_TICK);    now[0] = 5 * MS; s.end();
        s.onTickPost();
        now[0] = 10 * MS; s.onRenderPre(0, 0);
        Facts f = Facts.of(s.finish(), Environment.empty());
        assertEquals(3.0, f.shadowMs(), 1e-9, "the press's 2 ms and the cow's 1 ms in the shadow pass");
        assertEquals(4.0, f.shadowableRenderMs(), 1e-9, "render only: the cow's tick is not drawn");
    }

    /**
     * Four quiet 10 ms frames, then two hitches. Frame 4 (110 ms): a 60 ms chunk upload, the stage handler's 25 ms
     * of named render and 5 ms unnamed; tasks 10 ms, 8 of them the chunk packet; tick 10 ms, 3 of them the tick
     * handler; and a 20 ms pause, which the 25 ms handler could be. Frame 5 (140 ms): the press's 70 ms of block
     * entity beats a 60 ms chunk upload. A 3 ms pause lands in frame 1, so the total and the worst frame differ.
     */
    @Test
    void aHitchLosesItsNamedTimeWindowByWindowAndNotesAChunkUploadLead() {
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> now[0], 0);
        Accumulator stage = s.accumulator(TestSessions.STAGE_HANDLER);
        Accumulator tick = s.accumulator(TestSessions.TICK_HANDLER);
        Accumulator chunk = s.accumulator(TestSessions.CHUNK_PACKET);
        Accumulator press = s.accumulator(TestSessions.PRESS);
        for (int i = 0; i <= 4; i++) {
            now[0] = i * 10 * MS;
            s.onRenderPre(i >= 2 ? 1 : 0, i >= 2 ? 3 : 0);
        }
        s.begin(null, Bucket.CHUNK_UPLOAD);  now[0] = 100 * MS; s.end();
        s.beginAttribution(stage);           now[0] = 125 * MS; s.end();
        now[0] = 130 * MS;                   s.onRenderPost();
        s.onTasksBegin();
        s.beginAttribution(chunk);           now[0] = 138 * MS; s.end();
        now[0] = 140 * MS;                   s.onTasksEnd();
        s.onTickPre();
        s.beginAttribution(tick);            now[0] = 143 * MS; s.end();
        now[0] = 150 * MS;                   s.onTickPost();
        s.onRenderPre(2, 23);
        s.begin(press, Bucket.BLOCK_ENTITY); now[0] = 220 * MS; s.end();
        s.begin(null, Bucket.CHUNK_UPLOAD);  now[0] = 280 * MS; s.end();
        s.onRenderPost();
        now[0] = 290 * MS;                   s.onRenderPre(2, 23);
        Facts f = Facts.of(s.finish(), Environment.empty());

        assertEquals(2, f.hitches().size());
        Facts.Hitch worst = f.hitches().get(0);
        assertEquals(140.0, worst.totalMs(), 1e-9);
        assertEquals(60.0, worst.chunkUploadMs(), 1e-9);
        assertFalse(worst.chunkUploadLargest(), "the block entity bucket is larger");
        Facts.Hitch h = f.hitches().get(1);
        assertEquals(2, h.number());
        assertEquals(110.0, h.totalMs(), 1e-9);
        assertEquals(60.0, h.chunkUploadMs(), 1e-9);
        assertTrue(h.chunkUploadLargest(), "60 ms against 30 of other render and 10 each of tick and tasks");
        assertEquals(7.0, h.unnamedTickMs(), 1e-9, "10 ms of tick less the handler's 3");
        assertEquals(5.0, h.unnamedRenderMs(), 1e-9, "30 ms of other render less the handler's 25");
        assertEquals(2.0, h.untimedTasksMs(), 1e-9, "10 ms of tasks less the packet's 8");
        assertEquals(20.0, h.gcMs(), 1e-9);
        assertEquals(List.of(new Facts.Top(TestSessions.STAGE_HANDLER, 25.0, true),
                new Facts.Top(TestSessions.CHUNK_PACKET, 8.0, false), new Facts.Top(TestSessions.TICK_HANDLER, 3.0, false)),
                h.top());
        assertEquals(2, f.gcCount());
        assertEquals(23, f.gcTotalMs());
        assertEquals(20, f.maxFrameGcMs());
    }

    @Test
    void aSessionWithNoWholeFrameHasZerosNotNaN() {
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> now[0], 0);
        Accumulator press = s.accumulator(TestSessions.PRESS);
        s.onRenderPre(0, 0);
        s.begin(press, Bucket.BLOCK_ENTITY); now[0] = 2 * MS; s.end();   // in the open frame, which the finish drops
        Facts f = Facts.of(s.finish(), Environment.empty());
        assertEquals(0, f.frames());
        assertEquals(0, f.frameMs(), 0);
        assertEquals(0, f.blockEntityTypes().get(0).msPerFrame(), 0, "not Infinity");
        assertTrue(f.hitches().isEmpty());
    }

    @Test
    void unknownAndAbsentValuesAreMissingData() {
        Facts f = new FactsBuilder().env("shaderPack", "unknown").env("flywheelBackend", Environment.ABSENT)
                .env("renderDistance", " 12 ").env("vsync", "false").build();
        assertNull(f.text("shaderPack"), "a lookup that failed");
        assertFalse(f.shadersOn(), "unknown is not a pack");
        assertNull(f.text("flywheelBackend"), "not installed");
        assertEquals(12, f.number("renderDistance"), 1e-9);
        assertTrue(Double.isNaN(f.number("vsync")));
        assertTrue(new FactsBuilder().env("shaderPack", "Complementary").build().shadersOn());
    }

    @Test
    void thePhase6FixtureCarriesItsSoundRowsAndLod() {
        Facts f = Facts.of(TestSessions.phase6(), Environment.empty());
        assertEquals(List.of("create:steam", "minecraft:block.anvil.land", "minecraft:ui.button.click"),
                f.sounds().stream().map(Facts.SoundCost::event).toList(), "every sound row, the costliest first");
        Facts.SoundCost steam = f.sounds().get(0);
        assertEquals("create", steam.modId(), "the event id's namespace");
        assertEquals("block", steam.category());
        assertEquals(1.0, steam.msPerFrame(), 1e-9, "2 ms over the 2 frames");
        assertEquals(100.0, steam.playsPerSecond(), 1e-9, "4 plays in 0.04 s");
        assertEquals("master", f.sounds().get(2).category());
        assertEquals(1.6, f.soundMs(), 1e-9, "the Sound plays line's 1.60 ms a frame");
        assertEquals(2.0, f.bucket(Bucket.LOD), 1e-9);
        assertEquals(2.0, f.bucket(Bucket.TERRAIN), 1e-9, "4 ms less the 2 ms of lod inside it");

        Facts.ModCost create = f.mods().stream().filter(m -> m.modId().equals("create")).findFirst().orElseThrow();
        assertEquals(0, create.handlersMs() + create.layersMs() + create.packetsMs(), 1e-9,
                "a sound row is none of a mod's handlers, layers or payloads, so it feeds no mod-* rule");
        Facts.ModCost minecraft = f.mods().stream().filter(m -> m.modId().equals("minecraft")).findFirst().orElseThrow();
        assertEquals(1.0, minecraft.packetsMs(), 1e-9, "the sound packet, the anvil's play inside it not added again");
    }

    @Test
    void soundRowsThatCostTheSameGoByLabelAndNoSoundsIsEmpty() {
        // Review focus 5: a tie between two sound rows goes A to Z by label, as on the Mods tab, whatever order they
        // played in, so the advice names the same row the tab lists first.
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> now[0], 0);
        Accumulator flute = s.accumulator(ModKey.sound("minecraft:block.note_block.flute", "record"));
        Accumulator steam = s.accumulator(ModKey.sound("create:steam", "block"));
        s.onRenderPre(0, 0);
        s.beginAttribution(flute); now[0] = 1 * MS; s.end();
        s.beginAttribution(steam); now[0] = 2 * MS; s.end();
        now[0] = 10 * MS; s.onRenderPre(0, 0);
        Facts f = Facts.of(s.finish(), Environment.empty());
        assertEquals(List.of("create:steam", "minecraft:block.note_block.flute"),
                f.sounds().stream().map(Facts.SoundCost::event).toList());
        assertEquals(2.0, f.soundMs(), 1e-9);

        Facts none = Facts.of(TestSessions.sample(), Environment.empty());
        assertTrue(none.sounds().isEmpty(), "nothing played");
        assertEquals(0, none.soundMs(), 0);
    }

    @Test
    void aHitchCarriesItsSoundBucket() {
        // Four quiet 10 ms frames, then a 60 ms hitch whose tick is 45 ms, 40 of them in the sound engine's tick.
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> now[0], 0);
        for (int i = 0; i <= 4; i++) {
            now[0] = i * 10 * MS;
            s.onRenderPre(0, 0);
        }
        now[0] = 45 * MS;                    s.onRenderPost();
        s.onTickPre();
        s.begin(null, Bucket.SOUND);         now[0] = 85 * MS; s.end();
        now[0] = 90 * MS;                    s.onTickPost();
        now[0] = 100 * MS;                   s.onRenderPre(0, 0);
        Facts f = Facts.of(s.finish(), Environment.empty());

        assertEquals(1, f.hitches().size());
        Facts.Hitch h = f.hitches().get(0);
        assertEquals(60.0, h.totalMs(), 1e-9);
        assertEquals(40.0, h.soundMs(), 1e-9, "the sound engine's tick in the hitch");
        assertEquals(5.0, h.unnamedTickMs(), 1e-9, "the tick's 45 ms less the 40 in sound: a bucket is never unnamed");
        assertEquals(0, FactsBuilder.hitch(1, 60).soundMs(), 0, "the nine-argument form has no sound bucket");
    }
}
