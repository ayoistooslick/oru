package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** A track, as the API's song_data() describes it. */
public class Song {

    @SerializedName("id")
    public int id;

    @SerializedName("artist_id")
    public int artistId;

    /** Artist's @handle, sent with every song. */
    @SerializedName("artist_handle")
    public String artistHandle;

    /** Artist's stage name (or display name), sent with every song. */
    @SerializedName("artist_name")
    public String artistName;

    @SerializedName("artist_avatar_url")
    public String artistAvatarUrl;

    @SerializedName("title")
    public String title;

    @SerializedName("description")
    public String description;

    @SerializedName("genres")
    public List<String> genres;

    /** "published", "pending" or "hidden". */
    @SerializedName("status")
    public String status;

    @SerializedName("cover_url")
    public String coverUrl;

    @SerializedName("audio_url")
    public String audioUrl;

    /** Where a shared song was found; only set on listener shares. */
    @SerializedName("source_url")
    public String sourceUrl;

    @SerializedName("featuring")
    public List<Featuring> featuring;

    @SerializedName("shared_by")
    public SharedBy sharedBy;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("published_at")
    public String publishedAt;

    public String artistLabel() {
        if (artistName != null && !artistName.isEmpty()) {
            return artistName;
        }
        if (artistHandle != null && !artistHandle.isEmpty()) {
            return artistHandle;
        }
        return "";
    }

    public boolean isPublished() {
        return "published".equals(status);
    }

    /** Genres as one readable line; minSdk 24 has no String.join. */
    public String genreLabel() {
        if (genres == null || genres.isEmpty()) {
            return "";
        }
        StringBuilder label = new StringBuilder();
        for (String genre : genres) {
            if (label.length() > 0) {
                label.append(" · ");
            }
            label.append(genre);
        }
        return label.toString();
    }

    public static class Featuring {
        @SerializedName("artist_id")
        public int artistId;

        @SerializedName("handle")
        public String handle;

        @SerializedName("name")
        public String name;
    }

    public static class SharedBy {
        @SerializedName("handle")
        public String handle;

        @SerializedName("display_name")
        public String displayName;
    }
}
