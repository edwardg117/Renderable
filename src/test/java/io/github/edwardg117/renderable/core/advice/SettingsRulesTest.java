package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Bucket;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingsRulesTest {

    static List<Recommendation> run(Facts f, java.util.function.BiConsumer<Facts, List<Recommendation>> rule) {
        List<Recommendation> out = new ArrayList<>();
        rule.accept(f, out);
        return out;
    }

    @Test
    void entityDistanceNeedsAMillisecondATenthOfTheFrameAndRoomToLower() {
        List<Recommendation> r = run(new FactsBuilder().frameMs(12).bucket(Bucket.ENTITY, 2.4)
                .env("entityDistance", "3.0").build(), SettingsRules::entityDistance);
        assertEquals(1, r.size());
        Recommendation a = r.get(0);
        assertEquals("entity-distance", a.rule());
        assertEquals(Recommendation.Kind.FRAME_RATE, a.kind());
        assertEquals(Recommendation.Audience.PLAYER, a.audience());
        assertEquals("Entities take 2.4 ms of each 12.0 ms frame at 300% entity distance.", a.finding());
        assertEquals("Lower Entity Distance in Video Settings; the number of entities drawn falls with the square of the distance.", a.action());
        assertEquals(2.4, a.msPerFrame(), 1e-9);

        assertTrue(run(new FactsBuilder().frameMs(12).bucket(Bucket.ENTITY, 0.9).env("entityDistance", "3.0").build(),
                SettingsRules::entityDistance).isEmpty(), "under a millisecond");
        assertTrue(run(new FactsBuilder().frameMs(30).bucket(Bucket.ENTITY, 2.4).env("entityDistance", "3.0").build(),
                SettingsRules::entityDistance).isEmpty(), "8% of the frame");
        assertTrue(run(new FactsBuilder().frameMs(12).bucket(Bucket.ENTITY, 2.4).env("entityDistance", "0.5").build(),
                SettingsRules::entityDistance).isEmpty(), "already at the minimum");
        assertTrue(run(new FactsBuilder().frameMs(12).bucket(Bucket.ENTITY, 2.4).build(),
                SettingsRules::entityDistance).isEmpty(), "never fires on missing data");
    }

    @Test
    void particlesNeedAllAndHalfAMillisecond() {
        List<Recommendation> r = run(new FactsBuilder().bucket(Bucket.PARTICLE_RENDER, 0.6)
                .bucket(Bucket.PARTICLE_TICK, 0.3).env("particles", "ALL").build(), SettingsRules::particles);
        assertEquals(1, r.size());
        assertEquals("Particles take 0.9 ms a frame with Particles on All.", r.get(0).finding());
        assertEquals("Set Particles to Decreased in Video Settings.", r.get(0).action());
        assertTrue(run(new FactsBuilder().bucket(Bucket.PARTICLE_RENDER, 0.9).env("particles", "DECREASED").build(),
                SettingsRules::particles).isEmpty());
        assertTrue(run(new FactsBuilder().bucket(Bucket.PARTICLE_RENDER, 0.4).env("particles", "ALL").build(),
                SettingsRules::particles).isEmpty());
    }

    @Test
    void shadowsNeedAPackAMillisecondAndAThirdOfTheDrawing() {
        List<Recommendation> r = run(new FactsBuilder().env("shaderPack", "SomePack").shadow(2.1, 5.5).build(),
                SettingsRules::shadows);
        assertEquals(1, r.size());
        assertEquals("The shader pack's shadow pass is 38% of block entity and entity drawing, 2.1 ms a frame.", r.get(0).finding());
        assertEquals("Lower the shader pack's shadow distance or resolution, or turn its shadows off.", r.get(0).action());
        assertTrue(run(new FactsBuilder().env("shaderPack", "none").shadow(2.1, 5.5).build(), SettingsRules::shadows).isEmpty());
        assertTrue(run(new FactsBuilder().env("shaderPack", "SomePack").shadow(1.0, 5.0).build(), SettingsRules::shadows).isEmpty(),
                "a fifth of the drawing");
    }

    @Test
    void flywheelOffUnderShadersNeedsTheOffBackendAndCostlyBlockEntities() {
        List<Recommendation> r = run(new FactsBuilder().env("shaderPack", "SomePack").env("flywheelBackend", "flywheel:off")
                .bucket(Bucket.BLOCK_ENTITY, 3.2).build(), SettingsRules::flywheelShaders);
        assertEquals(1, r.size());
        assertEquals("Flywheel is not instancing under this shader pack, so the machines it would instance are drawn"
                + " one at a time: block entities take 3.2 ms a frame.", r.get(0).finding());
        assertTrue(run(new FactsBuilder().env("shaderPack", "SomePack").env("flywheelBackend", "flywheel:indirect")
                .bucket(Bucket.BLOCK_ENTITY, 3.2).build(), SettingsRules::flywheelShaders).isEmpty());
    }

    @Test
    void chunkRebuildsAreStutterWhenTheyLeadThreeHitches() {
        FactsBuilder b = new FactsBuilder().bucket(Bucket.CHUNK_UPLOAD, 1.8).env("renderDistance", "16").env("biomeBlend", "3");
        for (int i = 1; i <= 6; i++) {
            boolean leads = i <= 4;
            b.hitch(new Facts.Hitch(i, 60, 0, leads ? 30 + i : 1, leads, 0, 0, 0, List.of()));
        }
        Recommendation a = run(b.build(), SettingsRules::chunkRebuilds).get(0);
        assertEquals(Recommendation.Kind.STUTTER, a.kind());
        assertEquals(34.0, a.worstMs(), 1e-9, "the largest chunk upload among the hitches it leads");
        assertEquals("Chunk rebuilds take 1.8 ms a frame and are the largest part of 4 of 6 hitches,"
                + " at render distance 16 and biome blend 7x7.", a.finding());
        assertEquals("Lower Biome Blend to 5x5 or less. While travelling, a lower render distance rebuilds fewer sections.",
                a.action());
        assertEquals("up to 34 ms", a.atStake());

        Recommendation steady = run(new FactsBuilder().bucket(Bucket.CHUNK_UPLOAD, 1.8).env("renderDistance", "10")
                .env("biomeBlend", "3").build(), SettingsRules::chunkRebuilds).get(0);
        assertEquals(Recommendation.Kind.FRAME_RATE, steady.kind());
        assertEquals("Chunk rebuilds take 1.8 ms a frame, at render distance 10 and biome blend 7x7.", steady.finding());
        assertEquals("Lower Biome Blend to 5x5 or less.", steady.action());
        assertEquals("1.8 ms/frame", steady.atStake());

        assertTrue(run(new FactsBuilder().bucket(Bucket.CHUNK_UPLOAD, 1.8).env("renderDistance", "10")
                .env("biomeBlend", "2").build(), SettingsRules::chunkRebuilds).isEmpty(), "nothing to lower");
    }

    @Test
    void theFrameCapIsANoteAboutHeadroom() {
        List<Recommendation> r = run(new FactsBuilder().frameMs(16.7).cap(8.3).env("fpsLimit", "60").build(),
                SettingsRules::frameCap);
        assertEquals(Recommendation.Kind.NOTE, r.get(0).kind());
        assertEquals("8.3 ms of each 16.7 ms frame is spent waiting for the 60 fps limit, so the game has headroom.",
                r.get(0).finding());
        assertEquals("Raise Max Framerate if you want more frames.", r.get(0).action());
        assertEquals(0, r.get(0).msPerFrame(), 1e-9, "a note has nothing at stake");
        assertEquals("", r.get(0).atStake(), "a note has no figure for its heading");
        assertTrue(run(new FactsBuilder().frameMs(16.7).cap(4.1).build(), SettingsRules::frameCap).isEmpty(), "under a quarter");
    }

    @Test
    void vsyncIsANoteWhenTheSwapIsAQuarterOfTheFrame() {
        List<Recommendation> r = run(new FactsBuilder().frameMs(16.7).swap(5.2).env("vsync", "true").build(),
                SettingsRules::vsync);
        assertEquals("With VSync on, 5.2 ms of each 16.7 ms frame is spent waiting for the display.", r.get(0).finding());
        assertEquals("Turn VSync off for more frames, at the risk of tearing.", r.get(0).action());
        assertTrue(run(new FactsBuilder().frameMs(16.7).swap(5.2).env("vsync", "false").build(), SettingsRules::vsync).isEmpty());
    }


    @Test
    void entityDistanceThresholdsAreInclusiveAndEachHoldsOnItsOwn() {
        Recommendation a = run(new FactsBuilder().frameMs(10).bucket(Bucket.ENTITY, 1.0).env("entityDistance", "1.0").build(),
                SettingsRules::entityDistance).get(0);
        assertEquals("Entities take 1.0 ms of each 10.0 ms frame at 100% entity distance.", a.finding(),
                "exactly a millisecond and exactly a tenth, at the default distance");
        assertEquals(1, run(new FactsBuilder().frameMs(15).bucket(Bucket.ENTITY, 1.5).env("entityDistance", "0.75").build(),
                SettingsRules::entityDistance).size(), "75% can still go lower");
        assertTrue(run(new FactsBuilder().frameMs(5).bucket(Bucket.ENTITY, 0.9).env("entityDistance", "3.0").build(),
                SettingsRules::entityDistance).isEmpty(), "18% of the frame, but under a millisecond");
    }

    @Test
    void particlesFireAtHalfAMillisecondAndCarryTheirCost() {
        Recommendation a = run(new FactsBuilder().bucket(Bucket.PARTICLE_RENDER, 0.25).bucket(Bucket.PARTICLE_TICK, 0.25)
                .env("particles", "ALL").build(), SettingsRules::particles).get(0);
        assertEquals("particles", a.rule());
        assertEquals(Recommendation.Kind.FRAME_RATE, a.kind());
        assertEquals(0.5, a.msPerFrame(), 1e-9);
        assertTrue(run(new FactsBuilder().bucket(Bucket.PARTICLE_RENDER, 0.9).build(), SettingsRules::particles).isEmpty(),
                "never fires on missing data");
    }

    @Test
    void shadowsNeedTheMillisecondAndTheThirdEachOnItsOwn() {
        Recommendation a = run(new FactsBuilder().env("shaderPack", "SomePack").shadow(1.5, 5.0).build(),
                SettingsRules::shadows).get(0);
        assertEquals("The shader pack's shadow pass is 30% of block entity and entity drawing, 1.5 ms a frame.", a.finding(),
                "exactly 30%");
        assertEquals(Recommendation.Kind.FRAME_RATE, a.kind());
        assertEquals(1.5, a.msPerFrame(), 1e-9);
        assertEquals(1, run(new FactsBuilder().env("shaderPack", "SomePack").shadow(1.0, 2.0).build(), SettingsRules::shadows).size(),
                "exactly a millisecond");
        assertTrue(run(new FactsBuilder().env("shaderPack", "SomePack").shadow(0.9, 1.0).build(), SettingsRules::shadows).isEmpty(),
                "90% of the drawing, but under a millisecond");
        assertTrue(run(new FactsBuilder().env("shaderPack", "unknown").shadow(2.1, 5.5).build(), SettingsRules::shadows).isEmpty(),
                "never fires when the pack could not be read");
    }

    @Test
    void flywheelNeedsAShaderPackAndAMillisecondOfBlockEntities() {
        Recommendation a = run(new FactsBuilder().env("shaderPack", "SomePack").env("flywheelBackend", "flywheel:off")
                .bucket(Bucket.BLOCK_ENTITY, 1.0).build(), SettingsRules::flywheelShaders).get(0);
        assertEquals("flywheel-shaders", a.rule());
        assertEquals(Recommendation.Kind.FRAME_RATE, a.kind());
        assertEquals(Recommendation.Audience.PLAYER, a.audience());
        assertEquals("Turn shaders off to bring instancing back: Flywheel does not instance while any shader pack is in use.",
                a.action());
        assertEquals(1.0, a.msPerFrame(), 1e-9);
        assertTrue(run(new FactsBuilder().env("shaderPack", "none").env("flywheelBackend", "flywheel:off")
                .bucket(Bucket.BLOCK_ENTITY, 3.2).build(), SettingsRules::flywheelShaders).isEmpty(), "no shader pack");
        assertTrue(run(new FactsBuilder().env("shaderPack", "SomePack").env("flywheelBackend", "flywheel:off")
                .bucket(Bucket.BLOCK_ENTITY, 0.9).build(), SettingsRules::flywheelShaders).isEmpty(), "under a millisecond");
    }

    @Test
    void chunkRebuildsAtTheirEdges() {
        FactsBuilder b = new FactsBuilder().bucket(Bucket.CHUNK_UPLOAD, 1.0).env("renderDistance", "32").env("biomeBlend", "2");
        for (int i = 1; i <= 3; i++) b.hitch(new Facts.Hitch(i, 60, 0, 30, true, 0, 0, 0, List.of()));
        b.hitch(new Facts.Hitch(4, 200, 0, 50, false, 0, 0, 0, List.of()));
        Recommendation a = run(b.build(), SettingsRules::chunkRebuilds).get(0);
        assertEquals(Recommendation.Kind.STUTTER, a.kind(), "exactly three hitches, from exactly a millisecond");
        assertEquals(30.0, a.worstMs(), 1e-9, "the 50 ms upload is in a hitch something else leads");
        assertEquals(1.0, a.msPerFrame(), 1e-9, "the steady cost (deviation 5)");
        assertEquals("Chunk rebuilds take 1.0 ms a frame and are the largest part of 3 of 4 hitches,"
                + " at render distance 32 and biome blend 5x5.", a.finding());
        assertEquals("While travelling, a lower render distance rebuilds fewer sections.", a.action(),
                "biome blend is already 5x5");

        FactsBuilder two = new FactsBuilder().bucket(Bucket.CHUNK_UPLOAD, 1.8).env("renderDistance", "10").env("biomeBlend", "3");
        for (int i = 1; i <= 2; i++) two.hitch(new Facts.Hitch(i, 60, 0, 40, true, 0, 0, 0, List.of()));
        Recommendation steady = run(two.build(), SettingsRules::chunkRebuilds).get(0);
        assertEquals(Recommendation.Kind.FRAME_RATE, steady.kind(), "two hitches are not a pattern");
        assertEquals(0, steady.worstMs(), 1e-9);

        assertEquals("Chunk rebuilds take 1.8 ms a frame, at render distance 16 and biome blend off.",
                run(new FactsBuilder().bucket(Bucket.CHUNK_UPLOAD, 1.8).env("renderDistance", "16").env("biomeBlend", "0").build(),
                        SettingsRules::chunkRebuilds).get(0).finding());
        assertTrue(run(new FactsBuilder().bucket(Bucket.CHUNK_UPLOAD, 0.9).env("renderDistance", "32").env("biomeBlend", "7").build(),
                SettingsRules::chunkRebuilds).isEmpty(), "under a millisecond");
        assertTrue(run(new FactsBuilder().bucket(Bucket.CHUNK_UPLOAD, 1.8).env("renderDistance", "15").env("biomeBlend", "2").build(),
                SettingsRules::chunkRebuilds).isEmpty(), "render distance 15 is not far");
    }

    @Test
    void theFrameCapFiresAtAQuarterAndNeedsFrames() {
        Recommendation a = run(new FactsBuilder().frameMs(16).cap(4).build(), SettingsRules::frameCap).get(0);
        assertEquals("4.0 ms of each 16.0 ms frame is spent waiting for the fps limit, so the game has headroom.", a.finding(),
                "exactly a quarter, with no fpsLimit in the environment");
        assertEquals(Recommendation.Audience.PLAYER, a.audience());
        assertTrue(run(new FactsBuilder().frameMs(0).cap(0).build(), SettingsRules::frameCap).isEmpty(), "no frames");
    }

    @Test
    void vsyncNeedsAQuarterOfTheFrameAndIsANote() {
        Recommendation a = run(new FactsBuilder().frameMs(16).swap(4).env("vsync", "true").build(), SettingsRules::vsync).get(0);
        assertEquals("vsync", a.rule());
        assertEquals(Recommendation.Kind.NOTE, a.kind());
        assertEquals(0, a.msPerFrame(), 1e-9, "a note has nothing at stake");
        assertTrue(run(new FactsBuilder().frameMs(16.7).swap(2).env("vsync", "true").build(), SettingsRules::vsync).isEmpty(),
                "the frame is not waiting on the display");
        assertTrue(run(new FactsBuilder().frameMs(0).swap(0).env("vsync", "true").build(), SettingsRules::vsync).isEmpty(),
                "no frames");
    }
}
