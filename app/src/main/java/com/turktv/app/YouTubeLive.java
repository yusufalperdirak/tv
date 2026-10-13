package com.turktv.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bir kanalın resmi YouTube hesabında o an yayında olan canlı yayının kimliğini bulur. */
final class YouTubeLive {
    private YouTubeLive() {}

    interface Callback {
        void done(String videoId); // null = şu an canlı yayın yok
    }

    private static final long FRESH_MS = 10 * 60 * 1000;
    private static final Pattern CANONICAL = Pattern.compile(
            "<link rel=\"canonical\" href=\"https://www\\.youtube\\.com/watch\\?v=([\\w-]{11})\"");
    private static final Map<String, String> ids = new ConcurrentHashMap<>();
    private static final Map<String, Long> checkedAt = new ConcurrentHashMap<>();
    private static final ExecutorService pool = Executors.newSingleThreadExecutor();
    private static final Handler ui = new Handler(Looper.getMainLooper());

    private static SharedPreferences store;

    /** Uygulama açılırken bir kez çağrılır: bulunan yayın kimlikleri cihazda hatırlanır. */
    static void init(Context c) {
        if (store == null) store = c.getApplicationContext().getSharedPreferences("yt", Context.MODE_PRIVATE);
    }

    /** Daha önce bulunmuş yayın kimliği (varsa) — beklemeden hemen başlatmak için. */
    static String remembered(String handle) {
        return store == null ? null : store.getString(handle, null);
    }

    static void forget(String handle) {
        if (store != null) store.edit().remove(handle).apply();
        checkedAt.remove(handle);
        ids.remove(handle);
    }

    static void find(final String handle, boolean force, final Callback cb) {
        Long t = checkedAt.get(handle);
        if (!force && t != null && System.currentTimeMillis() - t < FRESH_MS) {
            cb.done(ids.get(handle));
            return;
        }
        pool.execute(() -> {
            String id = fetch(handle);
            if (id != null) {
                ids.put(handle, id);
                if (store != null) store.edit().putString(handle, id).apply();
            } else {
                ids.remove(handle);
            }
            checkedAt.put(handle, System.currentTimeMillis());
            ui.post(() -> cb.done(id));
        });
    }

    private static String fetch(String handle) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL("https://www.youtube.com/" + handle + "/live").openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(12000);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36");
            con.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9");
            con.setRequestProperty("Cookie", "SOCS=CAI; CONSENT=YES+1");
            if (con.getResponseCode() != 200) return null;
            StringBuilder html = new StringBuilder();
            String id = null;
            boolean live = false;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buf = new char[32768];
                int n, since = 0;
                while ((n = r.read(buf)) > 0 && html.length() < 3_000_000) {
                    html.append(buf, 0, n);
                    since += n;
                    if (since > 131072) {
                        since = 0;
                        if (id == null) {
                            Matcher m = CANONICAL.matcher(html);
                            if (m.find()) id = m.group(1);
                        }
                        live = live || html.indexOf("\"isLive\":true") >= 0 || html.indexOf("\"isLiveNow\":true") >= 0;
                        if (id != null && live) break;
                    }
                }
            }
            if (id == null) {
                Matcher m = CANONICAL.matcher(html);
                if (m.find()) id = m.group(1);
            }
            live = live || html.indexOf("\"isLive\":true") >= 0 || html.indexOf("\"isLiveNow\":true") >= 0;
            return (id != null && live) ? id : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }
}
