package com.oru.app.media;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionCommands;
import androidx.media3.session.SessionResult;
import androidx.media3.session.CommandButton;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import android.os.Bundle;

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

        // Music keeps playing with the screen off, pauses when headphones are
        // unplugged, and gives way to calls and other apps. Without these the
        // system suspends us as soon as the app leaves the screen.
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.setWakeMode(C.WAKE_MODE_NETWORK);
        player.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem item, int reason) {
                remember(item);
            }
        });

        // The session activity is what the notification and lock screen open.
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, PlayerActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(open)
                .setCallback(sessionCallback)
                .build();

        // Shuffle and repeat ride along as custom commands, so the notification,
        // the lock screen and every in-app control drive the same player.
        mediaSession.setCustomLayout(ImmutableList.of(
                new CommandButton.Builder()
                        .setSessionCommand(PlaybackCommands.SHUFFLE)
                        .setIconResId(com.oru.app.R.drawable.ic_shuffle)
                        .setDisplayName("Shuffle")
                        .build(),
                new CommandButton.Builder()
                        .setSessionCommand(PlaybackCommands.REPEAT)
                        .setIconResId(com.oru.app.R.drawable.ic_repeat)
                        .setDisplayName("Repeat")
                        .build()));
        setMediaNotificationProvider(new OruNotificationProvider(this));

        listenTicker.postDelayed(listenTick, LISTEN_REPORT_MS);
    }

    /** Offers the two extra commands and carries them out. */
    private final MediaSession.Callback sessionCallback = new MediaSession.Callback() {
        @Override
        public MediaSession.ConnectionResult onConnect(MediaSession session,
                MediaSession.ControllerInfo controller) {
            SessionCommands commands = MediaSession.ConnectionResult
                    .DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .add(PlaybackCommands.SHUFFLE)
                    .add(PlaybackCommands.REPEAT)
                    .build();
            return new MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(commands)
                    .build();
        }

        @Override
        public ListenableFuture<SessionResult> onCustomCommand(MediaSession session,
                MediaSession.ControllerInfo controller,
                SessionCommand customCommand, Bundle args) {
            Player current = session.getPlayer();
            if (customCommand.equals(PlaybackCommands.SHUFFLE)) {
                current.setShuffleModeEnabled(!current.getShuffleModeEnabled());
                return Futures.immediateFuture(
                        new SessionResult(SessionResult.RESULT_SUCCESS));
            }
            if (customCommand.equals(PlaybackCommands.REPEAT)) {
                current.setRepeatMode(nextRepeatMode(current.getRepeatMode()));
                return Futures.immediateFuture(
                        new SessionResult(SessionResult.RESULT_SUCCESS));
            }
            return Futures.immediateFuture(
                    new SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED));
        }
    };

    /** Keeps the in-app name fallback in step with what the session plays. */
    private static void remember(@Nullable MediaItem item) {
        if (item == null || item.mediaMetadata == null) {
            return;
        }
        int id = 0;
        try {
            id = item.mediaId != null ? Integer.parseInt(item.mediaId) : 0;
        } catch (NumberFormatException ignored) {
            // A non-song item still shows its own metadata.
        }
        androidx.media3.common.MediaMetadata metadata = item.mediaMetadata;
        com.oru.app.util.NowPlaying.remember(id,
                metadata.title != null ? metadata.title.toString() : null,
                metadata.artist != null ? metadata.artist.toString() : null,
                metadata.artworkUri != null ? metadata.artworkUri.toString() : null);
    }

    /** Off, then the whole queue, then this one song, then off again. */
    static int nextRepeatMode(int repeatMode) {
        if (repeatMode == Player.REPEAT_MODE_OFF) {
            return Player.REPEAT_MODE_ALL;
        }
        if (repeatMode == Player.REPEAT_MODE_ALL) {
            return Player.REPEAT_MODE_ONE;
        }
        return Player.REPEAT_MODE_OFF;
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    /**
     * Swiping the app away from recents must not silence the music; only stop
     * the service when nothing is playing any more.
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Player current = mediaSession != null ? mediaSession.getPlayer() : null;
        boolean playingOn = current != null && current.getPlayWhenReady()
                && current.getPlaybackState() != Player.STATE_IDLE;
        if (!playingOn) {
            stopSelf();
        }
        super.onTaskRemoved(rootIntent);
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
