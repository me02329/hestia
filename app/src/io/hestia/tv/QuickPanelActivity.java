package io.hestia.tv;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import io.hestia.tv.apps.Card;
import io.hestia.tv.apps.Launcher;
import io.hestia.tv.apps.TvInputs;
import io.hestia.tv.core.I18n;
import io.hestia.tv.core.Screens;
import io.hestia.tv.core.TimeText;
import io.hestia.tv.data.Weather;
import io.hestia.tv.media.NowPlaying;
import io.hestia.tv.media.PlayerCard;
import io.hestia.tv.panel.PanelActivity;
import io.hestia.tv.panel.PanelEntry;
import io.hestia.tv.panel.PanelHeader;
import io.hestia.tv.power.SleepDelay;
import io.hestia.tv.remote.KeyBindings;

import java.util.ArrayList;
import java.util.List;

/**
 * The quick panel, in place of Google TV's: what's playing, inputs and the usual settings, on the right of the screen.
 * Opens with the remote's Settings button, and anywhere with a long press on Home (System UI sends
 * TOGGLE_NOTIFICATION_HANDLER_PANEL to whoever handles it; Google TV's launcher did). Pressing either again closes it.
 */
public class QuickPanelActivity extends PanelActivity {
    private static final String TOGGLE = "android.app.action.TOGGLE_NOTIFICATION_HANDLER_PANEL";
    private static final long PROGRESS_EVERY = 500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private NowPlaying playing;
    private PlayerCard player;

    private final Runnable progress = new Runnable() {
        @Override public void run() {
            if (!playing.active()) return;
            invalidatePanel();
            handler.postDelayed(this, PROGRESS_EVERY);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        playing = new NowPlaying(this, new ComponentName(this, MediaListener.class), () -> refresh());
        super.onCreate(savedInstanceState);
        player = new PlayerCard(this, playing);
        // Home held down: System UI starts this panel; another action may have been chosen for it.
        if (getIntent() != null && TOGGLE.equals(getIntent().getAction())) {
            String action = KeyBindings.get(this, KeyBindings.LONG_HOME);
            if (!KeyBindings.opensPanel(action) && KeyBindings.run(this, action)) finish();
        }
    }

    @Override protected void onResume() {
        playing.find();
        handler.removeCallbacks(progress);
        handler.post(progress);
        super.onResume();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(progress);
        playing.release();
        super.onPause();
    }

    /** Long press on Home again: toggles the panel off. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    @Override protected boolean onRight() {
        return true;
    }

    @Override protected PanelHeader header() {
        return player;
    }

    @Override protected String title() {
        long now = System.currentTimeMillis();
        return TimeText.clock(now) + "  ·  " + TimeText.dateLine(now);
    }

    @Override protected String subtitle() {
        return Weather.get(this).summary();
    }

    @Override protected List<PanelEntry> entries() {
        List<PanelEntry> list = new ArrayList<>();
        if (playing.active()) {
            String pkg = playing.packageName();
            list.add(new PanelEntry("music", I18n.tr("Abrir ", "Open ") + PlayerCard.appLabel(this, pkg), () -> {
                Intent app = Launcher.appIntent(getPackageManager(), pkg);
                if (app != null) open(app);
            }));
        }
        Intent live = TvInputs.liveTv(this);
        if (live != null) list.add(new PanelEntry("tv", I18n.tr("TV ao Vivo", "Live TV"), () -> open(live)));
        for (Card input : TvInputs.hdmi(this)) {
            list.add(new PanelEntry("hdmi", input.label, input.connected ? I18n.tr("Ligado", "On") : "", !input.connected,
                    () -> open(Launcher.input(input.inputId))));
        }
        list.add(new PanelEntry("clear_night", I18n.tr("Suspensão", "Sleep after"), SleepDelay.currentLabel(this), false,
                this::changeSleepDelay));
        list.add(setting("tv_settings", I18n.tr("Imagem", "Picture"), Settings.ACTION_DISPLAY_SETTINGS));
        list.add(setting("speaker", I18n.tr("Som", "Sound"), Settings.ACTION_SOUND_SETTINGS));
        list.add(setting("sync", I18n.tr("Rede e Internet", "Network & Internet"), Settings.ACTION_WIFI_SETTINGS));
        list.add(setting("gamepad", I18n.tr("Controles e acessórios", "Remotes & accessories"), Settings.ACTION_BLUETOOTH_SETTINGS));
        list.add(setting("settings", I18n.tr("Ajustes da TV", "TV settings"), Settings.ACTION_SETTINGS));
        list.add(new PanelEntry("home", "Hestia", I18n.tr("Ajustes", "Settings"), false, () -> open(Screens.settings(this))));
        return list;
    }

    private PanelEntry setting(String symbol, String label, String action) {
        return new PanelEntry(symbol, label, () -> open(new Intent(action)));
    }

    /** OK goes to the next delay and shows it; without the permission, the TV's own energy page. */
    private void changeSleepDelay() {
        if (!SleepDelay.canChange(this)) {
            open(SleepDelay.systemPage());
        } else if (SleepDelay.next(this)) {
            refresh();
        } else {
            Toast.makeText(this, I18n.tr("Indisponível nesta TV.", "Not available on this TV."), Toast.LENGTH_SHORT).show();
        }
    }

    private void open(Intent intent) {
        if (Launcher.startOrSay(this, intent)) finish();
    }
}
