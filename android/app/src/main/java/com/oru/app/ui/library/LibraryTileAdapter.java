package com.oru.app.ui.library;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.Artist;
import com.oru.app.data.model.Playlist;
import com.oru.app.databinding.ItemLibraryTileBinding;
import com.oru.app.ui.components.Covers;

import java.util.ArrayList;
import java.util.List;

/**
 * The library grid, in Spotify's shape: square cards, two to a row, with the
 * Liked Songs tile pinned first and playlists and artists after it.
 */
public class LibraryTileAdapter extends RecyclerView.Adapter<LibraryTileAdapter.VH> {

    public interface Listener {
        void onLikedSongs();

        void onPlaylist(Playlist playlist);

        void onPlaylistLongPress(Playlist playlist);

        void onArtist(Artist artist);
    }

    private static final int LIKED = 0;
    private static final int PLAYLIST = 1;
    private static final int ARTIST = 2;

    private static final class Tile {
        final int type;
        final Playlist playlist;
        final Artist artist;
        final String countLabel;

        Tile(int type, Playlist playlist, Artist artist, String countLabel) {
            this.type = type;
            this.playlist = playlist;
            this.artist = artist;
            this.countLabel = countLabel;
        }
    }

    private final List<Tile> tiles = new ArrayList<>();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Liked songs first, then playlists, then the artists you follow. */
    public void setContent(int likedCount, List<Playlist> playlists,
            List<Artist> artists) {
        tiles.clear();
        tiles.add(new Tile(LIKED, null, null, songs(likedCount)));
        if (playlists != null) {
            for (Playlist playlist : playlists) {
                tiles.add(new Tile(PLAYLIST, playlist, null,
                        "Playlist · " + songs(playlist.songCount())));
            }
        }
        if (artists != null) {
            for (Artist artist : artists) {
                tiles.add(new Tile(ARTIST, null, artist, "Artist"));
            }
        }
        notifyDataSetChanged();
    }

    private static String songs(int count) {
        return count + (count == 1 ? " song" : " songs");
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemLibraryTileBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(tiles.get(position));
    }

    @Override
    public int getItemCount() {
        return tiles.size();
    }

    final class VH extends RecyclerView.ViewHolder {
        private final ItemLibraryTileBinding binding;

        VH(ItemLibraryTileBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Tile tile) {
            if (tile.type == LIKED) {
                binding.tileArtwork.setImageDrawable(null);
                binding.tileArtwork.setBackgroundResource(R.drawable.bg_liked_tile);
                binding.tileOverlay.setVisibility(android.view.View.VISIBLE);
                binding.tileTitle.setText(R.string.liked_songs);
                binding.tileSubtitle.setText(tile.countLabel);
                binding.getRoot().setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onLikedSongs();
                    }
                });
                binding.getRoot().setOnLongClickListener(null);
                return;
            }

            binding.tileArtwork.setBackgroundResource(R.drawable.artwork_rounded);
            binding.tileOverlay.setVisibility(android.view.View.GONE);

            if (tile.type == PLAYLIST) {
                Playlist playlist = tile.playlist;
                // Versioned: a cover the owner replaced must not stay on screen.
                Covers.loadVersioned(binding.tileArtwork, playlist.coverUrl,
                        playlist.imageVersion, R.drawable.ic_playlist);
                binding.tileTitle.setText(playlist.title);
                binding.tileSubtitle.setText(tile.countLabel);
                binding.getRoot().setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onPlaylist(playlist);
                    }
                });
                binding.getRoot().setOnLongClickListener(v -> {
                    if (listener != null) {
                        listener.onPlaylistLongPress(playlist);
                    }
                    return true;
                });
                return;
            }

            Artist artist = tile.artist;
            Covers.loadAvatar(binding.tileArtwork, artist.avatarUrl);
            binding.tileTitle.setText(artist.stageName != null
                    && !artist.stageName.isEmpty() ? artist.stageName : artist.handle);
            binding.tileSubtitle.setText(tile.countLabel);
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onArtist(artist);
                }
            });
            binding.getRoot().setOnLongClickListener(null);
        }
    }
}
