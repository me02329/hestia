package io.hestia.tv.data;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

/** A place from the city search. Plain Java: tested on a JVM (tests/java). */
public final class City {
    private static final int NAME_MAX = 80, QUERY_MIN = 2, QUERY_MAX = 80, RESULTS = 8;
    private static final String SEARCH = "https://geocoding-api.open-meteo.com/v1/search?format=json&count=" + RESULTS;

    public final String name, region;
    public final double latitude, longitude;

    private City(String name, String region, double latitude, double longitude) {
        this.name = name;
        this.region = region;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /** A checked city, or null: what the network says is only kept if it makes sense. */
    static City of(String name, String admin, String country, double latitude, double longitude) {
        String clean = trimmed(name);
        if (clean.isEmpty() || !(Math.abs(latitude) <= 90) || !(Math.abs(longitude) <= 180)) return null;
        String area = trimmed(admin), land = trimmed(country);
        String region = area.isEmpty() || area.equals(clean) ? land : land.isEmpty() ? area : area + ", " + land;
        return new City(clean, region, latitude, longitude);
    }

    private static String trimmed(String s) {
        String clean = FeedText.clean(s);
        return clean.length() > NAME_MAX ? clean.substring(0, NAME_MAX) : clean;
    }

    /** The search's address for what was typed, in the UI's language; null when there's nothing to look for. */
    static String searchUrl(String query, String language) {
        String q = query == null ? "" : query.trim();
        if (q.length() < QUERY_MIN || q.length() > QUERY_MAX) return null;
        try {
            return SEARCH + "&language=" + language + "&name=" + URLEncoder.encode(q, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    /** A coordinate as the forecast's address wants it, whatever the TV's language. */
    static String coordinate(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
