package com.oru.app.ui.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;

import com.google.android.material.snackbar.Snackbar;
import com.oru.app.R;
import com.oru.app.data.model.ApiResponse;
import com.oru.app.data.model.Song;
import com.oru.app.databinding.ActivityPlayerBinding;
import com.oru.app.media.PlayerService;

import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerActivity extends AppCompatActivity {

    private ActivityPlayerBinding binding;
    private ExoPlayer player;
    private MediaSession mediaSession;
    private AtomicBoolean serviceBound = new AtomicBoolean(false);
    private Song currentSong;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlayerService.LocalBinder binder =
                    (PlayerService.LocalBinder) service;
            player = binder.getPlayer();
            mediaSession = binder.getMediaSession();
            serviceBound.set(true);
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

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.playButton.setOnClickListener(v -> {
            if (player != null) {
                if (player.isPlaying()) player.pause();
                else player.play();
                updatePlayPauseButtons();
            }
        });

        Intent intent = new Intent(this, PlayerService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        int songId = getIntent().getIntExtra("songId", -1);
        if (songId > 0) {
            loadSongById(songId);
        }
    }

    private void loadSongById(int songId) {
        binding.progressBar.setVisibility(View.VISIBLE);
        com.oru.app.data.api.ApiService api
                = com.oru.app.util.LazySession.getApiService();
        api.getSong(songId).enqueue(
                new retrofit2.Callback<ApiResponse<Song>>() {
                    @Override
                    public void onResponse(
                            retrofit2.Call<ApiResponse<Song>> call,
                            retrofit2.Response<ApiResponse<Song>> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().data != null) {
                            currentSong = response.body().data;
                            runOnUiThread(() -> {
                                binding.progressBar.setVisibility(View.GONE);
                                populateUI(currentSong);
                                if (serviceBound.get()
                                        && player != null) {
                                    loadSong(currentSong);
                                }
                            });
                        } else {
                            runOnUiThread(() -> {
                                binding.progressBar.setVisibility(View.GONE);
                                showError();
                            });
                        }
                    }

                    @Override
                    public void onFailure(
                            retrofit2.Call<ApiResponse<Song>> call,
                            Throwable t) {
                        runOnUiThread(() -> {
                            binding.progressBar.setVisibility(View.GONE);
                            showError();
                        });
                    }
                });
    }

    private void populateUI(Song song) {
        binding.titleText.setText(song.title);
        binding.artistText.setText(
                song.artist != null ? song.artist.stageName : "");
        binding.albumText.setText(
                song.artist != null ? song.artist.stageName : "");
        if (song.coverUrl != null) {
            com.bumptech.glide.Glide.with(this)
                    .load(song.coverUrl)
                    .placeholder(R.drawable.ic_library)
                    .into(binding.coverImage);
        }
        binding.miniArtwork.setImageResource(R.drawable.ic_library);
        updatePlayPauseButtons();
    }

    private void loadSong(Song song) {
        if (player == null) return;
        MediaItem mediaItem = com.oru.app.media.MediaItemBuilder.create(
                song.audioUrl, song.id, song.title,
                song.artist != null ? song.artist.stageName : null,
                song.coverUrl);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
        updatePlayPauseButtons();
        binding.miniPlayerBar.setVisibility(View.VISIBLE);
    }

    private void updatePlayPauseButtons() {
        if (player == null) return;
        boolean playing = player.isPlaying();
        binding.playButton.setImageResource(
                playing ? R.drawable.ic_pause : R.drawable.ic_play);
        binding.miniPlayPauseButton.setImageResource(
                playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void showError() {
        binding.errorView.getRoot().setVisibility(View.VISIBLE);
        binding.errorView.retryButton.setOnClickListener(v -> {
            binding.errorView.getRoot().setVisibility(View.GONE);
            int songId = getIntent().getIntExtra("songId", -1);
            if (songId > 0) loadSongById(songId);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
        serviceBound.set(false);
    }
}
