package io.hestia.tv.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * The only door to the app's preferences. Every saved setting has a typed accessor here, so a key can't be misspelled
 * or given two defaults. The file and key names are what's already stored on the TV: don't rename them.
 */
public final class Prefs {
    private Prefs() {}

    private static SharedPreferences home(Context c) {
        return c.getSharedPreferences("home", Context.MODE_PRIVATE);
    }

    private static SharedPreferences usage(Context c) {
        return c.getSharedPreferences("usage", Context.MODE_PRIVATE);
    }

    /** A feed's private cache file (weather, headlines). */
    public static SharedPreferences cache(Context c, String name) {
        return c.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    /** Comma-separated packages, or null while no choice was made. */
    public static String favorites(Context c) {
        return home(c).getString("favorites", null);
    }

    public static void setFavorites(Context c, String joined) {
        home(c).edit().putString("favorites", joined).apply();
    }

    public static String sort(Context c, String fallback) {
        return home(c).getString("sort", fallback);
    }

    public static void setSort(Context c, String mode) {
        home(c).edit().putString("sort", mode).apply();
    }

    public static Set<String> hidden(Context c) {
        return new HashSet<>(home(c).getStringSet("hidden", new HashSet<>()));
    }

    public static void setHidden(Context c, Set<String> packages) {
        home(c).edit().putStringSet("hidden", packages).apply();
    }

    /** "auto", "en", "fr" or "pt": Hestia's settings first, then config.properties. */
    public static String language(Context c) {
        return home(c).getString("language", Config.LANGUAGE.trim());
    }

    public static void setLanguage(Context c, String choice) {
        home(c).edit().putString("language", choice).apply();
    }

    public static boolean newsEnabled(Context c) {
        return home(c).getBoolean("news", true);
    }

    public static void setNewsEnabled(Context c, boolean on) {
        home(c).edit().putBoolean("news", on).apply();
    }

    public static String keyAction(Context c, String key, String fallback) {
        return home(c).getString("key_" + key, fallback);
    }

    public static void setKeyAction(Context c, String key, String action) {
        home(c).edit().putString("key_" + key, action).apply();
    }

    /** Counted by Hestia itself when it opens an app (no usage-stats permission). */
    public static void recordLaunch(Context c, String pkg) {
        SharedPreferences usage = usage(c);
        usage.edit().putLong(pkg + ":n", usage.getLong(pkg + ":n", 0) + 1)
                .putLong(pkg + ":at", System.currentTimeMillis()).apply();
    }

    public static long launchCount(Context c, String pkg) {
        return usage(c).getLong(pkg + ":n", 0);
    }

    public static long lastLaunch(Context c, String pkg) {
        return usage(c).getLong(pkg + ":at", 0);
    }
}
