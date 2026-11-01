# ADR 003 : services désactivés sur la TV

Date : 2026-11-01. Décidé par le propriétaire.

Hestia sert à se débarrasser de la lourdeur de Google TV. Le relevé mémoire du 1er novembre montre ces services actifs
sans utilité pour le propriétaire : recherche vocale Google (90 Mo), Agenda Google (14 Mo), AirPlay (environ 19 Mo).

`tools/tv_safe.py disable-services` les désactive (`SERVICES`) en les enregistrant à côté de la sauvegarde ;
`enable-services` les réactive. L'app elle-même ne désactive toujours rien.

**Conservés volontairement** : Chromecast (`mediashell`), utilisé avec Google Home ; services Google Play et Play Store,
dont dépendent les apps ; les services TCL, qui ne gênent pas le propriétaire.

**Conséquences** : plus de recherche vocale ni de bouton micro, plus d'AirPlay. Retirer un paquet de cette liste ou en
ajouter un demande une nouvelle décision du propriétaire.
