package com.oru.app.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;

import androidx.browser.customtabs.CustomTabsIntent;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.oru.app.R;
import com.oru.app.data.api.ApiClient;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Asks the site what the newest build is, and offers it when this install is
 * behind.
 *
 * The Play Store is not in the picture: ORU is sideloaded, so nothing updates
 * it on its own. Every install checks the release manifest at
 * {@code /app-version.json} (the same file the site publishes) and, when that
 * names a higher version code, shows a dialog with the download. The check is
 * quiet and rate limited: a phone that is up to date, or that checked recently,
 * never sees anything, and a failed check is never an error message.
 */
public final class UpdateCheck {

    /** The manifest the site serves next to the app, per line. */
    private static final String MANIFEST = "/app-version.json";
    private static final String BETA_MANIFEST = "/app-version-beta.json";
    /** One check a day is plenty for a release that changes now and then. */
    private static final long EVERY_MS = TimeUnit.HOURS.toMillis(12);
    private static final String PREFS = "oru";
    private static final String CHECKED_AT = "update-checked-at";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build();

    private UpdateCheck() {
    }

    /** Fetches the manifest at most every twelve hours, and prompts if newer. */
    public static void maybeCheck(final Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        final SharedPreferences prefs = activity.getSharedPreferences(PREFS,
                Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - prefs.getLong(CHECKED_AT, 0L) < EVERY_MS) {
            return;
        }
        // Stamped before the request: a phone with no connection should not try
        // again on every screen change.
        prefs.edit().putLong(CHECKED_AT, now).apply();

        // The beta install follows its own channel: it must never be pointed at
        // the release build, and a release install must never be offered the
        // beta.
        boolean beta = activity.getPackageName().endsWith(".beta");
        Request request = new Request.Builder()
                .url(ApiClient.origin() + (beta ? BETA_MANIFEST : MANIFEST))
                .header("Cache-Control", "no-cache")
                .build();
        CLIENT.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException error) {
                // Offline, or the site is down: silence is the right answer.
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) {
                if (response.body() == null) {
                    response.close();
                    return;
                }
                final String body;
                try {
                    body = response.body().string();
                } catch (java.io.IOException error) {
                    return;
                } finally {
                    response.close();
                }
                final JSONObject manifest;
                try {
                    manifest = new JSONObject(body);
                } catch (org.json.JSONException error) {
                    return;
                }
                new Handler(Looper.getMainLooper()).post(
                        () -> offerIfNewer(activity, manifest));
            }
        });
    }

    /** The prompt itself, on the main thread. */
    private static void offerIfNewer(Activity activity, JSONObject manifest) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        int latest = manifest.optInt("versionCode", 0);
        int installed = installedVersionCode(activity);
        if (latest <= 0 || installed <= 0 || latest <= installed) {
            return;
        }
        String url = manifest.optString("url", "").trim();
        if (url.isEmpty()) {
            return;
        }
        String name = manifest.optString("versionName", String.valueOf(latest));
        String notes = manifest.optString("notes", "").trim();
        boolean required = manifest.optBoolean("required", false);

        StringBuilder message = new StringBuilder();
        message.append("ORU ").append(name).append(" is out");
        if (!notes.isEmpty()) {
            message.append(". ").append(notes);
        }
        message.append(notes.endsWith(".") || notes.isEmpty() ? "" : ".");
        message.append(" Install it to keep listening with the newest fixes.");

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Update ORU")
                .setMessage(message.toString())
                .setPositiveButton("Update", (dialog, which) -> open(activity, url))
                .setNegativeButton(required ? null : "Later", null)
                .setCancelable(!required)
                .show();
    }

    /** This install's own version code, read from the package itself. */
    private static int installedVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return (int) androidx.core.content.pm.PackageInfoCompat
                    .getLongVersionCode(info);
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            return 0;
        }
    }

    /**
     * The download page: a Custom Tab so the app stays behind it, and a plain
     * browser intent on a phone without one.
     */
    private static void open(Activity activity, String url) {
        android.net.Uri uri = android.net.Uri.parse(url);
        try {
            new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setToolbarColor(activity.getColor(R.color.paper_deep))
                    .build()
                    .launchUrl(activity, uri);
        } catch (RuntimeException noCustomTab) {
            Intent browser = new Intent(Intent.ACTION_VIEW, uri);
            browser.addCategory(Intent.CATEGORY_BROWSABLE);
            try {
                activity.startActivity(browser);
            } catch (android.content.ActivityNotFoundException none) {
                // No browser to finish the download with; say nothing.
            }
        }
    }
}
