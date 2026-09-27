package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.core.MeshTable;
import io.github.edwardg117.renderable.core.RankedTable;
import io.github.edwardg117.renderable.core.Ranking;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.*;

class TabTest {

    /** The vanilla font's advances for the characters the headers use. */
    static int vanillaWidth(String s) {
        int w = 0;
        for (char c : s.toCharArray()) {
            w += switch (c) {
                case 'i', '.', ',' -> 2;
                case 'l' -> 3;
                case 't', ' ' -> 4;
                case 'f', 'k' -> 5;
                default -> 6;
            };
        }
        return w;
    }

    @Test
    void aHeaderThatDoesNotFitWidensItsColumnAtTheNameColumnsExpense() {
        int arrow = vanillaWidth(" v");
        assertEquals(63, vanillaWidth("render us/f"), "the header the spec measured at GUI scale 3 on a 2560 px window");
        Tab.Columns ranked = Tab.BLOCK_ENTITIES.columns();

        int[][] plain = ranked.layout(834);   // GUI 854
        int[][] fitted = ranked.layout(834, TabTest::vanillaWidth, arrow);
        assertEquals(60, plain[1][1], "render us/f's column was 60 px");
        assertEquals(63 + arrow - (Tab.Columns.PADDING - 1), fitted[1][1], "now the header and its arrow fit");
        assertEquals(plain[1][0] - (fitted[1][1] - plain[1][1]), fitted[1][0], "the name column paid for it");
        for (int i = 2; i < ranked.size(); i++) {
            assertEquals(plain[1][i], fitted[1][i], ranked.get(i).name() + " already fitted");
            assertEquals(plain[0][i], fitted[0][i], ranked.get(i).name() + " did not move");
        }

        // GUI 427, Auto scale on 1440p: every header is short of room. The name gives at most a quarter, to the
        // smallest shortfalls first, so the four that need least fit whole and render us/f is left to truncate.
        int[][] narrowPlain = ranked.layout(407);
        int[][] narrow = ranked.layout(407, TabTest::vanillaWidth, arrow);
        assertTrue(narrow[1][0] >= narrowPlain[1][0] * 3 / 4, "the name keeps three quarters");
        assertEquals(narrowPlain[1][1], narrow[1][1], "render us/f needs more than there is to give");
        List<String> wholeHeaders = new ArrayList<>();
        for (int i = 1; i < ranked.size(); i++) {
            if (vanillaWidth(ranked.get(i).name()) + arrow <= narrow[1][i] + Tab.Columns.PADDING - 1) {
                wholeHeaders.add(ranked.get(i).name());
            } else {
                assertEquals(narrowPlain[1][i], narrow[1][i], ranked.get(i).name() + " truncates as before");
            }
        }
        assertEquals(List.of("share", "gc f", "shadow", "calls"), wholeHeaders);

        // Only a column that sorts gets room for an arrow. At GUI 427 the Hitches tab's total ms, which does not
        // sort, fits its column exactly with the 3 px a header may reach left, so nothing moves.
        Tab.Columns hitches = Tab.HITCHES.columns();
        assertArrayEquals(hitches.layout(407), hitches.layout(407, TabTest::vanillaWidth, arrow));
    }

    @Test
    void layoutFitsTheRowAndNeverOverlaps() {
        for (Tab t : Tab.values()) {
            Tab.Columns c = t.columns();
            assertTrue(c.size() > 0, t + " has no columns");
            assertFalse(c.get(0).rightAlign(), t + "'s first column is the name and is left-aligned");
            for (Tab.Column col : c.all()) assertTrue(col.weight() > 0, t + " has a non-positive weight");
            for (int rowWidth : new int[]{120, 320, 854, 1920}) {
                int[][] layout = c.layout(rowWidth);
                int[] x = layout[0];
                int[] w = layout[1];
                assertEquals(c.size(), x.length, t.toString());
                assertEquals(x.length, w.length, t.toString());
                assertEquals(0, x[0], t + " starts at zero");
                for (int i = 0; i < x.length; i++) {
                    assertTrue(w[i] >= 8, t + " column " + i + " is thinner than 8 px at " + rowWidth);
                    if (i > 0) assertTrue(x[i] > x[i - 1], t + " column " + i + " does not advance at " + rowWidth);
                }
                assertTrue(x[x.length - 1] <= rowWidth, t + " last column starts past the row at " + rowWidth);
            }
        }
    }

    @Test
    void sortableColumnsMatchTheKindOfRowsTheTabHolds() {
        // Frames and Hitches are a narrative, not a table: nothing on them sorts.
        for (Tab t : new Tab[]{Tab.ADVICE, Tab.FRAMES, Tab.HITCHES}) {
            for (Tab.Column col : t.columns().all()) assertFalse(col.sort().sortable(), t + " must not sort");
        }
        // Ranked rows carry ranked sorts only. The Flywheel tab reuses two of them for its class names and
        // average counts (spec addendum §P4.C7).
        for (Tab t : new Tab[]{Tab.BLOCK_ENTITIES, Tab.ENTITIES, Tab.PARTICLES, Tab.FLYWHEEL, Tab.MODS}) {
            assertEquals(ColumnSort.of(Ranking.Sort.LABEL), t.columns().get(0).sort(), t + "'s name column sorts by label");
            for (Tab.Column col : t.columns().all()) assertNull(col.sort().mesh(), t + " must not carry a mesh sort");
        }
        // Mesh rows carry mesh sorts only.
        for (Tab t : new Tab[]{Tab.BLOCKS, Tab.SECTIONS}) {
            assertEquals(ColumnSort.of(Ranking.MeshSort.LABEL), t.columns().get(0).sort(), t + "'s name column sorts by label");
            for (Tab.Column col : t.columns().all()) assertNull(col.sort().ranked(), t + " must not carry a ranked sort");
        }
        for (Tab t : new Tab[]{Tab.BLOCK_ENTITIES, Tab.ENTITIES, Tab.PARTICLES, Tab.FLYWHEEL, Tab.BLOCKS, Tab.SECTIONS, Tab.MODS}) {
            long sortable = t.columns().all().stream().filter(col -> col.sort().sortable()).count();
            assertTrue(sortable >= 2, t + " has only " + sortable + " sortable columns");
        }
        assertEquals(ColumnSort.of(Ranking.Sort.VERTICES), Tab.BLOCK_ENTITIES.columns().get(7).sort(), "verts/f");
        assertEquals(ColumnSort.of(Ranking.MeshSort.PER_CALL), Tab.SECTIONS.columns().get(4).sort(), "avg ms/rb, per rebuild");
        assertEquals("avg ms/rb", Tab.SECTIONS.columns().get(4).name(), "the report calls it avg ms/rebuild");

        // A name reads A to Z on its first click; a number reads largest first.
        assertTrue(ColumnSort.of(Ranking.Sort.LABEL).ascendingFirst());
        assertTrue(ColumnSort.of(Ranking.MeshSort.LABEL).ascendingFirst());
        assertFalse(ColumnSort.of(Ranking.Sort.VERTICES).ascendingFirst());
        assertFalse(ColumnSort.of(Ranking.MeshSort.TIME).ascendingFirst());
    }

    @Test
    void theScreenOpensOnAdviceAndTenTabsFitTwoRows() {
        assertEquals(Tab.ADVICE, Tab.values()[0], "first in the tab row");
        assertEquals(Tab.ADVICE, new ResultsView("minecraft:overworld").tab, "a new result opens on it");
        assertEquals(10, Tab.values().length);
    }

    @Test
    void theRankedTabsShowTheReportsColumnsAndEveryTabOpensInTheReportsOrder() {
        // The names are written out in Tab so each column reads as one line; this is what stops them drifting
        // from the report's.
        assertEquals(RankedTable.RANKED, names(Tab.BLOCK_ENTITIES));
        assertEquals(RankedTable.RANKED, names(Tab.ENTITIES));
        assertEquals(RankedTable.PARTICLE, names(Tab.PARTICLES));
        assertEquals(MeshTable.BLOCK, names(Tab.BLOCKS));
        assertEquals(MeshTable.SECTION_SCREEN, names(Tab.SECTIONS));
        assertEquals(RankedTable.MODS, names(Tab.MODS));
        for (Tab t : new Tab[]{Tab.BLOCK_ENTITIES, Tab.ENTITIES, Tab.PARTICLES, Tab.MODS}) {
            for (int i = 1; i < t.columns().size(); i++) {
                assertTrue(t.columns().get(i).rightAlign(), t + " column " + i + " is a number");
            }
        }

        // Ranking.byType and byKey rank by TOTAL, the Blocks table by mesh time, the Sections table by
        // rebuilds, and the Flywheel table by average count. A tab must open in that order.
        assertEquals(ColumnSort.of(Ranking.Sort.TOTAL), opening(Tab.BLOCK_ENTITIES));
        assertEquals("share", Tab.BLOCK_ENTITIES.columns().get(Tab.BLOCK_ENTITIES.columns().defaultSortColumn()).name());
        assertEquals(ColumnSort.of(Ranking.Sort.TOTAL), opening(Tab.ENTITIES));
        assertEquals(ColumnSort.of(Ranking.Sort.TOTAL), opening(Tab.PARTICLES));
        assertEquals(ColumnSort.of(Ranking.Sort.TOTAL), opening(Tab.FLYWHEEL));
        assertEquals(ColumnSort.of(Ranking.MeshSort.TIME), opening(Tab.BLOCKS));
        assertEquals(ColumnSort.of(Ranking.MeshSort.CALLS), opening(Tab.SECTIONS));
        assertEquals(ColumnSort.of(Ranking.Sort.TOTAL), opening(Tab.MODS));
        assertEquals("share", Tab.MODS.columns().get(Tab.MODS.columns().defaultSortColumn()).name(),
                "share and us/f both sort by total time; the arrow sits on the first, as on the other ranked tabs");
        assertEquals(1, Tab.SECTIONS.columns().defaultSortColumn(), "rebuilds, not rebuilds/s, which sorts the same way");
    }

    @Test
    void everyTabShowsTheseColumnsAndOpensOnTheStarredOne() {
        // name weight alignment sort; * marks the column a tab opens sorted by. Pinned in full because RowList and
        // ResultsScreen cannot be unit-tested: a sort swapped with its neighbour's fails here and nowhere else.
        assertEquals("metric 2 L - | value 3 L -", describe(Tab.FRAMES));
        assertEquals("frame 2 L - | total ms 1 R - | split 7 L -", describe(Tab.HITCHES));
        String ranked = "name 5 L LABEL | render us/f 1 R RENDER | tick us/f 1 R TICK | *share 1 R TOTAL | peak ms 1 R PEAK"
                + " | gc f 1 R GC_FRAMES | shadow 1 R SHADOW | verts/f 1 R VERTICES | calls 1 R CALLS";
        assertEquals(ranked, describe(Tab.BLOCK_ENTITIES));
        assertEquals(ranked, describe(Tab.ENTITIES));
        assertEquals("mod 5 L LABEL | *share 1 R TOTAL | us/f 1 R TOTAL | peak ms 1 R PEAK | gc f 1 R GC_FRAMES"
                + " | calls/f 1 R ALL_CALLS", describe(Tab.MODS));
        assertEquals("class 5 L LABEL | render us/f 1 R RENDER | tick us/f 1 R TICK | *share 1 R TOTAL"
                + " | avg live 1 R CALLS | verts/f 1 R VERTICES | gc f 1 R GC_FRAMES", describe(Tab.PARTICLES));
        assertEquals("visual class 6 L LABEL | *avg count 1 R TOTAL | share 1 R -", describe(Tab.FLYWHEEL));
        assertEquals("block 8 L LABEL | *mesh ms/s 2 R TIME | us/call 2 R PER_CALL | calls 2 R CALLS", describe(Tab.BLOCKS));
        assertEquals("section 6 L LABEL | *rebuilds 2 R CALLS | rebuilds/s 2 R CALLS | mesh ms 2 R TIME"
                + " | avg ms/rb 3 R PER_CALL", describe(Tab.SECTIONS));
    }

    @Test
    void layoutKeepsItsPaddingAfterEveryColumnAndEndsInsideTheRow() {
        for (Tab t : Tab.values()) {
            for (int rowWidth : new int[]{320, 407, 460, 620, 834, 1920}) {   // 407..834: GUI 427, 480, 640, 854
                for (int[][] layout : List.of(t.columns().layout(rowWidth),
                        t.columns().layout(rowWidth, TabTest::vanillaWidth, vanillaWidth(" v")))) {
                    int[] x = layout[0];
                    int[] w = layout[1];
                    for (int i = 1; i < x.length; i++) {
                        assertTrue(x[i] - (x[i - 1] + w[i - 1]) >= Tab.Columns.PADDING,
                                t + " column " + (i - 1) + " leaves too little padding at " + rowWidth);
                    }
                    assertTrue(x[x.length - 1] + w[w.length - 1] <= rowWidth, t + " ends past the row at " + rowWidth);
                }
            }
        }
    }

    @Test
    void aNumberMayReachIntoThePaddingAndIsRoundedBeforeItBecomesHashes() {
        // The vanilla font's advances: '.' and ',' are 2 px; digits, '%' and '#' are 6 px.
        ToIntFunction<String> vanilla = s -> s.chars().map(ch -> ch == '.' || ch == ',' ? 2 : 6).sum();
        assertEquals("74,287", Tab.Columns.fitNumber("74,287", 32, vanilla), "fits its column");
        assertEquals("1501.5", Tab.Columns.fitNumber("1501.5", 31, vanilla), "a ranked column at 1920x1080, Auto scale");
        assertEquals("100.0%", Tab.Columns.fitNumber("100.0%", 29, vanilla), "three px into the padding leave two blank");
        assertEquals("100%", Tab.Columns.fitNumber("100.0%", 28, vanilla), "four would leave one, so it is rounded instead");
        assertEquals("10781", Tab.Columns.fitNumber("10780.9", 31, vanilla), "rounded, never cut short");
        assertEquals("#####", Tab.Columns.fitNumber("125,539", 31, vanilla), "a count has no decimals to round away");
        assertEquals("####", Tab.Columns.fitNumber("12345.6", 24, vanilla), "too wide even when rounded");
    }

    private static List<String> names(Tab t) {
        return t.columns().all().stream().map(Tab.Column::name).toList();
    }

    private static ColumnSort opening(Tab t) {
        return t.columns().get(t.columns().defaultSortColumn()).sort();
    }

    private static String describe(Tab t) {
        Tab.Columns c = t.columns();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < c.size(); i++) {
            Tab.Column col = c.get(i);
            ColumnSort s = col.sort();
            if (i > 0) sb.append(" | ");
            if (s.sortable() && i == c.defaultSortColumn()) sb.append('*');
            sb.append(col.name()).append(' ').append(col.weight()).append(col.rightAlign() ? " R " : " L ")
                    .append(s.ranked() != null ? s.ranked() : s.mesh() != null ? s.mesh() : "-");
        }
        return sb.toString();
    }
}
