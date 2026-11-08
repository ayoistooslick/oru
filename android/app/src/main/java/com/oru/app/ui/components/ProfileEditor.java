package com.oru.app.ui.components;

import android.text.InputFilter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.oru.app.R;
import com.oru.app.data.model.User;
import com.oru.app.data.repository.AuthRepository;
import com.oru.app.data.repository.Callback;

/**
 * The name-and-bio form, in one place.
 *
 * The profile page and settings both offer it; two copies of the same dialog
 * would drift, so both call this.
 */
public final class ProfileEditor {

    /** Told when the API has stored the new name and bio. */
    public interface Listener {
        void onSaved(User updated);
    }

    private ProfileEditor() {
    }

    public static void show(AppCompatActivity activity, User user,
            AuthRepository repo, Listener listener) {
        int pad = Math.round(20 * activity.getResources()
                .getDisplayMetrics().density);
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(pad, pad, pad, 0);

        final EditText name = new EditText(activity);
        name.setHint("Display name");
        name.setSingleLine(true);
        name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(80)});
        if (user != null && user.displayName != null) {
            name.setText(user.displayName);
        }
        form.addView(name);

        final EditText bio = new EditText(activity);
        bio.setHint("A line about you");
        bio.setMinLines(2);
        bio.setMaxLines(4);
        bio.setFilters(new InputFilter[] {new InputFilter.LengthFilter(1000)});
        if (user != null && user.bio != null) {
            bio.setText(user.bio);
        }
        form.addView(bio);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Edit profile")
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String displayName = name.getText().toString().trim();
                    if (displayName.isEmpty()) {
                        name.setError("Display name cannot be empty");
                        return;
                    }
                    dialog.dismiss();
                    save(activity, repo, displayName,
                            bio.getText().toString().trim(), listener);
                }));
        dialog.show();
    }

    private static void save(AppCompatActivity activity, AuthRepository repo,
            String displayName, String bio, Listener listener) {
        repo.updateProfile(displayName, bio, new Callback<User>() {
            @Override
            public void onSuccess(User updated) {
                activity.runOnUiThread(() -> {
                    Toast.makeText(activity, "Profile saved", Toast.LENGTH_SHORT)
                            .show();
                    if (listener != null && updated != null) {
                        listener.onSaved(updated);
                    }
                });
            }

            @Override
            public void onFailure(Throwable error) {
                activity.runOnUiThread(() -> Toast.makeText(activity,
                        UiErrors.message(activity, error), Toast.LENGTH_LONG)
                        .show());
            }
        });
    }
}
