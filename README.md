# Hestia

<img src="branding/hestia-logo.png" alt="Hestia" width="160" align="right">

Écran d’accueil Android TV simple, sans compte ni clé, pensé pour une TCL Smart TV Pro (G08, Android 14).
Hestia — déesse grecque du foyer — remplace l’accueil Google TV.

Projet dérivé de *Free My TV* (licence MIT, voir [LICENSE](LICENSE)), largement réécrit : client ADB embarqué,
désactivations automatiques, recommandations TMDB et cours crypto retirés.

## Ce que fait Hestia

- **Fond :** photo du jour (archive Bing, sans clé), nouvelle toutes les 2 minutes.
- **En-tête :** heure et date, emblème Hestia, météo Open-Meteo de la ville configurée.
- **Actus cyber :** titres des dernières 24 h (The Hacker News, BleepingComputer, SecurityWeek, CERT-FR, ZATAZ),
  un toutes les 12 s, désactivable ; ↑ puis OK ouvre la liste et les articles dans le navigateur.
- **Rangées :** apps favorites (choisies dans les réglages), TV en direct et entrées HDMI, toutes les apps.
- **Économiseur :** diaporama des photos du jour, 45 s par photo, avec heure, météo et un titre cyber.
- **Réglages :** apps favorites, apps masquées, tri de Toutes les apps, langue (auto/français/anglais/portugais),
  actus, aperçu de l’économiseur, choix de l’accueil via Android, stockage libre.
- **Télécommande :** touche TV, touche Réglages (sur l’accueil) et appui long Home (partout) configurables dans
  les réglages ; par défaut, Settings et appui long Home → panneau rapide (lecteur de la musique/vidéo en cours avec
  pochette et progression, sources, image, son, réseau, accessoires) ; touche TV → TV en direct ; appui long OK sur
  une app → menu (favoris, déplacer, masquer, infos, Play Store, désinstaller).
- **Rangée TV :** TV en direct, HDMI, recherche vocale Google, Play Store, Hestia, réglages TV (cartes colorées).
- **Robustesse :** après trois plantages Java en cinq minutes, écran de récupération (autre accueil, réglages,
  réessayer).

Accès réseau limité à une liste fixe d’hôtes HTTPS dans `Net.java`, sans redirections, réponses bornées.
Le lecteur du panneau rapide demande l’accès aux notifications pour le service vide `MediaListener`
(`adb shell cmd notification allow_listener io.freemytv/io.freemytv.MediaListener`) ; aucune notification n’est lue.

## Construire

Python 3.10+, JDK **17**, SDK Android **34**, Build Tools **34.0.0**, `zip`. Android **13 minimum**.

```sh
python3 -m unittest discover -s tests -v
./build.sh          # -> Hestia.apk + Hestia.apk.sha256
```

`config.properties` est créé depuis `config.example.properties` au premier build : y mettre la latitude,
la longitude et le nom de la ville. Garder `freemytv.keystore` **et** `freemytv.keystore.pass` : une mise à jour
signée avec une autre clé est refusée par Android. Réglages, clés et sauvegardes de TV restent hors de Git.

Visuels : `tools/vectorize_logo.py` (emblème → SVG, PNG transparent, VectorDrawable) et
`tools/make_hestia_art.py` (icône et bannière).

## Installer et basculer

Voir [SAFE-DEPLOYMENT.md](SAFE-DEPLOYMENT.md) et le journal d’essai [TRIAL.md](TRIAL.md).

Sur ce firmware, le launcher Google TV a une priorité système supérieure : le rôle HOME seul ne suffit pas.
`tools/tv_safe.py disable-stock-home` désactive uniquement `com.google.android.apps.tv.launcherx` et
`com.google.android.tungsten.setupwraith` (en l’enregistrant), et `enable-stock-home` les remet avec l’accueil et
l’économiseur d’origine. Détails dans [SAFE-DEPLOYMENT.md](SAFE-DEPLOYMENT.md).

## Limites

Les tests simulent la TV (bascule/restauration) et vérifient les règles réseau sur une JVM, sans Internet.
Une compilation réussie ne remplace pas un essai sur la TV. Le watchdog ne couvre pas les ANR, crashs natifs ou
arrêts forcés.
