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

    private static List<String> pairs(List<String> headers, String[] cells) {
        assertEquals(headers.size(), cells.length, "one cell per header");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < cells.length; i++) out.add(headers.get(i) + "=" + cells[i]);
        return out;
    }
}
