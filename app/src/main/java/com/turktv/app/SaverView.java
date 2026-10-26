package com.turktv.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Ekran koruyucu görüntüsü: İstanbul fotoğrafı yavaşça yakınlaşıp kayar (AerialView),
 * üstünde büyük ince saat, tarih ve hava. Yazılar dakikada bir yer değiştirir (ekran izi kalmasın).
 */
final class SaverView extends FrameLayout {
    private final AerialView aerial;
    private final LinearLayout box;
    private final TextView time, date;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private int corner;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            ui.postDelayed(this, 1000 * (60 - new Date().getSeconds() % 60) + 50);
        }
    };

    SaverView(Context c) {
        super(c);
        setBackgroundColor(0xFF000000);
        aerial = new AerialView(c);
        addView(aerial, new LayoutParams(-1, -1));
        View shade = new View(c);
        shade.setBackgroundColor(0x40000000);
        addView(shade, new LayoutParams(-1, -1));

        box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        time = Ui.text(c, "", 120, Ui.THIN, 0xFFFFFFFF);
        time.setIncludeFontPadding(false);
        box.addView(time);
        date = Ui.text(c, "", 26, Ui.REGULAR, 0xFFE6E9EE);
        box.addView(date, Ui.margins(c, -2, -2, 6, 6, 0, 0));
        addView(box, new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START));
    }

    void start() {
        aerial.start();
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    void stop() {
        aerial.stop();
        ui.removeCallbacks(tick);
    }

    private void render() {
        Date now = new Date();
        time.setText(new SimpleDateFormat("HH:mm", Locale.US).format(now));
        String d = new SimpleDateFormat("d MMMM EEEE", new Locale("tr")).format(now);
        Info.Weather w = Info.weather(getContext());
        if (w != null) d += "  ·  " + Math.round(w.temp) + "°  " + Info.label(w.code);
        date.setText(d);
        // her dakika köşe değiştir: sol alt → sağ alt → sağ üst → sol üst
        corner = (corner + 1) % 4;
        int g = (corner == 0 ? Gravity.BOTTOM | Gravity.START : corner == 1 ? Gravity.BOTTOM | Gravity.END
                : corner == 2 ? Gravity.TOP | Gravity.END : Gravity.TOP | Gravity.START);
        LayoutParams lp = (LayoutParams) box.getLayoutParams();
        lp.gravity = g;
        int mx = Ui.px(getContext(), 88), my = Ui.px(getContext(), 56);
        lp.setMargins(mx, my, mx, my);
        box.setGravity((g & Gravity.END) == Gravity.END ? Gravity.END : Gravity.START);
        box.animate().alpha(0f).setDuration(400).withEndAction(() -> {
            box.setLayoutParams(lp);
            box.animate().alpha(1f).setDuration(800).start();
        }).start();
    }
}
