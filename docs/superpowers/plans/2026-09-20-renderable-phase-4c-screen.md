# Renderable Phase 4c Implementation Plan: screen and overlay

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the results screen do what spec §5.3 promised and stop it disagreeing with the report: sort by any numeric column by clicking its header, tell "that mod is not installed" from "nothing happened", cap the Sections list, teleport somewhere a player can stand, truncate right-aligned cells, print Flywheel class names the same way in both places, dimension-filter entity labels, and draw a real wireframe box instead of a bounding rectangle.

**Architecture:** The sortable columns become data: a `ColumnSort` value on every `Tab.Columns` entry says which `Ranking.Sort` or `Ranking.MeshSort` that header means, or that it means nothing. `ResultsScreen` keeps a sort column and direction per tab, handles clicks in the header band, and passes the resolved `ColumnSort` to `Rows`, which also receives the `Environment` it has been missing. The empty-state sentences move to one `core` class so the screen and the report cannot drift. The highlight projects the eight corners as before but draws the twelve edges as run-merged two-pixel lines in the same GUI layer, so it still shows through walls and shaders.

**Tech Stack:** Java 21, ModDevGradle 2.0.147, NeoForge 21.1.220, vanilla `Screen` and `ObjectSelectionList`, JUnit 5 for everything outside `ui.screen.Rows` and `ui.WorldOverlay` (both import Minecraft types and cannot be unit-tested).

**Spec:** `docs/superpowers/specs/2026-09-20-renderable-phase-4-design.md` §P4.C (all of it), plus §P4.D1's `TabTest`, which lives here because this plan changes `Tab`. Base spec §5.3, §5.4, §6.1.

**Repository:** `H:\repos\Renderable`, branch `main`. Commits are YubiKey-signed: the orchestrator commits after telling the user to touch the key. **Subagents stage and report; they do not commit.**

**Ordering:** This plan assumes **plan 4a is on `main`** (`v0.4.0`), which is where the `gc f` columns come from. It is independent of 4b and 4d and may run before or after either.

**Shared additions this plan may need** (plan 4b introduces them too; each plan says "skip if present"): `Environment.ABSENT`, `Environment.has(String)` and `Environment.value(String)`. Task 3 adds them if they are not there yet.

**Verified facts you can rely on:**

- `Tab` and `Tab.Columns` are pure Java: the file imports nothing, so a JUnit test can exercise `layout(int)` directly. `Ranking`, `RankedEntry`, `MeshEntry` and `FlywheelStats` are likewise Minecraft-free.
- `Rows` imports `net.minecraft.core.BlockPos` and `WorldOverlay` imports the client renderer, so neither is unit-testable; their proof is the dev client launch in Tasks 4 and 6 and the in-pack checks in Task 8.
- `RowList.Entry.render` truncates left-aligned cells with `font.plainSubstrByWidth(cell, colW[i])` but draws right-aligned cells with a bare `g.drawString(font, cell, ...)`, so a long number overlaps its neighbour. Confirmed by reading the method on `1ec32c3`.
- `ResultsScreen.teleportToSelected` sends `tp @s x (y+1) z` from `selected.pos()`, and `Rows.sections` sets that position to `new BlockPos(k.centerX(), k.centerY(), k.centerZ())` — the section's centre block, which is solid for any section below the surface.
- `WorldOverlay.setResult` builds entity labels with `dimension = null`, and `drawLabels` treats null as "draw in any dimension". The README claims labels are dimension-scoped.
- `WorldOverlay.drawHighlight` returns early when **any** of the eight corners has `w <= 0.001f`, so standing inside a highlighted section draws nothing at all.
- `FlywheelStats` is `record FlywheelStats(int samples, double avgBlockEntities, double avgEntities, double avgEffects, Map<String, Double> avgByClass)` with `total()` and `byAverageDesc()`. `Rows.flywheel` shortens class names with `k.substring(k.lastIndexOf('.') + 1)`; `TextReportWriter.appendFlywheel` prints `e.getKey()` in full.
- The phase 3 acceptance log recorded 1052 distinct sections rebuilt in one 30 s flight, which is what the Sections tab renders one row each for.
- The suite is **86 tests** after plan 4a.

**Deviations from the spec addendum recorded in this plan:** the Flywheel tab's two sortable columns reuse `Ranking.Sort.LABEL` (class name) and `Ranking.Sort.TOTAL` (average count) rather than getting an enum of their own, because those rows are `Map.Entry<String, Double>` and two orderings do not justify a third sort type. `Rows.flywheel` documents the interpretation and `TabTest` pins it.

---

## File map

```
README.md                                                     Modify (Task 7)
docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md
                                                              Modify: §5.3, §5.4 (Task 7)
src/main/java/io/github/edwardg117/renderable/
  core/Ranking.java                                           Modify: Sort.SHADOW/GC_FRAMES/LABEL, ascending, MeshSort
  core/EmptyStates.java                                       Create
  core/Environment.java                                       Modify: ABSENT/has/value if 4b has not run
  core/FlywheelStats.java                                     Modify: displayNames()
  report/TextReportWriter.java                                Modify: EmptyStates, Flywheel display names
  ui/screen/ColumnSort.java                                   Create
  ui/screen/Tab.java                                          Modify: sorts per column, Sections header
  ui/screen/Rows.java                                         Modify: Environment, sorting, section cap, Flywheel names
  ui/screen/RowList.java                                      Modify: truncate right-aligned cells
  ui/screen/ResultsScreen.java                                Modify: header clicks, arrows, teleport Y, no Sort button
  ui/WorldOverlay.java                                        Modify: entity dimension, wireframe highlight
  ProfilerController.java                                     Modify: remember the session dimension
src/test/java/io/github/edwardg117/renderable/
  core/RankingTest.java                                       Modify: new sorts
  core/EmptyStatesTest.java                                   Create
  core/FlywheelStatsTest.java                                 Modify: display names
  report/TextReportWriterTest.java                            Modify: Flywheel simple names
  ui/screen/TabTest.java                                      Create
docs/acceptance/phase-4c.md                                   Create (Task 8)
```

**Expected test totals**, assuming 4a is on `main` and neither 4b nor 4d has run: 86 at the start, 88 after
Task 1, 91 after Task 2, 93 after Task 3, 93 after Task 4, 95 after Task 5, 95 at the end.

---

### Task 1: Sort by any numeric column, in either direction

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Ranking.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/RankingTest.java` (append)

Spec addendum §P4.C7. `Ranking.Sort` covers six of the nine ranked columns; `MeshSort` covers two of the
nine mesh columns; neither can sort ascending. The two-argument `sort` and three-argument `mesh` keep their
current meaning (descending) so no existing caller changes.

- [ ] **Step 1: Write the failing test**

Append inside `RankingTest`:

```java
    @Test
    void everyNumericColumnHasASortAndBothDirectionsWork() {
        SessionResult p3 = TestSessions.phase3();
        List<RankedEntry> be = Ranking.byKey(p3, ProfileKey.Category.BLOCK_ENTITY);

        // Descending is what the two-argument form has always meant.
        assertEquals(PRESS, Ranking.sort(be, Ranking.Sort.VERTICES).get(0).key());
        assertEquals(PRESS, Ranking.sort(be, Ranking.Sort.VERTICES, false).get(0).key());
        // Ascending puts the smallest first; MIXER has 400 vertices, PRESS2 none.
        assertEquals(PRESS2, Ranking.sort(be, Ranking.Sort.VERTICES, true).get(0).key());
        assertEquals(PRESS, Ranking.sort(be, Ranking.Sort.VERTICES, true).get(2).key());

        // LABEL sorts alphabetically ascending and reverse-alphabetically descending.
        assertEquals("create:mechanical_mixer @ 90 65 -22", Ranking.sort(be, Ranking.Sort.LABEL, true).get(0).label());
        assertEquals("create:mechanical_press @ 103 64 -20", Ranking.sort(be, Ranking.Sort.LABEL, false).get(0).label());

        // SHADOW and GC_FRAMES exist and are stable when every row is zero: the label breaks the tie.
        assertEquals(List.of("create:mechanical_mixer @ 90 65 -22",
                        "create:mechanical_press @ 100 64 -20",
                        "create:mechanical_press @ 103 64 -20"),
                Ranking.sort(be, Ranking.Sort.SHADOW, false).stream().map(RankedEntry::label).toList());
        assertEquals(3, Ranking.sort(be, Ranking.Sort.GC_FRAMES, false).size());
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

        // section (0,4,0) 3 rebuilds / 9 ms = 3.00 ms each; (1,4,0) 1 rebuild / 2 ms = 2.00 ms each
        assertEquals(SECTION_A,
                Ranking.mesh(p3, ProfileKey.Category.SECTION, Ranking.MeshSort.AVG, false).get(0).key());
        assertEquals(SECTION_B,
                Ranking.mesh(p3, ProfileKey.Category.SECTION, Ranking.MeshSort.AVG, true).get(0).key());
        assertEquals(SECTION_A,
                Ranking.mesh(p3, ProfileKey.Category.SECTION, Ranking.MeshSort.CALLS).get(0).key(),
                "the three-argument form still means descending");
    }
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.RankingTest' --no-daemon
```

Expected: compilation failure — `cannot find symbol: variable LABEL`, `SHADOW`, `GC_FRAMES`, `PER_CALL`,
`AVG`, and no three-argument `sort` or four-argument `mesh`.

- [ ] **Step 3: Extend `Ranking`**

Replace the two enums and the two sorting methods. The enums first:

```java
    /**
     * A sortable column of a ranked table (spec §5.3). {@code next()} is kept because the config-free
     * default ordering still starts from {@link #TOTAL}; the screen picks a column directly.
     */
    public enum Sort {
        TOTAL, RENDER, TICK, PEAK, VERTICES, CALLS, SHADOW, GC_FRAMES, LABEL;

        public Sort next() {
            Sort[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    /** A sortable column of the Blocks or Sections table. */
    public enum MeshSort { TIME, CALLS, PER_CALL, AVG, LABEL }
```

`Sort.LABEL` is new and `next()` now cycles through nine values. The only surviving caller of `next()` is
`RankingTest.sortModesReorder`, which asserts `Sort.TOTAL.next() == Sort.RENDER` and
`Sort.CALLS.next() == Sort.TOTAL` — the second is no longer true once `SHADOW` follows `CALLS`. Change that
assertion to:

```java
        assertEquals(Ranking.Sort.RENDER, Ranking.Sort.TOTAL.next());
        assertEquals(Ranking.Sort.TOTAL, Ranking.Sort.LABEL.next());
```

Then the ranked sort:

```java
    /** Returns a new list sorted by the chosen column, descending, ties broken by label. */
    public static List<RankedEntry> sort(List<RankedEntry> entries, Sort sort) {
        return sort(entries, sort, false);
    }

    /**
     * Returns a new list sorted by the chosen column. Ties are always broken by label ascending, so the
     * order is stable whichever direction the column itself is read in.
     */
    public static List<RankedEntry> sort(List<RankedEntry> entries, Sort sort, boolean ascending) {
        List<RankedEntry> copy = new ArrayList<>(entries);
        if (sort == Sort.LABEL) {
            Comparator<RankedEntry> byLabel = Comparator.comparing(RankedEntry::label);
            copy.sort(ascending ? byLabel : byLabel.reversed());
            return copy;
        }
        Comparator<RankedEntry> by = switch (sort) {
            case TOTAL -> Comparator.comparingLong(RankedEntry::totalNanos);
            case RENDER -> Comparator.comparingLong(RankedEntry::renderNanos);
            case TICK -> Comparator.comparingLong(RankedEntry::tickNanos);
            case PEAK -> Comparator.comparingLong(RankedEntry::maxFrameNanos);
            case VERTICES -> Comparator.comparingLong(RankedEntry::vertices);
            case CALLS -> Comparator.comparingLong(RankedEntry::calls);
            case SHADOW -> Comparator.comparingLong(RankedEntry::shadowNanos);
            case GC_FRAMES -> Comparator.comparingLong(RankedEntry::gcFrames);
            case LABEL -> throw new IllegalStateException("handled above");
        };
        copy.sort((ascending ? by : by.reversed()).thenComparing(RankedEntry::label));
        return copy;
    }
```

And the mesh sort:

```java
    /** Mesh rows of one category, most expensive first; the default order the report and JSON use. */
    public static List<MeshEntry> mesh(SessionResult r, ProfileKey.Category category, MeshSort sort) {
        return mesh(r, category, sort, false);
    }

    /** Mesh rows of one category, sorted by any column the Blocks or Sections table shows. */
    public static List<MeshEntry> mesh(SessionResult r, ProfileKey.Category category, MeshSort sort, boolean ascending) {
        List<MeshEntry> out = new ArrayList<>();
        for (MeshEntry e : r.mesh()) {
            if (e.key().category() == category) out.add(e);
        }
        if (sort == MeshSort.LABEL) {
            Comparator<MeshEntry> byLabel = Comparator.comparing(e -> e.key().label());
            out.sort(ascending ? byLabel : byLabel.reversed());
            return out;
        }
        Comparator<MeshEntry> by = switch (sort) {
            case TIME -> Comparator.comparingLong(MeshEntry::nanos);
            case CALLS -> Comparator.comparingLong(MeshEntry::calls);
            case PER_CALL -> Comparator.comparingDouble(MeshEntry::usPerCall);
            case AVG -> Comparator.comparingDouble(e -> e.calls() == 0 ? 0 : (double) e.nanos() / e.calls());
            case LABEL -> throw new IllegalStateException("handled above");
        };
        out.sort((ascending ? by : by.reversed()).thenComparing(e -> e.key().label()));
        return out;
    }
```

`mesh` no longer returns an immutable copy — it did not before either; it returned the mutable `out`. That
is unchanged.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`, **88 tests**.

- [ ] **Step 5: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/Ranking.java src/test/java/io/github/edwardg117/renderable/core/RankingTest.java
git status --short
```

Commit message for the orchestrator:

```
feat(core): sort ranked and mesh rows by any column, in either direction
```

---

### Task 1a: Reshape `Columns`, and stop writing the ranked cells twice

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Tab.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/Columns.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java`

Added after phase 4a, from the code-quality review of its Task 7. **Do this before Task 2**, because Task 2
adds a fourth parallel array to `Tab.Columns` and Task 4 rewrites the same cell-building lines — doing it
after means doing it twice.

Two problems, one move:

1. `Tab.Columns` is three hand-aligned parallel arrays across seven constants. Phase 4a had to extend three
   of them in lockstep twice, and a mismatch is silent: `RowList.render` iterates
   `i < row.cells().length && i < colX.length`, so a short row truncates rather than throwing. Task 2 makes
   it four arrays. Replace them with a `record Column(String name, int weight, boolean rightAlign,
   ColumnSort sort)` and make `Columns` a list of those, so miscounting becomes impossible rather than
   merely checked. `layout(int)` reads more simply from a list, and the compact-constructor invariant phase
   4a added stops being necessary.
2. The ranked and particle **column names and cell expressions are written twice**, term for term, in
   `TextReportWriter` and in `Rows`. Nothing enforces that they agree; today they do only because a reviewer
   read both files side by side. Move the shared part — the column names and a
   `String[] cells(RankedEntry, int frames, long total)` — into `core`. That is not a new dependency
   direction: `ui` and `report` both already depend on `core`, and `core` already carries presentation code
   by precedent, since `Fmt` is entirely formatting and `Splits.frame`/`average` produce report strings.

What stays where it is: pixel weights, alignment, `TextReportWriter`'s `TOP` truncation and its mesh note,
and this plan's per-column sorting. Those genuinely differ between the two surfaces.

Once the cell builders live in `core` they are testable, which is the point: `TabTest` in Task 2 can then
assert that each tab's column count equals the length of the array its builder returns for a real
`RankedEntry`, turning the thing phase 4a had to verify by reading into a test.

Also add, while these files are open: the results screen's Frames tab has no equivalent of the report's
note explaining what `gc f` means, which phase 4a added to the text report only. Put the same sentence
beside the Frames tab's `GC` row, so the two surfaces are as consistent in their explanations as they now
are in their numbers.

---

### Task 2: Columns declare what they sort by

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/ui/screen/ColumnSort.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Tab.java`
- Create: `src/test/java/io/github/edwardg117/renderable/ui/screen/TabTest.java`

Spec addendum §P4.C7 and §P4.C6 #1, plus §P4.D1's `TabTest`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/io/github/edwardg117/renderable/ui/screen/TabTest.java`:

```java
package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.core.Ranking;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TabTest {

    @Test
    void everyTabsColumnArraysAreTheSameLength() {
        for (Tab t : Tab.values()) {
            Tab.Columns c = t.columns();
            int n = c.headers().length;
            assertTrue(n > 0, t + " has no columns");
            assertEquals(n, c.weights().length, t + " weights");
            assertEquals(n, c.rightAlign().length, t + " rightAlign");
            assertEquals(n, c.sorts().length, t + " sorts");
            for (int w : c.weights()) assertTrue(w > 0, t + " has a non-positive weight");
            assertFalse(c.rightAlign()[0], t + "'s first column is the name and is left-aligned");
        }
    }

    @Test
    void layoutFitsTheRowAndNeverOverlaps() {
        for (Tab t : Tab.values()) {
            for (int rowWidth : new int[]{120, 320, 854, 1920}) {
                int[][] layout = t.columns().layout(rowWidth);
                int[] x = layout[0];
                int[] w = layout[1];
                assertEquals(t.columns().headers().length, x.length, t.toString());
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
        for (Tab t : new Tab[]{Tab.FRAMES, Tab.HITCHES}) {
            for (ColumnSort s : t.columns().sorts()) assertFalse(s.sortable(), t + " must not sort");
        }
        // The six data tabs each have at least two sortable columns, and their first column sorts by label.
        for (Tab t : new Tab[]{Tab.BLOCK_ENTITIES, Tab.ENTITIES, Tab.PARTICLES, Tab.FLYWHEEL, Tab.BLOCKS, Tab.SECTIONS}) {
            long sortable = java.util.Arrays.stream(t.columns().sorts()).filter(ColumnSort::sortable).count();
            assertTrue(sortable >= 2, t + " has only " + sortable + " sortable columns");
            assertEquals(Ranking.Sort.LABEL, t.columns().sorts()[0].ranked(), t + "'s name column sorts by label");
        }
        // Ranked tabs carry ranked sorts; mesh tabs carry mesh sorts. Nothing carries both.
        for (Tab t : new Tab[]{Tab.BLOCK_ENTITIES, Tab.ENTITIES, Tab.PARTICLES, Tab.FLYWHEEL}) {
            for (ColumnSort s : t.columns().sorts()) assertNull(s.mesh(), t + " must not carry a mesh sort");
        }
        for (Tab t : new Tab[]{Tab.BLOCKS, Tab.SECTIONS}) {
            for (ColumnSort s : t.columns().sorts()) {
                if (s.sortable() && s.ranked() != null) {
                    assertEquals(Ranking.Sort.LABEL, s.ranked(), t + " may only use LABEL from the ranked sorts");
                }
            }
        }
        assertEquals(Ranking.Sort.VERTICES, Tab.BLOCK_ENTITIES.columns().sorts()[7].ranked(), "verts/f");
        assertEquals(Ranking.MeshSort.AVG, Tab.SECTIONS.columns().sorts()[4].mesh(), "avg ms/rb");
        assertEquals("avg ms/rb", Tab.SECTIONS.columns().headers()[4], "the report calls it avg ms/rebuild");
        assertEquals(1, Tab.BLOCK_ENTITIES.columns().defaultSortColumn(), "render us/f is the default order");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.ui.screen.TabTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: class ColumnSort`, `method sorts()`,
`method defaultSortColumn()`.

- [ ] **Step 3: Write `ColumnSort`**

`src/main/java/io/github/edwardg117/renderable/ui/screen/ColumnSort.java`:

```java
package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.core.Ranking;

/**
 * What clicking a column header sorts by (spec §5.3). A tab's rows are either ranked entries or mesh
 * entries, so at most one of the two is set; {@link #NONE} marks a column that is not sortable, which is
 * every column of the Frames and Hitches tabs.
 *
 * <p>The Flywheel tab's rows are neither: they are class names and average counts. It reuses
 * {@link Ranking.Sort#LABEL} for the class column and {@link Ranking.Sort#TOTAL} for the count, which
 * {@code Rows.flywheel} interprets directly.
 */
public record ColumnSort(Ranking.Sort ranked, Ranking.MeshSort mesh) {

    /** A column that cannot be sorted by. */
    public static final ColumnSort NONE = new ColumnSort(null, null);

    public static ColumnSort of(Ranking.Sort sort) {
        return new ColumnSort(sort, null);
    }

    public static ColumnSort of(Ranking.MeshSort sort) {
        return new ColumnSort(null, sort);
    }

    public boolean sortable() {
        return ranked != null || mesh != null;
    }
}
```

- [ ] **Step 4: Give every column its sort**

Replace `Tab.java` entirely:

```java
package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.core.Ranking;

/** Tabs of the results screen and the column layout each one uses. Eight tabs, four per row on the screen. */
public enum Tab {
    FRAMES("Frames", Columns.METRIC_VALUE),
    HITCHES("Hitches", Columns.HITCH),
    BLOCK_ENTITIES("Block Entities", Columns.RANKED),
    ENTITIES("Entities", Columns.RANKED),
    PARTICLES("Particles", Columns.PARTICLE),
    FLYWHEEL("Flywheel", Columns.FLYWHEEL),
    BLOCKS("Blocks", Columns.BLOCK),
    SECTIONS("Sections", Columns.SECTION);

    private final String title;
    private final Columns columns;

    Tab(String title, Columns columns) {
        this.title = title;
        this.columns = columns;
    }

    public String title() {
        return title;
    }

    public Columns columns() {
        return columns;
    }

    /** True when a click on a column header reorders this tab's rows (spec §5.3). */
    public boolean sortable() {
        for (ColumnSort s : columns.sorts()) {
            if (s.sortable()) return true;
        }
        return false;
    }

    /**
     * Column headers, relative weights, alignment, and what each column sorts by. {@link #layout(int)}
     * turns weights into pixel offsets. Kept as a nested class so enum constants can reference the
     * constants without a forward-reference error.
     */
    public record Columns(String[] headers, int[] weights, boolean[] rightAlign, ColumnSort[] sorts) {
        private static final ColumnSort N = ColumnSort.NONE;

        static final Columns METRIC_VALUE = new Columns(
                new String[]{"metric", "value"},
                new int[]{2, 3},
                new boolean[]{false, false},
                new ColumnSort[]{N, N});
        static final Columns HITCH = new Columns(
                new String[]{"frame", "total ms", "split"},
                new int[]{2, 1, 7},
                new boolean[]{false, true, false},
                new ColumnSort[]{N, N, N});
        static final Columns RANKED = new Columns(
                new String[]{"name", "render us/f", "tick us/f", "share", "peak ms", "gc f", "shadow", "verts/f", "calls"},
                new int[]{5, 1, 1, 1, 1, 1, 1, 1, 1},
                new boolean[]{false, true, true, true, true, true, true, true, true},
                new ColumnSort[]{
                        ColumnSort.of(Ranking.Sort.LABEL),
                        ColumnSort.of(Ranking.Sort.RENDER),
                        ColumnSort.of(Ranking.Sort.TICK),
                        ColumnSort.of(Ranking.Sort.TOTAL),
                        ColumnSort.of(Ranking.Sort.PEAK),
                        ColumnSort.of(Ranking.Sort.GC_FRAMES),
                        ColumnSort.of(Ranking.Sort.SHADOW),
                        ColumnSort.of(Ranking.Sort.VERTICES),
                        ColumnSort.of(Ranking.Sort.CALLS)});
        static final Columns PARTICLE = new Columns(
                new String[]{"class", "render us/f", "tick us/f", "share", "avg live", "verts/f", "gc f"},
                new int[]{5, 1, 1, 1, 1, 1, 1},
                new boolean[]{false, true, true, true, true, true, true},
                new ColumnSort[]{
                        ColumnSort.of(Ranking.Sort.LABEL),
                        ColumnSort.of(Ranking.Sort.RENDER),
                        ColumnSort.of(Ranking.Sort.TICK),
                        ColumnSort.of(Ranking.Sort.TOTAL),
                        ColumnSort.of(Ranking.Sort.CALLS),
                        ColumnSort.of(Ranking.Sort.VERTICES),
                        ColumnSort.of(Ranking.Sort.GC_FRAMES)});
        static final Columns FLYWHEEL = new Columns(
                new String[]{"visual class", "avg count", "share"},
                new int[]{6, 1, 1},
                new boolean[]{false, true, true},
                new ColumnSort[]{
                        ColumnSort.of(Ranking.Sort.LABEL),
                        ColumnSort.of(Ranking.Sort.TOTAL),
                        N});
        static final Columns BLOCK = new Columns(
                new String[]{"block", "mesh ms/s", "us/call", "calls"},
                new int[]{8, 2, 2, 2},
                new boolean[]{false, true, true, true},
                new ColumnSort[]{
                        ColumnSort.of(Ranking.Sort.LABEL),
                        ColumnSort.of(Ranking.MeshSort.TIME),
                        ColumnSort.of(Ranking.MeshSort.PER_CALL),
                        ColumnSort.of(Ranking.MeshSort.CALLS)});
        static final Columns SECTION = new Columns(
                new String[]{"section", "rebuilds", "rebuilds/s", "mesh ms", "avg ms/rb"},
                new int[]{6, 2, 2, 2, 3},
                new boolean[]{false, true, true, true, true},
                new ColumnSort[]{
                        ColumnSort.of(Ranking.Sort.LABEL),
                        ColumnSort.of(Ranking.MeshSort.CALLS),
                        ColumnSort.of(Ranking.MeshSort.CALLS),
                        ColumnSort.of(Ranking.MeshSort.TIME),
                        ColumnSort.of(Ranking.MeshSort.AVG)});

        /** The column a tab opens sorted by: the first sortable one after the name. */
        public int defaultSortColumn() {
            for (int i = 1; i < sorts.length; i++) {
                if (sorts[i].sortable()) return i;
            }
            return 0;
        }

        /** Returns {x offsets, widths} for a row of the given pixel width. Each column keeps 4 px of padding. */
        public int[][] layout(int rowWidth) {
            int total = 0;
            for (int w : weights) total += w;
            int[] x = new int[headers.length];
            int[] w = new int[headers.length];
            int cursor = 0;
            for (int i = 0; i < headers.length; i++) {
                int px = rowWidth * weights[i] / total;
                x[i] = cursor;
                w[i] = Math.max(8, px - 4);
                cursor += px;
            }
            return new int[][]{x, w};
        }
    }
}
```

`Blocks` maps `LABEL` onto a mesh table deliberately: `Rows.blocks` turns a `ranked() == LABEL` into
`MeshSort.LABEL`, which is what `TabTest` asserts is the only ranked sort those tabs may carry.

- [ ] **Step 5: Run the tests to verify they pass**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.ui.screen.TabTest' --no-daemon
```

Expected: `TabTest` green. The full suite does **not** compile yet, because `ResultsScreen` and `Rows` still
call `Tab.sortable()` on the old contract and `Rows.build` has the old signature — Tasks 3 and 4 fix that.
Run the scoped command above, not the whole suite.

If `layoutFitsTheRowAndNeverOverlaps` fails at `rowWidth = 120` because a column does not advance, the
weights are too uneven for a narrow screen: that is a real finding, not a test to relax. Report it.

- [ ] **Step 6: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/ui/screen src/test/java/io/github/edwardg117/renderable/ui
git status --short
```

Commit message for the orchestrator:

```
feat(ui): columns declare what they sort by; test the tab layout invariants
```

---

### Task 3: One place for the empty-state sentences, and `Rows` gets the environment

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/EmptyStates.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Environment.java` (only if plan 4b has not run)
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java`
- Create: `src/test/java/io/github/edwardg117/renderable/core/EmptyStatesTest.java`

Spec addendum §P4.C1 and §P4.C2. `TextReportWriter` can say `(no data: Flywheel is not installed)` and
`(no data: no Flywheel visualization ran; backend flywheel:off)`; `Rows` has no `Environment` and says
neither.

- [ ] **Step 1: Add the `Environment` helpers if they are missing**

Open `src/main/java/io/github/edwardg117/renderable/core/Environment.java`. **If it already has `ABSENT`,
`has` and `value` (plan 4b added them), skip this step.** Otherwise replace the file with:

```java
package io.github.edwardg117.renderable.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain environment description for reports (spec §6.1 item 2). Built by the Minecraft-aware collector in
 * {@code report}; the core only carries it. Entries keep insertion order, which is the order the text
 * report prints them in.
 */
public record Environment(Map<String, String> entries, List<String> hooksApplied) {

    /** The value the collector writes for a mod or feature that is not installed. */
    public static final String ABSENT = "absent";

    /**
     * Copies both collections so the record is really immutable. The entries copy is a
     * {@link LinkedHashMap}, not {@code Map.copyOf}, because {@code Map.copyOf} does not keep order.
     */
    public static Environment of(Map<String, String> entries, List<String> hooksApplied) {
        return new Environment(Collections.unmodifiableMap(new LinkedHashMap<>(entries)), List.copyOf(hooksApplied));
    }

    public static Environment empty() {
        return new Environment(Map.of(), List.of());
    }

    /** True when {@code key} was collected and is not {@link #ABSENT}. */
    public boolean has(String key) {
        String v = entries.get(key);
        return v != null && !ABSENT.equals(v);
    }

    /** The collected value for {@code key}, or {@link #ABSENT} when it was not collected. */
    public String value(String key) {
        return entries.getOrDefault(key, ABSENT);
    }
}
```

- [ ] **Step 2: Write the failing test**

Create `src/test/java/io/github/edwardg117/renderable/core/EmptyStatesTest.java`:

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmptyStatesTest {

    static Environment env(Map<String, String> entries, String... hooks) {
        return Environment.of(new LinkedHashMap<>(entries), List.of(hooks));
    }

    @Test
    void meshAndParticleStatesTellAbsentFromEmpty() {
        Environment none = env(Map.of());
        assertEquals("(no data: Sodium hooks were not active)", EmptyStates.blocks(none));
        assertEquals("(no data: Sodium hooks were not active)", EmptyStates.sections(none));
        assertEquals("(no data: hook not active in this build)", EmptyStates.particles(none));

        Environment hooked = env(Map.of("sodium", "0.8.13"), "SODIUM_MESH", "PARTICLE_RENDER");
        assertEquals("(no data: no blocks were meshed)", EmptyStates.blocks(hooked));
        assertEquals("(no data: no sections were rebuilt)", EmptyStates.sections(hooked));
        assertEquals("(no particles were rendered)", EmptyStates.particles(hooked));

        assertEquals("(no data: hook not active in this build)", EmptyStates.ranked());
    }

    @Test
    void flywheelStateNamesTheBackendItFoundOrSaysTheModIsAbsent() {
        assertEquals("(no data: Flywheel is not installed)", EmptyStates.flywheel(env(Map.of()), 0));
        assertEquals("(no data: Flywheel is not installed)",
                EmptyStates.flywheel(env(Map.of("flywheelBackend", Environment.ABSENT)), 3));
        assertEquals("(no data: no Flywheel visualization ran; backend flywheel:off)",
                EmptyStates.flywheel(env(Map.of("flywheelBackend", "flywheel:off")), 0));
        assertEquals("", EmptyStates.flywheel(env(Map.of("flywheelBackend", "flywheel:indirect")), 3),
                "an empty string means there is data to print");
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.EmptyStatesTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: class EmptyStates`.

- [ ] **Step 4: Write `EmptyStates`**

`src/main/java/io/github/edwardg117/renderable/core/EmptyStates.java`:

```java
package io.github.edwardg117.renderable.core;

/**
 * The one place the "there is nothing here" sentences live, so the results screen and the text report
 * cannot drift apart on the difference between "that mod is not installed" and "nothing happened"
 * (spec addendum §P4.C1). Callers add their own indentation and line ending.
 */
public final class EmptyStates {

    private EmptyStates() {
    }

    /** A ranked category with no rows: in practice the hook for it is not in this build. */
    public static String ranked() {
        return "(no data: hook not active in this build)";
    }

    public static String particles(Environment env) {
        return env.hooksApplied().contains("PARTICLE_RENDER")
                ? "(no particles were rendered)"
                : ranked();
    }

    public static String blocks(Environment env) {
        return mesh(env, "blocks were meshed");
    }

    public static String sections(Environment env) {
        return mesh(env, "sections were rebuilt");
    }

    private static String mesh(Environment env, String nothing) {
        return env.hooksApplied().contains("SODIUM_MESH")
                ? "(no data: no " + nothing + ")"
                : "(no data: Sodium hooks were not active)";
    }

    /**
     * The Flywheel section's empty state, or an empty string when there is data to print. {@code samples}
     * is {@code FlywheelStats.samples()}.
     */
    public static String flywheel(Environment env, int samples) {
        String backend = env.value("flywheelBackend");
        if (Environment.ABSENT.equals(backend)) return "(no data: Flywheel is not installed)";
        if (samples == 0) return "(no data: no Flywheel visualization ran; backend " + backend + ")";
        return "";
    }
}
```

- [ ] **Step 5: Use it in `TextReportWriter`**

The strings are unchanged, so no report test changes. In `appendRanked`:

```java
        if (entries.isEmpty()) {
            sb.append("  ").append(EmptyStates.ranked()).append("\n\n");
            return;
        }
```

In `appendParticles`:

```java
        if (entries.isEmpty()) {
            sb.append("  ").append(EmptyStates.particles(env)).append("\n\n");
            return;
        }
```

In `appendBlocks`:

```java
        if (rows.isEmpty()) {
            sb.append("  ").append(EmptyStates.blocks(env)).append("\n\n");
            return;
        }
```

In `appendSections`:

```java
        if (rows.isEmpty()) {
            sb.append("  ").append(EmptyStates.sections(env)).append("\n\n");
            return;
        }
```

In `appendFlywheel`, replace the first block:

```java
        sb.append("Flywheel\n");
        FlywheelStats fw = r.flywheel();
        String empty = EmptyStates.flywheel(env, fw.samples());
        if (!empty.isEmpty()) {
            sb.append("  ").append(empty).append("\n\n");
            return;
        }
        String backend = env.value("flywheelBackend");
```

and delete the now-unused `meshNoData` private method and the local `FlywheelStats fw = r.flywheel();`
further down. Add `import io.github.edwardg117.renderable.core.EmptyStates;`.

The old code appended `'\n'` after the no-data line via `sb.append(meshNoData(...)).append('\n')`, where
`meshNoData` already ended in `\n`; the replacements above append `"\n\n"` for the same result.

- [ ] **Step 6: Thread the environment and the sort through `Rows`, and cap the Sections tab**

Replace `Rows.build` and the five list builders it dispatches to. `Rows` keeps everything else.

```java
    /** Rows for one tab. {@code sort} is the clicked column; {@code ascending} its direction. */
    public static List<Row> build(Tab tab, SessionResult r, Environment env, ColumnSort sort, boolean ascending,
                                  Set<String> expanded) {
        return switch (tab) {
            case FRAMES -> frames(r);
            case HITCHES -> hitches(r);
            case BLOCK_ENTITIES -> ranked(r, ProfileKey.Category.BLOCK_ENTITY, sort, ascending, expanded);
            case ENTITIES -> ranked(r, ProfileKey.Category.ENTITY, sort, ascending, expanded);
            case PARTICLES -> particles(r, env, sort, ascending);
            case FLYWHEEL -> flywheel(r, env, sort, ascending);
            case BLOCKS -> blocks(r, env, sort, ascending);
            case SECTIONS -> sections(r, env, sort, ascending);
        };
    }

    /** The ranked sort a clicked column means, falling back to the default order. */
    private static Ranking.Sort rankedSort(ColumnSort sort) {
        return sort == null || sort.ranked() == null ? Ranking.Sort.TOTAL : sort.ranked();
    }

    /** The mesh sort a clicked column means. A LABEL click on a mesh tab sorts the mesh rows by label. */
    private static Ranking.MeshSort meshSort(ColumnSort sort, Ranking.MeshSort fallback) {
        if (sort == null) return fallback;
        if (sort.mesh() != null) return sort.mesh();
        return sort.ranked() == Ranking.Sort.LABEL ? Ranking.MeshSort.LABEL : fallback;
    }
```

`ranked`:

```java
    static List<Row> ranked(SessionResult r, ProfileKey.Category category, ColumnSort sort, boolean ascending,
                            Set<String> expanded) {
        int frames = r.frameCount();
        long total = r.stats().totalNanos();
        Ranking.Sort by = rankedSort(sort);
        List<Row> rows = new ArrayList<>();
        if (category == ProfileKey.Category.BLOCK_ENTITY
                && (r.bucketTotalNanos(Bucket.FLYWHEEL) > 0 || r.flywheel().total() > 0)) {
            rows.add(Row.heading("Flywheel-instanced machines are not timed here; see the Flywheel tab"));
        }
        for (RankedEntry t : Ranking.sort(Ranking.byType(r, category), by, ascending)) {
            rows.add(new Row(cells(t.typeId() + "  (" + t.members() + ")", t, frames, total),
                    null, t.typeId(), true, true, 0, null, -1));
            if (expanded.contains(t.typeId())) {
                for (RankedEntry m : Ranking.sort(Ranking.membersOf(r, category, t.typeId()), by, ascending)) {
                    rows.add(new Row(cells(memberLabel(m.key()), m, frames, total),
                            m.key(), t.typeId(), false, false, 1, posOf(m.key()), entityIdOf(m.key())));
                }
            }
        }
        if (rows.isEmpty()) rows.add(Row.plain(EmptyStates.ranked()));
        return rows;
    }
```

`particles`:

```java
    /** Spec §5.3 Particles tab: one row per particle class. */
    static List<Row> particles(SessionResult r, Environment env, ColumnSort sort, boolean ascending) {
        int frames = r.frameCount();
        long total = r.stats().totalNanos();
        List<Row> rows = new ArrayList<>();
        for (RankedEntry e : Ranking.sort(Ranking.byKey(r, ProfileKey.Category.PARTICLE), rankedSort(sort), ascending)) {
            rows.add(new Row(new String[]{
                    e.label(),
                    Fmt.us(e.renderUsPerFrame(frames)),
                    Fmt.us(e.tickUsPerFrame(frames)),
                    Fmt.pct(e.sharePercent(total)),
                    Fmt.num(e.callsPerFrame(frames)),
                    Fmt.num(e.verticesPerFrame(frames)),
                    Fmt.count(e.gcFrames())},
                    e.key(), e.typeId(), false, false, 0, null, -1));
        }
        if (rows.isEmpty()) rows.add(Row.plain(EmptyStates.particles(env)));
        return rows;
    }
```

`flywheel`:

```java
    /**
     * Spec §5.3 Flywheel tab: the bucket, then the averaged visual counts by class. The rows are class names
     * and averages rather than ranked entries, so the two sortable columns are read directly: LABEL sorts by
     * class name, anything else by average count (spec addendum §P4.C7).
     */
    static List<Row> flywheel(SessionResult r, Environment env, ColumnSort sort, boolean ascending) {
        int frames = r.frameCount();
        long total = r.stats().totalNanos();
        long bucket = r.bucketTotalNanos(Bucket.FLYWHEEL);
        FlywheelStats fw = r.flywheel();
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(new String[]{"Flywheel bucket",
                Fmt.us(frames == 0 ? 0 : bucket / 1000.0 / frames) + " us/f",
                Fmt.pct(total == 0 ? 0 : 100.0 * bucket / total)},
                null, null, true, false, 0, null, -1));
        String empty = EmptyStates.flywheel(env, fw.samples());
        if (!empty.isEmpty()) {
            rows.add(Row.plain(empty));
            return rows;
        }
        rows.add(new Row(new String[]{"Visuals, average over " + fw.samples() + " samples",
                Fmt.num(fw.total()), ""},
                null, null, true, false, 0, null, -1));

        List<Map.Entry<String, Double>> ordered = new ArrayList<>(fw.byAverageDesc());
        boolean byName = sort != null && sort.ranked() == Ranking.Sort.LABEL;
        Comparator<Map.Entry<String, Double>> by = byName
                ? Comparator.comparing(e -> shortName(e.getKey()))
                : Comparator.comparingDouble(Map.Entry::getValue);
        ordered.sort(ascending ? by : by.reversed());

        for (Map.Entry<String, Double> e : ordered) {
            rows.add(new Row(new String[]{shortName(e.getKey()), Fmt.num(e.getValue()), ""},
                    null, null, false, false, 1, null, -1));
        }
        return rows;
    }

    /** Task 5 replaces this with {@code FlywheelStats.displayNames()}, which the report will share. */
    private static String shortName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }
```

`shortName` is deliberately temporary: Task 5 adds `FlywheelStats.displayNames()` and replaces both call
sites, so the screen and the report cannot disagree. It exists here only so this task compiles on its own.

`blocks` and `sections`, the second with the cap:

```java
    /** Spec §5.3 Blocks tab: one row per block, by mesh time per second unless a header was clicked. */
    static List<Row> blocks(SessionResult r, Environment env, ColumnSort sort, boolean ascending) {
        double seconds = r.stats().durationSeconds();
        List<Row> rows = new ArrayList<>();
        for (MeshEntry e : Ranking.mesh(r, ProfileKey.Category.BLOCK, meshSort(sort, Ranking.MeshSort.TIME), ascending)) {
            rows.add(new Row(new String[]{e.key().label(), Fmt.num(e.msPerSecond(seconds)), Fmt.us(e.usPerCall()), Fmt.count(e.calls())},
                    e.key(), e.key().typeId(), false, false, 0, null, -1));
        }
        if (rows.isEmpty()) rows.add(Row.plain(EmptyStates.blocks(env)));
        return rows;
    }

    /**
     * Spec §5.3 Sections tab: one row per rebuilt section; the row's position is the section centre. A
     * 30 s flight can rebuild a thousand sections, so the list is capped and says how many it left out
     * (spec addendum §P4.C2); the report and JSON still carry every one.
     */
    static List<Row> sections(SessionResult r, Environment env, ColumnSort sort, boolean ascending) {
        double seconds = r.stats().durationSeconds();
        List<MeshEntry> all = Ranking.mesh(r, ProfileKey.Category.SECTION, meshSort(sort, Ranking.MeshSort.CALLS), ascending);
        List<Row> rows = new ArrayList<>();
        for (MeshEntry e : all.subList(0, Math.min(MAX_SECTION_ROWS, all.size()))) {
            SectionKey k = (SectionKey) e.key();
            rows.add(new Row(new String[]{k.label(), Fmt.count(e.calls()), Fmt.num(e.callsPerSecond(seconds)), Fmt.ms(e.nanos()),
                    Fmt.ms2(e.calls() == 0 ? 0 : e.nanos() / e.calls())},
                    k, k.typeId(), false, false, 0, new BlockPos(k.centerX(), k.centerY(), k.centerZ()), -1));
        }
        if (all.size() > MAX_SECTION_ROWS) {
            rows.add(Row.plain("... and " + Fmt.count(all.size() - MAX_SECTION_ROWS)
                    + " more sections (the report and JSON have all " + Fmt.count(all.size()) + ")"));
        }
        if (rows.isEmpty()) rows.add(Row.plain(EmptyStates.sections(env)));
        return rows;
    }
```

Add the constant beside `HISTOGRAM_EDGES_MS`:

```java
    /** Spec addendum §P4.C2: the Sections tab is capped; the report and JSON are not. */
    static final int MAX_SECTION_ROWS = 200;
```

and these imports to `Rows.java`:

```java
import io.github.edwardg117.renderable.core.EmptyStates;
import io.github.edwardg117.renderable.core.Environment;
import java.util.Comparator;
```

- [ ] **Step 7: Run the scoped tests**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.*' --tests 'io.github.edwardg117.renderable.report.*' --no-daemon
```

Expected: green. `ResultsScreen` still calls the old `Rows.build`, so `compileJava` fails — Task 4 fixes it.
Do not run the whole build yet.

- [ ] **Step 8: Stage and report**

```bash
git add src/main/java src/test/java
git status --short
```

Commit message for the orchestrator:

```
feat(ui): the tabs use the report's empty-state sentences and cap the Sections list
```

---

Carried in from phase 4b's review of its Task 5, since this task is already rewriting the buttons: **a
disabled Highlight or Teleport button now gives no reason.** Phase 4b greys them out for a second,
unrelated cause — the result's world is gone — and `ResultsScreen` has no tooltip anywhere to distinguish
that from "nothing is selected". It is the same shape as the unexplained `gc f` column phase 4a had to fix
with a note. Adding the file's first tooltip was not worth it inside 4b's small diff; here, where the button
row is being touched anyway, it is. A tooltip reading "the world this session was taken in is gone" on the
disabled state is enough.

### Task 4: Click a header to sort; teleport somewhere a player can stand; truncate right-aligned cells

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/ResultsScreen.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/RowList.java`

Spec addendum §P4.C3, §P4.C6 #3 and #4, §P4.C7.

- [ ] **Step 1: Truncate right-aligned cells in `RowList`**

In `RowList.Entry.render`, replace the cell loop:

```java
            for (int i = 1; i < row.cells().length && i < colX.length; i++) {
                String cell = font.plainSubstrByWidth(row.cells()[i], colW[i]);
                int cx = left + 4 + colX[i];
                if (right[i]) {
                    g.drawString(font, cell, cx + colW[i] - font.width(cell), top + 2, color, false);
                } else {
                    g.drawString(font, cell, cx, top + 2, color, false);
                }
            }
```

- [ ] **Step 2: Rewrite the sorting half of `ResultsScreen`**

Replace the field block, from `private Tab tab = Tab.FRAMES;` down to `private Row selected;`, with:

```java
    private Tab tab = Tab.FRAMES;
    /** The clicked column per tab, and its direction. Each tab opens on its own default column, descending. */
    private final EnumMap<Tab, Integer> sortColumn = new EnumMap<>(Tab.class);
    private final EnumMap<Tab, Boolean> sortAscending = new EnumMap<>(Tab.class);
    private final Set<String> expanded = new HashSet<>();
    private Row selected;
```

Delete the `private Button sortButton;` field.

In `init()`, delete the whole `sortButton = Button.builder(...)` block and its `addRenderableWidget`, and
widen the tab row now that nothing sits to its right:

```java
        int tabPitch = Math.min(88, (width - 8) / TABS_PER_ROW);   // 4 px margin each side
```

Then give the five bottom buttons the room the Sort button used to take — the existing arithmetic already
divides `(width - 24) / 5`, so leave it alone.

Replace `rebuild()`:

```java
    private void rebuild() {
        int[][] layout = tab.columns().layout(list.getRowWidth());
        colX = layout[0];
        colW = layout[1];
        list.setLayout(colX, colW);
        list.setRows(tab, Rows.build(tab, result, environment, activeSort(), ascending(), expanded));
        updateButtons();
    }

    /** The column this tab is sorted by, defaulting to its first sortable column. */
    private int sortIndex() {
        return sortColumn.computeIfAbsent(tab, t -> t.columns().defaultSortColumn());
    }

    private boolean ascending() {
        return sortAscending.getOrDefault(tab, Boolean.FALSE);
    }

    private ColumnSort activeSort() {
        ColumnSort[] sorts = tab.columns().sorts();
        int i = sortIndex();
        return i >= 0 && i < sorts.length ? sorts[i] : ColumnSort.NONE;
    }
```

Delete `sortLabel()`.

Replace `updateButtons()`. Only the `sortButton.visible` line goes; everything else stays. **If plan 4b has
not run**, the method becomes:

```java
    private void updateButtons() {
        boolean hasPos = selected != null && selected.pos() != null;
        boolean hasEntity = selected != null && selected.entityId() >= 0;
        highlightButton.active = hasPos;
        teleportButton.active = hasPos || hasEntity;
    }
```

**If plan 4b has run**, it added a stale-world guard that must be kept:

```java
    private void updateButtons() {
        boolean worldGone = ProfilerController.get().resultWorldGone();
        boolean hasPos = selected != null && selected.pos() != null;
        boolean hasEntity = selected != null && selected.entityId() >= 0;
        highlightButton.active = hasPos && !worldGone;
        teleportButton.active = (hasPos || hasEntity) && !worldGone;
    }
```

Add the click handler, after `onRowClicked`:

```java
    /**
     * A click in the header band sorts by that column: a new column sorts descending, the active column
     * flips direction (spec §5.3). Columns whose {@link ColumnSort} is {@link ColumnSort#NONE} ignore it,
     * which is every column of the Frames and Hitches tabs.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= HEADER_Y - 2 && mouseY < HEADER_Y + font.lineHeight) {
            int clicked = columnAt(mouseX);
            if (clicked >= 0 && tab.columns().sorts()[clicked].sortable()) {
                if (clicked == sortIndex()) {
                    sortAscending.put(tab, !ascending());
                } else {
                    sortColumn.put(tab, clicked);
                    sortAscending.put(tab, Boolean.FALSE);
                }
                rebuild();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** The column index under a screen x, or -1 outside the row. */
    private int columnAt(double mouseX) {
        int left = list.getRowLeft() + 4;
        for (int i = colX.length - 1; i >= 0; i--) {
            if (mouseX >= left + colX[i]) return i;
        }
        return -1;
    }
```

Replace the header-drawing loop in `render` so the active column carries an arrow:

```java
        int left = list.getRowLeft();
        String[] headers = tab.columns().headers();
        boolean[] right = tab.columns().rightAlign();
        ColumnSort[] sorts = tab.columns().sorts();
        int active = sortIndex();
        for (int i = 0; i < headers.length && i < colX.length; i++) {
            String label = headers[i];
            if (i == active && sorts[i].sortable()) label = label + (ascending() ? " ^" : " v");
            int cx = left + 4 + colX[i];
            String h = font.plainSubstrByWidth(label, colW[i]);
            int colour = sorts[i].sortable() ? 0xC0C0C0 : 0xA0A0A0;
            if (i == active && sorts[i].sortable()) colour = 0xFFFFFF;
            if (right[i]) {
                g.drawString(font, h, cx + colW[i] - font.width(h), HEADER_Y, colour, false);
            } else {
                g.drawString(font, h, cx, HEADER_Y, colour, false);
            }
        }
```

Add the imports `java.util.EnumMap` and, if it is not already there, `java.util.Set`. Delete
`import io.github.edwardg117.renderable.core.Ranking;` if nothing else in the file uses it.

- [ ] **Step 3: Teleport somewhere a player can stand**

Replace `teleportToSelected`:

```java
    /**
     * Sends a teleport for the selected row. A section row's stored position is the section's centre block,
     * which is solid ground for anything below the surface, so section rows keep the player's current Y
     * (spec addendum §P4.C3); block entity rows go one block above the block.
     */
    private void teleportToSelected() {
        if (selected == null || minecraft == null || minecraft.getConnection() == null) return;
        if (selected.pos() != null) {
            BlockPos p = selected.pos();
            int y = selected.key() instanceof SectionKey && minecraft.player != null
                    ? minecraft.player.blockPosition().getY()
                    : p.getY() + 1;
            minecraft.getConnection().sendCommand("tp @s " + p.getX() + " " + y + " " + p.getZ());
        } else if (selected.entityId() >= 0 && minecraft.level != null) {
            Entity e = minecraft.level.getEntity(selected.entityId());
            if (e == null) {
                ProfilerController.chat("Renderable: that entity is no longer loaded.");
                return;
            }
            Vec3 v = e.position();
            minecraft.getConnection().sendCommand(String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f", v.x, v.y, v.z));
        }
        onClose();
    }
```

- [ ] **Step 4: Build, then look at the screen**

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, **93 tests**.

```bash
./gradlew runClient -PquickPlay="New World" --no-daemon
```

In the world run `/renderable start 5`, let the results screen open, and check by eye:

- there is no **Sort** button;
- on Block Entities, `render us/f` carries a white `v`; clicking `calls` moves the arrow there and reorders;
  clicking `calls` again turns the arrow into `^` and reverses the order; clicking `name` sorts
  alphabetically;
- on Frames and Hitches, clicking any header does nothing;
- resize the window very narrow: right-aligned numbers truncate instead of overlapping;
- the Sections tab's last header reads `avg ms/rb`.

Stop the client with the filter in Task 6, Step 5.

- [ ] **Step 5: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/ui
git status --short
```

Commit message for the orchestrator:

```
feat(ui): click a column header to sort; teleport above ground from a section row; truncate right-aligned cells
```

---

### Task 5: Flywheel class names read the same on screen and in the report

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/FlywheelStats.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/FlywheelStatsTest.java` (append)
- Test: `src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java` (append)

Spec addendum §P4.C6 #2. The screen shows `SingleAxisRotatingVisual`; the report and JSON show
`com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual`. The screen and report align on the
simple name; the JSON keeps the fully-qualified one as the machine record.

- [ ] **Step 1: Write the failing tests**

Append inside `FlywheelStatsTest`:

```java
    @Test
    void displayNamesAreSimpleUnlessTwoClassesWouldCollide() {
        FlywheelStats plain = new FlywheelStats(1, 0, 0, 0, Map.of(
                "com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual", 4.0,
                "com.simibubi.create.content.kinetics.belt.BeltVisual", 2.0));
        assertEquals("SingleAxisRotatingVisual",
                plain.displayNames().get("com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual"));
        assertEquals("BeltVisual", plain.displayNames().get("com.simibubi.create.content.kinetics.belt.BeltVisual"));

        FlywheelStats clash = new FlywheelStats(1, 0, 0, 0, Map.of(
                "com.simibubi.create.content.kinetics.press.PressVisual", 4.0,
                "com.example.other.mixer.PressVisual", 2.0));
        Map<String, String> names = clash.displayNames();
        assertEquals("press.PressVisual", names.get("com.simibubi.create.content.kinetics.press.PressVisual"));
        assertEquals("mixer.PressVisual", names.get("com.example.other.mixer.PressVisual"));

        assertEquals("NoPackage", new FlywheelStats(1, 0, 0, 0, Map.of("NoPackage", 1.0))
                .displayNames().get("NoPackage"), "a class with no package keeps its name");
        assertTrue(FlywheelStats.none().displayNames().isEmpty());
    }
```

Append inside `TextReportWriterTest`:

```java
    @Test
    void theReportPrintsFlywheelClassesTheWayTheScreenDoes() {
        SessionResult withFw = withFlywheel(TestSessions.sample(), new FlywheelStats(3, 120, 10, 0,
                Map.of("com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual", 449.5,
                        "com.simibubi.create.content.logistics.funnel.FunnelVisual", 201.8)), 0);
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("flywheel", "1.0.6");
        m.put("flywheelBackend", "flywheel:indirect");
        String s = TextReportWriter.write(withFw, Environment.of(m, List.of("FLYWHEEL")), "0.4.0");

        assertTrue(s.contains("\n  SingleAxisRotatingVisual "), s);
        assertTrue(s.contains("\n  FunnelVisual "), s);
        assertFalse(s.contains("com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual"),
                "the fully qualified name belongs in the JSON, not the text table: " + s);
    }
```

- [ ] **Step 2: Run them to verify they fail**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.FlywheelStatsTest' --tests 'io.github.edwardg117.renderable.report.TextReportWriterTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: method displayNames()`.

- [ ] **Step 3: Add `displayNames` to `FlywheelStats`**

Append inside the record body, and add `import java.util.HashMap;` and `import java.util.LinkedHashMap;`:

```java
    /**
     * Display names for the visual classes, in {@link #byAverageDesc()} order: the simple class name, or the
     * last package segment plus the simple name when two classes in this session would otherwise read the
     * same (spec addendum §P4.C6). The screen and the text report both use these; the JSON keeps the
     * fully-qualified names, which are the machine record.
     */
    public Map<String, String> displayNames() {
        Map<String, Integer> byShort = new HashMap<>();
        for (String fqn : avgByClass.keySet()) byShort.merge(simpleName(fqn), 1, Integer::sum);
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : byAverageDesc()) {
            String fqn = e.getKey();
            String simple = simpleName(fqn);
            out.put(fqn, byShort.get(simple) > 1 ? qualifiedName(fqn) : simple);
        }
        return out;
    }

    private static String simpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    /** The last package segment plus the simple name, such as {@code press.PressVisual}. */
    private static String qualifiedName(String fqn) {
        int last = fqn.lastIndexOf('.');
        if (last < 0) return fqn;
        int previous = fqn.lastIndexOf('.', last - 1);
        return previous < 0 ? fqn : fqn.substring(previous + 1);
    }
```

- [ ] **Step 4: Use them in the report and on the screen**

In `TextReportWriter.appendFlywheel`, replace the table loop:

```java
        Table t = new Table(new String[]{"visual class", "avg count"}, new boolean[]{false, true});
        Map<String, String> names = fw.displayNames();
        for (Map.Entry<String, Double> e : fw.byAverageDesc().stream().limit(TOP).toList()) {
            t.row(names.get(e.getKey()), String.format(Locale.ROOT, "%.1f", e.getValue()));
        }
        sb.append(t.render("  ")).append('\n');
```

In `Rows.flywheel`, delete the temporary `shortName` helper Task 3 added and use the shared names, so the
two cannot drift:

```java
        Map<String, String> names = fw.displayNames();
        List<Map.Entry<String, Double>> ordered = new ArrayList<>(fw.byAverageDesc());
        boolean byName = sort != null && sort.ranked() == Ranking.Sort.LABEL;
        Comparator<Map.Entry<String, Double>> by = byName
                ? Comparator.comparing(e -> names.get(e.getKey()))
                : Comparator.comparingDouble(Map.Entry::getValue);
        ordered.sort(ascending ? by : by.reversed());

        for (Map.Entry<String, Double> e : ordered) {
            rows.add(new Row(new String[]{names.get(e.getKey()), Fmt.num(e.getValue()), ""},
                    null, null, false, false, 1, null, -1));
        }
        return rows;
    }
```

(and delete the `private static String shortName(String className)` method below it).

- [ ] **Step 5: Run the tests to verify they pass**

```bash
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`, **95 tests**. `flywheelHookMissingLineAndVisualOrder` uses the single-letter
keys `a`, `b`, `c`, whose simple names are themselves, so it is unaffected.

- [ ] **Step 6: Stage and report**

```bash
git add src/main/java src/test/java
git status --short
```

Commit message for the orchestrator:

```
fix(report): Flywheel class names read the same on screen and in the report
```

---

### Task 6: Dimension-filtered entity labels and a real wireframe highlight

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/WorldOverlay.java`

Spec addendum §P4.C4 and §P4.C5. Nothing here is unit-testable; the proof is the launch in Step 4 and the
in-pack checks in Task 8.

- [ ] **Step 1: Remember the session's dimension**

In `ProfilerController.java`, add the field beside `private long lastVisualSampleSecond = -1;`:

```java
    /** The dimension the running session started in, so the overlay can scope its labels to it. */
    private String sessionDimension = "";
```

In `start(int seconds)`, replace the two lines that attach the probe:

```java
        sessionDimension = mc.level.dimension().location().toString();
        WorldOverlay.clear();
        Probe.setListener(this);
        Probe.attach(session, sessionDimension);
```

In `finish()`, replace `WorldOverlay.setResult(result);` with:

```java
        WorldOverlay.setResult(result, sessionDimension);
```

- [ ] **Step 2: Stamp the dimension on entity labels**

In `WorldOverlay.java`, change the signature and the entity loop:

```java
    /** {@code dimension} is the dimension the session was taken in; every label is scoped to it. */
    public static void setResult(SessionResult r, String dimension) {
        int n = RenderableConfig.OVERLAY_LABEL_COUNT.get();
        long sessionTotal = r.stats().totalNanos();
        int frames = r.frameCount();
        Set<ProfileKey> flagged = r.hitchFlaggedKeys();
        List<Label> out = new ArrayList<>(3 * n);
        for (RankedEntry e : head(Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY), n)) {
            BlockEntityKey k = (BlockEntityKey) e.key();
            out.add(label(e, flagged, frames, sessionTotal, k.dimension(), k.x() + 0.5, k.y() + 1.25, k.z() + 0.5, -1));
        }
        for (RankedEntry e : head(Ranking.byKey(r, ProfileKey.Category.ENTITY), n)) {
            EntityKey k = (EntityKey) e.key();
            out.add(label(e, flagged, frames, sessionTotal, dimension, 0, 0, 0, k.entityId()));
        }
        for (MeshEntry e : head(Ranking.mesh(r, ProfileKey.Category.SECTION, Ranking.MeshSort.CALLS), n)) {
            SectionKey k = (SectionKey) e.key();
            String text = String.format(Locale.ROOT, "section %d %d %d: %d rebuilds, %s ms",
                    k.x(), k.y(), k.z(), e.calls(), Fmt.ms(e.nanos()));
            out.add(new Label(k.dimension(), k.centerX() + 0.5, k.centerY() + 0.5, k.centerZ() + 0.5, -1, text, Colors.YELLOW));
        }
        labels = List.copyOf(out);
    }
```

The trailing `enabled = RenderableConfig.OVERLAY_ENABLED.get();` line is gone from `setResult` either way.
**If plan 4b has not run**, that line was the only thing reading the config, so take 4b's lazy accessor with
it or `overlayEnabled = false` would stop being honoured — replace the field and the three accessors:

```java
    /** Null until first read, then whatever the config said or the user last chose. */
    private static Boolean enabled;
```

```java
    /**
     * Whether labels are drawn. Read from the config on first use, not at every session end, so
     * {@code /renderable overlay off} survives the next session (spec addendum §P4.B6).
     */
    public static boolean isEnabled() {
        Boolean e = enabled;
        if (e == null) {
            e = RenderableConfig.OVERLAY_ENABLED.get();
            enabled = e;
        }
        return e;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static void toggle() {
        setEnabled(!isEnabled());
    }
```

and replace the two raw reads of the field: in `onRenderLevelStage`,
`boolean wantLabels = enabled && !labels.isEmpty();` becomes
`boolean wantLabels = isEnabled() && !labels.isEmpty();`, and in `render`,
`if (enabled && !labels.isEmpty()) {` becomes `if (isEnabled() && !labels.isEmpty()) {`.

**If plan 4b has run**, it already did all of that; leave it alone.

Update the `Label` record's Javadoc, which still describes the old behaviour:

```java
    /**
     * One label, built once per result. Block entities and sections carry the position they were profiled
     * at; entities are looked up per frame by id and carry the dimension the session ran in, because entity
     * ids are reused across dimensions. A label is drawn only in the dimension it names.
     */
```

- [ ] **Step 3: Draw the box**

Replace `drawHighlight` and add the two helpers below it:

```java
    /** The twelve edges of a cube, as pairs of corner indices differing in one bit. */
    private static final int[] EDGES = {
            0, 1, 0, 2, 0, 4, 1, 3, 1, 5, 2, 3,
            2, 6, 3, 7, 4, 5, 4, 6, 5, 7, 6, 7};

    /**
     * Projects the highlighted cube's eight corners and draws its twelve edges, two pixels thick, over
     * everything (spec addendum §P4.C5). An edge with an endpoint behind the camera is skipped rather than
     * abandoning the whole marker, and a cube that projects smaller than {@link #MIN_HIGHLIGHT} falls back
     * to the rectangle so a distant block stays findable.
     */
    private static void drawHighlight(GuiGraphics g, Vec3 cam, int guiW, int guiH) {
        BlockPos p = highlight;
        if (p == null) return;
        if (System.currentTimeMillis() > highlightUntilMillis) {
            highlight = null;
            return;
        }
        float[] sx = new float[8];
        float[] sy = new float[8];
        boolean[] ok = new boolean[8];
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double x = p.getX() + ((i & 1) == 0 ? -0.01 : highlightSize + 0.01);
            double y = p.getY() + ((i & 2) == 0 ? -0.01 : highlightSize + 0.01);
            double z = p.getZ() + ((i & 4) == 0 ? -0.01 : highlightSize + 0.01);
            SCRATCH.set((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), 1.0f).mul(VIEW_PROJECTION);
            float w = SCRATCH.w();
            if (w <= 0.001f) continue;                       // behind the camera; the edges using it are skipped
            ok[i] = true;
            sx[i] = (SCRATCH.x() / w + 1.0f) * 0.5f * guiW;
            sy[i] = (1.0f - SCRATCH.y() / w) * 0.5f * guiH;
            minX = Math.min(minX, sx[i]);
            maxX = Math.max(maxX, sx[i]);
            minY = Math.min(minY, sy[i]);
            maxY = Math.max(maxY, sy[i]);
        }

        boolean allVisible = true;
        for (boolean v : ok) allVisible &= v;
        // A distant block projects to a few pixels: a box would be invisible, so draw the old marker instead.
        if (allVisible && maxX - minX < MIN_HIGHLIGHT && maxY - minY < MIN_HIGHLIGHT) {
            float cx = (minX + maxX) * 0.5f;
            float cy = (minY + maxY) * 0.5f;
            int x0 = Math.round(cx - MIN_HIGHLIGHT * 0.5f);
            int y0 = Math.round(cy - MIN_HIGHLIGHT * 0.5f);
            if (x0 + MIN_HIGHLIGHT <= 0 || y0 + MIN_HIGHLIGHT <= 0 || x0 >= guiW || y0 >= guiH) return;
            g.renderOutline(x0, y0, MIN_HIGHLIGHT, MIN_HIGHLIGHT, HIGHLIGHT_COLOR);
            return;
        }

        for (int e = 0; e < EDGES.length; e += 2) {
            int a = EDGES[e];
            int b = EDGES[e + 1];
            if (ok[a] && ok[b]) line(g, sx[a], sy[a], sx[b], sy[b], guiW, guiH);
        }
    }

    /**
     * A two-pixel line between two screen points. Pixels with the same minor coordinate are merged into one
     * fill, so a long edge costs a handful of quads rather than one per pixel.
     */
    private static void line(GuiGraphics g, float x0f, float y0f, float x1f, float y1f, int guiW, int guiH) {
        int x0 = Math.round(x0f);
        int y0 = Math.round(y0f);
        int x1 = Math.round(x1f);
        int y1 = Math.round(y1f);
        if (Math.abs(x1 - x0) >= Math.abs(y1 - y0)) {
            if (x0 > x1) {
                int t = x0; x0 = x1; x1 = t;
                t = y0; y0 = y1; y1 = t;
            }
            if (x1 < 0 || x0 > guiW) return;
            int span = x1 - x0;
            int runStart = x0;
            int runY = y0;
            for (int x = x0; x <= x1; x++) {
                int y = span == 0 ? y0 : y0 + Math.round((float) (y1 - y0) * (x - x0) / span);
                if (y != runY) {
                    fillClamped(g, runStart, runY, x, runY + 2, guiW, guiH);
                    runStart = x;
                    runY = y;
                }
            }
            fillClamped(g, runStart, runY, x1 + 2, runY + 2, guiW, guiH);
        } else {
            if (y0 > y1) {
                int t = y0; y0 = y1; y1 = t;
                t = x0; x0 = x1; x1 = t;
            }
            if (y1 < 0 || y0 > guiH) return;
            int span = y1 - y0;
            int runStart = y0;
            int runX = x0;
            for (int y = y0; y <= y1; y++) {
                int x = span == 0 ? x0 : x0 + Math.round((float) (x1 - x0) * (y - y0) / span);
                if (x != runX) {
                    fillClamped(g, runX, runStart, runX + 2, y, guiW, guiH);
                    runStart = y;
                    runX = x;
                }
            }
            fillClamped(g, runX, runStart, runX + 2, y1 + 2, guiW, guiH);
        }
    }

    /** Fills a rectangle clipped to the screen; nothing is drawn when it falls entirely outside. */
    private static void fillClamped(GuiGraphics g, int x0, int y0, int x1, int y1, int guiW, int guiH) {
        int cx0 = Math.max(0, x0);
        int cy0 = Math.max(0, y0);
        int cx1 = Math.min(guiW, x1);
        int cy1 = Math.min(guiH, y1);
        if (cx1 <= cx0 || cy1 <= cy0) return;
        g.fill(cx0, cy0, cx1, cy1, HIGHLIGHT_COLOR);
    }
```

- [ ] **Step 4: Build and look at it**

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 95 tests.

```bash
./gradlew runClient -PquickPlay="New World" --no-daemon
```

Place a chest, run `/renderable start 5`, open the results screen, select the chest's position row on Block
Entities and press **Highlight**. Expect a box in perspective around the block, visible through walls, whose
edges stay drawn when you walk so close that a corner goes behind you. Walk far away: the box shrinks to the
8-pixel square. Then go through a Nether portal and confirm no entity labels are drawn there, and that they
return on the way back.

Stop the client:

```powershell
Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*fml.modFolders=renderable%%H:\repos\Renderable\build*' } | Select-Object ProcessId, CommandLine
```

Confirm every row is this repository's dev client, then:

```powershell
Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*fml.modFolders=renderable%%H:\repos\Renderable\build*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
```

- [ ] **Step 5: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/ui src/main/java/io/github/edwardg117/renderable/ProfilerController.java
git status --short
```

Commit message for the orchestrator:

```
feat(ui): dimension-scoped entity labels and a wireframe highlight box
```

---

### Task 7: README and spec amendments

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md`

- [ ] **Step 1: Update `README.md`**

In "Using it", item 3, the sentence "Labels are shown only in the dimension they were profiled in" is now
true for entities as well and needs no change. Add a new item 4:

```markdown
4. On the results screen, click a column header to sort by it; click it again to reverse. The active column
   carries `v` (descending) or `^` (ascending). Frames and Hitches have a fixed order. **Highlight** draws a
   wireframe box around the selected block, or around the whole 16-block section for a Sections row, for ten
   seconds and through walls.
```

In "Reading a report", add:

```markdown
- **Flywheel** visual classes read as simple class names on screen and in the text report; the JSON keeps
  the fully-qualified names. Two classes with the same simple name are both prefixed with their last
  package segment.
- The **Sections** tab shows the 200 busiest sections and says how many it left out; the text report's top
  20 and the JSON's complete list are unchanged.
```

- [ ] **Step 2: Apply the spec amendments**

In `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md`:

1. **§5.3**, replace the paragraph beginning "Every list sorts by any numeric column" with:

```markdown
The Block Entities, Entities, Particles, Blocks, Sections and Flywheel lists sort by any numeric column:
clicking a column header sorts by it descending, clicking the active header reverses it, and the active
header carries an arrow. The Frames and Hitches tabs have a fixed order. Position and individual rows have
**Highlight** (draw a wireframe box at the position for 10 s, the whole 16-block section for a Sections row)
and **Teleport** (sends `/tp @s x y z` as chat, keeping the player's current Y for a section row so they do
not arrive inside stone; if the player lacks permission the server's error is what they see). A **Copy
report** button copies the text report; **Open folder** opens the report directory. The Sections list shows
the 200 busiest sections and a trailing row saying how many it left out.
```

2. **§5.4**, in the first paragraph, replace "Floating text labels" with "Floating text labels, each drawn
   only in the dimension the session ran in,".

- [ ] **Step 3: Build**

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 95 tests.

- [ ] **Step 4: Stage and report**

```bash
git add README.md docs/superpowers/specs
git status --short
```

Commit message for the orchestrator:

```
docs: README and spec for phase 4c
```

---

### Task 8: In-pack acceptance (human step)

**Files:**
- Create: `docs/acceptance/phase-4c.md`

The user runs this in the real Gears & Frontiers instance with the jar built from this plan's tip. The
orchestrator writes the log, has it reviewed, and commits it.

1. **Sorting (§P4.C7).** Run a 30 s session at the base. On Block Entities, click `verts/f`: rows reorder by
   vertices with a white `v` on that header. Click again: `^` and the reverse order. Click `name`:
   alphabetical. Repeat one click each on Entities, Particles, Blocks, Sections and Flywheel. Confirm
   clicking a header on Frames and Hitches does nothing. Say whether the arrow is legible at GUI scale 3.
2. **Empty states (§P4.C1).** With shaders on — which turned the Flywheel backend off in run 2 of the phase
   2/3 log — open the Flywheel tab and confirm it reads `(no data: no Flywheel visualization ran; backend
   flywheel:off)`, the same sentence the report prints. Paste both.
3. **Sections cap (§P4.C2).** Fly into unloaded terrain for 30 s, then open the Sections tab and paste its
   last row, which should read `... and N more sections (the report and JSON have all M)`.
4. **Teleport (§P4.C3).** Select a Sections row and press Teleport: you should arrive above ground at that
   section's column, not inside it.
5. **Highlight (§P4.C5).** Highlight a block entity and a section. Confirm a box in perspective rather than
   a flat rectangle, visible through walls, and that standing inside the highlighted section still draws the
   edges that are in front of you.
6. **Labels (§P4.C4).** After a session with entities in view, go through a portal: no entity labels. Come
   back: they return.
7. **Header parity (§P4.C6).** Sections tab's last header reads `avg ms/rb`; the Flywheel tab and the
   report's Flywheel table both read `SingleAxisRotatingVisual`; at GUI scale 3 with a narrow window, no
   right-aligned number overlaps its neighbour.

Then the orchestrator:

```bash
git add docs/acceptance/phase-4c.md
git commit -m "docs: phase 4c acceptance log"
```

---

## Notes for the orchestrator

- Tasks 1, 2, 3 and 5 are JUnit; Tasks 4 and 6 need a dev client launch and a human eye.
- Task 2 leaves the tree **not compiling** on purpose; Tasks 3 and 4 close it. Do not hand Task 2 to a
  subagent that has been told to run the full build.
- **Reviewer checklist per task:** `core` and `report` free of Minecraft imports; the empty-state sentences
  written once, in `EmptyStates`, and identical in the screen and the report; `Rows.build`'s five call
  paths all passing the environment; no per-frame allocation added to `WorldOverlay.render` beyond the three
  small arrays in `drawHighlight`, which only run while a highlight is live.
- If a reviewer proposes amending §5.3 to match the old cycling button rather than implementing header
  clicks, point at §P4.C7's rejected alternatives: the decision is the user's and it is recorded.
