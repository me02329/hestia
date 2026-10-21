package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cybersecurity headlines from a few RSS feeds (no key), the last 24 h (48 h when that's too few), newest first.
 * Refreshed every 30 min while the home screen is visible; cached so it opens with headlines. Only titles, sources
 * and dates are kept: nothing is opened or rendered from the feeds.
 */
final class News {
    static final class Headline {
        final String title, source;
        final long at;

        Headline(String title, String source, long at) {
            this.title = title;
            this.source = source;
            this.at = at;
        }
    }

    interface Listener { void onNews(); }

    /** Source name, feed URL. Each host must be listed in Net. */
    private static final String[][] FEEDS = {
        {"The Hacker News", "https://feeds.feedburner.com/TheHackersNews"},
        {"BleepingComputer", "https://www.bleepingcomputer.com/feed/"},
        {"SecurityWeek", "https://www.securityweek.com/feed/"},
        {"CERT-FR", "https://cert.cyber.gouv.fr/feed/"},
        {"ZATAZ", "https://www.zataz.com/feed/"},
    };
    private static final long EVERY = 30 * 60_000L, DAY = 24 * 3600_000L;
    private static final int MIN_ITEMS = 6, MAX_ITEMS = 40, TITLE_MAX = 220;

    private static News instance;

    static synchronized News get(Context c) {
        if (instance == null) instance = new News(c.getApplicationContext());
        return instance;
    }

    private final SharedPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private volatile List<Headline> all = new ArrayList<>();
    private volatile long fetchedAt;
    private volatile boolean fetching;

    private News(Context c) {
        prefs = c.getSharedPreferences("news", Context.MODE_PRIVATE);
        fetchedAt = prefs.getLong("at", 0);
        try {
            all = parseCache(new JSONArray(prefs.getString("items", "[]")));
        } catch (org.json.JSONException ignored) {
        }
    }

    static boolean enabled(Context c) {
        return c.getSharedPreferences("home", Context.MODE_PRIVATE).getBoolean("news", true);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("home", Context.MODE_PRIVATE).edit().putBoolean("news", on).apply();
    }

    void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    /** The recent headlines, newest first. */
    List<Headline> recent() {
        long now = System.currentTimeMillis();
        List<Headline> out = new ArrayList<>();
        for (long window : new long[] {DAY, 2 * DAY}) {
            out.clear();
            for (Headline h : all) if (now - h.at <= window && h.at <= now + 3600_000L) out.add(h);
            if (out.size() >= MIN_ITEMS) break;
        }
        return out;
    }

    void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (fetching || (now - fetchedAt < EVERY && now >= fetchedAt)) return;
        fetching = true;
        io.execute(() -> {
            try {
                fetch();
            } finally {
                fetching = false;
            }
        });
    }

    private void fetch() {
        List<Headline> found = new ArrayList<>();
        for (String[] feed : FEEDS) {
            try {
                found.addAll(parseFeed(Net.feed(feed[1]), feed[0]));
            } catch (Exception e) {
                AppLog.w("news " + feed[0] + ": " + Net.safeError(e));
            }
        }
        if (found.isEmpty()) return;
        Collections.sort(found, (a, b) -> Long.compare(b.at, a.at));
        List<Headline> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Headline h : found) {
            if (unique.size() >= MAX_ITEMS) break;
            if (seen.add(h.title.toLowerCase(Locale.ROOT))) unique.add(h);
        }
        all = unique;
        fetchedAt = System.currentTimeMillis();
        JSONArray cache = new JSONArray();
        try {
            for (Headline h : unique) cache.put(new JSONObject().put("t", h.title).put("s", h.source).put("at", h.at));
        } catch (org.json.JSONException ignored) {
        }
        prefs.edit().putString("items", cache.toString()).putLong("at", fetchedAt).apply();
        main.post(() -> { for (Listener l : new ArrayList<>(listeners)) l.onNews(); });
    }

    /** RSS 2.0 items (title + pubDate); Atom entries (title + updated/published) as a fallback. */
    static List<Headline> parseFeed(String xml, String source) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        p.setInput(new StringReader(xml));
        List<Headline> out = new ArrayList<>();
        boolean inItem = false;
        String title = null, date = null;
        for (int event = p.getEventType(); event != XmlPullParser.END_DOCUMENT; event = p.next()) {
            if (event == XmlPullParser.START_TAG) {
                String name = p.getName();
                if (name.equals("item") || name.equals("entry")) {
                    inItem = true;
                    title = date = null;
                } else if (inItem && name.equals("title")) {
                    title = p.nextText();
                } else if (inItem && (name.equals("pubDate") || name.equals("updated") || name.equals("published"))) {
                    if (date == null) date = p.nextText();
                }
            } else if (event == XmlPullParser.END_TAG && (p.getName().equals("item") || p.getName().equals("entry"))) {
                inItem = false;
                long at = parseDate(date);
                String clean = clean(title);
                if (at > 0 && !clean.isEmpty()) out.add(new Headline(clean, source, at));
            }
        }
        return out;
    }

    /** One line of plain text: no tags, entities or runs of spaces; bounded length. */
    static String clean(String s) {
        if (s == null) return "";
        s = s.replaceAll("<[^>]*>", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'")
                .replace("&#8217;", "’").replace("&#8216;", "‘").replace("&#8220;", "“").replace("&#8221;", "”")
                .replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
        return s.length() > TITLE_MAX ? s.substring(0, TITLE_MAX - 1) + "…" : s;
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

    private static List<Headline> parseCache(JSONArray list) {
        List<Headline> out = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o != null) out.add(new Headline(clean(o.optString("t")), o.optString("s"), o.optLong("at")));
        }
        return out;
    }

    /** "2 h", "35 min" (how long ago). */
    static String age(long at) {
        long minutes = Math.max(0, (System.currentTimeMillis() - at) / 60_000);
        return minutes < 60 ? minutes + " min" : (minutes / 60) + " h";
    }
}
