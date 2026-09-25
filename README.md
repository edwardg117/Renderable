# Renderable

A client-side render profiler for NeoForge 1.21.1. Observable tells you which block entities cost
server tick time; Renderable tells you which block entities, entities and effects cost **frame
time**, and what caused each hitch.

## Using it

1. Press **F7** (or run `/renderable start [seconds]`). A line at the top of the screen counts the
   session's elapsed seconds; default 30 s.
2. When it ends, a results screen opens and a chat message offers **Open results**, **Copy
   report** and **Open folder**. Two files land in `<game dir>/renderable/`: a text report you can
   paste into an issue, and a JSON file with everything.
3. In the world, the costliest block entities and entities carry floating labels in green,
   yellow or red, and the most rebuilt chunk sections carry yellow labels at their centres. A
   leading `!` means that thing showed up in a hitch. Labels are shown only in the dimension they
   were profiled in and are dropped when you leave the world. `/renderable overlay off` hides them
   and stays off until you turn it back on, including across later sessions. Editing `overlayEnabled`
   in `renderable-client.toml` while the game is running does nothing: it is read once per client run,
   and the command is what changes it after that.
4. On the results screen, click a column header to sort by it; click it again to reverse. Each list opens in
   the same order as the text report, the active column carries `v` (descending) or `^` (ascending), and a
   name column sorts A to Z on its first click. Frames and Hitches have a fixed order. A number too wide for
   its column is rounded to a whole number, and shows as `###` only if it still does not fit; widen the window
   or lower the GUI scale to read it. **Highlight** draws a wireframe box around the selected block, or around
   the whole 16-block section for a Sections row, for ten seconds and through walls. **Teleport** from a
   Sections row keeps your height, raised until you fit in that column, so it never leaves you inside a hill.
   Highlight and Teleport are greyed out once you leave the world or dimension the session was taken in, and
   hovering over them says so.

Commands: `start [seconds]`, `stop`, `cancel`, `results`, `copy`, `folder`, `overlay [on|off]`.

## Reading a report

- **Frames** gives the frame-time distribution. Trust p95 and p99 over the average.
- **Time split** shows where the average frame went: client tick (with its block entity, entity
  and particle tick shares in brackets), block entities, entities, particles, Flywheel, light
  updates, chunk upload, other render work, and swap/wait (vsync, the GPU, the driver). A big
  swap/wait with a low GPU load is not a rendering problem in the world.
- **Hitches** lists the frames that spiked, each with its split and the things that were most
  expensive in exactly that frame. GC pauses are shown per frame.
- **Block entities** and **entities** are ranked by microseconds per frame; expand a type to see
  each position. The **shadow** column is the share of a renderer's time spent in Iris's shadow
  pass, which draws everything a second time. Shares can add up to more than 100% because a
  renderer's time also counts towards everything it is nested in.
- **Particles** are ranked by class. **avg live** is the average number of that class rendered
  per frame; particles culled before rendering are not counted.
- **Flywheel** shows the instancing bucket and how many visuals of each class were alive, sampled
  once a second. Machines drawn by Flywheel do not appear in the block entity tables. Classes read as
  simple class names on screen and in the text report, while the JSON keeps the fully-qualified names; two
  classes with the same simple name are both prefixed with their last package segment, and shown in full
  if that still leaves two alike.
- **Hooks applied** lists the hooks that have fired at least once since the game started, not
  only during this session.
- **Renderable's own in-frame work** is measured, not estimated: it is the sampling HUD plus the
  once-a-second Flywheel visual sample, and it sits inside **other render**. The in-world overlay is
  cleared when a session starts, so it costs nothing; a highlight left over from an earlier session
  draws out the rest of its ten seconds unmeasured.
- **Blocks** ranks block types by the mesh-build time Sodium's workers spent on them, per
  second of session; **Sections** lists the chunk sections that were rebuilt, with rebuild
  counts. Mesh time is worker-thread time summed across Sodium's workers, so it can exceed 1000
  ms per second and is not a share of frame time. Rebuild storms show up as **chunk upload**
  time in the hitch splits, and rebuilds that Sodium runs on the render thread count in both
  places. Cancelled rebuilds are not counted, and a section's time can exceed the sum of its
  blocks when other mods add geometry to the section, and the blocks can exceed the section
  when a rebuild is cancelled part way through.
- The **Sections** tab shows at most 200 sections, the first 200 in whatever order it is sorted, and says
  how many it left out. The text report still prints its top 20 and the JSON every section.
- **verts/f** is the number of vertices a renderer emitted per frame, the proxy for GPU load. It counts
  emitted vertices: glint and outline passes emit the same geometry again, and Iris shaders roughly
  double it through the shadow pass. With Sodium installed the count relies on Sodium's own vertex
  path being hooked; if that hook did not apply the column reads near zero. Accelerated Rendering, which
  expands model vertices on the GPU, is hooked separately, so the renderers it takes over are counted too;
  another mod that replaces the vertex path the same way would not be, and its renderers would read zero.
- **peak ms** is the worst single frame, and a type row's peak is the whole type's worst frame, not its
  worst member. A stop-the-world GC pause inflates whatever happened to be running, and nothing shorter
  than the pause can have contained it — so a value at least as large as its frame's pause is not allowed
  to set a peak. **gc f** counts the frames dropped that way, and `-` in **peak ms** means every frame was.
  In the hitch list, `[gc]` marks a contributor that is at least as large as that frame's pause.

## Limits

Create machines rendered through Flywheel instancing never reach the vanilla renderer, so they
cannot be timed one by one; the Flywheel tab shows the instancing bucket and visual counts by
class instead. Mesh timing needs Sodium; without it the Blocks and Sections tabs say so, and
Sodium's translucency re-sorts are not timed. Each hook is a single branch when the mod is not
sampling (Sodium's mesh hooks pass an argument array per block model on the worker threads, which
the JIT removes in practice); while sampling, the report states its own overhead, which covers both
timed calls and vertex counting, and separately the measured cost of its own HUD and sampling. The
once-a-second Flywheel visual sample lands in that frame's other render time. Timing is CPU-side only;
vertex counts stand in for GPU cost.

## Report files

Both files go in the folder named by `reportFolder`, which must be a relative path with no `..`
segment; anything else falls back to `renderable` and says so in the log.

The JSON is streamed to disk a row at a time and is never held in memory as a tree or a string, so a long
flight costs disk space rather than heap. Every array is still complete: nothing is truncated to a top-N.

The JSON report carries a `schema` number.

Schema 4 (this version) adds `gcFrames` to every ranked row, `gc` to every entry of `frames[].top`,
`session.vertexCalls` and `session.selfMs`. `peakFrameUs` keeps its name but is now the largest **clean**
frame: a value of `0` with `gcFrames > 0` means every frame the key appeared in was GC-suspect, which is
what the text report prints as `-`.

Schema 3 changed the shape of `blocks` and `sections`: each row is now
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
