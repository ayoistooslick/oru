package com.oru.app.ui.components;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.LikeResult;
import com.oru.app.data.model.Song;
import com.oru.app.data.offline.Downloads;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.CommunityRepository;
import com.oru.app.ui.components.UiErrors;
import com.oru.app.databinding.ItemFeedBinding;
import com.oru.app.util.LazySession;

import java.util.Objects;

/** The following feed: songs from the artists you follow, newest first. */
public class FeedAdapter extends ListAdapter<Song, FeedAdapter.VH> {

    public FeedAdapter() {
        super(DIFF_CALLBACK);
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
                    return Objects.equals(oldItem.title, newItem.title)
                            && Objects.equals(oldItem.artistName,
                                    newItem.artistName)
                            && Objects.equals(oldItem.status, newItem.status);
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
        holder.bind(getItem(position), getCurrentList(), position);
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemFeedBinding binding;

        VH(ItemFeedBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Song song, java.util.List<Song> queue, int index) {
            Covers.loadSongCover(binding.artworkImage, song.id, song.coverUrl);
            binding.typeIcon.setImageResource(R.drawable.ic_music);
            binding.typeLabel.setText("Song");
            binding.titleText.setText(song.title);
            binding.subtitleText.setText(song.artistLabel());
            // The artist name is a link, as it is on every web row.
            binding.subtitleText.setOnClickListener(v -> Actions.openArtist(
                    binding.getRoot().getContext(), song.artistId));
            String length = song.durationLabel();
            String genres = song.genreLabel();
            binding.metaText.setText(length.isEmpty() ? genres
                    : genres.isEmpty() ? length : length + " · " + genres);
            binding.playButton.setVisibility(View.VISIBLE);
            binding.getRoot().setOnClickListener(v ->
                    com.oru.app.ui.player.Playback.start(
                            binding.getRoot().getContext(), queue, index));
            binding.playButton.setOnClickListener(v ->
                    com.oru.app.ui.player.Playback.start(
                            binding.getRoot().getContext(), queue, index));

            binding.getRoot().setOnLongClickListener(v -> {
                showActions(song);
                return true;
            });

            int tint = song.isLiked ? R.color.flame : R.color.ink_faint;
            Context context = binding.getRoot().getContext();
            binding.likeButton.setImageTintList(ColorStateList.valueOf(
                    context.getColor(tint)));
            binding.likeButton.setOnClickListener(v -> toggleLike(song));
        }

        /** The heart in every row works exactly like the web one. */
        private void toggleLike(Song song) {
            Context context = binding.getRoot().getContext();
            boolean liked = song.isLiked;
            CommunityRepository repo = new CommunityRepository(
                    LazySession.getApiService(), LazySession.getGson());
            Callback<LikeResult> done = new Callback<LikeResult>() {
                @Override
                public void onSuccess(LikeResult result) {
                    song.isLiked = result != null ? result.liked : !liked;
                    if (result != null) {
                        song.likesCount = result.likesCount;
                    }
                    refreshRow(getBindingAdapterPosition());
                }

                @Override
                public void onFailure(Throwable error) {
                    Toast.makeText(context,
                            UiErrors.message(context, error),
                            Toast.LENGTH_LONG).show();
                }
            };
            if (liked) {
                repo.unlikeSong(song.id, done);
            } else {
                repo.likeSong(song.id, done);
            }
        }

        private void refreshRow(int position) {
            if (position != RecyclerView.NO_POSITION) {
                getBindingAdapter().notifyItemChanged(position);
            }
        }

        /** Long press: the row's extra actions, same set as the web row menu. */
        private void showActions(Song song) {
            Context context = binding.getRoot().getContext();
            boolean saved = Downloads.isDownloaded(context, song.id);
            String[] options = {
                    "Add to playlist",
                    saved ? "Remove download" : "Save for offline",
                    "Share",
                    "Report",
                    "Go to artist"
            };
            new AlertDialog.Builder(context)
                    .setTitle(song.title)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            PlaylistPicker.addTo(context, song);
                        } else if (which == 1) {
                            if (saved) {
                                Downloads.remove(context, song.id);
                                Toast.makeText(context, "Download removed",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Downloads.download(context, song, percent -> {
                                }, (ok, message) -> Toast.makeText(context,
                                        message, Toast.LENGTH_SHORT).show());
                            }
                        } else if (which == 2) {
                            share(context, song);
                        } else if (which == 3) {
                            report(context, song);
                        } else if (which == 4 && song.artistId > 0) {
                            Intent intent = new Intent(context,
                                    com.oru.app.ui.artist.ArtistActivity.class);
                            intent.putExtra("artistId", song.artistId);
                            context.startActivity(intent);
                        }
                    })
                    .show();
        }

        private void share(Context context, Song song) {
            Actions.share(context, song.title + " by " + song.artistLabel(),
                    "https://oru.freebuff.app/artist/" + song.artistId);
        }

        private void report(Context context, Song song) {
            Actions.report(context, "song", song.id, song.title);
        }
    }
}
