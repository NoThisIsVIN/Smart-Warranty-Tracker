package org.example.ui;

import org.example.model.Warranty;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Display formatting shared by all views. */
public final class Formats {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final String DASH = "—";

    private Formats() {}

    /** "Mar 5, 2024", or "—" when {@code d} is null. */
    public static String date(LocalDate d) {
        return d == null ? DASH : DATE.format(d);
    }

    /** "214 days left", "1 day left", "Expires today", "Expired 3 days ago" or "No purchase date". */
    public static String daysLeft(Warranty w, LocalDate today) {
        if (w.getExpiryDate() == null) return "No purchase date";
        long days = w.daysLeft(today);
        if (days == 0) return "Expires today";
        if (days > 0) return plural(days, "day") + " left";
        return "Expired " + plural(-days, "day") + " ago";
    }

    /** "1 year", "2 years", "18 months", "1 month". */
    public static String duration(int months) {
        if (months > 0 && months % 12 == 0) return plural(months / 12, "year");
        return plural(months, "month");
    }

    public static String orDash(String s) {
        return s == null || s.isBlank() ? DASH : s.trim();
    }

    private static String plural(long count, String noun) {
        return count + " " + (count == 1 ? noun : noun + "s");
    }
}
