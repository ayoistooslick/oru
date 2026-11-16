package com.oru.app.ui.components;

import android.app.Activity;
import android.content.Intent;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.oru.app.R;
import com.oru.app.ui.MainActivity;

/**
 * The four-tab bar on pages that are not inside MainActivity (profile, artist).
 *
 * Those pages belong to no tab, so nothing is highlighted; tapping a tab hands
 * it to MainActivity, which already owns the fragments, its mini player and the
 * back stack. Keeping one owner avoids a second copy of every screen.
 */
public final class BottomNav {

    private BottomNav() {
    }

    public static void attach(BottomNavigationView nav, Activity activity) {
        if (nav == null || activity == null) {
            return;
        }
        // No tab is "current" here, so the checked state would be a lie.
        nav.getMenu().setGroupCheckable(0, false, false);
        nav.setOnItemSelectedListener(item -> {
            open(activity, item.getItemId());
            return false;
        });
    }

    private static void open(Activity activity, int destination) {
        Intent intent = new Intent(activity, MainActivity.class);
        intent.putExtra("tab", destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
    }

    /** The tab ids the bar knows, for callers that want to test a destination. */
    public static boolean isTab(int id) {
        return id == R.id.homeFragment || id == R.id.searchFragment
                || id == R.id.libraryFragment || id == R.id.studioFragment;
    }
}
