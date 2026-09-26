# Renderable Phase 5c Implementation Plan: ready for players

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Renderable ready to hand to players without rolling it out:
- no other player's name in a report;
- a default key that does not clash with the pack;
- an overhead estimate that covers the hooks' own guards;
- a README section for players;
- the pack module entry, written down;
- the idle cost, finally measured;
- one in-pack acceptance session for all of phase 5.

**Architecture:**

- **Players' names (C3).** `EntityKey.player` gives a player's key no name, or "you" for the local player.
  `KeyFactory` uses it, so a name never reaches the session.
- **The default key (C4)** becomes F6.
- **Calibration (C2).** A new `hooks.CallCalibration` drives whole calls through the static `Probe` methods the
  mixins call, and through an event-timing proxy, against a scratch session. Its three constants feed the
  estimate: a timed pair, a vertex and an attribution-only pair. `core.Calibration` keeps only the timing
  loop, which stays unit-tested with a fake clock.
- **The idle cost (C1)** is measured with Java Flight Recorder in the dev client. Two dev-only Gradle
  properties run the client without the mod (`-PwithoutRenderable`) and pass JVM arguments to the game alone
  (`-PclientJvmArgs`). A committed datapack builds the scene, and a script turns the recordings into P4.A4's
  numbers.
- **Documents.** A "For players" README section, `docs/pack-module.md`, version 0.5.0, and the acceptance
  logs.

**Tech Stack:** Java 21, NeoForge 21.1.220, ModDevGradle 2.0.147 (its run model's `loadedMods` and
`jvmArguments`), Java Flight Recorder and the JDK's `jfr` tool, Python 3 for the numbers script, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md` §P5.C1 to §P5.C7 and §P5.2, and the base
spec sections §P5.3 assigns to plan 5c (§3.4's player names, §3.5's calibration, §5.1's key, §9).

**Repository:** `H:\repos\Renderable`. Create the branch `phase-5c-readiness` from `phase-5d-polish` (plans 5a, 5b
and 5d are complete on it) before Task 1. **Subagents stage and report; they do not commit.** Tasks 7 to 9 need
the user and the orchestrator, not a subagent.

**Ordering:** last (spec addendum §P5.2), because its idle-cost run measures the finished hooks.

**Verified facts you can rely on** (checked 2026-09-26):

- **`EntityKey.label()`** is `typeId + " #" + entityId + (name.isEmpty() ? "" : " (" + name + ")")`. So the name
  "you" reads `minecraft:player #118098 (you)`, as §P5.C3 wants. `serialize()` never includes the name.
- **`KeyFactory.entity`** is the only place a player's name enters a key. Only `TestSessions.PLAYER` in the tests
  carries one ("Steve"), and no assertion reads it.
- **The session's call counters after 5a.** `Session.end()` counts every pair in `timedCalls`, bucket-owning or
  attribution-only (plan 5a Task 3). `vertex()` and `vertices(int)` count `vertexCalls`. `finish()` computes
  `overheadMs` as `(timedCalls * config.calibratedCallNanos() + vertexCalls * config.calibratedVertexNanos())
  / 1_000_000.0`, which 5a does not change.
- **Who calls the calibration code.**
  - `SessionConfig`'s six-argument canonical constructor is called in about fifteen places across
    `src/test` and plans 5a, 5b and 5d.
  - `SessionConfig.ofSeconds` is called by `ProfilerController.start` and by
    `SessionTest.configRejectsNonPositiveDuration`.
  - `Calibration.measureCallNanos` and `measureVertexNanos` are called by `ProfilerController.start` and by
    `CalibrationTest`.
- **ModDevGradle 2.0.147's `RunModel`** has `getLoadedMods()`, a `SetProperty<ModModel>` of the mods a run loads
  (all by default), and `jvmArguments` (javap of `moddev-gradle-2.0.147.jar`).
- **Where the JVM arguments go.** `JAVA_TOOL_OPTIONS` reaches every JVM that Gradle starts, including Gradle's
  own. That is harmless for `-Drenderable.autostart`, but a Flight Recorder option there would record Gradle
  too. So the game gets JVM arguments through the run model.
- **Event timing's classes (plan 5a Task 7).**
  - `TimedListener(EventListener delegate, Accumulator accumulator, EventTimingRun run)` and
    `EventTimingRun(Session session, Thread renderThread)` are package-private in `hooks.events`.
  - Bus 8.0.5 seals `net.neoforged.bus.api.EventListener`, permitting only `ConsumerEventHandler`,
    `GeneratedEventListener` and `SubscribeEventListener`. So plan 5a's `TimedListener` extends
    `GeneratedEventListener`.
  - A do-nothing delegate here is a `net.neoforged.bus.ConsumerEventHandler`. Its constructor takes a
    `Consumer<Event>` and is public; it is what `addListener` registers.
  - `net.neoforged.bus.api.Event` is abstract, not sealed, with a protected no-argument constructor.
- **How the mod shows in the log.** Mixin does not log config names. A dev-client log where Renderable loaded has
  two lines that `grep -cE "Renderable [^ ]+ \(renderable\)|Renderable [^ ]+ loaded"` matches: the mod list's
  `Renderable 0.4.1 (renderable)` and the mod's own `Renderable 0.4.1 loaded`.
- **ModDevGradle's run task.** `runClient` is a `RunGameTask`, whose classpath comes from `classpathProvider`, set
  from the run's source set. FML's userdev locator also loads every classpath root that holds a
  `META-INF/neoforge.mods.toml` no mod folder claims. So a run without the mod must take the mod's output off
  that classpath as well as emptying `loadedMods`.
- **JIT warm-up** (a replica of the post-5c code, benchmarked on JDK 21.0.11). Timing 12,000 calls stays under
  HotSpot's on-stack-replacement threshold, so one round mostly times the interpreter.

  | Call | One round (cold) | Warm |
  | --- | --- | --- |
  | Vertex | 21 to 24 ns | 2 ns |
  | Timed pair | 81 to 87 ns | 50 ns |

  Five rounds, keeping each kind's fastest, gave the warm figures in every JVM tried.
- **The pack's keys.** Its `options.txt` binds `key_key.lighty.enable` and `key_key.renderable.toggle` to F7. F2,
  F5, F7, F8, F9 and F11 are bound in the pack, and F1 and F3 are fixed by vanilla. F6 is free.
- **The pack XML** (`H:\repos\Gears-Frontiers`, read-only here). Modules look like this:
  `<Module name=... id=... depends="" side="CLIENT">` with `<URL priority="1">`, `<Size>`, `<Required>`,
  `<ModType>Regular</ModType>`, `<MD5>` and an optional `<Meta>`. The pack's optional client-only module is
  "ReForgedPlay Mod" in `updates/base.xml`, with `<Required>false</Required>`. The pack revision in progress
  adds its modules in `updates/updates-2026-09-18.xml`, which `GearsAndFrontiers_Preview.xml` imports and
  `GearsAndFrontiers.xml` does not yet.
- **The suite is 207 tests** after 5d.

**Deviations from the spec addendum, recorded here and applied to it in Task 5:**

1. **Calibration also measures `Session` alone and logs it beside the new figures.** §P5.C2's acceptance compares
   the new constants with phase 4's `Session`-only ones. Measuring both in one run on one JVM makes that a
   reading of one log line, not a comparison across machines and months. The report uses only the new ones.
2. **The timed pair is driven through `Probe.beginEntity` on the local player.** That is the busiest real hook,
   and it includes the accumulator lookup and the shadow check each entity render pays. `Probe`'s bucket-only
   calls are cheaper. Calls that also look a key up in a map (particles, HUD layers, packets, payloads) cost a
   few nanoseconds more than the pair or the proxy, which the estimate leaves out.
3. **`SessionConfig` keeps a six-argument constructor** that charges attribution-only pairs at the timed-pair
   cost, so the tests and fixtures that build a config by hand do not change.
4. **The idle-cost protocol runs in the dev client**, as P4.A4's did (`-PquickPlay`), with the pack's rendering
   mods (`-PpackMods`), as spec §9's in-game checks do.
   - The jar-absent runs use the new `-PwithoutRenderable`, which empties `loadedMods` and takes the mod's output
     off the game's classpath.
   - The Flight Recorder option goes through the new `-PclientJvmArgs`.
   - The scene is a committed datapack function rather than typed commands, so the twenty villagers stand
     exactly where the protocol says.
5. **The idle numbers count Renderable's code where it really runs.**
   - **On top, not on the stack.** Mixin merges each handler into the class it hooks, so an idle hook has no
     frame in Renderable's package. A sample counts when its top frame is Renderable's: its package, or a
     `renderable$` handler. P4.A4 said "anywhere on the stack", but a wrapper is on the stack of everything it
     wraps.
   - **The allocation control.** It reads the allocation in Renderable's frames, not the client's total. With
     escape analysis off, the wrappers allocate a few kilobytes a frame, which vanish in the client's megabyte a
     frame, so P4.A4's total-allocation control could not show them. The total comparison is still printed.
   - **A third check.** With escape analysis on, the hooks must allocate at most a tenth of what they do with it
     off.
   - **Dropped.** P4.A4's `jdk.GCHeapSummary` deltas: the allocation samples measure the same thing directly.
6. **Calibration keeps the fastest of five rounds.** One round of 12,000 calls mostly times the interpreter (see
   the facts above), while a session's millions of calls run warm code. The report's estimate is therefore
   much lower than 0.4.x's, which timed cold code: about ten times lower for vertices.

---

## File map

```
gradle.properties                                             Modify: mod_version 0.5.0 (Task 5)
build.gradle                                                  Modify: -PwithoutRenderable, -PclientJvmArgs (Task 4)
README.md                                                     Modify (Tasks 2, 5, 7)
docs/pack-module.md                                           Create (Task 5)
docs/acceptance/phase-5-idle.md                               Create (Task 4); results (Task 7)
docs/acceptance/phase-5-idle-numbers.py                       Create (Task 4)
docs/acceptance/phase-5-idle-scene/pack.mcmeta                Create (Task 4)
docs/acceptance/phase-5-idle-scene/data/renderable_idle/function/scene.mcfunction   Create (Task 4)
docs/acceptance/phase-5.md                                    Create (Task 8)
docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md   Modify (Tasks 2, 5, 7)
docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md        Modify: deviations above (Task 5)
src/main/java/io/github/edwardg117/renderable/
  core/EntityKey.java                                         Modify: player() (Task 1)
  core/Calibration.java                                       Rewrite: the timing loop only (Task 3)
  core/SessionConfig.java                                     Modify: the attribution constant (Task 3)
  core/Session.java                                           Modify: attribution calls in the estimate (Task 3)
  hooks/KeyFactory.java                                       Modify: no player names (Task 1)
  hooks/Probe.java                                            Modify: fired flags saved and restored (Task 3)
  hooks/CallCalibration.java                                  Create (Task 3)
  hooks/events/ProxyCalibration.java                          Create (Task 3)
  ui/Keybinds.java                                            Modify: F6 (Task 2)
  ProfilerController.java                                     Modify: calibration (Task 3)
src/test/java/io/github/edwardg117/renderable/
  core/KeysTest.java                                          Modify (Task 1)
  core/TestSessions.java                                      Modify: PLAYER (Task 1)
  core/CalibrationTest.java                                   Rewrite (Task 3)
  core/SessionTest.java                                       Modify (Task 3)
```

**Expected test totals:** 207 at the start, 208 after Task 1, 208 after Task 2, 209 after Task 3, and 209 at the
end.

**How to run the suite:** `./gradlew test --rerun --no-daemon` (always forced; the build cache can restore stale
results).

---

### Task 1: No other player's name in a report

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/EntityKey.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/KeyFactory.java`
- Modify: `src/test/java/io/github/edwardg117/renderable/core/TestSessions.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/KeysTest.java` (append 1)

Spec addendum §P5.C3.
- **Where names stop.** The rule lives in `core`, so it is unit-tested. `KeyFactory` applies it where keys are
  made, so no name reaches the session, the labels, the report or the JSON.
- **What stays.** Custom names on mobs.

- [ ] **Step 1: Write the failing test**

Append to `KeysTest`:

```java
    @Test
    void aPlayersKeyCarriesNoNameExceptTheLocalPlayersYou() {
        assertEquals("minecraft:player #1234", EntityKey.player("minecraft:player", 1234, false).label());
        assertEquals("minecraft:player #118098 (you)", EntityKey.player("minecraft:player", 118098, true).label());
        assertEquals("minecraft:player#118098", EntityKey.player("minecraft:player", 118098, true).serialize(),
                "the JSON key has no name either way");
    }
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew test --rerun --no-daemon --tests '*KeysTest'`
Expected: compilation failure, `cannot find symbol: method player`.

- [ ] **Step 3: Add `EntityKey.player`**

In `EntityKey.java`, after the record header's opening brace, add:

```java
    /** The only name a player's key carries: the local player's, whose row reads {@code minecraft:player #118098 (you)}. */
    public static final String YOU = "you";

    /**
     * A player's key (spec addendum §P5.C3): no name, so a report never carries another player's name, except that
     * the local player's says {@link #YOU}. Reports hold positions, so players share them privately anyway, but
     * the names of everyone near the player are not theirs to share.
     */
    public static EntityKey player(String typeId, int entityId, boolean local) {
        return new EntityKey(typeId, entityId, local ? YOU : "");
    }
```

- [ ] **Step 4: Use it where keys are made**

In `KeyFactory.java`, add `import net.minecraft.client.Minecraft;` and replace `entity(Entity e)` with:

```java
    static EntityKey entity(Entity e) {
        String typeId = EntityType.getKey(e.getType()).toString();
        if (e instanceof Player) {
            // Spec addendum §P5.C3: never a player's name; the local player is "you". Entity keys are made on the
            // render thread only, so reading Minecraft's player is safe.
            return EntityKey.player(typeId, e.getId(), e == Minecraft.getInstance().player);
        }
        String name = e.hasCustomName() && e.getCustomName() != null ? e.getCustomName().getString() : "";
        return new EntityKey(typeId, e.getId(), name);
    }
```

In the class Javadoc, after "Called once per object per session, so it may allocate.", add " Players' names never
enter a key (spec addendum §P5.C3)."

- [ ] **Step 5: The fixture's player is the local one**

In `TestSessions.java`, replace:

```java
    public static final EntityKey PLAYER = new EntityKey("minecraft:player", 1, "Steve");
```

with:

```java
    public static final EntityKey PLAYER = EntityKey.player("minecraft:player", 1, true);
```

No assertion reads the old name.

- [ ] **Step 6: Run the tests**

Run: `./gradlew compileJava --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **208** tests.

- [ ] **Step 7: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/EntityKey.java src/main/java/io/github/edwardg117/renderable/hooks/KeyFactory.java src/test/java/io/github/edwardg117/renderable/core/KeysTest.java src/test/java/io/github/edwardg117/renderable/core/TestSessions.java
```

Report for the commit: `fix: reports carry no other player's name; the local player reads "you"`.

---

### Task 2: The default key is F6

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/Keybinds.java`
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md` (§5.1)

Spec addendum §P5.C4. The game keeps a saved binding, so only new installs get F6. Nobody has the mod yet, so no
one is affected. There is no unit test: `KeyMapping` is a Minecraft class. Task 8's in-pack check covers it.

- [ ] **Step 1: Change the default**

In `Keybinds.java`, replace `GLFW.GLFW_KEY_F7` with `GLFW.GLFW_KEY_F6`, and in the class Javadoc replace
`F7 starts or stops a session` with `F6 starts or stops a session (F7 until phase 5, which clashed with Lighty
in the pack; spec addendum §P5.C4)`.

- [ ] **Step 2: The README and the spec**

In `README.md`, replace `1. Press **F7** (or run` with `1. Press **F6** (or run`, and after the sentence that
ends `default 30 s.` add ` The key can be changed in Options, Controls, Key Binds, under Renderable.`

In the base spec's §5.1, replace `- Keybind **F7** (rebindable)` with `- Keybind **F6** (rebindable; F7 until
phase 5, which clashed with Lighty's key in the pack)`.

- [ ] **Step 3: Build and check**

Run: `./gradlew compileJava --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **208** tests. Then `grep -rn "GLFW_KEY_F7" src/main` and
`grep -n "\*\*F7\*\*" README.md` print nothing.

- [ ] **Step 4: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/ui/Keybinds.java README.md docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md
```

Report for the commit: `fix: the default key is F6, which the pack leaves free`.

---

### Task 3: Calibration through whole calls

**Files:**
- Rewrite: `src/main/java/io/github/edwardg117/renderable/core/Calibration.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/SessionConfig.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Session.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/Probe.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/CallCalibration.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/events/ProxyCalibration.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/CalibrationTest.java` (rewrite: 3 tests stay 3)
- Test: `src/test/java/io/github/edwardg117/renderable/core/SessionTest.java` (update 1, append 1)

Spec addendum §P5.C2, with deviations 1 to 3 and 6.
- **The core side.** `core` cannot import `Probe`, so it keeps the timing loop, now taking the call to time.
- **The hooks side.** `hooks.CallCalibration` supplies whole calls:
  - a timed pair through `Probe.beginEntity` and `Probe.end`;
  - a vertex through `Probe.vertex`;
  - an attribution-only pair through a real event-timing proxy.
- **Accounting.** The session counts attribution-only pairs apart from the rest, so each kind is charged at its
  own cost.
- **Warm figures.** Each kind is timed in five rounds and keeps its fastest: the first rounds time the JIT, not
  the calls (deviation 6).

- [ ] **Step 1: Write the failing tests**

Replace the whole of `CalibrationTest.java` with:

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

class CalibrationTest {

    @Test
    void aFakeClockGivesTheExactCostOfEachCall() {
        long[] t = {0};
        LongSupplier clock = () -> t[0] += 7;
        // Each call reads the clock twice, 14 ns. The read that ends the loop adds 7 ns over 10,000 calls,
        // which floors away.
        assertEquals(14, Calibration.nanosPerCall(clock, () -> {
            clock.getAsLong();
            clock.getAsLong();
        }));
    }

    @Test
    void aClockThatGoesBackwardsIsClampedAndACallThatReadsNothingIsFree() {
        long[] backwards = {0};
        assertEquals(0, Calibration.nanosPerCall(() -> backwards[0] -= 1_000_000, () -> {
        }), "a clock that goes backwards is clamped, not reported as negative");
        long[] forwards = {0};
        assertEquals(0, Calibration.nanosPerCall(() -> forwards[0] += 3, () -> {
        }), "no reads inside the loop, so the two around it floor to zero");
    }

    @Test
    void theRealClockGivesASaneNumberForARealSessionPair() {
        Session s = new Session(new SessionConfig(Long.MAX_VALUE, 2.0, 0, 5, 0, 0), System::nanoTime, 0);
        Accumulator a = s.accumulator(new EntityKey("renderable:calibration", 0, ""));
        s.onRenderPre(0, 0);
        long perPair = Calibration.nanosPerCall(System::nanoTime, () -> {
            s.begin(a, Bucket.ENTITY);
            s.end();
        });
        assertTrue(perPair >= 0 && perPair < 10_000, "per-pair cost was " + perPair + " ns");
    }
}
```

In `SessionTest.configRejectsNonPositiveDuration`, replace:

```java
        SessionConfig c = SessionConfig.ofSeconds(30, 2.0, 16, 5, 25, 2);
```

with:

```java
        SessionConfig c = SessionConfig.ofSeconds(30, 2.0, 16, 5, 25, 2, 40);
        assertEquals(40, c.calibratedAttributionNanos());
        assertEquals(25, new SessionConfig(1, 2.0, 0, 5, 25, 2).calibratedAttributionNanos(),
                "without its own figure, an attribution-only pair costs what a timed pair does");
```

and append to `SessionTest`:

```java
    @Test
    void theOverheadEstimateChargesEachKindOfCallItsOwnCost() {
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 10, 1, 100), clock, 1_700_000_000_000L);
        at(0); s.onRenderPre(0, 0);
        Accumulator a = s.accumulator(press);
        Accumulator handler = s.accumulator(TestSessions.STAGE_HANDLER);
        s.begin(a, Bucket.BLOCK_ENTITY);
        s.vertex();
        s.vertex();
        s.vertex();
        s.end();
        s.beginAttribution(handler);
        s.end();
        s.onRenderPost();
        at(16); s.onRenderPre(0, 0);
        SessionResult r = s.finish();

        assertEquals(2, r.timedCalls(), "both pairs are timed calls");
        assertEquals(1, s.attributionCalls(), "one of them attribution-only");
        assertEquals(3, r.vertexCalls());
        assertEquals((1 * 10 + 1 * 100 + 3 * 1) / 1e6, r.overheadEstimateMs(), 1e-12);
    }
```

(`SessionTest` has its own `MS`, `clock`, `at(long)` and `press`; `TestSessions.STAGE_HANDLER` is plan 5a's.)

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*CalibrationTest' --tests '*SessionTest'`
Expected: compilation failure (`Calibration.nanosPerCall`, the seven-argument `SessionConfig` and
`Session.attributionCalls` do not exist).

- [ ] **Step 3: The timing loop**

Replace the whole of `core/Calibration.java` with:

```java
package io.github.edwardg117.renderable.core;

import java.util.function.LongSupplier;

/**
 * Spec §3.5 and spec addendum §P5.C2: the timing loop behind the report's overhead estimate. The calls it times
 * come from {@code hooks.CallCalibration}, which drives the same static {@code Probe} methods the mixins call,
 * guards included; this class has no Minecraft imports, so the loop is tested here with a fake clock.
 */
public final class Calibration {
    private static final int WARMUP = 2_000;
    private static final int SAMPLES = 10_000;

    private Calibration() {
    }

    /**
     * Runs {@code call} 2,000 times to warm it up, then times 10,000 runs, and returns the nanoseconds one run
     * took, never negative. The loop's own call through {@code Runnable} is in the figure, a nanosecond or two, so
     * the estimate errs high, never low.
     */
    public static long nanosPerCall(LongSupplier clock, Runnable call) {
        for (int i = 0; i < WARMUP; i++) call.run();
        long start = clock.getAsLong();
        for (int i = 0; i < SAMPLES; i++) call.run();
        long elapsed = clock.getAsLong() - start;
        return Math.max(0, elapsed / SAMPLES);
    }
}
```

- [ ] **Step 4: The third constant**

In `SessionConfig.java`:

1. In the Javadoc, replace the six `@param` lines with these, realigned for the longer new name:

```java
 * @param durationNanos              how long to sample
 * @param hitchMedianMultiplier      spec §3.3, default 2.0
 * @param hitchMinExcessNanos        spec §3.3, default 16 ms
 * @param topContributorsPerFrame    keys kept per frame, default 5
 * @param calibratedCallNanos        measured cost of one timed begin/end pair through {@code Probe}, guard included
 * @param calibratedVertexNanos      measured cost of one vertex-counting call through {@code Probe} (spec addendum §P4.A5)
 * @param calibratedAttributionNanos measured cost of one attribution-only pair: an event handler's timing proxy,
 *                                   which also stands for a HUD layer's and a packet's (spec addendum §P5.C2)
```

2. Replace the record header:

```java
public record SessionConfig(long durationNanos,
                            double hitchMedianMultiplier,
                            long hitchMinExcessNanos,
                            int topContributorsPerFrame,
                            long calibratedCallNanos,
                            long calibratedVertexNanos) {
```

   with:

```java
public record SessionConfig(long durationNanos,
                            double hitchMedianMultiplier,
                            long hitchMinExcessNanos,
                            int topContributorsPerFrame,
                            long calibratedCallNanos,
                            long calibratedVertexNanos,
                            long calibratedAttributionNanos) {
```

3. After the compact constructor, add:

```java
    /**
     * Settings with no figure of their own for attribution-only pairs, which are then charged what a timed pair
     * costs. For tests and fixtures that build a config by hand.
     */
    public SessionConfig(long durationNanos, double hitchMedianMultiplier, long hitchMinExcessNanos,
                         int topContributorsPerFrame, long calibratedCallNanos, long calibratedVertexNanos) {
        this(durationNanos, hitchMedianMultiplier, hitchMinExcessNanos, topContributorsPerFrame, calibratedCallNanos,
                calibratedVertexNanos, calibratedCallNanos);
    }
```

4. Replace `ofSeconds` with:

```java
    public static SessionConfig ofSeconds(int seconds, double hitchMedianMultiplier, int hitchMinExcessMs,
                                          int topContributorsPerFrame, long calibratedCallNanos,
                                          long calibratedVertexNanos, long calibratedAttributionNanos) {
        return new SessionConfig(seconds * 1_000_000_000L, hitchMedianMultiplier, hitchMinExcessMs * 1_000_000L,
                topContributorsPerFrame, calibratedCallNanos, calibratedVertexNanos, calibratedAttributionNanos);
    }
```

- [ ] **Step 5: Count attribution-only pairs**

In `Session.java`:

1. After the field `private long vertexCalls;`, add:

```java
    /** Of {@link #timedCalls}, the attribution-only pairs, which the estimate charges at their own cost. */
    private long attributionCalls;
```

2. After the `vertexCalls()` accessor, add:

```java
    /** Of {@link #timedCalls()}, the attribution-only pairs (spec addendum §P5.C2). */
    public long attributionCalls() {
        return attributionCalls;
    }
```

3. In `end()`, replace:

```java
        timedCalls++;
        boolean ownsBucket = bucket != null;
```

   with:

```java
        timedCalls++;
        boolean ownsBucket = bucket != null;
        if (!ownsBucket) attributionCalls++;
```

4. In `finish()`, replace:

```java
        double overheadMs = (timedCalls * config.calibratedCallNanos()
                + vertexCalls * config.calibratedVertexNanos()) / 1_000_000.0;
```

   with:

```java
        // Spec addendum §P5.C2: each kind of call at its own measured cost.
        double overheadMs = ((timedCalls - attributionCalls) * config.calibratedCallNanos()
                + attributionCalls * config.calibratedAttributionNanos()
                + vertexCalls * config.calibratedVertexNanos()) / 1_000_000.0;
```

- [ ] **Step 6: Keep calibration from marking hooks as fired**

In `Probe.java`, after `markFired(Hook)` (plan 5a's), add:

```java
    /** The fired-hook flags at one moment (spec addendum §P5.C2). */
    record Fired(boolean[] seen, boolean seenVertices, boolean seenAcceleratedVertices) {
    }

    /**
     * A copy of the fired-hook flags, which calibration puts back when it is done, so driving calls through
     * {@code Probe} marks no hook as fired. Render thread only, like every write to them.
     */
    static Fired fired() {
        return new Fired(SEEN.clone(), seenVertices, seenAcceleratedVertices);
    }

    /** Puts the fired-hook flags back as {@link #fired()} found them. Render thread only. */
    static void restore(Fired f) {
        System.arraycopy(f.seen(), 0, SEEN, 0, SEEN.length);
        seenVertices = f.seenVertices();
        seenAcceleratedVertices = f.seenAcceleratedVertices();
    }
```

- [ ] **Step 7: The proxy's cost**

Create `hooks/events/ProxyCalibration.java`:

```java
package io.github.edwardg117.renderable.hooks.events;

import io.github.edwardg117.renderable.RenderableMod;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.Session;
import net.neoforged.bus.ConsumerEventHandler;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventListener;

/**
 * One attribution-only pair to time (spec addendum §P5.C2): a timing proxy around a listener that does nothing,
 * invoked as the bus invokes it. HUD layers and packets pay the same begin and end, plus a map lookup the proxy
 * does not. Render thread only, against calibration's scratch session.
 */
public final class ProxyCalibration {

    private ProxyCalibration() {
    }

    /**
     * A call of the proxy. The delegate is a {@code ConsumerEventHandler}, the listener {@code addListener}
     * registers: bus 8.0.5 seals {@code EventListener}, so no class of ours can be one.
     */
    public static Runnable invocation(Session scratch) {
        EventListener nothing = new ConsumerEventHandler(event -> {
        });
        TimedListener proxy = new TimedListener(nothing,
                scratch.accumulator(ModKey.event(RenderableMod.MOD_ID, "calibration")),
                new EventTimingRun(scratch, Thread.currentThread()));
        Event event = new Event() {
        };
        return () -> proxy.invoke(event);
    }
}
```

- [ ] **Step 8: The whole-call calibration**

Create `hooks/CallCalibration.java`:

```java
package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Calibration;
import io.github.edwardg117.renderable.core.EntityKey;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.core.SessionConfig;
import io.github.edwardg117.renderable.hooks.events.ProxyCalibration;
import net.minecraft.world.entity.Entity;

/**
 * Measures what Renderable's own calls cost, for the report's overhead estimate (spec §3.5, spec addendum §P5.C2).
 * Phase 4 timed {@code Session} alone. This drives whole calls through the same static {@link Probe} methods the
 * mixins call, and through an event-timing proxy, so each figure includes the guard every hook pays first: a
 * volatile read, a thread comparison and a branch. It runs once, before the first real session, against a scratch
 * session attached for the duration. Anything other threads record into it is thrown away with it, and the
 * fired-hook flags are put back, so calibrating marks no hook as fired. Render thread only.
 */
public final class CallCalibration {
    /** Rounds of each measurement. The first rounds time the JIT, not the calls, so each kind keeps its fastest. */
    private static final int ROUNDS = 5;

    /**
     * Nanoseconds per call: a timed begin/end pair, a vertex and an attribution-only pair, which the report uses;
     * and {@code Session} alone for a pair and a vertex, phase 4's way, which the log shows beside them so the
     * guard's cost can be read off (plan 5c, deviation 1).
     */
    public record Costs(long timedPair, long vertex, long attributionPair, long sessionPair, long sessionVertex) {
    }

    private CallCalibration() {
    }

    /** {@code sample} is the entity whose render call is timed, the local player; {@code dimension} names its level. */
    public static Costs measure(Entity sample, String dimension) {
        Probe.Fired fired = Probe.fired();
        Session scratch = new Session(new SessionConfig(Long.MAX_VALUE, 2.0, 0, 5, 0, 0), System::nanoTime, 0);
        Probe.attach(scratch, dimension);
        try {
            // Load-bearing: an open frame makes end() do its in-frame work, as it does in a real frame.
            scratch.onRenderPre(0, 0);
            Runnable pairCall = () -> {
                if (Probe.ACTIVE) {
                    Probe.beginEntity(sample);
                    Probe.end();
                }
            };
            Runnable vertexCall = () -> {
                if (Probe.ACTIVE) Probe.vertex();
            };
            Runnable proxyCall = ProxyCalibration.invocation(scratch);
            Accumulator a = scratch.accumulator(new EntityKey("renderable:calibration", 0, ""));
            Runnable sessionPairCall = () -> {
                scratch.begin(a, Bucket.ENTITY);
                scratch.end();
            };
            Runnable sessionVertexCall = scratch::vertex;

            long pair = Long.MAX_VALUE;
            long vertex = Long.MAX_VALUE;
            long attribution = Long.MAX_VALUE;
            long sessionPair = Long.MAX_VALUE;
            long sessionVertex = Long.MAX_VALUE;
            for (int round = 0; round < ROUNDS; round++) {
                pair = Math.min(pair, Calibration.nanosPerCall(System::nanoTime, pairCall));
                Probe.beginEntity(sample);   // a vertex lands on an open keyed call, as in a real frame
                try {
                    vertex = Math.min(vertex, Calibration.nanosPerCall(System::nanoTime, vertexCall));
                } finally {
                    Probe.end();
                }
                attribution = Math.min(attribution, Calibration.nanosPerCall(System::nanoTime, proxyCall));
                sessionPair = Math.min(sessionPair, Calibration.nanosPerCall(System::nanoTime, sessionPairCall));
                scratch.begin(a, Bucket.ENTITY);
                try {
                    sessionVertex = Math.min(sessionVertex, Calibration.nanosPerCall(System::nanoTime, sessionVertexCall));
                } finally {
                    scratch.end();
                }
            }
            return new Costs(pair, vertex, attribution, sessionPair, sessionVertex);
        } finally {
            Probe.detach();
            Probe.restore(fired);
        }
    }
}
```

Five rounds of five kinds of 12,000 calls take about 15 ms, once, before the first session attaches, so no
session records them.

- [ ] **Step 9: Calibrate from the controller**

In `ProfilerController.java`:

1. Replace `import io.github.edwardg117.renderable.core.Calibration;` with
   `import io.github.edwardg117.renderable.hooks.CallCalibration;`.
2. After the field `private long calibratedVertexNanos = -1;`, add
   `private long calibratedAttributionNanos = -1;`.
3. In `start`, replace:

```java
        if (calibratedCallNanos < 0) {
            calibratedCallNanos = Calibration.measureCallNanos(System::nanoTime);
            calibratedVertexNanos = Calibration.measureVertexNanos(System::nanoTime);
        }
        SessionConfig config = SessionConfig.ofSeconds(seconds,
                RenderableConfig.HITCH_MEDIAN_MULTIPLIER.get(),
                RenderableConfig.HITCH_MIN_EXCESS_MS.get(),
                RenderableConfig.TOP_CONTRIBUTORS_PER_FRAME.get(),
                calibratedCallNanos,
                calibratedVertexNanos);
```

   with:

```java
        // Once, before the first real session (spec addendum §P5.C2). The player is there whenever the level is;
        // if it ever is not, this session charges nothing and the next one calibrates.
        if (calibratedCallNanos < 0 && mc.player != null) {
            try {
                CallCalibration.Costs c = CallCalibration.measure(mc.player, mc.level.dimension().location().toString());
                calibratedCallNanos = c.timedPair();
                calibratedVertexNanos = c.vertex();
                calibratedAttributionNanos = c.attributionPair();
                RenderableMod.LOGGER.info("Renderable calibrated its calls: a timed pair {} ns (Session alone {} ns),"
                                + " a vertex {} ns (Session alone {} ns), an attribution-only pair {} ns",
                        c.timedPair(), c.sessionPair(), c.vertex(), c.sessionVertex(), c.attributionPair());
            } catch (RuntimeException | LinkageError e) {
                // This runs inside a tick listener, where a throw would end the client. Charge nothing instead, for
                // this session and every later one, rather than fail again on each start.
                calibratedCallNanos = 0;
                calibratedVertexNanos = 0;
                calibratedAttributionNanos = 0;
                RenderableMod.LOGGER.warn("Renderable could not calibrate its calls; its overhead estimate will read 0", e);
            }
        }
        SessionConfig config = SessionConfig.ofSeconds(seconds,
                RenderableConfig.HITCH_MEDIAN_MULTIPLIER.get(),
                RenderableConfig.HITCH_MIN_EXCESS_MS.get(),
                RenderableConfig.TOP_CONTRIBUTORS_PER_FRAME.get(),
                Math.max(0, calibratedCallNanos),
                Math.max(0, calibratedVertexNanos),
                Math.max(0, calibratedAttributionNanos));
```

- [ ] **Step 10: Run the tests**

Run: `./gradlew compileJava --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **209** tests. Then `grep -rn "measureCallNanos\|measureVertexNanos" src` prints
nothing.

- [ ] **Step 11: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/Calibration.java src/main/java/io/github/edwardg117/renderable/core/SessionConfig.java src/main/java/io/github/edwardg117/renderable/core/Session.java src/main/java/io/github/edwardg117/renderable/hooks/Probe.java src/main/java/io/github/edwardg117/renderable/hooks/CallCalibration.java src/main/java/io/github/edwardg117/renderable/hooks/events/ProxyCalibration.java src/main/java/io/github/edwardg117/renderable/ProfilerController.java src/test/java/io/github/edwardg117/renderable/core/CalibrationTest.java src/test/java/io/github/edwardg117/renderable/core/SessionTest.java
```

Report for the commit: `feat: calibration drives whole calls through Probe, with a third constant for attribution-only pairs`.

---

### Task 4: The idle-cost protocol

**Files:**
- Modify: `build.gradle`
- Create: `docs/acceptance/phase-5-idle-scene/pack.mcmeta`
- Create: `docs/acceptance/phase-5-idle-scene/data/renderable_idle/function/scene.mcfunction`
- Create: `docs/acceptance/phase-5-idle-numbers.py`
- Create: `docs/acceptance/phase-5-idle.md`

Spec §9 check 5, and spec addendum §P4.A4 and §P5.C1, with deviations 4 and 5.
- **What this task writes.** Everything the user needs to run the check: two dev-only run properties, the scene
  as a datapack, the numbers script and the protocol.
- **Who runs it.** The user, in Task 7, after the dev client has proved the pieces in Task 6.
- **The scene.** P4.A4's, plus twenty villagers with `NoAI` in view, so the entity render and tick hooks run
  every frame. It uses the pack's rendering mods (`-PpackMods`), as spec §9's in-game checks do, so Sodium's
  hooks are measured too.
- **How Renderable shows in a recording.** Mixin merges each handler into the class it hooks, under a name that
  keeps the handler's own, and every handler's name starts `renderable$`. An idle hook runs only that handler,
  and a wrapper is on the stack of everything it wraps. So a sample is Renderable's when its top frame is a
  class in Renderable's package or a `renderable$` method, not when such a frame is anywhere on the stack. The
  numbers script counts it that way (deviation 5).

- [ ] **Step 1: Two dev-only run properties**

In `build.gradle`, in `runs { client { ... } }`, after the `quickPlay` block's closing brace, add:

```groovy

            // Dev only, for the idle-cost protocol (docs/acceptance/phase-5-idle.md). -PwithoutRenderable runs the
            // same client without this mod (runClient below does the rest). -PclientJvmArgs="..." adds JVM arguments
            // to the game alone, such as a Flight Recorder recording; JAVA_TOOL_OPTIONS would reach Gradle's own JVMs
            // too, and they would record as well.
            if (project.hasProperty('withoutRenderable')) {
                loadedMods.set([])
            }
            if (project.hasProperty('clientJvmArgs')) {
                jvmArguments.addAll(project.property('clientJvmArgs').toString().trim().split('\\s+').toList())
            }
```

and replace:

```groovy
tasks.named('runClient') {
    dependsOn installPackMods
}
```

with:

```groovy
tasks.named('runClient') {
    dependsOn installPackMods
    // -PwithoutRenderable: an empty loadedMods only empties fml.modFolders. FML's userdev locator also scans the
    // classpath for META-INF/neoforge.mods.toml and would load build/resources/main as a class-less "renderable"
    // mod with both mixin configs, so the mod's own output comes off the game's classpath as well.
    if (project.hasProperty('withoutRenderable')) {
        classpathProvider.setFrom(sourceSets.main.runtimeClasspath.minus(sourceSets.main.output))
    }
}
```

(ModDevGradle's `RunGameTask`, which `runClient` is, takes its classpath from `classpathProvider`, set from the
run's source set by default; this configure action runs after the plugin's, so `setFrom` replaces it.)

Run:

```bash
./gradlew prepareClientRun -PclientJvmArgs="-Dx=1 -Dy=2" --no-daemon
grep -c -- "^-D[xy]=" build/moddev/clientRunVmArgs.txt
./gradlew prepareClientRun -PwithoutRenderable --no-daemon
grep -c "renderable%%" build/moddev/clientRunVmArgs.txt
```

Expected: BUILD SUCCESSFUL twice. The first `grep` prints 2. The second prints 0: no mod folder for Renderable.
`prepareClientRun` also syncs `run/mods`, emptying it here; the next `-PpackMods` run puts the pack mods back.
Task 6 proves the classpath half in the game.

- [ ] **Step 2: The scene datapack**

`docs/acceptance/phase-5-idle-scene/pack.mcmeta`:

```json
{
  "pack": {
    "pack_format": 48,
    "description": "Renderable idle-cost scene (docs/acceptance/phase-5-idle.md)"
  }
}
```

`docs/acceptance/phase-5-idle-scene/data/renderable_idle/function/scene.mcfunction`:

```mcfunction
# Renderable's idle-cost scene (spec addendum §P4.A4 and §P5.C1). Run once, as the player, in a Classic Flat
# world whose surface is y -60: /function renderable_idle:scene
gamerule doDaylightCycle false
gamerule doMobSpawning false
gamerule doWeatherCycle false
time set noon
weather clear
kill @e[type=!player]
gamemode spectator @s
tp @s 0 -60 0 180 0
# Twenty villagers that never move, 5 across and 4 deep, 6 to 12 blocks in front of the camera (facing north).
summon minecraft:villager -4 -60 -6 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager -2 -60 -6 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 0 -60 -6 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 2 -60 -6 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 4 -60 -6 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager -4 -60 -8 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager -2 -60 -8 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 0 -60 -8 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 2 -60 -8 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 4 -60 -8 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager -4 -60 -10 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager -2 -60 -10 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 0 -60 -10 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 2 -60 -10 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 4 -60 -10 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager -4 -60 -12 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager -2 -60 -12 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 0 -60 -12 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 2 -60 -12 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 4 -60 -12 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
```

(Pack format 48 is Minecraft 1.21.1's data pack format; since 1.21 the folder is `function`, singular.)

- [ ] **Step 3: The numbers script**

`docs/acceptance/phase-5-idle-numbers.py`:

```python
"""Turns the idle-cost recordings into the numbers of phase-5-idle.md (spec §9 check 5, spec addendum §P5.C1).

Usage:
    python docs/acceptance/phase-5-idle-numbers.py run1.jfr run2.jfr run3.jfr run4.jfr
    python docs/acceptance/phase-5-idle-numbers.py one.jfr        (a single recording: just its numbers)

Needs the JDK's jfr tool, on the PATH or under JAVA_HOME/bin.

Renderable's code shows in a recording in two ways: classes in its own package, and the mixin handlers Mixin
merges into the classes they hook, which keep their names, all starting "renderable$". A sample is Renderable's
when its top frame is either. An idle hook runs only its handler, and a wrapper handler is on the stack of
everything it wraps, so "anywhere on the stack" would count the wrapped game code too.
"""
import json
import os
import shutil
import subprocess
import sys

PACKAGE = ("io.github.edwardg117.renderable", "io/github/edwardg117/renderable")
HANDLER = "renderable$"


def jfr_tool():
    found = shutil.which("jfr")
    if found:
        return found
    home = os.environ.get("JAVA_HOME")
    if home:
        for name in ("jfr", "jfr.exe"):
            path = os.path.join(home, "bin", name)
            if os.path.exists(path):
                return path
    sys.exit("Cannot find the JDK's jfr tool: put it on the PATH or set JAVA_HOME")


def events(recording, kind, depth):
    """One event type, with stack traces cut to depth frames; whole traces would run to hundreds of megabytes."""
    out = subprocess.run([jfr_tool(), "print", "--json", "--stack-depth", str(depth), "--events", kind, recording],
                         capture_output=True, text=True, encoding="utf-8", check=True).stdout
    return json.loads(out)["recording"]["events"]


def frames(event):
    return (event["values"].get("stackTrace") or {}).get("frames") or []


def ours(frame):
    method = frame.get("method") or {}
    cls = (method.get("type") or {}).get("name") or ""
    return cls.startswith(PACKAGE) or HANDLER in (method.get("name") or "")


def numbers(recording):
    allocations = events(recording, "jdk.ObjectAllocationSample", 3)
    loads = [e["values"]["jvmUser"] + e["values"]["jvmSystem"] for e in events(recording, "jdk.CPULoad", 0)]
    samples = events(recording, "jdk.ExecutionSample", 1)
    return {
        "allocatedMiB": sum(e["values"]["weight"] for e in allocations) / 1048576,
        # Allocation whose site, or one of the two calls above it, is Renderable's. A lower bound: MixinExtras
        # builds part of a wrapper's arguments in the hooked method itself.
        "oursKiB": sum(e["values"]["weight"] for e in allocations if any(ours(f) for f in frames(e))) / 1024,
        "meanCpu": sum(loads) / len(loads) if loads else float("nan"),
        "samples": len(samples),
        "ours": sum(1 for s in samples if frames(s) and ours(frames(s)[0])),
    }


def show(label, n):
    share = 100.0 * n["ours"] / n["samples"] if n["samples"] else float("nan")
    print(f"{label}: allocated {n['allocatedMiB']:.1f} MiB ({n['oursKiB']:.1f} KiB in Renderable's frames), "
          f"mean CPU {100 * n['meanCpu']:.2f}%, {n['samples']} execution samples, "
          f"{n['ours']} with Renderable on top ({share:.3f}%)")


def change(before, after):
    return 100.0 * (after - before) / before


def main(paths):
    if len(paths) == 1:
        show(paths[0], numbers(paths[0]))
        return
    if len(paths) != 4:
        sys.exit(__doc__)
    runs = [numbers(p) for p in paths]
    for i, (path, n) in enumerate(zip(paths, runs), 1):
        show(f"run {i} ({path})", n)
    allocation = change(runs[0]["allocatedMiB"], runs[1]["allocatedMiB"])
    cpu = change(runs[0]["meanCpu"], runs[1]["meanCpu"])
    share = 100.0 * runs[1]["ours"] / runs[1]["samples"]
    passed = abs(allocation) <= 2 and abs(cpu) <= 2 and share < 0.1
    sensitive = runs[3]["oursKiB"] > 0
    removed = runs[1]["oursKiB"] <= 0.1 * runs[3]["oursKiB"]
    total_control = change(runs[2]["allocatedMiB"], runs[3]["allocatedMiB"])
    print()
    print(f"1. Run 2 against run 1: allocation {allocation:+.2f}%, mean CPU {cpu:+.2f}% (each within 2%);"
          f" Renderable on top of {share:.3f}% of run 2's samples (under 0.1%): {'PASS' if passed else 'FAIL'}")
    print(f"2. The control: with escape analysis off, run 4 allocated {runs[3]['oursKiB']:.1f} KiB in Renderable's"
          f" frames (must be above zero): {'sensitive' if sensitive else 'NOT sensitive, so check 3 means nothing'}."
          f" Total allocation, run 4 against run 3, for context: {total_control:+.2f}%.")
    print(f"3. With escape analysis on, run 2 allocated {runs[1]['oursKiB']:.1f} KiB in Renderable's frames"
          f" (at most a tenth of run 4's): {'PASS' if removed else 'FAIL'}")


if __name__ == "__main__":
    main(sys.argv[1:])
```

- [ ] **Step 4: The protocol**

`docs/acceptance/phase-5-idle.md`:

````markdown
# Phase 5 idle cost (spec §9 check 5)

The claim under test: when Renderable is not sampling, each of its hooks costs one volatile read, and nothing is
allocated. Phase 1 could not measure it from F3, because frame time at the pack's base moves more than the
effect. Phase 4 wrote this protocol (spec addendum §P4.A4) and did not run it. Phase 5 adds twenty villagers
with `NoAI` in view, so the entity render and tick hooks, the busiest, run in every frame (§P5.C1), and runs
with the pack's rendering mods, so Sodium's hooks are measured too.

**Status:** protocol written; the runs are in the Results section once the user has done them.

## Once: the scene

1. Start the dev client, `./gradlew runClient -PpackMods --no-daemon`, and create a singleplayer world named
   `RenderableIdle`: World Type Superflat (the default Classic Flat preset), Creative, Allow Commands on. Then
   Save and Quit to Title, and close the game.
2. Copy the scene datapack into the world:
   `cp -r docs/acceptance/phase-5-idle-scene run/saves/RenderableIdle/datapacks/`
3. Start `./gradlew runClient -PquickPlay="RenderableIdle" -PpackMods --no-daemon`, run
   `/function renderable_idle:scene`, and check that twenty villagers stand still in view, five across and four
   deep. Then Save and Quit.
   The function sets the game rules (no daylight cycle, no mob spawning, no weather cycle), noon and clear
   weather. It kills every other entity, summons the villagers with `NoAI`, and puts the player in spectator
   at `0 -60 0` facing north. The save keeps all of it.
4. In Video Settings, set render distance 8, simulation distance 5, Max Framerate 60, VSync off, graphics
   Fast and GUI scale 2. Close the game.
5. With the game closed, edit `run/options.txt`:
   - `pauseOnLostFocus:false`, so a run that loses focus does not pause mid-recording;
   - `overrideWidth:1280` and `overrideHeight:720`, for the window size, which Video Settings cannot set.

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
is a fault in `-PwithoutRenderable`: stop and report it.

## The numbers

```
python docs/acceptance/phase-5-idle-numbers.py run/idle-run1.jfr run/idle-run2.jfr run/idle-run3.jfr run/idle-run4.jfr
```

For each run the script prints:
- **allocation:** the summed weight of `jdk.ObjectAllocationSample`, and the part whose site, or one of the two
  calls above it, is Renderable's code;
- **mean CPU load:** `jdk.CPULoad` `jvmUser + jvmSystem`;
- **execution samples:** how many, and how many have Renderable's code on top, in its package or a mixin
  handler named `renderable$...`.

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
  samples. `jfr view hot-methods run/idle-run2.jfr` shows whether any `renderable$` method does more than that.
- **Noise.** Mean CPU load from one run each has no noise estimate. If it lands between 2% and 4%, record runs 1
  and 2 once more each and report the spread.

**If a check fails,** `jfr view hot-methods run/idle-run2.jfr` and `jfr view allocation-by-site run/idle-run2.jfr`
name the code: look for `renderable$` or `io.github.edwardg117.renderable`. A failure is a defect to fix before
rollout. Event timing cannot be the cause: none of it exists outside a session.

## Results

Not run yet.
````

- [ ] **Step 5: Check the script against itself**

Run: `python -c "import ast, pathlib; ast.parse(pathlib.Path('docs/acceptance/phase-5-idle-numbers.py').read_text(encoding='utf-8')); print('ok')"`
Expected: `ok`. Task 6 runs it on a real recording.

- [ ] **Step 6: Stage**

```bash
git add build.gradle docs/acceptance/phase-5-idle.md docs/acceptance/phase-5-idle-numbers.py docs/acceptance/phase-5-idle-scene
```

Report for the commit: `docs: the idle-cost protocol, its scene and its numbers script`.

---

### Task 5: For players and for the pack

**Files:**
- Modify: `README.md`
- Create: `docs/pack-module.md`
- Modify: `gradle.properties`
- Modify: `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md` (§3.4, §3.5, §9)
- Modify: `docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md` (the deviations above)

Spec addendum §P5.C5, §P5.C6, §P5.2's version and the base spec amendments §P5.3 gives plan 5c. Each replacement
quotes the text to find. If a passage is not there, stop and report rather than guess.

- [ ] **Step 1: The README's "For players" section**

In `README.md`, insert before `## Using it`:

```markdown
## For players

Renderable finds what is costing your frame rate: which machines, entities, particles and mods take the time,
and what caused each stutter. It changes nothing in the game.

1. Go where the game feels slow, press **F6**, and keep playing normally for 30 seconds. The key can be changed
   in Options, Controls, Key Binds, under Renderable.
2. When it finishes, a results screen opens on the **Advice** tab: what to change, drawn from what was measured.
   Suggestions under **For you** are yours to try; those under **For the pack author** are for whoever maintains
   the pack.
3. To ask for help, send the pack author the text report: **Copy report** puts it on the clipboard, and the
   files are in `<game dir>/renderable/`. Send it privately: it holds your position in the world. It holds no
   player's name.
4. Coloured labels over machines show what the last session measured. `/renderable overlay off` hides them
   until you turn them back on.

When it is not sampling, each of its hooks checks one flag and returns.
```

- [ ] **Step 2: The pack module entry**

Create `docs/pack-module.md`:

````markdown
# Adding Renderable to Gears & Frontiers

Renderable is client-only and optional. This is the MCUpdater module entry the pack author would add. It follows
the pack's existing optional client-only module, ReForgedPlay Mod in `updates/base.xml`. It is written down
here and not applied: phase 5 leaves the pack repository alone (spec addendum §P5.C6).

```xml
		<Module name="Renderable" id="renderable" depends="" side="CLIENT">
			<URL priority="1">https://github.com/edwardg117/Renderable/releases/download/v0.5.0/renderable-0.5.0.jar</URL>
			<Size>0</Size>
			<Required>false</Required>
			<ModType>Regular</ModType>
			<MD5>0</MD5>
			<Meta>
				<license>MIT</license>
				<description>Client render profiler: shows which block entities, entities and effects cost frame time and what caused each hitch.</description>
				<version>0.5.0</version>
				<authors>edwardg117</authors>
			</Meta>
		</Module>
```

Before adding it, fill in three things:

- **`Size` and `MD5`**, the released jar's byte count and MD5. For example, on the downloaded jar:
  `stat -c %s renderable-0.5.0.jar` and `md5sum renderable-0.5.0.jar`, or `certutil -hashfile renderable-0.5.0.jar MD5`
  on Windows.
- **The URL**, if the repository is not `github.com/edwardg117/Renderable` or the tag is not `v0.5.0`. It must be
  the release asset's download link, not the release page.

**Where it goes.** In the update file of the pack revision that ships it. At the time of writing that is
`updates/updates-2026-09-18.xml`, which `GearsAndFrontiers_Preview.xml` imports and `GearsAndFrontiers.xml` does
not yet.

**Optional, unticked.** `<Required>false</Required>` makes it a module each player ticks in MCUpdater if they
want it. The pack also uses `<Required isDefault="true">false</Required>` for optional modules that start ticked.
A profiler is for when something is slow, so it starts unticked.

**Side.** `CLIENT`: the server never needs it, and a server that has it ignores it.
````

- [ ] **Step 3: The version**

In `gradle.properties`, change `mod_version=0.4.1` to `mod_version=0.5.0`.

- [ ] **Step 4: The base spec**

1. In §3.4's key table, replace `entity type id, entity id, display name for report` with `entity type id, entity
   id; a mob's custom name for the report, "you" for the local player, and never another player's name (phase 5)`.
2. In §3.5, replace:

```markdown
  0.5 ms per frame while sampling. The report states the session's own estimated overhead:
  `timedCalls * calibratedCallNanos + vertexCalls * calibratedVertexNanos`. Each constant is
  calibrated at session start — `calibratedCallNanos` by timing a loop of 10,000 empty begin/end
  pairs, `calibratedVertexNanos` by timing a loop of 10,000 vertex-counting calls the same way —
  so both what a timed call costs and what a vertex-counting call costs are covered. The reader
  can discount it.
```

   with:

```markdown
  0.5 ms per frame while sampling. The report states the session's own estimated overhead, each kind of
  call at its own measured cost: timed pairs that own a bucket, attribution-only pairs (event handlers, HUD
  layers, packets) and vertex-counting calls. The three constants are calibrated once, before the first
  session, against a scratch session (phase 5, spec addendum §P5.C2). Each is the fastest of five rounds of
  10,000 whole calls, guards included: timed pairs and vertices through the same static `Probe` methods the
  mixins call, and attribution-only pairs through an event-timing proxy. The log records them beside what
  `Session` alone costs. The reader can discount the estimate.
```

3. In §9's unit list, after this line:

```markdown
- `JsonReportWriter`: schema field presence, key string round trip through a parser.
```

   add:

```markdown
- Phase 5: event timing's listener swap against a real `net.neoforged:bus`; each recommendation rule against
  `Facts` built in the test, and the ranking; the mixin plugin's target check against small ASM class nodes and
  our own mixins' class files; natural name order, short class names, the mesh table cells and the column
  layout; the calibration loop with a fake clock; and a player's key never carrying another player's name.
```

4. In §9, replace check 5, both of its paragraphs:

```markdown
5. Idle cost: measured with Java Flight Recorder in a static superflat scene, four runs of 120 s (jar
   absent and present, each with and without `-XX:-DoEscapeAnalysis`). With escape analysis on, allocation
   and mean CPU load with the jar must be within 2% of without it, and fewer than 0.1% of execution samples
   may contain a `io.github.edwardg117.renderable` frame. With escape analysis off, the jar's run must
   allocate more than 2% above the jar-free run, which is the control proving the measurement is sensitive
   enough to support the first claim.

   **Still not run, as of `v0.4.0`.** This check has been outstanding since phase 1, which recorded it as
   NOT VERIFIED because frame time at the pack's base fluctuates more than the effect being looked for.
   Phase 4a replaced the criterion with the protocol above but did not execute it, so the README's
   "costs nothing when it is not sampling" remains a design claim rather than a measured one. When it is
   run, the results belong in `docs/acceptance/phase-4-idle.md`.
```

   with:

```markdown
5. Idle cost: measured with Java Flight Recorder in a static superflat scene with twenty `NoAI` villagers in
   view, so the entity hooks, the busiest, run every frame, and with the pack's rendering mods (phase 5, spec
   addendum §P5.C1). Four runs of 120 s: Renderable absent and loaded, each with and without
   `-XX:-DoEscapeAnalysis`.
   - With escape analysis on, allocation and mean CPU load with Renderable must be within 2% of without it.
   - Fewer than 0.1% of execution samples may have Renderable's code on top: a class in its package, or a mixin
     handler, all of whose names start `renderable$`.
   - With escape analysis off, the handlers must allocate, which is the control proving the measurement sees
     them. With it on, they must allocate at most a tenth of that.

   The protocol, the scene and the numbers script are in `docs/acceptance/phase-5-idle.md`, whose Results
   section records the runs.
```

- [ ] **Step 5: The phase 5 spec's corrections**

In `2026-09-26-renderable-phase-5-design.md`:

1. In §P5.C1, after the bullet `- **Runs and numbers.** P4.A4's four runs and acceptance numbers, unchanged.`, add:

```markdown
- **Where.** In the dev client, as P4.A4's runs were, with the pack's rendering mods.
  - `-PwithoutRenderable` gives the jar-absent runs, and `-PclientJvmArgs` passes the recording options to the
    game alone.
  - The scene is a datapack function in `docs/acceptance/phase-5-idle-scene/`.
  - `docs/acceptance/phase-5-idle-numbers.py` computes the numbers (plan 5c, deviation 4).
- **What counts as Renderable's.** A sample counts when its top frame is Renderable's code, not when that code
  is anywhere on the stack, since Mixin merges handlers into the classes they hook and a wrapper is on the
  stack of everything it wraps.
- **The control.** It reads the allocation in Renderable's own frames, which the client's total allocation
  would hide. A third check requires that, with escape analysis on, the hooks allocate at most a tenth of that
  (plan 5c, deviation 5).
```

2. In §P5.C2, replace the Javadocs bullet:

```markdown
- **Javadocs.** The boundary paragraphs in `Calibration`'s javadocs go, and the log records the three
  constants at startup.
```

   with:

```markdown
- **Javadocs.** The boundary paragraphs in `Calibration`'s javadocs go, and the log records the three
  constants when calibration runs, before the first session.
- **Warm figures.** Each constant is the fastest of five rounds of 10,000 calls: one round mostly times the
  JIT's interpreter (plan 5c, deviation 6).
- **Beside them in the log.** Calibration also times `Session` alone for a pair and a vertex, phase 4's way,
  and logs those beside the three constants, so C2's check reads off one log line (plan 5c, deviation 1). The
  timed pair is driven through `Probe.beginEntity` on the local player, the busiest real hook (deviation 2).
```

- [ ] **Step 6: Check and stage**

Run: `./gradlew build --no-daemon`
Expected: BUILD SUCCESSFUL, and `build/libs/renderable-0.5.0.jar` exists.

```bash
git add README.md docs/pack-module.md gradle.properties docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md
```

Report for the commit: `docs: a section for players, the pack module entry, and version 0.5.0`.

---

### Task 6: Check it in the dev client

**Files:** none in the repository.

As in plan 5a Task 11. The user's real Gears & Frontiers client may be running, so stop only the dev client,
through its Gradle wrapper (plan 5a Task 11, Step 4, has the commands and the rules). This task proves the
calibration, the player label and the two run properties, and runs the numbers script on a real recording.

- [ ] **Step 1: Build and test**

Run: `./gradlew build --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **209** tests.

- [ ] **Step 2: A session with a short recording**

Launch it in the background:

```bash
JAVA_TOOL_OPTIONS="-Drenderable.autostart=20" ./gradlew runClient -PquickPlay="New World" -PclientJvmArgs="-XX:StartFlightRecording=delay=30s,duration=40s,filename=probe.jfr,settings=profile" --no-daemon
```

Wait for a new report in `run/renderable/` and for `run/probe.jfr` to appear, then stop the client. Then:

```bash
grep "Renderable calibrated its calls" run/logs/latest.log
grep -cE "Renderable [^ ]+ \(renderable\)|Renderable [^ ]+ loaded" run/logs/latest.log
python - <<'EOF'
import glob, json, os
f = max(glob.glob("run/renderable/renderable-*.json"), key=os.path.getmtime)
r = json.load(open(f, encoding="utf-8"))
players = [e for e in r["entities"]["individuals"] if e["typeId"] == "minecraft:player"]
print("player rows:", [e["label"] for e in players])
EOF
python docs/acceptance/phase-5-idle-numbers.py run/probe.jfr
```

Expected:
- **One calibration line.** The timed pair is at least what `Session` alone costs and within a few nanoseconds
  of it, and likewise the vertex. The figures are whole nanoseconds, so equal counts. Report all five figures.
- **The mod loaded.** The log-line count is 2.
- **The player row.** It reads `minecraft:player #<id> (you)`.
- **The numbers script.** It prints one line of numbers without an error. If the recording overlapped the
  session, the Renderable sample count is above zero; if it did not, say so, since that is timing and not a
  script fault.

- [ ] **Step 3: A run without the mod**

```bash
JAVA_TOOL_OPTIONS="-Drenderable.autostart=10" ./gradlew runClient -PquickPlay="New World" -PwithoutRenderable --no-daemon
```

Wait until `run/logs/latest.log` shows `joined the game`, then 30 s more, so that a loaded mod would have run
and written its session, then stop the client. Then:
- `grep -cE "Renderable [^ ]+ \(renderable\)|Renderable [^ ]+ loaded" run/logs/latest.log` must print 0;
- no new report may appear in `run/renderable/`.

- [ ] **Step 4: Report**

Report to the orchestrator: the outputs above and anything unexpected. Nothing is staged in this task; delete
`run/probe.jfr`, which git ignores anyway.

---

### Task 7: The user's idle-cost runs (the orchestrator records them)

**Files:** `docs/acceptance/phase-5-idle.md` (Results), `README.md` (the idle sentence), the base spec's §9 check 5.

This is not a subagent task. The game has to be in the foreground for the recording.

- [ ] **Step 1:** Ask the user to follow `docs/acceptance/phase-5-idle.md` from "Once: the scene" through "The four
  runs" on the `phase-5c-readiness` branch, and to say when the four `.jfr` files are in `run/`.
- [ ] **Step 2:** Run the numbers script on them, and check the four `grep -c` results the user noted.
- [ ] **Step 3:** Replace "Not run yet." under Results with:
  - the date and the commit;
  - a table of the four runs' numbers;
  - the three checks' verdicts;
  - any change to the delay, and any repeated runs for noise.

  Replace the Status line with the verdict.
- [ ] **Step 4:** In the README's "For players" section, replace `When it is not sampling, each of its hooks
  checks one flag and returns.` with the measured result and its scene. For a pass: "When it is not sampling, it
  costs nothing measurable: in a static scene with twenty villagers in view, allocation and CPU load with it
  were within 2% of without it, and its hooks allocated next to nothing (see docs/acceptance/phase-5-idle.md)." For an
  inconclusive result or a failure, say that plainly instead. In the base spec's §9 check 5, add a sentence
  with the verdict.
- [ ] **Step 5:** On a failure, stop: the samples name the hook, and a fix is a new task before rollout. Otherwise
  tell the user "Touch the YubiKey", then commit `docs: the idle-cost runs`.

---

### Task 8: The in-pack acceptance (the orchestrator records it)

**Files:** `docs/acceptance/phase-5.md` (create)

Spec addendum §P5.2. One sitting in the real pack covers what no dev client can: phase 5's checks that need the
pack, and the six that 4b and 4c left unrun. The user installs `build/libs/renderable-0.5.0.jar` in the pack
instance in place of the 0.4.1 jar, and works through the list below. The orchestrator asks for screenshots
where a check is visual, and for the report files. The results go into `docs/acceptance/phase-5.md`, written like
`docs/acceptance/phase-4b-4c.md`: a PASS, FAIL or NOT RUN per check, with what was seen.

- [ ] **Step 1: Send the user this checklist**

A note for reading the numbers: the overhead estimate reads much lower than in 0.4.x reports, about ten times
lower for vertices, because calibration now times warm code (deviation 6). That is not a regression.

**Before starting**
1. **Keys (C4).** In the pack instance's `options.txt`, delete the `key_key.renderable.toggle` line, so the
   game falls back to the default. Then start the game. Options, Controls, Key Binds shows Renderable's key on
   F6, with no conflict marked on F6.

**Session 1: at the base, shaders off, GUI scale 3, 30 s with F6**
2. **The chat line.** It reports `N suggestions` (or `1 suggestion`) before its links, and N matches the
   number of suggestions on the Advice tab (B5).
3. **The Advice tab.** Say, for each suggestion, whether it makes sense for this base (B5).
4. **The Mods tab.**
   - It lists `neoforge` and the pack mods' handlers, with `RenderFrameEvent` and `ClientTickEvent` handlers
     among them (A2, A3).
   - Vanilla layers are under `minecraft`, and no mod's layers exceed the HUD bucket on the Frames tab (A4).
   - With a contraption running at the base, Create's payloads appear under `create` (A5).
   - The tab agrees with the report's Mods section (A7).
5. **After the session.** `/renderable debug events` reports no list holding a timing proxy (A3). This needs
   `debugCommands = true` in `renderable-client.toml`.
6. **The Frames tab.**
   - The split shows `hud`, and `tasks` when there was any.
   - `[mods x]` appears where handlers ran.
   - The environment in the report lists `hooks that did not apply: none` (D1).
7. **Block Entities.**
   - No `shadow` column in the report's tables with shaders off (D4).
   - `render us/f` reads whole at GUI scale 3 on a 2560 px wide window, unsorted and sorted by it (D9).
   - A type with no rows shows its sentence, if one exists here (D2).
8. **Particles.** The screen and the report name classes by short name (D4).
9. **Sections.** Sorted by name, it runs `-10` before `-2` (D5).
10. **Highlight.** Highlight a block entity row, then reopen with `/renderable results`. The screen comes back on
    the same tab, sort, open group and selected row (D6).
11. **Overlapping labels.** Where two labels overlap, the later one covers the earlier one's text cleanly
    (D8, screenshot).
12. **The calibration line.** Look in `logs/latest.log`: the timed pair and the vertex each cost at least what
    `Session` alone does, and no more than a few nanoseconds more (C2).

**Session 2: flying into new terrain, 30 s**
13. `tasks` has time, and `minecraft:level_chunk_with_light` is among the top packets on the Mods tab (A1, A5).

**Session 3: riding a boat or a minecart, 30 s**
14. The player's row has tick time, and the vehicle's is at least as large (A8).

**Session 4: frame cap and HUD**
15. With Max Framerate 30, `frame cap` is most of each frame and `swap/wait` is near zero. At Unlimited with
    VSync off, `frame cap` is zero (A1).
16. Run one 30 s session with the HUD shown and one with it hidden (F1): `hud` is much smaller in the second
    (A1).

**Session 5: shaders on**
17. The `shadow` column appears in the report's tables. The empty states read sensibly with shaders on (4c
    check 2, D2).

**Session 6: another dimension**
18. Go through a portal. The labels show only in the dimension they were measured in (4c check 6).
19. **Rows from the first dimension.** Select a block entity row measured there. Highlight and Teleport are
    greyed, and their tooltip names both dimensions. Back in the first dimension, they work again, and a
    highlight made there does not draw in the other (D7).

**Session 7: reconnect**
20. Log out and back in, then `/renderable results`. Highlight and Teleport are greyed, with the tooltip "You
    have left the world this session was taken in" (4b check 6, 4c check 9).

**Config and screen checks**
21. **The report folder.** Set `reportFolder = "../escape"`, run a short session, and check that the reports
    land in `renderable/` and the log says why (4b check 4).
22. **The overlay toggle.** Run `/renderable overlay off`, then a session: the labels stay hidden afterwards
    (4b check 5).
23. **Leftovers from 4c.**
    - At Auto GUI scale, the tabs, the headers and the numbers fit.
    - The Flywheel and Sections tabs look right on screen.
    - Standing inside a highlighted section still shows its edges.
    - F3's frame time is about the same with and without a session running (4c checks 5 and 7).
24. **Another player (C3).** On the server, with another player online and in view, run a session. Their row
    reads `minecraft:player #<id>` and yours `(you)`, and a search of the report and the JSON finds neither
    player's name.

- [ ] **Step 2:** Collect the answers, screenshots and report files, and write `docs/acceptance/phase-5.md`:
  one section per session above, each check PASS, FAIL or NOT RUN with what was seen, and a closing
  "Observations" section for anything else noticed. A FAIL is a defect: stop and plan the fix with the user.
- [ ] **Step 3:** Tell the user "Touch the YubiKey", then commit `docs: phase 5 in-pack acceptance`.

---

### Task 9: Merge and tag (the orchestrator, with the user's go-ahead)

- [ ] **Step 1:** With the acceptance passed and the user's go-ahead, fast-forward `main` to `phase-5c-readiness`:
  `git checkout main && git merge --ff-only phase-5c-readiness`. The branches stack (5a, 5b, 5d, 5c), so this
  brings all of phase 5. If `main` has moved, stop and ask.
- [ ] **Step 2:** Run `./gradlew test --rerun --no-daemon` on `main` (**209** tests), tell the user "Touch the
  YubiKey", and tag: `git tag -s v0.5.0 -m "Renderable 0.5.0"`.
- [ ] **Step 3:** Update the `phase-5-status` memory with the merge commit and the tag.

---

## After this plan

What is left is the user's (spec addendum §P5.C7):
1. **The remote.** Create the GitHub repository and push `main` and the tags.
2. **CI.** Plan 4d's Task 7: check CI on a real runner.
3. **The release.** Cut the first release by pushing `v0.5.0` once CI is green. The tag/version gate checks
   that the tag matches `mod_version`, which is 0.5.0.

Adding the mod to the pack stays the pack author's call: `docs/pack-module.md` has the entry.
