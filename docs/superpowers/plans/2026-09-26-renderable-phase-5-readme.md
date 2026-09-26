# Renderable phase 5: which plan to run, and what was decided

**Date:** 2026-09-26
**Spec addendum:** `docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md`
**Baseline:** `main` at `7922030` (the spec), code as of `f9a9acd`, tag `v0.4.1`, 132 JUnit tests.

## Order

The plans run in a fixed order, each on a branch cut from the one before, as phase 4's did (§P5.2):

| Plan | File | Branch, cut from | Tests at the end |
| --- | --- | --- | --- |
| 5a attribution | `2026-09-26-renderable-phase-5a-attribution.md` | `phase-5a-attribution`, from `main` | 160 |
| 5b advice | `2026-09-26-renderable-phase-5b-advice.md` | `phase-5b-advice`, from 5a's | 190 |
| 5d polish | `2026-09-26-renderable-phase-5d-polish.md` | `phase-5d-polish`, from 5b's | 207 |
| 5c readiness | `2026-09-26-renderable-phase-5c-readiness.md` | `phase-5c-readiness`, from 5d's | 209 |

Why this order:
- **5b after 5a.** 5b's pack-author rules read 5a's Mods data and named time.
- **5d after 5b.** 5d renames the hook list that 5a's schema 5 introduced, and opens the screen on 5b's Advice
  tab.
- **5c last.** Its idle-cost run measures the finished hooks, and its in-pack acceptance covers all four plans.

Nothing merges to `main` until 5c's acceptance has passed. Then `main` fast-forwards to `phase-5c-readiness`
and is tagged `v0.5.0` (5c Task 9). Each plan's test total assumes the plans before it have run; each plan's
"Verified facts" section says what it expects to find.

## Where two plans touch the same thing

Each later plan quotes the text as the earlier plans leave it, so they must run in order.

1. **`core/Environment`.**
   - 5a adds `EVENT_TIMING` and `UNAVAILABLE`.
   - 5b adds five memory and JVM keys.
   - 5d replaces the record's hook list with three lists (`hooksFired`, `hooksInstalled`, `hooksFailed`) and
     `state(Hook)`.
2. **`core/EmptyStates`.**
   - 5a adds the Mods sentences.
   - 5b adds the Advice one.
   - 5d replaces the ranked, particle and mesh sentences.
3. **`report/TextReportWriter`.**
   - 5a adds the Mods section and the new split line.
   - 5b adds the Recommendations section.
   - 5d changes `appendRanked` twice (the shadow column, then the empty sentence) and the hook lines.
4. **`ui/screen/Rows` and `ResultsScreen`.**
   - 5a adds the Mods tab and five tabs a row.
   - 5b adds the Advice tab and wrapping.
   - 5d adds the view, per-row dimensions and headers that fit; its Task 7 replaces `ResultsScreen` whole,
     and the replacement keeps 5a's and 5b's changes.
5. **`hooks/Probe`.**
   - 5a adds `markFired`, the layer, packet, payload and rider entry points, and two caches.
   - 5d renames `hooksApplied` to `hooksFired`.
   - 5c adds the fired-flag save and restore that calibration uses.
6. **`core/Session` and `SessionConfig`.**
   - 5a adds windows, attribution-only calls and named time.
   - 5c counts attribution-only pairs apart and adds a third calibration constant, keeping a six-argument
     `SessionConfig` constructor so every earlier test still compiles.
7. **The README and the two specs.** Every plan's documentation task edits them, in order. Each quotes its
   find text and stops rather than guessing when it is missing.

## Decisions the user made

Asked while designing phase 5; each is recorded in the spec addendum's §P5.1.

| Decision | Choice |
| --- | --- |
| What phase 5 is for | All three of attribution, readiness and polish, plus a **Recommendations** section that gives players quick wins drawn from their own report |
| What the advice covers | Settings, machines and builds in view, pointers for the pack author, and memory allocation and launch arguments |
| How much the rules know about the pack | **Generic rules only**, none written for this pack |
| How far toward players | **Ready, not rolled out.** No pack change; the pack module entry is written down, not applied; the remote, CI and the release are the user's steps |

## Deviations from the spec addendum

Each plan lists its own at the top, with the reason, and applies them to the spec in its documentation task. In
short:

- **5a.**
  - JSON averages go in `stats`, like every other average.
  - The Mods tab's sort arrow sits on `share`, like the other ranked tabs.
  - Tasks and the frame cap show in the split only when they have time.
  - A dev-only `-Drenderable.autostart` lets a headless dev client produce reports.
  - The rider hook has its own `Probe` entry and `RIDER_TICK` hook.
  - The mods array's members are under `entries`, since `members` is already the count.
- **5b.**
  - Rules are methods in four themed classes.
  - `mod-handlers` has two stutter forms.
  - `hitch-repeat` leaves mod keys to the mod rules.
  - The mod rules skip `minecraft`, `neoforge` and `unknown`.
  - A hitch counts toward a repeat only when the key took a quarter of it and carries no `[gc]` mark.
  - The live-heap figure is read from the non-pause beans.
- **5d.**
  - Three hook lists, not two.
  - Labels are drawn in two batches with a depth per label, since `drawManaged` alone does not batch.
  - The view also keeps the selection and the scroll.
  - Short particle names are for the tables only.
  - A header widens its column by at most a quarter of the name column.
- **5c.**
  - Calibration logs `Session`-alone figures beside the new ones, and keeps the fastest of five rounds.
  - The timed pair is driven through `Probe.beginEntity`.
  - `SessionConfig` keeps a six-argument constructor.
  - The idle protocol runs in the dev client, with the pack's rendering mods, two new run properties and a
    datapack scene.
  - Its numbers count samples with Renderable's code on top. Its allocation control reads the allocation in
    Renderable's own frames.

## What the reviews changed

Every plan had an Opus review against the code and the plans before it. Among what they caught before any code
ran:
- **5a.** Two schema assertions the plan had missed, and a duplicate `members` key in the JSON. Event timing
  now turns itself off on a runtime failure instead of crashing the client. Payload accumulators are keyed by
  id, so a mod that builds a new payload type per packet does not allocate while sampling.
- **5b.** The live heap read 0 under ZGC and Shenandoah. `hitch-repeat` fired on steady costs. GC-suspect
  contributors were blamed on code. `gc-pauses` fired on missing data. The cut at six was untested.
- **5a again, found by the 5d and 5c reviews.** Bus 8.0.5 seals `EventListener`, so `TimedListener` extends
  `GeneratedEventListener`, and 5c's calibration delegate is a `ConsumerEventHandler`.
- **5d.**
  - The natural name order broke the comparator contract around "-0", and a long sort could have thrown.
  - The injector table's test now also refuses injector kinds the check does not read.
  - "Fired" is described as fired while sampling.
  - The Flywheel sentence appears only when nothing was alive.
- **5c.**
  - `-PwithoutRenderable` would still have loaded a class-less mod from the classpath, so it now also takes the
    mod's output off it.
  - The log check matched no line.
  - The numbers script could not see merged mixin handlers.
  - One calibration round timed the JIT rather than the calls.
  - Calibration could crash the client from its tick listener, and now logs and charges nothing instead.

## Items deferred, and why

The spec addendum's §P5.4 lists them in full.
- **Other mods' mixins into vanilla rendering.** Distant Horizons' LOD pass and Sodium's terrain draw stay in
  other render. A `terrain` bucket is a candidate for later.
- **Other tick.** No finer split yet; the verified call sites are recorded for later.
- **No sampling profiler.** spark stays the tool for code Renderable does not hook.
- **Out of scope.** Pack-specific rules, rules that change anything, Highlight or Teleport from an Advice
  row, and timing network-thread payload handlers.

## What cannot be proved on this machine

1. **Event timing in the real pack.** Unit tests run against a real `net.neoforged:bus` 8.0.5, and the dev
   client runs Sodium, Iris and Create. Only the pack has 386 mods' listeners. 5c's in-pack acceptance checks
   the Mods tab and that no list keeps a proxy after a session.
2. **The idle cost.** It needs the game in the foreground for four recordings of 120 s each, so the user
   runs it (5c Task 7). Its control run has to separate from its baseline for the result to count.
3. **Everything on screen.** D6 to D9, the Advice tab's wording and the overlay are checked by eye in the
   pack (5c Task 8).
4. **CI.** It still needs a remote, which is the user's step (§P5.C7).

## What runs without a person, and what the user runs

**Without a person.** Each plan's last code task checks it in the dev client, and a subagent can run it:
- 5a Task 11: mixin export, and sessions uncapped and at 30 fps;
- 5b Task 9: a G1 run and a ZGC run;
- 5d Task 9: the hook lists, with and without the pack's rendering mods;
- 5c Task 6: calibration, the player label, the run without the mod and a short recording.

5a's `-Drenderable.autostart` makes this possible.

**The user's steps:**
- **5c Task 7: the idle-cost runs.** Four dev-client launches following `docs/acceptance/phase-5-idle.md`,
  about fifteen minutes.
- **5c Task 8: the in-pack acceptance.** One sitting in the pack with the 0.5.0 jar, following the 24-item
  checklist in that task: seven short sessions and a few config checks. Screenshots are wanted where a check
  is visual.
- **5c Task 9: the go-ahead** to fast-forward `main` and tag `v0.5.0`. It needs the YubiKey, as every commit
  and tag does.
- **After phase 5.** The remote, CI on a real runner (plan 4d Task 7) and the first release.
