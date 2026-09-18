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
import java.util.Collections;
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

        TextView app = text("TPAI_Android", 13, Color.rgb(146,154,164), Typeface.BOLD);
        root.addView(app, new LinearLayout.LayoutParams(-1, dp(26)));

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
                Collections.shuffle(items);
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
            m.player1 = playerName(row.optLong("player1_id", -1L), "Joueur 1");
            m.player2 = playerName(row.optLong("player2_id", -1L), "Joueur 2");
            m.live1 = a;
            m.live2 = b;
            String d = clean(row.optString("match_date", ""));
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
        try {
            JSONArray rows = getJson("/rest/v1/players?select=" + enc("player_name")
                    + "&player_id=eq." + playerId + "&limit=1");
            if (rows.length() > 0) {
                String name = clean(rows.getJSONObject(0).optString("player_name", ""));
                if (!name.isEmpty()) return name;
            }
        } catch (Exception ignored) { }
        return fallback + " #" + playerId;
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
        for (MatchItem m : items) list.addView(matchCard(m), new LinearLayout.LayoutParams(-1, -2));
    }

    private View matchCard(MatchItem m) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundColor(Color.rgb(27,31,35));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.setMargins(0,0,0,dp(8)); card.setLayoutParams(cp);

        TextView meta = text((m.stage.isEmpty()?"MATCH D’ENTRAÎNEMENT":m.stage) + (m.score.isEmpty()?"":"  •  " + m.score), 11, Color.rgb(150,158,166), Typeface.BOLD);
        card.addView(meta);
        card.addView(playerLine(m.player1, m.live1));
        card.addView(playerLine(m.player2, m.live2));
        TextView code = text(m.code, 10, Color.rgb(105,113,121), Typeface.NORMAL); code.setGravity(Gravity.RIGHT);
        card.addView(code);
        return card;
    }

    private View playerLine(String player, String odd) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(player, 16, Color.WHITE, Typeface.BOLD);
        row.addView(name, new LinearLayout.LayoutParams(0, dp(38), 1));
        TextView live = text("LIVE  " + formatOdd(odd), 14, Color.rgb(57,255,136), Typeface.BOLD);
        live.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        row.addView(live, new LinearLayout.LayoutParams(dp(112), dp(38)));
        return row;
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
