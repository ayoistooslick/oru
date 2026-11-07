package com.oru.app.ui.release;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.Release;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.databinding.ActivityReleaseBinding;
import com.oru.app.media.PlayerService;
import com.oru.app.util.LazySession;

import java.util.List;

public class ReleaseActivity extends AppCompatActivity {

    private ActivityReleaseBinding binding;
    private TrackAdapter trackAdapter;
    private MusicRepository musicRepo;
    private Release release;
    private com.oru.app.ui.components.MiniPlayer miniPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReleaseBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        trackAdapter = new TrackAdapter();

        binding.tracksRecyclerView.setLayoutManager(
                new LinearLayoutManager(this));
        binding.tracksRecyclerView.setAdapter(trackAdapter);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.playerPlayButton.setOnClickListener(v -> togglePlay());
        miniPlayer = new com.oru.app.ui.components.MiniPlayer(this,
                binding.miniPlayer);

        binding.toolbar.inflateMenu(R.menu.release_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (release == null) {
                return false;
            }
            if (item.getItemId() == R.id.action_share) {
                com.oru.app.ui.components.Actions.share(this,
                        "Listen to " + release.title + " on ORU",
                        "https://oru.freebuff.app/release/" + release.id);
                return true;
            }
            if (item.getItemId() == R.id.action_report) {
                com.oru.app.ui.components.Actions.report(this, "artist",
                        release.artistId, release.title);
                return true;
            }
            return false;
        });

        binding.errorView.retryButton.setOnClickListener(v -> {
            binding.errorView.getRoot().setVisibility(View.GONE);
            loadRelease();
        });

        if (musicRepo == null) {
            musicRepo = new MusicRepository(
                    LazySession.getApiService(),
                    LazySession.getGson());
        }

        loadRelease();
    }

    private void loadRelease() {
        int releaseId;
        try {
            releaseId = getIntent().getIntExtra("releaseId", -1);
        } catch (Exception e) {
            releaseId = -1;
        }
        if (releaseId <= 0) {
            finish();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.errorView.getRoot().setVisibility(View.GONE);

        musicRepo.getRelease(releaseId, new Callback<Release>() {
            @Override
            public void onSuccess(Release r) {
                release = r;
                populate(r);
                loadCover(firstCover(r));
            }

            @Override
            public void onFailure(Throwable error) {
                onFailure(error);
            }
        });
    }

    private void populate(Release r) {
        binding.collapsingToolbar.setTitle(r.title);
        binding.titleText.setText(r.title);
        binding.artistText.setText(
                r.songs != null && !r.songs.isEmpty()
                        ? r.songs.get(0).artistLabel()
                        : "");
        binding.artistText.setOnClickListener(v ->
                com.oru.app.ui.components.Actions.openArtist(this,
                        r.artistId));
        binding.playsCountText.setText(
                (r.songs != null ? r.songs.size() : 0) + " tracks");
        binding.descriptionText.setText(
                r.songs != null && r.songs.size() > 0
                        ? "A " + r.kind + " with " + r.songs.size() + " tracks"
                        : "");

        boolean hasSongs = r.songs != null && !r.songs.isEmpty();
        if (hasSongs) {
            trackAdapter.submitList(r.songs);
            binding.tracksRecyclerView.setVisibility(View.VISIBLE);
            // The play-all bar was only revealed after a tap, so the release
            // looked like it had no way to play. It is part of the page now.
            binding.playerBar.setVisibility(View.VISIBLE);
            binding.playerTitle.setText(r.title);
            binding.playerArtist.setText(r.songs.get(0).artistLabel());
        } else {
            binding.tracksRecyclerView.setVisibility(View.GONE);
            binding.playerBar.setVisibility(View.GONE);
        }

        binding.progressBar.setVisibility(View.GONE);
    }

    private void loadCover(String coverUrl) {
        // Covers turns the API's media path into a real address.
        com.oru.app.ui.components.Covers.load(binding.coverImage, coverUrl,
                R.drawable.ic_library);
    }

    private void togglePlay() {
        if (release == null || release.songs == null || release.songs.isEmpty()) {
            return;
        }
        // The same queue the rest of the app uses, so next and previous walk it.
        com.oru.app.ui.player.Playback.start(this, release.songs, 0);
    }

    private void onFailure(Throwable error) {
        binding.progressBar.setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.VISIBLE);
        binding.errorView.errorMessage.setText(describe(error));
    }

    /** The API reports the release's artwork, borrowed from its first track. */
    private String firstCover(Release r) {
        if (r == null) {
            return null;
        }
        String cover = com.oru.app.util.MediaUrl.of(r.coverUrl);
        if (cover != null) {
            return cover;
        }
        return r.songs != null && !r.songs.isEmpty()
                ? com.oru.app.util.MediaUrl.of(r.songs.get(0).coverUrl)
                : null;
    }

    private String describe(Throwable error) {
        return com.oru.app.ui.components.UiErrors.message(this, error);
    }

    private String formatPlays(int count) {
        if (count >= 1000) return (count / 1000F) + "K plays";
        return count + " plays";
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
