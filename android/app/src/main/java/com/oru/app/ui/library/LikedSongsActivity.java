package com.oru.app.ui.library;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.oru.app.data.model.Song;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.CommunityRepository;
import com.oru.app.databinding.ActivityLikedSongsBinding;
import com.oru.app.ui.components.MiniPlayer;
import com.oru.app.ui.components.UiErrors;
import com.oru.app.ui.player.Playback;
import com.oru.app.ui.release.TrackAdapter;
import com.oru.app.util.LazySession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Liked Songs, as its own playlist page — the heart in the library opens this.
 * It reads the same likes the grid counts, so the two never disagree.
 */
public class LikedSongsActivity extends AppCompatActivity {

    private ActivityLikedSongsBinding binding;
    private TrackAdapter trackAdapter;
    private CommunityRepository communityRepo;
    private MiniPlayer miniPlayer;
    private List<Song> songs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLikedSongsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        trackAdapter = new TrackAdapter();
        binding.tracksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.tracksRecyclerView.setAdapter(trackAdapter);

        binding.backButton.setOnClickListener(v -> finish());
        binding.playAllButton.setOnClickListener(v -> play(0));
        binding.shuffleButton.setOnClickListener(v -> shuffle());

        binding.errorView.retryButton.setOnClickListener(v -> {
            binding.errorView.getRoot().setVisibility(View.GONE);
            load();
        });

        miniPlayer = new MiniPlayer(this, binding.miniPlayer);

        communityRepo = new CommunityRepository(LazySession.getApiService(),
                LazySession.getGson());
        load();
    }

    private void load() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.errorView.getRoot().setVisibility(View.GONE);
        communityRepo.getMyLikes(new Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> items) {
                if (binding == null) {
                    return;
                }
                songs = items != null ? items : new ArrayList<>();
                binding.progressBar.setVisibility(View.GONE);
                render();
            }

            @Override
            public void onFailure(Throwable error) {
                if (binding == null) {
                    return;
                }
                binding.progressBar.setVisibility(View.GONE);
                binding.errorView.getRoot().setVisibility(View.VISIBLE);
                binding.errorView.errorMessage.setText(
                        UiErrors.message(LikedSongsActivity.this, error));
            }
        });
    }

    private void render() {
        binding.likedCountText.setText(songs.size()
                + (songs.size() == 1 ? " song" : " songs"));
        trackAdapter.submitList(songs);
        boolean empty = songs.isEmpty();
        binding.likedEmptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.tracksRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.playAllButton.setEnabled(!empty);
        binding.shuffleButton.setEnabled(!empty);
        binding.playAllButton.setAlpha(empty ? 0.4f : 1f);
    }

    private void play(int index) {
        if (songs.isEmpty()) {
            return;
        }
        Playback.start(this, new ArrayList<>(songs), index);
    }

    /** Shuffle plays the same songs from a randomised order. */
    private void shuffle() {
        if (songs.isEmpty()) {
            return;
        }
        List<Song> shuffled = new ArrayList<>(songs);
        Collections.shuffle(shuffled);
        Playback.start(this, shuffled, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (miniPlayer != null) {
            miniPlayer.release();
            miniPlayer = null;
        }
        binding = null;
    }
}
