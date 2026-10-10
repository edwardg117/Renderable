# Renderable Phase 6a Implementation Plan: measurement

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Executed** on 2026-10-10 on `phase-6a-measurement` (HEAD `4af0ad0`), **294 tests**. Reviews during execution
> added, beyond the task text below: one more `lod` test in `SessionTest`; `SoundPlays.perSecond(double)`, with three
> tests, which the Sound plays line's rate goes through (whole from 9.95 up, one decimal below, "under 0.1" for a
> rate that would read "0.0"); sounds in the Mods tab's empty-state sentence; `Facts.hitch` reading
> `FrameRecord.unnamedTickNanos()`; `HookInstallsTest.theThirdPartyConfigIsOptionalSoAMissedTargetOnlyLogs`;
> ASCII-only comments in the Gradle files; and more documentation in the specs. The commits are the record of what
> was built.

**Goal:** Name the two costs the phase 5 acceptance found without a name. Time the sound engine's tick and every
sound play, the random block effects, the chunk-layer draws and Distant Horizons' LOD drawing; show the plays as rows
in the Mods tab with a "Sound plays" line; and make the tick's brackets a full partition of the tick with `unnamed`.

**Architecture:**

- **`core`** gains four buckets (`TERRAIN` and `LOD` on the render side, `SOUND` and `ANIMATE_TICK` on the tick
  side), a `lod` exception to the outermost-bucket rule in `Session`, `unnamed` in `Splits`, a `SOUND` kind of
  `ModKey`, and `SoundPlays`, the session's sound rows summed.
- **Vanilla mixins** (required): two `@WrapOperation`s in `Minecraft.tick`, one `@WrapOperation` on all seven
  `renderSectionLayer` calls in `LevelRenderer.renderLevel`, and a `@WrapMethod` on `SoundEngine.play` that calls
  `Probe.beginSound`, which keys a play without allocating through `SoundKeys`. A required `@WrapMethod` on NeoForge's
  `ClientHooks.dispatchRenderStage(RenderType, ...)` steps out of `terrain` while each layer's `RenderLevelStageEvent`
  runs (deviation 4).
- **An optional Distant Horizons mixin** (`hooks.mixin.distanthorizons`, `require = 0`, gated by the mixin plugin)
  wraps DH's four `ClientApi` render methods by name, so nothing compiles against DH.
- **Output.** The JSON goes to schema 6 (four bucket labels; sound rows in `mods`). The report's Frames section and
  the Frames tab gain the Sound plays line. The text split and every hitch line gain `unnamed`.

**Tech Stack:** Java 21, ModDevGradle 2.0.147, NeoForge 21.1.220, Mixin 0.8.7 with MixinExtras 0.5.0
(`@WrapOperation`, `@WrapMethod`), ASM 9.8 (the hook checker and its tests), Gson's streaming writer, JUnit 5;
Distant Horizons 3.3.3 from the Modrinth Maven, for the dev client and javap only.

**Spec:** `docs/superpowers/specs/2026-10-10-renderable-phase-6-design.md` §P6.2 to §P6.8 (part P6.A) and the rows of
§P6.11 marked 6a. Read the file on disk: its §P6.3 has an uncommitted edit that adds the fifth hook, `SOUND_PLAYS`.

**Repository:** `H:\repos\Renderable`. The orchestrator commits the phase 6 spec's `SOUND_PLAYS` edit and this plan on
`main` first, then creates the branch `phase-6a-measurement` from `main` before Task 1. Commits are YubiKey-signed:
the orchestrator commits after telling the user to touch the key. **Subagents stage and report; they do not commit.**

**Ordering:** the first phase 6 plan. Plan 6b (advice, README, version 0.6.0 and the in-pack acceptance) stacks on
this branch, and nothing merges into `main` before 6b's in-pack acceptance (§P6.10). Run the tasks in order:
Task 2 needs Task 1's `LOD`; Task 4's fixture relies on Tasks 2 and 3; Task 5 needs Tasks 1 and 4; Task 6 needs
Tasks 1, 2 and 5; Task 7 documents Tasks 1 to 6; Task 8 runs last.

**Verified facts you can rely on** (javap and reads on 2026-10-10, against
`build/moddev/artifacts/neoforge-21.1.220-merged.jar`, the Gradle-cached Sodium 0.8.13 and Iris 1.8.14-beta.1 jars,
the Distant Horizons 3.3.3 jar from the Modrinth Maven, and read-only, the deployed DH 2.4.5-b jar):

- **`Minecraft.tick()V`** is the only `tick` in `Minecraft`. In it, in order:
  - 10 `INVOKESTATIC ClientHooks.fireClientTickPre()V`;
  - 502 `INVOKEVIRTUAL net/minecraft/client/sounds/MusicManager.tick()V`, skipped while `pause` is set (492-495);
  - 513 `INVOKEVIRTUAL net/minecraft/client/sounds/SoundManager.tick(Z)V` with the `pause` field as its argument:
    the only call of that method in `tick()`. `SoundManager.tick(boolean)` is public and returns void;
  - 773 `INVOKEVIRTUAL net/minecraft/client/multiplayer/ClientLevel.animateTick(III)V`, reached only when
    `!pause && isLevelRunningNormally()` (734-745): the only call of that method in `tick()`.
    `ClientLevel.animateTick(int, int, int)` is public and returns void;
  - 866 `INVOKESTATIC ClientHooks.fireClientTickPost()V`.

  So both calls sit inside the tick window `MinecraftMixin`'s boundaries already open and close.
- **`LevelRenderer.renderLevel`** is the only method of that name, descriptor
  `(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V`.
  It calls the private
  `renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V` by
  `INVOKEVIRTUAL` at 569 (`solid`, just after the profiler's `terrain` section at 547), 630, 663, 2561
  (`translucent`), 2588, 2755 and 2795. Those seven are the only calls of it in the class.
- **`SoundEngine.play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V`** is public and the only `play` in
  `SoundEngine`. It reads `loaded` at 1 and returns at once when false; posts `ClientHooks.playSound` at 9 (NeoForge's
  `PlaySoundEvent`, whose handlers may replace or drop the sound); calls `calculateVolume` at 168 and returns at zero
  volume (199-226) before asking for a channel; reads the listener's gain at 347; calls `ChannelAccess.createHandle`
  at 407 and `CompletableFuture.join` at 414. `SoundEngine.tickNonPaused()` calls `play` at 524 for the sounds
  `playDelayed` queued, so those plays run inside `SoundManager.tick`.
- **`SoundInstance`** declares `ResourceLocation getLocation()` and `SoundSource getSource()` (abstract). `SoundSource`
  is an enum of ten constants whose `getName()` returns a field: `master`, `music`, `record`, `weather`, `block`,
  `hostile`, `neutral`, `player`, `ambient`, `voice`, in ordinal order.
- **Sodium 0.8.13** (nested jar `net.caffeinemc.sodium-neoforge-0.8.13+mc1.21.1-mod.jar`, which the build extracts
  to `build/nested-mod-jars/`): of its 727 classes only `mixin/core/render/world/LevelRendererMixin` names
  `renderSectionLayer`, as an `@Overwrite`. Its injections into `renderLevel` are an `@Inject` at a field
  (`globalBlockEntities`); nothing redirects the seven calls.
- **Iris 1.8.14-beta.1:** `MixinLevelRenderer_SkipRendering` has a MixinExtras `@WrapWithCondition` on the
  `renderSectionLayer` INVOKE in `renderLevel`, which MixinExtras chains with a `@WrapOperation`;
  `MixinLevelRenderer` `@Inject`s at HEAD and RETURN of `renderSectionLayer` (inside the call);
  `LevelRendererAccessor` invokes `renderSectionLayer` for the shadow pass, outside `renderLevel`.
- **NeoForge's stage event ends every layer.** `net.neoforged.neoforge.client.ClientHooks` has two public static void
  `dispatchRenderStage` overloads: one taking a `RenderLevelStageEvent$Stage` and a `PoseStack`, and
  `dispatchRenderStage(Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/LevelRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V`,
  which maps the type to its stage (`Stage.fromRenderType`) and calls the first. So a selector needs the descriptor.
  - In `LevelRenderer`, only `renderSectionLayer` calls the `RenderType` overload: `INVOKESTATIC` at 555, just before
    its return at 562. `renderLevel` calls only the `Stage` overload.
  - Sodium 0.8.13's `@Overwrite` of `renderSectionLayer` ends with `PlatformLevelRenderHooks.runChunkLayerEvents` (71,
    then its return at 76). On NeoForge that is `NeoForgeLevelRenderHooks.runChunkLayerEvents`, which calls the
    `RenderType` overload at 11.
  - So inside the `terrain` wrapper, both renderers post the layer's `RenderLevelStageEvent` last (deviation 4).
- **Distant Horizons 3.3.3** (`maven.modrinth:distanthorizons:3.3.3-1.21.1` resolves; its POM has no dependencies and
  the artifact is `DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar`):
  - mod id `distanthorizons`, version `3.3.3`; mixin config `DistantHorizons.neoforge.mixins.json`;
  - `com/seibel/distanthorizons/core/api/internal/ClientApi.class` is in DH's own jar. There is no `META-INF/jarjar`
    (its `META-INF/jars` holds Fabric API jars for the Fabric side);
  - `ClientApi` has `public void renderLods()`, `public void renderDeferredLodsForShaders()`,
    `public void renderFadeOpaque()` and `public void renderFadeTransparent()`, each descriptor `()V` and the only
    method of its name. The deployed 2.4.5-b jar has the same four;
  - `com.seibel.distanthorizons.neoforge.mixins.client.MixinLevelRenderer` `@Inject`s at HEAD of `renderSectionLayer`
    (not cancellable) and calls them for `RenderType.solid()`, `translucent()`, `cutout()` and `tripwire()`;
  - on NeoForge nothing else calls the four (only `FabricClientProxy`, which is Fabric's), and none calls another:
    `renderLods` and `renderDeferredLodsForShaders` call `renderLodLayer(Z)V`, and the fades call
    `IDhVanillaFadeRenderer.render`.
- **`build.gradle`:**
  - `packModJars` feeds only `extractNestedModJars`, a `zipTree` that keeps Sodium's nested jars or
    `META-INF/jarjar/flywheel-*.jar` and lands on the compile classpath through `compileOnly nestedModJars`;
  - `packModRuntime` (`transitive = false`) feeds `installPackMods`, which syncs it into `run/mods` only under
    `-PpackMods` and empties `run/mods` otherwise.
- **`RenderableMixinPlugin`** gates a mixin by its last package segment through `REQUIRED_MOD` and
  `LoadingModList.get().getModFileById`. Its `postApply` runs `HookInstalls.check`. A gated mixin that never applies
  never reaches `postApply`, so its hook is in none of the three lists: Flywheel's hook without Flywheel.
- **`InjectionCheck`** reads a method selector as a name or a name with a descriptor, and an `@At("INVOKE")` target as
  `Lowner;name(desc)`. A `@WrapMethod` has no calls. `present()` needs a method that matches a selector.
- **`HookInstallsTest`** fails for an injector missing from `HookInstalls.HOOKS`/`BOUNDARIES` and for a `Hook`, other
  than `IRIS_SHADOW` and `EVENTS`, that no injector maps to. So a `Hook` value goes in the same task as its injectors.
- **`Session`:**
  - the outermost bucket-owning call (`ordinaryDepth == 0` after `end`) credits its bucket;
  - named time is an outermost attribution-only call's time less `bucketTotal` growth inside it;
  - `onRenderPre` resets an unbalanced stack (`depth != 0`) and counts it in `unbalancedFrames`.
- **`Facts.of`** switches on `ModKey.Kind` with a switch *statement*, so a new `SOUND` constant compiles, and sound
  rows feed none of the `mod-*` rules.
- **`Probe.beginBucket(Bucket, Hook)` and `Probe.endBucket()`** both decline off the render thread. `Probe.end()` does
  not check the thread, so a wrapper calls it only for a call it began.
- **The test classpath** has no Minecraft, NeoForge, FML or Mixin. `Probe`, `RenderableMixinPlugin` and the mixins
  cannot be loaded in a test: mixin classes are read as bytes, and the plugin's map is read as source.
- **`run/`** (read 2026-10-10, not to be edited by an agent): `options.txt` has `maxFps:260`, `enableVsync:false`
  and `soundCategory_master:1.0`; `run/saves` holds `New World`.
- **The suite is 264 tests** at `4171b84` on `main` (`./gradlew test --rerun --no-daemon`).
- **Files are CRLF** in the working tree (`core.autocrlf` is true).
- **A typed `\uXXXX` escape** becomes the literal character in the Edit/Write tools and in GNU sed. No step here
  needs one.

## Global Constraints

- `core` and the report writers have no Minecraft imports (`KeysTest.coreHasNoMinecraftImports` and
  `reportWritersHaveNoMinecraftImports`); the new `hooks.SoundKeys` has none either, so it can be tested.
- Every mixin handler's first statement reads `Probe.ACTIVE`; when it is false, the handler makes the wrapped call at
  once and returns.
- No allocation on hot paths while sampling: a key's accumulator is made once on first sight and cached (sounds:
  `HashMap` by event id, then an array by category ordinal). Nothing at all when idle.
- `Session` is render-thread only: every `Probe` entry point that begins a call declines off the render thread
  (`Thread.currentThread() != renderThread`), as `beginBucket` and `beginPacket` do.
- Every timed call is wrapped in try/finally, and a wrapper ends only what its begin began (`boolean timed`).
- Sodium, Iris, Flywheel, Accelerated Rendering and Distant Horizons mixins are optional: listed in
  `renderable.thirdparty.mixins.json` (`"required": false`, `"defaultRequire": 0`), in a sub-package named after the
  mod, with `require = 0`, gated by `RenderableMixinPlugin.REQUIRED_MOD`.
- Vanilla and NeoForge mixins are required: `renderable.mixins.json` (`"required": true`, `"defaultRequire": 1`).
- Every injector is listed in `HookInstalls.HOOKS` (or `BOUNDARIES`). Write a method selector as a name, or a name
  with a descriptor, and an `@At` target as `Lowner;name(desc)`.
- No `Hook` name is spelt as a string literal in main code (`SourceGuardsTest`).
- Pins: NeoForge `21.1.220`; Sodium `0.8.13` (`mc1.21.1-0.8.13-neoforge`); Iris `1.8.14-beta.1`
  (`1.8.14-beta.1+1.21.1-neoforge`); Create `6.0.10` with Flywheel `1.0.6`; Distant Horizons `3.3.3`
  (`3.3.3-1.21.1`); MixinExtras `0.5.0`; Java 21.
- Distant Horizons is never on the compile classpath; its mixin names its target by string.
- Buckets are written to reports and the JSON by label, never by ordinal.
- Run the suite with `./gradlew test --rerun --no-daemon`. Use the Gradle wrapper only.
- Do not edit `run/options.txt`. Never stop a Java process this plan did not start. Never open the user's MCUpdater
  instance.
- Commits use conventional prefixes (`feat:`, `fix:`, `docs:`, `test:`, `build:`); subagents stage and report.

## Review Focus

The five input classes or failure modes most likely to bite a user that no task's tests would otherwise exercise,
most likely first. Each has a test in the task that owns its code, marked "Review focus N".

1. **Distant Horizons absent** (most players). The plugin skips the DH mixin, `LOD` is in none of the three hook
   lists, nothing is logged, and `lod` reads 0 and is left out of the split. Test: Task 6,
   `theDistantHorizonsMixinIsSkippedWithoutDistantHorizons`; Task 8 Step 7's plain run shows it in a game.
2. **Zero-volume and early-return plays** (a category muted, which 6b's advice will recommend; a `PlaySoundEvent`
   handler that drops the sound; an unloaded engine). Each still counts as a play at near-zero cost. The Sound plays
   line prints whenever anything played, `0.00 ms a frame` included, and its rate goes through
   `SoundPlays.perSecond`: whole from 9.95 a second up, one decimal below, and "under 0.1" for a rate above zero
   that would read "0.0". Test: Task 4, `SoundPlaysTest.aPlayThatCostNothingStillCountsAndTheLineStillPrints`.
3. **A sound played off the render thread** (a mod playing from its network or worker thread). Not timed: no key is
   made and the session's stack is untouched, and the play itself runs as it would without Renderable. Test: Task 5,
   `SourceGuardsTest.everyAttributionOnlyEntryPointDeclinesOffTheRenderThread`.
4. **DH present with a render method renamed or re-signed** (a DH update). That injector misses quietly (`require = 0`,
   with the descriptor in its selector), and `LOD` is listed under "hooks that did not apply", not read as a silent 0.
   Test: Task 6, `aDistantHorizonsRenderMethodThatWasRenamedOrResignedDidNotApply`.
5. **A sound instance that names no event or no category, or whose sound a `PlaySoundEvent` handler replaces** (a
   mod's own `SoundInstance`). With no event or no category, the play is untimed and Renderable throws nothing. A
   replaced sound is keyed by the sound that was asked for, because `beginSound` reads the instance before the play
   runs. Test: Task 5, `SoundKeysTest.anInstanceThatNamesNoEventOrNoCategoryIsNotTimed`.

---

## File map

```
gradle.properties                                                     Modify: distanthorizons_version (Task 6)
build.gradle                                                          Modify: DH in packModRuntime (Task 6)
CLAUDE.md                                                             Modify, by the orchestrator: the DH pin (Task 6)
docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md   Modify (Task 7)
docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md        Modify: P5.A6, P5.A7 (Task 7)
docs/superpowers/specs/2026-10-10-renderable-phase-6-design.md        Modify: P6.8, deviation 1 (Task 7)
src/main/resources/renderable.mixins.json                             Modify: three mixins (Task 5)
src/main/resources/renderable.thirdparty.mixins.json                  Modify: the DH mixin (Task 6)
src/main/java/io/github/edwardg117/renderable/
  core/Bucket.java                                                    Rewrite: four buckets (Task 1)
  core/Session.java                                                   Modify: the lod carve-out (Task 2), innermostBucket (Task 5)
  core/FrameRecord.java                                               Modify: unnamedTickNanos (Task 3)
  core/FrameStats.java                                                Modify: avgUnnamedTickNanos (Task 3)
  core/Splits.java                                                    Rewrite: unnamed (Task 3)
  core/ModKey.java                                                    Modify: the SOUND kind (Task 4)
  core/SoundPlays.java                                                Create (Task 4)
  core/Hook.java                                                      Modify: four hooks (Task 5), LOD (Task 6)
  hooks/SoundKeys.java                                                Create (Task 5)
  hooks/Probe.java                                                    Modify: beginSound (Task 5)
  hooks/HookInstalls.java                                             Modify (Tasks 5, 6)
  hooks/RenderableMixinPlugin.java                                    Modify: the DH gate (Task 6)
  hooks/mixin/MinecraftMixin.java                                     Modify: sound, animate tick (Task 5)
  hooks/mixin/LevelRendererMixin.java                                 Create (Task 5)
  hooks/mixin/SoundEngineMixin.java                                   Create (Task 5)
  hooks/mixin/ClientHooksMixin.java                                   Create (Task 5)
  hooks/mixin/distanthorizons/ClientApiMixin.java                     Create (Task 6)
  ui/screen/Rows.java                                                 Modify: the Sound plays row (Task 4)
  report/TextReportWriter.java                                        Modify: the Sound plays line (Task 4)
  report/JsonReportWriter.java                                        Modify: schema 6 (Task 1)
src/test/java/io/github/edwardg117/renderable/
  core/KeysTest.java                                                  Modify (Tasks 1, 4)
  core/TestSessions.java                                              Modify: phase6Buckets (Task 1), phase6 (Task 4)
  core/SessionTest.java                                               Modify (Tasks 2, 5)
  core/SplitsTest.java                                                Rewrite (Task 3)
  core/SoundPlaysTest.java                                            Create (Task 4)
  core/SourceGuardsTest.java                                          Modify (Task 5)
  hooks/SoundKeysTest.java                                            Create (Task 5)
  hooks/HookInstallsTest.java                                         Modify (Tasks 5, 6)
  report/JsonReportWriterTest.java                                    Modify (Tasks 1, 4)
  report/TextReportWriterTest.java                                    Modify (Tasks 3, 4)
  ui/screen/RowsTest.java                                             Modify (Tasks 1, 4)
```

**Expected test totals:** 264 at the start, 267 after Task 1, 274 after Task 2, 276 after Task 3, 282 after Task 4,
287 after Task 5 and 289 after Task 6, which is also the total at the end (Tasks 7 and 8 add none). Review rounds may
add tests; a shortfall is worth investigating and an excess is not.

**How to run the suite** (the build cache can restore stale results, so always force it):

```bash
./gradlew test --rerun --no-daemon
```

---

### Task 1: Four buckets, by label in JSON schema 6

**Files:**
- Rewrite: `src/main/java/io/github/edwardg117/renderable/core/Bucket.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/JsonReportWriter.java` (`SCHEMA`)
- Modify: `src/test/java/io/github/edwardg117/renderable/core/TestSessions.java` (`phase6Buckets`)
- Test: `src/test/java/io/github/edwardg117/renderable/core/KeysTest.java` (append 1)
- Test: `src/test/java/io/github/edwardg117/renderable/report/JsonReportWriterTest.java` (update 2 lines, append 1)
- Test: `src/test/java/io/github/edwardg117/renderable/ui/screen/RowsTest.java` (append 1)

**Interfaces:**
- Consumes: nothing new.
- Produces: `Bucket.TERRAIN` and `Bucket.LOD` (render), `Bucket.SOUND` and `Bucket.ANIMATE_TICK` (tick), labels
  `terrain`, `lod`, `sound`, `animate tick`; `JsonReportWriter.SCHEMA == 6`;
  `public static SessionResult TestSessions.phase6Buckets()`.

Spec §P6.3 and §P6.7. Every use of a bucket goes through `ordinal()`, `VALUES` or `label()`, and nothing stores an
ordinal. So the split, the Frames tab (its two loops over `Bucket.VALUES`), `avgBucketsMs` and each frame's
`bucketsUs` pick the new buckets up with no further change. The render buckets lead the enum, so terrain and its
LODs lead the render side. Nothing fills the buckets until Tasks 5 and 6; the fixture drives `Session` directly.

- [ ] **Step 1: Write the failing tests**

In `TestSessions.java`, add this method directly after `manyMods()`, whose end is unique:

```java
        now[0] += MS;
        s.onRenderPre(0, 0);
        return s.finish();
    }
```

```java

    /**
     * The phase 6 buckets (spec addendum §P6.3), each on its own so the figures do not depend on the lod carve-out:
     * one 16 ms frame with render 0-8 holding terrain 1-4 and lod 4-6, then the tick 8-10 holding sound 8-9.5 and
     * animate tick 9.5-10. So terrain 3, lod 2, other render 3, sound 1.5, animate tick 0.5, other tick 0, swap 6.
     */
    public static SessionResult phase6Buckets() {
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 25, 2), () -> now[0], WALL_START);
        s.onRenderPre(0, 0);
        now[0] = 1 * MS;     s.begin(null, Bucket.TERRAIN);
        now[0] = 4 * MS;     s.end();
        now[0] = 4 * MS;     s.begin(null, Bucket.LOD);
        now[0] = 6 * MS;     s.end();
        now[0] = 8 * MS;     s.onRenderPost();
        now[0] = 8 * MS;     s.onTickPre();
        now[0] = 8 * MS;     s.begin(null, Bucket.SOUND);
        now[0] = 9_500_000;  s.end();
        now[0] = 9_500_000;  s.begin(null, Bucket.ANIMATE_TICK);
        now[0] = 10 * MS;    s.end();
        now[0] = 10 * MS;    s.onTickPost();
        now[0] = 16 * MS;    s.onRenderPre(0, 0);
        return s.finish();
    }
```

Append to `KeysTest`:

```java
    @Test
    void phase6BucketsLeadTheirSideInEnumOrder() {
        assertEquals(List.of(Bucket.TERRAIN, Bucket.LOD, Bucket.BLOCK_ENTITY, Bucket.ENTITY, Bucket.PARTICLE_RENDER,
                Bucket.FLYWHEEL, Bucket.CHUNK_UPLOAD, Bucket.LIGHT, Bucket.HUD, Bucket.PARTICLE_TICK, Bucket.BE_TICK,
                Bucket.ENTITY_TICK, Bucket.SOUND, Bucket.ANIMATE_TICK), List.of(Bucket.VALUES),
                "terrain and its LODs lead the render side; the split prints in this order");
        assertTrue(Bucket.TERRAIN.isRender());
        assertTrue(Bucket.LOD.isRender());
        assertFalse(Bucket.SOUND.isRender(), "the sound engine ticks inside the client tick");
        assertFalse(Bucket.ANIMATE_TICK.isRender());
        assertEquals("terrain", Bucket.TERRAIN.label());
        assertEquals("lod", Bucket.LOD.label());
        assertEquals("sound", Bucket.SOUND.label());
        assertEquals("animate tick", Bucket.ANIMATE_TICK.label());
    }
```

In `JsonReportWriterTest`, replace this line in `schemaModEnvironmentAndSession` (it occurs once):

```java
        assertEquals(5, o.get("schema").getAsInt());
```

with:

```java
        assertEquals(6, o.get("schema").getAsInt());
```

and in `theWriterOverloadProducesExactlyTheStringOverloadsBytes` replace these two lines (each occurs once):

```java
        assertEquals(5, JsonParser.parseString(out.toString()).getAsJsonObject().get("schema").getAsInt());
        assertTrue(out.toString().startsWith("{\n  \"schema\": 5,"), out.toString().substring(0, 40));
```

with:

```java
        assertEquals(6, JsonParser.parseString(out.toString()).getAsJsonObject().get("schema").getAsInt());
        assertTrue(out.toString().startsWith("{\n  \"schema\": 6,"), out.toString().substring(0, 40));
```

Then append to `JsonReportWriterTest`:

```java
    @Test
    void schema6NamesThePhase6BucketsByLabel() {
        assertEquals(List.of("terrain", "lod", "block entity", "entity", "particle render", "flywheel", "chunk upload",
                        "light", "hud", "particle tick", "be tick", "entity tick", "sound", "animate tick"),
                List.copyOf(parse().getAsJsonObject("stats").getAsJsonObject("avgBucketsMs").keySet()),
                "every bucket by label, in enum order, zeros included");

        JsonObject o = JsonParser.parseString(JsonReportWriter.write(TestSessions.phase6Buckets(), environment(), "0.6.0"))
                .getAsJsonObject();
        JsonObject buckets = o.getAsJsonArray("frames").get(0).getAsJsonObject().getAsJsonObject("bucketsUs");
        assertEquals(List.of("terrain", "lod", "sound", "animate tick"), List.copyOf(buckets.keySet()),
                "a frame lists the buckets that had time, by label");
        assertEquals(3_000, buckets.get("terrain").getAsLong());
        assertEquals(2_000, buckets.get("lod").getAsLong());
        assertEquals(1_500, buckets.get("sound").getAsLong());
        assertEquals(500, buckets.get("animate tick").getAsLong());
        assertEquals(1.5, o.getAsJsonObject("stats").getAsJsonObject("avgBucketsMs").get("sound").getAsDouble(), 1e-9);
    }
```

Append to `RowsTest`:

```java
    @Test
    void theFramesTabListsThePhase6BucketsThroughItsLoops() {
        List<String> rows = describe(Rows.frames(TestSessions.phase6Buckets()));
        int tick = rows.indexOf("0 - tick|2.00 ms");
        int render = rows.indexOf("0 - render|8.00 ms");
        assertTrue(tick >= 0 && render > tick, rows.toString());
        assertEquals(List.of("0 -   sound|1.50 ms", "0 -   animate tick|0.50 ms", "0 -   other tick|0.00 ms"),
                rows.subList(tick + 1, tick + 4), "the tick's buckets in enum order, then the rest");
        assertEquals(List.of("0 -   terrain|3.00 ms", "0 -   lod|2.00 ms", "0 -   other render|3.00 ms"),
                rows.subList(render + 1, render + 4), "terrain and lod lead the render side");
    }
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*KeysTest' --tests '*JsonReportWriterTest' --tests '*RowsTest'`
Expected: compilation failure, `cannot find symbol: variable TERRAIN` (and `LOD`, `SOUND`, `ANIMATE_TICK`).

- [ ] **Step 3: Rewrite `Bucket`**

Replace the whole of `Bucket.java` with:

```java
package io.github.edwardg117.renderable.core;

/**
 * Per-frame time buckets (spec §3.2). Render buckets nest inside render time, tick buckets inside tick time.
 * Phase 1 fills BLOCK_ENTITY and ENTITY; the rest exist so later phases do not change the frame record shape.
 *
 * <p>The split prints buckets in this order, so terrain and its LODs, drawn first and usually largest, lead the
 * render side (spec addendum §P6.3). Ordinals are never stored: the reports and the JSON name a bucket by its label.
 */
public enum Bucket {
    /** The chunk layers {@code LevelRenderer.renderLevel} draws, solid through tripwire (spec addendum §P6.3). */
    TERRAIN(true),
    /** Distant Horizons' LOD drawing, in every pass its head injection runs (spec addendum §P6.3). */
    LOD(true),
    BLOCK_ENTITY(true),
    ENTITY(true),
    PARTICLE_RENDER(true),
    FLYWHEEL(true),
    CHUNK_UPLOAD(true),
    LIGHT(true),
    /** {@code Gui.render}: every HUD layer and the HUD's own events (spec addendum §P5.A1). */
    HUD(true),
    PARTICLE_TICK(false),
    BE_TICK(false),
    ENTITY_TICK(false),
    /** {@code SoundManager.tick}: playing sounds' updates and the sounds {@code playDelayed} queued (§P6.3). */
    SOUND(false),
    /** {@code ClientLevel.animateTick}: the random block display effects around the player (§P6.3). */
    ANIMATE_TICK(false);

    public static final Bucket[] VALUES = values();
    public static final int COUNT = VALUES.length;

    private final boolean render;

    Bucket(boolean render) {
        this.render = render;
    }

    /** True when this bucket's time is part of render time rather than tick time. */
    public boolean isRender() {
        return render;
    }

    /** Lower-case label with spaces, for reports. */
    public String label() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
```

- [ ] **Step 4: Bump the schema**

In `JsonReportWriter.java`, replace (it occurs once):

```java
    public static final int SCHEMA = 5;
```

with:

```java
    /** Schema 6 (spec addendum §P6.7): {@code terrain}, {@code lod}, {@code sound} and {@code animate tick} buckets. */
    public static final int SCHEMA = 6;
```

`avgBucketsMs` and `bucketsUs` already loop over `Bucket.VALUES` by label, so nothing else changes.

- [ ] **Step 5: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **267** tests, 0 failures. No existing expectation moves: no fixture fills the new
buckets, and every loop leaves a bucket with no time out of the split, the Frames tab and `bucketsUs`.

- [ ] **Step 6: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/Bucket.java src/main/java/io/github/edwardg117/renderable/report/JsonReportWriter.java src/test/java/io/github/edwardg117/renderable/core/TestSessions.java src/test/java/io/github/edwardg117/renderable/core/KeysTest.java src/test/java/io/github/edwardg117/renderable/report/JsonReportWriterTest.java src/test/java/io/github/edwardg117/renderable/ui/screen/RowsTest.java
```

Report to the orchestrator for the commit: `feat(core): terrain, lod, sound and animate tick buckets, JSON schema 6`.

---

### Task 2: `lod` carves itself out of the bucket around it

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Session.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/SessionTest.java` (append 7)

**Interfaces:**
- Consumes: `Bucket.LOD` (Task 1).
- Produces: no new signature. `Session.begin(Accumulator, Bucket)` and `Session.end()` credit `Bucket.LOD` wherever it
  runs, once however it nests, and credit the outermost bucket around it its own time less every `lod` call inside
  it. `bucketTotal`, which named time subtracts, still grows by exactly the bucketed time.

Spec §P6.4. Three fields carry it: `lodDepth` (open `lod` calls), `lodTotal` (the `lod` credited so far this frame) and
`lodMark` (`lodTotal` when the outermost bucket-owning call began). The unbalanced-frame reset clears `lodDepth`.
Otherwise a `lod` left open by a throw would stop every later `lod` from counting.

- [ ] **Step 1: Write the failing tests**

Append to `SessionTest`:

```java
    @Test
    void lodCarvesItselfOutOfTheTerrainThatEnclosesIt() {
        Session s = session(10_000);
        at(0);  s.onRenderPre(0, 0);
        at(1);  s.begin(null, Bucket.TERRAIN);       // the solid layer
        at(2);  s.begin(null, Bucket.LOD);           // Distant Horizons' head injection draws its LODs
        at(6);  s.end();
        at(8);  s.end();
        at(9);  s.onRenderPost();
        at(20); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(4 * MS, f.bucket(Bucket.LOD), "lod counts as itself");
        assertEquals(3 * MS, f.bucket(Bucket.TERRAIN), "terrain's 7 ms less the 4 ms of lod inside it");
        assertEquals(2 * MS, f.otherRenderNanos(), "9 ms of render less the 7 bucketed: no nanosecond twice");
    }

    @Test
    void everyLodInsideOneTerrainIsSubtracted() {
        Session s = session(10_000);
        at(0);  s.onRenderPre(0, 0);
        at(1);  s.begin(null, Bucket.TERRAIN);
        at(2);  s.begin(null, Bucket.LOD); at(4); s.end();
        at(5);  s.begin(null, Bucket.LOD); at(6); s.end();
        at(8);  s.end();
        at(10); s.begin(null, Bucket.TERRAIN); at(11); s.end();     // a layer DH leaves alone
        at(12); s.onRenderPost();
        at(20); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(3 * MS, f.bucket(Bucket.LOD));
        assertEquals(5 * MS, f.bucket(Bucket.TERRAIN), "7 less both lods' 3, then the other layer's 1");
        assertEquals(4 * MS, f.otherRenderNanos(), "12 - 3 - 5");
    }

    @Test
    void aLodThatNothingEnclosesCountsAsItself() {
        Session s = session(10_000);
        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(null, Bucket.LOD);            // the layer called outside renderLevel, as Iris's shadow pass does
        at(3); s.end();
        at(6); s.onRenderPost();
        at(20); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(2 * MS, f.bucket(Bucket.LOD));
        assertEquals(4 * MS, f.otherRenderNanos());
    }

    @Test
    void onlyLodLeavesTheBucketAroundIt() {
        Session s = session(10_000);
        at(0);  s.onRenderPre(0, 0);
        at(1);  s.begin(null, Bucket.HUD);
        at(2);  s.begin(null, Bucket.TERRAIN);       // a minimap mod drawing chunk layers in the HUD
        at(4);  s.end();
        at(5);  s.begin(null, Bucket.LOD);           // and Distant Horizons' LODs with them
        at(6);  s.end();
        at(8);  s.end();
        at(9);  s.onRenderPost();
        at(20); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(0, f.bucket(Bucket.TERRAIN), "a bucket inside the HUD still counts as hud");
        assertEquals(1 * MS, f.bucket(Bucket.LOD), "lod is the one exception");
        assertEquals(6 * MS, f.bucket(Bucket.HUD), "the HUD's 7 ms less the lod inside it");
    }

    @Test
    void aLodLeftOpenByAThrowIsResetWithTheFrame() {
        Session s = session(10_000);
        at(0);  s.onRenderPre(0, 0);
        at(1);  s.begin(null, Bucket.TERRAIN);
        at(2);  s.begin(null, Bucket.LOD);           // a hook threw past both ends
        at(10); s.onRenderPre(0, 0);
        assertEquals(1, s.unbalancedFrames());

        at(11); s.begin(null, Bucket.TERRAIN);
        at(12); s.begin(null, Bucket.LOD); at(14); s.end();
        at(15); s.end();
        at(16); s.begin(null, Bucket.LOD); at(17); s.end();
        at(20); s.onRenderPre(0, 0);

        assertEquals(0, s.framesView().get(0).bucket(Bucket.LOD), "nothing ended in the frame that threw");
        FrameRecord f = s.framesView().get(1);
        assertEquals(3 * MS, f.bucket(Bucket.LOD), "both lods count: the one left open was dropped with its frame");
        assertEquals(2 * MS, f.bucket(Bucket.TERRAIN), "4 ms less the 2 ms of lod inside it");
    }

    @Test
    void aLodInsideALodCountsOnce() {
        Session s = session(10_000);
        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(null, Bucket.LOD);
        at(3); s.begin(null, Bucket.LOD);            // one DH render method calling another, should a version do so
        at(4); s.end();
        at(5); s.end();
        at(6); s.onRenderPost();
        at(20); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(4 * MS, f.bucket(Bucket.LOD), "the outer call's 4 ms, the inner one not added again");
        assertEquals(2 * MS, f.otherRenderNanos());
    }

    @Test
    void namedTimeLeavesOutTheLodAndTheTerrainAroundIt() {
        Session s = session(10_000);
        Accumulator handler = s.accumulator(ModKey.event("somemod", "a.RenderLevelStageEvent"));
        at(0); s.onRenderPre(0, 0);
        at(1); s.beginAttribution(handler);
        at(2); s.begin(null, Bucket.TERRAIN);
        at(3); s.begin(null, Bucket.LOD); at(5); s.end();
        at(6); s.end();
        at(8); s.end();
        at(9); s.onRenderPost();
        at(20); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(2 * MS, f.bucket(Bucket.LOD));
        assertEquals(2 * MS, f.bucket(Bucket.TERRAIN));
        assertEquals(3 * MS, f.named(Window.RENDER), "the handler's 7 ms less the 4 bucketed inside it, lod once");
    }
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*SessionTest'`
Expected: five of the seven fail. `lod` reads 0 when anything encloses it, and the bucket around it keeps the whole
interval. `aLodThatNothingEnclosesCountsAsItself` and `aLodInsideALodCountsOnce` pass already, as they should: they pin
behaviour the change must keep.

- [ ] **Step 3: Add the fields**

In `Session.java`, find (it occurs once):

```java
    /** Bucket nanoseconds credited so far in this frame, so named time can leave out what was bucketed. */
    private long bucketTotal;
```

and replace it with:

```java
    /** Bucket nanoseconds credited so far in this frame, so named time can leave out what was bucketed. */
    private long bucketTotal;
    /** Open {@link Bucket#LOD} calls; only the outermost credits {@code lod} (spec addendum §P6.4). */
    private int lodDepth;
    /** {@code lod} nanoseconds credited so far in this frame, so the bucket around a {@code lod} call can leave it out. */
    private long lodTotal;
    /** {@link #lodTotal} when the outermost open bucket-owning call began. */
    private long lodMark;
```

- [ ] **Step 4: Mark the outermost call and count open `lod` calls in `begin`**

Find the javadoc of `begin` (it occurs once):

```java
    /**
     * Start timing a call attributed to {@code a} in {@code bucket}. Must be paired with {@link #end()}.
     * {@code a} may be null for work that has no key (light updates, Flywheel, chunk upload, the HUD): the
     * bucket is still credited when no other bucket-owning call encloses it, but nothing is added to an
     * accumulator or to the frame's top list.
     */
```

and replace it with:

```java
    /**
     * Start timing a call attributed to {@code a} in {@code bucket}. Must be paired with {@link #end()}.
     * {@code a} may be null for work that has no key (light updates, Flywheel, chunk upload, the HUD, terrain): the
     * bucket is still credited when no other bucket-owning call encloses it, but nothing is added to an
     * accumulator or to the frame's top list. {@link Bucket#LOD} is the exception: it is credited wherever it runs,
     * and the outermost bucket around it is credited its own time less every {@code lod} call inside it (spec
     * addendum §P6.4).
     */
```

Then find (it occurs once; `beginAttribution` does not touch `innermostOrdinary`):

```java
            stackPrevOrdinary[depth] = innermostOrdinary;
            innermostOrdinary = depth;
            ordinaryDepth++;
        }
        depth++;
    }
```

and replace it with:

```java
            stackPrevOrdinary[depth] = innermostOrdinary;
            innermostOrdinary = depth;
            if (ordinaryDepth == 0) lodMark = lodTotal;
            ordinaryDepth++;
            if (bucket == Bucket.LOD) lodDepth++;
        }
        depth++;
    }
```

- [ ] **Step 5: Credit `lod` as itself in `end`**

Find (it occurs once):

```java
        if (ownsBucket) {
            ordinaryDepth--;
            innermostOrdinary = stackPrevOrdinary[depth];
        }
```

and replace it with:

```java
        if (ownsBucket) {
            ordinaryDepth--;
            innermostOrdinary = stackPrevOrdinary[depth];
            if (bucket == Bucket.LOD) lodDepth--;
        }
```

Then find (it occurs once):

```java
            if (ownsBucket && ordinaryDepth == 0) {
                buckets[bucket.ordinal()] += elapsed;
                bucketTotal += elapsed;
            }
```

and replace it with:

```java
            if (bucket == Bucket.LOD) {
                // Spec addendum §P6.4: lod counts as itself wherever it runs, and once however it nests.
                if (lodDepth == 0) {
                    buckets[Bucket.LOD.ordinal()] += elapsed;
                    bucketTotal += elapsed;
                    lodTotal += elapsed;
                }
            } else if (ownsBucket && ordinaryDepth == 0) {
                // The outermost bucket-owning call, less every lod call inside it: no nanosecond is in two buckets.
                long own = Math.max(0, elapsed - (lodTotal - lodMark));
                buckets[bucket.ordinal()] += own;
                bucketTotal += own;
            }
```

- [ ] **Step 6: Reset with the frame**

In `onRenderPre`, find (it occurs once):

```java
            ordinaryDepth = 0;
            innermostOrdinary = -1;
            namedOpen = false;
        }
```

and replace it with:

```java
            ordinaryDepth = 0;
            innermostOrdinary = -1;
            namedOpen = false;
            lodDepth = 0;
        }
```

Then find (it occurs once):

```java
        Arrays.fill(buckets, 0L);
        bucketTotal = 0;
```

and replace it with:

```java
        Arrays.fill(buckets, 0L);
        bucketTotal = 0;
        lodTotal = 0;
```

`lodMark` needs no reset: an outermost call sets it when it begins, and a call cannot span a frame boundary without
the unbalanced reset dropping it.

- [ ] **Step 7: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **274** tests, 0 failures.

- [ ] **Step 8: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/Session.java src/test/java/io/github/edwardg117/renderable/core/SessionTest.java
```

Report to the orchestrator for the commit: `feat(core): lod carves itself out of the bucket around it`.

---

### Task 3: `unnamed` completes the tick's split

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/FrameRecord.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/FrameStats.java`
- Rewrite: `src/main/java/io/github/edwardg117/renderable/core/Splits.java`
- Rewrite: `src/test/java/io/github/edwardg117/renderable/core/SplitsTest.java` (7 tests updated or renamed, 2 new)
- Test: `src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java` (update 4 expectations)

**Interfaces:**
- Consumes: the four buckets (Task 1).
- Produces: `public long FrameRecord.unnamedTickNanos()`, `public long FrameStats.avgUnnamedTickNanos()`; the tick's
  brackets in `Splits.frame` and `Splits.average` end with `unnamed x` whenever it is above zero.

Spec §P6.6. `unnamed` is `max(0, tick − tick buckets − named tick time)`. With the tick buckets and `mods`, it
partitions the tick. It is printed after `mods`, in the session split and in every hitch's, and only above zero.
A tick that had any time with no bucket and no name therefore gains a bracket. That is why the existing split
expectations change: the frames they build have such time. The Frames tab keeps `other tick` and its `in the Mods
tab` row as they are (§P6.6). The render side keeps its form.

- [ ] **Step 1: Write the failing tests**

Replace the whole of `SplitsTest.java` with:

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SplitsTest {
    static final long MS = 1_000_000L;

    static FrameRecord frame(long totalMs10, long tickMs10, long renderMs10, long... bucketMs10) {
        long[] b = new long[Bucket.COUNT];
        for (int i = 0; i + 1 < bucketMs10.length; i += 2) b[(int) bucketMs10[i]] = bucketMs10[i + 1] * MS / 10;
        return new FrameRecord(0, 0, totalMs10 * MS / 10, tickMs10 * MS / 10, renderMs10 * MS / 10, b, 0, 0, List.of());
    }

    @Test
    void frameSplitNestsTickBucketsUnderTick() {
        FrameRecord f = frame(200, 20, 150,
                Bucket.BLOCK_ENTITY.ordinal(), 50, Bucket.LIGHT.ordinal(), 5,
                Bucket.BE_TICK.ordinal(), 8, Bucket.ENTITY_TICK.ordinal(), 4);
        assertEquals("tick 2.0 [be tick 0.8, entity tick 0.4, unnamed 0.8] | block entity 5.0 | light 0.5"
                + " | other render 9.5 | swap 3.0", Splits.frame(f));
    }

    @Test
    void aTickWithNoBucketIsAllUnnamedAndAnEmptyTickHasNoBrackets() {
        FrameRecord f = frame(160, 10, 100, Bucket.ENTITY.ordinal(), 20);
        assertEquals("tick 1.0 [unnamed 1.0] | entity 2.0 | other render 8.0 | swap 5.0", Splits.frame(f));
        FrameRecord idle = frame(160, 0, 100, Bucket.ENTITY.ordinal(), 20);
        assertEquals("tick 0.0 | entity 2.0 | other render 8.0 | swap 6.0", Splits.frame(idle));
    }

    @Test
    void averageSplitUsesTwoDecimalsAndSkipsEmptyRenderBuckets() {
        FrameStats st = FrameStats.of(List.of(
                frame(200, 20, 150, Bucket.BLOCK_ENTITY.ordinal(), 50, Bucket.PARTICLE_TICK.ordinal(), 10),
                frame(200, 20, 150, Bucket.BLOCK_ENTITY.ordinal(), 30, Bucket.PARTICLE_TICK.ordinal(), 10)));
        assertEquals("tick 2.00 [particle tick 1.00, unnamed 1.00] | block entity 4.00 | other render 11.00 | swap/wait 3.00",
                Splits.average(st));
    }

    @Test
    void callsPerFrameIsTheAverageLiveCount() {
        Accumulator a = new Accumulator(new ParticleKey("x"));
        a.calls = 60;
        a.tickCalls = 40;
        RankedEntry e = RankedEntry.of(a);
        assertEquals(3.0, e.callsPerFrame(20), 1e-9);
        assertEquals(0.0, e.callsPerFrame(0), 1e-9);
    }

    @Test
    void newComponentsAppearWhenTheyHaveTime() {
        long[] b = new long[Bucket.COUNT];
        b[Bucket.HUD.ordinal()] = 2 * MS;
        b[Bucket.ENTITY_TICK.ordinal()] = 1 * MS;
        long[] named = new long[Window.COUNT];
        named[Window.RENDER.ordinal()] = 1 * MS;
        named[Window.TICK.ordinal()] = 400_000;
        named[Window.TASKS.ordinal()] = 600_000;
        FrameRecord f = new FrameRecord(0, 0, 20 * MS, 2 * MS, 5 * MS, 2 * MS, 3 * MS, b, named, 0, 0, List.of());

        assertEquals("tick 2.0 [entity tick 1.0, mods 0.4, unnamed 0.6] | hud 2.0 | other render 3.0 [mods 1.0]"
                + " | tasks 2.0 [mods 0.6] | frame cap 3.0 | swap 8.0", Splits.frame(f));
        assertEquals("tick 2.00 [entity tick 1.00, mods 0.40, unnamed 0.60] | hud 2.00 | other render 3.00 [mods 1.00]"
                + " | tasks 2.00 [mods 0.60] | frame cap 3.00 | swap/wait 8.00", Splits.average(FrameStats.of(List.of(f))));
    }

    @Test
    void tasksAndTheFrameCapAppearIndependently() {
        FrameRecord tasksOnly = new FrameRecord(0, 0, 20 * MS, 2 * MS, 5 * MS, 2 * MS, 0, new long[Bucket.COUNT],
                new long[Window.COUNT], 0, 0, List.of());
        FrameRecord capOnly = new FrameRecord(0, 0, 20 * MS, 2 * MS, 5 * MS, 0, 3 * MS, new long[Bucket.COUNT],
                new long[Window.COUNT], 0, 0, List.of());

        assertEquals("tick 2.0 [unnamed 2.0] | other render 5.0 | tasks 2.0 | swap 11.0", Splits.frame(tasksOnly));
        assertEquals("tick 2.0 [unnamed 2.0] | other render 5.0 | frame cap 3.0 | swap 10.0", Splits.frame(capOnly));
        assertEquals("tick 2.00 [unnamed 2.00] | other render 5.00 | tasks 2.00 | swap/wait 11.00",
                Splits.average(FrameStats.of(List.of(tasksOnly))));
        assertEquals("tick 2.00 [unnamed 2.00] | other render 5.00 | frame cap 3.00 | swap/wait 10.00",
                Splits.average(FrameStats.of(List.of(capOnly))));
    }

    @Test
    void aTickBracketCanHoldNamedTimeWithoutBuckets() {
        long[] named = new long[Window.COUNT];
        named[Window.TICK.ordinal()] = 400_000;
        FrameRecord f = new FrameRecord(0, 0, 20 * MS, 2 * MS, 5 * MS, 0, 0, new long[Bucket.COUNT], named, 0, 0,
                List.of());

        assertEquals("tick 2.0 [mods 0.4, unnamed 1.6] | other render 5.0 | swap 13.0", Splits.frame(f));
        assertEquals("tick 2.00 [mods 0.40, unnamed 1.60] | other render 5.00 | swap/wait 13.00",
                Splits.average(FrameStats.of(List.of(f))));
    }

    @Test
    void theTickBracketPartitionsTheTick() {
        // Spec addendum §P6.6's example: hitch #3 at the other player's base, with sound and DH named.
        long[] b = new long[Bucket.COUNT];
        b[Bucket.PARTICLE_TICK.ordinal()] = 900_000;
        b[Bucket.BE_TICK.ordinal()] = 2_300_000;
        b[Bucket.ENTITY_TICK.ordinal()] = 400_000;
        b[Bucket.SOUND.ordinal()] = 15_100_000;
        b[Bucket.ANIMATE_TICK.ordinal()] = 600_000;
        b[Bucket.TERRAIN.ordinal()] = 1_200_000;
        b[Bucket.LOD.ordinal()] = 9_800_000;
        b[Bucket.BLOCK_ENTITY.ordinal()] = 1_700_000;
        long[] named = new long[Window.COUNT];
        named[Window.TICK.ordinal()] = 2 * MS;
        FrameRecord f = new FrameRecord(0, 0, 45 * MS, 25_600_000, 14 * MS, 0, 0, b, named, 0, 0, List.of());

        assertEquals("tick 25.6 [particle tick 0.9, be tick 2.3, entity tick 0.4, sound 15.1, animate tick 0.6, mods 2.0,"
                + " unnamed 4.3] | terrain 1.2 | lod 9.8 | block entity 1.7 | other render 1.3 | swap 5.4", Splits.frame(f));
        assertEquals("tick 25.60 [particle tick 0.90, be tick 2.30, entity tick 0.40, sound 15.10, animate tick 0.60,"
                + " mods 2.00, unnamed 4.30] | terrain 1.20 | lod 9.80 | block entity 1.70 | other render 1.30"
                + " | swap/wait 5.40", Splits.average(FrameStats.of(List.of(f))));
        assertEquals(f.tickNanos(), f.tickBucketsSum() + f.named(Window.TICK) + f.unnamedTickNanos(),
                "the bracket's parts add up to the tick");
    }

    @Test
    void unnamedIsNeverNegativeAndLeftOutAtZero() {
        long[] b = new long[Bucket.COUNT];
        b[Bucket.ENTITY_TICK.ordinal()] = 1_500_000;
        long[] exact = new long[Window.COUNT];
        exact[Window.TICK.ordinal()] = 500_000;
        FrameRecord whole = new FrameRecord(0, 0, 20 * MS, 2 * MS, 5 * MS, 0, 0, b, exact, 0, 0, List.of());
        assertEquals(0, whole.unnamedTickNanos());
        assertEquals("tick 2.0 [entity tick 1.5, mods 0.5] | other render 5.0 | swap 13.0", Splits.frame(whole),
                "a tick its parts account for in full has no unnamed");

        // Named and bucketed time cannot exceed the tick on a well-formed stack, but the figure must never go negative.
        long[] over = new long[Window.COUNT];
        over[Window.TICK.ordinal()] = 800_000;
        FrameRecord clamped = new FrameRecord(0, 0, 20 * MS, 2 * MS, 5 * MS, 0, 0, b, over, 0, 0, List.of());
        assertEquals(0, clamped.unnamedTickNanos());
        assertEquals(0, FrameStats.of(List.of(clamped)).avgUnnamedTickNanos());
        assertEquals("tick 2.0 [entity tick 1.5, mods 0.8] | other render 5.0 | swap 13.0", Splits.frame(clamped));
    }
}
```

In `TextReportWriterTest`, make four replacements. Each find string occurs once.

1. In `framesSection`, replace `tick 1.00 [entity tick 0.20] | block entity 7.40` with
   `tick 1.00 [entity tick 0.20, unnamed 0.80] | block entity 7.40`.
2. In `hitchSectionListsWorstFramesWithTopContributors`, replace `|  tick 1.0 [entity tick 0.2] | block entity 62.5`
   with `|  tick 1.0 [entity tick 0.2, unnamed 0.8] | block entity 62.5`.
3. In `phase2Sections`, replace `[particle tick 0.10, be tick 0.40, entity tick 0.20] | block entity 7.40` with
   `[particle tick 0.10, be tick 0.40, entity tick 0.20, unnamed 0.30] | block entity 7.40`.
4. In `phase5SplitNamesTheNewComponents`, replace `tick 2.00 [entity tick 1.00, mods 0.40] | block entity 1.00` with
   `tick 2.00 [entity tick 1.00, mods 0.40, unnamed 0.60] | block entity 1.00`.

The figures come from the fixtures. `sample()` ticks 1 ms with 0.2 of entity tick. `phase2()` ticks 1 ms with 0.7 in
three buckets. `phase5()` ticks 2 ms with 1 ms of entity tick and 0.4 named.

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*SplitsTest' --tests '*TextReportWriterTest'`
Expected: compilation failure, `cannot find symbol: method unnamedTickNanos()` (and `avgUnnamedTickNanos()`).

- [ ] **Step 3: Give the frame record and the averages their `unnamed`**

In `FrameRecord.java`, find (it occurs once):

```java
    /** Tick time not explained by any tick bucket. Clamped at zero. */
    public long otherTickNanos() {
        return Math.max(0, tickNanos - tickBucketsSum());
    }
```

and replace it with:

```java
    /** Tick time not explained by any tick bucket. Clamped at zero. */
    public long otherTickNanos() {
        return Math.max(0, tickNanos - tickBucketsSum());
    }

    /**
     * The part of the tick that neither a tick bucket nor the Mods tab accounts for: the split's {@code unnamed}
     * (spec addendum §P6.6). With the tick buckets and the tick's named time it partitions the tick. Clamped at zero.
     */
    public long unnamedTickNanos() {
        return Math.max(0, otherTickNanos() - named(Window.TICK));
    }
```

In `FrameStats.java`, find (it occurs once):

```java
    /** The average named time in window {@code w} (spec addendum §P5.A6). */
    public long avgNamed(Window w) {
        return avgNamedNanos[w.ordinal()];
    }
```

and replace it with:

```java
    /** The average named time in window {@code w} (spec addendum §P5.A6). */
    public long avgNamed(Window w) {
        return avgNamedNanos[w.ordinal()];
    }

    /** The average frame's {@code unnamed} tick time (spec addendum §P6.6): other tick less its named time, clamped. */
    public long avgUnnamedTickNanos() {
        return Math.max(0, avgOtherTickNanos - avgNamed(Window.TICK));
    }
```

- [ ] **Step 4: Rewrite `Splits`**

Replace the whole of `Splits.java` with:

```java
package io.github.edwardg117.renderable.core;

/**
 * One-line descriptions of how a frame, or the average frame, was spent. Tick buckets are children of
 * {@code tick} and are printed in brackets after it, so the parts outside the brackets sum to the frame.
 * {@code mods x} in brackets after tick, other render or tasks is the part of it the Mods tab names (spec
 * addendum §P5.A6). The tick's brackets end with {@code unnamed x}, the rest of the tick, so they partition it
 * (spec addendum §P6.6). Tasks and the frame cap are printed when they have time, as render buckets are.
 */
public final class Splits {

    private Splits() {
    }

    /** Average per-frame split, two decimals. */
    public static String average(FrameStats st) {
        StringBuilder sb = new StringBuilder();
        sb.append("tick ").append(Fmt.ms2(st.avgTickNanos()));
        appendTickChildren(sb, st.avgBucketNanos(), st.avgNamed(Window.TICK), st.avgUnnamedTickNanos(), true);
        for (Bucket b : Bucket.VALUES) {
            long v = st.avgBucket(b);
            if (b.isRender() && v > 0) sb.append(" | ").append(b.label()).append(' ').append(Fmt.ms2(v));
        }
        sb.append(" | other render ").append(Fmt.ms2(st.avgOtherRenderNanos()));
        appendNamed(sb, st.avgNamed(Window.RENDER), true);
        if (st.avgTasksNanos() > 0) {
            sb.append(" | tasks ").append(Fmt.ms2(st.avgTasksNanos()));
            appendNamed(sb, st.avgNamed(Window.TASKS), true);
        }
        if (st.avgCapNanos() > 0) sb.append(" | frame cap ").append(Fmt.ms2(st.avgCapNanos()));
        sb.append(" | swap/wait ").append(Fmt.ms2(st.avgSwapNanos()));
        return sb.toString();
    }

    /** One frame's split, one decimal. */
    public static String frame(FrameRecord f) {
        StringBuilder sb = new StringBuilder();
        sb.append("tick ").append(Fmt.ms(f.tickNanos()));
        appendTickChildren(sb, f.buckets(), f.named(Window.TICK), f.unnamedTickNanos(), false);
        for (Bucket b : Bucket.VALUES) {
            long v = f.bucket(b);
            if (b.isRender() && v > 0) sb.append(" | ").append(b.label()).append(' ').append(Fmt.ms(v));
        }
        sb.append(" | other render ").append(Fmt.ms(f.otherRenderNanos()));
        appendNamed(sb, f.named(Window.RENDER), false);
        if (f.tasksNanos() > 0) {
            sb.append(" | tasks ").append(Fmt.ms(f.tasksNanos()));
            appendNamed(sb, f.named(Window.TASKS), false);
        }
        if (f.capNanos() > 0) sb.append(" | frame cap ").append(Fmt.ms(f.capNanos()));
        sb.append(" | swap ").append(Fmt.ms(f.swapNanos()));
        return sb.toString();
    }

    /** The tick's brackets: its buckets in enum order, then {@code mods}, then {@code unnamed}, each when above zero. */
    private static void appendTickChildren(StringBuilder sb, long[] buckets, long named, long unnamed, boolean twoDecimals) {
        boolean any = false;
        for (Bucket b : Bucket.VALUES) {
            long v = buckets[b.ordinal()];
            if (b.isRender() || v <= 0) continue;
            sb.append(any ? ", " : " [").append(b.label()).append(' ').append(twoDecimals ? Fmt.ms2(v) : Fmt.ms(v));
            any = true;
        }
        if (named > 0) {
            sb.append(any ? ", " : " [").append("mods ").append(twoDecimals ? Fmt.ms2(named) : Fmt.ms(named));
            any = true;
        }
        if (unnamed > 0) {
            sb.append(any ? ", " : " [").append("unnamed ").append(twoDecimals ? Fmt.ms2(unnamed) : Fmt.ms(unnamed));
            any = true;
        }
        if (any) sb.append(']');
    }

    private static void appendNamed(StringBuilder sb, long named, boolean twoDecimals) {
        if (named > 0) sb.append(" [mods ").append(twoDecimals ? Fmt.ms2(named) : Fmt.ms(named)).append(']');
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **276** tests, 0 failures.

- [ ] **Step 6: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/FrameRecord.java src/main/java/io/github/edwardg117/renderable/core/FrameStats.java src/main/java/io/github/edwardg117/renderable/core/Splits.java src/test/java/io/github/edwardg117/renderable/core/SplitsTest.java src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java
```

Report to the orchestrator for the commit: `feat(core): unnamed completes the tick's split`.

---

### Task 4: Sound rows in the Mods tab and the Sound plays line

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/ModKey.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/SoundPlays.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java`
- Modify: `src/test/java/io/github/edwardg117/renderable/core/TestSessions.java` (`phase6` and its keys)
- Test: `src/test/java/io/github/edwardg117/renderable/core/KeysTest.java` (append 1)
- Create: `src/test/java/io/github/edwardg117/renderable/core/SoundPlaysTest.java` (2)
- Test: `src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java` (append 1)
- Test: `src/test/java/io/github/edwardg117/renderable/ui/screen/RowsTest.java` (append 1)
- Test: `src/test/java/io/github/edwardg117/renderable/report/JsonReportWriterTest.java` (append 1)

**Interfaces:**
- Consumes: the `lod` carve-out (Task 2) and `unnamed` (Task 3), which the fixture's figures rely on;
  `Session.beginAttribution(Accumulator)`.
- Produces:
  - `ModKey.Kind.SOUND` (word `sound`);
  - `public static ModKey ModKey.sound(String eventId, String category)`;
  - `public String ModKey.soundEvent()` and `public String ModKey.soundCategory()`;
  - `public record SoundPlays(List<RankedEntry> rows, long nanos, long plays, int frames, double seconds)` with
    `static SoundPlays of(SessionResult r)`, `boolean any()`, `double msPerFrame()`, `double playsPerSecond()`,
    `double playsPerSecond(RankedEntry row)`, `String summary()` and `String line()`;
  - `TestSessions.phase6()`, with `FLUID_TANK`, `STEAM`, `ANVIL`, `CLICK` and `SOUND_PACKET`.

Spec §P6.5 and §P6.7. A play is an attribution-only call keyed by its event and category. `name` is
`<event id> (<category>)`, so a row reads `sound create:steam (block)` under `create`, and one event in two categories
is two rows. The existing `label()`/`memberLabel()` already print `<kind word> <name>` for every kind but `EVENT`, the
Mods tab and report section already show every `MOD` key, and the JSON's `mods` array already writes each as a ranked
row. So sound rows need no table code. The Sound plays line sums the sound rows. It sits under the overhead lines in
the report's Frames section and on the Frames tab, and only when there were plays. The JSON is unchanged in shape:
a sound row's `key` reads `sound@create:create:steam (block)`.

- [ ] **Step 1: Add the phase 6 fixture**

In `TestSessions.java`, find (it occurs once):

```java
    public static final EntityKey BOAT = new EntityKey("minecraft:boat", 9, "");
```

and replace it with:

```java
    public static final EntityKey BOAT = new EntityKey("minecraft:boat", 9, "");
    public static final BlockEntityKey FLUID_TANK =
            new BlockEntityKey("create:fluid_tank", "minecraft:overworld", 10, 64, 10);
    public static final ModKey STEAM = ModKey.sound("create:steam", "block");
    public static final ModKey ANVIL = ModKey.sound("minecraft:block.anvil.land", "block");
    public static final ModKey CLICK = ModKey.sound("minecraft:ui.button.click", "master");
    public static final ModKey SOUND_PACKET = ModKey.packet("minecraft", "minecraft:sound");
```

Then add this method directly after `phase6Buckets()` (Task 1), whose end is unique:

```java
        now[0] = 16 * MS;    s.onRenderPre(0, 0);
        return s.finish();
    }
```

```java

    /**
     * Phase 6 fixture (spec addendum §P6.3 to §P6.6): two identical 20 ms frames. Per frame, in milliseconds from its
     * start:
     *
     * render 0-8: terrain 0.5-4.5 holding lod 1-3. Tasks 8-10: the sound packet 8.5-9.5 holding an anvil play
     * 8.7-9.1. Tick 10-14: the fluid tank's tick 10-11 (BE_TICK) holding a steam play 10.2-10.8; the sound engine's
     * tick 11-12.5 (SOUND) holding a delayed steam play 11.2-11.6; animate tick 12.5-13; then a button click
     * 13.2-13.4 that nothing encloses.
     *
     * So per frame: render 8 (terrain 2, lod 2, other render 4), tasks 2 (1 named), tick 4 (be tick 1, sound 1.5,
     * animate tick 0.5, 0.2 named, 0.8 unnamed), swap 6. Sound rows over the session: steam 4 plays, 2 ms, on the
     * tick side; anvil 2 plays, 0.8 ms, on the render side (tasks); click 2 plays, 0.4 ms, tick side. Together 3.2 ms,
     * 1.60 ms a frame, and 8 plays in 0.04 s, 200 a second. No hitches.
     */
    public static SessionResult phase6() {
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 25, 2), () -> now[0], WALL_START);
        Accumulator tank = s.accumulator(FLUID_TANK);
        Accumulator steam = s.accumulator(STEAM);
        Accumulator anvil = s.accumulator(ANVIL);
        Accumulator click = s.accumulator(CLICK);
        Accumulator packet = s.accumulator(SOUND_PACKET);

        for (int i = 0; i <= 2; i++) {
            long t0 = i * 20 * MS;
            now[0] = t0; s.onRenderPre(0, 0);
            if (i == 2) break;

            now[0] = t0 + 500_000;    s.begin(null, Bucket.TERRAIN);
            now[0] = t0 + 1 * MS;     s.begin(null, Bucket.LOD);
            now[0] = t0 + 3 * MS;     s.end();
            now[0] = t0 + 4_500_000;  s.end();
            now[0] = t0 + 8 * MS;     s.onRenderPost();

            now[0] = t0 + 8 * MS;     s.onTasksBegin();
            now[0] = t0 + 8_500_000;  s.beginAttribution(packet);
            now[0] = t0 + 8_700_000;  s.beginAttribution(anvil);
            now[0] = t0 + 9_100_000;  s.end();
            now[0] = t0 + 9_500_000;  s.end();
            now[0] = t0 + 10 * MS;    s.onTasksEnd();

            now[0] = t0 + 10 * MS;    s.onTickPre();
            now[0] = t0 + 10 * MS;    s.begin(tank, Bucket.BE_TICK);
            now[0] = t0 + 10_200_000; s.beginAttribution(steam);       // a boiler's steam, from its block entity tick
            now[0] = t0 + 10_800_000; s.end();
            now[0] = t0 + 11 * MS;    s.end();
            now[0] = t0 + 11 * MS;    s.begin(null, Bucket.SOUND);
            now[0] = t0 + 11_200_000; s.beginAttribution(steam);       // a play playDelayed queued
            now[0] = t0 + 11_600_000; s.end();
            now[0] = t0 + 12_500_000; s.end();
            now[0] = t0 + 12_500_000; s.begin(null, Bucket.ANIMATE_TICK);
            now[0] = t0 + 13 * MS;    s.end();
            now[0] = t0 + 13_200_000; s.beginAttribution(click);       // nothing encloses it: named tick time
            now[0] = t0 + 13_400_000; s.end();
            now[0] = t0 + 14 * MS;    s.onTickPost();
        }
        return s.finish();
    }
```

- [ ] **Step 2: Write the failing tests**

Append to `KeysTest`:

```java
    @Test
    void soundKeysNameTheirEventAndCategory() {
        ModKey steam = ModKey.sound("create:steam", "block");
        assertEquals(ProfileKey.Category.MOD, steam.category());
        assertEquals(ModKey.Kind.SOUND, steam.kind());
        assertEquals("create", steam.typeId(), "the event id's namespace names the mod");
        assertEquals("create:steam (block)", steam.name());
        assertEquals("sound create:steam (block)", steam.label());
        assertEquals(steam.label(), steam.memberLabel(), "the event id already names its mod");
        assertEquals("sound@create:create:steam (block)", steam.serialize());
        assertEquals("create:steam", steam.soundEvent());
        assertEquals("block", steam.soundCategory());

        assertEquals(steam, ModKey.sound("create:steam", "block"));
        assertNotEquals(steam, ModKey.sound("create:steam", "master"), "one event in two categories is two rows");
        assertEquals("minecraft", ModKey.sound("minecraft:block.anvil.land", "block").modId());
        assertEquals("minecraft", ModKey.sound("steam", "block").modId(),
                "an id with no namespace is minecraft's, as ResourceLocation reads it");
        ModKey packet = ModKey.packet("minecraft", "minecraft:sound");
        assertEquals("", packet.soundEvent(), "only a sound key has an event");
        assertEquals("", packet.soundCategory(), "or a category");
    }
```

Create `src/test/java/io/github/edwardg117/renderable/core/SoundPlaysTest.java`:

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SoundPlaysTest {
    static final long MS = 1_000_000L;

    @Test
    void theLineSumsTheSoundRowsAndNothingElse() {
        SoundPlays sp = SoundPlays.of(TestSessions.phase6());
        assertEquals(List.of(TestSessions.STEAM, TestSessions.ANVIL, TestSessions.CLICK),
                sp.rows().stream().map(RankedEntry::key).toList(), "every sound row, the costliest first");
        assertEquals(3_200_000, sp.nanos(), "steam 2.0 + anvil 0.8 + click 0.4: the packet and the tank are not sound rows");
        assertEquals(8, sp.plays());
        assertEquals(1.6, sp.msPerFrame(), 1e-9);
        assertEquals(200.0, sp.playsPerSecond(), 1e-9);
        assertEquals(100.0, sp.playsPerSecond(sp.rows().get(0)), 1e-9, "steam played 4 times in 0.04 s");
        assertEquals("1.60 ms a frame, 200 plays a second", sp.summary());
        assertEquals("Sound plays: 1.60 ms a frame, 200 plays a second", sp.line());

        SoundPlays none = SoundPlays.of(TestSessions.sample());
        assertFalse(none.any(), "a session with no plays prints no line");
        assertEquals(List.of(), none.rows());
    }

    @Test
    void aPlayThatCostNothingStillCountsAndTheLineStillPrints() {
        // Review focus 2: at 0% the game skips a sound before asking for a channel, so a muted category's plays
        // cost nothing measurable. They are still plays.
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> now[0], 0);
        Accumulator muted = s.accumulator(ModKey.sound("create:steam", "block"));
        s.onRenderPre(0, 0);
        for (int i = 0; i < 3; i++) {
            s.beginAttribution(muted);
            s.end();                    // the clock did not move
        }
        now[0] = 2_000 * MS;
        s.onRenderPre(0, 0);

        SoundPlays sp = SoundPlays.of(s.finish());
        assertTrue(sp.any());
        assertEquals(0, sp.nanos());
        assertEquals(3, sp.plays());
        assertEquals("Sound plays: 0.00 ms a frame, 1.5 plays a second", sp.line(),
                "printed whenever anything played; under ten a second the rate keeps a decimal");
    }
}
```

Append to `TextReportWriterTest`:

```java
    @Test
    void theSoundPlaysLineFollowsTheOverheadLinesWhenThereWerePlays() {
        String s = TextReportWriter.write(TestSessions.phase6(), env, "0.6.0");
        assertTrue(s.contains(" vertex calls\n  Sound plays: 1.60 ms a frame, 200 plays a second\n\nHitches (worst 25)\n"), s);
        assertTrue(s.contains("  Time split (avg ms/frame): tick 4.00 [be tick 1.00, sound 1.50, animate tick 0.50,"
                + " mods 0.20, unnamed 0.80] | terrain 2.00 | lod 2.00 | other render 4.00 | tasks 2.00 [mods 1.00]"
                + " | swap/wait 6.00\n"), s);
        assertTrue(s.contains("\n    sound create:steam (block) "), "a sound row under its mod in the Mods section: " + s);
        assertFalse(TextReportWriter.write(r, env, "0.6.0").contains("Sound plays"), "no plays, no line");
    }
```

Append to `RowsTest`:

```java
    @Test
    void theFramesTabShowsSoundPlaysUnderTheOverheadRows() {
        List<String> rows = describe(Rows.frames(TestSessions.phase6()));
        int overhead = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).startsWith("0 - Renderable overhead|")) overhead = i;
        }
        assertTrue(overhead >= 0, rows.toString());
        assertEquals("0 - Sound plays|1.60 ms a frame, 200 plays a second", rows.get(overhead + 1));
        assertTrue(describe(Rows.frames(TestSessions.sample())).stream().noneMatch(row -> row.contains("Sound plays")),
                "no plays, no row");
    }
```

Append to `JsonReportWriterTest`:

```java
    @Test
    void aSoundRowIsAModsEntryKeyedByItsEventAndCategory() {
        JsonObject o = JsonParser.parseString(JsonReportWriter.write(TestSessions.phase6(), environment(), "0.6.0"))
                .getAsJsonObject();
        JsonObject steam = null;
        for (var m : o.getAsJsonArray("mods")) {
            JsonObject mod = m.getAsJsonObject();
            for (var e : mod.getAsJsonArray("entries")) {
                if (e.getAsJsonObject().get("key").getAsString().equals("sound@create:create:steam (block)")) {
                    assertEquals("create", mod.get("key").getAsString(), "under its namespace's mod");
                    steam = e.getAsJsonObject();
                }
            }
        }
        assertNotNull(steam, o.getAsJsonArray("mods").toString());
        assertEquals(RANKED_KEYS, List.copyOf(steam.keySet()), "a ranked row like any other: nothing changes shape");
        assertEquals("sound create:steam (block)", steam.get("label").getAsString());
        assertEquals("create", steam.get("typeId").getAsString());
        assertEquals(0, steam.get("calls").getAsLong());
        assertEquals(4, steam.get("tickCalls").getAsLong(), "two plays a frame, both in the tick");
        assertEquals(2_000, steam.get("tickUs").getAsLong());

        JsonObject f0 = o.getAsJsonArray("frames").get(0).getAsJsonObject().getAsJsonObject("bucketsUs");
        assertEquals(2_000, f0.get("terrain").getAsLong(), "4 ms less the 2 ms of lod inside it");
        assertEquals(2_000, f0.get("lod").getAsLong());
        assertEquals(1_500, f0.get("sound").getAsLong());
        assertEquals(500, f0.get("animate tick").getAsLong());
    }
```

- [ ] **Step 3: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*KeysTest' --tests '*SoundPlaysTest' --tests '*TextReportWriterTest' --tests '*RowsTest' --tests '*JsonReportWriterTest'`
Expected: compilation failure, `cannot find symbol: method sound(String,String)` and `cannot find symbol: class
SoundPlays`.

- [ ] **Step 4: Add the `SOUND` kind**

In `ModKey.java`, replace the class javadoc (it occurs once):

```java
/**
 * Something a mod does in the frame that Renderable can name by its owner (spec addendum §P5.A3 to §P5.A5):
 * the mod's handlers for one event class, one HUD layer, or one packet or payload type. All three roll up by
 * mod id into the Mods tab, so they share {@link Category#MOD} and use the mod id as the type id.
 *
 * @param kind  what was timed
 * @param modId the owning mod: the mod whose file registered the handler, or a layer's or packet's namespace
 * @param name  the event's fully-qualified class name, or the layer's or packet's full id
 */
```

with:

```java
/**
 * Something a mod does in the frame that Renderable can name by its owner (spec addendum §P5.A3 to §P5.A5, §P6.5):
 * the mod's handlers for one event class, one HUD layer, one packet or payload type, or one sound event played in
 * one category. All of them roll up by mod id into the Mods tab, so they share {@link Category#MOD} and use the mod
 * id as the type id.
 *
 * @param kind  what was timed
 * @param modId the owning mod: the mod whose file registered the handler, or a layer's, packet's or sound event's
 *              namespace
 * @param name  the event's fully-qualified class name, the layer's or packet's full id, or for a sound
 *              {@code <event id> (<category>)}
 */
```

Replace (it occurs once):

```java
        EVENT("event"), LAYER("layer"), PACKET("packet");
```

with:

```java
        EVENT("event"), LAYER("layer"), PACKET("packet"), SOUND("sound");
```

Then find (it occurs once):

```java
    public static ModKey packet(String modId, String packetId) {
        return new ModKey(Kind.PACKET, modId, packetId);
    }
```

and replace it with:

```java
    public static ModKey packet(String modId, String packetId) {
        return new ModKey(Kind.PACKET, modId, packetId);
    }

    /**
     * One sound event played in one category (spec addendum §P6.5). The event id's namespace names the mod, as a
     * layer's or packet's does, and the name carries the category, so one event played in two categories is two
     * rows: {@code sound create:steam (block)}.
     *
     * @param eventId  the event's id as {@code namespace:path}, from {@code SoundInstance.getLocation()}; an id with
     *                 no namespace is {@code minecraft}'s, as {@code ResourceLocation} reads it
     * @param category the category's name, from {@code SoundSource.getName()}: {@code block}, {@code master}, ...
     */
    public static ModKey sound(String eventId, String category) {
        int colon = eventId.indexOf(':');
        String modId = colon < 0 ? "minecraft" : eventId.substring(0, colon);
        return new ModKey(Kind.SOUND, modId, eventId + " (" + category + ")");
    }

    /** A sound key's event id, {@code create:steam}; "" for any other kind. */
    public String soundEvent() {
        int open = name.lastIndexOf(" (");
        return kind == Kind.SOUND && open >= 0 ? name.substring(0, open) : "";
    }

    /** A sound key's category, a {@code SoundSource} name such as {@code block}; "" for any other kind. */
    public String soundCategory() {
        int open = name.lastIndexOf(" (");
        return kind == Kind.SOUND && open >= 0 && name.endsWith(")") ? name.substring(open + 2, name.length() - 1) : "";
    }
```

An event id is a `ResourceLocation`, which holds no space or parenthesis, so the last ` (` always starts the category.

- [ ] **Step 5: Create `SoundPlays`**

```java
package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The session's sound plays (spec addendum §P6.5): the Mods tab's sound rows, summed for the "Sound plays" line in
 * the report's Frames section and on the Frames tab. Rows are inclusive like every row, so a play made inside another
 * play, by a {@code PlaySoundEvent} handler, counts in both.
 *
 * @param rows    every sound row, the costliest first
 * @param nanos   the rows' time, render and tick sides
 * @param plays   the rows' calls, render and tick sides: every play, a skipped one included
 * @param frames  frames in the session
 * @param seconds the session's length
 */
public record SoundPlays(List<RankedEntry> rows, long nanos, long plays, int frames, double seconds) {

    public static SoundPlays of(SessionResult r) {
        List<RankedEntry> rows = new ArrayList<>();
        long nanos = 0;
        long plays = 0;
        for (RankedEntry e : Ranking.byKey(r, ProfileKey.Category.MOD)) {
            if (!(e.key() instanceof ModKey k) || k.kind() != ModKey.Kind.SOUND) continue;
            rows.add(e);
            nanos += e.totalNanos();
            plays += e.calls() + e.tickCalls();
        }
        return new SoundPlays(List.copyOf(rows), nanos, plays, r.frameCount(), r.stats().durationSeconds());
    }

    /** True when anything played. The line is printed only then. */
    public boolean any() {
        return plays > 0;
    }

    /** The rows' time, milliseconds a frame. */
    public double msPerFrame() {
        return frames == 0 ? 0 : nanos / 1e6 / frames;
    }

    /** Plays a second over the session. */
    public double playsPerSecond() {
        return seconds <= 0 ? 0 : plays / seconds;
    }

    /** One row's plays a second over the session. */
    public double playsPerSecond(RankedEntry row) {
        return seconds <= 0 ? 0 : (row.calls() + row.tickCalls()) / seconds;
    }

    /**
     * {@code 1.21 ms a frame, 180 plays a second}, the Frames tab's value. The rate is whole at ten a second or more
     * and keeps one decimal below, so a quiet session never reads "0 plays a second" on a line printed only when
     * something played.
     */
    public String summary() {
        double rate = playsPerSecond();
        String perSecond = rate >= 10 ? String.format(Locale.ROOT, "%.0f", rate) : Fmt.num(rate);
        return Fmt.ms2(frames == 0 ? 0 : nanos / frames) + " ms a frame, " + perSecond + " plays a second";
    }

    /** {@code Sound plays: 1.21 ms a frame, 180 plays a second}, the report's line. */
    public String line() {
        return "Sound plays: " + summary();
    }
}
```

- [ ] **Step 6: Print the line in the report's Frames section**

In `TextReportWriter.java`, replace (it occurs once):

```java
import io.github.edwardg117.renderable.core.Splits;
```

with:

```java
import io.github.edwardg117.renderable.core.SoundPlays;
import io.github.edwardg117.renderable.core.Splits;
```

Then find (it occurs once):

```java
        if (r.unbalancedFrames() > 0) {
            sb.append(String.format(Locale.ROOT,
                    "  Attribution stack reset on %d frames (a hook threw past its end call; buckets on those frames are incomplete)\n",
                    r.unbalancedFrames()));
        }
        sb.append('\n');
```

and replace it with:

```java
        if (r.unbalancedFrames() > 0) {
            sb.append(String.format(Locale.ROOT,
                    "  Attribution stack reset on %d frames (a hook threw past its end call; buckets on those frames are incomplete)\n",
                    r.unbalancedFrames()));
        }
        SoundPlays sounds = SoundPlays.of(r);
        if (sounds.any()) sb.append("  ").append(sounds.line()).append('\n');
        sb.append('\n');
```

- [ ] **Step 7: Show it on the Frames tab**

In `Rows.java`, replace (it occurs once):

```java
import io.github.edwardg117.renderable.core.Splits;
```

with:

```java
import io.github.edwardg117.renderable.core.SoundPlays;
import io.github.edwardg117.renderable.core.Splits;
```

Then find (it occurs once):

```java
        if (r.unbalancedFrames() > 0) {
            rows.add(Row.plain("Attribution stack reset", r.unbalancedFrames() + " frames (a hook threw past its end call)"));
        }
        rows.add(Row.plain("", ""));
```

and replace it with:

```java
        if (r.unbalancedFrames() > 0) {
            rows.add(Row.plain("Attribution stack reset", r.unbalancedFrames() + " frames (a hook threw past its end call)"));
        }
        SoundPlays sounds = SoundPlays.of(r);
        if (sounds.any()) rows.add(Row.plain("Sound plays", sounds.summary()));
        rows.add(Row.plain("", ""));
```

- [ ] **Step 8: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **282** tests, 0 failures. `Facts.of` switches on the kind with a statement, so `SOUND`
compiles there and sound rows feed none of the `mod-*` rules; plan 6b's `sound-wait` reads them.

- [ ] **Step 9: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/ModKey.java src/main/java/io/github/edwardg117/renderable/core/SoundPlays.java src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java src/test/java/io/github/edwardg117/renderable/core/TestSessions.java src/test/java/io/github/edwardg117/renderable/core/KeysTest.java src/test/java/io/github/edwardg117/renderable/core/SoundPlaysTest.java src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java src/test/java/io/github/edwardg117/renderable/ui/screen/RowsTest.java src/test/java/io/github/edwardg117/renderable/report/JsonReportWriterTest.java
```

Report to the orchestrator for the commit: `feat(core): sound rows in the Mods tab and the sound plays line`.

---

### Task 5: The vanilla hooks: sound, animate tick, terrain and sound plays

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Hook.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/SoundKeys.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/Probe.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/HookInstalls.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/MinecraftMixin.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/LevelRendererMixin.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/SoundEngineMixin.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/ClientHooksMixin.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Session.java` (`innermostBucket`)
- Modify: `src/main/resources/renderable.mixins.json`
- Create: `src/test/java/io/github/edwardg117/renderable/hooks/SoundKeysTest.java` (2)
- Test: `src/test/java/io/github/edwardg117/renderable/core/SourceGuardsTest.java` (append 1)
- Test: `src/test/java/io/github/edwardg117/renderable/hooks/HookInstallsTest.java` (append 1)
- Test: `src/test/java/io/github/edwardg117/renderable/core/SessionTest.java` (append 1)

**Interfaces:**
- Consumes: `Bucket.SOUND`, `Bucket.ANIMATE_TICK`, `Bucket.TERRAIN` (Task 1); `ModKey.sound(String, String)` (Task 4);
  `Probe.beginBucket(Bucket, Hook)`, `Probe.endBucket()`, `Probe.end()`; `Session.beginAttribution(Accumulator)`,
  `Session.accumulator(ProfileKey)`, `Session.owns(Accumulator)`, `Session.begin(Accumulator, Bucket)`,
  `Session.end()`.
- Produces:
  - `Hook.SOUND`, `Hook.ANIMATE_TICK`, `Hook.TERRAIN` and `Hook.SOUND_PLAYS`;
  - `public static boolean Probe.beginSound(SoundInstance sound)`;
  - `public static boolean Probe.pauseBucket(Bucket bucket)` and `public static void Probe.resumeBucket(Bucket bucket)`;
  - `public Bucket Session.innermostBucket()`;
  - `final class SoundKeys` (package-private) with `SoundKeys(String[] categories)`,
    `Accumulator get(Session s, Object id, int category)` and `void clear()`;
  - the handlers `MinecraftMixin.renderable$timeSound`, `MinecraftMixin.renderable$timeAnimateTick`,
    `LevelRendererMixin.renderable$timeTerrain`, `SoundEngineMixin.renderable$timeSoundPlay` and
    `ClientHooksMixin.renderable$leaveTerrain`.

Spec §P6.2, §P6.3 and §P6.5. These target Minecraft's own classes at the pinned version, so they are required, in
`renderable.mixins.json`, like the other vanilla mixins. The three buckets are bucket-only timings through
`beginBucket`, which declines off the render thread. A sound play is attribution-only. `beginSound` reads the
instance's id and category before the play runs, and looks the key up without allocating through `SoundKeys`: a
`HashMap` by id (a `ResourceLocation`'s hash is two cached `String` hashes), then an array by category ordinal.
`SoundKeys` names no Minecraft type, so it is tested here. The `Hook` values go in this task because
`HookInstallsTest` fails for a hook no injector maps to.

Deviation 4: both renderers end each layer by calling NeoForge's `ClientHooks.dispatchRenderStage(RenderType, ...)`,
which posts that layer's `RenderLevelStageEvent`. Inside `terrain`, its handlers would stop being named time, and
whatever they draw would count as terrain. So a required `@WrapMethod` on that method steps out. It calls
`Probe.pauseBucket(Bucket.TERRAIN)`, which ends the innermost open call only when it is a `TERRAIN` bucket-only timing
(render thread only). After the event, `Probe.resumeBucket` begins it again, and the terrain wrapper's own `endBucket`
ends that. The method has two overloads, so the selector carries the descriptor. Its injector maps to `Hook.TERRAIN`,
so `TERRAIN` reads installed only when both of its injectors found their targets.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/io/github/edwardg117/renderable/hooks/SoundKeysTest.java`:

```java
package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.core.SessionConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Sound plays' keys (spec addendum §P6.5), without the game: an {@link Id} stands in for a ResourceLocation. */
class SoundKeysTest {
    static final long MS = 1_000_000L;
    /** The first five SoundSource names, in ordinal order. */
    static final String[] CATEGORIES = {"master", "music", "record", "weather", "block"};

    /** Value equality, and toString() is the id, as a ResourceLocation's is. */
    record Id(String text) {
        @Override
        public String toString() {
            return text;
        }
    }

    static Session session() {
        return new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> 0, 0);
    }

    @Test
    void aPlayIsKeyedByItsEventThenItsCategory() {
        Session s = session();
        SoundKeys keys = new SoundKeys(CATEGORIES);
        Accumulator steam = keys.get(s, new Id("create:steam"), 4);
        assertEquals(ModKey.sound("create:steam", "block"), steam.key);
        assertSame(steam, keys.get(s, new Id("create:steam"), 4),
                "an equal id that is another object finds the same row: a mod may build an id for every play");
        assertSame(steam, s.accumulator(ModKey.sound("create:steam", "block")), "the session's own accumulator");
        Accumulator master = keys.get(s, new Id("create:steam"), 0);
        assertNotSame(steam, master, "one event in two categories is two rows");
        assertEquals("sound create:steam (master)", master.key.label());

        Session next = session();
        assertTrue(next.owns(keys.get(next, new Id("create:steam"), 4)), "an earlier session's accumulator is replaced");
        keys.clear();
        assertSame(steam, keys.get(s, new Id("create:steam"), 4), "a cleared cache finds the session's accumulator again");
    }

    @Test
    void anInstanceThatNamesNoEventOrNoCategoryIsNotTimed() {
        // Review focus 5: a mod's own SoundInstance may return null from getLocation() or getSource(). The play is
        // not timed, and nothing here throws: vanilla's play may still cope, or a PlaySoundEvent handler replace it.
        Session s = session();
        SoundKeys keys = new SoundKeys(CATEGORIES);
        assertNull(keys.get(s, null, 4), "no event id");
        assertNull(keys.get(s, new Id("create:steam"), -1), "no category: Probe passes -1 for a null SoundSource");
        assertNull(keys.get(s, new Id("create:steam"), CATEGORIES.length), "an ordinal past the known categories");
        assertNotNull(keys.get(s, new Id("create:steam"), 4), "and the cache still works after them");
    }
}
```

Append to `SourceGuardsTest`:

```java
    @Test
    void everyAttributionOnlyEntryPointDeclinesOffTheRenderThread() throws IOException {
        // Review focus 3. A Session is render-thread only: an attribution-only call begun on another thread, such as a
        // sound a mod plays from its network thread, would corrupt the stack. So each entry point in Probe that
        // begins one must check the thread first, as beginPacket does (spec addendum §P6.5).
        String probe = Files.readString(MAIN.resolve("hooks/Probe.java"));
        int found = 0;
        for (int at = probe.indexOf(".beginAttribution("); at >= 0; at = probe.indexOf(".beginAttribution(", at + 1)) {
            int start = probe.lastIndexOf("\n    public static ", at);
            assertTrue(start >= 0, "a beginAttribution call outside a public entry point of Probe");
            String method = probe.substring(start + "\n    public static ".length(), probe.indexOf('(', start));
            assertTrue(probe.substring(start, at).contains("Thread.currentThread() != renderThread"),
                    method + " begins an attribution-only call without checking the render thread");
            found++;
        }
        assertTrue(found >= 4, "beginLayer, beginPacket, beginPayload and beginSound; found " + found);
    }
```

Append to `HookInstallsTest`:

```java
    @Test
    void thePhase6VanillaInjectorsNameTheirVerifiedTargets() throws IOException {
        // Spec addendum §P6.2, verified with javap against neoforge-21.1.220-merged.jar. These mixins are required, so a
        // misspelt target would stop the game at launch: pin each spelling here.
        String minecraft = "io.github.edwardg117.renderable.hooks.mixin.MinecraftMixin";
        assertEquals(new InjectionCheck.Injector("renderable$timeSound", List.of("tick"),
                        List.of("Lnet/minecraft/client/sounds/SoundManager;tick(Z)V")),
                injector(minecraft, "renderable$timeSound"));
        assertEquals(new InjectionCheck.Injector("renderable$timeAnimateTick", List.of("tick"),
                        List.of("Lnet/minecraft/client/multiplayer/ClientLevel;animateTick(III)V")),
                injector(minecraft, "renderable$timeAnimateTick"));
        assertEquals(new InjectionCheck.Injector("renderable$timeTerrain", List.of("renderLevel"),
                        List.of("Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer("
                                + "Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V")),
                injector("io.github.edwardg117.renderable.hooks.mixin.LevelRendererMixin", "renderable$timeTerrain"));
        assertEquals(new InjectionCheck.Injector("renderable$timeSoundPlay",
                        List.of("play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V"), List.of()),
                injector("io.github.edwardg117.renderable.hooks.mixin.SoundEngineMixin", "renderable$timeSoundPlay"));
        // Deviation 4: the RenderType overload, which both renderers call at the end of each layer.
        assertEquals(new InjectionCheck.Injector("renderable$leaveTerrain",
                        List.of("dispatchRenderStage(Lnet/minecraft/client/renderer/RenderType;"
                                + "Lnet/minecraft/client/renderer/LevelRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;I"
                                + "Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V"),
                        List.of()),
                injector("io.github.edwardg117.renderable.hooks.mixin.ClientHooksMixin", "renderable$leaveTerrain"));
    }
```

(`injector` comes from `InjectionCheckTest`, which `HookInstallsTest` imports statically.)

Append to `SessionTest`:

```java
    @Test
    void theStageEventsAfterATerrainLayerLeaveTerrainForTheirOwnBucketsAndNamedTime() {
        // Plan 6a, deviation 4: each layer ends with NeoForge's RenderLevelStageEvent, inside renderSectionLayer.
        // ClientHooksMixin steps out of the terrain call while it runs (Probe.pauseBucket ends it, resumeBucket begins
        // it again), so its handlers stay named time and what they draw keeps its own bucket.
        Session s = session(10_000);
        Accumulator handler = s.accumulator(ModKey.event("somemod", "a.RenderLevelStageEvent"));
        Accumulator cowAcc = s.accumulator(cow);
        at(0);  s.onRenderPre(0, 0);
        assertNull(s.innermostBucket(), "nothing is open");
        at(1);  s.begin(null, Bucket.TERRAIN);
        at(2);  s.begin(null, Bucket.LOD);
        at(4);  s.end();
        assertEquals(Bucket.TERRAIN, s.innermostBucket(), "what pauseBucket checks before it steps out");
        at(5);  s.end();                                   // pauseBucket
        assertNull(s.innermostBucket());
        at(5);  s.beginAttribution(handler);               // a handler of the layer's stage event
        assertNull(s.innermostBucket(), "an attribution-only call owns no bucket");
        at(6);  s.begin(cowAcc, Bucket.ENTITY);            // an entity it draws
        at(8);  s.end();
        at(9);  s.end();
        at(9);  s.begin(null, Bucket.TERRAIN);             // resumeBucket
        at(10); s.end();                                   // the terrain wrapper's own end
        at(12); s.onRenderPost();
        at(20); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(2 * MS, f.bucket(Bucket.LOD));
        assertEquals(3 * MS, f.bucket(Bucket.TERRAIN), "1-5 less the lod inside it, then 9-10");
        assertEquals(2 * MS, f.bucket(Bucket.ENTITY), "what the handler draws keeps its own bucket");
        assertEquals(2 * MS, f.named(Window.RENDER), "the handler's 4 ms less the 2 bucketed inside it");
    }
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*SoundKeysTest' --tests '*SourceGuardsTest' --tests '*HookInstallsTest' --tests '*SessionTest'`
Expected: compilation failure, `cannot find symbol: class SoundKeys` (and `method innermostBucket()`).

- [ ] **Step 3: Add the hooks**

In `Hook.java`, replace (it occurs once):

```java
    RIDER_TICK,
    EVENTS
}
```

with:

```java
    RIDER_TICK,
    EVENTS,
    /** The {@code sound} bucket: {@code SoundManager.tick} in {@code Minecraft.tick} (spec addendum §P6.3). */
    SOUND,
    /** The {@code animate tick} bucket: {@code ClientLevel.animateTick} in {@code Minecraft.tick}. */
    ANIMATE_TICK,
    /** The {@code terrain} bucket: every {@code renderSectionLayer} call {@code LevelRenderer.renderLevel} makes. */
    TERRAIN,
    /** The Mods tab's sound rows: {@code SoundEngine.play} (spec addendum §P6.5). */
    SOUND_PLAYS
}
```

- [ ] **Step 4: Create `SoundKeys`**

```java
package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.Session;

import java.util.HashMap;

/**
 * Sound plays' accumulators (spec addendum §P6.5), found without allocating once a key has been seen: by the sound
 * event's id, then by its category's ordinal. It names no Minecraft type, so it is tested without the game: the id is
 * any object with value equality whose {@code toString()} is {@code namespace:path}, as a {@code ResourceLocation}
 * is. Keyed by equality, not identity, because a mod may build a new id for every play. Render thread only.
 */
final class SoundKeys {
    private final String[] categories;
    private final HashMap<Object, Accumulator[]> byId = new HashMap<>(128);

    /** @param categories each category's name by ordinal, as {@code SoundSource.getName()} gives it */
    SoundKeys(String[] categories) {
        this.categories = categories.clone();
    }

    /**
     * The accumulator for a play of event {@code id} in the category with ordinal {@code category}, made on first
     * sight, or remade when it is an earlier session's. Null, with nothing made, when the instance named no event
     * ({@code id} null) or no known category ({@code Probe} passes -1 for a null {@code SoundSource}).
     */
    Accumulator get(Session s, Object id, int category) {
        if (id == null || category < 0 || category >= categories.length) return null;
        Accumulator[] byCategory = byId.get(id);
        if (byCategory == null) {
            byCategory = new Accumulator[categories.length];
            byId.put(id, byCategory);
        }
        Accumulator a = byCategory[category];
        if (a == null || !s.owns(a)) {
            a = s.accumulator(ModKey.sound(id.toString(), categories[category]));
            byCategory[category] = a;
        }
        return a;
    }

    /** Forgets every key. {@code Probe} calls it when a session is attached or detached. */
    void clear() {
        byId.clear();
    }
}
```

- [ ] **Step 5: Add `Probe.beginSound`**

In `Probe.java`, replace (it occurs once):

```java
import net.minecraft.client.particle.Particle;
```

with:

```java
import net.minecraft.client.particle.Particle;
import net.minecraft.client.resources.sounds.SoundInstance;
```

and replace (it occurs once):

```java
import net.minecraft.resources.ResourceLocation;
```

with:

```java
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
```

Find (it occurs once):

```java
    private static final HashMap<ResourceLocation, Accumulator> PAYLOAD_BY_ID = new HashMap<>(64);
```

and replace it with:

```java
    private static final HashMap<ResourceLocation, Accumulator> PAYLOAD_BY_ID = new HashMap<>(64);

    /**
     * Sound plays' accumulators by event id, then category (spec addendum §P6.5). Render thread only; cleared on attach
     * and detach.
     */
    private static final SoundKeys SOUNDS = new SoundKeys(soundCategoryNames());
```

In `attach`, find (it occurs once):

```java
        PAYLOAD_BY_ID.clear();
        MESH_BY_OBJECT.clear();
        seenVertices = false;
```

and replace it with:

```java
        PAYLOAD_BY_ID.clear();
        SOUNDS.clear();
        MESH_BY_OBJECT.clear();
        seenVertices = false;
```

In `detach`, find (it occurs once):

```java
        PAYLOAD_BY_ID.clear();
        MESH_BY_OBJECT.clear();
    }
```

and replace it with:

```java
        PAYLOAD_BY_ID.clear();
        SOUNDS.clear();
        MESH_BY_OBJECT.clear();
    }
```

Then find the end of `beginPayload` (it occurs once):

```java
            PAYLOAD_BY_ID.put(id, a);
        }
        s.beginAttribution(a);
        return true;
    }
```

and replace it with:

```java
            PAYLOAD_BY_ID.put(id, a);
        }
        s.beginAttribution(a);
        return true;
    }

    /**
     * One sound play (spec addendum §P6.5), keyed by the event and category the instance names as the play begins,
     * before any PlaySoundEvent handler replaces or drops it: a replaced sound's cost stays on the row of the sound
     * asked for, and a dropped one is still a play. Attribution-only, like a packet: the bucket the play runs in
     * keeps its time. Ignored off the render thread, where a mod may play from its own thread, and for an instance
     * that names no event or no category. Returns whether a call was begun.
     */
    public static boolean beginSound(SoundInstance sound) {
        Session s = session;
        if (s == null || sound == null || Thread.currentThread() != renderThread) return false;
        SoundSource source = sound.getSource();
        Accumulator a = SOUNDS.get(s, sound.getLocation(), source == null ? -1 : source.ordinal());
        if (a == null) return false;
        SEEN[Hook.SOUND_PLAYS.ordinal()] = true;
        s.beginAttribution(a);
        return true;
    }

    /** Each {@code SoundSource}'s name by ordinal, for {@link #SOUNDS}. */
    private static String[] soundCategoryNames() {
        SoundSource[] all = SoundSource.values();
        String[] names = new String[all.length];
        for (int i = 0; i < all.length; i++) names[i] = all[i].getName();
        return names;
    }
```

`SoundSource` is a plain enum with no static dependencies, so reading it in `Probe`'s static initializer is safe on any
thread.

- [ ] **Step 6: Time the sound engine's tick and the random block effects**

In `MinecraftMixin.java`, replace (it occurs once):

```java
import io.github.edwardg117.renderable.hooks.FrameBoundaries;
```

with:

```java
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Hook;
import io.github.edwardg117.renderable.hooks.FrameBoundaries;
```

and replace (it occurs once):

```java
import net.minecraft.client.Minecraft;
```

with:

```java
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundManager;
```

Replace the end of the class javadoc (it occurs once):

```java
 * those events, at any priority, falls inside the window it belongs to.
 */
```

with:

```java
 * those events, at any priority, falls inside the window it belongs to. Inside the tick it times two buckets, the
 * sound engine's tick and the random block display effects (spec addendum §P6.3).
 */
```

Then find the end of `renderable$timeFrameCap` and the class (it occurs once):

```java
        Probe.beginFrameCap();
        try {
            original.call(fps);
        } finally {
            Probe.endFrameCap();
        }
    }
}
```

and replace it with:

```java
        Probe.beginFrameCap();
        try {
            original.call(fps);
        } finally {
            Probe.endFrameCap();
        }
    }

    /**
     * The {@code sound} bucket (spec addendum §P6.3): updating the playing sounds and playing the ones
     * {@code playDelayed} queued. Called every tick, paused or not.
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;tick(Z)V"))
    private void renderable$timeSound(SoundManager soundManager, boolean paused, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(soundManager, paused);
            return;
        }
        Probe.beginBucket(Bucket.SOUND, Hook.SOUND);
        try {
            original.call(soundManager, paused);
        } finally {
            Probe.endBucket();
        }
    }

    /** The {@code animate tick} bucket: drips, smoke and sparkles around the player, and the particles they spawn. */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;animateTick(III)V"))
    private void renderable$timeAnimateTick(ClientLevel level, int x, int y, int z, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(level, x, y, z);
            return;
        }
        Probe.beginBucket(Bucket.ANIMATE_TICK, Hook.ANIMATE_TICK);
        try {
            original.call(level, x, y, z);
        } finally {
            Probe.endBucket();
        }
    }
}
```

- [ ] **Step 7: Create `LevelRendererMixin`**

```java
package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Hook;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The {@code terrain} bucket (spec addendum §P6.3): the seven chunk-layer draws {@code renderLevel} makes, solid
 * through tripwire, one injector for all of them. Whatever supplies the body is inside it: Sodium overwrites
 * {@code renderSectionLayer}, and Distant Horizons' head injection draws its LODs there, which {@code lod} carves out
 * (spec addendum §P6.4). Iris's {@code @WrapWithCondition} on the same calls chains with this wrapper; its shadow pass
 * calls the method through an accessor, so shadow terrain stays outside. The stage event NeoForge posts at the end of
 * each layer is not terrain: {@code ClientHooksMixin} steps out of this call while it runs.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void renderable$timeTerrain(LevelRenderer renderer, RenderType renderType, double camX, double camY,
                                        double camZ, Matrix4f frustumMatrix, Matrix4f projectionMatrix,
                                        Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(renderer, renderType, camX, camY, camZ, frustumMatrix, projectionMatrix);
            return;
        }
        Probe.beginBucket(Bucket.TERRAIN, Hook.TERRAIN);
        try {
            original.call(renderer, renderType, camX, camY, camZ, frustumMatrix, projectionMatrix);
        } finally {
            Probe.endBucket();
        }
    }
}
```

- [ ] **Step 7a: Step out of `terrain` for the layer's stage event (deviation 4)**

In `Session.java`, find (it occurs once):

```java
    /** The window open right now: the tick, tasks or the render window, or -1 outside all three. */
```

and replace it with:

```java
    /**
     * The bucket of the innermost open call: null when nothing is open, when that call is attribution-only, or when it
     * is deeper than {@link #MAX_DEPTH}. {@code Probe.pauseBucket} reads it before it steps out of a terrain call
     * (plan 6a, deviation 4).
     */
    public Bucket innermostBucket() {
        return depth == 0 || depth > MAX_DEPTH ? null : stackBucket[depth - 1];
    }

    /** The window open right now: the tick, tasks or the render window, or -1 outside all three. */
```

In `Probe.java`, find (it occurs once):

```java
    /** Ends a bucket-only timing. Makes the same thread decision as {@link #beginBucket} so the stack stays balanced. */
    public static void endBucket() {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return;
        s.end();
    }
```

and replace it with:

```java
    /** Ends a bucket-only timing. Makes the same thread decision as {@link #beginBucket} so the stack stays balanced. */
    public static void endBucket() {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return;
        s.end();
    }

    /**
     * Steps out of the innermost open call while other work runs inside it, when that call is a bucket-only timing of
     * {@code bucket}: NeoForge's stage event at the end of each terrain layer (plan 6a, deviation 4). Render thread
     * only. Returns whether it stepped out; the caller must then call {@link #resumeBucket} with the same bucket.
     */
    public static boolean pauseBucket(Bucket bucket) {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread || s.innermostBucket() != bucket) return false;
        s.end();
        return true;
    }

    /** Begins again the bucket-only timing {@link #pauseBucket} ended. Call it only when that returned true. */
    public static void resumeBucket(Bucket bucket) {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return;
        s.begin(null, bucket);
    }
```

The innermost `TERRAIN` call is always a bucket-only one: only `LevelRendererMixin` begins that bucket, through
`beginBucket`. When the event does not run inside a terrain call, nothing happens. That covers the event outside
`renderLevel`, and a terrain call nested inside another bucket, which credits nothing of its own anyway.

Create `src/main/java/io/github/edwardg117/renderable/hooks/mixin/ClientHooksMixin.java`:

```java
package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Keeps NeoForge's stage event out of the {@code terrain} bucket (plan 6a, deviation 4). Vanilla's
 * {@code renderSectionLayer} and Sodium's overwrite of it both end by posting the layer's RenderLevelStageEvent
 * through this method, inside {@code LevelRendererMixin}'s terrain call. While it runs, the terrain call is closed,
 * so the event's handlers stay named time and whatever they draw keeps its own bucket; then it is opened again. The
 * selector carries the descriptor because the method has an overload that takes a stage.
 */
@Mixin(ClientHooks.class)
public abstract class ClientHooksMixin {

    @WrapMethod(method = "dispatchRenderStage(Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/LevelRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V")
    private static void renderable$leaveTerrain(RenderType renderType, LevelRenderer levelRenderer,
                                                Matrix4f modelViewMatrix, Matrix4f projectionMatrix, int renderTick,
                                                Camera camera, Frustum frustum, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(renderType, levelRenderer, modelViewMatrix, projectionMatrix, renderTick, camera, frustum);
            return;
        }
        boolean paused = Probe.pauseBucket(Bucket.TERRAIN);
        try {
            original.call(renderType, levelRenderer, modelViewMatrix, projectionMatrix, renderTick, camera, frustum);
        } finally {
            if (paused) Probe.resumeBucket(Bucket.TERRAIN);
        }
    }
}
```

- [ ] **Step 8: Create `SoundEngineMixin`**

```java
package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Times each sound play (spec addendum §P6.5): the whole of {@code SoundEngine.play}, which posts NeoForge's
 * PlaySoundEvent, checks the volume and waits for the sound thread to hand over a channel. A play that returns early
 * costs almost nothing and still counts. Attribution-only: the bucket the play runs in keeps its time, and the Mods
 * tab names it {@code sound <event> (<category>)}.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    @WrapMethod(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V")
    private void renderable$timeSoundPlay(SoundInstance sound, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(sound);
            return;
        }
        boolean timed = Probe.beginSound(sound);
        try {
            original.call(sound);
        } finally {
            if (timed) Probe.end();
        }
    }
}
```

- [ ] **Step 9: Register the mixins and their hooks**

In `renderable.mixins.json`, replace (it occurs once):

```json
    "ClientLevelMixin"
  ],
```

with:

```json
    "ClientLevelMixin",
    "LevelRendererMixin",
    "SoundEngineMixin",
    "ClientHooksMixin"
  ],
```

In `HookInstalls.java`, replace (it occurs once):

```java
            entry("ClientLevelMixin.renderable$timeRiderTick", Hook.RIDER_TICK),
```

with:

```java
            entry("ClientLevelMixin.renderable$timeRiderTick", Hook.RIDER_TICK),
            entry("MinecraftMixin.renderable$timeSound", Hook.SOUND),
            entry("MinecraftMixin.renderable$timeAnimateTick", Hook.ANIMATE_TICK),
            entry("LevelRendererMixin.renderable$timeTerrain", Hook.TERRAIN),
            entry("ClientHooksMixin.renderable$leaveTerrain", Hook.TERRAIN),
            entry("SoundEngineMixin.renderable$timeSoundPlay", Hook.SOUND_PLAYS),
```

- [ ] **Step 10: Build and run the tests**

Run: `./gradlew compileJava --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL both times, **287** tests, 0 failures. `HookInstallsTest`'s existing test now reads the three
new mixins and maps all four new hooks.

- [ ] **Step 11: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/Hook.java src/main/java/io/github/edwardg117/renderable/hooks/SoundKeys.java src/main/java/io/github/edwardg117/renderable/hooks/Probe.java src/main/java/io/github/edwardg117/renderable/hooks/HookInstalls.java src/main/java/io/github/edwardg117/renderable/hooks/mixin/MinecraftMixin.java src/main/java/io/github/edwardg117/renderable/hooks/mixin/LevelRendererMixin.java src/main/java/io/github/edwardg117/renderable/hooks/mixin/SoundEngineMixin.java src/main/java/io/github/edwardg117/renderable/hooks/mixin/ClientHooksMixin.java src/main/java/io/github/edwardg117/renderable/core/Session.java src/main/resources/renderable.mixins.json src/test/java/io/github/edwardg117/renderable/hooks/SoundKeysTest.java src/test/java/io/github/edwardg117/renderable/core/SourceGuardsTest.java src/test/java/io/github/edwardg117/renderable/hooks/HookInstallsTest.java src/test/java/io/github/edwardg117/renderable/core/SessionTest.java
```

Report to the orchestrator for the commit: `feat(hooks): sound, animate tick, terrain and sound plays hooks`.

---

### Task 6: Distant Horizons

**Files:**
- Modify: `gradle.properties`
- Modify: `build.gradle`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Hook.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/RenderableMixinPlugin.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/HookInstalls.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/distanthorizons/ClientApiMixin.java`
- Modify: `src/main/resources/renderable.thirdparty.mixins.json`
- Test: `src/test/java/io/github/edwardg117/renderable/hooks/HookInstallsTest.java` (refactor a helper, append 2)
- Modify, by the orchestrator only: `CLAUDE.md` (Step 10)

**Interfaces:**
- Consumes: `Bucket.LOD` (Task 1) and its carve-out (Task 2); `Probe.beginBucket(Bucket, Hook)`, `Probe.endBucket()`.
- Produces:
  - `Hook.LOD`;
  - the handlers `ClientApiMixin.renderable$timeRenderLods`, `renderable$timeDeferredLods`, `renderable$timeFadeOpaque`
    and `renderable$timeFadeTransparent`;
  - `REQUIRED_MOD` entry `"distanthorizons" -> "distanthorizons"`;
  - Gradle property `distanthorizons_version=3.3.3-1.21.1`; DH in `run/mods` under `-PpackMods`.

Spec §P6.3, §P6.7 and §P6.8, with deviation 1. The mixin targets `ClientApi` by name with `remap = false`, and every
method it wraps takes nothing and returns nothing, so no DH class is needed to compile (P6.1, change 2). Each selector
carries its descriptor (`renderLods()V`). So a later DH that gives a method arguments is no match: with
`require = 0` the injector is skipped, and the install check lists `LOD` under "did not apply". Mixin is not asked to
fit a handler to a signature it no longer matches. Without DH, the plugin declines the mixin by its sub-package, so
`LOD` is in none of the three lists, as Flywheel's hook is without Flywheel.

DH goes into `packModRuntime`, not `packModJars`. In this build `packModJars` feeds only `extractNestedModJars`, whose
output is the compile classpath, and DH has no nested jar to extract. `packModRuntime` is what `installPackMods`
copies into `run/mods` under `-PpackMods`.

- [ ] **Step 1: Write the failing tests**

In `HookInstallsTest.java`, replace (it occurs once):

```java
import java.nio.charset.StandardCharsets;
```

with:

```java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
```

Replace the `mixins()` helper (it occurs once):

```java
    /** Every mixin both configs list, by its full class name. */
    static List<String> mixins() throws IOException {
        List<String> out = new ArrayList<>();
        for (String config : List.of("renderable.mixins.json", "renderable.thirdparty.mixins.json")) {
            try (InputStream in = HookInstallsTest.class.getResourceAsStream("/" + config)) {
                assertNotNull(in, config);
                JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                String pkg = json.get("package").getAsString();
                for (JsonElement e : json.getAsJsonArray("client")) out.add(pkg + "." + e.getAsString());
            }
        }
        return out;
    }
```

with:

```java
    /** Every mixin both configs list, by its full class name. */
    static List<String> mixins() throws IOException {
        List<String> out = new ArrayList<>(mixinsOf("renderable.mixins.json"));
        out.addAll(mixinsOf("renderable.thirdparty.mixins.json"));
        return out;
    }

    /** The mixins one config lists, by full class name. */
    static List<String> mixinsOf(String config) throws IOException {
        try (InputStream in = HookInstallsTest.class.getResourceAsStream("/" + config)) {
            assertNotNull(in, config);
            JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            String pkg = json.get("package").getAsString();
            List<String> out = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("client")) out.add(pkg + "." + e.getAsString());
            return out;
        }
    }
```

Then append to `HookInstallsTest`:

```java
    static final String CLIENT_API_MIXIN = "io.github.edwardg117.renderable.hooks.mixin.distanthorizons.ClientApiMixin";
    static final String CLIENT_API = "com/seibel/distanthorizons/core/api/internal/ClientApi";

    /** An annotation's value by name, or null. */
    private static Object annotationValue(AnnotationNode a, String name) {
        for (int i = 0; a.values != null && i + 1 < a.values.size(); i += 2) {
            if (name.equals(a.values.get(i))) return a.values.get(i + 1);
        }
        return null;
    }

    @Test
    void theDistantHorizonsMixinIsSkippedWithoutDistantHorizons() throws IOException {
        // Review focus 1. Most players have no DH, so the plugin must decline the mixin: LOD then sits in none of the
        // hook lists and nothing is logged. The plugin gates a mixin by its sub-package; its map is read as source,
        // because the plugin loads FML, which this classpath lacks. Only the optional config may list the mixin.
        assertTrue(CLIENT_API_MIXIN.startsWith("io.github.edwardg117.renderable.hooks.mixin.distanthorizons."));
        assertTrue(mixinsOf("renderable.thirdparty.mixins.json").contains(CLIENT_API_MIXIN));
        assertFalse(mixinsOf("renderable.mixins.json").contains(CLIENT_API_MIXIN), "the required config would fail the game");
        String plugin = Files.readString(Path.of("src/main/java/io/github/edwardg117/renderable/hooks/RenderableMixinPlugin.java"));
        assertTrue(plugin.contains("\"distanthorizons\", \"distanthorizons\""), "REQUIRED_MOD gates the package on DH's mod id");

        int injectors = 0;
        for (MethodNode m : classNode(CLIENT_API_MIXIN).methods) {
            List<AnnotationNode> annotations = new ArrayList<>();
            if (m.visibleAnnotations != null) annotations.addAll(m.visibleAnnotations);
            if (m.invisibleAnnotations != null) annotations.addAll(m.invisibleAnnotations);
            for (AnnotationNode a : annotations) {
                if (!a.desc.equals("Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;")) continue;
                assertEquals(0, annotationValue(a, "require"), m.name + " must not fail the game when DH changes");
                injectors++;
            }
        }
        assertEquals(4, injectors, "one per ClientApi render method");
    }

    @Test
    void aDistantHorizonsRenderMethodThatWasRenamedOrResignedDidNotApply() throws IOException {
        // Review focus 4. ClientApi as DH 3.3.3 and 2.4.5-b have it (javap, 2026-10-10): every injector finds its method.
        ClassNode mixin = classNode(CLIENT_API_MIXIN);
        ClassNode pinned = target(CLIENT_API, method("renderLods", "()V"), method("renderDeferredLodsForShaders", "()V"),
                method("renderFadeOpaque", "()V"), method("renderFadeTransparent", "()V"));
        for (InjectionCheck.Injector in : InjectionCheck.injectors(mixin)) {
            assertTrue(InjectionCheck.present(in, pinned), in.handler() + " finds its method in the pinned ClientApi");
        }

        // A later DH that gives renderLods an argument and renames the transparent fade.
        ClassNode updated = target(CLIENT_API, method("renderLods", "(F)V"), method("renderDeferredLodsForShaders", "()V"),
                method("renderFadeOpaque", "()V"), method("renderFadeTranslucent", "()V"));
        assertFalse(InjectionCheck.present(injector(CLIENT_API_MIXIN, "renderable$timeRenderLods"), updated),
                "the selector carries its descriptor, so a re-signed method is not taken for the one the handler wraps");
        assertFalse(InjectionCheck.present(injector(CLIENT_API_MIXIN, "renderable$timeFadeTransparent"), updated));
        HookInstalls.check(CLIENT_API_MIXIN, mixin, updated);
        assertTrue(HookInstalls.failed().contains(Hook.LOD.name()), "listed under hooks that did not apply, not a silent 0");
        assertFalse(HookInstalls.installed().contains(Hook.LOD.name()));
    }
```

(`target`, `method` and `injector` come from `InjectionCheckTest`, which `HookInstallsTest` imports statically.
`HookInstalls` keeps its state for the whole test run, so no test may expect `LOD` installed.)

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*HookInstallsTest'`
Expected: compilation failure, `cannot find symbol: variable LOD` in `Hook`.

- [ ] **Step 3: Add the hook**

In `Hook.java`, replace (it occurs once):

```java
    TERRAIN,
```

with:

```java
    TERRAIN,
    /** The {@code lod} bucket: Distant Horizons' four {@code ClientApi} render methods, when DH is loaded (§P6.3). */
    LOD,
```

So the hook lists read `SOUND, ANIMATE_TICK, TERRAIN, LOD, SOUND_PLAYS`, as the spec's check A1 lists them.

- [ ] **Step 4: Create `ClientApiMixin`**

Create `src/main/java/io/github/edwardg117/renderable/hooks/mixin/distanthorizons/ClientApiMixin.java`:

```java
package io.github.edwardg117.renderable.hooks.mixin.distanthorizons;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Hook;
import io.github.edwardg117.renderable.hooks.Probe;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The {@code lod} bucket (spec addendum §P6.3). Distant Horizons draws its LODs from a head injection in
 * {@code LevelRenderer.renderSectionLayer}, which calls one of these four methods by the layer it is given: the main
 * pass, the deferred pass for shaders and the two fades. No DH class is needed to compile: the target is named, and
 * every method wrapped takes nothing and returns nothing. Applied only when the distanthorizons mod is loaded (package
 * gate in RenderableMixinPlugin). Each injector is require = 0 and its selector carries the descriptor, so a renamed or
 * re-signed method leaves the hook under "did not apply" instead of failing the game.
 */
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ClientApi", remap = false)
public abstract class ClientApiMixin {

    @WrapMethod(method = "renderLods()V", remap = false, require = 0)
    private void renderable$timeRenderLods(Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call();
            return;
        }
        Probe.beginBucket(Bucket.LOD, Hook.LOD);
        try {
            original.call();
        } finally {
            Probe.endBucket();
        }
    }

    @WrapMethod(method = "renderDeferredLodsForShaders()V", remap = false, require = 0)
    private void renderable$timeDeferredLods(Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call();
            return;
        }
        Probe.beginBucket(Bucket.LOD, Hook.LOD);
        try {
            original.call();
        } finally {
            Probe.endBucket();
        }
    }

    @WrapMethod(method = "renderFadeOpaque()V", remap = false, require = 0)
    private void renderable$timeFadeOpaque(Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call();
            return;
        }
        Probe.beginBucket(Bucket.LOD, Hook.LOD);
        try {
            original.call();
        } finally {
            Probe.endBucket();
        }
    }

    @WrapMethod(method = "renderFadeTransparent()V", remap = false, require = 0)
    private void renderable$timeFadeTransparent(Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call();
            return;
        }
        Probe.beginBucket(Bucket.LOD, Hook.LOD);
        try {
            original.call();
        } finally {
            Probe.endBucket();
        }
    }
}
```

- [ ] **Step 5: Gate it, list it and map it**

In `RenderableMixinPlugin.java`, replace (it occurs once):

```java
            "acceleratedrendering", "acceleratedrendering");
```

with:

```java
            "acceleratedrendering", "acceleratedrendering",
            "distanthorizons", "distanthorizons");
```

In `renderable.thirdparty.mixins.json`, replace (it occurs once):

```json
    "acceleratedrendering.AcceleratedBufferBuilderMixin"
  ],
```

with:

```json
    "acceleratedrendering.AcceleratedBufferBuilderMixin",
    "distanthorizons.ClientApiMixin"
  ],
```

In `HookInstalls.java`, replace (it occurs once):

```java
            entry("FlywheelRenderDispatcherMixin.renderable$timeCrumbling", Hook.FLYWHEEL),
```

with:

```java
            entry("FlywheelRenderDispatcherMixin.renderable$timeCrumbling", Hook.FLYWHEEL),
            entry("ClientApiMixin.renderable$timeRenderLods", Hook.LOD),
            entry("ClientApiMixin.renderable$timeDeferredLods", Hook.LOD),
            entry("ClientApiMixin.renderable$timeFadeOpaque", Hook.LOD),
            entry("ClientApiMixin.renderable$timeFadeTransparent", Hook.LOD),
```

- [ ] **Step 6: Pin DH for the dev client**

In `gradle.properties`, replace (it occurs once):

```properties
kotlin_for_forge_version=5.12.0
```

with:

```properties
kotlin_for_forge_version=5.12.0
# Distant Horizons runs in the -PpackMods dev client and is read with javap. Nothing compiles against it: its mixin
# targets ClientApi by name (spec addendum §P6.8).
distanthorizons_version=3.3.3-1.21.1
```

In `build.gradle`, replace (it occurs once):

```groovy
    packModRuntime "maven.modrinth:create-enchantable-machinery:${enchantable_machinery_version}"
}
```

with:

```groovy
    packModRuntime "maven.modrinth:create-enchantable-machinery:${enchantable_machinery_version}"
    // Run only: no nested jar to extract and nothing compiles against it, so not in packModJars (spec addendum §P6.8).
    packModRuntime "maven.modrinth:distanthorizons:${distanthorizons_version}"
}
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **289** tests, 0 failures.

- [ ] **Step 8: Check the build resolves DH where it should, and only there**

```bash
./gradlew dependencies --configuration packModRuntime --no-daemon | grep distanthorizons
./gradlew dependencies --configuration compileClasspath --no-daemon | grep -c distanthorizons
./gradlew installPackMods -PpackMods --no-daemon
ls run/mods
```

Expected: the first prints `maven.modrinth:distanthorizons:3.3.3-1.21.1`, the second prints `0`, and `run/mods` holds
`distanthorizons-3.3.3-1.21.1.jar` beside the Sodium, Iris, Create, Kotlin for Forge and Enchantable Machinery jars.
(`run/` is ignored by git, and Task 8 installs the same set.)

- [ ] **Step 9: Re-verify the targets against the jar Gradle resolved**

As `CLAUDE.md` and §P6.8 require:

```bash
javap -p -s -cp run/mods/distanthorizons-3.3.3-1.21.1.jar com.seibel.distanthorizons.core.api.internal.ClientApi | grep -A1 "renderLods\|renderDeferredLodsForShaders\|renderFadeOpaque\|renderFadeTransparent"
```

Expected: the four `public void ...()` methods, each with `descriptor: ()V`. Anything else: stop and report.

- [ ] **Step 10: (Orchestrator only) Record the pin in `CLAUDE.md`**

The user approved the pin, and `CLAUDE.md` is otherwise the user's file, so the orchestrator makes this edit and stages
it with this task's commit (§P6.8). Replace (it occurs once):

```
  Enchantable Machinery `3.6.0+mc1.21.1-neoforge` with Kotlin for Forge.
```

with:

```
  Enchantable Machinery `3.6.0+mc1.21.1-neoforge` with Kotlin for Forge. Distant Horizons `3.3.3-1.21.1` is
  run only (dev client and javap): its mixin targets `ClientApi` by name.
```

Then `git add CLAUDE.md`.

- [ ] **Step 11: Stage and report**

```bash
git add gradle.properties build.gradle src/main/java/io/github/edwardg117/renderable/core/Hook.java src/main/java/io/github/edwardg117/renderable/hooks/RenderableMixinPlugin.java src/main/java/io/github/edwardg117/renderable/hooks/HookInstalls.java src/main/java/io/github/edwardg117/renderable/hooks/mixin/distanthorizons/ClientApiMixin.java src/main/resources/renderable.thirdparty.mixins.json src/test/java/io/github/edwardg117/renderable/hooks/HookInstallsTest.java
```

Report to the orchestrator for the commit (with `CLAUDE.md` from Step 10): `feat(hooks): Distant Horizons' LOD drawing as the lod bucket, and DH 3.3.3 in the dev client`.

---

### Task 7: Documentation

**Files:**
- Modify: `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md`
- Modify: `docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md`
- Modify: `docs/superpowers/specs/2026-10-10-renderable-phase-6-design.md`

**Interfaces:** none.

The 6a rows of §P6.11 and the additions in deviation 2. Deviation 1 is applied to §P6.8, deviation 4 to §P6.3, and
decision 3 to §P6.7. Each find string below occurs once in its file; if one does not, stop and report rather than
guess. The files are CRLF.

- [ ] **Step 1: Base spec §3.2, the frame record**

Replace:

```markdown
| buckets | `BLOCK_ENTITY`, `ENTITY`, `PARTICLE_RENDER`, `FLYWHEEL`, `CHUNK_UPLOAD`, `LIGHT`, `HUD`, `PARTICLE_TICK`, `BE_TICK`, `ENTITY_TICK`, each a nanosecond sum for this frame |
```

with:

```markdown
| buckets | `TERRAIN`, `LOD`, `BLOCK_ENTITY`, `ENTITY`, `PARTICLE_RENDER`, `FLYWHEEL`, `CHUNK_UPLOAD`, `LIGHT`, `HUD`, `PARTICLE_TICK`, `BE_TICK`, `ENTITY_TICK`, `SOUND`, `ANIMATE_TICK`, each a nanosecond sum for this frame (`TERRAIN`, `LOD`, `SOUND` and `ANIMATE_TICK` from phase 6) |
```

Replace:

```markdown
- `otherRenderNanos = renderNanos - (BLOCK_ENTITY + ENTITY + PARTICLE_RENDER + FLYWHEEL + CHUNK_UPLOAD + LIGHT + HUD)`
- `otherTickNanos = tickNanos - (PARTICLE_TICK + BE_TICK + ENTITY_TICK)`
```

with:

```markdown
- `otherRenderNanos = renderNanos - (TERRAIN + LOD + BLOCK_ENTITY + ENTITY + PARTICLE_RENDER + FLYWHEEL + CHUNK_UPLOAD + LIGHT + HUD)`
- `otherTickNanos = tickNanos - (PARTICLE_TICK + BE_TICK + ENTITY_TICK + SOUND + ANIMATE_TICK)`
- `unnamedTickNanos = max(0, otherTickNanos - namedNanos[tick])`: the part of the tick that neither a bucket nor the
  Mods tab names, printed as `unnamed` at the end of the tick's brackets (phase 6)
```

Replace:

```markdown
counter. Attribution-only calls (§3.4) own none, so a block entity drawn inside an event handler is still
block entity time.
```

with:

```markdown
counter. Attribution-only calls (§3.4) own none, so a block entity drawn inside an event handler is still
block entity time. `LOD` is the one exception (phase 6, spec addendum §P6.4): it counts as itself wherever it runs,
once however it nests, and the outermost bucket enclosing it is credited its own time less every `LOD` call inside
it. So `TERRAIN` holds Sodium's draw and `LOD` Distant Horizons', and no nanosecond is in both.
```

- [ ] **Step 2: Base spec §3.4, the keys**

Replace:

```markdown
| `ModKey` | kind (event, layer, packet), owning mod id, event class or id | event handlers, HUD layers, packets (phase 5); attribution-only |
```

with:

```markdown
| `ModKey` | kind (event, layer, packet, sound), owning mod id, event class or id; for a sound, `<event id> (<category>)` with the event id's namespace as the mod | event handlers, HUD layers, packets (phase 5) and sound plays (phase 6); attribution-only |
```

- [ ] **Step 3: Base spec §4, the hooks**

Replace:

```markdown
this pack pins (Sodium 0.8.13, Iris 1.8.14-beta.1, Flywheel 1.0.6 bundled in Create 6.0.10).
```

with:

```markdown
this pack pins (Sodium 0.8.13, Iris 1.8.14-beta.1, Flywheel 1.0.6 bundled in Create 6.0.10, Distant Horizons 3.3.3).
```

Replace:

```markdown
- A mixin config plugin applies Sodium, Iris and Flywheel mixins only when that mod is loaded.
```

with:

```markdown
- A mixin config plugin applies Sodium, Iris, Flywheel, Accelerated Rendering and Distant Horizons mixins only when
  that mod is loaded.
```

Replace:

```markdown
The spec addendum's §P5.A0 records the bytecode offsets these were verified at.
```

with:

```markdown
The spec addendum's §P5.A0 records the bytecode offsets these were verified at.

### 4.7 Phase 6: sound, terrain and Distant Horizons

| Target | Kind | Records |
| --- | --- | --- |
| call to `SoundManager#tick(boolean)` inside `Minecraft#tick()` | `@WrapOperation` | `SOUND` bucket |
| call to `ClientLevel#animateTick(int, int, int)` inside `Minecraft#tick()` | `@WrapOperation` | `ANIMATE_TICK` bucket |
| the seven calls to `LevelRenderer#renderSectionLayer(RenderType, double, double, double, Matrix4f, Matrix4f)` inside `LevelRenderer#renderLevel` | `@WrapOperation`, one injector for all seven | `TERRAIN` bucket |
| NeoForge `ClientHooks#dispatchRenderStage(RenderType, LevelRenderer, Matrix4f, Matrix4f, int, Camera, Frustum)`, which vanilla's and Sodium's `renderSectionLayer` call at the end of each layer | `@WrapMethod` | closes the open `TERRAIN` call while the layer's `RenderLevelStageEvent` runs, and opens it again after |
| `SoundEngine#play(SoundInstance)` | `@WrapMethod` | `ModKey` sound time per event and category, attribution-only |
| Distant Horizons `ClientApi#renderLods()`, `#renderDeferredLodsForShaders()`, `#renderFadeOpaque()` and `#renderFadeTransparent()` (gated on `distanthorizons`, targeted by string) | `@WrapMethod` | `LOD` bucket, carved out of the bucket enclosing it |

The phase 6 addendum (`docs/superpowers/specs/2026-10-10-renderable-phase-6-design.md`, §P6.2) records the bytecode
offsets these were verified at, and the Sodium, Iris and Distant Horizons code around the terrain calls.
```

- [ ] **Step 4: Base spec §6.1, the text report**

Replace:

```markdown
   hitch time as a share of wall time; average split per frame: tick, the buckets (the HUD among them),
   other render, tasks, the frame cap and swap, leaving out a bucket, tasks or the frame cap that took no
   time, with the part of tick, other render and tasks that the Mods section names in brackets; the GC count
```

with:

```markdown
   hitch time as a share of wall time; average split per frame: tick, the buckets (the HUD among them, and
   `terrain` and `lod` first), other render, tasks, the frame cap and swap, leaving out a bucket, tasks or the
   frame cap that took no time, with the part of tick, other render and tasks that the Mods section names in
   brackets; the tick's brackets partition it, its buckets, then `mods`, then `unnamed` for the rest when above
   zero (phase 6); the GC count
```

Replace:

```markdown
   none was clean; Renderable's own estimated overhead; and the measured time of its own in-frame work
   (§3.5), when it had any.
```

with:

```markdown
   none was clean; Renderable's own estimated overhead; the measured time of its own in-frame work
   (§3.5), when it had any; and, when anything played a sound, `Sound plays: x ms a frame, n plays a second`,
   the Mods section's sound rows summed (phase 6).
```

Replace:

```markdown
6. **Mods** (top 15, each with its top 3 event types, HUD layers or packets), under a note that counting is
```

with:

```markdown
6. **Mods** (top 15, each with its top 3 event types, HUD layers, packets or sounds), under a note that counting is
```

- [ ] **Step 5: Base spec §6.2, the JSON**

Replace:

```markdown
  "schema": 5,
  "mod": { "version": "0.5.0" },
```

with:

```markdown
  "schema": 6,
  "mod": { "version": "0.6.0" },
```

Replace:

```markdown
                "namedUs": { "render": ..., "tick": ..., "tasks": ... }, "bucketsUs": { ... },
```

with:

```markdown
                "namedUs": { "render": ..., "tick": ..., "tasks": ... }, "bucketsUs": { "terrain": ..., "lod": ..., ... },
```

Replace:

```markdown
The environment's `hooksInstalled`, `hooksFired` and `hooksFailed` arrays list hooks by name; schema 4 called
`hooksFired` `hooksApplied` (phase 5).
```

with:

```markdown
The environment's `hooksInstalled`, `hooksFired` and `hooksFailed` arrays list hooks by name; schema 4 called
`hooksFired` `hooksApplied` (phase 5).

Schema 6 (phase 6) adds four labels to `avgBucketsMs` and to each frame's `bucketsUs`: `terrain`, `lod`, `sound`
and `animate tick`. Sound rows sit in `mods` like any other key. Buckets are written by label, never by position.
Nothing else changes shape.
```

Replace:

```markdown
`event@mod:class`, `layer@mod:id`, `packet@mod:id`.
```

with:

```markdown
`event@mod:class`, `layer@mod:id`, `packet@mod:id`, `sound@mod:event (category)`.
```

- [ ] **Step 6: Base spec §11, the phases**

Replace:

```markdown
| 5b | the recommendation engine and its nineteen rules, the environment's memory and JVM facts, pause-only GC sampling, the Advice tab, the report's Recommendations section, the chat line's suggestion count, the JSON's `recommendations` | spec addendum §P5.B's checks, in the in-pack acceptance at the end of phase 5 |
```

with:

```markdown
| 5b | the recommendation engine and its nineteen rules, the environment's memory and JVM facts, pause-only GC sampling, the Advice tab, the report's Recommendations section, the chat line's suggestion count, the JSON's `recommendations` | spec addendum §P5.B's checks, in the in-pack acceptance at the end of phase 5 |
| 6a | the `terrain`, `lod`, `sound` and `animate tick` buckets, `lod` carved out of the bucket around it, sound plays as Mods tab rows with the Frames section's Sound plays line, `unnamed` in the tick's brackets, JSON schema 6, Distant Horizons in the dev client | the phase 6 addendum's checks A1 to A5 in unit tests and the dev client; the in-pack acceptance at the end of phase 6 |
```

Replace:

```markdown
order and released together as `v0.5.0`; see `docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md`.
```

with:

```markdown
order and released together as `v0.5.0`; see `docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md`.

Phase 6 is two stacked plans, 6a (measurement) and 6b (advice and release), released together as `v0.6.0`; see
`docs/superpowers/specs/2026-10-10-renderable-phase-6-design.md`.
```

- [ ] **Step 7: Phase 5 addendum, P5.A6 and P5.A7**

In `2026-09-26-renderable-phase-5-design.md`, replace:

```markdown
draws through `EntityRenderDispatcher.render` is an example: it moves from `entity` to `hud`, and is still
on the entity's own row.
```

with:

```markdown
draws through `EntityRenderDispatcher.render` is an example: it moves from `entity` to `hud`, and is still
on the entity's own row.

**Phase 6 exception (spec addendum §P6.4).** `lod` always counts as itself. When a `lod` call ends, its time is
credited to `lod` at once, and the outermost bucket enclosing it is credited its own time less every `lod` call
inside it; a `lod` call inside another counts once. Every other bucket keeps the outermost rule, so a bucket inside
`hud` still counts as `hud`. Named time is unchanged: `lod`'s credit is bucket time like any other, so a handler
around a `terrain` call around a `lod` call names neither.
```

Replace:

```markdown
  - A row expands to its members, each labelled with its kind: `event ClientTickEvent.Post`,
    `layer xaerominimap:minimap`, `packet somemod:sync_data`.
```

with:

```markdown
  - A row expands to its members, each labelled with its kind: `event ClientTickEvent.Post`,
    `layer xaerominimap:minimap`, `packet somemod:sync_data`, and from phase 6 `sound create:steam (block)`
    (spec addendum §P6.5): one row per sound event and category, under the event id's namespace, timing the whole
    `SoundEngine.play` call, so `calls/f` is plays per frame. A play only names time: the bucket it runs in keeps it.
```

- [ ] **Step 8: Phase 6 addendum: P6.3 (deviation 4), P6.7 (decision 3) and P6.8 (deviation 1)**

In `2026-10-10-renderable-phase-6-design.md`, replace:

```markdown
  - `terrain` is the chunk-layer draws `renderLevel` makes, solid through tripwire, whichever renderer
    supplies them: Sodium's, vanilla's, or another mod's head injection.
```

with:

```markdown
  - `terrain` is the chunk-layer draws `renderLevel` makes, solid through tripwire, whichever renderer
    supplies them: Sodium's, vanilla's, or another mod's head injection. It leaves out the
    `RenderLevelStageEvent` both renderers post at the end of each layer: a required wrapper on NeoForge's
    `ClientHooks.dispatchRenderStage(RenderType, ...)` closes the terrain call while it runs, so its handlers stay
    named time and what they draw keeps its own bucket (plan 6a, deviation 4).
```

Replace:

```markdown
  `lod`, by label. Sound rows appear under `mods` like any other key, with `kind` `sound`. Nothing else
  changes shape.
```

with:

```markdown
  `lod`, by label. Sound rows appear under `mods` like any other key; the key's kind word starts its serialised
  form, `sound@create:create:steam (block)`, and no `kind` field is added (plan 6a, decision 3). Nothing else
  changes shape.
```

Replace:

```markdown
- `gradle.properties` gains `distanthorizons_version=3.3.3-1.21.1`. `build.gradle` adds
  `maven.modrinth:distanthorizons:${distanthorizons_version}` to `packModJars`, so `-PpackMods` runs the
  dev client with DH. DH is not on the compile classpath (P6.1, change 2).
```

with:

```markdown
- `gradle.properties` gains `distanthorizons_version=3.3.3-1.21.1`. `build.gradle` adds
  `maven.modrinth:distanthorizons:${distanthorizons_version}` to `packModRuntime`, which `installPackMods` copies
  into `run/mods`, so `-PpackMods` runs the dev client with DH. It stays out of `packModJars`, whose jars
  `extractNestedModJars` searches for nested mod jars to compile against: DH has none, and DH is not on the compile
  classpath (P6.1, change 2; plan 6a, deviation 1).
```

- [ ] **Step 9: Check and stage**

Run: `git diff --stat`
Expected: the three spec files above, and nothing else from this task.

```bash
git add docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md docs/superpowers/specs/2026-10-10-renderable-phase-6-design.md
```

Report to the orchestrator for the commit: `docs: phase 6a amendments to the base spec and the addenda`.

---

### Task 8: Prove it in the dev client

**Files:** none in the repository. The dev client writes under `run/`, which git ignores. Distant Horizons also writes
its LOD database into the save and its config under `run/config/`; leave both.

This task proves checks A1, A3 and A4 (§P6.3, §P6.5, §P6.6) in a real game:
- every new mixin applies;
- the hook lists name the five new hooks;
- `terrain` and `lod` have time in every frame;
- sound rows appear under their mods;
- the split shows `unnamed`.

A plain run then shows the DH gate with DH absent (Review focus 1). The mute check needs the user (Step 8), because
no agent may edit `run/options.txt`. What only the pack can show, such as `create:steam` from a boiler and DH at the
other player's base, is left to 6b's in-pack acceptance.

**The user's real Gears & Frontiers client may be running on this machine.** Never stop a Java process that is not the
dev client this task started. With `-PpackMods`, Sodium rewrites the game's command line, so the dev client cannot be
matched by its own command line: stop it through the Gradle wrapper that launched it (Step 4), for every run here.

- [ ] **Step 1: Build and test**

Run: `./gradlew build --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **289** tests.

- [ ] **Step 2: Prepare the run**

1. List `run/saves`. Use the world named `New World` if it exists, or else the first world listed. If there is none,
   stop and report: this task needs an existing world.
2. Read, but do not edit, `run/options.txt`: note `maxFps:`, `enableVsync:` and `soundCategory_master:`. Expected
   `260`, `false` and `1.0`. If `soundCategory_master` is not `1.0`, stop and report: the first run needs sound on.
3. Delete `run/.mixin.out` if it exists, and note the newest file in `run/renderable/`, so the new reports can be told
   apart.

- [ ] **Step 3: Launch with the pack's mods, Distant Horizons among them**

Run it in the background:

```bash
JAVA_TOOL_OPTIONS="-Dmixin.debug.export=true -Drenderable.autostart=20" ./gradlew runClient -PquickPlay="New World" -PpackMods --no-daemon
```

(with the world name from Step 2). Wait for a new `run/renderable/renderable-*.json` to appear, checking every 15
seconds for up to 10 minutes: it arrives about 10 s after the world loads, plus the 20 s session, and a first launch
with DH loads slowly. If it never arrives, read `run/logs/latest.log`, then stop the client (Step 4) and report what
the log shows.

- [ ] **Step 4: Stop the client**

From PowerShell, find the Gradle wrapper process that launched it:

```powershell
Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
  Where-Object { $_.CommandLine -like '*-jar H:/repos/Renderable*' -and $_.CommandLine -like '*runClient*' } |
  Select-Object ProcessId, ParentProcessId, CommandLine
```

Exactly one process should match. If none does, or more than one, stop and report without killing anything. Then
list its descendants by `ParentProcessId` (wrapper → Gradle JVM → game) to confirm the chain, and kill that tree only:

```powershell
taskkill /PID <wrapper pid> /T /F
```

A client that MCUpdater launched is never a descendant of this wrapper.

- [ ] **Step 5: Check that every new mixin applied**

```bash
for c in net.minecraft.client.Minecraft net.minecraft.client.renderer.LevelRenderer net.minecraft.client.sounds.SoundEngine net.neoforged.neoforge.client.ClientHooks com.seibel.distanthorizons.core.api.internal.ClientApi; do echo "== $c"; javap -p -cp run/.mixin.out/class "$c" | grep 'renderable\$'; done
```

Expected:
- `Minecraft`: handlers whose names include `timeSound` and `timeAnimateTick`, beside phase 5's;
- `LevelRenderer`: one including `timeTerrain`;
- `SoundEngine`: one including `timeSoundPlay`;
- `ClientHooks`: one including `leaveTerrain`;
- `ClientApi`: ones including `timeRenderLods`, `timeDeferredLods`, `timeFadeOpaque` and `timeFadeTransparent`.

Do not count methods: MixinExtras may add bridge methods. A class with none of its handlers means its mixin did not
apply. Find the reason in `run/logs/latest.log`, which Mixin logs, before going on.

- [ ] **Step 6: Check the report and the log**

```bash
python - <<'EOF'
import glob, json, os
f = max(glob.glob("run/renderable/renderable-*.json"), key=os.path.getmtime)
r = json.load(open(f, encoding="utf-8"))
env, st, fs = r["environment"], r["stats"], r["frames"]
print(f, "schema", r["schema"], "frames", r["session"]["frames"], "distanthorizons", env.get("distanthorizons"))
for k in ("hooksInstalled", "hooksFired", "hooksFailed"):
    print(k, env[k])
b = st["avgBucketsMs"]
print("avg ms: terrain %.3f  lod %.3f  sound %.3f  animate tick %.3f  other tick %.3f  named tick %.3f"
      % (b["terrain"], b["lod"], b["sound"], b["animate tick"], st["avgOtherTickMs"], st["avgNamedMs"]["tick"]))
for k in ("terrain", "lod"):
    print(k, "absent from", sum(1 for x in fs if k not in x["bucketsUs"]), "of", len(fs), "frames")
rows = [(m["key"], e) for m in r["mods"] for e in m["entries"] if e["key"].startswith("sound@")]
print(len(rows), "sound rows")
for mod, e in rows[:15]:
    print("  %-14s %-60s %5d plays %9d us" % (mod, e["label"], e["calls"] + e["tickCalls"], e["renderUs"] + e["tickUs"]))
EOF
f=$(ls -t run/renderable/renderable-*.txt | head -1)
grep -n "hooks installed\|hooks that did not apply\|Time split\|Sound plays" "$f"
grep -n -m3 "^  #[0-9]*  frame" "$f"
grep -in "renderable" run/logs/latest.log | grep -i "mixin\|error\|exception" | head
grep -c "could not check the hooks" run/logs/latest.log
```

Expected:
- `schema 6`, and `distanthorizons 3.3.3`;
- `hooksInstalled` holds `SOUND`, `ANIMATE_TICK`, `TERRAIN`, `LOD` and `SOUND_PLAYS` beside phase 5's hooks, and
  `hooksFailed` is `[]`; the text report says `hooks that did not apply: none` (A1);
- `hooksFired` holds `TERRAIN`, `LOD`, `SOUND` and `ANIMATE_TICK`, and `SOUND_PLAYS` if anything played;
- `terrain` and `lod` are absent from 0 frames (A1);
- at least one sound row, each under its event id's namespace (A3). The dev world has no Create boiler, so expect
  `minecraft` rows (ambient, steps, blocks) and maybe `create` ones; say which events appeared. If no sound played at
  all in 20 s, say so: the orchestrator will ask the user to walk in the world during a rerun;
- the `Time split` line's tick brackets end with `unnamed x` when the tick had unnamed time, after `mods` if any, and
  its render side starts `| terrain a | lod b |` before any other render bucket (A4); hitch lines, if any, read the same;
- a `Sound plays: ... ms a frame, ... plays a second` line right after the overhead lines when there were sound rows;
- no Renderable mixin errors, and the count of "could not check the hooks" lines is 0.

Record the output.

- [ ] **Step 7: A plain run, without Distant Horizons**

Run, in the background:

```bash
JAVA_TOOL_OPTIONS="-Drenderable.autostart=10" ./gradlew runClient -PquickPlay="New World" --no-daemon
```

Without `-PpackMods`, the build empties `run/mods`, so no DH, Sodium, Iris or Create. Wait for the new report as in
Step 3, stop the client as in Step 4 (the wrapper filter matches this run too), then repeat Step 6's commands.
Expected:
- `LOD` is in none of `hooksInstalled`, `hooksFired` and `hooksFailed`, and `hooksFailed` is still `[]`
  (Review focus 1);
- `hooksInstalled` still holds `SOUND`, `ANIMATE_TICK`, `TERRAIN` and `SOUND_PLAYS`;
- `terrain` is absent from 0 frames, `lod` from every frame, and the split has no `lod`;
- the log names neither `ClientApiMixin` nor a Renderable mixin error.

- [ ] **Step 8: The muted run (orchestrator and user)**

The orchestrator asks the user to set `soundCategory_master:0.0` in `run/options.txt`, or Master Volume to 0% in a dev
client's Music & Sounds screen, which the game saves there. Agents may not edit that file. Then repeat Steps 3
(without `-Dmixin.debug.export=true`), 4 and 6. Expected:
- the average `sound` bucket falls to about zero, well below Step 6's figure;
- the Sound plays line, if any sound rows remain, reads close to `0.00 ms a frame`. At zero gain `play` returns before
  it asks for a channel (§P6.2), so plays still count but cost almost nothing (A1).

If Step 6's `sound` was already near zero in a quiet world, report both figures and call the check inconclusive.
Afterwards the orchestrator asks the user to set the master volume back to `1.0`.

- [ ] **Step 9: Put the run directory back**

Delete `run/.mixin.out`. Leave the reports in `run/renderable/`. Confirm with the orchestrator that the user has put
the master volume back.

- [ ] **Step 10: Report**

Report to the orchestrator: the `javap` output from Step 5, and the Python and `grep` output from the three runs.
Include anything unexpected, especially:
- a mixin that did not apply;
- a new hook missing from `hooksInstalled`;
- anything in `hooksFailed`;
- `LOD` listed in the plain run;
- a frame without `terrain` or `lod` in the first run.

Nothing is staged in this task.

---

## Deviations from the spec

1. **DH goes into `packModRuntime`, not `packModJars`** (§P6.8 says `packModJars`). In this `build.gradle`,
   `packModJars` feeds only `extractNestedModJars`, whose output is the compile classpath, and DH has no nested jar to
   extract. `packModRuntime` is what `installPackMods` copies into `run/mods` under `-PpackMods`. So DH runs in the dev
   client and stays off the compile classpath, as §P6.8 intends. Task 7 applies this to §P6.8.
2. **More documentation than §P6.11's 6a rows.** Each of these would otherwise be stale:
   - §3.4's `ModKey` row;
   - §4's list of pinned jars, and §4.5's gate bullet, which also gains Accelerated Rendering (gated since phase 4 but
     never listed there);
   - §6.1's item 6;
   - §6.2's list of key forms;
   - a 6a row and a phase 6 paragraph in §11.
3. **Task 8 adds a plain run** (§P6.10 names A1, A3 and A4 only), which shows the DH gate with DH absent. Its mute check
   needs the user, because no agent may edit `run/options.txt`. A3's dev-client part, `create:steam` from a running
   Create boiler, is left to 6b's in-pack acceptance: the dev world has no boiler, so Task 8 checks only that sound
   rows appear under their event ids' namespaces.
4. **`terrain` steps out for NeoForge's stage event** (§P6.3 says `terrain` is the chunk-layer draws `renderLevel`
   makes). Vanilla's `renderSectionLayer` (offset 555) and Sodium's `@Overwrite` of it (through
   `NeoForgeLevelRenderHooks.runChunkLayerEvents`, offset 11) both end with
   `ClientHooks.dispatchRenderStage(RenderType, ...)`, which posts the layer's `RenderLevelStageEvent`. Inside
   `terrain`, those handlers would stop being named time, and whatever they draw would count as terrain.
   - So a required `@WrapMethod` on that method (`ClientHooksMixin`, mapped to `Hook.TERRAIN`) closes the open terrain
     call while it runs and opens it again after. It works through `Probe.pauseBucket`/`resumeBucket` and
     `Session.innermostBucket()` (Task 5, Step 7a).
   - Each layer then costs one more timed pair. The calibration already prices it.
   - Task 7 applies this to §P6.3 and to the base spec's §4.7.

**Decisions the spec left open:**

1. **An instance that names no event or no category is not timed** (Review focus 5). There is no key to give it, and
   its time stays in the bucket it ran in.
2. **The Sound plays rate goes through `SoundPlays.perSecond(double)`** (as executed): whole from 9.95 a second up,
   so 9.96 reads "10" rather than "10.0"; one decimal below; and "under 0.1" for a rate above zero that would
   read "0.0". So a quiet session never reads "0 plays a second" on a line printed only when something played. The
   spec's example, `180 plays a second`, is unchanged. Task 4's code above shows the first draft, whole from ten.
3. **"With `kind` `sound`" (§P6.7) is read as the key's kind word**, as in `sound@create:create:steam (block)`. No
   `kind` field is added, because "nothing else changes shape".
4. **`FrameStats.avgUnnamedTickNanos()` is `max(0, average other tick − average named tick)`**, not an average of each
   frame's clamped figure. The two differ only where a frame clamps, which a well-formed stack never does.
5. **DH's selectors carry their descriptor** (`renderLods()V`), unlike Flywheel's bare names. A DH update that gives a
   method arguments then reads "did not apply" (Review focus 4) rather than leaving Mixin to fit a handler to a
   signature it no longer matches.
6. **A `lod` call inside another `lod` call counts once.** DH 3.3.3 never nests them; the test pins it.
7. **`LOD` sits after `TERRAIN` in `Hook`**, so the lists read `SOUND, ANIMATE_TICK, TERRAIN, LOD, SOUND_PLAYS`, as A1
   lists them.
8. **The sound key lookup lives in its own class, `hooks.SoundKeys`**, which names no Minecraft type, so its null
   handling and caching are unit-tested. `Probe.beginSound` keeps only the session, thread and null-instance checks.

## Interfaces for plan 6b

Plan 6b may rely on these exactly. Plan 6a ends at **294** tests as executed, the baseline 6b counts from.

- **Buckets.** `core.Bucket.TERRAIN` and `Bucket.LOD` (render, `isRender()` true), and `Bucket.SOUND` and
  `Bucket.ANIMATE_TICK` (tick). Labels `terrain`, `lod`, `sound` and `animate tick`. Enum order: `TERRAIN, LOD,
  BLOCK_ENTITY, ENTITY, PARTICLE_RENDER, FLYWHEEL, CHUNK_UPLOAD, LIGHT, HUD, PARTICLE_TICK, BE_TICK, ENTITY_TICK, SOUND,
  ANIMATE_TICK`.
- **`lod` ms/f:**
  - `Facts.bucket(Bucket.LOD)` (a `double` in ms; `Facts.of` already fills every bucket);
  - or `FrameStats.avgBucket(Bucket.LOD)` (a `long` in ns);
  - the average frame is `Facts.frameMs()` or `FrameStats.avgNanos()`;
  - per frame, `FrameRecord.bucket(Bucket.LOD)`.
- **Hooks.** `core.Hook.SOUND`, `ANIMATE_TICK`, `TERRAIN`, `LOD` and `SOUND_PLAYS`. `Environment.state(Hook.LOD)` gives
  `INSTALLED`, `FAILED` or `NOT_LOADED`. `env.value("distanthorizons")` is DH's version, or `Environment.ABSENT`.
- **Sound keys.**
  - `core.ModKey.Kind.SOUND`, whose word is `sound`.
  - `public static ModKey ModKey.sound(String eventId, String category)`: `modId` is the namespace of `eventId`
    (`minecraft` when it has none), and `name` is `eventId + " (" + category + ")"`. `category()` is `MOD`, and
    `typeId()` is `modId`.
  - `label()` and `memberLabel()` both read `sound create:steam (block)`; `serialize()` reads
    `sound@create:create:steam (block)`.
  - `public String ModKey.soundEvent()` → `create:steam`.
  - `public String ModKey.soundCategory()` → `block`, one of the `SoundSource.getName()` values `master`, `music`,
    `record`, `weather`, `block`, `hostile`, `neutral`, `player`, `ambient`, `voice`.
  - Both return `""` for any other kind.
  - A key is a sound row when `key instanceof ModKey k && k.kind() == ModKey.Kind.SOUND`.
  - A row's plays are `RankedEntry.calls() + RankedEntry.tickCalls()`.
- **The session-level summary** behind the "Sound plays" line:
  - `public record SoundPlays(List<RankedEntry> rows, long nanos, long plays, int frames, double seconds)`;
  - `public static SoundPlays of(SessionResult r)`; `rows()` is every sound row, the costliest first
    (`Ranking.byKey` order: total time descending, ties by label);
  - `boolean any()`, `double msPerFrame()`, `double playsPerSecond()`, `double playsPerSecond(RankedEntry row)`,
    `String summary()` (`1.60 ms a frame, 200 plays a second`) and `String line()` (`Sound plays: ` + summary);
  - `public static String SoundPlays.perSecond(double rate)`, which `summary()` uses: whole from 9.95 up, one decimal
    below, and "under 0.1" for a rate above zero that would read "0.0".
- **What the advice rules can compute from these:**
  - "the sound rows sum to ≥ 0.5 ms/f": `SoundPlays.of(r).msPerFrame() >= 0.5`. `Facts` has no sound field yet:
    6b adds one in `Facts.of`, which has the `SessionResult`, and to the test `FactsBuilder`;
  - "the costliest row, its category and plays per second": `sp.rows().get(0)`,
    `((ModKey) sp.rows().get(0).key()).soundCategory()` (and `soundEvent()`), and `sp.playsPerSecond(sp.rows().get(0))`;
  - "one sound row's largest time in a hitch": the hitch's top contributors, `Facts.Hitch.top()` as
    `Facts.Top(ProfileKey key, double ms, boolean gc)`, or `FrameRecord.top()` as
    `TopEntry(ProfileKey key, long nanos, boolean gc)`, filtered to sound keys. Only a frame's top
    `topContributorsPerFrame` keys (default 5) are kept, so a sound row outside them is not visible in that hitch.
    `RankedEntry.maxFrameNanos()` is a row's largest clean frame anywhere in the session.
- **The tick's unnamed time.**
  - `public long FrameRecord.unnamedTickNanos()` is one frame's `max(0, other tick − named tick)` in ns, the same
    figure as `Facts.Hitch.unnamedTickMs()`, which `Facts.hitch` now reads from it.
  - `public long FrameStats.avgUnnamedTickNanos()` is the average other tick less the average named tick, clamped at
    zero (decision 4), not an average of each frame's figure.
  - P5.B2's unnamed time for `unattributed-stall` (other tick, other render and tasks, less named time, less the GC
    pause) is still `Facts.Hitch`'s `unnamedTickMs`, `unnamedRenderMs` and `untimedTasksMs` with `gcMs`.
- **The rule engine.** `hitch-repeat` already skips `Category.MOD` keys, and `Facts.of`'s kind switch ignores `SOUND`,
  so sound rows reach no existing rule.
- **Test fixtures.**
  - `TestSessions.phase6()`: two 20 ms frames, no hitches. Terrain 2, lod 2, other render 4, tasks 2 (1 named),
    tick 4 (be tick 1, sound 1.5, animate tick 0.5, named 0.2, unnamed 0.8), swap 6. Sound rows `STEAM` (4 plays,
    2 ms), `ANVIL` (2, 0.8 ms) and `CLICK` (2, 0.4 ms): 1.60 ms a frame, 200 plays a second.
  - Its keys are `TestSessions.FLUID_TANK`, `STEAM`, `ANVIL`, `CLICK` and `SOUND_PACKET`.
  - `TestSessions.phase6Buckets()`: one frame with terrain 3, lod 2, sound 1.5 and animate tick 0.5, no plays.

## After this plan

Leave `phase-6a-measurement` unmerged. Plan 6b branches from it (§P6.10), and both merge to `main` after 6b's in-pack
acceptance. That acceptance checks what this plan could not check in the dev client:
- `create:steam (block)`, or whatever event Create's boiler uses, under `create` at the other player's base;
- `lod` named, against the pack's own DH;
- `unnamed` under a quarter of the tick in most hitches at the 16:08 spot;
- nothing named before reading differently at the user's base, apart from the new parts.

Carried to 6b: the Hitches tab cuts each hitch's split at its column width, and the longer tick brackets push
`terrain` and `lod` towards the cut; the text report prints every hitch line in full. 6b's acceptance should look at
the Hitches tab at the other player's base and decide whether the split needs a second line or a tooltip.
