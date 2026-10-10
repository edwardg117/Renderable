package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.ModKey;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A Mods tab key that caused stutter: the stutter forms of the {@code mod-*} rules (spec addendum §P5.B2) and of
 * {@code sound-wait} (§P6.9).
 *
 * @param worstMs     its largest time in one of the hitches the finding is about
 * @param hitchNumber the hitch it stalled, or 0 when it is a repeat
 * @param count       the hitches it caused: 1 for a stall
 */
record KeyStutter(ModKey key, double worstMs, int hitchNumber, int count) {

    /** "{subject} took 370 ms in hitch #1." or "{subject} was a top cost in 4 of 4 hitches, up to 24 ms." */
    String finding(String subject, int hitches) {
        return hitchNumber > 0
                ? String.format(Locale.ROOT, "%s took %.0f ms in hitch #%d.", subject, worstMs, hitchNumber)
                : String.format(Locale.ROOT, "%s was a top cost in %d of %d hitches, up to %.0f ms.",
                        subject, count, hitches, worstMs);
    }

    /**
     * The stutter one of the keys {@code which} accepts caused, or null. A stall: the key took at least
     * {@link Thresholds#STALL_MS} and {@link Thresholds#STALL_SHARE} of one hitch, and the largest wins. A repeat:
     * the key caused hitches by {@code hitch-repeat}'s test, and the one in the most wins, the first found on a
     * tie. The stall comes first unless the repeat's worst reached it. A contributor with the {@code [gc]} mark
     * counts toward neither. These forms fire whatever the steady cost, because a stall averages to little over a
     * session, the Mods tab names this time so {@code unattributed-stall} skips it, and {@code hitch-repeat} leaves
     * Mods tab keys out (plan 5b, deviations 2, 3 and 6).
     */
    static KeyStutter find(Facts f, Predicate<ModKey> which) {
        KeyStutter stall = null;
        LinkedHashMap<ModKey, Integer> repeats = new LinkedHashMap<>();
        LinkedHashMap<ModKey, Double> repeatWorst = new LinkedHashMap<>();
        for (Facts.Hitch h : f.hitches()) {
            Set<ModKey> seen = new HashSet<>();
            for (Facts.Top t : h.top()) {
                if (!(t.key() instanceof ModKey k) || !which.test(k) || t.gc()) continue;
                if (t.ms() >= Thresholds.STALL_MS && t.ms() >= Thresholds.STALL_SHARE * h.totalMs()
                        && (stall == null || t.ms() > stall.worstMs())) {
                    stall = new KeyStutter(k, t.ms(), h.number(), 1);
                }
                if (Thresholds.causes(t, h) && seen.add(k)) {
                    repeats.merge(k, 1, Integer::sum);
                    repeatWorst.merge(k, t.ms(), Math::max);
                }
            }
        }
        KeyStutter repeat = null;
        for (Map.Entry<ModKey, Integer> e : repeats.entrySet()) {
            int n = e.getValue();
            if (n >= Thresholds.REPEATED_HITCHES && n >= Thresholds.HITCH_REPEAT_SHARE * f.hitches().size()
                    && (repeat == null || n > repeat.count())) {
                repeat = new KeyStutter(e.getKey(), repeatWorst.get(e.getKey()), 0, n);
            }
        }
        // A repeat as bad as the stall says more: how often, as well as how much.
        return stall == null || repeat != null && repeat.worstMs() >= stall.worstMs() ? repeat : stall;
    }
}
