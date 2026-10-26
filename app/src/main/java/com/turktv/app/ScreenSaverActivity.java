package com.turktv.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;

/**
 * Uygulama içi ekran koruyucu: Bugün sayfası (hareketli hava, kurlar, ezan vakitleri).
 * Ekranda iz kalmasın diye sayfa dakikada bir birkaç piksel kayar. Herhangi bir tuşa basınca kapanır.
 */
public class ScreenSaverActivity extends Activity {
    private TodayPage page;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private int step;
    private final Runnable drift = new Runnable() {
        @Override
        public void run() {
            step++;
            float[][] d = {{0, 0}, {6, 4}, {-5, 6}, {4, -5}, {-6, -3}};
            float[] o = d[step % d.length];
            page.animate().translationX(Ui.px(ScreenSaverActivity.this, o[0]))
                    .translationY(Ui.px(ScreenSaverActivity.this, o[1])).setDuration(3000).start();
            ui.postDelayed(this, 60_000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        page = new TodayPage(this);
        setContentView(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        page.start();
        ui.postDelayed(drift, 60_000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        page.stop();
        ui.removeCallbacks(drift);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_UP) {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
        return true;
    }
}
