package com.turktv.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.HashMap;
import java.util.Map;

/**
 * Kanal yayınlarını oynatan video oynatıcı (ExoPlayer).
 * Kanal değişirken önceki kanalın son karesi donuk kalır, yeni görüntü gelince yumuşakça geçer.
 */
final class StreamPlayer {

    interface Listener {
        /** 0 bitti, 1 oynuyor, 2 duraklatıldı, 3 yükleniyor */
        void onState(int state);

        void onError();
    }

    /** Ekrana eklenecek görünüm (video + donuk kare katmanı). */
    final FrameLayout view;
    private final PlayerView playerView;
    private final ImageView freeze;
    private final Context ctx;
    private ExoPlayer player;
    private Listener listener;
    private int token;
    private boolean hasFrame;
    private Bitmap frozen;

    StreamPlayer(Context c) {
        ctx = c;
        view = new FrameLayout(c);
        view.setBackgroundColor(Color.BLACK);
        view.setFocusable(false);
        playerView = (PlayerView) LayoutInflater.from(c).inflate(R.layout.stream_view, view, false);
        view.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
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

    void play(Channel c, boolean muted) {
        captureFrame();
        releasePlayer();
        final int t = ++token;

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(c.ua != null ? c.ua : Health.DEFAULT_UA)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(15000);
        if (c.referer != null) {
            Map<String, String> h = new HashMap<>();
            h.put("Referer", c.referer);
            http.setDefaultRequestProperties(h);
        }

        player = new ExoPlayer.Builder(ctx)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .build();
        MediaItem.Builder item = new MediaItem.Builder().setUri(c.url);
        if (c.url.toLowerCase(Actions.TR).contains("m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        player.setMediaItem(item.build());
        player.setVolume(muted ? 0f : 1f);
        player.addListener(new Player.Listener() {
            @Override
            public void onRenderedFirstFrame() {
                if (t != token) return;
                hasFrame = true;
                unfreeze();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (t != token || listener == null) return;
                if (state == Player.STATE_BUFFERING) listener.onState(3);
                else if (state == Player.STATE_ENDED) listener.onState(0);
            }

            @Override
            public void onIsPlayingChanged(boolean playing) {
                if (t != token || listener == null || player == null) return;
                if (playing) listener.onState(1);
                else if (player.getPlaybackState() == Player.STATE_READY && !player.getPlayWhenReady()) listener.onState(2);
            }

            @Override
            public void onPlayerError(PlaybackException err) {
                if (t != token || player == null) return;
                if (err.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition();
                    player.prepare();
                    return;
                }
                if (listener != null) listener.onError();
            }
        });
        playerView.setPlayer(player);
        player.setPlayWhenReady(true);
        player.prepare();
    }

    /** O an ekrandaki kareyi yakalayıp üstte donuk gösterir. */
    private void captureFrame() {
        if (player == null || !hasFrame) return;
        View sv = playerView.getVideoSurfaceView();
        if (!(sv instanceof TextureView)) return;
        try {
            Bitmap b = ((TextureView) sv).getBitmap();
            if (b == null) return;
            freeze.animate().cancel();
            freeze.setImageBitmap(b);
            if (frozen != null && frozen != b) frozen.recycle();
            frozen = b;
            freeze.setAlpha(1f);
            freeze.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {
        }
    }

    private void unfreeze() {
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

    private void releasePlayer() {
        token++;
        hasFrame = false;
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    /** Tamamen durdurur (ekrandan çıkarken). */
    void stop() {
        releasePlayer();
        clearFreeze();
    }

    /** Hata durumunda: oynatmayı bırakır ama son kare donuk kalır. */
    void stopKeepFrame() {
        captureFrame();
        releasePlayer();
    }

    void toggle() {
        if (player != null) player.setPlayWhenReady(!player.getPlayWhenReady());
    }

    void setMuted(boolean m) {
        if (player != null) player.setVolume(m ? 0f : 1f);
    }

    void release() {
        stop();
        listener = null;
    }
}
