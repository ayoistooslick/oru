package com.oru.app.data.offline;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.oru.app.data.api.ApiClient;
import com.oru.app.data.model.Song;
import com.oru.app.util.MediaUrl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Songs saved to the device for listening without a connection.
 *
 * Audio is written to a plain file in app storage and the song's details are
 * kept next to it in a small index, so the player can build a media item from
 * the local file and the library can list what is saved.
 */
public final class Downloads {

    /** Called on the main thread while bytes arrive. */
    public interface Progress {
        void onProgress(int percent);
    }

    /** Called on the main thread when a download ends. */
    public interface Result {
        void onDone(boolean ok, String message);
    }

    private static final String FOLDER = "downloads";
    private static final String INDEX = "index.json";

    private static final ExecutorService io = Executors.newFixedThreadPool(2);
    private static final Set<Integer> active =
            Collections.synchronizedSet(new HashSet<>());
    private static final Gson gson = new Gson();

    private Downloads() {
    }

    /** Everything the index remembers besides the song itself. */
    private static class Index {
        @SerializedName("songs")
        List<Song> songs = new ArrayList<>();

        @SerializedName("mimes")
        Map<String, String> mimes = new HashMap<>();
    }

    public static File folder(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(),
                FOLDER);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    public static File audioFile(Context context, int songId) {
        return new File(folder(context), songId + ".bin");
    }

    public static boolean isDownloaded(Context context, int songId) {
        File file = audioFile(context, songId);
        return file.exists() && file.length() > 0;
    }

    public static boolean isDownloading(int songId) {
        return active.contains(songId);
    }

    /** The local file as a playable address, or null when it is not saved. */
    public static Uri localUri(Context context, int songId) {
        return isDownloaded(context, songId)
                ? Uri.fromFile(audioFile(context, songId))
                : null;
    }

    /** The type the server sent, so the player does not have to guess. */
    public static String mime(Context context, int songId) {
        return read(context).mimes.get(String.valueOf(songId));
    }

    /** One saved song's details, or null when it is not saved. */
    public static Song song(Context context, int songId) {
        for (Song song : songs(context)) {
            if (song.id == songId) {
                return song;
            }
        }
        return null;
    }

    public static List<Song> songs(Context context) {
        Index index = read(context);
        List<Song> saved = new ArrayList<>();
        for (Song song : index.songs) {
            if (song != null && isDownloaded(context, song.id)) {
                saved.add(song);
            }
        }
        return saved;
    }

    public static long totalBytes(Context context) {
        long total = 0;
        File[] files = folder(context).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".bin")) {
                    total += file.length();
                }
            }
        }
        return total;
    }

    public static void remove(Context context, int songId) {
        File file = audioFile(context, songId);
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        Index index = read(context);
        List<Song> kept = new ArrayList<>();
        for (Song song : index.songs) {
            if (song.id != songId) {
                kept.add(song);
            }
        }
        index.songs = kept;
        index.mimes.remove(String.valueOf(songId));
        write(context, index);
    }

    /** Saves a song's audio to the device. Safe to call from the main thread. */
    public static void download(Context context, Song song, Progress progress,
            Result result) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        if (song == null || song.id <= 0) {
            result.onDone(false, "That song can't be saved");
            return;
        }
        if (isDownloaded(app, song.id)) {
            result.onDone(true, "Already saved for offline");
            return;
        }
        if (!active.add(song.id)) {
            result.onDone(false, "That song is already downloading");
            return;
        }

        final String url = MediaUrl.of(song.audioUrl);
        if (url == null) {
            active.remove(song.id);
            result.onDone(false, "That song has no audio to save");
            return;
        }

        io.execute(() -> {
            File target = audioFile(app, song.id);
            File part = new File(folder(app), song.id + ".part");
            String mime = null;
            try {
                OkHttpClient client = ApiClient.getOkHttpClient();
                if (client == null) {
                    client = new OkHttpClient();
                }
                Request request = new Request.Builder().url(url).build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new IOException("The server answered "
                                + response.code());
                    }
                    long total = response.body().contentLength();
                    mime = cleanType(response.header("Content-Type"));
                    try (InputStream in = response.body().byteStream();
                            FileOutputStream out = new FileOutputStream(part)) {
                        byte[] buffer = new byte[64 * 1024];
                        long read = 0;
                        int lastStep = -1;
                        int count;
                        while ((count = in.read(buffer)) > 0) {
                            out.write(buffer, 0, count);
                            read += count;
                            if (total > 0) {
                                int percent = (int) (read * 100 / total);
                                if (percent / 5 != lastStep) {
                                    lastStep = percent / 5;
                                    final int shown = percent;
                                    main.post(() -> progress.onProgress(shown));
                                }
                            }
                        }
                    }
                }
                if (part.length() == 0) {
                    throw new IOException("The download was empty");
                }
                if (target.exists() && !target.delete()) {
                    throw new IOException("Couldn't replace the old copy");
                }
                if (!part.renameTo(target)) {
                    throw new IOException("Couldn't finish the file");
                }
                Index index = read(app);
                index.songs.removeIf(existing -> existing == null
                        || existing.id == song.id);
                index.songs.add(song);
                index.mimes.put(String.valueOf(song.id),
                        mime != null ? mime : "audio/mpeg");
                write(app, index);
                main.post(() -> result.onDone(true, "Saved for offline"));
            } catch (Exception error) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                String message = error.getMessage();
                main.post(() -> result.onDone(false,
                        "Couldn't save that song"
                                + (message != null ? ": " + message : "")));
            } finally {
                active.remove(song.id);
            }
        });
    }

    /** Strips parameters such as "; charset=utf-8". */
    private static String cleanType(String header) {
        if (header == null) {
            return null;
        }
        int semicolon = header.indexOf(';');
        String type = (semicolon >= 0 ? header.substring(0, semicolon) : header)
                .trim();
        return type.isEmpty() ? null : type;
    }

    private static Index read(Context context) {
        File file = new File(folder(context), INDEX);
        if (!file.exists()) {
            return new Index();
        }
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            java.io.ByteArrayOutputStream buffer =
                    new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8 * 1024];
            int count;
            while ((count = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, count);
            }
            Type type = new TypeToken<Index>() {
            }.getType();
            Index index = gson.fromJson(
                    new String(buffer.toByteArray(),
                            java.nio.charset.StandardCharsets.UTF_8),
                    type);
            if (index == null) {
                return new Index();
            }
            if (index.songs == null) {
                index.songs = new ArrayList<>();
            }
            if (index.mimes == null) {
                index.mimes = new HashMap<>();
            }
            return index;
        } catch (Exception ignored) {
            return new Index();
        }
    }

    private static void write(Context context, Index index) {
        File file = new File(folder(context), INDEX);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(gson.toJson(index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // A lost index only costs the list; the audio file still plays.
        }
    }
}
