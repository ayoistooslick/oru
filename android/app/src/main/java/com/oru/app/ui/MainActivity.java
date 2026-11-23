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
 * Main activity: avatar header + bottom navigation + NavHost.
 * The four tabs are home, search, library and studio; drops and the account
 * live behind the avatar so they are reachable from every tab.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private boolean keyboardVisible;
    private boolean mainDestination = true;

    private com.oru.app.ui.components.MiniPlayer miniPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Before anything inflates: if the last launch failed, say so.
        com.oru.app.util.CrashLog.reportIfAny(this);
        try {
            open();
        } catch (Throwable error) {
            // A startup failure must not look like the app closing by itself:
            // show the reason, with a way to copy it.
            com.oru.app.util.CrashLog.record(this, error);
            com.oru.app.util.CrashLog.showFallback(this, error);
        }
    }

    private void open() {
        // Auth gate: if not signed in, go to the auth activity.
        SessionManager session = new SessionManager(this);
        if (!session.isSignedIn()) {
            Intent intent = new Intent(this, com.oru.app.ui.auth.AuthActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // A shared link that arrived before there was an account: finish it now
        // that the sign-in is done, however it was done.
        String pendingLink = session.takePendingLink();
        if (pendingLink != null) {
            Intent open = new Intent(this, OpenLinkActivity.class);
            open.setData(android.net.Uri.parse(pendingLink));
            startActivity(open);
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // The root's inset listener belongs to SystemBars, which pads the whole
        // screen clear of the status and gesture bars. Watching the keyboard
        // from the bar itself keeps that padding intact — two listeners on one
        // view means the last one wins, and back then the safe area lost.
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomArea,
                (view, insets) -> {
                    keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
                    updateBottomNavigationVisibility();
                    return insets;
                });
        ViewCompat.requestApplyInsets(binding.getRoot());
        com.oru.app.util.CrashLog.reportIfAny(this);

        setupBottomNavigation();
        miniPlayer = new com.oru.app.ui.components.MiniPlayer(this, binding.miniPlayer);
        askForNotifications();
        binding.accountButton.setOnClickListener(v ->
                com.oru.app.ui.components.AccountSheet.show(this));
        binding.notificationsButton.setOnClickListener(v -> startActivity(
                new Intent(this,
                        com.oru.app.ui.notifications.NotificationsActivity.class)));
        loadHeaderAvatar();
        openRequestedTab(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            // A picture changed in the profile is the header's picture too, and
            // the bell has to know whether anything new arrived while away.
            loadHeaderAvatar();
            loadUnread();
            // Coming back to the app is the moment to notice a new release: an
            // install that was left open for days learns about it here.
            com.oru.app.util.UpdateCheck.maybeCheck(this);
        } catch (Throwable error) {
            // None of this is worth closing the app for.
            com.oru.app.util.CrashLog.record(this, error);
        }
    }

    /** A dot, not a count: the number is on the drops screen itself. */
    private void loadUnread() {
        if (binding == null) {
            return;
        }
        new com.oru.app.data.repository.NotificationsRepository(
                com.oru.app.util.LazySession.getApiService(),
                com.oru.app.util.LazySession.getGson())
                .unread(new com.oru.app.data.repository.Callback<
                        com.oru.app.data.model.NotificationsPage.Unread>() {
                    @Override
                    public void onSuccess(
                            com.oru.app.data.model.NotificationsPage.Unread unread) {
                        if (binding == null) {
                            return;
                        }
                        binding.unreadDot.setVisibility(unread != null
                                && unread.unread > 0
                                ? android.view.View.VISIBLE
                                : android.view.View.GONE);
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        // No dot is the right answer when the count is unknown.
                    }
                });
    }

    /** The header is a face, so it has to follow the saved identity. */
    private void loadHeaderAvatar() {
        if (binding == null) {
            return;
        }
        SessionManager session = new SessionManager(this);
        com.oru.app.ui.components.Covers.loadAvatar(binding.accountButton,
                session.getAvatarUrl());
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
            // Same options the bar uses: one entry per tab, and the tab's own
            // state restored when you come back to it.
            navController.navigate(tab, null, tabOptions());
        }
    }

    /**
     * How a tab is entered: no duplicate entries, and the back stack collapses
     * to the start destination. Without this every trip out to profile or
     * drops left another copy behind, so Back walked through old tabs instead of
     * leaving the app from Home the way it should.
     */
    private androidx.navigation.NavOptions tabOptions() {
        return new androidx.navigation.NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(navController.getGraph().getStartDestinationId(),
                        false, true)
                .setRestoreState(true)
                .build();
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

        // Tapping the tab you are already on returns to the top of it, which is
        // what a listener reaches for after scrolling a long list.
        bottomNav.setOnItemReselectedListener(item -> scrollCurrentToTop());

        // Back reaches Home first, then leaves the app. Before this, Back from
        // any tab closed ORU outright.
        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (navController.getCurrentDestination() != null
                                && navController.getCurrentDestination().getId()
                                        != R.id.homeFragment) {
                            navController.navigate(R.id.homeFragment, null,
                                    tabOptions());
                            return;
                        }
                        // Already home: leave, exactly as Back would. Calling
                        // finish() rather than re-dispatching keeps this callback
                        // armed for the next press instead of disabling it once.
                        finish();
                    }
                });

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

    /** Finds the scrollable child of whichever tab is showing and rewinds it. */
    private void scrollCurrentToTop() {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (host == null) {
            return;
        }
        for (androidx.fragment.app.Fragment fragment : host.getChildFragmentManager()
                .getFragments()) {
            android.view.View view = fragment.getView();
            if (view == null) {
                continue;
            }
            androidx.recyclerview.widget.RecyclerView list =
                    findRecycler(view);
            if (list != null && list.getLayoutManager() != null) {
                list.smoothScrollToPosition(0);
                return;
            }
            androidx.core.widget.NestedScrollView scroll =
                    findScroll(view);
            if (scroll != null) {
                scroll.smoothScrollTo(0, 0);
                return;
            }
        }
    }

    private static androidx.recyclerview.widget.RecyclerView findRecycler(
            android.view.View view) {
        if (view instanceof androidx.recyclerview.widget.RecyclerView) {
            return (androidx.recyclerview.widget.RecyclerView) view;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                androidx.recyclerview.widget.RecyclerView found =
                        findRecycler(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static androidx.core.widget.NestedScrollView findScroll(
            android.view.View view) {
        if (view instanceof androidx.core.widget.NestedScrollView) {
            return (androidx.core.widget.NestedScrollView) view;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                androidx.core.widget.NestedScrollView found =
                        findScroll(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void updateBottomNavigationVisibility() {
        if (binding != null) {
            binding.bottomNavigation.setVisibility(
                    mainDestination && !keyboardVisible
                            ? android.view.View.VISIBLE
                            : android.view.View.GONE);
        }
    }


    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}
