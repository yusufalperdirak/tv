package com.turktv.app;

import android.content.Context;
import android.content.SharedPreferences;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Kullanıcı ayarları. */
final class Prefs {
    private Prefs() {}

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("tv", Context.MODE_PRIVATE);
    }

    static boolean autostart(Context c) { return p(c).getBoolean("autostart", true); }
    static void autostart(Context c, boolean v) { p(c).edit().putBoolean("autostart", v).apply(); }

    static boolean openLast(Context c) { return p(c).getBoolean("openLast", true); }
    static void openLast(Context c, boolean v) { p(c).edit().putBoolean("openLast", v).apply(); }

    static boolean previewSound(Context c) { return p(c).getBoolean("previewSound", false); }
    static void previewSound(Context c, boolean v) { p(c).edit().putBoolean("previewSound", v).apply(); }

    static boolean hideOffline(Context c) { return p(c).getBoolean("hideOffline", false); }
    static void hideOffline(Context c, boolean v) { p(c).edit().putBoolean("hideOffline", v).apply(); }


    /** Ekran kenar boşluğu: 0 az, 1 normal, 2 çok (TV görüntüyü kenarlardan kırpıyorsa artırılır). */
    static int edge(Context c) { return p(c).getInt("edge", 1); }
    static void edge(Context c, int v) { p(c).edit().putInt("edge", v).apply(); }

    static int last(Context c) { return p(c).getInt("last", 0); }
    static void last(Context c, int v) { p(c).edit().putInt("last", v).apply(); }

    static boolean askedOverlay(Context c) { return p(c).getBoolean("askedOverlay", false); }
    static void askedOverlay(Context c, boolean v) { p(c).edit().putBoolean("askedOverlay", v).apply(); }

    static long listUpdated(Context c) { return p(c).getLong("listUpdated", 0); }
    static void listUpdated(Context c, long v) { p(c).edit().putLong("listUpdated", v).apply(); }


    /** Favoriler, kullanıcının belirlediği sırayla (kanal adları). */
    static List<String> favorites(Context c) {
        SharedPreferences sp = p(c);
        List<String> out = new ArrayList<>();
        String raw = sp.getString("favList", null);
        if (raw == null) {
            // eski sürümden: sırasız küme
            out.addAll(sp.getStringSet("favs", new HashSet<>()));
            setFavorites(c, out);
            return out;
        }
        for (String s : raw.split("\n")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    static void setFavorites(Context c, List<String> list) {
        p(c).edit().putString("favList", TextUtils.join("\n", list)).apply();
    }

    static boolean isFavorite(Context c, Channel ch) {
        return favorites(c).contains(ch.name);
    }

    /** Favori durumunu değiştirir, yeni durumu döndürür. Yeni favori sona eklenir. */
    static boolean toggleFavorite(Context c, Channel ch) {
        List<String> l = favorites(c);
        boolean now;
        if (l.remove(ch.name)) {
            now = false;
        } else {
            l.add(ch.name);
            now = true;
        }
        setFavorites(c, l);
        return now;
    }

    /** Uyku zamanlayıcısı bitiş zamanı (ms), 0 = kapalı. Uygulama açık kaldığı sürece geçerli. */
    static long sleepAt = 0;
    /** Uyku zamanlayıcısı "program bitince" modunda mı. */
    static boolean sleepAtProgramEnd = false;

    // ------------------------------------------------------------------ 7.0

    private static List<String> lines(Context c, String key) {
        List<String> out = new ArrayList<>();
        String raw = p(c).getString(key, "");
        for (String s : raw.split("\n")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    private static void lines(Context c, String key, List<String> v) {
        p(c).edit().putString(key, TextUtils.join("\n", v)).apply();
    }

    /** Kullanıcının kanal sırası (kanal adları). */
    static List<String> order(Context c) { return lines(c, "order"); }
    static void order(Context c, List<String> v) { lines(c, "order", v); }

    /** Gizlenen kanallar (kanal adları). */
    static List<String> hidden(Context c) { return lines(c, "hidden"); }
    static void hidden(Context c, List<String> v) { lines(c, "hidden", v); }

    static String lastName(Context c) { return p(c).getString("lastName", null); }

    /** Kanal izlendi: son kanal ve "son izlenenler" güncellenir. */
    static void watched(Context c, Channel ch) {
        List<String> r = recent(c);
        r.remove(ch.name);
        r.add(0, ch.name);
        while (r.size() > 6) r.remove(r.size() - 1);
        p(c).edit().putString("lastName", ch.name).putInt("last", ch.index)
                .putString("recent", TextUtils.join("\n", r)).apply();
    }

    static List<String> recent(Context c) { return lines(c, "recent"); }

    static boolean splashSound(Context c) { return p(c).getBoolean("splashSound", true); }
    static void splashSound(Context c, boolean v) { p(c).edit().putBoolean("splashSound", v).apply(); }

    static boolean askedHome(Context c) { return p(c).getBoolean("askedHome", false); }
    static void askedHome(Context c, boolean v) { p(c).edit().putBoolean("askedHome", v).apply(); }

    static boolean showRates(Context c) { return p(c).getBoolean("showRates", true); }
    static void showRates(Context c, boolean v) { p(c).edit().putBoolean("showRates", v).apply(); }

    static boolean showPrayer(Context c) { return p(c).getBoolean("showPrayer", true); }
    static void showPrayer(Context c, boolean v) { p(c).edit().putBoolean("showPrayer", v).apply(); }

    /** Gece modu: belirlenen saatlerde ses sınırlanır, görüntü hafifçe kararır. */
    static boolean nightMode(Context c) { return p(c).getBoolean("nightMode", false); }
    static void nightMode(Context c, boolean v) { p(c).edit().putBoolean("nightMode", v).apply(); }

    /** Gece modu saatleri: 0 = 22–07, 1 = 23–07, 2 = 00–08 */
    static final int[][] NIGHT_HOURS = {{22, 7}, {23, 7}, {0, 8}};
    static int nightHours(Context c) { return p(c).getInt("nightHours", 1); }
    static void nightHours(Context c, int v) { p(c).edit().putInt("nightHours", v).apply(); }

    static boolean isNightNow(Context c) {
        if (!nightMode(c)) return false;
        int[] h = NIGHT_HOURS[Math.max(0, Math.min(nightHours(c), NIGHT_HOURS.length - 1))];
        int now = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return h[0] > h[1] ? (now >= h[0] || now < h[1]) : (now >= h[0] && now < h[1]);
    }

    /** "Hâlâ izliyor musun?" sorusu. */
    static boolean stillWatching(Context c) { return p(c).getBoolean("stillWatching", true); }
    static void stillWatching(Context c, boolean v) { p(c).edit().putBoolean("stillWatching", v).apply(); }

    // ---- gece kanal taraması
    static long scanAt(Context c) { return p(c).getLong("scanAt", 0); }
    static List<String> broken(Context c) { return lines(c, "broken"); }

    static void scanResult(Context c, List<String> brokenUrls, Map<String, String> alt) {
        List<String> a = new ArrayList<>();
        for (Map.Entry<String, String> e : alt.entrySet()) a.add(e.getKey() + "\t" + e.getValue());
        p(c).edit().putLong("scanAt", System.currentTimeMillis())
                .putString("broken", TextUtils.join("\n", brokenUrls))
                .putString("alt", TextUtils.join("\n", a)).apply();
    }

    /** Bozuk yayının yedeği (bulunduysa), yoksa null. */
    static String altUrl(Context c, String url) {
        for (String s : lines(c, "alt")) {
            int t = s.indexOf('\t');
            if (t > 0 && s.substring(0, t).equals(url)) return s.substring(t + 1);
        }
        return null;
    }

    // ---- hava, döviz, ezan önbelleği (ham metin)
    static String cache(Context c, String key) { return p(c).getString("cache_" + key, null); }
    static long cacheAt(Context c, String key) { return p(c).getLong("cacheAt_" + key, 0); }
    static void cache(Context c, String key, String v) {
        p(c).edit().putString("cache_" + key, v).putLong("cacheAt_" + key, System.currentTimeMillis()).apply();
    }

    static int skippedUpdate(Context c) { return p(c).getInt("skippedUpdate", 0); }
    static void skippedUpdate(Context c, int v) { p(c).edit().putInt("skippedUpdate", v).apply(); }

    // ---- yedekleme: tüm ayarlar (önbellekler hariç)
    static org.json.JSONObject export(Context c) throws org.json.JSONException {
        org.json.JSONObject o = new org.json.JSONObject();
        for (Map.Entry<String, ?> e : p(c).getAll().entrySet()) {
            String k = e.getKey();
            if (k.startsWith("cache") || k.equals("scanAt") || k.equals("broken") || k.equals("alt")) continue;
            Object v = e.getValue();
            if (v instanceof java.util.Set) continue;
            o.put(k, v);
        }
        o.put("_app", "tv");
        return o;
    }

    static void restore(Context c, org.json.JSONObject o) throws org.json.JSONException {
        if (!"tv".equals(o.optString("_app"))) throw new org.json.JSONException("tanınmayan yedek");
        SharedPreferences.Editor ed = p(c).edit();
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (k.startsWith("_")) continue;
            Object v = o.get(k);
            if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
            else if (v instanceof Integer) {
                if (k.endsWith("At") || k.equals("listUpdated")) ed.putLong(k, (Integer) v);
                else ed.putInt(k, (Integer) v);
            } else if (v instanceof Long) ed.putLong(k, (Long) v);
            else if (v instanceof String) ed.putString(k, (String) v);
        }
        ed.apply();
    }
}
