package com.oru.app.media;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import com.oru.app.data.offline.Downloads;

/**
 * Centralizes MediaItem construction for playback. The builder methods used
 * here (setMediaId / setMediaMetadata) are part of Media3's @UnstableApi
 * surface, which the app opts into globally via the lint override in
 * app/build.gradle (disable 'UnsafeOptInUsageError' / 'UnsafeOptInUsageWarning').
 */
public class MediaItemBuilder {

    private static Uri audioUri(String audioUrl) {
        if (audioUrl == null || audioUrl.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "This song does not have a playable audio file.");
        }
        Uri uri = Uri.parse(audioUrl.trim());
        if (uri.getScheme() == null) {
            throw new IllegalArgumentException(
                    "This song has an invalid audio address.");
        }
        return uri;
    }

    /** Simple variant: audio URI + mediaId (uri is set via setUri). */
    public static MediaItem create(String audioUrl, long id) {
        return new MediaItem.Builder()
                .setUri(audioUri(audioUrl))
                .setMediaId(String.valueOf(id))
                .build();
    }

    /** Rich variant: adds metadata so the media notification shows it. */
    public static MediaItem create(String audioUrl, long id,
            @Nullable String title, @Nullable String artist,
            @Nullable String coverUrl) {
        return new MediaItem.Builder()
                .setUri(audioUri(audioUrl))
                .setMediaId(String.valueOf(id))
                .setMediaMetadata(metadata(id, title, artist, coverUrl))
                .build();
    }

    /**
     * One shape for every field a lock screen, notification or mini player
     * might read. Display and album values are filled in too, because the
     * system's own media cards prefer them over the plain title.
     */
    private static MediaMetadata metadata(long id, @Nullable String title,
            @Nullable String artist, @Nullable String coverUrl) {
        com.oru.app.util.NowPlaying.remember((int) id, title, artist, coverUrl);
        MediaMetadata.Builder meta = new MediaMetadata.Builder();
        if (title != null) {
            meta.setTitle(title).setDisplayTitle(title).setAlbumTitle(title);
        }
        if (artist != null) {
            meta.setArtist(artist).setAlbumArtist(artist);
        }
        if (coverUrl != null) {
            meta.setArtworkUri(Uri.parse(coverUrl));
        }
        return meta.build();
    }

    /**
     * Plays the saved copy when the song is downloaded, so it works with no
     * connection; the server's type is kept so the player does not guess.
     */
    public static MediaItem create(Context context, String audioUrl, long id,
            @Nullable String title, @Nullable String artist,
            @Nullable String coverUrl) {
        Uri local = Downloads.localUri(context, (int) id);
        if (local == null) {
            return create(audioUrl, id, title, artist, coverUrl);
        }
        MediaItem.Builder builder = new MediaItem.Builder()
                .setUri(local)
                .setMediaId(String.valueOf(id));
        String mime = Downloads.mime(context, (int) id);
        if (mime != null) {
            builder.setMimeType(mime);
        }
        builder.setMediaMetadata(metadata(id, title, artist, coverUrl));
        return builder.build();
    }
}
