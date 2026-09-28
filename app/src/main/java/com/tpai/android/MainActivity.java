package com.tpai.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String SUPABASE_URL="https://fdqvjaqclnjztiodfucg.supabase.co";
    private static final String SUPABASE_PUBLISHABLE_KEY="sb_publishable_gEReYquGc4yUs1MSDeNztw_FQCpwVuw";
    private static final String SCHEMA="timported";
    private static final int MAX_MATCHES=10;
    private static final String PREF="tpai_training";
    private static final String FAVORITES="favorites";
    private static final String SESSION="session_ids";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Random random=new Random();
    private final List<Replay> replays=new ArrayList<Replay>();
    private LinearLayout list; private TextView counter; private ProgressBar progress; private ImageButton refresh;
    private boolean forceRefresh=false;

    static class MatchItem {
        String code="",player1="Joueur 1",player2="Joueur 2",opening1="",opening2="";
        List<JSONObject> states=new ArrayList<JSONObject>(), odds=new ArrayList<JSONObject>();
        int startIndex=0;
    }

    @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(buildUi());loadMatches();}
    @Override protected void onDestroy(){for(Replay r:replays)r.stop();super.onDestroy();}

    private View buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(11,14,17));root.setPadding(dp(10),dp(10),dp(10),dp(8));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView menu=text("☰",25,Color.WHITE,Typeface.NORMAL);menu.setGravity(Gravity.CENTER);top.addView(menu,new LinearLayout.LayoutParams(dp(46),dp(40)));
        TextView app=text("TPAI_Android  •  V1.17",13,Color.rgb(146,154,164),Typeface.BOLD);top.addView(app,new LinearLayout.LayoutParams(0,dp(40),1));root.addView(top);
        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("MATCHS D’ENTRAÎNEMENT",20,Color.WHITE,Typeface.BOLD);titleRow.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        counter=text("0 match",12,Color.rgb(174,239,208),Typeface.BOLD);counter.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);titleRow.addView(counter,new LinearLayout.LayoutParams(dp(72),dp(42)));
        refresh=new ImageButton(this);refresh.setImageResource(android.R.drawable.ic_popup_sync);refresh.setColorFilter(Color.rgb(174,239,208));refresh.setBackgroundColor(Color.TRANSPARENT);refresh.setContentDescription("Actualiser");refresh.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){confirmRefresh();}});titleRow.addView(refresh,new LinearLayout.LayoutParams(dp(42),dp(42)));root.addView(titleRow);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));return root;
    }

    private void confirmRefresh(){new AlertDialog.Builder(this).setTitle("Actualiser les matchs ?").setMessage("Tous les matchs actuellement affichés seront remplacés par de nouveaux matchs aléatoires, sauf les matchs marqués comme favoris ★.").setNegativeButton("ANNULER",null).setPositiveButton("ACTUALISER",new DialogInterface.OnClickListener(){@Override public void onClick(DialogInterface d,int w){forceRefresh=true;loadMatches();}}).show();}

    private void loadMatches(){refresh.setEnabled(false);progress.setVisibility(View.VISIBLE);for(Replay r:replays)r.stop();replays.clear();new Thread(new Runnable(){@Override public void run(){try{List<MatchItem> candidates=fetchCandidates();final List<MatchItem> chosen=forceRefresh?refreshSelection(candidates):restoreOrChoose(candidates);forceRefresh=false;main.post(new Runnable(){@Override public void run(){render(chosen);}});}catch(final Exception e){main.post(new Runnable(){@Override public void run(){progress.setVisibility(View.GONE);refresh.setEnabled(true);counter.setText("0 match");list.removeAllViews();list.addView(text("Chargement impossible : "+e.getMessage(),12,Color.rgb(255,120,120),Typeface.NORMAL));}});}}},"TPAI-training").start();}

    private List<MatchItem> fetchCandidates() throws Exception {
        JSONArray ms=getJson("/rest/v1/matches?select="+enc("match_id,player1_id,player2_id,opening_odds_player1,opening_odds_player2")+"&has_live_odds=eq.true&order=match_date.desc&limit=250");
        List<MatchItem> out=new ArrayList<MatchItem>();
        for(int i=0;i<ms.length();i++){JSONObject row=ms.getJSONObject(i);long id=row.optLong("match_id",-1);if(id<0)continue;MatchItem m=new MatchItem();m.code=String.valueOf(id);m.player1=compactName(playerName(row.optLong("player1_id",-1),"Joueur 1"));m.player2=compactName(playerName(row.optLong("player2_id",-1),"Joueur 2"));m.opening1=clean(row.optString("opening_odds_player1",""));m.opening2=clean(row.optString("opening_odds_player2",""));m.states=states(id);m.odds=odds(id);if(validateChronology(m.states,m.odds)){out.add(m);if(out.size()>=60)break;}}
        return out;
    }

    private List<JSONObject> states(long id)throws Exception{JSONArray a=getJson("/rest/v1/match_states?select=*&match_id=eq."+id+"&order=state_number.asc&limit=5000");List<JSONObject> r=new ArrayList<JSONObject>();for(int i=0;i<a.length();i++)r.add(a.getJSONObject(i));return r;}
    private List<JSONObject> odds(long id)throws Exception{String[][] cs={{"odds_player1","odds_player2"},{"player1_odds","player2_odds"},{"odds_p1","odds_p2"},{"price_player1","price_player2"},{"home_odds","away_odds"},{"odd_player1","odd_player2"}};Exception last=null;for(int k=0;k<cs.length;k++)try{JSONArray a=getJson("/rest/v1/live_odds?select="+enc("state_number,"+cs[k][0]+","+cs[k][1])+"&match_id=eq."+id+"&order=state_number.asc&limit=5000");List<JSONObject> r=new ArrayList<JSONObject>();for(int i=0;i<a.length();i++){JSONObject x=new JSONObject(a.getJSONObject(i).toString());x.put("_o1",clean(x.optString(cs[k][0],"")));x.put("_o2",clean(x.optString(cs[k][1],"")));if(realOdd(x.optString("_o1"))&&realOdd(x.optString("_o2")))r.add(x);}return r;}catch(Exception e){last=e;}if(last!=null)throw last;return new ArrayList<JSONObject>();}

    private boolean validateChronology(List<JSONObject> s,List<JSONObject> o){if(s.size()<8||o.size()<2)return false;Map<Integer,JSONObject> om=new HashMap<Integer,JSONObject>();for(JSONObject x:o)om.put(Integer.valueOf(x.optInt("state_number",-1)),x);int prev=-1;for(int i=0;i<s.size();i++){JSONObject cur=s.get(i);int n=cur.optInt("state_number",-1);if(n<0||n<=prev)return false;prev=n;if(i>0&&scoreChanged(s.get(i-1),cur)&&!om.containsKey(Integer.valueOf(n)))return false;if(i>0&&impossibleSameGameTransition(s.get(i-1),cur))return false;}return true;}
    private boolean scoreChanged(JSONObject a,JSONObject b){String[] k={"sets_score","set_score","sets_won","score_sets","games_score","game_score","score_games","current_set_score","points","points_score","point_score","score_points"};for(String x:k)if(!clean(a.optString(x,"")).equals(clean(b.optString(x,""))))return true;return false;}
    private boolean impossibleSameGameTransition(JSONObject a,JSONObject b){String[] ga=pair(a,new String[]{"games_score","game_score","score_games","current_set_score"},new String[]{"games_player1","game_player1","games1","player1_games"},new String[]{"games_player2","game_player2","games2","player2_games"});String[] gb=pair(b,new String[]{"games_score","game_score","score_games","current_set_score"},new String[]{"games_player1","game_player1","games1","player1_games"},new String[]{"games_player2","game_player2","games2","player2_games"});if(!ga[0].equals(gb[0])||!ga[1].equals(gb[1]))return false;String[] pa=points(a),pb=points(b);boolean c1=!pa[0].equals(pb[0]),c2=!pa[1].equals(pb[1]);return c1&&c2;}

    private List<MatchItem> restoreOrChoose(List<MatchItem> c){String raw=prefs().getString(SESSION,"");if(raw.length()>0){List<MatchItem> r=new ArrayList<MatchItem>();String[] ids=raw.split(",");for(String id:ids)for(MatchItem m:c)if(m.code.equals(id)){m.startIndex=storedStart(m.code);r.add(m);break;}if(!r.isEmpty())return r;}return randomChoose(c,new HashSet<String>());}
    private List<MatchItem> refreshSelection(List<MatchItem> c){Set<String> fav=favorites();List<MatchItem> r=new ArrayList<MatchItem>();for(MatchItem m:c)if(fav.contains(m.code)){m.startIndex=storedStart(m.code);r.add(m);}Set<String> excluded=new HashSet<String>();for(MatchItem m:r)excluded.add(m.code);List<MatchItem> fresh=randomChoose(c,excluded);for(MatchItem m:fresh)if(r.size()<MAX_MATCHES)r.add(m);return r;}
    private List<MatchItem> randomChoose(List<MatchItem> c,Set<String> excluded){List<MatchItem> x=new ArrayList<MatchItem>();for(MatchItem m:c)if(!excluded.contains(m.code))x.add(m);Collections.shuffle(x);List<MatchItem> r=new ArrayList<MatchItem>();for(MatchItem m:x){if(r.size()>=MAX_MATCHES)break;m.startIndex=Math.max(0,Math.min(m.states.size()-2,random.nextInt(Math.max(1,m.states.size()-1))));r.add(m);}return r;}

    private void render(List<MatchItem> items){list.removeAllViews();counter.setText(items.size()+(items.size()>1?" matchs":" match"));progress.setVisibility(View.GONE);refresh.setEnabled(true);saveSession(items);for(MatchItem m:items)list.addView(matchRow(m));}
    private View matchRow(final MatchItem m){LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(3),dp(3),dp(3),dp(3));row.setBackgroundColor(Color.rgb(27,31,35));
        LinearLayout left=new LinearLayout(this);left.setGravity(Gravity.CENTER_VERTICAL);final TextView star=text(isFavorite(m.code)?"★":"☆",22,Color.rgb(255,214,64),Typeface.NORMAL);star.setGravity(Gravity.CENTER);star.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){star.setText(toggleFavorite(m.code)?"★":"☆");}});left.addView(star,new LinearLayout.LayoutParams(dp(34),dp(54)));LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.setPadding(dp(8),0,0,0);names.addView(text(m.player1,13,Color.WHITE,Typeface.BOLD),new LinearLayout.LayoutParams(-1,dp(27)));names.addView(text(m.player2,13,Color.WHITE,Typeface.BOLD),new LinearLayout.LayoutParams(-1,dp(27)));left.addView(names,new LinearLayout.LayoutParams(0,dp(54),1));row.addView(left,new LinearLayout.LayoutParams(0,dp(54),1));
        LinearLayout right=new LinearLayout(this);right.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);final TextView br1=cell("BREAK",42,8,Color.rgb(255,90,90)),br2=cell("BREAK",42,8,Color.rgb(255,90,90));br1.setVisibility(View.INVISIBLE);br2.setVisibility(View.INVISIBLE);right.addView(column(br1,br2,42));final ImageView ball1=ball(),ball2=ball();ball1.setVisibility(View.INVISIBLE);ball2.setVisibility(View.INVISIBLE);right.addView(column(ball1,ball2,22));final TextView sw1=cell("0",20,14,Color.rgb(255,214,64)),sw2=cell("0",20,14,Color.rgb(255,214,64));right.addView(column(sw1,sw2,20));final TextView gs1=cell("0",88,12,Color.WHITE),gs2=cell("0",88,12,Color.WHITE);right.addView(column(gs1,gs2,88));final TextView pt1=cell("0",30,14,Color.rgb(174,239,208)),pt2=cell("0",30,14,Color.rgb(174,239,208));right.addView(column(pt1,pt2,30));final TextView od1=cell("—",86,11,Color.rgb(255,214,64)),od2=cell("—",86,11,Color.rgb(255,214,64));right.addView(column(od1,od2,86));row.addView(right,new LinearLayout.LayoutParams(-2,dp(54)));wrap.addView(row,new LinearLayout.LayoutParams(-1,dp(60)));View sep=new View(this);sep.setBackgroundColor(Color.rgb(48,54,60));wrap.addView(sep,new LinearLayout.LayoutParams(-1,dp(1)));Replay rp=new Replay(m,ball1,ball2,br1,br2,sw1,sw2,gs1,gs2,pt1,pt2,od1,od2);replays.add(rp);rp.start();return wrap;}

    class Replay implements Runnable {MatchItem m;ImageView b1,b2;TextView br1,br2,sw1,sw2,gs1,gs2,pt1,pt2,od1,od2;int idx;boolean stopped=false;long base;
        Replay(MatchItem x,ImageView a,ImageView b,TextView c,TextView d,TextView e,TextView f,TextView g,TextView h,TextView i,TextView j,TextView k,TextView l){m=x;b1=a;b2=b;br1=c;br2=d;sw1=e;sw2=f;gs1=g;gs2=h;pt1=i;pt2=j;od1=k;od2=l;idx=Math.min(x.startIndex,x.states.size()-1);base=15000+random.nextInt(15001);}
        void start(){main.post(this);}void stop(){stopped=true;main.removeCallbacks(this);}
        @Override public void run(){if(stopped||idx>=m.states.size())return;JSONObject s=m.states.get(idx);String[] sets=sets(s),games=games(s),pts=points(s);sw1.setText(sets[0]);sw2.setText(sets[1]);String hist1=setHistory(s,1),hist2=setHistory(s,2);gs1.setText(hist1+(hist1.length()>0?" | ":"")+games[0]);gs2.setText(hist2+(hist2.length()>0?" | ":"")+games[1]);pt1.setText(pts[0]);pt2.setText(pts[1]);int server=server(s);b1.setVisibility(server==1?View.VISIBLE:View.INVISIBLE);b2.setVisibility(server==2?View.VISIBLE:View.INVISIBLE);boolean bp=isBreakPoint(pts,server);showBreak(server==1?br1:br2,server==1?br2:br1,bp);JSONObject o=oddFor(m.odds,s.optInt("state_number",-1));if(o!=null){od1.setText(oddLabel(o.optString("_o1",""),m.opening1));od2.setText(oddLabel(o.optString("_o2",""),m.opening2));}prefs().edit().putInt("start_"+m.code,idx).apply();long delay=base+random.nextInt(7001)-3500;if(idx+1<m.states.size()){JSONObject n=m.states.get(idx+1);if(!sets(n)[0].equals(sets[0])||!sets(n)[1].equals(sets[1]))delay+=18000+random.nextInt(10001);else if(!games(n)[0].equals(games[0])||!games(n)[1].equals(games[1]))delay+=9000+random.nextInt(7001);}idx++;main.postDelayed(this,Math.max(9000,delay));}
    }

    private void showBreak(TextView active,TextView other,boolean yes){other.clearAnimation();other.setVisibility(View.INVISIBLE);if(!yes){active.clearAnimation();active.setVisibility(View.INVISIBLE);return;}active.setVisibility(View.VISIBLE);if(active.getAnimation()==null){AlphaAnimation a=new AlphaAnimation(1f,.15f);a.setDuration(450);a.setRepeatMode(Animation.REVERSE);a.setRepeatCount(Animation.INFINITE);active.startAnimation(a);}}
    private boolean isBreakPoint(String[] p,int server){if(server==0)return false;int a=pointRank(p[0]),b=pointRank(p[1]);return server==1?b>=3&&b>a:a>=3&&a>b;}
    private int pointRank(String x){x=clean(x).toUpperCase(Locale.US);if("0".equals(x))return 0;if("15".equals(x))return 1;if("30".equals(x))return 2;if("40".equals(x))return 3;if(x.startsWith("A"))return 4;try{return Integer.parseInt(x);}catch(Exception e){return 0;}}
    private int server(JSONObject s){String[] keys={"server","serving_player","server_player","server_no","service_player"};for(String k:keys){String v=clean(s.optString(k,""));if("1".equals(v)||v.endsWith("1"))return 1;if("2".equals(v)||v.endsWith("2"))return 2;}return 0;}
    private JSONObject oddFor(List<JSONObject> os,int n){for(JSONObject o:os)if(o.optInt("state_number",-2)==n)return o;return null;}
    private String[] sets(JSONObject s){return pair(s,new String[]{"sets_score","set_score","sets_won","score_sets"},new String[]{"sets_player1","set_player1","sets1","player1_sets"},new String[]{"sets_player2","set_player2","sets2","player2_sets"});}
    private String[] games(JSONObject s){return pair(s,new String[]{"games_score","game_score","score_games","current_set_score"},new String[]{"games_player1","game_player1","games1","player1_games"},new String[]{"games_player2","game_player2","games2","player2_games"});}
    private String[] points(JSONObject s){return pair(s,new String[]{"points","points_score","point_score","score_points"},new String[]{"points_player1","point_player1","points1","player1_points"},new String[]{"points_player2","point_player2","points2","player2_points"});}
    private String[] pair(JSONObject s,String[] combined,String[] a,String[] b){for(String k:combined){String v=clean(s.optString(k,""));String[] z=v.split("[-: /]");if(z.length>=2)return new String[]{z[0],z[1]};}String x="",y="";for(String k:a){x=clean(s.optString(k,""));if(x.length()>0)break;}for(String k:b){y=clean(s.optString(k,""));if(y.length()>0)break;}return new String[]{x.length()>0?x:"0",y.length()>0?y:"0"};}
    private String setHistory(JSONObject s,int player){StringBuilder b=new StringBuilder();for(int n=1;n<=5;n++){String[] keys=player==1?new String[]{"set"+n+"_player1","set_"+n+"_player1","set"+n+"_p1","player1_set"+n}:new String[]{"set"+n+"_player2","set_"+n+"_player2","set"+n+"_p2","player2_set"+n};String v="";for(String k:keys){v=clean(s.optString(k,""));if(v.length()>0)break;}if(v.length()>0){if(b.length()>0)b.append(' ');b.append(v);}}return b.toString();}

    private View column(View a,View b,int w){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.addView(a,new LinearLayout.LayoutParams(dp(w),dp(27)));c.addView(b,new LinearLayout.LayoutParams(dp(w),dp(27)));return c;}
    private TextView cell(String s,int w,int sp,int color){TextView v=text(s,sp,color,Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setSingleLine(true);return v;}
    private ImageView ball(){ImageView v=new ImageView(this);v.setImageResource(R.drawable.tennis_ball);v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);return v;}
    private static String oddLabel(String live,String opening){return formatOdd(live)+" ("+(realOdd(opening)?formatOdd(opening):"—")+")";}
    private static String compactName(String n){n=clean(n);String[] p=n.split("\\s+");return p.length>=2?p[0]+" "+p[1].substring(0,1)+".":n;}

    private SharedPreferences prefs(){return getSharedPreferences(PREF,MODE_PRIVATE);}private Set<String> favorites(){return new HashSet<String>(prefs().getStringSet(FAVORITES,new HashSet<String>()));}private boolean isFavorite(String id){return favorites().contains(id);}private boolean toggleFavorite(String id){Set<String> s=favorites();boolean on;if(s.contains(id)){s.remove(id);on=false;}else{s.add(id);on=true;}prefs().edit().putStringSet(FAVORITES,s).apply();return on;}private int storedStart(String id){return prefs().getInt("start_"+id,0);}private void saveSession(List<MatchItem> x){StringBuilder b=new StringBuilder();for(MatchItem m:x){if(b.length()>0)b.append(',');b.append(m.code);}prefs().edit().putString(SESSION,b.toString()).apply();}

    private String playerName(long id,String fallback)throws Exception{if(id<0)return fallback;String canonical="",tid="";try{JSONArray a=getJson("/rest/v1/players?select="+enc("player_name,trefik_player_id")+"&player_id=eq."+id+"&limit=1");if(a.length()>0){JSONObject p=a.getJSONObject(0);canonical=clean(p.optString("player_name",""));tid=clean(p.optString("trefik_player_id",""));if(realName(canonical))return canonical;}}catch(Exception ignored){}try{JSONArray a=getJson("/rest/v1/player_sources?select="+enc("source_player_name")+"&player_id=eq."+id+"&limit=50");for(int i=0;i<a.length();i++){String n=clean(a.getJSONObject(i).optString("source_player_name",""));if(realName(n))return n;}}catch(Exception ignored){}if(!tid.isEmpty())try{JSONArray a=getJson("/rest/v1/player_sources?select="+enc("source_player_name")+"&source_player_id=eq."+enc(tid)+"&limit=50");for(int i=0;i<a.length();i++){String n=clean(a.getJSONObject(i).optString("source_player_name",""));if(realName(n))return n;}}catch(Exception ignored){}return canonical.length()>0?canonical:fallback+" #"+id;}
    private boolean realName(String s){String n=clean(s).toLowerCase(Locale.US);return n.length()>1&&!n.startsWith("trefík player #")&&!n.startsWith("trefik player #")&&!n.startsWith("trefík joueur inconnu")&&!n.startsWith("trefik joueur inconnu")&&!n.startsWith("joueur 1")&&!n.startsWith("joueur 2");}
    private JSONArray getJson(String path)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(SUPABASE_URL+path).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestProperty("apikey",SUPABASE_PUBLISHABLE_KEY);c.setRequestProperty("Authorization","Bearer "+SUPABASE_PUBLISHABLE_KEY);c.setRequestProperty("Accept-Profile",SCHEMA);c.setRequestProperty("Accept","application/json");int code=c.getResponseCode();BufferedReader r=new BufferedReader(new InputStreamReader(code>=200&&code<300?c.getInputStream():c.getErrorStream(),Charset.forName("UTF-8")));StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line);r.close();c.disconnect();if(code<200||code>=300)throw new Exception("HTTP "+code+" — "+b);return new JSONArray(b.toString());}
    private static boolean realOdd(String s){try{return Double.parseDouble(clean(s).replace(',','.'))>1.0;}catch(Exception e){return false;}}private static String formatOdd(String s){try{return String.format(Locale.FRANCE,"%.2f",Double.parseDouble(clean(s).replace(',','.')));}catch(Exception e){return "—";}}private static String clean(String s){return s==null||"null".equalsIgnoreCase(s)?"":s.trim();}private static String enc(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return s;}}private TextView text(String s,int sp,int color,int style){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setTypeface(Typeface.create("sans",style));v.setGravity(Gravity.CENTER_VERTICAL);return v;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
