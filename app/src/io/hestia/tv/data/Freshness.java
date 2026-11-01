package io.hestia.tv.data;

/** When cached data must be fetched again. Plain Java: tested on a JVM (tests/java). */
final class Freshness {
    private Freshness() {}

    /** Old enough, never fetched, or fetched "in the future" (the TV's clock went back: it would freeze otherwise). */
    static boolean isStale(long now, long fetchedAt, long maxAge) {
        return now - fetchedAt >= maxAge || now < fetchedAt;
    }
}
