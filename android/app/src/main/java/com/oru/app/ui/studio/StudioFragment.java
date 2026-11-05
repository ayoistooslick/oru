package com.oru.app.ui.studio;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.oru.app.R;
import com.oru.app.data.model.ArtistPost;
import com.oru.app.data.model.Release;
import com.oru.app.data.model.Song;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.databinding.FragmentStudioBinding;
import com.oru.app.ui.components.UiErrors;

import java.util.ArrayList;
import java.util.List;

/**
 * Studio tab: releases, posts and my unreleased/pending songs for an artist.
 * Mirrors the web app studio page.
 */
public class StudioFragment extends Fragment {

    private FragmentStudioBinding binding;
    private StudioAdapter adapter;
    private MyReleaseAdapter releaseAdapter;
    private PostAdapter postAdapter;
    private MusicRepository musicRepo;

    private List<Song> mySongs = new ArrayList<>();
    private boolean songsEmpty = true;
    private boolean releasesEmpty = true;

    private final androidx.activity.result.ActivityResultLauncher<Intent> uploadLauncher =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts
                            .StartActivityForResult(),
                    result -> loadAll(false));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentStudioBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new StudioAdapter();
        adapter.setListener(new StudioAdapter.Listener() {
            @Override
            public void onPublish(Song song) {
                act(song, true);
            }

            @Override
            public void onUnpublish(Song song) {
                act(song, false);
            }

            @Override
            public void onDelete(Song song) {
                binding.progressBar.setVisibility(View.VISIBLE);
                musicRepo.deleteSong(song.id, new Callback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        loadAll(false);
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        binding.progressBar.setVisibility(View.GONE);
                        toast(error);
                    }
                });
            }
        });
        binding.recyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        releaseAdapter = new MyReleaseAdapter();
        releaseAdapter.setListener(release -> publishRelease(release));
        binding.releasesRecyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        binding.releasesRecyclerView.setAdapter(releaseAdapter);

        postAdapter = new PostAdapter();
        binding.postsRecyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        binding.postsRecyclerView.setAdapter(postAdapter);

        binding.uploadButton.setOnClickListener(v -> uploadLauncher.launch(
                new Intent(requireContext(), UploadActivity.class)));

        binding.toolbar.inflateMenu(R.menu.studio_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_new_release) {
                showReleaseDialog();
                return true;
            }
            if (item.getItemId() == R.id.action_new_post) {
                showPostDialog();
                return true;
            }
            return false;
        });

        binding.errorView.retryButton.setOnClickListener(v -> {
            binding.errorView.getRoot().setVisibility(View.GONE);
            loadAll(true);
        });

        if (musicRepo == null) {
            musicRepo = new MusicRepository(
                    com.oru.app.util.LazySession.getApiService(),
                    com.oru.app.util.LazySession.getGson());
        }

        loadAll(false);
    }

    /** Publishes or pulls a track back to draft. */
    private void act(Song song, boolean publish) {
        binding.progressBar.setVisibility(View.VISIBLE);
        Callback<Song> done = new Callback<Song>() {
            @Override
            public void onSuccess(Song updated) {
                loadAll(false);
            }

            @Override
            public void onFailure(Throwable error) {
                binding.progressBar.setVisibility(View.GONE);
                toast(error);
            }
        };
        if (publish) {
            musicRepo.publishSong(song.id, done);
        } else {
            musicRepo.unpublishSong(song.id, done);
        }
    }

    private void publishRelease(Release release) {
        binding.progressBar.setVisibility(View.VISIBLE);
        musicRepo.publishRelease(release.id, new Callback<Release>() {
            @Override
            public void onSuccess(Release updated) {
                loadAll(false);
            }

            @Override
            public void onFailure(Throwable error) {
                binding.progressBar.setVisibility(View.GONE);
                toast(error);
            }
        });
    }

    /**
     * Groups my songs into a release. The dialog asks for a title, the kind and
     * the songs, since all three are required by the API.
     */
    private void showReleaseDialog() {
        if (musicRepo == null) {
            return;
        }
        if (mySongs.isEmpty()) {
            toastMessage("Upload a song first.");
            return;
        }
        Context context = requireContext();
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * density);

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, 0);

        EditText title = new EditText(context);
        title.setHint("Release title");
        title.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(title);

        RadioGroup kinds = new RadioGroup(context);
        kinds.setOrientation(RadioGroup.HORIZONTAL);
        String[] labels = {"Single", "EP", "Album"};
        String[] values = {"single", "ep", "album"};
        for (int i = 0; i < labels.length; i++) {
            RadioButton option = new RadioButton(context);
            option.setId(View.generateViewId());
            option.setTag(values[i]);
            option.setText(labels[i]);
            option.setChecked(i == 0);
            kinds.addView(option);
        }
        box.addView(kinds);

        ScrollView scroller = new ScrollView(context);
        scroller.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (200 * density)));
        LinearLayout songsBox = new LinearLayout(context);
        songsBox.setOrientation(LinearLayout.VERTICAL);
        List<CheckBox> pickers = new ArrayList<>();
        for (Song song : mySongs) {
            CheckBox pick = new CheckBox(context);
            pick.setTag(song.id);
            pick.setText(song.title + (song.isPublished() ? "" : " · draft"));
            pickers.add(pick);
            songsBox.addView(pick);
        }
        scroller.addView(songsBox);
        box.addView(scroller);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("New release")
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Create", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(
                AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = title.getText().toString().trim();
            if (name.isEmpty()) {
                title.setError("Give it a title");
                return;
            }
            String kind = values[0];
            int limit = 1;
            for (int i = 0; i < kinds.getChildCount(); i++) {
                RadioButton option = (RadioButton) kinds.getChildAt(i);
                if (option.isChecked()) {
                    kind = String.valueOf(option.getTag());
                    limit = "single".equals(kind) ? 1 : "ep".equals(kind) ? 6 : 40;
                }
            }
            List<Integer> picked = new ArrayList<>();
            for (CheckBox pick : pickers) {
                if (pick.isChecked()) {
                    picked.add((Integer) pick.getTag());
                }
            }
            if (picked.isEmpty()) {
                toastMessage("Pick at least one song.");
                return;
            }
            if (picked.size() > limit) {
                toastMessage("A " + kind + " holds up to " + limit + " songs.");
                return;
            }
            dialog.dismiss();
            createRelease(name, kind, picked);
        }));
        dialog.show();
    }

    private void createRelease(String title, String kind, List<Integer> songIds) {
        binding.progressBar.setVisibility(View.VISIBLE);
        musicRepo.createRelease(title, kind, songIds, new Callback<Release>() {
            @Override
            public void onSuccess(Release release) {
                binding.progressBar.setVisibility(View.GONE);
                toastMessage("Release created. Publish it when you are ready.");
                loadAll(false);
            }

            @Override
            public void onFailure(Throwable error) {
                binding.progressBar.setVisibility(View.GONE);
                toast(error);
            }
        });
    }

    /** Writes a short post to my artist profile. */
    private void showPostDialog() {
        if (musicRepo == null) {
            return;
        }
        Context context = requireContext();
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * density);

        EditText body = new EditText(context);
        body.setHint("What do you want to say?");
        body.setMinLines(3);
        body.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        body.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout box = new LinearLayout(context);
        box.setPadding(pad, pad, pad, 0);
        box.addView(body);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("New post")
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Post", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(
                AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = body.getText().toString().trim();
            if (text.isEmpty()) {
                body.setError("Write something first");
                return;
            }
            dialog.dismiss();
            musicRepo.createPost(text, new Callback<ArtistPost>() {
                @Override
                public void onSuccess(ArtistPost post) {
                    toastMessage("Posted.");
                    loadAll(false);
                }

                @Override
                public void onFailure(Throwable error) {
                    toast(error);
                }
            });
        }));
        dialog.show();
    }

    private void loadAll(boolean refresh) {
        if (binding == null) {
            return;
        }
        binding.swipeRefresh.setRefreshing(refresh);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.errorView.getRoot().setVisibility(View.GONE);
        loadSongs();
        loadReleases();
        loadPosts();
        loadStats();
    }

    /** The artist's own numbers, shown above the lists. */
    private void loadStats() {
        musicRepo.getArtistStats(new Callback<com.oru.app.data.model.ArtistStats>() {
            @Override
            public void onSuccess(com.oru.app.data.model.ArtistStats stats) {
                if (binding == null || stats == null) {
                    return;
                }
                String top = stats.weeklyTopSong != null
                        && stats.weeklyTopSong.title != null
                                ? " · top: " + stats.weeklyTopSong.title
                                : "";
                binding.statsText.setText(stats.publishedSongs + " published · "
                        + stats.likes + " likes · " + stats.qualifiedListens
                        + " plays · " + stats.weeklyPlays + " plays this week"
                        + top);
                binding.statsText.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure(Throwable error) {
                if (binding == null) {
                    return;
                }
                binding.statsText.setVisibility(View.GONE);
            }
        });
    }

    private void loadSongs() {
        musicRepo.getMySongs(new Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> songs) {
                if (binding == null) {
                    return;
                }
                binding.swipeRefresh.setRefreshing(false);
                binding.progressBar.setVisibility(View.GONE);
                mySongs = songs != null ? songs : new ArrayList<>();
                songsEmpty = mySongs.isEmpty();
                if (!songsEmpty) {
                    adapter.submitList(mySongs);
                }
                binding.recyclerView.setVisibility(
                        songsEmpty ? View.GONE : View.VISIBLE);
                updateEmpty();
            }

            @Override
            public void onFailure(Throwable error) {
                if (binding == null) {
                    return;
                }
                binding.swipeRefresh.setRefreshing(false);
                binding.progressBar.setVisibility(View.GONE);
                // A listener has no studio yet; offer to open one instead of
                // showing a dead error screen.
                if (isArtistProfileMissing(error)) {
                    binding.errorView.getRoot().setVisibility(View.GONE);
                    askForArtistProfile();
                    return;
                }
                binding.errorView.getRoot().setVisibility(View.VISIBLE);
                binding.errorView.errorMessage.setText(
                        UiErrors.message(requireContext(), error));
            }
        });
    }

    private boolean isArtistProfileMissing(Throwable error) {
        if (!(error instanceof com.oru.app.data.model.ApiResponse.ApiException)) {
            return false;
        }
        com.oru.app.data.model.ApiResponse.ApiException apiError =
                (com.oru.app.data.model.ApiResponse.ApiException) error;
        String code = apiError.code;
        return apiError.statusCode == 403
                || "artist_only".equals(code)
                || "artist_profile_required".equals(code);
    }

    /** Asks for the stage name and opens the artist profile. */
    private void askForArtistProfile() {
        if (binding == null || getContext() == null) {
            return;
        }
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout form = new android.widget.LinearLayout(requireContext());
        form.setOrientation(android.widget.LinearLayout.VERTICAL);
        form.setPadding(pad, pad, pad, 0);

        android.widget.TextView help = new android.widget.TextView(requireContext());
        help.setText("Your songs appear under this name. You can change it later.");
        help.setTextAppearance(com.oru.app.R.style.TextAppearance_Oru_BodySoft);
        form.addView(help);

        final android.widget.EditText name = new android.widget.EditText(requireContext());
        name.setHint("Stage name");
        name.setSingleLine(true);
        form.addView(name);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Open your studio")
                .setView(form)
                .setNegativeButton("Not now", null)
                .setPositiveButton("Create", (dialog, which) -> {
                    String stageName = name.getText().toString().trim();
                    if (stageName.length() < 2) {
                        toast(new IllegalArgumentException(
                                "Use at least 2 characters for your stage name"));
                        return;
                    }
                    createArtistProfile(stageName);
                })
                .show();
    }

    private void createArtistProfile(String stageName) {
        binding.progressBar.setVisibility(View.VISIBLE);
        new com.oru.app.data.repository.AuthRepository(
                com.oru.app.util.LazySession.getApiService(),
                new com.oru.app.data.session.SessionManager(requireContext()),
                com.oru.app.util.LazySession.getGson())
                .createArtistProfile(stageName,
                        new Callback<com.oru.app.data.model.Artist>() {
                            @Override
                            public void onSuccess(
                                    com.oru.app.data.model.Artist artist) {
                                if (binding == null) {
                                    return;
                                }
                                binding.progressBar.setVisibility(View.GONE);
                                android.widget.Toast.makeText(requireContext(),
                                        "Your studio is open",
                                        android.widget.Toast.LENGTH_SHORT).show();
                                loadAll(true);
                            }

                            @Override
                            public void onFailure(Throwable error) {
                                if (binding == null) {
                                    return;
                                }
                                binding.progressBar.setVisibility(View.GONE);
                                toast(error);
                            }
                        });
    }

    private void loadReleases() {
        musicRepo.getMyReleases(new Callback<List<Release>>() {
            @Override
            public void onSuccess(List<Release> releases) {
                if (binding == null) {
                    return;
                }
                releasesEmpty = releases == null || releases.isEmpty();
                if (!releasesEmpty) {
                    releaseAdapter.submitList(releases);
                }
                binding.releasesRecyclerView.setVisibility(
                        releasesEmpty ? View.GONE : View.VISIBLE);
                binding.releasesEmptyText.setVisibility(
                        releasesEmpty ? View.VISIBLE : View.GONE);
                updateEmpty();
            }

            @Override
            public void onFailure(Throwable error) {
                if (binding == null) {
                    return;
                }
                binding.releasesEmptyText.setVisibility(View.VISIBLE);
            }
        });
    }

    private void loadPosts() {
        musicRepo.getMyPosts(new Callback<List<ArtistPost>>() {
            @Override
            public void onSuccess(List<ArtistPost> posts) {
                if (binding == null) {
                    return;
                }
                boolean empty = posts == null || posts.isEmpty();
                if (!empty) {
                    postAdapter.submitList(posts);
                }
                binding.postsRecyclerView.setVisibility(
                        empty ? View.GONE : View.VISIBLE);
                binding.postsEmptyText.setVisibility(
                        empty ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(Throwable error) {
                if (binding == null) {
                    return;
                }
                binding.postsEmptyText.setVisibility(View.VISIBLE);
            }
        });
    }

    private void updateEmpty() {
        if (binding == null) {
            return;
        }
        binding.emptyView.getRoot().setVisibility(
                songsEmpty && releasesEmpty ? View.VISIBLE : View.GONE);
    }

    private void toast(Throwable error) {
        toastMessage(UiErrors.message(requireContext(), error));
    }

    private void toastMessage(String message) {
        if (binding == null) {
            return;
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
