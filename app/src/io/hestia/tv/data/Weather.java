package io.hestia.tv.data;

import android.content.Context;
import android.content.SharedPreferences;

import io.hestia.tv.core.Config;
import io.hestia.tv.core.Prefs;

import org.json.JSONObject;

/** The current weather for your city (Open-Meteo, every 30 min, no key). The city is chosen in the settings (CityActivity). */
public final class Weather extends Feed {
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

    private final Context context;
    private final SharedPreferences cache;
    private volatile Reading now = new Reading();

    private Weather(Context c) {
        super("weather", 30 * 60_000L);
        context = c;
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
        String latitude = Prefs.latitude(context), longitude = Prefs.longitude(context);
        String body = Net.json("https://api.open-meteo.com/v1/forecast?latitude=" + latitude + "&longitude=" + longitude
                + "&current=temperature_2m,weather_code,is_day&timezone=auto");
        if (!latitude.equals(Prefs.latitude(context)) || !longitude.equals(Prefs.longitude(context))) return;   // moved meanwhile
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

    /** The city's name; "" when none was given. */
    public String place() {
        return Prefs.cityName(context);
    }

    /** Makes this the city of the weather: the old reading goes at once, the new one is fetched. */
    public void moveTo(City city) {
        Prefs.setCity(context, city.name, City.coordinate(city.latitude), City.coordinate(city.longitude));
        cache.edit().remove("weather").remove("weatherAt").apply();
        now = new Reading();
        fetchedAt = 0;
        notifyListeners();
        refreshIfStale();
    }

    /** "Paris · 12° Nuageux" for the panels; "" before the first reading. */
    public String summary() {
        Reading r = now;
        if (Double.isNaN(r.temp)) return "";
        String city = place();
        return (city.isEmpty() ? "" : city + " · ") + WeatherText.degrees(r.temp, Config.TEMPERATURE) + " "
                + WeatherText.condition(r.code, r.isDay);
    }
}
