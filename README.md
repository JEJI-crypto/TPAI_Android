

V10
- Lecteur JSON: parcours désormais sections et subsections imbriquées dans les chapitres, en plus de children/content/blocks/items/entries.
- Mentions légales: page spéciale sans titre, sans numéro visible, bloc centré, suppression du long texte juridique entre Tous droits réservés et ISBN.
- Aucune réécriture du contenu des chapitres.


## V11
- Sommaire : suppression du titre courant du livre en haut de page.
- Ouverture de chapitre : le label « Chapitre I » et le titre du chapitre sont désormais regroupés et centrés.
- Sommaire : label CHAPITRE en gras, tiret puis titre en romain.
- Sommaire : texte calé à gauche, numéro de page calé à droite.
- Les points de conduite sont calculés selon la largeur réelle restante.


## V24
- Nouvelle icône officielle : livre moderne bleu/or.
- EPUB Kindle restructuré : page de titre, page copyright, menu cliquable, puis contenu.
- Menu EPUB sans pagination papier ni pointillés, avec liens vers les grandes sections et chapitres.
- ISBN papier exclu de l'EPUB.
- Journal : bouton « COPIER LE JOURNAL » et ascenseur permanent.


V24 : sommaire PDF enrichi avec les sections en retrait, plus petites et en italique, avec points de conduite et pages. Menu EPUB enrichi avec sections cliquables en retrait et italique, sans pagination.


V24 : correction de compilation EpubNav (support du niveau section) et remplacement de l’icône par l’image bleue carrée choisie par l’utilisateur.


V24 : sélection multiple de JSON via SAF, dossier de sortie choisi une seule fois, traitement séquentiel du lot, progression Livre X/Y, poursuite après erreur individuelle et bilan final réussites/erreurs.


V24 : vraie file d’attente visible. + AJOUTER DES JSON peut être utilisé plusieurs fois et dans différents dossiers ; sélection multiple conservée ; les nouveaux fichiers s’ajoutent sans effacer les précédents ; retrait individuel, VIDÉR LA LISTE, puis génération du lot vers un dossier choisi une seule fois.


V24 : version affichée en haut de l’écran ; refonte esthétique douce avec boutons arrondis pastel, bouton principal vert doux, ajout JSON bleu pastel, vidage/suppression rose pâle et file d’attente sous forme de cartes arrondies. Moteurs PDF/EPUB et file d’attente V18 conservés.


V24 : correction des marges miroir du PDF. À chaque nouvelle page, la marge intérieure est recalculée selon la parité : pages impaires = marge intérieure à gauche ; pages paires = marge intérieure à droite. La largeur utile reste identique. La correction s’applique aussi aux pages créées par débordement, aux ouvertures de chapitres, pages spéciales et sommaire.

V24 : parseur JSON sémantique renforcé.
- title accepte une chaîne ou un objet sémantique contenant text.
- aucune sérialisation brute d'un objet title ne doit être imprimée.
- le contexte parent est conservé lors du parcours.
- chapter_title dans title_page ne crée plus de faux chapitre.
- récupération du champ number des chapitres pour la numérotation réelle lorsqu'il est fourni.
- conservation des marges miroir V20 et de toute la chaîne PDF/EPUB existante.

V24 :
- suppression générique du préfixe redondant « Chapitre N — » dans le véritable titre
  lorsque le moteur affiche déjà « Chapitre + numéro romain » ;
- correction appliquée à la source sémantique commune afin d'alimenter correctement
  l'ouverture de chapitre, le sommaire PDF et l'EPUB ;
- hiérarchie de la page de titre renforcée : titre principal prioritaire, sous-titre
  secondaire, auteur en dessous ;
- conservation du parseur sémantique V21 et des marges miroir V20.

V24 :
- première page PDF traitée par un rendu dédié ;
- titre principal grand, gras, centré et en capitales ;
- sous-titre plus petit, romain et centré ;
- auteur récupéré du JSON et placé centré dans la partie basse de la page ;
- aucun numéro visible sur la page de titre ;
- toutes les corrections validées de V22 sont conservées.


V24 : correctif de compilation V23 et page de titre dédiée directement dans Composer.frontTitle : grand titre gras, sous-titre secondaire, auteur en bas, sans numéro. Toutes les corrections V22/V21/V20 sont conservées.

## V42
L'onglet PRÉPARATION IA est fusionné dans RÉÉCRITURE. Étape 1 génère les blocs directement dans un dossier REECRITUREn Google Drive avec contrôle vide/non vide. Étape 2 conserve le suivi Drive automatique toutes les 10 secondes et l'actualisation manuelle. Scope OAuth requis : `https://www.googleapis.com/auth/drive`.


V47 : reprise automatique des envois Drive sur erreurs réseau temporaires (4 tentatives) ; les erreurs techniques de lecture Drive restent dans JOURNAL et ne remplacent plus le dernier état valide affiché dans le suivi.


## V1.6 — structure timported canonique
- Sélection dans `timported.matches` avec `has_live_odds=TRUE`.
- Lecture des vraies cotes dans `timported.live_odds` par `match_id` et dernier `state_number`.
- Lecture des noms via `timported.players`.
- Aucune estimation/reconstruction des cotes LIVE.
- Nécessite que le schéma `timported` soit exposé dans Supabase Data API et lisible par le rôle utilisé.


## V1.7
- Ajout d’un bouton menu hamburger (trois barres horizontales) dans le coin supérieur gauche de l’application.
- Le bouton est visuel dans cette étape de test ; le contenu du menu pourra être ajouté ensuite.


## V1.8
- Menu hamburger rendu explicitement visible en haut à gauche.
- Résolution des noms : `players.player_name` en priorité ; si ce nom est encore technique Trefík, recherche d’un vrai `source_player_name` associé au même `player_id` dans `player_sources`.
- Aucun nom n’est inventé : si `timported` ne contient réellement que le libellé technique, celui-ci reste affiché.


## V1.9 — signature Android permanente
- Build release signé avec la clé permanente `tpai` reconstruite uniquement dans GitHub Actions depuis les Repository Secrets.
- Aucun keystore ni mot de passe n'est inclus dans le dépôt ou le ZIP.
- Secrets attendus : `TPAI_KEYSTORE_BASE64` et `TPAI_KEYSTORE_PASSWORD`.
- APK publié sous le nom stable `TPAI_Android.apk`.
- Cette version initialise la nouvelle signature permanente ; une dernière désinstallation de l'ancienne signature peut être nécessaire.

## V1.10 — noms réels des joueurs LIVE
- Résolution renforcée des noms dans `timported`.
- Recherche d'abord `players.player_name`, puis toutes les entrées `player_sources` du joueur.
- Si le joueur canonique porte encore un libellé technique Trefík, recherche aussi par `trefik_player_id` / `source_player_id` afin de retrouver un éventuel joueur déjà nommé par une autre source.
- Aucun nom n'est inventé : si `timported` ne contient réellement aucun nom, le libellé technique est conservé.
