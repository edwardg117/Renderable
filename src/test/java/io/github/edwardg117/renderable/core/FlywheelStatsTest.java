package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.junit.jupiter.api.Assertions.*;

class FlywheelStatsTest {

    @Test
    void byAverageDescOrdersByCountThenName() {
        FlywheelStats fw = new FlywheelStats(3, 120, 10, 0, Map.of("b", 1.0, "a", 2.0, "c", 2.0));
        List<Entry<String, Double>> ordered = fw.byAverageDesc();
        List<String> keys = ordered.stream().map(Entry::getKey).toList();
        assertEquals(List.of("a", "c", "b"), keys);

        assertTrue(FlywheelStats.none().byAverageDesc().isEmpty());
    }
}
