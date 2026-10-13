package com.turktv.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Kanal logolarını indirir, küçültür ve önbellekte (bellek + disk) tutar. */
final class Logos {
    private Logos() {}

    private static final LruCache<String, Bitmap> mem = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final ExecutorService pool = Executors.newFixedThreadPool(4);
    private static final Handler ui = new Handler(Looper.getMainLooper());

    /** Logoyu yükler; yoksa veya inemezse ImageView gizli kalır, ifNone görünür olur. */
    static void into(Context ctx, final String url, final ImageView iv, final View ifNone) {
        iv.setTag(url);
        if (url == null || url.isEmpty()) {
            iv.setVisibility(View.GONE);
            if (ifNone != null) ifNone.setVisibility(View.VISIBLE);
            return;
        }
        Bitmap hit = mem.get(url);
        if (hit != null) {
            show(iv, hit, ifNone);
            return;
        }
        iv.setVisibility(View.GONE);
        if (ifNone != null) ifNone.setVisibility(View.VISIBLE);
        final File dir = new File(ctx.getApplicationContext().getCacheDir(), "logos");
        pool.execute(() -> {
            Bitmap b = load(dir, url);
            if (b != null) mem.put(url, b);
            ui.post(() -> {
                if (b != null && url.equals(iv.getTag())) show(iv, b, ifNone);
            });
        });
    }

    private static void show(ImageView iv, Bitmap b, View ifNone) {
        iv.setImageBitmap(b);
        iv.setVisibility(View.VISIBLE);
        if (ifNone != null) ifNone.setVisibility(View.GONE);
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
            while (o.outWidth / (sample * 2) >= 240) sample *= 2;
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
