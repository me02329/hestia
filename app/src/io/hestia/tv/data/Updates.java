package io.hestia.tv.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import io.hestia.tv.core.Prefs;

import org.json.JSONObject;

/**
 * Whether a newer Hestia was published on GitHub. Asked at most once a day, only while Hestia's settings are open; it
 * tells, it never downloads or installs anything.
 */
public final class Updates extends Feed {
    private static final String LATEST = "https://api.github.com/repos/me02329/hestia/releases/latest";
    public static final String PAGE = "https://github.com/me02329/hestia/releases/latest";

    private static Updates instance;

    public static synchronized Updates get(Context c) {
        if (instance == null) instance = new Updates(c.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final SharedPreferences cache;

    private Updates(Context c) {
        super("updates", 24 * 3600_000L);
        context = c;
        cache = Prefs.cache(c, "updates");
        fetchedAt = cache.getLong("at", 0);
    }

    /** "1.13-safe"; "" if unknown. */
    public String installed() {
        try {
            String name = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            return name == null ? "" : name;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** The newer version published, or "" when up to date (or nothing was published yet). */
    public String available() {
        String tag = cache.getString("tag", "");
        return Version.isTag(tag) && Version.isNewer(tag, installed()) ? tag.replaceFirst("^v", "") : "";
    }

    @Override protected void fetch() throws Exception {
        String tag = new JSONObject(Net.json(LATEST)).optString("tag_name");
        fetchedAt = System.currentTimeMillis();
        cache.edit().putString("tag", tag).putLong("at", fetchedAt).apply();
        notifyListeners();
    }
}
