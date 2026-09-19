package io.github.edwardg117.renderable.report;

import io.github.edwardg117.renderable.RenderableConfig;
import io.github.edwardg117.renderable.RenderableMod;
import io.github.edwardg117.renderable.core.Fmt;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes the text and JSON reports under {@code <gameDir>/<reportFolder>/} (spec §6). */
public final class ReportFiles {

    /** Where the files went. {@code text} or {@code json} is null when that format is disabled or failed. */
    public record Written(Path folder, Path text, Path json) {
    }

    private ReportFiles() {
    }

    public static Written write(String text, String json, long wallStartMillis) {
        Path folder = Minecraft.getInstance().gameDirectory.toPath().resolve(RenderableConfig.REPORT_FOLDER.get());
        String stamp = Fmt.fileStamp(wallStartMillis);
        Path textPath = null;
        Path jsonPath = null;
        try {
            Files.createDirectories(folder);
            if (RenderableConfig.WRITE_TEXT.get()) {
                textPath = folder.resolve("renderable-" + stamp + ".txt");
                Files.writeString(textPath, text, StandardCharsets.UTF_8);
            }
            if (RenderableConfig.WRITE_JSON.get()) {
                jsonPath = folder.resolve("renderable-" + stamp + ".json");
                Files.writeString(jsonPath, json, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            RenderableMod.LOGGER.error("Renderable could not write reports to {}", folder, e);
        }
        return new Written(folder, textPath, jsonPath);
    }
}
