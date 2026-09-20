# Phase 4a acceptance log

Date: 2026-09-20. Machine: Windows 11 Pro. GPU: `NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2`,
`glVersion: 4.6.0 NVIDIA 596.49`.

Build under test: `renderable-0.4.0.jar` from branch `phase-4a-numbers` at `7d42d51`, in the real
Gears & Frontiers instance (`.MCUpdater/instances/Gears-And-Frontiers`, connected to the user's
server). Java 21.0.3 (Eclipse Adoptium), 8192 MiB heap, 386 mods, 2560x1440 fullscreen, GUI scale 3,
fps limit 220, vsync off, render distance 10.

The pack runs Sodium 0.6.13, Iris 1.8.12-snapshot, Flywheel 1.0.6, Distant Horizons 2.4.5-b and
**Accelerated Rendering 1.0.8-1.21.1-alpha**. Shaders were off for this run, so `IRIS_SHADOW` is
absent from `hooks applied` and every `shadow` column reads `0.0%`; that is the expected shape, not a
regression.

One 30 s session, `renderable-2026-09-20_15-23-32`, at the base (2577 97 1151):

| Frames | avg ms (fps) | p95 / p99 / max | Hitches | GC | Overhead |
| --- | --- | --- | --- | --- | --- |
| 2381 | 12.6 (79) | 21.4 / 30.5 / 76.3 | 39 (4.8%) | 13 / 230 ms | 1572.61 ms over 6,711,724 timed calls and 39,833,584 vertex calls |

```
hooks applied: BLOCK_ENTITY_RENDER, ENTITY_RENDER, PARTICLE_RENDER, PARTICLE_TICK,
BLOCK_ENTITY_TICK, ENTITY_TICK, LIGHT, FLYWHEEL, SODIUM_MESH, SODIUM_UPLOAD, VERTICES,
ACCELERATED_VERTICES
```

## Check 1: GC flagging (spec addendum §P4.A1)

**Result: PASS**, and this is the check the phase exists for.

Hitch #1 is the case the phase 2/3 log called "the most misleading number in the report", now
corrected:

```
  #1  frame 598 @ 9.0s  76.3 ms  |  tick 64.5 [particle tick 0.6, be tick 59.7, entity tick 1.0] | block entity 3.0 | ... |  gc 57 ms
      57.20 ms  create:portable_storage_interface @ 2549 87 1136  [gc]
      0.41 ms  minecraft:player #159525 (edwardg)
      0.15 ms  CampfireSmokeParticle
```

At `v0.3.0` that line read as a 57 ms block entity. It is now marked, and the four cheap
contributors beneath it on the same paused frame are not.

Hitch #2 is the control — a genuine 58.3 ms hitch with `gc 0 ms` and 38.3 ms of `other render`,
whose contributors are correctly unmarked:

```
  #2  frame 759 @ 11.0s  58.3 ms  |  ... | other render 38.3 | swap 4.9  |  gc 0 ms
      0.52 ms  minecraft:player #159525 (edwardg)
```

**The rule was verified exhaustively, not by sampling.** Every top-contributor entry in the JSON was
checked against its own frame's pause: **11,905 entries, 4 marked, 0 rule violations.** No entry was
marked that should not have been, and none was missed. Ten frames carried a pause, of 13, 14, 15,
17, 19, 21, 25, 28 and 57 ms.

Both explanatory notes printed: the `[gc]` legend under the `Hitches` heading, and the column note
beside the `GC:` line — the latter mattering because a session with small pauses and no hitch would
show `gc f` with the first note absent.

## Check 2: peaks and the `gc f` column (§P4.A1)

**Result: PASS.**

```
  name                                              render us/f  tick us/f  share  peak ms  gc f  shadow  verts/f    calls
  create:basin                                            576.7        5.9   4.6%     3.76     1    0.0%  10780.9   74,287
  createenchantablemachinery:enchantable_millstone        561.1        1.0   4.5%     1.70     1    0.0%   1501.5   31,920
  create:factory_panel                                    454.1       13.5   3.7%    15.78     0    0.0%   6548.5  125,539
```

The clearest single illustration is the millstone at `2571 99 1148`. It appears in a hitch at
**19.39 ms, marked `[gc]`**, while its row in `Block entities by position` reports a clean
`peak ms` of **0.16** with `gc f 1`. Before this branch, 19.39 ms would have been printed as that
renderer's peak.

`create:factory_panel` is the counter-example that shows the rule is not simply discarding large
values: a 15.78 ms peak with `gc f 0`, retained because its frame had no pause.

## Check 3: type peaks (§P4.A2), and the open question about them

**Result: PASS**, with a finding recorded below.

Type rows now carry the type's worst frame. The millstone type's 1.70 ms peak is larger than any of
its sixteen members' peaks (0.14 to 0.38 ms), which is the many-cheap-members case §P4.A2 exists to
fix.

**The divergence the whole-branch review predicted did not occur here.** §P4.A0 records that the
GC-suspect rule's physical justification is about a single call while type rows apply it to a sum of
members, so a type could in principle show a non-zero `gc f` above members that all read zero. Every
type in the JSON was checked:

| type | type `gcFrames` | members | max member `gcFrames` |
| --- | --- | --- | --- |
| `create:basin` | 1 | 39 | 1 |
| `createenchantablemachinery:enchantable_millstone` | 1 | 16 | 1 |
| `create:item_drain` | 1 | 117 | 1 |
| `create:portable_storage_interface` | 1 | 16 | 1 |

In all four, a member was suspect too. The reason is visible in the data: every pause this session
was 13 ms or larger, and a sum only crosses a 57 ms threshold if one member was itself hugely
inflated. The pathological case needs **small** pauses and many cheap members, which this run did not
produce — so the concern is unfalsified rather than disproved, and should be re-checked on a session
whose pauses are in the 2 to 5 ms range.

## Check 4: Accelerated Rendering vertices (§P4.A3)

**Result: PASS.** This closes the item phase 3's log left open.

The environment block now names the mod, and the hook fired:

```
  acceleratedrendering: 1.0.8-1.21.1-alpha
  hooks applied: ..., VERTICES, ACCELERATED_VERTICES
```

Mob models are counted for the first time. Against the phase 2/3 log, where "pig, horse, cow, sheep,
zombie" all read `0.0`:

| entity type | verts/f at v0.4.0 | verts/f at v0.3.0 |
| --- | --- | --- |
| `minecraft:chicken` | 655.3 | 5.1 |
| `minecraft:blaze` | 356.4 | 2.3 |
| `minecraft:pig` | 218.2 | 0.0 |
| `minecraft:horse` | 197.1 | 0.0 |

Particles are counted too (`CampfireSmokeParticle` 364.4 verts/f), as are Create's machines
(`create:basin` 10780.9).

`minecraft:player`, `minecraft:squid` and `minecraft:glow_squid` read `0.0` with **0 render calls** —
they were not rendered at all this session (first person, shaders off), which is a different thing
from being rendered and uncounted.

## Check 5: the overhead line (§P4.A5)

**Result: PASS**, with a cost worth knowing.

```
  Renderable overhead: about 1572.61 ms over 6,711,724 timed calls and 39,833,584 vertex calls
```

That is **5.2% of wall time**, against 1.9% (`555.76 ms over 6,947,008 calls`) at `v0.3.0` on the
same base. The rise is the bug being fixed, not a regression: vertex counting was always costing
this and was never in the estimate, and the calibration constant itself was understated because
`measureCallNanos` never opened a frame.

Roughly 0.54 s of the total is the timed calls, near what `v0.3.0` already spent; the remaining
~1.0 s is the 39.8 million vertex calls. Part of that is newly *incurred* rather than newly
*reported*, because the Accelerated Rendering hook now counts mob-model vertices that nothing
counted before — but the report cannot say how much, because `vertexCalls` does not distinguish the
vanilla, Sodium and Accelerated Rendering paths.

**Decision (user, 2026-09-20): leave it.** The figure is measured and stated, and the README already
tells the reader to discount it. A `countVertices` config flag for a low-overhead mode, and splitting
`vertexCalls` by source, were both considered and declined for now. Worth revisiting if a future
session is spent chasing a regression of the same order as the profiler's own cost.

## Check 6: stability

**Result: PASS.** No `Attribution stack reset` line. No crash during the session, at its end, or
afterwards. The client ran with Accelerated Rendering, Sodium, Iris, Flywheel and Distant Horizons
all loaded.

## Check 7: the results screen

**Result: NOT RUN.** Nobody has looked at the screen for this build. Outstanding items, from the
plan's Task 9: the ranked tabs showing nine columns ending `peak ms | gc f | shadow | verts/f |
calls`, the Particles tab showing seven ending `verts/f | gc f`, the `Renderable overhead` row naming
both counts, and whether right-aligned numbers collide in a non-maximised window — that last one is
known-deferred to plan 4c (§P4.C6 #4) and should be logged rather than treated as new.

## Check 8: idle cost (base spec §9 check 5)

**Result: NOT RUN.** See `docs/acceptance/phase-4-idle.md` when the four Java Flight Recorder runs
have been done. This is the check that has been outstanding since phase 1, and `v0.4.0` should not be
tagged until it has a verdict, because the spec's §11 row and §9 both now claim it was measured.

## Observations for later phases

- **Accelerated Rendering 1.0.8 does not run against Iris 1.8.14-beta.1**, which the build pins for
  the pack update in progress. Its own `compat.iris` mixin fails a constant-modifier injection at
  `RenderSystem.initRenderer` and the client dies before a world loads. Found while verifying this
  hook in the dev client; recorded in §P4.A3. It constrains the pack update, not this mod — but a
  tester who hits it will reasonably blame Renderable.
- **`other render` is still the largest unattributed bucket** at 5.70 ms per frame, and hitch #2 was
  38.3 ms of it with no GC. Unchanged from phase 3, and still a phase 5 question.
- **The base still rebuilds sections while standing still**: `chunk upload 0.06` ms/frame and a
  non-empty Sections table on a stationary 30 s session.
- **The player is by far the most expensive entity to tick in this pack, and it is not a phase 4a
  artefact.** The table reports 125.5 µs per *frame*, which is 600 ticks at **498.1 µs each** — 17
  times the next worst entity (`minecraft:minecart`, 28.9 µs) and 70 times a zombie. One entity is
  48.5% of all client entity-tick time; entity tick is 615 ms of a 2,992 ms tick budget, so the
  player alone is roughly 10% of the client's tick time and 1% of wall time. It is long-standing,
  not new: the three base sessions of 2026-09-20 measured 601.5, 449.6 and 498.1 µs per tick, and
  turning shaders on *lowered* it, which is the right direction for tick work and rules out render
  time leaking in.

  Renderable cannot say what inside that tick costs the time: `guardEntityTick` wraps the whole call,
  so the figure is inclusive of everything 386 mods attach to a player tick, and there is no call
  site to wrap inside another mod's listener. A sampling profiler such as spark, pointed at the
  `Player.tick` subtree, is the tool for that question.

- **A riding entity's tick is attributed to its vehicle** — found while investigating the above, and
  now recorded in base spec §4.2 and in the phase 4 addendum's deferred list. In the 08-56 flight
  session the player was rendered 4,983 times and ticked **zero** times, because
  `ClientLevel.tickEntities` routes only non-passengers through `guardEntityTick`; a passenger is
  ticked by its vehicle via `Entity.rideTick()`, which the hook never sees. The cost is not lost —
  `immersive_aircraft:gyrodyne` reported 600 ticks at 309.7 µs each, a figure that silently contains
  the player's tick — but it is misattributed, and nothing in the report says so. Given the point
  above, the entity table is least trustworthy exactly when someone is moving around on a vehicle.
  Closing it needs a hook on `Entity.rideTick()`, which is phase 5 scope.
