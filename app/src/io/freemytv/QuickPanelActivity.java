package io.freemytv;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.media.tv.TvContract;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * The quick panel, in place of Google TV's: inputs and the usual settings, on the right of the screen. Opens with the
 * remote's Settings button on the home screen, and anywhere with a long press on Home (System UI sends
 * TOGGLE_NOTIFICATION_HANDLER_PANEL to whoever handles it; Google TV's launcher did). Pressing either again closes it.
 * When something plays (Spotify, YouTube…), it comes first with play/pause, next and previous; that needs notification
 * access for MediaListener, otherwise the panel simply has no "now playing".
 */
public class QuickPanelActivity extends PanelActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaController media;
    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { refresh(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { refresh(); }
        @Override public void onSessionDestroyed() {
            releaseMedia();
            refresh();
        }
    };

    @Override protected void onResume() {
        findMedia();
        super.onResume();
    }

    @Override protected void onPause() {
        releaseMedia();
        super.onPause();
    }

    /** The session playing now, else a paused one with something to show. */
    private void findMedia() {
        releaseMedia();
        List<MediaController> sessions;
        try {
            MediaSessionManager msm = getSystemService(MediaSessionManager.class);
            sessions = msm.getActiveSessions(new ComponentName(this, MediaListener.class));
        } catch (SecurityException | NullPointerException e) {
            return;   // no notification access: no "now playing"
        }
        MediaController paused = null;
        for (MediaController m : sessions) {
            PlaybackState st = m.getPlaybackState();
            if (st == null || m.getMetadata() == null) continue;
            if (st.getState() == PlaybackState.STATE_PLAYING) {
                media = m;
                break;
            }
            if (paused == null && st.getState() == PlaybackState.STATE_PAUSED) paused = m;
        }
        if (media == null) media = paused;
        if (media != null) media.registerCallback(mediaCallback, handler);
    }

    private void releaseMedia() {
        if (media != null) media.unregisterCallback(mediaCallback);
        media = null;
    }

    private void addMedia(List<Entry> list) {
        if (media == null) return;
        MediaMetadata md = media.getMetadata();
        PlaybackState st = media.getPlaybackState();
        String title = md == null ? "" : String.valueOf(md.getText(MediaMetadata.METADATA_KEY_TITLE));
        CharSequence artist = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist != null && artist.length() > 0) title += " — " + artist;
        String app = media.getPackageName();
        try {
            PackageManager pm = getPackageManager();
            app = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(media.getPackageName(), 0)));
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        final MediaController m = media;
        boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
        list.add(new Entry("music", title, app, false, () -> {
            Intent launch = HomeApps.launchIntent(getPackageManager(), m.getPackageName());
            if (launch != null) open(launch);
        }));
        list.add(new Entry(playing ? "pause" : "play", playing ? Ui.tr("Pausar", "Pause") : Ui.tr("Reproduzir", "Play"), "",
                false, () -> {
            if (playing) m.getTransportControls().pause(); else m.getTransportControls().play();
        }));
        list.add(new Entry("skip_next", Ui.tr("Próxima", "Next"), "", false, () -> m.getTransportControls().skipToNext()));
        list.add(new Entry("skip_previous", Ui.tr("Anterior", "Previous"), "", false,
                () -> m.getTransportControls().skipToPrevious()));
    }

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
        addMedia(list);
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
