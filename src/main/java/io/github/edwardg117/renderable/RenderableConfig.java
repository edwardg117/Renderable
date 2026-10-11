package io.github.edwardg117.renderable;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config, written to config/renderable-client.toml. */
public final class RenderableConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue DEFAULT_DURATION_SECONDS;
    public static final ModConfigSpec.BooleanValue OPEN_RESULTS_ON_FINISH;
    public static final ModConfigSpec.DoubleValue HITCH_MEDIAN_MULTIPLIER;
    public static final ModConfigSpec.IntValue HITCH_MIN_EXCESS_MS;
    public static final ModConfigSpec.IntValue TOP_CONTRIBUTORS_PER_FRAME;
    public static final ModConfigSpec.BooleanValue OVERLAY_ENABLED;
    public static final ModConfigSpec.IntValue OVERLAY_LABEL_COUNT;
    public static final ModConfigSpec.IntValue OVERLAY_MAX_DISTANCE;
    public static final ModConfigSpec.BooleanValue WRITE_TEXT;
    public static final ModConfigSpec.BooleanValue WRITE_JSON;
    public static final ModConfigSpec.ConfigValue<String> REPORT_FOLDER;
    public static final ModConfigSpec.BooleanValue DEBUG_COMMANDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("session");
        DEFAULT_DURATION_SECONDS = b.comment("Seconds sampled by the keybind and by /renderable start without an argument.")
                .defineInRange("defaultDurationSeconds", 30, 2, 600);
        OPEN_RESULTS_ON_FINISH = b.comment("Open the results screen when a session ends.")
                .define("openResultsOnFinish", true);
        HITCH_MEDIAN_MULTIPLIER = b.comment("A frame is a hitch when it is longer than median * this value ...")
                .defineInRange("hitchMedianMultiplier", 2.0, 1.1, 10.0);
        HITCH_MIN_EXCESS_MS = b.comment("... and also longer than median + this many milliseconds.")
                .defineInRange("hitchMinExcessMs", 16, 1, 1000);
        TOP_CONTRIBUTORS_PER_FRAME = b.comment("Keys remembered per frame so hitches can be explained.")
                .defineInRange("topContributorsPerFrame", 5, 0, 20);
        b.pop();

        b.push("overlay");
        OVERLAY_ENABLED = b.comment("Draw in-world labels for the last finished session.",
                        "/renderable overlay and the overlay key change this setting.")
                .define("overlayEnabled", true);
        OVERLAY_LABEL_COUNT = b.comment("Labels per category (block entities, entities, sections).")
                .defineInRange("overlayLabelCount", 25, 1, 200);
        OVERLAY_MAX_DISTANCE = b.comment("Only label things within this many blocks of the camera.")
                .defineInRange("overlayMaxDistance", 64, 8, 512);
        b.pop();

        b.push("report");
        WRITE_TEXT = b.define("writeText", true);
        WRITE_JSON = b.define("writeJson", true);
        REPORT_FOLDER = b.comment("Folder under the game directory that reports are written to.",
                        "Must be a relative path with no '..' segment; anything else falls back to 'renderable'.")
                .define("reportFolder", "renderable");
        b.pop();

        b.push("debug");
        DEBUG_COMMANDS = b.comment("Enables /renderable debug stall <ms>, which sleeps the render thread to test hitch attribution,",
                        "and /renderable debug events, which counts the event lists holding timing proxies.")
                .define("debugCommands", false);
        b.pop();

        SPEC = b.build();
    }

    private RenderableConfig() {
    }
}
