package io.hestia.tv.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import io.hestia.tv.core.Background;
import io.hestia.tv.data.Photos;

import java.util.List;

/** The photo of the day behind a list screen (settings, news); the home color until it's loaded. */
public final class Backdrop {
    private static final int FALLBACK = 0xFF0E1420;

    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private Bitmap photo;

    /** Loads it once, off the main thread, and redraws the view when it's ready. */
    public void load(Context c, View view) {
        List<Photos.Photo> list = Photos.get(c).list();
        if (photo != null || list.isEmpty()) return;
        final Photos.Photo p = list.get(0);
        Background.run(() -> {
            final Bitmap b = Photos.load(p.file, (int) CanvasView.W, (int) CanvasView.H);
            Background.onMain(() -> {
                photo = b;
                view.invalidate();
            });
        });
    }

    public void draw(Canvas c) {
        if (photo != null) c.drawBitmap(photo, 0, 0, image); else c.drawColor(FALLBACK);
    }
}
