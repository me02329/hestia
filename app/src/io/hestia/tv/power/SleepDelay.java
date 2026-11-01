package io.hestia.tv.power;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

import io.hestia.tv.core.AppLog;
import io.hestia.tv.core.I18n;

/**
 * The TV's sleep delay, for the quick panel. The only code in the app that writes a system setting, and only these two:
 * it needs WRITE_SECURE_SETTINGS, granted once over ADB (docs/adr/001). Without it, canChange() is false and the panel
 * opens the TV's own energy page instead. Going back: the same row, or the TV's settings.
 */
public final class SleepDelay {
    private SleepDelay() {}

    private static final String SLEEP = "sleep_timeout", ATTENTIVE = "attentive_timeout";
    private static final int UNKNOWN = -1;

    public static boolean canChange(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    /** The TV's energy page, for when Hestia can't change the delay itself. */
    public static Intent systemPage() {
        return new Intent("com.android.tv.settings.action.ENERGY_SAVING");
    }

    /** The current choice, in minutes (SleepPolicy.NEVER = 0); UNKNOWN if this Android won't let it be read. */
    private static int current(Context c) {
        try {
            return SleepPolicy.fromSleepTimeout(Settings.Secure.getLong(c.getContentResolver(), SLEEP, -1));
        } catch (SecurityException e) {
            AppLog.w("sleep delay: " + e.getMessage());
            return UNKNOWN;
        }
    }

    /** "20 min", "1 h", "Jamais"; "" if unknown. */
    public static String currentLabel(Context c) {
        int minutes = current(c);
        if (minutes == UNKNOWN) return "";
        if (minutes == SleepPolicy.NEVER) return I18n.tr("Nunca", "Never");
        return minutes < 60 ? minutes + " min" : (minutes / 60) + " h";
    }

    /** Moves to the next choice; false if the TV refused it. */
    public static boolean next(Context c) {
        int minutes = SleepPolicy.next(current(c));
        try {
            return Settings.Secure.putLong(c.getContentResolver(), SLEEP, SleepPolicy.sleepTimeoutMs(minutes))
                    && Settings.Secure.putLong(c.getContentResolver(), ATTENTIVE, SleepPolicy.attentiveTimeoutMs(minutes));
        } catch (SecurityException e) {
            AppLog.w("sleep delay: " + e.getMessage());
            return false;
        }
    }
}
