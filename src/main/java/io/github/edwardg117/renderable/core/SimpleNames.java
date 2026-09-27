package io.github.edwardg117.renderable.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Short names for fully-qualified class names that stay unambiguous within one table (spec addendum §P4.C6 and
 * §P5.D4): the simple name; the last package segment plus the simple name when two classes in the table share a
 * simple name; the full name when that still leaves two alike. The screen and the text report show these; the
 * JSON keeps the full names, which are the machine record.
 */
public final class SimpleNames {

    private SimpleNames() {
    }

    /**
     * Each of {@code names} mapped to its short name, in the order given. The names must be distinct: a repeated
     * one collides with itself and keeps its full name.
     */
    public static Map<String, String> of(Collection<String> names) {
        Map<String, Integer> bySimple = new HashMap<>();
        Map<String, Integer> byQualified = new HashMap<>();
        for (String fqn : names) {
            bySimple.merge(simpleName(fqn), 1, Integer::sum);
            byQualified.merge(qualifiedName(fqn), 1, Integer::sum);
        }
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (String fqn : names) {
            String simple = simpleName(fqn);
            String qualified = qualifiedName(fqn);
            // One package segment tells most collisions apart; when it does not, only the full name can.
            out.put(fqn, bySimple.get(simple) == 1 ? simple : byQualified.get(qualified) == 1 ? qualified : fqn);
        }
        return out;
    }

    private static String simpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    /** The last package segment plus the simple name, such as {@code press.PressVisual}. */
    private static String qualifiedName(String fqn) {
        int last = fqn.lastIndexOf('.');
        if (last < 0) return fqn;
        int previous = fqn.lastIndexOf('.', last - 1);
        return previous < 0 ? fqn : fqn.substring(previous + 1);
    }
}
