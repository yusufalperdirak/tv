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
    private View progressFill;
    private FrameLayout addrBox;
    private ImageView lockIcon;
    private View cursor;
    private FrameLayout root, page;
    private LinearLayout bar;
    private TextView hintPill;
    private final java.util.List<View> targets = new java.util.ArrayList<>();
    private View hovered;
    private float cx, cy;          // imleç (kök görünüm koordinatları)
    private int blocked;
    private boolean scrollMode;
    private long okDownAt;
    private final Runnable hideCursor = () -> {
        if (cursor != null && hovered == null) cursor.animate().alpha(0.3f).setDuration(500).start();
    };

    private int px(float v) {
        return Ui.px(this, v);
    }

    private static final int GLASS = 0x1FFFFFFF, GLASS_HOVER = 0x47FFFFFF;

    /** Yuvarlak cam düğme (iPad imleci üstüne gelince vurgulanır). */
    private FrameLayout roundButton(int icon, String desc, Runnable action) {
        FrameLayout b = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(GLASS);
        b.setBackground(d);
        b.setContentDescription(desc);
        ImageView iv = Ui.icon(this, icon, 20, Ui.TEXT);
        b.addView(iv, new FrameLayout.LayoutParams(px(20), px(20), Gravity.CENTER));
        b.setOnClickListener(v -> action.run());
        targets.add(b);
        return b;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.setClipChildren(false);
        int sx = px(Ui.edgeX(this) * 0.6f), top = px(Ui.edgeY(this) * 0.55f), bottom = px(Ui.edgeY(this) * 0.5f);
        int barH = px(48);

        // ---- üst çubuk: Safari gibi; ortada adres hapı, iki yanda cam düğmeler
        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);
        FrameLayout back = roundButton(R.drawable.ic_back, "Geri", () -> { if (web.canGoBack()) web.goBack(); });
        bar.addView(back, new LinearLayout.LayoutParams(barH, barH));
        FrameLayout fwd = roundButton(R.drawable.ic_next, "İleri", () -> { if (web.canGoForward()) web.goForward(); });
        bar.addView(fwd, gap(barH, barH, 10));

        addrBox = new FrameLayout(this);
        addrBox.setBackground(Ui.rounded(this, GLASS, 24));
        addrBox.setClipToOutline(true);
        LinearLayout addrRow = new LinearLayout(this);
        addrRow.setGravity(Gravity.CENTER);
        addrRow.setPadding(px(20), 0, px(20), 0);
        lockIcon = Ui.icon(this, R.drawable.ic_lock, 14, 0xFFAEB4BE);
        addrRow.addView(lockIcon, new LinearLayout.LayoutParams(px(14), px(14)));
        address = new EditText(this);
        address.setSingleLine(true);
        address.setHint("Ara veya adres yaz");
        address.setHintTextColor(0xFF8E96A3);
        address.setTextColor(Ui.TEXT);
        address.setTypeface(Ui.MEDIUM);
        address.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(17));
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setSelectAllOnFocus(true);
        address.setBackground(null);
        address.setGravity(Gravity.CENTER);
        address.setPadding(px(8), 0, px(8), 0);
        address.setFocusable(true);
        address.setFocusableInTouchMode(true);
        address.setOnFocusChangeListener((v, has) -> {
            address.setGravity(has ? Gravity.CENTER_VERTICAL | Gravity.START : Gravity.CENTER);
            lockIcon.setVisibility(has ? View.GONE : View.VISIBLE);
            String u = web.getUrl();
            address.setText(has ? (u != null ? u : "") : pretty(u));
            if (has) address.selectAll();
            android.graphics.drawable.GradientDrawable d = Ui.rounded(this, has ? 0x33FFFFFF : GLASS, 24);
            if (has) d.setStroke(px(2), Ui.WHITE);
            addrBox.setBackground(d);
            if (has) {
                setHover(null);
                cursor.setAlpha(0f);
            }
        });
        address.setOnEditorActionListener((v, id, ev) -> {
            go(address.getText().toString());
            return true;
        });
        addrRow.addView(address, new LinearLayout.LayoutParams(-2, -1));
        addrBox.addView(addrRow, new FrameLayout.LayoutParams(-1, -1));
        progressFill = new View(this);
        progressFill.setBackgroundColor(0xFF4DA3FF);
        progressFill.setPivotX(0);
        progressFill.setScaleX(0f);
        addrBox.addView(progressFill, new FrameLayout.LayoutParams(-1, px(2), Gravity.BOTTOM));
        addrBox.setOnClickListener(v -> editAddress());
        targets.add(addrBox);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(0, barH, 1f);
        aLp.setMargins(px(16), 0, px(16), 0);
        bar.addView(addrBox, aLp);

        FrameLayout reload = roundButton(R.drawable.ic_refresh, "Yenile", () -> web.reload());
        bar.addView(reload, new LinearLayout.LayoutParams(barH, barH));
        FrameLayout home = roundButton(R.drawable.ic_home, "Google", () -> web.loadUrl(HOME));
        bar.addView(home, gap(barH, barH, 10));

        blockedText = Ui.text(this, "0", 15, Ui.BOLD, 0xFF7FD3A6);
        blockedText.setGravity(Gravity.CENTER);
        blockedText.setCompoundDrawablePadding(px(6));
        android.graphics.drawable.Drawable sh = getDrawable(R.drawable.ic_shield);
        if (sh != null) {
            sh.setTint(0xFF7FD3A6);
            sh.setBounds(0, 0, px(18), px(18));
            blockedText.setCompoundDrawables(sh, null, null, null);
        }
        blockedText.setPadding(px(14), 0, px(16), 0);
        blockedText.setBackground(Ui.rounded(this, GLASS, 24));
        blockedText.setContentDescription("Engellenen reklam");
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(-2, barH);
        bLp.leftMargin = px(10);
        bar.addView(blockedText, bLp);
        FrameLayout close = roundButton(R.drawable.ic_close, "Kapat", this::finish);
        bar.addView(close, gap(barH, barH, 10));

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(-1, barH, Gravity.TOP);
        barLp.setMargins(sx, top, sx, 0);
        root.addView(bar, barLp);

        // ---- sayfa: yuvarlak köşeli tam alan
        page = new FrameLayout(this);
        page.setBackground(Ui.rounded(this, 0xFF12161E, 22));
        page.setClipToOutline(true);
        web = new WebView(this);
        page.addView(web, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(-1, -1);
        pLp.setMargins(sx, top + barH + px(14), sx, bottom);
        root.addView(page, pLp);

        // ---- iPad imleci: yarı saydam gri daire
        cursor = new View(this);
        android.graphics.drawable.GradientDrawable cd = new android.graphics.drawable.GradientDrawable();
        cd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        cd.setColor(0x8C8A8F98);
        cd.setStroke(Math.max(1, px(1)), 0x40FFFFFF);
        cursor.setBackground(cd);
        cursor.setElevation(px(6));
        cursor.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        root.addView(cursor, new FrameLayout.LayoutParams(px(24), px(24)));

        // ---- kısa ipucu (birkaç saniye görünür)
        hintPill = Ui.text(this, "OK: tıkla   ·   OK basılı: kaydırma   ·   Yukarı: adres çubuğu", 14, Ui.MEDIUM, Ui.TEXT);
        hintPill.setPadding(px(20), px(10), px(20), px(10));
        hintPill.setBackground(Ui.rounded(this, 0xCC1C1F26, 22));
        FrameLayout.LayoutParams hLp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hLp.bottomMargin = bottom + px(20);
        root.addView(hintPill, hLp);
        ui.postDelayed(() -> hintPill.animate().alpha(0f).setDuration(600).start(), 5000);

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
                progressFill.animate().cancel();
                progressFill.setAlpha(1f);
                progressFill.animate().scaleX(p / 100f).setDuration(200).start();
                if (p >= 100) progressFill.animate().alpha(0f).setStartDelay(250).setDuration(300)
                        .withEndAction(() -> progressFill.setScaleX(0f)).start();
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
                lockIcon.setImageAlpha(url != null && url.startsWith("https") ? 255 : 0);
            }
        });

        page.post(() -> {
            cx = page.getLeft() + page.getWidth() / 2f;
            cy = page.getTop() + page.getHeight() / 2f;
            placeCursor();
            showCursor();
        });
        String start = getIntent().getDataString();
        web.loadUrl(start != null ? start : HOME);
        web.requestFocus();
    }

    private LinearLayout.LayoutParams gap(int w, int h, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.leftMargin = px(left);
        return lp;
    }

    private void editAddress() {
        address.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(address, InputMethodManager.SHOW_IMPLICIT);
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
        leaveAddress();
    }

    // ------------------------------------------------------------------ imleç

    private boolean inBarZone() {
        return cy < page.getTop();
    }

    private void placeCursor() {
        int half = px(12);
        cursor.setTranslationX(cx - half);
        cursor.setTranslationY(cy - half);
        if (inBarZone()) {
            View hit = null;
            for (View t : targets) {
                int[] o = new int[2], r = new int[2];
                t.getLocationInWindow(o);
                root.getLocationInWindow(r);
                float x = cx - (o[0] - r[0]), y = cy - (o[1] - r[1]);
                if (x >= -px(6) && x <= t.getWidth() + px(6) && y >= -px(10) && y <= t.getHeight() + px(10)) { hit = t; break; }
            }
            setHover(hit);
        } else setHover(null);
    }

    /** iPad imleci gibi: düğmenin üstüne gelince imleç kaybolur, düğme aydınlanıp hafifçe büyür. */
    private void setHover(View v) {
        if (v == hovered) return;
        if (hovered != null) {
            tint(hovered, GLASS);
            hovered.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(Ui.FOCUS).start();
        }
        hovered = v;
        if (v != null) {
            tint(v, GLASS_HOVER);
            float sc = v == addrBox ? 1.02f : 1.1f;
            v.animate().scaleX(sc).scaleY(sc).setDuration(200).setInterpolator(Ui.FOCUS).start();
            cursor.animate().cancel();
            cursor.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(140).start();
        } else {
            cursor.animate().cancel();
            cursor.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).start();
        }
    }

    private void tint(View v, int color) {
        android.graphics.drawable.Drawable d = v.getBackground();
        if (d instanceof android.graphics.drawable.GradientDrawable && !(v == addrBox && address.hasFocus()))
            ((android.graphics.drawable.GradientDrawable) d).setColor(color);
    }

    private void showCursor() {
        if (hovered != null) return;
        cursor.animate().cancel();
        cursor.setAlpha(scrollMode ? 0.5f : 1f);
        cursor.setScaleX(scrollMode ? 1.4f : 1f);
        cursor.setScaleY(scrollMode ? 1.4f : 1f);
        ui.removeCallbacks(hideCursor);
        ui.postDelayed(hideCursor, 4000);
    }

    private void move(int dx, int dy, int repeat) {
        float step = px(Math.min(9 + repeat * 3, 42));
        if (scrollMode) {
            web.scrollBy(dx * px(70), dy * px(90));
            showCursor();
            return;
        }
        float edge = px(14);
        float pl = page.getLeft(), pt = page.getTop(), pr = page.getRight(), pb = page.getBottom();
        if (inBarZone()) { // üst çubukta: sağa-sola serbest, aşağı basınca sayfaya döner
            if (dy > 0) {
                cy = pt + px(40);
            } else if (dy == 0) {
                cx = Math.max(pl, Math.min(pr, cx + dx * step));
            }
            placeCursor();
            showCursor();
            return;
        }
        cx += dx * step;
        cy += dy * step;
        if (cy < pt + edge) {
            // basılı tutulunca sayfa yukarı kayar; sayfanın başındaysa ya da tek basışta adres çubuğuna çıkar
            if (repeat > 0 && web.canScrollVertically(-1)) {
                web.scrollBy(0, -(int) step * 2);
                cy = pt + edge;
            } else {
                cy = bar.getTop() + bar.getHeight() / 2f;
                cx = Math.max(addrBox.getLeft() + bar.getLeft(), Math.min(addrBox.getRight() + bar.getLeft(), cx));
            }
        }
        if (cy > pb - edge) { web.scrollBy(0, (int) step * 2); cy = pb - edge; }
        if (cx < pl + edge) { web.scrollBy(-(int) step * 2, 0); cx = pl + edge; }
        if (cx > pr - edge) { web.scrollBy((int) step * 2, 0); cx = pr - edge; }
        placeCursor();
        showCursor();
    }

    private void click() {
        if (hovered != null) {
            View h = hovered;
            h.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).withEndAction(
                    () -> h.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start()).start();
            h.performClick();
            return;
        }
        if (inBarZone()) return;
        float x = cx - page.getLeft(), y = cy - page.getTop();
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        MotionEvent up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0);
        up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(down);
        web.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        cursor.animate().cancel();
        cursor.setAlpha(1f);
        cursor.animate().scaleX(0.7f).scaleY(0.7f).setDuration(80).withEndAction(
                () -> cursor.animate().scaleX(1f).scaleY(1f).setDuration(140).start()).start();
        ui.removeCallbacks(hideCursor);
        ui.postDelayed(hideCursor, 4000);
    }

    private void leaveAddress() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(address.getWindowToken(), 0);
        address.clearFocus();
        web.requestFocus();
        cy = page.getTop() + px(40);
        placeCursor();
        showCursor();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (address.hasFocus()) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_DPAD_DOWN)) {
                leaveAddress();
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
                case KeyEvent.KEYCODE_CHANNEL_UP:
                case KeyEvent.KEYCODE_PAGE_UP:
                    web.pageUp(false); return true;
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    web.pageDown(false); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (r == 0) okDownAt = SystemClock.uptimeMillis();
                    else if (r == 8 && hovered == null) { // basılı tutuldu: kaydırma modu
                        scrollMode = !scrollMode;
                        okDownAt = 0;
                        hintPill.setText(scrollMode ? "Kaydırma: yön tuşlarıyla sayfayı kaydır · OK ile bitir" : "İmleç");
                        hintPill.animate().cancel();
                        hintPill.setAlpha(1f);
                        hintPill.animate().alpha(0f).setStartDelay(2500).setDuration(500).start();
                        showCursor();
                    }
                    return true;
                case KeyEvent.KEYCODE_MENU:
                    editAddress();
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
