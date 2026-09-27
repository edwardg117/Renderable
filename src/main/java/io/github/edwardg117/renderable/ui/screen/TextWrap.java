package io.github.edwardg117.renderable.ui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/** Word wrapping for the Advice tab, measured by the caller's font so it can be tested without one. */
final class TextWrap {
    /** ASCII whitespace only: a non-breaking space stays inside its word. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TextWrap() {
    }

    /**
     * Greedy wrap into lines no wider than {@code width} by {@code measure}. A word wider than that gets a line
     * of its own, which the list then truncates; runs of whitespace, a tab or a line break among them, collapse
     * to one space.
     */
    static List<String> wrap(String text, int width, ToIntFunction<String> measure) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : WHITESPACE.split(text)) {
            if (word.isEmpty()) continue;
            if (line.isEmpty()) {
                line.append(word);
            } else if (measure.applyAsInt(line + " " + word) <= width) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
