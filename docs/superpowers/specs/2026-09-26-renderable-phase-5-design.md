# Renderable phase 5: attribution, advice, readiness, polish

**Date:** 2026-09-26
**Status:** written from the design approved on 2026-09-26; awaiting the user's review of the seven changes
listed at the end of P5.1. Plans not yet written.
**Extends:** `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md` (the base spec) and
`docs/superpowers/specs/2026-09-20-renderable-phase-4-design.md` (the phase 4 addendum). Sections here are
numbered `P5.x`; a number with no prefix refers to the base spec, and `P4.x` to the phase 4 addendum.
**Baseline:** `main` at `f9a9acd`, tag `v0.4.1` (`v0.4.0` is `88a3fed`), 132 JUnit tests.
**Evidence:**

- `docs/acceptance/phase-4b-4c.md`: two sessions in the Gears & Frontiers instance on 2026-09-26.
- P4.3's deferred list.
- javap against the pinned NeoForge 21.1.220 merged jar and `net.neoforged:bus` 8.0.5 (P5.A0).
- The pack instance's launch profile, `options.txt` and crash reports.
- Collector-bean checks on JDK 21.0.11 (P5.B4).

## P5.0 Why there is a phase 5

Phases 1 to 4 made Renderable's numbers trustworthy for the things it times. The 4b/4c acceptance sessions
show what it still cannot say, and who it is not yet written for:

1. **Time it cannot name.** At the base, hitch #1 is a 382.4 ms frame whose client tick took 376.7 ms, of
   which 2.7 ms is attributed. `other render` is 4.65 ms of a 10.4 ms average frame. `swap/wait` quietly
   holds packet handling and the frame-cap sleep as well as the buffer swap. Most of the missing time is
   other mods' event handlers, HUD layers and packets, and Renderable cannot name any of them.
2. **A reader who is not a developer.** The report answers "what costs what" for someone who already
   knows what a block entity renderer is. A player who presses the key gets tables, not an answer.
3. **Readiness.** Idle cost has never been measured (base §9 check 5). The overhead estimate leaves out
   the hook side of every call (P4.3). Reports carry other players' names. The default key collides with
   another mod in the pack.
4. **Deferred polish.** The items P4.3 set aside, and the observations in the 4b/4c log.

Phase 5 is four plans: **5a** attribution, **5b** recommendations, **5c** readiness, and **5d** polish.

## P5.1 Decisions the user made

| Decision | Choice | Rejected |
| --- | --- | --- |
| Goal | All three: attribute the unnamed time, add recommendations, get ready for players | Any one alone |
| Kinds of advice | Settings the player can change; machines and builds in view; pointers for the pack author; memory allocation and launch arguments | — |
| Knowledge the rules may use | Generic rules only | Rules that name this pack's mods or configs |
| Rollout | Ready, not rolled out: phase 5 makes the mod fit for players, while adding it to the pack, creating the GitHub remote and cutting the first release stay the user's steps | Rolling out within phase 5 |
| Event attribution | Per mod, broken down by event type | Per event type only |
| Riders (P4.3) | Time the rider's own tick, nested inside the vehicle's | Subtracting the rider from the vehicle |

### Changes since the design was approved

Writing this document meant verifying every hook target, and a review of the draft against the pinned
jars changed the event design again. Seven points differ from what was approved. Each is argued in its
own section; this list exists so they can be reviewed together.

1. **Event handlers are timed by proxies swapped in for the length of a session, not by a hook in the
   bus** (P5.A3).
   - *Why the approved hook fails.* The approved design timed listeners inside `EventBus`'s loop, with a
     runtime check first and per-event totals as the fallback. The launch profiles, and the stack frames
     in the pack's crash reports, show that the bus loads in the boot module layer. Mixin never sees that
     layer, so the hook cannot apply in dev or in the pack.
   - *The replacement.* While a session runs, each event's cached listener array is swapped for an array
     of timing proxies, and swapped back at the end.
   - *What it keeps and gains.* The per-mod split survives. Idle cost is zero, because nothing is
     installed between sessions. Every post to the game bus is covered, whoever makes it.
2. **The idle-cost fallback is gone.** The approved plan put the bus hook behind a switch in case P5.C1's
   measurement failed. The proxies cost nothing between sessions, so there is nothing to switch off.
3. **Frame and tick boundaries move out of the event bus** (P5.A2). This is how "handle the frame-boundary
   events" is met.
4. **Attribution-only calls leave the buckets and vertex counts alone** (P5.A6). Without this rule, timing
   a listener would move time out of the block entity and entity buckets, and vertices off their rows.
5. **Recommendations rank stutter first** (P5.B1). Averaging a stall over the session buries it: the
   382 ms stall above is 0.13 ms per frame, below every frame-rate finding, yet it is the thing a player
   notices.
6. **The default key becomes F6** (P5.C4). In the pack, Lighty's `key.lighty.enable` is also on F7.
7. **5d runs after 5b, not at any point** (P5.2). Two of its items build on changes that 5a and 5b make.

---

# P5.A Group A: attribution

## P5.A0 Verified hook targets

All against `neoforge-21.1.220-merged.jar` and `bus-8.0.5.jar`, the versions the build pins. Offsets are
bytecode offsets within the named method, recorded so a later NeoForge update can be checked quickly.

| # | Method | Instruction | Used for |
| --- | --- | --- | --- |
| 1 | `Minecraft.runTick(Z)V` | 398 `INVOKESTATIC ClientHooks.fireRenderFramePre(DeltaTracker)V` | frame start, just before (P5.A2) |
| 2 | `Minecraft.runTick(Z)V` | 438 `INVOKESTATIC ClientHooks.fireRenderFramePost(DeltaTracker)V` | render end, just after (P5.A2) |
| 3 | `Minecraft.tick()V` | 10 `INVOKESTATIC ClientHooks.fireClientTickPre()V` | tick start, just before (P5.A2) |
| 4 | `Minecraft.tick()V` | 866 `INVOKESTATIC ClientHooks.fireClientTickPost()V` | tick end, just after (P5.A2) |
| 5 | `Minecraft.runTick(Z)V` | 120 `INVOKEVIRTUAL Minecraft.runAllTasks()V` | tasks (P5.A1) |
| 6 | `Minecraft.runTick(Z)V` | 603 `INVOKESTATIC RenderSystem.limitDisplayFPS(I)V`, reached only when `getFramerateLimit() < 260` | frame cap (P5.A1) |
| 7 | `Gui.render(GuiGraphics, DeltaTracker)V` | whole method; `GameRenderer.render` calls it once, at 606 | HUD bucket (P5.A1) |
| 8 | `GuiLayerManager.renderInner(GuiGraphics, DeltaTracker)V` | 80 `INVOKEINTERFACE LayeredDraw$Layer.render(GuiGraphics, DeltaTracker)V`; local 4 is the loop's `NamedLayer` | HUD layers (P5.A4) |
| 9 | `PacketUtils.lambda$ensureRunningOnSameThread$0(PacketListener, Packet)V` | 12 `INVOKEINTERFACE Packet.handle(PacketListener)V` | vanilla packets (P5.A5) |
| 10 | `MainThreadPayloadHandler.lambda$handle$0(CustomPacketPayload, IPayloadContext)V` | 6 `INVOKEINTERFACE IPayloadHandler.handle(CustomPacketPayload, IPayloadContext)V` | mod payloads (P5.A5) |
| 11 | `ClientLevel.tickPassenger(Entity, Entity)V` | 55 `INVOKEVIRTUAL Entity.rideTick()V` | riders (P5.A8) |

The event bus, from `bus-8.0.5.jar`:

- **`post` itself.**
  - `EventBus.post(T)` returns the event if `shutdown` is set. Otherwise it calls `doPostChecks(event)`
    and returns `post(event, getListenerList(event.getClass()).getListeners())`.
  - `post(T, EventListener[])` is a plain loop, `listeners[i].invoke(event)`. On any `Throwable` it calls
    `exceptionHandler.handleException(this, event, listeners, i, t)`, then rethrows; the default handler
    logs every listener in the array by its `toString()`.
  - The loop has no cancellation check. The bus's own listener classes handle `receiveCanceled`
    themselves.
- **One list per event class.** There is one `ListenerList` per concrete event class. `getListenerList`
  creates it on first use, and `post` always uses the list for the posted event's exact class.
- **The cached array.**
  - `ListenerList.getListeners()` is `if (rebuild) buildCache(); return listeners.get();`, where
    `listeners` is a private `AtomicReference<EventListener[]>`.
  - `buildCache()` rebuilds the array from the per-priority lists and `set`s it.
  - `register` and `unregister` change the per-priority lists and call `forceRebuild()`, which sets
    `rebuild` on the list and its children.
  - `buildCache()` may swap an `IWrapperListener` for its `getWithoutCheck()` (`unwrapListeners`), so the
    cached array can hold a different object from the one that was registered.
- **Finding the lists.** `EventBus.listenerLists` is a private `LockHelper<Class<?>, ListenerList>`. Its
  package-private `getReadMap()` returns a read-only view of every list.
- **Finding a listener's owner.**
  - `EventBus.listeners` is a private `ConcurrentHashMap<Object, List<EventListener>>` from each
    registration target to the listeners it created. Its values are `Collections.synchronizedList`s.
  - A target is one of four things: a `Class` for a class of static subscribers, a `Method` for a single
    static method, an object for its instance subscribers, or the `Consumer` given to `addListener`.
  - `EventListenerFactory` defines each `@SubscribeEvent` listener as a hidden class in the bus's own
    lookup. A listener's class therefore says nothing about which mod registered it; only the `listeners`
    map does.
- **Open to reflection.** The bus's `module-info` reads `open module net.neoforged.bus`, so a mod can reach
  all of the above by reflection.
- **In the boot layer.** Stack frames in the pack's crash reports place the bus there:
  `at MC-BOOTSTRAP/net.neoforged.bus/net.neoforged.bus.EventBus.post(EventBus.java:360)`. Game classes
  read, for example, `at TRANSFORMER/minecraft@1.21.1/net.minecraft.client.Minecraft.runTick(Minecraft.java:1224)`.

For the record, a scan finds 255 `IEventBus.post(Event)` call sites in 20 classes. It covered the classes
under `net/minecraft/client/` and `net/neoforged/neoforge/client/`, plus `EventHooks` and `CommonHooks`:
`EventHooks` has 109 sites, `ClientHooks` 60, `CommonHooks` 52, `Minecraft` and `Screen` 5 each and
`GuiLayerManager` 4, with 20 more across 14 classes. A further 54 sites are in 33 other classes. None of
them uses the per-phase `post(EventPriority, Event)`. P5.A3 explains why the design does not wrap them.

## P5.A1 Three new frame components: tasks, frame cap and HUD

**Observed.** `FrameRecord.swapNanos()` is `max(0, total − tick − render)`. javap of `Minecraft.runTick(Z)`
gives the frame loop's order:

1. `runAllTasks()`: packets and other queued work.
2. The tick loop.
3. The sound update, then mouse turning.
4. `fireRenderFramePre`, `GameRenderer.render`, `fireRenderFramePost`.
5. The blit, then `Window.updateDisplay()`.
6. `RenderSystem.limitDisplayFPS(limit)`, called only when the limit is below 260.
7. `Thread.yield()`.

"swap/wait" is therefore packet handling and the frame-cap sleep as well as the buffer swap. A packet
storm and a vsync wait look the same in a hitch split.

**Design.** Two new frame components and one new render bucket:

- **`tasksNanos`** brackets the `runAllTasks()` call. It covers the main-thread handling of every vanilla
  packet and every main-thread mod payload, plus whatever the game or a mod queued with
  `Minecraft.execute`.
- **`capNanos`** brackets the `limitDisplayFPS` call. That method loops on `glfwWaitEventsTimeout`, so
  window events that arrive during the wait, such as key and mouse callbacks, run inside it and count as
  cap. They are negligible except when a keypress opens a screen.
- **`HUD`**, a render bucket, wraps `Gui.render`. It holds every HUD layer, vanilla and modded, and the
  handlers of `RenderGuiEvent` and `RenderGuiLayerEvent`. Screens (`ClientHooks.drawScreen`) stay in other
  render. Renderable's own sampling line is a HUD layer, so that part of the measured self time now sits
  inside HUD rather than other render. A bucketed call made inside the HUD, such as an entity model a HUD
  mod draws, now counts in HUD (P5.A6).
- **`swapNanos` becomes `max(0, total − tick − render − tasks − cap)`**. It keeps the buffer swap and any
  vsync wait, the blit, the sound update, mouse turning and the yield.

A frame still runs from one frame start to the next (§3.2). The tasks and ticks at the top of the next
`runTick` therefore count in the frame they follow, which is the convention tick time already uses.

**Alternatives rejected.**

- *Leave tasks and cap in swap and explain them in a note.* A hitch split still could not separate a packet
  storm from a vsync wait.
- *Make the cap a render bucket.* It is not rendering, and it would inflate the render share of every
  capped session.

**User-visible change.** The time split and each hitch split gain `tasks`, `frame cap` and `hud`, and
`swap/wait` shrinks by the same amount. Like the render buckets, each is printed only when it has time
(plan 5a, deviation 3); the Frames tab lists tasks and the frame cap always. JSON schema 5 (P5.A9).

**Acceptance check (A1).** In the dev client:

- With the fps limit at 30, `frame cap` is most of each frame and `swap/wait` is near zero.
- Unlimited (260) with vsync off, `frame cap` is zero.
- Flying into new terrain puts time in `tasks`. A session cannot span joining a world, because leaving
  a level discards it.
- `hud` falls sharply when the HUD is hidden with F1. The layers' own conditions skip their drawing, but
  their events are still posted.

## P5.A2 Frame and tick boundaries move out of the event bus

**Observed.** Renderable opens and closes frames and ticks from its own `@SubscribeEvent` listeners
(`FrameEvents.onRenderPre`, `onRenderPost`, `onTickPre`, `onTickPost`), all at the default priority.
Other mods' listeners on the same four events run before or after Renderable's in registration order, so
some of them fall outside the window they belong to. A mod's `ClientTickEvent.Post` listener registered
before Renderable's counts in the tick; one registered after it counts in swap. Once P5.A3 names those
listeners by mod, the windows have to contain all of them.

**Design.** The boundaries become `@Inject`s in `Minecraft`, at P5.A0 rows 1 to 4:

- **Frame start:** just before `fireRenderFramePre` is called.
- **Render end:** just after `fireRenderFramePost` returns.
- **Tick start:** just before `fireClientTickPre` is called.
- **Tick end:** just after `fireClientTickPost` returns.

Every listener of those events, at any priority, then falls inside render or tick. GC sampling and the
check that ends a session move with the frame start, unchanged. `FrameEvents` keeps its logout and
level-unload listeners.

**Alternatives rejected.** *Register Renderable's listeners at `HIGHEST` for Pre and `LOWEST` for Post.* A
mod registered earlier at the same priority would still fall outside the window.

**User-visible change.** Render and tick gain whatever other mods' listeners on these events cost, and
swap loses it.

**Acceptance check (A2).** The Mods tab lists handlers of `RenderFrameEvent` and `ClientTickEvent`, and a
session in the dev client shows their time in render and tick, not swap. `SessionTest` is unchanged: the
calls into `Session` are the same, and only their call site moves.

## P5.A3 Mod event handlers

**Observed.**

- **The approved hook.** The approved design timed each listener inside
  `EventBus.post(T, EventListener[])`, with a runtime check first and per-event totals as the fallback.
- **The bus cannot be transformed.** The bus jar is an ordinary library on the launcher's class path:
  - In the pack, `versions/neoforge-21.1.220/neoforge-21.1.220.json` launches
    `cpw.mods.bootstraplauncher.BootstrapLauncher` with `-DignoreList=client-extra,${version_name}.jar`,
    and `net.neoforged:bus:8.0.5` is in its libraries.
  - In the dev client, `build/moddev/clientLegacyClasspath.txt` lists `bus-8.0.5.jar`, and the ignore list
    is `mixinextras-neoforge-0.5.0.jar,client-extra,neoforge-`.
  - BootstrapLauncher loads every class-path jar that is not on the ignore list into the boot module
    layer, and Mixin transforms only classes that ModLauncher's `TRANSFORMER` loader defines. The pack's
    crash reports confirm where the bus lives (P5.A0), so the runtime check is not needed.
- **The bus is open to reflection,** and the array it posts to is held in one field per event class
  (P5.A0).

**Design.** For the length of a session, swap each event's cached listener array for timing proxies.

- **At session start,** after the session attaches, Renderable goes through every `ListenerList` in
  `NeoForge.EVENT_BUS`:
  1. It reads the list's current array `A` through `getListeners()`, which rebuilds the array if due.
  2. It builds an array `P` of the same length and order, wrapping each listener in a `TimedListener`.
  3. It calls `compareAndSet(A, P)` on the list's `listeners` reference.

  A failed compare-and-set means the list was rebuilt meanwhile, and it is left for the next check.
- **A `TimedListener`** holds the listener it wraps and the accumulator for (owning mod, the list's event
  class), resolved when the proxy is built.
  - Its `invoke(event)` calls the wrapped listener directly when it is off the render thread or once the
    session has detached. Otherwise it begins its accumulator, calls the wrapped listener, and ends the
    accumulator in a `finally`.
  - Its `toString()` returns the wrapped listener's, so the bus's exception log reads as it does without
    Renderable.
  - It does not implement `IWrapperListener`.
  - Nothing is looked up on the call path, because the accumulator is already in the proxy.
  - So every (owning mod, event class) pair with a listener has an accumulator from the start of the session,
    whether or not its event is ever posted on the render thread. The session's result leaves out any
    accumulator that completed no call, so a handler that never ran has no row anywhere (plan 5a, from review).
- **Once a second,** at frame start, the lists are checked again. Any list whose array is not the one
  Renderable installed is wrapped afresh. That happens when the list was rebuilt because a listener was
  registered or unregistered, or when the first post of a new event class created it. The check compares
  one reference per list. The check and any re-wrapping count as Renderable's self time.
- **At session end,** and on discard, each list's reference is compare-and-set from Renderable's array
  back to the one it replaced. A list rebuilt in the meantime already holds a fresh original array and is
  left alone.
- **Owner of a listener.** When the proxies are built, the bus's `listeners` map is inverted into an
  `IdentityHashMap<EventListener, owner>`.
  - The map's values are synchronized lists, so they are copied with `toArray()`.
  - A target's class is the target itself when it is a `Class`, the declaring class when it is a `Method`,
    and `getClass()` otherwise.
  - That class is mapped to the mod whose file holds it: its JPMS module name is matched against
    `ModList.get().getModFiles()`, using `IModFileInfo.moduleName()`, and the file's first mod id wins.
  - NeoForge's own listeners resolve to `neoforge`. A class in no mod file keeps its module name. A
    listener found under no target counts as `unknown`.
  - Both the registered listener and, for an `IWrapperListener`, its `getWithoutCheck()` are mapped,
    because the cached array may hold either.
- **Key.** Owning mod plus event class. The label is the event's simple name with its outer class:
  `RenderLevelStageEvent`, `ClientTickEvent.Post`, `RenderGuiLayerEvent.Pre`.
- **Renderable's own listeners** are not wrapped, and never appear.
- **Nesting.** Listener calls are inclusive, like every other timed call (§3.2). A listener that draws an
  entity includes that entity in its own time, and the entity's row includes it too. For buckets and
  vertex counts they are attribution-only (P5.A6).
- **Reaching the internals.** Four members are looked up once each, through
  `MethodHandles.privateLookupIn(<declaring class>, MethodHandles.lookup())`: `EventBus.listenerLists`,
  `LockHelper.getReadMap()`, `EventBus.listeners` and `ListenerList.listeners`. If any lookup fails, as it
  would with a different bus version, event timing is unavailable for the run. The Mods tab and the report
  say so, and why. Anything thrown while wrapping, re-checking or restoring the lists turns event timing off
  the same way, for the rest of the run.
- **Libraries that dispatch for other mods.** Architectury, Balm and Puzzles Lib are in the pack. Each
  registers NeoForge listeners on behalf of the mods built on it, so those mods' handler time is
  attributed to the library. The README says so.
- **Debug check.** `/renderable debug events` counts the lists that hold a Renderable array, which must be
  zero outside a session, and says whether event timing is on.

**What this covers.** Every `post(Event)` to `NeoForge.EVENT_BUS` made while the session runs, whoever
makes it: NeoForge, Minecraft's patched classes, common code, and mods posting their own events.

- Per-phase posts, `post(EventPriority, Event)`, read a different array and are not timed. No call site in
  the scan uses them.
- A listener registered mid-session is timed from the next once-a-second check.

**Idle cost.** None. Between sessions every list holds its original array, and no Renderable code runs
when an event is posted.

**Risk.**

- The design relies on private members of bus 8.0.5 behaving as P5.A0 records. The lookups catch a
  renamed member but not a changed behaviour, so after a NeoForge update P5.A0's bus facts need checking
  again.
- During a session, other threads' posts go through the proxies too. They pay a call and a thread check
  per listener.
- Another mod that reflects into `ListenerList` would see the proxies during a session.
- A crash mid-session leaves the arrays swapped, which does not matter, because the bus goes down with the
  game. A discarded session restores them like a finished one.
- While a session runs, a timed handler's stack trace, in a crash report too, has `TimedListener.invoke` one
  frame above the handler.
- The once-a-second check reads every list through `getListeners()`, so the render thread can build the cached
  array of a list that only another thread posts to. A listener registered on another thread at that moment
  can miss that rebuild until the list's next one, a race the bus already has between any two posting threads.
  A check that runs between the bus adding a listener to its list and recording who registered it attributes
  that listener to `unknown`.

**Alternatives rejected.**

- *A mixin into `EventBus`.* It cannot apply; see above.
- *A `@WrapOperation` on each of the 255 call sites that performs the post itself and times each
  listener.*
  - It adds a check to every post even when idle.
  - It misses the 54 sites outside the scanned packages, and every event a mod posts itself.
  - While sampling it would skip any other mod's `@Redirect` or `@WrapOperation` on the same call.
    MixinExtras chains those inside it, and the copy never calls `original`.
- *Wrap every registered listener permanently.* It costs a call per listener invocation even when idle.
  It also breaks `unregister`, which finds listeners by identity.
- *Replace `NeoForge.EVENT_BUS` with a delegating bus.* Every mod's registrations would pass through
  Renderable, so a bug there would break the pack, not just the profiler.
- *Per-event totals, the approved fallback.* Strictly less information.

**User-visible change.** Mod handlers appear in the Mods tab and report section (P5.A7).

**Acceptance check (A3).**

- *Unit,* against a real bus built with `BusBuilder.builder().build()`:
  - after the swap, a post invokes the same listeners in the same order, honours `receiveCanceled`, and
    passes a throwing listener to the exception handler with the same index;
  - each listener's time lands in its owner's accumulator, and Renderable's own listener is not wrapped;
  - the owner map resolves a `Class` target, a `Method` target, an instance target and an `addListener`
    lambda, including a listener the bus has unwrapped;
  - after restore, every list holds its original array. A list rebuilt during the session is left alone
    at restore, and the next check re-wraps it;
  - registering and then unregistering a listener mid-session leaves no proxy behind.
- *Dev client:* with `-PpackMods`, the Mods tab lists `neoforge` and the pack mods' handlers. After the
  session, `/renderable debug events` reports no list holding a Renderable array.

## P5.A4 HUD layers

**Observed.** `GuiLayerManager.renderInner` loops over its `NamedLayer(ResourceLocation name,
LayeredDraw.Layer layer)` list. For each layer it posts `RenderGuiLayerEvent.Pre`, and unless the event is
cancelled calls `layer().render(g, dt)` and posts `RenderGuiLayerEvent.Post`. Modded layers are registered
under the mod's namespace through `RegisterGuiLayersEvent`. `add(GuiLayerManager, BooleanSupplier)`
flattens a child manager into the list and keeps the names.

**Design.** A `@WrapOperation` on `LayeredDraw$Layer.render` in `renderInner` (P5.A0 row 8), capturing the
loop's `NamedLayer` with `@Local`. The key is the layer id, such as `xaerominimap:minimap`, and the mod is
its namespace. Vanilla layers are timed too and appear under `minecraft`. Renderable's own layers are left
out: `renderable:sampling` is self time, and the overlay's `renderable:labels` is cleared when a session
starts. The layers sit inside the HUD bucket.

Listeners of `RenderGuiLayerEvent` are timed by P5.A3 and belong to whichever mod registered them, which
need not be the layer's owner. The Mods tab shows both.

**User-visible change.** Layers appear under their mods in the Mods tab (P5.A7).

**Acceptance check (A4).** In the dev client with `-PpackMods`, vanilla layers appear under `minecraft`,
and a mod's layers sum to no more than the HUD bucket.

## P5.A5 Network: packets and payloads

**Observed.**

- **Vanilla packets.** A handler starts with `PacketUtils.ensureRunningOnSameThread`, which queues a lambda
  on the main thread's executor. That lambda, `lambda$ensureRunningOnSameThread$0`, calls
  `packet.handle(listener)` during `runAllTasks`.
- **Mod payloads.** NeoForge hands a modded payload to `NetworkRegistry.handleModdedPayload` on the network
  thread. A payload registered for the main thread, which is the default, is wrapped in
  `MainThreadPayloadHandler`. Its `handle` queues `lambda$handle$0(payload, context)` through
  `ReentrantBlockableEventLoop.submit`, and that lambda calls the mod's `IPayloadHandler.handle` during
  `runAllTasks`.

**Design.** Two `@WrapOperation`s (P5.A0 rows 9 and 10), both guarded to the render thread, because
`PacketUtils` also serves the integrated server:

- `Packet.handle` in the `PacketUtils` lambda, keyed by `packet.type().id()`, for example
  `minecraft:level_chunk_with_light`.
- `IPayloadHandler.handle` in the `MainThreadPayloadHandler` lambda, keyed by `payload.type().id()`. The
  mod is the id's namespace.

Both lie inside tasks. A bundle packet's time includes the packets inside it, which its handler runs
directly. Payload handlers that run on the network thread are not frame time and are not timed. Work that
a handler queues itself with `enqueueWork` lands in tasks unattributed.

**Alternatives rejected.** *Hook `Connection` on the network thread.* That times decoding, which is not
frame time.

**User-visible change.** Packet types appear under their mods in the Mods tab.

**Acceptance check (A5).** Flying into new terrain puts `minecraft:level_chunk_with_light` among the top
packets.
With `-PpackMods`, Create's payloads appear under `create` in a world with a running contraption.

## P5.A6 Attribution-only calls leave the buckets and vertex counts alone

**Observed.**

- A bucket is credited only by the outermost timed call, tracked with a depth counter (§3.2). If
  listeners, layers and packets were ordinary timed calls, a block entity drawn inside a
  `RenderLevelStageEvent` listener would no longer be outermost. Its time would leave the block entity
  bucket and fall into other render.
- `Session.vertex()` credits the innermost open call. A `RenderLivingEvent` listener that draws inside an
  entity's render would take that entity's vertices onto its own row.

**Design.** Event, layer and packet calls are **attribution-only**:

- **The stack.** They go on it like any timed call, so they get per-frame totals, peaks, GC-suspect flags
  and places among a hitch's top contributors.
- **Buckets.** The rule changes from "the stack is empty" to "no *bucket-owning* call encloses this one".
  An attribution-only call credits no bucket of its own. Its time stays wherever it ran:
  - in the HUD bucket, for a layer;
  - in other render or other tick, for a listener;
  - in tasks, for a packet.
- **Vertices** go to the innermost call that is not attribution-only, as they do today.
- **Named time.**
  - Each frame also records, per window (render, tick and tasks), the time of the outermost
    attribution-only calls that no bucket-owning call encloses, less any bucket time credited inside
    them.
  - That is the part of other render, other tick and tasks that the Mods tab names.
  - The time split prints it in brackets after those three figures, as `mods x`, and the Frames tab as an
    `in the Mods tab` row under each; `unattributed-stall` (P5.B2) subtracts it.
  - Subtracting the nested bucket time stops a block entity drawn inside a listener from counting twice.

One existing figure does move, by design. A bucketed call made inside the HUD is now inside a
bucket-owning call, so its time counts in `hud` instead of its old bucket. An entity model that a HUD mod
draws through `EntityRenderDispatcher.render` is an example: it moves from `entity` to `hud`, and is still
on the entity's own row.

**Phase 6 exception (spec addendum §P6.4).** `lod` always counts as itself. When a `lod` call ends, its time is
credited to `lod` at once, and the outermost bucket enclosing it is credited its own time less every `lod` call
inside it; a `lod` call inside another counts once. Every other bucket keeps the outermost rule, so a bucket inside
`hud` still counts as `hud`. Named time is unchanged: `lod`'s credit is bucket time like any other, so a handler
around a `terrain` call around a `lod` call names neither.

**User-visible change.** Apart from the HUD case above, the existing numbers do not change. Hitch top
contributors may now name mod handlers, which is what a stall needs.

**Acceptance check (A6).** Unit tests in `core`:

- a block entity call nested inside an attribution-only call is credited to the block entity bucket;
- an attribution-only call credits no bucket of its own, and inside a bucket-owning call its time stays in
  that call's bucket;
- vertices inside an attribution-only call nested in an entity call go to the entity;
- named time excludes the bucket time credited inside it;
- peaks and `gc f` work for attribution-only keys as they do for any other key.

## P5.A7 The Mods tab and report section

**Design.**

- **Mods tab.** One row per mod: `mod | share | us/f | peak ms | gc f | calls/f`.
  - A row expands to its members, each labelled with its kind: `event ClientTickEvent.Post`,
    `layer xaerominimap:minimap`, `packet somemod:sync_data`, and from phase 6 `sound create:steam (Blocks)`
    (spec addendum §P6.5): one row per sound event and category, under the event id's namespace, timing the whole
    `SoundEngine.play` call, so `calls/f` is plays per frame. A play only names time: the bucket it runs in keeps it.
  - The tab opens sorted by total time, largest first. `share` and `us/f` sort the same way, and the arrow
    sits on `share`, the first of them, as on the other ranked tabs. The columns sort like the other data
    tabs (P4.C7).
- **Where the time ran** is not a column. The time split's named figures (P5.A6) give it per window.
  Listeners that run outside render, tick and tasks still count in the Mods tab. These are input events
  fired during the frame-cap wait or the buffer swap, and `ClientPauseChangeEvent`, posted after the
  yield. In the split, their time is in `frame cap` or `swap/wait`.
- **Report section.** A `Mods` section after the hitch list lists the top mods by µs per frame, each with
  its top members indented, in the tab's columns.
- **JSON.** A `mods` array holds every mod with every member that completed a call, complete like every
  other array (P4.B1).
- **Shares overlap,** with each other and with the ranked tables, because counting is inclusive. A note above
  the tab's rows and above the report's table says so, and says that a library which dispatches events for
  other mods carries their time (P5.A3).
- **Empty states:**
  - `(event timing unavailable: <reason>)` when event timing is off (P5.A3);
  - `(no mod handlers, HUD layers, packets or sounds were timed)` otherwise (sounds from phase 6);
  - above a table that has layers or packets while event timing is off, the line
    `(event handlers were not timed: <reason>)` (plan 5a).

**Acceptance check (A7).**

- A `RankedTable`-style test covers the header, row and member cells.
- A JSON test checks that `mods` round-trips through a parser.
- In the dev client, the tab and the report agree for the same session.

## P5.A8 Riders

**Observed.** `ClientLevel.tickNonPassenger(vehicle)` ticks the vehicle inside `Level.guardEntityTick`. It
then calls `tickPassenger(vehicle, rider)` for each passenger, which calls `rider.rideTick()` and
recurses. A rider never passes through `guardEntityTick`, so its tick folds into the vehicle's row (P4.3).
The 4b/4c flight showed it again: the player ticked 0.0 while riding the gyrodyne.

**Design.** A `@WrapOperation` on `Entity.rideTick()` in `tickPassenger` (P5.A0 row 11) calls
`Probe.beginRiderTick(rider)`, which times an ordinary entity tick but records its own hook, `RIDER_TICK`,
so the environment shows whether it fired. The rider's row gets its own tick time, and the vehicle's row still
includes it, as it does for every nested call. It is an ordinary entity tick, not attribution-only; being
nested inside the vehicle's tick, it leaves the entity tick bucket credited once, by the vehicle.

**User-visible change.** The player's tick no longer vanishes while riding. Base §4.2's note on riders is
replaced.

**Acceptance check (A8).** In the dev client, riding a boat or a minecart, the player's row shows tick
time, and the vehicle's row is at least as large.

## P5.A9 JSON schema 5

- `schema` is 5.
- **Frames.** Every `frames[]` entry gains `tasksUs`, `capUs`, and `namedUs` with `render`, `tick` and
  `tasks` (P5.A6), leaving out a window with no named time; `bucketsUs` gains `hud`. `swapUs` now means
  `total − tick − render − tasks − cap`, and tick and render move with their boundaries (P5.A2).
- **Stats.** `stats`, where every average already lives, gains `avgTasksMs`, `avgCapMs` and `avgNamedMs`
  (`render`, `tick`, `tasks`), and `avgBucketsMs` gains `hud`. `session` is unchanged.
- **New arrays.** `mods` (P5.A7), each mod a ranked row followed by an `entries` array of its members, and
  `recommendations` (P5.B5).
- **Environment.** `environment` gains `eventTiming` (P5.A3) and P5.B3's keys. `hooksApplied` becomes `hooksFired`, and
  `hooksInstalled` and `hooksFailed` are added (P5.D1).

The README gains a schema 5 paragraph. It says that a schema 4 reader which derived swap from the other
fields must now subtract tasks and cap, or read `swapUs`.

---

# P5.B Group B: recommendations

## P5.B1 The rule engine

**Observed.** The report says what costs what. A player needs to know what to change, and a pack author
needs to know which mod to look at. The user asked for quick wins drawn from the player's own report, using
generic rules only.

**Design.**

- `core/advice` holds `Advisor.advise(SessionResult, Environment) → List<Recommendation>`. It is pure, has
  no Minecraft imports, and is derived at report time, never stored in the session.
- `Recommendation(String rule, Kind kind, Audience audience, String title, String finding, String action,
  double msPerFrame, double worstMs)`, with `Audience { PLAYER, PACK_AUTHOR }` and
  `Kind { STUTTER, FRAME_RATE, NOTE }`.
- **At stake** is the measured cost a finding is about. `msPerFrame` averages it over the session, and
  `worstMs`, for stutter only, is its largest single occurrence. A figure that does not apply is 0, so a note
  carries 0 in both. It is an upper bound on what the action can save, never a prediction of the saving. The
  README says so; the findings do not.
- **Ranking.**
  1. `STUTTER` findings, by `worstMs`.
  2. `FRAME_RATE` findings, by `msPerFrame`.
  3. `NOTE`s.

  Ties go to the player's findings first, then to rule order. Stutter comes first because averaging hides
  stalls (P5.1 change 5).
- **Rules.** Each rule is one static method in one of four themed classes (`SettingsRules`, `SceneRules`,
  `MemoryRules`, `PackRules`). It reads a `Facts` record derived once from the session, so each rule is
  unit-tested with `Facts` built in the test.
- **Missing data.** A rule never fires on it: if an environment value it needs is absent or unparseable,
  the rule is skipped.
- **Failure.** A rule that throws costs the advice, not the report. The report is written on the render
  thread as the session ends, where an exception would end the client and lose the session. So `advise`
  catches any `RuntimeException`, from a rule or from deriving `Facts`, and returns one pack-author note in
  place of the advice: `advice-failed`, which names the exception and where it was thrown (plan 5b, from
  review).
- **Thresholds.** They live as constants in one class, `Thresholds`. The numbers below are first
  estimates, to be revisited against real reports.
- **No automatic changes.** Recommendations never change a setting or a file.

## P5.B2 The rules

The examples are illustrative, and each one meets its rule's threshold. Only `unattributed-stall`'s is
real: it is hitch #1 of the 4b/4c base session.

| Rule | For | Kind | Fires when | At stake |
| --- | --- | --- | --- | --- |
| `entity-distance` | player | frame rate | entity render ≥ 1.0 ms/f and ≥ 10% of the average frame, with entity distance above 50% | entity render |
| `particles` | player | frame rate | Particles is `ALL`, and particle render plus tick ≥ 0.5 ms/f | particle render + tick |
| `shadows` | player | frame rate | a shader pack is on, and shadow time ≥ 1.0 ms/f and ≥ 30% of block entity plus entity render | shadow time |
| `flywheel-shaders` | player | frame rate | a shader pack is on, the Flywheel backend is `flywheel:off`, and block entity render ≥ 1.0 ms/f | block entity render |
| `chunk-rebuilds` | player | frame rate, or stutter when chunk upload is the largest part of the split in 3 or more hitches | chunk upload ≥ 1.0 ms/f, with biome blend ≥ 3 or render distance ≥ 16 | chunk upload; in the stutter form, its largest time in one of the hitches it leads |
| `distant-horizons` (phase 6) | player | frame rate | `lod` ≥ 1.0 ms/f and ≥ 10% of the average frame | `lod` |
| `sound-wait` (phase 6) | player | frame rate, or stutter when one sound row took at least 50 ms and at least half of a hitch, or passes `hitch-repeat`'s test, or the `sound` bucket is at least a quarter of the hitch in 3 or more hitches and in at least a quarter of all hitches | the sound rows sum to ≥ 0.5 ms/f, or one of the stutter form's tests | sound play time; in the stutter form, that row's largest time in one of the hitches the finding is about, or for the bucket test the `sound` bucket's largest time in one of those hitches |
| `frame-cap` | player | note | frame cap ≥ 25% of the average frame | — |
| `vsync` | player | note | vsync is on, and swap ≥ 25% of the average frame | — |
| `block-entity-type` | player | frame rate | a type's render plus tick ≥ 1.0 ms/f (at most three types) | that type |
| `entity-type` | player | frame rate | the same, for entity types | that type |
| `particle-type` | player | frame rate | a particle class ≥ 0.5 ms/f (at most three classes) | that class |
| `hitch-repeat` | player | stutter | a key other than a mod's (the mod rules and, from phase 6, `sound-wait` cover those) is among the top contributors, with at least a quarter of the hitch and no `[gc]` mark, of 3 or more hitches, and of at least a quarter of all hitches | its hitch time; its largest time in one hitch |
| `heap-vs-ram` | player | note, or stutter when a frame's GC pause reached 500 ms | physical memory, to the nearest whole GiB, minus the maximum heap is under 4 GiB | none as a note; in the stutter form, GC time and the longest GC pause in one frame |
| `heap-small` | player | frame rate | the smallest heap in use after any collection is ≥ 80% of the maximum heap | GC time |
| `gc-pauses` | player | stutter | the collector is G1, Parallel or Serial, and the longest GC pause in one frame is ≥ 50 ms | GC time; longest pause |
| `unattributed-stall` | pack author | stutter | a hitch where other tick, other render or tasks, less the time the Mods tab names in it (P5.A6) and less the frame's GC pause, is ≥ 50 ms and at least half the frame; or, from phase 6, where one of them is at least a quarter of the hitch in 3 or more hitches and in at least a quarter of all hitches | that time; its largest in one hitch |
| `mod-handlers` | pack author | frame rate, or stutter when one of its handlers took at least 50 ms and at least half of a hitch, or passes `hitch-repeat`'s test | a mod's event handlers ≥ 0.5 ms/f, or the stutter form's test, whatever their steady cost | that mod's handlers; in the stutter form, that handler's largest time in one of the hitches the finding is about |
| `mod-hud` | pack author | frame rate, or stutter when one of its layers took at least 50 ms and at least half of a hitch, or passes `hitch-repeat`'s test | a mod's HUD layers ≥ 0.5 ms/f, or the stutter form's test, whatever their steady cost | those layers; in the stutter form, that layer's largest time in one of the hitches the finding is about |
| `mod-network` | pack author | frame rate, or stutter when one of its payload types took at least 50 ms and at least half of a hitch, or passes `hitch-repeat`'s test | a mod's payloads ≥ 0.5 ms/f, or the stutter form's test, whatever their steady cost | those payloads; in the stutter form, that payload type's largest time in one of the hitches the finding is about |
| `stack-resets` | pack author | note | `unbalancedFrames` > 0 | — |

| Rule | Finding (example) | Action |
| --- | --- | --- |
| `entity-distance` | Entities take 2.4 ms of each 12.0 ms frame at 300% entity distance. | Lower Entity Distance in Video Settings; the number of entities drawn falls with the square of the distance. |
| `particles` | Particles take 0.9 ms a frame with Particles on All. | Set Particles to Decreased in Video Settings. |
| `shadows` | The shader pack's shadow pass is 38% of block entity and entity drawing, 2.1 ms a frame. | Lower the shader pack's shadow distance or resolution, or turn its shadows off. |
| `flywheel-shaders` | Flywheel is not instancing under this shader pack, so the machines it would instance are drawn one at a time: block entities take 3.2 ms a frame. | Turn shaders off to bring instancing back: Flywheel does not instance while any shader pack is in use. |
| `chunk-rebuilds` | Chunk rebuilds take 1.8 ms a frame and are the largest part of 4 of 6 hitches, at render distance 16 and biome blend 7x7. | Lower Biome Blend to 5x5 or less. While travelling, a lower render distance rebuilds fewer sections. |
| `distant-horizons` | Distant Horizons draws its far terrain in 3.2 ms of each 10.1 ms frame. | Lower Quality Preset or LOD Chunk Render Distance Radius in Distant Horizons' settings, which its button beside FOV in Options opens. |
| `sound-wait` | Playing sounds kept the game waiting 1.4 ms a frame; `create:steam` (Blocks) played 85 times a second. | Set the Blocks volume to 0% in Music & Sounds: the game then skips those sounds instead of waiting for them. A pack author can report the sound's mod with this file. |
| `sound-wait`, bucket test | In 30 of 83 hitches, the sound engine took at least a quarter of the frame (up to 18 ms). Playing sounds kept the game waiting 1.4 ms a frame; `create:steam` (Blocks) played 85 times a second. | Set the Blocks volume to 0% in Music & Sounds: the game then skips those sounds instead of waiting for them. A pack author can report the sound's mod with this file. |
| `frame-cap` | 8.3 ms of each 16.7 ms frame is spent waiting for the 60 fps limit, so the game has headroom. | Raise Max Framerate if you want more frames. |
| `vsync` | With VSync on, 5.2 ms of each 16.7 ms frame is spent waiting for the display. | Turn VSync off for more frames, at the risk of tearing. |
| `block-entity-type` | `create:factory_panel` takes 1.1 ms a frame across 212 blocks; the most expensive is at 120 64 -340. | It is the build, not a setting: fewer of them in view at once costs less. Highlight on the Block Entities tab shows where they are. |
| `entity-type` | `minecraft:item_frame` takes 1.3 ms a frame across 340 entities. | Fewer of them nearby, or a lower Entity Distance. |
| `particle-type` | `DripParticle` takes 0.7 ms a frame, with 1,400 drawn on average. | Find what emits them; lowering Particles hides most of them. |
| `hitch-repeat` | `create:mechanical_crafter @ 118 64 -338` was a top cost in 5 of 13 hitches, up to 42 ms. | Look at what it is doing when the stutter happens. A pack author can take this report to the mod. |
| `heap-vs-ram` | 14 GiB is allocated on a machine with 16 GiB, which leaves 2 GiB for Windows, drivers and the game's memory outside the heap. | Allocate at most 12 GiB, this machine's memory less 4 GiB. When the live data (`heap-small`) would fill 80% or more of that heap, the action says instead that the machine is short of memory for this pack, and to close other programs while playing. So does a machine with less than a whole GiB to spare. |
| `heap-small` | After every collection at least 6.9 of 8 GiB was still in use, so the collector runs often. | Allocate 12 GiB (1.6 x the live data, rounded up to a whole GiB). When that is more than `heap-vs-ram` allows, the action offers the most this machine can spare, if the live data would fill under 80% of it: "Allocate 10 GiB, the most this machine can spare (1.6 x the live data would be 12 GiB)." Otherwise: "This machine cannot spare 12 GiB (1.6 x the live data): close other programs, or lower render distance so the game needs less." |
| `gc-pauses` | Garbage collection paused the game for up to 120 ms (14 collections, 610 ms in total). | On Java 21, add `-XX:+UseZGC -XX:+ZGenerational` to the JVM arguments (leave out `-XX:+ZGenerational` on Java 23 or later: from Java 26 the game will not start with it). ZGC pauses for well under a millisecond but uses more CPU, and more memory for the same data, so keep a quarter of the heap free. From Java 23 the action names `-XX:+UseZGC` alone, with no note in brackets. When `jvmFlags` holds the flag that chose the collector in use, such as `-XX:+UseG1GC`, the action says to replace that flag instead: the JVM will not start with two collectors chosen. |
| `unattributed-stall` | Hitch #1 (382 ms): 374 ms of client tick is in code Renderable does not time. | Profile the same place with a sampling profiler such as spark, which names the code. |
| `unattributed-stall`, repeat form | In 30 of 83 hitches, at least a quarter of the frame was in code Renderable does not time, up to 20 ms of client tick. | Profile the same place with a sampling profiler such as spark, which names the code. |
| `mod-handlers` | `somemod`'s event handlers take 1.2 ms a frame, most of it in `RenderLevelStageEvent`. | Check the mod's config for the feature behind that event, or report it to the mod with this file. |
| `mod-hud` | `somemod`'s HUD layers take 0.8 ms a frame. | Check the mod's HUD settings, or report it to the mod with this file. |
| `mod-network` | The `somemod:sync` payload was a top cost in 4 of 13 hitches, up to 24 ms. `somemod`'s payloads take 0.6 ms a frame on the client. | The server is sending work the client handles on its main thread; report it to the mod with this file. |
| `stack-resets` | 12 frames had unbalanced timing: a timed call did not return normally. | The game log from around the session should hold the exception; report it with the log. |

The `mod-handlers`, `mod-hud` and `mod-network` rules skip `minecraft`, `neoforge` and `unknown`: vanilla HUD
layers and packets appear in the Mods tab, but a pack author cannot change them, nor NeoForge's own handlers,
nor handlers whose mod could not be found. The mod rules' stutter forms exist because `unattributed-stall`
no longer fires for time the Mods tab names, `hitch-repeat` leaves mod keys out, and a stall averages far
below half a millisecond a frame over a session (plan 5b, deviations 2, 3 and 6). A hitch counts toward a
repeat only when the key took at least a quarter of it and carries no `[gc]` mark, because every hitch's top
list also holds the frame's usual costs.

Review changed these rules after plan 5b was written (plan 5b, from review):

- **The mod rules share their stutter forms.** Plan 5b's deviation 2 gave `mod-handlers` a stall form and a
  repeat form; `mod-hud` and `mod-network` now have the same two, for one layer or one payload type. All
  three fire them whatever the mod's steady cost, and a contributor with the `[gc]` mark counts toward
  neither. The finding names the handler, layer or payload type, and adds the mod's steady cost when that
  reaches 0.5 ms a frame. Its figure is that key's largest time in one of the hitches the finding is about. A
  repeat is named rather than a stall when its worst time is at least as large.
- **Physical memory counts in whole GiB.** `physicalMemoryMiB` is usable memory (P5.B3), so a 16 GB PC reports
  15.8 to 15.9 GiB. `heap-vs-ram` and `heap-small` round it to the nearest whole GiB, so 12 GiB allocated on
  that PC is fine, while a reservation of more than half a GiB still counts against the machine. The
  environment keeps the figure as measured, and `heap-vs-ram`'s finding shows it rounded.
- **`heap-vs-ram` and `heap-small` share one test.** Each asks whether the live data would fill under 80% of
  the largest heap `heap-vs-ram` accepts, this machine's memory less 4 GiB. When it would not, `heap-vs-ram`
  says the machine is short of memory and `heap-small` says the machine cannot spare what the live data
  needs, so the two agree on whether the machine can hold this pack.
- **`heap-small` can offer the most the machine can spare.** When 1.6 times the live data is more than that,
  but the shared test passes, it suggests that heap instead.
- **`gc-pauses` swaps the collector flag.** The JVM will not start with two collectors chosen, and launchers
  often choose G1. When `jvmFlags` holds the flag for the collector in use, such as `-XX:+UseG1GC`, the action
  says to replace it rather than to add `-XX:+UseZGC`.
- **`gc-pauses` warns about `-XX:+ZGenerational`.** On Java 21 and 22 the action says to leave it out on Java
  23 or later. From Java 23 ZGC is generational by default, and from Java 26 the game will not start with it.
- **`particle-type` counts particles drawn, not alive.** The count is the class's average number drawn each
  frame, the Particles tab's avg live figure. The finding leaves it out when it rounds to 0.
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
  see sound rows, and the bucket is never unnamed time, so no sound row feeds two rules; a play's time still counts
  for whatever played it, so a block entity or payload that plays a costly sound can be named by its own rule too.
- **`distant-horizons`** is the one rule that names a mod, by the phase 6 design, although P5.1 chose generic rules
  only. `lod` is time Renderable measures in Distant Horizons' own render methods, so the rule can only fire where
  DH is installed and hooked. Its action names DH's own settings, the same in 2.4.5-b and 3.3.3.
- **`unattributed-stall`** also fires on repeated unnamed time. Each hitch's three parts are taken on their own, as
  in the stall form, and a hitch counts once however many of its parts pass. The finding names the window of the
  largest part. When both forms pass, the repeat is named, with that largest part as its worst: a stall's part is
  always a quarter of its hitch, so the repeat says both.

## P5.B3 New environment facts

| Key | Source | Notes |
| --- | --- | --- |
| `physicalMemoryMiB` | `com.sun.management.OperatingSystemMXBean.getTotalMemorySize()` | the memory the system can use: what is installed, less what the firmware and an integrated GPU reserve. 63100 on the dev machine. `heap-vs-ram` and `heap-small` round it to the nearest whole GiB |
| `gc` | the collector bean names | `G1`, `ZGC`, `ZGC generational`, `Shenandoah`, `Parallel`, `Serial`, or the raw names |
| `initialHeapMiB` | `MemoryMXBean.getHeapMemoryUsage().getInit()` | |
| `liveHeapAfterGcMiB` | the smallest heap in use after any collection in the session | absent when the session saw no collection |
| `jvmFlags` | `RuntimeMXBean.getInputArguments()`, through a whitelist | never paths or system properties |

`liveHeapAfterGcMiB` comes from GC notifications. A `NotificationListener` on each collector bean except the
pause beans (a ZGC or Shenandoah pause notification reports no heap in use) is registered at session start
and removed at the end. It sums `GcInfo.getMemoryUsageAfterGc()` over the heap pools and keeps the minimum,
ignoring a sum of 0. It runs on the JMX notification thread, so the render thread never allocates for it. The
value is an upper bound on live data, because a young collection leaves old garbage in place. `heap-small`
therefore fires only when every collection in the session left the heap at least 80% full.

The `jvmFlags` whitelist keeps only these, each with a value that is a number with an optional size suffix
or a bare word:

- `-Xmx`, `-Xms`, `-Xmn` and `-Xss`;
- `-XX:+/-` with `UseG1GC`, `UseZGC`, `ZGenerational`, `UseShenandoahGC`, `UseParallelGC`, `UseSerialGC`,
  `AlwaysPreTouch`, `UseLargePages`, `UseTransparentHugePages`, `UseStringDeduplication`,
  `DisableExplicitGC`, `UnlockExperimentalVMOptions` or a `G1`-prefixed name;
- `-XX:Name=value` for `MaxGCPauseMillis`, `ParallelGCThreads`, `ConcGCThreads`, `SoftMaxHeapSize`,
  `MaxRAMPercentage`, `InitialRAMPercentage` and `G1`-prefixed names.

Everything else is dropped, including `-D` properties, `-javaagent`, class paths and `--add-opens`, which
can carry user names in paths.

## P5.B4 GC pauses under ZGC and Shenandoah

**Observed.** `GcSampler` sums `getCollectionTime()` over every `GarbageCollectorMXBean`. The bean names on
JDK 21.0.11:

| Collector | Beans |
| --- | --- |
| G1 | `G1 Young Generation`, `G1 Old Generation`, `G1 Concurrent GC`. The last counts only the Remark and Cleanup pauses: in a check run under `-Xlog:gc` it reported 6 collections and 1 ms, matching the 3 Remark and 3 Cleanup pauses logged (1.98 ms), against 59.6 ms of concurrent marking. |
| ZGC | `ZGC Cycles`, `ZGC Pauses` |
| ZGC, generational | `ZGC Minor Cycles`, `ZGC Minor Pauses`, `ZGC Major Cycles`, `ZGC Major Pauses` |
| Shenandoah | `Shenandoah Cycles`, `Shenandoah Pauses` |

A `Cycles` bean times the concurrent cycle, not a pause: 33 ms of cycles against 0 ms of pauses in the
check run. A player who followed `gc-pauses` would get a report that counts every concurrent cycle as a
pause. Frames would be flagged GC-suspect when they were not (P4.A0), peaks would be dropped, and the
hitch GC lines would be inflated.

**Design.** When any bean's name ends in ` Pauses`, `GcSampler` sums only those beans, for both time and
count. Otherwise it sums them all, as now. The name test is a pure function in `core`, unit-tested with the
names above.

**Limit.** A pause is not the only way these two collectors hold the game up. A ZGC allocation stall, and
Shenandoah's pacing of a thread that allocates, make that thread wait outside any pause, so the time is not
GC time. It counts as frame time, charged to whatever was running, with no `[gc]` mark. When a timed call was
allocating, `hitch-repeat` or a mod rule can name it for a stall the collector caused (plan 5b, from review).

**Acceptance check (B4).** A unit test covers the four collectors' bean names. A dev-client session under
`-XX:+UseZGC -XX:+ZGenerational` reports pauses of a millisecond or less and no GC-suspect frames.

## P5.B5 Where recommendations appear

- **Text report.** A `Recommendations` section right after the header line, before `Environment`. It shows
  up to six recommendations, each tagged `[player]` or `[pack author]`, with its title and at-stake figure,
  then its finding and action indented. The figure reads "up to 83 ms" for stutter and "1.2 ms/frame" for
  frame rate, or "under 0.1 ms/frame" where that would read 0.0; a note has none. When there are more it
  adds "N more on the Advice tab and in the JSON". With none it says "No recommendations: nothing measured
  crossed a threshold."
- **Screen.** A new `Advice` tab, first in the row. A new result opens on it (P5.D6 keeps the last tab on
  reopen). The tab shows every recommendation, grouped under "For you" then "For the pack author", each
  wrapped to the list's width as single-column rows. The ten tabs run Advice, Frames, Hitches, Mods,
  Block Entities, then Entities, Particles, Flywheel, Blocks, Sections: five per row instead of four, so
  the tab area stays two rows high. At the narrowest supported GUI, 427 px at Auto scale on 1440p and
  4K (Minecraft rounds the scaled width up), that gives a 79 px button, and "Block Entities" is 66 px of text.
- **Chat line.** After the hitch count, `, N suggestions` when N > 0 (`, 1 suggestion` for one), for example
  "Renderable: 2872 frames in 30.0 s, avg 10.4 ms, 13 hitches, 4 suggestions."
- **JSON.** A `recommendations` array with every recommendation:
  `{rule, kind, audience, title, finding, action, msPerFrame, worstMs}`.

**Acceptance check (B5).** Unit tests cover each rule, the ranking, and the report section's cut at six.
In the pack, the user reads the Advice tab after a session at the base and judges whether each suggestion
makes sense. This is part of the phase 5 acceptance log.

---

# P5.C Group C: ready for players, not rolled out

## P5.C1 Idle cost (base §9 check 5)

**Observed.** This check has never been run: P4.A4 wrote the protocol and left it unexecuted. Its scene
kills every entity, so the per-entity hooks, which are the busiest, never run in it.

**Design.**

- **When.** After 5a, 5b and 5d, on 5c's branch, before anything merges to `main`. The user runs it, since it
  needs the game in the foreground. The results go in `docs/acceptance/phase-5-idle.md`.
- **Scene.** P4.A4's, with one change so that the entity render and tick hooks run in every frame: after
  `/kill @e[type=!player]`, twenty villagers are summoned with `NoAI` in view of the fixed camera. Their
  positions are written into the protocol. They stand still, so the scene stays static.
- **Runs and numbers.** P4.A4's four runs and acceptance numbers, with what counts as Renderable's and the
  control changed as below.
- **Where.** In the dev client, as P4.A4 planned, with the pack's rendering mods.
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
- **Pass.** The README states the measured result and its scene.
- **Fail.** The allocation and execution samples name the hook responsible, and that is a defect to fix
  before rollout. Event timing cannot be the cause, since none of it is installed outside a session
  (P5.A3).

**Acceptance check (C1).** `docs/acceptance/phase-5-idle.md` exists, with the acceptance numbers above and a
verdict.

**Status.** Not run for 0.5.0. On 2026-09-28 the user skipped the runs: players add Renderable for a profile
and remove it afterwards, and no cost was noticed while it was installed. The protocol stays for a later
release.

## P5.C2 Overhead calibration covers the hook side

**Observed.** P4.3: both calibrated constants measure from inside `Session` and leave out the guard each
hook pays first. At tens of millions of calls a session, one unmeasured nanosecond per call is tens of
milliseconds the report does not claim. Phase 5 adds proxy, layer and packet calls.

**Design.**

- **What it drives.** A calibration entry point in `hooks` drives whole calls through the same static
  `Probe` methods the mixins call, and through a `TimedListener`.
- **What it measures.** Three kinds of call, each with its own constant and its own counter in the
  overhead estimate:
  - a timed begin/end pair;
  - a vertex call;
  - an attribution-only pair: a proxy invocation, which also stands for layer and packet calls.
- **When it runs.** Once, before the first real session attaches, against a scratch session attached for
  the duration. Anything other threads record into the scratch session is discarded.
- **Fired-hook flags.** `Probe`'s `SEEN[]` flags and `seenVertices` are saved before calibration and
  restored after, so calibration does not mark hooks as fired (P5.D1).
- **Javadocs.** The boundary paragraphs in `Calibration`'s javadocs go, and the log records the three
  constants when calibration runs, before the first session.
- **Warm figures.** Each constant is the fastest of five rounds of 10,000 calls: one round mostly times the
  interpreter, not compiled code (plan 5c, deviation 6).
- **Beside them in the log.** Calibration also times `Session` alone for a pair and a vertex, phase 4's way,
  and logs those beside the three constants, so C2's check reads off one log line (plan 5c, deviation 1). The
  timed pair is driven through `Probe.beginEntity` on the local player, the busiest real hook (deviation 2).

**Acceptance check (C2).** The calibration line logs the timed pair and the vertex call beside `Session` alone.
The vertex costs at least what `Session` alone does, and at most a few nanoseconds more: its guard is a
volatile read, a thread comparison and a branch. The timed pair comes within a few nanoseconds of `Session`
alone, either way: what it adds, the guard, `Probe`'s checks, the entity's accumulator lookup and the shadow
check, is lost in the jitter of its two clock reads (plan 5c, deviations 1 and 2).

## P5.C3 Privacy: player names

**Observed.** `KeyFactory` puts `player.getGameProfile().getName()` into every player's `EntityKey`, so
reports, labels and the JSON carry the names of everyone near the player.

**Design.** Names are dropped when the key is created, so they never reach the session. Another player's
row reads `minecraft:player #1234`, and the local player's reads `minecraft:player #118098 (you)`. Custom
names on mobs stay. Coordinates stay too, and the README's "For players" section says to share reports
privately, because they contain the player's position.

**Acceptance check (C3).** A unit test on the label covers both cases. A session on a server with another
player online shows no names in the files.

## P5.C4 The default key collides with Lighty

**Observed.** The pack instance's `options.txt` binds both `key_key.lighty.enable` and
`key_key.renderable.toggle` to `key.keyboard.f7`. F2, F5, F7, F8, F9 and F11 are bound in the pack, and F1
and F3 are fixed by vanilla, as is F4, which toggles a spectator's view effect. F6, F10 and F12 are free.

**Design.** Change the default to **F6**. A saved binding is kept by the game, so only new installs get
F6. Nobody has the mod yet, so nobody is affected. The README says F6 and that it can be changed in
Controls.

**Acceptance check (C4).** A fresh instance binds F6. The pack's Controls screen shows no conflict on F6.

## P5.C5 A README section for players

A "For players" section at the top of the README:

- what the mod is for;
- press F6, play normally for 30 seconds, and read the Advice tab;
- what to send the pack author, and how (privately, per P5.C3);
- how to hide the overlay;
- that it is for profiling: add it, profile, then remove it (P5.C1's idle-cost runs were skipped).

The existing sections stay for readers who want the detail.

## P5.C6 The pack module entry: documented, not applied

`docs/pack-module.md` holds the MCUpdater module entry the pack author would add: a client-only, optional
module pointing at the GitHub release asset. It is modelled on an existing client-only module in the server
pack XML under `H:\repos\Gears-Frontiers`. Phase 5 does not edit that repository.

## P5.C7 What needs the user

Creating the GitHub remote and pushing, then plan 4d's Task 7 (CI on a real runner), then the first
release, cut by pushing the `v0.5.0` tag once CI is green. These stay outside the plans.

---

# P5.D Group D: polish

Each item was deferred with its reasoning in P4.3 or observed in the 4b/4c log; only the design is given
here.

## P5.D1 Hooks installed versus hooks fired

**Observed.** `Probe.hooksApplied()` lists the hooks that have fired at least once since the game started.
The README says so, but §4.5's "hook did not apply" state cannot be told from "nothing happened", and
`EmptyStates` guesses between the two. `8e47674` fixed one wrong guess, and further guesses remain.

**Design.**

- **What the plugin records.** `RenderableMixinPlugin.postApply`, which is already attached to the
  configs, records two facts per hook: that its target class has loaded, and whether the hook's injection
  point is present in it.
- **Why it checks the target, not the handler.** MixinExtras applies `@WrapOperation` and `@WrapMethod`
  in a later pass than `postApply` (verified in MixinExtras 0.5.0: `@WrapOperation` is a late injection, and
  `@WrapMethod` only offers a wrapper, which its applicator extension applies after the class's mixins). At
  `postApply` no call to their handlers exists yet, so searching for one would report every such hook as not
  installed. Instead the plugin checks two things: that the hook's target method exists in the class and, for
  an `INVOKE` injection point, that the target instruction is in it. A plain `@Inject` leaves its target
  instruction in place, so the same check serves it.
- **Sugar.** Before any mixin applies, MixinExtras moves the injector annotation of a handler with a sugar
  parameter, such as `@Local`, into the `original` of a `@SugarWrapper` on the mixin's cached class node, which
  `IMixinInfo.getClassNode` copies for `postApply`. Only a `@WrapMethod` keeps its annotation. The check reads
  the wrapper's `original`. The class file, which the unit tests read, has the plain annotation, so the wrapper
  showed only in a real client (plan 5d Task 9).
- **Installed** means that check passed.
- **Three lists.** The environment carries `hooksInstalled`, `hooksFired` (the old list, renamed) and
  `hooksFailed`, the hooks with an injector whose target was missing (plan 5d, deviation 1). Without the
  third, "did not apply" could not be told from "never loaded". A failed injector outweighs a firing: a hook
  in both lists applied only in part.
- **Mapping.** `HookInstalls.HOOKS` maps each injector, by mixin and handler, to its hook; the four frame and
  tick boundaries, which every session needs, are in `HookInstalls.BOUNDARIES` instead and are not checked.
  `HookInstallsTest` reads every mixin's class file and fails for an injector in neither table, and for an
  entry that names no injector.
- **Limit.** A third-party mixin that Mixin rejects as a whole, for a shadow or accessor that no longer
  matches, never reaches `postApply`. Its hooks read as never loaded, and Sodium's empty tables then point at
  the game log. A check that throws is logged too; it never fails the class it ran on, and it leaves its hooks
  unrecorded.
- **Empty states.** The three lists tell three cases apart, all named by Sodium's empty tables and only the
  first by the ranked and particle tables (P5.D2):
  1. **the hook did not apply:** its target loaded, and the check failed;
  2. **the code never ran:** its target class never loaded;
  3. **nothing happened:** it is installed, and no data arrived.
- **README.** The "Hooks applied" paragraph is rewritten to match.
- **Event timing** is not a mixin. Its state is reported by P5.A7's empty states and by
  `/renderable debug events` (P5.A3).

## P5.D2 Empty-state sentences

- **The ranked tables.** "(no data: hook not active in this build)" becomes, with D1's information, either
  "(nothing of this kind was drawn or ticked)" or "(hook not installed)".
- **A Block Entities tab that holds only Flywheel's heading** gets the same sentence under it.
- **A Flywheel table with no rows** says "Flywheel sampled N times and found no visuals alive" instead of
  printing a header and dashes.
- **Sodium's tables** tell four cases apart: "(no data: Sodium is not installed)", "(no data: Sodium's mesh
  hook did not apply)", "(no data: Sodium's mesh hook never loaded; the game log says why)", and "(no data:
  no blocks were meshed)" or "(no data: no sections were rebuilt)" (plan 5d).
- **Particles** keep "(no particles were rendered)", or say "(hook not installed)".
- **Wording.** The Flywheel sentence is "(Flywheel sampled 3 times and found no visuals alive)", or "sampled
  once". It is said only when the samples found nothing alive; visuals counted but not by class show their
  totals alone.

## P5.D3 Blocks and Sections cells are written once

`TextReportWriter` and `Rows` each build the Blocks and Sections cells by hand. They move into a `core`
class shaped like `RankedTable`. The only difference between the two surfaces stays the Sections
`avg ms/rb` header (P4.C6 #1).

## P5.D4 Particle names, and the shadow column

- **Particle classes** print simple names by the rule Flywheel's names already follow
  (`FlywheelStats.displayNames`), moved to a shared `core` helper. On a collision a class gets its last
  package segment, and the full name if that still collides. The JSON keeps full names. The short names are
  for the particle tables: a hitch's contributor lines and the recommendations keep each key's own label, as
  for every other kind of key (plan 5d, deviation 4).
  The particle rows are sorted again under their short names, so an exact tie reads A to Z by the name shown,
  in the report as on the screen.
- **The text report drops `shadow`** from a ranked table when no row in it has shadow time. The screen and
  the JSON keep it.

## P5.D5 Natural, case-insensitive name sort

Name columns compare case-insensitively. Digit runs, with a leading minus sign, compare as numbers, so
section labels sort `-10`, `-2`, `-1` in ascending order rather than as text. A minus sign is a sign only at
the start of a name or after a space, so `mod-2` still comes before `mod-10`, and `-0` sorts with the
negatives, which keeps the order transitive (plan 5d). The tie-break is unchanged, and the report, which never
sorts by name, is unaffected.

## P5.D6 The screen remembers its view

The tab, each tab's sort, the open groups, the selected row and the scroll position live in a `ResultsView`
that `ProfilerController` keeps for the current result (plan 5d, deviation 3), so
Highlight and Teleport no longer cost the reader their place. A new result clears them and opens on Advice
(P5.B5).

## P5.D7 Dimension checks per row

- **Highlight and Teleport** compare the selected row's own dimension with the current level's.
  Block entity and section keys carry a dimension; entity rows use the session dimension
  `ProfilerController` records.
- **The blanket world-gone flag** stays for logout only.
- **A highlight** carries its dimension and draws only there.

## P5.D8 Labels drawn managed

The overlay's labels are drawn in two batches, every background and then every text, instead of costing 150
draws a frame for 75 labels. `drawManaged` alone would not do it: the GUI's fill and text render types share
one buffer, and each switch between them ends a batch. Each label is drawn at its own depth, so where two
overlap the later one hides the earlier one's text instead of dimming it through its translucent background
(plan 5d, deviation 2).

## P5.D9 `render us/f` fits its column

At GUI scale 3 on a 2560 px window the header is 63 px of text in a 60 px column, and truncates to
`render us/`. The screen widens a number column until its header fits, with room for the sort arrow in every
sortable column, sorted or not, so the columns do not move when the sort changes; a header may reach 3 px
left, as a number may. The pixels come from the name column, which gives at most a quarter of its width, to
the smallest shortfalls first. A header they do not cover still truncates; at the Auto GUI scale on 1080p and
1440p screens, some of the ranked tabs' headers do (plan 5d, deviation 5). The header text stays the same as
the report's.

**Acceptance for group D.** Unit tests in `core` for D3, D4 and D5. D1's target check is tested against
small ASM class nodes. The rest is covered by the in-pack checks in P5.2.

---

## P5.2 Order, versions and acceptance

**Order.**

- **5a, then 5b.** 5b's pack-author rules read 5a's data.
- **5d after 5b.** D1 renames a field that 5a's schema 5 introduces, and D6 opens the screen on 5b's
  Advice tab.
- **5c last,** because its JFR run measures the finished hooks.

The branches stack as phase 4's did. Everything merges to `main` after the in-pack acceptance and is
tagged `v0.5.0`.

**Plans,** in `docs/superpowers/plans/`: `2026-09-26-renderable-phase-5-readme.md`, and one per group,
`2026-09-26-renderable-phase-5a-attribution.md`, `2026-09-26-renderable-phase-5b-advice.md`,
`2026-09-26-renderable-phase-5c-readiness.md` and `2026-09-26-renderable-phase-5d-polish.md`.

**Acceptance.** One in-pack session set at the end, recorded in `docs/acceptance/phase-5.md`, covers:

- the A, B, C and D checks above that need the pack;
- the checks 4b and 4c left unrun:
  1. the `reportFolder` fallback;
  2. the overlay toggle surviving a session;
  3. Highlight and Teleport greyed after a reconnect, with their tooltip;
  4. empty states and the shadow column with shaders on;
  5. labels across a portal;
  6. the Auto GUI scale check, the Flywheel and Sections tabs on screen, standing inside a highlighted
     section, and the F3 frame-time comparison.

The JFR run (P5.C1) is recorded separately in `docs/acceptance/phase-5-idle.md`.

A dev-only system property, `-Drenderable.autostart=<seconds>`, runs one session after joining a world, so an
unattended dev client can produce the reports that the dev-client checks above read (plan 5a Task 9).

## P5.3 Spec amendments collected

Applied by the plan that implements each:

| Where | Amendment | Plan |
| --- | --- | --- |
| §3.2 | The frame record: `tasksNanos` and `capNanos`, the `HUD` bucket, named time per window, and the new swap formula | 5a |
| §3.2 | Frame and tick boundaries at the `Minecraft` call sites | 5a |
| §3.2 | Buckets credited by the outermost *bucket-owning* call | 5a |
| §3.2 | GC time and counts from the `Pauses` beans when a collector has them (P5.B4) | 5b |
| §3.4 | Three new key kinds (mod event, HUD layer, packet), all attribution-only | 5a |
| §3.4 | Vertices credited to the innermost call that is not attribution-only | 5a |
| §3.4 | Players' names dropped from `EntityKey` (P5.C3) | 5c |
| §3.5 | Calibration through whole `Probe` calls, with three constants | 5c |
| §3.5 | Self time: the HUD sampling part now sits in `hud`; the Flywheel sample and the event-list check stay in other render | 5a |
| §4 | The P5.A0 hook table | 5a |
| §4 | Event timing by per-session proxies, which is not a mixin | 5a |
| §4.2 | The rider note replaced by the rider hook | 5a |
| §4.5 | Installed versus fired | 5d |
| §5.1 | Default key F6 | 5c |
| §5.3 | Advice and Mods tabs, five tabs per row, and the view remembered on reopen | 5a, 5b, 5d |
| §6.1 | Recommendations first; the Mods section | 5a, 5b |
| §6.1 | The new split lines and named figures; the self-time line | 5a |
| §6.1 | Simple particle names; the shadow column dropped when empty | 5d |
| §6.2 | Schema 5 | 5a, 5b, 5d |
| §9 | Check 5's scene gains `NoAI` mobs; its result; the new unit tests | 5c |
| §11 | A row each for 5a and 5b | 5a, 5b |

## P5.4 What phase 5 does not do

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
- **No sampling profiler.** spark remains the tool for code Renderable does not hook.
- **No pack-specific rules,** and no rule that changes anything. Phase 6's `distant-horizons` names one mod,
  Distant Horizons (spec addendum §P6.9); it fires only on time measured in DH's own render methods.
- **No Highlight or Teleport from an Advice row.**
- **No timing of network-thread payload handlers,** which are not frame time.
- **No rollout.** The pack module is documented, not applied. The remote, CI and the release are the
  user's steps (P5.C7).
- **No timing of per-phase posts,** `post(EventPriority, Event)`. No call site in the scan makes one.
- **No split of a library's dispatch.** Mods whose handlers run through Architectury, Balm or Puzzles Lib
  are attributed to the library.
- **No GPU timer queries.** §2 is unchanged.
