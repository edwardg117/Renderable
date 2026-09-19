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
}
