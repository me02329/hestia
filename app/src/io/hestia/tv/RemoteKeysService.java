package io.hestia.tv;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Lets the remote's Settings button work inside other apps (Netflix, YouTube… receive it and ignore it): that one
 * key opens what Hestia's settings chose for it, the quick panel by default. Every other key is passed on untouched.
 * The service sees which app is in front (package name) but never window content (res/xml/remote_keys_service.xml).
 * Enabled once over ADB, see README.
 * On Hestia's own screens the key is left to them (the home screen and the panels already handle it).
 */
public class RemoteKeysService extends AccessibilityService {
    @Override protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_NOTIFICATION && code != KeyEvent.KEYCODE_SETTINGS) return false;
        if (HomeMode.PKG.equals(foreground)) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            if (!RemoteKeys.run(this, RemoteKeys.get(this, RemoteKeys.SETTINGS))) RemoteKeys.run(this, RemoteKeys.PANEL);
        }
        return true;   // the app underneath gets neither the press nor the release
    }

    /** The package of the window in front, from the last window change seen (no content is read). */
    private volatile String foreground = "";

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName() != null) {
            foreground = event.getPackageName().toString();
        }
    }

    @Override public void onInterrupt() {
    }
}
