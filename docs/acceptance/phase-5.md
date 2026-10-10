# Phase 5 in-pack acceptance

Date: 2026-10-10. Machine: Windows 11 Pro. GPU: `NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2`,
`glVersion: 4.6.0 NVIDIA 596.49`.

Build under test: `renderable-0.5.0.jar`, built from branch `phase-5c-readiness` at `6a2fcef`, which carries
5a, 5b, 5d and 5c, in the real Gears & Frontiers instance (`.MCUpdater/instances/Gears-And-Frontiers`,
connected to the user's server). Java 21.0.3 (Eclipse Adoptium), 8192 MiB heap, 386 mods, 2560x1440
fullscreen, GUI scale 3, fps limit 220, vsync off, render distance 10. The pack runs Sodium 0.6.13, Iris
1.8.12-snapshot, Flywheel 1.0.6, Distant Horizons 2.4.5-b and Accelerated Rendering 1.0.8-1.21.1-alpha.
Shaders were off.

**Scope.** The plan lists 24 checks over seven sessions. On 2026-10-10 the user asked whether all of them were
needed, and chose a short pass: one session at the base, the screen checks it allows, and the dimension and
reconnect checks. The others are recorded NOT RUN below. Most of them confirm behaviour the unit tests cover
(report folder, overlay toggle) or that waits for the Observable-style overlay rework (label overlap, header
truncation at Auto GUI scale). The orchestrator read the report and `logs/latest.log` from the instance; the
user confirmed the screen checks.

One 30 s session:

| Session | Where | Frames | avg ms (fps) | p95 / p99 / max | Hitches | GC | Overhead | Self time |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `renderable-2026-10-10_15-56-32` | the base, 2562 97 1152 | 2783 | 10.8 (93) | 18.4 / 24.2 / 107.2 | 18 (2.2%) | 10 / 136 ms | 664.11 ms over 10,789,174 timed and 49,213,733 vertex calls | 93.46 ms |

```
hooks installed: BLOCK_ENTITY_RENDER, ENTITY_RENDER, PARTICLE_RENDER, PARTICLE_TICK, BLOCK_ENTITY_TICK,
ENTITY_TICK, LIGHT, FLYWHEEL, SODIUM_MESH, SODIUM_UPLOAD, VERTICES, ACCELERATED_VERTICES, TASKS, FRAME_CAP,
HUD, HUD_LAYERS, PACKETS, PAYLOADS, RIDER_TICK
hooks fired: the same, plus EVENTS
hooks that did not apply: none
```

## Before starting

**1. Keys (C4): NOT RUN.** The instance's `options.txt` still binds `key_key.renderable.toggle` to F7 from
0.4.1, so the new F6 default was not seen. A fresh install gets F6.

## Session 1: at the base

**2. The chat line (B5): PASS.** `Renderable: 2783 frames in 30.0 s, avg 10.8 ms, 18 hitches, 2 suggestions.`
followed by its links. The Advice tab showed the same two.

**3. The Advice tab (B5): PASS.** Two suggestions, both sound for this base:
- minecolonies's network payloads (up to 61 ms): `minecolonies:colony_view_building_view` took 61.07 ms in
  hitch #1, which was 107.2 ms with 89.2 ms in tasks.
- xaeroworldmap's event handlers (0.7 ms/frame), most of it in `RenderFrameEvent.Post`.

The user confirmed the lines wrap inside the list.

**4. The Mods tab (A2, A3, A4, A5, A7): PASS, with A5 not exercised.**
- Handlers are named and timed per mod, with `RenderFrameEvent` and `ClientTickEvent` among them
  (xaeroworldmap, creativecore, create, ponder and others). `neoforge` is not in the report's top 15, which
  is the cap, not a gap.
- Vanilla packets sit under `minecraft`.
- Create appears by its handlers (`ClientTickEvent.Post`, `RenderLevelStageEvent`, `EntityTickEvent.Pre`),
  but none of its payloads made its top 3, so A5's Create payload check did not really run.

**5. Proxies after the session (A3): PASS.** The log at session end:
`Renderable event timing restored 228 listener lists; 0 still hold a timing proxy`. That is the count
`/renderable debug events` reports, so the command was not run.

**6. The Frames split and hook lists (D1): PASS.** The split shows `hud 0.38`, `tasks 0.16 [mods 0.14]` and
`frame cap 0.00`, with `[mods x]` on tick, other render and tasks. The hook lists are above: none failed, and
every hook that fired is installed except EVENTS, which the install check does not cover.

**7. Block Entities (D4, D9, D2): NOT RUN** as a separate check. The report has no `shadow` column with shaders
off.

**8. Particles (D4): PASS.** The report names classes by short name (`CampfireSmokeParticle`,
`DripParticle$CoolingDripHangParticle`).

**9. Sections sort (D5): NOT RUN.**

**10. The screen's place (D6): NOT RUN.**

**11. Overlapping labels (D8): NOT RUN.** Deferred to the Observable-style overlay rework.

**12. The calibration line (C2): PASS.**
`a timed pair 53 ns (Session alone 51 ns), a vertex 2 ns (Session alone 1 ns), an attribution-only pair 51 ns`.
The pair is within a few ns of `Session` alone, and the vertex is at least `Session` alone's and 1 ns more.

## Sessions 2 to 5

**13. New terrain (A1, A5): NOT RUN.**

**14. Vehicles (A8): NOT RUN.** The `RIDER_TICK` hook fired at the base.

**15. Frame cap (A1): NOT RUN.** At an fps limit of 220 the cap was 0.00 ms, as expected when it never waits.

**16. HUD hidden (A1): NOT RUN.**

**17. Shaders on (4c check 2, D2): NOT RUN.**

## Session 6: another dimension

**18 and 19. Labels and rows from another dimension (4c check 6, D7): PASS.** Through a portal, Highlight and
Teleport were greyed on the first dimension's rows, with the tooltip naming both dimensions.

## Session 7: reconnect

**20. After logging out (4b check 6, 4c check 9): NOT RUN.** The user answered "probably" without a clear
sighting; it stays open.

## Config and screen checks

**21. The report folder (4b check 4): NOT RUN.** Covered by `ReportFiles`' unit tests.

**22. The overlay toggle (4b check 5): NOT RUN.**

**23. Leftovers from 4c: NOT RUN.** Header truncation at Auto GUI scale waits for the overlay rework.

**24. Another player (C3): NOT RUN** for another player. The user's own row reads
`minecraft:player #173009 (you)`. The report and the JSON hold no UUID (searched for the 8-4-4-4-12 form), no
username and no file path. The user may run it on the server later.

## Observations

- **The overhead estimate** is 664 ms over 30 s, about 2.2% of the session, against 1443 ms at the same base in
  0.4.1 with fewer timed calls. Calibration now times warm code, so the estimate is lower, as expected.
- **Mesh rebuilds at the base.** Sections `161 6 72` and `161 6 71` rebuilt about 8 times a second at 11 to 12 ms
  each, near the player. Something in those sections changes all the time; a lead for the pack, not a defect.
- **Outside testing.** The user gave the 0.5.0 jar to another player to try.

## After the tag: another player's base

On 2026-10-10, after `v0.5.0` was tagged, the user ran two more 30 s sessions with the same build at another
player's base. Both show `hooks that did not apply: none`, and the log says `restored 228 listener lists; 0
still hold a timing proxy` after each. Neither report holds a UUID. No other player was in view, so check 24
stays NOT RUN.

| Session | Where | Frames | avg ms (fps) | p50 / p95 / max | Hitches | GC | Overhead | Advice |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `renderable-2026-10-10_16-08-48` | -1169 91 -1678 | 3992 | 7.5 (133) | 5.3 / 18.5 / 35.4 | 83 (6.6%) | 14 / 84 ms | 1067.26 ms over 19,302,395 timed and 26,209,913 vertex calls | none |
| `renderable-2026-10-10_16-10-48` | -1127 138 -1684 | 2969 | 10.1 (99) | 8.1 / 18.9 / 40.0 | 17 (1.6%) | 11 / 68 ms | 969.82 ms over 17,174,795 timed and 32,848,883 vertex calls | none |

What the reports show at that base:
- Create: Enchantable Machinery's mixers and presses lead block entity render time (the mixers 0.33 ms a frame
  and about 4,200 vertices).
- Create fluid tanks lead block entity tick time and appear in several hitches; one at -1135 142 -1695 took
  2.13 ms in a frame.
- `ae2:cable_bus` makes 2.7 million render calls in 30 s but costs little.
- Protection Pixel (`RenderLevelStageEvent`, 0.2 ms a frame) and Xaero's World Map (`RenderFrameEvent.Post`,
  0.19 ms a frame) are the steady handler costs.

**A gap for the next phase.** The first session's worst hitches are mostly tick time Renderable cannot name.
Hitch #3 has 25.6 ms of tick, and its named parts (particle tick, block entity tick, entity tick, mods) add to
about 5.6 ms; hitches #6 and #7 look the same. The split has no `other tick` part in hitch lines, so the reader
has to subtract, and no row names what is left. The Advice tab offered nothing although hitches took 6.6% of
the session. Neither is a phase 5 defect: no plan promised either. The next phase should show unattributed
tick time in hitch lines, find a way to attribute it, and recommend when hitches are frequent with no named
cause.
