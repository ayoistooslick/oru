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
import androidx.recyclerview.widget.GridLayoutManager;

import com.oru.app.R;
import com.oru.app.data.model.Artist;
import com.oru.app.data.model.Playlist;
import com.oru.app.data.model.Song;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.CommunityRepository;
import com.oru.app.databinding.FragmentLibraryBinding;
import com.oru.app.ui.components.PlaylistPicker;
import com.oru.app.ui.components.UiErrors;
import com.oru.app.ui.playlist.PlaylistActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * The library, built the way Spotify builds one: square cards two to a row,
 * Liked Songs pinned first, your playlists next, then the artists you follow.
 * The + in the header makes a playlist without leaving the page.
 */
public class LibraryFragment extends Fragment {

    /** What the chips narrow the grid down to. */
    private enum Filter {
        ALL, PLAYLISTS, ARTISTS
    }

    private FragmentLibraryBinding binding;
    private LibraryTileAdapter adapter;
    private CommunityRepository communityRepo;
    private Filter filter = Filter.ALL;
    private boolean loaded;

    private List<Song> likes = new ArrayList<>();
    private List<Playlist> playlists = new ArrayList<>();
    private List<Artist> following = new ArrayList<>();

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

        adapter = new LibraryTileAdapter();
        adapter.setListener(new LibraryTileAdapter.Listener() {
            @Override
            public void onLikedSongs() {
                startActivity(new Intent(requireContext(),
                        LikedSongsActivity.class));
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
            public void onArtist(Artist artist) {
                Intent intent = new Intent(requireContext(),
                        com.oru.app.ui.artist.ArtistActivity.class);
                intent.putExtra("artistId", artist.artistId());
                startActivity(intent);
            }
        });

        binding.recyclerView.setLayoutManager(new GridLayoutManager(
                requireContext(), 2));
        binding.recyclerView.setAdapter(adapter);

        binding.toolbar.inflateMenu(R.menu.library_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_new_playlist) {
                createPlaylist();
                return true;
            }
            if (id == R.id.action_downloads) {
                startActivity(new Intent(requireContext(),
                        com.oru.app.ui.downloads.DownloadsActivity.class));
                return true;
            }
            return false;
        });

        binding.filterChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            int checked = checkedIds.get(0);
            if (checked == R.id.chipPlaylists) {
                filter = Filter.PLAYLISTS;
            } else if (checked == R.id.chipArtists) {
                filter = Filter.ARTISTS;
            } else {
                filter = Filter.ALL;
            }
            render();
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
        binding.createPlaylistEmptyButton.setOnClickListener(v -> createPlaylist());

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
            public void onSuccess(List<Song> items) {
                likes = items != null ? items : new ArrayList<>();
                loadPlaylists();
            }

            @Override
            public void onFailure(Throwable error) {
                likes = new ArrayList<>();
                loadPlaylists();
            }
        });
    }

    private void loadPlaylists() {
        communityRepo.listPlaylists(new Callback<List<Playlist>>() {
            @Override
            public void onSuccess(List<Playlist> items) {
                playlists = items != null ? items : new ArrayList<>();
                loadFollowing();
            }

            @Override
            public void onFailure(Throwable error) {
                playlists = new ArrayList<>();
                loadFollowing();
            }
        });
    }

    private void loadFollowing() {
        communityRepo.getFollowing(new Callback<List<Artist>>() {
            @Override
            public void onSuccess(List<Artist> items) {
                following = items != null ? items : new ArrayList<>();
                show();
            }

            @Override
            public void onFailure(Throwable error) {
                following = new ArrayList<>();
                show();
            }
        });
    }

    /** Paints the grid for the current chip. */
    private void render() {
        if (binding == null) {
            return;
        }
        boolean nothingForFilter = (filter == Filter.PLAYLISTS && playlists.isEmpty())
                || (filter == Filter.ARTISTS && following.isEmpty());
        if (nothingForFilter) {
            adapter.setContent(0, new ArrayList<>(), new ArrayList<>());
            if (filter == Filter.PLAYLISTS) {
                binding.emptyTitle.setText("No playlists yet");
                binding.emptyText.setText("Tap the heart on a song, or start a playlist of your own.");
            } else {
                binding.emptyTitle.setText("You follow no artists yet");
                binding.emptyText.setText("Follow an artist from their page and they show up here.");
            }
            binding.libraryEmpty.setVisibility(View.VISIBLE);
            binding.recyclerView.setVisibility(View.GONE);
            return;
        }

        if (likes.isEmpty() && playlists.isEmpty() && following.isEmpty()) {
            adapter.setContent(0, new ArrayList<>(), new ArrayList<>());
            binding.emptyTitle.setText("Your library is empty");
            binding.emptyText.setText(
                    "Tap the heart on any song to keep it here, or start a playlist of your own.");
            binding.libraryEmpty.setVisibility(View.VISIBLE);
            binding.recyclerView.setVisibility(View.GONE);
            return;
        }

        // Liked Songs stays pinned the way Spotify pins it; the chips only narrow
        // what comes after it. A playlist filter still counts Liked Songs.
        boolean showLiked = filter != Filter.ARTISTS;
        List<Playlist> shownPlaylists = filter == Filter.ARTISTS
                ? new ArrayList<>()
                : playlists;
        List<Artist> shownArtists = filter == Filter.PLAYLISTS
                ? new ArrayList<>()
                : following;
        adapter.setContent(showLiked ? likes.size() : 0, shownPlaylists,
                shownArtists);
        binding.libraryEmpty.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.VISIBLE);
    }

    private void show() {
        if (binding == null) {
            return;
        }
        binding.progressBar.setVisibility(View.GONE);
        loaded = true;
        render();
    }

    private void createPlaylist() {
        PlaylistPicker.create(requireContext(), playlist -> {
            if (binding != null && isAdded()) {
                Toast.makeText(requireContext(),
                        playlist.title + " created", Toast.LENGTH_SHORT).show();
            }
            loadLibrary();
        });
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
