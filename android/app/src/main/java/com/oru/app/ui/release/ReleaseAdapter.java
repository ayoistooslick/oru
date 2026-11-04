package com.oru.app.ui.release;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.Release;
import com.oru.app.databinding.ItemReleaseBinding;

public class ReleaseAdapter extends ListAdapter<Release, ReleaseAdapter.VH> {

    public ReleaseAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<Release> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Release>() {
                @Override
                public boolean areItemsTheSame(@NonNull Release oldItem,
                        @NonNull Release newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Release oldItem,
                        @NonNull Release newItem) {
                    return oldItem.title.equals(newItem.title)
                            && java.util.Objects.equals(oldItem.kind,
                                    newItem.kind)
                            && java.util.Objects.equals(oldItem.status,
                                    newItem.status);
                }
            };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReleaseBinding b = ItemReleaseBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemReleaseBinding binding;

        VH(ItemReleaseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Release release) {
            binding.titleText.setText(release.title);
            binding.kindText.setText(release.kind);
            String cover = com.oru.app.util.MediaUrl.of(release.coverUrl);
            if (cover != null) {
                com.bumptech.glide.Glide.with(binding.getRoot().getContext())
                        .load(cover)
                        .placeholder(com.oru.app.R.drawable.ic_library)
                        .into(binding.coverImage);
            }
            binding.playsText.setText(
                    release.isPublished() ? "Published" : "Unreleased");
            binding.coverImage.setOnClickListener(v -> {
                android.content.Intent i = new android.content.Intent(
                        binding.getRoot().getContext(),
                        com.oru.app.ui.release.ReleaseActivity.class);
                i.putExtra("releaseId", release.id);
                binding.getRoot().getContext().startActivity(i);
            });
            binding.getRoot().setOnClickListener(v -> {
                android.content.Intent i = new android.content.Intent(
                        binding.getRoot().getContext(),
                        com.oru.app.ui.release.ReleaseActivity.class);
                i.putExtra("releaseId", release.id);
                binding.getRoot().getContext().startActivity(i);
            });
        }
    }
}
