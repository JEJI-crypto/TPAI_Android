# TPAI Android 1.0.14 — accès distant Supabase / tpai

Application Android Java sans base locale, sans connexion PostgreSQL directe.
Les listes, les fiches et les historiques sont lus sur l'API REST native Supabase
avec une clé publiable. Aucun secret PostgreSQL dans l'APK.

## Correction V1.14

La fiche match n'utilise plus `select=*`. Elle sélectionne exclusivement
les colonnes publiques de `TPAI_REST_COLONNES_V4.sql`. Cela permet les GRANT
PostgreSQL au niveau des colonnes sans exposer les colonnes internes.
Les fichiers obsolètes de RPC expérimental ont été retirés.

## Avant de lancer

1. Ajoute `tpai` aux *Exposed schemas* du Dashboard Supabase.
2. Effectue l'audit `(v4)TPAI_Acces_REST.py` dans Termux/Download.
3. Après vérification du rapport et de l'usage public prévu des données,
   applique les GRANT/RLS avec `--apply`, puis relance l'audit sans --apply.
4. Téléverse le projet Android sur le dépôt de compilation GitHub déjà en place.

## Accès

Six tables mères lisibles en colonnes ciblées: matches, players, competitions,
live_states, p2p_rows, p2p_games. Pas de SELECT sur les partitions ni
sur les tables techniques. Pagination des matchs par curseur match_id.
Aucune base de données, synchronisation ou stockage disque applicatif.

## Limites connues

La recherche de joueurs dans ce prototype est limitée aux 30 premiers IDs
correspondants ; elle ne garantit pas une recherche exhaustive par nom.
Tous les matchs restent parcourables par pagination et filtrables par ID/date.
Compilation et tests d'intégration à exécuter sur GitHub + appareil Android.
API publique : validation des droits de rediffusion et anti-abus avant Play Store.

## Compilation

GitHub Actions construit l'APK et l'AAB avec les secrets de signature du dépôt.
versionCode=14, targetSdk=36. L'archive ZIP est du code source, pas un APK compilé.
