package com.oru.app.util;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.exifinterface.media.ExifInterface;

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

    /** Longest edge kept when a picture is re-encoded for an upload. */
    private static final int MAX_IMAGE_EDGE = 1600;

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
        File target = new File(dir, System.currentTimeMillis() + "-" + safeName(name));
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

    /**
     * A picture ready for the API: decoded, turned upright, shrunk to a sane
     * size and saved as a .jpg.
     *
     * The API checks the file name and the type together, so a phone's HEIC
     * photo (or a picker that hands back a nameless stream) used to be refused
     * before it ever left the device. Re-encoding also keeps big camera photos
     * under the size limit.
     */
    public static PickedFile copyImage(Context context, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream probe = context.getContentResolver().openInputStream(uri);
        if (probe == null) {
            throw new IOException("Couldn't open that picture");
        }
        try {
            BitmapFactory.decodeStream(probe, null, bounds);
        } finally {
            close(probe);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("That file isn't a picture the app can read");
        }

        Bitmap bitmap = decodeScaled(context, uri, bounds);
        if (bitmap == null) {
            throw new IOException("That picture couldn't be decoded");
        }
        bitmap = upright(context, uri, bitmap);

        File dir = new File(context.getCacheDir(), "uploads");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Couldn't prepare the upload");
        }
        File target = new File(dir, System.currentTimeMillis() + "-picture.jpg");
        FileOutputStream out = new FileOutputStream(target);
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) {
                throw new IOException("That picture couldn't be saved");
            }
        } finally {
            close(out);
            bitmap.recycle();
        }
        if (target.length() == 0) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw new IOException("That picture came out empty");
        }
        return new PickedFile(target, "picture.jpg", "image/jpeg", 0);
    }

    /** Bounds the longest edge at {@link #MAX_IMAGE_EDGE} pixels. */
    private static Bitmap decodeScaled(Context context, Uri uri,
            BitmapFactory.Options bounds) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (longest / sample > MAX_IMAGE_EDGE * 2) {
            sample *= 2;
        }
        options.inSampleSize = sample;
        Bitmap decoded;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            decoded = BitmapFactory.decodeStream(in, null, options);
        } catch (IOException | RuntimeException error) {
            return null;
        }
        if (decoded == null) {
            return null;
        }
        int edge = Math.max(decoded.getWidth(), decoded.getHeight());
        if (edge <= MAX_IMAGE_EDGE) {
            return decoded;
        }
        float scale = MAX_IMAGE_EDGE / (float) edge;
        Bitmap scaled = Bitmap.createScaledBitmap(decoded,
                Math.max(1, Math.round(decoded.getWidth() * scale)),
                Math.max(1, Math.round(decoded.getHeight() * scale)), true);
        if (scaled != decoded) {
            decoded.recycle();
        }
        return scaled;
    }

    /** Applies the camera's orientation tag, so faces are not sideways. */
    private static Bitmap upright(Context context, Uri uri, Bitmap bitmap) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return bitmap;
            }
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.postScale(1, -1);
                    break;
                default:
                    return bitmap;
            }
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        } catch (IOException | RuntimeException error) {
            return bitmap;
        }
    }

    private static void close(java.io.Closeable stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Nothing left to do with the stream.
        }
    }

    /** File names from providers may carry characters a path cannot hold. */
    private static String safeName(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return cleaned.isEmpty() ? "upload" : cleaned;
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
