# Audit technique et fonctionnel de Hestia

Date : 22 octobre 2026. Référence auditée : commit `d5c112c`, version `1.11-safe`.

Ce document restitue l'audit du code, de l'architecture, du build, des tests et des fonctionnalités. Il constitue un état des lieux et un backlog ; les problèmes ci-dessous ne sont pas encore corrigés. Le formatage et les outils de lint sont hors périmètre.

## Synthèse

La base est cohérente pour un launcher Android TV léger. Les protections réseau et les outils de déploiement ont reçu une attention particulière. Les principaux axes de progrès sont la fiabilité des scénarios d'échec, la gestion du cycle de vie Android et la séparation entre rendu, navigation, données et intégrations système.

Il n'est pas nécessaire de réécrire toute l'application en Kotlin ou Compose. Java et Canvas peuvent être conservés. Un build Android standard, des composants mieux délimités et des tests du comportement apporteraient davantage à court terme.

Ordre recommandé : récupération ADB et bugs de cache/favoris, cycle de vie et tests, build et architecture, puis nouvelles fonctionnalités.

## Périmètre et niveau de preuve

- Lecture du code Java, du manifeste, du build, des outils Python, des tests, de la CI et de la documentation.
- Exécution de tests locaux et de simulations sans contacter ni modifier la TV.
- Aucune nouvelle compilation APK ni validation sur appareil pendant cet audit.
- Les constats Android issus de la lecture du code restent à vérifier sur appareil ou avec des tests instrumentés.
- Les mesures de mémoire et de démarrage du README n'ont pas été revérifiées.

Priorités : **P1** = récupération ou fiabilité importante ; **P2** = fonctionnement ou maintenabilité ; **P3** = amélioration secondaire. Une priorité n'implique pas qu'une panne a été observée sur la TV.

## Points solides à préserver

- Frontière réseau centralisée dans `Net.java` : origines HTTPS fixes, redirections refusées, réponses bornées et délais de lecture.
- Téléchargements via des fichiers temporaires et publication du cache photo par renommage.
- Absence de shell, d'ADB et de désactivation de paquets dans l'application.
- Permissions limitées et services système protégés par les permissions de liaison Android.
- Sauvegardes et journaux de déploiement, vérification de l'identité de la TV et relecture des changements.
- Configuration personnelle, clés de signature et sauvegardes exclues de Git.
- Actions GitHub épinglées par SHA et permissions CI limitées.
- Journal d'essais distinguant ce qui est simulé, compilé et effectivement vérifié sur la TV.

## Bugs et problèmes fonctionnels

### B1 — P1 — Récupération incomplète après une interruption ADB

**Preuve : reproduit en simulation avec les fonctions du dépôt et une TV fictive en mémoire.**

Dans `disable_stock_home()`, un paquet entre dans la liste `done` après le retour réussi de `set_enabled()`. Si Android a appliqué la désactivation mais que la vérification échoue, le paquet n'entre pas dans cette liste et échappe à la récupération immédiate. Une interruption après l'application de `set_home(HOME)` réactive les paquets déjà enregistrés, sans rétablir explicitement l'accueil précédent.

Le fichier `.stock.json` permet toujours une récupération manuelle avec `enable-stock-home`. Aucun blocage définitif de la TV n'a été démontré.

**À faire :** enregistrer les opérations tentées avant leur exécution, relire l'état après une erreur lorsque la connexion le permet, restaurer explicitement l'accueil et conserver un état de récupération incomplet tant que la vérification finale n'a pas abouti.

**Validation attendue :** erreurs avant écriture, après écriture mais avant relecture, après changement d'accueil, déconnexion, interruption et reprise idempotente. Distinguer un état inconnu d'une restauration vérifiée.

Source : [tools/tv_safe.py](tools/tv_safe.py), `disable_stock_home()` et `enable_stock_home()`.

### B2 — P2 — Les favoris désinstallés occupent encore des places

**Preuve : lecture du code ; scénario Android à reproduire.**

Les favoris enregistrés conservent les paquets désinstallés. La sélection ne présente que les applications installées, mais la limite de dix porte sur la liste enregistrée complète. Un favori absent peut donc bloquer l'ajout d'un remplaçant sans être lui-même supprimable depuis la grille. L'ajout refusé reste silencieux.

**À faire :** permettre de supprimer les favoris indisponibles ou les exclure du quota, et afficher un retour explicite lorsque la limite est atteinte.

**Validation attendue :** enregistrer dix favoris, désinstaller l'un d'eux, puis ajouter un remplaçant ; couvrir également la réinstallation.

Sources : [HomeApps.java](app/src/io/hestia/tv/HomeApps.java), `favorites()` ; [SettingsActivity.java](app/src/io/hestia/tv/SettingsActivity.java), `activate()` ; [AppMenuActivity.java](app/src/io/hestia/tv/AppMenuActivity.java).

### B3 — P2 — Une photo invalide peut rester dans le cache

**Preuve : lecture du code ; téléchargement HTTP erroné à simuler.**

Un corps HTTP 200 est enregistré sans vérifier qu'il contient une image décodable. Ensuite, l'existence du fichier suffit pour éviter son téléchargement. Si `Photos.load()` échoue, le fichier reste présent et les rafraîchissements ne le réparent pas.

**À faire :** vérifier le décodage et les dimensions avant publication, invalider les fichiers défectueux et prévoir une nouvelle tentative bornée. Limiter les dimensions et le nombre de pixels en plus de la taille du fichier téléchargé.

**Validation attendue :** réponse HTML avec statut 200, image tronquée, dimensions excessives et réparation après retour d'une image valide.

Sources : [Photos.java](app/src/io/hestia/tv/Photos.java), `fetch()` et `load()` ; [Net.java](app/src/io/hestia/tv/Net.java), `download()`.

### B4 — P2 — Les outils ne fonctionnent pas sous Windows natif

**Preuve : reproduit pendant l'audit sous PowerShell avec Python 3.13.**

`tv_safe.py` échoue à l'import de `fcntl`. Il utilise également `os.O_DIRECTORY` et des mécanismes POSIX de verrouillage et de synchronisation. Dans les tests, `Path.read_text()` sans encodage explicite échoue sur certains fichiers UTF-8 lorsque Python utilise CP1252. Le générateur de configuration utilise également l'encodage implicite.

**À faire :** choisir entre support Windows natif et support explicite Linux/macOS/WSL. Documenter les prérequis et ajouter un diagnostic initial. Utiliser explicitement UTF-8 pour les fichiers texte. Ne pas considérer le remplacement de `fcntl` comme une correction suffisante.

Sources : [tv_safe.py](tools/tv_safe.py), [generate_config.py](tools/generate_config.py), [test_app_policy.py](tests/test_app_policy.py), [build.sh](build.sh).

### B5 — P2 — Rafraîchissements du lecteur et changement de session

**Preuve : lecture du code ; impact à mesurer sur appareil.**

`findMedia()` programme le runnable `progress`, tandis que `releaseMedia()` ne retire pas les callbacks en attente. Une reprise avant l'exécution de l'ancien callback peut créer plusieurs boucles de rafraîchissement. La destruction d'une session retire le lecteur sans rechercher une autre session active.

**À faire :** retirer les callbacks lors de la libération, garantir une seule boucle et observer les changements de sessions actives.

**Validation attendue :** pause/reprise rapide du panneau, destruction de la session sélectionnée, apparition d'une session et coexistence de plusieurs lecteurs.

Source : [QuickPanelActivity.java](app/src/io/hestia/tv/QuickPanelActivity.java), `findMedia()`, `releaseMedia()` et `mediaCallback`.

### B6 — P2 — Une panne partielle RSS remplace le cache global

**Preuve : lecture du code ; panne partielle à simuler.**

Lorsqu'au moins un flux fournit des articles, le résultat remplace le cache global. Les articles encore pertinents des sources temporairement indisponibles disparaissent.

**À faire :** conserver un cache et une date de rafraîchissement par source, fusionner les résultats et distinguer un flux valide mais vide d'une erreur de récupération.

**Validation attendue :** un flux disponible sur cinq, retour des sources, absence totale de réseau, expiration des anciens articles et déduplication.

Source : [News.java](app/src/io/hestia/tv/News.java), `fetch()`.

### B7 — P3 — Le navigateur par défaut peut être ignoré

**Preuve : lecture du code ; ordre de résolution à tester avec plusieurs applications.**

`openLink()` impose le premier paquet non exclu retourné par `queryIntentActivities()`. Cela ne garantit pas le navigateur choisi par l'utilisateur, contrairement au commentaire de la méthode.

**À faire :** respecter la résolution Android et limiter le contournement du navigateur factice TCL au cas qui le nécessite.

**Validation attendue :** navigateur par défaut, plusieurs navigateurs, lien associé à une application, navigateur factice et absence de navigateur utilisable.

Source : [NewsActivity.java](app/src/io/hestia/tv/NewsActivity.java), `openLink()`.

## Architecture et stack

### A1 — P2 — Séparer état, navigation, dessin et accès Android

`HomeView` construit les listes, gère les touches, charge les photos, observe Android et dessine l'écran. Son constructeur énumère les applications et prépare leurs images sur le thread principal, avant un autre chargement asynchrone à la reprise. Cela crée un risque de ralentissement au démarrage, non quantifié pendant cet audit.

Extraire un état `HomeState`, une logique de navigation testable sans Android, des fournisseurs de données et un rendu centré sur le dessin. Mutualiser les comportements réellement communs entre écrans : touches, animations et cycle de vie.

Un seul module Android suffit au départ. Organisation proposée :

```text
ui/          accueil, réglages, panneaux, navigation
data/        météo, actualités, photos, catalogue d'applications
platform/    entrées TV, sessions média, télécommande
core/        modèles, préférences, règles testables sans Android
```

Préférer des dépendances passées par constructeur et quelques interfaces ciblées à une multiplication prématurée des modules ou frameworks.

### A2 — P2 — Définir la propriété des tâches et des images

Plusieurs exécuteurs statiques publient leurs résultats sans vérifier que l'écran destinataire est encore actif. `HomeView.destroy()` n'annule pas les chargements en attente. Le cache `TILES` n'a pas de budget mémoire ni de mécanisme d'éviction.

Une carte de 384 × 216 pixels en ARGB_8888 représente environ 324 Kio de pixels ; cent cartes représentent environ 32 Mio, hors fonds et autres allocations. Il s'agit d'un calcul théorique, pas d'une mesure de mémoire du processus.

Prévoir annulation ou identifiant de génération, rejet des résultats obsolètes, cache borné en octets et libération sous pression mémoire. Définir clairement qui possède une image avant de partager les bitmaps ou d'appeler `recycle()`. Aucune fuite permanente n'a été démontrée pendant l'audit.

Sources : [HomeView.java](app/src/io/hestia/tv/HomeView.java), [HomeApps.java](app/src/io/hestia/tv/HomeApps.java), [SettingsActivity.java](app/src/io/hestia/tv/SettingsActivity.java), [NewsActivity.java](app/src/io/hestia/tv/NewsActivity.java).

### A3 — P2 — Ajouter la sémantique d'accessibilité des écrans Canvas

Les cartes et boutons dessinés ne sont pas exposés comme contrôles accessibles individuels. La navigation D-pad ne remplace pas les informations nécessaires à TalkBack et aux outils de test ciblant les éléments.

Canvas peut être conservé avec une hiérarchie virtuelle, des libellés, des états, des actions et des événements de focus. Examiner `AccessibilityNodeProvider` ou `ExploreByTouchHelper` et vérifier aussi le parcours au lecteur d'écran sur TV.

Référence : [vues personnalisées accessibles — Android Developers](https://developer.android.com/guide/topics/ui/accessibility/views/custom-views).

### A4 — P2 — Standardiser le build si le projet s'ouvre aux contributions

Le build shell reste lisible et épingle déjà ses outils. Il impose néanmoins de maintenir manuellement leur intégration et une chaîne principalement Unix.

Recommandation : Gradle Wrapper et Android Gradle Plugin, avec un seul module `app`, versions épinglées, variantes debug/release et tests instrumentés. Conserver Java et Canvas ne pose pas de contradiction avec ce choix. L'outillage de construction n'impose pas d'ajouter un framework au runtime.

Le JDK 17 est actuellement l'outil de compilation ; le script utilise `-source 8 -target 8`. Clarifier cette distinction dans les prérequis si nécessaire. Garder le même identifiant d'application et préserver la signature des mises à jour.

Référence : [configuration du build Android](https://developer.android.com/build).

### A5 — P2 — Utiliser des ressources de traduction stables

La traduction française dépend d'une table indexée par les phrases anglaises. Modifier une phrase peut faire disparaître sa traduction sans erreur explicite.

Migrer vers des identifiants de ressources Android et des fichiers par langue, utilisables également depuis Canvas. Vérifier les changements de langue, les textes longs et la lisibilité à distance.

Source : [Ui.java](app/src/io/hestia/tv/Ui.java), `tr()` et table `FR`.

### A6 — P2 — Isoler les adaptations constructeur

Les touches TCL, la sélection heuristique du tuner et les exclusions d'applications sont mêlées aux comportements génériques. Les regrouper derrière des composants d'intégration TV permettrait de tester les règles et de supporter d'autres modèles sans multiplier les exceptions dans l'interface.

Sources : [HomeApps.java](app/src/io/hestia/tv/HomeApps.java), [HomeView.java](app/src/io/hestia/tv/HomeView.java), [RemoteKeysService.java](app/src/io/hestia/tv/RemoteKeysService.java).

## Qualité du dépôt et livraison

### Q1 — Tests du comportement de l'application

La suite actuelle protège surtout le déploiement, la configuration et certaines frontières réseau. Les contrôles textuels de code ne remplacent pas des tests de comportement.

Ajouter des tests ciblés sur les favoris, la navigation, les mises à jour du catalogue d'applications, les résultats asynchrones après fermeture, les pannes partielles des flux et les reprises du lecteur. Compléter par des tests instrumentés des parcours D-pad, du cycle de vie et des intégrations système.

### Q2 — Parcours de développement reproductible

Documenter les plateformes prises en charge et les versions d'outils. Prévoir une vérification des prérequis et des commandes distinctes pour les tests Python, les tests JVM, les tests Android et le build. Étendre la matrice CI aux systèmes effectivement supportés.

### Q3 — Signature et livraison explicites

Le build génère une nouvelle clé locale si les fichiers de signature sont absents. Un clonage du dépôt sur un autre ordinateur ne récupère pas la clé utilisée sur la TV ; une nouvelle clé ne peut pas signer une mise à jour compatible de cette installation.

Séparer debug et release, documenter la sauvegarde privée de la clé et de son mot de passe, et faire échouer une release si l'identité de signature attendue manque. Ne jamais versionner les secrets. Définir ensuite le processus de versionnage, de génération des sommes de contrôle et de publication des artefacts destinés à être distribués.

Source : [build.sh](build.sh), section de signature locale.

### Q4 — Matrice de compatibilité et validation réelle

Conserver [TRIAL.md](TRIAL.md), mais compléter le journal historique par une matrice lisible : modèle, firmware/API Android, touches, HDMI, veille/réveil, redémarrage, lecteur et restauration. Distinguer les tests automatisés, les captures et la validation avec la vraie télécommande.

## Fonctionnalités proposées

| ID | Fonctionnalité | Objectif |
|---|---|---|
| F1 | Ville et unités configurables dans l'application | Éviter de reconstruire l'APK pour changer la météo ; invalider le cache lorsque la localisation change. |
| F2 | Écran de diagnostic | Afficher état réseau, fraîcheur des caches, accès média/accessibilité et dernier échec compréhensible. |
| F3 | Export/import des préférences | Restaurer favoris, ordre, touches et langue après réinstallation ; prévoir un format versionné et validé. |
| F4 | Sources d'actualités sélectionnables | Choisir langues et sources, avec conservation des articles hors connexion. |
| F5 | Choix explicite du tuner et des applications masquées | Réduire la dépendance aux heuristiques et exclusions propres à TCL. |
| F6 | Mode sobre et options de lisibilité | Fond fixe, animations réduites et affichage adapté à la lecture à distance. |

## Validation effectuée pendant l'audit

La commande complète `python -m unittest discover -s tests -v` a échoué dans l'environnement Windows :

- import de `fcntl` impossible pour la suite de déploiement ;
- erreur de décodage CP1252 dans le test de recherche de code interdit ;
- deux tests JVM bloqués par l'absence de compilateur JDK détecté.

Avec Python en mode UTF-8, sept tests ciblés ont réussi : quatre tests de configuration et trois contrôles de politique de l'application (manifeste, service d'accessibilité et absence de shell/ADB dans le code).

Deux scénarios d'interruption de `disable_stock_home()` ont été reproduits séparément en mémoire, sans lancer ADB : échec après désactivation effective d'un paquet et interruption après application du nouvel accueil. Cette simulation ciblée ne remplace pas l'exécution de la suite complète de déploiement.

Aucun nouvel APK n'a été compilé et aucun comportement n'a été validé sur la TV pendant cet audit.

## Plan de travail proposé

- [ ] **Étape 1 — Fiabilité :** B1, B2 et B3, accompagnés de leurs tests de régression.
- [ ] **Étape 2 — Cycle de vie et données :** B5, B6, A1, A2 et tests associés ; traiter également B7.
- [ ] **Étape 3 — Développement et livraison :** décision de support Windows (B4), build (A4), signature et CI (Q2, Q3).
- [ ] **Étape 4 — Accessibilité et portabilité :** A3, A5, A6 et matrice de validation Q4.
- [ ] **Étape 5 — Fonctionnalités :** F1 et F2 en premier, puis F3 à F6 selon les usages.

Pour chaque correction, relier le changement à son identifiant d'audit, ajouter la validation pertinente et noter le résultat. L'exécution du backlog n'autorise pas à elle seule un déploiement ni une mutation de la TV.
