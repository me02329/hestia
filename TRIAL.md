# Premier essai sur la TCL — 19 octobre 2026

- Association ADB sans fil TLS réussie après autorisation de l’utilisateur.
- TV confirmée : TCL Smart TV Pro, Android 14 / API 34.
- Aucun paquet désactivé renvoyé par `pm list packages -d` avant installation.
- Sauvegarde locale créée : `backups/tcl-original.json` (non versionnée).
- Installation de Free My TV 1.1-safe réussie.
- Aperçu lancé explicitement avec `am start -W -n io.freemytv/.HomeActivity`.
- Android indique `Status: ok`, démarrage à froid : 1091 ms ; processus présent.
- Aucune exception AndroidRuntime relevée dans les journaux consultés après démarrage.
- Vérification après lancement : l’accueil reste Google TV LauncherX, l’économiseur reste
  `com.google.android.apps.tv.dreamx/.service.Backdrop`.
- Aucune bascule d’accueil par défaut, aucun changement d’économiseur, aucune désactivation de paquet.

Retour visuel et tests D-pad/applications par l’utilisateur encore attendus.
Veille, réveil, redémarrage et cycle de bascule/restauration non testés à ce stade.
Le temps de lancement mesuré ne constitue pas une mesure de fluidité ou de stabilité prolongée.

Les ports et codes d’association temporaires ne sont pas conservés dans ce compte rendu.

Le retour utilisateur signale des éléments manquants en haut. La capture confirme que titre et onglets sont visibles une fois le panneau inférieur fermé, mais que les cartes de recommandation restent grises car la clé TMDB locale est vide. Version 1.2-safe : les cartes sans données expliquent cette configuration. Mise à jour en cours pour poursuivre l’essai.

## Tentative de bascule — 20 octobre 2026

- `activate` (accueil seul, sans économiseur) échoue trois fois avec restauration automatique.
  Le journal de la veille en `phase: restored` correspondait déjà à un échec de ce type, pas à un cycle réussi.
- Cause relevée : `set-home-activity` répond `Success` et attribue bien le rôle `android.app.role.HOME` à
  `io.freemytv`, mais la résolution HOME reste sur Google TV LauncherX. Son filtre HOME est déclaré en
  `priority=2` (Free My TV : `priority=0`) ; Android choisit l’activité de plus haute priorité avant le rôle.
- Effet de bord corrigé : après l’échec, le rôle HOME restait attribué à `io.freemytv` alors que l’outil
  annonçait une restauration. Rôle remis manuellement sur LauncherX (valeur du snapshot) et vérifié.
  `tv_safe.py` réapplique désormais l’accueil sauvegardé après une activation non vérifiée, attend jusqu’à 10 s
  la propagation du rôle et affiche la cause d’un échec.
- État final vérifié : accueil et rôle sur LauncherX, économiseur inchangé, aucun paquet désactivé.
- Conclusion : sur ce firmware, la bascule sans désactiver LauncherX n’est pas possible par le rôle HOME.

## Bascule effective et version 1.3-safe — 21 octobre 2026

- Sur décision explicite du propriétaire (« on test », sauvegarde et reset d’usine acceptés), désactivation
  manuelle par ADB (`pm disable-user --user 0`) de `com.google.android.apps.tv.launcherx` et
  `com.google.android.tungsten.setupwraith`, puis `set-home-activity` vers Free My TV. Écart assumé par rapport
  à la règle « garder le launcher d’origine activé » ; fait hors de `tv_safe.py`, dont le journal ne le couvre pas.
  Commandes de retour : `backups/home-components-2026-10-20.txt` (non versionné).
- ADB et le Wi-Fi ont décroché juste après ; Wi-Fi rétabli par le propriétaire, lien de cause non établi.
  Après redémarrage, la TV démarre bien sur Free My TV.
- L’Ambient Mode Google (`dreamx/Backdrop`) s’arrêtait seul sans le launcher : la TV passait directement en veille.
  Économiseur passé sur `io.freemytv/io.freemytv.Screensaver` (valeur d’origine dans `backups/tcl-original.json`).
- 1.3-safe : recommandations TMDB et cours crypto retirés ; accueil refait (photo du jour Bing sans clé, heure,
  météo de la ville configurée, rangées Apps / TV et entrées / Toutes les apps) ; économiseur en diaporama (45 s par photo).
  Installée, capture de l’accueil et aperçu de l’économiseur vérifiés sur la TV.
- Restent à vérifier par le propriétaire : navigation D-pad réelle, lancement des apps et HDMI, déclenchement
  automatique de l’économiseur après inactivité (musique non coupée), veille/réveil.

## Hestia 1.4-safe — 22 octobre 2026

- Renommage visible en « Hestia » (libellés, icône, bannière via `tools/make_hestia_art.py`) ; paquet inchangé
  (`io.freemytv`) pour conserver le rôle d’accueil et les données.
- Apps favorites choisies dans les réglages (ordre de sélection, 10 max), enregistrées dans les préférences.
- Bandeau d’actus cyber (The Hacker News, BleepingComputer, SecurityWeek, CERT-FR, ZATAZ ; titres seulement,
  24 h ou 48 h si trop peu), désactivable dans les réglages. Hôtes ajoutés à la liste fixe de `Net.java`.
- Fondu des cartes aux bords de l’écran ; réglages refaits sur fond photo ; ancien fond animé supprimé.
- Vérifié sur la TV par captures : accueil avec actus, réglages, grille des favoris ; aucun crash dans logcat.
  Navigation réelle à la télécommande et changement de favoris restent à valider par le propriétaire.

## Hestia 1.5-safe — 22 octobre 2026

- Emblème fourni par le propriétaire vectorisé (`tools/vectorize_logo.py`, potrace) : SVG et PNG transparents dans
  `branding/`, VectorDrawable affiché sur l’accueil (en haut au centre) et dans les réglages ; icône et bannière
  regénérées depuis l’emblème. Cartes « TV & inputs » colorées. Vérifié par captures sur la TV.

## Hestia 1.6-safe — 22 octobre 2026

- Actus : rotation 12 s (en pause quand le titre a le focus). ↑ depuis les apps sélectionne le titre ; OK ouvre la
  liste des actus (NewsActivity) ; OK sur une actu ouvre son lien https dans le navigateur de la TV (TV Bro).
  Vérifié sur la TV : focus, liste de 40 titres, ouverture d’un article.

## Hestia 1.7-safe — 22 octobre 2026

- Télécommande, diagnostiquée par capture `getevent` + logcat : Settings arrive en `KEYCODE_NOTIFICATION`,
  la touche TV en code TCL 4001 (le `KEYCODE_GUIDE` réinjecté par TCL n’atteint pas les apps), l’appui long sur
  Home déclenche `TOGGLE_NOTIFICATION_HANDLER_PANEL` que gérait le launcher Google TV.
- Ajouts : panneau rapide Hestia (sources, image, son, réseau, accessoires, réglages) sur Settings et appui long
  Home ; touche TV → TV en direct ; appui long OK → menu d’app (favoris, déplacer, masquer, infos, Play Store,
  désinstaller avec confirmation Android) ; tri de Toutes les apps, apps masquées, carte Play Store.
- Langue : Auto / Français / English / Português dans les réglages ; heure 24 h, date et météo en français.
- Vérifié sur la TV par touches simulées : Settings, appui long OK, touche TV (ouvre `com.tcl.tv`).
  L’appui long réel sur Home reste à confirmer à la télécommande.

## Hestia 1.8-safe — 22 octobre 2026

- « En cours de lecture » dans le panneau rapide (titre, app, lecture/pause, suivant, précédent) via les sessions
  média. Accès aux notifications accordé par ADB au service vide `MediaListener`
  (`cmd notification allow_listener io.freemytv/io.freemytv.MediaListener`) ; aucune notification n’est lue.
  Retrait : `cmd notification disallow_listener io.freemytv/io.freemytv.MediaListener`.
- Vérifié sur la TV : session Spotify en pause affichée. Commandes de lecture non déclenchées pendant le test.
- Lecteur dans le panneau rapide : pochette, titre/artiste, progression en direct, précédent/lecture/suivant
  (← → entre les boutons). Vérifié par capture avec la session Spotify en pause.
- AirPlay : récepteur TCL `com.tcl.airplay2` + démon MediaTek, service de premier plan permanent (normal) ;
  écran AirPlay visible pour la dernière fois le 2026-09-03 d’après usagestats. Déclencheur non encore observé.
