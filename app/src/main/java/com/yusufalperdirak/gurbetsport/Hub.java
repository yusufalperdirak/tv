package com.yusufalperdirak.gurbetsport;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

/**
 * Uygulamanin tek durum merkezi. Yerel taraf (medya, GPS, hava, tuslar...) degerleri buraya yazar;
 * Hub degisenleri toplayip en fazla ~8 kez/sn arayuze tek bir JSON olarak gonderir.
 * Butun erisim ana is parcaciginda yapilir.
 */
final class Hub {
    static final Handler H = new Handler(Looper.getMainLooper());
    static Context app;

    interface Sink { void send(String json); }

    private static Sink sink;
    private static final Map<String, Object> state = new HashMap<>();
    private static final Set<String> dirty = new LinkedHashSet<>();
    private static boolean scheduled;

    private Hub() {}

    static void init(Context c) {
        if (app == null) app = c.getApplicationContext();
    }

    static void setSink(Sink s) {
        runMain(() -> {
            sink = s;
            if (s != null) { dirty.addAll(state.keySet()); schedule(); }
        });
    }

    /** Bir alani gunceller (herhangi bir is parcacigindan cagrilabilir). */
    static void put(String key, Object value) {
        runMain(() -> {
            Object old = state.get(key);
            if (old != null && value != null && old.toString().equals(value.toString())) return;
            state.put(key, value);
            dirty.add(key);
            schedule();
        });
    }

    static Object get(String key) { return state.get(key); }

    /** Arayuz yeniden yuklendiginde butun durumu tekrar gonder. */
    static void resendAll() {
        runMain(() -> { dirty.addAll(state.keySet()); schedule(); });
    }

    private static void schedule() {
        if (scheduled) return;
        scheduled = true;
        H.postDelayed(FLUSH, 120);
    }

    private static final Runnable FLUSH = () -> {
        scheduled = false;
        if (sink == null || dirty.isEmpty()) return;
        try {
            JSONObject o = new JSONObject();
            for (String k : dirty) {
                Object v = state.get(k);
                o.put(k, v == null ? JSONObject.NULL : v);
            }
            dirty.clear();
            sink.send(o.toString());
        } catch (Exception ignored) {
            dirty.clear();
        }
    };

    static void runMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run(); else H.post(r);
    }

    static void safe(Runnable r) {
        try { r.run(); } catch (Throwable ignored) {}
    }
}
