# Phase 6 in-pack acceptance

Date: 2026-10-11. Machine: Windows 11 Pro. GPU: `NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2`,
`glVersion: 4.6.0 NVIDIA 596.49`.

Build under test: `renderable-0.6.0.jar` from branch `phase-6b-advice`, which carries 6a and 6b, in the real
Gears & Frontiers instance (`.MCUpdater/instances/Gears-And-Frontiers`, connected to the user's server). The three
planned sessions ran on the jar built at `05c31d4`, which includes the overlay fix below; the follow-up sessions at
the spawn power station ran on the builds named with them. Java 21.0.3 (Eclipse Adoptium), 8192 MiB heap, 387 mods,
2560x1440 fullscreen, GUI scale 3, fps limit 220, vsync off, render distance 10. The pack runs Sodium 0.6.13, Iris
1.8.12-snapshot, Flywheel 1.0.6 (indirect backend), Distant Horizons 2.4.5-b and Accelerated Rendering
1.0.8-1.21.1-alpha. Shaders were off. Renderable's key is F7 in this instance, kept from 0.4.1.

**Scope.** The short pass the plan (6b Task 7) sets out, as the user prefers: a session at another player's base
from the spot of the phase 5 session there, an optional muted session at the same spot, and a session at the user's
base, plus the screen and overlay checks. The orchestrator read the reports and `logs/latest.log` from the instance;
the user answered the screen checks with screenshots. The checks found three display problems and confirmed one
overlay bug; all four were fixed before tagging and re-checked in the pack (see **Fixes made during the acceptance**).

| Session | Where | Frames | avg ms (fps) | p50 / p95 / max | Hitches | GC | Overhead | Advice |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 `renderable-2026-10-11_11-30-32` | other player's base, -1141 141 -1677 | 3415 | 8.8 (114) | 7.2 / 17.9 / 48.8 | 17 (1.7%) | 11 / 93 ms | 812.89 ms over 15,779,149 timed and 4,077,342 vertex calls | `sound-wait`, `distant-horizons` |
| 2 `renderable-2026-10-11_11-33-58` | the same spot, Blocks at 0% | 3666 | 8.2 (122) | 7.4 / 13.9 / 25.2 | 2 (0.2%) | 10 / 64 ms | 890.09 ms over 17,259,294 timed and 4,934,471 vertex calls | `distant-horizons` |
| 3 `renderable-2026-10-11_11-37-59` | the user's base, 2568 102 1136 | 2368 | 12.7 (79) | 11.9 / 20.4 / 97.0 | 23 (2.8%) | 14 / 134 ms | 561.48 ms over 9,607,400 timed and 35,750,179 vertex calls | `distant-horizons`, `mod-handlers` |

```
hooks installed: BLOCK_ENTITY_RENDER, ENTITY_RENDER, PARTICLE_RENDER, PARTICLE_TICK, BLOCK_ENTITY_TICK,
ENTITY_TICK, LIGHT, FLYWHEEL, SODIUM_MESH, SODIUM_UPLOAD, VERTICES, ACCELERATED_VERTICES, TASKS, FRAME_CAP,
HUD, HUD_LAYERS, PACKETS, PAYLOADS, RIDER_TICK, SOUND, ANIMATE_TICK, TERRAIN, LOD, SOUND_PLAYS
hooks fired: the same, plus EVENTS
hooks that did not apply: none
```

All three sessions read the same hook lists.

## Before the sessions: the overlay switch (a 0.5.0 bug)

On 2026-10-11 the user reported that in 0.5.0, turning the overlay off in the mod's config screen left the floating
labels on. The cause: `WorldOverlay` copied `overlayEnabled` the first time it drew labels and never read it again
(spec addendum §P4.B6 item 4), so a change made while the game ran only took effect after a restart; the instance's
`renderable-client.toml` did hold `overlayEnabled = false`. The user chose one switch: the setting is the only state,
read every frame, and `/renderable overlay` and the overlay key write it (§P6.13, `05c31d4`).

## Session 1: the other player's base

**1. Hooks (A1): PASS.** As above: every new hook installed and fired, none failed, and `distanthorizons` reads
`2.4.5-b`, the version the pack deploys.

**2. Named (§P6.10, 1): PASS.** The split names what 0.5.0 left as other tick and other render:
`sound 0.53`, `animate tick 0.05`, `terrain 0.07` and `lod 3.34` ms a frame, of an 8.8 ms frame.

**3. Unnamed (§P6.10, 1): PASS.** In all 17 hitches, `unnamed` is under a quarter of the tick; the session's
average is 0.22 ms a frame. At this spot in 0.5.0, hitch #3 had about 20 ms of tick that nothing named.

**4. Sound rows (A3): PASS.** 39 sound rows, each under its event's namespace. The leading events, over 30 s:

| Row | Plays | Time |
| --- | --- | --- |
| `sound create:mixing (block)` | 4102 | 568 ms |
| `sound create:mixing_compounded_1 (block)` | 4102 | 568 ms |
| `sound create:funnel_flap (block)` | 2563 | 318 ms |
| `sound create:funnel_flap_compounded_1 (block)` | 2563 | 314 ms |
| `sound create:steam (neutral)` | 926 | 259 ms |
| `sound minecraft:block.candle.extinguish (block)` | 964 | 203 ms |

Create's boiler sound, `create:steam`, is under `create`. It plays in the `neutral` category, the Friendly Creatures
slider. (These labels are as the build at `05c31d4` printed them; since `879c7e8` they name the slider, see below.)

**5. Advice (B1): PASS.** `distant-horizons` fired: "Distant Horizons draws its far terrain in 3.3 ms of each 8.8 ms
frame." `sound-wait` fired in its stutter form, by the bucket test: "In 5 of 17 hitches, the sound engine took at
least a quarter of the frame (up to 16 ms). Playing sounds kept the game waiting 0.7 ms a frame; create:mixing (Blocks)
played 137 times a second." Its action named the Blocks slider, the costliest row's category, with the pack-author
sentence. The frame-rate form's test also passed (the Sound plays line read 0.67 ms a frame); the stutter form
outranks it, as decided. `unattributed-stall` stayed quiet: on the 0.5.0 numbers here its repeat form would have
fired on client tick in 64 of 83 hitches, time `sound` now names.

## Session 2: the same spot, muted

**6. The mute check (§P6.3, A1's third bullet): PASS.** With Blocks at 0%:

- the muted rows read about nothing while their plays still count: `create:mixing` 4107 plays in 3.2 ms (568 ms in
  session 1), `create:funnel_flap` 2473 plays in 1.9 ms;
- the `sound` bucket fell from 0.53 to 0.01 ms a frame, and the Sound plays line from 0.67 to 0.06 ms a frame;
- `create:steam`, in Friendly Creatures, was not muted and still cost 194 ms;
- hitches fell from 17 to 2, and the average frame from 8.8 to 8.2 ms.

That is `sound-wait`'s action working in the game: at 0% `SoundEngine.play` returns before it waits for a channel.
The user put the slider back before session 3.

## Session 3: the user's base

**7. Nothing named before reads differently (§P6.10, 2): PASS.** Against the 0.5.0 session at the same base
(`renderable-2026-10-10_15-56-32`, at 2562 97 1152), a day apart:

| | 0.5.0 | 0.6.0 |
| --- | --- | --- |
| avg frame | 10.78 ms | 12.67 ms |
| block entity / entity / flywheel / hud | 3.62 / 0.07 / 0.33 / 0.38 | 3.16 / 0.07 / 0.32 / 0.33 |
| particle tick / be tick / entity tick | 0.05 / 0.26 / 0.21 | 0.06 / 0.34 / 0.20 |
| other render then; other render + terrain + lod now | 4.75 | 2.54 + 0.08 + 4.26 = 6.88 |
| other tick then; other tick + sound + animate tick now | 0.49 | 0.42 + 0.02 + 0.09 = 0.53 |
| top block entity types | millstone, basin, factory panel, item drain, press | factory panel, millstone, basin, item drain, press |
| top mods (us/f) | xaeroworldmap 651, protection_pixel 157, minecraft 98, kubejs 89 | xaeroworldmap 860, distant_horizons 184, protection_pixel 154, minecraft 131, kubejs 89 |

The old buckets, the block entity types and the mods are in the same range. The old other render now splits into
`terrain`, `lod` and what is left; this session's DH cost was higher than the 0.5.0 session's whole other render,
which fits a frame 1.9 ms slower overall, and the scene a day later.

**8. Hooks and advice: PASS.** The hook lists are as above. The advice: `distant-horizons` (4.3 ms of each 12.7 ms
frame) and `mod-handlers` for xaeroworldmap (0.9 ms a frame, most in `RenderFrameEvent.Post`); the user found both
sensible. `unattributed-stall` stayed quiet: on the 0.5.0 numbers its repeat form would have fired here on rendering
in 8 of 18 hitches, time `terrain` and `lod` now take.

## Both

**9. The log and privacy: PASS.** No Renderable mixin error and no "could not check the hooks" line in
`logs/latest.log`. Each session's line reads `restored 228 listener lists; 0 still hold a timing proxy`. None of the
reports holds a UUID. The log's one Renderable-related warning, "Configuration file … renderable-client.toml is not
correct. Correcting", is NeoForge rewriting the file once for `overlayEnabled`'s new comment, as expected.

**10. Screen widths (carried from plan 6a): fixed, then PASS.** The checks found three display problems, none a
measurement error, all fixed before tagging (`879c7e8`, `a4b043c`; §P6.14):

- **The Hitches tab cut each split at its column**, so a hitch line ended "| terrain 0." and `lod` was never seen.
  A split now continues on rows under its hitch, broken at its `|` separators, and a part too wide for a line after
  its commas.
- **Contributor names were cut** at the narrow frame column (`minecraft:player #275110 (yo`). A contributor's time now
  sits under total ms and its name in the wide split column.
- **Sound rows named the category by its internal name** (`neutral`), while the Advice tab and the Music & Sounds
  screen say "Friendly Creatures". Labels now name the slider (`sound create:steam (Friendly Creatures)`); the key
  and the JSON keep the internal name. Labels themselves were not cut on the Mods tab.

The user checked each fix in a follow-up session at the spawn power station: the split wraps with `lod` visible
(`renderable-2026-10-11_12-17-33`, build `879c7e8`), sound rows read `(Friendly Creatures)`, and contributor names
show in full (`renderable-2026-10-11_12-32-34`, build `a4b043c`).

**11. The overlay switch (§P6.13): PASS.** With labels off in the instance's config, the user turned "Overlay
Enabled" on in Configured's screen after session 1 and saved: the labels appeared without a restart. Turned off the
same way, they went.

## Fixes made during the acceptance

| Commit | Fix | Tests |
| --- | --- | --- |
| `05c31d4` | One switch for the overlay: `/renderable overlay` and the key write `overlayEnabled`, and a change in a config screen or the file takes effect at once (§P6.13) | 319 |
| `879c7e8` | The Hitches tab wraps each split; sound rows name the Music & Sounds slider (§P6.14) | 328 |
| `a4b043c` | Hitches contributors show their name in the wide split column (§P6.14) | 330 |

## Observations

- **Distant Horizons is the largest single cost wherever it was measured:** 3.3 to 3.4 ms of an 8.2 to 8.8 ms frame at
  the other base, 4.3 of 12.7 at the user's base, and 2.5 to 2.9 ms at the spawn power station. It also spikes: one
  hitch at the other base drew `lod 23.1` ms, and one at the power station `lod 81.7` ms in a frame with a 78 ms GC
  pause. `terrain`, the chunk layers Sodium draws, is small by comparison (0.06 to 0.08 ms).
- **Create's machines are the sound load.** Mixers and funnels each play a second, `_compounded_1` event with every
  sound, so each costs twice; mixing played 137 times a second at the other base. Muting Blocks took 15 of that
  session's 17 hitches with it. The boilers' steam plays under Friendly Creatures, so muting Blocks does not touch it,
  and `sound-wait` names whichever slider the costly sound uses.
- **What `unnamed` is left with:** 0.20 to 0.27 ms a frame of client tick across the three sessions, in no case more
  than a quarter of a hitch's tick at the other base. `unattributed-stall` stayed quiet in all six 0.6.0 sessions.
- **The spawn power station** (three follow-up sessions, 115 to 141 fps): `sound-wait` (up to 18 ms),
  `distant-horizons` and, in two of them, long GC pauses (up to 98 ms); one session also had Particles on All.
- **A gap for later:** at the user's base, hitch #1 was 78 ms of packet handling from MineColonies, spread over
  `colony_view_building_view` (36 ms), `colony_view` (18 ms) and `colony_view_citizen_view` (11 ms).
  `mod-network`'s stall test looks for one payload type of 50 ms or more, so it said nothing; in 0.5.0 the same burst
  came as one 61 ms payload and was named. A test on a mod's payloads per hitch would catch it.
- **Overhead:** 0.56 to 1.5 s per 30 s session, about 2 to 5%. The power station, with 25 to 28 million timed calls
  a session, is the high end.
