package com.yusufalperdirak.gurbetsport;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Bildirim erisimi servisi. Iki is yapar:
 * 1) Calan medya oturumlarina (radyo, Bluetooth muzik, CP-Link) erisim saglar.
 * 2) CP-Link / harita bildirimlerinde yol tarifi varsa okur.
 */
public class MediaWatch extends NotificationListenerService {
    static volatile MediaWatch instance;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        Hub.init(this);
        Hub.safe(() -> Media.attach(this));
        Hub.safe(() -> {
            StatusBarNotification[] all = getActiveNotifications();
            if (all != null) for (StatusBarNotification n : all) Nav.onPosted(this, n);
        });
        Perms.refresh(this);
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        Hub.safe(Media::detach);
        MainActivity.rebindListener(this);
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Hub.safe(() -> Nav.onPosted(this, sbn));
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Hub.safe(() -> Nav.onRemoved(sbn));
    }
}
