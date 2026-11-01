package io.hestia.tv.settings;

import java.util.function.Supplier;

/** One row of a settings list: what it says (read at each draw) and what OK does. */
final class SettingsRow {
    final Supplier<String> label, value;
    final String sub;
    final Runnable action;

    SettingsRow(Supplier<String> label, Supplier<String> value, Runnable action) {
        this(label, value, null, action);
    }

    SettingsRow(Supplier<String> label, Supplier<String> value, String sub, Runnable action) {
        this.label = label;
        this.value = value;
        this.sub = sub;
        this.action = action;
    }
}
