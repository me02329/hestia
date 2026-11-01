package io.hestia.tv.panel;

import android.os.Bundle;
import android.view.KeyEvent;

import io.hestia.tv.ui.HestiaActivity;

import java.util.List;

/**
 * A translucent panel over whatever is on screen: a title and a list of actions. D-pad up/down, OK runs the action,
 * Back closes. Subclasses give the entries; the quick panel sits on the right, the menus in the middle.
 */
public abstract class PanelActivity extends HestiaActivity {
    private PanelView view;

    protected abstract String title();

    protected abstract List<PanelEntry> entries();

    protected String subtitle() {
        return "";
    }

    /** Right-hand side panel (quick panel) or centered (menus). */
    protected boolean onRight() {
        return false;
    }

    /** The card above the list, or null. */
    protected PanelHeader header() {
        return null;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new PanelView(this);
        setContentView(view);
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();   // the state may have changed (an input plugged in, a favorite added)
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (view.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    protected final void invalidatePanel() {
        view.invalidate();
    }

    /** Moves the focus to a row. */
    protected final void focusEntry(int index) {
        view.focusEntry(index);
    }

    /** Rebuilds the list (something it shows changed). */
    protected final void refresh() {
        view.setEntries(entries());
    }
}
