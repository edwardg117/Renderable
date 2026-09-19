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
   yellow or red. A leading `!` means that thing showed up in a hitch. `/renderable overlay off`
   hides them.

Commands: `start [seconds]`, `stop`, `cancel`, `results`, `copy`, `folder`, `overlay [on|off]`.

## Reading a report

- **Frames** gives the frame-time distribution. Trust p95 and p99 over the average.
- **Time split** shows where the average frame went: client tick, block entities, entities,
  other render work, and swap/wait (vsync, the GPU, the driver). A big swap/wait with a low GPU
  load is not a rendering problem in the world.
- **Hitches** lists the frames that spiked, each with its split and the things that were most
  expensive in exactly that frame. GC pauses are shown per frame.
- **Block entities** and **entities** are ranked by microseconds per frame; expand a type to see
  each position.

## Limits

Create machines rendered through Flywheel instancing never reach the vanilla renderer, so they
cannot be timed one by one (a Flywheel bucket arrives in phase 2). The mod costs nothing when it
is not sampling; while sampling, the report states its own overhead.

## Building

Java 21. `./gradlew build` produces `build/libs/renderable-<version>.jar`.
`./gradlew runClient` starts a dev client; add `-PpackMods` to include Sodium, Iris, Create and
Create: Enchantable Machinery at the versions the Gears & Frontiers pack pins.

Design and plans live in `docs/superpowers/`.
