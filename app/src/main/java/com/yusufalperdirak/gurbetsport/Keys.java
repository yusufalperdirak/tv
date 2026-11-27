package com.yusufalperdirak.gurbetsport;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.view.KeyEvent;

import org.json.JSONObject;

import java.util.Locale;

/** Direksiyon / radyo tuslarinin uygulama islevlerine atanmasi. */
final class Keys {
    private Keys() {}

    static final String[] ACTIONS = {"next", "prev", "play", "volup", "voldown", "source", "carplay", "home"};
    static volatile String learning;
    private static final Runnable TIMEOUT = () -> { learning = null; if (Hub.app != null) publish(Hub.app); };

    private static SharedPreferences p(Context c) { return c.getSharedPreferences("keys", Context.MODE_PRIVATE); }

    static void learn(Context c, String action) {
        if (!Perms.accOk(c)) { Perms.grant((android.app.Activity) c, "acc"); return; }
        learning = action;
        Hub.H.removeCallbacks(TIMEOUT);
        Hub.H.postDelayed(TIMEOUT, 15000);
        publish(c);
    }

    static void cancel(Context c) {
        learning = null;
        Hub.H.removeCallbacks(TIMEOUT);
        publish(c);
    }

    static void unlearn(Context c, String action) {
        p(c).edit().remove(action).apply();
        publish(c);
    }

    /** KeyService'ten: ogrenme sirasinda basilan tus. */
    static void assign(Context c, int code) {
        String a = learning;
        if (a == null) return;
        SharedPreferences.Editor e = p(c).edit();
        // Ayni tus baska bir isleve atanmissa oradan kaldir
        for (String x : ACTIONS) if (p(c).getInt(x, -1) == code) e.remove(x);
        e.putInt(a, code).apply();
        learning = null;
        Hub.H.removeCallbacks(TIMEOUT);
        publish(c);
    }

    static String actionFor(Context c, int code) {
        SharedPreferences s = p(c);
        for (String a : ACTIONS) if (s.getInt(a, -1) == code) return a;
        return null;
    }

    static void run(Context c, String a) {
        switch (a) {
            case "next": Media.cmd(c, "next"); break;
            case "prev": Media.cmd(c, "prev"); break;
            case "play": Media.cmd(c, "toggle"); break;
            case "volup": vol(c, AudioManager.ADJUST_RAISE); break;
            case "voldown": vol(c, AudioManager.ADJUST_LOWER); break;
            case "source": Media.toggleSource(c); break;
            case "carplay": CarPlay.launch(c); break;
            case "home": MainActivity.goHome(c); break;
            default:
        }
    }

    private static void vol(Context c, int dir) {
        Hub.safe(() -> {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI);
        });
    }

    static String name(int code) {
        String s = KeyEvent.keyCodeToString(code);
        if (s.startsWith("KEYCODE_")) s = s.substring(8);
        s = s.replace('_', ' ').toLowerCase(Locale.ROOT);
        if (!s.isEmpty()) s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
        return s + " (" + code + ")";
    }

    static void publish(Context c) {
        try {
            JSONObject o = new JSONObject();
            o.put("acc", Perms.accOk(c));
            o.put("learning", learning == null ? JSONObject.NULL : learning);
            JSONObject m = new JSONObject();
            SharedPreferences s = p(c);
            for (String a : ACTIONS) {
                int code = s.getInt(a, -1);
                if (code >= 0) m.put(a, name(code));
            }
            o.put("map", m);
            Hub.put("keys", o);
        } catch (Exception ignored) {}
    }
}
