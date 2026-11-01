package io.hestia.tv;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

import io.hestia.tv.core.Keys;
import io.hestia.tv.dream.DreamView;
import io.hestia.tv.settings.SettingsView;
import io.hestia.tv.ui.HestiaActivity;

/** Hestia's settings: favorites, headlines, screensaver preview and home choice (through Android, never forced). */
public class SettingsActivity extends HestiaActivity {
    private static final String EXTRA_SCREENSAVER = "screensaver";

    private SettingsView view;
    private DreamView preview;   // "Screensaver": the screensaver right here, without changing the system setting

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new SettingsView(this, this::showPreview);
        setContentView(view);
    }

    @Override protected void onResume() {
        super.onResume();
        view.resume();
        // From ADB: am start -n io.hestia.tv/.SettingsActivity --ez screensaver true (straight to the preview).
        if (preview == null && getIntent().getBooleanExtra(EXTRA_SCREENSAVER, false)) {
            getIntent().removeExtra(EXTRA_SCREENSAVER);
            showPreview();
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override protected void onPause() {
        closePreview();
        super.onPause();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (preview != null) {
            // Like the real screensaver: arrows, OK or Back close it (other keys go on to the system).
            int code = event.getKeyCode();
            if (Keys.isOk(code) || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_UP
                    || code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_LEFT
                    || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.getAction() == KeyEvent.ACTION_UP) closePreview();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        if (view.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private void showPreview() {
        preview = new DreamView(this);
        setContentView(preview);
        preview.start();
    }

    private void closePreview() {
        if (preview == null) return;
        preview.stop();
        preview = null;
        setContentView(view);
        view.resume();
    }
}
