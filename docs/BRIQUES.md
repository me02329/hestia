# Briques centrales

À lire **avant d'écrire une fonctionnalité**. Chaque mécanique ci-dessous est écrite à un seul endroit : on l'appelle,
on ne la recode pas. `tests/test_architecture.py` vérifie celles qu'on peut vérifier (colonne « Contrôlé »).

| Pour… | Utiliser | Ne pas… | Contrôlé |
|---|---|---|---|
| lire ou écrire un réglage | `Prefs.xxx(c)` ; nouveau réglage : un accesseur typé dans `core/Prefs` | `getSharedPreferences("home", …)` | oui |
| ouvrir quoi que ce soit | `Launcher.start`, `startOver` (Retour y revient), `startOrSay`, `openApp`, `openCard` | `startActivity` et son `try/catch` | oui |
| atteindre un écran de Hestia | `Screens.settings(c)`, `Screens.news(c, titre)`… | citer une activité depuis une couche basse | oui |
| dessiner un écran | `extends CanvasView` : `drawScene`, `animate`, `wake()` | gérer `Choreographer`, l'échelle ou `dt` | oui |
| animer le focus d'une liste | `FocusSprings` : `resize`, `focus`, `value`, `step` | une `List<Spring>` à la main | non |
| suivre OK ou Retour | `Press.track(e)` → `CLICK`, `LONG` | `okDown`, `okLong`, `backDown` | non |
| reconnaître une touche | `Keys.isOk`, `isSettings`, `isTv` | des `KEYCODE_` ou `4001` en dur | en partie |
| brancher une touche de la télécommande | `KeyRouter` ; nouvelle touche : une constante dans `KeyBindings` | ouvrir l'action depuis l'écran | non |
| ajouter une source réseau | `extends Feed` : `fetch()` qui lève une exception en cas d'échec (attente croissante entre les essais), `notifyListeners()` ; hôte dans `Net` | un thread, un cache, un calcul de fraîcheur à soi | oui |
| faire une requête | `Net.json`, `Net.feed`, `Net.download` | `openConnection` | oui |
| un travail d'écran hors du fil principal | `Background.run`, `Background.onMain` | un `Executor` par classe | oui |
| écrire un texte visible | `I18n.tr("pt", "en")` + le français dans la table de `I18n` | une chaîne en dur | oui, le français |
| afficher une heure ou une date | `TimeText.clock`, `date`, `dateLine`, `minutes` | `SimpleDateFormat` sur place | non |
| afficher la photo derrière un écran | `Backdrop` | charger et décoder à la main | non |
| ajouter une ligne aux réglages | une `SettingsRow` dans `buildMainRows()` | un index et un `else if` | non |
| ajouter une ligne au panneau rapide | une `PanelEntry` dans `entries()` | modifier `PanelView` | non |
| ajouter une carte au-dessus d'un panneau | `implements PanelHeader` | du dessin dans l'activité | non |
| ouvrir une page web | `Launcher.openLink` (évite le navigateur vide d'Android) | chercher un navigateur à la main | non |
| changer un réglage système | ne pas le faire : voir `docs/adr/001` et `002` | `Settings.*.put` hors de `SleepDelay` | oui |
