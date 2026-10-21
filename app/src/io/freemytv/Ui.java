package io.freemytv;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Fonts and text for the home screen, the screensaver and the settings. Font: Inter (variable, assets/fonts, SIL OFL),
 * with the "display" optical size for large text and the "text" size for small text; without it, the system font at a
 * similar weight.
 */
final class Ui {
    private Ui() {}

    static final Locale PT = new Locale("pt", "BR");
    static final String AUTO = "auto", LANG_EN = "en", LANG_FR = "fr", LANG_PT = "pt";
    /**
     * The language in use: the choice made in Hestia's settings, else config.properties ("language"); "auto" follows
     * the TV (French, Portuguese, otherwise English).
     */
    static volatile String lang = resolve(Config.LANGUAGE);
    static volatile boolean en = LANG_EN.equals(lang);
    /** 24-hour clock: always in French and Portuguese; in English when the TV is set to it. */
    static volatile boolean h24 = true;
    static Typeface thin, light, displayMedium, displaySemibold, regular, medium, semibold;
    private static boolean loaded;
    private static final Rect BOUNDS = new Rect();
    private static final RectF OVAL = new RectF();

    static synchronized void init(Context c) {
        h24 = android.text.format.DateFormat.is24HourFormat(c);
        apply(choice(c));
        if (loaded) return;
        loaded = true;
        AssetManager a = c.getAssets();
        thin = font(a, 32, 200);
        light = font(a, 32, 300);
        displayMedium = font(a, 32, 500);
        displaySemibold = font(a, 32, 600);
        regular = font(a, 14, 400);
        medium = font(a, 14, 500);
        semibold = font(a, 14, 600);
    }

    /** Inter at an optical size (14 = text, 32 = display) and weight; the system font if the file is missing. */
    private static Typeface font(AssetManager a, int opticalSize, int weight) {
        if (has(a, FONT)) {
            try {
                Typeface t = new Typeface.Builder(a, FONT)
                        .setFontVariationSettings("'opsz' " + opticalSize + ", 'wght' " + weight).build();
                if (t != null) return t;
            } catch (RuntimeException ignored) {
            }
        }
        return Typeface.create(Typeface.DEFAULT, weight, false);
    }

    private static final String FONT = "fonts/InterVariable.ttf";

    /** "auto", "en", "fr" or "pt": Hestia's settings first, then config.properties. */
    static String choice(Context c) {
        return c.getSharedPreferences("home", Context.MODE_PRIVATE).getString("language", Config.LANGUAGE.trim());
    }

    static void setChoice(Context c, String choice) {
        c.getSharedPreferences("home", Context.MODE_PRIVATE).edit().putString("language", choice).apply();
        apply(choice);
    }

    private static void apply(String choice) {
        lang = resolve(choice);
        en = LANG_EN.equals(lang);
    }

    static String resolve(String choice) {
        String l = choice == null ? "" : choice.trim().toLowerCase(Locale.ROOT);
        if (l.startsWith("en")) return LANG_EN;
        if (l.startsWith("fr")) return LANG_FR;
        if (l.startsWith("pt")) return LANG_PT;
        String device = Locale.getDefault().getLanguage();
        return "fr".equals(device) ? LANG_FR : "pt".equals(device) ? LANG_PT : LANG_EN;
    }

    static Locale locale() {
        return LANG_FR.equals(lang) ? Locale.FRANCE : LANG_PT.equals(lang) ? PT : Locale.US;
    }

    private static boolean has(AssetManager a, String path) {
        try (InputStream in = a.open(path)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }

    static Paint text(Typeface face, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(face);
        p.setTextSize(size);
        p.setColor(color);
        return p;
    }

    /** Fixed-width digits (the clock and the prices don't jitter when a digit changes). */
    static Paint numbers(Typeface face, float size, int color) {
        Paint p = text(face, size, color);
        p.setFontFeatureSettings("'tnum'");
        return p;
    }

    /** Height of capital letters (and digits) above the baseline. */
    static float cap(Paint p) {
        synchronized (BOUNDS) {
            p.getTextBounds("H8", 0, 2, BOUNDS);
            return -BOUNDS.top;
        }
    }

    /** Half the cap height: added to the center line, it gives the baseline that centers the text. */
    static float halfCap(Paint p) {
        return cap(p) / 2f;
    }

    /** Left bearing of the first character (large, thin fonts start a little inward). */
    static float leftBearing(Paint p, String s) {
        if (s.isEmpty()) return 0;
        synchronized (BOUNDS) {
            p.getTextBounds(s, 0, 1, BOUNDS);
            return BOUNDS.left;
        }
    }

    static int alpha(int color, float a) {
        int base = (color >>> 24) & 255;
        return (Math.round(base * Math.max(0, Math.min(1, a))) << 24) | (color & 0xFFFFFF);
    }

    static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    /** Portuguese or English as written at the call; French from the FR table (keyed by the English text). */
    static String tr(String pt, String english) {
        if (LANG_PT.equals(lang)) return pt;
        if (LANG_FR.equals(lang)) {
            String fr = FR.get(english);
            return fr != null ? fr : english;
        }
        return english;
    }

    /** "21:47" (in English with a 12-hour TV, "9:47"). */
    static String clock(long now) {
        return en && !h24 ? new SimpleDateFormat("h:mm", Locale.US).format(new Date(now))
                : new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(now));
    }

    /** "5:21" (sunrise and sunset; in English with a 12-hour TV, "5:21 AM"). */
    static String time(long at) {
        return en && !h24 ? new SimpleDateFormat("h:mm a", Locale.US).format(new Date(at))
                : new SimpleDateFormat("H:mm", Locale.ROOT).format(new Date(at));
    }

    /** "jeudi 22 octobre", "domingo, 18 de outubro", "Sunday, October 18". */
    static String date(long now) {
        if (LANG_FR.equals(lang)) return new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(new Date(now));
        if (LANG_PT.equals(lang)) return new SimpleDateFormat("EEEE, d 'de' MMMM", PT).format(new Date(now));
        return new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(new Date(now));
    }

    /** French for every English string passed to tr(). */
    private static final java.util.Map<String, String> FR = new java.util.HashMap<>();
    static {
        String[][] pairs = {
            {"Add to favorites", "Ajouter aux favoris"}, {"All apps", "Toutes les apps"},
            {"Android's own selection", "Sélection d’Android"}, {"App info", "Infos de l’app"}, {"Apps", "Apps"},
            {"Choose the default", "Choisir l’accueil par défaut"}, {"Cyber news", "Actus cyber"},
            {"Default home screen", "Écran d’accueil par défaut"}, {"Favorite apps", "Apps favorites"},
            {"Hide from All apps", "Masquer de Toutes les apps"}, {"Home screen", "Écran d’accueil"},
            {"If another one is enabled", "S’il en existe un autre activé"}, {"Live TV", "TV en direct"},
            {"Loading…", "Chargement…"}, {"Move left", "Déplacer à gauche"}, {"Move right", "Déplacer à droite"},
            {"Network & Internet", "Réseau et Internet"}, {"No headlines yet.", "Pas encore d’actus."},
            {"No link for this headline.", "Pas de lien pour cette actu."},
            {"Not available on this TV.", "Indisponible sur cette TV."},
            {"No web browser installed.", "Aucun navigateur installé."}, {"Off", "Désactivé"},
            {"OK · all headlines", "OK · toutes les actus"}, {"On", "Activé"}, {"Open", "Ouvrir"},
            {"Open another home", "Ouvrir un autre accueil"}, {"Open Hestia", "Ouvrir Hestia"}, {"Other", "Autre"},
            {"Photo: ", "Photo : "}, {"Photos of the day: Bing", "Photos du jour : Bing"}, {"Picture", "Image"},
            {"Preview", "Aperçu"}, {"Remotes & accessories", "Télécommandes et accessoires"},
            {"Remove from favorites", "Retirer des favoris"}, {"Screensaver", "Économiseur d’écran"},
            {"Settings", "Réglages"}, {"Show in All apps", "Afficher dans Toutes les apps"}, {"Sound", "Son"},
            {"TV & inputs", "TV et entrées"}, {"TV settings", "Réglages de la TV"},
            {"TV Settings", "Réglages de la TV"}, {"Uninstall", "Désinstaller"},
            {"View in Play Store", "Voir sur le Play Store"},
            {"Without changing the default", "Sans changer l’accueil par défaut"},
            {"Your home screen, simple and beautiful.", "Ton écran d’accueil, simple et beau."},
            {"Restore from your computer: tools/tv_safe.py restore", "Restaurer depuis l’ordinateur : tools/tv_safe.py restore"},
            {"OK adds or removes. The order you pick is the order of the first row. Back when done.",
                "OK ajoute ou retire. L’ordre choisi est celui de la première rangée. Retour pour terminer."},
            {" headlines · OK opens in the browser · Back to leave", " actus · OK ouvre dans le navigateur · Retour pour quitter"},
            {"Hidden apps", "Apps masquées"}, {"Sort all apps", "Tri de Toutes les apps"},
            {"A–Z", "A–Z"}, {"Most used", "Les plus utilisées"}, {"Recently used", "Récemment utilisées"},
            {"Language", "Langue"}, {"Automatic", "Automatique"},
            {"OK hides or shows. Hidden apps stay installed. Back when done.",
                "OK masque ou affiche. Les apps masquées restent installées. Retour pour terminer."},
            {"Hidden", "Masquée"},
        };
        for (String[] p : pairs) FR.put(p[0], p[1]);
    }

    /** Spinning arc (work in progress). */
    static void spinner(Canvas c, float cx, float cy, float r, float seconds, Paint stroke) {
        OVAL.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(OVAL, (seconds * 360f) % 360f, 270f, false, stroke);
    }
}
