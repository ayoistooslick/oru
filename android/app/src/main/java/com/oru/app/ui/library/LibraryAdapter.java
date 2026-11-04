package com.oru.app.ui.library;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.Playlist;
import com.oru.app.data.model.Song;
import com.oru.app.databinding.ItemLibrarySectionBinding;
import com.oru.app.databinding.ItemLibrarySongBinding;

public class LibraryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private java.util.List<LibraryItem> items = java.util.Collections.emptyList();

    public void setItems(java.util.List<LibraryItem> items) {
        this.items = items != null ? items : java.util.Collections.emptyList();
        notifyDataSetChanged();
    }

    private static final int SECTION_HEADER = 0;
    private static final int SONG_LIST = 1;
    private static final int PLAYLIST_LIST = 2;

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == SECTION_HEADER) {
            ItemLibrarySectionBinding b = ItemLibrarySectionBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new SectionVH(b);
        } else if (viewType == SONG_LIST) {
            ItemLibrarySongBinding b = ItemLibrarySongBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new SongVH(b);
        } else {
            return new PlaylistVH(
                    (com.google.android.material.card.MaterialCardView)
                            LayoutInflater.from(parent.getContext())
                                    .inflate(R.layout.item_playlist_card, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        LibraryItem item = items.get(position);
        if (holder instanceof SectionVH) {
            ((SectionVH) holder).bind(item);
        } else if (holder instanceof SongVH) {
            // Show first song of the item
            if (item.songs != null && !item.songs.isEmpty()) {
                ((SongVH) holder).bind(item.songs.get(0));
            }
        } else if (holder instanceof PlaylistVH) {
            // Playlist items shown via the playlist card; handled by PlaylistFragment activity
        }
    }

    @Override
    public int getItemViewType(int position) {
        LibraryItem item = items.get(position);
        if (item.type == LibraryItem.Type.LIKES || item.type == LibraryItem.Type.MY_SONGS) {
            return SONG_LIST;
        } else if (item.type == LibraryItem.Type.PLAYLISTS) {
            return PLAYLIST_LIST;
        }
        return SECTION_HEADER;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SectionVH extends RecyclerView.ViewHolder {
        private final ItemLibrarySectionBinding binding;

        SectionVH(ItemLibrarySectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(LibraryItem item) {
            binding.titleText.setText(item.label);
            binding.countText.setText(
                    item.type == LibraryItem.Type.LIKES
                            ? (item.songs != null ? item.songs.size() + " songs" : "0")
                            : item.type == LibraryItem.Type.PLAYLISTS
                                    ? (item.playlists != null ? item.playlists.size() + " playlists" : "0")
                                    : "");
        }
    }

    static class SongVH extends RecyclerView.ViewHolder {
        private final ItemLibrarySongBinding binding;

        SongVH(ItemLibrarySongBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Song song) {
            binding.titleText.setText(song.title);
            binding.artistText.setText(song.artistLabel());
            binding.likesText.setText(song.genreLabel());
            binding.playButton.setOnClickListener(v -> {
                android.content.Intent i = new android.content.Intent(
                        binding.getRoot().getContext(),
                        com.oru.app.ui.player.PlayerActivity.class);
                i.putExtra("songId", song.id);
                binding.getRoot().getContext().startActivity(i);
            });
            binding.getRoot().setOnClickListener(v -> {
                android.content.Intent i = new android.content.Intent(
                        binding.getRoot().getContext(),
                        com.oru.app.ui.player.PlayerActivity.class);
                i.putExtra("songId", song.id);
                binding.getRoot().getContext().startActivity(i);
            });
        }
    }

    static class PlaylistVH extends RecyclerView.ViewHolder {
        private final com.google.android.material.card.MaterialCardView card;

        PlaylistVH(com.google.android.material.card.MaterialCardView card) {
            super(card);
            this.card = card;
        }
    }
}
