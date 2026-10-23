# TPAI Android V1.23 — Menu général et filtrage des cotes estimées

Projet Android complet destiné à être compilé depuis Termux avec la commande unique `tpai`.

## Nouveau menu ☰

Tout en haut à gauche : ☰ → case **« Côtes estimées incluses »**.

- La case est toujours **décochée au lancement**, sans persistance du réglage.
- Décochée : `tpai.matches.live_status = COMPLETE_OBSERVED`, `has_estimated_live=false`, et aucun `live_states.is_estimated=true`.
- Cochée : `COMPLETE_OBSERVED` ou `COMPLETE_ESTIMATED` ; les états estimés déjà stockés par Booker sont affichés avec un `*`.
- Dans les deux cas : `live_missing_points=0`, `live_odds_count=live_states_count`, deux cotes AVM strictement positives, deux cotes LIVE valides à chaque état chargé, et nombre total d'états conforme ; aucune cote n'est estimée ni inventée par Android.
- Quand on décoche : suppression immédiate de la liste courante des matchs contenant des cotes estimées (y compris favoris concernés). Ils restent intégralement dans PostgreSQL distant ; les autres restent.
- Un téléchargement lancé sous l'ancien réglage est ignoré au retour, même si le menu a été modifié pendant son exécution. Les favoris restaurés sont toujours revalidés.
- Menu temporaire : ajouter les futures options sans classement jusqu'à la réorganisation finale demandée.

Les autres fonctions « Matchs d'entraînement » de V1.22 sont maintenues : sélection maximale 10 matchs, 4 tirages aléatoires au lancement, filtres catégories/tournois/saisons, recherche joueur, contrôles de lecture, favoris, scores, détail des jeux.

## Accès réseau

Exclusivement REST Supabase au schéma `tpai` (tables `matches`, `live_states`, `players`, `competitions`, `p2p_games`). Aucune base locale ni mot de passe PostgreSQL, aucune création/modification de tables. Seuls les ID/favoris/sélections sont mémorisés dans SharedPreferences. Les cotes proviennent de `matches.avm_odd1/2` et `live_states.odd1/2`.

L'état `COMPLETE_ESTIMATED` décrit la reconstitution d'une ou plusieurs cotes par le processus Booker antérieur, pas une opération de l'application Android.

## Compilation

Télécharger **TPAI_Android_GitHub_V1.23.zip** dans `Download` et exécuter uniquement `tpai` dans Termux. GitHub Actions doit produire `TPAI_Android_V1.23.apk` et `TPAI_Android.aab`.

## Contrôle de qualité

Contrôles locaux sources/compilation Java stubs ; **APK GitHub et comportement sur téléphone non vérifiés avant exécution de `tpai`**.
