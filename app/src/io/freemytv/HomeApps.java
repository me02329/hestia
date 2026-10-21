package io.freemytv;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What shows up on the home screen, and how to open each thing. Apps are 16:9 cards with their official TV art (the
 * Android TV "banner"), or their icon on a dark card. The favorites (first row) are chosen in the settings and saved
 * in the "home" preferences; until then, the usual streaming apps that are installed. Shortcuts (live TV, inputs,
 * settings) are icon cards drawn by the home screen.
 */
final class HomeApps {
    private HomeApps() {}

    static final int APP = 0, LIVE_TV = 1, INPUT = 2, SETTINGS = 4, ANDROID_SETTINGS = 5, SEARCH = 6;
    /** Image size of each card (16:9; on screen it shows up to ~330 px wide). */
    static final int TILE_W = 384, TILE_H = 216;

    static final class Item {
        int kind;
        String key, label, pkg, inputId, number;
        String symbol;      // icon for the shortcuts and special cards
        Bitmap tile;        // 16:9 card (apps and specials)
        boolean connected = true;
    }

    /** Favorites before any choice: these, in this order, when installed (up to DEFAULT_COUNT). */
    private static final String[] DEFAULT_FAVORITES = {
        "com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus", "com.canal.android.canal", "com.wbd.stream", "com.apple.atve.androidtv.appletv",
        "com.spotify.tv.android",
    };
    private static final int DEFAULT_COUNT = 5;
    static final int MAX_FAVORITES = 10;
    /** Live TV apps of some brands (the antenna's channels open through the standard Android request first). */
    static final String[] LIVE_TV_APPS = {"com.tcl.tv", "com.google.android.tv", "com.mediatek.wwtv.tvcenter", "com.sony.dtv.tvx"};
    /** Kept out of "All Apps" (only hidden, still installed). Add the ones your TV shows that you never use. */
    private static final Set<String> HIDDEN = new HashSet<>(java.util.Arrays.asList(
            "com.google.android.play.games",        // Google Play Games
            "com.tcl.t_solo",                       // TCL: T-Solo
            "com.tcl.waterfall.overseas",           // TCL Channel
            "com.tcl.dashboard",                    // TCL Home
            "com.tcl.ocean.instructions",           // TCL: User Guide
            "com.tcl.guard",                        // TCL: Safety Guard
            "com.tcl.exhibit"));                    // TCL: T-Exhibition

    private static final Map<String, Bitmap> TILES = new HashMap<>();   // key → ready card

    /** The favorite packages, in order (installed or not). */
    static List<String> favorites(Context c) {
        String saved = c.getSharedPreferences("home", Context.MODE_PRIVATE).getString("favorites", null);
        List<String> out = new ArrayList<>();
        if (saved != null) {
            for (String pkg : saved.split(",")) {
                if (pkg.matches("[A-Za-z0-9_.]{1,200}") && !out.contains(pkg)) out.add(pkg);
            }
            return out;
        }
        PackageManager pm = c.getPackageManager();
        for (String pkg : DEFAULT_FAVORITES) {
            if (out.size() < DEFAULT_COUNT && launchIntent(pm, pkg) != null) out.add(pkg);
        }
        return out;
    }

    static void setFavorites(Context c, List<String> packages) {
        c.getSharedPreferences("home", Context.MODE_PRIVATE).edit()
                .putString("favorites", TextUtils.join(",", packages)).apply();
    }

    /** First row of the home screen: the installed favorites. */
    static List<Item> shelf(Context c) {
        List<Item> items = new ArrayList<>();
        for (String pkg : favorites(c)) {
            Item it = appItem(c, pkg);
            if (it != null) items.add(it);
        }
        return items;
    }

    static Item appItem(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        if (launchIntent(pm, pkg) == null) return null;
        Item it = new Item();
        it.kind = APP;
        it.pkg = pkg;
        it.key = pkg;
        try {
            it.label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        it.tile = appTile(c, pkg);
        return it;
    }

    private static Item shortcut(int kind, String key, String label, String symbol) {
        Item it = new Item();
        it.kind = kind;
        it.key = key;
        it.label = label;
        it.symbol = symbol;
        return it;
    }

    /** Google voice search / Assistant (what the remote's mic button opens), when the TV has it. */
    static Item search(Context c) {
        Intent assist = new Intent(Intent.ACTION_ASSIST);
        if (assist.resolveActivity(c.getPackageManager()) == null) return null;
        return shortcut(SEARCH, "search", Ui.tr("Pesquisar", "Search"), "mic");
    }

    /** TV settings and this app's settings (the TV row of the home screen). */
    static List<Item> settingsShortcuts() {
        List<Item> items = new ArrayList<>();
        items.add(shortcut(SETTINGS, "settings", "Hestia", "home"));
        items.add(shortcut(ANDROID_SETTINGS, "tv-settings", Ui.tr("Ajustes da TV", "TV Settings"), "tv_settings"));
        return items;
    }

    /** For the bottom drawer: live TV and every HDMI input (the ones with no device are shown dimmed). */
    static List<Item> allInputs(Context c) {
        List<Item> items = new ArrayList<>();
        if (liveTv(c) != null) items.add(shortcut(LIVE_TV, "live", Ui.tr("TV ao Vivo", "Live TV"), "tv"));
        items.addAll(inputs(c, true));
        return items;
    }

    private static List<Item> inputs(Context c) {
        return inputs(c, false);
    }

    /**
     * HDMI inputs: only the ones with a device sending a signal (or all of them, with `all`). A name you gave the input
     * in the TV's settings is used as the label.
     */
    private static List<Item> inputs(Context c, boolean all) {
        List<Item> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        List<TvInputInfo> list = new ArrayList<>();
        try {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.getType() == TvInputInfo.TYPE_HDMI && info.getParentId() == null) list.add(info);
            }
        } catch (RuntimeException e) {
            return out;
        }
        Collections.sort(list, (a, b) -> a.getId().compareTo(b.getId()));
        Pattern digit = Pattern.compile("(\\d)");
        for (int i = 0; i < list.size(); i++) {
            TvInputInfo info = list.get(i);
            int number = i + 1;
            Matcher m = digit.matcher(String.valueOf(info.loadLabel(c)));
            if (m.find()) number = Integer.parseInt(m.group(1));
            // TCL TVs report "connected standby" for every port, with or without a device; only truly "connected"
            // (a device that is on and sending a signal) makes the input show up.
            int state = TvInputManager.INPUT_STATE_DISCONNECTED;
            try {
                state = tim.getInputState(info.getId());
            } catch (RuntimeException ignored) {
            }
            if (!all && state != TvInputManager.INPUT_STATE_CONNECTED) continue;
            CharSequence custom = null;
            try {
                custom = info.loadCustomLabel(c);
            } catch (RuntimeException ignored) {
            }
            String label = custom != null && custom.length() > 0 ? custom.toString() : "HDMI " + number;
            Item it = shortcut(INPUT, "hdmi" + number, label, "hdmi");
            it.inputId = info.getId();
            it.number = String.valueOf(number);
            it.connected = state == TvInputManager.INPUT_STATE_CONNECTED;
            out.add(it);
        }
        Collections.sort(out, (a, b) -> a.number.compareTo(b.number));
        return out;
    }

    /** Every TV app that can be opened (not this one, nor live TV apps or the HIDDEN ones), by name. */
    static List<Item> allApps(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> skip = new HashSet<>(java.util.Arrays.asList(LIVE_TV_APPS));
        skip.add(c.getPackageName());
        skip.addAll(HIDDEN);
        List<Item> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (skip.contains(pkg) || !seen.add(pkg)) continue;
            Item it = new Item();
            it.kind = APP;
            it.pkg = pkg;
            it.key = pkg;
            it.label = String.valueOf(ri.loadLabel(pm));
            it.tile = appTile(c, pkg);
            items.add(it);
        }
        Collections.sort(items, (a, b) -> a.label.toLowerCase(Locale.ROOT).compareTo(b.label.toLowerCase(Locale.ROOT)));
        return items;
    }

    /** Apps that are neither favorites nor hidden (the last row of the home screen), in the chosen order. */
    static List<Item> others(Context c) {
        Set<String> skip = new HashSet<>(favorites(c));
        skip.addAll(hidden(c));
        List<Item> items = new ArrayList<>();
        for (Item it : allApps(c)) if (!skip.contains(it.pkg)) items.add(it);
        String sort = sortMode(c);
        if (!sort.equals(SORT_NAME)) {
            SharedPreferences usage = c.getSharedPreferences("usage", Context.MODE_PRIVATE);
            String suffix = sort.equals(SORT_RECENT) ? ":at" : ":n";
            // Stable sort: apps never opened keep their alphabetical order, after the others.
            Collections.sort(items, (a, b) -> Long.compare(usage.getLong(b.pkg + suffix, 0), usage.getLong(a.pkg + suffix, 0)));
        }
        return items;
    }

    // MARK: - Hidden apps, launch counts and sort order (all in this app's preferences)

    static final String SORT_NAME = "name", SORT_USAGE = "usage", SORT_RECENT = "recent";

    static String sortMode(Context c) {
        return c.getSharedPreferences("home", Context.MODE_PRIVATE).getString("sort", SORT_NAME);
    }

    static void setSortMode(Context c, String mode) {
        c.getSharedPreferences("home", Context.MODE_PRIVATE).edit().putString("sort", mode).apply();
    }

    static Set<String> hidden(Context c) {
        return new HashSet<>(c.getSharedPreferences("home", Context.MODE_PRIVATE).getStringSet("hidden", new HashSet<>()));
    }

    static void setHidden(Context c, String pkg, boolean hide) {
        Set<String> set = hidden(c);
        if (hide) set.add(pkg); else set.remove(pkg);
        c.getSharedPreferences("home", Context.MODE_PRIVATE).edit().putStringSet("hidden", set).apply();
    }

    /** Counted by Hestia itself when it opens an app (no usage-stats permission). */
    static void recordLaunch(Context c, String pkg) {
        SharedPreferences usage = c.getSharedPreferences("usage", Context.MODE_PRIVATE);
        usage.edit().putLong(pkg + ":n", usage.getLong(pkg + ":n", 0) + 1)
                .putLong(pkg + ":at", System.currentTimeMillis()).apply();
    }

    /** The Play Store as a card of the TV row, when installed. */
    static Item playStore(Context c) {
        return appItem(c, "com.android.vending");
    }

    /** Opens the item with the system zoom transition growing out of the card (the app starts opening right away). */
    static boolean open(Activity a, Item it, View from, int x, int y, int w, int h) {
        Intent intent;
        switch (it.kind) {
            case APP:
                recordLaunch(a, it.pkg);
                intent = launchIntent(a.getPackageManager(), it.pkg);
                if (intent != null) intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                break;
            case LIVE_TV:
                intent = liveTv(a);
                break;
            case INPUT:
                intent = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(it.inputId));
                break;
            case SETTINGS:
                intent = new Intent().setComponent(new ComponentName(a, SettingsActivity.class));
                break;
            case ANDROID_SETTINGS:
                intent = new Intent(Settings.ACTION_SETTINGS);
                break;
            case SEARCH:
                intent = new Intent(Intent.ACTION_ASSIST);
                break;
            default:
                return false;
        }
        if (intent == null) return false;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            a.startActivity(intent, ActivityOptions.makeScaleUpAnimation(from, x, y, Math.max(w, 1), Math.max(h, 1)).toBundle());
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            AppLog.w("couldn't open " + it.label + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Live TV: the antenna input's channels through the standard Android request (opening the TV app by itself may go
     * back to the last input used, like an HDMI); otherwise a known live TV app. Null = this TV has none.
     */
    static Intent liveTv(Context c) {
        PackageManager pm = c.getPackageManager();
        String tuner = tunerInput(c);
        if (tuner != null) {
            Intent channels = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(tuner));
            if (channels.resolveActivity(pm) != null) return channels;
        }
        for (String pkg : LIVE_TV_APPS) {
            Intent app = launchIntent(pm, pkg);
            if (app != null) return app;
        }
        return null;
    }

    /**
     * The TV's antenna (tuner) input, or null. Some "tuner" inputs aren't antennas: on TCL, AirPlay and the IPTV
     * channels declare TYPE_TUNER too (and AirPlay is listed first), so those are skipped; a service named like a tuner
     * comes first, then the TV maker's own, then any other.
     */
    static String tunerInput(Context c) {
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return null;
        String best = null;
        int bestScore = -1;
        try {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.getType() != TvInputInfo.TYPE_TUNER || info.isPassthroughInput()) continue;
                String id = info.getId().toLowerCase(Locale.ROOT);
                if (id.contains("airplay") || id.contains("iptv") || id.contains("channelplus")) continue;
                int score = 0;
                if (id.contains("tuner")) score += 2;
                if (info.getServiceInfo() != null && info.getServiceInfo().applicationInfo != null
                        && (info.getServiceInfo().applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
                    score += 1;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = info.getId();
                }
            }
        } catch (RuntimeException ignored) {
        }
        return best;
    }

    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        return intent != null ? intent : pm.getLaunchIntentForPackage(pkg);
    }

    // MARK: - Cards

    private static synchronized Bitmap appTile(Context c, String pkg) {
        if (TILES.containsKey(pkg)) return TILES.get(pkg);
        Bitmap tile = bannerTile(c, pkg);
        if (tile == null) tile = iconTile(c, pkg);
        tile.setHasMipMap(true);
        TILES.put(pkg, tile);
        return tile;
    }

    /** The app's official TV art (the Android TV banner), at the highest density the app has. */
    private static Bitmap bannerTile(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Drawable d = null;
        try {
            Intent launch = launchIntent(pm, pkg);
            if (launch != null && launch.getComponent() != null) {
                ActivityInfo ai = pm.getActivityInfo(launch.getComponent(), 0);
                int res = ai.getBannerResource();
                if (res != 0) d = pm.getResourcesForApplication(ai.applicationInfo).getDrawableForDensity(res, DisplayMetrics.DENSITY_XXXHIGH, null);
            }
            if (d == null) d = pm.getApplicationBanner(pkg);
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
        }
        if (d == null) return null;
        Bitmap b = Bitmap.createBitmap(TILE_W, TILE_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        canvas.drawColor(0xFF15161C);   // banners with transparency sit on a dark background
        d.setBounds(0, 0, TILE_W, TILE_H);
        d.draw(canvas);
        return b;
    }

    /** No banner: the app's icon (in its proper shape, with some margin) on a dark card. */
    private static Bitmap iconTile(Context c, String pkg) {
        Bitmap b = darkTile();
        Canvas canvas = new Canvas(b);
        Drawable icon = null;
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            if (ai.icon != 0) {
                try {
                    icon = pm.getResourcesForApplication(ai).getDrawableForDensity(ai.icon, DisplayMetrics.DENSITY_XXXHIGH, null);
                } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                }
            }
            if (icon == null) icon = pm.getApplicationIcon(ai);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        if (icon == null) return b;
        int size = Math.round(TILE_H * 0.56f);
        float left = (TILE_W - size) / 2f, top = (TILE_H - size) / 2f;
        if (icon instanceof AdaptiveIconDrawable) {
            // Adaptive icon: the layers clipped to a squircle, like an Apple icon.
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
            Path clip = new Path();
            clip.addRoundRect(new RectF(left, top, left + size, top + size), size * 0.2237f, size * 0.2237f, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            int pad = size / 4;
            for (Drawable layer : new Drawable[] {a.getBackground(), a.getForeground()}) {
                if (layer == null) continue;
                layer.setBounds(Math.round(left) - pad, Math.round(top) - pad, Math.round(left) + size + pad, Math.round(top) + size + pad);
                layer.draw(canvas);
            }
            canvas.restore();
        } else {
            // Legacy icon (a free-form drawing with transparency): smaller, no frame.
            int s = Math.round(TILE_H * 0.46f);
            icon.setBounds((TILE_W - s) / 2, (TILE_H - s) / 2, (TILE_W + s) / 2, (TILE_H + s) / 2);
            icon.draw(canvas);
        }
        return b;
    }

    private static Bitmap darkTile() {
        Bitmap b = Bitmap.createBitmap(TILE_W, TILE_H, Bitmap.Config.ARGB_8888);
        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, 0, TILE_H, 0xFF2B2E3A, 0xFF17191F, Shader.TileMode.CLAMP));
        new Canvas(b).drawRect(0, 0, TILE_W, TILE_H, bg);
        return b;
    }
}
