package com.turktv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tek tuşla güncelleme: GitHub'daki son sürümü kontrol eder, indirip kurar. */
final class Updater {
    private Updater() {}

    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static boolean busy;

    private static String repo() {
        return BuildConfig.REPO;
    }

    /** manual=false: sadece yeni sürüm varsa ve kullanıcı bu sürümü ertelemediyse sorar. */
    static void check(final Activity a, final boolean manual) {
        if (repo().isEmpty()) {
            if (manual) Toast.makeText(a, "Güncelleme bu derlemede kapalı", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            int code = -1;
            String name = "";
            try {
                JSONObject o = new JSONObject(Info.get("https://api.github.com/repos/" + repo() + "/releases/tags/latest"));
                String body = o.optString("body", "");
                Matcher m = Pattern.compile("versionCode=(\\d+)").matcher(body);
                if (m.find()) code = Integer.parseInt(m.group(1));
                Matcher n = Pattern.compile("versionName=([\\w.]+)").matcher(body);
                if (n.find()) name = n.group(1);
            } catch (Exception ignored) {
            }
            final int fc = code;
            final String fn = name;
            ui.post(() -> {
                if (a.isFinishing()) return;
                if (fc <= BuildConfig.VERSION_CODE) {
                    if (manual) Toast.makeText(a, fc < 0 ? "Güncelleme kontrol edilemedi" : "Uygulama güncel (sürüm "
                            + BuildConfig.VERSION_NAME + ")", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!manual && Prefs.skippedUpdate(a) == fc) return;
                new AlertDialog.Builder(a)
                        .setTitle("Yeni sürüm hazır" + (fn.isEmpty() ? "" : ": " + fn))
                        .setMessage("Şu an sürüm " + BuildConfig.VERSION_NAME + " kullanıyorsun. "
                                + "Güncelleme indirilip kurulur; ayarların, kanal sıran ve son izlediklerin korunur.")
                        .setPositiveButton("Şimdi güncelle", (d, w) -> install(a))
                        .setNegativeButton("Sonra", (d, w) -> Prefs.skippedUpdate(a, fc))
                        .show();
            });
        }).start();
    }

    static void install(final Activity a) {
        if (busy) return;
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(a, "Güncellemeyi kurabilmek için bu izni bir kez açman gerekiyor, sonra tekrar \"Şimdi güncelle\"ye bas",
                    Toast.LENGTH_LONG).show();
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + a.getPackageName())));
            } catch (Exception ignored) {
            }
            return;
        }
        busy = true;
        Toast.makeText(a, "Güncelleme indiriliyor…", Toast.LENGTH_LONG).show();
        final Context c = a.getApplicationContext();
        new Thread(() -> {
            String err = null;
            try {
                PackageInstaller pi = c.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                sp.setAppPackageName(c.getPackageName());
                int id = pi.createSession(sp);
                try (PackageInstaller.Session s = pi.openSession(id)) {
                    HttpURLConnection con = open("https://github.com/" + repo() + "/releases/download/latest/TV.apk");
                    long len = con.getContentLengthLong();
                    try (InputStream in = con.getInputStream(); OutputStream out = s.openWrite("TV.apk", 0, len > 0 ? len : -1)) {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        s.fsync(out);
                    } finally {
                        con.disconnect();
                    }
                    Intent cb = new Intent(c, InstallReceiver.class);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    PendingIntent p = PendingIntent.getBroadcast(c, id, cb, flags);
                    s.commit(p.getIntentSender());
                }
            } catch (Exception e) {
                err = e.getMessage();
            }
            busy = false;
            final String fe = err;
            if (fe != null) ui.post(() -> Toast.makeText(c, "Güncelleme indirilemedi: " + fe, Toast.LENGTH_LONG).show());
        }).start();
    }

    /** Yönlendirmeleri (github → indirme sunucusu) izleyerek açar. */
    private static HttpURLConnection open(String url) throws Exception {
        for (int i = 0; i < 6; i++) {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(10000);
            con.setReadTimeout(30000);
            con.setRequestProperty("User-Agent", "TV/" + BuildConfig.VERSION_NAME);
            int code = con.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = con.getHeaderField("Location");
                con.disconnect();
                if (loc == null) throw new Exception("yönlendirme hatası");
                url = new URL(new URL(url), loc).toString();
                continue;
            }
            if (code != 200) throw new Exception("HTTP " + code);
            return con;
        }
        throw new Exception("çok fazla yönlendirme");
    }

    /** Kurulum sonucu: kullanıcı onayı gerekiyorsa onay ekranını açar. */
    public static class InstallReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        c.startActivity(confirm);
                    } catch (Exception ignored) {
                    }
                }
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Toast.makeText(c, "Kurulamadı" + (msg != null ? ": " + msg : ""), Toast.LENGTH_LONG).show();
            }
        }
    }
}
