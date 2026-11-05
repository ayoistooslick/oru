package com.oru.app.ui.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import com.google.android.material.slider.Slider;
import com.oru.app.R;
import com.oru.app.data.offline.Downloads;
import com.oru.app.data.model.LikeResult;
import com.oru.app.data.model.Song;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.CommunityRepository;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.databinding.ActivityPlayerBinding;
import com.oru.app.media.PlayerService;
import com.oru.app.ui.components.UiErrors;
import com.oru.app.util.LazySession;

import java.util.concurrent.atomic.AtomicBoolean;

/** The full player: transport, scrubbing, like and share. */
public class PlayerActivity extends AppCompatActivity {

    private ActivityPlayerBinding binding;
    private androidx.media3.exoplayer.ExoPlayer player;
    private androidx.media3.session.MediaSession mediaSession;
    private AtomicBoolean serviceBound = new AtomicBoolean(false);
    private Song currentSong;
    private int songLoadGeneration;
    /** The list this song came from, as ids, so next/previous walk it. */
    private int[] queueIds;
    private int queueIndex = -1;

    private MusicRepository musicRepo;
    private CommunityRepository communityRepo;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            ticker.postDelayed(this, 500);
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            updatePlayPauseButtons();
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_ENDED) {
                onTrackEnded();
            }
        }

        @Override
        public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
            // A broken stream used to fail silently; say what happened.
            if (binding == null) {
                return;
            }
            Toast.makeText(PlayerActivity.this,
                    "This song couldn't play (" + error.getErrorCodeName()
                            + "). Try again.",
                    Toast.LENGTH_LONG).show();
            updatePlayPauseButtons();
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            updateModeButtons();
        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            updateModeButtons();
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlayerService.LocalBinder binder = (PlayerService.LocalBinder) service;
            player = binder.getPlayer();
            mediaSession = binder.getMediaSession();
            serviceBound.set(true);
            player.addListener(playerListener);
            if (currentSong != null) {
                loadSong(currentSong);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound.set(false);
            player = null;
            mediaSession = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        musicRepo = new MusicRepository(LazySession.getApiService(),
                LazySession.getGson());
        communityRepo = new CommunityRepository(LazySession.getApiService(),
                LazySession.getGson());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.playButton.setOnClickListener(v -> togglePlay());
        binding.miniPlayPauseButton.setOnClickListener(v -> togglePlay());
        binding.previousButton.setOnClickListener(v -> skip(-1));
        binding.nextButton.setOnClickListener(v -> skip(1));
        // Holding a skip button still nudges 15 seconds, like the web player.
        binding.previousButton.setOnLongClickListener(v -> {
            jump(-1);
            return true;
        });
        binding.nextButton.setOnLongClickListener(v -> {
            jump(1);
            return true;
        });
        binding.shuffleButton.setOnClickListener(v -> toggleShuffle());
        binding.repeatButton.setOnClickListener(v -> cycleRepeat());
        binding.likeButton.setOnClickListener(v -> toggleLike());
        binding.miniLikeButton.setOnClickListener(v -> toggleLike());
        binding.shareButton.setOnClickListener(v -> shareSong());

        binding.toolbar.inflateMenu(R.menu.player_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_download) {
                toggleDownload();
                return true;
            }
            if (item.getItemId() == R.id.action_report) {
                askReportReason();
                return true;
            }
            return false;
        });

        binding.progressSlider.addOnSliderTouchListener(
                new Slider.OnSliderTouchListener() {
                    @Override
                    public void onStartTrackingTouch(@NonNull Slider slider) {
                        ticker.removeCallbacks(tick);
                    }

                    @Override
                    public void onStopTrackingTouch(@NonNull Slider slider) {
                        if (player != null) {
                            long duration = player.getDuration();
                            if (duration > 0) {
                                player.seekTo((long) (duration * slider.getValue() / 100f));
                            }
                        }
                        ticker.post(tick);
                    }
                });

        askForNotifications();

        Intent intent = new Intent(this, PlayerService.class);
        try {
            // Started as well as bound, so the music keeps playing when the
            // listener leaves the player screen.
            startService(intent);
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException error) {
            // Playback still works inside the player without the service.
            serviceBound.set(false);
        }

        openPlaybackIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openPlaybackIntent(intent);
    }

    private void openPlaybackIntent(Intent intent) {
        queueIds = intent.getIntArrayExtra("songIds");
        queueIndex = intent.getIntExtra("songIndex", -1);
        int songId = intent.getIntExtra("songId", -1);
        if (songId <= 0 && queueIds != null && queueIndex >= 0
                && queueIndex < queueIds.length) {
            songId = queueIds[queueIndex];
        }
        if (songId > 0) {
            loadSongById(songId);
        } else {
            showError(new IllegalArgumentException(
                    "Choose a song with playable audio to start listening."));
        }
    }

    /** Next or previous track in the list that opened the player. */
    private void skip(int direction) {
        if (queueIds == null || queueIds.length == 0 || queueIndex < 0) {
            jump(direction);
            return;
        }
        int next = queueIndex + direction;
        if (next < 0 || next >= queueIds.length) {
            Toast.makeText(this,
                    direction > 0 ? "That's the last song" : "That's the first song",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        queueIndex = next;
        loadSongById(queueIds[next]);
    }

    private void onTrackEnded() {
        if (queueIds != null && queueIndex >= 0
                && queueIndex + 1 < queueIds.length) {
            skip(1);
        }
    }

    private void loadSongById(int songId) {
        final int generation = ++songLoadGeneration;
        binding.progressBar.setVisibility(View.VISIBLE);
        musicRepo.getSong(songId, new Callback<Song>() {
            @Override
            public void onSuccess(Song song) {
                runOnUiThread(() -> {
                    if (binding == null || isFinishing()
                            || generation != songLoadGeneration) {
                        return;
                    }
                    binding.progressBar.setVisibility(View.GONE);
                    if (song == null) {
                        showError(new IllegalStateException(
                                "The song details were empty. Please try again."));
                        return;
                    }
                    currentSong = song;
                    if (queueIds != null) {
                        for (int i = 0; i < queueIds.length; i++) {
                            if (queueIds[i] == song.id) {
                                queueIndex = i;
                                break;
                            }
                        }
                    }
                    populateUI(song);
                    if (serviceBound.get() && player != null) {
                        loadSong(song);
                    }
                });
            }

            @Override
            public void onFailure(Throwable error) {
                runOnUiThread(() -> {
                    if (binding == null || isFinishing()
                            || generation != songLoadGeneration) {
                        return;
                    }
                    binding.progressBar.setVisibility(View.GONE);
                    // With no connection the saved copy still plays.
                    Song saved = Downloads.song(PlayerActivity.this, songId);
                    if (saved != null) {
                        currentSong = saved;
                        populateUI(saved);
                        if (serviceBound.get() && player != null) {
                            loadSong(saved);
                        }
                        return;
                    }
                    showError(error);
                });
            }
        });
    }

    /** Android 13+ hides the media notification until this is granted. */
    private void askForNotifications() {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        androidx.core.app.ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 41);
    }

    /** Saves the song to the device, or forgets the saved copy. */
    private void toggleDownload() {
        if (currentSong == null) {
            return;
        }
        final Song song = currentSong;
        if (Downloads.isDownloaded(this, song.id)) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Remove download?")
                    .setMessage("\"" + song.title
                            + "\" will be deleted from this device.")
                    .setNegativeButton("Keep", null)
                    .setPositiveButton("Remove", (dialog, which) -> {
                        Downloads.remove(this, song.id);
                        refreshDownloadMenu();
                    })
                    .show();
            return;
        }
        Toast.makeText(this, "Saving for offline…", Toast.LENGTH_SHORT).show();
        Downloads.download(this, song, percent -> {
            if (binding == null) {
                return;
            }
            android.view.MenuItem item = binding.toolbar.getMenu()
                    .findItem(R.id.action_download);
            if (item != null) {
                item.setTitle("Saving " + percent + "%");
            }
        }, (ok, message) -> {
            if (binding == null) {
                return;
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            refreshDownloadMenu();
        });
        refreshDownloadMenu();
    }

    private void refreshDownloadMenu() {
        if (binding == null) {
            return;
        }
        android.view.MenuItem item = binding.toolbar.getMenu()
                .findItem(R.id.action_download);
        if (item == null) {
            return;
        }
        if (currentSong == null) {
            item.setVisible(false);
            return;
        }
        boolean saving = Downloads.isDownloading(currentSong.id);
        item.setVisible(true);
        item.setEnabled(!saving);
        if (saving) {
            item.setTitle("Saving…");
        } else if (Downloads.isDownloaded(this, currentSong.id)) {
            item.setTitle("Remove download");
        } else {
            item.setTitle("Save for offline");
        }
    }

    private void populateUI(Song song) {
        binding.titleText.setText(song.title);
        binding.artistText.setText(song.artistLabel());
        String length = song.durationLabel();
        String genres = song.genreLabel();
        binding.albumText.setText(length.isEmpty() ? genres
                : genres.isEmpty() ? length : length + " · " + genres);
        binding.totalTimeText.setText(length.isEmpty() ? "0:00" : length);
        Object cover = Downloads.localCover(this, song.id);
        if (cover == null) {
            cover = com.oru.app.util.MediaUrl.of(song.coverUrl);
        }
        if (cover != null) {
            com.bumptech.glide.Glide.with(this)
                    .load(cover)
                    .placeholder(R.drawable.ic_library)
                    .into(binding.coverImage);
            com.bumptech.glide.Glide.with(this)
                    .load(cover)
                    .placeholder(R.drawable.ic_library)
                    .into(binding.miniArtwork);
        }
        if (queueIds != null && queueIds.length > 1 && queueIndex >= 0) {
            binding.queueText.setText("Track " + (queueIndex + 1) + " of "
                    + queueIds.length);
            binding.queueText.setVisibility(View.VISIBLE);
        } else {
            binding.queueText.setVisibility(View.GONE);
        }
        binding.miniTitle.setText(song.title);
        binding.miniArtist.setText(song.artistLabel());
        updatePlayPauseButtons();
        updateLikeButtons();
        updateModeButtons();
        refreshDownloadMenu();
    }

    private void loadSong(Song song) {
        if (player == null || song == null || binding == null) {
            return;
        }
        try {
            MediaItem mediaItem = com.oru.app.media.MediaItemBuilder.create(this,
                    com.oru.app.util.MediaUrl.of(song.audioUrl), song.id,
                    song.title, song.artistLabel(),
                    com.oru.app.util.MediaUrl.of(song.coverUrl));
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();
            updatePlayPauseButtons();
            binding.miniPlayerBar.setVisibility(View.VISIBLE);
            ticker.removeCallbacks(tick);
            ticker.post(tick);
        } catch (RuntimeException error) {
            showError(error);
        }
    }

    private void togglePlay() {
        if (player == null) {
            return;
        }
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
        updatePlayPauseButtons();
    }

    /** A single track, so these skip 15 seconds either way. */
    private void jump(int direction) {
        if (player == null) {
            return;
        }
        long target = player.getCurrentPosition() + direction * 15_000L;
        long duration = player.getDuration();
        if (target < 0) {
            target = 0;
        }
        if (duration > 0 && target > duration) {
            target = duration;
        }
        player.seekTo(target);
        updateProgress();
    }

    private void toggleShuffle() {
        if (player == null) {
            return;
        }
        player.setShuffleModeEnabled(!player.getShuffleModeEnabled());
        updateModeButtons();
    }

    private void cycleRepeat() {
        if (player == null) {
            return;
        }
        int next = player.getRepeatMode() == Player.REPEAT_MODE_OFF
                ? Player.REPEAT_MODE_ALL
                : player.getRepeatMode() == Player.REPEAT_MODE_ALL
                        ? Player.REPEAT_MODE_ONE
                        : Player.REPEAT_MODE_OFF;
        player.setRepeatMode(next);
        updateModeButtons();
    }

    private void toggleLike() {
        if (currentSong == null) {
            return;
        }
        boolean liked = currentSong.isLiked;
        Callback<LikeResult> done = new Callback<LikeResult>() {
            @Override
            public void onSuccess(LikeResult result) {
                runOnUiThread(() -> {
                    currentSong.isLiked = result != null ? result.liked
                            : !liked;
                    if (result != null) {
                        currentSong.likesCount = result.likesCount;
                    }
                    updateLikeButtons();
                });
            }

            @Override
            public void onFailure(Throwable error) {
                runOnUiThread(() -> Toast.makeText(PlayerActivity.this,
                        UiErrors.message(PlayerActivity.this, error),
                        Toast.LENGTH_LONG).show());
            }
        };
        if (liked) {
            communityRepo.unlikeSong(currentSong.id, done);
        } else {
            communityRepo.likeSong(currentSong.id, done);
        }
    }

    private void shareSong() {
        if (currentSong == null) {
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, currentSong.title);
        send.putExtra(Intent.EXTRA_TEXT, currentSong.title + " by "
                + currentSong.artistLabel() + " on ORU — "
                + "https://oru.freebuff.app");
        startActivity(Intent.createChooser(send, "Share this song"));
    }

    private void askReportReason() {
        if (currentSong == null) {
            return;
        }
        final String[] reasons = {"copyright", "harassment", "spam",
                "impersonation", "other"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Report " + currentSong.title)
                .setItems(reasons, (dialog, which) -> askReportDetails(
                        reasons[which]))
                .setNegativeButton(com.oru.app.R.string.cancel, null)
                .show();
    }

    private void askReportDetails(String reason) {
        final android.widget.EditText details = new android.widget.EditText(this);
        details.setHint("Anything that helps us look");
        details.setMinLines(2);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Report as " + reason)
                .setView(details)
                .setNegativeButton(com.oru.app.R.string.cancel, null)
                .setPositiveButton("Send", (dialog, which) -> {
                    com.oru.app.data.repository.ModerationRepository reports =
                            new com.oru.app.data.repository.ModerationRepository(
                                    LazySession.getApiService(),
                                    LazySession.getGson());
                    reports.createReport("song", currentSong.id, reason,
                            details.getText().toString().trim(),
                            new Callback<com.oru.app.data.model.Report>() {
                                @Override
                                public void onSuccess(
                                        com.oru.app.data.model.Report report) {
                                    runOnUiThread(() -> Toast.makeText(
                                            PlayerActivity.this,
                                            "Report sent — thank you",
                                            Toast.LENGTH_LONG).show());
                                }

                                @Override
                                public void onFailure(Throwable error) {
                                    runOnUiThread(() -> Toast.makeText(
                                            PlayerActivity.this,
                                            UiErrors.message(PlayerActivity.this,
                                                    error),
                                            Toast.LENGTH_LONG).show());
                                }
                            });
                })
                .show();
    }

    private void updateLikeButtons() {
        int tint = currentSong != null && currentSong.isLiked
                ? R.color.flame : R.color.ink_soft;
        binding.likeButton.setImageTintList(
                android.content.res.ColorStateList.valueOf(getColor(tint)));
        binding.miniLikeButton.setImageTintList(
                android.content.res.ColorStateList.valueOf(getColor(tint)));
    }

    private void updateModeButtons() {
        if (player == null) {
            return;
        }
        int active = R.color.flame;
        int idle = R.color.ink_faint;
        binding.shuffleButton.setImageTintList(
                android.content.res.ColorStateList.valueOf(getColor(
                        player.getShuffleModeEnabled() ? active : idle)));
        binding.repeatButton.setImageTintList(
                android.content.res.ColorStateList.valueOf(getColor(
                        player.getRepeatMode() == Player.REPEAT_MODE_OFF
                                ? idle : active)));
    }

    private void updateProgress() {
        if (player == null) {
            return;
        }
        long position = player.getCurrentPosition();
        long duration = player.getDuration();
        if (duration > 0) {
            // Material sliders only accept values on their step grid.
            float percent = Math.max(0f, Math.min(100f,
                    Math.round(position * 100f / duration)));
            if (binding.progressSlider.getValue() != percent) {
                binding.progressSlider.setValue(percent);
            }
            if (binding.miniProgressSlider.getValue() != percent) {
                binding.miniProgressSlider.setValue(percent);
            }
            binding.totalTimeText.setText(clock(duration));
        }
        binding.currentTimeText.setText(clock(position));
        updatePlayPauseButtons();
    }

    private static String clock(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(java.util.Locale.US, "%d:%02d", seconds / 60,
                seconds % 60);
    }

    private void updatePlayPauseButtons() {
        if (player == null) {
            return;
        }
        boolean playing = player.isPlaying();
        binding.playButton.setImageResource(
                playing ? R.drawable.ic_pause : R.drawable.ic_play);
        binding.miniPlayPauseButton.setImageResource(
                playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void showError(Throwable error) {
        binding.errorView.getRoot().setVisibility(View.VISIBLE);
        binding.errorView.errorMessage.setText(
                UiErrors.message(this, error));
        binding.errorView.retryButton.setOnClickListener(v -> {
            binding.errorView.getRoot().setVisibility(View.GONE);
            int songId = getIntent().getIntExtra("songId", -1);
            if (songId > 0) {
                loadSongById(songId);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ticker.removeCallbacks(tick);
        if (player != null) {
            player.removeListener(playerListener);
        }
        try {
            unbindService(connection);
        } catch (IllegalArgumentException ignored) {
            // Never bound, nothing to release.
        }
        serviceBound.set(false);
    }
}
