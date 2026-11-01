package io.hestia.tv.core;

import android.util.Log;

/** The app's log (adb logcat -s Hestia). */
public final class AppLog {
    private AppLog() {}

    public static void w(String message) {
        Log.i("Hestia", message);
    }
}
