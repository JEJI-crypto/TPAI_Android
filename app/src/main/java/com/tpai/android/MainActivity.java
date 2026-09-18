package com.tpai.android;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

public class MainActivity extends Activity {
    private static final String SUPABASE_URL = "https://fdqvjaqclnjztiodfucg.supabase.co";
    private static final String SUPABASE_PUBLISHABLE_KEY = "sb_publishable_gEReYquGc4yUs1MSDeNztw_FQCpwVuw";
    private static final String SCHEMA = "timported";
    private static final int MAX_MATCHES = 10;

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private TextView counter;
    private TextView status;
    private ProgressBar progress;
    private Button refresh;

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

        TextView app = text("TPAI_Android  •  V1.12", 13, Color.rgb(146,154,164), Typeface.BOLD);
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
                List<MatchItem> items = fetchLiveMatches();
                if (items.size() > MAX_MATCHES) items = new ArrayList<>(items.subList(0, MAX_MATCHES));
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
            m.player1 = playerName(row.optLong("player1_id", -1L), "Joueur 1");
            m.player2 = playerName(row.optLong("player2_id", -1L), "Joueur 2");
            m.live1 = a;
            m.live2 = b;
            String d = matchDate;
            String t = clean(row.optString("match_time", ""));
            m.stage = d + (t.isEmpty() ? "" : "  " + t);
            m.score = "LIVE réel • état " + live.optInt("state_number", 0);
            out.add(m);
            if (out.size() >= 30) break;
        }
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

    private View matchRow(MatchItem m) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(4), dp(3), dp(7), dp(3));
        line.setBackgroundColor(Color.rgb(27,31,35));

        TextView star = text("☆", 19, Color.rgb(220,228,234), Typeface.NORMAL);
        star.setGravity(Gravity.CENTER);
        line.addView(star, new LinearLayout.LayoutParams(dp(36), dp(62)));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_VERTICAL);
        TextView p1 = text(m.player1, 14, Color.WHITE, Typeface.BOLD);
        TextView p2 = text(m.player2, 14, Color.WHITE, Typeface.BOLD);
        center.addView(p1, new LinearLayout.LayoutParams(-1, dp(27)));
        center.addView(p2, new LinearLayout.LayoutParams(-1, dp(27)));
        line.addView(center, new LinearLayout.LayoutParams(0, dp(62), 1));

        LinearLayout odds = new LinearLayout(this);
        odds.setOrientation(LinearLayout.VERTICAL);
        odds.setGravity(Gravity.CENTER);
        TextView o1 = text(formatOdd(m.live1), 14, Color.rgb(255,214,64), Typeface.BOLD);
        TextView o2 = text(formatOdd(m.live2), 14, Color.rgb(255,214,64), Typeface.BOLD);
        o1.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        o2.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        odds.addView(o1, new LinearLayout.LayoutParams(-1, dp(27)));
        odds.addView(o2, new LinearLayout.LayoutParams(-1, dp(27)));
        line.addView(odds, new LinearLayout.LayoutParams(dp(62), dp(62)));

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setGravity(Gravity.CENTER);
        TextView st = text("ST", 10, Color.rgb(174,239,208), Typeface.BOLD);
        st.setGravity(Gravity.CENTER);
        TextView time = text(shortStage(m.stage), 9, Color.rgb(150,158,166), Typeface.NORMAL);
        time.setGravity(Gravity.CENTER);
        meta.addView(st, new LinearLayout.LayoutParams(-1, dp(27)));
        meta.addView(time, new LinearLayout.LayoutParams(-1, dp(27)));
        line.addView(meta, new LinearLayout.LayoutParams(dp(58), dp(62)));

        View separator = new View(this);
        separator.setBackgroundColor(Color.rgb(48,54,60));
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(line, new LinearLayout.LayoutParams(-1, dp(62)));
        wrapper.addView(separator, new LinearLayout.LayoutParams(-1, dp(1)));
        return wrapper;
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
