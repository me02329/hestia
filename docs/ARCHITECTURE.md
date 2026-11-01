# Architecture

Hestia est une app Android en Java 8 sans Gradle ni bibliothèque : tout l'écran est dessiné à la main sur un `Canvas`
de 1920 × 1080. Le code est découpé en couches, chacune ne connaît que celles du dessous. Un test
(`tests/test_architecture.py`) fait échouer la CI si une couche importe ce qu'elle ne doit pas.

```
io.hestia.tv            composants du manifeste : activités et services, fins, qui assemblent le reste
│
├── home  settings  news  dream        les écrans
│        │
│        ├── media                     lecture en cours et carte du lecteur
│        ├── panel                     panneau translucide réutilisable (panneau rapide, menus)
│        ├── ui                        boîte à outils du dessin : CanvasView, ressorts, polices, touches
│        ├── remote                    touches de la télécommande → actions
│        ├── power                     délai de mise en veille
│        ├── apps                      apps, entrées TV, lancement
│        └── data                      météo, actus, photos : réseau borné et cache
│
└── core                               préférences, langue, heure, écrans, journal, watchdog
```

| Paquet | Peut utiliser | Rôle |
|---|---|---|
| `core` | rien | `Prefs`, `I18n`, `TimeText`, `Keys`, `Screens`, `Background`, `Watchdog`, `AppLog` |
| `data` | `core` | `Feed` (socle) → `Weather`, `News`, `Photos` ; `Net` (frontière réseau), `FeedText`, `WeatherText` |
| `apps` | `core` | `AppCatalog`, `AppTiles`, `TvInputs`, `Launcher`, `HomeRole`, `Card` |
| `power` | `core` | `SleepDelay` (écriture système), `SleepPolicy` (règles) |
| `remote` | `core`, `apps` | `KeyBindings`, `KeyRouter`, `KeyAction` |
| `ui` | `core`, `data` | `CanvasView`, `HestiaActivity`, `FocusSprings`, `Press`, `Backdrop`, `Fonts`, `Paints`, `Colors`, `Spring`, `Symbols`, `Click` |
| `panel` | `core`, `ui` | `PanelActivity`, `PanelView`, `PanelEntry`, `PanelHeader` |
| `media` | `core`, `ui`, `panel` | `NowPlaying`, `PlayerCard` |
| `home`, `settings`, `news`, `dream` | ce qu'il leur faut en dessous | un écran chacun ; `home` : `HomeView`, `HomeHeader`, `CardRow`, `PhotoCarousel` |

## Trois règles à connaître

**Les composants du manifeste restent à la racine.** Le rôle HOME, `screensaver_components`, le service d'accessibilité
et `tools/tv_safe.py` désignent ces classes par leur nom complet sur la TV. En déplacer une casse l'installation
existante. La logique vit dans les paquets, la classe du manifeste reste fine.

**Le code sans Android est testé sur une vraie JVM.** Les règles qu'on peut isoler (`I18n`, `TimeText`, `Freshness`,
`WeatherText`, `SleepPolicy`, `KeyAction`, `Colors`, `Spring`, `Net`, `FeedText`) n'importent aucun type Android :
`tests/jvm.py` les compile et les exécute avec `tests/java/`. Une règle nouvelle va de préférence dans une telle classe.

**Les fichiers restent courts.** 500 lignes au maximum, vérifié par le même test.

## Ce que les tests ne couvrent pas

Rien ne fait tourner l'app sur un Android, émulé ou non. Le dessin, le focus, les touches et le lancement des apps se
valident sur la TV ; les résultats sont consignés dans `TRIAL.md`.
