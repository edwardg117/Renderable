package io.github.edwardg117.renderable.report;

import com.google.gson.stream.JsonWriter;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.FlywheelStats;
import io.github.edwardg117.renderable.core.FrameRecord;
import io.github.edwardg117.renderable.core.FrameStats;
import io.github.edwardg117.renderable.core.MeshEntry;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.RankedEntry;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SectionKey;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.TopEntry;
import io.github.edwardg117.renderable.core.Window;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Spec §6.2: complete, untruncated machine-readable report, streamed a row at a time so a long session is
 * never held in memory as a tree or as a string (spec addendum §P4.B1). Every array is complete; nothing is
 * capped.
 */
public final class JsonReportWriter {
    public static final int SCHEMA = 5;

    private JsonReportWriter() {
    }

    /**
     * Writes the report to {@code out}. The writer is flushed but not closed, so the caller owns the file.
     * HTML-safe escaping is on because {@code Gson.toJson} escapes that way by default and an environment
     * string such as a shader pack name can contain the characters it differs on.
     */
    public static void write(SessionResult r, Environment env, String modVersion, Writer out) throws IOException {
        body(configure(out), r, env, modVersion);
    }

    /**
     * Sets up a {@link JsonWriter} with the settings that keep it byte-identical to the old
     * {@code Gson.toJson} tree writer. Shared by both overloads below so neither can drift from the other.
     */
    private static JsonWriter configure(Writer out) {
        JsonWriter w = new JsonWriter(out);
        w.setIndent("  ");
        w.setHtmlSafe(true);
        // Matches Gson.toJson's default (serializeNulls is false unless GsonBuilder opts in): a null value
        // is omitted rather than written as "key": null. Nothing here is null today, but a future collector
        // field that can be null must keep the same shape, not just the same bytes.
        w.setSerializeNulls(false);
        return w;
    }

    /** Everything that makes up the report. Does not configure or close {@code w}; see the two overloads above. */
    private static void body(JsonWriter w, SessionResult r, Environment env, String modVersion) throws IOException {
        w.beginObject();
        w.name("schema").value(SCHEMA);

        w.name("mod").beginObject();
        w.name("version").value(modVersion);
        w.endObject();

        w.name("environment").beginObject();
        for (Map.Entry<String, String> e : env.entries().entrySet()) w.name(e.getKey()).value(e.getValue());
        w.name("hooksApplied").beginArray();
        for (String h : env.hooksApplied()) w.value(h);
        w.endArray();
        w.endObject();

        FrameStats st = r.stats();
        w.name("session").beginObject();
        w.name("startedAt").value(Instant.ofEpochMilli(r.wallStartMillis()).toString());
        w.name("durationSeconds").value(st.durationSeconds());
        w.name("frames").value(r.frameCount());
        w.name("hitchThresholdMs").value(r.hitchThresholdNanos() / 1e6);
        w.name("overheadEstimateMs").value(r.overheadEstimateMs());
        w.name("timedCalls").value(r.timedCalls());
        w.name("vertexCalls").value(r.vertexCalls());
        w.name("selfMs").value(r.selfNanos() / 1e6);
        w.name("unbalancedFrames").value(r.unbalancedFrames());
        w.name("config").beginObject();
        w.name("durationSeconds").value(r.config().durationSeconds());
        w.name("hitchMedianMultiplier").value(r.config().hitchMedianMultiplier());
        w.name("hitchMinExcessMs").value(r.config().hitchMinExcessNanos() / 1e6);
        w.name("topContributorsPerFrame").value(r.config().topContributorsPerFrame());
        w.endObject();
        w.endObject();

        w.name("stats").beginObject();
        w.name("avgMs").value(st.avgNanos() / 1e6);
        w.name("avgFps").value(st.avgFps());
        w.name("p50Ms").value(st.p50Nanos() / 1e6);
        w.name("p95Ms").value(st.p95Nanos() / 1e6);
        w.name("p99Ms").value(st.p99Nanos() / 1e6);
        w.name("maxMs").value(st.maxNanos() / 1e6);
        w.name("avgTickMs").value(st.avgTickNanos() / 1e6);
        w.name("avgRenderMs").value(st.avgRenderNanos() / 1e6);
        w.name("avgTasksMs").value(st.avgTasksNanos() / 1e6);
        w.name("avgCapMs").value(st.avgCapNanos() / 1e6);
        w.name("avgSwapMs").value(st.avgSwapNanos() / 1e6);
        w.name("avgOtherRenderMs").value(st.avgOtherRenderNanos() / 1e6);
        w.name("avgOtherTickMs").value(st.avgOtherTickNanos() / 1e6);
        w.name("avgNamedMs").beginObject();
        for (Window win : Window.VALUES) w.name(win.label()).value(st.avgNamed(win) / 1e6);
        w.endObject();
        w.name("gcCount").value(st.gcCount());
        w.name("gcMs").value(st.gcMillis());
        w.name("avgBucketsMs").beginObject();
        for (Bucket b : Bucket.VALUES) w.name(b.label()).value(st.avgBucket(b) / 1e6);
        w.endObject();
        w.endObject();

        w.name("frames").beginArray();
        for (FrameRecord f : r.frames()) {
            w.beginObject();
            w.name("i").value(f.index());
            w.name("startUs").value(f.startNanos() / 1000);
            w.name("totalUs").value(f.totalNanos() / 1000);
            w.name("tickUs").value(f.tickNanos() / 1000);
            w.name("renderUs").value(f.renderNanos() / 1000);
            w.name("tasksUs").value(f.tasksNanos() / 1000);
            w.name("capUs").value(f.capNanos() / 1000);
            w.name("swapUs").value(f.swapNanos() / 1000);
            w.name("otherRenderUs").value(f.otherRenderNanos() / 1000);
            w.name("namedUs").beginObject();
            for (Window win : Window.VALUES) {
                long v = f.named(win);
                if (v > 0) w.name(win.label()).value(v / 1000);
            }
            w.endObject();
            w.name("bucketsUs").beginObject();
            for (Bucket b : Bucket.VALUES) {
                long v = f.bucket(b);
                if (v > 0) w.name(b.label()).value(v / 1000);
            }
            w.endObject();
            w.name("gcCount").value(f.gcCount());
            w.name("gcMs").value(f.gcMillis());
            w.name("top").beginArray();
            for (TopEntry t : f.top()) {
                w.beginObject();
                w.name("key").value(t.key().serialize());
                w.name("us").value(t.nanos() / 1000);
                w.name("gc").value(t.gc());
                w.endObject();
            }
            w.endArray();
            w.endObject();
        }
        w.endArray();

        w.name("hitches").beginArray();
        for (FrameRecord h : r.hitches()) w.value(h.index());
        w.endArray();

        w.name("mods");
        mods(w, r, r.frameCount());

        w.name("blockEntities").beginObject();
        w.name("byType");
        ranked(w, Ranking.byType(r, ProfileKey.Category.BLOCK_ENTITY), r.frameCount());
        w.name("byPosition");
        ranked(w, Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY), r.frameCount());
        w.endObject();

        w.name("entities").beginObject();
        w.name("byType");
        ranked(w, Ranking.byType(r, ProfileKey.Category.ENTITY), r.frameCount());
        w.name("individuals");
        ranked(w, Ranking.byKey(r, ProfileKey.Category.ENTITY), r.frameCount());
        w.endObject();

        w.name("particles");
        ranked(w, Ranking.byKey(r, ProfileKey.Category.PARTICLE), r.frameCount());

        double seconds = st.durationSeconds();
        w.name("blocks");
        meshRows(w, Ranking.mesh(r, ProfileKey.Category.BLOCK, Ranking.MeshSort.TIME), seconds);
        w.name("sections");
        meshRows(w, Ranking.mesh(r, ProfileKey.Category.SECTION, Ranking.MeshSort.CALLS), seconds);

        long flywheelNanos = r.bucketTotalNanos(Bucket.FLYWHEEL);
        FlywheelStats fw = r.flywheel();
        w.name("flywheel").beginObject();
        w.name("bucketUs").value(flywheelNanos / 1000);
        w.name("sharePercent").value(st.totalNanos() == 0 ? 0.0 : 100.0 * flywheelNanos / st.totalNanos());
        w.name("samples").value(fw.samples());
        w.name("avgBlockEntities").value(fw.avgBlockEntities());
        w.name("avgEntities").value(fw.avgEntities());
        w.name("avgEffects").value(fw.avgEffects());
        w.name("visualCounts").beginObject();
        for (Map.Entry<String, Double> e : fw.byAverageDesc()) w.name(e.getKey()).value(e.getValue());
        w.endObject();
        w.endObject();

        w.endObject();
        w.flush();
    }

    /**
     * The whole report as a string, for tests and anything that needs it in memory. Closing the
     * {@link JsonWriter} here (a {@link StringWriter}'s own {@code close()} is a documented no-op, so this
     * never closes anything the caller owns) runs Gson's completeness check, which throws "Incomplete
     * document" for an unbalanced {@code beginObject}/{@code endObject} pair - exactly the shape of bug that
     * byte-identity with the old tree writer cannot catch once a field is added or moved.
     */
    public static String write(SessionResult r, Environment env, String modVersion) {
        StringWriter out = new StringWriter(1 << 16);
        try (JsonWriter w = configure(out)) {
            body(w, r, env, modVersion);
        } catch (IOException e) {
            throw new IllegalStateException("a StringWriter cannot fail, so this is an unbalanced document", e);
        }
        return out.toString();
    }

    private static void ranked(JsonWriter w, List<RankedEntry> entries, int frames) throws IOException {
        w.beginArray();
        for (RankedEntry e : entries) {
            w.beginObject();
            rankedFields(w, e, frames);
            w.endObject();
        }
        w.endArray();
    }

    /** One ranked row's fields, in the order {@code JsonReportWriterTest.RANKED_KEYS} pins. */
    private static void rankedFields(JsonWriter w, RankedEntry e, int frames) throws IOException {
        w.name("key").value(e.key() == null ? e.typeId() : e.key().serialize());
        w.name("label").value(e.label());
        w.name("typeId").value(e.typeId());
        w.name("members").value(e.members());
        w.name("calls").value(e.calls());
        w.name("tickCalls").value(e.tickCalls());
        w.name("callsPerFrame").value(e.callsPerFrame(frames));
        w.name("renderUs").value(e.renderNanos() / 1000);
        w.name("tickUs").value(e.tickNanos() / 1000);
        w.name("shadowUs").value(e.shadowNanos() / 1000);
        w.name("peakFrameUs").value(e.maxFrameNanos() / 1000);
        w.name("gcFrames").value(e.gcFrames());
        w.name("vertices").value(e.vertices());
    }

    /**
     * Spec addendum §P5.A7: every mod, most expensive first, each a ranked row followed by an {@code entries}
     * array of its members. Not {@code members}: the ranked row already has a {@code members} count.
     */
    private static void mods(JsonWriter w, SessionResult r, int frames) throws IOException {
        w.beginArray();
        for (RankedEntry m : Ranking.byType(r, ProfileKey.Category.MOD)) {
            w.beginObject();
            rankedFields(w, m, frames);
            w.name("entries");
            ranked(w, Ranking.membersOf(r, ProfileKey.Category.MOD, m.typeId()), frames);
            w.endObject();
        }
        w.endArray();
    }

    /** Spec §6.2 blocks and sections: mesh work is per session, not per frame, so rates are per second. */
    private static void meshRows(JsonWriter w, List<MeshEntry> rows, double seconds) throws IOException {
        w.beginArray();
        for (MeshEntry e : rows) {
            w.beginObject();
            w.name("key").value(e.key().serialize());
            w.name("label").value(e.key().label());
            if (e.key() instanceof SectionKey s) {
                w.name("dimension").value(s.dimension());
                w.name("x").value(s.x());
                w.name("y").value(s.y());
                w.name("z").value(s.z());
            }
            w.name("calls").value(e.calls());
            w.name("meshUs").value(e.nanos() / 1000);
            w.name("usPerCall").value(e.usPerCall());
            w.name("msPerSecond").value(e.msPerSecond(seconds));
            w.name("callsPerSecond").value(e.callsPerSecond(seconds));
            w.endObject();
        }
        w.endArray();
    }
}
