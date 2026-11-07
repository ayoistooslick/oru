package com.oru.app.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.oru.app.R;
import com.oru.app.data.model.User;
import com.oru.app.data.repository.AuthRepository;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.session.SessionManager;
import com.oru.app.databinding.ActivityProfileBinding;
import com.oru.app.ui.components.Covers;
import com.oru.app.ui.components.UiErrors;
import com.oru.app.util.LazySession;

/**
 * The account behind the home screen's gear: a face, a handle, and the three
 * doors that matter (artist page, studio, settings). Mirrors the web profile.
 */
public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private AuthRepository authRepo;
    private SessionManager session;
    private User user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        session = new SessionManager(this);
        authRepo = new AuthRepository(LazySession.getApiService(), session,
                LazySession.getGson());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.settingsRow.setOnClickListener(v -> openSettings());
        binding.editRow.setOnClickListener(v -> openSettings());
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
                    binding.errorText.setText("Your profile came back empty");
                    binding.errorText.setVisibility(View.VISIBLE);
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
                binding.errorText.setText(UiErrors.message(ProfileActivity.this, error));
                binding.errorText.setVisibility(View.VISIBLE);
            }
        });
    }

    private void bind(User account) {
        bindName(account.displayName, account.handle);
        Covers.loadAvatar(binding.avatarImage, account.avatarUrl);
        Covers.load(binding.bannerImage, account.artistBannerUrl(), R.drawable.ic_music);

        String bio = account.bio != null ? account.bio.trim() : "";
        binding.bioText.setText(bio);
        binding.bioText.setVisibility(bio.isEmpty() ? View.GONE : View.VISIBLE);

        boolean artist = account.hasArtistProfile();
        binding.artistPageRow.setVisibility(artist ? View.VISIBLE : View.GONE);
        binding.artistNote.setVisibility(artist ? View.VISIBLE : View.GONE);
        String stageName = account.artistStageName();
        if (stageName != null && !stageName.trim().isEmpty()) {
            binding.artistNote.setText("Publishing as " + stageName
                    + ". Your songs, releases and posts live in the studio.");
        }
    }

    private void bindName(String name, String handle) {
        String shown = name != null && !name.trim().isEmpty() ? name : "Your profile";
        binding.displayName.setText(shown);
        StringBuilder meta = new StringBuilder();
        if (handle != null && !handle.isEmpty()) {
            meta.append("@").append(handle);
        }
        if (user != null && user.role != null && !user.role.isEmpty()) {
            meta.append(meta.length() > 0 ? " · " : "").append(user.role);
        }
        binding.handleText.setText(meta);
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
        binding = null;
    }
}
