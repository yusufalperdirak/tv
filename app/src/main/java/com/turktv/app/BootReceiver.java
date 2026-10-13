package com.turktv.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Televizyon tamamen kapanıp açıldığında uygulamayı başlatır. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Prefs.autostart(c)) return;
        WakeService.start(c);
        WakeService.launchApp(c);
    }
}
