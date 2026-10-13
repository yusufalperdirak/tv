package com.turktv.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

/**
 * Program rehberi. Kanal listesindeki XMLTV adresinden indirilir, kanallarla tvg-id
 * (yoksa adı) üzerinden eşleştirilir ve cihazda küçük bir dosyada tutulur.
 */
final class Epg {
    private Epg() {}

    static final class Prog {
        final long start, end;
        final String title;

        Prog(long start, long end, String title) {
            this.start = start;
            this.end = end;
            this.title = title;
        }

        /** 0..1 arası ilerleme */
        float progress(long now) {
            if (end <= start) return 0;
            return Math.max(0f, Math.min(1f, (now - start) / (float) (end - start)));
        }
    }

    private static final String FILE = "epg.json";
    private static final long MAX_AGE = 6 * 60 * 60 * 1000L;
    private static final Handler ui = new Handler(Looper.getMainLooper());

    private static Map<String, List<Prog>> byName = new HashMap<>();
    private static long loadedAt;
    private static boolean loaded;
    private static boolean refreshing;

    static synchronized void ensureLoaded(Context ctx) {
        if (loaded) return;
        loaded = true;
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.exists()) return;
        try (InputStream in = new FileInputStream(f)) {
            JSONObject root = new JSONObject(Channel.readAll(in));
            loadedAt = root.optLong("t");
            byName = fromJson(root.getJSONObject("c"));
        } catch (Exception ignored) {
        }
    }

    static synchronized boolean hasAny() {
        return !byName.isEmpty();
    }

    static synchronized Prog now(Channel c) {
        List<Prog> l = byName.get(c.name);
        if (l == null) return null;
        long t = System.currentTimeMillis();
        for (Prog p : l) if (p.start <= t && t < p.end) return p;
        return null;
    }

    static synchronized Prog next(Channel c) {
        List<Prog> l = byName.get(c.name);
        if (l == null) return null;
        long t = System.currentTimeMillis();
        for (Prog p : l) if (p.start > t) return p;
        return null;
    }

    static String hm(long t) {
        return new SimpleDateFormat("HH:mm", Actions.TR).format(new java.util.Date(t));
    }

    /** Gerekirse (6 saatte bir) arka planda yeniler; bittiğinde onDone çağrılır (değiştiyse true). */
    static void refreshIfNeeded(Context ctx, List<Channel> channels, final OnDone onDone) {
        ensureLoaded(ctx);
        final String url = Channel.epgUrl;
        synchronized (Epg.class) {
            if (url.isEmpty() || refreshing || System.currentTimeMillis() - loadedAt < MAX_AGE) return;
            refreshing = true;
        }
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            boolean ok = false;
            try {
                Map<String, List<Prog>> m = download(url, channels);
                if (!m.isEmpty()) {
                    long t = System.currentTimeMillis();
                    JSONObject root = new JSONObject();
                    root.put("t", t);
                    root.put("c", toJson(m));
                    try (FileOutputStream out = new FileOutputStream(new File(app.getFilesDir(), FILE))) {
                        out.write(root.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    synchronized (Epg.class) {
                        byName = m;
                        loadedAt = t;
                    }
                    ok = true;
                }
            } catch (Exception ignored) {
            } finally {
                synchronized (Epg.class) {
                    refreshing = false;
                }
            }
            final boolean changed = ok;
            ui.post(() -> {
                if (onDone != null) onDone.done(changed);
            });
        }).start();
    }

    interface OnDone {
        void done(boolean changed);
    }

    // ------------------------------------------------------------------ XMLTV

    private static Map<String, List<Prog>> download(String url, List<Channel> channels) throws Exception {
        // tvg-id → kanal, ad anahtarı → kanal
        Map<String, Channel> byId = new HashMap<>();
        Map<String, Channel> byKey = new HashMap<>();
        for (Channel c : channels) {
            if (!c.tvgId.isEmpty()) byId.put(c.tvgId.toLowerCase(Locale.ROOT), c);
            byKey.put(Channel.key(c.name), c);
        }
        Map<String, Channel> xmlIdToChannel = new HashMap<>();
        Map<String, List<Prog>> out = new HashMap<>();
        long now = System.currentTimeMillis();
        long from = now - 3 * 3600_000L, to = now + 30 * 3600_000L;

        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(60000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        try {
            if (con.getResponseCode() != 200) return out;
            InputStream raw = new BufferedInputStream(con.getInputStream(), 65536);
            raw.mark(2);
            int b1 = raw.read(), b2 = raw.read();
            raw.reset();
            InputStream in = (b1 == 0x1f && b2 == 0x8b) ? new GZIPInputStream(raw, 65536) : raw;

            XmlPullParser xp = Xml.newPullParser();
            xp.setInput(in, "UTF-8");
            String chId = null;
            Channel progCh = null;
            long ps = 0, pe = 0;
            String tag;
            for (int ev = xp.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = xp.next()) {
                if (ev != XmlPullParser.START_TAG) continue;
                tag = xp.getName();
                if ("channel".equals(tag)) {
                    chId = xp.getAttributeValue(null, "id");
                    if (chId != null) {
                        Channel c = byId.get(chId.toLowerCase(Locale.ROOT));
                        if (c != null) xmlIdToChannel.put(chId, c);
                    }
                } else if ("display-name".equals(tag) && chId != null && !xmlIdToChannel.containsKey(chId)) {
                    Channel c = byKey.get(Channel.key(xp.nextText()));
                    if (c != null) xmlIdToChannel.put(chId, c);
                } else if ("programme".equals(tag)) {
                    chId = null;
                    progCh = xmlIdToChannel.get(xp.getAttributeValue(null, "channel"));
                    if (progCh != null) {
                        ps = time(xp.getAttributeValue(null, "start"));
                        pe = time(xp.getAttributeValue(null, "stop"));
                        if (pe < from || ps > to) progCh = null;
                    }
                } else if ("title".equals(tag) && progCh != null) {
                    String title = xp.nextText().trim();
                    List<Prog> l = out.get(progCh.name);
                    if (l == null) {
                        l = new ArrayList<>();
                        out.put(progCh.name, l);
                    }
                    l.add(new Prog(ps, pe, title));
                    progCh = null; // aynı programın başka dildeki başlıklarını alma
                }
            }
        } finally {
            con.disconnect();
        }
        for (List<Prog> l : out.values()) Collections.sort(l, (a, b) -> Long.compare(a.start, b.start));
        return out;
    }

    private static long time(String s) {
        if (s == null) return 0;
        s = s.trim();
        try {
            if (s.length() >= 20) {
                SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
                return f.parse(s).getTime();
            }
            SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(s.substring(0, Math.min(14, s.length()))).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    private static JSONObject toJson(Map<String, List<Prog>> m) throws Exception {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, List<Prog>> e : m.entrySet()) {
            JSONArray arr = new JSONArray();
            for (Prog p : e.getValue()) {
                JSONArray x = new JSONArray();
                x.put(p.start);
                x.put(p.end);
                x.put(p.title);
                arr.put(x);
            }
            o.put(e.getKey(), arr);
        }
        return o;
    }

    private static Map<String, List<Prog>> fromJson(JSONObject o) throws Exception {
        Map<String, List<Prog>> m = new HashMap<>();
        JSONArray names = o.names();
        if (names == null) return m;
        for (int i = 0; i < names.length(); i++) {
            String k = names.getString(i);
            JSONArray arr = o.getJSONArray(k);
            List<Prog> l = new ArrayList<>();
            for (int j = 0; j < arr.length(); j++) {
                JSONArray x = arr.getJSONArray(j);
                l.add(new Prog(x.getLong(0), x.getLong(1), x.getString(2)));
            }
            m.put(k, l);
        }
        return m;
    }
}
