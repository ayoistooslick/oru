package com.oru.app.ui.playlist;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.Playlist;
import com.oru.app.data.model.Song;
import com.oru.app.ui.release.TrackAdapter;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.CommunityRepository;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.databinding.ActivityPlaylistBinding;
import com.oru.app.media.PlayerService;
import com.oru.app.util.LazySession;

import java.util.List;

public class PlaylistActivity extends AppCompatActivity {

    private ActivityPlaylistBinding binding;
    private TrackAdapter trackAdapter;
    private MusicRepository musicRepo;
    private CommunityRepository communityRepo;
    private Playlist playlist;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlaylistBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        trackAdapter = new TrackAdapter();

        binding.tracksRecyclerView.setLayoutManager(
                new LinearLayoutManager(this));
        binding.tracksRecyclerView.setAdapter(trackAdapter);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.playerPlayButton.setOnClickListener(v -> togglePlay());

        binding.errorView.retryButton.setOnClickListener(v -> {
            binding.errorView.getRoot().setVisibility(View.GONE);
            loadPlaylist();
        });

        if (musicRepo == null) {
            musicRepo = new MusicRepository(
                    LazySession.getApiService(),
                    LazySession.getGson());
            communityRepo = new CommunityRepository(
                    LazySession.getApiService(),
                    LazySession.getGson());
        }

        loadPlaylist();
    }

    private void loadPlaylist() {
        int playlistId;
        try {
            playlistId = getIntent().getIntExtra("playlistId", -1);
        } catch (Exception e) {
            playlistId = -1;
        }
        if (playlistId <= 0) {
            finish();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.errorView.getRoot().setVisibility(View.GONE);

        // First load the playlist metadata.
        communityRepo.getPlaylist(playlistId, new Callback<Playlist>() {
            @Override
            public void onSuccess(Playlist p) {
                playlist = p;
                populateHeader(p);
                loadCover(p.coverUrl);
                // Then load tracks if the playlist includes songs.
                if (p.songs != null && !p.songs.isEmpty()) {
                    populateTracks(p.songs);
                } else {
                    // Load songs separately if not embedded.
                    // songs embedded in playlist response
                }
            }

            @Override
            public void onFailure(Throwable error) {
                onFailure(error);
            }
        });
    }

    private void populateHeader(Playlist p) {
        binding.collapsingToolbar.setTitle(p.title);
        binding.titleText.setText(p.title);
        binding.songCountText.setText(p.songCount + " songs");
        binding.ownerText.setText(p.owner != null
                ? "by " + p.owner.displayName
                : "by " + p.ownerId);
    }

    private void loadCover(String coverUrl) {
        if (coverUrl == null) {
            binding.coverImage.setImageResource(R.drawable.ic_library);
            return;
        }
        com.bumptech.glide.Glide.with(this)
                .load(coverUrl)
                .placeholder(R.drawable.ic_library)
                .error(R.drawable.ic_library)
                .into(binding.coverImage);
    }

    private void populateTracks(List<Song> songs) {
        trackAdapter.submitList(songs);
        binding.tracksRecyclerView.setVisibility(View.VISIBLE);
        binding.progressBar.setVisibility(View.GONE);
    }

    private void togglePlay() {
        if (playlist == null || playlist.songs == null
                || playlist.songs.isEmpty()) {
            return;
        }
        Song first = playlist.songs.get(0);
        android.content.Intent intent = new android.content.Intent(this,
                PlayerService.class);
        intent.setAction("com.oru.app.PLAY");
        startService(intent);
        binding.playerBar.setVisibility(View.VISIBLE);
        binding.playerTitle.setText(first.title);
        binding.playerArtist.setText(
                first.artist != null ? first.artist.stageName : "");
        com.bumptech.glide.Glide.with(this)
                .load(first.coverUrl != null ? first.coverUrl : null)
                .placeholder(R.drawable.ic_library)
                .into(binding.playerArtwork);
        binding.playerPlayButton.setImageResource(com.oru.app.R.drawable.ic_play);
    }

    private void onFailure(Throwable error) {
        binding.progressBar.setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.VISIBLE);
        binding.errorView.errorMessage.setText(describe(error));
    }

    private String describe(Throwable error) {
        if (error instanceof java.io.IOException) {
            return getString(R.string.error_network);
        }
        return getString(R.string.error_unknown);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
