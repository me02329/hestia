package io.hestia.tv.ui;

import android.content.Context;
import android.text.format.DateFormat;

import io.hestia.tv.core.I18n;
import io.hestia.tv.core.Prefs;
import io.hestia.tv.core.TimeText;

/** Gets a process ready to draw: fonts, language and clock format. Each screen calls it again as it opens. */
public final class Ui {
    private Ui() {}

    public static void init(Context c) {
        TimeText.h24 = DateFormat.is24HourFormat(c);
        I18n.apply(Prefs.language(c));
        Fonts.load(c.getAssets());
    }
}
