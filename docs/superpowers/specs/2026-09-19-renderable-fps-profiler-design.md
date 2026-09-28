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
| `index`, `startNanos` | frame counter, `System.nanoTime()` at frame start, just before NeoForge posts `RenderFrameEvent.Pre` |
| `totalNanos` | start of this frame to start of the next |
| `tickNanos` | sum of client ticks that ran inside this frame, each from just before `ClientTickEvent.Pre` is posted to just after `ClientTickEvent.Post` |
| `renderNanos` | frame start to just after `RenderFrameEvent.Post` is posted |
| `tasksNanos` | time in `Minecraft.runAllTasks`: packets and other queued main-thread work (phase 5) |
| `capNanos` | time in `RenderSystem.limitDisplayFPS`: the wait for the fps limit (phase 5) |
| buckets | `BLOCK_ENTITY`, `ENTITY`, `PARTICLE_RENDER`, `FLYWHEEL`, `CHUNK_UPLOAD`, `LIGHT`, `HUD`, `PARTICLE_TICK`, `BE_TICK`, `ENTITY_TICK`, each a nanosecond sum for this frame |
| `namedNanos` | per window (render, tick, tasks), the time of the outermost attribution-only calls (§3.4) that no bucket-owning call encloses, less the bucket time inside them (phase 5) |
| `gcCount`, `gcMillis` | delta since the previous frame of the collection count and time summed over the collectors' pause beans: all of them under G1, Parallel and Serial, and only the `Pauses` beans under ZGC and Shenandoah, whose `Cycles` beans time concurrent work (phase 5) |
| `top` | up to `topContributorsPerFrame` (default 5) keys with the most nanoseconds in this frame, across all timed keys |

Derived values, computed at report time and never stored twice:

- `otherRenderNanos = renderNanos - (BLOCK_ENTITY + ENTITY + PARTICLE_RENDER + FLYWHEEL + CHUNK_UPLOAD + LIGHT + HUD)`
- `otherTickNanos = tickNanos - (PARTICLE_TICK + BE_TICK + ENTITY_TICK)`
- `swapNanos = totalNanos - tickNanos - renderNanos - tasksNanos - capNanos` (the buffer swap and any vsync wait, the blit, the sound update, mouse turning and the yield)

Render buckets nest inside `renderNanos` and tick buckets inside `tickNanos`. Nothing is
counted in two buckets. When a timed call nests inside another timed call (an item rendered by
a block entity renderer), the inner call is attributed to the innermost key and the outer key's
time includes it; buckets are only incremented by the outermost call that owns a bucket, tracked with a depth
counter. Attribution-only calls (§3.4) own none, so a block entity drawn inside an event handler is still
block entity time.

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
| `EntityKey` | entity type id, entity id; for the report, a non-player entity's custom name, "you" for the local player, and no name for another player (phase 5) | render (phase 1), client tick (phase 2) |
| `EntityTypeKey` | entity type id | roll-up |
| `ParticleKey` | particle class name | render and tick (phase 2) |
| `BlockKey` | block id | Sodium mesh build (phase 3) |
| `SectionKey` | dimension, section position | Sodium mesh build (phase 3) |
| `ModKey` | kind (event, layer, packet), owning mod id, event class or id | event handlers, HUD layers, packets (phase 5); attribution-only |

Flywheel visual counts are averaged per class in `FlywheelStats`, not keyed accumulators.

A mod's event handlers, HUD layers and packets are **attribution-only** (phase 5, spec addendum §P5.A6):
their keys (`ModKey`, category `MOD`, type id the mod's id) are timed, ranked, peaked and GC-flagged like any
other, but they own no bucket, and vertices emitted inside one land on the innermost call that does own a
bucket. The time the outermost attribution-only calls account for outside any bucket is recorded per frame
window as `namedNanos`, so a `ChunkEvent.Load` handler run inside a chunk packet's handling is not counted
twice. Having no bucket to say which side it ran on, an attribution-only call is tick time on its key inside
the client tick and render time anywhere else, packets included.

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

- Every hook first reads one `static volatile boolean ACTIVE`. When it is false, the hook returns, and a
  hook that wraps a call makes it at once, handing it the arguments in an array for the JIT's escape analysis
  to remove. §9 check 5 measures what is left.
- Event timing (§4) has no idle cost at all: its proxies exist only while a session runs.
- No allocation on hot paths while sampling: accumulators are created on first sight of a key
  and reused; the touched-list is a pre-sized array list of references.
- Per-particle timing costs two clock reads per particle. With 10,000 particles that is roughly
  0.5 ms per frame while sampling. The report states the session's own estimated overhead, each kind of
  call at its own measured cost: timed pairs that own a bucket, attribution-only pairs (event handlers, HUD
  layers, packets) and vertex-counting calls. The three constants are calibrated once, before the first
  session, against a scratch session (phase 5, spec addendum §P5.C2). Each is the fastest of five rounds of
  10,000 whole calls, guards included: timed pairs and vertices through the same static `Probe` methods the
  mixins call, and attribution-only pairs through an event-timing proxy. The log records them beside what
  `Session` alone costs. The reader can discount the estimate.
- `BufferBuilder.beginVertex` (and Sodium's `push`, and Accelerated Rendering's builder) get one branch on
  the idle flag plus one increment while sampling.
- The mod's own in-frame work — the sampling HUD line, the once-a-second Flywheel visual sample and the
  once-a-second event-list check — is timed rather than estimated and reported separately. The HUD line is
  inside the `HUD` bucket and the rest is in other render.

## 4. Hooks

All mixins call into one static `Probe` class in `hooks`. `Probe` owns `ACTIVE`, the current
attribution stack (a small array, depth counter), and forwards to the live session in `core`.
Every timed hook is wrapped in try/finally so a renderer throwing never leaves a key on the
stack.

Mod event handlers are the exception: they are timed without a mixin, because NeoForge's event bus loads in
the boot module layer, where no mixin reaches. While a session runs, every listener list of the game bus has
its cached array swapped for timing proxies, and back when the session ends (§4.6, spec addendum §P5.A3).

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
| calls to `ClientHooks.fireRenderFramePre` / `fireRenderFramePost` inside `Minecraft#runTick(boolean)` | `@Inject` just before / just after (listeners of the two events until phase 5) | frame boundary, `renderNanos`, per-frame reset and top-N scan |
| calls to `ClientHooks.fireClientTickPre` / `fireClientTickPost` inside `Minecraft#tick()` | `@Inject` just before / just after (listeners until phase 5) | `tickNanos` |
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

**A riding entity's tick.** `ClientLevel.tickEntities` routes only non-passengers through `guardEntityTick`;
a passenger is ticked from its vehicle through `ClientLevel#tickPassenger`, which calls `Entity#rideTick()`.
Until phase 5 that tick was folded into the vehicle's with nothing to say so: in a 2026-09-20 flight the
player rendered 4,983 times and ticked zero times. Phase 5 hooks the call (§4.6), so the rider's own row
carries its tick time, and the vehicle's row still includes it, nested inclusively like every other call.

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
- Third-party mixins are `require = 0`. If one of their injectors misses its target after a version bump, the
  client keeps running and nothing is logged, but the report lists the hook among those that did not apply,
  and a table it leaves empty says so. Mixin does log a third-party mixin it rejects as a whole, whose hooks
  then read as never loaded (phase 5, below).
- Vanilla mixins are required; if they fail the mod is not usable and should fail loudly.
- The report's environment header lists hooks three ways (phase 5, spec addendum §P5.D1).
  - **Installed.** As each hooked class loads, `RenderableMixinPlugin.postApply` checks that every injector's
    target method is in it and, for an `INVOKE` injection point, that the call is too. It checks the target
    rather than the handler, because MixinExtras applies `@WrapOperation` and `@WrapMethod` after `postApply`.
  - **Fired.** `Probe` records each hook the first time it runs while a session is sampling.
  - **Did not apply.** An injector's target was missing.

  A hook whose classes never loaded is in none of the three; nor are `IRIS_SHADOW` and `EVENTS`, which are not
  mixins, until they fire. Sodium's empty tables tell "the hook did not apply", "the code never ran" and
  "nothing happened" apart; the ranked and particle tables tell only the first (spec addendum §P5.D2).
  `HookInstalls.HOOKS` maps each injector to its hook, apart from the four frame and tick boundaries in
  `HookInstalls.BOUNDARIES`, which are not checked; `HookInstallsTest` fails for an injector in neither.

### 4.6 Phase 5: attribution

| Target | Kind | Records |
| --- | --- | --- |
| call to `Minecraft#runAllTasks()` inside `Minecraft#runTick(boolean)` | `@WrapOperation` | `tasksNanos` |
| call to `RenderSystem#limitDisplayFPS(int)` inside `Minecraft#runTick(boolean)` | `@WrapOperation` | `capNanos` |
| `Gui#render(GuiGraphics, DeltaTracker)` | `@WrapMethod` | `HUD` bucket |
| call to `LayeredDraw$Layer#render` inside `GuiLayerManager#renderInner`, with the loop's `NamedLayer` | `@WrapOperation` with `@Local` | `ModKey` layer time, attribution-only |
| call to `Packet#handle(PacketListener)` inside `PacketUtils.lambda$ensureRunningOnSameThread$0` | `@WrapOperation` | `ModKey` packet time, attribution-only |
| call to `IPayloadHandler#handle` inside `MainThreadPayloadHandler.lambda$handle$0` | `@WrapOperation` | `ModKey` packet time for mod payloads, attribution-only |
| call to `Entity#rideTick()` inside `ClientLevel#tickPassenger(Entity, Entity)` | `@WrapOperation` | the rider's `EntityKey` tick time, nested in its vehicle's |
| the cached array of every `ListenerList` on `NeoForge.EVENT_BUS` | swapped for `TimedListener` proxies while a session runs; not a mixin | `ModKey` event time per owning mod and event class, attribution-only |

The spec addendum's §P5.A0 records the bytecode offsets these were verified at.

## 5. User interface

### 5.1 Trigger and control

- Keybind **F6** (rebindable; until phase 5 it was F7, which clashed with Lighty's key in the pack): idle
  starts a session with the default duration; sampling stops it early.
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
/renderable debug events          (only when config debugCommands = true)
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
| Advice | the recommendations, the player's first, each wrapped as a title and two short paragraphs | none; the screen opens on this tab |
| Frames | summary block plus a frame-time histogram | sampled seconds, frames, avg, p50, p95, p99, max, threshold, average bucket split |
| Hitches | one per hitch | frame index, time into session, total ms, bucket split bar, GC delta, top contributors (a GC-suspect contributor's **split** cell reads `[gc]` in place of the bar, since the name cell is the one the list truncates) |
| Mods | one row per mod, expandable to its event types, HUD layers and packets | share %, µs/frame, peak frame ms, gc f, calls/frame |
| Block Entities | type rows, expandable to positions | render µs/frame, tick µs/frame, share %, peak frame ms, gc f, shadow %, vertices/frame, calls |
| Entities | type rows, expandable to individuals | same |
| Particles | class rows | render µs/frame, tick µs/frame, share %, avg live count, vertices/frame, gc f |
| Blocks | block rows (Sodium only) | mesh ms/s, µs/call, calls |
| Sections | section rows (Sodium only) | rebuilds, rebuilds/s, mesh ms total, avg ms/rebuild |
| Flywheel | one totals row plus visual class rows | bucket µs/frame, share %, visual count |

`peak ms` reads `-` when every frame was GC-suspect.

The Mods, Block Entities, Entities, Particles, Blocks and Sections lists sort by any column; the Flywheel list
sorts by name or average count only, its share column being the bucket's own share of frame time rather than
a per-row value. Each opens in the order the text report uses; clicking a column header sorts by it (largest
first for a number, A to Z for a name, ignoring case and reading digit runs as numbers since phase 5),
clicking the active header reverses it, and the active header carries an arrow. The Advice, Frames and Hitches
tabs have a fixed order. A number too wide for its column is rounded to a whole number and, if it still does
not fit, drawn as `#`s; it is never cut short. Position rows have **Highlight** (draw a wireframe box at the
position for 10 s, the whole 16-block section for a Sections row), and position and individual rows have
**Teleport** (sends `/tp @s x y z` as chat; for a section row, at the player's own
height raised to the first height at which the player fits in that column, so they do not arrive inside
loaded terrain, and at the player's own height where the client has not loaded that column; if the player
lacks permission the server's error is what they see). Both are disabled, with a
tooltip saying why, while the selected row's dimension (a block entity's or section's own, an entity's the
session's) is not the player's, and for good once the player logs out of the world the session was taken in;
a highlight is drawn only in its row's dimension. The screen keeps its tab, each tab's sort, the open groups,
the selected row and the scroll position for the latest result, so closing it for Highlight or Teleport does
not lose the reader's place; a new result opens on Advice (phase 5). A
**Copy report** button copies the text report; **Open folder** opens the report directory. The Sections list
shows at most 200 sections, the first 200 in its current order, and a trailing row saying how many it left out
and that the JSON has them all. The ranked and particle cell text is defined once, in `core/RankedTable`, and
shared with the text report, as the Blocks and Sections cell text is in `core/MeshTable` (phase 5); the screen
writes its column names out in `Tab`, and `TabTest` holds them equal to those classes'. A header too wide for
its column widens it into the name column, which gives up at most a quarter of its width in all; a header that
still does not fit is cut short (phase 5).

### 5.4 In-world overlay

The camera and view-projection matrix are captured in `RenderLevelStageEvent` after particles, and the
overlay is drawn in a GUI layer above everything, so it shows through walls and shader packs (phase 1 moved it
there after Iris shaders hid the in-world version). Floating text labels, each drawn only in the dimension the
session ran in, projected with the same world-to-screen approach as vanilla's debug renderer, for:

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
2. **Recommendations**: up to six, most important first (stutter, then frame rate, then notes), each tagged
   with its audience, with what was measured and what to do; or one line saying none crossed a threshold.
3. **Environment**: Minecraft, NeoForge, Java version, max heap, mod count; physical memory, the collector,
   the initial heap, the smallest heap in use after a collection and the whitelisted JVM flags; GPU renderer
   and version strings from OpenGL; render distance, simulation distance, biome blend, graphics
   mode, vsync, FPS limit, particles setting, entity distance, fullscreen resolution and GUI
   scale; Sodium, Iris (and active shader pack), Flywheel (and backend), Accelerated Rendering,
   Distant Horizons present or not; dimension, player position, loaded section count; which
   Renderable hooks are installed, which fired and which did not apply.
4. **Frames**: frames, avg ms and FPS, p50, p95, p99, max; hitch threshold and count and total
   hitch time as a share of wall time; average split per frame: tick, the buckets (the HUD among them),
   other render, tasks, the frame cap and swap, leaving out a bucket, tasks or the frame cap that took no
   time, with the part of tick, other render and tasks that the Mods section names in brackets; the GC count
   and pause time, with a note under that line, printed whenever the session saw a collection, explaining that
   `gc f` counts frames a pause could account for, that those are left out of `peak ms`, and that `-` means
   none was clean; Renderable's own estimated overhead; and the measured time of its own in-frame work
   (§3.5), when it had any.
5. **Hitches**: up to 25 worst, each on two lines: frame and time, total, bucket split; then top
   contributors with their milliseconds in that frame, with a contributor at least as large as
   that frame's GC pause marked `[gc]`. A note under the section heading explains the marker,
   printed only when the session has at least one hitch.
6. **Mods** (top 15, each with its top 3 event types, HUD layers or packets), under a note that counting is
   inclusive and that a library carries the handlers it runs for other mods; or one line saying why there are
   none, and a line above the table when event timing was off.
7. **Block entities by type** (top 20) and **by position** (top 20), and the count of GC-suspect frames. The
   shadow column is left out of a table in which no row has shadow time (phase 5).
8. **Entities by type** (top 20) and **individuals** (top 20), and the count of GC-suspect frames, with the
   shadow column as for block entities.
9. **Particles** (top 20), by short class name as the Flywheel table names classes (phase 5; the JSON keeps
   full names), and the count of GC-suspect frames.
10. **Blocks** (top 20 by mesh ms/s) and **Sections** (top 20 by rebuilds), or one line saying why
   there are none: Sodium is not installed, its mesh hook did not apply or never loaded, or nothing was
   meshed or rebuilt while sampling.
11. **Flywheel**: bucket share and visual totals, then visual counts by class or a line saying the samples
    found no visuals alive; or one line saying why there is no data.

Tables are fixed-width plain text so they survive being pasted into a GitHub issue inside a
code fence.

### 6.2 JSON

```
{
  "schema": 5,
  "mod": { "version": "0.5.0" },
  "environment": { ... same fields as the text header, typed ... },
  "session": { "startedAt": iso8601, "durationSeconds": 30.0, "frames": 2412,
               "hitchThresholdMs": 33.2, "overheadEstimateMs": 0.4, "timedCalls": 6947008,
               "vertexCalls": 1204331 },
  "stats": { "avgMs": ..., "avgFps": ..., "p50Ms": ..., "p95Ms": ..., "p99Ms": ..., "maxMs": ..., ... },
  "recommendations": [ { "rule": "gc-pauses", "kind": "STUTTER", "audience": "PLAYER", "title": ...,
                         "finding": ..., "action": ..., "msPerFrame": ..., "worstMs": ... } ],
  "frames": [ { "i": 0, "totalUs": ..., "tickUs": ..., "renderUs": ..., "tasksUs": ..., "capUs": ...,
                "namedUs": { "render": ..., "tick": ..., "tasks": ... }, "bucketsUs": { ... },
                "gcCount": 0, "gcMs": 0, "top": [ { "key": "...", "us": ..., "gc": false } ] }, ... ],
  "hitches": [ frame indices ],
  "mods": [ { ...a ranked row for the mod..., "entries": [ ...ranked rows... ] } ],
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

The environment's `hooksInstalled`, `hooksFired` and `hooksFailed` arrays list hooks by name; schema 4 called
`hooksFired` `hooksApplied` (phase 5).

JSON is complete, not truncated to top-N. Keys serialise as strings of the form
`type@dim:x,y,z`, `type#entityId`, `particle:ClassName`, `block:id`, `section@dim:x,y,z`,
`event@mod:class`, `layer@mod:id`, `packet@mod:id`.

Every row of `blockEntities`, `entities`, `particles` and `mods`, and of each mod's `entries`, carries
`gcFrames`, the count of frames the key appeared in that were GC-suspect; its `peakFrameUs` is the largest
**clean** frame, `0` when `gcFrames` covers all of them. Every entry of `frames[].top` carries `gc`, set when
that entry's nanoseconds are GC-suspect for the frame it belongs to.

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
- `TextReportWriter`: fixed-width alignment, top-N truncation, and each table's empty-state sentence.
- `JsonReportWriter`: schema field presence, key string round trip through a parser.
- Phase 5: event timing's listener swap against a real `net.neoforged:bus`; each recommendation rule against
  `Facts` built in the test, the facts a session yields, and the ranking; the four collectors' bean names and
  the JVM flags the environment keeps; the mixin plugin's target check against small ASM class nodes and our
  own mixins' class files; natural name order, short class names, the mesh table cells, the column layout and
  the Advice tab's rows and wrapping; the calibration loop with a fake clock; and a player's key never
  carrying another player's name.

**In-game (dev client with this pack's pinned Sodium, Iris, Create, Enchantable Machinery):**

1. `/renderable debug stall 80` during a session produces exactly one hitch whose top
   contributor is `renderable:debug_stall` at about 80 ms.
2. Standing at a base with Enchantable Machinery machines, its renderers rank at the top of the
   Block Entities tab and carry labels in the overlay.
3. Toggling Iris shaders changes the shadow column from zero to non-zero without changing which
   renderers rank first.
4. Flying into unloaded terrain produces hitches attributed mostly to chunk upload, with the
   Sections tab showing the newly built sections (phase 3).
5. Idle cost: measured with Java Flight Recorder in a static superflat scene with twenty `NoAI` villagers in
   view, so the entity hooks, the busiest, run every frame, and with the pack's rendering mods (phase 5, spec
   addendum §P5.C1). Four runs of 120 s: Renderable absent and loaded, each with and without
   `-XX:-DoEscapeAnalysis`.
   - With escape analysis on, allocation and mean CPU load with Renderable must be within 2% of without it.
   - Fewer than 0.1% of execution samples may have Renderable's code on top: a class in its package, or a mixin
     handler, which Mixin merges into the hooked class under a name that contains `renderable$`.
   - With escape analysis off, the handlers must allocate, which is the control proving the measurement sees
     them. With it on, they must allocate at most a tenth of that.

   The protocol, the scene and the numbers script are in `docs/acceptance/phase-5-idle.md`, whose Results
   section records the runs.

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
| 5a | frame components (tasks, frame cap, HUD), frame and tick boundaries at the `Minecraft` call sites, mod event handlers through per-session listener proxies, HUD layers, packets and payloads, rider ticks, attribution-only calls and named time, the Mods tab and report section, JSON schema 5 | spec addendum §P5.A's checks, in the in-pack acceptance at the end of phase 5 |
| 5b | the recommendation engine and its nineteen rules, the environment's memory and JVM facts, pause-only GC sampling, the Advice tab, the report's Recommendations section, the chat line's suggestion count, the JSON's `recommendations` | spec addendum §P5.B's checks, in the in-pack acceptance at the end of phase 5 |

Phase 4 is four independently executable plans rather than one. Group A ships alone as `v0.4.0`; groups B
(robustness and cost), C (screen and overlay) and D (hygiene and CI) follow in any order, each with its own
commits, and are described in `docs/superpowers/specs/2026-09-20-renderable-phase-4-design.md`.

Phase 5 is four plans, 5a (attribution), 5b (recommendations), 5d (polish) and 5c (readiness), run in that
order and released together as `v0.5.0`; see `docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md`.
