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

    @Test
    void displayNamesAreSimpleUnlessTwoClassesWouldCollide() {
        FlywheelStats plain = new FlywheelStats(1, 0, 0, 0, Map.of(
                "com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual", 4.0,
                "com.simibubi.create.content.kinetics.belt.BeltVisual", 2.0));
        assertEquals("SingleAxisRotatingVisual",
                plain.displayNames().get("com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual"));
        assertEquals("BeltVisual", plain.displayNames().get("com.simibubi.create.content.kinetics.belt.BeltVisual"));

        FlywheelStats clash = new FlywheelStats(1, 0, 0, 0, Map.of(
                "com.simibubi.create.content.kinetics.press.PressVisual", 4.0,
                "com.example.other.mixer.PressVisual", 2.0));
        Map<String, String> names = clash.displayNames();
        assertEquals("press.PressVisual", names.get("com.simibubi.create.content.kinetics.press.PressVisual"));
        assertEquals("mixer.PressVisual", names.get("com.example.other.mixer.PressVisual"));

        assertEquals("NoPackage", new FlywheelStats(1, 0, 0, 0, Map.of("NoPackage", 1.0))
                .displayNames().get("NoPackage"), "a class with no package keeps its name");
        assertTrue(FlywheelStats.none().displayNames().isEmpty());

        Map<String, String> edges = new FlywheelStats(1, 0, 0, 0, Map.of(
                "PressVisual", 3.0, "x.PressVisual", 2.0, "com.example.y.PressVisual", 1.0)).displayNames();
        assertEquals("PressVisual", edges.get("PressVisual"), "a colliding class with no package has no segment to add");
        assertEquals("x.PressVisual", edges.get("x.PressVisual"), "a one-segment package is already its qualified name");
        assertEquals("y.PressVisual", edges.get("com.example.y.PressVisual"));

        Map<String, String> deep = new FlywheelStats(1, 0, 0, 0, Map.of(
                "a.x.PressVisual", 2.0, "b.x.PressVisual", 1.0, "c.z.PressVisual", 0.5)).displayNames();
        assertEquals("a.x.PressVisual", deep.get("a.x.PressVisual"), "one segment is not enough, so the full name");
        assertEquals("b.x.PressVisual", deep.get("b.x.PressVisual"));
        assertEquals("z.PressVisual", deep.get("c.z.PressVisual"), "its segment is unique, so one is enough");
    }
}
