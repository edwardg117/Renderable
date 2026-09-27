package io.github.edwardg117.renderable.core;

import java.util.List;
import java.util.Map;

/**
 * Flywheel visual counts averaged over the once-per-second samples taken while sampling (spec §4.2).
 * {@code samples} is 0 when Flywheel was absent or never sampled; every average is then 0.
 */
public record FlywheelStats(int samples,
                            double avgBlockEntities,
                            double avgEntities,
                            double avgEffects,
                            Map<String, Double> avgByClass) {

    private static final FlywheelStats NONE = new FlywheelStats(0, 0, 0, 0, Map.of());

    public static FlywheelStats none() {
        return NONE;
    }

    /** Average number of visuals of any kind. */
    public double total() {
        return avgBlockEntities + avgEntities + avgEffects;
    }

    /** Visual classes ordered by average count, highest first, then by name; the one order every report and screen uses. */
    public List<Map.Entry<String, Double>> byAverageDesc() {
        return avgByClass.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .toList();
    }

    /**
     * Display names for the visual classes, in {@link #byAverageDesc()} order: the simple class name, or the
     * last package segment plus the simple name when two classes in this session would otherwise read the
     * same, or the full name when that still leaves two alike (spec addendum §P4.C6). The screen and the
     * text report both use these; the JSON keeps the fully-qualified names, which are the machine record.
     * The rule is {@link SimpleNames}'s, which the particle tables share.
     */
    public Map<String, String> displayNames() {
        return SimpleNames.of(byAverageDesc().stream().map(Map.Entry::getKey).toList());
    }
}
