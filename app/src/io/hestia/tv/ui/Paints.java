package io.hestia.tv.ui;

import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

/** Text paints and the measures for placing text on the canvas. */
public final class Paints {
    private Paints() {}

    private static final Rect BOUNDS = new Rect();

    public static Paint text(Typeface face, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(face);
        p.setTextSize(size);
        p.setColor(color);
        return p;
    }

    /** Fixed-width digits (the clock doesn't jitter when a digit changes). */
    public static Paint numbers(Typeface face, float size, int color) {
        Paint p = text(face, size, color);
        p.setFontFeatureSettings("'tnum'");
        return p;
    }

    /** Height of capital letters (and digits) above the baseline. */
    public static float cap(Paint p) {
        synchronized (BOUNDS) {
            p.getTextBounds("H8", 0, 2, BOUNDS);
            return -BOUNDS.top;
        }
    }

    /** Half the cap height: added to the center line, it gives the baseline that centers the text. */
    public static float halfCap(Paint p) {
        return cap(p) / 2f;
    }

    /** Left bearing of the first character (large, thin fonts start a little inward). */
    public static float leftBearing(Paint p, String s) {
        if (s.isEmpty()) return 0;
        synchronized (BOUNDS) {
            p.getTextBounds(s, 0, 1, BOUNDS);
            return BOUNDS.left;
        }
    }
}
