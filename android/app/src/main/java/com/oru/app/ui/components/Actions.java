package com.oru.app.ui.components;

import android.content.Context;
import android.content.Intent;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.oru.app.R;
import com.oru.app.data.model.Report;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.ModerationRepository;
import com.oru.app.util.LazySession;

/** Share and report, shared by every screen that offers them. */
public final class Actions {

    private Actions() {
    }

    /** Hands the link to whatever app the listener wants to send it with. */
    public static void share(Context context, String subject, String url) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, subject);
        send.putExtra(Intent.EXTRA_TEXT, subject + " on ORU — " + url);
        context.startActivity(Intent.createChooser(send, "Share"));
    }

    /**
     * The same report flow the web uses: pick a reason, add details, send.
     *
     * @param targetType "song" or "artist", as the API expects.
     */
    public static void report(Context context, String targetType, int targetId,
            String label) {
        final String[] reasons = { "copyright", "harassment", "spam",
                "impersonation", "other" };
        new AlertDialog.Builder(context)
                .setTitle("Report " + label)
                .setItems(reasons, (dialog, which) -> {
                    EditText details = new EditText(context);
                    details.setHint("Anything that helps us look");
                    details.setMinLines(2);
                    new AlertDialog.Builder(context)
                            .setTitle("Report as " + reasons[which])
                            .setView(details)
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton("Send",
                                    (d, w) -> sendReport(context, targetType,
                                            targetId, reasons[which],
                                            details.getText().toString().trim()))
                            .show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void sendReport(Context context, String targetType,
            int targetId, String reason, String details) {
        new ModerationRepository(LazySession.getApiService(),
                LazySession.getGson())
                .createReport(targetType, targetId, reason, details,
                        new Callback<Report>() {
                            @Override
                            public void onSuccess(Report report) {
                                Toast.makeText(context,
                                        "Report sent — thank you",
                                        Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onFailure(Throwable error) {
                                Toast.makeText(context,
                                        UiErrors.message(context, error),
                                        Toast.LENGTH_LONG).show();
                            }
                        });
    }
}
