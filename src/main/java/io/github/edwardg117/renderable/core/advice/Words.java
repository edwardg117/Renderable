package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Fmt;

import java.util.Locale;

/** Small wording helpers shared by the rules. */
final class Words {

    private Words() {
    }

    /** MiB as GiB: whole numbers without a decimal ("8"), others with one ("6.9"). */
    static String gib(double mib) {
        double g = mib / 1024;
        return Math.abs(g - Math.rint(g)) < 0.05
                ? String.format(Locale.ROOT, "%.0f", g)
                : String.format(Locale.ROOT, "%.1f", g);
    }

    /** A biome blend radius as the Video Settings screen shows it: 0 is "off", 2 is "5x5". */
    static String blend(int radius) {
        if (radius <= 0) return "off";
        int side = 2 * radius + 1;
        return side + "x" + side;
    }

    /** The major version from the environment's java line ("21.0.3 (Eclipse Adoptium)" is 21), or -1. */
    static int javaMajor(String version) {
        if (version == null) return -1;
        int end = 0;
        while (end < version.length() && Character.isDigit(version.charAt(end))) end++;
        return end == 0 ? -1 : Integer.parseInt(version.substring(0, end));
    }

    /** "1 block" or "212 blocks". */
    static String count(int n, String one, String many) {
        return Fmt.count(n) + " " + (n == 1 ? one : many);
    }
}
