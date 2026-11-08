package com.oru.app.ui.player;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.data.model.Song;
import com.oru.app.databinding.ItemTrackBinding;
import com.oru.app.ui.components.Covers;

import java.util.ArrayList;
import java.util.List;

/**
 * The queue, as a list you can jump around in.
 *
 * Tapping a row moves the session itself rather than starting a new queue, which
 * is the whole point of looking at what is coming.
 */
public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.VH> {

    public interface Listener {
        void onPick(int index);
    }

    private final List<Song> items = new ArrayList<>();
    private final Listener listener;
    private int current = -1;

    public QueueAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Song> songs, int currentIndex) {
        items.clear();
        if (songs != null) {
            items.addAll(songs);
        }
        current = currentIndex;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemTrackBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Song song = items.get(position);
        holder.binding.trackNumberText.setText(
                position == current ? "▶" : String.valueOf(position + 1));
        Covers.loadSongCover(holder.binding.artworkImage, song.id, song.coverUrl);
        holder.binding.titleText.setText(song.title);
        holder.binding.titleText.setAlpha(position == current ? 1f : 0.86f);
        holder.binding.durationText.setText(song.durationLabel());
        holder.binding.getRoot().setOnClickListener(v -> {
            if (listener != null) {
                listener.onPick(position);
            }
        });
        holder.binding.playButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemTrackBinding binding;

        VH(ItemTrackBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
