package com.oru.app.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * "just now", "4m", "3h", "2d" — the way the web labels a drop's age.
 *
 * The API writes timestamps in UTC with or without a zone suffix and with or
 * without fractional seconds, so the parser accepts those shapes instead of
 * assuming one. An unreadable date returns an empty label rather than "1970".
 */
public final class RelativeTime {

    private static final String[] PATTERNS = {
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
    };

    private RelativeTime() {
    }

    public static String of(String iso) {
        Date when = parse(iso);
        if (when == null) {
            return "";
        }
        long seconds = Math.max(0, (System.currentTimeMillis() - when.getTime()) / 1000);
        if (seconds < 60) {
            return "just now";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h";
        }
        long days = hours / 24;
        if (days < 7) {
            return days + "d";
        }
        long weeks = days / 7;
        if (weeks < 5) {
            return weeks + "w";
        }
        return new SimpleDateFormat("d MMM", Locale.getDefault()).format(when);
    }

    private static Date parse(String iso) {
        if (iso == null || iso.isEmpty()) {
            return null;
        }
        String cleaned = iso.trim()
                .replace("Z", "+0000")
                .replaceAll("\\.\\d+", "")
                .replaceAll("([+-]\\d{2}):(\\d{2})$", "$1$2");
        for (String pattern : PATTERNS) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            try {
                return format.parse(cleaned);
            } catch (ParseException ignored) {
                // Try the next shape.
            }
        }
        return null;
    }
}
