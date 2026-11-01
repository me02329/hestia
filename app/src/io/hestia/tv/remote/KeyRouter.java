package io.hestia.tv.remote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import io.hestia.tv.core.Keys;

/**
 * Turns the remote's TV and Settings keys into what the settings chose for them, wherever they arrive: the accessibility
 * service (inside any app) or a Hestia screen. A long press is timed here rather than read from the event, because the
 * accessibility filter sees the press and the release but not Android's key repeats.
 */
public final class KeyRouter {
    private static final long LONG_PRESS_MS = 600;
    /** TCL's firmware can send a second code for one TV press; it must not open two things. */
    private static final long SAME_PRESS_MS = 400;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean tvDown, settingsDown;
    private long firedAt;

    private final Runnable longTv = () -> {
        tvDown = false;
        fire(KeyBindings.LONG_TV);
    };

    public KeyRouter(Context c) {
        context = c;
    }

    /** True when the key was the TV button: the event is consumed, press and release. */
    public boolean onTvKey(KeyEvent e) {
        if (!Keys.isTv(e.getKeyCode())) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0 && !tvDown) {
                tvDown = true;
                handler.postDelayed(longTv, LONG_PRESS_MS);
            }
        } else if (tvDown) {   // not down anymore: the long press already fired
            tvDown = false;
            handler.removeCallbacks(longTv);
            if (!e.isCanceled()) fire(KeyBindings.TV);
        }
        return true;
    }

    /** True when the key was the Settings button; fires on release, only if the press was seen here. */
    public boolean onSettingsKey(KeyEvent e) {
        if (!Keys.isSettings(e.getKeyCode())) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) settingsDown = true;
        } else {
            if (settingsDown && !e.isCanceled()) fire(KeyBindings.SETTINGS);
            settingsDown = false;
        }
        return true;
    }

    /** The screen or service is going away: a press in progress opens nothing. */
    public void cancel() {
        handler.removeCallbacks(longTv);
        tvDown = settingsDown = false;
    }

    private void fire(String key) {
        long now = SystemClock.uptimeMillis();
        if (now - firedAt < SAME_PRESS_MS) return;
        firedAt = now;
        KeyBindings.runKey(context, key);
    }
}
