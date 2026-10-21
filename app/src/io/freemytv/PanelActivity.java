package io.freemytv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * A translucent panel over whatever is on screen: a title and a list of actions (icon, label, optional detail).
 * D-pad up/down, OK runs the action, Back closes. Subclasses give the entries; the quick panel sits on the right,
 * the app menu in the middle.
 */
abstract class PanelActivity extends Activity {
    static final class Entry {
        final String symbol, label, detail;
        final Runnable action;
        final boolean dim;

        Entry(String symbol, String label, String detail, boolean dim, Runnable action) {
            this.symbol = symbol;
            this.label = label;
            this.detail = detail == null ? "" : detail;
            this.dim = dim;
            this.action = action;
        }
    }

    private PanelView view;

    abstract String title();

    String subtitle() {
        return "";
    }

    abstract List<Entry> entries();

    /** Right-hand side panel (quick panel) or centered (menus). */
    boolean onRight() {
        return false;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        Ui.init(this);
        view = new PanelView(this);
        setContentView(view);
    }

    @Override protected void onResume() {
        super.onResume();
        view.setEntries(entries());   // the state may have changed (an input plugged in, a favorite added)
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (view.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    /** Rebuilds the list (something it shows changed). */
    void refresh() {
        view.setEntries(entries());
    }

    /** Starts an activity from the panel; on failure says so instead of closing silently. */
    boolean launch(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, Ui.tr("Indisponível nesta TV.", "Not available on this TV."), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    static final class PanelView extends View implements Choreographer.FrameCallback {
        private static final float W = 1920, H = 1080, PANEL_W = 660, ROW_H = 92, ROW_GAP = 8, PAD = 48;

        private final PanelActivity activity;
        private final RectF panel = new RectF(), rect = new RectF();
        private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shade = new Paint(), titlePaint, subPaint;
        private final TextPaint label, detail;
        private final List<Spring> pop = new ArrayList<>();
        private final Spring scroll = new Spring(0.4f, 1f, 0), appear = new Spring(0.32f, 0.9f, 0);
        private List<Entry> entries = new ArrayList<>();
        private int focus;
        private boolean framePending, okDown, detached;
        private long lastNanos;
        private float k = 1;

        PanelView(PanelActivity a) {
            super(a);
            activity = a;
            setFocusable(true);
            glass.setColor(0xF0161820);
            glass.setShadowLayer(40, 0, 0, 0xCC000000);
            shade.setColor(0x80000000);
            titlePaint = Ui.text(Ui.displaySemibold, 44, Color.WHITE);
            subPaint = Ui.text(Ui.regular, 26, 0xB3FFFFFF);
            label = new TextPaint(Ui.text(Ui.medium, 32, Color.WHITE));
            detail = new TextPaint(Ui.text(Ui.regular, 24, 0x99FFFFFF));
            detail.setTextAlign(Paint.Align.RIGHT);
            appear.target = 1;
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            detached = false;
            retarget(false);
        }

        @Override protected void onDetachedFromWindow() {
            detached = true;
            super.onDetachedFromWindow();
        }

        void setEntries(List<Entry> list) {
            entries = list;
            while (pop.size() < list.size()) pop.add(new Spring(0.26f, 0.85f, 0));
            focus = Math.max(0, Math.min(focus, list.size() - 1));
            retarget(true);
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            k = width / W;
        }

        private float listTop() {
            return activity.subtitle().isEmpty() ? 150 : 190;
        }

        @Override protected void onDraw(Canvas c) {
            c.save();
            c.scale(k, k);
            float a = Ui.clamp(appear.value);
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
            if (!activity.subtitle().isEmpty()) {
                c.drawText(activity.subtitle(), panel.left + PAD, panel.top + 140, subPaint);
            }
            c.save();
            c.clipRect(panel.left, panel.top + listTop() - 20, panel.right, panel.bottom - 20);
            for (int i = 0; i < entries.size(); i++) drawEntry(c, i);
            c.restore();
            c.restore();
        }

        private void drawEntry(Canvas c, int i) {
            Entry e = entries.get(i);
            float y = panel.top + listTop() + i * (ROW_H + ROW_GAP) - scroll.value;
            if (y > panel.bottom || y + ROW_H < panel.top) return;
            float f = Ui.clamp(pop.get(i).value);
            rect.set(panel.left + 24, y, panel.right - 24, y + ROW_H);
            if (f > 0.01f) {
                fill.setColor(Ui.alpha(Color.WHITE, f));
                c.drawRoundRect(rect, 20, 20, fill);
            }
            int ink = f > 0.5f ? 0xFF15161A : Color.WHITE;
            float alpha = e.dim && f < 0.5f ? 0.5f : 1f;
            Symbols.draw(getContext(), c, e.symbol, rect.left + 52, rect.centerY(), 30, Ui.alpha(ink, alpha));
            label.setColor(Ui.alpha(ink, alpha));
            float right = rect.right - 28;
            if (!e.detail.isEmpty()) {
                detail.setColor(f > 0.5f ? 0x991C1C1E : 0x99FFFFFF);
                c.drawText(e.detail, right, rect.centerY() + Ui.halfCap(detail), detail);
                right -= detail.measureText(e.detail) + 24;
            }
            c.drawText(TextUtils.ellipsize(e.label, label, right - rect.left - 96, TextUtils.TruncateAt.END).toString(),
                    rect.left + 96, rect.centerY() + Ui.halfCap(label), label);
        }

        private void retarget(boolean snap) {
            for (int i = 0; i < pop.size(); i++) {
                Spring s = pop.get(i);
                s.target = i == focus ? 1 : 0;
                if (snap) s.snap(s.target);
            }
            float visible = (panel.height() > 0 ? panel.height() : H - 80) - listTop() - PAD;
            float y = focus * (ROW_H + ROW_GAP);
            scroll.target = Math.max(0, y + ROW_H - visible + ROW_H / 2);
            if (snap) scroll.snap(scroll.target);
            invalidate();
            if (!framePending) {
                framePending = true;
                lastNanos = 0;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }

        @Override public void doFrame(long nanos) {
            framePending = false;
            float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.05f, (nanos - lastNanos) / 1e9f);
            lastNanos = nanos;
            boolean moving = scroll.step(dt) | appear.step(dt);
            for (Spring s : pop) moving |= s.step(dt);
            invalidate();
            if (moving && !detached) {
                framePending = true;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }

        boolean onKey(KeyEvent e) {
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (down && focus > 0) { focus--; retarget(false); }
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down && focus < entries.size() - 1) { focus++; retarget(false); }
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (down) {
                        if (e.getRepeatCount() == 0) okDown = true;
                    } else {
                        if (okDown && !e.isCanceled() && focus < entries.size()) entries.get(focus).action.run();
                        okDown = false;
                    }
                    return true;
                case KeyEvent.KEYCODE_NOTIFICATION:
                case KeyEvent.KEYCODE_SETTINGS:
                    if (!down) activity.finish();   // the same button closes the panel
                    return true;
                default:
                    return false;   // Back closes (the system finishes the activity)
            }
        }
    }
}
