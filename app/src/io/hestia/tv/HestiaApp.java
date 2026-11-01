package io.hestia.tv;

import android.app.Application;

import io.hestia.tv.core.Watchdog;
import io.hestia.tv.ui.Ui;

/** Process start: crashes are recorded from the very first line, and everything can draw. */
public class HestiaApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Watchdog.install(this);
        Ui.init(this);
    }
}
