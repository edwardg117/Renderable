package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.core.SessionConfig;
import io.github.edwardg117.renderable.core.TestSessions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.edwardg117.renderable.core.advice.FactsBuilder.hitch;
import static io.github.edwardg117.renderable.core.advice.FactsBuilder.top;
import static org.junit.jupiter.api.Assertions.*;

class AdvisorTest {

    @Test
    void stutterComesFirstThenFrameRateThenNotes() {
        BlockEntityKey crafter = new BlockEntityKey("create:mechanical_crafter", "minecraft:overworld", 1, 2, 3);
        FactsBuilder b = new FactsBuilder().frameMs(12)
                .bucket(Bucket.ENTITY, 2.4).env("entityDistance", "3.0")            // entity-distance, 2.4 ms/frame
                .bucket(Bucket.CHUNK_UPLOAD, 1.8).env("biomeBlend", "3")            // chunk-rebuilds, 1.8 ms/frame
                .cap(4)                                                             // frame-cap, a note
                .env(Environment.GC, "G1").env("java", "21.0.3").gc(14, 610, 120)   // gc-pauses, up to 120 ms
                .env(Environment.PHYSICAL_MEMORY_MIB, "16384").env("maxHeapMiB", "14336")   // heap-vs-ram, a note
                .unbalanced(2);                                                     // stack-resets, a note
        for (int i = 1; i <= 4; i++) b.hitch(hitch(i, 80, top(crafter, 60)));        // hitch-repeat, up to 60 ms

        assertEquals(List.of("gc-pauses", "hitch-repeat", "entity-distance", "chunk-rebuilds", "frame-cap",
                        "heap-vs-ram", "stack-resets"),
                Advisor.advise(b.build()).stream().map(Recommendation::rule).toList());
    }

    @Test
    void aQuietSessionGetsNoAdvice() {
        assertTrue(Advisor.advise(new FactsBuilder().build()).isEmpty());
    }

    @Test
    void thePhase5FixtureGetsItsModsAndCostliestThings() {
        List<Recommendation> advice = Advisor.advise(TestSessions.phase5(), Environment.empty());
        assertEquals(List.of("mod-handlers", "block-entity-type", "entity-type", "mod-hud", "mod-handlers"),
                advice.stream().map(Recommendation::rule).toList(),
                "by ms/frame; the three at 1.0 keep the rules' order, the player's first");
        assertEquals("create's event handlers", advice.get(0).title());
        assertEquals("somemod's event handlers", advice.get(4).title());
        assertTrue(advice.stream().noneMatch(a -> a.title().startsWith("minecraft")), "vanilla packets are not a mod");
    }

    @Test
    void aTieGoesToThePlayerWhateverTheRulesOrder() {
        // Every player rule runs before PackRules, so advise() alone cannot tell this tiebreak from the rules' order.
        Recommendation author = new Recommendation("a", Recommendation.Kind.NOTE, Recommendation.Audience.PACK_AUTHOR,
                "A", "", "", 0, 0);
        Recommendation player = new Recommendation("p", Recommendation.Kind.NOTE, Recommendation.Audience.PLAYER,
                "P", "", "", 0, 0);
        List<Recommendation> ranked = new java.util.ArrayList<>(List.of(author, player));
        ranked.sort(Advisor.RANKING);
        assertEquals(List.of(player, author), ranked);
    }

    @Test
    void aRuleThatThrowsCostsTheAdviceNotTheReport() {
        // A null environment stands in for any bug in a rule: the first rule that reads the environment throws.
        List<Recommendation> advice = Advisor.advise(TestSessions.phase5(), null);
        assertEquals(List.of("advice-failed"), advice.stream().map(Recommendation::rule).toList());
        assertEquals(Recommendation.Kind.NOTE, advice.get(0).kind());
        assertTrue(advice.get(0).finding().startsWith("A rule failed with java.lang.NullPointerException"),
                advice.get(0).finding());
        assertTrue(advice.get(0).finding().contains(" at io.github.edwardg117.renderable.core."), advice.get(0).finding());
    }

    @Test
    void thePhase6FixtureGetsFrameRateAdviceAndNoStutter() {
        // Review focus 2: a session with no hitches. No stutter form can fire, so sound-wait gives its frame-rate form
        // and unattributed-stall nothing.
        List<Recommendation> advice = Advisor.advise(TestSessions.phase6(), Environment.empty());
        assertEquals(List.of("distant-horizons", "sound-wait", "block-entity-type"),
                advice.stream().map(Recommendation::rule).toList(), "by ms/frame: lod 2.0, sound 1.6, the fluid tank 1.0");
        assertTrue(advice.stream().allMatch(a -> a.kind() == Recommendation.Kind.FRAME_RATE));
        assertEquals("Distant Horizons draws its far terrain in 2.0 ms of each 20.0 ms frame.", advice.get(0).finding(),
                "exactly a tenth of the frame");
        assertEquals("Playing sounds kept the game waiting 1.6 ms a frame; create:steam (Blocks) played 100 times a second.",
                advice.get(1).finding());
        assertEquals("Set the Blocks volume to 0% in Music & Sounds: the game then skips those sounds instead of waiting"
                + " for them. A pack author can report the sound's mod with this file.", advice.get(1).action());
    }

    @Test
    void unnamedTimeAndASoundStallAreSeparateFindings() {
        // Review focus 4. A play is named or bucketed time, never unnamed, so a hitch a sound stalled can also have
        // unnamed time of its own, and each rule is about its own part of the hitch.
        ModKey steam = ModKey.sound("create:steam", "block");
        FactsBuilder both = new FactsBuilder().sound("create:steam", "block", 0.1, 40);
        for (int i = 1; i <= 4; i++) both.hitch(new Facts.Hitch(i, 100, 0, 0, false, 30, 0, 0, List.of(top(steam, 60))));
        List<Recommendation> advice = Advisor.advise(both.build());
        assertEquals(List.of("sound-wait", "unattributed-stall"), advice.stream().map(Recommendation::rule).toList(),
                "both stutter, ranked by their worst: the sound's 60 ms, then the unnamed 30");
        assertEquals("Playing create:steam (Blocks) was a top cost in 4 of 4 hitches, up to 60 ms.", advice.get(0).finding());
        assertEquals("In 4 of 4 hitches, at least a quarter of the frame was in code Renderable does not time, up to"
                + " 30 ms of client tick.", advice.get(1).finding());

        FactsBuilder named = new FactsBuilder().sound("create:steam", "block", 0.1, 40);
        for (int i = 1; i <= 4; i++) named.hitch(new Facts.Hitch(i, 100, 0, 0, false, 10, 0, 0, List.of(top(steam, 60))));
        assertEquals(List.of("sound-wait"), Advisor.advise(named.build()).stream().map(Recommendation::rule).toList(),
                "the 10 ms left unnamed is under a quarter of each hitch");
    }

    @Test
    void theSoundBucketIsNamedTimeNotUnnamed() {
        // Review focus 4. Eight quiet 10 ms frames and four 60 ms hitches, each with a 45 ms tick, 40 ms of it in the
        // sound engine's tick. Facts.of leaves the bucket out of the tick's unnamed time, so the hitches are
        // sound-wait's, and unattributed-stall's repeat form does not count them again.
        long ms = TestSessions.MS;
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * ms, 2.0, 16 * ms, 5, 0, 0), () -> now[0], 0);
        long t = 0;
        for (int i = 0; i < 12; i++) {
            boolean hitch = i % 3 == 2;
            now[0] = t;           s.onRenderPre(0, 0);
            now[0] = t + 5 * ms;  s.onRenderPost();
            if (hitch) {
                s.onTickPre();
                s.begin(null, Bucket.SOUND); now[0] = t + 45 * ms; s.end();
                now[0] = t + 50 * ms; s.onTickPost();
            }
            t += hitch ? 60 * ms : 10 * ms;
        }
        now[0] = t;
        s.onRenderPre(0, 0);

        List<Recommendation> advice = Advisor.advise(s.finish(), Environment.empty());
        assertEquals(List.of("sound-wait"), advice.stream().map(Recommendation::rule).toList(),
                "5 ms of each hitch's tick is unnamed, under a quarter of it");
        assertEquals("In 4 of 4 hitches, the sound engine took at least a quarter of the frame (up to 40 ms).",
                advice.get(0).finding());
    }
}
