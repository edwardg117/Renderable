# Renderable

A client-side render profiler for NeoForge 1.21.1. Observable tells you which block entities cost
server tick time; Renderable tells you which block entities, entities and effects cost **frame
time**, and what caused each hitch.

## Using it

1. Press **F7** (or run `/renderable start [seconds]`). A line at the top of the screen counts down
   the session; default 30 s.
2. When it ends, a results screen opens and a chat message offers **Open results**, **Copy
   report** and **Open folder**. Two files land in `<game dir>/renderable/`: a text report you can
   paste into an issue, and a JSON file with everything.
3. In the world, the costliest block entities and entities carry floating labels in green,
   yellow or red, and the most rebuilt chunk sections carry yellow labels at their centres. A
   leading `!` means that thing showed up in a hitch. Labels are shown only in the dimension they
   were profiled in and are dropped when you leave the world. `/renderable overlay off` hides them.

Commands: `start [seconds]`, `stop`, `cancel`, `results`, `copy`, `folder`, `overlay [on|off]`.

## Reading a report

- **Frames** gives the frame-time distribution. Trust p95 and p99 over the average.
- **Time split** shows where the average frame went: client tick (with its block entity, entity and
  particle tick shares in brackets), block entities, entities, particles, Flywheel, light updates,
  other render work, and swap/wait (vsync, the GPU, the driver). A big swap/wait with a low GPU
  load is not a rendering problem in the world.
- **Hitches** lists the frames that spiked, each with its split and the things that were most
  expensive in exactly that frame. GC pauses are shown per frame.
- **Block entities** and **entities** are ranked by microseconds per frame; expand a type to see
  each position. The **shadow** column is the share of a renderer's time spent in Iris's shadow
  pass, which draws everything a second time. Shares can add up to more than 100% because a
  renderer's time also counts towards everything it is nested in.
- **Particles** are ranked by class. **avg live** is the average number of that class rendered
  per frame; particles culled before rendering are not counted.
- **Flywheel** shows the instancing bucket and how many visuals of each class were alive, sampled
  once a second. Machines drawn by Flywheel do not appear in the block entity tables.
- **Hooks applied** lists the hooks that have fired at least once since the game started, not
  only during this session.
- **Blocks** ranks block types by the mesh-build time Sodium's workers spent on them, per second of
  session; **Sections** lists the chunk sections that were rebuilt, with rebuild counts. Mesh time is
  worker-thread time summed across Sodium's workers, so it can exceed 1000 ms per second and is not a
  share of frame time. Rebuild storms show up as **chunk upload** time in the hitch splits, and rebuilds
  that Sodium runs on the render thread count in both places. Cancelled rebuilds are not counted, and a
  section's time can exceed the sum of its blocks when other mods add geometry to the section.
- **verts/f** is the number of vertices a renderer emitted per frame, the proxy for GPU load. It counts
  emitted vertices: glint and outline passes emit the same geometry again, and Iris shaders roughly
  double it through the shadow pass. With Sodium installed the count relies on Sodium's own vertex
  path being hooked; if that hook did not apply the column reads near zero.

## Limits

Create machines rendered through Flywheel instancing never reach the vanilla renderer, so they
cannot be timed one by one; the Flywheel tab shows the instancing bucket and visual counts by
class instead. Mesh timing needs Sodium; without it the Blocks and Sections tabs say so, and
Sodium's translucency re-sorts are not timed. Each hook is a single branch when the mod is not
sampling (Sodium's mesh hooks pass an argument array per block model on the worker threads, which
the JIT removes in practice); while sampling, the report states its own overhead, which does not
include vertex counting, and the once-a-second Flywheel visual sample lands in that frame's other
render time. Timing is CPU-side only; vertex counts stand in for GPU cost.

## Report files

The JSON report carries a `schema` number.

Schema 3 (this version) changes the shape of `blocks` and `sections`: each row is now
`{key, label, calls, meshUs, usPerCall, msPerSecond, callsPerSecond}`, and section rows also carry
`dimension`, `x`, `y` and `z` in section coordinates. Schema 2 readers that expected ranked-entry
rows there must be updated. Every section rebuilt during the session is written, so a long flight
into new terrain adds a few hundred bytes per section to the file. The root `sections` array is
unrelated to `environment.sections`, which is Sodium's own section statistics string.

Schema 2 added `session.unbalancedFrames`, `callsPerFrame` on every ranked row, and a `flywheel`
object with `bucketUs`, `sharePercent`, `samples`, `avgBlockEntities`, `avgEntities`, `avgEffects`
and `visualCounts` (class to average count, highest first), replacing schema 1's
`flywheel.visuals` array.

## Building

Java 21. `./gradlew build` produces `build/libs/renderable-<version>.jar`.
`./gradlew runClient` starts a dev client; add `-PpackMods` to include Sodium, Iris, Create and
Create: Enchantable Machinery at the versions the Gears & Frontiers pack pins.
Add `-PquickPlay="<save folder>"` to join a world under `run/saves` straight from launch, which is
how the mixins that only load inside a level are checked with `-Dmixin.debug.export=true`.

Design and plans live in `docs/superpowers/`.
