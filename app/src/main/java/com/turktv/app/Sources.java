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

    static final String WEB = "web:";

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
        // kanalın sitesinden (ör. puhutv) yakalanan yayın; "a|b" birden çok sayfa
        int at = ch.preferWeb ? 0 : l.size(); // tvg-prefer="web": sayfalar en başta
        if (!ch.web.isEmpty()) for (String w : ch.web.split("\\|")) if (!w.trim().isEmpty()) l.add(at++, WEB + w.trim());
        String good = p(c).getString(ch.name, null);
        if (good != null && l.remove(good)) l.add(0, good);
        return l;
    }

    // ---- sayfadan yakalanan yayın adresleri (kısa süre geçerli; önizleme ve izleme aynı adresi kullanır)
    private static final java.util.Map<String, String[]> sniffed = new java.util.HashMap<>(); // ad → {url, ua, sayfa, zaman}
    private static final long SNIFF_TTL = 20 * 60_000;

    static void sniffed(Channel ch, String url, String ua, String page) {
        sniffed.put(ch.name, new String[]{url, ua, page, String.valueOf(System.currentTimeMillis())});
    }

    static void forgetSniffed(Channel ch) {
        sniffed.remove(ch.name);
    }

    /** Bu sayfa için taze yakalanmış yayın (yoksa null). */
    static Channel sniffedFor(Channel ch, String page) {
        String[] v = sniffed.get(ch.name);
        if (v == null || !v[2].equals(page) || System.currentTimeMillis() - Long.parseLong(v[3]) > SNIFF_TTL) return null;
        return Channel.web(ch.name, v[0], v[1], v[2]);
    }

    /** Ana ekran önizlemesinde oynatılacak kanal (sayfa kaynağı tercihliyse ve taze adres varsa o). */
    static Channel preview(Context c, Channel ch) {
        String first = of(c, ch).get(0);
        if (first.startsWith(WEB)) {
            Channel s = sniffedFor(ch, first.substring(WEB.length()));
            if (s != null) return s;
        }
        return ch.withUrl(best(c, ch));
    }

    /** Kanal için en iyi bilinen adres. */
    static String best(Context c, Channel ch) {
        for (String u : of(c, ch)) if (!u.startsWith(WEB)) return u; // önizleme doğrudan adresle
        return ch.url;
    }

    /** Bu adres sorunsuz oynadı. */
    static void good(Context c, Channel ch, String url) {
        if (url == null) return;
        if (url.equals(p(c).getString(ch.name, null))) return;
        p(c).edit().putString(ch.name, url).apply();
    }
}
