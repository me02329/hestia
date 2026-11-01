package io.hestia.tv.news;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;

import io.hestia.tv.core.I18n;
import io.hestia.tv.core.Keys;
import io.hestia.tv.data.FeedText;
import io.hestia.tv.data.News;
import io.hestia.tv.ui.Backdrop;
import io.hestia.tv.ui.CanvasView;
import io.hestia.tv.ui.Colors;
import io.hestia.tv.ui.FocusSprings;
import io.hestia.tv.ui.Fonts;
import io.hestia.tv.ui.Paints;
import io.hestia.tv.ui.Press;
import io.hestia.tv.ui.Spring;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Every cybersecurity headline fetched (News), newest first, drawn like the settings: the photo of the day, darkened,
 * behind a list. D-pad up/down, OK hands the link to the screen, Back leaves.
 */
public final class NewsView extends CanvasView {
    private static final float MARGIN = 120, TOP = 250, ROW_H = 128, ROW_GAP = 12;

    private final Consumer<String> openLink;
    private final List<News.Headline> items;
    private final FocusSprings pop = new FocusSprings(0.28f, 0.82f);
    private final Spring scroll = new Spring(0.45f, 1f, 0);
    private final Press ok = new Press();
    private final Backdrop backdrop = new Backdrop();
    private final RectF rect = new RectF(), pillRect = new RectF();
    private int focus;

    private final Paint scrim = new Paint(), fill = new Paint(Paint.ANTI_ALIAS_FLAG), shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint heading, sub, pillText;
    private final TextPaint title, meta, footer;

    public NewsView(Context c, String current, Consumer<String> openLink) {
        super(c);
        this.openLink = openLink;
        items = News.get(c).recent();
        pop.resize(items.size());
        for (int i = 0; i < items.size(); i++) if (items.get(i).title.equals(current)) focus = i;
        heading = Paints.text(Fonts.displaySemibold, 56, Color.WHITE);
        sub = Paints.text(Fonts.regular, 28, 0xBFFFFFFF);
        pillText = Paints.text(Fonts.semibold, 20, Color.WHITE);
        pillText.setLetterSpacing(0.08f);
        title = new TextPaint(Paints.text(Fonts.medium, 34, Color.WHITE));
        meta = new TextPaint(Paints.text(Fonts.regular, 24, 0xB3FFFFFF));
        footer = new TextPaint(Paints.text(Fonts.regular, 26, 0xBFFFFFFF));
        pill.setColor(0xFFE5484D);
        shadow.setColor(Color.BLACK);
        shadow.setShadowLayer(28, 0, 12, 0x99000000);
        scrim.setShader(new LinearGradient(0, 0, 0, H, 0xD9000000, 0xF2000000, Shader.TileMode.CLAMP));
        backdrop.load(c, this);
        retarget(true);
    }

    @Override protected void drawScene(Canvas c) {
        backdrop.draw(c);
        c.drawRect(0, 0, W, H, scrim);
        c.drawText(I18n.tr("Notícias de cibersegurança", "Cyber news"), MARGIN, 140, heading);
        c.drawText(items.size() + I18n.tr(" manchetes · OK abre no navegador · Voltar para sair",
                " headlines · OK opens in the browser · Back to leave"), MARGIN, 196, sub);
        if (items.isEmpty()) {
            c.drawText(I18n.tr("Nenhuma notícia por enquanto.", "No headlines yet."), MARGIN, TOP + 60, sub);
        }
        c.save();
        c.clipRect(0, TOP - 30, W, H - 90);
        for (int i = 0; i < items.size(); i++) drawItem(c, i);
        c.restore();
        if (focus < items.size() && !items.get(focus).link.isEmpty()) {
            c.drawText(TextUtils.ellipsize("↗ " + FeedText.domain(items.get(focus).link), footer, W - 2 * MARGIN,
                    TextUtils.TruncateAt.END).toString(), MARGIN, H - 46, footer);
        }
    }

    private void drawItem(Canvas c, int i) {
        float y = TOP + i * (ROW_H + ROW_GAP) - scroll.value;
        if (y > H || y + ROW_H < TOP - 30) return;
        News.Headline h = items.get(i);
        float f = pop.value(i);
        float s = 1 + 0.015f * f, cx = W / 2, cy = y + ROW_H / 2, half = (W - 2 * MARGIN) / 2;
        rect.set(cx - half * s, cy - ROW_H * s / 2, cx + half * s, cy + ROW_H * s / 2);
        if (f > 0.01f) {
            shadow.setAlpha(Math.round(255 * f));
            c.drawRoundRect(rect, 22, 22, shadow);
        }
        fill.setColor(Colors.blend(0x26FFFFFF, 0xFFFFFFFF, f));
        c.drawRoundRect(rect, 22, 22, fill);
        float x = rect.left + 32;
        String source = h.source.toUpperCase(Locale.ROOT);
        float pw = pillText.measureText(source) + 24;
        pillRect.set(x, rect.top + 20, x + pw, rect.top + 52);
        c.drawRoundRect(pillRect, 16, 16, pill);
        c.drawText(source, x + 12, pillRect.centerY() + Paints.halfCap(pillText), pillText);
        meta.setColor(Colors.blend(0xB3FFFFFF, 0x991C1C1E, f));
        c.drawText(News.age(h.at), pillRect.right + 16, pillRect.centerY() + Paints.halfCap(meta), meta);
        title.setColor(Colors.blend(0xFFFFFFFF, 0xFF15161A, f));
        c.drawText(TextUtils.ellipsize(h.title, title, rect.width() - 64, TextUtils.TruncateAt.END).toString(),
                x, rect.bottom - 26, title);
    }

    private void retarget(boolean snap) {
        pop.focus(focus, snap);
        float visible = (H - 90 - TOP) / (ROW_H + ROW_GAP);
        scroll.target = Math.max(0, (focus - (int) visible + 2) * (ROW_H + ROW_GAP));
        if (snap) scroll.snap(scroll.target);
        wake();
    }

    @Override protected boolean animate(float dt) {
        return scroll.step(dt) | pop.step(dt);
    }

    public boolean onKey(KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        int code = e.getKeyCode();
        if (Keys.isOk(code)) {
            if (ok.track(e) == Press.CLICK && focus < items.size()) openLink.accept(items.get(focus).link);
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down && focus > 0) { focus--; retarget(false); }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down && focus < items.size() - 1) { focus++; retarget(false); }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            default:
                return false;   // Back leaves (the system finishes the activity)
        }
    }
}
