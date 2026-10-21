package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.View;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The screensaver: the photos of the day (Photos) in full screen, one every 45 s, with a slow zoom and pan and a
 * crossfade; bottom left the time, date and weather (above them, one cyber headline per photo when the headlines are
 * on), bottom right the photo's title and credit. The text drifts a
 * little every minute (no burn-in). Without photos yet: the time on black until the first ones arrive. Any key exits.
 */
final class DreamView extends View implements Choreographer.FrameCallback, Photos.Listener, HomeData.Listener {
    private static final float W = 1920, H = 1080, MARGIN = 96, ZOOM = 0.08f;
    private static final long SHOW_MS = 45_000, FADE_MS = 2_500;

    private final Photos photos;
    private final News news;
    private final boolean newsOn;
    private final HomeData data;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();

    private Bitmap current, previous;
    private Photos.Photo currentInfo;
    private long currentStart, previousStart;
    private int index = -1, slides;
    private boolean running, loading;
    private float k = 1;

    private final Matrix matrix = new Matrix();
    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint scrim = new Paint();
    private final Paint clock, date;
    private final TextPaint title, credit, headline;
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG), pillText;

    private final Runnable advance = new Runnable() {
        @Override public void run() {
            if (!running) return;
            data.refreshIfStale();
            if (newsOn) news.refreshIfStale();
            photos.refreshIfStale();
            next();
            handler.postDelayed(this, SHOW_MS);
        }
    };

    DreamView(Context c) {
        super(c);
        photos = Photos.get(c);
        news = News.get(c);
        newsOn = News.enabled(c);
        data = HomeData.get(c);
        clock = Ui.numbers(Ui.light, 112, Color.WHITE);
        date = Ui.text(Ui.medium, 30, 0xE6FFFFFF);
        title = new TextPaint(Ui.text(Ui.medium, 28, 0xF2FFFFFF));
        title.setTextAlign(Paint.Align.RIGHT);
        credit = new TextPaint(Ui.text(Ui.regular, 22, 0x99FFFFFF));
        credit.setTextAlign(Paint.Align.RIGHT);
        headline = new TextPaint(Ui.text(Ui.medium, 30, 0xF2FFFFFF));
        headline.setShadowLayer(14, 0, 2, 0xAA000000);
        pillText = Ui.text(Ui.semibold, 18, Color.WHITE);
        pillText.setLetterSpacing(0.08f);
        pill.setColor(0xD9E5484D);
        for (Paint p : new Paint[] {clock, date, title, credit}) p.setShadowLayer(14, 0, 2, 0xAA000000);
        scrim.setShader(new LinearGradient(0, H * 0.55f, 0, H, 0x00000000, 0xB3000000, Shader.TileMode.CLAMP));
    }

    void start() {
        if (running) return;
        running = true;
        photos.addListener(this);
        data.addListener(this);
        handler.post(advance);
        Choreographer.getInstance().postFrameCallback(this);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(advance);
        photos.removeListener(this);
        data.removeListener(this);
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override public void onPhotos() {
        if (current == null) next();
    }

    @Override public void onData() { invalidate(); }

    private void next() {
        List<Photos.Photo> list = photos.list();
        if (list.isEmpty() || loading) return;
        index = (index + 1) % list.size();
        final Photos.Photo p = list.get(index);
        loading = true;
        LOADER.execute(() -> {
            final Bitmap b = Photos.load(p.file, (int) W, (int) H);
            if (b != null) b.prepareToDraw();
            handler.post(() -> {
                loading = false;
                if (b == null) return;
                if (!running) {
                    b.recycle();
                    return;
                }
                if (previous != null) previous.recycle();
                previous = current;
                previousStart = currentStart;
                current = b;
                currentInfo = p;
                currentStart = SystemClock.uptimeMillis();
                slides++;
            });
        });
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        k = width / W;
    }

    @Override public void doFrame(long nanos) {
        if (!running) return;
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDraw(Canvas c) {
        c.drawColor(Color.BLACK);
        c.save();
        c.scale(k, k);
        long now = SystemClock.uptimeMillis();
        float fade = Ui.clamp((now - currentStart) / (float) FADE_MS);
        if (previous != null && fade < 1) drawSlide(c, previous, previousStart, slides - 1, 1);
        if (current != null) drawSlide(c, current, currentStart, slides, fade);
        if (previous != null && fade >= 1) {
            previous.recycle();
            previous = null;
        }
        c.drawRect(0, H * 0.55f, W, H, scrim);
        drawText(c);
        c.restore();
    }

    /** Ken Burns: zooms in or out by ZOOM over the slide's time on screen, toward alternating corners. */
    private void drawSlide(Canvas c, Bitmap b, long start, int n, float alpha) {
        float t = Ui.clamp((SystemClock.uptimeMillis() - start) / (float) (SHOW_MS + FADE_MS));
        float zoom = 1 + ZOOM * (n % 2 == 0 ? t : 1 - t);
        float px = (n / 2) % 2 == 0 ? 0.3f : 0.7f, py = n % 3 == 0 ? 0.35f : 0.65f;
        matrix.setScale(zoom, zoom, W * px, H * py);
        image.setAlpha(Math.round(255 * alpha));
        c.drawBitmap(b, matrix, image);
    }

    private void drawText(Canvas c) {
        long wall = System.currentTimeMillis();
        // A different few-pixel offset every minute, so nothing stays lit in the same place for hours.
        long minute = wall / 60_000;
        float dx = (float) (Math.sin(minute * 1.7) * 18), dy = (float) (Math.cos(minute * 2.3) * 12);
        float x = MARGIN + dx, base = H - 150 + dy;
        String time = Ui.clock(wall);
        c.drawText(time, x - Ui.leftBearing(clock, time), base, clock);
        String line = Ui.date(wall);
        line = Character.toUpperCase(line.charAt(0)) + line.substring(1);
        HomeData.Weather wx = data.weather;
        if (!Double.isNaN(wx.temp)) line += "  ·  " + HomeData.degrees(wx.temp) + " " + HomeData.condition(wx.code, wx.isDay);
        c.drawText(line, x, base + 56, date);
        List<News.Headline> heads = newsOn ? news.recent() : null;
        if (heads != null && !heads.isEmpty()) {
            News.Headline h = heads.get(Math.max(0, slides) % heads.size());
            float hy = base - 128, pw = pillText.measureText("CYBER") + 24;
            c.drawRoundRect(x, hy - 26, x + pw, hy + 6, 16, 16, pill);
            c.drawText("CYBER", x + 12, hy - 10 + Ui.halfCap(pillText), pillText);
            c.drawText(TextUtils.ellipsize(h.title, headline, 1000, TextUtils.TruncateAt.END).toString(),
                    x + pw + 16, hy + 1, headline);
        }
        if (currentInfo != null) {
            float right = W - MARGIN - dx;
            c.drawText(TextUtils.ellipsize(currentInfo.title, title, 760, TextUtils.TruncateAt.END).toString(),
                    right, base + 12, title);
            c.drawText(TextUtils.ellipsize(currentInfo.credit, credit, 900, TextUtils.TruncateAt.END).toString(),
                    right, base + 56, credit);
        }
    }
}
