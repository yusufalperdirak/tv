package com.yusufalperdirak.gurbetsport;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** GitHub'daki update.json'a bakar; yeni surum varsa sorar, indirir ve Android kurulum ekranini acar. */
final class Updater {
    private Updater() {}

    static final String BASE = "https://raw.githubusercontent.com/yusufalperdirak/tv/gurbetsport/";
    private static volatile boolean busy;

    private static SharedPreferences p(Context c) { return c.getSharedPreferences("upd", Context.MODE_PRIVATE); }

    static int code(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 28) return (int) c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) { return 0; }
    }

    /** Acilista ve geri donuste en fazla 3 saatte bir sessizce denetler. */
    static void maybe(Activity a) {
        long now = System.currentTimeMillis();
        if (busy || now - p(a).getLong("at", 0) < 3 * 3600_000L) return;
        p(a).edit().putLong("at", now).apply();
        check(a, false);
    }

    static void check(Activity a, boolean loud) {
        if (busy) return;
        busy = true;
        Context app = a.getApplicationContext();
        new Thread(() -> {
            try {
                JSONObject j = new JSONObject(new String(get(BASE + "update.json?t=" + System.currentTimeMillis()), "UTF-8"));
                int vc = j.optInt("versionCode", 0);
                String name = j.optString("versionName", "");
                String notes = j.optString("notes", "");
                String apk = j.optString("apk", BASE + "GurbetSport.apk");
                Hub.H.post(() -> {
                    busy = false;
                    if (vc <= code(app)) { if (loud) Toast.makeText(app, "GurbetMap Sport güncel", Toast.LENGTH_SHORT).show(); return; }
                    if (a.isFinishing() || a.isDestroyed()) return;
                    Hub.safe(() -> new AlertDialog.Builder(a)
                            .setTitle("Yeni sürüm: " + name)
                            .setMessage(notes.isEmpty() ? "Şimdi kurulsun mu?" : notes + "\n\nŞimdi kurulsun mu?")
                            .setPositiveButton("Kur", (d, w) -> download(app, apk))
                            .setNegativeButton("Sonra", null)
                            .show());
                });
            } catch (Throwable t) {
                Hub.H.post(() -> { busy = false; if (loud) Toast.makeText(app, "Güncelleme denetlenemedi", Toast.LENGTH_SHORT).show(); });
            }
        }, "upd").start();
    }

    private static void download(Context app, String url) {
        Toast.makeText(app, "Güncelleme indiriliyor…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                byte[] apk = get(url + (url.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis());
                PackageInstaller pi = app.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                int id = pi.createSession(sp);
                try (PackageInstaller.Session s = pi.openSession(id)) {
                    try (OutputStream o = s.openWrite("app.apk", 0, apk.length)) { o.write(apk); s.fsync(o); }
                    Intent i = new Intent(app, Done.class);
                    int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    s.commit(PendingIntent.getBroadcast(app, id, i, fl).getIntentSender());
                }
            } catch (Throwable t) {
                Hub.H.post(() -> Toast.makeText(app, "Güncelleme indirilemedi", Toast.LENGTH_LONG).show());
            }
        }, "upd-dl").start();
    }

    static byte[] get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "GurbetMap/11 (Android; arac ekrani)");
        c.setInstanceFollowRedirects(true);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        } finally { c.disconnect(); }
    }

    /** Kurulum oturumunun sonucu: onay gerekiyorsa Android'in kurulum ekranini acar. */
    public static class Done extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) Hub.safe(() -> c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            } else if (st != PackageInstaller.STATUS_SUCCESS) {
                Toast.makeText(c, "Güncelleme kurulamadı", Toast.LENGTH_LONG).show();
            }
        }
    }
}
