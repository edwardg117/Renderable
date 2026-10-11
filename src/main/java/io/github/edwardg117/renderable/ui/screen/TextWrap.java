package io.github.edwardg117.renderable.ui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/**
 * Word wrapping for the Advice tab and for the Hitches tab's splits, measured by the caller's font so it can be
 * tested without one.
 */
final class TextWrap {
    /** ASCII whitespace only: a non-breaking space stays inside its word. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** What a split line puts between its parts. */
    private static final String SEPARATOR = " | ";

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

    /**
     * Wraps a split line at its {@code " | "} separators: as many whole parts as fit go on a line, and each
     * continuation line starts with {@code "| "}, so the parts read as before. A part wider than {@code width} on
     * its own is broken after its commas first, so a name keeps its figure, and a piece still too wide is then
     * word-wrapped by {@link #wrap}. Whitespace within a part collapses as {@code wrap} collapses it, so text with no
     * separator and no comma comes out as {@code wrap} gives it.
     */
    static List<String> wrapSplit(String text, int width, ToIntFunction<String> measure) {
        List<String> lines = new ArrayList<>();
        String line = null;   // the line being filled; null when the next part starts a fresh one
        int from = 0;
        while (from <= text.length()) {
            int at = text.indexOf(SEPARATOR, from);
            int end = at < 0 ? text.length() : at;
            String part = collapse(text.substring(from, end));
            from = at < 0 ? text.length() + 1 : at + SEPARATOR.length();
            if (part.isEmpty()) continue;
            if (line != null) {
                String joined = line + SEPARATOR + part;
                if (measure.applyAsInt(joined) <= width) {
                    line = joined;
                    continue;
                }
                lines.add(line);
                line = null;
            }
            String start = lines.isEmpty() ? part : "| " + part;
            if (measure.applyAsInt(start) <= width) {
                line = start;
            } else {
                lines.addAll(wrapAtCommas(start, width, measure));
            }
        }
        if (line != null) lines.add(line);
        return lines;
    }

    /**
     * A part too wide for a line, broken after its commas so a name keeps its figure; a piece still too wide is
     * word-wrapped.
     */
    private static List<String> wrapAtCommas(String part, int width, ToIntFunction<String> measure) {
        List<String> lines = new ArrayList<>();
        String line = null;
        String[] pieces = part.split(", ", -1);
        for (int i = 0; i < pieces.length; i++) {
            String piece = i < pieces.length - 1 ? pieces[i] + "," : pieces[i];
            if (line != null && measure.applyAsInt(line + " " + piece) <= width) {
                line = line + " " + piece;
                continue;
            }
            if (line != null) lines.add(line);
            line = null;
            if (measure.applyAsInt(piece) <= width) line = piece;
            else lines.addAll(wrap(piece, width, measure));
        }
        if (line != null) lines.add(line);
        return lines;
    }

    /** The words of {@code text} joined by single spaces, as {@link #wrap} would put them on a line. */
    private static String collapse(String text) {
        StringBuilder sb = new StringBuilder();
        for (String word : WHITESPACE.split(text)) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(word);
        }
        return sb.toString();
    }
}
