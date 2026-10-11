# Renderable

A client-side render profiler for NeoForge 1.21.1. Observable tells you which block entities cost
server tick time; Renderable tells you which block entities, entities and effects cost **frame
time**, and what caused each hitch.

## For players

Renderable finds what is costing your frame rate: which machines, entities, particles and mods take the time,
and what caused each stutter. It changes nothing in the game.

1. Go where the game feels slow, press **F6**, and keep playing normally for 30 seconds. The key can be changed
   in Options, Controls, Key Binds, under Renderable.
2. When it finishes, a results screen opens on the **Advice** tab: what to change, drawn from what was measured.
   Suggestions under **For you** are yours to try; those under **For the pack author** are for whoever maintains
   the pack.
3. To ask for help, send the pack author the text report: **Copy report** puts it on the clipboard, and
   **Open folder** shows the files in `<game dir>/renderable/`. Send it privately: it holds your position in
   the world. Other players show in it only as numbers, and you as "you"; a mob or statue keeps its name tag.
4. Coloured labels over machines show what the last session measured. `/renderable overlay off` hides them
   until you run `/renderable overlay on`, and the choice is kept across restarts. The same switch is
   `overlayEnabled` in `config/renderable-client.toml`; changing it there, or in a config screen such as
   Configured's, takes effect at once.

It is a diagnostic tool: add it when you want to find what is slowing the game down, and remove it when you
are done. While it is installed and not sampling, each of its hooks does little more than check one flag.

## Using it

1. Press **F6** (or run `/renderable start [seconds]`). A line at the top of the screen counts the
   session's elapsed seconds; default 30 s. The key can be changed in Options, Controls, Key Binds,
   under Renderable.
2. When it ends, a results screen opens on the **Advice** tab (what to change, drawn from what was measured)
   and a chat message offers **Open results**, **Copy report** and **Open folder**. Two files land in
   `<game dir>/renderable/`: a text report you can paste into an issue, and a JSON file with everything.
3. In the world, the costliest block entities and entities carry floating labels in green,
   yellow or red, and the most rebuilt chunk sections carry yellow labels at their centres. A
   leading `!` means that thing showed up in a hitch. Labels are shown only in the dimension they
   were profiled in and are dropped when you leave the world. `/renderable overlay off` hides them
   until you turn them back on, across later sessions and restarts: it sets `overlayEnabled` in
   `renderable-client.toml`, and a change to that setting, in the file or in a config screen, takes effect
   at once.
4. On the results screen, click a column header to sort by it; click it again to reverse. Each list opens in
   the same order as the text report, the active column carries `v` (descending) or `^` (ascending), and a
   name column sorts A to Z on its first click, ignoring case and reading numbers as numbers, so section -10
   comes before section -2. Advice, Frames and Hitches have a fixed order. A header too wide for its column
   widens it a little, at the name column's expense, and is cut short if that is not enough. A number too wide
   for its column is rounded to a whole number, and shows as `###` only if it still does not fit; widen the
   window or lower the GUI scale to read either. **Highlight** draws a wireframe box around the selected
   block, or around the whole 16-block section for a Sections row, for ten seconds and through walls, in that
   row's dimension only. **Teleport** from a Sections row keeps your height, raised until you fit in that
   column if the game has it loaded, so a loaded hill does not swallow you; a column it has not loaded keeps
   your height as it is. Highlight and Teleport are greyed out while the selected row is in another dimension,
   and for good once you log out of the world the session was taken in; hovering over them says which. They
   close the screen, but reopening it with `/renderable results` comes back to the same tab, sort, open
   groups, selection and scroll position, until the next session finishes.
5. The **Mods** tab ranks mods by the time their event handlers, HUD layers, packets and sounds took; click a
   mod to see its event types, layers, packets and sounds.

Commands: `start [seconds]`, `stop`, `cancel`, `results`, `copy`, `folder`, `overlay [on|off]`.

## Reading a report

- **Recommendations** come first: up to six, with the rest on the Advice tab and in the JSON. Each says who
  can act on it (`[player]` or `[pack author]`), what was measured and what to do, and, unless it is a note,
  how much time the finding is about: its worst single occurrence for stutter, milliseconds a frame for frame
  rate. That figure is an upper bound on what the change can save, not a prediction. Stutter comes first,
  then frame rate, then notes: a stall averaged over a session looks small but is what a player notices. The
  rules are generic, not written for any one pack, and their thresholds are first estimates. One is written for one mod:
  it reads the time Renderable measures in Distant Horizons' own drawing, so it can only appear where Distant
  Horizons is installed, and it names that mod's settings to lower.
- **Sounds keep the game waiting** appears when playing sounds takes half a millisecond a frame, or when one
  sound stalls a hitch or recurs in several, or when the sound engine takes a quarter of the frame in three or more
  hitches and in a quarter of them. Each play waits for the sound thread to hand it a channel, and at
  0% the game skips a sound without waiting, so the advice names the Music & Sounds slider to set to 0% for that
  sound's category.
- **A stall Renderable cannot name**, for the pack author, appears after one long stall in code Renderable does
  not time, and **Stutter Renderable cannot name** when such code took at least a quarter of the frame in three
  or more hitches and in a quarter of them. A sampling profiler such as spark names that code.
- A hitch contributor counts toward repeated stutter only when it took at least a quarter of that hitch and
  carries no `[gc]` mark: every hitch's top list also holds the frame's usual costs.
- **Environment** includes physical memory, the garbage collector, the initial heap, the smallest heap in use
  after a collection (an upper bound on the pack's live data), and the JVM flags that set the heap and the
  collector. Physical memory is what the system can use: what is installed, less what the firmware and an
  integrated GPU reserve. The memory advice rounds it to the nearest whole GiB, so a 16 GB PC that reports
  15.8 GiB counts as 16. Only flags on a fixed list are included, never paths or `-D` properties, because the
  full list can carry the player's user name. Under ZGC and Shenandoah, the GC figures count pauses only, not
  the collectors' concurrent cycles. A ZGC allocation stall, or Shenandoah holding back a thread that
  allocates, is not a pause either: its time lands in whatever was running, with no `[gc]` mark.
- **Frames** gives the frame-time distribution. Trust p95 and p99 over the average.
- **Time split** shows where the average frame went: client tick, with its parts in brackets; the chunk-layer
  draws (**terrain**) and Distant Horizons' LOD drawing (**lod**), which lead the render side; block entities,
  entities, particles, Flywheel, light updates, chunk upload, the HUD, other render work, **tasks** (packets and
  other work queued for the main thread), the **frame cap** (the wait for the fps limit, when one is set and
  reached), and swap/wait (the buffer swap and vsync, the GPU and driver, and the rest of the frame loop). A
  bucket, tasks and the frame cap are shown when they took time. The tick's brackets add up to the tick: its
  block entity, entity and particle ticks; **sound**, the sound engine's tick, which also plays the sounds it
  queued; **animate tick**, the drips, smoke and sparkles around the player; `mods x`, the part the **Mods** tab
  names; and **unnamed**, the rest, which a sampling profiler such as spark can break down. `mods x` after other
  render or tasks is likewise the part of it the Mods tab names. **lod** is counted apart from the terrain draw it
  runs inside, so no time is in both. The Frames tab is the exception: it always lists tasks and the frame cap,
  shows the rest of the tick as **other tick**, the Mods tab's part included, and shows that part of other tick,
  other render and tasks as an **in the Mods tab** row under each. Each figure is rounded on its own, so a bracket
  can differ from its total by a tenth or two. A big swap/wait with a low GPU load is not a rendering problem in
  the world.
- **Hitches** lists the frames that spiked, each with its split and the things that were most expensive in
  exactly that frame. GC pauses are shown per frame. A split too long for its column continues on the lines
  under its hitch. Each contributor's time sits under **total ms** and its name in the wide split column.
- **Block entities** and **entities** are ranked by microseconds per frame; expand a type to see
  each position. A riding entity's own row carries its tick time, which its vehicle's row also counts.
  The **shadow** column is the share of a renderer's time spent in Iris's shadow
  pass, which draws everything a second time; the text report leaves the column out of a table in which no row
  has any, as when no shader pack is on. Shares can add up to more than 100% because a
  renderer's time also counts towards everything it is nested in.
- **Particles** are ranked by class, named as the Flywheel classes are (see **Flywheel** below). **avg live**
  is the average number of that class rendered per frame; particles culled before rendering are not counted.
- **Mods** ranks each mod by the time its event handlers, its HUD layers, the packets addressed to it and the
  sounds in its namespace took, in microseconds per frame; expand a mod to see each event type, layer, packet
  type and sound. A sound row, such as `sound create:steam (Blocks)`, is one sound event played in one category,
  named as the Music & Sounds screen names it. It times the whole play, the wait for a channel included, and its
  **calls/f** is plays per frame. A sound only names time: the bucket it was played in keeps it, such as
  **be tick** for a machine's sound. The Frames section's **Sound plays** line sums the sound rows. A play inside
  a packet or handler of the same mod counts in both rows, so a mod's total on the Mods tab can read high: the
  `minecraft` row counts each vanilla sound the server sends in its packet's row and in its sound row. Counting is
  inclusive: a handler's time includes whatever it calls, so a handler that draws an entity counts that
  entity too, and shares overlap with each other and with the other tables. Events are timed whoever posts
  them: NeoForge, the game or another mod. A library that runs other mods' handlers for them, such as
  Architectury, Balm or Puzzles Lib, carries their time. Vanilla layers, packets and sounds appear under `minecraft`
  and NeoForge's own handlers under `neoforge`; a handler Renderable cannot tie to a mod shows under
  `unknown` or its library's name. Handlers are timed by swapping the event bus's listener lists for timing
  wrappers when a session starts and back when it ends, so none of it runs between sessions. A handler
  counts a call whenever the bus reaches it, even when it skips an event another handler cancelled, so
  **calls/f** includes those.
- **Flywheel** shows the instancing bucket and how many visuals of each class were alive, sampled
  once a second. Machines drawn by Flywheel do not appear in the block entity tables. Classes read as
  simple class names on screen and in the text report, while the JSON keeps the fully-qualified names; two
  classes with the same simple name are both prefixed with their last package segment, and shown in full
  if that still leaves two alike.
- **Hooks** are listed three ways in the environment. **Installed** hooks are in place: each class they hook
  has loaded and still has the method and the call the hook needs. **Fired** hooks have run while a session
  was sampling, in any session since the game started, not only this one. A hook that **did not apply** found
  its target changed, usually by a mod update; a table it leaves empty says so. A hook can be both fired and
  not applied when only part of it applied, such as Sodium's mesh hook when one of its three injectors misses
  its target. A hook in none of the lists hooks code that has not loaded in this client run, belongs to a hook
  class Mixin rejected as a whole, or failed its check with an error; the game log records the last two.
  `IRIS_SHADOW` and `EVENTS` are not mixins, so they only ever appear as fired.
- **Renderable's own in-frame work** is measured, not estimated: its HUD line, the once-a-second Flywheel
  sample and the once-a-second event-list check, which starts timing event handlers registered during the
  session. The HUD line sits inside **hud** and the other two inside **other render**. The in-world overlay
  is cleared when a session starts, so it costs nothing; a highlight left over from an earlier session draws
  out the rest of its ten seconds unmeasured.
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
sampling (a hook that wraps a call also hands that call its arguments in an array, for the JIT's escape
analysis to remove; that idle cost was not measured, since Renderable is meant to be removed after a
profile); while
sampling, the report states its own overhead, which covers both
timed calls and vertex counting, and separately the measured cost of its own HUD line, Flywheel sample and
event-list check. The HUD line lands in the hud bucket; the once-a-second Flywheel sample and event-list
check land in that frame's other render time. Timing is CPU-side only;
vertex counts stand in for GPU cost.

## Report files

Both files go in the folder named by `reportFolder`, which must be a relative path with no `..`
segment; anything else falls back to `renderable` and says so in the log.

The JSON is streamed to disk a row at a time and is never held in memory as a tree or a string, so a long
flight costs disk space rather than heap. Every array is still complete: nothing is truncated to a top-N.

The JSON report carries a `schema` number.

Schema 6 (this version) adds four buckets to `avgBucketsMs` and to each frame's `bucketsUs`: `terrain`, `lod`,
`sound` and `animate tick`. Buckets are written by label, never by position. Sound rows sit in a mod's `entries`
like its other rows, keyed `sound@<mod>:<event id> (<category>)`, such as `sound@create:create:steam (block)`. Its
`label` names the category as the Music & Sounds screen does, `(Blocks)`. `terrain` and `lod` are render buckets
and `sound` and `animate tick` tick buckets, so `otherRenderUs` and `avgOtherRenderMs` no longer include the first
two, nor `avgOtherTickMs` the last two. There is no per-frame `unnamed` field: it is
`max(0, max(0, tickUs − the tick buckets) − namedUs.tick)`. Nothing else changes shape.

Schema 5 added `tasksUs`, `capUs` and `namedUs` (`render`, `tick` and `tasks`, empty ones left out)
to every frame; `avgTasksMs`, `avgCapMs` and `avgNamedMs` (all three windows, zeros kept) to `stats`; a `hud`
bucket; `environment.eventTiming` (`on`, or `unavailable: ` and why);
a `mods` array, one ranked row per mod followed by an `entries` array of its event, layer and packet rows;
a `recommendations` array, right after `stats`, holding every recommendation with its `rule`, `kind`,
`audience`, `title`, `finding`, `action`, `msPerFrame` and `worstMs`; and `physicalMemoryMiB`, `gc`,
`initialHeapMiB`, `liveHeapAfterGcMiB` (left out when the session saw no collection) and `jvmFlags` in
`environment`.
As on every ranked row, a mod's `members` is a count and its `callsPerFrame` counts only `calls`; `calls`
plus `tickCalls` is its total. A mod has no bucket to say which side it ran on, so its `tickUs` and
`tickCalls` hold what ran inside the client tick, and its `renderUs` and `calls` everything else, packets
included.
`swapUs` no longer includes tasks or the frame cap, and tick and render now include every mod's handlers of
the client-tick and frame events, so a reader that derived swap from the other fields must also subtract
`tasksUs` and `capUs`, or read `swapUs`. `environment.hooksApplied` is renamed `hooksFired`, and
`hooksInstalled` and `hooksFailed` join it.

Schema 4 added `gcFrames` to every ranked row, `gc` to every entry of `frames[].top`,
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
`./gradlew runClient` starts a dev client; add `-PpackMods` to include Sodium, Iris, Create,
Create: Enchantable Machinery and Distant Horizons at the versions the Gears & Frontiers pack pins.
Add `-PquickPlay="<save folder>"` to join a world under `run/saves` straight from launch, which is
how the mixins that only load inside a level are checked with `-Dmixin.debug.export=true`.
Add `-Drenderable.autostart=<seconds>` to the client's JVM arguments (for example through
`JAVA_TOOL_OPTIONS`) to run one session of that length, once per launch, ten seconds after joining a world,
with no key press. This is how an unattended dev client produces a report to check; it is a development aid,
not a setting.

CI builds every push to `main` and every pull request, and a `v*` tag builds and attaches
`renderable-<version>.jar` to a GitHub Release. A release tag must match `mod_version` in
`gradle.properties`, or the workflow stops before building.

Design and plans live in `docs/superpowers/`.
