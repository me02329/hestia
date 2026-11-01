package io.hestia.tv.ui;

/** Color arithmetic on 0xAARRGGBB ints. Plain Java: tested on a JVM (tests/java). */
public final class Colors {
    private Colors() {}

    public static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    /** The color with its alpha multiplied by a. */
    public static int alpha(int color, float a) {
        int base = (color >>> 24) & 255;
        return (Math.round(base * clamp(a)) << 24) | (color & 0xFFFFFF);
    }

    /** From a to b as f goes from 0 to 1. */
    public static int blend(int a, int b, float f) {
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * f) << 24) | (Math.round(ar + (br - ar) * f) << 16)
                | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
    }
}
