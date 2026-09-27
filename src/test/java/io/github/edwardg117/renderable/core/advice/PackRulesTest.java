package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.ModKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.edwardg117.renderable.core.advice.FactsBuilder.hitch;
import static io.github.edwardg117.renderable.core.advice.FactsBuilder.top;
import static org.junit.jupiter.api.Assertions.*;

class PackRulesTest {

    static List<Recommendation> run(Facts f, java.util.function.BiConsumer<Facts, List<Recommendation>> rule) {
        List<Recommendation> out = new ArrayList<>();
        rule.accept(f, out);
        return out;
    }

    /** slowmod, with {@code handlersMs} of steady cost, in the given hitches. */
    static Facts slowmod(double handlersMs, Facts.Hitch... hitches) {
        FactsBuilder b = new FactsBuilder().mod("slowmod", handlersMs, 0, 0, "ClientTickEvent.Post");
        for (Facts.Hitch h : hitches) b.hitch(h);
        return b.build();
    }

    @Test
    void anUnattributedStallNamesTheWorstHitch() {
        Facts f = new FactsBuilder()
                .hitch(new Facts.Hitch(1, 382.4, 0, 0, false, 374, 1, 0, List.of()))
                .hitch(new Facts.Hitch(2, 112.1, 0, 0, false, 101, 1, 0, List.of()))
                .build();
        Recommendation a = run(f, PackRules::unattributedStall).get(0);
        assertEquals(Recommendation.Audience.PACK_AUTHOR, a.audience());
        assertEquals("Hitch #1 (382 ms): 374 ms of client tick is in code Renderable does not time.", a.finding());
        assertEquals("Profile the same place with a sampling profiler such as spark, which names the code.", a.action());
        assertEquals(374, a.worstMs(), 1e-9);
        assertEquals(0.475, a.msPerFrame(), 1e-9, "both stalls, over the 1,000 frames");

        assertTrue(run(new FactsBuilder()
                .hitch(new Facts.Hitch(1, 80, 0, 0, false, 20, 1, 0, List.of()))
                .hitch(new Facts.Hitch(2, 200, 0, 0, false, 0, 60, 0, List.of()))
                .build(), PackRules::unattributedStall).isEmpty(), "too small, or under half the frame");
        assertTrue(run(new FactsBuilder().hitch(new Facts.Hitch(1, 382.4, 350, 0, false, 374, 1, 0, List.of())).build(),
                PackRules::unattributedStall).isEmpty(), "a 350 ms GC pause leaves 24 ms untimed");
    }

    @Test
    void anUnattributedStallAtItsThresholdsAndInEachWindow() {
        assertEquals("Hitch #1 (100 ms): 50 ms of rendering is in code Renderable does not time.",
                run(new FactsBuilder().hitch(new Facts.Hitch(1, 100, 0, 0, false, 0, 50, 0, List.of())).build(),
                        PackRules::unattributedStall).get(0).finding(), "exactly 50 ms, and exactly half the frame");
        assertEquals("Hitch #1 (120 ms): 70 ms of main-thread tasks is in code Renderable does not time.",
                run(new FactsBuilder().hitch(new Facts.Hitch(1, 120, 0, 0, false, 0, 0, 70, List.of())).build(),
                        PackRules::unattributedStall).get(0).finding());
        assertTrue(run(new FactsBuilder().hitch(new Facts.Hitch(1, 80, 0, 0, false, 45, 0, 0, List.of())).build(),
                PackRules::unattributedStall).isEmpty(), "over half the frame, but under 50 ms");
    }

    @Test
    void modHandlersRankBySteadyCostOrByTheirWorstStall() {
        ModKey tick = ModKey.event("slowmod", "net.neoforged.neoforge.client.event.ClientTickEvent$Post");
        Facts f = new FactsBuilder()
                .mod("somemod", 1.2, 0, 0, "RenderLevelStageEvent")
                .mod("slowmod", 0.2, 0, 0, "ClientTickEvent.Post")
                .mod("minecraft", 5, 5, 5, "RenderLevelStageEvent")
                .mod("neoforge", 5, 0, 0, "RenderLevelStageEvent")
                .mod("unknown", 5, 0, 0, "RenderLevelStageEvent")
                .hitch(hitch(1, 382.4, top(tick, 370)))
                .build();
        List<Recommendation> r = run(f, PackRules::modHandlers);
        assertEquals(2, r.size(), "never for minecraft, neoforge or unknown");
        assertEquals(Recommendation.Kind.FRAME_RATE, r.get(0).kind());
        assertEquals("somemod's event handlers", r.get(0).title());
        assertEquals("somemod's event handlers take 1.2 ms a frame, most of it in RenderLevelStageEvent.", r.get(0).finding());
        assertEquals("Check the mod's config for the feature behind that event, or report it to the mod with this file.",
                r.get(0).action());
        assertEquals(Recommendation.Kind.STUTTER, r.get(1).kind());
        assertEquals("slowmod's ClientTickEvent.Post handler took 370 ms in hitch #1.", r.get(1).finding());
        assertEquals(370, r.get(1).worstMs(), 1e-9);

        ModKey stage = ModKey.event("repeatmod", "net.neoforged.neoforge.client.event.RenderLevelStageEvent");
        FactsBuilder repeated = new FactsBuilder().mod("repeatmod", 0.1, 0, 0, "RenderLevelStageEvent");
        for (int i = 1; i <= 4; i++) repeated.hitch(hitch(i, 80, top(stage, 20 + i)));
        Recommendation again = run(repeated.build(), PackRules::modHandlers).get(0);
        assertEquals(Recommendation.Kind.STUTTER, again.kind(), "under both the stall form and the steady threshold");
        assertEquals("repeatmod's RenderLevelStageEvent handler was a top cost in 4 of 4 hitches, up to 24 ms.", again.finding());
        assertEquals(24, again.worstMs(), 1e-9);
    }

    @Test
    void aHandlerStallsAtFiftyMillisecondsAndHalfTheHitch() {
        ModKey tick = ModKey.event("slowmod", "net.neoforged.neoforge.client.event.ClientTickEvent$Post");
        assertEquals("slowmod's ClientTickEvent.Post handler took 50 ms in hitch #1.",
                run(slowmod(0.2, hitch(1, 100, top(tick, 50))), PackRules::modHandlers).get(0).finding(), "exactly both");
        assertTrue(run(slowmod(0.2, hitch(1, 200, top(tick, 60))), PackRules::modHandlers).isEmpty(), "under half the hitch");
        assertTrue(run(slowmod(0.2, hitch(1, 60, top(tick, 49))), PackRules::modHandlers).isEmpty(), "under 50 ms");
        assertTrue(run(slowmod(0.2, hitch(1, 382.4, new Facts.Top(tick, 370, true))), PackRules::modHandlers).isEmpty(),
                "a GC pause could account for it");
        assertTrue(run(slowmod(0.2, hitch(1, 382.4, top(ModKey.packet("slowmod", "slowmod:sync"), 370))),
                PackRules::modHandlers).isEmpty(), "a payload is not a handler");

        Recommendation largest = run(slowmod(0.2, hitch(1, 200, top(tick, 120)), hitch(2, 150, top(tick, 90))),
                PackRules::modHandlers).get(0);
        assertEquals("slowmod's ClientTickEvent.Post handler took 120 ms in hitch #1.", largest.finding(), "the largest stall");
        assertEquals(120, largest.worstMs(), 1e-9);
        assertEquals(0.2, largest.msPerFrame(), 1e-9, "the steady cost, whatever the form");

        Recommendation costly = run(slowmod(0.5, hitch(1, 382.4, top(tick, 370))), PackRules::modHandlers).get(0);
        assertEquals("slowmod's ClientTickEvent.Post handler took 370 ms in hitch #1. slowmod's event handlers take 0.5 ms"
                + " a frame.", costly.finding(), "at the steady threshold, the average follows");
        assertEquals(1.2, run(new FactsBuilder().mod("somemod", 1.2, 0, 0, "RenderLevelStageEvent").build(),
                PackRules::modHandlers).get(0).msPerFrame(), 1e-9);
    }

    @Test
    void aHandlerRepeatsByHitchRepeatsTest() {
        ModKey stage = ModKey.event("repeatmod", "net.neoforged.neoforge.client.event.RenderLevelStageEvent");
        ModKey tick = ModKey.event("repeatmod", "net.neoforged.neoforge.client.event.ClientTickEvent$Post");
        FactsBuilder small = new FactsBuilder().mod("repeatmod", 0.1, 0, 0, "RenderLevelStageEvent");
        for (int i = 1; i <= 4; i++) small.hitch(hitch(i, 80, top(stage, 19)));
        assertTrue(run(small.build(), PackRules::modHandlers).isEmpty(), "under a quarter of each hitch");

        FactsBuilder two = new FactsBuilder().mod("repeatmod", 0.1, 0, 0, "RenderLevelStageEvent");
        for (int i = 1; i <= 2; i++) two.hitch(hitch(i, 80, top(stage, 30)));
        assertTrue(run(two.build(), PackRules::modHandlers).isEmpty(), "two hitches are not a repeat");

        FactsBuilder spread = new FactsBuilder().mod("repeatmod", 0.1, 0, 0, "RenderLevelStageEvent");
        for (int i = 1; i <= 13; i++) spread.hitch(hitch(i, 80, top(i <= 3 ? stage : ModKey.event("othermod", "a.B"), 30)));
        assertTrue(run(spread.build(), PackRules::modHandlers).isEmpty(), "3 of 13 hitches is under a quarter of them");

        FactsBuilder most = new FactsBuilder().mod("repeatmod", 0.1, 0, 0, "RenderLevelStageEvent");
        most.hitch(hitch(1, 80, top(stage, 25)));
        for (int i = 2; i <= 4; i++) most.hitch(hitch(i, 80, top(stage, 25), top(tick, 22 + i)));
        assertEquals("repeatmod's RenderLevelStageEvent handler was a top cost in 4 of 4 hitches, up to 25 ms.",
                run(most.build(), PackRules::modHandlers).get(0).finding(), "the handler in the most hitches");

        FactsBuilder both = new FactsBuilder().mod("repeatmod", 0.1, 0, 0, "RenderLevelStageEvent");
        both.hitch(hitch(1, 200, top(tick, 120)));
        for (int i = 2; i <= 5; i++) both.hitch(hitch(i, 80, top(stage, 30)));
        Recommendation stall = run(both.build(), PackRules::modHandlers).get(0);
        assertEquals("repeatmod's ClientTickEvent.Post handler took 120 ms in hitch #1.", stall.finding(), "a stall comes first");
        assertEquals(120, stall.worstMs(), 1e-9);

        FactsBuilder worse = new FactsBuilder().mod("repeatmod", 0.1, 0, 0, "RenderLevelStageEvent");
        for (int i = 1; i <= 3; i++) worse.hitch(hitch(i, 200, top(stage, 95 - 5 * i)));   // 45% to 40%: not stalls
        worse.hitch(hitch(4, 100, top(tick, 55)));
        Recommendation repeat = run(worse.build(), PackRules::modHandlers).get(0);
        assertEquals("repeatmod's RenderLevelStageEvent handler was a top cost in 3 of 4 hitches, up to 90 ms.",
                repeat.finding(), "unless a repeat was worse than the stall");
        assertEquals(90, repeat.worstMs(), 1e-9);

        FactsBuilder same = new FactsBuilder().mod("repeatmod", 0.1, 0, 0, "ClientTickEvent.Post");
        same.hitch(hitch(1, 200, top(tick, 120)));
        for (int i = 2; i <= 5; i++) same.hitch(hitch(i, 80, top(tick, 30)));
        assertEquals("repeatmod's ClientTickEvent.Post handler was a top cost in 5 of 5 hitches, up to 120 ms.",
                run(same.build(), PackRules::modHandlers).get(0).finding(), "one handler's stall and repeat: the repeat says both");
    }

    @Test
    void modHudAndNetworkNameTheMod() {
        ModKey sync = ModKey.packet("netmod", "netmod:sync");
        FactsBuilder b = new FactsBuilder()
                .mod("hudmod", 0, 0.8, 0, "")
                .mod("netmod", 0, 0, 0.6, "")
                .mod("quietmod", 0, 0, 0.3, "");
        for (int i = 1; i <= 4; i++) b.hitch(hitch(i, 60, top(sync, 20 + i)));
        Facts f = b.build();

        Recommendation hud = run(f, PackRules::modHud).get(0);
        assertEquals("hudmod's HUD layers take 0.8 ms a frame.", hud.finding());
        assertEquals("Check the mod's HUD settings, or report it to the mod with this file.", hud.action());

        List<Recommendation> network = run(f, PackRules::modNetwork);
        assertEquals(1, network.size(), "quietmod is under half a millisecond and in no hitch");
        assertEquals(Recommendation.Kind.STUTTER, network.get(0).kind());
        assertEquals("The netmod:sync payload was a top cost in 4 of 4 hitches, up to 24 ms. netmod's payloads take 0.6 ms"
                + " a frame on the client.", network.get(0).finding());
        assertEquals("The server is sending work the client handles on its main thread; report it to the mod with this file.",
                network.get(0).action());
        assertEquals(24, network.get(0).worstMs(), 1e-9);
    }

    @Test
    void aPayloadTypeStuttersWhateverTheModsSteadyCost() {
        ModKey sync = ModKey.packet("netmod", "netmod:sync");
        ModKey big = ModKey.packet("netmod", "netmod:big");
        FactsBuilder low = new FactsBuilder().mod("netmod", 0, 0, 0.02, "");
        for (int i = 1; i <= 3; i++) low.hitch(hitch(i, 60, top(sync, 20 + i)));
        Recommendation repeat = run(low.build(), PackRules::modNetwork).get(0);
        assertEquals(Recommendation.Kind.STUTTER, repeat.kind());
        assertEquals("The netmod:sync payload was a top cost in 3 of 3 hitches, up to 23 ms.", repeat.finding(),
                "no steady figure under half a millisecond a frame");
        assertEquals(23, repeat.worstMs(), 1e-9);
        assertEquals(0.02, repeat.msPerFrame(), 1e-9, "the steady cost");

        Recommendation stall = run(new FactsBuilder().mod("netmod", 0, 0, 0.15, "").hitch(hitch(1, 300, top(big, 150))).build(),
                PackRules::modNetwork).get(0);
        assertEquals("The netmod:big payload took 150 ms in hitch #1.", stall.finding(), "one payload took half the hitch");
        assertEquals(150, stall.worstMs(), 1e-9);

        FactsBuilder mixed = new FactsBuilder().mod("netmod", 0, 0, 0.7, "");
        for (int i = 1; i <= 3; i++) mixed.hitch(hitch(i, 60, top(sync, 20 + i)));
        mixed.hitch(hitch(4, 300, top(big, 120)));
        Recommendation named = run(mixed.build(), PackRules::modNetwork).get(0);
        assertEquals("The netmod:sync payload was a top cost in 3 of 4 hitches, up to 23 ms. netmod's payloads take 0.7 ms"
                + " a frame on the client.", named.finding());
        assertEquals(23, named.worstMs(), 1e-9, "the named type's worst, not netmod:big's 120 ms");
    }

    @Test
    void modNetworkEdges() {
        ModKey sync = ModKey.packet("netmod", "netmod:sync");
        Recommendation steady = run(new FactsBuilder().mod("netmod", 0, 0, 0.5, "").build(), PackRules::modNetwork).get(0);
        assertEquals(Recommendation.Kind.FRAME_RATE, steady.kind());
        assertEquals("netmod's payloads take 0.5 ms a frame on the client.", steady.finding(), "exactly the threshold");
        assertEquals(0.5, steady.msPerFrame(), 1e-9);
        assertTrue(run(new FactsBuilder().mod("minecraft", 0, 0, 3, "").build(), PackRules::modNetwork).isEmpty(), "vanilla packets");

        FactsBuilder notCauses = new FactsBuilder().mod("netmod", 0.1, 0, 0.1, "");
        for (int i = 1; i <= 4; i++) {
            notCauses.hitch(hitch(i, 80, top(sync, 19), top(ModKey.event("netmod", "a.SomeEvent"), 30)));
        }
        assertTrue(run(notCauses.build(), PackRules::modNetwork).isEmpty(),
                "a payload under a quarter of each hitch, and a handler, which is not a payload");

        FactsBuilder three = new FactsBuilder().mod("netmod", 0, 0, 0.1, "");
        for (int i = 1; i <= 3; i++) three.hitch(hitch(i, 80, top(sync, 20)));
        assertEquals(Recommendation.Kind.STUTTER, run(three.build(), PackRules::modNetwork).get(0).kind(), "exactly three hitches");
    }

    @Test
    void aHudLayerStuttersLikeAHandler() {
        ModKey minimap = ModKey.layer("mapmod", "mapmod:minimap");
        Recommendation stall = run(new FactsBuilder().mod("mapmod", 0, 0.2, 0, "").hitch(hitch(1, 160, top(minimap, 90))).build(),
                PackRules::modHud).get(0);
        assertEquals(Recommendation.Kind.STUTTER, stall.kind());
        assertEquals("mapmod's HUD", stall.title());
        assertEquals("The mapmod:minimap layer took 90 ms in hitch #1.", stall.finding());
        assertEquals(90, stall.worstMs(), 1e-9);
        assertEquals(0.2, stall.msPerFrame(), 1e-9);

        FactsBuilder repeated = new FactsBuilder().mod("mapmod", 0, 0.9, 0, "");
        for (int i = 1; i <= 3; i++) repeated.hitch(hitch(i, 60, top(minimap, 15 + i)));
        assertEquals("The mapmod:minimap layer was a top cost in 3 of 3 hitches, up to 18 ms. mapmod's HUD layers take"
                + " 0.9 ms a frame.", run(repeated.build(), PackRules::modHud).get(0).finding());

        Recommendation steady = run(new FactsBuilder().mod("hudmod", 0, 0.5, 0, "").build(), PackRules::modHud).get(0);
        assertEquals(Recommendation.Kind.FRAME_RATE, steady.kind(), "exactly the threshold");
        assertEquals(0.5, steady.msPerFrame(), 1e-9);
        assertTrue(run(new FactsBuilder().mod("minecraft", 0, 3, 0, "").build(), PackRules::modHud).isEmpty(), "vanilla layers");
    }

    @Test
    void unbalancedTimingIsANoteForThePackAuthor() {
        Recommendation a = run(new FactsBuilder().unbalanced(12).build(), PackRules::stackResets).get(0);
        assertEquals(Recommendation.Kind.NOTE, a.kind());
        assertEquals("12 frames had unbalanced timing: a timed call did not return normally.", a.finding());
        assertEquals("The game log from around the session should hold the exception; report it with the log.", a.action());
        assertTrue(run(new FactsBuilder().build(), PackRules::stackResets).isEmpty());
        assertEquals("1 frame had unbalanced timing: a timed call did not return normally.",
                run(new FactsBuilder().unbalanced(1).build(), PackRules::stackResets).get(0).finding());
    }
}
