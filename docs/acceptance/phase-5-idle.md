# Phase 5 idle cost (spec §9 check 5)

The claim under test: when Renderable is not sampling, each of its hooks costs one volatile read, and nothing is
allocated. Phase 1 could not measure it from F3, because frame time at the pack's base moves more than the
effect. Phase 4 wrote this protocol (spec addendum §P4.A4) and did not run it. Phase 5 adds twenty villagers
with `NoAI` in view, so the entity render and tick hooks, the busiest, run in every frame (§P5.C1), and runs
with the pack's rendering mods, so Sodium's hooks are measured too.

**Status:** not run. On 2026-09-28 the user chose to skip the runs: players add Renderable for a profile and
remove it afterwards, and no cost was noticed while it was installed. The protocol, the scene and the numbers
script stay for a later release that wants the number.

## Once: the scene

1. Start the dev client, `./gradlew runClient -PpackMods --no-daemon`, and create a singleplayer world named
   `RenderableIdle`: World Type Superflat (the default Classic Flat preset), Generate Structures off (the preset
   builds villages), Creative, Allow Commands on. Then Save and Quit to Title, and close the game.
2. Copy the scene datapack into the world:
   `cp -r docs/acceptance/phase-5-idle-scene run/saves/RenderableIdle/datapacks/`
3. Start `./gradlew runClient -PquickPlay="RenderableIdle" -PpackMods --no-daemon`, run
   `/function renderable_idle:scene`, and check that the villagers stand still in view, five across and four
   deep. Nearer ones hide some farther ones, such as the middle column's back three, so about fifteen show; to
   count them all, run `/execute if entity @e[type=minecraft:villager]`, which answers `Test passed, count: 20`.
   The save keeps the view, so run the function again if the view has moved, then press Esc without moving the
   mouse and Save and Quit.
   The function sets the game rules (no daylight cycle, no mob spawning, no weather cycle), noon and clear
   weather. It kills every other entity and summons the twenty villagers with `NoAI`, two blocks apart, at
   x -4 to 4 and z -6 to -12. It puts the player in spectator at `0 -60 0` facing north, so the rows stand 6 to
   12 blocks in front. The save keeps all of it.
4. In Video Settings, set render distance 8, simulation distance 5, Max Framerate 60, VSync off, graphics
   Fast and GUI scale 2. Close the game.
5. With the game closed, edit two files:
   - in `run/options.txt`, `pauseOnLostFocus:false`, so a run that loses focus does not pause mid-recording;
   - in `run/config/fml.toml`, `earlyWindowWidth = 1280` and `earlyWindowHeight = 720`, for the window size.
     NeoForge's early loading window sets the game window's size, so `options.txt`'s `overrideWidth` and
     `overrideHeight` do nothing here, and Video Settings cannot set it.

   Every run reads these settings. Keep F3 closed throughout.

## The four runs

Run each command, and do not touch the mouse or the keyboard once the world has loaded. The recording starts 60 s
after launch and lasts 120 s, and its file appears in `run/` when it ends. Then quit the game normally. If the
world takes longer than about 50 s to load on this machine, use a longer `delay` for all four runs alike, and
note it under Results.

| Run | Renderable | JVM | Command |
| --- | --- | --- | --- |
| 1 | absent | default | `./gradlew runClient -PquickPlay="RenderableIdle" -PpackMods -PwithoutRenderable -PclientJvmArgs="-XX:StartFlightRecording=delay=60s,duration=120s,filename=idle-run1.jfr,settings=profile" --no-daemon` |
| 2 | loaded, no session | default | `./gradlew runClient -PquickPlay="RenderableIdle" -PpackMods -PclientJvmArgs="-XX:StartFlightRecording=delay=60s,duration=120s,filename=idle-run2.jfr,settings=profile" --no-daemon` |
| 3 | absent | escape analysis off | `./gradlew runClient -PquickPlay="RenderableIdle" -PpackMods -PwithoutRenderable -PclientJvmArgs="-XX:-DoEscapeAnalysis -XX:StartFlightRecording=delay=60s,duration=120s,filename=idle-run3.jfr,settings=profile" --no-daemon` |
| 4 | loaded, no session | escape analysis off | `./gradlew runClient -PquickPlay="RenderableIdle" -PpackMods -PclientJvmArgs="-XX:-DoEscapeAnalysis -XX:StartFlightRecording=delay=60s,duration=120s,filename=idle-run4.jfr,settings=profile" --no-daemon` |

After each run, before the next overwrites the log, check which way it ran:
`grep -cE "Renderable [^ ]+ \(renderable\)|Renderable [^ ]+ loaded" run/logs/latest.log` prints 0 after runs 1
and 3, and 2 after runs 2 and 4. A 1 would mean the game found Renderable's metadata without its classes, which
is a fault in `-PwithoutRenderable`: stop and report it. Note each run's count for the Results. PowerShell has no
`grep`; there the count is
`(Select-String -CaseSensitive 'Renderable [^ ]+ \(renderable\)|Renderable [^ ]+ loaded' run/logs/latest.log).Count`.

## The numbers

```
python docs/acceptance/phase-5-idle-numbers.py run/idle-run1.jfr run/idle-run2.jfr run/idle-run3.jfr run/idle-run4.jfr
```

For each run the script prints:
- **allocation:** the summed weight of `jdk.ObjectAllocationSample` without each thread's first sample (JFR weighs
  that one from the thread's start, so it carries what the thread allocated before the recording), and the part
  whose site, or one of the two calls above it, is Renderable's code;
- **mean CPU load:** `jdk.CPULoad` `jvmUser + jvmSystem`, without the recording's first reading, which is often
  0% or a stray figure rather than a measurement;
- **execution samples:** how many, and how many have Renderable's code on top: a class in its package, or a
  mixin handler, which Mixin merges into the hooked class under a name that contains `renderable$`
  (`renderable$timeEntityTick` runs as `wrapMethod$zza000$renderable$timeEntityTick`, for example).

Then it judges three checks:

1. **Run 2 against run 1.** Allocation and mean CPU load within 2%, and Renderable on top of fewer than 0.1% of
   execution samples. These are P4.A4's numbers, with "on top" for "anywhere on the stack": a wrapper handler is
   on the stack of everything it wraps.
2. **The control.** With escape analysis off, run 4 allocates in Renderable's frames, because the argument
   arrays MixinExtras passes to `@WrapMethod` and `@WrapOperation` handlers are then really allocated. This
   shows the measurement can see the hooks' allocation. P4.A4 compared run 4's total allocation with run 3's
   instead, and the script still prints that for context, but a few kilobytes a frame vanish in the client's
   megabyte a frame.
3. **Nothing allocated.** With escape analysis on, run 2 allocates at most a tenth of run 4's figure in
   Renderable's frames.

**Reading the numbers.**
- **A small share on top is the guard itself.** A handler that only reads the flag still shows up in some
  samples. `jfr view --width 200 hot-methods run/idle-run2.jfr` shows whether any `renderable$` method does more
  than that. Without the width, a long class name pushes the handler's name out of the column.
- **Noise.** Mean CPU load from one run each has no noise estimate. If it lands between 2% and 4%, record runs 1
  and 2 once more each and report the spread.

**If a check fails,** `jfr view --width 200 hot-methods run/idle-run2.jfr` and
`jfr view --width 200 allocation-by-site run/idle-run2.jfr` name the code: look for `renderable$` or
`io.github.edwardg117.renderable`. The allocation view names only the method that allocates, so a box a handler
makes for its arguments shows there as `java.lang.Integer.valueOf` or the like. It also counts each thread's first
sample, which carries what the thread allocated before the recording (the script leaves these out), so one such
sample can put a site high on the list. To see the calls above each site, and each sample's weight, search the
output of `jfr print --stack-depth 3 --events jdk.ObjectAllocationSample run/idle-run2.jfr` for
`renderable$`. A failure is a defect to fix before rollout. Event timing cannot be the cause: none of it exists
outside a session.

## Results

Not run: skipped on 2026-09-28 (see **Status** above).
