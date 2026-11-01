# ADR 001 : WRITE_SECURE_SETTINGS, limité au délai de veille

Date : 2026-11-01. Décidé par le propriétaire.

## Contexte

Le projet interdisait `WRITE_SECURE_SETTINGS` dans l'app : aucun chemin privilégié ne devait pouvoir modifier la TV. Le
propriétaire veut changer le délai de mise en veille depuis le panneau rapide. Relevé sur la TCL G08 (Android 14) : ce
délai est dans `secure sleep_timeout` et `secure attentive_timeout`. Android ne laisse les écrire qu'avec cette permission.

## Décision

L'interdit est levé pour ces deux clés et rien d'autre :

- la permission n'est pas accordée à l'installation : elle l'est une fois, par ADB, depuis l'ordinateur du propriétaire ;
- `power/SleepDelay` est le seul code qui écrit un réglage système, un test le vérifie et vérifie les deux clés ;
- sans la permission, la ligne ouvre la page Économie d'énergie de la TV, sans rien écrire ;
- aucune écriture au démarrage ni en arrière-plan : seulement sur OK dans le panneau rapide.

Le reste de l'interdit tient : pas d'ADB dans l'app, pas de désactivation de paquet, rien au démarrage.

## Conséquences

Une fois accordée, la permission vaut pour tout le processus : Android ne la restreint pas à deux clés. La limite tient
donc au code et au test, pas au système. Pour la retirer :
`adb shell pm revoke io.hestia.tv android.permission.WRITE_SECURE_SETTINGS`.

Autres écritures possibles à l'avenir : chacune demande une nouvelle décision du propriétaire et une nouvelle ADR.
