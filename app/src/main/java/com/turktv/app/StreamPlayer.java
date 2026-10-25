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
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.media3.common.C;
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
import androidx.media3.ui.AspectRatioFrameLayout;
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
    // ekranlar arası geçişte son kare: yeni ekran ilk karesini çizene kadar bu gösterilir (kararma olmaz)
    private static Bitmap handoff;
    private static String handoffUrl;
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
            // Kesintisiz geçiş: önceki ekranın son karesi, bu ekranın ilk karesi gelene kadar üstte kalır
            if (freeze.getVisibility() == View.VISIBLE) {
                cover.setVisibility(View.GONE);
                ui.postDelayed(() -> {
                    if (owner == this) unfreeze();
                }, 900);
            } else if (!hasFrame) {
                cover.setAlpha(1f);
                cover.setVisibility(View.VISIBLE);
            } else {
                cover.setVisibility(View.GONE);
            }
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
                        .setTargetOffsetMs(LIVE_OFFSET_MS)
                        // hız ayarı yok: geri sarınca kendiliğinden canlıya yetişmeye çalışmasın
                        .setMinPlaybackSpeed(1f)
                        .setMaxPlaybackSpeed(1f)
                        .build());
        String lu = c.url.toLowerCase(Actions.TR);
        if (lu.contains("m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (lu.contains(".mpd")) item.setMimeType(MimeTypes.APPLICATION_MPD);
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
                // son 2 dakikayı bellekte tut: geri sarma anında olsun
                .setBackBuffer(120000, true)
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
        Bitmap carry = null;
        if (owner != null && owner != this) {
            carry = owner.grab();
            owner.playerView.setPlayer(null);
        } else if (owner == null && handoff != null && handoffUrl != null && handoffUrl.equals(sharedUrl)) {
            carry = handoff;
            handoff = null;
        }
        if (handoff != null && carry != handoff) {
            handoff.recycle();
            handoff = null;
        }
        if (carry != null && owner != this) showFrozen(carry);
        owner = this;
        ExoPlayer p = ensureShared();
        if (playerView.getPlayer() != p) playerView.setPlayer(p);
    }

    // ------------------------------------------------------------------ donuk kare

    /** O an ekrandaki kareyi yakalayıp üstte donuk gösterir. */
    private void captureFrame() {
        if (!hasFrame || freeze.getVisibility() == View.VISIBLE) return;
        View sv = playerView.getVideoSurfaceView();
        if (sv == null || sv.getWidth() <= 0 || sv.getHeight() <= 0) return;
        if (sv instanceof TextureView) {
            try {
                // yarı çözünürlük yeter (geçiş anında kısa süre görünür), bellek dostu
                Bitmap b = ((TextureView) sv).getBitmap(Math.max(1, sv.getWidth() / 2), Math.max(1, sv.getHeight() / 2));
                if (b != null) showFrozen(b);
            } catch (Exception ignored) {
            }
            return;
        }
        if (!(sv instanceof SurfaceView) || Build.VERSION.SDK_INT < 24) return;
        try {
            final Bitmap b = Bitmap.createBitmap(sv.getWidth(), sv.getHeight(), Bitmap.Config.ARGB_8888);
            PixelCopy.request((SurfaceView) sv, b, result -> {
                if (result != PixelCopy.SUCCESS || hasFrame) {
                    b.recycle();
                    return;
                }
                showFrozen(b);
            }, ui);
        } catch (Exception ignored) {
        }
    }

    private void showFrozen(Bitmap b) {
        freeze.animate().cancel();
        freeze.setImageBitmap(b);
        if (frozen != null && frozen != b) frozen.recycle();
        frozen = b;
        freeze.setAlpha(1f);
        freeze.setVisibility(View.VISIBLE);
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
    /** Bu ekrandaki o anki görüntü (geçişte yeni ekrana taşınır). */
    private Bitmap grab() {
        if (!hasFrame) return null;
        View sv = playerView.getVideoSurfaceView();
        if (!(sv instanceof TextureView) || sv.getWidth() <= 0) return null;
        try {
            return ((TextureView) sv).getBitmap(Math.max(1, sv.getWidth() / 2), Math.max(1, sv.getHeight() / 2));
        } catch (Exception e) {
            return null;
        }
    }

    void detach() {
        if (owner == this) {
            Bitmap b = grab();
            if (b != null) {
                if (handoff != null) handoff.recycle();
                handoff = b;
                handoffUrl = sharedUrl;
            }
        }
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

    // ------------------------------------------------------------------ geri sarma

    static final long LIVE_OFFSET_MS = 10000;

    /** Canlı yayının ne kadar gerisinde (ms, normal gecikme hariç); bilinmiyorsa -1. */
    long behindLiveMs() {
        if (owner != this || shared == null || !shared.isCurrentMediaItemLive()) return -1;
        long off = shared.getCurrentLiveOffset();
        if (off == C.TIME_UNSET) return -1;
        return Math.max(0, off - LIVE_OFFSET_MS);
    }

    /**
     * ms kadar ileri (+) ya da geri (−) sarar. İleri sararken canlıya ulaşırsa canlıya döner.
     * Geri sarılacak yer kalmadıysa false döner.
     */
    boolean seekBy(long ms) {
        if (owner != this || shared == null) return false;
        long pos = shared.getCurrentPosition();
        long dur = shared.getDuration();
        if (!shared.isCurrentMediaItemLive() && dur != C.TIME_UNSET && dur > 0) { // video (canlı değil)
            shared.seekTo(Math.max(0, Math.min(dur - 1000, pos + ms)));
            return true;
        }
        if (ms > 0) {
            if (dur == C.TIME_UNSET || pos + ms >= dur - LIVE_OFFSET_MS) goLive();
            else shared.seekTo(pos + ms);
            return true;
        }
        if (pos <= 500) return false;
        shared.seekTo(Math.max(0, pos + ms));
        return true;
    }

    /** Zaman çizgisi için {oynatma konumu, geri sarılabilir pencere uzunluğu} (ms); bilinmiyorsa null. */
    long[] timeline() {
        if (owner != this || shared == null || !shared.isCurrentMediaItemLive()) return null;
        long dur = shared.getDuration();
        if (dur == C.TIME_UNSET || dur <= 0) return null;
        return new long[]{shared.getCurrentPosition(), dur};
    }

    /** Canlı olmayan video için {konum, süre} (ms); bilinmiyorsa null. */
    long[] position() {
        if (owner != this || shared == null || shared.isCurrentMediaItemLive()) return null;
        long dur = shared.getDuration();
        if (dur == C.TIME_UNSET || dur <= 0) return null;
        return new long[]{shared.getCurrentPosition(), dur};
    }

    boolean isPlaying() {
        return owner == this && shared != null && shared.getPlayWhenReady();
    }

    void goLive() {
        if (owner == this && shared != null) shared.seekToDefaultPosition();
    }

    void toggle() {
        if (owner == this && shared != null) shared.setPlayWhenReady(!shared.getPlayWhenReady());
    }

    void setMuted(boolean m) {
        if (owner == this && shared != null) shared.setVolume(m ? 0f : 1f);
    }

    /** 0 sığdır, 1 doldur, 2 yakınlaştır */
    void setResizeMode(int m) {
        playerView.setResizeMode(m == 1 ? AspectRatioFrameLayout.RESIZE_MODE_FILL
                : m == 2 ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM : AspectRatioFrameLayout.RESIZE_MODE_FIT);
    }

    void release() {
        detach();
        listener = null;
    }
}
