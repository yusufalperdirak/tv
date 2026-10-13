package com.turktv.app;

import android.content.Context;
import android.content.SharedPreferences;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

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
}
