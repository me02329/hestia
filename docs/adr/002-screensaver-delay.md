# ADR 002 : délai de l'économiseur depuis le panneau rapide

Date : 2026-11-01. Décidé par le propriétaire.

Le propriétaire veut aussi changer le délai avant l'économiseur (`system screen_off_timeout`, 10 min sur la TV). C'est une
troisième clé écrite par `power/SleepDelay`, sous la permission et les conditions de l'[ADR 001](001-write-secure-settings.md) :
sur OK dans le panneau rapide seulement, page de la TV sans la permission, vérifié par test.

Seuls les délais plus courts que la mise en veille sont proposés : sinon la TV s'éteindrait sans jamais l'afficher.
