package com.oru.app.ui.release;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.Song;
import com.oru.app.databinding.ItemTrackBinding;

public class TrackAdapter extends ListAdapter<Song, TrackAdapter.VH> {

    /** Long-pressing a track in a playlist offers to take it out. */
    public interface Listener {
        void onLongPress(Song song);
    }

    private Listener listener;

    public TrackAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
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
        ItemTrackBinding b = ItemTrackBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Song song = getItem(position);
        holder.bind(position + 1, song, listener);
    }

    public void setTracks(java.util.List<Song> songs) {
        submitList(songs);
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemTrackBinding binding;

        VH(ItemTrackBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(int trackNumber, Song song, Listener listener) {
            binding.trackNumberText.setText(String.valueOf(trackNumber));
            com.oru.app.ui.components.Covers.loadSongCover(binding.artworkImage,
                    song.id, song.coverUrl);
            binding.titleText.setText(song.title);
            binding.durationText.setText(song.durationLabel());
            binding.getRoot().setOnClickListener(v -> {
                android.content.Intent i = new android.content.Intent(
                        binding.getRoot().getContext(),
                        com.oru.app.ui.player.PlayerActivity.class);
                i.putExtra("songId", song.id);
                binding.getRoot().getContext().startActivity(i);
            });
            binding.getRoot().setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onLongPress(song);
                }
                return true;
            });
        }
    }
}
