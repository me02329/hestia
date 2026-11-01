package io.hestia.tv.power;

/**
 * The choices of sleep delay and the values they mean for Android. Plain Java: tested on a JVM (tests/java).
 *
 * The TV keeps two delays since the last press of the remote: sleep_timeout (asleep if nothing is playing) and
 * attentive_timeout (asleep even if something is playing). TCL's own settings store each choice minus 61 s, the same
 * here so its settings page keeps showing the right value: 20 min is 1139000, 4 h is 14339000.
 */
final class SleepPolicy {
    private SleepPolicy() {}

    static final int NEVER = 0;
    /** Minutes offered, then never. */
    static final int[] MINUTES = {15, 20, 30, 60, 120, 240, NEVER};
    private static final long TCL_MARGIN_MS = 61_000;
    /** Asleep even while something plays: never sooner than this, so a film isn't cut by a short delay. */
    private static final int ATTENTIVE_MIN_MINUTES = 240;

    static long sleepTimeoutMs(int minutes) {
        return minutes == NEVER ? -1 : minutes * 60_000L - TCL_MARGIN_MS;
    }

    static long attentiveTimeoutMs(int minutes) {
        return minutes == NEVER ? -1 : sleepTimeoutMs(Math.max(minutes, ATTENTIVE_MIN_MINUTES));
    }

    /** The choice closest to a stored sleep_timeout (it may have been set elsewhere). */
    static int fromSleepTimeout(long ms) {
        if (ms <= 0) return NEVER;
        int best = MINUTES[0];
        for (int m : MINUTES) {
            if (m != NEVER && Math.abs(sleepTimeoutMs(m) - ms) < Math.abs(sleepTimeoutMs(best) - ms)) best = m;
        }
        return best;
    }

    /** The choice after this one, in a loop. */
    static int next(int minutes) {
        for (int i = 0; i < MINUTES.length; i++) {
            if (MINUTES[i] == minutes) return MINUTES[(i + 1) % MINUTES.length];
        }
        return MINUTES[0];
    }
}
