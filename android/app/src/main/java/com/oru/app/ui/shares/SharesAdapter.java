package com.oru.app.ui.shares;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.SharedSong;
import com.oru.app.databinding.ItemShareBinding;

public class SharesAdapter extends ListAdapter<SharedSong, SharesAdapter.VH> {

    public SharesAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<SharedSong> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<SharedSong>() {
                @Override
                public boolean areItemsTheSame(@NonNull SharedSong oldItem,
                        @NonNull SharedSong newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull SharedSong oldItem,
                        @NonNull SharedSong newItem) {
                    return oldItem.shareStatus.equals(newItem.shareStatus);
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
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemShareBinding binding;

        VH(ItemShareBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(SharedSong item) {
            binding.titleText.setText(item.song != null ? item.song.title : "");
            binding.artistText.setText(
                    item.song != null && item.song.artist != null
                            ? item.song.artist.stageName
                            : "");
            binding.statusText.setText(item.shareStatus);
            binding.getRoot().setOnClickListener(v -> {
                if (item.song != null) {
                    android.content.Intent i = new android.content.Intent(
                            binding.getRoot().getContext(),
                            com.oru.app.ui.player.PlayerActivity.class);
                    i.putExtra("songId", item.song.id);
                    binding.getRoot().getContext().startActivity(i);
                }
            });
        }
    }
}
