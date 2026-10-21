package io.freemytv;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Plain-Java checks on what comes from the RSS feeds (no Android types: tested on a JVM, tests/java). */
final class FeedText {
    private FeedText() {}

    static final int TITLE_MAX = 220;

    /** One line of plain text: no tags, entities or runs of spaces; bounded length. */
    static String clean(String s) {
        if (s == null) return "";
        s = s.replaceAll("<[^>]*>", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'")
                .replace("&#8217;", "’").replace("&#8216;", "‘").replace("&#8220;", "“").replace("&#8221;", "”")
                .replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
        return s.length() > TITLE_MAX ? s.substring(0, TITLE_MAX - 1) + "…" : s;
    }

    /** The link only if it's a plain https URL (no credentials, bounded length); otherwise "". */
    static String safeLink(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() > 600 || !s.startsWith("https://") || s.matches(".*[\\s\"<>].*")) return "";
        try {
            URL url = new URL(s);
            if (url.getUserInfo() != null || url.getHost().isEmpty()) return "";
            return s;
        } catch (MalformedURLException e) {
            return "";
        }
    }

    /** "bleepingcomputer.com" (shown under the list so you know where a link goes). */
    static String domain(String link) {
        try {
            String host = new URL(link).getHost();
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (MalformedURLException e) {
            return "";
        }
    }

    static long parseDate(String s) {
        if (s == null) return 0;
        s = s.trim();
        for (String pattern : new String[] {"EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z",
                "EEE, dd MMM yyyy HH:mm:ss zzz", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"}) {
            SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
            ParsePosition pos = new ParsePosition(0);
            Date d = f.parse(s, pos);
            if (d != null && pos.getIndex() > 0) return d.getTime();
        }
        return 0;
    }
}
