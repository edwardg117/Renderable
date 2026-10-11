package io.github.edwardg117.renderable.ui.screen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextWrapTest {

    @Test
    void wrapsAtWordsAndGivesAnOverlongWordALineOfItsOwn() {
        assertEquals(List.of("the quick", "brown fox"), TextWrap.wrap("the quick brown fox", 9, String::length));
        assertEquals(List.of("a", "extraordinarily", "b"), TextWrap.wrap("a extraordinarily b", 5, String::length));
        assertEquals(List.of(), TextWrap.wrap("", 10, String::length));
        assertEquals(List.of("one two"), TextWrap.wrap("one  two", 20, String::length), "runs of spaces collapse");
        assertEquals(List.of("a b", "c"), TextWrap.wrap(" a\tb\nc ", 3, String::length),
                "a tab or line break breaks like a space, and none leads or trails");
        assertEquals(List.of("a\u00a0b", "c"), TextWrap.wrap("a\u00a0b c", 3, String::length), "a non-breaking space does not");
        assertEquals(List.of("a", "b"), TextWrap.wrap("a b", 0, String::length), "no room: a word a line");
    }

    @Test
    void aSplitThatFitsStaysOnOneLine() {
        assertEquals(List.of("tick 1.0 | lod 2.0"), TextWrap.wrapSplit("tick 1.0 | lod 2.0", 40, String::length));
        assertEquals(List.of("tick 1.0 | lod 2.0"), TextWrap.wrapSplit("tick 1.0 | lod 2.0", 18, String::length),
                "a line exactly as wide as the width fits");
        assertEquals(List.of(), TextWrap.wrapSplit("", 40, String::length));
    }

    @Test
    void aSplitBreaksAtItsSeparatorsAndEachContinuationStartsWithABar() {
        String split = "tick 1.0 [a 0.5, b 0.5] | terrain 0.2 | lod 5.4 | swap 0.6";
        assertEquals(List.of("tick 1.0 [a 0.5, b 0.5]", "| terrain 0.2 | lod 5.4", "| swap 0.6"),
                TextWrap.wrapSplit(split, 30, String::length),
                "line one is 23 wide and a second part would make it 37; line two takes two parts and a third would make 34");
        assertEquals(List.of("alpha 1", "| beta 2", "| gamma 3"),
                TextWrap.wrapSplit("alpha 1 | beta 2 | gamma 3", 10, String::length), "no two parts fit together: a part a line");
    }

    @Test
    void aPartWiderThanTheLineIsWordWrappedWithoutLosingAWord() {
        String split = "tick 1.0 | mods a b c d e f g h i j k l m | swap 0.6";
        List<String> lines = TextWrap.wrapSplit(split, 20, String::length);
        assertEquals(List.of("tick 1.0", "| mods a b c d e f g", "h i j k l m", "| swap 0.6"), lines,
                "only the first line of the broken part carries the bar, as wrap keeps it as that line's first word");
        assertEquals("| mods a b c d e f g h i j k l m", lines.get(1) + " " + lines.get(2), "no word lost");
        for (String line : lines) assertTrue(line.length() <= 20, line);

        // The first part is the wide one: its lines come first, and the next part starts a fresh line after them.
        // It breaks after its comma first; "tick 1.0 [a 0.5," is still too wide, so that piece is word-wrapped.
        assertEquals(List.of("tick 1.0 [a", "0.5,", "b 0.5]", "| terrain 0.2", "| lod 5.4", "| swap 0.6"),
                TextWrap.wrapSplit("tick 1.0 [a 0.5, b 0.5] | terrain 0.2 | lod 5.4 | swap 0.6", 14, String::length));
    }

    @Test
    void anOverWidePartBreaksAfterItsCommasSoANameKeepsItsFigure() {
        String split = "tick 9.0 [particle tick 0.6, be tick 2.9, entity tick 2.3, mods 1.7, unnamed 2.5] | lod 8.6";
        List<String> lines = TextWrap.wrapSplit(split, 40, String::length);
        assertEquals(List.of("tick 9.0 [particle tick 0.6,", "be tick 2.9, entity tick 2.3, mods 1.7,", "unnamed 2.5]",
                "| lod 8.6"), lines, "the bracket breaks only after a comma, so every line starts with a name");
        for (String line : lines) {
            assertTrue(line.length() <= 40, line);
            assertFalse(Character.isDigit(line.charAt(0)), "a line never starts with a bare figure: " + line);
        }
        assertEquals("tick 9.0 [particle tick 0.6, be tick 2.9, entity tick 2.3, mods 1.7, unnamed 2.5]",
                String.join(" ", lines.subList(0, 3)), "no word lost");
    }

    @Test
    void textWithNoSeparatorWrapsAsWrapDoes() {
        for (String text : List.of("the quick brown fox jumps", "one  two", " a\tb\nc ", "extraordinarily", "   ")) {
            for (int width : new int[]{0, 3, 9, 40}) {
                assertEquals(TextWrap.wrap(text, width, String::length), TextWrap.wrapSplit(text, width, String::length),
                        "'" + text + "' at " + width);
            }
        }
    }
}
