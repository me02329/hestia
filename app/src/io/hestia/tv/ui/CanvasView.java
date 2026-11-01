package io.hestia.tv.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Choreographer;
import android.view.View;

/**
 * A screen drawn by hand on a 1920 × 1080 canvas, scaled to the display. Subclasses draw in drawScene and move their
 * springs in animate; this runs the frame loop, which only spins while something moves and stops off-screen.
 */
public abstract class CanvasView extends View implements Choreographer.FrameCallback {
    public static final float W = 1920, H = 1080;

    private boolean attached, framePending;
    private long lastNanos;
    /** Screen pixels per design pixel. */
    protected float scale = 1;

    protected CanvasView(Context c) {
        super(c);
        setFocusable(true);
    }

    protected abstract void drawScene(Canvas c);

    /** Advances everything by dt seconds; true while something still moves. */
    protected abstract boolean animate(float dt);

    /** Everything stopped moving. */
    protected void onSettled() {
    }

    /** Something changed: redraw now and run the frame loop until it settles. */
    protected final void wake() {
        invalidate();
        if (framePending) return;
        framePending = true;
        lastNanos = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override public final void doFrame(long nanos) {
        framePending = false;
        float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.05f, (nanos - lastNanos) / 1e9f);
        lastNanos = nanos;
        boolean moving = animate(dt);
        invalidate();
        if (moving && attached) {
            framePending = true;
            Choreographer.getInstance().postFrameCallback(this);
        } else if (!moving) {
            onSettled();
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        wake();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        super.onDetachedFromWindow();
    }

    @Override protected final void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        scale = width / W;
    }

    @Override protected final void onDraw(Canvas c) {
        c.save();
        c.scale(scale, scale);
        drawScene(c);
        c.restore();
    }
}
