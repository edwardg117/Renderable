package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.advice.Recommendation.Audience;
import io.github.edwardg117.renderable.core.advice.Recommendation.Kind;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

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

    /**
     * Time in code nothing names (spec addendum §P5.B2, §P6.9). A hitch has three parts that can hold it: other tick,
     * other render and tasks, each less the time the Mods tab names in it and less the frame's GC pause. A stall: one
     * part is at least {@link Thresholds#STALL_MS} and {@link Thresholds#STALL_SHARE} of its hitch. A repeat: one part
     * is at least {@link Thresholds#UNNAMED_SHARE} of its hitch, in {@link Thresholds#REPEATED_HITCHES} or more hitches
     * and in at least {@link Thresholds#HITCH_REPEAT_SHARE} of them, a hitch counting once. A stall's part is also a
     * quarter of its hitch, so a repeat always reaches the stall's worst: when it passes, it is named, and says both.
     */
    static void unattributedStall(Facts f, List<Recommendation> out) {
        String[] where = {"client tick", "rendering", "main-thread tasks"};
        Facts.Hitch stallHitch = null;
        String stallWhere = null;
        double stall = 0;
        double stallSum = 0;
        int repeats = 0;
        String repeatWhere = null;
        double repeatWorst = 0;
        double repeatSum = 0;
        for (Facts.Hitch h : f.hitches()) {
            double[] parts = {h.unnamedTickMs(), h.unnamedRenderMs(), h.untimedTasksMs()};
            boolean counted = false;
            for (int i = 0; i < parts.length; i++) {
                // The frame's GC pause landed in one of the parts; which one is not known, so each loses it.
                double v = Math.max(0, parts[i] - h.gcMs());
                if (v >= Thresholds.STALL_MS && v >= Thresholds.STALL_SHARE * h.totalMs()) {
                    stallSum += v;
                    if (v > stall) {
                        stall = v;
                        stallHitch = h;
                        stallWhere = where[i];
                    }
                }
                if (v >= Thresholds.UNNAMED_SHARE * h.totalMs()) {
                    repeatSum += v;
                    if (!counted) repeats++;
                    counted = true;
                    if (v > repeatWorst) {
                        repeatWorst = v;
                        repeatWhere = where[i];
                    }
                }
            }
        }
        int hitches = f.hitches().size();
        String action = "Profile the same place with a sampling profiler such as spark, which names the code.";
        if (repeats >= Thresholds.REPEATED_HITCHES && repeats >= Thresholds.HITCH_REPEAT_SHARE * hitches) {
            out.add(new Recommendation("unattributed-stall", Kind.STUTTER, Audience.PACK_AUTHOR,
                    "Stutter Renderable cannot name",
                    String.format(Locale.ROOT, "In %d of %d hitches, at least a quarter of the frame was in code"
                            + " Renderable does not time, up to %.0f ms of %s.", repeats, hitches, repeatWorst, repeatWhere),
                    action, repeatSum / f.frames(), repeatWorst));
        } else if (stallHitch != null) {
            out.add(new Recommendation("unattributed-stall", Kind.STUTTER, Audience.PACK_AUTHOR,
                    "A stall Renderable cannot name",
                    String.format(Locale.ROOT, "Hitch #%d (%.0f ms): %.0f ms of %s is in code Renderable does not time.",
                            stallHitch.number(), stallHitch.totalMs(), stall, stallWhere),
                    action, stallSum / f.frames(), stall));
        }
    }

    /** A mod's event handlers: their steady cost, or one handler's stutter (see {@link KeyStutter#find}). */
    static void modHandlers(Facts f, List<Recommendation> out) {
        for (Facts.ModCost m : f.mods()) {
            if (!Thresholds.isMod(m.modId())) continue;
            KeyStutter s = KeyStutter.find(f, ofMod(m.modId(), ModKey.Kind.EVENT));
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

    /** A mod's HUD layers: their steady cost, or one layer's stutter (see {@link KeyStutter#find}). */
    static void modHud(Facts f, List<Recommendation> out) {
        for (Facts.ModCost m : f.mods()) {
            if (!Thresholds.isMod(m.modId())) continue;
            KeyStutter s = KeyStutter.find(f, ofMod(m.modId(), ModKey.Kind.LAYER));
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

    /** A mod's payloads: their steady cost, or one payload type's stutter (see {@link KeyStutter#find}). */
    static void modNetwork(Facts f, List<Recommendation> out) {
        for (Facts.ModCost m : f.mods()) {
            if (!Thresholds.isMod(m.modId())) continue;
            KeyStutter s = KeyStutter.find(f, ofMod(m.modId(), ModKey.Kind.PACKET));
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

    /** A mod's keys of one kind: the keys its stutter form looks at. */
    private static Predicate<ModKey> ofMod(String modId, ModKey.Kind kind) {
        return k -> k.kind() == kind && k.modId().equals(modId);
    }
}
