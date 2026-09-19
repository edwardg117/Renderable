package io.github.edwardg117.renderable;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Calibration;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.core.SessionConfig;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.hooks.Probe;
import io.github.edwardg117.renderable.hooks.compat.FlywheelCompat;
import io.github.edwardg117.renderable.report.EnvironmentCollector;
import io.github.edwardg117.renderable.report.JsonReportWriter;
import io.github.edwardg117.renderable.report.ReportFiles;
import io.github.edwardg117.renderable.report.TextReportWriter;
import io.github.edwardg117.renderable.ui.WorldOverlay;
import io.github.edwardg117.renderable.ui.screen.ResultsScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.nio.file.Path;
import java.util.Locale;

/** Owns the session lifecycle (spec §3.1) and delivery (spec §6). Everything runs on the render thread. */
public final class ProfilerController implements Probe.Listener {
    private static final ProfilerController INSTANCE = new ProfilerController();
    private static final long MIN_KEEP_NANOS = 2_000_000_000L;

    public static ProfilerController get() {
        return INSTANCE;
    }

    private Session session;
    private long calibratedCallNanos = -1;
    private int pendingStallMs;
    private long lastVisualSampleSecond = -1;

    private SessionResult lastResult;
    private Environment lastEnvironment;
    private String lastReportText;
    private Path lastFolder;

    private ProfilerController() {
    }

    public boolean isRunning() {
        return session != null;
    }

    /** The running session, or null. */
    public Session session() {
        return session;
    }

    /** The last finished session, or null. */
    public SessionResult lastResult() {
        return lastResult;
    }

    public Environment lastEnvironment() {
        return lastEnvironment;
    }

    /** Keybind behaviour: start with the default duration, or stop early if running. */
    public void toggle() {
        if (isRunning()) {
            stop();
        } else {
            start(RenderableConfig.DEFAULT_DURATION_SECONDS.get());
        }
    }

    public void start(int seconds) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            chat("Renderable: join a world first.");
            return;
        }
        if (isRunning()) {
            chat("Renderable: a session is already running. Use /renderable stop to end it early.");
            return;
        }
        if (calibratedCallNanos < 0) {
            calibratedCallNanos = Calibration.measureCallNanos(System::nanoTime);
        }
        SessionConfig config = SessionConfig.ofSeconds(seconds,
                RenderableConfig.HITCH_MEDIAN_MULTIPLIER.get(),
                RenderableConfig.HITCH_MIN_EXCESS_MS.get(),
                RenderableConfig.TOP_CONTRIBUTORS_PER_FRAME.get(),
                calibratedCallNanos);
        lastVisualSampleSecond = -1;
        session = new Session(config, System::nanoTime, System.currentTimeMillis());
        WorldOverlay.clear();
        Probe.setListener(this);
        Probe.attach(session, mc.level.dimension().location().toString());
        chat("Renderable: sampling for " + seconds + " s. Press the key again or run /renderable stop to end early.");
    }

    /** Ends early. Keeps the data when at least two seconds were sampled, otherwise discards it. */
    public void stop() {
        if (!isRunning()) {
            chat("Renderable: no session is running.");
            return;
        }
        if (session.elapsedNanos() < MIN_KEEP_NANOS) {
            discard("stopped before 2 s of data");
        } else {
            finish();
        }
    }

    public void cancel() {
        if (!isRunning()) {
            chat("Renderable: no session is running.");
            return;
        }
        discard("cancelled");
    }

    private void discard(String reason) {
        Probe.detach();
        session = null;
        chat("Renderable: session discarded (" + reason + ").");
    }

    /** Freezes the session, writes reports, announces in chat. */
    public void finish() {
        Session s = session;
        if (s == null) return;
        Probe.detach();
        session = null;

        SessionResult result = s.finish();
        Environment env = EnvironmentCollector.collect(Probe.hooksApplied());
        String text = TextReportWriter.write(result, env, RenderableMod.VERSION);
        String json = JsonReportWriter.write(result, env, RenderableMod.VERSION);
        ReportFiles.Written written = ReportFiles.write(text, json, result.wallStartMillis());

        lastResult = result;
        lastEnvironment = env;
        lastReportText = text;
        lastFolder = written.folder();
        WorldOverlay.setResult(result);

        chat(finishedMessage(result, written));

        if (RenderableConfig.OPEN_RESULTS_ON_FINISH.get()) {
            openResults();
        }
    }

    private static MutableComponent finishedMessage(SessionResult r, ReportFiles.Written w) {
        MutableComponent msg = Component.literal(String.format(Locale.ROOT,
                "Renderable: %d frames in %.1f s, avg %.1f ms, %d hitches. ",
                r.frameCount(), r.stats().durationSeconds(), r.stats().avgNanos() / 1e6, r.hitches().size()));
        msg.append(commandLink("[Open results]", "/renderable results")).append(" ");
        msg.append(commandLink("[Copy report]", "/renderable copy")).append(" ");
        msg.append(Component.literal("[Open folder]").withStyle(st -> st
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, w.folder().toAbsolutePath().toString()))));
        return msg;
    }

    private static Component commandLink(String text, String command) {
        return Component.literal(text).withStyle(st -> st
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    public void copyReport() {
        if (lastReportText == null) {
            chat("Renderable: no finished session yet.");
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(lastReportText);
        chat("Renderable: report copied to the clipboard (" + lastReportText.length() + " characters).");
    }

    public void openFolder() {
        if (lastFolder == null) {
            chat("Renderable: no finished session yet.");
            return;
        }
        Util.getPlatform().openPath(lastFolder);
    }

    public void openResults() {
        if (lastResult == null) {
            chat("Renderable: no finished session yet.");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> mc.setScreen(new ResultsScreen(lastResult, lastEnvironment)));
    }

    /** Debug: sleep the render thread inside a fake block entity on the next frame (spec §5.1). */
    public void debugStall(int ms) {
        if (!isRunning()) {
            chat("Renderable: start a session first, then stall.");
            return;
        }
        pendingStallMs = ms;
        chat("Renderable: will stall the render thread for " + ms + " ms on the next frame.");
    }

    @Override
    public void onFrameStarted(Session s) {
        long second = s.elapsedNanos() / 1_000_000_000L;
        if (second != lastVisualSampleSecond) {
            lastVisualSampleSecond = second;
            sampleFlywheel(s);
        }

        int ms = pendingStallMs;
        if (ms <= 0) return;
        pendingStallMs = 0;
        Accumulator a = s.accumulator(new BlockEntityKey("renderable:debug_stall", "renderable:debug", 0, 0, 0));
        s.begin(a, Bucket.BLOCK_ENTITY);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            s.end();
        }
    }

    /** Spec §4.2: Flywheel visual counts, sampled about once per second. Runs at frame start, outside Flywheel's own
     * frame work; the sample is inside the frame, so its cost shows up in that frame's other render. */
    private static void sampleFlywheel(Session s) {
        if (!FlywheelCompat.PRESENT) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        FlywheelCompat.Counts counts = FlywheelCompat.sample(mc.level);
        if (counts != null) {
            s.recordVisualCounts(counts.blockEntities(), counts.entities(), counts.effects(), counts.byClass());
        }
    }

    @Override
    public void onFinishRequested() {
        finish();
    }

    @Override
    public void onDiscardRequested(String reason) {
        if (isRunning()) discard(reason);
    }

    public static void chat(String text) {
        chat(Component.literal(text));
    }

    public static void chat(Component component) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(component, false);
        } else {
            RenderableMod.LOGGER.info(component.getString());
        }
    }
}
