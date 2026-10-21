package io.freemytv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * What the remote's keys open, chosen in Hestia's settings. Only the keys Android hands to Hestia can be set: the TV
 * and Settings buttons while the home screen is showing, and a long press on Home anywhere (System UI asks Hestia's
 * quick panel). The others (Netflix, YouTube, volume, mic…) are handled by the TV's firmware.
 *
 * An action is a short string: "panel", "live", "search", "tv_settings", "hestia", "input:<TV input id>" or
 * "app:<package>". Saved in the "home" preferences as key_<key>.
 */
final class RemoteKeys {
    private RemoteKeys() {}

    static final String TV = "tv", SETTINGS = "settings", LONG_HOME = "long_home";
    static final String[] KEYS = {TV, SETTINGS, LONG_HOME};
    static final String PANEL = "panel", LIVE = "live", SEARCH = "search", TV_SETTINGS = "tv_settings", HESTIA = "hestia";

    static String keyName(String key) {
        switch (key) {
            case TV: return Ui.tr("Botão TV", "TV button");
            case SETTINGS: return Ui.tr("Botão Ajustes", "Settings button");
            default: return Ui.tr("Início (pressionar e segurar)", "Home (press and hold)");
        }
    }

    static String get(Context c, String key) {
        String fallback = key.equals(TV) ? LIVE : PANEL;
        return c.getSharedPreferences("home", Context.MODE_PRIVATE).getString("key_" + key, fallback);
    }

    static void set(Context c, String key, String action) {
        c.getSharedPreferences("home", Context.MODE_PRIVATE).edit().putString("key_" + key, action).apply();
    }

    static final class Choice {
        final String action, label, symbol;

        Choice(String action, String label, String symbol) {
            this.action = action;
            this.label = label;
            this.symbol = symbol;
        }
    }

    /** Everything a key can open: the panel, live TV, each HDMI input (with its name), shortcuts, then every app. */
    static List<Choice> choices(Context c) {
        List<Choice> list = new ArrayList<>();
        list.add(new Choice(PANEL, Ui.tr("Painel rápido", "Quick panel"), "settings"));
        if (HomeApps.liveTv(c) != null) list.add(new Choice(LIVE, Ui.tr("TV ao Vivo", "Live TV"), "tv"));
        for (HomeApps.Item it : HomeApps.allInputs(c)) {
            if (it.kind == HomeApps.INPUT) list.add(new Choice("input:" + it.inputId, it.label, "hdmi"));
        }
        if (HomeApps.search(c) != null) list.add(new Choice(SEARCH, Ui.tr("Pesquisar", "Search"), "mic"));
        list.add(new Choice(TV_SETTINGS, Ui.tr("Ajustes da TV", "TV Settings"), "tv_settings"));
        list.add(new Choice(HESTIA, "Hestia", "home"));
        for (HomeApps.Item it : HomeApps.allApps(c)) list.add(new Choice("app:" + it.pkg, it.label, "apps"));
        return list;
    }

    /** The name of what an action opens (for the settings), or the action itself if it no longer exists. */
    static String label(Context c, String action) {
        for (Choice ch : choices(c)) if (ch.action.equals(action)) return ch.label;
        return action.startsWith("app:") ? action.substring(4) : action;
    }

    /** Runs the action; false when it can't be opened (uninstalled app, unplugged input…). */
    static boolean run(Activity a, String action) {
        Intent intent;
        if (action.equals(PANEL)) {
            intent = new Intent(a, QuickPanelActivity.class);
        } else if (action.equals(LIVE)) {
            intent = HomeApps.liveTv(a);
        } else if (action.equals(SEARCH)) {
            intent = new Intent(Intent.ACTION_ASSIST);
        } else if (action.equals(TV_SETTINGS)) {
            intent = new Intent(Settings.ACTION_SETTINGS);
        } else if (action.equals(HESTIA)) {
            intent = new Intent().setComponent(new ComponentName(a, SettingsActivity.class));
        } else if (action.startsWith("input:")) {
            intent = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(action.substring(6)));
        } else if (action.startsWith("app:")) {
            String pkg = action.substring(4);
            intent = HomeApps.launchIntent(a.getPackageManager(), pkg);
            if (intent != null) HomeApps.recordLaunch(a, pkg);
        } else {
            intent = null;
        }
        if (intent == null) return false;
        try {
            a.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            AppLog.w("key action " + action + ": " + e.getMessage());
            return false;
        }
    }
}
