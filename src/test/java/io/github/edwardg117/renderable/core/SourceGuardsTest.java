package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Source-level guards: things the compiler cannot check but a reader would want kept true. */
class SourceGuardsTest {
    static final Path MAIN = Path.of("src/main/java/io/github/edwardg117/renderable");

    @Test
    void hookNamesAreNeverSpeltAsStringLiterals() throws IOException {
        // A renamed constant must break the build, not leave a writer comparing against a stale string.
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (p.getFileName().toString().equals("Hook.java")) continue;
                String src = Files.readString(p);
                for (Hook h : Hook.values()) {
                    assertFalse(src.contains('"' + h.name() + '"'),
                            p + " spells " + h.name() + " as a string; use Hook." + h.name() + ".name()");
                }
            }
        }
    }

    @Test
    void theAbsentSentinelIsNamedOnce() throws IOException {
        // Environment.ABSENT is the only place the string may appear, so a change of wording is one edit.
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (p.getFileName().toString().equals("Environment.java")) continue;
                String src = Files.readString(p);
                assertFalse(src.contains("\"absent\""),
                        p + " spells the absent sentinel; use Environment.ABSENT");
            }
        }
    }

    @Test
    void noSourceReadsAPlayersName() throws IOException {
        // Spec addendum §P5.C3: a report never carries a player's own name. Each of these calls reads one;
        // KeyFactory's getGameProfile().getName() was how names used to reach the report.
        List<String> calls = List.of("getGameProfile(", "getScoreboardName(", "getDisplayName(", "getPlayerInfo(",
                "getTabListDisplayName(", "getUser(");
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p);
                for (String call : calls) {
                    assertFalse(src.contains(call), p + " calls " + call + "), which reads a player's name");
                }
            }
        }
    }

    @Test
    void everyAttributionOnlyEntryPointDeclinesOffTheRenderThread() throws IOException {
        // Review focus 3. A Session is render-thread only: an attribution-only call begun on another thread, such as a
        // sound a mod plays from its network thread, would corrupt the stack. So each entry point in Probe that
        // begins one must check the thread first, as beginPacket does (spec addendum §P6.5).
        String probe = Files.readString(MAIN.resolve("hooks/Probe.java"));
        int found = 0;
        for (int at = probe.indexOf(".beginAttribution("); at >= 0; at = probe.indexOf(".beginAttribution(", at + 1)) {
            int start = probe.lastIndexOf("\n    public static ", at);
            assertTrue(start >= 0, "a beginAttribution call outside a public entry point of Probe");
            String method = probe.substring(start + "\n    public static ".length(), probe.indexOf('(', start));
            assertTrue(probe.substring(start, at).contains("Thread.currentThread() != renderThread"),
                    method + " begins an attribution-only call without checking the render thread");
            found++;
        }
        assertTrue(found >= 4, "beginLayer, beginPacket, beginPayload and beginSound; found " + found);
    }
}
