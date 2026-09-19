package io.github.edwardg117.renderable.report;

import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.TestSessions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

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
        assertTrue(s.contains("  Time split (avg ms/frame): tick 1.00 | block entity 7.40 | entity 0.80 | other render 2.10 | swap/wait 7.90\n"), s);
        assertTrue(s.contains("  GC: 1 collections, 5 ms paused\n"), s);
        assertTrue(s.contains("  Renderable overhead: about 0.00 ms over 120 timed calls\n"), s);
    }

    @Test
    void hitchSectionListsWorstFramesWithTopContributors() {
        String s = TextReportWriter.write(r, env, "0.1.0");
        int at = s.indexOf("Hitches (worst 25)\n");
        assertTrue(at > 0, s);
        String section = s.substring(at, s.indexOf("\n\n", at));
        assertTrue(section.contains("\n  #1  frame 10 @ 0.2s  80.0 ms  |  tick 1.0 | block entity 62.5 | entity 0.8 | entity tick 0.2 | other render 2.1 | swap 13.6  |  gc 0 ms\n"), section);
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
        assertTrue(s.contains("Blocks (mesh build) (top 20)\n  (no data: hook not active in this build)\n"), s);
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
}
