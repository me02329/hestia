package io.hestia.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import io.hestia.tv.apps.HomeRole;
import io.hestia.tv.power.SleepTimer;
import io.hestia.tv.remote.KeyRouter;

/**
 * Lets the remote's TV and Settings buttons work inside other apps (Netflix, YouTube… receive them and ignore them):
 * they open what Hestia's settings chose. Every other key is passed on untouched. The service sees which app is in front
 * (package name) but never window content (res/xml/remote_keys_service.xml). Enabled once over ADB, see README.
 * The Settings button is left to Hestia's own screens (the panels close with it); the TV button is the same everywhere.
 * It also carries the quick panel's timer: going to sleep is an action only this service can ask of Android.
 */
public class RemoteKeysService extends AccessibilityService {
    private KeyRouter router;
    /** The package of the window in front, from the last window change seen (no content is read). */
    private volatile String foreground = "";

    /** The TV turned off some other way: the timer must not fire at the next wake-up. */
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            SleepTimer.cancel();
        }
    };

    @Override protected void onServiceConnected() {
        router = new KeyRouter(this);
        SleepTimer.attach(this, () -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN));
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED);
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
        if (router != null) {
            router.cancel();
            SleepTimer.detach();
            unregisterReceiver(screenOff);
        }
        super.onDestroy();
    }
}
