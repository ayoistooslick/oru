package com.oru.app.ui.shares;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.data.model.Song;
import com.oru.app.databinding.ItemShareBinding;
import com.oru.app.ui.player.PlayerActivity;

import java.util.Objects;

/** Songs you shared, with the review status their artist gave them. */
public class SharesAdapter extends ListAdapter<Song, SharesAdapter.VH> {

    public SharesAdapter() {
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
                    return Objects.equals(oldItem.status, newItem.status)
                            && Objects.equals(oldItem.title, newItem.title);
                }
            };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemShareBinding b = ItemShareBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position), getCurrentList(), position);
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemShareBinding binding;

        VH(ItemShareBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Song song, java.util.List<Song> queue, int index) {
            com.oru.app.ui.components.Covers.loadSongCover(binding.artworkImage,
                    song.id, song.coverUrl);
            binding.titleText.setText(song.title);
            binding.artistText.setText(song.artistLabel());
            binding.statusText.setText(
                    song.status != null ? song.status : "");
            binding.getRoot().setOnClickListener(v ->
                    com.oru.app.ui.player.Playback.start(
                            binding.getRoot().getContext(), queue, index));
        }
    }
}
