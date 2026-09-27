package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NaturalOrderTest {

    static List<String> sorted(String... names) {
        List<String> out = new ArrayList<>(List.of(names));
        out.sort(NaturalOrder.INSTANCE);
        return out;
    }

    @Test
    void digitRunsCompareAsNumbersWithTheirSign() {
        assertEquals(List.of("section -10 4 0", "section -2 4 0", "section -1 4 0", "section 0 4 0", "section 2 4 0",
                        "section 10 4 0"),
                sorted("section 2 4 0", "section -1 4 0", "section 10 4 0", "section -10 4 0", "section 0 4 0",
                        "section -2 4 0"));
        assertEquals(List.of("create:press @ 5 64 -20", "create:press @ 40 64 -20", "create:press @ 100 64 -20"),
                sorted("create:press @ 100 64 -20", "create:press @ 5 64 -20", "create:press @ 40 64 -20"));
        assertEquals(List.of("mod-2", "mod-10"), sorted("mod-10", "mod-2"), "a hyphen inside a word is not a sign");
        assertEquals(List.of("-10", "-2", "1"), sorted("1", "-2", "-10"),
                "a minus sign at the start of a name is a sign too");
        assertEquals(List.of("x 99999999999999999999", "x 100000000000000000000"),
                sorted("x 100000000000000000000", "x 99999999999999999999"), "longer than a long");
        assertEquals(0, NaturalOrder.INSTANCE.compare("a 007", "a 7"), "leading zeros do not count");
        assertTrue(NaturalOrder.INSTANCE.compare("a -0", "a 0") < 0,
                "a signed zero sorts with the negatives, which keeps the order transitive");
    }

    @Test
    void caseIsIgnoredAndATieIsLeftToTheCaller() {
        assertEquals(List.of("apple", "Cow", "cow", "Zombie"), sorted("Zombie", "Cow", "apple", "cow"),
                "Cow and cow compare equal, so the stable sort keeps their given order");
        assertEquals(0, NaturalOrder.INSTANCE.compare("Cow", "cow"));
        assertTrue(NaturalOrder.INSTANCE.compare("cow", "cows") < 0, "a prefix comes first");
        assertEquals(List.of("minecraft:red_sand", "minecraft:redstone"),
                sorted("minecraft:redstone", "minecraft:red_sand"),
                "folding to lower case keeps a lower-case id where plain order put it");
    }
}
