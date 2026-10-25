package com.turktv.app;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ana ekran tuşu yönlendirmesi. TV'de ana ekranı değiştirme seçeneği olmadığı için:
 * TV'nin kendi ana ekranı öne geldiği anda bu uygulamanın ana ekranı açılır.
 * Ekran içeriği okunmaz; yalnızca hangi uygulamanın öne geldiğine bakılır.
 */
public class HomeService extends AccessibilityService {

    private Set<String> launchers = new HashSet<>();
    private long lastJump;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        launchers = findLaunchers(this);
    }

    static Set<String> findLaunchers(Context c) {
        Set<String> out = new HashSet<>();
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> l = c.getPackageManager().queryIntentActivities(home, 0);
            for (ResolveInfo r : l) {
                if (r.activityInfo != null && !r.activityInfo.packageName.equals(c.getPackageName())) {
                    out.add(r.activityInfo.packageName);
                }
            }
        } catch (Exception ignored) {
        }
        // bilinen TV ana ekranları (listede görünmese bile)
        out.add("com.google.android.tvlauncher");
        out.add("com.google.android.apps.tv.launcherx");
        out.add("com.google.android.leanbacklauncher");
        out.add("com.android.tv.launcher");
        out.remove("com.android.settings");
        out.remove("com.android.tv.settings");
        return out;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = e.getPackageName();
        if (pkg == null || !launchers.contains(pkg.toString())) return;
        long now = System.currentTimeMillis();
        if (now - lastJump < 800) return;
        lastJump = now;
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("home", true);
        i.putExtra("fromSplash", false);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onInterrupt() {
    }

    /** Hizmet açık mı. */
    static boolean enabled(Context c) {
        try {
            String list = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(list)) return false;
            ComponentName me = new ComponentName(c, HomeService.class);
            for (String s : list.split(":")) {
                ComponentName cn = ComponentName.unflattenFromString(s);
                if (me.equals(cn)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Erişilebilirlik ayarlarını açar. Bazı TV'lerde standart sayfa yok; sırayla bilinen sayfalar denenir,
     * hiçbiri yoksa yol tarif edilir.
     */
    static void openSettings(Activity a) {
        Intent[] tries = {
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                new Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.accessibility.AccessibilityActivity"),
                new Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.device.accessibility.AccessibilityActivity"),
                new Intent().setClassName("com.android.settings", "com.android.settings.Settings$AccessibilitySettingsActivity"),
                new Intent("android.settings.ACCESSIBILITY_SETTINGS_FOR_SUW"),
        };
        for (Intent i : tries) {
            try {
                if (i.resolveActivity(a.getPackageManager()) == null) continue;
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(i);
                Toast.makeText(a, "Listede \"TV ana ekran tuşu\"nu bul ve aç", Toast.LENGTH_LONG).show();
                return;
            } catch (Exception ignored) {
            }
        }
        new android.app.AlertDialog.Builder(a)
                .setTitle("Erişilebilirlik ayarını elle aç")
                .setMessage("Bu TV erişilebilirlik sayfasını doğrudan açmaya izin vermiyor. Şu yolu izle:\n\n"
                        + "TV Ayarları › Cihaz Tercihleri (bazı TV'lerde Sistem) › Erişilebilirlik › "
                        + "\"TV ana ekran tuşu\" › Aç\n\n"
                        + "TV ayarlarını şimdi açmak için \"TV ayarlarını aç\"a bas.")
                .setPositiveButton("TV ayarlarını aç", (d, w) -> {
                    try {
                        a.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception e) {
                        Toast.makeText(a, "TV ayarları açılamadı", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Kapat", null)
                .show();
    }
}
