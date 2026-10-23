-- TPAI REST V4 : API native Supabase, lecture publique limitée par colonnes.
-- Une seule base distante : tpai. Aucune table, copie, vue ou RPC créée.
-- Appliquer uniquement après avoir confirmé la publication de ces champs.
-- L'exposition de tpai dans Supabase > API > Exposed schemas se règle manuellement.
-- Script exécuté EN TRANSACTION avec validation finale et ROLLBACK en cas d'écart.

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '45s';

-- Retirer les droits directs préexistants des rôles publics SUR LES 6 TABLES.
-- Important : aucune permission de service_role/postgres n'est changée.
DO $acl$
DECLARE
  tab text;
  cols text;
  priv text;
BEGIN
  FOREACH tab IN ARRAY ARRAY[
      'matches','players','competitions','live_states','p2p_rows','p2p_games'
  ] LOOP
    SELECT string_agg(format('%I', a.attname), ', ' ORDER BY a.attnum)
      INTO cols
    FROM pg_attribute a
    WHERE a.attrelid = format('tpai.%I',tab)::regclass
      AND a.attnum > 0 AND NOT a.attisdropped;
    IF cols IS NULL THEN
      RAISE EXCEPTION 'Table sans colonnes : tpai.%', tab;
    END IF;
    EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE tpai.%I FROM anon, authenticated', tab);
    -- Les droits par colonnes ne sont pas supprimés par REVOKE ALL sur la table.
    FOREACH priv IN ARRAY ARRAY['SELECT','INSERT','UPDATE','REFERENCES'] LOOP
      EXECUTE format('REVOKE %s (%s) ON TABLE tpai.%I FROM anon, authenticated',priv,cols,tab);
    END LOOP;
  END LOOP;
END;
$acl$;

GRANT USAGE ON SCHEMA tpai TO anon, authenticated;

-- MATCHS : identifiants, dates, cotes AVM et statuts utiles à l'écran.
GRANT SELECT (
  match_id, match_date, match_time, scheduled_at,
  competition_id, player1_id, player2_id,
  match_type, team_type, avm_odd1, avm_odd2,
  live_status, has_estimated_live, live_missing_points,
  live_states_count, live_odds_count, p2p_rows_count, p2p_games_count,
  updated_at
) ON TABLE tpai.matches TO anon, authenticated;

-- Étiquettes publiques, sans source brute ni métadonnées d'ingestion.
GRANT SELECT (player_id, name) ON TABLE tpai.players TO anon, authenticated;
GRANT SELECT (competition_id, name) ON TABLE tpai.competitions TO anon, authenticated;

-- HISTORIQUE LIVE : résultats et origine observée/estimée.
GRANT SELECT (
  match_id, source_row, t_raw,
  sets_p1, sets_p2, games_p1, games_p2,
  points_p1, points_p2, server, odd1, odd2, is_estimated
) ON TABLE tpai.live_states TO anon, authenticated;

-- POINT PAR POINT : champs déjà affichés par l'application Android.
GRANT SELECT (
  match_id, source_row, code, set_no, row_type,
  payload_count, decode_status
) ON TABLE tpai.p2p_rows TO anon, authenticated;

GRANT SELECT (
  match_id, id, set_no, game_no, is_tiebreak,
  server, winner, points_count, decode_mode, confidence, lossy
) ON TABLE tpai.p2p_games TO anon, authenticated;

-- RLS sur les six tables mères : toutes les lignes des colonnes autorisées
-- sont consultables publiquement. Aucun droit d'écriture accordé.
ALTER TABLE tpai.matches ENABLE ROW LEVEL SECURITY;
ALTER TABLE tpai.players ENABLE ROW LEVEL SECURITY;
ALTER TABLE tpai.competitions ENABLE ROW LEVEL SECURITY;
ALTER TABLE tpai.live_states ENABLE ROW LEVEL SECURITY;
ALTER TABLE tpai.p2p_rows ENABLE ROW LEVEL SECURITY;
ALTER TABLE tpai.p2p_games ENABLE ROW LEVEL SECURITY;

DO $policies$
DECLARE tab text;
BEGIN
  FOREACH tab IN ARRAY ARRAY[
    'matches','players','competitions','live_states','p2p_rows','p2p_games'
  ] LOOP
    EXECUTE format('DROP POLICY IF EXISTS tpai_android_public_read_v4 ON tpai.%I', tab);
    EXECUTE format(
      'CREATE POLICY tpai_android_public_read_v4 ON tpai.%I FOR SELECT TO anon, authenticated USING (true)',
      tab
    );
  END LOOP;
END;
$policies$;

-- Aucune politique ni droit par défaut sur les autres objets du schéma.
NOTIFY pgrst, 'reload schema';
