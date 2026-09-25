# Renderable Phase 4c Implementation Plan: screen and overlay

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the results screen do what spec §5.3 promised and stop it disagreeing with the report: sort by any numeric column by clicking its header, tell "that mod is not installed" from "nothing happened", cap the Sections list, teleport somewhere a player can stand, truncate right-aligned cells, print Flywheel class names the same way in both places, dimension-filter entity labels, and draw a real wireframe box instead of a bounding rectangle.

**Architecture:** The sortable columns become data: a `ColumnSort` value on every `Tab.Columns` entry says which `Ranking.Sort` or `Ranking.MeshSort` that header means, or that it means nothing. `ResultsScreen` keeps a sort column and direction per tab, handles clicks in the header band, and passes the resolved `ColumnSort` to `Rows`, which also receives the `Environment` it has been missing. The empty-state sentences move to one `core` class so the screen and the report cannot drift. The highlight projects the eight corners as before but draws the twelve edges as run-merged two-pixel lines in the same GUI layer, so it still shows through walls and shaders.

**Tech Stack:** Java 21, ModDevGradle 2.0.147, NeoForge 21.1.220, vanilla `Screen` and `ObjectSelectionList`, JUnit 5 for everything outside `ui.screen.Rows` and `ui.WorldOverlay` (both import Minecraft types and cannot be unit-tested).

**Spec:** `docs/superpowers/specs/2026-09-20-renderable-phase-4-design.md` §P4.C (all of it), plus §P4.D1's `TabTest`, which lives here because this plan changes `Tab`. Base spec §5.3, §5.4, §6.1.

**Repository:** `H:\repos\Renderable`, branch `phase-4c-screen`, created from `phase-4b-robustness` at `cbbf3a1`. Commits are YubiKey-signed: the orchestrator commits after telling the user to touch the key. **Subagents stage and report; they do not commit.**

**Ordering:** Written assuming only plan 4a was on `main`; **executed from 2026-09-25 on top of plan 4b**, which the user chose so that 4b's and 4c's in-pack acceptance can run as one session (Task 8). Every "if 4b has run" branch below takes the 4b side, and the steps that applied only without 4b are gone. `main` stays at the accepted 4a state until the combined acceptance passes; then 4b and 4c merge together.

**Revised on 2026-09-25**, before execution, against the code as 4b left it. Tasks 1a, 2, 3 and 4 were rewritten: Task 1a had no code; Tasks 2 and 3 each claimed a state the build cannot be in (a scoped `--tests` run still compiles the whole source set, so an uncompilable tree runs no tests at all); Task 2 would have opened the ranked tabs in a different order from the report; and Task 4's header clicks would have sorted names Z to A on the first click. Each rewritten task says what changed and why.

**Verified facts you can rely on:**

- `Tab` and `Tab.Columns` are pure Java: `Tab.java` imports only `core.Ranking` and `java.util`, so a JUnit test can exercise it directly. `Ranking`, `RankedEntry`, `MeshEntry` and `FlywheelStats` are likewise Minecraft-free.
- `Rows` imports `net.minecraft.core.BlockPos` and `WorldOverlay` imports the client renderer, so neither is unit-testable; their proof is the in-pack checks in Task 8.
- `RowList.Entry.render` truncates left-aligned cells with `font.plainSubstrByWidth(cell, colW[i])` but draws right-aligned cells with a bare `g.drawString(font, cell, ...)`, so a long number overlaps its neighbour. Re-read on `cbbf3a1`.
- The ranked and particle column names and cell expressions are written twice, term for term, in `TextReportWriter.appendRanked`/`appendParticles` and in `Rows.cells`/`Rows.particles`. Re-read on `cbbf3a1`.
- `ResultsScreen.teleportToSelected` sends `tp @s x (y+1) z` from `selected.pos()`, and `Rows.sections` sets that position to `new BlockPos(k.centerX(), k.centerY(), k.centerZ())` — the section's centre block, which is solid for any section below the surface.
- `WorldOverlay.setResult` builds entity labels with `dimension = null`, and `drawLabels` treats null as "draw in any dimension". The README claims labels are dimension-scoped.
- `WorldOverlay.drawHighlight` returns early when **any** of the eight corners has `w <= 0.001f`, so standing inside a highlighted section draws nothing at all.
- `FlywheelStats` is `record FlywheelStats(int samples, double avgBlockEntities, double avgEntities, double avgEffects, Map<String, Double> avgByClass)` with `total()` and `byAverageDesc()`. `Rows.flywheel` shortens class names with `k.substring(k.lastIndexOf('.') + 1)`; `TextReportWriter.appendFlywheel` prints `e.getKey()` in full.
- `Tooltip.create(Component)` and `AbstractWidget.setTooltip(@Nullable Tooltip)` exist in the pinned NeoForge 21.1.220 jar (javap on `build/moddev/artifacts/neoforge-21.1.220.jar`), and `AbstractWidget.render` refreshes the tooltip from a hover test that does not look at `active` (sources jar), so a disabled button shows its tooltip. Vanilla's `TitleScreen` does exactly that for its disabled Multiplayer button.
- The phase 3 acceptance log recorded 1052 distinct sections rebuilt in one 30 s flight, which is what the Sections tab renders one row each for.
- The suite is **108 tests** after plans 4a and 4b.

**Deviations from the spec addendum recorded in this plan.** Task 7 amends the addendum for each.

- The Flywheel tab's two sortable columns reuse `Ranking.Sort.LABEL` (class name) and `Ranking.Sort.TOTAL` (average count) rather than getting an enum of their own, because those rows are `Map.Entry<String, Double>` and two orderings do not justify a third sort type. `Rows.flywheel` documents the interpretation and `TabTest` pins it.
- `Tab.Columns` becomes a list of `Column` records instead of gaining a fourth parallel array (Task 2).
- Each tab opens in the text report's order, and a name column's first click sorts A to Z (Tasks 2 and 4).
- The Blocks and Sections name columns sort by `MeshSort.LABEL` directly, instead of carrying `Ranking.Sort.LABEL` for `Rows` to translate (Task 2).
- A number too wide for its column is drawn as `#`s instead of being truncated like text (Task 2).
- `Sort.SHADOW` sorts by the shadow share the `shadow` column prints, not by shadow nanoseconds, and
  `MeshSort` has no `AVG`: the Sections average column sorts by `PER_CALL`, which for a section is per
  rebuild and orders rows identically (Task 1, from its spec review).
- The ranked and particle column names and cells move into `core/RankedTable`, which both surfaces call (Task 1a).

---

## File map

```
README.md                                                     Modify (Task 7)
docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md
                                                              Modify: §5.3, §5.4 (Task 7)
docs/superpowers/specs/2026-09-20-renderable-phase-4-design.md
                                                              Modify: §P4.B3, §P4.C6, §P4.C7 (Task 7)
src/main/java/io/github/edwardg117/renderable/
  core/Ranking.java                                           Modify: new sorts, ascending (Task 1); Sort.next() goes (Task 4)
  core/RankedTable.java                                       Create (Task 1a)
  core/EmptyStates.java                                       Create (Task 3)
  core/FlywheelStats.java                                     Modify: displayNames() (Task 5)
  report/Table.java                                           Modify: nameThenNumbers (Task 1a)
  report/TextReportWriter.java                                Modify: RankedTable, EmptyStates, Flywheel display names
  ui/screen/ColumnSort.java                                   Create (Task 2)
  ui/screen/Tab.java                                          Modify: Column records, default order (Task 2); sortable() goes (Task 4)
  ui/screen/Rows.java                                         Modify: RankedTable, gc f note, Environment, sorting, section cap, Flywheel names
  ui/screen/RowList.java                                      Modify: Column records, #s for numbers that do not fit (Task 2)
  ui/screen/ResultsScreen.java                                Modify: header clicks, arrows, teleport Y, tooltip, no Sort button
  ui/WorldOverlay.java                                        Modify: entity dimension, wireframe highlight (Task 6)
  ProfilerController.java                                     Modify: remember the session dimension (Task 6)
src/test/java/io/github/edwardg117/renderable/
  core/RankingTest.java                                       Modify: new sorts (Task 1); next() assertions go (Task 4)
  core/RankedTableTest.java                                   Create (Task 1a)
  core/EmptyStatesTest.java                                   Create (Task 3)
  core/FlywheelStatsTest.java                                 Modify: display names (Task 5)
  report/TextReportWriterTest.java                            Modify: Flywheel simple names (Task 5)
  ui/screen/TabTest.java                                      Create (Task 2)
docs/acceptance/phase-4b-4c.md                                Create (Task 8)
```

**Expected test totals**, starting from 108 with 4a and 4b in: 112 after Task 1, 114 after Task 1a, 120 after
Task 2, 122 after Task 3, 122 after Task 4, 124 after Task 5, 124 at the end. Every task ends with
`./gradlew build` green.

---

### Task 1: Sort by any numeric column, in either direction

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Ranking.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/RankingTest.java` (append)

Spec addendum §P4.C7. `Ranking.Sort` covers six of the nine ranked columns; `MeshSort` covers two of the
nine mesh columns; neither can sort ascending. The two-argument `sort` and three-argument `mesh` keep their
current meaning (descending) so no existing caller changes.

**Every sort orders rows by what its column displays.** Two corrections from this task's spec review on
2026-09-25, which the code below already carries:

- `Sort.SHADOW` sorts by `RankedEntry::shadowPercent`, the shadow share of the row's own render time that the
  `shadow` column prints. The first version sorted by `shadowNanos`, which puts a 2000 us renderer with 40%
  shadow above a 50 us one with 60% under a descending arrow — the reverse of what the column is for.
- `MeshSort` has no `AVG`. The first version had one for the Sections table's `avg ms/rb` column, computed as
  nanoseconds per rebuild, but `PER_CALL` (microseconds per call, and for a section a call is a rebuild)
  orders rows identically. Task 2 gives that column `PER_CALL`.

The review also found assertions that a plausibly wrong implementation would pass; the tests below close
them. The code-quality review then folded `LABEL` into each `switch` as an ordinary arm (identical results:
the shared label tie-break is a no-op when the primary key is the label), rewrote the Javadocs to say what
each form does, and added `meshTimeAndCallsAreDifferentSortsAndTiesReadAToZ`, because `phase3()` ranks every
mesh row the same by time as by calls and so could not tell `TIME` from `CALLS`. The code below is the
reviewed code as committed.

- [ ] **Step 1: Write the failing test**

Append inside `RankingTest` (the helpers `keys` and `meshKeys` go with them):

```java
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
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.RankingTest' --no-daemon
```

Expected: compilation failure — `cannot find symbol: variable LABEL`, `SHADOW`, `GC_FRAMES`, `PER_CALL`,
and no three-argument `sort` or four-argument `mesh`.

- [ ] **Step 3: Extend `Ranking`**

Replace the two enums and the two sorting methods. The enums first:

```java
    /** What a column of a ranked table sorts by (spec §5.3). */
    public enum Sort {
        TOTAL, RENDER, TICK, PEAK, VERTICES, CALLS, SHADOW, GC_FRAMES, LABEL;

        public Sort next() {
            Sort[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    /**
     * What a column of the Blocks or Sections table sorts by. PER_CALL is time per call, which for a
     * section is time per rebuild, so the Sections table's average column sorts by it.
     */
    public enum MeshSort { TIME, CALLS, PER_CALL, LABEL }
```

`next()` now cycles through nine values. Its callers are the Sort button in `ResultsScreen`, which Task 4
removes together with `next()` itself, and `RankingTest.sortModesReorder`, which asserts
`Sort.TOTAL.next() == Sort.RENDER` and `Sort.CALLS.next() == Sort.TOTAL` — the second is no longer true once
`SHADOW` follows `CALLS`. Until Task 4 deletes them, change that assertion to:

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
     * Returns a new list sorted by the chosen column, smallest first when {@code ascending}. Ties go A to Z
     * by label in both directions, so flipping a column never reorders the rows it ties.
     */
    public static List<RankedEntry> sort(List<RankedEntry> entries, Sort sort, boolean ascending) {
        Comparator<RankedEntry> by = switch (sort) {
            case TOTAL -> Comparator.comparingLong(RankedEntry::totalNanos);
            case RENDER -> Comparator.comparingLong(RankedEntry::renderNanos);
            case TICK -> Comparator.comparingLong(RankedEntry::tickNanos);
            case PEAK -> Comparator.comparingLong(RankedEntry::maxFrameNanos);
            case VERTICES -> Comparator.comparingLong(RankedEntry::vertices);
            case CALLS -> Comparator.comparingLong(RankedEntry::calls);
            // The shadow column shows shadow time as a share of the row's own render time, not nanoseconds.
            case SHADOW -> Comparator.comparingDouble(RankedEntry::shadowPercent);
            case GC_FRAMES -> Comparator.comparingLong(RankedEntry::gcFrames);
            case LABEL -> Comparator.comparing(RankedEntry::label);
        };
        List<RankedEntry> copy = new ArrayList<>(entries);
        copy.sort((ascending ? by : by.reversed()).thenComparing(RankedEntry::label));
        return copy;
    }
```

And the mesh sort:

```java
    /** Mesh rows of one category, descending by the chosen column, ties broken by label. */
    public static List<MeshEntry> mesh(SessionResult r, ProfileKey.Category category, MeshSort sort) {
        return mesh(r, category, sort, false);
    }

    /**
     * Mesh rows of one category sorted by the chosen column, smallest first when {@code ascending}. Ties go
     * A to Z by label in both directions, as in {@link #sort(List, Sort, boolean)}.
     */
    public static List<MeshEntry> mesh(SessionResult r, ProfileKey.Category category, MeshSort sort,
                                       boolean ascending) {
        List<MeshEntry> out = new ArrayList<>();
        for (MeshEntry e : r.mesh()) {
            if (e.key().category() == category) out.add(e);
        }
        Comparator<MeshEntry> by = switch (sort) {
            case TIME -> Comparator.comparingLong(MeshEntry::nanos);
            case CALLS -> Comparator.comparingLong(MeshEntry::calls);
            case PER_CALL -> Comparator.comparingDouble(MeshEntry::usPerCall);
            case LABEL -> Comparator.comparing(e -> e.key().label());
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

Expected: `BUILD SUCCESSFUL`, **112 tests**.

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

### Task 1a: One definition of the ranked and particle columns, for the report and the screen

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/RankedTable.java`
- Create: `src/test/java/io/github/edwardg117/renderable/core/RankedTableTest.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/Table.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java`

Added after phase 4a from the code-quality review of its Task 7, and rewritten on 2026-09-25 when this plan
was stacked on 4b: the first draft was prose with no code. **Do this before Task 2.**

The ranked (block entity, entity) and particle **column names and cell expressions are written twice**, term
for term: in `TextReportWriter.appendRanked`/`appendParticles` and in `Rows.cells`/`Rows.particles`. Nothing
enforces that they agree; today they do only because a reviewer read both files side by side, and phase 4a
had to add `gc f` to both by hand, in lockstep. This task moves the names and the cell text into one `core`
class that both surfaces call. That is not a new dependency direction: `ui` and `report` both already depend
on `core`, and `core` already carries presentation code by precedent (`Fmt` is entirely formatting;
`Splits.frame`/`average` produce report strings).

What stays where it is: the screen's column weights, alignment and sorting (Task 2), the report's `TOP`
truncation, and both surfaces' Blocks, Sections and Flywheel tables. Only the Flywheel table and one Sections
header (`avg ms/rb` on screen, `avg ms/rebuild` in the report, by design, §P4.C6 #1) genuinely differ; the
Blocks table and the Sections cells are identical on both surfaces and are still written twice. Moving them
into a `core` class the way this task moves the ranked ones is a sound follow-up, deliberately left out of
this plan to keep its scope; Task 7 adds it to the addendum's deferred list (§P4.3).

Two changes from the first draft of this task:

- The class is `RankedTable`, not `Columns`: `Tab.Columns` already exists, and two `Columns` types one
  import apart would be misread.
- The other half of the first draft — turning `Tab.Columns`' parallel arrays into a list of records — moved
  into Task 2, which rewrites `Tab.java` anyway and creates the `ColumnSort` type the record needs. Doing it
  here would mean writing every column twice.

Also, while these files are open: the results screen's Frames tab has no equivalent of the note explaining
`gc f` that phase 4a added to the text report. The sentence moves into `RankedTable`, and the Frames tab
prints it under its `GC` row when there was a collection, exactly as the report does under its `GC:` line.

Two corrections from this task's review on 2026-09-25, which the code below already carries:

- **The screen's `gc f` note is two rows, not one.** The sentence is 559 px wide in the vanilla font, and at
  the default (Auto) GUI scale the GUI is 426 to 480 px wide, so a one-row note lost its explanation of `-`.
  `RankedTable` holds it as two lines that fit (244 px and 311 px); the report prints them joined, which is
  the sentence it has always printed.
- **The test pins every cell under its header.** The first version checked lengths and two cells, and six
  deliberate swaps of cells or headers passed it.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/io/github/edwardg117/renderable/core/RankedTableTest.java`:

```java
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
```

The fixture facts it relies on: in `TestSessions.phase3()` the press is the costliest block entity and emits
100 vertices on each of 20 frames; the flame renders three times a frame, and `calls` counts render calls
only, so its average live count is 3.0. Every value within each row differs from the others, so a swap of two
cells or two headers fails the test.

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.RankedTableTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: class RankedTable`.

- [ ] **Step 3: Write `RankedTable`**

`src/main/java/io/github/edwardg117/renderable/core/RankedTable.java`:

```java
package io.github.edwardg117.renderable.core;

import java.util.List;

/**
 * The columns of the ranked tables (block entities, entities) and of the particle table: their names and the
 * text of each cell, written once for the text report and the results screen. The report takes its headers
 * from here; the screen writes its headers out in {@code Tab}, beside their widths and sorts. Widths,
 * truncation and sorting stay with each surface. The first cell is the row's name and the rest are numbers,
 * except that a peak with no clean frame reads "-" (see {@link Fmt#peakMs}).
 */
public final class RankedTable {

    /** Headers of the block entity and entity tables. */
    public static final List<String> RANKED = List.of(
            "name", "render us/f", "tick us/f", "share", "peak ms", "gc f", "shadow", "verts/f", "calls");

    /** Headers of the particle table: no peak or shadow of interest, and the average live count instead. */
    public static final List<String> PARTICLE = List.of(
            "class", "render us/f", "tick us/f", "share", "avg live", "verts/f", "gc f");

    /**
     * Explains the gc f column and a "-" peak, as two lines so each fits the results screen at the default GUI
     * scale. The screen prints them under its GC row; the report prints {@link #GC_COLUMN_NOTE}.
     */
    public static final List<String> GC_COLUMN_NOTE_LINES = List.of(
            "(gc f counts frames a pause could account for;",
            "those are left out of peak ms, and \"-\" means none was clean)");

    /** The note as one line, which the report prints under its GC line. */
    public static final String GC_COLUMN_NOTE = String.join(" ", GC_COLUMN_NOTE_LINES);

    private RankedTable() {
    }

    /** One row of a ranked table. The caller supplies the name, because the two surfaces label rows differently. */
    public static String[] ranked(String name, RankedEntry e, int frames, long total) {
        return new String[]{
                name,
                Fmt.us(e.renderUsPerFrame(frames)),
                Fmt.us(e.tickUsPerFrame(frames)),
                Fmt.pct(e.sharePercent(total)),
                Fmt.peakMs(e.maxFrameNanos(), e.gcFrames()),
                Fmt.count(e.gcFrames()),
                Fmt.pct(e.shadowPercent()),
                Fmt.num(e.verticesPerFrame(frames)),
                Fmt.count(e.calls())};
    }

    /** One row of the particle table. */
    public static String[] particle(RankedEntry e, int frames, long total) {
        return new String[]{
                e.label(),
                Fmt.us(e.renderUsPerFrame(frames)),
                Fmt.us(e.tickUsPerFrame(frames)),
                Fmt.pct(e.sharePercent(total)),
                Fmt.num(e.callsPerFrame(frames)),
                Fmt.num(e.verticesPerFrame(frames)),
                Fmt.count(e.gcFrames())};
    }
}
```

`RANKED` and `PARTICLE` are `List.of`, not arrays: a `public static final String[]` can be written to by any
caller.

- [ ] **Step 4: The report uses it**

In `src/main/java/io/github/edwardg117/renderable/report/Table.java`, add below the constructor:

```java
    /** A table whose first column is a left-aligned name and whose other columns are right-aligned numbers. */
    static Table nameThenNumbers(List<String> headers) {
        boolean[] right = new boolean[headers.size()];
        for (int i = 1; i < right.length; i++) right[i] = true;
        return new Table(headers.toArray(String[]::new), right);
    }
```

In `TextReportWriter.java`, add `import io.github.edwardg117.renderable.core.RankedTable;`, and make the
report's note the shared sentence with the report's indentation, so the existing tests that look for
`TextReportWriter.GC_COLUMN_NOTE` keep passing unchanged:

```java
    /** Printed under the GC line so every table's gc f column and its "-" peaks explain themselves. */
    static final String GC_COLUMN_NOTE = "  " + RankedTable.GC_COLUMN_NOTE;
```

In `appendRanked`, replace the `Table t = new Table(...)` statement and the loop below it with:

```java
        Table t = Table.nameThenNumbers(RankedTable.RANKED);
        for (RankedEntry e : entries.subList(0, Math.min(TOP, entries.size()))) {
            t.row((Object[]) RankedTable.ranked(e.label(), e, frames, total));
        }
```

In `appendParticles`, the same:

```java
        Table t = Table.nameThenNumbers(RankedTable.PARTICLE);
        for (RankedEntry e : entries.subList(0, Math.min(TOP, entries.size()))) {
            t.row((Object[]) RankedTable.particle(e, frames, total));
        }
```

The `(Object[])` cast passes the array as `row`'s varargs array itself, which is what `row` wants; without it
javac warns about an inexact varargs argument. `Table.row` already throws when the cell count and the header
count differ, so a future mismatch fails the first report test that prints a row.

The report's text must come out byte for byte as before. Every existing `TextReportWriterTest` assertion is
that check, and none of them changes.

- [ ] **Step 5: The screen uses it**

In `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java`, add
`import io.github.edwardg117.renderable.core.RankedTable;`, then:

1. In `ranked(...)`, both `cells(...)` calls become `RankedTable.ranked(...)` with the same arguments.
2. Delete `static String[] cells(String name, RankedEntry e, int frames, long total)`; nothing else calls it.
3. In `particles(...)`, replace the seven-element `new String[]{...}` with `RankedTable.particle(e, frames, total)`:

```java
        for (RankedEntry e : Ranking.sort(Ranking.byKey(r, ProfileKey.Category.PARTICLE), sort)) {
            rows.add(new Row(RankedTable.particle(e, frames, total),
                    e.key(), e.typeId(), false, false, 0, null, -1));
        }
```

4. In `frames(...)`, immediately after the `GC` row, add the note, one row per line. A one-cell row spans the
   whole row width (`RowList.Entry.render` draws single-cell rows across every column), and each line fits at
   the default GUI scale:

```java
        rows.add(Row.plain("GC", st.gcCount() + " collections, " + st.gcMillis() + " ms paused"));
        if (st.gcCount() > 0) {
            for (String line : RankedTable.GC_COLUMN_NOTE_LINES) rows.add(Row.plain(line));
        }
```

- [ ] **Step 6: Run the whole build**

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, **114 tests**.

- [ ] **Step 7: Stage and report**

```bash
git add src/main/java src/test/java
git status --short
```

Commit message for the orchestrator:

```
feat(ui): the Frames tab explains gc f; the report and the screen share one definition of the ranked columns
```

---

### Task 2: Columns are records that declare what they sort by

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/ui/screen/ColumnSort.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Tab.java` (replace)
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/RowList.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/ResultsScreen.java` (the header loop only)
- Create: `src/test/java/io/github/edwardg117/renderable/ui/screen/TabTest.java`

Spec addendum §P4.C7, §P4.C6 #1 and #4, plus §P4.D1's `TabTest`. Rewritten on 2026-09-25; four decisions
differ from the first draft and from the addendum's wording, and Task 7 amends the addendum to match:

1. **`Tab.Columns` becomes a list of `Column(name, weight, rightAlign, sort)` records**, not three parallel
   arrays plus a fourth. A mismatch between parallel arrays was silent — `RowList.render` iterates
   `i < row.cells().length && i < colX.length`, so a short array truncates a row rather than throwing — and
   phase 4a had to extend three of them in lockstep twice. With records, miscounting cannot be written.
2. **Each tab opens in the order the text report prints**, not on "the first sortable column after the
   name". The first draft's rule would have opened the ranked tabs on `render us/f`, while the report and
   today's screen rank by render plus tick time; an entity that is cheap to render but expensive to tick
   (the player, at 498 us a tick in the 4a acceptance run) would silently move. Each `Columns` constant names
   the sort it opens by, and `TabTest` pins those to the report's orderings.
3. **The Blocks and Sections name columns sort by `MeshSort.LABEL`** directly, instead of carrying
   `Ranking.Sort.LABEL` for `Rows` to translate. The column says what it means, and no tab carries both kinds
   of sort.
4. **A number too wide for its column is never cut short like text**, as §P4.C6 #4 said, because a
   truncated number reads as a different one: `125,539` cut to `125,5`. It may reach three of the four blank
   pixels `layout` leaves before it, which still leaves two blank pixels before its neighbour; if it is still
   too wide its decimals are rounded away (`10780.9` reads `10781`, less precise but never a different
   number); and only then is it drawn as `#`s, as a spreadsheet does. The first version of this task drew
   `#`s as soon as a number exceeded its column, and its review showed that at 1920x1080 on the Auto GUI scale
   (a 480-px GUI, the most common setup) that hid real values from the acceptance logs — `1501.5` verts/f,
   `31,920` calls, `1054.9` render us/f — which the old code drew legibly a pixel into the gap. The logic lives
   in `Tab.Columns.fitNumber`, which is pure Java and tested with the vanilla font's glyph widths. This fix
   lived in Task 4 in the first draft; it moves here because this task already rewrites the same loop in
   `RowList`.

This task's review on 2026-09-25 changed decision 4 as described there, and found that the first `TabTest`
passed 11 of 14 deliberate one-line mistakes in `Tab` (neighbouring sorts swapped, a wrong opening column on
Particles or Flywheel, a renamed header, a changed weight, columns overlapping). `TabTest` now pins every
tab's columns in full, checks the padding `layout` leaves at the GUI widths players use, and tests
`fitNumber`. It also gave `ColumnSort` a constructor that rejects a column carrying both kinds of sort, and
replaced the `N` alias in `Tab` with a static import of `ColumnSort.NONE`. The code below is the reviewed
code as committed.

The tree compiles and the suite passes at the end of this task. The first draft said the tree would not
compile until Task 4; that was wrong about why, and an uncompilable tree cannot run a scoped test either —
Gradle compiles the whole source set before `--tests` filters anything.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/io/github/edwardg117/renderable/ui/screen/TabTest.java`:

```java
package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.core.RankedTable;
import io.github.edwardg117.renderable.core.Ranking;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.*;

class TabTest {

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
        for (Tab t : new Tab[]{Tab.FRAMES, Tab.HITCHES}) {
            for (Tab.Column col : t.columns().all()) assertFalse(col.sort().sortable(), t + " must not sort");
        }
        // Ranked rows carry ranked sorts only. The Flywheel tab reuses two of them for its class names and
        // average counts (spec addendum §P4.C7).
        for (Tab t : new Tab[]{Tab.BLOCK_ENTITIES, Tab.ENTITIES, Tab.PARTICLES, Tab.FLYWHEEL}) {
            assertEquals(ColumnSort.of(Ranking.Sort.LABEL), t.columns().get(0).sort(), t + "'s name column sorts by label");
            for (Tab.Column col : t.columns().all()) assertNull(col.sort().mesh(), t + " must not carry a mesh sort");
        }
        // Mesh rows carry mesh sorts only.
        for (Tab t : new Tab[]{Tab.BLOCKS, Tab.SECTIONS}) {
            assertEquals(ColumnSort.of(Ranking.MeshSort.LABEL), t.columns().get(0).sort(), t + "'s name column sorts by label");
            for (Tab.Column col : t.columns().all()) assertNull(col.sort().ranked(), t + " must not carry a ranked sort");
        }
        for (Tab t : new Tab[]{Tab.BLOCK_ENTITIES, Tab.ENTITIES, Tab.PARTICLES, Tab.FLYWHEEL, Tab.BLOCKS, Tab.SECTIONS}) {
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
    void theRankedTabsShowTheReportsColumnsAndEveryTabOpensInTheReportsOrder() {
        // The names are written out in Tab so each column reads as one line; this is what stops them drifting
        // from the report's.
        assertEquals(RankedTable.RANKED, names(Tab.BLOCK_ENTITIES));
        assertEquals(RankedTable.RANKED, names(Tab.ENTITIES));
        assertEquals(RankedTable.PARTICLE, names(Tab.PARTICLES));
        for (Tab t : new Tab[]{Tab.BLOCK_ENTITIES, Tab.ENTITIES, Tab.PARTICLES}) {
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
                int[][] layout = t.columns().layout(rowWidth);
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
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.ui.screen.TabTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: class ColumnSort`, `class Column`, `method all()`.

- [ ] **Step 3: Write `ColumnSort`**

`src/main/java/io/github/edwardg117/renderable/ui/screen/ColumnSort.java`:

```java
package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.core.Ranking;

/**
 * What clicking a column header sorts by (spec §5.3). A tab's rows are either ranked entries or mesh entries,
 * so at most one of the two is set; {@link #NONE} marks a column that is not sortable, which is every column
 * of the Frames and Hitches tabs, and the Flywheel tab's {@code share} column.
 *
 * <p>The Flywheel tab's rows are neither: they are class names and average counts. It reuses
 * {@link Ranking.Sort#LABEL} for the class column and {@link Ranking.Sort#TOTAL} for the count, which
 * {@code Rows.flywheel} interprets directly.
 */
public record ColumnSort(Ranking.Sort ranked, Ranking.MeshSort mesh) {

    public ColumnSort {
        if (ranked != null && mesh != null) {
            throw new IllegalArgumentException("a column sorts one kind of row, not both");
        }
    }

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

    /** True for a name column, which reads A to Z on its first click; a number reads largest first. */
    public boolean ascendingFirst() {
        return ranked == Ranking.Sort.LABEL || mesh == Ranking.MeshSort.LABEL;
    }
}
```

- [ ] **Step 4: Replace `Tab.java`**

`Tab.sortable()` keeps its current meaning — the tabs the Sort button applies to — because the Sort button
still exists until Task 4, which deletes both.

```java
package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.core.Ranking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.edwardg117.renderable.ui.screen.ColumnSort.NONE;

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

    /** True for tabs whose rows are ranked entries the Sort button can reorder. */
    public boolean sortable() {
        return this == BLOCK_ENTITIES || this == ENTITIES || this == PARTICLES;
    }

    /**
     * One column: its header, its share of the row width, whether it holds a right-aligned number, and what
     * clicking its header sorts by.
     */
    public record Column(String name, int weight, boolean rightAlign, ColumnSort sort) {
    }

    /**
     * A tab's columns and the one it opens sorted by. {@link #layout(int)} turns weights into pixel offsets.
     * Kept as a nested class so enum constants can reference the constants without a forward-reference error.
     * Frames and Hitches have no sortable column, so their {@code defaultSortColumn} of 0 means nothing, and
     * the screen only draws a sort arrow on a sortable column.
     */
    public record Columns(List<Column> all, int defaultSortColumn) {
        public Columns {
            all = List.copyOf(all);
            if (all.isEmpty()) throw new IllegalArgumentException("a tab needs at least one column");
            if (defaultSortColumn < 0 || defaultSortColumn >= all.size()) {
                throw new IllegalArgumentException("defaultSortColumn " + defaultSortColumn + " is not a column");
            }
        }

        /** Blank pixels {@link #layout} leaves after every column; {@link #fitNumber} lets a number use all but one. */
        static final int PADDING = 4;

        private static final Pattern DECIMALS = Pattern.compile("\\d+\\.\\d+");

        static final Columns METRIC_VALUE = of(NONE,
                left("metric", 2, NONE),
                left("value", 3, NONE));
        static final Columns HITCH = of(NONE,
                left("frame", 2, NONE),
                right("total ms", 1, NONE),
                left("split", 7, NONE));
        static final Columns RANKED = of(ColumnSort.of(Ranking.Sort.TOTAL),
                left("name", 5, ColumnSort.of(Ranking.Sort.LABEL)),
                right("render us/f", 1, ColumnSort.of(Ranking.Sort.RENDER)),
                right("tick us/f", 1, ColumnSort.of(Ranking.Sort.TICK)),
                right("share", 1, ColumnSort.of(Ranking.Sort.TOTAL)),
                right("peak ms", 1, ColumnSort.of(Ranking.Sort.PEAK)),
                right("gc f", 1, ColumnSort.of(Ranking.Sort.GC_FRAMES)),
                right("shadow", 1, ColumnSort.of(Ranking.Sort.SHADOW)),
                right("verts/f", 1, ColumnSort.of(Ranking.Sort.VERTICES)),
                right("calls", 1, ColumnSort.of(Ranking.Sort.CALLS)));
        static final Columns PARTICLE = of(ColumnSort.of(Ranking.Sort.TOTAL),
                left("class", 5, ColumnSort.of(Ranking.Sort.LABEL)),
                right("render us/f", 1, ColumnSort.of(Ranking.Sort.RENDER)),
                right("tick us/f", 1, ColumnSort.of(Ranking.Sort.TICK)),
                right("share", 1, ColumnSort.of(Ranking.Sort.TOTAL)),
                right("avg live", 1, ColumnSort.of(Ranking.Sort.CALLS)),
                right("verts/f", 1, ColumnSort.of(Ranking.Sort.VERTICES)),
                right("gc f", 1, ColumnSort.of(Ranking.Sort.GC_FRAMES)));
        static final Columns FLYWHEEL = of(ColumnSort.of(Ranking.Sort.TOTAL),
                left("visual class", 6, ColumnSort.of(Ranking.Sort.LABEL)),
                right("avg count", 1, ColumnSort.of(Ranking.Sort.TOTAL)),
                right("share", 1, NONE));
        static final Columns BLOCK = of(ColumnSort.of(Ranking.MeshSort.TIME),
                left("block", 8, ColumnSort.of(Ranking.MeshSort.LABEL)),
                right("mesh ms/s", 2, ColumnSort.of(Ranking.MeshSort.TIME)),
                right("us/call", 2, ColumnSort.of(Ranking.MeshSort.PER_CALL)),
                right("calls", 2, ColumnSort.of(Ranking.MeshSort.CALLS)));
        static final Columns SECTION = of(ColumnSort.of(Ranking.MeshSort.CALLS),
                left("section", 6, ColumnSort.of(Ranking.MeshSort.LABEL)),
                right("rebuilds", 2, ColumnSort.of(Ranking.MeshSort.CALLS)),
                right("rebuilds/s", 2, ColumnSort.of(Ranking.MeshSort.CALLS)),
                right("mesh ms", 2, ColumnSort.of(Ranking.MeshSort.TIME)),
                right("avg ms/rb", 3, ColumnSort.of(Ranking.MeshSort.PER_CALL)));

        /** Columns that open sorted by the first column whose sort is {@code opensBy}. */
        private static Columns of(ColumnSort opensBy, Column... columns) {
            for (int i = 0; i < columns.length; i++) {
                if (columns[i].sort().equals(opensBy)) return new Columns(List.of(columns), i);
            }
            throw new IllegalArgumentException("no column sorts by " + opensBy);
        }

        private static Column left(String name, int weight, ColumnSort sort) {
            return new Column(name, weight, false, sort);
        }

        private static Column right(String name, int weight, ColumnSort sort) {
            return new Column(name, weight, true, sort);
        }

        public int size() {
            return all.size();
        }

        public Column get(int i) {
            return all.get(i);
        }

        /** Returns {x offsets, widths} for a row of the given pixel width. Each column keeps 4 px of padding. */
        public int[][] layout(int rowWidth) {
            int total = 0;
            for (Column c : all) total += c.weight();
            int[] x = new int[all.size()];
            int[] w = new int[all.size()];
            int cursor = 0;
            for (int i = 0; i < all.size(); i++) {
                int px = rowWidth * all.get(i).weight() / total;
                x[i] = cursor;
                w[i] = Math.max(8, px - PADDING);
                cursor += px;
            }
            return new int[][]{x, w};
        }

        /**
         * What to draw for a right-aligned number in a column {@code width} px wide. It may reach up to
         * {@code PADDING - 1} px left of its column: every glyph's advance ends in a blank pixel, so that still leaves
         * two blank pixels before the column on its left, twice the spacing inside a number. Wider than that, its
         * decimals are rounded away, and if it still does not fit it is drawn as #s, as a spreadsheet does. It is
         * never cut short like text, because a truncated number reads as a different one: {@code 125,539} cut to
         * {@code 125,5} (spec addendum §P4.C6 #4).
         */
        static String fitNumber(String cell, int width, ToIntFunction<String> textWidth) {
            int room = width + PADDING - 1;
            if (textWidth.applyAsInt(cell) <= room) return cell;
            Matcher m = DECIMALS.matcher(cell);
            if (m.find()) {
                // Rounded to a whole number, 10780.9 still reads 10781: less precise, never a different number.
                String whole = new BigDecimal(m.group()).setScale(0, RoundingMode.HALF_UP).toPlainString();
                String rounded = cell.substring(0, m.start()) + whole + cell.substring(m.end());
                if (textWidth.applyAsInt(rounded) <= room) return rounded;
            }
            return "#".repeat(Math.max(1, width / textWidth.applyAsInt("#")));
        }
    }
}
```

`Sections` opens on `rebuilds` rather than `rebuilds/s`, because `of` takes the first column whose sort
matches, and both sort by `MeshSort.CALLS`. The Sections header `avg ms` becomes `avg ms/rb` with its weight
raised from 2 to 3 (§P4.C6 #1); the report keeps `avg ms/rebuild`.

The names of the ranked and particle columns are written out here rather than read from `RankedTable` by
index, so each column is one line a reader can check; `TabTest` asserts they equal `RankedTable`'s.

Now that a test ties the two lists together, say so where a reader of `core` will look. In
`core/RankedTable.java`, the class Javadoc's sentence "the screen writes its headers out in {@code Tab}, beside
their widths and sorts" becomes "the screen writes its headers out in {@code Tab}, beside their widths and
sorts, and {@code TabTest} asserts they equal {@link #RANKED} and {@link #PARTICLE}". Keep each `{@code ...}`
tag on one line: split across a line break it renders with a stray space.

- [ ] **Step 5: `RowList` reads the records, and fits numbers with `Tab.Columns.fitNumber`**

`fitNumber` lives in `Tab.Columns` (Step 4), beside the `PADDING` it depends on, so a unit test can exercise
it with the vanilla font's glyph widths; `RowList` passes `font::width`. In `RowList.Entry.render`, replace
`boolean[] right = tab.columns().rightAlign();` with `Tab.Columns columns = tab.columns();`, and replace the
cell loop with:

```java
            for (int i = 1; i < row.cells().length && i < colX.length && i < columns.size(); i++) {
                int cx = left + 4 + colX[i];
                if (columns.get(i).rightAlign()) {
                    String cell = Tab.Columns.fitNumber(row.cells()[i], colW[i], font::width);
                    g.drawString(font, cell, cx + colW[i] - font.width(cell), top + 2, color, false);
                } else {
                    g.drawString(font, font.plainSubstrByWidth(row.cells()[i], colW[i]), cx, top + 2, color, false);
                }
            }
```

The number keeps its right edge and grows leftward, into up to three pixels of the padding before its column.

- [ ] **Step 6: `ResultsScreen` draws its headers from the records**

The header loop at the end of `render` is the only other reader of the old arrays. Replace it (from
`int left = list.getRowLeft();` to the end of the `for` loop) with:

```java
        int left = list.getRowLeft();
        Tab.Columns columns = tab.columns();
        for (int i = 0; i < columns.size() && i < colX.length; i++) {
            Tab.Column column = columns.get(i);
            int cx = left + 4 + colX[i];
            String h = font.plainSubstrByWidth(column.name(), colW[i]);
            if (column.rightAlign()) {
                g.drawString(font, h, cx + colW[i] - font.width(h), HEADER_Y, 0xA0A0A0, false);
            } else {
                g.drawString(font, h, cx, HEADER_Y, 0xA0A0A0, false);
            }
        }
```

Task 4 adds the sort arrow to this loop.

- [ ] **Step 7: Run the whole build**

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, **120 tests**.

If `layoutFitsTheRowAndNeverOverlaps` fails at `rowWidth = 120` because a column does not advance, the weights
are too uneven for a narrow screen: that is a real finding, not a test to relax. Report it.

- [ ] **Step 8: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/ui/screen src/test/java/io/github/edwardg117/renderable/ui src/main/java/io/github/edwardg117/renderable/core/RankedTable.java
git status --short
```

Commit message for the orchestrator:

```
feat(ui): columns are records that declare what they sort by; a number too wide for its column is rounded, never cut short
```

---

### Task 3: One place for the empty-state sentences, and `Rows` gets the environment

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/EmptyStates.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/ResultsScreen.java` (one line)
- Create: `src/test/java/io/github/edwardg117/renderable/core/EmptyStatesTest.java`

Spec addendum §P4.C1 and §P4.C2. `TextReportWriter` can say `(no data: Flywheel is not installed)` and
`(no data: no Flywheel visualization ran; backend flywheel:off)`; `Rows` has no `Environment` and says
neither.

Plan 4b has run, so `Environment` already has `ABSENT`, `has(String)` and `value(String)`, and
`TextReportWriter.appendFlywheel` already uses them. The first draft's step to add them is gone.

- [ ] **Step 1: Write the failing test**

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

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.EmptyStatesTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: class EmptyStates`.

- [ ] **Step 3: Write `EmptyStates`**

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
        if (!env.has("flywheelBackend")) return "(no data: Flywheel is not installed)";
        if (samples == 0) return "(no data: no Flywheel visualization ran; backend " + env.value("flywheelBackend") + ")";
        return "";
    }
}
```

`flywheel` tests `has`, not `value(...) == ABSENT`, so it makes exactly the decision the report makes today.

- [ ] **Step 4: Use it in `TextReportWriter`**

The strings are unchanged, so no report test changes. Add `import io.github.edwardg117.renderable.core.EmptyStates;`.

In `appendRanked`:

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

In `appendFlywheel`, replace everything from `sb.append("Flywheel\n");` down to and including the
`if (fw.samples() == 0) { ... }` block with:

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

Delete the now-unused private `meshNoData`. The old mesh code appended `'\n'` after a `meshNoData(...)` string
that already ended in `\n`; the replacements append `"\n\n"` for the same result.

- [ ] **Step 5: Thread the environment and the sort through `Rows`, and cap the Sections tab**

Replace `Rows.build` and the five list builders it dispatches to. `Rows` keeps everything else, including
`frames`, `hitches` and the helpers.

```java
    /** Rows for one tab. {@code sort} is the clicked column, or null for the tab's default order; {@code ascending} its direction. */
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

    /** The ranked sort a clicked column means, or the report's order, by total time, when there is none. */
    private static Ranking.Sort rankedSort(ColumnSort sort) {
        return sort == null || sort.ranked() == null ? Ranking.Sort.TOTAL : sort.ranked();
    }

    /** The mesh sort a clicked column means, or {@code fallback}, the report's order for that table. */
    private static Ranking.MeshSort meshSort(ColumnSort sort, Ranking.MeshSort fallback) {
        return sort == null || sort.mesh() == null ? fallback : sort.mesh();
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
            rows.add(new Row(RankedTable.ranked(t.typeId() + "  (" + t.members() + ")", t, frames, total),
                    null, t.typeId(), true, true, 0, null, -1));
            if (expanded.contains(t.typeId())) {
                for (RankedEntry m : Ranking.sort(Ranking.membersOf(r, category, t.typeId()), by, ascending)) {
                    rows.add(new Row(RankedTable.ranked(memberLabel(m.key()), m, frames, total),
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
            rows.add(new Row(RankedTable.particle(e, frames, total),
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
sites, so the screen and the report cannot disagree. It exists here only so this task compiles on its own,
and Task 5 deletes it together with its Javadoc.

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

- [ ] **Step 6: Keep `ResultsScreen` compiling**

`Rows.build` has a new signature and `ResultsScreen.rebuild()` is its only caller. Until Task 4 replaces the
Sort button with header clicks, pass the button's ranked sort through on the tabs it applies to, and the
default order everywhere else, so the screen behaves exactly as it does today. In `rebuild()`, replace the
`list.setRows(...)` line with:

```java
        list.setRows(tab, Rows.build(tab, result, environment, tab.sortable() ? ColumnSort.of(sort) : null, false, expanded));
```

Without the `tab.sortable()` guard, a sort chosen on Block Entities would carry over to Blocks, where the
button is hidden.

- [ ] **Step 7: Run the whole build**

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, **122 tests**.

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

### Task 4: Click a header to sort; teleport somewhere a player can stand; say why a button is disabled

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/ResultsScreen.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Tab.java` (delete `sortable()`)
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Ranking.java` (delete `Sort.next()`)
- Modify: `src/test/java/io/github/edwardg117/renderable/core/RankingTest.java` (two assertions)

Spec addendum §P4.C3 and §P4.C7, plus one item carried in from phase 4b's review of its Task 5 (Step 4).
Right-aligned truncation, which the first draft put here, moved to Task 2.

- [ ] **Step 1: Replace the Sort button with header clicks**

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

In `init()`, delete the whole `sortButton = Button.builder(...)` statement and its `addRenderableWidget`, and
widen the tab row now that nothing sits to its right:

```java
        int tabPitch = Math.min(88, (width - 8) / TABS_PER_ROW);   // 4 px margin each side
```

The five bottom buttons already divide `(width - 24) / 5`; leave that arithmetic alone.

Replace `rebuild()`, and add the three helpers after it:

```java
    private void rebuild() {
        int[][] layout = tab.columns().layout(list.getRowWidth());
        colX = layout[0];
        colW = layout[1];
        list.setLayout(colX, colW);
        list.setRows(tab, Rows.build(tab, result, environment, activeSort(), ascending(), expanded));
        updateButtons();
    }

    /** The column this tab is sorted by: the last one clicked, or the one the tab opens on. */
    private int sortIndex() {
        return sortColumn.computeIfAbsent(tab, t -> t.columns().defaultSortColumn());
    }

    private boolean ascending() {
        return sortAscending.getOrDefault(tab, Boolean.FALSE);
    }

    private ColumnSort activeSort() {
        return tab.columns().get(sortIndex()).sort();
    }
```

Delete `sortLabel()`.

Add the click handler after `onRowClicked`:

```java
    /**
     * A click in the header band sorts by that column. The active column flips direction; a new column starts
     * in its natural direction, which is A to Z for a name and largest first for a number (spec §5.3).
     * Columns whose {@link ColumnSort} is {@link ColumnSort#NONE} ignore it, which is every column of the
     * Frames and Hitches tabs.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= HEADER_Y - 2 && mouseY < HEADER_Y + font.lineHeight) {
            int clicked = columnAt(mouseX);
            if (clicked >= 0) {
                ColumnSort s = tab.columns().get(clicked).sort();
                if (s.sortable()) {
                    if (clicked == sortIndex()) {
                        sortAscending.put(tab, !ascending());
                    } else {
                        sortColumn.put(tab, clicked);
                        sortAscending.put(tab, s.ascendingFirst());
                    }
                    rebuild();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** The column index under a screen x, or -1 outside the row. */
    private int columnAt(double mouseX) {
        int left = list.getRowLeft() + 4;
        if (mouseX < left || mouseX >= left + list.getRowWidth()) return -1;
        for (int i = colX.length - 1; i >= 0; i--) {
            if (mouseX >= left + colX[i]) return i;
        }
        return -1;
    }
```

Replace the header loop in `render` (the one Task 2 wrote) so the active column carries an arrow:

```java
        int left = list.getRowLeft();
        Tab.Columns columns = tab.columns();
        int active = sortIndex();
        for (int i = 0; i < columns.size() && i < colX.length; i++) {
            Tab.Column column = columns.get(i);
            boolean sortable = column.sort().sortable();
            String arrow = i == active && sortable ? (ascending() ? " ^" : " v") : "";
            // Truncate the name, never the arrow: the arrow is the only sign of how the list is sorted.
            String h = font.plainSubstrByWidth(column.name(), Math.max(0, colW[i] - font.width(arrow))) + arrow;
            int colour = !sortable ? 0xA0A0A0 : i == active ? 0xFFFFFF : 0xC0C0C0;
            int cx = left + 4 + colX[i];
            if (column.rightAlign()) {
                g.drawString(font, h, cx + colW[i] - font.width(h), HEADER_Y, colour, false);
            } else {
                g.drawString(font, h, cx, HEADER_Y, colour, false);
            }
        }
```

Add the imports `java.util.EnumMap` and `net.minecraft.client.gui.components.Tooltip` (Step 4 uses it).
Delete `import io.github.edwardg117.renderable.core.Ranking;` once nothing in the file uses it.

- [ ] **Step 2: Delete what the Sort button leaves behind**

- `Tab.sortable()` has no caller once Step 1 is done. Delete it.
- `Ranking.Sort.next()` has no caller outside `RankingTest`. Delete it, and delete the two `next()`
  assertions at the end of `RankingTest.sortModesReorder`; the test keeps its three sorting assertions, so
  the test count does not change.

Confirm nothing else calls either:

```bash
grep -rn "sortable()\|\.next()" src/main/java src/test/java
```

Expected: only `ColumnSort.sortable()` and its callers, and no `Sort.next()`.

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

- [ ] **Step 4: Say why Highlight and Teleport are disabled**

Carried in from phase 4b's review of its Task 5, since this task is already rewriting the button row. Phase
4b greys out Highlight and Teleport for a second, unrelated cause — the result's world is gone — and nothing
on the screen distinguishes that from "nothing is selected". It is the same shape as the unexplained `gc f`
column phase 4a had to fix with a note.

Verified against the pinned jar (`build/moddev/artifacts/neoforge-21.1.220.jar` with javap, and the sources
jar): `Tooltip.create(Component)` exists; `AbstractWidget.setTooltip(@Nullable Tooltip)` exists and `null`
clears it; and `AbstractWidget.render` computes the hover state and refreshes the tooltip without looking at
`active`, so a disabled button shows its tooltip. Vanilla relies on that: `TitleScreen` gives its disabled
Multiplayer button a tooltip explaining why.

Add the field beside the others:

```java
    /** Shown on Highlight and Teleport while they are disabled because the session's world is gone. */
    private final Tooltip worldGoneTooltip = Tooltip.create(Component.literal("The world this session was taken in is gone"));
```

and replace `updateButtons()`, which keeps 4b's stale-world guard and drops the `sortButton` line:

```java
    private void updateButtons() {
        boolean worldGone = ProfilerController.get().resultWorldGone();
        boolean hasPos = selected != null && selected.pos() != null;
        boolean hasEntity = selected != null && selected.entityId() >= 0;
        highlightButton.active = hasPos && !worldGone;
        teleportButton.active = (hasPos || hasEntity) && !worldGone;
        Tooltip why = worldGone ? worldGoneTooltip : null;
        highlightButton.setTooltip(why);
        teleportButton.setTooltip(why);
    }
```

- [ ] **Step 5: Run the whole build**

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, **122 tests**.

Nothing in this task is unit-testable: `ResultsScreen` is a Minecraft `Screen`. The by-eye checks the first
draft put here — the arrow, the clicks, the narrow window, the teleport, the tooltip — are in Task 8, where a
person runs them in the pack in one session. Do not launch a client for this task.

- [ ] **Step 6: Stage and report**

```bash
git add src/main/java src/test/java
git status --short
```

Commit message for the orchestrator:

```
feat(ui): click a column header to sort; teleport above ground from a section row; say why a button is disabled
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

Expected: `BUILD SUCCESSFUL`, **124 tests**. `flywheelHookMissingLineAndVisualOrder` uses the single-letter
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

Plan 4b already removed the trailing `enabled = RenderableConfig.OVERLAY_ENABLED.get();` line from
`setResult` and made the flag lazy; leave both alone. Check for any other caller of the old one-argument
`setResult` with `grep -rn "setResult(" src/main/java`; `ProfilerController.finish` should be the only one.

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
    /**
     * Scratch space for {@link #drawHighlight}, reused every frame; render thread only, like {@link #SCRATCH}.
     * A highlight can outlive the session it came from and keep drawing while the next one samples, so it
     * allocates nothing per frame.
     */
    private static final float[] CORNER_X = new float[8];
    private static final float[] CORNER_Y = new float[8];
    private static final boolean[] CORNER_VISIBLE = new boolean[8];

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
        float[] sx = CORNER_X;
        float[] sy = CORNER_Y;
        boolean[] ok = CORNER_VISIBLE;
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
            ok[i] = w > 0.001f;                               // reset every frame: the arrays are reused
            if (!ok[i]) continue;                             // behind the camera; the edges using it are skipped
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

- [ ] **Step 4: Run the whole build**

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 124 tests. Nothing here is unit-testable, and the first draft's dev-client look
(the box in perspective, the corner behind the camera, the distant 8-pixel square, the portal) is in Task 8,
where a person runs it in the pack. Do not launch a client for this task.

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
- Modify: `docs/superpowers/specs/2026-09-20-renderable-phase-4-design.md`

Where the code and the text below disagree, the code is right: read what each sentence describes before
writing it, and report a disagreement rather than papering over it.

- [ ] **Step 1: Update `README.md`**

In "Using it", item 3, the sentence "Labels are shown only in the dimension they were profiled in" is now
true for entities as well and needs no change. Add a new item 4:

```markdown
4. On the results screen, click a column header to sort by it; click it again to reverse. Each list opens in
   the same order as the text report, the active column carries `v` (descending) or `^` (ascending), and a
   name column sorts A to Z on its first click. Frames and Hitches have a fixed order. A number too wide for
   its column is rounded to a whole number, and shows as `###` only if it still does not fit; widen the window
   or lower the GUI scale to read it. **Highlight** draws a wireframe box around the
   selected block, or around the whole 16-block section for a Sections row, for ten seconds and through
   walls. Highlight and Teleport are greyed out once the world the session was taken in is gone, and hovering
   over them says so.
```

In "Reading a report", add:

```markdown
- **Flywheel** visual classes read as simple class names on screen and in the text report; the JSON keeps
  the fully-qualified names. Two classes with the same simple name are both prefixed with their last
  package segment.
- The **Sections** tab shows the 200 busiest sections and says how many it left out; the text report's top
  20 and the JSON's complete list are unchanged.
```

Then read the rest of the README for anything this plan made false. Phase 4b's sentence about a highlight left
over from an earlier session is still true.

- [ ] **Step 2: Amend the base spec**

In `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md`:

1. **§5.3**, replace the paragraph beginning "Every list sorts by any numeric column" with:

```markdown
The Block Entities, Entities, Particles, Blocks, Sections and Flywheel lists sort by any column. Each opens in
the order the text report uses; clicking a column header sorts by it (largest first for a number, A to Z for a
name), clicking the active header reverses it, and the active header carries an arrow. The Frames and Hitches
tabs have a fixed order. A number too wide for its column is rounded to a whole number and, if it still does
not fit, drawn as `#`s; it is never cut short. Position and
individual rows have **Highlight** (draw a wireframe box at the position for 10 s, the whole 16-block section
for a Sections row) and **Teleport** (sends `/tp @s x y z` as chat, keeping the player's current Y for a
section row so they do not arrive inside stone; if the player lacks permission the server's error is what
they see). Both are disabled, with a tooltip saying why, once the world the session was taken in is gone. A
**Copy report** button copies the text report; **Open folder** opens the report directory. The Sections list
shows the 200 busiest sections and a trailing row saying how many it left out. The ranked and particle column
names and cell text are defined once, in `core/RankedTable`, and shared with the text report.
```

2. **§5.4**, in the first paragraph, replace "Floating text labels" with "Floating text labels, each drawn
   only in the dimension the session ran in,".

- [ ] **Step 3: Amend the addendum**

In `docs/superpowers/specs/2026-09-20-renderable-phase-4-design.md`:

1. **§P4.B3**, in the paragraph on why the overlay is not measured, the leftover highlight is described as
   "today two nested screen-space outlines around the block's projected corners, not the wireframe §P4.C5
   proposes". That is no longer true; it is now the wireframe box of §P4.C5. Say so.
2. **§P4.C6 #4**, replace the resolution with: a right-aligned number too wide for its column may reach three
   of the four blank pixels before it; beyond that its decimals are rounded away, and only if it still does not
   fit is it drawn as `#`s, as a spreadsheet does. It is never truncated like text, because a truncated number
   reads as a different one (`125,539` cut to `125,5`). Record why the threshold is not the column width: at
   1920x1080 on the Auto GUI scale that hid real values the old code drew legibly.
3. **§P4.C7**, the bullet beginning "`Tab.Columns` gains a `ColumnSort[] sorts`": the columns are now a list
   of `Column(name, weight, rightAlign, sort)` records, so parallel arrays cannot drift; keep the paragraph's
   reasoning for a record over the two existing enums. Add to the design: each tab opens in the text report's
   order (`TabTest` pins each to the report's ordering); a new column's first click is A to Z for a name and
   largest first for a number; the Blocks and Sections name columns carry `MeshSort.LABEL`. Correct the
   `Ranking` bullet: `MeshSort` gains `PER_CALL` and `LABEL` only (the Sections average column sorts by
   `PER_CALL`, which for a section is per rebuild, so an `AVG` would order rows identically), and every sort
   orders rows by what its column displays — `SHADOW` by the shadow share, not by shadow nanoseconds. Amend
   the acceptance check's "Click `name` and the rows sort alphabetically" only if it no longer matches.
4. **§P4.C6**, add a row: the ranked and particle column names and cell expressions were written twice, in
   `TextReportWriter` and `Rows`; they are now defined once in `core/RankedTable`, and `TabTest` asserts that
   the screen's ranked and particle headers equal it.
5. Check §P4.C1, §P4.C2, §P4.C3, §P4.C4 and §P4.C5 against the code and correct anything that no longer
   matches. §P4.C5 in particular should not claim the highlight allocates per frame: its corner arrays are
   static scratch space.
6. **§P4.3** (deferred items), add one: the Blocks table and the Sections cells are still written twice, in
   `TextReportWriter` and `Rows`, and could move into `core` the way Task 1a moved the ranked ones; only the
   Sections `avg ms/rb` header differs between the surfaces, by design.

- [ ] **Step 4: Build**

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 124 tests.

- [ ] **Step 5: Stage and report**

```bash
git add README.md docs/superpowers/specs
git status --short
```

Commit message for the orchestrator:

```
docs: README and spec for phase 4c
```

---

### Task 8: In-pack acceptance for 4b and 4c together (human step)

**Files:**
- Create: `docs/acceptance/phase-4b-4c.md`

The user chose on 2026-09-25 to run plan 4b's Task 7 and this task in one session, in the real Gears &
Frontiers instance, with the jar built from this branch's tip (`renderable-0.4.1.jar`; 4b bumped the version so
these reports cannot be mistaken for 4a's). The session also picks up **phase 4a's Check 7**, the look at the
results screen that was never run. The orchestrator writes one log from what comes back, has it reviewed, and
commits it; it replaces the `phase-4b.md` that 4b's plan names.

**Plan 4b's checks**, exactly as its Task 7 lists them: two back-to-back 30 s sessions at the base (the second
is the only coverage of the stale-accumulator check), the JSON's size and validity after a 30 s flight, the
self-time line in both sessions, the `reportFolder = "../escaped"` fallback, the overlay toggle surviving a
session, the greyed Highlight and Teleport after a disconnect and reconnect, and a clean `latest.log`.

**This plan's checks**, in the same client:

1. **Sorting (§P4.C7).** Open Block Entities: the rows are in the text report's order and `share` carries a
   white `v`. Click `verts/f`: rows reorder by vertices, largest first, and the `v` moves there. Click it
   again: `^` and the reverse order. Click `name`: A to Z, with `^`. Repeat one click each on Entities,
   Particles, Blocks, Sections and Flywheel. Clicking a header on Frames or Hitches does nothing. Say whether
   the arrow is legible at GUI scale 3.
2. **Empty states (§P4.C1).** With shaders on — which turned the Flywheel backend off in run 2 of the phase
   2/3 log — open the Flywheel tab and confirm it reads `(no data: no Flywheel visualization ran; backend
   flywheel:off)`, the same sentence the report prints. Paste both.
3. **Sections cap (§P4.C2).** After the 30 s flight from 4b's JSON check, open the Sections tab and paste its
   last row, which should read `... and N more sections (the report and JSON have all M)`.
4. **Teleport (§P4.C3).** Select a Sections row and press Teleport: you should arrive above ground at that
   section's column, not inside it.
5. **Highlight (§P4.C5).** Highlight a block entity and a section. Confirm a box in perspective rather than a
   flat rectangle, visible through walls; that standing inside the highlighted section still draws the edges
   in front of you; and that from far away it shrinks to a small square rather than vanishing.
6. **Labels (§P4.C4).** After a session with entities in view, go through a portal: no entity labels. Come
   back: they return.
7. **Header parity and narrow columns (§P4.C6).** The Sections tab's last header reads `avg ms/rb`; the
   Flywheel tab and the report's Flywheel table both read `SingleAxisRotatingVisual`. At GUI scale 3, where
   every column is wide, nothing overflows, so also switch the GUI scale to **Auto** (6 at 2560x1440, a 426-px
   GUI, the same as 1440p and 4K players get by default) and open Block Entities: four-digit decimals such as
   `1501.5` read rounded (`1502`), no number overlaps its neighbour, none is shown cut short, and anything that
   cannot fit even rounded shows as `#`s. Say whether the tab is still usable at that scale.
8. **The gc f note.** After a session with at least one collection, the Frames tab shows the `gc f` note
   under its `GC` row.
9. **The tooltip.** During 4b's reconnect check, hover over the greyed Highlight: it reads "The world this
   session was taken in is gone".

**Phase 4a's Check 7:** the ranked tabs show nine columns ending `peak ms | gc f | shadow | verts/f | calls`,
the Particles tab seven ending `verts/f | gc f`, and the `Renderable overhead` row names both counts. Say
whether all of it reads at GUI scale 3.

Then the orchestrator:

```bash
git add docs/acceptance/phase-4b-4c.md
git commit -m "docs: phase 4b and 4c acceptance log"
```

---

## Notes for the orchestrator

- Tasks 1, 1a, 2, 3 and 5 add JUnit tests. Tasks 4 and 6 are Minecraft-side with nothing to unit-test, and
  their by-eye checks are all in Task 8. No task launches a client.
- Every task ends with `./gradlew build` green. The first draft had Tasks 2 and 3 leave the tree
  uncompilable; the 2026-09-25 revision removed that.
- **Reviewer checklist per task:** `core` and `report` free of Minecraft imports; the empty-state sentences
  written once, in `EmptyStates`, and identical in the screen and the report; `Rows.build`'s five call
  paths all passing the environment; the ranked and particle names and cells coming only from `RankedTable`;
  no per-frame allocation added to `WorldOverlay.render` (`drawHighlight`'s corner arrays are static scratch
  space).
- If a reviewer proposes amending §5.3 to match the old cycling button rather than implementing header
  clicks, point at §P4.C7's rejected alternatives: the decision is the user's and it is recorded.
