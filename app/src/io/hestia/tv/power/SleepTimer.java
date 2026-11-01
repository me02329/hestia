package io.hestia.tv.power;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import io.hestia.tv.core.I18n;

/**
 * "Turn off in 30 min", once, without touching the TV's delays. RemoteKeysService attaches what actually puts the TV to
 * sleep (Android's accessibility action): without that service there's no timer. It stops if the TV turns off first.
 */
public final class SleepTimer {
    private SleepTimer() {}

    private static final long MINUTE = 60_000, WARN_BEFORE = MINUTE;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static Context context;
    private static Runnable sleep;
    private static long deadline;

    private static final Runnable WARN = () -> Toast.makeText(context,
            I18n.tr("A TV será desligada em 1 min", "The TV turns off in 1 min"), Toast.LENGTH_LONG).show();
    private static final Runnable FIRE = () -> {
        deadline = 0;
        if (sleep != null) sleep.run();
    };

    public static void attach(Context service, Runnable putToSleep) {
        context = service;
        sleep = putToSleep;
    }

    public static void detach() {
        cancel();
        context = null;
        sleep = null;
    }

    public static boolean available() {
        return sleep != null;
    }

    public static void cancel() {
        HANDLER.removeCallbacks(WARN);
        HANDLER.removeCallbacks(FIRE);
        deadline = 0;
    }

    /** Minutes left, rounded up; 0 when off. */
    private static int minutesLeft() {
        if (deadline == 0) return 0;
        return (int) Math.max(1, (deadline - SystemClock.uptimeMillis() + MINUTE - 1) / MINUTE);
    }

    /** "28 min", "1 h 30"; "" when off. */
    public static String label() {
        int left = minutesLeft();
        return left == 0 ? "" : left < 60 ? left + " min" : left % 60 == 0 ? (left / 60) + " h" : (left / 60) + " h " + (left % 60);
    }

    /** OK in the panel: the next longer choice, then off. */
    public static void next() {
        int minutes = SleepPolicy.nextTimer(minutesLeft());
        cancel();
        if (minutes == 0) return;
        long ms = minutes * MINUTE;
        deadline = SystemClock.uptimeMillis() + ms;
        HANDLER.postDelayed(WARN, ms - WARN_BEFORE);
        HANDLER.postDelayed(FIRE, ms);
    }
}
