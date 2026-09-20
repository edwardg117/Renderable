package io.github.edwardg117.renderable.report;

import io.github.edwardg117.renderable.core.Fmt;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Writes the text and JSON reports under {@code <gameDirectory>/<reportFolder>/} (spec §6). Pure Java: the
 * game directory and the configuration values are passed in, so spec §8's rule that
 * {@code EnvironmentCollector} is the only Minecraft-aware class in {@code report} holds, and the folder
 * validation below is unit-testable (spec addendum §P4.B6 and §P4.D2).
 */
public final class ReportFiles {

    /** Used when the configured folder is missing or could escape the game directory. */
    public static final String DEFAULT_FOLDER = "renderable";

    /** Writes the JSON report to a writer, so the report is never held in memory as a string. */
    @FunctionalInterface
    public interface JsonBody {
        void writeTo(Writer out) throws IOException;
    }

    /** Where the files went. {@code text} or {@code json} is null when that format is disabled. */
    public record Written(Path folder, Path text, Path json) {
    }

    /** Which report formats to write - named, so a call site cannot transpose them like two bare booleans. */
    public record Formats(boolean text, boolean json) {
    }

    /** Nothing, or not everything, was written. The caller shows the message to the user. */
    public static final class WriteFailed extends RuntimeException {
        /** What did get written before the failure; never null, though its fields may be. */
        public final Written partial;

        WriteFailed(String message, Written partial, Throwable cause) {
            super(message, cause);
            this.partial = partial;
        }
    }

    private ReportFiles() {
    }

    /**
     * Resolves the configured folder under the game directory. Anything that could write outside it — an
     * absolute path, a root or drive prefix, a {@code ..} segment, a blank name, or a string that is not a
     * path at all — falls back to {@link #DEFAULT_FOLDER}, with a logged warning (spec addendum §P4.B6 #3).
     */
    public static Path resolveFolder(Path gameDirectory, String configured) {
        Path fallback = gameDirectory.resolve(DEFAULT_FOLDER);
        if (configured == null || configured.isBlank()) return reject(configured, fallback, "it is missing or blank");
        Path relative;
        try {
            relative = Path.of(configured.trim());
        } catch (InvalidPathException e) {
            return reject(configured, fallback, "it is not a valid path");
        }
        if (relative.isAbsolute() || relative.getRoot() != null) return reject(configured, fallback, "it is absolute");
        for (Path part : relative) {
            if (part.toString().equals("..")) return reject(configured, fallback, "it contains a '..' segment");
        }
        return gameDirectory.resolve(relative).normalize();
    }

    /** Spec addendum §P4.B6 #3: a rejected folder must say so, or the setting is silently ignored. */
    private static Path reject(String configured, Path fallback, String why) {
        LoggerFactory.getLogger("Renderable").warn(
                "reportFolder \"{}\" was ignored because {}; writing to {} instead",
                configured, why, fallback);
        return fallback;
    }

    /**
     * Writes whichever formats are enabled and returns where they went.
     *
     * @throws WriteFailed if the folder cannot be created or a file cannot be written - including a bug in
     *                      {@code json} itself, such as an unbalanced {@code JsonWriter} document or a null
     *                      key, which throws unchecked rather than as an {@code IOException}
     */
    public static Written write(Path gameDirectory, String configuredFolder, Formats formats,
                                String text, JsonBody json, long wallStartMillis) {
        Path folder = resolveFolder(gameDirectory, configuredFolder);
        String stamp = Fmt.fileStamp(wallStartMillis);
        // Committed to the outer variable only once its own write has fully succeeded, so a WriteFailed
        // thrown mid-write never reports the file that caused it as having landed.
        Path textPath = null;
        Path jsonPath = null;
        Path attemptedJsonPath = null;
        try {
            Files.createDirectories(folder);
            if (formats.text()) {
                Path p = folder.resolve("renderable-" + stamp + ".txt");
                Files.writeString(p, text, StandardCharsets.UTF_8);
                textPath = p;
            }
            if (formats.json()) {
                attemptedJsonPath = folder.resolve("renderable-" + stamp + ".json");
                try (BufferedWriter out = Files.newBufferedWriter(attemptedJsonPath, StandardCharsets.UTF_8)) {
                    json.writeTo(out);
                }
                jsonPath = attemptedJsonPath;
            }
        } catch (IOException | RuntimeException e) {
            // Files.newBufferedWriter creates and truncates the file before json.writeTo runs, so a
            // mid-write failure leaves a file nothing can parse. Best-effort delete so Written.json == null
            // means what every caller already assumes: nothing usable landed there.
            if (attemptedJsonPath != null && jsonPath == null) {
                try {
                    Files.deleteIfExists(attemptedJsonPath);
                } catch (IOException ignored) {
                    // best-effort cleanup; the failure being reported below is what matters
                }
            }
            throw new WriteFailed("could not write reports to " + folder + ": " + e.getMessage(),
                    new Written(folder, textPath, jsonPath), e);
        }
        return new Written(folder, textPath, jsonPath);
    }
}
