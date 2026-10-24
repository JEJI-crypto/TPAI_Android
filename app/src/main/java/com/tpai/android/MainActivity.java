package com.tpai.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** TPAI V1.28 : onglet Matchs d'entrainement de la version PC 845, adaptation Android.
 *  Une seule base distante tpai via REST; ZERO SQLite / zero copie permanente.
 *  La lecture reproduit les etats LIVE enregistres, non le moteur PC PBP complet.
 */
public final class MainActivity extends Activity {
    private static final String BUILD_MARKER = "TPAI_BUILD_V128_PC_TRAINING_CASCADE";
    private static final int MAX_TRAINING = 10;
    private static final String MATCH_FIELDS = "match_id,match_date,match_time,competition_id,player1_id,player2_id,match_type,team_type,live_status,has_estimated_live,live_missing_points,live_states_count,live_odds_count,p2p_games_count,avm_odd1,avm_odd2";
    private static final String LIVE_FIELDS = "source_row,t_raw,sets_p1,sets_p2,games_p1,games_p2,points_p1,points_p2,server,odd1,odd2,is_estimated";
    private static final int BG = 0xff000000, PANEL = 0xff101820, PANEL2 = 0xff20262c;
    private static final int GREEN = 0xff39ff88, BLUE = 0xff22a9ff, PINK = 0xffff5a9e, MUTED = 0xffb7c2c9;
    private static final long QUICK_STEP_MS = 2500L;
    private static final long PC_POINT_MS = 20000L;
    private static final long PC_GAME_MS = 56000L;
    private static final long PC_SET_MS = 108000L;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<Long, TrainingMatch> training = new LinkedHashMap<>();
    private final Random random = new Random();
    private final Map<String,String> filters = new HashMap<>();
    private long generation = 0;
    private boolean inProgress = false;
    private LinearLayout listHost, bannerHost, filterHost, root, transportHost;
    private ScrollView listScroll;
    private TextView status, counter, playbackStatus;
    private CheckBox selectAll;
    private EditText playerSearch;
    private Button randomButton;
    private String selectedCategory = "", selectedTournament = "", selectedSeason = "";
    private String selectedCategoryLabel="", selectedTournamentLabel="";
    private final Map<String,Bitmap> portraits=new HashMap<>();
    private long activeId = -1;
    private int randomCount = 4;
    private String lastPlaybackMode = "Lecture normale";
    private boolean pcRhythm = true; // rythme naturel par defaut
    // Menu temporaire non organisé : option EN MEMOIRE SEULEMENT, remise à faux à chaque lancement.
    private volatile boolean includeEstimated = false;
    // Un téléchargement lancé sous l'ancien filtre ne peut jamais ajouter un match après bascule.
    private volatile long filterRevision = 0;
    private TextView subtitleView;
    private boolean appVisible = true;
    private int repeatedSpeed = 1;
    private int repeatedDirection = 0;
    private boolean destroyed = false;
    private SharedPreferences preferences;

    static final class TrainingMatch {
        final long id;
        final JSONObject match;
        String player1 = "Joueur 1", player2 = "Joueur 2", tournament = "Compétition";
        boolean checked = false, favorite = false, showResult = false, loadingStates = false;
        String mode = "play";
        int speed = 1, step = 0;
        final double tempo;
        long dueAt = 0L;
        final ArrayList<JSONObject> states = new ArrayList<>();
        boolean loaded = false, complete = false;
        TrainingMatch(JSONObject obj) { match=obj; id=obj.optLong("match_id",-1); tempo=0.88+(Math.abs(id*37L+117L)%25)/100.0; }
        JSONObject current() {
            if (states.isEmpty()) return null;
            return states.get(Math.min(Math.max(0,step),states.size()-1));
        }
        JSONObject finalState() { return states.isEmpty()?null:states.get(states.size()-1); }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xff080b0e);
        getWindow().setNavigationBarColor(0xff080b0e);
        includeEstimated=false; // Toujours décochée au lancement, même si l’utilisateur l’avait cochée.
        buildUi();
        preferences=getSharedPreferences("tpai_training_selection",MODE_PRIVATE);
        addRandom(); // 4 nouveaux matchs à chaque lancement, sans sélection implicite.
        timer.postDelayed(tick, 300);
    }
    @Override protected void onPause() {
        appVisible = false;
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        appVisible = true;
        // Pas de saut dans l'historique pendant que le téléphone était en arrière-plan.
        tickHistory.clear();
    }
    @Override protected void onDestroy() {
        destroyed=true;
        timer.removeCallbacksAndMessages(null);
        io.shutdownNow();
        super.onDestroy();
    }

    private View text(String s, int sp, int fg, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(sp); v.setTextColor(fg);
        v.setTypeface(Typeface.create("sans-serif", bold?Typeface.BOLD:Typeface.NORMAL));
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    private TextView tv(String s,int size,int color,boolean bold) { return (TextView)text(s,size,color,bold); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable bg(int color, int strokeColor, int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if(strokeColor!=0)d.setStroke(dp(1),strokeColor);
        return d;
    }
    private Button button(String s, int color) {
        Button b=new Button(this); b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(11);
        b.setAllCaps(false);b.setPadding(dp(6),0,dp(6),0);
        b.setBackground(bg(color,0xff344954,8));b.setMinWidth(0);b.setMinimumHeight(0);
        return b;
    }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL);return l; }
    private LinearLayout col() { LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l; }
    private void margin(View v, LinearLayout parent, int w, int h, int left,int top,int right,int bottom) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));
        p.setMargins(dp(left),dp(top),dp(right),dp(bottom));parent.addView(v,p);
    }
    private void message(String s) { if(status!=null)status.setText(s); }
    private void fail(Exception e) { if(destroyed)return;String s=e.getMessage()==null?e.toString():e.getMessage();
        if(s.length()>450)s=s.substring(0,450);
        message("Erreur serveur : "+s);
        new AlertDialog.Builder(this).setTitle("Lecture distante impossible").setMessage(s).setPositiveButton("OK",null).show();
    }
    private void ui(Runnable r) { runOnUiThread(()->{if(!destroyed)r.run();}); }
    private void work(String label, ThrowingJob job) {
        if(inProgress){message("Opération déjà en cours…");return;}
        inProgress=true;message(label);
        io.execute(()->{
            try {job.execute();}catch(Exception e){ui(()->fail(e));}
            finally {ui(()->inProgress=false);}
        });
    }
    interface ThrowingJob { void execute() throws Exception; }

    private void buildUi() {
        root=col();root.setBackgroundColor(BG);root.setPadding(dp(10),dp(5),dp(10),dp(5));
        if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets p=insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(dp(10)+p.left,dp(5)+p.top,dp(10)+p.right,dp(5)+p.bottom);return insets;});
        setContentView(root);
        LinearLayout topbar=row();
        Button hamburger=button("☰",0xff111820);hamburger.setTextSize(22);
        hamburger.setContentDescription("Menu général");hamburger.setOnClickListener(v->showMainMenu());
        margin(hamburger,topbar,44,44,0,0,7,0);
        TextView title=tv("TPAI",20,Color.WHITE,true);
        topbar.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));
        margin(topbar,root,-1,47,0,0,0,4);
        subtitleView=tv("MATCHS D’ENTRAÎNEMENT",12,GREEN,true);
        margin(subtitleView,root,-1,25,4,0,0,2);
        // Le bandeau ET les commandes restent invisibles sans sélection explicite.
        bannerHost=col();bannerHost.setBackground(bg(0xff10151b,0xff26363d,8));
        margin(bannerHost,root,-1,-2,0,0,0,2);
        transportHost=col();
        LinearLayout buttons=row();
        transportButton(buttons,"⏪",-1);transportButton(buttons,"⏸",0);
        transportButton(buttons,"▶",1);transportButton(buttons,"⏹",2);transportButton(buttons,"⏩",3);
        margin(buttons,transportHost,-1,44,0,0,0,0);
        playbackStatus=tv("Lecture des matchs cochés · appuyer pour changer le rythme",10,MUTED,false);
        playbackStatus.setGravity(Gravity.CENTER);
        margin(playbackStatus,transportHost,-1,29,0,0,0,0);
        playbackStatus.setOnClickListener(v->showRhythmDialog());
        margin(transportHost,root,-1,-2,0,0,0,4);
        // Conservés hors écran : les filtres s'ouvrent désormais exclusivement par l'icône +.
        filterHost=row();
        playerSearch=new EditText(this);playerSearch.setSingleLine(true);
        playerSearch.setText(" ");
        randomButton=button("ALÉATOIRE",0xff123b52);randomButton.setOnClickListener(v->addRandom());
        LinearLayout listBar=row();listBar.setPadding(dp(3),0,dp(2),0);
        selectAll=new CheckBox(this);selectAll.setText("Tous");selectAll.setTextColor(Color.WHITE);
        selectAll.setTextSize(12);selectAll.setButtonTintList(ColorStateList.valueOf(GREEN));
        selectAll.setOnClickListener(v->{for(TrainingMatch m:training.values())m.checked=selectAll.isChecked();saveSelection();renderList();});
        margin(selectAll,listBar,94,42,0,0,1,0);
        counter=tv("0 / 10 matchs",12,Color.WHITE,true);
        listBar.addView(counter,new LinearLayout.LayoutParams(0,dp(42),1));
        Button actions=button("⋮",0xff19232b);actions.setTextSize(23);
        actions.setContentDescription("Options de la liste");actions.setOnClickListener(v->showListMenu());
        Button search=button("⌕",0xff101820);search.setTextSize(22);
        search.setContentDescription("Rechercher un joueur");search.setOnClickListener(v->showSearchDialog());
        margin(search,listBar,41,42,0,0,3,0);
        Button add=button("＋",0xff133443);add.setTextSize(22);
        add.setContentDescription("Ajouter des matchs");add.setOnClickListener(v->showAddMenu());
        margin(add,listBar,41,42,0,0,3,0);
        margin(actions,listBar,44,42,0,0,0,0);
        margin(listBar,root,-1,43,0,0,0,2);
        listScroll=new ScrollView(this);listScroll.setFillViewport(true);
        listHost=col();listScroll.addView(listHost);
        root.addView(listScroll,new LinearLayout.LayoutParams(-1,0,1));
        status=tv("Recherche de 4 matchs aux cotes LIVE complètes…",10,MUTED,false);
        status.setMaxLines(2);margin(status,root,-1,-2,5,3,2,3);
        renderBanner();renderList();
    }
    private void showRhythmDialog(){
        new AlertDialog.Builder(this).setTitle("Rythme de lecture")
            .setItems(new String[]{"RAPIDE · 2,5 s par état", "INSPIRÉ DU PC · 20 s par état, pauses jeu/set"},(d,n)->{
                pcRhythm=n==1;tickHistory.clear();updatePlaybackStatus();
                message(pcRhythm?"Rythme PC activé.":"Lecture rapide activée.");
            }).show();
    }
    private void showSearchDialog(){
        EditText entry=new EditText(this);entry.setSingleLine(true);entry.setTextColor(Color.WHITE);
        entry.setHint("Nom du joueur");entry.setHintTextColor(MUTED);
        entry.setPadding(dp(16),dp(7),dp(16),dp(7));entry.setTextSize(16);
        new AlertDialog.Builder(this).setTitle("Rechercher un joueur")
            .setView(entry).setNegativeButton("ANNULER",null)
            .setPositiveButton("RECHERCHER",(d,w)->{
                playerSearch.setText(entry.getText().toString());searchPlayer();
            }).show();
    }
    /** Deux blocs uniquement : tirage aleatoire ou selection en cascade. */
    private void showAddMenu(){
        LinearLayout body=col();body.setPadding(dp(16),dp(10),dp(16),dp(12));
        TextView randomTitle=tv("1. MATCHS ALÉATOIRES",14,GREEN,true);
        margin(randomTitle,body,-1,38,0,0,0,4);
        Button randomAdd=button("Choisir de 1 à 10 matchs aléatoires",0xff133443);
        randomAdd.setOnClickListener(v->showRandomOptions());margin(randomAdd,body,-1,48,0,0,0,14);
        TextView title=tv("2. SÉLECTION MANUELLE",14,GREEN,true);
        margin(title,body,-1,38,0,0,0,6);
        Button choose=button("Catégorie  →  Tournoi  →  Saison  →  Matchs",0xff172832);
        choose.setOnClickListener(v->categoryCascade());margin(choose,body,-1,55,0,0,0,4);
        TextView hint=tv("Choisis d'abord une catégorie, puis les menus suivants deviennent disponibles.",12,MUTED,false);
        margin(hint,body,-1,-2,0,0,0,4);
        new AlertDialog.Builder(this).setTitle("Ajouter des matchs")
            .setView(body).setPositiveButton("FERMER",null).show();
    }
    private void categoryCascade(){
        final String[] types={"ATP","WTA","Challenger","ITF"};
        new AlertDialog.Builder(this).setTitle("1 / 4 · Catégorie")
            .setItems(types,(dialog,pos)->{
                selectedCategory=types[pos];selectedCategoryLabel=types[pos];
                selectedTournament="";selectedTournamentLabel="";selectedSeason="";
                tournamentCascade();
            }).setNegativeButton("ANNULER",null).show();
    }
    private void tournamentCascade(){
        final String category=selectedCategory;
        work("Chargement des tournois " + category + "…",()->{
            // Limite pour éviter les requêtes sur l'historique complet des matchs.
            JSONArray rows=SupabaseApi.list("competitions",SupabaseApi.args("select","competition_id,name", "name","ilike.*"+cleanFilter(category)+"*", "order","name.asc", "limit","200"));
            ArrayList<String> names=new ArrayList<>(), ids=new ArrayList<>();
            for(int i=0;i<rows.length();i++){
                JSONObject r=rows.getJSONObject(i);String name=r.optString("name","");
                if(name.trim().isEmpty())continue;
                names.add(name);ids.add(r.optString("competition_id"));
            }
            ui(()->{
                if(names.isEmpty()){notice("Tournois","Aucun tournoi pour cette catégorie.");return;}
                new AlertDialog.Builder(this).setTitle("2 / 4 · Tournoi · "+category)
                    .setItems(names.toArray(new String[0]),(d,n)->{
                        selectedTournament=ids.get(n);selectedTournamentLabel=names.get(n);selectedSeason="";
                        seasonCascade();
                    }).setNegativeButton("RETOUR",(d,w)->categoryCascade()).show();
            });
        });
    }
    private void seasonCascade(){
        final String competitionId=selectedTournament;
        work("Chargement des saisons…",()->{
            // Lire une petite plage de dates parmi les matchs du tournoi sélectionné.
            JSONArray rows=SupabaseApi.list("matches",SupabaseApi.args("select","match_date", "competition_id","eq."+competitionId, "order","match_date.desc", "limit","400"));
            Set<String> years=new LinkedHashSet<>();
            for(int i=0;i<rows.length();i++){
                String date=rows.getJSONObject(i).optString("match_date","");
                if(date.length()>=4)years.add(date.substring(0,4));
            }
            ui(()->{
                if(years.isEmpty()){notice("Saisons","Aucune saison disponible pour ce tournoi.");return;}
                ArrayList<String> options=new ArrayList<>(years);
                new AlertDialog.Builder(this).setTitle("3 / 4 · Saison · "+selectedTournamentLabel)
                    .setItems(options.toArray(new String[0]),(d,n)->{
                        selectedSeason=options.get(n);
                        matchesDialog();
                    }).setNegativeButton("RETOUR",(d,w)->tournamentCascade()).show();
            });
        });
    }
    private void showRandomOptions(){
        String[] opts=new String[10];for(int i=0;i<10;i++)opts[i]=(i+1)+" match"+(i==0?"":"s");
        new AlertDialog.Builder(this).setTitle("Ajouter des matchs aléatoires")
            .setSingleChoiceItems(opts,randomCount-1,(d,n)->{randomCount=n+1;d.dismiss();addRandom();})
            .setNegativeButton("ANNULER",null).show();
    }
    private void showListMenu(){
        new AlertDialog.Builder(this).setTitle("Liste d'entraînement")
            .setItems(new String[]{"Supprimer les matchs cochés", "Vider la liste (favoris protégés)", "Désélectionner le bandeau", "Rythme de lecture"},(d,n)->{
                if(n==0)deleteChecked();else if(n==1)clearTraining();else if(n==2){activeId=-1;saveSelection();renderBanner();renderList();}
                else showRhythmDialog();
            }).show();
    }
    /** Menu temporaire : toutes les nouvelles options seront ajoutées ici au fur et à mesure. */
    private void showMainMenu() {
        LinearLayout menu=col();
        menu.setPadding(dp(20),dp(10),dp(20),dp(12));
        menu.setBackgroundColor(PANEL);
        CheckBox estimated=new CheckBox(this);
        estimated.setText("Côtes estimées incluses");
        estimated.setTextSize(16);
        estimated.setTextColor(Color.WHITE);
        estimated.setButtonTintList(ColorStateList.valueOf(GREEN));
        estimated.setChecked(includeEstimated);
        margin(estimated,menu,-1,50,0,0,0,3);
        TextView explanation=tv("Décochée : cotes LIVE 100 % observées.\nCochée : inclut également les matchs dont des cotes LIVE ont été estimées par Booker. Les cotes manquantes restent interdites.",12,MUTED,false);
        margin(explanation,menu,-1,-2,0,0,0,5);
        estimated.setOnCheckedChangeListener((button,checked)->setIncludeEstimated(checked));
        new AlertDialog.Builder(this).setTitle("☰  Menu TPAI")
            .setView(menu).setPositiveButton("FERMER",null).show();
    }

    /** Applique immédiatement le filtre, sans sauvegarder l'option entre les sessions. */
    private void setIncludeEstimated(boolean allowed) {
        if(includeEstimated==allowed)return;
        includeEstimated=allowed;
        filterRevision++;
        if(subtitleView!=null)subtitleView.setText(allowed
            ?"MATCHS D’ENTRAÎNEMENT · ESTIMATIONS INCLUSES"
            :"MATCHS D’ENTRAÎNEMENT");
        int removed=0;
        if(!allowed){
            List<Long> invalid=new ArrayList<>();
            for(TrainingMatch m:training.values()){
                boolean estimatedState=false;
                for(JSONObject state:m.states){
                    if(state.optBoolean("is_estimated",false)){estimatedState=true;break;}
                }
                if(!eligibleMetadata(m.match,false)||estimatedState)invalid.add(m.id);
            }
            for(Long id:invalid){training.remove(id);tickHistory.remove(id);}
            removed=invalid.size();
            if(!training.containsKey(activeId))activeId=training.isEmpty()?-1:training.values().iterator().next().id;
        }
        saveSelection();
        renderBanner();renderList();
        message(allowed
            ?"Côtes estimées incluses : matchs observés et estimés admissibles. Aucun trou autorisé."
            :"Côtes estimées exclues : "+removed+" match(s) retiré(s). Cotes observées uniquement.");
    }

    private void makeSelector(String title,Runnable handler){
        Button b=button(title,0xff1a2933);b.setTextSize(11);b.setOnClickListener(v->handler.run());
        margin(b,filterHost,title.equals("Réinit.")?78:104,38,0,0,4,0);
    }
    private void transportButton(LinearLayout host,String symbol,int action){
        Button b=button(symbol,0xff172832);b.setTextSize(19);b.setOnClickListener(v->playback(action));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(39),1);p.setMargins(dp(2),0,dp(2),0);host.addView(b,p);
    }
    private void clearFilters(){selectedCategory="";selectedTournament="";selectedSeason="";message("Filtres réinitialisés.");}

    private void categoryDialog() {
        String[] names={"Toutes catégories","ATP","WTA","Challenger","ITF"};
        new AlertDialog.Builder(this).setTitle("Catégories · tournois distants")
            .setItems(names,(dialog,which)->{
                selectedCategory=which==0?"":names[which];
                selectedTournament="";message("Catégorie : "+(selectedCategory.isEmpty()?"Toutes":selectedCategory));
                matchesDialog();
            }).setNegativeButton("FERMER",null).show();
    }
    private void tournamentDialog() {
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Nom ou partie du tournoi");
        input.setText(selectedTournament);
        new AlertDialog.Builder(this).setTitle("Tournois").setMessage("Recherche les compétitions par nom sur Supabase.")
            .setView(input).setPositiveButton("CHERCHER",(d,w)->{
                final String term=input.getText().toString().trim();
                if(term.isEmpty()){selectedTournament="";matchesDialog();return;}
                work("Recherche des tournois…",()->{
                    JSONArray rows=SupabaseApi.list("competitions",SupabaseApi.args("select","competition_id,name","name","ilike.*"+cleanFilter(term)+"*","limit","80","order","name.asc"));
                    String[] labels=new String[rows.length()];String[] ids=new String[rows.length()];
                    for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);labels[i]=r.optString("name","?");ids[i]=r.optString("competition_id");}
                    ui(()->{
                        if(labels.length==0){message("Aucun tournoi trouvé.");return;}
                        new AlertDialog.Builder(this).setTitle("Choisir un tournoi")
                            .setItems(labels,(dd,idx)->{selectedTournament=ids[idx];message("Tournoi : "+labels[idx]);matchesDialog();})
                            .setNegativeButton("FERMER",null).show();
                    });
                });
            }).setNeutralButton("TOUS",(d,w)->{selectedTournament="";matchesDialog();})
            .setNegativeButton("ANNULER",null).show();
    }
    private void seasonDialog() {
        int thisYear=java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        String[] years=new String[19];years[0]="Toutes les saisons";
        for(int i=1;i<years.length;i++)years[i]=String.valueOf(thisYear-i+1);
        new AlertDialog.Builder(this).setTitle("Saisons")
            .setItems(years,(d,n)->{selectedSeason=n==0?"":years[n];message("Saison : "+(selectedSeason.isEmpty()?"Toutes":selectedSeason));matchesDialog();})
            .setNegativeButton("FERMER",null).show();
    }
    private void matchesDialog(){ browseMatches("",-1); }
    /** Filtre de qualité Booker ; les matchs incomplets restent toujours exclus. */
    private boolean eligibleMetadata(JSONObject obj) {
        return eligibleMetadata(obj,includeEstimated);
    }
    private static boolean eligibleMetadata(JSONObject obj, boolean allowEstimates) {
        if(obj==null)return false;
        String quality=obj.optString("live_status", "");
        boolean hasEstimates=obj.optBoolean("has_estimated_live",true);
        if("COMPLETE_OBSERVED".equals(quality)){
            if(hasEstimates)return false;
        } else if("COMPLETE_ESTIMATED".equals(quality)){
            if(!allowEstimates||!hasEstimates)return false;
        } else return false;
        if(obj.optLong("live_missing_points",-1)!=0)return false;
        long states=obj.optLong("live_states_count",0);
        long quotes=obj.optLong("live_odds_count",0);
        if(states<2||states>6000||quotes!=states)return false;
        return validOdd(obj,"avm_odd1")&&validOdd(obj,"avm_odd2");
    }

    /** Les cotes sont exclusivement celles déjà enregistrées ; aucune interpolation Android. */
    private static boolean validOdd(JSONObject src, String col) {
        if (src == null || src.isNull(col)) return false;
        double value = src.optDouble(col, Double.NaN);
        return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0;
    }

    private static void strictFilters(Map<String,String> q, boolean allowEstimates) {
        if(allowEstimates){
            q.put("live_status","in.(COMPLETE_OBSERVED,COMPLETE_ESTIMATED)");
        }else{
            q.put("live_status","eq.COMPLETE_OBSERVED");
            q.put("has_estimated_live","eq.false");
        }
        q.put("live_missing_points", "eq.0");
        q.put("live_states_count", "gt.1");
        q.put("live_odds_count", "gt.1");
    }

    /** Contrôle TOUS les états avant l'admission, y compris les estimations signalées. */
    private static void loadObservedHistory(TrainingMatch m, boolean allowEstimates) throws Exception {
        if (!eligibleMetadata(m.match,allowEstimates))
            throw new Exception("Match #"+m.id+" : historique Booker non admissible avec le filtre actuel.");
        ArrayList<JSONObject> loaded = new ArrayList<>();
        long cursor = -1;
        boolean reachedEnd = false;
        final long expected = m.match.optLong("live_states_count", 0);
        for (int batch = 0; batch < 13; batch++) {
            Map<String,String> q = SupabaseApi.args("select", LIVE_FIELDS, "match_id", "eq."+m.id,
                "order", "source_row.asc", "limit", "500");
            if (cursor >= 0) q.put("source_row", "gt."+cursor);
            JSONArray page = SupabaseApi.list("live_states", q);
            for (int i=0; i<page.length(); i++) {
                JSONObject state = page.getJSONObject(i);
                if ((!allowEstimates && state.optBoolean("is_estimated", true)) ||
                    !validOdd(state,"odd1") || !validOdd(state,"odd2"))
                    throw new Exception("Match #"+m.id+" refusé : cotes LIVE absentes ou estimation désactivée à la ligne " + state.optLong("source_row") + ".");
                long sourceRow = state.optLong("source_row", -1);
                if (sourceRow <= cursor) throw new Exception("Ordre LIVE invalide pour le match #"+m.id);
                loaded.add(state);
                cursor = sourceRow;
                if (loaded.size() > expected) throw new Exception("Nombre d'états LIVE supérieur à celui déclaré pour #"+m.id);
            }
            if (page.length() < 500) { reachedEnd = true; break; }
        }
        if (!reachedEnd || loaded.size() != expected || loaded.size() < 2 ||
            expected != m.match.optLong("live_odds_count", -1)) {
            throw new Exception("Match #"+m.id+" refusé : historique LIVE tronqué ou incohérent ("+
                loaded.size()+" / "+expected+" états).");
        }
        m.states.clear();
        m.states.addAll(loaded);
        m.loaded = true;
        m.complete = true;
        // Ouvrir une rencontre comme un vrai flux LIVE pris en cours de jeu.
        // Position stable et differente selon l'identifiant, loin du debut et de la fin.
        int n=m.states.size();
        int lo=Math.max(1,n/8), hi=Math.max(lo+1,n-n/7);
        m.step=Math.min(n-2,lo+new Random(System.nanoTime() ^ (m.id*1000003L)).nextInt(Math.max(1,hi-lo)));
        m.showResult = false;
    }

    private void browseMatches(String playerIds,long before){
        final boolean allowEstimates=includeEstimated;
        final long revision=filterRevision;
        work("Recherche des matchs historiques…",()->{
            Map<String,String> q=SupabaseApi.args("select",MATCH_FIELDS,"order","match_id.desc","limit","40");
            strictFilters(q,allowEstimates);
            if(before>0)q.put("match_id","lt."+before);
            if(!selectedSeason.isEmpty()){
                q.put("match_date","gte."+selectedSeason+"-01-01");
                q.put("and","(match_date.lt."+(Integer.parseInt(selectedSeason)+1)+"-01-01)");
            }
            if(!selectedTournament.isEmpty())q.put("competition_id","eq."+selectedTournament);
            else if(!selectedCategory.isEmpty()){
                String term=selectedCategory;
                String ids=competitionIdsForTerm(term);
                if(ids.isEmpty())throw new Exception("Aucun tournoi pour cette catégorie. Essaie un autre filtre.");
                q.put("competition_id","in.("+ids+")");
            }
            if(!playerIds.isEmpty())q.put("or","(player1_id.in.("+playerIds+"),player2_id.in.("+playerIds+"))");
            JSONArray results=SupabaseApi.list("matches",q);
            List<TrainingMatch> found=resolve(results,allowEstimates);
            long next=results.length()==0?-1:results.getJSONObject(results.length()-1).optLong("match_id",-1);
            ui(()->{if(revision==filterRevision)showMatchChoices(found,playerIds,next,revision,allowEstimates);});
        });
    }
    private String competitionIdsForTerm(String term) throws Exception {
        JSONArray a=SupabaseApi.list("competitions",SupabaseApi.args("select","competition_id","name","ilike.*"+cleanFilter(term)+"*","limit","300"));
        StringBuilder b=new StringBuilder();for(int i=0;i<a.length();i++){
            if(b.length()>0)b.append(',');b.append(a.getJSONObject(i).optLong("competition_id"));
        }return b.toString();
    }
    private void showMatchChoices(List<TrainingMatch> options,String playerIds,long next,long revision,boolean allowEstimates){
        if(options.isEmpty()) {message("Aucun match Booker avec historique LIVE admissible pour ces filtres.");return;}
        String[] entries=new String[options.size()];boolean[] chosen=new boolean[options.size()];
        for(int i=0;i<entries.length;i++){
            TrainingMatch m=options.get(i);
            entries[i]=m.match.optString("match_date","")+"  "+m.player1+" — "+m.player2+"\n"+m.tournament;
        }
        new AlertDialog.Builder(this).setTitle("Sélectionner les matchs · "+options.size()+" résultats")
            .setMultiChoiceItems(entries,chosen,(d,which,isChecked)->chosen[which]=isChecked)
            .setPositiveButton("VALIDER",(d,which)->{
                final ArrayList<TrainingMatch> requested=new ArrayList<>();
                for(int i=0;i<options.size();i++)if(chosen[i])requested.add(options.get(i));
                if(requested.isEmpty())return;
                if(revision!=filterRevision){message("Filtre modifié : relance la recherche.");return;}
                work("Contrôle des cotes de chaque match…",()->{
                    final ArrayList<TrainingMatch> accepted=new ArrayList<>();int refused=0;
                    for(TrainingMatch m:requested){
                        if(training.containsKey(m.id) || accepted.size()+training.size()>=MAX_TRAINING)break;
                        try {loadObservedHistory(m,allowEstimates);accepted.add(m);} catch(Exception rejected){refused++;}
                    }
                    final int rejectedCount=refused;
                    ui(()->{
                        if(revision!=filterRevision){message("Filtre modifié : résultats anciens ignorés.");return;}
                        int added=0;for(TrainingMatch m:accepted)if(addMatch(m))added++;
                        renderList();renderBanner();message(added+" ajouté(s), "+rejectedCount+" refusé(s) : cotes incomplètes.");});
                });
            })
            .setNeutralButton("PAGE SUIVANTE",(d,w)->{if(next>0)browseMatches(playerIds,next);})
            .setNegativeButton("FERMER",null).show();
    }
    private static String cleanFilter(String text){return text.replace("*"," ").replace("%"," ").replace(","," ").trim();}
    private void searchPlayer() {
        final String name=cleanFilter(playerSearch.getText().toString());
        if(name.isEmpty()){message("Saisis un nom de joueur.");return;}
        work("Recherche du joueur dans tpai.players…",()->{
            JSONArray people=SupabaseApi.list("players",SupabaseApi.args("select","player_id","name","ilike.*"+name+"*","limit","80"));
            StringBuilder ids=new StringBuilder();for(int i=0;i<people.length();i++){
                if(ids.length()>0)ids.append(',');ids.append(people.getJSONObject(i).optLong("player_id"));
            }
            String resolved=ids.toString();
            ui(()->{if(resolved.isEmpty()){message("Joueur introuvable.");return;}
                inProgress=false; browseMatches(resolved,-1);});
        });
    }
    private Map<Long,String> labels(String table,String idCol,Set<Long> ids)throws Exception {
        Map<Long,String> out=new HashMap<>();if(ids.isEmpty())return out;
        List<Long> a=new ArrayList<>(ids);
        for(int offset=0;offset<a.size();offset+=90){
            StringBuilder buf=new StringBuilder();for(int i=offset;i<Math.min(offset+90,a.size());i++){
                if(buf.length()>0)buf.append(',');buf.append(a.get(i));
            }
            JSONArray rows=SupabaseApi.list(table,SupabaseApi.args("select",idCol+",name",idCol,"in.("+buf+")","limit","100"));
            for(int i=0;i<rows.length();i++){
                JSONObject r=rows.getJSONObject(i);out.put(r.optLong(idCol),r.optString("name",""));
            }
        }return out;
    }
    private List<TrainingMatch> resolve(JSONArray rows,boolean allowEstimates)throws Exception {
        List<TrainingMatch> result=new ArrayList<>();Set<Long> people=new LinkedHashSet<>(), comps=new LinkedHashSet<>();
        for(int i=0;i<rows.length();i++){
            TrainingMatch m=new TrainingMatch(rows.getJSONObject(i));if(m.id<=0||!eligibleMetadata(m.match,allowEstimates))continue;
            people.add(m.match.optLong("player1_id"));people.add(m.match.optLong("player2_id"));
            comps.add(m.match.optLong("competition_id"));result.add(m);
        }
        people.remove(0L);comps.remove(0L);
        Map<Long,String> pn=labels("players","player_id",people);
        Map<Long,String> cn=labels("competitions","competition_id",comps);
        for(TrainingMatch m:result){
            m.player1=pn.getOrDefault(m.match.optLong("player1_id"),"Joueur #"+m.match.optLong("player1_id"));
            m.player2=pn.getOrDefault(m.match.optLong("player2_id"),"Joueur #"+m.match.optLong("player2_id"));
            m.tournament=cn.getOrDefault(m.match.optLong("competition_id"),"Compétition #"+m.match.optLong("competition_id"));
        }return result;
    }

    private void addRandom() {
        if(training.size()>=MAX_TRAINING){notice("Capacité atteinte","La liste est limitée à 10 matchs. Supprime d’abord des matchs.");return;}
        int needed=Math.min(randomCount,MAX_TRAINING-training.size());
        final boolean allowEstimates=includeEstimated;
        final long revision=filterRevision;
        work("Tirage aléatoire de "+needed+" matchs dans tpai…",()->{
            Map<String,String> base=SupabaseApi.args("select","match_id","order","match_id.asc","limit","1");
            JSONArray min=SupabaseApi.list("matches",base);
            base.put("order","match_id.desc");JSONArray max=SupabaseApi.list("matches",base);
            if(min.length()==0||max.length()==0)throw new Exception("Aucun match avec historique LIVE disponible.");
            long lower=min.getJSONObject(0).optLong("match_id"),upper=max.getJSONObject(0).optLong("match_id");
            ArrayList<TrainingMatch> accepted=new ArrayList<>();
            Set<Long> attempted=new HashSet<>();
            int timedOut=0;
            // Index match_id : lots courts ; jamais de tri global avec jointure de qualité.
            for(int attempt=0;attempt<90 && accepted.size()<needed;attempt++){
                long pivot=lower+(long)(random.nextDouble()*Math.max(1,upper-lower));
                Map<String,String> q=SupabaseApi.args("select",MATCH_FIELDS,
                    "match_id","gte."+pivot,"order","match_id.asc","limit","6");
                JSONArray rows;
                try {rows=SupabaseApi.list("matches",q);}
                catch(Exception problem){
                    if(problem.getMessage()!=null && problem.getMessage().contains("timeout") && timedOut++<5)continue;
                    throw problem;
                }
                JSONArray eligibles=new JSONArray();
                for(int k=0;k<rows.length();k++){
                    JSONObject r=rows.getJSONObject(k);long id=r.optLong("match_id",-1);
                    if(id>0&&!attempted.contains(id)&&!training.containsKey(id)&&eligibleMetadata(r,allowEstimates)){
                        eligibles.put(r);attempted.add(id);
                    }
                }
                if(eligibles.length()==0)continue;
                List<TrainingMatch> candidates=resolve(eligibles,allowEstimates);
                for(TrainingMatch m:candidates){
                    if(accepted.size()>=needed)break;
                    try{loadObservedHistory(m,allowEstimates);accepted.add(m);}
                    catch(Exception rejected){/* historique incomplet : écarté */}
                }
            }
            ui(()->{
                if(revision!=filterRevision){message("Filtre modifié : tirage précédent ignoré.");return;}
                int count=0;for(TrainingMatch m:accepted)if(addMatch(m))count++;
                renderList();renderBanner();message(count+" match(s) aux cotes LIVE vérifiées ajoutés "+
                    (allowEstimates?"(estimations autorisées).":"(sans estimation)."));});
        });
    }
    private boolean addMatch(TrainingMatch m) {
        if(m.id<0||training.containsKey(m.id)||training.size()>=MAX_TRAINING||!m.loaded||!m.complete||!eligibleMetadata(m.match))return false;
        training.put(m.id,m);
        // Sélection uniquement par appui explicite sur une carte.
        saveSelection();
        // Comme sur le PC, les simulations démarrent sans cocher les cases.
        // Ces cases servent uniquement à désigner les matchs pilotés par les commandes.
        // Le téléchargement est déclenché après ajout, de manière séquentielle.
        m.mode="play";
        return true;
    }
    // Seuls les identifiants/favoris sont memorises dans les preferences Android.
    // Les donnees des matchs et des cotes sont RELUES depuis PostgreSQL a chaque lancement.
    private void saveSelection() {
        if(preferences==null)return;
        JSONArray saved=new JSONArray();
        for(TrainingMatch m:training.values()){
            JSONObject o=new JSONObject();
            try{o.put("id",m.id);o.put("favorite",m.favorite);o.put("checked",m.checked);}catch(Exception ignored){}
            saved.put(o);
        }
        preferences.edit().putString("ids",saved.toString()).putLong("active",activeId).apply();
    }
    private void restoreSelection(){
        if(preferences==null)return;
        final boolean allowEstimates=includeEstimated;
        final long revision=filterRevision;
        JSONArray stored;
        try{stored=new JSONArray(preferences.getString("ids","[]"));}catch(Exception e){return;}
        if(stored.length()==0){addRandom();return;}
        final long previouslyActive=preferences.getLong("active",-1);
        work("Restauration des matchs depuis le serveur…",()->{
            StringBuilder ids=new StringBuilder();
            Map<Long,JSONObject> states=new HashMap<>();
            for(int i=0;i<Math.min(stored.length(),MAX_TRAINING);i++){
                JSONObject state=stored.optJSONObject(i);if(state==null)continue;
                long id=state.optLong("id",0);if(id<=0)continue;
                states.put(id,state);if(ids.length()>0)ids.append(',');ids.append(id);
            }
            if(ids.length()==0)return;
            JSONArray rows=SupabaseApi.list("matches",SupabaseApi.args("select",MATCH_FIELDS,
                "match_id","in.("+ids+")","limit","10"));
            List<TrainingMatch> candidates=resolve(rows,allowEstimates);
            List<TrainingMatch> restored=new ArrayList<>();
            for(TrainingMatch m:candidates){try{loadObservedHistory(m,allowEstimates);restored.add(m);}catch(Exception ignored){/* ancien favori écarté */}}
            ui(()->{
                if(revision!=filterRevision){message("Filtre modifié : restauration précédente ignorée.");return;}
                for(TrainingMatch m:restored){
                    JSONObject state=states.get(m.id);if(state==null)continue;
                    m.favorite=state.optBoolean("favorite",false);
                    m.checked=state.optBoolean("checked",true);
                    addMatch(m);
                }
                activeId=-1; // À chaque ouverture, aucune sélection implicite.
                renderList();renderBanner();message("Sélection restaurée depuis les matchs distants.");
                if(training.isEmpty())timer.postDelayed(()->addRandom(),100);
            });
        });
    }

    private void clearTraining(){
        if(training.isEmpty())return;
        new AlertDialog.Builder(this).setTitle("Vider les matchs d'entraînement")
            .setMessage("Supprimer tous les matchs non favoris de la liste ? Les matchs resteront dans PostgreSQL.")
            .setPositiveButton("VIDER",(d,w)->{
                int protectedCount=0;
                List<Long> removal=new ArrayList<>();for(TrainingMatch m:training.values())if(!m.favorite)removal.add(m.id);else protectedCount++;
                for(long id:removal)training.remove(id);
                if(!training.containsKey(activeId))activeId=training.isEmpty()?-1:training.values().iterator().next().id;
                saveSelection();renderList();renderBanner();message(removal.size()+" retiré(s). Favoris protégés : "+protectedCount);
            }).setNegativeButton("ANNULER",null).show();
    }
    private void deleteChecked() {
        List<Long> selection=new ArrayList<>();for(TrainingMatch m:training.values())if(m.checked)selection.add(m.id);
        if(selection.isEmpty()){message("Aucun match coché.");return;}
        new AlertDialog.Builder(this).setTitle("Supprimer "+selection.size()+" match(s) ?")
            .setMessage("Les matchs favoris ne seront pas supprimés. Seule la liste affichée est modifiée.")
            .setPositiveButton("SUPPRIMER",(d,w)->{
                for(long id:selection){TrainingMatch m=training.get(id);if(m!=null&&!m.favorite)training.remove(id);}
                if(!training.containsKey(activeId))activeId=training.isEmpty()?-1:training.values().iterator().next().id;
                saveSelection();renderList();renderBanner();
            }).setNegativeButton("ANNULER",null).show();
    }
    private void notice(String title,String msg){new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK",null).show();}
    private String odd(double value) {return Double.isFinite(value)&&value>0?String.format(Locale.FRANCE,"%.2f",value):"—";}
    private String odds(JSONObject src,String key){return src!=null&&!src.isNull(key)?odd(src.optDouble(key,Double.NaN)):"—";}
    private String score(JSONObject e,String p1,String p2) {
        if(e==null)return "0 - 0";
        return e.optString(p1,"0")+" - "+e.optString(p2,"0");
    }
    private int favoriteColor(TrainingMatch m,boolean first){
        double a=m.match.optDouble("avm_odd1",0),b=m.match.optDouble("avm_odd2",0);
        if(a<=0||b<=0||a==b)return Color.WHITE;
        return (first==(a<b))?BLUE:PINK;
    }
    private String currentOdds(TrainingMatch m,boolean first){
        JSONObject state=m.showResult?m.finalState():m.current();
        String key=first?"odd1":"odd2";
        if(state==null||!validOdd(state,key))return "—";
        if(state.optBoolean("is_estimated",false)&&!includeEstimated)return "—";
        // * signale sans ambiguïté une cote estimée par Booker, jamais par Android.
        return odds(state,key)+(state.optBoolean("is_estimated",false)?"*":"");
    }
    // V1.28 : lignes de scores compactes inspirees du tableau du client PC V845.
    // Les colonnes AVM, LIVE, sets, jeux et points sont alignees entre J1 et J2.
    private TextView gridCell(String value, int size, int color, boolean bold, int gravity) {
        TextView t=tv(value,size,color,bold);
        t.setGravity(gravity);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }
    private void gridWidth(LinearLayout parent, View v, int px) {
        parent.addView(v,new LinearLayout.LayoutParams(dp(px),dp(21)));
    }
    private void gridWeight(LinearLayout parent, View v) {
        parent.addView(v,new LinearLayout.LayoutParams(0,dp(21),1));
    }
    private String gridNum(JSONObject state,String key) {
        if(state==null)return "–";
        String value=state.optString(key,"").trim();
        return value.length()==0 || value.equals("null")?"–":value;
    }
    /** Live_states peut contenir un total de points cumulés. Convertit les deltas du jeu actif
     *  en tennis 0/15/30/40/AV, sans traiter 127 comme un score de jeu.
     *  Si la source est ambiguë, afficher un tiret plutôt qu'un score inventé.
     */
    private String tennisPoints(TrainingMatch m,JSONObject e){
        return tennisPointForSide(m,e,true)+" - "+tennisPointForSide(m,e,false);
    }
    private String tennisPointForSide(TrainingMatch m,JSONObject e,boolean first){
        if(e==null)return "—";
        String key=first?"points_p1":"points_p2";
        String raw=e.optString(key,"").trim().toUpperCase(Locale.ROOT);
        if(raw.equals("A")||raw.equals("AD")||raw.equals("40")||raw.equals("30")||raw.equals("15")||raw.equals("0"))return raw.equals("AD")?"A":raw;
        int ix=m.states.indexOf(e);
        if(ix<0)return "—";
        // Les cumuls de points nécessitent la référence du DEBUT du jeu courant.
        JSONObject cur=m.states.get(ix);
        int cp1=cur.optInt("points_p1",-1),cp2=cur.optInt("points_p2",-1);
        if(cp1<0||cp2<0)return "—";
        int start=ix;
        while(start>0){
            JSONObject prev=m.states.get(start-1);
            if(prev.optInt("games_p1",-1)!=cur.optInt("games_p1",-1)
                ||prev.optInt("games_p2",-1)!=cur.optInt("games_p2",-1)
                ||prev.optInt("sets_p1",-1)!=cur.optInt("sets_p1",-1)
                ||prev.optInt("sets_p2",-1)!=cur.optInt("sets_p2",-1))break;
            start--;
        }
        int baseline=Math.max(0,start-1);
        JSONObject base=m.states.get(baseline);
        int a=cp1-base.optInt("points_p1",cp1),b=cp2-base.optInt("points_p2",cp2);
        if(a<0||b<0)return "—";
        // Sans échantillon précédent le premier état enregistré, pas de score certain.
        if(start==0)return "—";
        boolean tie=cur.optInt("games_p1",0)>=6 && cur.optInt("games_p2",0)>=6;
        if(tie)return String.valueOf(first?a:b);
        if(a<=2&&b<=2)return new String[]{"0","15","30"}[first?a:b];
        if(a>=3&&b<=2)return first?"40":new String[]{"0","15","30"}[b];
        if(b>=3&&a<=2)return first?new String[]{"0","15","30"}[a]:"40";
        if(a==b)return "40";
        if(a==b+1)return first?"A":"40";
        if(b==a+1)return first?"40":"A";
        return "—";
    }
    private void renderList(){
        if(listHost==null)return;
        final int originalScroll=listScroll==null?0:listScroll.getScrollY();
        listHost.removeAllViews();
        counter.setText(training.size()+" / "+MAX_TRAINING+" matchs");
        if(training.isEmpty()){
            TextView empty=tv(inProgress?"Recherche de quatre matchs complets en cours…":"Aucun match admissible trouvé. Utilise ＋ pour relancer le tirage.",13,MUTED,false);
            empty.setGravity(Gravity.CENTER);margin(empty,listHost,-1,115,12,15,12,0);return;
        }
        String lastTournament="";
        for(TrainingMatch m:training.values()){
            if(!m.tournament.equals(lastTournament)){
                lastTournament=m.tournament;
                String title=m.tournament;
                if(title==null||title.trim().isEmpty()||title.equalsIgnoreCase("null"))title="Tournoi non renseigné";
                String year=m.match.optString("match_date","");
                if(year.length()>=4)title+="  |  "+year.substring(0,4);
                TextView header=tv("▰  "+title,11,0xffecf3f7,true);
                header.setSingleLine(true);header.setEllipsize(android.text.TextUtils.TruncateAt.END);
                header.setBackgroundColor(0xff343c43);header.setPadding(dp(7),dp(3),dp(6),dp(3));
                margin(header,listHost,-1,24,0,4,0,0);
            }
            final JSONObject state=m.showResult?m.finalState():m.current();
            LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(dp(1),dp(3),dp(1),dp(3));
            line.setBackgroundColor(m.id==activeId?0xff18392e:0xff0d1b27);
            // Tableau PC : UNE seule grille horizontale, deux lignes de joueurs.
            // Les cotes, les sets et les points sont alignes sur chaque joueur.
            LinearLayout leading=col();leading.setGravity(Gravity.CENTER);
            TextView star=tv(m.favorite?"★":"☆",16,m.favorite?0xfff2c94c:0xff7b90a1,false);
            star.setGravity(Gravity.CENTER);star.setContentDescription("Favori");
            star.setOnClickListener(v->{m.favorite=!m.favorite;saveSelection();renderList();});
            leading.addView(star,new LinearLayout.LayoutParams(dp(25),dp(28)));
            CheckBox check=new CheckBox(this);check.setButtonTintList(ColorStateList.valueOf(GREEN));check.setChecked(m.checked);
            check.setPadding(0,0,0,0);check.setMinWidth(0);check.setMinimumWidth(0);
            check.setOnCheckedChangeListener((v,checked)->{m.checked=checked;saveSelection();updatePlaybackStatus();});
            leading.addView(check,new LinearLayout.LayoutParams(dp(29),dp(28)));
            line.addView(leading,new LinearLayout.LayoutParams(dp(31),dp(57)));
            LinearLayout table=col();
            // En-tetes et valeurs partagent les memes tailles de colonnes.
            LinearLayout headings=row();
            gridWeight(headings,gridCell("",8,MUTED,false,Gravity.LEFT));
            gridWidth(headings,gridCell("",8,MUTED,false,Gravity.CENTER),34);
            gridWidth(headings,gridCell("",8,0xffffe267,false,Gravity.CENTER),38);
            gridWidth(headings,gridCell("",8,MUTED,false,Gravity.CENTER),10);
            gridWidth(headings,gridCell("S",8,MUTED,false,Gravity.CENTER),15);
            gridWidth(headings,gridCell("J",8,MUTED,false,Gravity.CENTER),15);
            gridWidth(headings,gridCell("PTS",8,MUTED,false,Gravity.CENTER),25);
            table.addView(headings,new LinearLayout.LayoutParams(-1,dp(17)));
            for(int side=0;side<2;side++){
                boolean first=side==0;
                LinearLayout r=row();
                String player=first?m.player1:m.player2;
                TextView name=gridCell(player,11,first?0xffe8f0f9:0xfff1f5fa,side==0,Gravity.LEFT|Gravity.CENTER_VERTICAL);
                name.setContentDescription(player);gridWeight(r,name);
                gridWidth(r,gridCell(odds(m.match,first?"avm_odd1":"avm_odd2"),10,0xffe4e7ec,false,Gravity.CENTER),34);
                gridWidth(r,gridCell(currentOdds(m,first),10,0xffffe267,true,Gravity.CENTER),38);
                String server=state==null?"":state.optString("server","");
                boolean serving=server.equalsIgnoreCase(first?"J1":"J2")||server.equals(first?"1":"2");
                gridWidth(r,gridCell(serving?"●":"",9,0xffefeb61,true,Gravity.CENTER),10);
                gridWidth(r,gridCell(gridNum(state,first?"sets_p1":"sets_p2"),10,Color.WHITE,true,Gravity.CENTER),15);
                gridWidth(r,gridCell(gridNum(state,first?"games_p1":"games_p2"),10,Color.WHITE,false,Gravity.CENTER),15);
                gridWidth(r,gridCell(tennisPointForSide(m,state,first),10,0xffffdc41,true,Gravity.CENTER),25);
                table.addView(r,new LinearLayout.LayoutParams(-1,dp(21)));
            }
            line.addView(table,new LinearLayout.LayoutParams(0,dp(60),1));
            line.setOnClickListener(v->{activeId=m.id;saveSelection();renderBanner();renderList();fetchStates(m,false);});
            margin(line,listHost,-1,65,0,1,0,0);
            View separator=new View(this);separator.setBackgroundColor(0xff25333f);
            listHost.addView(separator,new LinearLayout.LayoutParams(-1,dp(1)));
        }
        if(selectAll!=null){
            boolean everyone=!training.isEmpty();
            for(TrainingMatch m:training.values())if(!m.checked){everyone=false;break;}
            selectAll.setChecked(everyone);
        }
        updatePlaybackStatus();
        if(listScroll!=null)listScroll.post(()->listScroll.scrollTo(0,originalScroll));
    }
    private void renderBanner(){
        if(bannerHost==null)return;
        bannerHost.removeAllViews();
        TrainingMatch m=training.get(activeId);
        bannerHost.setVisibility(m==null?View.GONE:View.VISIBLE);
        if(transportHost!=null)transportHost.setVisibility(m==null?View.GONE:View.VISIBLE);
        if(m==null){
            TextView label=tv("AUCUN MATCH SÉLECTIONNÉ",13,MUTED,true);label.setGravity(Gravity.CENTER);
            margin(label,bannerHost,-1,64,4,1,4,0);
            return;
        }
        String date=m.match.optString("match_date","")+"  "+m.match.optString("match_time","");
        TextView comp=tv(m.tournament+"  •  "+date,10,MUTED,false);comp.setGravity(Gravity.CENTER);
        margin(comp,bannerHost,-1,23,4,3,4,1);
        JSONObject e=m.showResult?m.finalState():m.current();
        LinearLayout h=row();h.setPadding(dp(4),0,dp(4),0);
        LinearLayout a=playerBanner(m,true,e);LinearLayout b=playerBanner(m,false,e);
        h.addView(a,new LinearLayout.LayoutParams(0,dp(98),1));
        LinearLayout center=col();center.setGravity(Gravity.CENTER);
        TextView sets=tv(score(e,"sets_p1","sets_p2"),22,0xffe8e8e8,true);sets.setGravity(Gravity.CENTER);
        center.addView(sets);
        TextView games=tv("JEUX "+score(e,"games_p1","games_p2"),11,0xffbcd0d1,false);games.setGravity(Gravity.CENTER);
        center.addView(games);
        TextView points=tv(tennisPoints(m,e),17,0xffffd400,true);points.setGravity(Gravity.CENTER);
        center.addView(points);
        h.addView(center,new LinearLayout.LayoutParams(dp(104),dp(98)));
        h.addView(b,new LinearLayout.LayoutParams(0,dp(98),1));
        bannerHost.addView(h);
        if(e!=null && isBreakPoint(e)){
            TextView breakFlag=tv("BREAK  •  Balle de break",12,0xffff697d,true);
            breakFlag.setGravity(Gravity.CENTER);
            margin(breakFlag,bannerHost,-1,22,0,0,0,0);
        }
        // Curseur manuel : index réel des observations, sans reconstituer des points fictifs.
        if(m.loaded && m.states.size()>1 && !m.showResult){
            SeekBar seek=new SeekBar(this);
            seek.setMax(m.states.size()-1);seek.setProgress(Math.min(m.step,m.states.size()-1));
            seek.setProgressTintList(ColorStateList.valueOf(GREEN));
            seek.setThumbTintList(ColorStateList.valueOf(GREEN));
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                private int desired=m.step;
                @Override public void onStartTrackingTouch(SeekBar b) { desired=b.getProgress(); }
                @Override public void onProgressChanged(SeekBar b,int progress,boolean fromUser){
                    if(fromUser)desired=progress;
                }
                @Override public void onStopTrackingTouch(SeekBar b){
                    if(!training.containsKey(m.id))return;
                    m.step=Math.min(Math.max(0,desired),m.states.size()-1);
                    m.mode="pause";
                    tickHistory.remove(m.id);
                    renderBanner();renderList();
                }
            });
            margin(seek,bannerHost,-1,29,5,0,5,0);
        }
        TextView bottom=tv(e==null?"En attente de la chronologie LIVE…":
            (m.showResult?lastStateLabel(m).toUpperCase(Locale.ROOT):
            "SET "+(e.optInt("sets_p1")+e.optInt("sets_p2")+1)+"  •  ÉTAT "+(m.step+1)+" / "+m.states.size()+
            "  •  "+(e.optBoolean("is_estimated",false)?"* COTES BOOKER ESTIMÉES":"COTES BOOKER OBSERVÉES")),10,GREEN,false);
        bottom.setGravity(Gravity.CENTER);margin(bottom,bannerHost,-1,28,4,0,4,2);
    }
    private LinearLayout playerBanner(TrainingMatch m,boolean first,JSONObject e){
        LinearLayout out=col();out.setPadding(dp(3),dp(7),dp(3),dp(4));
        out.setBackground(bg(0xff242424,0xff3a3a3a,7));out.setGravity(Gravity.CENTER);
        TextView name=tv(first?m.player1:m.player2,13,favoriteColor(m,first),true);name.setGravity(Gravity.CENTER);
        // Portraits publics Supabase Storage, nom exact comme la version PC.
        ImageView photo=new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setContentDescription("Portrait de "+(first?m.player1:m.player2));
        out.addView(photo,new LinearLayout.LayoutParams(-1,dp(58)));
        loadPortrait(first?m.player1:m.player2,photo);
        out.addView(name,new LinearLayout.LayoutParams(-1,0,1));
        String server=e==null?"":(e.optInt("server",0)==(first?1:2)?"  🎾":"");
        TextView avm=tv(odds(m.match,first?"avm_odd1":"avm_odd2")+server,11,Color.WHITE,false);avm.setGravity(Gravity.CENTER);
        out.addView(avm);
        TextView live=tv(currentOdds(m,first),14,0xffdfff00,true);live.setGravity(Gravity.CENTER);
        out.addView(live);
        return out;
    }
    private void loadPortrait(String player,ImageView target){
        if(portraits.containsKey(player)){
            Bitmap cached=portraits.get(player);if(cached!=null)target.setImageBitmap(cached);return;
        }
        io.execute(()->{
            Bitmap bitmap=null;
            try{
                String file=player.replace("/","_").replace("\\","_").replaceAll("\\.+$","");
                String name=java.net.URLEncoder.encode(file.replaceAll("\\.$","" )+".png","UTF-8").replace("+","%20");
                URL url=new URL(SupabaseApi.ENDPOINT+"/storage/v1/object/public/images/joueurs/"+name);
                HttpURLConnection connection=(HttpURLConnection)url.openConnection();
                connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
                try(InputStream stream=connection.getInputStream()){
                    bitmap=BitmapFactory.decodeStream(stream);
                }finally{connection.disconnect();}
            }catch(Exception ignored){}
            final Bitmap finalBitmap=bitmap;
            ui(()->{portraits.put(player,finalBitmap);if(finalBitmap!=null)target.setImageBitmap(finalBitmap);});
        });
    }
    /** V1.21 : Balle de break déterminée uniquement depuis le score observé et le serveur. */
    private boolean isBreakPoint(JSONObject e){
        int service=e.optInt("server",0);
        if(service!=1&&service!=2)return false;
        String p1=e.optString("points_p1","").trim().toUpperCase(Locale.ROOT);
        String p2=e.optString("points_p2","").trim().toUpperCase(Locale.ROOT);
        String server=service==1?p1:p2,returner=service==1?p2:p1;
        if(returner.equals("40")&&(server.equals("0")||server.equals("15")||server.equals("30")))return true;
        return (returner.equals("A")||returner.equals("AD"))&&server.equals("40");
    }

    /** Affichage fidèle des jeux conservés dans tpai.p2p_games, sans extrapoler les points. */
    private void showGameDetails(TrainingMatch match){
        work("Lecture distante du détail des jeux…",()->{
            JSONArray games=SupabaseApi.list("p2p_games",SupabaseApi.args(
                "select","id,set_no,game_no,is_tiebreak,server,winner,points_count,decode_mode,confidence,lossy",
                "match_id","eq."+match.id,"order","set_no.asc,game_no.asc,id.asc","limit","150"));
            ui(()->displayGameDetails(match,games));
        });
    }
    private void displayGameDetails(TrainingMatch match,JSONArray games){
        ScrollView sc=new ScrollView(this);
        LinearLayout content=col();content.setBackgroundColor(0xff101820);
        content.setPadding(dp(11),dp(8),dp(11),dp(8));
        int lastSet=-1;
        for(int i=0;i<games.length();i++){
            JSONObject g=games.optJSONObject(i);if(g==null)continue;
            int set=g.optInt("set_no",0), game=g.optInt("game_no",0);
            if(set!=lastSet){
                lastSet=set;
                TextView title=tv("SET "+set,16,GREEN,true);
                margin(title,content,-1,32,1,8,1,3);
            }
            StringBuilder b=new StringBuilder("Jeu ").append(game);
            if(g.optBoolean("is_tiebreak",false))b.append("  •  TIE-BREAK");
            b.append("\nServeur : ").append(g.optString("server","—"));
            b.append("   •   Vainqueur : ").append(g.optString("winner","—"));
            b.append("\nPoints enregistrés : ").append(g.optInt("points_count",0));
            String mode=g.optString("decode_mode","");
            if(!mode.isEmpty())b.append("   •   Décodage : ").append(mode);
            if(g.optBoolean("lossy",false))b.append("\n⚠ Décodage potentiellement incomplet");
            TextView item=tv(b.toString(),12,Color.WHITE,false);
            item.setPadding(dp(9),dp(6),dp(9),dp(6));
            item.setBackground(bg(0xff20262c,0xff344954,6));
            margin(item,content,-1,-2,0,3,0,4);
        }
        if(games.length()==0){
            TextView no=tv("Aucun jeu décodé n'est disponible pour cette rencontre.",13,MUTED,false);
            margin(no,content,-1,-2,0,12,0,15);
        }
        TextView footer=tv("Jeux issus de tpai.p2p_games : "+games.length()+". Les points individuels ne sont pas exposés par l'API publique actuelle.",11,MUTED,false);
        margin(footer,content,-1,-2,0,12,0,10);
        sc.addView(content);
        new AlertDialog.Builder(this).setTitle(match.player1+" — "+match.player2)
            .setView(sc).setPositiveButton("FERMER",null).show();
    }
    private void updatePlaybackStatus(){
        if(playbackStatus==null)return;
        int count=0;for(TrainingMatch m:training.values())if(m.checked)count++;
        playbackStatus.setText(lastPlaybackMode+"  •  "+count+" sélectionné(s)  •  Rythme "+(pcRhythm?"PC":"rapide")+" ⌄");
    }
    private void playback(int action){
        boolean any=false;for(TrainingMatch m:training.values())if(m.checked)any=true;
        if(!any){message("Coche au moins un match pour piloter la simulation.");return;}
        String mode;int direction=0,speed=1;
        if(action==-1||action==3){
            direction=action==-1?-1:1;
            repeatedSpeed= repeatedDirection==direction?Math.min(repeatedSpeed*2,1024):2;
            repeatedDirection=direction;speed=repeatedSpeed;
            mode=direction<0?"rewind":"fast_forward";
            lastPlaybackMode=(direction<0?"Retour rapide":"Avance rapide")+" x"+speed;
        }else{
            repeatedDirection=0;repeatedSpeed=1;
            mode=action==0?"pause":action==2?"stop":"play";
            lastPlaybackMode=action==0?"Pause":action==2?"Stop":"Lecture normale";
        }
        for(TrainingMatch m:training.values())if(m.checked){
            m.mode=mode;m.speed=speed;
            if(mode.equals("stop")){m.step=0;m.showResult=false;}
            if(!mode.equals("pause"))tickHistory.remove(m.id);
            if(!mode.equals("pause")&&!mode.equals("stop"))fetchStates(m,false);
        }
        updatePlaybackStatus();renderList();
    }
    private final Runnable tick=new Runnable(){@Override public void run(){
        boolean changed=false;
        long now=android.os.SystemClock.uptimeMillis();
        for(TrainingMatch m:training.values()){
            if(!appVisible)break;
            if(!m.loaded||m.showResult||m.states.size()<2)continue;
            if(!(m.mode.equals("play")||m.mode.equals("rewind")||m.mode.equals("fast_forward")))continue;
            Long lastTick=tickHistory.get(m.id);
            long base=pcRhythm?transitionDelay(m):QUICK_STEP_MS;
            long variation= Math.abs((m.id*7919L + (long)m.step*104729L) % 19L) - 9L;
            long varied=Math.round(base*m.tempo*(1.0+variation/100.0));
            long interval=Math.max(130,varied/Math.max(1,m.speed));
            if(lastTick!=null&&now-lastTick<interval)continue;
            tickHistory.put(m.id,now);
            int delta=m.mode.equals("rewind")?-1:1;
            int next=Math.min(m.states.size()-1,Math.max(0,m.step+delta));
            if(next!=m.step){m.step=next;changed=true;}
            else {m.mode="pause";changed=true;}
        }
        if(changed){renderBanner();renderList();}
        if(!destroyed)timer.postDelayed(this,130);
    }};
    private final Map<Long,Long> tickHistory=new HashMap<>();
    private long transitionDelay(TrainingMatch m){
        if(m.states.size()<2)return PC_POINT_MS;
        int dir=m.mode.equals("rewind")?-1:1;
        int next=Math.max(0,Math.min(m.states.size()-1,m.step+dir));
        JSONObject first=m.states.get(m.step),second=m.states.get(next);
        if(first.optInt("sets_p1")!=second.optInt("sets_p1")||first.optInt("sets_p2")!=second.optInt("sets_p2"))return PC_SET_MS;
        if(first.optInt("games_p1")!=second.optInt("games_p1")||first.optInt("games_p2")!=second.optInt("games_p2"))return PC_GAME_MS;
        return PC_POINT_MS;
    }
    private void fetchStates(TrainingMatch m,boolean showFinal){
        if(m.loaded){if(showFinal){m.showResult=true;renderBanner();renderList();}return;}
        if(m.loadingStates)return;
        m.loadingStates=true;
        final boolean allowEstimates=includeEstimated;
        final long revision=filterRevision;
        io.execute(()->{
            try {
                loadObservedHistory(m,allowEstimates);
                ui(()->{
                    if(revision!=filterRevision)return;
                    m.loadingStates=false;
                    if(showFinal)m.showResult=true;
                    tickHistory.remove(m.id);
                    message("#"+m.id+" : "+m.states.size()+" états contrôlés, deux cotes LIVE valides à chacun.");
                    renderBanner();renderList();});
            }catch(Exception rejected){
                ui(()->{
                    if(revision!=filterRevision)return;
                    m.loadingStates=false;m.mode="pause";
                    training.remove(m.id);
                    if(activeId==m.id)activeId=training.isEmpty()?-1:training.values().iterator().next().id;
                    saveSelection();renderBanner();renderList();
                    message("Match #"+m.id+" exclu : "+rejected.getMessage());
                });
            }
        });
    }
    private String lastStateLabel(TrainingMatch m){
        String quality=m.match.optString("live_status","");
        return quality.startsWith("COMPLETE")?"Dernier état LIVE complet":"Dernier état LIVE enregistré (historique incomplet)";
    }
    private void showResult(TrainingMatch m){
        if(m.showResult){m.showResult=false;renderBanner();renderList();return;}
        if(!m.loaded){fetchStates(m,true);return;}
        if(!m.complete){notice("Résultat indisponible","L'historique n'est pas complet.");return;}
        m.showResult=true;renderBanner();renderList();
    }
}
