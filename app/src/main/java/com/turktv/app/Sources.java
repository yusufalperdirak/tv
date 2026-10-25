package com.turktv.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Bir kanalın yayın adresleri: listedeki ana adres, listedeki yedekler (tvg-alt) ve taramada bulunan yedek.
 * En son sorunsuz oynayan adres hatırlanır; kanal bir dahaki sefere doğrudan ondan açılır.
 */
final class Sources {
    private Sources() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("sources", Context.MODE_PRIVATE);
    }

    /** Denenecek adresler, sırayla (hatırlanan çalışan adres en başta). */
    static List<String> of(Context c, Channel ch) {
        List<String> l = new ArrayList<>();
        l.add(ch.url);
        for (String a : ch.alts) if (a != null && !a.trim().isEmpty() && !l.contains(a.trim())) l.add(a.trim());
        String scanned = Prefs.altUrl(c, ch.url);
        if (scanned != null && !l.contains(scanned)) l.add(scanned);
        String good = p(c).getString(ch.name, null);
        if (good != null && l.remove(good)) l.add(0, good);
        return l;
    }

    /** Kanal için en iyi bilinen adres. */
    static String best(Context c, Channel ch) {
        return of(c, ch).get(0);
    }

    /** Bu adres sorunsuz oynadı. */
    static void good(Context c, Channel ch, String url) {
        if (url == null) return;
        if (url.equals(p(c).getString(ch.name, null))) return;
        p(c).edit().putString(ch.name, url).apply();
    }
}
