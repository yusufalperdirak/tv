package com.turktv.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kanal logolarını indirir, küçültür ve önbellekte (bellek + disk) tutar.
 * Her logo için kartın zemin rengini de logodan çıkarır.
 */
final class Logos {
    private Logos() {}

    interface ColorCallback {
        void color(int bg);
    }

    private static final LruCache<String, Bitmap> mem = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final Map<String, Integer> colors = new ConcurrentHashMap<>();
    private static final ExecutorService pool = Executors.newFixedThreadPool(4);
    private static final Handler ui = new Handler(Looper.getMainLooper());

    static final int LIGHT_PLATE = 0xFFEEF0F3;

    /** Önbellekte varsa logodan çıkarılmış kart rengi, yoksa null. */
    static Integer cachedBg(String url, int fallbackBg) {
        if (url == null) return null;
        Integer a = colors.get(url);
        return a == null ? null : resolve(a, fallbackBg);
    }

    /**
     * Logoyu yükler. Yoksa veya inemezse ImageView gizli kalır, ifNone görünür olur.
     * fallbackBg: logo koyu/açık durumuna göre seçilecek koyu zemin.
     */
    static void into(Context ctx, final String url, final ImageView iv, final View ifNone,
                     final int fallbackBg, final ColorCallback cb) {
        iv.setTag(url);
        if (url == null || url.isEmpty()) {
            iv.setVisibility(View.GONE);
            if (ifNone != null) ifNone.setVisibility(View.VISIBLE);
            return;
        }
        Bitmap hit = mem.get(url);
        Integer col = colors.get(url);
        if (hit != null && col != null) {
            show(iv, hit, ifNone);
            if (cb != null) cb.color(resolve(col, fallbackBg));
            return;
        }
        iv.setVisibility(View.GONE);
        if (ifNone != null) ifNone.setVisibility(View.VISIBLE);
        final File dir = new File(ctx.getApplicationContext().getCacheDir(), "logos");
        pool.execute(() -> {
            Bitmap b = load(dir, url);
            if (b != null) {
                mem.put(url, b);
                colors.put(url, analyse(b));
            }
            ui.post(() -> {
                if (b == null || !url.equals(iv.getTag())) return;
                show(iv, b, ifNone);
                if (cb != null) cb.color(resolve(colors.get(url), fallbackBg));
            });
        });
    }

    private static void show(ImageView iv, Bitmap b, View ifNone) {
        iv.setImageBitmap(b);
        iv.setVisibility(View.VISIBLE);
        if (ifNone != null) ifNone.setVisibility(View.GONE);
    }

    /** analyse sonucu: 0 → koyu zemin (açık logo), 1 → açık plaka (koyu/renkli logo), diğerleri → logonun kendi zemini. */
    private static int resolve(Integer analysed, int fallbackBg) {
        if (analysed == null || analysed == 0) return fallbackBg;
        if (analysed == 1) return LIGHT_PLATE;
        return analysed;
    }

    /**
     * Logonun kartta nasıl duracağına karar verir:
     * - logonun kendi dolu zemini varsa (kenarlar opak) → o renk, logo kartı kaplar
     * - logo açık renkliyse (beyaz yazı vb.) → koyu zemin
     * - logo koyu/renkliyse → açık plaka
     */
    private static int analyse(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        if (w < 4 || h < 4) return 1;
        int edgeOpaque = 0, edgeTotal = 0;
        long er = 0, eg = 0, eb = 0;
        int n = 24;
        for (int i = 0; i < n; i++) {
            int x = i * (w - 1) / (n - 1), y = i * (h - 1) / (n - 1);
            int[] px = {b.getPixel(x, 0), b.getPixel(x, h - 1), b.getPixel(0, y), b.getPixel(w - 1, y)};
            for (int p : px) {
                edgeTotal++;
                if (Color.alpha(p) > 200) {
                    edgeOpaque++;
                    er += Color.red(p);
                    eg += Color.green(p);
                    eb += Color.blue(p);
                }
            }
        }
        if (edgeOpaque > edgeTotal * 0.85) {
            int c = Color.rgb((int) (er / edgeOpaque), (int) (eg / edgeOpaque), (int) (eb / edgeOpaque));
            return c == 0 || c == 1 ? 0xFF000002 : c; // 0 ve 1 özel değerler
        }
        // şeffaf zeminli logo: görünen piksellerin parlaklığına bak
        double lum = 0;
        int count = 0;
        for (int yy = 0; yy < n; yy++) {
            for (int xx = 0; xx < n; xx++) {
                int p = b.getPixel(xx * (w - 1) / (n - 1), yy * (h - 1) / (n - 1));
                if (Color.alpha(p) < 128) continue;
                lum += (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)) / 255.0;
                count++;
            }
        }
        if (count == 0) return 1;
        return (lum / count) > 0.72 ? 0 : 1;
    }

    private static Bitmap load(File dir, String url) {
        try {
            dir.mkdirs();
            File f = new File(dir, Integer.toHexString(url.hashCode()) + ".img");
            byte[] data;
            if (f.exists() && f.length() > 0) {
                try (InputStream in = new FileInputStream(f)) {
                    data = readAll(in);
                }
            } else {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(10000);
                con.setInstanceFollowRedirects(true);
                con.setRequestProperty("User-Agent", "Mozilla/5.0");
                try {
                    if (con.getResponseCode() != 200) return null;
                    try (InputStream in = con.getInputStream()) {
                        data = readAll(in);
                    }
                } finally {
                    con.disconnect();
                }
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(data);
                }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 320) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o2);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0 && out.size() < 4_000_000) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
