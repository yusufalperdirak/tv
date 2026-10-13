package com.turktv.app;

import android.content.Context;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bir TV kanalı. Liste önce internetten indirilmiş güncel kopyadan (files/channels.json),
 * yoksa uygulamanın içindeki assets/channels.json dosyasından okunur.
 */
public class Channel {
    public final int index;        // 0'dan başlar
    public final int number;       // ekranda görünen kanal numarası
    public final String name;
    public final String category;
    public final String youtube;   // örn. "@trthaber"
    public final String appQuery;  // Play Store'da aranacak resmi uygulama (boş olabilir)
    public final int color;

    private Channel(int index, String name, String category, String youtube, String appQuery, int color) {
        this.index = index;
        this.number = index + 1;
        this.name = name;
        this.category = category;
        this.youtube = youtube;
        this.appQuery = appQuery;
        this.color = color;
    }

    static final String LOCAL_FILE = "channels.json";
    private static List<Channel> cache;

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
                throw new RuntimeException("channels.json okunamadı", e);
            }
        }
        cache = Collections.unmodifiableList(list);
        return cache;
    }

    /** Liste değiştiğinde bir sonraki loadAll yeniden okusun. */
    public static synchronized void invalidate() {
        cache = null;
    }

    /** Kategorileri listedeki sırasıyla döndürür. */
    public static List<String> categories(List<Channel> list) {
        List<String> out = new ArrayList<>();
        for (Channel c : list) if (!out.contains(c.category)) out.add(c.category);
        return out;
    }

    static List<Channel> parse(String json) throws Exception {
        JSONArray arr = new JSONObject(json).getJSONArray("channels");
        List<Channel> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            int color;
            try {
                color = Color.parseColor(o.optString("color", "#37474F"));
            } catch (IllegalArgumentException e) {
                color = 0xFF37474F;
            }
            list.add(new Channel(list.size(),
                    o.getString("name"),
                    o.optString("category", "Diğer"),
                    o.getString("youtube"),
                    o.optString("app", ""),
                    darken(color)));
        }
        return list;
    }

    /** Kutucuk renklerini koyu temaya uydurmak için biraz koyulaştırır. */
    private static int darken(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[2] = Math.min(hsv[2], 0.52f);
        return Color.HSVToColor(hsv);
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
