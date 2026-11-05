package com.oru.app.ui.studio;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.data.model.ArtistPost;
import com.oru.app.databinding.ItemPostBinding;

import java.util.Objects;

/** Short posts an artist put on their profile. */
public class PostAdapter extends ListAdapter<ArtistPost, PostAdapter.VH> {

    public PostAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<ArtistPost> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ArtistPost>() {
                @Override
                public boolean areItemsTheSame(@NonNull ArtistPost oldItem,
                        @NonNull ArtistPost newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull ArtistPost oldItem,
                        @NonNull ArtistPost newItem) {
                    return Objects.equals(oldItem.body, newItem.body);
                }
            };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPostBinding b = ItemPostBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemPostBinding binding;

        VH(ItemPostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ArtistPost post) {
            binding.bodyText.setText(post.body);
            String created = post.createdAt != null ? post.createdAt : "";
            binding.timeText.setText(post.isHidden ? "Hidden · " + created : created);
        }
    }
}
