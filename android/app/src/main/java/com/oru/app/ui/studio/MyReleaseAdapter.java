package com.oru.app.ui.studio;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.data.model.Release;
import com.oru.app.databinding.ItemStudioReleaseBinding;
import com.oru.app.ui.release.ReleaseActivity;

import java.util.Objects;

/** My releases, with a publish action while they are still drafts. */
public class MyReleaseAdapter
        extends ListAdapter<Release, MyReleaseAdapter.VH> {

    public interface Listener {
        void onPublish(Release release);
    }

    private Listener listener;

    public MyReleaseAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
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
                    return Objects.equals(oldItem.title, newItem.title)
                            && oldItem.isPublished() == newItem.isPublished();
                }
            };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStudioReleaseBinding b = ItemStudioReleaseBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    class VH extends RecyclerView.ViewHolder {
        private final ItemStudioReleaseBinding binding;

        VH(ItemStudioReleaseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Release release) {
            binding.titleText.setText(release.title);
            String kind = release.kind != null ? release.kind : "release";
            String status = release.isPublished() ? "Out now" : "Draft";
            binding.metaText.setText(kind + " · " + release.songCount
                    + " songs · " + status);
            boolean published = release.isPublished();
            binding.publishButton.setVisibility(published ? View.GONE : View.VISIBLE);
            binding.publishButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPublish(release);
                }
            });
            binding.getRoot().setOnClickListener(v -> {
                Intent i = new Intent(binding.getRoot().getContext(),
                        ReleaseActivity.class);
                i.putExtra("releaseId", release.id);
                binding.getRoot().getContext().startActivity(i);
            });
        }
    }
}
