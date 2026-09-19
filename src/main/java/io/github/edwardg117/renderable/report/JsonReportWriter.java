package io.github.edwardg117.renderable.report;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.FlywheelStats;
import io.github.edwardg117.renderable.core.FrameRecord;
import io.github.edwardg117.renderable.core.FrameStats;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.RankedEntry;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.TopEntry;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Spec §6.2: complete, untruncated machine-readable report. */
public final class JsonReportWriter {
    public static final int SCHEMA = 2;

    private JsonReportWriter() {
    }

    public static String write(SessionResult r, Environment env, String modVersion) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);

        JsonObject mod = new JsonObject();
        mod.addProperty("version", modVersion);
        root.add("mod", mod);

        JsonObject environment = new JsonObject();
        env.entries().forEach(environment::addProperty);
        JsonArray hooks = new JsonArray();
        env.hooksApplied().forEach(hooks::add);
        environment.add("hooksApplied", hooks);
        root.add("environment", environment);

        FrameStats st = r.stats();
        JsonObject session = new JsonObject();
        session.addProperty("startedAt", Instant.ofEpochMilli(r.wallStartMillis()).toString());
        session.addProperty("durationSeconds", st.durationSeconds());
        session.addProperty("frames", r.frameCount());
        session.addProperty("hitchThresholdMs", r.hitchThresholdNanos() / 1e6);
        session.addProperty("overheadEstimateMs", r.overheadEstimateMs());
        session.addProperty("timedCalls", r.timedCalls());
        session.addProperty("unbalancedFrames", r.unbalancedFrames());
        JsonObject config = new JsonObject();
        config.addProperty("durationSeconds", r.config().durationSeconds());
        config.addProperty("hitchMedianMultiplier", r.config().hitchMedianMultiplier());
        config.addProperty("hitchMinExcessMs", r.config().hitchMinExcessNanos() / 1e6);
        config.addProperty("topContributorsPerFrame", r.config().topContributorsPerFrame());
        session.add("config", config);
        root.add("session", session);

        JsonObject stats = new JsonObject();
        stats.addProperty("avgMs", st.avgNanos() / 1e6);
        stats.addProperty("avgFps", st.avgFps());
        stats.addProperty("p50Ms", st.p50Nanos() / 1e6);
        stats.addProperty("p95Ms", st.p95Nanos() / 1e6);
        stats.addProperty("p99Ms", st.p99Nanos() / 1e6);
        stats.addProperty("maxMs", st.maxNanos() / 1e6);
        stats.addProperty("avgTickMs", st.avgTickNanos() / 1e6);
        stats.addProperty("avgRenderMs", st.avgRenderNanos() / 1e6);
        stats.addProperty("avgSwapMs", st.avgSwapNanos() / 1e6);
        stats.addProperty("avgOtherRenderMs", st.avgOtherRenderNanos() / 1e6);
        stats.addProperty("avgOtherTickMs", st.avgOtherTickNanos() / 1e6);
        stats.addProperty("gcCount", st.gcCount());
        stats.addProperty("gcMs", st.gcMillis());
        JsonObject avgBuckets = new JsonObject();
        for (Bucket b : Bucket.VALUES) avgBuckets.addProperty(b.label(), st.avgBucket(b) / 1e6);
        stats.add("avgBucketsMs", avgBuckets);
        root.add("stats", stats);

        JsonArray frames = new JsonArray();
        for (FrameRecord f : r.frames()) {
            JsonObject o = new JsonObject();
            o.addProperty("i", f.index());
            o.addProperty("startUs", f.startNanos() / 1000);
            o.addProperty("totalUs", f.totalNanos() / 1000);
            o.addProperty("tickUs", f.tickNanos() / 1000);
            o.addProperty("renderUs", f.renderNanos() / 1000);
            o.addProperty("swapUs", f.swapNanos() / 1000);
            o.addProperty("otherRenderUs", f.otherRenderNanos() / 1000);
            JsonObject buckets = new JsonObject();
            for (Bucket b : Bucket.VALUES) {
                long v = f.bucket(b);
                if (v > 0) buckets.addProperty(b.label(), v / 1000);
            }
            o.add("bucketsUs", buckets);
            o.addProperty("gcCount", f.gcCount());
            o.addProperty("gcMs", f.gcMillis());
            JsonArray top = new JsonArray();
            for (TopEntry t : f.top()) {
                JsonObject te = new JsonObject();
                te.addProperty("key", t.key().serialize());
                te.addProperty("us", t.nanos() / 1000);
                top.add(te);
            }
            o.add("top", top);
            frames.add(o);
        }
        root.add("frames", frames);

        JsonArray hitches = new JsonArray();
        for (FrameRecord h : r.hitches()) hitches.add(h.index());
        root.add("hitches", hitches);

        root.add("blockEntities", category(r, ProfileKey.Category.BLOCK_ENTITY, "byType", "byPosition"));
        root.add("entities", category(r, ProfileKey.Category.ENTITY, "byType", "individuals"));
        root.add("particles", ranked(Ranking.byKey(r, ProfileKey.Category.PARTICLE), r.frameCount()));
        root.add("blocks", ranked(Ranking.byKey(r, ProfileKey.Category.BLOCK), r.frameCount()));
        root.add("sections", ranked(Ranking.byKey(r, ProfileKey.Category.SECTION), r.frameCount()));
        JsonObject flywheel = new JsonObject();
        long flywheelNanos = r.bucketTotalNanos(Bucket.FLYWHEEL);
        flywheel.addProperty("bucketUs", flywheelNanos / 1000);
        flywheel.addProperty("sharePercent", st.totalNanos() == 0 ? 0.0 : 100.0 * flywheelNanos / st.totalNanos());
        FlywheelStats fw = r.flywheel();
        flywheel.addProperty("samples", fw.samples());
        flywheel.addProperty("avgBlockEntities", fw.avgBlockEntities());
        flywheel.addProperty("avgEntities", fw.avgEntities());
        flywheel.addProperty("avgEffects", fw.avgEffects());
        JsonObject visualCounts = new JsonObject();
        for (Map.Entry<String, Double> e : fw.byAverageDesc()) visualCounts.addProperty(e.getKey(), e.getValue());
        flywheel.add("visualCounts", visualCounts);
        root.add("flywheel", flywheel);

        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    private static JsonObject category(SessionResult r, ProfileKey.Category c, String typeName, String keyName) {
        JsonObject o = new JsonObject();
        o.add(typeName, ranked(Ranking.byType(r, c), r.frameCount()));
        o.add(keyName, ranked(Ranking.byKey(r, c), r.frameCount()));
        return o;
    }

    private static JsonArray ranked(List<RankedEntry> entries, int frames) {
        JsonArray arr = new JsonArray();
        for (RankedEntry e : entries) {
            JsonObject o = new JsonObject();
            o.addProperty("key", e.key() == null ? e.typeId() : e.key().serialize());
            o.addProperty("label", e.label());
            o.addProperty("typeId", e.typeId());
            o.addProperty("members", e.members());
            o.addProperty("calls", e.calls());
            o.addProperty("tickCalls", e.tickCalls());
            o.addProperty("callsPerFrame", e.callsPerFrame(frames));
            o.addProperty("renderUs", e.renderNanos() / 1000);
            o.addProperty("tickUs", e.tickNanos() / 1000);
            o.addProperty("shadowUs", e.shadowNanos() / 1000);
            o.addProperty("peakFrameUs", e.maxFrameNanos() / 1000);
            o.addProperty("vertices", e.vertices());
            arr.add(o);
        }
        return arr;
    }
}
