# Phase 4b and 4c acceptance log

Date: 2026-09-26. Machine: Windows 11 Pro. GPU: `NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2`,
`glVersion: 4.6.0 NVIDIA 596.49`.

One in-pack session accepted plans 4b and 4c together, as the user chose on 2026-09-25, and also covers
phase 4a's Check 7 (the results screen) and the one in-pack glance plan 4d needs. It replaces the
`phase-4b.md` that plan 4b names.

Build under test: `renderable-0.4.1.jar`, built at 12:21 from branch `phase-4d-hygiene` at `e12bdef`, which
carries 4b, 4c and 4d, in the real Gears & Frontiers instance (`.MCUpdater/instances/Gears-And-Frontiers`,
connected to the user's server). Java 21.0.3 (Eclipse Adoptium), 8192 MiB heap, 386 mods, 2560x1440
fullscreen, GUI scale 3, fps limit 220, vsync off, render distance 10. The pack runs Sodium 0.6.13, Iris
1.8.12-snapshot, Flywheel 1.0.6, Distant Horizons 2.4.5-b and Accelerated Rendering 1.0.8-1.21.1-alpha.
Shaders were off in both sessions.

Two 30 s sessions:

| Session | Where | Frames | avg ms (fps) | p95 / p99 / max | Hitches | GC | Overhead | Self time |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `renderable-2026-09-26_12-32-42` | the base, 2571 106 1146 | 2872 | 10.4 (96) | 16.5 / 21.5 / 382.4 | 13 (3.3%) | 10 / 183 ms | 1442.92 ms over 7,617,555 timed and 49,268,584 vertex calls | 92.68 ms |
| `renderable-2026-09-26_12-43-30` | far from it, 3187 148 929, flying a gyrodyne into new terrain | 4874 | 6.2 (162) | 7.9 / 9.1 / 27.8 | 4 (0.3%) | 11 / 116 ms | 125.21 ms over 390,396 timed and 5,372,655 vertex calls | 67.17 ms |

Both sessions:

```
hooks applied: BLOCK_ENTITY_RENDER, ENTITY_RENDER, PARTICLE_RENDER, PARTICLE_TICK, BLOCK_ENTITY_TICK,
ENTITY_TICK, LIGHT, FLYWHEEL, SODIUM_MESH, SODIUM_UPLOAD, VERTICES, ACCELERATED_VERTICES
```

The user took four screenshots of the results screen and the overlay between the sessions (12:37 to 12:40,
in the instance's `screenshots` folder), and confirmed the screen checks below that the reports cannot show.

## Plan 4b (its Task 7)

**1. GC figures and the stale-accumulator check: PASS.**

- At the base: 10 collections, 183 ms paused. The phase 2/3 log recorded 15 / 369 ms there and phase 4a's
  run 13 / 230 ms. The scene is live, so the comparison is indicative, but it is lower on both counts, which
  is the direction 4b's allocation work was for.
- The block entity tables are populated and plausible: `create:factory_panel` leads at 546.2 us/f render.
- **Staleness.** The second session was not at the base, so no block entity carried over between the two.
  The player entity did: the same id, `#118098`, in both. Between the sessions the log holds four screenshot
  lines and an AllTheLeaks dump whose counters ("World join: 1", "Client Level Updates: 2", "Client Player
  Logout: 1") are cumulative: the same dump repeats unchanged ten minutes later, and the reconnect they
  reflect is at 12:31, before the first session. With no death, relog or dimension change between the
  sessions, it was the same object with its cached accumulator. In the first session it rendered nothing (first person), ticked 87.1 us/f and peaked at
  2.09 ms. In the second it was riding the gyrodyne, which vanilla ticks in its place, and the row reads
  render 90.7 us/f, tick **0.0**, peak **0.97 ms**, and 4,874 render calls, exactly that session's frame count.
  A stale accumulator would have carried the first session's 250 ms of tick time and its 2.09 ms peak into
  the second. The mechanism is unit-tested with synthetic sessions (`SessionTest`'s
  `anAccumulatorKnowsTheSessionIdThatMadeIt` and `sessionOwnsExactlyTheAccumulatorsItMade`); this is its first
  confirmation with a real object carrying a cached accumulator from one session into the next.

**2. JSON: PASS.** Both files parse as schema 4 (`{` then `"schema": 4,`). The base session's is
5,405,998 bytes with 481 sections and 225 blocks; the flight's is 5,123,261 bytes with 973 sections and
165 blocks, close to phase 3's 4.86 MB for a comparable flight, with every array still complete.

**3. Self time: PASS.** 92.68 ms and 67.17 ms, against other render time of 4.65 and 3.91 ms a frame
(13.4 s and 19.1 s over the sessions).

**4. Report folder fallback: NOT RUN.** The instance's config still reads `reportFolder = "renderable"`.

**5. Overlay toggle surviving a session: NOT RUN.**

**6. Highlight and Teleport greyed after a reconnect: NOT RUN.**

**7. Stability: PASS.** `unbalancedFrames` is 0 in both JSON files. `logs/latest.log` has no
`Attribution stack reset`, no `ClassCastException` and no crash. Its Renderable lines are routine mod
loading (the jar found, the mod listed, loaded, its config factory registered), the four chat lines of the
two sessions, and one `renderable-client.toml is not correct. Correcting` at startup: a one-time rewrite, consistent with 4b's new `reportFolder` comment, since NeoForge rewrites a
config file whose comments no longer match its spec.

## Plan 4c (its Task 8)

**1. Sorting: PASS.** Block Entities opens in the report's order with `share v` in white on its header
(screenshot 12:39:06), and expanding a type lists its members in the same order (12:39:15). The user
confirmed that clicking headers sorted as expected.

**2. Empty states with shaders on: NOT RUN.** Shaders were off in both sessions.

**3. Sections cap: PASS.** The user confirmed that after the flight the Sections tab ended with
`... and 773 more sections (the JSON has all 973)`, which matches the 973 sections in that session's JSON
and the 200-row cap. No screenshot shows it.

**4. Teleport: PASS**, as confirmed by the user.

**5. Highlight: PASS for the box.** Screenshot 12:40:26 shows a 16-block section highlighted as a wireframe
box in perspective, all twelve edges drawn over the world, among the section labels. The user confirmed it
behaved. Standing inside the section and the F3 frame-time comparison were not reported.

**6. Labels across a portal: NOT RUN.**

**7. Header parity and narrow columns: PARTLY RUN.** The report's Flywheel table prints simple class names
(`SingleAxisRotatingVisual`, `FunnelVisual`, `BeltVisual`, ...). At GUI scale 3 every number on the ranked
tabs fits its column (screenshots 12:39:06 and 12:39:15, with values up to `146,623` calls). The Auto GUI
scale check, the screen's Flywheel tab and the Sections tab's `avg ms/rb` header were not reported.

**8. The gc f note: PASS.** The Frames tab shows it as two lines under the GC row (screenshot 12:37:10). The
session ran at GUI scale 3; the note is split so that it also fits at the narrower Auto scale.

**9. The disabled-button tooltip: NOT RUN** (it needs 4b's reconnect check).

## Phase 4a's Check 7: the results screen

**PASS at GUI scale 3.** Block Entities shows nine columns ending `peak ms | gc f | shadow | verts/f | calls`
(screenshot 12:39:06), and the Frames tab's `Renderable overhead` row names both counts, timed and vertex
calls (12:37:10). The Particles tab was not captured; the text report's particle table has its seven
columns ending `verts/f | gc f`.

## Plan 4d

The `hooks applied` line lists the same twelve hooks as phase 4a's run, so moving `Hook` into `core` changed
nothing a report shows. The fix in `e12bdef`, "no rebuilds" instead of "Sodium hooks were not active", was
not exercised: both sessions rebuilt sections. 4d's own acceptance, watching CI on a runner, needs a GitHub
remote and is separate.

## Not run

4b checks 4, 5 and 6; 4c checks 2, 6 and 9, and parts of 5 (inside the box, frame time) and 7 (Auto GUI
scale, the Flywheel and Sections tabs on screen). Everything that was run passed. A follow-up session can
cover the rest; nothing above depends on them.

## Observations for later phases

- **A half-second client-tick stall at the base.** Hitch #1 is a 382.4 ms frame whose tick took 376.7 ms,
  of which block entity, entity and particle ticks account for 2.7 ms; #2, the next frame, is a 112.1 ms tick
  with 11.1 ms of them. The rest, about 374 ms and 101 ms, is client-tick work Renderable does not hook, such
  as packet handling or another mod's client tick; spark can attribute it.
- **The rider rule again.** In the gyrodyne, the player's own tick is counted under the aircraft
  (`immersive_aircraft:gyrodyne` 33.7 us/f tick, the player 0.0). Phase 4a found the same with this aircraft:
  the client ticks a passenger through its vehicle, so the rider's tick lands in the vehicle's row.
- **An empty Flywheel table.** The flight sampled Flywheel 30 times and found no visuals, so the text report
  prints the table's header and dashes with no rows. A sentence such as "no visuals were alive" would read
  better.
- **`render us/f` truncates to `render us/` at GUI scale 3.** The header is a few pixels wider than a 60 px
  number column; headers truncate like text by design, but a shorter name would avoid it.
