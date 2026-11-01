package io.hestia.tv.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Xml;

import io.hestia.tv.core.AppLog;
import io.hestia.tv.core.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Cybersecurity headlines from a few RSS feeds (no key), the last 24 h (48 h when that's too few), newest first.
 * Refreshed every 30 min while the home screen is visible; cached so it opens with headlines. Only titles, sources,
 * dates and the article's https link are kept; nothing from the feeds is rendered as HTML. Links open only when chosen
 * in the news list (NewsActivity), in the TV's browser.
 */
public final class News extends Feed {
    public static final class Headline {
        public final String title, source, link;   // link: https URL or ""
        public final long at;

        Headline(String title, String source, String link, long at) {
            this.title = title;
            this.source = source;
            this.link = link;
            this.at = at;
        }
    }

    /** Source name, feed URL. Each host must be listed in Net. */
    private static final String[][] FEEDS = {
        {"The Hacker News", "https://feeds.feedburner.com/TheHackersNews"},
        {"BleepingComputer", "https://www.bleepingcomputer.com/feed/"},
        {"SecurityWeek", "https://www.securityweek.com/feed/"},
        {"CERT-FR", "https://cert.cyber.gouv.fr/feed/"},
        {"ZATAZ", "https://www.zataz.com/feed/"},
    };
    private static final long DAY = 24 * 3600_000L;
    private static final int MIN_ITEMS = 6, MAX_ITEMS = 40;

    private static News instance;

    public static synchronized News get(Context c) {
        if (instance == null) instance = new News(c.getApplicationContext());
        return instance;
    }

    private final SharedPreferences cache;
    private volatile List<Headline> all = new ArrayList<>();

    private News(Context c) {
        super("news", 30 * 60_000L);
        cache = Prefs.cache(c, "news");
        fetchedAt = cache.getLong("at", 0);
        try {
            all = parseCache(new JSONArray(cache.getString("items", "[]")));
        } catch (org.json.JSONException ignored) {
        }
    }

    /** The recent headlines, newest first. */
    public List<Headline> recent() {
        long now = System.currentTimeMillis();
        List<Headline> out = new ArrayList<>();
        for (long window : new long[] {DAY, 2 * DAY}) {
            out.clear();
            for (Headline h : all) if (now - h.at <= window && h.at <= now + 3600_000L) out.add(h);
            if (out.size() >= MIN_ITEMS) break;
        }
        return out;
    }

    @Override protected void fetch() throws java.io.IOException {
        List<Headline> found = new ArrayList<>();
        for (String[] feed : FEEDS) {
            try {
                found.addAll(parseFeed(Net.feed(feed[1]), feed[0]));
            } catch (Exception e) {
                AppLog.w("news " + feed[0] + ": " + Net.safeError(e));
            }
        }
        if (found.isEmpty()) throw new java.io.IOException("no feed answered");
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
            for (Headline h : unique) {
                cache.put(new JSONObject().put("t", h.title).put("s", h.source).put("l", h.link).put("at", h.at));
            }
        } catch (org.json.JSONException ignored) {
        }
        this.cache.edit().putString("items", cache.toString()).putLong("at", fetchedAt).apply();
        notifyListeners();
    }

    /** RSS 2.0 items (title + pubDate); Atom entries (title + updated/published) as a fallback. */
    private static List<Headline> parseFeed(String xml, String source) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        p.setInput(new StringReader(xml));
        List<Headline> out = new ArrayList<>();
        boolean inItem = false;
        String title = null, date = null, link = null;
        for (int event = p.getEventType(); event != XmlPullParser.END_DOCUMENT; event = p.next()) {
            if (event == XmlPullParser.START_TAG) {
                String name = p.getName();
                if (name.equals("item") || name.equals("entry")) {
                    inItem = true;
                    title = date = link = null;
                } else if (inItem && name.equals("title")) {
                    title = p.nextText();
                } else if (inItem && name.equals("link") && link == null) {
                    String href = p.getAttributeValue(null, "href");   // Atom
                    String rel = p.getAttributeValue(null, "rel");
                    if (href != null) {
                        if (rel == null || rel.equals("alternate")) link = href;
                    } else {
                        link = p.nextText();                           // RSS
                    }
                } else if (inItem && (name.equals("pubDate") || name.equals("updated") || name.equals("published"))) {
                    if (date == null) date = p.nextText();
                }
            } else if (event == XmlPullParser.END_TAG && (p.getName().equals("item") || p.getName().equals("entry"))) {
                inItem = false;
                long at = FeedText.parseDate(date);
                String clean = FeedText.clean(title);
                if (at > 0 && !clean.isEmpty()) out.add(new Headline(clean, source, FeedText.safeLink(link), at));
            }
        }
        return out;
    }

    private static List<Headline> parseCache(JSONArray list) {
        List<Headline> out = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o != null) {
                String link = FeedText.safeLink(o.optString("l"));
                out.add(new Headline(FeedText.clean(o.optString("t")), o.optString("s"), link, o.optLong("at")));
            }
        }
        return out;
    }

    /** "2 h", "35 min" (how long ago). */
    public static String age(long at) {
        long minutes = Math.max(0, (System.currentTimeMillis() - at) / 60_000);
        return minutes < 60 ? minutes + " min" : (minutes / 60) + " h";
    }
}
