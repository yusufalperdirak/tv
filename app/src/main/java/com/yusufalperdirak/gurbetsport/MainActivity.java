package com.yusufalperdirak.gurbetsport;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    static volatile boolean visible;
    private WebView web;
    private boolean pageReady;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Hub.init(this);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        w.setBackgroundDrawableResource(android.R.color.black);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        web.setHapticFeedbackEnabled(false);
        web.setLongClickable(false);
        web.setOnLongClickListener(v -> true);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.addJavascriptInterface(new Bridge(), "Native");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return true; }

            @Override
            public void onPageFinished(WebView v, String url) { pageReady = true; }

            @Override
            public boolean onRenderProcessGone(WebView v, android.webkit.RenderProcessGoneDetail d) {
                // WebView cokerse uygulama kapanmasin: arayuzu yeniden kur
                recreateWeb();
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) { return true; }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");

        Hub.setSink(json -> {
            if (web == null || !pageReady) { pendingResend = true; return; }
            String js = "window.onNative&&window.onNative(" + json.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029") + ")";
            web.evaluateJavascript(js, null);
        });

        Hub.put("ver", BuildConfigVer.name(this));
        Weather.loadCached(this);
        Keys.publish(this);
        CarPlay.find(this);
        Perms.refresh(this);
        Loc.start(this);
        hideBars();
    }

    private boolean pendingResend;

    private void recreateWeb() {
        Hub.safe(() -> {
            pageReady = false;
            web.loadUrl("file:///android_asset/index.html");
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        hideBars();
        Perms.refresh(this);
        Keys.publish(this);
        CarPlay.find(this);
        Loc.start(this);
        Media.ensureConnected(this);
        Weather.maybe(this, null);
        Updater.maybe(this);
    }

    @Override
    protected void onPause() {
        visible = false;
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Ana ekran tusu: muzik sayfasina don
        if (intent != null && (intent.hasCategory(Intent.CATEGORY_HOME) || intent.getBooleanExtra("home", false))) {
            js("window.onNative&&window.onNative({page:'ana'})");
        }
    }

    @Override
    public void onBackPressed() {
        js("window.onBack&&window.onBack()");
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        Perms.refresh(this);
        Loc.start(this);
    }

    @Override
    protected void onDestroy() {
        Hub.setSink(null);
        if (web != null) {
            web.removeJavascriptInterface("Native");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    void js(String code) {
        if (web != null) web.evaluateJavascript(code, null);
    }

    @SuppressWarnings("deprecation")
    private void hideBars() {
        Hub.safe(() -> {
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    c.hide(WindowInsets.Type.statusBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        });
    }

    /** Baska bir uygulama (or. radyo) acildiktan sonra ana ekrana geri getir. */
    static void bringBack(Context c) {
        Hub.safe(() -> {
            Intent i = new Intent(c, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            c.startActivity(i);
        });
    }

    static void goHome(Context c) {
        Hub.safe(() -> {
            Intent i = new Intent(c, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("home", true);
            c.startActivity(i);
        });
    }

    /** Arayuzden (JavaScript) gelen cagrilar. Hepsi ana is parcacigina aktarilir. */
    final class Bridge {
        @JavascriptInterface public void ready() {
            Hub.runMain(() -> { pageReady = true; Hub.resendAll(); });
        }
        @JavascriptInterface public void media(String cmd) { Hub.runMain(() -> Media.cmd(MainActivity.this, cmd)); }
        @JavascriptInterface public void source(String s) { Hub.runMain(() -> Media.source(MainActivity.this, s)); }
        @JavascriptInterface public void carplay() { Hub.runMain(() -> CarPlay.launch(MainActivity.this)); }
        @JavascriptInterface public void grant(String id) { Hub.runMain(() -> Perms.grant(MainActivity.this, id)); }
        @JavascriptInterface public void refreshPerms() { Hub.runMain(() -> Perms.refresh(MainActivity.this)); }
        @JavascriptInterface public void openSys(String what) {
            Hub.runMain(() -> {
                String a = "bt".equals(what) ? Settings.ACTION_BLUETOOTH_SETTINGS : Settings.ACTION_SOUND_SETTINGS;
                Perms.start(MainActivity.this, new Intent(a));
            });
        }
        @JavascriptInterface public void learn(String action) { Hub.runMain(() -> Keys.learn(MainActivity.this, action)); }
        @JavascriptInterface public void unlearn(String action) { Hub.runMain(() -> Keys.unlearn(MainActivity.this, action)); }
        @JavascriptInterface public void cancelLearn() { Hub.runMain(() -> Keys.cancel(MainActivity.this)); }
    }

    static ComponentName listener(Context c) { return new ComponentName(c, MediaWatch.class); }

    static void rebindListener(Context c) {
        Hub.safe(() -> NotificationListenerService.requestRebind(listener(c)));
    }

    static Uri pkgUri(Context c) { return Uri.parse("package:" + c.getPackageName()); }
}
