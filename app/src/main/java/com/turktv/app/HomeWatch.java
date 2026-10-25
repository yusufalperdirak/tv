package com.turktv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.widget.Toast;

import java.util.Set;

/**
 * Ana ekran tuşu yönlendirmesi, erişilebilirlik hizmeti olmayan TV'ler için:
 * "Kullanım erişimi" izniyle hangi uygulamanın öne geldiği izlenir; TV'nin kendi ana ekranı
 * öne gelince bu uygulamanın ana ekranı açılır ("Diğer uygulamaların üzerinde göster" izni de gerekir).
 */
final class HomeWatch {
    private HomeWatch() {}

    static boolean hasUsage(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean canOverlay(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    /** Ana ekran tuşu bağlı mı (iki yoldan biriyle). */
    static boolean active(Context c) {
        return HomeService.enabled(c) || (hasUsage(c) && canOverlay(c));
    }

    // ------------------------------------------------------------------ izleme (WakeService içinden)

    private static Set<String> launchers;
    private static long lastQuery, lastJump;

    /** Son kontrolden beri TV'nin ana ekranı öne geldiyse bizim ana ekranı açar. */
    static void poll(Context c) {
        if (!hasUsage(c)) return;
        if (launchers == null) launchers = HomeService.findLaunchers(c);
        long now = System.currentTimeMillis();
        long from = lastQuery == 0 ? now - 3000 : lastQuery - 200;
        lastQuery = now;
        try {
            UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            UsageEvents ev = usm.queryEvents(from, now);
            UsageEvents.Event e = new UsageEvents.Event();
            String lastPkg = null;
            long lastAt = 0;
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastPkg = e.getPackageName();
                    lastAt = e.getTimeStamp();
                }
            }
            if (lastPkg != null && launchers.contains(lastPkg) && lastAt > lastJump && now - lastJump > 1200) {
                lastJump = now;
                Intent i = new Intent(c, MainActivity.class);
                i.putExtra("home", true);
                i.putExtra("fromSplash", false);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                c.startActivity(i);
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ kurulum

    static void showSetup(final Activity a) {
        new AlertDialog.Builder(a)
                .setTitle("Ana ekran tuşunu bağla")
                .setMessage("Kumandadaki ana ekran tuşuna basınca bu uygulama açılsın. İki yol var:\n\n"
                        + "1) Kullanım erişimi (önerilen, çoğu TV'de çalışır): açılan listede \"TV\"yi bul ve izin ver.\n\n"
                        + "2) Erişilebilirlik: listede \"TV ana ekran tuşu\"nu aç.\n\n"
                        + "Birini yapman yeterli.")
                .setPositiveButton("Kullanım erişimi", (d, w) -> openUsage(a))
                .setNeutralButton("Erişilebilirlik", (d, w) -> HomeService.openSettings(a))
                .setNegativeButton("Sonra", null)
                .show();
    }

    static void openUsage(final Activity a) {
        if (!canOverlay(a)) {
            Toast.makeText(a, "Önce \"Diğer uygulamaların üzerinde göster\" iznini ver, sonra tekrar dene",
                    Toast.LENGTH_LONG).show();
            Actions.requestOverlay(a);
            return;
        }
        Intent[] tries = {
                new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + a.getPackageName())),
                new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                new Intent().setClassName("com.android.tv.settings",
                        "com.android.tv.settings.device.apps.specialaccess.UsageAccessActivity"),
                new Intent().setClassName("com.android.tv.settings",
                        "com.android.tv.settings.device.apps.specialaccess.SpecialAppAccessActivity"),
        };
        for (Intent i : tries) {
            try {
                if (i.resolveActivity(a.getPackageManager()) == null) continue;
                a.startActivity(i);
                Toast.makeText(a, "Listede \"TV\"yi bul ve izin ver", Toast.LENGTH_LONG).show();
                WakeService.start(a);
                return;
            } catch (Exception ignored) {
            }
        }
        new AlertDialog.Builder(a)
                .setTitle("Kullanım erişimini elle aç")
                .setMessage("Şu yolu izle:\n\nTV Ayarları › Uygulamalar › Özel uygulama erişimi › Kullanım erişimi › TV › İzin ver")
                .setPositiveButton("TV ayarlarını aç", (d, w) -> {
                    try {
                        a.startActivity(new Intent(Settings.ACTION_SETTINGS));
                    } catch (Exception e) {
                        Toast.makeText(a, "TV ayarları açılamadı", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Kapat", null)
                .show();
    }
}
