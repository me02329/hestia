package io.hestia.tv.core;

import android.view.KeyEvent;

/** What each key code of the remote means. */
public final class Keys {
    private Keys() {}

    /** The TV button of TCL remotes (scan 0x0c005b mapped by TCL's key layout; not an Android KEYCODE_ constant). */
    public static final int TCL_TV = 4001;

    public static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    /** The remote's Settings button (sent as NOTIFICATION on the TCL). */
    public static boolean isSettings(int code) {
        return code == KeyEvent.KEYCODE_NOTIFICATION || code == KeyEvent.KEYCODE_SETTINGS;
    }

    /** The TV button: the TCL remote's own code; GUIDE/TV on other remotes. */
    public static boolean isTv(int code) {
        return code == TCL_TV || code == KeyEvent.KEYCODE_GUIDE || code == KeyEvent.KEYCODE_TV;
    }
}
