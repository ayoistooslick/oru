package com.oru.app.ui.components;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.oru.app.R;
import com.oru.app.data.session.SessionManager;
import com.oru.app.ui.MainActivity;
import com.oru.app.ui.profile.ProfileActivity;
import com.oru.app.ui.settings.SettingsActivity;
import com.oru.app.util.LazySession;

/**
 * The sheet behind the avatar in the header: who is signed in, then the doors.
 *
 * It exists because the profile used to be reachable only from the Home
 * greeting, so a listener who opened Search first had no way back to their own
 * account. The sheet opens over any tab, and on Android 12+ the page behind it
 * is blurred rather than merely dimmed, which makes the sheet the only sharp
 * thing on screen.
 */
public final class AccountSheet {

    private AccountSheet() {
    }

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View sheet = LayoutInflater.from(activity)
                .inflate(R.layout.sheet_account, null, false);
        dialog.setContentView(sheet);

        SessionManager session = new SessionManager(activity);
        android.widget.TextView name = sheet.findViewById(R.id.sheetName);
        android.widget.TextView handle = sheet.findViewById(R.id.sheetHandle);
        android.widget.ImageView avatar = sheet.findViewById(R.id.sheetAvatar);

        String display = session.getDisplayName();
        String at = session.getHandle();
        name.setText(display != null && !display.trim().isEmpty()
                ? display
                : at != null ? at : "You");
        handle.setText(at != null ? "@" + at : "Signed in");
        Covers.loadAvatar(avatar, session.getAvatarUrl());

        sheet.findViewById(R.id.sheetProfile).setOnClickListener(v -> {
            dialog.dismiss();
            activity.startActivity(new Intent(activity, ProfileActivity.class));
        });
        sheet.findViewById(R.id.sheetDrops).setOnClickListener(v -> {
            dialog.dismiss();
            openTab(activity, R.id.sharesFragment);
        });
        sheet.findViewById(R.id.sheetSettings).setOnClickListener(v -> {
            dialog.dismiss();
            activity.startActivity(new Intent(activity, SettingsActivity.class));
        });

        // The header already knows the cached identity; this fills in a name or
        // face that changed on another device while the sheet is open.
        new com.oru.app.data.repository.AuthRepository(
                LazySession.getApiService(), session, LazySession.getGson())
                .getCurrentUser(new com.oru.app.data.repository.Callback<>() {
                    @Override
                    public void onSuccess(com.oru.app.data.model.User user) {
                        if (user == null) {
                            return;
                        }
                        session.updateIdentity(user.displayName, user.avatarUrl);
                        name.setText(user.displayName);
                        handle.setText(user.handle != null ? "@" + user.handle
                                : "Signed in");
                        Covers.loadAvatar(avatar, user.avatarUrl);
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        // The cached identity is enough to use the sheet.
                    }
                });

        Window window = dialog.getWindow();
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The blur radius lives on the window's layout params, not the
            // window itself, and only exists from Android 12.
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.setBlurBehindRadius(18);
            window.setAttributes(params);
        }
        dialog.show();
    }

    /** Switches MainActivity to a destination that is no longer in the bar. */
    private static void openTab(Activity activity, int destination) {
        Intent intent = new Intent(activity, MainActivity.class);
        intent.putExtra("tab", destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
    }
}
