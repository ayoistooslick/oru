package com.oru.app.util;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Keeps the screens clear of the status bar and the gesture bar.
 *
 * On phones that draw apps edge to edge, the window content starts at the very
 * top of the display: a heading lands underneath the clock and the last row of
 * a page sits under the home indicator. Padding the content view by the system
 * bar insets is what pushes it back into the safe area, and because the insets
 * arrive already consumed on phones that are not edge to edge, the padding is
 * zero there and nothing moves.
 */
public final class SystemBars {

    /** Marks a view that already carries the listener, so it is only added once. */
    private static final int TAG = View.generateViewId();

    private SystemBars() {
    }

    /**
     * Applies the inset padding to the root view of the activity's content.
     *
     * Called for every activity as it is created, so a new screen is safe by
     * default instead of waiting for someone to remember the attribute.
     */
    public static void fit(Activity activity) {
        if (activity == null) {
            return;
        }
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) {
            return;
        }
        fit(content.getChildAt(0));
    }

    /** Applies the inset padding to one view, keeping the padding it declares. */
    public static void fit(View root) {
        if (root == null || root.getTag(TAG) != null) {
            return;
        }
        root.setTag(TAG, Boolean.TRUE);
        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars());
            Insets keys = windowInsets.getInsets(
                    WindowInsetsCompat.Type.ime());
            int bottom = Math.max(bars.bottom, keys.bottom);
            view.setPadding(baseLeft + bars.left, baseTop + bars.top,
                    baseRight + bars.right, baseBottom + bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
