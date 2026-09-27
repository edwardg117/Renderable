package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Environment;
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
}
