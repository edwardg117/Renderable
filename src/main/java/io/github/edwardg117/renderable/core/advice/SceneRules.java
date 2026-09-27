package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Fmt;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.advice.Recommendation.Audience;
import io.github.edwardg117.renderable.core.advice.Recommendation.Kind;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Machines and builds in view (spec addendum §P5.B2): one method per rule, in the table's order. */
final class SceneRules {

    private SceneRules() {
    }

    static void all(Facts f, List<Recommendation> out) {
        blockEntityTypes(f, out);
        entityTypes(f, out);
        particleClasses(f, out);
        hitchRepeat(f, out);
    }

    static void blockEntityTypes(Facts f, List<Recommendation> out) {
        int n = 0;
        for (Facts.Cost c : f.blockEntityTypes()) {
            if (n == Thresholds.TOP_TYPES || c.msPerFrame() < Thresholds.TYPE_MS) break;
            n++;
            String where = c.where().isEmpty() ? "" : "; the most expensive is at " + c.where();
            out.add(new Recommendation("block-entity-type", Kind.FRAME_RATE, Audience.PLAYER, "Costly block entity: " + c.label(),
                    String.format(Locale.ROOT, "%s takes %.1f ms a frame across %s%s.",
                            c.label(), c.msPerFrame(), Words.count(c.members(), "block", "blocks"), where),
                    "It is the build, not a setting: fewer of them in view at once costs less."
                            + " Highlight on the Block Entities tab shows where they are.",
                    c.msPerFrame(), 0));
        }
    }

    static void entityTypes(Facts f, List<Recommendation> out) {
        int n = 0;
        for (Facts.Cost c : f.entityTypes()) {
            if (n == Thresholds.TOP_TYPES || c.msPerFrame() < Thresholds.TYPE_MS) break;
            n++;
            out.add(new Recommendation("entity-type", Kind.FRAME_RATE, Audience.PLAYER, "Costly entity: " + c.label(),
                    String.format(Locale.ROOT, "%s takes %.1f ms a frame across %s.",
                            c.label(), c.msPerFrame(), Words.count(c.members(), "entity", "entities")),
                    "Fewer of them nearby, or a lower Entity Distance.", c.msPerFrame(), 0));
        }
    }

    static void particleClasses(Facts f, List<Recommendation> out) {
        int n = 0;
        for (Facts.Cost c : f.particleClasses()) {
            if (n == Thresholds.TOP_TYPES || c.msPerFrame() < Thresholds.PARTICLE_CLASS_MS) break;
            n++;
            long drawn = Math.round(c.livePerFrame());
            out.add(new Recommendation("particle-type", Kind.FRAME_RATE, Audience.PLAYER, "Costly particle: " + c.label(),
                    String.format(Locale.ROOT, "%s takes %.1f ms a frame", c.label(), c.msPerFrame())
                            + (drawn > 0 ? ", with " + Fmt.count(drawn) + " drawn on average." : "."),
                    "Find what emits them; lowering Particles hides most of them.", c.msPerFrame(), 0));
        }
    }

    /**
     * A block entity, entity or particle that caused several hitches: among a hitch's top costs with at least a
     * quarter of it, and no GC pause that could account for it. Mod keys are the mod rules'.
     */
    static void hitchRepeat(Facts f, List<Recommendation> out) {
        int hitches = f.hitches().size();
        LinkedHashMap<ProfileKey, Integer> count = new LinkedHashMap<>();
        Map<ProfileKey, Double> worst = new LinkedHashMap<>();
        Map<ProfileKey, Double> sum = new LinkedHashMap<>();
        for (Facts.Hitch h : f.hitches()) {
            Set<ProfileKey> seen = new HashSet<>();
            for (Facts.Top t : h.top()) {
                if (t.key().category() == ProfileKey.Category.MOD || !Thresholds.causes(t, h) || !seen.add(t.key())) continue;
                count.merge(t.key(), 1, Integer::sum);
                worst.merge(t.key(), t.ms(), Math::max);
                sum.merge(t.key(), t.ms(), Double::sum);
            }
        }
        for (Map.Entry<ProfileKey, Integer> e : count.entrySet()) {
            int n = e.getValue();
            if (n < Thresholds.REPEATED_HITCHES || n < Thresholds.HITCH_REPEAT_SHARE * hitches) continue;
            ProfileKey k = e.getKey();
            double w = worst.get(k);
            out.add(new Recommendation("hitch-repeat", Kind.STUTTER, Audience.PLAYER, "Repeated stutter: " + k.label(),
                    String.format(Locale.ROOT, "%s was a top cost in %d of %d hitches, up to %.0f ms.", k.label(), n, hitches, w),
                    "Look at what it is doing when the stutter happens. A pack author can take this report to the mod.",
                    sum.get(k) / f.frames(), w));
        }
    }
}
