package com.oru.app.ui.shares;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.data.model.Song;
import com.oru.app.databinding.ItemShareRequestBinding;

import java.util.Objects;

/** Songs a listener sent to my artist profile, waiting for approval. */
public class ShareRequestAdapter
        extends ListAdapter<Song, ShareRequestAdapter.VH> {

    public interface Listener {
        void onApprove(Song song);

        void onReject(Song song);
    }

    private Listener listener;

    public ShareRequestAdapter() {
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
                    return Objects.equals(oldItem.status, newItem.status)
                            && Objects.equals(oldItem.title, newItem.title);
                }
            };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemShareRequestBinding b = ItemShareRequestBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    class VH extends RecyclerView.ViewHolder {
        private final ItemShareRequestBinding binding;

        VH(ItemShareRequestBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Song song) {
            binding.titleText.setText(song.title);
            binding.artistText.setText(song.artistLabel());
            binding.approveButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onApprove(song);
                }
            });
            binding.rejectButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onReject(song);
                }
            });
        }
    }
}
