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

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

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

    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;

    /**
     * The web keeps a player bar on every page; this keeps the same controls on
     * the main screen, driven by the same media session as the notification.
     */
    private final Player.Listener miniListener = new Player.Listener() {
        @Override
        public void onMediaItemTransition(@Nullable MediaItem item, int reason) {
            updateMiniPlayer();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            updateMiniPlayer();
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            updateMiniPlayer();
        }
    };

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
        setupMiniPlayer();
        askForNotifications();
        openRequestedTab(getIntent());
    }

    private void setupMiniPlayer() {
        binding.miniPlayerBar.setOnClickListener(v -> startActivity(
                new Intent(this, com.oru.app.ui.player.PlayerActivity.class)));
        binding.miniPlayPause.setOnClickListener(v -> {
            if (controller == null) {
                return;
            }
            if (controller.isPlaying()) {
                controller.pause();
            } else {
                controller.play();
            }
        });
        binding.miniNext.setOnClickListener(v -> {
            if (controller != null) {
                controller.seekToNextMediaItem();
            }
        });

        SessionToken token = new SessionToken(this,
                new ComponentName(this, com.oru.app.media.PlayerService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
            } catch (Exception error) {
                return;
            }
            runOnUiThread(() -> {
                if (binding == null) {
                    return;
                }
                controller.addListener(miniListener);
                updateMiniPlayer();
            });
        }, androidx.core.content.ContextCompat.getMainExecutor(this));
    }

    private void updateMiniPlayer() {
        if (binding == null) {
            return;
        }
        MediaItem item = controller != null ? controller.getCurrentMediaItem() : null;
        boolean playingSomething = item != null && controller != null
                && controller.getPlaybackState() != Player.STATE_IDLE
                && controller.getPlaybackState() != Player.STATE_ENDED;
        binding.miniPlayerBar.setVisibility(
                playingSomething ? View.VISIBLE : View.GONE);
        if (!playingSomething) {
            return;
        }
        MediaMetadata metadata = item.mediaMetadata;
        binding.miniTitle.setText(metadata.title);
        binding.miniArtist.setText(metadata.artist);
        com.oru.app.ui.components.Covers.load(binding.miniCover,
                metadata.artworkUri != null ? metadata.artworkUri.toString() : null,
                R.drawable.ic_music);
        binding.miniPlayPause.setImageResource(controller.isPlaying()
                ? R.drawable.ic_pause : R.drawable.ic_play);
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
        if (controller != null) {
            controller.removeListener(miniListener);
            controller = null;
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
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
