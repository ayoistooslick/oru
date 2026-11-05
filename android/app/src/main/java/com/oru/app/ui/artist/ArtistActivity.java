package com.oru.app.ui.artist;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.oru.app.R;
import com.oru.app.data.model.Artist;
import com.oru.app.data.model.ArtistPost;
import com.oru.app.data.model.Release;
import com.oru.app.data.model.Song;
import com.oru.app.ui.artist.SongAdapter;
import com.oru.app.ui.studio.PostAdapter;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.CommunityRepository;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.databinding.ActivityArtistBinding;
import com.oru.app.media.PlayerService;
import com.oru.app.util.LazySession;

import java.util.List;

public class ArtistActivity extends AppCompatActivity {

    private ActivityArtistBinding binding;
    private SongAdapter songAdapter;
    private SongAdapter featureAdapter;
    private ReleaseAdapter releaseAdapter;
    private PostAdapter postAdapter;
    private MusicRepository musicRepo;
    private CommunityRepository communityRepo;
    private Artist artist;
    private boolean following;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityArtistBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        songAdapter = new SongAdapter();
        featureAdapter = new SongAdapter();
        releaseAdapter = new ReleaseAdapter();
        postAdapter = new PostAdapter();

        binding.songsRecyclerView.setLayoutManager(
                new LinearLayoutManager(this));
        binding.songsRecyclerView.setAdapter(songAdapter);

        binding.releasesRecyclerView.setLayoutManager(
                new LinearLayoutManager(this));
        binding.releasesRecyclerView.setAdapter(releaseAdapter);

        binding.postsRecyclerView.setLayoutManager(
                new LinearLayoutManager(this));
        binding.postsRecyclerView.setAdapter(postAdapter);

        binding.appearsOnRecyclerView.setLayoutManager(
                new LinearLayoutManager(this));
        binding.appearsOnRecyclerView.setAdapter(featureAdapter);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.followButton.setOnClickListener(v -> toggleFollow());

        binding.errorView.retryButton.setOnClickListener(v -> {
            binding.errorView.getRoot().setVisibility(View.GONE);
            loadArtist();
        });

        if (musicRepo == null) {
            musicRepo = new MusicRepository(
                    LazySession.getApiService(),
                    LazySession.getGson());
            communityRepo = new CommunityRepository(
                    LazySession.getApiService(),
                    LazySession.getGson());
        }

        loadArtist();
    }

    private void loadArtist() {
        int artistId;
        try {
            artistId = getIntent().getIntExtra("artistId", -1);
        } catch (Exception e) {
            artistId = -1;
        }
        if (artistId <= 0) {
            finish();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.errorView.getRoot().setVisibility(View.GONE);

        musicRepo.getArtist(artistId, new Callback<Artist>() {
            @Override
            public void onSuccess(Artist a) {
                artist = a;
                following = a.isFollowed;
                populateHeader(a);
                loadContent(a.artistId());
            }

            @Override
            public void onFailure(Throwable error) {
                onFailure(error);
            }
        });
    }

    private void populateHeader(Artist a) {
        binding.collapsingToolbar.setTitle(a.stageName);
        binding.stageNameText.setText(a.stageName);
        binding.listenersText.setText(String.valueOf(a.followerCount));
        binding.bioText.setText(a.bio != null ? a.bio : "");
        binding.songCountText.setText(String.valueOf(a.publishedSongCount));
        binding.playsCountText.setText(String.valueOf(a.playsCount));
        binding.followersCountText.setText(formatFollows(a.followerCount));
        updateFollowButton(following);

        loadImage(binding.bannerImage, a.bannerUrl);
        loadImage(binding.avatarImage, a.avatarUrl);
    }

    private void loadContent(int artistId) {
        musicRepo.getSongsByArtist(artistId, 50, 0, new Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> songs) {
                if (songs != null && !songs.isEmpty()) {
                    songAdapter.submitList(songs);
                    binding.songsRecyclerView.setVisibility(View.VISIBLE);
                } else {
                    binding.songsRecyclerView.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                binding.songsRecyclerView.setVisibility(View.GONE);
            }
        });

        musicRepo.getPostsByArtist(artistId, new Callback<List<ArtistPost>>() {
            @Override
            public void onSuccess(List<ArtistPost> posts) {
                boolean empty = posts == null || posts.isEmpty();
                if (!empty) {
                    postAdapter.submitList(posts);
                }
                binding.postsHeader.setVisibility(
                        empty ? View.GONE : View.VISIBLE);
                binding.postsRecyclerView.setVisibility(
                        empty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onFailure(Throwable error) {
                binding.postsHeader.setVisibility(View.GONE);
                binding.postsRecyclerView.setVisibility(View.GONE);
            }
        });

        musicRepo.getAppearsOn(artistId, new Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> songs) {
                boolean empty = songs == null || songs.isEmpty();
                if (!empty) {
                    featureAdapter.submitList(songs);
                }
                binding.appearsOnHeader.setVisibility(
                        empty ? View.GONE : View.VISIBLE);
                binding.appearsOnRecyclerView.setVisibility(
                        empty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onFailure(Throwable error) {
                binding.appearsOnHeader.setVisibility(View.GONE);
                binding.appearsOnRecyclerView.setVisibility(View.GONE);
            }
        });

        musicRepo.getReleasesByArtist(artistId, 50, 0,
                new Callback<List<Release>>() {
            @Override
            public void onSuccess(List<Release> releases) {
                if (releases != null && !releases.isEmpty()) {
                    releaseAdapter.submitList(releases);
                    binding.releasesRecyclerView.setVisibility(View.VISIBLE);
                } else {
                    binding.releasesRecyclerView.setVisibility(View.GONE);
                }
                binding.progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onFailure(Throwable error) {
                binding.releasesRecyclerView.setVisibility(View.GONE);
                binding.progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void toggleFollow() {
        if (artist == null) return;
        int artistId = artist.artistId();
        if (following) {
            communityRepo.unfollowArtist(artistId, new Callback<com.oru.app.data.model.FollowResult>() {
                @Override
                public void onSuccess(com.oru.app.data.model.FollowResult r) {
                    following = r != null && r.following;
                    updateFollowButton(following);
                }

                @Override
                public void onFailure(Throwable error) {
                    showFailure(error);
                }
            });
        } else {
            communityRepo.followArtist(artistId, new Callback<com.oru.app.data.model.FollowResult>() {
                @Override
                public void onSuccess(com.oru.app.data.model.FollowResult r) {
                    following = r == null || r.following;
                    updateFollowButton(following);
                }

                @Override
                public void onFailure(Throwable error) {
                    showFailure(error);
                }
            });
        }
    }

    private void updateFollowButton(boolean following) {
        binding.followButton.setText(following ? "Following" : "Follow");
        binding.followButton.setBackgroundColor(
                getColor(following ? R.color.ink_faint : R.color.flame));
        binding.followButton.setTextColor(
                getColor(following ? R.color.ink : R.color.night));
    }

    private void onFailure(Throwable error) {
        binding.progressBar.setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.VISIBLE);
        binding.errorView.errorMessage.setText(describe(error));
    }

    private void showFailure(Throwable error) {
        Snackbar.make(binding.getRoot(), describe(error),
                Snackbar.LENGTH_SHORT).show();
    }

    private String describe(Throwable error) {
        return com.oru.app.ui.components.UiErrors.message(this, error);
    }

    private void loadImage(android.widget.ImageView view,
            String url) {
        if (url == null) {
            view.setImageResource(R.drawable.ic_library);
            return;
        }
        com.bumptech.glide.Glide.with(this)
                .load(url)
                .placeholder(R.drawable.ic_library)
                .error(R.drawable.ic_library)
                .into(view);
    }

    private String formatFollows(int count) {
        if (count >= 1000) return (count / 1000F) + "K";
        return String.valueOf(count);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
