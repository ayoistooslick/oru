package com.oru.app.media;

import androidx.annotation.OptIn;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.ui.player.PlayerActivity;
import com.oru.app.util.LazySession;

/**
 * Foreground service that owns the ExoPlayer instance and exposes a MediaSession
 * so the system shows the media notification and the lock screen controls, and
 * playback carries on when the app is not on screen.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerService extends MediaSessionService {

    private static final String TAG = "PlayerService";
    /** The server counts a listen once 30 seconds have played. */
    private static final long LISTEN_REPORT_MS = 30_000L;

    private MediaSession mediaSession;
    private ExoPlayer player;

    private final Handler listenTicker = new Handler(Looper.getMainLooper());
    private final Runnable listenTick = new Runnable() {
        @Override
        public void run() {
            reportListen();
            listenTicker.postDelayed(this, LISTEN_REPORT_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        player = new ExoPlayer.Builder(this).build();

        // The session activity is what the notification and lock screen open.
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, PlayerActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(open)
                .build();

        listenTicker.postDelayed(listenTick, LISTEN_REPORT_MS);
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    public void loadMedia(String audioUrl, String title, String artist,
            String coverUrl, long id) {
        if (player == null) {
            return;
        }
        // The saved copy wins when the song is downloaded.
        player.setMediaItem(MediaItemBuilder.create(this,
                audioUrl, id, title, artist, coverUrl));
        player.prepare();
        player.play();
    }

    public void play() {
        if (player != null) {
            player.play();
        }
    }

    public void pause() {
        if (player != null) {
            player.pause();
        }
    }

    public void stop() {
        if (player != null) {
            player.stop();
        }
    }

    public ExoPlayer getPlayer() {
        return player;
    }

    public MediaSession getMediaSession() {
        return mediaSession;
    }

    /** Counts the play the way the web player does, so rankings match. */
    private void reportListen() {
        if (player == null || !player.isPlaying()) {
            return;
        }
        MediaItem item = player.getCurrentMediaItem();
        if (item == null || item.mediaId == null) {
            return;
        }
        long position = player.getCurrentPosition();
        if (position < LISTEN_REPORT_MS) {
            return;
        }
        int songId;
        try {
            songId = Integer.parseInt(item.mediaId);
        } catch (NumberFormatException ignored) {
            return;
        }
        try {
            new MusicRepository(LazySession.getApiService(), LazySession.getGson())
                    .recordListen(songId, (int) position,
                            new Callback<com.oru.app.data.model.ListenRecord>() {
                        @Override
                        public void onSuccess(
                                com.oru.app.data.model.ListenRecord ignored) {
                        }

                        @Override
                        public void onFailure(Throwable error) {
                            Log.w(TAG, "Listen not recorded", error);
                        }
                    });
        } catch (RuntimeException error) {
            // No session yet: the play is not counted, nothing else breaks.
            Log.w(TAG, "Listen not recorded", error);
        }
    }

    @Override
    public void onDestroy() {
        listenTicker.removeCallbacks(listenTick);
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
