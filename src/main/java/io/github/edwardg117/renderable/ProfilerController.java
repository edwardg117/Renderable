package io.github.edwardg117.renderable;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Calibration;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.core.SessionConfig;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.hooks.GcWatcher;
import io.github.edwardg117.renderable.hooks.Probe;
import io.github.edwardg117.renderable.hooks.compat.FlywheelCompat;
import io.github.edwardg117.renderable.hooks.events.EventTiming;
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
    /** Dev aid: the length of one session started by itself after joining a world, or 0 for none. */
    private static final int AUTOSTART_SECONDS = Integer.getInteger("renderable.autostart", 0);
    /** Ten seconds of ticks, so the world has settled before an autostarted session begins. */
    private static final int AUTOSTART_DELAY_TICKS = 200;

    public static ProfilerController get() {
        return INSTANCE;
    }

    private Session session;
    private long calibratedCallNanos = -1;
    private long calibratedVertexNanos = -1;
    private int pendingStallMs;
    private long lastVisualSampleSecond = -1;
    /** The dimension the running session started in, so the overlay can scope its labels to it. */
    private String sessionDimension = "";

    private SessionResult lastResult;
    private boolean resultWorldGone;
    private Environment lastEnvironment;
    private String lastReportText;
    private Path lastFolder;
    private boolean autostarted;
    private int ticksInWorld;

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

    /**
     * True when the last result was taken in a world the client has since left, so its positions and entity
     * ids no longer mean anything (spec addendum §P4.B6). The report itself is still worth copying.
     */
    public boolean resultWorldGone() {
        return resultWorldGone;
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

    /**
     * Dev aid: with {@code -Drenderable.autostart=<seconds>}, runs one session of that length ten seconds after a
     * world is joined, so a headless dev client produces a report to check. Called every client tick; does
     * nothing when the property is absent.
     */
    public void tickAutostart() {
        if (AUTOSTART_SECONDS <= 0 || autostarted) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            ticksInWorld = 0;
            return;
        }
        if (++ticksInWorld < AUTOSTART_DELAY_TICKS) return;
        autostarted = true;
        start(Math.max(2, Math.min(600, AUTOSTART_SECONDS)));
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
            calibratedVertexNanos = Calibration.measureVertexNanos(System::nanoTime);
        }
        SessionConfig config = SessionConfig.ofSeconds(seconds,
                RenderableConfig.HITCH_MEDIAN_MULTIPLIER.get(),
                RenderableConfig.HITCH_MIN_EXCESS_MS.get(),
                RenderableConfig.TOP_CONTRIBUTORS_PER_FRAME.get(),
                calibratedCallNanos,
                calibratedVertexNanos);
        lastVisualSampleSecond = -1;
        session = new Session(config, System::nanoTime, System.currentTimeMillis());
        sessionDimension = mc.level.dimension().location().toString();
        WorldOverlay.clear();
        Probe.setListener(this);
        Probe.attach(session, sessionDimension);
        EventTiming.start(session);
        GcWatcher.start();
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
        EventTiming.stop();
        GcWatcher.stop();
        Probe.detach();
        session = null;
        pendingStallMs = 0;
        chat("Renderable: session discarded (" + reason + ").");
    }

    /** Freezes the session, writes reports, announces in chat. */
    public void finish() {
        Session s = session;
        if (s == null) return;
        EventTiming.stop();
        GcWatcher.stop();
        Probe.detach();
        session = null;

        SessionResult result = s.finish();
        Environment env = EnvironmentCollector.collect(Probe.hooksApplied());
        String text = TextReportWriter.write(result, env, RenderableMod.VERSION);
        ReportFiles.Written written = null;
        String writeFailure = null;
        try {
            written = ReportFiles.write(
                    Minecraft.getInstance().gameDirectory.toPath(),
                    RenderableConfig.REPORT_FOLDER.get(),
                    new ReportFiles.Formats(RenderableConfig.WRITE_TEXT.get(), RenderableConfig.WRITE_JSON.get()),
                    text,
                    out -> JsonReportWriter.write(result, env, RenderableMod.VERSION, out),
                    result.wallStartMillis());
        } catch (ReportFiles.WriteFailed e) {
            RenderableMod.LOGGER.warn("Renderable could not write reports", e);
            writeFailure = e.getMessage();
            // Whatever landed before the failure - e.g. the text report next to a JSON write that failed -
            // still gets a working [Open folder] link below.
            written = e.partial;
        } catch (RuntimeException e) {
            // Anything other than a wrapped IOException (a JsonWriter nesting bug, a null map key, a config
            // read before load, ...) must not escape finish(): the session was sampled successfully and the
            // user must still get the results screen and [Copy report], just without report files.
            RenderableMod.LOGGER.warn("Renderable could not write reports", e);
            writeFailure = e.getMessage();
        }

        lastResult = result;
        resultWorldGone = false;
        lastEnvironment = env;
        lastReportText = text;
        // Only trust the folder if something actually reached disk - e.partial.folder() above may name a
        // directory that was never created.
        if (written != null && (written.text() != null || written.json() != null)) {
            lastFolder = written.folder();
        }
        WorldOverlay.setResult(result, sessionDimension);

        chat(finishedMessage(result, written, writeFailure));

        if (RenderableConfig.OPEN_RESULTS_ON_FINISH.get()) {
            openResults();
        }
    }

    private static MutableComponent finishedMessage(SessionResult r, ReportFiles.Written w, String writeFailure) {
        MutableComponent msg = Component.literal(String.format(Locale.ROOT,
                "Renderable: %d frames in %.1f s, avg %.1f ms, %d hitches. ",
                r.frameCount(), r.stats().durationSeconds(), r.stats().avgNanos() / 1e6, r.hitches().size()));
        msg.append(commandLink("[Open results]", "/renderable results")).append(" ");
        msg.append(commandLink("[Copy report]", "/renderable copy"));
        // A partial write (e.g. the text report landed but the JSON write failed) still offers the folder
        // link; only a Written with nothing in it at all falls through to the failure text alone.
        boolean anythingLanded = w != null && (w.text() != null || w.json() != null);
        if (anythingLanded) {
            msg.append(" ").append(Component.literal("[Open folder]").withStyle(st -> st
                    .withColor(ChatFormatting.AQUA).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, w.folder().toAbsolutePath().toString()))));
        }
        if (writeFailure != null) {
            msg.append(Component.literal(anythingLanded
                    ? " Some report files could not be written: " + writeFailure
                    : " Report files could not be written: " + writeFailure));
        }
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
            s.beginSelf();
            try {
                sampleFlywheel(s);
                EventTiming.recheck();
            } finally {
                s.endSelf();
            }
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

    @Override
    public void onWorldGone() {
        resultWorldGone = true;
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
