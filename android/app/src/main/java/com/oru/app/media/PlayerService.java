package com.oru.app.media;

import androidx.annotation.OptIn;
import android.net.Uri;
import android.content.Intent;
import android.util.Log;
import android.os.Binder;
import android.os.IBinder;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/**
 * Foreground service that owns the ExoPlayer instance and exposes a MediaSession
 * for system media controls and the in-app player bar.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerService extends MediaSessionService {

    private static final String TAG = "PlayerService";

    private MediaSession mediaSession;
    private ExoPlayer player;

    @Override
    public void onCreate() {
        super.onCreate();

        player = new ExoPlayer.Builder(this).build();

        mediaSession = new MediaSession.Builder(this, player).build();
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    public void loadMedia(String audioUrl, String title, String artist,
            String coverUrl, long id) {
        if (player == null) return;
        player.setMediaItem(MediaItemBuilder.create(
                audioUrl, id, title, artist, coverUrl));
        player.prepare();
        player.play();
    }

    public void play() {
        if (player != null) player.play();
    }

    public void pause() {
        if (player != null) player.pause();
    }

    public void stop() {
        if (player != null) {
            player.stop();
            try {
            } catch (Exception e) {
                Log.w(TAG, "setMediaItem(null) failed", e);
            }
        }
    }

    public ExoPlayer getPlayer() {
        return player;
    }

    public MediaSession getMediaSession() {
        return mediaSession;
    }

    public static class LocalBinder extends Binder {
        private final PlayerService service;

        public LocalBinder(PlayerService service) {
            this.service = service;
        }

        public ExoPlayer getPlayer() {
            return service.getPlayer();
        }

        public MediaSession getMediaSession() {
            return service.getMediaSession();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder(this);
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
