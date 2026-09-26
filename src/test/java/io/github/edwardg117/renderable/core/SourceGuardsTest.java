package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
