package com.turktv.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Dışarıya yönlenen işlemler ve saat. */
final class Actions {
    private Actions() {}

    static final Locale TR = new Locale("tr", "TR");
    private static final TimeZone TR_ZONE = TimeZone.getTimeZone("Europe/Istanbul");

    /** Otomatik açılış için "diğer uygulamaların üzerinde göster" iznini ister. */
    static void requestOverlay(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + a.getPackageName())));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "TV Ayarları › Uygulamalar › Özel uygulama erişimi › "
                    + "Diğer uygulamaların üzerinde göster › TV yolundan izin verin", Toast.LENGTH_LONG).show();
        }
    }

    static boolean hasOverlay(Activity a) {
        return android.os.Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(a);
    }

    /** Yerel saat, "HH:mm". */
    static String localTime() {
        return new SimpleDateFormat("HH:mm", TR).format(new Date());
    }

    /** Türkiye saati; yerel saatle aynıysa null. */
    static String turkeyTime() {
        long now = System.currentTimeMillis();
        if (TimeZone.getDefault().getOffset(now) == TR_ZONE.getOffset(now)) return null;
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", TR);
        f.setTimeZone(TR_ZONE);
        return f.format(new Date(now));
    }

    /** "Salı, 13 Ekim" */
    static String dateText() {
        return new SimpleDateFormat("EEEE, d MMMM", TR).format(new Date());
    }
}
