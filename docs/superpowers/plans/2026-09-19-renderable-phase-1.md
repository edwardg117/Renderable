# Renderable Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A working client-only NeoForge 1.21.1 mod that samples the render loop for a fixed window, attributes render-thread time to individual block entities and entities, records per-frame bucket splits and GC so every hitch can be explained, and delivers the result as an in-game results screen, an in-world overlay, and text plus JSON reports.

**Architecture:** Four packages under `io.github.edwardg117.renderable`. `core` is pure Java (no Minecraft imports) and holds the sampling session, accumulators, hitch detection, ranking and formatting; it is fully unit-tested. `hooks` holds two MixinExtras `@WrapMethod` mixins and the NeoForge frame/tick event handlers, all funnelling into one static `Probe`. `ui` holds keybind, command, HUD, results screen and overlay. `report` holds the text and JSON writers, file output and the Minecraft-aware environment collector. A root `ProfilerController` coordinates start, stop, finish and delivery.

**Tech Stack:** Java 21, Gradle 9.2.1 wrapper, ModDevGradle 2.0.147, NeoForge 21.1.220 (MixinExtras 0.5.0 bundled), Parchment 2024.11.17, JUnit 5, Gson (ships with Minecraft), GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md`. Section numbers below refer to it.

**Repository:** `H:\repos\Renderable` (already initialised with the spec, `CLAUDE.md`, `.gitignore`; branch `main`). All paths below are relative to it. Commits are YubiKey-signed: the orchestrator runs commits after telling the user to touch the key. Subagents stage files and report back; they do not commit.

**Verified facts you can rely on** (read from the real jars and Mojang's 1.21.1 mappings on 2026-09-19):

- `BlockEntityRenderDispatcher.render(BlockEntity, float, PoseStack, MultiBufferSource)` is the only method named `render` in that class. Sodium calls it too.
- `EntityRenderDispatcher.render(Entity, double, double, double, float, float, PoseStack, MultiBufferSource, int)` is the only method named `render` in that class.
- `DebugRenderer.renderFloatingText(PoseStack, MultiBufferSource, String, double, double, double, int, float, boolean, float, boolean)` exists and takes world coordinates.
- `RenderFrameEvent.Pre/Post`, `ClientTickEvent.Pre/Post`, `RegisterGuiLayersEvent.registerAboveAll`, `RenderLevelStageEvent.Stage.AFTER_PARTICLES`, `RegisterClientCommandsEvent.getDispatcher()`, `RegisterKeyMappingsEvent.register`, `ClientPlayerNetworkEvent.LoggingOut`, `LevelEvent.Unload` all exist in NeoForge 21.1.220.
- `ModConfigSpec.Builder` has `define(String, boolean)`, `defineInRange(String, int, int, int)`, `defineInRange(String, double, double, double)`, `define(String, String)`.
- `ClientPacketListener.sendCommand(String)`, `KeyboardHandler.setClipboard(String)`, `Util.getPlatform().openPath(Path)`, `Minecraft.getFps()`, `LevelRenderer.getSectionStatistics()` exist.
- `ObjectSelectionList(Minecraft, int width, int height, int y, int itemHeight)` is the 1.21.1 constructor.

---

## File map

```
build.gradle                                   Gradle build (ModDevGradle, JUnit, optional pack mods)
settings.gradle                                plugin management
gradle.properties                              versions and mod metadata
gradle/wrapper/*, gradlew, gradlew.bat         copied from the NeoForge MDK
LICENSE                                        MIT
README.md                                      what it is, how to use, how to read a report
.github/workflows/build.yml                    build + test on push/PR
.github/workflows/release.yml                  build + attach jar on v* tags
src/main/templates/META-INF/neoforge.mods.toml mod metadata (expanded by Gradle)
src/main/resources/renderable.mixins.json      mixin config
src/main/resources/assets/renderable/lang/en_us.json
src/main/java/io/github/edwardg117/renderable/
  RenderableMod.java                           @Mod entry point, wiring
  RenderableConfig.java                        ModConfigSpec (spec §7)
  ProfilerController.java                      start/stop/finish/deliver (spec §3.1, §6)
  core/Bucket.java                             bucket enum (spec §3.2)
  core/ProfileKey.java                         key interface
  core/BlockEntityKey.java, core/EntityKey.java
  core/Accumulator.java                        per-key counters (spec §3.4)
  core/SessionConfig.java
  core/TopEntry.java, core/FrameRecord.java     per-frame record (spec §3.2)
  core/Session.java                            live sampling state machine
  core/FrameStats.java                         percentiles and averages
  core/HitchDetector.java                      threshold + detection (spec §3.3)
  core/SessionResult.java                      immutable finished session
  core/Calibration.java                        per-call overhead estimate (spec §3.5)
  core/RankedEntry.java, core/Ranking.java      roll-ups and sorting
  core/Environment.java                        plain environment record
  core/Fmt.java                                number/time formatting
  core/Splits.java                             one-line bucket split descriptions
  hooks/Probe.java                             static entry for all hooks
  hooks/Hook.java                              enum of hooks, for "applied" tracking
  hooks/KeyFactory.java                        Minecraft object -> ProfileKey
  hooks/GcSampler.java                         GC MXBean deltas
  hooks/FrameEvents.java                       RenderFrameEvent/ClientTickEvent/unload handlers
  hooks/RenderableMixinPlugin.java             gates third-party mixins by mod presence
  hooks/mixin/BlockEntityRenderDispatcherMixin.java
  hooks/mixin/EntityRenderDispatcherMixin.java
  ui/Keybinds.java, ui/RenderableCommand.java
  ui/SamplingHud.java                          HUD line while sampling (spec §5.2)
  ui/WorldOverlay.java                         floating labels + highlight box (spec §5.4)
  ui/Colors.java                               heat colour scale
  ui/screen/Tab.java, ui/screen/Row.java, ui/screen/Rows.java, ui/screen/RowList.java, ui/screen/ResultsScreen.java
  report/Table.java                            fixed-width text table
  report/TextReportWriter.java                 spec §6.1
  report/JsonReportWriter.java                 spec §6.2
  report/ReportFiles.java                      writes both files
  report/EnvironmentCollector.java             Minecraft-aware, builds core.Environment
src/test/java/io/github/edwardg117/renderable/
  core/KeysTest.java, core/SessionTest.java, core/FrameStatsTest.java, core/HitchDetectorTest.java,
  core/RankingTest.java, core/CalibrationTest.java, core/TestSessions.java
  report/TextReportWriterTest.java, report/JsonReportWriterTest.java
  ui/ColorsTest.java
docs/acceptance/phase-1.md                     results of the in-game acceptance checks
```

Dependency direction: `ui`, `report`, `hooks`, root -> `core`. `core` never imports `net.minecraft`, `net.neoforged`, `com.mojang` or `org.lwjgl`. A test in Task 2 enforces that with a source scan.

---

### Task 1: Project scaffold that builds an empty mod

**Files:**
- Create: `build.gradle`, `settings.gradle`, `gradle.properties`, `LICENSE`
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` (copied from the MDK)
- Create: `src/main/templates/META-INF/neoforge.mods.toml`
- Create: `src/main/resources/renderable.mixins.json`
- Create: `src/main/resources/assets/renderable/lang/en_us.json`
- Create: `src/main/java/io/github/edwardg117/renderable/RenderableMod.java`

- [ ] **Step 1: Copy the Gradle wrapper from the NeoForge MDK**

There is no global Gradle on this machine, so the wrapper must come from the MDK zip.

```bash
cd /h/repos/Renderable
curl -sL https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/archive/refs/heads/main.zip -o "$TEMP/mdk.zip"
unzip -q -o "$TEMP/mdk.zip" -d "$TEMP/mdk"
cp -r "$TEMP/mdk/MDK-1.21.1-ModDevGradle-main/gradle" .
cp "$TEMP/mdk/MDK-1.21.1-ModDevGradle-main/gradlew" "$TEMP/mdk/MDK-1.21.1-ModDevGradle-main/gradlew.bat" .
git update-index --add --chmod=+x gradlew
ls gradle/wrapper
```

Expected: `gradle-wrapper.jar  gradle-wrapper.properties`, and the properties file names `gradle-9.2.1-bin.zip`.

- [ ] **Step 2: Write `settings.gradle`**

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}

rootProject.name = 'renderable'
```

- [ ] **Step 3: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true

parchment_minecraft_version=1.21.1
parchment_mappings_version=2024.11.17

minecraft_version=1.21.1
minecraft_version_range=[1.21.1]
neo_version=21.1.220
loader_version_range=[1,)

mod_id=renderable
mod_name=Renderable
mod_license=MIT
mod_version=0.1.0
mod_group_id=io.github.edwardg117.renderable
mod_authors=edwardg117
mod_description=Client render profiler: shows which block entities, entities and effects cost frame time and what caused each hitch.

# Versions of the Gears & Frontiers pack's rendering stack, used by the dev client when -PpackMods is passed.
sodium_version=mc1.21.1-0.8.13-neoforge
iris_version=1.8.14-beta.1+1.21.1-neoforge
create_version=6.0.10+mc1.21.1
enchantable_machinery_version=3.6.0+mc1.21.1-neoforge
kotlin_for_forge_version=5.12.0
```

- [ ] **Step 4: Write `build.gradle`**

```groovy
plugins {
    id 'java-library'
    id 'net.neoforged.moddev' version '2.0.147'
    id 'idea'
}

tasks.named('wrapper', Wrapper).configure {
    distributionType = Wrapper.DistributionType.BIN
}

version = mod_version
group = mod_group_id

base {
    archivesName = mod_id
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository {
            maven {
                name = 'Modrinth'
                url = 'https://api.modrinth.com/maven'
            }
        }
        filter {
            includeGroup 'maven.modrinth'
        }
    }
}

neoForge {
    version = project.neo_version

    parchment {
        mappingsVersion = project.parchment_mappings_version
        minecraftVersion = project.parchment_minecraft_version
    }

    runs {
        client {
            client()
            gameDirectory = project.file('run')
            systemProperty 'forge.logging.markers', 'REGISTRIES'
            logLevel = org.slf4j.event.Level.INFO
        }
    }

    mods {
        "${mod_id}" {
            sourceSet(sourceSets.main)
        }
    }
}

configurations {
    runtimeClasspath.extendsFrom localRuntime
}

dependencies {
    testImplementation platform('org.junit:junit-bom:5.11.4')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testImplementation 'com.google.code.gson:gson:2.10.1'

    // Pass -PpackMods to run the dev client with the pack's rendering stack (Sodium, Iris,
    // Create with Flywheel, Enchantable Machinery). Slower to start; needed for acceptance checks.
    if (project.hasProperty('packMods')) {
        localRuntime "maven.modrinth:sodium:${sodium_version}"
        localRuntime "maven.modrinth:iris:${iris_version}"
        localRuntime "maven.modrinth:create:${create_version}"
        localRuntime "maven.modrinth:kotlin-for-forge:${kotlin_for_forge_version}"
        localRuntime "maven.modrinth:create-enchantable-machinery:${enchantable_machinery_version}"
    }
}

var generateModMetadata = tasks.register('generateModMetadata', ProcessResources) {
    var replaceProperties = [
            minecraft_version      : minecraft_version,
            minecraft_version_range: minecraft_version_range,
            neo_version            : neo_version,
            loader_version_range   : loader_version_range,
            mod_id                 : mod_id,
            mod_name               : mod_name,
            mod_license            : mod_license,
            mod_version            : mod_version,
            mod_authors            : mod_authors,
            mod_description        : mod_description,
    ]
    inputs.properties replaceProperties
    expand replaceProperties
    from 'src/main/templates'
    into 'build/generated/sources/modMetadata'
}

sourceSets.main.resources.srcDir generateModMetadata
neoForge.ideSyncTask generateModMetadata

tasks.named('test', Test) {
    useJUnitPlatform()
    testLogging {
        events 'passed', 'failed', 'skipped'
        exceptionFormat 'full'
    }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}

idea {
    module {
        downloadSources = true
        downloadJavadoc = true
    }
}
```

- [ ] **Step 5: Write `src/main/templates/META-INF/neoforge.mods.toml`**

```toml
modLoader="javafml"
loaderVersion="${loader_version_range}"
license="${mod_license}"
issueTrackerURL="https://github.com/edwardg117/Renderable/issues"

[[mixins]]
config="renderable.mixins.json"

[[mods]]
modId="${mod_id}"
version="${mod_version}"
displayName="${mod_name}"
authors="${mod_authors}"
description='''${mod_description}'''
# Client-only: never block joining a server that does not have it.
displayTest="IGNORE_ALL_VERSION"

[[dependencies.${mod_id}]]
    modId="neoforge"
    type="required"
    versionRange="[${neo_version},)"
    ordering="NONE"
    side="CLIENT"

[[dependencies.${mod_id}]]
    modId="minecraft"
    type="required"
    versionRange="${minecraft_version_range}"
    ordering="NONE"
    side="CLIENT"
```

- [ ] **Step 6: Write `src/main/resources/renderable.mixins.json`**

The mixin classes do not exist yet; an empty list is valid and the plugin is added in Task 9.

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "io.github.edwardg117.renderable.hooks.mixin",
  "compatibilityLevel": "JAVA_21",
  "client": [],
  "injectors": {
    "defaultRequire": 1
  }
}
```

- [ ] **Step 7: Write `src/main/resources/assets/renderable/lang/en_us.json`**

```json
{
  "key.categories.renderable": "Renderable",
  "key.renderable.toggle": "Start / stop profiling session",
  "key.renderable.overlay": "Toggle in-world overlay"
}
```

- [ ] **Step 8: Write the entry point `src/main/java/io/github/edwardg117/renderable/RenderableMod.java`**

```java
package io.github.edwardg117.renderable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = RenderableMod.MOD_ID, dist = Dist.CLIENT)
public final class RenderableMod {
    public static final String MOD_ID = "renderable";
    public static final Logger LOGGER = LoggerFactory.getLogger("Renderable");
    /** Set from the mod container in the constructor; "dev" until then. */
    public static String VERSION = "dev";

    public RenderableMod(IEventBus modBus, ModContainer container) {
        VERSION = container.getModInfo().getVersion().toString();
        LOGGER.info("Renderable {} loaded", VERSION);
    }
}
```

- [ ] **Step 9: Write `LICENSE`** (MIT, 2026, edwardg117)

```text
MIT License

Copyright (c) 2026 edwardg117

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- [ ] **Step 10: Build**

```bash
cd /h/repos/Renderable && ./gradlew build --no-daemon
```

The first run downloads Gradle 9.2.1 and NeoForge; allow ten minutes. Expected: `BUILD SUCCESSFUL` and `build/libs/renderable-0.1.0.jar` exists. If the build fails on `configuration-cache`, rerun once; if it fails again, set `org.gradle.configuration-cache=false` in `gradle.properties` and note it in the commit message.

- [ ] **Step 11: Commit**

```bash
git add settings.gradle gradle.properties build.gradle gradlew gradlew.bat gradle LICENSE src
git commit -m "build: scaffold NeoForge 21.1.220 mod with ModDevGradle and JUnit"
```

---

### Task 2: Core keys, buckets and accumulator

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/Bucket.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/ProfileKey.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/BlockEntityKey.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/EntityKey.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/Accumulator.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/KeysTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class KeysTest {

    @Test
    void blockEntityKeyFormats() {
        BlockEntityKey k = new BlockEntityKey("create:mechanical_press", "minecraft:overworld", 10, 64, -5);
        assertEquals(ProfileKey.Category.BLOCK_ENTITY, k.category());
        assertEquals("create:mechanical_press", k.typeId());
        assertEquals("create:mechanical_press @ 10 64 -5", k.label());
        assertEquals("create:mechanical_press@minecraft:overworld:10,64,-5", k.serialize());
    }

    @Test
    void entityKeyFormatsWithAndWithoutName() {
        EntityKey plain = new EntityKey("minecraft:cow", 42, "");
        assertEquals(ProfileKey.Category.ENTITY, plain.category());
        assertEquals("minecraft:cow #42", plain.label());
        assertEquals("minecraft:cow#42", plain.serialize());

        EntityKey named = new EntityKey("minecraft:cow", 42, "Bessie");
        assertEquals("minecraft:cow #42 (Bessie)", named.label());
    }

    @Test
    void keysAreValueEqual() {
        assertEquals(new BlockEntityKey("a:b", "d", 1, 2, 3), new BlockEntityKey("a:b", "d", 1, 2, 3));
        assertEquals(new EntityKey("a:b", 1, "x"), new EntityKey("a:b", 1, "x"));
    }

    @Test
    void renderBucketsAreDistinguishedFromTickBuckets() {
        assertTrue(Bucket.BLOCK_ENTITY.isRender());
        assertTrue(Bucket.ENTITY.isRender());
        assertFalse(Bucket.BE_TICK.isRender());
        assertFalse(Bucket.PARTICLE_TICK.isRender());
        assertEquals("block entity", Bucket.BLOCK_ENTITY.label());
        assertEquals(Bucket.values().length, Bucket.COUNT);
    }

    @Test
    void accumulatorStartsEmpty() {
        Accumulator a = new Accumulator(new EntityKey("a:b", 1, ""));
        assertEquals(0, a.calls);
        assertEquals(0, a.totalNanos());
        assertEquals(0, a.frameNanos());
    }

    /** Guards the architecture rule: core has no Minecraft, NeoForge, Mojang or LWJGL imports. */
    @Test
    void coreHasNoMinecraftImports() throws IOException {
        Path core = Path.of("src/main/java/io/github/edwardg117/renderable/core");
        List<String> forbidden = List.of("import net.minecraft", "import net.neoforged", "import com.mojang", "import org.lwjgl");
        try (Stream<Path> files = Files.walk(core)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p);
                for (String f : forbidden) {
                    assertFalse(src.contains(f), p + " contains " + f);
                }
            }
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.KeysTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: class BlockEntityKey`.

- [ ] **Step 3: Write `Bucket.java`**

```java
package io.github.edwardg117.renderable.core;

/**
 * Per-frame time buckets (spec §3.2). Render buckets nest inside render time, tick buckets inside tick time.
 * Phase 1 fills BLOCK_ENTITY and ENTITY; the rest exist so later phases do not change the frame record shape.
 */
public enum Bucket {
    BLOCK_ENTITY(true),
    ENTITY(true),
    PARTICLE_RENDER(true),
    FLYWHEEL(true),
    CHUNK_UPLOAD(true),
    LIGHT(true),
    PARTICLE_TICK(false),
    BE_TICK(false),
    ENTITY_TICK(false);

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

- [ ] **Step 4: Write `ProfileKey.java`**

```java
package io.github.edwardg117.renderable.core;

/** Identity of one timed thing. Implementations are value records so they work as map keys. */
public interface ProfileKey {

    enum Category { BLOCK_ENTITY, ENTITY, PARTICLE, BLOCK, SECTION, VISUAL }

    Category category();

    /** Registry id or class name used to roll individual keys up into a type row. */
    String typeId();

    /** One human-readable line for tables and overlay labels. */
    String label();

    /** Stable string form for JSON (spec §6.2). */
    String serialize();
}
```

- [ ] **Step 5: Write `BlockEntityKey.java` and `EntityKey.java`**

```java
package io.github.edwardg117.renderable.core;

public record BlockEntityKey(String typeId, String dimension, int x, int y, int z) implements ProfileKey {

    @Override
    public Category category() {
        return Category.BLOCK_ENTITY;
    }

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

```java
package io.github.edwardg117.renderable.core;

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
}
```

- [ ] **Step 6: Write `Accumulator.java`**

```java
package io.github.edwardg117.renderable.core;

/**
 * Counters for one key (spec §3.4). Fields are public and mutable on purpose: they are written on the
 * render thread's hot path and read only after the session is finished.
 */
public final class Accumulator {
    public final ProfileKey key;

    /** Number of timed render calls. */
    public long calls;
    /** Number of timed tick calls. */
    public long tickCalls;
    /** Nanoseconds spent in render-side buckets. */
    public long renderNanos;
    /** Nanoseconds spent in tick-side buckets. */
    public long tickNanos;
    /** Subset of renderNanos spent while the Iris shadow pass was active (phase 2 sets it). */
    public long shadowNanos;
    /** Largest render-plus-tick nanoseconds seen in a single frame. */
    public long maxFrameNanos;
    /** Vertices emitted while this key was innermost (phase 3 fills it). */
    public long vertices;

    /** Render plus tick nanoseconds in the current frame. Reset by Session at each frame boundary. */
    long frameNanos;
    /** True once this accumulator has been added to the session's touched list for the current frame. */
    boolean inFrame;

    public Accumulator(ProfileKey key) {
        this.key = key;
    }

    public long totalNanos() {
        return renderNanos + tickNanos;
    }

    public long frameNanos() {
        return frameNanos;
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.KeysTest' --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 6 tests passed.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/core src/test/java/io/github/edwardg117/renderable/core/KeysTest.java
git commit -m "feat(core): add profile keys, buckets and accumulator"
```

---

### Task 3: Session frame model (render, tick, swap, GC, duration)

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/SessionConfig.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/TopEntry.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/FrameRecord.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/Session.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/SessionTest.java`

The frame model (spec §3.2): a frame starts at `RenderFrameEvent.Pre` and ends at the next `Pre`. Render time is `Pre` to `Post`. Ticks that run between one frame's `Post` and the next `Pre` belong to the frame that just rendered. GC totals are read at each `Pre`; the delta belongs to the frame being closed.

- [ ] **Step 1: Write the failing tests**

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

class SessionTest {
    static final long MS = 1_000_000L;

    final long[] now = {0};
    final LongSupplier clock = () -> now[0];

    Session session(long durationMs) {
        return new Session(new SessionConfig(durationMs * MS, 2.0, 16 * MS, 5, 0), clock, 1_700_000_000_000L);
    }

    void at(long ms) {
        now[0] = ms * MS;
    }

    @Test
    void firstRenderPreOpensSessionWithoutRecordingAFrame() {
        Session s = session(10_000);
        at(0);
        assertFalse(s.onRenderPre(0, 0));
        assertEquals(0, s.frameCount());
        at(5);
        assertEquals(5 * MS, s.elapsedNanos());
    }

    @Test
    void frameSplitsIntoRenderTickAndSwap() {
        Session s = session(10_000);
        at(0);  s.onRenderPre(0, 0);
        at(4);  s.onRenderPost();
        at(5);  s.onTickPre();
        at(7);  s.onTickPost();
        at(10); s.onRenderPre(0, 0);

        assertEquals(1, s.frameCount());
        FrameRecord f = s.framesView().get(0);
        assertEquals(0, f.index());
        assertEquals(0, f.startNanos());
        assertEquals(10 * MS, f.totalNanos());
        assertEquals(4 * MS, f.renderNanos());
        assertEquals(2 * MS, f.tickNanos());
        assertEquals(4 * MS, f.swapNanos());
        assertEquals(4 * MS, f.otherRenderNanos());
        assertEquals(2 * MS, f.otherTickNanos());
    }

    @Test
    void secondFrameStartsAtOffsetOfFirstFrameEnd() {
        Session s = session(10_000);
        at(0);  s.onRenderPre(0, 0);
        at(10); s.onRenderPre(0, 0);
        at(25); s.onRenderPre(0, 0);
        FrameRecord f1 = s.framesView().get(1);
        assertEquals(1, f1.index());
        assertEquals(10 * MS, f1.startNanos());
        assertEquals(15 * MS, f1.totalNanos());
    }

    @Test
    void tickBeforeFirstFrameIsIgnored() {
        Session s = session(10_000);
        at(0);  s.onTickPre();
        at(2);  s.onTickPost();
        at(3);  s.onRenderPre(0, 0);
        at(4);  s.onRenderPost();
        at(10); s.onRenderPre(0, 0);
        assertEquals(0, s.framesView().get(0).tickNanos());
    }

    @Test
    void finishesWhenDurationElapses() {
        Session s = session(1000);
        at(0);    assertFalse(s.onRenderPre(0, 0));
        at(500);  assertFalse(s.onRenderPre(0, 0));
        at(999);  assertFalse(s.onRenderPre(0, 0));
        at(1000); assertTrue(s.onRenderPre(0, 0));
        assertEquals(3, s.frameCount());
    }

    @Test
    void gcDeltaIsAttributedToTheFrameThatJustClosed() {
        Session s = session(10_000);
        at(0);  s.onRenderPre(3, 100);
        at(10); s.onRenderPre(4, 112);
        at(20); s.onRenderPre(4, 112);
        assertEquals(1, s.framesView().get(0).gcCount());
        assertEquals(12, s.framesView().get(0).gcMillis());
        assertEquals(0, s.framesView().get(1).gcCount());
        assertEquals(0, s.framesView().get(1).gcMillis());
    }

    @Test
    void derivedValuesNeverGoNegative() {
        FrameRecord f = new FrameRecord(0, 0, 5 * MS, 4 * MS, 3 * MS, new long[Bucket.COUNT], 0, 0, List.of());
        assertEquals(0, f.swapNanos());

        long[] b = new long[Bucket.COUNT];
        b[Bucket.BLOCK_ENTITY.ordinal()] = 9 * MS;
        FrameRecord g = new FrameRecord(0, 0, 10 * MS, 0, 5 * MS, b, 0, 0, List.of());
        assertEquals(0, g.otherRenderNanos());
        assertEquals(9 * MS, g.bucket(Bucket.BLOCK_ENTITY));
    }

    @Test
    void configRejectsNonPositiveDuration() {
        assertThrows(IllegalArgumentException.class, () -> new SessionConfig(0, 2.0, 0, 5, 0));
        SessionConfig c = SessionConfig.ofSeconds(30, 2.0, 16, 5, 25);
        assertEquals(30_000 * MS, c.durationNanos());
        assertEquals(16 * MS, c.hitchMinExcessNanos());
        assertEquals(30.0, c.durationSeconds(), 1e-9);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.SessionTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: class Session`.

- [ ] **Step 3: Write `SessionConfig.java`**

```java
package io.github.edwardg117.renderable.core;

/**
 * Immutable settings for one session. Durations are nanoseconds so the core never converts units.
 *
 * @param durationNanos           how long to sample
 * @param hitchMedianMultiplier   spec §3.3, default 2.0
 * @param hitchMinExcessNanos     spec §3.3, default 16 ms
 * @param topContributorsPerFrame keys kept per frame, default 5
 * @param calibratedCallNanos     measured cost of one begin/end pair, for the overhead estimate
 */
public record SessionConfig(long durationNanos,
                            double hitchMedianMultiplier,
                            long hitchMinExcessNanos,
                            int topContributorsPerFrame,
                            long calibratedCallNanos) {

    public SessionConfig {
        if (durationNanos <= 0) {
            throw new IllegalArgumentException("durationNanos must be positive");
        }
        if (topContributorsPerFrame < 0) {
            throw new IllegalArgumentException("topContributorsPerFrame must be >= 0");
        }
    }

    public static SessionConfig ofSeconds(int seconds, double hitchMedianMultiplier, int hitchMinExcessMs,
                                          int topContributorsPerFrame, long calibratedCallNanos) {
        return new SessionConfig(seconds * 1_000_000_000L, hitchMedianMultiplier, hitchMinExcessMs * 1_000_000L,
                topContributorsPerFrame, calibratedCallNanos);
    }

    public double durationSeconds() {
        return durationNanos / 1e9;
    }
}
```

- [ ] **Step 4: Write `TopEntry.java` and `FrameRecord.java`**

```java
package io.github.edwardg117.renderable.core;

/** One of a frame's top contributors: the key and its render-plus-tick nanoseconds in that frame. */
public record TopEntry(ProfileKey key, long nanos) {
}
```

```java
package io.github.edwardg117.renderable.core;

import java.util.List;

/**
 * One sampled frame (spec §3.2).
 *
 * @param index       0-based frame number within the session
 * @param startNanos  offset of this frame's start from the session start
 * @param totalNanos  start of this frame to start of the next
 * @param tickNanos   client tick time that ran inside this frame
 * @param renderNanos RenderFrameEvent.Pre to Post
 * @param buckets     nanoseconds per {@link Bucket}, indexed by ordinal
 * @param gcCount     garbage collections observed during this frame
 * @param gcMillis    GC pause milliseconds observed during this frame
 * @param top         top contributors, most expensive first
 */
public record FrameRecord(int index,
                          long startNanos,
                          long totalNanos,
                          long tickNanos,
                          long renderNanos,
                          long[] buckets,
                          int gcCount,
                          long gcMillis,
                          List<TopEntry> top) {

    public long bucket(Bucket b) {
        return buckets[b.ordinal()];
    }

    public long renderBucketsSum() {
        long sum = 0;
        for (Bucket b : Bucket.VALUES) {
            if (b.isRender()) sum += buckets[b.ordinal()];
        }
        return sum;
    }

    public long tickBucketsSum() {
        long sum = 0;
        for (Bucket b : Bucket.VALUES) {
            if (!b.isRender()) sum += buckets[b.ordinal()];
        }
        return sum;
    }

    /** Render time not explained by any render bucket. Clamped at zero. */
    public long otherRenderNanos() {
        return Math.max(0, renderNanos - renderBucketsSum());
    }

    /** Tick time not explained by any tick bucket. Clamped at zero. */
    public long otherTickNanos() {
        return Math.max(0, tickNanos - tickBucketsSum());
    }

    /** Everything outside tick and render: window swap, vsync wait, input polling. Clamped at zero. */
    public long swapNanos() {
        return Math.max(0, totalNanos - tickNanos - renderNanos);
    }
}
```

- [ ] **Step 5: Write `Session.java` (frame model only; attribution is added in Task 4)**

```java
package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Live sampling state for one session (spec §3). All methods are called from the render thread only.
 * The clock is injected so tests can drive it.
 */
public final class Session {
    private final SessionConfig config;
    private final LongSupplier clock;
    private final long wallStartMillis;

    private final ArrayList<FrameRecord> frames = new ArrayList<>(4096);
    private final List<FrameRecord> framesView = Collections.unmodifiableList(frames);

    private long sessionStartNanos = -1;
    private long frameStartNanos = -1;
    private long renderStartNanos;
    private long tickStartNanos = -1;
    private long renderNanos;
    private long tickNanos;
    private final long[] buckets = new long[Bucket.COUNT];
    private long lastGcCount = -1;
    private long lastGcMillis;

    public Session(SessionConfig config, LongSupplier clock, long wallStartMillis) {
        this.config = config;
        this.clock = clock;
        this.wallStartMillis = wallStartMillis;
    }

    public SessionConfig config() {
        return config;
    }

    public long wallStartMillis() {
        return wallStartMillis;
    }

    /** Read-only live view of the frames recorded so far. */
    public List<FrameRecord> framesView() {
        return framesView;
    }

    public int frameCount() {
        return frames.size();
    }

    /** Nanoseconds since the first frame started, or 0 before that. */
    public long elapsedNanos() {
        return sessionStartNanos < 0 ? 0 : clock.getAsLong() - sessionStartNanos;
    }

    /**
     * Called at RenderFrameEvent.Pre with the JVM's cumulative GC count and pause time.
     * Closes the previous frame, if any, and opens a new one.
     *
     * @return true when the configured duration has elapsed and the caller should finish the session
     */
    public boolean onRenderPre(long gcCountTotal, long gcMillisTotal) {
        long now = clock.getAsLong();

        int gcCount = 0;
        long gcMillis = 0;
        if (lastGcCount >= 0) {
            gcCount = (int) Math.max(0, gcCountTotal - lastGcCount);
            gcMillis = Math.max(0, gcMillisTotal - lastGcMillis);
        }
        lastGcCount = gcCountTotal;
        lastGcMillis = gcMillisTotal;

        boolean done = false;
        if (frameStartNanos >= 0) {
            closeFrame(now, gcCount, gcMillis);
            done = now - sessionStartNanos >= config.durationNanos();
        } else {
            sessionStartNanos = now;
        }

        frameStartNanos = now;
        renderStartNanos = now;
        renderNanos = 0;
        tickNanos = 0;
        Arrays.fill(buckets, 0L);
        return done;
    }

    /** Called at RenderFrameEvent.Post. */
    public void onRenderPost() {
        if (frameStartNanos < 0) return;
        renderNanos += clock.getAsLong() - renderStartNanos;
    }

    /** Called at ClientTickEvent.Pre. */
    public void onTickPre() {
        tickStartNanos = clock.getAsLong();
    }

    /** Called at ClientTickEvent.Post. Ticks before the first frame are ignored. */
    public void onTickPost() {
        if (tickStartNanos < 0) return;
        long elapsed = clock.getAsLong() - tickStartNanos;
        tickStartNanos = -1;
        if (frameStartNanos >= 0) {
            tickNanos += elapsed;
        }
    }

    private void closeFrame(long now, int gcCount, long gcMillis) {
        frames.add(new FrameRecord(frames.size(), frameStartNanos - sessionStartNanos, now - frameStartNanos,
                tickNanos, renderNanos, buckets.clone(), gcCount, gcMillis, List.of()));
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.SessionTest' --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 8 tests passed.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/core src/test/java/io/github/edwardg117/renderable/core/SessionTest.java
git commit -m "feat(core): add session frame model with render, tick, swap and GC split"
```

---

### Task 4: Call attribution, nesting, per-frame top contributors

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Session.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/SessionTest.java` (append tests)

Rules (spec §3.2 and §3.4): every `begin` pushes onto a fixed-size stack; `end` pops, adds the elapsed time to the key, and adds it to the bucket only when the call was outermost (depth 0 after the pop). Inner calls are included in the outer call's elapsed time, so buckets never double count. Keys touched in a frame go on a touched-list; at frame close the top N by `frameNanos` are captured, `maxFrameNanos` is updated, and `frameNanos` is reset.

- [ ] **Step 1: Append the failing tests to `SessionTest`**

Add these inside the `SessionTest` class, after the existing tests:

```java
    final BlockEntityKey press = new BlockEntityKey("create:mechanical_press", "minecraft:overworld", 1, 2, 3);
    final BlockEntityKey mixer = new BlockEntityKey("create:mechanical_mixer", "minecraft:overworld", 4, 5, 6);
    final EntityKey cow = new EntityKey("minecraft:cow", 7, "");

    @Test
    void callsAttributeToKeyAndOnlyOutermostBucket() {
        Session s = session(10_000);
        at(0); s.onRenderPre(0, 0);
        Accumulator a = s.accumulator(press);
        Accumulator b = s.accumulator(cow);
        at(1);  s.begin(a, Bucket.BLOCK_ENTITY);
        at(3);  s.begin(b, Bucket.ENTITY);      // nested inside the block entity
        at(6);  s.end();                        // cow: 3 ms
        at(11); s.end();                        // press: 10 ms including the cow
        at(12); s.onRenderPost();
        at(20); s.onRenderPre(0, 0);

        assertEquals(10 * MS, a.renderNanos);
        assertEquals(1, a.calls);
        assertEquals(3 * MS, b.renderNanos);
        assertEquals(2, s.timedCalls());

        FrameRecord f = s.framesView().get(0);
        assertEquals(10 * MS, f.bucket(Bucket.BLOCK_ENTITY));
        assertEquals(0, f.bucket(Bucket.ENTITY), "inner call must not be counted in a second bucket");
        assertEquals(2 * MS, f.otherRenderNanos());
        assertSame(a, s.accumulator(press), "same key returns the same accumulator");
    }

    @Test
    void tickBucketsGoToTickNanos() {
        Session s = session(10_000);
        at(0); s.onRenderPre(0, 0);
        Accumulator a = s.accumulator(press);
        at(1); s.begin(a, Bucket.BE_TICK);
        at(3); s.end();
        at(10); s.onRenderPre(0, 0);
        assertEquals(2 * MS, a.tickNanos);
        assertEquals(0, a.renderNanos);
        assertEquals(1, a.tickCalls);
        assertEquals(0, a.calls);
        assertEquals(2 * MS, s.framesView().get(0).bucket(Bucket.BE_TICK));
    }

    @Test
    void topContributorsPerFrameAreRankedAndReset() {
        Session s = session(10_000);
        Accumulator a = s.accumulator(press);
        Accumulator b = s.accumulator(mixer);
        Accumulator c = s.accumulator(cow);

        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(b, Bucket.BLOCK_ENTITY); at(4); s.end();   // mixer 3
        at(4); s.begin(a, Bucket.BLOCK_ENTITY); at(9); s.end();   // press 5
        at(9); s.begin(c, Bucket.ENTITY);       at(10); s.end();  // cow 1
        at(20); s.onRenderPre(0, 0);

        List<TopEntry> top = s.framesView().get(0).top();
        assertEquals(3, top.size());
        assertEquals(press, top.get(0).key());
        assertEquals(5 * MS, top.get(0).nanos());
        assertEquals(mixer, top.get(1).key());
        assertEquals(cow, top.get(2).key());
        assertEquals(0, a.frameNanos(), "frame scratch must reset at the boundary");

        at(21); s.begin(b, Bucket.BLOCK_ENTITY); at(23); s.end(); // mixer 2, only key this frame
        at(40); s.onRenderPre(0, 0);
        List<TopEntry> top2 = s.framesView().get(1).top();
        assertEquals(1, top2.size());
        assertEquals(mixer, top2.get(0).key());
        assertEquals(2 * MS, top2.get(0).nanos());

        assertEquals(5 * MS, a.maxFrameNanos);
        assertEquals(3 * MS, b.maxFrameNanos, "peak is the biggest single frame, not the total");
    }

    @Test
    void topListIsCappedAtConfiguredSize() {
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 2, 0), clock, 0);
        at(0); s.onRenderPre(0, 0);
        for (int i = 0; i < 5; i++) {
            Accumulator a = s.accumulator(new EntityKey("minecraft:zombie", i, ""));
            at(10 * i); s.begin(a, Bucket.ENTITY); at(10 * i + i + 1); s.end(); // costs 1..5 ms
        }
        at(100); s.onRenderPre(0, 0);
        List<TopEntry> top = s.framesView().get(0).top();
        assertEquals(2, top.size());
        assertEquals(5 * MS, top.get(0).nanos());
        assertEquals(4 * MS, top.get(1).nanos());
    }

    @Test
    void sameKeyTwiceInOneFrameSumsInTop() {
        Session s = session(10_000);
        Accumulator a = s.accumulator(press);
        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(a, Bucket.BLOCK_ENTITY); at(3); s.end();
        at(5); s.begin(a, Bucket.BLOCK_ENTITY); at(9); s.end();
        at(20); s.onRenderPre(0, 0);
        List<TopEntry> top = s.framesView().get(0).top();
        assertEquals(1, top.size());
        assertEquals(6 * MS, top.get(0).nanos());
        assertEquals(2, a.calls);
    }

    @Test
    void shadowPassAddsToShadowNanos() {
        Session s = session(10_000);
        Accumulator a = s.accumulator(press);
        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(a, Bucket.BLOCK_ENTITY); at(3); s.end();
        s.shadowPass = true;
        at(4); s.begin(a, Bucket.BLOCK_ENTITY); at(5); s.end();
        s.shadowPass = false;
        assertEquals(3 * MS, a.renderNanos);
        assertEquals(1 * MS, a.shadowNanos);
    }

    @Test
    void endWithoutBeginIsIgnored() {
        Session s = session(10_000);
        at(0); s.onRenderPre(0, 0);
        assertDoesNotThrow(s::end);
        assertEquals(0, s.timedCalls());
    }

    @Test
    void nestingDeeperThanTheStackIsSafe() {
        Session s = session(10_000);
        Accumulator a = s.accumulator(press);
        at(0); s.onRenderPre(0, 0);
        at(1);
        for (int i = 0; i < 100; i++) s.begin(a, Bucket.BLOCK_ENTITY);
        at(2);
        for (int i = 0; i < 100; i++) s.end();
        at(10); s.onRenderPre(0, 0);
        assertEquals(Session.MAX_DEPTH, a.calls, "calls beyond the stack are dropped, not misattributed");
        assertEquals(1 * MS, s.framesView().get(0).bucket(Bucket.BLOCK_ENTITY));
    }

    @Test
    void callsBeforeFirstFrameCountTowardsKeyButNotFrame() {
        Session s = session(10_000);
        Accumulator a = s.accumulator(press);
        at(0); s.begin(a, Bucket.BLOCK_ENTITY); at(2); s.end();
        at(3); s.onRenderPre(0, 0);
        at(10); s.onRenderPre(0, 0);
        assertEquals(2 * MS, a.renderNanos);
        assertEquals(0, s.framesView().get(0).bucket(Bucket.BLOCK_ENTITY));
        assertTrue(s.framesView().get(0).top().isEmpty());
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.SessionTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: method accumulator`.

- [ ] **Step 3: Add attribution to `Session.java`**

Add these imports:

```java
import java.util.HashMap;
import java.util.Map;
```

Add these fields after `lastGcMillis`:

```java
    /** Maximum nesting depth tracked. Deeper calls are counted but not timed. */
    public static final int MAX_DEPTH = 64;

    private final Map<ProfileKey, Accumulator> accumulators = new HashMap<>(1024);
    private final ArrayList<Accumulator> touched = new ArrayList<>(1024);

    private final Accumulator[] stack = new Accumulator[MAX_DEPTH];
    private final long[] stackStart = new long[MAX_DEPTH];
    private final Bucket[] stackBucket = new Bucket[MAX_DEPTH];
    private int depth;
    private long timedCalls;

    /** Set by the Iris integration (phase 2) while the shadow pass is rendering. */
    public boolean shadowPass;
```

Add these methods after `elapsedNanos()`:

```java
    /** Number of completed begin/end pairs, for the overhead estimate. */
    public long timedCalls() {
        return timedCalls;
    }

    /** Snapshot of all accumulators; safe to call after the session is finished. */
    public Map<ProfileKey, Accumulator> accumulators() {
        return Map.copyOf(accumulators);
    }

    /** Returns the accumulator for a key, creating it on first sight. Hooks cache the result per instance. */
    public Accumulator accumulator(ProfileKey key) {
        return accumulators.computeIfAbsent(key, Accumulator::new);
    }

    /** Start timing a call attributed to {@code a} in {@code bucket}. Must be paired with {@link #end()}. */
    public void begin(Accumulator a, Bucket bucket) {
        long now = clock.getAsLong();
        if (depth < MAX_DEPTH) {
            stack[depth] = a;
            stackStart[depth] = now;
            stackBucket[depth] = bucket;
        }
        depth++;
    }

    /** Finish the innermost call. Extra calls with nothing open are ignored. */
    public void end() {
        if (depth == 0) return;
        depth--;
        if (depth >= MAX_DEPTH) return;

        long elapsed = clock.getAsLong() - stackStart[depth];
        Accumulator a = stack[depth];
        Bucket bucket = stackBucket[depth];
        stack[depth] = null;
        timedCalls++;

        if (bucket.isRender()) {
            a.renderNanos += elapsed;
            a.calls++;
            if (shadowPass) a.shadowNanos += elapsed;
        } else {
            a.tickNanos += elapsed;
            a.tickCalls++;
        }

        if (frameStartNanos >= 0) {
            if (!a.inFrame) {
                a.inFrame = true;
                touched.add(a);
            }
            a.frameNanos += elapsed;
            if (depth == 0) {
                buckets[bucket.ordinal()] += elapsed;
            }
        }
    }
```

Replace `closeFrame` with:

```java
    private void closeFrame(long now, int gcCount, long gcMillis) {
        int n = config.topContributorsPerFrame();
        Accumulator[] topAcc = new Accumulator[n];
        int topSize = 0;

        for (int i = 0, size = touched.size(); i < size; i++) {
            Accumulator a = touched.get(i);
            long fn = a.frameNanos;
            if (fn > a.maxFrameNanos) a.maxFrameNanos = fn;

            if (n == 0) continue;
            if (topSize < n) {
                int pos = topSize++;
                while (pos > 0 && topAcc[pos - 1].frameNanos < fn) {
                    topAcc[pos] = topAcc[pos - 1];
                    pos--;
                }
                topAcc[pos] = a;
            } else if (fn > topAcc[n - 1].frameNanos) {
                int pos = n - 1;
                while (pos > 0 && topAcc[pos - 1].frameNanos < fn) {
                    topAcc[pos] = topAcc[pos - 1];
                    pos--;
                }
                topAcc[pos] = a;
            }
        }

        List<TopEntry> top = new ArrayList<>(topSize);
        for (int i = 0; i < topSize; i++) {
            top.add(new TopEntry(topAcc[i].key, topAcc[i].frameNanos));
        }

        for (int i = 0, size = touched.size(); i < size; i++) {
            Accumulator a = touched.get(i);
            a.frameNanos = 0;
            a.inFrame = false;
        }
        touched.clear();

        frames.add(new FrameRecord(frames.size(), frameStartNanos - sessionStartNanos, now - frameStartNanos,
                tickNanos, renderNanos, buckets.clone(), gcCount, gcMillis, List.copyOf(top)));
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.SessionTest' --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 17 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/Session.java src/test/java/io/github/edwardg117/renderable/core/SessionTest.java
git commit -m "feat(core): attribute timed calls to keys and buckets with per-frame top contributors"
```

---

### Task 5: Frame statistics, hitch detection, finished result, calibration

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/FrameStats.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/HitchDetector.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/SessionResult.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/Calibration.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Session.java` (add `finish()`)
- Test: `src/test/java/io/github/edwardg117/renderable/core/TestSessions.java` (shared fixture)
- Test: `src/test/java/io/github/edwardg117/renderable/core/FrameStatsTest.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/HitchDetectorTest.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/CalibrationTest.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/SessionTest.java` (append one test)

- [ ] **Step 1: Write the shared fixture `TestSessions.java`**

This builds a deterministic 20-frame session that the core and report tests all use. Keep the numbers exactly as written; later assertions depend on them.

```java
package io.github.edwardg117.renderable.core;

/**
 * Deterministic session used by several tests.
 *
 * 20 frames. Every frame is 16 ms except frame 10, which is 80 ms because the press spends 60 ms in it.
 * Per frame: 0.1 ms other render, press 2 ms (60 ms in frame 10), press2 1.5 ms, mixer 1 ms, cow 0.5 ms,
 * player 0.3 ms, then 2 ms other render; tick is 1 ms of which cow ticks 0.2 ms. One 5 ms GC lands in frame 15.
 *
 * Totals: press 98 ms render, press2 30, mixer 20, cow 10 render + 4 tick, player 6. Session 384 ms.
 * Median frame 16 ms, hitch threshold 32 ms, one hitch (frame 10). 120 timed calls at 25 ns = 0.003 ms overhead.
 */
public final class TestSessions {
    public static final long MS = 1_000_000L;

    public static final BlockEntityKey PRESS = new BlockEntityKey("create:mechanical_press", "minecraft:overworld", 100, 64, -20);
    public static final BlockEntityKey PRESS2 = new BlockEntityKey("create:mechanical_press", "minecraft:overworld", 103, 64, -20);
    public static final BlockEntityKey MIXER = new BlockEntityKey("create:mechanical_mixer", "minecraft:overworld", 90, 65, -22);
    public static final EntityKey COW = new EntityKey("minecraft:cow", 7, "");
    public static final EntityKey PLAYER = new EntityKey("minecraft:player", 1, "Steve");

    public static final long WALL_START = 1_726_700_000_000L;

    private TestSessions() {
    }

    /** Public because the report tests in another package reuse it. */
    public static SessionResult sample() {
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 25), () -> now[0], WALL_START);
        Accumulator press = s.accumulator(PRESS);
        Accumulator press2 = s.accumulator(PRESS2);
        Accumulator mixer = s.accumulator(MIXER);
        Accumulator cow = s.accumulator(COW);
        Accumulator player = s.accumulator(PLAYER);

        long t = 0;
        for (int i = 0; i <= 20; i++) {
            long gcCount = i >= 16 ? 1 : 0;
            long gcMillis = i >= 16 ? 5 : 0;
            now[0] = t;
            s.onRenderPre(gcCount, gcMillis);
            if (i == 20) break;

            long frameStart = t;
            long pressCost = i == 10 ? 60 * MS : 2 * MS;

            t += 100_000;
            now[0] = t; s.begin(press, Bucket.BLOCK_ENTITY);  t += pressCost;  now[0] = t; s.end();
            now[0] = t; s.begin(press2, Bucket.BLOCK_ENTITY); t += 1_500_000;  now[0] = t; s.end();
            now[0] = t; s.begin(mixer, Bucket.BLOCK_ENTITY);  t += 1 * MS;     now[0] = t; s.end();
            now[0] = t; s.begin(cow, Bucket.ENTITY);          t += 500_000;    now[0] = t; s.end();
            now[0] = t; s.begin(player, Bucket.ENTITY);       t += 300_000;    now[0] = t; s.end();
            t += 2 * MS;
            now[0] = t; s.onRenderPost();

            now[0] = t; s.onTickPre();
            now[0] = t; s.begin(cow, Bucket.ENTITY_TICK);     t += 200_000;    now[0] = t; s.end();
            t += 800_000;
            now[0] = t; s.onTickPost();

            t = frameStart + (i == 10 ? 80 : 16) * MS;
        }
        return s.finish();
    }
}
```

- [ ] **Step 2: Write the failing tests**

`FrameStatsTest.java`:

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FrameStatsTest {
    static final long MS = 1_000_000L;

    static FrameRecord frame(int i, long totalMs, long tickMs, long renderMs, long blockEntityMs, int gc, long gcMs) {
        long[] b = new long[Bucket.COUNT];
        b[Bucket.BLOCK_ENTITY.ordinal()] = blockEntityMs * MS;
        return new FrameRecord(i, i * totalMs * MS, totalMs * MS, tickMs * MS, renderMs * MS, b, gc, gcMs, List.of());
    }

    @Test
    void percentilesUseNearestRank() {
        List<FrameRecord> frames = new ArrayList<>();
        for (int i = 1; i <= 10; i++) frames.add(frame(i - 1, 10L * i, 1, 5, 2, 0, 0));
        FrameStats st = FrameStats.of(frames);
        assertEquals(10, st.frames());
        assertEquals(550 * MS, st.totalNanos());
        assertEquals(55 * MS, st.avgNanos());
        assertEquals(50 * MS, st.p50Nanos());
        assertEquals(100 * MS, st.p95Nanos());
        assertEquals(100 * MS, st.p99Nanos());
        assertEquals(100 * MS, st.maxNanos());
        assertEquals(1e9 / (55 * MS), st.avgFps(), 1e-6);
        assertEquals(0.55, st.durationSeconds(), 1e-9);
    }

    @Test
    void averagesCoverTickRenderSwapBucketsAndGc() {
        List<FrameRecord> frames = List.of(
                frame(0, 20, 2, 10, 4, 0, 0),
                frame(1, 40, 4, 20, 8, 1, 6));
        FrameStats st = FrameStats.of(frames);
        assertEquals(3 * MS, st.avgTickNanos());
        assertEquals(15 * MS, st.avgRenderNanos());
        assertEquals(12 * MS, st.avgSwapNanos());
        assertEquals(6 * MS, st.avgBucket(Bucket.BLOCK_ENTITY));
        assertEquals(9 * MS, st.avgOtherRenderNanos());
        assertEquals(3 * MS, st.avgOtherTickNanos());
        assertEquals(1, st.gcCount());
        assertEquals(6, st.gcMillis());
    }

    @Test
    void emptyIsAllZero() {
        FrameStats st = FrameStats.of(List.of());
        assertEquals(0, st.frames());
        assertEquals(0, st.p99Nanos());
        assertEquals(0.0, st.avgFps());
        assertEquals(Bucket.COUNT, st.avgBucketNanos().length);
    }
}
```

`HitchDetectorTest.java`:

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HitchDetectorTest {
    static final long MS = 1_000_000L;

    static FrameRecord frame(int i, double totalMs) {
        return new FrameRecord(i, 0, (long) (totalMs * MS), 0, 0, new long[Bucket.COUNT], 0, 0, List.of());
    }

    @Test
    void thresholdIsTheLargerOfMultiplierAndMinimumExcess() {
        assertEquals((long) (33.2 * MS), HitchDetector.threshold((long) (16.6 * MS), 2.0, 16 * MS));
        assertEquals(23 * MS, HitchDetector.threshold(7 * MS, 2.0, 16 * MS));
    }

    @Test
    void detectReturnsFramesOverThresholdWorstFirst() {
        List<FrameRecord> frames = List.of(frame(0, 16), frame(1, 40), frame(2, 16), frame(3, 90), frame(4, 32));
        List<FrameRecord> hitches = HitchDetector.detect(frames, 32 * MS);
        assertEquals(2, hitches.size());
        assertEquals(3, hitches.get(0).index());
        assertEquals(1, hitches.get(1).index());
    }

    @Test
    void rollingCountUsesMedianOfRecentWindow() {
        List<FrameRecord> frames = List.of(frame(0, 16), frame(1, 16), frame(2, 16), frame(3, 50), frame(4, 16));
        assertEquals(1, HitchDetector.countRolling(frames, 120, 2.0, 16 * MS));
        assertEquals(0, HitchDetector.countRolling(List.of(), 120, 2.0, 16 * MS));
    }
}
```

`CalibrationTest.java`:

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CalibrationTest {

    @Test
    void fakeClockGivesExactPerPairCost() {
        long[] t = {0};
        // Each clock read advances 7 ns. begin + end = 2 reads = 14 ns per pair.
        long perPair = Calibration.measureCallNanos(() -> t[0] += 7);
        assertEquals(14, perPair);
    }

    @Test
    void realClockGivesSaneNumber() {
        long perPair = Calibration.measureCallNanos(System::nanoTime);
        assertTrue(perPair >= 0 && perPair < 10_000, "per-pair cost was " + perPair + " ns");
    }
}
```

Append to `SessionTest`:

```java
    @Test
    void finishProducesResultWithHitchesAndOverhead() {
        SessionResult r = TestSessions.sample();
        assertEquals(20, r.frameCount());
        assertEquals(384 * MS, r.stats().totalNanos());
        assertEquals(16 * MS, r.stats().p50Nanos());
        assertEquals(32 * MS, r.hitchThresholdNanos());
        assertEquals(1, r.hitches().size());
        assertEquals(10, r.hitches().get(0).index());
        assertEquals(80 * MS, r.hitches().get(0).totalNanos());
        assertEquals(TestSessions.PRESS, r.hitches().get(0).top().get(0).key());
        assertEquals(60 * MS, r.hitches().get(0).top().get(0).nanos());
        assertEquals(120, r.timedCalls());
        assertEquals(0.003, r.overheadEstimateMs(), 1e-9);
        assertEquals(5, r.hitchFlaggedKeys().size());
        assertTrue(r.hitchFlaggedKeys().contains(TestSessions.PRESS));
        assertEquals(98 * MS, r.accumulators().get(TestSessions.PRESS).renderNanos);
        assertEquals(4 * MS, r.accumulators().get(TestSessions.COW).tickNanos);
        assertEquals(1, r.frames().get(15).gcCount());
        assertEquals(5, r.frames().get(15).gcMillis());
        assertEquals(TestSessions.WALL_START, r.wallStartMillis());
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.*' --no-daemon
```

Expected: compilation failure, `cannot find symbol: class FrameStats`.

- [ ] **Step 4: Write `FrameStats.java`**

```java
package io.github.edwardg117.renderable.core;

import java.util.Arrays;
import java.util.List;

/** Whole-session frame statistics. Percentiles use nearest-rank on the sorted frame totals. */
public record FrameStats(int frames,
                         long totalNanos,
                         long avgNanos,
                         long p50Nanos,
                         long p95Nanos,
                         long p99Nanos,
                         long maxNanos,
                         long avgTickNanos,
                         long avgRenderNanos,
                         long avgSwapNanos,
                         long[] avgBucketNanos,
                         long avgOtherRenderNanos,
                         long avgOtherTickNanos,
                         int gcCount,
                         long gcMillis) {

    public static FrameStats of(List<FrameRecord> frames) {
        int n = frames.size();
        long[] avgBuckets = new long[Bucket.COUNT];
        if (n == 0) {
            return new FrameStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, avgBuckets, 0, 0, 0, 0);
        }

        long[] sorted = new long[n];
        long total = 0, tick = 0, render = 0, swap = 0, otherRender = 0, otherTick = 0, gcMillis = 0;
        int gcCount = 0;
        long[] bucketSums = new long[Bucket.COUNT];

        for (int i = 0; i < n; i++) {
            FrameRecord f = frames.get(i);
            sorted[i] = f.totalNanos();
            total += f.totalNanos();
            tick += f.tickNanos();
            render += f.renderNanos();
            swap += f.swapNanos();
            otherRender += f.otherRenderNanos();
            otherTick += f.otherTickNanos();
            gcCount += f.gcCount();
            gcMillis += f.gcMillis();
            long[] b = f.buckets();
            for (int k = 0; k < Bucket.COUNT; k++) bucketSums[k] += b[k];
        }
        Arrays.sort(sorted);
        for (int k = 0; k < Bucket.COUNT; k++) avgBuckets[k] = bucketSums[k] / n;

        return new FrameStats(n, total, total / n,
                percentile(sorted, 50), percentile(sorted, 95), percentile(sorted, 99), sorted[n - 1],
                tick / n, render / n, swap / n, avgBuckets, otherRender / n, otherTick / n, gcCount, gcMillis);
    }

    /** Nearest-rank percentile of an ascending array. */
    static long percentile(long[] sorted, double p) {
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    public long avgBucket(Bucket b) {
        return avgBucketNanos[b.ordinal()];
    }

    public double avgFps() {
        return avgNanos == 0 ? 0 : 1e9 / avgNanos;
    }

    public double durationSeconds() {
        return totalNanos / 1e9;
    }
}
```

- [ ] **Step 5: Write `HitchDetector.java`**

```java
package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Spec §3.3. */
public final class HitchDetector {

    private HitchDetector() {
    }

    public static long threshold(long medianNanos, double multiplier, long minExcessNanos) {
        return Math.max((long) (medianNanos * multiplier), medianNanos + minExcessNanos);
    }

    /** Frames strictly over the threshold, longest first. */
    public static List<FrameRecord> detect(List<FrameRecord> frames, long thresholdNanos) {
        ArrayList<FrameRecord> out = new ArrayList<>();
        for (FrameRecord f : frames) {
            if (f.totalNanos() > thresholdNanos) out.add(f);
        }
        out.sort(Comparator.comparingLong(FrameRecord::totalNanos).reversed());
        return List.copyOf(out);
    }

    /**
     * Live count for the HUD: threshold from the median of the last {@code window} frames, applied to all frames so far.
     */
    public static int countRolling(List<FrameRecord> frames, int window, double multiplier, long minExcessNanos) {
        int n = frames.size();
        if (n == 0) return 0;
        int from = Math.max(0, n - window);
        long[] recent = new long[n - from];
        for (int i = from; i < n; i++) recent[i - from] = frames.get(i).totalNanos();
        Arrays.sort(recent);
        long threshold = threshold(FrameStats.percentile(recent, 50), multiplier, minExcessNanos);
        int count = 0;
        for (FrameRecord f : frames) {
            if (f.totalNanos() > threshold) count++;
        }
        return count;
    }
}
```

- [ ] **Step 6: Write `SessionResult.java`**

```java
package io.github.edwardg117.renderable.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything a finished session knows. Immutable; produced by {@link Session#finish()}. */
public record SessionResult(SessionConfig config,
                            long wallStartMillis,
                            List<FrameRecord> frames,
                            Map<ProfileKey, Accumulator> accumulators,
                            FrameStats stats,
                            long hitchThresholdNanos,
                            List<FrameRecord> hitches,
                            long timedCalls,
                            double overheadEstimateMs) {

    public int frameCount() {
        return frames.size();
    }

    public long hitchTotalNanos() {
        long sum = 0;
        for (FrameRecord h : hitches) sum += h.totalNanos();
        return sum;
    }

    public double hitchSharePercent() {
        return stats.totalNanos() == 0 ? 0 : 100.0 * hitchTotalNanos() / stats.totalNanos();
    }

    /** Keys that appeared in any hitch's top contributors, in hitch order. */
    public Set<ProfileKey> hitchFlaggedKeys() {
        LinkedHashSet<ProfileKey> out = new LinkedHashSet<>();
        for (FrameRecord h : hitches) {
            for (TopEntry t : h.top()) out.add(t.key());
        }
        return out;
    }
}
```

- [ ] **Step 7: Write `Calibration.java`**

```java
package io.github.edwardg117.renderable.core;

import java.util.function.LongSupplier;

/** Spec §3.5: measures what one begin/end pair costs so the report can state its own overhead. */
public final class Calibration {
    private static final int WARMUP = 2_000;
    private static final int SAMPLES = 10_000;

    private Calibration() {
    }

    public static long measureCallNanos(LongSupplier clock) {
        Session s = new Session(new SessionConfig(Long.MAX_VALUE, 2.0, 0, 5, 0), clock, 0);
        Accumulator a = s.accumulator(new EntityKey("renderable:calibration", 0, ""));
        for (int i = 0; i < WARMUP; i++) {
            s.begin(a, Bucket.ENTITY);
            s.end();
        }
        long start = clock.getAsLong();
        for (int i = 0; i < SAMPLES; i++) {
            s.begin(a, Bucket.ENTITY);
            s.end();
        }
        long elapsed = clock.getAsLong() - start;
        return Math.max(0, elapsed / SAMPLES);
    }
}
```

- [ ] **Step 8: Add `finish()` to `Session.java`**

Add after `accumulator(...)`:

```java
    /** Freeze the session into a result. The frame currently open is dropped because it has no end. */
    public SessionResult finish() {
        List<FrameRecord> frameList = List.copyOf(frames);
        FrameStats stats = FrameStats.of(frameList);
        long threshold = HitchDetector.threshold(stats.p50Nanos(), config.hitchMedianMultiplier(), config.hitchMinExcessNanos());
        List<FrameRecord> hitches = HitchDetector.detect(frameList, threshold);
        double overheadMs = timedCalls * config.calibratedCallNanos() / 1_000_000.0;
        return new SessionResult(config, wallStartMillis, frameList, accumulators(), stats, threshold, hitches,
                timedCalls, overheadMs);
    }
```

- [ ] **Step 9: Run the tests to verify they pass**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.*' --no-daemon
```

Expected: `BUILD SUCCESSFUL`; SessionTest 18, FrameStatsTest 3, HitchDetectorTest 3, CalibrationTest 2, KeysTest 6.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/core src/test/java/io/github/edwardg117/renderable/core
git commit -m "feat(core): add frame stats, hitch detection, session result and overhead calibration"
```

---

### Task 6: Ranking, environment record, number formatting

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/RankedEntry.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/Ranking.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/Environment.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/Fmt.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/RankingTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.edwardg117.renderable.core.TestSessions.*;
import static org.junit.jupiter.api.Assertions.*;

class RankingTest {
    final SessionResult r = TestSessions.sample();

    @Test
    void byTypeRollsPositionsUp() {
        List<RankedEntry> types = Ranking.byType(r, ProfileKey.Category.BLOCK_ENTITY);
        assertEquals(2, types.size());
        RankedEntry press = types.get(0);
        assertEquals("create:mechanical_press", press.label());
        assertNull(press.key());
        assertEquals(2, press.members());
        assertEquals(128 * MS, press.renderNanos());
        assertEquals(40, press.calls());
        assertEquals(60 * MS, press.maxFrameNanos(), "group peak is the largest member peak");
        assertEquals("create:mechanical_mixer", types.get(1).label());
    }

    @Test
    void byKeyListsIndividualsMostExpensiveFirst() {
        List<RankedEntry> keys = Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY);
        assertEquals(List.of(PRESS, PRESS2, MIXER), keys.stream().map(RankedEntry::key).toList());
        assertEquals(1, keys.get(0).members());
        assertEquals(PRESS.label(), keys.get(0).label());
    }

    @Test
    void membersOfReturnsOnlyThatType() {
        List<RankedEntry> members = Ranking.membersOf(r, ProfileKey.Category.BLOCK_ENTITY, "create:mechanical_press");
        assertEquals(List.of(PRESS, PRESS2), members.stream().map(RankedEntry::key).toList());
    }

    @Test
    void derivedNumbers() {
        RankedEntry press = Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY).get(0);
        assertEquals(4900.0, press.usPerFrame(20), 1e-9);
        assertEquals(4900.0, press.renderUsPerFrame(20), 1e-9);
        assertEquals(0.0, press.tickUsPerFrame(20), 1e-9);
        assertEquals(100.0 * 98 / 384, press.sharePercent(r.stats().totalNanos()), 1e-9);
        assertEquals(0.0, press.shadowPercent(), 1e-9);

        RankedEntry cow = Ranking.byKey(r, ProfileKey.Category.ENTITY).get(0);
        assertEquals(COW, cow.key());
        assertEquals(14 * MS, cow.totalNanos());
        assertEquals(200.0, cow.tickUsPerFrame(20), 1e-9);
    }

    @Test
    void sortModesReorder() {
        List<RankedEntry> entities = Ranking.byKey(r, ProfileKey.Category.ENTITY);
        assertEquals(COW, Ranking.sort(entities, Ranking.Sort.TICK).get(0).key());
        assertEquals(COW, Ranking.sort(entities, Ranking.Sort.CALLS).get(0).key());
        List<RankedEntry> blocks = Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY);
        assertEquals(PRESS, Ranking.sort(blocks, Ranking.Sort.PEAK).get(0).key());
        assertEquals(Ranking.Sort.RENDER, Ranking.Sort.TOTAL.next());
        assertEquals(Ranking.Sort.TOTAL, Ranking.Sort.CALLS.next());
    }

    @Test
    void emptyCategoryGivesEmptyList() {
        assertTrue(Ranking.byKey(r, ProfileKey.Category.PARTICLE).isEmpty());
        assertTrue(Ranking.byType(r, ProfileKey.Category.PARTICLE).isEmpty());
    }

    @Test
    void formatting() {
        assertEquals("12.3", Fmt.ms(12_345_678));
        assertEquals("0.50", Fmt.ms2(500_000));
        assertEquals("4900.0", Fmt.us(4900.0));
        assertEquals("25.5%", Fmt.pct(25.52));
        assertEquals("0.4", Fmt.secs(384 * MS));
        assertEquals("1,234,567", Fmt.count(1_234_567));
    }

    @Test
    void environmentHoldsOrderedEntries() {
        Environment env = Environment.of(new java.util.LinkedHashMap<>(java.util.Map.of("minecraft", "1.21.1")), List.of("BLOCK_ENTITY_RENDER"));
        assertEquals("1.21.1", env.entries().get("minecraft"));
        assertEquals(List.of("BLOCK_ENTITY_RENDER"), env.hooksApplied());
        assertTrue(Environment.empty().entries().isEmpty());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.RankingTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: class RankedEntry`.

- [ ] **Step 3: Write `RankedEntry.java`**

```java
package io.github.edwardg117.renderable.core;

import java.util.List;

/**
 * One row of a ranked table: either an individual key ({@code key != null}, {@code members == 1})
 * or a type roll-up ({@code key == null}, {@code members} = number of keys summed).
 */
public record RankedEntry(String label,
                          ProfileKey key,
                          String typeId,
                          ProfileKey.Category category,
                          int members,
                          long calls,
                          long tickCalls,
                          long renderNanos,
                          long tickNanos,
                          long shadowNanos,
                          long maxFrameNanos,
                          long vertices) {

    public static RankedEntry of(Accumulator a) {
        return new RankedEntry(a.key.label(), a.key, a.key.typeId(), a.key.category(), 1,
                a.calls, a.tickCalls, a.renderNanos, a.tickNanos, a.shadowNanos, a.maxFrameNanos, a.vertices);
    }

    public static RankedEntry group(String typeId, ProfileKey.Category category, List<Accumulator> members) {
        long calls = 0, tickCalls = 0, render = 0, tick = 0, shadow = 0, peak = 0, vertices = 0;
        for (Accumulator a : members) {
            calls += a.calls;
            tickCalls += a.tickCalls;
            render += a.renderNanos;
            tick += a.tickNanos;
            shadow += a.shadowNanos;
            peak = Math.max(peak, a.maxFrameNanos);
            vertices += a.vertices;
        }
        return new RankedEntry(typeId, null, typeId, category, members.size(),
                calls, tickCalls, render, tick, shadow, peak, vertices);
    }

    public long totalNanos() {
        return renderNanos + tickNanos;
    }

    public double usPerFrame(int frames) {
        return frames == 0 ? 0 : totalNanos() / 1000.0 / frames;
    }

    public double renderUsPerFrame(int frames) {
        return frames == 0 ? 0 : renderNanos / 1000.0 / frames;
    }

    public double tickUsPerFrame(int frames) {
        return frames == 0 ? 0 : tickNanos / 1000.0 / frames;
    }

    public double sharePercent(long sessionTotalNanos) {
        return sessionTotalNanos == 0 ? 0 : 100.0 * totalNanos() / sessionTotalNanos;
    }

    public double shadowPercent() {
        return renderNanos == 0 ? 0 : 100.0 * shadowNanos / renderNanos;
    }

    public double verticesPerFrame(int frames) {
        return frames == 0 ? 0 : (double) vertices / frames;
    }
}
```

- [ ] **Step 4: Write `Ranking.java`**

```java
package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds ranked rows from a finished session. */
public final class Ranking {

    public enum Sort {
        TOTAL, RENDER, TICK, PEAK, VERTICES, CALLS;

        public Sort next() {
            Sort[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    private Ranking() {
    }

    /** One row per key of the category, most expensive first. */
    public static List<RankedEntry> byKey(SessionResult r, ProfileKey.Category category) {
        List<RankedEntry> out = new ArrayList<>();
        for (Accumulator a : r.accumulators().values()) {
            if (a.key.category() == category) out.add(RankedEntry.of(a));
        }
        return sort(out, Sort.TOTAL);
    }

    /** One row per type id of the category, most expensive first. */
    public static List<RankedEntry> byType(SessionResult r, ProfileKey.Category category) {
        Map<String, List<Accumulator>> groups = new LinkedHashMap<>();
        for (Accumulator a : r.accumulators().values()) {
            if (a.key.category() == category) {
                groups.computeIfAbsent(a.key.typeId(), k -> new ArrayList<>()).add(a);
            }
        }
        List<RankedEntry> out = new ArrayList<>(groups.size());
        for (Map.Entry<String, List<Accumulator>> e : groups.entrySet()) {
            out.add(RankedEntry.group(e.getKey(), category, e.getValue()));
        }
        return sort(out, Sort.TOTAL);
    }

    /** Individual rows of one type, most expensive first. */
    public static List<RankedEntry> membersOf(SessionResult r, ProfileKey.Category category, String typeId) {
        List<RankedEntry> out = new ArrayList<>();
        for (Accumulator a : r.accumulators().values()) {
            if (a.key.category() == category && a.key.typeId().equals(typeId)) out.add(RankedEntry.of(a));
        }
        return sort(out, Sort.TOTAL);
    }

    /** Returns a new list sorted by the chosen column, descending, ties broken by label. */
    public static List<RankedEntry> sort(List<RankedEntry> entries, Sort sort) {
        Comparator<RankedEntry> by = switch (sort) {
            case TOTAL -> Comparator.comparingLong(RankedEntry::totalNanos);
            case RENDER -> Comparator.comparingLong(RankedEntry::renderNanos);
            case TICK -> Comparator.comparingLong(RankedEntry::tickNanos);
            case PEAK -> Comparator.comparingLong(RankedEntry::maxFrameNanos);
            case VERTICES -> Comparator.comparingLong(RankedEntry::vertices);
            case CALLS -> Comparator.comparingLong(RankedEntry::calls);
        };
        List<RankedEntry> copy = new ArrayList<>(entries);
        copy.sort(by.reversed().thenComparing(RankedEntry::label));
        return copy;
    }
}
```

- [ ] **Step 5: Write `Environment.java` and `Fmt.java`**

```java
package io.github.edwardg117.renderable.core;

import java.util.List;
import java.util.Map;

/**
 * Plain environment description for reports (spec §6.1 item 2). Built by the Minecraft-aware collector in
 * {@code report}; the core only carries it. Entries keep insertion order.
 */
public record Environment(Map<String, String> entries, List<String> hooksApplied) {

    public static Environment of(Map<String, String> entries, List<String> hooksApplied) {
        return new Environment(entries, List.copyOf(hooksApplied));
    }

    public static Environment empty() {
        return new Environment(Map.of(), List.of());
    }
}
```

```java
package io.github.edwardg117.renderable.core;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Number and time formatting shared by reports and UI. All output uses Locale.ROOT. */
public final class Fmt {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private Fmt() {
    }

    /** Nanoseconds as milliseconds with one decimal. */
    public static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1e6);
    }

    /** Nanoseconds as milliseconds with two decimals. */
    public static String ms2(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1e6);
    }

    /** Microseconds with one decimal. */
    public static String us(double micros) {
        return String.format(Locale.ROOT, "%.1f", micros);
    }

    /** Nanoseconds as seconds with one decimal. */
    public static String secs(long nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1e9);
    }

    public static String pct(double percent) {
        return String.format(Locale.ROOT, "%.1f%%", percent);
    }

    public static String count(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    public static String timestamp(long epochMillis) {
        return TIMESTAMP.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()));
    }

    public static String fileStamp(long epochMillis) {
        return FILE_STAMP.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()));
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.*' --no-daemon
```

Expected: `BUILD SUCCESSFUL`, RankingTest 8 tests passed, all earlier tests still green.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/core src/test/java/io/github/edwardg117/renderable/core/RankingTest.java
git commit -m "feat(core): add ranking roll-ups, environment record and formatting helpers"
```

---

### Task 7: Text report writer

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/Splits.java`
- Create: `src/main/java/io/github/edwardg117/renderable/report/Table.java`
- Create: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`
- Test: `src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java`

Spec §6.1. The writer is pure Java and takes a finished `SessionResult`, an `Environment` and the mod version. Tables are fixed-width so they survive being pasted into a GitHub issue inside a code fence.

- [ ] **Step 1: Write the failing test**

```java
package io.github.edwardg117.renderable.report;

import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.TestSessions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextReportWriterTest {
    final SessionResult r = TestSessions.sample();
    final Environment env = environment();

    static Environment environment() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("minecraft", "1.21.1");
        m.put("gpu", "Test GPU");
        return Environment.of(m, List.of("BLOCK_ENTITY_RENDER", "ENTITY_RENDER"));
    }

    @Test
    void headerAndEnvironment() {
        String s = TextReportWriter.write(r, env, "0.1.0");
        assertTrue(s.startsWith("Renderable report  v0.1.0  "), s.substring(0, 40));
        assertTrue(s.contains("\nEnvironment\n  minecraft: 1.21.1\n  gpu: Test GPU\n  hooks applied: BLOCK_ENTITY_RENDER, ENTITY_RENDER\n"), s);
    }

    @Test
    void framesSection() {
        String s = TextReportWriter.write(r, env, "0.1.0");
        assertTrue(s.contains("  Sampled 0.4 s, 20 frames, avg 19.2 ms (52 fps), p50 16.0, p95 16.0, p99 80.0, max 80.0 ms\n"), s);
        assertTrue(s.contains("  Hitches: 1 frames over 32.0 ms threshold, totalling 0.1 s (20.8% of wall time)\n"), s);
        assertTrue(s.contains("  Time split (avg ms/frame): tick 1.00 | block entity 7.40 |"), s);
        assertTrue(s.contains("  GC: 1 collections, 5 ms paused\n"), s);
        assertTrue(s.contains("  Renderable overhead: about 0.00 ms over 120 timed calls\n"), s);
    }

    @Test
    void hitchSectionListsWorstFramesWithTopContributors() {
        String s = TextReportWriter.write(r, env, "0.1.0");
        int at = s.indexOf("Hitches (worst 25)\n");
        assertTrue(at > 0, s);
        String section = s.substring(at, s.indexOf("\n\n", at));
        assertTrue(section.contains("\n  #1  frame 10 @ 0.2s  80.0 ms  |  tick 1.0 | block entity 62.5 | entity 0.8 | entity tick 0.2 | other render 2.1 | swap 13.6  |  gc 0 ms\n"), section);
        assertTrue(section.contains("\n      60.00 ms  create:mechanical_press @ 100 64 -20\n"), section);
        assertTrue(section.contains("\n      1.50 ms  create:mechanical_press @ 103 64 -20\n"), section);
    }

    @Test
    void rankedTablesAlignAndEmptyCategoriesSaySo() {
        String s = TextReportWriter.write(r, env, "0.1.0");
        int at = s.indexOf("Block entities by type (top 20)\n");
        assertTrue(at > 0, s);
        String[] lines = s.substring(at).split("\n", 6);
        // lines[0] title, [1] header, [2] dashes, [3] press row, [4] mixer row
        assertEquals(lines[1].length(), lines[2].length());
        assertEquals(lines[1].length(), lines[3].length());
        assertEquals(lines[1].length(), lines[4].length());
        assertTrue(lines[1].startsWith("  name"), lines[1]);
        assertTrue(lines[3].startsWith("  create:mechanical_press"), lines[3]);
        assertTrue(lines[3].contains("6400.0"), "press type renders 128 ms over 20 frames = 6400 us/frame: " + lines[3]);
        assertTrue(lines[4].startsWith("  create:mechanical_mixer"), lines[4]);

        assertTrue(s.contains("Particles (top 20)\n  (no data: hook not active in this build)\n"), s);
        assertTrue(s.contains("Blocks (mesh build) (top 20)\n  (no data: hook not active in this build)\n"), s);
        assertTrue(s.contains("Entities (individuals) (top 20)\n"), s);
    }

    @Test
    void tableRendersAlignedColumns() {
        Table t = new Table(new String[]{"name", "n"}, new boolean[]{false, true});
        t.row("a", 1);
        t.row("long name", 1234);
        String expected = "name" + " ".repeat(10) + "n\n"
                + "-".repeat(9) + "  " + "-".repeat(4) + "\n"
                + "a" + " ".repeat(13) + "1\n"
                + "long name  1234\n";
        assertEquals(expected, t.render(""));
        assertThrows(IllegalArgumentException.class, () -> t.row("too", "many", "cells"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.report.TextReportWriterTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: class Table`.

- [ ] **Step 3: Write `Table.java`**

```java
package io.github.edwardg117.renderable.report;

import java.util.ArrayList;
import java.util.List;

/** Fixed-width plain-text table. Columns are two spaces apart; right-aligned columns are for numbers. */
final class Table {
    private final String[] headers;
    private final boolean[] rightAlign;
    private final List<String[]> rows = new ArrayList<>();

    Table(String[] headers, boolean[] rightAlign) {
        if (headers.length != rightAlign.length) {
            throw new IllegalArgumentException("headers and rightAlign must have the same length");
        }
        this.headers = headers;
        this.rightAlign = rightAlign;
    }

    void row(Object... cells) {
        if (cells.length != headers.length) {
            throw new IllegalArgumentException("expected " + headers.length + " cells, got " + cells.length);
        }
        String[] r = new String[cells.length];
        for (int i = 0; i < cells.length; i++) r[i] = String.valueOf(cells[i]);
        rows.add(r);
    }

    String render(String indent) {
        int[] w = new int[headers.length];
        for (int i = 0; i < headers.length; i++) w[i] = headers[i].length();
        for (String[] r : rows) {
            for (int i = 0; i < r.length; i++) w[i] = Math.max(w[i], r[i].length());
        }

        StringBuilder sb = new StringBuilder();
        line(sb, indent, headers, w);
        sb.append(indent);
        for (int i = 0; i < w.length; i++) {
            if (i > 0) sb.append("  ");
            sb.append("-".repeat(w[i]));
        }
        sb.append('\n');
        for (String[] r : rows) line(sb, indent, r, w);
        return sb.toString();
    }

    private void line(StringBuilder sb, String indent, String[] cells, int[] w) {
        sb.append(indent);
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append("  ");
            String c = cells[i];
            int pad = w[i] - c.length();
            if (rightAlign[i]) {
                sb.append(" ".repeat(pad)).append(c);
            } else {
                sb.append(c).append(" ".repeat(pad));
            }
        }
        sb.append('\n');
    }
}
```

Then `core/Splits.java`, the one-line bucket split descriptions shared by the text report (this task) and the results screen (Task 12):

```java
package io.github.edwardg117.renderable.core;

/** One-line descriptions of how a frame, or the average frame, was spent. */
public final class Splits {

    private Splits() {
    }

    /** Average per-frame split: tick, every bucket, other render, other tick, swap. Two decimals. */
    public static String average(FrameStats st) {
        StringBuilder sb = new StringBuilder();
        sb.append("tick ").append(Fmt.ms2(st.avgTickNanos()));
        for (Bucket b : Bucket.VALUES) {
            sb.append(" | ").append(b.label()).append(' ').append(Fmt.ms2(st.avgBucket(b)));
        }
        sb.append(" | other render ").append(Fmt.ms2(st.avgOtherRenderNanos()));
        sb.append(" | other tick ").append(Fmt.ms2(st.avgOtherTickNanos()));
        sb.append(" | swap/wait ").append(Fmt.ms2(st.avgSwapNanos()));
        return sb.toString();
    }

    /** One frame's split, skipping empty buckets. One decimal. */
    public static String frame(FrameRecord f) {
        StringBuilder sb = new StringBuilder();
        sb.append("tick ").append(Fmt.ms(f.tickNanos()));
        for (Bucket b : Bucket.VALUES) {
            long v = f.bucket(b);
            if (v > 0) sb.append(" | ").append(b.label()).append(' ').append(Fmt.ms(v));
        }
        sb.append(" | other render ").append(Fmt.ms(f.otherRenderNanos()));
        sb.append(" | swap ").append(Fmt.ms(f.swapNanos()));
        return sb.toString();
    }
}
```

- [ ] **Step 4: Write `TextReportWriter.java`**

```java
package io.github.edwardg117.renderable.report;

import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.Fmt;
import io.github.edwardg117.renderable.core.FrameRecord;
import io.github.edwardg117.renderable.core.FrameStats;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.RankedEntry;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.Splits;
import io.github.edwardg117.renderable.core.TopEntry;

import java.util.List;
import java.util.Locale;

/** Spec §6.1: the human-readable report. Pure Java. */
public final class TextReportWriter {
    static final int TOP = 20;
    static final int HITCHES = 25;

    private TextReportWriter() {
    }

    public static String write(SessionResult r, Environment env, String modVersion) {
        StringBuilder sb = new StringBuilder(16_384);
        FrameStats st = r.stats();
        int frames = r.frameCount();
        long total = st.totalNanos();

        sb.append("Renderable report  v").append(modVersion)
                .append("  ").append(Fmt.timestamp(r.wallStartMillis()))
                .append("  session ").append(Fmt.secs(total)).append(" s\n\n");

        sb.append("Environment\n");
        env.entries().forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append('\n'));
        sb.append("  hooks applied: ")
                .append(env.hooksApplied().isEmpty() ? "none" : String.join(", ", env.hooksApplied()))
                .append("\n\n");

        sb.append("Frames\n");
        sb.append(String.format(Locale.ROOT,
                "  Sampled %s s, %d frames, avg %s ms (%.0f fps), p50 %s, p95 %s, p99 %s, max %s ms%n",
                Fmt.secs(total), frames, Fmt.ms(st.avgNanos()), st.avgFps(),
                Fmt.ms(st.p50Nanos()), Fmt.ms(st.p95Nanos()), Fmt.ms(st.p99Nanos()), Fmt.ms(st.maxNanos())));
        sb.append(String.format(Locale.ROOT,
                "  Hitches: %d frames over %s ms threshold, totalling %s s (%s of wall time)%n",
                r.hitches().size(), Fmt.ms(r.hitchThresholdNanos()), Fmt.secs(r.hitchTotalNanos()),
                Fmt.pct(r.hitchSharePercent())));
        sb.append("  Time split (avg ms/frame): ").append(Splits.average(st)).append('\n');
        sb.append(String.format(Locale.ROOT, "  GC: %d collections, %d ms paused%n", st.gcCount(), st.gcMillis()));
        sb.append(String.format(Locale.ROOT, "  Renderable overhead: about %.2f ms over %d timed calls%n%n",
                r.overheadEstimateMs(), r.timedCalls()));

        sb.append("Hitches (worst ").append(HITCHES).append(")\n");
        if (r.hitches().isEmpty()) {
            sb.append("  none\n");
        }
        int shown = 0;
        for (FrameRecord h : r.hitches()) {
            if (shown == HITCHES) break;
            shown++;
            sb.append(String.format(Locale.ROOT, "  #%d  frame %d @ %ss  %s ms  |  %s  |  gc %d ms%n",
                    shown, h.index(), Fmt.secs(h.startNanos()), Fmt.ms(h.totalNanos()), Splits.frame(h), h.gcMillis()));
            for (TopEntry t : h.top()) {
                sb.append("      ").append(Fmt.ms2(t.nanos())).append(" ms  ").append(t.key().label()).append('\n');
            }
        }
        sb.append('\n');

        appendRanked(sb, "Block entities by type", Ranking.byType(r, ProfileKey.Category.BLOCK_ENTITY), frames, total);
        appendRanked(sb, "Block entities by position", Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY), frames, total);
        appendRanked(sb, "Entities by type", Ranking.byType(r, ProfileKey.Category.ENTITY), frames, total);
        appendRanked(sb, "Entities (individuals)", Ranking.byKey(r, ProfileKey.Category.ENTITY), frames, total);
        appendRanked(sb, "Particles", Ranking.byKey(r, ProfileKey.Category.PARTICLE), frames, total);
        appendRanked(sb, "Blocks (mesh build)", Ranking.byKey(r, ProfileKey.Category.BLOCK), frames, total);
        appendRanked(sb, "Sections (mesh rebuilds)", Ranking.byKey(r, ProfileKey.Category.SECTION), frames, total);
        appendRanked(sb, "Flywheel visuals", Ranking.byKey(r, ProfileKey.Category.VISUAL), frames, total);
        return sb.toString();
    }

    static void appendRanked(StringBuilder sb, String title, List<RankedEntry> entries, int frames, long total) {
        sb.append(title).append(" (top ").append(TOP).append(")\n");
        if (entries.isEmpty()) {
            sb.append("  (no data: hook not active in this build)\n\n");
            return;
        }
        Table t = new Table(
                new String[]{"name", "render us/f", "tick us/f", "share", "peak ms", "shadow", "verts/f", "calls"},
                new boolean[]{false, true, true, true, true, true, true, true});
        for (RankedEntry e : entries.subList(0, Math.min(TOP, entries.size()))) {
            t.row(e.label(),
                    Fmt.us(e.renderUsPerFrame(frames)),
                    Fmt.us(e.tickUsPerFrame(frames)),
                    Fmt.pct(e.sharePercent(total)),
                    Fmt.ms2(e.maxFrameNanos()),
                    Fmt.pct(e.shadowPercent()),
                    Fmt.us(e.verticesPerFrame(frames)),
                    Fmt.count(e.calls()));
        }
        sb.append(t.render("  ")).append('\n');
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.report.TextReportWriterTest' --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 5 tests passed. If `hitchSectionListsWorstFramesWithTopContributors` fails on the split line, print the section and compare bucket labels; the fixture's frame 10 has block entity 62.5, entity 0.8, entity tick 0.2, other render 2.1, swap 13.6.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/report src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java
git commit -m "feat(report): add fixed-width text report writer"
```

---

### Task 8: JSON report writer

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/report/JsonReportWriter.java`
- Test: `src/test/java/io/github/edwardg117/renderable/report/JsonReportWriterTest.java`

Spec §6.2. Uses Gson, which ships with Minecraft and is declared for tests in `build.gradle`. Microsecond integers throughout; milliseconds only where the spec names them.

- [ ] **Step 1: Write the failing test**

```java
package io.github.edwardg117.renderable.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.TestSessions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonReportWriterTest {
    final SessionResult r = TestSessions.sample();

    static Environment environment() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("minecraft", "1.21.1");
        return Environment.of(m, List.of("BLOCK_ENTITY_RENDER", "ENTITY_RENDER"));
    }

    JsonObject parse() {
        return JsonParser.parseString(JsonReportWriter.write(r, environment(), "0.1.0")).getAsJsonObject();
    }

    @Test
    void schemaModEnvironmentAndSession() {
        JsonObject o = parse();
        assertEquals(1, o.get("schema").getAsInt());
        assertEquals("0.1.0", o.getAsJsonObject("mod").get("version").getAsString());
        assertEquals("1.21.1", o.getAsJsonObject("environment").get("minecraft").getAsString());
        assertEquals(2, o.getAsJsonObject("environment").getAsJsonArray("hooksApplied").size());

        JsonObject session = o.getAsJsonObject("session");
        assertEquals(20, session.get("frames").getAsInt());
        assertEquals(32.0, session.get("hitchThresholdMs").getAsDouble(), 1e-9);
        assertEquals(0.384, session.get("durationSeconds").getAsDouble(), 1e-9);
        assertEquals(120, session.get("timedCalls").getAsLong());
        assertEquals(5, session.getAsJsonObject("config").get("topContributorsPerFrame").getAsInt());
        assertTrue(session.get("startedAt").getAsString().startsWith("2024-09-18T") || session.get("startedAt").getAsString().startsWith("2024-09-19T"));

        JsonObject stats = o.getAsJsonObject("stats");
        assertEquals(16.0, stats.get("p50Ms").getAsDouble(), 1e-9);
        assertEquals(80.0, stats.get("maxMs").getAsDouble(), 1e-9);
        assertEquals(7.4, stats.getAsJsonObject("avgBucketsMs").get("block entity").getAsDouble(), 1e-9);
    }

    @Test
    void framesAndHitches() {
        JsonObject o = parse();
        JsonArray frames = o.getAsJsonArray("frames");
        assertEquals(20, frames.size());
        JsonObject f10 = frames.get(10).getAsJsonObject();
        assertEquals(10, f10.get("i").getAsInt());
        assertEquals(160_000, f10.get("startUs").getAsLong());
        assertEquals(80_000, f10.get("totalUs").getAsLong());
        assertEquals(1_000, f10.get("tickUs").getAsLong());
        assertEquals(65_400, f10.get("renderUs").getAsLong());
        assertEquals(13_600, f10.get("swapUs").getAsLong());
        assertEquals(62_500, f10.getAsJsonObject("bucketsUs").get("block entity").getAsLong());
        assertFalse(f10.getAsJsonObject("bucketsUs").has("flywheel"), "empty buckets are omitted");
        JsonObject top0 = f10.getAsJsonArray("top").get(0).getAsJsonObject();
        assertEquals("create:mechanical_press@minecraft:overworld:100,64,-20", top0.get("key").getAsString());
        assertEquals(60_000, top0.get("us").getAsLong());

        JsonObject f15 = frames.get(15).getAsJsonObject();
        assertEquals(1, f15.get("gcCount").getAsInt());
        assertEquals(5, f15.get("gcMs").getAsLong());

        JsonArray hitches = o.getAsJsonArray("hitches");
        assertEquals(1, hitches.size());
        assertEquals(10, hitches.get(0).getAsInt());
    }

    @Test
    void rankedSectionsAreComplete() {
        JsonObject o = parse();
        JsonObject be = o.getAsJsonObject("blockEntities");
        JsonObject pressType = be.getAsJsonArray("byType").get(0).getAsJsonObject();
        assertEquals("create:mechanical_press", pressType.get("key").getAsString());
        assertEquals(2, pressType.get("members").getAsInt());
        assertEquals(128_000, pressType.get("renderUs").getAsLong());
        assertEquals(3, be.getAsJsonArray("byPosition").size(), "JSON is not truncated");
        JsonObject press = be.getAsJsonArray("byPosition").get(0).getAsJsonObject();
        assertEquals("create:mechanical_press@minecraft:overworld:100,64,-20", press.get("key").getAsString());
        assertEquals(98_000, press.get("renderUs").getAsLong());
        assertEquals(60_000, press.get("peakFrameUs").getAsLong());
        assertEquals(20, press.get("calls").getAsLong());

        JsonObject cow = o.getAsJsonObject("entities").getAsJsonArray("individuals").get(0).getAsJsonObject();
        assertEquals("minecraft:cow#7", cow.get("key").getAsString());
        assertEquals(4_000, cow.get("tickUs").getAsLong());

        assertEquals(0, o.getAsJsonArray("particles").size());
        assertEquals(0, o.getAsJsonArray("blocks").size());
        assertEquals(0, o.getAsJsonArray("sections").size());
        assertEquals(0, o.getAsJsonObject("flywheel").getAsJsonArray("visuals").size());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.report.JsonReportWriterTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: class JsonReportWriter`.

- [ ] **Step 3: Write `JsonReportWriter.java`**

```java
package io.github.edwardg117.renderable.report;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.FrameRecord;
import io.github.edwardg117.renderable.core.FrameStats;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.RankedEntry;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.TopEntry;

import java.time.Instant;
import java.util.List;

/** Spec §6.2: complete, untruncated machine-readable report. */
public final class JsonReportWriter {
    public static final int SCHEMA = 1;

    private JsonReportWriter() {
    }

    public static String write(SessionResult r, Environment env, String modVersion) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);

        JsonObject mod = new JsonObject();
        mod.addProperty("version", modVersion);
        root.add("mod", mod);

        JsonObject environment = new JsonObject();
        env.entries().forEach(environment::addProperty);
        JsonArray hooks = new JsonArray();
        env.hooksApplied().forEach(hooks::add);
        environment.add("hooksApplied", hooks);
        root.add("environment", environment);

        FrameStats st = r.stats();
        JsonObject session = new JsonObject();
        session.addProperty("startedAt", Instant.ofEpochMilli(r.wallStartMillis()).toString());
        session.addProperty("durationSeconds", st.durationSeconds());
        session.addProperty("frames", r.frameCount());
        session.addProperty("hitchThresholdMs", r.hitchThresholdNanos() / 1e6);
        session.addProperty("overheadEstimateMs", r.overheadEstimateMs());
        session.addProperty("timedCalls", r.timedCalls());
        JsonObject config = new JsonObject();
        config.addProperty("durationSeconds", r.config().durationSeconds());
        config.addProperty("hitchMedianMultiplier", r.config().hitchMedianMultiplier());
        config.addProperty("hitchMinExcessMs", r.config().hitchMinExcessNanos() / 1e6);
        config.addProperty("topContributorsPerFrame", r.config().topContributorsPerFrame());
        session.add("config", config);
        root.add("session", session);

        JsonObject stats = new JsonObject();
        stats.addProperty("avgMs", st.avgNanos() / 1e6);
        stats.addProperty("avgFps", st.avgFps());
        stats.addProperty("p50Ms", st.p50Nanos() / 1e6);
        stats.addProperty("p95Ms", st.p95Nanos() / 1e6);
        stats.addProperty("p99Ms", st.p99Nanos() / 1e6);
        stats.addProperty("maxMs", st.maxNanos() / 1e6);
        stats.addProperty("avgTickMs", st.avgTickNanos() / 1e6);
        stats.addProperty("avgRenderMs", st.avgRenderNanos() / 1e6);
        stats.addProperty("avgSwapMs", st.avgSwapNanos() / 1e6);
        stats.addProperty("avgOtherRenderMs", st.avgOtherRenderNanos() / 1e6);
        stats.addProperty("avgOtherTickMs", st.avgOtherTickNanos() / 1e6);
        stats.addProperty("gcCount", st.gcCount());
        stats.addProperty("gcMs", st.gcMillis());
        JsonObject avgBuckets = new JsonObject();
        for (Bucket b : Bucket.VALUES) avgBuckets.addProperty(b.label(), st.avgBucket(b) / 1e6);
        stats.add("avgBucketsMs", avgBuckets);
        root.add("stats", stats);

        JsonArray frames = new JsonArray();
        for (FrameRecord f : r.frames()) {
            JsonObject o = new JsonObject();
            o.addProperty("i", f.index());
            o.addProperty("startUs", f.startNanos() / 1000);
            o.addProperty("totalUs", f.totalNanos() / 1000);
            o.addProperty("tickUs", f.tickNanos() / 1000);
            o.addProperty("renderUs", f.renderNanos() / 1000);
            o.addProperty("swapUs", f.swapNanos() / 1000);
            o.addProperty("otherRenderUs", f.otherRenderNanos() / 1000);
            JsonObject buckets = new JsonObject();
            for (Bucket b : Bucket.VALUES) {
                long v = f.bucket(b);
                if (v > 0) buckets.addProperty(b.label(), v / 1000);
            }
            o.add("bucketsUs", buckets);
            o.addProperty("gcCount", f.gcCount());
            o.addProperty("gcMs", f.gcMillis());
            JsonArray top = new JsonArray();
            for (TopEntry t : f.top()) {
                JsonObject te = new JsonObject();
                te.addProperty("key", t.key().serialize());
                te.addProperty("us", t.nanos() / 1000);
                top.add(te);
            }
            o.add("top", top);
            frames.add(o);
        }
        root.add("frames", frames);

        JsonArray hitches = new JsonArray();
        for (FrameRecord h : r.hitches()) hitches.add(h.index());
        root.add("hitches", hitches);

        root.add("blockEntities", category(r, ProfileKey.Category.BLOCK_ENTITY, "byType", "byPosition"));
        root.add("entities", category(r, ProfileKey.Category.ENTITY, "byType", "individuals"));
        root.add("particles", ranked(Ranking.byKey(r, ProfileKey.Category.PARTICLE)));
        root.add("blocks", ranked(Ranking.byKey(r, ProfileKey.Category.BLOCK)));
        root.add("sections", ranked(Ranking.byKey(r, ProfileKey.Category.SECTION)));
        JsonObject flywheel = new JsonObject();
        long flywheelNanos = 0;
        for (FrameRecord f : r.frames()) flywheelNanos += f.bucket(Bucket.FLYWHEEL);
        flywheel.addProperty("bucketUs", flywheelNanos / 1000);
        flywheel.add("visuals", ranked(Ranking.byKey(r, ProfileKey.Category.VISUAL)));
        root.add("flywheel", flywheel);

        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    private static JsonObject category(SessionResult r, ProfileKey.Category c, String typeName, String keyName) {
        JsonObject o = new JsonObject();
        o.add(typeName, ranked(Ranking.byType(r, c)));
        o.add(keyName, ranked(Ranking.byKey(r, c)));
        return o;
    }

    private static JsonArray ranked(List<RankedEntry> entries) {
        JsonArray arr = new JsonArray();
        for (RankedEntry e : entries) {
            JsonObject o = new JsonObject();
            o.addProperty("key", e.key() == null ? e.typeId() : e.key().serialize());
            o.addProperty("label", e.label());
            o.addProperty("typeId", e.typeId());
            o.addProperty("members", e.members());
            o.addProperty("calls", e.calls());
            o.addProperty("tickCalls", e.tickCalls());
            o.addProperty("renderUs", e.renderNanos() / 1000);
            o.addProperty("tickUs", e.tickNanos() / 1000);
            o.addProperty("shadowUs", e.shadowNanos() / 1000);
            o.addProperty("peakFrameUs", e.maxFrameNanos() / 1000);
            o.addProperty("vertices", e.vertices());
            arr.add(o);
        }
        return arr;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.report.*' --no-daemon
```

Expected: `BUILD SUCCESSFUL`, JsonReportWriterTest 3 and TextReportWriterTest 5 passed.

- [ ] **Step 5: Run the whole suite and commit**

```bash
./gradlew test --no-daemon
git add src/main/java/io/github/edwardg117/renderable/report/JsonReportWriter.java src/test/java/io/github/edwardg117/renderable/report/JsonReportWriterTest.java
git commit -m "feat(report): add JSON report writer with schema 1"
```

---

### Task 9: Probe, mixins and frame events

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/Hook.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/Probe.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/KeyFactory.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/GcSampler.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/FrameEvents.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/RenderableMixinPlugin.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/BlockEntityRenderDispatcherMixin.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/EntityRenderDispatcherMixin.java`
- Modify: `src/main/resources/renderable.mixins.json`
- Modify: `src/main/java/io/github/edwardg117/renderable/RenderableMod.java`

Spec §4.1 and §4.5. There are no unit tests for this task because every class touches Minecraft; verification is that the client starts with the mixins applied. `Probe` exposes a `Listener` so the controller (Task 10) can react to frame start, finish and discard without `hooks` depending on the root package.

- [ ] **Step 1: Write `Hook.java`**

```java
package io.github.edwardg117.renderable.hooks;

/** Every hook the mod can install. A hook is "applied" once it has fired at least once in this client run. */
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
    VERTICES
}
```

- [ ] **Step 2: Write `Probe.java`**

```java
package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Session;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * The single static entry point every hook calls (spec §4). Mixins check {@link #ACTIVE} first, so the idle
 * cost is one volatile read. Accumulators are cached per Minecraft object in an identity map so the hot path
 * allocates nothing after a key has been seen once.
 */
public final class Probe {

    /** Implemented by the controller. All calls happen on the render thread. */
    public interface Listener {
        /** A new frame has just been opened in the session. */
        void onFrameStarted(Session session);

        /** The session's duration has elapsed; the listener should finish it. */
        void onFinishRequested();

        /** The level went away; the listener should discard the running session. */
        void onDiscardRequested(String reason);
    }

    /** True while a session is sampling. Volatile so worker-thread hooks in later phases see it. */
    public static volatile boolean ACTIVE;

    private static Session session;
    private static Listener listener;
    private static final IdentityHashMap<Object, Accumulator> BY_INSTANCE = new IdentityHashMap<>(4096);
    private static final boolean[] SEEN = new boolean[Hook.values().length];

    private Probe() {
    }

    public static void setListener(Listener l) {
        listener = l;
    }

    static Listener listener() {
        return listener;
    }

    public static void attach(Session s) {
        BY_INSTANCE.clear();
        session = s;
        ACTIVE = true;
    }

    public static void detach() {
        ACTIVE = false;
        session = null;
        BY_INSTANCE.clear();
    }

    /** The running session, or null. */
    public static Session session() {
        return session;
    }

    /** Names of hooks that have fired at least once, in enum order. */
    public static List<String> hooksApplied() {
        List<String> out = new ArrayList<>();
        for (Hook h : Hook.values()) {
            if (SEEN[h.ordinal()]) out.add(h.name());
        }
        return out;
    }

    public static void beginBlockEntity(BlockEntity be) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.BLOCK_ENTITY_RENDER.ordinal()] = true;
        Accumulator a = BY_INSTANCE.get(be);
        if (a == null) {
            a = s.accumulator(KeyFactory.blockEntity(be));
            BY_INSTANCE.put(be, a);
        }
        s.begin(a, Bucket.BLOCK_ENTITY);
    }

    public static void beginEntity(Entity e) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.ENTITY_RENDER.ordinal()] = true;
        Accumulator a = BY_INSTANCE.get(e);
        if (a == null) {
            a = s.accumulator(KeyFactory.entity(e));
            BY_INSTANCE.put(e, a);
        }
        s.begin(a, Bucket.ENTITY);
    }

    /** Ends the innermost timed call. Safe to call when no session is running. */
    public static void end() {
        Session s = session;
        if (s != null) s.end();
    }
}
```

- [ ] **Step 3: Write `KeyFactory.java`**

```java
package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.EntityKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Turns Minecraft objects into core keys. Called once per object per session, so it may allocate. */
final class KeyFactory {

    private KeyFactory() {
    }

    static BlockEntityKey blockEntity(BlockEntity be) {
        ResourceLocation id = BlockEntityType.getKey(be.getType());
        String typeId = id == null ? be.getClass().getName() : id.toString();
        Level level = be.getLevel();
        String dimension = level == null ? "unknown" : level.dimension().location().toString();
        BlockPos p = be.getBlockPos();
        return new BlockEntityKey(typeId, dimension, p.getX(), p.getY(), p.getZ());
    }

    static EntityKey entity(Entity e) {
        String typeId = EntityType.getKey(e.getType()).toString();
        String name;
        if (e instanceof Player player) {
            name = player.getGameProfile().getName();
        } else if (e.hasCustomName() && e.getCustomName() != null) {
            name = e.getCustomName().getString();
        } else {
            name = "";
        }
        return new EntityKey(typeId, e.getId(), name);
    }
}
```

- [ ] **Step 4: Write `GcSampler.java`**

```java
package io.github.edwardg117.renderable.hooks;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/** Sums collection count and pause time over all collectors. Cheap enough to call once per frame. */
public final class GcSampler {
    private final List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();
    private long count;
    private long millis;

    public void sample() {
        long c = 0;
        long m = 0;
        for (GarbageCollectorMXBean b : beans) {
            long cc = b.getCollectionCount();
            long mm = b.getCollectionTime();
            if (cc > 0) c += cc;
            if (mm > 0) m += mm;
        }
        count = c;
        millis = m;
    }

    public long count() {
        return count;
    }

    public long millis() {
        return millis;
    }
}
```

- [ ] **Step 5: Write `FrameEvents.java`**

```java
package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Session;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Feeds frame and tick boundaries into the running session. Registered on the NeoForge game bus. */
public final class FrameEvents {
    private static final GcSampler GC = new GcSampler();

    private FrameEvents() {
    }

    @SubscribeEvent
    public static void onRenderPre(RenderFrameEvent.Pre event) {
        Session s = Probe.session();
        if (s == null) return;
        GC.sample();
        boolean done = s.onRenderPre(GC.count(), GC.millis());
        Probe.Listener l = Probe.listener();
        if (l == null) return;
        if (done) {
            l.onFinishRequested();
        } else {
            l.onFrameStarted(s);
        }
    }

    @SubscribeEvent
    public static void onRenderPost(RenderFrameEvent.Post event) {
        Session s = Probe.session();
        if (s != null) s.onRenderPost();
    }

    @SubscribeEvent
    public static void onTickPre(ClientTickEvent.Pre event) {
        Session s = Probe.session();
        if (s != null) s.onTickPre();
    }

    @SubscribeEvent
    public static void onTickPost(ClientTickEvent.Post event) {
        Session s = Probe.session();
        if (s != null) s.onTickPost();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        discard("logged out");
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) discard("level unloaded");
    }

    private static void discard(String reason) {
        if (Probe.session() == null) return;
        Probe.Listener l = Probe.listener();
        if (l != null) {
            l.onDiscardRequested(reason);
        } else {
            Probe.detach();
        }
    }
}
```

- [ ] **Step 6: Write `RenderableMixinPlugin.java`**

```java
package io.github.edwardg117.renderable.hooks;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spec §4.5: mixins in a sub-package named after a mod apply only when that mod is loaded.
 * Vanilla mixins live directly in {@code hooks.mixin} and always apply.
 */
public final class RenderableMixinPlugin implements IMixinConfigPlugin {
    private static final Map<String, String> REQUIRED_MOD = Map.of(
            "sodium", "sodium",
            "iris", "iris",
            "flywheel", "flywheel");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String pkg = mixinClassName.substring(0, mixinClassName.lastIndexOf('.'));
        String sub = pkg.substring(pkg.lastIndexOf('.') + 1);
        String mod = REQUIRED_MOD.get(sub);
        return mod == null || LoadingModList.get().getModFileById(mod) != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
```

- [ ] **Step 7: Write the two mixins**

`BlockEntityRenderDispatcherMixin.java`:

```java
package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Times every block entity render call (spec §4.1). {@code render} is the only method of that name. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {

    @WrapMethod(method = "render")
    private void renderable$timeRender(BlockEntity blockEntity, float partialTick, PoseStack poseStack,
                                       MultiBufferSource bufferSource, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(blockEntity, partialTick, poseStack, bufferSource);
            return;
        }
        Probe.beginBlockEntity(blockEntity);
        try {
            original.call(blockEntity, partialTick, poseStack, bufferSource);
        } finally {
            Probe.end();
        }
    }
}
```

`EntityRenderDispatcherMixin.java`:

```java
package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** Times every entity render call (spec §4.1). {@code render} is the only method of that name. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @WrapMethod(method = "render")
    private void renderable$timeRender(Entity entity, double x, double y, double z, float rotationYaw,
                                       float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                                       int packedLight, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(entity, x, y, z, rotationYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }
        Probe.beginEntity(entity);
        try {
            original.call(entity, x, y, z, rotationYaw, partialTick, poseStack, bufferSource, packedLight);
        } finally {
            Probe.end();
        }
    }
}
```

- [ ] **Step 8: Update `renderable.mixins.json`**

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "io.github.edwardg117.renderable.hooks.mixin",
  "compatibilityLevel": "JAVA_21",
  "plugin": "io.github.edwardg117.renderable.hooks.RenderableMixinPlugin",
  "client": [
    "BlockEntityRenderDispatcherMixin",
    "EntityRenderDispatcherMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

- [ ] **Step 9: Register the frame events in `RenderableMod`**

Replace the constructor body:

```java
    public RenderableMod(IEventBus modBus, ModContainer container) {
        VERSION = container.getModInfo().getVersion().toString();
        NeoForge.EVENT_BUS.register(FrameEvents.class);
        LOGGER.info("Renderable {} loaded", VERSION);
    }
```

Add imports:

```java
import io.github.edwardg117.renderable.hooks.FrameEvents;
import net.neoforged.neoforge.common.NeoForge;
```

- [ ] **Step 10: Compile and launch the client**

```bash
./gradlew compileJava --no-daemon
```

Expected: `BUILD SUCCESSFUL`. Then launch the dev client in the background and watch its log:

```bash
./gradlew runClient --no-daemon > "$TEMP/renderable-runclient.log" 2>&1 &
sleep 120
grep -E "Renderable .* loaded|Mixin apply|MixinApplyError|InvalidMixinException|renderable.mixins.json" "$TEMP/renderable-runclient.log" run/logs/latest.log
```

Expected: a line `Renderable 0.1.0 loaded`, no `MixinApplyError` or `InvalidMixinException`, and the game window reaches the title screen. Close the game window when done. If the mixin fails to apply because `@WrapMethod` cannot match `render`, the error names the method descriptor it looked for; compare it with the verified signatures at the top of this plan and fix the handler's parameter types.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/hooks src/main/java/io/github/edwardg117/renderable/RenderableMod.java src/main/resources/renderable.mixins.json
git commit -m "feat(hooks): time block entity and entity render calls through a static probe"
```

---

### Task 10: Config, controller, report files, environment, command, keybinds

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/RenderableConfig.java`
- Create: `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`
- Create: `src/main/java/io/github/edwardg117/renderable/report/ReportFiles.java`
- Create: `src/main/java/io/github/edwardg117/renderable/report/EnvironmentCollector.java`
- Create: `src/main/java/io/github/edwardg117/renderable/ui/RenderableCommand.java`
- Create: `src/main/java/io/github/edwardg117/renderable/ui/Keybinds.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/RenderableMod.java`

After this task a session can be started by key or command, runs for its duration, and ends with both report files on disk and a chat message with clickable actions. The results screen and overlay come in Tasks 11 and 12; `openResults()` prints a placeholder until then.

- [ ] **Step 1: Write `RenderableConfig.java`** (spec §7)

```java
package io.github.edwardg117.renderable;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config, written to config/renderable-client.toml. */
public final class RenderableConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue DEFAULT_DURATION_SECONDS;
    public static final ModConfigSpec.BooleanValue OPEN_RESULTS_ON_FINISH;
    public static final ModConfigSpec.DoubleValue HITCH_MEDIAN_MULTIPLIER;
    public static final ModConfigSpec.IntValue HITCH_MIN_EXCESS_MS;
    public static final ModConfigSpec.IntValue TOP_CONTRIBUTORS_PER_FRAME;
    public static final ModConfigSpec.BooleanValue OVERLAY_ENABLED;
    public static final ModConfigSpec.IntValue OVERLAY_LABEL_COUNT;
    public static final ModConfigSpec.IntValue OVERLAY_MAX_DISTANCE;
    public static final ModConfigSpec.BooleanValue WRITE_TEXT;
    public static final ModConfigSpec.BooleanValue WRITE_JSON;
    public static final ModConfigSpec.ConfigValue<String> REPORT_FOLDER;
    public static final ModConfigSpec.BooleanValue DEBUG_COMMANDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("session");
        DEFAULT_DURATION_SECONDS = b.comment("Seconds sampled by the keybind and by /renderable start without an argument.")
                .defineInRange("defaultDurationSeconds", 30, 2, 600);
        OPEN_RESULTS_ON_FINISH = b.comment("Open the results screen when a session ends.")
                .define("openResultsOnFinish", true);
        HITCH_MEDIAN_MULTIPLIER = b.comment("A frame is a hitch when it is longer than median * this value ...")
                .defineInRange("hitchMedianMultiplier", 2.0, 1.1, 10.0);
        HITCH_MIN_EXCESS_MS = b.comment("... and also longer than median + this many milliseconds.")
                .defineInRange("hitchMinExcessMs", 16, 1, 1000);
        TOP_CONTRIBUTORS_PER_FRAME = b.comment("Keys remembered per frame so hitches can be explained.")
                .defineInRange("topContributorsPerFrame", 5, 0, 20);
        b.pop();

        b.push("overlay");
        OVERLAY_ENABLED = b.comment("Draw in-world labels for the last finished session.")
                .define("overlayEnabled", true);
        OVERLAY_LABEL_COUNT = b.comment("Labels per category (block entities, entities).")
                .defineInRange("overlayLabelCount", 25, 1, 200);
        OVERLAY_MAX_DISTANCE = b.comment("Only label things within this many blocks of the camera.")
                .defineInRange("overlayMaxDistance", 64, 8, 512);
        b.pop();

        b.push("report");
        WRITE_TEXT = b.define("writeText", true);
        WRITE_JSON = b.define("writeJson", true);
        REPORT_FOLDER = b.comment("Folder under the game directory that reports are written to.")
                .define("reportFolder", "renderable");
        b.pop();

        b.push("debug");
        DEBUG_COMMANDS = b.comment("Enables /renderable debug stall <ms>, which sleeps the render thread to test hitch attribution.")
                .define("debugCommands", false);
        b.pop();

        SPEC = b.build();
    }

    private RenderableConfig() {
    }
}
```

- [ ] **Step 2: Write `ReportFiles.java`**

```java
package io.github.edwardg117.renderable.report;

import io.github.edwardg117.renderable.RenderableConfig;
import io.github.edwardg117.renderable.RenderableMod;
import io.github.edwardg117.renderable.core.Fmt;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes the text and JSON reports under {@code <gameDir>/<reportFolder>/} (spec §6). */
public final class ReportFiles {

    /** Where the files went. {@code text} or {@code json} is null when that format is disabled or failed. */
    public record Written(Path folder, Path text, Path json) {
    }

    private ReportFiles() {
    }

    public static Written write(String text, String json, long wallStartMillis) {
        Path folder = Minecraft.getInstance().gameDirectory.toPath().resolve(RenderableConfig.REPORT_FOLDER.get());
        String stamp = Fmt.fileStamp(wallStartMillis);
        Path textPath = null;
        Path jsonPath = null;
        try {
            Files.createDirectories(folder);
            if (RenderableConfig.WRITE_TEXT.get()) {
                textPath = folder.resolve("renderable-" + stamp + ".txt");
                Files.writeString(textPath, text, StandardCharsets.UTF_8);
            }
            if (RenderableConfig.WRITE_JSON.get()) {
                jsonPath = folder.resolve("renderable-" + stamp + ".json");
                Files.writeString(jsonPath, json, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            RenderableMod.LOGGER.error("Renderable could not write reports to {}", folder, e);
        }
        return new Written(folder, textPath, jsonPath);
    }
}
```

- [ ] **Step 3: Write `EnvironmentCollector.java`** (spec §6.1 item 2)

```java
package io.github.edwardg117.renderable.report;

import com.mojang.blaze3d.platform.GlStateManager;
import io.github.edwardg117.renderable.core.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.internal.versions.neoforge.NeoForgeVersion;
import org.lwjgl.opengl.GL11;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;

/** Reads the client's environment for the report header. Must run on the render thread (reads OpenGL strings). */
public final class EnvironmentCollector {

    private EnvironmentCollector() {
    }

    public static Environment collect(List<String> hooksApplied) {
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        LinkedHashMap<String, String> m = new LinkedHashMap<>();

        m.put("minecraft", SharedConstants.getCurrentVersion().getName());
        m.put("neoforge", NeoForgeVersion.getVersion());
        m.put("java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        m.put("maxHeapMiB", Long.toString(Runtime.getRuntime().maxMemory() >> 20));
        m.put("mods", Integer.toString(ModList.get().size()));
        m.put("gpu", safe(() -> GlStateManager._getString(GL11.GL_RENDERER)));
        m.put("glVersion", safe(() -> GlStateManager._getString(GL11.GL_VERSION)));

        m.put("renderDistance", String.valueOf(o.renderDistance().get()));
        m.put("simulationDistance", String.valueOf(o.simulationDistance().get()));
        m.put("biomeBlend", String.valueOf(o.biomeBlendRadius().get()));
        m.put("graphics", String.valueOf(o.graphicsMode().get()));
        m.put("vsync", String.valueOf(o.enableVsync().get()));
        m.put("fpsLimit", String.valueOf(o.framerateLimit().get()));
        m.put("particles", String.valueOf(o.particles().get()));
        m.put("entityDistance", String.valueOf(o.entityDistanceScaling().get()));
        m.put("guiScale", String.valueOf(o.guiScale().get()));
        m.put("window", mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()
                + (mc.getWindow().isFullscreen() ? " fullscreen" : " windowed"));

        m.put("sodium", modVersion("sodium"));
        m.put("iris", modVersion("iris"));
        m.put("flywheel", modVersion("flywheel"));
        m.put("distanthorizons", modVersion("distanthorizons"));

        if (mc.level != null) {
            m.put("dimension", mc.level.dimension().location().toString());
            m.put("sections", safe(() -> mc.levelRenderer.getSectionStatistics()));
            m.put("entities", safe(() -> mc.levelRenderer.getEntityStatistics()));
        }
        if (mc.player != null) {
            BlockPos p = mc.player.blockPosition();
            m.put("position", p.getX() + " " + p.getY() + " " + p.getZ());
        }
        return Environment.of(m, hooksApplied);
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("absent");
    }

    private static String safe(Supplier<String> s) {
        try {
            String v = s.get();
            return v == null ? "unknown" : v;
        } catch (RuntimeException e) {
            return "unknown";
        }
    }
}
```

- [ ] **Step 4: Write `ProfilerController.java`**

```java
package io.github.edwardg117.renderable;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Calibration;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.core.SessionConfig;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.hooks.Probe;
import io.github.edwardg117.renderable.report.EnvironmentCollector;
import io.github.edwardg117.renderable.report.JsonReportWriter;
import io.github.edwardg117.renderable.report.ReportFiles;
import io.github.edwardg117.renderable.report.TextReportWriter;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.nio.file.Path;
import java.util.Locale;

/** Owns the session lifecycle (spec §3.1) and delivery (spec §6). Everything runs on the render thread. */
public final class ProfilerController implements Probe.Listener {
    private static final ProfilerController INSTANCE = new ProfilerController();
    private static final long MIN_KEEP_NANOS = 2_000_000_000L;

    public static ProfilerController get() {
        return INSTANCE;
    }

    private Session session;
    private long calibratedCallNanos = -1;
    private int pendingStallMs;

    private SessionResult lastResult;
    private Environment lastEnvironment;
    private String lastReportText;
    private Path lastFolder;

    private ProfilerController() {
    }

    public boolean isRunning() {
        return session != null;
    }

    /** The running session, or null. */
    public Session session() {
        return session;
    }

    /** The last finished session, or null. */
    public SessionResult lastResult() {
        return lastResult;
    }

    public Environment lastEnvironment() {
        return lastEnvironment;
    }

    /** Keybind behaviour: start with the default duration, or stop early if running. */
    public void toggle() {
        if (isRunning()) {
            stop();
        } else {
            start(RenderableConfig.DEFAULT_DURATION_SECONDS.get());
        }
    }

    public void start(int seconds) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            chat("Renderable: join a world first.");
            return;
        }
        if (isRunning()) {
            chat("Renderable: a session is already running. Use /renderable stop to end it early.");
            return;
        }
        if (calibratedCallNanos < 0) {
            calibratedCallNanos = Calibration.measureCallNanos(System::nanoTime);
        }
        SessionConfig config = SessionConfig.ofSeconds(seconds,
                RenderableConfig.HITCH_MEDIAN_MULTIPLIER.get(),
                RenderableConfig.HITCH_MIN_EXCESS_MS.get(),
                RenderableConfig.TOP_CONTRIBUTORS_PER_FRAME.get(),
                calibratedCallNanos);
        session = new Session(config, System::nanoTime, System.currentTimeMillis());
        Probe.setListener(this);
        Probe.attach(session);
        chat("Renderable: sampling for " + seconds + " s. Press the key again or run /renderable stop to end early.");
    }

    /** Ends early. Keeps the data when at least two seconds were sampled, otherwise discards it. */
    public void stop() {
        if (!isRunning()) {
            chat("Renderable: no session is running.");
            return;
        }
        if (session.elapsedNanos() < MIN_KEEP_NANOS) {
            discard("stopped before 2 s of data");
        } else {
            finish();
        }
    }

    public void cancel() {
        if (!isRunning()) {
            chat("Renderable: no session is running.");
            return;
        }
        discard("cancelled");
    }

    private void discard(String reason) {
        Probe.detach();
        session = null;
        chat("Renderable: session discarded (" + reason + ").");
    }

    /** Freezes the session, writes reports, announces in chat. */
    public void finish() {
        Session s = session;
        if (s == null) return;
        Probe.detach();
        session = null;

        SessionResult result = s.finish();
        Environment env = EnvironmentCollector.collect(Probe.hooksApplied());
        String text = TextReportWriter.write(result, env, RenderableMod.VERSION);
        String json = JsonReportWriter.write(result, env, RenderableMod.VERSION);
        ReportFiles.Written written = ReportFiles.write(text, json, result.wallStartMillis());

        lastResult = result;
        lastEnvironment = env;
        lastReportText = text;
        lastFolder = written.folder();

        chat(finishedMessage(result, written));
    }

    private static MutableComponent finishedMessage(SessionResult r, ReportFiles.Written w) {
        MutableComponent msg = Component.literal(String.format(Locale.ROOT,
                "Renderable: %d frames in %.1f s, avg %.1f ms, %d hitches. ",
                r.frameCount(), r.stats().durationSeconds(), r.stats().avgNanos() / 1e6, r.hitches().size()));
        msg.append(commandLink("[Open results]", "/renderable results")).append(" ");
        msg.append(commandLink("[Copy report]", "/renderable copy")).append(" ");
        msg.append(Component.literal("[Open folder]").withStyle(st -> st
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, w.folder().toAbsolutePath().toString()))));
        return msg;
    }

    private static Component commandLink(String text, String command) {
        return Component.literal(text).withStyle(st -> st
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    public void copyReport() {
        if (lastReportText == null) {
            chat("Renderable: no finished session yet.");
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(lastReportText);
        chat("Renderable: report copied to the clipboard (" + lastReportText.length() + " characters).");
    }

    public void openFolder() {
        if (lastFolder == null) {
            chat("Renderable: no finished session yet.");
            return;
        }
        Util.getPlatform().openPath(lastFolder);
    }

    /** Replaced by the results screen in Task 12. */
    public void openResults() {
        if (lastResult == null) {
            chat("Renderable: no finished session yet.");
            return;
        }
        chat("Renderable: results screen not built yet; see the report files.");
    }

    /** Debug: sleep the render thread inside a fake block entity on the next frame (spec §5.1). */
    public void debugStall(int ms) {
        if (!isRunning()) {
            chat("Renderable: start a session first, then stall.");
            return;
        }
        pendingStallMs = ms;
        chat("Renderable: will stall the render thread for " + ms + " ms on the next frame.");
    }

    @Override
    public void onFrameStarted(Session s) {
        int ms = pendingStallMs;
        if (ms <= 0) return;
        pendingStallMs = 0;
        Accumulator a = s.accumulator(new BlockEntityKey("renderable:debug_stall", "renderable:debug", 0, 0, 0));
        s.begin(a, Bucket.BLOCK_ENTITY);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            s.end();
        }
    }

    @Override
    public void onFinishRequested() {
        finish();
    }

    @Override
    public void onDiscardRequested(String reason) {
        if (isRunning()) discard(reason);
    }

    public static void chat(String text) {
        chat(Component.literal(text));
    }

    public static void chat(Component component) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(component, false);
        } else {
            RenderableMod.LOGGER.info(component.getString());
        }
    }
}
```

- [ ] **Step 5: Write `Keybinds.java`**

```java
package io.github.edwardg117.renderable.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.edwardg117.renderable.ProfilerController;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** F7 starts or stops a session; the overlay key is unbound by default (spec §5.1). */
public final class Keybinds {
    public static final KeyMapping TOGGLE = new KeyMapping("key.renderable.toggle", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, "key.categories.renderable");
    public static final KeyMapping OVERLAY = new KeyMapping("key.renderable.overlay", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.renderable");

    private Keybinds() {
    }

    /** Mod bus. */
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(OVERLAY);
    }

    /** Game bus. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (TOGGLE.consumeClick()) {
            ProfilerController.get().toggle();
        }
        while (OVERLAY.consumeClick()) {
            onOverlayKey();
        }
    }

    /** Task 11 replaces this with the overlay toggle. */
    private static void onOverlayKey() {
    }
}
```

- [ ] **Step 6: Write `RenderableCommand.java`**

```java
package io.github.edwardg117.renderable.ui;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.edwardg117.renderable.ProfilerController;
import io.github.edwardg117.renderable.RenderableConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Client-side /renderable command tree (spec §5.1). */
public final class RenderableCommand {

    private RenderableCommand() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        ProfilerController c = ProfilerController.get();

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("renderable")
                .then(Commands.literal("start")
                        .executes(ctx -> {
                            c.start(RenderableConfig.DEFAULT_DURATION_SECONDS.get());
                            return 1;
                        })
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(2, 600))
                                .executes(ctx -> {
                                    c.start(IntegerArgumentType.getInteger(ctx, "seconds"));
                                    return 1;
                                })))
                .then(Commands.literal("stop").executes(ctx -> {
                    c.stop();
                    return 1;
                }))
                .then(Commands.literal("cancel").executes(ctx -> {
                    c.cancel();
                    return 1;
                }))
                .then(Commands.literal("results").executes(ctx -> {
                    c.openResults();
                    return 1;
                }))
                .then(Commands.literal("copy").executes(ctx -> {
                    c.copyReport();
                    return 1;
                }))
                .then(Commands.literal("folder").executes(ctx -> {
                    c.openFolder();
                    return 1;
                }))
                .then(Commands.literal("debug")
                        .requires(src -> RenderableConfig.DEBUG_COMMANDS.get())
                        .then(Commands.literal("stall")
                                .then(Commands.argument("ms", IntegerArgumentType.integer(1, 5000))
                                        .executes(ctx -> {
                                            c.debugStall(IntegerArgumentType.getInteger(ctx, "ms"));
                                            return 1;
                                        }))));

        event.getDispatcher().register(root);
    }
}
```

- [ ] **Step 7: Wire everything in `RenderableMod`**

Replace the whole file:

```java
package io.github.edwardg117.renderable;

import io.github.edwardg117.renderable.hooks.FrameEvents;
import io.github.edwardg117.renderable.hooks.Probe;
import io.github.edwardg117.renderable.ui.Keybinds;
import io.github.edwardg117.renderable.ui.RenderableCommand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = RenderableMod.MOD_ID, dist = Dist.CLIENT)
public final class RenderableMod {
    public static final String MOD_ID = "renderable";
    public static final Logger LOGGER = LoggerFactory.getLogger("Renderable");
    /** Set from the mod container in the constructor; "dev" until then. */
    public static String VERSION = "dev";

    public RenderableMod(IEventBus modBus, ModContainer container) {
        VERSION = container.getModInfo().getVersion().toString();
        container.registerConfig(ModConfig.Type.CLIENT, RenderableConfig.SPEC);

        modBus.addListener(Keybinds::register);

        NeoForge.EVENT_BUS.register(FrameEvents.class);
        NeoForge.EVENT_BUS.register(Keybinds.class);
        NeoForge.EVENT_BUS.addListener(RenderableCommand::register);

        Probe.setListener(ProfilerController.get());
        LOGGER.info("Renderable {} loaded", VERSION);
    }
}
```

- [ ] **Step 8: Compile, then verify in the client (human step)**

```bash
./gradlew compileJava --no-daemon && ./gradlew runClient --no-daemon
```

In the client: create or open a singleplayer world, then:

1. Run `/renderable start 5`. Expected chat: `Renderable: sampling for 5 s. ...`. After five seconds: `Renderable: N frames in 5.0 s, avg X ms, H hitches. [Open results] [Copy report] [Open folder]`.
2. `run/renderable/` contains `renderable-<date>_<time>.txt` and `.json`. Open the text file: the Environment block lists your GPU and `hooks applied: BLOCK_ENTITY_RENDER, ENTITY_RENDER` (the entity hook needs at least one entity in view, the player's own hand does not count; if the block entity hook is missing, place a chest and look at it).
3. Press F7: sampling starts. Press F7 again within two seconds: `session discarded (stopped before 2 s of data)`. Press F7, wait three seconds, press again: a report is written.
4. Click `[Copy report]` and paste somewhere: the report text.
5. Leave the world while sampling: `session discarded (level unloaded)` or `(logged out)`.

Record what you saw in the task report. If step 2 shows no block entity rows even with a chest in view, the mixin did not apply; check `run/logs/latest.log` for `renderable.mixins.json`.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable
git commit -m "feat: session lifecycle with config, command, keybind, environment header and report files"
```

---

### Task 11: Sampling HUD, in-world overlay, highlight box

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/ui/Colors.java`
- Create: `src/main/java/io/github/edwardg117/renderable/ui/SamplingHud.java`
- Create: `src/main/java/io/github/edwardg117/renderable/ui/WorldOverlay.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/Keybinds.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/RenderableCommand.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/RenderableMod.java`
- Test: `src/test/java/io/github/edwardg117/renderable/ui/ColorsTest.java`

Spec §5.2 and §5.4. The HUD is drawn top centre so it never collides with the F3 overlay. Labels use vanilla's floating-text helper, which takes world coordinates and handles facing the camera. In-game text says `us` rather than `µs` because the default font does not reliably carry the micro sign.

- [ ] **Step 1: Write the failing colour test**

```java
package io.github.edwardg117.renderable.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorsTest {

    @Test
    void heatScaleEndpointsAndMidpoint() {
        assertEquals(0xFF40FF40, Colors.heat(0.0));
        assertEquals(0xFF40FF40, Colors.heat(1.0));
        assertEquals(0xFFA0FF40, Colors.heat(2.5), "halfway from green to yellow");
        assertEquals(0xFFFFFF40, Colors.heat(4.0));
        assertEquals(0xFFFF4040, Colors.heat(10.0));
        assertEquals(0xFFFF4040, Colors.heat(55.0));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.ui.ColorsTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: class Colors`.

- [ ] **Step 3: Write `Colors.java`**

```java
package io.github.edwardg117.renderable.ui;

/** ARGB colours for labels. Share of frame time maps green (under 1%) through yellow (4%) to red (10% and up). */
public final class Colors {
    public static final int GREEN = 0xFF40FF40;
    public static final int YELLOW = 0xFFFFFF40;
    public static final int RED = 0xFFFF4040;
    public static final int FLAGGED = RED;

    private Colors() {
    }

    public static int heat(double sharePercent) {
        if (sharePercent <= 1.0) return GREEN;
        if (sharePercent <= 4.0) return lerp(GREEN, YELLOW, (sharePercent - 1.0) / 3.0);
        if (sharePercent <= 10.0) return lerp(YELLOW, RED, (sharePercent - 4.0) / 6.0);
        return RED;
    }

    static int lerp(int a, int b, double t) {
        int r = channel(a >> 16, b >> 16, t);
        int g = channel(a >> 8, b >> 8, t);
        int bl = channel(a, b, t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int channel(int a, int b, double t) {
        int ca = a & 0xFF;
        int cb = b & 0xFF;
        return (int) Math.round(ca + (cb - ca) * t);
    }
}
```

- [ ] **Step 4: Run the colour test to verify it passes**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.ui.ColorsTest' --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 5: Write `SamplingHud.java`**

```java
package io.github.edwardg117.renderable.ui;

import io.github.edwardg117.renderable.ProfilerController;
import io.github.edwardg117.renderable.core.HitchDetector;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.core.SessionConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.Locale;

/** One line at the top centre of the screen while a session is sampling (spec §5.2). */
public final class SamplingHud {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath("renderable", "sampling");
    private static final int ROLLING_WINDOW = 120;

    private SamplingHud() {
    }

    /** Mod bus. */
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, SamplingHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Session s = ProfilerController.get().session();
        if (s == null) return;
        Minecraft mc = Minecraft.getInstance();
        SessionConfig c = s.config();
        int hitches = HitchDetector.countRolling(s.framesView(), ROLLING_WINDOW, c.hitchMedianMultiplier(), c.hitchMinExcessNanos());
        String text = String.format(Locale.ROOT, "Renderable: %.1f / %.0f s | %d frames | %d hitches",
                s.elapsedNanos() / 1e9, c.durationSeconds(), s.frameCount(), hitches);
        int w = mc.font.width(text);
        int x = (g.guiWidth() - w) / 2;
        int y = 4;
        g.fill(x - 3, y - 2, x + w + 3, y + mc.font.lineHeight + 1, 0x90000000);
        g.drawString(mc.font, text, x, y, 0xFFFFFF, true);
    }
}
```

- [ ] **Step 6: Write `WorldOverlay.java`**

```java
package io.github.edwardg117.renderable.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.edwardg117.renderable.RenderableConfig;
import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.EntityKey;
import io.github.edwardg117.renderable.core.Fmt;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.RankedEntry;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SessionResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;
import java.util.Set;

/** Floating labels for the last finished session plus a temporary highlight box (spec §5.4). Game bus. */
public final class WorldOverlay {
    private static final long HIGHLIGHT_MILLIS = 10_000;
    private static final float LABEL_SCALE = 0.02f;

    private static SessionResult result;
    private static List<RankedEntry> blockEntityLabels = List.of();
    private static List<RankedEntry> entityLabels = List.of();
    private static Set<ProfileKey> flagged = Set.of();
    private static boolean enabled = true;

    private static BlockPos highlight;
    private static long highlightUntilMillis;

    private WorldOverlay() {
    }

    public static void setResult(SessionResult r) {
        result = r;
        int n = RenderableConfig.OVERLAY_LABEL_COUNT.get();
        blockEntityLabels = head(Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY), n);
        entityLabels = head(Ranking.byKey(r, ProfileKey.Category.ENTITY), n);
        flagged = r.hitchFlaggedKeys();
        enabled = RenderableConfig.OVERLAY_ENABLED.get();
    }

    public static void clear() {
        result = null;
        blockEntityLabels = List.of();
        entityLabels = List.of();
        flagged = Set.of();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    /** Draw a red outline around the block for ten seconds. */
    public static void highlight(BlockPos pos) {
        highlight = pos.immutable();
        highlightUntilMillis = System.currentTimeMillis() + HIGHLIGHT_MILLIS;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        if (enabled && result != null) {
            int maxDist = RenderableConfig.OVERLAY_MAX_DISTANCE.get();
            double maxDistSq = (double) maxDist * maxDist;
            long sessionTotal = result.stats().totalNanos();
            int frames = result.frameCount();

            for (RankedEntry e : blockEntityLabels) {
                BlockEntityKey k = (BlockEntityKey) e.key();
                double x = k.x() + 0.5, y = k.y() + 1.25, z = k.z() + 0.5;
                if (cam.distanceToSqr(x, y, z) > maxDistSq) continue;
                drawLabel(pose, buffers, e, frames, sessionTotal, x, y, z);
            }
            for (RankedEntry e : entityLabels) {
                EntityKey k = (EntityKey) e.key();
                Entity ent = mc.level.getEntity(k.entityId());
                if (ent == null || ent == mc.getCameraEntity()) continue;
                Vec3 p = ent.position();
                double y = p.y + ent.getBbHeight() + 0.5;
                if (cam.distanceToSqr(p.x, y, p.z) > maxDistSq) continue;
                drawLabel(pose, buffers, e, frames, sessionTotal, p.x, y, p.z);
            }
            buffers.endBatch();
        }

        if (highlight != null) {
            if (System.currentTimeMillis() > highlightUntilMillis) {
                highlight = null;
                return;
            }
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            LevelRenderer.renderLineBox(pose, lines, new AABB(highlight).inflate(0.01), 1.0f, 0.3f, 0.3f, 1.0f);
            pose.popPose();
            buffers.endBatch(RenderType.lines());
        }
    }

    private static void drawLabel(PoseStack pose, MultiBufferSource buffers, RankedEntry e, int frames,
                                  long sessionTotal, double x, double y, double z) {
        boolean isFlagged = flagged.contains(e.key());
        String text = (isFlagged ? "! " : "") + e.typeId() + " " + Fmt.us(e.usPerFrame(frames)) + " us";
        int color = isFlagged ? Colors.FLAGGED : Colors.heat(e.sharePercent(sessionTotal));
        DebugRenderer.renderFloatingText(pose, buffers, text, x, y, z, color, LABEL_SCALE, true, 0.0f, true);
    }

    private static <T> List<T> head(List<T> list, int n) {
        return list.size() <= n ? list : List.copyOf(list.subList(0, n));
    }
}
```

- [ ] **Step 7: Wire the overlay key, command and controller**

In `Keybinds.java`, replace `onOverlayKey()`:

```java
    private static void onOverlayKey() {
        WorldOverlay.toggle();
        ProfilerController.chat("Renderable: overlay " + (WorldOverlay.isEnabled() ? "on" : "off"));
    }
```

In `RenderableCommand.java`, insert before `.then(Commands.literal("debug")`:

```java
                .then(Commands.literal("overlay")
                        .executes(ctx -> {
                            WorldOverlay.toggle();
                            ProfilerController.chat("Renderable: overlay " + (WorldOverlay.isEnabled() ? "on" : "off"));
                            return 1;
                        })
                        .then(Commands.literal("on").executes(ctx -> {
                            WorldOverlay.setEnabled(true);
                            ProfilerController.chat("Renderable: overlay on");
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            WorldOverlay.setEnabled(false);
                            ProfilerController.chat("Renderable: overlay off");
                            return 1;
                        })))
```

In `ProfilerController.java`:

- add `import io.github.edwardg117.renderable.ui.WorldOverlay;`
- in `start(...)`, immediately before `Probe.setListener(this);`, add `WorldOverlay.clear();`
- in `finish()`, immediately after `lastFolder = written.folder();`, add `WorldOverlay.setResult(result);`

In `RenderableMod.java`:

- add `import io.github.edwardg117.renderable.ui.SamplingHud;` and `import io.github.edwardg117.renderable.ui.WorldOverlay;`
- after `modBus.addListener(Keybinds::register);` add `modBus.addListener(SamplingHud::register);`
- after `NeoForge.EVENT_BUS.register(Keybinds.class);` add `NeoForge.EVENT_BUS.register(WorldOverlay.class);`

- [ ] **Step 8: Compile and verify in the client (human step)**

```bash
./gradlew compileJava --no-daemon && ./gradlew runClient --no-daemon
```

In a world with a few chests, furnaces or other block entities and some animals in view:

1. `/renderable start 10`: a dark bar appears top centre reading `Renderable: 3.2 / 10 s | 412 frames | 0 hitches` and counts up.
2. When it ends, labels float above the block entities and animals, e.g. `minecraft:chest 12.4 us`, green for cheap things. Walk 70 blocks away: labels vanish; walk back: they return.
3. `/renderable overlay off` hides them, `/renderable overlay` toggles them back.
4. Start another session: the old labels disappear while sampling and new ones appear at the end.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable src/test/java/io/github/edwardg117/renderable/ui
git commit -m "feat(ui): sampling HUD, in-world cost labels and highlight box"
```

---

### Task 12: Results screen

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/ui/screen/Tab.java`
- Create: `src/main/java/io/github/edwardg117/renderable/ui/screen/Row.java`
- Create: `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java`
- Create: `src/main/java/io/github/edwardg117/renderable/ui/screen/RowList.java`
- Create: `src/main/java/io/github/edwardg117/renderable/ui/screen/ResultsScreen.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`

Spec §5.3 with the phase 1 tabs: Frames, Hitches, Block Entities, Entities. Columns are laid out proportionally so the screen works at GUI scale 3 on a 1280-wide window. Type rows expand on click; the selected position row enables Highlight and Teleport.

- [ ] **Step 1: Write `Tab.java`**

```java
package io.github.edwardg117.renderable.ui.screen;

/** Tabs of the results screen and the column layout each one uses. */
public enum Tab {
    FRAMES("Frames", Columns.METRIC_VALUE),
    HITCHES("Hitches", Columns.HITCH),
    BLOCK_ENTITIES("Block Entities", Columns.RANKED),
    ENTITIES("Entities", Columns.RANKED);

    private final String title;
    private final Columns columns;

    Tab(String title, Columns columns) {
        this.title = title;
        this.columns = columns;
    }

    public String title() {
        return title;
    }

    public Columns columns() {
        return columns;
    }

    /**
     * Column headers, relative weights and alignment. {@link #layout(int)} turns weights into pixel offsets.
     * Kept as a nested class so enum constants can reference the constants without a forward-reference error.
     */
    public record Columns(String[] headers, int[] weights, boolean[] rightAlign) {
        static final Columns METRIC_VALUE = new Columns(
                new String[]{"metric", "value"},
                new int[]{1, 3},
                new boolean[]{false, false});
        static final Columns HITCH = new Columns(
                new String[]{"frame", "total ms", "split"},
                new int[]{2, 1, 7},
                new boolean[]{false, true, false});
        static final Columns RANKED = new Columns(
                new String[]{"name", "render us/f", "tick us/f", "share", "peak ms", "shadow", "verts/f", "calls"},
                new int[]{5, 1, 1, 1, 1, 1, 1, 1},
                new boolean[]{false, true, true, true, true, true, true, true});

        /** Returns {x offsets, widths} for a row of the given pixel width. Each column keeps 4 px of padding. */
        public int[][] layout(int rowWidth) {
            int total = 0;
            for (int w : weights) total += w;
            int[] x = new int[headers.length];
            int[] w = new int[headers.length];
            int cursor = 0;
            for (int i = 0; i < headers.length; i++) {
                int px = rowWidth * weights[i] / total;
                x[i] = cursor;
                w[i] = Math.max(8, px - 4);
                cursor += px;
            }
            return new int[][]{x, w};
        }
    }
}
```

- [ ] **Step 2: Write `Row.java`**

```java
package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.core.ProfileKey;
import net.minecraft.core.BlockPos;

/**
 * One list row. {@code group} rows are drawn brighter; {@code expandable} rows toggle their members on click;
 * {@code pos} or {@code entityId >= 0} enables Highlight and Teleport.
 */
public record Row(String[] cells,
                  ProfileKey key,
                  String typeId,
                  boolean group,
                  boolean expandable,
                  int indent,
                  BlockPos pos,
                  int entityId) {

    public static Row plain(String... cells) {
        return new Row(cells, null, null, false, false, 0, null, -1);
    }

    public static Row heading(String text) {
        return new Row(new String[]{text, ""}, null, null, true, false, 0, null, -1);
    }
}
```

- [ ] **Step 3: Write `Rows.java`**

```java
package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.EntityKey;
import io.github.edwardg117.renderable.core.Fmt;
import io.github.edwardg117.renderable.core.FrameRecord;
import io.github.edwardg117.renderable.core.FrameStats;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.RankedEntry;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.Splits;
import io.github.edwardg117.renderable.core.TopEntry;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Builds the rows for each tab from a finished session. */
public final class Rows {
    private static final long[] HISTOGRAM_EDGES_MS = {8, 16, 33, 50, 100, 250};

    private Rows() {
    }

    public static List<Row> build(Tab tab, SessionResult r, Ranking.Sort sort, Set<String> expanded) {
        return switch (tab) {
            case FRAMES -> frames(r);
            case HITCHES -> hitches(r);
            case BLOCK_ENTITIES -> ranked(r, ProfileKey.Category.BLOCK_ENTITY, sort, expanded);
            case ENTITIES -> ranked(r, ProfileKey.Category.ENTITY, sort, expanded);
        };
    }

    static List<Row> frames(SessionResult r) {
        FrameStats st = r.stats();
        List<Row> rows = new ArrayList<>();
        rows.add(Row.plain("Sampled", String.format(Locale.ROOT, "%.1f s, %d frames", st.durationSeconds(), st.frames())));
        rows.add(Row.plain("Average frame", String.format(Locale.ROOT, "%s ms (%.0f fps)", Fmt.ms(st.avgNanos()), st.avgFps())));
        rows.add(Row.plain("p50 / p95 / p99 / max", Fmt.ms(st.p50Nanos()) + " / " + Fmt.ms(st.p95Nanos()) + " / "
                + Fmt.ms(st.p99Nanos()) + " / " + Fmt.ms(st.maxNanos()) + " ms"));
        rows.add(Row.plain("Hitch threshold", Fmt.ms(r.hitchThresholdNanos()) + " ms"));
        rows.add(Row.plain("Hitches", r.hitches().size() + " (" + Fmt.pct(r.hitchSharePercent()) + " of time)"));
        rows.add(Row.plain("GC", st.gcCount() + " collections, " + st.gcMillis() + " ms paused"));
        rows.add(Row.plain("Renderable overhead", String.format(Locale.ROOT, "%.2f ms over %s timed calls",
                r.overheadEstimateMs(), Fmt.count(r.timedCalls()))));
        rows.add(Row.plain("", ""));

        rows.add(Row.heading("Average split per frame"));
        rows.add(Row.plain("tick", Fmt.ms2(st.avgTickNanos()) + " ms"));
        for (Bucket b : Bucket.VALUES) {
            if (st.avgBucket(b) > 0) rows.add(Row.plain("  " + b.label(), Fmt.ms2(st.avgBucket(b)) + " ms"));
        }
        rows.add(Row.plain("other render", Fmt.ms2(st.avgOtherRenderNanos()) + " ms"));
        rows.add(Row.plain("other tick", Fmt.ms2(st.avgOtherTickNanos()) + " ms"));
        rows.add(Row.plain("swap / wait", Fmt.ms2(st.avgSwapNanos()) + " ms"));
        rows.add(Row.plain("", ""));

        rows.add(Row.heading("Frame time histogram"));
        int[] counts = new int[HISTOGRAM_EDGES_MS.length + 1];
        for (FrameRecord f : r.frames()) {
            double ms = f.totalNanos() / 1e6;
            int bin = 0;
            while (bin < HISTOGRAM_EDGES_MS.length && ms >= HISTOGRAM_EDGES_MS[bin]) bin++;
            counts[bin]++;
        }
        int max = Arrays.stream(counts).max().orElse(0);
        for (int i = 0; i <= HISTOGRAM_EDGES_MS.length; i++) {
            String label;
            if (i == 0) {
                label = "< " + HISTOGRAM_EDGES_MS[0] + " ms";
            } else if (i == HISTOGRAM_EDGES_MS.length) {
                label = ">= " + HISTOGRAM_EDGES_MS[i - 1] + " ms";
            } else {
                label = HISTOGRAM_EDGES_MS[i - 1] + " - " + HISTOGRAM_EDGES_MS[i] + " ms";
            }
            int bar = max == 0 ? 0 : (int) Math.round(30.0 * counts[i] / max);
            rows.add(Row.plain(label, String.format(Locale.ROOT, "%5d  %s", counts[i], "#".repeat(bar))));
        }
        return rows;
    }

    static List<Row> hitches(SessionResult r) {
        List<Row> rows = new ArrayList<>();
        if (r.hitches().isEmpty()) {
            rows.add(Row.plain("No hitches", "", "No frame exceeded " + Fmt.ms(r.hitchThresholdNanos()) + " ms"));
            return rows;
        }
        int n = 0;
        for (FrameRecord h : r.hitches()) {
            n++;
            String frame = "#" + n + "  frame " + h.index() + " @ " + Fmt.secs(h.startNanos()) + " s";
            rows.add(new Row(new String[]{frame, Fmt.ms(h.totalNanos()), Splits.frame(h) + " | gc " + h.gcMillis() + " ms"},
                    null, null, true, false, 0, null, -1));
            for (TopEntry t : h.top()) {
                rows.add(new Row(new String[]{t.key().label(), Fmt.ms2(t.nanos()), ""},
                        t.key(), t.key().typeId(), false, false, 1, posOf(t.key()), entityIdOf(t.key())));
            }
        }
        return rows;
    }

    static List<Row> ranked(SessionResult r, ProfileKey.Category category, Ranking.Sort sort, Set<String> expanded) {
        int frames = r.frameCount();
        long total = r.stats().totalNanos();
        List<Row> rows = new ArrayList<>();
        for (RankedEntry t : Ranking.sort(Ranking.byType(r, category), sort)) {
            rows.add(new Row(cells(t.typeId() + "  (" + t.members() + ")", t, frames, total),
                    null, t.typeId(), true, true, 0, null, -1));
            if (expanded.contains(t.typeId())) {
                for (RankedEntry m : Ranking.sort(Ranking.membersOf(r, category, t.typeId()), sort)) {
                    rows.add(new Row(cells(memberLabel(m.key()), m, frames, total),
                            m.key(), t.typeId(), false, false, 1, posOf(m.key()), entityIdOf(m.key())));
                }
            }
        }
        if (rows.isEmpty()) rows.add(Row.plain("(no data)"));
        return rows;
    }

    static String[] cells(String name, RankedEntry e, int frames, long total) {
        return new String[]{
                name,
                Fmt.us(e.renderUsPerFrame(frames)),
                Fmt.us(e.tickUsPerFrame(frames)),
                Fmt.pct(e.sharePercent(total)),
                Fmt.ms2(e.maxFrameNanos()),
                Fmt.pct(e.shadowPercent()),
                Fmt.us(e.verticesPerFrame(frames)),
                Fmt.count(e.calls())};
    }

    static String memberLabel(ProfileKey k) {
        if (k instanceof BlockEntityKey b) return "@ " + b.x() + " " + b.y() + " " + b.z();
        if (k instanceof EntityKey e) return "#" + e.entityId() + (e.name().isEmpty() ? "" : "  " + e.name());
        return k.label();
    }

    static BlockPos posOf(ProfileKey k) {
        return k instanceof BlockEntityKey b ? new BlockPos(b.x(), b.y(), b.z()) : null;
    }

    static int entityIdOf(ProfileKey k) {
        return k instanceof EntityKey e ? e.entityId() : -1;
    }
}
```

- [ ] **Step 4: Write `RowList.java`**

```java
package io.github.edwardg117.renderable.ui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Scrollable list of {@link Row}s with column layout supplied by the screen. */
public final class RowList extends ObjectSelectionList<RowList.Entry> {
    private final ResultsScreen screen;
    private Tab tab = Tab.FRAMES;
    private int[] colX = {0};
    private int[] colW = {100};

    public RowList(Minecraft mc, ResultsScreen screen, int width, int height, int y, int itemHeight) {
        super(mc, width, height, y, itemHeight);
        this.screen = screen;
    }

    public void setLayout(int[] x, int[] w) {
        this.colX = x;
        this.colW = w;
    }

    public void setRows(Tab tab, List<Row> rows) {
        this.tab = tab;
        clearEntries();
        for (Row r : rows) addEntry(new Entry(r));
        setScrollAmount(0);
    }

    @Override
    public int getRowWidth() {
        return width - 20;
    }

    @Override
    protected int getScrollbarPosition() {
        return width - 8;
    }

    public final class Entry extends ObjectSelectionList.Entry<Entry> {
        final Row row;

        Entry(Row row) {
            this.row = row;
        }

        @Override
        public Component getNarration() {
            return Component.literal(row.cells()[0]);
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            Font font = minecraft.font;
            int color = row.group() ? 0xFFFFFF : 0xC8C8C8;
            int indentPx = row.indent() * 12;
            boolean[] right = tab.columns().rightAlign();

            String first = row.cells()[0];
            if (row.expandable()) first = (screen.isExpanded(row.typeId()) ? "v " : "> ") + first;
            g.drawString(font, font.plainSubstrByWidth(first, colW[0] - indentPx), left + 4 + indentPx, top + 2, color, false);

            for (int i = 1; i < row.cells().length && i < colX.length; i++) {
                String cell = row.cells()[i];
                int cx = left + 4 + colX[i];
                if (right[i]) {
                    g.drawString(font, cell, cx + colW[i] - font.width(cell), top + 2, color, false);
                } else {
                    g.drawString(font, font.plainSubstrByWidth(cell, colW[i]), cx, top + 2, color, false);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            screen.onRowClicked(row);
            return true;
        }
    }
}
```

- [ ] **Step 5: Write `ResultsScreen.java`**

```java
package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.ProfilerController;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.Fmt;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.ui.WorldOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Spec §5.3. */
public final class ResultsScreen extends Screen {
    private static final int TAB_Y = 4;
    private static final int SUMMARY_Y = 30;
    private static final int HEADER_Y = 52;
    private static final int LIST_Y = 64;
    private static final int BOTTOM_BAR = 32;
    private static final int ROW_HEIGHT = 12;

    private final SessionResult result;
    private final Environment environment;

    private Tab tab = Tab.FRAMES;
    private Ranking.Sort sort = Ranking.Sort.TOTAL;
    private final Set<String> expanded = new HashSet<>();
    private Row selected;

    private RowList list;
    private Button sortButton;
    private Button highlightButton;
    private Button teleportButton;
    private int[] colX = {0};
    private int[] colW = {100};

    public ResultsScreen(SessionResult result, Environment environment) {
        super(Component.literal("Renderable results"));
        this.result = result;
        this.environment = environment;
    }

    @Override
    protected void init() {
        int x = 4;
        for (Tab t : Tab.values()) {
            Button b = Button.builder(Component.literal(t.title()), btn -> selectTab(t)).bounds(x, TAB_Y, 84, 20).build();
            b.active = t != tab;
            addRenderableWidget(b);
            x += 88;
        }
        sortButton = Button.builder(sortLabel(), b -> {
            sort = sort.next();
            b.setMessage(sortLabel());
            rebuild();
        }).bounds(width - 4 - 110, TAB_Y, 110, 20).build();
        addRenderableWidget(sortButton);

        list = new RowList(minecraft, this, width, height - LIST_Y - BOTTOM_BAR, LIST_Y, ROW_HEIGHT);
        addRenderableWidget(list);

        int by = height - 26;
        addRenderableWidget(Button.builder(Component.literal("Copy report"), b -> ProfilerController.get().copyReport())
                .bounds(4, by, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Open folder"), b -> ProfilerController.get().openFolder())
                .bounds(98, by, 90, 20).build());
        highlightButton = Button.builder(Component.literal("Highlight"), b -> highlightSelected())
                .bounds(width / 2 - 92, by, 90, 20).build();
        teleportButton = Button.builder(Component.literal("Teleport"), b -> teleportToSelected())
                .bounds(width / 2 + 2, by, 90, 20).build();
        addRenderableWidget(highlightButton);
        addRenderableWidget(teleportButton);
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width - 94, by, 90, 20).build());

        rebuild();
    }

    private void selectTab(Tab t) {
        tab = t;
        selected = null;
        rebuildWidgets();
    }

    private void rebuild() {
        int[][] layout = tab.columns().layout(list.getRowWidth());
        colX = layout[0];
        colW = layout[1];
        list.setLayout(colX, colW);
        list.setRows(tab, Rows.build(tab, result, sort, expanded));
        updateButtons();
    }

    private Component sortLabel() {
        return Component.literal("Sort: " + sort.name().toLowerCase(Locale.ROOT));
    }

    boolean isExpanded(String typeId) {
        return typeId != null && expanded.contains(typeId);
    }

    void onRowClicked(Row row) {
        if (row.expandable()) {
            if (!expanded.add(row.typeId())) expanded.remove(row.typeId());
            rebuild();
            return;
        }
        selected = row;
        updateButtons();
    }

    private void updateButtons() {
        boolean hasPos = selected != null && selected.pos() != null;
        boolean hasEntity = selected != null && selected.entityId() >= 0;
        highlightButton.active = hasPos;
        teleportButton.active = hasPos || hasEntity;
        sortButton.visible = tab == Tab.BLOCK_ENTITIES || tab == Tab.ENTITIES;
    }

    private void highlightSelected() {
        if (selected == null || selected.pos() == null) return;
        WorldOverlay.highlight(selected.pos());
        onClose();
    }

    private void teleportToSelected() {
        if (selected == null || minecraft == null || minecraft.getConnection() == null) return;
        if (selected.pos() != null) {
            BlockPos p = selected.pos();
            minecraft.getConnection().sendCommand("tp @s " + p.getX() + " " + (p.getY() + 1) + " " + p.getZ());
        } else if (selected.entityId() >= 0 && minecraft.level != null) {
            Entity e = minecraft.level.getEntity(selected.entityId());
            if (e == null) {
                ProfilerController.chat("Renderable: that entity is no longer loaded.");
                return;
            }
            Vec3 v = e.position();
            minecraft.getConnection().sendCommand(String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f", v.x, v.y, v.z));
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        String summary = String.format(Locale.ROOT,
                "%d frames | %s s | avg %s ms | p99 %s ms | %d hitches over %s ms | %s",
                result.frameCount(), Fmt.secs(result.stats().totalNanos()), Fmt.ms(result.stats().avgNanos()),
                Fmt.ms(result.stats().p99Nanos()), result.hitches().size(), Fmt.ms(result.hitchThresholdNanos()),
                environment.entries().getOrDefault("gpu", ""));
        g.drawString(font, font.plainSubstrByWidth(summary, width - 8), 4, SUMMARY_Y, 0xE0E0E0, false);

        int left = list.getRowLeft();
        String[] headers = tab.columns().headers();
        boolean[] right = tab.columns().rightAlign();
        for (int i = 0; i < headers.length && i < colX.length; i++) {
            int cx = left + 4 + colX[i];
            if (right[i]) {
                g.drawString(font, headers[i], cx + colW[i] - font.width(headers[i]), HEADER_Y, 0xA0A0A0, false);
            } else {
                g.drawString(font, headers[i], cx, HEADER_Y, 0xA0A0A0, false);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
```

- [ ] **Step 6: Open the screen from the controller**

In `ProfilerController.java`:

- add `import io.github.edwardg117.renderable.ui.screen.ResultsScreen;`
- replace `openResults()`:

```java
    public void openResults() {
        if (lastResult == null) {
            chat("Renderable: no finished session yet.");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> mc.setScreen(new ResultsScreen(lastResult, lastEnvironment)));
    }
```

- in `finish()`, after `chat(finishedMessage(result, written));`, add:

```java
        if (RenderableConfig.OPEN_RESULTS_ON_FINISH.get()) {
            openResults();
        }
```

`tell` queues the screen change for the next tick, so it never happens in the middle of a frame.

- [ ] **Step 7: Compile and verify in the client (human step)**

```bash
./gradlew compileJava --no-daemon && ./gradlew runClient --no-daemon
```

1. `/renderable start 5` near some chests and animals. When it ends the results screen opens on the Frames tab with the summary line, the split, and a histogram.
2. Hitches tab: lists hitches or says none.
3. Block Entities tab: type rows with counts; click one to expand into `@ x y z` rows; click a position row and press Highlight: the screen closes and a red box outlines that block for ten seconds. Press `/renderable results`, select a row, Teleport: you are moved there (singleplayer has permission).
4. Sort button cycles `total, render, tick, peak, vertices, calls` and reorders rows.
5. Copy report and Open folder work from the screen. Escape closes it.
6. Resize the window to a small size: columns shrink but stay readable.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable
git commit -m "feat(ui): results screen with frames, hitches, block entity and entity tabs"
```

---

### Task 13: README, acceptance checks, acceptance log

**Files:**
- Create: `README.md`
- Create: `docs/acceptance/phase-1.md`

Spec §9 in-game checks 1, 2 and 5. Check 2 needs the pack's rendering stack in the dev client, which `-PpackMods` provides (first launch downloads Create and friends and takes several minutes). These are human steps; the orchestrator asks the user to run them and records the outcome.

- [ ] **Step 1: Write `README.md`**

```markdown
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
```

- [ ] **Step 2: Run acceptance check 1, the debug stall (human step)**

Edit `run/config/renderable-client.toml` and set `debugCommands = true` (the file exists after the first launch; NeoForge reloads it on save). Then in a world:

```
/renderable start 20
/renderable debug stall 80
```

Expected after the session: the Hitches tab shows exactly one hitch of roughly 80 ms plus that frame's normal cost, and its first top contributor is `renderable:debug_stall @ 0 0 0` at about 80.00 ms. The text report's Hitches section says the same.

- [ ] **Step 3: Run acceptance check 5, idle cost (human step)**

In the same world, standing still, open F3 and read the frame-time graph or FPS with no session running. Then remove the mod (`./gradlew runClient` from a checkout of `main~1`, or temporarily move the built jar out of a normal launcher's mods folder) and compare. Expected: no measurable difference beyond frame-to-frame noise.

- [ ] **Step 4: Run acceptance check 2, Enchantable Machinery (human step)**

```bash
./gradlew runClient -PpackMods --no-daemon
```

Create a world, place a few Create machines (a mechanical press, a mixer) with Enchantable Machinery installed, power them, stand where they are in view, and run `/renderable start 30`. Expected: the Block Entities tab is topped by Enchantable Machinery's renderer types near those machines, and the overlay labels them. Record the exact type ids and microseconds per frame.

- [ ] **Step 5: Write `docs/acceptance/phase-1.md`**

Record the date, machine, GPU string from the report header, and for each check: what was run, what was observed, pass or fail. Attach the relevant lines from the text report. If a check fails, stop and fix before continuing to CI.

- [ ] **Step 6: Commit**

```bash
git add README.md docs/acceptance/phase-1.md
git commit -m "docs: README and phase 1 acceptance log"
```

---

### Task 14: Continuous integration and release

**Files:**
- Create: `.github/workflows/build.yml`
- Create: `.github/workflows/release.yml`

`gh` was not logged in on this machine as of 2026-09-19, so creating the GitHub repository is a human step: run `gh auth login`, then `gh repo create edwardg117/Renderable --source=. --push` with `--public` or `--private` as the user prefers.

- [ ] **Step 1: Write `.github/workflows/build.yml`**

```yaml
name: build

on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: renderable
          path: build/libs/*.jar
```

- [ ] **Step 2: Write `.github/workflows/release.yml`**

```yaml
name: release

on:
  push:
    tags: ['v*']

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build --no-daemon
      - uses: softprops/action-gh-release@v2
        with:
          files: build/libs/renderable-*.jar
          generate_release_notes: true
```

- [ ] **Step 3: Confirm the wrapper is executable in git**

```bash
git ls-files -s gradlew
```

Expected: mode `100755`. If it shows `100644`, run `git update-index --chmod=+x gradlew`.

- [ ] **Step 4: Commit**

```bash
git add .github
git commit -m "ci: build on push and attach jar to GitHub releases on version tags"
```

- [ ] **Step 5: Publish and tag (human step)**

```bash
gh auth login
gh repo create edwardg117/Renderable --source=. --push --public
git tag v0.1.0
git push origin v0.1.0
```

Expected: the `build` workflow passes on `main`, and the `release` workflow attaches `renderable-0.1.0.jar` to a `v0.1.0` release. Phase 1 is complete when that jar exists and the acceptance log shows checks 1, 2 and 5 passing.

---

## Notes for the orchestrator

- Tasks 2 to 8 and the colour test in Task 11 are pure JUnit work and can be given to an implementer subagent with the task text as the whole brief. Tasks 9 to 12 need the dev client; the subagent can compile and launch, but the in-world checks need a person.
- Every task's final step is a commit. Subagents stage and report; the orchestrator commits after warning the user to touch the YubiKey.
- If a vanilla method name in a mixin fails to resolve, the verified signatures at the top of this plan are the reference. Do not guess a new name; check Mojang's mappings for 1.21.1.
- Phase 2 (particles, client tick, Flywheel, light, Iris shadow split) and phase 3 (Sodium mesh, chunk upload, vertices) get their own plans once this one has shipped `v0.1.0`.
