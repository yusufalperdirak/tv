package com.turktv.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ana ekrandaki küçük bilgiler: hava durumu, döviz kuru, ezan vakitleri.
 * Konum IP'den bulunur; sonuçlar önbellekte tutulur, internet yoksa son bilinenler gösterilir.
 */
final class Info {
    private Info() {}

    interface Callback {
        void done();
    }

    private static final ExecutorService pool = Executors.newSingleThreadExecutor();
    private static final Handler ui = new Handler(Looper.getMainLooper());

    // ------------------------------------------------------------------ okuma (önbellekten)

    static final class Weather {
        double temp;
        int code;
        boolean day;
    }

    static Weather weather(Context c) {
        try {
            String raw = Prefs.cache(c, "weather");
            if (raw == null) return null;
            JSONObject cur = new JSONObject(raw).getJSONObject("current");
            Weather w = new Weather();
            w.temp = cur.getDouble("temperature_2m");
            w.code = cur.getInt("weather_code");
            w.day = cur.optInt("is_day", 1) == 1;
            return w;
        } catch (Exception e) {
            return null;
        }
    }

    static String city(Context c) {
        try {
            String raw = Prefs.cache(c, "loc");
            return raw == null ? null : new JSONObject(raw).optString("city", null);
        } catch (Exception e) {
            return null;
        }
    }

    /** {EUR→TRY, USD→TRY} ya da null */
    static double[] rates(Context c) {
        try {
            String raw = Prefs.cache(c, "rates");
            if (raw == null) return null;
            JSONObject o = new JSONObject(raw);
            return new double[]{o.getDouble("eur"), o.getDouble("usd")};
        } catch (Exception e) {
            return null;
        }
    }

    private static final String[] PRAYER_KEYS = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private static final String[] PRAYER_NAMES = {"İmsak", "Güneş", "Öğle", "İkindi", "Akşam", "Yatsı"};

    /** Sıradaki vakit: {"Akşam", "17:42"} ya da null */
    static String[] nextPrayer(Context c) {
        try {
            String raw = Prefs.cache(c, "prayer");
            if (raw == null) return null;
            JSONObject o = new JSONObject(raw);
            if (!o.optString("date").equals(today())) return null;
            JSONObject t = o.getJSONObject("timings");
            Calendar now = Calendar.getInstance();
            int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            for (int i = 0; i < PRAYER_KEYS.length; i++) {
                String hm = t.getString(PRAYER_KEYS[i]).substring(0, 5);
                int m = Integer.parseInt(hm.substring(0, 2)) * 60 + Integer.parseInt(hm.substring(3, 5));
                if (m > nowMin) return new String[]{PRAYER_NAMES[i], hm};
            }
            return new String[]{PRAYER_NAMES[0], t.getString(PRAYER_KEYS[0]).substring(0, 5)};
        } catch (Exception e) {
            return null;
        }
    }

    static String label(int code) {
        if (code == 0) return "Açık";
        if (code <= 2) return "Parçalı bulutlu";
        if (code == 3) return "Bulutlu";
        if (code == 45 || code == 48) return "Sisli";
        if (code >= 51 && code <= 57) return "Çiseleme";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "Yağmurlu";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "Karlı";
        if (code >= 95) return "Fırtınalı";
        return "";
    }

    static int icon(Weather w) {
        int code = w.code;
        if (code <= 1) return w.day ? R.drawable.ic_w_sun : R.drawable.ic_w_moon;
        if (code <= 3) return R.drawable.ic_w_cloud;
        if (code == 45 || code == 48) return R.drawable.ic_w_fog;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.ic_w_snow;
        if (code >= 95) return R.drawable.ic_w_storm;
        if (code >= 51) return R.drawable.ic_w_rain;
        return R.drawable.ic_w_cloud;
    }

    // ------------------------------------------------------------------ yenileme

    private static boolean running;

    /** Eskimiş bilgileri arka planda yeniler; bir şey değiştiyse cb çağrılır. */
    static void refresh(Context ctx, Callback cb) {
        final Context c = ctx.getApplicationContext();
        if (running) return;
        running = true;
        pool.execute(() -> {
            boolean changed = false;
            long now = System.currentTimeMillis();
            try {
                if (Prefs.cache(c, "loc") == null || now - Prefs.cacheAt(c, "loc") > 24 * 3600_000L) {
                    String s = get("https://ipwho.is/?fields=success,city,latitude,longitude,country_code");
                    JSONObject o = new JSONObject(s);
                    if (o.optBoolean("success", false)) {
                        Prefs.cache(c, "loc", o.toString());
                        changed = true;
                    }
                }
            } catch (Exception ignored) {
            }
            JSONObject loc = null;
            try {
                loc = new JSONObject(Prefs.cache(c, "loc"));
            } catch (Exception ignored) {
            }
            if (loc != null) {
                double lat = loc.optDouble("latitude"), lon = loc.optDouble("longitude");
                String ll = String.format(Locale.US, "latitude=%.4f&longitude=%.4f", lat, lon);
                try {
                    if (now - Prefs.cacheAt(c, "weather") > 30 * 60_000L) {
                        String s = get("https://api.open-meteo.com/v1/forecast?" + ll
                                + "&current=temperature_2m,weather_code,is_day"
                                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                                + "&hourly=temperature_2m,weather_code&forecast_hours=12"
                                + "&timezone=auto&forecast_days=6");
                        new JSONObject(s).getJSONObject("current");
                        Prefs.cache(c, "weather", s);
                        changed = true;
                    }
                } catch (Exception ignored) {
                }
                try {
                    String cached = Prefs.cache(c, "prayer");
                    String date = today();
                    if (cached == null || !new JSONObject(cached).optString("date").equals(date)) {
                        String d = new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date());
                        String s = get("https://api.aladhan.com/v1/timings/" + d + "?" + ll + "&method=13");
                        JSONObject t = new JSONObject(s).getJSONObject("data").getJSONObject("timings");
                        JSONObject o = new JSONObject();
                        o.put("date", date);
                        o.put("timings", t);
                        Prefs.cache(c, "prayer", o.toString());
                        changed = true;
                    }
                } catch (Exception ignored) {
                }
            }
            try {
                if (now - Prefs.cacheAt(c, "rates") > 3 * 3600_000L) {
                    double eur, usd;
                    try {
                        JSONObject r = new JSONObject(get("https://api.frankfurter.app/latest?from=EUR&to=TRY,USD"))
                                .getJSONObject("rates");
                        eur = r.getDouble("TRY");
                        usd = eur / r.getDouble("USD");
                    } catch (Exception e) {
                        JSONObject r = new JSONObject(get("https://open.er-api.com/v6/latest/EUR")).getJSONObject("rates");
                        eur = r.getDouble("TRY");
                        usd = eur / r.getDouble("USD");
                    }
                    JSONObject o = new JSONObject();
                    o.put("eur", eur);
                    o.put("usd", usd);
                    Prefs.cache(c, "rates", o.toString());
                    changed = true;
                }
            } catch (Exception ignored) {
            }
            final boolean ch = changed;
            ui.post(() -> {
                running = false;
                if (ch && cb != null) cb.done();
            });
        });
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(8000);
            con.setReadTimeout(10000);
            con.setRequestProperty("User-Agent", "TV/" + BuildConfig.VERSION_NAME + " (Android TV)");
            con.setRequestProperty("Accept", "application/json");
            int code = con.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            try (InputStream in = con.getInputStream()) {
                return Channel.readAll(in);
            }
        } finally {
            con.disconnect();
        }
    }
}
