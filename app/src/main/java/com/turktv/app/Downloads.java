package com.turktv.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/** Tarayıcı indirmeleri: İndirilenler klasörüne kaydeder; APK ise bitince kurmayı önerir. */
final class Downloads {

    private final Activity a;
    private final DownloadManager dm;
    private final Map<Long, String> names = new HashMap<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String[] pending; // izin beklenirken: url, ua, cd, mime, referer
    static final int REQ = 41;

    private final BroadcastReceiver done = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            long id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            String name = names.remove(id);
            if (name == null) return;
            finished(id, name);
        }
    };

    Downloads(Activity a) {
        this.a = a;
        dm = (DownloadManager) a.getSystemService(Context.DOWNLOAD_SERVICE);
        IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) a.registerReceiver(done, f, Context.RECEIVER_EXPORTED);
        else a.registerReceiver(done, f);
    }

    void close() {
        try {
            a.unregisterReceiver(done);
        } catch (Exception ignored) {
        }
    }

    void start(String url, String ua, String cd, String mime, String referer) {
        if (url == null) return;
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            Toast.makeText(a, "Bu dosya sayfanın içinde oluşturuluyor, indirilemiyor", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT < 29 && a.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pending = new String[]{url, ua, cd, mime, referer};
            a.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ);
            return;
        }
        enqueue(url, ua, cd, mime, referer, true);
    }

    /** İzin cevabı geldiğinde (reddedilirse uygulamanın kendi klasörüne indirir). */
    void onPermission(boolean granted) {
        if (pending == null) return;
        String[] p = pending;
        pending = null;
        enqueue(p[0], p[1], p[2], p[3], p[4], granted);
    }

    private void enqueue(String url, String ua, String cd, String mime, String referer, boolean publicDir) {
        try {
            String name = URLUtil.guessFileName(url, cd, mime);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            if (ua != null) r.addRequestHeader("User-Agent", ua);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) r.addRequestHeader("Cookie", cookie);
            if (referer != null) r.addRequestHeader("Referer", referer);
            if (mime != null && !mime.isEmpty()) r.setMimeType(mime);
            r.setTitle(name);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (publicDir) r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            else r.setDestinationInExternalFilesDir(a, Environment.DIRECTORY_DOWNLOADS, name);
            long id = dm.enqueue(r);
            names.put(id, name);
            Toast.makeText(a, "İndiriliyor: " + name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(a, "İndirme başlatılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void finished(long id, String name) {
        int status = DownloadManager.STATUS_FAILED;
        try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (c != null && c.moveToFirst()) status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
        } catch (Exception ignored) {
        }
        if (status != DownloadManager.STATUS_SUCCESSFUL) {
            Toast.makeText(a, "İndirilemedi: " + name, Toast.LENGTH_LONG).show();
            return;
        }
        if (name.toLowerCase(Actions.TR).endsWith(".apk")) {
            if (a.isFinishing()) return;
            new AlertDialog.Builder(a)
                    .setTitle(name + " indirildi")
                    .setMessage("Uygulama şimdi kurulsun mu?")
                    .setPositiveButton("Kur", (d, w) -> install(id, name))
                    .setNegativeButton("Sonra", null)
                    .show();
        } else {
            Toast.makeText(a, name + " indirildi · İndirilenler klasöründe", Toast.LENGTH_LONG).show();
        }
    }

    private void install(long id, String name) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(a, "Kurabilmek için bu izni bir kez aç, sonra dosyayı tekrar indir", Toast.LENGTH_LONG).show();
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
            } catch (Exception ignored) {
            }
            return;
        }
        final Context c = a.getApplicationContext();
        new Thread(() -> {
            String err = null;
            try {
                PackageInstaller pi = c.getPackageManager().getPackageInstaller();
                int sid = pi.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));
                try (PackageInstaller.Session s = pi.openSession(sid)) {
                    try (ParcelFileDescriptor fd = dm.openDownloadedFile(id);
                         InputStream in = new FileInputStream(fd.getFileDescriptor());
                         OutputStream out = s.openWrite(name, 0, fd.getStatSize())) {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        s.fsync(out);
                    }
                    Intent cb = new Intent(c, Updater.InstallReceiver.class);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    s.commit(PendingIntent.getBroadcast(c, sid, cb, flags).getIntentSender());
                }
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String fe = err;
            if (fe != null) ui.post(() -> Toast.makeText(c, "Kurulamadı: " + fe, Toast.LENGTH_LONG).show());
        }).start();
    }
}
