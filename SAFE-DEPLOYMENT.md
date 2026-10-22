# Installer, basculer et revenir en arrière (TCL G08, Android 14)

Toutes les commandes passent par une connexion ADB déjà associée (débogage sans fil TLS) et un **serial explicite**.
Rien n’est désactivé ni modifié par l’app elle-même : les changements système passent par `tools/tv_safe.py`,
qui vérifie l’identité de la TV (série, firmware, utilisateur 0) contre une sauvegarde et relit chaque valeur écrite.

```sh
ADB="$HOME/Android/Sdk/platform-tools/adb"
"$ADB" pair IP:PORT CODE            # écran « Débogage sans fil » de la TV
"$ADB" devices -l
TV_SERIAL='identifiant exact affiché par adb devices'
BACKUP=backups/tcl-original.json    # privée, hors Git ; garder aussi ses fichiers .journal.json / .stock.json
```

## 1. Sauvegarder, installer, essayer

```sh
python3 tools/tv_safe.py snapshot --adb "$ADB" --serial "$TV_SERIAL" --backup "$BACKUP"   # ne modifie rien
./build.sh && sha256sum -c Hestia.apk.sha256
"$ADB" -s "$TV_SERIAL" install -r Hestia.apk
"$ADB" -s "$TV_SERIAL" shell am start -n io.hestia.tv/.HomeActivity                    # aperçu
```

La sauvegarde garde l’accueil et l’économiseur d’origine ; elle refuse d’écraser une sauvegarde existante.

## 2. Basculer l’accueil

Sur un firmware qui respecte l’accueil choisi :

```sh
python3 tools/tv_safe.py activate --adb "$ADB" --serial "$TV_SERIAL" --backup "$BACKUP" [--screensaver]
```

Sur la TCL G08, le launcher Google TV a une priorité système supérieure : `activate` échoue proprement et restaure.
Il faut alors désactiver le launcher Google TV et l’écran de récupération du setup (et rien d’autre) :

```sh
python3 tools/tv_safe.py disable-stock-home --adb "$ADB" --serial "$TV_SERIAL" --backup "$BACKUP"
"$ADB" -s "$TV_SERIAL" shell settings put secure screensaver_components io.hestia.tv/io.hestia.tv.Screensaver
"$ADB" -s "$TV_SERIAL" shell cmd notification allow_listener io.hestia.tv/io.hestia.tv.MediaListener   # lecteur
```

Touche Réglages dans les autres apps (facultatif) : service d’accessibilité limité aux touches. Vérifier d’abord la
valeur existante (`settings get secure enabled_accessibility_services`) et l’ajouter avec `:` si elle n’est pas vide.

```sh
"$ADB" -s "$TV_SERIAL" shell settings put secure enabled_accessibility_services io.hestia.tv/io.hestia.tv.RemoteKeysService
"$ADB" -s "$TV_SERIAL" shell settings put secure accessibility_enabled 1
```

L’économiseur Ambient de Google cesse de fonctionner sans son launcher (la TV passait directement en veille) :
d’où l’économiseur Hestia. L’accès aux notifications sert uniquement au lecteur du panneau rapide.

## 3. Revenir à Google TV

```sh
python3 tools/tv_safe.py enable-stock-home --adb "$ADB" --serial "$TV_SERIAL" --backup "$BACKUP"
```

Réactive les paquets enregistrés, remet l’accueil de la sauvegarde et, s’il pointe encore vers Hestia, l’économiseur
d’origine. Si la bascule a été faite à la main (sans fichier `.stock.json`, cas de la TV actuelle), ajouter
`--assume-stock`. `restore` reste la commande de retour après un `activate` réussi.

## Ce qui est vérifié

- Tests simulés (`python3 -m unittest discover -s tests -v`) : identité incorrecte, état concurrent, écriture refusée,
  coupure ADB, reprise, journal, bascule/retour du launcher d’origine, paquets étrangers refusés ; règles réseau et
  filtrage des liens RSS sur une JVM réelle.
- Essais réels sur la TV : voir [TRIAL.md](TRIAL.md). Une compilation réussie ne remplace pas un essai sur la TV.
