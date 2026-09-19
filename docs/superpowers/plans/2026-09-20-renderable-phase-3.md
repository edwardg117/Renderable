# Renderable Phase 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Attribute Sodium's chunk mesh work to blocks and sections, time chunk uploads as a per-frame bucket, count vertices per key, and add the Blocks and Sections tabs, so a "fly into unloaded terrain" hitch is explained by upload time and the sections being rebuilt.

**Architecture:** Mesh work happens on Sodium's worker threads, so it gets its own thread-safe store: `MeshStats` in `core` holds one `MeshAccumulator` (two `LongAdder`s) per `BlockKey` or `SectionKey`, written from any thread and snapshotted at `finish()`. It is not per-frame and never feeds `top`. Chunk upload runs on the render thread and is a bucket-only timing like light. Vertex counting hooks the private `BufferBuilder.beginVertex()` with a `@ModifyReturnValue`, which passes the primitive straight through with no boxing or callback object, and increments the innermost keyed accumulator on the render thread. The three new mixins live in `hooks.mixin.sodium` (gated by the plugin) plus one vanilla mixin.

**Tech Stack:** As phase 2: Java 21, ModDevGradle 2.0.147, NeoForge 21.1.220 with MixinExtras 0.5.0 (`@WrapMethod`, `@WrapOperation`, `@ModifyReturnValue`), JUnit 5, Gson; compile-only Sodium `0.8.13` from the nested jar extracted by phase 2's Task 1.

**Spec:** `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md` §3.4 (mesh accumulators), §3.5, §4.3, §4.5, §5.3, §5.4, §6, §9 check 4, §11. Phase 2 must be complete on `main` first (this plan modifies files phase 2 changes; the test counts below assume phase 2's 60).

**Repository:** `H:\repos\Renderable`, branch `main`. Commits are YubiKey-signed: the orchestrator commits after telling the user to touch the key. Subagents stage and report; they do not commit. In-game checks for phases 2 and 3 run together at the end of this plan.

**Verified facts you can rely on** (read with `javap` from `build/moddev/artifacts/neoforge-21.1.220.jar` and the extracted Sodium 0.8.13 mod jar on 2026-09-19):

- `BufferBuilder` has `private long beginVertex()`, and **both** `addVertex(float, float, float)` and the fast path `addVertex(float, float, float, int, float, float, int, int, float, float, float)` call it once per vertex (offsets 1 and 8); the fast path does not call the three-argument one. Nothing else in the class calls `beginVertex`. Every default `VertexConsumer.addVertex(...)` overload funnels into the three-argument method.
- `com.llamalad7.mixinextras.injector.ModifyReturnValue` exists in MixinExtras 0.5.0; for a primitive return the handler receives and returns the primitive.
- Sodium 0.8.13: `net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask extends ChunkBuilderTask<ChunkBuildOutput>` has `public ChunkBuildOutput execute(ChunkBuildContext, CancellationToken)` (plus a bridge returning `BuilderTaskOutput`). Inside it, `BlockRenderer.renderModel(BakedModel, BlockState, BlockPos, BlockPos)` (offset 338) and `FluidRenderer.render(LevelSlice, BlockState, FluidState, BlockPos, BlockPos, TranslucentGeometryCollector, ChunkBuildBuffers)` (offset 375) are invoked directly. `ChunkBuilderTask` declares `protected final RenderSection render`. `RenderSection` has `getChunkX()`, `getChunkY()`, `getChunkZ()` and `getPosition()` (`SectionPos`). `ChunkBuildContext` does **not** carry the level (fields: `buffers`, `cache`), so the dimension for `SectionKey` comes from the client level at session start.
- `RenderSectionManager` has `public void uploadChunks()` and `public void updateChunks(boolean)`, both called from `SodiumWorldRenderer` on the render thread each frame.
- Mod ids: `sodium`, `iris`, `flywheel`. Sodium's mod jar is on the compile classpath as `build/nested-mod-jars/net.caffeinemc.sodium-neoforge-0.8.13+mc1.21.1-mod.jar` after phase 2's Task 1.
- Phase 1 and 2 already define `Bucket.CHUNK_UPLOAD`, `Hook.SODIUM_MESH`, `Hook.SODIUM_UPLOAD`, `Hook.VERTICES`, `ProfileKey.Category.BLOCK`/`SECTION`, `Accumulator.vertices`, `RankedEntry.verticesPerFrame`, `Probe.beginBucket`/`endBucket`, and the `sodium` gate in `RenderableMixinPlugin`.

**Deviations from the spec recorded in this plan:** (1) vertices are counted at `beginVertex()` rather than by an `@Inject` at the head of `addVertex`, because that is the one call both vertex paths share and `@ModifyReturnValue` allocates nothing; (2) the Blocks and Sections lists use a fixed default order (mesh time, rebuild count) with no sort button, matching phase 1's per-tab sort deviation; (3) the JSON `blocks` and `sections` arrays carry mesh fields (`meshUs`, `msPerSecond`, `usPerCall`, `rebuilds`) instead of the ranked-entry shape, so `schema` becomes 3.

---

## File map

```
gradle.properties                                          Modify: mod_version 0.3.0 (Task 8)
README.md                                                  Modify: Blocks, Sections, vertices (Task 8)
src/main/resources/renderable.mixins.json                  Modify: BufferBuilderMixin, sodium.* mixins
src/main/java/io/github/edwardg117/renderable/
  core/BlockKey.java                                       Create
  core/SectionKey.java                                     Create
  core/MeshAccumulator.java                                Create: LongAdder calls and nanos
  core/MeshStats.java                                      Create: concurrent map of MeshAccumulator, snapshot
  core/MeshEntry.java                                      Create: immutable ranked mesh row with derived rates
  core/Session.java                                        Modify: vertex(), mesh(), snapshot in finish()
  core/SessionResult.java                                  Modify: mesh list
  core/Ranking.java                                        Modify: mesh(result, category, sort)
  hooks/Probe.java                                         Modify: attach(session, dimension), vertex(), mesh entry points
  hooks/KeyFactory.java                                    Modify: block, fluid, section keys
  hooks/mixin/BufferBuilderMixin.java                      Create
  hooks/mixin/sodium/ChunkBuilderMeshingTaskMixin.java     Create (gated)
  hooks/mixin/sodium/RenderSectionManagerMixin.java        Create (gated)
  ProfilerController.java                                  Modify: pass the dimension to attach
  report/TextReportWriter.java                             Modify: Blocks and Sections tables
  report/JsonReportWriter.java                             Modify: blocks/sections mesh shape, schema 3
  ui/screen/Tab.java                                       Modify: BLOCKS, SECTIONS
  ui/screen/Rows.java                                      Modify: blocks/sections rows
  ui/WorldOverlay.java                                     Modify: section labels, dimension check
src/test/java/io/github/edwardg117/renderable/
  core/KeysTest.java                                       Modify: block and section keys
  core/MeshStatsTest.java                                  Create
  core/SessionTest.java                                    Modify: vertex counting
  core/TestSessions.java                                   Modify: phase3() fixture via a shared builder
  report/TextReportWriterTest.java                         Modify: blocks and sections
  report/JsonReportWriterTest.java                         Modify: schema 3, blocks, sections
```

`core` stays free of Minecraft imports (`KeysTest.coreHasNoMinecraftImports`). `MeshStats` uses only `java.util.concurrent`.

---

### Task 1: Core: block and section keys, mesh accumulators, vertex counting

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/BlockKey.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/SectionKey.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/MeshAccumulator.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/MeshStats.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/MeshEntry.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Session.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/SessionResult.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Ranking.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/KeysTest.java` (append)
- Create: `src/test/java/io/github/edwardg117/renderable/core/MeshStatsTest.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/SessionTest.java` (append)

Spec §3.4: mesh accumulators are written from Sodium worker threads, so they are `ConcurrentHashMap` plus `LongAdder`s, are not per-frame and never feed `top`. `MeshEntry` is the immutable row the writers and screen consume, with rates derived against the session duration. `Session.vertex()` adds one to the innermost keyed call's accumulator (spec §4.3); calls outside any keyed timing, or inside a bucket-only timing, are not counted.

- [ ] **Step 1: Write the failing tests**

Append to `KeysTest` inside the class:

```java
    @Test
    void blockAndSectionKeysFormat() {
        BlockKey stone = new BlockKey("minecraft:stone");
        assertEquals(ProfileKey.Category.BLOCK, stone.category());
        assertEquals("minecraft:stone", stone.typeId());
        assertEquals("minecraft:stone", stone.label());
        assertEquals("block:minecraft:stone", stone.serialize());

        SectionKey s = new SectionKey("minecraft:overworld", 12, 4, -3);
        assertEquals(ProfileKey.Category.SECTION, s.category());
        assertEquals("section", s.typeId());
        assertEquals("section 12 4 -3", s.label());
        assertEquals("section@minecraft:overworld:12,4,-3", s.serialize());
        assertEquals(200, s.centerX());
        assertEquals(72, s.centerY());
        assertEquals(-40, s.centerZ());
        assertEquals(new SectionKey("d", 1, 2, 3), new SectionKey("d", 1, 2, 3));
    }
```

`src/test/java/io/github/edwardg117/renderable/core/MeshStatsTest.java`:

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MeshStatsTest {
    static final long MS = 1_000_000L;
    static final BlockKey STONE = new BlockKey("minecraft:stone");
    static final BlockKey SHAFT = new BlockKey("create:shaft");
    static final SectionKey SEC = new SectionKey("minecraft:overworld", 0, 4, 0);

    @Test
    void sameKeyReturnsSameAccumulator() {
        MeshStats m = new MeshStats();
        assertSame(m.get(STONE), m.get(new BlockKey("minecraft:stone")));
        assertTrue(m.snapshot().isEmpty(), "an accumulator with no calls is not reported");
    }

    @Test
    void snapshotSumsCallsAndNanosMostExpensiveFirst() {
        MeshStats m = new MeshStats();
        m.get(STONE).add(3 * MS);
        m.get(STONE).add(5 * MS);
        m.get(SHAFT).add(10 * MS);
        m.get(SEC).add(9 * MS);
        List<MeshEntry> snap = m.snapshot();
        assertEquals(List.of(SHAFT, SEC, STONE), snap.stream().map(MeshEntry::key).toList());
        MeshEntry stone = snap.get(2);
        assertEquals(2, stone.calls());
        assertEquals(8 * MS, stone.nanos());
        assertEquals(4000.0, stone.usPerCall(), 1e-9);
        assertEquals(20.0, stone.msPerSecond(0.4), 1e-9);
        assertEquals(5.0, stone.callsPerSecond(0.4), 1e-9);
        assertEquals(0.0, stone.msPerSecond(0), 1e-9);
    }

    @Test
    void concurrentAddsAreNotLost() throws InterruptedException {
        MeshStats m = new MeshStats();
        MeshAccumulator a = m.get(STONE);
        int threads = 8, perThread = 20_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                for (int i = 0; i < perThread; i++) a.add(3);
                done.countDown();
            });
        }
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();
        MeshEntry e = m.snapshot().get(0);
        assertEquals((long) threads * perThread, e.calls());
        assertEquals(3L * threads * perThread, e.nanos());
    }
}
```

Append to `SessionTest` inside the class:

```java
    @Test
    void verticesCountOnTheInnermostKeyedCall() {
        Session s = session(10_000);
        Accumulator a = s.accumulator(press);
        Accumulator b = s.accumulator(cow);
        at(0); s.onRenderPre(0, 0);
        s.vertex();                                   // outside any call: ignored
        at(1); s.begin(a, Bucket.BLOCK_ENTITY);
        s.vertex(); s.vertex(); s.vertex();
        at(2); s.begin(b, Bucket.ENTITY);
        s.vertex(); s.vertex();
        at(3); s.end();
        s.vertex();
        at(4); s.end();
        at(5); s.begin(null, Bucket.LIGHT);
        s.vertex();                                   // bucket-only timing has no key: ignored
        at(6); s.end();
        at(10); s.onRenderPre(0, 0);
        assertEquals(4, a.vertices);
        assertEquals(2, b.vertices);
        assertTrue(s.finish().mesh().isEmpty(), "mesh is empty for a session with no mesh work");
    }

    @Test
    void meshStatsSurviveIntoTheResult() {
        Session s = session(10_000);
        at(0); s.onRenderPre(0, 0);
        s.mesh().get(new BlockKey("minecraft:stone")).add(4 * MS);
        at(10); s.onRenderPre(0, 0);
        List<MeshEntry> mesh = s.finish().mesh();
        assertEquals(1, mesh.size());
        assertEquals(new BlockKey("minecraft:stone"), mesh.get(0).key());
        assertEquals(4 * MS, mesh.get(0).nanos());
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.*' --no-daemon
```

Expected: compilation failure, `cannot find symbol: class BlockKey` (and `MeshStats`, `MeshEntry`, `vertex`, `mesh`).

- [ ] **Step 3: Write the two keys**

`BlockKey.java`:

```java
package io.github.edwardg117.renderable.core;

/** One block type in Sodium's mesh build (spec §3.4). Fluids are keyed by the block they render as. */
public record BlockKey(String blockId) implements ProfileKey {

    @Override
    public Category category() {
        return Category.BLOCK;
    }

    @Override
    public String typeId() {
        return blockId;
    }

    @Override
    public String label() {
        return blockId;
    }

    @Override
    public String serialize() {
        return "block:" + blockId;
    }
}
```

`SectionKey.java`:

```java
package io.github.edwardg117.renderable.core;

/** One 16x16x16 render section rebuilt by Sodium (spec §3.4), in section coordinates. */
public record SectionKey(String dimension, int x, int y, int z) implements ProfileKey {

    @Override
    public Category category() {
        return Category.SECTION;
    }

    /** All sections roll up into one type row; the label carries the position. */
    @Override
    public String typeId() {
        return "section";
    }

    @Override
    public String label() {
        return "section " + x + " " + y + " " + z;
    }

    @Override
    public String serialize() {
        return "section@" + dimension + ":" + x + "," + y + "," + z;
    }

    public int centerX() {
        return x * 16 + 8;
    }

    public int centerY() {
        return y * 16 + 8;
    }

    public int centerZ() {
        return z * 16 + 8;
    }
}
```

- [ ] **Step 4: Write the mesh store**

`MeshAccumulator.java`:

```java
package io.github.edwardg117.renderable.core;

import java.util.concurrent.atomic.LongAdder;

/** Counters for one mesh key, written from any thread (spec §3.4). Sections count rebuilds as calls. */
public final class MeshAccumulator {
    public final ProfileKey key;
    private final LongAdder calls = new LongAdder();
    private final LongAdder nanos = new LongAdder();

    MeshAccumulator(ProfileKey key) {
        this.key = key;
    }

    /** Records one call that took {@code elapsedNanos}. Allocation-free. */
    public void add(long elapsedNanos) {
        calls.increment();
        nanos.add(elapsedNanos);
    }

    long calls() {
        return calls.sum();
    }

    long nanos() {
        return nanos.sum();
    }
}
```

`MeshStats.java`:

```java
package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe store of mesh accumulators (spec §3.4). Hooks cache the accumulator per Minecraft object. */
public final class MeshStats {
    private final ConcurrentHashMap<ProfileKey, MeshAccumulator> map = new ConcurrentHashMap<>(256);

    /** Returns the accumulator for a key, creating it on first sight. */
    public MeshAccumulator get(ProfileKey key) {
        return map.computeIfAbsent(key, MeshAccumulator::new);
    }

    /** Immutable rows for every key with at least one call, most nanoseconds first, ties by label. */
    public List<MeshEntry> snapshot() {
        List<MeshEntry> out = new ArrayList<>(map.size());
        for (MeshAccumulator a : map.values()) {
            long calls = a.calls();
            if (calls > 0) out.add(new MeshEntry(a.key, calls, a.nanos()));
        }
        out.sort(Comparator.comparingLong(MeshEntry::nanos).reversed().thenComparing(e -> e.key().label()));
        return List.copyOf(out);
    }
}
```

`MeshEntry.java`:

```java
package io.github.edwardg117.renderable.core;

/** One row of the Blocks or Sections table. Rates are derived against the session's sampled seconds. */
public record MeshEntry(ProfileKey key, long calls, long nanos) {

    public double usPerCall() {
        return calls == 0 ? 0 : nanos / 1000.0 / calls;
    }

    public double msPerSecond(double seconds) {
        return seconds <= 0 ? 0 : nanos / 1e6 / seconds;
    }

    public double callsPerSecond(double seconds) {
        return seconds <= 0 ? 0 : calls / seconds;
    }
}
```

- [ ] **Step 5: Modify `Session.java`**

Add a field after `private final ArrayList<Accumulator> touched = ...;`:

```java
    private final MeshStats mesh = new MeshStats();
```

Add after `accumulator(ProfileKey key)`:

```java
    /** Mesh-build accumulators, safe to write from any thread (spec §3.4). */
    public MeshStats mesh() {
        return mesh;
    }

    /** Counts one vertex on the innermost keyed call, if any (spec §4.3). Render thread only. */
    public void vertex() {
        if (depth == 0 || depth > MAX_DEPTH) return;
        Accumulator a = stack[depth - 1];
        if (a != null) a.vertices++;
    }
```

In `finish()`, change the `return new SessionResult(...)` to pass the snapshot as the last argument:

```java
        return new SessionResult(config, wallStartMillis, frameList, accumulators(), stats, threshold, hitches,
                timedCalls, overheadMs, unbalancedFrames, flywheelStats(), mesh.snapshot());
```

- [ ] **Step 6: Modify `SessionResult.java` and `Ranking.java`**

In `SessionResult`, add a last component `List<MeshEntry> mesh` after `FlywheelStats flywheel`.

In `Ranking`, add after the `Sort` enum:

```java
    /** Orderings for the mesh tables. */
    public enum MeshSort { TIME, CALLS }
```

and after `membersOf`:

```java
    /** Mesh rows of one category, ordered by nanoseconds or by calls, descending, ties by label. */
    public static List<MeshEntry> mesh(SessionResult r, ProfileKey.Category category, MeshSort sort) {
        List<MeshEntry> out = new ArrayList<>();
        for (MeshEntry e : r.mesh()) {
            if (e.key().category() == category) out.add(e);
        }
        Comparator<MeshEntry> by = sort == MeshSort.CALLS
                ? Comparator.comparingLong(MeshEntry::calls)
                : Comparator.comparingLong(MeshEntry::nanos);
        out.sort(by.reversed().thenComparing(e -> e.key().label()));
        return out;
    }
```

- [ ] **Step 7: Run the tests to verify they pass**

```bash
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`; KeysTest 8, MeshStatsTest 3, SessionTest 23; 66 total.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/core src/test/java/io/github/edwardg117/renderable/core
git commit -m "feat(core): block and section keys, thread-safe mesh accumulators, vertex counting"
```

---

### Task 2: Phase 3 fixture and ranking of mesh rows

**Files:**
- Modify: `src/test/java/io/github/edwardg117/renderable/core/TestSessions.java`
- Modify: `src/test/java/io/github/edwardg117/renderable/core/RankingTest.java` (append)

`phase3()` is `phase2()` plus vertices on the press and mixer, plus mesh data for three blocks and two sections. Both fixtures share one builder so their numbers cannot drift.

- [ ] **Step 1: Write the failing test**

Append to `RankingTest` inside the class:

```java
    @Test
    void meshRowsRankByTimeOrByCalls() {
        SessionResult p3 = TestSessions.phase3();
        List<MeshEntry> blocks = Ranking.mesh(p3, ProfileKey.Category.BLOCK, Ranking.MeshSort.TIME);
        assertEquals(List.of("minecraft:stone", "create:shaft", "minecraft:water"),
                blocks.stream().map(e -> e.key().label()).toList());
        assertEquals(400, blocks.get(0).calls());
        assertEquals(8 * MS, blocks.get(0).nanos());
        assertEquals(20.0, blocks.get(0).usPerCall(), 1e-9);
        assertEquals(8 / 0.384, blocks.get(0).msPerSecond(p3.stats().durationSeconds()), 1e-9);

        List<MeshEntry> sections = Ranking.mesh(p3, ProfileKey.Category.SECTION, Ranking.MeshSort.CALLS);
        assertEquals(2, sections.size());
        assertEquals(new SectionKey("minecraft:overworld", 0, 4, 0), sections.get(0).key());
        assertEquals(3, sections.get(0).calls());
        assertEquals(9 * MS, sections.get(0).nanos());
        assertEquals(3 / 0.384, sections.get(0).callsPerSecond(p3.stats().durationSeconds()), 1e-9);
        assertTrue(Ranking.mesh(p3, ProfileKey.Category.PARTICLE, Ranking.MeshSort.TIME).isEmpty());

        assertEquals(2000, p3.accumulators().get(PRESS).vertices);
        assertEquals(400, p3.accumulators().get(MIXER).vertices);
        assertTrue(TestSessions.phase2().mesh().isEmpty());
    }
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.RankingTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: method phase3`.

- [ ] **Step 3: Refactor `TestSessions`**

Add these constants after `PRESS_VISUAL`:

```java
    public static final BlockKey STONE = new BlockKey("minecraft:stone");
    public static final BlockKey SHAFT = new BlockKey("create:shaft");
    public static final BlockKey WATER = new BlockKey("minecraft:water");
    public static final SectionKey SECTION_A = new SectionKey("minecraft:overworld", 0, 4, 0);
    public static final SectionKey SECTION_B = new SectionKey("minecraft:overworld", 1, 4, 0);
```

Replace the whole `phase2()` method (Javadoc included) with:

```java
    /** Phase 2 fixture; see {@link #build(boolean)}. */
    public static SessionResult phase2() {
        return build(false);
    }

    /**
     * Phase 3 fixture: {@link #phase2()} plus 100 vertices per press render and 20 per mixer render (2000 and
     * 400 over 20 frames), and mesh data: stone 400 calls 8 ms, shaft 50 calls 5 ms, water 20 calls 1 ms,
     * section (0,4,0) 3 rebuilds 9 ms, section (1,4,0) 1 rebuild 2 ms. Over the 0.384 s session that is
     * stone 20.833 ms/s and 20.0 us/call, section (0,4,0) 7.8125 rebuilds/s and 3.00 ms per rebuild.
     */
    public static SessionResult phase3() {
        return build(true);
    }

    /**
     * The phase 2 skeleton: the {@link #sample()} frames plus particles, ticks, light, Flywheel and visual samples.
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
    private static SessionResult build(boolean phase3) {
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
            now[0] = t; s.begin(press, Bucket.BLOCK_ENTITY);
            if (phase3) for (int v = 0; v < 100; v++) s.vertex();
            t += pressCost; now[0] = t; s.end();
            now[0] = t; s.begin(press2, Bucket.BLOCK_ENTITY); t += 1_500_000;  now[0] = t; s.end();
            now[0] = t; s.begin(mixer, Bucket.BLOCK_ENTITY);
            if (phase3) for (int v = 0; v < 20; v++) s.vertex();
            t += 1 * MS; now[0] = t; s.end();
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

        if (phase3) {
            for (int i = 0; i < 400; i++) s.mesh().get(STONE).add(20_000);
            for (int i = 0; i < 50; i++) s.mesh().get(SHAFT).add(100_000);
            for (int i = 0; i < 20; i++) s.mesh().get(WATER).add(50_000);
            for (int i = 0; i < 3; i++) s.mesh().get(SECTION_A).add(3 * MS);
            s.mesh().get(SECTION_B).add(2 * MS);
        }
        return s.finish();
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`; RankingTest 9; every phase 2 report assertion on `phase2()` still passes; 67 total.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/io/github/edwardg117/renderable/core
git commit -m "test(core): shared fixture builder with a phase 3 variant carrying vertices and mesh data"
```

---

### Task 3: Probe: mesh entry points, vertex counting, dimension at attach

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/Probe.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/KeyFactory.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`

Mesh hooks run on Sodium's worker threads, so their accumulator cache is a `ConcurrentHashMap` keyed by the `Block` or `Fluid` instance (singletons, so identity lookups hit after first sight and allocate nothing). Sections are rebuilt a few times per frame at most, so a `SectionKey` per rebuild is acceptable. The dimension for section keys is captured on the render thread when the session attaches, because Sodium's build context does not carry the level. `vertex()` does the least possible work because it runs for every vertex the game emits while sampling.

- [ ] **Step 1: Add the key factories to `KeyFactory.java`**

Add imports `io.github.edwardg117.renderable.core.BlockKey`, `io.github.edwardg117.renderable.core.SectionKey`, `net.minecraft.core.registries.BuiltInRegistries`, `net.minecraft.world.level.block.Block`, `net.minecraft.world.level.material.Fluid`. Add after `particle(Class<?>)`:

```java
    static BlockKey block(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return new BlockKey(id == null ? block.getClass().getName() : id.toString());
    }

    /** Fluids are reported under the block they render as (water, lava, a mod's fluid block). */
    static BlockKey fluidBlock(Fluid fluid) {
        return block(fluid.defaultFluidState().createLegacyBlock().getBlock());
    }

    static SectionKey section(String dimension, int x, int y, int z) {
        return new SectionKey(dimension, x, y, z);
    }
```

- [ ] **Step 2: Modify `Probe.java`**

Add imports `io.github.edwardg117.renderable.core.MeshAccumulator`, `net.minecraft.world.level.block.Block`, `net.minecraft.world.level.block.state.BlockState`, `net.minecraft.world.level.material.Fluid`, `net.minecraft.world.level.material.FluidState`, `java.util.concurrent.ConcurrentHashMap`.

Add these fields after `BY_CLASS`:

```java
    /** Mesh accumulators by Block or Fluid instance; written from Sodium's worker threads. */
    private static final ConcurrentHashMap<Object, MeshAccumulator> MESH_BY_OBJECT = new ConcurrentHashMap<>(512);
    /** Dimension of the level the session was started in, for section keys built on worker threads. */
    private static volatile String dimension = "unknown";
    private static boolean seenVertices;
```

Replace `attach(Session s)` with:

```java
    /**
     * Attaches a session. Must be called on the render thread: bucket-only hooks and vertex counting compare
     * against it. {@code levelDimension} names the dimension for section keys.
     */
    public static void attach(Session s, String levelDimension) {
        BY_INSTANCE.clear();
        BY_CLASS.clear();
        MESH_BY_OBJECT.clear();
        seenVertices = false;
        dimension = levelDimension;
        renderThread = Thread.currentThread();
        session = s;
        ACTIVE = true;
    }
```

In `detach()`, add `MESH_BY_OBJECT.clear();` after `BY_CLASS.clear();`.

Add these methods after `endBucket()`:

```java
    /**
     * One vertex on the innermost keyed call (spec §4.3). Runs for every vertex the game emits while sampling,
     * so it is one null check, one thread check and one increment. Vanilla mesh workers also emit vertices;
     * those are ignored because they have no keyed call.
     */
    public static void vertex() {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return;
        if (!seenVertices) {
            seenVertices = true;
            SEEN[Hook.VERTICES.ordinal()] = true;
        }
        s.vertex();
    }

    /** Sodium mesh build, any thread: one block model rendered into a section mesh. */
    public static void meshBlock(BlockState state, long elapsedNanos) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.SODIUM_MESH.ordinal()] = true;
        Block block = state.getBlock();
        MeshAccumulator a = MESH_BY_OBJECT.get(block);
        if (a == null) {
            a = s.mesh().get(KeyFactory.block(block));
            MESH_BY_OBJECT.put(block, a);
        }
        a.add(elapsedNanos);
    }

    /** Sodium mesh build, any thread: one fluid rendered into a section mesh, keyed by its block. */
    public static void meshFluid(FluidState fluidState, long elapsedNanos) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.SODIUM_MESH.ordinal()] = true;
        Fluid fluid = fluidState.getType();
        MeshAccumulator a = MESH_BY_OBJECT.get(fluid);
        if (a == null) {
            a = s.mesh().get(KeyFactory.fluidBlock(fluid));
            MESH_BY_OBJECT.put(fluid, a);
        }
        a.add(elapsedNanos);
    }

    /** Sodium mesh build, any thread: one section rebuilt, in section coordinates. */
    public static void meshSection(int sectionX, int sectionY, int sectionZ, long elapsedNanos) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.SODIUM_MESH.ordinal()] = true;
        s.mesh().get(KeyFactory.section(dimension, sectionX, sectionY, sectionZ)).add(elapsedNanos);
    }
```

- [ ] **Step 3: Pass the dimension from `ProfilerController.start`**

Replace `Probe.attach(session);` with:

```java
        Probe.attach(session, mc.level.dimension().location().toString());
```

(`mc.level` is non-null there: the method returns early when it is null.)

- [ ] **Step 4: Compile and test**

```bash
./gradlew compileJava --no-daemon && ./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 67 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable
git commit -m "feat(hooks): mesh and vertex entry points on the probe; section keys carry the session's dimension"
```

---

### Task 4: Vertex counting mixin

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/BufferBuilderMixin.java`
- Modify: `src/main/resources/renderable.mixins.json`

`BufferBuilder.beginVertex()` is private and is the one call both `addVertex` paths share. `@ModifyReturnValue` hands the primitive `long` to the handler and takes it back unboxed, with no callback object, so the idle path is exactly one volatile read per vertex.

- [ ] **Step 1: Write the mixin**

```java
package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.BufferBuilder;
import io.github.edwardg117.renderable.hooks.Probe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Counts vertices for the innermost timed key (spec §4.3). Every {@code addVertex} overload calls the private
 * {@code beginVertex()} exactly once per vertex, and a return-value modifier on a primitive allocates nothing.
 */
@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin {

    @ModifyReturnValue(method = "beginVertex", at = @At("RETURN"))
    private long renderable$countVertex(long original) {
        if (Probe.ACTIVE) Probe.vertex();
        return original;
    }
}
```

- [ ] **Step 2: Register it**

```json
  "client": [
    "BlockEntityRenderDispatcherMixin",
    "EntityRenderDispatcherMixin",
    "ParticleEngineMixin",
    "BoundTickingBlockEntityMixin",
    "LevelMixin",
    "LevelLightEngineMixin",
    "BufferBuilderMixin",
    "flywheel.FlywheelRenderDispatcherMixin"
  ],
```

- [ ] **Step 3: Compile and prove it applies**

`./gradlew compileJava --no-daemon`, then the export launch (`JAVA_TOOL_OPTIONS="-Dmixin.debug.export=true" ./gradlew runClient --no-daemon`) to the title screen (the title screen renders text through `BufferBuilder`, so the class loads) and:

```bash
javap -p -c -cp run/.mixin.out/class com.mojang.blaze3d.vertex.BufferBuilder | grep -nE 'renderable\$|Probe' | head
```

Expected: a `renderable$countVertex` method and, inside `beginVertex`, an `invokestatic ... Probe.vertex` guarded by `getstatic ... Probe.ACTIVE`. No mixin errors in the log. Stop the client, delete `run/.mixin.out`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/hooks/mixin/BufferBuilderMixin.java src/main/resources/renderable.mixins.json
git commit -m "feat(hooks): count vertices per key at BufferBuilder.beginVertex"
```

---

### Task 5: Sodium mesh and upload mixins

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/sodium/ChunkBuilderMeshingTaskMixin.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/sodium/RenderSectionManagerMixin.java`
- Modify: `src/main/resources/renderable.mixins.json`

Both classes live in the `sodium` sub-package so `RenderableMixinPlugin` only applies them when the `sodium` mod is loaded; every injector has `require = 0` and `remap = false` (spec §4.5). The meshing task runs on worker threads and reports through the thread-safe mesh entry points; the section manager's upload and update run on the render thread and are a bucket-only timing.

- [ ] **Step 1: Write `ChunkBuilderMeshingTaskMixin.java`**

```java
package io.github.edwardg117.renderable.hooks.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.edwardg117.renderable.hooks.Probe;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Spec §4.3: times one section rebuild and every block and fluid rendered into it. Runs on Sodium's worker
 * threads; only the thread-safe mesh entry points of Probe are used. Applied only when sodium is loaded.
 */
@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
public abstract class ChunkBuilderMeshingTaskMixin {

    @Shadow(remap = false)
    @Final
    protected RenderSection render;

    @WrapMethod(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            remap = false, require = 0)
    private ChunkBuildOutput renderable$timeSection(ChunkBuildContext context, CancellationToken token,
                                                    Operation<ChunkBuildOutput> original) {
        if (!Probe.ACTIVE) {
            return original.call(context, token);
        }
        long start = System.nanoTime();
        try {
            return original.call(context, token);
        } finally {
            Probe.meshSection(render.getChunkX(), render.getChunkY(), render.getChunkZ(), System.nanoTime() - start);
        }
    }

    @WrapOperation(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;renderModel(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)V"),
            remap = false, require = 0)
    private void renderable$timeBlock(BlockRenderer renderer, BakedModel model, BlockState state, BlockPos pos, BlockPos origin,
                                      Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(renderer, model, state, pos, origin);
            return;
        }
        long start = System.nanoTime();
        try {
            original.call(renderer, model, state, pos, origin);
        } finally {
            Probe.meshBlock(state, System.nanoTime() - start);
        }
    }

    @WrapOperation(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/FluidRenderer;render(Lnet/caffeinemc/mods/sodium/client/world/LevelSlice;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/TranslucentGeometryCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildBuffers;)V"),
            remap = false, require = 0)
    private void renderable$timeFluid(FluidRenderer renderer, LevelSlice slice, BlockState state, FluidState fluid, BlockPos pos,
                                      BlockPos origin, TranslucentGeometryCollector collector, ChunkBuildBuffers buffers,
                                      Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(renderer, slice, state, fluid, pos, origin, collector, buffers);
            return;
        }
        long start = System.nanoTime();
        try {
            original.call(renderer, slice, state, fluid, pos, origin, collector, buffers);
        } finally {
            Probe.meshFluid(fluid, System.nanoTime() - start);
        }
    }
}
```

- [ ] **Step 2: Write `RenderSectionManagerMixin.java`**

```java
package io.github.edwardg117.renderable.hooks.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.hooks.Hook;
import io.github.edwardg117.renderable.hooks.Probe;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;

/** Spec §4.3: chunk upload and section bookkeeping on the render thread, as the CHUNK_UPLOAD bucket. */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class RenderSectionManagerMixin {

    @WrapMethod(method = "uploadChunks", remap = false, require = 0)
    private void renderable$timeUpload(Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call();
            return;
        }
        Probe.beginBucket(Bucket.CHUNK_UPLOAD, Hook.SODIUM_UPLOAD);
        try {
            original.call();
        } finally {
            Probe.endBucket();
        }
    }

    @WrapMethod(method = "updateChunks", remap = false, require = 0)
    private void renderable$timeUpdate(boolean updateImmediately, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(updateImmediately);
            return;
        }
        Probe.beginBucket(Bucket.CHUNK_UPLOAD, Hook.SODIUM_UPLOAD);
        try {
            original.call(updateImmediately);
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
    "BufferBuilderMixin",
    "flywheel.FlywheelRenderDispatcherMixin",
    "sodium.ChunkBuilderMeshingTaskMixin",
    "sodium.RenderSectionManagerMixin"
  ],
```

- [ ] **Step 4: Compile and check the gate**

```bash
./gradlew compileJava --no-daemon && ./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL` (Sodium's classes resolve from the nested jar), 67 tests. Launch the dev client **without** `-PpackMods` to the title screen: the log must not mention either `sodium.*Mixin` (gated out) and must show no errors. If the `-PpackMods` launch works in this checkout, launch with it too: both Sodium mixins target classes that load only with a level, so at the title screen just confirm no `MixinApplyError`; their application is checked in-game at the end. If `@Shadow` of the inherited `render` field is rejected, replace it with an accessor mixin on `ChunkBuilderTask` (`@Accessor("render") RenderSection renderable$render();` in a new `ChunkBuilderTaskAccessor` interface in the same package, registered in the json) and report the change.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/hooks/mixin/sodium src/main/resources/renderable.mixins.json
git commit -m "feat(hooks): Sodium mesh build per block and section, chunk upload bucket"
```

---

### Task 6: Report writers: Blocks and Sections, schema 3

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/JsonReportWriter.java`
- Modify: `src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java`
- Modify: `src/test/java/io/github/edwardg117/renderable/report/JsonReportWriterTest.java`

Spec §6.1 item 8 and §6.2. Blocks are ranked by mesh milliseconds per second, sections by rebuilds; when Sodium's hooks never fired the section says so.

- [ ] **Step 1: Write the failing tests**

In `TextReportWriterTest.rankedTablesAlignAndEmptyCategoriesSaySo`, replace

```java
        assertTrue(s.contains("Blocks (mesh build) (top 20)\n  (no data: hook not active in this build)\n"), s);
```

with

```java
        assertTrue(s.contains("Blocks (mesh build, top 20 by mesh ms/s)\n  (no data: Sodium hooks were not active)\n"), s);
        assertTrue(s.contains("Sections (mesh rebuilds, top 20 by rebuilds)\n  (no data: Sodium hooks were not active)\n"), s);
```

Append to `TextReportWriterTest` inside the class:

```java
    @Test
    void phase3MeshSections() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("sodium", "0.8.13");
        Environment env3 = Environment.of(m, List.of("BLOCK_ENTITY_RENDER", "SODIUM_MESH", "SODIUM_UPLOAD", "VERTICES"));
        String s = TextReportWriter.write(TestSessions.phase3(), env3, "0.3.0");

        int at = s.indexOf("Blocks (mesh build, top 20 by mesh ms/s)\n");
        assertTrue(at > 0, s);
        String[] lines = s.substring(at).split("\n", 7);
        assertEquals("  block            mesh ms/s  us/call  calls", lines[1]);
        assertEquals(lines[1].length(), lines[3].length());
        assertTrue(lines[3].startsWith("  minecraft:stone") && lines[3].endsWith("20.8     20.0    400"), lines[3]);
        assertTrue(lines[4].startsWith("  create:shaft") && lines[4].endsWith("13.0    100.0     50"), lines[4]);
        assertTrue(lines[5].startsWith("  minecraft:water") && lines[5].endsWith("2.6     50.0     20"), lines[5]);

        at = s.indexOf("Sections (mesh rebuilds, top 20 by rebuilds)\n");
        assertTrue(at > 0, s);
        lines = s.substring(at).split("\n", 6);
        assertEquals("  section        rebuilds  rebuilds/s  mesh ms  avg ms/rebuild", lines[1]);
        assertTrue(lines[3].startsWith("  section 0 4 0") && lines[3].endsWith("3         7.8      9.0            3.00"), lines[3]);
        assertTrue(lines[4].startsWith("  section 1 4 0") && lines[4].endsWith("1         2.6      2.0            2.00"), lines[4]);

        int press = s.indexOf("create:mechanical_press @ 100 64 -20");
        String pressRow = s.substring(press, s.indexOf('\n', press));
        assertTrue(pressRow.contains("100.0"), "press renders 2000 vertices over 20 frames: " + pressRow);

        LinkedHashMap<String, String> hookOnly = new LinkedHashMap<>();
        String empty = TextReportWriter.write(TestSessions.phase2(), Environment.of(hookOnly, List.of("SODIUM_MESH")), "0.3.0");
        assertTrue(empty.contains("Blocks (mesh build, top 20 by mesh ms/s)\n  (no data: no sections were rebuilt)\n"), empty);
    }
```

In `JsonReportWriterTest.schemaModEnvironmentAndSession`, change the schema assertion to `assertEquals(3, o.get("schema").getAsInt());`. Append to `JsonReportWriterTest` inside the class:

```java
    @Test
    void phase3MeshSections() {
        JsonObject o = JsonParser.parseString(JsonReportWriter.write(TestSessions.phase3(), environment(), "0.3.0")).getAsJsonObject();

        JsonArray blocks = o.getAsJsonArray("blocks");
        assertEquals(3, blocks.size());
        JsonObject stone = blocks.get(0).getAsJsonObject();
        assertEquals("block:minecraft:stone", stone.get("key").getAsString());
        assertEquals("minecraft:stone", stone.get("label").getAsString());
        assertEquals(400, stone.get("calls").getAsLong());
        assertEquals(8_000, stone.get("meshUs").getAsLong());
        assertEquals(20.0, stone.get("usPerCall").getAsDouble(), 1e-9);
        assertEquals(8 / 0.384, stone.get("msPerSecond").getAsDouble(), 1e-9);
        assertFalse(stone.has("x"));

        JsonArray sections = o.getAsJsonArray("sections");
        assertEquals(2, sections.size());
        JsonObject a = sections.get(0).getAsJsonObject();
        assertEquals("section@minecraft:overworld:0,4,0", a.get("key").getAsString());
        assertEquals("minecraft:overworld", a.get("dimension").getAsString());
        assertEquals(0, a.get("x").getAsInt());
        assertEquals(4, a.get("y").getAsInt());
        assertEquals(0, a.get("z").getAsInt());
        assertEquals(3, a.get("calls").getAsLong());
        assertEquals(9_000, a.get("meshUs").getAsLong());
        assertEquals(3 / 0.384, a.get("callsPerSecond").getAsDouble(), 1e-9);

        JsonObject press = o.getAsJsonObject("blockEntities").getAsJsonArray("byPosition").get(0).getAsJsonObject();
        assertEquals(2000, press.get("vertices").getAsLong());
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.report.*' --no-daemon
```

Expected: assertion failures on the Blocks heading and the schema number.

- [ ] **Step 3: Modify `TextReportWriter.java`**

Add the import `io.github.edwardg117.renderable.core.MeshEntry`. Replace

```java
        appendRanked(sb, "Blocks (mesh build)", Ranking.byKey(r, ProfileKey.Category.BLOCK), frames, total);
        appendRanked(sb, "Sections (mesh rebuilds)", Ranking.byKey(r, ProfileKey.Category.SECTION), frames, total);
```

with

```java
        appendBlocks(sb, r, env);
        appendSections(sb, r, env);
```

Add after `appendFlywheel`:

```java
    /** Spec §6.1 item 8: blocks by mesh time per second of session. */
    static void appendBlocks(StringBuilder sb, SessionResult r, Environment env) {
        sb.append("Blocks (mesh build, top ").append(TOP).append(" by mesh ms/s)\n");
        List<MeshEntry> rows = Ranking.mesh(r, ProfileKey.Category.BLOCK, Ranking.MeshSort.TIME);
        if (rows.isEmpty()) {
            sb.append(meshNoData(env)).append('\n');
            return;
        }
        double seconds = r.stats().durationSeconds();
        Table t = new Table(new String[]{"block", "mesh ms/s", "us/call", "calls"}, new boolean[]{false, true, true, true});
        for (MeshEntry e : rows.subList(0, Math.min(TOP, rows.size()))) {
            t.row(e.key().label(), Fmt.us(e.msPerSecond(seconds)), Fmt.us(e.usPerCall()), Fmt.count(e.calls()));
        }
        sb.append(t.render("  ")).append('\n');
    }

    /** Spec §6.1 item 8: sections by rebuild count. */
    static void appendSections(StringBuilder sb, SessionResult r, Environment env) {
        sb.append("Sections (mesh rebuilds, top ").append(TOP).append(" by rebuilds)\n");
        List<MeshEntry> rows = Ranking.mesh(r, ProfileKey.Category.SECTION, Ranking.MeshSort.CALLS);
        if (rows.isEmpty()) {
            sb.append(meshNoData(env)).append('\n');
            return;
        }
        double seconds = r.stats().durationSeconds();
        Table t = new Table(new String[]{"section", "rebuilds", "rebuilds/s", "mesh ms", "avg ms/rebuild"},
                new boolean[]{false, true, true, true, true});
        for (MeshEntry e : rows.subList(0, Math.min(TOP, rows.size()))) {
            t.row(e.key().label(), Fmt.count(e.calls()), Fmt.us(e.callsPerSecond(seconds)), Fmt.ms(e.nanos()),
                    Fmt.ms2(e.calls() == 0 ? 0 : e.nanos() / e.calls()));
        }
        sb.append(t.render("  ")).append('\n');
    }

    private static String meshNoData(Environment env) {
        return env.hooksApplied().contains("SODIUM_MESH")
                ? "  (no data: no sections were rebuilt)\n"
                : "  (no data: Sodium hooks were not active)\n";
    }
```

- [ ] **Step 4: Modify `JsonReportWriter.java`**

Change `SCHEMA` to `3`. Add the imports `io.github.edwardg117.renderable.core.MeshEntry` and `io.github.edwardg117.renderable.core.SectionKey`. Replace

```java
        root.add("blocks", ranked(Ranking.byKey(r, ProfileKey.Category.BLOCK), r.frameCount()));
        root.add("sections", ranked(Ranking.byKey(r, ProfileKey.Category.SECTION), r.frameCount()));
```

with

```java
        double seconds = st.durationSeconds();
        root.add("blocks", meshRows(Ranking.mesh(r, ProfileKey.Category.BLOCK, Ranking.MeshSort.TIME), seconds));
        root.add("sections", meshRows(Ranking.mesh(r, ProfileKey.Category.SECTION, Ranking.MeshSort.CALLS), seconds));
```

Add after `ranked(...)`:

```java
    /** Spec §6.2 blocks and sections: mesh work is per session, not per frame, so rates are per second. */
    private static JsonArray meshRows(List<MeshEntry> rows, double seconds) {
        JsonArray arr = new JsonArray();
        for (MeshEntry e : rows) {
            JsonObject o = new JsonObject();
            o.addProperty("key", e.key().serialize());
            o.addProperty("label", e.key().label());
            if (e.key() instanceof SectionKey s) {
                o.addProperty("dimension", s.dimension());
                o.addProperty("x", s.x());
                o.addProperty("y", s.y());
                o.addProperty("z", s.z());
            }
            o.addProperty("calls", e.calls());
            o.addProperty("meshUs", e.nanos() / 1000);
            o.addProperty("usPerCall", e.usPerCall());
            o.addProperty("msPerSecond", e.msPerSecond(seconds));
            o.addProperty("callsPerSecond", e.callsPerSecond(seconds));
            arr.add(o);
        }
        return arr;
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`; TextReportWriterTest 8, JsonReportWriterTest 5; 69 total. If a table row's trailing text differs only by spacing, the expected strings above were computed from `Table`'s two-space gutter and right alignment; print the row and compare before changing anything.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/report src/test/java/io/github/edwardg117/renderable/report
git commit -m "feat(report): Blocks and Sections mesh tables; JSON schema 3"
```

---

### Task 7: Results screen Blocks and Sections tabs, section overlay labels

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Tab.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/WorldOverlay.java`

Spec §5.3 Blocks and Sections tabs (eight tabs fill the two rows from phase 2) and §5.4's section labels at section centres. Section rows carry the section centre as their position so Highlight and Teleport work. While in `WorldOverlay`, labels gain a dimension check so a session profiled in one dimension does not leave ghosts in another (a phase 1 review finding).

- [ ] **Step 1: Add the tabs to `Tab.java`**

Add two constants after `FLYWHEEL(...)` (change the `;` after `FLYWHEEL` to `,`):

```java
    BLOCKS("Blocks", Columns.BLOCK),
    SECTIONS("Sections", Columns.SECTION);
```

Add two layouts inside `Columns` after `FLYWHEEL`:

```java
        static final Columns BLOCK = new Columns(
                new String[]{"block", "mesh ms/s", "us/call", "calls"},
                new int[]{5, 1, 1, 1},
                new boolean[]{false, true, true, true});
        static final Columns SECTION = new Columns(
                new String[]{"section", "rebuilds", "rebuilds/s", "mesh ms", "avg ms"},
                new int[]{4, 1, 1, 1, 1},
                new boolean[]{false, true, true, true, true});
```

- [ ] **Step 2: Add the rows to `Rows.java`**

Add the imports `io.github.edwardg117.renderable.core.MeshEntry` and `io.github.edwardg117.renderable.core.SectionKey`. Add two cases to `build`:

```java
            case BLOCKS -> blocks(r);
            case SECTIONS -> sections(r);
```

Add after `flywheel(...)`:

```java
    /** Spec §5.3 Blocks tab: one row per block, by mesh time per second. */
    static List<Row> blocks(SessionResult r) {
        double seconds = r.stats().durationSeconds();
        List<Row> rows = new ArrayList<>();
        for (MeshEntry e : Ranking.mesh(r, ProfileKey.Category.BLOCK, Ranking.MeshSort.TIME)) {
            rows.add(new Row(new String[]{e.key().label(), Fmt.us(e.msPerSecond(seconds)), Fmt.us(e.usPerCall()), Fmt.count(e.calls())},
                    e.key(), e.key().typeId(), false, false, 0, null, -1));
        }
        if (rows.isEmpty()) rows.add(Row.plain("(no data: Sodium mesh hooks did not run)"));
        return rows;
    }

    /** Spec §5.3 Sections tab: one row per rebuilt section, by rebuild count; the row's position is the section centre. */
    static List<Row> sections(SessionResult r) {
        double seconds = r.stats().durationSeconds();
        List<Row> rows = new ArrayList<>();
        for (MeshEntry e : Ranking.mesh(r, ProfileKey.Category.SECTION, Ranking.MeshSort.CALLS)) {
            SectionKey k = (SectionKey) e.key();
            rows.add(new Row(new String[]{k.label(), Fmt.count(e.calls()), Fmt.us(e.callsPerSecond(seconds)), Fmt.ms(e.nanos()),
                    Fmt.ms2(e.calls() == 0 ? 0 : e.nanos() / e.calls())},
                    k, k.typeId(), false, false, 0, new BlockPos(k.centerX(), k.centerY(), k.centerZ()), -1));
        }
        if (rows.isEmpty()) rows.add(Row.plain("(no data: Sodium mesh hooks did not run)"));
        return rows;
    }
```

- [ ] **Step 3: Section labels and a dimension check in `WorldOverlay.java`**

Add the imports `io.github.edwardg117.renderable.core.MeshEntry`, `io.github.edwardg117.renderable.core.SectionKey`, `net.minecraft.resources.ResourceKey`, `net.minecraft.world.level.Level`.

Change the `Label` record to carry the dimension (null means any):

```java
    private record Label(String dimension, double x, double y, double z, int entityId, String text, int color) {
    }
```

Replace `setResult` and `label(...)` with:

```java
    public static void setResult(SessionResult r) {
        int n = RenderableConfig.OVERLAY_LABEL_COUNT.get();
        long sessionTotal = r.stats().totalNanos();
        int frames = r.frameCount();
        double seconds = r.stats().durationSeconds();
        Set<ProfileKey> flagged = r.hitchFlaggedKeys();
        List<Label> out = new ArrayList<>(3 * n);
        for (RankedEntry e : head(Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY), n)) {
            BlockEntityKey k = (BlockEntityKey) e.key();
            out.add(label(e, flagged, frames, sessionTotal, k.dimension(), k.x() + 0.5, k.y() + 1.25, k.z() + 0.5, -1));
        }
        for (RankedEntry e : head(Ranking.byKey(r, ProfileKey.Category.ENTITY), n)) {
            EntityKey k = (EntityKey) e.key();
            out.add(label(e, flagged, frames, sessionTotal, null, 0, 0, 0, k.entityId()));
        }
        for (MeshEntry e : head(Ranking.mesh(r, ProfileKey.Category.SECTION, Ranking.MeshSort.CALLS), n)) {
            SectionKey k = (SectionKey) e.key();
            String text = String.format(java.util.Locale.ROOT, "section %d %d %d: %d rebuilds, %s ms",
                    k.x(), k.y(), k.z(), e.calls(), Fmt.ms(e.nanos()));
            out.add(new Label(k.dimension(), k.centerX() + 0.5, k.centerY() + 0.5, k.centerZ() + 0.5, -1, text, Colors.YELLOW));
        }
        labels = List.copyOf(out);
        enabled = RenderableConfig.OVERLAY_ENABLED.get();
    }

    private static Label label(RankedEntry e, Set<ProfileKey> flagged, int frames, long sessionTotal,
                               String dimension, double x, double y, double z, int entityId) {
        boolean isFlagged = flagged.contains(e.key());
        String text = (isFlagged ? "! " : "") + e.typeId() + " " + Fmt.us(e.usPerFrame(frames)) + " us";
        int color = isFlagged ? Colors.FLAGGED : Colors.heat(e.sharePercent(sessionTotal));
        return new Label(dimension, x, y, z, entityId, text, color);
    }
```

Add two fields after `haveMatrices`:

```java
    private static ResourceKey<Level> lastDimensionKey;
    private static String lastDimension = "";
```

In `drawLabels`, before the `for (Label l : labels)` loop, add:

```java
        ResourceKey<Level> dimKey = mc.level.dimension();
        if (dimKey != lastDimensionKey) {
            lastDimensionKey = dimKey;
            lastDimension = dimKey.location().toString();
        }
```

and as the first statement inside the loop body add:

```java
            if (l.dimension() != null && !l.dimension().equals(lastDimension)) continue;
```

- [ ] **Step 4: Compile, test, launch to the title screen**

```bash
./gradlew compileJava --no-daemon && ./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 69 tests, clean title-screen launch.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/edwardg117/renderable/ui
git commit -m "feat(ui): Blocks and Sections tabs, section overlay labels, overlay dimension check"
```

---

### Task 8: Version 0.3.0, README, final mixin export check

**Files:**
- Modify: `gradle.properties`
- Modify: `README.md`

- [ ] **Step 1: Bump the version**

In `gradle.properties` change `mod_version=0.2.0` to `mod_version=0.3.0`.

- [ ] **Step 2: Update `README.md`**

Append to the "Reading a report" list:

```markdown
- **Blocks** ranks block types by the mesh-build time Sodium's workers spent on them, per second of
  session; **Sections** lists the chunk sections that were rebuilt, with rebuild counts. Rebuild
  storms show up as **chunk upload** time in the hitch splits.
- **verts/f** is the number of vertices a renderer emitted per frame, the proxy for GPU load.
```

Replace the "Limits" paragraph with:

```markdown
Create machines rendered through Flywheel instancing never reach the vanilla renderer, so they
cannot be timed one by one; the Flywheel tab shows the instancing bucket and visual counts by
class instead. Mesh timing needs Sodium; without it the Blocks and Sections tabs say so. Each
hook is a single branch when the mod is not sampling; while sampling, the report states its own
overhead. Timing is CPU-side only; vertex counts stand in for GPU cost.
```

- [ ] **Step 3: Build and prove every phase 3 mixin applies**

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 69 tests, `build/libs/renderable-0.3.0.jar`. Export launch to the title screen without `-PpackMods`: `BufferBuilder` shows `renderable$countVertex`; `Renderable 0.3.0 loaded`; no mixin errors; no `sodium.*Mixin` mentioned. With `-PpackMods` if it works: no errors mentioning `ChunkBuilderMeshingTaskMixin` or `RenderSectionManagerMixin`. Stop the client, delete `run/.mixin.out`.

- [ ] **Step 4: Commit**

```bash
git add gradle.properties README.md
git commit -m "docs: README for phase 3; version 0.3.0"
```

---

### Task 9: Combined in-game checks, acceptance log, tags (human step)

**Files:**
- Create: `docs/acceptance/phase-2-3.md`

The user runs the phase 2 list (in the phase 2 plan) and the phase 3 list below in the real pack instance with `renderable-0.3.0.jar`, plus the dev client without Sodium for the last item. The orchestrator writes the log from the results, has it reviewed, commits it, and applies the tags.

Phase 3 checks:

1. **Spec §9 check 4, chunk upload.** Start a 30 s session, then fly fast into terrain that has never been loaded. Expect: several hitches whose split has `chunk upload` as the largest bucket; the Sections tab and the report's Sections table list the newly built sections with rebuild counts; the overlay shows yellow section labels at their centres for the last session.
2. **Blocks tab.** After the flight, the Blocks tab and table list block types by mesh ms/s, with `minecraft:stone`, `minecraft:grass_block` or the pack's common terrain near the top; fluids appear as `minecraft:water`.
3. **Vertices.** In the Block Entities and Entities tables, `verts/f` is non-zero for renderers in view; Particles show vertices too.
4. **Without Sodium.** In the dev client (no `-PpackMods`), a session's report shows `Blocks (mesh build, top 20 by mesh ms/s)` followed by `(no data: Sodium hooks were not active)`, and no crash from the gated mixins.
5. **Stability.** No `Attribution stack reset` line; no crash when the session ends mid-flight or the level unloads.

Then:

```bash
git add docs/acceptance/phase-2-3.md
git commit -m "docs: phase 2 and 3 acceptance log"
git tag v0.2.0 <phase 2 Task 10 commit>
git tag v0.3.0
```

The user pushes `main` and both tags when ready; the release workflow attaches one jar per tag.

---

## Notes for the orchestrator

- Tasks 1, 2 and 6 are pure JUnit. Tasks 3, 4, 5, 7 and 8 need the dev client for compile and a title-screen launch only.
- Task 5's Sodium mixins can only be proven in the user's pack (or a working `-PpackMods` dev client); the plugin gate and `require = 0` mean a mistake there degrades to empty Blocks and Sections tables, never a crash.
- Reviewer checklist per task: thread safety of everything reachable from a Sodium worker (only `Probe.mesh*` and `MeshStats`), no allocation in `Probe.vertex` or `Session.vertex`, `try`/`finally` around every `original.call`, `core` free of Minecraft imports.
- Phase 2's notes about `-PpackMods` apply unchanged.
