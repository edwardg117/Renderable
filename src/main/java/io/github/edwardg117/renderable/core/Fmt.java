package io.github.edwardg117.renderable.core;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Number and time formatting shared by reports and UI. All output uses Locale.ROOT. */
public final class Fmt {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private Fmt() {
    }

    /** Nanoseconds as milliseconds with one decimal. */
    public static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1e6);
    }

    /** Nanoseconds as milliseconds with two decimals. */
    public static String ms2(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1e6);
    }

    /** Microseconds with one decimal. */
    public static String us(double micros) {
        return String.format(Locale.ROOT, "%.1f", micros);
    }

    /** Nanoseconds as seconds with one decimal. */
    public static String secs(long nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1e9);
    }

    public static String pct(double percent) {
        return String.format(Locale.ROOT, "%.1f%%", percent);
    }

    public static String count(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    public static String timestamp(long epochMillis) {
        return TIMESTAMP.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()));
    }

    public static String fileStamp(long epochMillis) {
        return FILE_STAMP.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()));
    }
}
