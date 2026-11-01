package io.hestia.tv.ui;

import android.view.KeyEvent;

/**
 * Follows one key from press to release. It only counts if the press happened on this screen: the release of an OK
 * pressed inside another app opens nothing.
 */
public final class Press {
    public static final int NONE = 0, CLICK = 1, LONG = 2;

    private boolean down, longFired;

    /** Feed it every event of the key; says what just completed. LONG comes while still held (Android's timeout). */
    public int track(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                down = true;
                longFired = false;
            } else if (down && !longFired && e.isLongPress()) {
                longFired = true;
                return LONG;
            }
            return NONE;
        }
        int done = down && !longFired && !e.isCanceled() ? CLICK : NONE;
        down = false;
        return done;
    }
}
