package com.turktv.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;

/**
 * "TV açılınca otomatik başlat" için arka planda çalışan hafif servis.
 * Televizyon bekleme modundan uyanınca (ekran açılınca) uygulamayı öne getirir.
 * Tamamen kapatılıp açılan televizyonlarda BootReceiver devreye girer.
 */
public class WakeService extends Service {

    private static final String CHANNEL_ID = "autostart";

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Prefs.autostart(c)) launchApp(c);
        }
    };

    static void start(Context c) {
        Intent i = new Intent(c, WakeService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) {
            // Bazı cihazlar arka plandan servis başlatmayı engelleyebilir; uygulama açılınca tekrar denenir.
        }
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, WakeService.class));
    }

    static void launchApp(Context c) {
        // Başlatıcı gibi aç: uygulama zaten açıksa olduğu yerden öne gelir, değilse açılış ekranından başlar
        Intent i = c.getPackageManager().getLeanbackLaunchIntentForPackage(c.getPackageName());
        if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (i == null) i = new Intent(c, SplashActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Otomatik açılış",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        Notification n = b.setSmallIcon(R.drawable.ic_tv)
                .setContentTitle("TV")
                .setContentText("TV açılınca otomatik başlatma etkin")
                .setOngoing(true)
                .build();
        startForeground(1, n);
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.autostart(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(screenOn);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
