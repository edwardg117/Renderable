package io.github.edwardg117.renderable.report;

import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.FlywheelStats;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.TestSessions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        assertTrue(s.contains("  Time split (avg ms/frame): tick 1.00 [entity tick 0.20] | block entity 7.40 | entity 0.80 | other render 2.10 | swap/wait 7.90\n"), s);
        assertTrue(s.contains("  GC: 1 collections, 5 ms paused\n"), s);
        assertTrue(s.contains("  Renderable overhead: about 0.00 ms over 120 timed calls and 0 vertex calls\n"), s);
    }

    @Test
    void hitchSectionListsWorstFramesWithTopContributors() {
        String s = TextReportWriter.write(r, env, "0.1.0");
        int at = s.indexOf("Hitches (worst 25)\n");
        assertTrue(at > 0, s);
        String section = s.substring(at, s.indexOf("\n\n", at));
        assertTrue(section.contains("\n  #1  frame 10 @ 0.2s  80.0 ms  |  tick 1.0 [entity tick 0.2] | block entity 62.5 | entity 0.8 | other render 2.1 | swap 13.6  |  gc 0 ms\n"), section);
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
        assertTrue(s.contains("Blocks (mesh build, top 20 by mesh ms/s)\n  (no data: Sodium hooks were not active)\n"), s);
        assertTrue(s.contains("Sections (mesh rebuilds, top 20 by rebuilds)\n  (no data: Sodium hooks were not active)\n"), s);
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
        assertTrue(s.contains("  Renderable overhead: about 0.01 ms over 280 timed calls and 0 vertex calls\n"), s);
        assertFalse(s.contains("Attribution stack reset"), s);

        int at = s.indexOf("Particles (top 20)\n");
        assertTrue(at > 0, s);
        String[] lines = s.substring(at).split("\n", 5);
        assertTrue(lines[1].startsWith("  class"), lines[1]);
        assertTrue(lines[1].endsWith("avg live  verts/f  gc f"), lines[1]);
        assertEquals(lines[1].length(), lines[3].length());
        assertTrue(lines[3].startsWith("  FlameParticle"), lines[3]);
        assertTrue(lines[3].contains("300.0") && lines[3].contains("100.0") && lines[3].contains("2.1%") && lines[3].contains("3.0"), lines[3]);

        assertTrue(s.contains("Flywheel\n  bucket 1.20 ms/frame (6.3% of frame time), backend flywheel:instancing\n"
                + "  visuals: 120.0 block entities, 10.0 entities, 0.0 effects (average over 3 samples)\n"), s);
        // The table prints the simple class name, as the screen does; the JSON keeps the fully qualified one.
        assertTrue(s.contains("\n  PressVisual "), s);
        assertFalse(s.contains(TestSessions.PRESS_VISUAL), s);
        int row = s.indexOf("\n  PressVisual ") + 1;
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

    /** Rebuilds a SessionResult with a different unbalancedFrames and flywheel, everything else from base. */
    static SessionResult withFlywheel(SessionResult base, FlywheelStats fw, int unbalanced) {
        return new SessionResult(base.config(), base.wallStartMillis(), base.frames(), base.accumulators(),
                base.stats(), base.hitchThresholdNanos(), base.hitches(), base.timedCalls(), base.vertexCalls(),
                base.selfNanos(), base.overheadEstimateMs(), unbalanced, fw, base.mesh());
    }

    @Test
    void flywheelHookMissingLineAndVisualOrder() {
        SessionResult withFw = withFlywheel(TestSessions.sample(),
                new FlywheelStats(3, 120, 10, 0, Map.of("b", 1.0, "a", 2.0, "c", 2.0)), 0);
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("flywheel", "1.0.6");
        m.put("flywheelBackend", "flywheel:instancing");
        String s = TextReportWriter.write(withFw, Environment.of(m, List.of()), "0.2.0");
        assertTrue(s.contains("Flywheel\n  (hook did not apply for flywheel 1.0.6; the bucket is missing but visual counts were still sampled)\n"
                + "  bucket 0.00 ms/frame (0.0% of frame time), backend flywheel:instancing\n"
                + "  visuals: 120.0 block entities, 10.0 entities, 0.0 effects (average over 3 samples)\n"), s);
        int a = s.indexOf("\n  a ");
        int c = s.indexOf("\n  c ");
        int b = s.indexOf("\n  b ");
        assertTrue(a > 0 && c > 0 && b > 0, s);
        assertTrue(a < c && c < b, s);
    }

    @Test
    void unbalancedFramesLine() {
        SessionResult withFw = withFlywheel(TestSessions.sample(), FlywheelStats.none(), 2);
        String s = TextReportWriter.write(withFw, env, "0.1.0");
        assertTrue(s.contains("  Renderable overhead: about "), s);
        assertTrue(s.contains("  Attribution stack reset on 2 frames (a hook threw past its end call; buckets on those frames are incomplete)\n\n"), s);
    }

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
        assertTrue(s.contains("\n" + TextReportWriter.MESH_NOTE + "\n"), s);

        int table = s.indexOf("Block entities by position");
        int press = s.indexOf("create:mechanical_press @ 100 64 -20", table);   // the Hitches section lists the same label first
        String pressRow = s.substring(press, s.indexOf('\n', press));
        assertTrue(pressRow.contains("100.0"), "press renders 2000 vertices over 20 frames: " + pressRow);

        LinkedHashMap<String, String> hookOnly = new LinkedHashMap<>();
        String empty = TextReportWriter.write(TestSessions.phase2(), Environment.of(hookOnly, List.of("SODIUM_MESH")), "0.3.0");
        assertTrue(empty.contains("Blocks (mesh build, top 20 by mesh ms/s)\n  (no data: no blocks were meshed)\n"), empty);
        assertTrue(empty.contains("Sections (mesh rebuilds, top 20 by rebuilds)\n  (no data: no sections were rebuilt)\n"), empty);
        assertFalse(empty.contains(TextReportWriter.MESH_NOTE), empty);
    }

    @Test
    void gcSuspectPeaksAndContributorsAreMarked() {
        String s = TextReportWriter.write(TestSessions.gcSession(), env, "0.4.0");

        assertTrue(s.contains(TextReportWriter.GC_NOTE + "\n"), s);
        assertTrue(s.contains(TextReportWriter.GC_COLUMN_NOTE + "\n"), s);
        assertTrue(s.contains("\n      40.00 ms  create:mechanical_press @ 100 64 -20  [gc]\n"), s);
        assertTrue(s.contains("\n      1.00 ms  minecraft:cow #7\n"),
                "the cow's own 1 ms render should still print as a contributor: " + s);
        assertFalse(s.contains("minecraft:cow #7  [gc]"),
                "the marker comes from the entry, not the frame: the cow's 1 ms is nowhere near the 40 ms pause: " + s);

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

        // The cow was never GC-suspect, in contrast to the press above: a "gc f 0" row beside a "gc f 1" one.
        int entAt = s.indexOf("Entities (individuals) (top 20)\n");
        assertTrue(entAt > 0, s);
        String[] entLines = s.substring(entAt).split("\n", 5);
        assertTrue(entLines[3].startsWith("  minecraft:cow #7"), entLines[3]);
        int entGcColumnEnd = entLines[1].indexOf("gc f") + "gc f".length() - 1;
        assertEquals('0', entLines[3].charAt(entGcColumnEnd), "the cow was never GC-suspect: " + entLines[3]);
    }

    @Test
    void aPeakThatWasOnlyEverGcSuspectPrintsADash() {
        String out = TextReportWriter.write(TestSessions.everyFrameGcSuspect(), env, "0.4.0");
        int at = out.indexOf("Block entities by position (top 20)\n");
        String row = out.substring(at).split("\n", 5)[3];
        assertTrue(row.contains(" - "), "the only frame was GC-suspect, so there is no clean peak: " + row);
    }

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

    @Test
    void theReportPrintsFlywheelClassesTheWayTheScreenDoes() {
        SessionResult withFw = withFlywheel(TestSessions.sample(), new FlywheelStats(3, 120, 10, 0,
                Map.of("com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual", 449.5,
                        "com.simibubi.create.content.logistics.funnel.FunnelVisual", 201.8,
                        "com.simibubi.create.content.kinetics.press.PressVisual", 12.0,
                        "com.example.other.mixer.PressVisual", 3.0)), 0);
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("flywheel", "1.0.6");
        m.put("flywheelBackend", "flywheel:indirect");
        String s = TextReportWriter.write(withFw, Environment.of(m, List.of("FLYWHEEL")), "0.4.0");

        assertTrue(s.contains("\n  SingleAxisRotatingVisual "), s);
        assertTrue(s.contains("\n  FunnelVisual "), s);
        assertFalse(s.contains("com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual"),
                "the fully qualified name belongs in the JSON, not the text table: " + s);
        assertTrue(s.contains("\n  press.PressVisual "), s);
        assertTrue(s.contains("\n  mixer.PressVisual "), s);
    }
}
