package com.tpai.android;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lecture directe du schéma tpai, sans stockage local. */
final class SupabaseApi {
    static final String ENDPOINT = "https://fdqvjaqclnjztiodfucg.supabase.co";
    static final String PUBLISHABLE_KEY = "sb_publishable_gEReYquGc4yUs1MSDeNztw_FQCpwVuw";
    private SupabaseApi() { }

    static String esc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }

    static String path(String table, Map<String, String> args) {
        StringBuilder out = new StringBuilder("/rest/v1/").append(table);
        boolean first = true;
        for (Map.Entry<String, String> e : args.entrySet()) {
            out.append(first ? '?' : '&'); first = false;
            out.append(esc(e.getKey())).append('=').append(esc(e.getValue()));
        }
        return out.toString();
    }

    static Map<String, String> args(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return result;
    }

    static JSONArray list(String table, Map<String, String> query) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(ENDPOINT + path(table, query)).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("apikey", PUBLISHABLE_KEY);
            // La clé sb_publishable_* n'est PAS un jeton Bearer.
            conn.setRequestProperty("Accept-Profile", "tpai");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Cache-Control", "no-store");
            conn.setUseCaches(false);
            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder result = new StringBuilder();
            if (is != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                        if (result.length() > 3_000_000) throw new Exception("Réponse trop volumineuse");
                    }
                }
            }
            if (code < 200 || code >= 300) {
                String info = result.toString();
                try {
                    JSONObject json = new JSONObject(info);
                    info = json.optString("message", info);
                } catch (Exception ignored) { }
                if (info.length() > 240) info = info.substring(0, 240);
                if (code == 406 || code == 404) info += " | Vérifie que tpai figure dans Exposed schemas.";
                if (code == 401 || code == 403) info += " | Vérifie la clé publique, les droits SELECT et RLS.";
                throw new Exception("Supabase HTTP " + code + " : " + info);
            }
            return new JSONArray(result.toString());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
