package com.turktv.app;

import android.content.Context;
import android.graphics.Color;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.util.HashMap;
import java.util.Map;

/** Kanal yayınlarını oynatan video oynatıcı (ExoPlayer). */
final class StreamPlayer {

    interface Listener {
        /** 0 bitti, 1 oynuyor, 2 duraklatıldı, 3 yükleniyor */
        void onState(int state);

        void onError();
    }

    final PlayerView view;
    private final Context ctx;
    private ExoPlayer player;
    private Listener listener;
    private int token;

    StreamPlayer(Context c) {
        ctx = c;
        view = new PlayerView(c);
        view.setUseController(false);
        view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        view.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
        view.setShutterBackgroundColor(Color.BLACK);
        view.setBackgroundColor(Color.BLACK);
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);
    }

    void setListener(Listener l) {
        listener = l;
    }

    void play(Channel c, boolean muted) {
        stop();
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
                    // canlı yayının gerisinde kaldı: en güncel noktaya atla
                    player.seekToDefaultPosition();
                    player.prepare();
                    return;
                }
                if (listener != null) listener.onError();
            }
        });
        view.setPlayer(player);
        player.setPlayWhenReady(true);
        player.prepare();
    }

    void stop() {
        token++;
        if (player != null) {
            view.setPlayer(null);
            player.release();
            player = null;
        }
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
