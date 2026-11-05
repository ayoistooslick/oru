package com.oru.app.ui.studio;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.Song;
import com.oru.app.databinding.ItemStudioSongBinding;

public class StudioAdapter extends ListAdapter<Song, StudioAdapter.VH> {

    /** Long-press actions for a track in the studio. */
    public interface Listener {
        void onPublish(Song song);

        void onUnpublish(Song song);

        void onDelete(Song song);
    }

    private Listener listener;

    public StudioAdapter() {
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
                            && oldItem.isPublished() == newItem.isPublished();
                }
            };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStudioSongBinding b = ItemStudioSongBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position), getCurrentList(), position);
    }

    class VH extends RecyclerView.ViewHolder {
        private final ItemStudioSongBinding binding;

        VH(ItemStudioSongBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Song song, java.util.List<Song> queue, int index) {
            com.oru.app.ui.components.Covers.loadSongCover(binding.artworkImage,
                    song.id, song.coverUrl);
            binding.titleText.setText(song.title);
            binding.statusText.setText(song.isPublished() ? "Published" : "Unpublished");
            binding.statusText.setTextColor(
                    binding.getRoot().getContext().getColor(
                            song.isPublished() ? R.color.ink_faint : R.color.flame));
            binding.getRoot().setOnClickListener(v ->
                    com.oru.app.ui.player.Playback.start(
                            binding.getRoot().getContext(), queue, index));
            binding.getRoot().setOnLongClickListener(v -> {
                showActions(song);
                return true;
            });
        }

        private void showActions(Song song) {
            android.content.Context context = binding.getRoot().getContext();
            String[] items = song.isPublished()
                    ? new String[]{"Unpublish", "Delete"}
                    : new String[]{"Publish", "Delete"};
            new androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle(song.title)
                    .setItems(items, (dialog, which) -> {
                        if (listener == null) {
                            return;
                        }
                        boolean first = which == 0;
                        if (first && song.isPublished()) {
                            listener.onUnpublish(song);
                        } else if (first) {
                            listener.onPublish(song);
                        } else {
                            confirmDelete(context, song);
                        }
                    })
                    .show();
        }

        private void confirmDelete(android.content.Context context, Song song) {
            new androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Delete " + song.title + "?")
                    .setMessage("This removes the track and its audio for good.")
                    .setNegativeButton(com.oru.app.R.string.cancel, null)
                    .setPositiveButton("Delete", (dialog, which) -> {
                        if (listener != null) {
                            listener.onDelete(song);
                        }
                    })
                    .show();
        }
    }
}
