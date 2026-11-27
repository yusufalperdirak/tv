package com.yusufalperdirak.gurbetsport;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;

import org.json.JSONObject;

/** Ekran unitesinin GPS'i: hiz, yon, rakim, uydu sayisi. CarPlay de ayni GPS'i kullanir. */
final class Loc {
    private Loc() {}

    private static boolean started;
    private static LocationManager lm;
    private static int sats = -1;
    private static Location last;
    private static long lastFixAt;

    private static final LocationListener L = new LocationListener() {
        @Override public void onLocationChanged(Location l) { onFix(l); }
        @Override public void onStatusChanged(String p, int s, Bundle b) {}
        @Override public void onProviderEnabled(String p) {}
        @Override public void onProviderDisabled(String p) {}
    };

    private static final GnssStatus.Callback G = new GnssStatus.Callback() {
        @Override public void onSatelliteStatusChanged(GnssStatus s) {
            int used = 0;
            for (int i = 0; i < s.getSatelliteCount(); i++) if (s.usedInFix(i)) used++;
            if (used != sats) { sats = used; publish(); }
        }
    };

    static boolean granted(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    static void start(Context ctx) {
        if (started || !granted(ctx)) { if (!started) publish(); return; }
        Context c = ctx.getApplicationContext();
        lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;
        started = true;
        Hub.safe(() -> {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, L, Hub.H.getLooper());
        });
        Hub.safe(() -> {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 15000, 50, L, Hub.H.getLooper());
        });
        Hub.safe(() -> lm.registerGnssStatusCallback(G, Hub.H));
        Hub.safe(() -> {
            Location a = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location b = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location best = a != null ? a : b;
            if (best != null) { last = best; Weather.maybe(c, best); }
        });
        // Fix gelmezse arayuzde "GPS bekleniyor" gostermek icin periyodik kontrol
        Hub.H.postDelayed(WATCH, 5000);
        publish();
    }

    private static final Runnable WATCH = new Runnable() {
        @Override public void run() {
            publish();
            Hub.H.postDelayed(this, 5000);
        }
    };

    private static void onFix(Location l) {
        if (l == null) return;
        // Ag konumu, taze GPS konumunun ustune yazmasin
        if (LocationManager.NETWORK_PROVIDER.equals(l.getProvider()) && SystemClock.elapsedRealtime() - lastFixAt < 10000) return;
        if (LocationManager.GPS_PROVIDER.equals(l.getProvider())) lastFixAt = SystemClock.elapsedRealtime();
        last = l;
        publish();
        if (Hub.app != null) Weather.maybe(Hub.app, l);
    }

    static Location last() { return last; }

    private static void publish() {
        try {
            JSONObject o = new JSONObject();
            boolean fresh = last != null && SystemClock.elapsedRealtime() - lastFixAt < 8000;
            o.put("ok", fresh);
            if (sats >= 0) o.put("sats", sats);
            if (fresh) {
                o.put("speed", last.hasSpeed() ? Math.round(last.getSpeed() * 10) / 10.0 : 0);
                if (last.hasBearing()) o.put("bearing", Math.round(last.getBearing()));
                if (last.hasAltitude()) o.put("alt", Math.round(last.getAltitude()));
            }
            Hub.put("loc", o);
        } catch (Exception ignored) {}
    }
}
