package com.turktv.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Uygulamanın kendi tarayıcısı (Google altyapısı: Android'in Chromium tabanlı web motoru, aramalar Google'da).
 * Kumandayla fare imleci gibi gezinilir; reklam sunucuları engellenir.
 */
public class BrowserActivity extends Activity {

    private static final String HOME = "https://www.google.com/";

    private static final Set<String> AD_HOSTS = new HashSet<>(Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
            "pagead2.googlesyndication.com", "adnxs.com", "taboola.com", "outbrain.com", "criteo.com", "criteo.net",
            "amazon-adsystem.com", "pubmatic.com", "rubiconproject.com", "openx.net", "scorecardresearch.com",
            "zedo.com", "popads.net", "popcash.net", "propellerads.com", "adform.net", "smartadserver.com",
            "an.yandex.ru", "yandexadexchange.net", "adsrvr.org", "moatads.com", "casalemedia.com", "teads.tv",
            "sharethrough.com", "3lift.com", "media.net", "revcontent.com", "mgid.com", "exoclick.com",
            "juicyads.com", "trafficjunky.net", "adcolony.com", "applovin.com", "unityads.unity3d.com",
            "admixer.net", "adtelligent.com", "onclickads.net", "hilltopads.net", "adsterra.com",
            "googletagservices.com", "imasdk.googleapis.com", "ads.yahoo.com", "advertising.com",
            "serving-sys.com", "bidswitch.net", "contextweb.com", "lijit.com", "yieldmo.com", "gumgum.com"));

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WebView web;
    private EditText address;
    private TextView blockedText;
    private ProgressBar progress;
    private ImageView cursor;
    private FrameLayout page;
    private LinearLayout bar;
    private float cx, cy;
    private int blocked;
    private boolean scrollMode;
    private long okDownAt;
    private final Runnable hideCursor = () -> { if (cursor != null) cursor.animate().alpha(0.35f).setDuration(400).start(); };

    private int px(float v) {
        return Ui.px(this, v);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(px(Ui.edgeX(this) * 0.6f), px(Ui.edgeY(this) * 0.6f), px(Ui.edgeX(this) * 0.6f), px(Ui.edgeY(this) * 0.5f));

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);
        bar.setPadding(px(4), px(4), px(4), px(4));
        LinearLayout back = Ui.button(this, R.drawable.ic_back, null, 40, 0x1FFFFFFF);
        back.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); });
        bar.addView(back);
        LinearLayout fwd = Ui.button(this, R.drawable.ic_next, null, 40, 0x1FFFFFFF);
        fwd.setOnClickListener(v -> { if (web.canGoForward()) web.goForward(); });
        addGap(bar, fwd);
        LinearLayout reload = Ui.button(this, R.drawable.ic_refresh, null, 40, 0x1FFFFFFF);
        reload.setOnClickListener(v -> web.reload());
        addGap(bar, reload);
        LinearLayout home = Ui.button(this, R.drawable.ic_home, null, 40, 0x1FFFFFFF);
        home.setOnClickListener(v -> web.loadUrl(HOME));
        addGap(bar, home);

        address = new EditText(this);
        address.setSingleLine(true);
        address.setHint("Google'da ara veya adres yaz");
        address.setHintTextColor(0xFF8E96A3);
        address.setTextColor(Ui.TEXT);
        address.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(16));
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setSelectAllOnFocus(true);
        address.setPadding(px(18), 0, px(18), 0);
        address.setBackground(Ui.rounded(this, 0x1FFFFFFF, 20));
        address.setOnFocusChangeListener((v, has) -> {
            android.graphics.drawable.GradientDrawable d = Ui.rounded(this, has ? 0x33FFFFFF : 0x1FFFFFFF, 20);
            if (has) d.setStroke(px(2), Ui.WHITE);
            v.setBackground(d);
        });
        address.setOnEditorActionListener((v, id, ev) -> {
            go(address.getText().toString());
            return true;
        });
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(0, px(40), 1f);
        aLp.setMargins(px(12), 0, px(8), 0);
        bar.addView(address, aLp);

        blockedText = Ui.text(this, "", 14, Ui.BOLD, 0xFF7FD3A6);
        blockedText.setGravity(Gravity.CENTER_VERTICAL);
        blockedText.setCompoundDrawablePadding(px(6));
        android.graphics.drawable.Drawable sh = getDrawable(R.drawable.ic_shield);
        if (sh != null) {
            sh.setTint(0xFF7FD3A6);
            sh.setBounds(0, 0, px(18), px(18));
            blockedText.setCompoundDrawables(sh, null, null, null);
        }
        blockedText.setPadding(px(12), 0, px(12), 0);
        blockedText.setBackground(Ui.rounded(this, 0x1FFFFFFF, 20));
        bar.addView(blockedText, new LinearLayout.LayoutParams(-2, px(40)));
        LinearLayout close = Ui.button(this, R.drawable.ic_close, null, 40, 0x1FFFFFFF);
        close.setOnClickListener(v -> finish());
        addGap(bar, close);
        root.addView(bar);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.WHITE));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x22FFFFFF));
        root.addView(progress, Ui.margins(this, -1, px(3), 0, 8, 0, 6));

        page = new FrameLayout(this);
        page.setBackground(Ui.rounded(this, 0xFF12161E, 16));
        page.setClipToOutline(true);
        web = new WebView(this);
        page.addView(web, new FrameLayout.LayoutParams(-1, -1));
        cursor = new ImageView(this);
        cursor.setImageResource(R.drawable.ic_cursor);
        page.addView(cursor, new FrameLayout.LayoutParams(px(30), px(36)));
        root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView hint = Ui.text(this, "Yön tuşları: imleç   ·   OK: tıkla   ·   OK basılı: kaydırma modu   ·   Menü: adres çubuğu   ·   Geri: önceki sayfa",
                12, Ui.MEDIUM, 0xFF8E96A3);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, Ui.margins(this, -1, -2, 0, 8, 0, 0));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        CookieManager.getInstance().setAcceptCookie(true);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.INVISIBLE : View.VISIBLE);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (u != null && isAd(u.getHost())) {
                    ui.post(() -> {
                        blocked++;
                        blockedText.setText(String.valueOf(blocked));
                    });
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String scheme = req.getUrl().getScheme();
                return scheme == null || !(scheme.equals("http") || scheme.equals("https"));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (!address.hasFocus()) address.setText(pretty(url));
            }
        });

        blockedText.setText("0");
        page.post(() -> {
            cx = page.getWidth() / 2f;
            cy = page.getHeight() / 2f;
            placeCursor();
        });
        String start = getIntent().getDataString();
        web.loadUrl(start != null ? start : HOME);
        web.requestFocus();
    }

    private void addGap(LinearLayout row, View b) {
        row.addView(b);
        ((LinearLayout.LayoutParams) b.getLayoutParams()).setMargins(px(8), 0, 0, 0);
    }

    private static boolean isAd(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        while (true) {
            if (AD_HOSTS.contains(host)) return true;
            int dot = host.indexOf('.');
            if (dot < 0 || host.indexOf('.', dot + 1) < 0) return false;
            host = host.substring(dot + 1);
        }
    }

    private static String pretty(String url) {
        if (url == null) return "";
        return url.replaceFirst("^https?://(www\\.)?", "");
    }

    private void go(String q) {
        q = q.trim();
        if (TextUtils.isEmpty(q)) return;
        String url;
        if (q.startsWith("http://") || q.startsWith("https://")) url = q;
        else if (!q.contains(" ") && q.contains(".") && !q.endsWith(".")) url = "https://" + q;
        else url = "https://www.google.com/search?q=" + Uri.encode(q);
        web.loadUrl(url);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(address.getWindowToken(), 0);
        address.clearFocus();
        web.requestFocus();
        showCursor();
    }

    // ------------------------------------------------------------------ imleç

    private void placeCursor() {
        cursor.setTranslationX(cx - px(4));
        cursor.setTranslationY(cy - px(2));
    }

    private void showCursor() {
        cursor.animate().cancel();
        cursor.setAlpha(1f);
        cursor.setImageAlpha(scrollMode ? 120 : 255);
        ui.removeCallbacks(hideCursor);
        ui.postDelayed(hideCursor, 4000);
    }

    private void move(int dx, int dy, int repeat) {
        float step = px(Math.min(9 + repeat * 3, 42));
        int w = page.getWidth(), h = page.getHeight();
        if (scrollMode) {
            web.scrollBy(dx * px(70), dy * px(90));
            showCursor();
            return;
        }
        cx += dx * step;
        cy += dy * step;
        float edge = px(14);
        if (cy < edge) { web.scrollBy(0, -(int) step * 2); cy = edge; }
        if (cy > h - edge) { web.scrollBy(0, (int) step * 2); cy = h - edge; }
        if (cx < edge) { web.scrollBy(-(int) step * 2, 0); cx = edge; }
        if (cx > w - edge) { web.scrollBy((int) step * 2, 0); cx = w - edge; }
        placeCursor();
        showCursor();
    }

    private void click() {
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cx, cy, 0);
        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        MotionEvent up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, cx, cy, 0);
        up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(down);
        web.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        cursor.animate().scaleX(0.8f).scaleY(0.8f).setDuration(80).withEndAction(
                () -> cursor.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();
        showCursor();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean inBar = bar.hasFocus();
        if (inBar) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_BACK
                    || k == KeyEvent.KEYCODE_DPAD_DOWN)) {
                View f = getCurrentFocus();
                if (f != null) f.clearFocus();
                web.requestFocus();
                showCursor();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(e);
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            int r = e.getRepeatCount();
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_LEFT: move(-1, 0, r); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: move(1, 0, r); return true;
                case KeyEvent.KEYCODE_DPAD_UP: move(0, -1, r); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: move(0, 1, r); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (r == 0) okDownAt = SystemClock.uptimeMillis();
                    else if (r == 8) { // basılı tutuldu: kaydırma modu
                        scrollMode = !scrollMode;
                        okDownAt = 0;
                        android.widget.Toast.makeText(this, scrollMode ? "Kaydırma modu: yön tuşlarıyla sayfayı kaydır" : "İmleç modu",
                                android.widget.Toast.LENGTH_SHORT).show();
                        showCursor();
                    }
                    return true;
                case KeyEvent.KEYCODE_MENU:
                    address.requestFocus();
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (scrollMode) { scrollMode = false; showCursor(); return true; }
                    if (web.canGoBack()) { web.goBack(); return true; }
                    finish();
                    return true;
            }
        } else if (e.getAction() == KeyEvent.ACTION_UP && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
            if (okDownAt != 0) {
                okDownAt = 0;
                if (scrollMode) { scrollMode = false; showCursor(); } else click();
            }
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_DPAD_LEFT
                || k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_BACK) return true;
        return super.dispatchKeyEvent(e);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        web.destroy();
        super.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
