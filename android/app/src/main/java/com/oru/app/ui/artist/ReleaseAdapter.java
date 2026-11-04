package com.oru.app.ui.artist;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.oru.app.R;
import com.oru.app.data.model.Release;

public class ReleaseAdapter extends ListAdapter<Release, ReleaseAdapter.ViewHolder> {

    public ReleaseAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<Release> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Release>() {
                @Override
                public boolean areItemsTheSame(@NonNull Release oldItem, @NonNull Release newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Release oldItem, @NonNull Release newItem) {
                    return oldItem.title.equals(newItem.title)
                            && java.util.Objects.equals(oldItem.status,
                                    newItem.status);
                }
            };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_release, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Release release = getItem(position);
        holder.titleText.setText(release.title);
        holder.kindText.setText(release.kind != null ? release.kind : "");
        // The API sends the release's artwork; fall back to its first track.
        String cover = com.oru.app.util.MediaUrl.of(release.coverUrl);
        if (cover == null && release.songs != null && !release.songs.isEmpty()) {
            cover = com.oru.app.util.MediaUrl.of(release.songs.get(0).coverUrl);
        }
        if (cover != null) {
            Glide.with(holder.itemView.getContext())
                    .load(cover)
                    .into(holder.coverImage);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final androidx.appcompat.widget.AppCompatImageView coverImage;
        final android.widget.TextView titleText;
        final android.widget.TextView kindText;

        ViewHolder(@NonNull android.view.View itemView) {
            super(itemView);
            coverImage = itemView.findViewById(R.id.coverImage);
            titleText = itemView.findViewById(R.id.titleText);
            kindText = itemView.findViewById(R.id.kindText);
        }
    }
}
