# Renderable phase 4: which plan to run, and what was decided

**Date:** 2026-09-20
**Spec addendum:** `docs/superpowers/specs/2026-09-20-renderable-phase-4-design.md`
**Baseline:** `main` at `1ec32c3`, tag `v0.3.0`, 75 JUnit tests.

## Order

Run **4a first**. It is the only plan that changes numbers a reader acts on, it ships on its own as
`v0.4.0`, and it is the only one that changes the JSON shape (`schema` 3 to 4). 4b, 4c and 4d each assume
4a is on `main` and are independent of each other.

| Plan | File | Ships | Tests at the end |
| --- | --- | --- | --- |
| 4a numbers | `2026-09-20-renderable-phase-4a-numbers.md` | `v0.4.0`, tagged | 86 |
| 4b robustness | `2026-09-20-renderable-phase-4b-robustness.md` | no tag of its own | 97 |
| 4c screen | `2026-09-20-renderable-phase-4c-screen.md` | no tag of its own | 95 |
| 4d hygiene and CI | `2026-09-20-renderable-phase-4d-hygiene.md` | no tag of its own | 90 |

Each of 4b, 4c and 4d states its test total on the assumption that **only 4a** has run before it. Running
two of them means adding the second's additions to the first's total; each plan says so in its file map.

A sensible full sequence, if all four are run: **4a → 4b → 4c → 4d**, then one version bump and tag for
whatever ships together. 4d last is convenient rather than required: its hook-name guard test is the thing
most likely to catch a literal a later plan introduced, and running it last means it catches everything.

## The three places two plans touch the same thing

These are written so either order works, but they are worth knowing about before reviewing a diff.

1. **`Environment.ABSENT`, `has(String)` and `value(String)`.** Introduced by **4b** (Task 5) and by **4c**
   (Task 3), each with a "skip if present" instruction and the identical snippet. **4d** (Task 3) then
   replaces whatever literals are left, and greps first rather than assuming. Whichever runs first wins; the
   others are no-ops.
2. **`core/EmptyStates`.** Created by **4c**. It spells two hook names as string literals, which **4d**'s
   guard test forbids, so 4d Task 1 has an explicit "if plan 4c created it" branch that fixes them. If 4c
   has not run, 4d fixes the same two literals in `TextReportWriter` instead.
3. **`ResultsScreen.updateButtons`.** **4b** adds a stale-world check to it and **4c** removes the Sort
   button from it. Both plans quote the whole method and say which line belongs to the other plan.

## Decisions the user made

Asked before any of this was written; each is recorded with its rejected alternatives in the spec addendum.

| Decision | Choice | Where |
| --- | --- | --- |
| GC pauses credited to whichever key was open | **Flag, do not subtract.** Mark contributors whose cost is at least the frame's pause as `[gc]` in text, JSON and on screen, and keep GC-suspect frames out of per-key and per-type peaks, counting them in a new `gc f` column. Subtraction was rejected because the cumulative counters do not carry the pause's position and a notification listener would be asynchronous, millisecond-resolution and untestable. | §P4.A1, plan 4a |
| Mob vertices reading zero under Accelerated Rendering | **Hook it,** with a string-targeted mixin in a gated sub-package at `require = 0`. Reporting `n/a` instead was rejected because vertex counts are the spec's only GPU-load proxy; a config flag was rejected as a second rarely-exercised path for no safety gain. | §P4.A3, plan 4a |
| JSON completeness versus a cap | **Stream it and keep every array complete.** No caps, no `sectionsTruncated`, no schema change for this item. The memory problem was the Gson tree, not the file size. | §P4.B1, plan 4b |
| `Probe.BY_INSTANCE` keeping rendered objects alive | **Cache the accumulator on the object** through a `@Unique` field and a duck-typed interface. Eviction-on-removal and weak keys were both rejected; clearing on level unload does not address the within-session window the acceptance log ran into. | §P4.B2, plan 4b |
| Spec §5.3's per-column sorting versus the cycling Sort button | **Implement the promise.** Click a column header to sort by it; click again to reverse; the Sort button goes away; Frames and Hitches keep a fixed order and §5.3 is amended to say exactly that. | §P4.C7, plan 4c |

## Items moved between groups

The punch list's four groups were written before the code was read. Three items ended up in a different
plan, always because the plan that owns the file is the plan that should change it.

- **D2 (`ReportFiles` imports `Minecraft`, contradicting §8)** and **D1's extended no-Minecraft-imports
  test** moved from 4d into **4b**. 4b already rewrites `ReportFiles.write`'s signature for streaming, and
  splitting one method's rework across two independently-ordered plans would make whichever ran second
  conflict. 4b also picks up **B6 #3**, the `reportFolder` path-traversal check, which only becomes
  unit-testable once the class is Minecraft-free.
- **D1's `TabTest`** moved from 4d into **4c**, which is the plan that changes `Tab`'s shape. A test written
  against the old shape would have to be rewritten immediately.
- **C6 #3 (`PEAK` in the Particles sort cycle)** is not a task at all: 4c's decision to replace the cycling
  button with per-column header clicks removes the cycle, and a tab can then only sort by a column it
  actually shows.

## Items dropped or deferred, and why

Nothing on the punch list was dropped silently. These are the ones that do not become code in phase 4:

- **Subtracting GC pauses.** The user chose flagging. If the flag turns out not to be enough in practice,
  the rejected design — a `GarbageCollectionNotificationInfo` listener writing pause windows into a
  lock-free ring buffer, subtracted at `finish()` — is written up in §P4.A1 and is a phase 5 item.
- **A cap on the JSON `sections` array.** The user chose completeness. §6.2's promise is unchanged.
- **Capping the Blocks tab** alongside the Sections tab (C2). Blocks is bounded by the number of block types
  in the pack, which is hundreds, not thousands. Only Sections is capped.
- **Adding the dimension to `BlockEntityKey.label()`** (D5). A session cannot span two dimensions —
  `FrameEvents.onLevelUnload` discards a running session when the client level goes away — and the
  environment header names the dimension, so repeating it on every row would widen the tables for no
  information. 4d pins the rule with a test and a Javadoc instead of changing it.
- **Measuring the in-world overlay in the mod's self time** (B3). `ProfilerController.start` clears the
  overlay, so a running session has no labels to draw. The only overlay work that can overlap a session is a
  ten-second highlight of twelve projected edges, which the README records rather than measures.
- **Attributing the rest of `other render`.** Still 5 to 6 ms per frame at the base, and still the largest
  unexplained bucket. Distant Horizons, Sodium Extra and the pack's other renderers are a phase 5 question.
- **Per-visual Flywheel timing** and **GPU timer queries.** Base spec §4.4 and §2 are unchanged; nothing
  learned in phases 2 and 3 changes either conclusion.

## Two things the plans cannot fully prove on this machine

Both are written into the plans with explicit fallbacks so a subagent cannot get stuck:

1. **The Accelerated Rendering mixin** (4a, Task 6). The mod is an alpha that is not published on the
   Modrinth Maven under any resolvable slug, so the dev client can only load it from the pack instance's
   jar, via a new `-PextraMods` property. If the dev client refuses to start with it, the plan says to
   record the failure, drop the property, and leave the proof to the in-pack run in Task 9. The gate and
   `require = 0` mean a mistake there degrades the `verts/f` column rather than crashing.
2. **CI** (4d, Task 5). The workflows have never run on a runner, so nothing local can accept them. 4d's
   Task 7 is the acceptance: open a pull request, watch the run, push a deliberately mismatched tag to see
   the version gate refuse it, then delete that tag.

## What the user runs, in total

Four human steps, one per plan, each listing exactly what to paste back:

- **4a Task 9**, in the pack: GC flagging, peaks, type peaks, Accelerated Rendering vertices, the overhead
  line, the screen, stability — **plus** the controlled idle-cost measurement (base spec §9 check 5), which
  has been outstanding since phase 1. That one is four Java Flight Recorder runs in a static superflat
  scene, with a `-XX:-DoEscapeAnalysis` control that has to separate for the result to count as a pass.
- **4b Task 7**, in the pack: GC figures against phase 3's baseline, JSON size and validity, self time,
  report-folder validation, the overlay toggle, the stale-world guard.
- **4c Task 8**, in the pack: sorting on all six data tabs, empty states with shaders on, the Sections cap,
  teleport, the wireframe highlight, dimension-scoped labels, header parity.
- **4d Task 7**, on GitHub: a pull request build, a cache hit on the second run, a cancelled run, a failing
  version gate and a passing one.
