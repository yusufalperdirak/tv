package com.yusufalperdirak.gurbetsport;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Unite acilinca uygulamayi baslatir (varsayilan ana ekran secilmemis olsa bile). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (i == null || !Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        if (Perms.homeOk(c)) return; // zaten ana ekran olarak acilacak
        Hub.safe(() -> {
            Intent s = new Intent(c, MainActivity.class);
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(s);
        });
    }
}
