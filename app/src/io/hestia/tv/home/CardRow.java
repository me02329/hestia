package io.hestia.tv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;

import io.hestia.tv.apps.Card;
import io.hestia.tv.ui.Colors;
import io.hestia.tv.ui.FocusSprings;
import io.hestia.tv.ui.Fonts;
import io.hestia.tv.ui.Paints;
import io.hestia.tv.ui.Spring;
import io.hestia.tv.ui.Symbols;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** One row of 16:9 cards: its title, cards, focused index and horizontal scroll. The focused card grows and gets an outline. */
final class CardRow {
    static final float CARD_W = 320, CARD_H = 180, TITLE_GAP = 26;
    private static final float W = HomeView.W, MARGIN = HomeView.MARGIN;
    private static final float GAP = 36, RADIUS = 18, FOCUS_GROW = 0.1f;
    /** Cards fade out over this width at the screen edges instead of being cut. */
    private static final float EDGE_FADE = 160;

    /** What the rows share to draw: one set of paints. */
    static final class Style {
        private final Context context;
        private final Drawable logo;
        private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG), edgeMask = new Paint();
        private final Paint titlePaint;
        private final TextPaint cardLabel;
        private final RectF rect = new RectF();
        private final Path clip = new Path();
        private final Matrix cardMatrix = new Matrix();
        private final Map<Card.Kind, LinearGradient> shaders = new EnumMap<>(Card.Kind.class);

        Style(Context c, Drawable logo) {
            context = c;
            this.logo = logo;
            titlePaint = Paints.text(Fonts.semibold, 30, 0xF2FFFFFF);
            titlePaint.setShadowLayer(12, 0, 2, 0x99000000);
            cardLabel = new TextPaint(Paints.text(Fonts.medium, 26, Color.WHITE));
            cardLabel.setTextAlign(Paint.Align.CENTER);
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeWidth(5);
            outline.setColor(Color.WHITE);
            shadow.setColor(0xFF000000);
            shadow.setShadowLayer(30, 0, 14, 0xAA000000);
            edgeMask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        }

        /** Shortcut card colors: live TV coral, inputs blue, search violet, Hestia night (emblem), TV settings teal. */
        private LinearGradient shader(Card.Kind kind) {
            LinearGradient g = shaders.get(kind);
            if (g == null) {
                int from, to;
                switch (kind) {
                    case LIVE_TV: from = 0xFFF0625F; to = 0xFF9B1C3A; break;
                    case INPUT: from = 0xFF4F8CF7; to = 0xFF3B2FB8; break;
                    case HESTIA_SETTINGS: from = 0xFF2E2448; to = 0xFF120C1E; break;
                    case SEARCH: from = 0xFF8B6CFF; to = 0xFF2F5BD3; break;
                    default: from = 0xFF1FB5A3; to = 0xFF0E5470; break;
                }
                g = new LinearGradient(0, 0, 1, 1, from, to, Shader.TileMode.CLAMP);
                shaders.put(kind, g);
            }
            return g;
        }
    }

    final String title;
    final List<Card> cards;
    private final Style style;
    private final FocusSprings pop = new FocusSprings(0.28f, 0.82f);
    private final Spring scroll = new Spring(0.45f, 1f, 0);
    int index;

    CardRow(String title, List<Card> cards, Style style) {
        this.title = title;
        this.cards = cards;
        this.style = style;
        pop.resize(cards.size());
    }

    float cardX(int i) {
        return MARGIN + i * (CARD_W + GAP) - scroll.value;
    }

    /** The card closest to a horizontal position (moving between rows lands on the card underneath). */
    int cardAt(float x) {
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < cards.size(); i++) {
            float d = Math.abs(cardX(i) + CARD_W / 2 - x);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    /** Keeps the focused card (and a bit of its neighbour) inside the margins; `focused` when the focus is on this row. */
    void retarget(boolean focused, boolean snap) {
        index = Math.max(0, Math.min(cards.size() - 1, index));
        float x = MARGIN + index * (CARD_W + GAP);
        float target = scroll.target;
        if (x - target < MARGIN) target = x - MARGIN - (index > 0 ? CARD_W * 0.35f : 0);
        if (x + CARD_W - target > W - MARGIN) target = x + CARD_W - (W - MARGIN) + (index < cards.size() - 1 ? CARD_W * 0.35f : 0);
        float max = Math.max(0, MARGIN + cards.size() * (CARD_W + GAP) - GAP - (W - MARGIN));
        scroll.target = Math.max(0, Math.min(max, target));
        if (snap) scroll.snap(scroll.target);
        pop.focus(focused ? index : -1, snap);
    }

    boolean animate(float dt) {
        return scroll.step(dt) | pop.step(dt);
    }

    void draw(Canvas c, float top, boolean active) {
        style.titlePaint.setAlpha(active ? 255 : 190);
        c.drawText(title, MARGIN, top, style.titlePaint);
        float cardTop = top + TITLE_GAP;
        // The cards go into a layer whose edges are then faded (DST_IN), so they don't stop dead at the screen edge.
        float left = Colors.clamp(scroll.value / 80f);
        float overflow = MARGIN + cards.size() * (CARD_W + GAP) - GAP - scroll.value - (W - MARGIN);
        float right = Colors.clamp(overflow / 80f);
        boolean fade = left > 0 || right > 0;
        int layer = fade ? c.saveLayer(0, cardTop - 60, W, cardTop + CARD_H + 60, null) : -1;
        // Unfocused cards first, so the focused one (bigger) sits on top of its neighbours.
        for (int i = 0; i < cards.size(); i++) {
            if (i != index || !active) drawCard(c, i, cardTop);
        }
        if (active && index < cards.size()) drawCard(c, index, cardTop);
        if (fade) {
            Paint mask = style.edgeMask;
            if (left > 0) {
                mask.setShader(new LinearGradient(0, 0, EDGE_FADE, 0, Colors.alpha(0xFF000000, 1 - left), 0xFF000000,
                        Shader.TileMode.CLAMP));
                c.drawRect(0, cardTop - 60, EDGE_FADE, cardTop + CARD_H + 60, mask);
            }
            if (right > 0) {
                mask.setShader(new LinearGradient(W - EDGE_FADE, 0, W, 0, 0xFF000000, Colors.alpha(0xFF000000, 1 - right),
                        Shader.TileMode.CLAMP));
                c.drawRect(W - EDGE_FADE, cardTop - 60, W, cardTop + CARD_H + 60, mask);
            }
            c.restoreToCount(layer);
        }
    }

    private void drawCard(Canvas c, int i, float top) {
        float x = cardX(i);
        if (x > W || x + CARD_W < 0) return;
        Card card = cards.get(i);
        RectF rect = style.rect;
        float p = pop.value(i), s = 1 + FOCUS_GROW * pop.raw(i);
        float cx = x + CARD_W / 2, cy = top + CARD_H / 2;
        rect.set(cx - CARD_W * s / 2, cy - CARD_H * s / 2, cx + CARD_W * s / 2, cy + CARD_H * s / 2);
        if (p > 0.01f) {
            style.shadow.setAlpha(Math.round(255 * p));
            c.drawRoundRect(rect, RADIUS * s, RADIUS * s, style.shadow);
        }
        if (card.kind == Card.Kind.APP && card.tile != null) drawTile(c, card, rect, s, p); else drawShortcut(c, card, rect, s);
        if (p > 0.01f) {
            style.outline.setAlpha(Math.round(255 * p));
            c.drawRoundRect(rect, RADIUS * s, RADIUS * s, style.outline);
        }
    }

    private void drawTile(Canvas c, Card card, RectF rect, float s, float p) {
        c.save();
        style.clip.reset();
        style.clip.addRoundRect(rect, RADIUS * s, RADIUS * s, Path.Direction.CW);
        c.clipPath(style.clip);
        style.image.setAlpha(Math.round(255 * (0.82f + 0.18f * p)));
        c.drawBitmap(card.tile, null, rect, style.image);
        style.image.setAlpha(255);
        c.restore();
    }

    /** Colored card (diagonal gradient per kind); a unit-square shader moved onto the card, no allocation. */
    private void drawShortcut(Canvas c, Card card, RectF rect, float s) {
        float dim = card.kind == Card.Kind.INPUT && !card.connected ? 0.5f : 1f;
        style.cardMatrix.setScale(rect.width(), rect.height());
        style.cardMatrix.postTranslate(rect.left, rect.top);
        LinearGradient g = style.shader(card.kind);
        g.setLocalMatrix(style.cardMatrix);
        Paint fill = style.fill;
        fill.setShader(g);
        fill.setAlpha(Math.round(255 * dim));
        c.drawRoundRect(rect, RADIUS * s, RADIUS * s, fill);
        fill.setShader(null);
        fill.setAlpha(255);
        int ink = Colors.alpha(Color.WHITE, dim < 1 ? 0.7f : 1f);
        Drawable logo = style.logo;
        if (card.kind == Card.Kind.HESTIA_SETTINGS && logo != null) {
            float lh = 82 * s, lw = lh * logo.getIntrinsicWidth() / Math.max(1, logo.getIntrinsicHeight());
            logo.setBounds(Math.round(rect.centerX() - lw / 2), Math.round(rect.centerY() - 22 * s - lh / 2),
                    Math.round(rect.centerX() + lw / 2), Math.round(rect.centerY() - 22 * s + lh / 2));
            logo.setAlpha(255);
            logo.draw(c);
        } else {
            Symbols.draw(style.context, c, card.symbol, rect.centerX(), rect.centerY() - 24 * s, 52 * s, ink);
        }
        style.cardLabel.setColor(ink);
        style.cardLabel.setTextSize(26 * s);
        c.drawText(TextUtils.ellipsize(card.label, style.cardLabel, rect.width() - 24, TextUtils.TruncateAt.END).toString(),
                rect.centerX(), rect.centerY() + 52 * s, style.cardLabel);
    }
}
