package io.hestia.tv.remote;

/**
 * What a key does, stored as a short string: "panel", "live", "search", "tv_settings", "hestia", "input:<TV input id>"
 * or "app:<package>". Plain Java: tested on a JVM (tests/java). Saved values are checked when read: they drive intents.
 */
final class KeyAction {
    private KeyAction() {}

    static final String PANEL = "panel", LIVE = "live", SEARCH = "search", TV_SETTINGS = "tv_settings", HESTIA = "hestia";
    private static final String INPUT = "input:", APP = "app:";

    static String input(String inputId) {
        return INPUT + inputId;
    }

    static String app(String pkg) {
        return APP + pkg;
    }

    /** The TV input of an "input:" action, or null. */
    static String inputOf(String action) {
        return action.startsWith(INPUT) && action.length() > INPUT.length() ? action.substring(INPUT.length()) : null;
    }

    /** The package of a well-formed "app:" action, or null. */
    static String appOf(String action) {
        if (!action.startsWith(APP)) return null;
        String pkg = action.substring(APP.length());
        return pkg.matches("[A-Za-z0-9_.]{1,200}") ? pkg : null;
    }
}
