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
        assertEquals(60 * MS, press.maxFrameNanos(), "group peak is the largest member peak");
        assertEquals("create:mechanical_mixer", types.get(1).label());
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
}
