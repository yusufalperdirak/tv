package com.yusufalperdirak.gurbetsport;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/** Izinlerin durumu ve verilmesi. */
final class Perms {
    private Perms() {}

    static final String[] IDS = {"loc", "bt", "notif", "acc", "overlay", "battery", "home"};

    static boolean notifOk(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(c.getPackageName() + "/");
    }

    static boolean accOk(Context c) {
        try {
            if (Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false;
            String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (s == null) return false;
            String me = new ComponentName(c, KeyService.class).flattenToString();
            String meShort = new ComponentName(c, KeyService.class).flattenToShortString();
            TextUtils.SimpleStringSplitter sp = new TextUtils.SimpleStringSplitter(':');
            sp.setString(s);
            while (sp.hasNext()) {
                String x = sp.next();
                if (x.equalsIgnoreCase(me) || x.equalsIgnoreCase(meShort)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    static boolean btOk(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        return c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean overlayOk(Context c) { return Settings.canDrawOverlays(c); }

    static boolean batteryOk(Context c) {
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
        } catch (Exception e) { return true; }
    }

    static boolean homeOk(Context c) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo r = c.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            return r != null && r.activityInfo != null && c.getPackageName().equals(r.activityInfo.packageName);
        } catch (Exception e) { return false; }
    }

    static boolean ok(Context c, String id) {
        switch (id) {
            case "loc": return Loc.granted(c);
            case "bt": return btOk(c);
            case "notif": return notifOk(c);
            case "acc": return accOk(c);
            case "overlay": return overlayOk(c);
            case "battery": return batteryOk(c);
            case "home": return homeOk(c);
            default: return true;
        }
    }

    static void refresh(Context c) {
        try {
            JSONArray a = new JSONArray();
            for (String id : IDS) {
                JSONObject o = new JSONObject();
                o.put("id", id);
                o.put("ok", ok(c, id));
                a.put(o);
            }
            Hub.put("perms", a);
        } catch (Exception ignored) {}
    }

    static void grant(Activity a, String id) {
        switch (id) {
            case "loc":
                a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
                Hub.H.postDelayed(() -> { if (!Loc.granted(a)) start(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, MainActivity.pkgUri(a))); }, 1500);
                break;
            case "bt":
                if (Build.VERSION.SDK_INT >= 31) a.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 2);
                break;
            case "notif":
                start(a, new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                break;
            case "acc":
                start(a, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                break;
            case "overlay":
                start(a, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, MainActivity.pkgUri(a)));
                break;
            case "battery":
                start(a, new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, MainActivity.pkgUri(a)));
                break;
            case "home":
                if (!start(a, new Intent(Settings.ACTION_HOME_SETTINGS))) {
                    Intent chooser = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
                    start(a, Intent.createChooser(chooser, "Ana ekran uygulaması"));
                }
                break;
            default:
        }
    }

    /** Ayar ekranini acar; unitede o ekran yoksa uygulama bilgileri, o da yoksa genel ayarlar. */
    static boolean start(Context c, Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); return true; } catch (Exception ignored) {}
        try {
            c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, MainActivity.pkgUri(c)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Toast.makeText(c, "Bu ünitede ilgili ayar ekranı yok; uygulama ayarları açıldı", Toast.LENGTH_LONG).show();
            return false;
        } catch (Exception ignored) {}
        try { c.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
        return false;
    }
}
