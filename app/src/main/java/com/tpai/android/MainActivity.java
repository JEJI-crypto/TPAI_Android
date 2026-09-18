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
        // Source unique : schéma PostgreSQL/Supabase timported.
        // On demande directement le Déroulement car c'est là que le Client PC lit
        // matchs_j1_cote_direct / matchs_j2_cote_direct.
        String select = "matchs_code,matchs_j1_nom,matchs_j2_nom,matchs_j1_cote_direct,matchs_j2_cote_direct,matchs_set,matchs_score_final";
        String query = "/rest/v1/Tennis_matchs_deroulement?select=" + enc(select)
                + "&matchs_j1_cote_direct=not.is.null&matchs_j2_cote_direct=not.is.null"
                + "&limit=250";
        JSONArray rows;
        try {
            rows = getJson(query);
        } catch (Exception first) {
            // Compatibilité si les champs set/score portent des noms plus courts.
            select = "matchs_code,matchs_j1_nom,matchs_j2_nom,matchs_j1_cote_direct,matchs_j2_cote_direct";
            query = "/rest/v1/Tennis_matchs_deroulement?select=" + enc(select)
                    + "&matchs_j1_cote_direct=not.is.null&matchs_j2_cote_direct=not.is.null"
                    + "&limit=250";
            rows = getJson(query);
        }
        List<MatchItem> out = new ArrayList<>();
        for (int i=0; i<rows.length(); i++) {
            JSONObject o = rows.getJSONObject(i);
            String a = clean(o.optString("matchs_j1_cote_direct", ""));
            String b = clean(o.optString("matchs_j2_cote_direct", ""));
            if (!realOdd(a) || !realOdd(b)) continue;
            MatchItem m = new MatchItem();
            m.code = clean(o.optString("matchs_code", ""));
            m.player1 = valueOr(o.optString("matchs_j1_nom", ""), "Joueur 1");
            m.player2 = valueOr(o.optString("matchs_j2_nom", ""), "Joueur 2");
            m.live1 = a; m.live2 = b;
            m.stage = clean(o.optString("matchs_set", ""));
            m.score = clean(o.optString("matchs_score_final", ""));
            if (!m.code.isEmpty()) out.add(m);
        }
        return out;
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
