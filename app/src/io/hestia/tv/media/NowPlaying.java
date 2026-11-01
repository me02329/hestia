package io.hestia.tv.media;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;

/**
 * What's playing on the TV (Spotify, YouTube…), through the media sessions. Android only shows them to an app with
 * notification access, granted to MediaListener; without it, nothing is playing as far as Hestia knows.
 */
public final class NowPlaying {
    private final Context context;
    private final ComponentName listener;
    private final Runnable onChanged;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaController media;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { onChanged.run(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { onChanged.run(); }
        @Override public void onSessionDestroyed() {
            release();
            onChanged.run();
        }
    };

    public NowPlaying(Context c, ComponentName listener, Runnable onChanged) {
        context = c;
        this.listener = listener;
        this.onChanged = onChanged;
    }

    /** Follows the session playing now, else a paused one with something to show. */
    public void find() {
        release();
        List<MediaController> sessions;
        try {
            sessions = context.getSystemService(MediaSessionManager.class).getActiveSessions(listener);
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
        if (media != null) media.registerCallback(callback, handler);
    }

    public void release() {
        if (media != null) media.unregisterCallback(callback);
        media = null;
    }

    public boolean active() {
        return media != null;
    }

    public String packageName() {
        return media.getPackageName();
    }

    /** The title of what's playing, or "". */
    public String song() {
        MediaMetadata md = media.getMetadata();
        CharSequence title = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_TITLE);
        return title == null ? "" : title.toString();
    }

    public MediaMetadata metadata() {
        return media.getMetadata();
    }

    public boolean isPlaying() {
        PlaybackState st = media == null ? null : media.getPlaybackState();
        return st != null && st.getState() == PlaybackState.STATE_PLAYING;
    }

    /** The state's position moved on by the time since it was reported, at the playback speed. */
    public long position() {
        PlaybackState st = media.getPlaybackState();
        if (st == null) return 0;
        long position = st.getPosition();
        if (st.getState() == PlaybackState.STATE_PLAYING) {
            position += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
        }
        return position;
    }

    public void previous() {
        media.getTransportControls().skipToPrevious();
    }

    public void next() {
        media.getTransportControls().skipToNext();
    }

    public void togglePlay() {
        if (isPlaying()) media.getTransportControls().pause(); else media.getTransportControls().play();
    }
}
