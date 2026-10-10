package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.FrameRecord;
import io.github.edwardg117.renderable.core.FrameStats;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.RankedEntry;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.TopEntry;
import io.github.edwardg117.renderable.core.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What the recommendation rules read (spec addendum §P5.B1), derived once from a finished session and its
 * environment. Milliseconds are per frame unless the name says otherwise. Rules take this rather than the
 * session, so each rule can be tested with only the numbers it cares about.
 *
 * @param bucketMs           average milliseconds per frame, per {@link Bucket} ordinal
 * @param shadowMs           Iris shadow-pass time of block entities and entities
 * @param shadowableRenderMs render time of block entities and entities, which the shadow share is taken of
 * @param blockEntityTypes   block entity types, most expensive first
 * @param entityTypes        entity types, most expensive first
 * @param particleClasses    particle classes, most expensive first
 * @param hitches            the hitch list, worst first
 * @param maxFrameGcMs       the most GC pause reported in any one frame
 * @param mods               mods, most expensive first
 */
public record Facts(Environment env,
                    int frames,
                    double frameMs,
                    double[] bucketMs,
                    double tasksMs,
                    double capMs,
                    double swapMs,
                    double shadowMs,
                    double shadowableRenderMs,
                    List<Cost> blockEntityTypes,
                    List<Cost> entityTypes,
                    List<Cost> particleClasses,
                    List<Hitch> hitches,
                    int gcCount,
                    long gcTotalMs,
                    long maxFrameGcMs,
                    List<ModCost> mods,
                    int unbalancedFrames) {

    /**
     * One ranked row, reduced to what a rule says about it.
     *
     * @param label        a type id, or a particle class's label
     * @param msPerFrame   render plus tick
     * @param members      positions or individuals in the type; 1 for a particle class
     * @param livePerFrame particles only: the average number rendered each frame
     * @param where        block entities only: the most expensive member's position as "x y z", or ""
     */
    public record Cost(String label, double msPerFrame, int members, double livePerFrame, String where) {
    }

    /**
     * One hitch, numbered as the report numbers them: 1 is the worst.
     *
     * @param gcMs               the GC pause reported in the frame
     * @param chunkUploadLargest true when chunk upload is the largest part of the frame's split
     * @param unnamedTickMs      other tick, less what the Mods tab names in it
     * @param unnamedRenderMs    other render, less what the Mods tab names in it
     * @param untimedTasksMs     tasks, less what the Mods tab names in them
     */
    public record Hitch(int number, double totalMs, double gcMs, double chunkUploadMs, boolean chunkUploadLargest,
                        double unnamedTickMs, double unnamedRenderMs, double untimedTasksMs, List<Top> top) {
    }

    /**
     * One of a hitch's top contributors.
     *
     * @param gc true when the frame's GC pause could account for it: the report's {@code [gc]} mark
     */
    public record Top(ProfileKey key, double ms, boolean gc) {
    }

    /**
     * A mod's timed work per frame: its event handlers, its HUD layers and its packets (spec addendum §P5.A7).
     *
     * @param topEvent the simple name of the event its handlers spent the most on, or ""
     */
    public record ModCost(String modId, double handlersMs, double layersMs, double packetsMs, String topEvent) {
    }

    public double bucket(Bucket b) {
        return bucketMs[b.ordinal()];
    }

    /** The environment value for {@code key}, or null when it was not collected or reads "unknown". */
    public String text(String key) {
        String v = env.value(key);
        return env.has(key) && !"unknown".equals(v) ? v : null;
    }

    /** The environment value for {@code key} as a number, or NaN when it is absent or not a number. */
    public double number(String key) {
        String v = text(key);
        if (v == null) return Double.NaN;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** True when Iris reports a shader pack by name. */
    public boolean shadersOn() {
        String pack = text("shaderPack");
        return pack != null && !"none".equals(pack);
    }

    public static Facts of(SessionResult r, Environment env) {
        FrameStats st = r.stats();
        int frames = r.frameCount();
        double[] bucketMs = new double[Bucket.COUNT];
        for (Bucket b : Bucket.VALUES) bucketMs[b.ordinal()] = st.avgBucket(b) / 1e6;

        List<RankedEntry> blockEntityRows = Ranking.byType(r, ProfileKey.Category.BLOCK_ENTITY);
        List<RankedEntry> entityRows = Ranking.byType(r, ProfileKey.Category.ENTITY);
        long shadow = 0;
        long shadowable = 0;
        for (List<RankedEntry> rows : List.of(blockEntityRows, entityRows)) {
            for (RankedEntry e : rows) {
                shadow += e.shadowNanos();
                shadowable += e.renderNanos();
            }
        }

        Map<String, List<RankedEntry>> blockEntityMembers = Ranking.membersByType(r, ProfileKey.Category.BLOCK_ENTITY);
        List<Cost> blockEntities = new ArrayList<>();
        for (RankedEntry t : blockEntityRows) {
            List<RankedEntry> members = blockEntityMembers.getOrDefault(t.typeId(), List.of());
            String where = !members.isEmpty() && members.get(0).key() instanceof BlockEntityKey k
                    ? k.x() + " " + k.y() + " " + k.z() : "";
            blockEntities.add(new Cost(t.typeId(), perFrame(t.totalNanos(), frames), t.members(), 0, where));
        }
        List<Cost> entities = new ArrayList<>();
        for (RankedEntry t : entityRows) {
            entities.add(new Cost(t.typeId(), perFrame(t.totalNanos(), frames), t.members(), 0, ""));
        }
        List<Cost> particles = new ArrayList<>();
        for (RankedEntry p : Ranking.byKey(r, ProfileKey.Category.PARTICLE)) {
            particles.add(new Cost(p.label(), perFrame(p.totalNanos(), frames), 1, p.callsPerFrame(frames), ""));
        }

        List<Hitch> hitches = new ArrayList<>();
        for (int i = 0; i < r.hitches().size(); i++) hitches.add(hitch(i + 1, r.hitches().get(i)));

        long maxFrameGc = 0;
        for (FrameRecord f : r.frames()) maxFrameGc = Math.max(maxFrameGc, f.gcMillis());

        Map<String, List<RankedEntry>> modMembers = Ranking.membersByType(r, ProfileKey.Category.MOD);
        List<ModCost> mods = new ArrayList<>();
        for (RankedEntry m : Ranking.byType(r, ProfileKey.Category.MOD)) {
            long handlers = 0;
            long layers = 0;
            long packets = 0;
            long topEventNanos = -1;
            String topEvent = "";
            for (RankedEntry e : modMembers.getOrDefault(m.typeId(), List.of())) {
                ModKey k = (ModKey) e.key();
                switch (k.kind()) {
                    case EVENT -> {
                        handlers += e.totalNanos();
                        if (e.totalNanos() > topEventNanos) {
                            topEventNanos = e.totalNanos();
                            topEvent = ModKey.simpleEventName(k.name());
                        }
                    }
                    case LAYER -> layers += e.totalNanos();
                    case PACKET -> packets += e.totalNanos();
                }
            }
            mods.add(new ModCost(m.typeId(), perFrame(handlers, frames), perFrame(layers, frames),
                    perFrame(packets, frames), topEvent));
        }

        return new Facts(env, frames, st.avgNanos() / 1e6, bucketMs, st.avgTasksNanos() / 1e6,
                st.avgCapNanos() / 1e6, st.avgSwapNanos() / 1e6, perFrame(shadow, frames),
                perFrame(shadowable, frames), List.copyOf(blockEntities), List.copyOf(entities),
                List.copyOf(particles), List.copyOf(hitches), st.gcCount(), st.gcMillis(), maxFrameGc,
                List.copyOf(mods), r.unbalancedFrames());
    }

    private static Hitch hitch(int number, FrameRecord h) {
        long upload = h.bucket(Bucket.CHUNK_UPLOAD);
        long other = Math.max(Math.max(h.tickNanos(), h.otherRenderNanos()),
                Math.max(h.tasksNanos(), Math.max(h.capNanos(), h.swapNanos())));
        for (Bucket b : Bucket.VALUES) {
            if (b.isRender() && b != Bucket.CHUNK_UPLOAD) other = Math.max(other, h.bucket(b));
        }
        List<Top> top = new ArrayList<>();
        for (TopEntry t : h.top()) top.add(new Top(t.key(), t.nanos() / 1e6, t.gc()));
        return new Hitch(number, h.totalNanos() / 1e6, h.gcMillis(), upload / 1e6, upload > other,
                h.unnamedTickNanos() / 1e6,
                Math.max(0, h.otherRenderNanos() - h.named(Window.RENDER)) / 1e6,
                Math.max(0, h.tasksNanos() - h.named(Window.TASKS)) / 1e6,
                List.copyOf(top));
    }

    private static double perFrame(long nanos, int frames) {
        return frames == 0 ? 0 : nanos / 1e6 / frames;
    }
}
