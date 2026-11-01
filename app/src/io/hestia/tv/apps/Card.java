package io.hestia.tv.apps;

import android.graphics.Bitmap;

/** One thing on a row of the home screen: an app (its 16:9 art) or a shortcut (an icon on a colored card). */
public final class Card {
    public enum Kind { APP, LIVE_TV, INPUT, HESTIA_SETTINGS, TV_SETTINGS, SEARCH, NOW_PLAYING }

    public final Kind kind;
    public final String key, label;
    public String pkg, inputId;
    public String symbol;              // icon of the shortcuts
    public Bitmap tile;                // 16:9 art of the apps
    public boolean connected = true;   // inputs: a device is sending a signal
    String number;                     // inputs: HDMI number, for the order

    Card(Kind kind, String key, String label) {
        this.kind = kind;
        this.key = key;
        this.label = label;
    }

    /** What's playing: opens its app. */
    public static Card nowPlaying(String song, String pkg) {
        Card card = shortcut(Kind.NOW_PLAYING, "now-playing", song, "music");
        card.pkg = pkg;
        return card;
    }

    static Card shortcut(Kind kind, String key, String label, String symbol) {
        Card card = new Card(kind, key, label);
        card.symbol = symbol;
        return card;
    }
}
