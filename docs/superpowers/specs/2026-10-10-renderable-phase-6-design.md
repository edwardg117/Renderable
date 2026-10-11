# Renderable phase 6: sound, terrain and Distant Horizons

**Date:** 2026-10-10
**Status:** approved 2026-10-10; plan 6a done on `phase-6a-measurement` (294 tests); plan 6b written.
**Extends:** the base spec `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md`, the phase 4
addendum `2026-09-20-renderable-phase-4-design.md` and the phase 5 addendum
`2026-09-26-renderable-phase-5-design.md`. Sections here are numbered `P6.x`. A number with no prefix refers to
the base spec, and `P4.x` or `P5.x` to an addendum.
**Baseline:** `main` at `5e600a3`, tag `v0.5.0` (`623beb3`), 264 JUnit tests.
**Evidence:**

- Two 30 s sessions at another player's base on 2026-10-10 (`docs/acceptance/phase-5.md`, "After the tag").
- A spark profile taken there the same day (`https://spark.lucko.me/52qelkXCJu`): 60 s, the built-in Java
  sampler at 4 ms, keeping only ticks of 15 ms or more.
- javap against the pinned NeoForge 21.1.220 merged jar, Sodium 0.8.13 and Iris 1.8.14-beta.1 (the pins),
  Sodium 0.6.13 and Iris 1.8.12 (the deployed pack), and Distant Horizons 2.4.5-b (deployed) and 3.3.3 (the
  new pin) (P6.2).

## P6.0 Why there is a phase 6

The phase 5 acceptance at another player's base found time Renderable cannot name. In the 16:08 session,
83 frames were hitches, 6.6% of the session, and the worst were mostly client tick. Hitch #3 was a 30.0 ms
frame with 25.6 ms of tick, of which the named parts (particle tick 0.9, be tick 2.3, entity tick 0.4,
mods 2.0) cover 5.6 ms. About 20 ms had no name, and the Advice tab said nothing: `unattributed-stall`
(P5.B2) waits for 50 ms.

The spark profile, taken at the same spot, names it. Of the samples kept, `Minecraft.tick` is 19.31%:

| Part of the tick | Share of samples | Renderable today |
| --- | --- | --- |
| `ClientLevel.tickEntities` (block entity ticks 7.63%) | 8.64% | `be tick`, `entity tick` |
| `SoundManager.tick` | 4.03% (2,420 ms) | unnamed |
| `ParticleEngine.tick` | 3.89% | `particle tick` |
| `ClientTickEvent` Post and Pre handlers | 1.42% | `mods` |
| `ClientLevel.animateTick` | 0.79% (476 ms) | unnamed |
| `TextureManager.tick` | 0.36% | unnamed |

`SoundManager.tick` is three quarters of the unnamed tick. Inside it, 3.69% (2,216 ms) is
`SoundEngine.play` blocked in `CompletableFuture.join`: the render thread waiting for the sound thread to
hand it a channel. Across the whole profile, `SoundEngine.play` is 6.67%, and `CompletableFuture.join`
6.35%, because sounds also play from block entity ticks (Create's boilers, through `BoilerData.tick` and
`SoundPool.play`) and from packets.

On the render side, `LevelRenderer.renderSectionLayer` is 35.22% of all samples, and 34.34% of that is
Distant Horizons' handler injected at its head (`ClientApi.renderLods` 33.49%). DH draws its LODs there, so
Renderable counts the largest single cost in the frame as `other render`. Most of it is GL driver work:
`glDrawElements` 18.87%, `glUseProgram` 5.14%.

Phase 6 names both: sound and Distant Horizons, and the terrain draw around them.

## P6.1 Decisions the user made

| Decision | Choice | Rejected |
| --- | --- | --- |
| Scope | Sound and Distant Horizons, with a `terrain` bucket and the cheap tick parts | A generic split of every call in the tick; sound alone |
| Where per-sound figures go | Rows in the Mods tab, like packets | A Sounds tab, which would start a third row of tabs |
| How DH is timed | An optional mixin on DH's four `ClientApi` render methods | DH's public API events (they bracket only the main pass, sit outside the hook lists, and a cancelled render leaves a "before" with no "after"); no DH code at all (the report could never say "Distant Horizons") |
| The DH pin | 3.3.3, the newest release for NeoForge 1.21.1; the pack update file still names 3.3.1, and bumping it is the user's step | 3.3.1 |
| Plans | Two, stacked: 6a measurement, 6b advice and release | One plan |

### Changes since the design was approved

Two points were settled while writing:

1. **The sound row's category is `SoundSource.getName()`**, so a row reads `sound create:steam (block)`, not
   `(blocks)` as the design's example had it. The advice uses the slider's name, "Blocks" (P6.9).
2. **The DH mixin needs no DH classes at compile time.** It targets `ClientApi` by name, and every method it
   wraps takes no arguments and returns nothing. DH 3.3.3 is still pinned, for the dev client and for javap
   (P6.8).

During plan review the user added one rule test: `sound-wait`'s stutter form also fires on the `sound` bucket
in repeated hitches (P6.9), for stalls spread over many sound events.

# P6.A Measurement (plan 6a)

## P6.2 Verified hook targets

Vanilla targets, against `neoforge-21.1.220-merged.jar`. Offsets are bytecode offsets within the named
method.

| # | Method | Instruction | Used for |
| --- | --- | --- | --- |
| 1 | `Minecraft.tick()V` | 513 `INVOKEVIRTUAL SoundManager.tick(Z)V`, after `MusicManager.tick` (502, skipped while paused) | `sound` bucket (P6.3) |
| 2 | `Minecraft.tick()V` | 773 `INVOKEVIRTUAL ClientLevel.animateTick(III)V`, reached only when `isLevelRunningNormally()` | `animate tick` bucket (P6.3) |
| 3 | `LevelRenderer.renderLevel(...)V` | 569, 630, 663, 2561, 2588, 2755, 2795 `INVOKEVIRTUAL LevelRenderer.renderSectionLayer(RenderType, DDD, Matrix4f, Matrix4f)V` | `terrain` bucket, one injector for all seven (P6.3) |
| 4 | `SoundEngine.play(SoundInstance)V` | whole method | sound rows (P6.5) |

What `SoundEngine.play` does, in order:

1. It returns at once if the engine is not `loaded` (offset 1).
2. It posts NeoForge's `PlaySoundEvent` through `ClientHooks.playSound` (9), whose handlers may replace or
   drop the sound. Those handlers are timed by the event proxies (P5.A3) and nest inside the play.
3. It checks `canPlaySound` and resolves the sound, warning once for a missing one.
4. It computes the volume (`calculateVolume`, 168). When that is 0 and the instance cannot start silent, it
   logs at debug level and returns (199 to 226), before asking for a channel.
5. It returns when the listener's gain is 0 (347).
6. It asks for a channel, `ChannelAccess.createHandle(pool)` (407), and blocks on
   `CompletableFuture.join` (414) until the sound thread answers. This is the wait spark shows.

Third-party code around target 3, checked so the `terrain` wrapper chains with it:

- **Sodium** `@Overwrite`s `renderSectionLayer` in `LevelRendererMixin`, in both 0.8.13 (pinned) and 0.6.13
  (deployed). The body changes; the seven call sites in `renderLevel` stay.
- **Iris** puts a `@WrapWithCondition` on the same call sites in `MixinLevelRenderer_SkipRendering`, in both
  1.8.14-beta.1 and 1.8.12. MixinExtras chains it with a `@WrapOperation`. Iris's shadow pass calls
  `renderSectionLayer` through its own accessor, not through `renderLevel`, so shadow terrain stays outside
  `terrain`.
- **Distant Horizons** `@Inject`s at the head of `renderSectionLayer` (`MixinLevelRenderer`, both 2.4.5-b and
  3.3.3). It does not cancel. By the layer it is given, it calls one of four `ClientApi` methods.

DH's targets, against both `distanthorizons` 2.4.5-b (the deployed jar) and 3.3.3:

| # | Method in `com.seibel.distanthorizons.core.api.internal.ClientApi` | Called by DH's head injection for | Used for |
| --- | --- | --- | --- |
| 5 | `public void renderLods()` | `RenderType.solid()` | `lod` bucket (P6.3) |
| 6 | `public void renderDeferredLodsForShaders()` | `RenderType.translucent()` | `lod` bucket |
| 7 | `public void renderFadeOpaque()` | `RenderType.cutout()` | `lod` bucket |
| 8 | `public void renderFadeTransparent()` | `RenderType.tripwire()` | `lod` bucket |

`ClientApi` sits in DH's own jar, not in a nested one, so Mixin can reach it. DH's mod id is
`distanthorizons`.

## P6.3 Four new buckets

| Bucket | Label | Side | Hook | Kind of hook |
| --- | --- | --- | --- | --- |
| `SOUND` | `sound` | tick | target 1, `@WrapOperation` | vanilla, required |
| `ANIMATE_TICK` | `animate tick` | tick | target 2, `@WrapOperation` | vanilla, required |
| `TERRAIN` | `terrain` | render | target 3, `@WrapOperation` | vanilla, required |
| `LOD` | `lod` | render | targets 5 to 8, `@WrapMethod` each | optional, in `hooks.mixin.distanthorizons`, gated on DH being loaded |

- **What each holds.**
  - `sound` is the sound engine's tick: updating playing sounds, and playing the sounds `playDelayed`
    queued. Sounds played from elsewhere stay where they are played (P6.5).
  - `animate tick` is the random block display effects around the player: drips, smoke, sparkles, and the
    particles they spawn.
  - `terrain` is the chunk-layer draws `renderLevel` makes, solid through tripwire, whichever renderer
    supplies them: Sodium's, vanilla's, or another mod's head injection. It leaves out the
    `RenderLevelStageEvent` both renderers post at the end of each layer: a required wrapper on NeoForge's
    `ClientHooks.dispatchRenderStage(RenderType, ...)` closes the terrain call while it runs, so its handlers stay
    named time and what they draw keeps its own bucket (plan 6a, deviation 4).
  - `lod` is Distant Horizons' LOD drawing, in every pass DH's head injection runs: the main pass, the
    deferred pass for shaders, and the two fades.
- **Order.** The enum lists the render buckets `TERRAIN`, `LOD`, then the existing seven, and the tick
  buckets `PARTICLE_TICK`, `BE_TICK`, `ENTITY_TICK`, `SOUND`, `ANIMATE_TICK`. The split prints in enum order,
  so terrain and its LODs, drawn first and usually largest, lead the render side. Ordinals are not
  persisted: the JSON writes buckets by label (P6.7).
- **Hooks.** Five new `Hook` values: one per bucket, `SOUND`, `ANIMATE_TICK`, `TERRAIN` and `LOD`, and
  `SOUND_PLAYS` for the sound rows' hook on target 4 (P6.5). Each injector goes in `HookInstalls.HOOKS`, as
  `CLAUDE.md` requires. The mixin plugin gains
  `"distanthorizons" → "distanthorizons"` in `REQUIRED_MOD`.
- **Cost.** `sound` and `animate tick` run once per tick, `terrain` up to seven times a frame and `lod` up to
  four times. None of them runs per entity or per vertex.

**Left out:** animated textures (`TextureManager.tick`, 0.36%), the HUD tick, the crosshair raycast
(`GameRenderer.pick`), keybinds, the open screen's tick and the connection tick. They stay in other tick,
which the split now shows as `unnamed` (P6.6).

**Acceptance check (A1).** In the dev client with `-PpackMods` (now with DH):

- `hooks installed` lists `SOUND`, `ANIMATE_TICK`, `TERRAIN`, `LOD` and `SOUND_PLAYS`, and `hooks that did not apply` is
  `none`.
- `terrain` and `lod` have time every frame.
- Muting the master volume drops `sound` to about zero.

## P6.4 `lod` carves itself out of its enclosing bucket

**Today's rule (P5.A6).** A bucket is credited by the outermost call that owns one. A bucketed call made
inside another bucketed call counts in the outer bucket: an entity model a HUD mod draws counts as `hud`.

**The exception.** `LOD` always counts as itself. When it ends, its time is credited to `lod` at once, and
the outermost bucket enclosing it is credited its own time less every `lod` call inside it. So with DH,
`terrain` is Sodium's draw and `lod` is DH's, and the two never hold the same nanosecond. Outside any bucket,
`lod` counts as itself, like any outermost bucket.

- Only `LOD` behaves this way. Every other bucket keeps the outermost rule.
- Named time (P5.A6) is unchanged: a handler that encloses a bucket already leaves out all bucket time
  credited inside it, and `lod`'s credit is bucket time like any other.
- Vertices: DH draws through its own GL buffers, not vertex consumers, so no vertex lands inside `lod`.

**Acceptance check (A2).** Unit tests in `core`:

- `terrain` enclosing `lod`: `terrain` gets its elapsed time less `lod`'s, and `lod` its own.
- Two `lod` calls inside one `terrain`: both are subtracted.
- `lod` with nothing enclosing it counts as itself.
- A bucket inside `hud` still counts as `hud`.
- An unbalanced frame (a hook threw past its end) still resets cleanly.

## P6.5 Sound plays in the Mods tab

**Rows.** Every sound play is an attribution-only call (P5.A6), keyed by its sound event and its category:

- `ModKey.Kind` gains `SOUND("sound")`.
- `modId` is the event id's namespace, and `name` is `<event id> (<category>)`, where the category is
  `SoundSource.getName()`. A row's label reads `sound create:steam (block)`, under `create`; vanilla sounds go
  under `minecraft`. The same event played in two categories makes two rows.
- `Probe.beginSound(SoundInstance)` reads `getLocation()` and `getSource()`, which return objects that
  already exist, and looks the key up without allocating: by `ResourceLocation`, then by the category's
  ordinal. Like `beginPacket`, it returns whether it began, and the wrapper ends only what began.
- The wrapper is a `@WrapMethod` on target 4, with `end` in a `finally`.

**What a row measures** is the whole `play` call, including the wait for a channel. A play that returns
early (zero volume, an unloaded engine, a dropped event) costs almost nothing and still counts as a play.
`calls/f` is plays per frame.

**Where the time counts.** A sound play only names time: the bucket it runs in keeps it, as with a packet.

- A boiler's steam stays in `be tick`, and on the fluid tank's row.
- A sound queued with `playDelayed` plays inside `SoundManager.tick`, so it stays in `sound`.
- A sound packet stays in `tasks`, inside its packet's row.
- `[mods x]` keeps its P5.A6 meaning. A play enclosed by a handler or a packet is not outermost, so it adds
  nothing to named time. A play that nothing encloses, in other tick, other render or tasks, is named time
  like a handler.
- The per-mod rollup on the Mods tab sums its rows' inclusive time, so a play inside a packet or handler of the same
  namespace counts in both rows: a vanilla sound the server sends counts twice in the `minecraft` row, once in its
  packet's row (`packet minecraft:sound` or `packet minecraft:sound_entity`) and once in its `sound minecraft:…`
  row. The tab's note that shares overlap covers it, and no advice rule reads the rollup for sounds.

**Thread.** Only plays on the render thread are timed, as for every other hook.

**The sound plays line.** The Frames section of the report and the Frames tab gain one line, under the
overhead lines, summing the sound rows over the session:

```
Sound plays: 1.21 ms a frame, 180 plays a second
```

It is printed only when there were plays. There is no per-frame sound field and no `[sound x]` bracket: a
hitch's top contributors already list the sound rows active in that frame by name.

**Acceptance check (A3).** Unit tests: the label and serialised form of a sound key; one event in two
categories gives two rows; the sound plays line's sums and wording. In the dev client, walking past a
running Create boiler lists `sound create:steam (block)` (or the event the boiler uses) under `create` on the
Mods tab.

## P6.6 The split shows `unnamed`

The tick bracket becomes a full partition of the tick. Every part is disjoint, and the parts add up to the
tick:

```
tick 25.6 [particle tick 0.9, be tick 2.3, entity tick 0.4, sound 15.1, animate tick 0.6, mods 2.0, unnamed 4.3] | terrain 1.2 | lod 9.8 | block entity 1.7 | ...
```

- `unnamed` is `max(0, tick − the tick buckets − named tick time)`: what is left of other tick once the Mods
  tab's share is out. It is printed only when it is above zero, in both the session split and each hitch
  split.
- The word is `unnamed`, not `other tick`. On the Frames tab, `other tick` already means the remainder
  including the Mods tab's share, with an `in the Mods tab` row under it, and that stays as it is.
- The render side keeps its form. `terrain` and `lod` are printed when they have time, like the other
  render buckets, and lead it (P6.3). `other render [mods x]` is unchanged.
- The Frames tab lists the new buckets under tick and render, through the same loops as the existing ones.

**Acceptance check (A4).** Unit tests on `Splits`: a tick bracket's parts add up to the tick; `unnamed` is
never negative and is left out at zero; `sound`, `animate tick`, `terrain` and `lod` print in enum order; a
frame without the new buckets prints as before, apart from `unnamed`.

## P6.7 JSON schema 6, environment and hook lists

- **Schema 6.** `avgBucketsMs` and each frame's `bucketsUs` gain `sound`, `animate tick`, `terrain` and
  `lod`, by label. Sound rows appear under `mods` like any other key; the key's kind word starts its serialised
  form, `sound@create:create:steam (block)`, and no `kind` field is added (plan 6a, decision 3). Nothing else
  changes shape.
- **Environment.** DH's version is already recorded (`distanthorizons: 2.4.5-b`). Without DH, `LOD` is absent
  from all three hook lists, as Flywheel's hook is without Flywheel.
- **Self time** and the overhead estimate need no change: sound plays are attribution-only calls, which the
  calibration's attribution-only pair already prices (P5.C2).

**Acceptance check (A5).** `JsonReportWriterTest`: schema 6, the four labels, and a sound row's fields.

## P6.8 The DH pin and the build

- `gradle.properties` gains `distanthorizons_version=3.3.3-1.21.1`. `build.gradle` adds
  `maven.modrinth:distanthorizons:${distanthorizons_version}` to `packModRuntime`, which `installPackMods` copies
  into `run/mods`, so `-PpackMods` runs the dev client with DH. It stays out of `packModJars`, whose jars
  `extractNestedModJars` searches for nested mod jars to compile against: DH has none, and DH is not on the compile
  classpath (P6.1, change 2; plan 6a, deviation 1).
- `CLAUDE.md`'s toolchain list gains Distant Horizons `3.3.3-1.21.1`. The orchestrator makes that edit
  with the plan's commit, since the user approved the pin; `CLAUDE.md` is otherwise the user's file.
- The DH mixin goes in `hooks.mixin.distanthorizons` with `require = 0`, like the Sodium, Iris and Flywheel
  mixins, and targets `ClientApi` by name with `remap = false`.
- Before 6a's DH task is written, the plan re-runs javap on the four methods against the jar Gradle
  resolves, as `CLAUDE.md` requires.

# P6.B Advice and release (plan 6b)

## P6.9 Three rules

Two new rules and one widened, in P5.B2's format:

| Rule | For | Kind | Fires when | At stake |
| --- | --- | --- | --- | --- |
| `sound-wait` | player | frame rate, or stutter when one sound row took at least 50 ms and at least half of a hitch, or passes `hitch-repeat`'s test, or the `sound` bucket is at least a quarter of the hitch in 3 or more hitches and in at least a quarter of all hitches | the sound rows sum to ≥ 0.5 ms/f, or one of the stutter form's tests | sound play time; in the stutter form, that row's largest time in one of the hitches the finding is about, or for the bucket test the `sound` bucket's largest time in one of those hitches |
| `distant-horizons` | player | frame rate | `lod` ≥ 1.0 ms/f and ≥ 10% of the average frame | `lod` |
| `unattributed-stall` | pack author | stutter | as in P5.B2 (≥ 50 ms and at least half the frame), **or** the unnamed time (as P5.B2 computes it: other tick, other render and tasks, less named time, less the frame's GC pause) is at least a quarter of the hitch in 3 or more hitches and in at least a quarter of all hitches | that time; its largest in one hitch |

| Rule | Finding (example) | Action |
| --- | --- | --- |
| `sound-wait` | Playing sounds kept the game waiting 1.4 ms a frame; `create:steam` (Blocks) played 85 times a second. | Set the Blocks volume to 0% in Music & Sounds: the game then skips those sounds instead of waiting for them. A pack author can report the sound's mod with this file. |
| `distant-horizons` | Distant Horizons draws its far terrain in 3.2 ms of each 10.1 ms frame. | Lower Quality Preset or LOD Chunk Render Distance Radius in Distant Horizons' settings, which its button beside FOV in Options opens. |
| `unattributed-stall`, repeat form | In 30 of 83 hitches, at least a quarter of the frame was in code Renderable does not time, up to 20 ms of client tick. | Profile the same place with a sampling profiler such as spark, which names the code. |

- **The slider's name.** The finding and action name the category by its Music & Sounds slider, from a fixed
  English table in `core`: `master` Master Volume, `music` Music, `record` Jukebox/Note Blocks, `weather`
  Weather, `block` Blocks, `hostile` Hostile Creatures, `neutral` Friendly Creatures, `player` Players,
  `ambient` Ambient/Environment, `voice` Voice/Speech. The action names the category of the costliest row.
- **No double finding.** `hitch-repeat` already skips the Mods tab's keys, so sound rows never trigger it;
  `sound-wait`'s stutter form covers them. The `mod-*` rules count only their own kinds, so sound rows do
  not feed `mod-network` or `mod-handlers`.
- **The muting claim** rests on P6.2: `play` returns before asking for a channel when the volume works out
  to zero.
- **The `sound` bucket test** (added by the user on 2026-10-10, during plan review) covers stalls spread over
  many sound events, none of which reaches a quarter of a hitch alone: queued sounds play inside
  `SoundManager.tick`, so the bucket shows the whole wait. Its finding reads, for example, "In 30 of 83 hitches,
  the sound engine took at least a quarter of the frame (up to 18 ms)." Its action is the muting action, naming
  the slider of the costliest sound row, or no slider when the session has no sound rows.
- **Settled in plan 6b.**
  - The bucket test takes the frame's GC pause off the bucket first, as `unattributed-stall`'s repeat form does with
    its parts, and speaks only when no single sound row passes the row test. Its finding adds the steady sound time
    and the costliest row when there is one, since its action names that row's slider.
  - The slider names are the English captions of Minecraft 1.21.1's `soundCategory.*` keys, and the
    `distant-horizons` action uses DH's own setting names, the same in 2.4.5-b and 3.3.3; both were checked against
    the jars.
  - `sound-wait`'s action says to set the slider to 0%, not to lower it: `play` skips a sound only when its volume
    works out to exactly 0, so any other setting saves nothing.
  - In the row test, `sound-wait`'s action names the category of the stalled or repeated sound, not the
    costliest row's. It leaves out the pack-author sentence for a vanilla sound.
  - `unattributed-stall`'s repeat form takes each of the three parts on its own, as the stall form does, and counts
    a hitch once. It names the window of its worst part, and when both forms pass it is the one named.
  - At zero volume, `play` skips every sound except one whose instance can start silent (vanilla's bees and
    minecarts), which still asks for a channel.

**Acceptance check (B1).** Unit tests for each rule at and just under each threshold, in each form, and for
the slider table. At the other player's base, `distant-horizons` fires, and `sound-wait` fires if the sound
rows reach 0.5 ms a frame.

## P6.10 Order, versions and acceptance

- **Plans.** 6a (measurement: P6.2 to P6.8) on a branch from `main`, then 6b (advice and
  release: P6.9, docs, version) stacked on it. Nothing merges into `main` before 6b's in-pack acceptance.
- **Version.** `mod_version` becomes 0.6.0 in 6b.
- **Docs.** The README's report description gains the new buckets, the sound rows and `unnamed`; the base
  spec and the P5 addendum take the amendments in P6.11.
- **Dev client** (6a). With `-PpackMods`, which now includes DH: the A1, A3 and A4 checks.
- **In-pack acceptance** (6b), short, as the user prefers:
  1. One 30 s session at the other player's base, from the 16:08 spot (-1169 91 -1678). Expected:
     `sound` and `lod` named; `unnamed` under a quarter of the tick in most hitches; `sound …` rows on the
     Mods tab; `distant-horizons` advice, and `sound-wait` if the sound rows reach 0.5 ms a frame.
  2. One 30 s session at the user's base: nothing that was named before reads differently, apart from the
     new parts.
  The results go in `docs/acceptance/phase-6.md`.

## P6.11 Spec amendments collected

| Section | Change | Plan |
| --- | --- | --- |
| §3.2 | Buckets gain `sound`, `animate tick`, `terrain`, `lod`; `lod` carves itself out of its enclosing bucket | 6a |
| §4 | The hook table gains P6.2's targets 1 to 8 | 6a |
| §6.1 | The tick bracket partitions the tick, with `unnamed`; the sound plays line | 6a |
| §6.2 | Schema 6 | 6a |
| P5.A6 | The `lod` exception to the outermost-bucket rule | 6a |
| P5.A7 | Sound rows in the Mods tab | 6a |
| P5.B2 | `sound-wait`, `distant-horizons`; `unattributed-stall`'s repeat form | 6b |
| P5.4 | "No finer split of other tick" and the `terrain` candidate are now done in part; the rest is P6.12 | 6b |
| §5.1, §7 | `/renderable overlay` and the overlay key write `overlayEnabled` (P6.13) | 6b |
| P4.B6 | Item 4's copy taken on first use is superseded by P6.13 | 6b |

## P6.12 What phase 6 does not do

- **No timing of the small tick calls:** animated textures, the HUD tick, the crosshair raycast, keybinds,
  the screen tick and the connection tick stay in `unnamed`. spark put them together under 1% at the base.
- **No split of DH's own work.** `lod` is one figure; which of DH's passes or buffers costs most is DH's
  business, and its F3 page shows some of it.
- **No separate shadow-pass terrain.** Iris's shadow pass draws terrain through its accessor, outside
  `terrain`; it stays in other render, or under the shadow column where a bucketed call runs (P5.D4).
- **No fix for the sound wait.** Renderable names it; it does not make channel creation asynchronous or cap
  sounds.
- **No DH API events,** for the reasons in P6.1.
- **No overlay rework.** The Observable-style rework (P5's carried items) is its own phase.

## P6.13 One switch for the overlay (a 0.5.0 bug)

**Observed.** In 0.5.0, turning `overlayEnabled` off in the mod's config screen left the labels on until the game
restarted. `WorldOverlay` copied the setting on first use (§P4.B6 item 4) and never read it again.

**Design (the user's choice, 2026-10-11).** The setting is the only state. `core.OverlaySwitch` reads it every
time and writes it, through a small interface that keeps `core` free of NeoForge; `WorldOverlay` reads it every
frame (`ModConfigSpec.ConfigValue.get()` returns a cached value, so this allocates nothing), and
`/renderable overlay [on|off]` and the overlay key write and save it. A change in the config screen therefore
shows at once, and `overlay off` lasts across restarts as well as sessions. Before the config has loaded, labels
count as on and nothing is written, since NeoForge throws on reading a setting that early.

**Acceptance check.** Unit tests in `OverlaySwitchTest`. In the pack: after a session, turn labels off in the
mod's config screen and see them go without a restart; turn them on again there; `/renderable overlay off`, then
a restart, keeps them off.
