package io.hestia.tv.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;

import io.hestia.tv.core.Background;
import io.hestia.tv.data.Photos;
import io.hestia.tv.ui.CanvasView;
import io.hestia.tv.ui.Colors;

import java.util.List;

/** The full-screen photo of the day: a new one every 2 minutes, with a crossfade. */
final class PhotoCarousel {
    private static final long EVERY = 120_000, FADE_MS = 1_500;
    private static final int FALLBACK = 0xFF0E1420;

    private final Photos photos;
    private final Runnable onChanged;
    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private Bitmap photo, old;
    private Photos.Photo shown;
    private long fadeStart, changedAt;
    private int index = -1;

    PhotoCarousel(Photos photos, Runnable onChanged) {
        this.photos = photos;
        this.onChanged = onChanged;
    }

    boolean isEmpty() {
        return photo == null;
    }

    boolean due() {
        return photo == null || SystemClock.uptimeMillis() - changedAt >= EVERY;
    }

    boolean fading() {
        return SystemClock.uptimeMillis() - fadeStart < FADE_MS;
    }

    /** The title of the photo on screen, or "". */
    String title() {
        return shown == null ? "" : shown.title;
    }

    void next() {
        List<Photos.Photo> list = photos.list();
        changedAt = SystemClock.uptimeMillis();
        if (list.isEmpty()) {
            photos.refreshIfStale();
            return;
        }
        index = (index + 1) % list.size();
        final Photos.Photo p = list.get(index);
        Background.run(() -> {
            final Bitmap b = Photos.load(p.file, (int) CanvasView.W, (int) CanvasView.H);
            if (b == null) return;
            b.prepareToDraw();
            Background.onMain(() -> {
                if (old != null && old != photo) old.recycle();
                old = photo;
                photo = b;
                shown = p;
                fadeStart = SystemClock.uptimeMillis();
                onChanged.run();
            });
        });
    }

    void draw(Canvas c) {
        if (photo == null) {
            c.drawColor(FALLBACK);
            return;
        }
        float f = Colors.clamp((SystemClock.uptimeMillis() - fadeStart) / (float) FADE_MS);
        if (old != null && f < 1) {
            image.setAlpha(255);
            c.drawBitmap(old, 0, 0, image);
        }
        image.setAlpha(Math.round(255 * f));
        c.drawBitmap(photo, 0, 0, image);
        image.setAlpha(255);
    }

    /** The fade is over: the previous photo's memory can go. */
    void releaseOld() {
        if (old != null) {
            old.recycle();
            old = null;
        }
    }
}
