package io.hestia.tv.apps;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import io.hestia.tv.core.AppLog;
import io.hestia.tv.core.I18n;
import io.hestia.tv.core.Prefs;
import io.hestia.tv.core.Screens;

/**
 * The only place that starts activities: what each thing opens, and starting it without ever crashing the home screen
 * when the TV can't (an uninstalled app, a disabled settings page). Everything returns false on failure.
 */
public final class Launcher {
    private Launcher() {}

    /** The TV-optimized entry of an app, else its regular one; null when it can't be opened. */
    public static Intent appIntent(PackageManager pm, String pkg) {
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        return intent != null ? intent : pm.getLaunchIntentForPackage(pkg);
    }

    public static Intent input(String inputId) {
        return new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId));
    }

    public static Intent search() {
        return new Intent(Intent.ACTION_ASSIST);
    }

    public static Intent tvSettings() {
        return new Intent(Settings.ACTION_SETTINGS);
    }

    public static Intent appDetails(String pkg) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
    }

    public static Intent uninstall(String pkg) {
        return new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
    }

    public static Intent playStorePage(String pkg) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)).setPackage(AppCatalog.PLAY_STORE);
    }

    /** What a card opens; null when it no longer can. */
    private static Intent forCard(Context c, Card card) {
        switch (card.kind) {
            case APP: {
                Intent intent = appIntent(c.getPackageManager(), card.pkg);
                return intent == null ? null : intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            }
            case LIVE_TV: return TvInputs.liveTv(c);
            case INPUT: return input(card.inputId);
            case HESTIA_SETTINGS: return Screens.settings(c);
            case TV_SETTINGS: return tvSettings();
            case SEARCH: return search();
            default: return null;
        }
    }

    /** Opens a card with the system zoom transition growing out of it (the app starts opening right away). */
    public static boolean openCard(Activity a, Card card, View from, int x, int y, int w, int h) {
        Intent intent = forCard(a, card);
        if (intent == null) return false;
        if (card.kind == Card.Kind.APP) Prefs.recordLaunch(a, card.pkg);
        Bundle zoom = ActivityOptions.makeScaleUpAnimation(from, x, y, Math.max(w, 1), Math.max(h, 1)).toBundle();
        return attempt(a, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), zoom);
    }

    /** Opens an installed app by package and counts it; false when it's gone. */
    public static boolean openApp(Context c, String pkg) {
        Intent intent = appIntent(c.getPackageManager(), pkg);
        if (intent == null) return false;
        Prefs.recordLaunch(c, pkg);
        return start(c, intent);
    }

    /** In a task of its own: it can start from a service or from a panel over another app. */
    public static boolean start(Context c, Intent intent) {
        return attempt(c, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null);
    }

    /** Stacked on the current activity: Back returns to it. */
    public static boolean startOver(Activity a, Intent intent) {
        return attempt(a, intent, null);
    }

    /** Like start, telling the viewer when this TV can't. */
    public static boolean startOrSay(Context c, Intent intent) {
        if (start(c, intent)) return true;
        Toast.makeText(c, I18n.tr("Indisponível nesta TV.", "Not available on this TV."), Toast.LENGTH_SHORT).show();
        return false;
    }

    private static boolean attempt(Context c, Intent intent, Bundle options) {
        try {
            c.startActivity(intent, options);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            AppLog.w("can't open " + intent.getAction() + " " + intent.getComponent() + ": " + e.getMessage());
            return false;
        }
    }
}
