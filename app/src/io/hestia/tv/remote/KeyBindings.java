package io.hestia.tv.remote;

import android.content.Context;
import android.content.Intent;

import io.hestia.tv.apps.AppCatalog;
import io.hestia.tv.apps.Card;
import io.hestia.tv.apps.Launcher;
import io.hestia.tv.apps.TvInputs;
import io.hestia.tv.core.I18n;
import io.hestia.tv.core.Prefs;
import io.hestia.tv.core.Screens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What the remote's keys open, chosen in Hestia's settings. Keys Android hands to Hestia: the TV button (short and long
 * press) and the Settings button everywhere when RemoteKeysService is enabled, else on Hestia's own screens; a long
 * press on Home anywhere (System UI asks Hestia's quick panel). The others (Netflix, YouTube, volume, mic…) are the
 * TV firmware's.
 */
public final class KeyBindings {
    private KeyBindings() {}

    public static final String TV = "tv", LONG_TV = "long_tv", SETTINGS = "settings", LONG_HOME = "long_home";
    public static final List<String> KEYS = Arrays.asList(TV, LONG_TV, SETTINGS, LONG_HOME);

    public static String keyName(String key) {
        switch (key) {
            case TV: return I18n.tr("Botão TV", "TV button");
            case LONG_TV: return I18n.tr("Botão TV (pressionar e segurar)", "TV button (press and hold)");
            case SETTINGS: return I18n.tr("Botão Ajustes", "Settings button");
            default: return I18n.tr("Início (pressionar e segurar)", "Home (press and hold)");
        }
    }

    private static String fallback(String key) {
        return key.equals(TV) || key.equals(LONG_TV) ? KeyAction.LIVE : KeyAction.PANEL;
    }

    public static String get(Context c, String key) {
        return Prefs.keyAction(c, key, fallback(key));
    }

    public static void set(Context c, String key, String action) {
        Prefs.setKeyAction(c, key, action);
    }

    public static boolean opensPanel(String action) {
        return KeyAction.PANEL.equals(action);
    }

    public static final class Choice {
        public final String action, label, symbol;

        Choice(String action, String label, String symbol) {
            this.action = action;
            this.label = label;
            this.symbol = symbol;
        }
    }

    /** Everything a key can open: the panel, live TV, each HDMI input (with its name), shortcuts, then every app. */
    public static List<Choice> choices(Context c) {
        List<Choice> list = new ArrayList<>();
        list.add(new Choice(KeyAction.PANEL, I18n.tr("Painel rápido", "Quick panel"), "settings"));
        if (TvInputs.liveTv(c) != null) list.add(new Choice(KeyAction.LIVE, I18n.tr("TV ao Vivo", "Live TV"), "tv"));
        for (Card input : TvInputs.hdmi(c)) list.add(new Choice(KeyAction.input(input.inputId), input.label, "hdmi"));
        if (AppCatalog.hasSearch(c)) list.add(new Choice(KeyAction.SEARCH, I18n.tr("Pesquisar", "Search"), "mic"));
        list.add(new Choice(KeyAction.TV_SETTINGS, I18n.tr("Ajustes da TV", "TV Settings"), "tv_settings"));
        list.add(new Choice(KeyAction.HESTIA, "Hestia", "home"));
        for (Card app : AppCatalog.appNames(c)) list.add(new Choice(KeyAction.app(app.pkg), app.label, "apps"));
        return list;
    }

    /** The name of what an action opens, from choices(), or the action's own text if it no longer exists. */
    public static String label(List<Choice> choices, String action) {
        for (Choice ch : choices) if (ch.action.equals(action)) return ch.label;
        String pkg = KeyAction.appOf(action);
        return pkg != null ? pkg : action;
    }

    /** Runs what the key is set to; if that can't open (uninstalled app, unplugged input…), the key's default. */
    static boolean runKey(Context c, String key) {
        return run(c, get(c, key)) || run(c, fallback(key));
    }

    /** Runs the action; false when it can't be opened. */
    public static boolean run(Context c, String action) {
        String pkg = KeyAction.appOf(action);
        if (pkg != null) return Launcher.openApp(c, pkg);
        String input = KeyAction.inputOf(action);
        Intent intent = input != null ? Launcher.input(input) : builtIn(c, action);
        return intent != null && Launcher.start(c, intent);
    }

    private static Intent builtIn(Context c, String action) {
        switch (action) {
            case KeyAction.PANEL: return Screens.quickPanel(c);
            case KeyAction.LIVE: return TvInputs.liveTv(c);
            case KeyAction.SEARCH: return Launcher.search();
            case KeyAction.TV_SETTINGS: return Launcher.tvSettings();
            case KeyAction.HESTIA: return Screens.settings(c);
            default: return null;
        }
    }
}
