# Renderable Phase 4a Implementation Plan: numbers

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the report printing numbers that are wrong or misleading: GC pauses credited to whichever renderer was open, type peaks taken from the worst member instead of the worst frame, mob vertex counts reading zero under Accelerated Rendering, and an overhead estimate that ignores vertex counting. Ships as `v0.4.0`.

**Architecture:** One rule, defined once in `Session`, decides whether a per-frame measurement could be a GC pause; `closeFrame` applies it to per-key peaks, to a new per-type aggregation, and to each frame's top contributors. The per-type aggregation is a `TypeStats` object that every `Accumulator` of that type holds a reference to, so `Session.end()` costs one field dereference rather than a map lookup. Vertex counting gets its own calibration constant and call counter so the overhead line stops under-reporting. Accelerated Rendering's builder is hooked by a string-targeted, gated, `require = 0` mixin, because the mod is an alpha that is not on any Maven.

**Tech Stack:** Java 21, ModDevGradle 2.0.147, NeoForge 21.1.220 with MixinExtras 0.5.0 (`@ModifyReturnValue`, `@ModifyVariable`), Mixin `@Shadow`/`@Unique`/`@Inject`, JUnit 5, Gson.

**Spec:** `docs/superpowers/specs/2026-09-20-renderable-phase-4-design.md` §P4.A (all of it) and §P4.2. Base spec `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md` §3.4, §3.5, §4.3, §4.5, §6.1, §6.2, §9, §11.

**Repository:** `H:\repos\Renderable`, branch `main`, starting from `1ec32c3` (`v0.3.0`, 75 tests). Commits are YubiKey-signed: the orchestrator commits after telling the user to touch the key. **Subagents stage and report; they do not commit.**

**Ordering:** This plan is the first of phase 4 and assumes nothing else from phase 4 has run. Plans 4b, 4c and 4d assume this one is on `main`.

**Verified facts you can rely on:**

- Read with `javap` from `%APPDATA%\.MCUpdater\instances\Gears-And-Frontiers\mods\accelerated_rendering.jar` on 2026-09-20 (`modId = "acceleratedrendering"`, `version = "1.0.8-1.21.1-alpha"`, `license="MIT"`, issue tracker `github.com/Argon4W/AcceleratedRendering`):

  ```
  public class com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.AcceleratedBufferBuilder
      implements ...builders.IAcceleratedVertexConsumer,com.mojang.blaze3d.vertex.VertexConsumer,java.util.function.LongSupplier {
    private int elementCount;
    private int meshVertexCount;
    private int vertexCount;
    public com.mojang.blaze3d.vertex.VertexConsumer addVertex(com.mojang.blaze3d.vertex.PoseStack$Pose, float, float, float);
    public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float, float, float);
    public void addVertex(float, float, float, int, float, float, int, int, float, float, float);
    public void addClientMesh(java.nio.ByteBuffer, int, int, int, int);
    public void addServerMesh(com.github.argon4w.acceleratedrendering.core.meshes.ServerMesh, int, int, int);
    public int getTotalVertexCount();
    public int getMeshVertexCount();
    public int getVertexCount();
  }
  ```

  The class is **not final**, so it can be mixed into. `javap -c` shows the arithmetic:

  - `addVertex(float, float, float)`: `vertexCount = vertexCount + 1` (offsets 159-164).
  - `addVertex(float, float, float, int, float, float, int, int, float, float, float)`: `vertexCount = vertexCount + 1` (offsets 274-279).
  - `addClientMesh(ByteBuffer, int, int, int, int)`: offset 4 is `iload_2` multiplied by `vertexSize`, and offsets 205-212 are `vertexCount = vertexCount + iload_2`. **The second parameter is the vertex count.**
  - `addServerMesh(ServerMesh, int, int, int)`: branches on `CoreFeature.shouldUploadMeshImmediately()`; one branch does `vertexCount += mesh.size()` (offsets 210-216), the other `meshVertexCount += mesh.size()` (offsets 301-307). Both are covered by the delta of `getTotalVertexCount()`.
  - `getTotalVertexCount()` is `vertexCount + meshVertexCount` (offsets 1-6 of that method).
  - `addVertex(PoseStack$Pose, float, float, float)` writes **neither** counter, so it delegates to `addVertex(float, float, float)` and must not be hooked separately.
  - The class has **no `LocalVariableTable`** (`javap -p -c` prints none), so no injection may capture locals.

- `com.github.argon4w.acceleratedrendering.core.mixins.buffers.BufferBuilderMixin` adds an `AcceleratedBufferBuilder acceleration` field to vanilla `BufferBuilder` and implements `IAcceleratedVertexConsumer.doRender`. Accelerated geometry therefore leaves vanilla `BufferBuilder` **before** `beginVertex()`, so the existing `BufferBuilderMixin` in this repo cannot double-count it.
- Accelerated Rendering is **not resolvable from the Modrinth Maven**: `GET /v2/project/{accelerated-rendering,acceleratedrendering,accelerated_rendering}` all return empty, and a search for "AcceleratedRendering" returns only third-party ports (`accrelatedrendering-refabricated`, `acceleratedrendering-ported`). The dev client must load it from the pack instance jar, which is why this plan adds `-PextraMods`.
- `net.minecraft.world.level.block.entity.BlockEntity` and `net.minecraft.world.entity.Entity` both extend `net.neoforged.neoforge.attachment.AttachmentHolder` in NeoForge 21.1.220. Not used by this plan; recorded because plan 4b mixes into both.
- `TestSessions.sample()` reports one GC of 5 ms, delivered as a cumulative jump at `i == 16` and therefore attributed to **frame 15**. In frame 15 the largest per-key frame total is the press at 2 ms and the largest per-type total is `create:mechanical_press` at 3.5 ms, both below the 4 ms suspicion threshold a 5 ms pause produces. Every existing peak assertion in the suite therefore stands unchanged.
- The existing suite is **75 tests** (`./gradlew test` on `1ec32c3`).

**Deviations from the spec addendum recorded in this plan:** none. Where the addendum's §P4.A0 rule is quoted here it is quoted exactly, including the two-millisecond floor.

---

## File map

```
gradle.properties                                             Modify: mod_version 0.4.0 (Task 8)
build.gradle                                                  Modify: -PextraMods (Task 6)
README.md                                                     Modify (Task 8)
docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md
                                                              Modify: §3.4, §3.5, §4.3, §6.1, §6.2, §9, §11 (Task 8)
src/main/resources/renderable.thirdparty.mixins.json          Modify: acceleratedrendering mixin (Task 6)
src/main/java/io/github/edwardg117/renderable/
  core/TypeStats.java                                         Create: per-frame aggregation per (category, type id)
  core/Accumulator.java                                       Modify: gcFrames, TypeStats reference
  core/TopEntry.java                                          Modify: gc flag
  core/Session.java                                           Modify: GC-suspect rule, touchedTypes, vertexCalls
  core/SessionConfig.java                                     Modify: calibratedVertexNanos
  core/SessionResult.java                                     Modify: vertexCalls
  core/Calibration.java                                       Modify: measureVertexNanos
  core/RankedEntry.java                                       Modify: type peak, gcFrames
  core/Fmt.java                                               Modify: num(), peakMs()
  ProfilerController.java                                     Modify: vertex calibration
  hooks/Hook.java                                             Modify: ACCELERATED_VERTICES
  hooks/Probe.java                                            Modify: acceleratedVertex(), acceleratedVertices()
  hooks/RenderableMixinPlugin.java                            Modify: acceleratedrendering gate
  hooks/mixin/acceleratedrendering/AcceleratedBufferBuilderMixin.java   Create (gated)
  report/TextReportWriter.java                                Modify: gc f column, [gc], peak "-", overhead line
  report/JsonReportWriter.java                                Modify: schema 4
  ui/screen/Tab.java                                          Modify: gc f columns
  ui/screen/Rows.java                                         Modify: gc f cells, [gc], overhead row
src/test/java/io/github/edwardg117/renderable/
  core/SessionTest.java                                       Modify: 4 new tests
  core/RankingTest.java                                       Modify: type peak test
  core/CalibrationTest.java                                   Modify: vertex calibration
  core/FmtTest.java                                           Create
  core/TestSessions.java                                      Modify: SessionConfig arity
  core/KeysTest.java                                          Modify: Accumulator constructor
  report/TextReportWriterTest.java                            Modify: overhead line, SessionResult arity, gc column
  report/JsonReportWriterTest.java                            Modify: schema 4
docs/acceptance/phase-4a.md                                   Create (Task 9)
docs/acceptance/phase-4-idle.md                               Create (Task 9)
```

`core` stays free of Minecraft imports (`KeysTest.coreHasNoMinecraftImports`). `TypeStats` uses no imports at all.

**Expected test totals**, assuming no other phase 4 plan has run: 75 at the start, 78 after Task 1, 79 after Task 2, 82 after Task 3, 84 after Task 4, 86 after Task 5, and 86 at the end.

---

### Task 1: The GC-suspect rule, per-type aggregation, and flagged top contributors

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/TypeStats.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Accumulator.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/TopEntry.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Session.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/SessionTest.java` (append)

Spec addendum §P4.A0 and §P4.A1. The rule, quoted:

> A measured value `v` taken inside a frame that reported a GC pause of `p` is **GC-suspect** when
> `p >= 2 ms` and `v >= p - 1 ms`. Otherwise it is clean.

A stop-the-world pause inflates exactly the calls that were open while it ran, and `System.nanoTime()` runs through it, so a call that contained the pause must measure at least `p`. A value below `p` cannot contain it. `GarbageCollectorMXBean.getCollectionTime()` is documented as approximate and reports whole milliseconds, so the comparison allows one millisecond of slack; the two-millisecond floor keeps that slack from swallowing the pause, and a pause under two milliseconds cannot make a peak misleading in any case.

- [ ] **Step 1: Write the failing tests**

Append inside `SessionTest`:

```java
    @Test
    void gcSuspectFramesDoNotSetTheKeyPeak() {
        // onRenderPre takes the JVM's CUMULATIVE counters, and the delta is credited to the frame it closes.
        Session s = session(10_000);
        Accumulator a = s.accumulator(TestSessions.PRESS);
        at(0);  s.onRenderPre(0, 0);
        at(1);  s.begin(a, Bucket.BLOCK_ENTITY); at(4); s.end();      // 3 ms
        at(16); s.onRenderPre(0, 0);                                   // closes a clean frame: the peak is 3 ms
        at(17); s.begin(a, Bucket.BLOCK_ENTITY); at(57); s.end();      // 40 ms
        at(80); s.onRenderPre(1, 40);                                  // that frame paused for 40 ms: suspect
        at(81); s.begin(a, Bucket.BLOCK_ENTITY); at(83); s.end();      // 2 ms
        at(96); s.onRenderPre(1, 40);                                  // cumulative unchanged, so a clean frame

        assertEquals(3 * MS, a.maxFrameNanos, "the 40 ms frame was GC-suspect and must not set the peak");
        assertEquals(1, a.gcFrames);
    }

    @Test
    void gcSuspicionIsBoundedByTheReportedPause() {
        // p = 10 ms, so the threshold is 9 ms: at 9 ms the frame is suspect and the peak stays at the
        // earlier clean frame's 2 ms; one nanosecond below, the frame is clean and sets the peak itself.
        assertEquals(9 * MS - 1, peakAfterOneFrameOf(9 * MS - 1, 10), "one nanosecond below the threshold is clean");
        assertEquals(2 * MS, peakAfterOneFrameOf(9 * MS, 10), "9 ms against a 10 ms pause is suspect");
        // The floor is inclusive on the suspect side: a 2 ms pause arms the rule, a 1 ms pause does not.
        // Without the first of these, a `<=` typo in the floor comparison would pass every other case here.
        assertEquals(2 * MS, peakAfterOneFrameOf(5 * MS, 2), "a 2 ms pause is at the floor, so it arms the rule");
        assertEquals(5 * MS, peakAfterOneFrameOf(5 * MS, 1), "a 1 ms pause is below the floor");
        assertEquals(5 * MS, peakAfterOneFrameOf(5 * MS, 0), "no pause at all");
    }

    /**
     * Runs one 2 ms frame, then one frame costing {@code costNanos} closed with a {@code gcMillis} pause,
     * and returns the key's peak. The 2 ms first frame is the clean fallback the peak falls back to.
     */
    long peakAfterOneFrameOf(long costNanos, long gcMillis) {
        long[] clock = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> clock[0], 0);
        Accumulator a = s.accumulator(TestSessions.PRESS);
        clock[0] = 0;              s.onRenderPre(0, 0);
        clock[0] = 1 * MS;         s.begin(a, Bucket.BLOCK_ENTITY);
        clock[0] = 3 * MS;         s.end();
        clock[0] = 100 * MS;       s.onRenderPre(0, 0);
        clock[0] = 101 * MS;       s.begin(a, Bucket.BLOCK_ENTITY);
        clock[0] = 101 * MS + costNanos; s.end();
        clock[0] = 300 * MS;       s.onRenderPre(gcMillis == 0 ? 0 : 1, gcMillis);
        return a.maxFrameNanos;
    }

    @Test
    void typePeakIsTheTypesWorstFrameAndTopEntriesCarryTheGcFlag() {
        Session s = session(10_000);
        Accumulator one = s.accumulator(TestSessions.PRESS);
        Accumulator two = s.accumulator(TestSessions.PRESS2);
        at(0);  s.onRenderPre(0, 0);
        at(1);  s.begin(one, Bucket.BLOCK_ENTITY); at(3);  s.end();    // 2 ms
        at(3);  s.begin(two, Bucket.BLOCK_ENTITY); at(6);  s.end();    // 3 ms; the type cost 5 ms this frame
        at(16); s.onRenderPre(0, 0);
        at(17); s.begin(one, Bucket.BLOCK_ENTITY); at(21); s.end();    // 4 ms alone: this key's worst clean frame
        at(32); s.onRenderPre(0, 0);
        at(33); s.begin(one, Bucket.BLOCK_ENTITY); at(40); s.end();    // 7 ms, but the frame paused for 7 ms
        at(48); s.onRenderPre(1, 7);
        SessionResult r = s.finish();

        assertEquals(4 * MS, one.maxFrameNanos, "the 7 ms frame was GC-suspect and did not count");
        assertEquals(1, one.gcFrames);
        assertEquals(3 * MS, two.maxFrameNanos);
        assertEquals(0, two.gcFrames, "press2 was not touched in the GC frame, so it is unaffected");
        assertEquals(5 * MS, one.type().maxFrameNanos, "the type's worst frame, not its worst member");
        assertSame(one.type(), two.type(), "members of a type share one TypeStats");
        assertEquals(1, one.type().gcFrames);

        List<TopEntry> gcFrame = r.frames().get(2).top();
        assertEquals(1, gcFrame.size());
        assertEquals(TestSessions.PRESS, gcFrame.get(0).key());
        assertTrue(gcFrame.get(0).gc(), "7 ms on a frame with a 7 ms pause is GC-suspect");
        assertFalse(r.frames().get(1).top().get(0).gc(), "the second frame had no pause");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.SessionTest' --no-daemon
```

Expected: compilation failure — `cannot find symbol: method gcFrames`, `method type()`, `method gc()`, and `constructor SessionConfig(long,double,long,int,int,int)`.

- [ ] **Step 3: Create `TypeStats.java`**

`src/main/java/io/github/edwardg117/renderable/core/TypeStats.java`:

```java
package io.github.edwardg117.renderable.core;

/**
 * Per-frame aggregation for one (category, type id), so a type row's peak is the type's worst frame rather
 * than its worst member (spec addendum §P4.A2). Written on the render thread only. Every {@link Accumulator}
 * of the type holds a reference to the same instance, so {@link Session#end()} never looks one up.
 */
public final class TypeStats {
    public final String typeId;
    public final ProfileKey.Category category;

    /** Largest clean render-plus-tick nanoseconds this type saw in a single frame (spec addendum §P4.A0). */
    public long maxFrameNanos;
    /** Frames in which this type's total was GC-suspect, and so did not update the peak. */
    public long gcFrames;

    /** Render plus tick nanoseconds in the current frame. Reset by Session at each frame boundary. */
    long frameNanos;
    /** True once this type has been added to the session's touched list for the current frame. */
    boolean inFrame;

    TypeStats(String typeId, ProfileKey.Category category) {
        this.typeId = typeId;
        this.category = category;
    }
}
```

- [ ] **Step 4: Modify `Accumulator.java`**

Add the `type` field and `gcFrames`, and a second constructor. The existing one-argument constructor stays, creating a private `TypeStats`, so `KeysTest.accumulatorStartsEmpty` keeps compiling and a standalone accumulator still behaves.

Replace the field block and constructor (everything from `public final ProfileKey key;` to the end of the constructor) with:

```java
    public final ProfileKey key;
    /** The aggregation shared by every accumulator of this key's type (spec addendum §P4.A2). */
    private final TypeStats type;

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
    /** Largest clean render-plus-tick nanoseconds seen in a single frame (spec addendum §P4.A0). */
    public long maxFrameNanos;
    /** Frames in which this key's total was GC-suspect, and so did not update {@link #maxFrameNanos}. */
    public long gcFrames;
    /** Vertices emitted while this key was innermost (phase 3 fills it). */
    public long vertices;

    /** Render plus tick nanoseconds in the current frame. Reset by Session at each frame boundary. */
    long frameNanos;
    /** True once this accumulator has been added to the session's touched list for the current frame. */
    boolean inFrame;

    /** A standalone accumulator with a type aggregation of its own. Sessions use the two-argument form. */
    public Accumulator(ProfileKey key) {
        this(key, new TypeStats(key.typeId(), key.category()));
    }

    Accumulator(ProfileKey key, TypeStats type) {
        this.key = key;
        this.type = type;
    }

    /** The aggregation this key's type shares. Never null. */
    public TypeStats type() {
        return type;
    }
```

- [ ] **Step 5: Modify `TopEntry.java`**

Replace the whole file:

```java
package io.github.edwardg117.renderable.core;

/**
 * One of a frame's top contributors: the key, its render-plus-tick nanoseconds in that frame, and whether
 * that figure is GC-suspect for the frame it belongs to (spec addendum §P4.A0), meaning most of it may be
 * the collector's pause rather than the code's.
 */
public record TopEntry(ProfileKey key, long nanos, boolean gc) {
}
```

- [ ] **Step 6: Modify `Session.java`**

Add the two constants just above `public static final int MAX_DEPTH = 64;`:

```java
    /** Spec addendum §P4.A0: pauses below this are too small to make a peak misleading, so they are ignored. */
    public static final long GC_MIN_NANOS = 2_000_000L;
    /** Spec addendum §P4.A0: the JVM reports pauses in whole milliseconds, so the comparison allows one. */
    public static final long GC_SLACK_NANOS = 1_000_000L;
```

Add after `private final ArrayList<Accumulator> touched = new ArrayList<>(1024);`:

```java
    private final Map<String, TypeStats> types = new HashMap<>(256);
    private final ArrayList<TypeStats> touchedTypes = new ArrayList<>(256);
```

Replace `accumulator(ProfileKey key)` with:

```java
    /** Returns the accumulator for a key, creating it on first sight. Hooks cache the result per instance. */
    public Accumulator accumulator(ProfileKey key) {
        return accumulators.computeIfAbsent(key, k -> new Accumulator(k, typeStats(k)));
    }

    /**
     * The aggregation shared by every key of one category and type id. Resolved once, when an accumulator is
     * created, so the hot path in {@link #end()} never looks one up (spec addendum §P4.A2).
     */
    private TypeStats typeStats(ProfileKey key) {
        return types.computeIfAbsent(key.category().ordinal() + ":" + key.typeId(),
                k -> new TypeStats(key.typeId(), key.category()));
    }
```

**`end()` is not touched.** A type's frame total is the sum of its members' `frameNanos`, so it is
accumulated at the frame boundary instead — see `closeFrame` below. Putting it in `end()` would compute the
same number on the per-call path, and would do it inside the enclosing call's timing window whenever calls
nest, inflating the outer measurement.

Replace the whole `closeFrame` method with:

```java
    private void closeFrame(long now, int gcCount, long gcMillis) {
        // Spec addendum P4.A0. Long.MAX_VALUE means "nothing in this frame can be suspect".
        long gcNanos = gcMillis * 1_000_000L;
        long suspectAt = gcNanos < GC_MIN_NANOS ? Long.MAX_VALUE : gcNanos - GC_SLACK_NANOS;

        int n = config.topContributorsPerFrame();
        Accumulator[] topAcc = new Accumulator[n];
        int topSize = 0;

        for (int i = 0, size = touched.size(); i < size; i++) {
            Accumulator a = touched.get(i);
            long fn = a.frameNanos;
            if (fn >= suspectAt) {
                a.gcFrames++;
            } else if (fn > a.maxFrameNanos) {
                a.maxFrameNanos = fn;
            }

            // The type's frame total is the sum of its members', gathered here rather than in end() so no
            // work lands inside an enclosing call's timing window.
            TypeStats ts = a.type();
            if (!ts.inFrame) {
                ts.inFrame = true;
                touchedTypes.add(ts);
            }
            ts.frameNanos += fn;

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
            long fn = topAcc[i].frameNanos;
            top.add(new TopEntry(topAcc[i].key, fn, fn >= suspectAt));
        }

        for (int i = 0, size = touchedTypes.size(); i < size; i++) {
            TypeStats ts = touchedTypes.get(i);
            long fn = ts.frameNanos;
            if (fn >= suspectAt) {
                ts.gcFrames++;
            } else if (fn > ts.maxFrameNanos) {
                ts.maxFrameNanos = fn;
            }
            ts.frameNanos = 0;
            ts.inFrame = false;
        }
        touchedTypes.clear();

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

- [ ] **Step 7: Add the sixth `SessionConfig` component so the new tests compile**

Task 3 uses it; adding it here keeps Task 1's tests compilable. In `SessionConfig.java`, add `calibratedVertexNanos` to the record header and the factory, and document it:

```java
/**
 * Immutable settings for one session. Durations are nanoseconds so the core never converts units.
 *
 * @param durationNanos           how long to sample
 * @param hitchMedianMultiplier   spec §3.3, default 2.0
 * @param hitchMinExcessNanos     spec §3.3, default 16 ms
 * @param topContributorsPerFrame keys kept per frame, default 5
 * @param calibratedCallNanos     measured cost of one begin/end pair, for the overhead estimate
 * @param calibratedVertexNanos   measured cost of one vertex-counting call (spec addendum §P4.A5)
 */
public record SessionConfig(long durationNanos,
                            double hitchMedianMultiplier,
                            long hitchMinExcessNanos,
                            int topContributorsPerFrame,
                            long calibratedCallNanos,
                            long calibratedVertexNanos) {
```

and replace `ofSeconds`:

```java
    public static SessionConfig ofSeconds(int seconds, double hitchMedianMultiplier, int hitchMinExcessMs,
                                          int topContributorsPerFrame, long calibratedCallNanos,
                                          long calibratedVertexNanos) {
        return new SessionConfig(seconds * 1_000_000_000L, hitchMedianMultiplier, hitchMinExcessMs * 1_000_000L,
                topContributorsPerFrame, calibratedCallNanos, calibratedVertexNanos);
    }
```

- [ ] **Step 8: Update every existing `SessionConfig` construction**

Eight call sites across four files. Find them all before editing — `ofSeconds` is a factory, so a grep for
`new SessionConfig(` alone misses three of them:

```bash
grep -rn 'new SessionConfig(\|SessionConfig.ofSeconds(' src
```

In `src/test/java/io/github/edwardg117/renderable/core/SessionTest.java`:

- line 17: `return new Session(new SessionConfig(durationMs * MS, 2.0, 16 * MS, 5, 0, 0), clock, 1_700_000_000_000L);`
- line 114: `assertThrows(IllegalArgumentException.class, () -> new SessionConfig(0, 2.0, 0, 5, 0, 0));`
- line 115: `SessionConfig c = SessionConfig.ofSeconds(30, 2.0, 16, 5, 25, 2);` (the test does not assert on the
  calibration values, so the two new arguments are arbitrary)
- line 199: `Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 2, 0, 0), clock, 0);`

In `src/test/java/io/github/edwardg117/renderable/core/TestSessions.java`, both occurrences of
`new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 25)` become `new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 25, 2)`.

In `src/main/java/io/github/edwardg117/renderable/core/Calibration.java`:

```java
        Session s = new Session(new SessionConfig(Long.MAX_VALUE, 2.0, 0, 5, 0, 0), clock, 0);
```

In `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`, the `SessionConfig.ofSeconds(...)` call gains a sixth argument; Task 3 gives it a real value, so for now pass `0`:

```java
        SessionConfig config = SessionConfig.ofSeconds(seconds,
                RenderableConfig.HITCH_MEDIAN_MULTIPLIER.get(),
                RenderableConfig.HITCH_MIN_EXCESS_MS.get(),
                RenderableConfig.TOP_CONTRIBUTORS_PER_FRAME.get(),
                calibratedCallNanos,
                0);
```

- [ ] **Step 9: Run the tests to verify they pass**

```bash
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`, **78 tests** (SessionTest 27, everything else unchanged). If `RankingTest.byTypeRollsPositionsUp` fails, stop: it must still pass here, because `RankedEntry.group` is untouched until Task 2.

- [ ] **Step 10: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/core src/main/java/io/github/edwardg117/renderable/ProfilerController.java src/test/java/io/github/edwardg117/renderable/core
git status --short
```

Report to the orchestrator; do not commit. Commit message for the orchestrator:

```
fix(core): GC-suspect frames no longer set per-key peaks; per-type frame aggregation
```

---

### Task 2: A type row's peak is the type's worst frame

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/RankedEntry.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Ranking.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/RankingTest.java` (modify one test, append one)

Spec addendum §P4.A2. `RankedEntry.group` currently takes `max(member.maxFrameNanos)`. Every member of a group shares one `TypeStats` (Task 1), so the type's own peak and `gcFrames` are read from `members.get(0).type()`.

- [ ] **Step 1: Change the failing assertion and add the new test**

In `RankingTest.byTypeRollsPositionsUp`, replace this line:

```java
        assertEquals(60 * MS, press.maxFrameNanos(), "group peak is the largest member peak");
```

with:

```java
        assertEquals(61_500_000L, press.maxFrameNanos(),
                "group peak is the type's worst frame: 60 ms press + 1.5 ms press2 in frame 10");
        assertEquals(0, press.gcFrames(), "the 5 ms pause in frame 15 is above 3.5 ms of press type time, so it is clean");
```

Append inside `RankingTest`:

```java
    @Test
    void typePeakBeatsTheWorstMemberWhenMembersShareAFrame() {
        long[] clock = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> clock[0], 0);
        Accumulator a = s.accumulator(PRESS);
        Accumulator b = s.accumulator(PRESS2);
        clock[0] = 0;        s.onRenderPre(0, 0);
        clock[0] = 1 * MS;   s.begin(a, Bucket.BLOCK_ENTITY); clock[0] = 3 * MS;  s.end();   // 2 ms
        clock[0] = 3 * MS;   s.begin(b, Bucket.BLOCK_ENTITY); clock[0] = 6 * MS;  s.end();   // 3 ms
        clock[0] = 16 * MS;  s.onRenderPre(0, 0);
        clock[0] = 17 * MS;  s.begin(a, Bucket.BLOCK_ENTITY); clock[0] = 21 * MS; s.end();   // 4 ms alone
        clock[0] = 32 * MS;  s.onRenderPre(0, 0);

        RankedEntry type = Ranking.byType(s.finish(), ProfileKey.Category.BLOCK_ENTITY).get(0);
        assertEquals(2, type.members());
        assertEquals(5 * MS, type.maxFrameNanos(), "frame 0 cost the type 5 ms; no member ever cost more than 4");
        assertEquals(4 * MS, Ranking.membersOf(s.finish(), ProfileKey.Category.BLOCK_ENTITY, "create:mechanical_press")
                .get(0).maxFrameNanos(), "individual rows still report their own worst frame");
    }
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.RankingTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: method gcFrames()`, and once that is added, `byTypeRollsPositionsUp` failing with `expected: 61500000 but was: 60000000`.

- [ ] **Step 3: Modify `RankedEntry.java`**

Add `gcFrames` to the record header after `maxFrameNanos`, and change the two factories. Replace the record header and both factories with:

```java
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
                          long gcFrames,
                          long vertices) {

    public static RankedEntry of(Accumulator a) {
        return new RankedEntry(a.key.label(), a.key, a.key.typeId(), a.key.category(), 1,
                a.calls, a.tickCalls, a.renderNanos, a.tickNanos, a.shadowNanos, a.maxFrameNanos, a.gcFrames,
                a.vertices);
    }

    /**
     * Rolls members up into one type row. The peak and GC-frame count come from the shared
     * {@link TypeStats}, which counted the type's whole frame, not from the members (spec addendum §P4.A2).
     */
    public static RankedEntry group(String typeId, ProfileKey.Category category, List<Accumulator> members) {
        long calls = 0, tickCalls = 0, render = 0, tick = 0, shadow = 0, vertices = 0;
        for (Accumulator a : members) {
            calls += a.calls;
            tickCalls += a.tickCalls;
            render += a.renderNanos;
            tick += a.tickNanos;
            shadow += a.shadowNanos;
            vertices += a.vertices;
        }
        TypeStats type = members.get(0).type();
        return new RankedEntry(typeId, null, typeId, category, members.size(),
                calls, tickCalls, render, tick, shadow, type.maxFrameNanos, type.gcFrames, vertices);
    }
```

`Ranking.byType` only ever calls `group` with a non-empty list (it is built from a `computeIfAbsent`), so `members.get(0)` is safe; no other caller exists.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`, **79 tests**.

- [ ] **Step 5: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/core src/test/java/io/github/edwardg117/renderable/core
git status --short
```

Commit message for the orchestrator:

```
fix(core): a type row's peak is the type's worst frame, not its worst member
```

---

### Task 3: Vertex counting in the overhead estimate, and `Fmt.num`

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Session.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/SessionResult.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Calibration.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Fmt.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java` (`Fmt.num` only)
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java` (`Fmt.num` only)
- Create: `src/test/java/io/github/edwardg117/renderable/core/FmtTest.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/SessionTest.java` (append)
- Test: `src/test/java/io/github/edwardg117/renderable/core/CalibrationTest.java` (append)
- Test: `src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java` (`withFlywheel` arity)

Spec addendum §P4.A5. `overheadEstimateMs` models begin/end pairs only, while `Probe.vertex()` fires once per vertex. The cost is **per call**, not per vertex, because Sodium's batched `push` and Accelerated Rendering's mesh paths add many vertices in one call.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/io/github/edwardg117/renderable/core/FmtTest.java`:

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FmtTest {

    @Test
    void numAndPeakMs() {
        // num is the generic one-decimal formatter: same shape as us(), but for values that are not microseconds.
        assertEquals("4900.0", Fmt.num(4900.0));
        assertEquals("0.0", Fmt.num(0.04));
        assertEquals("1234567.9", Fmt.num(1234567.89));
        assertEquals(Fmt.us(12.34), Fmt.num(12.34), "num and us format identically; only the name differs");

        // A peak of zero means "never measured a clean frame" only when some frame was GC-suspect.
        assertEquals("2.50", Fmt.peakMs(2_500_000L, 0));
        assertEquals("2.50", Fmt.peakMs(2_500_000L, 7));
        assertEquals("0.00", Fmt.peakMs(0, 0));
        assertEquals("-", Fmt.peakMs(0, 1));
    }
}
```

Append inside `SessionTest`:

```java
    @Test
    void overheadCountsVertexCallsAsWellAsTimedCalls() {
        long[] clock = {0};
        // 100 ns per begin/end pair, 7 ns per vertex call.
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 100, 7), () -> clock[0], 0);
        Accumulator a = s.accumulator(TestSessions.PRESS);
        clock[0] = 0; s.onRenderPre(0, 0);
        clock[0] = MS; s.begin(a, Bucket.BLOCK_ENTITY);
        s.vertex();
        s.vertex();
        s.vertices(500);                      // one call, five hundred vertices
        clock[0] = 2 * MS; s.end();
        clock[0] = 16 * MS; s.onRenderPre(0, 0);

        SessionResult r = s.finish();
        assertEquals(1, r.timedCalls());
        assertEquals(3, r.vertexCalls(), "a batched call counts once; the cost is per call");
        assertEquals(502, r.accumulators().get(TestSessions.PRESS).vertices);
        assertEquals((1 * 100 + 3 * 7) / 1_000_000.0, r.overheadEstimateMs(), 1e-12);
    }
```

Append inside `CalibrationTest`:

```java
    @Test
    void vertexCalibrationIsANonNegativeNumberOfNanoseconds() {
        long[] clock = {0};
        // A clock that advances 3 ns per read: each vertex() call reads it zero times, so the loop's
        // elapsed time is the two reads around it, which divided by the sample count floors to zero.
        long fake = Calibration.measureVertexNanos(() -> clock[0] += 3);
        assertTrue(fake >= 0, "never negative: " + fake);

        long real = Calibration.measureVertexNanos(System::nanoTime);
        assertTrue(real >= 0 && real < 10_000, "a vertex count is a field increment, not a microsecond: " + real);
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.*' --no-daemon
```

Expected: compilation failure — `cannot find symbol: class FmtTest` dependencies `Fmt.num`, `Fmt.peakMs`, `SessionResult.vertexCalls`, `Calibration.measureVertexNanos`.

- [ ] **Step 3: Add `Fmt.num` and `Fmt.peakMs`**

In `Fmt.java`, insert after the `us` method:

```java
    /** A plain number with one decimal: counts, rates and anything else that is not microseconds. */
    public static String num(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /**
     * A per-key peak in milliseconds, or {@code "-"} when every frame the key appeared in was GC-suspect
     * and there is therefore no clean peak to report (spec addendum §P4.A1).
     */
    public static String peakMs(long maxFrameNanos, long gcFrames) {
        return maxFrameNanos == 0 && gcFrames > 0 ? "-" : ms2(maxFrameNanos);
    }
```

- [ ] **Step 4: Count vertex calls in `Session` and carry them into the result**

In `Session.java`, add after `private long timedCalls;`:

```java
    private long vertexCalls;
```

Add after the `timedCalls()` accessor:

```java
    /** Number of vertex-counting calls, for the overhead estimate (spec addendum §P4.A5). */
    public long vertexCalls() {
        return vertexCalls;
    }
```

Replace `vertex()` and `vertices(int)` with:

```java
    /**
     * Counts one vertex on the innermost open call if it has a key (spec §4.3). Render thread only. A keyless
     * inner timing (bucket-only work) hides the outer key, and vertices emitted deeper than {@link #MAX_DEPTH}
     * are dropped rather than credited to an ancestor, matching {@link #end()}. The call is counted for the
     * overhead estimate whether or not it lands on a key, because it cost the same either way.
     */
    public void vertex() {
        vertexCalls++;
        if (depth == 0 || depth > MAX_DEPTH) return;
        Accumulator a = stack[depth - 1];
        if (a != null) a.vertices++;
    }

    /** Counts {@code n} vertices on the innermost open call if it has a key; same rules as {@link #vertex()}. */
    public void vertices(int n) {
        vertexCalls++;
        if (depth == 0 || depth > MAX_DEPTH) return;
        Accumulator a = stack[depth - 1];
        if (a != null) a.vertices += n;
    }
```

Replace the `finish()` body's last two statements with:

```java
        double overheadMs = (timedCalls * config.calibratedCallNanos()
                + vertexCalls * config.calibratedVertexNanos()) / 1_000_000.0;
        return new SessionResult(config, wallStartMillis, frameList, accumulators(), stats, threshold, hitches,
                timedCalls, vertexCalls, overheadMs, unbalancedFrames, flywheelStats(), mesh.snapshot());
```

- [ ] **Step 5: Add `vertexCalls` to `SessionResult`**

In `SessionResult.java`, add `long vertexCalls,` immediately after `long timedCalls,` in the record header. Nothing else in the file changes.

- [ ] **Step 6: Fix and extend `Calibration`**

First, a pre-existing bug the phase 4a Task 1 review turned up: `measureCallNanos` never calls
`onRenderPre`, so `frameStartNanos` stays `-1` and the whole `if (frameStartNanos >= 0)` block in `end()`
— the touched-list bookkeeping and the bucket add — is skipped for all 12,000 calibration pairs. The
constant therefore under-states what `end()` really costs, and every overhead figure the mod has ever
printed is low by that amount. Add one line to `measureCallNanos`, immediately after the `accumulator(...)`
call:

```java
        s.onRenderPre(0, 0);   // calibrate the path a real session runs, not the frameless shortcut
```

The session's duration is `Long.MAX_VALUE`, so opening a frame changes nothing else; the throwaway
`touched` and `buckets` writes are harmless.

Then add `measureVertexNanos` after `measureCallNanos`:

```java
    /**
     * Spec addendum §P4.A5: what one {@link Session#vertex()} costs. Measured inside one open call, because
     * that is the shape the hook has in a real frame: the loop body is the branch and the increment.
     */
    public static long measureVertexNanos(LongSupplier clock) {
        Session s = new Session(new SessionConfig(Long.MAX_VALUE, 2.0, 0, 5, 0, 0), clock, 0);
        Accumulator a = s.accumulator(new EntityKey("renderable:calibration", 0, ""));
        s.onRenderPre(0, 0);
        s.begin(a, Bucket.ENTITY);
        for (int i = 0; i < WARMUP; i++) s.vertex();
        long start = clock.getAsLong();
        for (int i = 0; i < SAMPLES; i++) s.vertex();
        long elapsed = clock.getAsLong() - start;
        s.end();
        return Math.max(0, elapsed / SAMPLES);
    }
```

- [ ] **Step 7: Calibrate the vertex cost in `ProfilerController`**

Replace the field `private long calibratedCallNanos = -1;` with:

```java
    private long calibratedCallNanos = -1;
    private long calibratedVertexNanos = -1;
```

In `start(int seconds)`, replace the calibration block and the config construction with:

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

- [ ] **Step 8: Move the non-microsecond `Fmt.us` uses to `Fmt.num`**

Output is byte-identical; the change is to make the intent legible. In `report/TextReportWriter.java`:

- in `appendRanked`, `Fmt.us(e.verticesPerFrame(frames))` becomes `Fmt.num(e.verticesPerFrame(frames))`;
- in `appendParticles`, `Fmt.us(e.callsPerFrame(frames))` becomes `Fmt.num(...)` and `Fmt.us(e.verticesPerFrame(frames))` becomes `Fmt.num(...)`;
- in `appendBlocks`, `Fmt.us(e.msPerSecond(seconds))` becomes `Fmt.num(...)` (the `Fmt.us(e.usPerCall())` beside it stays: that one really is microseconds);
- in `appendSections`, `Fmt.us(e.callsPerSecond(seconds))` becomes `Fmt.num(...)`.

In `ui/screen/Rows.java`:

- in `particles`, `Fmt.us(e.callsPerFrame(frames))` and `Fmt.us(e.verticesPerFrame(frames))` become `Fmt.num(...)`;
- in `flywheel`, `Fmt.us(fw.total())` and `Fmt.us(e.getValue())` become `Fmt.num(...)` (the `Fmt.us(bucket / 1000.0 / frames)` above them stays);
- in `blocks`, `Fmt.us(e.msPerSecond(seconds))` becomes `Fmt.num(...)`;
- in `sections`, `Fmt.us(e.callsPerSecond(seconds))` becomes `Fmt.num(...)`;
- in `cells`, `Fmt.us(e.verticesPerFrame(frames))` becomes `Fmt.num(...)`.

- [ ] **Step 9: Update `TextReportWriterTest.withFlywheel` for the new component**

`SessionResult` gained a component, so the hand-built copy needs it:

```java
    /** Rebuilds a SessionResult with a different unbalancedFrames and flywheel, everything else from base. */
    static SessionResult withFlywheel(SessionResult base, FlywheelStats fw, int unbalanced) {
        return new SessionResult(base.config(), base.wallStartMillis(), base.frames(), base.accumulators(),
                base.stats(), base.hitchThresholdNanos(), base.hitches(), base.timedCalls(), base.vertexCalls(),
                base.overheadEstimateMs(), unbalanced, fw, base.mesh());
    }
```

- [ ] **Step 10: Run the tests to verify they pass**

```bash
./gradlew test --no-daemon
```

Expected: two failures remain, both in `TextReportWriterTest`, because the overhead line's text has not changed yet — `framesSection` and `phase2Sections` (or whichever test asserts `Renderable overhead: about 0.01 ms over 280 timed calls`). Everything else is green at **82 tests**. Those two are fixed in Task 4; if any *other* test fails, stop and report.

- [ ] **Step 11: Stage and report**

```bash
git add src/main/java src/test/java
git status --short
```

Commit message for the orchestrator:

```
fix(core): the overhead estimate covers vertex-counting calls; add Fmt.num and Fmt.peakMs
```

---

### Task 4: The text report shows GC-suspect values and the vertex-call count

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`
- Test: `src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java`

Spec addendum §P4.A1 and §P4.A5.

- [ ] **Step 1: Update the two existing assertions and write the new tests**

In `TextReportWriterTest.framesSection`, replace:

```java
        assertTrue(s.contains("  Renderable overhead: about 0.00 ms over 120 timed calls\n"), s);
```

with:

```java
        assertTrue(s.contains("  Renderable overhead: about 0.00 ms over 120 timed calls and 0 vertex calls\n"), s);
```

In the test that asserts the phase 2 overhead (around line 101), replace:

```java
        assertTrue(s.contains("  Renderable overhead: about 0.01 ms over 280 timed calls\n"), s);
```

with:

```java
        assertTrue(s.contains("  Renderable overhead: about 0.01 ms over 280 timed calls and 0 vertex calls\n"), s);
```

Append inside `TextReportWriterTest`:

```java
    /** A two-frame session where the second frame's cost is entirely a 40 ms GC pause. */
    static SessionResult gcSession() {
        long[] clock = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> clock[0], TestSessions.WALL_START);
        Accumulator press = s.accumulator(TestSessions.PRESS);
        Accumulator cow = s.accumulator(TestSessions.COW);
        clock[0] = 0;          s.onRenderPre(0, 0);
        clock[0] = 1 * MS;     s.begin(press, Bucket.BLOCK_ENTITY); clock[0] = 3 * MS;  s.end();
        clock[0] = 3 * MS;     s.begin(cow, Bucket.ENTITY);         clock[0] = 4 * MS;  s.end();
        clock[0] = 16 * MS;    s.onRenderPre(0, 0);
        clock[0] = 17 * MS;    s.begin(press, Bucket.BLOCK_ENTITY); clock[0] = 57 * MS; s.end();
        clock[0] = 100 * MS;   s.onRenderPre(1, 40);
        return s.finish();
    }

    static final long MS = 1_000_000L;

    @Test
    void gcSuspectPeaksAndContributorsAreMarked() {
        String s = TextReportWriter.write(gcSession(), env, "0.4.0");

        assertTrue(s.contains("  [gc] marks a contributor at least as large as that frame's GC pause: most of it may be the pause.\n"), s);
        assertTrue(s.contains("\n      40.00 ms  create:mechanical_press @ 100 64 -20  [gc]\n"), s);
        assertFalse(s.contains("minecraft:cow #7  [gc]"), "1 ms is nowhere near a 40 ms pause: " + s);

        int at = s.indexOf("Block entities by position (top 20)\n");
        assertTrue(at > 0, s);
        String[] lines = s.substring(at).split("\n", 5);
        assertTrue(lines[1].contains("peak ms") && lines[1].contains("gc f"), lines[1]);
        assertEquals(lines[1].length(), lines[3].length(), "the gc column keeps the table aligned");
        assertTrue(lines[3].startsWith("  create:mechanical_press"), lines[3]);
        assertTrue(lines[3].contains("2.00"), "the clean 2 ms frame is the peak, not the 40 ms GC frame: " + lines[3]);

        // The gc column is right-aligned, so its value's last character sits under the header's last one.
        int gcColumnEnd = lines[1].indexOf("gc f") + "gc f".length() - 1;
        assertTrue(gcColumnEnd > 3, lines[1]);
        assertEquals('1', lines[3].charAt(gcColumnEnd), "one GC-suspect frame, right-aligned: " + lines[3]);
    }

    @Test
    void aPeakThatWasOnlyEverGcSuspectPrintsADash() {
        long[] clock = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> clock[0], TestSessions.WALL_START);
        Accumulator press = s.accumulator(TestSessions.PRESS);
        clock[0] = 0;        s.onRenderPre(0, 0);
        clock[0] = 1 * MS;   s.begin(press, Bucket.BLOCK_ENTITY); clock[0] = 41 * MS; s.end();
        clock[0] = 100 * MS; s.onRenderPre(1, 40);

        String out = TextReportWriter.write(s.finish(), env, "0.4.0");
        int at = out.indexOf("Block entities by position (top 20)\n");
        String row = out.substring(at).split("\n", 5)[3];
        assertTrue(row.contains(" - "), "the only frame was GC-suspect, so there is no clean peak: " + row);
    }
```

Add these imports to the test file if they are not already there:

```java
import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.core.SessionConfig;
```

- [ ] **Step 2: Run them to verify they fail**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.report.TextReportWriterTest' --no-daemon
```

Expected: four failures — the two overhead lines and the two new tests.

- [ ] **Step 3: Modify `TextReportWriter.java`**

Replace the overhead line in `write`:

```java
        sb.append(String.format(Locale.ROOT,
                "  Renderable overhead: about %.2f ms over %s timed calls and %s vertex calls\n",
                r.overheadEstimateMs(), Fmt.count(r.timedCalls()), Fmt.count(r.vertexCalls())));
```

Add the legend constant beside `MESH_NOTE`:

```java
    /** Printed under the Hitches heading so the [gc] marks on contributor lines explain themselves. */
    static final String GC_NOTE = "  [gc] marks a contributor at least as large as that frame's GC pause: most of it may be the pause.";
```

Replace the hitch heading and contributor loop:

```java
        sb.append("Hitches (worst ").append(HITCHES).append(")\n");
        if (r.hitches().isEmpty()) {
            sb.append("  none\n");
        } else {
            sb.append(GC_NOTE).append('\n');
        }
        int shown = 0;
        for (FrameRecord h : r.hitches()) {
            if (shown == HITCHES) break;
            shown++;
            sb.append(String.format(Locale.ROOT, "  #%d  frame %d @ %ss  %s ms  |  %s  |  gc %d ms\n",
                    shown, h.index(), Fmt.secs(h.startNanos()), Fmt.ms(h.totalNanos()), Splits.frame(h), h.gcMillis()));
            for (TopEntry t : h.top()) {
                sb.append("      ").append(Fmt.ms2(t.nanos())).append(" ms  ").append(t.key().label())
                        .append(t.gc() ? "  [gc]" : "").append('\n');
            }
        }
        sb.append('\n');
```

Replace `appendRanked`'s table construction and row loop:

```java
        Table t = new Table(
                new String[]{"name", "render us/f", "tick us/f", "share", "peak ms", "gc f", "shadow", "verts/f", "calls"},
                new boolean[]{false, true, true, true, true, true, true, true, true});
        for (RankedEntry e : entries.subList(0, Math.min(TOP, entries.size()))) {
            t.row(e.label(),
                    Fmt.us(e.renderUsPerFrame(frames)),
                    Fmt.us(e.tickUsPerFrame(frames)),
                    Fmt.pct(e.sharePercent(total)),
                    Fmt.peakMs(e.maxFrameNanos(), e.gcFrames()),
                    Fmt.count(e.gcFrames()),
                    Fmt.pct(e.shadowPercent()),
                    Fmt.num(e.verticesPerFrame(frames)),
                    Fmt.count(e.calls()));
        }
```

Replace `appendParticles`'s table construction and row loop:

```java
        Table t = new Table(
                new String[]{"class", "render us/f", "tick us/f", "share", "avg live", "verts/f", "gc f"},
                new boolean[]{false, true, true, true, true, true, true});
        for (RankedEntry e : entries.subList(0, Math.min(TOP, entries.size()))) {
            t.row(e.label(),
                    Fmt.us(e.renderUsPerFrame(frames)),
                    Fmt.us(e.tickUsPerFrame(frames)),
                    Fmt.pct(e.sharePercent(total)),
                    Fmt.num(e.callsPerFrame(frames)),
                    Fmt.num(e.verticesPerFrame(frames)),
                    Fmt.count(e.gcFrames()));
        }
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`, **84 tests**. If `rankedTablesAlignAndEmptyCategoriesSaySo` fails on a line-length comparison, the `Table` widths are being computed from a row with the wrong number of cells — check that every `t.row(...)` in `appendRanked` passes nine values and `appendParticles` seven.

- [ ] **Step 5: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/report src/test/java/io/github/edwardg117/renderable/report
git status --short
```

Commit message for the orchestrator:

```
fix(report): mark GC-suspect peaks and contributors; count vertex calls in the overhead line
```

---

### Task 5: JSON schema 4

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/report/JsonReportWriter.java`
- Test: `src/test/java/io/github/edwardg117/renderable/report/JsonReportWriterTest.java`

Spec addendum §P4.A1 and §P4.A5. The diff from schema 3: every ranked row gains `gcFrames`, every `frames[].top` entry gains `gc`, and `session` gains `vertexCalls`.

- [ ] **Step 1: Update the existing assertion and write the new tests**

In `JsonReportWriterTest.schemaModEnvironmentAndSession`, replace:

```java
        assertEquals(3, o.get("schema").getAsInt());
```

with:

```java
        assertEquals(4, o.get("schema").getAsInt());
        assertEquals(0, session.get("vertexCalls").getAsLong());
```

(The `session` local is declared a few lines below the schema assertion; move the new line under it.)

Append inside `JsonReportWriterTest`:

```java
    @Test
    void rankedRowsCarryGcFramesAndTopEntriesCarryTheGcFlag() {
        JsonObject o = JsonParser.parseString(
                JsonReportWriter.write(TextReportWriterTest.gcSession(), environment(), "0.4.0")).getAsJsonObject();

        JsonObject press = o.getAsJsonObject("blockEntities").getAsJsonArray("byPosition").get(0).getAsJsonObject();
        assertEquals("create:mechanical_press@minecraft:overworld:100,64,-20", press.get("key").getAsString());
        assertEquals(1, press.get("gcFrames").getAsLong());
        assertEquals(2_000, press.get("peakFrameUs").getAsLong(), "the clean 2 ms frame, not the 40 ms GC frame");

        JsonObject cow = o.getAsJsonObject("entities").getAsJsonArray("individuals").get(0).getAsJsonObject();
        assertEquals(0, cow.get("gcFrames").getAsLong());

        JsonArray top0 = o.getAsJsonArray("frames").get(0).getAsJsonObject().getAsJsonArray("top");
        assertFalse(top0.get(0).getAsJsonObject().get("gc").getAsBoolean(), "frame 0 had no pause");
        JsonArray top1 = o.getAsJsonArray("frames").get(1).getAsJsonObject().getAsJsonArray("top");
        assertTrue(top1.get(0).getAsJsonObject().get("gc").getAsBoolean(), "40 ms on a frame with a 40 ms pause");
    }

    @Test
    void vertexCallsAreReportedForASessionThatCountedThem() {
        JsonObject o = JsonParser.parseString(
                JsonReportWriter.write(TestSessions.phase3(), environment(), "0.4.0")).getAsJsonObject();
        // phase3 counts 100 vertices on the press and 20 on the mixer, one call each, over 20 frames.
        assertEquals(2400, o.getAsJsonObject("session").get("vertexCalls").getAsLong());
        assertEquals(4, o.get("schema").getAsInt());
    }
```

- [ ] **Step 2: Run them to verify they fail**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.report.JsonReportWriterTest' --no-daemon
```

Expected: `expected: <4> but was: <3>` and `NullPointerException` on the missing `gcFrames`, `gc` and `vertexCalls` members.

- [ ] **Step 3: Modify `JsonReportWriter.java`**

Change the schema constant:

```java
    public static final int SCHEMA = 4;
```

In `write`, add after `session.addProperty("timedCalls", r.timedCalls());`:

```java
        session.addProperty("vertexCalls", r.vertexCalls());
```

In the frames loop, replace the `top` block with:

```java
            JsonArray top = new JsonArray();
            for (TopEntry t : f.top()) {
                JsonObject te = new JsonObject();
                te.addProperty("key", t.key().serialize());
                te.addProperty("us", t.nanos() / 1000);
                te.addProperty("gc", t.gc());
                top.add(te);
            }
            o.add("top", top);
```

In `ranked`, add after `o.addProperty("peakFrameUs", e.maxFrameNanos() / 1000);`:

```java
            o.addProperty("gcFrames", e.gcFrames());
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`, **86 tests**.

- [ ] **Step 5: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/report src/test/java/io/github/edwardg117/renderable/report
git status --short
```

Commit message for the orchestrator:

```
feat(report): JSON schema 4 with gcFrames, per-contributor gc flags and vertexCalls
```

---

### Task 6: The Accelerated Rendering vertex hook

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/Hook.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/Probe.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/RenderableMixinPlugin.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/acceleratedrendering/AcceleratedBufferBuilderMixin.java`
- Modify: `src/main/resources/renderable.thirdparty.mixins.json`

Spec addendum §P4.A3. There is nothing to unit-test here: the target is not on the compile classpath and the hook's effect is a field increment inside a mixin. The proof is the exported class in Step 6 and the in-pack check in Task 9.

- [ ] **Step 1: Add `-PextraMods` to the dev client**

Accelerated Rendering is not on the Modrinth Maven, so the dev client has to load it from a local file. In `build.gradle`, replace the `installPackMods` registration with:

```groovy
var installPackMods = tasks.register('installPackMods', InstallPackMods) {
    description = 'Installs the pack mods into run/mods when -PpackMods is set; empties run/mods otherwise.'
    if (project.hasProperty('packMods')) {
        modFiles.from(configurations.packModRuntime)
    }
    // -PextraMods="C:\path\one.jar;C:\path\two.jar" adds jars that are on no Maven, such as the pack's
    // Accelerated Rendering build, beside the pinned pack mods. Semicolon-separated; Windows paths are fine
    // because a path may contain a colon but never a semicolon.
    if (project.hasProperty('extraMods')) {
        modFiles.from(project.property('extraMods').toString().split(';')
                .findAll { !it.trim().isEmpty() }
                .collect { project.file(it.trim()) })
    }
    modsDir.set(layout.projectDirectory.dir('run/mods'))
}
```

- [ ] **Step 2: Add the hook name**

In `Hook.java`, add `ACCELERATED_VERTICES` after `VERTICES`:

```java
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

- [ ] **Step 3: Add the `Probe` entry points**

In `Probe.java`, add beside `private static boolean seenVertices;`:

```java
    private static boolean seenAcceleratedVertices;
```

In `attach(Session, String)`, add beside `seenVertices = false;`:

```java
        seenAcceleratedVertices = false;
```

Add after `vertices(int n)`:

```java
    /**
     * One vertex written through Accelerated Rendering's own buffer builder (spec addendum §P4.A3). That
     * builder replaces vanilla's for the renderers the mod takes over, so those vertices never reach
     * {@code BufferBuilder.beginVertex} and there is no double counting. Render thread only.
     */
    public static void acceleratedVertex() {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return;
        if (!seenAcceleratedVertices) {
            seenAcceleratedVertices = true;
            SEEN[Hook.ACCELERATED_VERTICES.ordinal()] = true;
        }
        s.vertex();
    }

    /** {@code n} vertices from one Accelerated Rendering mesh upload. Render thread only. */
    public static void acceleratedVertices(int n) {
        Session s = session;
        if (s == null || n <= 0 || Thread.currentThread() != renderThread) return;
        if (!seenAcceleratedVertices) {
            seenAcceleratedVertices = true;
            SEEN[Hook.ACCELERATED_VERTICES.ordinal()] = true;
        }
        s.vertices(n);
    }
```

- [ ] **Step 4: Gate the new sub-package**

In `RenderableMixinPlugin.java`, replace the map:

```java
    private static final Map<String, String> REQUIRED_MOD = Map.of(
            "sodium", "sodium",
            "iris", "iris",
            "flywheel", "flywheel",
            "acceleratedrendering", "acceleratedrendering");
```

- [ ] **Step 5: Write the mixin**

`src/main/java/io/github/edwardg117/renderable/hooks/mixin/acceleratedrendering/AcceleratedBufferBuilderMixin.java`:

```java
package io.github.edwardg117.renderable.hooks.mixin.acceleratedrendering;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.edwardg117.renderable.hooks.Probe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts the vertices Accelerated Rendering writes, so the renderers it takes over stop reading zero
 * (spec addendum §P4.A3). The target is named by string because the mod is an alpha that is on no Maven,
 * so there is no compile-time dependency; every injector is {@code require = 0} and the whole sub-package
 * is gated on the mod being loaded, so a rename degrades this column to the zero it already showed.
 *
 * <p>The two {@code addVertex} overloads each add one vertex, {@code addClientMesh}'s second parameter is
 * its vertex count, and {@code addServerMesh} adds {@code mesh.size()} to one of two counters depending on
 * whether the mesh is uploaded immediately, which the delta of {@code getTotalVertexCount()} covers either
 * way. {@code addVertex(PoseStack$Pose, float, float, float)} writes no counter and delegates, so hooking
 * it as well would double count.
 */
@Mixin(targets = "com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.AcceleratedBufferBuilder",
        remap = false)
public abstract class AcceleratedBufferBuilderMixin {

    @Shadow
    public abstract int getTotalVertexCount();

    /** Vertex total before the current addServerMesh call; that method is never re-entrant. */
    @Unique
    private int renderable$meshBefore;

    @ModifyReturnValue(method = "addVertex(FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At("RETURN"), require = 0)
    private VertexConsumer renderable$countSlowVertex(VertexConsumer original) {
        if (Probe.ACTIVE) Probe.acceleratedVertex();
        return original;
    }

    @ModifyVariable(method = "addVertex(FFFIFFIIFFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float renderable$countFastVertex(float x) {
        if (Probe.ACTIVE) Probe.acceleratedVertex();
        return x;
    }

    @ModifyVariable(method = "addClientMesh", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int renderable$countClientMesh(int vertexCount) {
        if (Probe.ACTIVE) Probe.acceleratedVertices(vertexCount);
        return vertexCount;
    }

    @Inject(method = "addServerMesh", at = @At("HEAD"), require = 0)
    private void renderable$serverMeshBefore(CallbackInfo ci) {
        if (Probe.ACTIVE) renderable$meshBefore = getTotalVertexCount();
    }

    @Inject(method = "addServerMesh", at = @At("RETURN"), require = 0)
    private void renderable$serverMeshAfter(CallbackInfo ci) {
        if (Probe.ACTIVE) Probe.acceleratedVertices(getTotalVertexCount() - renderable$meshBefore);
    }
}
```

- [ ] **Step 6: Register it**

`src/main/resources/renderable.thirdparty.mixins.json`:

```json
{
  "required": false,
  "minVersion": "0.8",
  "package": "io.github.edwardg117.renderable.hooks.mixin",
  "compatibilityLevel": "JAVA_21",
  "plugin": "io.github.edwardg117.renderable.hooks.RenderableMixinPlugin",
  "client": [
    "flywheel.FlywheelRenderDispatcherMixin",
    "sodium.ChunkBuilderTaskAccessor",
    "sodium.ChunkBuilderMeshingTaskMixin",
    "sodium.RenderSectionManagerMixin",
    "sodium.SodiumBufferBuilderMixin",
    "acceleratedrendering.AcceleratedBufferBuilderMixin"
  ],
  "injectors": {
    "defaultRequire": 0
  }
}
```

- [ ] **Step 7: Compile and check the gate**

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 86 tests, `build/libs/renderable-0.3.0.jar` (the version bump is Task 8).

Then a plain export launch with no third-party mods, to prove the gate keeps the mixin out:

```bash
JAVA_TOOL_OPTIONS="-Dmixin.debug.export=true" ./gradlew runClient -PquickPlay="New World" --no-daemon
```

Expected in `run/logs/latest.log`: `Renderable 0.3.0 loaded`, and **no** line mentioning `AcceleratedBufferBuilderMixin`. Stop the client (Step 8), then delete `run/.mixin.out`.

- [ ] **Step 8: Prove it applies against the real jar**

```bash
JAVA_TOOL_OPTIONS="-Dmixin.debug.export=true" ./gradlew runClient -PquickPlay="New World" -PpackMods -PextraMods="%APPDATA%\.MCUpdater\instances\Gears-And-Frontiers\mods\accelerated_rendering.jar" --no-daemon
```

Once the world has loaded and a mob is on screen, stop the client and run:

```bash
javap -p -cp run/.mixin.out/class com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.AcceleratedBufferBuilder | grep -n 'renderable\$'
```

Expected: five `renderable$` members — `renderable$meshBefore`, `renderable$countSlowVertex`,
`renderable$countFastVertex`, `renderable$countClientMesh`, `renderable$serverMeshBefore`,
`renderable$serverMeshAfter` (the field plus five handlers). No `Mixin apply failed` line in
`run/logs/latest.log`.

**If the dev client does not start with Accelerated Rendering installed** (it is an alpha and has never been
run in this dev environment), do not spend time on it: record the exact failure from `run/logs/latest.log`
in your report, remove `-PextraMods` from the launch, confirm the other mixins still apply, and leave the
proof to Task 9's in-pack run, where the mod is known to work. The gate and `require = 0` mean a mistake
here degrades the `verts/f` column rather than crashing.

- [ ] **Step 9: Stop the client**

List first, then kill; the user's real pack client may be running on the same machine:

```powershell
Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*fml.modFolders=renderable%%H:\repos\Renderable\build*' } | Select-Object ProcessId, CommandLine
```

Confirm every row is the dev client from this repository, then:

```powershell
Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*fml.modFolders=renderable%%H:\repos\Renderable\build*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
```

Then `rm -rf run/.mixin.out`.

- [ ] **Step 10: Stage and report**

```bash
git add build.gradle src/main/java/io/github/edwardg117/renderable/hooks src/main/resources/renderable.thirdparty.mixins.json
git status --short
```

Commit message for the orchestrator:

```
feat(hooks): count Accelerated Rendering's vertices so mob renderers stop reading zero
```

---

### Task 7: The results screen shows the GC columns and the vertex-call count

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Tab.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java`

Spec addendum §P4.A1 and §P4.A5. `ui` cannot be unit-tested without Minecraft, so this task's proof is that the client launches and the tabs render (Step 4) plus Task 9's in-pack look.

- [ ] **Step 1: Add the columns in `Tab.java`**

Replace the `RANKED` and `PARTICLE` constants:

```java
        static final Columns RANKED = new Columns(
                new String[]{"name", "render us/f", "tick us/f", "share", "peak ms", "gc f", "shadow", "verts/f", "calls"},
                new int[]{5, 1, 1, 1, 1, 1, 1, 1, 1},
                new boolean[]{false, true, true, true, true, true, true, true, true});
        static final Columns PARTICLE = new Columns(
                new String[]{"class", "render us/f", "tick us/f", "share", "avg live", "verts/f", "gc f"},
                new int[]{5, 1, 1, 1, 1, 1, 1},
                new boolean[]{false, true, true, true, true, true, true});
```

- [ ] **Step 2: Fill the cells in `Rows.java`**

Replace `cells`:

```java
    static String[] cells(String name, RankedEntry e, int frames, long total) {
        return new String[]{
                name,
                Fmt.us(e.renderUsPerFrame(frames)),
                Fmt.us(e.tickUsPerFrame(frames)),
                Fmt.pct(e.sharePercent(total)),
                Fmt.peakMs(e.maxFrameNanos(), e.gcFrames()),
                Fmt.count(e.gcFrames()),
                Fmt.pct(e.shadowPercent()),
                Fmt.num(e.verticesPerFrame(frames)),
                Fmt.count(e.calls())};
    }
```

Replace the row construction in `particles`:

```java
            rows.add(new Row(new String[]{
                    e.label(),
                    Fmt.us(e.renderUsPerFrame(frames)),
                    Fmt.us(e.tickUsPerFrame(frames)),
                    Fmt.pct(e.sharePercent(total)),
                    Fmt.num(e.callsPerFrame(frames)),
                    Fmt.num(e.verticesPerFrame(frames)),
                    Fmt.count(e.gcFrames())},
                    e.key(), e.typeId(), false, false, 0, null, -1));
```

Replace the contributor row construction in `hitches`:

```java
            for (TopEntry t : h.top()) {
                rows.add(new Row(new String[]{t.key().label() + (t.gc() ? "  [gc]" : ""), Fmt.ms2(t.nanos()), ""},
                        t.key(), t.key().typeId(), false, false, 1, posOf(t.key()), entityIdOf(t.key())));
            }
```

Replace the overhead row in `frames`:

```java
        rows.add(Row.plain("Renderable overhead", String.format(Locale.ROOT, "%.2f ms over %s timed calls and %s vertex calls",
                r.overheadEstimateMs(), Fmt.count(r.timedCalls()), Fmt.count(r.vertexCalls()))));
```

- [ ] **Step 3: Compile**

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 86 tests.

- [ ] **Step 4: Launch and look at the screen**

```bash
./gradlew runClient -PquickPlay="New World" --no-daemon
```

In the world, set `debugCommands = true` in `run/config/renderable-client.toml` if it is not already, then run `/renderable start 5`, wait for the results screen, and check by eye:

- the Block Entities and Entities tabs show nine columns ending `... peak ms | gc f | shadow | verts/f | calls`, with `gc f` reading `0`;
- the Particles tab shows seven columns ending `verts/f | gc f`;
- the Frames tab's `Renderable overhead` row reads `... over N timed calls and M vertex calls`.

Stop the client with the Step 9 filter from Task 6.

- [ ] **Step 5: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/ui
git status --short
```

Commit message for the orchestrator:

```
feat(ui): gc columns on the ranked and particle tabs; vertex calls on the Frames tab
```

---

### Task 8: Version 0.4.0, README, spec amendments, final export check

**Files:**
- Modify: `gradle.properties`
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md`

- [ ] **Step 1: Bump the version**

In `gradle.properties`, change `mod_version=0.3.0` to `mod_version=0.4.0`.

- [ ] **Step 2: Update `README.md`**

In the "Reading a report" list, replace the `verts/f` bullet with:

```markdown
- **verts/f** is the number of vertices a renderer emitted per frame, the proxy for GPU load. It counts
  emitted vertices: glint and outline passes emit the same geometry again, and Iris shaders roughly
  double it through the shadow pass. With Sodium installed the count relies on Sodium's own vertex
  path being hooked; if that hook did not apply the column reads near zero. Accelerated Rendering, which
  expands model vertices on the GPU, is hooked separately, so the renderers it takes over are counted too;
  another mod that replaces the vertex path the same way would not be, and its renderers would read zero.
```

Add after that bullet:

```markdown
- **peak ms** is the worst single frame, and a type row's peak is the whole type's worst frame, not its
  worst member. A frame whose GC pause is as large as the value itself is not allowed to set a peak,
  because a stop-the-world pause inflates whatever happened to be running: **gc f** counts the frames
  that were thrown out that way, and `-` in **peak ms** means every frame was. In the hitch list, `[gc]`
  marks a contributor that is at least as large as that frame's pause.
```

In the "Limits" paragraph, replace `while sampling, the report states its own overhead, which does not include vertex counting,` with `while sampling, the report states its own overhead, which covers both timed calls and vertex counting,`.

In the "Report files" section, insert above the schema 3 paragraph:

```markdown
Schema 4 (this version) adds `gcFrames` to every ranked row, `gc` to every entry of `frames[].top`, and
`session.vertexCalls`. `peakFrameUs` keeps its name but is now the largest **clean** frame: a value of `0`
with `gcFrames > 0` means every frame the key appeared in was GC-suspect, which is what the text report
prints as `-`.
```

- [ ] **Step 3: Apply the spec amendments**

In `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md`:

1. **§3.4**, after the paragraph beginning "Each key's accumulator holds", add:

```markdown
A parallel `TypeStats` per (category, type id) keeps the same per-frame scratch value, so a type row's peak
is the type's worst frame rather than its worst member. Accumulators hold a reference to their `TypeStats`,
so the hot path never looks one up. A frame's contribution is only allowed to set a peak when it is not
GC-suspect: a value at least as large as that frame's reported GC pause (which the JVM gives in whole
milliseconds, so the comparison allows one millisecond of slack, and pauses under two milliseconds are
ignored) may be the collector rather than the code, and is counted in `gcFrames` instead.
```

2. **§3.5**, replace the last bullet with:

```markdown
- `BufferBuilder.beginVertex` (and Sodium's `push`, and Accelerated Rendering's builder) get one branch on
  the idle flag plus one increment while sampling. The reported overhead is
  `timedCalls * calibratedCallNanos + vertexCalls * calibratedVertexNanos`, both constants measured at
  session start, so vertex counting is no longer missing from it.
```

3. **§4.3**, add a row to the table:

```markdown
| `AcceleratedBufferBuilder#addVertex` (both overloads), `#addClientMesh`, `#addServerMesh` added by Accelerated Rendering (gated on `acceleratedrendering`, targeted by string) | `@ModifyReturnValue`, `@ModifyVariable`, `@Inject` head/return | adds the vertices of renderers whose geometry the mod expands on the GPU, which never reach `beginVertex` |
```

4. **§5.3**, in the Block Entities, Entities and Particles rows of the tab table, add `gc f` to the column
   list, and note after the table: "`peak ms` reads `-` when every frame was GC-suspect."

5. **§6.1**, in item 5 and 6, add "and the count of GC-suspect frames"; in item 4, add "contributors that
   are at least as large as the frame's pause are marked `[gc]`."

6. **§6.2**, change `"schema": 3` to `"schema": 4` in the example, add `"gcFrames"` to the ranked row
   description and `"gc"` to the `top` entry, and add `"vertexCalls"` to the `session` object.

7. **§9**, replace check 5 with:

```markdown
5. Idle cost: measured with Java Flight Recorder in a static superflat scene, four runs of 120 s (jar
   absent and present, each with and without `-XX:-DoEscapeAnalysis`). With escape analysis on, allocation
   and mean CPU load with the jar must be within 2% of without it, and fewer than 0.1% of execution samples
   may contain a `io.github.edwardg117.renderable` frame. With escape analysis off, the jar's run must
   allocate more than 2% above the jar-free run, which is the control proving the measurement is sensitive
   enough to support the first claim. See `docs/acceptance/phase-4-idle.md`.
```

8. **§11**, add a row to the phases table:

```markdown
| 4 | GC-suspect flagging and type peaks, Accelerated Rendering vertices, the vertex-aware overhead estimate, streamed JSON, the object-cached accumulator, screen and overlay polish, hygiene and CI | in-game checks 1 to 5, check 5 for the first time |
```

- [ ] **Step 4: Build and prove every mixin still applies**

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 86 tests, `build/libs/renderable-0.4.0.jar`.

```bash
JAVA_TOOL_OPTIONS="-Dmixin.debug.export=true" ./gradlew runClient -PquickPlay="New World" --no-daemon
```

Expected in the log: `Renderable 0.4.0 loaded`, no mixin errors, and no mention of any `sodium.*`,
`flywheel.*` or `acceleratedrendering.*` mixin. Stop the client with Task 6's Step 9 filter, delete
`run/.mixin.out`.

- [ ] **Step 5: Stage and report**

```bash
git add gradle.properties README.md docs/superpowers/specs
git status --short
```

Commit message for the orchestrator:

```
docs: README and spec for phase 4a; version 0.4.0
```

---

### Task 9: In-pack acceptance and the idle-cost measurement (human step)

**Files:**
- Create: `docs/acceptance/phase-4a.md`
- Create: `docs/acceptance/phase-4-idle.md`

The user runs these in the real Gears & Frontiers instance with `renderable-0.4.0.jar`. The orchestrator
writes both logs from what comes back, has them reviewed, commits them, and tags `v0.4.0`.

**Part 1 — in the pack.** Copy `build/libs/renderable-0.4.0.jar` into
`%APPDATA%\.MCUpdater\instances\Gears-And-Frontiers\mods\`, replacing the 0.3.0 jar,
and run a 30 s session at the base (the same place as run 1 of the phase 2/3 log, around 2571 105 1145) and
a second 30 s session flying into unloaded terrain.

1. **GC flagging (§P4.A1).** In the base report's `Hitches` section, every contributor whose milliseconds
   are at or above that frame's `gc` figure carries `[gc]`, and none below it does. Paste the three worst
   hitches.
2. **Peaks (§P4.A1).** In `Block entities by type` and `Particles`, at least one row has a non-zero `gc f`.
   Paste those rows. Confirm no `peak ms` in the report is now equal to that frame's `gc` figure.
3. **Type peaks (§P4.A2).** In `Block entities by type`, the `create:factory_panel` or
   `createenchantablemachinery:enchantable_millstone` row's `peak ms` is at least as large as the largest
   `peak ms` among that type's rows in `Block entities by position`. Paste both.
4. **Accelerated Rendering (§P4.A3).** `hooks applied` contains `ACCELERATED_VERTICES`, and `verts/f` is
   non-zero for at least one ordinary mob (`minecraft:pig`, `minecraft:cow`, `minecraft:chicken` — whatever
   is in view) in `Entities by type`. Paste the environment line and those rows. This is the check that
   phase 3's log left open.
5. **Overhead (§P4.A5).** The `Renderable overhead` line now names a vertex-call count. Paste it next to
   the phase 2/3 log's `555.76 ms over 6,947,008 calls` so the difference is on the record.
6. **Screen.** Open the results screen: the ranked tabs show `gc f`, the Particles tab shows `gc f`, and a
   hitch contributor with `[gc]` reads to the end of its cell.
7. **Stability.** No `Attribution stack reset` line; no crash on session end, logout or dimension change.

**Part 2 — the idle-cost measurement (§P4.A4, base spec §9 check 5).**

Create the scene once, in the dev client:

```bash
./gradlew runClient --no-daemon
```

Create a new **superflat** world named `RenderableIdle`, then in it run:

```
/gamerule doDaylightCycle false
/gamerule doMobSpawning false
/gamerule doWeatherCycle false
/time set noon
/kill @e[type=!player]
/gamemode spectator
```

Set render distance 8, simulation distance 5, fps limit 60, vsync off, graphics fast, GUI scale 2, windowed
1280x720, and close F3. Quit. Do not move the camera during any of the four runs.

Then run each of the four, letting each finish on its own:

```bash
# Run 1: jar absent, escape analysis on
JAVA_TOOL_OPTIONS="-XX:StartFlightRecording=delay=60s,duration=120s,filename=run1.jfr,settings=profile" ./gradlew runClient -PquickPlay="RenderableIdle" --no-daemon
```

For runs 2 and 4 the mod's own classes must be loaded, which the dev client always does, so "jar present" is
the plain `runClient` above and "jar absent" is the same launch with the mod source set excluded. The
simplest way to get a jar-free client with an identical scene is to run the pack instance's launcher
profile with and without `renderable-0.4.0.jar` in `mods\`, on the same superflat world copied into the
instance's `saves\`. Use whichever of the two you can make reproducible, and **say which one you used in the
log**; the comparison only has to be like-for-like.

Runs 3 and 4 add `-XX:-DoEscapeAnalysis` to `JAVA_TOOL_OPTIONS` and write `run3.jfr` / `run4.jfr`.

For each recording:

```bash
jfr summary run1.jfr
jfr print --events jdk.CPULoad run1.jfr | grep -E 'jvmUser|jvmSystem' | head -40
jfr print --events jdk.ObjectAllocationSample run1.jfr | grep -c 'renderable'
```

Paste, per run: the `jdk.ObjectAllocationSample` and `jdk.ExecutionSample` event counts from `jfr summary`,
the mean of `jvmUser + jvmSystem`, and the number of execution samples whose stack mentions
`io.github.edwardg117.renderable`.

**Verdict rules, from §P4.A4:** run 2 versus run 1 must be within ±2% on allocation and on mean CPU load
with fewer than 0.1% Renderable execution samples; and run 4 must allocate more than 2% above run 3. If run
4 does not separate from run 3, the result is **inconclusive**, not a pass, and the README keeps its
"design claim" wording.

Then the orchestrator:

```bash
git add docs/acceptance/phase-4a.md docs/acceptance/phase-4-idle.md README.md
git commit -m "docs: phase 4a acceptance log and the idle-cost measurement"
git tag v0.4.0
```

The user pushes `main` and the tag when ready.

---

## Notes for the orchestrator

- Tasks 1 to 5 are pure JUnit and can each be reviewed from the diff and the test output alone.
- Task 6 is the only one that needs a third-party jar; its fallback is written into Step 8 so a subagent
  cannot get stuck there.
- Task 7 needs a dev client launch for a visual check only.
- **Reviewer checklist per task:** no allocation added to `Session.end()`, `Session.vertex()`,
  `Probe.acceleratedVertex()` or `Probe.acceleratedVertices()`; `core` free of Minecraft imports;
  the GC-suspect comparison written exactly once (in `closeFrame`) and never duplicated; `TypeStats`
  resolved at accumulator creation, never inside `end()`; every new injector `require = 0` and in the
  third-party config.
- If a reviewer proposes subtracting GC pauses rather than flagging them, point at §P4.A1's rejected
  alternatives: the decision is the user's and it is recorded.
