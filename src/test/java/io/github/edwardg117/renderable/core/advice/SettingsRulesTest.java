package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.ModKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static io.github.edwardg117.renderable.core.advice.FactsBuilder.hitch;
import static io.github.edwardg117.renderable.core.advice.FactsBuilder.soundHitch;
import static io.github.edwardg117.renderable.core.advice.FactsBuilder.top;
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

    @Test
    void distantHorizonsFiresAtAMillisecondAndATenthOfTheFrame() {
        Recommendation a = run(new FactsBuilder().frameMs(10.1).bucket(Bucket.LOD, 3.2).build(),
                SettingsRules::distantHorizons).get(0);
        assertEquals("distant-horizons", a.rule());
        assertEquals(Recommendation.Kind.FRAME_RATE, a.kind());
        assertEquals(Recommendation.Audience.PLAYER, a.audience());
        assertEquals("Distant Horizons' far terrain costs a lot to draw", a.title());
        assertEquals("Distant Horizons draws its far terrain in 3.2 ms of each 10.1 ms frame.", a.finding());
        assertEquals("Lower Quality Preset or LOD Chunk Render Distance Radius in Distant Horizons' settings, which its"
                + " button beside FOV in Options opens.", a.action());
        assertEquals(3.2, a.msPerFrame(), 1e-9);
        assertEquals("3.2 ms/frame", a.atStake());

        assertEquals("Distant Horizons draws its far terrain in 1.0 ms of each 10.0 ms frame.",
                run(new FactsBuilder().frameMs(10).bucket(Bucket.LOD, 1.0).build(), SettingsRules::distantHorizons)
                        .get(0).finding(), "exactly a millisecond, and exactly a tenth of the frame");
        assertTrue(run(new FactsBuilder().frameMs(9).bucket(Bucket.LOD, 0.99).build(),
                SettingsRules::distantHorizons).isEmpty(), "just under a millisecond, though 11% of the frame");
        assertTrue(run(new FactsBuilder().frameMs(20).bucket(Bucket.LOD, 1.99).build(),
                SettingsRules::distantHorizons).isEmpty(), "just under a tenth of the frame");
    }

    @Test
    void distantHorizonsIsQuietWithoutItsTime() {
        // Review focus 1. Without Distant Horizons, or with its render hook under "did not apply", lod reads 0 however
        // much terrain costs: DH's head injection then draws inside terrain, unnamed. No rule speaks for terrain.
        Facts withoutLod = new FactsBuilder().frameMs(10).bucket(Bucket.TERRAIN, 6).build();
        assertTrue(run(withoutLod, SettingsRules::distantHorizons).isEmpty());
        assertTrue(Advisor.advise(withoutLod).isEmpty(), "no other rule reads terrain");
        assertTrue(run(new FactsBuilder().frameMs(0).build(), SettingsRules::distantHorizons).isEmpty(), "no frames");
    }

    static final String MUTE = "Set the Blocks volume to 0% in Music & Sounds: the game then skips those sounds instead"
            + " of waiting for them.";
    static final String REPORT = " A pack author can report the sound's mod with this file.";

    @Test
    void theSliderTableNamesEveryCategoryAsMusicAndSoundsDoes() {
        // Review focus 5. The captions of Minecraft 1.21.1's soundCategory.* keys in en_us.json, by SoundSource name.
        assertEquals(List.of("Master Volume", "Music", "Jukebox/Note Blocks", "Weather", "Blocks", "Hostile Creatures",
                        "Friendly Creatures", "Players", "Ambient/Environment", "Voice/Speech"),
                Stream.of("master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient",
                        "voice").map(Words::slider).toList());
        assertEquals("somemod_engines", Words.slider("somemod_engines"), "a category with no slider reads as it is");
        assertEquals("the Blocks volume", Words.volume("block"));
        assertEquals("the Master Volume", Words.volume("master"), "its caption already says Volume");
        assertEquals("the somemod_engines volume", Words.volume("somemod_engines"));
    }

    @Test
    void soundWaitFiresAtHalfAMillisecondAFrameAndNamesTheCostliestRow() {
        Facts f = new FactsBuilder()
                .sound("create:steam", "block", 1.1, 85.2)
                .sound("minecraft:entity.cow.ambient", "neutral", 0.3, 4)
                .build();
        Recommendation a = run(f, SettingsRules::soundWait).get(0);
        assertEquals("sound-wait", a.rule());
        assertEquals(Recommendation.Kind.FRAME_RATE, a.kind());
        assertEquals(Recommendation.Audience.PLAYER, a.audience());
        assertEquals("Sounds keep the game waiting", a.title());
        assertEquals("Playing sounds kept the game waiting 1.4 ms a frame; create:steam (Blocks) played 85 times a second.",
                a.finding());
        assertEquals(MUTE + REPORT, a.action());
        assertEquals(1.4, a.msPerFrame(), 1e-9, "every sound row, not only the costliest");
        assertEquals(0, a.worstMs(), 1e-9);

        Recommendation exactly = run(new FactsBuilder().sound("minecraft:weather.rain", "weather", 0.5, 3.5).build(),
                SettingsRules::soundWait).get(0);
        assertEquals("Playing sounds kept the game waiting 0.5 ms a frame; minecraft:weather.rain (Weather) played 3.5"
                + " times a second.", exactly.finding(), "exactly the threshold");
        assertEquals("Set the Weather volume to 0% in Music & Sounds: the game then skips those sounds instead of"
                + " waiting for them.", exactly.action(), "a vanilla sound has no mod to report it to");
        assertTrue(run(new FactsBuilder().sound("create:steam", "block", 0.3, 40).sound("create:whistle", "block", 0.19, 2)
                .build(), SettingsRules::soundWait).isEmpty(), "0.49 ms a frame");
        assertTrue(run(new FactsBuilder().build(), SettingsRules::soundWait).isEmpty(), "nothing played");
    }

    @Test
    void aSoundStallsAtFiftyMillisecondsAndHalfTheHitch() {
        ModKey steam = ModKey.sound("create:steam", "block");
        Recommendation a = run(new FactsBuilder().sound("create:steam", "block", 0.2, 40)
                .hitch(hitch(1, 100, top(steam, 50))).build(), SettingsRules::soundWait).get(0);
        assertEquals(Recommendation.Kind.STUTTER, a.kind());
        assertEquals("Playing create:steam (Blocks) took 50 ms in hitch #1.", a.finding(),
                "exactly both, whatever the steady cost");
        assertEquals(MUTE + REPORT, a.action());
        assertEquals(50, a.worstMs(), 1e-9);
        assertEquals(0.2, a.msPerFrame(), 1e-9, "the steady cost, whatever the form");
        assertEquals("up to 50 ms", a.atStake());

        assertTrue(run(new FactsBuilder().hitch(hitch(1, 200, top(steam, 60))).build(), SettingsRules::soundWait).isEmpty(),
                "under half the hitch");
        assertTrue(run(new FactsBuilder().hitch(hitch(1, 60, top(steam, 49))).build(), SettingsRules::soundWait).isEmpty(),
                "under 50 ms");
        assertTrue(run(new FactsBuilder().hitch(hitch(1, 100, new Facts.Top(steam, 60, true))).build(),
                SettingsRules::soundWait).isEmpty(), "a GC pause could account for it");
        assertTrue(run(new FactsBuilder().hitch(hitch(1, 100, top(ModKey.packet("create", "create:sync"), 60))).build(),
                SettingsRules::soundWait).isEmpty(), "a payload is not a sound");

        Recommendation costly = run(new FactsBuilder().sound("create:steam", "block", 0.7, 40)
                .hitch(hitch(1, 200, top(steam, 120))).hitch(hitch(2, 150, top(steam, 90))).build(),
                SettingsRules::soundWait).get(0);
        assertEquals("Playing create:steam (Blocks) took 120 ms in hitch #1. Playing sounds kept the game waiting 0.7 ms"
                + " a frame.", costly.finding(), "the largest stall, then the steady cost when it reaches the threshold");
        assertEquals(120, costly.worstMs(), 1e-9);
    }

    @Test
    void aSoundRepeatsByHitchRepeatsTest() {
        ModKey steam = ModKey.sound("create:steam", "block");
        ModKey anvil = ModKey.sound("minecraft:block.anvil.land", "block");
        FactsBuilder quarter = new FactsBuilder().sound("create:steam", "block", 0.1, 40);
        for (int i = 1; i <= 12; i++) quarter.hitch(i <= 3 ? hitch(i, 60, top(steam, 15)) : hitch(i, 60));
        Recommendation a = run(quarter.build(), SettingsRules::soundWait).get(0);
        assertEquals(Recommendation.Kind.STUTTER, a.kind());
        assertEquals("Playing create:steam (Blocks) was a top cost in 3 of 12 hitches, up to 15 ms.", a.finding(),
                "15 of 60 ms is exactly a quarter of each hitch, and 3 of 12 exactly a quarter of them");
        assertEquals(15, a.worstMs(), 1e-9);

        FactsBuilder under = new FactsBuilder();
        for (int i = 1; i <= 4; i++) under.hitch(hitch(i, 60, top(steam, 14.9)));
        assertTrue(run(under.build(), SettingsRules::soundWait).isEmpty(), "just under a quarter of each hitch");
        FactsBuilder thirteen = new FactsBuilder();
        for (int i = 1; i <= 13; i++) thirteen.hitch(i <= 3 ? hitch(i, 60, top(steam, 30)) : hitch(i, 60));
        assertTrue(run(thirteen.build(), SettingsRules::soundWait).isEmpty(), "3 of 13 is under a quarter of them");
        FactsBuilder two = new FactsBuilder();
        for (int i = 1; i <= 2; i++) two.hitch(hitch(i, 60, top(steam, 30)));
        assertTrue(run(two.build(), SettingsRules::soundWait).isEmpty(), "two hitches are not a repeat");

        FactsBuilder worse = new FactsBuilder();
        for (int i = 1; i <= 3; i++) worse.hitch(hitch(i, 200, top(anvil, 95 - 5 * i)));   // 45% to 40%: not stalls
        worse.hitch(hitch(4, 100, top(steam, 55)));
        Recommendation repeat = run(worse.build(), SettingsRules::soundWait).get(0);
        assertEquals("Playing minecraft:block.anvil.land (Blocks) was a top cost in 3 of 4 hitches, up to 90 ms.",
                repeat.finding(), "a repeat at least as bad as the stall is named instead");
        assertEquals(MUTE, repeat.action(), "a vanilla sound: no mod to report it to");
    }

    @Test
    void soundWaitNamesTheRowItIsAboutAndTiesGoToTheFirst() {
        // Review focus 5. The frame-rate form names the first row in the facts' order, which is the Mods tab's, so of
        // two that cost the same it names the first by label. The stutter form names the stalled sound, and the action
        // its slider: muting the costliest row's category would leave the stall alone.
        Facts tie = new FactsBuilder().sound("create:steam", "block", 0.4, 40).sound("create:whistle", "ambient", 0.4, 2)
                .build();
        assertTrue(run(tie, SettingsRules::soundWait).get(0).finding().endsWith("; create:steam (Blocks) played 40 times a"
                + " second."));

        ModKey hiss = ModKey.sound("create:hiss", "ambient");
        Recommendation stalled = run(new FactsBuilder().sound("create:steam", "block", 0.9, 40)
                .sound("create:hiss", "ambient", 0.1, 1).hitch(hitch(1, 120, top(hiss, 70))).build(),
                SettingsRules::soundWait).get(0);
        assertEquals("Playing create:hiss (Ambient/Environment) took 70 ms in hitch #1. Playing sounds kept the game"
                + " waiting 1.0 ms a frame.", stalled.finding());
        assertEquals("Set the Ambient/Environment volume to 0% in Music & Sounds: the game then skips those sounds"
                + " instead of waiting for them." + REPORT, stalled.action(), "the stalled sound's slider, not the costliest row's");

        ModKey bell = ModKey.sound("minecraft:block.bell.use", "block");
        assertEquals("Playing create:hiss (Ambient/Environment) took 70 ms in hitch #1.",
                run(new FactsBuilder().hitch(hitch(1, 120, top(hiss, 70))).hitch(hitch(2, 110, top(bell, 70))).build(),
                        SettingsRules::soundWait).get(0).finding(),
                "two equal stalls: the first found, in the worse hitch, which the report numbers first");
        assertEquals(MUTE, run(new FactsBuilder().sound("create:steam", "block", 0.9, 40).hitch(hitch(1, 120, top(bell, 70)))
                .build(), SettingsRules::soundWait).get(0).action(),
                "the stalled sound is vanilla: no mod to report it to, whatever the costliest row's mod");
    }

    @Test
    void soundRowsOutsideEveryHitchsTopListLeaveOnlyTheFrameRateForm() {
        // Review focus 3. A frame keeps only its five costliest keys, so many cheap plays may never be named in a
        // hitch, and the row test cannot see them. Here they are not in the sound engine's tick either, so the bucket
        // test has nothing: only the steady sum can fire, and only from half a millisecond a frame.
        BlockEntityKey tank = new BlockEntityKey("create:fluid_tank", "minecraft:overworld", 1, 64, 1);
        FactsBuilder busy = new FactsBuilder().sound("create:steam", "block", 0.6, 120);
        for (int i = 1; i <= 8; i++) busy.hitch(hitch(i, 40, top(tank, 20)));
        Recommendation a = run(busy.build(), SettingsRules::soundWait).get(0);
        assertEquals(Recommendation.Kind.FRAME_RATE, a.kind());
        assertEquals("Playing sounds kept the game waiting 0.6 ms a frame; create:steam (Blocks) played 120 times a second.",
                a.finding());

        FactsBuilder quiet = new FactsBuilder().sound("create:steam", "block", 0.4, 120);
        for (int i = 1; i <= 8; i++) quiet.hitch(hitch(i, 40, top(tank, 20)));
        assertTrue(run(quiet.build(), SettingsRules::soundWait).isEmpty());
    }

    @Test
    void theSoundBucketStuttersAtAQuarterOfTheHitchInThreeHitchesAndAQuarterOfThem() {
        // The bucket test: a stall spread over many plays, none of them a quarter of a hitch alone.
        FactsBuilder quarter = new FactsBuilder().sound("create:steam", "block", 0.2, 40);
        for (int i = 1; i <= 12; i++) quarter.hitch(soundHitch(i, 60, i <= 3 ? 15 : 0));
        Recommendation a = run(quarter.build(), SettingsRules::soundWait).get(0);
        assertEquals(Recommendation.Kind.STUTTER, a.kind());
        assertEquals("In 3 of 12 hitches, the sound engine took at least a quarter of the frame (up to 15 ms). Playing"
                + " sounds kept the game waiting 0.2 ms a frame; create:steam (Blocks) played 40 times a second.",
                a.finding(), "15 of 60 ms is exactly a quarter of each hitch, and 3 of 12 exactly a quarter of them");
        assertEquals(MUTE + REPORT, a.action(), "the costliest row's slider");
        assertEquals(15, a.worstMs(), 1e-9, "the bucket's largest time in one of those hitches");
        assertEquals(0.2, a.msPerFrame(), 1e-9, "the sound play time, whatever the form");

        FactsBuilder under = new FactsBuilder();
        for (int i = 1; i <= 4; i++) under.hitch(soundHitch(i, 60, 14.9));
        assertTrue(run(under.build(), SettingsRules::soundWait).isEmpty(), "just under a quarter of each hitch");
        FactsBuilder thirteen = new FactsBuilder();
        for (int i = 1; i <= 13; i++) thirteen.hitch(soundHitch(i, 60, i <= 3 ? 30 : 0));
        assertTrue(run(thirteen.build(), SettingsRules::soundWait).isEmpty(), "3 of 13 is just under a quarter of them");
        FactsBuilder two = new FactsBuilder();
        for (int i = 1; i <= 2; i++) two.hitch(soundHitch(i, 60, 30));
        assertTrue(run(two.build(), SettingsRules::soundWait).isEmpty(), "two hitches are not a repeat");
        FactsBuilder paused = new FactsBuilder();
        for (int i = 1; i <= 4; i++) paused.hitch(new Facts.Hitch(i, 60, 6, 0, false, 0, 0, 0, 20, List.of()));
        assertTrue(run(paused.build(), SettingsRules::soundWait).isEmpty(),
                "the frame's 6 ms GC pause comes off the bucket's 20, leaving 14 of 60");
        FactsBuilder rare = new FactsBuilder().sound("create:steam", "block", 0.04, 0.3);
        for (int i = 1; i <= 4; i++) rare.hitch(soundHitch(i, 60, 20));
        assertEquals("In 4 of 4 hitches, the sound engine took at least a quarter of the frame (up to 20 ms). Playing sounds"
                + " kept the game waiting under 0.1 ms a frame; create:steam (Blocks) played 0.3 times a second.",
                run(rare.build(), SettingsRules::soundWait).get(0).finding(), "a spread stall can leave little steady cost");
    }

    @Test
    void theSoundBucketWithNoSoundRowNamesNoSlider() {
        // No sound row was timed (the play hook under "did not apply"), or the engine's time went on sounds already
        // playing: the finding has no row to name, and the action no slider and no mod.
        FactsBuilder b = new FactsBuilder();
        for (int i = 1; i <= 4; i++) b.hitch(soundHitch(i, 60, 20));
        Recommendation a = run(b.build(), SettingsRules::soundWait).get(0);
        assertEquals("In 4 of 4 hitches, the sound engine took at least a quarter of the frame (up to 20 ms).", a.finding());
        assertEquals("Set the slider for the sounds playing here to 0% in Music & Sounds: the game then skips those sounds"
                + " instead of waiting for them.", a.action());
        assertEquals(20, a.worstMs(), 1e-9);
        assertEquals(0, a.msPerFrame(), 1e-9, "no sound row, no sound play time");
    }

    @Test
    void aSoundRowsStutterComesBeforeTheBucketTest() {
        // One finding: the row test names the sound and the slider that stops it, so it wins when it passes, and the
        // bucket test speaks only when it does not. Either stutter form outranks the frame-rate form, as in the mod rules.
        ModKey steam = ModKey.sound("create:steam", "block");
        FactsBuilder both = new FactsBuilder().sound("create:steam", "block", 0.7, 40);
        for (int i = 1; i <= 4; i++) both.hitch(soundHitch(i, 100, 70, top(steam, 60)));
        List<Recommendation> r = run(both.build(), SettingsRules::soundWait);
        assertEquals(1, r.size());
        assertEquals("Playing create:steam (Blocks) was a top cost in 4 of 4 hitches, up to 60 ms. Playing sounds kept the"
                + " game waiting 0.7 ms a frame.", r.get(0).finding());
        assertEquals(60, r.get(0).worstMs(), 1e-9, "the row's worst, not the bucket's 70");

        ModKey hiss = ModKey.sound("create:hiss", "ambient");
        FactsBuilder spread = new FactsBuilder().sound("create:steam", "block", 0.7, 40).sound("create:hiss", "ambient", 0.1, 3);
        for (int i = 1; i <= 4; i++) spread.hitch(soundHitch(i, 100, 40, top(hiss, 20)));
        Recommendation a = run(spread.build(), SettingsRules::soundWait).get(0);
        assertEquals(Recommendation.Kind.STUTTER, a.kind(), "the bucket test outranks the steady 0.8 ms a frame");
        assertEquals("In 4 of 4 hitches, the sound engine took at least a quarter of the frame (up to 40 ms). Playing"
                + " sounds kept the game waiting 0.8 ms a frame; create:steam (Blocks) played 40 times a second.",
                a.finding(), "no row reaches a quarter of a hitch, so the bucket speaks, naming the costliest row");
        assertEquals(MUTE + REPORT, a.action(), "the costliest row's slider: the bucket names no sound of its own");
        assertEquals(40, a.worstMs(), 1e-9);
    }
}
