package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.advice.Recommendation.Audience;
import io.github.edwardg117.renderable.core.advice.Recommendation.Kind;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pointers for the pack author (spec addendum §P5.B2): one method per rule, in the table's order. */
final class PackRules {

    private PackRules() {
    }

    static void all(Facts f, List<Recommendation> out) {
        unattributedStall(f, out);
        modHandlers(f, out);
        modHud(f, out);
        modNetwork(f, out);
        stackResets(f, out);
    }

    /** A hitch whose time is mostly in code nothing names: other tick, other render or tasks, less named time. */
    static void unattributedStall(Facts f, List<Recommendation> out) {
        Facts.Hitch worstHitch = null;
        String worstWhere = null;
        double worst = 0;
        double sum = 0;
        for (Facts.Hitch h : f.hitches()) {
            double[] parts = {h.unnamedTickMs(), h.unnamedRenderMs(), h.untimedTasksMs()};
            String[] where = {"client tick", "rendering", "main-thread tasks"};
            for (int i = 0; i < parts.length; i++) {
                // The frame's GC pause landed in one of the parts; which one is not known, so each loses it.
                double v = Math.max(0, parts[i] - h.gcMs());
                if (v < Thresholds.STALL_MS || v < Thresholds.STALL_SHARE * h.totalMs()) continue;
                sum += v;
                if (v > worst) {
                    worst = v;
                    worstHitch = h;
                    worstWhere = where[i];
                }
            }
        }
        if (worstHitch == null) return;
        out.add(new Recommendation("unattributed-stall", Kind.STUTTER, Audience.PACK_AUTHOR, "A stall Renderable cannot name",
                String.format(Locale.ROOT, "Hitch #%d (%.0f ms): %.0f ms of %s is in code Renderable does not time.",
                        worstHitch.number(), worstHitch.totalMs(), worst, worstWhere),
                "Profile the same place with a sampling profiler such as spark, which names the code.",
                sum / f.frames(), worst));
    }

    /** A mod's event handlers: their steady cost, or one handler's stutter (see {@link #stutter}). */
    static void modHandlers(Facts f, List<Recommendation> out) {
        for (Facts.ModCost m : f.mods()) {
            if (!Thresholds.isMod(m.modId())) continue;
            Stutter s = stutter(f, m.modId(), ModKey.Kind.EVENT);
            boolean costly = m.handlersMs() >= Thresholds.MOD_MS;
            if (!costly && s == null) continue;
            String finding = s == null
                    ? String.format(Locale.ROOT, "%s's event handlers take %.1f ms a frame, most of it in %s.",
                            m.modId(), m.handlersMs(), m.topEvent())
                    : s.finding(m.modId() + "'s " + ModKey.simpleEventName(s.key().name()) + " handler", f.hitches().size())
                            + (costly ? String.format(Locale.ROOT, " %s's event handlers take %.1f ms a frame.", m.modId(),
                                    m.handlersMs()) : "");
            out.add(new Recommendation("mod-handlers", s == null ? Kind.FRAME_RATE : Kind.STUTTER, Audience.PACK_AUTHOR,
                    m.modId() + "'s event handlers", finding,
                    "Check the mod's config for the feature behind that event, or report it to the mod with this file.",
                    m.handlersMs(), s == null ? 0 : s.worstMs()));
        }
    }

    /** A mod's HUD layers: their steady cost, or one layer's stutter (see {@link #stutter}). */
    static void modHud(Facts f, List<Recommendation> out) {
        for (Facts.ModCost m : f.mods()) {
            if (!Thresholds.isMod(m.modId())) continue;
            Stutter s = stutter(f, m.modId(), ModKey.Kind.LAYER);
            boolean costly = m.layersMs() >= Thresholds.MOD_MS;
            if (!costly && s == null) continue;
            String steady = String.format(Locale.ROOT, "%s's HUD layers take %.1f ms a frame.", m.modId(), m.layersMs());
            String finding = s == null ? steady
                    : s.finding("The " + s.key().name() + " layer", f.hitches().size()) + (costly ? " " + steady : "");
            out.add(new Recommendation("mod-hud", s == null ? Kind.FRAME_RATE : Kind.STUTTER, Audience.PACK_AUTHOR,
                    m.modId() + "'s HUD", finding, "Check the mod's HUD settings, or report it to the mod with this file.",
                    m.layersMs(), s == null ? 0 : s.worstMs()));
        }
    }

    /** A mod's payloads: their steady cost, or one payload type's stutter (see {@link #stutter}). */
    static void modNetwork(Facts f, List<Recommendation> out) {
        for (Facts.ModCost m : f.mods()) {
            if (!Thresholds.isMod(m.modId())) continue;
            Stutter s = stutter(f, m.modId(), ModKey.Kind.PACKET);
            boolean costly = m.packetsMs() >= Thresholds.MOD_MS;
            if (!costly && s == null) continue;
            String steady = String.format(Locale.ROOT, "%s's payloads take %.1f ms a frame on the client.", m.modId(), m.packetsMs());
            String finding = s == null ? steady
                    : s.finding("The " + s.key().name() + " payload", f.hitches().size()) + (costly ? " " + steady : "");
            out.add(new Recommendation("mod-network", s == null ? Kind.FRAME_RATE : Kind.STUTTER, Audience.PACK_AUTHOR,
                    m.modId() + "'s network payloads", finding,
                    "The server is sending work the client handles on its main thread; report it to the mod with this file.",
                    m.packetsMs(), s == null ? 0 : s.worstMs()));
        }
    }

    static void stackResets(Facts f, List<Recommendation> out) {
        if (f.unbalancedFrames() <= 0) return;
        out.add(new Recommendation("stack-resets", Kind.NOTE, Audience.PACK_AUTHOR, "Unbalanced timing",
                String.format(Locale.ROOT, "%s had unbalanced timing: a timed call did not return normally.",
                        Words.count(f.unbalancedFrames(), "frame", "frames")),
                "The game log from around the session should hold the exception; report it with the log.", 0, 0));
    }

    /**
     * A mod's key that caused stutter.
     *
     * @param worstMs     its largest time in one of the hitches the finding is about
     * @param hitchNumber the hitch it stalled, or 0 when it is a repeat
     * @param count       the hitches it caused: 1 for a stall
     */
    private record Stutter(ModKey key, double worstMs, int hitchNumber, int count) {

        /** "{subject} took 370 ms in hitch #1." or "{subject} was a top cost in 4 of 4 hitches, up to 24 ms." */
        String finding(String subject, int hitches) {
            return hitchNumber > 0
                    ? String.format(Locale.ROOT, "%s took %.0f ms in hitch #%d.", subject, worstMs, hitchNumber)
                    : String.format(Locale.ROOT, "%s was a top cost in %d of %d hitches, up to %.0f ms.",
                            subject, count, hitches, worstMs);
        }
    }

    /**
     * The stutter one of a mod's keys of {@code kind} caused, or null. A stall: the key took at least
     * {@link Thresholds#STALL_MS} and {@link Thresholds#STALL_SHARE} of one hitch, and the largest wins. A repeat:
     * the key caused hitches by {@code hitch-repeat}'s test, and the one in the most wins, the first found on a
     * tie. The stall comes first unless the repeat's worst reached it. A contributor with the {@code [gc]} mark
     * counts toward neither. These forms fire whatever the mod's steady cost, because a stall averages to little
     * over a session, the Mods tab names this time so {@code unattributed-stall} skips it, and
     * {@code hitch-repeat} leaves mod keys out (plan 5b, deviations 2, 3 and 6).
     */
    private static Stutter stutter(Facts f, String modId, ModKey.Kind kind) {
        Stutter stall = null;
        LinkedHashMap<ModKey, Integer> repeats = new LinkedHashMap<>();
        LinkedHashMap<ModKey, Double> repeatWorst = new LinkedHashMap<>();
        for (Facts.Hitch h : f.hitches()) {
            Set<ModKey> seen = new HashSet<>();
            for (Facts.Top t : h.top()) {
                if (!(t.key() instanceof ModKey k) || k.kind() != kind || !k.modId().equals(modId) || t.gc()) continue;
                if (t.ms() >= Thresholds.STALL_MS && t.ms() >= Thresholds.STALL_SHARE * h.totalMs()
                        && (stall == null || t.ms() > stall.worstMs())) {
                    stall = new Stutter(k, t.ms(), h.number(), 1);
                }
                if (Thresholds.causes(t, h) && seen.add(k)) {
                    repeats.merge(k, 1, Integer::sum);
                    repeatWorst.merge(k, t.ms(), Math::max);
                }
            }
        }
        Stutter repeat = null;
        for (Map.Entry<ModKey, Integer> e : repeats.entrySet()) {
            int n = e.getValue();
            if (n >= Thresholds.REPEATED_HITCHES && n >= Thresholds.HITCH_REPEAT_SHARE * f.hitches().size()
                    && (repeat == null || n > repeat.count())) {
                repeat = new Stutter(e.getKey(), repeatWorst.get(e.getKey()), 0, n);
            }
        }
        // A repeat as bad as the stall says more: how often, as well as how much.
        return stall == null || repeat != null && repeat.worstMs() >= stall.worstMs() ? repeat : stall;
    }
}
