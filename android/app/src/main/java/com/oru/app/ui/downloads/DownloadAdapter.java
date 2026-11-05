package com.oru.app.ui.downloads;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.oru.app.R;
import com.oru.app.data.model.Song;
import com.oru.app.databinding.ItemLibrarySongBinding;
import com.oru.app.util.MediaUrl;

import java.util.ArrayList;
import java.util.List;

/** Songs saved on the device: tap to play, long press to remove. */
public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.VH> {

    public interface Listener {
        void onPlay(Song song);

        void onRemove(Song song);
    }

    private final List<Song> items = new ArrayList<>();
    private final Listener listener;

    public DownloadAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Song> songs) {
        items.clear();
        if (songs != null) {
            items.addAll(songs);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemLibrarySongBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class VH extends RecyclerView.ViewHolder {
        private final ItemLibrarySongBinding binding;

        VH(ItemLibrarySongBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Song song) {
            binding.titleText.setText(song.title);
            String length = song.durationLabel();
            binding.artistText.setText(length.isEmpty()
                    ? song.artistLabel()
                    : song.artistLabel() + " · " + length);
            binding.likesText.setVisibility(View.GONE);
            binding.playButton.setImageResource(R.drawable.ic_play);
            Object cover = com.oru.app.data.offline.Downloads.localCover(
                    binding.getRoot().getContext(), song.id);
            if (cover == null) {
                cover = MediaUrl.of(song.coverUrl);
            }
            if (cover != null) {
                Glide.with(binding.getRoot()).load(cover)
                        .placeholder(R.drawable.ic_library)
                        .into(binding.artworkImage);
            } else {
                binding.artworkImage.setImageResource(R.drawable.ic_library);
            }
            binding.getRoot().setOnClickListener(v -> listener.onPlay(song));
            binding.playButton.setOnClickListener(v -> listener.onPlay(song));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onRemove(song);
                return true;
            });
        }
    }
}
