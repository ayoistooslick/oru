package com.oru.app.ui.library;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.Artist;
import com.oru.app.data.model.Playlist;
import com.oru.app.data.model.Song;
import com.oru.app.databinding.ItemLibrarySectionBinding;
import com.oru.app.databinding.ItemLibrarySongBinding;
import com.oru.app.databinding.ItemShareBinding;
import com.oru.app.ui.player.PlayerActivity;
import com.oru.app.util.MediaUrl;

import java.util.ArrayList;
import java.util.List;

/** The library list: section headers, song rows and playlist cards, flattened. */
public class LibraryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onSong(Song song);

        void onSongLongPress(Song song);

        void onPlaylist(Playlist playlist);

        void onPlaylistLongPress(Playlist playlist);

        void onArtist(Artist artist);
    }

    public static final class Row {
        public static final int HEADER = 0;
        public static final int SONG = 1;
        public static final int PLAYLIST = 2;
        public static final int ARTIST = 3;

        public final int type;
        public final String title;
        public final String count;
        public final Song song;
        public final Playlist playlist;
        public final Artist artist;

        private Row(int type, String title, String count, Song song,
                Playlist playlist, Artist artist) {
            this.type = type;
            this.title = title;
            this.count = count;
            this.song = song;
            this.playlist = playlist;
            this.artist = artist;
        }

        public static Row header(String title, String count) {
            return new Row(HEADER, title, count, null, null, null);
        }

        public static Row song(Song song) {
            return new Row(SONG, null, null, song, null, null);
        }

        public static Row playlist(Playlist playlist) {
            return new Row(PLAYLIST, null, null, null, playlist, null);
        }

        public static Row artist(Artist artist) {
            return new Row(ARTIST, null, null, null, null, artist);
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private Listener listener;

    public void setRows(List<Row> next) {
        rows.clear();
        if (next != null) {
            rows.addAll(next);
        }
        notifyDataSetChanged();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
            int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == Row.HEADER) {
            return new HeaderVH(ItemLibrarySectionBinding.inflate(inflater, parent,
                    false));
        }
        if (viewType == Row.SONG) {
            return new SongVH(ItemLibrarySongBinding.inflate(inflater, parent,
                    false));
        }
        if (viewType == Row.ARTIST) {
            return new ArtistVH(ItemShareBinding.inflate(inflater, parent,
                    false));
        }
        return new PlaylistVH((com.google.android.material.card.MaterialCardView)
                inflater.inflate(R.layout.item_playlist_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder,
            int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderVH) {
            ((HeaderVH) holder).bind(row);
        } else if (holder instanceof SongVH) {
            ((SongVH) holder).bind(row.song);
        } else if (holder instanceof PlaylistVH) {
            ((PlaylistVH) holder).bind(row.playlist);
        } else if (holder instanceof ArtistVH) {
            ((ArtistVH) holder).bind(row.artist);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private final class HeaderVH extends RecyclerView.ViewHolder {
        private final ItemLibrarySectionBinding binding;

        HeaderVH(ItemLibrarySectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Row row) {
            binding.titleText.setText(row.title);
            binding.countText.setText(row.count != null ? row.count : "");
        }
    }

    private final class SongVH extends RecyclerView.ViewHolder {
        private final ItemLibrarySongBinding binding;

        SongVH(ItemLibrarySongBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Song song) {
            com.oru.app.ui.components.Covers.loadSongCover(binding.artworkImage,
                    song.id, song.coverUrl);
            binding.titleText.setText(song.title);
            binding.artistText.setText(song.artistLabel());
            binding.likesText.setText(song.durationLabel());
            binding.playButton.setOnClickListener(v -> open(song));
            binding.getRoot().setOnClickListener(v -> open(song));
            binding.getRoot().setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onSongLongPress(song);
                }
                return true;
            });
        }

        private void open(Song song) {
            if (listener != null) {
                listener.onSong(song);
            }
            Intent intent = new Intent(binding.getRoot().getContext(),
                    PlayerActivity.class);
            intent.putExtra("songId", song.id);
            binding.getRoot().getContext().startActivity(intent);
        }
    }

    /** An artist I follow, opening their profile. */
    private final class ArtistVH extends RecyclerView.ViewHolder {
        private final ItemShareBinding binding;

        ArtistVH(ItemShareBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Artist artist) {
            binding.artworkImage.setVisibility(android.view.View.GONE);
            binding.titleText.setText(artist.stageName != null
                    ? artist.stageName
                    : artist.handle);
            binding.artistText.setText("@" + artist.handle);
            binding.statusText.setText(artist.followerCount + " followers");
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onArtist(artist);
                }
            });
        }
    }

    private final class PlaylistVH extends RecyclerView.ViewHolder {
        private final com.google.android.material.card.MaterialCardView card;
        private final android.widget.ImageView cover;
        private final android.widget.TextView title;
        private final android.widget.TextView subtitle;
        private final android.widget.TextView count;

        PlaylistVH(com.google.android.material.card.MaterialCardView card) {
            super(card);
            this.card = card;
            cover = card.findViewById(R.id.coverImage);
            title = card.findViewById(R.id.titleText);
            subtitle = card.findViewById(R.id.subtitleText);
            count = card.findViewById(R.id.songCountText);
        }

        void bind(Playlist playlist) {
            title.setText(playlist.title);
            String owner = playlist.owner != null
                    && playlist.owner.displayName != null
                            ? "by " + playlist.owner.displayName
                            : (playlist.isPublic ? "Public" : "Private");
            subtitle.setText(owner);
            count.setText(playlist.songCount() + " songs");
            String art = MediaUrl.of(playlist.coverUrl);
            if (art != null) {
                com.bumptech.glide.Glide.with(card.getContext())
                        .load(art)
                        .placeholder(R.drawable.ic_library)
                        .into(cover);
            } else {
                cover.setImageResource(R.drawable.ic_library);
            }
            card.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlaylist(playlist);
                }
            });
            card.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onPlaylistLongPress(playlist);
                }
                return true;
            });
        }
    }
}
