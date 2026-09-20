package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.edwardg117.renderable.core.TestSessions.*;
import static org.junit.jupiter.api.Assertions.*;

class RankingTest {
    final SessionResult r = TestSessions.sample();

    @Test
    void byTypeRollsPositionsUp() {
        List<RankedEntry> types = Ranking.byType(r, ProfileKey.Category.BLOCK_ENTITY);
        assertEquals(2, types.size());
        RankedEntry press = types.get(0);
        assertEquals("create:mechanical_press", press.label());
        assertNull(press.key());
        assertEquals(2, press.members());
        assertEquals(128 * MS, press.renderNanos());
        assertEquals(40, press.calls());
        assertEquals(61 * MS + 500_000, press.maxFrameNanos(),
                "group peak is the type's worst frame: 60 ms press + 1.5 ms press2 in frame 10");
        assertEquals(0, press.gcFrames(),
                "frame 15's 5 ms pause makes anything from 4 ms suspect; the press type cost 3.5 ms there");
        assertEquals("create:mechanical_mixer", types.get(1).label());
    }

    @Test
    void typePeakBeatsTheWorstMemberWhenMembersShareAFrame() {
        long[] clock = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> clock[0], 0);
        Accumulator a = s.accumulator(PRESS);
        Accumulator b = s.accumulator(PRESS2);
        clock[0] = 0;        s.onRenderPre(0, 0);
        clock[0] = 1 * MS;   s.begin(a, Bucket.BLOCK_ENTITY); clock[0] = 3 * MS;  s.end();   // 2 ms
        clock[0] = 3 * MS;   s.begin(b, Bucket.BLOCK_ENTITY); clock[0] = 6 * MS;  s.end();   // 3 ms
        clock[0] = 16 * MS;  s.onRenderPre(0, 0);
        clock[0] = 17 * MS;  s.begin(a, Bucket.BLOCK_ENTITY); clock[0] = 21 * MS; s.end();   // 4 ms alone
        clock[0] = 32 * MS;  s.onRenderPre(0, 0);

        SessionResult r = s.finish();
        RankedEntry type = Ranking.byType(r, ProfileKey.Category.BLOCK_ENTITY).get(0);
        assertEquals(2, type.members());
        // Discriminates three candidates: 4 ms under the old max-of-member-peaks (max(a=4, b=3)); 7 ms
        // under a sum-of-member-peaks implementation (a's own worst frame 4 ms + b's own worst frame 3 ms,
        // which never occurred together); 5 ms for the type's actual worst frame (a=2 ms + b=3 ms, both in
        // frame 0).
        assertEquals(5 * MS, type.maxFrameNanos(), "frame 0 cost the type 5 ms; no member ever cost more than 4");
        assertEquals(4 * MS, Ranking.membersOf(r, ProfileKey.Category.BLOCK_ENTITY, "create:mechanical_press")
                .get(0).maxFrameNanos(), "individual rows still report their own worst frame");
    }

    @Test
    void byKeyListsIndividualsMostExpensiveFirst() {
        List<RankedEntry> keys = Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY);
        assertEquals(List.of(PRESS, PRESS2, MIXER), keys.stream().map(RankedEntry::key).toList());
        assertEquals(1, keys.get(0).members());
        assertEquals(PRESS.label(), keys.get(0).label());
    }

    @Test
    void membersOfReturnsOnlyThatType() {
        List<RankedEntry> members = Ranking.membersOf(r, ProfileKey.Category.BLOCK_ENTITY, "create:mechanical_press");
        assertEquals(List.of(PRESS, PRESS2), members.stream().map(RankedEntry::key).toList());
    }

    @Test
    void derivedNumbers() {
        RankedEntry press = Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY).get(0);
        assertEquals(4900.0, press.usPerFrame(20), 1e-9);
        assertEquals(4900.0, press.renderUsPerFrame(20), 1e-9);
        assertEquals(0.0, press.tickUsPerFrame(20), 1e-9);
        assertEquals(100.0 * 98 / 384, press.sharePercent(r.stats().totalNanos()), 1e-9);
        assertEquals(0.0, press.shadowPercent(), 1e-9);

        RankedEntry cow = Ranking.byKey(r, ProfileKey.Category.ENTITY).get(0);
        assertEquals(COW, cow.key());
        assertEquals(14 * MS, cow.totalNanos());
        assertEquals(200.0, cow.tickUsPerFrame(20), 1e-9);
    }

    @Test
    void sortModesReorder() {
        List<RankedEntry> entities = Ranking.byKey(r, ProfileKey.Category.ENTITY);
        assertEquals(COW, Ranking.sort(entities, Ranking.Sort.TICK).get(0).key());
        assertEquals(COW, Ranking.sort(entities, Ranking.Sort.CALLS).get(0).key());
        List<RankedEntry> blocks = Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY);
        assertEquals(PRESS, Ranking.sort(blocks, Ranking.Sort.PEAK).get(0).key());
        assertEquals(Ranking.Sort.RENDER, Ranking.Sort.TOTAL.next());
        assertEquals(Ranking.Sort.TOTAL, Ranking.Sort.CALLS.next());
    }

    @Test
    void emptyCategoryGivesEmptyList() {
        assertTrue(Ranking.byKey(r, ProfileKey.Category.PARTICLE).isEmpty());
        assertTrue(Ranking.byType(r, ProfileKey.Category.PARTICLE).isEmpty());
    }

    @Test
    void formatting() {
        assertEquals("12.3", Fmt.ms(12_345_678));
        assertEquals("0.50", Fmt.ms2(500_000));
        assertEquals("4900.0", Fmt.us(4900.0));
        assertEquals("25.5%", Fmt.pct(25.52));
        assertEquals("0.4", Fmt.secs(384 * MS));
        assertEquals("1,234,567", Fmt.count(1_234_567));
    }

    @Test
    void environmentHoldsOrderedEntries() {
        Environment env = Environment.of(new java.util.LinkedHashMap<>(java.util.Map.of("minecraft", "1.21.1")), List.of("BLOCK_ENTITY_RENDER"));
        assertEquals("1.21.1", env.entries().get("minecraft"));
        assertEquals(List.of("BLOCK_ENTITY_RENDER"), env.hooksApplied());
        assertTrue(Environment.empty().entries().isEmpty());
    }

    @Test
    void meshRowsRankByTimeOrByCalls() {
        SessionResult p3 = TestSessions.phase3();
        List<MeshEntry> blocks = Ranking.mesh(p3, ProfileKey.Category.BLOCK, Ranking.MeshSort.TIME);
        assertEquals(List.of("minecraft:stone", "create:shaft", "minecraft:water"),
                blocks.stream().map(e -> e.key().label()).toList());
        assertEquals(400, blocks.get(0).calls());
        assertEquals(8 * MS, blocks.get(0).nanos());
        assertEquals(20.0, blocks.get(0).usPerCall(), 1e-9);
        assertEquals(8 / 0.384, blocks.get(0).msPerSecond(p3.stats().durationSeconds()), 1e-9);

        List<MeshEntry> sections = Ranking.mesh(p3, ProfileKey.Category.SECTION, Ranking.MeshSort.CALLS);
        assertEquals(2, sections.size());
        assertEquals(new SectionKey("minecraft:overworld", 0, 4, 0), sections.get(0).key());
        assertEquals(3, sections.get(0).calls());
        assertEquals(9 * MS, sections.get(0).nanos());
        assertEquals(3 / 0.384, sections.get(0).callsPerSecond(p3.stats().durationSeconds()), 1e-9);
        assertTrue(Ranking.mesh(p3, ProfileKey.Category.PARTICLE, Ranking.MeshSort.TIME).isEmpty());

        assertEquals(2000, p3.accumulators().get(PRESS).vertices);
        assertEquals(400, p3.accumulators().get(MIXER).vertices);
        assertTrue(TestSessions.phase2().mesh().isEmpty());
    }
}
