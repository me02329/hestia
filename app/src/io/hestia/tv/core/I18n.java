package io.hestia.tv.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The UI's language: French, English or Portuguese. Plain Java: tested on a JVM (tests/java). Portuguese and English are
 * written at each tr() call; French comes from the table below, keyed by the English text (a test checks that every
 * call has its French).
 */
public final class I18n {
    private I18n() {}

    public static final Locale PT = new Locale("pt", "BR");
    public static final String AUTO = "auto", LANG_EN = "en", LANG_FR = "fr", LANG_PT = "pt";
    /** The language in use. "auto" follows the TV (French, Portuguese, otherwise English). */
    public static volatile String lang = LANG_EN;

    public static void apply(String choice) {
        lang = resolve(choice);
    }

    static String resolve(String choice) {
        String l = choice == null ? "" : choice.trim().toLowerCase(Locale.ROOT);
        if (l.startsWith("en")) return LANG_EN;
        if (l.startsWith("fr")) return LANG_FR;
        if (l.startsWith("pt")) return LANG_PT;
        String device = Locale.getDefault().getLanguage();
        return "fr".equals(device) ? LANG_FR : "pt".equals(device) ? LANG_PT : LANG_EN;
    }

    public static Locale locale() {
        return LANG_FR.equals(lang) ? Locale.FRANCE : LANG_PT.equals(lang) ? PT : Locale.US;
    }

    /** Portuguese or English as written at the call; French from the FR table. */
    public static String tr(String pt, String english) {
        if (LANG_PT.equals(lang)) return pt;
        if (LANG_FR.equals(lang)) {
            String fr = FR.get(english);
            return fr != null ? fr : english;
        }
        return english;
    }

    /** French for every English string passed to tr(). */
    private static final Map<String, String> FR = new HashMap<>();
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
            {"Hidden", "Masquée"}, {"Play", "Lecture"}, {"Pause", "Pause"}, {"Next", "Suivant"},
            {"Previous", "Précédent"}, {"Open ", "Ouvrir "},
            {"Hestia crashed several times.\nThe default home screen has not been changed.",
                "Hestia a planté plusieurs fois.\nL’accueil par défaut n’a pas été modifié."},
            {"Apps (re-enable Google TV)", "Applications (réactiver Google TV)"}, {"Another home", "Autre accueil"},
            {"Retry Hestia", "Relancer Hestia"}, {"Remote buttons", "Touches de la télécommande"},
            {"TV button", "Touche TV"}, {"Settings button", "Touche Réglages"},
            {"Home (press and hold)", "Home (appui long)"}, {"Quick panel", "Panneau rapide"}, {"Search", "Rechercher"},
            {"free of", "libres sur"}, {"Storage", "Stockage"},
            {"Sleep after", "Mise en veille"}, {"Never", "Jamais"},
            {"Status", "État"}, {"New version", "Nouvelle version"}, {"Working", "Actif"}, {"Not working", "Inactif"},
            {"Buttons in any app", "Touches dans toutes les apps"}, {"Panel's player", "Lecteur du panneau"},
            {"Sleep delays", "Délais de veille"}, {"Weather", "Météo"}, {"Photos of the day", "Photos du jour"},
            {"Failing · ", "En échec · "}, {"Turn off in", "Éteindre dans"}, {"Screensaver after", "Économiseur après"},
            {"The TV turns off in 1 min", "La TV s’éteint dans 1 min"},
            {"City", "Ville"}, {"City name", "Nom de la ville"}, {"No name", "Sans nom"}, {"Cancel", "Annuler"},
            {"Search for a city…", "Rechercher une ville…"}, {"Searching…", "Recherche…"},
            {"Search failed. Check the network.", "Échec de la recherche. Vérifie le réseau."},
            {"No city found.", "Aucune ville trouvée."},
            {"TV button (press and hold)", "Touche TV (appui long)"},
            {"In any app, with the accessibility service", "Dans toute app, avec le service d’accessibilité"},
        };
        for (String[] p : pairs) FR.put(p[0], p[1]);
    }
}
