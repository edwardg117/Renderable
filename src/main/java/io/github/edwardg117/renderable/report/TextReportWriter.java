package io.github.edwardg117.renderable.report;

import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.FlywheelStats;
import io.github.edwardg117.renderable.core.Fmt;
import io.github.edwardg117.renderable.core.FrameRecord;
import io.github.edwardg117.renderable.core.FrameStats;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.RankedEntry;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.Splits;
import io.github.edwardg117.renderable.core.TopEntry;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Spec §6.1: the human-readable report. Pure Java. */
public final class TextReportWriter {
    static final int TOP = 20;
    static final int HITCHES = 25;

    private TextReportWriter() {
    }

    public static String write(SessionResult r, Environment env, String modVersion) {
        StringBuilder sb = new StringBuilder(16_384);
        FrameStats st = r.stats();
        int frames = r.frameCount();
        long total = st.totalNanos();

        sb.append("Renderable report  v").append(modVersion)
                .append("  ").append(Fmt.timestamp(r.wallStartMillis()))
                .append("  session ").append(Fmt.secs(total)).append(" s\n\n");

        sb.append("Environment\n");
        env.entries().forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append('\n'));
        sb.append("  hooks applied: ")
                .append(env.hooksApplied().isEmpty() ? "none" : String.join(", ", env.hooksApplied()))
                .append("\n\n");

        sb.append("Frames\n");
        sb.append(String.format(Locale.ROOT,
                "  Sampled %s s, %d frames, avg %s ms (%.0f fps), p50 %s, p95 %s, p99 %s, max %s ms\n",
                Fmt.secs(total), frames, Fmt.ms(st.avgNanos()), st.avgFps(),
                Fmt.ms(st.p50Nanos()), Fmt.ms(st.p95Nanos()), Fmt.ms(st.p99Nanos()), Fmt.ms(st.maxNanos())));
        sb.append(String.format(Locale.ROOT,
                "  Hitches: %d frames over %s ms threshold, totalling %s s (%s of wall time)\n",
                r.hitches().size(), Fmt.ms(r.hitchThresholdNanos()), Fmt.secs(r.hitchTotalNanos()),
                Fmt.pct(r.hitchSharePercent())));
        sb.append("  Time split (avg ms/frame): ").append(Splits.average(st)).append('\n');
        sb.append(String.format(Locale.ROOT, "  GC: %d collections, %d ms paused\n", st.gcCount(), st.gcMillis()));
        sb.append(String.format(Locale.ROOT, "  Renderable overhead: about %.2f ms over %d timed calls\n",
                r.overheadEstimateMs(), r.timedCalls()));
        if (r.unbalancedFrames() > 0) {
            sb.append(String.format(Locale.ROOT,
                    "  Attribution stack reset on %d frames (a hook threw past its end call; buckets on those frames are incomplete)\n",
                    r.unbalancedFrames()));
        }
        sb.append('\n');

        sb.append("Hitches (worst ").append(HITCHES).append(")\n");
        if (r.hitches().isEmpty()) {
            sb.append("  none\n");
        }
        int shown = 0;
        for (FrameRecord h : r.hitches()) {
            if (shown == HITCHES) break;
            shown++;
            sb.append(String.format(Locale.ROOT, "  #%d  frame %d @ %ss  %s ms  |  %s  |  gc %d ms\n",
                    shown, h.index(), Fmt.secs(h.startNanos()), Fmt.ms(h.totalNanos()), Splits.frame(h), h.gcMillis()));
            for (TopEntry t : h.top()) {
                sb.append("      ").append(Fmt.ms2(t.nanos())).append(" ms  ").append(t.key().label()).append('\n');
            }
        }
        sb.append('\n');

        appendRanked(sb, "Block entities by type", Ranking.byType(r, ProfileKey.Category.BLOCK_ENTITY), frames, total);
        appendRanked(sb, "Block entities by position", Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY), frames, total);
        appendRanked(sb, "Entities by type", Ranking.byType(r, ProfileKey.Category.ENTITY), frames, total);
        appendRanked(sb, "Entities (individuals)", Ranking.byKey(r, ProfileKey.Category.ENTITY), frames, total);
        appendParticles(sb, r, env, frames, total);
        appendRanked(sb, "Blocks (mesh build)", Ranking.byKey(r, ProfileKey.Category.BLOCK), frames, total);
        appendRanked(sb, "Sections (mesh rebuilds)", Ranking.byKey(r, ProfileKey.Category.SECTION), frames, total);
        appendFlywheel(sb, r, env, frames, total);
        return sb.toString();
    }

    static void appendRanked(StringBuilder sb, String title, List<RankedEntry> entries, int frames, long total) {
        sb.append(title).append(" (top ").append(TOP).append(")\n");
        if (entries.isEmpty()) {
            sb.append("  (no data: hook not active in this build)\n\n");
            return;
        }
        Table t = new Table(
                new String[]{"name", "render us/f", "tick us/f", "share", "peak ms", "shadow", "verts/f", "calls"},
                new boolean[]{false, true, true, true, true, true, true, true});
        for (RankedEntry e : entries.subList(0, Math.min(TOP, entries.size()))) {
            t.row(e.label(),
                    Fmt.us(e.renderUsPerFrame(frames)),
                    Fmt.us(e.tickUsPerFrame(frames)),
                    Fmt.pct(e.sharePercent(total)),
                    Fmt.ms2(e.maxFrameNanos()),
                    Fmt.pct(e.shadowPercent()),
                    Fmt.us(e.verticesPerFrame(frames)),
                    Fmt.count(e.calls()));
        }
        sb.append(t.render("  ")).append('\n');
    }

    /** Spec §6.1 item 7. Particles have no peak or shadow of interest; the average live count replaces them. */
    static void appendParticles(StringBuilder sb, SessionResult r, Environment env, int frames, long total) {
        sb.append("Particles (top ").append(TOP).append(")\n");
        List<RankedEntry> entries = Ranking.byKey(r, ProfileKey.Category.PARTICLE);
        if (entries.isEmpty()) {
            sb.append(env.hooksApplied().contains("PARTICLE_RENDER")
                    ? "  (no particles were rendered)\n\n"
                    : "  (no data: hook not active in this build)\n\n");
            return;
        }
        Table t = new Table(
                new String[]{"class", "render us/f", "tick us/f", "share", "avg live", "verts/f"},
                new boolean[]{false, true, true, true, true, true});
        for (RankedEntry e : entries.subList(0, Math.min(TOP, entries.size()))) {
            t.row(e.label(),
                    Fmt.us(e.renderUsPerFrame(frames)),
                    Fmt.us(e.tickUsPerFrame(frames)),
                    Fmt.pct(e.sharePercent(total)),
                    Fmt.us(e.callsPerFrame(frames)),
                    Fmt.us(e.verticesPerFrame(frames)));
        }
        sb.append(t.render("  ")).append('\n');
    }

    /** Spec §6.1 item 9 and §4.4: the Flywheel bucket plus visual counts, or the exact reason there are none. */
    static void appendFlywheel(StringBuilder sb, SessionResult r, Environment env, int frames, long total) {
        sb.append("Flywheel\n");
        String backend = env.entries().getOrDefault("flywheelBackend", "absent");
        if ("absent".equals(backend)) {
            sb.append("  (no data: Flywheel is not installed)\n\n");
            return;
        }
        FlywheelStats fw = r.flywheel();
        if (fw.samples() == 0) {
            sb.append("  (no data: no Flywheel visualization ran; backend ").append(backend).append(")\n\n");
            return;
        }
        long bucketNanos = r.bucketTotalNanos(Bucket.FLYWHEEL);
        if (bucketNanos == 0 && !env.hooksApplied().contains("FLYWHEEL")) {
            sb.append("  (hook did not apply for flywheel ").append(env.entries().getOrDefault("flywheel", "?"))
                    .append("; the bucket is missing but visual counts were still sampled)\n");
        }
        sb.append(String.format(Locale.ROOT, "  bucket %s ms/frame (%s of frame time), backend %s\n",
                Fmt.ms2(frames == 0 ? 0 : bucketNanos / frames),
                Fmt.pct(total == 0 ? 0 : 100.0 * bucketNanos / total), backend));
        sb.append(String.format(Locale.ROOT, "  visuals: %.1f block entities, %.1f entities, %.1f effects (average over %d samples)\n",
                fw.avgBlockEntities(), fw.avgEntities(), fw.avgEffects(), fw.samples()));
        Table t = new Table(new String[]{"visual class", "avg count"}, new boolean[]{false, true});
        for (Map.Entry<String, Double> e : fw.byAverageDesc().stream().limit(TOP).toList()) {
            t.row(e.getKey(), String.format(Locale.ROOT, "%.1f", e.getValue()));
        }
        sb.append(t.render("  ")).append('\n');
    }
}
