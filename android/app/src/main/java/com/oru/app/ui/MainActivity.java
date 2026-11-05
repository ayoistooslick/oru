package com.oru.app.ui;

import android.content.Intent;
import android.os.Bundle;

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
        setupToolbar();
        openRequestedTab(getIntent());
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

    private void setupToolbar() {
        // Settings and the account live behind the toolbar's overflow.
        binding.toolbar.inflateMenu(R.menu.main_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(this,
                        com.oru.app.ui.settings.SettingsActivity.class));
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}
