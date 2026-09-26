# Renderable Phase 4d Implementation Plan: hygiene and CI

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the stringly-typed hook names, the dead enum constant and the scattered `"absent"` literals; fix an entity key that splits when a mob is renamed; fix a Gradle 10 incompatibility; and make CI something that can actually run — it never has.

**Architecture:** `Hook` is a bare enum with no imports, so it moves from `hooks` to `core` and the report writers reference the constants instead of retyping their names; a source-scanning test then guards against the next literal. `EntityKey` overrides the record's generated equality so a rename mid-session does not fork a key. The workflows gain a permissions block, a concurrency group, a cache for the extracted mod jars, a retry around the one step that talks to a third-party Maven, and a gate that refuses to release a tag whose version does not match `gradle.properties`.

**Tech Stack:** Java 21, ModDevGradle 2.0.147, Gradle wrapper, JUnit 5, GitHub Actions (`actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4`, `actions/cache@v4`, `actions/upload-artifact@v4`, `softprops/action-gh-release@v2`).

**Spec:** `docs/superpowers/specs/2026-09-20-renderable-phase-4-design.md` §P4.D. §P4.D1's `TabTest` belongs to plan 4c, which changes `Tab`; §P4.D1's report-import test and §P4.D2 belong to plan 4b, which changes `ReportFiles`. Both reassignments are recorded in §P4.D and in the phase 4 readme.

**Repository:** `H:\repos\Renderable`, branch `phase-4d-hygiene`, created from `phase-4c-screen` at `c831f0f`. Commits are YubiKey-signed: the orchestrator commits after telling the user to touch the key. **Subagents stage and report; they do not commit.**

**Ordering:** Written assuming only plan 4a was on `main`; **executed from 2026-09-26 on top of plans 4b and
4c**, which the user chose so that one in-pack session accepts 4b, 4c and 4d together. Every "if 4b or 4c has
run" branch below takes that side. `main` stays at the accepted 4a state until the combined acceptance passes.

**Revised on 2026-09-26**, before execution, against the code as 4b and 4c left it:

- **Task 3 is folded into Task 1.** 4b already replaced every `"absent"` literal in code with
  `Environment.ABSENT`; what is left is the guard test and two Javadoc comments (`FlywheelCompat.backendName`,
  `IrisCompat.packName`) that spell `"absent"` in quotes, which the guard would reject. Both guards belong in
  the one `SourceGuardsTest` Task 1 creates, so they land together.
- **Task 2 still stands, for a rarer case than it describes.** Since 4b, an entity's accumulator is cached
  on the entity object, so a mob renamed while it stays loaded keeps its first key. The fork now needs the
  client to re-create the entity object (it leaves tracking range and comes back) with a different name, at
  which point `Session.accumulator(key)` looks the key up by value.
- **Task 4's forced failure moves to `EnvironmentTest`**, because the assertion it broke was in Task 3's
  test, and `--rerun-tasks` became `test --rerun`: the former reruns every task in the build, including
  ModDevGradle's Minecraft setup.
- **Task 7 needs a GitHub remote, and this repository has none yet** (`git remote -v` is empty). It is the
  user's step and separate from the in-pack acceptance.
- The expected test totals start from 127.

**Verified facts you can rely on:**

- `hooks/Hook.java` is a bare enum with **no import statements**, so moving it to `core` cannot break
  `KeysTest.coreHasNoMinecraftImports`. Three mixins import it (`mixin/LevelLightEngineMixin`,
  `mixin/flywheel/FlywheelRenderDispatcherMixin`, `mixin/sodium/RenderSectionManagerMixin`) and `Probe` uses
  it unqualified, so the move touches four files plus the enum itself.
- `report/TextReportWriter` spells three hook names as string literals: `"PARTICLE_RENDER"` in
  `appendParticles`, `"SODIUM_MESH"` in `meshNoData`, and `"FLYWHEEL"` in `appendFlywheel`. (If plan 4c has
  run, the first two have moved into `core/EmptyStates`.)
- `ProfileKey.Category.VISUAL` is declared and never constructed: `grep -rn 'Category.VISUAL' src` returns
  only the enum declaration. Spec §3.4 says Flywheel visuals are counted in `FlywheelStats`, not keyed.
- `"absent"` appears as a literal in four production places on `1ec32c3`:
  `hooks/compat/FlywheelCompat.backendName`, `hooks/compat/IrisCompat.packName`,
  `report/EnvironmentCollector.modVersion`, and twice in `report/TextReportWriter.appendFlywheel`. Plans 4b
  and 4c each remove some of them, so Task 3 greps rather than assuming.
- `EntityKey` is `record EntityKey(String typeId, int entityId, String name)`. The generated `equals`
  includes `name`, but `serialize()` is `typeId + "#" + entityId`, so two keys that differ only in name
  serialise identically.
- `build.gradle` has exactly **one** Groovy space-assignment: `exceptionFormat 'full'` at line 196. The
  other single-quoted lines are method calls (`id`, `include`, `from`, `into`, and the dependency
  configurations), which Gradle 10 keeps.
- `.github/workflows/build.yml` and `release.yml` are unchanged since phase 1 and have **never run on a
  runner**. Neither declares `permissions` or `concurrency`; neither caches anything beyond
  `gradle/actions/setup-gradle`'s defaults; `release.yml` will attach a jar whose version does not match its
  tag.
- `gradle/actions/setup-gradle@v4` already caches `~/.gradle`, including `caches/modules-2` where the
  Modrinth artifacts land, and by default writes that cache only from the default branch. A second cache
  for the same path would fight it, so this plan does not add one.
- The suite is **127 tests** after plans 4a, 4b and 4c.

**Deviations from the spec addendum recorded in this plan:** none.

---

## File map

```
.github/workflows/build.yml                                   Rewrite (Task 5)
.github/workflows/release.yml                                 Rewrite (Task 5)
build.gradle                                                  Modify: exceptionFormat (Task 4)
README.md                                                     Modify (Task 6)
src/main/java/io/github/edwardg117/renderable/
  core/Hook.java                                              Create (moved from hooks/)
  hooks/Hook.java                                             Delete
  core/ProfileKey.java                                        Modify: drop VISUAL
  core/EmptyStates.java                                       Modify: Hook constants (Task 1)
  core/EntityKey.java                                         Modify: identity without the name
  core/BlockEntityKey.java                                    Modify: document the label
  hooks/Probe.java                                            Modify: import core.Hook
  hooks/mixin/LevelLightEngineMixin.java                      Modify: import core.Hook
  hooks/mixin/flywheel/FlywheelRenderDispatcherMixin.java     Modify: import core.Hook
  hooks/mixin/sodium/RenderSectionManagerMixin.java           Modify: import core.Hook
  hooks/compat/FlywheelCompat.java                            Modify: Javadoc names Environment.ABSENT (Task 1)
  hooks/compat/IrisCompat.java                                Modify: Javadoc names Environment.ABSENT (Task 1)
  report/TextReportWriter.java                                Modify: Hook.FLYWHEEL (Task 1)
src/test/java/io/github/edwardg117/renderable/
  core/KeysTest.java                                          Modify: key identity tests
  core/SourceGuardsTest.java                                  Create: hook-name and absent literal guards
docs/acceptance/phase-4d.md                                   Create (Task 7)
```

**Expected test totals**, starting from 127 with 4a, 4b and 4c in: 129 after Task 1 (which now includes Task
3's guard), 131 after Task 2, 131 at the end. Every task ends with `./gradlew build` green.

---

### Task 1: `Hook` moves to `core`, the writers use it, and a guard stops the next literal

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/Hook.java`
- Delete: `src/main/java/io/github/edwardg117/renderable/hooks/Hook.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/Probe.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/LevelLightEngineMixin.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/flywheel/FlywheelRenderDispatcherMixin.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/sodium/RenderSectionManagerMixin.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/EmptyStates.java` (plan 4c created it)
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/compat/FlywheelCompat.java` (Javadoc only, Step 5b)
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/compat/IrisCompat.java` (Javadoc only, Step 5b)
- Modify: `src/main/java/io/github/edwardg117/renderable/core/ProfileKey.java`
- Create: `src/test/java/io/github/edwardg117/renderable/core/SourceGuardsTest.java`

Spec addendum §P4.D1. `TextReportWriter` decides what to print by comparing `env.hooksApplied()` against
typed-out names. A renamed enum constant would leave the writer silently printing "hook not active" forever.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/io/github/edwardg117/renderable/core/SourceGuardsTest.java`:

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Source-level guards: things the compiler cannot check but a reader would want kept true. */
class SourceGuardsTest {
    static final Path MAIN = Path.of("src/main/java/io/github/edwardg117/renderable");

    @Test
    void hookNamesAreNeverSpeltAsStringLiterals() throws IOException {
        // A renamed constant must break the build, not leave a writer comparing against a stale string.
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (p.getFileName().toString().equals("Hook.java")) continue;
                String src = Files.readString(p);
                for (Hook h : Hook.values()) {
                    assertFalse(src.contains('"' + h.name() + '"'),
                            p + " spells " + h.name() + " as a string; use Hook." + h.name() + ".name()");
                }
            }
        }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.SourceGuardsTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: class Hook` (it is still in `hooks`). After Step 3 it
will instead fail on `TextReportWriter.java spells PARTICLE_RENDER as a string`.

- [ ] **Step 3: Move the enum**

Create `src/main/java/io/github/edwardg117/renderable/core/Hook.java`:

```java
package io.github.edwardg117.renderable.core;

/**
 * Every hook the mod can install. A hook is "applied" once it has fired at least once in this client run;
 * {@code Probe} records that and the report's environment header lists it (spec §4.5).
 *
 * <p>Lives in {@code core} rather than {@code hooks} so the report writers and the results screen can name
 * a hook by its constant instead of retyping its name (spec addendum §P4.D1). The enum has no imports, so
 * it does not endanger {@code core}'s freedom from Minecraft types.
 */
public enum Hook {
    BLOCK_ENTITY_RENDER,
    ENTITY_RENDER,
    PARTICLE_RENDER,
    PARTICLE_TICK,
    BLOCK_ENTITY_TICK,
    ENTITY_TICK,
    LIGHT,
    FLYWHEEL,
    IRIS_SHADOW,
    SODIUM_MESH,
    SODIUM_UPLOAD,
    VERTICES,
    ACCELERATED_VERTICES
}
```

Delete `src/main/java/io/github/edwardg117/renderable/hooks/Hook.java`.

In `hooks/Probe.java`, add `import io.github.edwardg117.renderable.core.Hook;` beside the other `core`
imports. In each of the three mixins, change
`import io.github.edwardg117.renderable.hooks.Hook;` to
`import io.github.edwardg117.renderable.core.Hook;`.

- [ ] **Step 4: Use the constants in the writers**

Plan 4c has run, so two of the three literals live in `core/EmptyStates.java`; change them there:

```java
    public static String particles(Environment env) {
        return env.hooksApplied().contains(Hook.PARTICLE_RENDER.name())
                ? "(no particles were rendered)"
                : ranked();
    }

    private static String mesh(Environment env, String nothing) {
        return env.hooksApplied().contains(Hook.SODIUM_MESH.name())
                ? "(no data: no " + nothing + ")"
                : "(no data: Sodium hooks were not active)";
    }
```

and the third in `TextReportWriter.appendFlywheel`:

```java
        if (bucketNanos == 0 && !env.hooksApplied().contains(Hook.FLYWHEEL.name())) {
```

Add `import io.github.edwardg117.renderable.core.Hook;` to `TextReportWriter.java`. `EmptyStates` is
already in `core`, so it needs no import.

Run the grep to be sure nothing was missed:

```bash
grep -rn '"\(BLOCK_ENTITY_RENDER\|ENTITY_RENDER\|PARTICLE_RENDER\|PARTICLE_TICK\|BLOCK_ENTITY_TICK\|ENTITY_TICK\|LIGHT\|FLYWHEEL\|IRIS_SHADOW\|SODIUM_MESH\|SODIUM_UPLOAD\|VERTICES\|ACCELERATED_VERTICES\)"' src/main/java
```

Expected: no output. (Test sources still use the strings on purpose — they are fixtures describing what a
report saw, not production decisions — and `SourceGuardsTest` only walks `src/main/java`.)

- [ ] **Step 5: Delete the dead category**

In `core/ProfileKey.java`:

```java
    enum Category { BLOCK_ENTITY, ENTITY, PARTICLE, BLOCK, SECTION }
```

Confirm nothing referenced it:

```bash
grep -rn 'Category.VISUAL' src
```

Expected: no output.

- [ ] **Step 5b: One name for "absent" (formerly Task 3)**

Spec addendum §P4.D1 and §P4.B6 #5. Plan 4b already made the code use `Environment.ABSENT` everywhere;
confirm it:

```bash
grep -rn '"absent"' src/main/java
```

Expected: `core/Environment.java` (the constant itself) and two Javadoc comments, in
`hooks/compat/FlywheelCompat.backendName` and `hooks/compat/IrisCompat.packName`, which spell `"absent"` in
quotes. The guard below reads source text, so it rejects those comments too. Rewrite each to name the
constant instead, for example `or {@link Environment#ABSENT} without Flywheel`; add the `Environment` import
only if the file does not have it and the Javadoc needs it to resolve.

Append inside `SourceGuardsTest`:

```java
    @Test
    void theAbsentSentinelIsNamedOnce() throws IOException {
        // Environment.ABSENT is the only place the string may appear, so a change of wording is one edit.
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (p.getFileName().toString().equals("Environment.java")) continue;
                String src = Files.readString(p);
                assertFalse(src.contains("\"absent\""),
                        p + " spells the absent sentinel; use Environment.ABSENT");
            }
        }
    }
```

(`EnvironmentTest` already pins `Environment.ABSENT` to `"absent"`, so this test does not repeat it.)

- [ ] **Step 6: Run the tests to verify they pass**

```bash
./gradlew build --no-daemon
./gradlew test --rerun --no-daemon
```

Expected: `BUILD SUCCESSFUL`, **129 tests**, and the test task executed rather than restored from the build
cache.

- [ ] **Step 7: Stage and report**

```bash
git add -A src/main/java src/test/java
git status --short
```

`git add -A` is used here because a file was deleted. Commit message for the orchestrator:

```
fix(core): name hooks by constant and the absent sentinel once, with guards; drop the unused VISUAL category
```

---

### Task 2: An entity key that a rename cannot fork

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/EntityKey.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/BlockEntityKey.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/KeysTest.java` (append)

Spec addendum §P4.D5. Since plan 4b, an entity's accumulator is cached on the entity object, so a mob
renamed while it stays loaded keeps its first key and does not fork. The fork still happens when the client
re-creates the entity object (it leaves tracking range and comes back) under a different name: the new
object has no cached accumulator, and `Session.accumulator(key)` looks the key up by value, name included.
Identity without the name closes that path too, and makes "the first name seen wins" true in every case.

- [ ] **Step 1: Write the failing tests**

Append inside `KeysTest`:

```java
    @Test
    void entityIdentityIgnoresTheNameSoARenameDoesNotForkTheKey() {
        EntityKey unnamed = new EntityKey("minecraft:cow", 7, "");
        EntityKey named = new EntityKey("minecraft:cow", 7, "Bessie");
        EntityKey other = new EntityKey("minecraft:cow", 8, "");
        EntityKey otherType = new EntityKey("minecraft:pig", 7, "");

        assertEquals(unnamed, named, "the same entity, renamed part way through a session, is one key");
        assertEquals(unnamed.hashCode(), named.hashCode());
        assertNotEquals(unnamed, other);
        assertNotEquals(unnamed, otherType);
        assertNotEquals(unnamed, new Object());

        // The name is still carried, for the label and the report.
        assertEquals("minecraft:cow #7", unnamed.label());
        assertEquals("minecraft:cow #7 (Bessie)", named.label());
        assertEquals(named.serialize(), unnamed.serialize(), "identity and serialisation agree");

        java.util.Map<ProfileKey, String> map = new java.util.HashMap<>();
        map.put(unnamed, "first");
        map.put(named, "second");
        assertEquals(1, map.size(), "one accumulator, not two");
        assertEquals("second", map.get(unnamed));
    }

    @Test
    void blockEntityLabelsOmitTheDimensionOnPurpose() {
        BlockEntityKey overworld = new BlockEntityKey("minecraft:chest", "minecraft:overworld", 1, 2, 3);
        BlockEntityKey nether = new BlockEntityKey("minecraft:chest", "minecraft:the_nether", 1, 2, 3);

        assertEquals(overworld.label(), nether.label(),
                "a session cannot span two dimensions, so the label does not need one");
        assertNotEquals(overworld, nether, "the dimension is still part of identity");
        assertNotEquals(overworld.serialize(), nether.serialize(), "and of the serialised form");
        assertEquals("minecraft:chest@minecraft:the_nether:1,2,3", nether.serialize());
    }
```

- [ ] **Step 2: Run them to verify they fail**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.KeysTest' --no-daemon
```

Expected: `entityIdentityIgnoresTheNameSoARenameDoesNotForkTheKey` fails with
`expected: <EntityKey[typeId=minecraft:cow, entityId=7, name=]> but was: <EntityKey[... name=Bessie]>`.
`blockEntityLabelsOmitTheDimensionOnPurpose` passes already; it is there so the rule is pinned.

- [ ] **Step 3: Override the record's identity**

Replace `EntityKey.java`:

```java
package io.github.edwardg117.renderable.core;

/**
 * One entity being rendered or ticked. Identity is the type and the entity id **only**: the name is carried
 * for {@link #label()} and the report, but including it in equality would fork the key when a mob is renamed
 * part way through a session, producing two rows with the same {@link #serialize()} form that a reader could
 * not tell apart (spec addendum §P4.D5). The first name seen wins.
 */
public record EntityKey(String typeId, int entityId, String name) implements ProfileKey {

    @Override
    public Category category() {
        return Category.ENTITY;
    }

    @Override
    public String label() {
        return typeId + " #" + entityId + (name.isEmpty() ? "" : " (" + name + ")");
    }

    @Override
    public String serialize() {
        return typeId + "#" + entityId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EntityKey k && k.entityId == entityId && k.typeId.equals(typeId);
    }

    @Override
    public int hashCode() {
        return typeId.hashCode() * 31 + entityId;
    }
}
```

- [ ] **Step 4: Document the block entity label**

Replace the class Javadoc and `label()` in `BlockEntityKey.java`:

```java
/**
 * One block entity being rendered or ticked, identified by type, dimension and position.
 */
public record BlockEntityKey(String typeId, String dimension, int x, int y, int z) implements ProfileKey {

    @Override
    public Category category() {
        return Category.BLOCK_ENTITY;
    }

    /**
     * Deliberately without the dimension, which {@link #serialize()} carries. A session is confined to one
     * dimension — {@code FrameEvents.onLevelUnload} discards a running session when the client level goes
     * away — and the report's environment header names it, so repeating it on every row would widen the
     * tables for no information (spec addendum §P4.D5).
     */
    @Override
    public String label() {
        return typeId + " @ " + x + " " + y + " " + z;
    }

    @Override
    public String serialize() {
        return typeId + "@" + dimension + ":" + x + "," + y + "," + z;
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`, **131 tests** (run `./gradlew test --rerun --no-daemon` so they execute).

- [ ] **Step 6: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/core src/test/java/io/github/edwardg117/renderable/core
git status --short
```

Commit message for the orchestrator:

```
fix(core): an entity key is its type and id, so a rename mid-session does not split it
```

---

### Task 3: One name for "absent" (folded into Task 1)

Revised on 2026-09-26: plan 4b had already replaced every literal in code, so only the guard test and two
Javadoc comments were left, and they moved into Task 1 as Step 5b. There is nothing to do here.

---

### Task 4: The Gradle 10 incompatibility

**Files:**
- Modify: `build.gradle`

Spec addendum §P4.D3. Groovy space-assignment for properties is removed in Gradle 10.

- [ ] **Step 1: Find every occurrence**

```bash
grep -n "exceptionFormat" build.gradle
```

Expected: one line, `        exceptionFormat 'full'`.

Then confirm nothing else uses the shape, by listing every line that is an identifier followed directly by a
literal with no `=`:

```bash
grep -nE "^\s+[a-zA-Z][a-zA-Z0-9]* ['\"]" build.gradle
```

Expected: `id`, `include`, `from`, `into`, the dependency configurations, and `exceptionFormat`. All but
`exceptionFormat` are real methods that Gradle 10 keeps; only `exceptionFormat` is a property.

- [ ] **Step 2: Fix it**

```groovy
tasks.named('test', Test) {
    useJUnitPlatform()
    testLogging {
        events 'passed', 'failed', 'skipped'
        exceptionFormat = 'full'
    }
}
```

- [ ] **Step 3: Verify the build still behaves**

```bash
./gradlew test --rerun --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 131 tests, and per-test `PASSED` lines in the output — which is what
`events 'passed'` produces and proves `testLogging` is still configured. (`--rerun` reruns just the test
task; `--rerun-tasks` would rerun every task, including ModDevGradle's Minecraft setup.)

Then force a failure to prove `exceptionFormat = 'full'` is live: temporarily change the assertion in
`EnvironmentTest` that pins the sentinel, `assertEquals("absent", Environment.ABSENT)`, to expect
`"not-absent"`, run the same command, and confirm the output carries a full stack trace rather than a
one-line message. Revert the change and confirm `git diff` shows only `build.gradle`.

- [ ] **Step 4: Stage and report**

```bash
git add build.gradle
git status --short
```

Commit message for the orchestrator:

```
build: assign exceptionFormat rather than using the space syntax Gradle 10 removes
```

---

### Task 5: Workflows that can survive a runner

**Files:**
- Modify: `.github/workflows/build.yml`
- Modify: `.github/workflows/release.yml`

Spec addendum §P4.D4.

- [ ] **Step 1: Rewrite `build.yml`**

```yaml
name: build

on:
  push:
    branches: [main]
  pull_request:

# Nothing here needs to write to the repository.
permissions:
  contents: read

# A second push to the same ref makes the first run pointless.
concurrency:
  group: build-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      # setup-gradle caches ~/.gradle, which includes caches/modules-2 where the Modrinth artifacts land.
      # That cache is what lets a build survive a Modrinth outage, so do not add a second cache for the
      # same path: two caches on one directory fight each other.
      - uses: gradle/actions/setup-gradle@v4

      # The extraction output is cheap to keep and saves unzipping the JarJar containers every run.
      - name: Cache the extracted mod jars
        uses: actions/cache@v4
        with:
          path: build/nested-mod-jars
          key: nested-mod-jars-${{ hashFiles('gradle.properties') }}

      # Sodium, Iris and Create come from api.modrinth.com, a third-party service. Give a transient failure
      # two more chances before failing the whole build.
      - name: Resolve the third-party mod jars
        run: |
          for attempt in 1 2 3; do
            if ./gradlew extractNestedModJars --no-daemon; then
              exit 0
            fi
            echo "attempt $attempt failed; retrying in 20s"
            sleep 20
          done
          echo "could not resolve the Modrinth artifacts after three attempts"
          exit 1

      - name: Build
        run: ./gradlew build --no-daemon

      - uses: actions/upload-artifact@v4
        with:
          name: renderable
          path: build/libs/*.jar
```

- [ ] **Step 2: Rewrite `release.yml`**

```yaml
name: release

on:
  push:
    tags: ['v*']

permissions:
  contents: write

# Two runs attaching assets to one release would race; let the first finish.
concurrency:
  group: release-${{ github.ref }}
  cancel-in-progress: false

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      # Fail before spending a build on a jar whose name will not match its tag.
      - name: The tag must match mod_version
        run: |
          version=$(sed -n 's/^mod_version=//p' gradle.properties | tr -d '\r')
          if [ -z "$version" ]; then
            echo "gradle.properties has no mod_version"
            exit 1
          fi
          if [ "v$version" != "$GITHUB_REF_NAME" ]; then
            echo "tag $GITHUB_REF_NAME does not match mod_version=$version"
            exit 1
          fi
          echo "tag $GITHUB_REF_NAME matches mod_version=$version"

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - uses: gradle/actions/setup-gradle@v4

      - name: Cache the extracted mod jars
        uses: actions/cache@v4
        with:
          path: build/nested-mod-jars
          key: nested-mod-jars-${{ hashFiles('gradle.properties') }}

      - name: Resolve the third-party mod jars
        run: |
          for attempt in 1 2 3; do
            if ./gradlew extractNestedModJars --no-daemon; then
              exit 0
            fi
            echo "attempt $attempt failed; retrying in 20s"
            sleep 20
          done
          echo "could not resolve the Modrinth artifacts after three attempts"
          exit 1

      - name: Build
        run: ./gradlew build --no-daemon

      - uses: softprops/action-gh-release@v2
        with:
          files: build/libs/renderable-*.jar
          generate_release_notes: true
```

- [ ] **Step 3: Check the version gate's shell logic locally**

The gate is the one new piece of logic, and it is plain `sh`. Run it the way the runner will:

```bash
GITHUB_REF_NAME=v0.4.1 sh -c 'version=$(sed -n "s/^mod_version=//p" gradle.properties | tr -d "\r"); test "v$version" = "$GITHUB_REF_NAME" && echo MATCH || echo MISMATCH'
```

Expected: `MATCH` (`mod_version=0.4.1` since plan 4b). Then:

```bash
GITHUB_REF_NAME=v9.9.9 sh -c 'version=$(sed -n "s/^mod_version=//p" gradle.properties | tr -d "\r"); test "v$version" = "$GITHUB_REF_NAME" && echo MATCH || echo MISMATCH'
```

Expected: `MISMATCH`.

- [ ] **Step 4: Check the YAML parses**

```bash
python -c "import yaml,sys; [yaml.safe_load(open(f)) for f in ['.github/workflows/build.yml','.github/workflows/release.yml']]; print('both parse')"
```

Expected: `both parse`. If Python has no `yaml` module on this machine, say so in the report and rely on the
runner check in Task 7 instead; do not install anything.

- [ ] **Step 5: Stage and report**

```bash
git add .github/workflows
git status --short
```

Commit message for the orchestrator:

```
ci: permissions, concurrency, mod-jar cache, Modrinth retry, and a tag/version gate
```

---

### Task 6: README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Document the report folder rule and the release gate**

In "Building", after the `-PquickPlay` paragraph, add:

```markdown
CI builds every push to `main` and every pull request, and a `v*` tag builds and attaches
`renderable-<version>.jar` to a GitHub Release. A release tag must match `mod_version` in
`gradle.properties`, or the workflow stops before building.
```

- [ ] **Step 2: Build**

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 131 tests.

- [ ] **Step 3: Stage and report**

```bash
git add README.md
git status --short
```

Commit message for the orchestrator:

```
docs: README for phase 4d
```

---

### Task 7: Watch CI actually run (human step)

**Files:**
- Create: `docs/acceptance/phase-4d.md`

CI has never executed on a runner, so the only way to accept this plan is to watch it. The user does this;
the orchestrator writes the log, has it reviewed, and commits it. **It needs a GitHub remote first:** as of
2026-09-26 the repository has none. It is independent of the in-pack acceptance and can happen before or
after it; the version gate's passing case (step 5) belongs with the release tag, after the merge.

1. **Pull request build.** Push this plan's branch and open a pull request against `main`. Paste the run's
   conclusion, its duration, and the `Resolve the third-party mod jars` step's output. Confirm the
   `renderable` artifact is attached and contains one jar.
2. **Cache.** Push a trivial second commit to the same branch. The second run's
   `Cache the extracted mod jars` step should report a cache hit and `setup-gradle` should report restoring
   the Gradle cache. Paste both lines and the two run durations, so the saving is on the record.
3. **Concurrency.** Push two commits within a few seconds. The first run should be cancelled. Paste its
   status.
4. **Version gate, failing.** From a scratch branch, tag a commit `v9.9.9` and push the tag. The release
   workflow must fail at **The tag must match mod_version** with
   `tag v9.9.9 does not match mod_version=0.4.1` (or whatever `mod_version` is by then), before any build
   step runs. Paste the step log, then
   delete the tag locally and on the remote:

```bash
git tag -d v9.9.9
git push origin :refs/tags/v9.9.9
```

5. **Version gate, passing.** After merging, push the real `v0.4.0` tag (if plan 4a's tag has not been
   pushed yet) or bump to the next version and tag it. Confirm the gate passes, the build runs, and the
   release carries `renderable-<version>.jar`. Paste the release URL.

Then the orchestrator:

```bash
git add docs/acceptance/phase-4d.md
git commit -m "docs: phase 4d acceptance log"
```

---

## Notes for the orchestrator

- Tasks 1 and 2 are pure JUnit. Task 1 does change three mixins' imports, which compiles or does not, but the
  dev-client check the user asked for at the end of 4d (mixin export with `-PpackMods`, then a look at the
  results screen) covers it at runtime before the in-pack session. Task 4 needs one Gradle run. Task 5
  changes nothing a local build can prove, which is why Task 7 exists.
- Task 1's guard test is the piece most likely to surprise: it walks `src/main/java` and fails on **any**
  hook name in quotes, including ones a later plan might add. That is the point — if a subagent adds one, it
  should be told to use the constant.
- Task 3 was folded into Task 1 (Step 5b) on 2026-09-26.
- **Reviewer checklist per task:** `core/Hook.java` still has no imports; `EntityKey.equals` and `hashCode`
  agree with each other and with `serialize()`; both workflows declare `permissions` and `concurrency`; the
  release gate runs before `setup-java`; no second cache for `~/.gradle`.
