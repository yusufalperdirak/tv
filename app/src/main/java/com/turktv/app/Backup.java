package com.turktv.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Ayarları yedekle / geri yükle: kanal sırası, gizlenen kanallar ve tüm ayarlar kısa bir koda kaydedilir
 * (dpaste.com üzerinde, 1 yıl saklanır). Uygulamayı yeniden kurunca ya da başka TV'de bu kodla geri gelir.
 */
final class Backup {
    private Backup() {}

    interface Result {
        void done(String codeOrNull, String error);
    }

    private static final Handler ui = new Handler(Looper.getMainLooper());

    static void save(Context ctx, Result r) {
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            String code = null, err = null;
            try {
                String json = Prefs.export(c).toString();
                String body = "content=" + URLEncoder.encode(json, "UTF-8") + "&syntax=json&expiry_days=365";
                HttpURLConnection con = (HttpURLConnection) new URL("https://dpaste.com/api/v2/").openConnection();
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setConnectTimeout(10000);
                con.setReadTimeout(15000);
                con.setRequestProperty("User-Agent", "TV/" + BuildConfig.VERSION_NAME + " (Android TV)");
                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream o = con.getOutputStream()) {
                    o.write(body.getBytes(StandardCharsets.UTF_8));
                }
                int status = con.getResponseCode();
                if (status < 200 || status >= 300) throw new Exception("sunucu yanıtı " + status);
                String url;
                String loc = con.getHeaderField("Location");
                if (loc != null) url = loc;
                else try (InputStream in = con.getInputStream()) {
                    url = Channel.readAll(in).trim();
                }
                con.disconnect();
                code = url.replaceAll("/+$", "");
                code = code.substring(code.lastIndexOf('/') + 1);
                if (code.isEmpty()) throw new Exception("kod alınamadı");
            } catch (Exception e) {
                err = "Yedeklenemedi: " + e.getMessage();
            }
            final String fc = code, fe = err;
            ui.post(() -> r.done(fc, fe));
        }).start();
    }

    static void restore(Context ctx, String code, Result r) {
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            String err = null;
            try {
                String clean = code.trim().replaceAll("[^A-Za-z0-9]", "");
                if (clean.isEmpty()) throw new Exception("kod boş");
                String json = Info.get("https://dpaste.com/" + clean + ".txt");
                Prefs.restore(c, new JSONObject(json));
                Lineup.invalidate();
            } catch (Exception e) {
                err = "Geri yüklenemedi: kod yanlış ya da süresi dolmuş olabilir";
            }
            final String fe = err;
            ui.post(() -> r.done(fe == null ? code : null, fe));
        }).start();
    }
}
