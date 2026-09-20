# Renderable: client render profiler for Gears & Frontiers

**Date:** 2026-09-19
**Status:** approved design, awaiting implementation plan
**Mod id:** `renderable`
**Home:** separate repository (`H:\repos\Renderable`, GitHub `edwardg117/Renderable`). This
copy of the spec lives in the pack repo until the mod repo exists, then moves there.

## 1. Purpose

Observable shows which block entities cost server tick time. Nothing in the pack does the same
for the client. Players report "stutters and unplayable framerates", and each report so far has
needed a hand-read vanilla debug profile to separate a slow renderer from a chunk-upload burst
from a GC pause from a swap stall.

Renderable is a client-only NeoForge mod that samples the render loop for a fixed window and
answers two questions:

1. **What costs frame time every frame?** Ranked block entities, entities, particles, static
   blocks, and chunk sections, by microseconds per frame, with in-world labels.
2. **What caused each hitch?** For every frame that spiked, which buckets and which specific
   objects made it long.

The pack's known problems shape the acceptance criteria: Create: Enchantable Machinery's
renderers (measured 81-102x the cost of the Create renderers they wrap) must rank at the top
near their machines, and a forced render-thread stall must show up in the hitch log with the
right attribution.

## 2. Scope decisions

| Decision | Choice |
| --- | --- |
| Measured layers | Renderers, client ticking, particles, Flywheel aggregate, Sodium mesh build, chunk upload, light updates, GC, swap remainder, vertex counts |
| Language / toolchain | Java 21, ModDevGradle 2, NeoForge 21.1.220 (matches pack revision 4.4) |
| Hosting | Separate repo, jar attached to GitHub Releases by CI |
| Rollout | Dev-only until proven. No pack XML entry in this design. |
| Export | Text report and JSON, both written on every finished session |
| Name | Renderable |
| License | MIT |
| Dependencies | None required at runtime. Sodium, Iris and Flywheel are optional integrations. |

Out of scope, deliberately:

- Server-side anything. Observable already covers ticks on the server.
- Per-visual timing inside Flywheel. See section 4.4.
- GPU-side timing (timer queries). Vertex counts are the proxy.
- A vanilla (non-Sodium) chunk-mesh hook. The pack always ships Sodium; without it the mesh tabs
  say so.
- Publishing to Modrinth or CurseForge.

## 3. Measurement model

### 3.1 Session

A **session** is one sampling window. Default 30 s, configurable per run, hard cap 600 s.
It starts at the next frame boundary and ends at the frame boundary after the duration
elapses, or early on `/renderable stop` (kept if at least 2 s were sampled), or on
`/renderable cancel` and on level unload (discarded, with a chat message saying why).

Only one session runs at a time. The last finished session stays in memory until the next one
finishes or the client exits, so the results screen and overlay can be reopened.

### 3.2 Frame record

Every frame in the session produces one record:

| Field | Source |
| --- | --- |
| `index`, `startNanos` | frame counter, `System.nanoTime()` at frame start |
| `totalNanos` | start of this frame to start of the next |
| `tickNanos` | sum of client ticks that ran inside this frame (`ClientTickEvent.Pre` to `Post`) |
| `renderNanos` | `RenderFrameEvent.Pre` to `RenderFrameEvent.Post` |
| buckets | `BLOCK_ENTITY`, `ENTITY`, `PARTICLE_RENDER`, `PARTICLE_TICK`, `FLYWHEEL`, `CHUNK_UPLOAD`, `LIGHT`, `BE_TICK`, `ENTITY_TICK`, each a nanosecond sum for this frame |
| `gcCount`, `gcMillis` | delta of `GarbageCollectorMXBean` collection count and time since the previous frame |
| `top` | up to `topContributorsPerFrame` (default 5) keys with the most nanoseconds in this frame, across all timed keys |

Derived values, computed at report time and never stored twice:

- `otherRenderNanos = renderNanos - (BLOCK_ENTITY + ENTITY + PARTICLE_RENDER + FLYWHEEL + CHUNK_UPLOAD + LIGHT)`
- `otherTickNanos = tickNanos - (PARTICLE_TICK + BE_TICK + ENTITY_TICK)`
- `swapNanos = totalNanos - tickNanos - renderNanos` (window swap, vsync wait, input polling, everything outside tick and render)

Render buckets nest inside `renderNanos` and tick buckets inside `tickNanos`. Nothing is
counted in two buckets. When a timed call nests inside another timed call (an item rendered by
a block entity renderer), the inner call is attributed to the innermost key and the outer key's
time includes it; buckets are only incremented by the outermost call, tracked with a depth
counter.

### 3.3 Hitch detection

After the session ends:

- `median` = median of `totalNanos` over all frames.
- `threshold = max(median * hitchMedianMultiplier, median + hitchMinExcessMs)` with defaults
  `2.0` and `16 ms`. At a 16.6 ms median the threshold is 33.2 ms; at 7 ms it is 23 ms.
- Every frame above the threshold is a **hitch**. The hitch list is sorted by `totalNanos`
  descending and carries the frame's bucket split, GC delta, and `top` entries.

The thresholds are config values, and the results screen shows the computed threshold so the
reader knows what "hitch" meant for that session.

### 3.4 Keys and accumulators

Every timed thing has a key. Keys are small immutable records and are the map keys for the
accumulators.

| Key | Identity | Timed in |
| --- | --- | --- |
| `BlockEntityKey` | block entity type id, dimension, packed block position | render (phase 1), client tick (phase 2) |
| `BlockEntityTypeKey` | block entity type id | roll-up of the above |
| `EntityKey` | entity type id, entity id, display name for report | render (phase 1), client tick (phase 2) |
| `EntityTypeKey` | entity type id | roll-up |
| `ParticleKey` | particle class name | render and tick (phase 2) |
| `BlockKey` | block id | Sodium mesh build (phase 3) |
| `SectionKey` | dimension, section position | Sodium mesh build (phase 3) |

Flywheel visual counts are averaged per class in `FlywheelStats`, not keyed accumulators.

Each key's accumulator holds: `calls`, `totalNanos`, `shadowNanos` (Iris shadow pass share),
`tickNanos`, `maxFrameNanos`, `vertices`, and a scratch `frameNanos` (render plus tick
nanoseconds in the current frame, the value the per-frame `top` scan ranks by) that is reset
each frame.
Keys touched in the current frame go on a touched-list so the per-frame reset and the top-N
scan cost O(touched), not O(all keys).

A parallel `TypeStats` per (category, type id) keeps the same per-frame scratch value, so a type row's peak
is the type's worst frame rather than its worst member. Accumulators hold a reference to their `TypeStats`,
so the hot path never looks one up, but the reference is only ever read: a type's frame total is summed
from its members' `frameNanos` in the frame-boundary loop that already walks the touched list, not inside
`end()`. That placement is load-bearing, not tidiness — work done inside `end()` lands inside the timing
window of whichever call encloses it, so an item renderer summing into its block entity's type would
inflate the block entity's own measurement; the frame boundary has nothing open, so it inflates nothing.
A frame's contribution is only allowed to set a peak when it is not
GC-suspect: a value at least as large as that frame's reported GC pause (which the JVM gives in whole
milliseconds, so the comparison allows one millisecond of slack, and pauses under two milliseconds are
ignored) may be the collector rather than the code, and is counted in `gcFrames` instead.

Render-thread accumulators are plain `HashMap`s guarded by the render thread only. Mesh
accumulators (`BlockKey`, `SectionKey`) are written from Sodium worker threads and use
`ConcurrentHashMap` with `LongAdder`s; they are not per-frame and do not feed `top`.

### 3.5 Overhead policy

- Every hook first reads one `static boolean ACTIVE`. When false it returns. That is the entire
  idle cost.
- No allocation on hot paths while sampling: accumulators are created on first sight of a key
  and reused; the touched-list is a pre-sized array list of references.
- Per-particle timing costs two clock reads per particle. With 10,000 particles that is roughly
  0.5 ms per frame while sampling. The report states the session's own estimated overhead:
  `timedCalls * calibratedCallNanos + vertexCalls * calibratedVertexNanos`. Each constant is
  calibrated at session start — `calibratedCallNanos` by timing a loop of 10,000 empty begin/end
  pairs, `calibratedVertexNanos` by timing a loop of 10,000 vertex-counting calls the same way —
  so both what a timed call costs and what a vertex-counting call costs are covered. The reader
  can discount it.
- `BufferBuilder.beginVertex` (and Sodium's `push`, and Accelerated Rendering's builder) get one branch on
  the idle flag plus one increment while sampling.
- The mod's own in-frame work — the sampling HUD and the once-a-second Flywheel visual sample — is timed
  rather than estimated and reported separately; it is part of other render time, not of any bucket.

## 4. Hooks

All mixins call into one static `Probe` class in `hooks`. `Probe` owns `ACTIVE`, the current
attribution stack (a small array, depth counter), and forwards to the live session in `core`.
Every timed hook is wrapped in try/finally so a renderer throwing never leaves a key on the
stack.

Block entity and entity accumulators are cached on the object itself through a `@Unique` field reached by a
duck-typed interface, not in a map, so a running session never keeps a rendered object alive and cannot
distort the GC figures it reports. The accumulator stores the **id** of the session that created it, never
a reference to the session itself — a reference would reach that session's whole frame list — so an object
that survives into the next session is re-keyed on first sight.

Vanilla targets below are Mojang-mapped names as expected for 1.21.1 and are verified in the
dev environment in the phase 1 plan. Third-party targets were read with `javap` from the jars
this pack pins (Sodium 0.8.13, Iris 1.8.14-beta.1, Flywheel 1.0.6 bundled in Create 6.0.10).

### 4.1 Phase 1: frame model, block entities, entities

| Target | Kind | Records |
| --- | --- | --- |
| `RenderFrameEvent.Pre` / `Post` | NeoForge events | frame boundary, `renderNanos`, per-frame reset and top-N scan |
| `ClientTickEvent.Pre` / `Post` | NeoForge events | `tickNanos` |
| `GarbageCollectorMXBean` | read at frame start | `gcCount`, `gcMillis` |
| `BlockEntityRenderDispatcher#render(BlockEntity, float, PoseStack, MultiBufferSource)` | `@WrapMethod` | `BlockEntityKey` render time, `BLOCK_ENTITY` bucket |
| `EntityRenderDispatcher#render(Entity, double, double, double, float, float, PoseStack, MultiBufferSource, int)` | `@WrapMethod` | `EntityKey` render time, `ENTITY` bucket |
| `ClientPlayerNetworkEvent.LoggingOut`, level unload | NeoForge events | discard running session |

Sodium's world renderer calls the same `BlockEntityRenderDispatcher#render` for every block
entity it draws (confirmed in `SodiumWorldRenderer.renderBlockEntity`), so one hook covers
vanilla and Sodium.

### 4.2 Phase 2: particles, client ticking, Flywheel, light, shadow split

| Target | Kind | Records |
| --- | --- | --- |
| call to `Particle#render(VertexConsumer, Camera, float)` inside `ParticleEngine#render(LightTexture, Camera, float, Frustum, Predicate<ParticleRenderType>)` | `@WrapOperation` | `ParticleKey` render time, `PARTICLE_RENDER`; live particle count per class |
| call to `Particle#tick()` inside `ParticleEngine#tickParticle(Particle)` | `@WrapOperation` | `ParticleKey` tick time, `PARTICLE_TICK` |
| `LevelChunk$BoundTickingBlockEntity#tick()` when `level.isClientSide` | `@WrapMethod` | `BlockEntityKey` tick time, `BE_TICK` |
| `Level#guardEntityTick(Consumer, Entity)` when client level | `@WrapMethod` | `EntityKey` tick time, `ENTITY_TICK` |
| `LevelLightEngine#runLightUpdates()` on the client | `@WrapMethod` | `LIGHT` |
| Flywheel `VisualizationManager$RenderDispatcher` implementation: `onStartLevelRender`, `afterEntities`, `beforeCrumbling` | `@WrapMethod` on the impl class | `FLYWHEEL` |
| Flywheel `VisualizationManagerImpl` storage, read directly by `FlywheelCompat` with a failure latch | once per second while sampling, no mixin | Flywheel visual totals and visual counts by class, averaged over samples |
| Iris `ShadowRenderingState.areShadowsCurrentlyBeingRendered()` | static call through an `IrisCompat` class loaded only when Iris is present | routes the current call's nanoseconds into `shadowNanos` as well |

**Known gap: a riding entity's tick is attributed to its vehicle.** `ClientLevel.tickEntities` routes only
*non-passengers* through `guardEntityTick`; a passenger is ticked by its vehicle through
`Entity.rideTick()`, which this hook never sees. So while a player is riding, their own tick disappears from
the entity table and its cost is folded into the vehicle's inclusive tick time. Measured on 2026-09-20: in a
flight session the player was rendered 4,983 times but ticked **zero** times, while
`immersive_aircraft:gyrodyne` reported 600 ticks at 309.7 µs each — a figure that silently contains the
player's tick. The cost is misattributed, not lost, and nothing in the report says so. Closing it needs a
second hook on `Entity.rideTick()`, which is phase 5 scope; see the phase 4 addendum's "What phase 4 does
not do".

Iris's shadow pass renders block entities and entities a second time per frame. Both passes
are real frame cost, so they are summed into `totalNanos`, and the shadow share is shown as
its own column so a reader can tell "expensive renderer" from "expensive because shadows
double it".

### 4.3 Phase 3: Sodium mesh build, chunk upload, vertex counts

| Target | Kind | Records |
| --- | --- | --- |
| `ChunkBuilderMeshingTask#execute(ChunkBuildContext, CancellationToken)` | `@WrapMethod` | `SectionKey` rebuild count and time (position from the superclass's `render` field, read through an accessor mixin on `ChunkBuilderTask`) |
| call to `BlockRenderer#renderModel(BakedModel, BlockState, BlockPos, BlockPos)` inside that `execute` | `@WrapOperation` | `BlockKey` time and count |
| call to `FluidRenderer#render(LevelSlice, BlockState, FluidState, BlockPos, BlockPos, TranslucentGeometryCollector, ChunkBuildBuffers)` inside that `execute` | `@WrapOperation` | `BlockKey` (fluid's block) time and count |
| `RenderSectionManager#uploadChunks()` and `#updateChunks(boolean)` | `@WrapMethod` | `CHUNK_UPLOAD` |
| `BufferBuilder#beginVertex()` (private; both `addVertex` paths call it once per vertex) | `@ModifyReturnValue` | increments `vertices` on the innermost current key |
| `BufferBuilder#push(MemoryStack, long, int, VertexFormat)` added by Sodium's `BufferBuilderMixin` (gated on Sodium, priority 1100) | `@ModifyVariable` on the count argument | adds the pushed vertex count; Sodium routes entity, block entity, particle and item vertices here without calling `beginVertex` |
| `AcceleratedBufferBuilder#addVertex` (both overloads), `#addClientMesh`, `#addServerMesh` added by Accelerated Rendering (gated on `acceleratedrendering`, targeted by string) | `@ModifyReturnValue`, `@ModifyVariable`, `@Inject` head/return | adds the vertices of renderers whose geometry the mod expands on the GPU, which never reach `beginVertex` |

Mesh work runs on worker threads and does not directly cost frame time; it is reported as
milliseconds per second and per call, and the section view shows rebuild rate. Chunk upload is
on the render thread and is a per-frame bucket, so a rebuild storm shows up in the hitch log
as upload time while the Blocks and Sections tabs show what was being rebuilt.

### 4.4 Flywheel limitation

Create 6 renders most machines through Flywheel instancing. Those block entities never reach
`BlockEntityRenderDispatcher`, and each visual's per-frame work is invoked through a method
reference inside Flywheel's task plans, which leaves no call site to wrap. Renderable therefore
reports Flywheel as one render-thread bucket plus visual counts by class, and the results
screen says so in the Block Entities tab header when the Flywheel bucket is non-zero. Create:
Enchantable Machinery's renderers are vanilla renderers wrapping Create's and are timed per
block like any other.

### 4.5 Mixin gating and failure policy

- A mixin config plugin applies Sodium, Iris and Flywheel mixins only when that mod is loaded.
- Third-party mixins are `require = 0`. If one fails to apply after a version bump, the client
  keeps running, the log carries a warning, and the affected tab shows "hook did not apply for
  <mod> <version>".
- Vanilla mixins are required; if they fail the mod is not usable and should fail loudly.
- `Probe` records which hooks applied. The report's environment header lists them.

## 5. User interface

### 5.1 Trigger and control

- Keybind **F7** (rebindable): idle starts a session with the default duration; sampling stops
  it early.
- Keybind **overlay toggle**, unbound by default.
- Client commands, registered through `RegisterClientCommandsEvent`:

```
/renderable start [seconds]
/renderable stop
/renderable cancel
/renderable results
/renderable overlay [on|off]
/renderable copy
/renderable debug stall <ms>      (only when config debugCommands = true)
```

The stall command sleeps the render thread inside a fake block-entity attribution named
`renderable:debug_stall` at position 0 0 0, so the hitch pipeline can be verified end to end
without needing a naturally slow mod.

### 5.2 HUD while sampling

One line, top centre so it never collides with the F3 overlay: `Renderable: 12.0 / 30 s |
960 frames | 3 hitches`. The hitch count while sampling uses a rolling median of the last 120 frames since
the final median is unknown; the results screen recomputes with the full session.

### 5.3 Results screen

Opens automatically when a session finishes (config `openResultsOnFinish`, default true), and
on `/renderable results`. Vanilla `Screen` with a tab row and an `ObjectSelectionList` per tab.
No UI library dependency.

Tabs and columns:

| Tab | Rows | Columns |
| --- | --- | --- |
| Frames | summary block plus a frame-time histogram | sampled seconds, frames, avg, p50, p95, p99, max, threshold, average bucket split |
| Hitches | one per hitch | frame index, time into session, total ms, bucket split bar, GC delta, top contributors (a GC-suspect contributor's **split** cell reads `[gc]` in place of the bar, since the name cell is the one the list truncates) |
| Block Entities | type rows, expandable to positions | render µs/frame, tick µs/frame, share %, peak frame ms, gc f, shadow %, vertices/frame, calls |
| Entities | type rows, expandable to individuals | same |
| Particles | class rows | render µs/frame, tick µs/frame, share %, avg live count, vertices/frame, gc f |
| Blocks | block rows (Sodium only) | mesh ms/s, µs/call, calls |
| Sections | section rows (Sodium only) | rebuilds, rebuilds/s, mesh ms total, avg ms/rebuild |
| Flywheel | one totals row plus visual class rows | bucket µs/frame, share %, visual count |

`peak ms` reads `-` when every frame was GC-suspect.

Every list sorts by any numeric column (implemented as a cycling Sort button on the Block
Entities, Entities and Particles tabs; the Frames, Hitches, Flywheel, Blocks and Sections tabs
have a fixed order). Position and individual rows have **Highlight** (draw
an outline box at the position for 10 s) and **Teleport** (sends `/tp @s x y z` as chat; if the
player lacks permission the server's error is what they see). A **Copy report** button copies
the text report; **Open folder** opens the report directory.

### 5.4 In-world overlay

Drawn in `RenderLevelStageEvent` after particles. Floating text labels, using the same
world-to-screen approach as vanilla's debug renderer, for:

- the top `overlayLabelCount` (default 25) block entities and entities by render + tick
  µs/frame within `overlayMaxDistance` (default 64 blocks),
- the top rebuilding sections (label at section centre) when Sodium hooks are active.

Label text: `create:mechanical_press 412 µs` coloured on a green to yellow to red scale by
share of frame time. Any key that appeared in a hitch's `top` list gets a leading `!` and is
drawn red regardless of average share. The overlay shows the last finished session and turns
off when a new session starts.

## 6. Reports

Both files are written to `<gameDir>/renderable/` on every finished session, named
`renderable-YYYY-MM-DD_HH-mm-ss.txt` and `.json`. The chat message at session end has three
clickable actions: **Open results**, **Copy report**, **Open folder**.

### 6.1 Text report

Sections, in order:

1. **Header**: mod version, timestamp, session length.
2. **Environment**: Minecraft, NeoForge, Java version, max heap, mod count; GPU renderer and
   version strings from OpenGL; render distance, simulation distance, biome blend, graphics
   mode, vsync, FPS limit, particles setting, entity distance, fullscreen resolution and GUI
   scale; Sodium, Iris (and active shader pack), Flywheel (and backend), Accelerated Rendering,
   Distant Horizons present or not; dimension, player position, loaded section count; which
   Renderable hooks applied.
3. **Frames**: frames, avg ms and FPS, p50, p95, p99, max; hitch threshold and count and total
   hitch time as a share of wall time; average bucket split per frame including GC and swap; a
   note beside the GC line, printed whenever the session saw a collection, explaining that `gc f`
   counts frames a pause could account for, that those are left out of `peak ms`, and that `-`
   means none was clean; Renderable's own estimated overhead.
4. **Hitches**: up to 25 worst, each on two lines: frame and time, total, bucket split; then top
   contributors with their milliseconds in that frame, with a contributor at least as large as
   that frame's GC pause marked `[gc]`. A note under the section heading explains the marker,
   printed only when the session has at least one hitch.
5. **Block entities by type** (top 20) and **by position** (top 20), and the count of GC-suspect frames.
6. **Entities by type** (top 20) and **individuals** (top 20), and the count of GC-suspect frames.
7. **Particles** (top 20), and the count of GC-suspect frames.
8. **Blocks** (top 20 by mesh ms/s) and **Sections** (top 20 by rebuilds), or one line saying
   Sodium hooks were not active.
9. **Flywheel**: bucket share and visual counts.

Tables are fixed-width plain text so they survive being pasted into a GitHub issue inside a
code fence.

### 6.2 JSON

```
{
  "schema": 4,
  "mod": { "version": "0.4.0" },
  "environment": { ... same fields as the text header, typed ... },
  "session": { "startedAt": iso8601, "durationSeconds": 30.0, "frames": 2412,
               "hitchThresholdMs": 33.2, "overheadEstimateMs": 0.4, "timedCalls": 6947008,
               "vertexCalls": 1204331 },
  "frames": [ { "i": 0, "totalUs": ..., "tickUs": ..., "renderUs": ..., "bucketsUs": { ... },
                "gcCount": 0, "gcMs": 0, "top": [ { "key": "...", "us": ..., "gc": false } ] }, ... ],
  "hitches": [ frame indices ],
  "blockEntities": { "byType": [ ... ], "byPosition": [ ... ] },
  "entities": { "byType": [ ... ], "individuals": [ ... ] },
  "particles": [ ... ],
  "blocks": [ { "key": "block:id", "calls": n, "meshUs": ..., "usPerCall": ...,
                "msPerSecond": ..., "callsPerSecond": ... } ],
  "sections": [ { "key": "section@dim:x,y,z", "dimension": ..., "x": ..., "y": ..., "z": ...,
                  "calls": ..., "meshUs": ..., ... } ],
  "flywheel": { "bucketUs": ..., "sharePercent": ..., "samples": ..., "avgBlockEntities": ...,
                "avgEntities": ..., "avgEffects": ..., "visualCounts": { "class": avg } }
}
```

JSON is complete, not truncated to top-N. Keys serialise as strings of the form
`type@dim:x,y,z`, `type#entityId`, `particle:ClassName`, `block:id`, `section@dim:x,y,z`.

Every row of `blockEntities`, `entities` and `particles` carries `gcFrames`, the count of frames the key
appeared in that were GC-suspect; its `peakFrameUs` is the largest **clean** frame, `0` when `gcFrames`
covers all of them. Every entry of `frames[].top` carries `gc`, set when that entry's nanoseconds are
GC-suspect for the frame it belongs to.

The JSON is streamed to the file a row at a time; the report is never held in memory as a tree or as a
string. Every array is complete, as above.

## 7. Configuration

NeoForge client config `renderable-client.toml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `defaultDurationSeconds` | 30 | keybind and bare `start` duration |
| `openResultsOnFinish` | true | open the screen when a session ends |
| `hitchMedianMultiplier` | 2.0 | see 3.3 |
| `hitchMinExcessMs` | 16 | see 3.3 |
| `topContributorsPerFrame` | 5 | keys kept per frame |
| `overlayEnabled` | true | draw labels for the last session |
| `overlayLabelCount` | 25 | labels per category |
| `overlayMaxDistance` | 64 | blocks |
| `writeText` / `writeJson` | true / true | which files to write |
| `reportFolder` | `renderable` | relative to the game directory |
| `debugCommands` | false | enables `/renderable debug` |

## 8. Code structure

```
renderable/
  core/      SessionConfig, Session, FrameRecord, Bucket, keys, Accumulator, HitchDetector,
             Ranking, ReportModel, Environment (a plain data record)
  hooks/     Probe, mixins/, plugin/ (RenderableMixinPlugin), compat/IrisCompat,
             compat/FlywheelCompat, compat/SodiumCompat
  ui/        Keybinds, RenderableCommand, SamplingHud, ResultsScreen, tabs/, WorldOverlay
  report/    TextReportWriter, JsonReportWriter, ReportFiles, EnvironmentCollector
  RenderableMod (entry point, config registration, event bus wiring)
```

Dependency direction is `ui`, `report`, `hooks` -> `core`. `core` and the writers in `report`
have no Minecraft imports; `EnvironmentCollector` is the only Minecraft-aware class in
`report` and produces the plain `Environment` record that the writers consume.

## 9. Testing

**Unit (JUnit 5, no Minecraft):**

- `Accumulator`: per-frame reset, touched-list bookkeeping, nested-call depth handling, shadow
  and tick columns.
- `Session`: frame boundaries, bucket arithmetic, derived `other` and `swap` values never
  negative (clamped and flagged if the hooks disagree).
- `HitchDetector`: threshold maths at several medians, sorting, top contributors carried
  through.
- `Ranking`: type roll-ups equal the sum of their positions; sort stability.
- `TextReportWriter`: fixed-width alignment, top-N truncation, "Sodium hooks not active" path.
- `JsonReportWriter`: schema field presence, key string round trip through a parser.

**In-game (dev client with this pack's pinned Sodium, Iris, Create, Enchantable Machinery):**

1. `/renderable debug stall 80` during a session produces exactly one hitch whose top
   contributor is `renderable:debug_stall` at about 80 ms.
2. Standing at a base with Enchantable Machinery machines, its renderers rank at the top of the
   Block Entities tab and carry labels in the overlay.
3. Toggling Iris shaders changes the shadow column from zero to non-zero without changing which
   renderers rank first.
4. Flying into unloaded terrain produces hitches attributed mostly to chunk upload, with the
   Sections tab showing the newly built sections (phase 3).
5. Idle cost: measured with Java Flight Recorder in a static superflat scene, four runs of 120 s (jar
   absent and present, each with and without `-XX:-DoEscapeAnalysis`). With escape analysis on, allocation
   and mean CPU load with the jar must be within 2% of without it, and fewer than 0.1% of execution samples
   may contain a `io.github.edwardg117.renderable` frame. With escape analysis off, the jar's run must
   allocate more than 2% above the jar-free run, which is the control proving the measurement is sensitive
   enough to support the first claim.

   **Still not run, as of `v0.4.0`.** This check has been outstanding since phase 1, which recorded it as
   NOT VERIFIED because frame time at the pack's base fluctuates more than the effect being looked for.
   Phase 4a replaced the criterion with the protocol above but did not execute it, so the README's
   "costs nothing when it is not sampling" remains a design claim rather than a measured one. When it is
   run, the results belong in `docs/acceptance/phase-4-idle.md`.

## 10. Repository, build, release

- `H:\repos\Renderable`, pushed to `github.com/edwardg117/Renderable`, MIT license.
- ModDevGradle 2.x, `neoforge = 21.1.220`, Java 21 toolchain, Gradle wrapper committed.
- Optional dependencies from the Modrinth Maven (`https://api.modrinth.com/maven`) as
  `compileOnly` and `localRuntime` for the dev client: Sodium `0.8.13+mc1.21.1`, Iris
  `1.8.14-beta.1+mc1.21.1`, Create `1.21.1-6.0.10` (for Flywheel and to test against real
  machines), Create: Enchantable Machinery `3.6.0+mc1.21.1-neoforge` plus Kotlin for Forge.
- GitHub Actions: `build.yml` runs `gradle build` on push and pull request; `release.yml` runs
  on `v*` tags, builds, and attaches `renderable-<version>.jar` to a GitHub Release.
- `neoforge.mods.toml` declares the mod client-only (`displayTest = "IGNORE_ALL_VERSION"`,
  server side ignore) so it never blocks joining a server that lacks it.

When the mod graduates from dev-only, the pack gets a `side="CLIENT"` module pointing at the
release asset URL, alongside CreateBetterFps. That step is not part of this design.

## 11. Phases

Each phase is its own implementation plan and its own release tag.

| Phase | Delivers | Verifies |
| --- | --- | --- |
| 1 | repo and CI, `core`, frame model with tick/render/swap/GC, block entity and entity render hooks, hitch log, keybind and command, HUD, results screen with Frames, Hitches, Block Entities and Entities tabs, overlay, text and JSON export, debug stall | in-game checks 1, 2, 5 |
| 2 | particle render and tick, block entity and entity client tick, Flywheel bucket and counts, light bucket, Iris shadow split, Particles and Flywheel tabs | in-game check 3 |
| 3 | Sodium mesh per block and per section, chunk upload bucket, vertex counts, Blocks and Sections tabs | in-game check 4 |
| 4a | GC-suspect values excluded from per-key and per-type peaks and flagged in every output, type peaks taken from the type's worst frame, Accelerated Rendering vertices counted, the overhead estimate extended to vertex calls and its calibration corrected, JSON schema 4 | in-game checks 1 to 4; check 5 (idle cost) still not run |

Phase 4 is four independently executable plans rather than one. Group A ships alone as `v0.4.0`; groups B
(robustness and cost), C (screen and overlay) and D (hygiene and CI) follow in any order, each with its own
commits, and are described in `docs/superpowers/specs/2026-09-20-renderable-phase-4-design.md`.
