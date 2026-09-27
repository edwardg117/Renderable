package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.ModKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.edwardg117.renderable.core.advice.FactsBuilder.hitch;
import static io.github.edwardg117.renderable.core.advice.FactsBuilder.top;
import static org.junit.jupiter.api.Assertions.*;

class SceneRulesTest {

    static List<Recommendation> run(Facts f, java.util.function.BiConsumer<Facts, List<Recommendation>> rule) {
        List<Recommendation> out = new ArrayList<>();
        rule.accept(f, out);
        return out;
    }

    @Test
    void typesFireForTheTopThreeAboveAMillisecond() {
        Facts f = new FactsBuilder()
                .blockEntity("create:factory_panel", 1.1, 212, "120 64 -340")
                .blockEntity("create:belt", 1.05, 40, "1 2 3")
                .blockEntity("create:shaft", 1.02, 300, "4 5 6")
                .blockEntity("create:gearbox", 1.01, 9, "7 8 9")
                .build();
        List<Recommendation> r = run(f, SceneRules::blockEntityTypes);
        assertEquals(3, r.size(), "at most three types");
        assertEquals("create:factory_panel takes 1.1 ms a frame across 212 blocks; the most expensive is at 120 64 -340.",
                r.get(0).finding());
        assertEquals("It is the build, not a setting: fewer of them in view at once costs less."
                + " Highlight on the Block Entities tab shows where they are.", r.get(0).action());
        assertTrue(run(new FactsBuilder().blockEntity("create:shaft", 0.9, 300, "").build(), SceneRules::blockEntityTypes).isEmpty());
    }

    @Test
    void entityAndParticleFindingsNameTheirCounts() {
        Recommendation e = run(new FactsBuilder().entity("minecraft:item_frame", 1.3, 340).build(), SceneRules::entityTypes).get(0);
        assertEquals("minecraft:item_frame takes 1.3 ms a frame across 340 entities.", e.finding());
        assertEquals("Fewer of them nearby, or a lower Entity Distance.", e.action());

        Recommendation p = run(new FactsBuilder().particle("DripParticle", 0.7, 1400).build(), SceneRules::particleClasses).get(0);
        assertEquals("DripParticle takes 0.7 ms a frame, with 1,400 drawn on average.", p.finding());
        assertEquals("Find what emits them; lowering Particles hides most of them.", p.action());
    }

    @Test
    void hitchRepeatNeedsThreeHitchesAndAQuarterOfThem() {
        BlockEntityKey crafter = new BlockEntityKey("create:mechanical_crafter", "minecraft:overworld", 118, 64, -338);
        ModKey handler = ModKey.event("somemod", "a.ClientTickEvent$Post");
        FactsBuilder b = new FactsBuilder();
        for (int i = 1; i <= 13; i++) {
            double ms = i <= 5 ? 30 + 2 * i : 0;
            b.hitch(i <= 5 ? hitch(i, 60, top(crafter, ms), top(handler, 40)) : hitch(i, 60, top(handler, 40)));
        }
        List<Recommendation> r = run(b.build(), SceneRules::hitchRepeat);
        assertEquals(1, r.size(), "the handler is a mod key, which the mod rules cover");
        Recommendation a = r.get(0);
        assertEquals(Recommendation.Kind.STUTTER, a.kind());
        assertEquals("create:mechanical_crafter @ 118 64 -338 was a top cost in 5 of 13 hitches, up to 40 ms.", a.finding());
        assertEquals(40.0, a.worstMs(), 1e-9);
        assertEquals((32 + 34 + 36 + 38 + 40) / 1000.0, a.msPerFrame(), 1e-9, "its hitch time over the 1,000 frames");

        FactsBuilder few = new FactsBuilder();
        for (int i = 1; i <= 13; i++) few.hitch(i <= 3 ? hitch(i, 60, top(crafter, 30)) : hitch(i, 60));
        assertTrue(run(few.build(), SceneRules::hitchRepeat).isEmpty(), "3 of 13 is under a quarter");

        FactsBuilder steady = new FactsBuilder();
        for (int i = 1; i <= 4; i++) steady.hitch(hitch(i, 60, top(crafter, 2)));
        assertTrue(run(steady.build(), SceneRules::hitchRepeat).isEmpty(),
                "a steady 2 ms is in every hitch's top list without being its cause");

        FactsBuilder paused = new FactsBuilder();
        for (int i = 1; i <= 4; i++) paused.hitch(hitch(i, 60, new Facts.Top(crafter, 40, true)));
        assertTrue(run(paused.build(), SceneRules::hitchRepeat).isEmpty(), "a GC pause could account for it");
    }

    @Test
    void typeRulesFireAtTheirThresholdAndCarryTheirCost() {
        Recommendation be = run(new FactsBuilder().blockEntity("create:crushing_wheel", 1.0, 1, "").build(),
                SceneRules::blockEntityTypes).get(0);
        assertEquals("block-entity-type", be.rule());
        assertEquals(Recommendation.Kind.FRAME_RATE, be.kind());
        assertEquals(Recommendation.Audience.PLAYER, be.audience());
        assertEquals("Costly block entity: create:crushing_wheel", be.title());
        assertEquals("create:crushing_wheel takes 1.0 ms a frame across 1 block.", be.finding(), "one block, no position");
        assertEquals(1.0, be.msPerFrame(), 1e-9);

        Recommendation e = run(new FactsBuilder().entity("minecraft:ender_dragon", 1.0, 1).build(), SceneRules::entityTypes).get(0);
        assertEquals("entity-type", e.rule());
        assertEquals(Recommendation.Kind.FRAME_RATE, e.kind());
        assertEquals("Costly entity: minecraft:ender_dragon", e.title());
        assertEquals("minecraft:ender_dragon takes 1.0 ms a frame across 1 entity.", e.finding());
        assertEquals(1.0, e.msPerFrame(), 1e-9);
        assertTrue(run(new FactsBuilder().entity("minecraft:item_frame", 0.9, 340).build(), SceneRules::entityTypes).isEmpty());

        Recommendation p = run(new FactsBuilder().particle("DripParticle", 0.5, 1234.5).build(), SceneRules::particleClasses).get(0);
        assertEquals("particle-type", p.rule());
        assertEquals(Recommendation.Kind.FRAME_RATE, p.kind());
        assertEquals("Costly particle: DripParticle", p.title());
        assertEquals("DripParticle takes 0.5 ms a frame, with 1,235 drawn on average.", p.finding(), "rounded, not truncated");
        assertEquals(0.5, p.msPerFrame(), 1e-9);
        assertTrue(run(new FactsBuilder().particle("DripParticle", 0.4, 1400).build(), SceneRules::particleClasses).isEmpty());
    }

    @Test
    void entityAndParticleRulesAlsoStopAtThree() {
        FactsBuilder b = new FactsBuilder();
        for (int i = 4; i >= 1; i--) b.entity("mod:entity" + i, 1 + i, 10).particle("Particle" + i, 1 + i, 10);
        Facts f = b.build();
        assertEquals(3, run(f, SceneRules::entityTypes).size());
        assertEquals(3, run(f, SceneRules::particleClasses).size());
    }

    @Test
    void hitchRepeatCountsExactlyAQuarterAndNeedsThreeHitches() {
        BlockEntityKey crafter = new BlockEntityKey("create:mechanical_crafter", "minecraft:overworld", 118, 64, -338);
        FactsBuilder quarter = new FactsBuilder();
        for (int i = 1; i <= 4; i++) quarter.hitch(hitch(i, 60, top(crafter, 15)));
        Recommendation a = run(quarter.build(), SceneRules::hitchRepeat).get(0);
        assertEquals("create:mechanical_crafter @ 118 64 -338 was a top cost in 4 of 4 hitches, up to 15 ms.", a.finding(),
                "15 of 60 ms is exactly a quarter of each hitch");
        assertEquals("hitch-repeat", a.rule());
        assertEquals(Recommendation.Audience.PLAYER, a.audience());
        assertEquals("Repeated stutter: create:mechanical_crafter @ 118 64 -338", a.title());
        assertEquals("Look at what it is doing when the stutter happens. A pack author can take this report to the mod.",
                a.action());
        assertEquals("up to 15 ms", a.atStake());

        FactsBuilder two = new FactsBuilder();
        for (int i = 1; i <= 2; i++) two.hitch(hitch(i, 60, top(crafter, 40)));
        assertTrue(run(two.build(), SceneRules::hitchRepeat).isEmpty(), "every hitch, but only two of them");

        FactsBuilder threeOfTwelve = new FactsBuilder();
        for (int i = 1; i <= 12; i++) threeOfTwelve.hitch(i <= 3 ? hitch(i, 60, top(crafter, 30)) : hitch(i, 60));
        assertEquals(1, run(threeOfTwelve.build(), SceneRules::hitchRepeat).size(), "3 of 12 is exactly a quarter");
    }
}
