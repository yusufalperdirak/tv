package com.turktv.app;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ekranda görünen kanal dizilimi: kullanıcının sırası, gizlediği kanallar çıkarılmış hâli.
 * Kanal numaraları bu dizilime göre verilir (1, 2, 3…).
 */
final class Lineup {
    private Lineup() {}

    private static List<Channel> cache;
    private static List<Channel> cacheRaw;
    private static int cacheVersion = -1;
    private static int version;

    /** Düzenleme sonrası dizilimi yeniden kur. */
    static synchronized void invalidate() {
        version++;
    }

    static synchronized List<Channel> get(Context c) {
        List<Channel> raw = Channel.loadAll(c);
        if (cache != null && raw == cacheRaw && version == cacheVersion) return cache;
        List<Channel> ordered = ordered(c, raw);
        Set<String> hidden = new HashSet<>(Prefs.hidden(c));
        List<Channel> out = new ArrayList<>();
        for (Channel ch : ordered) if (!hidden.contains(ch.name)) out.add(ch.at(out.size()));
        if (out.isEmpty()) for (Channel ch : ordered) out.add(ch.at(out.size()));
        cache = Collections.unmodifiableList(out);
        cacheRaw = raw;
        cacheVersion = version;
        return cache;
    }

    /** Tüm kanallar (gizliler dahil), kullanıcının sırasıyla. */
    static List<Channel> ordered(Context c, List<Channel> raw) {
        Map<String, Channel> byName = new LinkedHashMap<>();
        for (Channel ch : raw) if (!byName.containsKey(ch.name)) byName.put(ch.name, ch);
        List<Channel> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (String n : Prefs.order(c)) {
            Channel ch = byName.get(n);
            if (ch != null && used.add(n)) out.add(ch);
        }
        for (Channel ch : raw) if (used.add(ch.name)) out.add(ch);
        return out;
    }

    static int indexOfName(Context c, String name) {
        if (name == null) return -1;
        List<Channel> l = get(c);
        for (Channel ch : l) if (ch.name.equals(name)) return ch.index;
        return -1;
    }

    /** Son izlenen kanalın bu dizilimdeki sırası. */
    static int lastIndex(Context c) {
        int i = indexOfName(c, Prefs.lastName(c));
        if (i >= 0) return i;
        List<Channel> l = get(c);
        return Math.max(0, Math.min(Prefs.last(c), l.size() - 1));
    }

    /** Son izlenen kanallar (en yeni başta), görünür olanlar. */
    static List<Channel> recent(Context c, int max) {
        List<Channel> out = new ArrayList<>();
        for (String n : Prefs.recent(c)) {
            int i = indexOfName(c, n);
            if (i >= 0) out.add(get(c).get(i));
            if (out.size() >= max) break;
        }
        return out;
    }
}
