# Essai et restauration sur la TCL

Ce guide décrit la branche locale `harden/tcl-safe-launcher`, pas la version amont.
**Aucune commande ci-dessous n’a été exécutée sur la TV pendant le développement.**

## Préparation

Le relevé du 4 septembre 2026 identifiait une TCL G08 Android 14 avec débogage sans fil TLS.
Le port et l’adresse actuels doivent être relus. L’outil ne fait ni association automatique,
ni ouverture du port 5555, ni changement de paramètres réseau/débogage.
Si nécessaire, utiliser les écrans Android de débogage sans fil et les commandes `adb pair` / `adb connect`.
Ne pas conserver les codes d’association dans Git ou dans les journaux du projet.

Outils locaux préparés sur ce PC :

- SDK : `$HOME/Android/Sdk`, platform 34, Build Tools 34.0.0 ;
- JDK 17 : `$HOME/.local/share/free-my-tv/jdk17` (détecté par `build.sh`) ;
- ADB : `$HOME/Android/Sdk/platform-tools/adb`.

Construire après personnalisation de `config.properties`. Les coordonnées de l’exemple ne sont pas celles
du propriétaire de la TV. Garder les deux fichiers de signature locaux ensemble pour les futures mises à jour.

```sh
./build.sh
sha256sum -c FreeMyTV.apk.sha256
ADB="$HOME/Android/Sdk/platform-tools/adb"
"$ADB" devices -l
TV_SERIAL='identifiant exact affiché par adb devices'
```

Tous les appels ciblent explicitement `TV_SERIAL`. L’outil refuse les profils autres que l’utilisateur 0.
Les exemples supposent un shell Linux/macOS ; l’outil Python utilise les verrous POSIX.

## Sauvegarder puis installer pour un aperçu

```sh
python3 tools/tv_safe.py status --adb "$ADB" --serial "$TV_SERIAL"
python3 tools/tv_safe.py snapshot --adb "$ADB" --serial "$TV_SERIAL" --backup backups/tcl-original.json
"$ADB" -s "$TV_SERIAL" install -r FreeMyTV.apk
"$ADB" -s "$TV_SERIAL" shell am start -n io.freemytv/.HomeActivity
```

`snapshot` ne modifie pas la TV. Son fichier privé contient l’identité matérielle, le firmware,
l’utilisateur, le composant HOME résolu et la valeur exacte de `screensaver_components` (y compris son absence).
Il refuse d’écraser une sauvegarde existante. **Garder le dossier `backups/` sur le PC.**

Avant de sélectionner Free My TV par défaut : tester D-pad, OK, Retour, ouverture des applications,
réglages image/son et sources HDMI. Le bouton Home doit encore revenir à l’accueil d’origine.
Ne pas choisir un nouvel accueil via les réglages Android pendant cette étape : la bascule journalisée suit.

Cette procédure vise une TV dont les applications système sont encore activées. Si la version amont a déjà
été utilisée pour les désactiver, restaurer d’abord son état initial ; cet outil ne réactive pas des paquets
sur la base d’hypothèses.

## Basculer, puis vérifier sur la TV

```sh
python3 tools/tv_safe.py activate --adb "$ADB" --serial "$TV_SERIAL" --backup backups/tcl-original.json
```

Seul l’accueil par défaut change. Aucun paquet n’est désactivé. L’économiseur reste inchangé.
Pour changer aussi l’économiseur, ajouter explicitement `--screensaver` à cette première activation.
Après une activation, restaurer avant de relancer une activation avec d’autres options.

Le programme vérifie l’identité de la TV et le maintien de l’état sauvegardé, persiste son intention dans
`backups/tcl-original.json.journal.json`, applique puis relit chaque valeur. Une erreur ou Ctrl+C déclenche
une tentative de restauration. Une rupture ADB ou une panne du PC peut empêcher cette tentative : le journal
reste disponible pour reprendre la restauration après reconnexion. Un arrêt brutal ne garantit pas un retour
automatique immédiat.

Sur la TV, vérifier :

1. Home ouvre Free My TV ; Retour et navigation ne bloquent pas.
2. Netflix/YouTube/Prime, micro, Sources, HDMI-CEC et réglages TCL fonctionnent encore.
3. TV Control Center et son raccourci Settings fonctionnent encore, si utilisés.
4. Mise en veille, réveil et redémarrage conservent un comportement utilisable.
5. La restauration ci-dessous fonctionne, puis une nouvelle activation fonctionne également.

Si le firmware reprend l’accueil Google TV, l’outil l’affichera lors d’un nouveau `status`.
Ne pas résoudre ce cas par une désactivation improvisée de composants système.

## Restaurer

```sh
python3 tools/tv_safe.py restore --adb "$ADB" --serial "$TV_SERIAL" --backup backups/tcl-original.json
```

La commande remet l’accueil sauvegardé et, seulement si cette opération l’avait demandé, l’économiseur
sauvegardé. Elle vérifie le résultat et peut être relancée après interruption. Elle ne touche pas aux
applications, à leurs données, ni aux autres réglages de la TV.

Si une autre action a entre-temps changé l’accueil ou l’économiseur, le programme refuse d’écraser la ressource
en conflit et tente de restaurer les autres ressources sans conflit. Il signale une restauration incomplète.
Après une mise à jour du firmware, il refuse aussi l’ancienne sauvegarde : relire l’état avant toute intervention.
Ne pas éditer le JSON pour contourner ces vérifications. Le lien SHA-256 entre sauvegarde et journal détecte
un mélange ou une modification accidentelle ; ce n’est pas une signature contre un attaquant local.

Presser Home pour vérifier le retour. Désinstaller l’application seulement après restauration réussie.
Une désinstallation directe ne restaure pas un éventuel réglage d’économiseur pointant vers elle.

## Récupération dans l’application

Après trois plantages Java rapprochés, l’accueil propose un écran simple : ouvrir un autre accueil,
ouvrir les réglages TV, ou réessayer. Ouvrir l’ancien accueil **ne change pas** le choix par défaut.
Le retour durable se fait par `restore` ou par le sélecteur Android s’il est disponible sur le firmware.
Ce mécanisme ne couvre pas les crashs natifs, ANR, arrêts forcés, ni les pannes système.

## Ce qui est vérifié localement

- Compilation Android, signature APK et alignement : vérifiés sur le PC.
- Tests simulés : identité incorrecte, changement d’état concurrent, écriture refusée, panne de disque,
  interruption, déconnexion ADB, reprise, journal persistant, restauration répétée et conflits.
- Tests JVM hors réseau : destinations refusées, absence de redirections, plafonds de réponse et erreurs sans clé.
- Pas de test matériel TCL effectué pendant cette préparation ; les contrôles ci-dessus restent nécessaires.

Les métriques annoncées par l’auteur pour sa TV 98 pouces ne sont pas des mesures de cette version sur la G08.
