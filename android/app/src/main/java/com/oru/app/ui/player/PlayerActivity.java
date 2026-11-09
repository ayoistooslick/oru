package com.oru.app.ui.player;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.slider.Slider;
import com.oru.app.R;
import com.oru.app.data.offline.Downloads;
import com.oru.app.data.model.LikeResult;
import com.oru.app.data.model.Song;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.CommunityRepository;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.databinding.ActivityPlayerBinding;
import com.oru.app.media.PlaybackCommands;
import com.oru.app.media.PlayerService;
import com.oru.app.ui.components.UiErrors;
import com.oru.app.util.LazySession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The full player: transport, scrubbing, like and share. */
public class PlayerActivity extends AppCompatActivity {

    private ActivityPlayerBinding binding;
    private Player player;
    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private Song currentSong;
    private int songLoadGeneration;
    /** The list the session is playing, so next/previous walk it. */
    private List<Song> queue;
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
        public void onMediaItemTransition(@Nullable MediaItem item, int reason) {
            if (item == null) {
                return;
            }
            // The session moved on by itself (queue, notification, lock screen).
            syncWithPlayer();
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

    /**
     * Connects through a MediaController rather than a local binder, because the
     * media notification and the lock screen controls are only published for a
     * session the system can see.
     */
    private void connectPlayer() {
        SessionToken token = new SessionToken(this,
                new ComponentName(this, PlayerService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            MediaController connected;
            try {
                connected = controllerFuture.get();
            } catch (Exception error) {
                runOnUiThread(() -> showError(new IllegalStateException(
                        "The player could not start (" + error
                                + "). Reopen the song to try again.")));
                return;
            }
            runOnUiThread(() -> {
                if (binding == null) {
                    return;
                }
                controller = connected;
                player = connected;
                player.addListener(playerListener);
                if (queue != null) {
                    playQueue();
                } else {
                    resumeCurrentTrack();
                }
                updatePlayPauseButtons();
                updateModeButtons();
            });
        }, androidx.core.content.ContextCompat.getMainExecutor(this));
    }

    /**
     * Opened from the notification, the lock screen or the player bar: pick up
     * whatever the session is already playing, without starting it over.
     */
    private void resumeCurrentTrack() {
        MediaItem item = player != null ? player.getCurrentMediaItem() : null;
        if (item == null || item.mediaId == null) {
            showError(new IllegalArgumentException(
                    "Choose a song with playable audio to start listening."));
            return;
        }
        syncWithPlayer();
    }

    /** Shows what the session is playing. Never restarts or seeks. */
    private void syncWithPlayer() {
        if (player == null || binding == null) {
            return;
        }
        MediaItem item = player.getCurrentMediaItem();
        if (item == null || item.mediaId == null) {
            return;
        }
        int id;
        try {
            id = Integer.parseInt(item.mediaId);
        } catch (NumberFormatException error) {
            return;
        }
        Song known = songInQueue(id);
        if (known != null) {
            currentSong = known;
            populateUI(known);
        } else {
            // Opened cold from the notification: read the details, keep playing.
            loadSongById(id, true);
        }
        startTicker();
    }

    private Song songInQueue(int songId) {
        if (queue == null) {
            return null;
        }
        for (Song song : queue) {
            if (song.id == songId) {
                return song;
            }
        }
        return null;
    }

    private void startTicker() {
        ticker.removeCallbacks(tick);
        ticker.post(tick);
    }

    /** Hands the tapped list to the session and starts on the tapped song. */
    private void playQueue() {
        if (player == null || queue == null || queue.isEmpty() || binding == null) {
            return;
        }
        int wanted = Math.max(0, Math.min(queueIndex, queue.size() - 1));
        List<MediaItem> items = new ArrayList<>(queue.size());
        List<Song> playable = new ArrayList<>(queue.size());
        int at = 0;
        for (int i = 0; i < queue.size(); i++) {
            Song song = queue.get(i);
            try {
                items.add(com.oru.app.media.MediaItemBuilder.create(this,
                        com.oru.app.util.MediaUrl.of(song.audioUrl), song.id,
                        song.title, song.artistLabel(),
                        com.oru.app.util.MediaUrl.of(song.coverUrl)));
                if (i == wanted) {
                    at = items.size() - 1;
                }
                playable.add(song);
            } catch (RuntimeException unplayable) {
                // A song without audio cannot be queued; the rest still play.
            }
        }
        if (items.isEmpty()) {
            showError(new IllegalArgumentException(
                    "None of these songs have playable audio."));
            return;
        }
        // Keep the queue in step with what actually made it into the session.
        queue = playable;
        queueIndex = at;
        try {
            player.setMediaItems(items, at, 0L);
            player.prepare();
            player.play();
            currentSong = queue.get(at);
            populateUI(currentSong);
            startTicker();
        } catch (RuntimeException error) {
            showError(error);
        }
    }

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
        binding.shareButton.setOnClickListener(v -> shareSong());
        binding.addButton.setOnClickListener(v -> {
            if (currentSong != null) {
                com.oru.app.ui.components.PlaylistPicker.addTo(this, currentSong);
            }
        });
        binding.queueButton.setOnClickListener(v -> openQueue());
        binding.sleepTimerButton.setOnClickListener(v -> askSleepTimer());
        // The artist line in the player opens that artist, like the web one.
        binding.artistText.setOnClickListener(v -> {
            if (currentSong != null) {
                com.oru.app.ui.components.Actions.openArtist(this,
                        currentSong.artistId);
            }
        });

        binding.toolbar.inflateMenu(R.menu.player_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_playlist) {
                if (currentSong != null) {
                    com.oru.app.ui.components.PlaylistPicker.addTo(this,
                            currentSong);
                }
                return true;
            }
            if (item.getItemId() == R.id.action_download) {
                toggleDownload();
                return true;
            }
            if (item.getItemId() == R.id.action_report) {
                askReportReason();
                return true;
            }
            if (item.getItemId() == R.id.action_queue) {
                openQueue();
                return true;
            }
            if (item.getItemId() == R.id.action_sleep_timer) {
                askSleepTimer();
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

        connectPlayer();
        openPlaybackIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openPlaybackIntent(intent);
    }

    private void openPlaybackIntent(Intent intent) {
        List<Song> requested = null;
        String payload = intent.getStringExtra("queue");
        if (payload != null) {
            try {
                Song[] songs = LazySession.getGson().fromJson(payload, Song[].class);
                if (songs != null && songs.length > 0) {
                    requested = new ArrayList<>(Arrays.asList(songs));
                }
            } catch (RuntimeException ignore) {
                requested = null;
            }
        }
        if (requested != null) {
            queue = requested;
            int index = intent.getIntExtra("songIndex", -1);
            queueIndex = Math.max(0, Math.min(index, requested.size() - 1));
            if (player != null) {
                playQueue();
            }
            // Otherwise the controller is still connecting and will start it.
            return;
        }
        if (player != null) {
            resumeCurrentTrack();
        }
    }

    /** Next or previous track in the list the session is playing. */
    private void skip(int direction) {
        if (player == null) {
            return;
        }
        if (direction > 0) {
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem();
            } else {
                Toast.makeText(this, "That's the last song",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem();
        } else if (player.getCurrentPosition() > 3_000) {
            player.seekTo(0);
        } else {
            Toast.makeText(this, "That's the first song",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSongById(int songId) {
        loadSongById(songId, false);
    }

    /**
     * Reads a song's details.
     *
     * @param playingAlready the session is already playing it, so start nothing.
     */
    private void loadSongById(int songId, final boolean playingAlready) {
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
                    if (queue != null) {
                        for (int i = 0; i < queue.size(); i++) {
                            if (queue.get(i).id == song.id) {
                                queueIndex = i;
                                break;
                            }
                        }
                    }
                    populateUI(song);
                    if (player != null && !playingAlready) {
                        playNow(song);
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
                        if (player != null && !playingAlready) {
                            playNow(saved);
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
        }
        int count = player != null ? player.getMediaItemCount() : 0;
        if (count > 1) {
            int at = player.getCurrentMediaItemIndex();
            binding.queueText.setText("Track " + (at + 1) + " of " + count);
        } else {
            binding.queueText.setText("Now playing");
        }
        updatePlayPauseButtons();
        updateLikeButtons();
        updateModeButtons();
        refreshDownloadMenu();
    }

    /** Starts a song, unless the session is already on it. */
    private void playNow(Song song) {
        MediaItem current = player != null ? player.getCurrentMediaItem() : null;
        boolean same = current != null && current.mediaId != null
                && current.mediaId.equals(String.valueOf(song.id));
        if (same) {
            startTicker();
            return;
        }
        loadSong(song);
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
        // A song link, not an artist link: the page plays this song in the
        // browser and opens the app straight into it on Android.
        send.putExtra(Intent.EXTRA_TEXT, currentSong.title + " by "
                + currentSong.artistLabel() + " on ORU — "
                + "https://oru.freebuff.app/song/" + currentSong.id);
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
    }

    /**
     * What is queued, as a sheet you can jump around in.
     *
     * Tapping a row seeks the running session, so the song starts immediately
     * instead of starting the whole queue over.
     */
    private void openQueue() {
        if (player == null) {
            Toast.makeText(this, "The player is still starting",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        List<Song> items = queueForSheet();
        if (items.isEmpty()) {
            Toast.makeText(this, "Nothing is queued yet", Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        View sheet = getLayoutInflater().inflate(R.layout.sheet_queue, null);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(sheet);

        int at = player.getCurrentMediaItemIndex();
        int after = Math.max(0, items.size() - at - 1);
        TextView subtitle = sheet.findViewById(R.id.queueSubtitle);
        subtitle.setText(after == 0
                ? "This is the last song in the queue"
                : after + (after == 1 ? " song after this one" : " songs after this one"));

        QueueAdapter adapter = new QueueAdapter(index -> {
            player.seekTo(index, 0);
            player.play();
            dialog.dismiss();
        });
        androidx.recyclerview.widget.RecyclerView list =
                sheet.findViewById(R.id.queueRecyclerView);
        list.setLayoutManager(
                new androidx.recyclerview.widget.LinearLayoutManager(this));
        list.setAdapter(adapter);
        adapter.submit(items, at);
        dialog.show();
    }

    /**
     * The queue as song objects.
     *
     * With the app open this is the list that was tapped. Opened cold from the
     * notification there is no such list, so the session's own items are read
     * back, which still names and pictures every song.
     */
    private List<Song> queueForSheet() {
        if (queue != null && !queue.isEmpty()) {
            return queue;
        }
        List<Song> built = new ArrayList<>();
        if (player == null) {
            return built;
        }
        for (int i = 0; i < player.getMediaItemCount(); i++) {
            MediaItem item = player.getMediaItemAt(i);
            Song song = new Song();
            try {
                song.id = item.mediaId != null ? Integer.parseInt(item.mediaId) : 0;
            } catch (NumberFormatException ignored) {
                song.id = 0;
            }
            song.title = item.mediaMetadata != null
                    && item.mediaMetadata.title != null
                            ? item.mediaMetadata.title.toString() : "Unknown song";
            song.artistName = item.mediaMetadata != null
                    && item.mediaMetadata.artist != null
                            ? item.mediaMetadata.artist.toString() : "";
            song.coverUrl = item.mediaMetadata != null
                    && item.mediaMetadata.artworkUri != null
                            ? item.mediaMetadata.artworkUri.toString() : null;
            built.add(song);
        }
        return built;
    }

    /** Off, a few durations, or the end of the song that is playing. */
    private void askSleepTimer() {
        final String[] options = {"Off", "5 minutes", "15 minutes",
                "30 minutes", "45 minutes", "60 minutes",
                "End of this song"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Sleep timer")
                .setItems(options, (dialog, which) -> {
                    int minutes;
                    if (which == 0) {
                        minutes = 0;
                    } else if (which == options.length - 1) {
                        minutes = -1;
                    } else {
                        minutes = new int[] {0, 5, 15, 30, 45, 60}[which];
                    }
                    sendSleepTimer(minutes);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void sendSleepTimer(int minutes) {
        if (controller == null) {
            Toast.makeText(this, "The player is still starting",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Bundle args = new Bundle();
        args.putLong(PlaybackCommands.SLEEP_MINUTES, minutes);
        controller.sendCustomCommand(PlaybackCommands.SLEEP_TIMER, args);

        String label;
        String message;
        if (minutes == 0) {
            label = "Sleep";
            message = "Sleep timer off";
        } else if (minutes < 0) {
            label = "Song end";
            message = "Playback stops when this song ends";
        } else {
            label = minutes + "m";
            message = "Playback stops in " + minutes + " minutes";
        }
        binding.sleepTimerLabel.setText(label);
        binding.sleepTimerButton.setImageTintList(
                android.content.res.ColorStateList.valueOf(getColor(
                        minutes == 0 ? R.color.ink_soft : R.color.flame)));
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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
        player = null;
        controller = null;
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
    }
}
