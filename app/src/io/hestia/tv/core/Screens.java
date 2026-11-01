package io.hestia.tv.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * How to reach each of Hestia's own screens, without the lower layers knowing the activities. The names are the
 * manifest's components (a test checks them); TV settings and tools/tv_safe.py point at them too, so they don't move.
 */
public final class Screens {
    private Screens() {}

    public static final String EXTRA_TITLE = "title", EXTRA_PACKAGE = "package", EXTRA_KEY = "key";
    private static final String ROOT = "io.hestia.tv.";

    private static Intent screen(Context c, String name) {
        return new Intent().setComponent(new ComponentName(c.getPackageName(), ROOT + name));
    }

    public static Intent home(Context c) {
        return screen(c, "HomeActivity");
    }

    public static Intent settings(Context c) {
        return screen(c, "SettingsActivity");
    }

    public static Intent quickPanel(Context c) {
        return screen(c, "QuickPanelActivity");
    }

    public static Intent news(Context c, String focusedTitle) {
        return screen(c, "NewsActivity").putExtra(EXTRA_TITLE, focusedTitle);
    }

    public static Intent appMenu(Context c, String pkg) {
        return screen(c, "AppMenuActivity").putExtra(EXTRA_PACKAGE, pkg);
    }

    /** Settings › Remote buttons; with a key, the list of what that key can open. */
    public static Intent remoteKeys(Context c, String key) {
        Intent intent = screen(c, "RemoteKeysActivity");
        return key == null ? intent : intent.putExtra(EXTRA_KEY, key);
    }
}
