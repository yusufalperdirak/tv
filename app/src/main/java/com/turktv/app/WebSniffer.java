package com.turktv.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Locale;

/**
 * Kanalın kendi web sitesindeki canlı yayın sayfasını görünmez bir tarayıcıda açar ve sayfanın
 * oynatıcısının yüklediği yayın adresini (m3u8) yakalar. Sitede çalışan yayın, uygulamanın
 * oynatıcısında açılır.
 */
final class WebSniffer {
    interface Callback {
        /** url: yakalanan yayın adresi (bulunamazsa null); ua: sayfanın tarayıcı kimliği */
        void done(String url, String ua);
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WebView web;
    private boolean finished;

    @SuppressLint("SetJavaScriptEnabled")
    void find(Context c, String page, Callback cb) {
        cancel();
        finished = false;
        web = new WebView(c.getApplicationContext());
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(StreamPlayer.BROWSER_UA);
        CookieManager.getInstance().setAcceptCookie(true);
        final String ua = s.getUserAgentString();
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String path = u.getPath() == null ? "" : u.getPath().toLowerCase(Locale.ROOT);
                if (path.endsWith(".m3u8")) {
                    final String found = u.toString();
                    ui.post(() -> finish(cb, found, ua));
                }
                return null;
            }
        });
        web.loadUrl(page);
        ui.postDelayed(() -> finish(cb, null, ua), 12000);
    }

    private void finish(Callback cb, String url, String ua) {
        if (finished) return;
        finished = true;
        cancel();
        cb.done(url, ua);
    }

    void cancel() {
        ui.removeCallbacksAndMessages(null);
        if (web != null) {
            WebView w = web;
            web = null;
            w.stopLoading();
            w.destroy();
        }
    }
}
