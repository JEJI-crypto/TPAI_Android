TPAI_Android V1
===============

Première base AndroidIDE de Tennis Predict AI.

Fonction :
- un seul écran « MATCHS D’ENTRAÎNEMENT » ;
- source distante exclusive : schéma timported ;
- lecture via l’API REST Supabase avec la clé publiable (aucun mot de passe PostgreSQL ni clé secrète dans l’APK) ;
- seuls les matchs possédant deux cotes LIVE réelles sont retenus ;
- maximum 10 matchs affichés ;
- bouton ACTUALISER pour obtenir une nouvelle sélection.

Le projet est volontairement limité à cette première étape. Les simulations, estimations de cotes, analyses, STRATÉGIE, PARCOURS et autres onglets ne sont pas inclus.

IMPORTANT : timported doit être exposé à l’API Supabase et les droits/RLS doivent autoriser la lecture nécessaire avec la clé publiable.
