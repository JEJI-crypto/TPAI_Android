# TPAI Android V1.34 — Reconstruction PTS par jeu

Cette version conserve la liste, le balayage vers la droite pour supprimer et le curseur caché par défaut (icône ⏩).

## Reconstruction des scores

- Lecture des séquences `tpai.p2p_games.point_winners` et contrôle de `points_count`, `lossy`, `confidence` et `decode_mode`.
- Reconstruction du score standard, jeu par jeu, y compris points numériques en tie-break.
- Segmentation des lignes LIVE en plages de score de sets/jeux identique.
- Alignement des variations de compteurs de chaque plage LIVE avec les points P2P possibles de ce jeu. Si plusieurs positions sont plausibles, l'affichage reste `—` : aucun score inventé.
- Ne modifie aucune table Supabase. Les cotes restent attachées aux états LIVE d'origine.

## Diagnostic

Dans `adb logcat -s TPAI_PTS:I`, une ligne par match résume `live`, `aligned`, `unmatched`, `matchingRuns`, `ambiguousRuns`, `missingRuns` et `invalidGames`.

Important : ce correctif améliore la méthode d'alignement mais ne garantit pas l'élimination des tirets quand les états LIVE ne permettent pas de déduire un point précis. Les correspondances doivent être vérifiées sur le téléphone et sur les données réelles.

## Installation

Placer `TPAI_Android_GitHub_V1.34.zip` dans Download, puis exécuter `tpai` dans Termux. GitHub Actions doit produire `TPAI_Android_V1.34.apk`.
