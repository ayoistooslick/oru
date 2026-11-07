package com.oru.app.ui.library;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.oru.app.R;
import com.oru.app.data.model.Playlist;
import com.oru.app.data.offline.Downloads;
import com.oru.app.data.model.Song;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.CommunityRepository;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.databinding.FragmentLibraryBinding;
import com.oru.app.ui.components.PlaylistPicker;
import com.oru.app.ui.components.UiErrors;
import com.oru.app.ui.playlist.PlaylistActivity;

import java.util.ArrayList;
import java.util.List;

/** Library tab: liked songs, your uploads and your playlists. */
public class LibraryFragment extends Fragment {

    private FragmentLibraryBinding binding;
    private LibraryAdapter adapter;
    private MusicRepository musicRepo;
    private CommunityRepository communityRepo;
    private boolean loaded;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new LibraryAdapter();
        adapter.setListener(new LibraryAdapter.Listener() {
            @Override
            public void onSong(Song song) {
                // The row already opens the player.
            }

            @Override
            public void onSongLongPress(Song song) {
                songActions(song);
            }

            @Override
            public void onPlaylist(Playlist playlist) {
                open(playlist);
            }

            @Override
            public void onPlaylistLongPress(Playlist playlist) {
                playlistActions(playlist);
            }

            @Override
            public void onArtist(com.oru.app.data.model.Artist artist) {
                Intent intent = new Intent(requireContext(),
                        com.oru.app.ui.artist.ArtistActivity.class);
                intent.putExtra("artistId", artist.artistId());
                startActivity(intent);
            }
        });

        binding.recyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        binding.toolbar.inflateMenu(R.menu.library_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_downloads) {
                startActivity(new Intent(requireContext(),
                        com.oru.app.ui.downloads.DownloadsActivity.class));
                return true;
            }
            return false;
        });

        binding.errorView.retryButton.setOnClickListener(v -> {
            binding.errorView.getRoot().setVisibility(View.GONE);
            loadLibrary();
        });

        binding.goHomeButton.setOnClickListener(v -> {
            // The web's empty library sends you home; so does this one.
            androidx.navigation.fragment.NavHostFragment.findNavController(this)
                    .navigate(R.id.homeFragment);
        });
        binding.newPlaylistButton.setOnClickListener(v -> PlaylistPicker.create(
                requireContext(), playlist -> {
                    Toast.makeText(requireContext(), "Playlist created",
                            Toast.LENGTH_SHORT).show();
                    loadLibrary();
                }));

        musicRepo = new MusicRepository(com.oru.app.util.LazySession.getApiService(),
                com.oru.app.util.LazySession.getGson());
        communityRepo = new CommunityRepository(
                com.oru.app.util.LazySession.getApiService(),
                com.oru.app.util.LazySession.getGson());

        loadLibrary();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Coming back from a playlist or the player: show what changed.
        if (loaded && binding != null) {
            loadLibrary();
        }
    }

    private void loadLibrary() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);
        binding.libraryEmpty.setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.GONE);

        communityRepo.getMyLikes(new Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> likes) {
                loadPlaylists(likes);
            }

            @Override
            public void onFailure(Throwable error) {
                loadPlaylists(null);
            }
        });
    }

    private void loadPlaylists(List<Song> likes) {
        communityRepo.listPlaylists(new Callback<List<Playlist>>() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                loadUploads(likes, playlists);
            }

            @Override
            public void onFailure(Throwable error) {
                loadUploads(likes, null);
            }
        });
    }

    private void loadUploads(List<Song> likes, List<Playlist> playlists) {
        musicRepo.getMySongs(new Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> uploads) {
                loadFollowing(likes, playlists, uploads);
            }

            @Override
            public void onFailure(Throwable error) {
                // Listeners have no artist profile; that is not a failure here.
                loadFollowing(likes, playlists, null);
            }
        });
    }

    private void loadFollowing(List<Song> likes, List<Playlist> playlists,
            List<Song> uploads) {
        communityRepo.getFollowing(new Callback<List<com.oru.app.data.model.Artist>>() {
            @Override
            public void onSuccess(List<com.oru.app.data.model.Artist> following) {
                show(likes, playlists, uploads, following);
            }

            @Override
            public void onFailure(Throwable error) {
                show(likes, playlists, uploads, null);
            }
        });
    }

    /** Long press on a song: the same actions every web row offers. */
    private void songActions(Song song) {
        boolean saved = Downloads.isDownloaded(requireContext(), song.id);
        String[] options = {
                "Add to playlist",
                saved ? "Remove download" : "Save for offline",
                "Share",
                "Report",
                "Go to artist"
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(song.title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        PlaylistPicker.addTo(requireContext(), song);
                    } else if (which == 2) {
                        com.oru.app.ui.components.Actions.share(requireContext(),
                                song.title + " by " + song.artistLabel(),
                                "https://oru.freebuff.app/artist/" + song.artistId);
                    } else if (which == 3) {
                        com.oru.app.ui.components.Actions.report(requireContext(),
                                "song", song.id, song.title);
                    } else if (which == 4) {
                        com.oru.app.ui.components.Actions.openArtist(
                                requireContext(), song.artistId);
                    } else if (saved) {
                        Downloads.remove(requireContext(), song.id);
                        Toast.makeText(requireContext(), "Download removed",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Saving for offline…",
                                Toast.LENGTH_SHORT).show();
                        Downloads.download(requireContext(), song, percent -> {
                        }, (ok, message) -> {
                            if (isAdded()) {
                                Toast.makeText(requireContext(), message,
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .show();
    }

    private void show(List<Song> likes, List<Playlist> playlists,
            List<Song> uploads, List<com.oru.app.data.model.Artist> following) {
        if (binding == null) {
            return;
        }
        binding.progressBar.setVisibility(View.GONE);
        loaded = true;

        List<LibraryAdapter.Row> rows = new ArrayList<>();
        if (likes != null && !likes.isEmpty()) {
            rows.add(LibraryAdapter.Row.header("Liked songs",
                    likes.size() + " songs"));
            for (Song song : likes) {
                rows.add(LibraryAdapter.Row.song(song));
            }
        }
        if (uploads != null && !uploads.isEmpty()) {
            rows.add(LibraryAdapter.Row.header("Your uploads",
                    uploads.size() + " songs"));
            for (Song song : uploads) {
                rows.add(LibraryAdapter.Row.song(song));
            }
        }
        if (playlists != null && !playlists.isEmpty()) {
            rows.add(LibraryAdapter.Row.header("Your playlists",
                    playlists.size() + " playlists"));
            for (Playlist playlist : playlists) {
                rows.add(LibraryAdapter.Row.playlist(playlist));
            }
        }
        if (following != null && !following.isEmpty()) {
            rows.add(LibraryAdapter.Row.header("Following",
                    following.size() + " artists"));
            for (com.oru.app.data.model.Artist artist : following) {
                rows.add(LibraryAdapter.Row.artist(artist));
            }
        }

        if (rows.isEmpty()) {
            binding.libraryEmpty.setVisibility(View.VISIBLE);
            binding.recyclerView.setVisibility(View.GONE);
            return;
        }
        adapter.setRows(rows);
        binding.recyclerView.setVisibility(View.VISIBLE);
    }

    private void open(Playlist playlist) {
        Intent intent = new Intent(requireContext(), PlaylistActivity.class);
        intent.putExtra("playlistId", playlist.id);
        startActivity(intent);
    }

    private void playlistActions(Playlist playlist) {
        new AlertDialog.Builder(requireContext())
                .setTitle(playlist.title)
                .setItems(new String[]{"Rename", "Delete"}, (dialog, which) -> {
                    if (which == 0) {
                        rename(playlist);
                    } else {
                        confirmDelete(playlist);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void rename(Playlist playlist) {
        final EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setText(playlist.title);
        new AlertDialog.Builder(requireContext())
                .setTitle("Rename playlist")
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        return;
                    }
                    communityRepo.updatePlaylist(playlist.id, name, null,
                            new Callback<Playlist>() {
                                @Override
                                public void onSuccess(Playlist updated) {
                                    loadLibrary();
                                }

                                @Override
                                public void onFailure(Throwable error) {
                                    toast(UiErrors.message(requireContext(), error));
                                }
                            });
                })
                .show();
    }

    private void confirmDelete(Playlist playlist) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete " + playlist.title + "?")
                .setMessage("The songs stay in your library.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Delete", (dialog, which) ->
                        communityRepo.deletePlaylist(playlist.id,
                                new Callback<Void>() {
                                    @Override
                                    public void onSuccess(Void ignored) {
                                        loadLibrary();
                                    }

                                    @Override
                                    public void onFailure(Throwable error) {
                                        toast(UiErrors.message(requireContext(),
                                                error));
                                    }
                                }))
                .show();
    }

    private void toast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
