package com.oru.app.ui.components;

import android.view.View;


import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.FeedItem;
import com.oru.app.databinding.ItemFeedBinding;

public class FeedAdapter extends ListAdapter<FeedItem, FeedAdapter.VH> {

    public FeedAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<FeedItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<FeedItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull FeedItem oldItem,
                        @NonNull FeedItem newItem) {
                    return oldItem.type.equals(newItem.type)
                            && getItemIdentity(oldItem)
                                    .equals(getItemIdentity(newItem));
                }

                @Override
                public boolean areContentsTheSame(@NonNull FeedItem oldItem,
                        @NonNull FeedItem newItem) {
                    return oldItem.equals(newItem);
                }

                private String getItemIdentity(FeedItem item) {
                    if (item.song != null) return "s:" + item.song.id;
                    if (item.release != null) return "r:" + item.release.id;
                    if (item.post != null) return "p:" + item.post.id;
                    return "";
                }
            };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFeedBinding b = ItemFeedBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemFeedBinding binding;

        VH(ItemFeedBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FeedItem item) {
            if (item.song != null) {
                binding.typeIcon.setImageResource(R.drawable.ic_music);
                binding.typeLabel.setText("Song");
                binding.titleText.setText(item.song.title);
                binding.subtitleText.setText(
                        item.song.artist != null ? item.song.artist.stageName
                                : "");
                binding.metaText.setText(
                        item.song.durationMs / 1000 + "s · "
                                + item.song.likesCount + " likes");
                binding.playButton.setVisibility(View.VISIBLE);
                binding.getRoot().setOnClickListener(v -> {
                    android.content.Intent i = new android.content.Intent(
                            binding.getRoot().getContext(),
                            com.oru.app.ui.player.PlayerActivity.class);
                    i.putExtra("songId", item.song.id);
                    binding.getRoot().getContext().startActivity(i);
                });
            } else if (item.release != null) {
                binding.typeIcon.setImageResource(R.drawable.ic_music);
                binding.typeLabel.setText("Release");
                binding.titleText.setText(item.release.title);
                binding.subtitleText.setText(
                        item.release.artist != null
                                ? item.release.artist.stageName
                                : "");
                binding.metaText.setText(
                        (item.release.songs != null ? item.release.songs.size() : 0) + " tracks · "
                                + item.release.playCount + " plays");
                binding.playButton.setVisibility(View.GONE);
                binding.getRoot().setOnClickListener(v -> {
                    android.content.Intent i = new android.content.Intent(
                            binding.getRoot().getContext(),
                            com.oru.app.ui.release.ReleaseActivity.class);
                    i.putExtra("releaseId", item.release.id);
                    binding.getRoot().getContext().startActivity(i);
                });
            } else if (item.post != null) {
                binding.typeIcon.setImageResource(R.drawable.ic_studio);
                binding.typeLabel.setText("Post");
                binding.titleText.setText(
                        item.post.artist != null
                                ? item.post.artist.stageName
                                : "");
                binding.subtitleText.setText(item.post.body);
                binding.metaText.setText("");
                binding.playButton.setVisibility(View.GONE);
            }
        }
    }
}
