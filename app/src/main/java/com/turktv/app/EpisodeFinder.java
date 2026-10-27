package com.turktv.app;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Geçmiş bir programın bölümünü YouTube'da arar (kanalların resmi hesapları dizi ve programlarını
 * yayından sonra yükler). Sonuçlar, program adını içeren ve uzun olan videolar önde olacak şekilde sıralanır.
 */
final class EpisodeFinder {
    static final class Result {
        String id, title, channel, length, published;
        int score;
    }

    interface Callback {
        void done(List<Result> results);
    }

    private static final ExecutorService pool = Executors.newSingleThreadExecutor();
    private static final Handler ui = new Handler(Looper.getMainLooper());

    static void search(String programTitle, String channelName, Callback cb) {
        pool.execute(() -> {
            List<Result> out = new ArrayList<>();
            try {
                String q = programTitle + " " + channelName;
                String html = get("https://www.youtube.com/results?hl=tr&gl=TR&search_query=" + Uri.encode(q));
                int i = html.indexOf("var ytInitialData = ");
                if (i < 0) i = html.indexOf("ytInitialData\"] = ");
                if (i >= 0) {
                    int s = html.indexOf('{', i);
                    int e = html.indexOf(";</script>", s);
                    JSONObject data = new JSONObject(html.substring(s, e));
                    collect(data, out);
                }
            } catch (Exception ignored) {
            }
            String key = Channel.key(programTitle);
            for (Result r : out) {
                String k = Channel.key(r.title);
                if (!key.isEmpty() && k.contains(key)) r.score += 10;
                if (Channel.key(r.channel).contains(Channel.key(channelName))) r.score += 4;
                if (r.title.toLowerCase(Actions.TR).contains("bölüm")) r.score += 3;
                if (minutes(r.length) >= 20) r.score += 3;
                else if (minutes(r.length) < 5) r.score -= 5;
            }
            Collections.sort(out, (a, b) -> b.score - a.score);
            final List<Result> res = out.size() > 8 ? out.subList(0, 8) : out;
            ui.post(() -> cb.done(res));
        });
    }

    private static int minutes(String len) {
        if (len == null || len.isEmpty()) return 0;
        String[] p = len.split(":");
        try {
            if (p.length == 3) return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
            if (p.length == 2) return Integer.parseInt(p[0]);
        } catch (Exception ignored) {
        }
        return 0;
    }

    /** JSON ağacında videoRenderer'ları bulur. */
    private static void collect(Object o, List<Result> out) {
        if (out.size() >= 20) return;
        if (o instanceof JSONObject) {
            JSONObject j = (JSONObject) o;
            JSONObject v = j.optJSONObject("videoRenderer");
            if (v != null) {
                Result r = new Result();
                r.id = v.optString("videoId");
                r.title = text(v.optJSONObject("title"));
                r.channel = text(v.optJSONObject("ownerText"));
                r.length = text(v.optJSONObject("lengthText"));
                r.published = text(v.optJSONObject("publishedTimeText"));
                if (!r.id.isEmpty() && !r.title.isEmpty() && !r.length.isEmpty()) out.add(r); // canlı yayınlar hariç
                return;
            }
            JSONArray names = j.names();
            if (names == null) return;
            for (int i = 0; i < names.length(); i++) collect(j.opt(names.optString(i)), out);
        } else if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            for (int i = 0; i < a.length(); i++) collect(a.opt(i), out);
        }
    }

    private static String text(JSONObject t) {
        if (t == null) return "";
        if (t.has("simpleText")) return t.optString("simpleText");
        JSONArray runs = t.optJSONArray("runs");
        if (runs == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < runs.length(); i++) b.append(runs.optJSONObject(i).optString("text"));
        return b.toString();
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(15000);
        con.setRequestProperty("User-Agent", StreamPlayer.BROWSER_UA);
        con.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9");
        con.setRequestProperty("Cookie", "CONSENT=YES+1; SOCS=CAI"); // AB onay sayfasını atla
        try (InputStream in = con.getInputStream()) {
            return Channel.readAll(in);
        } finally {
            con.disconnect();
        }
    }
}
