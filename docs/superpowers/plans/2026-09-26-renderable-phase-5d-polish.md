# Renderable Phase 5d Implementation Plan: polish

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nine small corrections from phase 4's deferred list and the 4b/4c playtest: the report and the screen say
exactly why a table is empty, name columns sort the way people read them, particle names are short, the shadow
column goes when it is empty, the screen remembers the reader's place, Highlight and Teleport check each row's
dimension, the overlay's labels cost two draws instead of 150, and the `render us/f` header fits.

**Architecture:**

- **Installed versus fired (D1).** `RenderableMixinPlugin.postApply` reads the mixin's injector annotations
  (`InjectionCheck`, pure ASM) and checks each injector's target method and call in the class it was just
  applied to. `HookInstalls` maps each injector to its `Hook` and records which hooks are installed and which
  did not apply. The environment then carries three hook lists: installed, fired and failed.
- **Empty states (D2)** read those lists through `Environment.state(Hook)`.
- **Shared table code in `core`:** `NaturalOrder` (D5), `SimpleNames` and `MeshTable` (D3, D4).
- **The screen.** `ResultsView`, owned by `ProfilerController`, holds the tab, the sorts, the open groups, the
  selection and the scroll (D6). Rows report their dimension through `ProfileKey.dimension()` (D7). The
  overlay draws labels in two batches (D8), and `Tab.Columns.layout` widens a column for its header (D9).

**Tech Stack:** Java 21, NeoForge 21.1.220, Mixin 0.8.7 (`net.fabricmc:sponge-mixin:0.15.2+mixin.0.8.7`) with
MixinExtras 0.5.0, ASM 9.8 (`asm-tree`, now also on the test classpath), JUnit 5, Gson.

**Spec:** `docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md` §P5.D1 to §P5.D9, and the base spec
sections §P5.3 assigns to plan 5d (§4.5; §5.3's remembered view; §6.1's particle names and shadow column; §6.2).

**Repository:** `H:\repos\Renderable`. Create the branch `phase-5d-polish` from `phase-5b-advice` (plans 5a and 5b
are complete on it) before Task 1. **Subagents stage and report; they do not commit.**

**Ordering:** after 5b (spec addendum §P5.2). D1 renames a field that 5a's schema 5 introduced, and D6 opens the
screen on 5b's Advice tab. Plan 5c follows this one.

**Verified facts you can rely on** (checked 2026-09-26):

- **When MixinExtras applies its injectors** (javap of `mixinextras-neoforge-0.5.0.jar`, which NeoForge 21.1.220
  bundles):
  - `WrapOperationInjectionInfo` extends `MixinExtrasLateInjectionInfo`: a late injection, applied after all the
    class's mixins.
  - `WrapMethodInjector.inject` only calls `WrapMethodApplicatorExtension.offerWrapper`. The rewrite happens in
    that extension's `postApply(ITargetClassContext)`.
  - Mixin calls each plugin's `IMixinConfigPlugin.postApply` from `MixinApplicatorStandard.apply`, and runs the
    extensions' `IExtension.postApply` as the last step of `TargetClassContext.applyMixins`, after that (javap).
  - So when our plugin's `postApply` runs, every target method still has its original body and its original
    calls. The exception is a call that another mod's `@Redirect` replaced.
  - `@ModifyReturnValue`, `@ModifyVariable` and `@Inject` have already applied by then. None of them removes the
    call it targets.
- **Mixin API.**
  - `IMixinInfo.getClassNode(int flags)` returns a fresh tree of the mixin class.
  - Injector annotations are runtime-visible: javap of our compiled `ParticleEngineMixin` shows
    `RuntimeVisibleAnnotations` holding `WrapOperation(method=["tickParticle"], at=[@At(value="INVOKE", target=...)])`.
  - `@ModifyVariable`'s `at` is a single annotation, not a list.
- **ASM 9.8** is what NeoForge resolves: `./gradlew dependencies --configuration runtimeClasspath` shows
  `org.ow2.asm:asm-tree:{strictly 9.8}`. `InsnList` is `Iterable<AbstractInsnNode>`.
- **GUI batching** (javap of `neoforge-21.1.220-merged.jar`):
  - `MultiBufferSource.BufferSource.getBuffer` gives a render type without a fixed buffer the shared buffer,
    and asking for a different such type ends the previous one's batch.
  - `RenderBuffers`' fixed buffers are block, sheet, glint, water-mask and crumbling types, so `RenderType.gui()`
    (every `fill`) and the text types share one buffer.
  - `GuiGraphics.drawManaged(Runnable)` only suppresses the flush after each `fill` and `drawString`.
  - So labels drawn background, text, background, text... end a batch at every switch, managed or not.
- **Font and light.** `Font.drawInBatch(String, float, float, int, boolean, Matrix4f, MultiBufferSource,
  Font.DisplayMode, int, int)` and `LightTexture.FULL_BRIGHT` exist in 21.1.220. `GuiGraphics.drawString` is that
  call with the pose's matrix, `DisplayMode.NORMAL`, no background and full light.
- **GUI depth.** Higher GUI z draws on top: vanilla translates tooltips to z 400. `RenderType.gui()` and the
  text types test depth with `LEQUAL` and write it.
- **The vanilla font's advances.** Most lower-case letters are 6 px. The narrower ones are `i` 2, `l` 3, `t` 4,
  and `f` and `k` 5. A space is 4 and `/` is 6. So `render us/f` is 63 px, as the spec measured, and the sort
  arrow `" v"` is 10.
- **`Screen.removed()`** runs when a screen is closed or replaced. A resize and `rebuildWidgets()` call `init()`
  again without it.
- **After 5b:**
  - `Tab` has ADVICE, FRAMES, HITCHES, MODS, BLOCK_ENTITIES, ENTITIES, PARTICLES, FLYWHEEL, BLOCKS, SECTIONS;
  - `ResultsScreen` has `TABS_PER_ROW = 5` and `private Tab tab = Tab.ADVICE;`;
  - `Rows.build` takes a seventh parameter, the wrap function;
  - `Environment` has 5a's `EVENT_TIMING` and `UNAVAILABLE` and 5b's five memory keys;
  - `EmptyStates` has 5a's `mods`/`modsHandlersMissing` and 5b's `advice`;
  - `TextReportWriter` has 5a's `appendMods` and 5b's `appendRecommendations`;
  - `Hook` has 5a's TASKS, FRAME_CAP, HUD, HUD_LAYERS, PACKETS, PAYLOADS, RIDER_TICK and EVENTS;
  - `Probe` has 5a's `markFired`;
  - `renderable.mixins.json` lists 5a's `MinecraftMixin`, `GuiMixin`, `GuiLayerManagerMixin`, `PacketUtilsMixin`,
    `MainThreadPayloadHandlerMixin` and `ClientLevelMixin`.
- **The suite is 190 tests** after 5b.

**Deviations from the spec addendum, recorded here and applied to it in Task 8:**

1. **Three hook lists, not two.** §P5.D1 says the environment carries `hooksInstalled` and `hooksFired`. It also
   carries `hooksFailed`, the hooks with an injector whose target was missing. Without it, "the hook did not
   apply" cannot be told from "the code never loaded", which §P5.D1's own three cases need. A failed injector
   outweighs a firing, so a hook in both lists applied only in part, for example one of Sodium's three mesh
   hooks after an update.
2. **Labels are drawn in two passes, not only inside `drawManaged`.** Managed or not, a background followed by
   text ends a batch every time (verified facts above), so 75 labels would still cost 150 draws.
   - All backgrounds go into one batch and all text into another.
   - Each label is drawn at its own depth, so where two overlap the later one covers the earlier one's text.
     Drawn in turn, the later label's translucent background only dimmed that text; now the depth test hides
     it where they overlap, which reads more cleanly.
   - The overlay writes to the buffer source directly, as `drawHighlight` already does, rather than passing
     `drawManaged` a lambda that would allocate every frame.
3. **The view also keeps the selected row and the scroll position.** §P5.D6 names the tab, the sorts and the open
   groups. The reader's place after Highlight is also the row they highlighted and where the list was.
4. **Short particle names are for the particle tables only.** A hitch's contributor lines and the
   recommendations keep each key's own label, as they do for every other kind of key; a modded class's full
   name also says which mod it is from. The JSON keeps full names everywhere.
5. **A header widens its column only as far as a quarter of the name column.** §P5.D9 says the column widens
   until the header fits.
   - The column gets room for the sort arrow too, when it sorts, because a sorted header is the one the reader
     needs.
   - A right-aligned header may reach 3 px left, as a number may.
   - The name column gives at most a quarter of its width, to the smallest shortfalls first. At the Auto GUI
     scale on 1440p, where every header is short of room, as many as possible then fit whole, and names do not
     shrink by more than a quarter.
   - At GUI scale 3 on a 2560 px window, the case the spec measured, `render us/f` gets its 10 px and nothing
     else moves.

**Decisions the spec left open:**

- **Empty-state sentences.** The ranked tables say "(nothing of this kind was drawn or ticked)" or "(hook not
  installed)", as §P5.D2 says. Particles keep "(no particles were rendered)". Sodium's Blocks and Sections tables
  tell four cases apart:
  - "(no data: Sodium is not installed)";
  - "(no data: Sodium's mesh hook did not apply)";
  - "(no data: Sodium's mesh hook never loaded; the game log says why)": Sodium is present but the hooked class
    never loaded, which in practice means Mixin rejected the whole hook class;
  - "(no data: no blocks were meshed)" or "(no data: no sections were rebuilt)".
- **The Flywheel sentence** is "(Flywheel sampled 3 times and found no visuals alive)", or "sampled once".
- **Natural order and minus signs.** A minus sign counts as a number's sign only at the start of a name or after
  a space. So `section -10 4 0` sorts before `section -2 4 0`, while `mod-2` still sorts before `mod-10`.

---

## File map

```
gradle.properties                                             Modify: asm_version (Task 3)
build.gradle                                                  Modify: asm-tree on the test classpath (Task 3)
CLAUDE.md                                                     Modify: the injector table rule (Task 8)
README.md                                                     Modify (Task 8)
docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md   Modify (Task 8)
docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md        Modify: deviations above (Task 8)
src/main/java/io/github/edwardg117/renderable/
  core/NaturalOrder.java                                      Create (Task 1)
  core/Ranking.java                                           Modify: natural LABEL sort (Task 1), particles (Task 2)
  core/SimpleNames.java                                       Create (Task 2)
  core/MeshTable.java                                         Create (Task 2)
  core/FlywheelStats.java                                     Modify: displayNames through SimpleNames (Task 2)
  core/RankedEntry.java                                       Modify: withLabel (Task 2)
  core/RankedTable.java                                       Modify: the table without shadow (Task 2)
  core/Environment.java                                       Modify: three hook lists, HookState (Task 4)
  core/EmptyStates.java                                       Modify: the sentences (Task 4)
  core/ProfileKey.java                                        Modify: dimension() (Task 5)
  hooks/InjectionCheck.java                                   Create (Task 3)
  hooks/HookInstalls.java                                     Create (Task 3)
  hooks/RenderableMixinPlugin.java                            Modify: postApply (Task 3)
  hooks/Probe.java                                            Modify: hooksFired (Task 4)
  hooks/FrameEvents.java                                      Modify: world gone on logout only (Task 7)
  report/EnvironmentCollector.java                            Modify: the three lists (Task 4)
  report/TextReportWriter.java                                Modify (Tasks 2, 4)
  report/JsonReportWriter.java                                Modify: the three lists (Task 4)
  ProfilerController.java                                     Modify (Tasks 4, 7)
  ui/WorldOverlay.java                                        Replace (Task 6)
  ui/screen/Rows.java                                         Modify (Tasks 1, 2, 4)
  ui/screen/Tab.java                                          Modify: layout for headers (Task 5)
  ui/screen/ResultsView.java                                  Create (Task 7)
  ui/screen/ResultsScreen.java                                Modify: highlight calls (Task 6); replace (Task 7)
src/test/java/io/github/edwardg117/renderable/
  core/NaturalOrderTest.java                                  Create (Task 1)
  core/RankingTest.java                                       Modify (Tasks 1, 2, 4)
  core/SimpleNamesTest.java                                   Create (Task 2)
  core/MeshTableTest.java                                     Create (Task 2)
  core/RankedTableTest.java                                   Modify (Task 2)
  core/EnvironmentTest.java                                   Modify (Task 4)
  core/EmptyStatesTest.java                                   Modify (Task 4)
  core/KeysTest.java                                          Modify (Task 5)
  hooks/InjectionCheckTest.java                               Create (Task 3)
  hooks/HookInstallsTest.java                                 Create (Task 3)
  report/TextReportWriterTest.java                            Modify (Tasks 2, 4)
  report/JsonReportWriterTest.java                            Modify (Task 4)
  ui/screen/TabTest.java                                      Modify (Tasks 2, 5)
```

**Expected test totals:** 190 at the start, 193 after Task 1, 198 after Task 2, 203 after Task 3, 205 after
Task 4, 207 after Task 5, and 207 at the end. Tasks 6 and 7 are Minecraft-side and are checked in the dev client
(Task 9) and the in-pack acceptance.

**How to run the suite:** `./gradlew test --rerun --no-daemon` (always forced; the build cache can restore stale
results).

---

### Task 1: Natural, case-insensitive name order

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/NaturalOrder.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Ranking.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java` (the Flywheel name sort)
- Test: `src/test/java/io/github/edwardg117/renderable/core/NaturalOrderTest.java` (2)
- Test: `src/test/java/io/github/edwardg117/renderable/core/RankingTest.java` (append 1)

Spec addendum §P5.D5. Only the `LABEL` sorts change. The tie-break stays the plain label comparison, so two names
that differ only in case keep a fixed order, and the report, which never sorts by name, is unaffected.

- [ ] **Step 1: Write the failing tests**

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NaturalOrderTest {

    static List<String> sorted(String... names) {
        List<String> out = new ArrayList<>(List.of(names));
        out.sort(NaturalOrder.INSTANCE);
        return out;
    }

    @Test
    void digitRunsCompareAsNumbersWithTheirSign() {
        assertEquals(List.of("section -10 4 0", "section -2 4 0", "section -1 4 0", "section 0 4 0", "section 2 4 0",
                        "section 10 4 0"),
                sorted("section 2 4 0", "section -1 4 0", "section 10 4 0", "section -10 4 0", "section 0 4 0",
                        "section -2 4 0"));
        assertEquals(List.of("create:press @ 5 64 -20", "create:press @ 40 64 -20", "create:press @ 100 64 -20"),
                sorted("create:press @ 100 64 -20", "create:press @ 5 64 -20", "create:press @ 40 64 -20"));
        assertEquals(List.of("mod-2", "mod-10"), sorted("mod-10", "mod-2"), "a hyphen inside a word is not a sign");
        assertEquals(List.of("x 99999999999999999999", "x 100000000000000000000"),
                sorted("x 100000000000000000000", "x 99999999999999999999"), "longer than a long");
        assertEquals(0, NaturalOrder.INSTANCE.compare("a 007", "a 7"), "leading zeros do not count");
        assertTrue(NaturalOrder.INSTANCE.compare("a -0", "a 0") < 0,
                "a signed zero sorts with the negatives, which keeps the order transitive");
    }

    @Test
    void caseIsIgnoredAndATieIsLeftToTheCaller() {
        assertEquals(List.of("apple", "Cow", "cow", "Zombie"), sorted("Zombie", "Cow", "apple", "cow"),
                "Cow and cow compare equal, so the stable sort keeps their given order");
        assertEquals(0, NaturalOrder.INSTANCE.compare("Cow", "cow"));
        assertTrue(NaturalOrder.INSTANCE.compare("cow", "cows") < 0, "a prefix comes first");
    }
}
```

Append to `RankingTest`:

```java
    @Test
    void theLabelSortReadsNumbersAsNumbersAndIgnoresCase() {
        List<RankedEntry> rows = List.of(named("section -1 4 0"), named("Section -10 4 0"), named("section -2 4 0"));
        assertEquals(List.of("Section -10 4 0", "section -2 4 0", "section -1 4 0"),
                Ranking.sort(rows, Ranking.Sort.LABEL, true).stream().map(RankedEntry::label).toList());

        SessionResult base = TestSessions.phase3();
        List<MeshEntry> sections = List.of(
                new MeshEntry(new SectionKey("minecraft:overworld", -1, 4, 0), 1, MS),
                new MeshEntry(new SectionKey("minecraft:overworld", -10, 4, 0), 1, MS),
                new MeshEntry(new SectionKey("minecraft:overworld", -2, 4, 0), 1, MS));
        SessionResult r = new SessionResult(base.config(), base.wallStartMillis(), base.frames(), base.accumulators(),
                base.stats(), base.hitchThresholdNanos(), base.hitches(), base.timedCalls(), base.vertexCalls(),
                base.selfNanos(), base.overheadEstimateMs(), base.unbalancedFrames(), base.flywheel(), sections);
        assertEquals(List.of("section -10 4 0", "section -2 4 0", "section -1 4 0"),
                Ranking.mesh(r, ProfileKey.Category.SECTION, Ranking.MeshSort.LABEL, true).stream()
                        .map(e -> e.key().label()).toList());
    }

    private static RankedEntry named(String label) {
        return new RankedEntry(label, null, label, ProfileKey.Category.BLOCK_ENTITY, 1, 0, 0, 0, 0, 0, 0, 0, 0);
    }
```

`RankingTest` already has `import static io.github.edwardg117.renderable.core.TestSessions.*;`, which gives it `MS`.

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*NaturalOrderTest' --tests '*RankingTest'`
Expected: compilation failure, `cannot find symbol: variable NaturalOrder`.

- [ ] **Step 3: Create `NaturalOrder`**

```java
package io.github.edwardg117.renderable.core;

import java.util.Comparator;

/**
 * The order the results screen sorts names in (spec addendum §P5.D5). Case is ignored, and a run of digits
 * compares as a number, so {@code section -10 4 0} comes before {@code section -2 4 0}. A minus sign is a
 * number's sign only at the start of a name or after a space, so {@code mod-2} still comes before
 * {@code mod-10}. Names that differ only in case compare equal: the caller breaks the tie.
 */
public final class NaturalOrder implements Comparator<String> {
    public static final NaturalOrder INSTANCE = new NaturalOrder();

    private NaturalOrder() {
    }

    @Override
    public int compare(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            int na = numberStart(a, i);
            int nb = numberStart(b, j);
            if (na >= 0 && nb >= 0) {
                int ea = digitsEnd(a, na);
                int eb = digitsEnd(b, nb);
                int c = compareNumbers(a, na, ea, na > i, b, nb, eb, nb > j);
                if (c != 0) return c;
                i = ea;
                j = eb;
            } else {
                int c = Character.compare(Character.toLowerCase(a.charAt(i)), Character.toLowerCase(b.charAt(j)));
                if (c != 0) return c;
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    /** Where the digits of a number starting at {@code k} begin, past its sign, or -1 when none starts there. */
    private static int numberStart(String s, int k) {
        char c = s.charAt(k);
        if (isDigit(c)) return k;
        boolean sign = c == '-' && (k == 0 || s.charAt(k - 1) == ' ');
        return sign && k + 1 < s.length() && isDigit(s.charAt(k + 1)) ? k + 1 : -1;
    }

    private static int digitsEnd(String s, int k) {
        while (k < s.length() && isDigit(s.charAt(k))) k++;
        return k;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * Compares two digit runs as signed numbers of any length, without parsing them. A minus sign counts even on a
     * zero: a negative number meets a non-number by its {@code -}, and a zero by its {@code 0}, so treating
     * {@code -0} as {@code 0} would make the order intransitive around {@code .} and {@code /}, and a long sort
     * could throw.
     */
    private static int compareNumbers(String a, int sa, int ea, boolean negA, String b, int sb, int eb, boolean negB) {
        while (sa < ea - 1 && a.charAt(sa) == '0') sa++;
        while (sb < eb - 1 && b.charAt(sb) == '0') sb++;
        if (negA != negB) return negA ? -1 : 1;
        int magnitude = Integer.compare(ea - sa, eb - sb);
        for (int k = 0; magnitude == 0 && k < ea - sa; k++) {
            magnitude = Character.compare(a.charAt(sa + k), b.charAt(sb + k));
        }
        return negA ? -magnitude : magnitude;
    }
}
```

- [ ] **Step 4: Sort labels with it**

In `Ranking.java`, in `mesh(SessionResult, ProfileKey.Category, MeshSort, boolean)`, replace:

```java
            case LABEL -> Comparator.comparing(e -> e.key().label());
```

with:

```java
            case LABEL -> Comparator.comparing((MeshEntry e) -> e.key().label(), NaturalOrder.INSTANCE);
```

and in `sort(List, Sort, boolean)`, replace:

```java
            case LABEL -> Comparator.comparing(RankedEntry::label);
```

with:

```java
            case LABEL -> Comparator.comparing(RankedEntry::label, NaturalOrder.INSTANCE);
```

At the end of both methods' Javadoc, add this line:

```java
     * A {@code LABEL} sort compares in {@link NaturalOrder}; ties still go by plain string order (spec addendum §P5.D5).
```

Leave both `thenComparing` tie-breaks as they are.

In `Rows.java`, add `import io.github.edwardg117.renderable.core.NaturalOrder;` and, in `flywheel`, replace:

```java
                ? Comparator.comparing(e -> names.get(e.getKey()))
```

with:

```java
                ? Comparator.comparing((Map.Entry<String, Double> e) -> names.get(e.getKey()), NaturalOrder.INSTANCE)
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew compileJava --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **193** tests. `RankingTest`'s existing label test still passes: mixer, then the press
at 100, then the press at 103, in natural order as in text order.

- [ ] **Step 6: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/NaturalOrder.java src/main/java/io/github/edwardg117/renderable/core/Ranking.java src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java src/test/java/io/github/edwardg117/renderable/core/NaturalOrderTest.java src/test/java/io/github/edwardg117/renderable/core/RankingTest.java
```

Report for the commit: `feat: name columns sort case-insensitively, with numbers read as numbers`.

---

### Task 2: Shared names and cells: particle names, the shadow column, Blocks and Sections

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/core/SimpleNames.java`
- Create: `src/main/java/io/github/edwardg117/renderable/core/MeshTable.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/FlywheelStats.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/RankedEntry.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Ranking.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/RankedTable.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java`
- Test: `src/test/java/io/github/edwardg117/renderable/core/SimpleNamesTest.java` (1)
- Test: `src/test/java/io/github/edwardg117/renderable/core/MeshTableTest.java` (1)
- Test: `core/RankingTest.java`, `core/RankedTableTest.java`, `report/TextReportWriterTest.java` (append 1 each)
- Test: `src/test/java/io/github/edwardg117/renderable/ui/screen/TabTest.java` (extend)

Spec addendum §P5.D3 and §P5.D4, with deviation 4.
- **Short names.** The naming rule moves from `FlywheelStats` into `SimpleNames`. `Ranking.particles` hands both
  surfaces particle rows under short names, so a sort by name sorts what the reader sees.
- **The shadow column.** The text report leaves it out of a table in which no printed row has shadow time. The
  screen and the JSON keep it.
- **Blocks and Sections cells** move into `MeshTable`. The only difference between the two surfaces stays the
  Sections `avg ms/rb` header.

- [ ] **Step 1: Write the failing tests**

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimpleNamesTest {

    @Test
    void namesAreSimpleUnlessTwoCollideInTheTable() {
        List<String> classes = List.of(
                "net.minecraft.client.particle.FlameParticle",
                "com.example.fx.SparkParticle",
                "com.other.fx.SparkParticle",
                "com.example.dust.DustParticle",
                "com.other.smoke.DustParticle",
                "NoPackage");
        Map<String, String> names = SimpleNames.of(classes);
        assertEquals(classes, List.copyOf(names.keySet()), "in the order given");
        assertEquals("FlameParticle", names.get("net.minecraft.client.particle.FlameParticle"));
        assertEquals("com.example.fx.SparkParticle", names.get("com.example.fx.SparkParticle"),
                "the same last segment, so only the full name tells them apart");
        assertEquals("com.other.fx.SparkParticle", names.get("com.other.fx.SparkParticle"));
        assertEquals("dust.DustParticle", names.get("com.example.dust.DustParticle"));
        assertEquals("smoke.DustParticle", names.get("com.other.smoke.DustParticle"));
        assertEquals("NoPackage", names.get("NoPackage"));
    }
}
```

```java
package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MeshTableTest {

    @Test
    void blockAndSectionCellsSitUnderTheirHeaders() {
        SessionResult p3 = TestSessions.phase3();
        double seconds = p3.stats().durationSeconds();

        MeshEntry stone = Ranking.mesh(p3, ProfileKey.Category.BLOCK, Ranking.MeshSort.TIME).get(0);
        assertEquals(List.of("block=minecraft:stone", "mesh ms/s=20.8", "us/call=20.0", "calls=400"),
                pairs(MeshTable.BLOCK, MeshTable.block(stone, seconds)));

        MeshEntry section = Ranking.mesh(p3, ProfileKey.Category.SECTION, Ranking.MeshSort.CALLS).get(0);
        assertEquals(List.of("section=section 0 4 0", "rebuilds=3", "rebuilds/s=7.8", "mesh ms=9.0", "avg ms/rebuild=3.00"),
                pairs(MeshTable.SECTION, MeshTable.section(section, seconds)));

        assertEquals(MeshTable.SECTION.subList(0, 4), MeshTable.SECTION_SCREEN.subList(0, 4),
                "the screen differs only in the last header, which it shortens to fit (spec addendum §P4.C6 #1)");
        assertEquals("avg ms/rb", MeshTable.SECTION_SCREEN.get(4));
    }

    private static List<String> pairs(List<String> headers, String[] cells) {
        assertEquals(headers.size(), cells.length, "one cell per header");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < cells.length; i++) out.add(headers.get(i) + "=" + cells[i]);
        return out;
    }
}
```

Append to `RankingTest`:

```java
    @Test
    void particleRowsAreNamedByTheirShortClassName() {
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> now[0], WALL_START);
        Accumulator spark = s.accumulator(new ParticleKey("com.example.fx.SparkParticle"));
        Accumulator flame = s.accumulator(FLAME);
        s.onRenderPre(0, 0);
        now[0] = 1 * MS;
        s.begin(spark, Bucket.PARTICLE_RENDER);
        now[0] = 3 * MS;
        s.end();
        s.begin(flame, Bucket.PARTICLE_RENDER);
        now[0] = 4 * MS;
        s.end();
        s.onRenderPost();
        now[0] = 16 * MS;
        s.onRenderPre(0, 0);
        SessionResult r = s.finish();

        assertEquals(List.of("SparkParticle", "FlameParticle"),
                Ranking.particles(r).stream().map(RankedEntry::label).toList(), "most expensive first, short names");
        assertEquals("com.example.fx.SparkParticle", Ranking.byKey(r, ProfileKey.Category.PARTICLE).get(0).label(),
                "byKey, which the JSON writes, keeps the key's own label");
    }
```

Append to `RankedTableTest`:

```java
    @Test
    void theShadowColumnCanBeLeftOut() {
        SessionResult p3 = TestSessions.phase3();
        RankedEntry press = Ranking.byKey(p3, ProfileKey.Category.BLOCK_ENTITY).get(0);
        assertEquals(List.of("name=row name", "render us/f=4900.0", "tick us/f=400.0", "share=27.6%", "peak ms=60.40",
                        "gc f=0", "verts/f=100.0", "calls=20"),
                pairs(RankedTable.RANKED_WITHOUT_SHADOW, RankedTable.withoutShadow(
                        RankedTable.ranked("row name", press, p3.frameCount(), p3.stats().totalNanos()))));
    }
```

Append to `TextReportWriterTest`, adding `import io.github.edwardg117.renderable.core.Accumulator;`,
`import io.github.edwardg117.renderable.core.Bucket;`, `import io.github.edwardg117.renderable.core.ParticleKey;`,
`import io.github.edwardg117.renderable.core.Session;` and `import io.github.edwardg117.renderable.core.SessionConfig;`
where they are not already there:

```java
    @Test
    void theShadowColumnIsLeftOutUnlessARowHasShadowTimeAndParticlesGoByShortName() {
        String plain = TextReportWriter.write(r, env, "0.5.0");
        String header = plain.substring(plain.indexOf("Block entities by type (top 20)\n")).split("\n", 3)[1];
        assertFalse(header.contains("shadow"), "no shader pack, no shadow time: " + header);
        assertTrue(header.endsWith("gc f  verts/f  calls"), header);

        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000_000_000L, 2.0, 16_000_000L, 5, 0, 0), () -> now[0], 0);
        Accumulator press = s.accumulator(TestSessions.PRESS);
        Accumulator spark = s.accumulator(new ParticleKey("com.example.fx.SparkParticle"));
        s.onRenderPre(0, 0);
        s.shadowPass = true;
        now[0] = 1_000_000;
        s.begin(press, Bucket.BLOCK_ENTITY);
        now[0] = 3_000_000;
        s.end();
        s.shadowPass = false;
        s.begin(spark, Bucket.PARTICLE_RENDER);
        now[0] = 4_000_000;
        s.end();
        s.onRenderPost();
        now[0] = 16_000_000;
        s.onRenderPre(0, 0);
        String shadowed = TextReportWriter.write(s.finish(), env, "0.5.0");
        String shadowHeader = shadowed.substring(shadowed.indexOf("Block entities by type (top 20)\n")).split("\n", 3)[1];
        assertTrue(shadowHeader.contains("gc f  shadow  verts/f"), shadowHeader);
        // Only the table: a recommendation names the class in full, which says which mod it is from.
        String particles = shadowed.substring(shadowed.indexOf("Particles (top 20)\n"));
        particles = particles.substring(0, particles.indexOf("\n\n"));
        assertTrue(particles.contains("\n  SparkParticle "), particles);
        assertFalse(particles.contains("com.example.fx.SparkParticle"), "the table's short name: " + particles);
    }
```

In `TabTest.theRankedTabsShowTheReportsColumnsAndEveryTabOpensInTheReportsOrder`, after
`assertEquals(RankedTable.PARTICLE, names(Tab.PARTICLES));`, add:

```java
        assertEquals(MeshTable.BLOCK, names(Tab.BLOCKS));
        assertEquals(MeshTable.SECTION_SCREEN, names(Tab.SECTIONS));
```

and add `import io.github.edwardg117.renderable.core.MeshTable;`.

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon`
Expected: compilation failure (`SimpleNames`, `MeshTable`, `Ranking.particles`, `RankedTable.withoutShadow` do not
exist).

- [ ] **Step 3: Create `SimpleNames` and move `FlywheelStats` onto it**

```java
package io.github.edwardg117.renderable.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Short names for fully-qualified class names that stay unambiguous within one table (spec addendum §P4.C6 and
 * §P5.D4): the simple name; the last package segment plus the simple name when two classes in the table share a
 * simple name; the full name when that still leaves two alike. The screen and the text report show these; the
 * JSON keeps the full names, which are the machine record.
 */
public final class SimpleNames {

    private SimpleNames() {
    }

    /** Each of {@code names} mapped to its short name, in the order given. */
    public static Map<String, String> of(Collection<String> names) {
        Map<String, Integer> bySimple = new HashMap<>();
        Map<String, Integer> byQualified = new HashMap<>();
        for (String fqn : names) {
            bySimple.merge(simpleName(fqn), 1, Integer::sum);
            byQualified.merge(qualifiedName(fqn), 1, Integer::sum);
        }
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (String fqn : names) {
            String simple = simpleName(fqn);
            String qualified = qualifiedName(fqn);
            // One package segment tells most collisions apart; when it does not, only the full name can.
            out.put(fqn, bySimple.get(simple) == 1 ? simple : byQualified.get(qualified) == 1 ? qualified : fqn);
        }
        return out;
    }

    private static String simpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    /** The last package segment plus the simple name, such as {@code press.PressVisual}. */
    private static String qualifiedName(String fqn) {
        int last = fqn.lastIndexOf('.');
        if (last < 0) return fqn;
        int previous = fqn.lastIndexOf('.', last - 1);
        return previous < 0 ? fqn : fqn.substring(previous + 1);
    }
}
```

In `FlywheelStats.java`, replace the body of `displayNames()` with:

```java
        return SimpleNames.of(byAverageDesc().stream().map(Map.Entry::getKey).toList());
```

delete the two private helpers `simpleName` and `qualifiedName` below it, and delete the imports
`java.util.HashMap` and `java.util.LinkedHashMap`, which nothing else in the file uses. Keep `displayNames()`'s
Javadoc, and add at its end: "The rule is {@link SimpleNames}'s, which the particle tables share."
`FlywheelStatsTest` keeps passing unchanged: it is the same rule over the same order.

- [ ] **Step 4: Relabelled particle rows**

In `RankedEntry.java`, after `of(Accumulator)`, add:

```java
    /** This row under another label, for a table that names its rows differently (spec addendum §P5.D4). */
    public RankedEntry withLabel(String newLabel) {
        return new RankedEntry(newLabel, key, typeId, category, members, calls, tickCalls, renderNanos, tickNanos,
                shadowNanos, maxFrameNanos, gcFrames, vertices);
    }
```

In `Ranking.java`, after `byKey`, add:

```java
    /**
     * The particle table's rows, most expensive first, each labelled with its class's short name, as the Flywheel
     * table names its classes (spec addendum §P5.D4). The JSON uses {@link #byKey}, which keeps the full names.
     */
    public static List<RankedEntry> particles(SessionResult r) {
        List<RankedEntry> rows = byKey(r, ProfileKey.Category.PARTICLE);
        Map<String, String> names = SimpleNames.of(rows.stream().map(RankedEntry::typeId).toList());
        List<RankedEntry> out = new ArrayList<>(rows.size());
        for (RankedEntry e : rows) out.add(e.withLabel(names.get(e.typeId())));
        return out;
    }
```

`Ranking.java` already imports `ArrayList`, `List` and `Map`.

- [ ] **Step 5: The table without shadow, and `MeshTable`**

In `RankedTable.java`, after `PARTICLE`, add:

```java
    /**
     * {@link #RANKED} without the shadow column: the text report's header for a table in which no row has shadow
     * time, as with no shader pack on (spec addendum §P5.D4). The screen and the JSON always keep the column.
     */
    public static final List<String> RANKED_WITHOUT_SHADOW = RANKED.stream().filter(h -> !h.equals("shadow")).toList();
```

and after `ranked(...)`, add:

```java
    /** A {@link #ranked} row without its shadow cell, to go under {@link #RANKED_WITHOUT_SHADOW}. */
    public static String[] withoutShadow(String[] cells) {
        int shadow = RANKED.indexOf("shadow");
        String[] out = new String[cells.length - 1];
        System.arraycopy(cells, 0, out, 0, shadow);
        System.arraycopy(cells, shadow + 1, out, shadow, cells.length - shadow - 1);
        return out;
    }
```

Create `MeshTable.java`:

```java
package io.github.edwardg117.renderable.core;

import java.util.List;

/**
 * The columns of the Blocks and Sections tables (spec addendum §P5.D3): their names and the text of each cell,
 * written once for the text report and the results screen, as {@link RankedTable} is for the ranked tables. The
 * screen writes its headers out in {@code Tab}, and {@code TabTest} holds them equal to these.
 */
public final class MeshTable {

    /** Headers of the Blocks table. */
    public static final List<String> BLOCK = List.of("block", "mesh ms/s", "us/call", "calls");

    /** Headers of the report's Sections table. */
    public static final List<String> SECTION = List.of("section", "rebuilds", "rebuilds/s", "mesh ms", "avg ms/rebuild");

    /**
     * Headers of the screen's Sections table. The only difference between the surfaces: the last is shortened to
     * fit its column at the default GUI scale (spec addendum §P4.C6 #1).
     */
    public static final List<String> SECTION_SCREEN = List.of("section", "rebuilds", "rebuilds/s", "mesh ms", "avg ms/rb");

    private MeshTable() {
    }

    /** One row of the Blocks table; {@code seconds} is the session's sampled length. */
    public static String[] block(MeshEntry e, double seconds) {
        return new String[]{e.key().label(), Fmt.num(e.msPerSecond(seconds)), Fmt.us(e.usPerCall()), Fmt.count(e.calls())};
    }

    /** One row of the Sections table. */
    public static String[] section(MeshEntry e, double seconds) {
        return new String[]{e.key().label(), Fmt.count(e.calls()), Fmt.num(e.callsPerSecond(seconds)), Fmt.ms(e.nanos()),
                Fmt.ms2(e.calls() == 0 ? 0 : e.nanos() / e.calls())};
    }
}
```

- [ ] **Step 6: Use them in the report**

In `TextReportWriter.java`, add `import io.github.edwardg117.renderable.core.MeshTable;`.

Replace the body of `appendRanked` from `Table t = Table.nameThenNumbers(RankedTable.RANKED);` to the end of its
`for` loop:

```java
        Table t = Table.nameThenNumbers(RankedTable.RANKED);
        for (RankedEntry e : entries.subList(0, Math.min(TOP, entries.size()))) {
            t.row((Object[]) RankedTable.ranked(e.label(), e, frames, total));
        }
```

with:

```java
        // Without shaders the shadow column is a column of zeros, so a table with no shadow time leaves it out
        // (spec addendum §P5.D4); the screen and the JSON keep it.
        List<RankedEntry> shown = entries.subList(0, Math.min(TOP, entries.size()));
        boolean shadow = shown.stream().anyMatch(e -> e.shadowNanos() > 0);
        Table t = Table.nameThenNumbers(shadow ? RankedTable.RANKED : RankedTable.RANKED_WITHOUT_SHADOW);
        for (RankedEntry e : shown) {
            String[] cells = RankedTable.ranked(e.label(), e, frames, total);
            t.row((Object[]) (shadow ? cells : RankedTable.withoutShadow(cells)));
        }
```

In `appendParticles`, replace `List<RankedEntry> entries = Ranking.byKey(r, ProfileKey.Category.PARTICLE);` with
`List<RankedEntry> entries = Ranking.particles(r);`.

In `appendBlocks`, replace:

```java
        Table t = new Table(new String[]{"block", "mesh ms/s", "us/call", "calls"}, new boolean[]{false, true, true, true});
        for (MeshEntry e : rows.subList(0, Math.min(TOP, rows.size()))) {
            t.row(e.key().label(), Fmt.num(e.msPerSecond(seconds)), Fmt.us(e.usPerCall()), Fmt.count(e.calls()));
        }
```

with:

```java
        Table t = Table.nameThenNumbers(MeshTable.BLOCK);
        for (MeshEntry e : rows.subList(0, Math.min(TOP, rows.size()))) {
            t.row((Object[]) MeshTable.block(e, seconds));
        }
```

In `appendSections`, replace:

```java
        Table t = new Table(new String[]{"section", "rebuilds", "rebuilds/s", "mesh ms", "avg ms/rebuild"},
                new boolean[]{false, true, true, true, true});
        for (MeshEntry e : rows.subList(0, Math.min(TOP, rows.size()))) {
            t.row(e.key().label(), Fmt.count(e.calls()), Fmt.num(e.callsPerSecond(seconds)), Fmt.ms(e.nanos()),
                    Fmt.ms2(e.calls() == 0 ? 0 : e.nanos() / e.calls()));
        }
```

with:

```java
        Table t = Table.nameThenNumbers(MeshTable.SECTION);
        for (MeshEntry e : rows.subList(0, Math.min(TOP, rows.size()))) {
            t.row((Object[]) MeshTable.section(e, seconds));
        }
```

`Table.nameThenNumbers` aligns the first column left and the rest right, as the two `boolean` arrays did, so
`phase3MeshSections`' exact header lines still match.

- [ ] **Step 7: Use them on the screen**

In `Rows.java`, add `import io.github.edwardg117.renderable.core.MeshTable;`.

In `particles`, replace `Ranking.sort(Ranking.byKey(r, ProfileKey.Category.PARTICLE), rankedSort(sort), ascending)`
with `Ranking.sort(Ranking.particles(r), rankedSort(sort), ascending)`.

In `blocks`, replace:

```java
            rows.add(new Row(new String[]{e.key().label(), Fmt.num(e.msPerSecond(seconds)), Fmt.us(e.usPerCall()), Fmt.count(e.calls())},
                    e.key(), e.key().typeId(), false, false, 0, null, -1));
```

with:

```java
            rows.add(new Row(MeshTable.block(e, seconds), e.key(), e.key().typeId(), false, false, 0, null, -1));
```

In `sections`, replace:

```java
            rows.add(new Row(new String[]{k.label(), Fmt.count(e.calls()), Fmt.num(e.callsPerSecond(seconds)), Fmt.ms(e.nanos()),
                    Fmt.ms2(e.calls() == 0 ? 0 : e.nanos() / e.calls())},
                    k, k.typeId(), false, false, 0, new BlockPos(k.centerX(), k.centerY(), k.centerZ()), -1));
```

with:

```java
            rows.add(new Row(MeshTable.section(e, seconds), k, k.typeId(), false, false, 0,
                    new BlockPos(k.centerX(), k.centerY(), k.centerZ()), -1));
```

- [ ] **Step 8: Run the tests**

Run: `./gradlew compileJava --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **198** tests. The existing report tests still pass. Their fixtures have no shadow
time, and no existing assertion names the shadow header. The particle rows they check are vanilla classes,
whose short names are the labels they already had.

- [ ] **Step 9: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/core src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java src/test/java/io/github/edwardg117/renderable
```

Report for the commit: `feat: short particle names, no empty shadow column in the report, and shared mesh cells`.

---

### Task 3: The mixin plugin checks each hook's target

**Files:**
- Modify: `gradle.properties`, `build.gradle`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/InjectionCheck.java`
- Create: `src/main/java/io/github/edwardg117/renderable/hooks/HookInstalls.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/RenderableMixinPlugin.java`
- Test: `src/test/java/io/github/edwardg117/renderable/hooks/InjectionCheckTest.java` (3)
- Test: `src/test/java/io/github/edwardg117/renderable/hooks/HookInstallsTest.java` (2)

Spec addendum §P5.D1, with deviation 1.
- **What the check reads.** `InjectionCheck` reads a mixin's injector annotations from its class file and checks
  each one against the class it was applied to. The target method must be there, found by name, and by
  descriptor when the selector has one. For an `INVOKE` injection point, so must the call. It is pure ASM, so
  the tests feed it small hand-built class nodes and our real mixins' class files.
- **What the table holds.** `HookInstalls` maps each injector to its hook and records the results.
- **How the table stays complete.** `HookInstallsTest` reads every mixin listed in both mixin configs, so an
  injector added without a table entry fails the build, and so does an injector of a kind `InjectionCheck` does
  not read.

Neither class may import Minecraft or Mixin types. The plugin, which does, hands them plain `ClassNode`s. The test
classpath has neither Minecraft nor Mixin, and gains ASM here.

- [ ] **Step 1: Put ASM on the test classpath**

In `gradle.properties`, after `bus_version=8.0.5`, add:

```properties
# The ASM that Mixin and this NeoForge version run on. The hook checker's tests read class files with it, which
# only main's classpath gets from the moddev plugin. Keep it equal to what the pinned NeoForge resolves:
# `./gradlew dependencies --configuration runtimeClasspath | grep asm-tree`.
asm_version=9.8
```

In `build.gradle`, after `testImplementation "net.neoforged:bus:${bus_version}"`, add:

```groovy
    testImplementation "org.ow2.asm:asm-tree:${asm_version}"
```

- [ ] **Step 2: Write the failing tests**

```java
package io.github.edwardg117.renderable.hooks;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InjectionCheckTest {
    static final String PARTICLE_ENGINE_MIXIN = "io.github.edwardg117.renderable.hooks.mixin.ParticleEngineMixin";
    static final String ACCELERATED_MIXIN =
            "io.github.edwardg117.renderable.hooks.mixin.acceleratedrendering.AcceleratedBufferBuilderMixin";
    static final String ENGINE = "net/minecraft/client/particle/ParticleEngine";
    static final String PARTICLE = "net/minecraft/client/particle/Particle";
    /** NeoForge's ParticleEngine.render, with the frustum and predicate it adds. */
    static final String RENDER_DESC = "(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;"
            + "FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V";
    static final String PARTICLE_RENDER_DESC = "(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V";

    static ClassNode target(String name, MethodNode... methods) {
        ClassNode c = new ClassNode();
        c.name = name;
        c.methods.addAll(List.of(methods));
        return c;
    }

    /** A method that makes the given calls, each a fresh node: an ASM node belongs to one instruction list. */
    static MethodNode method(String name, String desc, MethodInsnNode... calls) {
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, name, desc, null, null);
        for (MethodInsnNode call : calls) m.instructions.add(call);
        m.instructions.add(new InsnNode(Opcodes.RETURN));
        return m;
    }

    static MethodInsnNode call(String owner, String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, name, desc, false);
    }

    @Test
    void theReaderFindsEachInjectorsMethodsAndCalls() throws IOException {
        assertEquals(List.of(
                        new InjectionCheck.Injector("renderable$timeParticleRender", List.of("render" + RENDER_DESC),
                                List.of("L" + PARTICLE + ";render" + PARTICLE_RENDER_DESC)),
                        new InjectionCheck.Injector("renderable$timeParticleTick", List.of("tickParticle"),
                                List.of("L" + PARTICLE + ";tick()V"))),
                InjectionCheck.injectors(HookInstallsTest.classNode(PARTICLE_ENGINE_MIXIN)));

        // ModifyVariable's single @At, and HEAD and RETURN points, which name no call.
        List<InjectionCheck.Injector> accelerated = InjectionCheck.injectors(HookInstallsTest.classNode(ACCELERATED_MIXIN));
        assertEquals(5, accelerated.size(), "the shadowed getter is not an injector");
        assertEquals(new InjectionCheck.Injector("renderable$countFastVertex", List.of("addVertex(FFFIFFIIFFF)V"), List.of()),
                accelerated.get(1));
    }

    @Test
    void anInvokeInjectorNeedsItsMethodAndItsCall() throws IOException {
        InjectionCheck.Injector tick = InjectionCheck.injectors(HookInstallsTest.classNode(PARTICLE_ENGINE_MIXIN)).get(1);
        String desc = "(Lnet/minecraft/client/particle/Particle;)V";
        assertTrue(InjectionCheck.present(tick, target(ENGINE, method("tickParticle", desc, call(PARTICLE, "tick", "()V")))));
        assertFalse(InjectionCheck.present(tick, target(ENGINE, method("tickParticle", desc, call(PARTICLE, "update", "()V")))),
                "the call was renamed");
        assertFalse(InjectionCheck.present(tick, target(ENGINE, method("tickParticles", desc, call(PARTICLE, "tick", "()V")))),
                "the method was renamed");
    }

    @Test
    void aSelectorWithADescriptorMatchesOnlyThatOverloadAndHeadNeedsOnlyTheMethod() throws IOException {
        InjectionCheck.Injector render = InjectionCheck.injectors(HookInstallsTest.classNode(PARTICLE_ENGINE_MIXIN)).get(0);
        assertTrue(InjectionCheck.present(render,
                target(ENGINE, method("render", RENDER_DESC, call(PARTICLE, "render", PARTICLE_RENDER_DESC)))));
        assertFalse(InjectionCheck.present(render,
                target(ENGINE, method("render", "(F)V", call(PARTICLE, "render", PARTICLE_RENDER_DESC)))),
                "an overload with another descriptor is not the target");

        InjectionCheck.Injector head = InjectionCheck.injectors(HookInstallsTest.classNode(ACCELERATED_MIXIN)).get(1);
        String builder = "com/github/argon4w/acceleratedrendering/core/buffers/accelerated/builders/AcceleratedBufferBuilder";
        assertTrue(InjectionCheck.present(head, target(builder, method("addVertex", "(FFFIFFIIFFF)V"))));
        assertFalse(InjectionCheck.present(head,
                target(builder, method("addVertex", "(FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))));
    }
}
```

```java
package io.github.edwardg117.renderable.hooks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.edwardg117.renderable.core.Hook;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.github.edwardg117.renderable.hooks.InjectionCheckTest.*;
import static org.junit.jupiter.api.Assertions.*;

class HookInstallsTest {

    /** A class read as bytes: a mixin class must never be loaded outside Mixin, and there is no Minecraft here. */
    static ClassNode classNode(String className) throws IOException {
        try (InputStream in = HookInstallsTest.class.getResourceAsStream("/" + className.replace('.', '/') + ".class")) {
            assertNotNull(in, className + " is not on the test classpath");
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        }
    }

    /** Every mixin both configs list, by its full class name. */
    static List<String> mixins() throws IOException {
        List<String> out = new ArrayList<>();
        for (String config : List.of("renderable.mixins.json", "renderable.thirdparty.mixins.json")) {
            try (InputStream in = HookInstallsTest.class.getResourceAsStream("/" + config)) {
                assertNotNull(in, config);
                JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                String pkg = json.get("package").getAsString();
                for (JsonElement e : json.getAsJsonArray("client")) out.add(pkg + "." + e.getAsString());
            }
        }
        return out;
    }

    @Test
    void everyInjectorIsReadAndMappedAndEveryEntryIsAnInjector() throws IOException {
        Set<String> seen = new HashSet<>();
        for (String mixin : mixins()) {
            String simple = mixin.substring(mixin.lastIndexOf('.') + 1);
            ClassNode node = classNode(mixin);
            // An injector of a kind InjectionCheck does not read would go unchecked, so every annotation from an
            // injector package must be one it reads. Group and Surrogate live there but are not injectors.
            for (MethodNode m : node.methods) {
                List<AnnotationNode> annotations = new ArrayList<>();
                if (m.visibleAnnotations != null) annotations.addAll(m.visibleAnnotations);
                if (m.invisibleAnnotations != null) annotations.addAll(m.invisibleAnnotations);
                for (AnnotationNode a : annotations) {
                    boolean injectorPackage = a.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/")
                            || a.desc.startsWith("Lcom/llamalad7/mixinextras/injector/");
                    boolean notAnInjector = a.desc.endsWith("/Group;") || a.desc.endsWith("/Surrogate;");
                    assertTrue(!injectorPackage || notAnInjector || InjectionCheck.INJECTORS.contains(a.desc),
                            simple + "." + m.name + " uses " + a.desc + ", which InjectionCheck does not read: add it there");
                }
            }
            for (InjectionCheck.Injector in : InjectionCheck.injectors(node)) {
                String id = simple + "." + in.handler();
                assertTrue(HookInstalls.HOOKS.containsKey(id) || HookInstalls.BOUNDARIES.contains(id),
                        id + " is neither a hook nor a boundary: add it to HookInstalls.HOOKS");
                seen.add(id);
            }
        }
        Set<String> listed = new HashSet<>(HookInstalls.HOOKS.keySet());
        listed.addAll(HookInstalls.BOUNDARIES);
        assertEquals(listed, seen, "every entry names an injector that exists");
    }

    @Test
    void aHookWithAMissingTargetDidNotApplyAndOneWithEveryTargetIsInstalled() throws IOException {
        // ParticleEngine as it might look after an update that renamed tickParticle but kept render.
        ClassNode updated = target(ENGINE,
                method("render", RENDER_DESC, call(PARTICLE, "render", PARTICLE_RENDER_DESC)),
                method("updateParticle", "(Lnet/minecraft/client/particle/Particle;)V", call(PARTICLE, "tick", "()V")));
        HookInstalls.check(PARTICLE_ENGINE_MIXIN, classNode(PARTICLE_ENGINE_MIXIN), updated);

        assertTrue(HookInstalls.installed().contains(Hook.PARTICLE_RENDER.name()));
        assertTrue(HookInstalls.failed().contains(Hook.PARTICLE_TICK.name()));
        assertFalse(HookInstalls.installed().contains(Hook.PARTICLE_TICK.name()), "a hook is in one list or the other");
    }
}
```

The second test leaves its results in `HookInstalls`' static state for the rest of the test run. That is harmless:
nothing else under test reads `HookInstalls`, and the environment tests build their lists by hand.

- [ ] **Step 3: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*InjectionCheckTest' --tests '*HookInstallsTest'`
Expected: compilation failure, `cannot find symbol: class InjectionCheck`.

- [ ] **Step 4: Create `InjectionCheck`**

```java
package io.github.edwardg117.renderable.hooks;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads a mixin's injectors and checks each one's target against the class it was applied to (spec addendum
 * §P5.D1). Pure ASM, so it is tested against small class nodes.
 *
 * <p>It checks the target, not the handler. When the plugin's {@code postApply} runs, MixinExtras has applied
 * neither {@code @WrapOperation}, a late injection, nor {@code @WrapMethod}, which its applicator extension
 * rewrites afterwards, so no call to their handlers exists yet. The target method is still there with its original
 * body: a plain {@code @Inject} leaves its call in place, and the wrapping injectors have not run. Another mod's
 * {@code @Redirect} of the same call would remove it, and the check would then report a hook that MixinExtras
 * still wraps; the hook's rows would show it working.
 */
final class InjectionCheck {
    /** The injector annotations read; {@code HookInstallsTest} fails on any other from an injector package. */
    static final Set<String> INJECTORS = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;",
            "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;");

    private InjectionCheck() {
    }

    /**
     * One injector of a mixin.
     *
     * @param handler the handler method's name
     * @param methods the target method selectors: a name, or a name followed by a descriptor
     * @param calls   the calls its {@code INVOKE} injection points name, as {@code Lowner;name(desc)}; empty for
     *                {@code HEAD}, {@code RETURN} and {@code @WrapMethod}
     */
    record Injector(String handler, List<String> methods, List<String> calls) {
    }

    /** Every injector annotation on {@code mixin}'s methods, in the order the class file lists the methods. */
    static List<Injector> injectors(ClassNode mixin) {
        List<Injector> out = new ArrayList<>();
        for (MethodNode m : mixin.methods) {
            for (AnnotationNode a : annotations(m)) {
                if (!INJECTORS.contains(a.desc)) continue;
                List<String> methods = new ArrayList<>();
                List<String> calls = new ArrayList<>();
                for (int i = 0; a.values != null && i < a.values.size(); i += 2) {
                    Object name = a.values.get(i);
                    Object value = a.values.get(i + 1);
                    if ("method".equals(name)) {
                        for (Object o : asList(value)) methods.add((String) o);
                    } else if ("at".equals(name)) {
                        for (Object o : asList(value)) {
                            String call = invokeTarget((AnnotationNode) o);
                            if (call != null) calls.add(call);
                        }
                    }
                }
                out.add(new Injector(m.name, List.copyOf(methods), List.copyOf(calls)));
            }
        }
        return out;
    }

    /**
     * True when a method of {@code target} matches one of the injector's selectors and, for an {@code INVOKE}
     * injector, makes one of its calls.
     */
    static boolean present(Injector injector, ClassNode target) {
        for (MethodNode m : target.methods) {
            if (!selected(injector.methods(), m)) continue;
            if (injector.calls().isEmpty()) return true;
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode call
                        && injector.calls().contains("L" + call.owner + ";" + call.name + call.desc)) return true;
            }
        }
        return false;
    }

    private static boolean selected(List<String> selectors, MethodNode m) {
        for (String s : selectors) {
            int paren = s.indexOf('(');
            String name = paren < 0 ? s : s.substring(0, paren);
            if (name.equals(m.name) && (paren < 0 || s.substring(paren).equals(m.desc))) return true;
        }
        return false;
    }

    /** The call an {@code @At("INVOKE")} names, or null for any other injection point. */
    private static String invokeTarget(AnnotationNode at) {
        String value = null;
        String target = null;
        for (int i = 0; at.values != null && i < at.values.size(); i += 2) {
            if ("value".equals(at.values.get(i))) value = (String) at.values.get(i + 1);
            if ("target".equals(at.values.get(i))) target = (String) at.values.get(i + 1);
        }
        return "INVOKE".equals(value) ? target : null;
    }

    private static List<AnnotationNode> annotations(MethodNode m) {
        List<AnnotationNode> all = new ArrayList<>();
        if (m.visibleAnnotations != null) all.addAll(m.visibleAnnotations);
        if (m.invisibleAnnotations != null) all.addAll(m.invisibleAnnotations);
        return all;
    }

    /** An annotation value that is one element or an array of them: {@code @ModifyVariable}'s {@code at} is one. */
    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of(value);
    }
}
```

- [ ] **Step 5: Create `HookInstalls`**

```java
package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Hook;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

/**
 * Which hooks are in place (spec addendum §P5.D1), as {@link RenderableMixinPlugin} finds them when each hooked
 * class loads. A hook is installed when every checked injector of it found its target, and did not apply when one
 * did not. A hook whose classes never loaded in this client run is in neither list, and neither is a hook of a
 * mixin that Mixin rejected as a whole, which never reaches {@code postApply}; the game log names that. Which hooks
 * fired is {@link Probe#hooksFired()}'s business. Any thread: a class loads wherever the game first uses it.
 */
public final class HookInstalls {

    /**
     * Every injector's hook, by mixin class simple name and handler name. {@code HookInstallsTest} reads every mixin
     * and fails when an injector is missing here, so a new injector cannot go unchecked.
     */
    static final Map<String, Hook> HOOKS = Map.ofEntries(
            entry("BlockEntityRenderDispatcherMixin.renderable$timeRender", Hook.BLOCK_ENTITY_RENDER),
            entry("EntityRenderDispatcherMixin.renderable$timeRender", Hook.ENTITY_RENDER),
            entry("ParticleEngineMixin.renderable$timeParticleRender", Hook.PARTICLE_RENDER),
            entry("ParticleEngineMixin.renderable$timeParticleTick", Hook.PARTICLE_TICK),
            entry("BoundTickingBlockEntityMixin.renderable$timeTick", Hook.BLOCK_ENTITY_TICK),
            entry("LevelMixin.renderable$timeEntityTick", Hook.ENTITY_TICK),
            entry("LevelLightEngineMixin.renderable$timeLightUpdates", Hook.LIGHT),
            entry("BufferBuilderMixin.renderable$countVertex", Hook.VERTICES),
            entry("MinecraftMixin.renderable$timeTasks", Hook.TASKS),
            entry("MinecraftMixin.renderable$timeFrameCap", Hook.FRAME_CAP),
            entry("GuiMixin.renderable$timeHud", Hook.HUD),
            entry("GuiLayerManagerMixin.renderable$timeLayer", Hook.HUD_LAYERS),
            entry("PacketUtilsMixin.renderable$timePacket", Hook.PACKETS),
            entry("MainThreadPayloadHandlerMixin.renderable$timePayload", Hook.PAYLOADS),
            entry("ClientLevelMixin.renderable$timeRiderTick", Hook.RIDER_TICK),
            entry("FlywheelRenderDispatcherMixin.renderable$timeStartLevelRender", Hook.FLYWHEEL),
            entry("FlywheelRenderDispatcherMixin.renderable$timeAfterEntities", Hook.FLYWHEEL),
            entry("FlywheelRenderDispatcherMixin.renderable$timeCrumbling", Hook.FLYWHEEL),
            entry("ChunkBuilderMeshingTaskMixin.renderable$timeSection", Hook.SODIUM_MESH),
            entry("ChunkBuilderMeshingTaskMixin.renderable$timeBlock", Hook.SODIUM_MESH),
            entry("ChunkBuilderMeshingTaskMixin.renderable$timeFluid", Hook.SODIUM_MESH),
            entry("RenderSectionManagerMixin.renderable$timeUpload", Hook.SODIUM_UPLOAD),
            entry("RenderSectionManagerMixin.renderable$timeUpdate", Hook.SODIUM_UPLOAD),
            entry("SodiumBufferBuilderMixin.renderable$countPushedVertices", Hook.VERTICES),
            entry("AcceleratedBufferBuilderMixin.renderable$countSlowVertex", Hook.ACCELERATED_VERTICES),
            entry("AcceleratedBufferBuilderMixin.renderable$countFastVertex", Hook.ACCELERATED_VERTICES),
            entry("AcceleratedBufferBuilderMixin.renderable$countClientMesh", Hook.ACCELERATED_VERTICES),
            entry("AcceleratedBufferBuilderMixin.renderable$serverMeshBefore", Hook.ACCELERATED_VERTICES),
            entry("AcceleratedBufferBuilderMixin.renderable$serverMeshAfter", Hook.ACCELERATED_VERTICES));

    /** Injectors that are not hooks of their own: the frame and tick boundaries, which every session needs. */
    static final Set<String> BOUNDARIES = Set.of(
            "MinecraftMixin.renderable$frameStart", "MinecraftMixin.renderable$renderEnd",
            "MinecraftMixin.renderable$tickStart", "MinecraftMixin.renderable$tickEnd");

    private static final boolean[] PASSED = new boolean[Hook.values().length];
    private static final boolean[] FAILED = new boolean[Hook.values().length];

    private HookInstalls() {
    }

    /** Checks each injector of {@code mixin} against {@code target}, the class it was just applied to. */
    static void check(String mixinClassName, ClassNode mixin, ClassNode target) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        for (InjectionCheck.Injector injector : InjectionCheck.injectors(mixin)) {
            Hook hook = HOOKS.get(simple + "." + injector.handler());
            if (hook != null) record(hook, InjectionCheck.present(injector, target));
        }
    }

    private static synchronized void record(Hook hook, boolean present) {
        (present ? PASSED : FAILED)[hook.ordinal()] = true;
    }

    /** Hooks every checked injector of which found its target, in enum order. */
    public static synchronized List<String> installed() {
        List<String> out = new ArrayList<>();
        for (Hook h : Hook.values()) {
            if (PASSED[h.ordinal()] && !FAILED[h.ordinal()]) out.add(h.name());
        }
        return out;
    }

    /** Hooks with an injector whose target was missing, in enum order: they did not apply, wholly or in part. */
    public static synchronized List<String> failed() {
        List<String> out = new ArrayList<>();
        for (Hook h : Hook.values()) {
            if (FAILED[h.ordinal()]) out.add(h.name());
        }
        return out;
    }
}
```

Before going on, compare the table with the handlers in `src/main/java/io/github/edwardg117/renderable/hooks/mixin`
(`grep -rn 'renderable\$' --include=*.java` there): 5a's six new mixins must be in it under the handler names
5a gave them. The test in Step 2 is what enforces this; the look now saves a round trip.

- [ ] **Step 6: Call it from the plugin**

In `RenderableMixinPlugin.java`, add these imports:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

add this field at the top of the class:

```java
    private static final Logger LOGGER = LoggerFactory.getLogger("Renderable");
```

and replace the empty `postApply` with:

```java
    /**
     * Records whether each of the mixin's hooks found its target in the class it was just applied to (spec addendum
     * §P5.D1). A failed check must never fail the class it ran on, whose mixins have applied either way.
     */
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        try {
            HookInstalls.check(mixinClassName, mixinInfo.getClassNode(0), targetClass);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Renderable could not check the hooks {} applied to {}", mixinClassName, targetClassName, e);
        }
    }
```

Extend the class Javadoc with a second paragraph:

```java
 * <p>Attached to both configs. {@link #postApply} also records which hooks found their targets, for the report's
 * installed and failed hook lists (spec addendum §P5.D1).
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew compileJava --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **203** tests. If `everyInjectorIsReadAndMappedAndEveryEntryIsAnInjector` names an
injector, fix the table in `HookInstalls` (or `InjectionCheck.INJECTORS`, for a new kind of injector), not the
test.

- [ ] **Step 8: Stage**

```bash
git add gradle.properties build.gradle src/main/java/io/github/edwardg117/renderable/hooks/InjectionCheck.java src/main/java/io/github/edwardg117/renderable/hooks/HookInstalls.java src/main/java/io/github/edwardg117/renderable/hooks/RenderableMixinPlugin.java src/test/java/io/github/edwardg117/renderable/hooks/InjectionCheckTest.java src/test/java/io/github/edwardg117/renderable/hooks/HookInstallsTest.java
```

Report for the commit: `feat(hooks): the mixin plugin checks each hook's target as its class loads`.

---

### Task 4: Installed, fired and failed in the environment, the reports and the empty tables

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/core/Environment.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/EmptyStates.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/Probe.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/EnvironmentCollector.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/TextReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/report/JsonReportWriter.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java`
- Test: `core/EnvironmentTest.java` (update, append 1), `core/RankingTest.java` (update), `core/EmptyStatesTest.java`
  (replace 2), `report/TextReportWriterTest.java` (update, append 1), `report/JsonReportWriterTest.java` (update)

Spec addendum §P5.D1 and §P5.D2, with deviation 1.
- **The environment.** It carries the three lists, and `Environment.state(Hook)` reads them as failed,
  installed or not loaded.
- **The empty states** use that to tell "the hook did not apply", "the code never ran" and "nothing happened"
  apart.
- **Renames.** `hooksApplied` becomes `hooksFired` everywhere: the record, `Probe`, the text report and the
  JSON. 5a's schema 5 is not released, so the JSON keeps schema 5.

- [ ] **Step 1: Write the failing tests and update the old ones**

In `EnvironmentTest`, change `assertThrows(UnsupportedOperationException.class, () -> env.hooksApplied().add("x"));`
to `assertThrows(UnsupportedOperationException.class, () -> env.hooksFired().add("x"));`, and append:

```java
    @Test
    void aHookIsFailedInstalledOrNotLoaded() {
        Environment env = Environment.of(Map.of(), List.of("PARTICLE_RENDER", "VERTICES"),
                List.of("BLOCK_ENTITY_RENDER"), List.of("VERTICES", "SODIUM_MESH"));
        assertEquals(Environment.HookState.INSTALLED, env.state(Hook.BLOCK_ENTITY_RENDER));
        assertEquals(Environment.HookState.INSTALLED, env.state(Hook.PARTICLE_RENDER), "firing shows it is in place");
        assertEquals(Environment.HookState.FAILED, env.state(Hook.SODIUM_MESH));
        assertEquals(Environment.HookState.FAILED, env.state(Hook.VERTICES), "it fired, but part of it did not apply");
        assertEquals(Environment.HookState.NOT_LOADED, env.state(Hook.FLYWHEEL));
        assertThrows(UnsupportedOperationException.class, () -> env.hooksInstalled().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> env.hooksFailed().add("x"));
    }
```

(`EnvironmentTest` already imports `Map` and `List`; it is in `core`, as `Hook` is.)

In `RankingTest.environmentHoldsOrderedEntries`, change `env.hooksApplied()` to `env.hooksFired()`.

In `EmptyStatesTest`, add this helper after `env(...)`:

```java
    /** An environment as the plugin reports it: hooks installed and hooks that did not apply, none fired. */
    static Environment checked(Map<String, String> entries, List<String> installed, List<String> failed) {
        return Environment.of(new LinkedHashMap<>(entries), List.of(), installed, failed);
    }
```

and replace the two tests `meshAndParticleStatesTellAbsentFromEmpty` and
`sodiumsUploadHookFiresEveryFrameSoItAloneShowsSodiumIsHooked`, from the first one's `@Test` to the second one's
closing brace, with:

```java
    @Test
    void meshStatesTellAbsentFailedUnloadedAndEmptyApart() {
        Map<String, String> sodium = Map.of("sodium", "0.8.13");
        assertEquals("(no data: Sodium is not installed)", EmptyStates.blocks(env(Map.of())));
        assertEquals("(no data: Sodium's mesh hook did not apply)",
                EmptyStates.blocks(checked(sodium, List.of(), List.of("SODIUM_MESH"))));
        assertEquals("(no data: Sodium's mesh hook never loaded; the game log says why)", EmptyStates.sections(env(sodium)));
        assertEquals("(no data: no blocks were meshed)", EmptyStates.blocks(checked(sodium, List.of("SODIUM_MESH"), List.of())));
        assertEquals("(no data: no sections were rebuilt)", EmptyStates.sections(env(sodium, "SODIUM_MESH")),
                "a hook that fired is in place too");
    }

    @Test
    void rankedAndParticleStatesBlameTheHookOnlyWhenItDidNotApply() {
        assertEquals("(nothing of this kind was drawn or ticked)", EmptyStates.blockEntities(env(Map.of())));
        assertEquals("(hook not installed)", EmptyStates.entities(checked(Map.of(), List.of(), List.of("RIDER_TICK"))));
        assertEquals("(no particles were rendered)", EmptyStates.particles(env(Map.of())));
        assertEquals("(hook not installed)",
                EmptyStates.particles(checked(Map.of(), List.of("PARTICLE_RENDER"), List.of("PARTICLE_TICK"))));
        assertEquals("(Flywheel sampled 3 times and found no visuals alive)", EmptyStates.flywheelNoVisuals(3));
        assertEquals("(Flywheel sampled once and found no visuals alive)", EmptyStates.flywheelNoVisuals(1));
    }
```

In `TextReportWriterTest`:

1. In `headerAndEnvironment`, replace:

```java
        assertTrue(s.contains("\nEnvironment\n  minecraft: 1.21.1\n  gpu: Test GPU\n  hooks applied: BLOCK_ENTITY_RENDER, ENTITY_RENDER\n"), s);
```

   with:

```java
        assertTrue(s.contains("\nEnvironment\n  minecraft: 1.21.1\n  gpu: Test GPU\n  hooks installed: none\n"
                + "  hooks fired: BLOCK_ENTITY_RENDER, ENTITY_RENDER\n  hooks that did not apply: none\n\n"), s);
        String checked = TextReportWriter.write(r, Environment.of(new LinkedHashMap<>(), List.of("VERTICES"),
                List.of("BLOCK_ENTITY_RENDER"), List.of("SODIUM_MESH")), "0.5.0");
        assertTrue(checked.contains("\nEnvironment\n  hooks installed: BLOCK_ENTITY_RENDER\n  hooks fired: VERTICES\n"
                + "  hooks that did not apply: SODIUM_MESH\n\n"), checked);
```

2. In `rankedTablesAlignAndEmptyCategoriesSaySo`, replace the three empty-state lines:

```java
        assertTrue(s.contains("Particles (top 20)\n  (no data: hook not active in this build)\n"), s);
        assertTrue(s.contains("Blocks (mesh build, top 20 by mesh ms/s)\n  (no data: Sodium hooks were not active)\n"), s);
        assertTrue(s.contains("Sections (mesh rebuilds, top 20 by rebuilds)\n  (no data: Sodium hooks were not active)\n"), s);
```

   with:

```java
        assertTrue(s.contains("Particles (top 20)\n  (no particles were rendered)\n"), s);
        assertTrue(s.contains("Blocks (mesh build, top 20 by mesh ms/s)\n  (no data: Sodium is not installed)\n"), s);
        assertTrue(s.contains("Sections (mesh rebuilds, top 20 by rebuilds)\n  (no data: Sodium is not installed)\n"), s);
```

3. In `flywheelSectionExplainsMissingData`, change
   `.contains("Particles (top 20)\n  (no data: hook not active in this build)\n")` to
   `.contains("Particles (top 20)\n  (no particles were rendered)\n")`.

4. Append:

```java
    @Test
    void aFlywheelTableWithSamplesButNoVisualsSaysSo() {
        SessionResult withFw = withFlywheel(TestSessions.sample(), new FlywheelStats(3, 0, 0, 0, Map.of()), 0);
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("flywheel", "1.0.6");
        m.put("flywheelBackend", "flywheel:instancing");
        String s = TextReportWriter.write(withFw, Environment.of(m, List.of("FLYWHEEL")), "0.5.0");
        assertTrue(s.contains("  visuals: 0.0 block entities, 0.0 entities, 0.0 effects (average over 3 samples)\n"
                + "  (Flywheel sampled 3 times and found no visuals alive)\n\n"), s);
        assertFalse(s.contains("visual class"), "no header over no rows: " + s);
    }
```

In `JsonReportWriterTest.schemaModEnvironmentAndSession`, replace:

```java
        assertEquals(2, o.getAsJsonObject("environment").getAsJsonArray("hooksApplied").size());
```

with:

```java
        JsonObject environment = o.getAsJsonObject("environment");
        assertEquals(2, environment.getAsJsonArray("hooksFired").size());
        assertEquals(0, environment.getAsJsonArray("hooksInstalled").size());
        assertEquals(0, environment.getAsJsonArray("hooksFailed").size());
        assertNull(environment.get("hooksApplied"), "renamed to hooksFired");
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon`
Expected: compilation failure (`hooksFired`, `Environment.HookState`, `EmptyStates.blockEntities` do not exist).

- [ ] **Step 3: The environment's three lists**

In `Environment.java`:

1. Replace the class Javadoc and the record header:

```java
/**
 * Plain environment description for reports (spec §6.1 item 2). Built by the Minecraft-aware collector in
 * {@code report}; the core only carries it. Entries keep insertion order.
 */
public record Environment(Map<String, String> entries, List<String> hooksApplied) {
```

   with:

```java
/**
 * Plain environment description for reports (spec §6.1 item 2). Built by the Minecraft-aware collector in
 * {@code report}; the core only carries it. Entries keep insertion order.
 *
 * <p>Three hook lists (spec addendum §P5.D1): {@code hooksFired}, the hooks that have run while a session was
 * sampling, in any session since the game started; {@code hooksInstalled}, those every injector of which found its
 * target when its class loaded; and
 * {@code hooksFailed}, those with an injector that did not. A hook whose classes never loaded is in neither of the
 * last two. {@link #state} reads them together.
 */
public record Environment(Map<String, String> entries, List<String> hooksFired, List<String> hooksInstalled,
                          List<String> hooksFailed) {
```

2. Replace the end of the compact constructor and the factories:

```java
        hooksApplied = List.copyOf(hooksApplied);
    }

    public static Environment of(Map<String, String> entries, List<String> hooksApplied) {
        return new Environment(entries, hooksApplied);
    }

    public static Environment empty() {
        return new Environment(Map.of(), List.of());
    }
```

   with:

```java
        hooksFired = List.copyOf(hooksFired);
        hooksInstalled = List.copyOf(hooksInstalled);
        hooksFailed = List.copyOf(hooksFailed);
    }

    /** An environment with fired hooks only, for tests that need no more. */
    public static Environment of(Map<String, String> entries, List<String> hooksFired) {
        return new Environment(entries, hooksFired, List.of(), List.of());
    }

    public static Environment of(Map<String, String> entries, List<String> hooksFired, List<String> hooksInstalled,
                                 List<String> hooksFailed) {
        return new Environment(entries, hooksFired, hooksInstalled, hooksFailed);
    }

    public static Environment empty() {
        return new Environment(Map.of(), List.of(), List.of(), List.of());
    }
```

   Also update the compact constructor's Javadoc: "Copies both collections" becomes "Copies every collection".

3. At the end of the record, add:

```java
    /** What the three hook lists say about one hook (spec addendum §P5.D1). */
    public enum HookState {
        /** An injector of it did not find its target: the hook did not apply, wholly or in part. */
        FAILED,
        /** Every checked injector of it found its target, or it fired. */
        INSTALLED,
        /** None of the classes it hooks loaded in this client run, and it never fired. */
        NOT_LOADED
    }

    /** The state of {@code hook}. A failed injector outweighs a firing: part of the hook is missing. */
    public HookState state(Hook hook) {
        String name = hook.name();
        if (hooksFailed.contains(name)) return HookState.FAILED;
        if (hooksInstalled.contains(name) || hooksFired.contains(name)) return HookState.INSTALLED;
        return HookState.NOT_LOADED;
    }
```

- [ ] **Step 4: The empty-state sentences**

In `EmptyStates.java`, replace everything from the Javadoc of `ranked()` down to the closing brace of `mesh(...)`
(the methods `ranked`, `particles`, `blocks`, `sections` and `mesh`) with:

```java
    private static final String NOT_INSTALLED = "(hook not installed)";

    /**
     * An empty Block Entities table (spec addendum §P5.D2). Its hooks are required vanilla mixins, without which the
     * game does not start, so in practice the table is empty because nothing of the kind ran while sampling.
     */
    public static String blockEntities(Environment env) {
        return ranked(env, Hook.BLOCK_ENTITY_RENDER, Hook.BLOCK_ENTITY_TICK);
    }

    /** An empty Entities table: as {@link #blockEntities}, with the rider hook as well. */
    public static String entities(Environment env) {
        return ranked(env, Hook.ENTITY_RENDER, Hook.ENTITY_TICK, Hook.RIDER_TICK);
    }

    public static String particles(Environment env) {
        return failed(env, Hook.PARTICLE_RENDER, Hook.PARTICLE_TICK) ? NOT_INSTALLED : "(no particles were rendered)";
    }

    public static String blocks(Environment env) {
        return mesh(env, "blocks were meshed");
    }

    public static String sections(Environment env) {
        return mesh(env, "sections were rebuilt");
    }

    /**
     * Sodium's tables tell four cases apart (spec addendum §P5.D1): Sodium is absent; its mesh hook did not apply;
     * the hook is in place and nothing was meshed or rebuilt while sampling; or Sodium is present but the hooked
     * class never loaded, which in a world means Mixin rejected the whole hook class and logged why.
     */
    private static String mesh(Environment env, String nothing) {
        return switch (env.state(Hook.SODIUM_MESH)) {
            case FAILED -> "(no data: Sodium's mesh hook did not apply)";
            case INSTALLED -> "(no data: no " + nothing + ")";
            case NOT_LOADED -> env.has("sodium")
                    ? "(no data: Sodium's mesh hook never loaded; the game log says why)"
                    : "(no data: Sodium is not installed)";
        };
    }

    private static String ranked(Environment env, Hook... hooks) {
        return failed(env, hooks) ? NOT_INSTALLED : "(nothing of this kind was drawn or ticked)";
    }

    private static boolean failed(Environment env, Hook... hooks) {
        for (Hook h : hooks) {
            if (env.state(h) == Environment.HookState.FAILED) return true;
        }
        return false;
    }
```

After `flywheel(...)`, add:

```java
    /** A Flywheel table whose samples found no visual classes (spec addendum §P5.D2), in place of an empty table. */
    public static String flywheelNoVisuals(int samples) {
        return "(Flywheel sampled " + (samples == 1 ? "once" : samples + " times") + " and found no visuals alive)";
    }
```

Delete the now unused `import java.util.List;`.

- [ ] **Step 5: `Probe`, the collector and the controller**

In `Probe.java`, rename `hooksApplied()` to `hooksFired()`. Its Javadoc already says what it returns.

In `EnvironmentCollector.java`:
- add `import io.github.edwardg117.renderable.hooks.HookInstalls;` and `import io.github.edwardg117.renderable.hooks.Probe;`;
- change `public static Environment collect(List<String> hooksApplied) {` to `public static Environment collect() {`;
- replace the last line of `collect`, `return Environment.of(m, hooksApplied);`, with:

```java
        return Environment.of(m, Probe.hooksFired(), HookInstalls.installed(), HookInstalls.failed());
```

Delete `import java.util.List;` if nothing else in the file uses it (5b's additions use `ManagementFactory` and
streams, not `List`; check before deleting).

In `ProfilerController.java`, replace `Environment env = EnvironmentCollector.collect(Probe.hooksApplied());` with
`Environment env = EnvironmentCollector.collect();`.

- [ ] **Step 6: The report**

In `TextReportWriter.java`:

1. Replace:

```java
        sb.append("  hooks applied: ")
                .append(env.hooksApplied().isEmpty() ? "none" : String.join(", ", env.hooksApplied()))
                .append("\n\n");
```

   with:

```java
        hooks(sb, "hooks installed", env.hooksInstalled());
        hooks(sb, "hooks fired", env.hooksFired());
        hooks(sb, "hooks that did not apply", env.hooksFailed());
        sb.append('\n');
```

   and add this method after `write`:

```java
    /** One of the environment's three hook lists (spec addendum §P5.D1). */
    private static void hooks(StringBuilder sb, String what, List<String> hooks) {
        sb.append("  ").append(what).append(": ").append(hooks.isEmpty() ? "none" : String.join(", ", hooks)).append('\n');
    }
```

2. Give `appendRanked` its empty sentence as a parameter. Change its signature to:

```java
    static void appendRanked(StringBuilder sb, String title, List<RankedEntry> entries, int frames, long total,
                             String empty) {
```

   and in its body replace `sb.append("  ").append(EmptyStates.ranked()).append("\n\n");` with
   `sb.append("  ").append(empty).append("\n\n");`. In `write`, replace the four calls:

```java
        appendRanked(sb, "Block entities by type", Ranking.byType(r, ProfileKey.Category.BLOCK_ENTITY), frames, total);
        appendRanked(sb, "Block entities by position", Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY), frames, total);
        appendRanked(sb, "Entities by type", Ranking.byType(r, ProfileKey.Category.ENTITY), frames, total);
        appendRanked(sb, "Entities (individuals)", Ranking.byKey(r, ProfileKey.Category.ENTITY), frames, total);
```

   with:

```java
        appendRanked(sb, "Block entities by type", Ranking.byType(r, ProfileKey.Category.BLOCK_ENTITY), frames, total,
                EmptyStates.blockEntities(env));
        appendRanked(sb, "Block entities by position", Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY), frames, total,
                EmptyStates.blockEntities(env));
        appendRanked(sb, "Entities by type", Ranking.byType(r, ProfileKey.Category.ENTITY), frames, total,
                EmptyStates.entities(env));
        appendRanked(sb, "Entities (individuals)", Ranking.byKey(r, ProfileKey.Category.ENTITY), frames, total,
                EmptyStates.entities(env));
```

3. In `appendFlywheel`, replace:

```java
        if (bucketNanos == 0 && !env.hooksApplied().contains(Hook.FLYWHEEL.name())) {
```

   with:

```java
        // With visuals sampled, Flywheel's dispatcher class has loaded, so a hook that is not installed did not apply.
        if (bucketNanos == 0 && env.state(Hook.FLYWHEEL) != Environment.HookState.INSTALLED) {
```

   and, after the `visuals:` line's `sb.append(...)`, before `Table t = new Table(...)`, add:

```java
        if (fw.avgByClass().isEmpty()) {
            // No classes to list. Say why when nothing was alive; when visuals were counted but not by class, the
            // totals above are all there is.
            if (fw.total() == 0) sb.append("  ").append(EmptyStates.flywheelNoVisuals(fw.samples())).append('\n');
            sb.append('\n');
            return;
        }
```

- [ ] **Step 7: The JSON**

In `JsonReportWriter.java`, replace:

```java
        w.name("hooksApplied").beginArray();
        for (String h : env.hooksApplied()) w.value(h);
        w.endArray();
```

with:

```java
        hooks(w, "hooksFired", env.hooksFired());
        hooks(w, "hooksInstalled", env.hooksInstalled());
        hooks(w, "hooksFailed", env.hooksFailed());
```

and add after `body`:

```java
    /** One of the environment's three hook lists (spec addendum §P5.D1). */
    private static void hooks(JsonWriter w, String name, List<String> hooks) throws IOException {
        w.name(name).beginArray();
        for (String h : hooks) w.value(h);
        w.endArray();
    }
```

- [ ] **Step 8: The screen**

In `Rows.java`:

1. In `build`, change the two ranked arms to pass the environment:

```java
            case BLOCK_ENTITIES -> ranked(r, env, ProfileKey.Category.BLOCK_ENTITY, sort, ascending, expanded);
            case ENTITIES -> ranked(r, env, ProfileKey.Category.ENTITY, sort, ascending, expanded);
```

2. Replace the whole `ranked` method, from `static List<Row> ranked(SessionResult r, ProfileKey.Category category,`
   to its closing brace, with this version. It takes the environment, and it puts the empty sentence under the
   Flywheel heading when that heading is the only row:

```java
    static List<Row> ranked(SessionResult r, Environment env, ProfileKey.Category category, ColumnSort sort,
                            boolean ascending, Set<String> expanded) {
        int frames = r.frameCount();
        long total = r.stats().totalNanos();
        Ranking.Sort by = rankedSort(sort);
        List<Row> rows = new ArrayList<>();
        if (category == ProfileKey.Category.BLOCK_ENTITY
                && (r.bucketTotalNanos(Bucket.FLYWHEEL) > 0 || r.flywheel().total() > 0)) {
            rows.add(Row.heading("Flywheel-instanced machines are not timed here; see the Flywheel tab"));
        }
        List<RankedEntry> types = Ranking.sort(Ranking.byType(r, category), by, ascending);
        if (types.isEmpty()) {
            // Under the Flywheel heading too, when there is one: it is not a row of this table (spec addendum §P5.D2).
            rows.add(Row.plain(category == ProfileKey.Category.BLOCK_ENTITY
                    ? EmptyStates.blockEntities(env) : EmptyStates.entities(env)));
            return rows;
        }
        for (RankedEntry t : types) {
            rows.add(new Row(RankedTable.ranked(t.typeId() + "  (" + t.members() + ")", t, frames, total),
                    null, t.typeId(), true, true, 0, null, -1));
            if (expanded.contains(t.typeId())) {
                for (RankedEntry m : Ranking.sort(Ranking.membersOf(r, category, t.typeId()), by, ascending)) {
                    rows.add(new Row(RankedTable.ranked(memberLabel(m.key()), m, frames, total),
                            m.key(), t.typeId(), false, false, 1, posOf(m.key()), entityIdOf(m.key())));
                }
            }
        }
        return rows;
    }
```

3. In `flywheel`, after the row that begins `new Row(new String[]{"Visuals, average over "`, add:

```java
        if (fw.avgByClass().isEmpty()) {
            // No classes to list; say why when nothing was alive (the totals row above covers the rest).
            if (fw.total() == 0) rows.add(Row.plain(EmptyStates.flywheelNoVisuals(fw.samples())));
            return rows;
        }
```

- [ ] **Step 9: Run the tests**

Run: `./gradlew compileJava --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **205** tests. Then check that nothing still uses the old names:
`grep -rn "hooksApplied" src/main` and `grep -rn "EmptyStates.ranked()" src` print nothing. (The tests name
`hooksApplied` once, to check it is gone from the JSON.)

- [ ] **Step 10: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/core/Environment.java src/main/java/io/github/edwardg117/renderable/core/EmptyStates.java src/main/java/io/github/edwardg117/renderable/hooks/Probe.java src/main/java/io/github/edwardg117/renderable/report src/main/java/io/github/edwardg117/renderable/ProfilerController.java src/main/java/io/github/edwardg117/renderable/ui/screen/Rows.java src/test/java/io/github/edwardg117/renderable
```

Report for the commit: `feat: hooks installed, fired and failed; empty tables say which`.

---

### Task 5: A layout that fits its headers, and each key's dimension

**Files:**
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/Tab.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/core/ProfileKey.java`
- Test: `src/test/java/io/github/edwardg117/renderable/ui/screen/TabTest.java` (append 1, extend 1)
- Test: `src/test/java/io/github/edwardg117/renderable/core/KeysTest.java` (append 1)

Spec addendum §P5.D9 with deviation 5, and the pure part of §P5.D7. Both are the unit-tested halves of what
Task 7 puts on the screen.

- [ ] **Step 1: Write the failing tests**

Add to `TabTest`, inside the class before its first test:

```java
    /** The vanilla font's advances for the characters the headers use. */
    static int vanillaWidth(String s) {
        int w = 0;
        for (char c : s.toCharArray()) {
            w += switch (c) {
                case 'i', '.', ',' -> 2;
                case 'l' -> 3;
                case 't', ' ' -> 4;
                case 'f', 'k' -> 5;
                default -> 6;
            };
        }
        return w;
    }

    @Test
    void aHeaderThatDoesNotFitWidensItsColumnAtTheNameColumnsExpense() {
        int arrow = vanillaWidth(" v");
        assertEquals(63, vanillaWidth("render us/f"), "the header the spec measured at GUI scale 3 on a 2560 px window");
        Tab.Columns ranked = Tab.BLOCK_ENTITIES.columns();

        int[][] plain = ranked.layout(834);   // GUI 854
        int[][] fitted = ranked.layout(834, TabTest::vanillaWidth, arrow);
        assertEquals(60, plain[1][1], "render us/f's column was 60 px");
        assertEquals(63 + arrow - (Tab.Columns.PADDING - 1), fitted[1][1], "now the header and its arrow fit");
        assertEquals(plain[1][0] - (fitted[1][1] - plain[1][1]), fitted[1][0], "the name column paid for it");
        for (int i = 2; i < ranked.size(); i++) {
            assertEquals(plain[1][i], fitted[1][i], ranked.get(i).name() + " already fitted");
            assertEquals(plain[0][i], fitted[0][i], ranked.get(i).name() + " did not move");
        }

        // GUI 427, Auto scale on 1440p: every header is short of room. The name gives at most a quarter, to the
        // smallest shortfalls first, so the four that need least fit whole and render us/f is left to truncate.
        int[][] narrowPlain = ranked.layout(407);
        int[][] narrow = ranked.layout(407, TabTest::vanillaWidth, arrow);
        assertTrue(narrow[1][0] >= narrowPlain[1][0] * 3 / 4, "the name keeps three quarters");
        assertEquals(narrowPlain[1][1], narrow[1][1], "render us/f needs more than there is to give");
        int wholeHeaders = 0;
        for (int i = 1; i < ranked.size(); i++) {
            if (vanillaWidth(ranked.get(i).name()) + arrow <= narrow[1][i] + Tab.Columns.PADDING - 1) wholeHeaders++;
        }
        assertEquals(4, wholeHeaders, "gc f, calls, share and shadow");
    }
```

In `layoutKeepsItsPaddingAfterEveryColumnAndEndsInsideTheRow`, check the fitted layout as well: replace the line
`int[][] layout = t.columns().layout(rowWidth);` and the block under it with:

```java
                for (int[][] layout : List.of(t.columns().layout(rowWidth),
                        t.columns().layout(rowWidth, TabTest::vanillaWidth, vanillaWidth(" v")))) {
                    int[] x = layout[0];
                    int[] w = layout[1];
                    for (int i = 1; i < x.length; i++) {
                        assertTrue(x[i] - (x[i - 1] + w[i - 1]) >= Tab.Columns.PADDING,
                                t + " column " + (i - 1) + " leaves too little padding at " + rowWidth);
                    }
                    assertTrue(x[x.length - 1] + w[w.length - 1] <= rowWidth, t + " ends past the row at " + rowWidth);
                }
```

Append to `KeysTest`:

```java
    @Test
    void blockEntityAndSectionKeysKnowTheirDimensionAndOthersDoNot() {
        assertEquals("minecraft:the_nether",
                new BlockEntityKey("create:mechanical_press", "minecraft:the_nether", 1, 2, 3).dimension());
        assertEquals("minecraft:the_nether", new SectionKey("minecraft:the_nether", 1, 2, 3).dimension());
        assertNull(new EntityKey("minecraft:cow", 7, "").dimension(), "an entity's is its session's");
        assertNull(new ParticleKey("a.B").dimension());
        assertNull(new BlockKey("minecraft:stone").dimension());
    }
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew test --rerun --no-daemon --tests '*TabTest' --tests '*KeysTest'`
Expected: compilation failure (the three-argument `layout` and `ProfileKey.dimension()` do not exist).

- [ ] **Step 3: Widen for headers**

In `Tab.java`, in `Columns`, after `layout(int rowWidth)`, add:

```java
        /**
         * {@link #layout(int)}, with each right-aligned column whose header does not fit widened until it does,
         * with room for the sort arrow when the column sorts (spec addendum §P5.D9). A header may reach
         * {@code PADDING - 1} px left of its column, as a number may. The pixels come from the first column, the
         * name, which keeps at least three quarters of its width. The smallest shortfalls go first, so on a narrow
         * screen as many headers as possible fit whole and the rest truncate as before.
         */
        public int[][] layout(int rowWidth, ToIntFunction<String> textWidth, int arrowWidth) {
            int[][] layout = layout(rowWidth);
            int[] x = layout[0];
            int[] w = layout[1];
            int[] need = new int[all.size()];
            for (int i = 1; i < all.size(); i++) {
                Column c = all.get(i);
                if (!c.rightAlign()) continue;
                int header = textWidth.applyAsInt(c.name()) + (c.sort().sortable() ? arrowWidth : 0);
                need[i] = Math.max(0, header - (w[i] + PADDING - 1));
            }
            int spare = Math.max(0, Math.min(w[0] / 4, w[0] - 8));
            int[] grow = new int[all.size()];
            while (true) {
                int pick = -1;
                for (int i = 1; i < need.length; i++) {
                    if (need[i] > 0 && grow[i] == 0 && need[i] <= spare && (pick < 0 || need[i] < need[pick])) pick = i;
                }
                if (pick < 0) break;
                grow[pick] = need[pick];
                spare -= need[pick];
            }
            // The name shrinks by the total, so every later column moves left by what the columns after it gained.
            int shift = 0;
            for (int g : grow) shift += g;
            w[0] -= shift;
            for (int i = 1; i < all.size(); i++) {
                x[i] -= shift;
                shift -= grow[i];
                w[i] += grow[i];
            }
            return layout;
        }
```

`Tab.java` already imports `ToIntFunction`. In the `Columns` Javadoc, change "{@link #layout(int)} turns weights
into pixel offsets." to "{@link #layout(int)} turns weights into pixel offsets, and
{@link #layout(int, ToIntFunction, int)} then makes room for the headers."

- [ ] **Step 4: A key's dimension**

In `ProfileKey.java`, after `String serialize();`, add:

```java
    /**
     * The dimension the key was measured in, for the kinds that record one: block entities and sections. Null for
     * the rest; an entity's is its session's (spec addendum §P5.D7).
     */
    default String dimension() {
        return null;
    }
```

`BlockEntityKey` and `SectionKey` are records with a `dimension` component, so their accessors already implement
this; nothing else changes.

- [ ] **Step 5: Run the tests**

Run: `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **207** tests.

- [ ] **Step 6: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/ui/screen/Tab.java src/main/java/io/github/edwardg117/renderable/core/ProfileKey.java src/test/java/io/github/edwardg117/renderable/ui/screen/TabTest.java src/test/java/io/github/edwardg117/renderable/core/KeysTest.java
```

Report for the commit: `feat: a column layout that fits its headers, and each key's dimension`.

---

### Task 6: The overlay: a highlight's dimension, and labels in two batches

**Files:**
- Replace: `src/main/java/io/github/edwardg117/renderable/ui/WorldOverlay.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ui/screen/ResultsScreen.java` (the two `highlight` calls)

Spec addendum §P5.D7's overlay part and §P5.D8, with deviation 2. There are no unit tests: the file draws with
Minecraft's GUI. Task 9 and the in-pack acceptance check it by eye.
- **The highlight** now carries the dimension of the row it came from, and draws only there.
- **The labels** are drawn in two passes. Each gets its own depth, so where labels overlap, the later one covers
  the earlier one's text: fully, where its translucent background used to dim it.
- **The highlight's depth.** It is drawn above the last label, as it was when it was drawn after them.

Everything else in the file is unchanged. `drawHighlight`'s edge clipping and its no-allocation rule stay as they
are.

- [ ] **Step 1: Replace `WorldOverlay.java`**

```java
package io.github.edwardg117.renderable.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.edwardg117.renderable.RenderableConfig;
import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.EntityKey;
import io.github.edwardg117.renderable.core.Fmt;
import io.github.edwardg117.renderable.core.MeshEntry;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.RankedEntry;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SectionKey;
import io.github.edwardg117.renderable.core.SessionResult;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Cost labels for the last finished session plus a temporary highlight (spec §5.4).
 * <p>
 * Both are projected to screen space and drawn as a GUI layer so they sit on top of everything the world
 * renderer and any shader pipeline produce; an obstructed machine is still findable, as in Observable.
 * The view-projection matrix and camera are captured from the level render each frame (game bus) and
 * consumed by the GUI layer (mod bus). Labels and the highlight are each drawn only in the dimension they
 * belong to (spec addendum §P5.D7).
 */
public final class WorldOverlay {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath("renderable", "labels");
    private static final long HIGHLIGHT_MILLIS = 10_000;
    private static final int BACKGROUND = 0x90000000;
    private static final int HIGHLIGHT_COLOR = 0xFFFF4D4D;
    private static final int MIN_HIGHLIGHT = 8;
    /**
     * GUI depth between one label and the next, so that where two overlap the later one covers the earlier one's
     * text, though every background is drawn before any text (spec addendum §P5.D8). Seventy-five labels span
     * under four units.
     */
    private static final float LABEL_DEPTH_STEP = 0.05f;

    /**
     * One label, built once per result. Block entities and sections carry the position they were profiled
     * at; entities are looked up per frame by id. Every label is drawn only in the dimension it was measured
     * in; for an entity that is the session's dimension, which matters for an entity that keeps its id
     * through a portal, such as another player.
     */
    private record Label(String dimension, double x, double y, double z, int entityId, String text, int color) {
    }

    private static List<Label> labels = List.of();
    /** Where this frame's labels go, in drawing order. Sized with the labels, so a frame allocates nothing. */
    private static int[] drawnLabel = new int[0];
    private static int[] drawnX = new int[0];
    private static int[] drawnY = new int[0];
    /** Null until first read, then whatever the config said or the user last chose. */
    private static Boolean enabled;

    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    /** The GUI pose moved to one label's depth; reused every frame, render thread only. */
    private static final Matrix4f LABEL_POSE = new Matrix4f();
    private static final Vector4f SCRATCH = new Vector4f();
    private static Vec3 cameraPos = Vec3.ZERO;
    private static boolean haveMatrices;
    private static ResourceKey<Level> lastDimensionKey;
    private static String lastDimension = "";

    private static BlockPos highlight;
    private static int highlightSize = 1;
    /** The dimension of the row the highlight came from; it is drawn only there. */
    private static String highlightDimension = "";
    private static long highlightUntilMillis;
    /** The depth the highlight is drawn at this frame: above every label, as when it was drawn after them. */
    private static float highlightDepth;

    private WorldOverlay() {
    }

    /** Mod bus. */
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, WorldOverlay::render);
    }

    /** {@code dimension} is the dimension the session was taken in; every label is scoped to it. */
    public static void setResult(SessionResult r, String dimension) {
        int n = RenderableConfig.OVERLAY_LABEL_COUNT.get();
        long sessionTotal = r.stats().totalNanos();
        int frames = r.frameCount();
        Set<ProfileKey> flagged = r.hitchFlaggedKeys();
        List<Label> out = new ArrayList<>(3 * n);
        for (RankedEntry e : head(Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY), n)) {
            BlockEntityKey k = (BlockEntityKey) e.key();
            out.add(label(e, flagged, frames, sessionTotal, k.dimension(), k.x() + 0.5, k.y() + 1.25, k.z() + 0.5, -1));
        }
        for (RankedEntry e : head(Ranking.byKey(r, ProfileKey.Category.ENTITY), n)) {
            EntityKey k = (EntityKey) e.key();
            out.add(label(e, flagged, frames, sessionTotal, dimension, 0, 0, 0, k.entityId()));
        }
        for (MeshEntry e : head(Ranking.mesh(r, ProfileKey.Category.SECTION, Ranking.MeshSort.CALLS), n)) {
            SectionKey k = (SectionKey) e.key();
            String text = String.format(Locale.ROOT, "section %d %d %d: %d rebuilds, %s ms",
                    k.x(), k.y(), k.z(), e.calls(), Fmt.ms(e.nanos()));
            out.add(new Label(k.dimension(), k.centerX() + 0.5, k.centerY() + 0.5, k.centerZ() + 0.5, -1, text, Colors.YELLOW));
        }
        labels = List.copyOf(out);
        drawnLabel = new int[labels.size()];
        drawnX = new int[labels.size()];
        drawnY = new int[labels.size()];
    }

    private static Label label(RankedEntry e, Set<ProfileKey> flagged, int frames, long sessionTotal,
                               String dimension, double x, double y, double z, int entityId) {
        boolean isFlagged = flagged.contains(e.key());
        String text = (isFlagged ? "! " : "") + e.typeId() + " " + Fmt.us(e.usPerFrame(frames)) + " us";
        int color = isFlagged ? Colors.FLAGGED : Colors.heat(e.sharePercent(sessionTotal));
        return new Label(dimension, x, y, z, entityId, text, color);
    }

    public static void clear() {
        labels = List.of();
    }

    /** Entity ids and dimension names restart in the next world, so a result's labels do not outlive the connection. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        highlight = null;
    }

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

    /** Draw a wireframe box around the block, in {@code dimension} only, for ten seconds. */
    public static void highlight(BlockPos pos, String dimension) {
        highlight(pos, 1, dimension);
    }

    /**
     * Draw a wireframe box around the {@code size}-block cube whose minimum corner is {@code min}, for ten seconds,
     * in {@code dimension} only: the dimension of the row it came from (spec addendum §P5.D7).
     */
    public static void highlight(BlockPos min, int size, String dimension) {
        highlight = min.immutable();
        highlightSize = Math.max(1, size);
        highlightDimension = dimension;
        highlightUntilMillis = System.currentTimeMillis() + HIGHLIGHT_MILLIS;
    }

    /** Game bus: captures the camera and view-projection matrix whenever there is something to draw. */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        boolean wantLabels = isEnabled() && !labels.isEmpty();
        if ((!wantLabels && highlight == null) || Minecraft.getInstance().level == null) {
            haveMatrices = false;
            return;
        }
        VIEW_PROJECTION.set(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        cameraPos = event.getCamera().getPosition();
        haveMatrices = true;
    }

    /** GUI layer: projects labels and the highlight to the screen and draws them over everything. */
    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (!haveMatrices) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        ResourceKey<Level> dimKey = mc.level.dimension();
        if (dimKey != lastDimensionKey) {
            lastDimensionKey = dimKey;
            lastDimension = dimKey.location().toString();
        }
        Vec3 cam = cameraPos;
        int guiW = g.guiWidth();
        int guiH = g.guiHeight();

        highlightDepth = 0;
        if (isEnabled() && !labels.isEmpty()) {
            drawLabels(g, mc, cam, guiW, guiH);
        }
        drawHighlight(g, cam, guiW, guiH);
    }

    /**
     * Draws the labels in two batches (spec addendum §P5.D8): every background, then every text. Drawn a label at
     * a time, each background and each text was a batch of its own, 150 draws for 75 labels, because the GUI's fill
     * and text render types share one buffer and every switch between them ends a batch; {@code drawManaged} alone
     * does not change that. The buffer source is written to directly, as {@link #drawHighlight} does. Each label
     * is drawn at its own depth, so where two overlap the later one hides the earlier one's text; drawn in turn,
     * its translucent background only dimmed it.
     */
    private static void drawLabels(GuiGraphics g, Minecraft mc, Vec3 cam, int guiW, int guiH) {
        int maxDist = RenderableConfig.OVERLAY_MAX_DISTANCE.get();
        double maxDistSq = (double) maxDist * maxDist;
        int lineH = mc.font.lineHeight;
        Matrix4f pose = g.pose().last().pose();
        List<Label> all = labels;

        VertexConsumer backgrounds = g.bufferSource().getBuffer(RenderType.gui());
        int drawn = 0;
        for (int i = 0; i < all.size(); i++) {
            Label l = all.get(i);
            if (!lastDimension.equals(l.dimension())) continue;
            double x;
            double y;
            double z;
            if (l.entityId() >= 0) {
                Entity ent = mc.level.getEntity(l.entityId());
                if (ent == null || ent == mc.getCameraEntity()) continue;
                Vec3 p = ent.position();
                x = p.x;
                y = p.y + ent.getBbHeight() + 0.5;
                z = p.z;
            } else {
                x = l.x();
                y = l.y();
                z = l.z();
            }
            if (cam.distanceToSqr(x, y, z) > maxDistSq) continue;

            SCRATCH.set((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), 1.0f).mul(VIEW_PROJECTION);
            float w = SCRATCH.w();
            if (w <= 0.001f) continue; // behind the camera
            float nx = SCRATCH.x() / w;
            float ny = SCRATCH.y() / w;
            if (nx < -1.1f || nx > 1.1f || ny < -1.1f || ny > 1.1f) continue;
            int sx = Math.round((nx + 1.0f) * 0.5f * guiW);
            int sy = Math.round((1.0f - ny) * 0.5f * guiH);

            int tw = mc.font.width(l.text());
            int left = sx - tw / 2;
            quad(backgrounds, pose, left - 2, sy - 1, left + tw + 2, sy + lineH, drawn * LABEL_DEPTH_STEP, BACKGROUND);
            drawnLabel[drawn] = i;
            drawnX[drawn] = left;
            drawnY[drawn] = sy;
            drawn++;
        }
        // Asking for the text's render type ends the backgrounds' batch; the flush below ends the text's.
        for (int d = 0; d < drawn; d++) {
            Label l = all.get(drawnLabel[d]);
            LABEL_POSE.set(pose).translate(0, 0, d * LABEL_DEPTH_STEP);
            mc.font.drawInBatch(l.text(), drawnX[d], drawnY[d], l.color(), false, LABEL_POSE, g.bufferSource(),
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }
        g.flush();
        highlightDepth = drawn * LABEL_DEPTH_STEP;
    }

    /**
     * Scratch space for {@link #drawHighlight}, reused every frame; render thread only, like {@link #SCRATCH}.
     * A highlight can outlive the session it came from and keep drawing while the next one samples, so
     * Renderable's own code allocates nothing per frame.
     */
    private static final float[] CLIP_X = new float[8];
    private static final float[] CLIP_Y = new float[8];
    private static final float[] CLIP_W = new float[8];
    private static final Vector3f VERTEX = new Vector3f();

    /** The camera's near plane in clip space: a point is in front of it when its w exceeds this. */
    private static final float NEAR_W = GameRenderer.PROJECTION_Z_NEAR;

    /** The twelve edges of a cube, as pairs of corner indices differing in one bit. */
    private static final int[] EDGES = {
            0, 1, 0, 2, 0, 4, 1, 3, 1, 5, 2, 3,
            2, 6, 3, 7, 4, 5, 4, 6, 5, 7, 6, 7};

    /**
     * Projects the highlighted cube's eight corners and draws its twelve edges over everything (spec
     * addendum §P4.C5), in the highlight's dimension only. An edge that crosses the camera's near plane is
     * clipped at that plane, so a box the camera stands inside still shows its edges. Each edge is drawn as one
     * two-pixel-wide quad, and the whole box is one flush. A wholly visible cube that projects smaller than
     * {@link #MIN_HIGHLIGHT} falls back to a small square instead, so a distant block stays findable.
     * Renderable's own code allocates nothing per frame.
     */
    private static void drawHighlight(GuiGraphics g, Vec3 cam, int guiW, int guiH) {
        BlockPos p = highlight;
        if (p == null) return;
        if (System.currentTimeMillis() > highlightUntilMillis) {
            highlight = null;
            return;
        }
        if (!lastDimension.equals(highlightDimension)) return;
        int inFront = 0;
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double x = p.getX() + ((i & 1) == 0 ? -0.01 : highlightSize + 0.01);
            double y = p.getY() + ((i & 2) == 0 ? -0.01 : highlightSize + 0.01);
            double z = p.getZ() + ((i & 4) == 0 ? -0.01 : highlightSize + 0.01);
            SCRATCH.set((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), 1.0f).mul(VIEW_PROJECTION);
            CLIP_X[i] = SCRATCH.x();
            CLIP_Y[i] = SCRATCH.y();
            CLIP_W[i] = SCRATCH.w();
            if (CLIP_W[i] <= NEAR_W) continue;
            inFront++;
            float sx = screenX(CLIP_X[i], CLIP_W[i], guiW);
            float sy = screenY(CLIP_Y[i], CLIP_W[i], guiH);
            minX = Math.min(minX, sx);
            maxX = Math.max(maxX, sx);
            minY = Math.min(minY, sy);
            maxY = Math.max(maxY, sy);
        }
        if (inFront == 0) return; // entirely behind the camera

        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f pose = g.pose().last().pose();
        if (inFront == 8 && maxX - minX < MIN_HIGHLIGHT && maxY - minY < MIN_HIGHLIGHT) {
            // Only an unclipped cube can be distant. It projects to a few pixels, so mark it with the
            // MIN_HIGHLIGHT square instead; centre lines 1 px inside, so the 2 px lines end flush with it.
            float cx = (minX + maxX) * 0.5f;
            float cy = (minY + maxY) * 0.5f;
            float h = MIN_HIGHLIGHT * 0.5f - 1.0f;
            line(vc, pose, cx - h, cy - h, cx + h, cy - h);
            line(vc, pose, cx + h, cy - h, cx + h, cy + h);
            line(vc, pose, cx + h, cy + h, cx - h, cy + h);
            line(vc, pose, cx - h, cy + h, cx - h, cy - h);
        } else {
            for (int e = 0; e < EDGES.length; e += 2) {
                edge(vc, pose, EDGES[e], EDGES[e + 1], guiW, guiH);
            }
        }
        g.flush(); // what fill() does after every rectangle, once for the whole box
    }

    /**
     * One edge of the cube. An endpoint behind the near plane is moved along the edge onto it; the move is
     * made in clip space, where the edge is still a straight line, so the part in front is exact.
     */
    private static void edge(VertexConsumer vc, Matrix4f pose, int a, int b, int guiW, int guiH) {
        float ax = CLIP_X[a];
        float ay = CLIP_Y[a];
        float aw = CLIP_W[a];
        float bx = CLIP_X[b];
        float by = CLIP_Y[b];
        float bw = CLIP_W[b];
        if (aw <= NEAR_W && bw <= NEAR_W) return;
        if (aw <= NEAR_W) {
            float t = (bw - NEAR_W) / (bw - aw);
            ax = bx + t * (ax - bx);
            ay = by + t * (ay - by);
            aw = NEAR_W;
        } else if (bw <= NEAR_W) {
            float t = (aw - NEAR_W) / (aw - bw);
            bx = ax + t * (bx - ax);
            by = ay + t * (by - ay);
            bw = NEAR_W;
        }
        line(vc, pose, screenX(ax, aw, guiW), screenY(ay, aw, guiH), screenX(bx, bw, guiW), screenY(by, bw, guiH));
    }

    private static float screenX(float clipX, float w, int guiW) {
        return (clipX / w + 1.0f) * 0.5f * guiW;
    }

    private static float screenY(float clipY, float w, int guiH) {
        return (1.0f - clipY / w) * 0.5f * guiH;
    }

    /**
     * A line two GUI pixels wide with square ends, as one quad in the GUI buffer. The GPU rasterises and clips
     * it, so it costs the same however long it is and however far off screen it runs. The corners go round
     * the same way as {@link GuiGraphics#fill}'s, which the GUI's y-down projection keeps front-facing.
     */
    private static void line(VertexConsumer vc, Matrix4f pose, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = 1.0f;
        float uy = 0.0f;
        if (len > 1.0e-3f) {
            ux = dx / len;
            uy = dy / len;
        }
        // One pixel of cap along the line at each end, one pixel of half-width across it.
        float sx = x0 - ux;
        float sy = y0 - uy;
        float ex = x1 + ux;
        float ey = y1 + uy;
        vertex(vc, pose, sx - uy, sy + ux, highlightDepth, HIGHLIGHT_COLOR);
        vertex(vc, pose, ex - uy, ey + ux, highlightDepth, HIGHLIGHT_COLOR);
        vertex(vc, pose, ex + uy, ey - ux, highlightDepth, HIGHLIGHT_COLOR);
        vertex(vc, pose, sx + uy, sy - ux, highlightDepth, HIGHLIGHT_COLOR);
    }

    /** A filled rectangle as one quad, its corners in {@link GuiGraphics#fill}'s order. */
    private static void quad(VertexConsumer vc, Matrix4f pose, float x0, float y0, float x1, float y1, float z, int color) {
        vertex(vc, pose, x0, y0, z, color);
        vertex(vc, pose, x0, y1, z, color);
        vertex(vc, pose, x1, y1, z, color);
        vertex(vc, pose, x1, y0, z, color);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, float x, float y, float z, int color) {
        pose.transformPosition(x, y, z, VERTEX);
        vc.addVertex(VERTEX.x, VERTEX.y, VERTEX.z).setColor(color);
    }

    private static <T> List<T> head(List<T> list, int n) {
        return list.size() <= n ? list : List.copyOf(list.subList(0, n));
    }
}
```

Diff it against the old file before going on (`git diff src/main/java/io/github/edwardg117/renderable/ui/WorldOverlay.java`).
The only changes should be these:
- the `Font` and `LightTexture` imports;
- the class Javadoc's last sentence;
- `LABEL_DEPTH_STEP`, the three `drawn` arrays and their sizing in `setResult`, and `LABEL_POSE`;
- `highlightDimension` and `highlightDepth`;
- the two `highlight` signatures;
- the dimension lookup moved from `drawLabels` to `render`;
- `drawLabels`' two passes;
- `drawHighlight`'s dimension check and its Javadoc's "in the highlight's dimension only";
- `vertex`'s depth and colour, and `quad`.

- [ ] **Step 2: Pass the dimension from the screen**

In `ResultsScreen.highlightSelected`, replace:

```java
        if (selected.key() instanceof SectionKey k) {
            WorldOverlay.highlight(new BlockPos(k.x() * 16, k.y() * 16, k.z() * 16), 16);
        } else {
            WorldOverlay.highlight(selected.pos());
        }
```

with:

```java
        if (selected.key() instanceof SectionKey k) {
            WorldOverlay.highlight(new BlockPos(k.x() * 16, k.y() * 16, k.z() * 16), 16, k.dimension());
        } else {
            // A row with a position is a block entity's, and its key carries its dimension (Task 5).
            WorldOverlay.highlight(selected.pos(), selected.key().dimension());
        }
```

Task 7 replaces the screen and routes this through the view; this keeps the build whole until then.

- [ ] **Step 3: Build and run the tests**

Run: `./gradlew compileJava --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL both times, **207** tests.

- [ ] **Step 4: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/ui/WorldOverlay.java src/main/java/io/github/edwardg117/renderable/ui/screen/ResultsScreen.java
```

Report for the commit: `feat(ui): labels draw in two batches, and a highlight draws only in its dimension`.

---

### Task 7: The results screen keeps its view and checks each row's dimension

**Files:**
- Create: `src/main/java/io/github/edwardg117/renderable/ui/screen/ResultsView.java`
- Replace: `src/main/java/io/github/edwardg117/renderable/ui/screen/ResultsScreen.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/ProfilerController.java`
- Modify: `src/main/java/io/github/edwardg117/renderable/hooks/FrameEvents.java`

Spec addendum §P5.D6, the screen's part of §P5.D7, and §P5.D9's drawing, with deviations 3 and 5. No unit tests:
this is Minecraft-side and is checked in Task 9 and the in-pack acceptance.
- **The view.** `ResultsView` holds what `ResultsScreen` kept in fields until now. `ProfilerController` makes one
  for each new result and hands it to every screen it opens on that result.
- **Rows.** A row's dimension is its key's (Task 5), or the session's for an entity.
- **World gone.** The flag is now set on logout only.
- **Headers.** They use Task 5's layout and may reach 3 px left, as the layout assumes.

- [ ] **Step 1: Create `ResultsView`**

```java
package io.github.edwardg117.renderable.ui.screen;

import java.util.HashSet;
import java.util.Set;

/**
 * Where the reader is on the results screen (spec addendum §P5.D6): the tab, each tab's sort, the open groups,
 * the selected row and how far the list is scrolled. {@code ProfilerController} keeps one for the latest result,
 * so Highlight and Teleport, which close the screen, do not lose the reader's place; a new result gets a new
 * view, which opens on Advice. Render thread only.
 */
public final class ResultsView {
    /** The dimension the result was taken in: an entity row's, since entity keys record none (spec addendum §P5.D7). */
    final String dimension;
    Tab tab = Tab.ADVICE;
    final SortState sorts = new SortState();
    final Set<String> expanded = new HashSet<>();
    Row selected;
    double scroll;

    public ResultsView(String dimension) {
        this.dimension = dimension;
    }
}
```

- [ ] **Step 2: Replace `ResultsScreen.java`**

The new file keeps everything the old one did (5a's five tabs a row, 5b's Advice wrapping), with the view in
place of the `tab`, `sorts`, `expanded` and `selected` fields:

```java
package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.ProfilerController;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.Fmt;
import io.github.edwardg117.renderable.core.SectionKey;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.ui.WorldOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Spec §5.3. */
public final class ResultsScreen extends Screen {
    private static final int TAB_Y = 4;
    private static final int TABS_PER_ROW = 5;
    private static final int TAB_ROW_HEIGHT = 22;
    private static final int SUMMARY_Y = 52;
    private static final int HEADER_Y = 74;
    private static final int LIST_Y = 86;
    private static final int BOTTOM_BAR = 32;
    private static final int ROW_HEIGHT = 12;

    private final SessionResult result;
    private final Environment environment;
    /** The reader's place, which outlives this screen (spec addendum §P5.D6). */
    private final ResultsView view;

    private RowList list;
    private Button highlightButton;
    private Button teleportButton;
    /** Shown on Highlight and Teleport while they are disabled because the player has logged out since the session. */
    private final Tooltip worldGoneTooltip =
            Tooltip.create(Component.literal("You have left the world this session was taken in"));
    private int[] colX = {0};
    private int[] colW = {100};

    public ResultsScreen(SessionResult result, Environment environment, ResultsView view) {
        super(Component.literal("Renderable results"));
        this.result = result;
        this.environment = environment;
        this.view = view;
    }

    @Override
    protected void init() {
        // A resize runs init again with the list in place; opening the screen starts where the view left off.
        double scroll = list != null ? list.getScrollAmount() : view.scroll;
        int tabPitch = Math.min(88, (width - 8) / TABS_PER_ROW);   // 4 px margin each side
        int tabWidth = tabPitch - 4;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int x = 4 + (i % TABS_PER_ROW) * tabPitch;
            int y = TAB_Y + (i / TABS_PER_ROW) * TAB_ROW_HEIGHT;
            Button b = Button.builder(Component.literal(t.title()), btn -> selectTab(t)).bounds(x, y, tabWidth, 20).build();
            b.active = t != view.tab;
            addRenderableWidget(b);
        }
        list = new RowList(minecraft, this, width, height - LIST_Y - BOTTOM_BAR, LIST_Y, ROW_HEIGHT);
        addRenderableWidget(list);

        int by = height - 26;
        int bw = Math.min(90, (width - 24) / 5);   // five buttons, four 4 px gaps, 4 px margins
        int bx = 4;
        addRenderableWidget(Button.builder(Component.literal("Copy report"), b -> ProfilerController.get().copyReport())
                .bounds(bx, by, bw, 20).build());
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.literal("Open folder"), b -> ProfilerController.get().openFolder())
                .bounds(bx, by, bw, 20).build());
        bx += bw + 4;
        highlightButton = Button.builder(Component.literal("Highlight"), b -> highlightSelected())
                .bounds(bx, by, bw, 20).build();
        bx += bw + 4;
        teleportButton = Button.builder(Component.literal("Teleport"), b -> teleportToSelected())
                .bounds(bx, by, bw, 20).build();
        bx += bw + 4;
        addRenderableWidget(highlightButton);
        addRenderableWidget(teleportButton);
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(bx, by, bw, 20).build());

        rebuild();
        list.setScrollAmount(scroll);
    }

    /** A new tab starts at the top, with nothing selected. */
    private void selectTab(Tab t) {
        view.tab = t;
        view.selected = null;
        view.scroll = 0;
        list = null;   // so init reads the view's scroll, not this list's
        rebuildWidgets();
    }

    /** Keeps the scroll for the next time the screen opens on this result (spec addendum §P5.D6). */
    @Override
    public void removed() {
        if (list != null) view.scroll = list.getScrollAmount();
    }

    private void rebuild() {
        Tab tab = view.tab;
        int arrow = Math.max(font.width(" ^"), font.width(" v"));
        int[][] layout = tab.columns().layout(list.getRowWidth(), font::width, arrow);
        colX = layout[0];
        colW = layout[1];
        list.setLayout(colX, colW);
        // A body line is drawn two indents (24 px) in, and a single-cell row loses 8 px of margin.
        int wrapWidth = Math.max(40, list.getRowWidth() - 8 - 24);
        list.setRows(tab, Rows.build(tab, result, environment, view.sorts.sort(tab), view.sorts.ascending(tab),
                view.expanded, text -> TextWrap.wrap(text, wrapWidth, font::width)));
        if (view.selected != null && !list.reselect(view.selected)) view.selected = null;
        updateButtons();
    }

    boolean isExpanded(String typeId) {
        return typeId != null && view.expanded.contains(typeId);
    }

    void onRowClicked(Row row) {
        if (row.expandable()) {
            if (!view.expanded.add(row.typeId())) view.expanded.remove(row.typeId());
            double scroll = list.getScrollAmount();
            rebuild();
            list.setScrollAmount(scroll);   // opening a group should not jump the list back to the top
            return;
        }
        view.selected = row;
        updateButtons();
    }

    /** A click in the header band sorts by that column; see {@link SortState#click}. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= HEADER_Y - 2 && mouseY < HEADER_Y + font.lineHeight) {
            int clicked = columnAt(mouseX);
            if (clicked >= 0 && view.sorts.click(view.tab, clicked)) {
                rebuild();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** The column index under a screen x, or -1 outside the row. */
    private int columnAt(double mouseX) {
        int left = list.getRowLeft() + 4;
        if (mouseX < left || mouseX >= left + list.getRowWidth()) return -1;
        for (int i = colX.length - 1; i >= 0; i--) {
            if (mouseX >= left + colX[i]) return i;
        }
        return -1;
    }

    /**
     * Highlight and Teleport need a position or an entity, and the selected row's dimension must be the player's
     * (spec addendum §P5.D7). After a logout nothing in the result points at anything in the world.
     */
    private void updateButtons() {
        Row selected = view.selected;
        boolean worldGone = ProfilerController.get().resultWorldGone();
        boolean hasPos = selected != null && selected.pos() != null;
        boolean hasEntity = selected != null && selected.entityId() >= 0;
        String here = minecraft != null && minecraft.level != null ? minecraft.level.dimension().location().toString() : "";
        String there = selected == null ? here : dimensionOf(selected);
        boolean elsewhere = !there.equals(here);
        highlightButton.active = hasPos && !worldGone && !elsewhere;
        teleportButton.active = (hasPos || hasEntity) && !worldGone && !elsewhere;
        Tooltip why = worldGone ? worldGoneTooltip
                : elsewhere && (hasPos || hasEntity)
                ? Tooltip.create(Component.literal("This row is in " + there + "; you are in " + here))
                : null;
        highlightButton.setTooltip(why);
        teleportButton.setTooltip(why);
    }

    /** The dimension a row was measured in: its key's own, or for an entity the session's (spec addendum §P5.D7). */
    private String dimensionOf(Row row) {
        String own = row.key() == null ? null : row.key().dimension();
        return own != null ? own : view.dimension;
    }

    /** Highlights the selected row's block, or the full section for a Sections-tab row, in the row's dimension. */
    private void highlightSelected() {
        Row selected = view.selected;
        if (selected == null || selected.pos() == null) return;
        if (selected.key() instanceof SectionKey k) {
            WorldOverlay.highlight(new BlockPos(k.x() * 16, k.y() * 16, k.z() * 16), 16, k.dimension());
        } else {
            WorldOverlay.highlight(selected.pos(), dimensionOf(selected));
        }
        onClose();
    }

    /**
     * Sends a teleport for the selected row. A section row's stored position is the section's centre block,
     * which is solid ground for anything below the surface, so section rows keep the player's height, raised
     * until the player fits (spec addendum §P4.C3); block entity rows go one block above the block.
     */
    private void teleportToSelected() {
        Row selected = view.selected;
        if (selected == null || minecraft == null || minecraft.getConnection() == null) return;
        if (selected.pos() != null) {
            BlockPos p = selected.pos();
            int y = selected.key() instanceof SectionKey && minecraft.player != null && minecraft.level != null
                    ? standingY(minecraft.level, minecraft.player, p.getX(), p.getZ())
                    : p.getY() + 1;
            minecraft.getConnection().sendCommand("tp @s " + p.getX() + " " + y + " " + p.getZ());
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

    /**
     * The height a section-row teleport lands at: the player's own, raised to the first height at which the
     * player fits in the section's column, so it never ends inside a hill (spec addendum §P4.C3). {@code /tp}
     * centres whole-number X and Z in their block, so that is where the player's box is tested. A column the
     * client has not loaded has no collision shapes, so it keeps the player's height.
     */
    private static int standingY(ClientLevel level, LocalPlayer player, int x, int z) {
        int y = player.blockPosition().getY();
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(x + 0.5, y, z + 0.5);
        for (int up = 0; y + up < level.getMaxBuildHeight(); up++) {
            if (level.noBlockCollision(player, box.move(0, up, 0))) return y + up;
        }
        return y;
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
        Tab tab = view.tab;
        Tab.Columns columns = tab.columns();
        int active = view.sorts.column(tab);
        for (int i = 0; i < columns.size() && i < colX.length; i++) {
            Tab.Column column = columns.get(i);
            boolean sortable = column.sort().sortable();
            String arrow = i == active && sortable ? (view.sorts.ascending(tab) ? " ^" : " v") : "";
            // A right-aligned header may reach PADDING - 1 px left, as a number may; the layout widened its column
            // for the header and an arrow where it could (spec addendum §P5.D9). Truncate the name, never the arrow:
            // the arrow is the only sign of how the list is sorted.
            int room = colW[i] + (column.rightAlign() ? Tab.Columns.PADDING - 1 : 0);
            String h = font.plainSubstrByWidth(column.name(), Math.max(0, room - font.width(arrow))) + arrow;
            int colour = !sortable ? 0xA0A0A0 : i == active ? 0xFFFFFF : 0xC0C0C0;
            int cx = left + 4 + colX[i];
            if (column.rightAlign()) {
                g.drawString(font, h, cx + colW[i] - font.width(h), HEADER_Y, colour, false);
            } else {
                g.drawString(font, h, cx, HEADER_Y, colour, false);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
```

Before replacing, open the current file and check that it has nothing the new one lacks beyond the four fields
the view takes over: 5a's `TABS_PER_ROW = 5` and 5b's wrap lines in `rebuild` are in the new file. If anything
else was added since, keep it and tell the orchestrator.

- [ ] **Step 3: The controller keeps the view**

In `ProfilerController.java`:
- add `import io.github.edwardg117.renderable.ui.screen.ResultsView;`;
- after the field `private SessionResult lastResult;`, add:

```java
    /** Where the reader is on the results screen for {@link #lastResult} (spec addendum §P5.D6). */
    private ResultsView resultsView;
```

- in `finish`, after `lastResult = result;`, add `resultsView = new ResultsView(sessionDimension);`;
- in `openResults`, replace `new ResultsScreen(lastResult, lastEnvironment)` with
  `new ResultsScreen(lastResult, lastEnvironment, resultsView)`;
- replace the Javadoc of `resultWorldGone()` with:

```java
    /**
     * True when the player has logged out since the last result was taken, so its positions and entity ids no
     * longer mean anything (spec addendum §P4.B6). A change of dimension does not set it: the results screen
     * checks each row's own dimension instead (spec addendum §P5.D7). The report itself is still worth copying.
     */
```

- [ ] **Step 4: Only a logout makes the world gone**

In `FrameEvents.java`, replace:

```java
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        discard("level unloaded");
        worldGone();
    }
```

with:

```java
    /**
     * A client level unloads on every change of dimension, not only on logout. A running session is discarded
     * either way, but a finished result's rows stay good after a change of dimension: the results screen compares
     * each row's own dimension with the player's (spec addendum §P5.D7). So only {@link #onLoggingOut} marks the
     * result's world as gone.
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        discard("level unloaded");
    }
```

- [ ] **Step 5: Build and run the tests**

Run: `./gradlew compileJava --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL both times, **207** tests. Then
`grep -rn "WorldOverlay.highlight(\|new ResultsScreen(" src/main/java` shows only the calls this task wrote.

- [ ] **Step 6: Stage**

```bash
git add src/main/java/io/github/edwardg117/renderable/ui/screen/ResultsView.java src/main/java/io/github/edwardg117/renderable/ui/screen/ResultsScreen.java src/main/java/io/github/edwardg117/renderable/ProfilerController.java src/main/java/io/github/edwardg117/renderable/hooks/FrameEvents.java
```

Report for the commit: `feat(ui): the results screen keeps the reader's place and checks each row's dimension`.

---

### Task 8: Documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md`
- Modify: `docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md`
- Modify: `CLAUDE.md`

Each replacement below quotes the text to find. It is the text as plans 5a and 5b leave it. If a passage is not
there, stop and report rather than guess.

- [ ] **Step 1: README, "Using it"**

Replace item 4:

```markdown
4. On the results screen, click a column header to sort by it; click it again to reverse. Each list opens in
   the same order as the text report, the active column carries `v` (descending) or `^` (ascending), and a
   name column sorts A to Z on its first click. Frames and Hitches have a fixed order. A number too wide for
   its column is rounded to a whole number, and shows as `###` only if it still does not fit; widen the window
   or lower the GUI scale to read it. **Highlight** draws a wireframe box around the selected block, or around
   the whole 16-block section for a Sections row, for ten seconds and through walls. **Teleport** from a
   Sections row keeps your height, raised until you fit in that column if the game has it loaded, so a
   loaded hill does not swallow you; a column it has not loaded keeps your height as it is.
   Highlight and Teleport are greyed out once you leave the world or dimension the session was taken in, and
   hovering over them says so.
```

with:

```markdown
4. On the results screen, click a column header to sort by it; click it again to reverse. Each list opens in
   the same order as the text report, the active column carries `v` (descending) or `^` (ascending), and a
   name column sorts A to Z on its first click, ignoring case and reading numbers as numbers, so section -10
   comes before section -2. Frames and Hitches have a fixed order. A header too wide for its column widens the
   column a little, at the name column's expense. A number too wide for its column is rounded to a whole
   number, and shows as `###` only if it still does not fit; widen the window or lower the GUI scale to read
   it. **Highlight** draws a wireframe box around the selected block, or around the whole 16-block section for
   a Sections row, for ten seconds and through walls, in that row's dimension only. **Teleport** from a
   Sections row keeps your height, raised until you fit in that column if the game has it loaded, so a
   loaded hill does not swallow you; a column it has not loaded keeps your height as it is.
   Highlight and Teleport are greyed out while the selected row is in another dimension, and for good once you
   log out of the world the session was taken in; hovering over them says which. They close the screen, but
   reopening it with `/renderable results` comes back to the same tab, sort, open groups and selection, until
   the next session.
```

- [ ] **Step 2: README, "Reading a report"**

1. Replace this text, which is part of a line:

```markdown
pass, which draws everything a second time.
```

   with:

```markdown
pass, which draws everything a second time; the text report leaves the column out of a table in which no row
  has any, as when no shader pack is on.
```

2. Replace this text, which is part of a line:

```markdown
- **Particles** are ranked by class.
```

   with:

```markdown
- **Particles** are ranked by class, named as the Flywheel classes are (see **Flywheel** below).
```

3. Replace:

```markdown
- **Hooks applied** lists the hooks that have fired at least once since the game started, not
  only during this session.
```

   with:

```markdown
- **Hooks** are listed three ways in the environment. **Installed** hooks are in place: each class they
  hook has loaded and still has the method and the call the hook needs. **Fired** hooks have run while a
  session was sampling, in any session since the game started, not only this one. A hook that **did not apply**
  found its target
  changed, usually by a mod update, and the tables it feeds say so. A hook in none of the lists hooks code
  that has not loaded in this client run, or belongs to a hook class Mixin rejected as a whole, which the game
  log records. A hook can be both fired and not applied when only part of it applied, such as one of
  Sodium's three mesh hooks.
```

4. In the schema 5 paragraph, replace:

```markdown
`tasksUs` and `capUs`, or read `swapUs`.
```

   with:

```markdown
`tasksUs` and `capUs`, or read `swapUs`. `environment.hooksApplied` is renamed `hooksFired`, and
`hooksInstalled` and `hooksFailed` join it.
```

- [ ] **Step 3: Base spec §4.5**

1. Replace:

```markdown
the affected tab shows "hook did not apply for
  <mod> <version>".
```

   with:

```markdown
the affected tab says the hook did not apply
  (phase 5, below).
```

2. Replace:

```markdown
- `Probe` records which hooks applied. The report's environment header lists them.
```

   with:

```markdown
- The report's environment header lists hooks three ways (phase 5, spec addendum §P5.D1).
  - **Installed.** As each hooked class loads, `RenderableMixinPlugin.postApply` checks that every injector's
    target method is in it and, for an `INVOKE` injection point, that the call is too. It checks the target
    rather than the handler, because MixinExtras applies `@WrapOperation` and `@WrapMethod` after `postApply`.
  - **Fired.** `Probe` records each hook the first time it runs while a session is sampling.
  - **Did not apply.** An injector's target was missing.

  A hook whose classes never loaded is in none of the three. The empty tables use these lists to tell "the
  hook did not apply", "the code never ran" and "nothing happened" apart. `HookInstalls.HOOKS` maps each
  injector to its hook, and `HookInstallsTest` keeps that table complete.
```

- [ ] **Step 4: Base spec §5.3**

1. Replace this text, which is part of a line:

```markdown
A to Z for a name), clicking
```

   with:

```markdown
A to Z for a name, ignoring case and reading digit runs as numbers
since phase 5), clicking
```

2. Replace:

```markdown
Both are disabled, with a
tooltip saying why, once the player leaves the world or dimension the session was taken in.
```

   with:

```markdown
Both are disabled, with a
tooltip saying why, while the selected row's dimension (a block entity's or section's own, an entity's the
session's) is not the player's, and for good once the player logs out of the world the session was taken in;
a highlight is drawn only in its row's dimension. The screen keeps its tab, each tab's sort, the open groups,
the selected row and the scroll position for the latest result, so closing it for Highlight or Teleport does
not lose the reader's place; a new result opens on Advice (phase 5).
```

3. Replace:

```markdown
shared with the text report; the screen writes its column names out in `Tab`, and `TabTest` holds them equal
to `RankedTable`'s.
```

   with:

```markdown
shared with the text report, as the Blocks and Sections cell text is in `core/MeshTable` (phase 5); the screen
writes its column names out in `Tab`, and `TabTest` holds them equal to those classes'. A header too wide for
its column widens it, taking at most a quarter of the name column (phase 5).
```

- [ ] **Step 5: Base spec §6.1 and §6.2**

Each find text in items 1 to 4 and 6 is part of a line; the rest of the line stays.

1. Replace `Renderable hooks applied.` with `Renderable hooks are installed, which fired and which did not apply.`
2. Replace:

```markdown
**by position** (top 20), and the count of GC-suspect frames.
```

   with:

```markdown
**by position** (top 20), and the count of GC-suspect frames. The shadow column is left out of a table in
   which no row has shadow time (phase 5).
```

3. Replace:

```markdown
**individuals** (top 20), and the count of GC-suspect frames.
```

   with:

```markdown
**individuals** (top 20), and the count of GC-suspect frames, with the shadow column as for block entities.
```

4. Replace:

```markdown
**Particles** (top 20), and the count of GC-suspect frames.
```

   with:

```markdown
**Particles** (top 20), by short class name as the Flywheel table names classes (phase 5; the JSON keeps
   full names), and the count of GC-suspect frames.
```

5. Replace:

```markdown
or one line saying why
   there are none: Sodium hooks were not active, or, when a Sodium hook fired, nothing was meshed or
   rebuilt while sampling.
```

   with:

```markdown
or one line saying why
   there are none: Sodium is not installed, its mesh hook did not apply or never loaded, or nothing was
   meshed or rebuilt while sampling.
```

6. Replace:

```markdown
**Flywheel**: bucket share and visual counts.
```

   with:

```markdown
**Flywheel**: bucket share and visual counts, or a line saying the samples found no visuals alive.
```
7. In §6.2, replace `JSON is complete, not truncated to top-N.` with:

```markdown
The environment's `hooksInstalled`, `hooksFired` and `hooksFailed` arrays list hooks by name; schema 4 called
`hooksFired` `hooksApplied` (phase 5).

JSON is complete, not truncated to top-N.
```

8. In §9's unit list, replace:

```markdown
- `TextReportWriter`: fixed-width alignment, top-N truncation, "Sodium hooks not active" path.
```

   with:

```markdown
- `TextReportWriter`: fixed-width alignment, top-N truncation, and each table's empty-state sentence.
```

- [ ] **Step 6: The phase 5 spec's corrections**

In `2026-09-26-renderable-phase-5-design.md`:

1. In §P5.D1, replace this text, which is part of a line:

```markdown
in a later pass than `postApply`.
```

   with:

```markdown
in a later pass than `postApply` (verified in MixinExtras 0.5.0: `@WrapOperation` is a late injection, and
  `@WrapMethod` only offers a wrapper, which its applicator extension applies after the class's mixins).
```

2. In §P5.D1, replace:

```markdown
- **Two lists.** The environment carries `hooksInstalled` and `hooksFired`; `hooksFired` is the old list,
  renamed.
```

   with:

```markdown
- **Three lists.** The environment carries `hooksInstalled`, `hooksFired` (the old list, renamed) and
  `hooksFailed`, the hooks with an injector whose target was missing (plan 5d, deviation 1). Without the
  third, "did not apply" could not be told from "never loaded". A failed injector outweighs a firing: a hook
  in both lists applied only in part.
- **Mapping.** `HookInstalls.HOOKS` maps each injector, by mixin and handler, to its hook. `HookInstallsTest`
  reads every mixin's class file and fails when an injector is missing from it.
- **Limit.** A third-party mixin that Mixin rejects as a whole, for a shadow or accessor that no longer
  matches, never reaches `postApply`. Its hooks read as never loaded, and Sodium's empty tables then point at
  the game log.
```

3. In §P5.D2, after the bullet that ends `printing a header and dashes.`, add:

```markdown
- **Sodium's tables** tell four cases apart: "(no data: Sodium is not installed)", "(no data: Sodium's mesh
  hook did not apply)", "(no data: Sodium's mesh hook never loaded; the game log says why)", and "(no data:
  no blocks were meshed)" or "(no data: no sections were rebuilt)" (plan 5d).
- **Particles** keep "(no particles were rendered)", or say "(hook not installed)".
- **Wording.** The Flywheel sentence is "(Flywheel sampled 3 times and found no visuals alive)", or "sampled
  once".
```

4. In §P5.D4, replace:

```markdown
package segment, and the full name if that still collides. The JSON keeps full names.
```

   with:

```markdown
package segment, and the full name if that still collides. The JSON keeps full names. The short names are
  for the particle tables: a hitch's contributor lines and the recommendations keep each key's own label, as
  for every other kind of key (plan 5d, deviation 4).
```

5. In §P5.D6, replace:

```markdown
The tab, each tab's sort and the open groups live in `ProfilerController`, keyed to the current result, so
```

   with:

```markdown
The tab, each tab's sort, the open groups, the selected row and the scroll position live in a `ResultsView`
that `ProfilerController` keeps for the current result (plan 5d, deviation 3), so
```
6. In §P5.D8, replace:

```markdown
The overlay's labels are drawn inside `GuiGraphics.drawManaged`, so 75 labels batch instead of costing
150 draws a frame. Check the draw order of overlapping labels when doing it.
```

   with:

```markdown
The overlay's labels are drawn in two batches, every background and then every text, instead of costing 150
draws a frame for 75 labels. `drawManaged` alone would not do it: the GUI's fill and text render types share
one buffer, and each switch between them ends a batch. Each label is drawn at its own depth, so where two
overlap the later one covers the earlier one's text: fully, where its translucent background used to dim it
(plan 5d, deviation 2).
```

7. In §P5.D9, replace:

```markdown
The screen widens that column until the header fits.
```

   with:

```markdown
The screen widens a column until its header fits, with room for the sort arrow when it sorts, and a header
may reach 3 px left, as a number may. The pixels come from the name column, which gives at most a quarter of
its width, to the smallest shortfalls first (plan 5d, deviation 5).
```

8. In §P5.A9, replace:

```markdown
`hooksInstalled` is added (P5.D1).
```

   with:

```markdown
`hooksInstalled` and `hooksFailed` are added (P5.D1).
```

- [ ] **Step 7: CLAUDE.md**

Under `## Rules that keep the design intact`, after the bullet that ends `so do not try to hook it.` (plan 5a's),
add:

```markdown
- Every mixin injector is listed in `HookInstalls.HOOKS` (or `BOUNDARIES`), which the report's installed
  and failed hook lists come from. `HookInstallsTest` fails for one that is not.
```

- [ ] **Step 8: Check and stage**

Run: `git diff --stat` and read each changed paragraph once more against the code: the sentences quoted must
match `EmptyStates` exactly, and the README's hook-list names must match the report's lines (`hooks installed`,
`hooks fired`, `hooks that did not apply`).

```bash
git add README.md CLAUDE.md docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md docs/superpowers/specs/2026-09-26-renderable-phase-5-design.md
```

Report for the commit: `docs: README and specs for phase 5d`.

---

### Task 9: Check it in the dev client

**Files:** none in the repository.

As in plan 5a Task 11. The user's real Gears & Frontiers client may be running, so stop only the dev client,
through its Gradle wrapper (plan 5a Task 11, Step 4, has the commands and the rules).

This task proves D1 and D2 in a real game: the plugin finds every hook's target in the real classes, and the
report shows it. The screen and overlay changes (D6 to D9) need a person, and are in the in-pack acceptance that
plan 5c runs.

- [ ] **Step 1: Build and test**

Run: `./gradlew build --no-daemon`, then `./gradlew test --rerun --no-daemon`
Expected: BUILD SUCCESSFUL, **207** tests.

- [ ] **Step 2: A run with the pack's rendering mods**

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
print("schema", r["schema"])
for k in ("hooksInstalled", "hooksFired", "hooksFailed", "hooksApplied"):
    print(k, "=", env.get(k))
EOF
f=$(ls -t run/renderable/*.txt | head -1)
grep -n "hooks installed\|hooks fired\|hooks that did not apply" "$f"
sed -n '/^Block entities by type/,+1p' "$f"
sed -n '/^Blocks (mesh/,+1p' "$f"
grep -c "could not check the hooks" run/logs/latest.log
```

Expected:
- `schema` is 5, and `hooksApplied` is `None`;
- `hooksFailed` is empty, and the report says `hooks that did not apply: none`;
- `hooksInstalled` holds at least BLOCK_ENTITY_RENDER, ENTITY_RENDER, PARTICLE_RENDER, PARTICLE_TICK,
  BLOCK_ENTITY_TICK, ENTITY_TICK, LIGHT, VERTICES, TASKS, FRAME_CAP, HUD, HUD_LAYERS, PACKETS, RIDER_TICK,
  FLYWHEEL, SODIUM_MESH and SODIUM_UPLOAD. PAYLOADS appears if `MainThreadPayloadHandler` loaded;
  ACCELERATED_VERTICES does not, since that mod is not in the dev client;
- the block entities header has no `shadow` column (no shader pack is on);
- the Blocks table has rows, or says `(no data: no blocks were meshed)`;
- the log has no "could not check the hooks" line (the count is 0).

A hook missing from `hooksInstalled` that should be there means either its class did not load or its
`HookInstalls.HOOKS` entry does not match the handler. Say which, with the evidence, before going on.

- [ ] **Step 3: A plain run**

Repeat Step 2 without `-PpackMods`. Expected:
- `hooksInstalled` has no SODIUM_ or FLYWHEEL entries;
- `hooksFailed` is still empty;
- the Blocks and Sections tables say `(no data: Sodium is not installed)`.

- [ ] **Step 4: Report**

Report to the orchestrator: both runs' output, and anything unexpected. Nothing is staged in this task.

---

## After this plan

Leave `phase-5d-polish` unmerged. Plan 5c branches from it (spec addendum §P5.2). Its in-pack acceptance checks
what this plan could not check headlessly:
- the view kept across Highlight and a reopen;
- Highlight and Teleport greyed in another dimension, with their tooltip, and working again on return;
- a highlight drawn only in its dimension;
- overlapping labels, where the later label covers the earlier one's text;
- the `render us/f` header whole at GUI scale 3 on a 2560 px window, with and without its arrow;
- a Sections list sorted by name in natural order;
- `hooks that did not apply: none` in the pack's report. The check assumes MixinExtras applies `@WrapOperation`
  and `@WrapMethod` after the plugin's `postApply`, as 0.5.0 does. A pack mod bundling a MixinExtras that
  changed that would make working hooks read as not applied.
