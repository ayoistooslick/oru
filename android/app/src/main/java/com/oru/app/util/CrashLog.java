package com.oru.app.util;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Turns a crash into something the next person can act on.
 *
 * A sideloaded app has no crash console: when it closes by itself, the only
 * evidence is gone with it. This keeps the last uncaught exception in the app's
 * own files, and on the next launch shows it with a Copy button, so a report is
 * a paste instead of a guess. Only one trace is kept — the newest is the one
 * worth reading.
 */
public final class CrashLog {

    private static final String FILE = "last-crash.txt";
    private static final int SHOWN_CHARS = 2500;

    private CrashLog() {
    }

    /** Installs the handler. Call once, from the Application. */
    public static void install(Context context) {
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                write(app, error);
            } catch (Throwable ignored) {
                // Reporting must never be the reason a crash is worse.
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    private static void write(Context context, Throwable error) throws IOException {
        StringWriter text = new StringWriter();
        PrintWriter writer = new PrintWriter(text);
        text.append("ORU ").append(appVersion(context)).append('\n');
        text.append(android.os.Build.MANUFACTURER).append(' ')
                .append(android.os.Build.MODEL)
                .append(" · Android ").append(android.os.Build.VERSION.RELEASE)
                .append(" (API ").append(String.valueOf(android.os.Build.VERSION.SDK_INT))
                .append(")\n\n");
        error.printStackTrace(writer);
        writer.flush();
        try (FileOutputStream out = new FileOutputStream(
                new File(context.getFilesDir(), FILE), false)) {
            out.write(text.toString().getBytes("UTF-8"));
        }
    }

    private static String appVersion(Context context) {
        try {
            return String.valueOf(context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName);
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    /** Shows the stored trace once, then forgets it. */
    public static void reportIfAny(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        String trace = read(activity);
        if (trace == null) {
            return;
        }
        String shown = trace.length() > SHOWN_CHARS
                ? trace.substring(0, SHOWN_CHARS) + "\n…"
                : trace;
        new MaterialAlertDialogBuilder(activity)
                .setTitle("ORU closed unexpectedly")
                .setMessage(shown)
                .setPositiveButton("Copy", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager)
                            activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(
                                ClipData.newPlainText("ORU crash", trace));
                        Toast.makeText(activity, "Copied. Send it to the developer.",
                                Toast.LENGTH_LONG).show();
                    }
                    clear(activity);
                })
                .setNegativeButton("Close", (dialog, which) -> clear(activity))
                .show();
    }

    private static String read(Context context) {
        File file = new File(context.getFilesDir(), FILE);
        if (!file.isFile()) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int count;
            while ((count = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, count);
            }
            return buffer.toString("UTF-8");
        } catch (IOException error) {
            return null;
        }
    }

    private static void clear(Context context) {
        //noinspection ResultOfMethodCallIgnored
        new File(context.getFilesDir(), FILE).delete();
    }
}
