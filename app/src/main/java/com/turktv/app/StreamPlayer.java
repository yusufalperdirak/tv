package com.turktv.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.ui.PlayerView;

import java.util.HashMap;
import java.util.Map;

/**
 * Kanal yayınlarını oynatan video oynatıcı (ExoPlayer).
 *
 * Tüm ekranlar TEK bir oynatıcıyı paylaşır: ana ekrandaki önizleme tam ekrana geçerken
 * yayın kesilmeden devam eder, aynı kanal yeniden açılmaz. Kanal değişirken önceki kanalın
 * son karesi donuk kalır, yeni görüntü gelince yumuşakça geçer.
 */
final class StreamPlayer {

    interface Listener {
        /** 0 bitti, 1 oynuyor, 2 duraklatıldı, 3 yükleniyor */
        void onState(int state);

        void onError();
    }

    /** Tarayıcı gibi görün: bu yayınlar web oynatıcıları için hazırlanmış. */
    static final String BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    // ---- paylaşılan oynatıcı
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static ExoPlayer shared;
    private static String sharedUrl;
    private static StreamPlayer owner;
    private static boolean hasFrame;
    private static int quickRetries;
    private static final Runnable releaseIfUnused = () -> {
        if (owner == null && shared != null) {
            shared.release();
            shared = null;
            sharedUrl = null;
        }
    };

    // ---- bu ekrana ait görünüm
    final FrameLayout view;
    private final PlayerView playerView;
    private final ImageView freeze;
    private final View cover; // ilk gerçek kare gelene kadar video katmanını tamamen örten siyah katman
    private final Context ctx;
    private Listener listener;
    private Bitmap frozen;

    StreamPlayer(Context c) {
        ctx = c.getApplicationContext();
        view = new FrameLayout(c);
        view.setBackgroundColor(Color.BLACK);
        view.setFocusable(false);
        playerView = (PlayerView) LayoutInflater.from(c).inflate(R.layout.stream_view, view, false);
        view.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        cover = new View(c);
        cover.setBackgroundColor(Color.BLACK);
        view.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        freeze = new ImageView(c);
        freeze.setScaleType(ImageView.ScaleType.FIT_XY);
        freeze.setVisibility(View.GONE);
        view.addView(freeze, new FrameLayout.LayoutParams(-1, -1));
    }

    void setListener(Listener l) {
        listener = l;
    }

    /** Donuk kare gösteriliyor mu (kanal geçişi sürüyor). */
    boolean isFrozen() {
        return freeze.getVisibility() == View.VISIBLE;
    }

    // ------------------------------------------------------------------ oynatma

    void play(Channel c, boolean muted) {
        play(c, muted, false);
    }

    /** force: aynı kanal oynuyor olsa bile baştan bağlan (Yenile / yeniden bağlanma). */
    void play(Channel c, boolean muted, boolean force) {
        attach();
        ExoPlayer p = ensureShared();
        p.setVolume(muted ? 0f : 1f);

        // Aynı kanal zaten oynuyorsa (ör. önizlemeden tam ekrana geçiş): hiçbir şey yeniden açılmaz
        if (!force && c.url.equals(sharedUrl) && p.getPlayerError() == null
                && p.getPlaybackState() != Player.STATE_IDLE && p.getPlaybackState() != Player.STATE_ENDED) {
            p.setPlayWhenReady(true);
            cover.setAlpha(1f);
            cover.setVisibility(View.VISIBLE);
            final boolean playing = p.isPlaying();
            ui.post(() -> {
                if (owner == this && listener != null) listener.onState(playing ? 1 : 3);
            });
            return;
        }

        captureFrame();
        cover.animate().cancel();
        cover.setAlpha(1f);
        cover.setVisibility(View.VISIBLE);
        hasFrame = false;
        quickRetries = 0;
        sharedUrl = c.url;
        p.setMediaSource(source(c));
        p.setPlayWhenReady(true);
        p.prepare();
    }

    private MediaSource source(Channel c) {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(c.ua != null ? c.ua : BROWSER_UA)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(8000)
                .setReadTimeoutMs(12000);
        if (c.referer != null) {
            Map<String, String> h = new HashMap<>();
            h.put("Referer", c.referer);
            http.setDefaultRequestProperties(h);
        }
        MediaItem.Builder item = new MediaItem.Builder()
                .setUri(c.url)
                // canlı yayının en ucunda durma: birkaç saniye geriden oynat, takılma azalır
                .setLiveConfiguration(new MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(10000)
                        .setMinPlaybackSpeed(0.97f)
                        .setMaxPlaybackSpeed(1.03f)
                        .build());
        if (c.url.toLowerCase(Actions.TR).contains("m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        return new DefaultMediaSourceFactory(http)
                // geçici sunucu hatalarında hemen pes etme
                .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(8))
                .createMediaSource(item.build());
    }

    private ExoPlayer ensureShared() {
        ui.removeCallbacks(releaseIfUnused);
        if (shared != null) return shared;
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(ctx)
                .setEnableDecoderFallback(true); // donanım çözücü desteklemezse yazılıma geç
        DefaultLoadControl load = new DefaultLoadControl.Builder()
                // hızlı başlat (1 sn tampon), kesintide 2.5 sn biriktir, en fazla 40 sn tut
                .setBufferDurationsMs(15000, 40000, 1000, 2500)
                .build();
        shared = new ExoPlayer.Builder(ctx, renderers).setLoadControl(load).build();
        shared.addListener(new Player.Listener() {
            @Override
            public void onRenderedFirstFrame() {
                hasFrame = true;
                if (owner != null) owner.unfreeze();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                Listener l = owner != null ? owner.listener : null;
                if (l == null) return;
                if (state == Player.STATE_BUFFERING) l.onState(3);
                else if (state == Player.STATE_ENDED) l.onState(0);
            }

            @Override
            public void onIsPlayingChanged(boolean playing) {
                Listener l = owner != null ? owner.listener : null;
                if (l == null || shared == null) return;
                if (playing) {
                    quickRetries = 0;
                    // güvenlik: ilk kare bildirimi gelmese bile kapak ve donuk kare kısa süre sonra kalksın
                    final StreamPlayer o = owner;
                    ui.postDelayed(() -> {
                        if (owner == o && o != null) o.unfreeze();
                    }, 700);
                    l.onState(1);
                } else if (shared.getPlaybackState() == Player.STATE_READY && !shared.getPlayWhenReady()) {
                    l.onState(2);
                }
            }

            @Override
            public void onPlayerError(PlaybackException err) {
                if (shared == null) return;
                // Önce yerinde toparlanmayı dene (oynatıcıyı yeniden kurmadan, son kare ekranda kalır)
                if (err.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW || quickRetries < 2) {
                    quickRetries++;
                    shared.seekToDefaultPosition();
                    shared.prepare();
                    return;
                }
                Listener l = owner != null ? owner.listener : null;
                if (l != null) l.onError();
            }
        });
        return shared;
    }

    private void attach() {
        if (owner != null && owner != this) owner.playerView.setPlayer(null);
        owner = this;
        ExoPlayer p = ensureShared();
        if (playerView.getPlayer() != p) playerView.setPlayer(p);
    }

    // ------------------------------------------------------------------ donuk kare

    /** O an ekrandaki kareyi yakalayıp üstte donuk gösterir. */
    private void captureFrame() {
        if (!hasFrame || Build.VERSION.SDK_INT < 24) return;
        View sv = playerView.getVideoSurfaceView();
        if (!(sv instanceof SurfaceView) || sv.getWidth() <= 0 || sv.getHeight() <= 0) return;
        try {
            final Bitmap b = Bitmap.createBitmap(sv.getWidth(), sv.getHeight(), Bitmap.Config.ARGB_8888);
            PixelCopy.request((SurfaceView) sv, b, result -> {
                if (result != PixelCopy.SUCCESS || hasFrame) {
                    b.recycle(); // kopyalanamadı ya da yeni kanal zaten geldi
                    return;
                }
                freeze.animate().cancel();
                freeze.setImageBitmap(b);
                if (frozen != null && frozen != b) frozen.recycle();
                frozen = b;
                freeze.setAlpha(1f);
                freeze.setVisibility(View.VISIBLE);
            }, ui);
        } catch (Exception ignored) {
        }
    }

    private void unfreeze() {
        if (cover.getVisibility() == View.VISIBLE) {
            cover.animate().alpha(0f).setDuration(150).withEndAction(() -> cover.setVisibility(View.GONE)).start();
        }
        if (freeze.getVisibility() != View.VISIBLE) return;
        freeze.animate().alpha(0f).setDuration(220).withEndAction(this::clearFreeze).start();
    }

    private void clearFreeze() {
        freeze.animate().cancel();
        freeze.setVisibility(View.GONE);
        freeze.setImageDrawable(null);
        if (frozen != null) {
            frozen.recycle();
            frozen = null;
        }
    }

    // ------------------------------------------------------------------ yaşam döngüsü

    /**
     * Ekran arka plana geçerken: oynatıcıyı bırakır ama hemen kapatmaz. Başka bir ekran
     * (ör. tam ekran izleme) kısa süre içinde devralırsa yayın kesilmeden sürer.
     */
    void detach() {
        clearFreeze();
        if (owner != this) return;
        owner = null;
        playerView.setPlayer(null);
        ui.removeCallbacks(releaseIfUnused);
        ui.postDelayed(releaseIfUnused, 1500);
    }

    /** Oynatmayı tamamen durdurur. */
    void stop() {
        clearFreeze();
        if (owner == this && shared != null) {
            shared.stop();
            shared.clearMediaItems();
            sharedUrl = null;
            hasFrame = false;
        }
    }

    /** Hata durumunda: oynatmayı bırakır, ekrandaki son kare kalır. */
    void stopKeepFrame() {
        if (owner == this && shared != null) {
            shared.stop();
            sharedUrl = null;
        }
    }

    void toggle() {
        if (owner == this && shared != null) shared.setPlayWhenReady(!shared.getPlayWhenReady());
    }

    void setMuted(boolean m) {
        if (owner == this && shared != null) shared.setVolume(m ? 0f : 1f);
    }

    void release() {
        detach();
        listener = null;
    }
}
