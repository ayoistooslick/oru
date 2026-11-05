package com.oru.app.ui.artist;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.Song;
import com.oru.app.databinding.ItemArtistSongBinding;

public class SongAdapter extends ListAdapter<Song, SongAdapter.VH> {

    public SongAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<Song> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Song>() {
                @Override
                public boolean areItemsTheSame(@NonNull Song oldItem,
                        @NonNull Song newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Song oldItem,
                        @NonNull Song newItem) {
                    return oldItem.title.equals(newItem.title)
                            && java.util.Objects.equals(oldItem.status,
                                    newItem.status);
                }
            };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemArtistSongBinding b = ItemArtistSongBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemArtistSongBinding binding;

        VH(ItemArtistSongBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Song song) {
            com.oru.app.ui.components.Covers.loadSongCover(binding.artworkImage,
                    song.id, song.coverUrl);
            binding.titleText.setText(song.title);
            binding.durationText.setText(song.durationLabel());
            binding.likesText.setText(song.isPublished()
                    ? song.likesCount + " likes"
                    : "Draft");
            binding.getRoot().setOnClickListener(v -> {
                android.content.Intent i = new android.content.Intent(
                        binding.getRoot().getContext(),
                        com.oru.app.ui.player.PlayerActivity.class);
                i.putExtra("songId", song.id);
                binding.getRoot().getContext().startActivity(i);
            });
        }

        private String formatDuration(int ms) {
            int sec = ms / 1000;
            int m = sec / 60;
            int s = sec % 60;
            return String.format("%d:%02d", m, s);
        }
    }
}
