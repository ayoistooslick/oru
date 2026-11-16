package com.oru.app.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.oru.app.R;
import com.oru.app.data.model.Artist;
import com.oru.app.data.model.User;
import com.oru.app.data.repository.AuthRepository;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.CommunityRepository;
import com.oru.app.data.session.SessionManager;
import com.oru.app.databinding.ActivityProfileBinding;
import com.oru.app.ui.components.BottomNav;
import com.oru.app.ui.components.Covers;
import com.oru.app.ui.components.ProfileEditor;
import com.oru.app.ui.components.UiErrors;
import com.oru.app.util.LazySession;
import com.oru.app.util.PickedFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The account behind the header's avatar: a short cover, a face, the two
 * numbers that describe an account, and the doors that matter. The join date is
 * still recorded on the server; it is simply not worth the space here.
 */
public class ProfileActivity extends AppCompatActivity {

    private static final String IMAGE_MIME = "image/*";

    private ActivityProfileBinding binding;
    private AuthRepository authRepo;
    private CommunityRepository communityRepo;
    private SessionManager session;
    private User user;
    private com.oru.app.ui.components.MiniPlayer miniPlayer;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    /** Both pictures use the system picker, so no storage permission is needed. */
    private final ActivityResultLauncher<String> pickBanner =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> upload(uri, false));

    private final ActivityResultLauncher<String> pickAvatar =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> upload(uri, true));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        session = new SessionManager(this);
        authRepo = new AuthRepository(LazySession.getApiService(), session,
                LazySession.getGson());
        communityRepo = new CommunityRepository(LazySession.getApiService(),
                LazySession.getGson());

        binding.backButton.setOnClickListener(v -> finish());
        binding.shareButton.setOnClickListener(v -> share());
        binding.settingsButton.setOnClickListener(v -> openSettings());
        binding.settingsRow.setOnClickListener(v -> openSettings());
        binding.bannerTapTarget.setOnClickListener(v -> pickBanner.launch(IMAGE_MIME));
        binding.changeBannerButton.setOnClickListener(v -> pickBanner.launch(IMAGE_MIME));
        binding.editAvatarButton.setOnClickListener(v -> pickAvatar.launch(IMAGE_MIME));
        binding.avatarImage.setOnClickListener(v -> pickAvatar.launch(IMAGE_MIME));
        binding.editProfileButton.setOnClickListener(v -> ProfileEditor.show(this,
                user, authRepo, this::onProfileSaved));
        binding.studioRow.setOnClickListener(v -> openStudio());
        binding.dropsRow.setOnClickListener(v -> openTab(R.id.sharesFragment));
        BottomNav.attach(binding.profileNav, this);
        miniPlayer = new com.oru.app.ui.components.MiniPlayer(this,
                binding.miniPlayer);
        binding.artistPageRow.setOnClickListener(v -> {
            if (user != null) {
                com.oru.app.ui.components.Actions.openArtist(this, user.id);
            }
        });
        binding.logOutRow.setOnClickListener(v -> logOut());

        // The saved session already knows the name and face; show those while
        // the full profile loads.
        bindName(session.getDisplayName(), session.getHandle());
        Covers.loadAvatar(binding.avatarImage, session.getAvatarUrl());
        load();
    }

    private void load() {
        authRepo.getCurrentUser(new Callback<User>() {
            @Override
            public void onSuccess(User loaded) {
                if (binding == null) {
                    return;
                }
                binding.progressBar.setVisibility(View.GONE);
                if (loaded == null) {
                    showError("Your profile came back empty");
                    return;
                }
                user = loaded;
                bind(loaded);
            }

            @Override
            public void onFailure(Throwable error) {
                if (binding == null) {
                    return;
                }
                binding.progressBar.setVisibility(View.GONE);
                showError(UiErrors.message(ProfileActivity.this, error));
            }
        });
    }

    private void bind(User account) {
        bindName(account.displayName, account.handle);
        Covers.loadVersionedAvatar(binding.avatarImage, account.avatarUrl,
                account.imageVersion);
        // A banner looks like a cover in the layout, so it fills the space; with
        // no picture the paper tint underneath keeps the header calm.
        Covers.loadVersioned(binding.bannerImage, account.artistBannerUrl(),
                account.imageVersion, R.drawable.ic_image);
        binding.changeBannerButton.setText(
                account.artistBannerUrl() == null ? "Add a cover" : "Change cover");

        String bio = account.bio != null ? account.bio.trim() : "";
        binding.bioText.setText(bio);
        binding.bioText.setVisibility(bio.isEmpty() ? View.GONE : View.VISIBLE);

        String role = roleLabel(account.role);
        binding.roleText.setText(role);
        binding.roleText.setVisibility(role.isEmpty() ? View.GONE : View.VISIBLE);

        boolean artist = account.hasArtistProfile();
        binding.artistPageRow.setVisibility(artist ? View.VISIBLE : View.GONE);
        String stageName = account.artistStageName();
        binding.artistNote.setText(stageName != null && !stageName.trim().isEmpty()
                ? "Publishing as " + stageName
                : "The page listeners see");
        binding.studioSummary.setText(artist
                ? "Songs, releases and posts"
                : "Upload your own songs and publish them");

        loadStats();
    }

    /**
     * One line instead of three tiles: who this account follows, and — for an
     * artist — who follows back. A listener with no followers yet is not shown a
     * zero that means nothing.
     */
    private void loadStats() {
        final int[] following = {-1};
        final int[] followers = {-1};
        communityRepo.getFollowing(new Callback<List<Artist>>() {
            @Override
            public void onSuccess(List<Artist> artists) {
                following[0] = artists != null ? artists.size() : 0;
                renderFollowLine(following[0], followers[0]);
            }

            @Override
            public void onFailure(Throwable error) {
                renderFollowLine(following[0], followers[0]);
            }
        });
        if (user != null && user.hasArtistProfile()) {
            new com.oru.app.data.repository.MusicRepository(
                    LazySession.getApiService(), LazySession.getGson())
                    .getArtist(user.id, new Callback<Artist>() {
                        @Override
                        public void onSuccess(Artist artist) {
                            followers[0] = artist != null ? artist.followerCount : 0;
                            renderFollowLine(following[0], followers[0]);
                        }

                        @Override
                        public void onFailure(Throwable error) {
                            renderFollowLine(following[0], followers[0]);
                        }
                    });
        }
    }

    private void renderFollowLine(int following, int followers) {
        if (binding == null) {
            return;
        }
        StringBuilder line = new StringBuilder();
        if (following >= 0) {
            line.append("Following ").append(following);
        }
        if (followers >= 0) {
            if (line.length() > 0) {
                line.append(" · ");
            }
            line.append(followers).append(followers == 1 ? " follower" : " followers");
        }
        binding.followLine.setText(line);
        binding.followLine.setVisibility(line.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void onProfileSaved(User updated) {
        user = updated;
        session.updateIdentity(updated.displayName, updated.avatarUrl);
        bind(updated);
    }

    private void upload(Uri uri, boolean avatar) {
        if (uri == null) {
            return;
        }
        Toast.makeText(this,
                avatar ? "Uploading picture…" : "Uploading cover…",
                Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                PickedFile picked = PickedFile.copyImage(this, uri);
                Callback<User> done = new Callback<User>() {
                    @Override
                    public void onSuccess(User updated) {
                        runOnUiThread(() -> {
                            if (binding == null || updated == null) {
                                return;
                            }
                            user = updated;
                            session.updateIdentity(updated.displayName,
                                    updated.avatarUrl);
                            bind(updated);
                            Toast.makeText(ProfileActivity.this,
                                    avatar ? "Picture updated" : "Cover updated",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        runOnUiThread(() -> Toast.makeText(ProfileActivity.this,
                                UiErrors.message(ProfileActivity.this, error),
                                Toast.LENGTH_LONG).show());
                    }
                };
                if (avatar) {
                    authRepo.uploadAvatar(picked.file, picked.mime, done);
                } else {
                    authRepo.uploadBanner(picked.file, picked.mime, done);
                }
            } catch (IOException | RuntimeException error) {
                // Say what actually went wrong; "couldn't read" hid the cause.
                String reason = error.getMessage();
                runOnUiThread(() -> Toast.makeText(this,
                        reason != null && !reason.isEmpty() ? reason
                                : "Couldn't read that picture",
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void bindName(String name, String handle) {
        String shown = name != null && !name.trim().isEmpty() ? name : "Your profile";
        binding.displayName.setText(shown);
        StringBuilder meta = new StringBuilder();
        if (handle != null && !handle.isEmpty()) {
            meta.append("@").append(handle);
        }
        if (user != null && user.role != null && !user.role.isEmpty()) {
            meta.append(meta.length() > 0 ? " · " : "").append(roleLabel(user.role));
        }
        binding.handleText.setText(meta);
    }

    private static String roleLabel(String role) {
        if ("admin".equals(role)) {
            return "Moderator";
        }
        if ("artist".equals(role)) {
            return "Artist";
        }
        return "";
    }

    private void showError(String message) {
        binding.errorText.setText(message);
        binding.errorText.setVisibility(View.VISIBLE);
    }

    /** Shares the artist page when there is one, so the link has a face. */
    private void share() {
        boolean artist = user != null && user.hasArtistProfile();
        String url = artist
                ? "https://oru.freebuff.app/artist/" + user.id
                : "https://oru.freebuff.app";
        com.oru.app.ui.components.Actions.share(this,
                artist ? user.displayName + " on ORU" : "ORU", url);
    }

    private void openSettings() {
        startActivity(new Intent(this,
                com.oru.app.ui.settings.SettingsActivity.class));
    }

    private void openStudio() {
        openTab(R.id.studioFragment);
    }

    /** Hands a tab back to the one activity that owns the navigation. */
    private void openTab(int destination) {
        Intent intent = new Intent(this, com.oru.app.ui.MainActivity.class);
        intent.putExtra("tab", destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void logOut() {
        session.clearSession();
        Intent intent = new Intent(this, com.oru.app.ui.auth.AuthActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (miniPlayer != null) {
            miniPlayer.release();
            miniPlayer = null;
        }
        io.shutdown();
        binding = null;
    }
}
