package io.hestia.tv.panel;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;

import io.hestia.tv.core.Keys;
import io.hestia.tv.ui.CanvasView;
import io.hestia.tv.ui.Colors;
import io.hestia.tv.ui.FocusSprings;
import io.hestia.tv.ui.Fonts;
import io.hestia.tv.ui.Paints;
import io.hestia.tv.ui.Press;
import io.hestia.tv.ui.Spring;
import io.hestia.tv.ui.Symbols;

import java.util.ArrayList;
import java.util.List;

/** Draws a PanelActivity and moves through it. */
final class PanelView extends CanvasView {
    private static final float PANEL_W = 660, ROW_H = 92, ROW_GAP = 8, PAD = 48;

    private final PanelActivity activity;
    private final RectF panel = new RectF(), rect = new RectF();
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(), titlePaint, subPaint;
    private final TextPaint label, detail;
    private final FocusSprings pop = new FocusSprings(0.26f, 0.85f);
    private final Spring scroll = new Spring(0.4f, 1f, 0), appear = new Spring(0.32f, 0.9f, 0);
    private final Press ok = new Press();
    private List<PanelEntry> entries = new ArrayList<>();
    private int focus, headerFocus = 1;   // focus -1: the header's buttons
    private boolean hadHeader;

    PanelView(PanelActivity a) {
        super(a);
        activity = a;
        glass.setColor(0xF0161820);
        glass.setShadowLayer(40, 0, 0, 0xCC000000);
        shade.setColor(0x80000000);
        titlePaint = Paints.text(Fonts.displaySemibold, 44, Color.WHITE);
        subPaint = Paints.text(Fonts.regular, 26, 0xB3FFFFFF);
        label = new TextPaint(Paints.text(Fonts.medium, 32, Color.WHITE));
        detail = new TextPaint(Paints.text(Fonts.regular, 24, 0x99FFFFFF));
        detail.setTextAlign(Paint.Align.RIGHT);
        appear.target = 1;
    }

    private int headerButtons() {
        PanelHeader header = activity.header();
        return header == null ? 0 : header.buttons();
    }

    private float headerHeight() {
        PanelHeader header = activity.header();
        return header == null ? 0 : header.height();
    }

    void setEntries(List<PanelEntry> list) {
        entries = list;
        pop.resize(list.size());
        int buttons = headerButtons();
        boolean header = buttons > 0;
        if (header && !hadHeader) focus = -1;   // a player appeared: start on play/pause
        hadHeader = header;
        focus = Math.max(header ? -1 : 0, Math.min(focus, list.size() - 1));
        headerFocus = Math.min(headerFocus, Math.max(0, buttons - 1));
        retarget(true);
    }

    void focusEntry(int index) {
        focus = Math.max(0, Math.min(index, entries.size() - 1));
        retarget(false);
    }

    private float headerTop() {
        return activity.subtitle().isEmpty() ? 150 : 190;
    }

    private float listTop() {
        return headerTop() + headerHeight();
    }

    @Override protected void drawScene(Canvas c) {
        float a = Colors.clamp(appear.value);
        shade.setAlpha(Math.round(128 * a));
        c.drawRect(0, 0, W, H, shade);
        float h = Math.min(H - 80, listTop() + entries.size() * (ROW_H + ROW_GAP) + PAD);
        if (activity.onRight()) {
            float x = W - PANEL_W - 40 + (1 - a) * 120;
            panel.set(x, 40, x + PANEL_W, H - 40);
        } else {
            panel.set((W - PANEL_W) / 2, (H - h) / 2 + (1 - a) * 60, (W + PANEL_W) / 2, (H + h) / 2 + (1 - a) * 60);
        }
        glass.setAlpha(Math.round(240 * a));
        c.drawRoundRect(panel, 36, 36, glass);
        c.drawText(TextUtils.ellipsize(activity.title(), new TextPaint(titlePaint), PANEL_W - 2 * PAD,
                TextUtils.TruncateAt.END).toString(), panel.left + PAD, panel.top + 92, titlePaint);
        String subtitle = activity.subtitle();
        if (!subtitle.isEmpty()) c.drawText(subtitle, panel.left + PAD, panel.top + 140, subPaint);
        PanelHeader header = activity.header();
        if (header != null && header.height() > 0) {
            rect.set(panel.left + 24, panel.top + headerTop(), panel.right - 24,
                    panel.top + headerTop() + header.height() - 24);
            header.draw(c, rect, focus == -1 ? headerFocus : -1);
        }
        c.save();
        c.clipRect(panel.left, panel.top + listTop() - 20, panel.right, panel.bottom - 20);
        for (int i = 0; i < entries.size(); i++) drawEntry(c, i);
        c.restore();
    }

    private void drawEntry(Canvas c, int i) {
        PanelEntry e = entries.get(i);
        float y = panel.top + listTop() + i * (ROW_H + ROW_GAP) - scroll.value;
        if (y > panel.bottom || y + ROW_H < panel.top) return;
        float f = pop.value(i);
        rect.set(panel.left + 24, y, panel.right - 24, y + ROW_H);
        if (f > 0.01f) {
            fill.setColor(Colors.alpha(Color.WHITE, f));
            c.drawRoundRect(rect, 20, 20, fill);
        }
        int ink = f > 0.5f ? 0xFF15161A : Color.WHITE;
        float alpha = e.dim && f < 0.5f ? 0.5f : 1f;
        Symbols.draw(getContext(), c, e.symbol, rect.left + 52, rect.centerY(), 30, Colors.alpha(ink, alpha));
        label.setColor(Colors.alpha(ink, alpha));
        float right = rect.right - 28;
        if (!e.detail.isEmpty()) {
            detail.setColor(f > 0.5f ? 0x991C1C1E : 0x99FFFFFF);
            c.drawText(e.detail, right, rect.centerY() + Paints.halfCap(detail), detail);
            right -= detail.measureText(e.detail) + 24;
        }
        c.drawText(TextUtils.ellipsize(e.label, label, right - rect.left - 96, TextUtils.TruncateAt.END).toString(),
                rect.left + 96, rect.centerY() + Paints.halfCap(label), label);
    }

    private void retarget(boolean snap) {
        pop.focus(focus, snap);
        float visible = (panel.height() > 0 ? panel.height() : H - 80) - listTop() - PAD;
        float y = Math.max(0, focus) * (ROW_H + ROW_GAP);
        scroll.target = Math.max(0, y + ROW_H - visible + ROW_H / 2);
        if (snap) scroll.snap(scroll.target);
        wake();
    }

    @Override protected boolean animate(float dt) {
        return scroll.step(dt) | appear.step(dt) | pop.step(dt);
    }

    boolean onKey(KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        int code = e.getKeyCode();
        if (Keys.isOk(code)) {
            if (ok.track(e) == Press.CLICK) {
                if (focus == -1) activity.header().press(headerFocus);
                else if (focus < entries.size()) entries.get(focus).action.run();
            }
            return true;
        }
        if (Keys.isSettings(code)) {
            if (!down) activity.finish();   // the same button closes the panel
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down && focus > (headerButtons() > 0 ? -1 : 0)) { focus--; retarget(false); }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down && focus < entries.size() - 1) { focus++; retarget(false); }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down && focus == -1) {
                    int dx = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                    headerFocus = Math.max(0, Math.min(headerButtons() - 1, headerFocus + dx));
                    invalidate();
                }
                return true;
            default:
                return false;   // Back closes (the system finishes the activity)
        }
    }
}
