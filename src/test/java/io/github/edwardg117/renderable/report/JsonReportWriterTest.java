package io.github.edwardg117.renderable.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.FlywheelStats;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.TestSessions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonReportWriterTest {
    private static final List<String> RANKED_KEYS = List.of("key", "label", "typeId", "members", "calls",
            "tickCalls", "callsPerFrame", "renderUs", "tickUs", "shadowUs", "peakFrameUs", "gcFrames", "vertices");

    /** Pins the session object's field order, the same way RANKED_KEYS pins a ranked row's (spec §6.2). */
    private static final List<String> SESSION_KEYS = List.of("startedAt", "durationSeconds", "frames",
            "hitchThresholdMs", "overheadEstimateMs", "timedCalls", "vertexCalls", "selfMs", "unbalancedFrames",
            "config");

    /** Pins a frame's field order, and the stats object's, the same way SESSION_KEYS pins the session's. */
    private static final List<String> FRAME_KEYS = List.of("i", "startUs", "totalUs", "tickUs", "renderUs", "tasksUs",
            "capUs", "swapUs", "otherRenderUs", "namedUs", "bucketsUs", "gcCount", "gcMs", "top");
    private static final List<String> STATS_KEYS = List.of("avgMs", "avgFps", "p50Ms", "p95Ms", "p99Ms", "maxMs",
            "avgTickMs", "avgRenderMs", "avgTasksMs", "avgCapMs", "avgSwapMs", "avgOtherRenderMs", "avgOtherTickMs",
            "avgNamedMs", "gcCount", "gcMs", "avgBucketsMs");

    final SessionResult r = TestSessions.sample();

    static Environment environment() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("minecraft", "1.21.1");
        return Environment.of(m, List.of("BLOCK_ENTITY_RENDER", "ENTITY_RENDER"));
    }

    JsonObject parse() {
        return JsonParser.parseString(JsonReportWriter.write(r, environment(), "0.1.0")).getAsJsonObject();
    }

    @Test
    void schemaModEnvironmentAndSession() {
        JsonObject o = parse();
        assertEquals(5, o.get("schema").getAsInt());
        assertEquals("0.1.0", o.getAsJsonObject("mod").get("version").getAsString());
        assertEquals("1.21.1", o.getAsJsonObject("environment").get("minecraft").getAsString());
        assertEquals(2, o.getAsJsonObject("environment").getAsJsonArray("hooksApplied").size());

        JsonObject session = o.getAsJsonObject("session");
        assertEquals(0, session.get("vertexCalls").getAsLong());
        assertEquals(20, session.get("frames").getAsInt());
        assertEquals(32.0, session.get("hitchThresholdMs").getAsDouble(), 1e-9);
        assertEquals(0.384, session.get("durationSeconds").getAsDouble(), 1e-9);
        assertEquals(120, session.get("timedCalls").getAsLong());
        assertEquals(5, session.getAsJsonObject("config").get("topContributorsPerFrame").getAsInt());
        assertEquals(0, session.get("unbalancedFrames").getAsInt());
        assertTrue(session.get("startedAt").getAsString().startsWith("2024-09-18T") || session.get("startedAt").getAsString().startsWith("2024-09-19T"));

        JsonObject stats = o.getAsJsonObject("stats");
        assertEquals(16.0, stats.get("p50Ms").getAsDouble(), 1e-9);
        assertEquals(80.0, stats.get("maxMs").getAsDouble(), 1e-9);
        assertEquals(7.4, stats.getAsJsonObject("avgBucketsMs").get("block entity").getAsDouble(), 1e-9);
    }

    @Test
    void framesAndHitches() {
        JsonObject o = parse();
        JsonArray frames = o.getAsJsonArray("frames");
        assertEquals(20, frames.size());
        JsonObject f10 = frames.get(10).getAsJsonObject();
        assertEquals(10, f10.get("i").getAsInt());
        assertEquals(160_000, f10.get("startUs").getAsLong());
        assertEquals(80_000, f10.get("totalUs").getAsLong());
        assertEquals(1_000, f10.get("tickUs").getAsLong());
        assertEquals(65_400, f10.get("renderUs").getAsLong());
        assertEquals(13_600, f10.get("swapUs").getAsLong());
        assertEquals(62_500, f10.getAsJsonObject("bucketsUs").get("block entity").getAsLong());
        assertFalse(f10.getAsJsonObject("bucketsUs").has("flywheel"), "empty buckets are omitted");
        JsonObject top0 = f10.getAsJsonArray("top").get(0).getAsJsonObject();
        assertEquals("create:mechanical_press@minecraft:overworld:100,64,-20", top0.get("key").getAsString());
        assertEquals(60_000, top0.get("us").getAsLong());

        JsonObject f15 = frames.get(15).getAsJsonObject();
        assertEquals(1, f15.get("gcCount").getAsInt());
        assertEquals(5, f15.get("gcMs").getAsLong());

        JsonArray hitches = o.getAsJsonArray("hitches");
        assertEquals(1, hitches.size());
        assertEquals(10, hitches.get(0).getAsInt());
    }

    @Test
    void rankedSectionsAreComplete() {
        JsonObject o = parse();
        JsonObject be = o.getAsJsonObject("blockEntities");
        JsonObject pressType = be.getAsJsonArray("byType").get(0).getAsJsonObject();
        assertEquals("create:mechanical_press", pressType.get("key").getAsString());
        assertEquals(2, pressType.get("members").getAsInt());
        assertEquals(128_000, pressType.get("renderUs").getAsLong());
        assertEquals(3, be.getAsJsonArray("byPosition").size(), "JSON is not truncated");
        JsonObject press = be.getAsJsonArray("byPosition").get(0).getAsJsonObject();
        assertEquals("create:mechanical_press@minecraft:overworld:100,64,-20", press.get("key").getAsString());
        assertEquals(98_000, press.get("renderUs").getAsLong());
        assertEquals(60_000, press.get("peakFrameUs").getAsLong());
        assertEquals(20, press.get("calls").getAsLong());

        JsonObject cow = o.getAsJsonObject("entities").getAsJsonArray("individuals").get(0).getAsJsonObject();
        assertEquals("minecraft:cow#7", cow.get("key").getAsString());
        assertEquals(4_000, cow.get("tickUs").getAsLong());

        assertEquals(0, o.getAsJsonArray("particles").size());
        assertEquals(0, o.getAsJsonArray("blocks").size());
        assertEquals(0, o.getAsJsonArray("sections").size());
        JsonObject flywheel = o.getAsJsonObject("flywheel");
        assertEquals(0, flywheel.get("bucketUs").getAsLong());
        assertEquals(0, flywheel.get("samples").getAsInt());
        assertTrue(flywheel.getAsJsonObject("visualCounts").entrySet().isEmpty());
        assertEquals(1.0, press.get("callsPerFrame").getAsDouble(), 1e-9);
    }

    @Test
    void phase2Sections() {
        JsonObject o = JsonParser.parseString(JsonReportWriter.write(TestSessions.phase2(), environment(), "0.2.0")).getAsJsonObject();

        JsonObject f10 = o.getAsJsonArray("frames").get(10).getAsJsonObject().getAsJsonObject("bucketsUs");
        assertEquals(300, f10.get("light").getAsLong());
        assertEquals(1_200, f10.get("flywheel").getAsLong());
        assertEquals(400, f10.get("be tick").getAsLong());
        assertEquals(100, f10.get("particle tick").getAsLong());

        JsonObject flame = o.getAsJsonArray("particles").get(0).getAsJsonObject();
        assertEquals("particle:net.minecraft.client.particle.FlameParticle", flame.get("key").getAsString());
        assertEquals("FlameParticle", flame.get("label").getAsString());
        assertEquals(6_000, flame.get("renderUs").getAsLong());
        assertEquals(2_000, flame.get("tickUs").getAsLong());
        assertEquals(60, flame.get("calls").getAsLong());
        assertEquals(40, flame.get("tickCalls").getAsLong());
        assertEquals(3.0, flame.get("callsPerFrame").getAsDouble(), 1e-9);

        JsonObject flywheel = o.getAsJsonObject("flywheel");
        assertEquals(24_000, flywheel.get("bucketUs").getAsLong());
        assertEquals(6.25, flywheel.get("sharePercent").getAsDouble(), 1e-9);
        assertEquals(3, flywheel.get("samples").getAsInt());
        assertEquals(120.0, flywheel.get("avgBlockEntities").getAsDouble(), 1e-9);
        assertEquals(10.0, flywheel.get("avgEntities").getAsDouble(), 1e-9);
        assertEquals(0.0, flywheel.get("avgEffects").getAsDouble(), 1e-9);
        assertEquals(120.0, flywheel.getAsJsonObject("visualCounts").get(TestSessions.PRESS_VISUAL).getAsDouble(), 1e-9);
    }

    @Test
    void visualCountsAreSortedByAverage() {
        SessionResult withFw = TextReportWriterTest.withFlywheel(TestSessions.sample(),
                new FlywheelStats(3, 120, 10, 0, Map.of("b", 1.0, "a", 2.0, "c", 2.0)), 0);
        JsonObject o = JsonParser.parseString(JsonReportWriter.write(withFw, environment(), "0.2.0")).getAsJsonObject();
        List<String> keys = new ArrayList<>(o.getAsJsonObject("flywheel").getAsJsonObject("visualCounts").keySet());
        assertEquals(List.of("a", "c", "b"), keys);
    }

    @Test
    void phase3MeshSections() {
        JsonObject o = JsonParser.parseString(JsonReportWriter.write(TestSessions.phase3(), environment(), "0.3.0")).getAsJsonObject();

        JsonArray blocks = o.getAsJsonArray("blocks");
        assertEquals(3, blocks.size());
        JsonObject stone = blocks.get(0).getAsJsonObject();
        assertEquals("block:minecraft:stone", stone.get("key").getAsString());
        assertEquals("minecraft:stone", stone.get("label").getAsString());
        assertEquals(400, stone.get("calls").getAsLong());
        assertEquals(8_000, stone.get("meshUs").getAsLong());
        assertEquals(20.0, stone.get("usPerCall").getAsDouble(), 1e-9);
        assertEquals(8 / 0.384, stone.get("msPerSecond").getAsDouble(), 1e-9);
        assertFalse(stone.has("x"));

        JsonArray sections = o.getAsJsonArray("sections");
        assertEquals(2, sections.size());
        JsonObject a = sections.get(0).getAsJsonObject();
        assertEquals("section@minecraft:overworld:0,4,0", a.get("key").getAsString());
        assertEquals("minecraft:overworld", a.get("dimension").getAsString());
        assertEquals(0, a.get("x").getAsInt());
        assertEquals(4, a.get("y").getAsInt());
        assertEquals(0, a.get("z").getAsInt());
        assertEquals(3, a.get("calls").getAsLong());
        assertEquals(9_000, a.get("meshUs").getAsLong());
        assertEquals(3 / 0.384, a.get("callsPerSecond").getAsDouble(), 1e-9);

        JsonObject press = o.getAsJsonObject("blockEntities").getAsJsonArray("byPosition").get(0).getAsJsonObject();
        assertEquals(2000, press.get("vertices").getAsLong());
    }

    @Test
    void rankedRowsHaveTheirShapeInOrder() {
        JsonObject o = JsonParser.parseString(
                JsonReportWriter.write(TestSessions.phase3(), environment(), "0.4.0")).getAsJsonObject();

        List<JsonArray> rankedArrays = List.of(
                o.getAsJsonObject("blockEntities").getAsJsonArray("byType"),
                o.getAsJsonObject("blockEntities").getAsJsonArray("byPosition"),
                o.getAsJsonObject("entities").getAsJsonArray("byType"),
                o.getAsJsonObject("entities").getAsJsonArray("individuals"),
                o.getAsJsonArray("particles"));

        for (JsonArray arr : rankedArrays) {
            assertFalse(arr.isEmpty(), "phase3() should populate every ranked array");
            JsonObject row = arr.get(0).getAsJsonObject();
            assertEquals(RANKED_KEYS, List.copyOf(row.keySet()),
                    "ranked row shape changed - bump SCHEMA if that was intended");
        }
    }

    @Test
    void rankedRowsCarryGcFrames() {
        JsonObject o = JsonParser.parseString(
                JsonReportWriter.write(TestSessions.gcSession(), environment(), "0.4.0")).getAsJsonObject();

        JsonObject press = o.getAsJsonObject("blockEntities").getAsJsonArray("byPosition").get(0).getAsJsonObject();
        assertEquals("create:mechanical_press@minecraft:overworld:100,64,-20", press.get("key").getAsString());
        assertEquals(1, press.get("gcFrames").getAsLong());
        assertEquals(2_000, press.get("peakFrameUs").getAsLong(), "the clean 2 ms frame, not the 40 ms GC frame");

        JsonObject cow = o.getAsJsonObject("entities").getAsJsonArray("individuals").get(0).getAsJsonObject();
        assertEquals(0, cow.get("gcFrames").getAsLong());
    }

    @Test
    void topEntriesCarryTheGcFlag() {
        JsonObject o = JsonParser.parseString(
                JsonReportWriter.write(TestSessions.gcSession(), environment(), "0.4.0")).getAsJsonObject();

        JsonArray top0 = o.getAsJsonArray("frames").get(0).getAsJsonObject().getAsJsonArray("top");
        assertFalse(top0.get(0).getAsJsonObject().get("gc").getAsBoolean(), "frame 0 had no pause");
        JsonArray top1 = o.getAsJsonArray("frames").get(1).getAsJsonObject().getAsJsonArray("top");
        assertTrue(top1.get(0).getAsJsonObject().get("gc").getAsBoolean(), "40 ms on a frame with a 40 ms pause");
    }

    @Test
    void peakFrameUsIsZeroWhenEveryFrameWasGcSuspect() {
        JsonObject o = JsonParser.parseString(JsonReportWriter.write(
                TestSessions.everyFrameGcSuspect(), environment(), "0.4.0")).getAsJsonObject();
        JsonObject row = o.getAsJsonObject("blockEntities").getAsJsonArray("byPosition").get(0).getAsJsonObject();
        assertEquals(0, row.get("peakFrameUs").getAsLong());
        assertEquals(1, row.get("gcFrames").getAsLong(), "peakFrameUs 0 means \"no clean frame\" only because gcFrames > 0");
    }

    @Test
    void vertexCallsAreReportedForASessionThatCountedThem() {
        JsonObject o = JsonParser.parseString(
                JsonReportWriter.write(TestSessions.phase3(), environment(), "0.4.0")).getAsJsonObject();
        // phase3 calls vertex() once per vertex: 100 on the press and 20 on the mixer, each frame, over 20
        // frames. The batched vertices(int) path is covered by SessionTest, not here.
        assertEquals(2400, o.getAsJsonObject("session").get("vertexCalls").getAsLong());
        assertEquals(JsonReportWriter.SCHEMA, o.get("schema").getAsInt());
    }

    @Test
    void selfMsIsReported() {
        JsonObject o = parse();
        assertEquals(0.0, o.getAsJsonObject("session").get("selfMs").getAsDouble(), 1e-9);
    }

    @Test
    void sessionObjectHasItsShapeInOrder() {
        JsonObject session = parse().getAsJsonObject("session");
        assertEquals(SESSION_KEYS, List.copyOf(session.keySet()),
                "session object shape changed - bump SCHEMA if that was intended");
    }

    @Test
    void theWriterOverloadProducesExactlyTheStringOverloadsBytes() throws Exception {
        SessionResult r3 = TestSessions.phase3();
        java.io.StringWriter out = new java.io.StringWriter();
        JsonReportWriter.write(r3, environment(), "0.4.0", out);
        assertEquals(JsonReportWriter.write(r3, environment(), "0.4.0"), out.toString());
        assertEquals(5, JsonParser.parseString(out.toString()).getAsJsonObject().get("schema").getAsInt());
        assertTrue(out.toString().startsWith("{\n  \"schema\": 5,"), out.toString().substring(0, 40));
    }

    @Test
    void phase5FramesAndStatsCarryTheNewComponents() {
        JsonObject o = JsonParser.parseString(JsonReportWriter.write(TestSessions.phase5(), environment(), "0.5.0"))
                .getAsJsonObject();
        JsonObject f0 = o.getAsJsonArray("frames").get(0).getAsJsonObject();
        assertEquals(5_000, f0.get("renderUs").getAsLong());
        assertEquals(2_000, f0.get("tasksUs").getAsLong());
        assertEquals(3_000, f0.get("capUs").getAsLong());
        assertEquals(8_000, f0.get("swapUs").getAsLong());
        JsonObject named = f0.getAsJsonObject("namedUs");
        assertEquals(1_000, named.get("render").getAsLong());
        assertEquals(400, named.get("tick").getAsLong());
        assertEquals(1_000, named.get("tasks").getAsLong());
        assertEquals(2_000, f0.getAsJsonObject("bucketsUs").get("hud").getAsLong());

        JsonObject stats = o.getAsJsonObject("stats");
        assertEquals(2.0, stats.get("avgTasksMs").getAsDouble(), 1e-9);
        assertEquals(3.0, stats.get("avgCapMs").getAsDouble(), 1e-9);
        assertEquals(8.0, stats.get("avgSwapMs").getAsDouble(), 1e-9);
        assertEquals(1.0, stats.getAsJsonObject("avgNamedMs").get("render").getAsDouble(), 1e-9);
        assertEquals(0.4, stats.getAsJsonObject("avgNamedMs").get("tick").getAsDouble(), 1e-9);
        assertEquals(1.0, stats.getAsJsonObject("avgNamedMs").get("tasks").getAsDouble(), 1e-9);
        assertEquals(2.0, stats.getAsJsonObject("avgBucketsMs").get("hud").getAsDouble(), 1e-9);

        JsonObject plain = parse().getAsJsonArray("frames").get(0).getAsJsonObject();
        assertEquals(0, plain.get("tasksUs").getAsLong(), "a frame with no tasks still says so");
        assertTrue(plain.getAsJsonObject("namedUs").keySet().isEmpty(), "empty windows are omitted, like empty buckets");
    }

    @Test
    void framesAndStatsHaveTheirShapeInOrder() {
        JsonObject o = parse();
        assertEquals(FRAME_KEYS, List.copyOf(o.getAsJsonArray("frames").get(0).getAsJsonObject().keySet()),
                "frame shape changed - bump SCHEMA if that was intended");
        JsonObject stats = o.getAsJsonObject("stats");
        assertEquals(STATS_KEYS, List.copyOf(stats.keySet()),
                "stats object shape changed - bump SCHEMA if that was intended");
        assertEquals(List.of("render", "tick", "tasks"), List.copyOf(stats.getAsJsonObject("avgNamedMs").keySet()),
                "the averages list every window, zeros included, as avgBucketsMs lists every bucket");
    }

    @Test
    void modsArrayCarriesEveryModAndMember() {
        JsonObject o = JsonParser.parseString(JsonReportWriter.write(TestSessions.phase5(), environment(), "0.5.0"))
                .getAsJsonObject();
        JsonArray mods = o.getAsJsonArray("mods");
        assertEquals(4, mods.size());
        JsonObject create = mods.get(0).getAsJsonObject();
        List<String> keys = new ArrayList<>(RANKED_KEYS);
        keys.add("entries");
        assertEquals(keys, List.copyOf(create.keySet()), "a ranked row, then its entries");
        assertEquals("create", create.get("key").getAsString());
        assertEquals(4_000, create.get("renderUs").getAsLong());
        assertEquals(1, create.get("members").getAsInt(), "members stays the count, as on every ranked row");
        JsonArray entries = create.getAsJsonArray("entries");
        assertEquals(1, entries.size());
        assertEquals("event@create:net.neoforged.neoforge.client.event.RenderLevelStageEvent",
                entries.get(0).getAsJsonObject().get("key").getAsString());
        JsonObject somemod = mods.get(3).getAsJsonObject();
        assertEquals(2, somemod.getAsJsonArray("entries").size());
        assertEquals(800, somemod.get("tickUs").getAsLong(), "the tick handler's time is tick time");
        assertEquals(0, parse().getAsJsonArray("mods").size(), "a session with none still writes the array");
    }

    @Test
    void modsArrayIsNotCutLikeTheReport() {
        JsonArray mods = JsonParser.parseString(JsonReportWriter.write(TestSessions.manyMods(), environment(), "0.5.0"))
                .getAsJsonObject().getAsJsonArray("mods");
        assertEquals(17, mods.size(), "every mod, where the report shows 15");
        for (int i = 0; i < mods.size(); i++) {
            assertEquals(5, mods.get(i).getAsJsonObject().getAsJsonArray("entries").size(),
                    "every member, where the report shows 3");
        }
        assertEquals("mod17", mods.get(16).getAsJsonObject().get("key").getAsString(), "most expensive first");
    }

    @Test
    void everyRecommendationIsInTheJson() {
        JsonObject o = JsonParser.parseString(JsonReportWriter.write(TestSessions.phase5(), environment(), "0.5.0"))
                .getAsJsonObject();
        JsonArray advice = o.getAsJsonArray("recommendations");
        assertEquals(5, advice.size());
        JsonObject first = advice.get(0).getAsJsonObject();
        assertEquals(List.of("rule", "kind", "audience", "title", "finding", "action", "msPerFrame", "worstMs"),
                List.copyOf(first.keySet()));
        assertEquals("mod-handlers", first.get("rule").getAsString());
        assertEquals("FRAME_RATE", first.get("kind").getAsString());
        assertEquals("PACK_AUTHOR", first.get("audience").getAsString());
        assertEquals(2.0, first.get("msPerFrame").getAsDouble(), 1e-9);
        List<String> keys = new ArrayList<>(o.keySet());
        assertEquals(keys.indexOf("stats") + 1, keys.indexOf("recommendations"), "right after the averages");
    }
}
