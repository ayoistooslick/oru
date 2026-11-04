package com.oru.app.util;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * A document the listener picked, copied into the cache so Retrofit has a real
 * file to stream. The length is read here because the API never opens the audio.
 */
public final class PickedFile {

    public final File file;
    public final String name;
    public final String mime;
    public final long durationMs;

    private PickedFile(File file, String name, String mime, long durationMs) {
        this.file = file;
        this.name = name;
        this.mime = mime;
        this.durationMs = durationMs;
    }

    public boolean isAudio() {
        return mime != null && mime.startsWith("audio/");
    }

    public boolean isImage() {
        return mime != null && mime.startsWith("image/");
    }

    public static PickedFile copy(Context context, Uri uri) throws IOException {
        String name = displayName(context, uri);
        if (name == null || name.trim().isEmpty()) {
            name = "upload";
        }
        String mime = context.getContentResolver().getType(uri);
        if (mime == null) {
            mime = guessMime(name);
        }
        File dir = new File(context.getCacheDir(), "uploads");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Couldn't prepare the upload");
        }
        File target = new File(dir, System.currentTimeMillis() + "-" + name);
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IOException("Couldn't read that file");
        }
        try {
            FileOutputStream out = new FileOutputStream(target);
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
        long duration = mime.startsWith("audio/") ? durationOf(context, uri) : 0;
        return new PickedFile(target, name, mime, duration);
    }

    private static String displayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (RuntimeException ignored) {
            // Fall back to the URI's last segment below.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : null;
    }

    private static long durationOf(Context context, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String value = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            return value != null ? Long.parseLong(value) : 0;
        } catch (RuntimeException ignored) {
            return 0;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
                // Nothing to do; the length simply stays unknown.
            }
        }
    }

    /** Files picked from storage often arrive without a type. */
    private static String guessMime(String name) {
        String lower = name.toLowerCase(Locale.US);
        int dot = lower.lastIndexOf('.');
        String extension = dot >= 0 ? lower.substring(dot + 1) : "";
        switch (extension) {
            case "mp3":
                return "audio/mpeg";
            case "m4a":
                return "audio/mp4";
            case "wav":
                return "audio/wav";
            case "ogg":
            case "oga":
                return "audio/ogg";
            case "flac":
                return "audio/flac";
            case "aac":
                return "audio/aac";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "webp":
                return "image/webp";
            case "gif":
                return "image/gif";
            default:
                return "application/octet-stream";
        }
    }
}
