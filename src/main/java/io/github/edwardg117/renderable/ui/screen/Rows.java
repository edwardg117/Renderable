package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.EntityKey;
import io.github.edwardg117.renderable.core.FlywheelStats;
import io.github.edwardg117.renderable.core.Fmt;
import io.github.edwardg117.renderable.core.FrameRecord;
import io.github.edwardg117.renderable.core.FrameStats;
import io.github.edwardg117.renderable.core.MeshEntry;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.RankedEntry;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SectionKey;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.Splits;
import io.github.edwardg117.renderable.core.TopEntry;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Builds the rows for each tab from a finished session. */
public final class Rows {
    private static final long[] HISTOGRAM_EDGES_MS = {8, 16, 33, 50, 100, 250};

    private Rows() {
    }

    public static List<Row> build(Tab tab, SessionResult r, Ranking.Sort sort, Set<String> expanded) {
        return switch (tab) {
            case FRAMES -> frames(r);
            case HITCHES -> hitches(r);
            case BLOCK_ENTITIES -> ranked(r, ProfileKey.Category.BLOCK_ENTITY, sort, expanded);
            case ENTITIES -> ranked(r, ProfileKey.Category.ENTITY, sort, expanded);
            case PARTICLES -> particles(r, sort);
            case FLYWHEEL -> flywheel(r);
            case BLOCKS -> blocks(r);
            case SECTIONS -> sections(r);
        };
    }

    static List<Row> frames(SessionResult r) {
        FrameStats st = r.stats();
        List<Row> rows = new ArrayList<>();
        rows.add(Row.plain("Sampled", String.format(Locale.ROOT, "%.1f s, %d frames", st.durationSeconds(), st.frames())));
        rows.add(Row.plain("Average frame", String.format(Locale.ROOT, "%s ms (%.0f fps)", Fmt.ms(st.avgNanos()), st.avgFps())));
        rows.add(Row.plain("p50 / p95 / p99 / max", Fmt.ms(st.p50Nanos()) + " / " + Fmt.ms(st.p95Nanos()) + " / "
                + Fmt.ms(st.p99Nanos()) + " / " + Fmt.ms(st.maxNanos()) + " ms"));
        rows.add(Row.plain("Hitch threshold", Fmt.ms(r.hitchThresholdNanos()) + " ms"));
        rows.add(Row.plain("Hitches", r.hitches().size() + " (" + Fmt.pct(r.hitchSharePercent()) + " of time)"));
        rows.add(Row.plain("GC", st.gcCount() + " collections, " + st.gcMillis() + " ms paused"));
        rows.add(Row.plain("Renderable overhead", String.format(Locale.ROOT, "%.2f ms over %s timed calls and %s vertex calls",
                r.overheadEstimateMs(), Fmt.count(r.timedCalls()), Fmt.count(r.vertexCalls()))));
        if (r.selfNanos() > 0) {
            rows.add(Row.plain("Renderable self time", Fmt.ms2(r.selfNanos()) + " ms (HUD and Flywheel sampling)"));
        }
        if (r.unbalancedFrames() > 0) {
            rows.add(Row.plain("Attribution stack reset", r.unbalancedFrames() + " frames (a hook threw past its end call)"));
        }
        rows.add(Row.plain("", ""));

        rows.add(Row.heading("Average split per frame"));
        rows.add(Row.plain("tick", Fmt.ms2(st.avgTickNanos()) + " ms"));
        for (Bucket b : Bucket.VALUES) {
            if (!b.isRender() && st.avgBucket(b) > 0) rows.add(Row.plain("  " + b.label(), Fmt.ms2(st.avgBucket(b)) + " ms"));
        }
        rows.add(Row.plain("  other tick", Fmt.ms2(st.avgOtherTickNanos()) + " ms"));
        rows.add(Row.plain("render", Fmt.ms2(st.avgRenderNanos()) + " ms"));
        for (Bucket b : Bucket.VALUES) {
            if (b.isRender() && st.avgBucket(b) > 0) rows.add(Row.plain("  " + b.label(), Fmt.ms2(st.avgBucket(b)) + " ms"));
        }
        rows.add(Row.plain("  other render", Fmt.ms2(st.avgOtherRenderNanos()) + " ms"));
        rows.add(Row.plain("swap / wait", Fmt.ms2(st.avgSwapNanos()) + " ms"));
        rows.add(Row.plain("", ""));

        rows.add(Row.heading("Frame time histogram"));
        int[] counts = new int[HISTOGRAM_EDGES_MS.length + 1];
        for (FrameRecord f : r.frames()) {
            double ms = f.totalNanos() / 1e6;
            int bin = 0;
            while (bin < HISTOGRAM_EDGES_MS.length && ms >= HISTOGRAM_EDGES_MS[bin]) bin++;
            counts[bin]++;
        }
        int max = Arrays.stream(counts).max().orElse(0);
        for (int i = 0; i <= HISTOGRAM_EDGES_MS.length; i++) {
            String label;
            if (i == 0) {
                label = "< " + HISTOGRAM_EDGES_MS[0] + " ms";
            } else if (i == HISTOGRAM_EDGES_MS.length) {
                label = ">= " + HISTOGRAM_EDGES_MS[i - 1] + " ms";
            } else {
                label = HISTOGRAM_EDGES_MS[i - 1] + " - " + HISTOGRAM_EDGES_MS[i] + " ms";
            }
            int bar = max == 0 ? 0 : (int) Math.round(30.0 * counts[i] / max);
            rows.add(Row.plain(label, String.format(Locale.ROOT, "%5d  %s", counts[i], "#".repeat(bar))));
        }
        return rows;
    }

    static List<Row> hitches(SessionResult r) {
        List<Row> rows = new ArrayList<>();
        if (r.hitches().isEmpty()) {
            rows.add(Row.plain("No hitches", "", "No frame exceeded " + Fmt.ms(r.hitchThresholdNanos()) + " ms"));
            return rows;
        }
        int n = 0;
        for (FrameRecord h : r.hitches()) {
            n++;
            String frame = "#" + n + "  frame " + h.index() + " @ " + Fmt.secs(h.startNanos()) + " s";
            rows.add(new Row(new String[]{frame, Fmt.ms(h.totalNanos()), Splits.frame(h) + " | gc " + h.gcMillis() + " ms"},
                    null, null, true, false, 0, null, -1));
            for (TopEntry t : h.top()) {
                rows.add(new Row(new String[]{t.key().label(), Fmt.ms2(t.nanos()), t.gc() ? "[gc]" : ""},
                        t.key(), t.key().typeId(), false, false, 1, posOf(t.key()), entityIdOf(t.key())));
            }
        }
        return rows;
    }

    static List<Row> ranked(SessionResult r, ProfileKey.Category category, Ranking.Sort sort, Set<String> expanded) {
        int frames = r.frameCount();
        long total = r.stats().totalNanos();
        List<Row> rows = new ArrayList<>();
        if (category == ProfileKey.Category.BLOCK_ENTITY
                && (r.bucketTotalNanos(Bucket.FLYWHEEL) > 0 || r.flywheel().total() > 0)) {
            rows.add(Row.heading("Flywheel-instanced machines are not timed here; see the Flywheel tab"));
        }
        for (RankedEntry t : Ranking.sort(Ranking.byType(r, category), sort)) {
            rows.add(new Row(cells(t.typeId() + "  (" + t.members() + ")", t, frames, total),
                    null, t.typeId(), true, true, 0, null, -1));
            if (expanded.contains(t.typeId())) {
                for (RankedEntry m : Ranking.sort(Ranking.membersOf(r, category, t.typeId()), sort)) {
                    rows.add(new Row(cells(memberLabel(m.key()), m, frames, total),
                            m.key(), t.typeId(), false, false, 1, posOf(m.key()), entityIdOf(m.key())));
                }
            }
        }
        if (rows.isEmpty()) rows.add(Row.plain("(no data)"));
        return rows;
    }

    /** Spec §5.3 Particles tab: one row per particle class. */
    static List<Row> particles(SessionResult r, Ranking.Sort sort) {
        int frames = r.frameCount();
        long total = r.stats().totalNanos();
        List<Row> rows = new ArrayList<>();
        for (RankedEntry e : Ranking.sort(Ranking.byKey(r, ProfileKey.Category.PARTICLE), sort)) {
            rows.add(new Row(new String[]{
                    e.label(),
                    Fmt.us(e.renderUsPerFrame(frames)),
                    Fmt.us(e.tickUsPerFrame(frames)),
                    Fmt.pct(e.sharePercent(total)),
                    Fmt.num(e.callsPerFrame(frames)),
                    Fmt.num(e.verticesPerFrame(frames)),
                    Fmt.count(e.gcFrames())},
                    e.key(), e.typeId(), false, false, 0, null, -1));
        }
        if (rows.isEmpty()) rows.add(Row.plain("(no particles rendered)"));
        return rows;
    }

    /** Spec §5.3 Flywheel tab: the bucket, then the averaged visual counts by class. */
    static List<Row> flywheel(SessionResult r) {
        int frames = r.frameCount();
        long total = r.stats().totalNanos();
        long bucket = r.bucketTotalNanos(Bucket.FLYWHEEL);
        FlywheelStats fw = r.flywheel();
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(new String[]{"Flywheel bucket",
                Fmt.us(frames == 0 ? 0 : bucket / 1000.0 / frames) + " us/f",
                Fmt.pct(total == 0 ? 0 : 100.0 * bucket / total)},
                null, null, true, false, 0, null, -1));
        if (fw.samples() == 0) {
            rows.add(Row.plain("(no Flywheel visualization ran)"));
            return rows;
        }
        rows.add(new Row(new String[]{"Visuals, average over " + fw.samples() + " samples",
                Fmt.num(fw.total()), ""},
                null, null, true, false, 0, null, -1));
        for (Map.Entry<String, Double> e : fw.byAverageDesc()) {
            String k = e.getKey();
            String label = k.substring(k.lastIndexOf('.') + 1);
            rows.add(new Row(new String[]{label, Fmt.num(e.getValue()), ""},
                    null, null, false, false, 1, null, -1));
        }
        return rows;
    }

    /** Spec §5.3 Blocks tab: one row per block, by mesh time per second; the same order as the report. */
    static List<Row> blocks(SessionResult r) {
        double seconds = r.stats().durationSeconds();
        List<Row> rows = new ArrayList<>();
        for (MeshEntry e : Ranking.mesh(r, ProfileKey.Category.BLOCK, Ranking.MeshSort.TIME)) {
            rows.add(new Row(new String[]{e.key().label(), Fmt.num(e.msPerSecond(seconds)), Fmt.us(e.usPerCall()), Fmt.count(e.calls())},
                    e.key(), e.key().typeId(), false, false, 0, null, -1));
        }
        if (rows.isEmpty()) rows.add(Row.plain("(no data: Sodium mesh hooks did not run)"));
        return rows;
    }

    /** Spec §5.3 Sections tab: one row per rebuilt section, by rebuild count; the row's position is the section centre. */
    static List<Row> sections(SessionResult r) {
        double seconds = r.stats().durationSeconds();
        List<Row> rows = new ArrayList<>();
        for (MeshEntry e : Ranking.mesh(r, ProfileKey.Category.SECTION, Ranking.MeshSort.CALLS)) {
            SectionKey k = (SectionKey) e.key();
            rows.add(new Row(new String[]{k.label(), Fmt.count(e.calls()), Fmt.num(e.callsPerSecond(seconds)), Fmt.ms(e.nanos()),
                    Fmt.ms2(e.calls() == 0 ? 0 : e.nanos() / e.calls())},
                    k, k.typeId(), false, false, 0, new BlockPos(k.centerX(), k.centerY(), k.centerZ()), -1));
        }
        if (rows.isEmpty()) rows.add(Row.plain("(no data: Sodium mesh hooks did not run)"));
        return rows;
    }

    static String[] cells(String name, RankedEntry e, int frames, long total) {
        return new String[]{
                name,
                Fmt.us(e.renderUsPerFrame(frames)),
                Fmt.us(e.tickUsPerFrame(frames)),
                Fmt.pct(e.sharePercent(total)),
                Fmt.peakMs(e.maxFrameNanos(), e.gcFrames()),
                Fmt.count(e.gcFrames()),
                Fmt.pct(e.shadowPercent()),
                Fmt.num(e.verticesPerFrame(frames)),
                Fmt.count(e.calls())};
    }

    static String memberLabel(ProfileKey k) {
        if (k instanceof BlockEntityKey b) return "@ " + b.x() + " " + b.y() + " " + b.z();
        if (k instanceof EntityKey e) return "#" + e.entityId() + (e.name().isEmpty() ? "" : "  " + e.name());
        return k.label();
    }

    static BlockPos posOf(ProfileKey k) {
        return k instanceof BlockEntityKey b ? new BlockPos(b.x(), b.y(), b.z()) : null;
    }

    static int entityIdOf(ProfileKey k) {
        return k instanceof EntityKey e ? e.entityId() : -1;
    }
}
