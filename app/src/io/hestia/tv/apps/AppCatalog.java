package io.hestia.tv.apps;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;

import io.hestia.tv.core.I18n;
import io.hestia.tv.core.Prefs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which apps and shortcuts show up, and in what order. The favorites (first row) are chosen in the settings; until
 * then, the usual streaming apps that are installed.
 */
public final class AppCatalog {
    private AppCatalog() {}

    public static final String SORT_NAME = "name", SORT_USAGE = "usage", SORT_RECENT = "recent";
    public static final int MAX_FAVORITES = 10;
    static final String PLAY_STORE = "com.android.vending";
    private static final String VALID_PACKAGE = "[A-Za-z0-9_.]{1,200}";

    /** Favorites before any choice: these, in this order, when installed (up to DEFAULT_COUNT). */
    private static final String[] DEFAULT_FAVORITES = {
        "com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus", "com.canal.android.canal", "com.wbd.stream", "com.apple.atve.androidtv.appletv",
        "com.spotify.tv.android",
    };
    private static final int DEFAULT_COUNT = 5;
    /** Kept out of "All Apps" (only hidden, still installed). Add the ones your TV shows that you never use. */
    private static final Set<String> NEVER_LISTED = new HashSet<>(Arrays.asList(
            "com.google.android.play.games",        // Google Play Games
            "com.tcl.t_solo",                       // TCL: T-Solo
            "com.tcl.waterfall.overseas",           // TCL Channel
            "com.tcl.dashboard",                    // TCL Home
            "com.tcl.ocean.instructions",           // TCL: User Guide
            "com.tcl.guard",                        // TCL: Safety Guard
            "com.tcl.exhibit"));                    // TCL: T-Exhibition

    public static boolean isValidPackage(String pkg) {
        return pkg != null && pkg.matches(VALID_PACKAGE);
    }

    /** The favorite packages, in order (installed or not). */
    public static List<String> favorites(Context c) {
        String saved = Prefs.favorites(c);
        List<String> out = new ArrayList<>();
        if (saved != null) {
            for (String pkg : saved.split(",")) {
                if (isValidPackage(pkg) && !out.contains(pkg)) out.add(pkg);
            }
            return out;
        }
        PackageManager pm = c.getPackageManager();
        for (String pkg : DEFAULT_FAVORITES) {
            if (out.size() < DEFAULT_COUNT && Launcher.appIntent(pm, pkg) != null) out.add(pkg);
        }
        return out;
    }

    public static void setFavorites(Context c, List<String> packages) {
        Prefs.setFavorites(c, TextUtils.join(",", packages));
    }

    /** First row of the home screen: the installed favorites. */
    public static List<Card> favoriteCards(Context c) {
        List<Card> cards = new ArrayList<>();
        for (String pkg : favorites(c)) {
            Card card = appCard(c, pkg);
            if (card != null) cards.add(card);
        }
        return cards;
    }

    private static Card appCard(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        if (Launcher.appIntent(pm, pkg) == null) return null;
        try {
            Card card = new Card(Card.Kind.APP, pkg, String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))));
            card.pkg = pkg;
            card.tile = AppTiles.of(c, pkg);
            return card;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** Second row of the home screen: live TV and inputs, search, the Play Store, then TV and Hestia settings. */
    public static List<Card> tvCards(Context c) {
        List<Card> cards = TvInputs.liveAndInputs(c);
        if (hasSearch(c)) cards.add(Card.shortcut(Card.Kind.SEARCH, "search", I18n.tr("Pesquisar", "Search"), "mic"));
        Card store = appCard(c, PLAY_STORE);
        if (store != null) cards.add(store);
        cards.add(Card.shortcut(Card.Kind.HESTIA_SETTINGS, "settings", "Hestia", "home"));
        cards.add(Card.shortcut(Card.Kind.TV_SETTINGS, "tv-settings", I18n.tr("Ajustes da TV", "TV Settings"), "tv_settings"));
        return cards;
    }

    /** Google voice search / Assistant (what the remote's mic button opens), when the TV has it. */
    public static boolean hasSearch(Context c) {
        return Launcher.search().resolveActivity(c.getPackageManager()) != null;
    }

    public static boolean hasPlayStore(Context c) {
        return Launcher.appIntent(c.getPackageManager(), PLAY_STORE) != null;
    }

    /** Every TV app that can be opened (not this one, nor live TV apps or the never-listed ones), by name. */
    public static List<Card> allApps(Context c) {
        List<Card> cards = appNames(c);
        for (Card card : cards) card.tile = AppTiles.of(c, card.pkg);
        return cards;
    }

    /** The same list without the art, which is what's slow: for lists of names. */
    public static List<Card> appNames(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> skip = new HashSet<>(Arrays.asList(TvInputs.LIVE_TV_APPS));
        skip.add(c.getPackageName());
        skip.addAll(NEVER_LISTED);
        List<Card> cards = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
            String pkg = ri.activityInfo.packageName;
            if (skip.contains(pkg) || !seen.add(pkg)) continue;
            Card card = new Card(Card.Kind.APP, pkg, String.valueOf(ri.loadLabel(pm)));
            card.pkg = pkg;
            cards.add(card);
        }
        Collections.sort(cards, (a, b) -> a.label.toLowerCase(Locale.ROOT).compareTo(b.label.toLowerCase(Locale.ROOT)));
        return cards;
    }

    /** Apps that are neither favorites nor hidden (the last row of the home screen), in the chosen order. */
    public static List<Card> otherApps(Context c) {
        Set<String> skip = new HashSet<>(favorites(c));
        skip.addAll(Prefs.hidden(c));
        List<Card> cards = new ArrayList<>();
        for (Card card : allApps(c)) if (!skip.contains(card.pkg)) cards.add(card);
        // Stable sort: apps never opened keep their alphabetical order, after the others.
        String sort = sortMode(c);
        if (sort.equals(SORT_USAGE)) {
            Collections.sort(cards, (a, b) -> Long.compare(Prefs.launchCount(c, b.pkg), Prefs.launchCount(c, a.pkg)));
        } else if (sort.equals(SORT_RECENT)) {
            Collections.sort(cards, (a, b) -> Long.compare(Prefs.lastLaunch(c, b.pkg), Prefs.lastLaunch(c, a.pkg)));
        }
        return cards;
    }

    public static String sortMode(Context c) {
        return Prefs.sort(c, SORT_NAME);
    }

    public static void setHidden(Context c, String pkg, boolean hide) {
        Set<String> set = Prefs.hidden(c);
        if (hide) set.add(pkg); else set.remove(pkg);
        Prefs.setHidden(c, set);
    }
}
