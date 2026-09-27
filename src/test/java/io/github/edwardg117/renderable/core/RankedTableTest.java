package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.edwardg117.renderable.core.TestSessions.PRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RankedTableTest {

    @Test
    void everyCellSitsUnderItsHeader() {
        SessionResult p3 = TestSessions.phase3();
        int frames = p3.frameCount();
        long total = p3.stats().totalNanos();

        RankedEntry press = Ranking.byKey(p3, ProfileKey.Category.BLOCK_ENTITY).get(0);
        assertEquals(PRESS, press.key());
        assertEquals(List.of("name=row name", "render us/f=4900.0", "tick us/f=400.0", "share=27.6%", "peak ms=60.40",
                        "gc f=0", "shadow=0.0%", "verts/f=100.0", "calls=20"),
                pairs(RankedTable.RANKED, RankedTable.ranked("row name", press, frames, total)));

        RankedEntry flame = Ranking.byKey(p3, ProfileKey.Category.PARTICLE).get(0);
        assertEquals(List.of("class=FlameParticle", "render us/f=300.0", "tick us/f=100.0", "share=2.1%",
                        "avg live=3.0", "verts/f=0.0", "gc f=0"),
                pairs(RankedTable.PARTICLE, RankedTable.particle(flame, frames, total)));
    }

    @Test
    void theGcNoteSplitsIntoScreenLinesThatJoinIntoTheReportsSentence() {
        // The report has always printed this sentence; splitting it for the screen must not change it.
        assertEquals("(gc f counts frames a pause could account for; those are left out of peak ms, and \"-\" means none was clean)",
                RankedTable.GC_COLUMN_NOTE);
        assertEquals(2, RankedTable.GC_COLUMN_NOTE_LINES.size());
    }

    @Test
    void modsRowsCarryTotalsNotSplits() {
        SessionResult r = TestSessions.phase5();
        RankedEntry create = Ranking.byType(r, ProfileKey.Category.MOD).get(0);
        assertEquals(List.of("mod=create", "share=10.0%", "us/f=2000.0", "peak ms=2.00", "gc f=0", "calls/f=1.0"),
                pairs(RankedTable.MODS, RankedTable.mods(create.typeId(), create, r.frameCount(), r.stats().totalNanos())));
        assertEquals(2, RankedTable.MODS_NOTE_LINES.size());
    }

    @Test
    void aModsRowAddsItsTickTimeAndTickCalls() {
        SessionResult r = TestSessions.phase5();
        int frames = r.frameCount();
        long total = r.stats().totalNanos();
        List<RankedEntry> mods = Ranking.byType(r, ProfileKey.Category.MOD);
        RankedEntry somemod = mods.get(mods.size() - 1);
        assertEquals("somemod", somemod.typeId());
        assertEquals(List.of("mod=somemod", "share=3.0%", "us/f=600.0", "peak ms=0.60", "gc f=0", "calls/f=2.0"),
                pairs(RankedTable.MODS, RankedTable.mods(somemod.typeId(), somemod, frames, total)),
                "0.4 ms of render and 0.8 ms of tick over two frames, one render call and one tick call a frame");
        RankedEntry tick = Ranking.membersOf(r, ProfileKey.Category.MOD, "somemod").get(0);
        assertEquals(TestSessions.TICK_HANDLER, tick.key());
        assertEquals(List.of("mod=member", "share=2.0%", "us/f=400.0", "peak ms=0.40", "gc f=0", "calls/f=1.0"),
                pairs(RankedTable.MODS, RankedTable.mods("member", tick, frames, total)),
                "a member that only ticks shows its tick time and its tick calls");
    }

    private static List<String> pairs(List<String> headers, String[] cells) {
        assertEquals(headers.size(), cells.length, "one cell per header");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < cells.length; i++) out.add(headers.get(i) + "=" + cells[i]);
        return out;
    }

    @Test
    void theShadowColumnCanBeLeftOut() {
        SessionResult p3 = TestSessions.phase3();
        RankedEntry press = Ranking.byKey(p3, ProfileKey.Category.BLOCK_ENTITY).get(0);
        assertEquals(List.of("name=row name", "render us/f=4900.0", "tick us/f=400.0", "share=27.6%", "peak ms=60.40",
                        "gc f=0", "verts/f=100.0", "calls=20"),
                pairs(RankedTable.RANKED_WITHOUT_SHADOW, RankedTable.withoutShadow(
                        RankedTable.ranked("row name", press, p3.frameCount(), p3.stats().totalNanos()))));
    }
}
