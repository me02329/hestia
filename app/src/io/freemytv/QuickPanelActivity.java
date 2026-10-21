package io.freemytv;

import android.content.ComponentName;
import android.content.Intent;
import android.media.tv.TvContract;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * The quick panel, in place of Google TV's: inputs and the usual settings, on the right of the screen. Opens with the
 * remote's Settings button on the home screen, and anywhere with a long press on Home (System UI sends
 * TOGGLE_NOTIFICATION_HANDLER_PANEL to whoever handles it; Google TV's launcher did). Pressing either again closes it.
 */
public class QuickPanelActivity extends PanelActivity {
    @Override String title() {
        return Ui.clock(System.currentTimeMillis()) + "  ·  " + cap(Ui.date(System.currentTimeMillis()));
    }

    @Override String subtitle() {
        HomeData.Weather wx = HomeData.get(this).weather;
        if (Double.isNaN(wx.temp)) return "";
        String city = Config.CITY.trim();
        return (city.isEmpty() ? "" : city + " · ") + HomeData.degrees(wx.temp) + " " + HomeData.condition(wx.code, wx.isDay);
    }

    @Override boolean onRight() {
        return true;
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();   // long press on Home again: toggles the panel off
    }

    @Override List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        Intent live = HomeApps.liveTv(this);
        if (live != null) {
            list.add(new Entry("tv", Ui.tr("TV ao Vivo", "Live TV"), "", false, () -> open(live)));
        }
        for (HomeApps.Item it : HomeApps.allInputs(this)) {
            if (it.kind != HomeApps.INPUT) continue;
            Intent hdmi = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(it.inputId));
            list.add(new Entry("hdmi", it.label, it.connected ? Ui.tr("Ligado", "On") : "", !it.connected, () -> open(hdmi)));
        }
        list.add(new Entry("tv_settings", Ui.tr("Imagem", "Picture"), "", false,
                () -> open(new Intent(Settings.ACTION_DISPLAY_SETTINGS))));
        list.add(new Entry("speaker", Ui.tr("Som", "Sound"), "", false,
                () -> open(new Intent(Settings.ACTION_SOUND_SETTINGS))));
        list.add(new Entry("sync", Ui.tr("Rede e Internet", "Network & Internet"), "", false,
                () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS))));
        list.add(new Entry("gamepad", Ui.tr("Controles e acessórios", "Remotes & accessories"), "", false,
                () -> open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))));
        list.add(new Entry("settings", Ui.tr("Ajustes da TV", "TV settings"), "", false,
                () -> open(new Intent(Settings.ACTION_SETTINGS))));
        list.add(new Entry("home", "Hestia", Ui.tr("Ajustes", "Settings"), false,
                () -> open(new Intent().setComponent(new ComponentName(this, SettingsActivity.class)))));
        return list;
    }

    private void open(Intent intent) {
        if (launch(intent)) finish();
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
