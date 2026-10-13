package com.turktv.app;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;

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

/** M3U listesindeki yayın adreslerini oynatan yerel oynatıcı (ExoPlayer). */
final class StreamPlayer {

    final PlayerView view;
    private final Context ctx;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private YtPlayer.Listener listener;
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

    void setListener(YtPlayer.Listener l) {
        listener = l;
    }

    void play(Playlist.Entry e, boolean muted) {
        stop();
        final int t = ++token;

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(e.ua != null ? e.ua : Playlist.DEFAULT_UA)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(15000);
        if (e.referer != null) {
            Map<String, String> h = new HashMap<>();
            h.put("Referer", e.referer);
            http.setDefaultRequestProperties(h);
        }

        player = new ExoPlayer.Builder(ctx)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .build();
        MediaItem.Builder item = new MediaItem.Builder().setUri(e.url);
        if (e.url.toLowerCase(Actions.TR).contains("m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
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
                if (listener != null) listener.onError(-1);
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
