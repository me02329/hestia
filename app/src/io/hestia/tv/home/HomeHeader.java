package io.hestia.tv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;

import io.hestia.tv.core.Config;
import io.hestia.tv.core.I18n;
import io.hestia.tv.core.TimeText;
import io.hestia.tv.data.News;
import io.hestia.tv.data.Weather;
import io.hestia.tv.data.WeatherText;
import io.hestia.tv.ui.Colors;
import io.hestia.tv.ui.Fonts;
import io.hestia.tv.ui.Paints;
import io.hestia.tv.ui.Spring;
import io.hestia.tv.ui.Symbols;

import java.util.ArrayList;
import java.util.List;

/**
 * The top of the home screen: top left the time and date, top center the emblem and name, top right the weather and the
 * photo's title; under the date, the latest cybersecurity headline, a new one every 12 s.
 */
final class HomeHeader {
    private static final float W = HomeView.W, MARGIN = HomeView.MARGIN;
    private static final long HEADLINE_MS = 600;
    private static final float LOGO_TOP = 44, LOGO_H = 132;

    private final Context context;
    private final Drawable logo;
    private final Weather weather;
    private final Spring newsPop = new Spring(0.28f, 0.85f, 0);
    private final RectF rect = new RectF();
    private List<News.Headline> headlines = new ArrayList<>();
    private int headline;
    private long headlineAt;

    private final Paint clockPaint, datePaint, tempPaint, pillText, sourcePaint, brandPaint, hintPaint;
    private final TextPaint smallPaint, headlinePaint;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG), newsScrim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logoHalo = new Paint();

    HomeHeader(Context c, Drawable logo, Weather weather) {
        context = c;
        this.logo = logo;
        this.weather = weather;
        clockPaint = Paints.numbers(Fonts.light, 128, Color.WHITE);
        datePaint = Paints.text(Fonts.medium, 32, 0xE6FFFFFF);
        tempPaint = Paints.numbers(Fonts.light, 64, Color.WHITE);
        tempPaint.setTextAlign(Paint.Align.RIGHT);
        smallPaint = new TextPaint(Paints.text(Fonts.regular, 26, 0xCCFFFFFF));
        smallPaint.setTextAlign(Paint.Align.RIGHT);
        pillText = Paints.text(Fonts.semibold, 20, Color.WHITE);
        pillText.setLetterSpacing(0.08f);
        sourcePaint = Paints.text(Fonts.medium, 24, 0xBFFFFFFF);
        headlinePaint = new TextPaint(Paints.text(Fonts.medium, 36, Color.WHITE));
        for (Paint p : new Paint[] {clockPaint, datePaint, tempPaint, smallPaint, sourcePaint, headlinePaint}) {
            p.setShadowLayer(12, 0, 2, 0x99000000);
        }
        pill.setColor(0xD9E5484D);
        hintPaint = Paints.text(Fonts.medium, 22, Color.WHITE);
        hintPaint.setTextAlign(Paint.Align.RIGHT);
        brandPaint = Paints.text(Fonts.semibold, 24, 0xFFE9C587);
        brandPaint.setTextAlign(Paint.Align.CENTER);
        brandPaint.setLetterSpacing(0.35f);
        brandPaint.setShadowLayer(12, 0, 2, 0xCC000000);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(5);
        outline.setColor(Color.WHITE);
        // Soft dark band behind the headline: white text stays readable on bright photos.
        newsScrim.setShader(new LinearGradient(0, 0, 1750, 0, new int[] {0xA6000000, 0x73000000, 0x00000000},
                new float[] {0, 0.6f, 1}, Shader.TileMode.CLAMP));
        newsScrim.setMaskFilter(new android.graphics.BlurMaskFilter(48, android.graphics.BlurMaskFilter.Blur.NORMAL));
        logoHalo.setShader(new RadialGradient(W / 2, LOGO_TOP + LOGO_H / 2, LOGO_H, 0x8C000000, 0x00000000,
                Shader.TileMode.CLAMP));
    }

    // MARK: - Headlines

    boolean hasHeadlines() {
        return !headlines.isEmpty();
    }

    String currentHeadlineTitle() {
        return headlines.isEmpty() ? null : headlines.get(headline).title;
    }

    /** New list: stays on the same headline if it's still there. */
    void setHeadlines(List<News.Headline> list) {
        String current = currentHeadlineTitle();
        headlines = list;
        headline = 0;
        for (int i = 0; i < list.size(); i++) if (list.get(i).title.equals(current)) headline = i;
    }

    /** Next headline; false when there's only one. */
    boolean nextHeadline() {
        if (headlines.size() <= 1) return false;
        headline = (headline + 1) % headlines.size();
        headlineAt = SystemClock.uptimeMillis();
        return true;
    }

    void setNewsFocus(boolean focused, boolean snap) {
        newsPop.target = focused ? 1 : 0;
        if (snap) newsPop.snap(newsPop.target);
    }

    boolean animate(float dt) {
        return newsPop.step(dt) | SystemClock.uptimeMillis() - headlineAt < HEADLINE_MS;
    }

    // MARK: - Drawing

    void draw(Canvas c, float alpha, String photoTitle) {
        if (alpha <= 0.01f) return;
        int a = Math.round(255 * alpha);
        long now = System.currentTimeMillis();
        clockPaint.setAlpha(a);
        datePaint.setAlpha(Math.round(0.9f * a));
        String time = TimeText.clock(now);
        c.drawText(time, MARGIN - Paints.leftBearing(clockPaint, time), 180, clockPaint);
        c.drawText(TimeText.dateLine(now), MARGIN, 236, datePaint);

        drawHeadline(c, alpha);
        drawBrand(c, alpha);

        Weather.Reading wx = weather.now();
        float right = W - MARGIN;
        smallPaint.setAlpha(Math.round(0.8f * a));
        if (!Double.isNaN(wx.temp)) {
            tempPaint.setAlpha(a);
            String temp = WeatherText.degrees(wx.temp, Config.TEMPERATURE);
            c.drawText(temp, right, 150, tempPaint);
            float iconX = right - tempPaint.measureText(temp) - 44;
            Symbols.draw(context, c, Symbols.weather(wx.code, wx.isDay), iconX, 150 - Paints.halfCap(tempPaint),
                    56, Colors.alpha(Color.WHITE, alpha));
            String where = Config.CITY.trim();
            String line = WeatherText.condition(wx.code, wx.isDay);
            if (!where.isEmpty()) line = where + " · " + line;
            c.drawText(line, right, 196, smallPaint);
        }
        if (!photoTitle.isEmpty()) {
            smallPaint.setAlpha(Math.round(0.6f * a));
            c.drawText(TextUtils.ellipsize(photoTitle, smallPaint, 700, TextUtils.TruncateAt.END).toString(), right, 236, smallPaint);
        }
    }

    /** The emblem (vector, transparent) over a soft shadow, and the name in spaced gold capitals. */
    private void drawBrand(Canvas c, float alpha) {
        if (logo == null) return;
        logoHalo.setAlpha(Math.round(255 * alpha));
        c.drawRect(W / 2 - LOGO_H, LOGO_TOP - LOGO_H / 2, W / 2 + LOGO_H, LOGO_TOP + LOGO_H * 1.5f, logoHalo);
        float lw = LOGO_H * logo.getIntrinsicWidth() / Math.max(1, logo.getIntrinsicHeight());
        logo.setBounds(Math.round(W / 2 - lw / 2), Math.round(LOGO_TOP), Math.round(W / 2 + lw / 2), Math.round(LOGO_TOP + LOGO_H));
        logo.setAlpha(Math.round(255 * alpha));
        logo.draw(c);
        brandPaint.setAlpha(Math.round(255 * alpha));
        c.drawText("HESTIA", W / 2 + 4, LOGO_TOP + LOGO_H + 38, brandPaint);   // + 4: the trailing letter spacing
    }

    /** The current headline sliding up over the previous one. */
    private void drawHeadline(Canvas c, float alpha) {
        if (headlines.isEmpty()) return;
        newsScrim.setAlpha(Math.round(255 * alpha));
        c.drawRoundRect(-60, 262, 1750, 404, 60, 60, newsScrim);
        float t = Colors.clamp((SystemClock.uptimeMillis() - headlineAt) / (float) HEADLINE_MS);
        t = t * t * (3 - 2 * t);
        String label = "CYBER";
        float pw = pillText.measureText(label) + 28;
        rect.set(MARGIN, 284, MARGIN + pw, 320);
        pill.setAlpha(Math.round(217 * alpha));
        c.drawRoundRect(rect, 18, 18, pill);
        pillText.setAlpha(Math.round(255 * alpha));
        c.drawText(label, MARGIN + 14, rect.centerY() + Paints.halfCap(pillText), pillText);
        float f = Colors.clamp(newsPop.value);
        if (f > 0.01f) {
            float tw = Math.min(W - 2 * MARGIN, headlinePaint.measureText(headlines.get(headline).title));
            rect.set(MARGIN - 28, 262, MARGIN + Math.max(tw, 760) + 28, 398);
            fill.setColor(Colors.alpha(0x40FFFFFF, f));
            c.drawRoundRect(rect, 26, 26, fill);
            outline.setAlpha(Math.round(255 * f));
            c.drawRoundRect(rect, 26, 26, outline);
            hintPaint.setAlpha(Math.round(230 * f * alpha));
            c.drawText(I18n.tr("OK · todas as notícias", "OK · all headlines"), rect.right - 24, 310, hintPaint);
        }
        int previous = (headline - 1 + headlines.size()) % headlines.size();
        if (t < 1 && headlines.size() > 1) drawHeadlineText(c, headlines.get(previous), MARGIN + pw + 16, -24 * t, (1 - t) * alpha);
        drawHeadlineText(c, headlines.get(headline), MARGIN + pw + 16, 24 * (1 - t), t * alpha);
    }

    private void drawHeadlineText(Canvas c, News.Headline h, float sourceX, float dy, float alpha) {
        if (alpha <= 0.01f) return;
        sourcePaint.setAlpha(Math.round(191 * alpha));
        c.drawText(h.source + " · " + News.age(h.at), sourceX, 302 + Paints.halfCap(sourcePaint) + dy, sourcePaint);
        headlinePaint.setAlpha(Math.round(255 * alpha));
        c.drawText(TextUtils.ellipsize(h.title, headlinePaint, W - 2 * MARGIN, TextUtils.TruncateAt.END).toString(),
                MARGIN, 372 + dy, headlinePaint);
    }
}
