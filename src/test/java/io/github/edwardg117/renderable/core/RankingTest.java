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
        assertEquals(Ranking.Sort.TOTAL, Ranking.Sort.LABEL.next());
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

    @Test
    void ascendingFlipsTheColumnButNotItsTies() {
        SessionResult p3 = TestSessions.phase3();
        List<RankedEntry> be = Ranking.byKey(p3, ProfileKey.Category.BLOCK_ENTITY);

        // Vertices: PRESS 2000, MIXER 400, PRESS2 none. The two-argument form has always meant descending.
        assertEquals(List.of(PRESS, MIXER, PRESS2), keys(Ranking.sort(be, Ranking.Sort.VERTICES)));
        assertEquals(List.of(PRESS, MIXER, PRESS2), keys(Ranking.sort(be, Ranking.Sort.VERTICES, false)));
        assertEquals(List.of(PRESS2, MIXER, PRESS), keys(Ranking.sort(be, Ranking.Sort.VERTICES, true)));

        // LABEL is A to Z ascending and Z to A descending: mixer @ 90 65 -22, press @ 100 64 -20, press @ 103 64 -20.
        assertEquals(List.of(MIXER, PRESS, PRESS2), keys(Ranking.sort(be, Ranking.Sort.LABEL, true)));
        assertEquals(List.of(PRESS2, PRESS, MIXER), keys(Ranking.sort(be, Ranking.Sort.LABEL, false)));

        // No row in phase3() has shadow time, so all three tie on SHADOW and the label decides, A to Z both ways.
        assertEquals(List.of(MIXER, PRESS, PRESS2), keys(Ranking.sort(be, Ranking.Sort.SHADOW, false)));
        assertEquals(List.of(MIXER, PRESS, PRESS2), keys(Ranking.sort(be, Ranking.Sort.SHADOW, true)));
    }

    @Test
    void shadowAndGcFramesSortByWhatTheirColumnsShow() {
        // The shadow column shows shadow time as a share of the row's own render time, so a small renderer that
        // is mostly shadow outranks a large one that is partly shadow. Largest first, shadow share orders these
        // rows PRESS, PRESS2, MIXER and GC frames PRESS, MIXER, PRESS2, while shadow nanoseconds and total time
        // both give PRESS2, MIXER, PRESS. None of those is label order, so each assertion fails on its own if
        // the sort reads the wrong field.
        Accumulator a = new Accumulator(PRESS);    // 50 us render, 30 us of it shadow: 60%, 5 GC frames
        a.renderNanos = 50_000;
        a.shadowNanos = 30_000;
        a.gcFrames = 5;
        Accumulator b = new Accumulator(PRESS2);   // 2000 us render, 800 us of it shadow: 40%, no GC frames
        b.renderNanos = 2_000_000;
        b.shadowNanos = 800_000;
        Accumulator c = new Accumulator(MIXER);    // 400 us render, 80 us of it shadow: 20%, 2 GC frames
        c.renderNanos = 400_000;
        c.shadowNanos = 80_000;
        c.gcFrames = 2;
        List<RankedEntry> rows = List.of(RankedEntry.of(a), RankedEntry.of(b), RankedEntry.of(c));

        assertEquals(List.of(PRESS, PRESS2, MIXER), keys(Ranking.sort(rows, Ranking.Sort.SHADOW)));
        assertEquals(List.of(MIXER, PRESS2, PRESS), keys(Ranking.sort(rows, Ranking.Sort.SHADOW, true)));
        assertEquals(List.of(PRESS, MIXER, PRESS2), keys(Ranking.sort(rows, Ranking.Sort.GC_FRAMES)));
        assertEquals(List.of(PRESS2, MIXER, PRESS), keys(Ranking.sort(rows, Ranking.Sort.GC_FRAMES, true)));
    }

    @Test
    void meshRowsSortByEveryColumnTheTablesShow() {
        SessionResult p3 = TestSessions.phase3();
        // stone 400 calls / 8 ms (20.0 us each), shaft 50 / 5 ms (100.0), water 20 / 1 ms (50.0)
        assertEquals("minecraft:stone",
                Ranking.mesh(p3, ProfileKey.Category.BLOCK, Ranking.MeshSort.TIME, false).get(0).key().label());
        assertEquals("minecraft:water",
                Ranking.mesh(p3, ProfileKey.Category.BLOCK, Ranking.MeshSort.TIME, true).get(0).key().label());
        assertEquals("create:shaft",
                Ranking.mesh(p3, ProfileKey.Category.BLOCK, Ranking.MeshSort.PER_CALL, false).get(0).key().label());
        assertEquals("minecraft:stone",
                Ranking.mesh(p3, ProfileKey.Category.BLOCK, Ranking.MeshSort.CALLS, false).get(0).key().label());
        assertEquals("create:shaft",
                Ranking.mesh(p3, ProfileKey.Category.BLOCK, Ranking.MeshSort.LABEL, true).get(0).key().label());
        assertEquals("minecraft:water",
                Ranking.mesh(p3, ProfileKey.Category.BLOCK, Ranking.MeshSort.LABEL, false).get(0).key().label());

        // section (0,4,0) 3 rebuilds / 9 ms = 3.00 ms each; (1,4,0) 1 rebuild / 2 ms = 2.00 ms each
        assertEquals(SECTION_A,
                Ranking.mesh(p3, ProfileKey.Category.SECTION, Ranking.MeshSort.PER_CALL, false).get(0).key());
        assertEquals(SECTION_B,
                Ranking.mesh(p3, ProfileKey.Category.SECTION, Ranking.MeshSort.PER_CALL, true).get(0).key());
        assertEquals(SECTION_A,
                Ranking.mesh(p3, ProfileKey.Category.SECTION, Ranking.MeshSort.CALLS).get(0).key(),
                "the three-argument form still means descending");
    }

    @Test
    void meshTimeAndCallsAreDifferentSortsAndTiesReadAToZ() {
        // phase3() ranks every mesh row the same by time as by calls, so it cannot tell TIME from CALLS. Here
        // stone is many cheap calls, and shaft and water tie on one call each. The session's own list puts water
        // first because it took longer, so only the label tie-break puts shaft ahead of it on calls.
        long[] clock = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> clock[0], 0);
        s.onRenderPre(0, 0);
        for (int i = 0; i < 10; i++) s.mesh().get(STONE).add(100_000);   // 10 calls, 1 ms
        s.mesh().get(SHAFT).add(2 * MS);                                   // 1 call, 2 ms
        s.mesh().get(WATER).add(3 * MS);                                   // 1 call, 3 ms
        clock[0] = 16 * MS;
        s.onRenderPre(0, 0);
        SessionResult r = s.finish();

        assertEquals(List.of(WATER, SHAFT, STONE),
                meshKeys(Ranking.mesh(r, ProfileKey.Category.BLOCK, Ranking.MeshSort.TIME)));
        assertEquals(List.of(STONE, SHAFT, WATER),
                meshKeys(Ranking.mesh(r, ProfileKey.Category.BLOCK, Ranking.MeshSort.TIME, true)));
        assertEquals(List.of(STONE, SHAFT, WATER),
                meshKeys(Ranking.mesh(r, ProfileKey.Category.BLOCK, Ranking.MeshSort.CALLS)));
        assertEquals(List.of(SHAFT, WATER, STONE),
                meshKeys(Ranking.mesh(r, ProfileKey.Category.BLOCK, Ranking.MeshSort.CALLS, true)));
    }

    private static List<ProfileKey> meshKeys(List<MeshEntry> rows) {
        return rows.stream().map(MeshEntry::key).toList();
    }

    private static List<ProfileKey> keys(List<RankedEntry> rows) {
        return rows.stream().map(RankedEntry::key).toList();
    }
}
