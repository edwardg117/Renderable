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
}
