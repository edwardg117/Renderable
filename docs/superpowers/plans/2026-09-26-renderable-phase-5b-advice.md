# Renderable Phase 5b Implementation Plan: recommendations

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn a session into advice. Players get settings to change, costly things in view, and memory and launch arguments. Pack authors get mods to look at and stalls to profile. Each piece of advice carries the numbers behind it, and the advice leads the report, a new Advice tab, the chat line and the JSON.

**Architecture:**

- **A pure rule engine** in `core.advice`:
  - `Facts` derives what the rules need from a finished `SessionResult` and its `Environment`;
  - four themed classes hold one static method per rule;
  - `Advisor` runs them and ranks the result.

  No Minecraft imports, so every rule is unit-tested against `Facts` built directly in tests.
- **Five new environment facts:** physical memory, the collector, the initial heap, the live heap after
  collection, and whitelisted JVM flags. A `GcWatcher` listens to the JVM's collection notifications during a
  session.
- **Pause-only GC sampling.** `GcSampler` counts only a collector's `Pauses` beans when it has them.
- **Presentation.** The report, the new Advice tab (the screen opens on it), the chat line and the JSON each
  present the advice.

**Tech Stack:** Java 21, `java.lang.management` and `com.sun.management` (JDK 21), NeoForge 21.1.220, Gson's streaming writer, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md` §P5.B (all of it), and the base spec sections that §P5.3 assigns to plan 5b.

**Repository:** `H:\repos\Renderable`. Create the branch `phase-5b-advice` from `phase-5a-attribution` (plan 5a is complete on it) before Task 1. **Subagents stage and report; they do not commit.**

**Ordering:** runs after 5a. It reads 5a's frame components, `ModKey`, the `MOD` category and named time.

**Verified facts you can rely on:**

- **Collector bean names on JDK 21.0.11** (checked 2026-09-26):
  - G1: `G1 Young Generation`, `G1 Concurrent GC`, `G1 Old Generation`. `G1 Concurrent GC` counts only the
    Remark and Cleanup pauses.
  - ZGC: `ZGC Cycles`, `ZGC Pauses`.
  - ZGC with `-XX:+ZGenerational`: `ZGC Minor Cycles`, `ZGC Minor Pauses`, `ZGC Major Cycles`, `ZGC Major Pauses`.
  - Shenandoah: `Shenandoah Pauses`, `Shenandoah Cycles`.
  - Parallel and Serial (standard names): `PS Scavenge` and `PS MarkSweep`; `Copy` and `MarkSweepCompact`.
- **Memory MXBeans.**
  - `((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize()`
    gives physical memory in bytes (63100 MiB on the dev machine).
  - Heap pools are the `MemoryPoolMXBean`s whose `getType()` is `MemoryType.HEAP`.
  - `GarbageCollectorMXBean`s are `javax.management.NotificationEmitter`s.
  - `com.sun.management.GarbageCollectionNotificationInfo.from(CompositeData)`, `getGcInfo()` and
    `getMemoryUsageAfterGc()` give each pool's usage after a collection.
- **Environment values the rules read,** as `EnvironmentCollector` writes them (from a real report):
  `java: 21.0.3 (Eclipse Adoptium)`, `maxHeapMiB: 8192`, `renderDistance: 10`, `biomeBlend: 2` (the radius:
  2 is 5x5), `vsync: false`, `fpsLimit: 220` (260 means unlimited), `particles: ALL` (or `DECREASED`,
  `MINIMAL`), `entityDistance: 5.0` (1.0 is 100%), and `flywheelBackend: flywheel:indirect` (the off backend is
  `flywheel:off`). `shaderPack` reads `absent` without Iris, `none` with no pack in use, `unknown` when the
  lookup failed, and otherwise the pack's name.
- **5a's pieces:** `FrameRecord.tasksNanos/capNanos/named(Window)`, `FrameStats.avgTasksNanos/avgCapNanos/avgNamed`,
  `ModKey` with `Kind { EVENT, LAYER, PACKET }`, `ProfileKey.Category.MOD`, `TestSessions.phase5()`,
  `Environment.EVENT_TIMING` and `Environment.UNAVAILABLE`.
- **The suite is 160 tests** after 5a.

**Deviations from the spec addendum, recorded here and applied to it in Task 8:**

1. **Rules are methods, not classes.** Four themed classes, `SettingsRules`, `SceneRules`, `MemoryRules` and
   `PackRules`, hold one static method per rule, instead of one class per rule. Each method is still a small
   unit with its own tests.
2. **`mod-handlers` also has a stutter form.** It fires when one of the mod's handlers took at least 50 ms and
   at least half of a hitch, or when one of them was a top cost of several hitches by `hitch-repeat`'s test
   (deviation 6).
   - *Why.* Since 5a names that time, `unattributed-stall` no longer fires for it. A single 370 ms stall
     averages far below `mod-handlers`' 0.5 ms a frame, and a handler that takes 20 to 40 ms of many hitches
     is below the stall form too, while `hitch-repeat` leaves mod keys out (deviation 3). Without this form
     the advice would be silent about exactly the stalls phase 5 exists to explain.
3. **`hitch-repeat` leaves out `MOD` keys.** The mod rules cover those, and `hitch-repeat` is for the player.
4. **The `mod-*` rules skip `minecraft`, `neoforge` and `unknown`.** Vanilla HUD layers and packets appear in
   the Mods tab but are not something a pack author can change; neither are NeoForge's own handlers, nor
   handlers whose mod could not be found.
5. **What a stutter finding's `msPerFrame` holds.** Every finding's `msPerFrame` is the at-stake figure of
   §P5.B2's table averaged over the session: the time in hitches for `hitch-repeat` and `unattributed-stall`,
   all GC time for `heap-vs-ram` and `gc-pauses`, and the steady cost for `chunk-rebuilds`, `mod-handlers`
   and `mod-network`. Stutter ranks by `worstMs` whatever it is, so a stall ranks first even when it is
   small on average. A note's is 0.
6. **A hitch counts toward a repeat only when the key took at least a quarter of it, and never when a GC
   pause could account for it** (the report's `[gc]` mark). Every hitch's top list also holds the frame's
   usual costs, so without the floor a machine that costs 2 ms every frame would read as repeated stutter.
   The same floor and GC rule apply to `hitch-repeat`, `mod-network` and `mod-handlers`, and
   `unattributed-stall` takes the frame's GC pause off the time it calls untimed.
7. **`liveHeapAfterGcMiB` comes from the collectors' non-pause beans.** A ZGC or Shenandoah pause notification
   reports no heap in use in any pool (measured on JDK 21.0.11), so listening to it would pin the minimum at
   0. A notification whose heap sum is 0 is ignored as well.

---

## File map

```
README.md                                                     Modify (Task 8)
docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md   Modify (Task 8)
docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md        Modify: deviations above (Task 8)
src/main/java/io/github/edwardg117/renderable/
  core/GcNames.java                                           Create (Task 1)
  core/JvmFlags.java                                          Create (Task 2)
  core/Environment.java                                       Modify: five keys (Task 2)
  core/EmptyStates.java                                       Modify: advice (Task 6)
  core/advice/Facts.java                                      Create (Task 3)
  core/advice/Recommendation.java                             Create (Task 4)
  core/advice/Thresholds.java                                 Create (Task 4)
  core/advice/Words.java                                      Create (Task 4)
  core/advice/SettingsRules.java                              Create (Task 4)
  core/advice/SceneRules.java                                 Create (Task 4)
  core/advice/MemoryRules.java                                Create (Task 5)
  core/advice/PackRules.java                                  Create (Task 5)
  core/advice/Advisor.java                                    Create (Task 6)
  hooks/GcSampler.java                                        Modify: pause beans only (Task 1)
  hooks/GcWatcher.java                                        Create (Task 2)
  report/EnvironmentCollector.java                            Modify: five facts (Task 2)
  report/TextReportWriter.java                                Modify: Recommendations section (Task 6)
  report/JsonReportWriter.java                                Modify: recommendations array (Task 6)
  ProfilerController.java                                     Modify: GcWatcher, chat line (Tasks 2, 6)
  ui/screen/TextWrap.java                                     Create (Task 7)
  ui/screen/Tab.java                                          Modify: ADVICE (Task 7)
  ui/screen/Rows.java                                         Modify: advice, wrap parameter (Task 7)
  ui/screen/ResultsScreen.java                                Modify: opens on Advice (Task 7)
src/test/java/io/github/edwardg117/renderable/
  core/GcNamesTest.java                                       Create (Task 1)
  core/JvmFlagsTest.java                                      Create (Task 2)
  core/advice/FactsBuilder.java                               Create (Task 3)
  core/advice/FactsTest.java                                  Create (Task 3)
  core/advice/SettingsRulesTest.java                          Create (Task 4)
  core/advice/SceneRulesTest.java                             Create (Task 4)
  core/advice/MemoryRulesTest.java                            Create (Task 5)
  core/advice/PackRulesTest.java                              Create (Task 5)
  core/advice/AdvisorTest.java                                Create (Task 6)
  report/TextReportWriterTest.java                            Modify (Task 6)
  report/JsonReportWriterTest.java                            Modify (Task 6)
  ui/screen/TextWrapTest.java                                 Create (Task 7)
  ui/screen/TabTest.java                                      Modify (Task 7)
```

**Expected test totals:** 160 at the start, 162 after Task 1, 163 after Task 2, 165 after Task 3, 175 after
Task 4, 183 after Task 5, 188 after Task 6, 190 after Task 7, and 190 at the end.

**How to run the suite:** `./gradlew test --rerun --no-daemon` (always forced; the build cache can restore stale
results).

---

### Task 1: Count pauses, not concurrent cycles

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/GcNames.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/GcSampler.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/GcNamesTest.java`

Spec addendum §P5.B4. The name logic is pure and lives in `core`; `GcSampler` asks it which beans to sum. The
same class names the collector for the environment header, and picks the beans `GcWatcher` listens to, in
Task 2.

- [ ] **Step 1: Write the failing tests**

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GcNamesTest {
    static final List<String> G1 = List.of("G1 Young Generation", "G1 Concurrent GC", "G1 Old Generation");
    static final List<String> ZGC = List.of("ZGC Cycles", "ZGC Pauses");
    static final List<String> GENERATIONAL_ZGC =
            List.of("ZGC Minor Cycles", "ZGC Minor Pauses", "ZGC Major Cycles", "ZGC Major Pauses");
    static final List<String> SHENANDOAH = List.of("Shenandoah Pauses", "Shenandoah Cycles");

    @Test
    void pauseBeansAreCountedAndTheOthersReportTheHeap() {
        assertEquals(G1, GcNames.countedBeans(G1), "G1's concurrent bean counts the Remark and Cleanup pauses");
        assertEquals(List.of("ZGC Pauses"), GcNames.countedBeans(ZGC));
        assertEquals(List.of("ZGC Minor Pauses", "ZGC Major Pauses"), GcNames.countedBeans(GENERATIONAL_ZGC));
        assertEquals(List.of("Shenandoah Pauses"), GcNames.countedBeans(SHENANDOAH));
        assertEquals(List.of("PS Scavenge", "PS MarkSweep"), GcNames.countedBeans(List.of("PS Scavenge", "PS MarkSweep")));

        // A pause bean's notification reports no heap in use, so the live-heap figure listens to the others.
        assertEquals(G1, GcNames.liveHeapBeans(G1));
        assertEquals(List.of("ZGC Cycles"), GcNames.liveHeapBeans(ZGC));
        assertEquals(List.of("ZGC Minor Cycles", "ZGC Major Cycles"), GcNames.liveHeapBeans(GENERATIONAL_ZGC));
        assertEquals(List.of("Shenandoah Cycles"), GcNames.liveHeapBeans(SHENANDOAH));
    }

    @Test
    void theCollectorIsNamedFromItsBeans() {
        assertEquals("G1", GcNames.collector(G1));
        assertEquals("ZGC", GcNames.collector(ZGC));
        assertEquals("ZGC generational", GcNames.collector(GENERATIONAL_ZGC));
        assertEquals("Shenandoah", GcNames.collector(SHENANDOAH));
        assertEquals("Parallel", GcNames.collector(List.of("PS Scavenge", "PS MarkSweep")));
        assertEquals("Serial", GcNames.collector(List.of("Copy", "MarkSweepCompact")));
        assertEquals("Epsilon, Other", GcNames.collector(List.of("Epsilon", "Other")), "anything else is listed as it is");
    }
}
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*GcNamesTest'`
Expected: compilation failure, `cannot find symbol: variable GcNames`.

- [ ] **Step 3: Create `GcNames`**

```java
package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the JVM's garbage-collector bean names (spec addendum §P5.B4). ZGC and Shenandoah publish separate
 * beans for their concurrent cycles and for their pauses, and a cycle bean's time is concurrent work, not a
 * pause. So when a collector has pause beans, only those count. G1's three beans are all pauses: its
 * concurrent bean counts the Remark and Cleanup pauses.
 */
public final class GcNames {
    private static final String PAUSES = " Pauses";

    private GcNames() {
    }

    /** The beans whose time and count are pauses: those ending in " Pauses" if any do, otherwise all of them. */
    public static List<String> countedBeans(List<String> names) {
        List<String> pauses = new ArrayList<>();
        for (String n : names) {
            if (n.endsWith(PAUSES)) pauses.add(n);
        }
        return pauses.isEmpty() ? List.copyOf(names) : List.copyOf(pauses);
    }

    /**
     * The beans whose collection notifications report the heap in use afterwards: every bean but the pause beans.
     * A ZGC or Shenandoah pause notification reports 0 used in every pool (measured on JDK 21.0.11).
     */
    public static List<String> liveHeapBeans(List<String> names) {
        List<String> out = new ArrayList<>();
        for (String n : names) {
            if (!n.endsWith(PAUSES)) out.add(n);
        }
        return List.copyOf(out);
    }

    /** The collector in use, for the report: G1, ZGC, ZGC generational, Shenandoah, Parallel, Serial, or the names. */
    public static String collector(List<String> names) {
        if (names.stream().anyMatch(n -> n.startsWith("G1 "))) return "G1";
        if (names.stream().anyMatch(n -> n.startsWith("ZGC Minor") || n.startsWith("ZGC Major"))) return "ZGC generational";
        if (names.stream().anyMatch(n -> n.startsWith("ZGC"))) return "ZGC";
        if (names.stream().anyMatch(n -> n.startsWith("Shenandoah"))) return "Shenandoah";
        if (names.contains("PS Scavenge") || names.contains("PS MarkSweep")) return "Parallel";
        if (names.contains("Copy") || names.contains("MarkSweepCompact")) return "Serial";
        return String.join(", ", names);
    }
}
```

- [ ] **Step 4: Make `GcSampler` sum only those beans**

Replace the whole of `GcSampler.java` with:

```java
package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.GcNames;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Sums collection count and pause time over the collectors' pause beans (spec addendum §P5.B4): all of them
 * under G1, Parallel and Serial, and only the {@code Pauses} beans under ZGC and Shenandoah, whose
 * {@code Cycles} beans time concurrent work. Cheap enough to call once per frame.
 */
public final class GcSampler {
    private final List<GarbageCollectorMXBean> beans;
    private long count;
    private long millis;

    public GcSampler() {
        List<GarbageCollectorMXBean> all = ManagementFactory.getGarbageCollectorMXBeans();
        List<String> counted = GcNames.countedBeans(all.stream().map(GarbageCollectorMXBean::getName).toList());
        beans = all.stream().filter(b -> counted.contains(b.getName())).toList();
    }

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

- [ ] **Step 5: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **162** tests.

- [ ] **Step 6: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/GcNames.java src/main/java/io/github/edwardg117/renderable/hooks/GcSampler.java src/test/java/io/github/edwardg117/renderable/core/GcNamesTest.java
```

Report for the commit: `fix(hooks): count GC pauses, not ZGC and Shenandoah concurrent cycles`.

---

### Task 2: Five new environment facts

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/JvmFlags.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Environment.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/GcWatcher.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/EnvironmentCollector.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/JvmFlagsTest.java`

Spec addendum §P5.B3. The whitelist is the privacy boundary. `RuntimeMXBean.getInputArguments()` carries
library paths and the player's user name in them, so nothing outside the list may reach a report, and the list
is tested in `core`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JvmFlagsTest {

    @Test
    void onlyHeapAndCollectorFlagsSurviveAndNeverAPath() {
        List<String> args = List.of(
                "-Xmx8G", "-Xms4G", "-Xss2m",
                "-XX:+UseZGC", "-XX:+ZGenerational", "-XX:-UseAdaptiveSizePolicy",
                "-XX:MaxGCPauseMillis=50", "-XX:G1NewSizePercent=20", "-XX:+UnlockExperimentalVMOptions",
                "-Djava.library.path=C:\\Users\\bob\\natives", "-javaagent:C:\\tools\\agent.jar",
                "-XX:HeapDumpPath=C:\\dumps", "-XX:ErrorFile=/tmp/err.log", "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "-cp", "C:\\Users\\bob\\libs.jar", "-XX:MaxRAMPercentage=75.0", "-XX:SoftMaxHeapSize=C:\\x");

        assertEquals(List.of("-Xmx8G", "-Xms4G", "-Xss2m", "-XX:+UseZGC", "-XX:+ZGenerational",
                        "-XX:MaxGCPauseMillis=50", "-XX:G1NewSizePercent=20", "-XX:+UnlockExperimentalVMOptions",
                        "-XX:MaxRAMPercentage=75.0"),
                JvmFlags.kept(args));
    }
}
```

`-XX:-UseAdaptiveSizePolicy` is not on the list, and `-XX:SoftMaxHeapSize=C:\x` is on it but its value is not a
number or a bare word, so neither survives.

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew test --rerun --no-daemon --tests '*JvmFlagsTest'`
Expected: compilation failure, `cannot find symbol: variable JvmFlags`.

- [ ] **Step 3: Create `JvmFlags`**

```java
package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The JVM arguments a report may carry (spec addendum §P5.B3): heap sizes and collector choices, which the
 * memory recommendations read. A whitelist, not a blacklist, because the argument list also carries paths
 * with the player's user name in them. Everything not listed here is dropped: {@code -D} properties,
 * {@code -javaagent}, class paths, {@code --add-opens}, and any value that is not a number with an optional
 * size suffix or a bare word.
 */
public final class JvmFlags {
    private static final Pattern SIZE = Pattern.compile("-X(?:mx|ms|mn|ss)\\d+[kKmMgGtT]?");
    private static final Pattern SWITCH = Pattern.compile("-XX:[+-](\\w+)");
    private static final Pattern VALUE = Pattern.compile("-XX:(\\w+)=([\\w.]+)");
    private static final Set<String> SWITCHES = Set.of("UseG1GC", "UseZGC", "ZGenerational", "UseShenandoahGC",
            "UseParallelGC", "UseSerialGC", "AlwaysPreTouch", "UseLargePages", "UseTransparentHugePages",
            "UseStringDeduplication", "DisableExplicitGC", "UnlockExperimentalVMOptions");
    private static final Set<String> VALUES = Set.of("MaxGCPauseMillis", "ParallelGCThreads", "ConcGCThreads",
            "SoftMaxHeapSize", "MaxRAMPercentage", "InitialRAMPercentage");

    private JvmFlags() {
    }

    /** The whitelisted arguments of {@code args}, in their order. */
    public static List<String> kept(List<String> args) {
        List<String> out = new ArrayList<>();
        for (String a : args) {
            if (SIZE.matcher(a).matches()) {
                out.add(a);
                continue;
            }
            Matcher s = SWITCH.matcher(a);
            if (s.matches() && (SWITCHES.contains(s.group(1)) || s.group(1).startsWith("G1"))) {
                out.add(a);
                continue;
            }
            Matcher v = VALUE.matcher(a);
            if (v.matches() && (VALUES.contains(v.group(1)) || v.group(1).startsWith("G1"))) out.add(a);
        }
        return out;
    }
}
```

- [ ] **Step 4: Add the environment keys**

In `core/Environment.java`, after the `UNAVAILABLE` constant that plan 5a added, add:

```java
    /** Physical memory in MiB (spec addendum §P5.B3). */
    public static final String PHYSICAL_MEMORY_MIB = "physicalMemoryMiB";
    /** The garbage collector in use, as {@code GcNames.collector} names it. */
    public static final String GC = "gc";
    /** The initial heap in MiB. */
    public static final String INITIAL_HEAP_MIB = "initialHeapMiB";
    /** The smallest heap in use after any collection during the session, in MiB; absent when none ran. */
    public static final String LIVE_HEAP_AFTER_GC_MIB = "liveHeapAfterGcMiB";
    /** The whitelisted JVM arguments, space-separated (see {@code JvmFlags}). */
    public static final String JVM_FLAGS = "jvmFlags";
```

- [ ] **Step 5: Create `GcWatcher`**

```java
package io.github.edwardg117.renderable.hooks;

import com.sun.management.GarbageCollectionNotificationInfo;
import io.github.edwardg117.renderable.RenderableMod;
import io.github.edwardg117.renderable.core.GcNames;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Records the smallest heap in use after any collection during a session (spec addendum §P5.B3), from the
 * JVM's collection notifications. They arrive on the JMX notification thread, so the render thread never
 * allocates for this. The minimum is an upper bound on live data, because a young collection leaves old
 * garbage in place. Only the beans {@link GcNames#liveHeapBeans} names are watched: a ZGC or Shenandoah pause
 * notification reports no heap in use. Started and stopped by the controller with each session.
 */
public final class GcWatcher {
    private static final List<NotificationEmitter> EMITTERS = new ArrayList<>();
    private static final AtomicLong MIN_AFTER_BYTES = new AtomicLong(Long.MAX_VALUE);
    private static NotificationListener listener;

    private GcWatcher() {
    }

    /**
     * Forgets the last session's minimum and starts listening. Render thread, inside the keybind's tick listener,
     * so a JVM without these beans turns the figure off rather than crashing the client.
     */
    public static synchronized void start() {
        stop();
        MIN_AFTER_BYTES.set(Long.MAX_VALUE);
        try {
            Set<String> heapPools = ManagementFactory.getMemoryPoolMXBeans().stream()
                    .filter(p -> p.getType() == MemoryType.HEAP)
                    .map(MemoryPoolMXBean::getName)
                    .collect(Collectors.toUnmodifiableSet());
            List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();
            Set<String> watched = Set.copyOf(GcNames.liveHeapBeans(beans.stream().map(GarbageCollectorMXBean::getName).toList()));
            listener = (Notification n, Object handback) -> {
                if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(n.getType())) return;
                GarbageCollectionNotificationInfo info =
                        GarbageCollectionNotificationInfo.from((CompositeData) n.getUserData());
                long after = 0;
                for (Map.Entry<String, MemoryUsage> e : info.getGcInfo().getMemoryUsageAfterGc().entrySet()) {
                    if (heapPools.contains(e.getKey())) after += e.getValue().getUsed();
                }
                if (after > 0) MIN_AFTER_BYTES.accumulateAndGet(after, Math::min);
            };
            for (GarbageCollectorMXBean bean : beans) {
                if (watched.contains(bean.getName()) && bean instanceof NotificationEmitter emitter) {
                    emitter.addNotificationListener(listener, null, null);
                    EMITTERS.add(emitter);
                }
            }
        } catch (RuntimeException | LinkageError e) {
            RenderableMod.LOGGER.warn("Renderable cannot watch garbage collections; the report will have no live heap figure", e);
            stop();
        }
    }

    /** Stops listening; the minimum so far is kept for {@link #liveHeapAfterGcMiB()}. */
    public static synchronized void stop() {
        for (NotificationEmitter emitter : EMITTERS) {
            try {
                emitter.removeNotificationListener(listener);
            } catch (ListenerNotFoundException ignored) {
                // already gone; nothing to undo
            }
        }
        EMITTERS.clear();
        listener = null;
    }

    /** The smallest heap in use after a collection in the last session, in MiB, or -1 when none ran. */
    public static long liveHeapAfterGcMiB() {
        long b = MIN_AFTER_BYTES.get();
        return b == Long.MAX_VALUE ? -1 : b >> 20;
    }
}
```

- [ ] **Step 6: Collect the facts**

In `EnvironmentCollector.java`, add these imports:

```java
import io.github.edwardg117.renderable.core.GcNames;
import io.github.edwardg117.renderable.core.JvmFlags;
import io.github.edwardg117.renderable.hooks.GcWatcher;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
```

Replace:

```java
        m.put("maxHeapMiB", Long.toString(Runtime.getRuntime().maxMemory() >> 20));
```

with:

```java
        m.put("maxHeapMiB", Long.toString(Runtime.getRuntime().maxMemory() >> 20));
        m.put(Environment.INITIAL_HEAP_MIB, safe(() -> mib(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getInit())));
        m.put(Environment.PHYSICAL_MEMORY_MIB, safe(() -> mib(((com.sun.management.OperatingSystemMXBean)
                ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize())));
        m.put(Environment.GC, safe(() -> GcNames.collector(ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(GarbageCollectorMXBean::getName).toList())));
        long live = GcWatcher.liveHeapAfterGcMiB();
        if (live >= 0) m.put(Environment.LIVE_HEAP_AFTER_GC_MIB, Long.toString(live));
        m.put(Environment.JVM_FLAGS, safe(() -> String.join(" ",
                JvmFlags.kept(ManagementFactory.getRuntimeMXBean().getInputArguments()))));
```

and add this helper after `modVersion`:

```java
    /** Bytes as MiB, or "unknown" for the -1 an MXBean reports when it does not know. */
    private static String mib(long bytes) {
        return bytes < 0 ? "unknown" : Long.toString(bytes >> 20);
    }
```

`safe` already turns a failed cast into `unknown` on a JVM without `com.sun.management`.

- [ ] **Step 7: Watch collections during a session**

In `ProfilerController.java`, add `import io.github.edwardg117.renderable.hooks.GcWatcher;`, then:

- in `start`, after `EventTiming.start(session);`, add `GcWatcher.start();`;
- in `discard`, after `EventTiming.stop();`, add `GcWatcher.stop();`;
- in `finish`, after `EventTiming.stop();`, add `GcWatcher.stop();`.

`finish` collects the environment after stopping the watcher, and the watcher keeps its minimum after stopping,
so the report sees the session's figure.

- [ ] **Step 8: Run the tests**

Run: `./gradlew compileJava --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **163** tests.

- [ ] **Step 9: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/JvmFlags.java src/main/java/io/github/edwardg117/renderable/core/Environment.java src/main/java/io/github/edwardg117/renderable/hooks/GcWatcher.java src/main/java/io/github/edwardg117/renderable/report/EnvironmentCollector.java src/main/java/io/github/edwardg117/renderable/ProfilerController.java src/test/java/io/github/edwardg117/renderable/core/JvmFlagsTest.java
```

Report for the commit: `feat: physical memory, collector, heap and whitelisted JVM flags in the environment`.

---

### Task 3: `Facts`, what the rules read

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/advice/Facts.java`
- Create: `src/test/java/io/github/edwardg117/renderable/core/advice/FactsBuilder.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/advice/FactsTest.java`

Spec addendum §P5.B1. `Facts` is computed once from a session and its environment. Rules read it, so a rule
test builds a `Facts` with the few numbers the rule cares about (`FactsBuilder`), and `FactsTest` checks the
derivation against the shared fixtures. `KeysTest.coreHasNoMinecraftImports` walks `core` recursively, so it
already guards `core.advice`.

- [ ] **Step 1: Write the failing tests**

```java
package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.TestSessions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FactsTest {

    @Test
    void theFrameComponentsCostsAndModsOfThePhase5Fixture() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>(Map.of("particles", "ALL", "shaderPack", "none"));
        Facts f = Facts.of(TestSessions.phase5(), Environment.of(m, List.of()));

        assertEquals(2, f.frames());
        assertEquals(20.0, f.frameMs(), 1e-9);
        assertEquals(2.0, f.bucket(Bucket.HUD), 1e-9);
        assertEquals(2.0, f.tasksMs(), 1e-9);
        assertEquals(3.0, f.capMs(), 1e-9);
        assertEquals(8.0, f.swapMs(), 1e-9);
        assertEquals("ALL", f.text("particles"));
        assertFalse(f.shadersOn(), "\"none\" is not a pack");
        assertTrue(Double.isNaN(f.number("renderDistance")), "not collected");

        Facts.Cost press = f.blockEntityTypes().get(0);
        assertEquals("create:mechanical_press", press.label());
        assertEquals(1.0, press.msPerFrame(), 1e-9);
        assertEquals("100 64 -20", press.where());
        assertEquals(List.of("minecraft:boat", "minecraft:cow", "minecraft:player"),
                f.entityTypes().stream().map(Facts.Cost::label).toList());

        assertEquals(List.of("create", "minecraft", "xaerominimap", "somemod"),
                f.mods().stream().map(Facts.ModCost::modId).toList());
        Facts.ModCost create = f.mods().get(0);
        assertEquals(2.0, create.handlersMs(), 1e-9);
        assertEquals("RenderLevelStageEvent", create.topEvent());
        assertEquals(1.0, f.mods().get(1).packetsMs(), 1e-9);
        assertEquals(1.0, f.mods().get(2).layersMs(), 1e-9);
        Facts.ModCost somemod = f.mods().get(3);
        assertEquals(0.6, somemod.handlersMs(), 1e-9);
        assertEquals("ClientTickEvent.Post", somemod.topEvent(), "the tick handler costs more than the living one");
        assertTrue(f.hitches().isEmpty());
    }

    @Test
    void aHitchCarriesItsUnnamedTimeAndTopContributors() {
        Facts f = Facts.of(TestSessions.sample(), Environment.empty());
        assertEquals(1, f.hitches().size());
        Facts.Hitch h = f.hitches().get(0);
        assertEquals(1, h.number());
        assertEquals(80.0, h.totalMs(), 1e-9);
        assertEquals(0, h.gcMs(), 1e-9, "the hitch frame had no pause");
        assertFalse(h.chunkUploadLargest());
        assertEquals(2.1, h.unnamedRenderMs(), 1e-9, "other render, none of it named");
        assertEquals(0.8, h.unnamedTickMs(), 1e-9);
        assertEquals(0, h.untimedTasksMs(), 1e-9);
        assertEquals(TestSessions.PRESS, h.top().get(0).key());
        assertEquals(60.0, h.top().get(0).ms(), 1e-9);
        assertFalse(h.top().get(0).gc());
        assertEquals(1, f.gcCount());
        assertEquals(5, f.maxFrameGcMs());
    }
}
```

The numbers come from the fixtures' descriptions in `TestSessions`. In `sample()`, frame 10 is 80 ms, with
render 65.4 ms, of which 62.5 ms is block entity and 0.8 ms entity, leaving 2.1 ms of other render. Its tick is
1.0 ms, with entity tick 0.2 ms, leaving 0.8 ms of other tick.

- [ ] **Step 2: Create `FactsBuilder`**

This test helper gives the rule tests in Tasks 4 and 5 a quiet session to change: 1,000 frames of 10 ms,
2 ms of swap, and nothing costly.

```java
package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.ProfileKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Builds {@link Facts} for rule tests: a quiet session of 1,000 frames of 10 ms, and nothing costly. */
final class FactsBuilder {
    private final LinkedHashMap<String, String> env = new LinkedHashMap<>();
    private int frames = 1000;
    private double frameMs = 10;
    private final double[] bucketMs = new double[Bucket.COUNT];
    private double tasksMs;
    private double capMs;
    private double swapMs = 2;
    private double shadowMs;
    private double shadowableMs;
    private final List<Facts.Cost> blockEntities = new ArrayList<>();
    private final List<Facts.Cost> entities = new ArrayList<>();
    private final List<Facts.Cost> particles = new ArrayList<>();
    private final List<Facts.Hitch> hitches = new ArrayList<>();
    private int gcCount;
    private long gcTotalMs;
    private long maxFrameGcMs;
    private final List<Facts.ModCost> mods = new ArrayList<>();
    private int unbalanced;

    FactsBuilder env(String key, String value) {
        env.put(key, value);
        return this;
    }

    FactsBuilder frameMs(double ms) {
        frameMs = ms;
        return this;
    }

    FactsBuilder bucket(Bucket b, double ms) {
        bucketMs[b.ordinal()] = ms;
        return this;
    }

    FactsBuilder tasks(double ms) {
        tasksMs = ms;
        return this;
    }

    FactsBuilder cap(double ms) {
        capMs = ms;
        return this;
    }

    FactsBuilder swap(double ms) {
        swapMs = ms;
        return this;
    }

    FactsBuilder shadow(double shadow, double shadowable) {
        shadowMs = shadow;
        shadowableMs = shadowable;
        return this;
    }

    FactsBuilder blockEntity(String type, double ms, int members, String where) {
        blockEntities.add(new Facts.Cost(type, ms, members, 0, where));
        return this;
    }

    FactsBuilder entity(String type, double ms, int members) {
        entities.add(new Facts.Cost(type, ms, members, 0, ""));
        return this;
    }

    FactsBuilder particle(String label, double ms, double live) {
        particles.add(new Facts.Cost(label, ms, 1, live, ""));
        return this;
    }

    FactsBuilder hitch(Facts.Hitch h) {
        hitches.add(h);
        return this;
    }

    FactsBuilder gc(int count, long totalMs, long maxFrameMs) {
        gcCount = count;
        gcTotalMs = totalMs;
        maxFrameGcMs = maxFrameMs;
        return this;
    }

    FactsBuilder mod(String id, double handlers, double layers, double packets, String topEvent) {
        mods.add(new Facts.ModCost(id, handlers, layers, packets, topEvent));
        return this;
    }

    FactsBuilder unbalanced(int frames) {
        unbalanced = frames;
        return this;
    }

    Facts build() {
        return new Facts(Environment.of(env, List.of()), frames, frameMs, bucketMs.clone(), tasksMs, capMs, swapMs,
                shadowMs, shadowableMs, List.copyOf(blockEntities), List.copyOf(entities), List.copyOf(particles),
                List.copyOf(hitches), gcCount, gcTotalMs, maxFrameGcMs, List.copyOf(mods), unbalanced);
    }

    /** A hitch with no GC pause, nothing unnamed and nothing leading from chunk upload: only its size and top contributors. */
    static Facts.Hitch hitch(int number, double totalMs, Facts.Top... top) {
        return new Facts.Hitch(number, totalMs, 0, 0, false, 0, 0, 0, List.of(top));
    }

    /** A top contributor that no GC pause could account for. */
    static Facts.Top top(ProfileKey key, double ms) {
        return new Facts.Top(key, ms, false);
    }
}
```

- [ ] **Step 3: Run the tests to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*FactsTest'`
Expected: compilation failure, `cannot find symbol: class Facts`.

- [ ] **Step 4: Create `Facts`**

```java
package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.FrameRecord;
import io.github.edwardg117.renderable.core.FrameStats;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.RankedEntry;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.TopEntry;
import io.github.edwardg117.renderable.core.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * What the recommendation rules read (spec addendum §P5.B1), derived once from a finished session and its
 * environment. Milliseconds are per frame unless the name says otherwise. Rules take this rather than the
 * session, so each rule can be tested with only the numbers it cares about.
 *
 * @param bucketMs           average milliseconds per frame, per {@link Bucket} ordinal
 * @param shadowMs           Iris shadow-pass time of block entities and entities
 * @param shadowableRenderMs render time of block entities and entities, which the shadow share is taken of
 * @param blockEntityTypes   block entity types, most expensive first
 * @param entityTypes        entity types, most expensive first
 * @param particleClasses    particle classes, most expensive first
 * @param hitches            the hitch list, worst first
 * @param maxFrameGcMs       the most GC pause reported in any one frame
 * @param mods               mods, most expensive first
 */
public record Facts(Environment env,
                    int frames,
                    double frameMs,
                    double[] bucketMs,
                    double tasksMs,
                    double capMs,
                    double swapMs,
                    double shadowMs,
                    double shadowableRenderMs,
                    List<Cost> blockEntityTypes,
                    List<Cost> entityTypes,
                    List<Cost> particleClasses,
                    List<Hitch> hitches,
                    int gcCount,
                    long gcTotalMs,
                    long maxFrameGcMs,
                    List<ModCost> mods,
                    int unbalancedFrames) {

    /**
     * One ranked row, reduced to what a rule says about it.
     *
     * @param label        a type id, or a particle class's label
     * @param msPerFrame   render plus tick
     * @param members      positions or individuals in the type; 1 for a particle class
     * @param livePerFrame particles only: the average number rendered each frame
     * @param where        block entities only: the most expensive member's position as "x y z", or ""
     */
    public record Cost(String label, double msPerFrame, int members, double livePerFrame, String where) {
    }

    /**
     * One hitch, numbered as the report numbers them: 1 is the worst.
     *
     * @param gcMs               the GC pause reported in the frame
     * @param chunkUploadLargest true when chunk upload is the largest part of the frame's split
     * @param unnamedTickMs      other tick, less what the Mods tab names in it
     * @param unnamedRenderMs    other render, less what the Mods tab names in it
     * @param untimedTasksMs     tasks, less what the Mods tab names in them
     */
    public record Hitch(int number, double totalMs, double gcMs, double chunkUploadMs, boolean chunkUploadLargest,
                        double unnamedTickMs, double unnamedRenderMs, double untimedTasksMs, List<Top> top) {
    }

    /**
     * One of a hitch's top contributors.
     *
     * @param gc true when the frame's GC pause could account for it: the report's {@code [gc]} mark
     */
    public record Top(ProfileKey key, double ms, boolean gc) {
    }

    /**
     * A mod's timed work per frame: its event handlers, its HUD layers and its packets (spec addendum §P5.A7).
     *
     * @param topEvent the simple name of the event its handlers spent the most on, or ""
     */
    public record ModCost(String modId, double handlersMs, double layersMs, double packetsMs, String topEvent) {
    }

    public double bucket(Bucket b) {
        return bucketMs[b.ordinal()];
    }

    /** The environment value for {@code key}, or null when it was not collected or reads "unknown". */
    public String text(String key) {
        String v = env.value(key);
        return env.has(key) && !"unknown".equals(v) ? v : null;
    }

    /** The environment value for {@code key} as a number, or NaN when it is absent or not a number. */
    public double number(String key) {
        String v = text(key);
        if (v == null) return Double.NaN;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** True when Iris reports a shader pack by name. */
    public boolean shadersOn() {
        String pack = text("shaderPack");
        return pack != null && !"none".equals(pack);
    }

    public static Facts of(SessionResult r, Environment env) {
        FrameStats st = r.stats();
        int frames = r.frameCount();
        double[] bucketMs = new double[Bucket.COUNT];
        for (Bucket b : Bucket.VALUES) bucketMs[b.ordinal()] = st.avgBucket(b) / 1e6;

        List<RankedEntry> blockEntityRows = Ranking.byType(r, ProfileKey.Category.BLOCK_ENTITY);
        List<RankedEntry> entityRows = Ranking.byType(r, ProfileKey.Category.ENTITY);
        long shadow = 0;
        long shadowable = 0;
        for (List<RankedEntry> rows : List.of(blockEntityRows, entityRows)) {
            for (RankedEntry e : rows) {
                shadow += e.shadowNanos();
                shadowable += e.renderNanos();
            }
        }

        List<Cost> blockEntities = new ArrayList<>();
        for (RankedEntry t : blockEntityRows) {
            List<RankedEntry> members = Ranking.membersOf(r, ProfileKey.Category.BLOCK_ENTITY, t.typeId());
            String where = !members.isEmpty() && members.get(0).key() instanceof BlockEntityKey k
                    ? k.x() + " " + k.y() + " " + k.z() : "";
            blockEntities.add(new Cost(t.typeId(), perFrame(t.totalNanos(), frames), t.members(), 0, where));
        }
        List<Cost> entities = new ArrayList<>();
        for (RankedEntry t : entityRows) {
            entities.add(new Cost(t.typeId(), perFrame(t.totalNanos(), frames), t.members(), 0, ""));
        }
        List<Cost> particles = new ArrayList<>();
        for (RankedEntry p : Ranking.byKey(r, ProfileKey.Category.PARTICLE)) {
            particles.add(new Cost(p.label(), perFrame(p.totalNanos(), frames), 1, p.callsPerFrame(frames), ""));
        }

        List<Hitch> hitches = new ArrayList<>();
        for (int i = 0; i < r.hitches().size(); i++) hitches.add(hitch(i + 1, r.hitches().get(i)));

        long maxFrameGc = 0;
        for (FrameRecord f : r.frames()) maxFrameGc = Math.max(maxFrameGc, f.gcMillis());

        List<ModCost> mods = new ArrayList<>();
        for (RankedEntry m : Ranking.byType(r, ProfileKey.Category.MOD)) {
            long handlers = 0;
            long layers = 0;
            long packets = 0;
            long topEventNanos = -1;
            String topEvent = "";
            for (RankedEntry e : Ranking.membersOf(r, ProfileKey.Category.MOD, m.typeId())) {
                ModKey k = (ModKey) e.key();
                switch (k.kind()) {
                    case EVENT -> {
                        handlers += e.totalNanos();
                        if (e.totalNanos() > topEventNanos) {
                            topEventNanos = e.totalNanos();
                            topEvent = ModKey.simpleEventName(k.name());
                        }
                    }
                    case LAYER -> layers += e.totalNanos();
                    case PACKET -> packets += e.totalNanos();
                }
            }
            mods.add(new ModCost(m.typeId(), perFrame(handlers, frames), perFrame(layers, frames),
                    perFrame(packets, frames), topEvent));
        }

        return new Facts(env, frames, st.avgNanos() / 1e6, bucketMs, st.avgTasksNanos() / 1e6,
                st.avgCapNanos() / 1e6, st.avgSwapNanos() / 1e6, perFrame(shadow, frames),
                perFrame(shadowable, frames), List.copyOf(blockEntities), List.copyOf(entities),
                List.copyOf(particles), List.copyOf(hitches), st.gcCount(), st.gcMillis(), maxFrameGc,
                List.copyOf(mods), r.unbalancedFrames());
    }

    private static Hitch hitch(int number, FrameRecord h) {
        long upload = h.bucket(Bucket.CHUNK_UPLOAD);
        long other = Math.max(Math.max(h.tickNanos(), h.otherRenderNanos()),
                Math.max(h.tasksNanos(), Math.max(h.capNanos(), h.swapNanos())));
        for (Bucket b : Bucket.VALUES) {
            if (b.isRender() && b != Bucket.CHUNK_UPLOAD) other = Math.max(other, h.bucket(b));
        }
        List<Top> top = new ArrayList<>();
        for (TopEntry t : h.top()) top.add(new Top(t.key(), t.nanos() / 1e6, t.gc()));
        return new Hitch(number, h.totalNanos() / 1e6, h.gcMillis(), upload / 1e6, upload > other,
                Math.max(0, h.otherTickNanos() - h.named(Window.TICK)) / 1e6,
                Math.max(0, h.otherRenderNanos() - h.named(Window.RENDER)) / 1e6,
                Math.max(0, h.tasksNanos() - h.named(Window.TASKS)) / 1e6,
                List.copyOf(top));
    }

    private static double perFrame(long nanos, int frames) {
        return frames == 0 ? 0 : nanos / 1e6 / frames;
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **165** tests. (`FactsBuilder` has no tests of its own; Tasks 4 and 5 use it.)

- [ ] **Step 6: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/advice/Facts.java src/test/java/io/github/edwardg117/renderable/core/advice
```

Report for the commit: `feat(core): the facts the recommendation rules read`.

---

### Task 4: `Recommendation`, thresholds, and the settings and scene rules

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/advice/Recommendation.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/advice/Thresholds.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/advice/Words.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/advice/SettingsRules.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/advice/SceneRules.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/advice/SettingsRulesTest.java` (7)
- Test: `src/test/java/io/github/edwardg117/renderable/core/advice/SceneRulesTest.java` (3)

Spec addendum §P5.B1 and §P5.B2, with deviations 1 and 3. Every threshold is a constant in `Thresholds`, named
after the table in §P5.B2. The wording of findings and actions follows §P5.B2's examples, which the tests pin.

- [ ] **Step 1: Write the failing tests**

```java
package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Bucket;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingsRulesTest {

    static List<Recommendation> run(Facts f, java.util.function.BiConsumer<Facts, List<Recommendation>> rule) {
        List<Recommendation> out = new ArrayList<>();
        rule.accept(f, out);
        return out;
    }

    @Test
    void entityDistanceNeedsAMillisecondATenthOfTheFrameAndRoomToLower() {
        List<Recommendation> r = run(new FactsBuilder().frameMs(12).bucket(Bucket.ENTITY, 2.4)
                .env("entityDistance", "3.0").build(), SettingsRules::entityDistance);
        assertEquals(1, r.size());
        Recommendation a = r.get(0);
        assertEquals("entity-distance", a.rule());
        assertEquals(Recommendation.Kind.FRAME_RATE, a.kind());
        assertEquals(Recommendation.Audience.PLAYER, a.audience());
        assertEquals("Entities take 2.4 ms of each 12.0 ms frame at 300% entity distance.", a.finding());
        assertEquals("Lower Entity Distance in Video Settings; the number of entities drawn falls with the square of the distance.", a.action());
        assertEquals(2.4, a.msPerFrame(), 1e-9);

        assertTrue(run(new FactsBuilder().frameMs(12).bucket(Bucket.ENTITY, 0.9).env("entityDistance", "3.0").build(),
                SettingsRules::entityDistance).isEmpty(), "under a millisecond");
        assertTrue(run(new FactsBuilder().frameMs(30).bucket(Bucket.ENTITY, 2.4).env("entityDistance", "3.0").build(),
                SettingsRules::entityDistance).isEmpty(), "8% of the frame");
        assertTrue(run(new FactsBuilder().frameMs(12).bucket(Bucket.ENTITY, 2.4).env("entityDistance", "0.5").build(),
                SettingsRules::entityDistance).isEmpty(), "already at the minimum");
        assertTrue(run(new FactsBuilder().frameMs(12).bucket(Bucket.ENTITY, 2.4).build(),
                SettingsRules::entityDistance).isEmpty(), "never fires on missing data");
    }

    @Test
    void particlesNeedAllAndHalfAMillisecond() {
        List<Recommendation> r = run(new FactsBuilder().bucket(Bucket.PARTICLE_RENDER, 0.6)
                .bucket(Bucket.PARTICLE_TICK, 0.3).env("particles", "ALL").build(), SettingsRules::particles);
        assertEquals(1, r.size());
        assertEquals("Particles take 0.9 ms a frame with Particles on All.", r.get(0).finding());
        assertEquals("Set Particles to Decreased in Video Settings.", r.get(0).action());
        assertTrue(run(new FactsBuilder().bucket(Bucket.PARTICLE_RENDER, 0.9).env("particles", "DECREASED").build(),
                SettingsRules::particles).isEmpty());
        assertTrue(run(new FactsBuilder().bucket(Bucket.PARTICLE_RENDER, 0.4).env("particles", "ALL").build(),
                SettingsRules::particles).isEmpty());
    }

    @Test
    void shadowsNeedAPackAMillisecondAndAThirdOfTheDrawing() {
        List<Recommendation> r = run(new FactsBuilder().env("shaderPack", "SomePack").shadow(2.1, 5.5).build(),
                SettingsRules::shadows);
        assertEquals(1, r.size());
        assertEquals("The shader pack's shadow pass is 38% of block entity and entity drawing, 2.1 ms a frame.", r.get(0).finding());
        assertEquals("Lower the shader pack's shadow distance or resolution, or turn its shadows off.", r.get(0).action());
        assertTrue(run(new FactsBuilder().env("shaderPack", "none").shadow(2.1, 5.5).build(), SettingsRules::shadows).isEmpty());
        assertTrue(run(new FactsBuilder().env("shaderPack", "SomePack").shadow(1.0, 5.0).build(), SettingsRules::shadows).isEmpty(),
                "a fifth of the drawing");
    }

    @Test
    void flywheelOffUnderShadersNeedsTheOffBackendAndCostlyBlockEntities() {
        List<Recommendation> r = run(new FactsBuilder().env("shaderPack", "SomePack").env("flywheelBackend", "flywheel:off")
                .bucket(Bucket.BLOCK_ENTITY, 3.2).build(), SettingsRules::flywheelShaders);
        assertEquals(1, r.size());
        assertEquals("Flywheel is not instancing under this shader pack, so the machines it would instance are drawn"
                + " one at a time: block entities take 3.2 ms a frame.", r.get(0).finding());
        assertTrue(run(new FactsBuilder().env("shaderPack", "SomePack").env("flywheelBackend", "flywheel:indirect")
                .bucket(Bucket.BLOCK_ENTITY, 3.2).build(), SettingsRules::flywheelShaders).isEmpty());
    }

    @Test
    void chunkRebuildsAreStutterWhenTheyLeadThreeHitches() {
        FactsBuilder b = new FactsBuilder().bucket(Bucket.CHUNK_UPLOAD, 1.8).env("renderDistance", "16").env("biomeBlend", "3");
        for (int i = 1; i <= 6; i++) {
            boolean leads = i <= 4;
            b.hitch(new Facts.Hitch(i, 60, 0, leads ? 30 + i : 1, leads, 0, 0, 0, List.of()));
        }
        Recommendation a = run(b.build(), SettingsRules::chunkRebuilds).get(0);
        assertEquals(Recommendation.Kind.STUTTER, a.kind());
        assertEquals(34.0, a.worstMs(), 1e-9, "the largest chunk upload among the hitches it leads");
        assertEquals("Chunk rebuilds take 1.8 ms a frame and are the largest part of 4 of 6 hitches,"
                + " at render distance 16 and biome blend 7x7.", a.finding());
        assertEquals("Lower Biome Blend to 5x5 or less. While travelling, a lower render distance rebuilds fewer sections.",
                a.action());

        Recommendation steady = run(new FactsBuilder().bucket(Bucket.CHUNK_UPLOAD, 1.8).env("renderDistance", "10")
                .env("biomeBlend", "3").build(), SettingsRules::chunkRebuilds).get(0);
        assertEquals(Recommendation.Kind.FRAME_RATE, steady.kind());
        assertEquals("Chunk rebuilds take 1.8 ms a frame, at render distance 10 and biome blend 7x7.", steady.finding());
        assertEquals("Lower Biome Blend to 5x5 or less.", steady.action());

        assertTrue(run(new FactsBuilder().bucket(Bucket.CHUNK_UPLOAD, 1.8).env("renderDistance", "10")
                .env("biomeBlend", "2").build(), SettingsRules::chunkRebuilds).isEmpty(), "nothing to lower");
    }

    @Test
    void theFrameCapIsANoteAboutHeadroom() {
        List<Recommendation> r = run(new FactsBuilder().frameMs(16.7).cap(8.3).env("fpsLimit", "60").build(),
                SettingsRules::frameCap);
        assertEquals(Recommendation.Kind.NOTE, r.get(0).kind());
        assertEquals("8.3 ms of each 16.7 ms frame is spent waiting for the 60 fps limit, so the game has headroom.",
                r.get(0).finding());
        assertEquals("Raise Max Framerate if you want more frames.", r.get(0).action());
        assertEquals(0, r.get(0).msPerFrame(), 1e-9, "a note has nothing at stake");
        assertTrue(run(new FactsBuilder().frameMs(16.7).cap(4.1).build(), SettingsRules::frameCap).isEmpty(), "under a quarter");
    }

    @Test
    void vsyncIsANoteWhenTheSwapIsAQuarterOfTheFrame() {
        List<Recommendation> r = run(new FactsBuilder().frameMs(16.7).swap(5.2).env("vsync", "true").build(),
                SettingsRules::vsync);
        assertEquals("With VSync on, 5.2 ms of each 16.7 ms frame is spent waiting for the display.", r.get(0).finding());
        assertEquals("Turn VSync off for more frames, at the risk of tearing.", r.get(0).action());
        assertTrue(run(new FactsBuilder().frameMs(16.7).swap(5.2).env("vsync", "false").build(), SettingsRules::vsync).isEmpty());
    }
}
```

```java
package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.ModKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.edwardg117.renderable.core.advice.FactsBuilder.hitch;
import static io.github.edwardg117.renderable.core.advice.FactsBuilder.top;
import static org.junit.jupiter.api.Assertions.*;

class SceneRulesTest {

    static List<Recommendation> run(Facts f, java.util.function.BiConsumer<Facts, List<Recommendation>> rule) {
        List<Recommendation> out = new ArrayList<>();
        rule.accept(f, out);
        return out;
    }

    @Test
    void typesFireForTheTopThreeAboveAMillisecond() {
        Facts f = new FactsBuilder()
                .blockEntity("create:factory_panel", 1.1, 212, "120 64 -340")
                .blockEntity("create:belt", 1.05, 40, "1 2 3")
                .blockEntity("create:shaft", 1.02, 300, "4 5 6")
                .blockEntity("create:gearbox", 1.01, 9, "7 8 9")
                .build();
        List<Recommendation> r = run(f, SceneRules::blockEntityTypes);
        assertEquals(3, r.size(), "at most three types");
        assertEquals("create:factory_panel takes 1.1 ms a frame across 212 blocks; the most expensive is at 120 64 -340.",
                r.get(0).finding());
        assertEquals("It is the build, not a setting: fewer of them in view at once costs less."
                + " Highlight on the Block Entities tab shows where they are.", r.get(0).action());
        assertTrue(run(new FactsBuilder().blockEntity("create:shaft", 0.9, 300, "").build(), SceneRules::blockEntityTypes).isEmpty());
    }

    @Test
    void entityAndParticleFindingsNameTheirCounts() {
        Recommendation e = run(new FactsBuilder().entity("minecraft:item_frame", 1.3, 340).build(), SceneRules::entityTypes).get(0);
        assertEquals("minecraft:item_frame takes 1.3 ms a frame across 340 entities.", e.finding());
        assertEquals("Fewer of them nearby, or a lower Entity Distance.", e.action());

        Recommendation p = run(new FactsBuilder().particle("DripParticle", 0.7, 1400).build(), SceneRules::particleClasses).get(0);
        assertEquals("DripParticle takes 0.7 ms a frame, with 1,400 alive on average.", p.finding());
        assertEquals("Find what emits them; lowering Particles hides most of them.", p.action());
    }

    @Test
    void hitchRepeatNeedsThreeHitchesAndAQuarterOfThem() {
        BlockEntityKey crafter = new BlockEntityKey("create:mechanical_crafter", "minecraft:overworld", 118, 64, -338);
        ModKey handler = ModKey.event("somemod", "a.ClientTickEvent$Post");
        FactsBuilder b = new FactsBuilder();
        for (int i = 1; i <= 13; i++) {
            double ms = i <= 5 ? 30 + 2 * i : 0;
            b.hitch(i <= 5 ? hitch(i, 60, top(crafter, ms), top(handler, 40)) : hitch(i, 60, top(handler, 40)));
        }
        List<Recommendation> r = run(b.build(), SceneRules::hitchRepeat);
        assertEquals(1, r.size(), "the handler is a mod key, which the mod rules cover");
        Recommendation a = r.get(0);
        assertEquals(Recommendation.Kind.STUTTER, a.kind());
        assertEquals("create:mechanical_crafter @ 118 64 -338 was a top cost in 5 of 13 hitches, up to 40 ms.", a.finding());
        assertEquals(40.0, a.worstMs(), 1e-9);
        assertEquals((32 + 34 + 36 + 38 + 40) / 1000.0, a.msPerFrame(), 1e-9, "its hitch time over the 1,000 frames");

        FactsBuilder few = new FactsBuilder();
        for (int i = 1; i <= 13; i++) few.hitch(i <= 3 ? hitch(i, 60, top(crafter, 30)) : hitch(i, 60));
        assertTrue(run(few.build(), SceneRules::hitchRepeat).isEmpty(), "3 of 13 is under a quarter");

        FactsBuilder steady = new FactsBuilder();
        for (int i = 1; i <= 4; i++) steady.hitch(hitch(i, 60, top(crafter, 2)));
        assertTrue(run(steady.build(), SceneRules::hitchRepeat).isEmpty(),
                "a steady 2 ms is in every hitch's top list without being its cause");

        FactsBuilder paused = new FactsBuilder();
        for (int i = 1; i <= 4; i++) paused.hitch(hitch(i, 60, new Facts.Top(crafter, 40, true)));
        assertTrue(run(paused.build(), SceneRules::hitchRepeat).isEmpty(), "a GC pause could account for it");
    }
}
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*SettingsRulesTest' --tests '*SceneRulesTest'`
Expected: compilation failure, `cannot find symbol: class Recommendation`.

- [ ] **Step 3: Create `Recommendation`**

```java
package io.github.edwardg117.renderable.core.advice;

import java.util.Locale;

/**
 * One piece of advice drawn from a session (spec addendum §P5.B1): who it is for, what kind of problem it is,
 * what was measured, what to do, and how much time the finding is about.
 *
 * @param rule       the rule that made it, as spec addendum §P5.B2 names it
 * @param msPerFrame the measured cost the finding is about, averaged over the session. It is an upper bound on
 *                   what the action can save, never a prediction; 0 for a note
 * @param worstMs    the largest single occurrence, for stutter; 0 otherwise
 */
public record Recommendation(String rule, Kind kind, Audience audience, String title, String finding, String action,
                             double msPerFrame, double worstMs) {

    /** Who can act on it. The player's come first when two rank the same. */
    public enum Audience {
        PLAYER("player"), PACK_AUTHOR("pack author");

        private final String label;

        Audience(String label) {
            this.label = label;
        }

        /** How the report tags it: {@code [player]} or {@code [pack author]}. */
        public String label() {
            return label;
        }
    }

    /** In ranking order: stutter first, because a stall averaged over the session hides behind any steady cost. */
    public enum Kind { STUTTER, FRAME_RATE, NOTE }

    /** What is at stake, for a heading: "up to 83 ms", "1.2 ms/frame", or "" for a note. */
    public String atStake() {
        return switch (kind) {
            case STUTTER -> String.format(Locale.ROOT, "up to %.0f ms", worstMs);
            case FRAME_RATE -> String.format(Locale.ROOT, "%.1f ms/frame", msPerFrame);
            case NOTE -> "";
        };
    }
}
```

- [ ] **Step 4: Create `Thresholds` and `Words`**

```java
package io.github.edwardg117.renderable.core.advice;

/**
 * Every threshold the rules use (spec addendum §P5.B2), in one place. They are first estimates, to be revisited
 * against real reports; a rule reads them from here rather than spelling a number.
 */
final class Thresholds {
    static final double ENTITY_RENDER_MS = 1.0;
    static final double ENTITY_RENDER_SHARE = 0.10;
    /** Entity distance is a scale, 1.0 being 100%; at 50% there is nothing left to lower. */
    static final double ENTITY_DISTANCE_LOWEST = 0.5;
    static final double PARTICLES_MS = 0.5;
    static final double SHADOW_MS = 1.0;
    static final double SHADOW_SHARE = 0.30;
    static final double FLYWHEEL_OFF_BLOCK_ENTITY_MS = 1.0;
    static final double CHUNK_UPLOAD_MS = 1.0;
    /** Biome blend is a radius: 3 is 7x7. */
    static final int BIOME_BLEND_HIGH = 3;
    static final int RENDER_DISTANCE_HIGH = 16;
    static final double CAP_SHARE = 0.25;
    static final double VSYNC_SWAP_SHARE = 0.25;
    static final double TYPE_MS = 1.0;
    static final int TOP_TYPES = 3;
    static final double PARTICLE_CLASS_MS = 0.5;
    static final int REPEATED_HITCHES = 3;
    static final double HITCH_REPEAT_SHARE = 0.25;
    /**
     * A key counts toward a repeated hitch only when it took at least this share of the hitch: every hitch's top
     * list also holds the frame's usual costs (plan 5b, deviation 6).
     */
    static final double HITCH_KEY_SHARE = 0.25;
    static final double RAM_RESERVE_MIB = 4096;
    static final long RAM_STALL_GC_MS = 500;
    static final double LIVE_HEAP_SHARE = 0.80;
    static final double HEAP_GROWTH = 1.6;
    static final long GC_PAUSE_MS = 50;
    static final double STALL_MS = 50;
    static final double STALL_SHARE = 0.5;
    static final double MOD_MS = 0.5;

    private Thresholds() {
    }

    /**
     * The mod rules skip owners a pack author cannot act on: vanilla's HUD layers and packets, NeoForge's own
     * handlers, and handlers whose mod could not be found (plan 5b, deviation 4).
     */
    static boolean isMod(String modId) {
        return !"minecraft".equals(modId) && !"neoforge".equals(modId) && !"unknown".equals(modId);
    }

    /** True when {@code t} is a cause of hitch {@code h}, not a steady cost or a GC suspect (deviation 6). */
    static boolean causes(Facts.Top t, Facts.Hitch h) {
        return !t.gc() && t.ms() >= HITCH_KEY_SHARE * h.totalMs();
    }
}
```

```java
package io.github.edwardg117.renderable.core.advice;

import java.util.Locale;

/** Small wording helpers shared by the rules. */
final class Words {

    private Words() {
    }

    /** MiB as GiB: whole numbers without a decimal ("8"), others with one ("6.9"). */
    static String gib(double mib) {
        double g = mib / 1024;
        return Math.abs(g - Math.rint(g)) < 0.05
                ? String.format(Locale.ROOT, "%.0f", g)
                : String.format(Locale.ROOT, "%.1f", g);
    }

    /** A biome blend radius as the Video Settings screen shows it: 0 is "off", 2 is "5x5". */
    static String blend(int radius) {
        if (radius <= 0) return "off";
        int side = 2 * radius + 1;
        return side + "x" + side;
    }

    /** The major version from the environment's java line ("21.0.3 (Eclipse Adoptium)" is 21), or -1. */
    static int javaMajor(String version) {
        if (version == null) return -1;
        int end = 0;
        while (end < version.length() && Character.isDigit(version.charAt(end))) end++;
        return end == 0 ? -1 : Integer.parseInt(version.substring(0, end));
    }

    /** "1 block" or "212 blocks". */
    static String count(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }
}
```

- [ ] **Step 5: Create `SettingsRules`**

```java
package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.advice.Recommendation.Audience;
import io.github.edwardg117.renderable.core.advice.Recommendation.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Settings the player can change (spec addendum §P5.B2): one method per rule, in the table's order. */
final class SettingsRules {

    private SettingsRules() {
    }

    static void all(Facts f, List<Recommendation> out) {
        entityDistance(f, out);
        particles(f, out);
        shadows(f, out);
        flywheelShaders(f, out);
        chunkRebuilds(f, out);
        frameCap(f, out);
        vsync(f, out);
    }

    static void entityDistance(Facts f, List<Recommendation> out) {
        double entity = f.bucket(Bucket.ENTITY);
        double distance = f.number("entityDistance");
        if (Double.isNaN(distance) || distance <= Thresholds.ENTITY_DISTANCE_LOWEST) return;
        if (entity < Thresholds.ENTITY_RENDER_MS || entity < Thresholds.ENTITY_RENDER_SHARE * f.frameMs()) return;
        out.add(new Recommendation("entity-distance", Kind.FRAME_RATE, Audience.PLAYER, "Entities cost a lot to draw",
                String.format(Locale.ROOT, "Entities take %.1f ms of each %.1f ms frame at %.0f%% entity distance.",
                        entity, f.frameMs(), distance * 100),
                "Lower Entity Distance in Video Settings; the number of entities drawn falls with the square of the distance.",
                entity, 0));
    }

    static void particles(Facts f, List<Recommendation> out) {
        double ms = f.bucket(Bucket.PARTICLE_RENDER) + f.bucket(Bucket.PARTICLE_TICK);
        if (!"ALL".equalsIgnoreCase(f.text("particles")) || ms < Thresholds.PARTICLES_MS) return;
        out.add(new Recommendation("particles", Kind.FRAME_RATE, Audience.PLAYER, "Particles are on All",
                String.format(Locale.ROOT, "Particles take %.1f ms a frame with Particles on All.", ms),
                "Set Particles to Decreased in Video Settings.", ms, 0));
    }

    static void shadows(Facts f, List<Recommendation> out) {
        if (!f.shadersOn() || f.shadowableRenderMs() <= 0) return;
        double share = f.shadowMs() / f.shadowableRenderMs();
        if (f.shadowMs() < Thresholds.SHADOW_MS || share < Thresholds.SHADOW_SHARE) return;
        out.add(new Recommendation("shadows", Kind.FRAME_RATE, Audience.PLAYER, "Shader shadows draw everything twice",
                String.format(Locale.ROOT, "The shader pack's shadow pass is %.0f%% of block entity and entity drawing, %.1f ms a frame.",
                        share * 100, f.shadowMs()),
                "Lower the shader pack's shadow distance or resolution, or turn its shadows off.", f.shadowMs(), 0));
    }

    static void flywheelShaders(Facts f, List<Recommendation> out) {
        double blockEntities = f.bucket(Bucket.BLOCK_ENTITY);
        if (!f.shadersOn() || !"flywheel:off".equals(f.text("flywheelBackend"))
                || blockEntities < Thresholds.FLYWHEEL_OFF_BLOCK_ENTITY_MS) return;
        out.add(new Recommendation("flywheel-shaders", Kind.FRAME_RATE, Audience.PLAYER, "Flywheel is off under this shader pack",
                String.format(Locale.ROOT, "Flywheel is not instancing under this shader pack, so the machines it would instance"
                        + " are drawn one at a time: block entities take %.1f ms a frame.", blockEntities),
                "Turn shaders off, or use a shader pack that Flywheel supports, to bring instancing back.", blockEntities, 0));
    }

    static void chunkRebuilds(Facts f, List<Recommendation> out) {
        double upload = f.bucket(Bucket.CHUNK_UPLOAD);
        double blend = f.number("biomeBlend");
        double distance = f.number("renderDistance");
        boolean highBlend = !Double.isNaN(blend) && blend >= Thresholds.BIOME_BLEND_HIGH;
        boolean farView = !Double.isNaN(distance) && distance >= Thresholds.RENDER_DISTANCE_HIGH;
        if (upload < Thresholds.CHUNK_UPLOAD_MS || !(highBlend || farView)) return;

        int led = 0;
        double worst = 0;
        for (Facts.Hitch h : f.hitches()) {
            if (!h.chunkUploadLargest()) continue;
            led++;
            worst = Math.max(worst, h.chunkUploadMs());
        }
        boolean stutter = led >= Thresholds.REPEATED_HITCHES;

        List<String> settings = new ArrayList<>();
        if (!Double.isNaN(distance)) settings.add(String.format(Locale.ROOT, "render distance %.0f", distance));
        if (!Double.isNaN(blend)) settings.add("biome blend " + Words.blend((int) blend));
        String finding = String.format(Locale.ROOT, "Chunk rebuilds take %.1f ms a frame", upload)
                + (stutter ? String.format(Locale.ROOT, " and are the largest part of %d of %d hitches", led, f.hitches().size()) : "")
                + ", at " + String.join(" and ", settings) + ".";
        List<String> actions = new ArrayList<>();
        if (highBlend) actions.add("Lower Biome Blend to 5x5 or less.");
        if (farView) actions.add("While travelling, a lower render distance rebuilds fewer sections.");
        out.add(new Recommendation("chunk-rebuilds", stutter ? Kind.STUTTER : Kind.FRAME_RATE, Audience.PLAYER,
                "Chunk rebuilds", finding, String.join(" ", actions), upload, stutter ? worst : 0));
    }

    static void frameCap(Facts f, List<Recommendation> out) {
        if (f.frameMs() <= 0 || f.capMs() < Thresholds.CAP_SHARE * f.frameMs()) return;
        double limit = f.number("fpsLimit");
        String fps = Double.isNaN(limit) ? "the fps limit" : String.format(Locale.ROOT, "the %.0f fps limit", limit);
        out.add(new Recommendation("frame-cap", Kind.NOTE, Audience.PLAYER, "Frames are waiting for the fps limit",
                String.format(Locale.ROOT, "%.1f ms of each %.1f ms frame is spent waiting for %s, so the game has headroom.",
                        f.capMs(), f.frameMs(), fps),
                "Raise Max Framerate if you want more frames.", 0, 0));
    }

    static void vsync(Facts f, List<Recommendation> out) {
        if (!"true".equals(f.text("vsync")) || f.frameMs() <= 0 || f.swapMs() < Thresholds.VSYNC_SWAP_SHARE * f.frameMs()) return;
        out.add(new Recommendation("vsync", Kind.NOTE, Audience.PLAYER, "VSync is waiting for the display",
                String.format(Locale.ROOT, "With VSync on, %.1f ms of each %.1f ms frame is spent waiting for the display.",
                        f.swapMs(), f.frameMs()),
                "Turn VSync off for more frames, at the risk of tearing.", 0, 0));
    }
}
```

- [ ] **Step 6: Create `SceneRules`**

```java
package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Fmt;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.advice.Recommendation.Audience;
import io.github.edwardg117.renderable.core.advice.Recommendation.Kind;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Machines and builds in view (spec addendum §P5.B2): one method per rule, in the table's order. */
final class SceneRules {

    private SceneRules() {
    }

    static void all(Facts f, List<Recommendation> out) {
        blockEntityTypes(f, out);
        entityTypes(f, out);
        particleClasses(f, out);
        hitchRepeat(f, out);
    }

    static void blockEntityTypes(Facts f, List<Recommendation> out) {
        int n = 0;
        for (Facts.Cost c : f.blockEntityTypes()) {
            if (n == Thresholds.TOP_TYPES || c.msPerFrame() < Thresholds.TYPE_MS) break;
            n++;
            String where = c.where().isEmpty() ? "" : "; the most expensive is at " + c.where();
            out.add(new Recommendation("block-entity-type", Kind.FRAME_RATE, Audience.PLAYER, "Costly block entity: " + c.label(),
                    String.format(Locale.ROOT, "%s takes %.1f ms a frame across %s%s.",
                            c.label(), c.msPerFrame(), Words.count(c.members(), "block", "blocks"), where),
                    "It is the build, not a setting: fewer of them in view at once costs less."
                            + " Highlight on the Block Entities tab shows where they are.",
                    c.msPerFrame(), 0));
        }
    }

    static void entityTypes(Facts f, List<Recommendation> out) {
        int n = 0;
        for (Facts.Cost c : f.entityTypes()) {
            if (n == Thresholds.TOP_TYPES || c.msPerFrame() < Thresholds.TYPE_MS) break;
            n++;
            out.add(new Recommendation("entity-type", Kind.FRAME_RATE, Audience.PLAYER, "Costly entity: " + c.label(),
                    String.format(Locale.ROOT, "%s takes %.1f ms a frame across %s.",
                            c.label(), c.msPerFrame(), Words.count(c.members(), "entity", "entities")),
                    "Fewer of them nearby, or a lower Entity Distance.", c.msPerFrame(), 0));
        }
    }

    static void particleClasses(Facts f, List<Recommendation> out) {
        int n = 0;
        for (Facts.Cost c : f.particleClasses()) {
            if (n == Thresholds.TOP_TYPES || c.msPerFrame() < Thresholds.PARTICLE_CLASS_MS) break;
            n++;
            out.add(new Recommendation("particle-type", Kind.FRAME_RATE, Audience.PLAYER, "Costly particle: " + c.label(),
                    String.format(Locale.ROOT, "%s takes %.1f ms a frame, with %s alive on average.",
                            c.label(), c.msPerFrame(), Fmt.count(Math.round(c.livePerFrame()))),
                    "Find what emits them; lowering Particles hides most of them.", c.msPerFrame(), 0));
        }
    }

    /**
     * A block entity, entity or particle that caused several hitches: among a hitch's top costs with at least a
     * quarter of it, and no GC pause that could account for it. Mod keys are the mod rules'.
     */
    static void hitchRepeat(Facts f, List<Recommendation> out) {
        int hitches = f.hitches().size();
        LinkedHashMap<ProfileKey, Integer> count = new LinkedHashMap<>();
        Map<ProfileKey, Double> worst = new LinkedHashMap<>();
        Map<ProfileKey, Double> sum = new LinkedHashMap<>();
        for (Facts.Hitch h : f.hitches()) {
            Set<ProfileKey> seen = new HashSet<>();
            for (Facts.Top t : h.top()) {
                if (t.key().category() == ProfileKey.Category.MOD || !Thresholds.causes(t, h) || !seen.add(t.key())) continue;
                count.merge(t.key(), 1, Integer::sum);
                worst.merge(t.key(), t.ms(), Math::max);
                sum.merge(t.key(), t.ms(), Double::sum);
            }
        }
        for (Map.Entry<ProfileKey, Integer> e : count.entrySet()) {
            int n = e.getValue();
            if (n < Thresholds.REPEATED_HITCHES || n < Thresholds.HITCH_REPEAT_SHARE * hitches) continue;
            ProfileKey k = e.getKey();
            double w = worst.get(k);
            out.add(new Recommendation("hitch-repeat", Kind.STUTTER, Audience.PLAYER, "Repeated stutter: " + k.label(),
                    String.format(Locale.ROOT, "%s was a top cost in %d of %d hitches, up to %.0f ms.", k.label(), n, hitches, w),
                    "Look at what it is doing when the stutter happens. A pack author can take this report to the mod.",
                    sum.get(k) / f.frames(), w));
        }
    }
}
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **175** tests.

- [ ] **Step 8: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/advice src/test/java/io/github/edwardg117/renderable/core/advice
```

Report for the commit: `feat(core): recommendations for settings and for the scene`.

---

### Task 5: The memory and pack-author rules

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/advice/MemoryRules.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/advice/PackRules.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/advice/MemoryRulesTest.java` (4)
- Test: `src/test/java/io/github/edwardg117/renderable/core/advice/PackRulesTest.java` (4)

Spec addendum §P5.B2, with deviations 2 and 4 above.

- [ ] **Step 1: Write the failing tests**

```java
package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Environment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryRulesTest {

    static List<Recommendation> run(Facts f, java.util.function.BiConsumer<Facts, List<Recommendation>> rule) {
        List<Recommendation> out = new ArrayList<>();
        rule.accept(f, out);
        return out;
    }

    static FactsBuilder machine(String physicalMib, String heapMib) {
        return new FactsBuilder().env(Environment.PHYSICAL_MEMORY_MIB, physicalMib).env("maxHeapMiB", heapMib);
    }

    @Test
    void heapVsRamAdvisesTheLargestSafeAllocation() {
        Recommendation a = run(machine("16384", "14336").build(), MemoryRules::heapVsRam).get(0);
        assertEquals(Recommendation.Kind.NOTE, a.kind());
        assertEquals("14 GiB is allocated on a machine with 16 GiB, which leaves 2 GiB for Windows, drivers and the"
                + " game's memory outside the heap.", a.finding());
        assertEquals("Allocate at most 12 GiB, this machine's memory less 4 GiB, rounded down.", a.action());

        Recommendation stalled = run(machine("16384", "14336").gc(3, 900, 600).build(), MemoryRules::heapVsRam).get(0);
        assertEquals(Recommendation.Kind.STUTTER, stalled.kind(), "a pause of half a second or more is the symptom");
        assertEquals(600, stalled.worstMs(), 1e-9);
        assertEquals(0.9, stalled.msPerFrame(), 1e-9);

        assertTrue(run(machine("32768", "8192").build(), MemoryRules::heapVsRam).isEmpty(), "24 GiB to spare");

        assertEquals("This machine is short of memory for this pack: close other programs while playing.",
                run(machine("4096", "3072").build(), MemoryRules::heapVsRam).get(0).action(), "not a whole GiB to spare");
    }

    @Test
    void heapVsRamSaysTheMachineIsShortWhenTheLiveDataNeedsMore() {
        Recommendation a = run(machine("16384", "14336").env(Environment.LIVE_HEAP_AFTER_GC_MIB, "8192").build(),
                MemoryRules::heapVsRam).get(0);
        assertEquals("The live data alone is 8 GiB, so this machine is short of memory for this pack: close other"
                + " programs while playing.", a.action());
    }

    @Test
    void heapSmallSuggestsOnePointSixTimesTheLiveData() {
        Recommendation a = run(machine("32768", "8192").env(Environment.LIVE_HEAP_AFTER_GC_MIB, "7066")
                .gc(10, 183, 30).build(), MemoryRules::heapSmall).get(0);
        assertEquals(Recommendation.Kind.FRAME_RATE, a.kind());
        assertEquals("After every collection at least 6.9 of 8 GiB was still in use, so the collector runs often.", a.finding());
        assertEquals("Allocate 12 GiB (1.6 x the live data, rounded up to a whole GiB).", a.action());
        assertEquals(0.183, a.msPerFrame(), 1e-9, "the session's GC time per frame");

        Recommendation cannot = run(machine("12288", "8192").env(Environment.LIVE_HEAP_AFTER_GC_MIB, "7066").build(),
                MemoryRules::heapSmall).get(0);
        assertEquals("This machine cannot spare 12 GiB (1.6 x the live data): close other programs, or lower render"
                + " distance so the game needs less.", cannot.action());

        assertTrue(run(machine("32768", "8192").env(Environment.LIVE_HEAP_AFTER_GC_MIB, "4096").build(),
                MemoryRules::heapSmall).isEmpty(), "half full");
    }

    @Test
    void gcPausesRecommendZgcWithTheFlagsTheJavaVersionNeeds() {
        FactsBuilder g1 = new FactsBuilder().env(Environment.GC, "G1").env("java", "21.0.3 (Eclipse Adoptium)").gc(14, 610, 120);
        Recommendation a = run(g1.build(), MemoryRules::gcPauses).get(0);
        assertEquals(Recommendation.Kind.STUTTER, a.kind());
        assertEquals("Garbage collection paused the game for up to 120 ms (14 collections, 610 ms in total).", a.finding());
        assertEquals("On Java 21, add -XX:+UseZGC -XX:+ZGenerational to the JVM arguments. ZGC pauses for well under a"
                + " millisecond but uses more CPU, and more memory for the same data, so keep a quarter of the heap free.",
                a.action());
        assertEquals(120, a.worstMs(), 1e-9);

        Recommendation newer = run(new FactsBuilder().env(Environment.GC, "G1").env("java", "23.0.1").gc(14, 610, 120).build(),
                MemoryRules::gcPauses).get(0);
        assertTrue(newer.action().startsWith("On Java 23, add -XX:+UseZGC to"), newer.action());

        assertTrue(run(new FactsBuilder().env(Environment.GC, "ZGC generational").env("java", "21").gc(14, 610, 120).build(),
                MemoryRules::gcPauses).isEmpty(), "already on ZGC");
        assertTrue(run(new FactsBuilder().env(Environment.GC, "G1").env("java", "21").gc(14, 400, 40).build(),
                MemoryRules::gcPauses).isEmpty(), "under 50 ms");
        assertTrue(run(new FactsBuilder().env("java", "21").gc(14, 610, 120).build(), MemoryRules::gcPauses).isEmpty(),
                "never fires on missing data: the collector is not known");
    }
}
```

```java
package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.ModKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.edwardg117.renderable.core.advice.FactsBuilder.hitch;
import static io.github.edwardg117.renderable.core.advice.FactsBuilder.top;
import static org.junit.jupiter.api.Assertions.*;

class PackRulesTest {

    static List<Recommendation> run(Facts f, java.util.function.BiConsumer<Facts, List<Recommendation>> rule) {
        List<Recommendation> out = new ArrayList<>();
        rule.accept(f, out);
        return out;
    }

    @Test
    void anUnattributedStallNamesTheWorstHitch() {
        Facts f = new FactsBuilder()
                .hitch(new Facts.Hitch(1, 382.4, 0, 0, false, 374, 1, 0, List.of()))
                .hitch(new Facts.Hitch(2, 112.1, 0, 0, false, 101, 1, 0, List.of()))
                .build();
        Recommendation a = run(f, PackRules::unattributedStall).get(0);
        assertEquals(Recommendation.Audience.PACK_AUTHOR, a.audience());
        assertEquals("Hitch #1 (382 ms): 374 ms of client tick is in code Renderable does not time.", a.finding());
        assertEquals("Profile the same place with a sampling profiler such as spark, which names the code.", a.action());
        assertEquals(374, a.worstMs(), 1e-9);
        assertEquals(0.475, a.msPerFrame(), 1e-9, "both stalls, over the 1,000 frames");

        assertTrue(run(new FactsBuilder()
                .hitch(new Facts.Hitch(1, 80, 0, 0, false, 20, 1, 0, List.of()))
                .hitch(new Facts.Hitch(2, 200, 0, 0, false, 0, 60, 0, List.of()))
                .build(), PackRules::unattributedStall).isEmpty(), "too small, or under half the frame");
        assertTrue(run(new FactsBuilder().hitch(new Facts.Hitch(1, 382.4, 350, 0, false, 374, 1, 0, List.of())).build(),
                PackRules::unattributedStall).isEmpty(), "a 350 ms GC pause leaves 24 ms untimed");
    }

    @Test
    void modHandlersRankBySteadyCostOrByTheirWorstStall() {
        ModKey tick = ModKey.event("slowmod", "net.neoforged.neoforge.client.event.ClientTickEvent$Post");
        Facts f = new FactsBuilder()
                .mod("somemod", 1.2, 0, 0, "RenderLevelStageEvent")
                .mod("slowmod", 0.2, 0, 0, "ClientTickEvent.Post")
                .mod("minecraft", 5, 5, 5, "RenderLevelStageEvent")
                .mod("neoforge", 5, 0, 0, "RenderLevelStageEvent")
                .mod("unknown", 5, 0, 0, "RenderLevelStageEvent")
                .hitch(hitch(1, 382.4, top(tick, 370)))
                .build();
        List<Recommendation> r = run(f, PackRules::modHandlers);
        assertEquals(2, r.size(), "never for minecraft, neoforge or unknown");
        assertEquals(Recommendation.Kind.FRAME_RATE, r.get(0).kind());
        assertEquals("somemod's event handlers", r.get(0).title());
        assertEquals("somemod's event handlers take 1.2 ms a frame, most of it in RenderLevelStageEvent.", r.get(0).finding());
        assertEquals("Check the mod's config for the feature behind that event, or report it to the mod with this file.",
                r.get(0).action());
        assertEquals(Recommendation.Kind.STUTTER, r.get(1).kind());
        assertEquals("slowmod's ClientTickEvent.Post handler took 370 ms in hitch #1.", r.get(1).finding());
        assertEquals(370, r.get(1).worstMs(), 1e-9);

        ModKey stage = ModKey.event("repeatmod", "net.neoforged.neoforge.client.event.RenderLevelStageEvent");
        FactsBuilder repeated = new FactsBuilder().mod("repeatmod", 0.1, 0, 0, "RenderLevelStageEvent");
        for (int i = 1; i <= 4; i++) repeated.hitch(hitch(i, 80, top(stage, 20 + i)));
        Recommendation again = run(repeated.build(), PackRules::modHandlers).get(0);
        assertEquals(Recommendation.Kind.STUTTER, again.kind(), "under both the stall form and the steady threshold");
        assertEquals("repeatmod's RenderLevelStageEvent handler was a top cost in 4 of 4 hitches, up to 24 ms.", again.finding());
        assertEquals(24, again.worstMs(), 1e-9);
    }

    @Test
    void modHudAndNetworkNameTheMod() {
        ModKey sync = ModKey.packet("netmod", "netmod:sync");
        FactsBuilder b = new FactsBuilder()
                .mod("hudmod", 0, 0.8, 0, "")
                .mod("netmod", 0, 0, 0.6, "")
                .mod("quietmod", 0, 0, 0.3, "");
        for (int i = 1; i <= 4; i++) b.hitch(hitch(i, 60, top(sync, 20 + i)));
        Facts f = b.build();

        Recommendation hud = run(f, PackRules::modHud).get(0);
        assertEquals("hudmod's HUD layers take 0.8 ms a frame.", hud.finding());
        assertEquals("Check the mod's HUD settings, or report it to the mod with this file.", hud.action());

        List<Recommendation> network = run(f, PackRules::modNetwork);
        assertEquals(1, network.size(), "quietmod is under half a millisecond and in no hitch");
        assertEquals(Recommendation.Kind.STUTTER, network.get(0).kind());
        assertEquals("netmod's payloads take 0.6 ms a frame on the client, and one type appears in 4 hitches.",
                network.get(0).finding());
        assertEquals("The server is sending work the client handles on its main thread; report it to the mod with this file.",
                network.get(0).action());
        assertEquals(24, network.get(0).worstMs(), 1e-9);
    }

    @Test
    void unbalancedTimingIsANoteForThePackAuthor() {
        Recommendation a = run(new FactsBuilder().unbalanced(12).build(), PackRules::stackResets).get(0);
        assertEquals(Recommendation.Kind.NOTE, a.kind());
        assertEquals("12 frames had unbalanced timing: a timed call did not return normally.", a.finding());
        assertEquals("The game log from around the session should hold the exception; report it with the log.", a.action());
        assertTrue(run(new FactsBuilder().build(), PackRules::stackResets).isEmpty());
    }
}
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*MemoryRulesTest' --tests '*PackRulesTest'`
Expected: compilation failure, `cannot find symbol: class MemoryRules`.

- [ ] **Step 3: Create `MemoryRules`**

```java
package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.advice.Recommendation.Audience;
import io.github.edwardg117.renderable.core.advice.Recommendation.Kind;

import java.util.List;
import java.util.Locale;

/** Memory allocation and launch arguments (spec addendum §P5.B2): one method per rule, in the table's order. */
final class MemoryRules {

    private MemoryRules() {
    }

    static void all(Facts f, List<Recommendation> out) {
        heapVsRam(f, out);
        heapSmall(f, out);
        gcPauses(f, out);
    }

    /** The swap-stall problem: a heap so large that the system and the game's off-heap memory are squeezed. */
    static void heapVsRam(Facts f, List<Recommendation> out) {
        double physical = f.number(Environment.PHYSICAL_MEMORY_MIB);
        double heap = f.number("maxHeapMiB");
        if (Double.isNaN(physical) || Double.isNaN(heap) || physical - heap >= Thresholds.RAM_RESERVE_MIB) return;
        double spare = physical - Thresholds.RAM_RESERVE_MIB;
        double live = f.number(Environment.LIVE_HEAP_AFTER_GC_MIB);
        String action;
        if (!Double.isNaN(live) && live * Thresholds.HEAP_GROWTH > spare) {
            action = "The live data alone is " + Words.gib(live) + " GiB, so this machine is short of memory for this pack:"
                    + " close other programs while playing.";
        } else if (spare < 1024) {
            action = "This machine is short of memory for this pack: close other programs while playing.";
        } else {
            action = String.format(Locale.ROOT, "Allocate at most %d GiB, this machine's memory less 4 GiB, rounded down.",
                    (long) Math.floor(spare / 1024));
        }
        boolean stalled = f.maxFrameGcMs() >= Thresholds.RAM_STALL_GC_MS;
        out.add(new Recommendation("heap-vs-ram", stalled ? Kind.STUTTER : Kind.NOTE, Audience.PLAYER,
                "Allocated memory leaves too little for the system",
                String.format(Locale.ROOT, "%s GiB is allocated on a machine with %s GiB, which leaves %s GiB for Windows,"
                                + " drivers and the game's memory outside the heap.",
                        Words.gib(heap), Words.gib(physical), Words.gib(Math.max(0, physical - heap))),
                action, stalled ? gcPerFrame(f) : 0, stalled ? f.maxFrameGcMs() : 0));
    }

    /** A heap nearly full of live data even after collecting, so the collector runs often. */
    static void heapSmall(Facts f, List<Recommendation> out) {
        double live = f.number(Environment.LIVE_HEAP_AFTER_GC_MIB);
        double heap = f.number("maxHeapMiB");
        if (Double.isNaN(live) || Double.isNaN(heap) || heap <= 0 || live < Thresholds.LIVE_HEAP_SHARE * heap) return;
        long suggest = (long) Math.ceil(live * Thresholds.HEAP_GROWTH / 1024);
        double physical = f.number(Environment.PHYSICAL_MEMORY_MIB);
        boolean cannotSpare = !Double.isNaN(physical) && suggest * 1024 > physical - Thresholds.RAM_RESERVE_MIB;
        String action = cannotSpare
                ? String.format(Locale.ROOT, "This machine cannot spare %d GiB (1.6 x the live data): close other programs,"
                        + " or lower render distance so the game needs less.", suggest)
                : String.format(Locale.ROOT, "Allocate %d GiB (1.6 x the live data, rounded up to a whole GiB).", suggest);
        out.add(new Recommendation("heap-small", Kind.FRAME_RATE, Audience.PLAYER, "The heap is nearly full",
                String.format(Locale.ROOT, "After every collection at least %s of %s GiB was still in use, so the collector runs often.",
                        Words.gib(live), Words.gib(heap)),
                action, gcPerFrame(f), 0));
    }

    /**
     * Long stop-the-world pauses under a collector known to have them; ZGC's are well under a millisecond. An
     * unknown collector, or none recorded, is missing data, so the rule stays quiet.
     */
    static void gcPauses(Facts f, List<Recommendation> out) {
        String gc = f.text(Environment.GC);
        if (!"G1".equals(gc) && !"Parallel".equals(gc) && !"Serial".equals(gc)) return;
        int java = Words.javaMajor(f.text("java"));
        if (f.maxFrameGcMs() < Thresholds.GC_PAUSE_MS || java < 21) return;
        String flags = java <= 22 ? "-XX:+UseZGC -XX:+ZGenerational" : "-XX:+UseZGC";
        out.add(new Recommendation("gc-pauses", Kind.STUTTER, Audience.PLAYER, "Long garbage-collection pauses",
                String.format(Locale.ROOT, "Garbage collection paused the game for up to %d ms (%d collections, %d ms in total).",
                        f.maxFrameGcMs(), f.gcCount(), f.gcTotalMs()),
                String.format(Locale.ROOT, "On Java %d, add %s to the JVM arguments. ZGC pauses for well under a millisecond"
                        + " but uses more CPU, and more memory for the same data, so keep a quarter of the heap free.", java, flags),
                gcPerFrame(f), f.maxFrameGcMs()));
    }

    private static double gcPerFrame(Facts f) {
        return f.frames() == 0 ? 0 : (double) f.gcTotalMs() / f.frames();
    }
}
```

- [ ] **Step 4: Create `PackRules`**

```java
package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.advice.Recommendation.Audience;
import io.github.edwardg117.renderable.core.advice.Recommendation.Kind;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    /**
     * A mod's event handlers: steady cost, one handler that took most of a hitch, or one that caused several
     * hitches by {@code hitch-repeat}'s test. The stutter forms exist because a stall averages to little over a
     * session (plan 5b, deviations 2 and 6).
     */
    static void modHandlers(Facts f, List<Recommendation> out) {
        int hitches = f.hitches().size();
        for (Facts.ModCost m : f.mods()) {
            if (!Thresholds.isMod(m.modId())) continue;
            Facts.Hitch stallHitch = null;
            ModKey stallKey = null;
            double stallMs = 0;
            LinkedHashMap<ModKey, Integer> repeats = new LinkedHashMap<>();
            LinkedHashMap<ModKey, Double> repeatWorst = new LinkedHashMap<>();
            for (Facts.Hitch h : f.hitches()) {
                Set<ModKey> seen = new HashSet<>();
                for (Facts.Top t : h.top()) {
                    if (!(t.key() instanceof ModKey k) || k.kind() != ModKey.Kind.EVENT || !k.modId().equals(m.modId())
                            || t.gc()) continue;
                    if (t.ms() >= Thresholds.STALL_MS && t.ms() >= Thresholds.STALL_SHARE * h.totalMs() && t.ms() > stallMs) {
                        stallMs = t.ms();
                        stallHitch = h;
                        stallKey = k;
                    }
                    if (Thresholds.causes(t, h) && seen.add(k)) {
                        repeats.merge(k, 1, Integer::sum);
                        repeatWorst.merge(k, t.ms(), Math::max);
                    }
                }
            }
            ModKey repeatKey = null;
            int repeatCount = 0;
            for (Map.Entry<ModKey, Integer> e : repeats.entrySet()) {
                int n = e.getValue();
                if (n >= Thresholds.REPEATED_HITCHES && n >= Thresholds.HITCH_REPEAT_SHARE * hitches && n > repeatCount) {
                    repeatKey = e.getKey();
                    repeatCount = n;
                }
            }
            boolean costly = m.handlersMs() >= Thresholds.MOD_MS;
            if (!costly && stallKey == null && repeatKey == null) continue;
            String average = costly ? String.format(Locale.ROOT, " Its handlers average %.1f ms a frame.", m.handlersMs()) : "";
            String finding;
            double worst;
            if (stallKey != null) {
                worst = stallMs;
                finding = String.format(Locale.ROOT, "%s's %s handler took %.0f ms in hitch #%d.", m.modId(),
                        ModKey.simpleEventName(stallKey.name()), stallMs, stallHitch.number()) + average;
            } else if (repeatKey != null) {
                worst = repeatWorst.get(repeatKey);
                finding = String.format(Locale.ROOT, "%s's %s handler was a top cost in %d of %d hitches, up to %.0f ms.",
                        m.modId(), ModKey.simpleEventName(repeatKey.name()), repeatCount, hitches, worst) + average;
            } else {
                worst = 0;
                finding = String.format(Locale.ROOT, "%s's event handlers take %.1f ms a frame, most of it in %s.",
                        m.modId(), m.handlersMs(), m.topEvent());
            }
            out.add(new Recommendation("mod-handlers", worst > 0 ? Kind.STUTTER : Kind.FRAME_RATE, Audience.PACK_AUTHOR,
                    m.modId() + "'s event handlers", finding,
                    "Check the mod's config for the feature behind that event, or report it to the mod with this file.",
                    m.handlersMs(), worst));
        }
    }

    static void modHud(Facts f, List<Recommendation> out) {
        for (Facts.ModCost m : f.mods()) {
            if (!Thresholds.isMod(m.modId()) || m.layersMs() < Thresholds.MOD_MS) continue;
            out.add(new Recommendation("mod-hud", Kind.FRAME_RATE, Audience.PACK_AUTHOR, m.modId() + "'s HUD",
                    String.format(Locale.ROOT, "%s's HUD layers take %.1f ms a frame.", m.modId(), m.layersMs()),
                    "Check the mod's HUD settings, or report it to the mod with this file.", m.layersMs(), 0));
        }
    }

    /** A mod's payloads: steady cost, or a packet type that caused several hitches (deviation 6). */
    static void modNetwork(Facts f, List<Recommendation> out) {
        for (Facts.ModCost m : f.mods()) {
            if (!Thresholds.isMod(m.modId())) continue;
            LinkedHashMap<ModKey, Integer> inHitches = new LinkedHashMap<>();
            double worst = 0;
            for (Facts.Hitch h : f.hitches()) {
                Set<ModKey> seen = new HashSet<>();
                for (Facts.Top t : h.top()) {
                    if (t.key() instanceof ModKey k && k.kind() == ModKey.Kind.PACKET && k.modId().equals(m.modId())
                            && Thresholds.causes(t, h) && seen.add(k)) {
                        inHitches.merge(k, 1, Integer::sum);
                        worst = Math.max(worst, t.ms());
                    }
                }
            }
            int most = inHitches.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            boolean stutter = most >= Thresholds.REPEATED_HITCHES;
            if (m.packetsMs() < Thresholds.MOD_MS && !stutter) continue;
            out.add(new Recommendation("mod-network", stutter ? Kind.STUTTER : Kind.FRAME_RATE, Audience.PACK_AUTHOR,
                    m.modId() + "'s network payloads",
                    String.format(Locale.ROOT, "%s's payloads take %.1f ms a frame on the client", m.modId(), m.packetsMs())
                            + (stutter ? String.format(Locale.ROOT, ", and one type appears in %d hitches.", most) : "."),
                    "The server is sending work the client handles on its main thread; report it to the mod with this file.",
                    m.packetsMs(), stutter ? worst : 0));
        }
    }

    static void stackResets(Facts f, List<Recommendation> out) {
        if (f.unbalancedFrames() <= 0) return;
        out.add(new Recommendation("stack-resets", Kind.NOTE, Audience.PACK_AUTHOR, "Unbalanced timing",
                String.format(Locale.ROOT, "%s had unbalanced timing: a timed call did not return normally.",
                        Words.count(f.unbalancedFrames(), "frame", "frames")),
                "The game log from around the session should hold the exception; report it with the log.", 0, 0));
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **183** tests.

- [ ] **Step 6: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/advice src/test/java/io/github/edwardg117/renderable/core/advice
```

Report for the commit: `feat(core): recommendations for memory, launch arguments and the pack author`.

---

### Task 6: `Advisor`, and the advice in the report, the JSON and the chat line

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/advice/Advisor.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/EmptyStates.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/JsonReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/advice/AdvisorTest.java` (3)
- Test: `src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java` (1)
- Test: `src/test/java/io/github/edwardg117/renderable/report/JsonReportWriterTest.java` (1)

Spec addendum §P5.B1 (the ranking) and §P5.B5.

- [ ] **Step 1: Write the failing tests**

```java
package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.TestSessions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.edwardg117.renderable.core.advice.FactsBuilder.hitch;
import static io.github.edwardg117.renderable.core.advice.FactsBuilder.top;
import static org.junit.jupiter.api.Assertions.*;

class AdvisorTest {

    @Test
    void stutterComesFirstThenFrameRateThenNotes() {
        BlockEntityKey crafter = new BlockEntityKey("create:mechanical_crafter", "minecraft:overworld", 1, 2, 3);
        FactsBuilder b = new FactsBuilder().frameMs(12)
                .bucket(Bucket.ENTITY, 2.4).env("entityDistance", "3.0")            // entity-distance, 2.4 ms/frame
                .bucket(Bucket.CHUNK_UPLOAD, 1.8).env("biomeBlend", "3")            // chunk-rebuilds, 1.8 ms/frame
                .cap(4)                                                             // frame-cap, a note
                .env(Environment.GC, "G1").env("java", "21.0.3").gc(14, 610, 120)   // gc-pauses, up to 120 ms
                .env(Environment.PHYSICAL_MEMORY_MIB, "16384").env("maxHeapMiB", "14336")   // heap-vs-ram, a note
                .unbalanced(2);                                                     // stack-resets, a note
        for (int i = 1; i <= 4; i++) b.hitch(hitch(i, 80, top(crafter, 60)));        // hitch-repeat, up to 60 ms

        assertEquals(List.of("gc-pauses", "hitch-repeat", "entity-distance", "chunk-rebuilds", "frame-cap",
                        "heap-vs-ram", "stack-resets"),
                Advisor.advise(b.build()).stream().map(Recommendation::rule).toList());
    }

    @Test
    void aQuietSessionGetsNoAdvice() {
        assertTrue(Advisor.advise(new FactsBuilder().build()).isEmpty());
    }

    @Test
    void thePhase5FixtureGetsItsModsAndCostliestThings() {
        List<Recommendation> advice = Advisor.advise(TestSessions.phase5(), Environment.empty());
        assertEquals(List.of("mod-handlers", "block-entity-type", "entity-type", "mod-hud", "mod-handlers"),
                advice.stream().map(Recommendation::rule).toList(),
                "by ms/frame; the three at 1.0 keep the rules' order, the player's first");
        assertEquals("create's event handlers", advice.get(0).title());
        assertEquals("somemod's event handlers", advice.get(4).title());
        assertTrue(advice.stream().noneMatch(a -> a.title().startsWith("minecraft")), "vanilla packets are not a mod");
    }
}
```


Append to `TextReportWriterTest` (it needs `import io.github.edwardg117.renderable.core.Session;`,
`import io.github.edwardg117.renderable.core.SessionConfig;`,
`import io.github.edwardg117.renderable.core.advice.Recommendation;` and `import java.util.ArrayList;`):

```java
    @Test
    void recommendationsLeadTheReport() {
        String s = TextReportWriter.write(TestSessions.phase5(), env, "0.5.0");
        int at = s.indexOf("Recommendations\n");
        assertTrue(at > 0 && at < s.indexOf("\nEnvironment\n"), "before the environment");
        assertTrue(s.contains("\n  [pack author] create's event handlers  (2.0 ms/frame)\n"
                + "    create's event handlers take 2.0 ms a frame, most of it in RenderLevelStageEvent.\n"
                + "    Check the mod's config for the feature behind that event, or report it to the mod with this file.\n"), s);
        assertTrue(s.contains("\n  [player] Costly block entity: create:mechanical_press  (1.0 ms/frame)\n"), s);
        assertFalse(s.contains("more on the Advice tab"), "all five fit under the limit of six");

        long[] now = {0};
        Session quiet = new Session(new SessionConfig(10_000_000_000L, 2.0, 16_000_000L, 5, 0, 0), () -> now[0], 0);
        quiet.onRenderPre(0, 0);
        now[0] = 10_000_000L;
        quiet.onRenderPre(0, 0);
        assertTrue(TextReportWriter.write(quiet.finish(), env, "0.5.0")
                .contains("Recommendations\n  No recommendations: nothing measured crossed a threshold.\n\n"));

        List<Recommendation> eight = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            eight.add(new Recommendation("r" + i, Recommendation.Kind.FRAME_RATE, Recommendation.Audience.PLAYER,
                    "Title " + i, "Finding " + i + ".", "Action " + i + ".", 9 - i, 0));
        }
        StringBuilder sb = new StringBuilder();
        TextReportWriter.appendRecommendations(sb, eight);
        String cut = sb.toString();
        assertEquals(6, cut.split("\n  \\[player\\] ", -1).length - 1, cut);
        assertTrue(cut.contains("\n  [player] Title 6  (3.0 ms/frame)\n"), cut);
        assertFalse(cut.contains("Title 7"), cut);
        assertTrue(cut.endsWith("\n  2 more on the Advice tab and in the JSON.\n\n"), cut);
    }
```

Append to `JsonReportWriterTest`:

```java
    @Test
    void everyRecommendationIsInTheJson() {
        JsonObject o = JsonParser.parseString(JsonReportWriter.write(TestSessions.phase5(), environment(), "0.5.0"))
                .getAsJsonObject();
        JsonArray advice = o.getAsJsonArray("recommendations");
        assertEquals(5, advice.size());
        JsonObject first = advice.get(0).getAsJsonObject();
        assertEquals(List.of("rule", "kind", "audience", "title", "finding", "action", "msPerFrame", "worstMs"),
                List.copyOf(first.keySet()));
        assertEquals("mod-handlers", first.get("rule").getAsString());
        assertEquals("FRAME_RATE", first.get("kind").getAsString());
        assertEquals("PACK_AUTHOR", first.get("audience").getAsString());
        assertEquals(2.0, first.get("msPerFrame").getAsDouble(), 1e-9);
        List<String> keys = new ArrayList<>(o.keySet());
        assertEquals(keys.indexOf("stats") + 1, keys.indexOf("recommendations"), "right after the averages");
    }
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*AdvisorTest' --tests '*TextReportWriterTest' --tests '*JsonReportWriterTest'`
Expected: compilation failure, `cannot find symbol: class Advisor`.

- [ ] **Step 3: Create `Advisor`**

```java
package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.SessionResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs every rule over a finished session and ranks what they find (spec addendum §P5.B1). Stutter comes
 * first, by its worst occurrence, then frame rate, by milliseconds per frame, then notes. Ties go to the
 * player's first, then keep the rules' order. Derived on demand, never stored with the session.
 */
public final class Advisor {
    /** How many the text report prints; the rest are on the Advice tab and in the JSON. */
    public static final int REPORT_LIMIT = 6;

    private static final Comparator<Recommendation> RANKING = Comparator
            .comparing(Recommendation::kind)
            .thenComparing((a, b) -> switch (a.kind()) {
                case STUTTER -> Double.compare(b.worstMs(), a.worstMs());
                case FRAME_RATE -> Double.compare(b.msPerFrame(), a.msPerFrame());
                case NOTE -> 0;
            })
            .thenComparing(Recommendation::audience);

    private Advisor() {
    }

    public static List<Recommendation> advise(SessionResult r, Environment env) {
        return advise(Facts.of(r, env));
    }

    static List<Recommendation> advise(Facts f) {
        List<Recommendation> out = new ArrayList<>();
        if (f.frames() == 0) return List.of();
        SettingsRules.all(f, out);
        SceneRules.all(f, out);
        MemoryRules.all(f, out);
        PackRules.all(f, out);
        out.sort(RANKING);   // stable, so ties keep the rules' order
        return List.copyOf(out);
    }
}
```

- [ ] **Step 4: The empty state**

In `core/EmptyStates.java`, at the end of the class, add:

```java
    /** Said by the report and the Advice tab when no rule fired (spec addendum §P5.B5). */
    public static String advice() {
        return "No recommendations: nothing measured crossed a threshold.";
    }
```

- [ ] **Step 5: The report section**

In `TextReportWriter.java`, add these imports:

```java
import io.github.edwardg117.renderable.core.advice.Advisor;
import io.github.edwardg117.renderable.core.advice.Recommendation;
```

Replace:

```java
                .append("  session ").append(Fmt.secs(total)).append(" s\n\n");

        sb.append("Environment\n");
```

with:

```java
                .append("  session ").append(Fmt.secs(total)).append(" s\n\n");

        appendRecommendations(sb, Advisor.advise(r, env));

        sb.append("Environment\n");
```

and add this method after `write`:

```java
    /** Spec addendum §P5.B5: the six most important recommendations, before everything else. */
    static void appendRecommendations(StringBuilder sb, List<Recommendation> advice) {
        sb.append("Recommendations\n");
        if (advice.isEmpty()) {
            sb.append("  ").append(EmptyStates.advice()).append("\n\n");
            return;
        }
        int shown = Math.min(Advisor.REPORT_LIMIT, advice.size());
        for (Recommendation a : advice.subList(0, shown)) {
            sb.append("  [").append(a.audience().label()).append("] ").append(a.title());
            if (!a.atStake().isEmpty()) sb.append("  (").append(a.atStake()).append(')');
            sb.append('\n');
            sb.append("    ").append(a.finding()).append('\n');
            sb.append("    ").append(a.action()).append('\n');
        }
        if (advice.size() > shown) {
            sb.append("  ").append(advice.size() - shown).append(" more on the Advice tab and in the JSON.\n");
        }
        sb.append('\n');
    }
```

- [ ] **Step 6: The JSON array**

In `JsonReportWriter.java`, add these imports:

```java
import io.github.edwardg117.renderable.core.advice.Advisor;
import io.github.edwardg117.renderable.core.advice.Recommendation;
```

In `body`, after the `stats` object's closing `w.endObject();`, which comes just before `w.name("frames").beginArray();`, add:

```java
        w.name("recommendations").beginArray();
        for (Recommendation a : Advisor.advise(r, env)) {
            w.beginObject();
            w.name("rule").value(a.rule());
            w.name("kind").value(a.kind().name());
            w.name("audience").value(a.audience().name());
            w.name("title").value(a.title());
            w.name("finding").value(a.finding());
            w.name("action").value(a.action());
            w.name("msPerFrame").value(a.msPerFrame());
            w.name("worstMs").value(a.worstMs());
            w.endObject();
        }
        w.endArray();
```

- [ ] **Step 7: The chat line**

In `ProfilerController.java`, add `import io.github.edwardg117.renderable.core.advice.Advisor;`, then:

1. In `finish`, replace `chat(finishedMessage(result, written, writeFailure));` with
   `chat(finishedMessage(result, written, writeFailure, Advisor.advise(result, env).size()));`.
2. Change `finishedMessage`'s signature and its first statement to:

```java
    private static MutableComponent finishedMessage(SessionResult r, ReportFiles.Written w, String writeFailure,
                                                    int suggestions) {
        MutableComponent msg = Component.literal(String.format(Locale.ROOT,
                "Renderable: %d frames in %.1f s, avg %.1f ms, %d hitches%s. ",
                r.frameCount(), r.stats().durationSeconds(), r.stats().avgNanos() / 1e6, r.hitches().size(),
                suggestions == 0 ? "" : ", " + suggestions + (suggestions == 1 ? " suggestion" : " suggestions")));
```

The rest of the method is unchanged.

- [ ] **Step 8: Run the tests**

Run: `./gradlew compileJava --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **188** tests. Existing report tests still pass: every assertion on the header,
the environment and the sections is a `contains`, which the new section does not disturb.

- [ ] **Step 9: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/advice/Advisor.java src/main/java/io/github/edwardg117/renderable/core/EmptyStates.java src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java src/main/java/io/github/edwardg117/renderable/report/JsonReportWriter.java src/main/java/io/github/edwardg117/renderable/ProfilerController.java src/test/java/io/github/edwardg117/renderable
```

Report for the commit: `feat: recommendations lead the report, and reach the JSON and the chat line`.

---

### Task 7: The Advice tab

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/ui/screen/TextWrap.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Tab.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/ResultsScreen.java`
- Test: `src/test/java/io/github/edwardg117/renderable/ui/screen/TextWrapTest.java` (1)
- Test: `src/test/java/io/github/edwardg117/renderable/ui/screen/TabTest.java` (extend)

Spec addendum §P5.B5. The tab is a list of single-column rows, each recommendation wrapped to the list's
width. The wrapping is pure Java, so it is unit-tested with a stand-in for the font. Ten tabs at five a row
still take two rows (plan 5a Task 8 set five per row).

- [ ] **Step 1: Write the failing test**

```java
package io.github.edwardg117.renderable.ui.screen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextWrapTest {

    @Test
    void wrapsAtWordsAndGivesAnOverlongWordALineOfItsOwn() {
        assertEquals(List.of("the quick", "brown fox"), TextWrap.wrap("the quick brown fox", 9, String::length));
        assertEquals(List.of("a", "extraordinarily", "b"), TextWrap.wrap("a extraordinarily b", 5, String::length));
        assertEquals(List.of(), TextWrap.wrap("", 10, String::length));
        assertEquals(List.of("one two"), TextWrap.wrap("one  two", 20, String::length), "runs of spaces collapse");
    }
}
```

In `TabTest`, in the test that requires the Frames and Hitches columns not to sort (the loop over
`new Tab[]{Tab.FRAMES, Tab.HITCHES}`), change the array to `new Tab[]{Tab.ADVICE, Tab.FRAMES, Tab.HITCHES}`.
Then add this test:

```java
    @Test
    void theScreenOpensOnAdviceAndTenTabsFitTwoRows() {
        assertEquals(Tab.ADVICE, Tab.values()[0], "a new result opens on it");
        assertEquals(10, Tab.values().length);
    }
```

The task adds these two tests, taking the total to **190**.

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*TextWrapTest' --tests '*TabTest'`
Expected: compilation failure (`TextWrap` and `Tab.ADVICE` do not exist).

- [ ] **Step 3: Create `TextWrap`**

```java
package io.github.edwardg117.renderable.ui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Word wrapping for the Advice tab, measured by the caller's font so it can be tested without one. */
final class TextWrap {

    private TextWrap() {
    }

    /**
     * Greedy wrap into lines no wider than {@code width} by {@code measure}. A word wider than that gets a line
     * of its own, which the list then truncates; runs of spaces collapse.
     */
    static List<String> wrap(String text, int width, ToIntFunction<String> measure) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            if (line.isEmpty()) {
                line.append(word);
            } else if (measure.applyAsInt(line + " " + word) <= width) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
```

- [ ] **Step 4: Add the tab**

In `Tab.java`, replace:

```java
/** Tabs of the results screen and the column layout each one uses. Nine tabs, five per row on the screen. */
public enum Tab {
    FRAMES("Frames", Columns.METRIC_VALUE),
```

with:

```java
/** Tabs of the results screen and the column layout each one uses. Ten tabs, five per row on the screen. */
public enum Tab {
    ADVICE("Advice", Columns.TEXT),
    FRAMES("Frames", Columns.METRIC_VALUE),
```

and, in `Columns`, before `METRIC_VALUE`, add:

```java
        /** One unnamed column of wrapped text: the Advice tab (spec addendum §P5.B5). */
        static final Columns TEXT = of(NONE, left("", 1, NONE));
```

- [ ] **Step 5: Build the Advice rows**

In `Rows.java`, add these imports:

```java
import io.github.edwardg117.renderable.core.advice.Advisor;
import io.github.edwardg117.renderable.core.advice.Recommendation;

import java.util.function.Function;
```

Replace the signature of `build`, its Javadoc and its first switch arm:

```java
    /**
     * Rows for one tab. {@code sort} is always the active column's sort ({@link ColumnSort#NONE} on Frames and
     * Hitches, which ignore it); {@code ascending} its direction.
     */
    public static List<Row> build(Tab tab, SessionResult r, Environment env, ColumnSort sort, boolean ascending,
                                  Set<String> expanded) {
        return switch (tab) {
            case FRAMES -> frames(r);
```

with:

```java
    /**
     * Rows for one tab. {@code sort} is always the active column's sort ({@link ColumnSort#NONE} on Advice,
     * Frames and Hitches, which ignore it); {@code ascending} its direction. {@code wrap} splits a paragraph
     * into lines that fit the list, for the Advice tab.
     */
    public static List<Row> build(Tab tab, SessionResult r, Environment env, ColumnSort sort, boolean ascending,
                                  Set<String> expanded, Function<String, List<String>> wrap) {
        return switch (tab) {
            case ADVICE -> advice(Advisor.advise(r, env), wrap);
            case FRAMES -> frames(r);
```

and add this method before `frames`:

```java
    /** Spec addendum §P5.B5 Advice tab: every recommendation, the player's first, wrapped to the list's width. */
    static List<Row> advice(List<Recommendation> advice, Function<String, List<String>> wrap) {
        List<Row> rows = new ArrayList<>();
        if (advice.isEmpty()) {
            rows.add(Row.plain(EmptyStates.advice()));
            return rows;
        }
        for (Recommendation.Audience audience : Recommendation.Audience.values()) {
            List<Recommendation> theirs = advice.stream().filter(a -> a.audience() == audience).toList();
            if (theirs.isEmpty()) continue;
            rows.add(Row.heading(audience == Recommendation.Audience.PLAYER ? "For you" : "For the pack author"));
            for (Recommendation a : theirs) {
                String title = a.atStake().isEmpty() ? a.title() : a.title() + "  (" + a.atStake() + ")";
                for (String line : wrap.apply(title)) rows.add(new Row(new String[]{line}, null, null, true, false, 1, null, -1));
                for (String line : wrap.apply(a.finding())) rows.add(new Row(new String[]{line}, null, null, false, false, 2, null, -1));
                for (String line : wrap.apply(a.action())) rows.add(new Row(new String[]{line}, null, null, false, false, 2, null, -1));
                rows.add(Row.plain(""));
            }
        }
        return rows;
    }
```

- [ ] **Step 6: Open on Advice and pass the wrapping**

In `ResultsScreen.java`:

1. Change `private Tab tab = Tab.FRAMES;` to `private Tab tab = Tab.ADVICE;`.
2. In `rebuild`, replace:

```java
        list.setRows(tab, Rows.build(tab, result, environment, sorts.sort(tab), sorts.ascending(tab), expanded));
```

with:

```java
        // A body line is drawn two indents (24 px) in, and a single-cell row loses 8 px of margin.
        int wrapWidth = Math.max(40, list.getRowWidth() - 8 - 24);
        list.setRows(tab, Rows.build(tab, result, environment, sorts.sort(tab), sorts.ascending(tab), expanded,
                text -> TextWrap.wrap(text, wrapWidth, font::width)));
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew compileJava --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **190** tests.

- [ ] **Step 8: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/ui/screen src/test/java/io/github/edwardg117/renderable/ui/screen
```

Report for the commit: `feat(ui): the Advice tab, which the results screen opens on`.

---

### Task 8: Documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md`
- Modify: `docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md`

Each replacement quotes the text to find. If a passage is not there, stop and report rather than guess.

- [ ] **Step 1: README**

In "Using it", replace the text below. It ends part-way through a line: the line goes on with
" Two files land in", which stays where it is, after the replacement.

```markdown
2. When it ends, a results screen opens and a chat message offers **Open results**, **Copy
   report** and **Open folder**.
```

with:

```markdown
2. When it ends, a results screen opens on the **Advice** tab (what to change, drawn from what was measured)
   and a chat message offers **Open results**, **Copy report** and **Open folder**.
```

In "Reading a report", insert as the first bullet:

```markdown
- **Recommendations** come first: up to six, with the rest on the Advice tab and in the JSON. Each says who
  can act on it (`[player]` or `[pack author]`), what was measured and what to do, and how much time the
  finding is about. That figure is an upper bound on what the change can save, not a prediction. Stutter
  comes first, then frame rate, then notes: a stall averaged over a session looks small but is what a player
  notices. The rules are generic, not written for any one pack, and their thresholds are first estimates.
- The environment lists physical memory, the garbage collector, the initial heap, the smallest heap in use
  after a collection (an upper bound on the pack's live data), and the JVM flags that set the heap and the
  collector. Only flags on a fixed list are included, never paths or `-D` properties, because the full list
  can carry the player's user name. Under ZGC and Shenandoah, the GC figures count pauses only, not the
  collectors' concurrent cycles.
- A hitch contributor counts toward repeated stutter only when it took at least a quarter of that hitch and
  carries no `[gc]` mark: every hitch's top list also holds the frame's usual costs.
```

In the schema 5 paragraph that plan 5a added, replace:

```markdown
and a `mods` array, one ranked row per mod followed by an `entries` array of its event, layer and packet rows.
```

with:

```markdown
a `mods` array, one ranked row per mod followed by an `entries` array of its event, layer and packet rows;
a `recommendations` array, right after `stats`, holding every recommendation with its `rule`, `kind`,
`audience`, `title`, `finding`, `action`, `msPerFrame` and `worstMs`; and `physicalMemoryMiB`, `gc`,
`initialHeapMiB`, `liveHeapAfterGcMiB` and `jvmFlags` in `environment`.
```

- [ ] **Step 2: Base spec**

1. **§3.2.** In the frame record table, replace this row:

```markdown
| `gcCount`, `gcMillis` | delta of `GarbageCollectorMXBean` collection count and time since the previous frame |
```

   with:

```markdown
| `gcCount`, `gcMillis` | delta since the previous frame of the collectors' pause beans' collection count and time: all of them under G1, Parallel and Serial, and only the `Pauses` beans under ZGC and Shenandoah, whose `Cycles` beans time concurrent work (phase 5) |
```
2. **§5.3.** In the tab table, add this row as the first:

```markdown
| Advice | the recommendations, the player's first, each wrapped as a title and two short paragraphs | none; the screen opens on this tab |
```

3. **§6.1.** Insert as item 2, and renumber the rest from 3:

```markdown
2. **Recommendations**: up to six, most important first (stutter, then frame rate, then notes), each tagged
   with its audience, with what was measured and what to do; or one line saying none crossed a threshold.
```

   In the Environment item, after "max heap, mod count;", add "physical memory, the collector, the initial
   heap, the smallest heap in use after a collection and the whitelisted JVM flags;".
4. **§6.2.** In the example, after the `"session": ...` block, add:

```
  "recommendations": [ { "rule": "gc-pauses", "kind": "STUTTER", "audience": "PLAYER", "title": ...,
                         "finding": ..., "action": ..., "msPerFrame": ..., "worstMs": ... } ],
```

5. **§11.** After the `5a` row, add:

```markdown
| 5b | the recommendation engine and its nineteen rules, the environment's memory and JVM facts, pause-only GC sampling, the Advice tab, the report's Recommendations section, the chat line's suggestion count, the JSON's `recommendations` | spec addendum §P5.B's checks, in the in-pack acceptance at the end of phase 5 |
```

- [ ] **Step 3: The phase 5 spec's corrections**

In `2026-09-26-renderable-phase-5-design.md`:

1. In §P5.B1, replace:

```markdown
- **Rules.** Each rule is a small class with unit tests on synthetic sessions (`TestSessions`).
```

   with:

```markdown
- **Rules.** Each rule is one static method in one of four themed classes (`SettingsRules`, `SceneRules`,
  `MemoryRules`, `PackRules`). It reads a `Facts` record derived once from the session, so each rule is
  unit-tested with `Facts` built in the test.
```

2. In §P5.B2's first table, replace the `mod-handlers` row:

```markdown
| `mod-handlers` | pack author | frame rate | a mod's event handlers ≥ 0.5 ms/f | that mod's handlers |
```

   with:

```markdown
| `mod-handlers` | pack author | frame rate, or stutter when one of its handlers took at least 50 ms and at least half of a hitch, or passes `hitch-repeat`'s test | a mod's event handlers ≥ 0.5 ms/f | that mod's handlers; in the stutter form, that handler's largest time in one hitch |
```

   In the `hitch-repeat` row, replace "a key is among the top contributors" with "a key other than a mod's
   (the mod rules cover those) is among the top contributors, with at least a quarter of the hitch and no
   `[gc]` mark,". In the `mod-network` row, replace "is among the top contributors of 3 or more hitches" with
   "is among the top contributors, with at least a quarter of the hitch and no `[gc]` mark, of 3 or more
   hitches". In the `gc-pauses` row, replace "the collector is not ZGC or Shenandoah" with "the collector is
   G1, Parallel or Serial". In the `unattributed-stall` row, replace "less the time the Mods tab names in it
   (P5.A6)" with "less the time the Mods tab names in it (P5.A6) and less the frame's GC pause".

   In the second table's `heap-vs-ram` row, after "the finding says instead that the machine is short of
   memory for this pack." add " So does a machine with less than a whole GiB to spare."
4. In §P5.B3, replace "A `NotificationListener` on each collector bean is" with "A `NotificationListener` on
   each collector bean except the pause beans (a ZGC or Shenandoah pause notification reports no heap in use)
   is".
3. After §P5.B2's second table, add:

```markdown

The `mod-handlers`, `mod-hud` and `mod-network` rules skip `minecraft`, `neoforge` and `unknown`: vanilla HUD
layers and packets appear in the Mods tab, but a pack author cannot change them, nor NeoForge's own handlers,
nor handlers whose mod could not be found. `mod-handlers`' stutter forms exist because `unattributed-stall`
no longer fires for time the Mods tab names, and a stall averages far below half a millisecond a frame over a
session (plan 5b, deviations 2 and 6). A hitch counts toward a repeat only when the key took at least a
quarter of it and carries no `[gc]` mark, because every hitch's top list also holds the frame's usual costs.
```

- [ ] **Step 4: Stage**

```bash
git add README.md docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md
```

Report for the commit: `docs: README and specs for phase 5b`.

---

### Task 9: Check it in the dev client

**Files:** none in the repository.

As in plan 5a Task 11. The user's real Gears & Frontiers client may be running, so stop only the dev client,
through its Gradle wrapper (plan 5a Task 11, Step 4, has the commands and the rules).

- [ ] **Step 1: Build and test**

Run: `./gradlew build --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **190** tests.

- [ ] **Step 2: A G1 run**

Launch it in the background:

```bash
JAVA_TOOL_OPTIONS="-Drenderable.autostart=20" ./gradlew runClient -PquickPlay="New World" -PpackMods --no-daemon
```

(with the world from plan 5a Task 11). Wait for the new report, then stop the client. Then:

```bash
python - <<'EOF'
import glob, json, os
f = max(glob.glob("run/renderable/renderable-*.json"), key=os.path.getmtime)
r = json.load(open(f, encoding="utf-8"))
env = r["environment"]
for k in ("maxHeapMiB", "initialHeapMiB", "physicalMemoryMiB", "gc", "liveHeapAfterGcMiB", "jvmFlags", "eventTiming"):
    print(k, "=", env.get(k))
print("schema", r["schema"], "gcCount", r["stats"]["gcCount"], "gcMs", r["stats"]["gcMs"])
for a in r["recommendations"]:
    print(a["kind"], a["audience"], a["rule"], "|", a["title"], "|", a["finding"])
EOF
grep -n "\[CHAT\] Renderable:.*frames in" run/logs/latest.log | tail -1
sed -n '/^Recommendations/,/^Environment/p' "$(ls -t run/renderable/*.txt | head -1)"
```

Expected:

- `gc` is `G1`;
- `physicalMemoryMiB` is the machine's memory (about 63100 here);
- `jvmFlags` holds only heap and collector flags, or is empty, and no path;
- `recommendations` exists, possibly empty;
- the chat line ends `N hitches.` or `N hitches, M suggestions.`, with M equal to the array's length;
- the report's first section is `Recommendations`.

`liveHeapAfterGcMiB` is present only if a collection ran during the session.

- [ ] **Step 3: A ZGC run**

Repeat Step 2 with `JAVA_TOOL_OPTIONS="-Drenderable.autostart=20 -XX:+UseZGC -XX:+ZGenerational"`. Expected:

- `gc` is `ZGC generational`;
- `jvmFlags` contains `-XX:+UseZGC -XX:+ZGenerational`;
- `liveHeapAfterGcMiB`, if present, is not 0: a 0 means the pause beans' notifications were counted;
- `stats.gcMs` is small against `stats.gcCount`: ZGC pauses are well under a millisecond each, and the
  concurrent cycles are no longer counted.

Record `gcCount` and `gcMs`.

- [ ] **Step 4: Report**

Report to the orchestrator: both runs' output, and anything unexpected. The things to look for are a path
in `jvmFlags`, a `gc` name that is not one of the six, a chat count that disagrees with the array, or a ZGC
`gcMs` that looks like cycle time: tens of milliseconds a collection. Nothing is staged in this task.

---

## After this plan

Leave `phase-5b-advice` unmerged. Plan 5d branches from it, then 5c (spec addendum §P5.2).
