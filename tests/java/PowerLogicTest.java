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
        System.out.println("checks passed");
    }
}
