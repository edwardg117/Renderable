package io.github.edwardg117.renderable.report;

import com.google.gson.JsonParser;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.Fmt;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.TestSessions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
        // Spec addendum §P4.B6 #3: each fallback below also logs one warning naming why the configured
        // folder was rejected, via ReportFiles.reject(). Not asserted here - capturing log output is not
        // worth the machinery for this - but every branch that returns the fallback routes through it.
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
        ReportFiles.Written w = ReportFiles.write(game, "renderable", new ReportFiles.Formats(true, true),
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
    void disabledFormatsAreNotWritten(@TempDir Path game) throws IOException {
        ReportFiles.Written textOnly = ReportFiles.write(game, "renderable", new ReportFiles.Formats(true, false),
                "text", out -> fail("the JSON body must not be asked for when writeJson is false"), 1L);
        assertNotNull(textOnly.text());
        assertNull(textOnly.json());
        try (Stream<Path> files = Files.list(textOnly.folder())) {
            assertEquals(List.of("renderable-" + Fmt.fileStamp(1L) + ".txt"),
                    files.map(p -> p.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    void aFailureIsReportedNotSwallowed(@TempDir Path game) {
        ReportFiles.WriteFailed failed = assertThrows(ReportFiles.WriteFailed.class,
                () -> ReportFiles.write(game, "renderable", new ReportFiles.Formats(false, true), "",
                        out -> { throw new IOException("disk went away"); }, 2L));
        assertTrue(failed.getMessage().contains("disk went away"), failed.getMessage());
    }

    @Test
    void theRealJsonWriterStreamsThroughToAFileThatParses(@TempDir Path game) throws IOException {
        SessionResult r = TestSessions.phase3();
        Environment env = Environment.of(new LinkedHashMap<>(Map.of("minecraft", "1.21.1")), List.of());

        ReportFiles.Written w = ReportFiles.write(game, "renderable", new ReportFiles.Formats(false, true), "",
                out -> JsonReportWriter.write(r, env, "0.4.0", out), 1_726_700_000_000L);

        String onDisk = Files.readString(w.json(), StandardCharsets.UTF_8);
        assertEquals(JsonReportWriter.write(r, env, "0.4.0"), onDisk,
                "the only test that drives the real writer through ReportFiles into a real file: "
                        + "the streamed bytes must equal the in-memory ones");
        assertEquals(4, JsonParser.parseString(onDisk).getAsJsonObject().get("schema").getAsInt());
    }

    @Test
    void aFailedJsonWriteStillReportsTheTextFileThatSucceeded(@TempDir Path game) {
        ReportFiles.WriteFailed failed = assertThrows(ReportFiles.WriteFailed.class,
                () -> ReportFiles.write(game, "renderable", new ReportFiles.Formats(true, true), "the text report\n",
                        out -> { throw new IOException("disk went away"); }, 1_726_700_000_000L));

        assertNotNull(failed.partial, "partial is never null, even on total failure");
        assertNotNull(failed.partial.text(), "the text report landed before the JSON write failed");
        assertTrue(Files.exists(failed.partial.text()), failed.partial.text().toString());
        assertNull(failed.partial.json(), "the JSON write is what threw, so it must not be reported as landed");
        Path truncatedJson = failed.partial.folder().resolve(
                "renderable-" + Fmt.fileStamp(1_726_700_000_000L) + ".json");
        assertFalse(Files.exists(truncatedJson),
                "newBufferedWriter truncates the file before writeTo runs; the failure path must delete it");
    }
}
