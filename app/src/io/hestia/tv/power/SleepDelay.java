package io.hestia.tv.power;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

import io.hestia.tv.core.AppLog;
import io.hestia.tv.core.I18n;

/**
 * The TV's sleep and screensaver delays, for the quick panel. The only code in the app that writes a system setting, and
 * only these three: it needs WRITE_SECURE_SETTINGS, granted once over ADB (docs/adr/001, 002). Without it, canChange() is
 * false and the panel opens the TV's own page instead. Going back: the same row, or the TV's settings.
 */
public final class SleepDelay {
    private SleepDelay() {}

    private static final String SLEEP = "sleep_timeout", ATTENTIVE = "attentive_timeout";
    private static final String SCREEN_OFF = Settings.System.SCREEN_OFF_TIMEOUT;
    private static final int UNKNOWN = -1, MINUTE = 60_000;

    public static boolean canChange(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    /** The TV's energy page, for when Hestia can't change the delay itself. */
    public static Intent systemPage() {
        return new Intent("com.android.tv.settings.action.ENERGY_SAVING");
    }

    /** The TV's screensaver page. */
    public static Intent screensaverPage() {
        return new Intent(Settings.ACTION_DREAM_SETTINGS);
    }

    private static int screensaverMinutes(Context c) {
        return Settings.System.getInt(c.getContentResolver(), SCREEN_OFF, 10 * MINUTE) / MINUTE;
    }

    /** "10 min". */
    public static String screensaverLabel(Context c) {
        return screensaverMinutes(c) + " min";
    }

    /** Moves the screensaver to the next delay that still starts before the TV sleeps; false if the TV refused it. */
    public static boolean nextScreensaver(Context c) {
        int sleep = current(c);
        int minutes = SleepPolicy.nextScreensaver(screensaverMinutes(c), sleep == UNKNOWN ? SleepPolicy.NEVER : sleep);
        try {
            return Settings.System.putInt(c.getContentResolver(), SCREEN_OFF, minutes * MINUTE);
        } catch (SecurityException | IllegalArgumentException e) {
            AppLog.w("screensaver delay: " + e.getMessage());
            return false;
        }
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
