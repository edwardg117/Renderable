package io.github.edwardg117.renderable.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorsTest {

    @Test
    void heatScaleEndpointsAndMidpoint() {
        assertEquals(0xFF40FF40, Colors.heat(0.0));
        assertEquals(0xFF40FF40, Colors.heat(1.0));
        assertEquals(0xFFA0FF40, Colors.heat(2.5), "halfway from green to yellow");
        assertEquals(0xFFFFFF40, Colors.heat(4.0));
        assertEquals(0xFFFF4040, Colors.heat(10.0));
        assertEquals(0xFFFF4040, Colors.heat(55.0));
    }
}
