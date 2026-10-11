# Renderable Phase 6b Implementation Plan: advice and release

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn what plan 6a measures into advice, and release it. Players learn when playing sounds keeps the game
waiting, and which Music & Sounds slider stops it, and when Distant Horizons' far terrain is costly, and which of its
settings to lower. Pack authors hear about time Renderable cannot name when it recurs in hitches, not only when it is
one long stall. Then the README, version 0.6.0, the in-pack acceptance, and the merge and tag.

**Architecture:**

- **`Facts` carries the sound rows.** `Facts.SoundCost` is one Mods tab sound row reduced to what a rule says about it
  (its mod, event, category, ms a frame and plays a second), and `Facts.soundMs()` is their sum, the Sound plays
  line's figure. `Facts.of` fills them from 6a's `SoundPlays`.
- **Two player rules in `SettingsRules`:** `distant-horizons` reads the `lod` bucket, and `sound-wait` reads the sound
  rows and the hitches' top contributors.
- **The mod rules' stutter test becomes `KeyStutter`**, a package-private record shared by the `mod-*` rules and
  `sound-wait`. It is PackRules' private `Stutter` and `stutter()` moved, taking a predicate over `ModKey` in place
  of a mod id and kind.
- **`unattributed-stall` gains a repeat form** in `PackRules`.
- **`Words`** gains the Music & Sounds slider table.
- **Documents and release:** the README, the P5 addendum's P5.B2 and P5.4, the base spec's §11, the phase 6 addendum's
  P6.9, version 0.6.0, the in-pack acceptance and the tag.

Everything in `src/` that this plan touches is in `core.advice` or its tests: no Minecraft import, no hook, nothing on a
hot path. Advice is derived once, when a session ends.

**Tech Stack:** Java 21, JUnit 5, NeoForge 21.1.220 (the build only), Python 3 (reading the acceptance reports).

**Spec:** `docs/superpowers/specs/2026-10-10-renderable-phase-6-design.md` §P6.9 (all of it), the 6b items of §P6.10
(the README, version 0.6.0, the in-pack acceptance and `docs/acceptance/phase-6.md`) and the 6b rows of §P6.11 (P5.B2
and P5.4 in `docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md`). Read the phase 6 file on disk: its
§P6.3 carries an edit that adds `SOUND_PLAYS`, and plan 6a's Task 7 amends its §P6.8.

**Repository:** `H:\repos\Renderable`. Create the branch `phase-6b-advice` from `phase-6a-measurement` once plan 6a is
done (all of its tasks, its Task 8 dev-client run included). This plan itself is committed on `main` with plan 6a,
before `phase-6a-measurement` is branched; if that branch already exists, the orchestrator commits this plan as the
first commit on `phase-6b-advice` instead, so `main` does not move and Task 8's fast-forward still works. Commits are
YubiKey-signed: the orchestrator commits after telling the user to touch the key. **Subagents stage and report; they
do not commit.** Tasks 7 and 8 are the orchestrator's, with the user.

**Ordering:** after plan 6a, on its branch. Run the tasks in order:
- Task 1 gives the rules their facts.
- Tasks 2 and 3 each add a rule to `SettingsRules` and a constant to `Thresholds`; Task 3's find strings include
  Task 2's lines.
- Task 4 edits the `PackRules` that Task 3 rewrites, and its fixture test needs Tasks 1 to 3.
- Task 5 documents the rules as Tasks 2 to 4 word them.
- Task 6 makes the 0.6.0 jar that Task 7 installs in the pack.
- Task 8 merges and tags after the acceptance.

**Verified facts you can rely on** (read on 2026-10-10):

- **The Music & Sounds sliders.** Minecraft 1.21.1's `assets/minecraft/lang/en_us.json`, in
  `build/moddev/artifacts/neoforge-21.1.220-client-extra-aka-minecraft-resources.jar`:

  | `SoundSource.getName()` | Key | English caption |
  | --- | --- | --- |
  | `master` | `soundCategory.master` | Master Volume |
  | `music` | `soundCategory.music` | Music |
  | `record` | `soundCategory.record` | Jukebox/Note Blocks |
  | `weather` | `soundCategory.weather` | Weather |
  | `block` | `soundCategory.block` | Blocks |
  | `hostile` | `soundCategory.hostile` | Hostile Creatures |
  | `neutral` | `soundCategory.neutral` | Friendly Creatures |
  | `player` | `soundCategory.player` | Players |
  | `ambient` | `soundCategory.ambient` | Ambient/Environment |
  | `voice` | `soundCategory.voice` | Voice/Speech |

  The Options screen's button is `options.sounds`, "Music & Sounds...", and the screen's title `options.sounds.title`,
  "Music & Sound Options". The spec's draft table is right on every name.
- **How a slider gets its caption** (javap, `neoforge-21.1.220-merged.jar`). `Options`' constructor builds one slider
  per `SoundSource.values()` with `createSoundSliderOptionInstance("soundCategory." + source.getName(), source)`. The
  string concatenation's recipe constant is `soundCategory.` followed by the argument.
- **`SoundSource`** is a plain final enum of ten constants, with no NeoForge enum-extension interface. So no mod can
  add a category on 1.21.1, and every sound row's category is one of the ten.
- **Zero volume** (javap, `SoundEngine`):
  - `getVolume(SoundSource)` is 1.0 for `MASTER` or null, and otherwise the slider's value;
  - `play` multiplies it in through `calculateVolume` (168);
  - when the result is 0 and `SoundInstance.canStartSilent()` is false, `play` logs at debug level and returns
    (196 to 231), before the listener gain check (347) and before `ChannelAccess.createHandle` (407);
  - the master slider is the listener's gain, which `play` checks at 347, also before asking for a channel.

  In vanilla, only `BeeSoundInstance`, `MinecartSoundInstance` and `RidingMinecartSoundInstance` override
  `canStartSilent`. Those still ask for a channel at 0%.
- **Distant Horizons 3.3.3's settings** (`DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar`, its
  `assets/distanthorizons/lang/en_us.json` and javap of its config classes):
  - `distanthorizons.config.client.qualityPresetSetting` is "Quality Preset", an enum from "1. Minimum" to
    "5. Extreme", default Medium;
  - `distanthorizons.config.client.advanced.graphics.quality.lodChunkRenderDistanceRadius` is "LOD Chunk Render
    Distance Radius", default 256 chunks, range 32 to 4096;
  - `Config$Client`, the first page, holds `quickEnableRendering`, `quickLodChunkRenderDistance` (a
    `ConfigUiLinkedEntry` to `lodChunkRenderDistanceRadius`, which the screen shows as that entry), and
    `qualityPresetSetting`. So both settings are on the first page of DH's settings.
- **How a player opens DH's settings.**
  - DH's `MixinOptionsScreen` injects into `OptionsScreen.init()` and adds a textured button. Its config entry
    `showDhOptionsButtonInMinecraftUi` defaults to true, is file-only, and is described as "Show the config button to
    the left of the fov button".
  - `NeoforgeMain` registers an `IConfigScreenFactory`, so NeoForge's Mods list ("Mods", then "Config",
    `fml.menu.mods.config`) opens the same screen.
  - DH 2.4.5-b, the deployed jar, is the same (read by the plan's reviewer): its `en_us.json` holds both captions;
    its `Config$Client` holds `quickLodChunkRenderDistance`, linked to `lodChunkRenderDistanceRadius`, and
    `qualityPresetSetting`, default Medium; its `MixinOptionsScreen` injects at RETURN of `OptionsScreen.init()`
    behind `showDhOptionsButtonInMinecraftUi`, which the instance's `config/DistantHorizons.toml` sets true; and its
    `NeoforgeMain` registers the `IConfigScreenFactory`. So the action is right for both versions.
- **The advice engine** (`core.advice`, unchanged by plan 6a):
  - `Facts`' canonical constructor is called in exactly two places, `Facts.of` and `FactsBuilder.build`;
  - `Facts.of` sums a mod's members with a switch *statement* over `ModKey.Kind`, which skips `SOUND`, so sound rows
    reach no `ModCost`;
  - `SceneRules.hitchRepeat` skips `ProfileKey.Category.MOD` keys, and every sound key is `MOD`;
  - `PackRules`' private `Stutter` record and `stutter(Facts, String, ModKey.Kind)` hold the stall and repeat test
    that P5.B2 gives the `mod-*` rules, which `sound-wait`'s stutter form repeats for sound rows;
  - `Thresholds.causes(Top, Hitch)` is that repeat test's per-hitch floor: no `[gc]` mark, and at least
    `HITCH_KEY_SHARE` (0.25) of the hitch;
  - `Advisor.advise(Facts)` is package-private, runs `SettingsRules`, `SceneRules`, `MemoryRules` and `PackRules` in
    that order, sorts stably by `RANKING`, and returns nothing when there are no frames.
- **What plan 6a gives this plan** (its "Interfaces for plan 6b", as executed on `phase-6a-measurement`):
  - `Bucket.LOD` and `Bucket.TERRAIN`;
  - `ModKey.Kind.SOUND`, `ModKey.sound(eventId, category)`, `soundEvent()` and `soundCategory()`;
  - `SoundPlays.of(r)`, its `rows()` (the costliest first, ties A to Z by label, as `Ranking.byKey` sorts) and
    `playsPerSecond(RankedEntry)`;
  - `public static String SoundPlays.perSecond(double rate)`, which `summary()` uses: whole from 9.95 up, one decimal
    below, and "under 0.1" for a rate above zero that would read "0.0";
  - `FrameRecord.unnamedTickNanos()`, which `Facts.hitch` now reads (`h.unnamedTickNanos() / 1e6,`);
  - `TestSessions.phase6()` and its keys.
- **`TestSessions.phase6()`'s figures, as the rules see them:**
  - two 20 ms frames in 0.04 s, no hitches;
  - `lod` 2.0 ms a frame, which is exactly a tenth of the frame;
  - `terrain` 2.0;
  - the fluid tank's block entity tick 1.0 ms a frame (its steam play inside it);
  - sound rows `create:steam (block)` 1.0 ms a frame and 100 plays a second, `minecraft:block.anvil.land (block)`
    0.4 and 50, and `minecraft:ui.button.click (master)` 0.2 and 50, which sum to 1.6;
  - `minecraft`'s payloads 1.0 ms a frame: the sound packet, with the anvil's play inside it.
- **Floating point at the thresholds:** `0.10 * 10.0` is exactly `1.0` as a double, but `0.10 * 12.0` is
  `1.2000000000000002`. So the "exactly a tenth" test uses 1.0 ms of a 10.0 ms frame.
- **The `sound` bucket is never unnamed time.** `Facts.of` takes a hitch's `unnamedTickMs` from
  `FrameRecord.unnamedTickNanos()`: other tick (the tick less every tick bucket, `SOUND` among them), less the
  tick's named time.
- **The suite is 294 tests** after plan 6a, as executed on `phase-6a-measurement` (`4af0ad0`): its plan's 289, one
  more `lod` test in `SessionTest`, three `SoundPlays.perSecond` tests in `SoundPlaysTest`, and
  `HookInstallsTest.theThirdPartyConfigIsOptionalSoAMissedTargetOnlyLogs`. This plan's Tasks 1 to 6 were applied
  from their own code blocks to a clone of that branch and run with `./gradlew test --rerun --no-daemon`: every
  step's expected failure and count below came out as written.
- **The pack instance** (from `docs/acceptance/phase-5.md`; no subagent opens it, and the orchestrator only reads it, in
  Task 7):
  - `%APPDATA%\.MCUpdater\instances\Gears-And-Frontiers\`, with `renderable-0.5.0.jar` in `mods/`;
  - it runs Distant Horizons 2.4.5-b, Sodium 0.6.13 and Iris 1.8.12;
  - plan 6a verified the `ClientApi` targets and the terrain call sites against those versions;
  - its `renderable/` folder holds the 0.5.0 reports `renderable-2026-10-10_15-56-32` (the user's base, 2562 97 1152)
    and `renderable-2026-10-10_16-08-48` (the other player's base, -1169 91 -1678).
- **Files are CRLF** in the working tree (`core.autocrlf` is true). The Edit and Write tools, and GNU sed, turn a typed
  `\uXXXX` escape into the literal character; no step here needs one.

## Global Constraints

- `core` and the report writers have no Minecraft imports (`KeysTest.coreHasNoMinecraftImports` and
  `reportWritersHaveNoMinecraftImports`).
- Advice text is fixed English in `core.advice`. The slider captions and Distant Horizons' setting names are literals,
  checked against the jars above, never looked up at run time.
- No rule names a specific mod except `distant-horizons`, which names Distant Horizons by §P6.9 (deviation 2).
  `sound-wait` names sounds and mods only from the measured rows.
- A rule never fires on missing data: with no sound rows, or `lod` at zero, its rule says nothing.
- Every threshold is a constant in `Thresholds`; no rule spells a number.
- `SettingsRules.all` and `PackRules.all` run their rules in their P5.B2 table's order. `Advisor.RANKING` is unchanged.
- Recommendation text is plain: no backticks, no Markdown.
- Run the suite with `./gradlew test --rerun --no-daemon`. Use the Gradle wrapper only.
- Never edit `run/options.txt`, and never write in the user's MCUpdater instance. Only the orchestrator reads its
  `renderable/` and `logs/` folders, in Task 7.
- Commits use conventional prefixes (`feat:`, `test:`, `docs:`, `build:`); subagents stage and report.

## Review Focus

The five input classes or failure modes most likely to bite a user that no task's tests would otherwise exercise, most
likely first. Each has a test in the task that owns its code, marked "Review focus N".

1. **Distant Horizons absent, or its render hook did not apply** (most players have no DH; a DH update can rename a
   render method). `lod` reads 0, and `distant-horizons` says nothing however much `terrain` costs, even when
   `terrain` then holds DH's draw. No other rule reads `terrain`. Test: Task 2,
   `distantHorizonsIsQuietWithoutItsTime`.
2. **A session with no hitches** (a good machine, or a quiet spot). No stutter form can fire: `sound-wait` and the
   `mod-*` rules can give only their frame-rate forms, and `unattributed-stall` gives nothing. Test: Task 4,
   `AdvisorTest.thePhase6FixtureGetsFrameRateAdviceAndNoStutter`.
3. **Sound rows that never reach a hitch's top five** (many cheap plays, each smaller than the frame's usual costs). A
   frame keeps only its five costliest keys, so the row test cannot see them. When they play inside the sound
   engine's tick, the bucket test can; otherwise only the steady sum can fire, at 0.5 ms a frame. Test: Task 3, `soundRowsOutsideEveryHitchsTopListLeaveOnlyTheFrameRateForm`.
4. **`unattributed-stall`'s two forms together, or beside `sound-wait` on the same hitches** (the base the phase was
   written for: frequent hitches, with sounds and unnamed time in the same frames). Each rule gives one finding. When
   both forms pass, `unattributed-stall` names the repeat, whose worst is the stall's. A sound's time is never unnamed,
   and the `sound` bucket is named time, so the two rules are about different parts of each hitch. Tests: Task 4,
   `theRepeatFormSaysTheStallToo`, `AdvisorTest.unnamedTimeAndASoundStallAreSeparateFindings` and
   `AdvisorTest.theSoundBucketIsNamedTimeNotUnnamed`.
5. **Ties between sound rows, and a category with no slider.** Two rows that cost the same name the first by label,
   as the Mods tab sorts them; two equal stalls name the first found, in the worse hitch. A category outside the table
   reads as its own name, so the advice never prints a blank (on 1.21.1 no mod can add one). The stutter form's action
   names the stalled sound's slider, not the costliest row's. Tests: Task 1,
   `soundRowsThatCostTheSameGoByLabelAndNoSoundsIsEmpty`; Task 3, `soundWaitNamesTheRowItIsAboutAndTiesGoToTheFirst`
   and `theSliderTableNamesEveryCategoryAsMusicAndSoundsDoes`.

---

## File map

```
README.md                                                             Modify (Task 5)
gradle.properties                                                     Modify: mod_version 0.6.0 (Task 6)
docs/pack-module.md                                                   Modify: 0.6.0 (Task 6)
docs/acceptance/phase-6.md                                            Create (Task 7)
docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md   Modify: §11 (Task 5)
docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md        Modify: P5.B2, P5.4 (Task 5)
docs/superpowers/specs/2026-10-10-renderable-phase-6-design.md        Modify: P6.9 (Task 5)
src/main/java/io/github/edwardg117/renderable/core/advice/
  Facts.java                                                          Modify: SoundCost, sounds, soundMs, Hitch.soundMs (Task 1)
  Thresholds.java                                                     Modify (Tasks 2, 3, 4)
  SettingsRules.java                                                  Modify: distant-horizons (Task 2), sound-wait (Task 3)
  KeyStutter.java                                                     Create (Task 3)
  Words.java                                                          Modify: the slider table (Task 3)
  PackRules.java                                                      Rewrite (Task 3); Modify: unattributed-stall (Task 4)
src/test/java/io/github/edwardg117/renderable/core/advice/
  FactsBuilder.java                                                   Modify: sound rows, sound hitches (Task 1)
  FactsTest.java                                                      Modify (Task 1)
  SettingsRulesTest.java                                              Modify (Tasks 2, 3)
  PackRulesTest.java                                                  Modify (Tasks 3, 4)
  AdvisorTest.java                                                    Modify (Task 4)
```

**Expected test totals:** 294 at the start, 297 after Task 1, 299 after Task 2, 309 after Task 3 and 315 after Task 4.
Tasks 5 to 8 add none; the overlay fix of §P6.13, made before Task 7, adds 4, so the total at the end is 319. Review
rounds may add tests; a shortfall is worth investigating and an excess is not.

**How to run the suite** (the build cache can restore stale results, so always force it):

```bash
./gradlew test --rerun --no-daemon
```

---

### Task 1: The facts carry the sound rows and each hitch's `sound` bucket

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/advice/Facts.java`
- Modify: `src/test/java/io/github/edwardg117/renderable/core/advice/FactsBuilder.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/advice/FactsTest.java` (append 3)

**Interfaces:**
- Consumes: `SoundPlays.of(SessionResult)`, `SoundPlays.rows()`, `SoundPlays.playsPerSecond(RankedEntry)`;
  `ModKey.modId()`, `soundEvent()`, `soundCategory()` (plan 6a Task 4); `TestSessions.phase6()`.
- Produces:
  - `public record Facts.SoundCost(String modId, String event, String category, double msPerFrame, double playsPerSecond)`;
  - the component `List<SoundCost> sounds` (every sound row, the costliest first), between `mods` and
    `unbalancedFrames`, so the canonical constructor is
    `Facts(Environment, int, double, double[], double, double, double, double, double, List<Cost>, List<Cost>, List<Cost>, List<Hitch>, int, long, long, List<ModCost>, List<SoundCost>, int)`;
  - `public double Facts.soundMs()`, the sum of the rows' `msPerFrame`;
  - `FactsBuilder sound(String event, String category, double ms, double playsPerSecond)`;
  - `Facts.Hitch` gains `double soundMs`, the hitch's `sound` bucket, before `top`. The canonical constructor is
    `Hitch(int, double, double, double, boolean, double, double, double, double, List<Top>)`, and a second
    constructor keeps the nine-argument form with `soundMs` 0;
  - `static Facts.Hitch FactsBuilder.soundHitch(int number, double totalMs, double soundMs, Facts.Top... top)`.

Spec §P6.9's "the sound rows sum" is the Sound plays line's figure, which `SoundPlays` already computes (§P6.5).
`Facts.of` keeps `SoundPlays`' order: total time descending, ties A to Z by label, as the Mods tab sorts. A sound row
stays out of its mod's `ModCost`, so it feeds no `mod-*` rule (§P6.9, "no double finding").

`sound-wait`'s bucket test (§P6.9) reads each hitch's `sound` bucket, so `Facts.Hitch` carries it. Like every tick
bucket it is outside `unnamedTickMs`, which `Facts.of` takes from `FrameRecord.unnamedTickNanos()`: the sound
engine's time is named, and `unattributed-stall` never counts it. A second constructor keeps the nine-argument form, with
`soundMs` 0, so the other rules' tests and the thirty-odd hitches they build stay as they are (decision 11).

- [ ] **Step 1: Write the failing tests**

In `FactsTest.java`, replace (it occurs once):

```java
import io.github.edwardg117.renderable.core.Environment;
```

with:

```java
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.ModKey;
```

Then append to `FactsTest`:

```java
    @Test
    void thePhase6FixtureCarriesItsSoundRowsAndLod() {
        Facts f = Facts.of(TestSessions.phase6(), Environment.empty());
        assertEquals(List.of("create:steam", "minecraft:block.anvil.land", "minecraft:ui.button.click"),
                f.sounds().stream().map(Facts.SoundCost::event).toList(), "every sound row, the costliest first");
        Facts.SoundCost steam = f.sounds().get(0);
        assertEquals("create", steam.modId(), "the event id's namespace");
        assertEquals("block", steam.category());
        assertEquals(1.0, steam.msPerFrame(), 1e-9, "2 ms over the 2 frames");
        assertEquals(100.0, steam.playsPerSecond(), 1e-9, "4 plays in 0.04 s");
        assertEquals("master", f.sounds().get(2).category());
        assertEquals(1.6, f.soundMs(), 1e-9, "the Sound plays line's 1.60 ms a frame");
        assertEquals(2.0, f.bucket(Bucket.LOD), 1e-9);
        assertEquals(2.0, f.bucket(Bucket.TERRAIN), 1e-9, "4 ms less the 2 ms of lod inside it");

        Facts.ModCost create = f.mods().stream().filter(m -> m.modId().equals("create")).findFirst().orElseThrow();
        assertEquals(0, create.handlersMs() + create.layersMs() + create.packetsMs(), 1e-9,
                "a sound row is none of a mod's handlers, layers or payloads, so it feeds no mod-* rule");
        Facts.ModCost minecraft = f.mods().stream().filter(m -> m.modId().equals("minecraft")).findFirst().orElseThrow();
        assertEquals(1.0, minecraft.packetsMs(), 1e-9, "the sound packet, the anvil's play inside it not added again");
    }

    @Test
    void soundRowsThatCostTheSameGoByLabelAndNoSoundsIsEmpty() {
        // Review focus 5: a tie between two sound rows goes A to Z by label, as on the Mods tab, whatever order they
        // played in, so the advice names the same row the tab lists first.
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> now[0], 0);
        Accumulator flute = s.accumulator(ModKey.sound("minecraft:block.note_block.flute", "record"));
        Accumulator steam = s.accumulator(ModKey.sound("create:steam", "block"));
        s.onRenderPre(0, 0);
        s.beginAttribution(flute); now[0] = 1 * MS; s.end();
        s.beginAttribution(steam); now[0] = 2 * MS; s.end();
        now[0] = 10 * MS; s.onRenderPre(0, 0);
        Facts f = Facts.of(s.finish(), Environment.empty());
        assertEquals(List.of("create:steam", "minecraft:block.note_block.flute"),
                f.sounds().stream().map(Facts.SoundCost::event).toList());
        assertEquals(2.0, f.soundMs(), 1e-9);

        Facts none = Facts.of(TestSessions.sample(), Environment.empty());
        assertTrue(none.sounds().isEmpty(), "nothing played");
        assertEquals(0, none.soundMs(), 0);
    }

    @Test
    void aHitchCarriesItsSoundBucket() {
        // Four quiet 10 ms frames, then a 60 ms hitch whose tick is 45 ms, 40 of them in the sound engine's tick.
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> now[0], 0);
        for (int i = 0; i <= 4; i++) {
            now[0] = i * 10 * MS;
            s.onRenderPre(0, 0);
        }
        now[0] = 45 * MS;                    s.onRenderPost();
        s.onTickPre();
        s.begin(null, Bucket.SOUND);         now[0] = 85 * MS; s.end();
        now[0] = 90 * MS;                    s.onTickPost();
        now[0] = 100 * MS;                   s.onRenderPre(0, 0);
        Facts f = Facts.of(s.finish(), Environment.empty());

        assertEquals(1, f.hitches().size());
        Facts.Hitch h = f.hitches().get(0);
        assertEquals(60.0, h.totalMs(), 1e-9);
        assertEquals(40.0, h.soundMs(), 1e-9, "the sound engine's tick in the hitch");
        assertEquals(5.0, h.unnamedTickMs(), 1e-9, "the tick's 45 ms less the 40 in sound: a bucket is never unnamed");
        assertEquals(0, FactsBuilder.hitch(1, 60).soundMs(), 0, "the nine-argument form has no sound bucket");
    }
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*FactsTest'`
Expected: compilation failure, `cannot find symbol: class SoundCost` (and `method sounds()`, `method soundMs()`).

- [ ] **Step 3: Give `Facts` its sound rows, and each hitch its `sound` bucket**

In `Facts.java`, replace (it occurs once):

```java
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.TopEntry;
```

with:

```java
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.SoundPlays;
import io.github.edwardg117.renderable.core.TopEntry;
```

Replace (it occurs once):

```java
 * @param mods               mods, most expensive first
 */
```

with:

```java
 * @param mods               mods, most expensive first
 * @param sounds             the Mods tab's sound rows, most expensive first (spec addendum §P6.5)
 */
```

Replace (it occurs once):

```java
                    List<ModCost> mods,
                    int unbalancedFrames) {
```

with:

```java
                    List<ModCost> mods,
                    List<SoundCost> sounds,
                    int unbalancedFrames) {
```

Replace (it occurs once):

```java
    public record ModCost(String modId, double handlersMs, double layersMs, double packetsMs, String topEvent) {
    }
```

with:

```java
    public record ModCost(String modId, double handlersMs, double layersMs, double packetsMs, String topEvent) {
    }

    /**
     * One sound row of the Mods tab: one sound event played in one category (spec addendum §P6.5). Its time is the
     * whole play, the wait for a channel included. A mod's sound rows are not in its {@link ModCost}.
     *
     * @param modId          the event id's namespace
     * @param event          the event id, such as {@code create:steam}
     * @param category       the category's {@code SoundSource} name, such as {@code block}
     * @param playsPerSecond the row's plays a second over the session
     */
    public record SoundCost(String modId, String event, String category, double msPerFrame, double playsPerSecond) {
    }
```

Replace the `Hitch` record, with the end of its javadoc (it occurs once):

```java
     * @param untimedTasksMs     tasks, less what the Mods tab names in them
     */
    public record Hitch(int number, double totalMs, double gcMs, double chunkUploadMs, boolean chunkUploadLargest,
                        double unnamedTickMs, double unnamedRenderMs, double untimedTasksMs, List<Top> top) {
    }
```

with:

```java
     * @param untimedTasksMs     tasks, less what the Mods tab names in them
     * @param soundMs            the {@code sound} bucket: the sound engine's tick, with the plays it ran (spec addendum
     *                           §P6.9). Like every tick bucket, it is not in {@code unnamedTickMs}
     */
    public record Hitch(int number, double totalMs, double gcMs, double chunkUploadMs, boolean chunkUploadLargest,
                        double unnamedTickMs, double unnamedRenderMs, double untimedTasksMs, double soundMs,
                        List<Top> top) {

        /** A hitch with nothing in the {@code sound} bucket: every rule but {@code sound-wait} reads it as before. */
        public Hitch(int number, double totalMs, double gcMs, double chunkUploadMs, boolean chunkUploadLargest,
                     double unnamedTickMs, double unnamedRenderMs, double untimedTasksMs, List<Top> top) {
            this(number, totalMs, gcMs, chunkUploadMs, chunkUploadLargest, unnamedTickMs, unnamedRenderMs,
                    untimedTasksMs, 0, top);
        }
    }
```

Replace (it occurs once):

```java
    public double bucket(Bucket b) {
        return bucketMs[b.ordinal()];
    }
```

with:

```java
    public double bucket(Bucket b) {
        return bucketMs[b.ordinal()];
    }

    /** Every sound row's time, milliseconds a frame: the Sound plays line's figure (spec addendum §P6.5). */
    public double soundMs() {
        double ms = 0;
        for (SoundCost s : sounds) ms += s.msPerFrame();
        return ms;
    }
```

Then replace the end of `Facts.of` (it occurs once):

```java
            mods.add(new ModCost(m.typeId(), perFrame(handlers, frames), perFrame(layers, frames),
                    perFrame(packets, frames), topEvent));
        }

        return new Facts(env, frames, st.avgNanos() / 1e6, bucketMs, st.avgTasksNanos() / 1e6,
                st.avgCapNanos() / 1e6, st.avgSwapNanos() / 1e6, perFrame(shadow, frames),
                perFrame(shadowable, frames), List.copyOf(blockEntities), List.copyOf(entities),
                List.copyOf(particles), List.copyOf(hitches), st.gcCount(), st.gcMillis(), maxFrameGc,
                List.copyOf(mods), r.unbalancedFrames());
    }
```

with:

```java
            mods.add(new ModCost(m.typeId(), perFrame(handlers, frames), perFrame(layers, frames),
                    perFrame(packets, frames), topEvent));
        }

        // The kind switch above skips sound rows: they are sound-wait's alone (spec addendum §P6.9).
        SoundPlays plays = SoundPlays.of(r);
        List<SoundCost> sounds = new ArrayList<>();
        for (RankedEntry e : plays.rows()) {
            ModKey k = (ModKey) e.key();
            sounds.add(new SoundCost(k.modId(), k.soundEvent(), k.soundCategory(), perFrame(e.totalNanos(), frames),
                    plays.playsPerSecond(e)));
        }

        return new Facts(env, frames, st.avgNanos() / 1e6, bucketMs, st.avgTasksNanos() / 1e6,
                st.avgCapNanos() / 1e6, st.avgSwapNanos() / 1e6, perFrame(shadow, frames),
                perFrame(shadowable, frames), List.copyOf(blockEntities), List.copyOf(entities),
                List.copyOf(particles), List.copyOf(hitches), st.gcCount(), st.gcMillis(), maxFrameGc,
                List.copyOf(mods), List.copyOf(sounds), r.unbalancedFrames());
    }
```

In `hitch`, replace (it occurs once):

```java
                Math.max(0, h.tasksNanos() - h.named(Window.TASKS)) / 1e6,
                List.copyOf(top));
```

with:

```java
                Math.max(0, h.tasksNanos() - h.named(Window.TASKS)) / 1e6,
                h.bucket(Bucket.SOUND) / 1e6, List.copyOf(top));
```

- [ ] **Step 4: Let `FactsBuilder` add sound rows and sound hitches**

In `FactsBuilder.java`, replace (it occurs once):

```java
import io.github.edwardg117.renderable.core.Environment;
```

with:

```java
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.ModKey;
```

Replace (it occurs once):

```java
    private final List<Facts.ModCost> mods = new ArrayList<>();
```

with:

```java
    private final List<Facts.ModCost> mods = new ArrayList<>();
    private final List<Facts.SoundCost> sounds = new ArrayList<>();
```

Replace (it occurs once):

```java
    FactsBuilder mod(String id, double handlers, double layers, double packets, String topEvent) {
        mods.add(new Facts.ModCost(id, handlers, layers, packets, topEvent));
        return this;
    }
```

with:

```java
    FactsBuilder mod(String id, double handlers, double layers, double packets, String topEvent) {
        mods.add(new Facts.ModCost(id, handlers, layers, packets, topEvent));
        return this;
    }

    /** A sound row, its mod the event id's namespace. Add them costliest first, as {@link Facts#of} lists them. */
    FactsBuilder sound(String event, String category, double ms, double playsPerSecond) {
        sounds.add(new Facts.SoundCost(ModKey.sound(event, category).modId(), event, category, ms, playsPerSecond));
        return this;
    }
```

Replace (it occurs once):

```java
                List.copyOf(hitches), gcCount, gcTotalMs, maxFrameGcMs, List.copyOf(mods), unbalanced);
```

with:

```java
                List.copyOf(hitches), gcCount, gcTotalMs, maxFrameGcMs, List.copyOf(mods), List.copyOf(sounds),
                unbalanced);
```

Replace (it occurs once):

```java
    static Facts.Hitch hitch(int number, double totalMs, Facts.Top... top) {
        return new Facts.Hitch(number, totalMs, 0, 0, false, 0, 0, 0, List.of(top));
    }
```

with:

```java
    static Facts.Hitch hitch(int number, double totalMs, Facts.Top... top) {
        return new Facts.Hitch(number, totalMs, 0, 0, false, 0, 0, 0, List.of(top));
    }

    /** A hitch whose sound engine took {@code soundMs}, with no GC pause and nothing unnamed (spec addendum §P6.9). */
    static Facts.Hitch soundHitch(int number, double totalMs, double soundMs, Facts.Top... top) {
        return new Facts.Hitch(number, totalMs, 0, 0, false, 0, 0, 0, soundMs, List.of(top));
    }
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **297** tests, 0 failures.

- [ ] **Step 6: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/advice/Facts.java src/test/java/io/github/edwardg117/renderable/core/advice/FactsBuilder.java src/test/java/io/github/edwardg117/renderable/core/advice/FactsTest.java
```

Report to the orchestrator for the commit: `feat(advice): the facts carry the sound rows and each hitch's sound bucket`.

---

### Task 2: `distant-horizons`

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/advice/Thresholds.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/advice/SettingsRules.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/advice/SettingsRulesTest.java` (append 2)

**Interfaces:**
- Consumes: `Bucket.LOD` (plan 6a Task 1); `Facts.bucket(Bucket)`, `Facts.frameMs()`.
- Produces: `static void SettingsRules.distantHorizons(Facts, List<Recommendation>)`, rule `distant-horizons`, run after
  `chunkRebuilds` in `SettingsRules.all`; `Thresholds.LOD_MS = 1.0` and `Thresholds.LOD_SHARE = 0.10`.

Spec §P6.9: player, frame rate, `lod` ≥ 1.0 ms a frame and ≥ 10% of the average frame; at stake, `lod`. The action names
the two settings by their own captions in DH 3.3.3, and the button that opens them (verified facts). `lod` is time
Renderable measures in DH's own render methods, so without DH, or with that hook listed under "did not apply", it is 0
and the rule is silent. It is the one rule that names a mod (deviation 2).

- [ ] **Step 1: Write the failing tests**

Append to `SettingsRulesTest`:

```java
    @Test
    void distantHorizonsFiresAtAMillisecondAndATenthOfTheFrame() {
        Recommendation a = run(new FactsBuilder().frameMs(10.1).bucket(Bucket.LOD, 3.2).build(),
                SettingsRules::distantHorizons).get(0);
        assertEquals("distant-horizons", a.rule());
        assertEquals(Recommendation.Kind.FRAME_RATE, a.kind());
        assertEquals(Recommendation.Audience.PLAYER, a.audience());
        assertEquals("Distant Horizons' far terrain costs a lot to draw", a.title());
        assertEquals("Distant Horizons draws its far terrain in 3.2 ms of each 10.1 ms frame.", a.finding());
        assertEquals("Lower Quality Preset or LOD Chunk Render Distance Radius in Distant Horizons' settings, which its"
                + " button beside FOV in Options opens.", a.action());
        assertEquals(3.2, a.msPerFrame(), 1e-9);
        assertEquals("3.2 ms/frame", a.atStake());

        assertEquals("Distant Horizons draws its far terrain in 1.0 ms of each 10.0 ms frame.",
                run(new FactsBuilder().frameMs(10).bucket(Bucket.LOD, 1.0).build(), SettingsRules::distantHorizons)
                        .get(0).finding(), "exactly a millisecond, and exactly a tenth of the frame");
        assertTrue(run(new FactsBuilder().frameMs(9).bucket(Bucket.LOD, 0.99).build(),
                SettingsRules::distantHorizons).isEmpty(), "just under a millisecond, though 11% of the frame");
        assertTrue(run(new FactsBuilder().frameMs(20).bucket(Bucket.LOD, 1.99).build(),
                SettingsRules::distantHorizons).isEmpty(), "just under a tenth of the frame");
    }

    @Test
    void distantHorizonsIsQuietWithoutItsTime() {
        // Review focus 1. Without Distant Horizons, or with its render hook under "did not apply", lod reads 0 however
        // much terrain costs: DH's head injection then draws inside terrain, unnamed. No rule speaks for terrain.
        Facts withoutLod = new FactsBuilder().frameMs(10).bucket(Bucket.TERRAIN, 6).build();
        assertTrue(run(withoutLod, SettingsRules::distantHorizons).isEmpty());
        assertTrue(Advisor.advise(withoutLod).isEmpty(), "no other rule reads terrain");
        assertTrue(run(new FactsBuilder().frameMs(0).build(), SettingsRules::distantHorizons).isEmpty(), "no frames");
    }
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*SettingsRulesTest'`
Expected: compilation failure, `invalid method reference: cannot find symbol distantHorizons`.

- [ ] **Step 3: Add the thresholds**

In `Thresholds.java`, replace (it occurs once):

```java
    static final double MOD_MS = 0.5;
```

with:

```java
    static final double MOD_MS = 0.5;
    static final double LOD_MS = 1.0;
    static final double LOD_SHARE = 0.10;
```

- [ ] **Step 4: Add the rule**

In `SettingsRules.java`, replace (it occurs once):

```java
        chunkRebuilds(f, out);
        frameCap(f, out);
```

with:

```java
        chunkRebuilds(f, out);
        distantHorizons(f, out);
        frameCap(f, out);
```

Then replace the end of `chunkRebuilds` (it occurs once):

```java
        out.add(new Recommendation("chunk-rebuilds", stutter ? Kind.STUTTER : Kind.FRAME_RATE, Audience.PLAYER,
                "Chunk rebuilds", finding, String.join(" ", actions), upload, stutter ? worst : 0));
    }
```

with:

```java
        out.add(new Recommendation("chunk-rebuilds", stutter ? Kind.STUTTER : Kind.FRAME_RATE, Audience.PLAYER,
                "Chunk rebuilds", finding, String.join(" ", actions), upload, stutter ? worst : 0));
    }

    /**
     * Distant Horizons' LOD drawing (spec addendum §P6.9), the one rule that names a mod. {@code lod} is time
     * Renderable measures in DH's own render methods, so it is 0 without DH or when that hook did not apply. The
     * action names DH 3.3.3's settings by their captions; both are on the first page of its settings.
     */
    static void distantHorizons(Facts f, List<Recommendation> out) {
        double lod = f.bucket(Bucket.LOD);
        if (lod < Thresholds.LOD_MS || lod < Thresholds.LOD_SHARE * f.frameMs()) return;
        out.add(new Recommendation("distant-horizons", Kind.FRAME_RATE, Audience.PLAYER,
                "Distant Horizons' far terrain costs a lot to draw",
                String.format(Locale.ROOT, "Distant Horizons draws its far terrain in %.1f ms of each %.1f ms frame.",
                        lod, f.frameMs()),
                "Lower Quality Preset or LOD Chunk Render Distance Radius in Distant Horizons' settings, which its"
                        + " button beside FOV in Options opens.",
                lod, 0));
    }
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **299** tests, 0 failures. No existing fixture has `lod`, so no other expectation moves.

- [ ] **Step 6: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/advice/Thresholds.java src/main/java/io/github/edwardg117/renderable/core/advice/SettingsRules.java src/test/java/io/github/edwardg117/renderable/core/advice/SettingsRulesTest.java
```

Report to the orchestrator for the commit: `feat(advice): distant-horizons names Distant Horizons' LOD settings`.

---

### Task 3: `sound-wait`

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/advice/KeyStutter.java`
- Rewrite: `src/main/java/io/github/edwardg117/renderable/core/advice/PackRules.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/advice/Words.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/advice/Thresholds.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/advice/SettingsRules.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/advice/SettingsRulesTest.java` (imports; append 9)
- Test: `src/test/java/io/github/edwardg117/renderable/core/advice/PackRulesTest.java` (append 1)

**Interfaces:**
- Consumes: `Facts.sounds()`, `Facts.soundMs()`, `Facts.SoundCost`, `Facts.Hitch.soundMs()` and
  `FactsBuilder.soundHitch` (Task 1); `ModKey.Kind.SOUND`, `ModKey.soundEvent()`,
  `ModKey.soundCategory()` (plan 6a Task 4); `Thresholds.causes`, `Thresholds.isMod`; `SoundPlays.perSecond(double)`
  (plan 6a, as executed).
- Produces:
  - `record KeyStutter(ModKey key, double worstMs, int hitchNumber, int count)` (package-private), with
    `String finding(String subject, int hitches)` and `static KeyStutter find(Facts f, Predicate<ModKey> which)`;
  - `static String Words.slider(String category)` and `static String Words.volume(String category)`;
  - `Thresholds.SOUND_MS = 0.5` and `Thresholds.SOUND_BUCKET_SHARE = 0.25`;
  - `static void SettingsRules.soundWait(Facts, List<Recommendation>)`, rule `sound-wait`, run after
    `distantHorizons` in `SettingsRules.all`.

Spec §P6.9: player. Frame rate when the sound rows sum to at least 0.5 ms a frame. Stutter by either of two tests:
one sound row took at least 50 ms and at least half of a hitch, or passes `hitch-repeat`'s test (the row test); or
the `sound` bucket is at least a quarter of the hitch in 3 or more hitches and in at least a quarter of all hitches
(the bucket test). At stake are the sound play time and, in a stutter form, the row's or the bucket's largest time
in one of the hitches the finding is about.

The row test is the `mod-*` rules' shape, so their stall and repeat test moves out of `PackRules` into `KeyStutter`
unchanged, taking a predicate where it took a mod id and a kind. `mod-network`, `mod-handlers` and `mod-hud` keep
their behaviour, and their tests do not change. `sound-wait` asks the same test for every sound key, whatever its mod.

The bucket test catches a stall spread over many plays, none a quarter of a hitch alone: queued sounds play inside
`SoundManager.tick`, so the bucket shows the whole wait. It counts a hitch as `hitch-repeat` and
`unattributed-stall`'s repeat form do, once the frame's GC pause is taken off the bucket, as `unattributed-stall`
takes it off its parts (decision 8). The rule gives one finding: the row test's when it passes, because it names the
sound and its slider; otherwise the bucket test's; otherwise the frame-rate form's (decision 9). A stutter form
outranks the frame-rate form, and `msPerFrame` stays the steady sound time, as in the mod rules.

The finding names a sound by its event and its slider: `create:steam (Blocks)`. The action says to set a slider to
0% (deviation 7): the stalled or repeated sound's in the row test (deviation 4), the costliest row's otherwise, and
"the slider for the sounds playing here" when the bucket test fires with no sound row (decision 10). That works
because at a volume of exactly 0, `play` returns before asking for a channel (verified facts). The pack-author
sentence is left out for a vanilla sound, whose namespace no pack author can report to (deviation 5). Plays a second
go through plan 6a's `SoundPlays.perSecond`, as on the Sound plays line: whole from 9.95 up, one decimal below, and
"under 0.1" for a rate above zero that would read 0.0. `Words` has no rate formatter of its own, so the two cannot
drift apart, and `SoundPlaysTest` covers the rule.

- [ ] **Step 1: Write the failing tests**

In `SettingsRulesTest.java`, replace the imports (they occur once):

```java
import io.github.edwardg117.renderable.core.Bucket;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
```

with:

```java
import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.ModKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static io.github.edwardg117.renderable.core.advice.FactsBuilder.hitch;
import static io.github.edwardg117.renderable.core.advice.FactsBuilder.soundHitch;
import static io.github.edwardg117.renderable.core.advice.FactsBuilder.top;
import static org.junit.jupiter.api.Assertions.*;
```

Then append to `SettingsRulesTest`:

```java
    static final String MUTE = "Set the Blocks volume to 0% in Music & Sounds: the game then skips those sounds instead"
            + " of waiting for them.";
    static final String REPORT = " A pack author can report the sound's mod with this file.";

    @Test
    void theSliderTableNamesEveryCategoryAsMusicAndSoundsDoes() {
        // Review focus 5. The captions of Minecraft 1.21.1's soundCategory.* keys in en_us.json, by SoundSource name.
        assertEquals(List.of("Master Volume", "Music", "Jukebox/Note Blocks", "Weather", "Blocks", "Hostile Creatures",
                        "Friendly Creatures", "Players", "Ambient/Environment", "Voice/Speech"),
                Stream.of("master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient",
                        "voice").map(Words::slider).toList());
        assertEquals("somemod_engines", Words.slider("somemod_engines"), "a category with no slider reads as it is");
        assertEquals("the Blocks volume", Words.volume("block"));
        assertEquals("the Master Volume", Words.volume("master"), "its caption already says Volume");
        assertEquals("the somemod_engines volume", Words.volume("somemod_engines"));
    }

    @Test
    void soundWaitFiresAtHalfAMillisecondAFrameAndNamesTheCostliestRow() {
        Facts f = new FactsBuilder()
                .sound("create:steam", "block", 1.1, 85.2)
                .sound("minecraft:entity.cow.ambient", "neutral", 0.3, 4)
                .build();
        Recommendation a = run(f, SettingsRules::soundWait).get(0);
        assertEquals("sound-wait", a.rule());
        assertEquals(Recommendation.Kind.FRAME_RATE, a.kind());
        assertEquals(Recommendation.Audience.PLAYER, a.audience());
        assertEquals("Sounds keep the game waiting", a.title());
        assertEquals("Playing sounds kept the game waiting 1.4 ms a frame; create:steam (Blocks) played 85 times a second.",
                a.finding());
        assertEquals(MUTE + REPORT, a.action());
        assertEquals(1.4, a.msPerFrame(), 1e-9, "every sound row, not only the costliest");
        assertEquals(0, a.worstMs(), 1e-9);

        Recommendation exactly = run(new FactsBuilder().sound("minecraft:weather.rain", "weather", 0.5, 3.5).build(),
                SettingsRules::soundWait).get(0);
        assertEquals("Playing sounds kept the game waiting 0.5 ms a frame; minecraft:weather.rain (Weather) played 3.5"
                + " times a second.", exactly.finding(), "exactly the threshold");
        assertEquals("Set the Weather volume to 0% in Music & Sounds: the game then skips those sounds instead of"
                + " waiting for them.", exactly.action(), "a vanilla sound has no mod to report it to");
        assertTrue(run(new FactsBuilder().sound("create:steam", "block", 0.3, 40).sound("create:whistle", "block", 0.19, 2)
                .build(), SettingsRules::soundWait).isEmpty(), "0.49 ms a frame");
        assertTrue(run(new FactsBuilder().build(), SettingsRules::soundWait).isEmpty(), "nothing played");
    }

    @Test
    void aSoundStallsAtFiftyMillisecondsAndHalfTheHitch() {
        ModKey steam = ModKey.sound("create:steam", "block");
        Recommendation a = run(new FactsBuilder().sound("create:steam", "block", 0.2, 40)
                .hitch(hitch(1, 100, top(steam, 50))).build(), SettingsRules::soundWait).get(0);
        assertEquals(Recommendation.Kind.STUTTER, a.kind());
        assertEquals("Playing create:steam (Blocks) took 50 ms in hitch #1.", a.finding(),
                "exactly both, whatever the steady cost");
        assertEquals(MUTE + REPORT, a.action());
        assertEquals(50, a.worstMs(), 1e-9);
        assertEquals(0.2, a.msPerFrame(), 1e-9, "the steady cost, whatever the form");
        assertEquals("up to 50 ms", a.atStake());

        assertTrue(run(new FactsBuilder().hitch(hitch(1, 200, top(steam, 60))).build(), SettingsRules::soundWait).isEmpty(),
                "under half the hitch");
        assertTrue(run(new FactsBuilder().hitch(hitch(1, 60, top(steam, 49))).build(), SettingsRules::soundWait).isEmpty(),
                "under 50 ms");
        assertTrue(run(new FactsBuilder().hitch(hitch(1, 100, new Facts.Top(steam, 60, true))).build(),
                SettingsRules::soundWait).isEmpty(), "a GC pause could account for it");
        assertTrue(run(new FactsBuilder().hitch(hitch(1, 100, top(ModKey.packet("create", "create:sync"), 60))).build(),
                SettingsRules::soundWait).isEmpty(), "a payload is not a sound");

        Recommendation costly = run(new FactsBuilder().sound("create:steam", "block", 0.7, 40)
                .hitch(hitch(1, 200, top(steam, 120))).hitch(hitch(2, 150, top(steam, 90))).build(),
                SettingsRules::soundWait).get(0);
        assertEquals("Playing create:steam (Blocks) took 120 ms in hitch #1. Playing sounds kept the game waiting 0.7 ms"
                + " a frame.", costly.finding(), "the largest stall, then the steady cost when it reaches the threshold");
        assertEquals(120, costly.worstMs(), 1e-9);
    }

    @Test
    void aSoundRepeatsByHitchRepeatsTest() {
        ModKey steam = ModKey.sound("create:steam", "block");
        ModKey anvil = ModKey.sound("minecraft:block.anvil.land", "block");
        FactsBuilder quarter = new FactsBuilder().sound("create:steam", "block", 0.1, 40);
        for (int i = 1; i <= 12; i++) quarter.hitch(i <= 3 ? hitch(i, 60, top(steam, 15)) : hitch(i, 60));
        Recommendation a = run(quarter.build(), SettingsRules::soundWait).get(0);
        assertEquals(Recommendation.Kind.STUTTER, a.kind());
        assertEquals("Playing create:steam (Blocks) was a top cost in 3 of 12 hitches, up to 15 ms.", a.finding(),
                "15 of 60 ms is exactly a quarter of each hitch, and 3 of 12 exactly a quarter of them");
        assertEquals(15, a.worstMs(), 1e-9);

        FactsBuilder under = new FactsBuilder();
        for (int i = 1; i <= 4; i++) under.hitch(hitch(i, 60, top(steam, 14.9)));
        assertTrue(run(under.build(), SettingsRules::soundWait).isEmpty(), "just under a quarter of each hitch");
        FactsBuilder thirteen = new FactsBuilder();
        for (int i = 1; i <= 13; i++) thirteen.hitch(i <= 3 ? hitch(i, 60, top(steam, 30)) : hitch(i, 60));
        assertTrue(run(thirteen.build(), SettingsRules::soundWait).isEmpty(), "3 of 13 is under a quarter of them");
        FactsBuilder two = new FactsBuilder();
        for (int i = 1; i <= 2; i++) two.hitch(hitch(i, 60, top(steam, 30)));
        assertTrue(run(two.build(), SettingsRules::soundWait).isEmpty(), "two hitches are not a repeat");

        FactsBuilder worse = new FactsBuilder();
        for (int i = 1; i <= 3; i++) worse.hitch(hitch(i, 200, top(anvil, 95 - 5 * i)));   // 45% to 40%: not stalls
        worse.hitch(hitch(4, 100, top(steam, 55)));
        Recommendation repeat = run(worse.build(), SettingsRules::soundWait).get(0);
        assertEquals("Playing minecraft:block.anvil.land (Blocks) was a top cost in 3 of 4 hitches, up to 90 ms.",
                repeat.finding(), "a repeat at least as bad as the stall is named instead");
        assertEquals(MUTE, repeat.action(), "a vanilla sound: no mod to report it to");
    }

    @Test
    void soundWaitNamesTheRowItIsAboutAndTiesGoToTheFirst() {
        // Review focus 5. The frame-rate form names the first row in the facts' order, which is the Mods tab's, so of
        // two that cost the same it names the first by label. The stutter form names the stalled sound, and the action
        // its slider: muting the costliest row's category would leave the stall alone.
        Facts tie = new FactsBuilder().sound("create:steam", "block", 0.4, 40).sound("create:whistle", "ambient", 0.4, 2)
                .build();
        assertTrue(run(tie, SettingsRules::soundWait).get(0).finding().endsWith("; create:steam (Blocks) played 40 times a"
                + " second."));

        ModKey hiss = ModKey.sound("create:hiss", "ambient");
        Recommendation stalled = run(new FactsBuilder().sound("create:steam", "block", 0.9, 40)
                .sound("create:hiss", "ambient", 0.1, 1).hitch(hitch(1, 120, top(hiss, 70))).build(),
                SettingsRules::soundWait).get(0);
        assertEquals("Playing create:hiss (Ambient/Environment) took 70 ms in hitch #1. Playing sounds kept the game"
                + " waiting 1.0 ms a frame.", stalled.finding());
        assertEquals("Set the Ambient/Environment volume to 0% in Music & Sounds: the game then skips those sounds"
                + " instead of waiting for them." + REPORT, stalled.action(), "the stalled sound's slider, not the costliest row's");

        ModKey bell = ModKey.sound("minecraft:block.bell.use", "block");
        assertEquals("Playing create:hiss (Ambient/Environment) took 70 ms in hitch #1.",
                run(new FactsBuilder().hitch(hitch(1, 120, top(hiss, 70))).hitch(hitch(2, 110, top(bell, 70))).build(),
                        SettingsRules::soundWait).get(0).finding(),
                "two equal stalls: the first found, in the worse hitch, which the report numbers first");
    }

    @Test
    void soundRowsOutsideEveryHitchsTopListLeaveOnlyTheFrameRateForm() {
        // Review focus 3. A frame keeps only its five costliest keys, so many cheap plays may never be named in a
        // hitch, and the row test cannot see them. Here they are not in the sound engine's tick either, so the bucket
        // test has nothing: only the steady sum can fire, and only from half a millisecond a frame.
        BlockEntityKey tank = new BlockEntityKey("create:fluid_tank", "minecraft:overworld", 1, 64, 1);
        FactsBuilder busy = new FactsBuilder().sound("create:steam", "block", 0.6, 120);
        for (int i = 1; i <= 8; i++) busy.hitch(hitch(i, 40, top(tank, 20)));
        Recommendation a = run(busy.build(), SettingsRules::soundWait).get(0);
        assertEquals(Recommendation.Kind.FRAME_RATE, a.kind());
        assertEquals("Playing sounds kept the game waiting 0.6 ms a frame; create:steam (Blocks) played 120 times a second.",
                a.finding());

        FactsBuilder quiet = new FactsBuilder().sound("create:steam", "block", 0.4, 120);
        for (int i = 1; i <= 8; i++) quiet.hitch(hitch(i, 40, top(tank, 20)));
        assertTrue(run(quiet.build(), SettingsRules::soundWait).isEmpty());
    }

    @Test
    void theSoundBucketStuttersAtAQuarterOfTheHitchInThreeHitchesAndAQuarterOfThem() {
        // The bucket test: a stall spread over many plays, none of them a quarter of a hitch alone.
        FactsBuilder quarter = new FactsBuilder().sound("create:steam", "block", 0.2, 40);
        for (int i = 1; i <= 12; i++) quarter.hitch(soundHitch(i, 60, i <= 3 ? 15 : 0));
        Recommendation a = run(quarter.build(), SettingsRules::soundWait).get(0);
        assertEquals(Recommendation.Kind.STUTTER, a.kind());
        assertEquals("In 3 of 12 hitches, the sound engine took at least a quarter of the frame (up to 15 ms). Playing"
                + " sounds kept the game waiting 0.2 ms a frame; create:steam (Blocks) played 40 times a second.",
                a.finding(), "15 of 60 ms is exactly a quarter of each hitch, and 3 of 12 exactly a quarter of them");
        assertEquals(MUTE + REPORT, a.action(), "the costliest row's slider");
        assertEquals(15, a.worstMs(), 1e-9, "the bucket's largest time in one of those hitches");
        assertEquals(0.2, a.msPerFrame(), 1e-9, "the sound play time, whatever the form");

        FactsBuilder under = new FactsBuilder();
        for (int i = 1; i <= 4; i++) under.hitch(soundHitch(i, 60, 14.9));
        assertTrue(run(under.build(), SettingsRules::soundWait).isEmpty(), "just under a quarter of each hitch");
        FactsBuilder thirteen = new FactsBuilder();
        for (int i = 1; i <= 13; i++) thirteen.hitch(soundHitch(i, 60, i <= 3 ? 30 : 0));
        assertTrue(run(thirteen.build(), SettingsRules::soundWait).isEmpty(), "3 of 13 is just under a quarter of them");
        FactsBuilder two = new FactsBuilder();
        for (int i = 1; i <= 2; i++) two.hitch(soundHitch(i, 60, 30));
        assertTrue(run(two.build(), SettingsRules::soundWait).isEmpty(), "two hitches are not a repeat");
        FactsBuilder paused = new FactsBuilder();
        for (int i = 1; i <= 4; i++) paused.hitch(new Facts.Hitch(i, 60, 6, 0, false, 0, 0, 0, 20, List.of()));
        assertTrue(run(paused.build(), SettingsRules::soundWait).isEmpty(),
                "the frame's 6 ms GC pause comes off the bucket's 20, leaving 14 of 60");
    }

    @Test
    void theSoundBucketWithNoSoundRowNamesNoSlider() {
        // No sound row was timed (the play hook under "did not apply"), or the engine's time went on sounds already
        // playing: the finding has no row to name, and the action no slider and no mod.
        FactsBuilder b = new FactsBuilder();
        for (int i = 1; i <= 4; i++) b.hitch(soundHitch(i, 60, 20));
        Recommendation a = run(b.build(), SettingsRules::soundWait).get(0);
        assertEquals("In 4 of 4 hitches, the sound engine took at least a quarter of the frame (up to 20 ms).", a.finding());
        assertEquals("Set the slider for the sounds playing here to 0% in Music & Sounds: the game then skips those sounds"
                + " instead of waiting for them.", a.action());
        assertEquals(20, a.worstMs(), 1e-9);
        assertEquals(0, a.msPerFrame(), 1e-9, "no sound row, no sound play time");
    }

    @Test
    void aSoundRowsStutterComesBeforeTheBucketTest() {
        // One finding: the row test names the sound and the slider that stops it, so it wins when it passes, and the
        // bucket test speaks only when it does not. Either stutter form outranks the frame-rate form, as in the mod rules.
        ModKey steam = ModKey.sound("create:steam", "block");
        FactsBuilder both = new FactsBuilder().sound("create:steam", "block", 0.7, 40);
        for (int i = 1; i <= 4; i++) both.hitch(soundHitch(i, 100, 70, top(steam, 60)));
        List<Recommendation> r = run(both.build(), SettingsRules::soundWait);
        assertEquals(1, r.size());
        assertEquals("Playing create:steam (Blocks) was a top cost in 4 of 4 hitches, up to 60 ms. Playing sounds kept the"
                + " game waiting 0.7 ms a frame.", r.get(0).finding());
        assertEquals(60, r.get(0).worstMs(), 1e-9, "the row's worst, not the bucket's 70");

        ModKey hiss = ModKey.sound("create:hiss", "ambient");
        FactsBuilder spread = new FactsBuilder().sound("create:steam", "block", 0.7, 40).sound("create:hiss", "ambient", 0.1, 3);
        for (int i = 1; i <= 4; i++) spread.hitch(soundHitch(i, 100, 40, top(hiss, 20)));
        Recommendation a = run(spread.build(), SettingsRules::soundWait).get(0);
        assertEquals(Recommendation.Kind.STUTTER, a.kind(), "the bucket test outranks the steady 0.8 ms a frame");
        assertEquals("In 4 of 4 hitches, the sound engine took at least a quarter of the frame (up to 40 ms). Playing"
                + " sounds kept the game waiting 0.8 ms a frame; create:steam (Blocks) played 40 times a second.",
                a.finding(), "no row reaches a quarter of a hitch, so the bucket speaks, naming the costliest row");
        assertEquals(MUTE + REPORT, a.action(), "the costliest row's slider: the bucket names no sound of its own");
        assertEquals(40, a.worstMs(), 1e-9);
    }
```

Append to `PackRulesTest`:

```java
    @Test
    void soundRowsFeedNoModRuleAndNoHitchRepeat() {
        // Spec addendum §P6.9, "no double finding": a sound that stutters is sound-wait's alone.
        ModKey steam = ModKey.sound("create:steam", "block");
        FactsBuilder b = new FactsBuilder().mod("create", 0, 0, 0, "").sound("create:steam", "block", 0.8, 85);
        for (int i = 1; i <= 4; i++) b.hitch(hitch(i, 100, top(steam, 60)));
        Facts f = b.build();
        assertTrue(run(f, PackRules::modHandlers).isEmpty());
        assertTrue(run(f, PackRules::modHud).isEmpty());
        assertTrue(run(f, PackRules::modNetwork).isEmpty());
        assertTrue(run(f, SceneRules::hitchRepeat).isEmpty(), "a Mods tab key");
        assertEquals(List.of("sound-wait"), Advisor.advise(f).stream().map(Recommendation::rule).toList());
    }
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*SettingsRulesTest' --tests '*PackRulesTest'`
Expected: compilation failure: `invalid method reference` for `Words::slider` and `SettingsRules::soundWait`, and
`cannot find symbol` for `Words.volume`.

- [ ] **Step 3: Create `KeyStutter`**

Create `src/main/java/io/github/edwardg117/renderable/core/advice/KeyStutter.java`. It is `PackRules`' `Stutter` record
and `stutter()` method, moved, with a predicate in place of the mod id and kind:

```java
package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.ModKey;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A Mods tab key that caused stutter: the stutter forms of the {@code mod-*} rules (spec addendum §P5.B2) and of
 * {@code sound-wait} (§P6.9).
 *
 * @param worstMs     its largest time in one of the hitches the finding is about
 * @param hitchNumber the hitch it stalled, or 0 when it is a repeat
 * @param count       the hitches it caused: 1 for a stall
 */
record KeyStutter(ModKey key, double worstMs, int hitchNumber, int count) {

    /** "{subject} took 370 ms in hitch #1." or "{subject} was a top cost in 4 of 4 hitches, up to 24 ms." */
    String finding(String subject, int hitches) {
        return hitchNumber > 0
                ? String.format(Locale.ROOT, "%s took %.0f ms in hitch #%d.", subject, worstMs, hitchNumber)
                : String.format(Locale.ROOT, "%s was a top cost in %d of %d hitches, up to %.0f ms.",
                        subject, count, hitches, worstMs);
    }

    /**
     * The stutter one of the keys {@code which} accepts caused, or null. A stall: the key took at least
     * {@link Thresholds#STALL_MS} and {@link Thresholds#STALL_SHARE} of one hitch, and the largest wins. A repeat:
     * the key caused hitches by {@code hitch-repeat}'s test, and the one in the most wins, the first found on a
     * tie. The stall comes first unless the repeat's worst reached it. A contributor with the {@code [gc]} mark
     * counts toward neither. These forms fire whatever the steady cost, because a stall averages to little over a
     * session, the Mods tab names this time so {@code unattributed-stall} skips it, and {@code hitch-repeat} leaves
     * Mods tab keys out (plan 5b, deviations 2, 3 and 6).
     */
    static KeyStutter find(Facts f, Predicate<ModKey> which) {
        KeyStutter stall = null;
        LinkedHashMap<ModKey, Integer> repeats = new LinkedHashMap<>();
        LinkedHashMap<ModKey, Double> repeatWorst = new LinkedHashMap<>();
        for (Facts.Hitch h : f.hitches()) {
            Set<ModKey> seen = new HashSet<>();
            for (Facts.Top t : h.top()) {
                if (!(t.key() instanceof ModKey k) || !which.test(k) || t.gc()) continue;
                if (t.ms() >= Thresholds.STALL_MS && t.ms() >= Thresholds.STALL_SHARE * h.totalMs()
                        && (stall == null || t.ms() > stall.worstMs())) {
                    stall = new KeyStutter(k, t.ms(), h.number(), 1);
                }
                if (Thresholds.causes(t, h) && seen.add(k)) {
                    repeats.merge(k, 1, Integer::sum);
                    repeatWorst.merge(k, t.ms(), Math::max);
                }
            }
        }
        KeyStutter repeat = null;
        for (Map.Entry<ModKey, Integer> e : repeats.entrySet()) {
            int n = e.getValue();
            if (n >= Thresholds.REPEATED_HITCHES && n >= Thresholds.HITCH_REPEAT_SHARE * f.hitches().size()
                    && (repeat == null || n > repeat.count())) {
                repeat = new KeyStutter(e.getKey(), repeatWorst.get(e.getKey()), 0, n);
            }
        }
        // A repeat as bad as the stall says more: how often, as well as how much.
        return stall == null || repeat != null && repeat.worstMs() >= stall.worstMs() ? repeat : stall;
    }
}
```

- [ ] **Step 4: Rewrite `PackRules` to use it**

Replace the whole of `PackRules.java` with the following. Only the stutter test moves: `unattributedStall` and
`stackResets` are as they were, and each `mod-*` rule asks `KeyStutter.find` for its mod's keys of its kind.

```java
package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.advice.Recommendation.Audience;
import io.github.edwardg117.renderable.core.advice.Recommendation.Kind;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Pointers for the pack author (spec addendum §P5.B2): one method per rule, in the table's order. */
final class PackRules {

    private PackRules() {
    }

    static void all(Facts f, List<Recommendation> out) {
        unattributedStall(f, out);
        modHandlers(f, out);
        modHud(f, out);
        modNetwork(f, out);
        stackResets(f, out);
    }

    /** A hitch whose time is mostly in code nothing names: other tick, other render or tasks, less named time. */
    static void unattributedStall(Facts f, List<Recommendation> out) {
        Facts.Hitch worstHitch = null;
        String worstWhere = null;
        double worst = 0;
        double sum = 0;
        for (Facts.Hitch h : f.hitches()) {
            double[] parts = {h.unnamedTickMs(), h.unnamedRenderMs(), h.untimedTasksMs()};
            String[] where = {"client tick", "rendering", "main-thread tasks"};
            for (int i = 0; i < parts.length; i++) {
                // The frame's GC pause landed in one of the parts; which one is not known, so each loses it.
                double v = Math.max(0, parts[i] - h.gcMs());
                if (v < Thresholds.STALL_MS || v < Thresholds.STALL_SHARE * h.totalMs()) continue;
                sum += v;
                if (v > worst) {
                    worst = v;
                    worstHitch = h;
                    worstWhere = where[i];
                }
            }
        }
        if (worstHitch == null) return;
        out.add(new Recommendation("unattributed-stall", Kind.STUTTER, Audience.PACK_AUTHOR, "A stall Renderable cannot name",
                String.format(Locale.ROOT, "Hitch #%d (%.0f ms): %.0f ms of %s is in code Renderable does not time.",
                        worstHitch.number(), worstHitch.totalMs(), worst, worstWhere),
                "Profile the same place with a sampling profiler such as spark, which names the code.",
                sum / f.frames(), worst));
    }

    /** A mod's event handlers: their steady cost, or one handler's stutter (see {@link KeyStutter#find}). */
    static void modHandlers(Facts f, List<Recommendation> out) {
        for (Facts.ModCost m : f.mods()) {
            if (!Thresholds.isMod(m.modId())) continue;
            KeyStutter s = KeyStutter.find(f, ofMod(m.modId(), ModKey.Kind.EVENT));
            boolean costly = m.handlersMs() >= Thresholds.MOD_MS;
            if (!costly && s == null) continue;
            String finding = s == null
                    ? String.format(Locale.ROOT, "%s's event handlers take %.1f ms a frame, most of it in %s.",
                            m.modId(), m.handlersMs(), m.topEvent())
                    : s.finding(m.modId() + "'s " + ModKey.simpleEventName(s.key().name()) + " handler", f.hitches().size())
                            + (costly ? String.format(Locale.ROOT, " %s's event handlers take %.1f ms a frame.", m.modId(),
                                    m.handlersMs()) : "");
            out.add(new Recommendation("mod-handlers", s == null ? Kind.FRAME_RATE : Kind.STUTTER, Audience.PACK_AUTHOR,
                    m.modId() + "'s event handlers", finding,
                    "Check the mod's config for the feature behind that event, or report it to the mod with this file.",
                    m.handlersMs(), s == null ? 0 : s.worstMs()));
        }
    }

    /** A mod's HUD layers: their steady cost, or one layer's stutter (see {@link KeyStutter#find}). */
    static void modHud(Facts f, List<Recommendation> out) {
        for (Facts.ModCost m : f.mods()) {
            if (!Thresholds.isMod(m.modId())) continue;
            KeyStutter s = KeyStutter.find(f, ofMod(m.modId(), ModKey.Kind.LAYER));
            boolean costly = m.layersMs() >= Thresholds.MOD_MS;
            if (!costly && s == null) continue;
            String steady = String.format(Locale.ROOT, "%s's HUD layers take %.1f ms a frame.", m.modId(), m.layersMs());
            String finding = s == null ? steady
                    : s.finding("The " + s.key().name() + " layer", f.hitches().size()) + (costly ? " " + steady : "");
            out.add(new Recommendation("mod-hud", s == null ? Kind.FRAME_RATE : Kind.STUTTER, Audience.PACK_AUTHOR,
                    m.modId() + "'s HUD", finding, "Check the mod's HUD settings, or report it to the mod with this file.",
                    m.layersMs(), s == null ? 0 : s.worstMs()));
        }
    }

    /** A mod's payloads: their steady cost, or one payload type's stutter (see {@link KeyStutter#find}). */
    static void modNetwork(Facts f, List<Recommendation> out) {
        for (Facts.ModCost m : f.mods()) {
            if (!Thresholds.isMod(m.modId())) continue;
            KeyStutter s = KeyStutter.find(f, ofMod(m.modId(), ModKey.Kind.PACKET));
            boolean costly = m.packetsMs() >= Thresholds.MOD_MS;
            if (!costly && s == null) continue;
            String steady = String.format(Locale.ROOT, "%s's payloads take %.1f ms a frame on the client.", m.modId(), m.packetsMs());
            String finding = s == null ? steady
                    : s.finding("The " + s.key().name() + " payload", f.hitches().size()) + (costly ? " " + steady : "");
            out.add(new Recommendation("mod-network", s == null ? Kind.FRAME_RATE : Kind.STUTTER, Audience.PACK_AUTHOR,
                    m.modId() + "'s network payloads", finding,
                    "The server is sending work the client handles on its main thread; report it to the mod with this file.",
                    m.packetsMs(), s == null ? 0 : s.worstMs()));
        }
    }

    static void stackResets(Facts f, List<Recommendation> out) {
        if (f.unbalancedFrames() <= 0) return;
        out.add(new Recommendation("stack-resets", Kind.NOTE, Audience.PACK_AUTHOR, "Unbalanced timing",
                String.format(Locale.ROOT, "%s had unbalanced timing: a timed call did not return normally.",
                        Words.count(f.unbalancedFrames(), "frame", "frames")),
                "The game log from around the session should hold the exception; report it with the log.", 0, 0));
    }

    /** A mod's keys of one kind: the keys its stutter form looks at. */
    private static Predicate<ModKey> ofMod(String modId, ModKey.Kind kind) {
        return k -> k.kind() == kind && k.modId().equals(modId);
    }
}
```

- [ ] **Step 5: Add the slider table to `Words`**

In `Words.java`, replace (it occurs once):

```java
    /** "1 block" or "212 blocks". */
    static String count(int n, String one, String many) {
        return Fmt.count(n) + " " + (n == 1 ? one : many);
    }
```

with:

```java
    /** "1 block" or "212 blocks". */
    static String count(int n, String one, String many) {
        return Fmt.count(n) + " " + (n == 1 ? one : many);
    }

    /**
     * A sound category's slider in Music & Sounds, by its {@code SoundSource} name: the English caption of
     * {@code soundCategory.<name>} in Minecraft 1.21.1's {@code en_us.json} (spec addendum §P6.9). A category with no
     * slider here reads as it is.
     */
    static String slider(String category) {
        return switch (category) {
            case "master" -> "Master Volume";
            case "music" -> "Music";
            case "record" -> "Jukebox/Note Blocks";
            case "weather" -> "Weather";
            case "block" -> "Blocks";
            case "hostile" -> "Hostile Creatures";
            case "neutral" -> "Friendly Creatures";
            case "player" -> "Players";
            case "ambient" -> "Ambient/Environment";
            case "voice" -> "Voice/Speech";
            default -> category;
        };
    }

    /** "the Blocks volume", or "the Master Volume", whose caption already says so. */
    static String volume(String category) {
        String slider = slider(category);
        return slider.endsWith(" Volume") ? "the " + slider : "the " + slider + " volume";
    }
```

- [ ] **Step 6: Add the threshold**

In `Thresholds.java`, replace (it occurs once):

```java
    static final double LOD_SHARE = 0.10;
```

with:

```java
    static final double LOD_SHARE = 0.10;
    static final double SOUND_MS = 0.5;
    /** The share of a hitch the {@code sound} bucket must reach to count toward {@code sound-wait}'s bucket test. */
    static final double SOUND_BUCKET_SHARE = 0.25;
```

- [ ] **Step 7: Add the rule**

In `SettingsRules.java`, replace (it occurs once):

```java
import io.github.edwardg117.renderable.core.Bucket;
```

with:

```java
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.SoundPlays;
```

Replace (it occurs once):

```java
        distantHorizons(f, out);
        frameCap(f, out);
```

with:

```java
        distantHorizons(f, out);
        soundWait(f, out);
        frameCap(f, out);
```

Then replace the end of `distantHorizons` (it occurs once):

```java
                "Lower Quality Preset or LOD Chunk Render Distance Radius in Distant Horizons' settings, which its"
                        + " button beside FOV in Options opens.",
                lod, 0));
    }
```

with:

```java
                "Lower Quality Preset or LOD Chunk Render Distance Radius in Distant Horizons' settings, which its"
                        + " button beside FOV in Options opens.",
                lod, 0));
    }

    /**
     * Sound plays (spec addendum §P6.9): their steady cost, or stutter. Two stutter tests, one finding: one sound row by
     * the mod rules' test ({@link KeyStutter#find}), which names the sound and wins when it passes; otherwise the
     * {@code sound} bucket in repeated hitches, which catches a stall spread over many plays, since queued sounds play
     * inside the sound engine's tick. A play waits for the sound thread to hand it a channel, and returns before asking
     * for one (§P6.2), but only at a volume of exactly 0. So the action says to set a slider to 0%: the stalled or
     * repeated sound's in the row test, the costliest row's otherwise, and none when no sound row was timed. A vanilla
     * sound has no mod to report it to.
     */
    static void soundWait(Facts f, List<Recommendation> out) {
        double ms = f.soundMs();
        boolean costly = ms >= Thresholds.SOUND_MS;
        KeyStutter s = KeyStutter.find(f, k -> k.kind() == ModKey.Kind.SOUND);
        int inBucket = 0;
        double bucketWorst = 0;
        for (Facts.Hitch h : f.hitches()) {
            // The frame's GC pause may have landed in the sound engine's tick: the bucket loses it, as
            // unattributed-stall's parts do.
            double v = Math.max(0, h.soundMs() - h.gcMs());
            if (v < Thresholds.SOUND_BUCKET_SHARE * h.totalMs()) continue;
            inBucket++;
            bucketWorst = Math.max(bucketWorst, v);
        }
        boolean bucket = inBucket >= Thresholds.REPEATED_HITCHES
                && inBucket >= Thresholds.HITCH_REPEAT_SHARE * f.hitches().size();
        if (!costly && s == null && !bucket) return;

        Facts.SoundCost top = f.sounds().isEmpty() ? null : f.sounds().get(0);
        String steady = String.format(Locale.ROOT, "Playing sounds kept the game waiting %.1f ms a frame", ms);
        String costliest = top == null ? "" : steady + "; " + top.event() + " (" + Words.slider(top.category())
                + ") played " + SoundPlays.perSecond(top.playsPerSecond()) + " times a second.";
        String modId = top == null ? null : top.modId();
        String category = top == null ? null : top.category();
        String finding;
        double worst = 0;
        if (s != null) {
            modId = s.key().modId();
            category = s.key().soundCategory();
            finding = s.finding("Playing " + s.key().soundEvent() + " (" + Words.slider(category) + ")",
                    f.hitches().size()) + (costly ? " " + steady + "." : "");
            worst = s.worstMs();
        } else if (bucket) {
            finding = String.format(Locale.ROOT, "In %d of %d hitches, the sound engine took at least a quarter of the"
                    + " frame (up to %.0f ms).", inBucket, f.hitches().size(), bucketWorst) + (top == null ? "" : " " + costliest);
            worst = bucketWorst;
        } else {
            finding = costliest;
        }
        String slider = category == null ? "the slider for the sounds playing here" : Words.volume(category);
        out.add(new Recommendation("sound-wait", s != null || bucket ? Kind.STUTTER : Kind.FRAME_RATE, Audience.PLAYER,
                "Sounds keep the game waiting", finding,
                "Set " + slider + " to 0% in Music & Sounds: the game then skips those sounds instead of waiting for them."
                        + (modId != null && Thresholds.isMod(modId)
                                ? " A pack author can report the sound's mod with this file." : ""),
                ms, worst));
    }
```

- [ ] **Step 8: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **309** tests, 0 failures. Every existing `PackRulesTest` test passes unchanged, which shows
the stutter test moved without changing.

- [ ] **Step 9: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/advice/KeyStutter.java src/main/java/io/github/edwardg117/renderable/core/advice/PackRules.java src/main/java/io/github/edwardg117/renderable/core/advice/Words.java src/main/java/io/github/edwardg117/renderable/core/advice/Thresholds.java src/main/java/io/github/edwardg117/renderable/core/advice/SettingsRules.java src/test/java/io/github/edwardg117/renderable/core/advice/SettingsRulesTest.java src/test/java/io/github/edwardg117/renderable/core/advice/PackRulesTest.java
```

Report to the orchestrator for the commit: `feat(advice): sound-wait names the Music & Sounds slider that stops the wait`.

---

### Task 4: `unattributed-stall`'s repeat form

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/advice/Thresholds.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/advice/PackRules.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/advice/PackRulesTest.java` (append 3)
- Test: `src/test/java/io/github/edwardg117/renderable/core/advice/AdvisorTest.java` (imports; append 3)

**Interfaces:**
- Consumes: `Facts.Hitch.unnamedTickMs()`, `unnamedRenderMs()`, `untimedTasksMs()`, `gcMs()`; Tasks 1 to 3 for the
  fixture test.
- Produces: `PackRules.unattributedStall(Facts, List<Recommendation>)` with a repeat form; `Thresholds.UNNAMED_SHARE =
  0.25`.

Spec §P6.9. The repeat form fires when the unnamed time, as P5.B2 computes it, is at least a quarter of the hitch in 3
or more hitches and in at least a quarter of all hitches. P5.B2 computes three parts per hitch: other tick, other render
and tasks, each less the time the Mods tab names in it and less the frame's GC pause. The stall form tests each part on
its own, and so does the repeat form. A hitch counts once however many of its parts pass, and the finding names the
window of the largest part (decision 1). At stake, as for every stutter finding (plan 5b, deviation 5): `msPerFrame` is
the qualifying time summed over the session, and `worstMs` is its largest in one hitch.

A stall's part is at least half of its hitch, so it is also at least a quarter: when the repeat form passes, its worst
is at least the stall's. So, as with the mod rules, the repeat is named when it passes and says both (decision 2). The
stall form is unchanged, and so are its tests.

- [ ] **Step 1: Write the failing tests**

Append to `PackRulesTest`:

```java
    @Test
    void unnamedTimeRepeatsAtAQuarterOfTheHitchInThreeHitchesAndAQuarterOfThem() {
        FactsBuilder quarter = new FactsBuilder();
        for (int i = 1; i <= 12; i++) quarter.hitch(new Facts.Hitch(i, 40, 0, 0, false, i <= 3 ? 10 : 2, 0, 0, List.of()));
        Recommendation a = run(quarter.build(), PackRules::unattributedStall).get(0);
        assertEquals("unattributed-stall", a.rule());
        assertEquals(Recommendation.Kind.STUTTER, a.kind());
        assertEquals(Recommendation.Audience.PACK_AUTHOR, a.audience());
        assertEquals("Stutter Renderable cannot name", a.title());
        assertEquals("In 3 of 12 hitches, at least a quarter of the frame was in code Renderable does not time, up to"
                + " 10 ms of client tick.", a.finding(), "10 of 40 ms is exactly a quarter, in exactly a quarter of them");
        assertEquals("Profile the same place with a sampling profiler such as spark, which names the code.", a.action());
        assertEquals(10, a.worstMs(), 1e-9);
        assertEquals(0.03, a.msPerFrame(), 1e-9, "three hitches' 10 ms over the 1,000 frames");

        FactsBuilder under = new FactsBuilder();
        for (int i = 1; i <= 4; i++) under.hitch(new Facts.Hitch(i, 40, 0, 0, false, 9.9, 0, 0, List.of()));
        assertTrue(run(under.build(), PackRules::unattributedStall).isEmpty(), "just under a quarter of each hitch");
        FactsBuilder thirteen = new FactsBuilder();
        for (int i = 1; i <= 13; i++) thirteen.hitch(new Facts.Hitch(i, 40, 0, 0, false, i <= 3 ? 10 : 2, 0, 0, List.of()));
        assertTrue(run(thirteen.build(), PackRules::unattributedStall).isEmpty(), "3 of 13 is under a quarter of them");
        FactsBuilder two = new FactsBuilder();
        for (int i = 1; i <= 2; i++) two.hitch(new Facts.Hitch(i, 40, 0, 0, false, 20, 0, 0, List.of()));
        assertTrue(run(two.build(), PackRules::unattributedStall).isEmpty(), "two hitches are not a repeat, nor 20 ms a stall");
        FactsBuilder paused = new FactsBuilder();
        for (int i = 1; i <= 4; i++) paused.hitch(new Facts.Hitch(i, 40, 5, 0, false, 12, 0, 0, List.of()));
        assertTrue(run(paused.build(), PackRules::unattributedStall).isEmpty(), "a 5 ms GC pause leaves 7 ms of 40");
    }

    @Test
    void aRepeatCountsAHitchOnceAndNamesTheWindowOfItsWorst() {
        FactsBuilder both = new FactsBuilder();
        for (int i = 1; i <= 12; i++) {
            both.hitch(i <= 3 ? new Facts.Hitch(i, 40, 0, 0, false, 10, 10, 0, List.of()) : FactsBuilder.hitch(i, 40));
        }
        Recommendation a = run(both.build(), PackRules::unattributedStall).get(0);
        assertEquals("In 3 of 12 hitches, at least a quarter of the frame was in code Renderable does not time, up to"
                + " 10 ms of client tick.", a.finding(), "two parts of one hitch count it once; a tie goes to the first part");
        assertEquals(0.06, a.msPerFrame(), 1e-9, "both parts of each hitch are time at stake");

        FactsBuilder windows = new FactsBuilder();
        for (int i = 1; i <= 3; i++) windows.hitch(new Facts.Hitch(i, 40, 0, 0, false, 0, 12, 0, List.of()));
        windows.hitch(new Facts.Hitch(4, 40, 0, 0, false, 0, 0, 15, List.of()));
        assertEquals("In 4 of 4 hitches, at least a quarter of the frame was in code Renderable does not time, up to"
                + " 15 ms of main-thread tasks.", run(windows.build(), PackRules::unattributedStall).get(0).finding());
    }

    @Test
    void theRepeatFormSaysTheStallToo() {
        // Review focus 4. One hitch passes the stall form and three more the repeat form: one finding, the repeat, whose
        // worst is the stall's. A stall's part is half its hitch, so it always counts toward the repeat as well.
        FactsBuilder b = new FactsBuilder().hitch(new Facts.Hitch(1, 382.4, 0, 0, false, 374, 1, 0, List.of()));
        for (int i = 2; i <= 4; i++) b.hitch(new Facts.Hitch(i, 40, 0, 0, false, 12, 1, 0, List.of()));
        List<Recommendation> r = run(b.build(), PackRules::unattributedStall);
        assertEquals(1, r.size());
        assertEquals("In 4 of 4 hitches, at least a quarter of the frame was in code Renderable does not time, up to"
                + " 374 ms of client tick.", r.get(0).finding());
        assertEquals(374, r.get(0).worstMs(), 1e-9);
        assertEquals((374 + 3 * 12) / 1000.0, r.get(0).msPerFrame(), 1e-9);
    }
```

In `AdvisorTest.java`, replace (it occurs once):

```java
import io.github.edwardg117.renderable.core.Environment;
```

with:

```java
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.core.SessionConfig;
```

Then append to `AdvisorTest`:

```java
    @Test
    void thePhase6FixtureGetsFrameRateAdviceAndNoStutter() {
        // Review focus 2: a session with no hitches. No stutter form can fire, so sound-wait gives its frame-rate form
        // and unattributed-stall nothing.
        List<Recommendation> advice = Advisor.advise(TestSessions.phase6(), Environment.empty());
        assertEquals(List.of("distant-horizons", "sound-wait", "block-entity-type"),
                advice.stream().map(Recommendation::rule).toList(), "by ms/frame: lod 2.0, sound 1.6, the fluid tank 1.0");
        assertTrue(advice.stream().allMatch(a -> a.kind() == Recommendation.Kind.FRAME_RATE));
        assertEquals("Distant Horizons draws its far terrain in 2.0 ms of each 20.0 ms frame.", advice.get(0).finding(),
                "exactly a tenth of the frame");
        assertEquals("Playing sounds kept the game waiting 1.6 ms a frame; create:steam (Blocks) played 100 times a second.",
                advice.get(1).finding());
        assertEquals("Set the Blocks volume to 0% in Music & Sounds: the game then skips those sounds instead of waiting"
                + " for them. A pack author can report the sound's mod with this file.", advice.get(1).action());
    }

    @Test
    void unnamedTimeAndASoundStallAreSeparateFindings() {
        // Review focus 4. A play is named or bucketed time, never unnamed, so a hitch a sound stalled can also have
        // unnamed time of its own, and each rule is about its own part of the hitch.
        ModKey steam = ModKey.sound("create:steam", "block");
        FactsBuilder both = new FactsBuilder().sound("create:steam", "block", 0.1, 40);
        for (int i = 1; i <= 4; i++) both.hitch(new Facts.Hitch(i, 100, 0, 0, false, 30, 0, 0, List.of(top(steam, 60))));
        List<Recommendation> advice = Advisor.advise(both.build());
        assertEquals(List.of("sound-wait", "unattributed-stall"), advice.stream().map(Recommendation::rule).toList(),
                "both stutter, ranked by their worst: the sound's 60 ms, then the unnamed 30");
        assertEquals("Playing create:steam (Blocks) was a top cost in 4 of 4 hitches, up to 60 ms.", advice.get(0).finding());
        assertEquals("In 4 of 4 hitches, at least a quarter of the frame was in code Renderable does not time, up to"
                + " 30 ms of client tick.", advice.get(1).finding());

        FactsBuilder named = new FactsBuilder().sound("create:steam", "block", 0.1, 40);
        for (int i = 1; i <= 4; i++) named.hitch(new Facts.Hitch(i, 100, 0, 0, false, 10, 0, 0, List.of(top(steam, 60))));
        assertEquals(List.of("sound-wait"), Advisor.advise(named.build()).stream().map(Recommendation::rule).toList(),
                "the 10 ms left unnamed is under a quarter of each hitch");
    }

    @Test
    void theSoundBucketIsNamedTimeNotUnnamed() {
        // Review focus 4. Eight quiet 10 ms frames and four 60 ms hitches, each with a 45 ms tick, 40 ms of it in the
        // sound engine's tick. Facts.of leaves the bucket out of the tick's unnamed time, so the hitches are
        // sound-wait's, and unattributed-stall's repeat form does not count them again.
        long ms = TestSessions.MS;
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * ms, 2.0, 16 * ms, 5, 0, 0), () -> now[0], 0);
        long t = 0;
        for (int i = 0; i < 12; i++) {
            boolean hitch = i % 3 == 2;
            now[0] = t;           s.onRenderPre(0, 0);
            now[0] = t + 5 * ms;  s.onRenderPost();
            if (hitch) {
                s.onTickPre();
                s.begin(null, Bucket.SOUND); now[0] = t + 45 * ms; s.end();
                now[0] = t + 50 * ms; s.onTickPost();
            }
            t += hitch ? 60 * ms : 10 * ms;
        }
        now[0] = t;
        s.onRenderPre(0, 0);

        List<Recommendation> advice = Advisor.advise(s.finish(), Environment.empty());
        assertEquals(List.of("sound-wait"), advice.stream().map(Recommendation::rule).toList(),
                "5 ms of each hitch's tick is unnamed, under a quarter of it");
        assertEquals("In 4 of 4 hitches, the sound engine took at least a quarter of the frame (up to 40 ms).",
                advice.get(0).finding());
    }
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*PackRulesTest' --tests '*AdvisorTest'`
Expected: 22 tests (the two classes), 4 failures, the four that need the repeat form:
- `unnamedTimeRepeatsAtAQuarterOfTheHitchInThreeHitchesAndAQuarterOfThem` and
  `aRepeatCountsAHitchOnceAndNamesTheWindowOfItsWorst` throw `IndexOutOfBoundsException` from `get(0)` on an empty list;
- `theRepeatFormSaysTheStallToo` gets the stall form's finding, `Hitch #1 (382 ms): ...`;
- `unnamedTimeAndASoundStallAreSeparateFindings` gets `[sound-wait]` alone.

`thePhase6FixtureGetsFrameRateAdviceAndNoStutter` passes already, as it should: it pins Tasks 1 to 3 on a session
with no hitches. So does `theSoundBucketIsNamedTimeNotUnnamed`: the partition it pins is `Facts.of`'s, which already
leaves the bucket out of unnamed time, and no stall form can see 5 ms.

- [ ] **Step 3: Add the threshold**

In `Thresholds.java`, replace (it occurs once):

```java
    static final double SOUND_BUCKET_SHARE = 0.25;
```

with:

```java
    static final double SOUND_BUCKET_SHARE = 0.25;
    /** The share of a hitch its unnamed time must reach to count toward {@code unattributed-stall}'s repeat form. */
    static final double UNNAMED_SHARE = 0.25;
```

- [ ] **Step 4: Give `unattributed-stall` its repeat form**

In `PackRules.java`, replace the whole of `unattributedStall`, with its javadoc (it occurs once):

```java
    /** A hitch whose time is mostly in code nothing names: other tick, other render or tasks, less named time. */
    static void unattributedStall(Facts f, List<Recommendation> out) {
        Facts.Hitch worstHitch = null;
        String worstWhere = null;
        double worst = 0;
        double sum = 0;
        for (Facts.Hitch h : f.hitches()) {
            double[] parts = {h.unnamedTickMs(), h.unnamedRenderMs(), h.untimedTasksMs()};
            String[] where = {"client tick", "rendering", "main-thread tasks"};
            for (int i = 0; i < parts.length; i++) {
                // The frame's GC pause landed in one of the parts; which one is not known, so each loses it.
                double v = Math.max(0, parts[i] - h.gcMs());
                if (v < Thresholds.STALL_MS || v < Thresholds.STALL_SHARE * h.totalMs()) continue;
                sum += v;
                if (v > worst) {
                    worst = v;
                    worstHitch = h;
                    worstWhere = where[i];
                }
            }
        }
        if (worstHitch == null) return;
        out.add(new Recommendation("unattributed-stall", Kind.STUTTER, Audience.PACK_AUTHOR, "A stall Renderable cannot name",
                String.format(Locale.ROOT, "Hitch #%d (%.0f ms): %.0f ms of %s is in code Renderable does not time.",
                        worstHitch.number(), worstHitch.totalMs(), worst, worstWhere),
                "Profile the same place with a sampling profiler such as spark, which names the code.",
                sum / f.frames(), worst));
    }
```

with:

```java
    /**
     * Time in code nothing names (spec addendum §P5.B2, §P6.9). A hitch has three parts that can hold it: other tick,
     * other render and tasks, each less the time the Mods tab names in it and less the frame's GC pause. A stall: one
     * part is at least {@link Thresholds#STALL_MS} and {@link Thresholds#STALL_SHARE} of its hitch. A repeat: one part
     * is at least {@link Thresholds#UNNAMED_SHARE} of its hitch, in {@link Thresholds#REPEATED_HITCHES} or more hitches
     * and in at least {@link Thresholds#HITCH_REPEAT_SHARE} of them, a hitch counting once. A stall's part is also a
     * quarter of its hitch, so a repeat always reaches the stall's worst: when it passes, it is named, and says both.
     */
    static void unattributedStall(Facts f, List<Recommendation> out) {
        String[] where = {"client tick", "rendering", "main-thread tasks"};
        Facts.Hitch stallHitch = null;
        String stallWhere = null;
        double stall = 0;
        double stallSum = 0;
        int repeats = 0;
        String repeatWhere = null;
        double repeatWorst = 0;
        double repeatSum = 0;
        for (Facts.Hitch h : f.hitches()) {
            double[] parts = {h.unnamedTickMs(), h.unnamedRenderMs(), h.untimedTasksMs()};
            boolean counted = false;
            for (int i = 0; i < parts.length; i++) {
                // The frame's GC pause landed in one of the parts; which one is not known, so each loses it.
                double v = Math.max(0, parts[i] - h.gcMs());
                if (v >= Thresholds.STALL_MS && v >= Thresholds.STALL_SHARE * h.totalMs()) {
                    stallSum += v;
                    if (v > stall) {
                        stall = v;
                        stallHitch = h;
                        stallWhere = where[i];
                    }
                }
                if (v >= Thresholds.UNNAMED_SHARE * h.totalMs()) {
                    repeatSum += v;
                    if (!counted) repeats++;
                    counted = true;
                    if (v > repeatWorst) {
                        repeatWorst = v;
                        repeatWhere = where[i];
                    }
                }
            }
        }
        int hitches = f.hitches().size();
        String action = "Profile the same place with a sampling profiler such as spark, which names the code.";
        if (repeats >= Thresholds.REPEATED_HITCHES && repeats >= Thresholds.HITCH_REPEAT_SHARE * hitches) {
            out.add(new Recommendation("unattributed-stall", Kind.STUTTER, Audience.PACK_AUTHOR,
                    "Stutter Renderable cannot name",
                    String.format(Locale.ROOT, "In %d of %d hitches, at least a quarter of the frame was in code"
                            + " Renderable does not time, up to %.0f ms of %s.", repeats, hitches, repeatWorst, repeatWhere),
                    action, repeatSum / f.frames(), repeatWorst));
        } else if (stallHitch != null) {
            out.add(new Recommendation("unattributed-stall", Kind.STUTTER, Audience.PACK_AUTHOR,
                    "A stall Renderable cannot name",
                    String.format(Locale.ROOT, "Hitch #%d (%.0f ms): %.0f ms of %s is in code Renderable does not time.",
                            stallHitch.number(), stallHitch.totalMs(), stall, stallWhere),
                    action, stallSum / f.frames(), stall));
        }
    }
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **315** tests, 0 failures. The stall form's existing tests (`anUnattributedStallNamesTheWorstHitch`,
`anUnattributedStallAtItsThresholdsAndInEachWindow`) pass unchanged: each has one or two hitches, too few for a repeat.
The report and JSON tests do not move either: no other fixture has three hitches.

- [ ] **Step 6: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/advice/Thresholds.java src/main/java/io/github/edwardg117/renderable/core/advice/PackRules.java src/test/java/io/github/edwardg117/renderable/core/advice/PackRulesTest.java src/test/java/io/github/edwardg117/renderable/core/advice/AdvisorTest.java
```

Report to the orchestrator for the commit: `feat(advice): unattributed-stall also fires on repeated unnamed time`.

---

### Task 5: The README and the spec amendments

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md` (P5.B2, P5.4)
- Modify: `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md` (§11)
- Modify: `docs/superpowers/specs/2026-10-10-renderable-phase-6-design.md` (P6.9)

**Interfaces:** none.

Spec §P6.10 (docs) and the 6b rows of §P6.11, with deviations 1 to 7 applied. Every find string below occurs
exactly once in its file. If one does not, stop and report rather than guess. The files are CRLF. Step 3's find string
is plan 6a's §11 row, as 6a's Task 7 wrote it.

- [ ] **Step 1: The README**

Replace:

```markdown
5. The **Mods** tab ranks mods by the time their event handlers, HUD layers and packets took; click a mod
   to see its event types, layers and packets.
```

with:

```markdown
5. The **Mods** tab ranks mods by the time their event handlers, HUD layers, packets and sounds took; click a
   mod to see its event types, layers, packets and sounds.
```

Replace:

```markdown
  then frame rate, then notes: a stall averaged over a session looks small but is what a player notices. The
  rules are generic, not written for any one pack, and their thresholds are first estimates.
```

with:

```markdown
  then frame rate, then notes: a stall averaged over a session looks small but is what a player notices. The
  rules are generic, not written for any one pack, and their thresholds are first estimates. One names a mod:
  it reads the time Renderable measures in Distant Horizons' own drawing, so it can only appear where Distant
  Horizons is installed, and it names that mod's settings to lower.
- **Sounds keep the game waiting** appears when playing sounds takes half a millisecond a frame, or when one
  sound stalls a hitch or recurs in several, or when the sound engine takes a quarter of the frame in three or more
  hitches and in a quarter of them. Each play waits for the sound thread to hand it a channel, and at
  0% the game skips a sound without waiting, so the advice names the Music & Sounds slider to set to 0% for that
  sound's category.
- The pack author's note about code Renderable does not time appears after one long stall, or when such code
  took at least a quarter of the frame in three or more hitches and in a quarter of them. A sampling profiler
  such as spark names that code.
```

Replace:

```markdown
- **Time split** shows where the average frame went: client tick (with its block entity, entity
  and particle tick shares in brackets), block entities, entities, particles, Flywheel, light
  updates, chunk upload, the HUD, other render work, **tasks** (packets and other work queued for the
  main thread), the **frame cap** (the wait for the fps limit, when one is set and reached), and swap/wait
  (the buffer swap and vsync, the GPU and driver, and the rest of the frame loop). Tasks and the frame cap
  are shown when they took time. `mods x` in brackets after tick, other render or tasks is the part of it the
  **Mods** tab names. The Frames tab is the exception: it always lists tasks and the frame cap, and shows that
  part as an **in the Mods tab** row under each. A big swap/wait with a low GPU load is not a rendering
  problem in the world.
```

with:

```markdown
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
  shows the Mods tab's part as an **in the Mods tab** row under each, and shows the rest of the tick as **other
  tick**, that part included. Each figure is rounded on its own, so a bracket can differ from its total by a tenth
  or two. A big swap/wait with a low GPU load is not a rendering problem in the world.
```

Replace:

```markdown
- **Mods** ranks each mod by the time its event handlers, its HUD layers and the packets addressed to it
  took, in microseconds per frame; expand a mod to see each event type, layer and packet type. Counting is
```

with:

```markdown
- **Mods** ranks each mod by the time its event handlers, its HUD layers, the packets addressed to it and the
  sounds in its namespace took, in microseconds per frame; expand a mod to see each event type, layer, packet
  type and sound. A sound row, such as `sound create:steam (block)`, is one sound event played in one category.
  It times the whole play, the wait for a channel included, and its **calls/f** is plays per frame. A sound only
  names time: the bucket it was played in keeps it, such as **be tick** for a machine's sound. The Frames
  section's **Sound plays** line sums the sound rows. A play inside a packet or handler of the same mod counts in
  both rows, so a mod's total on the Mods tab can read high: the `minecraft` row counts each vanilla sound the
  server sends in its packet's row and in its sound row. Counting is
```

Replace:

```markdown
Vanilla layers and packets appear under `minecraft`
```

with:

```markdown
Vanilla layers, packets and sounds appear under `minecraft`
```

Replace:

```markdown
`./gradlew runClient` starts a dev client; add `-PpackMods` to include Sodium, Iris, Create and
Create: Enchantable Machinery at the versions the Gears & Frontiers pack pins.
```

with:

```markdown
`./gradlew runClient` starts a dev client; add `-PpackMods` to include Sodium, Iris, Create,
Create: Enchantable Machinery and Distant Horizons at the versions the Gears & Frontiers pack pins.
```

Replace:

```markdown
Schema 5 (this version) adds `tasksUs`, `capUs` and `namedUs` (`render`, `tick` and `tasks`, empty ones left out)
```

with:

```markdown
Schema 6 (this version) adds four buckets to `avgBucketsMs` and to each frame's `bucketsUs`: `terrain`, `lod`,
`sound` and `animate tick`. Buckets are written by label, never by position. Sound rows sit in a mod's `entries`
like its other rows, keyed `sound@<mod>:<event id> (<category>)`, such as `sound@create:create:steam (block)`.
`terrain` and `lod` are render buckets and `sound` and `animate tick` tick buckets, so `otherRenderUs` and
`avgOtherRenderMs` no longer include the first two, nor `avgOtherTickMs` the last two. There is no per-frame
`unnamed` field: it is `max(0, max(0, tickUs − the tick buckets) − namedUs.tick)`. Nothing else changes shape.

Schema 5 added `tasksUs`, `capUs` and `namedUs` (`render`, `tick` and `tasks`, empty ones left out)
```

- [ ] **Step 2: The P5 addendum's P5.B2 and P5.4**

In `docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md`, replace:

```markdown
| `chunk-rebuilds` | player | frame rate, or stutter when chunk upload is the largest part of the split in 3 or more hitches | chunk upload ≥ 1.0 ms/f, with biome blend ≥ 3 or render distance ≥ 16 | chunk upload; in the stutter form, its largest time in one of the hitches it leads |
```

with:

```markdown
| `chunk-rebuilds` | player | frame rate, or stutter when chunk upload is the largest part of the split in 3 or more hitches | chunk upload ≥ 1.0 ms/f, with biome blend ≥ 3 or render distance ≥ 16 | chunk upload; in the stutter form, its largest time in one of the hitches it leads |
| `distant-horizons` (phase 6) | player | frame rate | `lod` ≥ 1.0 ms/f and ≥ 10% of the average frame | `lod` |
| `sound-wait` (phase 6) | player | frame rate, or stutter when one sound row took at least 50 ms and at least half of a hitch, or passes `hitch-repeat`'s test, or the `sound` bucket is at least a quarter of the hitch in 3 or more hitches and in at least a quarter of all hitches | the sound rows sum to ≥ 0.5 ms/f, or one of the stutter form's tests | sound play time; in the stutter form, that row's largest time in one of the hitches the finding is about, or for the bucket test the `sound` bucket's largest time in one of those hitches |
```

Replace:

```markdown
| `unattributed-stall` | pack author | stutter | a hitch where other tick, other render or tasks, less the time the Mods tab names in it (P5.A6) and less the frame's GC pause, is ≥ 50 ms and at least half the frame | that time |
```

with:

```markdown
| `unattributed-stall` | pack author | stutter | a hitch where other tick, other render or tasks, less the time the Mods tab names in it (P5.A6) and less the frame's GC pause, is ≥ 50 ms and at least half the frame; or, from phase 6, where one of them is at least a quarter of the hitch in 3 or more hitches and in at least a quarter of all hitches | that time; its largest in one hitch |
```

Replace:

```markdown
| `chunk-rebuilds` | Chunk rebuilds take 1.8 ms a frame and are the largest part of 4 of 6 hitches, at render distance 16 and biome blend 7x7. | Lower Biome Blend to 5x5 or less. While travelling, a lower render distance rebuilds fewer sections. |
```

with:

```markdown
| `chunk-rebuilds` | Chunk rebuilds take 1.8 ms a frame and are the largest part of 4 of 6 hitches, at render distance 16 and biome blend 7x7. | Lower Biome Blend to 5x5 or less. While travelling, a lower render distance rebuilds fewer sections. |
| `distant-horizons` | Distant Horizons draws its far terrain in 3.2 ms of each 10.1 ms frame. | Lower Quality Preset or LOD Chunk Render Distance Radius in Distant Horizons' settings, which its button beside FOV in Options opens. |
| `sound-wait` | Playing sounds kept the game waiting 1.4 ms a frame; `create:steam` (Blocks) played 85 times a second. | Set the Blocks volume to 0% in Music & Sounds: the game then skips those sounds instead of waiting for them. A pack author can report the sound's mod with this file. |
| `sound-wait`, bucket test | In 30 of 83 hitches, the sound engine took at least a quarter of the frame (up to 18 ms). | Set the Blocks volume to 0% in Music & Sounds: the game then skips those sounds instead of waiting for them. A pack author can report the sound's mod with this file. |
```

Replace:

```markdown
| `unattributed-stall` | Hitch #1 (382 ms): 374 ms of client tick is in code Renderable does not time. | Profile the same place with a sampling profiler such as spark, which names the code. |
```

with:

```markdown
| `unattributed-stall` | Hitch #1 (382 ms): 374 ms of client tick is in code Renderable does not time. | Profile the same place with a sampling profiler such as spark, which names the code. |
| `unattributed-stall`, repeat form | In 30 of 83 hitches, at least a quarter of the frame was in code Renderable does not time, up to 20 ms of client tick. | Profile the same place with a sampling profiler such as spark, which names the code. |
```

Replace:

```markdown
- **`flywheel-shaders` says only to turn shaders off.** The Flywheel the pack pins does not instance while any
  shader pack is in use, so no shader pack brings instancing back.
```

with:

```markdown
- **`flywheel-shaders` says only to turn shaders off.** The Flywheel the pack pins does not instance while any
  shader pack is in use, so no shader pack brings instancing back.

Phase 6 added two rules and widened one (spec addendum §P6.9, plan 6b):

- **`sound-wait`** reads the Mods tab's sound rows (§P6.5), and each hitch's `sound` bucket. It names a category by
  its slider in Music & Sounds, from a fixed English table, the captions of Minecraft 1.21.1's `soundCategory.*`
  keys: `master` Master Volume, `music` Music, `record` Jukebox/Note Blocks, `weather` Weather, `block` Blocks,
  `hostile` Hostile Creatures, `neutral` Friendly Creatures, `player` Players, `ambient` Ambient/Environment, `voice`
  Voice/Speech. The action says to set a slider to 0%, because `play` skips a sound only at a volume of exactly 0:
  the stalled or repeated sound's slider in the row test, the costliest row's otherwise, and none when the session
  timed no sound row. It leaves out the pack-author sentence for a vanilla sound. Its row test is the mod rules'
  stutter test, applied to sound rows. Its bucket test counts a hitch when the `sound` bucket, less the frame's GC
  pause, is a quarter of it, and speaks only when no single row passes. `hitch-repeat` and the `mod-*` rules never
  see sound rows, and the bucket is never unnamed time, so no sound is found twice.
- **`distant-horizons`** is the one rule that names a mod, by the phase 6 design, although P5.1 chose generic rules
  only. `lod` is time Renderable measures in Distant Horizons' own render methods, so the rule can only fire where
  DH is installed and hooked. Its action names DH's own settings, the same in 2.4.5-b and 3.3.3.
- **`unattributed-stall`** also fires on repeated unnamed time. Each hitch's three parts are taken on their own, as
  in the stall form, and a hitch counts once however many of its parts pass. The finding names the window of the
  largest part. When both forms pass, the repeat is named, with that largest part as its worst: a stall's part is
  always a quarter of its hitch, so the repeat says both.
```

Replace:

```markdown
- **No attribution for rendering that other mods inject into vanilla methods by mixin.** Distant
  Horizons' LOD pass and Sodium's terrain draw still land in other render, which remains the largest
  unnamed bucket. A `terrain` bucket around Sodium's chunk-layer draw is a candidate
  for a later phase.
- **No finer split of other tick.** The verified call sites for a later phase are
  `ClientLevel.animateTick(III)V` (offset 773 in `Minecraft.tick`) and `Gui.tick(Z)V` (offset 81).
  `unattributed-stall` points at a sampling profiler meanwhile.
```

with:

```markdown
- **No attribution for rendering that other mods inject into vanilla methods by mixin.** Distant
  Horizons' LOD pass and Sodium's terrain draw still land in other render, which remains the largest
  unnamed bucket. A `terrain` bucket around Sodium's chunk-layer draw is a candidate
  for a later phase. *Done in phase 6:* `terrain` times the chunk-layer draws, and `lod` Distant Horizons' LOD
  drawing inside them (spec addendum §P6.3, §P6.4). Rendering other mods inject anywhere but a chunk-layer draw
  still lands in other render.
- **No finer split of other tick.** The verified call sites for a later phase are
  `ClientLevel.animateTick(III)V` (offset 773 in `Minecraft.tick`) and `Gui.tick(Z)V` (offset 81).
  `unattributed-stall` points at a sampling profiler meanwhile. *Done in part in phase 6:* `sound` and
  `animate tick` are timed, the rest of the tick shows as `unnamed`, and `unattributed-stall` also fires when
  unnamed time recurs in hitches (spec addendum §P6.3, §P6.6, §P6.9). `Gui.tick` and the other small tick calls
  stay in `unnamed` (§P6.12).
```

Replace:

```markdown
- **No pack-specific rules,** and no rule that changes anything.
```

with:

```markdown
- **No pack-specific rules,** and no rule that changes anything. Phase 6's `distant-horizons` names one mod,
  Distant Horizons (spec addendum §P6.9); it fires only on time measured in DH's own render methods.
```

- [ ] **Step 3: The base spec's §11**

In `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md`, replace plan 6a's row:

```markdown
| 6a | the `terrain`, `lod`, `sound` and `animate tick` buckets, `lod` carved out of the bucket around it, sound plays as Mods tab rows with the Frames section's Sound plays line, `unnamed` in the tick's brackets, JSON schema 6, Distant Horizons in the dev client | the phase 6 addendum's checks A1 to A5 in unit tests and the dev client; the in-pack acceptance at the end of phase 6 |
```

with:

```markdown
| 6a | the `terrain`, `lod`, `sound` and `animate tick` buckets, `lod` carved out of the bucket around it, sound plays as Mods tab rows with the Frames section's Sound plays line, `unnamed` in the tick's brackets, JSON schema 6, Distant Horizons in the dev client | the phase 6 addendum's checks A1 to A5 in unit tests and the dev client; the in-pack acceptance at the end of phase 6 |
| 6b | the `sound-wait` and `distant-horizons` rules, `unattributed-stall`'s repeat form, the README's phase 6 parts, version 0.6.0 | the phase 6 addendum's check B1 in unit tests and in the in-pack acceptance (`docs/acceptance/phase-6.md`) |
```

- [ ] **Step 4: The phase 6 addendum's P6.9**

In `docs/superpowers/specs/2026-10-10-renderable-phase-6-design.md`, replace:

```markdown
| `sound-wait` | Playing sounds kept the game waiting 1.4 ms a frame; `create:steam` (Blocks) played 85 times a second. | Lower the Blocks volume in Music & Sounds: at 0% the game skips those sounds instead of waiting for them. A pack author can report the sound's mod with this file. |
| `distant-horizons` | Distant Horizons draws its far terrain in 3.2 ms of each 10.1 ms frame. | Lower its LOD render distance or its quality preset in Distant Horizons' settings. |
| `unattributed-stall`, repeat form | In 30 of 83 hitches, at least a quarter of the frame was in code Renderable does not time (up to 20 ms). | Profile the same place with a sampling profiler such as spark, which names the code. |
```

with:

```markdown
| `sound-wait` | Playing sounds kept the game waiting 1.4 ms a frame; `create:steam` (Blocks) played 85 times a second. | Set the Blocks volume to 0% in Music & Sounds: the game then skips those sounds instead of waiting for them. A pack author can report the sound's mod with this file. |
| `distant-horizons` | Distant Horizons draws its far terrain in 3.2 ms of each 10.1 ms frame. | Lower Quality Preset or LOD Chunk Render Distance Radius in Distant Horizons' settings, which its button beside FOV in Options opens. |
| `unattributed-stall`, repeat form | In 30 of 83 hitches, at least a quarter of the frame was in code Renderable does not time, up to 20 ms of client tick. | Profile the same place with a sampling profiler such as spark, which names the code. |
```

Replace:

```markdown
  the slider of the costliest sound row, or no slider when the session has no sound rows.
```

with:

```markdown
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
  - In the stutter form, `sound-wait`'s action names the category of the stalled or repeated sound, not the
    costliest row's. It leaves out the pack-author sentence for a vanilla sound.
  - `unattributed-stall`'s repeat form takes each of the three parts on its own, as the stall form does, and counts
    a hitch once. It names the window of its worst part, and when both forms pass it is the one named.
  - At zero volume, `play` skips every sound except one whose instance can start silent (vanilla's bees and
    minecarts), which still asks for a channel.
```

- [ ] **Step 5: Check and stage**

Run: `git diff --stat`
Expected: `README.md` and the three spec files, and nothing else from this task.

```bash
git add README.md docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md docs/superpowers/specs/2026-10-10-renderable-phase-6-design.md
```

Report to the orchestrator for the commit: `docs: phase 6 in the README, and the 6b amendments to the specs`.

---

### Task 6: Version 0.6.0

**Files:**
- Modify: `gradle.properties`
- Modify: `docs/pack-module.md`

**Interfaces:** none. Produces `build/libs/renderable-0.6.0.jar` (git ignores `build/`).

Spec §P6.10: `mod_version` becomes 0.6.0 in 6b. The pack module entry names the release it would install, so it moves
with the version (deviation 1). The CI release workflow refuses a `v*` tag that does not match `mod_version`, so this
must land before Task 8's tag.

- [ ] **Step 1: Bump the version**

In `gradle.properties`, replace (it occurs once):

```properties
mod_version=0.5.0
```

with:

```properties
mod_version=0.6.0
```

- [ ] **Step 2: Point the pack module entry at 0.6.0**

In `docs/pack-module.md`, make four replacements. Each find string occurs once; the first two are inside the XML
block, after its leading tabs.

Replace:

```xml
<URL priority="1">https://github.com/edwardg117/Renderable/releases/download/v0.5.0/renderable-0.5.0.jar</URL>
```

with:

```xml
<URL priority="1">https://github.com/edwardg117/Renderable/releases/download/v0.6.0/renderable-0.6.0.jar</URL>
```

Replace:

```xml
<version>0.5.0</version>
```

with:

```xml
<version>0.6.0</version>
```

Replace:

```markdown
`stat -c %s renderable-0.5.0.jar` and `md5sum renderable-0.5.0.jar`, or `certutil -hashfile renderable-0.5.0.jar MD5`
```

with:

```markdown
`stat -c %s renderable-0.6.0.jar` and `md5sum renderable-0.6.0.jar`, or `certutil -hashfile renderable-0.6.0.jar MD5`
```

Replace:

```markdown
or the tag is not `v0.5.0`. It must be
```

with:

```markdown
or the tag is not `v0.6.0`. It must be
```

Then check: `grep -c "0\.5\.0" docs/pack-module.md` prints `0`.

- [ ] **Step 3: Build and test**

Run: `./gradlew build --no-daemon`, then `./gradlew test --rerun --no-daemon`, then `ls build/libs/`
Expected: BUILD SUCCESSFUL both times, **315** tests, and `renderable-0.6.0.jar` in `build/libs/`.

- [ ] **Step 4: Stage and report**

```bash
git add gradle.properties docs/pack-module.md
```

Report to the orchestrator for the commit: `build: version 0.6.0`.

---

### Task 7: The in-pack acceptance (the orchestrator, with the user)

**Files:** `docs/acceptance/phase-6.md` (create)

Spec §P6.10, short, as the user prefers. It checks what no dev client could:
- §P6.9's check B1 in the pack;
- plan 6a's leftovers ("After this plan" in plan 6a): Create's boiler sound under `create`, `lod` against the pack's own
  DH, `unnamed` at the 16:08 spot, nothing named before reading differently at the user's base, and the Hitches
  tab's split width (plan 6a's "Carried to 6b");
- the mute check of §P6.3's A1 (its third bullet), which plan 6a's dev client could not show because its world was
  all but silent. It is optional, and it is `sound-wait`'s action tried in the game;
- whether the Mods tab's name column cuts a long sound label's `(category)`.

The user plays; the orchestrator reads the reports and the log from the instance, read-only, and writes the results.
A FAIL is a defect: stop and plan the fix with the user before Task 8.

- [ ] **Step 1: Ask the user to run the sessions**

Send the user this:

> 1. Close the game. In `%APPDATA%\.MCUpdater\instances\Gears-And-Frontiers\mods\`, add
>    `H:\repos\Renderable\build\libs\renderable-0.6.0.jar` (remove `renderable-0.5.0.jar` first if it is there).
> 2. Start the pack and join the server.
> 3. **Session 1, the other player's base.** Stand at -1169 91 -1678, the spot of the 16:08 session, press **F7**
>    (this instance still binds Renderable's key there) and play normally for the 30 seconds.
> 4. When its results screen opens, tell me:
>    - on the **Hitches** tab, whether a hitch's split is cut off before `terrain` and `lod`;
>    - on the **Mods** tab, opening `create` or `minecraft`, whether a long `sound ...` label loses its `(category)`
>      at the end;
>    - on the **Advice** tab, which slider "Sounds keep the game waiting" names, if it is there.
>
>    Then close the screen.
>
>    Then open Renderable's config screen (Mods, Renderable, Config: Configured's screen here) and its client
>    config's Overlay section. "Overlay Enabled" is off in this instance, so the session's labels are hidden: turn
>    it on, click Save, return to the game and tell me whether the labels appear without a restart. Then turn it
>    off there the same way and tell me whether they go. Leave it as you prefer afterwards.
> 5. **Session 2, optional: the same spot, muted.** In Options, Music & Sounds, set that slider to 0% (Blocks if the
>    Advice tab named none). From the same spot, press **F7** again and play normally for 30 seconds. Then put the
>    slider back where it was. Skip this session if you would rather not.
> 6. **Session 3, your base.** At your base (about 2562 97 1152), press **F7** again and play normally for 30 seconds.
> 7. Tell me when the sessions are done, and whether you ran session 2. You can leave the game running.

- [ ] **Step 2: Read the new reports and the log**

In Git Bash, with the instance at `I="$APPDATA/.MCUpdater/instances/Gears-And-Frontiers"`:

```bash
I="$APPDATA/.MCUpdater/instances/Gears-And-Frontiers"
ls -t "$I/renderable/" | head -6
python -I - "$I" <<'EOF'
import glob, json, os, sys
inst = sys.argv[1]
TICK = ("particle tick", "be tick", "entity tick", "sound", "animate tick")
for f in sorted(glob.glob(os.path.join(inst, "renderable", "renderable-*.json")), key=os.path.getmtime)[-3:]:
    r = json.load(open(f, encoding="utf-8"))
    if r["mod"]["version"] != "0.6.0":
        continue    # the 0.5.0 report before them, when session 2 was skipped
    env, st, b = r["environment"], r["stats"], r["stats"]["avgBucketsMs"]
    frames = {x["i"]: x for x in r["frames"]}
    print("==", os.path.basename(f), "schema", r["schema"], "version", r["mod"]["version"],
          "frames", r["session"]["frames"], "distanthorizons", env.get("distanthorizons"))
    for k in ("hooksInstalled", "hooksFired", "hooksFailed"):
        print(k, env[k])
    print("avg ms: frame %.2f  terrain %.2f  lod %.2f  sound %.2f  animate tick %.2f  other render %.2f"
          "  other tick %.2f  named tick %.2f" % (st["avgMs"], b["terrain"], b["lod"], b["sound"], b["animate tick"],
          st["avgOtherRenderMs"], st["avgOtherTickMs"], st["avgNamedMs"]["tick"]))
    under = 0
    for i in r["hitches"]:
        x = frames[i]
        unnamed = max(0, x["tickUs"] - sum(x["bucketsUs"].get(k, 0) for k in TICK) - x["namedUs"].get("tick", 0))
        if unnamed < 0.25 * x["tickUs"] or x["tickUs"] == 0:
            under += 1
    print("hitches", len(r["hitches"]), "of which unnamed is under a quarter of the tick:", under)
    tops = [(t["us"], frames[i]["totalUs"]) for i in r["hitches"] for t in frames[i]["top"]
            if t["key"].startswith("sound@") and not t["gc"]]
    print("sound rows among the hitches' top contributors:", len(tops), "of them a quarter of their hitch or more:",
          sum(1 for us, total in tops if us >= 0.25 * total))
    bucket = [(max(0, frames[i]["bucketsUs"].get("sound", 0) - 1000 * frames[i]["gcMs"]), frames[i]["totalUs"])
              for i in r["hitches"]]
    print("hitches whose sound bucket, less the GC pause, is a quarter of the frame or more:",
          sum(1 for us, total in bucket if us >= 0.25 * total), "up to %.1f ms" % (max([0] + [us for us, _ in bucket]) / 1000))
    rows = [(m["key"], e) for m in r["mods"] for e in m["entries"] if e["key"].startswith("sound@")]
    print(len(rows), "sound rows; the costliest:")
    for mod, e in sorted(rows, key=lambda p: -(p[1]["renderUs"] + p[1]["tickUs"]))[:10]:
        print("  %-14s %-60s %6d plays %9d us" % (mod, e["label"], e["calls"] + e["tickCalls"], e["renderUs"] + e["tickUs"]))
    for a in r["recommendations"]:
        print("advice:", a["rule"], a["kind"], "|", a["finding"], "|", a["action"])
EOF
for f in $(ls -t "$I"/renderable/renderable-*.txt | head -3); do    # with session 2 skipped, the third is 0.5.0's
  echo "== $f"
  grep -n "hooks installed\|hooks that did not apply\|Time split\|Sound plays" "$f"
  grep -n -m5 "^  #[0-9]*  frame" "$f"
  grep -cE "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" "$f" "${f%.txt}.json"
done
grep -n "restored .* listener lists" "$I/logs/latest.log" | tail -2
grep -c "could not check the hooks" "$I/logs/latest.log"
grep -in "renderable" "$I/logs/latest.log" | grep -i "mixin\|error\|exception" | head
```

Then compare the user's base, the newest report, with the 0.5.0 session taken there
(`renderable-2026-10-10_15-56-32`):

```bash
python -I - "$I" <<'EOF'
import glob, json, os, sys
inst = sys.argv[1]
old = os.path.join(inst, "renderable", "renderable-2026-10-10_15-56-32.json")
new = max(glob.glob(os.path.join(inst, "renderable", "renderable-*.json")), key=os.path.getmtime)
OLD = ("block entity", "entity", "particle render", "flywheel", "chunk upload", "light", "hud",
       "particle tick", "be tick", "entity tick")
for f in (old, new):
    r = json.load(open(f, encoding="utf-8"))
    st, n, b = r["stats"], r["session"]["frames"], r["stats"]["avgBucketsMs"]
    print("==", os.path.basename(f), "version", r["mod"]["version"], "avg %.2f ms" % st["avgMs"])
    print("  old buckets:", ", ".join("%s %.2f" % (k, b.get(k, 0)) for k in OLD))
    print("  other render then = other render + terrain + lod now: %.2f + %.2f + %.2f = %.2f" % (st["avgOtherRenderMs"],
          b.get("terrain", 0), b.get("lod", 0), st["avgOtherRenderMs"] + b.get("terrain", 0) + b.get("lod", 0)))
    print("  other tick then = other tick + sound + animate tick now: %.2f + %.2f + %.2f = %.2f" % (st["avgOtherTickMs"],
          b.get("sound", 0), b.get("animate tick", 0), st["avgOtherTickMs"] + b.get("sound", 0) + b.get("animate tick", 0)))
    be = r["blockEntities"]["byType"]
    print("  block entity types:", ", ".join("%s %.0f" % (e["label"], (e["renderUs"] + e["tickUs"]) / n) for e in be[:5]))
    print("  mods (us/f):", ", ".join("%s %.0f" % (m["label"], (m["renderUs"] + m["tickUs"]) / n) for m in r["mods"][:6]))
EOF
```

- [ ] **Step 3: Judge each check**

Session 1, the other player's base:
1. **Hooks (A1).** `hooks installed` holds `SOUND`, `ANIMATE_TICK`, `TERRAIN`, `LOD` and `SOUND_PLAYS`.
   `hooks that did not apply` is `none`, and `distanthorizons` reads `2.4.5-b`.
2. **Named (§P6.10, 1).** `sound` and `lod` have time in the split, and `terrain` too.
3. **Unnamed (§P6.10, 1).** In more than half the hitches, `unnamed` is under a quarter of the tick.
4. **Sound rows (A3).** `sound ...` rows appear in the report's Mods section, each under its event's namespace.
   Record which events lead, and whether Create's boiler sound appears under `create`.
5. **Advice (B1).** `distant-horizons` fires. `sound-wait` is expected to fire, by any of its forms:
   - frame rate, if the Sound plays line reads 0.50 ms a frame or more;
   - the row test, if a sound row took a quarter or more of three or more hitches and of a quarter of them (the
     script's "a quarter of their hitch or more" count), or 50 ms and half of one;
   - the bucket test, if the script's sound-bucket count is 3 or more and a quarter of the hitches.

   Record which form it took, and its finding. Its slider is the named row's category, or for the bucket test the
   costliest row's. `unattributed-stall` is expected to be quiet: on the 0.5.0 numbers here its repeat form
   would have fired on client tick (64 of 83 hitches), time `sound` now names. If it fires, record its finding.

Session 2, muted (optional), the same spot:
6. **The mute check (§P6.3, A1's third bullet).** Against session 1: the muted category's sound rows read about
   0 ms a frame while their plays still count, and the `sound` bucket drops. That is `sound-wait`'s action working
   in the game: at 0% `play` skips the wait for a channel. If the user skipped the session, record it **NOT RUN**.
   Check that the user has put the slider back.

Session 3, the user's base:
7. **Nothing named before reads differently (§P6.10, 2).** Against the 0.5.0 session at the same base:
   - the old buckets are in the same range;
   - the top block entity types and mods are much the same;
   - 0.5.0's other render is about this session's other render plus `terrain` plus `lod`;
   - its other tick is about this session's other tick plus `sound` plus `animate tick`.

   The two sessions are hours apart and the scene moves, so judge by order of magnitude, not exact figures.
8. **Hooks and advice.** As check 1, and the user finds the advice sensible for the base. `unattributed-stall` is
   expected to be quiet: on the 0.5.0 numbers its repeat form would have fired here on rendering (8 of 18 hitches, up
   to 14 ms), time `terrain` and `lod` now take. If it fires on ordinary hitches, that is noise for the pack author:
   record its finding, and before Task 8 ask the user whether to count a part only by what it exceeds that window's
   session average (on the 0.5.0 reports that keeps the 16:08 tick stalls, 50 of 83, and drops this base's, 1 of 18).

Both:
9. **The log and privacy.** No Renderable mixin error. No "could not check the hooks" line. Each session's line reads
   `restored N listener lists; 0 still hold a timing proxy`. Neither report holds a UUID: the `grep -cE` counts are 0.
10. **Screen widths (carried from plan 6a).** From the user's answers: whether the Hitches tab cuts a hitch's split
    before `terrain` and `lod`, and whether the Mods tab's name column cuts a long sound label's `(category)`. If
    either does, record it and decide with the user whether it needs a second line or a tooltip, and whether that is
    a task before Task 8 or a later phase. It is not a FAIL.
11. **The overlay switch (§P6.13).** Turning `overlayEnabled` on in the config screen shows the labels at once,
    and off again hides them. A FAIL here is a defect.

- [ ] **Step 4: Write `docs/acceptance/phase-6.md`**

Follow `docs/acceptance/phase-5.md`:
- a header: the date, the machine and GPU, the build under test (`renderable-0.6.0.jar` from `phase-6b-advice` at its
  commit), and the pack's rendering stack;
- a **Scope** paragraph: the short pass the user chose;
- a table of the sessions (session, where, frames, avg ms (fps), p50 / p95 / max, hitches, GC, overhead, advice), and
  the mute session as **NOT RUN** if the user skipped it;
- the hook lists in a code block;
- one section per session, each check above as **PASS**, **FAIL** or **NOT RUN**, with what was seen;
- a closing **Observations** section for anything else noticed: the leading sound events, how much of the old other
  render `terrain` and `lod` took, and what `unnamed` is left with.

- [ ] **Step 5: Commit**

Tell the user "Touch the YubiKey", then `git add docs/acceptance/phase-6.md` and commit
`docs: phase 6 in-pack acceptance`.

---

### Task 8: Merge and tag (the orchestrator, with the user's go-ahead)

- [ ] **Step 1:** With the acceptance passed and the user's go-ahead, fast-forward `main` to `phase-6b-advice`:
  `git checkout main && git merge --ff-only phase-6b-advice`. The branches stack (6a, then 6b), so this brings all of
  phase 6. If `main` has moved, stop and ask.
- [ ] **Step 2:** Run `./gradlew test --rerun --no-daemon` on `main` (**319** tests). Tell the user "Touch the YubiKey",
  and tag: `git tag -s v0.6.0 -m "Renderable 0.6.0"`.
- [ ] **Step 3:** Update the phase 6 status memory with the merge commit and the tag.

---

## Deviations from the spec

1. **More documentation than §P6.11's 6b rows.** Each of these would otherwise be stale or silent:
   - the README's Building paragraph (`-PpackMods` now brings Distant Horizons) and its Report files section
     (schema 6, which plan 6a leaves undocumented there);
   - `docs/pack-module.md`, whose release URL and version follow `mod_version`;
   - a 6b row in the base spec's §11, beside plan 6a's;
   - P5.4's "No pack-specific rules" bullet (deviation 2);
   - the phase 6 addendum's P6.9: the example rows, and a "Settled in plan 6b" bullet for deviations 3 to 7.
2. **`distant-horizons` names a mod, which P5.1's "Generic rules only" rejected.** P5.1 rejected "rules that name this
   pack's mods or configs", and Distant Horizons is one of this pack's mods. §P6.9, the user's phase 6 design,
   supersedes that for this one rule, and the plan does not reopen it. The rule fires only on `lod`, time Renderable
   measures in DH's own methods, so it cannot fire in a pack without DH. P5.1's table stays as the record of the
   phase 5 decision. P5.B2's phase 6 paragraph, P5.4's bullet and the README say where the exception lies.
3. **The `distant-horizons` action uses DH's own names.** §P6.9's draft read "Lower its LOD render distance or its
   quality preset in Distant Horizons' settings." The action names the two settings by their DH 3.3.3 captions and
   says where the button is: "Lower Quality Preset or LOD Chunk Render Distance Radius in Distant Horizons' settings,
   which its button beside FOV in Options opens." The deployed DH 2.4.5-b has the same names, first page and
   button (verified facts).
4. **In the stutter form, `sound-wait`'s action names the stalled sound's slider.** §P6.9 says "the action names the
   category of the costliest row". That holds for the frame-rate form. In the stutter form, the finding is about the
   stalled or repeated sound, which may be in another category: muting the costliest row's slider would leave the
   stall alone.
5. **The pack-author sentence is left out for a vanilla sound.** §P6.9's action ends "A pack author can report the
   sound's mod with this file." For an event in `minecraft`'s namespace (or `neoforge`'s, or `unknown`), no mod owns
   the sound, so the sentence is dropped, by the test the `mod-*` rules use (`Thresholds.isMod`).
6. **The repeat form's finding names its window.** §P6.9's example reads "(up to 20 ms)". The finding reads "up to
   20 ms of client tick", as the stall form names its window, so the pack author knows which part of a spark profile
   to read.
7. **`sound-wait`'s action says to set the slider to 0%, not to lower it.** §P6.9's example said "Lower the Blocks
   volume in Music & Sounds: at 0% the game skips those sounds". Only 0% skips the wait: `SoundEngine.play` returns
   early only when the computed volume is exactly 0, so lowering a slider to anything above 0% saves nothing. The
   action reads "Set the Blocks volume to 0% in Music & Sounds: the game then skips those sounds instead of waiting
   for them." Task 5 applies this to P5.B2's and P6.9's examples.

**Decisions the spec left open:**

1. **The repeat form takes P5.B2's three parts one at a time, as the stall form does.** It does not sum them. A hitch
   counts once however many of its parts pass. `msPerFrame` sums every qualifying part over the session, and the worst
   is the largest part. Summing the parts would let a frame's steady untimed render work, which every short hitch
   carries, count as stutter: the same reason plan 5b's deviation 6 put a floor under the repeat test.
2. **When both `unattributed-stall` forms pass, the repeat is named**, as the mod rules name a repeat whose worst reaches
   the stall's. Here it always does, because a stall's part is at least half its hitch, so it also counts toward the
   repeat. The repeat form's title is "Stutter Renderable cannot name"; the stall form keeps "A stall Renderable cannot
   name".
3. **Where the new rules sit.** Both are settings a player can change, so they live in `SettingsRules`, after
   `chunk-rebuilds` and before the two notes. They take the same place in P5.B2's table. Order matters only for ties.
4. **The stutter test is shared, not copied.** `PackRules`' private `Stutter` and `stutter()` become `KeyStutter`, with a
   `Predicate<ModKey>` in place of a mod id and kind, so `sound-wait`'s stutter form is the mod rules' test by
   construction.
5. **Wording.**
   - Titles: "Sounds keep the game waiting" and "Distant Horizons' far terrain costs a lot to draw".
   - The stutter subject is "Playing create:steam (Blocks)", so it reads "Playing create:steam (Blocks) took 120 ms in
     hitch #1."
   - "Set the Master Volume to 0%" for the master slider, whose caption already says Volume, and "Set the Blocks
     volume to 0%" for the others.
   - Plays a second go through plan 6a's `SoundPlays.perSecond`, as on the Sound plays line: whole from 9.95 up, one
     decimal below, and "under 0.1" for a rate above zero that would read 0.0.
   - A category outside the slider table reads as its own name. On 1.21.1 there is none (verified facts).
6. **The muting claim.** At 0%, `play` skips every sound except one whose instance can start silent, which in
   vanilla is bees and minecarts. The action says "the game then skips those sounds", which is true for every sound
   but those. No sound row says whether its instance can start silent, so the rule cannot single them out.
7. **`Facts.soundMs()` sums the rows' per-frame figures** rather than storing a second copy of `SoundPlays`' total, so
   `FactsBuilder` cannot build facts whose sum and rows disagree.
8. **The bucket test takes the frame's GC pause off the bucket first**, as `unattributed-stall`'s repeat form does
   with its parts. A bucket is not a top contributor, so it carries no `[gc]` mark. Taking the pause off has the
   mark's effect, since a bucket the pause could account for counts nothing, and keeps the rest of a larger one. It
   then counts a hitch at a quarter of it (`Thresholds.SOUND_BUCKET_SHARE`), in `Thresholds.REPEATED_HITCHES` or more
   hitches and `Thresholds.HITCH_REPEAT_SHARE` of them, as `hitch-repeat` does.
9. **One finding, the row test first.** When both stutter tests pass, the row test is named: it names the sound and
   the slider that stops it, and its worst is the row's. The bucket test speaks only when no single row passes.
   Either stutter form outranks the frame-rate form, which then adds its steady figure to the finding, as the mod
   rules do; `msPerFrame` is the steady sound time in every form.
10. **The bucket form's finding names the costliest row when there is one**, with the steady sound time, because its
    action names that row's slider and the reader should see why. With no sound row (the play hook under "did not
    apply", or engine time spent on sounds already playing), the finding is the bucket sentence alone, and the action
    reads "Set the slider for the sounds playing here to 0% in Music & Sounds: ...", with no pack-author sentence.
11. **`Facts.Hitch` keeps a nine-argument constructor** with `soundMs` 0. Only `sound-wait` reads the bucket, and the
    other rules' tests build some thirty hitches in the old form, which stay as they are.
12. **`chunk-rebuilds`' stutter form now also weighs chunk upload against `terrain` and `lod`.** `Facts.hitch` calls
    chunk upload "the largest part of the split" by comparing it with every other render bucket, and phase 6's two
    are render buckets. So a hitch that terrain or LOD drawing leads no longer counts as led by chunk upload. That is
    what the field means; accepted, with no code change.

## After this plan

What is left is the user's:
1. **The pack update.** Bump Distant Horizons from 3.3.1 to 3.3.3 in the pack's update file (`H:\repos\Gears-Frontiers`),
   as §P6.1 left to the user. Renderable's DH targets are the same in 2.4.5-b and 3.3.3, and so
   are the setting names and the button `distant-horizons`' action names.
2. **Push.** Push `main` and `v0.6.0` to the GitHub remote, creating the remote first if it does not exist yet.
3. **CI.** Check the push's CI run on the runner.
4. **The release.** The `v0.6.0` tag builds and attaches `renderable-0.6.0.jar` to a GitHub Release. The workflow's gate
   checks that the tag matches `mod_version`, which is 0.6.0.
5. **The pack module,** if Renderable is to go in the pack: `docs/pack-module.md` points at the 0.6.0 release. Fill in
   its `Size` and `MD5` from the released jar.
