package com.oru.app.media;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

/**
 * Centralizes MediaItem construction for playback. The builder methods used
 * here (setMediaId / setMediaMetadata) are part of Media3's @UnstableApi
 * surface, which the app opts into globally via the lint override in
 * app/build.gradle (disable 'UnsafeOptInUsageError' / 'UnsafeOptInUsageWarning').
 */
public class MediaItemBuilder {

    /** Simple variant: audio URI + mediaId (uri is set via setUri). */
    public static MediaItem create(String audioUrl, long id) {
        return new MediaItem.Builder()
                .setUri(Uri.parse(audioUrl))
                .setMediaId(String.valueOf(id))
                .build();
    }

    /** Rich variant: adds metadata so the media notification shows it. */
    public static MediaItem create(String audioUrl, long id,
            @Nullable String title, @Nullable String artist,
            @Nullable String coverUrl) {
        MediaItem.Builder builder = new MediaItem.Builder()
                .setUri(Uri.parse(audioUrl))
                .setMediaId(String.valueOf(id));

        if (title != null || artist != null) {
            MediaMetadata.Builder meta = new MediaMetadata.Builder();
            if (title != null) meta.setTitle(title);
            if (artist != null) meta.setArtist(artist);
            if (coverUrl != null) meta.setArtworkUri(Uri.parse(coverUrl));
            builder.setMediaMetadata(meta.build());
        }
        return builder.build();
    }
}
