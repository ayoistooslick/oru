package com.oru.app.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.oru.app.R;
import com.oru.app.data.model.NotificationPreference;
import com.oru.app.data.model.User;
import com.oru.app.data.repository.AuthRepository;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.NotificationsRepository;
import com.oru.app.data.session.SessionManager;
import com.oru.app.databinding.ActivitySettingsBinding;
import com.oru.app.ui.about.AboutActivity;
import com.oru.app.ui.auth.AuthActivity;
import com.oru.app.ui.components.UiErrors;
import com.oru.app.util.LazySession;
import com.oru.app.util.PickedFile;
import com.oru.app.util.ThemeMode;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Account settings: picture, banner, profile details and signing out. */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private AuthRepository authRepo;
    private NotificationsRepository notificationsRepo;
    private SessionManager session;
    /** Set while the switch is being filled in, so that is not a tap. */
    private boolean bindingDrops;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private User user;

    /**
     * The system document picker hands back a readable URI, so no storage
     * permission is needed and the app never asks for one.
     */
    private final ActivityResultLauncher<String> pickAvatar =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> uploadImage(uri, true));

    private final ActivityResultLauncher<String> pickBanner =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> uploadImage(uri, false));

    private static final String IMAGE_MIME = "image/*";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        session = new SessionManager(this);
        authRepo = new AuthRepository(LazySession.getApiService(), session,
                LazySession.getGson());
        notificationsRepo = new NotificationsRepository(
                LazySession.getApiService(), LazySession.getGson());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        bindTheme();
        binding.editProfileButton.setOnClickListener(v -> editProfile());
        binding.studioButton.setOnClickListener(v -> openTab(R.id.studioFragment));
        binding.reportsButton.setOnClickListener(v -> startActivity(
                new Intent(this, com.oru.app.ui.moderation.ModerationActivity.class)));
        binding.downloadsButton.setOnClickListener(v -> startActivity(
                new Intent(this, com.oru.app.ui.downloads.DownloadsActivity.class)));
        binding.removePictureButton.setOnClickListener(v -> removePicture());
        binding.removeBannerButton.setOnClickListener(v -> removeBanner());
        binding.permissionButton.setOnClickListener(v -> openAppSettings());
        binding.versionText.setText("Oru " + versionName());
        binding.editAvatarButton.setOnClickListener(v -> pickAvatar.launch(IMAGE_MIME));
        binding.bannerButton.setOnClickListener(v -> pickBanner.launch(IMAGE_MIME));
        binding.aboutButton.setOnClickListener(v -> startActivity(
                new Intent(this, AboutActivity.class)));
        binding.githubButton.setOnClickListener(v -> openDeveloper());
        binding.signOutButton.setOnClickListener(v -> signOut());
        binding.dropNotificationsSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingDrops) {
                return;
            }
            saveDropPreference(checked);
        });

        binding.toolbar.inflateMenu(R.menu.settings_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_view_artist) {
                if (user != null) {
                    com.oru.app.ui.components.Actions.openArtist(this, user.id);
                }
                return true;
            }
            if (item.getItemId() == R.id.action_remove_picture) {
                removePicture();
                return true;
            }
            if (item.getItemId() == R.id.action_remove_banner) {
                removeBanner();
                return true;
            }
            return false;
        });

        loadUser();
    }

    private void loadUser() {
        authRepo.getCurrentUser(new Callback<User>() {
            @Override
            public void onSuccess(User current) {
                runOnUiThread(() -> {
                    user = current;
                    showIdentity();
                });
            }

            @Override
            public void onFailure(Throwable error) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                        UiErrors.message(SettingsActivity.this, error),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    /** Everything the account card and its sections show. */
    private void showIdentity() {
        if (binding == null) {
            return;
        }
        boolean signedIn = user != null;
        android.view.MenuItem artistPage = binding.toolbar.getMenu()
                .findItem(R.id.action_view_artist);
        if (artistPage != null) {
            artistPage.setVisible(signedIn && user.hasArtistProfile());
        }
        binding.accountName.setText(signedIn
                ? (user.displayName != null ? user.displayName : user.handle)
                : "");
        bindingDrops = true;
        binding.dropNotificationsSwitch.setChecked(!signedIn || user.notifyNewDrops);
        binding.dropNotificationsSwitch.setEnabled(signedIn);
        bindingDrops = false;
        StringBuilder meta = new StringBuilder();
        if (signedIn) {
            if (user.handle != null) {
                meta.append('@').append(user.handle);
            }
            String role = roleLabel(user.role);
            if (!role.isEmpty()) {
                if (meta.length() > 0) {
                    meta.append(" · ");
                }
                meta.append(role);
            }
        }
        binding.accountMeta.setText(meta);
        binding.accountEmail.setText(signedIn && user.email != null
                ? user.email
                : "");
        boolean hasBio = signedIn && user.bio != null && !user.bio.trim().isEmpty();
        binding.accountBio.setText(hasBio ? user.bio.trim() : "");
        binding.accountBio.setVisibility(hasBio ? View.VISIBLE : View.GONE);

        showAvatar();

        boolean artist = signedIn && user.isArtist();
        binding.studioSummary.setText(artist
                ? "Upload songs, publish them and make releases."
                : "Upload your own songs and publish them.");
        // A banner is not an artist privilege any more: the wide picture sits
        // behind every profile.
        binding.bannerCard.setVisibility(View.VISIBLE);
        com.oru.app.ui.components.Covers.loadVersioned(binding.bannerImage,
                user != null ? user.artistBannerUrl() : null,
                user != null ? user.imageVersion : 0, R.drawable.ic_image);
        binding.bannerButton.setText(hasBanner(user) ? "Change banner" : "Add a banner");
        binding.removeBannerButton.setVisibility(hasBanner(user)
                ? View.VISIBLE
                : View.GONE);

        binding.reportsButton.setVisibility("admin".equals(user != null
                ? user.role : null) ? View.VISIBLE : View.GONE);
        binding.removePictureButton.setVisibility(signedIn
                && user.avatarUrl != null ? View.VISIBLE : View.GONE);
        binding.permissionSummary.setText(notificationsAllowed()
                ? "Notifications are on, so playback controls show up."
                : "Turn on notifications to see playback controls.");
    }

    private boolean hasBanner(User user) {
        return user != null && user.artistBannerUrl() != null;
    }

    private static String roleLabel(String role) {
        if ("admin".equals(role)) {
            return "Moderator";
        }
        if ("artist".equals(role)) {
            return "Artist";
        }
        return role == null ? "" : "Listener";
    }

    private boolean notificationsAllowed() {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            return true;
        }
        return androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Android hides the notification switch deep in settings; take them there. */
    /**
     * The same switch the web has: following is the opt-in, this only silences
     * future notices, and old ones stay under the bell. A failed save puts the
     * switch back where it was rather than lying about the account.
     */
    private void saveDropPreference(boolean enabled) {
        if (user != null) {
            user.notifyNewDrops = enabled;
        }
        binding.dropNotificationsSwitch.setEnabled(false);
        notificationsRepo.setDropsEnabled(enabled, new Callback<NotificationPreference>() {
            @Override
            public void onSuccess(NotificationPreference saved) {
                runOnUiThread(() -> {
                    if (binding == null) return;
                    binding.dropNotificationsSwitch.setEnabled(true);
                    if (user != null && saved != null) {
                        user.notifyNewDrops = saved.notifyNewDrops;
                    }
                });
            }

            @Override
            public void onFailure(Throwable error) {
                runOnUiThread(() -> {
                    if (binding == null) return;
                    bindingDrops = true;
                    binding.dropNotificationsSwitch.setChecked(!enabled);
                    bindingDrops = false;
                    binding.dropNotificationsSwitch.setEnabled(true);
                    if (user != null) {
                        user.notifyNewDrops = !enabled;
                    }
                    Toast.makeText(SettingsActivity.this,
                            UiErrors.message(SettingsActivity.this, error),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void openAppSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException error) {
            startActivity(new Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())));
        }
    }

    /** Three choices, remembered; System is the default and follows the phone. */
    private void bindTheme() {
        String mode = ThemeMode.get(this);
        int checked = R.id.themeSystem;
        if (ThemeMode.LIGHT.equals(mode)) {
            checked = R.id.themeLight;
        } else if (ThemeMode.DARK.equals(mode)) {
            checked = R.id.themeDark;
        }
        binding.themeChipGroup.check(checked);
        binding.themeChipGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) {
                return;
            }
            int id = ids.get(0);
            String next = id == R.id.themeLight ? ThemeMode.LIGHT
                    : id == R.id.themeDark ? ThemeMode.DARK : ThemeMode.SYSTEM;
            if (!next.equals(ThemeMode.get(this))) {
                ThemeMode.set(this, next);
            }
        });
    }

    /** The same door the web footer has, straight to the developer's page. */
    private void openDeveloper() {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/ayoistooslick"));
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException error) {
            Toast.makeText(this, "No browser to open that link",
                    Toast.LENGTH_LONG).show();
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0)
                    .versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            return "1.0";
        }
    }

    private void showAvatar() {
        if (binding == null) {
            return;
        }
        com.oru.app.ui.components.Covers.loadAvatar(binding.avatarImage,
                user != null ? user.avatarUrl : null);
    }

    private void removePicture() {
        authRepo.deleteAvatar(new Callback<User>() {
            @Override
            public void onSuccess(User updated) {
                runOnUiThread(() -> {
                    user = updated;
                    showIdentity();
                    Toast.makeText(SettingsActivity.this,
                            "Picture removed", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(Throwable error) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                        UiErrors.message(SettingsActivity.this, error),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void removeBanner() {
        authRepo.deleteBanner(new Callback<User>() {
            @Override
            public void onSuccess(User updated) {
                runOnUiThread(() -> {
                    user = updated;
                    showIdentity();
                    Toast.makeText(SettingsActivity.this,
                            "Banner removed", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(Throwable error) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                        UiErrors.message(SettingsActivity.this, error),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void uploadImage(Uri uri, boolean avatar) {
        if (uri == null) {
            return;
        }
        Toast.makeText(this, avatar ? "Uploading picture…" : "Uploading banner…",
                Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                PickedFile picked = PickedFile.copyImage(this, uri);
                if (avatar) {
                    authRepo.uploadAvatar(picked.file, picked.mime,
                            new Callback<User>() {
                                @Override
                                public void onSuccess(User updated) {
                                    runOnUiThread(() -> {
                                        user = updated;
                                        session.updateIdentity(updated.displayName,
                                                updated.avatarUrl);
                                        showIdentity();
                                        Toast.makeText(SettingsActivity.this,
                                                "Picture updated",
                                                Toast.LENGTH_SHORT).show();
                                    });
                                }

                                @Override
                                public void onFailure(Throwable error) {
                                    runOnUiThread(() -> Toast.makeText(
                                            SettingsActivity.this,
                                            UiErrors.message(SettingsActivity.this,
                                                    error),
                                            Toast.LENGTH_LONG).show());
                                }
                            });
                } else {
                    authRepo.uploadBanner(picked.file, picked.mime,
                            new Callback<User>() {
                                @Override
                                public void onSuccess(User updated) {
                                    runOnUiThread(() -> {
                                        user = updated;
                                        session.updateIdentity(updated.displayName,
                                                updated.avatarUrl);
                                        showIdentity();
                                        Toast.makeText(SettingsActivity.this,
                                                "Banner updated",
                                                Toast.LENGTH_SHORT).show();
                                    });
                                }

                                @Override
                                public void onFailure(Throwable error) {
                                    runOnUiThread(() -> Toast.makeText(
                                            SettingsActivity.this,
                                            UiErrors.message(SettingsActivity.this,
                                                    error),
                                            Toast.LENGTH_LONG).show());
                                }
                            });
                }
            } catch (IOException | RuntimeException error) {
                // Say what actually went wrong; "couldn't read" hid the cause.
                String reason = error.getMessage();
                runOnUiThread(() -> Toast.makeText(this,
                        reason != null && !reason.isEmpty()
                                ? reason
                                : "Couldn't read that picture",
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void editProfile() {
        com.oru.app.ui.components.ProfileEditor.show(this, user, authRepo,
                updated -> {
                    user = updated;
                    session.updateIdentity(updated.displayName,
                            updated.avatarUrl);
                    showIdentity();
                });
    }

    /** Brings the main screen forward on one of its tabs. */
    private void openTab(int tabId) {
        Intent intent = new Intent(this, com.oru.app.ui.MainActivity.class);
        intent.putExtra("tab", tabId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void signOut() {
        session.clearSession();
        Intent intent = new Intent(this, AuthActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
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
