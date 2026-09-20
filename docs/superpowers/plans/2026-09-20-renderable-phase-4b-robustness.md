# Renderable Phase 4b Implementation Plan: robustness and cost

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the profiler distorting what it measures and stop it failing in ways it was designed not to: stream the JSON instead of building a 4.86 MB Gson tree, cache accumulators on the objects instead of in a map that keeps every rendered thing alive for the session, throttle the HUD's rescan and measure the mod's own in-frame work, and put a failure latch on the one third-party call still exposed on the hot path.

**Architecture:** `JsonReportWriter` gains a `Writer` overload built on `com.google.gson.stream.JsonWriter` and keeps a `String` overload for tests; `ReportFiles` opens the file and hands the writer over, becomes free of Minecraft imports, and validates the configured folder. `Probe`'s `IdentityHashMap` is replaced by a `@Unique` field on `BlockEntity` and `Entity` reached through a duck-typed interface, with the accumulator carrying the session that made it so a stale one is spotted. `SamplingHud` caches its rolling hitch count, and `Session` gains a measured `selfNanos` for the work the mod does inside a frame for itself.

**Tech Stack:** Java 21, ModDevGradle 2.0.147, NeoForge 21.1.220, Mixin `@Unique` plus a duck-typed interface, Gson 2.10.1's streaming writer, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-20-renderable-phase-4-design.md` §P4.B (all of it), plus §P4.D1's import test and §P4.D2, both of which this plan implements because they touch the same method. Base spec §4, §4.5, §6.1, §6.2, §8.

**Repository:** `H:\repos\Renderable`, branch `main`. Commits are YubiKey-signed: the orchestrator commits after telling the user to touch the key. **Subagents stage and report; they do not commit.**

**Ordering:** This plan assumes **plan 4a is on `main`** (`v0.4.0`). It is independent of 4c and 4d, and may run before or after either. Where it adds something 4c or 4d also needs, that item is listed under "Shared additions" below and each of those plans is written to skip it if it is already there.

**Shared additions this plan introduces** (4c and 4d each say "skip if present"): `Environment.ABSENT`, `Environment.has(String)` and `Environment.value(String)` in Task 5.

**Verified facts you can rely on:**

- `com.google.gson.stream.JsonWriter` in the Gson on the compile classpath (`gson-2.10.1.jar`, read with `javap` on 2026-09-20):

  ```
  public class com.google.gson.stream.JsonWriter implements java.io.Closeable,java.io.Flushable {
    public final void setIndent(java.lang.String);
    public com.google.gson.stream.JsonWriter beginArray() throws java.io.IOException;
    public com.google.gson.stream.JsonWriter endArray() throws java.io.IOException;
    public com.google.gson.stream.JsonWriter beginObject() throws java.io.IOException;
    public com.google.gson.stream.JsonWriter endObject() throws java.io.IOException;
    public com.google.gson.stream.JsonWriter name(java.lang.String) throws java.io.IOException;
    public com.google.gson.stream.JsonWriter value(java.lang.String) throws java.io.IOException;
    public com.google.gson.stream.JsonWriter value(boolean) throws java.io.IOException;
    public com.google.gson.stream.JsonWriter value(double) throws java.io.IOException;
    public com.google.gson.stream.JsonWriter value(long) throws java.io.IOException;
    public void close() throws java.io.IOException;
  }
  ```

  `GsonBuilder().setPrettyPrinting()` is exactly `setIndent("  ")` on the same writer, so the streamed
  output is formatted identically. `Gson.toJson` writes HTML-safe by default and a bare `JsonWriter` does
  not, so the new code calls `setHtmlSafe(true)` to keep the bytes identical for values containing
  `<`, `>`, `&`, `=` or `'` — a shader pack or GPU string could.
- `net.minecraft.world.level.block.entity.BlockEntity` and `net.minecraft.world.entity.Entity` in
  `build/moddev/artifacts/neoforge-21.1.220.jar` (read with `javap` on 2026-09-20) are both
  `public abstract class ... extends net.neoforged.neoforge.attachment.AttachmentHolder`, so both are
  ordinary mixin targets. `Entity` overrides `equals(Object)` and `hashCode()` (by entity id), which is
  why a `WeakHashMap` would not be an identity map and the object-field cache is used instead.
- `Probe.detach()` already clears `BY_INSTANCE`, so the retention this plan removes is **within** a
  session, which is the window the report's GC figures describe.
- `FrameEvents` already handles `ClientPlayerNetworkEvent.LoggingOut` and `LevelEvent.Unload`, so there is
  a place to notice that a kept result's world has gone.
- Phase 3's acceptance log: the JSON is 4.86 MB for 4983 frames and the sections array about 280 KB of it;
  a 30 s base session recorded 15 collections and 369 ms of pause.
- The suite is **86 tests** after plan 4a.

**Deviations from the spec addendum recorded in this plan:** the mod's own in-frame measurement
(`selfNanos`) covers the sampling HUD and the Flywheel visual sample but **not** the world overlay, because
`ProfilerController.start` calls `WorldOverlay.clear()`, so a running session has no labels to draw. The
only overlay work that can overlap a session is a ten-second highlight of twelve projected edges, which is
recorded in the README rather than measured.

---

## File map

```
README.md                                                     Modify (Task 6)
docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md
                                                              Modify: §3.5, §4, §6.2 (Task 6)
src/main/resources/renderable.mixins.json                     Modify: two accumulator mixins (Task 2)
src/main/java/io/github/edwardg117/renderable/
  core/Session.java                                           Modify: owner on accumulators, selfNanos
  core/Accumulator.java                                       Modify: owner
  core/SessionResult.java                                     Modify: selfNanos
  core/Environment.java                                       Modify: defensive ordered copy, ABSENT, has, value
  report/JsonReportWriter.java                                Rewrite: streaming
  report/ReportFiles.java                                     Rewrite: JsonBody, no Minecraft, folder validation
  report/TextReportWriter.java                                Modify: self-time line, Environment.value
  report/EnvironmentCollector.java                            Modify: Environment.ABSENT
  hooks/AccumulatorHolder.java                                Create
  hooks/Probe.java                                            Modify: object-cached accumulators, Listener.onWorldGone
  hooks/FrameEvents.java                                      Modify: onWorldGone
  hooks/compat/IrisCompat.java                                Modify: failure latch
  hooks/compat/FlywheelCompat.java                            Modify: backendName latch
  hooks/mixin/BlockEntityAccumulatorMixin.java                Create
  hooks/mixin/EntityAccumulatorMixin.java                     Create
  ui/SamplingHud.java                                         Modify: throttle, self timing
  ui/WorldOverlay.java                                        Modify: enabled is lazy, not reset per session
  ui/screen/ResultsScreen.java                                Modify: disable world actions after logout
  ui/screen/Rows.java                                         Modify: self-time row
  ProfilerController.java                                     Modify: streaming write, game dir, resultWorldGone
  RenderableConfig.java                                       Modify: reportFolder comment
src/test/java/io/github/edwardg117/renderable/
  core/SessionTest.java                                       Modify: owner, selfNanos
  core/EnvironmentTest.java                                   Create
  core/KeysTest.java                                          Modify: report import guard
  report/ReportFilesTest.java                                 Create
  report/JsonReportWriterTest.java                            Modify: streaming equality, selfMs
  report/TextReportWriterTest.java                            Modify: SessionResult arity, self-time line
docs/acceptance/phase-4b.md                                   Create (Task 7)
```

**Expected test totals**, assuming 4a is on `main` and neither 4c nor 4d has run: **90** at the start, 96
after Task 1, 97 after Task 2, 100 after Task 3, 100 after Task 4, 102 after Task 5, 102 at the end.

This plan was drafted expecting 86, because 4a's own plan arithmetic reached 86. 4a actually landed at 90:
its two-stage reviews added four tests its steps do not contain — a GC floor boundary that a `<=` typo would
have slipped past, a calibration assertion that could not fail as written, a key-set test pinning the JSON
shape, and a split of one overloaded test. Expect the same here: the totals below are the plan's arithmetic,
and review rounds will likely push the real number above them. A shortfall against these figures is worth
investigating; an excess is not.

---

### Task 1: Stream the JSON, and make `ReportFiles` pure Java with a validated folder

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/report/JsonReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/ReportFiles.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/RenderableConfig.java`
- Create: `src/test/java/io/github/edwardg117/renderable/report/ReportFilesTest.java`
- Test: `src/test/java/io/github/edwardg117/renderable/report/JsonReportWriterTest.java` (append)
- Test: `src/test/java/io/github/edwardg117/renderable/core/KeysTest.java` (append)

Spec addendum §P4.B1, §P4.B6 #3 and #6, §P4.D1's import test and §P4.D2. All four land here because they
change the same method, and splitting one method's rework across two independently-ordered plans would make
whichever ran second conflict.

- [ ] **Step 0: Capture the current output, before you touch anything**

This task's acceptance criterion is that the JSON is **byte-identical** after the rewrite, and no test in the
repository would detect a whitespace or number-formatting difference. The key-set test added in phase 4a
pins field order and membership, but not indentation, separators or how a `double` renders. So capture the
current bytes first and diff against them at the end. Write to a scratch directory, not the repository —
this is a working artefact, not a golden file to commit:

```bash
mkdir -p "$TMPDIR/renderable-json" 2>/dev/null || mkdir -p /tmp/renderable-json
cat > /tmp/CaptureJson.java <<'JAVA'
// Throwaway: prints the current JSON for one fixture so the rewrite can be diffed against it.
JAVA
```

The simplest reliable capture is a temporary JUnit test you delete afterwards: add a method to
`JsonReportWriterTest` that does
`java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/renderable-json/before.json"), JsonReportWriter.write(TestSessions.phase3(), environment(), "0.4.0"));`,
run it with `./gradlew test --tests '...JsonReportWriterTest' --no-daemon`, then remove the method. Repeat
after Step 3 writing `after.json`, and confirm:

```bash
diff /tmp/renderable-json/before.json /tmp/renderable-json/after.json && echo "byte-identical"
```

Expected: `byte-identical`, no output from `diff`. If it differs, the two most likely causes are the indent
string (Gson's pretty printer is two spaces; `JsonWriter.setIndent("  ")` must match) and HTML-safe escaping
(`Gson.toJson` escapes `<`, `>`, `&`, `=` and `'` by default; a bare `JsonWriter` does not, which is why
Step 3 calls `setHtmlSafe(true)`). Fix the writer, not the expectation.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/io/github/edwardg117/renderable/report/ReportFilesTest.java`:

```java
package io.github.edwardg117.renderable.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportFilesTest {

    @Test
    void aSafeRelativeFolderIsResolvedUnderTheGameDirectory(@TempDir Path game) {
        assertEquals(game.resolve("renderable"), ReportFiles.resolveFolder(game, "renderable"));
        assertEquals(game.resolve("logs").resolve("profiles"), ReportFiles.resolveFolder(game, "logs/profiles"));
        assertEquals(game.resolve("renderable"), ReportFiles.resolveFolder(game, "  renderable  "),
                "surrounding whitespace is trimmed");
    }

    @Test
    void anythingThatCouldEscapeTheGameDirectoryFallsBackToTheDefault(@TempDir Path game) {
        Path fallback = game.resolve(ReportFiles.DEFAULT_FOLDER);
        assertEquals(fallback, ReportFiles.resolveFolder(game, "../outside"));
        assertEquals(fallback, ReportFiles.resolveFolder(game, "reports/../../outside"));
        assertEquals(fallback, ReportFiles.resolveFolder(game, "/etc"));
        assertEquals(fallback, ReportFiles.resolveFolder(game, ""));
        assertEquals(fallback, ReportFiles.resolveFolder(game, "   "));
        assertEquals(fallback, ReportFiles.resolveFolder(game, null));
    }

    @Test
    void writesBothFilesAndStreamsTheJsonBody(@TempDir Path game) throws IOException {
        ReportFiles.Written w = ReportFiles.write(game, "renderable", true, true,
                "the text report\n", out -> out.write("{\"streamed\":true}"), 1_726_700_000_000L);

        assertEquals(game.resolve("renderable"), w.folder());
        assertNotNull(w.text());
        assertNotNull(w.json());
        assertEquals("the text report\n", Files.readString(w.text()));
        assertEquals("{\"streamed\":true}", Files.readString(w.json()));
        assertTrue(w.text().getFileName().toString().startsWith("renderable-"), w.text().toString());
        assertTrue(w.text().getFileName().toString().endsWith(".txt"), w.text().toString());
        assertTrue(w.json().getFileName().toString().endsWith(".json"), w.json().toString());
    }

    @Test
    void disabledFormatsAreNotWrittenAndAFailureIsReportedNotSwallowed(@TempDir Path game) throws IOException {
        ReportFiles.Written textOnly = ReportFiles.write(game, "renderable", true, false,
                "text", out -> fail("the JSON body must not be asked for when writeJson is false"), 1L);
        assertNotNull(textOnly.text());
        assertNull(textOnly.json());
        assertEquals(List.of("renderable-" + io.github.edwardg117.renderable.core.Fmt.fileStamp(1L) + ".txt"),
                Files.list(textOnly.folder()).map(p -> p.getFileName().toString()).sorted().toList());

        ReportFiles.WriteFailed failed = assertThrows(ReportFiles.WriteFailed.class,
                () -> ReportFiles.write(game, "renderable", false, true, "",
                        out -> { throw new IOException("disk went away"); }, 2L));
        assertTrue(failed.getMessage().contains("disk went away"), failed.getMessage());
    }
}
```

Append inside `JsonReportWriterTest`:

```java
    @Test
    void theWriterOverloadProducesExactlyTheStringOverloadsBytes() throws Exception {
        SessionResult r3 = TestSessions.phase3();
        java.io.StringWriter out = new java.io.StringWriter();
        JsonReportWriter.write(r3, environment(), "0.4.0", out);
        assertEquals(JsonReportWriter.write(r3, environment(), "0.4.0"), out.toString());
        assertEquals(4, JsonParser.parseString(out.toString()).getAsJsonObject().get("schema").getAsInt());
        assertTrue(out.toString().startsWith("{\n  \"schema\": 4,"), out.toString().substring(0, 40));
    }
```

Append inside `KeysTest` (it already imports `java.nio.file.Path`, `java.util.List`, `java.util.stream.Stream` and `java.io.IOException` for the existing walk):

```java
    @Test
    void reportWritersHaveNoMinecraftImports() throws IOException {
        // Spec §8: EnvironmentCollector is the only Minecraft-aware class in report.
        Path report = Path.of("src/main/java/io/github/edwardg117/renderable/report");
        List<String> forbidden = List.of("import net.minecraft", "import net.neoforged", "import com.mojang", "import org.lwjgl");
        try (Stream<Path> files = Files.walk(report)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (p.getFileName().toString().equals("EnvironmentCollector.java")) continue;
                String src = Files.readString(p);
                for (String f : forbidden) {
                    assertFalse(src.contains(f), p + " contains " + f);
                }
            }
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.report.*' --tests 'io.github.edwardg117.renderable.core.KeysTest' --no-daemon
```

Expected: compilation failure — `cannot find symbol: method resolveFolder`, `class WriteFailed`,
`DEFAULT_FOLDER`, and no four-argument `JsonReportWriter.write`. `reportWritersHaveNoMinecraftImports`
fails on `ReportFiles.java contains import net.minecraft`.

- [ ] **Step 3: Rewrite `JsonReportWriter.java` as a streaming writer**

Replace the whole file:

```java
package io.github.edwardg117.renderable.report;

import com.google.gson.stream.JsonWriter;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.FlywheelStats;
import io.github.edwardg117.renderable.core.FrameRecord;
import io.github.edwardg117.renderable.core.FrameStats;
import io.github.edwardg117.renderable.core.MeshEntry;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.RankedEntry;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SectionKey;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.TopEntry;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Spec §6.2: complete, untruncated machine-readable report, streamed a row at a time so a long session is
 * never held in memory as a tree or as a string (spec addendum §P4.B1). Every array is complete; nothing is
 * capped.
 */
public final class JsonReportWriter {
    public static final int SCHEMA = 4;

    private JsonReportWriter() {
    }

    /**
     * Writes the report to {@code out}. The writer is flushed but not closed, so the caller owns the file.
     * HTML-safe escaping is on because {@code Gson.toJson} escapes that way by default and an environment
     * string such as a shader pack name can contain the characters it differs on.
     */
    public static void write(SessionResult r, Environment env, String modVersion, Writer out) throws IOException {
        JsonWriter w = new JsonWriter(out);
        w.setIndent("  ");
        w.setHtmlSafe(true);
        w.setSerializeNulls(false);   // Gson.toJson's default; a bare JsonWriter would emit "k": null

        w.beginObject();
        w.name("schema").value(SCHEMA);

        w.name("mod").beginObject();
        w.name("version").value(modVersion);
        w.endObject();

        w.name("environment").beginObject();
        for (Map.Entry<String, String> e : env.entries().entrySet()) w.name(e.getKey()).value(e.getValue());
        w.name("hooksApplied").beginArray();
        for (String h : env.hooksApplied()) w.value(h);
        w.endArray();
        w.endObject();

        FrameStats st = r.stats();
        w.name("session").beginObject();
        w.name("startedAt").value(Instant.ofEpochMilli(r.wallStartMillis()).toString());
        w.name("durationSeconds").value(st.durationSeconds());
        w.name("frames").value(r.frameCount());
        w.name("hitchThresholdMs").value(r.hitchThresholdNanos() / 1e6);
        w.name("overheadEstimateMs").value(r.overheadEstimateMs());
        w.name("timedCalls").value(r.timedCalls());
        w.name("vertexCalls").value(r.vertexCalls());
        w.name("unbalancedFrames").value(r.unbalancedFrames());
        w.name("config").beginObject();
        w.name("durationSeconds").value(r.config().durationSeconds());
        w.name("hitchMedianMultiplier").value(r.config().hitchMedianMultiplier());
        w.name("hitchMinExcessMs").value(r.config().hitchMinExcessNanos() / 1e6);
        w.name("topContributorsPerFrame").value(r.config().topContributorsPerFrame());
        w.endObject();
        w.endObject();

        w.name("stats").beginObject();
        w.name("avgMs").value(st.avgNanos() / 1e6);
        w.name("avgFps").value(st.avgFps());
        w.name("p50Ms").value(st.p50Nanos() / 1e6);
        w.name("p95Ms").value(st.p95Nanos() / 1e6);
        w.name("p99Ms").value(st.p99Nanos() / 1e6);
        w.name("maxMs").value(st.maxNanos() / 1e6);
        w.name("avgTickMs").value(st.avgTickNanos() / 1e6);
        w.name("avgRenderMs").value(st.avgRenderNanos() / 1e6);
        w.name("avgSwapMs").value(st.avgSwapNanos() / 1e6);
        w.name("avgOtherRenderMs").value(st.avgOtherRenderNanos() / 1e6);
        w.name("avgOtherTickMs").value(st.avgOtherTickNanos() / 1e6);
        w.name("gcCount").value(st.gcCount());
        w.name("gcMs").value(st.gcMillis());
        w.name("avgBucketsMs").beginObject();
        for (Bucket b : Bucket.VALUES) w.name(b.label()).value(st.avgBucket(b) / 1e6);
        w.endObject();
        w.endObject();

        w.name("frames").beginArray();
        for (FrameRecord f : r.frames()) {
            w.beginObject();
            w.name("i").value(f.index());
            w.name("startUs").value(f.startNanos() / 1000);
            w.name("totalUs").value(f.totalNanos() / 1000);
            w.name("tickUs").value(f.tickNanos() / 1000);
            w.name("renderUs").value(f.renderNanos() / 1000);
            w.name("swapUs").value(f.swapNanos() / 1000);
            w.name("otherRenderUs").value(f.otherRenderNanos() / 1000);
            w.name("bucketsUs").beginObject();
            for (Bucket b : Bucket.VALUES) {
                long v = f.bucket(b);
                if (v > 0) w.name(b.label()).value(v / 1000);
            }
            w.endObject();
            w.name("gcCount").value(f.gcCount());
            w.name("gcMs").value(f.gcMillis());
            w.name("top").beginArray();
            for (TopEntry t : f.top()) {
                w.beginObject();
                w.name("key").value(t.key().serialize());
                w.name("us").value(t.nanos() / 1000);
                w.name("gc").value(t.gc());
                w.endObject();
            }
            w.endArray();
            w.endObject();
        }
        w.endArray();

        w.name("hitches").beginArray();
        for (FrameRecord h : r.hitches()) w.value(h.index());
        w.endArray();

        w.name("blockEntities").beginObject();
        w.name("byType");
        ranked(w, Ranking.byType(r, ProfileKey.Category.BLOCK_ENTITY), r.frameCount());
        w.name("byPosition");
        ranked(w, Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY), r.frameCount());
        w.endObject();

        w.name("entities").beginObject();
        w.name("byType");
        ranked(w, Ranking.byType(r, ProfileKey.Category.ENTITY), r.frameCount());
        w.name("individuals");
        ranked(w, Ranking.byKey(r, ProfileKey.Category.ENTITY), r.frameCount());
        w.endObject();

        w.name("particles");
        ranked(w, Ranking.byKey(r, ProfileKey.Category.PARTICLE), r.frameCount());

        double seconds = st.durationSeconds();
        w.name("blocks");
        meshRows(w, Ranking.mesh(r, ProfileKey.Category.BLOCK, Ranking.MeshSort.TIME), seconds);
        w.name("sections");
        meshRows(w, Ranking.mesh(r, ProfileKey.Category.SECTION, Ranking.MeshSort.CALLS), seconds);

        long flywheelNanos = r.bucketTotalNanos(Bucket.FLYWHEEL);
        FlywheelStats fw = r.flywheel();
        w.name("flywheel").beginObject();
        w.name("bucketUs").value(flywheelNanos / 1000);
        w.name("sharePercent").value(st.totalNanos() == 0 ? 0.0 : 100.0 * flywheelNanos / st.totalNanos());
        w.name("samples").value(fw.samples());
        w.name("avgBlockEntities").value(fw.avgBlockEntities());
        w.name("avgEntities").value(fw.avgEntities());
        w.name("avgEffects").value(fw.avgEffects());
        w.name("visualCounts").beginObject();
        for (Map.Entry<String, Double> e : fw.byAverageDesc()) w.name(e.getKey()).value(e.getValue());
        w.endObject();
        w.endObject();

        w.endObject();
        w.flush();
    }

    /** The whole report as a string, for tests and anything that needs it in memory. */
    public static String write(SessionResult r, Environment env, String modVersion) {
        StringWriter out = new StringWriter(1 << 16);
        try {
            write(r, env, modVersion, out);
        } catch (IOException e) {
            throw new UncheckedIOException("a StringWriter cannot fail", e);
        }
        return out.toString();
    }

    private static void ranked(JsonWriter w, List<RankedEntry> entries, int frames) throws IOException {
        w.beginArray();
        for (RankedEntry e : entries) {
            w.beginObject();
            w.name("key").value(e.key() == null ? e.typeId() : e.key().serialize());
            w.name("label").value(e.label());
            w.name("typeId").value(e.typeId());
            w.name("members").value(e.members());
            w.name("calls").value(e.calls());
            w.name("tickCalls").value(e.tickCalls());
            w.name("callsPerFrame").value(e.callsPerFrame(frames));
            w.name("renderUs").value(e.renderNanos() / 1000);
            w.name("tickUs").value(e.tickNanos() / 1000);
            w.name("shadowUs").value(e.shadowNanos() / 1000);
            w.name("peakFrameUs").value(e.maxFrameNanos() / 1000);
            w.name("gcFrames").value(e.gcFrames());
            w.name("vertices").value(e.vertices());
            w.endObject();
        }
        w.endArray();
    }

    /** Spec §6.2 blocks and sections: mesh work is per session, not per frame, so rates are per second. */
    private static void meshRows(JsonWriter w, List<MeshEntry> rows, double seconds) throws IOException {
        w.beginArray();
        for (MeshEntry e : rows) {
            w.beginObject();
            w.name("key").value(e.key().serialize());
            w.name("label").value(e.key().label());
            if (e.key() instanceof SectionKey s) {
                w.name("dimension").value(s.dimension());
                w.name("x").value(s.x());
                w.name("y").value(s.y());
                w.name("z").value(s.z());
            }
            w.name("calls").value(e.calls());
            w.name("meshUs").value(e.nanos() / 1000);
            w.name("usPerCall").value(e.usPerCall());
            w.name("msPerSecond").value(e.msPerSecond(seconds));
            w.name("callsPerSecond").value(e.callsPerSecond(seconds));
            w.endObject();
        }
        w.endArray();
    }
}
```

`w.name("session")` writes `session.selfMs` in Task 3; it is not here yet on purpose, so Task 3's test fails first.

- [ ] **Step 4: Rewrite `ReportFiles.java`**

Replace the whole file:

```java
package io.github.edwardg117.renderable.report;

import io.github.edwardg117.renderable.core.Fmt;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Writes the text and JSON reports under {@code <gameDirectory>/<reportFolder>/} (spec §6). Pure Java: the
 * game directory and the configuration values are passed in, so spec §8's rule that
 * {@code EnvironmentCollector} is the only Minecraft-aware class in {@code report} holds, and the folder
 * validation below is unit-testable (spec addendum §P4.B6 and §P4.D2).
 */
public final class ReportFiles {

    /** Used when the configured folder is missing or could escape the game directory. */
    public static final String DEFAULT_FOLDER = "renderable";

    /** Writes the JSON report to a writer, so the report is never held in memory as a string. */
    @FunctionalInterface
    public interface JsonBody {
        void writeTo(Writer out) throws IOException;
    }

    /** Where the files went. {@code text} or {@code json} is null when that format is disabled. */
    public record Written(Path folder, Path text, Path json) {
    }

    /** Nothing, or not everything, was written. The caller shows the message to the user. */
    public static final class WriteFailed extends RuntimeException {
        /** What did get written before the failure; never null, though its fields may be. */
        public final Written partial;

        WriteFailed(String message, Written partial, Throwable cause) {
            super(message, cause);
            this.partial = partial;
        }
    }

    private ReportFiles() {
    }

    /**
     * Resolves the configured folder under the game directory. Anything that could write outside it — an
     * absolute path, a root or drive prefix, a {@code ..} segment, a blank name, or a string that is not a
     * path at all — falls back to {@link #DEFAULT_FOLDER}.
     */
    public static Path resolveFolder(Path gameDirectory, String configured) {
        Path fallback = gameDirectory.resolve(DEFAULT_FOLDER);
        if (configured == null || configured.isBlank()) return reject(configured, fallback, "it is blank");
        Path relative;
        try {
            relative = Path.of(configured.trim());
        } catch (InvalidPathException e) {
            return reject(configured, fallback, "it is not a valid path");
        }
        if (relative.isAbsolute() || relative.getRoot() != null) {
            return reject(configured, fallback, "it is absolute");
        }
        for (Path part : relative) {
            if (part.toString().equals("..")) return reject(configured, fallback, "it contains a '..' segment");
        }
        return gameDirectory.resolve(relative).normalize();
    }

    /** Spec addendum §P4.B6 #3: a rejected folder must say so, or the setting is silently ignored. */
    private static Path reject(String configured, Path fallback, String why) {
        LoggerFactory.getLogger("Renderable").warn(
                "Renderable: reportFolder \"{}\" was ignored because {}; writing to {} instead",
                configured, why, fallback);
        return fallback;
    }

    /**
     * Writes whichever formats are enabled and returns where they went.
     *
     * @throws WriteFailed if the folder cannot be created or a file cannot be written
     */
    public static Written write(Path gameDirectory, String configuredFolder, boolean writeText, boolean writeJson,
                                String text, JsonBody json, long wallStartMillis) {
        Path folder = resolveFolder(gameDirectory, configuredFolder);
        String stamp = Fmt.fileStamp(wallStartMillis);
        Path textPath = null;
        Path jsonPath = null;
        try {
            Files.createDirectories(folder);
            if (writeText) {
                textPath = folder.resolve("renderable-" + stamp + ".txt");
                Files.writeString(textPath, text, StandardCharsets.UTF_8);
            }
            if (writeJson) {
                jsonPath = folder.resolve("renderable-" + stamp + ".json");
                try (BufferedWriter out = Files.newBufferedWriter(jsonPath, StandardCharsets.UTF_8)) {
                    json.writeTo(out);
                }
            }
        } catch (IOException e) {
            throw new WriteFailed("could not write reports to " + folder + ": " + e.getMessage(),
                    new Written(folder, textPath, jsonPath), e);
        }
        return new Written(folder, textPath, jsonPath);
    }
}
```

- [ ] **Step 5: Call it from `ProfilerController`**

In `ProfilerController.finish()`, replace everything from `String text = ...` down to the end of the
`catch` block with:

```java
        SessionResult result = s.finish();
        Environment env = EnvironmentCollector.collect(Probe.hooksApplied());
        String text = TextReportWriter.write(result, env, RenderableMod.VERSION);
        ReportFiles.Written written = null;
        String writeFailure = null;
        try {
            written = ReportFiles.write(
                    Minecraft.getInstance().gameDirectory.toPath(),
                    RenderableConfig.REPORT_FOLDER.get(),
                    RenderableConfig.WRITE_TEXT.get(),
                    RenderableConfig.WRITE_JSON.get(),
                    text,
                    out -> JsonReportWriter.write(result, env, RenderableMod.VERSION, out),
                    result.wallStartMillis());
        } catch (ReportFiles.WriteFailed e) {
            RenderableMod.LOGGER.warn("Renderable could not write reports", e);
            written = e.partial;
            writeFailure = e.getMessage();
        } catch (RuntimeException e) {
            RenderableMod.LOGGER.warn("Renderable could not write reports", e);
            writeFailure = e.getMessage();
        }
```

**Both catches are needed, and the second is not optional.** The first exists so a failed JSON write does not
hide the text file that did land — `finishedMessage` can still offer **Open folder**. The second is the
original broad catch, and it is what stops a report-writing problem costing the user the session they just
sampled: `ReportFiles.write` wraps only `IOException`, so a `JsonWriter` nesting bug, a null map key reaching
`w.name(null)`, or a config read before load would otherwise escape `finish()` entirely — past
`lastResult = result`, the overlay update and the chat message — and `finish()` is reached from the
`Probe.Listener` callback on the render thread as well as from `/renderable stop`. Narrowing this to the
`WriteFailed` catch alone was tried during phase 4b and caught in review; do not repeat it.

Guard the two assignments that follow on there being something on disk, because `e.partial.folder()` is
non-null even when nothing was created:

```java
        boolean anythingWritten = written != null && (written.text() != null || written.json() != null);
        if (anythingWritten) lastFolder = written.folder();
```

and have `finishedMessage` show the folder link on `anythingWritten` while printing `writeFailure`
independently, so a partial failure shows both the link and the reason.

The local `String json = JsonReportWriter.write(...)` is deleted: the report now goes straight to the file.
Delete the now-unused `import io.github.edwardg117.renderable.report.JsonReportWriter;`? **No** — it is still
used by the lambda. Leave the imports alone.

- [ ] **Step 5a: Put slf4j on the test classpath**

`resolveFolder`'s warning is the first logging call in this codebase that a unit test ever reaches, and it
fails the suite with `NoClassDefFoundError: org/slf4j/LoggerFactory`. slf4j is on the main compile classpath
through the moddev plugin but was never on the plain JUnit test runtime classpath; `FlywheelCompat` has used
the same idiom since phase 2 without anyone noticing, because no test exercised it.

Add the version beside the other pins in `gradle.properties`:

```properties
# Matches what NeoForge puts on the runtime classpath; check with
# ./gradlew dependencies --configuration runtimeClasspath | grep slf4j
slf4j_version=2.0.9
```

and in `build.gradle`'s `dependencies` block:

```groovy
    testImplementation "org.slf4j:slf4j-api:${slf4j_version}"
```

The version belongs in `gradle.properties` rather than inline because the strict constraint that pins it
lives in NeoForge's metadata, not this build — so a `neo_version` bump can move it, and keeping the two
literals adjacent means that lands in the same review. The blast radius is test-only: main always gets
whatever NeoForge provides.

- [ ] **Step 6: Document the validation in the config**

In `RenderableConfig.java`, replace the `REPORT_FOLDER` definition:

```java
        REPORT_FOLDER = b.comment("Folder under the game directory that reports are written to.",
                        "Must be a relative path with no '..' segment; anything else falls back to 'renderable'.")
                .define("reportFolder", "renderable");
```

- [ ] **Step 7: Run the tests to verify they pass**

```bash
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`, **92 tests** (ReportFilesTest 4, JsonReportWriterTest 9, KeysTest 10).

If `theWriterOverloadProducesExactlyTheStringOverloadsBytes` fails on the leading characters, check that
`setIndent("  ")` is called before the first `beginObject()`.

- [ ] **Step 8: Close the end-to-end gap with a test, not a client launch**

The unit tests so far prove two halves separately: `JsonReportWriter`'s `Writer` overload is byte-identical
to its `String` overload (into a `StringWriter`), and `ReportFiles.write` puts a `JsonBody`'s output on
disk (with a literal body). Nothing yet drives the **real** writer through `ReportFiles` into a **real
file** — which is the path `ProfilerController` now takes, and the one where a streaming writer that is
never flushed or closed would truncate.

That gap does not need a game. Append to `ReportFilesTest`:

```java
    @Test
    void theRealJsonWriterStreamsThroughToAFileThatParses(@TempDir Path game) throws IOException {
        SessionResult r = TestSessions.phase3();
        Environment env = Environment.of(new LinkedHashMap<>(Map.of("minecraft", "1.21.1")), List.of());

        ReportFiles.Written w = ReportFiles.write(game, "renderable", false, true, "",
                out -> JsonReportWriter.write(r, env, "0.4.0", out), 1_726_700_000_000L);

        String onDisk = Files.readString(w.json(), StandardCharsets.UTF_8);
        assertEquals(JsonReportWriter.write(r, env, "0.4.0"), onDisk,
                "the only test that drives the real writer through ReportFiles into a real file: "
                        + "the streamed bytes must equal the in-memory ones");
        assertEquals(4, JsonParser.parseString(onDisk).getAsJsonObject().get("schema").getAsInt());
    }
```

**What this test does and does not guard.** It was drafted with a message claiming a writer left unflushed
would truncate here. It would not, and that was checked empirically rather than assumed: deleting
`JsonReportWriter`'s `w.flush()` leaves the whole suite green, because `ReportFiles` opens the file in a
try-with-resources and `BufferedWriter.close()` flushes unconditionally. So `close()` is what gets the bytes
to disk, and the writer's own `flush()` is belt-and-braces for a future caller that hands in a writer it
does not close — which is exactly what its javadoc promises ("flushed but not closed"). Keep the flush;
do not let this test's existence be read as covering it. What the test genuinely adds is end-to-end byte
parity between the file path and the in-memory path, which neither existing test covers.

It needs `io.github.edwardg117.renderable.core.{Environment, SessionResult, TestSessions}`,
`com.google.gson.JsonParser`, `java.nio.charset.StandardCharsets` and `java.util.{LinkedHashMap, List, Map}`.

**The in-game check moves to Task 7**, the human step. Driving it needs someone to type `/renderable start`
in a running client, which no automated step in this plan can do — and claiming a verification nobody
performed is worse than deferring it. Task 7 already opens the results screen in the real pack; confirming
the chat message still offers **Open folder**, and that `reportFolder = "../escaped"` lands the files in the
instance's own `renderable/` folder, costs nothing extra there.

**What the reviews changed, beyond the steps above.** Three rounds reshaped this task; the code blocks
above are the intent, and the git history is what landed. The differences worth knowing, because later
tasks build on them:

- **The writer is split into `configure(Writer)` and a private `body(JsonWriter, ...)`.** The `String`
  overload runs `body` inside `try (JsonWriter w = configure(out))`, so `close()` runs Gson's completeness
  check and an unbalanced document throws `IOException("Incomplete document")` instead of producing output
  that still parses as *something*. The `Writer` overload cannot close — that would close the caller's file
  and break the "flushed but not closed" contract — so the two share `body`, which is the part a future
  author edits. Verified by deleting one `endObject()`: several tests then fail with `Incomplete document`.
  This is what protects Task 3, which inserts `session.selfMs` into the middle of that object.
- **`ReportFiles.write` catches `IOException | RuntimeException`**, not `IOException` alone. A `JsonWriter`
  nesting bug throws `IllegalStateException` and `w.name(null)` throws `NullPointerException`; wrapping only
  I/O would let those escape unwrapped and cost the user the `[Open folder]` link for a text report already
  on disk — the very case `partial` exists for, uncovered for every cause except I/O.
- **A failed JSON write deletes its own truncated file**, so `Written.json == null` means what callers assume.
- **The two format booleans became `record Formats(boolean text, boolean json)`.** Two adjacent booleans at a
  seven-argument call site are a transposition waiting to happen, and there was exactly one production caller
  at the time to change.

- [ ] **Step 9: Stage and report**

```bash
git add src/main/java src/test/java
git status --short
```

Commit message for the orchestrator:

```
perf(report): stream the JSON instead of building a Gson tree; ReportFiles is pure Java with a validated folder
```

---

### Task 2: Cache the accumulator on the object instead of in a map

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Accumulator.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Session.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/AccumulatorHolder.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/BlockEntityAccumulatorMixin.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/EntityAccumulatorMixin.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/Probe.java`
- Modify: `src/main/resources/renderable.mixins.json`
- Test: `src/test/java/io/github/edwardg117/renderable/core/SessionTest.java` (append)

Spec addendum §P4.B2. `Probe.BY_INSTANCE` keeps every rendered block entity and entity reachable until
`detach()`, which is exactly the window the report's GC figures cover. A `@Unique` field on the object
removes the retention entirely and makes the hot path a field read instead of an `IdentityHashMap.get`.

- [ ] **Step 1: Write the failing test**

Append inside `SessionTest`:

```java
    @Test
    void anAccumulatorKnowsTheSessionThatMadeIt() {
        Session first = session(10_000);
        Session second = session(10_000);
        Accumulator a = first.accumulator(TestSessions.PRESS);

        assertSame(first, a.owner(), "a cache on a long-lived object uses this to spot a stale accumulator");
        assertSame(a, first.accumulator(TestSessions.PRESS), "the same key gives the same accumulator");
        assertNotSame(a, second.accumulator(TestSessions.PRESS), "a new session makes a new one");
        assertNull(new Accumulator(TestSessions.PRESS).owner(), "a standalone accumulator has no session");
    }
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.SessionTest' --no-daemon
```

Expected: compilation failure, `cannot find symbol: method owner()`.

- [ ] **Step 3: Give `Accumulator` its session**

In `Accumulator.java`, add the field beside `type` and thread it through both constructors:

```java
    public final ProfileKey key;
    /** The aggregation shared by every accumulator of this key's type (spec addendum §P4.A2). */
    private final TypeStats type;
    /** The session that created this accumulator, or null for a standalone one (spec addendum §P4.B2). */
    private final Session owner;
```

```java
    /** A standalone accumulator with a type aggregation of its own and no session. */
    public Accumulator(ProfileKey key) {
        this(key, new TypeStats(key.typeId(), key.category()), null);
    }

    Accumulator(ProfileKey key, TypeStats type, Session owner) {
        this.key = key;
        this.type = type;
        this.owner = owner;
    }

    /** The aggregation this key's type shares. Never null. */
    public TypeStats type() {
        return type;
    }

    /**
     * The session that created this accumulator, or null. A hook that caches an accumulator on a
     * Minecraft object compares this with the running session, so an object that survives into the next
     * session is re-keyed rather than credited to the wrong one.
     */
    public Session owner() {
        return owner;
    }
```

In `Session.accumulator(ProfileKey)`, pass `this`:

```java
    public Accumulator accumulator(ProfileKey key) {
        return accumulators.computeIfAbsent(key, k -> new Accumulator(k, typeStats(k), this));
    }
```

- [ ] **Step 4: Write the duck-typed interface**

`src/main/java/io/github/edwardg117/renderable/hooks/AccumulatorHolder.java`:

```java
package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Accumulator;

/**
 * One accumulator slot on a Minecraft object. Implemented onto {@code BlockEntity} and {@code Entity} by
 * mixins so {@link Probe} can cache an accumulator on the object itself rather than in a map that would
 * keep every rendered object alive for the length of a session (spec addendum §P4.B2).
 *
 * <p>The field lives and dies with the object, so there is nothing to clear and nothing is retained. It is
 * written and read on the render thread only.
 */
public interface AccumulatorHolder {

    /** The cached accumulator, or null if this object has not been timed yet. */
    Accumulator renderable$accumulator();

    /** Caches an accumulator on this object. */
    void renderable$accumulator(Accumulator accumulator);
}
```

- [ ] **Step 5: Write the two mixins**

`src/main/java/io/github/edwardg117/renderable/hooks/mixin/BlockEntityAccumulatorMixin.java`:

```java
package io.github.edwardg117.renderable.hooks.mixin;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.hooks.AccumulatorHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * One reference field so {@link io.github.edwardg117.renderable.hooks.Probe} can cache a block entity's
 * accumulator on the block entity (spec addendum §P4.B2). No injector, no behaviour change: the field is
 * null for every block entity until one is rendered or ticked while a session is sampling.
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityAccumulatorMixin implements AccumulatorHolder {

    @Unique
    private Accumulator renderable$accumulator;

    @Override
    public Accumulator renderable$accumulator() {
        return renderable$accumulator;
    }

    @Override
    public void renderable$accumulator(Accumulator accumulator) {
        this.renderable$accumulator = accumulator;
    }
}
```

`src/main/java/io/github/edwardg117/renderable/hooks/mixin/EntityAccumulatorMixin.java`:

```java
package io.github.edwardg117.renderable.hooks.mixin;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.hooks.AccumulatorHolder;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * One reference field so {@link io.github.edwardg117.renderable.hooks.Probe} can cache an entity's
 * accumulator on the entity (spec addendum §P4.B2). No injector, no behaviour change.
 */
@Mixin(Entity.class)
public abstract class EntityAccumulatorMixin implements AccumulatorHolder {

    @Unique
    private Accumulator renderable$accumulator;

    @Override
    public Accumulator renderable$accumulator() {
        return renderable$accumulator;
    }

    @Override
    public void renderable$accumulator(Accumulator accumulator) {
        this.renderable$accumulator = accumulator;
    }
}
```

- [ ] **Step 6: Register them**

`src/main/resources/renderable.mixins.json`'s `client` array becomes:

```json
  "client": [
    "BlockEntityRenderDispatcherMixin",
    "EntityRenderDispatcherMixin",
    "ParticleEngineMixin",
    "BoundTickingBlockEntityMixin",
    "LevelMixin",
    "LevelLightEngineMixin",
    "BufferBuilderMixin",
    "BlockEntityAccumulatorMixin",
    "EntityAccumulatorMixin"
  ],
```

- [ ] **Step 7: Use them in `Probe`**

In `Probe.java`, delete the field:

```java
    private static final IdentityHashMap<Object, Accumulator> BY_INSTANCE = new IdentityHashMap<>(4096);
```

and its `import java.util.IdentityHashMap;`. Delete the two `BY_INSTANCE.clear();` lines in `attach` and
the one in `detach`, and update `detach`'s Javadoc so it no longer claims to clear an instance cache:

```java
    /**
     * The write order matters: ACTIVE goes false first so no new hook call starts, then the session is
     * nulled (a call already past its ACTIVE check sees null and returns), then the class and mesh caches
     * are cleared; a worker still holding a mesh accumulator may put it back, which the owner check in
     * meshBlock/meshFluid tolerates. Accumulators cached on block entities and entities are left where they
     * are: the owner check in {@link #instanceAccumulator} replaces a stale one on first sight.
     */
    public static void detach() {
        ACTIVE = false;
        session = null;
        BY_CLASS.clear();
        MESH_BY_OBJECT.clear();
    }
```

Replace the two `instanceAccumulator` methods:

```java
    /**
     * The accumulator cached on the object, replaced when it came from an earlier session (spec addendum
     * §P4.B2). The cast is safe because {@code BlockEntityAccumulatorMixin} is a required vanilla mixin.
     */
    private static Accumulator instanceAccumulator(Session s, BlockEntity be) {
        AccumulatorHolder holder = (AccumulatorHolder) be;
        Accumulator a = holder.renderable$accumulator();
        if (a == null || a.owner() != s) {
            a = s.accumulator(KeyFactory.blockEntity(be));
            holder.renderable$accumulator(a);
        }
        return a;
    }

    /** As above, for entities; {@code EntityAccumulatorMixin} is likewise required. */
    private static Accumulator instanceAccumulator(Session s, Entity e) {
        AccumulatorHolder holder = (AccumulatorHolder) e;
        Accumulator a = holder.renderable$accumulator();
        if (a == null || a.owner() != s) {
            a = s.accumulator(KeyFactory.entity(e));
            holder.renderable$accumulator(a);
        }
        return a;
    }
```

Finally, update the class Javadoc's second sentence:

```java
 * The single static entry point every hook calls (spec §4). Mixins check {@link #ACTIVE} first, so the idle
 * cost is one volatile read. Accumulators are cached on the Minecraft object itself (block entities,
 * entities, through {@link AccumulatorHolder}) or per class (particles), so the hot path allocates nothing
 * after a key has been seen once and a session never keeps a rendered object alive. Render and tick hooks
 * for the same object share one accumulator.
```

- [ ] **Step 8: Run the tests and prove the mixins apply**

```bash
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`, **93 tests**.

```bash
JAVA_TOOL_OPTIONS="-Dmixin.debug.export=true" ./gradlew runClient -PquickPlay="New World" --no-daemon
```

Once in the world, run `/renderable start 5`, let it finish, then stop the client and check both classes:

```bash
javap -p -cp run/.mixin.out/class net.minecraft.world.level.block.entity.BlockEntity | grep -n 'renderable\$'
javap -p -cp run/.mixin.out/class net.minecraft.world.entity.Entity | grep -n 'renderable\$'
```

Expected from each: one `renderable$accumulator` field and the two `renderable$accumulator` methods. No
`Mixin apply failed` and no `ClassCastException` in `run/logs/latest.log`, and the results screen's Block
Entities and Entities tabs still have rows.

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
git add src/main/java src/main/resources/renderable.mixins.json src/test/java
git status --short
```

Commit message for the orchestrator:

```
perf(hooks): cache accumulators on the object so a session never keeps rendered objects alive
```

---

### Task 3: Throttle the HUD and measure the mod's own in-frame work

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Session.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/SessionResult.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/SamplingHud.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/JsonReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/SessionTest.java` (append)
- Test: `src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java` (append and fix arity)
- Test: `src/test/java/io/github/edwardg117/renderable/report/JsonReportWriterTest.java` (append)

Spec addendum §P4.B3. `HitchDetector.countRolling` sorts 120 frames and then walks **every** frame of the
session, once per rendered frame — 4983 of them by the end of a flight. None of that, nor the once-a-second
Flywheel sample, is in the overhead estimate, which models begin/end pairs only.

- [ ] **Step 1: Write the failing tests**

Append inside `SessionTest`:

```java
    @Test
    void selfTimeIsMeasuredNotEstimated() {
        Session s = session(10_000);
        at(0);  s.onRenderPre(0, 0);
        at(1);  s.beginSelf();
        at(3);  s.endSelf();                 // 2 ms of HUD work
        at(4);  s.beginSelf();
        at(4);  s.endSelf();                 // an instant sample costs nothing
        at(5);  s.endSelf();                 // unpaired end is ignored
        at(16); s.onRenderPre(0, 0);

        SessionResult r = s.finish();
        assertEquals(2 * MS, r.selfNanos());
        assertEquals(0, r.timedCalls(), "self time is not a keyed call and must not inflate the estimate");
    }
```

Append inside `TextReportWriterTest`:

```java
    @Test
    void selfTimeLineIsPrintedWhenThereIsAny() {
        SessionResult none = TestSessions.sample();
        assertFalse(TextReportWriter.write(none, env, "0.4.0").contains("Renderable's own in-frame work"),
                "a session with no self time says nothing");

        SessionResult some = new SessionResult(none.config(), none.wallStartMillis(), none.frames(),
                none.accumulators(), none.stats(), none.hitchThresholdNanos(), none.hitches(),
                none.timedCalls(), none.vertexCalls(), 12_400_000L, none.overheadEstimateMs(),
                none.unbalancedFrames(), none.flywheel(), none.mesh());
        assertTrue(TextReportWriter.write(some, env, "0.4.0").contains(
                "  Renderable's own in-frame work (HUD and Flywheel sampling): 12.40 ms measured, inside other render.\n"),
                TextReportWriter.write(some, env, "0.4.0"));
    }
```

Append inside `JsonReportWriterTest`:

```java
    @Test
    void selfMsIsReported() {
        JsonObject o = parse();
        assertEquals(0.0, o.getAsJsonObject("session").get("selfMs").getAsDouble(), 1e-9);
    }
```

- [ ] **Step 2: Run them to verify they fail**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.SessionTest' --tests 'io.github.edwardg117.renderable.report.*' --no-daemon
```

Expected: compilation failure, `cannot find symbol: method beginSelf`, `method selfNanos`, and the
fifteen-argument `SessionResult` constructor.

- [ ] **Step 3: Measure self time in `Session`**

In `Session.java`, add beside `private long vertexCalls;`:

```java
    private long selfNanos;
    private long selfStartNanos = -1;
```

Add after `vertexCalls()`:

```java
    /**
     * Starts timing work the mod does for itself inside a frame: the sampling HUD and the Flywheel visual
     * sample (spec addendum §P4.B3). It is not attributed to any key or bucket, because it really is part of
     * other render time; the report states it so a reader can subtract it. Pair with {@link #endSelf()}.
     */
    public void beginSelf() {
        selfStartNanos = clock.getAsLong();
    }

    /** Ends a self-timing. An end with nothing open is ignored. */
    public void endSelf() {
        if (selfStartNanos < 0) return;
        selfNanos += clock.getAsLong() - selfStartNanos;
        selfStartNanos = -1;
    }

    /** Nanoseconds the mod spent on its own in-frame work. */
    public long selfNanos() {
        return selfNanos;
    }
```

In `finish()`, pass it through:

```java
        return new SessionResult(config, wallStartMillis, frameList, accumulators(), stats, threshold, hitches,
                timedCalls, vertexCalls, selfNanos, overheadMs, unbalancedFrames, flywheelStats(), mesh.snapshot());
```

In `SessionResult.java`, add `long selfNanos,` immediately after `long vertexCalls,`.

- [ ] **Step 4: Update `TextReportWriterTest.withFlywheel` for the new component**

```java
    /** Rebuilds a SessionResult with a different unbalancedFrames and flywheel, everything else from base. */
    static SessionResult withFlywheel(SessionResult base, FlywheelStats fw, int unbalanced) {
        return new SessionResult(base.config(), base.wallStartMillis(), base.frames(), base.accumulators(),
                base.stats(), base.hitchThresholdNanos(), base.hitches(), base.timedCalls(), base.vertexCalls(),
                base.selfNanos(), base.overheadEstimateMs(), unbalanced, fw, base.mesh());
    }
```

- [ ] **Step 5: Throttle and time the HUD**

Replace `src/main/java/io/github/edwardg117/renderable/ui/SamplingHud.java` entirely:

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

/**
 * One line at the top centre of the screen while a session is sampling (spec §5.2).
 *
 * <p>The rolling hitch count walks every frame recorded so far, which is thousands by the end of a session,
 * so it is recomputed at most four times a second and cached in between (spec addendum §P4.B3). The whole
 * layer is timed into the session's self time, because it runs inside the frame it is measuring.
 */
public final class SamplingHud {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath("renderable", "sampling");
    private static final int ROLLING_WINDOW = 120;
    private static final long REFRESH_MILLIS = 250;

    private static Session countedFor;
    private static long countedAtMillis;
    private static int cachedHitches;

    private SamplingHud() {
    }

    /** Mod bus. */
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, SamplingHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Session s = ProfilerController.get().session();
        if (s == null) {
            countedFor = null;
            return;
        }
        s.beginSelf();
        try {
            Minecraft mc = Minecraft.getInstance();
            SessionConfig c = s.config();
            String text = String.format(Locale.ROOT, "Renderable: %.1f / %.0f s | %d frames | %d hitches",
                    s.elapsedNanos() / 1e9, c.durationSeconds(), s.frameCount(), hitches(s, c));
            int w = mc.font.width(text);
            int x = (g.guiWidth() - w) / 2;
            int y = 4;
            g.fill(x - 3, y - 2, x + w + 3, y + mc.font.lineHeight + 1, 0x90000000);
            g.drawString(mc.font, text, x, y, 0xFFFFFF, true);
        } finally {
            s.endSelf();
        }
    }

    /** The cached rolling count, recomputed when the session changed or the refresh interval has passed. */
    private static int hitches(Session s, SessionConfig c) {
        long now = System.currentTimeMillis();
        if (s != countedFor || now - countedAtMillis >= REFRESH_MILLIS) {
            countedFor = s;
            countedAtMillis = now;
            cachedHitches = HitchDetector.countRolling(s.framesView(), ROLLING_WINDOW,
                    c.hitchMedianMultiplier(), c.hitchMinExcessNanos());
        }
        return cachedHitches;
    }
}
```

- [ ] **Step 6: Time the Flywheel sample**

In `ProfilerController.onFrameStarted`, wrap the sample:

```java
    @Override
    public void onFrameStarted(Session s) {
        long second = s.elapsedNanos() / 1_000_000_000L;
        if (second != lastVisualSampleSecond) {
            lastVisualSampleSecond = second;
            s.beginSelf();
            try {
                sampleFlywheel(s);
            } finally {
                s.endSelf();
            }
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
```

- [ ] **Step 7: Report it**

In `TextReportWriter.write`, add immediately after the overhead line:

```java
        if (r.selfNanos() > 0) {
            sb.append(String.format(Locale.ROOT,
                    "  Renderable's own in-frame work (HUD and Flywheel sampling): %s ms measured, inside other render.\n",
                    Fmt.ms2(r.selfNanos())));
        }
```

In `JsonReportWriter.write`, add immediately after `w.name("vertexCalls").value(r.vertexCalls());`:

```java
        w.name("selfMs").value(r.selfNanos() / 1e6);
```

In `Rows.frames`, add immediately after the `Renderable overhead` row:

```java
        if (r.selfNanos() > 0) {
            rows.add(Row.plain("Renderable self time", Fmt.ms2(r.selfNanos()) + " ms (HUD and Flywheel sampling)"));
        }
```

- [ ] **Step 8: Run the tests to verify they pass**

```bash
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`, **96 tests**.

- [ ] **Step 9: Stage and report**

```bash
git add src/main/java src/test/java
git status --short
```

Commit message for the orchestrator:

```
perf(ui): throttle the HUD's rolling hitch count; measure and report the mod's own in-frame work
```

---

### Task 4: Failure latches on the third-party call sites

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/compat/IrisCompat.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/compat/FlywheelCompat.java`

Spec addendum §P4.B4 and §P4.B5. `Probe.markShadow` calls `IrisCompat.shadowActive()` **before**
`s.begin(...)`. A `LinkageError` from Iris there propagates out of `beginBlockEntity` while the mixin's
`finally` still calls `end()`, which closes the caller's call instead and unbalances the attribution stack
for the rest of the frame. There is nothing to unit-test — the failure needs a broken Iris — so the proof is
the code review and the audit table in §P4.B5.

- [ ] **Step 1: Give `IrisCompat` the latch `FlywheelCompat` already has**

Replace the whole file:

```java
package io.github.edwardg117.renderable.hooks.compat;

import io.github.edwardg117.renderable.core.Environment;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.neoforged.fml.ModList;
import org.slf4j.LoggerFactory;

/**
 * Spec §4.2: Iris renders block entities, entities and particles a second time in its shadow pass. The
 * shadow flag is a static read on Iris's side, so a timed call can be tagged as shadow work for the
 * {@code shadowNanos} column. Iris classes are referenced only inside {@link Impl}, which is loaded on first
 * use and only when {@link #PRESENT}.
 *
 * <p>Spec addendum §P4.B4: {@link #shadowActive()} is called on the hot path before a timed call is opened,
 * so it must never throw. A {@code LinkageError} after an Iris version bump would otherwise leave the
 * attribution stack unbalanced for the rest of the frame. One failure disables the shadow column for the
 * rest of the client run, which is spec §4.5's policy: degrade to missing data, never crash.
 */
public final class IrisCompat {
    public static final boolean PRESENT = ModList.get().isLoaded("iris");

    /** Set once Iris's internals stopped matching; the shadow column then stays off for this client run. */
    private static volatile boolean broken;

    private IrisCompat() {
    }

    /** True while Iris is rendering its shadow pass; always false without Iris, or after a failure. */
    public static boolean shadowActive() {
        if (!PRESENT || broken) return false;
        try {
            return Impl.shadowActive();
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    /** The active shader pack name, "none" when shaders are off, "absent" without Iris. */
    public static String packName() {
        if (!PRESENT) return Environment.ABSENT;
        if (broken) return "unknown";
        try {
            return Impl.packName();
        } catch (Throwable t) {
            fail(t);
            return "unknown";
        }
    }

    private static void fail(Throwable t) {
        broken = true;
        LoggerFactory.getLogger("Renderable")
                .warn("Iris integration failed; the shadow column is disabled for this client run", t);
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

`Environment.ABSENT` is added in Task 5. **If Task 5 has not run yet**, add it now — it is three lines and
Task 5 is written to skip it if it is present:

```java
    /** The value written for a mod or feature that is not installed. */
    public static final String ABSENT = "absent";
```

on `core/Environment.java`, inside the record body.

- [ ] **Step 2: Give `FlywheelCompat.backendName` the same guard**

`FlywheelCompat.sample` already catches `Throwable` and latches. `backendName()` does not: it is safe only
because `EnvironmentCollector.safe` wraps it, which a future caller might not. Replace the method:

```java
    /** The active backend id such as {@code flywheel:instancing}, or "absent" without Flywheel. */
    public static String backendName() {
        if (!PRESENT) return Environment.ABSENT;
        if (broken) return "unknown";
        try {
            return Impl.backendName();
        } catch (Throwable t) {
            broken = true;
            LoggerFactory.getLogger("Renderable").warn("Flywheel backend lookup failed; disabled for this client run", t);
            return "unknown";
        }
    }
```

and add `import io.github.edwardg117.renderable.core.Environment;` to the file.

- [ ] **Step 3: Compile and launch**

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 96 tests.

```bash
./gradlew runClient -PquickPlay="New World" -PpackMods --no-daemon
```

Expected: the client reaches the world, and the report's Environment block still shows `iris:` and
`shaderPack:` lines (`none`, since no pack is enabled in the dev client) and `flywheelBackend:`. No
"integration failed" warning in `run/logs/latest.log`. Stop the client with Task 2's Step 9 filter.

- [ ] **Step 4: Stage and report**

```bash
git add src/main/java/io/github/edwardg117/renderable/hooks/compat src/main/java/io/github/edwardg117/renderable/core/Environment.java
git status --short
```

Commit message for the orchestrator:

```
fix(hooks): latch Iris and Flywheel failures so a version bump cannot unbalance the attribution stack
```

---

### Task 5: The five small correctness defects

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Environment.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/EnvironmentCollector.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/Probe.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/FrameEvents.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/WorldOverlay.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/ResultsScreen.java`
- Create: `src/test/java/io/github/edwardg117/renderable/core/EnvironmentTest.java`

Spec addendum §P4.B6 items 1, 2, 4 and 5 (item 3 and 6 were done in Task 1).

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/io/github/edwardg117/renderable/core/EnvironmentTest.java`:

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentTest {

    @Test
    void entriesAreCopiedInOrderAndCannotBeMutatedThroughTheCaller() {
        LinkedHashMap<String, String> source = new LinkedHashMap<>();
        source.put("minecraft", "1.21.1");
        source.put("neoforge", "21.1.220");
        source.put("java", "21.0.3");
        Environment env = Environment.of(source, List.of("BLOCK_ENTITY_RENDER"));

        source.put("gpu", "added after the fact");
        assertEquals(3, env.entries().size(), "the record kept its own copy");
        assertEquals(List.of("minecraft", "neoforge", "java"), List.copyOf(env.entries().keySet()),
                "insertion order is what the text report prints, so the copy must keep it");
        assertThrows(UnsupportedOperationException.class, () -> env.entries().put("x", "y"));
        assertThrows(UnsupportedOperationException.class, () -> env.hooksApplied().add("x"));
    }

    @Test
    void absentIsNamedAndTestable() {
        assertEquals("absent", Environment.ABSENT);
        Environment env = Environment.of(new LinkedHashMap<>(Map.of(
                "sodium", "0.6.13", "iris", Environment.ABSENT)), List.of());
        assertTrue(env.has("sodium"));
        assertFalse(env.has("iris"), "absent means the mod is not installed");
        assertFalse(env.has("flywheel"), "a key that was never collected is not present either");
        assertEquals("0.6.13", env.value("sodium"));
        assertEquals(Environment.ABSENT, env.value("flywheel"));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew test --tests 'io.github.edwardg117.renderable.core.EnvironmentTest' --no-daemon
```

Expected: compilation failure on `Environment.has`, `Environment.value`, and (unless Task 4 added it)
`Environment.ABSENT`; then the order test failing because `of` does not copy `entries`.

- [ ] **Step 3: Fix `Environment`**

Replace the whole file:

```java
package io.github.edwardg117.renderable.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain environment description for reports (spec §6.1 item 2). Built by the Minecraft-aware collector in
 * {@code report}; the core only carries it. Entries keep insertion order, which is the order the text
 * report prints them in.
 */
public record Environment(Map<String, String> entries, List<String> hooksApplied) {

    /** The value the collector writes for a mod or feature that is not installed. */
    public static final String ABSENT = "absent";

    /**
     * Copies both collections so the record is really immutable. The entries copy is a
     * {@link LinkedHashMap}, not {@code Map.copyOf}, because {@code Map.copyOf} does not keep order.
     */
    public static Environment of(Map<String, String> entries, List<String> hooksApplied) {
        return new Environment(Collections.unmodifiableMap(new LinkedHashMap<>(entries)), List.copyOf(hooksApplied));
    }

    public static Environment empty() {
        return new Environment(Map.of(), List.of());
    }

    /** True when {@code key} was collected and is not {@link #ABSENT}. */
    public boolean has(String key) {
        String v = entries.get(key);
        return v != null && !ABSENT.equals(v);
    }

    /** The collected value for {@code key}, or {@link #ABSENT} when it was not collected. */
    public String value(String key) {
        return entries.getOrDefault(key, ABSENT);
    }
}
```

- [ ] **Step 4: Use the constant instead of the literal**

In `report/EnvironmentCollector.java`, replace `.orElse("absent")` with `.orElse(Environment.ABSENT)`.

In `report/TextReportWriter.appendFlywheel`, replace the first two lines of the method body:

```java
        String backend = env.value("flywheelBackend");
        if (Environment.ABSENT.equals(backend)) {
```

**If plan 4c has already run**, those two lines are gone: `appendFlywheel` starts with
`EmptyStates.flywheel(env, fw.samples())` and already reads the backend with `env.value(...)`, so there is
nothing to change here. Say so in your report rather than inventing an edit.

- [ ] **Step 5: Notice when a kept result's world has gone**

In `Probe.java`, add a method to the `Listener` interface:

```java
        /** The player left the world; a result kept in memory now describes a world that is gone. */
        void onWorldGone();
```

In `FrameEvents.java`, replace `onLoggingOut` and `onLevelUnload`:

```java
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        discard("logged out");
        worldGone();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        discard("level unloaded");
        worldGone();
    }

    private static void worldGone() {
        Probe.Listener l = Probe.listener();
        if (l != null) l.onWorldGone();
    }
```

In `ProfilerController.java`, add the field beside `private SessionResult lastResult;`:

```java
    private boolean resultWorldGone;
```

add the accessor beside `lastResult()`:

```java
    /**
     * True when the last result was taken in a world the client has since left, so its positions and entity
     * ids no longer mean anything (spec addendum §P4.B6). The report itself is still worth copying.
     */
    public boolean resultWorldGone() {
        return resultWorldGone;
    }
```

set it false in `finish()`, immediately after `lastResult = result;`:

```java
        resultWorldGone = false;
```

and implement the new listener method beside `onDiscardRequested`:

```java
    @Override
    public void onWorldGone() {
        resultWorldGone = true;
    }
```

- [ ] **Step 6: Stop `setResult` overriding `/renderable overlay off`**

In `ui/WorldOverlay.java`, replace the field declaration:

```java
    /** Null until first read, then whatever the config said or the user last chose. */
    private static Boolean enabled;
```

delete the last line of `setResult`:

```java
        enabled = RenderableConfig.OVERLAY_ENABLED.get();
```

and replace the three accessors:

```java
    /**
     * Whether labels are drawn. Read from the config on first use, not at every session end, so
     * {@code /renderable overlay off} survives the next session (spec addendum §P4.B6).
     */
    public static boolean isEnabled() {
        Boolean e = enabled;
        if (e == null) {
            e = RenderableConfig.OVERLAY_ENABLED.get();
            enabled = e;
        }
        return e;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static void toggle() {
        setEnabled(!isEnabled());
    }
```

Then replace the two remaining reads of the raw field with the accessor: in `onRenderLevelStage`,
`boolean wantLabels = enabled && !labels.isEmpty();` becomes `boolean wantLabels = isEnabled() && !labels.isEmpty();`,
and in `render`, `if (enabled && !labels.isEmpty()) {` becomes `if (isEnabled() && !labels.isEmpty()) {`.

- [ ] **Step 7: Disable world actions when the world has gone**

In `ui/screen/ResultsScreen.updateButtons`:

```java
    private void updateButtons() {
        boolean worldGone = ProfilerController.get().resultWorldGone();
        boolean hasPos = selected != null && selected.pos() != null;
        boolean hasEntity = selected != null && selected.entityId() >= 0;
        highlightButton.active = hasPos && !worldGone;
        teleportButton.active = (hasPos || hasEntity) && !worldGone;
        sortButton.visible = tab.sortable();
    }
```

**If plan 4c has already run**, it deleted the Sort button, so drop the last line — keeping it would not
compile:

```java
    private void updateButtons() {
        boolean worldGone = ProfilerController.get().resultWorldGone();
        boolean hasPos = selected != null && selected.pos() != null;
        boolean hasEntity = selected != null && selected.entityId() >= 0;
        highlightButton.active = hasPos && !worldGone;
        teleportButton.active = (hasPos || hasEntity) && !worldGone;
    }
```

- [ ] **Step 8: Run the tests to verify they pass**

```bash
./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`, **98 tests**.

- [ ] **Step 9: Compile and check the two behaviours in the client**

```bash
./gradlew runClient -PquickPlay="New World" --no-daemon
```

- Run `/renderable overlay off`, then `/renderable start 5`. When it finishes, no labels appear. Run
  `/renderable overlay on` and they do.
- Run another session, then disconnect to the title screen and reconnect to the same world. Open
  `/renderable results`: **Highlight** and **Teleport** are greyed out, **Copy report** still works.

Stop the client with Task 2's Step 9 filter.

- [ ] **Step 10: Stage and report**

```bash
git add src/main/java src/test/java
git status --short
```

Commit message for the orchestrator:

```
fix: defensive Environment copy, named absent sentinel, overlay toggle and stale-world guards
```

---

### Task 6: README and spec amendments

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md`

- [ ] **Step 1: Update `README.md`**

In the "Using it" list, item 3, replace the last sentence with:

```markdown
`/renderable overlay off` hides them and stays off until you turn it back on.
```

In "Reading a report", after the "Hooks applied" bullet, add:

```markdown
- **Renderable's own in-frame work** is measured, not estimated: it is the sampling HUD plus the
  once-a-second Flywheel visual sample, and it sits inside **other render**. The in-world overlay is
  cleared when a session starts, so it contributes nothing; a highlight left running from a previous
  session draws twelve projected edges for ten seconds and is not measured.
```

In "Limits", replace `while sampling, the report states its own overhead, which covers both timed calls and vertex counting,` with:

```markdown
while sampling, the report states its own overhead, which covers both timed calls and vertex counting, and
separately the measured cost of its own HUD and sampling,
```

In "Report files", add above the schema 4 paragraph:

```markdown
The JSON is streamed to the file a row at a time and is never held in memory as a tree or as a string, so a
long flight costs disk space rather than heap. Every array is still complete: nothing is truncated to
top-N. `session.selfMs` was added alongside schema 4's fields; it is additive, so a schema 4 reader that
ignores unknown fields is unaffected.
```

Also add, in "Reading a report", after the `verts/f` bullet:

```markdown
- The `reportFolder` config value must be a relative path with no `..` segment. Anything else falls back to
  `renderable` and the log says so.
```

- [ ] **Step 2: Apply the spec amendments**

In `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md`:

1. **§3.5**, add a bullet:

```markdown
- The mod's own in-frame work — the sampling HUD and the once-a-second Flywheel visual sample — is timed
  rather than estimated and reported separately; it is part of other render time, not of any bucket.
```

2. **§4**, after the paragraph beginning "All mixins call into one static `Probe`", add:

```markdown
Block entity and entity accumulators are cached on the object itself through a `@Unique` field reached by a
duck-typed interface, not in a map, so a running session never keeps a rendered object alive and cannot
distort the GC figures it reports. The accumulator carries the session that created it, so an object that
survives into the next session is re-keyed on first sight.
```

3. **§6.2**, after the JSON example, add:

```markdown
The JSON is streamed to the file a row at a time; the report is never held in memory as a tree or as a
string. Every array is complete, as above.
```

- [ ] **Step 3: Build**

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, 98 tests, `build/libs/renderable-0.4.0.jar`.

- [ ] **Step 4: Stage and report**

```bash
git add README.md docs/superpowers/specs
git status --short
```

Commit message for the orchestrator:

```
docs: README and spec for phase 4b
```

---

### Task 7: In-pack acceptance (human step)

**Files:**
- Create: `docs/acceptance/phase-4b.md`

The user runs this in the real Gears & Frontiers instance with the jar built from this plan's tip. The
orchestrator writes the log from what comes back, has it reviewed, and commits it.

1. **GC figures (§P4.B2).** Run a 30 s session at the base, at the same place as run 1 of the phase 2/3
   log. Paste the `GC:` line and compare it with that log's `15 / 369 ms` and with plan 4a's run at the same
   place. The expectation is "no higher", not an exact number: the scene is live and the comparison is
   indicative. Also confirm the Block Entities and Entities tables still have the same shape of content —
   this is the check that the two new vanilla mixins did not break the instance cache.
2. **JSON (§P4.B1).** Fly into unloaded terrain for 30 s. Paste the size of the `.json` file and the first
   two lines of it, and confirm it still parses (open it in any editor that validates JSON, or run
   `python -m json.tool <file> > NUL`). The size should be close to phase 3's 4.86 MB for a comparable
   flight.
3. **Self time (§P4.B3).** Paste the `Renderable's own in-frame work` line from both sessions. It must be
   smaller than the `other render` figure in the same report's time split.
4. **Report folder (§P4.B6).** Set `reportFolder = "../escaped"` in the instance's
   `config/renderable-client.toml`, run a short session, and confirm the files land in the instance's
   `renderable/` folder and nothing was written outside it.
5. **Overlay toggle (§P4.B6).** `/renderable overlay off`, run a session, confirm no labels; `/renderable
   overlay on`, confirm they appear.
6. **Stale world (§P4.B6).** Run a session, disconnect to the title screen, reconnect, `/renderable
   results`: Highlight and Teleport greyed out, Copy report still working.
7. **Stability.** No `Attribution stack reset`, no crash, no `ClassCastException` in `logs/latest.log`.

Then the orchestrator:

```bash
git add docs/acceptance/phase-4b.md
git commit -m "docs: phase 4b acceptance log"
```

---

## Notes for the orchestrator

- Tasks 1, 3 and 5 are mostly JUnit; Tasks 2 and 4 need a dev client launch.
- Task 2 adds two **required** vanilla mixins. If either fails to apply, the client must fail loudly (spec
  §4.5) — do not be tempted to make them `require = 0`, because `Probe`'s cast depends on them.
- **Reviewer checklist per task:** no allocation added to `Probe.instanceAccumulator` or `Session.end()`;
  `report` free of Minecraft imports except `EnvironmentCollector` (the new test enforces it); every
  third-party call reachable from the render thread inside a `try`/`catch (Throwable)` with a latch;
  `JsonWriter` balanced — every `beginObject`/`beginArray` has its `end`, which a failed parse in the tests
  would catch; `Environment.of` using `LinkedHashMap`, never `Map.copyOf`.
- The §P4.B5 audit table is the review artefact for Task 4; walk it row by row against the code.
