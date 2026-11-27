package com.yusufalperdirak.gurbetsport;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Hava durumu: GPS konumuna gore Open-Meteo'dan (anahtarsiz, ucretsiz). 20 dk'da bir ya da 5 km yer degisince. */
final class Weather {
    private Weather() {}

    private static volatile boolean busy;
    private static long lastAt;
    private static long failAt;
    private static Location lastLoc;

    static void loadCached(Context c) {
        SharedPreferences p = c.getSharedPreferences("wx", Context.MODE_PRIVATE);
        String j = p.getString("last", null);
        if (j != null) {
            try { Hub.put("wx", new JSONObject(j)); } catch (Exception ignored) {}
        }
    }

    static void maybe(Context ctx, Location l) {
        if (l == null) l = Loc.last();
        if (l == null || busy) return;
        long now = SystemClock.elapsedRealtime();
        boolean moved = lastLoc == null || lastLoc.distanceTo(l) > 5000;
        boolean old = lastAt == 0 || now - lastAt > 20 * 60 * 1000L;
        boolean cooled = failAt == 0 || now - failAt > 2 * 60 * 1000L;
        if (!(moved || old) || !cooled) return;
        busy = true;
        final Location loc = l;
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                String u = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&timezone=auto",
                        loc.getLatitude(), loc.getLongitude());
                JSONObject r = new JSONObject(get(u));
                JSONObject cur = r.getJSONObject("current");
                JSONObject o = new JSONObject();
                o.put("t", cur.getDouble("temperature_2m"));
                int code = cur.optInt("weather_code", -1);
                o.put("code", code);
                o.put("txt", text(code));
                String city = city(c, loc);
                if (city != null && !city.isEmpty()) o.put("city", city);
                Hub.put("wx", o);
                c.getSharedPreferences("wx", Context.MODE_PRIVATE).edit().putString("last", o.toString()).apply();
                lastAt = SystemClock.elapsedRealtime();
                lastLoc = loc;
                failAt = 0;
            } catch (Throwable t) {
                failAt = SystemClock.elapsedRealtime();
            } finally {
                busy = false;
            }
        }, "hava").start();
    }

    private static String get(String u) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        h.setConnectTimeout(8000);
        h.setReadTimeout(8000);
        h.setRequestProperty("User-Agent", "GurbetMapSport");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(h.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            h.disconnect();
        }
    }

    @SuppressWarnings("deprecation")
    private static String city(Context c, Location l) {
        try {
            if (!Geocoder.isPresent()) return null;
            List<Address> a = new Geocoder(c, new Locale("tr")).getFromLocation(l.getLatitude(), l.getLongitude(), 1);
            if (a == null || a.isEmpty()) return null;
            Address x = a.get(0);
            String s = x.getLocality();
            if (s == null || s.isEmpty()) s = x.getSubAdminArea();
            if (s == null || s.isEmpty()) s = x.getAdminArea();
            return s;
        } catch (Throwable t) {
            return null;
        }
    }

    static String text(int c) {
        if (c == 0) return "açık";
        if (c == 1) return "az bulutlu";
        if (c == 2) return "parçalı bulutlu";
        if (c == 3) return "bulutlu";
        if (c == 45 || c == 48) return "sisli";
        if (c >= 51 && c <= 57) return "çiseliyor";
        if (c >= 61 && c <= 67) return "yağmurlu";
        if (c >= 71 && c <= 77) return "karlı";
        if (c >= 80 && c <= 82) return "sağanak";
        if (c == 85 || c == 86) return "kar sağanağı";
        if (c >= 95) return "fırtınalı";
        return "";
    }
}
