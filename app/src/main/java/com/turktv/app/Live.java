package com.turktv.app;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bir kanalın resmi YouTube hesabında o an yayında olan canlı yayını bulur ve önbellekte tutar. */
final class Live {
    private Live() {}

    static final int UNKNOWN = 0, LIVE = 1, OFFLINE = 2;

    interface Callback {
        void done(Channel c, String videoId); // videoId null = canlı yayın yok
    }

    private static final long FRESH_MS = 10 * 60 * 1000;
    private static final Pattern CANONICAL = Pattern.compile(
            "<link rel=\"canonical\" href=\"https://www\\.youtube\\.com/watch\\?v=([\\w-]{11})\"");

    private static final Map<String, String> ids = new ConcurrentHashMap<>();
    private static final Map<String, Long> checkedAt = new ConcurrentHashMap<>();
    private static final Map<String, Integer> status = new ConcurrentHashMap<>();

    private static final ExecutorService pool = Executors.newFixedThreadPool(4);
    private static final Handler ui = new Handler(Looper.getMainLooper());

    static int status(Channel c) {
        Integer s = status.get(c.youtube);
        return s == null ? UNKNOWN : s;
    }

    /** Son 10 dakikada bulunmuş yayın kimliği varsa döndürür. */
    static String cached(Channel c) {
        Long t = checkedAt.get(c.youtube);
        if (t == null || System.currentTimeMillis() - t > FRESH_MS) return null;
        return ids.get(c.youtube);
    }

    /** Arka planda arar, sonucu ana iş parçacığında bildirir. */
    static void find(Channel c, boolean force, Callback cb) {
        if (!force) {
            String hit = cached(c);
            if (hit != null) {
                cb.done(c, hit);
                return;
            }
        }
        pool.execute(() -> {
            String id = resolve(c);
            ui.post(() -> cb.done(c, id));
        });
    }

    /** Tüm kanalların durumunu arka planda kontrol eder. */
    static void checkAll(List<Channel> list, Callback cb) {
        for (Channel c : list) find(c, false, cb);
    }

    private static String resolve(Channel c) {
        String id = fetch(c.youtube);
        long now = System.currentTimeMillis();
        if (id != null) {
            ids.put(c.youtube, id);
            status.put(c.youtube, LIVE);
        } else {
            ids.remove(c.youtube);
            status.put(c.youtube, OFFLINE);
        }
        checkedAt.put(c.youtube, now);
        return id;
    }

    private static String fetch(String handle) {
        HttpURLConnection con = null;
        try {
            URL url = new URL("https://www.youtube.com/" + handle + "/live");
            con = (HttpURLConnection) url.openConnection();
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
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buf = new char[32768];
                int n, sinceCheck = 0;
                while ((n = r.read(buf)) > 0 && html.length() < 3_000_000) {
                    html.append(buf, 0, n);
                    sinceCheck += n;
                    if (sinceCheck > 131072) { // ara ara kontrol et, bulunca indirmeyi kes (hız)
                        sinceCheck = 0;
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
