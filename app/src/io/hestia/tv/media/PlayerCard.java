package io.hestia.tv.media;

import android.content.Context;
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
import android.text.TextPaint;
import android.text.TextUtils;

import io.hestia.tv.core.TimeText;
import io.hestia.tv.panel.PanelHeader;
import io.hestia.tv.ui.Fonts;
import io.hestia.tv.ui.Paints;
import io.hestia.tv.ui.Symbols;

import java.util.Locale;

/** The quick panel's player: artwork, app, title and artist; a live progress bar; previous, play/pause, next. */
public final class PlayerCard implements PanelHeader {
    private static final int PREVIOUS = 0, NEXT = 2;

    private final Context context;
    private final NowPlaying playing;
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG), bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();
    private final Paint appPaint, timePaint, timeRight;
    private final TextPaint songPaint, artistPaint;

    public PlayerCard(Context c, NowPlaying playing) {
        context = c;
        this.playing = playing;
        appPaint = Paints.text(Fonts.semibold, 20, 0xFF1ED760);
        appPaint.setLetterSpacing(0.12f);
        songPaint = new TextPaint(Paints.text(Fonts.semibold, 34, Color.WHITE));
        artistPaint = new TextPaint(Paints.text(Fonts.regular, 26, 0xB3FFFFFF));
        timePaint = Paints.numbers(Fonts.regular, 22, 0x99FFFFFF);
        timeRight = Paints.numbers(Fonts.regular, 22, 0x99FFFFFF);
        timeRight.setTextAlign(Paint.Align.RIGHT);
    }

    /** An app's name, or its package if it's gone. */
    public static String appLabel(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    @Override public float height() {
        return playing.active() ? 400 : 0;
    }

    @Override public int buttons() {
        return playing.active() ? 3 : 0;
    }

    @Override public void press(int button) {
        if (!playing.active()) return;
        if (button == PREVIOUS) playing.previous();
        else if (button == NEXT) playing.next();
        else playing.togglePlay();
    }

    @Override public void draw(Canvas c, RectF area, int focused) {
        if (!playing.active()) return;
        card.setColor(0x1AFFFFFF);
        c.drawRoundRect(area, 28, 28, card);
        MediaMetadata md = playing.metadata();

        float art = 176, ax = area.left + 24, ay = area.top + 24;
        box.set(ax, ay, ax + art, ay + art);
        drawArtwork(c, md);

        float tx = box.right + 26, tw = area.right - 24 - tx;
        c.drawText(appLabel(context, playing.packageName()).toUpperCase(Locale.ROOT), tx, ay + 30, appPaint);
        String song = md == null ? "" : String.valueOf(md.getText(MediaMetadata.METADATA_KEY_TITLE));
        CharSequence artist = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_ARTIST);
        c.drawText(TextUtils.ellipsize(song, songPaint, tw, TextUtils.TruncateAt.END).toString(), tx, ay + 88, songPaint);
        if (artist != null) {
            c.drawText(TextUtils.ellipsize(artist, artistPaint, tw, TextUtils.TruncateAt.END).toString(), tx, ay + 128, artistPaint);
        }

        long duration = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long position = playing.position();
        if (duration > 0) position = Math.max(0, Math.min(duration, position));
        float by = ay + art + 34, bl = area.left + 24, br = area.right - 24;
        bar.setColor(0x33FFFFFF);
        c.drawRoundRect(bl, by, br, by + 6, 3, 3, bar);
        if (duration > 0) {
            bar.setColor(Color.WHITE);
            c.drawRoundRect(bl, by, bl + (br - bl) * position / duration, by + 6, 3, 3, bar);
        }
        c.drawText(TimeText.minutes(position), bl, by + 36, timePaint);
        if (duration > 0) c.drawText(TimeText.minutes(duration), br, by + 36, timeRight);

        float cy = area.bottom - 50, cx = area.centerX();
        String[] icons = {"skip_previous", playing.isPlaying() ? "pause" : "play", "skip_next"};
        for (int i = 0; i < 3; i++) {
            float x = cx + (i - 1) * 132, r = i == 1 ? 42 : 34;
            boolean f = i == focused;
            card.setColor(f ? Color.WHITE : 0x26FFFFFF);
            c.drawCircle(x, cy, r, card);
            Symbols.draw(context, c, icons[i], x, cy, i == 1 ? 40 : 32, f ? 0xFF15161A : Color.WHITE);
        }
    }

    /** The app's artwork, or a note on a gradient when it sends none. */
    private void drawArtwork(Canvas c, MediaMetadata md) {
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
            Symbols.draw(context, c, "music", box.centerX(), box.centerY(), 64, Color.WHITE);
        }
        c.restore();
    }
}
