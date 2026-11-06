package com.oru.app.ui.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.RankingEntry;
import com.oru.app.data.model.Song;
import com.oru.app.databinding.ItemRankBinding;
import com.oru.app.databinding.ItemSectionBinding;
import com.oru.app.ui.components.Covers;
import com.oru.app.ui.player.Playback;

import java.util.ArrayList;
import java.util.List;

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
