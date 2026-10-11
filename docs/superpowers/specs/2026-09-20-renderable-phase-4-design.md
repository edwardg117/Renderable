# Renderable phase 4: correctness, robustness, polish

**Date:** 2026-09-20
**Status:** approved design, plans written
**Extends:** `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md` (the base spec). Section
numbers below with no prefix refer to that document; sections here are numbered `P4.x`.
**Baseline:** `main` at `1ec32c3`, tag `v0.3.0`, 75 JUnit tests.
**Evidence:** `docs/acceptance/phase-1.md` and `docs/acceptance/phase-2-3.md`, both written from real runs in
the Gears & Frontiers instance.

## P4.0 Why there is a phase 4

Phases 1 to 3 built the measurement. Running it against a real 386-mod pack turned up four separate
classes of problem, in descending order of how much they mislead a reader:

1. **Numbers that are wrong or misleading.** GC pauses are credited to whichever renderer happened to be
   open; a type row's peak is its worst member rather than its worst frame; mob vertex counts read zero
   under Accelerated Rendering; the overhead estimate ignores vertex counting; spec §9 check 5 has never
   been measured.
2. **Cost and robustness of the profiler itself.** The JSON report is built as a Gson tree before it is
   written; the instance cache holds every rendered object alive for the session, distorting the very GC
   figures the report prints; an unguarded Iris call sits on the hot path.
3. **Screen and overlay polish.** Empty states that cannot tell "absent" from "nothing happened",
   unbounded lists, a teleport that lands inside stone, a highlight that is a rectangle rather than a box,
   headers that disagree with the report, and a sort story that contradicts §5.3.
4. **Hygiene and CI.** Stringly-typed hook names, a dead enum constant, a Gradle 10 incompatibility, and a
   CI configuration that has never run on a runner.

Phase 4 is four independent plans, one per group. **Group A ships alone as `v0.4.0`**; B, C and D may follow
in any order. Group A is the only one that changes the JSON shape.

## P4.1 Decisions the user made

| Decision | Choice | Rejected |
| --- | --- | --- |
| GC pauses (P4.A1) | Flag GC-suspect values and keep them out of per-key peaks | Subtracting pauses via a `GarbageCollectionNotificationInfo` listener; a config-gated hybrid |
| Accelerated Rendering (P4.A3) | Hook it, gated and `require = 0` | Reporting the gap as `n/a`; hooking behind a config flag |
| JSON completeness (P4.B1) | Stream with `JsonWriter`, every array complete, no caps | A `sectionsTruncated` cap; a size warning |
| Sorting (P4.C7) | Click-to-sort column headers on the six data tabs | Amending §5.3 to match the cycling button; extending the cycling button |
| Instance cache (P4.B2) | Cache the accumulator on the object through a `@Unique` field | Evicting on removal; clearing on level unload and documenting it |

---

# P4.A Group A: numbers that are wrong or misleading

## P4.A0 The GC-suspect rule

Groups A1 and A2 share one rule, defined once here and used everywhere a GC pause could have inflated a
measurement.

> **GC-suspect.** A measured value `v` taken inside a frame that reported a GC pause of `p` is
> **GC-suspect** when `p >= 2 ms` **and** `v >= p - 1 ms`. Otherwise it is **clean**.

The rule is physical, not a heuristic. A stop-the-world pause of `p` inflates exactly the calls that were
open while it ran, and `System.nanoTime()` runs through the pause, so any call that actually contained the
pause must measure at least `p`. A value below `p` therefore cannot contain it.

The two constants both have reasons. `GarbageCollectorMXBean.getCollectionTime()` is documented as
approximate and reports whole milliseconds, so the comparison allows **one millisecond of slack**: a
genuinely 34.4 ms call should not read clean against a pause reported as 34. The **two-millisecond floor**
stops that slack from swallowing the pause — without it a 1 ms pause would mark every measurement in the
frame, down to zero — and a pause that small cannot make a peak misleading in any case.

The rule is deliberately conservative in one direction: a 40 ms renderer on a frame with a 34 ms pause is
marked suspect even though only 34 ms of it is pause. Renderable cannot tell those apart, and saying so is
better than printing a number that is silently 85% garbage collector.

It is also incomplete in a known way: a pause that starts before a call opens inflates that call by less
than `p`, so the inner call reads clean while the outer call that spanned the whole pause is flagged.
Renderable flags what it can prove.

**The argument is about one call, and the type rows apply it to a sum.** `TypeStats.frameNanos` is the total
of a type's members' inclusive frame times, and P4.A2 tests it with the same comparison. That is no longer
physical: twenty enchantable millstones at 1.5 ms each sum to 30 ms against a 30 ms pause, but the pause can
have been inside at most one of those calls, so at most about 1.5 ms of that sum is collector and the frame
is provably clean — yet its peak is discarded. The documented nested-inclusive double-count above pushes such
sums further over the threshold, and the types most affected are exactly the many-cheap-members types P4.A2
exists to serve.

The error is one-sided: the rule discards peaks it need not, and never invents one. A reader can therefore
see a `by type` row with a non-zero `gc f` above `by position` rows that all read zero, which is not a
contradiction — it means the type's *total* crossed the threshold while no individual member did. Phase 4a
accepts that rather than tracking, per type per frame, which member was innermost when the pause struck,
which would put per-type depth bookkeeping on the hot path for a column that is already conservative. If it
proves noisy on real data, the sharper rule is to test a type's **largest member** against the pause rather
than the type's sum.

`GC_MIN_NANOS = 2_000_000L` and `GC_SLACK_NANOS = 1_000_000L` live in `core/Session.java` and are quoted by
the tests, which pin both boundaries.

## P4.A1 GC pauses are credited to whichever key was open

**Observed.** `core/Session.closeFrame` records a frame's GC count and pause as the delta of the JVM's
cumulative counters, then leaves per-key `Accumulator.frameNanos`, `maxFrameNanos` and the frame's `top`
list to absorb the pause. From `docs/acceptance/phase-2-3.md`, "Observations for phase 4":

> In run 1 nearly every hitch above 40 ms coincides with a GC pause of the same size:
> `DripParticle$CoolingDripHangParticle` 34.00 ms tick with `gc 34`, `create:basin` 27.99 ms with `gc 28`,
> `create:item_vault` 23.13 ms `be tick` with `gc 23`, `flywheel 30.9` with `gc 26` [...] This is the most
> misleading number in the report and the first phase 4 item.

Phase 1's log found the same thing a day earlier: `colossalchests:colossal_chest` at 68.17 ms on a frame
reporting `gc 68 ms`.

**Design.** Apply the P4.A0 rule at the two places a GC pause escapes into a per-key number.

1. **Peaks.** In `closeFrame`, a key's `maxFrameNanos` is updated only from clean frames. A suspect frame
   increments a new `Accumulator.gcFrames` counter instead. A key whose every frame was suspect ends with
   `maxFrameNanos == 0` and `gcFrames > 0`, which the writers render as `-` rather than `0.00`.
2. **Top contributors.** `TopEntry` gains `boolean gc`, set when that entry's nanoseconds are GC-suspect
   for the frame it belongs to. The hitch list marks those entries.

The frame's own `gcCount` and `gcMillis` are unchanged, the `top` list still ranks by raw nanoseconds (the
frame really did take that long), and no bucket or total is altered. Only the claim "this key cost that
much" is qualified.

**Alternatives rejected.**

- *Subtracting the pause.* Needs the pause's position inside the frame. The cumulative counters do not
  carry it; only a `GarbageCollectionNotificationInfo` listener does. That listener delivers
  asynchronously on a JVM notification thread, usually after the frame has closed, with millisecond
  resolution against a `RuntimeMXBean.getUptime()` epoch that has to be correlated with `System.nanoTime()`.
  It would add an off-thread component with a lock-free hand-off, and there is no way to unit-test it
  against a real pause. The accuracy gained is at most "34 ms became 0.4 ms", which the flag already
  communicates.
- *Dropping GC frames from the session entirely.* They are real frames and belong in the frame
  distribution, the hitch list and the buckets.
- *Excluding every frame with `gcCount > 0` from peaks.* Simpler than P4.A0 but throws away legitimate
  peaks: a 40 ms renderer on a frame with a 2 ms pause is clean and should still set the peak.

**User-visible change.**

- *Text report.* The ranked tables (`Block entities by type`, `by position`, `Entities by type`,
  `individuals`) gain a right-aligned `gc f` column after `peak ms`, and `peak ms` prints `-` when it is
  unknown. The `Particles` table gains the same `gc f` column (particles have no peak column, and the
  `DripParticle` case above shows they are affected). Hitch contributors whose line is GC-suspect gain a
  trailing `  [gc]`:

  ```
    #2  frame 1841 @ 12.4s  71.8 ms  |  ... | gc 34 ms
        34.00 ms  net.minecraft.client.particle.DripParticle$CoolingDripHangParticle  [gc]
         2.10 ms  create:basin @ 2571 105 1145
  ```

  A note is printed once under the `Hitches` heading, and only when there is at least one hitch:
  `  [gc] marks a contributor that is at least as large as that frame's GC pause, so most of it may be the
  pause rather than the code.` The closing clause is the actionable half — it is what stops a reader
  opening a profiler on a renderer that was merely unlucky.

  Because that note is tied to the hitch list, it can be absent from a report that still shows `gc f` and
  `-`: a session with small pauses and no frame over the hitch threshold prints `Hitches: none`. So the
  **Frames** section carries its own line, beside the `GC:` line where the reader already meets garbage
  collection, printed whenever `gcCount > 0`:

  ```
    GC: 15 collections, 369 ms paused
    (gc f counts frames a pause could account for; those are left out of peak ms, and "-" means none was clean)
  ```

  One insertion covers every table below it, and it says the thing neither the header nor the marker can:
  that a peak printed beside a non-zero `gc f` is the **clean** maximum, not a contaminated one.
- *JSON.* `schema` becomes **4**. Diff against schema 3:
  - every object in `blockEntities.byType`, `blockEntities.byPosition`, `entities.byType`,
    `entities.individuals` and `particles` gains `"gcFrames": <long>`;
  - `peakFrameUs` keeps its name and becomes the clean peak; it is `0` when every frame was suspect, which
    `gcFrames > 0` distinguishes from "never cost anything";
  - every object in `frames[].top` gains `"gc": <boolean>`.
- *Results screen.* The Block Entities and Entities tabs gain a `gc f` column; the Particles tab gains one
  too. The Hitches tab appends `  [gc]` to a suspect contributor's name cell.
- *Overlay.* Unchanged. Labels rank by average cost per frame, which no single pause dominates.

**Acceptance check (A1).** In the pack, run a 30 s session at the base. In the report, every hitch
contributor whose milliseconds are within a millisecond of that frame's `gc` figure carries `[gc]`, and no
contributor below it does. At least one block entity or particle type shows a non-zero `gc f` with a
`peak ms` lower than the `gc` figure on the frame it was suspect in. Unit tests pin the rule at the
boundary (`p - 1 ms` exactly suspect, one nanosecond below it clean).

## P4.A2 A type row's peak is its worst member, not its worst frame

**Observed.** `core/RankedEntry.group` computes a type row's `maxFrameNanos` as
`max(member.maxFrameNanos)`. Twenty enchantable millstones costing 1.5 ms each in the same frame report a
type peak of 1.5 ms, when the type actually cost 30 ms in that frame. Phase 1's log shows exactly this
shape: sixteen millstones at 30.1 to 37.5 µs each rolling up to a 505.4 µs/f type row whose peak column
came from one member.

**Design.** Aggregate per frame, per type, on the render thread, without allocating per frame.

`core/TypeStats` is a small mutable object per `(category, typeId)` holding `frameNanos`, `inFrame`,
`maxFrameNanos` and `gcFrames` — the same four fields `Accumulator` already keeps, at type granularity.
`Session` owns a `HashMap<String, TypeStats>` (key `category.ordinal() + ":" + typeId`) and a
`touchedTypes` list that mirrors the existing `touched` list. Each `Accumulator` holds a final reference to
its `TypeStats`, resolved once when the accumulator is created, so no lookup is ever needed at timing time.

**`Session.end()` is not changed at all.** A type's frame total is accumulated at the frame boundary, in
the loop `closeFrame` already runs over `touched`: a type's total is by definition the sum of its members'
`frameNanos`, so adding those up once per key per frame gives the identical number that adding each
`elapsed` would. `closeFrame` then walks `touchedTypes` with the same GC-suspect rule as P4.A0 and resets
it.

That placement is load-bearing, not tidiness. Work done inside `end()` lands **inside the enclosing call's
timing window** whenever calls nest — an item renderer inside a block entity renderer — so it inflates the
outer measurement. `closeFrame` runs at the frame boundary with nothing open, and inflates nothing.

**Known limitation.** Because members contribute their inclusive times, a renderer of type T that renders
another T (Create's nested contraption renderers are the realistic case) has the inner call's time counted
twice in the type's frame total, and a type's peak can exceed the frame it was measured in. This is
inherent to summing inclusive times, and is documented on `TypeStats.frameNanos` rather than corrected:
subtracting nested same-type time would need a per-type depth counter on the hot path, which §3.5 forbids
for a case that changes one row of one table.

`RankedEntry.group` reads the type peak and `gcFrames` from `members.get(0).type()` rather than computing
them, because every member of a group shares one `TypeStats`.

**Alternatives rejected.**

- *Summing member peaks.* `sum(member.maxFrameNanos)` over-reports: members peak in different frames.
- *A map lookup per `end()`.* Correct but puts a `HashMap.get` on the hot path, which §3.5 forbids.
- *Accumulating the type total inside `end()`*, through the accumulator's field reference rather than a
  map. Allocation-free and correct, and it is what the first implementation did, but it puts a dereference,
  a branch and an add on the per-call path to compute a number the per-frame path already has — and it does
  so inside the enclosing call's timing window. Superseded by the frame-boundary form above, found in the
  code-quality review of phase 4a Task 1.
- *Computing it at report time from the frame records.* Frame records only keep the top N keys, so the
  information is not there.

**User-visible change.**

- *Text report.* The `by type` tables' `peak ms` column becomes the type's worst clean frame. Values go up
  wherever a type has more than one member. The `by position` and `individuals` tables are unchanged.
- *JSON.* No shape change beyond P4.A1's `gcFrames`; `peakFrameUs` on `byType` rows changes value.
- *Results screen.* Same change on the Block Entities and Entities type rows.
- *Overlay.* Unchanged.

**Spec amendment.** §3.4's accumulator paragraph gains: *"A parallel `TypeStats` per (category, type id)
keeps the same per-frame scratch value, so a type row's peak is the type's worst frame rather than its
worst member. Accumulators hold a reference to their `TypeStats`, so the hot path never looks it up."*

**Acceptance check (A2).** A unit test builds two block entities of one type costing 2 ms and 3 ms in the
same frame and 4 ms and 0 ms in another; the type row's peak is 5 ms, not 4 ms. In the pack, an
enchantable-millstone type row's `peak ms` is at least as large as the largest individual millstone's.

## P4.A3 Mob vertices read zero under Accelerated Rendering

**Observed.** From `docs/acceptance/phase-2-3.md` check 9:

> Ordinary mob models read `0.0` (pig, horse, cow, sheep, zombie, and chicken in runs 2 and 3) [...] The
> cause appears to be the pack's Accelerated Rendering 1.0.8-1.21.1-alpha, whose mixin config takes over
> the buffer builder, buffer source and vertex consumer [...] so those vertices never pass through
> `BufferBuilder.beginVertex` or Sodium's `push`; this was read from the mod's mixin config, not confirmed
> by removing the mod.

**Verified since.** Read with `javap` from
`%APPDATA%\.MCUpdater\instances\Gears-And-Frontiers\mods\accelerated_rendering.jar`
(`modId = "acceleratedrendering"`, `version = "1.0.8-1.21.1-alpha"`) on 2026-09-20:

- `com.github.argon4w.acceleratedrendering.core.mixins.buffers.BufferBuilderMixin` gives vanilla
  `BufferBuilder` an `AcceleratedBufferBuilder acceleration` field and implements
  `IAcceleratedVertexConsumer.doRender`, so accelerated geometry leaves the vanilla class before
  `beginVertex` and **cannot be double-counted** by the existing hook.
- `com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.AcceleratedBufferBuilder`
  is a non-final `public class` implementing `VertexConsumer`. It is the single point every accelerated
  vertex passes through; the decorating consumers (`AcceleratedSheetedDecalTextureGenerator`,
  `AcceleratedSpriteCoordinateExpander`, `AcceleratedEntityOutlineGenerator`,
  `AcceleratedVertexConsumerWrapper`) delegate into it.
- Its four writing methods and what each adds to the builder's vertex total:

  | Method | Arithmetic in the bytecode | Count |
  | --- | --- | --- |
  | `addVertex(float, float, float)` | `vertexCount += 1` | 1 |
  | `addVertex(float, float, float, int, float, float, int, int, float, float, float)` | `vertexCount += 1` | 1 |
  | `addClientMesh(ByteBuffer, int, int, int, int)` | `vertexCount += <2nd parameter>` | that parameter |
  | `addServerMesh(ServerMesh, int, int, int)` | `vertexCount += mesh.size()` or `meshVertexCount += mesh.size()` depending on `CoreFeature.shouldUploadMeshImmediately()` | delta of `getTotalVertexCount()` |

  `public int getTotalVertexCount()` returns `vertexCount + meshVertexCount`.
  `addVertex(PoseStack$Pose, float, float, float)` writes neither counter, so it delegates to
  `addVertex(float, float, float)` and needs no hook of its own.

**Design.** One gated mixin, `hooks/mixin/acceleratedrendering/AcceleratedBufferBuilderMixin`, targeting
the builder **by string** (`@Mixin(targets = "...", remap = false)`) so the build needs no dependency on an
alpha mod, in the third-party config with `require = 0`. `RenderableMixinPlugin` gains
`"acceleratedrendering" -> "acceleratedrendering"` in its sub-package map. Three of the four hooks are
allocation-free argument or return-value modifiers, matching the vanilla `beginVertex` hook; `addServerMesh`
uses a head/return pair around `getTotalVertexCount()` because its count is not an argument, and it fires
once per model part rather than once per vertex.

`Hook` gains `ACCELERATED_VERTICES` so the environment header states whether the hook fired.

**Alternatives rejected.**

- *Reporting `n/a` instead of `0.0`.* Honest but leaves the pack's dominant renderer class unmeasured, and
  vertex counts are the spec's only GPU-load proxy (§2, "GPU-side timing (timer queries). Vertex counts are
  the proxy.").
- *A compile-time dependency on the AR jar.* It is not published on the Modrinth Maven under any
  resolvable slug (`accelerated-rendering`, `acceleratedrendering` and `accelerated_rendering` all 404;
  the only Modrinth hits are third-party ports), so the dependency would have to be a machine-local file
  path. String targeting avoids that entirely.
- *A config flag, default off.* A second rarely-exercised code path for no safety gain: the mixin is
  already gated by mod presence and `require = 0`, and its failure mode is the zero the column reads today.
- *Hooking `MeshUploaderPool$MeshUploader`.* Further from the renderer and would miss the direct
  `addVertex` path.

**Verified applying, 2026-09-20.** The dev client with the real jar exports all five handlers and the
`@Unique` field into `AcceleratedBufferBuilder`, and each is invoked from its target: `addVertex(FFF)` at
its return, the 11-argument `addVertex` and `addClientMesh` at head, and `addServerMesh` at head plus
**both** of its return points — confirming the two-branch structure the bytecode analysis predicted, and
why the delta of `getTotalVertexCount()` is the right measure there rather than either counter alone.

**Accelerated Rendering 1.0.8 does not run against Iris 1.8.14-beta.1.** Found while verifying this hook:
AR's own `compat.iris` mixin `ModelToEntityVertexSerializerMixin` fails a constant-modifier injection at
`RenderSystem.initRenderer`, crashing the client before any world loads. Nothing to do with Renderable —
AR 1.0.8 was built against Iris 1.8.12, which is what the deployed pack runs, and `1.8.14-beta.1` is a pin
for the pack update in progress. It matters twice: the dev-client proof of this hook must run **without**
`-PpackMods`, and the pack update cannot ship Iris 1.8.14-beta.1 alongside Accelerated Rendering 1.0.8
without one of the two moving.

**Accepted idle-cost exception.** `addServerMesh`'s head handler writes `renderable$meshBefore` on every
call, including when idle, where it stores `-1`. Every other hook in the mod costs exactly one volatile read
when idle, so this is a deviation from §3.5 and is listed here rather than left silent. The alternative —
writing only while sampling — leaves a stale baseline that the return handler's `>= 0` guard would accept,
and the only case that could then inflate a count requires `ACTIVE` to flip between head and return of one
call, which cannot happen because `attach`/`detach` run at a frame boundary. Both forms are therefore safe;
the sentinel is chosen because its correctness is legible without that argument, and the cost is one `int`
store to an object the caller is already writing through.

Stated in full, because the paragraph above understated it at first: the `addServerMesh` pair is `@Inject`,
so Mixin also constructs two `CallbackInfo` objects per call, idle or not. That is the same shape as the
`@WrapMethod` and `@WrapOperation` argument arrays §3.5 already accepts, and it is why the two `addVertex`
hooks — which fire per **vertex** rather than per mesh — deliberately use `@ModifyReturnValue` and
`@ModifyVariable` instead, where a primitive passes through with no callback object at all. The whole
exception is per mesh upload, never per vertex.

**Risk accepted.** AR 1.0.8 is an alpha. The mixin is gated, `require = 0`, and string-targeted, so a
rename degrades the `verts/f` column for accelerated renderers to the zero it already shows, logs a warning,
and changes nothing else. `AcceleratedBufferBuilder` has no `LocalVariableTable`, so no injection may
capture locals; all four use arguments, the return value, or a shadowed public method.

**User-visible change.**

- *Text report, JSON, screen.* `verts/f` becomes non-zero for mob renderers when AR is installed. No shape
  change. `hooks applied` gains `ACCELERATED_VERTICES`.
- *Environment.* `EnvironmentCollector` gains an `acceleratedrendering` version line beside `sodium`, `iris`,
  `flywheel` and `distanthorizons`, and base spec §6.1 item 2's list gains it too. This is not cosmetic: it
  is the only thing that makes this hook's failure mode diagnosable. The hook is string-targeted against an
  alpha, so a renamed target means the mixin quietly does not apply and `ACCELERATED_VERTICES` is simply
  absent from `hooks applied` — which, without a version line, reads exactly like a pack that never had the
  mod installed. With it, "AR 1.0.8 present" beside "`ACCELERATED_VERTICES` absent" is self-diagnosing from
  the report a user already pastes into an issue.
- *README.* The caveat that GPU vertex-expansion mods bypass the vertex hooks is replaced with: Accelerated
  Rendering is hooked, other such mods are not.

**Acceptance check (A3).** In the pack, a 30 s session with mobs in view shows non-zero `verts/f` for
`minecraft:pig`, `minecraft:cow` or whichever mobs are present, and `hooks applied` contains
`ACCELERATED_VERTICES`. In the dev client with the pack mods plus the AR jar, the exported
`AcceleratedBufferBuilder` class shows the four `renderable$` handlers. Without AR, no mixin error is
logged and the class is absent.

## P4.A4 Idle cost has never been measured (spec §9 check 5)

**Observed.** `docs/acceptance/phase-1.md` check 5 records **NOT VERIFIED**:

> Frame time at the base fluctuates more than the effect being looked for, so the with/without comparison
> was inconclusive. [...] Until that is done, the README's "costs nothing when it is not sampling" is a
> design claim rather than a measured one.

Its recommendation was "a controlled A/B in a static test world (fixed camera, no entities moving)".

**Design.** A written, repeatable protocol measured with Java Flight Recorder, which is part of the JDK and
needs no code in the mod, so the instrument does not perturb what it measures.

*Scene.* A superflat singleplayer save under `run/saves/RenderableIdle`, created once and reused:
`/gamerule doDaylightCycle false`, `/gamerule doMobSpawning false`, `/gamerule doWeatherCycle false`,
`/time set noon`, `/kill @e[type=!player]`, then `/gamemode spectator` and stand still. Render distance 8,
simulation distance 5, fps limit 60, vsync off, graphics fast, GUI scale 2, windowed 1280x720, F3 closed.
The camera is not touched for the whole measurement.

*Runs.* Four, each 120 s of recording after a 60 s warm-up, launched with `-PquickPlay="RenderableIdle"`:

| Run | Jar | JVM |
| --- | --- | --- |
| 1 | absent | default |
| 2 | present, no session | default |
| 3 | absent | `-XX:-DoEscapeAnalysis` |
| 4 | present, no session | `-XX:-DoEscapeAnalysis` |

Each adds `-XX:StartFlightRecording=delay=60s,duration=120s,filename=<run>.jfr,settings=profile`.

*Measurements,* from `jfr summary` and `jfr print`:

- **Allocation.** Total bytes from `jdk.ObjectAllocationSample`, plus `jdk.GCHeapSummary` deltas.
- **CPU.** Mean `jdk.CPULoad` `jvmUser + jvmSystem` over the recording, at a fixed frame cap, which is the
  frame-time proxy that phase 1 could not get from F3.
- **Mod frames on the stack.** Count of `jdk.ExecutionSample` events whose stack contains
  `io.github.edwardg117.renderable`.

*Acceptance numbers.*

1. Run 2 versus run 1: allocation within **±2%**, mean CPU load within **±2%**, and fewer than **0.1%** of
   execution samples containing a Renderable frame.
2. Runs 3 and 4 are the control that proves the instrument is sensitive: with escape analysis off, run 4's
   allocation must exceed run 3's by **more than 2%**, because the MixinExtras `@WrapMethod` and
   `@WrapOperation` argument arrays are then really allocated. If run 4 does not separate from run 3, the
   measurement is not sensitive enough to support claim 1 and the result is recorded as inconclusive rather
   than as a pass.

**Alternatives rejected.**

- *F3 frame time with and without the jar.* Phase 1 tried it; the base fluctuates more than the effect.
- *Instrumenting the mod to report its own idle cost.* Circular: the instrument is the thing under test.
- *Measuring in the real pack.* 386 mods, live entities and a server connection make it irreproducible.
  The claim being tested — "one volatile read per hook when idle" — does not depend on the scene.

**User-visible change.** None in the mod. A new `docs/acceptance/phase-4-idle.md` records the runs, and the
README's idle-cost sentence is rewritten to state the measured result and its scene, or to say the check
was inconclusive, whichever the runs show.

**Acceptance check (A4).** The four runs are recorded in `docs/acceptance/phase-4-idle.md` with the numbers
above and a verdict against §9 check 5.

## P4.A5 The overhead estimate ignores vertex counting; `Fmt.us` is a generic formatter

**Observed.** `docs/acceptance/phase-2-3.md`: *"Sampling overhead is about 1.9% at the base [...] Vertex
counting is not included in the estimate."* `core/Session.finish` computes
`timedCalls * calibratedCallNanos`, and `Probe.vertex()` / `Probe.vertices(int)` fire outside that count —
run 1 of the pack alone drove `create:basin` to 9575.4 vertices per frame. Separately, `Fmt.us` ("microseconds
with one decimal") is used for average live counts, rebuilds per second, mesh milliseconds per second,
vertices per frame and Flywheel visual counts, none of which are microseconds.

**Design.**

- `Calibration` gains `measureVertexNanos(LongSupplier)`, the same shape as `measureCallNanos`: warm up 2000
  `Session.vertex()` calls inside one open call, then time 10 000 and divide.
- `SessionConfig` gains `calibratedVertexNanos` as a sixth component; `ofSeconds` gains the matching
  parameter.
- `Session` counts `vertexCalls` (one increment in `vertex()` and one in `vertices(int)`; a batched call
  counts once, because the cost is per call, not per vertex) and carries it into `SessionResult`.
- `overheadEstimateMs = (timedCalls * calibratedCallNanos + vertexCalls * calibratedVertexNanos) / 1e6`.
- `Fmt.num(double)` is added — "a plain number with one decimal" — and every non-microsecond use of
  `Fmt.us` moves to it. Rendered output is byte-identical; the change is to make the next reader's
  intent legible and is covered by the existing report tests passing unchanged.

**Alternatives rejected.**

- *Counting vertices rather than vertex calls.* The per-call cost is what the calibration measures; Sodium's
  batched `push` and AR's mesh paths add many vertices in one call.
- *Timing the vertex hook directly.* Two clock reads per vertex would cost far more than the hook.

**User-visible change.**

- *Text report.* The overhead line becomes
  `  Renderable overhead: about 555.76 ms over 6,947,008 timed calls and 1,204,331 vertex calls`.
  (Counts are grouped with `Fmt.count` now, which the old line did not do.)
- *JSON.* `session.vertexCalls` is added (schema 4). `session.overheadEstimateMs` changes value.
- *Results screen.* The Frames tab's `Renderable overhead` row gains the vertex-call count.
- *Overlay.* Unchanged.

**Acceptance check (A5).** In the pack, the reported overhead rises relative to `v0.3.0` on the same scene
and the vertex-call count is non-zero. A unit test pins
`overheadEstimateMs == (timedCalls * callNanos + vertexCalls * vertexNanos) / 1e6` on a fixture with both.

---

# P4.B Group B: robustness and cost

## P4.B1 The JSON report is built as a Gson tree

**Observed.** `report/JsonReportWriter.write` assembles the whole report into a `JsonObject` and then calls
`toJson`. From the phase 2/3 log: *"The JSON report is 4.86 MB for 4983 frames; the sections array is about
280 KB of that."* Every frame contributes a `JsonObject` plus one per top entry, so a 30 s flight builds
millions of short-lived objects and then a 4.86 MB `String`, inside the same client whose GC behaviour the
report is describing.

**Design.** Stream. `JsonReportWriter.write(SessionResult, Environment, String modVersion, Writer out)`
writes through `com.google.gson.stream.JsonWriter` with `setIndent("  ")` and never holds more than one row.
A convenience `String write(SessionResult, Environment, String)` stays for the tests and writes into a
`StringWriter`. `ReportFiles` opens the file with `Files.newBufferedWriter` and hands the writer over, so
the 4.86 MB string is never materialised in the client.

Every array stays complete, per the decision in P4.1: §6.2's promise is unchanged, the observed sizes are
not a problem once nothing is held in memory, and the phase 3 log already concluded the sections array is
bounded in practice at 1052 rows.

**Alternatives rejected.**

- *Capping `sections` with a `sectionsTruncated` marker.* Breaks §6.2's completeness promise and needs a
  schema bump, to bound a number that has never been a problem.
- *Keeping the tree and writing it in chunks.* The tree is the cost.

**User-visible change.**

- *Text report, screen, overlay.* None.
- *JSON.* Byte-identical output for the same session; no schema change. `ReportFiles.write`'s second
  parameter becomes a `JsonBody` functional interface rather than a `String`.

**Spec amendment.** §6.2 gains: *"The JSON is streamed to the file a row at a time; the report is never held
in memory as a tree or as a string."*

**Acceptance check (B1).** A unit test writes the phase 3 fixture through both the `Writer` overload and the
`String` overload and asserts the two are equal and parse to the same tree. In the pack, a 30 s flight
produces a JSON file of the same size and content as `v0.3.0` for the same scene.

## P4.B2 The instance cache holds every rendered object alive

**Observed.** `hooks/Probe.BY_INSTANCE` is an `IdentityHashMap<Object, Accumulator>` populated on first
sight of every block entity and entity and cleared only at `detach()`. During a session that is exactly the
window the report's GC figures cover: anything rendered once cannot be collected until the session ends, so
the mod inflates the numbers it prints. Run 1 recorded 15 collections and 369 ms of pause over 30 s.

**Design.** Remove the map. A duck-typed interface `hooks/AccumulatorHolder` with
`Accumulator renderable$accumulator()` / `void renderable$accumulator(Accumulator)` is implemented onto
`BlockEntity` and `Entity` by two vanilla mixins carrying one `@Unique Accumulator` field each. `Accumulator`
carries the **id** of the session that made it — a `long` from a monotonic counter, with `0` reserved for a
standalone accumulator — so a cached accumulator left over from an earlier session is detected and replaced
rather than reused.

**An id, not a reference.** The obvious form, and the one the first implementation used, is a `Session owner`
field mirroring `MeshAccumulator.owner`. It is wrong here, and the difference is the whole point of this
item. `MeshAccumulator` points at `MeshStats`, a small per-session container; an `Accumulator` pointing at
the `Session` would reach its `frames` list — every `FrameRecord`, its bucket array and its top list — plus
`accumulators`, `types` and `touched`, none of which `finish()` clears. Every block entity and entity
rendered during a session would then hold that session's entire frame history alive until it was re-rendered
under a newer session or unloaded: roughly a megabyte per stale session from the second session onward. That
is the same defect this item exists to remove, inverted rather than fixed, and it would have made the
sentence below false. A `long` comparison has identical staleness semantics and holds nothing.

The hot path becomes a field read and a reference comparison instead of an `IdentityHashMap.get`, so it is
both allocation-free and faster than today. Nothing is retained: the field lives and dies with the object.

`BY_CLASS` (particles, keyed by `Class`) stays as it is — classes are already strongly reachable and there
is no retention to remove. `MESH_BY_OBJECT` stays as it is: `Block` and `Fluid` instances are registry
singletons.

**Alternatives rejected.**

- *Evicting on `BlockEntity.setRemoved` and `Entity.remove`.* Same mixin count, keeps a map lookup on the
  hot path, and misses any object that goes away without those calls.
- *Weak keys.* `WeakHashMap` uses `equals`, and `Entity.equals` compares entity ids, so it is not an
  identity map; a hand-written weak identity map means a `WeakReference` allocation per key and a reference
  queue to drain.
- *Clearing on level unload.* Does not address the within-session window, which is the one the acceptance
  log ran into.

**Cost accepted.** One reference field on every `BlockEntity` and `Entity` in the client, roughly 8 bytes
each, and two required vanilla mixins on two heavily-mixed classes. A `@Unique` field plus an interface is
the least conflict-prone mixin shape there is, and §4.5 already requires vanilla mixins to fail loudly.

**User-visible change.** None in any output. The report's GC figures become the client's rather than the
client's plus the profiler's.

**Spec amendment.** §4's `Probe` paragraph gains: *"Block entity and entity accumulators are cached on the
object itself through a `@Unique` field, not in a map, so a session never keeps a rendered object alive."*

**Acceptance check (B2).** In the pack, a 30 s session at the base reports a GC count and pause no higher
than `v0.3.0` on a comparable scene, and the Block Entities and Entities tables are unchanged in content.
The acceptance log records both runs' `GC:` lines side by side.

## P4.B3 The HUD rescans every frame, and the mod's own in-frame cost is unreported

**Observed.** `ui/SamplingHud.render` calls `HitchDetector.countRolling(s.framesView(), 120, ...)`, which
sorts the last 120 frames and then walks **all** frames so far — 4983 of them by the end of a 30 s flight —
once per rendered frame. None of that cost, nor the once-a-second Flywheel visual sample, is in
`overheadEstimateMs`, which only models begin/end pairs.

**Design.** Two changes.

1. *Throttle.* `SamplingHud` caches the last computed hitch count and the frame count it was computed at,
   and recomputes at most four times a second (and always on the first frame of a session). `countRolling`
   itself is unchanged, so its unit tests stand.
2. *Measure, do not estimate.* `Session` gains `selfNanos`, a plain counter, and `beginSelf()`/`endSelf()`
   that time work the mod does inside a frame for itself: the HUD render and the Flywheel visual sample.
   Those are not attributed to any key or bucket — they sit in `other render` where they really are — but
   the report can now say how much of `other render` is Renderable.

   **The overlay is deliberately not measured**, though an earlier draft of this section listed it.
   `ProfilerController.start` calls `WorldOverlay.clear()`, so while a session is sampling the overlay has
   no labels to draw: `onRenderLevelStage` early-returns and `render` skips `drawLabels`, leaving two field
   reads and a return per frame. Wrapping that in a `beginSelf`/`endSelf` pair would add a clock read to
   every measured frame in order to measure approximately zero — the opposite of this item's point. The
   only overlay work that can overlap a session is a ten-second highlight left over from a previous one —
   now the wireframe box §P4.C5 built, whose corner and clip arrays are static scratch space reused every
   frame, so Renderable's own code allocates nothing per frame — which the README records rather than
   measures. Naming the overlay in the
   report line while not measuring it would be a false claim, so the line names what it measures.

**Alternatives rejected.**

- *An incremental hitch count.* The rolling threshold moves as the median moves, so a frame that was not a
  hitch can become one; keeping it exact incrementally costs more than the throttle saves.
- *Estimating the HUD cost.* It is trivially measurable; estimating a measurable thing is worse.

**User-visible change.**

- *Text report.* The overhead line gains a second sentence:
  `  Renderable's own in-frame work (HUD and Flywheel sampling): 12.40 ms measured, inside other render.`
- *JSON.* `session.selfMs` is added. This is an additive field on an existing object; `schema` stays at the
  value group A set (4), because readers of schema 4 that ignore unknown fields are unaffected and no
  existing field changes meaning. The README's schema section records it.
- *Results screen.* The Frames tab gains a `Renderable self time` row.
- *Overlay.* Unchanged.

**Acceptance check (B3).** In the pack, a 30 s session reports a non-zero self time smaller than the
`other render` bucket, and the HUD's hitch count still moves during sampling.

## P4.B4 `markShadow` calls Iris unguarded on the hot path

**Observed.** `Probe.markShadow` calls `IrisCompat.shadowActive()` before `s.begin(...)` in
`beginBlockEntity`, `beginEntity` and `beginParticleRender`. If Iris's
`ShadowRenderingState.areShadowsCurrentlyBeingRendered()` were removed or renamed, the resulting
`NoSuchMethodError` would propagate out of `beginX` **before** `begin` ran, while the mixin's `finally`
still calls `end()` — closing the caller's call instead and unbalancing the attribution stack for the rest
of the frame. `EnvironmentCollector.safe` already catches `LinkageError`; this path does not.

**Design.** Give `IrisCompat` the latch `FlywheelCompat` already has: a `volatile boolean broken`, a
`try`/`catch (Throwable)` around the implementation call, one warning logged, and `false` returned for the
rest of the client run. `Probe.markShadow` then cannot throw. The `try`/`catch` costs nothing when nothing
throws, so the hot path is unchanged.

**Alternatives rejected.**

- *Moving `markShadow` after `begin`.* An exception would then leave a call open instead of closing the
  wrong one — no better.
- *Wrapping the whole `beginX` body.* Hides real bugs in Renderable's own code behind a catch.

**User-visible change.** None unless Iris breaks, in which case the shadow column reads 0.0%, the log
carries one warning, and everything else keeps working — §4.5's stated policy.

**Acceptance check (B4).** Code review plus the B5 audit table; there is nothing to observe at runtime
against a working Iris.

## P4.B5 Audit of the other third-party call sites

**Design.** The audit is part of the deliverable, recorded here so it does not have to be redone:

| Call site | Reaches third-party code | Guarded |
| --- | --- | --- |
| `IrisCompat.PRESENT`, `FlywheelCompat.PRESENT` | `ModList.isLoaded` (NeoForge, always present) | not needed |
| `IrisCompat.shadowActive()` | `ShadowRenderingState` | **no — fixed by P4.B4** |
| `IrisCompat.packName()` | `Iris` | yes, via `EnvironmentCollector.safe`, and after B4 also by the latch |
| `FlywheelCompat.sample(Level)` | `VisualizationManagerImpl`, `VisualManagerImpl` | yes, `catch (Throwable)` plus a latch |
| `FlywheelCompat.backendName()` | `BackendManager`, `Backend.REGISTRY` | via `EnvironmentCollector.safe` only; **gains its own latch** so a non-collector caller is safe too |
| `hooks/mixin/flywheel/*`, `hooks/mixin/sodium/*`, `hooks/mixin/acceleratedrendering/*` | mixin targets | `require = 0` plus the plugin gate: a failure **to resolve an injection** is a load-time warning, not a runtime throw. See the qualification below — that guarantee does not extend to the bodies of handlers that did apply |

**What `require = 0` does not cover.** The row above is about *injection resolution*. Once a handler has
applied, its body is ordinary code: a third-party call inside it throws at runtime like any other. Two
consequences the audit had to chase rather than assume.

The sharper one is a **cross-mixin cast**. `ChunkBuilderMeshingTaskMixin` reaches the section position through
`((ChunkBuilderTaskAccessor) (Object) this)`, and that accessor is applied by a *different* mixin targeting a
*different* class — `ChunkBuilderTask`, the generic superclass, deliberately. The two can fail independently:
if Sodium renames the `render` field, the accessor is skipped with exactly the load-time warning the row
promises, while the meshing mixin's own injection points may still resolve. The cast would then throw
`ClassCastException` into Sodium's `execute`, on a worker thread, on the first completed rebuild of a session
— a runtime throw produced by a load-time failure, breaking the chunk build rather than merely emptying the
Sections table. Phase 4b guards it with an `instanceof` and skips the section timing when the accessor is
absent, which is the same degrade-quietly policy as everything else in §4.5.

The milder one is that a handler body which survived a version bump can still call a method that did not:
`render.getChunkX()` and `Backend.REGISTRY.getId(backend)` are plain runtime calls. The probability is low,
because a rename usually moves the injection target too and takes the whole handler with it, and
`backendName()` is now latched. It is recorded rather than guarded.

Both compat classes end up with the same shape — `PRESENT`, an inner `Impl` class that is the only thing
referencing third-party types, and a latch **per entry point** rather than per class. The per-entry-point
part is not pedantry: a shared latch lets a decorative failure switch off a data feature. `backendName()`
runs once per session purely to print a string, and `packName()` likewise; if either shared a flag with
`sample()` or `shadowActive()`, a renamed id-lookup would disable visual counts or blank the shadow column
for the rest of the client run, leaving a wrong number in the report and a warning about something else in
the log. The load-bearing lookups and the cosmetic ones fail independently because they are independent API
surfaces.

`ABSENT` and `"unknown"` are a pair and mean different things: `ABSENT` is "this mod or feature is not
installed", `"unknown"` is "it is installed and we could not find out". A reader who sees `shaderPack:
unknown` should look in the log for a latched failure; `shaderPack: absent` means Iris is simply not there.

**User-visible change.** None.

**Acceptance check (B5).** The table above is reproduced in the plan's review checklist, and a unit-free
code review confirms every row.

## P4.B6 Six small correctness defects

| # | Defect | Fix |
| --- | --- | --- |
| 1 | `core/Environment.of` copies `hooksApplied` but hands the caller's `entries` map straight through, so the caller can mutate a "record" | Copy it **preserving order**: `Collections.unmodifiableMap(new LinkedHashMap<>(entries))`. `Map.copyOf` must not be used — it loses insertion order, and the text report prints the environment in insertion order |
| 2 | `ProfilerController.lastResult` survives logout while `WorldOverlay`'s labels do not, so Highlight and Teleport act on a world that is gone | Keep the result (the report is still worth copying) and add `resultWorldGone`, set on `ClientPlayerNetworkEvent.LoggingOut`; `ResultsScreen` disables Highlight and Teleport when it is set |
| 3 | `reportFolder` is resolved straight from config with no validation, so `../../` escapes the game directory | Validate in `ReportFiles`: reject absolute paths, any `..` segment, and empty values, falling back to `renderable` with one logged warning. Testable once the class is Minecraft-free (P4.D2, implemented with this item) |
| 4 | `WorldOverlay.setResult` ends with `enabled = OVERLAY_ENABLED.get()`, so finishing a session re-enables labels the user turned off with `/renderable overlay off` | Initialise `enabled` from config once, on first use; `setResult` no longer touches it. Not at mod construction: `ModConfigSpec.ConfigValue.get()` throws `IllegalStateException` before the config has loaded, and mod construction is exactly when that is possible |
| 5 | `Environment` has no name for the `"absent"` sentinel, which is spelled as a literal in `EnvironmentCollector`, `TextReportWriter` and (after P4.C1) `Rows` | `public static final String ABSENT = "absent"` on `Environment`, plus `boolean has(String key)` returning `entries` having a value that is neither null nor `ABSENT`, and `String value(String key)` returning the entry or `ABSENT` |
| 6 | `ReportFiles` imports `Minecraft`, contradicting §8 | Take the game directory as a `Path` parameter from `ProfilerController` (P4.D2) |

**User-visible change.** Only #2 and #4 are observable: Highlight and Teleport grey out after logout, and
`/renderable overlay off` stays off across sessions.

**Item 4 superseded in phase 6 (§P6.13).** Copying the setting on first use meant a change made in the mod's config
screen did nothing until a restart. The command and the overlay key now write `overlayEnabled` and the overlay
reads it every frame, so the command and the setting are one switch, and `overlay off` lasts across restarts too.

**Acceptance check (B6).** Unit tests for #1, #3 and #5. In the pack: turn the overlay off, run a session,
confirm it stays off; log out with a result loaded, open the results screen, confirm Highlight and Teleport
are disabled and Copy report still works.

---

# P4.C Group C: screen and overlay polish

## P4.C1 The tabs cannot tell "absent" from "nothing happened"

**Observed.** `report/TextReportWriter` distinguishes `(no data: Sodium hooks were not active)` from
`(no data: no sections were rebuilt)` and `(no data: Flywheel is not installed)` from
`(no data: no Flywheel visualization ran; backend flywheel:off)` — the phase 2/3 log confirms the last one
fired for real. `ui/screen/Rows.build` has no `Environment`, so the same situations all read
`(no data: Sodium mesh hooks did not run)` or `(no Flywheel visualization ran)`.

**Design.** Thread the `Environment` through: `Rows.build(Tab, SessionResult, Environment, ColumnSort,
boolean, Set<String>)`, which `ResultsScreen` already holds. The empty-state sentences move to a new
`core/EmptyStates` class — plain Java, no Minecraft, no Gson — and both `TextReportWriter` and `Rows` call
it, so the screen and the report can no longer drift. `ui` keeps its dependency direction (`ui -> core`);
it does not gain one on `report`.

Corrected on 2026-09-26, after a dev-client report said `(no data: Sodium hooks were not active)` in a client
where Sodium was installed and hooked: `hooksApplied` lists hooks that have **fired while sampling**, not
hooks that are installed, and a session in which no section is rebuilt never fires the mesh hook. Sodium's
upload hook (`SODIUM_UPLOAD`, on `RenderSectionManager.uploadChunks` and `updateChunks`) fires on every frame
Sodium renders, so either Sodium hook in the list now counts as proof the hooks are in place, and an empty
table then reads `(no data: no sections were rebuilt)` or `(no data: no blocks were meshed)`. A session
standing still at a base, the first step of the in-pack acceptance, is exactly that case.

**Alternatives rejected.**

- *Duplicating the strings in `Rows`.* Guarantees drift.
- *Having `ui` call `report`.* Breaks §8's dependency direction.

**User-visible change.** The Blocks, Sections and Flywheel tabs print the same sentence the report does.
No JSON or overlay change.

**Acceptance check (C1).** In the dev client without Sodium, the Blocks and Sections tabs read
`(no data: Sodium hooks were not active)`. In the pack with shaders on (which turns the Flywheel backend
off, as run 2 showed), the Flywheel tab reads `(no data: no Flywheel visualization ran; backend
flywheel:off)`.

## P4.C2 The Sections tab is unbounded

**Observed.** `Rows.sections` adds one row per rebuilt section. The phase 3 flight produced 1052.

**Design.** Cap the Sections tab at 200 rows and append one plain row reading
`... and 852 more sections (the JSON has all 1052)` — singular, `... and 1 more section (the JSON has all
201)`, at exactly one left out. The report is not named in that row: the text report prints only its own top
20, so it does not have the rest either. The cap takes the first 200 of whatever order the tab is currently
sorted in, which is the 200 most-rebuilt sections only when the tab is still in its default order. The Blocks
tab is bounded by the number of block types in the pack and is left alone. The text report's top-20
truncation and the JSON's completeness are unchanged.

**User-visible change.** Screen only.

**Acceptance check (C2).** After a flight into new terrain, the Sections tab ends with the "and N more" row
and the JSON still contains every section.

## P4.C3 Teleporting from a Sections row lands inside stone

**Observed.** `ResultsScreen.teleportToSelected` sends `tp @s x (y+1) z` using the row's position, which for
a section row is the section's centre block — solid ground for most sections.

**Design.** The design changed in review: the player's current Y is only a place they can stand at their own
column, not necessarily at the section's, so a player on foot walking towards a hill landed inside it under
the first version of this fix. Section rows now teleport to the section's centre X and Z at the player's own
Y raised to the first height in that column at which the player's bounding box has no block collision
(`ResultsScreen.standingY`) — the client does have the collision shapes of loaded chunks, which is what this
uses. A column the client has not loaded has none to test, so the player's own Y is kept as is. Block-entity
rows keep `y + 1`. Entity rows are unchanged.

**Alternatives rejected.**

- *The section's top (`y * 16 + 16`).* Still inside terrain for any section below the surface.
- *A heightmap lookup for an unloaded column.* `ClientLevel.getHeight` reads an empty chunk and returns the
  bottom of the world; in the Nether it returns the top of the bedrock roof; and `MOTION_BLOCKING`, the type
  that would otherwise fit, counts water and leaves as ground. None of the three heightmap types is safe to
  fall back on, so an unloaded column keeps the player's own Y instead.

**User-visible change.** Screen only. The command sent for a section row becomes
`tp @s <centreX> <standingY> <centreZ>`, where `standingY` is the player's own Y raised until their bounding
box fits.

**Acceptance check (C3).** Teleport from a Sections row and arrive above ground at the section's column.

## P4.C4 Entity overlay labels are not dimension-filtered

**Observed.** `WorldOverlay.setResult` builds entity labels with `dimension = null`, which `drawLabels`
treats as "draw in any dimension". The README claims *"Labels are shown only in the dimension they were
profiled in"*.

**Design.** `ProfilerController` remembers the dimension it started the session in and passes it to
`WorldOverlay.setResult(SessionResult, String dimension)`, which stamps it on entity labels the way block
entity and section labels already carry theirs. The README's claim becomes true for all three kinds.

This is belt and braces rather than a live defect — `FrameEvents.onLevelUnload` discards a running session
when the client level goes away, so a session cannot span two dimensions — but the *result* outlives the
session. Entity ids are not reused within a server run (every `Entity` takes a fresh one from a static
counter), so what the stamp guards against is an entity that keeps its id through a portal, such as another
player, being labelled in a dimension it was not measured in.

**Alternatives rejected.**

- *Softening the README.* The behaviour is wrong, not the documentation.
- *Adding the dimension to `SessionResult`.* A wider change to a record five tests construct by hand, for
  one consumer.

**User-visible change.** Overlay only.

**Acceptance check (C4).** Run a session with entities in view, go through a portal, and confirm no entity
labels are drawn there. On the way back, block entity and section labels return; an entity's label returns
only if that entity stayed loaded, because a mob whose chunk unloaded comes back with a new id.

## P4.C5 The highlight is a bounding rectangle, not a box

**Observed.** `WorldOverlay.drawHighlight` projects the eight corners and draws the rectangle that bounds
them. Phase 1's log: *"The user notes it is less polished than Observable's marker but acceptable for a
first pass; a wireframe box drawn in screen space is a phase 2 polish item."* It slipped phases 2 and 3.

**Design.** Project the eight corners as now, then push the twelve edges into the GUI layer's vertex buffer
as two-pixel-wide quads, one `flush()` for the whole box, so the marker still shows through walls and
shaders. Two refinements the rectangle could not have:

- An edge that crosses the camera's near plane is clipped there, in clip space, instead of the whole
  highlight vanishing when any one of the eight corners is behind the camera, which is what the rectangle
  did; only an edge with both endpoints behind the plane is skipped.
- When the projected box is smaller than `MIN_HIGHLIGHT` on both axes, a small square is drawn in its place,
  so a distant block stays findable.

Each edge is a quad handed to the GPU rather than pixel runs rasterised on the CPU, so an edge that runs off
screen costs the same as one that does not: the GPU's own rasteriser clips it for free. The corner and clip
arrays are static scratch space reused every frame, so Renderable's own code allocates nothing to draw the
box (vanilla builds one buffer for the single flush) — which matters
because a highlight can outlive the session it came from and keep drawing while the next one samples
(§P4.B3).

**Alternatives rejected.**

- *Drawing in the world with a depth test off.* This is exactly what phase 1 replaced: Iris's pipeline drew
  it behind blocks.
- *`GuiGraphics.renderOutline` per face.* Only axis-aligned rectangles; a projected cube's faces are not.
- *Rasterising each edge on the CPU into runs of `GuiGraphics.fill`, as the first version of this design
  did.* In 1.21.1 a GUI layer is not drawn managed, so every `fill` flushes: each run was its own draw call
  carrying about 1 KB of vanilla garbage, up to 1,275 draw calls for one frame of a section highlighted from
  three blocks away (the old rectangle took 8), and the loop was unbounded for a corner just in front of the
  camera.

**User-visible change.** Overlay only.

**Acceptance check (C5).** Highlight a block entity and a section from the results screen: a box in
perspective, visible through walls, with the section's box enclosing sixteen blocks on a side.

## P4.C6 Screen and report disagree in five places

| # | Disagreement | Resolution |
| --- | --- | --- |
| 1 | Sections tab header `avg ms`; report column `avg ms/rebuild` | Screen header becomes `avg ms/rb` and the column weight rises from 2 to 3; the report keeps `avg ms/rebuild` in full, because plain text has the room |
| 2 | Flywheel visual classes are simple names on screen, fully qualified in the report and JSON | Screen **and** report print the simple name; JSON keeps the fully-qualified name as the machine record. When two classes in one session share a simple name, both are prefixed with their last package segment (`press.PressVisual`, `mixer.PressVisual`); if that still leaves two alike (`a.x.PressVisual`, `b.x.PressVisual`), both are shown in full |
| 3 | `PEAK` is in the Sort cycle on Particles, which has no peak column | Resolved by P4.C7: the cycle is replaced by header clicks, and a tab can only sort by a column it shows |
| 4 | `RowList.Entry.render` truncates left-aligned cells with `plainSubstrByWidth` but draws right-aligned cells untruncated, so a long number runs into its neighbour | A right-aligned number too wide for its column may reach three of the four blank pixels before it; beyond that its decimals are rounded away, and only if it still does not fit is it drawn as `#`s, as a spreadsheet does. It is never truncated like text, because a truncated number reads as a different one (`125,539` cut to `125,5`). The threshold is not the column's own width, because at 1920x1080 on the Auto GUI scale that plain threshold hid real values the old code drew legibly |
| 5 | The ranked and particle column names and cell expressions were written twice, in `TextReportWriter` and `Rows` | The cell text is now defined once, in `core/RankedTable`, which both call, and the report takes its headers from there; the screen writes its column names out in `Tab`, beside their widths and sorts, and `TabTest` asserts they equal `RankedTable`'s |

**User-visible change.** Screen for 1, 3 and 4; text report for 2, and the screen too whenever two classes
collide. JSON unchanged.

**Acceptance check (C6).** On screen: the Sections header reads `avg ms/rb`; in a narrow column a number is
rounded, or shown as `#`s, never cut short and never overlapping its neighbour. In the report: Flywheel rows read `SingleAxisRotatingVisual`, and the
JSON's `flywheel.visualCounts` keys are still fully qualified.

## P4.C7 §5.3 promises per-column sorting that does not exist

**Observed.** §5.3 says *"Every list sorts by any numeric column"* and then, in the same sentence,
*"(implemented as a cycling Sort button on the Block Entities, Entities and Particles tabs; the Frames,
Hitches, Flywheel, Blocks and Sections tabs have a fixed order)"*. The spec contradicts itself, and the code
matches the parenthesis.

**Design.** Implement the promise. Every tab declares, per column, the sort key that column means — or none
for columns that are not sortable. Clicking a header sorts by that column descending; clicking the active
header again reverses it; the active header draws an arrow (`v` for descending, `^` for ascending). The
Sort button is removed. (A name column is the exception to "descending" on the first click: it sorts A to Z,
which is what a reader clicking `name` expects; see the second bullet.)

- `Ranking.Sort` gains `SHADOW`, `GC_FRAMES` and `LABEL`, so the ranked tabs can sort by every column they
  show; `Ranking.MeshSort` gains `PER_CALL` and `LABEL` only — the Sections average column sorts by
  `PER_CALL`, which for a section is time per rebuild, so an `AVG` column would order rows identically and
  was dropped. Both `Ranking.sort` and `Ranking.mesh` gain an `ascending` overload, and both keep their
  existing descending form so no writer changes. Every sort orders rows by what its column displays, not by
  a raw field behind it: `SHADOW` orders by the shadow share the column prints, not by shadow nanoseconds.
- A tab's columns are a `List<Column>` of `Column(name, weight, rightAlign, sort)` records, not the parallel
  `headers`/`weights`/`rightAlign`/`sorts` arrays first proposed, so one column's pieces cannot drift apart
  from each other. `ColumnSort` is a pure-Java record in `ui/screen` holding a nullable `Ranking.Sort` and a
  nullable `Ranking.MeshSort`, with `ColumnSort.NONE` for a column that does not sort. A record over the two
  existing enums is preferred to a third, parallel enum of column names: a parallel enum would have to be
  kept in step with both by hand, and nothing would fail if it drifted. Each tab opens in the text report's
  order (`TabTest` pins every tab to it); a new column's first click sorts A to Z for a name and largest
  first for a number; the Blocks and Sections name columns carry `MeshSort.LABEL`.
- The Flywheel tab's rows are class names and averages rather than ranked or mesh entries, so its two
  sortable columns reuse `Ranking.Sort.LABEL` (class name) and `Ranking.Sort.TOTAL` (average count), which
  `Rows.flywheel` reads directly. Two orderings do not justify a third sort type; `TabTest` pins the
  interpretation.
- Frames and Hitches keep a fixed order: their rows are a narrative, not a table, and `ColumnSort.NONE` on
  every column disables clicking there.

**Alternatives rejected.**

- *Amending §5.3 to match the button.* The promise is the better design for a profiler; a reader who wants
  "what emitted the most vertices" should not cycle through six orderings to find out.
- *Extending the cycling button to all six tabs.* Still not per-column, and a cycle over nine columns is
  worse than the three it replaces.

**Spec amendment.** §5.3 was rewritten to say what shipped: the Block Entities, Entities, Particles, Blocks
and Sections lists sort by any column and the Flywheel list by name or average count (its share column is the
bucket's, not a per-row value); each opens in the text report's order; a click sorts largest first for a
number and A to Z for a name, a second click reverses it, and the active header carries an arrow; Frames and
Hitches have a fixed order.

**User-visible change.** Screen only. The Sort button disappears from the button row, which leaves room for
the five bottom buttons to grow.

**Acceptance check (C7).** On the Block Entities tab, click `verts/f` and the rows reorder by vertices
descending with an arrow on that header; click it again and the order reverses. Click `name` and the rows
sort alphabetically. On Frames, clicking a header does nothing.

---

# P4.D Group D: hygiene and CI

## P4.D1 Tests and dead code

| Item | Design |
| --- | --- |
| Hook names are string literals in `TextReportWriter` (`"PARTICLE_RENDER"`, `"SODIUM_MESH"`, `"FLYWHEEL"`) | Move `Hook` from `hooks` to `core` — it is a bare enum with no imports — and have the writers use `Hook.SODIUM_MESH.name()`. `hooks` keeps a re-export-free import. A `HookNamesTest` then guards anything still stringly-typed by round-tripping every name the writers emit through `Hook.valueOf` |
| `Tab.Columns.layout` has no test | `TabTest` (moved into plan 4c, which changes `Tab`). Plan 4c made a tab's columns a list of `Column(name, weight, rightAlign, sort)` records, so the parallel-array lengths this row first proposed checking cannot differ. The test pins every tab's columns in full (name, weight, alignment, sort, and the column it opens on); checks that `layout(w)` keeps its padding after every column and ends within `w` at the GUI widths players use; that every width is at least 8 and offsets strictly increase; that every sort is one the tab's rows can supply; that the ranked and particle names equal `RankedTable`'s; and `fitNumber` against the vanilla font's glyph widths |
| `KeysTest.coreHasNoMinecraftImports` covers `core` only | Extend it to `report`, exempting `EnvironmentCollector`, which §8 names as the one Minecraft-aware class there. Moved into plan 4b, which is what makes `ReportFiles` pass it |
| `ProfileKey.Category.VISUAL` is never constructed | Delete it. Flywheel visuals are counted in `FlywheelStats`, not keyed, exactly as §3.4 says |
| `"absent"` appears as a literal in three files | Replaced by `Environment.ABSENT` (P4.B6 #5) |

## P4.D2 §8 says `EnvironmentCollector` is the only Minecraft-aware class in `report`

**Observed.** `report/ReportFiles` imports `net.minecraft.client.Minecraft` for
`Minecraft.getInstance().gameDirectory`.

**Design.** Fix the code, not the spec. `ReportFiles.write` takes the game directory as a `Path`, supplied
by `ProfilerController`, which is already Minecraft-aware and already owns delivery. The class becomes pure
Java, joins the extended import test, and the path-traversal check of P4.B6 #3 becomes unit-testable
against a temporary directory.

**Implemented in plan 4b**, not 4d: 4b changes the same method's signature for streaming, and splitting one
method's rework across two independently-ordered plans would make whichever ran second conflict. Plan 4d
records the reassignment; the readme lists it.

**Spec amendment.** None needed — §8 becomes true.

## P4.D3 `build.gradle` uses a Gradle 10 incompatibility

**Observed.** `tasks.named('test', Test) { testLogging { exceptionFormat 'full' } }` uses Groovy
space-assignment syntax, removed in Gradle 10.

**Design.** `exceptionFormat = 'full'`. The plan also greps the whole file for the same shape so the fix is
complete rather than anecdotal.

## P4.D4 CI has never run on a runner

**Observed.** `.github/workflows/build.yml` and `release.yml` are unchanged since phase 1, before the build
grew `packModJars`, `extractNestedModJars` and three Modrinth coordinates. A Modrinth outage now fails the
build, nothing is cached beyond `setup-gradle`'s defaults, neither workflow declares `permissions` or a
`concurrency` group, and `release.yml` will happily attach a jar whose version does not match its tag.

**Design.**

- **`build.yml`**: `permissions: contents: read`; `concurrency: group: build-${{ github.ref }}`,
  `cancel-in-progress: true`; a cache of `build/nested-mod-jars` keyed on `gradle.properties`'s hash, so a
  warm cache survives a Modrinth outage; `extractNestedModJars` and `createMinecraftArtifacts` run first in
  a three-attempt retry loop, so a transient failure at Modrinth or at Minecraft's or NeoForge's servers does
  not fail the build (both tasks only download and unpack, so a compile error never reaches the loop); then
  `./gradlew build --no-daemon`; artifact upload unchanged. `setup-gradle` already caches `~/.gradle`, and the plan states that rather than adding a
  second, conflicting cache.
- **`release.yml`**: the same `permissions: contents: write` it has, plus a concurrency group, the same
  cache and retry, and a **version gate** that fails before building when
  `v$(mod_version from gradle.properties) != $GITHUB_REF_NAME`; and the release step fails, rather than
  publishing a release without its jar, when `build/libs/renderable-*.jar` matches nothing
  (`fail_on_unmatched_files`).

**Acceptance check (D4).** The workflows have never run, so the check is the user pushing a branch and a
tag and reading the run: `build` green on a pull request, the version gate failing a deliberately mismatched
tag, and `release` attaching `renderable-<version>.jar`.

## P4.D5 Key identity and labels

| Item | Observed | Design |
| --- | --- | --- |
| `EntityKey` | The record's generated `equals`/`hashCode` include `name`, but `serialize()` is `typeId#entityId`. An entity renamed mid-session becomes a second key with the same serialised form, splitting its measurements and producing two JSON rows with identical `key` fields. (Since P4.B2 caches the accumulator on the entity object, an entity that stays loaded keeps its first key; the fork needs the client to re-create the entity object under a new name, as when it leaves tracking range and returns.) | Override `equals` and `hashCode` to use `typeId` and `entityId` only, documenting that the first name seen wins. `name` stays in the record for `label()` and the report |
| `BlockEntityKey.label()` | Omits the dimension that `serialize()` carries | Leave the label alone and document why: `FrameEvents.onLevelUnload` discards a running session when the client level goes away, so every key in one session shares one dimension, which the environment header prints. Adding it to every row would widen the tables for no information. A unit test pins the rule so the next reader does not "fix" it |

---

## P4.2 Spec amendments collected

Applied to `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md` by the plan that
implements each:

| Section | Amendment | Plan |
| --- | --- | --- |
| §3.4 | `TypeStats` per (category, type id) for type-level peaks | 4a |
| §3.5 | The overhead estimate covers vertex calls as well as begin/end pairs; the mod's own in-frame work is measured, not estimated | 4a, 4b |
| §4 | Block entity and entity accumulators are cached on the object, not in a map | 4b |
| §4.3 | The vertex-count table gains the Accelerated Rendering row | 4a |
| §5.3 | Per-column click sorting on the six data tabs; Frames and Hitches fixed | 4c |
| §6.1 | The ranked tables' `gc f` column and the `[gc]` marker | 4a |
| §6.2 | `schema` 4, the new fields, and "streamed to the file a row at a time" | 4a, 4b |
| §9 | Check 5 gains the JFR protocol and its acceptance numbers | 4a |
| §11 | A phase 4 row | 4a |

## P4.3 What phase 4 does not do

- **No GC subtraction.** See P4.A1.
- **No calibration of the `hooks` side of a hook.** `Calibration` lives in `core` and cannot import
  `Probe`, so both calibrated constants measure from inside `Session` and exclude the guard each hook pays
  first: `Probe.vertex()`'s static read, thread comparison and `seenVertices` check, and
  `Probe.beginBlockEntity`'s `SEEN[]` write and `markShadow` call. At tens of millions of vertex calls a
  session, one unmeasured nanosecond per call is tens of milliseconds of real overhead the report does not
  claim. Closing this properly needs a calibration entry point in `hooks` that drives a whole `Probe` call,
  which is a phase 5 change; phase 4 instead states the boundary in both `Calibration` javadocs, so the
  figure is not read as exact. The error is one-sided in the safe direction for the part that *is*
  measured — the calibration loop always runs the most expensive branch, so guard-returning calls are
  over-charged rather than under-charged.
- **No per-visual Flywheel timing.** §4.4 is unchanged; nothing new was learned that would change it.
- **No GPU timer queries.** §2 is unchanged; vertex counts remain the proxy.
- **No hook for a riding entity's tick.** `ClientLevel.tickEntities` routes only non-passengers through
  `Level#guardEntityTick`, so while a player rides anything their tick is folded into the vehicle's
  inclusive time and vanishes from the entity table. Confirmed on real data: a flight session rendered the
  player 4,983 times and ticked them **zero** times, while the aircraft reported 309.7 µs per tick — a
  number that contains the player's tick without saying so. This matters more than it looks, because the
  player is the most expensive entity to tick in this pack by a wide margin (498 µs against 28.9 µs for the
  next worst, 48% of all client entity-tick time), so the entity table is at its least trustworthy exactly
  when someone is moving around on a vehicle. Closing it means a second hook on `Entity.rideTick()` and a
  decision about whether to subtract the passenger from the vehicle's time or report both. Phase 5, recorded
  in base spec §4.2.
- **No decomposition of a single entity's tick.** The same measurement raises the obvious next question —
  *what* costs 498 µs inside one player tick — and Renderable cannot answer it. `guardEntityTick` wraps the
  whole call, so the figure is inclusive of everything the pack's mods attach to a player tick, and there is
  no call site to wrap inside another mod's event listener. A sampling profiler such as spark is the right
  tool for that question, and the README should not imply otherwise.
- **No hook for Distant Horizons, Sodium Extra, Sodium Dynamic Lights or the other pack renderers.** The
  phase 2/3 log's largest unattributed bucket is still `other render` at 5 to 6 ms per frame; attributing
  it further is a phase 5 question, not a phase 4 one.
- **No narrowing of the ranked tables.** `gc f` takes the ranked tables to nine columns and a 108-character
  header, and the particles table to 124 with a real class name such as
  `net.minecraft.client.particle.DripParticle$CoolingDripHangParticle` — past the point where a GitHub code
  block scrolls sideways. The unbounded dimension is the label, not the column count, so dropping a column
  would not fix it. If the width is ever reclaimed, `shadow` is the candidate: it prints `0.0%` on every row
  of every session without Iris shadows, and `Splits.average` already establishes the "omit what is empty"
  precedent in this codebase. Phase 5.
- **No Modrinth or CurseForge release.** §10 is unchanged.
- **Blocks and Sections rows are still written twice.** `TextReportWriter` and `Rows` each build the Blocks
  and Sections cells by hand; they could move into `core` the way `core/RankedTable` now holds the ranked
  ones. Only the
  Sections `avg ms/rb` header differs between the two surfaces, by design (P4.C6 #1).
- **The ranked empty-state sentence blames the wrong thing.** `EmptyStates.ranked()` says `(no data: hook
  not active in this build)`, but the block entity and entity hooks are required vanilla mixins, so in
  practice an empty list means nothing of that kind was drawn. Correcting the sentence changes the report,
  so it waits for a plan that may.
- **A Flywheel-only area shows no empty-state sentence.** With Flywheel drawing, the Block Entities tab
  always starts with its "Flywheel-instanced machines are not timed here" heading, so an otherwise empty tab
  (open terrain, or an area whose only block entities Flywheel draws) shows just that heading and never the
  sentence above. Fix it together with that item.
- **The world-gone flag is coarser than it needs to be.** Highlight and Teleport are disabled by any level
  unload, which includes every dimension change, so after a portal round trip they stay greyed until the
  next session finishes, even though the player is back where the session was taken. An exact check would
  compare the selected row's dimension — block entity and section keys carry one; entity rows would use the
  session dimension `ProfilerController` now records — against the current level's, and keep the blanket
  flag for logout only.
- **Labels still cost one flush per draw call.** The label overlay has the same flush-per-call cost the
  highlight had before P4.C5: each label is a `fill` and a `drawString`, each its own draw call, so 75
  labels are 150 draws a frame during ordinary play after a session (labels are cleared while sampling, so
  measurements are unaffected). Drawing them inside `GuiGraphics.drawManaged` would batch them; check the
  draw order of overlapping labels when doing it.
- **The highlight is not dimension-scoped.** Unlike labels, a highlight carries no dimension: one set just
  before a portal trip keeps drawing, for the rest of its ten seconds, at the same coordinates in the new
  dimension.
- **Reopening the results screen resets its view.** Highlight and Teleport close the screen, and every reopen
  builds a new one on the Frames tab with default sorts and collapsed groups, so working down a sorted list
  means re-selecting the tab and re-sorting after each Highlight. The view state could live in
  `ProfilerController` and be cleared when a new result arrives.
- **Name sorts are case-sensitive and textual.** A qualified Flywheel name such as `mixer.PressVisual` sorts
  after every capitalised class name, and section labels sort as text (`-1`, `-10`, `-2`). A case-insensitive
  comparison for the name sort itself, not the tie-break, would fix the first without changing the report,
  which never sorts by name.
