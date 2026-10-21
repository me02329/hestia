package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The current weather for your location (Open-Meteo, every 30 min, no key), cached so the screen opens with it.
 * Only fetched while the home screen or the screensaver is visible (the screen asks). Location from config.properties.
 */
final class HomeData {
    private static final String WEATHER_URL = "https://api.open-meteo.com/v1/forecast?latitude=" + Config.LATITUDE
            + "&longitude=" + Config.LONGITUDE
            + "&current=temperature_2m,weather_code,is_day&timezone=auto";
    private static final long WEATHER_EVERY = 30 * 60_000L;

    interface Listener { void onData(); }

    static final class Weather {
        double temp = Double.NaN;
        int code = -1;
        boolean isDay = true;
    }

    private static HomeData instance;

    static synchronized HomeData get(Context c) {
        if (instance == null) instance = new HomeData(c.getApplicationContext());
        return instance;
    }

    volatile long weatherAt;
    volatile Weather weather = new Weather();

    private final SharedPreferences cache;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private volatile boolean fetchingWeather;

    private HomeData(Context c) {
        cache = c.getSharedPreferences("homedata", Context.MODE_PRIVATE);
        try {
            String w = cache.getString("weather", null);
            if (w != null) weather = parseWeather(w);
            weatherAt = cache.getLong("weatherAt", 0);
        } catch (Exception ignored) {}
    }

    void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    /** Fetches whatever is stale. The screen calls this when it appears and every minute while it's visible. */
    void refreshIfStale() {
        long now = System.currentTimeMillis();
        // A negative age (the TV's clock went back) counts as stale: otherwise the data would freeze.
        if (!fetchingWeather && (now - weatherAt > WEATHER_EVERY || now < weatherAt)) {
            fetchingWeather = true;
            io.execute(() -> {
                try {
                    String body = get(WEATHER_URL);
                    weather = parseWeather(body);
                    weatherAt = System.currentTimeMillis();
                    cache.edit().putString("weather", body).putLong("weatherAt", weatherAt).apply();
                    notifyListeners();
                } catch (Exception e) {
                    AppLog.w("weather: " + e.getMessage());
                } finally {
                    fetchingWeather = false;
                }
            });
        }
    }

    private void notifyListeners() {
        main.post(() -> { for (Listener l : new ArrayList<>(listeners)) l.onData(); });
    }

    private static Weather parseWeather(String body) throws Exception {
        JSONObject current = new JSONObject(body).getJSONObject("current");
        Weather w = new Weather();
        w.temp = current.getDouble("temperature_2m");
        w.code = current.getInt("weather_code");
        w.isDay = current.optInt("is_day", 1) == 1;
        return w;
    }

    private static String get(String url) throws IOException {
        return Net.json(url);
    }

    // MARK: - Formatting

    /** The weather in words, from Open-Meteo's WMO code. */
    static String condition(int code, boolean day) {
        if (code < 0) return "";
        if (Ui.LANG_FR.equals(Ui.lang)) {
            if (code == 0) return day ? "Ensoleillé" : "Ciel dégagé";
            if (code == 1) return "Peu nuageux";
            if (code == 2) return "Partiellement nuageux";
            if (code == 3) return "Nuageux";
            if (code == 45 || code == 48) return "Brouillard";
            if (code >= 51 && code <= 57) return "Bruine";
            if (code == 65 || code == 67 || code == 82) return "Forte pluie";
            if (code >= 71 && code <= 77 || code == 85 || code == 86) return "Neige";
            if (code >= 95) return "Orages";
            return "Pluie";
        }
        if (Ui.en) {
            if (code == 0) return "Clear";
            if (code == 1) return day ? "Mostly Sunny" : "Mostly Clear";
            if (code == 2) return "Partly Cloudy";
            if (code == 3) return "Cloudy";
            if (code == 45 || code == 48) return "Fog";
            if (code >= 51 && code <= 57) return "Drizzle";
            if (code == 65 || code == 67 || code == 82) return "Heavy Rain";
            if (code >= 71 && code <= 77 || code == 85 || code == 86) return "Snow";
            if (code >= 95) return "Thunderstorms";
            return "Rain";
        }
        if (code == 0) return day ? "Céu limpo" : "Noite limpa";
        if (code == 1) return "Poucas nuvens";
        if (code == 2) return "Parcialmente nublado";
        if (code == 3) return "Nublado";
        if (code == 45 || code == 48) return "Neblina";
        if (code >= 51 && code <= 57) return "Garoa";
        if (code == 65 || code == 67 || code == 82) return "Chuva forte";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "Neve";
        if (code >= 95) return "Tempestade";
        return "Chuva";
    }

    /** Celsius or Fahrenheit (config.properties "temperature": C, F, or auto = Fahrenheit in the US). */
    static String degrees(double v) {
        if (Double.isNaN(v)) return "—";
        return Math.round(fahrenheit() ? v * 9 / 5 + 32 : v) + "°";
    }

    private static boolean fahrenheit() {
        String unit = Config.TEMPERATURE.trim().toUpperCase(Locale.ROOT);
        if (unit.startsWith("F")) return true;
        if (unit.startsWith("C")) return false;
        return "US".equals(Locale.getDefault().getCountry());
    }
}
