package com.yusufalperdirak.gurbetsport;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Radyo ve Bluetooth muzigi Android'in medya oturumlari uzerinden okur ve kontrol eder.
 * Kaynak uygulamanin ekranini hicbir zaman acmaz.
 */
final class Media {
    private Media() {}

    private static MediaSessionManager msm;
    private static ComponentName comp;
    private static final List<MediaController> sessions = new ArrayList<>();
    private static final Map<MediaController, MediaController.Callback> cbs = new HashMap<>();
    private static MediaController current;
    private static String lastSrc = "radio";
    private static final MediaSessionManager.OnActiveSessionsChangedListener LISTENER = list -> onSessions(list);

    static final Pattern FREQ = Pattern.compile("(?<![\\d.,])(8[7-9]|9\\d|10[0-8])[.,](\\d{1,2})(?!\\d)");
    private static final String[] RADIO_HINTS = {"radio", "fmradio", ".fm", "tuner", "radyo"};
    private static final String[] RADIO_PKGS = {"com.syu.radio", "com.microntek.radio", "com.ts.radio", "com.hzbhd.radio",
            "com.android.fmradio", "com.fyt.radio", "com.qf.radio", "com.car.radio", "com.autochips.radio", "com.jcst.radio"};
    private static final String[] BT_HINTS = {"btmusic", "bt.music", "bluetoothmusic", "bluetooth.music", "a2dp", "btaudio", "bt_music"};

    static void attach(MediaWatch svc) {
        detach();
        msm = (MediaSessionManager) svc.getSystemService(Context.MEDIA_SESSION_SERVICE);
        comp = MainActivity.listener(svc);
        if (msm == null) return;
        msm.addOnActiveSessionsChangedListener(LISTENER, comp, Hub.H);
        onSessions(msm.getActiveSessions(comp));
    }

    static void detach() {
        Hub.safe(() -> { if (msm != null) msm.removeOnActiveSessionsChangedListener(LISTENER); });
        for (MediaController c : sessions) {
            MediaController.Callback cb = cbs.get(c);
            if (cb != null) Hub.safe(() -> c.unregisterCallback(cb));
        }
        sessions.clear();
        cbs.clear();
        msm = null;
    }

    /** Bildirim erisimi verildiyse ama servis bagli degilse yeniden bagla. */
    static void ensureConnected(Context c) {
        if (MediaWatch.instance == null && Perms.notifOk(c)) MainActivity.rebindListener(c);
        if (msm != null && comp != null) Hub.safe(() -> onSessions(msm.getActiveSessions(comp)));
    }

    private static void onSessions(List<MediaController> list) {
        for (MediaController c : sessions) {
            MediaController.Callback cb = cbs.get(c);
            if (cb != null) Hub.safe(() -> c.unregisterCallback(cb));
        }
        sessions.clear();
        cbs.clear();
        if (list != null) {
            for (MediaController c : list) {
                if (c == null) continue;
                if (Hub.app != null && Hub.app.getPackageName().equals(c.getPackageName())) continue;
                MediaController.Callback cb = new MediaController.Callback() {
                    @Override public void onPlaybackStateChanged(PlaybackState s) { pickAndPublish(); }
                    @Override public void onMetadataChanged(MediaMetadata m) { pickAndPublish(); }
                    @Override public void onSessionDestroyed() { pickAndPublish(); }
                };
                Hub.safe(() -> c.registerCallback(cb, Hub.H));
                sessions.add(c);
                cbs.put(c, cb);
            }
        }
        pickAndPublish();
    }

    private static boolean playing(MediaController c) {
        try {
            PlaybackState s = c.getPlaybackState();
            return s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING);
        } catch (Exception e) { return false; }
    }

    private static void pickAndPublish() {
        MediaController pick = null;
        for (MediaController c : sessions) if (playing(c)) { pick = c; break; }
        if (pick == null && current != null) {
            for (MediaController c : sessions) if (c.getSessionToken().equals(current.getSessionToken())) { pick = c; break; }
        }
        if (pick == null && !sessions.isEmpty()) {
            // Son secilen kaynak turunu tercih et
            for (MediaController c : sessions) if (lastSrc.equals(kind(c))) { pick = c; break; }
            if (pick == null) pick = sessions.get(0);
        }
        current = pick;
        publish();
    }

    static String kind(MediaController c) {
        String p = c.getPackageName() == null ? "" : c.getPackageName().toLowerCase(Locale.ROOT);
        for (String r : RADIO_PKGS) if (p.equals(r)) return "radio";
        for (String h : RADIO_HINTS) if (p.contains(h)) return "radio";
        String label = label(c.getPackageName()).toLowerCase(Locale.ROOT);
        if (label.contains("radio") || label.contains("radyo")) return "radio";
        try {
            MediaMetadata m = c.getMetadata();
            if (m != null) {
                String t = s(m, MediaMetadata.METADATA_KEY_TITLE) + " " + s(m, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                if (FREQ.matcher(t).find() && !p.contains("blue") && !p.contains("bt")) return "radio";
            }
        } catch (Exception ignored) {}
        return "bt";
    }

    private static String s(MediaMetadata m, String k) {
        try { CharSequence v = m.getText(k); return v == null ? "" : v.toString().trim(); } catch (Exception e) { return ""; }
    }

    private static void publish() {
        try {
            JSONObject o = new JSONObject();
            MediaController c = current;
            if (c == null) { Hub.put("media", o); return; }
            String k = kind(c);
            o.put("src", k);
            o.put("app", label(c.getPackageName()));
            o.put("pkg", c.getPackageName());
            o.put("playing", playing(c));
            MediaMetadata m = c.getMetadata();
            String title = m == null ? "" : first(s(m, MediaMetadata.METADATA_KEY_TITLE), s(m, MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
            String artist = m == null ? "" : first(s(m, MediaMetadata.METADATA_KEY_ARTIST), s(m, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE), s(m, MediaMetadata.METADATA_KEY_ALBUM_ARTIST));
            String album = m == null ? "" : s(m, MediaMetadata.METADATA_KEY_ALBUM);
            if ("radio".equals(k)) {
                String all = title + " " + artist + " " + album;
                Matcher f = FREQ.matcher(all);
                String freq = null;
                if (f.find()) freq = f.group(1) + "." + f.group(2);
                String ps = title;
                if (freq != null && FREQ.matcher(ps).find() && ps.replaceAll("[^A-Za-z]", "").replaceAll("(?i)mhz|fm", "").isEmpty()) ps = "";
                if (freq != null) o.put("freq", freq);
                o.put("ps", ps);
                String rt = artist.equals(ps) ? album : artist;
                if (freq != null && FREQ.matcher(rt).find() && rt.replaceAll("[^A-Za-z]", "").replaceAll("(?i)mhz|fm", "").isEmpty()) rt = "";
                o.put("rt", rt);
            } else {
                o.put("title", title);
                o.put("artist", artist);
                o.put("album", album);
                long dur = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
                if (dur > 0) {
                    o.put("dur", dur);
                    PlaybackState ps = c.getPlaybackState();
                    if (ps != null) {
                        long pos = ps.getPosition();
                        if (ps.getState() == PlaybackState.STATE_PLAYING && ps.getLastPositionUpdateTime() > 0) {
                            pos += (long) ((SystemClock.elapsedRealtime() - ps.getLastPositionUpdateTime()) * ps.getPlaybackSpeed());
                        }
                        o.put("pos", Math.max(0, Math.min(dur, pos)));
                    }
                }
            }
            Hub.put("media", o);
        } catch (Exception ignored) {}
    }

    private static String first(String... v) {
        for (String x : v) if (x != null && !x.isEmpty()) return x;
        return "";
    }

    static String label(String pkg) {
        if (pkg == null || Hub.app == null) return "";
        try {
            PackageManager pm = Hub.app.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence l = pm.getApplicationLabel(ai);
            return l == null ? pkg : l.toString();
        } catch (Exception e) { return pkg; }
    }

    /** Onceki / sonraki / oynat-duraklat. */
    static void cmd(Context ctx, String cmd) {
        MediaController c = current;
        if (c != null) {
            try {
                MediaController.TransportControls t = c.getTransportControls();
                switch (cmd) {
                    case "prev": t.skipToPrevious(); return;
                    case "next": t.skipToNext(); return;
                    case "toggle":
                        if (playing(c)) t.pause(); else t.play();
                        return;
                    default: return;
                }
            } catch (Exception ignored) {}
        }
        int code = "prev".equals(cmd) ? KeyEvent.KEYCODE_MEDIA_PREVIOUS : "next".equals(cmd) ? KeyEvent.KEYCODE_MEDIA_NEXT : KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        mediaKey(ctx, code);
    }

    static void mediaKey(Context ctx, int code) {
        Hub.safe(() -> {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            long now = SystemClock.uptimeMillis();
            am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0));
            am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0));
        });
    }

    static void toggleSource(Context ctx) {
        String k = current == null ? lastSrc : kind(current);
        source(ctx, "radio".equals(k) ? "bt" : "radio");
    }

    /** Radyo veya Bluetooth'a gec. Once mevcut oturumu calistirir; yoksa uygulamayi bir anlik baslatir. */
    static void source(Context ctx, String want) {
        lastSrc = "radio".equals(want) ? "radio" : "bt";
        for (MediaController c : sessions) {
            if (lastSrc.equals(kind(c))) {
                try {
                    if (current != null && current != c && playing(current)) current.getTransportControls().pause();
                    c.getTransportControls().play();
                    current = c;
                    publish();
                    return;
                } catch (Exception ignored) {}
            }
        }
        // Kaynak uygulamanin ekrani ASLA acilmaz: once medya servisine arka planda baglanilir,
        // olmazsa yalnizca o uygulamaya "oynat" tusu gonderilir.
        String pkg = findApp(ctx, lastSrc);
        boolean any = browse(ctx, lastSrc, pkg);
        if (pkg != null) buttonTo(ctx, pkg, KeyEvent.KEYCODE_MEDIA_PLAY);
        if (!any && pkg == null) {
            Toast.makeText(ctx, "radio".equals(lastSrc) ? "Radyo bulunamadı" : "Bluetooth müzik bulunamadı", Toast.LENGTH_SHORT).show();
        }
        Hub.H.postDelayed(() -> { if (msm != null && comp != null) Hub.safe(() -> onSessions(msm.getActiveSessions(comp))); }, 1500);
    }

    private static final Map<String, MediaBrowser> browsers = new HashMap<>();

    /** Radyo / Bluetooth uygulamasinin medya servisine arayuz acmadan baglanir ve calmaya baslatir. */
    private static boolean browse(Context ctx, String kind, String pkg) {
        boolean any = false;
        try {
            List<ResolveInfo> list = ctx.getPackageManager().queryIntentServices(new Intent("android.media.browse.MediaBrowserService"), 0);
            if (list == null) return false;
            for (ResolveInfo r : list) {
                if (r.serviceInfo == null) continue;
                String p = r.serviceInfo.packageName;
                if (p.equals(ctx.getPackageName())) continue;
                boolean match = p.equals(pkg) || ("radio".equals(kind) ? isRadioPkg(p) : isBtPkg(p));
                if (!match) continue;
                any = true;
                ComponentName cn = new ComponentName(p, r.serviceInfo.name);
                String key = cn.flattenToShortString();
                MediaBrowser old = browsers.remove(key);
                if (old != null) Hub.safe(old::disconnect);
                final MediaBrowser[] holder = new MediaBrowser[1];
                holder[0] = new MediaBrowser(ctx.getApplicationContext(), cn, new MediaBrowser.ConnectionCallback() {
                    @Override public void onConnected() {
                        Hub.safe(() -> {
                            MediaController c = new MediaController(ctx.getApplicationContext(), holder[0].getSessionToken());
                            if (current != null && playing(current) && !c.getPackageName().equals(current.getPackageName())) current.getTransportControls().pause();
                            c.getTransportControls().play();
                            current = c;
                            publish();
                        });
                    }
                    @Override public void onConnectionFailed() {}
                    @Override public void onConnectionSuspended() {}
                }, null);
                browsers.put(key, holder[0]);
                Hub.safe(holder[0]::connect);
            }
        } catch (Exception ignored) {}
        return any;
    }

    private static void buttonTo(Context ctx, String pkg, int code) {
        Hub.safe(() -> {
            long now = SystemClock.uptimeMillis();
            for (int a : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
                Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg);
                i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(now, now, a, code, 0));
                ctx.sendBroadcast(i);
            }
        });
    }

    static boolean isRadioPkg(String p) {
        String l = p.toLowerCase(Locale.ROOT);
        for (String r : RADIO_PKGS) if (l.equals(r)) return true;
        for (String h : RADIO_HINTS) if (l.contains(h)) return true;
        return false;
    }

    static boolean isBtPkg(String p) {
        String l = p.toLowerCase(Locale.ROOT);
        if (l.equals("com.android.bluetooth")) return true;
        for (String h : BT_HINTS) if (l.contains(h)) return true;
        return l.contains("bluetooth") && !l.contains("settings");
    }

    static String findApp(Context ctx, String kind) {
        PackageManager pm = ctx.getPackageManager();
        if ("radio".equals(kind)) {
            for (String p : RADIO_PKGS) if (pm.getLaunchIntentForPackage(p) != null) return p;
        }
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps;
        try { apps = pm.queryIntentActivities(q, 0); } catch (Exception e) { return null; }
        String best = null;
        int bestScore = 0;
        for (ResolveInfo r : apps) {
            String p = r.activityInfo.packageName;
            if (p.equals(ctx.getPackageName())) continue;
            String l = String.valueOf(r.loadLabel(pm)).toLowerCase(Locale.ROOT);
            String pl = p.toLowerCase(Locale.ROOT);
            int score = 0;
            if ("radio".equals(kind)) {
                for (String h : RADIO_HINTS) if (pl.contains(h)) score += 2;
                if (l.contains("radio") || l.contains("radyo") || l.equals("fm")) score += 3;
            } else {
                for (String h : BT_HINTS) if (pl.contains(h)) score += 3;
                if ((l.contains("bluetooth") || l.startsWith("bt")) && (l.contains("müzik") || l.contains("music") || l.contains("muzik") || l.contains("audio"))) score += 4;
                if (pl.contains("bluetooth") && pl.contains("music")) score += 2;
            }
            if (score > bestScore) { bestScore = score; best = p; }
        }
        return best;
    }
}
