package com.yusufalperdirak.gurbetsport;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CP-Link (ya da telefondan yansitilan harita uygulamasi) yol tarifini bildirim olarak gosteriyorsa
 * siradaki donusu ve mesafeyi okur. Gostermiyorsa hicbir sey yapmaz; harita yalnizca GPS ile calisir.
 */
final class Nav {
    private Nav() {}

    private static final String[] APPS = {"cplink", "cp.link", "cp_link", "carplay", "zlink", "carlink", "autokit", "autolink",
            "com.google.android.apps.maps", "com.waze", "yandex.navi", "here.app", "sygic", "osmand"};
    private static final Pattern DIST = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s?(km|m)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ETA = Pattern.compile("\\b([01]?\\d|2[0-3]):[0-5]\\d\\b");
    private static String key;
    private static final Runnable EXPIRE = () -> { key = null; off(); };

    static boolean isNavApp(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase(Locale.ROOT);
        for (String a : APPS) if (p.contains(a)) return true;
        return false;
    }

    static void onPosted(Context c, StatusBarNotification sbn) {
        if (sbn == null || !isNavApp(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;
        Bundle e = n.extras;
        String title = str(e.getCharSequence(Notification.EXTRA_TITLE));
        String text = str(e.getCharSequence(Notification.EXTRA_TEXT));
        String big = str(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String sub = str(e.getCharSequence(Notification.EXTRA_SUB_TEXT));
        String all = (title + " \n " + text + " \n " + big + " \n " + sub).trim();
        Matcher d = DIST.matcher(all);
        if (!d.find()) return;
        try {
            String num = d.group(1).replace(',', '.');
            String unit = d.group(2).toLowerCase(Locale.ROOT);
            double v = Double.parseDouble(num);
            int meters = (int) Math.round("km".equals(unit) ? v * 1000 : v);
            String dist = d.group(1) + " " + unit;
            String instr = title.contains(d.group(0)) ? (text.isEmpty() ? big : text) : title;
            if (instr.isEmpty()) instr = all.replace(d.group(0), "").replaceAll("\\s+", " ").trim();
            if (instr.length() > 60) instr = instr.substring(0, 60).trim() + "…";
            String low = all.toLowerCase(new Locale("tr"));
            String turn = low.contains("u dönüş") || low.contains("u-turn") || low.contains("geri dön") ? "uturn"
                    : (low.contains("sol") || low.contains("left")) ? "left"
                    : (low.contains("sağ") || low.contains("sag ") || low.contains("right")) ? "right" : "straight";
            JSONObject o = new JSONObject();
            o.put("on", true);
            o.put("dist", dist);
            o.put("meters", meters);
            o.put("text", instr);
            o.put("turn", turn);
            Matcher t = ETA.matcher(all);
            if (t.find()) o.put("eta", t.group(0));
            o.put("app", Media.label(sbn.getPackageName()));
            key = sbn.getKey();
            Hub.put("nav", o);
            Hub.H.removeCallbacks(EXPIRE);
            Hub.H.postDelayed(EXPIRE, 90_000);
        } catch (Exception ignored) {}
    }

    static void onRemoved(StatusBarNotification sbn) {
        if (sbn != null && sbn.getKey() != null && sbn.getKey().equals(key)) {
            key = null;
            Hub.H.removeCallbacks(EXPIRE);
            off();
        }
    }

    private static void off() {
        try { JSONObject o = new JSONObject(); o.put("on", false); Hub.put("nav", o); } catch (Exception ignored) {}
    }

    private static String str(CharSequence c) { return c == null ? "" : c.toString().trim(); }
}
