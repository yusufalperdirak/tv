package com.turktv.app;

import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
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

    static int status(Channel c) {
        Integer s = status.get(c.url);
        return s == null ? UNKNOWN : s;
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
                boolean ok = probe(c);
                report(c, ok);
                ui.post(() -> cb.done(c));
            });
        }
    }

    private static boolean probe(Channel c) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(c.url).openConnection();
            con.setConnectTimeout(6000);
            con.setReadTimeout(6000);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", c.ua != null ? c.ua : StreamPlayer.BROWSER_UA);
            if (c.referer != null) con.setRequestProperty("Referer", c.referer);
            int code = con.getResponseCode();
            if (code < 200 || code >= 400) return false;
            try (InputStream in = con.getInputStream()) {
                return in.read(new byte[64]) > 0;
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (con != null) con.disconnect();
        }
    }
}
