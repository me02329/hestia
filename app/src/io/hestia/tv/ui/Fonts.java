package io.hestia.tv.ui;

import android.content.res.AssetManager;
import android.graphics.Typeface;

import java.io.IOException;
import java.io.InputStream;

/**
 * Font: Inter (variable, assets/fonts, SIL OFL), with the "display" optical size for large text and the "text" size for
 * small text; without it, the system font at a similar weight.
 */
public final class Fonts {
    private Fonts() {}

    private static final String FILE = "fonts/InterVariable.ttf";
    public static Typeface light, displaySemibold, regular, medium, semibold;
    private static boolean loaded;

    static synchronized void load(AssetManager a) {
        if (loaded) return;
        loaded = true;
        light = font(a, 32, 300);
        displaySemibold = font(a, 32, 600);
        regular = font(a, 14, 400);
        medium = font(a, 14, 500);
        semibold = font(a, 14, 600);
    }

    /** Inter at an optical size (14 = text, 32 = display) and weight; the system font if the file is missing. */
    private static Typeface font(AssetManager a, int opticalSize, int weight) {
        if (has(a)) {
            try {
                Typeface t = new Typeface.Builder(a, FILE)
                        .setFontVariationSettings("'opsz' " + opticalSize + ", 'wght' " + weight).build();
                if (t != null) return t;
            } catch (RuntimeException ignored) {
            }
        }
        return Typeface.create(Typeface.DEFAULT, weight, false);
    }

    private static boolean has(AssetManager a) {
        try (InputStream in = a.open(FILE)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }
}
