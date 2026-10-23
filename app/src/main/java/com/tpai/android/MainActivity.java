package com.tpai.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** TPAI V1.19 : uniquement HTTP REST sur PostgreSQL distant. Aucune base locale. */
public final class MainActivity extends Activity {
    private static final int PAGE_SIZE = 40;
    private static final int HISTORY_PAGE = 40;
    private static final String MATCH_FIELDS = "match_id,match_date,match_time,competition_id,player1_id,player2_id,match_type,team_type,live_status,live_states_count,live_odds_count,p2p_rows_count,p2p_games_count,avm_odd1,avm_odd2";
    private static final String MATCH_DETAIL_FIELDS = MATCH_FIELDS + ",scheduled_at,has_estimated_live,live_missing_points,updated_at";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout list;
    private TextView status, counter;
    private EditText search;
    private Button more, refresh;
    private ProgressBar progress;
    private boolean loading = false;
    private boolean hasMore = true;
    private long lastId = -1;
    private int totalLoaded = 0;
    private String currentFilter = "";
    private String playerIdClause = "";

    private static final class Match {
        JSONObject data;
        long id;
        String p1, p2, competition;
        Match(JSONObject row) { data = row; id = row.optLong("match_id", -1L); }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(11, 14, 17));
        getWindow().setNavigationBarColor(Color.rgb(11, 14, 17));
        setContentView(createScreen());
        loadMatches(true);
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View createScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(8), dp(14), dp(10));
        root.setBackgroundColor(Color.rgb(11, 14, 17));
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets pad = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(dp(14) + pad.left, dp(8) + pad.top, dp(14) + pad.right, dp(10) + pad.bottom);
                return insets;
            });
        }

        TextView title = txt("TPAI  ·  V1.19  ·  TOUS LES MATCHS", 19, Color.WHITE, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(45)));
        TextView sub = txt("TPAI_BUILD_V119_REST_TPAI • lecture distante • sans base locale", 11, 0xff9db2ac, 0);
        root.addView(sub, new LinearLayout.LayoutParams(-1, dp(31)));

        LinearLayout filters = new LinearLayout(this);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xff98a4a7);
        search.setTextSize(13);
        search.setHint("ID, AAAA-MM-JJ ou nom de joueur");
        search.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff5b9582));
        filters.addView(search, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button find = button("CHERCHER", 0xff285d45);
        find.setOnClickListener(v -> loadMatches(true));
        filters.addView(find, new LinearLayout.LayoutParams(dp(110), dp(50)));
        root.addView(filters);

        LinearLayout actions = new LinearLayout(this);
        refresh = button("ACTUALISER", 0xff25573e);
        refresh.setOnClickListener(v -> loadMatches(true));
        actions.addView(refresh, new LinearLayout.LayoutParams(0, dp(43), 1));
        Button reset = button("TOUS", 0xff2b3238);
        reset.setOnClickListener(v -> { search.setText(""); loadMatches(true); });
        actions.addView(reset, new LinearLayout.LayoutParams(0, dp(43), 1));
        root.addView(actions);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        status = txt("Connexion…", 12, 0xffb6edce, 0);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(38)));
        counter = txt("", 12, 0xffbec6cc, 0);
        root.addView(counter, new LinearLayout.LayoutParams(-1, dp(30)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        more = button("CHARGER 40 MATCHS SUPPLÉMENTAIRES", 0xff225740);
        more.setOnClickListener(v -> loadMatches(false));
        root.addView(more, new LinearLayout.LayoutParams(-1, dp(48)));
        return root;
    }

    private void busy(boolean value) {
        loading = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        refresh.setEnabled(!value);
        more.setEnabled(!value && hasMore);
    }

    private void loadMatches(boolean first) {
        if (loading) return;
        if (first) {
            lastId = -1;
            hasMore = true;
            totalLoaded = 0;
            playerIdClause = "";
            currentFilter = search.getText().toString().trim();
            list.removeAllViews();
        }
        if (!hasMore) return;
        busy(true);
        status.setText("Lecture des matchs sur le serveur…");
        final long cursor = lastId;
        final String filter = currentFilter;
        final String resolved = playerIdClause;
        executor.execute(() -> {
            try {
                String ids = resolved;
                if (first && !filter.isEmpty() && !filter.matches("\\d+") && !filter.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    ids = findPlayerIds(filter);
                }
                final String nextPlayerIds = ids;
                Map<String,String> q = SupabaseApi.args("select", MATCH_FIELDS, "order", "match_id.desc", "limit", String.valueOf(PAGE_SIZE));
                if (cursor >= 0) q.put("match_id", "lt." + cursor);
                if (filter.matches("\\d+")) q.put("match_id", "eq." + filter);
                else if (filter.matches("\\d{4}-\\d{2}-\\d{2}")) q.put("match_date", "eq." + filter);
                else if (!filter.isEmpty() && !ids.isEmpty())
                    q.put("or", "(player1_id.in.(" + ids + "),player2_id.in.(" + ids + "))");
                JSONArray rows = (!filter.isEmpty() && !filter.matches("\\d+") && !filter.matches("\\d{4}-\\d{2}-\\d{2}") && ids.isEmpty())
                        ? new JSONArray() : SupabaseApi.list("matches", q);
                List<Match> data = new ArrayList<>();
                for (int i=0;i<rows.length();i++) {
                    Match m = new Match(rows.getJSONObject(i));
                    if (m.id >= 0) data.add(m);
                }
                addLabels(data);
                final int returned = data.size();
                runOnUiThread(() -> {
                    playerIdClause = nextPlayerIds;
                    for (Match m : data) list.addView(card(m));
                    // Au-delà de 200 cartes, les plus anciennes quittent la RAM, pas le serveur.
                    while (list.getChildCount() > 200) list.removeViewAt(0);
                    totalLoaded += returned;
                    if (!data.isEmpty()) lastId = data.get(data.size()-1).id;
                    hasMore = returned == PAGE_SIZE && !filter.matches("\\d+");
                    busy(false);
                    counter.setText(totalLoaded + " matchs parcourus • " + list.getChildCount() + " affichés");
                    status.setText(returned == 0 ? "Aucun autre match pour cette recherche." : "Matchs lus directement dans tpai.matches");
                    if (returned == 0 && totalLoaded == 0) list.addView(txt("Aucun match trouvé.", 15, Color.WHITE, 0));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy(false);
                    status.setText("Erreur de lecture distante");
                    showFailure(e);
                });
            }
        });
    }

    private String findPlayerIds(String name) throws Exception {
        // Recherche de noms limitée à 30 IDs pour éviter les requêtes coûteuses.
        // L'ensemble des matchs reste consultable via navigation, ID ou date.
        String sanitized = name.replace('*', ' ').replace('%', ' ').replace(',', ' ').trim();
        if (sanitized.isEmpty()) return "";
        JSONArray names = SupabaseApi.list("players", SupabaseApi.args(
            "select", "player_id", "name", "ilike.*" + sanitized + "*", "limit", "30"));
        StringBuilder ids = new StringBuilder();
        for (int i=0;i<names.length();i++) {
            if (ids.length() > 0) ids.append(',');
            ids.append(names.getJSONObject(i).getLong("player_id"));
        }
        return ids.toString();
    }

    private void addLabels(List<Match> matches) throws Exception {
        Set<Long> players = new LinkedHashSet<>(), comps = new LinkedHashSet<>();
        for (Match m : matches) {
            long p1 = m.data.optLong("player1_id", 0), p2 = m.data.optLong("player2_id", 0), c = m.data.optLong("competition_id", 0);
            if (p1 > 0) players.add(p1);
            if (p2 > 0) players.add(p2);
            if (c > 0) comps.add(c);
        }
        Map<Long,String> playerNames = lookup("players", "player_id", "name", players);
        Map<Long,String> compNames = lookup("competitions", "competition_id", "name", comps);
        for (Match m : matches) {
            long p1 = m.data.optLong("player1_id",0), p2 = m.data.optLong("player2_id",0);
            m.p1 = playerNames.getOrDefault(p1, p1 > 0 ? "Joueur #"+p1 : "Joueur 1");
            m.p2 = playerNames.getOrDefault(p2, p2 > 0 ? "Joueur #"+p2 : "Joueur 2");
            long c=m.data.optLong("competition_id",0);
            m.competition = compNames.getOrDefault(c, c > 0 ? "Compétition #"+c : "Compétition inconnue");
        }
    }

    private Map<Long,String> lookup(String table, String col, String display, Set<Long> ids) throws Exception {
        Map<Long,String> names = new HashMap<>();
        if (ids.isEmpty()) return names;
        StringBuilder arg = new StringBuilder("in.(");
        for (Long id : ids) {
            if (arg.length() > 4) arg.append(',');
            arg.append(id);
        }
        arg.append(')');
        JSONArray data = SupabaseApi.list(table, SupabaseApi.args("select", col+","+display, col, arg.toString(), "limit", "100"));
        for (int i=0;i<data.length();i++) {
            JSONObject row=data.getJSONObject(i);
            String n=row.optString(display,"").trim();
            if (!n.isEmpty() && !n.equals("null")) names.put(row.getLong(col), n);
        }
        return names;
    }

    private View card(Match match) {
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12),dp(10),dp(12),dp(10));
        card.setBackgroundColor(0xff1c2225);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,0,0,dp(9)); card.setLayoutParams(lp);
        JSONObject r=match.data;
        String date=r.optString("match_date", "Date inconnue") + "  " + r.optString("match_time", "");
        card.addView(txt(date + " • #" + match.id, 11, 0xff97ada5, 0));
        card.addView(txt(match.p1 + "   —   " + match.p2, 16, Color.WHITE, Typeface.BOLD));
        card.addView(txt(match.competition, 12, 0xffbac6cf, 0));
        String state = r.optString("live_status", "N/C");
        String hist = "États: " + r.optLong("live_states_count",0) + " • Points: " + r.optLong("p2p_rows_count",0) + " • Jeux: " + r.optLong("p2p_games_count",0);
        card.addView(txt("Qualité historique LIVE: " + state + " • " + hist, 11, 0xff97bea5, 0));
        card.setOnClickListener(v -> showMatch(match));
        return card;
    }

    private void showMatch(Match m) {
        status.setText("Lecture de la fiche #" + m.id + "…");
        executor.execute(() -> {
            try {
                JSONArray data=SupabaseApi.list("matches", SupabaseApi.args("select", MATCH_DETAIL_FIELDS, "match_id", "eq."+m.id,"limit","1"));
                if (data.length()==0) throw new Exception("Match absent sur le serveur");
                JSONObject record=data.getJSONObject(0);
                JSONArray odds=SupabaseApi.list("live_states", SupabaseApi.args("select","source_row,sets_p1,sets_p2,games_p1,games_p2,odd1,odd2,is_estimated",
                        "match_id","eq."+m.id,"is_estimated","eq.false","odd1","gt.1","odd2","gt.1","order","source_row.desc","limit","1"));
                StringBuilder info=new StringBuilder();
                info.append(m.p1).append("  —  ").append(m.p2).append("\n").append(m.competition).append("\n\n");
                info.append("DERNIÈRES COTES LIVE NON ESTIMÉES ENREGISTRÉES\n");
                if (odds.length()>0) {
                    JSONObject o=odds.getJSONObject(0);
                    info.append("Joueur 1 : ").append(decimal(o.optDouble("odd1"))).append("   Joueur 2 : ").append(decimal(o.optDouble("odd2"))).append("\n");
                    info.append("Sets : ").append(o.optString("sets_p1","?")).append("-").append(o.optString("sets_p2","?"));
                    info.append("  Jeux : ").append(o.optString("games_p1","?")).append("-").append(o.optString("games_p2","?")).append("\n\n");
                } else info.append("Non disponibles pour ce match\n\n");
                info.append("FICHE MATCH BRUTE (SOURCE DISTANTE)\n").append(record.toString(2));
                runOnUiThread(() -> {
                    status.setText("Fiche #"+m.id+" chargée depuis Supabase");
                    new AlertDialog.Builder(this)
                        .setTitle("Match #"+m.id)
                        .setMessage(info.toString())
                        .setPositiveButton("LIVE", (dialog, which) -> showHistory(m.id,"live_states",-1))
                        .setNeutralButton("POINTS", (dialog, which) -> showHistory(m.id,"p2p_rows",-1))
                        .setNegativeButton("JEUX", (dialog, which) -> showHistory(m.id,"p2p_games",-1))
                        .show();
                });
            } catch(Exception e) { runOnUiThread(() -> showFailure(e)); }
        });
    }

    private void showHistory(long id,String table,long cursor) {
        status.setText("Historique "+table+" : lecture distante…");
        executor.execute(() -> {
            try {
                String orderCol = table.equals("p2p_games") ? "id" : "source_row";
                String select;
                if (table.equals("live_states"))
                    select="source_row,t_raw,sets_p1,sets_p2,games_p1,games_p2,points_p1,points_p2,server,odd1,odd2,is_estimated";
                else if (table.equals("p2p_rows"))
                    select="source_row,code,set_no,row_type,payload_count,decode_status";
                else select="id,set_no,game_no,is_tiebreak,server,winner,points_count,decode_mode,confidence,lossy";
                Map<String,String> q=SupabaseApi.args("select",select,"match_id","eq."+id,"order",orderCol+".desc","limit",String.valueOf(HISTORY_PAGE));
                if (cursor>=0) q.put(orderCol,"lt."+cursor);
                JSONArray data=SupabaseApi.list(table,q);
                StringBuilder message=new StringBuilder();
                for (int i=0;i<data.length();i++) {
                    JSONObject row=data.getJSONObject(i);
                    message.append(row.toString()).append("\n\n");
                }
                if (data.length()==0) message.append("Aucun événement pour cette page.");
                long next=data.length()>0 ? data.getJSONObject(data.length()-1).optLong(orderCol,-1) : -1;
                runOnUiThread(() -> {
                    status.setText("Historique distant : "+table);
                    new AlertDialog.Builder(this).setTitle(table+"  •  match #"+id)
                        .setMessage(message.toString())
                        .setPositiveButton(data.length()==HISTORY_PAGE ? "PAGE SUIVANTE" : "TERMINÉ", (dialog, which) -> {
                            if (data.length()==HISTORY_PAGE && next>=0) showHistory(id,table,next);
                        })
                        .setNegativeButton("FERMER",null)
                        .show();
                });
            } catch(Exception e) { runOnUiThread(() -> showFailure(e)); }
        });
    }

    private void showFailure(Exception error) {
        String msg = error.getMessage()==null ? error.getClass().getSimpleName() : error.getMessage();
        if (msg.length()>500) msg=msg.substring(0,500);
        status.setText("Lecture distante indisponible");
        new AlertDialog.Builder(this).setTitle("TPAI — Connexion").setMessage(msg).setPositiveButton("OK",null).show();
    }

    private String decimal(double value) { return Double.isFinite(value) ? String.format(Locale.FRANCE,"%.2f",value) : "N/C"; }
    private Button button(String caption,int background) {
        Button result=new Button(this);
        result.setText(caption);result.setTextSize(11);result.setTextColor(Color.WHITE);
        result.setBackgroundTintList(android.content.res.ColorStateList.valueOf(background));
        return result;
    }
    private TextView txt(String content,int size,int color,int style) {
        TextView view=new TextView(this);
        view.setText(content);view.setTextSize(size);view.setTextColor(color);
        view.setTypeface(Typeface.create("sans",style));view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
