package com.oru.app.ui.components;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;
import com.oru.app.R;
import com.oru.app.databinding.ViewMiniPlayerBinding;
import com.oru.app.media.PlayerService;
import com.oru.app.util.NowPlaying;

import java.util.concurrent.ExecutionException;

/**
 * One mini player for every screen: the song, its artist, a progress line and
 * the controls the web bar carries — shuffle, previous, play, next, repeat.
 *
 * It talks to the media session rather than the player itself, so a tap here
 * moves the same queue the notification and the lock screen show.
 */
public final class MiniPlayer {

    private final Activity activity;
    private final ViewMiniPlayerBinding binding;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private boolean released;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            showProgress();
            if (!released && controller != null && controller.isPlaying()) {
                ticker.postDelayed(this, 500);
            }
        }
    };

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onEvents(Player player, Player.Events events) {
            refresh();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            refresh();
            ticker.removeCallbacks(tick);
            if (isPlaying) {
                ticker.post(tick);
            }
        }
    };

    public MiniPlayer(Activity activity, ViewMiniPlayerBinding binding) {
        this.activity = activity;
        this.binding = binding;
        wireClicks();

        SessionToken token = new SessionToken(activity,
                new ComponentName(activity, PlayerService.class));
        controllerFuture = new MediaController.Builder(activity, token).buildAsync();
        controllerFuture.addListener(() -> {
            MediaController connected;
            try {
                connected = controllerFuture.get();
            } catch (InterruptedException | ExecutionException error) {
                // No session: the bar simply stays hidden.
                return;
            }
            activity.runOnUiThread(() -> {
                if (released) {
                    connected.release();
                    return;
                }
                controller = connected;
                controller.addListener(listener);
                refresh();
            });
        }, ContextCompat.getMainExecutor(activity));
    }

    private void wireClicks() {
        binding.miniPlayerBar.setOnClickListener(v -> {
            Intent intent = new Intent(activity,
                    com.oru.app.ui.player.PlayerActivity.class);
            activity.startActivity(intent);
        });
        binding.miniPlayPause.setOnClickListener(v -> {
            if (controller == null) {
                return;
            }
            if (controller.isPlaying()) {
                controller.pause();
            } else {
                controller.play();
            }
        });
        binding.miniPrevious.setOnClickListener(v -> {
            if (controller == null) {
                return;
            }
            if (controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem();
            } else {
                controller.seekTo(0);
            }
        });
        binding.miniNext.setOnClickListener(v -> {
            if (controller != null && controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem();
            }
        });
        binding.miniShuffle.setOnClickListener(v -> {
            if (controller != null) {
                controller.setShuffleModeEnabled(!controller.getShuffleModeEnabled());
            }
        });
        binding.miniRepeat.setOnClickListener(v -> {
            if (controller == null) {
                return;
            }
            int mode = controller.getRepeatMode();
            controller.setRepeatMode(mode == Player.REPEAT_MODE_OFF
                    ? Player.REPEAT_MODE_ALL
                    : mode == Player.REPEAT_MODE_ALL
                            ? Player.REPEAT_MODE_ONE
                            : Player.REPEAT_MODE_OFF);
        });
    }

    /** Everything the bar shows, refreshed whenever the session changes. */
    private void refresh() {
        if (released) {
            return;
        }
        MediaItem item = controller != null ? controller.getCurrentMediaItem() : null;
        boolean playing = item != null && controller != null
                && controller.getPlaybackState() != Player.STATE_IDLE
                && controller.getPlaybackState() != Player.STATE_ENDED;
        binding.miniPlayerBar.setVisibility(playing ? View.VISIBLE : View.GONE);
        if (!playing) {
            ticker.removeCallbacks(tick);
            return;
        }

        String title = item.mediaMetadata.title != null
                ? item.mediaMetadata.title.toString() : null;
        String artist = item.mediaMetadata.artist != null
                ? item.mediaMetadata.artist.toString() : null;
        // The session can arrive without names on some phones; the app's own
        // copy of what is playing always knows them.
        binding.miniTitle.setText(title != null && !title.isEmpty() ? title
                : orEmpty(NowPlaying.title()));
        binding.miniArtist.setText(artist != null && !artist.isEmpty() ? artist
                : orEmpty(NowPlaying.artist()));

        String cover = item.mediaMetadata.artworkUri != null
                ? item.mediaMetadata.artworkUri.toString()
                : NowPlaying.coverUrl();
        Covers.load(binding.miniCover, cover, R.drawable.ic_music);

        binding.miniPlayPause.setImageResource(controller.isPlaying()
                ? R.drawable.ic_pause : R.drawable.ic_play);
        tint(binding.miniShuffle,
                controller.getShuffleModeEnabled() ? R.color.flame_ink : R.color.ink_faint);
        tint(binding.miniRepeat,
                controller.getRepeatMode() == Player.REPEAT_MODE_OFF
                        ? R.color.ink_faint : R.color.flame_ink);
        binding.miniPrevious.setEnabled(controller.hasPreviousMediaItem());
        binding.miniNext.setEnabled(controller.hasNextMediaItem());
        showProgress();
        ticker.removeCallbacks(tick);
        if (controller.isPlaying()) {
            ticker.post(tick);
        }
    }

    private void showProgress() {
        if (controller == null || released) {
            return;
        }
        long duration = controller.getDuration();
        int percent = 0;
        if (duration > 0) {
            percent = (int) Math.max(0, Math.min(1000,
                    controller.getCurrentPosition() * 1000 / duration));
        }
        binding.miniProgress.setMax(1000);
        binding.miniProgress.setProgressCompat(percent, true);
    }

    private void tint(View view, int color) {
        if (view instanceof android.widget.ImageView) {
            ((android.widget.ImageView) view).setImageTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(activity, color)));
        }
    }

    private static String orEmpty(@Nullable String value) {
        return value != null ? value : "";
    }

    /** Stops listening. Call from the screen's onDestroy. */
    public void release() {
        released = true;
        ticker.removeCallbacks(tick);
        if (controller != null) {
            controller.removeListener(listener);
            controller.release();
            controller = null;
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
    }
}
