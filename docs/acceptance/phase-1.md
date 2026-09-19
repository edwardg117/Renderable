# Phase 1 acceptance log

Date: 2026-09-19. Machine: Windows 11 Pro. GPU: `NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2`, `glVersion: 4.6.0 NVIDIA 596.49`.

Two client configurations were used:

- **Dev client** (check 1): Java 21.0.11 (Eclipse Adoptium), NeoForge 21.1.220, window 854x480 windowed, GUI scale auto (0).
- **Pack client** (checks 2 and 5): Java 21.0.3 (Eclipse Adoptium), 8192 MiB heap, 386 mods, 2560x1440 fullscreen, GUI scale 3, fps limit 220, vsync off, render distance 10.

Build under test: `renderable-0.1.0.jar` built from `main`. The first pack run (check 2, 21:57:52) used the jar built from commit `ecb12e1`. The second pack run (check 2b, 22:42:40) used a working-tree build that included the screen-space overlay-label fix described below, committed as `0a43259` after these checks.

## Check 1: debug stall (spec §9 check 1)

**Ran:** dev client, singleplayer. Set `debugCommands = true` in `run/config/renderable-client.toml`, then ran `/renderable start 20` and `/renderable debug stall 80`. This ran on a bare dev client (`mods: 3`; Sodium, Iris and Flywheel absent) because the `-PpackMods` route did not start, as explained under check 2; the build was the working tree at commit `ecb12e1` (identical mod code to `a257767`; `ecb12e1` adds only the CI workflows).

**Observed:** exactly one hitch was recorded, attributed to the fake block entity:

```
  #1  frame 863 @ 7.3s  86.2 ms  |  tick 0.9 | block entity 83.0 | entity 0.0 | other render 1.7 | swap 0.5  |  gc 0 ms
      81.77 ms  renderable:debug_stall @ 0 0 0
      0.03 ms  minecraft:sheep #18
      0.01 ms  minecraft:sheep #9
      0.01 ms  minecraft:cow #178
      0.01 ms  minecraft:chest @ 17 65 1
```

The results screen's Hitches tab showed the same frame and contributors (observed in-game; no artefact captured for this part).

**Result: PASS.** The stall is attributed to `renderable:debug_stall` at about 80 ms plus that frame's normal cost, as spec §9 check 1 requires.

## Check 2: Enchantable Machinery in the pack (spec §9 check 2)

**Ran:** the plan's route, `./gradlew runClient -PpackMods`, did not start the client: the Modrinth Sodium NeoForge jar is a JarJar container, so Sodium's classes are not on the dev classpath, and Iris's Sodium-compat mixins fail to resolve their targets (`ClassNotFoundException: net.caffeinemc.mods.sodium...` in `run/logs/latest.log`). This is a dev-environment limitation, not a defect in Renderable.

Instead, the built jar was dropped into the user's real Gears & Frontiers instance (`.MCUpdater/instances/Gears-And-Frontiers`, connected to the user's actual server) and `/renderable start 30` was run at the user's main base (position 2566 97 1152) with enchanted Create machines in view. The pack currently ships Sodium 0.6.13 and Iris 1.8.12-snapshot; the versions pinned for the build (Sodium 0.8.13, Iris 1.8.14-beta.1) target the pack update in progress, so the version mismatch shown in the Environment block below is expected, not a problem.

**Observed (first run, 21:57:52):**

```
Environment
  minecraft: 1.21.1
  neoforge: 21.1.220
  java: 21.0.3 (Eclipse Adoptium)
  maxHeapMiB: 8192
  mods: 386
  gpu: NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2
  glVersion: 4.6.0 NVIDIA 596.49
  renderDistance: 10
  simulationDistance: 5
  biomeBlend: 2
  graphics: fancy
  vsync: false
  fpsLimit: 220
  particles: ALL
  entityDistance: 5.0
  guiScale: 3
  window: 2560x1440 fullscreen
  sodium: 0.6.13+mc1.21.1
  iris: 1.8.12-snapshot+mc1.21.1-local
  flywheel: 1.0.6
  distanthorizons: 2.4.5-b
  dimension: minecraft:overworld
  sections: C: 312/8760 D: 10
  entities: E: 36/229, B: 0, SD: 10
  position: 2566 97 1152
  hooks applied: BLOCK_ENTITY_RENDER, ENTITY_RENDER
```

Sodium, Iris, Flywheel 1.0.6 and Distant Horizons 2.4.5-b were all loaded alongside Renderable.

```
Frames
  Sampled 30.0 s, 2889 frames, avg 10.4 ms (96 fps), p50 9.4, p95 17.1, p99 21.9, max 91.4 ms
  Hitches: 16 frames over 25.4 ms threshold, totalling 0.7 s (2.2% of wall time)
  Time split (avg ms/frame): tick 0.85 | block entity 3.11 | entity 0.07 | particle render 0.00 | flywheel 0.00 | chunk upload 0.00 | light 0.00 | particle tick 0.00 | be tick 0.00 | entity tick 0.00 | other render 5.09 | other tick 0.85 | swap/wait 1.27
  GC: 15 collections, 286 ms paused
  Renderable overhead: about 304.38 ms over 4005009 timed calls
```

```
Block entities by type (top 20)
  name                                                     render us/f  tick us/f  share  peak ms  shadow  verts/f    calls
  -------------------------------------------------------  -----------  ---------  -----  -------  ------  -------  -------
  createenchantablemachinery:enchantable_millstone               505.4        0.0   4.9%     0.29    0.0%      0.0   36,352
  create:basin                                                   488.4        0.0   4.7%    14.71    0.0%      0.0   97,163
  create:factory_panel                                           330.9        0.0   3.2%     0.76    0.0%      0.0  123,999
  create:item_drain                                              268.6        0.0   2.6%    20.77    0.0%      0.0  272,858
  createenchantablemachinery:enchantable_mechanical_press        217.6        0.0   2.1%     0.27    0.0%      0.0   23,112
  createenchantablemachinery:enchantable_mechanical_mixer        158.4        0.0   1.5%     0.19    0.0%      0.0   26,001
```

`createenchantablemachinery:enchantable_millstone` topped the ranking at 505.4 us/f over 36,352 calls; `enchantable_mechanical_press` and `enchantable_mechanical_mixer` placed 5th (217.6 us/f) and 6th (158.4 us/f). "Block entities by position" listed 16 individual enchantable millstones at 30.1-37.5 us/f each, plus `colossalchests:colossal_chest`, `createenchantablemachinery:enchantable_spout` and two enchantable presses in the top 20. The overlay labelled the machines with their type ids and microseconds per frame.

**Observed (second run, check 2b, 22:42:40, screen-space label build):**

```
Frames
  Sampled 30.0 s, 2535 frames, avg 11.8 ms (84 fps), p50 10.7, p95 18.9, p99 29.2, max 104.9 ms
  Hitches: 31 frames over 26.7 ms threshold, totalling 1.3 s (4.3% of wall time)
  Time split (avg ms/frame): tick 1.08 | block entity 3.53 | entity 0.08 | particle render 0.00 | flywheel 0.00 | chunk upload 0.00 | light 0.00 | particle tick 0.00 | be tick 0.00 | entity tick 0.00 | other render 5.94 | other tick 1.08 | swap/wait 1.20
  GC: 11 collections, 208 ms paused
  Renderable overhead: about 301.86 ms over 3509988 timed calls
```

```
Block entities by type (top 20)
  name                                                     render us/f  tick us/f  share  peak ms  shadow  verts/f    calls
  -------------------------------------------------------  -----------  ---------  -----  -------  ------  -------  -------
  create:factory_panel                                           694.8        0.0   5.9%     1.09    0.0%      0.0  175,082
  create:basin                                                   589.2        0.0   5.0%     3.33    0.0%      0.0   73,234
  createenchantablemachinery:enchantable_millstone               568.8        0.0   4.8%     0.44    0.0%      0.0   36,400
  create:item_drain                                              210.8        0.0   1.8%     0.95    0.0%      0.0  195,884
  createenchantablemachinery:enchantable_mechanical_press        158.9        0.0   1.3%     2.47    0.0%      0.0   15,336
  createenchantablemachinery:enchantable_mechanical_mixer        157.6        0.0   1.3%     0.26    0.0%      0.0   22,815
```

`create:factory_panel`, `create:basin` and `enchantable_millstone` top the ranking at 694.8, 589.2 and 568.8 us/f respectively; `enchantable_mechanical_press` and `enchantable_mechanical_mixer` again placed 5th and 6th.

**Result: PASS.** Spec §9 check 2 requires the renderer types to "rank at the top of the Block Entities tab and carry labels in the overlay". The ranking evidence here is the text report (the in-game Block Entities tab shows the same data); Enchantable Machinery's renderer types top or nearly top that ranking near those machines in both runs. The label half of the check is more qualified: in the first run the labels were drawn but occluded under Iris shaders (see the overlay defect below), so it is the second run (22:42:40), after the screen-space fix, that actually satisfies "carry labels in the overlay".

## Check 5: idle cost (spec §9 check 5)

**Ran:** real pack, main base, standing still with the jar installed and no session running, reading F3 FPS and allocation rate; then removed the jar and repeated.

**Observed:** with the jar, 290-320 fps and 560-590 MB/s allocation; without the jar, 330-340 fps and 600-660 MB/s allocation. That works out to about 1.9 MB per frame in both cases, which is consistent with the MixinExtras `@WrapMethod` argument boxing being eliminated by the JIT; it is not direct proof.

The FPS difference could not be attributed to the mod: the first "with jar" reading was taken while the previous session's overlay was still drawing up to 50 labels per frame, and frame rate at the base fluctuates too much on its own to isolate a 10% effect. That same "with jar" reading is also the source of the 560-590 MB/s allocation figure, so the two caveats do not offset: the overlay's label drawing would add allocation as well as frame time, so if anything that figure overstates the mod's idle allocation, which strengthens rather than weakens the "no measurable allocation" conclusion.

Sampling overhead as reported by the mod itself:

```
  Renderable overhead: about 304.38 ms over 4005009 timed calls
```
(first pack run, about 1% of wall time, roughly 76 ns per timed call), and

```
  Renderable overhead: about 301.86 ms over 3509988 timed calls
```
(second pack run).

**Result: NOT VERIFIED against the spec's frame-time criterion.** Spec §9 check 5 reads "with no session running, the F3 frame time is unchanged within noise when the mod is added and removed". Frame time at the base fluctuates more than the effect being looked for, so the with/without comparison was inconclusive. Supporting evidence that the idle cost is small: indistinguishable per-frame allocation with and without the jar, and a self-reported overhead of about 1% of wall time while sampling (not an idle figure). Recommendation: run a controlled A/B in a static test world (fixed camera, no entities moving) as a phase 2 follow-up before claiming zero idle cost in the README. Until that is done, the README's "costs nothing when it is not sampling" is a design claim rather than a measured one.

## Overlay defect found during check 2

With Iris shaders active, the in-world cost labels (rendered as vanilla see-through text) drew behind blocks instead of through them. The labels were reimplemented as a screen-space GUI layer: the world position is projected through the captured view-projection matrix and the label is drawn over everything, rather than as see-through world text. The second check-2 run (check 2b, 22:42:40) confirmed labels are now visible through obstructions.

The in-world highlight box had the same problem and was moved to the same screen-space layer: the block's eight corners are projected and a red rectangle is drawn around their screen bounds, never smaller than 8 px so a distant block stays findable. Re-tested in the pack with the 22:53 build: the rectangle shows through walls. The user notes it is less polished than Observable's marker but acceptable for a first pass; a wireframe box drawn in screen space is a phase 2 polish item.

## Observations for phase 2

- In the first pack run, GC dominated the hitches recorded at the base: 15 collections totalling 286 ms paused, and every large single-block-entity hitch coincided with a GC pause of the same size, e.g. `colossalchests:colossal_chest` at 68.17 ms on a frame reporting `gc 68 ms`. The hitch lines expose this; the per-key "peak ms" column in the block-entity tables does not distinguish it and silently includes GC-inflated peaks. The second run was not dominated by GC the same way: only 9 of its 25 listed hitches had a GC pause at all, its two largest hitches (104.9 and 88.8 ms) had `gc 0 ms` and were dominated by "other render" instead (49.6 and 73.2 ms of the frame), though the third largest at 85.5 ms was a 62 ms GC pause, and its largest single per-contributor costs (`animal_pen:animal_pen_tile_entity` 4.61 ms, `createenchantablemachinery:enchantable_mechanical_press` 2.47 ms) also occurred on `gc 0 ms` frames.
- The largest bucket on average across both pack runs was "other render" (5-6 ms/frame), i.e. work not yet attributed to a hook (Sodium terrain, Flywheel, Distant Horizons). Attributing it is phase 2/3 scope.
- The pack instance's `renderable/` folder holds `renderable-2026-09-19_21-57-52.json` at 3,548,665 bytes and `renderable-2026-09-19_22-42-40.json` at 3,166,025 bytes, both for 30 s sessions.
- The zero buckets in the time split (particle render, flywheel, chunk upload, light, and the three tick buckets) and the `shadow 0.0%` column in the block-entity tables are not measured zeros: they are hooks not present in this build (`hooks applied: BLOCK_ENTITY_RENDER, ENTITY_RENDER` only). Spec §11 places the Iris shadow-pass split and the rest of those hooks in phases 2 and 3.
