package io.hestia.tv.data;

import android.content.Context;
import android.content.SharedPreferences;

import io.hestia.tv.core.Prefs;

import org.json.JSONObject;

import io.hestia.tv.core.Config;

/** The current weather for your location (Open-Meteo, every 30 min, no key). Location from config.properties. */
public final class Weather extends Feed {
    private static final String URL = "https://api.open-meteo.com/v1/forecast?latitude=" + Config.LATITUDE
            + "&longitude=" + Config.LONGITUDE
            + "&current=temperature_2m,weather_code,is_day&timezone=auto";

    public static final class Reading {
        public double temp = Double.NaN;
        public int code = -1;
        public boolean isDay = true;
    }

    private static Weather instance;

    public static synchronized Weather get(Context c) {
        if (instance == null) instance = new Weather(c.getApplicationContext());
        return instance;
    }

    private final SharedPreferences cache;
    private volatile Reading now = new Reading();

    private Weather(Context c) {
        super("weather", 30 * 60_000L);
        cache = Prefs.cache(c, "homedata");
        try {
            String saved = cache.getString("weather", null);
            if (saved != null) now = parse(saved);
            fetchedAt = cache.getLong("weatherAt", 0);
        } catch (Exception ignored) {
        }
    }

    public Reading now() {
        return now;
    }

    @Override protected void fetch() throws Exception {
        String body = Net.json(URL);
        now = parse(body);
        fetchedAt = System.currentTimeMillis();
        cache.edit().putString("weather", body).putLong("weatherAt", fetchedAt).apply();
        notifyListeners();
    }

    private static Reading parse(String body) throws Exception {
        JSONObject current = new JSONObject(body).getJSONObject("current");
        Reading r = new Reading();
        r.temp = current.getDouble("temperature_2m");
        r.code = current.getInt("weather_code");
        r.isDay = current.optInt("is_day", 1) == 1;
        return r;
    }

    /** "Paris · 12° Nuageux" for the panels; "" before the first reading. */
    public String summary() {
        Reading r = now;
        if (Double.isNaN(r.temp)) return "";
        String city = Config.CITY.trim();
        return (city.isEmpty() ? "" : city + " · ") + WeatherText.degrees(r.temp, Config.TEMPERATURE) + " "
                + WeatherText.condition(r.code, r.isDay);
    }
}
