# Renderable Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the phase 1 profiler with particle render and tick timing, block entity and entity client-tick timing, a light-update bucket, a Flywheel bucket with visual counts, the Iris shadow-pass split, and Particles and Flywheel tabs, so a session explains every bucket the frame model defines except Sodium mesh work.

**Architecture:** The phase 1 shape is unchanged: mixins call the static `Probe`, which forwards to the render-thread `Session` in `core`. Phase 2 adds four vanilla mixins (particle engine, bound ticking block entity, level entity tick, light engine), one gated Flywheel mixin, two compat classes (`IrisCompat`, `FlywheelCompat`) that touch third-party classes only behind a mod-presence flag, bucket-only timings in `Session` for work that has no per-object key, and once-per-second Flywheel visual counts stored on the session. Reports and the results screen grow Particles and Flywheel sections.

**Tech Stack:** Java 21, Gradle 9.2.1 wrapper, ModDevGradle 2.0.147, NeoForge 21.1.220 (MixinExtras 0.5.0: `@WrapMethod`, `@WrapOperation`), JUnit 5, Gson. Compile-time only: Sodium `0.8.13`, Iris `1.8.14-beta.1`, Flywheel `1.0.6` (from the Create `6.0.10` jar), all from the Modrinth Maven.

**Spec:** `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md` §3.2, §3.4, §3.5, §4.2, §4.4, §4.5, §5.3, §6, §9 check 3, §11. Phase 1 is complete on `main` at `f4f9b3c` (49 JUnit tests, `renderable-0.1.0.jar`).

**Repository:** `H:\repos\Renderable`, branch `main`. Commits are YubiKey-signed: the orchestrator commits after telling the user to touch the key. Subagents stage files and report back; they do not commit. In-game checks are batched at the end of phase 3, so the only per-task verification here is compile, JUnit, and a dev-client launch to the title screen.

**Verified facts you can rely on** (read with `javap` from `build/moddev/artifacts/neoforge-21.1.220.jar` and the pinned Modrinth jars on 2026-09-19):

- `ParticleEngine.render(LightTexture, Camera, float)` delegates to NeoForge's `render(LightTexture, Camera, float, Frustum, Predicate<ParticleRenderType>)`; the call `Particle.render(VertexConsumer, Camera, float)` is inside the **five-argument** overload (bytecode offset 203, not in a lambda). `@WrapOperation` must target that descriptor. This corrects spec §4.2, which names the three-argument method.
- `ParticleEngine.tickParticle(Particle)` is private and calls `Particle.tick()` first (offset 1).
- `LevelChunk$BoundTickingBlockEntity.tick()` calls `BlockEntityTicker.tick(Level, BlockPos, BlockState, BlockEntity)` (invokeinterface at offset 118). Its fields are `private final T blockEntity`, `private final BlockEntityTicker<T> ticker`, and the synthetic `this$0`.
- `Level.guardEntityTick(Consumer<T>, T)` is `public <T extends Entity> void`, wraps `consumer.accept(entity)` in try/catch; `Level.isClientSide` is a `public final boolean` field.
- `LevelLightEngine.runLightUpdates()` returns `int`. `LevelRenderer.renderLevel` calls it under the `light_updates` profiler section on the render thread. `ThreadedLevelLightEngine` (server) overrides it to throw, but its `runUpdate` calls `super.runLightUpdates()` on the **server light thread**, so the base method also runs off the render thread in singleplayer; the hook must check the thread.
- Flywheel 1.0.6: `dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl$RenderDispatcherImpl` (package-private inner class) implements `VisualizationManager$RenderDispatcher` with `onStartLevelRender(RenderContext)`, `afterEntities(RenderContext)`, `beforeCrumbling(RenderContext, Long2ObjectMap<SortedSet<BlockDestructionProgress>>)`. `VisualizationManagerImpl.get(LevelAccessor)` is static and may return null; `blockEntities()`, `entities()`, `effects()` return `VisualManager<T>` with `int visualCount()`; the impl `VisualManagerImpl<T, S extends Storage<T>>` has `public S getStorage()` and `Storage.getAllVisuals()` returns `Collection<Visual>`. `BackendManager.currentBackend()` returns `Backend`; `Backend.REGISTRY.getId(Backend)` returns `ResourceLocation`. Mod id `flywheel`.
- Iris 1.8.14-beta.1: `net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()` is `public static boolean`; `net.irisshaders.iris.Iris.isPackInUseQuick()` and `getCurrentPackName()` are static. Iris's classes are at the top level of the Modrinth jar. Mod id `iris`.
- The Modrinth Sodium jar is a JarJar container: its classes live in `META-INF/jarjar/net.caffeinemc.sodium-neoforge-0.8.13+mc1.21.1-mod.jar`. Create bundles Flywheel as `META-INF/jarjar/flywheel-neoforge-1.21.1-1.0.6.jar`. Both must be extracted to be on a compile classpath. Mod id `sodium`.
- `com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation` and `Operation` exist in MixinExtras 0.5.0; `@WrapMethod` and `@WrapOperation` accept `require` and `remap`.
- Phase 1 already defines every bucket (`Bucket`), every hook name (`Hook`), the `PARTICLE`/`VISUAL` categories (`ProfileKey.Category`), `Accumulator.shadowNanos`/`tickNanos`/`tickCalls`/`vertices`, `Session.shadowPass`, and the report writers' Particles/Flywheel placeholders.

**Deviations from the spec recorded in this plan:** (1) the particle wrap targets the five-argument `render`; (2) `VisualClassKey` is not a key record, because visuals are never timed; class counts are a `Map<String, Double>` on the result; (3) `LIGHT`, `FLYWHEEL` and (in phase 3) `CHUNK_UPLOAD` are bucket-only timings with no key, so they appear in bucket splits but not in `top`; (4) the JSON `flywheel` object gains `visualCounts` as spec §6.2 shows and drops the phase 1 `visuals` array, so `schema` becomes 2.

---

## File map

```
build.gradle                                              Modify: packMods configuration, nested-jar extraction, compileOnly
gradle.properties                                         Modify: mod_version 0.2.0 (Task 10)
README.md                                                 Modify: particles, Flywheel, shadow column (Task 10)
src/main/resources/renderable.mixins.json                 Modify: register the five new mixins
src/main/java/io/github/edwardg117/renderable/
  core/ParticleKey.java                                   Create
  core/FlywheelStats.java                                 Create: averaged visual counts
  core/Session.java                                       Modify: bucket-only begin, depth reset, visual counts
  core/SessionResult.java                                 Modify: unbalancedFrames, flywheel
  core/Splits.java                                        Modify: tick children
  core/RankedEntry.java                                   Modify: callsPerFrame
  hooks/Probe.java                                        Modify: particle, tick, bucket-only entry points, render thread
  hooks/KeyFactory.java                                   Modify: particle key
  hooks/RenderableMixinPlugin.java                        Modify: records which mixins applied
  hooks/compat/IrisCompat.java                            Create
  hooks/compat/FlywheelCompat.java                        Create
  hooks/mixin/ParticleEngineMixin.java                    Create
  hooks/mixin/BoundTickingBlockEntityMixin.java           Create
  hooks/mixin/LevelMixin.java                             Create
  hooks/mixin/LevelLightEngineMixin.java                  Create
  hooks/mixin/flywheel/FlywheelRenderDispatcherMixin.java Create (gated by the plugin)
  ProfilerController.java                                 Modify: once-per-second Flywheel sampling
  report/EnvironmentCollector.java                        Modify: shader pack, Flywheel backend, mixins not applied
  report/TextReportWriter.java                            Modify: Particles and Flywheel sections, unbalanced frames
  report/JsonReportWriter.java                            Modify: schema 2, particles avgLive, flywheel object
  ui/screen/Tab.java                                      Modify: PARTICLES, FLYWHEEL and their columns
  ui/screen/Rows.java                                     Modify: particle and flywheel rows, tick/render hierarchy
  ui/screen/ResultsScreen.java                            Modify: two tab rows
src/test/java/io/github/edwardg117/renderable/
  core/KeysTest.java                                      Modify: particle key
  core/SessionTest.java                                   Modify: bucket-only, depth reset, visual counts
  core/SplitsTest.java                                    Create
  core/TestSessions.java                                  Modify: phase2() fixture
  report/TextReportWriterTest.java                        Modify: new split format, particles, flywheel
  report/JsonReportWriterTest.java                        Modify: schema 2, particles, flywheel
```

Dependency direction is unchanged: `ui`, `report`, `hooks`, root -> `core`. `core` never imports Minecraft, NeoForge, Mojang or LWJGL; `KeysTest.coreHasNoMinecraftImports` still enforces it. `hooks/compat` classes reference Iris and Flywheel only inside a nested class that is loaded on first use, behind a `PRESENT` flag.

---

### Task 1: Compile against the pinned Sodium, Iris and Flywheel jars

**Files:**
- Modify: `build.gradle`

Phase 2 and 3 mixins reference Flywheel, Iris and Sodium classes, so they must be on the compile classpath. Sodium and Flywheel ship nested inside JarJar containers, so a Gradle task extracts the nested jars into `build/nested-mod-jars/` and `compileOnly` points at them. The same extraction fixes the `-PpackMods` dev client: NeoForge's JarJar in the dev environment selects Sodium's empty container over the nested mod, so the nested jars go on `localRuntime` directly instead of the container.

- [ ] **Step 1: Replace the `configurations` and `dependencies` blocks in `build.gradle`**

Replace everything from `configurations {` through the closing brace of `dependencies {` with:

```groovy
configurations {
    runtimeClasspath.extendsFrom localRuntime
    // The pack's rendering stack from the Modrinth Maven. Sodium and Create ship the real mod jar nested
    // under META-INF/jarjar, so extractNestedModJars below pulls those out for the compile classpath.
    packMods
}

var extractNestedModJars = tasks.register('extractNestedModJars', Sync) {
    description = 'Extracts the Sodium mod jar and its libraries, and Flywheel from Create, out of their JarJar containers.'
    from(configurations.packMods.elements.map { files ->
        files.collect { f ->
            zipTree(f.asFile).matching {
                include 'META-INF/jarjar/net.caffeinemc.sodium-*.jar'
                include 'META-INF/jarjar/fabric-*.jar'
                include 'META-INF/jarjar/flywheel-*.jar'
            }
        }
    })
    into layout.buildDirectory.dir('nested-mod-jars')
    eachFile { it.path = it.name }
    includeEmptyDirs = false
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

var nestedModJars = fileTree(dir: layout.buildDirectory.dir('nested-mod-jars'), include: '*.jar', builtBy: extractNestedModJars)

dependencies {
    testImplementation platform('org.junit:junit-bom:5.11.4')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testImplementation 'com.google.code.gson:gson:2.10.1'

    packMods "maven.modrinth:sodium:${sodium_version}"
    packMods "maven.modrinth:iris:${iris_version}"
    packMods "maven.modrinth:create:${create_version}"

    // Third-party classes the optional mixins and compat classes reference. Never on the runtime classpath.
    compileOnly "maven.modrinth:iris:${iris_version}"
    compileOnly nestedModJars

    // Pass -PpackMods to run the dev client with the pack's rendering stack (Sodium, Iris,
    // Create with Flywheel, Enchantable Machinery). Slower to start; needed for acceptance checks.
    // Sodium goes on as its nested mod jar plus libraries, because JarJar in the dev environment picks the
    // empty Modrinth container over the nested mod and Iris's Sodium-compat mixins then fail to resolve.
    if (project.hasProperty('packMods')) {
        localRuntime fileTree(dir: layout.buildDirectory.dir('nested-mod-jars'), include: ['net.caffeinemc.sodium-*.jar', 'fabric-*.jar'], builtBy: extractNestedModJars)
        localRuntime "maven.modrinth:iris:${iris_version}"
        localRuntime "maven.modrinth:create:${create_version}"
        localRuntime "maven.modrinth:kotlin-for-forge:${kotlin_for_forge_version}"
        localRuntime "maven.modrinth:create-enchantable-machinery:${enchantable_machinery_version}"
    }
}
```

- [ ] **Step 2: Extract and inspect**

```bash
./gradlew extractNestedModJars --no-daemon && ls -la build/nested-mod-jars
```

Expected: `net.caffeinemc.sodium-neoforge-0.8.13+mc1.21.1-mod.jar`, `flywheel-neoforge-1.21.1-1.0.6.jar`, and four `fabric-*.jar` libraries (duplicates from Iris and Sodium collapse to one copy each).

- [ ] **Step 3: Prove the compile classpath sees the third-party classes**

```bash
./gradlew compileJava --no-daemon
./gradlew dependencies --configuration compileClasspath --no-daemon | grep -E "iris|nested-mod-jars|sodium|flywheel"
```

Expected: `BUILD SUCCESSFUL`; the compile classpath lists `maven.modrinth:iris` and the `nested-mod-jars` file collection. The runtime classpath (`--configuration runtimeClasspath`) must **not** list them when `-PpackMods` is absent.

- [ ] **Step 4: Launch the dev client with the pack mods**

```bash
./gradlew runClient -PpackMods --no-daemon
```

Poll `run/logs/latest.log`. Expected: the mod list contains `Sodium 0.8.13`, `Iris 1.8.14-beta.1`, `Flywheel 1.0.6`, `Create 6.0.10`, and the log reaches the texture atlases with no `ClassNotFoundException: net.caffeinemc` lines and no `MixinApplyError`. If Sodium still fails to load its classes, record the exact warning lines in the task report and continue; the pack mods are then verified only in the user's real instance at the end of phase 3, and the third-party mixins still compile.

- [ ] **Step 5: Run the test suite**

```bash
./gradlew test --no-daemon
```

Expected: 49 tests pass.

- [ ] **Step 6: Commit**

```bash
git add build.gradle
git commit -m "build: compile against the pinned Sodium, Iris and Flywheel jars; run the dev client with Sodium's nested mod jar"
```

---

### Task 2: Core: particle key, bucket-only timing, depth reset, Flywheel visual counts

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/ParticleKey.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/FlywheelStats.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Session.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/SessionResult.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/KeysTest.java` (append)
- Test: `src/test/java/io/github/edwardg117/renderable/core/SessionTest.java` (append)

Three core additions. `ParticleKey` identifies a particle by class. `Session.begin(null, bucket)` times work that has no key (light updates, Flywheel, later chunk upload): the bucket is credited at depth 0 but nothing goes to an accumulator or to `top`. `Session.onRenderPre` resets an unbalanced attribution stack and counts how often it had to, so a hook that threw past its `end` cannot silently disable bucket attribution for the rest of the session. `recordVisualCounts` stores once-per-second Flywheel visual counts, averaged at `finish()`.

- [ ] **Step 1: Append the failing tests**

Append to `KeysTest` inside the class:

```java
    @Test
    void particleKeyFormats() {
        ParticleKey vanilla = new ParticleKey("net.minecraft.client.particle.FlameParticle");
        assertEquals(ProfileKey.Category.PARTICLE, vanilla.category());
        assertEquals("net.minecraft.client.particle.FlameParticle", vanilla.typeId());
        assertEquals("FlameParticle", vanilla.label());
        assertEquals("particle:net.minecraft.client.particle.FlameParticle", vanilla.serialize());

        ParticleKey modded = new ParticleKey("com.simibubi.create.content.fluids.particle.FluidParticle");
        assertEquals("com.simibubi.create.content.fluids.particle.FluidParticle", modded.label());
        assertEquals(new ParticleKey("a.B"), new ParticleKey("a.B"));
    }
```

Append to `SessionTest` inside the class, after the existing tests:

```java
    @Test
    void bucketOnlyTimingFillsTheBucketWithoutAKey() {
        Session s = session(10_000);
        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(null, Bucket.LIGHT); at(4); s.end();
        at(5); s.begin(null, Bucket.FLYWHEEL);
        at(6); s.begin(s.accumulator(press), Bucket.BLOCK_ENTITY); at(7); s.end(); // nested inside flywheel
        at(9); s.end();
        at(20); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(3 * MS, f.bucket(Bucket.LIGHT));
        assertEquals(4 * MS, f.bucket(Bucket.FLYWHEEL));
        assertEquals(0, f.bucket(Bucket.BLOCK_ENTITY), "inner keyed call is not counted in a second bucket");
        assertEquals(1 * MS, s.accumulator(press).renderNanos);
        assertEquals(3, s.timedCalls());
        assertEquals(1, f.top().size(), "bucket-only timings never appear in top");
        assertEquals(press, f.top().get(0).key());
        assertTrue(s.accumulators().values().stream().noneMatch(a -> a.key == null));
    }

    @Test
    void unbalancedStackIsResetAtTheFrameBoundary() {
        Session s = session(10_000);
        Accumulator a = s.accumulator(press);
        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(a, Bucket.BLOCK_ENTITY);            // a hook threw past its end()
        at(10); s.onRenderPre(0, 0);
        assertEquals(1, s.unbalancedFrames());

        at(11); s.begin(a, Bucket.BLOCK_ENTITY); at(13); s.end();
        at(20); s.onRenderPre(0, 0);
        assertEquals(2 * MS, s.framesView().get(1).bucket(Bucket.BLOCK_ENTITY), "attribution works again after the reset");
        assertEquals(1, s.unbalancedFrames());
        assertEquals(1, s.finish().unbalancedFrames());
    }

    @Test
    void visualCountsAverageOverSamples() {
        Session s = session(10_000);
        at(0); s.onRenderPre(0, 0);
        s.recordVisualCounts(100, 10, 0, java.util.Map.of("a.PressVisual", 100, "b.FanVisual", 5));
        s.recordVisualCounts(140, 10, 2, java.util.Map.of("a.PressVisual", 140));
        at(10); s.onRenderPre(0, 0);
        FlywheelStats fw = s.finish().flywheel();
        assertEquals(2, fw.samples());
        assertEquals(120.0, fw.avgBlockEntities(), 1e-9);
        assertEquals(10.0, fw.avgEntities(), 1e-9);
        assertEquals(1.0, fw.avgEffects(), 1e-9);
        assertEquals(120.0, fw.avgByClass().get("a.PressVisual"), 1e-9);
        assertEquals(2.5, fw.avgByClass().get("b.FanVisual"), 1e-9, "a class absent from a sample counts as zero");
        assertEquals(131.0, fw.total(), 1e-9);

        assertEquals(0, TestSessions.sample().flywheel().samples());
        assertTrue(TestSessions.sample().flywheel().avgByClass().isEmpty());
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.*' --no-daemon
```

Expected: compilation failure, `cannot find symbol: class ParticleKey` (and `unbalancedFrames`, `recordVisualCounts`, `FlywheelStats`).

- [ ] **Step 3: Write `ParticleKey.java`**

```java
package io.github.edwardg117.renderable.core;

/** One particle class (spec §3.4). Particles have no identity beyond their class, so the class is the key. */
public record ParticleKey(String className) implements ProfileKey {
    private static final String VANILLA_PREFIX = "net.minecraft.client.particle.";

    @Override
    public Category category() {
        return Category.PARTICLE;
    }

    @Override
    public String typeId() {
        return className;
    }

    /** Vanilla particle classes drop their package for readability; others keep it so mods stay distinguishable. */
    @Override
    public String label() {
        return className.startsWith(VANILLA_PREFIX) ? className.substring(VANILLA_PREFIX.length()) : className;
    }

    @Override
    public String serialize() {
        return "particle:" + className;
    }
}
```

- [ ] **Step 4: Write `FlywheelStats.java`**

```java
package io.github.edwardg117.renderable.core;

import java.util.Map;

/**
 * Flywheel visual counts averaged over the once-per-second samples taken while sampling (spec §4.2).
 * {@code samples} is 0 when Flywheel was absent or never sampled; every average is then 0.
 */
public record FlywheelStats(int samples,
                            double avgBlockEntities,
                            double avgEntities,
                            double avgEffects,
                            Map<String, Double> avgByClass) {

    private static final FlywheelStats NONE = new FlywheelStats(0, 0, 0, 0, Map.of());

    public static FlywheelStats none() {
        return NONE;
    }

    /** Average number of visuals of any kind. */
    public double total() {
        return avgBlockEntities + avgEntities + avgEffects;
    }
}
```

- [ ] **Step 5: Modify `Session.java`**

Add the import `java.util.LinkedHashMap` (keep the existing imports). Add these fields after `public boolean shadowPass;`:

```java
    /** Frames that started with a call still open, because a hook threw past its end(). The stack is reset. */
    private int unbalancedFrames;

    private int visualSamples;
    private long visualBlockEntities;
    private long visualEntities;
    private long visualEffects;
    private final LinkedHashMap<String, Long> visualByClass = new LinkedHashMap<>();
```

Add these methods after `timedCalls()`:

```java
    /** Number of frame boundaries at which the attribution stack was found unbalanced and reset. */
    public int unbalancedFrames() {
        return unbalancedFrames;
    }

    /**
     * Records one sample of Flywheel visual counts (spec §4.2). Called about once per second by the
     * controller, so it may allocate. Classes absent from a sample count as zero for that sample.
     */
    public void recordVisualCounts(int blockEntities, int entities, int effects, Map<String, Integer> byClass) {
        visualSamples++;
        visualBlockEntities += blockEntities;
        visualEntities += entities;
        visualEffects += effects;
        for (Map.Entry<String, Integer> e : byClass.entrySet()) {
            visualByClass.merge(e.getKey(), (long) e.getValue(), Long::sum);
        }
    }

    private FlywheelStats flywheelStats() {
        if (visualSamples == 0) return FlywheelStats.none();
        LinkedHashMap<String, Double> avg = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : visualByClass.entrySet()) {
            avg.put(e.getKey(), (double) e.getValue() / visualSamples);
        }
        return new FlywheelStats(visualSamples,
                (double) visualBlockEntities / visualSamples,
                (double) visualEntities / visualSamples,
                (double) visualEffects / visualSamples,
                Map.copyOf(avg));
    }
```

In `onRenderPre`, replace the block

```java
        boolean done = false;
        if (frameStartNanos >= 0) {
            closeFrame(now, gcCount, gcMillis);
```

with

```java
        if (depth != 0) {
            unbalancedFrames++;
            depth = 0;
            Arrays.fill(stack, null);
        }

        boolean done = false;
        if (frameStartNanos >= 0) {
            closeFrame(now, gcCount, gcMillis);
```

Replace the Javadoc and signature of `begin` and the whole of `end()` with:

```java
    /**
     * Start timing a call attributed to {@code a} in {@code bucket}. Must be paired with {@link #end()}.
     * {@code a} may be null for work that has no key (light updates, Flywheel, chunk upload): the bucket is
     * still credited when the call is outermost, but nothing is added to an accumulator or to the frame's top list.
     */
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

        if (a != null) {
            if (bucket.isRender()) {
                a.renderNanos += elapsed;
                a.calls++;
                if (shadowPass) a.shadowNanos += elapsed;
            } else {
                a.tickNanos += elapsed;
                a.tickCalls++;
            }
        }

        if (frameStartNanos >= 0) {
            if (a != null) {
                if (!a.inFrame) {
                    a.inFrame = true;
                    touched.add(a);
                }
                a.frameNanos += elapsed;
            }
            if (depth == 0) {
                buckets[bucket.ordinal()] += elapsed;
            }
        }
    }
```

Replace `finish()` with:

```java
    /** Freeze the session into a result. The frame currently open is dropped because it has no end. */
    public SessionResult finish() {
        List<FrameRecord> frameList = List.copyOf(frames);
        FrameStats stats = FrameStats.of(frameList);
        long threshold = HitchDetector.threshold(stats.p50Nanos(), config.hitchMedianMultiplier(), config.hitchMinExcessNanos());
        List<FrameRecord> hitches = HitchDetector.detect(frameList, threshold);
        double overheadMs = timedCalls * config.calibratedCallNanos() / 1_000_000.0;
        return new SessionResult(config, wallStartMillis, frameList, accumulators(), stats, threshold, hitches,
                timedCalls, overheadMs, unbalancedFrames, flywheelStats());
    }
```

- [ ] **Step 6: Modify `SessionResult.java`**

Replace the record header (keep the methods) with:

```java
/** Everything a finished session knows. Immutable; produced by {@link Session#finish()}. */
public record SessionResult(SessionConfig config,
                            long wallStartMillis,
                            List<FrameRecord> frames,
                            Map<ProfileKey, Accumulator> accumulators,
                            FrameStats stats,
                            long hitchThresholdNanos,
                            List<FrameRecord> hitches,
                            long timedCalls,
                            double overheadEstimateMs,
                            int unbalancedFrames,
                            FlywheelStats flywheel) {
```

- [ ] **Step 7: Run the tests to verify they pass**

```bash
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`; KeysTest 7, SessionTest 21, everything else unchanged (53 total). If `CalibrationTest.fakeClockGivesExactPerPairCost` changed, something in `begin`/`end` allocated an extra clock read; it must still be exactly 14.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/core src/test/java/io/github/edwardg117/renderable/core
git commit -m "feat(core): particle key, bucket-only timings, stack reset at frame boundaries, Flywheel visual counts"
```

---

### Task 3: Core: tick children in splits, calls per frame, phase 2 fixture

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Splits.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/RankedEntry.java`
- Modify: `src/test/java/io/github/edwardg117/renderable/core/TestSessions.java`
- Create: `src/test/java/io/github/edwardg117/renderable/core/SplitsTest.java`
- Modify: `src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java` (two assertions)

Once tick buckets carry data, printing them beside `tick` double counts (the phase 1 review's item 2). Both split lines now print `tick X [particle tick a, be tick b, entity tick c]` with only the non-zero children in brackets, then the render buckets, `other render` and swap. The parts outside the brackets sum to the frame. `RankedEntry.callsPerFrame` is the particle tab's "avg live count". `TestSessions.phase2()` is a second deterministic fixture with particles, ticks, light, Flywheel and visual samples; `sample()` is unchanged so phase 1 tests keep their numbers.

- [ ] **Step 1: Write the failing tests**

`src/test/java/io/github/edwardg117/renderable/core/SplitsTest.java`:

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
        assertEquals("tick 2.0 [be tick 0.8, entity tick 0.4] | block entity 5.0 | light 0.5 | other render 9.5 | swap 3.0",
                Splits.frame(f));
    }

    @Test
    void frameSplitOmitsBracketsWhenNoTickBucketHasData() {
        FrameRecord f = frame(160, 10, 100, Bucket.ENTITY.ordinal(), 20);
        assertEquals("tick 1.0 | entity 2.0 | other render 8.0 | swap 5.0", Splits.frame(f));
    }

    @Test
    void averageSplitUsesTwoDecimalsAndSkipsEmptyRenderBuckets() {
        FrameStats st = FrameStats.of(List.of(
                frame(200, 20, 150, Bucket.BLOCK_ENTITY.ordinal(), 50, Bucket.PARTICLE_TICK.ordinal(), 10),
                frame(200, 20, 150, Bucket.BLOCK_ENTITY.ordinal(), 30, Bucket.PARTICLE_TICK.ordinal(), 10)));
        assertEquals("tick 2.00 [particle tick 1.00] | block entity 4.00 | other render 11.00 | swap/wait 3.00",
                Splits.average(st));
    }

    @Test
    void callsPerFrameIsTheAverageLiveCount() {
        RankedEntry e = new RankedEntry("x", null, "x", ProfileKey.Category.PARTICLE, 1, 60, 40, 0, 0, 0, 0, 0);
        assertEquals(3.0, e.callsPerFrame(20), 1e-9);
        assertEquals(0.0, e.callsPerFrame(0), 1e-9);
    }
}
```

In `TextReportWriterTest.framesSection`, replace the time-split assertion with:

```java
        assertTrue(s.contains("  Time split (avg ms/frame): tick 1.00 [entity tick 0.20] | block entity 7.40 | entity 0.80 | other render 2.10 | swap/wait 7.90\n"), s);
```

In `TextReportWriterTest.hitchSectionListsWorstFramesWithTopContributors`, replace the `#1` assertion with:

```java
        assertTrue(section.contains("\n  #1  frame 10 @ 0.2s  80.0 ms  |  tick 1.0 [entity tick 0.2] | block entity 62.5 | entity 0.8 | other render 2.1 | swap 13.6  |  gc 0 ms\n"), section);
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.SplitsTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: method callsPerFrame`, or assertion failures on the split strings.

- [ ] **Step 3: Replace `Splits.java`**

```java
package io.github.edwardg117.renderable.core;

/**
 * One-line descriptions of how a frame, or the average frame, was spent. Tick buckets are children of
 * {@code tick} and are printed in brackets after it, so the parts outside the brackets sum to the frame.
 */
public final class Splits {

    private Splits() {
    }

    /** Average per-frame split, two decimals: tick with its children, each non-zero render bucket, other render, swap. */
    public static String average(FrameStats st) {
        StringBuilder sb = new StringBuilder();
        sb.append("tick ").append(Fmt.ms2(st.avgTickNanos()));
        appendTickChildren(sb, st.avgBucketNanos(), true);
        for (Bucket b : Bucket.VALUES) {
            long v = st.avgBucket(b);
            if (b.isRender() && v > 0) sb.append(" | ").append(b.label()).append(' ').append(Fmt.ms2(v));
        }
        sb.append(" | other render ").append(Fmt.ms2(st.avgOtherRenderNanos()));
        sb.append(" | swap/wait ").append(Fmt.ms2(st.avgSwapNanos()));
        return sb.toString();
    }

    /** One frame's split, one decimal: tick with its children, each non-zero render bucket, other render, swap. */
    public static String frame(FrameRecord f) {
        StringBuilder sb = new StringBuilder();
        sb.append("tick ").append(Fmt.ms(f.tickNanos()));
        appendTickChildren(sb, f.buckets(), false);
        for (Bucket b : Bucket.VALUES) {
            long v = f.bucket(b);
            if (b.isRender() && v > 0) sb.append(" | ").append(b.label()).append(' ').append(Fmt.ms(v));
        }
        sb.append(" | other render ").append(Fmt.ms(f.otherRenderNanos()));
        sb.append(" | swap ").append(Fmt.ms(f.swapNanos()));
        return sb.toString();
    }

    private static void appendTickChildren(StringBuilder sb, long[] buckets, boolean twoDecimals) {
        boolean any = false;
        for (Bucket b : Bucket.VALUES) {
            long v = buckets[b.ordinal()];
            if (b.isRender() || v <= 0) continue;
            sb.append(any ? ", " : " [").append(b.label()).append(' ').append(twoDecimals ? Fmt.ms2(v) : Fmt.ms(v));
            any = true;
        }
        if (any) sb.append(']');
    }
}
```

- [ ] **Step 4: Add `callsPerFrame` to `RankedEntry.java`**

Add after `usPerFrame`:

```java
    /** Render calls per frame. For particles this is the average number alive and rendered each frame. */
    public double callsPerFrame(int frames) {
        return frames == 0 ? 0 : (double) calls / frames;
    }
```

- [ ] **Step 5: Add the `phase2()` fixture to `TestSessions.java`**

Add these constants after `PLAYER`:

```java
    public static final ParticleKey FLAME = new ParticleKey("net.minecraft.client.particle.FlameParticle");
    public static final String PRESS_VISUAL = "com.simibubi.create.content.kinetics.press.PressVisual";
```

Add this method after `sample()`:

```java
    /**
     * Phase 2 fixture: the {@link #sample()} skeleton plus particles, ticks, light, Flywheel and visual samples.
     *
     * Per frame: 0.1 ms other render, press 2 ms (60 in frame 10), press2 1.5, mixer 1, cow 0.5, player 0.3,
     * flywheel 1.2 (bucket only), three FlameParticle renders of 0.1, light 0.3 (bucket only), 2 ms other render;
     * render 9.2 ms (67.2 in frame 10). Tick: cow 0.2 (entity tick), press 0.4 (be tick), two FlameParticle
     * ticks of 0.05 (particle tick), 0.3 other tick; tick 1.0 ms. Frames are 16 ms (frame 10: 80 ms).
     * Visual counts sampled at frames 5, 10 and 15: 100/120/140 block entities, 10 entities, 0 effects,
     * PRESS_VISUAL 100/120/140. One 5 ms GC in frame 15.
     *
     * Totals: 20 frames, 384 ms; tick avg 1.00 [particle tick 0.10, be tick 0.40, entity tick 0.20];
     * block entity 7.40, entity 0.80, particle render 0.30, flywheel 1.20, light 0.30, other render 2.10,
     * swap 6.10. FlameParticle 6 ms render over 60 calls (3.0 per frame), 2 ms tick over 40 calls.
     * 14 timed calls per frame, 280 total, at 25 ns = 0.007 ms overhead. Flywheel: 3 samples, avg 120/10/0.
     */
    public static SessionResult phase2() {
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 25), () -> now[0], WALL_START);
        Accumulator press = s.accumulator(PRESS);
        Accumulator press2 = s.accumulator(PRESS2);
        Accumulator mixer = s.accumulator(MIXER);
        Accumulator cow = s.accumulator(COW);
        Accumulator player = s.accumulator(PLAYER);
        Accumulator flame = s.accumulator(FLAME);

        long t = 0;
        for (int i = 0; i <= 20; i++) {
            long gcCount = i >= 16 ? 1 : 0;
            long gcMillis = i >= 16 ? 5 : 0;
            now[0] = t;
            s.onRenderPre(gcCount, gcMillis);
            if (i == 20) break;
            if (i == 5 || i == 10 || i == 15) {
                int be = 100 + (i - 5) * 4;
                s.recordVisualCounts(be, 10, 0, java.util.Map.of(PRESS_VISUAL, be));
            }

            long frameStart = t;
            long pressCost = i == 10 ? 60 * MS : 2 * MS;

            t += 100_000;
            now[0] = t; s.begin(press, Bucket.BLOCK_ENTITY);  t += pressCost;  now[0] = t; s.end();
            now[0] = t; s.begin(press2, Bucket.BLOCK_ENTITY); t += 1_500_000;  now[0] = t; s.end();
            now[0] = t; s.begin(mixer, Bucket.BLOCK_ENTITY);  t += 1 * MS;     now[0] = t; s.end();
            now[0] = t; s.begin(cow, Bucket.ENTITY);          t += 500_000;    now[0] = t; s.end();
            now[0] = t; s.begin(player, Bucket.ENTITY);       t += 300_000;    now[0] = t; s.end();
            now[0] = t; s.begin(null, Bucket.FLYWHEEL);       t += 1_200_000;  now[0] = t; s.end();
            for (int p = 0; p < 3; p++) {
                now[0] = t; s.begin(flame, Bucket.PARTICLE_RENDER); t += 100_000; now[0] = t; s.end();
            }
            now[0] = t; s.begin(null, Bucket.LIGHT);          t += 300_000;    now[0] = t; s.end();
            t += 2 * MS;
            now[0] = t; s.onRenderPost();

            now[0] = t; s.onTickPre();
            now[0] = t; s.begin(cow, Bucket.ENTITY_TICK);     t += 200_000;    now[0] = t; s.end();
            now[0] = t; s.begin(press, Bucket.BE_TICK);       t += 400_000;    now[0] = t; s.end();
            for (int p = 0; p < 2; p++) {
                now[0] = t; s.begin(flame, Bucket.PARTICLE_TICK); t += 50_000; now[0] = t; s.end();
            }
            t += 300_000;
            now[0] = t; s.onTickPost();

            t = frameStart + (i == 10 ? 80 : 16) * MS;
        }
        return s.finish();
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

```bash
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`; SplitsTest 4, TextReportWriterTest 5 (with the two updated assertions), all others unchanged (57 total).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/core src/test/java/io/github/edwardg117/renderable
git commit -m "feat(core): nest tick buckets under tick in splits, calls per frame, phase 2 fixture"
```

---

### Task 4: Probe entry points, particle key factory, Iris and Flywheel compat

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/Probe.java` (replace the whole file)
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/KeyFactory.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/compat/IrisCompat.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/compat/FlywheelCompat.java`

`Probe` gains every phase 2 entry point at once so the mixin tasks that follow are pure wiring: particle render and tick keyed by class, block entity and entity tick reusing the per-instance cache from the render hooks (so render and tick land on the same accumulator), and bucket-only begin/end that ignores calls off the render thread. Iris's shadow flag is read through `IrisCompat` once per timed render call; `FlywheelCompat` samples visual counts. Both compat classes touch third-party classes only inside a nested `Impl` class that the JVM loads on first use, behind a `PRESENT` flag, so a client without Iris or Flywheel never resolves those classes. No unit tests: everything here touches Minecraft. Verification is compile and the title screen.

- [ ] **Step 1: Write `hooks/compat/IrisCompat.java`**

```java
package io.github.edwardg117.renderable.hooks.compat;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.neoforged.fml.ModList;

/**
 * Spec §4.2: Iris renders block entities, entities and particles a second time in its shadow pass. The
 * shadow flag is a static read on Iris's side, so a timed call can be tagged as shadow work for the
 * {@code shadowNanos} column. Iris classes are referenced only inside {@link Impl}, which is loaded on first
 * use and only when {@link #PRESENT}.
 */
public final class IrisCompat {
    public static final boolean PRESENT = ModList.get().isLoaded("iris");

    private IrisCompat() {
    }

    /** True while Iris is rendering its shadow pass; always false without Iris. One static call. */
    public static boolean shadowActive() {
        return PRESENT && Impl.shadowActive();
    }

    /** The active shader pack name, "none" when shaders are off, "absent" without Iris. */
    public static String packName() {
        return PRESENT ? Impl.packName() : "absent";
    }

    private static final class Impl {
        static boolean shadowActive() {
            return ShadowRenderingState.areShadowsCurrentlyBeingRendered();
        }

        static String packName() {
            if (!Iris.isPackInUseQuick()) return "none";
            String name = Iris.getCurrentPackName();
            return name == null || name.isEmpty() ? "unknown" : name;
        }
    }
}
```

- [ ] **Step 2: Write `hooks/compat/FlywheelCompat.java`**

```java
package io.github.edwardg117.renderable.hooks.compat;

import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.api.visual.Visual;
import dev.engine_room.flywheel.api.visualization.VisualManager;
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl;
import dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.util.HashMap;
import java.util.Map;

/**
 * Spec §4.2 and §4.4: Flywheel draws Create's machines through instancing, so they never reach the block
 * entity renderer. Renderable reports Flywheel as one bucket (see the Flywheel mixin) plus visual counts,
 * sampled here about once per second. Flywheel classes are referenced only inside {@link Impl}.
 */
public final class FlywheelCompat {
    public static final boolean PRESENT = ModList.get().isLoaded("flywheel");

    /** One sample of live visuals. {@code byClass} maps visual class name to its count in this sample. */
    public record Counts(int blockEntities, int entities, int effects, Map<String, Integer> byClass) {
    }

    private FlywheelCompat() {
    }

    /** Counts the level's visuals, or null when Flywheel is absent or not visualizing this level. Render thread only. */
    public static Counts sample(Level level) {
        return PRESENT ? Impl.sample(level) : null;
    }

    /** The active backend id such as {@code flywheel:instancing}, or "absent" without Flywheel. */
    public static String backendName() {
        return PRESENT ? Impl.backendName() : "absent";
    }

    private static final class Impl {
        static Counts sample(Level level) {
            VisualizationManagerImpl manager = VisualizationManagerImpl.get(level);
            if (manager == null) return null;
            Map<String, Integer> byClass = new HashMap<>();
            countByClass(manager.blockEntities(), byClass);
            countByClass(manager.entities(), byClass);
            countByClass(manager.effects(), byClass);
            return new Counts(manager.blockEntities().visualCount(), manager.entities().visualCount(),
                    manager.effects().visualCount(), byClass);
        }

        private static void countByClass(VisualManager<?> manager, Map<String, Integer> byClass) {
            if (manager instanceof VisualManagerImpl<?, ?> impl) {
                for (Visual visual : impl.getStorage().getAllVisuals()) {
                    byClass.merge(visual.getClass().getName(), 1, Integer::sum);
                }
            }
        }

        static String backendName() {
            Backend backend = BackendManager.currentBackend();
            ResourceLocation id = Backend.REGISTRY.getId(backend);
            return id == null ? String.valueOf(backend) : id.toString();
        }
    }
}
```

- [ ] **Step 3: Add the particle key to `KeyFactory.java`**

Add this method after `entity(Entity)`:

```java
    static ParticleKey particle(Class<?> particleClass) {
        return new ParticleKey(particleClass.getName());
    }
```

and the import `io.github.edwardg117.renderable.core.ParticleKey`.

- [ ] **Step 4: Replace `Probe.java`**

```java
package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.hooks.compat.IrisCompat;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * The single static entry point every hook calls (spec §4). Mixins check {@link #ACTIVE} first, so the idle
 * cost is one volatile read. Accumulators are cached per Minecraft object (block entities, entities) or per
 * class (particles) so the hot path allocates nothing after a key has been seen once. Render and tick hooks
 * for the same object share one accumulator.
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

    /** True while a session is sampling. Volatile so worker-thread hooks see it. */
    public static volatile boolean ACTIVE;

    private static Session session;
    private static Listener listener;
    private static Thread renderThread;
    private static final IdentityHashMap<Object, Accumulator> BY_INSTANCE = new IdentityHashMap<>(4096);
    private static final HashMap<Class<?>, Accumulator> BY_CLASS = new HashMap<>(256);
    private static final boolean[] SEEN = new boolean[Hook.values().length];

    private Probe() {
    }

    public static void setListener(Listener l) {
        listener = l;
    }

    static Listener listener() {
        return listener;
    }

    /** Attaches a session. Must be called on the render thread: bucket-only hooks compare against it. */
    public static void attach(Session s) {
        BY_INSTANCE.clear();
        BY_CLASS.clear();
        renderThread = Thread.currentThread();
        session = s;
        ACTIVE = true;
    }

    public static void detach() {
        ACTIVE = false;
        session = null;
        BY_INSTANCE.clear();
        BY_CLASS.clear();
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
        markShadow(s);
        s.begin(instanceAccumulator(s, be), Bucket.BLOCK_ENTITY);
    }

    public static void beginEntity(Entity e) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.ENTITY_RENDER.ordinal()] = true;
        markShadow(s);
        s.begin(instanceAccumulator(s, e), Bucket.ENTITY);
    }

    public static void beginParticleRender(Particle p) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.PARTICLE_RENDER.ordinal()] = true;
        markShadow(s);
        s.begin(classAccumulator(s, p), Bucket.PARTICLE_RENDER);
    }

    public static void beginBlockEntityTick(BlockEntity be) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.BLOCK_ENTITY_TICK.ordinal()] = true;
        s.begin(instanceAccumulator(s, be), Bucket.BE_TICK);
    }

    public static void beginEntityTick(Entity e) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.ENTITY_TICK.ordinal()] = true;
        s.begin(instanceAccumulator(s, e), Bucket.ENTITY_TICK);
    }

    public static void beginParticleTick(Particle p) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.PARTICLE_TICK.ordinal()] = true;
        s.begin(classAccumulator(s, p), Bucket.PARTICLE_TICK);
    }

    /**
     * Times work that has no key: light updates, Flywheel, chunk upload. Ignored off the render thread,
     * because the light engine's base method also runs on the server's light thread in singleplayer.
     * Pair with {@link #endBucket()}, never with {@link #end()}.
     */
    public static void beginBucket(Bucket bucket, Hook hook) {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return;
        SEEN[hook.ordinal()] = true;
        s.begin(null, bucket);
    }

    /** Ends a bucket-only timing. Makes the same thread decision as {@link #beginBucket} so the stack stays balanced. */
    public static void endBucket() {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return;
        s.end();
    }

    /** Ends the innermost keyed call. Safe to call when no session is running. */
    public static void end() {
        Session s = session;
        if (s != null) s.end();
    }

    private static Accumulator instanceAccumulator(Session s, BlockEntity be) {
        Accumulator a = BY_INSTANCE.get(be);
        if (a == null) {
            a = s.accumulator(KeyFactory.blockEntity(be));
            BY_INSTANCE.put(be, a);
        }
        return a;
    }

    private static Accumulator instanceAccumulator(Session s, Entity e) {
        Accumulator a = BY_INSTANCE.get(e);
        if (a == null) {
            a = s.accumulator(KeyFactory.entity(e));
            BY_INSTANCE.put(e, a);
        }
        return a;
    }

    private static Accumulator classAccumulator(Session s, Particle p) {
        Class<?> c = p.getClass();
        Accumulator a = BY_CLASS.get(c);
        if (a == null) {
            a = s.accumulator(KeyFactory.particle(c));
            BY_CLASS.put(c, a);
        }
        return a;
    }

    /** Iris draws block entities, entities and particles again in its shadow pass; tag those calls. */
    private static void markShadow(Session s) {
        if (IrisCompat.PRESENT) {
            boolean shadow = IrisCompat.shadowActive();
            s.shadowPass = shadow;
            if (shadow) SEEN[Hook.IRIS_SHADOW.ordinal()] = true;
        }
    }
}
```

- [ ] **Step 5: Compile, test, launch**

```bash
./gradlew compileJava --no-daemon
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL` (this proves Task 1's compile classpath resolves `net.irisshaders.iris.*` and `dev.engine_room.flywheel.*`) and 57 tests. Then launch the dev client **without** `-PpackMods` to the title screen and confirm `Renderable 0.1.0 loaded` with no `NoClassDefFoundError` for Iris or Flywheel classes: the compat classes must not be initialised outside a session, and their `Impl` nested classes must never load when the mods are absent.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/hooks
git commit -m "feat(hooks): probe entry points for particles, ticks and bucket-only work; Iris and Flywheel compat"
```

---

### Task 5: Particle render and tick mixin

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/ParticleEngineMixin.java`
- Modify: `src/main/resources/renderable.mixins.json`

Two `@WrapOperation`s on `ParticleEngine`: the `Particle.render` call inside NeoForge's five-argument `render`, and the `Particle.tick` call inside the private `tickParticle`. Each particle render call while sampling is one live particle, so `calls / frames` is the average live count per class with no extra bookkeeping.

- [ ] **Step 1: Write the mixin**

```java
package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Times every particle render and tick by particle class (spec §4.2). The render call lives in NeoForge's
 * five-argument {@code render} overload, which the three-argument vanilla signature delegates to.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @WrapOperation(
            method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"))
    private void renderable$timeParticleRender(Particle particle, VertexConsumer consumer, Camera camera, float partialTick,
                                               Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(particle, consumer, camera, partialTick);
            return;
        }
        Probe.beginParticleRender(particle);
        try {
            original.call(particle, consumer, camera, partialTick);
        } finally {
            Probe.end();
        }
    }

    @WrapOperation(
            method = "tickParticle",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;tick()V"))
    private void renderable$timeParticleTick(Particle particle, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(particle);
            return;
        }
        Probe.beginParticleTick(particle);
        try {
            original.call(particle);
        } finally {
            Probe.end();
        }
    }
}
```

- [ ] **Step 2: Register it**

In `renderable.mixins.json`, change the `client` array to:

```json
  "client": [
    "BlockEntityRenderDispatcherMixin",
    "EntityRenderDispatcherMixin",
    "ParticleEngineMixin"
  ],
```

- [ ] **Step 3: Compile and prove the mixin applies**

```bash
./gradlew compileJava --no-daemon
```

Then launch with Mixin's class export on (the environment variable reaches the game JVM):

```bash
JAVA_TOOL_OPTIONS="-Dmixin.debug.export=true" ./gradlew runClient --no-daemon
```

Wait for the texture atlases, then:

```bash
javap -p -cp run/.mixin.out/class net.minecraft.client.particle.ParticleEngine | grep -E 'renderable\$'
```

Expected: two synthetic methods containing `renderable$timeParticleRender` and `renderable$timeParticleTick`; no `MixinApplyError`, `InvalidInjectionException` or `Critical injection failure` in `run/logs/latest.log`. If the render target is not found, the descriptor in `method =` is wrong for this NeoForge build; re-run `javap -p -cp build/moddev/artifacts/neoforge-21.1.220.jar net.minecraft.client.particle.ParticleEngine | grep 'void render'` and use exactly what it prints. Stop the client and delete `run/.mixin.out`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/hooks/mixin/ParticleEngineMixin.java src/main/resources/renderable.mixins.json
git commit -m "feat(hooks): time particle render and tick by class"
```

---

### Task 6: Block entity and entity client-tick mixins

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/BoundTickingBlockEntityMixin.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/LevelMixin.java`
- Modify: `src/main/resources/renderable.mixins.json`

Client ticks run on the render thread between frames, inside `tickNanos`. Block entities tick through `LevelChunk$BoundTickingBlockEntity.tick()`, which delegates to a `BlockEntityTicker`; wrapping that delegate call gives the level and the block entity as arguments with no shadow fields. Entities tick through `Level.guardEntityTick`, which is a plain method on `Level`; both classes are shared with the integrated server, so each hook checks `isClientSide` and leaves server ticks alone (no `Probe` call, so no cross-thread access).

- [ ] **Step 1: Write `BoundTickingBlockEntityMixin.java`**

```java
package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Times every client-side block entity tick (spec §4.2). Server ticks in singleplayer pass straight through. */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BoundTickingBlockEntityMixin {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"))
    private void renderable$timeTick(BlockEntityTicker ticker, Level level, BlockPos pos, BlockState state, BlockEntity blockEntity,
                                     Operation<Void> original) {
        if (!Probe.ACTIVE || !level.isClientSide) {
            original.call(ticker, level, pos, state, blockEntity);
            return;
        }
        Probe.beginBlockEntityTick(blockEntity);
        try {
            original.call(ticker, level, pos, state, blockEntity);
        } finally {
            Probe.end();
        }
    }
}
```

- [ ] **Step 2: Write `LevelMixin.java`**

```java
package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Consumer;

/** Times every client-side entity tick (spec §4.2). {@code guardEntityTick} is the one funnel vanilla uses. */
@Mixin(Level.class)
public abstract class LevelMixin {

    @Shadow
    @Final
    public boolean isClientSide;

    @WrapMethod(method = "guardEntityTick")
    private void renderable$timeEntityTick(Consumer<?> consumer, Entity entity, Operation<Void> original) {
        if (!Probe.ACTIVE || !isClientSide) {
            original.call(consumer, entity);
            return;
        }
        Probe.beginEntityTick(entity);
        try {
            original.call(consumer, entity);
        } finally {
            Probe.end();
        }
    }
}
```

- [ ] **Step 3: Register both**

```json
  "client": [
    "BlockEntityRenderDispatcherMixin",
    "EntityRenderDispatcherMixin",
    "ParticleEngineMixin",
    "BoundTickingBlockEntityMixin",
    "LevelMixin"
  ],
```

- [ ] **Step 4: Compile and prove both apply**

`./gradlew compileJava --no-daemon`, then the same export launch as Task 5. `LevelChunk$BoundTickingBlockEntity` only loads when a world loads, so the title-screen launch proves `Level` (loaded early) and the config; check:

```bash
javap -p -cp run/.mixin.out/class net.minecraft.world.level.Level | grep -E 'renderable\$'
ls run/.mixin.out/class/net/minecraft/world/level/chunk/ 2>/dev/null
```

Expected: `Level` shows `renderable$timeEntityTick`; the chunk directory may be absent at the title screen (expected). `run/logs/latest.log` has no mixin errors. If `Level`'s wrap fails on the generic signature, change the handler parameters to `(Consumer<Entity> consumer, Entity entity, Operation<Void> original)` and report the change. Stop the client, delete `run/.mixin.out`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/hooks/mixin src/main/resources/renderable.mixins.json
git commit -m "feat(hooks): time client-side block entity and entity ticks"
```

---

### Task 7: Light bucket, Flywheel bucket, visual sampling, environment

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/LevelLightEngineMixin.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/flywheel/FlywheelRenderDispatcherMixin.java`
- Modify: `src/main/resources/renderable.mixins.json`
- Modify: `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/EnvironmentCollector.java`

`runLightUpdates` is bucket-only work on the render thread (it runs inside `LevelRenderer.renderLevel`). The Flywheel mixin lives in the `flywheel` sub-package so `RenderableMixinPlugin` only applies it when the `flywheel` mod is loaded, and every injector carries `require = 0` so a Flywheel version bump degrades to a missing bucket instead of a crash (spec §4.5). The controller samples visual counts once per second from `onFrameStarted`. The environment gains the shader pack and the Flywheel backend.

- [ ] **Step 1: Write `LevelLightEngineMixin.java`**

```java
package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.hooks.Hook;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Times the client's light updates (spec §4.2). The same base method runs on the server's light thread in
 * singleplayer; {@code Probe.beginBucket} ignores calls off the render thread.
 */
@Mixin(LevelLightEngine.class)
public abstract class LevelLightEngineMixin {

    @WrapMethod(method = "runLightUpdates")
    private int renderable$timeLightUpdates(Operation<Integer> original) {
        if (!Probe.ACTIVE) {
            return original.call();
        }
        Probe.beginBucket(Bucket.LIGHT, Hook.LIGHT);
        try {
            return original.call();
        } finally {
            Probe.endBucket();
        }
    }
}
```

- [ ] **Step 2: Write `hooks/mixin/flywheel/FlywheelRenderDispatcherMixin.java`**

```java
package io.github.edwardg117.renderable.hooks.mixin.flywheel;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.engine_room.flywheel.api.backend.RenderContext;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.hooks.Hook;
import io.github.edwardg117.renderable.hooks.Probe;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.BlockDestructionProgress;
import org.spongepowered.asm.mixin.Mixin;

import java.util.SortedSet;

/**
 * Spec §4.2 and §4.4: Flywheel's per-level render dispatcher is the only render-thread entry into its
 * instancing work, so its three calls per frame are summed into the FLYWHEEL bucket. Applied only when the
 * flywheel mod is loaded (package gate in RenderableMixinPlugin); require = 0 so a version bump cannot crash.
 */
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl$RenderDispatcherImpl", remap = false)
public abstract class FlywheelRenderDispatcherMixin {

    @WrapMethod(method = "onStartLevelRender", remap = false, require = 0)
    private void renderable$timeStart(RenderContext context, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(context);
            return;
        }
        Probe.beginBucket(Bucket.FLYWHEEL, Hook.FLYWHEEL);
        try {
            original.call(context);
        } finally {
            Probe.endBucket();
        }
    }

    @WrapMethod(method = "afterEntities", remap = false, require = 0)
    private void renderable$timeAfterEntities(RenderContext context, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(context);
            return;
        }
        Probe.beginBucket(Bucket.FLYWHEEL, Hook.FLYWHEEL);
        try {
            original.call(context);
        } finally {
            Probe.endBucket();
        }
    }

    @WrapMethod(method = "beforeCrumbling", remap = false, require = 0)
    private void renderable$timeCrumbling(RenderContext context, Long2ObjectMap<SortedSet<BlockDestructionProgress>> progress,
                                          Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(context, progress);
            return;
        }
        Probe.beginBucket(Bucket.FLYWHEEL, Hook.FLYWHEEL);
        try {
            original.call(context, progress);
        } finally {
            Probe.endBucket();
        }
    }
}
```

- [ ] **Step 3: Register both**

```json
  "client": [
    "BlockEntityRenderDispatcherMixin",
    "EntityRenderDispatcherMixin",
    "ParticleEngineMixin",
    "BoundTickingBlockEntityMixin",
    "LevelMixin",
    "LevelLightEngineMixin",
    "flywheel.FlywheelRenderDispatcherMixin"
  ],
```

- [ ] **Step 4: Sample Flywheel visuals once per second in `ProfilerController.java`**

Add the import `io.github.edwardg117.renderable.hooks.compat.FlywheelCompat`. Add a field after `private int pendingStallMs;`:

```java
    private long lastVisualSampleSecond = -1;
```

In `start(int seconds)`, immediately before `session = new Session(...)`, add:

```java
        lastVisualSampleSecond = -1;
```

Replace `onFrameStarted` with:

```java
    @Override
    public void onFrameStarted(Session s) {
        long second = s.elapsedNanos() / 1_000_000_000L;
        if (second != lastVisualSampleSecond) {
            lastVisualSampleSecond = second;
            sampleFlywheel(s);
        }

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

    /** Spec §4.2: Flywheel visual counts, sampled about once per second. Runs at frame start, outside Flywheel's own frame work. */
    private static void sampleFlywheel(Session s) {
        if (!FlywheelCompat.PRESENT) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        FlywheelCompat.Counts counts = FlywheelCompat.sample(mc.level);
        if (counts != null) {
            s.recordVisualCounts(counts.blockEntities(), counts.entities(), counts.effects(), counts.byClass());
        }
    }
```

- [ ] **Step 5: Extend `EnvironmentCollector.java`**

Add the imports `io.github.edwardg117.renderable.hooks.compat.FlywheelCompat` and `io.github.edwardg117.renderable.hooks.compat.IrisCompat`. Replace the four `modVersion` lines with:

```java
        m.put("sodium", modVersion("sodium"));
        m.put("iris", modVersion("iris"));
        m.put("shaderPack", safe(IrisCompat::packName));
        m.put("flywheel", modVersion("flywheel"));
        m.put("flywheelBackend", safe(FlywheelCompat::backendName));
        m.put("distanthorizons", modVersion("distanthorizons"));
```

- [ ] **Step 6: Compile, test, prove the vanilla light mixin applies, then try the Flywheel one**

```bash
./gradlew compileJava --no-daemon && ./gradlew test --no-daemon
```

Expected 57 tests. Export launch without `-PpackMods`: `javap -p -cp run/.mixin.out/class net.minecraft.world.level.lighting.LevelLightEngine | grep 'renderable\$'` shows `renderable$timeLightUpdates`; the log shows **no** attempt to apply `FlywheelRenderDispatcherMixin` (the plugin gated it: grep the log for `FlywheelRenderDispatcherMixin`, expect nothing). If Task 1's `-PpackMods` launch worked, repeat the export launch with `-PpackMods`; `RenderDispatcherImpl` is only instantiated for a level, so at the title screen just confirm the log has no error mentioning `FlywheelRenderDispatcherMixin`; its application is verified in-game at the end of phase 3. Stop the client, delete `run/.mixin.out`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable src/main/resources/renderable.mixins.json
git commit -m "feat(hooks): light and Flywheel buckets, once-per-second visual counts, shader pack and backend in the environment"
```

---

### Task 8: Report writers: Particles and Flywheel sections, schema 2

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/JsonReportWriter.java`
- Modify: `src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java` (append)
- Modify: `src/test/java/io/github/edwardg117/renderable/report/JsonReportWriterTest.java` (edit and append)

Spec §6.1 items 3, 7 and 9 and §6.2. The particle table has the spec's columns (avg live count instead of peak and shadow). The Flywheel section reports the bucket, the averaged visual counts and a per-class table, or says precisely why there is nothing: Flywheel absent, no visualization ran (backend off or no level), or the hook did not apply. The JSON `flywheel` object matches spec §6.2's `visualCounts` shape, every ranked row gains `callsPerFrame`, and `session.unbalancedFrames` is added; the schema number becomes 2.

- [ ] **Step 1: Write the failing tests**

Append to `TextReportWriterTest` inside the class:

```java
    static Environment phase2Environment() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("minecraft", "1.21.1");
        m.put("flywheel", "1.0.6");
        m.put("flywheelBackend", "flywheel:instancing");
        return Environment.of(m, List.of("BLOCK_ENTITY_RENDER", "ENTITY_RENDER", "PARTICLE_RENDER", "PARTICLE_TICK",
                "BLOCK_ENTITY_TICK", "ENTITY_TICK", "LIGHT", "FLYWHEEL"));
    }

    @Test
    void phase2Sections() {
        String s = TextReportWriter.write(TestSessions.phase2(), phase2Environment(), "0.2.0");
        assertTrue(s.contains("  Time split (avg ms/frame): tick 1.00 [particle tick 0.10, be tick 0.40, entity tick 0.20] | block entity 7.40 | entity 0.80 | particle render 0.30 | flywheel 1.20 | light 0.30 | other render 2.10 | swap/wait 6.10\n"), s);
        assertTrue(s.contains("  Renderable overhead: about 0.01 ms over 280 timed calls\n"), s);
        assertFalse(s.contains("Attribution stack reset"), s);

        int at = s.indexOf("Particles (top 20)\n");
        assertTrue(at > 0, s);
        String[] lines = s.substring(at).split("\n", 5);
        assertTrue(lines[1].startsWith("  class"), lines[1]);
        assertTrue(lines[1].endsWith("avg live  verts/f"), lines[1]);
        assertEquals(lines[1].length(), lines[3].length());
        assertTrue(lines[3].startsWith("  FlameParticle"), lines[3]);
        assertTrue(lines[3].contains("300.0") && lines[3].contains("100.0") && lines[3].contains("2.1%") && lines[3].contains("3.0"), lines[3]);

        assertTrue(s.contains("Flywheel\n  bucket 1.20 ms/frame (6.3% of frame time), backend flywheel:instancing\n"
                + "  visuals: 120.0 block entities, 10.0 entities, 0.0 effects (average over 3 samples)\n"), s);
        assertTrue(s.contains("\n  " + TestSessions.PRESS_VISUAL), s);
        int row = s.indexOf(TestSessions.PRESS_VISUAL);
        assertTrue(s.substring(row, s.indexOf('\n', row)).endsWith("120.0"), s);
    }

    @Test
    void flywheelSectionExplainsMissingData() {
        LinkedHashMap<String, String> absent = new LinkedHashMap<>();
        absent.put("flywheelBackend", "absent");
        assertTrue(TextReportWriter.write(r, Environment.of(absent, List.of()), "0.2.0")
                .contains("Flywheel\n  (no data: Flywheel is not installed)\n\n"));

        LinkedHashMap<String, String> off = new LinkedHashMap<>();
        off.put("flywheelBackend", "flywheel:off");
        assertTrue(TextReportWriter.write(r, Environment.of(off, List.of()), "0.2.0")
                .contains("Flywheel\n  (no data: no Flywheel visualization ran; backend flywheel:off)\n\n"));

        LinkedHashMap<String, String> noHook = new LinkedHashMap<>();
        noHook.put("flywheel", "1.0.6");
        noHook.put("flywheelBackend", "flywheel:instancing");
        String s = TextReportWriter.write(TestSessions.sample(), Environment.of(noHook, List.of()), "0.2.0");
        assertTrue(s.contains("Flywheel\n  (no data: no Flywheel visualization ran; backend flywheel:instancing)\n\n"), s);

        assertTrue(TextReportWriter.write(r, env, "0.1.0").contains("Particles (top 20)\n  (no data: hook not active in this build)\n"));
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        assertTrue(TextReportWriter.write(r, Environment.of(m, List.of("PARTICLE_RENDER")), "0.2.0")
                .contains("Particles (top 20)\n  (no particles were rendered)\n"));
    }
```

In `JsonReportWriterTest.schemaModEnvironmentAndSession`, change `assertEquals(1, o.get("schema").getAsInt());` to `assertEquals(2, o.get("schema").getAsInt());` and add after the `topContributorsPerFrame` assertion:

```java
        assertEquals(0, session.get("unbalancedFrames").getAsInt());
```

In `JsonReportWriterTest.rankedSectionsAreComplete`, replace the last line (`assertEquals(0, o.getAsJsonObject("flywheel").getAsJsonArray("visuals").size());`) with:

```java
        JsonObject flywheel = o.getAsJsonObject("flywheel");
        assertEquals(0, flywheel.get("bucketUs").getAsLong());
        assertEquals(0, flywheel.get("samples").getAsInt());
        assertTrue(flywheel.getAsJsonObject("visualCounts").entrySet().isEmpty());
        assertEquals(1.0, press.get("callsPerFrame").getAsDouble(), 1e-9);
```

Append to `JsonReportWriterTest` inside the class:

```java
    @Test
    void phase2Sections() {
        JsonObject o = JsonParser.parseString(JsonReportWriter.write(TestSessions.phase2(), environment(), "0.2.0")).getAsJsonObject();

        JsonObject f10 = o.getAsJsonArray("frames").get(10).getAsJsonObject().getAsJsonObject("bucketsUs");
        assertEquals(300, f10.get("light").getAsLong());
        assertEquals(1_200, f10.get("flywheel").getAsLong());
        assertEquals(400, f10.get("be tick").getAsLong());
        assertEquals(100, f10.get("particle tick").getAsLong());

        JsonObject flame = o.getAsJsonArray("particles").get(0).getAsJsonObject();
        assertEquals("particle:net.minecraft.client.particle.FlameParticle", flame.get("key").getAsString());
        assertEquals("FlameParticle", flame.get("label").getAsString());
        assertEquals(6_000, flame.get("renderUs").getAsLong());
        assertEquals(2_000, flame.get("tickUs").getAsLong());
        assertEquals(60, flame.get("calls").getAsLong());
        assertEquals(40, flame.get("tickCalls").getAsLong());
        assertEquals(3.0, flame.get("callsPerFrame").getAsDouble(), 1e-9);

        JsonObject flywheel = o.getAsJsonObject("flywheel");
        assertEquals(24_000, flywheel.get("bucketUs").getAsLong());
        assertEquals(6.25, flywheel.get("sharePercent").getAsDouble(), 1e-9);
        assertEquals(3, flywheel.get("samples").getAsInt());
        assertEquals(120.0, flywheel.get("avgBlockEntities").getAsDouble(), 1e-9);
        assertEquals(10.0, flywheel.get("avgEntities").getAsDouble(), 1e-9);
        assertEquals(0.0, flywheel.get("avgEffects").getAsDouble(), 1e-9);
        assertEquals(120.0, flywheel.getAsJsonObject("visualCounts").get(TestSessions.PRESS_VISUAL).getAsDouble(), 1e-9);
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.report.*' --no-daemon
```

Expected: assertion failures (schema 1, missing `callsPerFrame`, missing `Flywheel` text).

- [ ] **Step 3: Modify `TextReportWriter.java`**

Add imports `io.github.edwardg117.renderable.core.Bucket`, `io.github.edwardg117.renderable.core.FlywheelStats`, `java.util.Map`. Replace the overhead line

```java
        sb.append(String.format(Locale.ROOT, "  Renderable overhead: about %.2f ms over %d timed calls\n\n",
                r.overheadEstimateMs(), r.timedCalls()));
```

with

```java
        sb.append(String.format(Locale.ROOT, "  Renderable overhead: about %.2f ms over %d timed calls\n",
                r.overheadEstimateMs(), r.timedCalls()));
        if (r.unbalancedFrames() > 0) {
            sb.append(String.format(Locale.ROOT,
                    "  Attribution stack reset on %d frames (a hook threw past its end call; buckets on those frames are incomplete)\n",
                    r.unbalancedFrames()));
        }
        sb.append('\n');
```

Replace

```java
        appendRanked(sb, "Particles", Ranking.byKey(r, ProfileKey.Category.PARTICLE), frames, total);
```

with

```java
        appendParticles(sb, r, env, frames, total);
```

and replace

```java
        appendRanked(sb, "Flywheel visuals", Ranking.byKey(r, ProfileKey.Category.VISUAL), frames, total);
```

with

```java
        appendFlywheel(sb, r, env, frames, total);
```

Add these methods after `appendRanked`:

```java
    /** Spec §6.1 item 7. Particles have no peak or shadow of interest; the average live count replaces them. */
    static void appendParticles(StringBuilder sb, SessionResult r, Environment env, int frames, long total) {
        sb.append("Particles (top ").append(TOP).append(")\n");
        List<RankedEntry> entries = Ranking.byKey(r, ProfileKey.Category.PARTICLE);
        if (entries.isEmpty()) {
            sb.append(env.hooksApplied().contains("PARTICLE_RENDER")
                    ? "  (no particles were rendered)\n\n"
                    : "  (no data: hook not active in this build)\n\n");
            return;
        }
        Table t = new Table(
                new String[]{"class", "render us/f", "tick us/f", "share", "avg live", "verts/f"},
                new boolean[]{false, true, true, true, true, true});
        for (RankedEntry e : entries.subList(0, Math.min(TOP, entries.size()))) {
            t.row(e.label(),
                    Fmt.us(e.renderUsPerFrame(frames)),
                    Fmt.us(e.tickUsPerFrame(frames)),
                    Fmt.pct(e.sharePercent(total)),
                    Fmt.us(e.callsPerFrame(frames)),
                    Fmt.us(e.verticesPerFrame(frames)));
        }
        sb.append(t.render("  ")).append('\n');
    }

    /** Spec §6.1 item 9 and §4.4: the Flywheel bucket plus visual counts, or the exact reason there are none. */
    static void appendFlywheel(StringBuilder sb, SessionResult r, Environment env, int frames, long total) {
        sb.append("Flywheel\n");
        String backend = env.entries().getOrDefault("flywheelBackend", "absent");
        if ("absent".equals(backend)) {
            sb.append("  (no data: Flywheel is not installed)\n\n");
            return;
        }
        FlywheelStats fw = r.flywheel();
        if (fw.samples() == 0) {
            sb.append("  (no data: no Flywheel visualization ran; backend ").append(backend).append(")\n\n");
            return;
        }
        long bucketNanos = 0;
        for (FrameRecord f : r.frames()) bucketNanos += f.bucket(Bucket.FLYWHEEL);
        if (bucketNanos == 0 && !env.hooksApplied().contains("FLYWHEEL")) {
            sb.append("  (hook did not apply for flywheel ").append(env.entries().getOrDefault("flywheel", "?"))
                    .append("; the bucket is missing but visual counts were still sampled)\n");
        }
        sb.append(String.format(Locale.ROOT, "  bucket %s ms/frame (%s of frame time), backend %s\n",
                Fmt.ms2(frames == 0 ? 0 : bucketNanos / frames),
                Fmt.pct(total == 0 ? 0 : 100.0 * bucketNanos / total), backend));
        sb.append(String.format(Locale.ROOT, "  visuals: %.1f block entities, %.1f entities, %.1f effects (average over %d samples)\n",
                fw.avgBlockEntities(), fw.avgEntities(), fw.avgEffects(), fw.samples()));
        Table t = new Table(new String[]{"visual class", "avg count"}, new boolean[]{false, true});
        fw.avgByClass().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP)
                .forEach(e -> t.row(e.getKey(), String.format(Locale.ROOT, "%.1f", e.getValue())));
        sb.append(t.render("  ")).append('\n');
    }
```

- [ ] **Step 4: Modify `JsonReportWriter.java`**

Change `public static final int SCHEMA = 1;` to `public static final int SCHEMA = 2;`. Add the import `io.github.edwardg117.renderable.core.FlywheelStats`.

After `session.addProperty("timedCalls", r.timedCalls());` add:

```java
        session.addProperty("unbalancedFrames", r.unbalancedFrames());
```

Replace the flywheel block

```java
        JsonObject flywheel = new JsonObject();
        long flywheelNanos = 0;
        for (FrameRecord f : r.frames()) flywheelNanos += f.bucket(Bucket.FLYWHEEL);
        flywheel.addProperty("bucketUs", flywheelNanos / 1000);
        flywheel.add("visuals", ranked(Ranking.byKey(r, ProfileKey.Category.VISUAL)));
        root.add("flywheel", flywheel);
```

with

```java
        JsonObject flywheel = new JsonObject();
        long flywheelNanos = 0;
        for (FrameRecord f : r.frames()) flywheelNanos += f.bucket(Bucket.FLYWHEEL);
        flywheel.addProperty("bucketUs", flywheelNanos / 1000);
        flywheel.addProperty("sharePercent", st.totalNanos() == 0 ? 0.0 : 100.0 * flywheelNanos / st.totalNanos());
        FlywheelStats fw = r.flywheel();
        flywheel.addProperty("samples", fw.samples());
        flywheel.addProperty("avgBlockEntities", fw.avgBlockEntities());
        flywheel.addProperty("avgEntities", fw.avgEntities());
        flywheel.addProperty("avgEffects", fw.avgEffects());
        JsonObject visualCounts = new JsonObject();
        fw.avgByClass().forEach(visualCounts::addProperty);
        flywheel.add("visualCounts", visualCounts);
        root.add("flywheel", flywheel);
```

Change every `ranked(...)` call to pass the frame count, and the helper to add `callsPerFrame`:

```java
        root.add("blockEntities", category(r, ProfileKey.Category.BLOCK_ENTITY, "byType", "byPosition"));
        root.add("entities", category(r, ProfileKey.Category.ENTITY, "byType", "individuals"));
        root.add("particles", ranked(Ranking.byKey(r, ProfileKey.Category.PARTICLE), r.frameCount()));
        root.add("blocks", ranked(Ranking.byKey(r, ProfileKey.Category.BLOCK), r.frameCount()));
        root.add("sections", ranked(Ranking.byKey(r, ProfileKey.Category.SECTION), r.frameCount()));
```

```java
    private static JsonObject category(SessionResult r, ProfileKey.Category c, String typeName, String keyName) {
        JsonObject o = new JsonObject();
        o.add(typeName, ranked(Ranking.byType(r, c), r.frameCount()));
        o.add(keyName, ranked(Ranking.byKey(r, c), r.frameCount()));
        return o;
    }

    private static JsonArray ranked(List<RankedEntry> entries, int frames) {
        JsonArray arr = new JsonArray();
        for (RankedEntry e : entries) {
            JsonObject o = new JsonObject();
            o.addProperty("key", e.key() == null ? e.typeId() : e.key().serialize());
            o.addProperty("label", e.label());
            o.addProperty("typeId", e.typeId());
            o.addProperty("members", e.members());
            o.addProperty("calls", e.calls());
            o.addProperty("tickCalls", e.tickCalls());
            o.addProperty("callsPerFrame", e.callsPerFrame(frames));
            o.addProperty("renderUs", e.renderNanos() / 1000);
            o.addProperty("tickUs", e.tickNanos() / 1000);
            o.addProperty("shadowUs", e.shadowNanos() / 1000);
            o.addProperty("peakFrameUs", e.maxFrameNanos() / 1000);
            o.addProperty("vertices", e.vertices());
            arr.add(o);
        }
        return arr;
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`; TextReportWriterTest 7, JsonReportWriterTest 4, total 60.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/report src/test/java/io/github/edwardg117/renderable/report
git commit -m "feat(report): particles and Flywheel sections, calls per frame, unbalanced frames; JSON schema 2"
```

---

### Task 9: Results screen: Particles and Flywheel tabs, two tab rows, tick and render hierarchy

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Tab.java` (replace)
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java` (replace)
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/ResultsScreen.java`

Spec §5.3: Particles and Flywheel tabs. Six tabs no longer fit one row beside the Sort button at the plan's 427 px target, so tabs are laid out four per row and the summary, header and list move down one row. The Frames tab's split section now shows tick and render as parents with their buckets indented, matching the report's bracket notation. Spec §4.4: the Block Entities tab says where Flywheel-drawn machines went when the Flywheel bucket is non-zero.

- [ ] **Step 1: Replace `Tab.java`**

```java
package io.github.edwardg117.renderable.ui.screen;

/** Tabs of the results screen and the column layout each one uses. Four per row on the screen. */
public enum Tab {
    FRAMES("Frames", Columns.METRIC_VALUE),
    HITCHES("Hitches", Columns.HITCH),
    BLOCK_ENTITIES("Block Entities", Columns.RANKED),
    ENTITIES("Entities", Columns.RANKED),
    PARTICLES("Particles", Columns.PARTICLE),
    FLYWHEEL("Flywheel", Columns.FLYWHEEL);

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

    /** True for tabs whose rows are ranked entries the Sort button can reorder. */
    public boolean sortable() {
        return this == BLOCK_ENTITIES || this == ENTITIES || this == PARTICLES;
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
        static final Columns PARTICLE = new Columns(
                new String[]{"class", "render us/f", "tick us/f", "share", "avg live", "verts/f"},
                new int[]{5, 1, 1, 1, 1, 1},
                new boolean[]{false, true, true, true, true, true});
        static final Columns FLYWHEEL = new Columns(
                new String[]{"visual class", "avg count", "share"},
                new int[]{6, 1, 1},
                new boolean[]{false, true, true});

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

- [ ] **Step 2: Replace `Rows.java`**

```java
package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.EntityKey;
import io.github.edwardg117.renderable.core.FlywheelStats;
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
import java.util.Map;
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
            case PARTICLES -> particles(r, sort);
            case FLYWHEEL -> flywheel(r);
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
        if (r.unbalancedFrames() > 0) {
            rows.add(Row.plain("Attribution stack reset", r.unbalancedFrames() + " frames (a hook threw past its end call)"));
        }
        rows.add(Row.plain("", ""));

        rows.add(Row.heading("Average split per frame"));
        rows.add(Row.plain("tick", Fmt.ms2(st.avgTickNanos()) + " ms"));
        for (Bucket b : Bucket.VALUES) {
            if (!b.isRender() && st.avgBucket(b) > 0) rows.add(Row.plain("  " + b.label(), Fmt.ms2(st.avgBucket(b)) + " ms"));
        }
        rows.add(Row.plain("  other tick", Fmt.ms2(st.avgOtherTickNanos()) + " ms"));
        rows.add(Row.plain("render", Fmt.ms2(st.avgRenderNanos()) + " ms"));
        for (Bucket b : Bucket.VALUES) {
            if (b.isRender() && st.avgBucket(b) > 0) rows.add(Row.plain("  " + b.label(), Fmt.ms2(st.avgBucket(b)) + " ms"));
        }
        rows.add(Row.plain("  other render", Fmt.ms2(st.avgOtherRenderNanos()) + " ms"));
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
        if (category == ProfileKey.Category.BLOCK_ENTITY && flywheelBucketNanos(r) > 0) {
            rows.add(Row.heading("Machines drawn by Flywheel instancing are not timed here; see the Flywheel tab"));
        }
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

    /** Spec §5.3 Particles tab: one row per particle class. */
    static List<Row> particles(SessionResult r, Ranking.Sort sort) {
        int frames = r.frameCount();
        long total = r.stats().totalNanos();
        List<Row> rows = new ArrayList<>();
        for (RankedEntry e : Ranking.sort(Ranking.byKey(r, ProfileKey.Category.PARTICLE), sort)) {
            rows.add(new Row(new String[]{
                    e.label(),
                    Fmt.us(e.renderUsPerFrame(frames)),
                    Fmt.us(e.tickUsPerFrame(frames)),
                    Fmt.pct(e.sharePercent(total)),
                    Fmt.us(e.callsPerFrame(frames)),
                    Fmt.us(e.verticesPerFrame(frames))},
                    e.key(), e.typeId(), false, false, 0, null, -1));
        }
        if (rows.isEmpty()) rows.add(Row.plain("(no particles rendered)"));
        return rows;
    }

    /** Spec §5.3 Flywheel tab: the bucket, then the averaged visual counts by class. */
    static List<Row> flywheel(SessionResult r) {
        int frames = r.frameCount();
        long total = r.stats().totalNanos();
        long bucket = flywheelBucketNanos(r);
        FlywheelStats fw = r.flywheel();
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(new String[]{"Flywheel bucket",
                String.format(Locale.ROOT, "%.1f us/f", frames == 0 ? 0 : bucket / 1000.0 / frames),
                Fmt.pct(total == 0 ? 0 : 100.0 * bucket / total)},
                null, null, true, false, 0, null, -1));
        if (fw.samples() == 0) {
            rows.add(Row.plain("(no Flywheel visualization ran)", "", ""));
            return rows;
        }
        rows.add(new Row(new String[]{"Visuals, average over " + fw.samples() + " samples",
                String.format(Locale.ROOT, "%.1f", fw.total()), ""},
                null, null, true, false, 0, null, -1));
        fw.avgByClass().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> rows.add(new Row(new String[]{e.getKey(), String.format(Locale.ROOT, "%.1f", e.getValue()), ""},
                        null, null, false, false, 1, null, -1)));
        return rows;
    }

    static long flywheelBucketNanos(SessionResult r) {
        long sum = 0;
        for (FrameRecord f : r.frames()) sum += f.bucket(Bucket.FLYWHEEL);
        return sum;
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

- [ ] **Step 3: Modify `ResultsScreen.java`**

Replace the layout constants at the top of the class with:

```java
    private static final int TAB_Y = 4;
    private static final int TABS_PER_ROW = 4;
    private static final int TAB_ROW_HEIGHT = 22;
    private static final int SUMMARY_Y = 52;
    private static final int HEADER_Y = 74;
    private static final int LIST_Y = 86;
    private static final int BOTTOM_BAR = 32;
    private static final int ROW_HEIGHT = 12;
```

In `init()`, replace the tab loop (from `int tabPitch = ...` through the closing brace of the `for (Tab t : Tab.values())` loop) with:

```java
        int tabPitch = Math.min(88, (width - 122) / TABS_PER_ROW);
        int tabWidth = tabPitch - 4;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int x = 4 + (i % TABS_PER_ROW) * tabPitch;
            int y = TAB_Y + (i / TABS_PER_ROW) * TAB_ROW_HEIGHT;
            Button b = Button.builder(Component.literal(t.title()), btn -> selectTab(t)).bounds(x, y, tabWidth, 20).build();
            b.active = t != tab;
            addRenderableWidget(b);
        }
```

In `updateButtons()`, replace `sortButton.visible = tab == Tab.BLOCK_ENTITIES || tab == Tab.ENTITIES;` with:

```java
        sortButton.visible = tab.sortable();
```

Nothing else in the file changes; the Sort button stays on the first row at `width - 4 - 110`.

- [ ] **Step 4: Compile, test, launch to the title screen**

```bash
./gradlew compileJava --no-daemon && ./gradlew test --no-daemon
```

Expected 60 tests. Launch the dev client to the title screen; no errors. The screen itself is exercised in the in-game checks at the end of phase 3.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/ui
git commit -m "feat(ui): Particles and Flywheel tabs, two tab rows, tick and render hierarchy on the Frames tab"
```

---

### Task 10: Version 0.2.0, README, mixin export check

**Files:**
- Modify: `gradle.properties`
- Modify: `README.md`

- [ ] **Step 1: Bump the version**

In `gradle.properties` change `mod_version=0.1.0` to `mod_version=0.2.0`.

- [ ] **Step 2: Update `README.md`**

Replace the "Reading a report" list with:

```markdown
- **Frames** gives the frame-time distribution. Trust p95 and p99 over the average.
- **Time split** shows where the average frame went: client tick (with its block entity, entity and
  particle tick shares in brackets), block entities, entities, particles, Flywheel, light updates,
  other render work, and swap/wait (vsync, the GPU, the driver). A big swap/wait with a low GPU
  load is not a rendering problem in the world.
- **Hitches** lists the frames that spiked, each with its split and the things that were most
  expensive in exactly that frame. GC pauses are shown per frame.
- **Block entities** and **entities** are ranked by microseconds per frame; expand a type to see
  each position. The **shadow** column is the share of a renderer's time spent in Iris's shadow
  pass, which draws everything a second time.
- **Particles** are ranked by class with their average live count.
- **Flywheel** shows the instancing bucket and how many visuals of each class were alive.
```

Replace the "Limits" paragraph with:

```markdown
Create machines rendered through Flywheel instancing never reach the vanilla renderer, so they
cannot be timed one by one; the Flywheel tab shows the instancing bucket and visual counts by
class instead. Sodium's chunk meshing and uploads arrive in phase 3. Each hook is a single
branch when the mod is not sampling; while sampling, the report states its own overhead.
```

- [ ] **Step 3: Build, run the whole suite, prove every phase 2 mixin applies**

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 60 tests, `build/libs/renderable-0.2.0.jar`. Then the export launch (`JAVA_TOOL_OPTIONS="-Dmixin.debug.export=true" ./gradlew runClient --no-daemon`, and again with `-PpackMods` if Task 1's launch worked) to the title screen and:

```bash
for c in net/minecraft/client/particle/ParticleEngine net/minecraft/world/level/Level net/minecraft/world/level/lighting/LevelLightEngine; do echo "== $c"; javap -p -cp run/.mixin.out/class ${c//\//.} | grep -c 'renderable\$'; done
grep -E "Renderable .* loaded|MixinApplyError|InvalidInjectionException|Critical injection" run/logs/latest.log
```

Expected: counts 2, 1, 1; `Renderable 0.2.0 loaded`; no mixin errors. Stop the client, delete `run/.mixin.out`.

- [ ] **Step 4: Commit**

```bash
git add gradle.properties README.md
git commit -m "docs: README for phase 2; version 0.2.0"
```

The `v0.2.0` tag is applied after the combined in-game checks at the end of phase 3, on this commit.

---

## In-game checks deferred to the end of phase 3

Run in the user's real pack instance with the phase 3 jar, plus the dev client where noted. Record results in `docs/acceptance/phase-2-3.md`.

1. **Spec §9 check 3, shadow split.** With an Iris shader pack active, run a 30 s session at the base: the Block Entities and Entities tables' `shadow` column is non-zero for renderers in view, `hooks applied` lists `IRIS_SHADOW`, and the Environment block names the shader pack. Turn shaders off (Iris menu), run again: the column is 0.0% and the same renderers still rank first.
2. **Particles.** Stand next to a campfire, a furnace and some torches; run a session. The Particles tab and the text report list the particle classes with `avg live` counts above zero; the time split shows `particle render` and `particle tick`.
3. **Client ticks.** At the base, the `tick us/f` column is non-zero for Create machines (kinetic block entities tick client-side) and for animals; the time split shows `be tick` and `entity tick` inside the brackets.
4. **Flywheel.** At the base, the Flywheel tab shows a non-zero bucket and visual counts by class (`PressVisual`, `MixerVisual`, ...); the Environment block shows `flywheelBackend: flywheel:instancing` (or indirect); the Block Entities tab shows the Flywheel note at the top.
5. **Light.** Place and break a few torches during a session: the time split shows `light` above 0.00 ms.
6. **Stability.** `Attribution stack reset` does not appear in any report; no crash on world load, dimension change or logout while sampling.

## Notes for the orchestrator

- Tasks 2, 3 and 8 are pure JUnit. Tasks 1, 4, 5, 6, 7, 9 and 10 need the dev client for compile and a title-screen launch only; the in-world behaviour is verified at the end of phase 3 together with phase 3's checks.
- Every task's final step is a commit. Subagents stage and report; the orchestrator commits after warning the user to touch the YubiKey.
- If Task 1's `-PpackMods` launch fails, do not spend more than one fix attempt on it; record the failure and carry on. The pinned jars are still on the compile classpath, which is what the third-party mixins need.
- Reviewer checklist per task: the design rules in `CLAUDE.md` (one `ACTIVE` read when idle, no allocation on the sampling hot path, `core` free of Minecraft imports, third-party mixins gated and `require = 0`), thread safety of anything a mixin can reach off the render thread, and `try`/`finally` around every `original.call`.
