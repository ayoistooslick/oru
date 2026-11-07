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
    private com.oru.app.ui.components.MiniPlayer miniPlayer;

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
        miniPlayer = new com.oru.app.ui.components.MiniPlayer(this,
                binding.miniPlayer);

        binding.toolbar.inflateMenu(R.menu.playlist_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (playlist == null) {
                return false;
            }
            int id = item.getItemId();
            if (id == R.id.action_edit) {
                askEdit();
                return true;
            }
            if (id == R.id.action_add_songs) {
                askAddSongs();
                return true;
            }
            if (id == R.id.action_delete) {
                askDelete();
                return true;
            }
            return false;
        });

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
        // The API sends a media path, not a full address; Covers knows that.
        com.oru.app.ui.components.Covers.load(binding.coverImage, coverUrl,
                R.drawable.ic_library);
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
            binding.playerArtist.setOnClickListener(v ->
                    com.oru.app.ui.components.Actions.openArtist(this,
                            first.artistId));
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

    /** Rename it, and decide who may listen — the web's edit form. */
    private void askEdit() {
        final android.widget.EditText title = new android.widget.EditText(this);
        title.setText(playlist.title);
        title.setHint("Playlist name");
        title.setSelectAllOnFocus(true);

        final android.widget.CheckBox isPublic = new android.widget.CheckBox(this);
        isPublic.setText("Anyone with the link can listen");
        isPublic.setChecked(playlist.isPublic);

        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(title);
        box.addView(isPublic);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Edit playlist")
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = title.getText().toString().trim();
                    if (name.isEmpty()) {
                        android.widget.Toast.makeText(this,
                                "Give the playlist a name",
                                android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    communityRepo.updatePlaylist(playlist.id, name,
                            isPublic.isChecked(), new Callback<Playlist>() {
                                @Override
                                public void onSuccess(Playlist updated) {
                                    loadPlaylist();
                                }

                                @Override
                                public void onFailure(Throwable error) {
                                    android.widget.Toast.makeText(
                                            PlaylistActivity.this,
                                            describe(error),
                                            android.widget.Toast.LENGTH_LONG)
                                            .show();
                                }
                            });
                })
                .show();
    }

    /** Search the catalogue and add what the listener taps. */
    private void askAddSongs() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Song or artist");
        input.setSingleLine(true);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Add songs")
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Search", (dialog, which) -> {
                    String query = input.getText().toString().trim();
                    if (query.isEmpty()) {
                        return;
                    }
                    searchToAdd(query);
                })
                .show();
    }

    private void searchToAdd(String query) {
        binding.progressBar.setVisibility(View.VISIBLE);
        musicRepo.listSongs(query, 20, 0, new Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> songs) {
                binding.progressBar.setVisibility(View.GONE);
                if (songs == null || songs.isEmpty()) {
                    android.widget.Toast.makeText(PlaylistActivity.this,
                            "Nothing matched \"" + query + "\"",
                            android.widget.Toast.LENGTH_LONG).show();
                    return;
                }
                String[] labels = new String[songs.size()];
                for (int i = 0; i < songs.size(); i++) {
                    labels[i] = songs.get(i).title + " — "
                            + songs.get(i).artistLabel();
                }
                new androidx.appcompat.app.AlertDialog.Builder(PlaylistActivity.this)
                        .setTitle("Add to " + playlist.title)
                        .setItems(labels, (dialog, which) ->
                                addSong(songs.get(which)))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }

            @Override
            public void onFailure(Throwable error) {
                binding.progressBar.setVisibility(View.GONE);
                android.widget.Toast.makeText(PlaylistActivity.this,
                        describe(error), android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addSong(Song song) {
        communityRepo.addSongToPlaylist(playlist.id, song.id,
                new Callback<com.oru.app.data.model.PlaylistSong>() {
                    @Override
                    public void onSuccess(com.oru.app.data.model.PlaylistSong added) {
                        android.widget.Toast.makeText(PlaylistActivity.this,
                                song.title + " added",
                                android.widget.Toast.LENGTH_SHORT).show();
                        loadPlaylist();
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        android.widget.Toast.makeText(PlaylistActivity.this,
                                describe(error),
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void askDelete() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete " + playlist.title + "?")
                .setMessage("The playlist goes away; the songs stay on ORU.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Delete", (dialog, which) ->
                        communityRepo.deletePlaylist(playlist.id,
                                new Callback<Void>() {
                                    @Override
                                    public void onSuccess(Void ignored) {
                                        android.widget.Toast.makeText(
                                                PlaylistActivity.this,
                                                "Playlist deleted",
                                                android.widget.Toast.LENGTH_SHORT)
                                                .show();
                                        finish();
                                    }

                                    @Override
                                    public void onFailure(Throwable error) {
                                        android.widget.Toast.makeText(
                                                PlaylistActivity.this,
                                                describe(error),
                                                android.widget.Toast.LENGTH_LONG)
                                                .show();
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
        if (miniPlayer != null) {
            miniPlayer.release();
            miniPlayer = null;
        }
        binding = null;
    }
}
