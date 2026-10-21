package io.freemytv;

import android.util.Log;

/** The app's log (adb logcat -s FreeMyTV). Never logs keys. */
final class AppLog {
    private AppLog() {}

    static void w(String message) {
        String key = Config.TMDB_KEY.trim();
        if (!key.isEmpty()) message = message.replace(key, "[redacted]");
        message = message.replaceAll("(?i)(api_key=)[^&\\s]+", "$1[redacted]");
        Log.i("FreeMyTV", message);
    }
}
