package io.hestia.tv.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** The clock and the date as shown on screen. Plain Java: tested on a JVM (tests/java). */
public final class TimeText {
    private TimeText() {}

    /** 24-hour clock: always in French and Portuguese; in English when the TV is set to it. */
    public static volatile boolean h24 = true;

    /** "21:47" (in English with a 12-hour TV, "9:47"). */
    public static String clock(long now) {
        return I18n.LANG_EN.equals(I18n.lang) && !h24 ? new SimpleDateFormat("h:mm", Locale.US).format(new Date(now))
                : new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(now));
    }

    /** "jeudi 22 octobre", "domingo, 18 de outubro", "Sunday, October 18". */
    public static String date(long now) {
        if (I18n.LANG_FR.equals(I18n.lang)) return new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(new Date(now));
        if (I18n.LANG_PT.equals(I18n.lang)) return new SimpleDateFormat("EEEE, d 'de' MMMM", I18n.PT).format(new Date(now));
        return new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(new Date(now));
    }

    /** The date with a capital first letter, for the start of a line. */
    public static String dateLine(long now) {
        return capitalized(date(now));
    }

    public static String capitalized(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** "3:07" (a track's position). */
    public static String minutes(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }
}
