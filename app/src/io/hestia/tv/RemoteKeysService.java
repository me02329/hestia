package io.hestia.tv;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import io.hestia.tv.apps.HomeRole;
import io.hestia.tv.remote.KeyRouter;

/**
 * Lets the remote's TV and Settings buttons work inside other apps (Netflix, YouTube… receive them and ignore them):
 * they open what Hestia's settings chose. Every other key is passed on untouched. The service sees which app is in front
 * (package name) but never window content (res/xml/remote_keys_service.xml). Enabled once over ADB, see README.
 * The Settings button is left to Hestia's own screens (the panels close with it); the TV button is the same everywhere.
 */
public class RemoteKeysService extends AccessibilityService {
    private KeyRouter router;
    /** The package of the window in front, from the last window change seen (no content is read). */
    private volatile String foreground = "";

    @Override protected void onServiceConnected() {
        router = new KeyRouter(this);
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (router == null) return false;
        if (router.onTvKey(event)) return true;
        return !HomeRole.PACKAGE.equals(foreground) && router.onSettingsKey(event);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName() != null) {
            foreground = event.getPackageName().toString();
        }
    }

    @Override public void onInterrupt() {
    }

    @Override public void onDestroy() {
        if (router != null) router.cancel();
        super.onDestroy();
    }
}
