package com.oru.app.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
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

        setupBottomNavigation();
        setupToolbar();
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
                    if (destination.getId() == R.id.homeFragment
                            || destination.getId() == R.id.searchFragment
                            || destination.getId() == R.id.libraryFragment
                            || destination.getId() == R.id.sharesFragment
                            || destination.getId() == R.id.studioFragment) {
                        binding.bottomNavigation.setVisibility(
                                android.view.View.VISIBLE);
                    } else {
                        binding.bottomNavigation.setVisibility(
                                android.view.View.GONE);
                    }
                });
    }

    private void setupToolbar() {
        // Toolbar title can be updated per destination via the listener above.
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}
