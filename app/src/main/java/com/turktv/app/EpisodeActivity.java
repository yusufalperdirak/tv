package com.turktv.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/** Önceki bir programın bölümünü (YouTube) tam ekran oynatır. OK duraklat · ◀▶ sar · Geri: kanala dön. */
public class EpisodeActivity extends Activity {
    static final String EXTRA_ID = "id", EXTRA_TITLE = "title", EXTRA_SUB = "sub";

    private YtPlayer yt;
    private LinearLayout osd;
    private TextView seekText;
    private ProgressBar spinner;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable hide = () -> osd.animate().alpha(0f).setDuration(400).start();
    private final Runnable hideSeek = () -> seekText.animate().alpha(0f).setDuration(300).start();

    private int px(float v) {
        return Ui.px(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null) {
            finish();
            return;
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        yt = new YtPlayer(this);
        root.addView(yt.web, new FrameLayout.LayoutParams(-1, -1));
        spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.WHITE));
        root.addView(spinner, new FrameLayout.LayoutParams(px(48), px(48), Gravity.CENTER));

        osd = new LinearLayout(this);
        osd.setOrientation(LinearLayout.VERTICAL);
        osd.setPadding(px(Ui.edgeX(this)), px(40), px(Ui.edgeX(this)), px(Ui.edgeY(this) + 8));
        osd.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xD9000000, 0x00000000}));
        TextView sub = Ui.text(this, getIntent().getStringExtra(EXTRA_SUB), 16, Ui.MEDIUM, Ui.SOFT);
        osd.addView(sub);
        TextView title = Ui.text(this, getIntent().getStringExtra(EXTRA_TITLE), 28, Ui.BOLD, Ui.TEXT);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        osd.addView(title, Ui.margins(this, -1, -2, 0, 4, 0, 0));
        osd.addView(Ui.text(this, "OK: duraklat   ·   ◀ ▶: 10 sn sar   ·   Geri: kanala dön", 15, Ui.REGULAR, Ui.MUTED),
                Ui.margins(this, -1, -2, 0, 12, 0, 0));
        root.addView(osd, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        seekText = Ui.text(this, "", 26, Ui.BOLD, Ui.TEXT);
        seekText.setPadding(px(24), px(12), px(24), px(12));
        seekText.setBackground(Ui.rounded(this, 0xB30B0D12, 26));
        seekText.setAlpha(0f);
        root.addView(seekText, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        setContentView(root);

        yt.web.setVisibility(View.VISIBLE);
        yt.setListener(new YtPlayer.Listener() {
            @Override
            public void onState(int s) {
                spinner.setVisibility(s == 3 || s == -1 ? View.VISIBLE : View.GONE);
                if (s == 2) showOsd(false);
            }

            @Override
            public void onError(int code) {
                Toast.makeText(EpisodeActivity.this, "Bu bölüm burada oynatılamıyor, başka bir sonuç dene", Toast.LENGTH_LONG).show();
                finish();
            }
        });
        yt.play(id, false);
        showOsd(true);
    }

    private void showOsd(boolean autoHide) {
        osd.animate().cancel();
        osd.setAlpha(1f);
        ui.removeCallbacks(hide);
        if (autoHide) ui.postDelayed(hide, 4000);
    }

    private void seek(int sec) {
        yt.seekBy(sec);
        seekText.setText((sec > 0 ? "+" : "−") + Math.abs(sec) + " sn");
        seekText.animate().cancel();
        seekText.setAlpha(1f);
        ui.removeCallbacks(hideSeek);
        ui.postDelayed(hideSeek, 900);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (e.getRepeatCount() == 0) {
                    yt.toggle();
                    showOsd(true);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seek(e.getRepeatCount() > 0 ? -30 : -10);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seek(e.getRepeatCount() > 0 ? 30 : 10);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                showOsd(true);
                return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (yt != null) yt.destroy();
        super.onDestroy();
    }
}
