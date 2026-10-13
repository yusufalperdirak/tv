package com.turktv.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kullanıcının kendi M3U yayın listesi. Liste adresi yalnızca bu cihazda saklanır.
 * Listedeki kayıtlar kanal adlarından tanınıp uygulamadaki kanallarla eşleştirilir;
 * eşleşen kayıtlar (en iyi ilk olmak üzere birkaç yedek) cihazda küçük bir dosyada tutulur.
 */
final class Playlist {
    private Playlist() {}

    static final class Entry {
        final String name, url, ua, referer;
        int score;

        Entry(String name, String url, String ua, String referer) {
            this.name = name;
            this.url = url;
            this.ua = ua;
            this.referer = referer;
        }
    }

    interface Result {
        void done(boolean changed, String error);
    }

    static final String DEFAULT_UA = "VLC/3.0.20 LibVLC/3.0.20";
    private static final String FILE = "playlist_matches.json";
    private static final int MAX_PER_CHANNEL = 5;
    private static final Pattern GROUP = Pattern.compile("group-title=\"([^\"]*)\"");
    private static final Pattern TVG_NAME = Pattern.compile("tvg-name=\"([^\"]*)\"");

    private static Map<String, List<Entry>> byChannel = new HashMap<>();
    private static boolean loaded;
    private static int version;

    // ------------------------------------------------------------------ okuma

    static synchronized void ensureLoaded(Context ctx) {
        if (loaded) return;
        loaded = true;
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.exists() || Prefs.playlistUrl(ctx).isEmpty()) return;
        try (FileInputStream in = new FileInputStream(f)) {
            byChannel = fromJson(Channel.readAll(in));
        } catch (Exception ignored) {
            byChannel = new HashMap<>();
        }
    }

    static synchronized List<Entry> entries(Channel c) {
        List<Entry> l = byChannel.get(c.youtube);
        return l == null ? Collections.<Entry>emptyList() : l;
    }

    static synchronized boolean has(Channel c) {
        List<Entry> l = byChannel.get(c.youtube);
        return l != null && !l.isEmpty();
    }

    static synchronized int matched() {
        return byChannel.size();
    }

    /** Eşleşmeler her değiştiğinde artar; ekranlar kendini yenilemek için kullanır. */
    static synchronized int version() {
        return version;
    }

    static synchronized void clear(Context ctx) {
        byChannel = new HashMap<>();
        version++;
        new File(ctx.getFilesDir(), FILE).delete();
        Prefs.playlistTotal(ctx, 0);
    }

    // ------------------------------------------------------------------ indirme ve eşleştirme

    static void refresh(Context ctx, List<Channel> channels, Result cb) {
        final Context app = ctx.getApplicationContext();
        final Handler ui = new Handler(Looper.getMainLooper());
        final String listUrl = Prefs.playlistUrl(app);
        if (listUrl.isEmpty()) {
            ui.post(() -> cb.done(false, null));
            return;
        }
        new Thread(() -> {
            String error = null;
            boolean changed = false;
            HttpURLConnection con = null;
            try {
                Map<String, List<Channel>> keys = channelKeys(channels);
                Map<String, List<Entry>> found = new HashMap<>();
                int total = 0;

                con = (HttpURLConnection) new URL(listUrl).openConnection();
                con.setConnectTimeout(15000);
                con.setReadTimeout(30000);
                con.setInstanceFollowRedirects(true);
                con.setRequestProperty("User-Agent", DEFAULT_UA);
                int code = con.getResponseCode();
                if (code != 200) throw new Exception("sunucu " + code + " döndürdü");

                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                    String line, name = null, tvgName = null, group = "", ua = null, ref = null;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) continue;
                        if (line.startsWith("#EXTINF")) {
                            int comma = lastCommaOutsideQuotes(line);
                            name = comma >= 0 ? line.substring(comma + 1).trim() : "";
                            Matcher g = GROUP.matcher(line);
                            group = g.find() ? g.group(1) : "";
                            Matcher t = TVG_NAME.matcher(line);
                            tvgName = t.find() ? t.group(1) : null;
                            ua = null;
                            ref = null;
                        } else if (line.startsWith("#EXTVLCOPT:")) {
                            String opt = line.substring(11).trim();
                            if (opt.startsWith("http-user-agent=")) ua = opt.substring(16).trim();
                            else if (opt.startsWith("http-referrer=")) ref = opt.substring(14).trim();
                            else if (opt.startsWith("http-referer=")) ref = opt.substring(13).trim();
                        } else if (!line.startsWith("#")) {
                            if (name != null) {
                                total++;
                                List<Channel> hits = keys.get(key(name));
                                if (hits == null) hits = keys.get(shortKey(key(name)));
                                if (hits == null && tvgName != null) {
                                    hits = keys.get(key(tvgName));
                                    if (hits == null) hits = keys.get(shortKey(key(tvgName)));
                                }
                                if (hits != null) {
                                    for (Channel c : hits) {
                                        Entry e = new Entry(name, line, ua, ref);
                                        e.score = score(name, group);
                                        List<Entry> l = found.get(c.youtube);
                                        if (l == null) {
                                            l = new ArrayList<>();
                                            found.put(c.youtube, l);
                                        }
                                        l.add(e);
                                    }
                                }
                            }
                            name = null;
                            tvgName = null;
                        }
                    }
                }
                if (total == 0) throw new Exception("listede kanal bulunamadı (M3U biçiminde değil mi?)");

                for (List<Entry> l : found.values()) {
                    Collections.sort(l, (a, b) -> b.score - a.score); // kararlı sıralama: eşitlerde liste sırası korunur
                    while (l.size() > MAX_PER_CHANNEL) l.remove(l.size() - 1);
                }

                String json = toJson(found);
                File f = new File(app.getFilesDir(), FILE);
                String old = null;
                if (f.exists()) {
                    try (FileInputStream in = new FileInputStream(f)) {
                        old = Channel.readAll(in);
                    }
                }
                if (!json.equals(old)) {
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        out.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                    synchronized (Playlist.class) {
                        byChannel = found;
                        loaded = true;
                        version++;
                    }
                    changed = true;
                }
                Prefs.playlistTotal(app, total);
                Prefs.playlistUpdated(app, System.currentTimeMillis());
            } catch (Exception e) {
                error = "Liste indirilemedi: " + e.getMessage();
            } finally {
                if (con != null) con.disconnect();
            }
            final boolean ch = changed;
            final String err = error;
            ui.post(() -> cb.done(ch, err));
        }).start();
    }

    // ------------------------------------------------------------------ ad eşleştirme

    private static Map<String, List<Channel>> channelKeys(List<Channel> channels) {
        Map<String, List<Channel>> m = new HashMap<>();
        for (Channel c : channels) {
            List<String> names = new ArrayList<>();
            names.add(c.name);
            names.addAll(c.aliases);
            for (String n : names) {
                String k = key(n);
                put(m, k, c);
                put(m, shortKey(k), c);
            }
        }
        return m;
    }

    private static void put(Map<String, List<Channel>> m, String k, Channel c) {
        if (k.isEmpty()) return;
        List<Channel> l = m.get(k);
        if (l == null) {
            l = new ArrayList<>();
            m.put(k, l);
        }
        if (!l.contains(c)) l.add(c);
    }

    /** "TR: ATV HD", "ATV FHD (Yedek)", "atv" → "atv" */
    static String key(String s) {
        s = s.toLowerCase(Actions.TR)
                .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
                .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c').replace('â', 'a').replace('î', 'i');
        s = s.replaceAll("\\(.*?\\)|\\[.*?\\]|\\{.*?\\}", " ");
        s = s.replaceAll("^\\W*tr\\W+", " ");            // TR: , TR | , [TR] öneki
        s = s.replaceAll("\\b(uhd|fhd|hd|sd|4k|hevc|h265|h264|raw|1080p?|720p?|576p?|50fps|backup|yedek|vip|plus|\\+)\\b", " ");
        s = s.replaceAll("[^a-z0-9]", "");
        return s;
    }

    /** Sonundaki "tv"yi atar: "startv" → "star", "nowtv" → "now" (çok kısa kalanlar hariç). */
    static String shortKey(String k) {
        if (k.endsWith("tv") && k.length() > 4) return k.substring(0, k.length() - 2);
        return k;
    }

    /** Türkiye grubundaki ve yüksek kaliteli kayıtlar öne geçsin. */
    private static int score(String name, String group) {
        String n = name.toLowerCase(Actions.TR);
        String g = group.toLowerCase(Actions.TR);
        int s = 0;
        if (n.matches("^\\W*tr\\W.*") || g.contains("türk") || g.contains("turk") || g.matches(".*\\btr\\b.*")) s += 10;
        if (n.contains("fhd") || n.contains("1080")) s += 3;
        else if (n.contains("hd")) s += 2;
        if (n.contains("4k") || n.contains("uhd")) s += 1; // çok ağır olabilir, FHD'den sonra
        if (n.contains("sd")) s -= 1;
        if (n.contains("backup") || n.contains("yedek")) s -= 2;
        return s;
    }

    private static int lastCommaOutsideQuotes(String s) {
        boolean q = false;
        int last = -1;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') q = !q;
            else if (ch == ',' && !q) last = i;
        }
        return last;
    }

    // ------------------------------------------------------------------ kayıt

    private static String toJson(Map<String, List<Entry>> m) throws Exception {
        JSONObject root = new JSONObject();
        List<String> ks = new ArrayList<>(m.keySet());
        Collections.sort(ks);
        for (String k : ks) {
            JSONArray arr = new JSONArray();
            for (Entry e : m.get(k)) {
                JSONObject o = new JSONObject();
                o.put("n", e.name);
                o.put("u", e.url);
                if (e.ua != null) o.put("a", e.ua);
                if (e.referer != null) o.put("r", e.referer);
                arr.put(o);
            }
            root.put(k, arr);
        }
        return root.toString();
    }

    private static Map<String, List<Entry>> fromJson(String json) throws Exception {
        Map<String, List<Entry>> m = new HashMap<>();
        JSONObject root = new JSONObject(json);
        JSONArray names = root.names();
        if (names == null) return m;
        for (int i = 0; i < names.length(); i++) {
            String k = names.getString(i);
            JSONArray arr = root.getJSONArray(k);
            List<Entry> l = new ArrayList<>();
            for (int j = 0; j < arr.length(); j++) {
                JSONObject o = arr.getJSONObject(j);
                l.add(new Entry(o.optString("n"), o.getString("u"),
                        o.has("a") ? o.getString("a") : null, o.has("r") ? o.getString("r") : null));
            }
            m.put(k, l);
        }
        return m;
    }
}
