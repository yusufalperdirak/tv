package com.turktv.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Kısa açılış ekranı. */
public class SplashActivity extends Activity {

    private static final long SHOW_MS = 1800;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean done;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(col, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.rounded(this, Ui.ACCENT, 32));
        android.widget.ImageView mark = new android.widget.ImageView(this);
        mark.setImageResource(R.drawable.ic_tv);
        badge.addView(mark, new FrameLayout.LayoutParams(Ui.px(this, 112), Ui.px(this, 112), Gravity.CENTER));
        col.addView(badge, new LinearLayout.LayoutParams(Ui.px(this, 136), Ui.px(this, 136)));

        TextView title = Ui.text(this, "TV", 56, Ui.BOLD, Ui.TEXT);
        col.addView(title, Ui.margins(this, -2, -2, 0, 26, 0, 0));

        TextView sub = Ui.text(this, "Türk kanalları, tek yerde", 18, Ui.REGULAR, Ui.MUTED);
        col.addView(sub, Ui.margins(this, -2, -2, 0, 10, 0, 0));

        // yükleniyor çubuğu
        FrameLayout bar = new FrameLayout(this);
        bar.setBackground(Ui.rounded(this, Ui.SURFACE2, 3));
        final View fill = new View(this);
        fill.setBackground(Ui.rounded(this, Ui.ACCENT, 3));
        bar.addView(fill, new FrameLayout.LayoutParams(Ui.px(this, 240), -1));
        col.addView(bar, Ui.margins(this, Ui.px(this, 240), Ui.px(this, 6), 0, 40, 0, 0));
        fill.setPivotX(0);
        fill.setScaleX(0f);
        fill.animate().scaleX(1f).setDuration(SHOW_MS - 200).start();

        TextView credit = Ui.text(this, "Bu uygulama Yusuf Alper Dırak tarafından oluşturulmuştur", 15, Ui.MEDIUM, Ui.SOFT);
        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cLp.setMargins(0, 0, 0, Ui.px(this, 48));
        root.addView(credit, cLp);

        col.setAlpha(0f);
        col.setTranslationY(Ui.px(this, 16));
        col.animate().alpha(1f).translationY(0).setDuration(450).start();
        credit.setAlpha(0f);
        credit.animate().alpha(1f).setStartDelay(250).setDuration(450).start();

        setContentView(root);

        // Kanal listesi bu sırada hazırlansın
        new Thread(() -> Channel.loadAll(getApplicationContext())).start();
        ui.postDelayed(this::go, SHOW_MS);
    }

    private void go() {
        if (done || isFinishing()) return;
        done = true;
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
