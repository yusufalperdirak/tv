package com.turktv.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/**
 * Sağ üstteki saat: yerel saat "21:17" biçiminde; aradaki iki nokta saniyede bir yumuşakça
 * yanıp söner. Arkadaki görüntüden ayrışsın diye yarı saydam zemin üzerinde durur.
 */
final class Clock {

    final LinearLayout view;
    private final TextView time;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean colonOn = true;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            colonOn = !colonOn;
            render();
            if (running) ui.postDelayed(this, 1000);
        }
    };

    Clock(Context c) {
        view = new LinearLayout(c);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER);
        view.setPadding(Ui.px(c, 20), Ui.px(c, 8), Ui.px(c, 20), Ui.px(c, 8));
        view.setBackground(Ui.rounded(c, 0x8C0B0D12, 18));

        time = Ui.text(c, "", 32, Ui.MEDIUM, Ui.TEXT);
        time.setSingleLine(true);
        time.setGravity(Gravity.CENTER);
        time.setLetterSpacing(0.04f);
        time.setMinWidth((int) Math.ceil(time.getPaint().measureText("00:00")) + Ui.px(c, 6));
        view.addView(time);
        render();
    }

    /** Ekran açıkken saniyelik akışı başlatır. */
    void start() {
        if (running) return;
        running = true;
        ui.removeCallbacks(tick);
        render();
        ui.postDelayed(tick, 1000);
    }

    void stop() {
        running = false;
        ui.removeCallbacks(tick);
    }

    /** Saati hemen yeniler. */
    void update() {
        render();
    }

    private void render() {
        Calendar cal = Calendar.getInstance();
        String h = String.format(Actions.TR, "%02d", cal.get(Calendar.HOUR_OF_DAY));
        String m = String.format(Actions.TR, "%02d", cal.get(Calendar.MINUTE));
        SpannableString s = new SpannableString(h + ":" + m);
        int colon = colonOn ? 0xCCF2F4F7 : 0x33F2F4F7;
        s.setSpan(new ForegroundColorSpan(colon), 2, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        time.setText(s);
    }
}
