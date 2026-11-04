package com.oru.app.ui.components;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.Song;
import com.oru.app.databinding.ItemFeedBinding;
import com.oru.app.ui.player.PlayerActivity;

import java.util.Objects;

/** The following feed: songs from the artists you follow, newest first. */
public class FeedAdapter extends ListAdapter<Song, FeedAdapter.VH> {

    public FeedAdapter() {
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
                    return Objects.equals(oldItem.title, newItem.title)
                            && Objects.equals(oldItem.artistName,
                                    newItem.artistName)
                            && Objects.equals(oldItem.status, newItem.status);
                }
            };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFeedBinding b = ItemFeedBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemFeedBinding binding;

        VH(ItemFeedBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Song song) {
            binding.typeIcon.setImageResource(R.drawable.ic_music);
            binding.typeLabel.setText("Song");
            binding.titleText.setText(song.title);
            binding.subtitleText.setText(song.artistLabel());
            String length = song.durationLabel();
            String genres = song.genreLabel();
            binding.metaText.setText(length.isEmpty() ? genres
                    : genres.isEmpty() ? length : length + " · " + genres);
            binding.playButton.setVisibility(View.VISIBLE);
            binding.getRoot().setOnClickListener(v -> {
                Intent i = new Intent(binding.getRoot().getContext(),
                        PlayerActivity.class);
                i.putExtra("songId", song.id);
                binding.getRoot().getContext().startActivity(i);
            });
        }
    }
}
