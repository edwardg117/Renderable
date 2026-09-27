package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.EmptyStates;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.FlywheelStats;
import io.github.edwardg117.renderable.core.ParticleKey;
import io.github.edwardg117.renderable.core.RankedEntry;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.core.SessionConfig;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.TestSessions;
import io.github.edwardg117.renderable.core.advice.Recommendation;
import io.github.edwardg117.renderable.core.advice.Recommendation.Audience;
import io.github.edwardg117.renderable.core.advice.Recommendation.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rows the screen builds: the Advice tab's, and the names and order of the Flywheel and Particles tabs.
 * {@code Rows} imports {@code BlockPos}, which the test classpath lacks, so a test here must never call
 * {@link Row#pos()}; building rows that leave it null loads fine.
 */
class RowsTest {

    /** Each row as "indent group|- text". */
    private static List<String> describe(List<Row> rows) {
        return rows.stream().map(r -> r.indent() + (r.group() ? " G " : " - ") + String.join("|", r.cells())).toList();
    }

    @Test
    void adviceGroupsThePlayersFirstWithEachBodyTwoIndentsIn() {
        Recommendation author = new Recommendation("stack-resets", Kind.NOTE, Audience.PACK_AUTHOR, "Unbalanced timing",
                "12 frames had unbalanced timing.", "Report it with the log.", 0, 0);
        Recommendation player = new Recommendation("particles", Kind.FRAME_RATE, Audience.PLAYER, "Particles are on All",
                "Particles take 0.9 ms a frame.", "Set Particles to Decreased.", 0.9, 0);
        Function<String, List<String>> oneLine = List::of;
        List<Row> rows = Rows.advice(List.of(author, player), oneLine);
        assertEquals(List.of(
                "0 G For you",
                "1 G Particles are on All  (0.9 ms/frame)",
                "2 - Particles take 0.9 ms a frame.",
                "2 - Set Particles to Decreased.",
                "0 - ",
                "0 G For the pack author",
                "1 G Unbalanced timing",
                "2 - 12 frames had unbalanced timing.",
                "2 - Report it with the log.",
                "0 - "), describe(rows), "a note has no at-stake figure; an audience with none gets no heading");
        for (Row r : rows) {
            assertNull(r.key(), "nothing to reselect after a rebuild");
            assertEquals(-1, r.entityId(), "Teleport stays off");
            assertFalse(r.expandable());
        }
        assertEquals(List.of("0 - " + EmptyStates.advice()), describe(Rows.advice(List.of(), oneLine)));
    }

    @Test
    void theFlywheelNameColumnReadsNumbersAsNumbersAndIgnoresCase() {
        // Two PressVisuals collide, so they read by package, in lower case.
        FlywheelStats fw = new FlywheelStats(3, 0, 0, 0, Map.of("a.Visual10", 1.0, "a.Visual2", 2.0,
                "x.press.PressVisual", 3.0, "y.other.PressVisual", 4.0, "z.ShaftVisual", 5.0));
        SessionResult base = TestSessions.phase3();
        SessionResult r = new SessionResult(base.config(), base.wallStartMillis(), base.frames(), base.accumulators(),
                base.stats(), base.hitchThresholdNanos(), base.hitches(), base.timedCalls(), base.vertexCalls(),
                base.selfNanos(), base.overheadEstimateMs(), base.unbalancedFrames(), fw, base.mesh());
        Environment env = Environment.of(Map.of("flywheelBackend", "flywheel:indirect"), List.of());
        List<String> names = Rows.flywheel(r, env, ColumnSort.of(Ranking.Sort.LABEL), true).stream()
                .filter(row -> row.indent() == 1).map(row -> row.cells()[0]).toList();
        assertEquals(List.of("other.PressVisual", "press.PressVisual", "ShaftVisual", "Visual2", "Visual10"), names);
    }

    @Test
    void theParticlesTabOpensInTheReportsOrderUnderTheReportsNames() {
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000_000_000L, 2.0, 16_000_000L, 5, 0, 0), () -> now[0], 0);
        Accumulator flame = s.accumulator(TestSessions.FLAME);
        Accumulator ember = s.accumulator(new ParticleKey("com.example.fx.EmberParticle"));
        s.onRenderPre(0, 0);
        now[0] = 1_000_000;
        s.begin(flame, Bucket.PARTICLE_RENDER);
        now[0] = 2_000_000;
        s.end();
        s.begin(ember, Bucket.PARTICLE_RENDER);
        now[0] = 3_000_000;
        s.end();
        s.onRenderPost();
        now[0] = 16_000_000;
        s.onRenderPre(0, 0);
        SessionResult r = s.finish();

        SortState opening = new SortState();
        List<String> screen = Rows.particles(r, Environment.empty(), opening.sort(Tab.PARTICLES),
                opening.ascending(Tab.PARTICLES)).stream().map(row -> row.cells()[0]).toList();
        assertEquals(List.of("EmberParticle", "FlameParticle"), screen, "short names; the two tie, so A to Z");
        assertEquals(Ranking.particles(r).stream().map(RankedEntry::label).toList(), screen,
                "the order and names the report prints (spec §5.3)");
    }
}
