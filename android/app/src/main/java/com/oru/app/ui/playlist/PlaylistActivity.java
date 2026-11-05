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
        trackAdapter.setListener(this::askRemove);

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
                loadCover(com.oru.app.util.MediaUrl.of(p.coverUrl));
                populateTracks(p.songs != null ? p.songs
                        : new java.util.ArrayList<>());
            }

            @Override
            public void onFailure(Throwable error) {
                PlaylistActivity.this.onFailure(error);
            }
        });
    }

    private void populateHeader(Playlist p) {
        binding.collapsingToolbar.setTitle(p.title);
        binding.titleText.setText(p.title);
        binding.songCountText.setText(p.songCount() + " songs");
        binding.ownerText.setText(p.owner != null
                && p.owner.displayName != null
                        ? "by " + p.owner.displayName
                        : (p.isPublic ? "Public" : "Private"));
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
        binding.playerBar.setVisibility(songs.isEmpty() ? View.GONE : View.VISIBLE);
        if (!songs.isEmpty()) {
            Song first = songs.get(0);
            binding.playerTitle.setText(first.title);
            binding.playerArtist.setText(first.artistLabel());
            com.bumptech.glide.Glide.with(this)
                    .load(com.oru.app.util.MediaUrl.of(first.coverUrl))
                    .placeholder(R.drawable.ic_library)
                    .into(binding.playerArtwork);
        }
    }

    /** Play all: open the player on the first track. */
    private void togglePlay() {
        if (playlist == null || playlist.songs == null
                || playlist.songs.isEmpty()) {
            return;
        }
        com.oru.app.ui.player.Playback.start(this, playlist.songs, 0);
    }

    private void askRemove(Song song) {
        if (playlist == null) {
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Remove " + song.title + "?")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Remove", (dialog, which) ->
                        communityRepo.removeSongFromPlaylist(playlist.id,
                                song.id, new Callback<Void>() {
                                    @Override
                                    public void onSuccess(Void ignored) {
                                        loadPlaylist();
                                    }

                                    @Override
                                    public void onFailure(Throwable error) {
                                        binding.errorView.getRoot()
                                                .setVisibility(View.VISIBLE);
                                        binding.errorView.errorMessage
                                                .setText(describe(error));
                                    }
                                }))
                .show();
    }

    private void onFailure(Throwable error) {
        binding.progressBar.setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.VISIBLE);
        binding.errorView.errorMessage.setText(describe(error));
    }

    private String describe(Throwable error) {
        return com.oru.app.ui.components.UiErrors.message(this, error);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
