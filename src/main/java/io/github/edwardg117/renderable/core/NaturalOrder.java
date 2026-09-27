package io.github.edwardg117.renderable.core;

import java.util.Comparator;

/**
 * The order the results screen sorts names in (spec addendum §P5.D5). Case is ignored, and a run of digits
 * compares as a number, so {@code section -10 4 0} comes before {@code section -2 4 0}. A minus sign is a
 * number's sign only at the start of a name or after a space, so {@code mod-2} still comes before
 * {@code mod-10}. Names that differ only in case, or only in a number's leading zeros, compare equal: the caller
 * breaks the tie.
 */
public final class NaturalOrder implements Comparator<String> {
    public static final NaturalOrder INSTANCE = new NaturalOrder();

    private NaturalOrder() {
    }

    @Override
    public int compare(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            int na = numberStart(a, i);
            int nb = numberStart(b, j);
            if (na >= 0 && nb >= 0) {
                int ea = digitsEnd(a, na);
                int eb = digitsEnd(b, nb);
                int c = compareNumbers(a, na, ea, na > i, b, nb, eb, nb > j);
                if (c != 0) return c;
                i = ea;
                j = eb;
            } else {
                int c = Character.compare(Character.toLowerCase(a.charAt(i)), Character.toLowerCase(b.charAt(j)));
                if (c != 0) return c;
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    /** Where the digits of a number starting at {@code k} begin, past its sign, or -1 when none starts there. */
    private static int numberStart(String s, int k) {
        char c = s.charAt(k);
        if (isDigit(c)) return k;
        boolean sign = c == '-' && (k == 0 || s.charAt(k - 1) == ' ');
        return sign && k + 1 < s.length() && isDigit(s.charAt(k + 1)) ? k + 1 : -1;
    }

    private static int digitsEnd(String s, int k) {
        while (k < s.length() && isDigit(s.charAt(k))) k++;
        return k;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * Compares two digit runs as signed numbers of any length, without parsing them. A minus sign counts even on a
     * zero: a negative number meets a non-number by its {@code -}, and a zero by its {@code 0}, so treating
     * {@code -0} as {@code 0} would make the order intransitive around {@code .} and {@code /}, and a long sort
     * could throw.
     */
    private static int compareNumbers(String a, int sa, int ea, boolean negA, String b, int sb, int eb, boolean negB) {
        while (sa < ea - 1 && a.charAt(sa) == '0') sa++;
        while (sb < eb - 1 && b.charAt(sb) == '0') sb++;
        if (negA != negB) return negA ? -1 : 1;
        int magnitude = Integer.compare(ea - sa, eb - sb);
        for (int k = 0; magnitude == 0 && k < ea - sa; k++) {
            magnitude = Character.compare(a.charAt(sa + k), b.charAt(sb + k));
        }
        return negA ? -magnitude : magnitude;
    }
}
