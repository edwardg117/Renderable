package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MeshTableTest {

    @Test
    void blockAndSectionCellsSitUnderTheirHeaders() {
        SessionResult p3 = TestSessions.phase3();
        double seconds = p3.stats().durationSeconds();

        MeshEntry stone = Ranking.mesh(p3, ProfileKey.Category.BLOCK, Ranking.MeshSort.TIME).get(0);
        assertEquals(List.of("block=minecraft:stone", "mesh ms/s=20.8", "us/call=20.0", "calls=400"),
                pairs(MeshTable.BLOCK, MeshTable.block(stone, seconds)));

        MeshEntry section = Ranking.mesh(p3, ProfileKey.Category.SECTION, Ranking.MeshSort.CALLS).get(0);
        assertEquals(List.of("section=section 0 4 0", "rebuilds=3", "rebuilds/s=7.8", "mesh ms=9.0", "avg ms/rebuild=3.00"),
                pairs(MeshTable.SECTION, MeshTable.section(section, seconds)));

        assertEquals(MeshTable.SECTION.subList(0, 4), MeshTable.SECTION_SCREEN.subList(0, 4),
                "the screen differs only in the last header, which it shortens to fit (spec addendum §P4.C6 #1)");
        assertEquals("avg ms/rb", MeshTable.SECTION_SCREEN.get(4));
    }

    private static List<String> pairs(List<String> headers, String[] cells) {
        assertEquals(headers.size(), cells.length, "one cell per header");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < cells.length; i++) out.add(headers.get(i) + "=" + cells[i]);
        return out;
    }
}
