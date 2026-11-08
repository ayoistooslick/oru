package com.oru.app.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.oru.app.R;
import com.oru.app.data.session.SessionManager;
import com.oru.app.databinding.ActivityMainBinding;
import com.oru.app.ui.search.SearchFragment;
import com.oru.app.ui.library.LibraryFragment;
import com.oru.app.ui.shares.SharesFragment;
import com.oru.app.ui.studio.StudioFragment;
import com.oru.app.ui.home.HomeFragment;

/**
 * Main activity: toolbar + bottom navigation + NavHost.
 * The five tab destinations match the web app shell.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private boolean keyboardVisible;
    private boolean mainDestination = true;
    private com.oru.app.data.model.User account;

    private com.oru.app.ui.components.MiniPlayer miniPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Auth gate: if not signed in, go to the auth activity.
        SessionManager session = new SessionManager(this);
        if (!session.isSignedIn()) {
            Intent intent = new Intent(this, com.oru.app.ui.auth.AuthActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            updateBottomNavigationVisibility();
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());

        setupBottomNavigation();
        miniPlayer = new com.oru.app.ui.components.MiniPlayer(this, binding.miniPlayer);
        askForNotifications();
        openRequestedTab(getIntent());
    }

    /** Android 13+ hides the media notification until this is granted. */
    private void askForNotifications() {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 41);
    }

    @Override
    protected void onDestroy() {
        if (miniPlayer != null) {
            miniPlayer.release();
            miniPlayer = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openRequestedTab(intent);
    }

    /** Another screen can ask for a tab, e.g. Settings opening the studio. */
    private void openRequestedTab(Intent intent) {
        if (intent == null || navController == null) {
            return;
        }
        int tab = intent.getIntExtra("tab", 0);
        if (tab != 0) {
            navController.navigate(tab);
        }
    }

    private void setupBottomNavigation() {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (host == null) {
            // Fallback if layout missing: create programmatically.
            return;
        }
        navController = host.getNavController();

        BottomNavigationView bottomNav = binding.bottomNavigation;
        NavigationUI.setupWithNavController(bottomNav, navController);

        // Preserve the selected tab when returning from sub-activities.
        navController.addOnDestinationChangedListener(
                (controller, destination, arguments) -> {
                    mainDestination = destination.getId() == R.id.homeFragment
                            || destination.getId() == R.id.searchFragment
                            || destination.getId() == R.id.libraryFragment
                            || destination.getId() == R.id.sharesFragment
                            || destination.getId() == R.id.studioFragment;
                    updateBottomNavigationVisibility();
                });
    }

    private void updateBottomNavigationVisibility() {
        if (binding != null) {
            binding.bottomNavigation.setVisibility(
                    mainDestination && !keyboardVisible
                            ? android.view.View.VISIBLE
                            : android.view.View.GONE);
        }
    }

    /**
     * The account menu — profile, artist page when there is one, settings — is
     * opened by the button beside the greeting on Home.
     */
    public void openAccountMenu(View anchor) {
        if (account == null) {
            new com.oru.app.data.repository.AuthRepository(
                    com.oru.app.util.LazySession.getApiService(),
                    new SessionManager(this),
                    com.oru.app.util.LazySession.getGson())
                    .getCurrentUser(new com.oru.app.data.repository.Callback<
                            com.oru.app.data.model.User>() {
                        @Override
                        public void onSuccess(com.oru.app.data.model.User loaded) {
                            account = loaded;
                            showAccountMenu(anchor);
                        }

                        @Override
                        public void onFailure(Throwable error) {
                            // Still offer the doors that do not need the account.
                            showAccountMenu(anchor);
                        }
                    });
            return;
        }
        showAccountMenu(anchor);
    }

    private void showAccountMenu(View anchor) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(
                this, anchor != null ? anchor : binding.toolbar);
        popup.inflate(R.menu.account_menu);
        android.view.MenuItem artistItem =
                popup.getMenu().findItem(R.id.menu_artist_page);
        if (artistItem != null) {
            artistItem.setVisible(account != null && account.hasArtistProfile());
        }
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_profile) {
                startActivity(new Intent(this,
                        com.oru.app.ui.profile.ProfileActivity.class));
                return true;
            }
            if (id == R.id.menu_artist_page && account != null) {
                com.oru.app.ui.components.Actions.openArtist(this, account.id);
                return true;
            }
            if (id == R.id.menu_settings) {
                startActivity(new Intent(this,
                        com.oru.app.ui.settings.SettingsActivity.class));
                return true;
            }
            return false;
        });
        popup.show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}
