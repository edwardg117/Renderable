package io.github.edwardg117.renderable.ui;

/** ARGB colours for labels. Share of frame time maps green (under 1%) through yellow (4%) to red (10% and up). */
public final class Colors {
    public static final int GREEN = 0xFF40FF40;
    public static final int YELLOW = 0xFFFFFF40;
    public static final int RED = 0xFFFF4040;
    public static final int FLAGGED = RED;

    private Colors() {
    }

    public static int heat(double sharePercent) {
        if (sharePercent <= 1.0) return GREEN;
        if (sharePercent <= 4.0) return lerp(GREEN, YELLOW, (sharePercent - 1.0) / 3.0);
        if (sharePercent <= 10.0) return lerp(YELLOW, RED, (sharePercent - 4.0) / 6.0);
        return RED;
    }

    static int lerp(int a, int b, double t) {
        int r = channel(a >> 16, b >> 16, t);
        int g = channel(a >> 8, b >> 8, t);
        int bl = channel(a, b, t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int channel(int a, int b, double t) {
        int ca = a & 0xFF;
        int cb = b & 0xFF;
        return (int) Math.round(ca + (cb - ca) * t);
    }
}
