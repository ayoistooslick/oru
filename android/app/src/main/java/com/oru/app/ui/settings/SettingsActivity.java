package com.oru.app.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.oru.app.R;
import com.oru.app.data.model.Artist;
import com.oru.app.data.model.User;
import com.oru.app.data.repository.AuthRepository;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.session.SessionManager;
import com.oru.app.databinding.ActivitySettingsBinding;
import com.oru.app.ui.about.AboutActivity;
import com.oru.app.ui.auth.AuthActivity;
import com.oru.app.ui.components.UiErrors;
import com.oru.app.util.LazySession;
import com.oru.app.util.MediaUrl;
import com.oru.app.util.PickedFile;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Account settings: picture, banner, profile details and signing out. */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private AuthRepository authRepo;
    private SessionManager session;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private User user;

    private final ActivityResultLauncher<String> pickAvatar =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> uploadImage(uri, true));

    private final ActivityResultLauncher<String> pickBanner =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> uploadImage(uri, false));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        session = new SessionManager(this);
        authRepo = new AuthRepository(LazySession.getApiService(), session,
                LazySession.getGson());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.editProfileButton.setOnClickListener(v -> editProfile());
        binding.editAvatarButton.setOnClickListener(v -> pickAvatar.launch("image/*"));
        binding.bannerButton.setOnClickListener(v -> pickBanner.launch("image/*"));
        binding.aboutButton.setOnClickListener(v -> startActivity(
                new Intent(this, AboutActivity.class)));
        binding.signOutButton.setOnClickListener(v -> signOut());

        binding.toolbar.inflateMenu(R.menu.settings_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
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
                    showAvatar();
                    binding.bannerButton.setVisibility(
                            current.isArtist() ? View.VISIBLE : View.GONE);
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

    private void showAvatar() {
        String avatar = MediaUrl.of(user != null ? user.avatarUrl : null);
        if (avatar != null) {
            com.bumptech.glide.Glide.with(this)
                    .load(avatar)
                    .placeholder(R.drawable.ic_artist)
                    .into(binding.avatarImage);
        } else {
            binding.avatarImage.setImageResource(R.drawable.ic_artist);
        }
    }

    private void removePicture() {
        authRepo.deleteAvatar(new Callback<User>() {
            @Override
            public void onSuccess(User updated) {
                runOnUiThread(() -> {
                    user = updated;
                    showAvatar();
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
        authRepo.deleteBanner(new Callback<Artist>() {
            @Override
            public void onSuccess(Artist updated) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                        "Banner removed", Toast.LENGTH_SHORT).show());
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
                PickedFile picked = PickedFile.copy(this, uri);
                if (avatar) {
                    authRepo.uploadAvatar(picked.file, picked.mime,
                            new Callback<User>() {
                                @Override
                                public void onSuccess(User updated) {
                                    runOnUiThread(() -> {
                                        user = updated;
                                        showAvatar();
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
                            new Callback<Artist>() {
                                @Override
                                public void onSuccess(Artist updated) {
                                    runOnUiThread(() -> Toast.makeText(
                                            SettingsActivity.this,
                                            "Banner updated",
                                            Toast.LENGTH_SHORT).show());
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
                runOnUiThread(() -> Toast.makeText(this,
                        "Couldn't read that picture", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void editProfile() {
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(pad, pad, pad, 0);

        final EditText name = new EditText(this);
        name.setHint("Display name");
        name.setSingleLine(true);
        if (user != null && user.displayName != null) {
            name.setText(user.displayName);
        }
        form.addView(name);

        final EditText bio = new EditText(this);
        bio.setHint("Bio");
        bio.setMinLines(2);
        bio.setMaxLines(4);
        if (user != null && user.bio != null) {
            bio.setText(user.bio);
        }
        form.addView(bio);

        new AlertDialog.Builder(this)
                .setTitle("Edit profile")
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> saveProfile(
                        name.getText().toString().trim(),
                        bio.getText().toString().trim()))
                .show();
    }

    private void saveProfile(String displayName, String bio) {
        authRepo.updateProfile(displayName, bio, new Callback<User>() {
            @Override
            public void onSuccess(User updated) {
                runOnUiThread(() -> {
                    user = updated;
                    showAvatar();
                    Toast.makeText(SettingsActivity.this, "Profile saved",
                            Toast.LENGTH_SHORT).show();
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
