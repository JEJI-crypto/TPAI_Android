# TPAI Android V1.25 — Interface minimaliste

- Menu ☰ et filtre « Côtes estimées incluses », décoché au lancement.
- Recherche ⌕ et ajout ＋ dans la barre supérieure.
- Quatre matchs éligibles cherchés aléatoirement au démarrage, sans sélection automatique du bandeau.
- Bandeau et commandes de lecture visibles seulement après clic sur un match, boutons **immédiatement sous le bandeau**.
- Opérations de liste dans ⋮ ; catégorie/tournoi/saison/match dans ＋.
- Accès distant exclusif REST Supabase schéma `tpai`; cotes réelles Booker, sans estimation Android.
- Tirage indexé par `match_id` en petits lots, avec nouvelle tentative limitée sur timeout.

**Limites** : la durée de tirage dépend de la disponibilité des matchs complets et du serveur ; le projet ne contient aucun accès direct à PostgreSQL et n'a pas été testé sur le téléphone.

Après téléchargement du ZIP dans Download, exécuter `tpai` depuis Termux.
