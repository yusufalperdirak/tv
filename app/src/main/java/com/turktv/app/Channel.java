package com.turktv.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bir TV kanalı. Liste M3U biçimindedir: önce internetten indirilmiş güncel kopya
 * (files/channels.m3u), yoksa uygulamanın içindeki assets/channels.m3u okunur.
 */
public class Channel {
    public final int index;       // 0'dan başlar
    public final int number;      // ekranda görünen kanal numarası
    public final String name;
    public final String category;
    public final String url;
    public final String logo;     // boş olabilir
    public final String ua;       // null olabilir
    public final String referer;  // null olabilir
    public final String tvgId;    // program rehberi kimliği (boş olabilir)
    public final String youtube;  // varsa önce bu resmi YouTube canlı yayını denenir (ör. "@atv"), yoksa boş
    public final String[] alts;   // yedek yayın adresleri (tvg-alt="a|b"); ana adres açılmazsa sırayla denenir
    public final String web;      // kanalın canlı yayın sayfası (tvg-web); yayın adresi oradan yakalanır
    public boolean preferWeb;     // tvg-prefer="web": önce sayfadan yakalanan yayın denenir
    public final String ytAlt;    // son çare: kanalın resmi YouTube canlı yayını (tvg-ytalt="@kanal"), yoksa boş
    public final int color;

    /** Listenin başındaki program rehberi (XMLTV) adresi, yoksa boş. */
    static String epgUrl = "";

    private Channel(int index, String name, String category, String url, String logo, String ua, String referer,
                    String tvgId, String youtube) {
        this(index, name, category, url, logo, ua, referer, tvgId, youtube, new String[0], "", "");
    }

    private Channel(int index, String name, String category, String url, String logo, String ua, String referer,
                    String tvgId, String youtube, String[] alts, String ytAlt, String web) {
        this.web = web == null ? "" : web;
        this.alts = alts == null ? new String[0] : alts;
        this.ytAlt = ytAlt == null ? "" : ytAlt;
        this.index = index;
        this.number = index + 1;
        this.name = name;
        this.category = category;
        this.url = url;
        this.logo = logo;
        this.ua = ua;
        this.referer = referer;
        this.tvgId = tvgId;
        this.youtube = youtube;
        Integer c = COLORS.get(category);
        this.color = c != null ? c : 0xFF2B3340;
    }

    /** Tarayıcıda bulunan bir video (kendi oynatıcımızda açmak için). */
    static Channel web(String title, String url, String ua, String referer) {
        return new Channel(0, title, "", url, "", ua, referer, "", "");
    }

    /** Aynı kanal, başka bir sırayla (kullanıcının düzenlediği dizilim için). */
    Channel at(int newIndex) {
        Channel n = new Channel(newIndex, name, category, url, logo, ua, referer, tvgId, youtube, alts, ytAlt, web);
        n.preferWeb = preferWeb;
        return n;
    }

    /** Aynı kanal, yedek yayın adresiyle. */
    Channel withUrl(String newUrl) {
        Channel n = new Channel(index, name, category, newUrl, logo, ua, referer, tvgId, youtube, alts, ytAlt, web);
        n.preferWeb = preferWeb;
        return n;
    }

    static final String LOCAL_FILE = "channels.m3u";
    private static List<Channel> cache;

    /** Kategori sırası (kanal numaraları da bu sırayla verilir). */
    static final List<String> ORDER = Arrays.asList(
            "Ulusal", "Haber", "Spor", "Eğlence", "Film", "Belgesel & Kültür", "Müzik",
            "Çocuk", "Genel", "Dini", "Yurt Dışı", "Diğer");

    private static final Map<String, Integer> COLORS = new HashMap<>();
    static {
        COLORS.put("Ulusal", 0xFF173A6E);
        COLORS.put("Haber", 0xFF6B1B22);
        COLORS.put("Spor", 0xFF0E4D3A);
        COLORS.put("Eğlence", 0xFF5A1A55);
        COLORS.put("Film", 0xFF3D2A6B);
        COLORS.put("Belgesel & Kültür", 0xFF2F4A1C);
        COLORS.put("Müzik", 0xFF6B3A12);
        COLORS.put("Çocuk", 0xFF0F4C5C);
        COLORS.put("Genel", 0xFF2B3340);
        COLORS.put("Dini", 0xFF1F4A44);
        COLORS.put("Yurt Dışı", 0xFF4A2A1A);
    }

    /** Listedeki grup adlarını ekrandaki kategorilere çevirir. */
    static String category(String group) {
        String g = group.trim().toLowerCase(Actions.TR)
                .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
                .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c');
        if (g.isEmpty()) return "Diğer";
        if (g.startsWith("cocuk")) return "Çocuk";
        if (g.contains("yurt disi")) return "Yurt Dışı";
        if (g.startsWith("ulusal")) return "Ulusal";
        if (g.startsWith("haber")) return "Haber";
        if (g.startsWith("spor")) return "Spor";
        if (g.startsWith("eglence")) return "Eğlence";
        if (g.startsWith("film") || g.startsWith("sinema") || g.startsWith("dizi")) return "Film";
        if (g.startsWith("belgesel") || g.startsWith("kultur") || g.startsWith("egitim") || g.startsWith("yasam"))
            return "Belgesel & Kültür";
        if (g.startsWith("muzik")) return "Müzik";
        if (g.startsWith("dini")) return "Dini";
        if (g.startsWith("genel") || g.startsWith("yerel")) return "Genel";
        return "Diğer";
    }

    public static synchronized List<Channel> loadAll(Context ctx) {
        if (cache != null) return cache;
        List<Channel> list = null;
        File f = new File(ctx.getFilesDir(), LOCAL_FILE);
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                list = parse(readAll(in));
            } catch (Exception ignored) {
                list = null;
            }
        }
        if (list == null || list.isEmpty()) {
            try (InputStream in = ctx.getAssets().open(LOCAL_FILE)) {
                list = parse(readAll(in));
            } catch (Exception e) {
                throw new RuntimeException("channels.m3u okunamadı", e);
            }
        }
        cache = Collections.unmodifiableList(list);
        return cache;
    }

    public static synchronized void invalidate() {
        cache = null;
    }

    /** Listede bulunan kategoriler, sabit sırayla. */
    public static List<String> categories(List<Channel> list) {
        List<String> out = new ArrayList<>();
        for (String c : ORDER) {
            for (Channel ch : list) {
                if (ch.category.equals(c)) {
                    out.add(c);
                    break;
                }
            }
        }
        return out;
    }

    private static final Pattern ATTR = Pattern.compile("([a-zA-Z-]+)=\"([^\"]*)\"");

    static List<Channel> parse(String m3u) throws Exception {
        final class Raw {
            String name, group, logo, url, ua, ref, id, yt, alt, ytAlt, web;
            boolean preferWeb;
            int order;
        }
        List<Raw> raws = new ArrayList<>();
        BufferedReader r = new BufferedReader(new StringReader(m3u));
        String line;
        Raw cur = null;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTM3U")) {
                Matcher h = ATTR.matcher(line);
                while (h.find()) {
                    String k = h.group(1).toLowerCase(Actions.TR);
                    if (k.equals("url-tvg") || k.equals("x-tvg-url")) epgUrl = h.group(2).split(",")[0].trim();
                }
                continue;
            }
            if (line.startsWith("#EXTINF")) {
                cur = new Raw();
                int comma = lastCommaOutsideQuotes(line);
                cur.name = comma >= 0 ? line.substring(comma + 1).trim() : "";
                cur.group = "";
                cur.logo = "";
                cur.id = "";
                cur.yt = "";
                cur.alt = "";
                cur.ytAlt = "";
                cur.web = "";
                Matcher m = ATTR.matcher(comma >= 0 ? line.substring(0, comma) : line);
                while (m.find()) {
                    String k = m.group(1).toLowerCase(Actions.TR);
                    if (k.equals("group-title")) cur.group = m.group(2);
                    else if (k.equals("tvg-logo")) cur.logo = m.group(2).trim();
                    else if (k.equals("tvg-id")) cur.id = m.group(2).trim();
                    else if (k.equals("tvg-youtube")) cur.yt = m.group(2).trim();
                    else if (k.equals("tvg-alt")) cur.alt = m.group(2).trim();
                    else if (k.equals("tvg-ytalt")) cur.ytAlt = m.group(2).trim();
                    else if (k.equals("tvg-web")) cur.web = m.group(2).trim();
                    else if (k.equals("tvg-prefer")) cur.preferWeb = "web".equalsIgnoreCase(m.group(2).trim());
                    else if (k.equals("tvg-name") && cur.name.isEmpty()) cur.name = m.group(2);
                }
            } else if (line.startsWith("#EXTVLCOPT:") && cur != null) {
                String opt = line.substring(11).trim();
                if (opt.startsWith("http-user-agent=")) cur.ua = opt.substring(16).trim();
                else if (opt.startsWith("http-referrer=")) cur.ref = opt.substring(14).trim();
                else if (opt.startsWith("http-referer=")) cur.ref = opt.substring(13).trim();
            } else if (!line.startsWith("#") && cur != null) {
                cur.url = line;
                if (cur.name.isEmpty()) cur.name = "Kanal " + (raws.size() + 1);
                cur.order = raws.size();
                raws.add(cur);
                cur = null;
            }
        }
        // kategori sırasına göre diz (kategori içinde listedeki sıra korunur)
        Collections.sort(raws, (a, b) -> {
            int ca = ORDER.indexOf(category(a.group)), cb = ORDER.indexOf(category(b.group));
            return ca != cb ? ca - cb : a.order - b.order;
        });
        List<Channel> list = new ArrayList<>();
        for (Raw x : raws) {
            String[] alts = x.alt.isEmpty() ? new String[0] : x.alt.split("\\|");
            list.add(new Channel(list.size(), x.name, category(x.group), x.url, x.logo, x.ua, x.ref, x.id, x.yt, alts, x.ytAlt, x.web));
            list.get(list.size() - 1).preferWeb = x.preferWeb;
        }
        return list;
    }

    /** Karşılaştırma anahtarı: "TR: Habertürk HD" → "haberturk" */
    static String key(String s) {
        s = s.toLowerCase(Actions.TR)
                .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
                .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c').replace('â', 'a').replace('î', 'i');
        s = s.replaceAll("\\(.*?\\)|\\[.*?\\]", " ");
        s = s.replaceAll("^\\W*tr\\W+", " ");
        s = s.replaceAll("\\b(uhd|fhd|hd|sd|4k|hevc|1080p?|720p?|canli|live)\\b", " ");
        s = s.replaceAll("[^a-z0-9]", "");
        if (s.endsWith("tv") && s.length() > 4) s = s.substring(0, s.length() - 2);
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

    static String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}
