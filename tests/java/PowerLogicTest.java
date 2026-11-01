package io.hestia.tv.power;

public final class PowerLogicTest {
    private static void require(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }

    public static void main(String[] args) {
        // What TCL's own settings store, read on the real TV on 2026-11-01.
        require(SleepPolicy.sleepTimeoutMs(20) == 1139000, "20 min");
        require(SleepPolicy.attentiveTimeoutMs(240) == 14339000, "4 h");
        require(SleepPolicy.fromSleepTimeout(1139000) == 20, "reads 20 min back");
        require(SleepPolicy.fromSleepTimeout(1200000) == 20, "exact value set elsewhere");
        require(SleepPolicy.fromSleepTimeout(-1) == SleepPolicy.NEVER, "never");
        require(SleepPolicy.fromSleepTimeout(0) == SleepPolicy.NEVER, "zero is never");
        require(SleepPolicy.sleepTimeoutMs(SleepPolicy.NEVER) == -1, "never written");
        require(SleepPolicy.attentiveTimeoutMs(SleepPolicy.NEVER) == -1, "never for both");
        require(SleepPolicy.attentiveTimeoutMs(15) == 14339000, "a short delay doesn't cut a film");
        for (int m : SleepPolicy.MINUTES) {
            require(m == SleepPolicy.NEVER || SleepPolicy.fromSleepTimeout(SleepPolicy.sleepTimeoutMs(m)) == m, "round trip " + m);
            require(SleepPolicy.attentiveTimeoutMs(m) == -1 || SleepPolicy.attentiveTimeoutMs(m) >= SleepPolicy.sleepTimeoutMs(m),
                    "attentive never shorter " + m);
        }
        int seen = 0, m = SleepPolicy.MINUTES[0];
        do {
            m = SleepPolicy.next(m);
            seen++;
        } while (m != SleepPolicy.MINUTES[0] && seen < 100);
        require(seen == SleepPolicy.MINUTES.length, "next loops through every choice");
        require(SleepPolicy.next(7) == SleepPolicy.MINUTES[0], "unknown value restarts");
        require(SleepPolicy.nextScreensaver(10, 20) == 15, "next screensaver");
        require(SleepPolicy.nextScreensaver(15, 20) == 5, "never as late as the sleep: back to the first");
        require(SleepPolicy.nextScreensaver(20, SleepPolicy.NEVER) == 30, "no sleep, no limit");
        require(SleepPolicy.nextScreensaver(30, SleepPolicy.NEVER) == 5, "loops");
        require(SleepPolicy.nextTimer(0) == 15, "off to first");
        require(SleepPolicy.nextTimer(28) == 30, "next longer than what's left");
        require(SleepPolicy.nextTimer(120) == 0, "past the longest: off");
        System.out.println("checks passed");
    }
}
