package com.turktv.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** YouTube'un resmi gömülü oynatıcısını uygulama içinde çalıştıran WebView sarmalayıcı. */
final class YtPlayer {

    interface Listener {
        /** -1 başlamadı, 0 bitti, 1 oynuyor, 2 duraklatıldı, 3 yükleniyor */
        void onState(int state);

        void onError(int code);
    }

    static final String ORIGIN = "https://turktv.app";

    final WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Listener listener;
    private int token = 0;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    YtPlayer(Context c) {
        web = new WebView(c);
        web.setBackgroundColor(Color.BLACK);
        web.setFocusable(false);
        web.setFocusableInTouchMode(false);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public Bitmap getDefaultVideoPoster() {
                // Android'in varsayılan gri "oynat" simgesi görünmesin
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            }
        });
        web.setAlpha(0f); // görüntü gelene kadar gizli (arkadaki yükleme simgesi görünür)
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
    }

    void setListener(Listener l) {
        listener = l;
    }

    void play(String videoId, boolean muted) {
        int t = ++token;
        web.animate().cancel();
        web.setAlpha(0f);
        String html = "<!doctype html><html><head>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}"
                + "#p{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style>"
                + "</head><body><div id='p'></div>"
                + "<script src='https://www.youtube.com/iframe_api'></script><script>"
                + "var player;"
                + "function onYouTubeIframeAPIReady(){player=new YT.Player('p',{width:'100%',height:'100%',"
                + "videoId:'" + videoId + "',"
                + "playerVars:{autoplay:1,mute:" + (muted ? 1 : 0) + ",controls:0,rel:0,playsinline:1,"
                + "iv_load_policy:3,disablekb:1,fs:0,origin:'" + ORIGIN + "'},"
                + "events:{"
                + "onReady:function(e){" + (muted ? "e.target.mute();" : "e.target.unMute();e.target.setVolume(100);")
                + "e.target.playVideo();},"
                + "onStateChange:function(e){Android.onState(" + t + ",e.data);},"
                + "onError:function(e){Android.onError(" + t + ",e.data);}"
                + "}});}"
                + "function toggle(){if(!player||!player.getPlayerState)return;"
                + "if(player.getPlayerState()==1){player.pauseVideo();}else{player.playVideo();}}"
                + "function setMuted(m){if(!player||!player.mute)return;if(m){player.mute();}else{player.unMute();player.setVolume(100);}}"
                + "</script></body></html>";
        web.loadDataWithBaseURL(ORIGIN + "/", html, "text/html", "utf-8", null);
    }

    void stop() {
        token++;
        web.animate().cancel();
        web.setAlpha(0f);
        web.loadUrl("about:blank");
    }

    void toggle() {
        web.evaluateJavascript("toggle()", null);
    }

    void setMuted(boolean m) {
        web.evaluateJavascript("setMuted(" + m + ")", null);
    }

    void destroy() {
        token++;
        listener = null;
        web.destroy();
    }

    private class Bridge {
        @JavascriptInterface
        public void onState(int t, int state) {
            ui.post(() -> {
                if (t != token) return;
                if (state == 1) web.animate().alpha(1f).setDuration(200).start();
                if (listener != null) listener.onState(state);
            });
        }

        @JavascriptInterface
        public void onError(int t, int code) {
            ui.post(() -> {
                if (t == token && listener != null) listener.onError(code);
            });
        }
    }
}
