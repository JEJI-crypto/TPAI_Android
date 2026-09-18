package com.tpai.android;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Random;

public class MainActivity extends Activity {
    private static final String SUPABASE_URL = "https://fdqvjaqclnjztiodfucg.supabase.co";
    private static final String SUPABASE_PUBLISHABLE_KEY = "sb_publishable_gEReYquGc4yUs1MSDeNztw_FQCpwVuw";
    private static final String SCHEMA = "timported";
    private static final int MAX_MATCHES = 10;
    private static final int REPLAY_MIN_MS = 4500;
    private static final int REPLAY_MAX_MS = 12000;
    private final Random random = new Random();

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private TextView counter;
    private TextView status;
    private ProgressBar progress;
    private Button refresh;
    private final List<ReplayController> replays = new ArrayList<ReplayController>();

    static class MatchItem {
        String code = "";
        String player1 = "Joueur 1";
        String player2 = "Joueur 2";
        String live1 = "";
        String live2 = "";
        String stage = "";
        String score = "";
        String competitionKey = "";
        String competitionHeader = "";
        List<JSONObject> states = new ArrayList<JSONObject>();
        List<JSONObject> oddsHistory = new ArrayList<JSONObject>();
        int replayStartIndex = 0;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
        loadMatches();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(11,14,17));
        root.setPadding(dp(12), dp(12), dp(12), dp(10));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        Button menu = new Button(this);
        menu.setText("☰");
        menu.setTextSize(24);
        menu.setTextColor(Color.WHITE);
        menu.setGravity(Gravity.CENTER);
        menu.setPadding(0, 0, 0, 0);
        menu.setBackgroundColor(Color.TRANSPARENT);
        menu.setContentDescription("Menu");
        topBar.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(42)));

        TextView app = text("TPAI_Android  •  V1.14", 13, Color.rgb(146,154,164), Typeface.BOLD);
        topBar.addView(app, new LinearLayout.LayoutParams(0, dp(42), 1));
        root.addView(topBar, new LinearLayout.LayoutParams(-1, dp(42)));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("MATCHS D’ENTRAÎNEMENT", 20, Color.WHITE, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1));
        counter = text("0 match", 12, Color.rgb(174,239,208), Typeface.BOLD);
        counter.setGravity(Gravity.CENTER);
        titleRow.addView(counter, new LinearLayout.LayoutParams(dp(72), dp(42)));
        root.addView(titleRow);

        LinearLayout action = new LinearLayout(this);
        action.setGravity(Gravity.CENTER_VERTICAL);
        TextView rule = text("timported  •  cotes LIVE réelles uniquement", 12, Color.rgb(180,187,194), Typeface.NORMAL);
        action.addView(rule, new LinearLayout.LayoutParams(0, dp(44), 1));
        refresh = new Button(this);
        refresh.setText("ACTUALISER");
        refresh.setTextSize(11);
        refresh.setTextColor(Color.WHITE);
        refresh.setBackgroundColor(Color.rgb(28,91,62));
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                loadMatches();
            }
        });
        action.addView(refresh, new LinearLayout.LayoutParams(dp(112), dp(42)));
        root.addView(action);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        status = text("Chargement…", 12, Color.rgb(174,239,208), Typeface.NORMAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(38)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    private void loadMatches() {
        refresh.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        status.setText("Recherche des matchs avec cotes LIVE…");
        new Thread(new Runnable() {
            @Override public void run() {
            try {
                List<MatchItem> candidates = fetchLiveMatches();
                Collections.shuffle(candidates, random);
                List<MatchItem> items = new ArrayList<MatchItem>();
                int take = Math.min(MAX_MATCHES, candidates.size());
                for (int i = 0; i < take; i++) {
                    MatchItem m = candidates.get(i);
                    // Départs répartis dans la chronologie : certains tôt, d'autres au milieu ou plus tard.
                    if (m.states.size() > 3) {
                        double band = take <= 1 ? 0.35 : (0.05 + (0.70 * i / (double)(take - 1)));
                        double jitter = (random.nextDouble() - 0.5) * 0.12;
                        double ratio = Math.max(0.0, Math.min(0.82, band + jitter));
                        m.replayStartIndex = Math.min(m.states.size() - 1, (int)Math.floor(ratio * m.states.size()));
                    }
                    items.add(m);
                }
                Collections.shuffle(items, random);
                final List<MatchItem> finalItems = items;
                main.post(new Runnable() {
                    @Override public void run() {
                        render(finalItems);
                    }
                });
            } catch (Exception e) {
                final Exception error = e;
                main.post(new Runnable() {
                    @Override public void run() {
                        showError(error);
                    }
                });
            }
            }
        }, "TPAI-timported").start();
    }

    private List<MatchItem> fetchLiveMatches() throws Exception {
        // V1.6 — structure canonique confirmée par l'Admin PostgreSQL/Trefík.
        // matches.has_live_odds n'est vrai que lorsque des lignes existent réellement
        // dans timported.live_odds. Aucune cote n'est reconstruite sur Android.
        JSONArray matches = getJson("/rest/v1/matches?select=" + enc(
                "match_id,player1_id,player2_id,match_date,match_time,edition_id,opening_odds_player1,opening_odds_player2")
                + "&has_live_odds=eq.true&order=match_date.desc,match_time.desc&limit=80");

        List<MatchItem> out = new ArrayList<MatchItem>();
        for (int i = 0; i < matches.length(); i++) {
            JSONObject row = matches.getJSONObject(i);
            long matchId = row.optLong("match_id", -1L);
            if (matchId < 0) continue;

            JSONObject live = latestLiveOdds(matchId);
            if (live == null) continue;
            String a = live.optString("_odd1", "");
            String b = live.optString("_odd2", "");
            if (!realOdd(a) || !realOdd(b)) continue;

            MatchItem m = new MatchItem();
            m.code = String.valueOf(matchId);
            String editionId = clean(row.optString("edition_id", ""));
            String matchDate = clean(row.optString("match_date", ""));
            m.competitionKey = editionId.isEmpty() ? matchDate : editionId;
            m.competitionHeader = editionId.isEmpty() ? "MATCHS D’ENTRAÎNEMENT" : "TOURNOI • " + editionId;
            m.player1 = compactPlayerName(playerName(row.optLong("player1_id", -1L), "Joueur 1"));
            m.player2 = compactPlayerName(playerName(row.optLong("player2_id", -1L), "Joueur 2"));
            m.live1 = a;
            m.live2 = b;
            String d = matchDate;
            String t = clean(row.optString("match_time", ""));
            m.stage = d + (t.isEmpty() ? "" : "  " + t);
            m.score = "LIVE réel • état " + live.optInt("state_number", 0);
            m.states = matchStates(matchId);
            if (m.states.isEmpty()) continue;
            m.oddsHistory = liveOddsHistory(matchId);
            if (m.oddsHistory.isEmpty()) continue;
            out.add(m);
            if (out.size() >= 60) break;
        }
        return out;
    }

    private List<JSONObject> matchStates(long matchId) throws Exception {
        List<JSONObject> out = new ArrayList<JSONObject>();
        JSONArray rows = getJson("/rest/v1/match_states?select=*&match_id=eq." + matchId + "&order=state_number.asc");
        for (int i = 0; i < rows.length(); i++) out.add(rows.getJSONObject(i));
        return out;
    }

    private List<JSONObject> liveOddsHistory(long matchId) throws Exception {
        List<JSONObject> out = new ArrayList<JSONObject>();
        String[][] candidates = new String[][] {
                {"odds_player1", "odds_player2"}, {"player1_odds", "player2_odds"},
                {"odds_p1", "odds_p2"}, {"price_player1", "price_player2"},
                {"home_odds", "away_odds"}, {"odd_player1", "odd_player2"}
        };
        Exception last = null;
        for (int i=0;i<candidates.length;i++) {
            try {
                String c1=candidates[i][0], c2=candidates[i][1];
                JSONArray rows=getJson("/rest/v1/live_odds?select=" + enc("state_number,"+c1+","+c2)
                        + "&match_id=eq." + matchId + "&order=state_number.asc&limit=5000");
                for(int j=0;j<rows.length();j++) {
                    JSONObject src=rows.getJSONObject(j);
                    String a=clean(src.optString(c1,"")), b=clean(src.optString(c2,""));
                    if(!realOdd(a)||!realOdd(b)) continue;
                    JSONObject dst=new JSONObject(src.toString());
                    dst.put("_odd1",a); dst.put("_odd2",b); out.add(dst);
                }
                return out;
            } catch(Exception e) { last=e; out.clear(); }
        }
        if(last!=null) throw last;
        return out;
    }

    private JSONObject latestLiveOdds(long matchId) throws Exception {
        // L'Admin accepte plusieurs noms historiques pour les deux colonnes de cote.
        // On les essaie dans le même ordre pour rester compatible avec la BDD réelle.
        String[][] candidates = new String[][] {
                {"odds_player1", "odds_player2"},
                {"player1_odds", "player2_odds"},
                {"odds_p1", "odds_p2"},
                {"price_player1", "price_player2"},
                {"home_odds", "away_odds"},
                {"odd_player1", "odd_player2"}
        };
        Exception last = null;
        for (int i = 0; i < candidates.length; i++) {
            try {
                String c1 = candidates[i][0], c2 = candidates[i][1];
                JSONArray rows = getJson("/rest/v1/live_odds?select=" + enc(
                        "state_number,bookmaker_id," + c1 + "," + c2)
                        + "&match_id=eq." + matchId
                        + "&order=state_number.desc&limit=1");
                if (rows.length() == 0) return null;
                JSONObject src = rows.getJSONObject(0);
                JSONObject dst = new JSONObject(src.toString());
                dst.put("_odd1", clean(src.optString(c1, "")));
                dst.put("_odd2", clean(src.optString(c2, "")));
                return dst;
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) throw last;
        return null;
    }

    private String playerName(long playerId, String fallback) throws Exception {
        if (playerId < 0) return fallback;

        String canonical = "";
        String trefikId = "";
        try {
            JSONArray rows = getJson("/rest/v1/players?select=" + enc("player_name,trefik_player_id")
                    + "&player_id=eq." + playerId + "&limit=1");
            if (rows.length() > 0) {
                JSONObject p = rows.getJSONObject(0);
                canonical = clean(p.optString("player_name", ""));
                trefikId = clean(p.optString("trefik_player_id", ""));
                if (isRealPlayerName(canonical)) return canonical;
            }
        } catch (Exception ignored) { }

        // 1) Cherche d'abord un vrai nom rattaché au joueur canonique.
        try {
            JSONArray rows = getJson("/rest/v1/player_sources?select=" + enc("source_player_name")
                    + "&player_id=eq." + playerId + "&limit=50");
            for (int i = 0; i < rows.length(); i++) {
                String candidate = clean(rows.getJSONObject(i).optString("source_player_name", ""));
                if (isRealPlayerName(candidate)) return candidate;
            }
        } catch (Exception ignored) { }

        // 2) Les imports Trefik peuvent avoir créé un joueur technique alors qu'une autre
        // source connaît déjà le même identifiant Trefik. On recherche donc aussi par
        // source_player_id, puis on suit le player_id trouvé vers players/player_sources.
        if (!trefikId.isEmpty()) {
            try {
                JSONArray links = getJson("/rest/v1/player_sources?select=" + enc("player_id,source_player_name")
                        + "&source_player_id=eq." + enc(trefikId) + "&limit=50");
                for (int i = 0; i < links.length(); i++) {
                    JSONObject link = links.getJSONObject(i);
                    String candidate = clean(link.optString("source_player_name", ""));
                    if (isRealPlayerName(candidate)) return candidate;

                    long linkedPlayerId = link.optLong("player_id", -1L);
                    if (linkedPlayerId >= 0 && linkedPlayerId != playerId) {
                        JSONArray linked = getJson("/rest/v1/players?select=" + enc("player_name")
                                + "&player_id=eq." + linkedPlayerId + "&limit=1");
                        if (linked.length() > 0) {
                            candidate = clean(linked.getJSONObject(0).optString("player_name", ""));
                            if (isRealPlayerName(candidate)) return candidate;
                        }
                    }
                }
            } catch (Exception ignored) { }
        }

        // Aucun vrai nom présent dans timported : ne jamais en inventer un.
        if (!canonical.isEmpty()) return canonical;
        return fallback + " #" + playerId;
    }

    private boolean isRealPlayerName(String name) {
        if (name == null) return false;
        String n = name.trim().toLowerCase(Locale.US);
        if (n.length() < 2) return false;
        if (n.startsWith("trefík player #") || n.startsWith("trefik player #")) return false;
        if (n.startsWith("trefík joueur inconnu") || n.startsWith("trefik joueur inconnu")) return false;
        if (n.startsWith("joueur 1") || n.startsWith("joueur 2")) return false;
        return true;
    }

    private JSONArray getJson(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(SUPABASE_URL + path).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(25000);
        c.setRequestProperty("apikey", SUPABASE_PUBLISHABLE_KEY);
        c.setRequestProperty("Authorization", "Bearer " + SUPABASE_PUBLISHABLE_KEY);
        c.setRequestProperty("Accept-Profile", SCHEMA);
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        BufferedReader r = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream(), Charset.forName("UTF-8")));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close(); c.disconnect();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + " — " + sb);
        return new JSONArray(sb.toString());
    }

    private void render(List<MatchItem> items) {
        stopAllReplays();
        list.removeAllViews();
        counter.setText(items.size() + (items.size() > 1 ? " matchs" : " match"));
        progress.setVisibility(View.GONE); refresh.setEnabled(true);
        if (items.isEmpty()) {
            status.setText("Aucun match avec deux cotes LIVE réelles trouvé.");
            TextView empty = text("Aucun match d’entraînement disponible.", 15, Color.rgb(160,166,172), Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER); list.addView(empty, new LinearLayout.LayoutParams(-1, dp(120)));
            return;
        }
        status.setText("Liste chargée depuis timported");
        String currentCompetition = null;
        for (MatchItem m : items) {
            if (currentCompetition == null || !currentCompetition.equals(m.competitionKey)) {
                currentCompetition = m.competitionKey;
                list.addView(competitionHeader(m.competitionHeader));
            }
            list.addView(matchRow(m));
        }
    }

    private View competitionHeader(String label) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(9), 0, dp(9), 0);
        header.setBackgroundColor(Color.rgb(25, 91, 68));
        TextView gender = text("●", 11, Color.rgb(174,239,208), Typeface.BOLD);
        gender.setGravity(Gravity.CENTER);
        header.addView(gender, new LinearLayout.LayoutParams(dp(22), dp(30)));
        TextView title = text(valueOr(label, "MATCHS D’ENTRAÎNEMENT"), 12, Color.WHITE, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(30), 1));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, dp(30));
        hp.setMargins(0, dp(3), 0, 0);
        header.setLayoutParams(hp);
        return header;
    }

    private View matchRow(final MatchItem m) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setPadding(dp(7), dp(4), dp(7), dp(4));
        line.setBackgroundColor(Color.rgb(27,31,35));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        final TextView p1 = text(m.player1, 13, Color.WHITE, Typeface.BOLD);
        final TextView p2 = text(m.player2, 13, Color.WHITE, Typeface.BOLD);
        names.addView(p1, new LinearLayout.LayoutParams(-1, dp(26)));
        names.addView(p2, new LinearLayout.LayoutParams(-1, dp(26)));
        top.addView(names, new LinearLayout.LayoutParams(0, dp(52), 1));

        LinearLayout server = new LinearLayout(this);
        server.setOrientation(LinearLayout.VERTICAL);
        final TextView ball1 = text("🎾", 15, Color.WHITE, Typeface.NORMAL);
        final TextView ball2 = text("🎾", 15, Color.WHITE, Typeface.NORMAL);
        ball1.setGravity(Gravity.CENTER); ball2.setGravity(Gravity.CENTER);
        ball1.setVisibility(View.INVISIBLE); ball2.setVisibility(View.INVISIBLE);
        server.addView(ball1, new LinearLayout.LayoutParams(dp(28), dp(26)));
        server.addView(ball2, new LinearLayout.LayoutParams(dp(28), dp(26)));
        top.addView(server, new LinearLayout.LayoutParams(dp(28), dp(52)));

        LinearLayout setsWon = new LinearLayout(this);
        setsWon.setOrientation(LinearLayout.VERTICAL);
        final TextView sw1 = scoreText("0", 15, Color.rgb(255,214,64));
        final TextView sw2 = scoreText("0", 15, Color.rgb(255,214,64));
        setsWon.addView(sw1, new LinearLayout.LayoutParams(dp(25), dp(26)));
        setsWon.addView(sw2, new LinearLayout.LayoutParams(dp(25), dp(26)));
        top.addView(setsWon, new LinearLayout.LayoutParams(dp(25), dp(52)));

        LinearLayout games = new LinearLayout(this);
        games.setOrientation(LinearLayout.VERTICAL);
        final TextView gs1 = scoreText("0", 13, Color.WHITE);
        final TextView gs2 = scoreText("0", 13, Color.WHITE);
        games.addView(gs1, new LinearLayout.LayoutParams(dp(86), dp(26)));
        games.addView(gs2, new LinearLayout.LayoutParams(dp(86), dp(26)));
        top.addView(games, new LinearLayout.LayoutParams(dp(86), dp(52)));

        LinearLayout points = new LinearLayout(this);
        points.setOrientation(LinearLayout.VERTICAL);
        final TextView pt1 = scoreText("0", 15, Color.rgb(174,239,208));
        final TextView pt2 = scoreText("0", 15, Color.rgb(174,239,208));
        points.addView(pt1, new LinearLayout.LayoutParams(dp(34), dp(26)));
        points.addView(pt2, new LinearLayout.LayoutParams(dp(34), dp(26)));
        top.addView(points, new LinearLayout.LayoutParams(dp(34), dp(52)));

        LinearLayout liveOdds = new LinearLayout(this);
        liveOdds.setOrientation(LinearLayout.VERTICAL);
        final TextView odd1 = scoreText(formatOdd(m.live1), 12, Color.rgb(255,214,64));
        final TextView odd2 = scoreText(formatOdd(m.live2), 12, Color.rgb(255,214,64));
        liveOdds.addView(odd1, new LinearLayout.LayoutParams(dp(48), dp(26)));
        liveOdds.addView(odd2, new LinearLayout.LayoutParams(dp(48), dp(26)));
        top.addView(liveOdds, new LinearLayout.LayoutParams(dp(48), dp(52)));

        final TextView breakView = text("BREAK", 9, Color.rgb(255,90,90), Typeface.BOLD);
        breakView.setGravity(Gravity.CENTER);
        breakView.setVisibility(View.INVISIBLE);
        top.addView(breakView, new LinearLayout.LayoutParams(dp(44), dp(52)));
        line.addView(top, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        final TextView state = text(m.states.isEmpty() ? "Données de déroulement indisponibles" : "PRÊT • faux direct", 9, Color.rgb(150,158,166), Typeface.NORMAL);
        footer.addView(state, new LinearLayout.LayoutParams(0, dp(20), 1));
        line.addView(footer, new LinearLayout.LayoutParams(-1, dp(20)));

        View separator = new View(this); separator.setBackgroundColor(Color.rgb(48,54,60));
        LinearLayout wrapper = new LinearLayout(this); wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(line, new LinearLayout.LayoutParams(-1, dp(80)));
        wrapper.addView(separator, new LinearLayout.LayoutParams(-1, dp(1)));

        ReplayController rc = new ReplayController(m, ball1, ball2, sw1, sw2, gs1, gs2, pt1, pt2, odd1, odd2, breakView, state);
        replays.add(rc); rc.start();
        return wrapper;
    }

    private TextView scoreText(String value, int sp, int color) {
        TextView v = text(value, sp, color, Typeface.BOLD); v.setGravity(Gravity.CENTER); return v;
    }

    private class ReplayController implements Runnable {
        final MatchItem match; final TextView ball1, ball2, sw1, sw2, gs1, gs2, pt1, pt2, odd1, odd2, breakView, statusView;
        int index; boolean stopped = false; boolean blink = false;
        ReplayController(MatchItem m, TextView b1, TextView b2, TextView s1, TextView s2, TextView g1, TextView g2, TextView p1, TextView p2, TextView o1, TextView o2, TextView br, TextView st) {
            match=m; ball1=b1; ball2=b2; sw1=s1; sw2=s2; gs1=g1; gs2=g2; pt1=p1; pt2=p2; odd1=o1; odd2=o2; breakView=br; statusView=st; index=m.replayStartIndex;
        }
        void start() { if (!match.states.isEmpty()) main.postDelayed(this, 500); }
        void stop() { stopped=true; main.removeCallbacks(this); }
        @Override public void run() {
            if (stopped || index >= match.states.size()) { if (!stopped) statusView.setText("TERMINÉ"); return; }
            JSONObject s = match.states.get(index++); applyState(s);
            if (!stopped) main.postDelayed(this, naturalDelay(s));
        }
        void applyState(JSONObject s) {
            String server = first(s, "server", "service", "server_player", "serving_player", "serveur").toUpperCase(Locale.US);
            int side = sideOf(server);
            ball1.setVisibility(side==1 ? View.VISIBLE : View.INVISIBLE); ball2.setVisibility(side==2 ? View.VISIBLE : View.INVISIBLE);

            String[] wins = pairFrom(s, new String[]{"sets_score","set_score","sets_won","score_sets"}, new String[]{"sets_player1","set_player1","sets1","player1_sets"}, new String[]{"sets_player2","set_player2","sets2","player2_sets"});
            sw1.setText(wins[0]); sw2.setText(wins[1]);
            String[] game = pairFrom(s, new String[]{"games_score","game_score","score_games","current_set_score"}, new String[]{"games_player1","game_player1","games1","player1_games"}, new String[]{"games_player2","game_player2","games2","player2_games"});
            String[] setLine = setScores(s, game);
            gs1.setText(setLine[0]); gs2.setText(setLine[1]);
            String[] pts = pairFrom(s, new String[]{"points","points_score","point_score","score_points"}, new String[]{"points_player1","point_player1","points1","player1_points"}, new String[]{"points_player2","point_player2","points2","player2_points"});
            pt1.setText(pts[0]); pt2.setText(pts[1]);

            JSONObject live = oddsForState(s.optInt("state_number", index));
            if (live != null) { odd1.setText(formatOdd(live.optString("_odd1", match.live1))); odd2.setText(formatOdd(live.optString("_odd2", match.live2))); }

            boolean br = isBreakPoint(pts[0], pts[1], side);
            if (br) {
                breakView.setVisibility(View.VISIBLE);
                if (breakView.getAnimation() == null) {
                    AlphaAnimation flash = new AlphaAnimation(1.0f, 0.15f);
                    flash.setDuration(450);
                    flash.setRepeatMode(Animation.REVERSE);
                    flash.setRepeatCount(Animation.INFINITE);
                    breakView.startAnimation(flash);
                }
            } else {
                breakView.clearAnimation();
                breakView.setVisibility(View.INVISIBLE);
            }
            statusView.setText("EN DIRECT • état " + s.optInt("state_number", index));
        }
        JSONObject oddsForState(int stateNo) {
            JSONObject best=null;
            for(int i=0;i<match.oddsHistory.size();i++) {
                JSONObject o=match.oddsHistory.get(i);
                int n=o.optInt("state_number",-1);
                if(n<=stateNo) best=o; else break;
            }
            return best;
        }
        long naturalDelay(JSONObject current) {
            // Rythme irrégulier proche d'un direct : petites variations entre états,
            // pauses plus longues lors d'un changement de jeu/set.
            long d=REPLAY_MIN_MS + random.nextInt(REPLAY_MAX_MS-REPLAY_MIN_MS+1);
            if(index<match.states.size()) {
                JSONObject next=match.states.get(index);
                String cg=first(current,"games_score","game_score","score_games","current_set_score");
                String ng=first(next,"games_score","game_score","score_games","current_set_score");
                String cs=first(current,"sets_score","set_score","sets_won","score_sets");
                String ns=first(next,"sets_score","set_score","sets_won","score_sets");
                if(!cs.equals(ns)) d += 9000 + random.nextInt(7000);
                else if(!cg.equals(ng)) d += 5000 + random.nextInt(5000);
            }
            return d;
        }
    }

    private void stopAllReplays() { for (int i=0;i<replays.size();i++) replays.get(i).stop(); replays.clear(); }

    private static int sideOf(String v) {
        String s=clean(v).toLowerCase(Locale.US);
        if (s.equals("1")||s.equals("j1")||s.equals("p1")||s.contains("player1")||s.contains("joueur 1")) return 1;
        if (s.equals("2")||s.equals("j2")||s.equals("p2")||s.contains("player2")||s.contains("joueur 2")) return 2;
        return 0;
    }
    private static String first(JSONObject o, String... keys) { for(String k:keys){ String v=clean(o.optString(k,"")); if(!v.isEmpty()) return v; } return ""; }
    private static String[] splitPair(String raw) { String s=clean(raw).replace(':','-'); String[] a=s.split("\\s*-\\s*"); return a.length>=2?new String[]{a[0],a[1]}:new String[]{"0","0"}; }
    private static String[] pairFrom(JSONObject o, String[] pairKeys, String[] p1Keys, String[] p2Keys) {
        for(String k:pairKeys){ String v=clean(o.optString(k,"")); if(!v.isEmpty()) return splitPair(v); }
        String a="",b=""; for(String k:p1Keys){a=clean(o.optString(k,""));if(!a.isEmpty())break;} for(String k:p2Keys){b=clean(o.optString(k,""));if(!b.isEmpty())break;}
        return new String[]{a.isEmpty()?"0":a,b.isEmpty()?"0":b};
    }
    private static String[] setScores(JSONObject o, String[] current) {
        StringBuilder a=new StringBuilder(), b=new StringBuilder();
        for(int n=1;n<=5;n++) {
            String raw=first(o,"set"+n+"_score","set_"+n+"_score","score_set"+n);
            if(raw.isEmpty()) continue; String[] p=splitPair(raw);
            if(a.length()>0){a.append(" ");b.append(" ");} a.append(p[0]);b.append(p[1]);
        }
        if(a.length()==0) return current; return new String[]{a.toString(),b.toString()};
    }
    private static int tennisPoint(String p) { String s=clean(p).toUpperCase(Locale.US); if(s.equals("A")||s.equals("AD")) return 4; try{int n=Integer.parseInt(s); if(n==40)return 3;if(n==30)return 2;if(n==15)return 1;if(n>3)return n;}catch(Exception e){} return 0; }
    private static boolean isBreakPoint(String p1, String p2, int server) {
        if(server==0) return false; int a=tennisPoint(p1),b=tennisPoint(p2); int r=server==1?b:a, sv=server==1?a:b;
        return (r>=3 && r>sv) || (r==3 && sv<=2);
    }

    private static String compactPlayerName(String name) {
        String n=clean(name);
        if(n.isEmpty()) return n;
        String[] parts=n.split("\\s+");
        if(parts.length<2) return n;
        String second=parts[1];
        if(second.length()==0) return parts[0];
        return parts[0] + " " + second.substring(0,1) + ".";
    }

    private static String shortStage(String stage) {
        String s = clean(stage);
        if (s.length() >= 10) {
            String time = s.substring(10).trim();
            if (!time.isEmpty()) return time.length() > 5 ? time.substring(0,5) : time;
        }
        return s;
    }

    private void showError(Exception e) {
        progress.setVisibility(View.GONE); refresh.setEnabled(true); list.removeAllViews(); counter.setText("0 match");
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (msg.length() > 420) msg = msg.substring(0,420) + "…";
        status.setText("Connexion timported impossible");
        TextView t = text("Impossible de charger la liste.\n\n" + msg, 13, Color.rgb(255,120,120), Typeface.NORMAL);
        t.setPadding(dp(8),dp(20),dp(8),dp(20)); list.addView(t);
    }

    private static boolean realOdd(String s) {
        try { double v = Double.parseDouble(s.replace(',', '.')); return v > 1.0 && Double.isFinite(v); }
        catch (Exception e) { return false; }
    }
    private static String formatOdd(String s) { try { return String.format(Locale.FRANCE, "%.2f", Double.parseDouble(s.replace(',', '.'))); } catch(Exception e){ return s; } }
    private static String clean(String s) { if (s == null || "null".equalsIgnoreCase(s)) return ""; return s.trim(); }
    private static String valueOr(String s, String fallback) { s=clean(s); return s.isEmpty()?fallback:s; }
    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }
    private TextView text(String s, int sp, int color, int style) { TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); v.setTypeface(Typeface.create("sans", style)); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
