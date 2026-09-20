# Phase 2 and 3 acceptance log

Date: 2026-09-20. Machine: Windows 11 Pro. GPU: `NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2`, `glVersion: 4.6.0 NVIDIA 596.49`.

The user chose to run the phase 2 and phase 3 in-game checks together at the end of phase 3, in the real Gears & Frontiers instance (`.MCUpdater/instances/Gears-And-Frontiers`, connected to the user's server): Java 21.0.3 (Eclipse Adoptium), 8192 MiB heap, 386 mods, 2560x1440 fullscreen, GUI scale 3, fps limit 220, vsync off, render distance 10. The pack ships Sodium 0.6.13, Iris 1.8.12-snapshot, Flywheel 1.0.6 (bundled by Create 6.0.10) and Distant Horizons 2.4.5-b; the build pins Sodium 0.8.13 and Iris 1.8.14-beta.1 for the pack update in progress, so the Environment blocks below show older versions by design.

Build under test: `renderable-0.3.0.jar` built from the phase 3 tree `a62ba52` (the first run) and, possibly, the rebuild from `4144da6` for the later runs; the two differ only in the third-party mixin config split, report-write error handling and documentation, not in any hook or number. Every mixin in both phases was also proven to apply before these runs by exporting the transformed classes from the dev client (`-Dmixin.debug.export=true`, auto-joining the dev save with `-PquickPlay`, with and without the pinned pack mods installed via `-PpackMods`).

Three sessions of 30 s were run:

| Run | Report | Where | Shaders | Frames | avg ms (fps) | p95 / p99 / max ms | Hitches | GC | Overhead |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `renderable-2026-09-20_08-27-35` | base, 2571 105 1145 | off | 2266 | 13.2 (76) | 23.7 / 32.5 / 91.4 | 44 (5.8%) | 15 / 369 ms | 555.76 ms over 6,947,008 calls |
| 2 | `renderable-2026-09-20_08-45-51` | base, 2569 104 1141 | Complementary Unbound r5.7.1 | 1370 | 21.9 (46) | 32.6 / 41.5 / 117.3 | 13 (2.4%) | 10 / 174 ms | 636.66 ms over 5,684,457 calls |
| 3 | `renderable-2026-09-20_08-56-32` | flight into unloaded terrain, ending at 3299 135 1107 | off | 4983 | 6.0 (166) | 8.2 / 9.8 / 25.4 | 3 (0.2%) | 19 / 152 ms | 78.59 ms over 701,661 calls |

`hooks applied` in every run: `BLOCK_ENTITY_RENDER, ENTITY_RENDER, PARTICLE_RENDER, PARTICLE_TICK, BLOCK_ENTITY_TICK, ENTITY_TICK, LIGHT, SODIUM_MESH, SODIUM_UPLOAD, VERTICES`, plus `FLYWHEEL` when the Flywheel backend was on and `IRIS_SHADOW` once shaders had been used in the client run. The three Sodium hooks applied against the pack's Sodium 0.6.13: its `push(MemoryStack, long, int, VertexFormat)` has the same descriptor as the pinned 0.8.13 (checked with `javap` on the instance's jar). No run printed `Attribution stack reset`.

Run 1's time split, which is the base's normal state:

```
tick 1.65 [particle tick 0.19, be tick 0.41, entity tick 0.33] | block entity 3.74 | entity 0.13 | particle render 0.04 | flywheel 0.44 | chunk upload 0.05 | light 0.01 | other render 5.36 | swap/wait 1.82
```

## Phase 2 checks

### 1. Shadow split (spec §9 check 3)

**Ran:** run 2 with Complementary Unbound r5.7.1 active, then run 1 (earlier, shaders off) as the comparison.

**Observed:** run 2's Environment shows `shaderPack: ComplementaryUnbound_r5.7.1.zip` and `hooks applied` includes `IRIS_SHADOW`. The Entities table carries shadow shares: `minecraft:player` 193.5 us/f render, `shadow 100.0%`, `verts/f 488.0` (in first person the player is drawn only into the shadow map, so 100% is the expected signature); `create:stationary_contraption` 30.0%; `create:contraption` 46.6%; animals 0.0%. Every block entity type shows `shadow 0.0%`, which is consistent with the pack not drawing block entities into the shadow map (not verified against the pack's settings). With shaders off (run 1) the column is `0.0%` everywhere and `shaderPack: none`.

Two side effects worth recording. With Complementary active, Flywheel reported `backend flywheel:off` and the Flywheel section printed `(no data: no Flywheel visualization ran; backend flywheel:off)`, exercising that empty-state branch for real; Create's machines then fell back to vanilla block entity renderers, so `block entity` rose from 3.74 to 8.40 ms per frame and `create:factory_panel` (1054.9 us/f), `create:chain_conveyor` (743.5) and `create:crushing_wheel` (486.7) rose to the top of the table. Average frame time went from 13.2 to 21.9 ms.

**Result: PASS** for the shadow column and the environment entries. The "same renderers still rank first" expectation does not apply cleanly because turning shaders on also turns Flywheel off in this pack.

### 2. Particles

**Observed (run 1):** `particle render 0.04` and `particle tick 0.19` in the time split; the Particles table lists `tv.soaryn.simpleweather.particles.RainParticle` (11.0 us/f render, 79.9 tick, avg live 77.0, 307.8 verts/f), `CampfireSmokeParticle` (90.6 live, 362.6 verts/f), `DripParticle$CoolingDripHangParticle`, Create's `AirFlowParticle`, `WaterDropParticle` and fifteen more. Hitch #2 attributed 34.00 ms of a 71.8 ms frame to a `CoolingDripHangParticle` tick (with `gc 34 ms` on the same frame; see the observations below).

**Result: PASS.**

### 3. Client ticks

**Observed (run 1):** `tick us/f` is non-zero for Create machines (`create:chute` 45.7, `create:hose_pulley` 43.7, `create:belt` 26.7, `create:mechanical_crafter` 21.4, `create:factory_panel` 16.9) and for animals (`minecraft:chicken` 37.8, `minecraft:squid` 16.1); the brackets show `be tick 0.41` and `entity tick 0.33`. Hitch #11 attributed 23.13 ms of `be tick` to `create:item_vault @ 2568 102 1146` (again with `gc 23 ms`).

**Result: PASS.**

### 4. Flywheel

**Observed (run 1):** `flywheel 0.44` in the split; the Flywheel section reads `bucket 0.44 ms/frame (3.3% of frame time), backend flywheel:indirect`, `visuals: 1765.7 block entities, 15.9 entities, 0.0 effects (average over 30 samples)`, then the class table led by `SingleAxisRotatingVisual` 449.5, `FunnelVisual` 201.8, `BeltVisual` 186.5, `FrogportVisual` 130.8. Environment: `flywheelBackend: flywheel:indirect`. On the results screen the user confirmed the Block Entities tab's note ("Flywheel-instanced machines are not timed here; see the Flywheel tab") reads to the end and the Flywheel tab shows simple class names.

**Result: PASS.**

### 5. Light

**Observed (run 1):** `light 0.01` in the average split; hitch #12 shows `light 2.6` on a frame with `gc 0 ms`, and run 1's hitch #2 `light 0.4`.

**Result: PASS.**

### 6. Stability

**Observed:** no `Attribution stack reset` line in any of the three reports. The user confirmed that walking through a portal after a session hides the block-entity and section labels and that they return on the way back, and that logging out with a result loaded caused no problem.

**Result: PASS.**

## Phase 3 checks

### 7. Chunk upload and sections (spec §9 check 4)

**Ran:** run 3, flying continuously into never-loaded terrain for the whole 30 s.

**Observed:** the JSON lists 1052 distinct sections rebuilt (1100 rebuilds, 4058 ms of worker mesh time, about 135 ms per second across Sodium's workers); the Sections table is led by `section 192 6 72` and `section 193 6 72` at 7 rebuilds each (28 ms, about 4 ms per rebuild). The `chunk upload` bucket totalled 72.1 ms over the run with a per-frame maximum of 1.35 ms. There were only three hitches, all around 25 ms with `gc 10-17 ms` and `other render` about 21 ms. The Blocks table is led by `minecraft:stone` 58.0 ms/s (1,904,482 calls, 0.9 us/call), then `oak_leaves` 12.7 (8.3 us/call), `grass_block`, `water` 5.7, `dirt`, `deepslate`. The JSON report is 4.86 MB for 4983 frames; the sections array is about 280 KB of that.

**Result: PASS for the buckets and tables; the spec's hitch expectation was not reproduced.** On this GPU at render distance 10, 1100 section rebuilds cost 72 ms of upload in total, so no frame had `chunk upload` as its largest bucket. The hook is live (the bucket is non-zero on 1249 of the flight's 4983 frames, 72.1 ms in total) and the section attribution works; the "rebuild storm" hitch remains to be seen on lower-end hardware. Whether yellow section labels appeared after the flight was not separately reported.

### 8. Blocks tab

**Observed:** run 3's Blocks table as above; run 1 at the base shows `minecraft:stone` 44.4 ms/s and `create:andesite_casing` 11.1 ms/s at 8.5 us/call while standing still: the base's machines drove 954 rebuilds across 575 sections in 30 s, the busiest being section `159 5 69` at 35 rebuilds (161.7 ms). Fluids appear as `minecraft:water`. The user confirmed the Blocks and Sections tab headers are readable on screen.

**Result: PASS.**

### 9. Vertices

**Observed:** `verts/f` is populated for block entities (`create:basin` 9575.4, `create:factory_panel` 6435.6, `create:item_drain` 6259.4 in run 1) and particles (`CampfireSmokeParticle` 362.6). Ordinary mob models read `0.0` (pig, horse, cow, sheep, zombie, and chicken in runs 2 and 3) with a few small residuals in run 1 (`minecraft:chicken` 5.1, `minecraft:blaze` 2.3). The cause appears to be the pack's Accelerated Rendering 1.0.8-1.21.1-alpha, whose mixin config takes over the buffer builder, buffer source and vertex consumer (`buffers.BufferBuilderMixin`, `buffers.BufferSourceMixin`, `buffers.VertexConsumerMixin`) and model parts (`feature.modelparts.ModelPartMixin`) to expand model vertices on the GPU, so those vertices never pass through `BufferBuilder.beginVertex` or Sodium's `push`; this was read from the mod's mixin config, not confirmed by removing the mod. Create's machines write their vertices directly and are counted. The player is counted in both later runs (488.0 verts/f in run 2, all of it shadow pass; 1268.0 verts/f in run 3 with shaders off while riding), as are Create's contraptions, dropped items (23.1 verts/f in run 3) and the aircraft the user flew (`immersive_aircraft:gyrodyne`, 1,793,880 vertices).

**Result: PASS for block entities, particles and the player; zero for the mob renderers Accelerated Rendering takes over, documented in the README.** Hooking Accelerated Rendering's mesh uploads is on the phase 4 list.

### 10. Section row actions

**Observed:** Highlight from a Sections row drew the marker at the section's centre block only; the user asked how big a section is. The marker was changed after these runs to outline the whole 16x16x16 section (the change is in the same commit as this log). Teleport from a section row was not separately reported.

**Result: PASS for Highlight after the follow-up; Teleport not reported.**

### 11. Without Sodium

**Not run by the user**, who judged it not useful for the pack. Coverage instead: every dev-client launch without `-PpackMods` during phases 2 and 3 showed the Sodium and Flywheel mixins gated out with no errors, and `TextReportWriterTest.rankedTablesAlignAndEmptyCategoriesSaySo` pins the `(no data: Sodium hooks were not active)` text.

**Result: NOT RUN.**

### 12. Stability

**Observed:** no crash during the flight, at session end mid-flight, or on logout; no `Attribution stack reset` in any report.

**Result: PASS.**

## Observations for phase 4

- **GC pauses land on whichever key was open.** In run 1 nearly every hitch above 40 ms coincides with a GC pause of the same size: `DripParticle$CoolingDripHangParticle` 34.00 ms tick with `gc 34`, `create:basin` 27.99 ms with `gc 28`, `create:item_vault` 23.13 ms `be tick` with `gc 23`, `flywheel 30.9` with `gc 26`, and `other render` 35-49 ms frames with `gc 25-41`. Run 2's #2 (`create:item_drain` 30.10 ms, `gc 30`) is the same. The hitch line's `gc` column exposes it, but the per-key peaks and the hitch top contributors do not subtract it. This is the most misleading number in the report and the first phase 4 item.
- **Genuine non-GC hitches exist too:** run 1 #8 (`swap 46.4`, `gc 0`), #14 and #15 (`swap 21-23`, `gc 0`), and #12 (`other render 12.2`, `light 2.6`, `gc 0`).
- **The base rebuilds sections while the player stands still:** 954 rebuilds across 575 sections in 30 s (run 1), 4282 ms of worker mesh time, of which stone alone is 44 ms/s; one section (`159 5 69`) was rebuilt 35 times. Worth a look at which Create blocks trigger it.
- **Shaders turn Flywheel off in this pack**, doubling block-entity time; Create machines then show up as ordinary block entities.
- **Sampling overhead** is about 1.9% at the base (about 80 ns per timed pair, 6.9 million pairs in 30 s) and about 0.3% in flight. Vertex counting is not included in the estimate.
- **Entity vertices are invisible under Accelerated Rendering** (check 9).
- The sections array is bounded in practice (1052 rows for a 30 s flight at render distance 10), so no cap was added; the JSON stays under 5 MB per 30 s.

## Follow-ups made after these runs

- README: caveat that GPU vertex-expansion mods bypass the vertex hooks.
- Section highlight enlarged from the centre block to the full 16-block section.
