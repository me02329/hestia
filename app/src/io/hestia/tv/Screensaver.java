package io.hestia.tv;

import android.service.dreams.DreamService;

import io.hestia.tv.core.Watchdog;
import io.hestia.tv.dream.DreamView;
import io.hestia.tv.ui.Ui;

/**
 * The Hestia screensaver (enabled via `settings put secure screensaver_components`). The system
 * still decides when it starts and when the TV turns off, from its own delays (the quick panel can change the second,
 * see SleepDelay). Any key exits.
 */
public class Screensaver extends DreamService {
    private DreamView view;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (Watchdog.crashedTooOften(this)) {
            finish();
            return;
        }
        Ui.init(this);
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        view = new DreamView(this);
        setContentView(view);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        if (view != null) view.start();
    }

    @Override public void onDreamingStopped() {
        if (view != null) view.stop();
        super.onDreamingStopped();
    }

    @Override public void onDetachedFromWindow() {
        if (view != null) view.stop();
        super.onDetachedFromWindow();
    }
}
