package io.hestia.tv.dream;

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

import io.hestia.tv.core.Background;
import io.hestia.tv.core.Config;
import io.hestia.tv.core.Prefs;
import io.hestia.tv.core.TimeText;
import io.hestia.tv.data.Feed;
import io.hestia.tv.data.News;
import io.hestia.tv.data.Photos;
import io.hestia.tv.data.Weather;
import io.hestia.tv.data.WeatherText;
import io.hestia.tv.ui.CanvasView;
import io.hestia.tv.ui.Colors;
import io.hestia.tv.ui.Fonts;
import io.hestia.tv.ui.Paints;

import java.util.List;

/**
 * The screensaver: the photos of the day (Photos) in full screen, one every 45 s, with a slow zoom and pan and a
 * crossfade; bottom left the time, date and weather (above them, one cyber headline per photo when the headlines are
 * on), bottom right the photo's title and credit. The text drifts a
 * little every minute (no burn-in). Without photos yet: the time on black until the first ones arrive. Any key exits.
 */
public final class DreamView extends CanvasView {
    private static final float MARGIN = 96, ZOOM = 0.08f;
    private static final long SHOW_MS = 45_000, FADE_MS = 2_500;

    private final Photos photos;
    private final News news;
    private final boolean newsOn;
    private final Weather weather;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Bitmap current, previous;
    private Photos.Photo currentInfo;
    private long currentStart, previousStart;
    private int index = -1, slides;
    private boolean running, loading;

    private final Matrix matrix = new Matrix();
    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint scrim = new Paint();
    private final Paint clock, date;
    private final TextPaint title, credit, headline;
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG), pillText;

    private final Runnable advance = new Runnable() {
        @Override public void run() {
            if (!running) return;
            weather.refreshIfStale();
            if (newsOn) news.refreshIfStale();
            photos.refreshIfStale();
            next();
            handler.postDelayed(this, SHOW_MS);
        }
    };

    private final Feed.Listener photoListener = () -> {
        if (current == null) next();
    };
    private final Feed.Listener weatherListener = this::invalidate;

    public DreamView(Context c) {
        super(c);
        photos = Photos.get(c);
        news = News.get(c);
        newsOn = Prefs.newsEnabled(c);
        weather = Weather.get(c);
        clock = Paints.numbers(Fonts.light, 112, Color.WHITE);
        date = Paints.text(Fonts.medium, 30, 0xE6FFFFFF);
        title = new TextPaint(Paints.text(Fonts.medium, 28, 0xF2FFFFFF));
        title.setTextAlign(Paint.Align.RIGHT);
        credit = new TextPaint(Paints.text(Fonts.regular, 22, 0x99FFFFFF));
        credit.setTextAlign(Paint.Align.RIGHT);
        headline = new TextPaint(Paints.text(Fonts.medium, 30, 0xF2FFFFFF));
        headline.setShadowLayer(14, 0, 2, 0xAA000000);
        pillText = Paints.text(Fonts.semibold, 18, Color.WHITE);
        pillText.setLetterSpacing(0.08f);
        pill.setColor(0xD9E5484D);
        for (Paint p : new Paint[] {clock, date, title, credit}) p.setShadowLayer(14, 0, 2, 0xAA000000);
        scrim.setShader(new LinearGradient(0, H * 0.55f, 0, H, 0x00000000, 0xB3000000, Shader.TileMode.CLAMP));
    }

    public void start() {
        if (running) return;
        running = true;
        photos.addListener(photoListener);
        weather.addListener(weatherListener);
        handler.post(advance);
        wake();
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(advance);
        photos.removeListener(photoListener);
        weather.removeListener(weatherListener);
    }

    private void next() {
        List<Photos.Photo> list = photos.list();
        if (list.isEmpty() || loading) return;
        index = (index + 1) % list.size();
        final Photos.Photo p = list.get(index);
        loading = true;
        Background.run(() -> {
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

    @Override protected boolean animate(float dt) {
        return running;
    }

    @Override protected void drawScene(Canvas c) {
        c.drawColor(Color.BLACK);
        long now = SystemClock.uptimeMillis();
        float fade = Colors.clamp((now - currentStart) / (float) FADE_MS);
        if (previous != null && fade < 1) drawSlide(c, previous, previousStart, slides - 1, 1);
        if (current != null) drawSlide(c, current, currentStart, slides, fade);
        if (previous != null && fade >= 1) {
            previous.recycle();
            previous = null;
        }
        c.drawRect(0, H * 0.55f, W, H, scrim);
        drawText(c);
    }

    /** Ken Burns: zooms in or out by ZOOM over the slide's time on screen, toward alternating corners. */
    private void drawSlide(Canvas c, Bitmap b, long start, int n, float alpha) {
        float t = Colors.clamp((SystemClock.uptimeMillis() - start) / (float) (SHOW_MS + FADE_MS));
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
        String time = TimeText.clock(wall);
        c.drawText(time, x - Paints.leftBearing(clock, time), base, clock);
        String line = TimeText.dateLine(wall);
        Weather.Reading wx = weather.now();
        if (!Double.isNaN(wx.temp)) {
            line += "  ·  " + WeatherText.degrees(wx.temp, Config.TEMPERATURE) + " " + WeatherText.condition(wx.code, wx.isDay);
        }
        c.drawText(line, x, base + 56, date);
        List<News.Headline> heads = newsOn ? news.recent() : null;
        if (heads != null && !heads.isEmpty()) {
            News.Headline h = heads.get(Math.max(0, slides) % heads.size());
            float hy = base - 128, pw = pillText.measureText("CYBER") + 24;
            c.drawRoundRect(x, hy - 26, x + pw, hy + 6, 16, 16, pill);
            c.drawText("CYBER", x + 12, hy - 10 + Paints.halfCap(pillText), pillText);
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
