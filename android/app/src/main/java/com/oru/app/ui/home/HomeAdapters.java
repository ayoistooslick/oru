package com.oru.app.ui.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.RankingEntry;
import com.oru.app.data.model.Song;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.oru.app.databinding.ItemRailBinding;
import com.oru.app.databinding.ItemRankBinding;
import com.oru.app.databinding.ItemSectionBinding;
import com.oru.app.databinding.ItemSongCardBinding;
import com.oru.app.ui.components.Covers;
import com.oru.app.ui.player.Playback;

import java.util.ArrayList;
import java.util.List;

/** The six quick picks at the top of home, two to a line. */
class QuickGridAdapter extends RecyclerView.Adapter<QuickGridAdapter.VH> {

    private final List<Song> songs;

    QuickGridAdapter(List<Song> songs) {
        this.songs = songs;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemRailBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        if (holder.binding.railRecyclerView.getAdapter() != null) {
            return;
        }
        holder.binding.railRecyclerView.setLayoutManager(
                new androidx.recyclerview.widget.GridLayoutManager(
                        holder.itemView.getContext(), 2));
        holder.binding.railRecyclerView.setAdapter(new TileAdapter(songs));
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemRailBinding binding;

        VH(ItemRailBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static class TileAdapter extends RecyclerView.Adapter<TileAdapter.VH> {

        private final List<Song> songs;

        TileAdapter(List<Song> songs) {
            this.songs = songs;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(com.oru.app.databinding.ItemQuickTileBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Song song = songs.get(position);
            holder.binding.tileTitle.setText(song.title);
            Covers.loadSongCover(holder.binding.tileCover, song.id, song.coverUrl);
            holder.binding.getRoot().setOnClickListener(v -> Playback.start(
                    holder.binding.getRoot().getContext(), songs, position));
        }

        @Override
        public int getItemCount() {
            return songs.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final com.oru.app.databinding.ItemQuickTileBinding binding;

            VH(com.oru.app.databinding.ItemQuickTileBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}

/** A horizontally scrolling rail of song cards, like the web's "New songs". */
class RailAdapter extends RecyclerView.Adapter<RailAdapter.VH> {

    private final List<Song> songs;

    RailAdapter(List<Song> songs) {
        this.songs = songs;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemRailBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        if (holder.binding.railRecyclerView.getAdapter() != null) {
            return;
        }
        CardAdapter cards = new CardAdapter(songs);
        holder.binding.railRecyclerView.setLayoutManager(
                new LinearLayoutManager(holder.itemView.getContext(),
                        RecyclerView.HORIZONTAL, false));
        holder.binding.railRecyclerView.setAdapter(cards);
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemRailBinding binding;

        VH(ItemRailBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    /** The cards inside a rail; playing one walks the whole rail. */
    private static class CardAdapter extends RecyclerView.Adapter<CardAdapter.VH> {

        private final List<Song> songs;

        CardAdapter(List<Song> songs) {
            this.songs = songs;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemSongCardBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Song song = songs.get(position);
            holder.binding.cardTitle.setText(song.title);
            holder.binding.cardArtist.setText(song.artistLabel());
            Covers.loadSongCover(holder.binding.cardCover, song.id, song.coverUrl);
            holder.binding.getRoot().setOnClickListener(v -> Playback.start(
                    holder.binding.getRoot().getContext(), songs, position));
        }

        @Override
        public int getItemCount() {
            return songs.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final ItemSongCardBinding binding;

            VH(ItemSongCardBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}

/** A home section heading: "This week", "New songs"… */
class SectionAdapter extends RecyclerView.Adapter<SectionAdapter.VH> {

    private final String title;
    private final String subtitle;

    SectionAdapter(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemSectionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.binding.sectionTitle.setText(title);
        holder.binding.sectionSubtitle.setText(subtitle == null ? "" : subtitle);
        holder.binding.sectionSubtitle.setVisibility(
                subtitle == null ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemSectionBinding binding;

        VH(ItemSectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

/** The weekly chart, ranked, exactly like the web home page. */
class RankAdapter extends RecyclerView.Adapter<RankAdapter.VH> {

    private final List<RankingEntry> entries;
    private final List<Song> songs = new ArrayList<>();

    RankAdapter(List<RankingEntry> entries) {
        this.entries = entries;
        for (RankingEntry entry : entries) {
            if (entry != null && entry.song != null) {
                songs.add(entry.song);
            }
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemRankBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        RankingEntry entry = entries.get(position);
        Song song = entry.song;
        holder.binding.rankText.setText(String.valueOf(entry.rank));
        holder.binding.rankTitle.setText(song != null ? song.title : "");
        holder.binding.rankArtist.setText(
                song != null ? song.artistLabel() : "");
        holder.binding.rankPlays.setText(entry.plays + (entry.plays == 1
                ? " play" : " plays"));
        if (song != null) {
            Covers.loadSongCover(holder.binding.rankCover, song.id, song.coverUrl);
        }
        android.view.View.OnClickListener play = v -> {
            if (song != null) {
                Playback.start(holder.binding.getRoot().getContext(), songs,
                        Math.max(0, songs.indexOf(song)));
            }
        };
        holder.binding.getRoot().setOnClickListener(play);
        holder.binding.rankPlay.setOnClickListener(play);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemRankBinding binding;

        VH(ItemRankBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
