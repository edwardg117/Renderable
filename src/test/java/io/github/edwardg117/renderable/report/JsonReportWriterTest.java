package io.github.edwardg117.renderable.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.TestSessions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonReportWriterTest {
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
        assertEquals(1, o.get("schema").getAsInt());
        assertEquals("0.1.0", o.getAsJsonObject("mod").get("version").getAsString());
        assertEquals("1.21.1", o.getAsJsonObject("environment").get("minecraft").getAsString());
        assertEquals(2, o.getAsJsonObject("environment").getAsJsonArray("hooksApplied").size());

        JsonObject session = o.getAsJsonObject("session");
        assertEquals(20, session.get("frames").getAsInt());
        assertEquals(32.0, session.get("hitchThresholdMs").getAsDouble(), 1e-9);
        assertEquals(0.384, session.get("durationSeconds").getAsDouble(), 1e-9);
        assertEquals(120, session.get("timedCalls").getAsLong());
        assertEquals(5, session.getAsJsonObject("config").get("topContributorsPerFrame").getAsInt());
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
        assertEquals(0, o.getAsJsonObject("flywheel").getAsJsonArray("visuals").size());
    }
}
