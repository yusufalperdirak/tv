package com.turktv.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Kanalların yayın adreslerine ulaşılabiliyor mu, arka planda hızlıca kontrol eder. */
final class Health {
    private Health() {}

    static final int UNKNOWN = 0, LIVE = 1, OFFLINE = 2;
    static final String DEFAULT_UA = "VLC/3.0.20 LibVLC/3.0.20";

    interface Callback {
        void done(Channel c);
    }

    private static final long FRESH_MS = 10 * 60 * 1000;
    private static final Map<String, Integer> status = new ConcurrentHashMap<>();
    private static final Map<String, Long> checkedAt = new ConcurrentHashMap<>();
    private static final ExecutorService pool = Executors.newFixedThreadPool(3);
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static volatile Set<String> nightBroken = new HashSet<>();

    /** Gece taramasının sonuçlarını yükler (henüz denenmemiş kanallar için kullanılır). */
    static void init(Context c) {
        nightBroken = new HashSet<>(Prefs.broken(c));
    }

    static int status(Channel c) {
        Integer s = status.get(c.url);
        if (s != null) return s;
        return nightBroken.contains(c.url) ? OFFLINE : UNKNOWN;
    }

    /** Oynatıcı sonucu bildirir: açıldıysa canlı, açılmadıysa yayın yok. */
    static void report(Channel c, boolean ok) {
        status.put(c.url, ok ? LIVE : OFFLINE);
        checkedAt.put(c.url, System.currentTimeMillis());
    }

    static void checkAll(List<Channel> list, Callback cb) {
        long now = System.currentTimeMillis();
        for (final Channel c : list) {
            Long t = checkedAt.get(c.url);
            if (t != null && now - t < FRESH_MS) continue;
            pool.execute(() -> {
                boolean ok = probe(c.url, c.ua, c.referer);
                report(c, ok);
                ui.post(() -> cb.done(c));
            });
        }
    }

    /** Yayın adresi yanıt veriyor mu (HLS listesiyse içi de kontrol edilir). Arka planda çağrılmalı. */
    static boolean probe(String url, String ua, String referer) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(6000);
            con.setReadTimeout(6000);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", ua != null ? ua : StreamPlayer.BROWSER_UA);
            if (referer != null) con.setRequestProperty("Referer", referer);
            int code = con.getResponseCode();
            if (code < 200 || code >= 400) return false;
            try (InputStream in = con.getInputStream()) {
                byte[] b = new byte[512];
                int n = in.read(b);
                if (n <= 0) return false;
                if (url.toLowerCase(Actions.TR).contains("m3u8")) {
                    String head = new String(b, 0, n, StandardCharsets.UTF_8);
                    return head.contains("#EXT");
                }
                return true;
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    /** Bağlantıyı önceden ısıt (DNS, TLS, sunucu): yan kanallara geçiş daha hızlı olur. */
    static void warm(Channel c) {
        if (c == null || !c.youtube.isEmpty()) return;
        pool.execute(() -> probe(c.url, c.ua, c.referer));
    }
}
