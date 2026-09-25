package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.core.Ranking;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SortStateTest {

    @Test
    void aTabOpensOnItsDefaultColumnLargestFirst() {
        SortState s = new SortState();
        assertEquals(3, s.column(Tab.BLOCK_ENTITIES), "share, the report's order");
        assertFalse(s.ascending(Tab.BLOCK_ENTITIES));
        assertEquals(ColumnSort.of(Ranking.Sort.TOTAL), s.sort(Tab.BLOCK_ENTITIES));
    }

    @Test
    void aNumberStartsLargestFirstANameStartsAToZAndTheActiveColumnReverses() {
        SortState s = new SortState();
        assertTrue(s.click(Tab.BLOCK_ENTITIES, 3));
        assertTrue(s.ascending(Tab.BLOCK_ENTITIES), "the column a tab opens on is already active, so it reverses");

        assertTrue(s.click(Tab.BLOCK_ENTITIES, 7));
        assertEquals(ColumnSort.of(Ranking.Sort.VERTICES), s.sort(Tab.BLOCK_ENTITIES));
        assertFalse(s.ascending(Tab.BLOCK_ENTITIES), "a new number column starts largest first");
        assertTrue(s.click(Tab.BLOCK_ENTITIES, 7));
        assertTrue(s.ascending(Tab.BLOCK_ENTITIES), "a second click reverses it");

        assertTrue(s.click(Tab.BLOCK_ENTITIES, 0));
        assertEquals(ColumnSort.of(Ranking.Sort.LABEL), s.sort(Tab.BLOCK_ENTITIES));
        assertTrue(s.ascending(Tab.BLOCK_ENTITIES), "a name starts A to Z");

        assertEquals(3, s.column(Tab.ENTITIES), "each tab keeps its own column");
        assertFalse(s.ascending(Tab.ENTITIES));
    }

    @Test
    void aColumnThatDoesNotSortChangesNothing() {
        SortState s = new SortState();
        assertFalse(s.click(Tab.FRAMES, 0));
        assertFalse(s.click(Tab.HITCHES, 1));
        assertFalse(s.click(Tab.FLYWHEEL, 2), "the Flywheel tab's share column");
        assertEquals(Tab.FLYWHEEL.columns().defaultSortColumn(), s.column(Tab.FLYWHEEL));
        assertFalse(s.ascending(Tab.FLYWHEEL));
    }
}
