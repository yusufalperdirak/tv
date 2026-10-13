package com.turktv.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

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

    static boolean useYoutube(Context c) { return p(c).getBoolean("useYoutube", false); }
    static void useYoutube(Context c, boolean v) { p(c).edit().putBoolean("useYoutube", v).apply(); }

    static int last(Context c) { return p(c).getInt("last", 0); }
    static void last(Context c, int v) { p(c).edit().putInt("last", v).apply(); }

    static boolean askedOverlay(Context c) { return p(c).getBoolean("askedOverlay", false); }
    static void askedOverlay(Context c, boolean v) { p(c).edit().putBoolean("askedOverlay", v).apply(); }

    static long listUpdated(Context c) { return p(c).getLong("listUpdated", 0); }
    static void listUpdated(Context c, long v) { p(c).edit().putLong("listUpdated", v).apply(); }

    static String playlistUrl(Context c) { return p(c).getString("playlistUrl", ""); }
    static void playlistUrl(Context c, String v) { p(c).edit().putString("playlistUrl", v).apply(); }

    static int playlistTotal(Context c) { return p(c).getInt("playlistTotal", 0); }
    static void playlistTotal(Context c, int v) { p(c).edit().putInt("playlistTotal", v).apply(); }

    static long playlistUpdated(Context c) { return p(c).getLong("playlistUpdated", 0); }
    static void playlistUpdated(Context c, long v) { p(c).edit().putLong("playlistUpdated", v).apply(); }

    static boolean isFavorite(Context c, Channel ch) {
        return p(c).getStringSet("favs", new HashSet<>()).contains(ch.youtube);
    }

    /** Favori durumunu değiştirir, yeni durumu döndürür. */
    static boolean toggleFavorite(Context c, Channel ch) {
        Set<String> s = new HashSet<>(p(c).getStringSet("favs", new HashSet<>()));
        boolean now;
        if (s.contains(ch.youtube)) {
            s.remove(ch.youtube);
            now = false;
        } else {
            s.add(ch.youtube);
            now = true;
        }
        p(c).edit().putStringSet("favs", s).apply();
        return now;
    }

    /** Uyku zamanlayıcısı bitiş zamanı (ms), 0 = kapalı. Uygulama açık kaldığı sürece geçerli. */
    static long sleepAt = 0;
}
