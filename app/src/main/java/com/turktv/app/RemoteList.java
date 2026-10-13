package com.turktv.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Kanal listesini internetten günceller (GitHub deposundaki channels.json).
 * Adres derleme sırasında BuildConfig.CHANNELS_URL olarak gelir; boşsa özellik kapalıdır.
 */
final class RemoteList {
    private RemoteList() {}

    interface Result {
        /** changed: liste değişti mi; error: hata mesajı ya da null */
        void done(boolean changed, String error);
    }

    static boolean enabled() {
        return BuildConfig.CHANNELS_URL != null && !BuildConfig.CHANNELS_URL.isEmpty();
    }

    static void update(Context ctx, Result cb) {
        final Context app = ctx.getApplicationContext();
        final Handler ui = new Handler(Looper.getMainLooper());
        if (!enabled()) {
            ui.post(() -> cb.done(false, "Uzaktan liste adresi ayarlanmamış"));
            return;
        }
        new Thread(() -> {
            boolean changed = false;
            String error = null;
            HttpURLConnection con = null;
            try {
                URL url = new URL(BuildConfig.CHANNELS_URL + "?t=" + System.currentTimeMillis());
                con = (HttpURLConnection) url.openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(10000);
                con.setUseCaches(false);
                if (con.getResponseCode() != 200) throw new Exception("HTTP " + con.getResponseCode());
                String json = Channel.readAll(con.getInputStream());
                if (Channel.parse(json).isEmpty()) throw new Exception("Liste boş");

                File f = new File(app.getFilesDir(), Channel.LOCAL_FILE);
                String old = null;
                if (f.exists()) {
                    try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                        old = Channel.readAll(in);
                    }
                } else {
                    try (java.io.InputStream in = app.getAssets().open(Channel.LOCAL_FILE)) {
                        old = Channel.readAll(in);
                    }
                }
                if (!json.equals(old)) {
                    File tmp = new File(app.getFilesDir(), Channel.LOCAL_FILE + ".tmp");
                    try (FileOutputStream out = new FileOutputStream(tmp)) {
                        out.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                    if (!tmp.renameTo(f)) throw new Exception("Kaydedilemedi");
                    Channel.invalidate();
                    changed = true;
                }
                Prefs.listUpdated(app, System.currentTimeMillis());
            } catch (Exception e) {
                error = "Güncellenemedi: " + e.getMessage();
            } finally {
                if (con != null) con.disconnect();
            }
            final boolean ch = changed;
            final String err = error;
            ui.post(() -> cb.done(ch, err));
        }).start();
    }
}
