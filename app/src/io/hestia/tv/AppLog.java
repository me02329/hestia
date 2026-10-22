package io.hestia.tv;

import android.util.Log;

/** The app's log (adb logcat -s Hestia). Never logs keys. */
final class AppLog {
    private AppLog() {}

    static void w(String message) {
        message = message.replaceAll("(?i)(api_key=)[^&\\s]+", "$1[redacted]");
        Log.i("Hestia", message);
    }
}
