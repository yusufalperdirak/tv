package com.turktv.app;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Tarayıcıda bulunan videoyu uygulamanın kendi oynatıcısında oynatır.
 * OK: duraklat/devam · ◀▶: 10 sn (basılı: 30 sn) · Geri: tarayıcıya dön.
 */
public class VideoActivity extends Activity {

    static final String EXTRA_URL = "url", EXTRA_TITLE = "title", EXTRA_REFERER = "referer", EXTRA_UA = "ua";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private StreamPlayer stream;
    private LinearLayout osd;
    private TextView posText, durText, liveText;
    private FrameLayout track;
    private View fill;
    private ImageView playIcon;
    private ProgressBar spinner;
    private final Runnable hide = () -> osd.animate().alpha(0f).setDuration(400).start();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateBar();
            ui.postDelayed(this, 500);
        }
    };

    private int px(float v) {
        return Ui.px(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null) {
            finish();
            return;
        }
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String ref = getIntent().getStringExtra(EXTRA_REFERER);
        String ua = getIntent().getStringExtra(EXTRA_UA);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        stream = new StreamPlayer(this);
        root.addView(stream.view, new FrameLayout.LayoutParams(-1, -1));

        spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.WHITE));
        root.addView(spinner, new FrameLayout.LayoutParams(px(48), px(48), Gravity.CENTER));

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xD9000000, 0x80000000, 0x00000000}));

        osd = new LinearLayout(this);
        osd.setOrientation(LinearLayout.VERTICAL);
        FrameLayout osdBox = new FrameLayout(this);
        osdBox.addView(scrim, new FrameLayout.LayoutParams(-1, px(260), Gravity.BOTTOM));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        pLp.setMargins(px(Ui.edgeX(this)), 0, px(Ui.edgeX(this)), px(Ui.edgeY(this) + 4));
        osdBox.addView(panel, pLp);

        String host = "";
        try {
            host = Uri.parse(ref != null ? ref : url).getHost();
            if (host != null) host = host.replaceFirst("^www\\.", "");
        } catch (Exception ignored) {
        }
        TextView src = Ui.text(this, host == null ? "" : host, 15, Ui.MEDIUM, Ui.SOFT);
        panel.addView(src);
        TextView t = Ui.text(this, TextUtils.isEmpty(title) ? "Video" : title, 28, Ui.BOLD, Ui.TEXT);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(t, Ui.margins(this, -1, -2, 0, 4, 0, 0));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout play = new FrameLayout(this);
        GradientDrawable pd = new GradientDrawable();
        pd.setShape(GradientDrawable.OVAL);
        pd.setColor(0x33FFFFFF);
        play.setBackground(pd);
        playIcon = Ui.icon(this, R.drawable.ic_pause, 20, Ui.TEXT);
        play.addView(playIcon, new FrameLayout.LayoutParams(px(20), px(20), Gravity.CENTER));
        row.addView(play, new LinearLayout.LayoutParams(px(46), px(46)));
        posText = Ui.text(this, "", 15, Ui.MEDIUM, Ui.TEXT);
        row.addView(posText, Ui.margins(this, -2, -2, 18, 0, 14, 0));
        track = new FrameLayout(this);
        View base = new View(this);
        base.setBackground(Ui.rounded(this, 0x40FFFFFF, 3));
        track.addView(base, new FrameLayout.LayoutParams(-1, px(6), Gravity.CENTER_VERTICAL));
        fill = new View(this);
        fill.setBackground(Ui.rounded(this, Ui.WHITE, 3));
        fill.setPivotX(0);
        fill.setScaleX(0f);
        track.addView(fill, new FrameLayout.LayoutParams(-1, px(6), Gravity.CENTER_VERTICAL));
        row.addView(track, new LinearLayout.LayoutParams(0, px(20), 1f));
        durText = Ui.text(this, "", 15, Ui.MEDIUM, Ui.SOFT);
        row.addView(durText, Ui.margins(this, -2, -2, 14, 0, 0, 0));
        liveText = Ui.text(this, "●  CANLI", 13, Ui.BOLD, Ui.LIVE);
        liveText.setLetterSpacing(0.08f);
        liveText.setPadding(px(10), px(4), px(10), px(4));
        liveText.setBackground(Ui.rounded(this, 0x33E30A17, 10));
        liveText.setVisibility(View.GONE);
        row.addView(liveText, Ui.margins(this, -2, -2, 14, 0, 0, 0));
        panel.addView(row, Ui.margins(this, -1, -2, 0, 16, 0, 0));
        osd.addView(osdBox, new LinearLayout.LayoutParams(-1, -1));
        root.addView(osd, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        stream.setListener(new StreamPlayer.Listener() {
            @Override
            public void onState(int state) {
                spinner.setVisibility(state == 3 ? View.VISIBLE : View.GONE);
                playIcon.setImageResource(state == 2 ? R.drawable.ic_play : R.drawable.ic_pause);
                if (state == 2) showOsd(false);
            }

            @Override
            public void onError() {
                Toast.makeText(VideoActivity.this, "Bu video kendi oynatıcımızda açılamadı, sayfadaki oynatıcıyla izle",
                        Toast.LENGTH_LONG).show();
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        stream.play(Channel.web(title == null ? "" : title, url, ua, ref), false, true);
        showOsd(true);
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Actions.TR, "%d:%02d:%02d", h, m, sec) : String.format(Actions.TR, "%d:%02d", m, sec);
    }

    private void updateBar() {
        long[] p = stream.position();
        boolean live = p == null;
        liveText.setVisibility(live ? View.VISIBLE : View.GONE);
        track.setVisibility(live ? View.INVISIBLE : View.VISIBLE);
        posText.setVisibility(live ? View.GONE : View.VISIBLE);
        durText.setVisibility(live ? View.GONE : View.VISIBLE);
        if (live) return;
        posText.setText(fmt(p[0]));
        durText.setText("−" + fmt(p[1] - p[0]));
        fill.setScaleX(Math.max(0f, Math.min(1f, p[0] / (float) p[1])));
    }

    private void showOsd(boolean autoHide) {
        osd.animate().cancel();
        osd.setAlpha(1f);
        ui.removeCallbacks(hide);
        if (autoHide && stream.isPlaying()) ui.postDelayed(hide, 4000);
        updateBar();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (e.getRepeatCount() > 0) return true;
                stream.toggle();
                ui.postDelayed(() -> showOsd(true), 100);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                stream.seekBy(e.getRepeatCount() > 0 ? -30_000 : -10_000);
                showOsd(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                stream.seekBy(e.getRepeatCount() > 0 ? 30_000 : 10_000);
                showOsd(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                showOsd(true);
                return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
        if (isFinishing()) stream.stop();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (stream != null) {
            stream.stop();
            stream.release();
        }
        super.onDestroy();
    }
}
