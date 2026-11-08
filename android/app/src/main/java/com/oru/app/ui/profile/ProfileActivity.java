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
import com.oru.app.data.model.Playlist;
import com.oru.app.data.model.Song;
import com.oru.app.data.model.User;
import com.oru.app.data.repository.AuthRepository;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.CommunityRepository;
import com.oru.app.data.session.SessionManager;
import com.oru.app.databinding.ActivityProfileBinding;
import com.oru.app.ui.components.Covers;
import com.oru.app.ui.components.ProfileEditor;
import com.oru.app.ui.components.UiErrors;
import com.oru.app.util.LazySession;
import com.oru.app.util.PickedFile;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The account behind the home screen's gear: a banner anyone can change, a face,
 * three numbers that describe a listener, and the doors that matter.
 */
public class ProfileActivity extends AppCompatActivity {

    private static final String IMAGE_MIME = "image/*";

    private ActivityProfileBinding binding;
    private AuthRepository authRepo;
    private CommunityRepository communityRepo;
    private SessionManager session;
    private User user;
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
        binding.settingsButton.setOnClickListener(v -> openSettings());
        binding.settingsRow.setOnClickListener(v -> openSettings());
        binding.bannerTapTarget.setOnClickListener(v -> pickBanner.launch(IMAGE_MIME));
        binding.changeBannerButton.setOnClickListener(v -> pickBanner.launch(IMAGE_MIME));
        binding.editAvatarButton.setOnClickListener(v -> pickAvatar.launch(IMAGE_MIME));
        binding.avatarImage.setOnClickListener(v -> pickAvatar.launch(IMAGE_MIME));
        binding.editProfileButton.setOnClickListener(v -> ProfileEditor.show(this,
                user, authRepo, this::onProfileSaved));
        binding.studioRow.setOnClickListener(v -> openStudio());
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

        binding.joinedText.setText(joinedLine(account.createdAt));

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

    /** Three counts, each shown as soon as it arrives. */
    private void loadStats() {
        communityRepo.getMyLikes(new Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> songs) {
                setStat(binding.statLikes, songs != null ? songs.size() : 0);
            }

            @Override
            public void onFailure(Throwable error) {
                // The number is decoration; the page works without it.
            }
        });
        communityRepo.listPlaylists(new Callback<List<Playlist>>() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                setStat(binding.statPlaylists,
                        playlists != null ? playlists.size() : 0);
            }

            @Override
            public void onFailure(Throwable error) {
            }
        });
        communityRepo.getFollowing(new Callback<List<Artist>>() {
            @Override
            public void onSuccess(List<Artist> artists) {
                setStat(binding.statFollowing,
                        artists != null ? artists.size() : 0);
            }

            @Override
            public void onFailure(Throwable error) {
            }
        });
    }

    private void setStat(android.widget.TextView view, int value) {
        if (binding != null) {
            view.setText(String.valueOf(value));
        }
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

    /** "Listening since March 2026", from the account's creation date. */
    private static String joinedLine(String createdAt) {
        if (createdAt == null || createdAt.length() < 10) {
            return "";
        }
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = in.parse(createdAt.substring(0, 10));
            if (date == null) {
                return "";
            }
            SimpleDateFormat out = new SimpleDateFormat("MMMM yyyy", Locale.US);
            return "Listening since " + out.format(date);
        } catch (java.text.ParseException error) {
            return "";
        }
    }

    private void showError(String message) {
        binding.errorText.setText(message);
        binding.errorText.setVisibility(View.VISIBLE);
    }

    private void openSettings() {
        startActivity(new Intent(this,
                com.oru.app.ui.settings.SettingsActivity.class));
    }

    private void openStudio() {
        Intent intent = new Intent(this, com.oru.app.ui.MainActivity.class);
        intent.putExtra("tab", R.id.studioFragment);
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
        io.shutdown();
        binding = null;
    }
}
