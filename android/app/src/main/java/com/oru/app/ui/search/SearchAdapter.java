package com.oru.app.ui.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.databinding.ItemSearchResultBinding;
import android.content.Context;
import android.content.Intent;
import android.view.View;

public class SearchAdapter extends ListAdapter<SearchResult, SearchAdapter.VH> {

    public SearchAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<SearchResult> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<SearchResult>() {
                @Override
                public boolean areItemsTheSame(@NonNull SearchResult oldItem,
                        @NonNull SearchResult newItem) {
                    return oldItem.type == newItem.type
                            && getItemIdentity(oldItem)
                                    .equals(getItemIdentity(newItem));
                }

                @Override
                public boolean areContentsTheSame(@NonNull SearchResult oldItem,
                        @NonNull SearchResult newItem) {
                    return oldItem.title.equals(newItem.title)
                            && oldItem.subtitle.equals(newItem.subtitle)
                            && oldItem.meta.equals(newItem.meta);
                }

                private String getItemIdentity(SearchResult item) {
                    if (item.song != null) return "s:" + item.song.id;
                    if (item.artist != null) return "a:" + item.artist.artistId();
                    if (item.playlist != null) return "p:" + item.playlist.id;
                    return "";
                }
            };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSearchResultBinding b = ItemSearchResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemSearchResultBinding binding;

        VH(ItemSearchResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(SearchResult item) {
            binding.typeLabel.setText(item.type.name().toLowerCase());
            binding.titleText.setText(item.title);
            binding.subtitleText.setText(item.subtitle);
            binding.metaText.setText(item.meta);

            binding.getRoot().setOnClickListener(v -> {
                Context ctx = binding.getRoot().getContext();
                if (item.song != null) {
                    Intent i = new Intent(ctx, com.oru.app.ui.player.PlayerActivity.class);
                    i.putExtra("songId", item.song.id);
                    ctx.startActivity(i);
                } else if (item.artist != null) {
                    Intent i = new Intent(ctx, com.oru.app.ui.artist.ArtistActivity.class);
                    i.putExtra("artistId", item.artist.artistId());
                    ctx.startActivity(i);
                } else if (item.playlist != null) {
                    Intent i = new Intent(ctx, com.oru.app.ui.playlist.PlaylistActivity.class);
                    i.putExtra("playlistId", item.playlist.id);
                    ctx.startActivity(i);
                }
            });
        }
    }
}
