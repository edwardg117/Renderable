# Renderable Phase 5a Implementation Plan: attribution

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Name the frame time Renderable cannot name yet. Split packets and the frame-cap wait out of swap, give the HUD a bucket, time every mod's event handlers, HUD layers and packets and roll them up by mod in a new Mods tab, time a rider's own tick, and move the frame and tick boundaries so all of it lands in the window it belongs to.

**Architecture:**

- **`core`** gains:
  - two frame components, `tasksNanos` and `capNanos`;
  - a `HUD` bucket and a `Window` enum (render, tick, tasks);
  - a `MOD` key category with its key, `ModKey`;
  - **attribution-only calls**: timed and ranked like any keyed call, but owning no bucket, so the time they account for is recorded per window as *named* time.
- **Mixins in `Minecraft`** move the frame and tick boundaries to NeoForge's call sites, and time tasks and the cap.
- **Further mixins** time the HUD (`Gui`), each HUD layer (`GuiLayerManager`), packets (`PacketUtils`), main-thread payloads (`MainThreadPayloadHandler`) and rider ticks (`ClientLevel`).
- **Event handlers are timed without a mixin.** `hooks.events` swaps each `ListenerList`'s cached array on NeoForge's game bus for `TimedListener` proxies at session start, and back at the end.
- **Output.** The Mods tab, the report's Mods section and the JSON `mods` array reuse the ranked-table machinery.

**Tech Stack:** Java 21, ModDevGradle 2.0.147, NeoForge 21.1.220, `net.neoforged:bus` 8.0.5 (reflection into its open module), Mixin with MixinExtras 0.5.0 (`@WrapOperation`, `@WrapMethod`, `@Inject`, `@Local`), Gson's streaming writer, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md` §P5.A (all of it), and the base spec sections that §P5.3 assigns to plan 5a.

**Repository:** `H:\repos\Renderable`. Create the branch `phase-5a-attribution` from `main` before Task 1. Commits are YubiKey-signed: the orchestrator commits after telling the user to touch the key. **Subagents stage and report; they do not commit.**

**Ordering:** the first phase 5 plan. 5b builds on it, 5d runs after 5b, and 5c runs last (§P5.2).

**Verified facts you can rely on** (javap against `build/moddev/artifacts/neoforge-21.1.220-merged.jar` and `bus-8.0.5.jar` on 2026-09-26; the spec's §P5.A0 has the full table):

- **`Minecraft.runTick(Z)V`** calls, in this order:
  - `Minecraft.runAllTasks()V` (INVOKEVIRTUAL, owner `net/minecraft/client/Minecraft`, offset 120);
  - `ClientHooks.fireRenderFramePre(DeltaTracker)V` (INVOKESTATIC, 398);
  - `ClientHooks.fireRenderFramePost(DeltaTracker)V` (INVOKESTATIC, 438);
  - `RenderSystem.limitDisplayFPS(I)V` (INVOKESTATIC, 603), reached only when `getFramerateLimit() < 260`.

  `runTick` is the only method of that name.
- **`Minecraft.tick()V`** calls `ClientHooks.fireClientTickPre()V` at offset 10 and `ClientHooks.fireClientTickPost()V` at offset 866. `Minecraft` has one `tick()`, which is public.
- **`Gui.render(GuiGraphics, DeltaTracker)V`** is the only `render` in `Gui`. `GameRenderer.render` calls it once, at offset 606.
- **`GuiLayerManager.renderInner(GuiGraphics, DeltaTracker)V`** is private. At offset 80 it calls `LayeredDraw$Layer.render(GuiGraphics, DeltaTracker)V` (INVOKEINTERFACE). Local 4 is the loop's `GuiLayerManager$NamedLayer`, a public record with `name()` returning a `ResourceLocation`, and it is the only local of that type.
- **`PacketUtils.lambda$ensureRunningOnSameThread$0(PacketListener, Packet)V`** is private static. At offset 12 it calls `Packet.handle(PacketListener)V` (INVOKEINTERFACE). `Packet.type()` returns a constant `PacketType` record with `id()`.
- **`MainThreadPayloadHandler.lambda$handle$0(CustomPacketPayload, IPayloadContext)V`** is private and not static. At offset 6 it calls `IPayloadHandler.handle(CustomPacketPayload, IPayloadContext)V` (INVOKEINTERFACE). `CustomPacketPayload.type()` returns a constant `CustomPacketPayload$Type` with `id()`.
- **`ClientLevel.tickPassenger(Entity, Entity)V`** is private and the only method of that name. At offset 55 it calls `Entity.rideTick()V` (INVOKEVIRTUAL) on the passenger.
- **Bus 8.0.5.**
  - **Module.** `open module net.neoforged.bus`, exporting `net.neoforged.bus` and `net.neoforged.bus.api`. `EventBus`, `ListenerList`, `LockHelper` and `IWrapperListener` are public classes.
  - **`EventBus` fields.**
    - `listenerLists` is a private `LockHelper<Class<?>, ListenerList>`.
    - `LockHelper.getReadMap()` is package-private and returns a `Map` snapshot that the bus replaces rather than edits.
    - `listeners` is a private `ConcurrentHashMap<Object, List<EventListener>>`. It maps each registration target (a `Class`, a static `Method`, an object, or an `addListener` `Consumer`) to the listeners it made; the lists are `Collections.synchronizedList`.
  - **`ListenerList`.**
    - `listeners` is a private `AtomicReference<EventListener[]>`.
    - `getListeners()` is public and does `if (rebuild) buildCache(); return listeners.get();`.
    - `buildCache()` may swap an `IWrapperListener` for its `getWithoutCheck()`, which returns a stable `handler` field.
  - **Posting.** `EventBus.post(T)` → `post(event, getListenerList(event.getClass()).getListeners())`, a plain `listeners[i].invoke(event)` loop.
  - **`EventListener` is sealed.** Its permitted subclasses are `ConsumerEventHandler`, `GeneratedEventListener` and
    `SubscribeEventListener`, so a class of ours cannot extend it directly. `net.neoforged.bus.GeneratedEventListener`
    is public, abstract and non-sealed, with a public no-argument constructor. It is the base of the listeners the bus
    generates, and nothing in the bus tests for it: only `EventListenerFactory` names it, and `ListenerList` tests only
    for `IWrapperListener`. So `TimedListener` extends it.
  - **The launcher.** The bus loads in the boot module layer (`at MC-BOOTSTRAP/net.neoforged.bus/...` in the pack's crash reports), so no mixin can reach it.
  - **Dependencies.** The bus artifact's POM brings typetools 0.6.3, asm 9.2 and log4j-api 2.17.1.
  - **Defaults.** `BusBuilder.builder().build()` gives a running bus: `startShutdown` is false by default.
- **`ModList`.** `ModList.get().getModFiles()` returns `List<IModFileInfo>`, and `IModFileInfo` has `moduleName()` and `getMods()` (`net.neoforged.neoforgespi.language`).
- **The test classpath** has neither Minecraft nor NeoForge: `build.gradle` adds only JUnit, Gson and slf4j to it. Anything unit-tested here must use `core`, the bus API and the JDK only.
- **The suite is 132 tests on `main`.**

**Deviations from the spec addendum, recorded here and applied to it in Task 10:**

1. **JSON averages go in `stats`.** §P5.A9 says `session` gains `avgTasksMs`, `avgCapMs` and `avgNamedMs`, but every existing average (`avgTickMs`, `avgSwapMs`, …) lives in `stats`. The new ones go there too, and `session` is unchanged.
2. **The Mods tab's sort arrow sits on `share`.** §P5.A7 says the tab opens sorted by `us/f`. `share` and `us/f` both sort by total time, and `Tab.Columns.of` opens on the first column with the chosen sort, so the tab opens in the same order with its arrow on `share`, like the other ranked tabs.
3. **Tasks and the frame cap appear in the split line only when they have time,** like render buckets, and the named part of a window prints as `[mods x]`. This keeps the phase 1 to 4 split lines byte-identical for sessions that have neither.
4. **Addition:** a dev-only system property, `-Drenderable.autostart=<seconds>` (Task 9). It lets a subagent run a session in a headless dev client and read the JSON, which Task 11 needs. It is not a config option, and the README's Building section documents it.
5. **The rider hook has its own `Probe` entry and hook constant.** §P5.A8 says the rider's wrapper calls `Probe.beginEntityTick`. It calls `Probe.beginRiderTick` instead, which times the same thing but records `Hook.RIDER_TICK`, so the environment's hook list shows whether the rider hook fired apart from ordinary entity ticks.

---

## File map

```
gradle.properties                                             Modify: bus_version (Task 7)
build.gradle                                                  Modify: bus on the test classpath (Task 7)
CLAUDE.md                                                     Modify: event timing rule (Task 10)
README.md                                                     Modify (Task 10)
docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md   Modify (Task 10)
docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md        Modify: deviations above (Task 10)
src/main/resources/renderable.mixins.json                     Modify: six mixins (Tasks 5, 6)
src/main/java/io/github/edwardg117/renderable/
  core/ProfileKey.java                                        Modify: MOD category (Task 1)
  core/ModKey.java                                            Create (Task 1)
  core/Bucket.java                                            Modify: HUD (Task 2)
  core/Window.java                                            Create (Task 2)
  core/FrameRecord.java                                       Rewrite: tasks, cap, named (Task 2)
  core/FrameStats.java                                        Rewrite: their averages (Task 2)
  core/Session.java                                           Modify (Tasks 2, 3, 5, 7)
  core/Splits.java                                            Rewrite (Task 4)
  core/Hook.java                                              Modify: eight hooks (Task 5)
  core/RankedEntry.java                                       Modify: allCallsPerFrame (Task 8)
  core/Ranking.java                                           Modify: Sort.ALL_CALLS (Task 8)
  core/RankedTable.java                                       Modify: Mods table (Task 8)
  core/EmptyStates.java                                       Modify: mods (Task 8)
  hooks/FrameBoundaries.java                                  Create (Task 5)
  hooks/FrameEvents.java                                      Modify: boundaries move out (Task 5)
  hooks/Probe.java                                            Modify (Tasks 5, 6)
  hooks/mixin/MinecraftMixin.java                             Create (Task 5)
  hooks/mixin/GuiMixin.java                                   Create (Task 5)
  hooks/mixin/GuiLayerManagerMixin.java                       Create (Task 6)
  hooks/mixin/PacketUtilsMixin.java                           Create (Task 6)
  hooks/mixin/MainThreadPayloadHandlerMixin.java              Create (Task 6)
  hooks/mixin/ClientLevelMixin.java                           Create (Task 6)
  hooks/events/BusInternals.java                              Create (Task 7)
  hooks/events/ListenerOwners.java                            Create (Task 7)
  hooks/events/ListenerSwap.java                              Create (Task 7)
  hooks/events/TimedListener.java                             Create (Task 7)
  hooks/events/EventTimingRun.java                            Create (Task 7)
  hooks/events/ModOwners.java                                 Create (Task 7)
  hooks/events/EventTiming.java                               Create (Task 7)
  ProfilerController.java                                     Modify (Tasks 7, 9)
  ui/Keybinds.java                                            Modify: autostart tick (Task 9)
  ui/RenderableCommand.java                                   Modify: debug events (Task 7)
  ui/screen/Tab.java                                          Modify: MODS (Task 8)
  ui/screen/ResultsScreen.java                                Modify: five tabs per row (Task 8)
  ui/screen/Rows.java                                         Modify (Tasks 4, 7, 8)
  report/EnvironmentCollector.java                            Modify: eventTiming (Task 7)
  report/TextReportWriter.java                                Modify (Tasks 7, 8)
  report/JsonReportWriter.java                                Modify: schema 5 (Tasks 4, 8)
src/test/java/io/github/edwardg117/renderable/
  core/KeysTest.java                                          Modify (Tasks 1, 2)
  core/SessionTest.java                                       Modify (Tasks 2, 3)
  core/FrameStatsTest.java                                    Modify (Task 2)
  core/TestSessions.java                                      Modify: phase5 fixture (Task 3)
  core/SplitsTest.java                                        Modify (Task 4)
  core/RankedTableTest.java                                   Modify (Task 8)
  core/RankingTest.java                                       Modify (Task 8)
  core/EmptyStatesTest.java                                   Modify (Task 8)
  hooks/events/ListenerSwapTest.java                          Create (Task 7)
  report/JsonReportWriterTest.java                            Modify (Tasks 4, 8)
  report/TextReportWriterTest.java                            Modify (Tasks 4, 7, 8)
  ui/screen/TabTest.java                                      Modify (Task 8)
```

**Expected test totals:** 132 at the start, 133 after Task 1, 136 after Task 2, 143 after Task 3, 146 after
Task 4 (and after Tasks 5 and 6, which add no unit tests), 155 after Task 7, and 160 after Task 8, which is
also the total at the end. As in phase 4, review rounds may add tests; a shortfall is worth investigating and
an excess is not.

**How to run the suite** (the build cache can restore stale results, so always force it):

```bash
./gradlew test --rerun --no-daemon
```

---

### Task 1: `ModKey` and the `MOD` category

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/ProfileKey.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/ModKey.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/KeysTest.java` (append)

Spec addendum §P5.A3 to §P5.A5 and §P5.A7. A mod's event handlers, HUD layers and packets all roll up by
mod id into the Mods tab, so they share one category and use the mod id as the type id. The existing
per-type machinery (`TypeStats`, `Ranking.byType`, `RankedEntry.group`) then gives each mod a row with its
worst frame, and that peak spans all three kinds of key.

- [ ] **Step 1: Write the failing test**

Append to `KeysTest`:

```java
    @Test
    void modKeysFormatAndRollUpByMod() {
        ModKey event = ModKey.event("create", "net.neoforged.neoforge.client.event.ClientTickEvent$Post");
        assertEquals(ProfileKey.Category.MOD, event.category());
        assertEquals("create", event.typeId());
        assertEquals("event ClientTickEvent.Post (create)", event.label(), "standalone, an event row names its mod");
        assertEquals("event ClientTickEvent.Post", event.memberLabel(), "under its mod's row it need not");
        assertEquals("event@create:net.neoforged.neoforge.client.event.ClientTickEvent$Post", event.serialize());

        ModKey layer = ModKey.layer("xaerominimap", "xaerominimap:minimap");
        assertEquals("xaerominimap", layer.typeId());
        assertEquals("layer xaerominimap:minimap", layer.label());
        assertEquals(layer.label(), layer.memberLabel(), "a layer id already names its mod");
        assertEquals("layer@xaerominimap:xaerominimap:minimap", layer.serialize());

        ModKey packet = ModKey.packet("minecraft", "minecraft:level_chunk_with_light");
        assertEquals("packet minecraft:level_chunk_with_light", packet.label());
        assertEquals("packet@minecraft:minecraft:level_chunk_with_light", packet.serialize());

        assertEquals(ModKey.event("a", "x.Y"), ModKey.event("a", "x.Y"));
        assertNotEquals(ModKey.event("a", "x.Y"), ModKey.event("b", "x.Y"), "one event handled by two mods is two keys");
        assertNotEquals(ModKey.layer("a", "a:y"), ModKey.packet("a", "a:y"), "the kind is part of identity");
        assertEquals("Event", ModKey.simpleEventName("Event"), "a class in the default package keeps its name");
    }
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew test --rerun --no-daemon --tests '*KeysTest'`
Expected: compilation failure, `cannot find symbol: class ModKey`.

- [ ] **Step 3: Add the category**

In `ProfileKey.java`, replace the enum line:

```java
    enum Category { BLOCK_ENTITY, ENTITY, PARTICLE, BLOCK, SECTION }
```

with:

```java
    /**
     * {@code MOD} holds a mod's event handlers, HUD layers and packets, whose type id is the mod's id so they
     * roll up into one row per mod (spec addendum §P5.A7).
     */
    enum Category { BLOCK_ENTITY, ENTITY, PARTICLE, BLOCK, SECTION, MOD }
```

- [ ] **Step 4: Create `ModKey`**

```java
package io.github.edwardg117.renderable.core;

/**
 * Something a mod does in the frame that Renderable can name by its owner (spec addendum §P5.A3 to §P5.A5):
 * the mod's handlers for one event class, one HUD layer, or one packet or payload type. All three roll up by
 * mod id into the Mods tab, so they share {@link Category#MOD} and use the mod id as the type id.
 *
 * @param kind  what was timed
 * @param modId the owning mod: the mod whose file registered the handler, or a layer's or packet's namespace
 * @param name  the event's fully-qualified class name, or the layer's or packet's full id
 */
public record ModKey(Kind kind, String modId, String name) implements ProfileKey {

    /** What a {@link ModKey} timed. */
    public enum Kind {
        EVENT("event"), LAYER("layer"), PACKET("packet");

        private final String word;

        Kind(String word) {
            this.word = word;
        }

        /** The word that starts the key's label and its serialised form. */
        public String word() {
            return word;
        }
    }

    public static ModKey event(String modId, String eventClassName) {
        return new ModKey(Kind.EVENT, modId, eventClassName);
    }

    public static ModKey layer(String modId, String layerId) {
        return new ModKey(Kind.LAYER, modId, layerId);
    }

    public static ModKey packet(String modId, String packetId) {
        return new ModKey(Kind.PACKET, modId, packetId);
    }

    @Override
    public Category category() {
        return Category.MOD;
    }

    @Override
    public String typeId() {
        return modId;
    }

    /**
     * The key on its own, as in a hitch's contributor list. An event row names its mod, because the event's
     * name does not; a layer's or packet's id already carries its namespace.
     */
    @Override
    public String label() {
        return kind == Kind.EVENT ? memberLabel() + " (" + modId + ")" : memberLabel();
    }

    /** The key under its mod's own row in the Mods tab and report, where the mod is already named. */
    public String memberLabel() {
        return kind.word + " " + (kind == Kind.EVENT ? simpleEventName(name) : name);
    }

    @Override
    public String serialize() {
        return kind.word + "@" + modId + ":" + name;
    }

    /** {@code net.neoforged.neoforge.client.event.ClientTickEvent$Post} reads {@code ClientTickEvent.Post}. */
    public static String simpleEventName(String className) {
        return className.substring(className.lastIndexOf('.') + 1).replace('$', '.');
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **133** tests, 0 failures.

- [ ] **Step 6: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/ProfileKey.java src/main/java/io/github/edwardg117/renderable/core/ModKey.java src/test/java/io/github/edwardg117/renderable/core/KeysTest.java
```

Report to the orchestrator for the commit: `feat(core): ModKey and the MOD category`.

---

### Task 2: Frame components: tasks, the frame cap, the HUD bucket and windows

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Bucket.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/Window.java`
- Rewrite: `src/main/java/io/github/edwardg117/renderable/core/FrameRecord.java`
- Rewrite: `src/main/java/io/github/edwardg117/renderable/core/FrameStats.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Session.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/SessionTest.java` (append 2)
- Test: `src/test/java/io/github/edwardg117/renderable/core/FrameStatsTest.java` (append 1)
- Test: `src/test/java/io/github/edwardg117/renderable/core/KeysTest.java` (extend one test)

Spec addendum §P5.A1. `FrameRecord` keeps a nine-argument constructor with the phase 1 to 4 shape, so the
fixtures in `SessionTest`, `SplitsTest`, `FrameStatsTest` and `HitchDetectorTest` compile unchanged. The named
array is added here and filled in Task 3.

- [ ] **Step 1: Write the failing tests**

Append to `SessionTest`:

```java
    @Test
    void tasksAndTheFrameCapAreFrameComponentsOutsideSwap() {
        Session s = session(10_000);
        at(0);  s.onRenderPre(0, 0);
        at(4);  s.onRenderPost();
        at(5);  s.onCapBegin();
        at(9);  s.onCapEnd();
        at(10); s.onTasksBegin();
        at(13); s.onTasksEnd();
        at(14); s.onTickPre();
        at(16); s.onTickPost();
        at(20); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(4 * MS, f.renderNanos());
        assertEquals(4 * MS, f.capNanos());
        assertEquals(3 * MS, f.tasksNanos());
        assertEquals(2 * MS, f.tickNanos());
        assertEquals(7 * MS, f.swapNanos(), "20 total - 4 render - 4 cap - 3 tasks - 2 tick");
    }

    @Test
    void tasksAndTheCapOutsideAFrameAreIgnored() {
        Session s = session(10_000);
        at(0); s.onTasksBegin(); at(2); s.onTasksEnd();     // before the first frame
        at(2); s.onCapBegin();   at(3); s.onCapEnd();
        at(3); s.onRenderPre(0, 0);
        at(4); s.onTasksEnd();                              // an end with nothing open
        at(5); s.onCapEnd();
        at(10); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(0, f.tasksNanos());
        assertEquals(0, f.capNanos());
        assertEquals(7 * MS, f.swapNanos());
    }
```

Append to `FrameStatsTest`:

```java
    @Test
    void averagesCoverTasksTheCapAndNamedTime() {
        long[] named = new long[Window.COUNT];
        named[Window.RENDER.ordinal()] = 2 * MS;
        named[Window.TASKS.ordinal()] = 1 * MS;
        FrameRecord a = new FrameRecord(0, 0, 30 * MS, 2 * MS, 10 * MS, 4 * MS, 6 * MS,
                new long[Bucket.COUNT], named, 0, 0, List.of());
        FrameRecord b = new FrameRecord(1, 30 * MS, 30 * MS, 2 * MS, 10 * MS, 2 * MS, 0,
                new long[Bucket.COUNT], new long[Window.COUNT], 0, 0, List.of());
        FrameStats st = FrameStats.of(List.of(a, b));
        assertEquals(3 * MS, st.avgTasksNanos());
        assertEquals(3 * MS, st.avgCapNanos());
        assertEquals(1 * MS, st.avgNamed(Window.RENDER));
        assertEquals(500_000, st.avgNamed(Window.TASKS));
        assertEquals(0, st.avgNamed(Window.TICK));
        assertEquals(12 * MS, st.avgSwapNanos(), "a: 30-2-10-4-6 = 8, b: 30-2-10-2-0 = 16");
        assertEquals(Window.COUNT, FrameStats.of(List.of()).avgNamedNanos().length);
    }
```

In `KeysTest.renderBucketsAreDistinguishedFromTickBuckets`, add before its last line:

```java
        assertTrue(Bucket.HUD.isRender(), "the HUD is drawn inside the render window");
        assertEquals("hud", Bucket.HUD.label());
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*SessionTest' --tests '*FrameStatsTest' --tests '*KeysTest'`
Expected: compilation failure (`onCapBegin`, `Window`, `Bucket.HUD` and the 12-argument `FrameRecord` do
not exist).

- [ ] **Step 3: Add the HUD bucket**

In `Bucket.java`, replace:

```java
    LIGHT(true),
    PARTICLE_TICK(false),
```

with:

```java
    LIGHT(true),
    /** {@code Gui.render}: every HUD layer and the HUD's own events (spec addendum §P5.A1). */
    HUD(true),
    PARTICLE_TICK(false),
```

Every use of a bucket goes through `ordinal()`, `VALUES` or `label()`, and nothing stores an ordinal, so
inserting it here keeps the render buckets together in reports.

- [ ] **Step 4: Create `Window`**

```java
package io.github.edwardg117.renderable.core;

import java.util.Locale;

/**
 * The parts of a frame an attribution-only call can run in, for named time (spec addendum §P5.A6): the
 * render window (frame start to render end), the client tick, and {@code Minecraft.runAllTasks}. Anything
 * else, such as a handler run during the frame-cap wait, is in no window.
 */
public enum Window {
    RENDER, TICK, TASKS;

    public static final Window[] VALUES = values();
    public static final int COUNT = VALUES.length;

    /** Lower case, for reports and JSON keys. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
```

- [ ] **Step 5: Rewrite `FrameRecord`**

Replace the whole file with:

```java
package io.github.edwardg117.renderable.core;

import java.util.List;

/**
 * One sampled frame (spec §3.2, amended by spec addendum §P5.A1).
 *
 * @param index       0-based frame number within the session
 * @param startNanos  offset of this frame's start from the session start
 * @param totalNanos  start of this frame to start of the next
 * @param tickNanos   client tick time that ran inside this frame
 * @param renderNanos frame start, just before RenderFrameEvent.Pre is posted, to just after RenderFrameEvent.Post
 * @param tasksNanos  time in {@code Minecraft.runAllTasks}: packets and other queued main-thread work
 * @param capNanos    time in {@code RenderSystem.limitDisplayFPS}: the wait for the frame-rate limit
 * @param buckets     nanoseconds per {@link Bucket}, indexed by ordinal
 * @param namedNanos  nanoseconds per {@link Window} that the Mods tab names (spec addendum §P5.A6)
 * @param gcCount     garbage collections observed during this frame
 * @param gcMillis    GC pause milliseconds observed during this frame
 * @param top         top contributors, most expensive first
 */
public record FrameRecord(int index,
                          long startNanos,
                          long totalNanos,
                          long tickNanos,
                          long renderNanos,
                          long tasksNanos,
                          long capNanos,
                          long[] buckets,
                          long[] namedNanos,
                          int gcCount,
                          long gcMillis,
                          List<TopEntry> top) {

    /** A frame with no tasks, no frame-cap wait and nothing named: the phase 1 to 4 shape that tests build. */
    public FrameRecord(int index, long startNanos, long totalNanos, long tickNanos, long renderNanos,
                       long[] buckets, int gcCount, long gcMillis, List<TopEntry> top) {
        this(index, startNanos, totalNanos, tickNanos, renderNanos, 0, 0, buckets, new long[Window.COUNT],
                gcCount, gcMillis, top);
    }

    public long bucket(Bucket b) {
        return buckets[b.ordinal()];
    }

    /** The part of window {@code w} that an attribution-only call accounts for and no bucket does. */
    public long named(Window w) {
        return namedNanos[w.ordinal()];
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

    /**
     * Everything outside tick, render, tasks and the frame cap: the buffer swap and any vsync wait, the blit,
     * the sound update, mouse turning and the yield. Clamped at zero.
     */
    public long swapNanos() {
        return Math.max(0, totalNanos - tickNanos - renderNanos - tasksNanos - capNanos);
    }
}
```

- [ ] **Step 6: Rewrite `FrameStats`**

Replace the whole file with:

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
                         long avgTasksNanos,
                         long avgCapNanos,
                         long avgSwapNanos,
                         long[] avgBucketNanos,
                         long[] avgNamedNanos,
                         long avgOtherRenderNanos,
                         long avgOtherTickNanos,
                         int gcCount,
                         long gcMillis) {

    public static FrameStats of(List<FrameRecord> frames) {
        int n = frames.size();
        long[] avgBuckets = new long[Bucket.COUNT];
        long[] avgNamed = new long[Window.COUNT];
        if (n == 0) {
            return new FrameStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, avgBuckets, avgNamed, 0, 0, 0, 0);
        }

        long[] sorted = new long[n];
        long total = 0, tick = 0, render = 0, tasks = 0, cap = 0, swap = 0, otherRender = 0, otherTick = 0;
        long gcMillis = 0;
        int gcCount = 0;
        long[] bucketSums = new long[Bucket.COUNT];
        long[] namedSums = new long[Window.COUNT];

        for (int i = 0; i < n; i++) {
            FrameRecord f = frames.get(i);
            sorted[i] = f.totalNanos();
            total += f.totalNanos();
            tick += f.tickNanos();
            render += f.renderNanos();
            tasks += f.tasksNanos();
            cap += f.capNanos();
            swap += f.swapNanos();
            otherRender += f.otherRenderNanos();
            otherTick += f.otherTickNanos();
            gcCount += f.gcCount();
            gcMillis += f.gcMillis();
            long[] b = f.buckets();
            for (int k = 0; k < Bucket.COUNT; k++) bucketSums[k] += b[k];
            long[] named = f.namedNanos();
            for (int k = 0; k < Window.COUNT; k++) namedSums[k] += named[k];
        }
        Arrays.sort(sorted);
        for (int k = 0; k < Bucket.COUNT; k++) avgBuckets[k] = bucketSums[k] / n;
        for (int k = 0; k < Window.COUNT; k++) avgNamed[k] = namedSums[k] / n;

        return new FrameStats(n, total, total / n,
                percentile(sorted, 50), percentile(sorted, 95), percentile(sorted, 99), sorted[n - 1],
                tick / n, render / n, tasks / n, cap / n, swap / n, avgBuckets, avgNamed,
                otherRender / n, otherTick / n, gcCount, gcMillis);
    }

    /** Nearest-rank percentile of an ascending array. */
    static long percentile(long[] sorted, double p) {
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    public long avgBucket(Bucket b) {
        return avgBucketNanos[b.ordinal()];
    }

    /** The average named time in window {@code w} (spec addendum §P5.A6). */
    public long avgNamed(Window w) {
        return avgNamedNanos[w.ordinal()];
    }

    public double avgFps() {
        return avgNanos == 0 ? 0 : 1e9 / avgNanos;
    }

    public double durationSeconds() {
        return totalNanos / 1e9;
    }
}
```

- [ ] **Step 7: Give `Session` the new components**

In `Session.java`, find:

```java
    private long renderNanos;
    private long tickNanos;
```

and replace it with:

```java
    private long renderNanos;
    private long tickNanos;
    private long tasksStartNanos = -1;
    private long capStartNanos = -1;
    /** True from a frame's start to its render end: the render window (spec addendum §P5.A6). */
    private boolean renderOpen;
    private long tasksNanos;
    private long capNanos;
    /**
     * Nanoseconds per {@link Window} that an outermost attribution-only call accounts for in the current frame
     * (spec addendum §P5.A6).
     */
    private final long[] named = new long[Window.COUNT];
```

Find the end of `onRenderPre`:

```java
        frameStartNanos = now;
        renderStartNanos = now;
        renderNanos = 0;
        tickNanos = 0;
        Arrays.fill(buckets, 0L);
        return done;
    }
```

and replace it with:

```java
        frameStartNanos = now;
        renderStartNanos = now;
        renderNanos = 0;
        tickNanos = 0;
        Arrays.fill(buckets, 0L);
        tasksNanos = 0;
        capNanos = 0;
        Arrays.fill(named, 0L);
        renderOpen = true;
        return done;
    }
```

Replace `onRenderPost` with:

```java
    /** Called at RenderFrameEvent.Post. */
    public void onRenderPost() {
        if (frameStartNanos < 0) return;
        renderNanos += clock.getAsLong() - renderStartNanos;
        renderOpen = false;
    }
```

Directly after `onTickPost`, add:

```java
    /** Called just before {@code Minecraft.runAllTasks}: packets and other queued work (spec addendum §P5.A1). */
    public void onTasksBegin() {
        tasksStartNanos = clock.getAsLong();
    }

    /** Called just after {@code Minecraft.runAllTasks}. Tasks before the first frame are ignored. */
    public void onTasksEnd() {
        if (tasksStartNanos < 0) return;
        long elapsed = clock.getAsLong() - tasksStartNanos;
        tasksStartNanos = -1;
        if (frameStartNanos >= 0) tasksNanos += elapsed;
    }

    /** Called just before {@code RenderSystem.limitDisplayFPS}: the frame-rate cap's wait (spec addendum §P5.A1). */
    public void onCapBegin() {
        capStartNanos = clock.getAsLong();
    }

    /** Called just after {@code RenderSystem.limitDisplayFPS}. A wait before the first frame is ignored. */
    public void onCapEnd() {
        if (capStartNanos < 0) return;
        long elapsed = clock.getAsLong() - capStartNanos;
        capStartNanos = -1;
        if (frameStartNanos >= 0) capNanos += elapsed;
    }
```

At the end of `closeFrame`, replace:

```java
        frames.add(new FrameRecord(frames.size(), frameStartNanos - sessionStartNanos, now - frameStartNanos,
                tickNanos, renderNanos, buckets.clone(), gcCount, gcMillis, List.copyOf(top)));
```

with:

```java
        frames.add(new FrameRecord(frames.size(), frameStartNanos - sessionStartNanos, now - frameStartNanos,
                tickNanos, renderNanos, tasksNanos, capNanos, buckets.clone(), named.clone(), gcCount, gcMillis,
                List.copyOf(top)));
```

- [ ] **Step 8: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **136** tests, 0 failures. Every existing expectation holds: the fixtures have no
tasks, cap or named time, so their swap figures are unchanged.

- [ ] **Step 9: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/Bucket.java src/main/java/io/github/edwardg117/renderable/core/Window.java src/main/java/io/github/edwardg117/renderable/core/FrameRecord.java src/main/java/io/github/edwardg117/renderable/core/FrameStats.java src/main/java/io/github/edwardg117/renderable/core/Session.java src/test/java/io/github/edwardg117/renderable/core/SessionTest.java src/test/java/io/github/edwardg117/renderable/core/FrameStatsTest.java src/test/java/io/github/edwardg117/renderable/core/KeysTest.java
```

Report for the commit: `feat(core): tasks, frame cap and HUD in the frame record`.

---

### Task 3: Attribution-only calls

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Session.java`
- Modify: `src/test/java/io/github/edwardg117/renderable/core/TestSessions.java` (phase 5 fixture and keys)
- Test: `src/test/java/io/github/edwardg117/renderable/core/SessionTest.java` (append 7)

Spec addendum §P5.A6 and §P5.A8. The existing rule is that a bucket is credited when the stack is empty. It
becomes: a bucket is credited when no *bucket-owning* call encloses the call. `begin(a, bucket)` stays the
bucket-owning call. The new `beginAttribution(a)` pushes a call with a null bucket. Vertices go to the
innermost call that owns a bucket, and an attribution-only call that nothing encloses is named time for its
window, less any bucket time credited inside it.

The rider case needs no code here: a rider's tick is an ordinary `ENTITY_TICK` call nested inside its
vehicle's, and the unchanged nesting rules already credit the bucket once, by the vehicle. The fixture proves
it.

- [ ] **Step 1: Add the phase 5 fixture**

In `TestSessions.java`, add these constants after `SECTION_B`:

```java
    public static final ModKey STAGE_HANDLER =
            ModKey.event("create", "net.neoforged.neoforge.client.event.RenderLevelStageEvent");
    public static final ModKey LIVING_HANDLER =
            ModKey.event("somemod", "net.neoforged.neoforge.client.event.RenderLivingEvent$Pre");
    public static final ModKey TICK_HANDLER =
            ModKey.event("somemod", "net.neoforged.neoforge.client.event.ClientTickEvent$Post");
    public static final ModKey MINIMAP = ModKey.layer("xaerominimap", "xaerominimap:minimap");
    public static final ModKey CHUNK_PACKET = ModKey.packet("minecraft", "minecraft:level_chunk_with_light");
    public static final EntityKey BOAT = new EntityKey("minecraft:boat", 9, "");
```

and this method after `everyFrameGcSuspect()`:

```java
    /**
     * Phase 5 fixture (spec addendum §P5.A1, §P5.A6 and §P5.A8): two identical 20 ms frames that exercise every
     * new frame component and every kind of attribution-only call. Per frame, in milliseconds from its start:
     *
     * render 0-5: cow render 0.5-1.0 (ENTITY) holding the living-render handler 0.6-0.8, which emits 7
     * vertices; the stage handler 1-3 holding a press render 1.5-2.5 (BLOCK_ENTITY) that emits 10 vertices;
     * the HUD 3-5 (bucket only) holding the minimap layer 3.5-4.5. Frame cap 5-8. Tasks 8-10 holding the chunk
     * packet 8.5-9.5. Tick 10-12: the tick handler 10.2-10.6, then the boat's tick 10.6-11.6 (ENTITY_TICK)
     * holding the player's rider tick 11.0-11.4.
     *
     * So per frame: render 5, frame cap 3, tasks 2, tick 2, swap 8. Buckets: entity 0.5, block entity 1,
     * hud 2, entity tick 1 (the boat only; the rider is nested). Other render 1.5, of which 1 is named (the
     * stage handler less the press inside it; the living handler is inside the cow). Other tick 1, of which
     * 0.4 is named. Tasks 2, of which 1 is named. Vertices land on the cow (7) and the press (10). Type peaks:
     * create 2, somemod 0.6, xaerominimap 1, minecraft 1. 10 timed calls and 2 vertex calls a frame.
     */
    public static SessionResult phase5() {
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 25, 2), () -> now[0], WALL_START);
        Accumulator cow = s.accumulator(COW);
        Accumulator press = s.accumulator(PRESS);
        Accumulator player = s.accumulator(PLAYER);
        Accumulator boat = s.accumulator(BOAT);
        Accumulator stage = s.accumulator(STAGE_HANDLER);
        Accumulator living = s.accumulator(LIVING_HANDLER);
        Accumulator tick = s.accumulator(TICK_HANDLER);
        Accumulator minimap = s.accumulator(MINIMAP);
        Accumulator chunk = s.accumulator(CHUNK_PACKET);

        for (int i = 0; i <= 2; i++) {
            long t0 = i * 20 * MS;
            now[0] = t0; s.onRenderPre(0, 0);
            if (i == 2) break;

            now[0] = t0 + 500_000;    s.begin(cow, Bucket.ENTITY);
            now[0] = t0 + 600_000;    s.beginAttribution(living);
            s.vertices(7);
            now[0] = t0 + 800_000;    s.end();
            now[0] = t0 + 1 * MS;     s.end();
            now[0] = t0 + 1 * MS;     s.beginAttribution(stage);
            now[0] = t0 + 1_500_000;  s.begin(press, Bucket.BLOCK_ENTITY);
            s.vertices(10);
            now[0] = t0 + 2_500_000;  s.end();
            now[0] = t0 + 3 * MS;     s.end();
            now[0] = t0 + 3 * MS;     s.begin(null, Bucket.HUD);
            now[0] = t0 + 3_500_000;  s.beginAttribution(minimap);
            now[0] = t0 + 4_500_000;  s.end();
            now[0] = t0 + 5 * MS;     s.end();
            now[0] = t0 + 5 * MS;     s.onRenderPost();

            now[0] = t0 + 5 * MS;     s.onCapBegin();
            now[0] = t0 + 8 * MS;     s.onCapEnd();

            now[0] = t0 + 8 * MS;     s.onTasksBegin();
            now[0] = t0 + 8_500_000;  s.beginAttribution(chunk);
            now[0] = t0 + 9_500_000;  s.end();
            now[0] = t0 + 10 * MS;    s.onTasksEnd();

            now[0] = t0 + 10 * MS;    s.onTickPre();
            now[0] = t0 + 10_200_000; s.beginAttribution(tick);
            now[0] = t0 + 10_600_000; s.end();
            now[0] = t0 + 10_600_000; s.begin(boat, Bucket.ENTITY_TICK);
            now[0] = t0 + 11 * MS;    s.begin(player, Bucket.ENTITY_TICK);    // riding the boat
            now[0] = t0 + 11_400_000; s.end();
            now[0] = t0 + 11_600_000; s.end();
            now[0] = t0 + 12 * MS;    s.onTickPost();
        }
        return s.finish();
    }
```

- [ ] **Step 2: Write the failing tests**

Append to `SessionTest` (it needs `import java.util.Map;` beside its other imports):

```java
    @Test
    void attributionOnlyCallsLeaveBucketsToTheCallsTheyEnclose() {
        Session s = session(10_000);
        Accumulator handler = s.accumulator(ModKey.event("somemod", "a.RenderLevelStageEvent"));
        Accumulator be = s.accumulator(press);
        at(0); s.onRenderPre(0, 0);
        at(1); s.beginAttribution(handler);
        at(2); s.begin(be, Bucket.BLOCK_ENTITY);    // a block entity the handler draws
        at(5); s.end();
        at(6); s.end();
        at(8); s.onRenderPost();
        at(10); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(3 * MS, f.bucket(Bucket.BLOCK_ENTITY), "still block entity time");
        assertEquals(5 * MS, handler.renderNanos, "the handler's own row is inclusive");
        assertEquals(1, handler.calls);
        assertEquals(3 * MS, be.renderNanos);
        assertEquals(2 * MS, f.named(Window.RENDER), "the handler's 5 ms less the 3 ms bucketed inside it");
        assertEquals(5 * MS, f.otherRenderNanos(), "8 ms of render less the 3 ms bucketed");
    }

    @Test
    void anAttributionOnlyCallInsideABucketOwnerCreditsNoBucketAndIsNotNamed() {
        Session s = session(10_000);
        Accumulator layer = s.accumulator(ModKey.layer("xaerominimap", "xaerominimap:minimap"));
        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(null, Bucket.HUD);
        at(2); s.beginAttribution(layer);
        at(4); s.end();
        at(5); s.end();
        at(6); s.onRenderPost();
        at(10); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(4 * MS, f.bucket(Bucket.HUD), "the layer's time stays in the HUD bucket");
        assertEquals(2 * MS, layer.renderNanos);
        assertEquals(0, f.named(Window.RENDER), "a bucket owner encloses it, so it is not named");
        assertEquals(layer.key, f.top().get(0).key(), "an attribution-only key is a top contributor like any other");
    }

    @Test
    void verticesSkipAttributionOnlyCalls() {
        Session s = session(10_000);
        Accumulator cowAcc = s.accumulator(cow);
        Accumulator handler = s.accumulator(ModKey.event("somemod", "a.RenderLivingEvent$Pre"));
        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(cowAcc, Bucket.ENTITY);
        at(2); s.beginAttribution(handler);
        s.vertex();
        s.vertices(6);                  // drawn by the handler, inside the cow's render
        at(3); s.end();
        at(4); s.end();
        at(5); s.beginAttribution(handler);
        s.vertex();                     // no call that owns a bucket is open: dropped
        at(6); s.end();
        at(10); s.onRenderPre(0, 0);

        assertEquals(7, cowAcc.vertices);
        assertEquals(0, handler.vertices);
        assertEquals(3, s.vertexCalls(), "counted whether or not the call landed on a key");
        assertEquals(3 * MS, s.framesView().get(0).bucket(Bucket.ENTITY));
    }

    @Test
    void namedTimeFollowsTheWindowItRanIn() {
        Session s = session(10_000);
        Accumulator tickHandler = s.accumulator(ModKey.event("somemod", "a.ClientTickEvent$Post"));
        Accumulator packet = s.accumulator(ModKey.packet("minecraft", "minecraft:level_chunk_with_light"));
        Accumulator inputHandler = s.accumulator(ModKey.event("somemod", "a.InputEvent$Key"));
        at(0);  s.onRenderPre(0, 0);
        at(2);  s.onRenderPost();
        at(3);  s.onCapBegin();
        at(3);  s.beginAttribution(inputHandler);
        at(4);  s.end();
        at(5);  s.onCapEnd();
        at(6);  s.onTasksBegin();
        at(6);  s.beginAttribution(packet);
        at(8);  s.end();
        at(9);  s.onTasksEnd();
        at(10); s.onTickPre();
        at(10); s.beginAttribution(tickHandler);
        at(11); s.end();
        at(12); s.onTickPost();
        at(20); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(2 * MS, f.named(Window.TASKS));
        assertEquals(1 * MS, f.named(Window.TICK));
        assertEquals(0, f.named(Window.RENDER));
        assertEquals(1 * MS, tickHandler.tickNanos, "a call inside the client tick is tick time");
        assertEquals(1, tickHandler.tickCalls);
        assertEquals(2 * MS, packet.renderNanos, "outside the tick, an attribution-only call is on the render side");
        assertEquals(1 * MS, inputHandler.renderNanos, "a handler during the frame-cap wait still counts for its mod");
        long named = 0;
        for (Window w : Window.VALUES) named += f.named(w);
        assertEquals(3 * MS, named, "the frame-cap wait is in no window, so that handler is not named");
    }

    @Test
    void attributionOnlyKeysGetPeaksAndGcFrames() {
        Session s = session(10_000);
        Accumulator h = s.accumulator(ModKey.event("somemod", "a.ClientTickEvent$Post"));
        at(0);  s.onRenderPre(0, 0);
        at(1);  s.onTickPre(); s.beginAttribution(h);
        at(4);  s.end(); s.onTickPost();                      // 3 ms, clean
        at(10); s.onRenderPre(0, 0);
        at(11); s.onTickPre(); s.beginAttribution(h);
        at(51); s.end(); s.onTickPost();                      // 40 ms
        at(60); s.onRenderPre(1, 40);                         // a 40 ms pause in that frame

        RankedEntry row = Ranking.byType(s.finish(), ProfileKey.Category.MOD).get(0);
        assertEquals("somemod", row.typeId());
        assertEquals(3 * MS, row.maxFrameNanos(), "the 40 ms frame is GC-suspect and does not set the peak");
        assertEquals(1, row.gcFrames());
    }

    @Test
    void anUnbalancedAttributionCallIsResetAtTheFrameBoundary() {
        Session s = session(10_000);
        Accumulator h = s.accumulator(ModKey.event("somemod", "a.RenderLevelStageEvent"));
        at(0);  s.onRenderPre(0, 0);
        at(1);  s.beginAttribution(h);                        // a handler threw past its end()
        at(10); s.onRenderPre(0, 0);
        at(11); s.begin(s.accumulator(press), Bucket.BLOCK_ENTITY);
        at(13); s.end();
        at(20); s.onRenderPre(0, 0);

        assertEquals(1, s.unbalancedFrames());
        FrameRecord f = s.framesView().get(1);
        assertEquals(2 * MS, f.bucket(Bucket.BLOCK_ENTITY), "the bucket rule works again after the reset");
        assertEquals(0, f.named(Window.RENDER));
    }

    @Test
    void phase5FixtureMatchesItsDescription() {
        SessionResult r = TestSessions.phase5();
        assertEquals(2, r.frameCount());
        FrameRecord f = r.frames().get(0);
        assertEquals(20 * MS, f.totalNanos());
        assertEquals(5 * MS, f.renderNanos());
        assertEquals(3 * MS, f.capNanos());
        assertEquals(2 * MS, f.tasksNanos());
        assertEquals(2 * MS, f.tickNanos());
        assertEquals(8 * MS, f.swapNanos());
        assertEquals(500_000, f.bucket(Bucket.ENTITY));
        assertEquals(1 * MS, f.bucket(Bucket.BLOCK_ENTITY));
        assertEquals(2 * MS, f.bucket(Bucket.HUD));
        assertEquals(1 * MS, f.bucket(Bucket.ENTITY_TICK), "the boat only: the rider is nested inside it");
        assertEquals(1_500_000, f.otherRenderNanos());
        assertEquals(1 * MS, f.otherTickNanos());
        assertEquals(1 * MS, f.named(Window.RENDER));
        assertEquals(400_000, f.named(Window.TICK));
        assertEquals(1 * MS, f.named(Window.TASKS));

        Map<ProfileKey, Accumulator> acc = r.accumulators();
        assertEquals(14, acc.get(TestSessions.COW).vertices, "the living-render handler's vertices stay on the cow");
        assertEquals(0, acc.get(TestSessions.LIVING_HANDLER).vertices);
        assertEquals(20, acc.get(TestSessions.PRESS).vertices);
        assertEquals(800_000, acc.get(TestSessions.PLAYER).tickNanos, "the rider has its own tick");
        assertEquals(2 * MS, acc.get(TestSessions.BOAT).tickNanos, "and the vehicle still includes it");

        List<RankedEntry> mods = Ranking.byType(r, ProfileKey.Category.MOD);
        assertEquals(List.of("create", "minecraft", "xaerominimap", "somemod"),
                mods.stream().map(RankedEntry::typeId).toList(), "by total, ties A to Z");
        assertEquals(2 * MS, mods.get(0).maxFrameNanos());
        assertEquals(600_000, mods.get(3).maxFrameNanos(), "somemod's two handlers in one frame: 0.2 + 0.4");
        assertEquals(20, r.timedCalls());
        assertEquals(4, r.vertexCalls());
    }
```

- [ ] **Step 3: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*SessionTest'`
Expected: compilation failure, `cannot find symbol: method beginAttribution(Accumulator)`.

- [ ] **Step 4: Add the attribution state to `Session`**

Find:

```java
    private final Bucket[] stackBucket = new Bucket[MAX_DEPTH];
```

and replace it with:

```java
    /** Null marks an attribution-only call (spec addendum §P5.A6). */
    private final Bucket[] stackBucket = new Bucket[MAX_DEPTH];
    /** Where {@link #innermostOrdinary} stood before the call at each index began, for end() to restore. */
    private final int[] stackPrevOrdinary = new int[MAX_DEPTH];
    /** Open tracked calls that own a bucket: every call except attribution-only ones. */
    private int ordinaryDepth;
    /** Stack index of the innermost open call that owns a bucket, or -1: where vertices land. */
    private int innermostOrdinary = -1;
    /** True while an attribution-only call that nothing encloses is open; its time is named time. */
    private boolean namedOpen;
    private int namedWindow;
    private long namedBucketMark;
    /** Bucket nanoseconds credited so far in this frame, so named time can leave out what was bucketed. */
    private long bucketTotal;
```

- [ ] **Step 5: Route vertices past attribution-only calls**

Replace `vertex()` and `vertices(int)`, including their Javadoc, with:

```java
    /**
     * Counts one vertex on the innermost open call that owns a bucket, if that call has a key (spec §4.3).
     * Render thread only. Attribution-only calls are skipped (spec addendum §P5.A6), so a handler drawing
     * inside an entity's render adds to the entity's count, as it did before handlers were timed. A keyless
     * inner timing (bucket-only work) hides the outer key, and vertices emitted deeper than {@link #MAX_DEPTH}
     * are dropped rather than credited to an ancestor, matching {@link #end()}. The call is counted for the
     * overhead estimate whether or not it lands on a key, because it cost the same either way.
     */
    public void vertex() {
        vertexCalls++;
        if (depth > MAX_DEPTH || innermostOrdinary < 0) return;
        Accumulator a = stack[innermostOrdinary];
        if (a != null) a.vertices++;
    }

    /** Counts {@code n} vertices on the same call {@link #vertex()} would; same rules. */
    public void vertices(int n) {
        vertexCalls++;
        if (depth > MAX_DEPTH || innermostOrdinary < 0) return;
        Accumulator a = stack[innermostOrdinary];
        if (a != null) a.vertices += n;
    }
```

`innermostOrdinary` is -1 whenever the stack is empty, so the old `depth == 0` test is implied.

- [ ] **Step 6: Replace `begin` and add `beginAttribution`**

Replace `begin`, including its Javadoc, with:

```java
    /**
     * Start timing a call attributed to {@code a} in {@code bucket}. Must be paired with {@link #end()}.
     * {@code a} may be null for work that has no key (light updates, Flywheel, chunk upload, the HUD): the
     * bucket is still credited when no other bucket-owning call encloses it, but nothing is added to an
     * accumulator or to the frame's top list.
     */
    public void begin(Accumulator a, Bucket bucket) {
        long now = clock.getAsLong();
        if (depth < MAX_DEPTH) {
            stack[depth] = a;
            stackStart[depth] = now;
            stackBucket[depth] = bucket;
            stackPrevOrdinary[depth] = innermostOrdinary;
            innermostOrdinary = depth;
            ordinaryDepth++;
        }
        depth++;
    }

    /**
     * Start timing an attribution-only call (spec addendum §P5.A6): a mod's event handler, a HUD layer or a
     * packet. It is timed and ranked like any keyed call, but it owns no bucket. A bucket is credited by the
     * outermost call that owns one, so a block entity drawn inside a handler still counts as block entity
     * time, and vertices still land on the innermost call that owns a bucket. An attribution-only call that
     * nothing encloses is named time for the window it runs in. Pair with {@link #end()}.
     */
    public void beginAttribution(Accumulator a) {
        long now = clock.getAsLong();
        if (depth < MAX_DEPTH) {
            stack[depth] = a;
            stackStart[depth] = now;
            stackBucket[depth] = null;
            if (depth == 0 && frameStartNanos >= 0) {
                int w = window();
                if (w >= 0) {
                    namedOpen = true;
                    namedWindow = w;
                    namedBucketMark = bucketTotal;
                }
            }
        }
        depth++;
    }

    /** The window open right now: the tick, tasks or the render window, or -1 outside all three. */
    private int window() {
        if (tickStartNanos >= 0) return Window.TICK.ordinal();
        if (tasksStartNanos >= 0) return Window.TASKS.ordinal();
        if (renderOpen) return Window.RENDER.ordinal();
        return -1;
    }
```

- [ ] **Step 7: Replace `end`**

Replace `end()`, including its Javadoc, with:

```java
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
        boolean ownsBucket = bucket != null;
        if (ownsBucket) {
            ordinaryDepth--;
            innermostOrdinary = stackPrevOrdinary[depth];
        }

        if (a != null) {
            // An attribution-only call has no bucket to say which side it is on, so the window decides: a call
            // inside the client tick is tick time, and anything else is on the render side.
            boolean render = ownsBucket ? bucket.isRender() : tickStartNanos < 0;
            if (render) {
                a.renderNanos += elapsed;
                a.calls++;
                if (shadowPass && ownsBucket) a.shadowNanos += elapsed;
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
            if (ownsBucket && ordinaryDepth == 0) {
                buckets[bucket.ordinal()] += elapsed;
                bucketTotal += elapsed;
            }
            if (namedOpen && depth == 0) {
                named[namedWindow] += Math.max(0, elapsed - (bucketTotal - namedBucketMark));
                namedOpen = false;
            }
        }
    }
```

The shadow flag is only meaningful for the block entity, entity and particle hooks that set it, which all own
buckets, so an attribution-only call never adds shadow time.

- [ ] **Step 8: Reset the new state at the frame boundary**

In `onRenderPre`, replace:

```java
        if (depth != 0) {
            unbalancedFrames++;
            depth = 0;
            Arrays.fill(stack, null);
        }
```

with:

```java
        if (depth != 0) {
            unbalancedFrames++;
            depth = 0;
            Arrays.fill(stack, null);
            ordinaryDepth = 0;
            innermostOrdinary = -1;
            namedOpen = false;
        }
```

and, in the per-frame resets at the end of the same method, replace:

```java
        Arrays.fill(buckets, 0L);
        tasksNanos = 0;
```

with:

```java
        Arrays.fill(buckets, 0L);
        bucketTotal = 0;
        tasksNanos = 0;
```

- [ ] **Step 9: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **143** tests, 0 failures. The existing bucket and vertex tests pass unchanged,
because with no attribution-only call open the new rules are the old ones.

- [ ] **Step 10: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/Session.java src/test/java/io/github/edwardg117/renderable/core/TestSessions.java src/test/java/io/github/edwardg117/renderable/core/SessionTest.java
```

Report for the commit: `feat(core): attribution-only calls and named time`.

---

### Task 4: The split line, the Frames tab and JSON schema 5

**Files:**
- Rewrite: `src/main/java/io/github/edwardg117/renderable/core/Splits.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java` (`frames`)
- Modify: `src/main/java/io/github/edwardg117/renderable/report/JsonReportWriter.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/SplitsTest.java` (append 1)
- Test: `src/test/java/io/github/edwardg117/renderable/report/JsonReportWriterTest.java` (update 4, append 1)
- Test: `src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java` (append 1)
- Test: `src/test/java/io/github/edwardg117/renderable/report/ReportFilesTest.java` (update 1)

Spec addendum §P5.A1, §P5.A6 and §P5.A9, with deviations 1 and 3 above. The text report's split line comes
from `Splits.average`, so `TextReportWriter` itself does not change here; its self-time line changes in
Task 7, once everything the line names has landed.

- [ ] **Step 1: Write the failing tests**

Append to `SplitsTest`:

```java
    @Test
    void newComponentsAppearWhenTheyHaveTime() {
        long[] b = new long[Bucket.COUNT];
        b[Bucket.HUD.ordinal()] = 2 * MS;
        b[Bucket.ENTITY_TICK.ordinal()] = 1 * MS;
        long[] named = new long[Window.COUNT];
        named[Window.RENDER.ordinal()] = 1 * MS;
        named[Window.TICK.ordinal()] = 400_000;
        named[Window.TASKS.ordinal()] = 1 * MS;
        FrameRecord f = new FrameRecord(0, 0, 20 * MS, 2 * MS, 5 * MS, 2 * MS, 3 * MS, b, named, 0, 0, List.of());

        assertEquals("tick 2.0 [entity tick 1.0, mods 0.4] | hud 2.0 | other render 3.0 [mods 1.0]"
                + " | tasks 2.0 [mods 1.0] | frame cap 3.0 | swap 8.0", Splits.frame(f));
        assertEquals("tick 2.00 [entity tick 1.00, mods 0.40] | hud 2.00 | other render 3.00 [mods 1.00]"
                + " | tasks 2.00 [mods 1.00] | frame cap 3.00 | swap/wait 8.00", Splits.average(FrameStats.of(List.of(f))));
    }
```

In `JsonReportWriterTest`:
- In `schemaModEnvironmentAndSession`, change `assertEquals(4, o.get("schema").getAsInt());` to
  `assertEquals(5, o.get("schema").getAsInt());`.
- In `theWriterOverloadProducesExactlyTheStringOverloadsBytes`, change the two `4`s to `5`:
  `assertEquals(5, JsonParser.parseString(...)...)` and `startsWith("{\n  \"schema\": 5,")`.
- In `vertexCallsAreReportedForASessionThatCountedThem`, change `assertEquals(4, o.get("schema").getAsInt());`
  to `assertEquals(JsonReportWriter.SCHEMA, o.get("schema").getAsInt());`. That test is about vertex calls, so
  it should not have to change with each schema.
- Rename `rankedRowsHaveTheSchema4ShapeInOrder` to `rankedRowsHaveTheirShapeInOrder`, and
  `sessionObjectHasTheSchema4ShapeInOrder` to `sessionObjectHasItsShapeInOrder`. Their bodies stay the same:
  neither shape changes.
- Append:

```java
    @Test
    void phase5FramesAndStatsCarryTheNewComponents() {
        JsonObject o = JsonParser.parseString(JsonReportWriter.write(TestSessions.phase5(), environment(), "0.5.0"))
                .getAsJsonObject();
        JsonObject f0 = o.getAsJsonArray("frames").get(0).getAsJsonObject();
        assertEquals(5_000, f0.get("renderUs").getAsLong());
        assertEquals(2_000, f0.get("tasksUs").getAsLong());
        assertEquals(3_000, f0.get("capUs").getAsLong());
        assertEquals(8_000, f0.get("swapUs").getAsLong());
        JsonObject named = f0.getAsJsonObject("namedUs");
        assertEquals(1_000, named.get("render").getAsLong());
        assertEquals(400, named.get("tick").getAsLong());
        assertEquals(1_000, named.get("tasks").getAsLong());
        assertEquals(2_000, f0.getAsJsonObject("bucketsUs").get("hud").getAsLong());

        JsonObject stats = o.getAsJsonObject("stats");
        assertEquals(2.0, stats.get("avgTasksMs").getAsDouble(), 1e-9);
        assertEquals(3.0, stats.get("avgCapMs").getAsDouble(), 1e-9);
        assertEquals(8.0, stats.get("avgSwapMs").getAsDouble(), 1e-9);
        assertEquals(1.0, stats.getAsJsonObject("avgNamedMs").get("render").getAsDouble(), 1e-9);
        assertEquals(0.4, stats.getAsJsonObject("avgNamedMs").get("tick").getAsDouble(), 1e-9);
        assertEquals(1.0, stats.getAsJsonObject("avgNamedMs").get("tasks").getAsDouble(), 1e-9);
        assertEquals(2.0, stats.getAsJsonObject("avgBucketsMs").get("hud").getAsDouble(), 1e-9);

        JsonObject plain = parse().getAsJsonArray("frames").get(0).getAsJsonObject();
        assertEquals(0, plain.get("tasksUs").getAsLong(), "a frame with no tasks still says so");
        assertTrue(plain.getAsJsonObject("namedUs").keySet().isEmpty(), "empty windows are omitted, like empty buckets");
    }
```

In `ReportFilesTest.theRealJsonWriterStreamsThroughToAFileThatParses`, change
`assertEquals(4, JsonParser.parseString(onDisk).getAsJsonObject().get("schema").getAsInt());` to
`assertEquals(JsonReportWriter.SCHEMA, JsonParser.parseString(onDisk).getAsJsonObject().get("schema").getAsInt());`,
for the same reason.

Append to `TextReportWriterTest`:

```java
    @Test
    void phase5SplitNamesTheNewComponents() {
        String s = TextReportWriter.write(TestSessions.phase5(), env, "0.5.0");
        assertTrue(s.contains("  Time split (avg ms/frame): tick 2.00 [entity tick 1.00, mods 0.40] | block entity 1.00"
                + " | entity 0.50 | hud 2.00 | other render 1.50 [mods 1.00] | tasks 2.00 [mods 1.00]"
                + " | frame cap 3.00 | swap/wait 8.00\n"), s);
    }
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*SplitsTest' --tests '*JsonReportWriterTest' --tests '*TextReportWriterTest'`
Expected: the new Splits and text assertions fail (no `tasks` or `mods` in the line), and the JSON tests fail
on `schema` and the missing keys.

- [ ] **Step 3: Rewrite `Splits`**

Replace the whole file with:

```java
package io.github.edwardg117.renderable.core;

/**
 * One-line descriptions of how a frame, or the average frame, was spent. Tick buckets are children of
 * {@code tick} and are printed in brackets after it, so the parts outside the brackets sum to the frame.
 * {@code mods x} in brackets after tick, other render or tasks is the part of it the Mods tab names (spec
 * addendum §P5.A6). Tasks and the frame cap are printed when they have time, as render buckets are.
 */
public final class Splits {

    private Splits() {
    }

    /** Average per-frame split, two decimals. */
    public static String average(FrameStats st) {
        StringBuilder sb = new StringBuilder();
        sb.append("tick ").append(Fmt.ms2(st.avgTickNanos()));
        appendTickChildren(sb, st.avgBucketNanos(), st.avgNamed(Window.TICK), true);
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
        appendTickChildren(sb, f.buckets(), f.named(Window.TICK), false);
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

    private static void appendTickChildren(StringBuilder sb, long[] buckets, long named, boolean twoDecimals) {
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
        if (any) sb.append(']');
    }

    private static void appendNamed(StringBuilder sb, long named, boolean twoDecimals) {
        if (named > 0) sb.append(" [mods ").append(twoDecimals ? Fmt.ms2(named) : Fmt.ms(named)).append(']');
    }
}
```

- [ ] **Step 4: Show the new components on the Frames tab**

In `Rows.frames`, replace:

```java
        rows.add(Row.plain("  other tick", Fmt.ms2(st.avgOtherTickNanos()) + " ms"));
        rows.add(Row.plain("render", Fmt.ms2(st.avgRenderNanos()) + " ms"));
        for (Bucket b : Bucket.VALUES) {
            if (b.isRender() && st.avgBucket(b) > 0) rows.add(Row.plain("  " + b.label(), Fmt.ms2(st.avgBucket(b)) + " ms"));
        }
        rows.add(Row.plain("  other render", Fmt.ms2(st.avgOtherRenderNanos()) + " ms"));
        rows.add(Row.plain("swap / wait", Fmt.ms2(st.avgSwapNanos()) + " ms"));
```

with:

```java
        rows.add(Row.plain("  other tick", Fmt.ms2(st.avgOtherTickNanos()) + " ms"));
        namedRow(rows, "    in the Mods tab", st.avgNamed(Window.TICK));
        rows.add(Row.plain("render", Fmt.ms2(st.avgRenderNanos()) + " ms"));
        for (Bucket b : Bucket.VALUES) {
            if (b.isRender() && st.avgBucket(b) > 0) rows.add(Row.plain("  " + b.label(), Fmt.ms2(st.avgBucket(b)) + " ms"));
        }
        rows.add(Row.plain("  other render", Fmt.ms2(st.avgOtherRenderNanos()) + " ms"));
        namedRow(rows, "    in the Mods tab", st.avgNamed(Window.RENDER));
        rows.add(Row.plain("tasks", Fmt.ms2(st.avgTasksNanos()) + " ms"));
        namedRow(rows, "  in the Mods tab", st.avgNamed(Window.TASKS));
        rows.add(Row.plain("frame cap", Fmt.ms2(st.avgCapNanos()) + " ms"));
        rows.add(Row.plain("swap / wait", Fmt.ms2(st.avgSwapNanos()) + " ms"));
```

and add this method after `frames`:

```java
    /** A named-time row (spec addendum §P5.A6), shown only when there is some. */
    private static void namedRow(List<Row> rows, String label, long nanos) {
        if (nanos > 0) rows.add(Row.plain(label, Fmt.ms2(nanos) + " ms"));
    }
```

Add `import io.github.edwardg117.renderable.core.Window;` to `Rows`.

- [ ] **Step 5: Write schema 5's frame and stats fields**

In `JsonReportWriter.java`:

1. Change `public static final int SCHEMA = 4;` to `public static final int SCHEMA = 5;`.
2. Add `import io.github.edwardg117.renderable.core.Window;`.
3. In the `stats` object, replace:

```java
        w.name("avgTickMs").value(st.avgTickNanos() / 1e6);
        w.name("avgRenderMs").value(st.avgRenderNanos() / 1e6);
        w.name("avgSwapMs").value(st.avgSwapNanos() / 1e6);
        w.name("avgOtherRenderMs").value(st.avgOtherRenderNanos() / 1e6);
        w.name("avgOtherTickMs").value(st.avgOtherTickNanos() / 1e6);
```

with:

```java
        w.name("avgTickMs").value(st.avgTickNanos() / 1e6);
        w.name("avgRenderMs").value(st.avgRenderNanos() / 1e6);
        w.name("avgTasksMs").value(st.avgTasksNanos() / 1e6);
        w.name("avgCapMs").value(st.avgCapNanos() / 1e6);
        w.name("avgSwapMs").value(st.avgSwapNanos() / 1e6);
        w.name("avgOtherRenderMs").value(st.avgOtherRenderNanos() / 1e6);
        w.name("avgOtherTickMs").value(st.avgOtherTickNanos() / 1e6);
        w.name("avgNamedMs").beginObject();
        for (Window win : Window.VALUES) w.name(win.label()).value(st.avgNamed(win) / 1e6);
        w.endObject();
```

4. In the `frames` loop, replace:

```java
            w.name("renderUs").value(f.renderNanos() / 1000);
            w.name("swapUs").value(f.swapNanos() / 1000);
            w.name("otherRenderUs").value(f.otherRenderNanos() / 1000);
```

with:

```java
            w.name("renderUs").value(f.renderNanos() / 1000);
            w.name("tasksUs").value(f.tasksNanos() / 1000);
            w.name("capUs").value(f.capNanos() / 1000);
            w.name("swapUs").value(f.swapNanos() / 1000);
            w.name("otherRenderUs").value(f.otherRenderNanos() / 1000);
            w.name("namedUs").beginObject();
            for (Window win : Window.VALUES) {
                long v = f.named(win);
                if (v > 0) w.name(win.label()).value(v / 1000);
            }
            w.endObject();
```

The `HUD` bucket appears in `bucketsUs` and `avgBucketsMs` by itself, as `hud`, because both loop over
`Bucket.VALUES`.

- [ ] **Step 6: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **146** tests, 0 failures. The existing split-line assertions in `SplitsTest`
and `TextReportWriterTest` still pass, because their fixtures have no tasks, cap or named time.

- [ ] **Step 7: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/Splits.java src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java src/main/java/io/github/edwardg117/renderable/report/JsonReportWriter.java src/test/java/io/github/edwardg117/renderable/core/SplitsTest.java src/test/java/io/github/edwardg117/renderable/report/JsonReportWriterTest.java src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java src/test/java/io/github/edwardg117/renderable/report/ReportFilesTest.java
```

Report for the commit: `feat(report): tasks, frame cap, hud and named time in the split and JSON schema 5`.

---

### Task 5: Frame boundaries, tasks, the frame cap and the HUD in the game

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Hook.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Session.java` (Javadoc only)
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/FrameBoundaries.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/FrameEvents.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/Probe.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/MinecraftMixin.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/GuiMixin.java`
- Modify: `src/main/resources/renderable.mixins.json`

Spec addendum §P5.A1 and §P5.A2. There are no unit tests here: everything touches Minecraft classes, which the
test classpath does not have. Task 11 proves the mixins apply, and the in-pack acceptance proves the numbers.

- [ ] **Step 1: Add the hooks phase 5 installs**

In `Hook.java`, replace:

```java
    VERTICES,
    ACCELERATED_VERTICES
}
```

with:

```java
    VERTICES,
    ACCELERATED_VERTICES,
    TASKS,
    FRAME_CAP,
    HUD,
    HUD_LAYERS,
    PACKETS,
    PAYLOADS,
    RIDER_TICK,
    EVENTS
}
```

- [ ] **Step 2: Create `FrameBoundaries`**

The four frame and tick listeners in `FrameEvents` move here, word for word, as plain static methods.

```java
package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Session;

/**
 * Frame and tick boundaries (spec §3.2, spec addendum §P5.A2), called by {@code MinecraftMixin} just before
 * NeoForge posts RenderFrameEvent.Pre and ClientTickEvent.Pre, and just after it posts the matching Post.
 * They were listeners in {@link FrameEvents}, but listeners at the default priority split other mods'
 * listeners of the same events across the window boundary, which matters once those listeners are timed.
 * Render thread only.
 */
public final class FrameBoundaries {
    private static final GcSampler GC = new GcSampler();

    private FrameBoundaries() {
    }

    /** Closes the previous frame and opens the next; ends the session when its duration has passed. */
    public static void renderPre() {
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

    public static void renderPost() {
        Session s = Probe.session();
        if (s != null) s.onRenderPost();
    }

    public static void tickPre() {
        Session s = Probe.session();
        if (s != null) s.onTickPre();
    }

    public static void tickPost() {
        Session s = Probe.session();
        if (s != null) s.onTickPost();
    }
}
```

- [ ] **Step 3: Remove the boundaries from `FrameEvents`**

Replace the whole of `FrameEvents.java` with:

```java
package io.github.edwardg117.renderable.hooks;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Ends a running session when its world goes away. Registered on the NeoForge game bus. The frame and tick
 * boundaries that were listeners here moved to {@link FrameBoundaries} (spec addendum §P5.A2).
 */
public final class FrameEvents {

    private FrameEvents() {
    }

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

    private static void discard(String reason) {
        if (Probe.session() == null) return;
        Probe.Listener l = Probe.listener();
        if (l != null) {
            l.onDiscardRequested(reason);
        } else {
            Probe.detach();
        }
    }

    private static void worldGone() {
        Probe.Listener l = Probe.listener();
        if (l != null) l.onWorldGone();
    }
}
```

- [ ] **Step 4: Add the tasks, frame-cap and marking entry points to `Probe`**

After `hooksApplied()`, add:

```java
    /**
     * Records that a hook fired, for work that is not a Probe call: event timing swaps listener arrays rather
     * than calling in (spec addendum §P5.A3). Render thread only, like every other SEEN write.
     */
    public static void markFired(Hook hook) {
        SEEN[hook.ordinal()] = true;
    }
```

After `endBucket()`, add:

```java
    /** {@code Minecraft.runAllTasks}: packets and other queued work (spec addendum §P5.A1). Render thread only. */
    public static void beginTasks() {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return;
        SEEN[Hook.TASKS.ordinal()] = true;
        s.onTasksBegin();
    }

    /** Ends {@link #beginTasks()}, with the same thread decision so the pair stays balanced. */
    public static void endTasks() {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return;
        s.onTasksEnd();
    }

    /** {@code RenderSystem.limitDisplayFPS}: the frame-rate cap's wait (spec addendum §P5.A1). Render thread only. */
    public static void beginFrameCap() {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return;
        SEEN[Hook.FRAME_CAP.ordinal()] = true;
        s.onCapBegin();
    }

    /** Ends {@link #beginFrameCap()}, with the same thread decision. */
    public static void endFrameCap() {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return;
        s.onCapEnd();
    }
```

- [ ] **Step 5: Create `MinecraftMixin`**

```java
package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.edwardg117.renderable.hooks.FrameBoundaries;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The frame loop's boundaries and two of its components (spec addendum §P5.A1 and §P5.A2). A frame opens just
 * before NeoForge posts RenderFrameEvent.Pre and its render window closes just after RenderFrameEvent.Post; a
 * tick opens just before ClientTickEvent.Pre and closes just after ClientTickEvent.Post. So every listener of
 * those events, at any priority, falls inside the window it belongs to.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;fireRenderFramePre(Lnet/minecraft/client/DeltaTracker;)V"))
    private void renderable$frameStart(boolean renderLevel, CallbackInfo ci) {
        if (!Probe.ACTIVE) return;
        FrameBoundaries.renderPre();
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;fireRenderFramePost(Lnet/minecraft/client/DeltaTracker;)V",
            shift = At.Shift.AFTER))
    private void renderable$renderEnd(boolean renderLevel, CallbackInfo ci) {
        if (!Probe.ACTIVE) return;
        FrameBoundaries.renderPost();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;fireClientTickPre()V"))
    private void renderable$tickStart(CallbackInfo ci) {
        if (!Probe.ACTIVE) return;
        FrameBoundaries.tickPre();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;fireClientTickPost()V", shift = At.Shift.AFTER))
    private void renderable$tickEnd(CallbackInfo ci) {
        if (!Probe.ACTIVE) return;
        FrameBoundaries.tickPost();
    }

    @WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V"))
    private void renderable$timeTasks(Minecraft instance, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(instance);
            return;
        }
        Probe.beginTasks();
        try {
            original.call(instance);
        } finally {
            Probe.endTasks();
        }
    }

    @WrapOperation(method = "runTick", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"))
    private void renderable$timeFrameCap(int fps, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(fps);
            return;
        }
        Probe.beginFrameCap();
        try {
            original.call(fps);
        } finally {
            Probe.endFrameCap();
        }
    }
}
```

- [ ] **Step 6: Create `GuiMixin`**

```java
package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Hook;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The HUD bucket (spec addendum §P5.A1): every layer, vanilla and modded, and the HUD's own events.
 * {@code GameRenderer.render} calls {@code Gui.render} once a frame; screens are drawn separately.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @WrapMethod(method = "render")
    private void renderable$timeHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(guiGraphics, deltaTracker);
            return;
        }
        Probe.beginBucket(Bucket.HUD, Hook.HUD);
        try {
            original.call(guiGraphics, deltaTracker);
        } finally {
            Probe.endBucket();
        }
    }
}
```

- [ ] **Step 7: Register both mixins**

In `renderable.mixins.json`, replace:

```json
    "BlockEntityAccumulatorMixin",
    "EntityAccumulatorMixin"
  ],
```

with:

```json
    "BlockEntityAccumulatorMixin",
    "EntityAccumulatorMixin",
    "MinecraftMixin",
    "GuiMixin"
  ],
```

- [ ] **Step 8: Correct `Session`'s boundary Javadoc**

In `Session.java`, change the Javadoc of the four boundary methods to say where they are now called from:

- `onRenderPre`: replace the first line, `Called at RenderFrameEvent.Pre with the JVM's cumulative GC count and pause time.`,
  with `Called just before NeoForge posts RenderFrameEvent.Pre (spec addendum §P5.A2), with the JVM's cumulative GC count and pause time.`
- `onRenderPost`: `/** Called just after NeoForge posts RenderFrameEvent.Post. */`
- `onTickPre`: `/** Called just before NeoForge posts ClientTickEvent.Pre. */`
- `onTickPost`: `/** Called just after NeoForge posts ClientTickEvent.Post. Ticks before the first frame are ignored. */`

- [ ] **Step 9: Build and run the tests**

Run: `./gradlew compileJava --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL both times, **146** tests, 0 failures.

- [ ] **Step 10: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/Hook.java src/main/java/io/github/edwardg117/renderable/core/Session.java src/main/java/io/github/edwardg117/renderable/hooks/FrameBoundaries.java src/main/java/io/github/edwardg117/renderable/hooks/FrameEvents.java src/main/java/io/github/edwardg117/renderable/hooks/Probe.java src/main/java/io/github/edwardg117/renderable/hooks/mixin/MinecraftMixin.java src/main/java/io/github/edwardg117/renderable/hooks/mixin/GuiMixin.java src/main/resources/renderable.mixins.json
```

Report for the commit: `feat(hooks): frame boundaries at the Minecraft call sites; tasks, frame cap and HUD`.

---

### Task 6: HUD layers, packets, payloads and riders

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/Probe.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/GuiLayerManagerMixin.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/PacketUtilsMixin.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/MainThreadPayloadHandlerMixin.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/mixin/ClientLevelMixin.java`
- Modify: `src/main/resources/renderable.mixins.json`

Spec addendum §P5.A4, §P5.A5 and §P5.A8. The layer, packet and payload entry points return whether they began
a call, because each can decline: Renderable's own layer, or a packet on the integrated server's thread. A
wrapper ends only what was begun. These mixins target NeoForge's and Minecraft's own classes at the pinned
versions, so they are required, like the vanilla mixins already in this config.

- [ ] **Step 1: Add the entry points to `Probe`**

Add these imports:

```java
import io.github.edwardg117.renderable.RenderableMod;
import io.github.edwardg117.renderable.core.ModKey;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.IdentityHashMap;
```

After the `BY_CLASS` field, add:

```java
    /**
     * HUD layer ids and packet types, each a constant object, mapped to the accumulator of their key (spec
     * addendum §P5.A4 and §P5.A5). Render thread only; cleared on attach and detach.
     */
    private static final IdentityHashMap<Object, Accumulator> BY_CONSTANT = new IdentityHashMap<>(256);

    /**
     * Mod payload ids mapped to the accumulator of their key (spec addendum §P5.A5). Keyed by id, not by the
     * payload's type object, because a mod may build a new {@code Type} on every call; keyed by identity, every
     * such payload would allocate a key while sampling. Render thread only; cleared on attach and detach.
     */
    private static final HashMap<ResourceLocation, Accumulator> PAYLOAD_BY_ID = new HashMap<>(64);
```

In `attach`, after `BY_CLASS.clear();`, add `BY_CONSTANT.clear();` and `PAYLOAD_BY_ID.clear();`. In `detach`,
after `BY_CLASS.clear();`, add the same two lines.

After `endFrameCap()`, add:

```java
    /**
     * One HUD layer (spec addendum §P5.A4), keyed by its id, whose namespace names the mod. Renderable's own
     * layers are left out: the sampling line is self time, and the label overlay is empty while sampling.
     * Returns whether a call was begun, which the caller must then end with {@link #end()}.
     */
    public static boolean beginLayer(ResourceLocation layer) {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return false;
        if (RenderableMod.MOD_ID.equals(layer.getNamespace())) return false;
        SEEN[Hook.HUD_LAYERS.ordinal()] = true;
        s.beginAttribution(constantAccumulator(s, layer, ModKey.Kind.LAYER, layer));
        return true;
    }

    /**
     * One packet's main-thread handling (spec addendum §P5.A5), keyed by its type's id. Ignored off the render
     * thread, because the integrated server handles its own packets through the same method. Returns whether
     * a call was begun.
     */
    public static boolean beginPacket(Packet<?> packet) {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return false;
        SEEN[Hook.PACKETS.ordinal()] = true;
        PacketType<?> type = packet.type();
        s.beginAttribution(constantAccumulator(s, type, ModKey.Kind.PACKET, type.id()));
        return true;
    }

    /** A mod payload's main-thread handler (spec addendum §P5.A5), keyed by the payload's id. Same rules. */
    public static boolean beginPayload(CustomPacketPayload payload) {
        Session s = session;
        if (s == null || Thread.currentThread() != renderThread) return false;
        SEEN[Hook.PAYLOADS.ordinal()] = true;
        ResourceLocation id = payload.type().id();
        Accumulator a = PAYLOAD_BY_ID.get(id);
        if (a == null) {
            a = s.accumulator(new ModKey(ModKey.Kind.PACKET, id.getNamespace(), id.toString()));
            PAYLOAD_BY_ID.put(id, a);
        }
        s.beginAttribution(a);
        return true;
    }

    /**
     * A passenger's tick, which vanilla runs from its vehicle's (spec addendum §P5.A8). It is an ordinary entity
     * tick nested inside the vehicle's, so the entity-tick bucket is still credited once, by the vehicle.
     */
    public static void beginRiderTick(Entity rider) {
        Session s = session;
        if (s == null) return;
        SEEN[Hook.RIDER_TICK.ordinal()] = true;
        s.begin(instanceAccumulator(s, rider), Bucket.ENTITY_TICK);
    }

    private static Accumulator constantAccumulator(Session s, Object constant, ModKey.Kind kind, ResourceLocation id) {
        Accumulator a = BY_CONSTANT.get(constant);
        if (a == null) {
            a = s.accumulator(new ModKey(kind, id.getNamespace(), id.toString()));
            BY_CONSTANT.put(constant, a);
        }
        return a;
    }
```

- [ ] **Step 2: Create `GuiLayerManagerMixin`**

```java
package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.neoforged.neoforge.client.gui.GuiLayerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Times each HUD layer (spec addendum §P5.A4). {@code renderInner} loops over its named layers; the loop's
 * {@code NamedLayer} local, the only one of that type, gives the layer's id.
 */
@Mixin(GuiLayerManager.class)
public abstract class GuiLayerManagerMixin {

    @WrapOperation(method = "renderInner", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/LayeredDraw$Layer;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
    private void renderable$timeLayer(LayeredDraw.Layer layer, GuiGraphics guiGraphics, DeltaTracker deltaTracker,
                                      Operation<Void> original, @Local GuiLayerManager.NamedLayer named) {
        if (!Probe.ACTIVE) {
            original.call(layer, guiGraphics, deltaTracker);
            return;
        }
        boolean timed = Probe.beginLayer(named.name());
        try {
            original.call(layer, guiGraphics, deltaTracker);
        } finally {
            if (timed) Probe.end();
        }
    }
}
```

- [ ] **Step 3: Create `PacketUtilsMixin`**

```java
package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Times each vanilla packet's main-thread handling (spec addendum §P5.A5). A handler that is not on the main
 * thread queues this lambda there through {@code ensureRunningOnSameThread}, and the lambda runs it inside
 * {@code Minecraft.runAllTasks}.
 */
@Mixin(PacketUtils.class)
public abstract class PacketUtilsMixin {

    @WrapOperation(method = "lambda$ensureRunningOnSameThread$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
    private static void renderable$timePacket(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(packet, listener);
            return;
        }
        boolean timed = Probe.beginPacket(packet);
        try {
            original.call(packet, listener);
        } finally {
            if (timed) Probe.end();
        }
    }
}
```

- [ ] **Step 4: Create `MainThreadPayloadHandlerMixin`**

```java
package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.handling.MainThreadPayloadHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Times each mod payload handled on the main thread (spec addendum §P5.A5). NeoForge wraps a main-thread
 * handler, the default, in {@code MainThreadPayloadHandler}, which queues this lambda to run the mod's handler
 * inside {@code Minecraft.runAllTasks}.
 */
@Mixin(MainThreadPayloadHandler.class)
public abstract class MainThreadPayloadHandlerMixin {

    @WrapOperation(method = "lambda$handle$0", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/network/handling/IPayloadHandler;handle(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V"))
    private void renderable$timePayload(IPayloadHandler<?> handler, CustomPacketPayload payload, IPayloadContext context,
                                        Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(handler, payload, context);
            return;
        }
        boolean timed = Probe.beginPayload(payload);
        try {
            original.call(handler, payload, context);
        } finally {
            if (timed) Probe.end();
        }
    }
}
```

- [ ] **Step 5: Create `ClientLevelMixin`**

```java
package io.github.edwardg117.renderable.hooks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.edwardg117.renderable.hooks.Probe;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Times a passenger's own tick (spec addendum §P5.A8). The client ticks a rider from its vehicle through
 * {@code tickPassenger}, never through {@code Level.guardEntityTick}, so {@code LevelMixin} never sees it.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @WrapOperation(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
    private void renderable$timeRiderTick(Entity rider, Operation<Void> original) {
        if (!Probe.ACTIVE) {
            original.call(rider);
            return;
        }
        Probe.beginRiderTick(rider);
        try {
            original.call(rider);
        } finally {
            Probe.end();
        }
    }
}
```

- [ ] **Step 6: Register the four mixins**

In `renderable.mixins.json`, replace:

```json
    "MinecraftMixin",
    "GuiMixin"
  ],
```

with:

```json
    "MinecraftMixin",
    "GuiMixin",
    "GuiLayerManagerMixin",
    "PacketUtilsMixin",
    "MainThreadPayloadHandlerMixin",
    "ClientLevelMixin"
  ],
```

- [ ] **Step 7: Build and run the tests**

Run: `./gradlew compileJava --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL both times, **146** tests, 0 failures.

- [ ] **Step 8: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/hooks/Probe.java src/main/java/io/github/edwardg117/renderable/hooks/mixin/GuiLayerManagerMixin.java src/main/java/io/github/edwardg117/renderable/hooks/mixin/PacketUtilsMixin.java src/main/java/io/github/edwardg117/renderable/hooks/mixin/MainThreadPayloadHandlerMixin.java src/main/java/io/github/edwardg117/renderable/hooks/mixin/ClientLevelMixin.java src/main/resources/renderable.mixins.json
```

Report for the commit: `feat(hooks): HUD layers, packets, payloads and rider ticks`.

---

### Task 7: Event handlers

**Files:**
- Modify: `gradle.properties`, `build.gradle`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Environment.java` (two constants)
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/events/BusInternals.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/events/ListenerOwners.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/events/EventTimingRun.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/events/TimedListener.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/events/ListenerSwap.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/events/ModOwners.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/events/EventTiming.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/RenderableConfig.java` (a comment)
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/RenderableCommand.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/EnvironmentCollector.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`, `ui/screen/Rows.java`, `core/Session.java` (the self-time wording)
- Create: `src/test/java/io/github/edwardg117/renderable/hooks/events/ListenerSwapTest.java`
- Modify: `src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java`

Spec addendum §P5.A3. Everything the unit tests exercise (`BusInternals`, `ListenerOwners`, `ListenerSwap`,
`TimedListener` and `EventTimingRun`) uses only the bus API, `core` and the JDK. `ModOwners` and `EventTiming`
reach NeoForge's `ModList` and `NeoForge.EVENT_BUS`, so they stay out of the tests.

- [ ] **Step 1: Put the bus on the test classpath**

In `gradle.properties`, after `slf4j_version=2.0.9`, add:

```properties
# The event bus library this NeoForge version runs on (net.neoforged:bus in its launch profile's libraries).
# Event timing's tests run against the real bus, which only main's classpath gets from the moddev plugin.
# Keep it equal to what the pinned NeoForge resolves:
# `./gradlew dependencies --configuration runtimeClasspath | grep net.neoforged:bus`.
bus_version=8.0.5
```

In `build.gradle`, after `testImplementation "org.slf4j:slf4j-api:${slf4j_version}"`, add:

```groovy
    testImplementation "net.neoforged:bus:${bus_version}"
```

Run: `./gradlew dependencies --configuration runtimeClasspath --no-daemon | grep "net.neoforged:bus"`
Expected: a line containing `net.neoforged:bus:8.0.5`. If it names another version, use that one in
`gradle.properties` and tell the orchestrator.

- [ ] **Step 2: Add the environment vocabulary**

In `core/Environment.java`, after the `ABSENT` constant, add:

```java
    /** The key saying whether event handlers were timed (spec addendum §P5.A3): "on", or {@link #UNAVAILABLE} and why. */
    public static final String EVENT_TIMING = "eventTiming";

    /** Starts the {@link #EVENT_TIMING} value when the event bus could not be reached; the reason follows it. */
    public static final String UNAVAILABLE = "unavailable: ";
```

- [ ] **Step 3: Write the failing tests**

Create `src/test/java/io/github/edwardg117/renderable/hooks/events/ListenerSwapTest.java`:

```java
package io.github.edwardg117.renderable.hooks.events;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.core.SessionConfig;
import net.neoforged.bus.ListenerList;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventListener;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Event timing against a real NeoForge bus (spec addendum §P5.A3). The test classpath carries the same bus
 * version the game runs, so these tests pin the bus behaviour {@link BusInternals} relies on as well as ours.
 */
class ListenerSwapTest {
    static final long MS = 1_000_000L;

    /** Listeners record their name here and advance the fake clock, so each one's time is known. */
    static final List<String> CALLS = new ArrayList<>();
    static final long[] NOW = {0};

    public static class Tick extends Event {
    }

    public static class Stoppable extends Event implements ICancellableEvent {
    }

    public static class InstanceSubscriber {
        @SubscribeEvent
        public void onTick(Tick e) {
            CALLS.add("instance");
            NOW[0] += 1 * MS;
        }
    }

    public static class StaticSubscriber {
        @SubscribeEvent
        public static void onTick(Tick e) {
            CALLS.add("static");
        }
    }

    public static class MethodSubscriber {
        @SubscribeEvent
        public static void onTick(Tick e) {
            CALLS.add("method");
        }
    }

    public static class Stoppers {
        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void stop(Stoppable e) {
            CALLS.add("stopper");
            e.setCanceled(true);
        }

        @SubscribeEvent
        public static void skipped(Stoppable e) {
            CALLS.add("skipped");
        }

        @SubscribeEvent(receiveCanceled = true)
        public static void seesCancelled(Stoppable e) {
            CALLS.add("sees cancelled");
        }
    }

    IEventBus bus;
    BusInternals internals;
    Session session;
    EventTimingRun run;
    ListenerOwners owners;

    @BeforeEach
    void setUp() throws Exception {
        CALLS.clear();
        NOW[0] = 0;
        bus = BusBuilder.builder().build();
        internals = BusInternals.of(bus);
        session = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> NOW[0], 0);
        run = new EventTimingRun(session, Thread.currentThread());
        owners = new ListenerOwners(internals::registrations);
    }

    /** Wraps every listener, keying it by the simple name of the class that registered it. */
    ListenerSwap swapTimingEverything() {
        return new ListenerSwap(internals, (listener, eventClass) -> {
            Class<?> owner = owners.ownerOf(listener);
            String mod = owner == null ? "unknown" : owner.getSimpleName();
            return new TimedListener(listener, session.accumulator(ModKey.event(mod, eventClass.getName())), run);
        });
    }

    Accumulator timing(String mod, Class<?> event) {
        return session.accumulator(ModKey.event(mod, event.getName()));
    }

    @Test
    void aSwappedBusInvokesTheSameListenersInOrderAndTimesEach() {
        bus.addListener(Tick.class, e -> {
            CALLS.add("lambda");
            NOW[0] += 2 * MS;
        });
        bus.register(new InstanceSubscriber());
        bus.register(StaticSubscriber.class);
        bus.post(new Tick());
        List<String> before = List.copyOf(CALLS);
        CALLS.clear();

        assertEquals(1, swapTimingEverything().wrapAll(), "one list, for Tick");
        session.onRenderPre(0, 0);
        bus.post(new Tick());

        assertEquals(before, CALLS, "the same listeners, in the same order");
        assertEquals(1 * MS, timing("InstanceSubscriber", Tick.class).renderNanos);
        assertEquals(1, timing("StaticSubscriber", Tick.class).calls);
        long lambdaNanos = session.accumulators().entrySet().stream()
                .filter(e -> ((ModKey) e.getKey()).modId().startsWith("ListenerSwapTest"))
                .mapToLong(e -> e.getValue().renderNanos).sum();
        assertEquals(2 * MS, lambdaNanos, "a lambda belongs to the class that wrote it");
    }

    @Test
    void aThrowingListenerReachesTheExceptionHandlerWithItsIndexAndIsRethrown() throws Exception {
        List<String> handled = new ArrayList<>();
        bus = BusBuilder.builder().setExceptionHandler((b, event, listeners, index, throwable) ->
                handled.add(index + " " + listeners[index] + " " + throwable.getMessage())).build();
        internals = BusInternals.of(bus);
        owners = new ListenerOwners(internals::registrations);
        bus.addListener(Tick.class, e -> CALLS.add("first"));
        bus.addListener(Tick.class, e -> {
            throw new IllegalStateException("boom");
        });

        swapTimingEverything().wrapAll();
        session.onRenderPre(0, 0);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> bus.post(new Tick()));

        assertEquals("boom", thrown.getMessage());
        assertEquals(List.of("first"), CALLS);
        assertEquals(1, handled.size());
        assertTrue(handled.get(0).startsWith("1 "), handled.get(0));
        assertFalse(handled.get(0).contains("TimedListener"), "the log names the listener, not the proxy: " + handled.get(0));
        NOW[0] = 10 * MS;
        session.onRenderPre(0, 0);
        assertEquals(0, session.unbalancedFrames(), "the proxy ended its call on the way out");
    }

    @Test
    void cancellationStillSkipsListenersThatDoNotReceiveIt() {
        bus.register(Stoppers.class);
        swapTimingEverything().wrapAll();
        session.onRenderPre(0, 0);
        bus.post(new Stoppable());
        assertEquals(List.of("stopper", "sees cancelled"), CALLS);
    }

    @Test
    void ownersResolveEveryKindOfRegistrationTarget() throws Exception {
        bus.register(new InstanceSubscriber());
        bus.register(StaticSubscriber.class);
        bus.register(MethodSubscriber.class.getMethod("onTick", Tick.class));
        bus.addListener(Tick.class, e -> CALLS.add("lambda"));
        bus.post(new Tick());    // builds the cached array, unwrapping what the bus unwraps

        ListenerList list = internals.lists().get(Tick.class);
        List<String> owned = new ArrayList<>();
        for (EventListener l : internals.cachedArray(list).get()) {
            Class<?> owner = owners.ownerOf(l);
            assertNotNull(owner, "every listener the bus posts to has an owner: " + l);
            owned.add(owner.getSimpleName());
        }
        assertTrue(owned.contains("InstanceSubscriber"), owned.toString());
        assertTrue(owned.contains("StaticSubscriber"), owned.toString());
        assertTrue(owned.contains("MethodSubscriber"), "a single static method belongs to its class: " + owned);
        assertTrue(owned.stream().anyMatch(o -> o.startsWith("ListenerSwapTest")), owned.toString());
        assertSame(StaticSubscriber.class, ListenerOwners.ownerClass(StaticSubscriber.class), "a class target is itself");
    }

    @Test
    void restorePutsBackEveryArrayItInstalled() {
        bus.register(StaticSubscriber.class);
        bus.post(new Tick());
        bus.post(new Stoppable());
        ListenerList tickList = internals.lists().get(Tick.class);
        EventListener[] original = internals.cachedArray(tickList).get();

        ListenerSwap swap = swapTimingEverything();
        swap.wrapAll();
        assertEquals(1, ListenerSwap.listsHoldingProxies(internals), "Stoppable has no listeners, so nothing to wrap");
        assertNotSame(original, internals.cachedArray(tickList).get());

        assertEquals(1, swap.restore());
        assertSame(original, internals.cachedArray(tickList).get(), "the very array the bus built");
        assertEquals(0, ListenerSwap.listsHoldingProxies(internals));
        bus.post(new Tick());
        assertEquals(List.of("static", "static"), CALLS);
    }

    @Test
    void aListRebuiltMidSessionIsWrappedAgainAndLeftAloneByRestore() {
        bus.register(StaticSubscriber.class);
        ListenerSwap swap = swapTimingEverything();
        assertEquals(1, swap.wrapAll());

        bus.register(new InstanceSubscriber());          // marks the Tick list for a rebuild
        session.onRenderPre(0, 0);
        bus.post(new Tick());                            // the rebuild replaces the proxies with originals
        assertEquals(0, timing("InstanceSubscriber", Tick.class).calls, "not timed until the next pass");
        assertEquals(0, ListenerSwap.listsHoldingProxies(internals));

        owners.newRound();
        assertEquals(1, swap.wrapAll(), "the rebuilt list is wrapped again");
        bus.post(new Tick());
        assertEquals(1, timing("InstanceSubscriber", Tick.class).calls, "a listener registered mid-session is timed");

        bus.register(new InstanceSubscriber());          // a second rebuild, which restore must not undo
        bus.post(new Tick());
        EventListener[] rebuilt = internals.cachedArray(internals.lists().get(Tick.class)).get();
        assertEquals(0, swap.restore(), "the only array it installed has already been replaced");
        assertSame(rebuilt, internals.cachedArray(internals.lists().get(Tick.class)).get());
    }

    @Test
    void registeringAndUnregisteringMidSessionLeavesNoProxyBehind() {
        bus.register(StaticSubscriber.class);
        ListenerSwap swap = swapTimingEverything();
        swap.wrapAll();
        InstanceSubscriber late = new InstanceSubscriber();
        bus.register(late);
        owners.newRound();
        swap.wrapAll();
        bus.unregister(late);
        swap.restore();

        assertEquals(0, ListenerSwap.listsHoldingProxies(internals));
        bus.post(new Tick());
        assertEquals(List.of("static"), CALLS, "the unregistered listener is gone, and nothing is wrapped");
    }

    @Test
    void offTheRenderThreadAProxyOnlyCallsThrough() {
        run = new EventTimingRun(session, new Thread(() -> { }));   // a render thread that is not this one
        bus.register(new InstanceSubscriber());
        swapTimingEverything().wrapAll();
        session.onRenderPre(0, 0);
        bus.post(new Tick());
        assertEquals(List.of("instance"), CALLS);
        assertEquals(0, timing("InstanceSubscriber", Tick.class).calls);
    }

    @Test
    void anEndedRunOnlyCallsThrough() {
        bus.register(new InstanceSubscriber());
        swapTimingEverything().wrapAll();
        run.live = false;
        session.onRenderPre(0, 0);
        bus.post(new Tick());
        assertEquals(List.of("instance"), CALLS);
        assertEquals(0, timing("InstanceSubscriber", Tick.class).calls);
    }
}
```

- [ ] **Step 4: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*ListenerSwapTest'`
Expected: compilation failure, `cannot find symbol: class BusInternals` (and the other new classes).

- [ ] **Step 5: Create `BusInternals`**

```java
package io.github.edwardg117.renderable.hooks.events;

import net.neoforged.bus.EventBus;
import net.neoforged.bus.ListenerList;
import net.neoforged.bus.LockHelper;
import net.neoforged.bus.api.EventListener;
import net.neoforged.bus.api.IEventBus;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The private members of NeoForge's event bus 8.0.x that event timing needs (spec addendum §P5.A0 and
 * §P5.A3). The bus module is declared {@code open}, so they are reachable by reflection. Each is looked up
 * once, in {@link #of}, so a bus version that renamed one fails there rather than part way through a session.
 */
final class BusInternals {
    private final EventBus bus;
    private final MethodHandle listenerLists;
    private final MethodHandle readMap;
    private final MethodHandle registrations;
    private final MethodHandle cachedArray;

    private BusInternals(EventBus bus, MethodHandle listenerLists, MethodHandle readMap,
                         MethodHandle registrations, MethodHandle cachedArray) {
        this.bus = bus;
        this.listenerLists = listenerLists;
        this.readMap = readMap;
        this.registrations = registrations;
        this.cachedArray = cachedArray;
    }

    /** Looks the members up for {@code bus}. Fails when the bus is not bus 8.0's {@link EventBus}. */
    static BusInternals of(IEventBus bus) throws ReflectiveOperationException {
        if (!(bus instanceof EventBus eventBus)) {
            throw new ReflectiveOperationException("the bus is a " + bus.getClass().getName() + ", not an "
                    + EventBus.class.getName());
        }
        MethodHandles.Lookup here = MethodHandles.lookup();
        MethodHandles.Lookup inBus = MethodHandles.privateLookupIn(EventBus.class, here);
        MethodHandles.Lookup inLockHelper = MethodHandles.privateLookupIn(LockHelper.class, here);
        MethodHandles.Lookup inList = MethodHandles.privateLookupIn(ListenerList.class, here);
        return new BusInternals(eventBus,
                inBus.findGetter(EventBus.class, "listenerLists", LockHelper.class),
                inLockHelper.findVirtual(LockHelper.class, "getReadMap", MethodType.methodType(Map.class)),
                inBus.findGetter(EventBus.class, "listeners", ConcurrentHashMap.class),
                inList.findGetter(ListenerList.class, "listeners", AtomicReference.class));
    }

    /** Every event class's listener list, as a snapshot that the bus replaces rather than edits. */
    @SuppressWarnings("unchecked")
    Map<Class<?>, ListenerList> lists() {
        try {
            return (Map<Class<?>, ListenerList>) readMap.invoke(listenerLists.invoke(bus));
        } catch (Throwable t) {
            throw new IllegalStateException("could not read the event bus's listener lists", t);
        }
    }

    /** Each registration target (a class, a static method, an object or a consumer) and the listeners it made. */
    @SuppressWarnings("unchecked")
    Map<Object, List<EventListener>> registrations() {
        try {
            return (Map<Object, List<EventListener>>) registrations.invoke(bus);
        } catch (Throwable t) {
            throw new IllegalStateException("could not read the event bus's registrations", t);
        }
    }

    /** The reference holding {@code list}'s cached array, which {@code EventBus.post} reads. */
    @SuppressWarnings("unchecked")
    AtomicReference<EventListener[]> cachedArray(ListenerList list) {
        try {
            return (AtomicReference<EventListener[]>) cachedArray.invoke(list);
        } catch (Throwable t) {
            throw new IllegalStateException("could not read a listener list's cached array", t);
        }
    }
}
```

- [ ] **Step 6: Create `ListenerOwners`**

```java
package io.github.edwardg117.renderable.hooks.events;

import net.neoforged.bus.IWrapperListener;
import net.neoforged.bus.api.EventListener;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Which class registered each listener on a bus (spec addendum §P5.A3). The bus keeps a map from each
 * registration target to the listeners it made, and this inverts it by identity. A listener's own class says
 * nothing, because the bus defines every {@code @SubscribeEvent} listener as a hidden class in its own lookup.
 */
final class ListenerOwners {
    private final Supplier<Map<Object, List<EventListener>>> registrations;
    private IdentityHashMap<EventListener, Class<?>> index;
    private boolean rebuiltThisRound;

    ListenerOwners(Supplier<Map<Object, List<EventListener>>> registrations) {
        this.registrations = registrations;
        this.index = index(registrations.get());
    }

    /** Allows one more rebuild of the index; called before each wrapping pass. */
    void newRound() {
        rebuiltThisRound = false;
    }

    /**
     * The class that registered {@code listener}, or null when the bus has no record of it. A listener missing
     * from the index was registered after it was built, so the first miss in a round rebuilds it, once.
     */
    Class<?> ownerOf(EventListener listener) {
        Class<?> owner = index.get(listener);
        if (owner == null && !rebuiltThisRound) {
            rebuiltThisRound = true;
            index = index(registrations.get());
            owner = index.get(listener);
        }
        return owner;
    }

    static IdentityHashMap<EventListener, Class<?>> index(Map<Object, List<EventListener>> registrations) {
        IdentityHashMap<EventListener, Class<?>> out = new IdentityHashMap<>();
        for (Map.Entry<Object, List<EventListener>> e : registrations.entrySet()) {
            Class<?> owner = ownerClass(e.getKey());
            // The bus's lists are Collections.synchronizedList, which must not be iterated unlocked; toArray locks.
            for (Object o : e.getValue().toArray()) {
                EventListener listener = (EventListener) o;
                out.put(listener, owner);
                // The array the bus posts to may hold the unwrapped listener instead (ListenerList.unwrapListeners).
                if (listener instanceof IWrapperListener wrapper) out.put(wrapper.getWithoutCheck(), owner);
            }
        }
        return out;
    }

    /** A class target is itself, a single static method is its declaring class, and anything else its own class. */
    static Class<?> ownerClass(Object target) {
        if (target instanceof Class<?> c) return c;
        if (target instanceof Method m) return m.getDeclaringClass();
        return target.getClass();
    }
}
```

- [ ] **Step 7: Create `EventTimingRun` and `TimedListener`**

```java
package io.github.edwardg117.renderable.hooks.events;

import io.github.edwardg117.renderable.core.Session;

/** One session's event timing: its session, its render thread, and whether its proxies still time anything. */
final class EventTimingRun {
    final Session session;
    final Thread renderThread;
    /** Cleared before the arrays are restored, so a post already iterating a proxy array stops timing at once. */
    volatile boolean live = true;

    EventTimingRun(Session session, Thread renderThread) {
        this.session = session;
        this.renderThread = renderThread;
    }
}
```

```java
package io.github.edwardg117.renderable.hooks.events;

import io.github.edwardg117.renderable.core.Accumulator;
import net.neoforged.bus.GeneratedEventListener;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventListener;

/**
 * A listener wrapped for one session (spec addendum §P5.A3). It times the listener it wraps into its owner's
 * accumulator as an attribution-only call. Off the render thread, or once its run has ended, it only calls
 * through. It is not an {@code IWrapperListener}, so the bus never unwraps it.
 *
 * <p>Bus 8.0.5 seals {@code EventListener}, so this extends {@code GeneratedEventListener}, the non-sealed base of
 * the listeners the bus generates. Nothing in the bus tests for that class, so the proxy is treated as any other
 * listener.
 */
final class TimedListener extends GeneratedEventListener {
    private final EventListener delegate;
    private final Accumulator accumulator;
    private final EventTimingRun run;

    TimedListener(EventListener delegate, Accumulator accumulator, EventTimingRun run) {
        this.delegate = delegate;
        this.accumulator = accumulator;
        this.run = run;
    }

    @Override
    public void invoke(Event event) {
        EventTimingRun r = run;
        if (!r.live || Thread.currentThread() != r.renderThread) {
            delegate.invoke(event);
            return;
        }
        r.session.beginAttribution(accumulator);
        try {
            delegate.invoke(event);
        } finally {
            r.session.end();
        }
    }

    /** The bus's exception log names listeners by {@code toString}, so this reads as the listener it wraps. */
    @Override
    public String toString() {
        return delegate.toString();
    }
}
```

- [ ] **Step 8: Create `ListenerSwap`**

```java
package io.github.edwardg117.renderable.hooks.events;

import net.neoforged.bus.ListenerList;
import net.neoforged.bus.api.EventListener;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Swaps every listener list's cached array on one bus for a wrapped copy, and back (spec addendum §P5.A3).
 * {@code EventBus.post} reads a list's array through an {@code AtomicReference}, so a compare-and-set there
 * changes what later posts invoke and nothing else. Registration, unregistration and a post already running
 * are untouched. Render thread only.
 */
final class ListenerSwap {

    /** What goes in a wrapped array in place of one listener: a proxy, or the listener itself. */
    interface Wrapper {
        EventListener wrap(EventListener listener, Class<?> eventClass);
    }

    private record Swapped(AtomicReference<EventListener[]> ref, EventListener[] original, EventListener[] wrapped) {
    }

    private final BusInternals bus;
    private final Wrapper wrapper;
    private final IdentityHashMap<ListenerList, Swapped> swapped = new IdentityHashMap<>();

    ListenerSwap(BusInternals bus, Wrapper wrapper) {
        this.bus = bus;
        this.wrapper = wrapper;
    }

    /**
     * Wraps every list whose cached array is not one this swap installed. That is all of them the first time;
     * after that, any list rebuilt since (a listener was registered or unregistered) or created since (the first
     * post of a new event class). A list with nothing to wrap is left alone. Returns how many lists it wrapped.
     */
    int wrapAll() {
        int n = 0;
        for (Map.Entry<Class<?>, ListenerList> e : bus.lists().entrySet()) {
            ListenerList list = e.getValue();
            Swapped mine = swapped.get(list);
            EventListener[] current = list.getListeners();   // rebuilds the array first if it is due
            if (mine != null && current == mine.wrapped()) continue;
            EventListener[] wrapped = wrap(current, e.getKey());
            if (wrapped == null) continue;
            AtomicReference<EventListener[]> ref = bus.cachedArray(list);
            if (ref.compareAndSet(current, wrapped)) {
                swapped.put(list, new Swapped(ref, current, wrapped));
                n++;
            }
        }
        return n;
    }

    /** A copy of {@code listeners} with each one the wrapper replaces, or null when it replaces none. */
    private EventListener[] wrap(EventListener[] listeners, Class<?> eventClass) {
        EventListener[] out = null;
        for (int i = 0; i < listeners.length; i++) {
            EventListener w = wrapper.wrap(listeners[i], eventClass);
            if (w != listeners[i]) {
                if (out == null) out = listeners.clone();
                out[i] = w;
            }
        }
        return out;
    }

    /**
     * Puts back every array this swap installed that is still in place; a list rebuilt meanwhile already holds a
     * fresh original array and is left alone. Returns how many it restored.
     */
    int restore() {
        int n = 0;
        for (Swapped s : swapped.values()) {
            if (s.ref().compareAndSet(s.wrapped(), s.original())) n++;
        }
        swapped.clear();
        return n;
    }

    /** Lists on {@code bus} whose cached array holds a {@link TimedListener}; zero outside a session. */
    static int listsHoldingProxies(BusInternals bus) {
        int n = 0;
        for (ListenerList list : bus.lists().values()) {
            EventListener[] array = bus.cachedArray(list).get();
            if (array == null) continue;
            for (EventListener l : array) {
                if (l instanceof TimedListener) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }
}
```

- [ ] **Step 9: Run the new tests**

Run: `./gradlew test --rerun --no-daemon --tests '*ListenerSwapTest'`
Expected: 9 tests, 0 failures. If `ownersResolveEveryKindOfRegistrationTarget` finds a listener with no
owner, report the listener's class and `toString`: it means the bus caches an object this plan did not
expect, and the fix belongs in `ListenerOwners.index`, not in the test.

- [ ] **Step 10: Create `ModOwners` and `EventTiming`**

```java
package io.github.edwardg117.renderable.hooks.events;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a class to the id of the mod whose file holds it, through the class's JPMS module (spec addendum
 * §P5.A3). A class in no mod file keeps its module's name, and a class in no named module is "unknown".
 */
final class ModOwners {
    static final String UNKNOWN = "unknown";

    private final Map<String, String> modByModule;
    private final HashMap<Class<?>, String> cache = new HashMap<>();

    private ModOwners(Map<String, String> modByModule) {
        this.modByModule = modByModule;
    }

    /** Built from NeoForge's mod list; a file with several mods is named by its first. */
    static ModOwners fromModList() {
        HashMap<String, String> byModule = new HashMap<>();
        for (IModFileInfo file : ModList.get().getModFiles()) {
            List<IModInfo> mods = file.getMods();
            if (!mods.isEmpty()) byModule.putIfAbsent(file.moduleName(), mods.get(0).getModId());
        }
        return new ModOwners(byModule);
    }

    String modOf(Class<?> c) {
        return cache.computeIfAbsent(c, k -> {
            Module module = k.getModule();
            if (!module.isNamed()) return UNKNOWN;
            return modByModule.getOrDefault(module.getName(), module.getName());
        });
    }
}
```

```java
package io.github.edwardg117.renderable.hooks.events;

import io.github.edwardg117.renderable.RenderableMod;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.Hook;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.hooks.Probe;
import net.neoforged.bus.api.EventListener;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Times mod event handlers for the length of a session (spec addendum §P5.A3). Every listener list of
 * NeoForge's game bus is swapped for timing proxies when a session starts and swapped back when it ends, so
 * nothing of it is installed between sessions. The bus loads in the boot module layer, where no mixin
 * reaches, which is why this is not a mixin. Render thread only.
 */
public final class EventTiming {
    private static final String ON = "on";

    private static BusInternals bus;
    /** Why the bus could not be reached, or null when it could or has not been tried. */
    private static String unavailable;
    private static EventTimingRun run;
    private static ListenerSwap swap;
    private static ListenerOwners owners;

    private EventTiming() {
    }

    /** "on", or {@link Environment#UNAVAILABLE} and why, for the report's environment header. */
    public static String status() {
        bus();
        return unavailable == null ? ON : Environment.UNAVAILABLE + unavailable;
    }

    /**
     * Wraps every listener list of the game bus for {@code session}. Runs inside a tick listener, so a failure
     * here would crash the client: anything thrown turns event timing off instead (see {@link #fail}).
     */
    public static void start(Session session) {
        stop();
        BusInternals b = bus();
        if (b == null) return;
        try {
            EventTimingRun r = new EventTimingRun(session, Thread.currentThread());
            ListenerOwners o = new ListenerOwners(b::registrations);
            ModOwners mods = ModOwners.fromModList();
            run = r;
            owners = o;
            swap = new ListenerSwap(b, (listener, eventClass) -> wrap(r, o, mods, listener, eventClass));
            o.newRound();
            if (swap.wrapAll() > 0) Probe.markFired(Hook.EVENTS);
        } catch (RuntimeException | LinkageError e) {
            fail("start", e);
        }
    }

    /**
     * Wraps the lists rebuilt or created since the last pass; called about once a second while sampling, from
     * inside the frame loop, so a failure turns event timing off rather than escaping.
     */
    public static void recheck() {
        if (swap == null) return;
        try {
            owners.newRound();
            swap.wrapAll();
        } catch (RuntimeException | LinkageError e) {
            fail("recheck", e);
        }
    }

    /** Stops timing and puts every array back. Safe to call when nothing is running, and never throws. */
    public static void stop() {
        EventTimingRun r = run;
        if (r != null) r.live = false;
        ListenerSwap s = swap;
        run = null;
        swap = null;
        owners = null;
        if (s == null) return;
        try {
            int restored = s.restore();
            RenderableMod.LOGGER.info("Renderable event timing restored {} listener lists; {} still hold a timing proxy",
                    restored, listsHoldingProxies());
        } catch (RuntimeException | LinkageError e) {
            // The run is already dead, so a proxy left behind only calls through; the list drops it when it rebuilds.
            unavailable = describe(e);
            bus = null;
            RenderableMod.LOGGER.error("Renderable could not put every event listener list back: {}", unavailable, e);
        }
    }

    /**
     * The bus was found but did not behave as bus 8.0.5 does (spec addendum §P5.A3's risks): the lookups catch a
     * rename, not a change of behaviour. Event timing is off for the rest of the client run, and the report's
     * {@code eventTiming} entry says why.
     */
    private static void fail(String during, Throwable e) {
        RenderableMod.LOGGER.warn("Renderable event timing failed during {} and is off until restart", during, e);
        stop();
        unavailable = describe(e);
        bus = null;
    }

    private static String describe(Throwable e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /** Lists holding a timing proxy right now; zero outside a session. For {@code /renderable debug events}. */
    public static int listsHoldingProxies() {
        BusInternals b = bus();
        return b == null ? 0 : ListenerSwap.listsHoldingProxies(b);
    }

    private static BusInternals bus() {
        if (bus == null && unavailable == null) {
            try {
                bus = BusInternals.of(NeoForge.EVENT_BUS);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                unavailable = describe(e);
                RenderableMod.LOGGER.warn("Renderable cannot time event handlers: {}", unavailable, e);
            }
        }
        return bus;
    }

    private static EventListener wrap(EventTimingRun r, ListenerOwners o, ModOwners mods, EventListener listener,
                                      Class<?> eventClass) {
        Class<?> owner = o.ownerOf(listener);
        String mod = owner == null ? ModOwners.UNKNOWN : mods.modOf(owner);
        if (RenderableMod.MOD_ID.equals(mod)) return listener;
        return new TimedListener(listener, r.session.accumulator(ModKey.event(mod, eventClass.getName())), r);
    }
}
```

- [ ] **Step 11: Wire it into the controller**

In `ProfilerController.java`, add `import io.github.edwardg117.renderable.hooks.events.EventTiming;`, then:

1. In `start`, replace:

```java
        Probe.attach(session, sessionDimension);
```

with:

```java
        Probe.attach(session, sessionDimension);
        EventTiming.start(session);
```

2. In `discard`, replace:

```java
    private void discard(String reason) {
        Probe.detach();
```

with:

```java
    private void discard(String reason) {
        EventTiming.stop();
        Probe.detach();
```

3. In `finish`, replace:

```java
        Session s = session;
        if (s == null) return;
        Probe.detach();
```

with:

```java
        Session s = session;
        if (s == null) return;
        EventTiming.stop();
        Probe.detach();
```

4. In `onFrameStarted`, replace:

```java
            s.beginSelf();
            try {
                sampleFlywheel(s);
            } finally {
                s.endSelf();
            }
```

with:

```java
            s.beginSelf();
            try {
                sampleFlywheel(s);
                EventTiming.recheck();
            } finally {
                s.endSelf();
            }
```

- [ ] **Step 12: The debug command, the environment and the config comment**

In `RenderableCommand.java`, add `import io.github.edwardg117.renderable.hooks.events.EventTiming;` and replace
the `debug` branch:

```java
                .then(Commands.literal("debug")
                        .requires(src -> RenderableConfig.DEBUG_COMMANDS.get())
                        .then(Commands.literal("stall")
                                .then(Commands.argument("ms", IntegerArgumentType.integer(1, 5000))
                                        .executes(ctx -> {
                                            c.debugStall(IntegerArgumentType.getInteger(ctx, "ms"));
                                            return 1;
                                        }))));
```

with:

```java
                .then(Commands.literal("debug")
                        .requires(src -> RenderableConfig.DEBUG_COMMANDS.get())
                        .then(Commands.literal("stall")
                                .then(Commands.argument("ms", IntegerArgumentType.integer(1, 5000))
                                        .executes(ctx -> {
                                            c.debugStall(IntegerArgumentType.getInteger(ctx, "ms"));
                                            return 1;
                                        })))
                        .then(Commands.literal("events").executes(ctx -> {
                            ProfilerController.chat("Renderable: " + EventTiming.listsHoldingProxies()
                                    + " event lists hold timing proxies; event timing is " + EventTiming.status() + ".");
                            return 1;
                        })));
```

In `RenderableConfig.java`, replace the `DEBUG_COMMANDS` comment:

```java
        DEBUG_COMMANDS = b.comment("Enables /renderable debug stall <ms>, which sleeps the render thread to test hitch attribution.")
```

with:

```java
        DEBUG_COMMANDS = b.comment("Enables /renderable debug stall <ms>, which sleeps the render thread to test hitch attribution,",
                        "and /renderable debug events, which counts the event lists holding timing proxies.")
```

In `EnvironmentCollector.java`, add `import io.github.edwardg117.renderable.hooks.events.EventTiming;` and, after
`m.put("distanthorizons", modVersion("distanthorizons"));`, add:

```java
        m.put(Environment.EVENT_TIMING, EventTiming.status());
```

- [ ] **Step 13: Name the event-list check in the self-time line**

The self-time line has to name everything `beginSelf` wraps, and the check `EventTiming.recheck` does once a
second is now one of them. Task 5 also moved the HUD line into the HUD bucket. Both changes land together here.

In `TextReportWriter.java`, replace:

```java
                    "  Renderable's own in-frame work (HUD and Flywheel sampling): %s ms measured, inside other render.\n",
```

with:

```java
                    "  Renderable's own in-frame work (its HUD line, the Flywheel sample and the event-list check): %s ms measured, inside hud and other render.\n",
```

In `Rows.frames`, replace:

```java
            rows.add(Row.plain("Renderable self time", Fmt.ms2(r.selfNanos()) + " ms (HUD and Flywheel sampling)"));
```

with:

```java
            rows.add(Row.plain("Renderable self time", Fmt.ms2(r.selfNanos()) + " ms (HUD line, Flywheel sample, event-list check)"));
```

In `Session.java`, replace the Javadoc of `beginSelf` with:

```java
    /**
     * Starts timing work the mod does for itself inside a frame: the sampling HUD line, the Flywheel visual
     * sample (spec addendum §P4.B3) and the once-a-second event-list check (spec addendum §P5.A3). It is not
     * attributed to any key: the HUD line is inside the HUD bucket and the other two are in other render, and
     * the report states the total so a reader can subtract it. Pair with {@link #endSelf()}.
     *
     * <p>Nests: an inner begin/end pair inside an outer one does not truncate the outer interval, matching
     * {@link #begin} and {@link #end}. Everything timed here must be named in the self-time line printed by
     * {@code TextReportWriter} and {@code Rows} ("its HUD line, the Flywheel sample and the event-list check"
     * as of this writing); wrapping another region without updating both labels (and the README) makes that
     * text silently false.
     */
```

In `TextReportWriterTest.selfTimeLineIsPrintedWhenThereIsAny`, replace the expected line with:

```java
                "  Renderable's own in-frame work (its HUD line, the Flywheel sample and the event-list check): 12.40 ms measured, inside hud and other render.\n"),
```

- [ ] **Step 14: Build and run the tests**

Run: `./gradlew compileJava --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **155** tests, 0 failures.

- [ ] **Step 15: Stage**

```bash
git add gradle.properties build.gradle src/main/java/io/github/edwardg117/renderable/core/Environment.java src/main/java/io/github/edwardg117/renderable/core/Session.java src/main/java/io/github/edwardg117/renderable/hooks/events src/main/java/io/github/edwardg117/renderable/ProfilerController.java src/main/java/io/github/edwardg117/renderable/RenderableConfig.java src/main/java/io/github/edwardg117/renderable/ui/RenderableCommand.java src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java src/main/java/io/github/edwardg117/renderable/report/EnvironmentCollector.java src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java src/test/java/io/github/edwardg117/renderable/hooks/events/ListenerSwapTest.java src/test/java/io/github/edwardg117/renderable/report/TextReportWriterTest.java
```

Report for the commit: `feat(hooks): time mod event handlers with per-session listener proxies`.

---

### Task 8: The Mods tab, the report's Mods section and the JSON `mods` array

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/RankedEntry.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Ranking.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/RankedTable.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/EmptyStates.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Tab.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/ResultsScreen.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/JsonReportWriter.java`
- Test: `core/RankedTableTest.java`, `core/RankingTest.java`, `core/EmptyStatesTest.java` (one each),
  `report/TextReportWriterTest.java`, `report/JsonReportWriterTest.java` (one each), `ui/screen/TabTest.java`
  (extend)

Spec addendum §P5.A7, with deviation 2 above. The Mods rows show a mod's time in total, not split by render
and tick: where it ran is what the split line's named figures give.

- [ ] **Step 1: Write the failing tests**

Append to `RankedTableTest`:

```java
    @Test
    void modsRowsCarryTotalsNotSplits() {
        SessionResult r = TestSessions.phase5();
        RankedEntry create = Ranking.byType(r, ProfileKey.Category.MOD).get(0);
        assertEquals(List.of("mod=create", "share=10.0%", "us/f=2000.0", "peak ms=2.00", "gc f=0", "calls/f=1.0"),
                pairs(RankedTable.MODS, RankedTable.mods(create.typeId(), create, r.frameCount(), r.stats().totalNanos())));
        assertEquals(2, RankedTable.MODS_NOTE_LINES.size());
    }
```

Append to `RankingTest`:

```java
    @Test
    void allCallsSortsByRenderAndTickCallsTogether() {
        SessionResult r = TestSessions.phase5();
        List<RankedEntry> mods = Ranking.sort(Ranking.byType(r, ProfileKey.Category.MOD), Ranking.Sort.ALL_CALLS);
        assertEquals("somemod", mods.get(0).typeId(), "4 calls: 2 render, 2 tick; every other mod has 2");
        assertEquals(2, mods.get(0).tickCalls());
        assertEquals(2.0, mods.get(0).allCallsPerFrame(r.frameCount()), 1e-9);
    }
```

Append to `EmptyStatesTest`:

```java
    @Test
    void modsStatesTellUnavailableFromNothing() {
        assertEquals("(no mod handlers, HUD layers or packets were timed)", EmptyStates.mods(env(Map.of())));
        assertEquals("", EmptyStates.modsHandlersMissing(env(Map.of(Environment.EVENT_TIMING, "on"))));
        Environment off = env(Map.of(Environment.EVENT_TIMING, Environment.UNAVAILABLE + "NoSuchFieldException: listenerLists"));
        assertEquals("(event timing unavailable: NoSuchFieldException: listenerLists)", EmptyStates.mods(off));
        assertEquals("(event handlers were not timed: NoSuchFieldException: listenerLists)",
                EmptyStates.modsHandlersMissing(off));
    }
```

Append to `TextReportWriterTest`, adding `import io.github.edwardg117.renderable.core.RankedTable;`:

```java
    @Test
    void modsSectionListsModsWithTheirTopMembers() {
        String s = TextReportWriter.write(TestSessions.phase5(), env, "0.5.0");
        int at = s.indexOf("Mods (top 15, each with its top 3)\n");
        assertTrue(at > 0, s);
        assertTrue(at < s.indexOf("Block entities by type"), "right after the hitch list");
        String section = s.substring(at, s.indexOf("\n\n", at));
        assertTrue(section.contains("  " + String.join(" ", RankedTable.MODS_NOTE_LINES) + "\n"), section);
        assertTrue(section.contains("\n  create    "), section);
        assertTrue(section.contains("\n    event RenderLevelStageEvent "), section);
        assertTrue(section.contains("\n    packet minecraft:level_chunk_with_light "), section);
        assertTrue(section.indexOf("\n  create") < section.indexOf("\n  somemod"), "most expensive first");

        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put(Environment.EVENT_TIMING, Environment.UNAVAILABLE + "NoSuchFieldException: listenerLists");
        String off = TextReportWriter.write(TestSessions.phase5(), Environment.of(m, List.of()), "0.5.0");
        assertTrue(off.contains("  (event handlers were not timed: NoSuchFieldException: listenerLists)\n"), off);
        assertTrue(TextReportWriter.write(TestSessions.sample(), env, "0.5.0")
                .contains("Mods (top 15, each with its top 3)\n  (no mod handlers, HUD layers or packets were timed)\n"));
    }
```

Append to `JsonReportWriterTest`:

```java
    @Test
    void modsArrayCarriesEveryModAndMember() {
        JsonObject o = JsonParser.parseString(JsonReportWriter.write(TestSessions.phase5(), environment(), "0.5.0"))
                .getAsJsonObject();
        JsonArray mods = o.getAsJsonArray("mods");
        assertEquals(4, mods.size());
        JsonObject create = mods.get(0).getAsJsonObject();
        List<String> keys = new ArrayList<>(RANKED_KEYS);
        keys.add("entries");
        assertEquals(keys, List.copyOf(create.keySet()), "a ranked row, then its entries");
        assertEquals("create", create.get("key").getAsString());
        assertEquals(4_000, create.get("renderUs").getAsLong());
        assertEquals(1, create.get("members").getAsInt(), "members stays the count, as on every ranked row");
        JsonArray entries = create.getAsJsonArray("entries");
        assertEquals(1, entries.size());
        assertEquals("event@create:net.neoforged.neoforge.client.event.RenderLevelStageEvent",
                entries.get(0).getAsJsonObject().get("key").getAsString());
        JsonObject somemod = mods.get(3).getAsJsonObject();
        assertEquals(2, somemod.getAsJsonArray("entries").size());
        assertEquals(800, somemod.get("tickUs").getAsLong(), "the tick handler's time is tick time");
        assertEquals(0, parse().getAsJsonArray("mods").size(), "a session with none still writes the array");
    }
```

In `TabTest.theRankedTabsShowTheReportsColumnsAndEveryTabOpensInTheReportsOrder`, add after
`assertEquals(RankedTable.PARTICLE, names(Tab.PARTICLES));`:

```java
        assertEquals(RankedTable.MODS, names(Tab.MODS));
```

change the right-align loop's array to `new Tab[]{Tab.BLOCK_ENTITIES, Tab.ENTITIES, Tab.PARTICLES, Tab.MODS}`,
and add before the method's last line:

```java
        assertEquals(ColumnSort.of(Ranking.Sort.TOTAL), opening(Tab.MODS));
        assertEquals("share", Tab.MODS.columns().get(Tab.MODS.columns().defaultSortColumn()).name(),
                "share and us/f both sort by total time; the arrow sits on the first, as on the other ranked tabs");
```

In `TabTest.sortableColumnsMatchTheKindOfRowsTheTabHolds`, add `Tab.MODS` to exactly two arrays:

- the ranked loop's `new Tab[]{Tab.BLOCK_ENTITIES, Tab.ENTITIES, Tab.PARTICLES, Tab.FLYWHEEL}`;
- the last loop's `new Tab[]{Tab.BLOCK_ENTITIES, Tab.ENTITIES, Tab.PARTICLES, Tab.FLYWHEEL, Tab.BLOCKS, Tab.SECTIONS}`,
  which requires at least two sortable columns.

Leave the mesh loop's `new Tab[]{Tab.BLOCKS, Tab.SECTIONS}` alone: it requires a mesh label sort.

In `TabTest.everyTabShowsTheseColumnsAndOpensOnTheStarredOne`, add before the Particles assertion:

```java
        assertEquals("mod 5 L LABEL | *share 1 R TOTAL | us/f 1 R TOTAL | peak ms 1 R PEAK | gc f 1 R GC_FRAMES"
                + " | calls/f 1 R ALL_CALLS", describe(Tab.MODS));
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon`
Expected: compilation failure (`RankedTable.MODS`, `Sort.ALL_CALLS`, `EmptyStates.mods`, `Tab.MODS` do not
exist).

- [ ] **Step 3: The core pieces**

In `RankedEntry.java`, after `callsPerFrame`, add:

```java
    /** Render and tick calls per frame together: a mod's handlers run on both sides (spec addendum §P5.A7). */
    public double allCallsPerFrame(int frames) {
        return frames == 0 ? 0 : (double) (calls + tickCalls) / frames;
    }
```

In `Ranking.java`, change the `Sort` enum to:

```java
    public enum Sort {
        TOTAL, RENDER, TICK, PEAK, VERTICES, CALLS, ALL_CALLS, SHADOW, GC_FRAMES, LABEL
    }
```

and add this arm to the switch in `sort(List, Sort, boolean)`, after the `CALLS` arm:

```java
            case ALL_CALLS -> Comparator.comparingLong(e -> e.calls() + e.tickCalls());
```

In `RankedTable.java`, after `PARTICLE`, add:

```java
    /** Headers of the Mods table (spec addendum §P5.A7): a mod's time in total, not split by where it ran. */
    public static final List<String> MODS = List.of("mod", "share", "us/f", "peak ms", "gc f", "calls/f");

    /**
     * Printed above the Mods table on both surfaces: the screen prints each line as a row, the report the two
     * joined. Two lines so each fits the results screen at the default GUI scale.
     */
    public static final List<String> MODS_NOTE_LINES = List.of(
            "(a mod's time includes what its handlers call, so shares overlap;",
            "a library that runs other mods' handlers carries their time)");
```

and after `particle(...)`, add:

```java
    /** One row of the Mods table. The caller supplies the name: a mod id, or a member's label. */
    public static String[] mods(String name, RankedEntry e, int frames, long total) {
        return new String[]{
                name,
                Fmt.pct(e.sharePercent(total)),
                Fmt.us(e.usPerFrame(frames)),
                Fmt.peakMs(e.maxFrameNanos(), e.gcFrames()),
                Fmt.count(e.gcFrames()),
                Fmt.num(e.allCallsPerFrame(frames))};
    }
```

In `EmptyStates.java`, at the end of the class, add:

```java
    /** The Mods table's empty state (spec addendum §P5.A7). */
    public static String mods(Environment env) {
        String why = eventTimingUnavailable(env);
        return why != null
                ? "(event timing unavailable: " + why + ")"
                : "(no mod handlers, HUD layers or packets were timed)";
    }

    /**
     * A line to print above a Mods table that has rows but no event handlers because the bus could not be
     * reached, or an empty string when handlers were timed or the report predates event timing.
     */
    public static String modsHandlersMissing(Environment env) {
        String why = eventTimingUnavailable(env);
        return why != null ? "(event handlers were not timed: " + why + ")" : "";
    }

    /** Why event timing could not run, or null. */
    private static String eventTimingUnavailable(Environment env) {
        String status = env.value(Environment.EVENT_TIMING);
        return status.startsWith(Environment.UNAVAILABLE) ? status.substring(Environment.UNAVAILABLE.length()) : null;
    }
```

- [ ] **Step 4: The tab**

In `Tab.java`, replace the enum's Javadoc and constants:

```java
/** Tabs of the results screen and the column layout each one uses. Eight tabs, four per row on the screen. */
public enum Tab {
    FRAMES("Frames", Columns.METRIC_VALUE),
    HITCHES("Hitches", Columns.HITCH),
    BLOCK_ENTITIES("Block Entities", Columns.RANKED),
```

with:

```java
/** Tabs of the results screen and the column layout each one uses. Nine tabs, five per row on the screen. */
public enum Tab {
    FRAMES("Frames", Columns.METRIC_VALUE),
    HITCHES("Hitches", Columns.HITCH),
    MODS("Mods", Columns.MODS),
    BLOCK_ENTITIES("Block Entities", Columns.RANKED),
```

and, in `Columns`, after `PARTICLE`, add:

```java
        static final Columns MODS = of(ColumnSort.of(Ranking.Sort.TOTAL),
                left("mod", 5, ColumnSort.of(Ranking.Sort.LABEL)),
                right("share", 1, ColumnSort.of(Ranking.Sort.TOTAL)),
                right("us/f", 1, ColumnSort.of(Ranking.Sort.TOTAL)),
                right("peak ms", 1, ColumnSort.of(Ranking.Sort.PEAK)),
                right("gc f", 1, ColumnSort.of(Ranking.Sort.GC_FRAMES)),
                right("calls/f", 1, ColumnSort.of(Ranking.Sort.ALL_CALLS)));
```

In `ResultsScreen.java`, change `private static final int TABS_PER_ROW = 4;` to
`private static final int TABS_PER_ROW = 5;`. Nine tabs then take two rows, as eight did, so `SUMMARY_Y` and
everything below it stay where they are. At the narrowest supported GUI (427 px, at Auto scale on 1440p and
4K) a button is `(427 - 8) / 5 - 4 = 79` px wide, and "Block Entities" is 66 px of text.

- [ ] **Step 5: The rows**

In `Rows.java`, add `import io.github.edwardg117.renderable.core.ModKey;`, add this arm to the switch in
`build` after `HITCHES`:

```java
            case MODS -> mods(r, env, sort, ascending, expanded);
```

and add this method after `ranked`:

```java
    /** Spec addendum §P5.A7 Mods tab: one row per mod, expandable to its event types, layers and packets. */
    static List<Row> mods(SessionResult r, Environment env, ColumnSort sort, boolean ascending, Set<String> expanded) {
        int frames = r.frameCount();
        long total = r.stats().totalNanos();
        Ranking.Sort by = rankedSort(sort);
        List<RankedEntry> mods = Ranking.sort(Ranking.byType(r, ProfileKey.Category.MOD), by, ascending);
        List<Row> rows = new ArrayList<>();
        if (mods.isEmpty()) {
            rows.add(Row.plain(EmptyStates.mods(env)));
            return rows;
        }
        String missing = EmptyStates.modsHandlersMissing(env);
        if (!missing.isEmpty()) rows.add(Row.plain(missing));
        for (String line : RankedTable.MODS_NOTE_LINES) rows.add(Row.plain(line));
        for (RankedEntry m : mods) {
            rows.add(new Row(RankedTable.mods(m.typeId() + "  (" + m.members() + ")", m, frames, total),
                    null, m.typeId(), true, true, 0, null, -1));
            if (expanded.contains(m.typeId())) {
                for (RankedEntry e : Ranking.sort(Ranking.membersOf(r, ProfileKey.Category.MOD, m.typeId()), by, ascending)) {
                    rows.add(new Row(RankedTable.mods(((ModKey) e.key()).memberLabel(), e, frames, total),
                            e.key(), m.typeId(), false, false, 1, null, -1));
                }
            }
        }
        return rows;
    }
```

A mod id never contains a colon and every block entity or entity type id does, so the screen's single
`expanded` set cannot confuse a mod with a type.

- [ ] **Step 6: The report section**

In `TextReportWriter.java`, add `import io.github.edwardg117.renderable.core.ModKey;`, add these constants after
`HITCHES`:

```java
    static final int TOP_MODS = 15;
    static final int TOP_MOD_MEMBERS = 3;
```

insert the call right after the hitch loop's closing `sb.append('\n');`, before
`appendRanked(sb, "Block entities by type", ...`:

```java
        appendMods(sb, r, env, frames, total);
```

and add this method after `appendRanked`:

```java
    /** Spec addendum §P5.A7: the top mods by time, each with its top members indented beneath it. */
    static void appendMods(StringBuilder sb, SessionResult r, Environment env, int frames, long total) {
        sb.append("Mods (top ").append(TOP_MODS).append(", each with its top ").append(TOP_MOD_MEMBERS).append(")\n");
        List<RankedEntry> mods = Ranking.byType(r, ProfileKey.Category.MOD);
        if (mods.isEmpty()) {
            sb.append("  ").append(EmptyStates.mods(env)).append("\n\n");
            return;
        }
        String missing = EmptyStates.modsHandlersMissing(env);
        if (!missing.isEmpty()) sb.append("  ").append(missing).append('\n');
        sb.append("  ").append(String.join(" ", RankedTable.MODS_NOTE_LINES)).append('\n');
        Table t = Table.nameThenNumbers(RankedTable.MODS);
        for (RankedEntry m : mods.subList(0, Math.min(TOP_MODS, mods.size()))) {
            t.row((Object[]) RankedTable.mods(m.typeId(), m, frames, total));
            List<RankedEntry> members = Ranking.membersOf(r, ProfileKey.Category.MOD, m.typeId());
            for (RankedEntry e : members.subList(0, Math.min(TOP_MOD_MEMBERS, members.size()))) {
                t.row((Object[]) RankedTable.mods("  " + ((ModKey) e.key()).memberLabel(), e, frames, total));
            }
        }
        sb.append(t.render("  ")).append('\n');
    }
```

- [ ] **Step 7: The JSON array**

In `JsonReportWriter.java`, replace `ranked(...)` with this pair, which keeps its output byte for byte:

```java
    private static void ranked(JsonWriter w, List<RankedEntry> entries, int frames) throws IOException {
        w.beginArray();
        for (RankedEntry e : entries) {
            w.beginObject();
            rankedFields(w, e, frames);
            w.endObject();
        }
        w.endArray();
    }

    /** One ranked row's fields, in the order {@code JsonReportWriterTest.RANKED_KEYS} pins. */
    private static void rankedFields(JsonWriter w, RankedEntry e, int frames) throws IOException {
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
    }

    /**
     * Spec addendum §P5.A7: every mod, most expensive first, each a ranked row followed by an {@code entries}
     * array of its members. Not {@code members}: the ranked row already has a {@code members} count.
     */
    private static void mods(JsonWriter w, SessionResult r, int frames) throws IOException {
        w.beginArray();
        for (RankedEntry m : Ranking.byType(r, ProfileKey.Category.MOD)) {
            w.beginObject();
            rankedFields(w, m, frames);
            w.name("entries");
            ranked(w, Ranking.membersOf(r, ProfileKey.Category.MOD, m.typeId()), frames);
            w.endObject();
        }
        w.endArray();
    }
```

In `body`, replace:

```java
        w.name("hitches").beginArray();
        for (FrameRecord h : r.hitches()) w.value(h.index());
        w.endArray();
```

with:

```java
        w.name("hitches").beginArray();
        for (FrameRecord h : r.hitches()) w.value(h.index());
        w.endArray();

        w.name("mods");
        mods(w, r, r.frameCount());
```

- [ ] **Step 8: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **160** tests, 0 failures.

- [ ] **Step 9: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/RankedEntry.java src/main/java/io/github/edwardg117/renderable/core/Ranking.java src/main/java/io/github/edwardg117/renderable/core/RankedTable.java src/main/java/io/github/edwardg117/renderable/core/EmptyStates.java src/main/java/io/github/edwardg117/renderable/ui/screen/Tab.java src/main/java/io/github/edwardg117/renderable/ui/screen/ResultsScreen.java src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java src/main/java/io/github/edwardg117/renderable/report/JsonReportWriter.java src/test/java/io/github/edwardg117/renderable
```

Report for the commit: `feat: the Mods tab, report section and JSON array`.

---

### Task 9: A dev aid: `-Drenderable.autostart`

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/Keybinds.java`

Deviation 4 above. A subagent cannot press a key in a dev client, so Task 11 could not otherwise produce a
report to check. The property is read once, from the JVM, and does nothing unless it is set.

- [ ] **Step 1: Add the autostart to the controller**

In `ProfilerController.java`, after `private static final long MIN_KEEP_NANOS = 2_000_000_000L;`, add:

```java
    /** Dev aid: the length of one session started by itself after joining a world, or 0 for none. */
    private static final int AUTOSTART_SECONDS = Integer.getInteger("renderable.autostart", 0);
    /** Ten seconds of ticks, so the world has settled before an autostarted session begins. */
    private static final int AUTOSTART_DELAY_TICKS = 200;
```

after the `lastFolder` field, add:

```java
    private boolean autostarted;
    private int ticksInWorld;
```

and after `toggle()`, add:

```java
    /**
     * Dev aid: with {@code -Drenderable.autostart=<seconds>}, runs one session of that length ten seconds after a
     * world is joined, so a headless dev client produces a report to check. Called every client tick; does
     * nothing when the property is absent.
     */
    public void tickAutostart() {
        if (AUTOSTART_SECONDS <= 0 || autostarted) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            ticksInWorld = 0;
            return;
        }
        if (++ticksInWorld < AUTOSTART_DELAY_TICKS) return;
        autostarted = true;
        start(Math.max(2, Math.min(600, AUTOSTART_SECONDS)));
    }
```

- [ ] **Step 2: Call it every tick**

In `Keybinds.onClientTick`, add as the first line of the method body:

```java
        ProfilerController.get().tickAutostart();
```

- [ ] **Step 3: Build and run the tests**

Run: `./gradlew compileJava --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **160** tests.

- [ ] **Step 4: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/ProfilerController.java src/main/java/io/github/edwardg117/renderable/ui/Keybinds.java
```

Report for the commit: `feat: -Drenderable.autostart for headless dev-client checks`.

---

### Task 10: Documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md`
- Modify: `docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md`
- Modify: `CLAUDE.md`

Each replacement below quotes the text to find exactly. If a quoted passage is not in the file, stop and
report rather than guess.

- [ ] **Step 1: README, "Using it"**

After item 4, whose last line is `   hovering over them says so.`, add:

```markdown
5. The **Mods** tab ranks mods by the time their event handlers, HUD layers and packets took; click a mod
   to see which.
```

- [ ] **Step 2: README, "Reading a report"**

Replace the time split bullet:

```markdown
- **Time split** shows where the average frame went: client tick (with its block entity, entity
  and particle tick shares in brackets), block entities, entities, particles, Flywheel, light
  updates, chunk upload, other render work, and swap/wait (vsync, the GPU, the driver). A big
  swap/wait with a low GPU load is not a rendering problem in the world.
```

with:

```markdown
- **Time split** shows where the average frame went: client tick (with its block entity, entity
  and particle tick shares in brackets), block entities, entities, particles, Flywheel, light
  updates, chunk upload, the HUD, other render work, **tasks** (packets and other work queued for the
  main thread), the **frame cap** (the wait for the fps limit, when one is set and reached), and swap/wait
  (the buffer swap and vsync, the GPU and driver, and the rest of the frame loop). Tasks and the frame cap
  are shown when they took time. `[mods x]` after tick, other render or tasks is the part of it the **Mods**
  tab names. A big swap/wait with a low GPU load is not a rendering problem in the world.
```

In the block entities and entities bullet, replace this text (the bullet continues after it):

```markdown
- **Block entities** and **entities** are ranked by microseconds per frame; expand a type to see
  each position.
```

with:

```markdown
- **Block entities** and **entities** are ranked by microseconds per frame; expand a type to see
  each position. A riding entity's tick has its own row and is also counted in its vehicle's.
```

After the particles bullet (it ends `particles culled before rendering are not counted.`), add:

```markdown
- **Mods** ranks each mod by the time its event handlers, its HUD layers and the packets addressed to it
  took, in microseconds per frame; expand a mod to see each event type, layer and packet type. Counting is
  inclusive: a handler's time includes whatever it calls, so a handler that draws an entity counts that
  entity too, and shares overlap with each other and with the other tables. Events are timed however they
  are posted, by NeoForge, the game or another mod. A library that runs other mods' handlers for them,
  such as Architectury, Balm or Puzzles Lib, carries their time. Vanilla layers and packets appear under
  `minecraft`. Handlers are timed by swapping the event bus's listener lists for timing wrappers when a
  session starts and back when it ends, so nothing of it runs between sessions. A handler counts a call
  whenever the bus reaches it, including one that skips an event another handler cancelled.
```

Replace the self-time bullet:

```markdown
- **Renderable's own in-frame work** is measured, not estimated: it is the sampling HUD plus the
  once-a-second Flywheel visual sample, and it sits inside **other render**. The in-world overlay is
  cleared when a session starts, so it costs nothing; a highlight left over from an earlier session
  draws out the rest of its ten seconds unmeasured.
```

with:

```markdown
- **Renderable's own in-frame work** is measured, not estimated: its HUD line, the once-a-second Flywheel
  visual sample and the once-a-second check for newly registered event handlers. The HUD line sits inside
  **hud** and the other two inside **other render**. The in-world overlay is cleared when a session
  starts, so it costs nothing; a highlight left over from an earlier session draws out the rest of its ten
  seconds unmeasured.
```

In the Limits section, replace:

```markdown
timed calls and vertex counting, and separately the measured cost of its own HUD and sampling. The
once-a-second Flywheel visual sample lands in that frame's other render time. Timing is CPU-side only;
```

with:

```markdown
timed calls and vertex counting, and separately the measured cost of its own HUD line, Flywheel sample and
event-list check. The HUD line lands in the hud bucket; the once-a-second Flywheel sample and event-list
check land in that frame's other render time. Timing is CPU-side only;
```

- [ ] **Step 3: README, "Report files" and "Building"**

Replace `Schema 4 (this version) adds` with `Schema 4 added`, and insert before that paragraph:

```markdown
Schema 5 (this version) adds `tasksUs`, `capUs` and `namedUs` (`render`, `tick` and `tasks`, zeros left out)
to every frame; `avgTasksMs`, `avgCapMs` and `avgNamedMs` to `stats`; a `hud` bucket; `environment.eventTiming`;
and a `mods` array, one ranked row per mod followed by an `entries` array of its event, layer and packet rows.
As on every ranked row, a mod's `members` is a count and its `callsPerFrame` counts only render-side calls;
`calls` plus `tickCalls` is its total.
`swapUs` no longer includes tasks or the frame cap, and tick and render now include every mod's handlers of
the client-tick and frame events, so a reader that derived swap from the other fields must also subtract
`tasksUs` and `capUs`, or read `swapUs`.

```

After the sentence that ends "are checked with `-Dmixin.debug.export=true`.", add:

```markdown
Add `-Drenderable.autostart=<seconds>` to the client's JVM arguments (for example through
`JAVA_TOOL_OPTIONS`) to run one session of that length ten seconds after joining a world, with no key
press. This is how a headless dev client produces a report to check; it is a development aid, not a
setting.
```

- [ ] **Step 4: Base spec §3.2**

In the frame record table, replace these rows:

```markdown
| `index`, `startNanos` | frame counter, `System.nanoTime()` at frame start |
| `totalNanos` | start of this frame to start of the next |
| `tickNanos` | sum of client ticks that ran inside this frame (`ClientTickEvent.Pre` to `Post`) |
| `renderNanos` | `RenderFrameEvent.Pre` to `RenderFrameEvent.Post` |
| buckets | `BLOCK_ENTITY`, `ENTITY`, `PARTICLE_RENDER`, `PARTICLE_TICK`, `FLYWHEEL`, `CHUNK_UPLOAD`, `LIGHT`, `BE_TICK`, `ENTITY_TICK`, each a nanosecond sum for this frame |
```

with:

```markdown
| `index`, `startNanos` | frame counter, `System.nanoTime()` at frame start, just before NeoForge posts `RenderFrameEvent.Pre` |
| `totalNanos` | start of this frame to start of the next |
| `tickNanos` | sum of client ticks that ran inside this frame, each from just before `ClientTickEvent.Pre` is posted to just after `ClientTickEvent.Post` |
| `renderNanos` | frame start to just after `RenderFrameEvent.Post` is posted |
| `tasksNanos` | time in `Minecraft.runAllTasks`: packets and other queued main-thread work (phase 5) |
| `capNanos` | time in `RenderSystem.limitDisplayFPS`: the wait for the fps limit (phase 5) |
| buckets | `BLOCK_ENTITY`, `ENTITY`, `PARTICLE_RENDER`, `FLYWHEEL`, `CHUNK_UPLOAD`, `LIGHT`, `HUD`, `PARTICLE_TICK`, `BE_TICK`, `ENTITY_TICK`, each a nanosecond sum for this frame |
| `namedNanos` | per window (render, tick, tasks), the time of attribution-only calls (§3.4) that no bucket-owning call encloses, less the bucket time inside them (phase 5) |
```

In the derived values, replace:

```markdown
- `otherRenderNanos = renderNanos - (BLOCK_ENTITY + ENTITY + PARTICLE_RENDER + FLYWHEEL + CHUNK_UPLOAD + LIGHT)`
```

with:

```markdown
- `otherRenderNanos = renderNanos - (BLOCK_ENTITY + ENTITY + PARTICLE_RENDER + FLYWHEEL + CHUNK_UPLOAD + LIGHT + HUD)`
```

and replace:

```markdown
- `swapNanos = totalNanos - tickNanos - renderNanos` (window swap, vsync wait, input polling, everything outside tick and render)
```

with:

```markdown
- `swapNanos = totalNanos - tickNanos - renderNanos - tasksNanos - capNanos` (the buffer swap and any vsync wait, the blit, the sound update, mouse turning and the yield)
```

In the paragraph after them, replace:

```markdown
buckets are only incremented by the outermost call, tracked with a depth
counter.
```

with:

```markdown
buckets are only incremented by the outermost call that owns a bucket, tracked with a depth
counter. Attribution-only calls (§3.4) own none, so a block entity drawn inside an event handler is still
block entity time.
```

- [ ] **Step 5: Base spec §3.4 and §3.5**

In §3.4's key table, after the `SectionKey` row, add:

```markdown
| `ModKey` | kind (event, layer, packet), owning mod id, event class or id | event handlers, HUD layers, packets (phase 5); attribution-only |
```

After the line "Flywheel visual counts are averaged per class in `FlywheelStats`, not keyed accumulators.",
add:

```markdown

A mod's event handlers, HUD layers and packets are **attribution-only** (phase 5, spec addendum §P5.A6):
their keys (`ModKey`, category `MOD`, type id the mod's id) are timed, ranked, peaked and GC-flagged like any
other, but they own no bucket, and vertices emitted inside one land on the innermost call that does own a
bucket. The time an attribution-only call accounts for outside any bucket is recorded per frame window as
`namedNanos`.
```

In §3.5, after the first bullet (it ends `That is the entire
  idle cost.`), add:

```markdown
- Event timing (§4) has no idle cost at all: its proxies exist only while a session runs.
```

and replace the last bullet:

```markdown
- The mod's own in-frame work — the sampling HUD and the once-a-second Flywheel visual sample — is timed
  rather than estimated and reported separately; it is part of other render time, not of any bucket.
```

with:

```markdown
- The mod's own in-frame work — the sampling HUD line, the once-a-second Flywheel visual sample and the
  once-a-second event-list check — is timed rather than estimated and reported separately. The HUD line is
  inside the `HUD` bucket and the rest is in other render.
```

- [ ] **Step 6: Base spec §4**

In §4's introduction, after the sentence that ends `never leaves a key on the
stack.`, add:

```markdown

Mod event handlers are the exception: they are timed without a mixin, because NeoForge's event bus loads in
the boot module layer, where no mixin reaches. While a session runs, every listener list of the game bus has
its cached array swapped for timing proxies, and back when the session ends (§4.6, spec addendum §P5.A3).
```

In §4.1's table, replace:

```markdown
| `RenderFrameEvent.Pre` / `Post` | NeoForge events | frame boundary, `renderNanos`, per-frame reset and top-N scan |
| `ClientTickEvent.Pre` / `Post` | NeoForge events | `tickNanos` |
```

with:

```markdown
| calls to `ClientHooks.fireRenderFramePre` / `fireRenderFramePost` inside `Minecraft#runTick(boolean)` | `@Inject` just before / just after (listeners of the two events until phase 5) | frame boundary, `renderNanos`, per-frame reset and top-N scan |
| calls to `ClientHooks.fireClientTickPre` / `fireClientTickPost` inside `Minecraft#tick()` | `@Inject` just before / just after (listeners until phase 5) | `tickNanos` |
```

In §4.2, replace the whole paragraph that starts `**Known gap: a riding entity's tick is attributed to its vehicle.**`
and ends `not do".` with:

```markdown
**A riding entity's tick.** `ClientLevel.tickEntities` routes only non-passengers through `guardEntityTick`;
a passenger is ticked from its vehicle through `ClientLevel#tickPassenger`, which calls `Entity#rideTick()`.
Until phase 5 that tick was folded into the vehicle's with nothing to say so: in a 2026-09-20 flight the
player rendered 4,983 times and ticked zero times. Phase 5 hooks the call (§4.6), so the rider has its own
tick row, and the vehicle's row still includes it, nested inclusively like every other call.
```

After §4.5, before `## 5. User interface`, add:

```markdown
### 4.6 Phase 5: attribution

| Target | Kind | Records |
| --- | --- | --- |
| call to `Minecraft#runAllTasks()` inside `Minecraft#runTick(boolean)` | `@WrapOperation` | `tasksNanos` |
| call to `RenderSystem#limitDisplayFPS(int)` inside `Minecraft#runTick(boolean)` | `@WrapOperation` | `capNanos` |
| `Gui#render(GuiGraphics, DeltaTracker)` | `@WrapMethod` | `HUD` bucket |
| call to `LayeredDraw$Layer#render` inside `GuiLayerManager#renderInner`, with the loop's `NamedLayer` | `@WrapOperation` with `@Local` | `ModKey` layer time, attribution-only |
| call to `Packet#handle(PacketListener)` inside `PacketUtils.lambda$ensureRunningOnSameThread$0` | `@WrapOperation` | `ModKey` packet time, attribution-only |
| call to `IPayloadHandler#handle` inside `MainThreadPayloadHandler.lambda$handle$0` | `@WrapOperation` | `ModKey` packet time for mod payloads, attribution-only |
| call to `Entity#rideTick()` inside `ClientLevel#tickPassenger(Entity, Entity)` | `@WrapOperation` | the rider's `EntityKey` tick time, nested in its vehicle's |
| the cached array of every `ListenerList` on `NeoForge.EVENT_BUS` | swapped for `TimedListener` proxies while a session runs; not a mixin | `ModKey` event time per owning mod and event class, attribution-only |

The spec addendum's §P5.A0 records the bytecode offsets these were verified at.
```

- [ ] **Step 7: Base spec §5.3, §6.1, §6.2 and §11**

In §5.3's tab table, after the `Hitches` row, add:

```markdown
| Mods | one row per mod, expandable to its event types, HUD layers and packets | share %, µs/frame, peak frame ms, gc f, calls/frame |
```

and replace `The Block Entities, Entities, Particles, Blocks and Sections lists sort by any column` with
`The Mods, Block Entities, Entities, Particles, Blocks and Sections lists sort by any column`.

In §6.1, replace `average bucket split per frame including GC and swap` with `average split per frame: tick, the
buckets (the HUD among them), other render, tasks, the frame cap and swap, with the part of tick, other render
and tasks that the Mods section names in brackets`. After item 4 (**Hitches**), insert:

```markdown
5. **Mods** (top 15, each with its top 3 event types, HUD layers or packets), under a note that counting is
   inclusive and that a library carries the handlers it runs for other mods.
```

and renumber the items after it, from 6 to 10.

In §6.2's example, change `"schema": 4,` to `"schema": 5,` and `"version": "0.4.0"` to `"version": "0.5.0"`,
replace the `frames` line pair:

```
  "frames": [ { "i": 0, "totalUs": ..., "tickUs": ..., "renderUs": ..., "bucketsUs": { ... },
                "gcCount": 0, "gcMs": 0, "top": [ { "key": "...", "us": ..., "gc": false } ] }, ... ],
```

with:

```
  "frames": [ { "i": 0, "totalUs": ..., "tickUs": ..., "renderUs": ..., "tasksUs": ..., "capUs": ...,
                "namedUs": { "render": ..., "tick": ..., "tasks": ... }, "bucketsUs": { ... },
                "gcCount": 0, "gcMs": 0, "top": [ { "key": "...", "us": ..., "gc": false } ] }, ... ],
```

and after the `"hitches": [ frame indices ],` line add:

```
  "mods": [ { ...a ranked row for the mod..., "entries": [ ...ranked rows... ] } ],
```

In the sentence `Keys serialise as strings of the form`, add `` `event@mod:class`, `layer@mod:id`, `packet@mod:id` ``
to the list of forms.

In §11's table, after the `4a` row, add:

```markdown
| 5a | frame components (tasks, frame cap, HUD), frame and tick boundaries at the `Minecraft` call sites, mod event handlers through per-session listener proxies, HUD layers, packets and payloads, rider ticks, attribution-only calls and named time, the Mods tab and report section, JSON schema 5 | spec addendum §P5.A's checks, in the in-pack acceptance at the end of phase 5 |
```

and after the paragraph about phase 4, add:

```markdown

Phase 5 is four plans, 5a (attribution), 5b (recommendations), 5d (polish) and 5c (readiness), run in that
order and released together as `v0.5.0`; see `docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md`.
```

- [ ] **Step 8: The phase 5 spec's corrections**

In `2026-09-26-renderable-phase-5-design.md`:

1. In §P5.A9, replace:

```markdown
- **Session.** `session` gains `avgTasksMs`, `avgCapMs` and `avgNamedMs`, and `avgBucketsMs` gains `HUD`.
```

   with:

```markdown
- **Stats.** `stats`, where every average already lives, gains `avgTasksMs`, `avgCapMs` and `avgNamedMs`
  (`render`, `tick`, `tasks`), and `avgBucketsMs` gains `hud`. `session` is unchanged.
```

   In the same section's **Frames** bullet, change "`bucketsUs` gains `HUD`" to "`bucketsUs` gains `hud`". In
   its **Environment** bullet, change "`environment` gains P5.B3's keys." to "`environment` gains
   `eventTiming` (P5.A3) and P5.B3's keys."
2. In §P5.A7, replace:

```markdown
  - The default sort is `us/f`, descending, and the columns sort like the other data tabs (P4.C7).
```

   with:

```markdown
  - The tab opens sorted by total time, largest first. `share` and `us/f` sort the same way, and the arrow
    sits on `share`, the first of them, as on the other ranked tabs. The columns sort like the other data
    tabs (P4.C7).
```

3. In §P5.2, after the line "The JFR run (P5.C1) is recorded separately in `docs/acceptance/phase-5-idle.md`.",
   add:

```markdown

A dev-only system property, `-Drenderable.autostart=<seconds>`, runs one session after joining a world, so a
headless dev client can produce the reports that the dev-client checks above read (plan 5a Task 9).
```

4. In §P5.A8, replace:

```markdown
`Probe.beginEntityTick(rider)`. The rider's row gets its own tick time, and the vehicle's row still
```

   with:

```markdown
`Probe.beginRiderTick(rider)`, which times an ordinary entity tick but records its own hook, `RIDER_TICK`,
so the environment shows whether it fired. The rider's row gets its own tick time, and the vehicle's row still
```

5. In §P5.A9, replace "`mods` (P5.A7)" with "`mods` (P5.A7), each mod a ranked row followed by an `entries`
   array of its members".

- [ ] **Step 9: CLAUDE.md**

Under `## Rules that keep the design intact`, after the bullet that ends `Vanilla mixins are required.`, add:

```markdown
- Mod event handlers are timed without a mixin: `hooks.events` swaps the game bus's listener arrays
  for proxies only while a session runs (spec addendum §P5.A3). The bus loads in the boot module
  layer, where no mixin reaches, so do not try to hook it.
```

- [ ] **Step 10: Check and stage**

Run: `git diff --stat` and read each changed paragraph once more against the code. The README's numbers and
names must match what Tasks 1 to 9 built: `[mods x]`, the frame cap shown only when it has time, and
`Mods (top 15, each with its top 3)`.

```bash
git add README.md CLAUDE.md docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md
```

Report for the commit: `docs: README and specs for phase 5a`.

---

### Task 11: Prove it in the dev client

**Files:** none in the repository. The dev client writes under `run/`, which git ignores.

This task needs no person, thanks to Task 9. It proves that every new mixin applies and that a real session
fills the new fields. What only the pack and a player can show, such as riding, flying into new terrain and
reading the Mods tab by eye, is left to the in-pack acceptance at the end of phase 5.

**The user's real Gears & Frontiers client may be running on this machine.** Never stop a Java process that is
not the dev client this task started. With `-PpackMods`, Sodium rewrites the game's command line, so the dev
client cannot be matched by its own command line: stop it through the Gradle wrapper that launched it
(Step 4).

- [ ] **Step 1: Build and test**

Run: `./gradlew build --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **160** tests.

- [ ] **Step 2: Prepare the run**

1. List `run/saves`. Use the world named `New World` if it exists, or else the first world listed. If there
   is none, stop and report: this task needs an existing world.
2. In `run/options.txt`, note the current `maxFps:` and `enableVsync:` values, then set `maxFps:260` and
   `enableVsync:false`.
3. Delete `run/.mixin.out` if it exists, and note the newest file in `run/renderable/`, if any, so the new
   report can be told apart.

- [ ] **Step 3: Launch, uncapped**

Run it in the background:

```bash
JAVA_TOOL_OPTIONS="-Dmixin.debug.export=true -Drenderable.autostart=10" ./gradlew runClient -PquickPlay="New World" -PpackMods --no-daemon
```

(with the world name from Step 2). Wait for a new `run/renderable/renderable-*.json` to appear, checking
every 15 seconds for up to 8 minutes. It arrives about 10 s after the world loads, plus the 10 s session. If
it never arrives, read `run/logs/latest.log`, then stop the client (Step 4) and report what the log shows.

- [ ] **Step 4: Stop the client**

From PowerShell, find the Gradle wrapper process that launched it:

```powershell
Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
  Where-Object { $_.CommandLine -like '*-jar H:/repos/Renderable*' -and $_.CommandLine -like '*runClient*' } |
  Select-Object ProcessId, ParentProcessId, CommandLine
```

Exactly one process should match. If none does, or more than one, stop and report without killing anything.
Then list its descendants by `ParentProcessId` (wrapper → Gradle JVM → game) to confirm the chain, and kill that
tree only:

```powershell
taskkill /PID <wrapper pid> /T /F
```

A client that MCUpdater launched is never a descendant of this wrapper.

- [ ] **Step 5: Check that every new mixin applied**

```bash
for c in net.minecraft.client.Minecraft net.minecraft.client.gui.Gui net.neoforged.neoforge.client.gui.GuiLayerManager net.minecraft.network.protocol.PacketUtils net.neoforged.neoforge.network.handling.MainThreadPayloadHandler net.minecraft.client.multiplayer.ClientLevel; do echo "== $c"; javap -p -cp run/.mixin.out/class "$c" | grep 'renderable\$'; done
```

Expected: at least one `renderable$` handler for each class. Do not count them: MixinExtras may add bridge
methods, so the number per class can differ from the number of injectors. A class with none means its mixin
did not apply; find the reason in `run/logs/latest.log`, which Mixin logs, before going on.

- [ ] **Step 6: Check the report and the log**

```bash
python - <<'EOF'
import glob, json, os
f = max(glob.glob("run/renderable/renderable-*.json"), key=os.path.getmtime)
r = json.load(open(f, encoding="utf-8"))
st, env, frames = r["stats"], r["environment"], r["session"]["frames"]
print(f, "schema", r["schema"], "frames", frames)
print("avgTasksMs", st["avgTasksMs"], "avgCapMs", st["avgCapMs"], "avgSwapMs", st["avgSwapMs"])
print("hud", st["avgBucketsMs"].get("hud"), "named", st["avgNamedMs"])
print("eventTiming", env.get("eventTiming"))
print("hooks", env["hooksApplied"])
for m in r["mods"][:12]:
    print("  %-24s %8.1f us/f  %d members" % (m["key"], (m["renderUs"] + m["tickUs"]) / frames, len(m["entries"])))
EOF
grep -n "event timing restored\|cannot time event handlers" run/logs/latest.log
grep -in "renderable" run/logs/latest.log | grep -i "mixin\|error\|exception" | head
```

Expected:

- `schema 5`;
- `avgTasksMs` above zero, and `avgCapMs` zero (the limit is 260);
- `hud` above zero;
- `eventTiming` is `on`;
- the hooks include `TASKS`, `HUD`, `HUD_LAYERS`, `PACKETS` and `EVENTS`;
- the mods list is not empty, has a `minecraft` row (vanilla HUD layers and packets), and has no
  `renderable` row;
- the log line reads `... restored N listener lists; 0 still hold a timing proxy`, with N above zero;
- no Renderable mixin errors.

Record the output.

- [ ] **Step 7: Launch again, capped at 30 fps**

Set `maxFps:30` in `run/options.txt`, then repeat Steps 3, 4 and 6 (not 5). Expected:

- `avgCapMs` is well above zero: at 30 fps a frame is about 33 ms, and most of it is the wait;
- the hooks include `FRAME_CAP`;
- `avgSwapMs` has fallen accordingly.

- [ ] **Step 8: Put the run directory back**

Restore `maxFps:` and `enableVsync:` in `run/options.txt` to the values noted in Step 2, and delete
`run/.mixin.out`. Leave the two reports in `run/renderable/`.

- [ ] **Step 9: Report**

Report to the orchestrator: the `javap` output from Step 5, and the Python and `grep` output from both runs.
Include anything unexpected, especially a mixin that did not apply, `eventTiming` other than `on`, or a
non-zero count of lists still holding a proxy. Nothing is staged in this task.

---

## After this plan

Leave `phase-5a-attribution` unmerged. Plan 5b branches from it, as phase 4's plans stacked (spec addendum
§P5.2), and everything merges to `main` after the in-pack acceptance at the end of phase 5.
