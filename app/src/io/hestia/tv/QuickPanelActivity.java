package io.hestia.tv;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.media.tv.TvContract;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * The quick panel, in place of Google TV's: inputs and the usual settings, on the right of the screen. Opens with the
 * remote's Settings button on the home screen, and anywhere with a long press on Home (System UI sends
 * TOGGLE_NOTIFICATION_HANDLER_PANEL to whoever handles it; Google TV's launcher did). Pressing either again closes it.
 * When something plays (Spotify, YouTube…), a player card comes first: artwork, title, live progress and
 * previous/play-pause/next. That needs notification access for MediaListener; without it, no player.
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

    /** Home held down: System UI starts this panel; another action may have been chosen for it. */
    @Override protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isToggle(getIntent())) {
            String action = RemoteKeys.get(this, RemoteKeys.LONG_HOME);
            if (!action.equals(RemoteKeys.PANEL) && RemoteKeys.run(this, action)) finish();
        }
    }

    private static boolean isToggle(Intent intent) {
        return intent != null && "android.app.action.TOGGLE_NOTIFICATION_HANDLER_PANEL".equals(intent.getAction());
    }

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
        if (media != null) {
            media.registerCallback(mediaCallback, handler);
            handler.post(progress);
        }
    }

    private void releaseMedia() {
        if (media != null) media.unregisterCallback(mediaCallback);
        media = null;
    }

    private void addMedia(List<Entry> list) {
        if (media == null) return;
        final MediaController m = media;
        list.add(new Entry("music", Ui.tr("Abrir ", "Open ") + appLabel(m.getPackageName()), "", false, () -> {
            Intent launch = HomeApps.launchIntent(getPackageManager(), m.getPackageName());
            if (launch != null) open(launch);
        }));
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    // MARK: - Player card (header of the panel)

    private final Runnable progress = new Runnable() {
        @Override public void run() {
            if (media == null) return;
            invalidatePanel();
            handler.postDelayed(this, 500);
        }
    };
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG), bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();
    private Paint appPaint, timePaint, timeRight;
    private TextPaint songPaint, artistPaint;

    @Override float headerHeight() {
        return media == null ? 0 : 400;
    }

    @Override int headerButtons() {
        return media == null ? 0 : 3;
    }

    @Override void onHeaderButton(int index) {
        if (media == null) return;
        MediaController.TransportControls t = media.getTransportControls();
        if (index == 0) t.skipToPrevious();
        else if (index == 2) t.skipToNext();
        else if (isPlaying()) t.pause();
        else t.play();
    }

    private boolean isPlaying() {
        PlaybackState st = media == null ? null : media.getPlaybackState();
        return st != null && st.getState() == PlaybackState.STATE_PLAYING;
    }

    /** Pochette, app, title and artist; a live progress bar with times; previous, play/pause, next. */
    @Override void drawHeader(Canvas c, RectF area, int focused) {
        if (media == null) return;
        if (appPaint == null) {
            appPaint = Ui.text(Ui.semibold, 20, 0xFF1ED760);
            appPaint.setLetterSpacing(0.12f);
            songPaint = new TextPaint(Ui.text(Ui.semibold, 34, Color.WHITE));
            artistPaint = new TextPaint(Ui.text(Ui.regular, 26, 0xB3FFFFFF));
            timePaint = Ui.numbers(Ui.regular, 22, 0x99FFFFFF);
            timeRight = Ui.numbers(Ui.regular, 22, 0x99FFFFFF);
            timeRight.setTextAlign(Paint.Align.RIGHT);
        }
        card.setColor(0x1AFFFFFF);
        c.drawRoundRect(area, 28, 28, card);
        MediaMetadata md = media.getMetadata();
        PlaybackState st = media.getPlaybackState();

        // Artwork (or a note on a gradient when the app sends none).
        float art = 176, ax = area.left + 24, ay = area.top + 24;
        box.set(ax, ay, ax + art, ay + art);
        Bitmap cover = md == null ? null : md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (cover == null && md != null) cover = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (cover == null && md != null) cover = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        c.save();
        clip.reset();
        clip.addRoundRect(box, 18, 18, Path.Direction.CW);
        c.clipPath(clip);
        if (cover != null) {
            c.drawBitmap(cover, null, box, artPaint);
        } else {
            card.setShader(new LinearGradient(box.left, box.top, box.right, box.bottom, 0xFF1ED760, 0xFF0B5E2C,
                    Shader.TileMode.CLAMP));
            c.drawRect(box, card);
            card.setShader(null);
            Symbols.draw(this, c, "music", box.centerX(), box.centerY(), 64, Color.WHITE);
        }
        c.restore();

        float tx = box.right + 26, tw = area.right - 24 - tx;
        c.drawText(appLabel(media.getPackageName()).toUpperCase(java.util.Locale.ROOT), tx, ay + 30, appPaint);
        String song = md == null ? "" : String.valueOf(md.getText(MediaMetadata.METADATA_KEY_TITLE));
        CharSequence artist = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_ARTIST);
        c.drawText(TextUtils.ellipsize(song, songPaint, tw, TextUtils.TruncateAt.END).toString(), tx, ay + 88, songPaint);
        if (artist != null) {
            c.drawText(TextUtils.ellipsize(artist, artistPaint, tw, TextUtils.TruncateAt.END).toString(), tx, ay + 128, artistPaint);
        }

        // Progress: the state's position moved on by the time since it was reported, at the playback speed.
        long duration = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long position = 0;
        if (st != null) {
            position = st.getPosition();
            if (st.getState() == PlaybackState.STATE_PLAYING) {
                position += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
            }
        }
        if (duration > 0) position = Math.max(0, Math.min(duration, position));
        float by = ay + art + 34, bl = area.left + 24, br = area.right - 24;
        bar.setColor(0x33FFFFFF);
        c.drawRoundRect(bl, by, br, by + 6, 3, 3, bar);
        if (duration > 0) {
            bar.setColor(Color.WHITE);
            c.drawRoundRect(bl, by, bl + (br - bl) * position / duration, by + 6, 3, 3, bar);
        }
        c.drawText(minutes(position), bl, by + 36, timePaint);
        if (duration > 0) c.drawText(minutes(duration), br, by + 36, timeRight);

        // Buttons: previous, play/pause (larger), next.
        float cy = area.bottom - 50, cx = area.centerX();
        String[] icons = {"skip_previous", isPlaying() ? "pause" : "play", "skip_next"};
        for (int i = 0; i < 3; i++) {
            float x = cx + (i - 1) * 132, r = i == 1 ? 42 : 34;
            boolean f = i == focused;
            card.setColor(f ? Color.WHITE : 0x26FFFFFF);
            c.drawCircle(x, cy, r, card);
            Symbols.draw(this, c, icons[i], x, cy, i == 1 ? 40 : 32, f ? 0xFF15161A : Color.WHITE);
        }
    }

    private static String minutes(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60);
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
