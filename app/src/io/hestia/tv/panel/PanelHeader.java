package io.hestia.tv.panel;

import android.graphics.Canvas;
import android.graphics.RectF;

/** Optional card above a panel's list (the quick panel's player). Up from the first entry reaches its buttons. */
public interface PanelHeader {
    float height();

    /** Buttons in the card, moved through with left/right. */
    int buttons();

    void draw(Canvas c, RectF area, int focusedButton);

    void press(int button);
}
