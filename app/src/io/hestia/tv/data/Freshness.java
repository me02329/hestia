package io.hestia.tv.data;

/** When cached data must be fetched again. Plain Java: tested on a JVM (tests/java). */
final class Freshness {
    private Freshness() {}

    /** How long to wait after this many failures in a row: 1 min, doubling, at most 30 min. */
    static long retryDelay(int failures) {
        return Math.min(30, 1L << Math.min(5, Math.max(0, failures - 1))) * 60_000L;
    }

    /** Old enough, never fetched, or fetched "in the future" (the TV's clock went back: it would freeze otherwise). */
    static boolean isStale(long now, long fetchedAt, long maxAge) {
        return now - fetchedAt >= maxAge || now < fetchedAt;
    }
}
